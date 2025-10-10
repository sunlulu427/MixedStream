package com.astrastream.streamer.ui.live

import android.net.Uri

object StreamUrlFormatter {

    fun buildPullUrls(sourceUrl: String): List<String> {
        if (sourceUrl.isBlank()) return emptyList()
        return listOfNotNull(convertToHttpFlv(sourceUrl))
    }

    private fun convertToHttpFlv(sourceUrl: String): String? {
        val uri = runCatching { Uri.parse(sourceUrl.trim()) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val pathSegments = uri.pathSegments
        if (pathSegments.isEmpty()) return null

        val scheme = when (uri.scheme?.lowercase()) {
            "rtmps" -> "https"
            else -> "http"
        }

        val portSuffix = if (uri.port != -1) ":${uri.port}" else ""
        val basePath = buildString {
            if (uri.path?.startsWith("/") == true) append("/")
            if (pathSegments.size > 1) {
                append(pathSegments.dropLast(1).joinToString("/"))
                append('/')
            }
        }

        val streamKey = pathSegments.last().ifEmpty { return null }
        val normalizedStreamKey = if (streamKey.endsWith(".flv", true)) streamKey else "$streamKey.flv"

        val query = uri.query?.let { "?$it" } ?: ""
        val fragment = uri.fragment?.let { "#$it" } ?: ""

        return "$scheme://$host$portSuffix$basePath$normalizedStreamKey$query$fragment"
    }
}

