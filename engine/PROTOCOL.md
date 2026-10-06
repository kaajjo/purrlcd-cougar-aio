# COUGAR 1D6B:0110 display protocol

This describes the POSEIDON VISTEK PRO ARGB device examined with COUGAR LCD
Editor 1.0.14 and firmware V1.0.5. It must not be assumed compatible with
other COUGAR products or USB IDs.

## Evidence and scope

- Windows HID descriptors: VID `1D6B`, PID `0110`, usage page `FF00`, usage
  `0001`, input/output report lengths 1025 bytes, including report ID zero.
- 625 complete command/response frames from a local vendor log round-trip
  exactly through this codec, including escaped checksums.
- The vendor's pure frame and file codec functions were exercised with
  synthetic input in an isolated JavaScript runtime. File, network, process,
  timer, and hardware dependencies were replaced with stubs. The full file
  constructor and first/middle/final blocks were compared, including the
  21-byte metadata that precedes image data.
- The native implementation subsequently received successful acknowledgements
  from the actual device for connection, resume, background PNG, and repeated
  PNG overlays with the `.osd` filename extension. An acknowledgement confirms
  protocol acceptance; physical appearance remains a separate visual check.

The shipped application contains an independent C++ codec, not vendor
JavaScript, bytecode, native modules, or a JavaScript runtime.

## Command frames

Before escaping:

```text
5A | total length: U16BE | UTF-8 message | checksum: U8 | 5A
```

`total length` includes both delimiters, two length bytes and checksum. The
checksum is the low eight bits of the sum of the two length bytes and message
bytes. It is not a CRC.

Escape every byte between the delimiters, including the length and checksum:

```text
5A -> 5B 01
5B -> 5B 02
```

The length and checksum are calculated before escaping. Then prepend HID
report ID `00` and pad the output report to 1025 bytes with zeroes. No
command fragmentation is currently implemented; oversized HID payloads fail.

Request syntax:

```text
POST transport 1\r\n
SeqNumber=123\r\n
Date=1790558973251\r\n
ContentType=json\r\n
ContentLength=<UTF-8 body bytes>\r\n
\r\n
{"type":"media","fileSize":6633,"fileName":"example.osd"}
```

For a bodyless `POST conn`, omit ContentType and ContentLength. Responses use
`1 200\r\n` as their first line and an `AckNumber` header. A normal command
with sequence N is acknowledged with N+1. File completion uses AckNumber 0.

## Image transfer

1. Send `POST transport` with `type: "media"`, the raw file byte count and
   filename. Require successful acknowledgement and read `blockMaxSize`.
2. Send the file blocks described below. The examined device advertises 1024
   bytes; vendor file content chunks are 1000 bytes each.
3. Require the successful response with AckNumber 0 after the final block.
4. Send `POST transported` with `{"md5":"todo","fileName":"..."}` and require
   its successful acknowledgement. The literal `todo` is present in the
   original application; this field is not an MD5 hash in that implementation.

Backgrounds are PNG files with `.png` names. Overlays carry PNG bytes with
`.osd` names; the extension selects the overlay role. There is no custom OSD
image encoding needed for the supported PNG path. No separate theme-selection
command appears between successful background transfer and overlay updates.

Each file block uses a distinct, unescaped TLV. Its layout, excluding the HID
report ID, is:

| Offset | Size | Meaning |
| --- | --- | --- |
| 0 | 1 | `5C` |
| 1 | 2 | U16BE value length: 21 metadata bytes + content bytes |
| 3 | 1 | Transfer ID; vendor uses current seconds, 0–59 |
| 4 | 2 | U16BE total block count |
| 6 | 2 | U16BE zero-based block index |
| 8 | 1 | Type byte |
| 9 | 15 | Reserved zeroes |
| 24 | 1–1000 | Raw file bytes |

For 1000 content bytes, the TLV is exactly 1024 bytes and fills a 1025-byte HID
report after the report ID. Final blocks may be shorter and are padded only
at the HID report boundary. Do not apply command-frame escaping or checksum
to file blocks. There is no final-block flag: the block index/count identify
the last block.

The low-level vendor file constructor defaults the type byte to zero, but the
actual higher-level HID sender was separately exercised with a fake device
and its constructor output intercepted before transmission. It selected type
one for the tested PNG/OSD transfer path. The application explicitly sends
type one to follow that sender; the general codec retains the constructor's
default zero. Type zero also received successful acknowledgements and the user
confirmed visible updates during the initial hardware test. The precise
meaning of this byte, and any relationship to storage or JPEG modes, remain
unestablished; it must not be described as a persistence/streaming flag.

## Connection and settings

Begin with `POST conn`, sequence 0, then `POST power` with
`{"event":"resume"}`. Connection data has top-level `degree`, `brightness`,
`sn`, `osdState`, `mode`, `logo`, and `timeout`; version information is nested
under `version`. The native implementation preserves existing settings except
those explicitly selected by the user.

The actual vendor setting handlers were called with a fake device and the
request factory intercepted before transmission:

- Rotation: `POST rotate`, body `{"degree":90}`. UI angles are 0/90/180/270.
- Brightness: `POST brightness`, body `{"value":100}`. The vendor UI maps its
  0–100 slider to `round(10 + 0.9 * slider)` in the body.

PurrLCD saves the same 0–100 slider percentage as `brightness` in the scene
(default 100 for older scenes). Configuration sends `POST brightness` only
when the mapped value differs from the device's reported or last applied value.
The minimum is a dim backlight.

Firmware reports `timeout: 60` in the examined connection response. Its exact
keepalive and idle-display semantics require separate measurement; connection
fields are not sufficient evidence of those semantics.

## Validation

`protocol_test.cpp` checks captured request/response fixtures, pure-codec
golden file blocks, malformed/truncated messages, duplicate headers, integer
and size bounds, checksum/length escaping, file reconstruction and HID sizes.
Passing a vendor log path as its optional first argument adds exact frame
round-trip checks. The final research run passed 771 checks, including all
625 captured frames.
