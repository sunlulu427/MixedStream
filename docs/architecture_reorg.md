# Architecture Reorganization

## Goals
- Collapse all possible business logic into the C++ core, keeping the Java layer as a minimal API surface that exposes only lifecycle and configuration entry points.
- Reevaluate and merge redundant infrastructure classes (for example, consolidate `NativeLogger`, `AstraLog`, and helper wrappers into a single cross-language logging channel) to simplify ownership and configuration.
- Preserve Clean Architecture boundaries (UI → Controllers → Infrastructure) while shifting the execution responsibility for controllers and infrastructure toward native modules.

## Target Layering

| Layer | Responsibility | Key Notes |
| --- | --- | --- |
| Java API | Provide the thinnest possible surface for apps to configure audio/video parameters, start/stop preview/live, and receive callbacks. All methods should delegate to JNI without additional branching or caching logic. | Compose operators, LiveActivity, and DI modules expose only configuration data classes and lifecycle calls. |
| JNI Bridge | Single gateway converting Kotlin data classes to native structs and routing callbacks back to the UI. No orchestration or transformation should stay here beyond marshaling. | Use one `NativeSessionBridge` that owns handles for capture, render, encode, and stream pipelines. |
| C++ Core | Implement the full capture → render → encode → package → send pipeline, including state management, error recovery, and adaptive strategies. | Encapsulate camera, OpenGL ES processing, MediaCodec bindings, FLV muxing, RTMP sender, watermark pipeline, and diagnostics entirely in C++. |

## Implementation Principles
1. **Minimal Java surface**
   - Convert current controllers (`LiveController`, `CameraController`, etc.) into thin facades that immediately invoke native commands.
   - All policy decisions (fallback resolutions, bitrate adaptation, watermark scheduling, retry loops) migrate to native modules.
   - Expose only immutable config objects and callback interfaces in Java to keep API ergonomic for integrators.

2. **Comprehensive native orchestration**
   - Centralize state machines inside the native session coordinator so that camera, encoder, muxer, and sender share the same lifecycle graph.
   - Move queue management, flow control, and performance counters into C++ so diagnostics reflect the actual runtime behavior without Java-side sampling.
   - Guarantee that each JNI entry point maps to a deterministic native command (`StartPreview`, `StartLive`, `StopLive`, `UpdateWatermark`, etc.).

3. **Unified logging channel**
   - Replace `NativeLogger`, `AstraLog`, and similar classes with a single logging service that is initialized once from Java and consumed from both C++ and Kotlin.
   - Provide level filtering and sink selection (logcat, file, remote upload) through native configuration to avoid duplicate toggles.

## Class Consolidation Plan
- **Logging**: Implement `AstraLogger` (placeholder name) in C++ and expose lightweight setters in Java for log level, file path, and callback hooks. Remove `NativeLogger`, `AstraLog`, and any wrapper that does not contribute unique functionality.
- **Sender Bridges**: Collapse multiple JNI handle wrappers (`NativeSenderBridge`, specialized controller proxies) into a single session-oriented bridge so that resource ownership remains inside native code.
- **Render/Encode Helpers**: Merge overlapping utility classes (e.g., separate GL renderers for preview vs. encode) when their behavior is identical; keep configurable flags to switch between FBO targets.

## Next Steps
1. Enumerate every Java class that contains business decisions and create a migration checklist to relocate logic into corresponding native modules.
2. Design the consolidated native logging and diagnostics module, ensuring compatibility with existing tooling.
3. Refactor JNI interfaces to match the new command set, updating the Kotlin demo (app module) to verify the reduced API surface.
4. Validate performance and stability on target devices after the migration, paying special attention to camera fallback, watermark timing, and RTMP retries.

