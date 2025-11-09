# Module Guide — `app` (AstraStream Demo)

## Purpose
- Demonstrates the full Astra streaming pipeline (preview → encode → package → uplink) with runtime permissions, adaptive bitrate controls, and Compose-based UI.
- Serves as a reference implementation of `AVLiveView` integration and controller orchestration.

## Entry Points
- `app/src/main/java/com/astrastream/streamer/app/LiveActivity.kt`
  - Initialises Compose content in `onCreate()` via `setContent { LiveScreen(...) }` and wires `AVLiveView` through `LiveSessionCoordinator`.
  - Requests camera/microphone/storage permissions using RxPermissions; caches consent in `SharedPreferences` for re-entry.
  - Lazily creates native sender handles (via `NativeSenderFactory`) inside the RTMP dialog to avoid loading native binaries on unsupported ABIs.
  - Cleans up `sender`, stops the live session, and releases the camera in `onDestroy()`.

## Permissions & Stability
- `LiveActivity.requestRuntimePermissions()` handles CAMERA, RECORD_AUDIO, and storage permissions. When denied, preview remains pending until granted.
- Watermark configuration is safe pre-preview: the coordinator defers actual GL calls until the renderer is ready.
- Native sender handles are created lazily (via `NativeSenderFactory`) to avoid loading native binaries on unsupported ABIs.

## Logging
- Use `LogHelper` for important lifecycle events (preview start, encoder changes, bitrate updates, RTMP state).
- Debug builds enable logging by default; release builds should keep output minimal.

## UI Overview
- `LiveScreen` (Compose) is the primary surface. `AndroidView` hosts `AVLiveView`, while Compose manages controls, dialogs, and overlays.
- Controls include:
  - Top-right camera toggle (40dp circular button with translucent background).
  - Bottom-centered LIVE FAB that switches between connect/disconnect states based on session status.
  - Parameter sheet (Tune icon) exposing publish URL, capture/stream resolutions, encoder options, bitrate sliders, pull URL shortcuts, and watermark settings.
- Overlay cards show capture/stream resolution, fps, bitrate bounds, GOP, and encoder selection for quick verification.

## Common Scenarios
- Dynamic bitrate: `live.setVideoBps(bps)` responds to slider input.
- Camera switching: `live.switchCamera()` keeps preview aligned.
- Watermark updates: `coordinator.updateWatermark(text, scale)` queues changes until GL is ready.
- Preview-only mode: leaving the publish URL empty keeps the session in preview while allowing parameter tweaks.

## Styling Notes
- Status/navigation bars are transparent; immersive mode keeps the preview full screen (see `enableImmersiveMode()`).
- Parameter sheet uses Material 3 modal bottom sheet with `LiveUiState` for state hoisting.
- Watermark defaults to bottom-right alignment with resolution-aware scaling.

## Testing Tips
- Validate camera start/stop with permissions toggled, especially after background/foreground transitions.
- Verify adaptive bitrate and encoder switches on representative devices (arm64-v8a).
- When diagnosing preview issues, pair with `tools/diagnose_camera.sh` for reproducible logs.
