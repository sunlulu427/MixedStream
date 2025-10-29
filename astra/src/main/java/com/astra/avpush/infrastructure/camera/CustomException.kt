package com.astra.avpush.infrastructure.camera

/**
 * @Author sunlulu.tomato
 * @Date 2024/9/7
 */
class CameraNotSupportException : Exception()
class CameraHardwareException(t: Throwable) : Exception(t)
class CameraDisabledException : Exception()
class NoCameraException : Exception()
