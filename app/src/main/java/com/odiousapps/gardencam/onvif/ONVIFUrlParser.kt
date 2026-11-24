package com.odiousapps.gardencam.onvif

import java.net.URI

object ONVIFUrlParser {
    data class ParsedOnvifUrl(
        val serviceUrl: String,
        val credentials: ONVIFCredentials?
    )
    
    fun parseOnvifUrl(url: String): ParsedOnvifUrl? {
        return try {
            val uri = URI(url)
            // No host means the URL didn't parse as intended, e.g. a password containing an
            // unescaped '#', '/' or '@'
            if (uri.host == null) return null
            val userInfo = uri.userInfo
            val credentials = if (userInfo != null && userInfo.contains(":")) {
                val parts = userInfo.split(":", limit = 2)
                ONVIFCredentials(parts[0], parts[1])
            } else null
            
            val port = if (uri.port == -1) 80 else uri.port
            val serviceUrl = "http://${uri.host}:${port}${uri.path}"
            ParsedOnvifUrl(serviceUrl, credentials)
        } catch (e: Exception) {
            null
        }
    }
}
