package com.sd.play

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log

object AudioPrefs {
    const val NAME = "sd_settings"
    const val SURROUND = "surround"
    const val SOLID = "solid"
}

private val HEADPHONE_TYPES = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_USB_HEADSET,
)

fun headphonesConnected(context: Context): Boolean {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONE_TYPES }
}

// Efectos de Android sobre la sesión de audio del reproductor
class AudioEffects(private val sessionId: Int) {
    private var virtualizer: Virtualizer? = null
    private var bassBoost: BassBoost? = null
    private var reverb: PresetReverb? = null

    fun apply(surround: Boolean, solid: Boolean) {
        if (sessionId == 0) return
        try {
            val v = virtualizer ?: Virtualizer(0, sessionId).also {
                it.setStrength(1000.toShort())
                virtualizer = it
            }
            v.enabled = surround

            val b = bassBoost ?: BassBoost(0, sessionId).also {
                it.setStrength(700.toShort())
                bassBoost = it
            }
            b.enabled = solid

            val r = reverb ?: PresetReverb(0, sessionId).also {
                it.preset = PresetReverb.PRESET_LARGEHALL
                reverb = it
            }
            r.enabled = solid
        } catch (e: Exception) {
            Log.e("SDFx", "Efectos no disponibles en este teléfono", e)
        }
    }

    fun release() {
        virtualizer?.release()
        bassBoost?.release()
        reverb?.release()
        virtualizer = null
        bassBoost = null
        reverb = null
    }
}
