package com.sd.play

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SongRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
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
                SongRow(a.name, a.artist, Icons.Filled.Album) { open = a }
            }
        }
    } else {
        LazyColumn {
            item {
                Text(
                    current.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(current.songs) { s ->
                SongRow(s.title, s.artist, Icons.Filled.MusicNote) { onPlay(s) }
            }
        }
    }
}

@Composable
fun FoldersScreen(songs: List<Song>, onPlay: (Song) -> Unit) {
    var path by rememberSaveable { mutableStateOf(ROOT_PATH) }
    BackHandler(enabled = path != ROOT_PATH) { path = path.substringBeforeLast('/') }

    val folders = remember(songs, path) { subfoldersOf(songs, path) }
    val direct = remember(songs, path) {
        songs.filter { it.folder == path }.sortedBy { it.title.lowercase() }
    }
    val header = if (path == ROOT_PATH) "root" else path.substringAfterLast('/')

    LazyColumn {
        item {
            Text(
                header,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
        }
        items(folders) { f ->
            SongRow(f.name, "${f.count} canciones", Icons.Filled.Folder) { path = f.path }
        }
        items(direct) { s ->
            SongRow(s.title, s.artist, Icons.Filled.MusicNote) { onPlay(s) }
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
) {
    val list = if (likesOn) likes else songs
    Column {
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
        LazyColumn {
            itemsIndexed(list) { index, s ->
                SongRow(s.title, s.artist, Icons.Filled.MusicNote) {
                    if (likesOn) onPlayLikes(list, index) else onPlay(s)
                }
            }
        }
    }
}
