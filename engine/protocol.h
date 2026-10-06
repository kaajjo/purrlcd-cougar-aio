#pragma once
#include <cstddef>
#include <cstdint>
#include <map>
#include <string>
#include <vector>

namespace purrlcd {
using Bytes = std::vector<std::uint8_t>;
// Vendor slider 0..100 maps to device backlight values 10..100.
int brightnessValue(int percent);
// Encodes the command frame only, without HID report ID or padding.
// Empty jsonBody omits ContentType and ContentLength, as in POST conn.
Bytes encodeRequest(const std::string& method, const std::string& command,
                    std::uint32_t sequence, std::uint64_t unixMillis,
                    const std::string& jsonBody = {});
Bytes encodeFrame(const Bytes& message);
Bytes decodeFrame(const Bytes& wireFrame);
struct Response {
    unsigned version = 0;
    unsigned code = 0;
    std::uint32_t ackNumber = 0;
    std::map<std::string, std::string> headers;
    std::string body;
    bool success() const { return code >= 200 && code < 300; }
};
// Accepts an exact frame or a zero-padded HID payload (report ID excluded).
// All malformed input throws std::invalid_argument; size overflow throws
// std::length_error. A response must contain AckNumber.
Response decodeResponse(const Bytes& payload);
// Wraps a file value in an unescaped TLV: 5C + U16BE(value bytes) + value.
// The value must include the 21-byte file header; makeFileBlocks adds it.
Bytes encodeFileBlock(const Bytes& bytes);
// Fills a Windows HID output report with report ID 0 and zero padding.
// Never truncates. Default HID report length includes the report ID.
Bytes makeHidReport(const Bytes& payload, std::size_t reportBytes = 1025);
// Splits the file into negotiated TLV blocks. Each value starts with:
// transferId:u8, blockCount:u16BE, zeroBasedIndex:u16BE, type:u8, 15 zero
// reserved bytes, followed by up to 1000 file bytes. Full TLV fits one HID
// report payload. transferId=255 chooses current seconds (vendor behavior).
std::vector<Bytes> makeFileBlocks(const Bytes& file, std::size_t blockMaxSize = 1024,
                                  std::size_t reportBytes = 1025,
                                  std::uint8_t transferId = 255, std::uint8_t type = 0);
}
