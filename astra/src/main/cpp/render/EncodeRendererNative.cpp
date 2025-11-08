#include "EncodeRendererNative.h"

#include <android/bitmap.h>
#include <android/log.h>
#include <GLES2/gl2ext.h>

#include <array>
#include <cstring>
#include <string>

#include "NativeLogger.h"
#include "RenderUtil.h"
#include "ShaderLibrary.h"

namespace {

#define GL_CHECK_ERROR(label)                                                                    \
    do {                                                                                         \
        GLenum error = glGetError();                                                             \
        if (error != GL_NO_ERROR) {                                                              \
            __android_log_print(ANDROID_LOG_ERROR, "EncodeRendererNative",                       \
                                "%s failed with error 0x%x", label, error);                   \
        }                                                                                        \
    } while (false)

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

constexpr float kMinHeightNdc = 0.1f;
constexpr float kMaxHeightNdc = 0.3f;
constexpr float kMaxWidthNdc = 0.6f;
constexpr float kHorizontalMargin = 0.05f;
constexpr float kVerticalMargin = 0.06f;

size_t bytesForVertices(size_t vertexCount) {
    return vertexCount * kCoordsPerVertex * sizeof(float);
}

}  // namespace

EncodeRendererNative::EncodeRendererNative(GLuint textureId)
    : videoTextureId_(textureId) {
    vertexData_.assign(kDefaultVertexData.begin(), kDefaultVertexData.end());
    fragmentData_.assign(kDefaultFragmentData.begin(), kDefaultFragmentData.end());
    watermarkCoords_.assign(kDefaultVertexData.begin() + kQuadVertexCount * kCoordsPerVertex,
                            kDefaultVertexData.end());
}

EncodeRendererNative::~EncodeRendererNative() {
    release();
}

void EncodeRendererNative::initialize(int width, int height) {
    surfaceWidth_ = width;
    surfaceHeight_ = height;
    destroyProgram();
    destroyBuffers();
    destroyWatermarkTexture();

    ensureProgram();
    ensureVbo();
    uploadGeometry();
    initialized_ = true;
    applyPendingDefaultWatermark();
}

void EncodeRendererNative::surfaceChanged(int width, int height) {
    surfaceWidth_ = width;
    surfaceHeight_ = height;
    glViewport(0, 0, width, height);
    applyPendingDefaultWatermark();
}

void EncodeRendererNative::draw() {
    if (!initialized_) {
        return;
    }

    glClear(GL_COLOR_BUFFER_BIT);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

    glUseProgram(program_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);

    glEnableVertexAttribArray(positionLocation_);
    glVertexAttribPointer(positionLocation_, kCoordsPerVertex, GL_FLOAT, GL_FALSE,
                          static_cast<GLsizei>(kCoordsPerVertex * sizeof(float)),
                          reinterpret_cast<void*>(0));

    glEnableVertexAttribArray(texCoordLocation_);
    glVertexAttribPointer(texCoordLocation_, kCoordsPerVertex, GL_FLOAT, GL_FALSE,
                          static_cast<GLsizei>(kCoordsPerVertex * sizeof(float)),
                          reinterpret_cast<void*>(vertexData_.size() * sizeof(float)));

    glBindTexture(GL_TEXTURE_2D, videoTextureId_);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, static_cast<GLsizei>(kQuadVertexCount));

    if (watermarkTextureId_ != 0) {
        glBindTexture(GL_TEXTURE_2D, watermarkTextureId_);
        const size_t watermarkOffsetBytes = bytesForVertices(kQuadVertexCount);
        glVertexAttribPointer(positionLocation_, kCoordsPerVertex, GL_FLOAT, GL_FALSE,
                              static_cast<GLsizei>(kCoordsPerVertex * sizeof(float)),
                              reinterpret_cast<void*>(watermarkOffsetBytes));
        glDrawArrays(GL_TRIANGLE_STRIP, 0, static_cast<GLsizei>(kQuadVertexCount));

        glBindTexture(GL_TEXTURE_2D, 0);
        glVertexAttribPointer(positionLocation_, kCoordsPerVertex, GL_FLOAT, GL_FALSE,
                              static_cast<GLsizei>(kCoordsPerVertex * sizeof(float)),
                              reinterpret_cast<void*>(0));
    }

    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

void EncodeRendererNative::updateWatermark(JNIEnv* env,
                                           jobject bitmap,
                                           const std::vector<float>& coords,
                                           float scale) {
    if (bitmap == nullptr) {
        destroyWatermarkTexture();
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

void EncodeRendererNative::release() {
    destroyProgram();
    destroyBuffers();
    destroyWatermarkTexture();
    initialized_ = false;
}

GLuint EncodeRendererNative::compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);

    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLen = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
        std::string infoLog(infoLen, '\0');
        glGetShaderInfoLog(shader, infoLen, nullptr, infoLog.data());
        __android_log_print(ANDROID_LOG_ERROR, "EncodeRendererNative",
                            "Shader compile failed: %s", infoLog.c_str());
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

void EncodeRendererNative::ensureProgram() {
    if (program_ != 0) {
        return;
    }
    const char* vertexSource = astra::GetShaderScript(0);
    const char* fragmentSource = astra::GetShaderScript(1);
    GLuint vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource);
    GLuint fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
    program_ = linkProgram(vertexShader, fragmentShader);
    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);

    if (program_ == 0) {
        astra::logLine(4, "EncodeRendererNative", "Failed to create shader program");
        return;
    }

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    positionLocation_ = glGetAttribLocation(program_, "v_Position");
    texCoordLocation_ = glGetAttribLocation(program_, "f_Position");
}

GLuint EncodeRendererNative::linkProgram(GLuint vertexShader, GLuint fragmentShader) {
    GLuint program = glCreateProgram();
    glAttachShader(program, vertexShader);
    glAttachShader(program, fragmentShader);
    glLinkProgram(program);

    GLint linked = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (!linked) {
        GLint infoLen = 0;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &infoLen);
        std::string infoLog(infoLen, '\0');
        glGetProgramInfoLog(program, infoLen, nullptr, infoLog.data());
        __android_log_print(ANDROID_LOG_ERROR, "EncodeRendererNative",
                            "Program link failed: %s", infoLog.c_str());
        glDeleteProgram(program);
        return 0;
    }
    return program;
}

void EncodeRendererNative::ensureVbo() {
    if (vbo_ == 0) {
        glGenBuffers(1, &vbo_);
    }
}

void EncodeRendererNative::uploadGeometry() {
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

void EncodeRendererNative::destroyProgram() {
    if (program_ != 0) {
        glDeleteProgram(program_);
        program_ = 0;
        positionLocation_ = -1;
        texCoordLocation_ = -1;
        astra::logLine(1, "EncodeRendererNative", "Shader program destroyed");
    }
}

void EncodeRendererNative::destroyBuffers() {
    if (vbo_ != 0) {
        glDeleteBuffers(1, &vbo_);
        vbo_ = 0;
    }
}

void EncodeRendererNative::destroyWatermarkTexture() {
    if (watermarkTextureId_ != 0) {
        glDeleteTextures(1, &watermarkTextureId_);
        watermarkTextureId_ = 0;
    }
    watermarkWidth_ = 0;
    watermarkHeight_ = 0;
    pendingDefaultWatermark_ = false;
}

bool EncodeRendererNative::applyWatermarkCoords(const std::vector<float>& coords) {
    if (coords.size() < kQuadVertexCount * kCoordsPerVertex) {
        astra::logLine(3, "EncodeRendererNative", "Watermark coordinates insufficient");
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

void EncodeRendererNative::applyPendingDefaultWatermark() {
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

void EncodeRendererNative::applyWatermarkTexture(JNIEnv* env, jobject bitmap) {
    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        astra::logLine(4, "EncodeRendererNative", "Failed to get watermark bitmap info");
        return;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGB_565 &&
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        astra::logLine(3, "EncodeRendererNative", "Unsupported bitmap format");
        return;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        astra::logLine(4, "EncodeRendererNative", "Unable to lock watermark bitmap pixels");
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
    glTexImage2D(GL_TEXTURE_2D, 0, format, static_cast<GLsizei>(info.width),
                 static_cast<GLsizei>(info.height), 0, format, type, pixels);
    glBindTexture(GL_TEXTURE_2D, 0);

    AndroidBitmap_unlockPixels(env, bitmap);

    watermarkWidth_ = static_cast<int>(info.width);
    watermarkHeight_ = static_cast<int>(info.height);
}
