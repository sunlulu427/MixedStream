# Module Guide — app

## 角色
- 示例应用，演示推流链路（预览→编码→封包→发送）与权限、UI 交互。

## 入口与页面
- 入口 Activity：`app/src/main/java/com/devyk/av/rtmppush/LiveActivity.kt`
  - 配置与准备：在 `init()` 中绑定打包器与预览；权限就绪后再 `startPreview()`。
  - 推流：从地址弹窗中创建并连接 `RtmpSender`（惰性创建，避免不支持 ABI 的崩溃）。
  - 生命周期：`onDestroy()` 关闭 `sender`、停止直播、释放相机。

## 权限与稳定性
- 相机/录音/存储权限通过 `BaseActivity.checkPermission()` 请求；未授权时不启动预览。
- 为避免启动崩溃：
  - 水印设置支持延迟应用（见 library/AGENTS.md），即使未预览也不会崩溃。
  - `RtmpSender` 在连接前惰性创建，并捕获 `UnsatisfiedLinkError` 给出友好提示。

## 日志
- 关键路径日志统一使用 `LogHelper`；Debug 构建开启日志。

## 常用场景
- 动态码率调整：`live.setVideoBps(bps)`。
- 切换摄像头：`live.switchCamera()`。

## 构建
- `./gradlew :app:assembleDebug` 生成 APK；安装：`./gradlew :app:installDebug`。

