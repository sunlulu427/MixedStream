package com.astra.avpush.runtime

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object AstraLog {
    const val LEVEL_VERBOSE = 0
    const val LEVEL_DEBUG = 1
    const val LEVEL_INFO = 2
    const val LEVEL_WARN = 3
    const val LEVEL_ERROR = 4
    private val configured = AtomicBoolean(false)

    @Volatile
    var isShowLog: Boolean = false
        set(value) {
            field = value
        }

    fun initialize(
        context: Context,
        directory: String = "logs",
        fileName: String = "astra.log",
        enable: Boolean = true
    ) {
        val targetDir = File(context.getExternalFilesDir(null), directory)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, fileName)
        NativeLogger.initialise(targetFile.absolutePath)
        configured.set(true)
        isShowLog = enable
        d("LogHelper") { "Logger initialised at ${targetFile.absolutePath}" }
    }

    fun enable(enable: Boolean) {
        isShowLog = enable
    }

    fun i(tag: String, info: String?) = log(LEVEL_INFO, tag, info)

    fun e(tag: String, info: String?) = log(LEVEL_ERROR, tag, info)

    fun w(tag: String, info: String?) = log(LEVEL_WARN, tag, info)

    fun d(tag: String, info: String?) = log(LEVEL_DEBUG, tag, info)

    inline fun i(tag: String, crossinline block: () -> String) {
        if (isShowLog) log(LEVEL_INFO, tag, block())
    }

    inline fun e(tag: String, crossinline block: () -> String) {
        if (isShowLog) log(LEVEL_ERROR, tag, block())
    }

    inline fun w(tag: String, crossinline block: () -> String) {
        if (isShowLog) log(LEVEL_WARN, tag, block())
    }

    inline fun d(tag: String, crossinline block: () -> String) {
        if (isShowLog) log(LEVEL_DEBUG, tag, block())
    }

    fun e(tag: String, throwable: Throwable, message: String? = null) {
        if (!isShowLog) return
        val content = buildString {
            if (!message.isNullOrBlank()) append(message).append('\n')
            append(throwable.stackTraceToString())
        }
        log(LEVEL_ERROR, tag, content)
    }


    fun log(priority: Int, tag: String, message: String?) {
        if (!isShowLog || !configured.get()) return
        NativeLogger.write(priority, tag, message.orEmpty())
    }
}
