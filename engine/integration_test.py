"""Windows-only IPC checks against an already running, disconnected agent.

Run with a normal user token to exercise the elevated-agent boundary. Close the
editor during this test. No connect, disconnect or quit commands are sent and no
valid scene is saved. Preview requests refresh the agent's temporary PNG files.
Use --allow-connected to test while the agent continues its existing LCD output.
Only Python's standard library is required.
"""
from __future__ import annotations

import argparse
import copy
import ctypes
from ctypes import wintypes
import json
from pathlib import Path
import struct
import sys
import time
import zlib


class Overlapped(ctypes.Structure):
    _fields_ = [("Internal", ctypes.c_size_t), ("InternalHigh", ctypes.c_size_t),
                ("Offset", wintypes.DWORD), ("OffsetHigh", wintypes.DWORD),
                ("hEvent", wintypes.HANDLE)]


class PipeClient:
    def __init__(self, name: str | None, timeout: float):
        self.kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        self.security = ctypes.WinDLL("advapi32", use_last_error=True)
        k = self.kernel
        k.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD,
                                 ctypes.c_void_p, wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
        k.CreateFileW.restype = wintypes.HANDLE
        k.CreateEventW.argtypes = [ctypes.c_void_p, wintypes.BOOL, wintypes.BOOL, wintypes.LPCWSTR]
        k.CreateEventW.restype = wintypes.HANDLE
        k.CloseHandle.argtypes = [wintypes.HANDLE]
        k.CloseHandle.restype = wintypes.BOOL
        for api in (k.ReadFile, k.WriteFile):
            api.argtypes = [wintypes.HANDLE, ctypes.c_void_p, wintypes.DWORD,
                            ctypes.POINTER(wintypes.DWORD), ctypes.POINTER(Overlapped)]
            api.restype = wintypes.BOOL
        k.GetOverlappedResult.argtypes = [wintypes.HANDLE, ctypes.POINTER(Overlapped),
                                        ctypes.POINTER(wintypes.DWORD), wintypes.BOOL]
        k.GetOverlappedResult.restype = wintypes.BOOL
        k.CancelIoEx.argtypes = [wintypes.HANDLE, ctypes.POINTER(Overlapped)]
        k.CancelIoEx.restype = wintypes.BOOL
        k.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
        k.WaitForSingleObject.restype = wintypes.DWORD
        self.security.GetUserNameW.argtypes = [wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD)]
        self.security.GetUserNameW.restype = wintypes.BOOL
        if name is None:
            user = ctypes.create_unicode_buffer(256)
            size = wintypes.DWORD(len(user))
            if not self.security.GetUserNameW(user, ctypes.byref(size)):
                raise ctypes.WinError(ctypes.get_last_error())
            name = "\\\\.\\pipe\\PurrLCD-" + user.value
        self.name, self.timeout = name, timeout

    def _io(self, pipe, payload: bytes | None, length: int, deadline: float) -> bytes:
        k = self.kernel
        result = bytearray()
        offset = 0
        while offset < length:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError("IPC request deadline exceeded")
            count = length - offset
            buffer = ctypes.create_string_buffer(payload[offset:] if payload is not None else count)
            transferred = wintypes.DWORD()
            operation = Overlapped()
            operation.hEvent = k.CreateEventW(None, True, False, None)
            if not operation.hEvent:
                raise ctypes.WinError(ctypes.get_last_error())
            try:
                api = k.WriteFile if payload is not None else k.ReadFile
                ok = api(pipe, buffer, count, ctypes.byref(transferred), ctypes.byref(operation))
                error = ctypes.get_last_error() if not ok else 0
                if not ok and error == 997:  # ERROR_IO_PENDING
                    wait = k.WaitForSingleObject(operation.hEvent, max(1, int(remaining * 1000)))
                    if wait != 0:
                        k.CancelIoEx(pipe, ctypes.byref(operation))
                        k.GetOverlappedResult(pipe, ctypes.byref(operation), ctypes.byref(transferred), True)
                        raise TimeoutError("Agent did not complete the IPC operation")
                    ok = k.GetOverlappedResult(pipe, ctypes.byref(operation), ctypes.byref(transferred), False)
                    error = ctypes.get_last_error() if not ok else 0
                if not ok:
                    raise ctypes.WinError(error)
                if not transferred.value:
                    raise EOFError("Agent closed an incomplete IPC frame")
                if payload is None:
                    result.extend(buffer.raw[:transferred.value])
                offset += transferred.value
            finally:
                k.CloseHandle(operation.hEvent)
        return bytes(result)

    def request(self, command: str, **fields) -> dict:
        if command not in {"status", "getScene", "preview", "saveScene"}:
            raise ValueError("This diagnostic does not send device-control commands")
        deadline = time.monotonic() + self.timeout
        invalid = ctypes.c_void_p(-1).value
        while True:
            # SECURITY_SQOS_PRESENT | SECURITY_IMPERSONATION allows the server
            # to perform the caller's file access under the caller's token.
            pipe = self.kernel.CreateFileW(self.name, 0xC0000000, 0, None, 3,
                                           0x40000000 | 0x00100000 | 0x00020000, None)
            if pipe != invalid:
                break
            error = ctypes.get_last_error()
            if error not in (2, 231) or time.monotonic() >= deadline:
                raise ctypes.WinError(error)
            time.sleep(0.025)  # Retry only before any command bytes are sent.
        try:
            payload = json.dumps({"command": command, **fields}, ensure_ascii=False).encode("utf-8")
            packet = struct.pack("<I", len(payload)) + payload
            self._io(pipe, packet, len(packet), deadline)
            size, = struct.unpack("<I", self._io(pipe, None, 4, deadline))
            if not 0 < size <= 1024 * 1024:
                raise AssertionError(f"Unexpected response frame size: {size}")
            reply = json.loads(self._io(pipe, None, size, deadline))
            if not isinstance(reply, dict):
                raise AssertionError("Response must be a JSON object")
            return reply
        finally:
            # Closing after consuming the full response releases the server's
            # response-drain wait. A fresh pipe is used for the next request.
            self.kernel.CloseHandle(pipe)


def success(reply: dict) -> dict:
    if reply.get("ok") is not True:
        raise AssertionError(f"Agent rejected request: {reply}")
    return reply


def verify_png(path: Path, solid_rgb: tuple[int, int, int] | None = None) -> dict:
    raw = path.read_bytes()
    if raw[:8] != b"\x89PNG\r\n\x1a\n":
        raise AssertionError("Preview is not a PNG")
    position, compressed, header, ended = 8, bytearray(), None, False
    while position + 12 <= len(raw):
        size, = struct.unpack_from(">I", raw, position)
        kind = raw[position + 4:position + 8]
        end = position + 8 + size
        if end + 4 > len(raw):
            raise AssertionError("Truncated PNG chunk")
        data = raw[position + 8:end]
        crc, = struct.unpack_from(">I", raw, end)
        if zlib.crc32(kind + data) & 0xFFFFFFFF != crc:
            raise AssertionError("PNG checksum mismatch")
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", data)
        elif kind == b"IDAT":
            compressed.extend(data)
        elif kind == b"IEND":
            ended = True
            break
        position = end + 4
    if not ended or header is None:
        raise AssertionError("Incomplete PNG")
    width, height, depth, kind, compression, filtering, interlace = header
    if (width, height, depth, compression, filtering, interlace) != (720, 720, 8, 0, 0, 0) or kind not in (2, 6):
        raise AssertionError(f"Unexpected preview PNG format: {header}")
    channels = 4 if kind == 6 else 3
    stride = width * channels
    expected_size = (stride + 1) * height
    decoder = zlib.decompressobj()
    pixels = decoder.decompress(compressed, expected_size + 1)
    if len(pixels) != expected_size or not decoder.eof:
        raise AssertionError("Unexpected decoded PNG length")
    previous = bytearray(stride)
    for row_index in range(height):
        start = row_index * (stride + 1)
        method = pixels[start]
        row = bytearray(pixels[start + 1:start + stride + 1])
        if method > 4:
            raise AssertionError("Invalid PNG filter")
        if solid_rgb is not None:
            for index in range(stride):
                left = row[index - channels] if index >= channels else 0
                above = previous[index]
                corner = previous[index - channels] if index >= channels else 0
                if method == 1:
                    prediction = left
                elif method == 2:
                    prediction = above
                elif method == 3:
                    prediction = (left + above) // 2
                elif method == 4:
                    estimate = left + above - corner
                    choices = (left, above, corner)
                    prediction = min(choices, key=lambda value: abs(estimate - value))
                else:
                    prediction = 0
                row[index] = (row[index] + prediction) & 255
            pixel = bytes(solid_rgb) + (b"\xff" if channels == 4 else b"")
            if row != pixel * width:
                raise AssertionError(f"Preview pixels differ from the requested solid color at row {row_index}")
        previous = row
    return {"width": width, "height": height, "bytes": len(raw)}


def run(args) -> dict:
    is_admin = ctypes.WinDLL("shell32", use_last_error=True).IsUserAnAdmin
    is_admin.argtypes = []
    is_admin.restype = wintypes.BOOL
    if is_admin():
        raise AssertionError("Run this diagnostic from a normal, non-administrator terminal to verify the IPC privilege boundary")
    client = PipeClient(args.pipe, args.timeout)
    initial = success(client.request("status"))["status"]
    if not args.allow_connected and (initial["connected"] or initial["connecting"]):
        raise AssertionError("Run this test with the agent already disconnected; the test will not change device state")
    if args.require_temperatures and (initial.get("cpuTemp") is None or initial.get("gpuTemp") is None):
        raise AssertionError(f"Live CPU/GPU temperatures are required: {initial.get('sensorStatus')}")
    original = success(client.request("getScene"))["scene"]
    preview = success(client.request("preview"))
    saved_preview = verify_png(Path(preview["previewPath"]))

    proposed = copy.deepcopy(original)
    proposed["backgroundPath"] = ""
    proposed["backgroundColor"] = "#153759"
    proposed["cpu"]["enabled"] = proposed["gpu"]["enabled"] = False
    solid = success(client.request("preview", scene=proposed))
    verify_png(Path(solid["previewPath"]), (0x15, 0x37, 0x59))
    if success(client.request("getScene"))["scene"] != original:
        raise AssertionError("Preview unexpectedly changed the saved scene")

    invalid = copy.deepcopy(original)
    invalid["intervalMs"] = 0
    rejected = client.request("saveScene", scene=invalid)
    if rejected.get("ok") is not False or not rejected.get("error"):
        raise AssertionError(f"Invalid scene was not explicitly rejected: {rejected}")
    if success(client.request("getScene"))["scene"] != original:
        raise AssertionError("Rejected save changed the saved scene")

    memory = []
    for _ in range(args.previews):
        reply = success(client.request("preview", scene=proposed))
        memory.append({key: reply["status"][key] for key in ("workingSetBytes", "privateBytes")})
    final = success(client.request("status"))["status"]
    if not args.allow_connected and (final["connected"] or final["connecting"] or final["framesSent"] != initial["framesSent"]):
        raise AssertionError("Device activity changed during the test; ensure no other client is active")
    if success(client.request("getScene"))["scene"] != original:
        raise AssertionError("Saved scene changed during the test; close the editor while testing")
    return {"ok": True, "checks": ["normal-user IPC", "status", "getScene", "valid preview PNG",
             "preview pixel color", "preview does not save", "invalid save rejected without mutation", "no device-control requests"],
            "preview": saved_preview, "cpuTemp": final.get("cpuTemp"), "gpuTemp": final.get("gpuTemp"),
            "memorySamples": memory, "memoryNote": "Short preview samples detect obvious growth; they are not a steady-state benchmark."}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pipe", help="Override the current user's named pipe")
    parser.add_argument("--timeout", type=float, default=5.0)
    parser.add_argument("--previews", type=int, default=12, help="Additional unsaved previews, 0..100")
    parser.add_argument("--require-temperatures", action="store_true")
    parser.add_argument("--allow-connected", action="store_true", help="Allow the agent to continue its existing LCD output")
    args = parser.parse_args()
    if sys.platform != "win32":
        parser.error("This test requires Windows")
    if not 0 <= args.previews <= 100 or not 0.5 <= args.timeout <= 30:
        parser.error("Use 0..100 previews and a timeout between 0.5 and 30 seconds")
    try:
        print(json.dumps(run(args), indent=2, ensure_ascii=False))
        return 0
    except (AssertionError, OSError, ValueError, KeyError, EOFError, zlib.error) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
