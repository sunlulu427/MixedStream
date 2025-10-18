package com.astrastream.avpush.core.utils

import android.util.Log
import com.astrastream.avpush.domain.callback.ILog

object LogHelper : ILog {

    @Volatile
    var isShowLog: Boolean = false

    override fun i(tag: String, info: String?) = log(Log.INFO, tag, info)

    override fun e(tag: String, info: String?) = log(Log.ERROR, tag, info)

    override fun w(tag: String, info: String?) = log(Log.WARN, tag, info)

    override fun d(tag: String, info: String?) = log(Log.DEBUG, tag, info)

    inline fun i(tag: String, crossinline block: () -> String) {
        if (isShowLog) log(Log.INFO, tag, block())
    }

    inline fun e(tag: String, crossinline block: () -> String) {
        if (isShowLog) log(Log.ERROR, tag, block())
    }

    inline fun w(tag: String, crossinline block: () -> String) {
        if (isShowLog) log(Log.WARN, tag, block())
    }

    inline fun d(tag: String, crossinline block: () -> String) {
        if (isShowLog) log(Log.DEBUG, tag, block())
    }

    fun e(tag: String, throwable: Throwable, message: String? = null) {
        if (!isShowLog) return
        val content = buildString {
            if (!message.isNullOrBlank()) append(message).append('\n')
            append(Log.getStackTraceString(throwable))
        }
        Log.println(Log.ERROR, tag, content)
    }

    @PublishedApi
    internal fun log(priority: Int, tag: String, message: String?) {
        if (!isShowLog) return
        Log.println(priority, tag, message.orEmpty())
    }
}
