package com.astrastream.streamer.ui.live

import android.net.Uri

object StreamUrlFormatter {

    fun buildPullUrls(sourceUrl: String): List<String> {
        if (sourceUrl.isBlank()) return emptyList()
        val cleanedSource = sourceUrl.trim()
        return listOfNotNull(
            normalizeRtmp(cleanedSource),
            convertToHttpFlv(cleanedSource)
        ).distinct()
    }

    private fun normalizeRtmp(sourceUrl: String): String? {
        val uri = runCatching { Uri.parse(sourceUrl) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in setOf("rtmp", "rtmps")) return null
        val host = uri.host ?: return null
        val portSuffix = if (uri.port != -1) ":${uri.port}" else ""
        val rawPath = uri.encodedPath?.takeIf { it.isNotBlank() } ?: return null
        val normalizedPath = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
        val query = uri.encodedQuery?.let { "?$it" } ?: ""
        val fragment = uri.encodedFragment?.let { "#$it" } ?: ""
        return "$scheme://$host$portSuffix$normalizedPath$query$fragment"
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
