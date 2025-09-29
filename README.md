# AVRtmpPushSDK

AVRtmpPushSDK is a Kotlin + C++ streaming toolkit that delivers end-to-end AV capture, processing, encoding, FLV packaging, and RTMP uplink. The repository contains a demonstration Android app and a reusable library that powers hardware-accelerated live streaming pipelines.

## Contents

- [Key Capabilities](#key-capabilities)
- [Architecture Overview](#architecture-overview)
- [Getting Started](#getting-started)
- [Live Session Lifecycle](#live-session-lifecycle)
- [Extensibility](#extensibility)
- [Tooling & Diagnostics](#tooling--diagnostics)

## Key Capabilities

- Hardware-based audio and video encoding via `MediaCodec`
- Adaptive bitrate control at runtime
- Camera parameter tuning with front/back switching
- Audio preprocessing (AEC/AGC) and capture configuration APIs
- OpenGL watermark composition and FBO based rendering
- FLV muxing and RTMP delivery backed by `librtmp`
- Orientation-aware preview with seamless live/preview transitions

## Architecture Overview

The project follows a clean architecture split:

- **UI Layer** – `app/` demo and `widget/AVLiveView`
- **Use-Case Layer** – `controller/*` orchestrating capture/encode/package/send
- **Device/Infrastructure** – `camera/*`, `mediacodec/*`, `stream/*`, native `librtmp`

Core data-flow and component diagrams are authored in PlantUML:

| Diagram | Purpose |
| --- | --- |
| [`docs/av_dataflow.puml`](docs/av_dataflow.puml) | High-level audio/video pipeline from capture to RTMP |
| [`docs/video_capture.puml`](docs/video_capture.puml) | Camera setup, preview, and sensor flow |
| [`docs/video_render.puml`](docs/video_render.puml) | GL thread, FBO, and watermark rendering |
| [`docs/video_encode.puml`](docs/video_encode.puml) | `MediaCodec` session management |
| [`docs/video_streaming.puml`](docs/video_streaming.puml) | FLV muxing and RTMP sender interactions |

> CI runs `tools/render_docs.sh` to publish PNG/Markdown artifacts in the `docs-diagrams` workflow artifact. Run the script locally to regenerate visuals as needed.

## Getting Started

### Prerequisites

- Gradle 8.6 with Android Gradle Plugin 8.4.2 and Kotlin 1.9.24
- JDK 17
- Android SDK 34 (compile/target) with minimum API level 21
- Android NDK 27.1.12297006
- CMake 3.22.1

### Clone and Build

```bash
git clone git@github.com:sunlulu427/AVRtmpPushSDK.git
cd AVRtmpPushSDK

# assemble demo app & release AAR
./gradlew :app:assembleDebug :library:assembleRelease
```

The demo Activity (`LiveActivity`) showcases camera permission handling, preview, and streaming controls.

## Live Session Lifecycle

`AVLiveView` bridges UI interactions to the streaming pipeline through the `LiveStreamSession` interface:

```kotlin
class LiveActivity : AppCompatActivity() {
    private val live: AVLiveView by lazy { findViewById(R.id.live) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live)

        live.setAudioConfigure(AudioConfiguration.createDefault())
        live.setVideoConfigure(
            VideoConfiguration.Builder()
                .setBps(400, 800)
                .setFps(25)
                .setSize(1280, 720)
                .setMediaCodec(true)
                .build()
        )
        live.setCameraConfigure(
            CameraConfiguration.Builder()
                .setFacing(CameraConfiguration.Facing.BACK)
                .setFps(25)
                .setPreview(1280, 720)
                .build()
        )

        val packer = RtmpPacker()
        live.setPacker(packer)

        val sender = RtmpSender().apply {
            setDataSource("rtmp://<host>/<app>/<stream>")
        }
        live.setSender(sender)
    }

    private fun startStreaming() {
        live.startPreview()
        live.startLive()
    }
}
```

Typical runtime sequence:

1. Configure audio, video, and camera parameters
2. Attach packer (`FLV`) and sender (`RTMP`)
3. Call `startPreview()` to spin up camera GL pipeline
4. Open RTMP connection and invoke `startLive()`
5. Use `setVideoBps()` for adaptive bitrate, `setMute()` for audio mute
6. Stop via `live.stopLive()`, `sender.close()`, `packer.stop()`

Watermarks can be queued before GL initialization—`AVLiveView` applies them once the renderer is ready.

## Extensibility

Implement `LiveStreamSession` to swap streaming strategies (e.g., different packer, custom sender) without touching UI code:

```kotlin
class FileRecordingSession : LiveStreamSession { /* ... */ }

live.attachStreamSession(FileRecordingSession())
```

The interface exposes lifecycle hooks (`prepare`, `start`, `pause`, `resume`, `stop`) and configuration APIs, promoting dependency inversion and testability.

## Tooling & Diagnostics

- `tools/render_docs.sh`: generate diagram PNG/Markdown in `docs/generated/`
- `tools/diagnose_camera.sh`: automated ADB script for camera/GL troubleshooting
- GitHub Actions (`.github/workflows/ci.yml`): builds the app/AAR and uploads rendered diagrams for every push/PR

Refer to [AGENTS.md](AGENTS.md) for detailed development guidelines, Clean Architecture rules, and logging/diagnostic expectations.
