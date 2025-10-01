package com.devyk.av.rtmp.library.black

import android.os.Build
import android.text.TextUtils
import java.util.Arrays


object BlackListHelper {
    private val BLACKLISTED_AEC_MODELS = arrayOf("Nexus 5")// Nexus 5

    private val BLACKLISTED_FPS_MODELS = arrayOf("OPPO R9", "Nexus 6P")


    fun deviceInAecBlacklisted(): Boolean {
        for (blackModel in BLACKLISTED_AEC_MODELS) {
            val model = Build.MODEL
            if (!TextUtils.isEmpty(model) && model.contains(blackModel)) {
                return true
            }
        }
        return false
    }

    fun deviceInFpsBlacklisted(): Boolean {
        val blackListedModels = Arrays.asList(*BLACKLISTED_FPS_MODELS)
        for (blackModel in blackListedModels) {
            val model = Build.MODEL
            if (!TextUtils.isEmpty(model) && model.contains(blackModel)) {
                return true
            }
        }
        return false
    }
}