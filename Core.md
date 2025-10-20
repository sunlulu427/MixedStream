# Core Architecture Notes

## Streaming Pipeline
- RTMP initialisation, connection, packet dispatch, shutdown, and resource cleanup follow the sequences documented in the PlantUML diagrams (`docs/av_dataflow.puml`).
- Diagrams adopt a restrained palette: 🟡 gold highlights the critical processing path while grey variants annotate supporting layers. Render assets with `./tools/render_docs.sh` when updating diagrams locally.

## Startup Stability
- To avoid `UnsatisfiedLinkError` on unsupported ABIs (e.g., x86 emulators), `RtmpSender` defers native loading until the user explicitly starts streaming.
- Unsupported devices surface a toast instead of crashing. The SDK officially targets `arm64-v8a`.

## Camera Defaults & Fallbacks
- Preview defaults to **720 × 1280 @ 30 fps**, matching common portrait devices.
- `CameraView` executes capture setup on the render thread. When the requested profile fails, it falls back to 720 × 1280 and reports the downgrade via `LogHelper`.
- `LiveSessionCoordinator` receives `onCameraPreviewSizeSelected` and keeps UI state aligned, preventing aspect ratio drift and black screens.
- `LiveActivity` embeds `AVLiveView` through Compose (`AndroidView`). The floating Tune button expands the parameter sheet (publish URL, capture/stream resolution, encoder selection, bitrate controls).
- The floating action button adapts to state: prompts for RTMP URL when empty, connects when provided, and stops when streaming.
- Preview-only mode remains available with an empty publish URL.
- A live info card (top left) surfaces capture/stream resolution, frame rate, bitrate bounds, GOP, and encoder to support quick validation.
- Watermark text scales with capture resolution to stay legible across displays.
- The demo operates in immersive mode (transparent status/navigation bars, cutout support) for full-screen previews.
- The parameter sheet lists derived pull URLs (RTMP and HTTP-FLV) at the bottom for fast copy/paste during validation.

## Encoding & Frame Rate
- H.265/HEVC support includes length-prefixed NALU handling and encoder profile configuration to resolve previously broken push streams.
- The GL render thread stabilises preview/stream FPS at the configured value (default 30 fps) and avoids device-specific overshoot (e.g., 50+ fps) that otherwise bursts bitrate.

## Logging & Diagnostics
- Runtime logs are persisted to `<external-files>/logs/astra.log` via the native logger. Initialise logging once from the host app (e.g., `LogHelper.initialize(context)`) before starting preview/streaming.
- Capture, encode, and transport nodes emit lifecycle markers, format transitions, bitrate/FPS samples, and error messages to the same log. The native GL renderer records shader setup and watermark texture updates.
- Retrieve artefacts with `adb pull /sdcard/Android/data/<package>/files/logs/astra.log` when analysing customer reproductions.

## Watermark Rendering
- `FboRenderer` (preview) and `EncodeRenderer` (stream) composite watermarks in off-screen FBOs, ensuring the preview matches the encoded output.
- Default sizing uses normalised coordinates: watermark height 10–30% of the frame, width derived from bitmap aspect ratio, with ~5% horizontal and ~6% vertical padding from the bottom-right corner.
- `Watermark` accepts `Bitmap` or plain text. `scale` adjusts relative size (1.0 baseline). Custom vertex arrays remain supported.
- Updating the watermark rebuilds textures and refreshes VBOs. Resolution/rotation changes reuse the latest config while preserving clarity.

## Screen Recording Mode
- `LiveActivity` now exposes a single Compose surface with a segmented control that switches between camera and screen live flows. Both modes share the same `LiveSessionCoordinator` lifecycle, while screen live delegates to `ScreenLiveSessionCoordinator` for MediaProjection setup and pipeline orchestration.
- `ScreenStreamController` reuses the GStreamer-style pipeline (`StreamingPipeline`) while swapping capture sources:
  - **Video:** `ScreenVideoController` drives `ScreenRecorder`, which delegates to `VulkanScreenRenderer`. The renderer hosts a `VirtualDisplay`, consumes RGBA frames from `MediaProjection`, and paints them onto the encoder surface via `HardwareRenderer` (Vulkan backend) so no OpenGL surface is touched.
  - **Audio:** `ScreenAudioController` composes microphone and playback streams. `MixedAudioProcessor` captures PCM from the mic (`AudioRecord`) and, when API ≥ 29, playback via `AudioPlaybackCaptureConfiguration`, performing 16‑bit saturating mixes before AAC encoding.
- `ScreenCaptureConfiguration` expresses capture bounds (width, height, dpi, fps, includeMic/includePlayback) and is forwarded through `setScreenCapture`. Coordinators regenerate the configuration whenever toggles change.
- `ScreenLiveSessionCoordinator` maintains a shared `MutableState<ScreenLiveUiState>` so Compose, overlay, and pipeline logic stay in sync. `setOverlayObserver` forwards state snapshots to `ScreenOverlayManager` so bitrate/FPS updates propagate even when the overlay is visible.
- `ScreenOverlayManager` renders a draggable-style floating card using `WindowManager` (`TYPE_APPLICATION_OVERLAY`) when the app is backgrounded, showing live status and providing a tap-back shortcut into `LiveActivity`. Overlay usage is optional and requires the user to grant `SYSTEM_ALERT_WINDOW` permission via settings.
- Transport remains unchanged: `TransportNode` assembles encoded frames, configures `Sender`, and forwards payloads to native `librtmp`.
- Refer to [`docs/screen_streaming.puml`](docs/screen_streaming.puml) when updating this pipeline; run `./tools/render_docs.sh` to refresh rendered assets locally.
