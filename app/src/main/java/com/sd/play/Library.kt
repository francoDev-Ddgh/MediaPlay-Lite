package com.sd.play

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

// Ruta interna de la raíz. Solo se usa por dentro; en pantalla siempre se muestra "root".
val ROOT_PATH: String = Environment.getExternalStorageDirectory().absolutePath

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val path: String,
) {
    val folder: String get() = path.substringBeforeLast('/')
}

data class Album(val name: String, val artist: String, val songs: List<Song>)

data class FolderEntry(val name: String, val path: String, val count: Int)

fun loadSongs(context: Context): List<Song> {
    val songs = mutableListOf<Song>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
    )
    // Todo el audio del teléfono, excepto tonos, notificaciones y alarmas
    val selection = "${MediaStore.Audio.Media.MIME_TYPE} LIKE ? AND " +
        "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
        "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
        "${MediaStore.Audio.Media.IS_ALARM} = 0"
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, arrayOf("audio/%"), null
    )?.use { c ->
        val idI = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumIdI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val durI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dataI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        while (c.moveToNext()) {
            val path = c.getString(dataI) ?: continue
            val id = c.getLong(idI)
            songs += Song(
                id = id,
                title = c.getString(titleI) ?: "Sin título",
                artist = c.getString(artistI) ?: "Artista desconocido",
                album = c.getString(albumI) ?: "Álbum desconocido",
                albumId = c.getLong(albumIdI),
                durationMs = c.getLong(durI),
                uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                path = path,
            )
        }
    }
    return songs
}

fun albumsOf(songs: List<Song>): List<Album> =
    songs.groupBy { it.albumId }
        .map { (_, list) -> Album(list.first().album, list.first().artist, list.sortedBy { it.title.lowercase() }) }
        .sortedBy { it.name.lowercase() }

fun subfoldersOf(songs: List<Song>, path: String): List<FolderEntry> {
    val prefix = "$path/"
    return songs
        .filter { it.path.startsWith(prefix) }
        .map { it.path.removePrefix(prefix) }
        .filter { it.contains('/') }
        .groupingBy { it.substringBefore('/') }
        .eachCount()
        .map { (name, count) -> FolderEntry(name, "$path/$name", count) }
        .sortedBy { it.name.lowercase() }
}

fun foldersOf(songs: List<Song>): List<FolderEntry> =
    songs.groupBy { it.folder }
        .map { (path, list) ->
            FolderEntry(if (path == ROOT_PATH) "root" else path.substringAfterLast('/'), path, list.size)
        }
        .sortedBy { it.path.lowercase() }

fun displayPath(path: String): String =
    if (path == ROOT_PATH) "root" else "root/" + path.removePrefix("$ROOT_PATH/")

fun Song.matches(query: String): Boolean =
    query.isBlank() ||
        title.contains(query, ignoreCase = true) ||
        artist.contains(query, ignoreCase = true) ||
        album.contains(query, ignoreCase = true)
