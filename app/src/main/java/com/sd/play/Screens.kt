package com.sd.play

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val LETTERS = listOf("#") + ('A'..'Z').map { it.toString() }

private fun letterOf(title: String): String {
    val c = title.firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c in 'A'..'Z') c.toString() else "#"
}

@Composable
fun SongRow(title: String, subtitle: String, artwork: Uri?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1) },
        supportingContent = { Text(subtitle, maxLines = 1) },
        leadingContent = { Artwork(artwork, 48.dp, RoundedCornerShape(8.dp)) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun IconRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1) },
        supportingContent = { Text(subtitle, maxLines = 1) },
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun AlbumsScreen(songs: List<Song>, onPlay: (Song) -> Unit) {
    val albums = remember(songs) { albumsOf(songs) }
    var open by remember { mutableStateOf<Album?>(null) }
    BackHandler(enabled = open != null) { open = null }

    val current = open
    if (current == null) {
        LazyColumn {
            items(albums) { a ->
                SongRow(a.name, "${a.artist} · ${a.songs.size} canciones", albumArtUri(a.albumId)) { open = a }
            }
        }
    } else {
        LazyColumn {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(albumArtUri(current.albumId), 96.dp, RoundedCornerShape(16.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(current.name, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                        Text(current.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
            }
            items(current.songs) { s ->
                SongRow(s.title, s.artist, s.artwork) { onPlay(s) }
            }
        }
    }
}

@Composable
fun FoldersScreen(songs: List<Song>, onPlay: (Song) -> Unit) {
    val folders = remember(songs) { foldersOf(songs) }
    var open by remember { mutableStateOf<FolderEntry?>(null) }
    BackHandler(enabled = open != null) { open = null }

    val current = open
    if (current == null) {
        LazyColumn {
            items(folders) { f ->
                IconRow(f.name, "${f.count} canciones", Icons.Filled.Folder) { open = f }
            }
        }
    } else {
        val list = remember(songs, current) { songs.filter { it.folder == current.path } }
        LazyColumn {
            item {
                Text(
                    current.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(list) { s ->
                SongRow(s.title, s.artist, s.artwork) { onPlay(s) }
            }
        }
    }
}

@Composable
fun AllScreen(
    songs: List<Song>,
    likes: List<Song>,
    likesOn: Boolean,
    onToggleLikes: () -> Unit,
    onPlay: (Song) -> Unit,
    onPlayLikes: (List<Song>, Int) -> Unit,
    showLetters: Boolean,
) {
    val list = if (likesOn) likes else songs
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Índice de la primera canción de cada letra, para el índice lateral
    val firstIndex = remember(list, showLetters, likesOn) {
        if (showLetters && !likesOn) {
            list.withIndex().groupBy { letterOf(it.value.title) }.mapValues { it.value.first().index }
        } else {
            emptyMap()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            FilterChip(
                selected = likesOn,
                onClick = onToggleLikes,
                label = { Text("Modo Likes") }
            )
        }
        if (likesOn && likes.isEmpty()) {
            Text(
                "Todavía no hay canciones escuchadas.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(list) { index, s ->
                    SongRow(s.title, s.artist, s.artwork) {
                        if (likesOn) onPlayLikes(list, index) else onPlay(s)
                    }
                }
            }
            if (firstIndex.isNotEmpty()) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LETTERS.forEach { letter ->
                        val target = firstIndex[letter]
                        Text(
                            letter,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (target != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier
                                .padding(vertical = 1.dp)
                                .clickable(enabled = target != null) {
                                    target?.let { scope.launch { listState.scrollToItem(it) } }
                                }
                        )
                    }
                }
            }
        }
    }
}
