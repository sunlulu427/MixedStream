
## 核心逻辑说明
- rtmp 初始化、建联、发包、停止、释放等 
![img.png](docs/rtmp.png)

### 启动稳定性
- 为避免在不支持的 ABI（如 x86/x86_64 模拟器）上因加载 `libAVRtmpPush.so` 失败导致的启动崩溃，`RtmpSender` 改为惰性创建：仅在用户发起推流时才实例化并加载 native 库。
- 若设备架构不支持，会给出 Toast 提示且不再崩溃；真机（arm64-v8a/armeabi-v7a）不受影响。

### Demo UI（Compose）
- `LiveActivity` 通过 `ComposeView` 嵌入 `LiveActivityScreen`，`AndroidView` 仅托管 `AVLiveView` 负责采集/渲染。
- 左下角的调节按钮（Tune 图标）可展开/收起参数面板；面板包含推流地址（可选）、采集/推流分辨率、编码器选择、码率滑杆等设置。
- 浮动操作按钮按状态动态切换：无 URL 时唤起 RTMP 地址对话框，有 URL 时直接连接，推流中则用于结束直播。
- 即使未填写推流地址也支持纯预览，配合后续本地录制等场景。
- 预览左上角的实时信息卡片展示采集/推流分辨率、帧率、码率范围、GOP 与编码方式，便于快速确认当前配置。
- 水印文字尺寸根据采集分辨率动态缩放，保持在不同设备上的可读性。
