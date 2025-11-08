#ifndef ASTRASTREAM_CAMERA_RENDERER_NATIVE_H
#define ASTRASTREAM_CAMERA_RENDERER_NATIVE_H

#include <GLES2/gl2.h>
#include <jni.h>

#include <array>
#include <vector>

class CameraRendererNative {
public:
    struct InitResult {
        GLuint cameraTextureId = 0;
        GLuint outputTextureId = 0;
    };

    CameraRendererNative();
    ~CameraRendererNative();

    InitResult initialize(int width, int height);
    void surfaceChanged(int width, int height);
    void draw();
    void updateMatrix(const std::vector<float>& matrix);
    void updateWatermark(JNIEnv* env, jobject bitmap, const std::vector<float>& coords, float scale);
    void release();

    GLuint cameraTextureId() const { return cameraTextureId_; }
    GLuint outputTextureId() const { return fboTextureId_; }

private:
    GLuint compileShader(GLenum type, const char* source);
    GLuint linkProgram(GLuint vertexShader, GLuint fragmentShader);
    void ensurePrograms();
    void ensureBuffers();
    void ensureFramebuffer();
    void ensureCameraTexture();
    void uploadGeometry();
    void destroyPrograms();
    void destroyBuffers();
    void destroyTextures();
    bool applyWatermarkCoords(const std::vector<float>& coords);
    void applyWatermarkTexture(JNIEnv* env, jobject bitmap);
    void applyPendingDefaultWatermark();

    GLuint cameraProgram_ = 0;
    GLuint screenProgram_ = 0;
    GLint cameraPositionLocation_ = -1;
    GLint cameraTexCoordLocation_ = -1;
    GLint cameraSamplerLocation_ = -1;
    GLint cameraMatrixLocation_ = -1;
    GLint screenPositionLocation_ = -1;
    GLint screenTexCoordLocation_ = -1;
    GLint screenSamplerLocation_ = -1;
    GLuint vbo_ = 0;
    GLuint fbo_ = 0;
    GLuint fboTextureId_ = 0;
    GLuint cameraTextureId_ = 0;
    GLuint watermarkTextureId_ = 0;
    std::vector<float> vertexData_;
    std::vector<float> fragmentData_;
    std::vector<float> watermarkCoords_;
    std::array<float, 16> matrix_{};
    int surfaceWidth_ = 0;
    int surfaceHeight_ = 0;
    int watermarkWidth_ = 0;
    int watermarkHeight_ = 0;
    bool pendingDefaultWatermark_ = false;
    float pendingScale_ = 1.f;
    bool initialized_ = false;
};

#endif  // ASTRASTREAM_CAMERA_RENDERER_NATIVE_H
