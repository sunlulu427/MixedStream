# Repository Guidelines

## 概览与职责
- 你是音视频领域专家，精通 FFmpeg、OpenGL ES，熟悉直播链路各环节的性能瓶颈与优化策略。
- 擅长 C++/Kotlin 协同开发，能在 JNI/NDK 与上层间设计高效边界层；遵循《架构整洁之道》，分层清晰、接口驱动、可测试。
- 改动遵循最小化原则，小步提交；迭代或修复需同步更新文档（README/Core.md/docs）。

## 项目结构
- `app/`：示例 Android 应用（Kotlin/Java）。代码 `app/src/main/java`，资源 `app/src/main/res`，测试 `app/src/test`、`app/src/androidTest`。
- `library/`：RTMP 推流 SDK。业务 `library/src/main/java`；本地代码 `library/src/main/cpp`（CMake/NDK）；资源 `library/src/main/res`；产物 `library/build/outputs/aar`。
- `docs/`：设计与流程（PlantUML/PNG）。构建脚本：根 `build.gradle`、模块 `*/build.gradle`、`settings.gradle`。

## 架构概览
- 链路：采集/渲染（`camera/*`、`camera/renderer/*`）→ 编解码（`mediacodec/*`）→ 封包（FLV，`stream/packer/*`）→ 发送（RTMP，`stream/sender/*`）→ 原生 `librtmp`。
- 依赖：`app` 仅示例层依赖 `library`；`library` 通过 JNI 调用 `src/main/cpp` 并链接预编译 `librtmp`。
- 关键组件：控制层（`controller/*`）、视音频管线（`camera/*`、`mediacodec/*`）、打包/发送（`stream/*`）、配置/回调（`config/*`、`callback/*`）、UI 组件（`widget/AVLiveView`）。

### Clean Architecture 基本原则
- **依赖内向**：高层策略（Controller/配置接口）不依赖底层实现，所有实现向内依赖接口/抽象。
- **分层职责**：UI（`app`/`widget`）→ 用例协调层（`controller`）→ 数据/设备实现（`camera`、`mediacodec`、`stream`、`sender`）。跨层交互必须通过接口或数据模型。
- **接口驱动**：任何跨层能力（相机、推流、打包、日志）先定义接口后实现，便于替换与测试。
- **边界明确**：JNI/NDK、网络、硬件调用隔离在基础设施层，不向上暴露具体依赖；上层仅接触抽象能力。
- **可测试性**：控制层以接口注入依赖，便于替换假实现（Fake）进行用例测试。

### 使用概览（提炼自 README/Core）
- 预览视图：在布局使用 `com.astrastream.avpush.widget.AVLiveView`，通过 `setAudioConfigure`、`setVideoConfigure`、`setCameraConfigure` 配置参数。
- 启动顺序：`startPreview()` →（准备）→ `mSender.connect()` → `mPacker.start()` → `live.startLive()`。
- 动态码率：`live.setVideoBps(bps)`。
- 结束：`live.stopLive()` → `mSender.close()` → `mPacker.stop()`。
- 核心流程图：
  - 音视频数据流：`docs/av_dataflow.puml`
  - 视频采集：`docs/video_capture.puml`
  - 视频渲染：`docs/video_render.puml`
  - 视频编码：`docs/video_encode.puml`
  - 推流封包：`docs/video_streaming.puml`

### 文档生成规范
- 所有架构图统一存放于 `docs/*.puml`。
- CI 通过 `tools/render_docs.sh` 使用 PlantUML 生成 `docs/generated/<name>.png` 与同名 Markdown。
- Markdown 格式：一级标题（文件名转为 Title Case）、内嵌 `![Title](./name.png)`、并罗列 `Source` 与 `Generated` 元数据。
- 本地更新图表前请手动运行脚本，确保生成物与 CI 一致，提交时勿包含 `docs/generated/`（由 CI 产出并以 artifact 形式提供）。

### 启动稳定性与常见问题
- 避免崩溃：
  - 原因：早期在 Activity 初始化阶段创建 `RtmpSender()` 会加载 `libAVRtmpPush.so`，在不支持的 ABI（如 x86 模拟器）上导致 `UnsatisfiedLinkError` 并崩溃。
  - 处理：`RtmpSender` 采用惰性创建，仅在用户发起推流时实例化，并通过 try/catch 兜底提示。
- 水印设置时机：
  - 原因：`CameraView` 中 `renderer` 为 `lateinit`，未开始预览或 GL 尚未就绪时调用 `setWatermark` 会触发 `lateinit` 崩溃。
  - 处理：支持延迟水印生效。`CameraView.setWatermark()` 会缓存水印，待 GL onCreate 回调后自动应用，避免崩溃。
- 权限与预览：
  - 相机权限未授予时不会调用 `startPreview()`，相关操作（如切换相机、设置水印）会延后，日志以 `LogHelper.w` 输出。

## 构建与运行（提炼）
- 环境：Android SDK 34、NDK 27.1.12297006、CMake 3.22.1、Java 17。
- 常用命令：
  - 构建 APK：`./gradlew :app:assembleDebug`；安装：`./gradlew :app:installDebug`
  - 构建 AAR：`./gradlew :library:assembleRelease`
  - 测试：`./gradlew test`、`./gradlew connectedAndroidTest`
  - Lint：`./gradlew :app:lintDebug :library:lint`

## 模块说明
- 应用（`app/`）：示例入口 `LiveActivity`，演示推流全链路与权限处理；模ysave00块内 AGENTS.md 描述 UI、权限与日志。
- SDK（`library/`）：核心推流能力；模块内 AGENTS.md 描述 OpenGL/相机、编码、打包、发送、JNI/NDK 细节与注意事项。

## 环境与构建
- 要求：Android SDK 34、NDK 27.1.12297006、CMake 3.22.1、Java 17。
- 配置：于本机 `local.properties` 设置 `sdk.dir`/`ndk.dir`（勿提交）。
- 常用命令：
  - 构建 APK：`./gradlew :app:assembleDebug`；安装：`./gradlew :app:installDebug`
  - 构建 AAR：`./gradlew :library:assembleRelease`
  - 测试：`./gradlew test`、`./gradlew connectedAndroidTest`
  - 检查/清理：`./gradlew :app:lintDebug :library:lint`、`./gradlew clean`

## 开发工作流
- 分支命名：`feature/<module>-<desc>`、`fix/<module>-<issue>`、`perf/<module>-<desc>`。
- 本地校验：构建、运行关键用例；必要时在真机验证相机/GL 与推流连通性。
- 自检清单：无未提交文件、日志开关按构建类型、Lint 无新增告警、回归关键路径。

### 诊断与自动化
- 原则：自行通过 adb 重启 App 并抓取日志，不依赖人工操作；让流程自动可复用，问题定位更高效、更智能。
- 建议脚本：`tools/diagnose_camera.sh` 自动执行 `am force-stop` → `am start` → 抓取关键 tag（CameraView/CameraHolder/CameraRenderer/GLSurfaceView/EglHelper/SurfaceTexture/CameraService）。
- 日志不足时，先在关键路径补齐日志（相机 open/setSurfaceTexture/startPreview、GL onSurfaceCreate/onDraw、updateTexImage），再复测抓取。
- 预览黑屏排查：`./gradlew :app:installDebug` 后执行 `bash tools/diagnose_camera.sh [waitSeconds]`（默认 8s），脚本会等待预览就绪并聚焦 LiveActivity/LiveSessionCoordinator/Camera*、GLSurfaceView/GLThread 等日志，同时落盘至 `diagnostics/camera-startup-<timestamp>.log`；根据 `cameraTex`、`state`、`glError` 等关键信息即可区分相机参数问题与 GL 纹理解链路异常。
- 已在 `CameraHolder` 与 `CameraRenderer` 增补 LogHelper 输出，覆盖 open/start/updateTexImage 以及 onSurfaceCreate/onDraw，便于精确定位预览黑屏根因；如需新增场景请沿用统一标签与采样策略。

## 代码风格与命名
- Kotlin/Java：4 空格；类 UpperCamelCase；方法/字段 lowerCamelCase；常量 UPPER_SNAKE_CASE；包名小写；资源与 ID 使用 snake_case（如 `camera_view`）。
- C++：`.h`/`.cpp` 分离；UpperCamelCase 类、lowerCamelCase 方法；RAII；避免裸指针泄露；错误码统一到枚举。

## 日志规范（关键路径）
- 统一使用 `LogHelper`；Debug 构建启用 `LogHelper.isShowLog = true`；Native 用 `__android_log_print`。
- 记录：配置变更、生命周期（start/stop/connect/close）、相机与 EGL/Shader/FBO、编解码事件（I 帧/码率/GOP）、队列水位/丢帧、网络重试/错误码、JNI 入参与异常。
- 分级：I 关键节点、D 细节、W 可恢复、E 失败；采样输出避免刷屏；对 URL/密钥打码。

## 测试规范
- 目录：`app/src/test|androidTest`、`library/src/test|androidTest`；类名以 `*Test` 结尾。
- 优先执行 `./gradlew test`；涉及相机/GL/网络再执行 `connectedAndroidTest`。推荐在 PR 中描述测试场景与设备型号。

## 提交与 PR
- 提交前缀：`feat`、`fix`、`perf`、`refactor`、`docs`、`build`、`ci`，可加作用域（如 `library:`）。示例：`app: fix preview rotation`。
- PR 内容：问题背景/目标、方案与取舍、影响范围、测试计划（命令/截图/日志）、风险与回滚。改动小而聚焦，保证构建与测试通过。

## 安全与配置
- 禁止提交密钥与个人路径；勿提交/修改 `local.properties`。
- 已跟踪的大型二进制（`library/src/main/cpp/librtmp/libs`）保持稳定，替换需充分论证并先讨论。
---

附：快速开始（简版）
- 在布局添加 `AVLiveView` 并按需 XML 配置 `fps`、`preview_width/height`、`videoMinRate/MaxRate`。
- 代码中配置音视频与相机参数，调用 `live.startPreview()`（需相机权限）。
- 创建 `RtmpSender` 并 `setDataSource(url)` → `connect()`；收到 `onConnected` 后 `mPacker.start()` + `live.startLive()`。
- 停止：`live.stopLive()` → `mSender.close()` → `mPacker.stop()`。
