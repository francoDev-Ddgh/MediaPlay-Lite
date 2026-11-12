package com.sd.play

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        // El botón ⏮ siempre va a la canción anterior si existe, sin reiniciar la actual
        val controlled = object : ForwardingPlayer(player) {
            override fun seekToPrevious() {
                if (hasPreviousMediaItem()) seekToPreviousMediaItem()
            }
        }

        session = MediaSession.Builder(this, controlled).build()
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
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }
}
