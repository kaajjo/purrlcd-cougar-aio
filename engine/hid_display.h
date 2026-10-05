#pragma once
#include "common.h"
#include <memory>
namespace purrlcd {
bool stockEditorRunning();
class HidDisplay {
public:
    explicit HidDisplay(HANDLE stopEvent = nullptr);
    ~HidDisplay();
    Json open();
    void close();
    bool isOpen() const;
    void configure(const std::vector<uint8_t>& backgroundPng, int rotation);
    void overlay(const std::vector<uint8_t>& png);
private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};
}
