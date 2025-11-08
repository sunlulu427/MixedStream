#ifndef ASTRASTREAM_ENCODERENDERERNATIVE_H
#define ASTRASTREAM_ENCODERENDERERNATIVE_H

#include <GLES2/gl2.h>
#include <jni.h>

#include <vector>

class EncodeRendererNative {
public:
    explicit EncodeRendererNative(GLuint textureId);
    ~EncodeRendererNative();

    void initialize(int width, int height);
    void surfaceChanged(int width, int height);
    void draw();
    void updateWatermarkCoords(const std::vector<float>& coords);
    void updateWatermarkTexture(JNIEnv* env, jobject bitmap);
    void release();

private:
    GLuint compileShader(GLenum type, const char* source);
    GLuint linkProgram(GLuint vertexShader, GLuint fragmentShader);
    void ensureVbo();
    void ensureProgram();
    void uploadGeometry();
    void destroyProgram();
    void destroyBuffers();
    void destroyWatermarkTexture();

    GLuint program_ = 0;
    GLint positionLocation_ = -1;
    GLint texCoordLocation_ = -1;
    GLuint vbo_ = 0;
    GLuint videoTextureId_ = 0;
    GLuint watermarkTextureId_ = 0;
    std::vector<float> vertexData_;
    std::vector<float> fragmentData_;
    std::vector<float> watermarkCoords_;
    int surfaceWidth_ = 0;
    int surfaceHeight_ = 0;
    bool initialized_ = false;
};

#endif  // ASTRASTREAM_ENCODERENDERERNATIVE_H
