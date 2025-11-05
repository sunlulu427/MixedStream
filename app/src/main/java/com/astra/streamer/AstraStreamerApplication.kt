package com.astra.streamer

import android.app.Application
import android.content.Context

class AstraStreamerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile
        private var instance: AstraStreamerApplication? = null

        val appContext: Context
            get() = instance?.applicationContext
                ?: throw IllegalStateException("AstraStreamerApplication not initialised")

        fun requireInstance(): AstraStreamerApplication =
            instance ?: throw IllegalStateException("AstraStreamerApplication not initialised")
    }
}
