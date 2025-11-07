# Module Guide — `library` (Astra Streaming SDK)

## Layering Overview
- Capture/Render: `camera/*`, `camera/renderer/*`, `widget/*` (see `docs/video_capture.puml`, `docs/video_render.puml`).
- Encode: native `src/main/cpp/codec/*` (see `docs/video_encode.puml`).
- Transport: `stream/sender/*` (RTMP sender + JNI bridge) backed by native FLV/AMF muxing.
- Controllers: `controller/*` coordinate the pipeline end-to-end.
- Config/Callback: `config/*`, `callback/*` define host-facing contracts.
- Native: `src/main/cpp/*` (muxing, queueing, RTMP glue, `librtmp` static library).

## Key Considerations
- **GLSurface & Render Thread**: EGL must be created on the render thread. Avoid GL calls from the UI thread.
- **Watermark Application**: `CameraView.setWatermark()` buffers requests until GL is ready; watermarks are applied once `onSurfaceCreated` fires to prevent `lateinit` crashes.
- **FBO Rendering**: `FboRenderer` manages off-screen rendering. Create textures only after the EGL context is live.
- **Camera Management**: `CameraHolder` centralises open/start/stop/release and downgrades gracefully when hardware is busy or unavailable.
- **Codec Output**: Kotlin controllers supply raw surfaces/PCM; native `NativeStreamEngine` drives `AMediaCodec` encoders and pushes encoded frames directly into the FLV/RTMP muxer.
- **RTMP Sender**: `RtmpStreamSession` calls into native code lazily, avoiding ABI mismatches. Refer to `docs/video_streaming.puml` for flow details.

## Native Build
- CMake target: `astra` (shared library) linked with `librtmp.a` and `log`.
- STL: `c++_shared`.
- Supported ABI: `arm64-v8a`.

## Configuration Tips
- Use `VideoConfiguration`/`AudioConfiguration`/`CameraConfiguration` to define resolution, frame rate, bitrate, and encoder preferences.
- `AVLiveView` hosts the full user interaction surface while `StreamController` orchestrates audio, video, and transport.

## Logging
- Keep `LogHelper` statements at key checkpoints: camera/EGL/shader/FBO lifecycle, I-frame cadence, bitrate adjustments, queue depth, network retries/errors.

## Build
- Run `./gradlew :library:assembleRelease` to produce an AAR.
- Global build requirements are listed in the root `AGENTS.md`.

## Native C++ Standards
- Remove template header comments (e.g., `// Created by ...`). Start files with includes and keep spacing compact.
- Mark every override explicitly with `override`, including pure virtual overrides.
- Manage ownership rigorously: match `new[]/delete[]`, `malloc/free`, and clear `RTMPPacket` queues before destruction.
- `IThread` exposes helper methods to spawn and join worker threads; guard against double start/stop.
- JNI callbacks must promote objects to `GlobalRef` and clean up once native work is done. Use attach/detach guards when crossing threads.

## Diagnostics (Camera Black Screen)
- Prefer automation via `tools/diagnose_camera.sh` (force-stop → launch → wait → capture logs).
- Inspect Camera logs (`openCamera`, `setSurfaceTexture`, `startPreview`), GL logs (`onSurfaceCreate`, `onDraw`, FBO bind status), and frame signals (`onFrameAvailable`).
- Common fixes:
  - Watermark invoked before renderer ready ⇒ rely on deferred application.
  - OES texture/sampler not bound ⇒ ensure `GL_TEXTURE_EXTERNAL_OES` is bound with the proper sampler uniform.
  - Viewport mismatch ⇒ use FBO dimensions for off-screen rendering and view size for final blit.
  - SurfaceTexture buffer mismatch ⇒ call `setDefaultBufferSize(width, height)` before starting preview.
