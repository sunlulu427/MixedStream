#include "ShaderLibrary.h"

namespace astra {

namespace {

constexpr const char* kBasicVertex = R"(
attribute vec4 v_Position;
attribute vec2 f_Position;
varying vec2 ft_Position;
void main() {
    ft_Position = f_Position;
    gl_Position = v_Position;
}
)";

constexpr const char* kBasicFragment = R"(
precision mediump float;
varying vec2 ft_Position;
uniform sampler2D sTexture;
void main() {
    gl_FragColor=texture2D(sTexture, ft_Position);
}
)";

constexpr const char* kCameraVertex = R"(
attribute vec4 v_Position;
attribute vec2 f_Position;
varying vec2 ft_Position;
uniform mat4 u_Matrix;
void main() {
    ft_Position = f_Position;
    gl_Position = v_Position  * u_Matrix;
}
)";

constexpr const char* kCameraFragment = R"(
#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 ft_Position;
uniform samplerExternalOES sTexture;
void main() {
    gl_FragColor=texture2D(sTexture, ft_Position);
}
)";

}  // namespace

const char* GetShaderScript(int id) {
    switch (id) {
        case 0:
            return kBasicVertex;
        case 1:
            return kBasicFragment;
        case 2:
            return kCameraVertex;
        case 3:
            return kCameraFragment;
        default:
            return "";
    }
}

}  // namespace astra
