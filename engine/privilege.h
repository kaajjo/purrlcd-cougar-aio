#pragma once
#include <string>

namespace purrlcd {
bool isElevated();
// Starts a new copy through the normal Windows consent prompt. Returns zero
// after dispatch or the Win32 error (ERROR_CANCELLED when consent is declined).
int relaunchElevated(int argc, wchar_t** argv);
// Uses the existing desktop Explorer, never an elevated ShellExecute fallback.
bool launchUnelevated(const std::wstring& executable, const std::wstring& arguments,
                      const std::wstring& workingDirectory, std::wstring* error = nullptr);
}
