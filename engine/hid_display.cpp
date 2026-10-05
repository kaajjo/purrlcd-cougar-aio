#include "hid_display.h"
#include "protocol.h"
#include <hidsdi.h>
#include <hidpi.h>
#include <setupapi.h>
#include <tlhelp32.h>
#include <chrono>
#include <algorithm>

namespace purrlcd {
bool stockEditorRunning() {
    HANDLE snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snapshot == INVALID_HANDLE_VALUE) return true; // Fail closed if enumeration fails.
    PROCESSENTRY32W entry{}; entry.dwSize = sizeof(entry);
    bool found = false;
    if (Process32FirstW(snapshot, &entry)) do {
        if (_wcsicmp(entry.szExeFile, L"COUGAR LCD Editor.exe") == 0) { found = true; break; }
    } while (Process32NextW(snapshot, &entry));
    CloseHandle(snapshot); return found;
}

struct HidDisplay::Impl {
    HANDLE handle = INVALID_HANDLE_VALUE;
    HANDLE stopEvent = nullptr;
    USHORT inputBytes = 0, outputBytes = 0;
    uint32_t sequence = 0;
    uint64_t fileCounter = 0;
    int deviceRotation = 180;
    Bytes previousBackground;
    ~Impl() { close(); }
    void close() {
        if (handle != INVALID_HANDLE_VALUE) { CancelIoEx(handle, nullptr); CloseHandle(handle); handle = INVALID_HANDLE_VALUE; }
        previousBackground.clear();
    }
    DWORD io(Bytes& bytes, bool write, DWORD timeout) {
        if (stopEvent && WaitForSingleObject(stopEvent, 0) == WAIT_OBJECT_0)
            throw std::runtime_error("Transfer stopped");
        OVERLAPPED op{}; op.hEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
        if (!op.hEvent) throw std::runtime_error("Cannot create USB event");
        DWORD count = 0;
        BOOL ok = write ? WriteFile(handle, bytes.data(), (DWORD)bytes.size(), &count, &op)
                        : ReadFile(handle, bytes.data(), (DWORD)bytes.size(), &count, &op);
        DWORD error = ok ? ERROR_SUCCESS : GetLastError();
        if (!ok && error == ERROR_IO_PENDING) {
            HANDLE events[]{op.hEvent, stopEvent};
            DWORD wait = WaitForMultipleObjects(stopEvent ? 2 : 1, events, FALSE, timeout);
            if (wait == WAIT_OBJECT_0) {
                ok = GetOverlappedResult(handle, &op, &count, FALSE);
                error = ok ? ERROR_SUCCESS : GetLastError();
            } else {
                CancelIoEx(handle, &op); GetOverlappedResult(handle, &op, &count, TRUE);
                error = wait == WAIT_OBJECT_0 + 1 ? ERROR_OPERATION_ABORTED : ERROR_TIMEOUT;
            }
        }
        CloseHandle(op.hEvent);
        if (!ok) throw std::runtime_error("USB: " + errorText(error));
        if (!count || (write && count != bytes.size())) throw std::runtime_error("Incomplete USB transfer");
        return count;
    }
    void write(const Bytes& payload) {
        auto report = makeHidReport(payload, outputBytes); io(report, true, 3000);
    }
    Response response(uint32_t ack, DWORD timeout = 5000) {
        auto deadline = GetTickCount64() + timeout;
        do {
            auto now = GetTickCount64();
            if (now >= deadline) break;
            Bytes report(inputBytes);
            DWORD count = io(report, false, (DWORD)(deadline - now));
            if (count < 2 || report[0] != 0) throw std::runtime_error("Unexpected HID report ID");
            report.erase(report.begin()); report.resize(count - 1);
            auto result = decodeResponse(report);
            if (result.ackNumber != ack) continue; // A delayed response from an earlier transaction.
            if (!result.success()) throw std::invalid_argument("Display rejected command: " + std::to_string(result.code) + " " + result.body);
            if (!result.body.empty()) {
                auto body = Json::parse(result.body);
                if (body.contains("state") && body["state"] != "success")
                    throw std::runtime_error("Display error: " + result.body);
            }
            return result;
        } while (GetTickCount64() < deadline);
        throw std::runtime_error("No acknowledgement from display");
    }
    Json command(const std::string& name, const Json& body = Json()) {
        auto now = (uint64_t)std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count();
        auto seq = sequence++;
        write(encodeRequest("POST", name, seq, now, body.is_null() ? "" : body.dump()));
        Response result;
        try { result = response(seq + 1); }
        catch (const std::invalid_argument& e) { throw std::invalid_argument(name + ": " + e.what()); }
        catch (const std::exception& e) { throw std::runtime_error(name + ": " + e.what()); }
        return result.body.empty() ? Json::object() : Json::parse(result.body);
    }
    void file(const Bytes& bytes, const char* extension) {
        if (bytes.empty() || bytes.size() > 20 * 1024 * 1024) throw std::runtime_error("Invalid LCD image size");
        auto filename = "purrlcd-" + std::to_string(GetTickCount64()) + "-" + std::to_string(++fileCounter) + extension;
        auto result = command("transport", {{"type", "media"}, {"fileSize", bytes.size()}, {"fileName", filename}});
        size_t blockSize = result.at("blockMaxSize").get<size_t>();
        // The vendor's high-level HID sender uses type 1 for PNG and OSD files.
        auto blocks = makeFileBlocks(bytes, blockSize, outputBytes, 255, 1);
        auto deadline = GetTickCount64() + 30000;
        for (const auto& block : blocks) {
            if (GetTickCount64() >= deadline) throw std::runtime_error("Image transfer timed out");
            write(block);
        }
        try { response(0, 10000); }
        catch (const std::invalid_argument& e) { throw std::invalid_argument("File blocks: " + std::string(e.what())); }
        catch (const std::exception& e) { throw std::runtime_error("File blocks: " + std::string(e.what())); }
        command("transported", {{"md5", "todo"}, {"fileName", filename}});
    }
};

HidDisplay::HidDisplay(HANDLE stopEvent) : impl_(std::make_unique<Impl>()) { impl_->stopEvent = stopEvent; }
HidDisplay::~HidDisplay() = default;
bool HidDisplay::isOpen() const { return impl_->handle != INVALID_HANDLE_VALUE; }
void HidDisplay::close() { impl_->close(); }
Json HidDisplay::open() {
    close();
    if (stockEditorRunning()) throw std::runtime_error("Exit COUGAR LCD Editor first");
    GUID guid; HidD_GetHidGuid(&guid);
    HDEVINFO devices = SetupDiGetClassDevsW(&guid, nullptr, nullptr, DIGCF_PRESENT | DIGCF_DEVICEINTERFACE);
    if (devices == INVALID_HANDLE_VALUE) throw std::runtime_error("Could not enumerate USB devices");
    SP_DEVICE_INTERFACE_DATA interfaceData{}; interfaceData.cbSize = sizeof(interfaceData);
    for (DWORD index = 0; SetupDiEnumDeviceInterfaces(devices, nullptr, &guid, index, &interfaceData); ++index) {
        DWORD needed = 0;
        SetupDiGetDeviceInterfaceDetailW(devices, &interfaceData, nullptr, 0, &needed, nullptr);
        if (needed < sizeof(SP_DEVICE_INTERFACE_DETAIL_DATA_W)) continue;
        std::vector<uint8_t> detailBytes(needed);
        auto* detail = (SP_DEVICE_INTERFACE_DETAIL_DATA_W*)detailBytes.data(); detail->cbSize = sizeof(*detail);
        if (!SetupDiGetDeviceInterfaceDetailW(devices, &interfaceData, detail, needed, nullptr, nullptr)) continue;
        HANDLE query = CreateFileW(detail->DevicePath, 0, FILE_SHARE_READ | FILE_SHARE_WRITE, nullptr, OPEN_EXISTING, 0, nullptr);
        if (query == INVALID_HANDLE_VALUE) continue;
        HIDD_ATTRIBUTES attributes{}; attributes.Size = sizeof(attributes);
        bool match = HidD_GetAttributes(query, &attributes) && attributes.VendorID == 0x1d6b && attributes.ProductID == 0x0110;
        HIDP_CAPS caps{}; PHIDP_PREPARSED_DATA data = nullptr;
        if (match && HidD_GetPreparsedData(query, &data)) {
            match = HidP_GetCaps(data, &caps) == HIDP_STATUS_SUCCESS && caps.UsagePage == 0xff00 && caps.Usage == 1 &&
                    caps.InputReportByteLength == 1025 && caps.OutputReportByteLength == 1025;
            HidD_FreePreparsedData(data);
        } else match = false;
        CloseHandle(query);
        if (!match) continue;
        impl_->handle = CreateFileW(detail->DevicePath, GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE,
            nullptr, OPEN_EXISTING, FILE_FLAG_OVERLAPPED, nullptr);
        if (impl_->handle != INVALID_HANDLE_VALUE) {
            impl_->inputBytes = caps.InputReportByteLength; impl_->outputBytes = caps.OutputReportByteLength; break;
        }
    }
    SetupDiDestroyDeviceInfoList(devices);
    if (!isOpen()) throw std::runtime_error("COUGAR 1D6B:0110 display not found or in use");
    try {
        impl_->sequence = 0;
        HidD_FlushQueue(impl_->handle);
        auto info = impl_->command("conn");
        impl_->deviceRotation = info.value("degree", 180);
        impl_->command("power", {{"event", "resume"}});
        return info;
    } catch (...) { close(); throw; }
}
void HidDisplay::configure(const Bytes& background, int rotation) {
    if (!isOpen()) throw std::runtime_error("Display not connected");
    if (rotation != impl_->deviceRotation) {
        impl_->command("rotate", {{"degree", rotation}}); impl_->deviceRotation = rotation;
    }
    if (background != impl_->previousBackground) { impl_->file(background, ".png"); impl_->previousBackground = background; }
}
void HidDisplay::overlay(const Bytes& png) {
    if (!isOpen()) throw std::runtime_error("Display not connected");
    impl_->file(png, ".osd");
}
}
