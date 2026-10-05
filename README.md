# PurrLCD

A replacement for COUGAR LCDEditor, with a C++ background process and a separate Compose Desktop / Material 3 editor. Close the editor and the LCD keeps running without the JVM.

## Hardware

**This project is currently built specifically for my own hardware. Support for other setups has not been implemented or verified.**

- **AIO:** COUGAR Poseidon Vistek Pro ARGB 360, 720 × 720 LCD, USB HID `1D6B:0110`
- **CPU:** AMD Ryzen 7 5700X
- **GPU:** AMD Radeon RX 9070 XT
- **OS:** Windows x64

CPU temperature uses an installed [PawnIO](https://pawnio.eu/) driver and the bundled `AMDFamily17.bin` module. The current sensor implementation targets AMD family 19h/model 21h and requires the background process to run as administrator. GPU temperature uses AMD ADLX from the installed AMD driver. PurrLCD does not install either driver.

## Features

- Image or solid-color background.
- CPU/GPU temperatures with editable labels, positions, font sizes and colors.
- Live preview with draggable temperature layers.
- Display rotation in 90° steps and a 1–5 second update interval.
- Saved layouts, tray controls and reconnection after restarting the background process.

No GIF/video playback, custom layer types, Windows startup registration, or pump/fan control yet.

## Compared with LCDEditor

Observed on the hardware listed above, with the LCD running:

| Resource | Original LCDEditor | PurrLCD, editor closed |
| --- | --- | --- |
| CPU usage | ~1.2% continuously | 0.022–0.034% |
| Memory | 200–300 MiB | 30.4–36.9 MiB working set |

The LCDEditor figures are my observations. PurrLCD figures come from two 40-second runs; its private commit was 71.6–73.0 MiB. The memory counter used for the LCDEditor figure is unspecified, so the memory figures are approximate comparisons. See [measurement notes](engine/sensors-vendor/MEASUREMENTS.txt).

The open PurrLCD editor was measured at about **285 MiB working set**. It exits completely when closed; only the C++ process remains.

## Build

Requires Windows x64, an x64 **LLVM-MinGW** compiler with C++17 support, **JDK 21**, and internet access for the first build. Close PurrLCD and its editor before building.

To build with GPU temperature support:

```powershell
.\engine\sensors-vendor\fetch-adlx.ps1 -Destination .\.tools\adlx-1.5
.\build.ps1 `
  -Clang C:\tools\llvm-mingw\bin\x86_64-w64-mingw32-clang++.exe `
  -AdlxInclude .\.tools\adlx-1.5
```

Omit `-AdlxInclude` to build without ADLX; GPU temperature will be unavailable. `-SkipEditor` builds only the background process. The build runs tests and writes the application to `app/`, preserving `app/data/`. Java is included in the editor package.

## Run

1. Exit the original LCDEditor, including its tray process.
2. Launch `app/editor/PurrLCDEditor.exe` and approve elevation for the background process.
3. Edit the layout, click **Apply**, then **Connect**.
4. Close the editor. Use the tray icon to reopen it or exit PurrLCD.

A successful connection enables reconnection on the next launch. **Stop** disables it. Settings and backgrounds are stored in `app/data/`; keep that folder when moving the application.

## Source

- [`engine/`](engine/) — sensors, rendering, USB HID, IPC and tray controls; [LCD protocol notes](engine/PROTOCOL.md).
- [`editor/`](editor/) — Kotlin / Compose Desktop editor; [build and development notes](editor/README.md).
- `app/`, `.work/`, `.tools/` — local application, build output and tools; excluded from Git.

## License

[MIT](LICENSE) for PurrLCD's own code. See [THIRD_PARTY.md](THIRD_PARTY.md) for dependency licenses, including the optional AMD ADLX SDK.

Unofficial project, not affiliated with COUGAR.

Developed with assistance from ChatGPT Codex.
