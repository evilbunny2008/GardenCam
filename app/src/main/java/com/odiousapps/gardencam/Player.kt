package com.odiousapps.gardencam

import android.content.Context
import android.net.Uri
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.IOException

interface Player {
    var libVlc: LibVLC
    var mediaPlayer: MediaPlayer
    var volume: Int

    fun initPlayer(context: Context, view: VLCVideoLayout, tcp: Boolean = true, audio: Boolean = true) {
        val options = arrayListOf(
            "--file-caching=300",
            "--live-caching=100",
            "--network-caching=50",
            "--drop-late-frames",
            "--skip-frames",
            "--clock-jitter=0",
            "--clock-synchro=0",
            "--image-duration=1",  // for "events" slideshow
        )
        if (tcp)
            options.add("--rtsp-tcp")
        // Without audio, video is no longer clocked against Android's audio output buffer
        if (!audio)
            options.add("--no-audio")
        if (!StreamData.logConnections)
            options.add("--verbose=-1")

        libVlc = LibVLC(context, options)
        mediaPlayer = MediaPlayer(libVlc)
        mediaPlayer.attachViews(view, null, false, false)

        // Fit the picture to the frame, keeping its aspect ratio and centring it. VLC rescales it
        // whenever the window changes. (SURFACE_FIT_SCREEN instead fixes a zoom factor computed
        // once from the track and layout, which left the picture far smaller than the frame.)
        mediaPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT

        mediaPlayer.setEventListener {
            when (it.type) {
                MediaPlayer.Event.Buffering -> if (it.buffering == 100f)
                    onBufferReady(mediaPlayer.audioTracksCount > 0)
            }
        }
    }

    fun start(uri: Uri) {
        startPlayback(Media(libVlc, uri))
    }

    fun start(path: String) {
        startPlayback(Media(libVlc, path))
    }

    private fun startPlayback(media: Media) {
        try {
            // Repeated per media, which takes precedence over the instance default
            media.addOption(":network-caching=50")
            mediaPlayer.media = media
            play()
            media.release()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun play() {
        mediaPlayer.play()
        mediaPlayer.volume = volume
    }

    fun stop() {
        mediaPlayer.stop()
    }

    fun release() {
        mediaPlayer.release()
        mediaPlayer.media?.release()
        libVlc.release()
    }

    fun watchdog(): Boolean {
        if (mediaPlayer.isPlaying)
            return true
        mediaPlayer.stop()
        play()
        return false
    }

    fun onBufferReady(isAudioAvailable: Boolean)
}
