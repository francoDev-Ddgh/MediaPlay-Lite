package com.sd.play

import android.content.Context
import android.media.MediaScannerConnection
import java.io.File

private val AUDIO_EXT = setOf(
    "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "ape", "amr", "mid", "3gp", "webm", "mka"
)

// Recorre el almacenamiento de forma recursiva y pide a Android indexar los audios
// que todavía no aparecen en MediaStore. "known" son las rutas ya indexadas.
fun scanMissingAudio(context: Context, known: Set<String>) {
    val pending = ArrayList<String>()
    val stack = ArrayDeque<File>()
    stack.add(File(ROOT_PATH))
    while (stack.isNotEmpty()) {
        val dir = stack.removeLast()
        val children = dir.listFiles() ?: continue
        for (f in children) {
            if (f.isDirectory) {
                if (!f.name.startsWith(".")) stack.add(f)
            } else if (f.extension.lowercase() in AUDIO_EXT && f.absolutePath !in known) {
                pending += f.absolutePath
            }
        }
    }
    if (pending.isNotEmpty()) {
        MediaScannerConnection.scanFile(context, pending.toTypedArray(), null, null)
    }
}
