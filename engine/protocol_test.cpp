#include "protocol.h"
#include <fstream>
#include <iostream>
#include <iterator>
#include <regex>
#include <stdexcept>
#include <string>

namespace {
int checks = 0;
void check(bool value, const char* message) {
    ++checks;
    if (!value) throw std::runtime_error(message);
}
purrlcd::Bytes hex(const std::string& value) {
    purrlcd::Bytes result;
    for (std::size_t i = 0; i < value.size(); i += 2)
        result.push_back(static_cast<std::uint8_t>(std::stoul(value.substr(i, 2), nullptr, 16)));
    return result;
}
template<class F> void rejected(F function, const char* message) {
    bool threw = false;
    try { function(); } catch (const std::exception&) { threw = true; }
    check(threw, message);
}
purrlcd::Bytes frame(const std::string& text) { return purrlcd::encodeFrame({text.begin(), text.end()}); }
}

int main(int argc, char** argv) {
    try {
        // Genuine COUGAR LCD Editor 1.0.14 log fixtures, September 28, 2026.
        const auto conn = hex("5a0035504f535420636f6e6e20310d0a5365714e756d6265723d300d0a446174653d313739303632393134373339370d0a0d0a615a");
        check(purrlcd::encodeRequest("POST", "conn", 0, 1790629147397ULL) == conn,
              "connection request differs from captured packet");
        const auto transport = hex("5a00ab504f5354207472616e73706f727420310d0a5365714e756d6265723d37383533330d0a446174653d313739303535383937333235310d0a436f6e74656e74547970653d6a736f6e0d0a436f6e74656e744c656e6774683d37330d0a0d0a7b2274797065223a226d65646961222c2266696c6553697a65223a363633332c2266696c654e616d65223a22323032362d30392d32385f30342d32392d33332d3234392e6f7364227dda5a");
        check(purrlcd::encodeRequest("POST", "transport", 78533, 1790558973251ULL,
              "{\"type\":\"media\",\"fileSize\":6633,\"fileName\":\"2026-09-28_04-29-33-249.osd\"}") == transport,
              "transport request differs from captured packet");
        const auto captured = hex("5a006a31203230300d0a41636b4e756d6265723d37383533320d0a436f6e74656e74547970653d6a736f6e0d0a436f6e74656e744c656e6774683d33390d0a0d0a7b227374617465223a2273756363657373222c22626c6f636b4d617853697a65223a313032347de95a");
        const auto response = purrlcd::decodeResponse(captured);
        check(response.success() && response.ackNumber == 78532 &&
              response.body == "{\"state\":\"success\",\"blockMaxSize\":1024}",
              "captured transport response did not decode");
        const auto blockAck = purrlcd::decodeResponse(hex("5a001b31203230300d0a41636b4e756d6265723d300d0a0d0a285a"));
        check(blockAck.success() && blockAck.ackNumber == 0 && blockAck.body.empty(), "invalid block ACK");

        // Golden result obtained by calling the vendor's pure packData function
        // in an isolated VM with every I/O dependency replaced by a stub.
        const auto escaped = hex("5a00085b015b025c195a");
        check(purrlcd::encodeFrame({0x5a, 0x5b, 0x5c}) == escaped, "escape golden fixture mismatch");
        check(purrlcd::decodeFrame(escaped) == purrlcd::Bytes({0x5a, 0x5b, 0x5c}), "escape decode failed");
        auto padded = captured;
        padded.resize(1024, 0);
        check(purrlcd::decodeResponse(padded).ackNumber == 78532, "zero-padded input rejected");
        auto allBytes = purrlcd::Bytes(256);
        for (unsigned i = 0; i < 256; ++i) allBytes[i] = static_cast<std::uint8_t>(i);
        check(purrlcd::decodeFrame(purrlcd::encodeFrame(allBytes)) == allBytes, "byte values round trip failed");
        // All interior bytes are escaped, including length and checksum.
        check(purrlcd::encodeFrame({0x54}) == hex("5a0006545b015a"), "checksum escape mismatch");
        check(purrlcd::decodeFrame(hex("5a0006545b015a")) == purrlcd::Bytes({0x54}), "delimiter checksum rejected");
        check(purrlcd::decodeFrame(purrlcd::encodeFrame(purrlcd::Bytes(85, 0))) == purrlcd::Bytes(85, 0), "escaped length rejected");

        check(purrlcd::encodeFileBlock({0x01, 0x02, 0x03}) == hex("5c0003010203"), "TLV golden fixture mismatch");
        check(purrlcd::encodeFileBlock({0x5a, 0x5b, 0x5c}) == hex("5c00035a5b5c"), "binary TLV must not escape bytes");
        purrlcd::Bytes file(2001);
        for (std::size_t i = 0; i < file.size(); ++i) file[i] = static_cast<std::uint8_t>(i);
        const auto blocks = purrlcd::makeFileBlocks(file, 1024, 1025, 9);
        check(blocks.size() == 3 && blocks[0].size() == 1024 && blocks[2].size() == 25,
              "vendor 1000-byte file chunk size mismatch");
        // Full original Ja(buffer).valueWithTLV() golden fixtures, not only
        // the lower-level TLV wrapper. Header is 21 bytes before file data.
        check(purrlcd::Bytes(blocks[0].begin(), blocks[0].begin() + 40) ==
              hex("5c03fd090003000000000000000000000000000000000000000102030405060708090a0b0c0d0e0f"),
              "first complete file block differs from vendor codec");
        check(blocks[2] == hex("5c0016090003000200000000000000000000000000000000d0"),
              "final complete file block differs from vendor codec");
        check(purrlcd::Bytes(blocks[1].begin(), blocks[1].begin() + 40) ==
              hex("5c03fd090003000100000000000000000000000000000000e8e9eaebecedeeeff0f1f2f3f4f5f6f7"),
              "middle complete file block differs from vendor codec");
        const auto typed = purrlcd::makeFileBlocks(file, 1024, 1025, 9, 1);
        check(typed[2] == hex("5c0016090003000201000000000000000000000000000000d0"),
              "file type byte differs from vendor codec");
        purrlcd::Bytes reconstructed;
        for (const auto& block : blocks) {
            check(block[0] == 0x5c && block.size() == 3U + block[1] * 256U + block[2], "invalid TLV length");
            reconstructed.insert(reconstructed.end(), block.begin() + 24, block.end());
            const auto report = purrlcd::makeHidReport(block);
            check(report.size() == 1025 && report[0] == 0 && report[1] == 0x5c, "invalid HID report");
        }
        check(reconstructed == file, "chunked file was corrupted");
        check(purrlcd::makeFileBlocks(purrlcd::Bytes(950733, 0)).size() == 951,
              "captured background file block count mismatch");

        for (std::size_t n = 0; n < captured.size(); ++n)
            rejected([&] { purrlcd::decodeResponse({captured.begin(), captured.begin() + n}); }, "truncation accepted");
        auto bad = captured; bad[bad.size() - 2] ^= 1;
        rejected([&] { purrlcd::decodeResponse(bad); }, "bad checksum accepted");
        rejected([&] { purrlcd::decodeFrame(hex("5a00065b03005a")); }, "unknown escape accepted");
        rejected([&] { purrlcd::decodeFrame(hex("5a000600065a01")); }, "nonzero trailing data accepted");
        rejected([&] { purrlcd::decodeFrame(hex("5a000100005a")); }, "invalid length accepted");
        rejected([&] { purrlcd::decodeResponse(frame("1 200\r\nAckNumber=1\r\nAckNumber=2\r\n\r\n")); }, "duplicate header accepted");
        rejected([&] { purrlcd::decodeResponse(frame("1 200\r\nAckNumber=4294967296\r\n\r\n")); }, "ACK overflow accepted");
        rejected([&] { purrlcd::decodeResponse(frame("1 200\r\nAckNumber=1\r\nContentLength=3\r\n\r\n{}")); }, "bad ContentLength accepted");
        rejected([&] { purrlcd::encodeRequest("POST\r\nX", "conn", 0, 0); }, "header injection accepted");
        rejected([&] { purrlcd::encodeFrame(purrlcd::Bytes(65531)); }, "oversized frame accepted");
        rejected([&] { purrlcd::encodeFileBlock({}); }, "empty file block accepted");
        rejected([&] { purrlcd::makeHidReport(purrlcd::Bytes(1025)); }, "oversized HID payload accepted");
        rejected([&] { purrlcd::makeFileBlocks(file, 0); }, "zero chunk size accepted");

        if (argc > 1) {
            std::ifstream input(argv[1], std::ios::binary);
            check(input.good(), "could not read optional vendor log");
            const std::string text((std::istreambuf_iterator<char>(input)), {});
            const std::regex pattern("data hex: (5a[0-9a-f]+)");
            unsigned fixtures = 0;
            for (std::sregex_iterator it(text.begin(), text.end(), pattern), end; it != end; ++it) {
                const auto wire = hex((*it)[1].str());
                try { check(purrlcd::encodeFrame(purrlcd::decodeFrame(wire)) == wire, "captured frame mismatch"); }
                catch (...) { std::cerr << "Captured fixture: " << (*it)[1].str() << '\n'; throw; }
                ++fixtures;
            }
            check(fixtures > 0, "no frames found in optional vendor log");
            std::cout << "Validated " << fixtures << " captured command/response frames.\n";
        }
        std::cout << checks << " protocol checks passed.\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "FAIL after " << checks << " checks: " << error.what() << '\n';
        return 1;
    }
}
