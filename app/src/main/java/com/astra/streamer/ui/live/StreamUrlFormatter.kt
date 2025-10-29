package com.astra.streamer.ui.live

import android.net.Uri

object StreamUrlFormatter {

    fun buildPullUrls(sourceUrl: String): List<String> {
        if (sourceUrl.isBlank()) return emptyList()
        val cleanedSource = sourceUrl.trim()
        return listOfNotNull(
            normalizeRtmp(cleanedSource),
            convertToHttpQuery(cleanedSource)
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

    private fun convertToHttpQuery(sourceUrl: String): String? {
        val uri = runCatching { Uri.parse(sourceUrl.trim()) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val pathSegments = uri.pathSegments.filter { it.isNotBlank() }
        if (pathSegments.isEmpty()) return null

        val scheme = when (uri.scheme?.lowercase()) {
            "rtmps" -> "https"
            else -> "http"
        }

        val portSuffix = when (uri.port) {
            -1, 1935, 80, 443 -> ""
            else -> ":${uri.port}"
        }

        val app = pathSegments.first()
        val streamKey = pathSegments.drop(1).joinToString("/").ifBlank { app }

        val basePath = "/$app"
        val existingQuery = uri.query?.let { "&$it" } ?: ""
        val fragment = uri.fragment?.let { "#$it" } ?: ""

        return "$scheme://$host$portSuffix$basePath?app=$app&stream=$streamKey$existingQuery$fragment"
    }
}
