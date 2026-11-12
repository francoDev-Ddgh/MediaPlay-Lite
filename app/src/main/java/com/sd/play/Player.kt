package com.sd.play

import android.content.ComponentName
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay

data class PlayerUi(
    val hasItem: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val position: Long = 0L,
    val duration: Long = 0L,
)

fun Player.toUi() = PlayerUi(
    hasItem = currentMediaItem != null,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    playing = isPlaying,
    shuffle = shuffleModeEnabled,
    repeat = repeatMode,
    position = currentPosition.coerceAtLeast(0L),
    duration = duration.coerceAtLeast(0L),
)

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build()
    )
    .build()

// Reproduce una lista; si shuffle es true, el orden se mezcla y la canción inicial queda primero
fun MediaController.playList(songs: List<Song>, start: Int, shuffle: Boolean = false) {
    if (songs.isEmpty()) return
    setMediaItems(songs.map { it.toMediaItem() }, start.coerceIn(0, songs.lastIndex), 0L)
    shuffleModeEnabled = shuffle
    prepare()
    play()
}

// La cola es siempre la carpeta física donde está la canción
fun MediaController.playFolderOf(song: Song, all: List<Song>) {
    val folder = all.filter { it.folder == song.folder }.sortedBy { it.title.lowercase() }
    playList(folder, folder.indexOfFirst { it.id == song.id })
}

fun MediaController.cycleRepeat() {
    repeatMode = when (repeatMode) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
        else -> Player.REPEAT_MODE_OFF
    }
}

fun repeatLabel(mode: Int): String = when (mode) {
    Player.REPEAT_MODE_ONE -> "Repetir canción"
    Player.REPEAT_MODE_ALL -> "Repetir carpeta"
    else -> "Repetir desactivado"
}

fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun rememberController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ controller = future.get() }, ContextCompat.getMainExecutor(context))
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }
    return controller
}

@Composable
fun rememberPlayerUi(controller: MediaController?): PlayerUi {
    var ui by remember { mutableStateOf(PlayerUi()) }
    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                ui = player.toUi()
            }
        }
        controller.addListener(listener)
        ui = controller.toUi()
        onDispose { controller.removeListener(listener) }
    }
    // Actualiza la posición de la barra mientras suena
    LaunchedEffect(controller, ui.playing) {
        while (controller != null && ui.playing) {
            delay(500)
            ui = controller.toUi()
        }
    }
    return ui
}

@Composable
fun MiniPlayer(ui: PlayerUi, controller: MediaController, onOpen: () -> Unit) {
    var drag by remember { mutableFloatStateOf(0f) }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (drag < -120f) onOpen()
                        drag = 0f
                    }
                ) { _, amount -> drag += amount }
            }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(ui.title, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                Text(ui.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { if (ui.playing) controller.pause() else controller.play() }) {
                Icon(
                    imageVector = if (ui.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
fun FullPlayer(ui: PlayerUi, controller: MediaController, onMinimize: () -> Unit) {
    var drag by remember { mutableFloatStateOf(0f) }
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (drag > 150f) onMinimize()
                        drag = 0f
                    }
                ) { _, amount -> drag += amount }
            }
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(ui.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            Text(ui.artist, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Spacer(Modifier.height(32.dp))
            Slider(
                value = if (ui.duration > 0) ui.position.toFloat() / ui.duration else 0f,
                onValueChange = { controller.seekTo((it * ui.duration).toLong()) }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(ui.position), style = MaterialTheme.typography.bodySmall)
                Text(formatMs(ui.duration), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(onClick = { controller.seekToPrevious() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = null, modifier = Modifier.size(40.dp))
                }
                FilledIconButton(onClick = { if (ui.playing) controller.pause() else controller.play() }) {
                    Icon(
                        imageVector = if (ui.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null
                    )
                }
                IconButton(onClick = { controller.seekToNext() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                IconButton(onClick = { controller.shuffleModeEnabled = !ui.shuffle }) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = null,
                        tint = if (ui.shuffle) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { controller.cycleRepeat() }) {
                    Icon(
                        imageVector = if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = null,
                        tint = if (ui.repeat == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(repeatLabel(ui.repeat), style = MaterialTheme.typography.bodySmall)
        }
    }
}
