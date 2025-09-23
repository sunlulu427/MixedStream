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

## 环境与构建
- 要求：Android SDK 29、NDK 21.1.6352462、CMake 3.10.2、Java 8。
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
