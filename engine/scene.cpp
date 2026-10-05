#include "scene.h"
#include <gdiplus.h>
#include <objidl.h>
#include <cmath>
#include <sstream>
#include <iomanip>

namespace purrlcd {
using namespace Gdiplus;
static Color color(const std::string& s) {
    unsigned long c = std::stoul(s.substr(1), nullptr, 16);
    return Color(255, (BYTE)(c >> 16), (BYTE)(c >> 8), (BYTE)c);
}
static bool validColor(const std::string& s) {
    if (s.size() != 7 || s[0] != '#') return false;
    return s.find_first_not_of("0123456789abcdefABCDEF", 1) == std::string::npos;
}
Json defaultScene() {
    return {{"backgroundPath", ""}, {"backgroundColor", "#111318"}, {"rotation", 180}, {"intervalMs", 1000},
        {"cpu", {{"enabled", true}, {"x", 40}, {"y", 565}, {"fontSize", 44}, {"color", "#FFFFFF"}, {"label", "CPU"}}},
        {"gpu", {{"enabled", true}, {"x", 40}, {"y", 625}, {"fontSize", 44}, {"color", "#FF954F"}, {"label", "GPU"}}}};
}
Json validateScene(const Json& input) {
    if (!input.is_object()) throw std::runtime_error("Scene must be an object");
    Json out = defaultScene();
    for (const auto& k : {"backgroundPath", "backgroundColor", "rotation", "intervalMs"})
        if (input.contains(k)) out[k] = input[k];
    auto p = out.at("backgroundPath").get<std::string>();
    if (p.size() > 32700) throw std::runtime_error("Background path is too long");
    if (!p.empty() && (!fs::is_regular_file(wide(p)) || fs::file_size(wide(p)) > 20 * 1024 * 1024))
        throw std::runtime_error("Background image not found or exceeds 20 MB");
    if (!validColor(out.at("backgroundColor").get<std::string>())) throw std::runtime_error("Invalid background color");
    int rotation = out.at("rotation").get<int>();
    if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) throw std::runtime_error("Invalid rotation");
    int interval = out.at("intervalMs").get<int>();
    if (interval < 1000 || interval > 5000) throw std::runtime_error("Interval must be between 1 and 5 seconds");
    for (const auto& k : {"cpu", "gpu"}) {
        if (input.contains(k)) {
            if (!input[k].is_object()) throw std::runtime_error("Invalid text layer");
            for (const auto& field : {"enabled", "x", "y", "fontSize", "color", "label"})
                if (input[k].contains(field)) out[k][field] = input[k][field];
        }
        auto& layer = out[k];
        layer.at("enabled").get<bool>();
        int x = layer.at("x").get<int>(), y = layer.at("y").get<int>(), size = layer.at("fontSize").get<int>();
        if (x < 0 || x > 710 || y < 0 || y > 710 || size < 12 || size > 120) throw std::runtime_error("Text coordinates or size out of range");
        if (!validColor(layer.at("color").get<std::string>())) throw std::runtime_error("Invalid text color");
        if (layer.at("label").get<std::string>().size() > 80) throw std::runtime_error("Text label too long");
    }
    return out;
}
static std::string number(bool available, double value) {
    if (!available || !std::isfinite(value)) return "—";
    return std::to_string((int)std::lround(value));
}
std::string visualKey(const Json& scene, const SensorSnapshot& v) {
    return scene.dump() + "|" + (scene["cpu"]["enabled"].get<bool>() ? number(v.hasCpuTemp, v.cpuTemp) : "") + "|" +
           (scene["gpu"]["enabled"].get<bool>() ? number(v.hasGpuTemp, v.gpuTemp) : "");
}
Renderer::Renderer() {
    GdiplusStartupInput startup;
    if (GdiplusStartup(&token_, &startup, nullptr) != Ok) throw std::runtime_error("Cannot start graphics renderer");
}
Renderer::~Renderer() { if (token_) GdiplusShutdown(token_); }
static std::vector<uint8_t> png(Bitmap& bitmap) {
    const CLSID pngEncoder = {0x557cf406, 0x1a04, 0x11d3, {0x9a,0x73,0x00,0x00,0xf8,0x1e,0xf3,0x2e}};
    IStream* stream = nullptr;
    if (FAILED(CreateStreamOnHGlobal(nullptr, TRUE, &stream))) throw std::runtime_error("Cannot allocate PNG stream");
    Status ret = bitmap.Save(stream, &pngEncoder, nullptr);
    if (ret != Ok) { stream->Release(); throw std::runtime_error("PNG encoding failed"); }
    HGLOBAL h = nullptr; GetHGlobalFromStream(stream, &h);
    STATSTG st{}; stream->Stat(&st, STATFLAG_NONAME);
    auto* data = (uint8_t*)GlobalLock(h);
    std::vector<uint8_t> result(data, data + (size_t)st.cbSize.QuadPart);
    GlobalUnlock(h); stream->Release();
    return result;
}
static void drawBackground(Graphics& g, const Json& scene) {
    g.Clear(color(scene.at("backgroundColor").get<std::string>()));
    auto path = scene.at("backgroundPath").get<std::string>();
    if (path.empty()) return;
    Image image(wide(path).c_str(), FALSE);
    if (image.GetLastStatus() != Ok || !image.GetWidth() || !image.GetHeight())
        throw std::runtime_error("Could not read background image");
    if ((uint64_t)image.GetWidth() * image.GetHeight() > 32000000)
        throw std::runtime_error("Image is too large: maximum 32 megapixels");
    double w = image.GetWidth(), h = image.GetHeight();
    double side = std::min(w, h);
    g.SetInterpolationMode(InterpolationModeHighQualityBicubic);
    g.DrawImage(&image, RectF(0, 0, 720, 720), (REAL)((w-side)/2), (REAL)((h-side)/2), (REAL)side, (REAL)side, UnitPixel);
}
std::vector<uint8_t> Renderer::background(const Json& scene) {
    Bitmap bitmap(720, 720, PixelFormat32bppARGB);
    Graphics g(&bitmap); drawBackground(g, scene);
    return png(bitmap);
}
static void drawLayers(Graphics& g, const Json& scene, const SensorSnapshot& values) {
    g.SetSmoothingMode(SmoothingModeAntiAlias);
    g.SetTextRenderingHint(TextRenderingHintAntiAliasGridFit);
    for (const auto& key : {"cpu", "gpu"}) {
        const auto& layer = scene.at(key);
        if (!layer.at("enabled").get<bool>()) continue;
        bool cpu = std::string(key) == "cpu";
        bool available = cpu ? values.hasCpuTemp : values.hasGpuTemp;
        double temp = cpu ? values.cpuTemp : values.gpuTemp;
        auto label = layer.at("label").get<std::string>();
        std::string text = (label.empty() ? "" : label + "  ") + number(available, temp) + (available ? " °C" : "");
        std::wstring ws = wide(text);
        Font font(L"Segoe UI", (REAL)layer.at("fontSize").get<int>(), FontStyleBold, UnitPixel);
        SolidBrush brush(color(layer.at("color").get<std::string>()));
        StringFormat format(StringFormat::GenericTypographic());
        format.SetFormatFlags(StringFormatFlagsNoWrap);
        PointF origin((REAL)layer.at("x").get<int>(), (REAL)layer.at("y").get<int>());
        g.DrawString(ws.c_str(), (INT)ws.size(), &font, origin, &format, &brush);
    }
}
std::vector<uint8_t> Renderer::render(const Json& scene, const SensorSnapshot& values, bool overlayOnly) {
    Bitmap bitmap(720, 720, PixelFormat32bppARGB);
    Graphics g(&bitmap);
    if (overlayOnly) g.Clear(Color(0, 0, 0, 0)); else drawBackground(g, scene);
    drawLayers(g, scene, values);
    return png(bitmap);
}
std::vector<uint8_t> Renderer::layer(const Json& scene, const SensorSnapshot& values, const std::string& key) {
    auto isolated = scene;
    for (const auto& name : {"cpu", "gpu"}) {
        isolated[name]["enabled"] = key == name;
        isolated[name]["x"] = 0;
        isolated[name]["y"] = 0;
    }
    Bitmap bitmap(720, 720, PixelFormat32bppARGB);
    {
        Graphics g(&bitmap);
        g.Clear(Color(0, 0, 0, 0));
        drawLayers(g, isolated, values);
    }
    // Retain transparent top/left padding: image (0,0) must equal the text origin.
    Rect area(0, 0, 720, 720);
    BitmapData data{};
    if (bitmap.LockBits(&area, ImageLockModeRead, PixelFormat32bppARGB, &data) != Ok)
        throw std::runtime_error("Cannot read text layer pixels");
    int width = 1, height = 1;
    for (int y = 0; y < 720; ++y) {
        const auto* row = static_cast<const BYTE*>(data.Scan0) + y * data.Stride;
        for (int x = 0; x < 720; ++x) if (row[x * 4 + 3]) {
            width = std::max(width, x + 1);
            height = std::max(height, y + 1);
        }
    }
    bitmap.UnlockBits(&data);
    std::unique_ptr<Bitmap> cropped(bitmap.Clone(0, 0, width, height, PixelFormat32bppARGB));
    if (!cropped || cropped->GetLastStatus() != Ok) throw std::runtime_error("Cannot crop text layer");
    return png(*cropped);
}

}
