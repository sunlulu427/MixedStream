# Module Guide — app

## 角色
- 示例应用，演示推流链路（预览→编码→封包→发送）与权限、UI 交互。

## 入口与页面
- 入口 Activity：`app/src/main/java/com/astrastream/streamer/app/LiveActivity.kt`
  - 初始化：`onCreate()` 中调用 `initializeEnvironment()`，创建 Compose 视图后立即准备打包器并触发 `ensurePreview()`。
  - 权限：`requestRuntimePermissions()` 通过 RxPermissions 请求所需权限，并在成功后缓存到 `SPUtils`。
  - 推流：从地址弹窗中创建并连接 `RtmpSender`（惰性创建，避免不支持 ABI 的崩溃）。
  - 生命周期：`onDestroy()` 关闭 `sender`、停止直播、释放相机。

## 权限与稳定性
- 相机/录音/存储权限由 `LiveActivity.requestRuntimePermissions()` 触发，未授权时通过 `coordinator.markPreviewPending()` 暂停预览。
- 为避免启动崩溃：
  - 水印设置支持延迟应用（见 library/AGENTS.md），即使未预览也不会崩溃。
  - `RtmpSender` 在连接前惰性创建，并捕获 `UnsatisfiedLinkError` 给出友好提示。

## 日志
- 关键路径日志统一使用 `LogHelper`；Debug 构建开启日志。

## UI
- 顶部切换摄像头按钮：40dp 半透明圆形背景（`bg_icon_circle`），内边距 8dp。
- 底部 LIVE 按钮：44dp 高度、16sp 粗体、圆角高亮背景（`bg_button_yellow_round`）。
- 水印：`Watermark("text", color, textSize, null, scale)` 中 `scale` 控制屏幕占比，示例使用 1.3。

## 常用场景
- 动态码率调整：`live.setVideoBps(bps)`。
- 切换摄像头：`live.switchCamera()`。

## 构建
- `./gradlew :app:assembleDebug` 生成 APK；安装：`./gradlew :app:installDebug`。

## 自动化诊断
- 使用根目录脚本 `tools/diagnose_camera.sh` 自动完成 adb 重启与关键日志抓取（不依赖人工点按）。
