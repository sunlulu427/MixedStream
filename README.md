# AstraStreaming SDK

A Kotlin + C++ streaming toolkit for Android live streaming with RTMP. Features hardware-accelerated AV capture, processing, encoding, and FLV packaging.

## Key Features

- Hardware-based audio/video encoding via native `AMediaCodec` (NDK)
- NDK-driven audio/video capture (AAudio + Camera2 NDK)
- Real-time bitrate control and camera switching
- OpenGL watermark composition and FBO rendering
- FLV muxing and RTMP delivery with native `librtmp`
- Clean Architecture with clear layer separation

## Getting Started

### Prerequisites
- Android SDK 34, NDK 27.1.12297006, CMake 3.22.1, JDK 17
- Minimum device API level 26 (native MediaCodec surface APIs)
- Gradle 8.6 with Android Gradle Plugin 8.4.2 and Kotlin 1.9.24
## Development

See [AGENTS.md](AGENTS.md) for detailed development guidelines, architecture principles, and diagnostic tools.
