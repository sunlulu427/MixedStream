# AGENTS.md - Development Guidelines

## Overview & Responsibilities
- You are an audio/video domain expert, proficient in FFmpeg, OpenGL ES, familiar with performance bottlenecks and optimization strategies across the live streaming pipeline.
- Expert in C++/Kotlin collaborative development, able to design efficient boundary layers between JNI/NDK and upper layers; follows Clean Architecture principles with clear layering, interface-driven design, and testability.
- Changes follow the minimization principle with small commits; iterations or fixes require synchronous documentation updates.

## Project Structure
- `app/`: Demo Android application (Kotlin/Compose). Code in `app/src/main/java`, resources in `app/src/main/res`, tests in `app/src/test`, `app/src/androidTest`.
- `library/`: RTMP streaming SDK. Business logic in `library/src/main/java`; native code in `library/src/main/cpp` (CMake/NDK); resources in `library/src/main/res`; artifacts in `library/build/outputs/aar`.
- `docs/`: Design and process documentation (PlantUML/PNG). Build scripts: root `build.gradle`, module `*/build.gradle`, `settings.gradle`.

## Architecture Overview
This is a Kotlin + C++ streaming toolkit for Android live streaming with RTMP. The codebase follows Clean Architecture principles with clear separation of concerns:

### Core Architecture Layers
1. **UI Layer**: `app/` demo app and `widget/AVLiveView`
2. **Use-Case Layer**: `controller/*` orchestrating capture/encode/package/send
3. **Device/Infrastructure**: `camera/*`, `mediacodec/*`, `stream/*`, native `librtmp`

### Key Components
- **Video Pipeline**: Camera capture → OpenGL rendering (with watermarks) → MediaCodec encoding → FLV packaging → RTMP transmission
- **Audio Pipeline**: Audio capture → preprocessing (AEC/AGC) → MediaCodec encoding → FLV packaging
- **Stream Control**: Native session coordinator (C++ `native_session_bridge`) multiplexes AV streams for RTMP delivery
- **Native Layer**: JNI bridge to librtmp for RTMP protocol implementation

### Data Flow
The end-to-end pipeline processes frames from camera capture through GPU processing, hardware encoding, FLV packaging, and RTMP transmission. Core flow diagrams are in `docs/*.puml`:
- `av_dataflow.puml`: Complete audio/video pipeline
- `video_capture.puml`: Camera setup and sensor flow
- `video_render.puml`: OpenGL rendering and watermarks
- `video_encode.puml`: MediaCodec encoding sessions
- `video_streaming.puml`: FLV muxing and RTMP delivery

## Clean Architecture Principles
- **Dependency Inversion**: High-level policies (Controllers) don't depend on low-level implementations
- **Layer Separation**: UI → Use Cases → Infrastructure, with interactions through interfaces
- **Interface-Driven**: Cross-layer capabilities defined as interfaces before implementation
- **Clear Boundaries**: JNI/NDK, network, hardware calls isolated in infrastructure layer, not exposing specific dependencies upward
- **Testability**: Dependencies injected via interfaces for easy mocking

## Key Usage Pattern
```kotlin
// 1. Configure parameters
live.setAudioConfigure(audioConfig)
live.setVideoConfigure(videoConfig)
live.setCameraConfigure(cameraConfig)

// 2. Start preview
live.startPreview()

// 3. Start streaming (optional)
sender.connect(rtmpUrl)
live.startLive()

// 4. Stop streaming
live.stopLive()
sender.close()
```

## Startup Stability & Common Issues
- **NativeSenderBridge**: Sender handles are created lazily to avoid loading native code on unsupported ABIs (x86 emulators)
- **Camera Fallback**: Automatically falls back to 720×1280 if requested resolution fails
- **Watermark Timing**: Supports deferred watermark application after GL context creation
- **Permission Handling**: Camera operations gracefully defer until permissions granted

## Documentation Generation Standards
- All architecture diagrams uniformly stored in `docs/*.puml` with subtle, selective highlighting
- Color scheme: 🟡 Gold highlighting for core processing components only; gray variants for all other layers to reduce visual noise
- CI generates `docs/generated/<name>.png` and corresponding Markdown through `tools/render_docs.sh` using PlantUML
- Markdown format: First-level title (filename converted to Title Case), embedded `![Title](./name.png)`, and listed `Source` and `Generated` metadata
- Run script manually before local diagram updates to ensure generated artifacts match CI; don't include `docs/generated/` in commits (produced by CI as artifacts)

## Build Commands

### Common Commands
- **Build APK**: `./gradlew :app:assembleDebug`
- **Install APK**: `./gradlew :app:installDebug`
- **Build AAR**: `./gradlew :library:assembleRelease`
- **Run tests**: `./gradlew test`
- **Run instrumented tests**: `./gradlew connectedAndroidTest`
- **Lint checks**: `./gradlew :app:lintDebug :library:lint`
- **Clean**: `./gradlew clean`

### Requirements
- Android SDK 34 (compile/target) with minimum API level 26 (native `AMediaCodec` support)
- Android NDK 27.1.12297006
- CMake 3.22.1
- JDK 17
- Gradle 8.6 with Android Gradle Plugin 8.4.2 and Kotlin 1.9.24

## Development Workflow
- Branch naming: `feature/<module>-<desc>`, `fix/<module>-<issue>`, `perf/<module>-<desc>`
- Local validation: Build, run key use cases; verify camera/GL and streaming connectivity on real devices when necessary
- Self-check list: No uncommitted files, log switches per build type, no new Lint warnings, regression test key paths

## Diagnostics & Automation
- Principle: Self-restart App via adb and capture logs without manual operations; make processes automatically reusable for more efficient and intelligent problem location
- `tools/diagnose_camera.sh` auto-executes `am force-stop` → `am start` → capture key tags (CameraView/CameraHolder/CameraRenderer/GLSurfaceView/EglHelper/SurfaceTexture/CameraService)
- When logs insufficient, first add logging at key paths (camera open/setSurfaceTexture/startPreview, GL onSurfaceCreate/onDraw, updateTexImage), then retest and capture
- Preview black screen troubleshooting: After `./gradlew :app:installDebug`, run `bash tools/diagnose_camera.sh [waitSeconds]` (default 8s). Script waits for preview readiness and focuses on LiveActivity/LiveSessionCoordinator/Camera*, GLSurfaceView/GLThread logs, saving to `diagnostics/camera-startup-<timestamp>.log`; distinguish camera parameter issues from GL texture pipeline anomalies based on `cameraTex`, `state`, `glError` key information

## Command Usage

### Screenshot Capture
Use command line tools to capture screenshots and save to temporary directory:

```bash
# Using adb
mkdir -p temp/screenshots
adb shell screencap -p > temp/screenshots/screenshot_$(date +%Y%m%d_%H%M%S).png

# Using scrcpy (if installed)
scrcpy --record temp/screenshots/recording_$(date +%Y%m%d_%H%M%S).mp4 --time-limit=30
```

### Stream Verification with ffplay
Use ffplay to verify streaming functionality:

```bash
# RTMP stream verification
ffplay rtmp://YOUR_STREAM_SERVER:1935/live/YOUR_STREAM_KEY

# HTTP-FLV stream verification
ffplay http://YOUR_STREAM_SERVER:1935/live/YOUR_STREAM_KEY.flv

# Example with placeholder
ffplay rtmp://example.com:1935/live/test_stream_key
```

Replace `YOUR_STREAM_SERVER` and `YOUR_STREAM_KEY` with actual values when testing.

## Streaming Pipeline Notes
- RTMP initialisation, connection, packet dispatch, shutdown, and resource cleanup follow the sequences documented in the PlantUML diagrams (`docs/av_dataflow.puml`)
- Preview defaults to **720 × 1280 @ 30 fps**, matching common portrait devices
- `CameraView` executes capture setup on the render thread. When the requested profile fails, it falls back to 720 × 1280 and reports the downgrade via `LogHelper`
- H.265/HEVC support includes length-prefixed NALU handling and encoder profile configuration
- The GL render thread stabilises preview/stream FPS at the configured value (default 30 fps) and avoids device-specific overshoot

## Watermark Rendering
- `FboRenderer` (preview) and `EncodeRenderer` (stream) composite watermarks in off-screen FBOs, ensuring the preview matches the encoded output
- Default sizing uses normalised coordinates: watermark height 10–30% of the frame, width derived from bitmap aspect ratio, with ~5% horizontal and ~6% vertical padding from the bottom-right corner
- `Watermark` accepts `Bitmap` or plain text. `scale` adjusts relative size (1.0 baseline). Custom vertex arrays remain supported
- Updating the watermark rebuilds textures and refreshes VBOs. Resolution/rotation changes reuse the latest config while preserving clarity

## Code Style & Naming
- Kotlin/Java: 4 spaces; UpperCamelCase classes; lowerCamelCase methods/fields; UPPER_SNAKE_CASE constants; lowercase package names; snake_case resources and IDs
- C++: `.h`/`.cpp` separation; UpperCamelCase classes, lowerCamelCase methods; RAII; avoid naked pointer leaks; unified error codes to enums

## Logging Standards
- Unified use of `LogHelper`; Debug builds enable `LogHelper.isShowLog = true`; Native uses `__android_log_print`
- Record: Configuration changes, lifecycle (start/stop/connect/close), camera and EGL/Shader/FBO, encoding/decoding events (I frames/bitrate/GOP), queue levels/frame drops, network retries/error codes, JNI parameters and exceptions
- Classification: I key nodes, D details, W recoverable, E failures; sampled output to avoid log flooding; mask URLs/keys
- Runtime logs are persisted to `<external-files>/logs/astra.log` via the native logger

## Testing Standards
- Directories: `app/src/test|androidTest`, `library/src/test|androidTest`; class names ending with `*Test`
- Priority execute `./gradlew test`; involve camera/GL/network then execute `connectedAndroidTest`. Recommend describing test scenarios and device models in PRs

## Commits & PRs
- Commit prefixes: `feat`, `fix`, `perf`, `refactor`, `docs`, `build`, `ci`, can add scope (like `library:`). Example: `app: fix preview rotation`
- PR content: Problem background/objectives, solution and tradeoffs, impact scope, test plan (commands/screenshots/logs), risks and rollback. Keep changes small and focused, ensure builds and tests pass

## Security & Configuration
- Prohibit committing keys and personal paths; don't commit/modify `local.properties`
- Keep tracked large binaries (`library/src/main/cpp/librtmp/libs`) stable; replacements need full justification and prior discussion

## Module-Specific Guidelines

### App Module (`app/`)
- Role: Sample application demonstrating streaming pipeline (preview→encoding→packaging→sending) with permissions and UI interactions
- Entry Activity: `app/src/main/java/com/devyk/av/rtmppush/LiveActivity.kt`
- Configuration & preparation: Bind packager and preview in `init()`; `startPreview()` after permissions ready
- Streaming: Create and connect a `NativeSender` handle from the address dialog (lazy creation to avoid unsupported ABI crashes)
- Permissions & Stability: Camera/recording/storage permissions requested via `LiveActivity.requestRuntimePermissions()`; preview stays pending until granted
- Common scenarios: Dynamic bitrate adjustment: `live.setVideoBps(bps)`. Camera switching: `live.switchCamera()`

### Library Module (`library/`)
- Architecture layers:
  - Capture/rendering: `camera/*`, `camera/renderer/*`, `widget/*`
  - Encoding/decoding: `mediacodec/*`
  - Native packaging: `src/main/cpp/stream/*`
  - Sending: `stream/sender/*` (including `rtmp`)
  - Controllers: `controller/*`
  - Configuration/callbacks: `config/*`, `callback/*`
  - Native: `src/main/cpp/*` (JNI + `librtmp` static library)
- Key points:
  - GLSurface & rendering thread: Custom `GLSurfaceView` + `GLThread`; EGL initialization on rendering thread, no GL calls on UI thread
  - Watermark setting: Supports deferred application: `CameraView.setWatermark()` only caches when renderer/GL not ready, auto-applies after GL onCreate callback to avoid `lateinit renderer` crashes
  - Camera management: `CameraHolder` unified open/start/stop/release; exception capture and degradation
  - Encoding/decoding: Video: `VideoMediaCodec`, `VideoEncoder`; audio similar. Output SPS/PPS is now fed directly into the FLV/RTMP pipeline by the native session controller
  - Sending (RTMP): `NativeSenderBridge` routes all commands to JNI; lazy handle creation avoids unsupported ABI crashes
- JNI/NDK: CMake: `library/src/main/cpp/CMakeLists.txt`. Target library: `astra` (SHARED); links `librtmp.a` and `log`. STL: `c++_shared`. Supported ABI: `arm64-v8a`
