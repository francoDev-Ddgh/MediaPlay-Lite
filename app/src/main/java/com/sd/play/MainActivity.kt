package com.sd.play

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SDTheme {
                App(onExit = { finish() })
            }
        }
    }
}

private val TABS = listOf("Álbumes", "Carpetas", "Todos")

fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

fun missingPermissions(context: Context): Array<String> =
    buildList<String> {
        add(audioPermission())
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        .toTypedArray()

fun isBatteryExempt(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
fun App(onExit: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var audioOk by remember { mutableStateOf(hasAudioPermission(context)) }
    var batteryOk by remember { mutableStateOf(isBatteryExempt(context)) }

    // Sin permiso de audio la app se cierra de forma ordenada
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        audioOk = hasAudioPermission(context)
        if (!audioOk) onExit()
    }

    LaunchedEffect(Unit) {
        val missing = missingPermissions(context)
        if (missing.isNotEmpty()) permLauncher.launch(missing)
    }

    val watcher = remember(context) { LibraryWatcher(context) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && audioOk) watcher.start()
            if (event == Lifecycle.Event.ON_STOP) watcher.stop()
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = hasAudioPermission(context)
                // Solo cerrar si el permiso se quitó mientras la app estaba abierta
                if (audioOk && !granted) onExit()
                audioOk = granted
                batteryOk = isBatteryExempt(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Biblioteca: carga inicial y actualización en tiempo real.
    // Los cambios se agrupan: como máximo se recarga cada 0,3 s mientras Android escanea.
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val reloadSignal = remember { Channel<Unit>(Channel.CONFLATED) }

    DisposableEffect(audioOk) {
        if (!audioOk) return@DisposableEffect onDispose { }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reloadSignal.trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    LaunchedEffect(audioOk) {
        if (!audioOk) return@LaunchedEffect
        // Arranca la vigilancia en cuanto el permiso está concedido (la primera vez no hay ON_START con permiso)
        watcher.start()
        reloadSignal.trySend(Unit)
        // Recarga la lista en cuanto llega un cambio, sin esperar
        while (true) {
            reloadSignal.receive()
            songs = withContext(Dispatchers.IO) { loadSongs(context) }
            watcher.knownPaths = songs.mapTo(HashSet()) { it.path }
        }
    }

    val controller = rememberController()
    val ui = rememberPlayerUi(controller)

    var likesOn by rememberSaveable { mutableStateOf(false) }
    var likeSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    LaunchedEffect(likesOn, songs, ui.title) {
        likeSongs = withContext(Dispatchers.IO) {
            val ids = AppDatabase.get(context).playDao().top40()
            ids.mapNotNull { id -> songs.find { it.id == id } }
        }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = tab) { TABS.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage) { tab = pagerState.currentPage }

    BackHandler(enabled = playerOpen) { playerOpen = false }

    val onPlaySong: (Song) -> Unit = { s -> controller?.playFolderOf(s, songs) }
    val onPlayLikes: (List<Song>, Int) -> Unit = { list, index ->
        controller?.playList(list, index, shuffle = true)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            if (audioOk && !batteryOk) BatteryBanner(context)

            TabRow(selectedTabIndex = pagerState.currentPage) {
                TABS.forEachIndexed { i, title ->
                    Tab(
                        selected = pagerState.currentPage == i,
                        onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                        text = { Text(title) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> AlbumsScreen(songs, onPlaySong)
                    1 -> FoldersScreen(songs, onPlaySong)
                    else -> AllScreen(
                        songs = songs,
                        likes = likeSongs,
                        likesOn = likesOn,
                        onToggleLikes = { likesOn = !likesOn },
                        onPlay = onPlaySong,
                        onPlayLikes = onPlayLikes,
                    )
                }
            }

            if (ui.hasItem && controller != null) {
                MiniPlayer(ui, controller) { playerOpen = true }
            }
        }

        if (playerOpen && controller != null) {
            FullPlayer(ui, controller) { playerOpen = false }
        }
    }
}

@Composable
fun BatteryBanner(context: Context) {
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "La app funcionaría mejor sin restricciones de batería.",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }) {
                Text("Activar")
            }
        }
    }
}
