#pragma once

#include <memory>
#include <string>

namespace purrlcd {

struct SensorSnapshot {
    bool hasCpuTemp = false;
    bool hasGpuTemp = false;
    double cpuTemp = 0;
    double gpuTemp = 0;
    double cpuUsage = 0;
    double ramPercent = 0;
    std::string status;
};

// Construct, sample and destroy on the agent's sensor thread. One instance per process.
// A missing temperature is represented by its has* flag, never a synthetic value.
class Sensors {
public:
    Sensors();
    ~Sensors();
    Sensors(const Sensors&) = delete;
    Sensors& operator=(const Sensors&) = delete;
    SensorSnapshot sample();

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace purrlcd
