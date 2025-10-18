package com.astrastream.avpush.core

const val STREAM_LOG_TAG = "astra"

enum class RtmpErrorCode(val code: Int) {
    INIT_FAILURE(-9),
    URL_SETUP_FAILURE(-10),
    CONNECT_FAILURE(-11);

    companion object {
        fun fromCode(code: Int): RtmpErrorCode? = values().firstOrNull { it.code == code }
    }
}
