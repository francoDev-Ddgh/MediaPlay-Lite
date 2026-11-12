package com.sd.play

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(AudioPrefs.NAME, Context.MODE_PRIVATE) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var surround by remember { mutableStateOf(prefs.getBoolean(AudioPrefs.SURROUND, false)) }
    var solid by remember { mutableStateOf(prefs.getBoolean(AudioPrefs.SOLID, false)) }
    var connected by remember { mutableStateOf(headphonesConnected(context)) }

    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>?) {
                connected = headphonesConnected(context)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>?) {
                connected = headphonesConnected(context)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    BackHandler(onBack = onBack)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text("Ajustes", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Ecualizador",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            EffectRow(
                title = "Sonido envolvente",
                description = "Amplía la imagen estéreo en audífonos.",
                checked = surround,
                enabled = connected,
                onChange = {
                    surround = it
                    prefs.edit().putBoolean(AudioPrefs.SURROUND, it).apply()
                }
            )
            EffectRow(
                title = "Sólido espacial",
                description = "Más graves y un poco de espacio (reverberación).",
                checked = solid,
                enabled = connected,
                onChange = {
                    solid = it
                    prefs.edit().putBoolean(AudioPrefs.SOLID, it).apply()
                }
            )
            if (!connected) {
                Text(
                    "Conecta audífonos para activar estos efectos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun EffectRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
