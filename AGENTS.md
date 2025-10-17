# Repository Guidelines

## Overview & Expectations
- You are the resident audio/video specialist. Assume deep knowledge of FFmpeg, GL ES, Android camera, and live streaming bottlenecks.
- The codebase follows Clean Architecture: clear boundaries, dependency inversion, interface-driven contracts, and testability.
- Prefer the smallest viable change. Keep revisions scoped and update documentation (`README`, `Core.md`, `docs/`) whenever behaviour changes.

## Project Layout
- `app/`: demo Android application (Kotlin/Compose). Code lives in `app/src/main/java`, resources in `app/src/main/res`, tests under `app/src/test` and `app/src/androidTest`.
- `library/`: Astra streaming SDK. JVM sources in `library/src/main/java`, native code in `library/src/main/cpp`, resources in `library/src/main/res`, artifacts in `library/build/outputs/aar`.
- `docs/`: architecture diagrams (PlantUML) and high-level notes.
- Build scripts: root `build.gradle.kts`, module-level `*/build.gradle.kts`, `settings.gradle.kts`.

## Architecture Highlights
- Pipeline: capture/render (`camera/*`, `camera/renderer/*`) → encode (`mediacodec/*`) → package (FLV, `stream/packer/*`) → transport (RTMP, `stream/sender/*`) → native `librtmp`.
- Dependencies: the demo app only consumes the SDK. The SDK talks to native code exclusively through JNI abstractions and links against the prebuilt `librtmp` static library.
- Key components: controllers (`controller/*`), AV pipeline (`camera/*`, `mediacodec/*`), packer/sender (`stream/*`), configuration callbacks (`config/*`, `callback/*`), and UI widgets (`widget/AVLiveView`).

## Clean Architecture Checklist
- **Inward Dependencies**: high-level policy (controllers, configuration contracts) never depends on concrete hardware/API implementations.
- **Layered Responsibilities**: UI (`app`, `widget`) → orchestration (`controller`) → data & device access (`camera`, `mediacodec`, `stream`, `sender`). Cross-layer communication goes through interfaces or data models.
- **Interface First**: add abstractions before implementations (camera, streaming, packaging, logging). This keeps swapability and testing simple.
- **Explicit Boundaries**: JNI, network, and hardware calls stay in infrastructure layers. Upper layers operate on abstractions only.
- **Testability**: controllers accept injected interfaces; provide fakes for unit tests.

## Usage Snapshot (see `README`/`Core.md` for details)
1. Place `com.astrastream.avpush.widget.AVLiveView` in the layout.
2. Configure audio, video, and camera parameters through the exposed setters.
3. Start sequence: `startPreview()` → `sender.connect()` → `packer.start()` → `live.startLive()`.
4. Adjust bitrate dynamically with `live.setVideoBps(bps)`.
5. Stop sequence: `live.stopLive()` → `sender.close()` → `packer.stop()`.

## Diagram & Documentation Rules
- Store all architecture diagrams inside `docs/*.puml`.
- CI runs `tools/render_docs.sh` to render PNG + Markdown into `docs/generated/<name>.png` with matching `.md` (artifact only, do not commit generated output).
- Generated Markdown uses Title Case for the main heading, embeds `![Title](./name.png)`, and lists `Source`/`Generated` metadata.
- Before committing diagram changes, run the render script locally to verify output.

## Startup Stability
- **Lazy native loading**: instantiate `RtmpSender` only when the user initiates streaming to avoid `UnsatisfiedLinkError` on unsupported ABIs.
- **Watermark timing**: `CameraView.setWatermark()` can be called before GL is ready; the view buffers requests until the renderer is initialised to avoid `lateinit` crashes.
- **Permissions**: when camera permission is missing the system delays `startPreview()`. Defer toggles (camera switch, watermark) until preview starts and emit warnings through `LogHelper`.

## Build & Tooling
- Environment: Android SDK 34, NDK 27.1.12297006, CMake 3.22.1, JDK 17, Gradle 8.6.
- Common commands:
  - Build APK: `./gradlew :app:assembleDebug`
  - Install APK: `./gradlew :app:installDebug`
  - Build AAR: `./gradlew :library:assembleRelease`
  - Unit tests: `./gradlew test`
  - Lint: `./gradlew :app:lintDebug :library:lint`

## Development Workflow
- Branch naming: `feature/<module>-<desc>`, `fix/<module>-<issue>`, `perf/<module>-<desc>`.
- Local validation: run builds and key scenarios; use real devices when investigating camera/GL/RTMP.
- Pre-flight checklist: clean working tree, logging flags match build type, no new lint warnings, key paths regressed manually.

## Diagnostics & Automation
- Automate repro: prefer scripts + adb over manual steps.
- `tools/diagnose_camera.sh`: runs `am force-stop`, `am start`, waits for preview, and captures targeted logs (Camera*, GLSurfaceView, GLThread, etc.) into `diagnostics/camera-startup-<timestamp>.log`.
- If logs are insufficient, add scoped `LogHelper` statements (camera open, `setSurfaceTexture`, `startPreview`, GL lifecycle) before rerunning diagnostics.
- For black screen preview issues, install the demo then execute `bash tools/diagnose_camera.sh [waitSeconds]` (default 8s). Inspect `cameraTex`, `state`, `glError` to triage camera parameter vs. GL texture problems.

## Coding Conventions
- **Kotlin/Java**: 4-space indent; classes UpperCamelCase; methods/fields lowerCamelCase; constants UPPER_SNAKE_CASE; packages lowercase; resources snake_case.
- **C++**: separate headers/sources; classes UpperCamelCase; methods lowerCamelCase; use RAII; avoid raw pointer leaks; consolidate error codes into enums.

## Logging
- Use `LogHelper`; enable logs in debug builds via `LogHelper.isShowLog = true`.
- Native side logs through `__android_log_print`.
- Capture lifecycle milestones (start/stop/connect/close), camera/EGL/shader/FBO events, codec status (I-frame/bitrate/GOP), queue depth & drops, network retries/errors, and JNI parameter/exception details.
- Keep logs sampling-friendly; redact secrets (URLs/keys).

## Testing
- Tests reside in `app/src/test|androidTest` and `library/src/test|androidTest`; suffix classes with `*Test`.
- Run `./gradlew test` first. Use `connectedAndroidTest` when flows depend on camera/GL/network.
- Document device models and scenarios in PR descriptions.

## Commits & PRs
- Commit prefixes: `feat`, `fix`, `perf`, `refactor`, `docs`, `build`, `ci` (optionally scoped, e.g., `library:`).
- PR template: problem statement, proposed solution and trade-offs, impact, testing plan (commands/screenshots/logs), risk & rollback.
- Keep changes focused and ensure CI/testing is green.

## Security & Configuration
- Never commit secrets or local paths; leave `local.properties` untouched.
- The prebuilt binaries in `library/src/main/cpp/librtmp/libs` are tracked—discuss replacements before attempting updates.

## Quick Start Recap
1. Add `AVLiveView` to the layout (configure `fps`, `preview_width/height`, bitrate bounds in XML if desired).
2. Configure audio/video/camera parameters in code, then call `live.startPreview()` (after camera permission is granted).
3. Create `RtmpSender`, call `setDataSource(url)` and `connect()`. Once `onConnected` fires: `packer.start()` + `live.startLive()`.
4. Stop with `live.stopLive()`, `sender.close()`, `packer.stop()`.
