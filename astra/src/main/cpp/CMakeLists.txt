cmake_minimum_required(VERSION 3.22.1)

project(astra LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(SRC_ROOT ${CMAKE_CURRENT_SOURCE_DIR})
set(LIBRTMP_ROOT ${SRC_ROOT}/librtmp)
set(JNI_ROOT ${SRC_ROOT}/jni)
set(PUSH_ROOT ${SRC_ROOT}/push)
set(COMMON_ROOT ${SRC_ROOT}/common)
set(CALLBACK_ROOT ${SRC_ROOT}/callback)
set(STREAM_ROOT ${SRC_ROOT}/stream)
set(RENDER_ROOT ${SRC_ROOT}/render)
set(CODEC_ROOT ${SRC_ROOT}/codec)
set(CAPTURE_ROOT ${SRC_ROOT}/capture)

file(GLOB JNI_SOURCES CONFIGURE_DEPENDS ${JNI_ROOT}/*.cpp)
file(GLOB PUSH_SOURCES CONFIGURE_DEPENDS ${PUSH_ROOT}/*.cpp)
file(GLOB COMMON_SOURCES CONFIGURE_DEPENDS ${COMMON_ROOT}/*.cpp)
file(GLOB CALLBACK_SOURCES CONFIGURE_DEPENDS ${CALLBACK_ROOT}/*.cpp)
file(GLOB STREAM_SOURCES CONFIGURE_DEPENDS ${STREAM_ROOT}/*.cpp)
file(GLOB RENDER_SOURCES CONFIGURE_DEPENDS ${RENDER_ROOT}/*.cpp)
file(GLOB CODEC_SOURCES CONFIGURE_DEPENDS ${CODEC_ROOT}/*.cpp)
file(GLOB CAPTURE_SOURCES CONFIGURE_DEPENDS ${CAPTURE_ROOT}/*.cpp)

add_library(
        astra
        SHARED
        ${JNI_SOURCES}
        ${PUSH_SOURCES}
        ${COMMON_SOURCES}
        ${CALLBACK_SOURCES}
        ${STREAM_SOURCES}
        ${RENDER_SOURCES}
        ${CODEC_SOURCES}
        ${CAPTURE_SOURCES}
)

target_include_directories(
        astra
        PRIVATE
        ${LIBRTMP_ROOT}/include
        ${JNI_ROOT}
        ${PUSH_ROOT}
        ${COMMON_ROOT}
        ${CALLBACK_ROOT}
        ${STREAM_ROOT}
        ${RENDER_ROOT}
        ${CODEC_ROOT}
        ${CAPTURE_ROOT}
)

add_library(rtmp STATIC IMPORTED)
set_target_properties(
        rtmp
        PROPERTIES
        IMPORTED_LOCATION ${LIBRTMP_ROOT}/libs/${CMAKE_ANDROID_ARCH_ABI}/librtmp.a
)

find_library(android_log log)
find_library(GLESv2_lib GLESv2)
find_library(jnigraphics_lib jnigraphics)
find_library(mediandk_lib mediandk)
find_library(android_lib android)
find_library(camera2ndk_lib camera2ndk)
find_library(aaudio_lib aaudio)
find_library(egl_lib EGL)

target_link_libraries(
        astra
        PRIVATE
        rtmp
        ${android_log}
        ${GLESv2_lib}
        ${jnigraphics_lib}
        ${mediandk_lib}
        ${android_lib}
        ${camera2ndk_lib}
        ${aaudio_lib}
        ${egl_lib}
)
