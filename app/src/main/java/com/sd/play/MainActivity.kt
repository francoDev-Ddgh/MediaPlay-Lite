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
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel

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
    var visible by remember { mutableStateOf(false) }

    // Sin permiso de audio la app se cierra de forma ordenada
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        audioOk = hasAudioPermission(context)
        if (!audioOk) onExit()
    }

    LaunchedEffect(Unit) {
        val missing = missingPermissions(context)
        if (missing.isNotEmpty()) permLauncher.launch(missing)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) visible = true
            if (event == Lifecycle.Event.ON_STOP) visible = false
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = hasAudioPermission(context)
                if (audioOk && !granted) onExit()
                audioOk = granted
                batteryOk = isBatteryExempt(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Biblioteca: carga inicial y recarga en cuanto llega un cambio
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
        reloadSignal.trySend(Unit)
        while (true) {
            reloadSignal.receive()
            try {
                songs = withContext(Dispatchers.IO) { loadSongs(context) }
                Log.d("SDScan", "recarga: ${songs.size} canciones")
            } catch (e: Exception) {
                Log.e("SDScan", "error al recargar", e)
            }
        }
    }

    // Escaneo recursivo cada 0,1 s, solo mientras la app está visible
    LaunchedEffect(audioOk, visible) {
        if (!audioOk || !visible) return@LaunchedEffect
        while (isActive) {
            val known = songs.mapTo(HashSet()) { it.path }
            withContext(Dispatchers.IO) { scanMissingAudio(context, known) }
            delay(100)
        }
    }

    val controller = rememberController()
    val ui = rememberPlayerUi(controller)

    var likesOn by rememberSaveable { mutableStateOf(false) }
    var likeSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var plays by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }

    LaunchedEffect(likesOn, songs, ui.title) {
        val result = withContext(Dispatchers.IO) {
            val dao = AppDatabase.get(context).playDao()
            Pair(dao.top40(), dao.all().associate { it.songId to it.total })
        }
        likeSongs = result.first.mapNotNull { id -> songs.find { it.id == id } }
        plays = result.second
    }

    var sortIndex by rememberSaveable { mutableIntStateOf(0) }
    val sortMode = SortMode.entries[sortIndex]
    val sortedSongs = remember(songs, sortMode, plays) { sortSongs(songs, sortMode, plays) }

    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val visibleSongs = remember(sortedSongs, query) { sortedSongs.filter { it.matches(query) } }

    var tab by rememberSaveable { mutableIntStateOf(1) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = tab) { TABS.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) { tab = pagerState.currentPage }

    BackHandler(enabled = playerOpen) { playerOpen = false }

    val currentSong = remember(songs, ui.mediaId) { songs.find { it.id == ui.mediaId?.toLongOrNull() } }

    val onPlaySong: (Song) -> Unit = { s -> controller?.playFolderOf(s, sortedSongs) }
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

            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (searching) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { query = ""; searching = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar búsqueda")
                    }
                } else {
                    Text(
                        "SDPlayer Lite",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { searching = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Buscar")
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Filled.Sort, contentDescription = "Ordenar")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = {
                                        sortIndex = mode.ordinal
                                        sortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortIndex == mode.ordinal) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                }
            }

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
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top
            ) { page ->
                when (page) {
                    0 -> AlbumsScreen(visibleSongs, currentSong, ui.playing, onPlaySong)
                    1 -> FoldersScreen(visibleSongs, currentSong, ui.playing, onPlaySong)
                    else -> AllScreen(
                        songs = visibleSongs,
                        likes = likeSongs,
                        likesOn = likesOn,
                        onToggleLikes = { likesOn = !likesOn },
                        onPlay = onPlaySong,
                        onPlayLikes = onPlayLikes,
                        showLetters = sortMode == SortMode.TITLE,
                        currentSong = currentSong,
                        playing = ui.playing,
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

        if (settingsOpen) {
            SettingsScreen { settingsOpen = false }
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
