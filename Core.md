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

## Watermark Rendering
- `FboRenderer` (preview) and `EncodeRenderer` (stream) composite watermarks in off-screen FBOs, ensuring the preview matches the encoded output.
- Default sizing uses normalised coordinates: watermark height 10–30% of the frame, width derived from bitmap aspect ratio, with ~5% horizontal and ~6% vertical padding from the bottom-right corner.
- `Watermark` accepts `Bitmap` or plain text. `scale` adjusts relative size (1.0 baseline). Custom vertex arrays remain supported.
- Updating the watermark rebuilds textures and refreshes VBOs. Resolution/rotation changes reuse the latest config while preserving clarity.
