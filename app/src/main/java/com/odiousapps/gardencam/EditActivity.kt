package com.odiousapps.gardencam

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.odiousapps.gardencam.Effects.edgeToEdge
import com.odiousapps.gardencam.Utils.decodeString
import com.odiousapps.gardencam.Utils.encodeString
import com.odiousapps.gardencam.Utils.parseUrl
import com.odiousapps.gardencam.Utils.replacePassword
import com.odiousapps.gardencam.onvif.*
import kotlinx.coroutines.launch
import com.odiousapps.gardencam.databinding.ActivityEditBinding
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class EditActivity : AppCompatActivity() {
    private val binding by lazy { ActivityEditBinding.inflate(layoutInflater) }
    private var streamId: Int = -1
    private val streams by lazy { StreamData.getAll() }

    // Discovery waits for the local network prompt, then runs whether or not access was granted
    private var localNetworkAsked = false
    private var localNetworkAction: (() -> Unit)? = null
    private val localNetworkLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) {
        localNetworkAction?.invoke()
        localNetworkAction = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        edgeToEdge(binding.root)
        initActivity()
    }

    private fun initActivity() {
        streamId = intent.getIntExtra("streamId", -1)
        val stream = StreamData.getById(streamId)
        if (stream == null) {
            streamId = -1
            binding.toolbar.tvLabel.text = getString(R.string.cam_add)
            binding.tvDeleteLink.visibility = View.GONE
            binding.tvCopyLink.visibility = View.GONE
            if (StreamData.copyStreamId >= 0) {
                binding.tvPasteLink.visibility = View.VISIBLE
                binding.tvPasteLink.setOnClickListener {
                    paste()
                }
            }
            binding.llChannelBox.layoutParams.height = 0
            binding.llSftpBox.layoutParams.height = 0
            binding.rbEditTcp.isChecked = true
        } else {
            binding.toolbar.tvLabel.text = stream.name

            binding.etEditName.setText(stream.name)
            binding.etEditUrl.setText(safeUrl(stream.url))
            binding.etEditChannel.setText(safeUrl(stream.url2))
            binding.etEditSftpUrl.setText(safeUrl(stream.sftp))
            binding.rbEditTcp.isChecked = stream.tcp
            binding.rbEditUdp.isChecked = !stream.tcp
            binding.cbAlert.isChecked = stream.alert == true

            // Populate ONVIF fields if this is an ONVIF device
            if (stream.isOnvifDevice && stream.onvifServiceUrl != null) {
                val credentials = stream.onvifCredentials
                val onvifUrl = if (credentials != null) {
                    "onvif://${credentials.username}:${credentials.password}@${stream.onvifServiceUrl.removePrefix("http://")}"
                } else {
                    stream.onvifServiceUrl
                }
                binding.etOnvifUrl.setText(onvifUrl)
                binding.cbInvertHorizontal.isChecked = stream.invertHorizontalPTZ
                binding.cbInvertVertical.isChecked = stream.invertVerticalPTZ
                // ASCII digits so the value parses back with toLong()
                binding.etPtzRateLimit.setText(String.format(Locale.ROOT, "%d", stream.ptzRateLimit))
            }

            val startup = SourceData.getStartup()
            binding.cbStartup.isChecked = (
                    startup != null && startup.type == "stream" && startup.id == streamId
            )

            binding.tvDeleteLink.setOnClickListener {
                delete()
            }
            binding.tvPasteLink.visibility = View.GONE
            binding.tvCopyLink.visibility = View.VISIBLE
            binding.tvCopyLink.setOnClickListener {
                copy()
            }
            if (stream.url2 == null) {
                binding.llChannelBox.layoutParams.height = 0
            } else {
                binding.tvAddChannel.visibility = View.GONE
            }
            if (stream.sftp == null) {
                binding.llSftpBox.layoutParams.height = 0
            } else {
                binding.tvSftpLabel.visibility = View.VISIBLE
                binding.llSftpBox.visibility = View.VISIBLE
                binding.tvAddSftp.visibility = View.GONE
            }
        }
        
        // Set up ONVIF URL change handler for automatic stream discovery
        // Must be after the if/else block so it works for both new and existing cameras
        setupOnvifUrlHandler()
        
        binding.tvAddChannel.setOnClickListener {
            Effects.fadeOut(arrayOf(binding.tvAddChannel))
            binding.tvDelChannel.visibility = View.VISIBLE
            binding.etEditChannel.setText(binding.etEditUrl.text.toString().trim())
            Effects.toggle(binding.llChannelBox)
        }
        binding.tvDelChannel.setOnClickListener {
            binding.tvDelChannel.visibility = View.GONE
            binding.tvAddChannel.visibility = View.VISIBLE
            binding.etEditChannel.setText("")
            Effects.toggle(binding.llChannelBox)
        }
        binding.tvAddSftp.setOnClickListener {
            showSftp()
        }
        binding.tvDiscoverOnvif.setOnClickListener {
            discoverOnvifDevices()
        }
        binding.tvTuneEncoder.setOnClickListener {
            confirmTuneEncoder()
        }
        binding.btnSave.setOnClickListener {
            save()
        }
        binding.toolbar.btnBack.setOnClickListener {
            back()
        }
        this.onBackPressedDispatcher.addCallback(callback)

        Alert.init(this, binding.toolbar.btnAlert)
    }

    private fun safeUrl(url: String?): String {
        return if (url != null) replacePassword(url, "***") else ""
    }

    private fun getEncodedUrl(newUrl: String, oldUrl: String?): String {
        val new = parseUrl(newUrl)
        val old = parseUrl(oldUrl)
        if (new != null && old != null && new.password == "***")
            return replacePassword(newUrl, encodeString(decodeString(old.password)))
        if (new != null && new.password != "")
            return replacePassword(newUrl, encodeString(decodeString(new.password)))
        return newUrl
    }

    private val callback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (streams.isNotEmpty())
                back()
            else
                finishAffinity()
        }
    }

    private fun save() {
        val oldStream = if (streamId >= 0)
            StreamData.getById(streamId)
        else
            StreamData.getById(StreamData.copyStreamId)

        var streamUrl = binding.etEditUrl.text.toString().trim()
        streamUrl = getEncodedUrl(streamUrl, oldStream?.url)

        var channelUrl = binding.etEditChannel.text.toString().trim()
        val oldChannelUrl = if (oldStream?.url2 != null) oldStream.url2
            else if (oldStream?.url != null) oldStream.url
            else streamUrl
        channelUrl = getEncodedUrl(channelUrl, oldChannelUrl)

        var sftpUrl = binding.etEditSftpUrl.text.toString().trim()
        sftpUrl = getEncodedUrl(sftpUrl, oldStream?.sftp)

        if (!validate(streamUrl, channelUrl))
            return

        // Handle ONVIF configuration
        val onvifUrl = binding.etOnvifUrl.text.toString().trim()
        val parsedOnvif = if (onvifUrl.isNotEmpty()) {
            ONVIFUrlParser.parseOnvifUrl(onvifUrl)
        } else null
        
        val isOnvifDevice = parsedOnvif != null

        var newStream = StreamDataModel(
            name = binding.etEditName.text.toString().trim(),
            url = streamUrl,
            url2 = if (channelUrl != "") channelUrl else null,
            tcp = binding.rbEditTcp.isChecked,
            sftp = if (sftpUrl != "") sftpUrl else null,
            alert = if (binding.cbAlert.isChecked && sftpUrl != "") true else null,
            isOnvifDevice = isOnvifDevice,
            onvifServiceUrl = parsedOnvif?.serviceUrl,
            invertHorizontalPTZ = binding.cbInvertHorizontal.isChecked,
            invertVerticalPTZ = binding.cbInvertVertical.isChecked,
            ptzRateLimit = binding.etPtzRateLimit.text.toString().toLongOrNull() ?: 200L,
            deviceCapabilities = if (isOnvifDevice) {
                // Default capabilities - will be updated when device is tested
                DeviceCapabilities(
                    supportsPTZ = true,
                    supportsMotionEvents = true,
                    ptzCapabilities = PTZCapabilities(supportsPan = true, supportsTilt = true, supportsZoom = true, supportsPresets = true, maxPresets = 10),
                    eventCapabilities = EventCapabilities(supportsMotionDetection = true, supportsTamperDetection = false, maxEventSubscriptions = 5)
                )
            } else null
        )
        
        // Apply credentials if provided
        if (parsedOnvif?.credentials != null) {
            newStream = newStream.withCredentials(parsedOnvif.credentials)
        }
        if (streamId < 0)
            streamId = StreamData.add(newStream)
        else
            StreamData.update(streamId, newStream)

        SourceData.setStartup(binding.cbStartup.isChecked, "stream", streamId)

        Alert.checkAvailability()
        back()
    }

    private fun validate(url: String, channelUrl: String): Boolean {
        val name = binding.etEditName.text.toString().trim()
        var ok = true

        if (name.isEmpty() || name.length > 255) {
            binding.etEditName.error = getString(R.string.err_invalid)
            ok = false
        }
        if (url.isEmpty() || url.length > 255) {
            binding.etEditUrl.error = getString(R.string.err_invalid)
            ok = false
        }
        for (i in streams.indices) {
            if (i == streamId)
                break

            if (streams[i].name == name) {
                binding.etEditName.error = getString(R.string.err_cam_exists)
                ok = false
            }
            if (streams[i].url == url) {
                binding.etEditUrl.error = getString(R.string.err_cam_exists)
                ok = false
            }
        }
        if (channelUrl != "" && channelUrl == url) {
            binding.etEditChannel.error = getString(R.string.err_channels_equal)
            ok = false
        }
        return ok
    }

    private fun delete() {
        AlertDialog.Builder(this)
            .setMessage(R.string.cam_delete)
            .setPositiveButton(R.string.delete) { _, _ ->
                StreamData.delete(streamId)
                Alert.checkAvailability()
                back()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create().show()
    }

    private fun copy() {
        StreamData.copyStreamId = streamId
        Effects.fadeOut(arrayOf(binding.tvCopyLink))
    }

    private fun paste() {
        Effects.fadeOut(arrayOf(binding.tvPasteLink))
        val stream = StreamData.getById(StreamData.copyStreamId) ?: return

        binding.etEditName.setText(stream.name, TextView.BufferType.EDITABLE)
        binding.etEditUrl.setText(safeUrl(stream.url), TextView.BufferType.EDITABLE)
        binding.etEditSftpUrl.setText(safeUrl(stream.sftp), TextView.BufferType.EDITABLE)
        binding.rbEditTcp.isChecked = stream.tcp
        binding.rbEditUdp.isChecked = !stream.tcp
        binding.cbAlert.isChecked = stream.alert == true
        if (stream.sftp !=null)
            showSftp()
    }

    private fun showSftp() {
        binding.tvSftpLabel.visibility = View.VISIBLE
        Effects.fadeOut(arrayOf(binding.tvAddSftp))
        Effects.toggle(binding.llSftpBox)
    }

    private fun back() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    // Runs action now, or after the local network prompt if a LAN address is involved
    private fun withLocalNetwork(needed: Boolean, action: () -> Unit) {
        if (!needed || localNetworkAsked) {
            action()
            return
        }
        localNetworkAsked = true
        localNetworkAction = action
        localNetworkLauncher.launch(LocalNetwork.PERMISSION)
    }

    private fun discoverOnvifDevices() {
        // Scanning always targets the LAN
        withLocalNetwork(LocalNetwork.needsPermission(this)) { scanOnvifDevices() }
    }

    private fun scanOnvifDevices() {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Discovering ONVIF Cameras")
            .setMessage("Scanning network for ONVIF devices...")
            .setCancelable(false)
            .create()
        
        progressDialog.show()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val devices = withContext(Dispatchers.IO) {
                    ONVIFManager.getInstance().discoverDevices()
                }
                
                progressDialog.dismiss()
                
                if (devices.isEmpty()) {
                    AlertDialog.Builder(this@EditActivity)
                        .setTitle("No Devices Found")
                        .setMessage("No ONVIF cameras were discovered on the network.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    showDeviceSelectionDialog(devices)
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                AlertDialog.Builder(this@EditActivity)
                    .setTitle("Discovery Error")
                    .setMessage("Error discovering ONVIF devices: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun confirmTuneEncoder() {
        val parsed = ONVIFUrlParser.parseOnvifUrl(binding.etOnvifUrl.text.toString().trim())
        if (parsed == null) {
            AlertDialog.Builder(this)
                .setTitle("Low Latency")
                .setMessage("Enter the camera's ONVIF URL first.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Low Latency")
            .setMessage("This changes the camera's own video encoder settings for every stream: " +
                    "H.264 Baseline profile (no B-frames) and constant bitrate, where the camera supports them. " +
                    "Streams may briefly reconnect.")
            .setPositiveButton("Apply") { _, _ ->
                withLocalNetwork(LocalNetwork.needsPermission(this, listOf(parsed.serviceUrl))) {
                    tuneEncoder(parsed)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun tuneEncoder(parsed: ONVIFUrlParser.ParsedOnvifUrl) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Low Latency")
            .setMessage("Updating camera encoder settings...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch {
            val report = try {
                withContext(Dispatchers.IO) {
                    ONVIFEncoderTuner(parsed.serviceUrl, parsed.credentials).applyLowLatency()
                }
            } catch (e: Exception) {
                listOf("Error: ${e.message}")
            }
            progressDialog.dismiss()
            AlertDialog.Builder(this@EditActivity)
                .setTitle("Low Latency")
                .setMessage(report.joinToString("\n\n"))
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun showDeviceSelectionDialog(devices: List<ONVIFDevice>) {
        val deviceNames = devices.map { "${it.name} (${it.ipAddress})" }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Select ONVIF Camera")
            .setItems(deviceNames) { _, which ->
                val selectedDevice = devices[which]
                populateFromOnvifDevice(selectedDevice)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun populateFromOnvifDevice(device: ONVIFDevice) {
        // Auto-populate camera settings from ONVIF device
        binding.etEditName.setText(device.name)
        
        // Generate RTSP URL (simplified - may need device-specific logic)
        val rtspUrl = "rtsp://${device.ipAddress}/stream1"
        binding.etEditUrl.setText(rtspUrl)
        
        // Show credentials dialog if needed
        if (device.credentials == null) {
            showCredentialsDialog(device)
        }
    }

    private fun showCredentialsDialog(device: ONVIFDevice) {
        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_2, null)
        // Simplified credentials dialog - would need proper layout
        AlertDialog.Builder(this)
            .setTitle("ONVIF Credentials")
            .setMessage("Enter credentials for ${device.name}")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                // Handle credentials input
            }
            .setNegativeButton("Skip", null)
            .show()
    }
    
    private fun setupOnvifUrlHandler() {
        Log.e("ONVIF_HANDLER", "=== Setting up ONVIF URL handler ===")
        
        // Trigger on focus loss
        binding.etOnvifUrl.setOnFocusChangeListener { _, hasFocus ->
            Log.e("ONVIF_HANDLER", "=== Focus changed: hasFocus=$hasFocus ===")
            if (!hasFocus) {
                val onvifUrl = binding.etOnvifUrl.text.toString().trim()
                val rtspUrl = binding.etEditUrl.text.toString().trim()
                Log.e("ONVIF_HANDLER", "=== Focus lost - ONVIF URL: '$onvifUrl', RTSP URL: '$rtspUrl' ===")
                
                if (onvifUrl.startsWith("onvif://")) {
                    Log.e("ONVIF_HANDLER", "=== ONVIF URL detected ===")
                    if (rtspUrl.isEmpty()) {
                        Log.e("ONVIF_HANDLER", "=== RTSP URL is empty, triggering discovery ===")
                        discoverStreamsFromOnvif(onvifUrl)
                    } else {
                        Log.e("ONVIF_HANDLER", "=== RTSP URL already populated, skipping discovery ===")
                    }
                } else {
                    Log.e("ONVIF_HANDLER", "=== Not an ONVIF URL, skipping ===")
                }
            }
        }
        
        // Also trigger when user finishes typing (after 1 second delay)
        var discoveryJob: Job? = null
        binding.etOnvifUrl.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                discoveryJob?.cancel()
                val onvifUrl = s.toString().trim()
                val rtspUrl = binding.etEditUrl.text.toString().trim()
                
                Log.e("ONVIF_HANDLER", "=== Text changed - ONVIF URL: '$onvifUrl', RTSP URL: '$rtspUrl' ===")
                
                if (onvifUrl.startsWith("onvif://")) {
                    if (rtspUrl.isEmpty()) {
                        Log.e("ONVIF_HANDLER", "=== Starting 1-second delay timer for discovery ===")
                        // Wait 1 second after user stops typing before triggering discovery
                        discoveryJob = lifecycleScope.launch {
                            delay(1000.milliseconds)
                            Log.e("ONVIF_HANDLER", "=== Timer expired, triggering discovery ===")
                            discoverStreamsFromOnvif(onvifUrl)
                        }
                    } else {
                        Log.e("ONVIF_HANDLER", "=== RTSP URL already populated, not starting timer ===")
                    }
                } else {
                    Log.e("ONVIF_HANDLER", "=== Not an ONVIF URL yet ===")
                }
            }
        })
    }
    
    private fun discoverStreamsFromOnvif(onvifUrl: String) {
        withLocalNetwork(LocalNetwork.needsPermission(this, listOf(onvifUrl))) {
            fetchStreamsFromOnvif(onvifUrl)
        }
    }

    private fun fetchStreamsFromOnvif(onvifUrl: String) {
        Log.d("ONVIF", "EditActivity: Starting discovery for URL: $onvifUrl")
        lifecycleScope.launch {
            try {
                // Show progress
                binding.etEditUrl.hint = "Discovering streams..."
                binding.etEditUrl.isEnabled = false
                
                // Parse ONVIF URL
                val parsed = ONVIFUrlParser.parseOnvifUrl(onvifUrl)
                    ?: throw Exception("Invalid ONVIF URL format")
                
                Log.d("ONVIF", "EditActivity: Parsed URL - Service: ${parsed.serviceUrl}, User: ${parsed.credentials?.username}")
                
                // Discover streams
                val discovery = ONVIFStreamDiscovery()
                val result = discovery.discoverStreams(parsed.serviceUrl, parsed.credentials)
                
                Log.d("ONVIF", "EditActivity: Discovery successful - Primary: ${result.primaryRtspUrl}")
                
                // Auto-populate RTSP URLs
                binding.etEditUrl.setText(result.primaryRtspUrl)
                if (result.secondaryRtspUrl != null) {
                    binding.etEditChannel.setText(result.secondaryRtspUrl)
                }
                
                // Show success message
                binding.etEditUrl.hint = "Stream discovered (${result.profiles.size} profiles found)"
                
            } catch (e: Exception) {
                Log.e("ONVIF", "EditActivity: Discovery failed", e)
                // Show error and allow manual entry
                binding.etEditUrl.hint = "Discovery failed: ${e.message}. Enter RTSP URL manually."
            } finally {
                binding.etEditUrl.isEnabled = true
            }
        }
    }
}
