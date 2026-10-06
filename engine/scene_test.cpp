#include "scene.h"
#include <gdiplus.h>
#include <objidl.h>
#include <iostream>
#include <cstring>

using namespace purrlcd;
using namespace Gdiplus;
struct Pixels { int width, height; std::vector<ARGB> values; };
static Pixels decode(const std::vector<uint8_t>& bytes) {
    IStream* stream = nullptr;
    if (FAILED(CreateStreamOnHGlobal(nullptr, TRUE, &stream))) throw std::runtime_error("stream");
    stream->Write(bytes.data(), (ULONG)bytes.size(), nullptr);
    LARGE_INTEGER zero{}; stream->Seek(zero, STREAM_SEEK_SET, nullptr);
    std::unique_ptr<Bitmap> image(Bitmap::FromStream(stream));
    if (!image || image->GetLastStatus() != Ok) throw std::runtime_error("decode");
    Pixels out{(int)image->GetWidth(), (int)image->GetHeight(), {}};
    out.values.resize(out.width * out.height);
    Rect rect(0, 0, out.width, out.height); BitmapData data{};
    if (image->LockBits(&rect, ImageLockModeRead, PixelFormat32bppARGB, &data) != Ok)
        throw std::runtime_error("pixels");
    for (int y = 0; y < out.height; ++y)
        std::memcpy(out.values.data() + y * out.width, (BYTE*)data.Scan0 + y * data.Stride, out.width * sizeof(ARGB));
    image->UnlockBits(&data); image.reset(); stream->Release();
    return out;
}
int main() {
    try {
        auto legacy = defaultScene();
        legacy.erase("brightness");
        if (validateScene(legacy)["brightness"] != 100)
            throw std::runtime_error("Legacy scene brightness migration failed");
        for (int percent : {0, 37, 100}) {
            auto scene = legacy; scene["brightness"] = percent;
            auto saved = validateScene(Json::parse(scene.dump()));
            if (saved["brightness"] != percent || saved["cpu"] != legacy["cpu"])
                throw std::runtime_error("Brightness save/load changed scene data");
        }
        for (const Json invalid : {Json(-1), Json(101), Json(50.5), Json("50"), Json(nullptr), Json(4294967346ULL)}) {
            auto scene = legacy; scene["brightness"] = invalid;
            bool rejected = false;
            try { validateScene(scene); } catch (const std::exception&) { rejected = true; }
            if (!rejected) throw std::runtime_error("Invalid brightness accepted");
        }
        Renderer renderer;
        SensorSnapshot sensors{}; sensors.hasCpuTemp = true; sensors.cpuTemp = 56.7;
        for (const auto& key : {"cpu", "gpu"}) for (int fontSize : {12, 44, 120}) {
            auto scene = defaultScene();
            scene["cpu"]["enabled"] = std::string(key) == "cpu";
            scene["gpu"]["enabled"] = std::string(key) == "gpu";
            scene[key]["fontSize"] = fontSize;
            for (const auto& origin : {std::pair<int,int>{0,0}, {40,570}, {710,710}}) {
                scene[key]["x"] = origin.first; scene[key]["y"] = origin.second;
                const auto layer = decode(renderer.layer(scene, sensors, key));
                const auto full = decode(renderer.render(scene, sensors, true));
                bool visible = false;
                for (int y = 0; y < 720; ++y) for (int x = 0; x < 720; ++x) {
                    int sx = x - origin.first, sy = y - origin.second;
                    ARGB expected = sx >= 0 && sy >= 0 && sx < layer.width && sy < layer.height
                        ? layer.values[sy * layer.width + sx] : 0;
                    ARGB actual = full.values[y * 720 + x];
                    visible = visible || (expected >> 24);
                    if (actual != expected) throw std::runtime_error("Layer pixels differ from display render");
                }
                if (origin.first == 0 && !visible) throw std::runtime_error("Empty layer");
            }
        }
        std::cout << "18 pixel-exact layer comparisons passed\n";
        return 0;
    } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
