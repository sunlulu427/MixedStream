# Module Guide — library (RTMP SDK)

## 架构分层
- 采集/渲染：`camera/*`, `camera/renderer/*`, `widget/*`（见 `docs/video_capture.puml`, `docs/video_render.puml`）
- 编解码：`mediacodec/*`（见 `docs/video_encode.puml`）
- 封包：`stream/packer/*`
- 发送：`stream/sender/*`（含 `rtmp`）
- 控制器：`controller/*`
- 配置/回调：`config/*`, `callback/*`
- 原生：`src/main/cpp/*`（JNI + `librtmp` 静态库）

## 关键点与易错项
- GLSurface 与渲染线程：
  - 自定义 `GLSurfaceView` + `GLThread`；EGL 初始化于渲染线程，不在 UI 线程做 GL 调用。
- Watermark 设置（启动崩溃修复点）：
  - 现在支持延迟应用：`CameraView.setWatermark()` 在 renderer/GL 未就绪时仅缓存，GL onCreate 回调后自动应用，避免 `lateinit renderer` 空引用崩溃。
  - FBO 渲染在 `FboRenderer`；贴图创建需 GL 上下文，请勿在 EGL 建立前做 GLES 调用。
  - `Watermark.scale` 控制屏幕占比，默认依据文本位图尺寸，可在应用层调整。
- 相机管理：
  - `CameraHolder` 统一 open/start/stop/release；异常捕获并降级（硬件占用、无相机、无权限）。
- 编解码：
  - 视频：`VideoMediaCodec`、`VideoEncoder`；音频同理。输出 SPS/PPS 由 `StreamController` 透传给打包器。
- 发送（RTMP）：
  - `RtmpSender` 通过 JNI 调用 native；懒加载 native 库，避免不支持 ABI 的崩溃。参考 `docs/video_streaming.puml`。

## JNI/NDK
- CMake：`library/src/main/cpp/CMakeLists.txt`
  - 目标库：`AVRtmpPush`（SHARED）；链接 `librtmp.a` 和 `log`。
  - STL：`c++_shared`。
- 支持 ABI：`armeabi-v7a`、`arm64-v8a`（见 Gradle 与 CMake）。

## 配置建议
- `VideoConfiguration`/`AudioConfiguration`/`CameraConfiguration` 覆盖分辨率、帧率、码率、编码器选择。
- 通过 `AVLiveView` 统一承载人机接口，`StreamController` 组织音视频、打包与发送。

## 日志
- `LogHelper` 输出关键节点：相机/EGL/Shader/FBO、I 帧/码率/GOP、队列水位/丢帧、网络错误等。

## 构建
- `./gradlew :library:assembleRelease` 生成 AAR。
- NDK/SDK 版本要求参见根 AGENTS.md。

## 诊断流程（Camera 黑屏）
- 自动化原则：通过 adb 脚本自行重启并抓日志，避免人工操作。使用根目录 `tools/diagnose_camera.sh`。
- 重点观察：
  - Camera：`openCamera`、`setSurfaceTexture`、`startPreview` 是否成功；`BufferQueueProducer connect` 是否建立。
  - GL：`onSurfaceCreate`、`fbo bind success`、`onDraw`；是否有 `bindTextureImage: clearing GL error: 0x500` 等报错。
  - 帧驱动：`onFrameAvailable` 是否触发；若无则检查 `startPreview` 与 `updateTexImage` 调用时序。
- 常见原因与修复：
  - 未就绪调用水印：已支持延迟应用，避免 `lateinit renderer` 崩溃。
  - OES 纹理未绑定/采样器未设置：在 `CameraRenderer` 中绑定 `GL_TEXTURE_EXTERNAL_OES` 并设置 `sTexture` 采样器（已修复）。
- 视口尺寸错误：渲染到 FBO 时使用 FBO 尺寸；绘制到屏幕再用屏幕尺寸（已修复）。
- 预览裁剪：`updateAspect()` 根据相机/视图比例调整纹理坐标，保持画面不变形。
- SurfaceTexture 缓冲大小不匹配：对 `SurfaceTexture` 调用 `setDefaultBufferSize(width,height)`（已修复）。
