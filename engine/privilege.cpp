#include "privilege.h"
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include <shellapi.h>
#include <shlobj.h>
#include <shldisp.h>
#include <exdisp.h>
#include <servprov.h>
#include <vector>
#include <cwchar>

namespace purrlcd {
namespace {
template<class T> struct ComPtr {
    T* value = nullptr;
    ~ComPtr() { if (value) value->Release(); }
    T* operator->() const { return value; }
    T** address() { return &value; }
    ComPtr() = default;
    ComPtr(const ComPtr&) = delete;
    ComPtr& operator=(const ComPtr&) = delete;
};
struct ComApartment {
    HRESULT result = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    ~ComApartment() { if (SUCCEEDED(result)) CoUninitialize(); }
};
struct AutoVariant {
    VARIANT value{};
    ~AutoVariant() { VariantClear(&value); }
    bool text(const std::wstring& input) {
        value.vt = VT_BSTR;
        value.bstrVal = SysAllocStringLen(input.data(), static_cast<UINT>(input.size()));
        return value.bstrVal != nullptr;
    }
};
bool failure(std::wstring* error, const wchar_t* stage, HRESULT result) {
    if (error) {
        wchar_t code[16]{};
        std::swprintf(code, 16, L"0x%08lX", static_cast<unsigned long>(result));
        *error = std::wstring(stage) + L" (" + code + L")";
    }
    return false;
}
bool elevatedToken(HANDLE token, bool& elevated) {
    TOKEN_ELEVATION value{};
    DWORD size = 0;
    if (!GetTokenInformation(token, TokenElevation, &value, sizeof(value), &size)) return false;
    elevated = value.TokenIsElevated != 0;
    return true;
}
std::wstring quoteArgument(const std::wstring& input) {
    std::wstring result = L"\"";
    size_t slashes = 0;
    for (wchar_t ch : input) {
        if (ch == L'\\') { ++slashes; continue; }
        result.append(slashes * (ch == L'\"' ? 2 : 1), L'\\');
        slashes = 0;
        if (ch == L'\"') result += L'\\';
        result += ch;
    }
    result.append(slashes * 2, L'\\');
    result += L'\"';
    return result;
}
}

bool isElevated() {
    HANDLE token = nullptr;
    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token)) return false;
    bool elevated = false;
    elevatedToken(token, elevated);
    CloseHandle(token);
    return elevated;
}

int relaunchElevated(int argc, wchar_t** argv) {
    wchar_t executable[32768]{};
    const DWORD length = GetModuleFileNameW(nullptr, executable, 32768);
    if (!length || length >= 32768) return ERROR_INSUFFICIENT_BUFFER;
    std::wstring args;
    for (int index = 1; index < argc; ++index) {
        if (!args.empty()) args += L' ';
        args += quoteArgument(argv[index]);
    }
    const std::wstring path(executable, length);
    const auto separator = path.find_last_of(L"\\/");
    wchar_t currentDirectory[32768]{};
    const DWORD directoryLength = GetCurrentDirectoryW(32768, currentDirectory);
    const std::wstring directory = directoryLength && directoryLength < 32768
        ? std::wstring(currentDirectory, directoryLength) : path.substr(0, separator);
    SHELLEXECUTEINFOW request{};
    request.cbSize = sizeof(request);
    request.fMask = SEE_MASK_NOCLOSEPROCESS | SEE_MASK_FLAG_NO_UI;
    request.lpVerb = L"runas";
    request.lpFile = path.c_str();
    request.lpParameters = args.c_str();
    request.lpDirectory = directory.c_str();
    request.nShow = SW_HIDE;
    if (!ShellExecuteExW(&request)) return static_cast<int>(GetLastError());
    if (request.hProcess) CloseHandle(request.hProcess);
    return 0;
}

bool launchUnelevated(const std::wstring& executable, const std::wstring& arguments,
                      const std::wstring& workingDirectory, std::wstring* error) {
    if (error) error->clear();
    // Only use the shell actually hosting this interactive desktop. Constructing
    // a fresh Shell.Application object can keep the caller's elevated token.
    HWND shellWindow = GetShellWindow();
    if (!shellWindow) return failure(error, L"Desktop Explorer is unavailable", E_FAIL);
    DWORD processId = 0;
    GetWindowThreadProcessId(shellWindow, &processId);
    HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, processId);
    if (!process) return failure(error, L"Cannot inspect desktop Explorer", HRESULT_FROM_WIN32(GetLastError()));
    HANDLE token = nullptr;
    const BOOL tokenOpened = OpenProcessToken(process, TOKEN_QUERY, &token);
    const DWORD tokenError = GetLastError();
    CloseHandle(process);
    if (!tokenOpened) return failure(error, L"Cannot inspect Explorer token", HRESULT_FROM_WIN32(tokenError));
    bool elevated = true;
    const bool tokenRead = elevatedToken(token, elevated);
    const DWORD readError = GetLastError();
    CloseHandle(token);
    if (!tokenRead) return failure(error, L"Cannot read Explorer token", HRESULT_FROM_WIN32(readError));
    if (elevated) return failure(error, L"Desktop Explorer is elevated; launch editor normally", E_ACCESSDENIED);

    ComApartment apartment;
    if (FAILED(apartment.result) && apartment.result != RPC_E_CHANGED_MODE)
        return failure(error, L"Cannot initialize shell automation", apartment.result);
    ComPtr<IShellWindows> windows;
    HRESULT result = CoCreateInstance(CLSID_ShellWindows, nullptr, CLSCTX_LOCAL_SERVER,
                                     IID_IShellWindows, reinterpret_cast<void**>(windows.address()));
    if (FAILED(result)) return failure(error, L"Cannot find Explorer windows", result);
    VARIANT location{}; location.vt = VT_I4; location.lVal = CSIDL_DESKTOP;
    VARIANT empty{};
    long desktopHandle = 0;
    ComPtr<IDispatch> desktop;
    result = windows->FindWindowSW(&location, &empty, SWC_DESKTOP, &desktopHandle,
                                   SWFO_NEEDDISPATCH, desktop.address());
    if (FAILED(result) || !desktop.value) return failure(error, L"Cannot find Explorer desktop", FAILED(result) ? result : E_FAIL);
    ComPtr<IServiceProvider> services;
    result = desktop->QueryInterface(IID_IServiceProvider, reinterpret_cast<void**>(services.address()));
    if (FAILED(result)) return failure(error, L"Cannot query Explorer services", result);
    ComPtr<IShellBrowser> browser;
    result = services->QueryService(SID_STopLevelBrowser, IID_IShellBrowser, reinterpret_cast<void**>(browser.address()));
    if (FAILED(result)) return failure(error, L"Cannot query desktop browser", result);
    ComPtr<IShellView> view;
    result = browser->QueryActiveShellView(view.address());
    if (FAILED(result)) return failure(error, L"Cannot query desktop view", result);
    ComPtr<IDispatch> viewDispatch;
    result = view->GetItemObject(SVGIO_BACKGROUND, IID_IDispatch, reinterpret_cast<void**>(viewDispatch.address()));
    if (FAILED(result)) return failure(error, L"Cannot query desktop automation", result);
    ComPtr<IShellFolderViewDual> folder;
    result = viewDispatch->QueryInterface(IID_IShellFolderViewDual, reinterpret_cast<void**>(folder.address()));
    if (FAILED(result)) return failure(error, L"Cannot query desktop folder", result);
    ComPtr<IDispatch> application;
    result = folder->get_Application(application.address());
    if (FAILED(result)) return failure(error, L"Cannot query Explorer application", result);
    ComPtr<IShellDispatch2> shell;
    result = application->QueryInterface(IID_IShellDispatch2, reinterpret_cast<void**>(shell.address()));
    if (FAILED(result)) return failure(error, L"Cannot query Explorer launcher", result);

    AutoVariant file, parameters, directory, operation, show;
    if (!file.text(executable) || !parameters.text(arguments) || !directory.text(workingDirectory) || !operation.text(L"open"))
        return failure(error, L"Cannot allocate launch parameters", E_OUTOFMEMORY);
    show.value.vt = VT_I4; show.value.lVal = SW_SHOWNORMAL;
    result = shell->ShellExecute(file.value.bstrVal, parameters.value, directory.value, operation.value, show.value);
    if (FAILED(result)) return failure(error, L"Explorer could not launch editor", result);
    return true;
}
}
