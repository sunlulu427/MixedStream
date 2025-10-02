# Architecture Color Scheme

The PlantUML diagrams in this project use a subtle color scheme that highlights only the most critical components, following a minimalist design approach for improved focus and readability.

## Color Legend

| Color | Hex Code | Layer | Purpose | Example Components |
|-------|----------|-------|---------|-------------------|
| 🟡 **Gold** | `#D4AF37` | **Core Processing** | Critical data processing and transformation logic | `CameraRenderer`, `VideoEncoder`, `FLV Packer`, `RtmpPacker` |
| ⚫ **Dark Gray** | `#4a4a4a` | **Control Layer** | Business logic coordination and orchestration | `StreamController`, `AVLiveView`, `VideoController` |
| ⬛ **Medium Gray** | `#3a3a3a` | **UI Layer** | User interface and presentation components | `LiveActivity`, `CameraView`, `GLSurfaceView` |
| ⬛ **Base Gray** | `#2b2b2b` | **Infrastructure Layer** | Hardware interfaces and system resources | `CameraHolder`, `MediaCodec`, `RtmpSender`, `SurfaceTexture` |
| ⬛ **Light Gray** | `#333333` | **External Systems** | Third-party services and system APIs | `CameraService`, `RTMP Server`, `librtmp` |

## Design Principles

### 1. **Selective Highlighting**
- Only core processing components use bright color (Gold) to draw attention
- All other components use subtle gray variations to maintain focus
- Background remains consistent dark theme for readability
- Avoids visual noise while emphasizing critical logic paths

### 2. **Minimalist Approach**
- Most components blend into the background with muted colors
- High-contrast highlighting reserved for essential components only
- Clean, professional appearance suitable for technical documentation
- Reduces cognitive load when analyzing complex system interactions

### 3. **Functional Clarity**
- Gold highlighting immediately identifies where core data transformation occurs
- Gray variations provide subtle layering without distraction
- Consistent application across all diagram types
- Clear distinction between highlighted and non-highlighted elements

## Usage in Diagrams

### Sequence Diagrams
```plantuml
participant LiveActivity <<ui>>
participant StreamController <<control>>
participant VideoEncoder <<processing>>
participant MediaCodec <<infrastructure>>
participant "RTMP Server" as Server <<external>>
```

### Architecture Diagrams
```plantuml
rectangle "Preview Rendering Pipeline" as PreviewRender <<ui>> {
  [Preview Surface\n(GLSurfaceView)] --> [Display Buffer]
}

rectangle "Stream Processing & Transmission" as StreamTx <<processing>> {
  [FLV Muxer\n(Packer)] --> [StreamController\n(A/V Sync)]
}
```

## Benefits

1. **Quick Understanding**: Developers can immediately identify which layer a component belongs to
2. **Architecture Validation**: Color inconsistencies can reveal architectural violations
3. **Documentation Clarity**: Technical discussions can reference layers by color
4. **Maintenance**: Easier to spot when components are placed in wrong architectural layers

## Regenerating Diagrams

To update all diagrams with the color scheme:

```bash
./tools/render_docs.sh
```

This generates PNG files in `docs/generated/` with the full color-coded architecture visualization.