#include "sensors.h"

#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <string>
#include <vector>

#if defined(_MSC_VER)
#include <intrin.h>
#else
#include <cpuid.h>
#endif

// AMD SDK headers are a local build dependency, not redistributed with this project.
// See sensors-vendor/NOTICE.txt. The driver DLL is loaded from Windows System32.
#if defined(PURRLCD_WITH_ADLX) && PURRLCD_WITH_ADLX
#include <ADLX.h>
#include <IPerformanceMonitoring.h>
#endif

namespace purrlcd {
namespace {

std::uint64_t ticks(const FILETIME& time) {
    return (static_cast<std::uint64_t>(time.dwHighDateTime) << 32) |
           time.dwLowDateTime;
}

void addStatus(std::string& status, const std::string& message) {
    if (!status.empty()) status += "; ";
    status += message;
}

bool isSupportedRyzenCpu() {
    unsigned eax = 0, ebx = 0, ecx = 0, edx = 0;
#if defined(_MSC_VER)
    int registers[4] = {};
    __cpuid(registers, 0);
    ebx = static_cast<unsigned>(registers[1]);
    ecx = static_cast<unsigned>(registers[2]);
    edx = static_cast<unsigned>(registers[3]);
#else
    if (!__get_cpuid(0, &eax, &ebx, &ecx, &edx)) return false;
#endif
    // "AuthenticAMD" in CPUID's EBX, EDX, ECX order.
    if (ebx != 0x68747541 || edx != 0x69746e65 || ecx != 0x444d4163) return false;
#if defined(_MSC_VER)
    __cpuid(registers, 1);
    eax = static_cast<unsigned>(registers[0]);
#else
    if (!__get_cpuid(1, &eax, &ebx, &ecx, &edx)) return false;
#endif
    const unsigned baseFamily = (eax >> 8) & 0x0f;
    const unsigned family = baseFamily + (baseFamily == 0x0f ? ((eax >> 20) & 0xff) : 0);
    const unsigned model = ((eax >> 4) & 0x0f) |
        ((baseFamily == 0x06 || baseFamily == 0x0f) ? ((eax >> 12) & 0xf0) : 0);
    // Vermeer only; other CPU families need verified temperature formats and offsets.
    return family == 0x19 && model == 0x21;
}

std::string windowsCode(HRESULT result) {
    return std::to_string(static_cast<unsigned long>(result));
}

class PawnCpu {
    using OpenFn = HRESULT (WINAPI*)(PHANDLE);
    using LoadFn = HRESULT (WINAPI*)(HANDLE, const UCHAR*, SIZE_T);
    using ExecuteFn = HRESULT (WINAPI*)(HANDLE, PCSTR, const ULONG64*, SIZE_T,
                                      PULONG64, SIZE_T, PSIZE_T);
    using CloseFn = HRESULT (WINAPI*)(HANDLE);

    HMODULE library_ = nullptr;
    HANDLE executor_ = nullptr;
    HANDLE pciMutex_ = nullptr;
    ExecuteFn execute_ = nullptr;
    CloseFn close_ = nullptr;
    std::string status_;

    void close() {
        if (executor_ && close_) close_(executor_);
        executor_ = nullptr;
        if (pciMutex_) CloseHandle(pciMutex_);
        pciMutex_ = nullptr;
        execute_ = nullptr;
        close_ = nullptr;
        if (library_) FreeLibrary(library_);
        library_ = nullptr;
    }

    bool initialize() {
        if (!isSupportedRyzenCpu()) {
            status_ = "CPU temperature unavailable: this provider supports AMD Vermeer CPUs only";
            return false;
        }
        wchar_t installDirectory[32768] = {};
        DWORD size = sizeof(installDirectory);
        LONG registryResult = RegGetValueW(HKEY_LOCAL_MACHINE,
            L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\PawnIO",
            L"InstallLocation", RRF_RT_REG_SZ, nullptr,
            installDirectory, &size);
        std::wstring directory;
        if (registryResult == ERROR_SUCCESS && installDirectory[0]) {
            directory = installDirectory;
        } else {
            const DWORD length = GetEnvironmentVariableW(L"ProgramW6432", installDirectory, 32768);
            if (length && length < 32768) directory = std::wstring(installDirectory) + L"\\PawnIO";
        }
        if (directory.empty()) {
            status_ = "CPU temperature unavailable: install official signed PawnIO, then restart agent";
            return false;
        }
        if (directory.back() != L'\\') directory += L'\\';
        const std::wstring libraryPath = directory + L"PawnIOLib.dll";
        library_ = LoadLibraryExW(libraryPath.c_str(), nullptr,
                                 LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_SYSTEM32);
        if (!library_) {
            status_ = "CPU temperature unavailable: install official signed PawnIO, then restart agent";
            return false;
        }
        const auto open = reinterpret_cast<OpenFn>(GetProcAddress(library_, "pawnio_open"));
        const auto load = reinterpret_cast<LoadFn>(GetProcAddress(library_, "pawnio_load"));
        execute_ = reinterpret_cast<ExecuteFn>(GetProcAddress(library_, "pawnio_execute"));
        close_ = reinterpret_cast<CloseFn>(GetProcAddress(library_, "pawnio_close"));
        if (!open || !load || !execute_ || !close_) {
            status_ = "CPU temperature unavailable: installed PawnIO API is incompatible";
            return false;
        }

        wchar_t executable[32768] = {};
        const DWORD length = GetModuleFileNameW(nullptr, executable, 32768);
        if (!length || length >= 32768) {
            status_ = "CPU temperature unavailable: executable path lookup failed";
            return false;
        }
        std::wstring modulePath(executable, length);
        const auto separator = modulePath.find_last_of(L"\\/");
        if (separator == std::wstring::npos) return false;
        modulePath.resize(separator + 1);
        modulePath += L"sensors-vendor\\pawnio\\AMDFamily17.bin";
        const HANDLE file = CreateFileW(modulePath.c_str(), GENERIC_READ, FILE_SHARE_READ,
                                       nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
        if (file == INVALID_HANDLE_VALUE) {
            status_ = "CPU temperature unavailable: signed AMDFamily17.bin module missing beside agent";
            return false;
        }
        LARGE_INTEGER fileSize = {};
        if (!GetFileSizeEx(file, &fileSize) || fileSize.QuadPart <= 0 || fileSize.QuadPart > 1024 * 1024) {
            CloseHandle(file);
            status_ = "CPU temperature unavailable: invalid signed module size";
            return false;
        }
        std::vector<UCHAR> blob(static_cast<std::size_t>(fileSize.QuadPart));
        DWORD bytesRead = 0;
        const BOOL read = ReadFile(file, blob.data(), static_cast<DWORD>(blob.size()), &bytesRead, nullptr);
        CloseHandle(file);
        if (!read || bytesRead != blob.size()) {
            status_ = "CPU temperature unavailable: cannot read signed module";
            return false;
        }
        HRESULT result = open(&executor_);
        if (FAILED(result) || !executor_) {
            status_ = result == E_ACCESSDENIED
                ? "CPU temperature unavailable: run the native agent as administrator to access PawnIO"
                : "CPU temperature unavailable: PawnIO driver cannot open (" + windowsCode(result) + ")";
            return false;
        }
        result = load(executor_, blob.data(), blob.size());
        if (FAILED(result)) {
            status_ = "CPU temperature unavailable: signed module rejected (" + windowsCode(result) + ")";
            return false;
        }
        // The official AMDFamily17 module requires the global Access_PCI mutex
        // around indexed SMN reads, to coexist with other hardware monitors.
        pciMutex_ = OpenMutexW(SYNCHRONIZE | MUTEX_MODIFY_STATE, FALSE, L"Global\\Access_PCI");
        if (!pciMutex_) pciMutex_ = CreateMutexW(nullptr, FALSE, L"Global\\Access_PCI");
        if (!pciMutex_) {
            status_ = "CPU temperature unavailable: cannot acquire global PCI mutex";
            return false;
        }
        status_ = "CPU temperature: PawnIO / Ryzen Tctl/Tdie";
        return true;
    }

public:
    PawnCpu() { if (!initialize()) close(); }
    ~PawnCpu() { close(); }
    PawnCpu(const PawnCpu&) = delete;
    PawnCpu& operator=(const PawnCpu&) = delete;

    void sample(SensorSnapshot& snapshot) {
        if (!executor_ || !execute_ || !pciMutex_) {
            addStatus(snapshot.status, status_);
            return;
        }
        const DWORD wait = WaitForSingleObject(pciMutex_, 100);
        if (wait != WAIT_OBJECT_0 && wait != WAIT_ABANDONED) {
            addStatus(snapshot.status, "CPU temperature unavailable: PCI sensor access busy");
            return;
        }
        const ULONG64 registerAddress = 0x00059800;
        ULONG64 raw = 0;
        SIZE_T count = 0;
        const HRESULT result = execute_(executor_, "ioctl_read_smn", &registerAddress, 1, &raw, 1, &count);
        ReleaseMutex(pciMutex_);
        if (FAILED(result) || count != 1 || raw == 0 || raw > 0xffffffffULL) {
            addStatus(snapshot.status, "CPU temperature unavailable: PawnIO sensor read failed (" + windowsCode(result) + ")");
            return;
        }
        // Vermeer THM_TCON_CUR_TMP: bits 31:21 have 0.125 C units. RANGE_SEL
        // (bit 19) or TJ_SEL=3 (bits 17:16) select the -49 C range adjustment.
        // Register definition is also used by Linux k10temp and LibreHardwareMonitor.
        double temperature = static_cast<double>((raw >> 21) & 0x7ff) * 0.125;
        if ((raw & 0x80000) != 0 || (raw & 0x30000) == 0x30000) temperature -= 49.0;
        if (!std::isfinite(temperature) || temperature < -20 || temperature > 150) {
            addStatus(snapshot.status, "CPU temperature unavailable: sensor value out of range");
            return;
        }
        snapshot.cpuTemp = temperature;
        snapshot.hasCpuTemp = true;
        addStatus(snapshot.status, status_);
    }
};

#if defined(PURRLCD_WITH_ADLX) && PURRLCD_WITH_ADLX
template<class T> void release(T*& value) {
    if (value) value->Release();
    value = nullptr;
}

std::string adlxError(const char* operation, ADLX_RESULT result) {
    return std::string(operation) + " (ADLX " +
           std::to_string(static_cast<int>(result)) + ")";
}
#endif

} // namespace

struct Sensors::Impl {
    PawnCpu cpu;
    std::uint64_t previousIdle = 0;
    std::uint64_t previousKernel = 0;
    std::uint64_t previousUser = 0;
    bool havePrevious = false;
    double previousUsage = 0;
    std::string gpuStatus;

#if defined(PURRLCD_WITH_ADLX) && PURRLCD_WITH_ADLX
    HMODULE driver = nullptr;
    ADLXTerminate_Fn terminate = nullptr;
    adlx::IADLXSystem* system = nullptr; // Owned by ADLXTerminate, not Release().
    adlx::IADLXPerformanceMonitoringServices* performance = nullptr;
    adlx::IADLXGPU* gpu = nullptr;
    bool initialized = false;

    void shutdownGpu() {
        release(gpu);
        release(performance);
        system = nullptr;
        if (initialized && terminate) terminate();
        initialized = false;
        terminate = nullptr;
        if (driver) FreeLibrary(driver);
        driver = nullptr;
    }

    bool initializeGpu() {
        wchar_t systemDirectory[MAX_PATH + 1] = {};
        const UINT length = GetSystemDirectoryW(systemDirectory, MAX_PATH + 1);
        if (!length || length > MAX_PATH) {
            gpuStatus = "GPU temperature unavailable: Windows system directory missing";
            return false;
        }
        const std::wstring path = std::wstring(systemDirectory, length) + L"\\amdadlx64.dll";
        driver = LoadLibraryExW(path.c_str(), nullptr, LOAD_LIBRARY_SEARCH_SYSTEM32);
        if (!driver) {
            gpuStatus = "GPU temperature unavailable: AMD ADLX driver not installed";
            return false;
        }
        const auto initialize = reinterpret_cast<ADLXInitialize_Fn>(
            GetProcAddress(driver, ADLX_INIT_FUNCTION_NAME));
        terminate = reinterpret_cast<ADLXTerminate_Fn>(
            GetProcAddress(driver, ADLX_TERMINATE_FUNCTION_NAME));
        if (!initialize || !terminate) {
            gpuStatus = "GPU temperature unavailable: required AMD ADLX entry points missing";
            return false;
        }
        ADLX_RESULT result = initialize(ADLX_FULL_VERSION, &system);
        if (!ADLX_SUCCEEDED(result) || !system) {
            gpuStatus = adlxError("GPU temperature unavailable: initialization failed", result);
            return false;
        }
        initialized = true;
        result = system->GetPerformanceMonitoringServices(&performance);
        if (!ADLX_SUCCEEDED(result) || !performance) {
            gpuStatus = adlxError("GPU temperature unavailable: monitoring service failed", result);
            return false;
        }
        adlx::IADLXGPUList* list = nullptr;
        result = system->GetGPUs(&list);
        if (!ADLX_SUCCEEDED(result) || !list) {
            release(list);
            gpuStatus = adlxError("GPU temperature unavailable: GPU enumeration failed", result);
            return false;
        }

        // Prefer a discrete GPU. Only select adapters reporting temperature support.
        for (adlx_uint index = list->Begin(); index < list->End(); ++index) {
            adlx::IADLXGPU* candidate = nullptr;
            if (!ADLX_SUCCEEDED(list->At(index, &candidate)) || !candidate) {
                release(candidate);
                continue;
            }
            adlx::IADLXGPUMetricsSupport* support = nullptr;
            adlx_bool supported = false;
            if (ADLX_SUCCEEDED(performance->GetSupportedGPUMetrics(candidate, &support)) && support)
                support->IsSupportedGPUTemperature(&supported);
            release(support);
            if (!supported) {
                release(candidate);
                continue;
            }
            ADLX_GPU_TYPE type = GPUTYPE_UNDEFINED;
            candidate->Type(&type);
            if (!gpu || type == GPUTYPE_DISCRETE) {
                release(gpu);
                gpu = candidate;
                candidate = nullptr;
            }
            release(candidate);
            if (type == GPUTYPE_DISCRETE) break;
        }
        release(list);
        if (!gpu) {
            gpuStatus = "GPU temperature unavailable: no AMD adapter supports this metric";
            return false;
        }
        const char* name = nullptr;
        gpu->Name(&name);
        gpuStatus = "GPU temperature: AMD ADLX";
        if (name && *name) gpuStatus += std::string(" / ") + name;
        return true;
    }
#endif

    Impl() {
        FILETIME idle = {}, kernel = {}, user = {};
        if (GetSystemTimes(&idle, &kernel, &user)) {
            previousIdle = ticks(idle);
            previousKernel = ticks(kernel);
            previousUser = ticks(user);
            havePrevious = true;
        }
#if defined(PURRLCD_WITH_ADLX) && PURRLCD_WITH_ADLX
        if (!initializeGpu()) shutdownGpu();
#else
        gpuStatus = "GPU temperature unavailable: build without AMD ADLX support";
#endif
    }

    ~Impl() {
#if defined(PURRLCD_WITH_ADLX) && PURRLCD_WITH_ADLX
        shutdownGpu();
#endif
    }

    SensorSnapshot sample() {
        SensorSnapshot result;
        // Never substitute an ACPI thermal zone for the CPU package sensor.
        cpu.sample(result);

        FILETIME idle = {}, kernel = {}, user = {};
        if (GetSystemTimes(&idle, &kernel, &user)) {
            const auto nowIdle = ticks(idle);
            const auto nowKernel = ticks(kernel);
            const auto nowUser = ticks(user);
            if (havePrevious && nowIdle >= previousIdle && nowKernel >= previousKernel &&
                nowUser >= previousUser) {
                const auto total = (nowKernel - previousKernel) + (nowUser - previousUser);
                const auto idleDelta = nowIdle - previousIdle;
                if (total) {
                    previousUsage = 100.0 * static_cast<double>(total - (std::min)(total, idleDelta)) /
                                    static_cast<double>(total);
                }
            }
            previousIdle = nowIdle;
            previousKernel = nowKernel;
            previousUser = nowUser;
            havePrevious = true;
            result.cpuUsage = previousUsage;
        } else {
            addStatus(result.status, "CPU usage unavailable: GetSystemTimes failed");
        }

        MEMORYSTATUSEX memory = {};
        memory.dwLength = sizeof(memory);
        if (GlobalMemoryStatusEx(&memory) && memory.ullTotalPhys) {
            result.ramPercent = 100.0 * static_cast<double>(memory.ullTotalPhys - memory.ullAvailPhys) /
                                static_cast<double>(memory.ullTotalPhys);
        } else {
            addStatus(result.status, "RAM usage unavailable: GlobalMemoryStatusEx failed");
        }

#if defined(PURRLCD_WITH_ADLX) && PURRLCD_WITH_ADLX
        if (gpu && performance) {
            adlx::IADLXGPUMetrics* metrics = nullptr;
            ADLX_RESULT readResult = performance->GetCurrentGPUMetrics(gpu, &metrics);
            adlx_double temperature = 0;
            if (ADLX_SUCCEEDED(readResult) && metrics)
                readResult = metrics->GPUTemperature(&temperature);
            if (ADLX_SUCCEEDED(readResult) && metrics && std::isfinite(temperature) &&
                temperature >= -50 && temperature <= 200) {
                result.gpuTemp = temperature;
                result.hasGpuTemp = true;
                addStatus(result.status, gpuStatus);
            } else {
                addStatus(result.status, adlxError("GPU temperature unavailable: reading failed", readResult));
            }
            release(metrics);
        } else {
            addStatus(result.status, gpuStatus);
        }
#else
        addStatus(result.status, gpuStatus);
#endif
        return result;
    }
};

Sensors::Sensors() : impl_(new Impl()) {}
Sensors::~Sensors() = default;
SensorSnapshot Sensors::sample() { return impl_->sample(); }

} // namespace purrlcd
