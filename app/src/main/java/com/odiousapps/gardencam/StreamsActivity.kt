package com.odiousapps.gardencam

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.odiousapps.gardencam.Effects.edgeToEdge
import com.odiousapps.gardencam.databinding.ActivityStreamsBinding
import com.odiousapps.gardencam.onvif.*
import kotlinx.coroutines.*


class StreamsActivity : AppCompatActivity(), Layout {
    private val binding by lazy { ActivityStreamsBinding.inflate(layoutInflater) }
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    override lateinit var rootView: View
    override lateinit var layoutListener: ViewTreeObserver.OnGlobalLayoutListener
    override var insets: Insets? = null
    override var hideBars = false

    private var sourceId = -1
    private var isGroup: Boolean = false
    private var streams = listOf<Int>()

    private var fragments = arrayListOf<StreamFragment>()
    private val loadings = mutableMapOf<Int, Boolean>()

    private lateinit var gestureDetector: VideoGestureDetector
    private var gestureInProgress = 0
    private val watchdogInterval: Long = 10000 // milliseconds

    // ONVIF properties
    private var ptzControlView: PTZControlView? = null
    private var motionIndicator: MotionIndicator? = null
    private var onvifManager: ONVIFManager? = null
    private val onvifScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Backchannel audio
    private var backchannelManager: BackchannelManager? = null
    private var micActive = false
    private lateinit var micPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var localNetworkLauncher: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register permission launcher
        micPermissionLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startMic()
            } else {
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root,
                    R.string.mic_permission_denied,
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                ).show()
            }
        }
        
        // Streams started before local network access was granted have already failed, so restart them
        localNetworkLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted)
                recreate()
        }
        // Lock to landscape orientation for better video viewing
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        setContentView(binding.root)
        
        // Enable fullscreen immersive mode (bars reappear transiently on swipe)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        
        // Hide toolbar for true fullscreen experience
        binding.toolbar.root.visibility = View.GONE
        edgeToEdge(binding.root) { innerPadding ->
            insets = innerPadding
        }
        sourceId = intent.getIntExtra("id", -1)
        val sourceType = intent.getStringExtra("type") ?: return
        isGroup = sourceType == "group"

        try { // prevents exception if settings file is corrupted
            initActivity(sourceType)
            val urls = streams.mapNotNull { StreamData.getById(it) }.flatMap { LocalNetwork.urlsOf(it) }
            if (LocalNetwork.needsPermission(this, urls))
                localNetworkLauncher.launch(LocalNetwork.PERMISSION)
            if (savedInstanceState == null) {
                initFragments()
            }
            initLayout(binding.root)
            initMute()
            initMic()
        } catch (_: Exception) {
            Log.e("StreamActivity", "Data is corrupted ($sourceType $sourceId), redirect")
            startActivity(
                if (isGroup) {
                    Intent(this, GroupEditActivity::class.java).putExtra("groupId", sourceId)
                } else {
                    Intent(this, EditActivity::class.java).putExtra("streamId", sourceId)
                }
            )
        }
    }

    private fun initActivity(sourceType: String) {
        this.onBackPressedDispatcher.addCallback(callback)
        binding.toolbar.btnBack.setOnClickListener {
            back()
        }
        when (sourceType) {
            "stream" -> {
                val stream = StreamData.getById(sourceId) ?: return
                binding.toolbar.tvLabel.text = stream.name
                streams = listOf(sourceId)
                // Hide toolbar for single stream fullscreen experience
                binding.toolbar.root.visibility = View.GONE
            }
            "group" -> {
                val group = GroupData.getById(sourceId) ?: return
                binding.toolbar.tvLabel.text = group.name
                streams = group.streams
                GroupData.backGroupId = -1  // drop
            }
            else -> throw Exception("invalid source type")
        }
        gestureDetector = VideoGestureDetector(binding.rlStreamsBox)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        Alert.init(this, binding.toolbar.btnAlert)
        
        // Initialize ONVIF features
        initOnvifFeatures()
    }

    private fun initFragments() {
        var isChannelsAvailable = false
        for (id in streams) {
            if (id > StreamData.getAll().count())
                throw Exception("invalid group ID")

            val stream = StreamData.getById(id) ?: continue

            val frame = FrameLayout(this)
            frame.id = FrameLayout.generateViewId()
            
            // Center the frame in the RelativeLayout
            val layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT)
            frame.layoutParams = layoutParams
            
            binding.rlStreamsBox.addView(frame)

            val fragment = StreamFragment.newInstance(id)
            supportFragmentManager.beginTransaction().add(frame.id, fragment).commit()

            if (isGroup) {
                frame.setOnClickListener {
                    if (gestureInProgress <= 0) {
                        GroupData.backGroupId = sourceId  // save for back navigation
                        reload("stream", id)
                    }
                }
                frame.setOnLongClickListener {
                    initBars()
                    true
                }
            }
            fragments.add(fragment)

            isChannelsAvailable = isChannelsAvailable || stream.url2 != null

            loadings[id] = true
        }
        initChannel(isChannelsAvailable)
    }

    // The channel button is always shown. Without a second stream URL (Channel 2 in the camera
    // editor) it is greyed out and explains how to add one.
    private fun initChannel(available: Boolean) {
        binding.btnChannel.visibility = View.VISIBLE
        if (!available) {
            binding.btnChannel.setImageResource(Utils.getChannelButton(0))
            binding.btnChannel.alpha = 0.4f
            binding.btnChannel.setOnClickListener {
                android.widget.Toast.makeText(this, R.string.no_channel_2,
                    android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        var channel = StreamData.getChannel(isGroup)
        binding.btnChannel.alpha = 1f
        binding.btnChannel.setImageResource(Utils.getChannelButton(channel))
        binding.btnChannel.setOnClickListener {
            channel = if (channel != 1) 1 else 0
            StreamData.setChannel(channel, isGroup)
            binding.btnChannel.setImageResource(Utils.getChannelButton(channel))
            startAll(false)
        }
    }

    private fun initMute() {
        if (isGroup) {
            return
        }
        setMute(StreamData.getMute())

        binding.btnMute.setOnClickListener {
            var mute = StreamData.getMute()
            mute = if (mute == 0) 1 else 0
            setMute(mute)
            fragments[0].play()
            StreamData.setMute(mute)
        }
    }

    private fun setMute(mute: Int) {
        if (mute == 1) {
            fragments[0].volume = 0
            binding.btnMute.setImageResource(R.drawable.ic_baseline_volume_off_24)
        } else {
            fragments[0].volume = 100
            binding.btnMute.setImageResource(R.drawable.ic_baseline_volume_on_24)
        }
    }

    fun showMute() {
        binding.btnMute.visibility = View.VISIBLE
    }

    // === Backchannel Mic ===

    // btnMic is push-to-talk; the touch listener calls performClick() on release
    @SuppressLint("ClickableViewAccessibility")
    private fun initMic() {
        if (isGroup) return
        val stream = StreamData.getById(sourceId) ?: return
        val rtspUrl = StreamData.getUrl(stream, false)
        Log.d("BACKCHANNEL", "initMic: rtspUrl=$rtspUrl")

        // Detect backchannel support on background thread
        onvifScope.launch(Dispatchers.IO) {
            try {
                val manager = BackchannelManager()
                Log.d("BACKCHANNEL", "Detecting backchannel...")
                val supported = manager.detectBackchannel(rtspUrl)
                Log.d("BACKCHANNEL", "Backchannel supported: $supported")
                if (supported) {
                    backchannelManager = manager
                    withContext(Dispatchers.Main) {
                        binding.btnMic.visibility = View.VISIBLE
                        binding.btnMic.setOnTouchListener { v, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    onMicPress()
                                    true
                                }
                                MotionEvent.ACTION_UP -> {
                                    onMicRelease()
                                    v.performClick()
                                    true
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    onMicRelease()
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BACKCHANNEL", "initMic error: ${e.message}", e)
            }
        }
    }

    private fun onMicPress() {
        if (micActive) return
        // Check permission
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startMic()
        } else {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun onMicRelease() {
        if (micActive) {
            stopMic()
        }
    }

    private fun startMic() {
        val stream = StreamData.getById(sourceId) ?: return
        val rtspUrl = StreamData.getUrl(stream, false)

        micActive = true
        binding.btnMic.setImageResource(R.drawable.ic_mic)
        binding.micBorderOverlay.visibility = View.VISIBLE
        // Haptic feedback on press
        vibrate(50)
        // Mute playback to prevent feedback
        if (fragments.isNotEmpty()) {
            fragments[0].volume = 0
            fragments[0].mediaPlayer.volume = 0
        }
        Log.d("BACKCHANNEL", "startMic: starting with url=$rtspUrl")

        onvifScope.launch(Dispatchers.IO) {
            try {
                val manager = backchannelManager ?: BackchannelManager()
                backchannelManager = manager
                // Ensure any previous session is fully stopped
                if (manager.state != BackchannelManager.State.IDLE) {
                    manager.stop()
                }
                val result = manager.start(rtspUrl)
                Log.d("BACKCHANNEL", "startMic: start result=$result")
                if (!result) {
                    withContext(Dispatchers.Main) {
                        stopMic()
                        android.widget.Toast.makeText(
                            this@StreamsActivity,
                            "Failed to start audio",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("BACKCHANNEL", "startMic error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    stopMic()
                    android.widget.Toast.makeText(
                        this@StreamsActivity,
                        "Failed to start audio: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun vibrate(millis: Long) {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(millis)
        }
    }

    private fun stopMic() {
        micActive = false
        binding.btnMic.setImageResource(R.drawable.ic_mic_off)
        binding.micBorderOverlay.visibility = View.GONE
        // Haptic feedback on release
        vibrate(30)
        // Restore playback audio
        if (fragments.isNotEmpty()) {
            val vol = if (StreamData.getMute() == 0) 100 else 0
            fragments[0].volume = vol
            fragments[0].mediaPlayer.volume = vol
        }
        onvifScope.launch(Dispatchers.IO) {
            backchannelManager?.stop()
        }
    }

    fun hideLoading(streamId: Int) {
        loadings.remove(streamId)
        if (loadings.isEmpty())
            binding.progressBar.pbLoading.visibility = View.GONE
    }

    private val callback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            back()
        }
    }

    private fun back() {
        if (GroupData.backGroupId > -1) {
            reload("group", GroupData.backGroupId)
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        startAll(true)
        handler.postDelayed(runnable, watchdogInterval)
    }

    override fun onStop() {
        super.onStop()
        // Stop mic when app loses focus
        if (micActive) {
            stopMic()
        }
        for (f in fragments) {
            f.release()
        }
        handler.removeCallbacks(runnable)
    }

    private fun startAll(init: Boolean = false) {
        for (f in fragments) {
            if (init)
                f.initPlayer()
            f.start(StreamData.getUrl(f.stream, isGroup).toUri())
            loadings[f.streamId] = true
        }
        binding.progressBar.pbLoading.visibility = View.VISIBLE
    }

    private val runnable = object : Runnable {
        override fun run() {
            for (f in fragments) {
                if (f.watchdog())
                    continue
                binding.progressBar.pbLoading.visibility = View.VISIBLE
            }
            handler.postDelayed(this, watchdogInterval)
        }
    }

    override fun resizeLayout() {
        resizeGrid(fragments)
        gestureDetector.reset()
        initBars()
    }

    override fun dispatchTouchEvent(e: MotionEvent?): Boolean {
        if (e == null)
            return super.dispatchTouchEvent(null)

        val res = gestureDetector.onTouchEvent(e) || e.pointerCount > 1
        if (res)
            gestureInProgress = e.pointerCount + 1

        if (e.action == MotionEvent.ACTION_UP) {
            if (gestureInProgress == 0)
                initBars()
            else
                gestureInProgress -= 1

            for (f in fragments) {
                if (f.getFrame().getGlobalVisibleRect(Rect()))
                    f.play()
                else
                    f.stop()
            }
        }
        return res || super.dispatchTouchEvent(e)
    }

    private fun reload(type: String, id: Int) {
        if (type == "stream") {
            val i = streams.indexOf(id)
            Effects.dimmer(fragments[i].getFrame())
        }
        startActivity(
            Intent(this, StreamsActivity::class.java)
                .putExtra("type", type)
                .putExtra("id", id)
        )
    }

    private fun initBars() {
        Effects.cancel()
        if (!isGroup) {
            val stream = StreamData.getById(sourceId)
            if (stream?.sftp != null) {
                binding.toolbar.btnLink.visibility = View.VISIBLE
                binding.toolbar.btnLink.setOnClickListener {
                    filesScreen()
                }
            }
        }
        binding.toolbar.root.visibility = View.VISIBLE
        binding.llStreamsBar.visibility = View.VISIBLE
        if (hideBars) {
            Effects.delayedFadeOut(arrayOf(binding.toolbar.root, binding.llStreamsBar))
        }
    }

    private fun filesScreen() {
        val intent = Intent(this, FilesActivity::class.java)
            .putExtra("streamId", sourceId)
        startActivity(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        initLayout(binding.root)
    }

    private fun initOnvifFeatures() {
        Log.d("ONVIF", "initOnvifFeatures called")
        // For single stream, check if it's ONVIF enabled
        if (!isGroup && streams.isNotEmpty()) {
            val stream = StreamData.getById(streams[0])
            Log.d("ONVIF", "Stream: ${stream?.name}, isOnvif: ${stream?.isOnvifDevice}")
            
            if (stream?.isOnvifDevice == true && stream.onvifServiceUrl != null) {
                Log.d("ONVIF", "ONVIF stream detected - PTZ controls available via touch")
                
                // Initialize ONVIF features
                onvifManager = ONVIFManager.getInstance()
                
                // Enable PTZ controls for ONVIF devices regardless of strict capability flags
                Log.d("ONVIF", "Initializing PTZ controls")
                initPTZControls(stream)
                
                if (stream.deviceCapabilities?.supportsMotionEvents == true) {
                    Log.d("ONVIF", "Initializing motion detection")
                    Log.d("ONVIF", "Device capabilities: ${stream.deviceCapabilities}")
                    initMotionDetection(stream)
                } else {
                    Log.d("ONVIF", "Motion events not supported. Capabilities: ${stream.deviceCapabilities}")
                }
            } else {
                Log.d("ONVIF", "Not an ONVIF device or missing service URL")
            }
        } else {
            Log.d("ONVIF", "Group stream or no streams available")
        }
    }

    private fun initPTZControls(stream: StreamDataModel) {
        Log.d("ONVIF", "initPTZControls called")
        val serviceUrl = stream.onvifServiceUrl ?: return
        val credentials = stream.onvifCredentials
        val deviceId = stream.url // Use RTSP URL as device ID consistently
        
        Log.d("ONVIF", "Creating PTZ control view")
        // Create PTZ control view
        ptzControlView = PTZControlView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        Log.d("ONVIF", "Adding PTZ control view to container")
        // Add to streams container
        binding.rlStreamsBox.addView(ptzControlView)
        
        Log.d("ONVIF", "PTZ control view setup complete")
        
        // Initialize PTZ controller SYNCHRONOUSLY first
        Log.d("ONVIF", "Creating PTZ controller for device: $deviceId")
        val controller = onvifManager?.initializePTZController(deviceId, serviceUrl, credentials)
        
        if (controller != null) {
            Log.d("ONVIF", "PTZ controller created successfully")
            ptzControlView?.setPTZController(controller)
            
            ptzControlView?.setListener(object : PTZControlView.PTZControlListener {
                override fun onPTZCommand(direction: PTZDirection, isPressed: Boolean) {
                    Log.d("ONVIF", "PTZ command: $direction, pressed: $isPressed")
                    onvifScope.launch {
                        if (isPressed) {
                            onvifManager?.performPTZMove(deviceId, direction)
                        } else {
                            onvifManager?.stopPTZMovement(deviceId)
                        }
                    }
                }

                override fun onZoomCommand(factor: Float) {
                    Log.d("ONVIF", "Zoom command: $factor")
                    onvifScope.launch {
                        onvifManager?.performZoom(deviceId, factor)
                    }
                }
            })
        } else {
            Log.e("ONVIF", "Failed to create PTZ controller")
        }
    }

    private fun initMotionDetection(stream: StreamDataModel) {
        Log.d("MOTION_INIT", "=== Initializing motion detection ===")
        val serviceUrl = stream.onvifServiceUrl ?: return
        val credentials = stream.onvifCredentials
        
        Log.d("MOTION_INIT", "Service URL: $serviceUrl")
        Log.d("MOTION_INIT", "Has credentials: ${credentials != null}")
        
        // Create motion indicator
        motionIndicator = MotionIndicator(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        // Add to streams container
        binding.rlStreamsBox.addView(motionIndicator)
        Log.d("MOTION_INIT", "Motion indicator added to view")
        
        // Initialize motion event service
        onvifScope.launch {
            val deviceId = stream.url
            Log.d("MOTION_INIT", "Initializing motion event service for device: $deviceId")
            
            val service = onvifManager?.initializeMotionEventService(deviceId, serviceUrl, credentials)
            Log.d("MOTION_INIT", "Motion event service created: ${service != null}")
            
            onvifManager?.setListener(object : ONVIFManager.ONVIFManagerListener {
                override fun onMotionDetected(deviceId: String) {
                    Log.d("MOTION_INIT", "*** Motion detected callback for: $deviceId ***")
                    if (deviceId == stream.url) {
                        runOnUiThread {
                            Log.d("MOTION_INIT", "Showing motion indicator")
                            motionIndicator?.showMotionDetected()
                        }
                    }
                }
                
                override fun onMotionStopped(deviceId: String) {
                    Log.d("MOTION_INIT", "*** Motion stopped callback for: $deviceId ***")
                    if (deviceId == stream.url) {
                        runOnUiThread {
                            Log.d("MOTION_INIT", "Hiding motion indicator")
                            motionIndicator?.hideMotionDetected()
                        }
                    }
                }
                
                override fun onPTZError(deviceId: String, error: String) {
                    Log.w("PTZ", "$deviceId: $error")
                }
            })
            
            onvifManager?.subscribeToMotionEvents(deviceId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        onvifScope.cancel()

        if (!isGroup && streams.isNotEmpty()) {
            StreamData.getById(streams[0])?.url?.let {
                onvifManager?.releaseMotionEventService(it)
            }
        }
    }

}
