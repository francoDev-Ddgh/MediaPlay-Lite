package com.sd.play

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.SystemClock
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var fx: AudioEffects
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs by lazy { getSharedPreferences(AudioPrefs.NAME, MODE_PRIVATE) }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applyEffects() }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>?) = applyEffects()
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>?) = applyEffects()
    }

    // Conteo de reproducciones: cuenta si se escuchó 30 s o la mitad de la canción
    private var startedAt = 0L
    private var playedMs = 0L
    private var lastDuration = 0L
    private var songId: Long? = null
    private var counted = false

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startedAt = SystemClock.elapsedRealtime()
                    if (player.duration > 0) lastDuration = player.duration
                } else {
                    stopClock()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                stopClock()
                songId = mediaItem?.mediaId?.toLongOrNull()
                playedMs = 0L
                lastDuration = 0L
                counted = false
                if (player.isPlaying) startedAt = SystemClock.elapsedRealtime()
            }
        })

        // Efectos de audio sobre la sesión del reproductor
        fx = AudioEffects(player.audioSessionId)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        audioManager().registerAudioDeviceCallback(deviceCallback, null)
        applyEffects()

        // El botón ⏮ siempre va a la canción anterior si existe, sin reiniciar la actual
        val controlled = object : ForwardingPlayer(player) {
            override fun seekToPrevious() {
                if (hasPreviousMediaItem()) seekToPreviousMediaItem()
            }
        }

        session = MediaSession.Builder(this, controlled).build()
    }

    private fun audioManager(): AudioManager =
        getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Los efectos solo se activan con audífonos conectados
    private fun applyEffects() {
        if (!::fx.isInitialized) return
        val hp = headphonesConnected(this)
        fx.apply(
            surround = hp && prefs.getBoolean(AudioPrefs.SURROUND, false),
            solid = hp && prefs.getBoolean(AudioPrefs.SOLID, false),
        )
    }

    private fun stopClock() {
        if (startedAt != 0L) {
            playedMs += SystemClock.elapsedRealtime() - startedAt
            startedAt = 0L
        }
        val id = songId ?: return
        if (counted) return
        val needed = if (lastDuration > 0) minOf(30_000L, lastDuration / 2) else 30_000L
        if (playedMs >= needed) {
            counted = true
            scope.launch {
                AppDatabase.get(applicationContext).playDao().increment(id)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        audioManager().unregisterAudioDeviceCallback(deviceCallback)
        if (::fx.isInitialized) fx.release()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }
}
