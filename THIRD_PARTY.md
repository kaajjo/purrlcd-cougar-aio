# Third-party components

The root MIT license covers original PurrLCD code only. Third-party components retain their own copyright notices and licenses.

| Component | Use | License and source |
| --- | --- | --- |
| nlohmann/json 3.12.0 | Vendored C++ header | [MIT license](engine/third_party/JSON-LICENSE.txt), [upstream](https://github.com/nlohmann/json) |
| PawnIO AMDFamily17 module 0.2.11 | External replaceable signed module | LGPL 2.1 or later; [notice, license and corresponding source archive](engine/sensors-vendor/pawnio/NOTICE.txt) |
| Gradle wrapper 9.6.0 | Build tooling | [Apache 2.0](editor/gradle/wrapper/GRADLE-LICENSE), [notice](editor/gradle/wrapper/GRADLE-NOTICE) |
| LLVM-MinGW runtime | Native binary build dependency | [Bundled distribution license](engine/third_party/LLVM-MINGW-LICENSE.txt); compiler is not in Git |
| Kotlin, Compose, kotlinx libraries | Editor dependencies fetched by Gradle | Upstream licenses and notices remain applicable; see [Compose](https://github.com/JetBrains/compose-multiplatform) and [Kotlin](https://github.com/JetBrains/kotlin) |
| Java runtime | Generated local editor package | Runtime licenses are retained under `app/editor/runtime/legal` |
| AMD ADLX SDK | Optional external compile dependency | AMD SDK agreement; [integration notice](engine/sensors-vendor/NOTICE.txt) |

PurrLCD does not bundle or install the PawnIO driver or AMD display driver. The installed runtime libraries have separate terms. The PawnIO module remains a separate file, with its license and matching source archive available beside it.

AMD ADLX headers, driver DLLs and generated executables are excluded from Git. The SDK agreement contains distribution and end-user conditions for software using it. Treat a binary release built with that optional integration separately from the MIT-licensed source project; review the [official agreement](https://github.com/GPUOpen-LibrariesAndSDKs/ADLX/blob/d9f04a9bba022d6cf6333f005dd540b4ad19fb63/ADLX%20SDK%20License%20Agreement.pdf) before publishing such binaries.

No extracted vendor application code, firmware, vendor logos, personal images, user settings or device logs are included in the repository. References to COUGAR and its application identify supported hardware and interoperability behavior; they are not PurrLCD branding or an endorsement.
