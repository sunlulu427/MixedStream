package com.devyk.av.rtmp.library.utils

import android.util.Log
import com.devyk.av.rtmp.library.callback.ILog

object LogHelper : ILog {

    var isShowLog = false


    override fun i(tag: String, info: String?) {
        if (isShowLog)
            Log.i(tag, info.orEmpty())

    }

    override fun e(tag: String, info: String?) {
        if (isShowLog)
            Log.e(tag, info.orEmpty())
    }

    override fun w(tag: String, info: String?) {
        if (isShowLog)
            Log.w(tag, info.orEmpty())
    }

    override fun d(tag: String, info: String?) {
        if (isShowLog)
            Log.d(tag, info.orEmpty())
    }
}
