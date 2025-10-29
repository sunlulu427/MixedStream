package com.astra.streamer.core.util

import android.app.Application
import android.content.Context

/**
 * Application context utilities following Clean Architecture principles
 *
 * This singleton provides global access to the application context in a clean way.
 * Initialize once in Application.onCreate(), then access anywhere without leaking context.
 */
object Utils {
    private var application: Application? = null

    /**
     * Initialize with the application instance
     * Should be called once in Application.onCreate()
     */
    fun init(app: Application) {
        application = app
    }

    /**
     * Get application context
     * @return Application context or throws if not initialized
     */
    fun getApp(): Context =
        application ?: throw IllegalStateException("Utils not initialized. Call Utils.init() in Application.onCreate()")

    /**
     * Check if Utils has been initialized
     */
    fun isInitialized(): Boolean = application != null
}