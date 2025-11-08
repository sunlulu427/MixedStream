# AstraStreaming SDK

A Kotlin + C++ streaming toolkit for Android live streaming with RTMP. Features hardware-accelerated AV capture, processing, encoding, and FLV packaging.

## Key Features

- Hardware-based audio/video encoding via native `AMediaCodec` (NDK)
- NDK-driven audio/video capture (AAudio + Camera2 NDK)
- Real-time bitrate control and camera switching
- OpenGL watermark composition and FBO rendering
- FLV muxing and RTMP delivery with native `librtmp`
- Clean Architecture with clear layer separation

## Architecture

- **UI Layer**: `app/` demo and `widget/AVLiveView`
- **Use-Case Layer**: `controller/*` orchestrating capture/encode/package/send
- **Infrastructure**: `camera/*`, native `capture/*`, `codec/*`, `stream/*`, RTMP bridge via `librtmp`

## Getting Started

### Prerequisites
- Android SDK 34, NDK 27.1.12297006, CMake 3.22.1, JDK 17
- Minimum device API level 26 (native MediaCodec surface APIs)
- Gradle 8.6 with Android Gradle Plugin 8.4.2 and Kotlin 1.9.24

### Build
```bash
# Build demo app and library
./gradlew :app:assembleDebug :library:assembleRelease
```

### Basic Usage
```kotlin
// 1. Configure parameters
live.setAudioConfigure(audioConfig)
live.setVideoConfigure(videoConfig)
live.setCameraConfigure(cameraConfig)

// 2. Start preview
live.startPreview()

// 3. Start streaming
sender.connect(rtmpUrl)
live.startLive()

// 4. Stop streaming
live.stopLive()
sender.close()
```

## Development

See [AGENTS.md](AGENTS.md) for detailed development guidelines, architecture principles, and diagnostic tools.
