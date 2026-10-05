#pragma once
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include <string>
#include <vector>
#include <stdexcept>
#include <fstream>
#include <filesystem>
#include "third_party/json.hpp"

namespace purrlcd {
using Json = nlohmann::json;
namespace fs = std::filesystem;
inline std::wstring wide(const std::string& s) {
    if (s.empty()) return {};
    int n = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, s.data(), (int)s.size(), nullptr, 0);
    if (!n) throw std::runtime_error("Invalid UTF-8");
    std::wstring out(n, 0);
    MultiByteToWideChar(CP_UTF8, 0, s.data(), (int)s.size(), &out[0], n);
    return out;
}
inline std::string utf8(const std::wstring& s) {
    if (s.empty()) return {};
    int n = WideCharToMultiByte(CP_UTF8, 0, s.data(), (int)s.size(), nullptr, 0, nullptr, nullptr);
    std::string out(n, 0);
    WideCharToMultiByte(CP_UTF8, 0, s.data(), (int)s.size(), &out[0], n, nullptr, nullptr);
    return out;
}
inline std::string errorText(DWORD e = GetLastError()) {
    wchar_t* p = nullptr;
    FormatMessageW(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                   nullptr, e, 0, (LPWSTR)&p, 0, nullptr);
    std::string s = p ? utf8(p) : "Windows error " + std::to_string(e);
    if (p) LocalFree(p);
    return s;
}
inline std::vector<uint8_t> readBytes(const fs::path& p, size_t max = 20 * 1024 * 1024) {
    std::ifstream f(p, std::ios::binary | std::ios::ate);
    if (!f) throw std::runtime_error("Cannot open file: " + utf8(p.wstring()));
    auto length = f.tellg();
    if (length < 0 || (uint64_t)length > max) throw std::runtime_error("File is too large");
    std::vector<uint8_t> data((size_t)length);
    f.seekg(0); f.read((char*)data.data(), data.size());
    if (!f && !data.empty()) throw std::runtime_error("Cannot read file");
    return data;
}
inline void atomicWrite(const fs::path& p, const std::vector<uint8_t>& data) {
    fs::create_directories(p.parent_path());
    auto tmp = p; tmp += L".tmp";
    { std::ofstream f(tmp, std::ios::binary | std::ios::trunc);
      if (!f) throw std::runtime_error("Cannot write file");
      f.write((const char*)data.data(), data.size()); f.flush();
      if (!f) throw std::runtime_error("Cannot save file"); }
    if (!MoveFileExW(tmp.c_str(), p.c_str(), MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH))
        throw std::runtime_error("Cannot commit file: " + errorText());
}
inline void atomicWriteJson(const fs::path& p, const Json& j) {
    auto s = j.dump(2); atomicWrite(p, std::vector<uint8_t>(s.begin(), s.end()));
}
inline Json readJson(const fs::path& p) {
    auto b = readBytes(p, 1024 * 1024);
    return Json::parse(b.begin(), b.end());
}
inline std::wstring username() {
    wchar_t name[256]; DWORD n = 256;
    if (!GetUserNameW(name, &n)) throw std::runtime_error("Cannot identify current user");
    return name;
}
}
