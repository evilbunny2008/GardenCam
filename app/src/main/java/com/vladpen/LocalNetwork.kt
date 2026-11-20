package com.vladpen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

// Android 17 (API 37) blocks LAN access (cameras, ONVIF, SFTP) until this runtime permission is granted
object LocalNetwork {
    const val PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK

    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home", ".home.arpa", ".internal", ".localdomain")

    fun needsPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= 37 // Android 17 API 37
                && context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED
    }

    // Only ask when at least one URL points at the local network
    fun needsPermission(context: Context, urls: Iterable<String?>): Boolean {
        return needsPermission(context) && urls.any { isLocalUrl(it) }
    }

    fun urlsOf(stream: StreamDataModel): List<String?> {
        return listOf(stream.url, stream.url2, stream.sftp, stream.onvifServiceUrl)
    }

    fun isLocalUrl(url: String?): Boolean {
        if (url.isNullOrBlank())
            return false
        val host = try {
            Uri.parse(url).host
        } catch (_: Exception) {
            null
        } ?: return false
        return isLocalHost(host.trim('[', ']').lowercase())
    }

    private fun isLocalHost(host: String): Boolean {
        if (host.isEmpty() || host == "localhost")
            return false

        val octets = host.split('.').map { it.toIntOrNull() }
        if (octets.size == 4 && octets.all { it != null && it in 0..255 }) {
            val (a, b) = octets.map { it!! }
            return a == 10
                    || (a == 172 && b in 16..31)
                    || (a == 192 && b == 168)
                    || (a == 169 && b == 254) // link-local
        }
        if (host.contains(':')) // IPv6: unique local fc00::/7 or link-local fe80::/10
            return host.startsWith("fc") || host.startsWith("fd")
                    || Regex("^fe[89ab]").containsMatchIn(host)

        // Bare names ("nvr") and mDNS/home domains only resolve on the LAN
        return !host.contains('.') || LOCAL_SUFFIXES.any { host.endsWith(it) }
    }
}
