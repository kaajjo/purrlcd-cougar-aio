#include "common.h"
#include "scene.h"
#include "hid_display.h"
#include "sensors.h"
#include "privilege.h"
#include <shellapi.h>
#include <sddl.h>
#include <psapi.h>
#include <thread>
#include <mutex>
#include <atomic>
#include <chrono>
#include <iostream>

using namespace purrlcd;
namespace {
constexpr UINT TRAY_MESSAGE = WM_APP + 1;
constexpr UINT OPEN_EDITOR = 100, PAUSE_SCREEN = 101, EXIT_APP = 102;
HANDLE stopEvent = nullptr, wakeEvent = nullptr;
HWND appWindow = nullptr;
NOTIFYICONDATAW trayData{};
UINT taskbarCreated = 0;
fs::path exePath, dataPath, editorPath;
std::wstring pipePath;

struct State {
    std::mutex mutex;
    Json scene = defaultScene();
    SensorSnapshot values;
    bool connected = false, wanted = false, stockRunning = false;
    unsigned long sceneRevision = 1;
    uint64_t framesSent = 0, lastFrameMs = 0;
    std::string message = "Ready to configure";
    std::string deviceInfo;
    fs::path previewPath;
} state;
std::mutex logMutex;

void logEvent(const std::string& s) {
    try {
        std::lock_guard<std::mutex> lock(logMutex);
        auto file = dataPath / L"events.log";
        if (fs::exists(file) && fs::file_size(file) > 1024 * 1024) {
            auto old = dataPath / L"events.previous.log";
            MoveFileExW(file.c_str(), old.c_str(), MOVEFILE_REPLACE_EXISTING);
        }
        SYSTEMTIME t{}; GetLocalTime(&t);
        std::ofstream f(file, std::ios::app);
        f << t.wYear << '-' << t.wMonth << '-' << t.wDay << ' ' << t.wHour << ':' << t.wMinute << ':' << t.wSecond << ' ' << s << '\n';
    } catch (...) {}
}

Json snapshotStatus() {
    PROCESS_MEMORY_COUNTERS_EX mem{}; mem.cb = sizeof(mem);
    GetProcessMemoryInfo(GetCurrentProcess(), (PROCESS_MEMORY_COUNTERS*)&mem, sizeof(mem));
    std::lock_guard<std::mutex> lock(state.mutex);
    return {{"connected", state.connected}, {"connecting", state.wanted && !state.connected},
        {"stockRunning", state.stockRunning}, {"cpuTemp", state.values.hasCpuTemp ? Json(state.values.cpuTemp) : Json(nullptr)},
        {"gpuTemp", state.values.hasGpuTemp ? Json(state.values.gpuTemp) : Json(nullptr)},
        {"cpuUsage", state.values.cpuUsage}, {"ramPercent", state.values.ramPercent},
        {"message", state.message}, {"sensorStatus", state.values.status},
        {"framesSent", state.framesSent}, {"lastFrameMs", state.lastFrameMs},
        {"workingSetBytes", mem.WorkingSetSize}, {"privateBytes", mem.PrivateUsage},
        {"deviceInfo", state.deviceInfo}, {"version", "0.1.0"}};
}

void importVendorScene() {
    wchar_t* appdata = _wgetenv(L"APPDATA");
    if (!appdata) return;
    auto vendor = fs::path(appdata) / L"cougar_lcd_editor";
    try {
        auto store = readJson(vendor / L"store.json");
        if (!store.contains("devicesConfig") || store["devicesConfig"].empty()) return;
        const auto& device = store["devicesConfig"].begin().value();
        Json scene = defaultScene();
        scene["rotation"] = device.value("waterBlockScreenRotate", 180);
        auto background = device.value("currentThemeBg", std::string());
        if (!background.empty() && fs::is_regular_file(wide(background))) {
            auto original = fs::path(wide(background));
            auto dst = dataPath / (L"background" + original.extension().wstring());
            fs::copy_file(original, dst, fs::copy_options::overwrite_existing);
            scene["backgroundPath"] = utf8(dst.wstring());
        }
        // Vendor coordinates are in its 510-pixel editor canvas, not physical LCD pixels.
        auto themeId = device.value("currentThemeId", Json());
        for (const auto& theme : store.value("customizationTheme", Json::array())) {
            if (theme.value("id", Json()) != themeId || !theme.contains("antvX6")) continue;
            for (const auto& cell : theme["antvX6"].value("cells", Json::array())) {
                if (!cell.contains("data")) continue;
                const auto& d = cell["data"];
                auto source = d.value("source", "");
                std::string key = source == "CPU Temperature" ? "cpu" : source == "GPU Temperature" ? "gpu" : "";
                if (key.empty()) continue;
                double canvas = d.value("canvasProperties", Json::object()).value("width", 510.0);
                double scale = canvas > 0 ? 720.0 / canvas : 1.0;
                scene[key]["x"] = std::min(710, (int)(d.value("x", 0.0) * scale));
                scene[key]["y"] = std::min(710, (int)(d.value("y", 0.0) * scale));
                scene[key]["fontSize"] = std::min(120, std::max(12, (int)(d.value("fontSize", 35.0) * scale)));
                scene[key]["color"] = "#FFFFFF";
            }
        }
        state.scene = validateScene(scene);
        logEvent("Imported existing background and layer positions; original files unchanged.");
    } catch (const std::exception& e) { logEvent(std::string("Theme import unavailable: ") + e.what()); }
}

void worker() {
    CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    try {
        Sensors sensors;
        Renderer renderer;
        HidDisplay display(stopEvent);
        unsigned long configuredRevision = 0;
        std::string lastKey;
        uint64_t lastSample = 0, lastStockCheck = 0, retryAt = 0, lastKeepalive = 0;
        while (WaitForSingleObject(stopEvent, 0) != WAIT_OBJECT_0) {
            auto now = GetTickCount64();
            Json scene; bool wanted; unsigned long revision;
            {
                std::lock_guard<std::mutex> lock(state.mutex);
                scene = state.scene; wanted = state.wanted; revision = state.sceneRevision;
            }
            int interval = scene["intervalMs"].get<int>();
            if (!lastStockCheck || now - lastStockCheck >= 5000) {
                bool stock = stockEditorRunning();
                std::lock_guard<std::mutex> lock(state.mutex); state.stockRunning = stock;
                if (stock && display.isOpen()) {
                    display.close(); configuredRevision = 0; lastKey.clear();
                    state.connected = false; state.wanted = wanted = false;
                    state.message = "Transfer stopped: COUGAR LCD Editor is running";
                }
                lastStockCheck = now;
            }
            if (!lastSample || now - lastSample >= (uint64_t)interval) {
                SensorSnapshot sample = sensors.sample();
                { std::lock_guard<std::mutex> lock(state.mutex); state.values = sample; }
                lastSample = now;
            }
            if (!wanted && display.isOpen()) {
                display.close(); configuredRevision = 0; lastKey.clear();
                std::lock_guard<std::mutex> lock(state.mutex);
                state.connected = false; state.message = "Transfer stopped";
            }
            if (wanted && !display.isOpen() && now >= retryAt) {
                if (stockEditorRunning()) {
                    std::lock_guard<std::mutex> lock(state.mutex);
                    state.stockRunning = true; state.wanted = false;
                    state.message = "Exit COUGAR LCD Editor from its system tray menu, then connect the display";
                } else {
                    try {
                        Json info = display.open();
                        { std::lock_guard<std::mutex> lock(state.mutex);
                          state.deviceInfo = info.dump(); state.message = "Preparing display..."; }
                        display.configure(renderer.background(scene), scene["rotation"].get<int>());
                        configuredRevision = revision; lastKey.clear();
                        std::lock_guard<std::mutex> lock(state.mutex);
                        state.connected = true; state.message = "Display connected";
                        atomicWriteJson(dataPath / L"agent.json", {{"connectOnStartup", true}});
                        logEvent("LCD connected.");
                    } catch (const std::exception& e) {
                        display.close(); retryAt = now + 10000;
                        std::lock_guard<std::mutex> lock(state.mutex);
                        if (dynamic_cast<const std::invalid_argument*>(&e)) state.wanted = false;
                        state.connected = false; state.message = std::string(e.what()) + (state.wanted ? " · retrying in 10 seconds" : "");
                        logEvent(std::string("LCD connection: ") + e.what());
                    }
                }
            }
            if (WaitForSingleObject(stopEvent, 0) == WAIT_OBJECT_0) break;
            if (display.isOpen()) {
                try {
                    SensorSnapshot values;
                    { std::lock_guard<std::mutex> lock(state.mutex); values = state.values; }
                    if (configuredRevision != revision) {
                        display.configure(renderer.background(scene), scene["rotation"].get<int>());
                        configuredRevision = revision; lastKey.clear();
                    }
                    auto key = visualKey(scene, values);
                    // Refresh unchanged frames every 20 seconds to prevent the device's 60-second timeout.
                    if (key != lastKey || now - lastKeepalive >= 20000) {
                        display.overlay(renderer.render(scene, values, true));
                        lastKey = key; lastKeepalive = now;
                        std::lock_guard<std::mutex> lock(state.mutex);
                        ++state.framesSent; state.lastFrameMs = now;
                    }
                } catch (const std::exception& e) {
                    display.close(); retryAt = GetTickCount64() + 10000;
                    std::lock_guard<std::mutex> lock(state.mutex);
                    if (dynamic_cast<const std::invalid_argument*>(&e)) state.wanted = false;
                    state.connected = false; state.message = std::string(e.what()) + (state.wanted ? " · retrying in 10 seconds" : "");
                    logEvent(std::string("LCD transfer: ") + e.what());
                }
            }
            HANDLE events[] = {stopEvent, wakeEvent};
            auto elapsed = GetTickCount64() - lastSample;
            DWORD sleepMs = elapsed >= (uint64_t)interval ? 1 : (DWORD)(interval - elapsed);
            WaitForMultipleObjects(2, events, FALSE, sleepMs);
        }
        display.close();
    } catch (const std::exception& e) {
        std::lock_guard<std::mutex> lock(state.mutex);
        state.message = e.what(); state.connected = false;
        logEvent(std::string("Worker stopped: ") + e.what());
    }
    CoUninitialize();
}

Json handleRequest(const Json& request, Renderer& renderer) {
    std::string command = request.at("command").get<std::string>();
    Json response{{"ok", true}};
    if (command == "status") response["status"] = snapshotStatus();
    else if (command == "getScene") {
        std::lock_guard<std::mutex> lock(state.mutex); response["scene"] = state.scene;
    } else if (command == "saveScene") {
        Json scene = validateScene(request.at("scene"));
        // Decode before committing, so invalid images cannot break an existing saved scene.
        auto normalizedBackground = renderer.background(scene);
        auto path = scene["backgroundPath"].get<std::string>();
        if (!path.empty()) {
            auto target = fs::absolute(dataPath / L"background.png");
            atomicWrite(target, normalizedBackground);
            scene["backgroundPath"] = utf8(target.wstring());
        }
        atomicWriteJson(dataPath / L"scene.json", scene);
        { std::lock_guard<std::mutex> lock(state.mutex); state.scene = scene; ++state.sceneRevision; }
        response["scene"] = scene; SetEvent(wakeEvent);
    } else if (command == "preview") {
        Json scene;
        SensorSnapshot values;
        { std::lock_guard<std::mutex> lock(state.mutex); scene = state.scene; values = state.values; }
        if (request.contains("scene")) scene = validateScene(request.at("scene"));
        static unsigned counter = 0;
        auto file = dataPath / (L"preview-" + std::to_wstring(++counter % 3) + L".png");
        atomicWrite(file, renderer.render(scene, values));
        response["previewPath"] = utf8(file.wstring()); response["revision"] = counter;
        response["status"] = snapshotStatus();
    } else if (command == "connect") {
        bool stock = stockEditorRunning();
        { std::lock_guard<std::mutex> lock(state.mutex);
          state.stockRunning = stock;
          if (stock) throw std::runtime_error("Exit COUGAR LCD Editor from its system tray menu before connecting");
          state.wanted = true; state.message = "Connecting to display..."; }
        SetEvent(wakeEvent); response["status"] = snapshotStatus();
    } else if (command == "disconnect") {
        atomicWriteJson(dataPath / L"agent.json", {{"connectOnStartup", false}});
        { std::lock_guard<std::mutex> lock(state.mutex); state.wanted = false; }
        SetEvent(wakeEvent); response["status"] = snapshotStatus();
    } else if (command == "quit") {
        if (appWindow) PostMessageW(appWindow, WM_CLOSE, 0, 0);
    } else throw std::runtime_error("Unknown command");
    return response;
}

bool pipeIo(HANDLE pipe, void* buffer, DWORD length, bool write) {
    auto* bytes = (uint8_t*)buffer;
    while (length) {
        OVERLAPPED op{}; op.hEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
        if (!op.hEvent) return false;
        DWORD done = 0;
        BOOL ok = write ? WriteFile(pipe, bytes, length, &done, &op) : ReadFile(pipe, bytes, length, &done, &op);
        if (!ok && GetLastError() == ERROR_IO_PENDING) {
            HANDLE events[]{stopEvent, op.hEvent};
            DWORD result = WaitForMultipleObjects(2, events, FALSE, 3000);
            if (result == WAIT_OBJECT_0 + 1) ok = GetOverlappedResult(pipe, &op, &done, FALSE);
            else { CancelIoEx(pipe, &op); GetOverlappedResult(pipe, &op, &done, TRUE); ok = FALSE; }
        }
        CloseHandle(op.hEvent);
        if (!ok || !done) return false;
        bytes += done; length -= done;
    }
    return true;
}

void pipeServer() {
    CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    PSECURITY_DESCRIPTOR descriptor = nullptr;
    try {
        HANDLE token = nullptr;
        if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token)) throw std::runtime_error("Cannot query process token");
        DWORD size = 0; GetTokenInformation(token, TokenUser, nullptr, 0, &size);
        std::vector<uint8_t> tokenData(size);
        BOOL tokenOk = GetTokenInformation(token, TokenUser, tokenData.data(), size, &size);
        CloseHandle(token);
        if (!tokenOk) throw std::runtime_error("Cannot read user SID");
        LPWSTR sid = nullptr;
        if (!ConvertSidToStringSidW(((TOKEN_USER*)tokenData.data())->User.Sid, &sid)) throw std::runtime_error("Cannot format SID");
        std::wstring sddl = L"D:P(A;;GA;;;SY)(A;;GA;;;" + std::wstring(sid) + L")S:(ML;;NW;;;ME)";
        LocalFree(sid);
        if (!ConvertStringSecurityDescriptorToSecurityDescriptorW(sddl.c_str(), SDDL_REVISION_1, &descriptor, nullptr))
            throw std::runtime_error("Cannot secure IPC pipe");
        SECURITY_ATTRIBUTES sa{sizeof(sa), descriptor, FALSE};
        Renderer renderer;
        while (WaitForSingleObject(stopEvent, 0) != WAIT_OBJECT_0) {
            HANDLE pipe = CreateNamedPipeW(pipePath.c_str(), PIPE_ACCESS_DUPLEX | FILE_FLAG_OVERLAPPED | FILE_FLAG_FIRST_PIPE_INSTANCE,
                PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT | PIPE_REJECT_REMOTE_CLIENTS, 1, 65536, 65536, 3000, &sa);
            if (pipe == INVALID_HANDLE_VALUE) throw std::runtime_error("Cannot create IPC pipe: " + errorText());
            OVERLAPPED op{}; op.hEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
            BOOL connected = ConnectNamedPipe(pipe, &op);
            DWORD err = connected ? ERROR_SUCCESS : GetLastError();
            if (err == ERROR_PIPE_CONNECTED) connected = TRUE;
            else if (err == ERROR_IO_PENDING) {
                HANDLE events[]{stopEvent, op.hEvent};
                if (WaitForMultipleObjects(2, events, FALSE, INFINITE) == WAIT_OBJECT_0 + 1) {
                    DWORD n = 0; connected = GetOverlappedResult(pipe, &op, &n, FALSE);
                } else { CancelIoEx(pipe, &op); DWORD n = 0; GetOverlappedResult(pipe, &op, &n, TRUE); }
            }
            CloseHandle(op.hEvent);
            if (connected) {
                uint32_t length = 0;
                if (pipeIo(pipe, &length, 4, false) && length > 0 && length <= 1024 * 1024) {
                    std::string request(length, 0);
                    if (pipeIo(pipe, &request[0], length, false)) {
                        Json response;
                        try {
                            if (!ImpersonateNamedPipeClient(pipe)) throw std::runtime_error("Cannot identify IPC client");
                            struct Revert { ~Revert() { RevertToSelf(); } } revert;
                            response = handleRequest(Json::parse(request), renderer);
                        }
                        catch (const std::exception& e) { response = {{"ok", false}, {"error", e.what()}}; }
                        auto text = response.dump(); uint32_t n = (uint32_t)text.size();
                        if (pipeIo(pipe, &n, 4, true) && pipeIo(pipe, &text[0], n, true)) {
                            // Keep the pipe alive until the client consumes its response and
                            // closes. A bounded read prevents an idle client blocking the server.
                            uint8_t ignored = 0; pipeIo(pipe, &ignored, 1, false);
                        }
                    }
                }
            }
            DisconnectNamedPipe(pipe); CloseHandle(pipe);
        }
    } catch (const std::exception& e) {
        logEvent(std::string("IPC error: ") + e.what());
        if (appWindow) PostMessageW(appWindow, WM_CLOSE, 0, 0);
    }
    if (descriptor) LocalFree(descriptor);
    CoUninitialize();
}

void launchEditor() {
    if (!fs::exists(editorPath)) {
        MessageBoxW(appWindow, L"Editor not found next to the application. Run PurrLCDEditor.exe from the editor folder.", L"PurrLCD", MB_OK | MB_ICONINFORMATION);
        return;
    }
    auto args = L"--engine \"" + exePath.wstring() + L"\" --data \"" + dataPath.wstring() + L"\"";
    std::wstring error;
    if (!launchUnelevated(editorPath.wstring(), args, editorPath.parent_path().wstring(), &error)) {
        logEvent("Cannot launch editor: " + utf8(error));
        MessageBoxW(appWindow, (L"Could not open the editor. Run PurrLCDEditor.exe manually.\n" + error).c_str(), L"PurrLCD", MB_OK | MB_ICONINFORMATION);
    }
}

HICON createTrayIcon() {
    constexpr int n = 32;
    BITMAPV5HEADER h{}; h.bV5Size = sizeof(h); h.bV5Width = n; h.bV5Height = -n;
    h.bV5Planes = 1; h.bV5BitCount = 32; h.bV5Compression = BI_BITFIELDS;
    h.bV5RedMask = 0x00FF0000; h.bV5GreenMask = 0x0000FF00; h.bV5BlueMask = 0x000000FF; h.bV5AlphaMask = 0xFF000000;
    uint32_t* pixels = nullptr;
    HDC dc = GetDC(nullptr);
    HBITMAP bitmap = CreateDIBSection(dc, (BITMAPINFO*)&h, DIB_RGB_COLORS, (void**)&pixels, nullptr, 0);
    ReleaseDC(nullptr, dc);
    if (!bitmap) return LoadIconW(nullptr, IDI_APPLICATION);
    for (int y = 0; y < n; ++y) for (int x = 0; x < n; ++x) {
        bool edge = x >= 4 && x <= 27 && y >= 5 && y <= 26;
        bool inner = x >= 8 && x <= 23 && y >= 9 && y <= 22;
        bool bar = x >= 11 && x <= 20 && (y == 14 || y == 15 || y == 19);
        pixels[y*n+x] = edge && (!inner || bar) ? 0xFFFF8A45 : 0;
    }
    HBITMAP mask = CreateBitmap(n, n, 1, 1, nullptr);
    ICONINFO info{}; info.fIcon = TRUE; info.hbmColor = bitmap; info.hbmMask = mask;
    HICON icon = CreateIconIndirect(&info); DeleteObject(bitmap); DeleteObject(mask);
    return icon;
}

LRESULT CALLBACK windowProc(HWND h, UINT msg, WPARAM wp, LPARAM lp) {
    if (taskbarCreated && msg == taskbarCreated) { Shell_NotifyIconW(NIM_ADD, &trayData); return 0; }
    if (msg == TRAY_MESSAGE) {
        if (LOWORD(lp) == WM_LBUTTONDBLCLK || LOWORD(lp) == NIN_SELECT) launchEditor();
        if (LOWORD(lp) == WM_RBUTTONUP || LOWORD(lp) == WM_CONTEXTMENU) {
            HMENU menu = CreatePopupMenu();
            AppendMenuW(menu, MF_STRING, OPEN_EDITOR, L"Open editor");
            AppendMenuW(menu, MF_STRING, PAUSE_SCREEN, L"Stop transfer");
            AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
            AppendMenuW(menu, MF_STRING, EXIT_APP, L"Exit PurrLCD");
            POINT p{}; GetCursorPos(&p); SetForegroundWindow(h);
            TrackPopupMenu(menu, TPM_RIGHTBUTTON, p.x, p.y, 0, h, nullptr);
            DestroyMenu(menu); PostMessageW(h, WM_NULL, 0, 0);
        }
        return 0;
    }
    if (msg == WM_COMMAND) {
        if (LOWORD(wp) == OPEN_EDITOR) launchEditor();
        if (LOWORD(wp) == PAUSE_SCREEN) {
            { std::lock_guard<std::mutex> lock(state.mutex); state.wanted = false; SetEvent(wakeEvent); }
            try { atomicWriteJson(dataPath / L"agent.json", {{"connectOnStartup", false}}); }
            catch (const std::exception& e) { logEvent(e.what()); }
        }
        if (LOWORD(wp) == EXIT_APP) DestroyWindow(h);
        return 0;
    }
    if (msg == WM_CLOSE) { DestroyWindow(h); return 0; }
    if (msg == WM_DESTROY) { SetEvent(stopEvent); PostQuitMessage(0); return 0; }
    return DefWindowProcW(h, msg, wp, lp);
}
}

int wmain(int argc, wchar_t** argv) {
    try {
        if (!isElevated()) return relaunchElevated(argc, argv);
        wchar_t executable[32768]; GetModuleFileNameW(nullptr, executable, 32768);
        exePath = executable;
        dataPath = exePath.parent_path().parent_path() / L"data";
        editorPath = exePath.parent_path().parent_path() / L"editor" / L"PurrLCDEditor.exe";
        bool openUi = false, diagnostic = false;
        for (int i = 1; i < argc; ++i) {
            std::wstring arg = argv[i];
            if (arg == L"--data" && i + 1 < argc) dataPath = fs::absolute(argv[++i]);
            else if (arg == L"--editor" && i + 1 < argc) editorPath = fs::absolute(argv[++i]);
            else if (arg == L"--open") openUi = true;
            else if (arg == L"--diagnostic") diagnostic = true;
        }
        if (diagnostic) {
            CoInitializeEx(nullptr, COINIT_MULTITHREADED);
            { Sensors sensors; Sleep(1000); auto v = sensors.sample();
              Json j{{"cpuTemp", v.hasCpuTemp ? Json(v.cpuTemp) : Json(nullptr)}, {"gpuTemp", v.hasGpuTemp ? Json(v.gpuTemp) : Json(nullptr)},
                   {"cpuUsage", v.cpuUsage}, {"ramPercent", v.ramPercent}, {"sensorStatus", v.status}, {"stockRunning", stockEditorRunning()}};
              std::cout << j.dump(2) << std::endl; }
            CoUninitialize(); return 0;
        }
        auto user = username();
        HANDLE singleton = CreateMutexW(nullptr, FALSE, (L"Local\\PurrLCD-" + user).c_str());
        if (!singleton) return 1;
        if (GetLastError() == ERROR_ALREADY_EXISTS) { if (openUi) launchEditor(); CloseHandle(singleton); return 0; }
        pipePath = L"\\\\.\\pipe\\PurrLCD-" + user;
        fs::create_directories(dataPath);
        auto sceneFile = dataPath / L"scene.json";
        if (fs::exists(sceneFile)) {
            try {
                auto saved = readJson(sceneFile);
                auto oldBackground = saved.value("backgroundPath", "");
                if (!oldBackground.empty()) {
                    auto name = fs::path(wide(oldBackground)).filename();
                    auto relocated = dataPath / name;
                    if (name.wstring().find(L"background.") == 0 && fs::is_regular_file(relocated))
                        saved["backgroundPath"] = utf8(fs::absolute(relocated).wstring());
                }
                state.scene = validateScene(saved);
            }
            catch (const std::exception& e) { logEvent(std::string("Saved scene invalid; default loaded: ") + e.what()); }
        } else { importVendorScene(); atomicWriteJson(sceneFile, state.scene); }
        try {
            if (fs::exists(dataPath / L"agent.json")) state.wanted = readJson(dataPath / L"agent.json").value("connectOnStartup", false);
        } catch (const std::exception& e) { logEvent(std::string("Agent settings ignored: ") + e.what()); }
        state.stockRunning = stockEditorRunning();
        stopEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
        wakeEvent = CreateEventW(nullptr, FALSE, FALSE, nullptr);
        HINSTANCE instance = GetModuleHandleW(nullptr);
        WNDCLASSW wc{}; wc.hInstance = instance; wc.lpfnWndProc = windowProc; wc.lpszClassName = L"PurrLCDBackground";
        RegisterClassW(&wc);
        taskbarCreated = RegisterWindowMessageW(L"TaskbarCreated");
        appWindow = CreateWindowW(wc.lpszClassName, L"PurrLCD", 0, 0, 0, 0, 0, nullptr, nullptr, instance, nullptr);
        if (!appWindow) throw std::runtime_error("Cannot create tray window");
        NOTIFYICONDATAW tray{}; tray.cbSize = sizeof(tray); tray.hWnd = appWindow; tray.uID = 1;
        tray.uFlags = NIF_ICON | NIF_MESSAGE | NIF_TIP; tray.uCallbackMessage = TRAY_MESSAGE;
        tray.hIcon = createTrayIcon(); wcscpy_s(tray.szTip, L"PurrLCD — double-click to open the editor");
        trayData = tray;
        Shell_NotifyIconW(NIM_ADD, &tray);
        std::thread sensorThread(worker), ipcThread(pipeServer);
        logEvent("Agent started.");
        if (openUi) launchEditor();
        MSG message{}; while (GetMessageW(&message, nullptr, 0, 0) > 0) { TranslateMessage(&message); DispatchMessageW(&message); }
        SetEvent(stopEvent); SetEvent(wakeEvent);
        ipcThread.join(); sensorThread.join();
        Shell_NotifyIconW(NIM_DELETE, &tray); DestroyIcon(tray.hIcon);
        CloseHandle(stopEvent); CloseHandle(wakeEvent); CloseHandle(singleton);
        logEvent("Agent stopped.");
        return 0;
    } catch (const std::exception& e) {
        std::cerr << e.what() << std::endl;
        MessageBoxW(nullptr, wide(e.what()).c_str(), L"PurrLCD", MB_OK | MB_ICONERROR);
        return 1;
    }
}

int WINAPI wWinMain(HINSTANCE, HINSTANCE, PWSTR, int) {
    int argc = 0;
    wchar_t** argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    if (!argv) return 1;
    int result = wmain(argc, argv);
    LocalFree(argv);
    return result;
}
