package com.vladpen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

// Android 17 (API 37) blocks LAN access (cameras, ONVIF, SFTP) until this runtime permission is granted
object LocalNetwork {
    const val PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK

    fun needsPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= 37 // Android 17 API 37
                && context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED
    }
}
