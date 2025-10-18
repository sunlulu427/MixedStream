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

file(GLOB JNI_SOURCES CONFIGURE_DEPENDS ${JNI_ROOT}/*.cpp)
file(GLOB PUSH_SOURCES CONFIGURE_DEPENDS ${PUSH_ROOT}/*.cpp)
file(GLOB COMMON_SOURCES CONFIGURE_DEPENDS ${COMMON_ROOT}/*.cpp)
file(GLOB CALLBACK_SOURCES CONFIGURE_DEPENDS ${CALLBACK_ROOT}/*.cpp)
file(GLOB STREAM_SOURCES CONFIGURE_DEPENDS ${STREAM_ROOT}/*.cpp)

add_library(
        astra
        SHARED
        ${JNI_SOURCES}
        ${PUSH_SOURCES}
        ${COMMON_SOURCES}
        ${CALLBACK_SOURCES}
        ${STREAM_SOURCES}
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
)

add_library(rtmp STATIC IMPORTED)
set_target_properties(
        rtmp
        PROPERTIES
        IMPORTED_LOCATION ${LIBRTMP_ROOT}/libs/${CMAKE_ANDROID_ARCH_ABI}/librtmp.a
)

find_library(android_log log)

target_link_libraries(
        astra
        PRIVATE
        rtmp
        ${android_log}
)
