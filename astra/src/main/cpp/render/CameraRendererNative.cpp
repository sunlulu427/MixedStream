#include "CameraRendererNative.h"

#include <android/bitmap.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2ext.h>

#include <algorithm>
#include <cstring>
#include <string>

#include "NativeLogger.h"
#include "RenderUtil.h"
#include "ShaderLibrary.h"

namespace {

constexpr size_t kQuadVertexCount = 4;
constexpr size_t kCoordsPerVertex = 2;

constexpr std::array<float, 16> kDefaultVertexData = {
        -1.f, -1.f,
        1.f, -1.f,
        -1.f, 1.f,
        1.f, 1.f,

        0.55f, -0.9f,
        0.9f, -0.9f,
        0.55f, -0.7f,
        0.9f, -0.7f
};

constexpr std::array<float, 8> kDefaultFragmentData = {
        0.f, 1.f,
        1.f, 1.f,
        0.f, 0.f,
        1.f, 0.f
};

constexpr std::array<float, 8> kDefaultWatermarkCoords = {
        0.55f, -0.9f,
        0.9f, -0.9f,
        0.55f, -0.7f,
        0.9f, -0.7f
};

constexpr float kMinHeightNdc = 0.1f;
constexpr float kMaxHeightNdc = 0.3f;
constexpr float kMaxWidthNdc = 0.6f;
constexpr float kHorizontalMargin = 0.05f;
constexpr float kVerticalMargin = 0.06f;

size_t bytesForVertices(size_t vertexCount) {
    return vertexCount * kCoordsPerVertex * sizeof(float);
}

bool HasCurrentContext() {
    return eglGetCurrentContext() != EGL_NO_CONTEXT;
}

}  // namespace

CameraRendererNative::CameraRendererNative() {
    vertexData_.assign(kDefaultVertexData.begin(), kDefaultVertexData.end());
    fragmentData_.assign(kDefaultFragmentData.begin(), kDefaultFragmentData.end());
    watermarkCoords_.assign(kDefaultWatermarkCoords.begin(), kDefaultWatermarkCoords.end());
    matrix_.fill(0.f);
    matrix_[0] = matrix_[5] = matrix_[10] = matrix_[15] = 1.f;
}

CameraRendererNative::~CameraRendererNative() {
    release();
}

CameraRendererNative::InitResult CameraRendererNative::initialize(int width, int height) {
    surfaceWidth_ = width;
    surfaceHeight_ = height;
    ensurePrograms();
    ensureBuffers();
    ensureFramebuffer();
    ensureCameraTexture();
    uploadGeometry();
    initialized_ = true;
    applyPendingDefaultWatermark();
    return {cameraTextureId_, fboTextureId_};
}

void CameraRendererNative::surfaceChanged(int width, int height) {
    surfaceWidth_ = width;
    surfaceHeight_ = height;
    ensureFramebuffer();
    applyPendingDefaultWatermark();
}

void CameraRendererNative::draw() {
    if (!initialized_ || surfaceWidth_ <= 0 || surfaceHeight_ <= 0) {
        return;
    }

    if (cameraProgram_ == 0 || screenProgram_ == 0 || vbo_ == 0 || fbo_ == 0) {
        return;
    }

    const GLsizeiptr vertexBytes = static_cast<GLsizeiptr>(vertexData_.size() * sizeof(float));
    const GLsizeiptr fragmentOffset = vertexBytes;

    glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
    glViewport(0, 0, surfaceWidth_, surfaceHeight_);
    glClearColor(0.f, 0.f, 0.f, 0.f);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(cameraProgram_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);

    glEnableVertexAttribArray(cameraPositionLocation_);
    glVertexAttribPointer(cameraPositionLocation_, kCoordsPerVertex, GL_FLOAT, GL_FALSE,
                          static_cast<GLsizei>(kCoordsPerVertex * sizeof(float)),
                          reinterpret_cast<void*>(0));

    glEnableVertexAttribArray(cameraTexCoordLocation_);
    glVertexAttribPointer(cameraTexCoordLocation_, kCoordsPerVertex, GL_FLOAT, GL_FALSE,
                          static_cast<GLsizei>(kCoordsPerVertex * sizeof(float)),
                          reinterpret_cast<void*>(fragmentOffset));

    if (cameraMatrixLocation_ >= 0) {
        glUniformMatrix4fv(cameraMatrixLocation_, 1, GL_FALSE, matrix_.data());
    }

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, cameraTextureId_);
    if (cameraSamplerLocation_ >= 0) {
        glUniform1i(cameraSamplerLocation_, 0);
    }

    glDrawArrays(GL_TRIANGLE_STRIP, 0, static_cast<GLsizei>(kQuadVertexCount));

    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    glViewport(0, 0, surfaceWidth_, surfaceHeight_);
    glClearColor(0.f, 0.f, 0.f, 0.f);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(screenProgram_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);

    glEnableVertexAttribArray(screenPositionLocation_);
    glVertexAttribPointer(screenPositionLocation_, kCoordsPerVertex, GL_FLOAT, GL_FALSE,
                          static_cast<GLsizei>(kCoordsPerVertex * sizeof(float)),
                          reinterpret_cast<void*>(0));

    glEnableVertexAttribArray(screenTexCoordLocation_);
    glVertexAttribPointer(screenTexCoordLocation_, kCoordsPerVertex, GL_FLOAT, GL_FALSE,
                          static_cast<GLsizei>(kCoordsPerVertex * sizeof(float)),
                          reinterpret_cast<void*>(fragmentOffset));

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, fboTextureId_);
    if (screenSamplerLocation_ >= 0) {
        glUniform1i(screenSamplerLocation_, 0);
    }

    glDrawArrays(GL_TRIANGLE_STRIP, 0, static_cast<GLsizei>(kQuadVertexCount));

    if (watermarkTextureId_ != 0) {
        const GLsizeiptr watermarkOffset = static_cast<GLsizeiptr>(bytesForVertices(kQuadVertexCount));
        glVertexAttribPointer(screenPositionLocation_, kCoordsPerVertex, GL_FLOAT, GL_FALSE,
                              static_cast<GLsizei>(kCoordsPerVertex * sizeof(float)),
                              reinterpret_cast<void*>(watermarkOffset));
        glBindTexture(GL_TEXTURE_2D, watermarkTextureId_);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, static_cast<GLsizei>(kQuadVertexCount));
        glVertexAttribPointer(screenPositionLocation_, kCoordsPerVertex, GL_FLOAT, GL_FALSE,
                              static_cast<GLsizei>(kCoordsPerVertex * sizeof(float)),
                              reinterpret_cast<void*>(0));
    }

    glBindTexture(GL_TEXTURE_2D, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

void CameraRendererNative::updateMatrix(const std::vector<float>& matrix) {
    if (matrix.size() < 16) {
        return;
    }
    std::copy(matrix.begin(), matrix.begin() + 16, matrix_.begin());
}

void CameraRendererNative::updateWatermark(JNIEnv* env,
                                           jobject bitmap,
                                           const std::vector<float>& coords,
                                           float scale) {
    if (bitmap == nullptr) {
        if (watermarkTextureId_ != 0) {
            glDeleteTextures(1, &watermarkTextureId_);
            watermarkTextureId_ = 0;
        }
        watermarkWidth_ = 0;
        watermarkHeight_ = 0;
        pendingDefaultWatermark_ = false;
        return;
    }

    applyWatermarkTexture(env, bitmap);

    if (!coords.empty()) {
        pendingDefaultWatermark_ = false;
        applyWatermarkCoords(coords);
        return;
    }

    pendingDefaultWatermark_ = true;
    pendingScale_ = scale > 0.f ? scale : 1.f;
    applyPendingDefaultWatermark();
}

void CameraRendererNative::release() {
    destroyPrograms();
    destroyBuffers();
    destroyTextures();
    initialized_ = false;
}

GLuint CameraRendererNative::compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    if (shader == 0) {
        return 0;
    }
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);

    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLen = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
        std::string infoLog(infoLen > 0 ? infoLen : 1, '\0');
        glGetShaderInfoLog(shader, infoLen, nullptr, infoLog.data());
        __android_log_print(ANDROID_LOG_ERROR, "CameraRendererNative",
                            "Shader compile failed: %s", infoLog.c_str());
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

GLuint CameraRendererNative::linkProgram(GLuint vertexShader, GLuint fragmentShader) {
    GLuint program = glCreateProgram();
    glAttachShader(program, vertexShader);
    glAttachShader(program, fragmentShader);
    glLinkProgram(program);

    GLint linked = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (!linked) {
        GLint infoLen = 0;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &infoLen);
        std::string infoLog(infoLen > 0 ? infoLen : 1, '\0');
        glGetProgramInfoLog(program, infoLen, nullptr, infoLog.data());
        __android_log_print(ANDROID_LOG_ERROR, "CameraRendererNative",
                            "Program link failed: %s", infoLog.c_str());
        glDeleteProgram(program);
        return 0;
    }
    return program;
}

void CameraRendererNative::ensurePrograms() {
    if (cameraProgram_ == 0) {
        const char* vertexSource = astra::GetShaderScript(2);
        const char* fragmentSource = astra::GetShaderScript(3);
        GLuint vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource);
        GLuint fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
        cameraProgram_ = linkProgram(vertexShader, fragmentShader);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        cameraPositionLocation_ = glGetAttribLocation(cameraProgram_, "v_Position");
        cameraTexCoordLocation_ = glGetAttribLocation(cameraProgram_, "f_Position");
        cameraMatrixLocation_ = glGetUniformLocation(cameraProgram_, "u_Matrix");
        cameraSamplerLocation_ = glGetUniformLocation(cameraProgram_, "sTexture");
    }

    if (screenProgram_ == 0) {
        const char* vertexSource = astra::GetShaderScript(0);
        const char* fragmentSource = astra::GetShaderScript(1);
        GLuint vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource);
        GLuint fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
        screenProgram_ = linkProgram(vertexShader, fragmentShader);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        screenPositionLocation_ = glGetAttribLocation(screenProgram_, "v_Position");
        screenTexCoordLocation_ = glGetAttribLocation(screenProgram_, "f_Position");
        screenSamplerLocation_ = glGetUniformLocation(screenProgram_, "sTexture");
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }
}

void CameraRendererNative::ensureBuffers() {
    if (vbo_ == 0) {
        glGenBuffers(1, &vbo_);
    }
}

void CameraRendererNative::ensureFramebuffer() {
    if (surfaceWidth_ <= 0 || surfaceHeight_ <= 0) {
        return;
    }
    if (fbo_ == 0) {
        glGenFramebuffers(1, &fbo_);
    }
    if (fboTextureId_ == 0) {
        glGenTextures(1, &fboTextureId_);
    }

    glBindTexture(GL_TEXTURE_2D, fboTextureId_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexImage2D(GL_TEXTURE_2D,
                 0,
                 GL_RGBA,
                 surfaceWidth_,
                 surfaceHeight_,
                 0,
                 GL_RGBA,
                 GL_UNSIGNED_BYTE,
                 nullptr);
    glBindTexture(GL_TEXTURE_2D, 0);

    glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
    glFramebufferTexture2D(GL_FRAMEBUFFER,
                           GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D,
                           fboTextureId_,
                           0);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

void CameraRendererNative::ensureCameraTexture() {
    if (cameraTextureId_ != 0) {
        return;
    }
    glGenTextures(1, &cameraTextureId_);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, cameraTextureId_);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
}

void CameraRendererNative::uploadGeometry() {
    if (vbo_ == 0) {
        return;
    }
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    const GLsizeiptr vertexBytes = static_cast<GLsizeiptr>(vertexData_.size() * sizeof(float));
    const GLsizeiptr fragmentBytes = static_cast<GLsizeiptr>(fragmentData_.size() * sizeof(float));
    glBufferData(GL_ARRAY_BUFFER, vertexBytes + fragmentBytes, nullptr, GL_STATIC_DRAW);
    glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBytes, vertexData_.data());
    glBufferSubData(GL_ARRAY_BUFFER, vertexBytes, fragmentBytes, fragmentData_.data());
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

void CameraRendererNative::destroyPrograms() {
    const bool hasContext = HasCurrentContext();
    if (cameraProgram_ != 0) {
        if (hasContext) {
            glDeleteProgram(cameraProgram_);
        }
        cameraProgram_ = 0;
        cameraPositionLocation_ = -1;
        cameraTexCoordLocation_ = -1;
        cameraSamplerLocation_ = -1;
        cameraMatrixLocation_ = -1;
    }
    if (screenProgram_ != 0) {
        if (hasContext) {
            glDeleteProgram(screenProgram_);
        }
        screenProgram_ = 0;
        screenPositionLocation_ = -1;
        screenTexCoordLocation_ = -1;
        screenSamplerLocation_ = -1;
    }
}

void CameraRendererNative::destroyBuffers() {
    const bool hasContext = HasCurrentContext();
    if (vbo_ != 0) {
        if (hasContext) {
            glDeleteBuffers(1, &vbo_);
        }
        vbo_ = 0;
    }
    if (fbo_ != 0) {
        if (hasContext) {
            glDeleteFramebuffers(1, &fbo_);
        }
        fbo_ = 0;
    }
}

void CameraRendererNative::destroyTextures() {
    const bool hasContext = HasCurrentContext();
    if (fboTextureId_ != 0) {
        if (hasContext) {
            glDeleteTextures(1, &fboTextureId_);
        }
        fboTextureId_ = 0;
    }
    if (cameraTextureId_ != 0) {
        if (hasContext) {
            glDeleteTextures(1, &cameraTextureId_);
        }
        cameraTextureId_ = 0;
    }
    if (watermarkTextureId_ != 0) {
        if (hasContext) {
            glDeleteTextures(1, &watermarkTextureId_);
        }
        watermarkTextureId_ = 0;
    }
    watermarkWidth_ = 0;
    watermarkHeight_ = 0;
    pendingDefaultWatermark_ = false;
}

bool CameraRendererNative::applyWatermarkCoords(const std::vector<float>& coords) {
    if (coords.size() < kQuadVertexCount * kCoordsPerVertex) {
        return false;
    }

    watermarkCoords_ = coords;
    std::copy(watermarkCoords_.begin(), watermarkCoords_.end(),
              vertexData_.begin() + kQuadVertexCount * kCoordsPerVertex);

    if (vbo_ == 0) {
        return false;
    }

    glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    const GLsizeiptr offset = static_cast<GLsizeiptr>(bytesForVertices(kQuadVertexCount));
    glBufferSubData(GL_ARRAY_BUFFER, offset,
                    static_cast<GLsizeiptr>(bytesForVertices(kQuadVertexCount)),
                    vertexData_.data() + kQuadVertexCount * kCoordsPerVertex);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    return true;
}

void CameraRendererNative::applyWatermarkTexture(JNIEnv* env, jobject bitmap) {
    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        astra::logLine(4, "CameraRendererNative", "Unable to get watermark bitmap info");
        return;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGB_565 &&
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        astra::logLine(3, "CameraRendererNative", "Unsupported watermark bitmap format");
        return;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        astra::logLine(4, "CameraRendererNative", "Unable to lock watermark pixels");
        return;
    }

    if (watermarkTextureId_ == 0) {
        glGenTextures(1, &watermarkTextureId_);
    }
    glBindTexture(GL_TEXTURE_2D, watermarkTextureId_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    GLenum format = GL_RGBA;
    GLenum type = GL_UNSIGNED_BYTE;
    if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        format = GL_RGB;
        type = GL_UNSIGNED_SHORT_5_6_5;
    }

    glTexImage2D(GL_TEXTURE_2D,
                 0,
                 format,
                 static_cast<GLsizei>(info.width),
                 static_cast<GLsizei>(info.height),
                 0,
                 format,
                 type,
                 pixels);
    glBindTexture(GL_TEXTURE_2D, 0);

    AndroidBitmap_unlockPixels(env, bitmap);

    watermarkWidth_ = static_cast<int>(info.width);
    watermarkHeight_ = static_cast<int>(info.height);
}

void CameraRendererNative::applyPendingDefaultWatermark() {
    if (!pendingDefaultWatermark_ || surfaceWidth_ <= 0 || surfaceHeight_ <= 0 ||
        watermarkWidth_ <= 0 || watermarkHeight_ <= 0) {
        return;
    }

    bool valid = false;
    const auto quad = astra::ComputeWatermarkQuad(surfaceWidth_,
                                                 surfaceHeight_,
                                                 watermarkWidth_,
                                                 watermarkHeight_,
                                                 pendingScale_,
                                                 kMinHeightNdc,
                                                 kMaxHeightNdc,
                                                 kMaxWidthNdc,
                                                 kHorizontalMargin,
                                                 kVerticalMargin,
                                                 valid);
    if (!valid) {
        return;
    }

    std::vector<float> coords(quad.begin(), quad.end());
    if (applyWatermarkCoords(coords)) {
        pendingDefaultWatermark_ = false;
    }
}
