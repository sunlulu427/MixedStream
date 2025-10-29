#ifndef ASTRASTREAM_ENCODERENDERERNATIVE_H
#define ASTRASTREAM_ENCODERENDERERNATIVE_H

#include <GLES2/gl2.h>
#include <jni.h>

#include <string>
#include <vector>

class EncodeRendererNative {
public:
    EncodeRendererNative(GLuint textureId,
                         std::vector<float> vertexData,
                         std::vector<float> fragmentData);
    ~EncodeRendererNative();

    void initialize(const std::string& vertexSource, const std::string& fragmentSource);
    void surfaceChanged(int width, int height);
    void draw();
    void updateWatermarkCoords(const std::vector<float>& coords);
    void updateWatermarkTexture(JNIEnv* env, jobject bitmap);
    void release();

private:
    GLuint compileShader(GLenum type, const std::string& source);
    GLuint linkProgram(GLuint vertexShader, GLuint fragmentShader);
    void ensureVbo();
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
