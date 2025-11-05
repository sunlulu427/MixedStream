package com.astra.streamer.core.util

import android.content.Context
import android.content.SharedPreferences
import com.astra.streamer.AstraStreamerApplication
import java.util.concurrent.ConcurrentHashMap

/**
 * SharedPreferences utilities following Clean Architecture principles
 *
 * Provides type-safe, thread-safe access to SharedPreferences with a clean API.
 * Uses lazy initialization and caching for performance.
 */
class SPUtils private constructor(
    private val spName: String,
    private val mode: Int = Context.MODE_PRIVATE
) {
    companion object {
        private const val DEFAULT_SP_NAME = "spUtils"
        private val instanceMap = ConcurrentHashMap<String, SPUtils>()

        /**
         * Get default instance with private mode
         */
        @JvmStatic
        fun getInstance(): SPUtils = getInstance(DEFAULT_SP_NAME, Context.MODE_PRIVATE)

        /**
         * Get instance with specified mode
         */
        @JvmStatic
        fun getInstance(mode: Int): SPUtils = getInstance(DEFAULT_SP_NAME, mode)

        /**
         * Get instance with specified name
         */
        @JvmStatic
        fun getInstance(spName: String): SPUtils = getInstance(spName, Context.MODE_PRIVATE)

        /**
         * Get instance with specified name and mode
         */
        @JvmStatic
        fun getInstance(spName: String, mode: Int): SPUtils {
            val normalizedName = spName.takeIf { it.isNotBlank() } ?: DEFAULT_SP_NAME
            val key = "${normalizedName}_$mode"

            return instanceMap.getOrPut(key) {
                SPUtils(normalizedName, mode)
            }
        }
    }

    private val sp: SharedPreferences by lazy {
        AstraStreamerApplication.appContext.getSharedPreferences(spName, mode)
    }

    // Boolean operations
    fun put(key: String, value: Boolean, isCommit: Boolean = false) {
        val editor = sp.edit().putBoolean(key, value)
        if (isCommit) editor.commit() else editor.apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        sp.getBoolean(key, defaultValue)

    fun contains(key: String): Boolean = sp.contains(key)
}
