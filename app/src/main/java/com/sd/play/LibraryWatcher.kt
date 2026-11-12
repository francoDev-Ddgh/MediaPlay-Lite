package com.sd.play

import android.content.Context
import android.media.MediaScannerConnection
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private val AUDIO_EXT = setOf(
    "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "ape", "amr", "mid", "3gp", "webm", "mka"
)

private val EVENTS = FileObserver.CREATE or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE or
    FileObserver.MOVED_FROM or FileObserver.DELETE

private fun isAudio(f: File): Boolean = f.isFile && f.extension.lowercase() in AUDIO_EXT

// Vigila las carpetas con FileObserver y solo escanea mientras la app está visible.
class LibraryWatcher(private val context: Context) {

    // Rutas que ya están en la biblioteca; se actualiza desde la pantalla
    @Volatile
    var knownPaths: Set<String> = emptySet()

    @Volatile
    private var running = false

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val observers = ConcurrentHashMap<String, FileObserver>()

    // Se llama al mostrar la app: escaneo completo y vigilancia de carpetas
    fun start() {
        if (running) return
        running = true
        worker.execute {
            watchTree(File(ROOT_PATH))
            Log.d("SDScan", "vigilando ${observers.size} carpetas")
            postScan(audioTree(File(ROOT_PATH)))
        }
    }

    // Se llama al ocultar la app: se dejan de vigilar carpetas y no se escanea nada
    fun stop() {
        running = false
        observers.values.forEach { it.stopWatching() }
        observers.clear()
    }

    private fun watchTree(root: File) {
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (running && stack.isNotEmpty()) {
            val dir = stack.removeLast()
            if (!dir.isDirectory || dir.name.startsWith(".")) continue
            if (observers.containsKey(dir.absolutePath)) continue
            val obs = makeObserver(dir)
            observers[dir.absolutePath] = obs
            obs.startWatching()
            dir.listFiles()?.forEach { if (it.isDirectory) stack.add(it) }
        }
    }

    private fun audioTree(root: File): List<File> {
        val result = ArrayList<File>()
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (running && stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (f in children) {
                if (f.isDirectory) {
                    if (!f.name.startsWith(".")) stack.add(f)
                } else if (isAudio(f)) {
                    result += f
                }
            }
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun makeObserver(dir: File): FileObserver =
        object : FileObserver(dir.absolutePath, EVENTS) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                val f = File(dir, path)
                Log.d("SDScan", "evento $event en ${f.absolutePath}")
                if (f.isDirectory) {
                    if ((event and (FileObserver.CREATE or FileObserver.MOVED_TO)) != 0) {
                        worker.execute {
                            watchTree(f)
                            postScan(audioTree(f))
                        }
                    }
                } else if (isAudio(f) &&
                    (event and (FileObserver.CREATE or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE)) != 0
                ) {
                    postScan(listOf(f))
                }
            }
        }

    // Pide a Android indexar solo los archivos que todavía no están en la biblioteca
    private fun postScan(files: List<File>) {
        if (files.isEmpty()) return
        main.post {
            val pending = files.filter { it.absolutePath !in knownPaths }.map { it.absolutePath }
            if (pending.isNotEmpty()) {
                Log.d("SDScan", "escaneando ${pending.size} archivos")
                MediaScannerConnection.scanFile(context, pending.toTypedArray(), null, null)
            }
        }
    }
}
