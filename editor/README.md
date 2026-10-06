# PurrLCD Editor

A Compose Desktop / Material 3 editor for the C++ background engine. Closing the window exits the JVM; the engine keeps updating the LCD.

## Build and run

Requires JDK 21. The bundled wrapper downloads Gradle 9.6.0 on the first build. From this directory:

```powershell
.\gradlew.bat --gradle-user-home ../.work/editor-build/gradle-home --project-cache-dir ../.work/editor-build/project-cache test createDistributable
```

The portable package is written to `.work/editor-build/build/compose/binaries/main/app/PurrLCDEditor`, relative to the repository root. It includes a Java runtime, so users do not need to install Java.

```powershell
PurrLCDEditor.exe --engine "C:\path\engine\PurrLCD.exe" --data "C:\path\data"
```

Without arguments, the packaged editor looks for `engine/PurrLCD.exe` and `data/` beside its own application directory. Use `--no-engine-start` to inspect the editor without launching the engine. The engine can reconnect to the LCD on startup if that preference was saved; the editor's Connect/Stop button controls the connection.

## Interface

- **Screen:** 720 × 720 preview, connection controls and current sensor readings.
- **Editor:** background, CPU/GPU layers, draggable positions, font size, labels, colors and visibility.
- **Settings:** refresh interval, display rotation and brightness (0–100%; minimum keeps a dim backlight).
- **Apply:** saves the scene through the engine. Preview edits alone do not save it; connecting also saves pending edits first.

Colors, typography and shapes live in `ui/theme`. The interface uses Material 3 components and a dark theme with a peach accent.

## Code layout

```text
purrlcd/
  Main.kt                 window creation and application shutdown
  app/                    launch options and native image picker
  model/                  serializable scene and text layers
  engine/                 named-pipe client and engine messages
  ui/
    EditorScreen.kt       connects application state to the UI
    EditorContent.kt      layout and page selection without engine access
    state/                editor state, actions, polling and preview updates
    panels/               overview, appearance and display settings
    components/           navigation, cards, fields and shared controls
    preview/              image loading and preview rendering
    theme/                colors, typography, shapes and value formatting
    tooling/              IDE previews with sample data
```

Panels receive state and callbacks rather than calling the engine directly. `rememberEditorState` ties polling and preview updates to the screen's lifetime. `EditorContent` can render without an engine client or background tasks. `Main.kt` closes the IPC client when the window exits.

## Engine communication

The editor uses the Windows named pipe `PurrLCD-<username>`. Each request opens a separate connection and sends a 4-byte little-endian length followed by UTF-8 JSON, up to 1 MiB. Opening a busy pipe is retried for up to 500 ms before any bytes are sent.

Requests time out after 3 seconds. Blocking Windows I/O runs on a single daemon thread so a stalled engine cannot freeze the UI or prevent JVM shutdown. Status polling runs only while the editor is open. Scene edits trigger a preview request after a 250 ms delay; changes to displayed integer temperatures also refresh the preview.

Two IPC tests use temporary Windows pipes without accessing the physical LCD. They cover framing/JSON exchange and a stalled server, including timeout and nonblocking client shutdown. Preview tests cover layer matching and drag coordinates.

## IDE previews

`ui/tooling` contains `@Preview` functions for all three pages, individual panels, navigation, cards, fields and the LCD layout. They use sample data and the PurrLCD theme without creating an `EngineClient` or launching the engine. Sample backgrounds have an empty file path.

The annotation is `androidx.compose.ui.tooling.preview.Preview` from `org.jetbrains.compose.ui:ui-tooling-preview:1.12.1`. After Gradle Sync, open a preview function in an IDE with Compose Desktop preview support, such as one configured with the Kotlin Multiplatform plugin. Preview availability depends on the IDE and plugin versions. No Android target is required.

## Strings and localization

UI strings live in `src/main/composeResources`:

- `values/strings.xml`: English and fallback strings.
- `values-ru/strings.xml`: Russian.

Compose Resources generates `purrlcd.resources.Res` and typed string identifiers. Use `stringResource(Res.string.apply)` in composables and `getString(Res.string.scene_saved)` in suspend functions. For parameters, use XML placeholders such as `%1$d s` and pass the value with `stringResource(Res.string.duration_seconds, seconds)`. Use `<plurals>` and `pluralStringResource` when plural forms are needed.

The JVM locale selects the language at startup. Add `values-<language-code>/strings.xml` with matching keys for another translation. There is no in-app language switch. User-defined layer labels and filenames are left as entered; C++ diagnostic messages are displayed as received.

`StringResourcesTest` checks resource loading, English and Russian strings, parameter substitution and English fallback for unsupported languages.

References: [resource setup](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-setup.html), [strings and plurals](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html), [locale selection](https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html).

## Dependencies and licenses

Versions are pinned in `build.gradle.kts`: Kotlin 2.4.20, Compose 1.12.1, Material 3 1.12.0-alpha03, kotlinx.coroutines 1.10.2 and kotlinx.serialization 1.9.0. Material 3 has a separate version and is currently an alpha dependency. Packaging uses the [Compose Gradle plugin](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html).

The Gradle wrapper is distributed under Apache 2.0. Its [LICENSE](gradle/wrapper/GRADLE-LICENSE) and [NOTICE](gradle/wrapper/GRADLE-NOTICE) come from the Gradle 9.6.0 distribution. Dependency and bundled Java runtime licenses remain separate from the project's MIT license; see [THIRD_PARTY.md](../THIRD_PARTY.md).
