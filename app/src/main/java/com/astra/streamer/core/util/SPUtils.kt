package com.astra.streamer.core.util

import android.content.Context
import android.content.SharedPreferences
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
        Utils.getApp().getSharedPreferences(spName, mode)
    }

    // String operations
    fun put(key: String, value: String, isCommit: Boolean = false) {
        val editor = sp.edit().putString(key, value)
        if (isCommit) editor.commit() else editor.apply()
    }

    fun getString(key: String, defaultValue: String = ""): String =
        sp.getString(key, defaultValue) ?: defaultValue

    // Int operations
    fun put(key: String, value: Int, isCommit: Boolean = false) {
        val editor = sp.edit().putInt(key, value)
        if (isCommit) editor.commit() else editor.apply()
    }

    fun getInt(key: String, defaultValue: Int = -1): Int =
        sp.getInt(key, defaultValue)

    // Long operations
    fun put(key: String, value: Long, isCommit: Boolean = false) {
        val editor = sp.edit().putLong(key, value)
        if (isCommit) editor.commit() else editor.apply()
    }

    fun getLong(key: String, defaultValue: Long = -1L): Long =
        sp.getLong(key, defaultValue)

    // Float operations
    fun put(key: String, value: Float, isCommit: Boolean = false) {
        val editor = sp.edit().putFloat(key, value)
        if (isCommit) editor.commit() else editor.apply()
    }

    fun getFloat(key: String, defaultValue: Float = -1f): Float =
        sp.getFloat(key, defaultValue)

    // Boolean operations
    fun put(key: String, value: Boolean, isCommit: Boolean = false) {
        val editor = sp.edit().putBoolean(key, value)
        if (isCommit) editor.commit() else editor.apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        sp.getBoolean(key, defaultValue)

    // StringSet operations
    fun put(key: String, value: Set<String>, isCommit: Boolean = false) {
        val editor = sp.edit().putStringSet(key, value)
        if (isCommit) editor.commit() else editor.apply()
    }

    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Set<String> =
        sp.getStringSet(key, defaultValue) ?: defaultValue

    // Utility operations
    fun getAll(): Map<String, *> = sp.all

    fun contains(key: String): Boolean = sp.contains(key)

    fun remove(key: String, isCommit: Boolean = false) {
        val editor = sp.edit().remove(key)
        if (isCommit) editor.commit() else editor.apply()
    }

    fun clear(isCommit: Boolean = false) {
        val editor = sp.edit().clear()
        if (isCommit) editor.commit() else editor.apply()
    }
}