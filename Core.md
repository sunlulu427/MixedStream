
## 核心逻辑说明
- RTMP 初始化、建联、发包、停止、释放等链路保持不变，详见 PlantUML：

![Core Media Pipeline](docs/generated/av_dataflow.png)

- Source：`docs/av_dataflow.puml`（使用 `./tools/render_docs.sh` 本地生成 PNG/Markdown）
- 架构图采用低调配色，仅对核心处理逻辑使用 🟡 金色高亮，其他组件使用灰色变体

### 启动稳定性
- 为避免在不支持的 ABI（如 x86/x86_64 模拟器）上因加载 `libastra.so` 失败导致的启动崩溃，`RtmpSender` 改为惰性创建：仅在用户发起推流时才实例化并加载 native 库。
- 若设备架构不支持，会给出 Toast 提示且不再崩溃；真机（arm64-v8a/armeabi-v7a）不受影响。

### 相机采集默认值与降级策略
- 预览默认使用 720 × 1280 @ 30 fps，以贴合大部分竖屏手机的实际展示比例。
- `CameraView` 在渲染线程中回调 `CameraHolder`，若目标分辨率初始化失败，会自动回退到 720 × 1280 并通过 `LogHelper` 输出：
  - `open camera failed (960x1920): ...`
  - `retry camera with safe preview 720x1280`
  - `camera fallback succeeded with 720x1280`
- `LiveSessionCoordinator` 接收 `onCameraPreviewSizeSelected` 回调后，会同步 UI 状态中的采集分辨率，避免黑屏和宽高比拉伸。

### Compose Demo UI
- `LiveActivity` 通过 `ComposeView` 嵌入 `LiveScreen`，`AndroidView` 仅托管 `AVLiveView` 负责采集/渲染。
- 左下角的调节按钮（Tune 图标）可展开/收起参数面板；面板包含推流地址（可选）、采集/推流分辨率、编码器选择、码率调节等设置。
- 浮动操作按钮按状态动态切换：无 URL 时唤起 RTMP 地址对话框，有 URL 时直接连接，推流中则用于结束直播。
- 即使未填写推流地址也支持纯预览，配合后续本地录制等场景。
- 预览左上角的实时信息卡片展示采集/推流分辨率、帧率、码率范围、GOP 与编码方式，便于快速确认当前配置。
- 水印文字尺寸根据采集分辨率动态缩放，保持在不同设备上的可读性。
- `LiveActivity` 启用沉浸式全屏：状态栏、导航栏透明并支持刘海区域，预览画面铺满全面屏设备。
- 参数面板新增“拉流地址”卡片：列表展示源 RTMP 与推导出的 HTTP-FLV（`http://<host>/<app>?app=<app>&stream=<stream>`），支持一键复制。

### 编码与帧率
- H.265/HEVC 重新适配：新增长度前缀 NALU 处理与编码器 profile 配置，解决硬件编码产生的视频数据无法推流的问题。
- GL 渲染线程支持自适应帧率，推流/预览帧率稳定在配置值（默认 30fps），避免部分设备实际输出 50fps 以上导致码率飙升。

### 水印渲染
- 预览与编码输出分别由 `FboRenderer` 与 `EncodeRenderer` 在离屏 FBO 中叠加水印，将文本/图片纹理写入同一帧缓冲，从而保证预览与推流画面一致。
- 默认会根据当前渲染 surface 尺寸计算标准化坐标，水印高度维持在总高度的 10%～30%，宽度按位图纵横比自适应，并在右下角留出 ~5% 横向、~6% 纵向边距，避免拉伸或截断。
- `Watermark` 既支持传入 `Bitmap` 也可直接使用文本，新增加的 `scale` 参数可在默认尺寸基础上放大/缩小（1.0 为基准，高于 1 放大，低于 1 缩小但仍保留最小可读高度），也仍兼容自定义 `floatArray` 顶点。
- 更新水印时会重建纹理并刷新 VBO 缓存；分辨率或旋转发生变化时复用最近一次配置，动态适配不同画面尺寸仍保持清晰比例。
