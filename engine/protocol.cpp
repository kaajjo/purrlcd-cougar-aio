#include "protocol.h"
#include <algorithm>
#include <chrono>
#include <limits>
#include <stdexcept>

namespace purrlcd {
namespace {
constexpr std::uint8_t kFrame = 0x5a, kEscape = 0x5b, kFile = 0x5c;
void require(bool condition, const char* message) {
    if (!condition) throw std::invalid_argument(message);
}
std::uint64_t decimal(const std::string& text, std::uint64_t maximum) {
    require(!text.empty(), "empty integer");
    std::uint64_t value = 0;
    for (const char c : text) {
        require(c >= '0' && c <= '9', "invalid integer");
        const unsigned digit = static_cast<unsigned>(c - '0');
        require(value <= (maximum - digit) / 10, "integer out of range");
        value = value * 10 + digit;
    }
    return value;
}
void token(const std::string& text) {
    require(!text.empty() && text.size() <= 64, "invalid protocol token");
    for (const unsigned char c : text)
        require((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') || c == '_' || c == '-', "invalid protocol token");
}
}

Bytes encodeFrame(const Bytes& message) {
    if (message.size() > 65530) throw std::length_error("protocol message exceeds 16-bit frame");
    const std::size_t total = message.size() + 5;
    Bytes result;
    result.reserve(total * 2);
    result.push_back(kFrame);
    const auto escaped = [&result](std::uint8_t byte) {
        if (byte == kFrame || byte == kEscape) {
            result.push_back(kEscape);
            result.push_back(byte == kFrame ? 1 : 2);
        } else result.push_back(byte);
    };
    escaped(static_cast<std::uint8_t>(total >> 8));
    escaped(static_cast<std::uint8_t>(total));
    std::uint8_t sum = static_cast<std::uint8_t>((total >> 8) + (total & 0xff));
    for (const std::uint8_t byte : message) {
        sum = static_cast<std::uint8_t>(sum + byte);
        escaped(byte);
    }
    escaped(sum);
    result.push_back(kFrame);
    return result;
}

Bytes encodeRequest(const std::string& method, const std::string& command,
                    std::uint32_t sequence, std::uint64_t unixMillis,
                    const std::string& jsonBody) {
    token(method); token(command);
    std::string message = method + " " + command + " 1\r\nSeqNumber=" +
        std::to_string(sequence) + "\r\nDate=" + std::to_string(unixMillis) + "\r\n";
    if (!jsonBody.empty()) message += "ContentType=json\r\nContentLength=" +
        std::to_string(jsonBody.size()) + "\r\n";
    message += "\r\n";
    message += jsonBody;
    return encodeFrame(Bytes(message.begin(), message.end()));
}

Bytes decodeFrame(const Bytes& frame) {
    require(frame.size() >= 5 && frame[0] == kFrame, "invalid frame header");
    require(frame.size() <= 131068, "oversized wire frame");
    Bytes inner;
    inner.reserve(std::min<std::size_t>(frame.size(), 65533));
    std::size_t position = 1;
    while (position < frame.size() && frame[position] != kFrame) {
        std::uint8_t byte = frame[position++];
        if (byte == kEscape) {
            require(position < frame.size(), "truncated escape");
            const std::uint8_t escaped = frame[position++];
            require(escaped == 1 || escaped == 2, "invalid escape");
            byte = escaped == 1 ? kFrame : kEscape;
        }
        require(inner.size() < 65533, "oversized logical frame");
        inner.push_back(byte);
    }
    require(position < frame.size(), "missing frame tail");
    require(inner.size() >= 3, "truncated frame interior");
    const std::size_t total = (static_cast<std::size_t>(inner[0]) << 8) | inner[1];
    require(total >= 5 && total == inner.size() + 2, "invalid logical frame length");
    std::uint8_t sum = 0;
    for (std::size_t i = 0; i + 1 < inner.size(); ++i)
        sum = static_cast<std::uint8_t>(sum + inner[i]);
    require(inner.back() == sum, "invalid frame checksum");
    for (++position; position < frame.size(); ++position)
        require(frame[position] == 0, "unexpected trailing data");
    return Bytes(inner.begin() + 2, inner.end() - 1);
}

Response decodeResponse(const Bytes& payload) {
    const Bytes bytes = decodeFrame(payload);
    const std::string message(bytes.begin(), bytes.end());
    const std::size_t separator = message.find("\r\n\r\n");
    require(separator != std::string::npos, "missing response header separator");
    const std::size_t firstEnd = message.find("\r\n");
    require(firstEnd != std::string::npos && firstEnd <= separator, "invalid response line");
    const std::string first = message.substr(0, firstEnd);
    const std::size_t space = first.find(' ');
    require(space != std::string::npos && first.find(' ', space + 1) == std::string::npos,
            "invalid response line");
    Response response;
    response.version = static_cast<unsigned>(decimal(first.substr(0, space), 255));
    require(response.version == 1, "unsupported protocol version");
    response.code = static_cast<unsigned>(decimal(first.substr(space + 1), 999));
    require(response.code >= 100, "invalid response status");
    std::size_t position = firstEnd + 2;
    while (position < separator) {
        const std::size_t end = message.find("\r\n", position);
        require(end != std::string::npos && end <= separator, "invalid response header");
        const std::string line = message.substr(position, end - position);
        const std::size_t equals = line.find('=');
        require(equals != std::string::npos && equals > 0, "invalid response header");
        const std::string key = line.substr(0, equals);
        token(key);
        require(response.headers.emplace(key, line.substr(equals + 1)).second,
                "duplicate response header");
        position = end + 2;
    }
    auto ack = response.headers.find("AckNumber");
    require(ack != response.headers.end(), "response has no AckNumber");
    response.ackNumber = static_cast<std::uint32_t>(decimal(ack->second,
                                        std::numeric_limits<std::uint32_t>::max()));
    response.body = message.substr(separator + 4);
    const auto length = response.headers.find("ContentLength");
    if (length == response.headers.end()) require(response.body.empty(), "body has no ContentLength");
    else require(decimal(length->second, 65530) == response.body.size(), "ContentLength mismatch");
    return response;
}

Bytes encodeFileBlock(const Bytes& bytes) {
    require(!bytes.empty(), "empty file block");
    if (bytes.size() > 65535) throw std::length_error("file block exceeds 16-bit length");
    Bytes result;
    result.reserve(bytes.size() + 3);
    result.push_back(kFile);
    result.push_back(static_cast<std::uint8_t>(bytes.size() >> 8));
    result.push_back(static_cast<std::uint8_t>(bytes.size()));
    result.insert(result.end(), bytes.begin(), bytes.end());
    return result;
}

Bytes makeHidReport(const Bytes& payload, std::size_t reportBytes) {
    require(reportBytes > 1 && reportBytes <= 65536, "invalid HID report size");
    if (payload.empty() || payload.size() > reportBytes - 1)
        throw std::length_error("payload does not fit HID report");
    Bytes report(reportBytes, 0);
    std::copy(payload.begin(), payload.end(), report.begin() + 1);
    return report;
}

std::vector<Bytes> makeFileBlocks(const Bytes& file, std::size_t blockMaxSize,
                                 std::size_t reportBytes, std::uint8_t transferId,
                                 std::uint8_t type) {
    require(!file.empty(), "cannot transfer empty file");
    require(reportBytes >= 26 && reportBytes <= 65536, "invalid HID report size");
    require(blockMaxSize >= 25 && blockMaxSize <= 65535, "invalid negotiated block size");
    const std::size_t chunk = std::min<std::size_t>(1000,
        std::min(blockMaxSize - 24, reportBytes - 25));
    const std::size_t blockCount = (file.size() - 1) / chunk + 1;
    if (blockCount > 65535) throw std::length_error("file needs more than 65535 blocks");
    if (transferId == 255) {
        const auto seconds = std::chrono::duration_cast<std::chrono::seconds>(
            std::chrono::system_clock::now().time_since_epoch()).count();
        transferId = static_cast<std::uint8_t>(seconds % 60);
    }
    std::vector<Bytes> result;
    result.reserve(blockCount);
    for (std::size_t offset = 0; offset < file.size();) {
        const std::size_t count = std::min(chunk, file.size() - offset);
        const std::size_t blockIndex = result.size();
        Bytes value(21, 0);
        value[0] = transferId;
        value[1] = static_cast<std::uint8_t>(blockCount >> 8);
        value[2] = static_cast<std::uint8_t>(blockCount);
        value[3] = static_cast<std::uint8_t>(blockIndex >> 8);
        value[4] = static_cast<std::uint8_t>(blockIndex);
        value[5] = type;
        value.insert(value.end(), file.begin() + offset, file.begin() + offset + count);
        result.push_back(encodeFileBlock(value));
        offset += count;
    }
    return result;
}
}
