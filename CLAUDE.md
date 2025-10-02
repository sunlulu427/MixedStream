# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
- Android SDK 34 (compile/target) with minimum API level 21
- Android NDK 27.1.12297006
- CMake 3.22.1
- JDK 17
- Gradle 8.6 with Android Gradle Plugin 8.4.2 and Kotlin 1.9.24

## Architecture Overview

This is a Kotlin + C++ streaming toolkit for Android live streaming with RTMP. The codebase follows Clean Architecture principles with clear separation of concerns:

### Project Structure
- **`app/`**: Demo Android application ("AstraStream") using Jetpack Compose
- **`library/`**: Reusable RTMP streaming SDK
- **`docs/`**: PlantUML diagrams and generated documentation
- **`tools/`**: Build and diagnostic scripts

### Core Architecture Layers
1. **UI Layer**: `app/` demo app and `widget/AVLiveView`
2. **Use-Case Layer**: `controller/*` orchestrating capture/encode/package/send
3. **Device/Infrastructure**: `camera/*`, `mediacodec/*`, `stream/*`, native `librtmp`

### Key Components
- **Video Pipeline**: Camera capture → OpenGL rendering (with watermarks) → MediaCodec encoding → FLV packaging → RTMP transmission
- **Audio Pipeline**: Audio capture → preprocessing (AEC/AGC) → MediaCodec encoding → FLV packaging
- **Stream Control**: `StreamController` multiplexes AV streams for RTMP delivery
- **Native Layer**: JNI bridge to librtmp for RTMP protocol implementation

### Data Flow
The end-to-end pipeline processes frames from camera capture through GPU processing, hardware encoding, FLV packaging, and RTMP transmission. Core flow diagrams are in `docs/*.puml`:
- `av_dataflow.puml`: Complete audio/video pipeline
- `video_capture.puml`: Camera setup and sensor flow
- `video_render.puml`: OpenGL rendering and watermarks
- `video_encode.puml`: MediaCodec encoding sessions
- `video_streaming.puml`: FLV muxing and RTMP delivery

## Development Workflow

### Clean Architecture Principles
- **Dependency Inversion**: High-level policies (Controllers) don't depend on low-level implementations
- **Layer Separation**: UI → Use Cases → Infrastructure, with interactions through interfaces
- **Interface-Driven**: Cross-layer capabilities defined as interfaces before implementation
- **Testability**: Dependencies injected via interfaces for easy mocking

### Key Usage Pattern
```kotlin
// 1. Configure parameters
live.setAudioConfigure(audioConfig)
live.setVideoConfigure(videoConfig)
live.setCameraConfigure(cameraConfig)

// 2. Start preview
live.startPreview()

// 3. Start streaming (optional)
sender.connect(rtmpUrl)
packer.start()
live.startLive()

// 4. Stop streaming
live.stopLive()
sender.close()
packer.stop()
```

### Stability Notes
- **RtmpSender**: Lazy initialization to avoid crashes on unsupported ABIs (x86 emulators)
- **Camera Fallback**: Automatically falls back to 720×1280 if requested resolution fails
- **Watermark Timing**: Supports deferred watermark application after GL context creation
- **Permission Handling**: Camera operations gracefully defer until permissions granted

## Tooling & Diagnostics

### Scripts
- **`tools/render_docs.sh`**: Generate PNG/Markdown from PlantUML diagrams
- **`tools/diagnose_camera.sh [waitSeconds]`**: Automated camera/GL troubleshooting via ADB

### Logging
- Use `LogHelper` for consistent logging across Kotlin/Java
- Native code uses `__android_log_print`
- Key tags: `CameraView`, `CameraHolder`, `CameraRenderer`, `GLSurfaceView`, `StreamController`
- Debug builds enable verbose logging via `LogHelper.isShowLog = true`

## Code Style

### Kotlin/Java
- 4 spaces indentation
- Classes: UpperCamelCase
- Methods/fields: lowerCamelCase
- Constants: UPPER_SNAKE_CASE
- Resources: snake_case

### C++
- Header/implementation separation (.h/.cpp)
- RAII principles
- Avoid raw pointer leaks
- Unified error codes via enums

## Agent Guidelines

### Repository Overview & Responsibilities
- You are an audio/video domain expert, proficient in FFmpeg, OpenGL ES, familiar with performance bottlenecks and optimization strategies across the live streaming pipeline.
- Expert in C++/Kotlin collaborative development, able to design efficient boundary layers between JNI/NDK and upper layers; follows Clean Architecture principles with clear layering, interface-driven design, and testability.
- Changes follow the minimization principle with small commits; iterations or fixes require synchronous documentation updates (README/Core.md/docs).

### Project Structure
- `app/`: Sample Android application (Kotlin/Java). Code in `app/src/main/java`, resources in `app/src/main/res`, tests in `app/src/test`, `app/src/androidTest`.
- `library/`: RTMP streaming SDK. Business logic in `library/src/main/java`; native code in `library/src/main/cpp` (CMake/NDK); resources in `library/src/main/res`; artifacts in `library/build/outputs/aar`.
- `docs/`: Design and process documentation (PlantUML/PNG). Build scripts: root `build.gradle`, module `*/build.gradle`, `settings.gradle`.

### Architecture Overview
- Pipeline: Capture/Rendering (`camera/*`, `camera/renderer/*`) → Encoding/Decoding (`mediacodec/*`) → Packaging (FLV, `stream/packer/*`) → Sending (RTMP, `stream/sender/*`) → Native `librtmp`.
- Dependencies: `app` depends on `library` only at the demo layer; `library` calls `src/main/cpp` through JNI and links precompiled `librtmp`.
- Key components: Control layer (`controller/*`), audio/video pipelines (`camera/*`, `mediacodec/*`), packaging/sending (`stream/*`), configuration/callbacks (`config/*`, `callback/*`), UI components (`widget/AVLiveView`).

### Clean Architecture Principles
- **Inward Dependencies**: High-level policies (Controller/configuration interfaces) don't depend on low-level implementations; all implementations depend inward on interfaces/abstractions.
- **Layer Responsibilities**: UI (`app`/`widget`) → Use case coordination layer (`controller`) → Data/device implementation (`camera`, `mediacodec`, `stream`, `sender`). Cross-layer interactions must go through interfaces or data models.
- **Interface-Driven**: Any cross-layer capabilities (camera, streaming, packaging, logging) define interfaces before implementation for easy replacement and testing.
- **Clear Boundaries**: JNI/NDK, network, hardware calls isolated in infrastructure layer, not exposing specific dependencies upward; upper layers only touch abstract capabilities.
- **Testability**: Control layer injects dependencies via interfaces for easy fake implementation replacement in use case testing.

### Usage Overview (from README/Core)
- Preview view: Use `com.astrastream.avpush.widget.AVLiveView` in layout, configure parameters through `setAudioConfigure`, `setVideoConfigure`, `setCameraConfigure`.
- Startup sequence: `startPreview()` → (prepare) → `mSender.connect()` → `mPacker.start()` → `live.startLive()`.
- Dynamic bitrate: `live.setVideoBps(bps)`.
- Shutdown: `live.stopLive()` → `mSender.close()` → `mPacker.stop()`.
- Core flow diagrams:
  - Audio/video data flow: `docs/av_dataflow.puml`
  - Video capture: `docs/video_capture.puml`
  - Video rendering: `docs/video_render.puml`
  - Video encoding: `docs/video_encode.puml`
  - Streaming packaging: `docs/video_streaming.puml`

### Documentation Generation Standards
- All architecture diagrams uniformly stored in `docs/*.puml` with subtle, selective highlighting.
- Color scheme: 🟡 Gold highlighting for core processing components only; gray variants for all other layers to reduce visual noise.
- CI generates `docs/generated/<name>.png` and corresponding Markdown through `tools/render_docs.sh` using PlantUML.
- Markdown format: First-level title (filename converted to Title Case), embedded `![Title](./name.png)`, and listed `Source` and `Generated` metadata.
- Run script manually before local diagram updates to ensure generated artifacts match CI; don't include `docs/generated/` in commits (produced by CI as artifacts).
- See `docs/COLOR_SCHEME.md` for detailed minimalist color guidelines and selective highlighting principles.

### Startup Stability & Common Issues
- Avoid crashes:
  - Cause: Creating `RtmpSender()` early in Activity initialization loads `libAVRtmpPush.so`, causing `UnsatisfiedLinkError` crashes on unsupported ABIs (like x86 emulators).
  - Handling: `RtmpSender` uses lazy creation, only instantiated when user initiates streaming, with try/catch fallback for user notification.
- Watermark timing:
  - Cause: `renderer` in `CameraView` is `lateinit`; calling `setWatermark` before preview starts or GL is ready triggers `lateinit` crashes.
  - Handling: Supports deferred watermark application. `CameraView.setWatermark()` caches watermarks and auto-applies after GL onCreate callback to avoid crashes.
- Permissions and preview:
  - Camera operations defer when camera permissions not granted, with related operations (camera switching, watermark setting) delayed and logged via `LogHelper.w`.

### Build & Run (Summary)
- Environment: Android SDK 34, NDK 27.1.12297006, CMake 3.22.1, Java 17.
- Common commands:
  - Build APK: `./gradlew :app:assembleDebug`; Install: `./gradlew :app:installDebug`
  - Build AAR: `./gradlew :library:assembleRelease`
  - Tests: `./gradlew test`, `./gradlew connectedAndroidTest`
  - Lint: `./gradlew :app:lintDebug :library:lint`

### Module Descriptions
- Application (`app/`): Sample entry `LiveActivity` demonstrating full streaming pipeline and permission handling; module AGENTS.md describes UI, permissions, and logging.
- SDK (`library/`): Core streaming capabilities; module AGENTS.md describes OpenGL/camera, encoding, packaging, sending, JNI/NDK details and considerations.

### Environment & Build
- Requirements: Android SDK 34, NDK 27.1.12297006, CMake 3.22.1, Java 17.
- Configuration: Set `sdk.dir`/`ndk.dir` in local `local.properties` (don't commit).
- Common commands:
  - Build APK: `./gradlew :app:assembleDebug`; Install: `./gradlew :app:installDebug`
  - Build AAR: `./gradlew :library:assembleRelease`
  - Tests: `./gradlew test`, `./gradlew connectedAndroidTest`
  - Check/Clean: `./gradlew :app:lintDebug :library:lint`, `./gradlew clean`

### Development Workflow
- Branch naming: `feature/<module>-<desc>`, `fix/<module>-<issue>`, `perf/<module>-<desc>`.
- Local validation: Build, run key use cases; verify camera/GL and streaming connectivity on real devices when necessary.
- Self-check list: No uncommitted files, log switches per build type, no new Lint warnings, regression test key paths.

### Diagnostics & Automation
- Principle: Self-restart App via adb and capture logs without manual operations; make processes automatically reusable for more efficient and intelligent problem location.
- Suggested script: `tools/diagnose_camera.sh` auto-executes `am force-stop` → `am start` → capture key tags (CameraView/CameraHolder/CameraRenderer/GLSurfaceView/EglHelper/SurfaceTexture/CameraService).
- When logs insufficient, first add logging at key paths (camera open/setSurfaceTexture/startPreview, GL onSurfaceCreate/onDraw, updateTexImage), then retest and capture.
- Preview black screen troubleshooting: After `./gradlew :app:installDebug`, run `bash tools/diagnose_camera.sh [waitSeconds]` (default 8s). Script waits for preview readiness and focuses on LiveActivity/LiveSessionCoordinator/Camera*, GLSurfaceView/GLThread logs, saving to `diagnostics/camera-startup-<timestamp>.log`; distinguish camera parameter issues from GL texture pipeline anomalies based on `cameraTex`, `state`, `glError` key information.
- Added LogHelper output in `CameraHolder` and `CameraRenderer` covering open/start/updateTexImage and onSurfaceCreate/onDraw for precise preview black screen root cause location; follow unified tags and sampling strategies for new scenarios.

### Code Style & Naming
- Kotlin/Java: 4 spaces; UpperCamelCase classes; lowerCamelCase methods/fields; UPPER_SNAKE_CASE constants; lowercase package names; snake_case resources and IDs (like `camera_view`).
- C++: `.h`/`.cpp` separation; UpperCamelCase classes, lowerCamelCase methods; RAII; avoid naked pointer leaks; unified error codes to enums.

### Logging Standards (Key Paths)
- Unified use of `LogHelper`; Debug builds enable `LogHelper.isShowLog = true`; Native uses `__android_log_print`.
- Record: Configuration changes, lifecycle (start/stop/connect/close), camera and EGL/Shader/FBO, encoding/decoding events (I frames/bitrate/GOP), queue levels/frame drops, network retries/error codes, JNI parameters and exceptions.
- Classification: I key nodes, D details, W recoverable, E failures; sampled output to avoid log flooding; mask URLs/keys.

### Testing Standards
- Directories: `app/src/test|androidTest`, `library/src/test|androidTest`; class names ending with `*Test`.
- Priority execute `./gradlew test`; involve camera/GL/network then execute `connectedAndroidTest`. Recommend describing test scenarios and device models in PRs.

### Commits & PRs
- Commit prefixes: `feat`, `fix`, `perf`, `refactor`, `docs`, `build`, `ci`, can add scope (like `library:`). Example: `app: fix preview rotation`.
- PR content: Problem background/objectives, solution and tradeoffs, impact scope, test plan (commands/screenshots/logs), risks and rollback. Keep changes small and focused, ensure builds and tests pass.

### Security & Configuration
- Prohibit committing keys and personal paths; don't commit/modify `local.properties`.
- Keep tracked large binaries (`library/src/main/cpp/librtmp/libs`) stable; replacements need full justification and prior discussion.

## Module-Specific Agent Guidelines

### App Module (`app/`)
- Role: Sample application demonstrating streaming pipeline (preview→encoding→packaging→sending) with permissions and UI interactions.
- Entry & Pages: Entry Activity `app/src/main/java/com/devyk/av/rtmppush/LiveActivity.kt`
  - Configuration & preparation: Bind packager and preview in `init()`; `startPreview()` after permissions ready.
  - Streaming: Create and connect `RtmpSender` from address dialog (lazy creation to avoid unsupported ABI crashes).
  - Lifecycle: `onDestroy()` closes `sender`, stops live streaming, releases camera.
- Permissions & Stability: Camera/recording/storage permissions requested via `BaseActivity.checkPermission()`; no preview start when unauthorized.
- Logging: Key path logs unified using `LogHelper`; Debug builds enable logging.
- UI: Top camera switch button: 40dp semi-transparent circular background (`bg_icon_circle`), 8dp inner padding. Bottom LIVE button: 44dp height, 16sp bold, rounded highlight background (`bg_button_yellow_round`). Watermark: `Watermark("text", color, textSize, null, scale)` where `scale` controls screen ratio, example uses 1.3.
- Common scenarios: Dynamic bitrate adjustment: `live.setVideoBps(bps)`. Camera switching: `live.switchCamera()`.
- Build: `./gradlew :app:assembleDebug` generates APK; Install: `./gradlew :app:installDebug`.
- Automated diagnostics: Use root directory script `tools/diagnose_camera.sh` to auto-complete adb restart and key log capture (no manual tapping dependency).

### Library Module (`library/`)
- Architecture layers:
  - Capture/rendering: `camera/*`, `camera/renderer/*`, `widget/*` (see `docs/video_capture.puml`, `docs/video_render.puml`)
  - Encoding/decoding: `mediacodec/*` (see `docs/video_encode.puml`)
  - Packaging: `stream/packer/*`
  - Sending: `stream/sender/*` (including `rtmp`)
  - Controllers: `controller/*`
  - Configuration/callbacks: `config/*`, `callback/*`
  - Native: `src/main/cpp/*` (JNI + `librtmp` static library)
- Key points & error-prone items:
  - GLSurface & rendering thread: Custom `GLSurfaceView` + `GLThread`; EGL initialization on rendering thread, no GL calls on UI thread.
  - Watermark setting (startup crash fix): Now supports deferred application: `CameraView.setWatermark()` only caches when renderer/GL not ready, auto-applies after GL onCreate callback to avoid `lateinit renderer` null reference crashes. FBO rendering in `FboRenderer`; texture creation needs GL context, don't make GLES calls before EGL establishment. `Watermark.scale` controls screen ratio, defaults based on text bitmap size, adjustable at application layer.
  - Camera management: `CameraHolder` unified open/start/stop/release; exception capture and degradation (hardware occupied, no camera, no permissions).
  - Encoding/decoding: Video: `VideoMediaCodec`, `VideoEncoder`; audio similar. Output SPS/PPS transparently passed to packager by `StreamController`.
  - Sending (RTMP): `RtmpSender` calls native via JNI; lazy load native library to avoid unsupported ABI crashes. Reference `docs/video_streaming.puml`.
- JNI/NDK: CMake: `library/src/main/cpp/CMakeLists.txt`. Target library: `AVRtmpPush` (SHARED); links `librtmp.a` and `log`. STL: `c++_shared`. Supported ABIs: `armeabi-v7a`, `arm64-v8a` (see Gradle and CMake).
- Configuration suggestions: `VideoConfiguration`/`AudioConfiguration`/`CameraConfiguration` cover resolution, frame rate, bitrate, encoder selection. Unified human-machine interface via `AVLiveView`, `StreamController` organizes audio/video, packaging and sending.
- Logging: `LogHelper` outputs key nodes: camera/EGL/Shader/FBO, I frames/bitrate/GOP, queue levels/frame drops, network errors, etc.
- Build: `./gradlew :library:assembleRelease` generates AAR. NDK/SDK version requirements see root AGENTS.md.
- Diagnostic process (Camera black screen): Automation principle: auto-restart and capture logs via adb scripts, avoid manual operations. Use root directory `tools/diagnose_camera.sh`. Focus observations: Camera: whether `openCamera`, `setSurfaceTexture`, `startPreview` succeed; whether `BufferQueueProducer connect` established. GL: `onSurfaceCreate`, `fbo bind success`, `onDraw`; whether errors like `bindTextureImage: clearing GL error: 0x500`. Frame driving: whether `onFrameAvailable` triggers; if not, check `startPreview` and `updateTexImage` call timing. Common causes & fixes: Unready watermark calls: Now supports deferred application, avoiding `lateinit renderer` crashes. OES texture not bound/sampler not set: Bind `GL_TEXTURE_EXTERNAL_OES` and set `sTexture` sampler in `CameraRenderer` (fixed). Viewport size errors: Use FBO size when rendering to FBO; use screen size when drawing to screen (fixed). Preview cropping: `updateAspect()` adjusts texture coordinates based on camera/view ratio to maintain undistorted images. SurfaceTexture buffer size mismatch: Call `setDefaultBufferSize(width,height)` on `SurfaceTexture` (fixed).