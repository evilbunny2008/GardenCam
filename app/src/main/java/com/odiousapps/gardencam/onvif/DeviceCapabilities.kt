package com.odiousapps.gardencam.onvif

data class DeviceCapabilities(
    val supportsPTZ: Boolean,
    val supportsMotionEvents: Boolean,
    val ptzCapabilities: PTZCapabilities?,
    val eventCapabilities: EventCapabilities?
)
