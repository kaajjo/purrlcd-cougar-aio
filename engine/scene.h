#pragma once
#include "common.h"
#include "sensors.h"

namespace purrlcd {
Json defaultScene();
Json validateScene(const Json& input);
std::string visualKey(const Json& scene, const SensorSnapshot& sensors);
class Renderer {
public:
    Renderer();
    ~Renderer();
    std::vector<uint8_t> render(const Json& scene, const SensorSnapshot& values, bool overlayOnly = false);
    std::vector<uint8_t> background(const Json& scene);
private:
    ULONG_PTR token_ = 0;
};
}
