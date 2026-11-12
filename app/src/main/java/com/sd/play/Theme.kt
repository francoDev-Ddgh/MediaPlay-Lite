package com.sd.play

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF3A5F94),
    background = Color(0xFFF8FAFF),
    surface = Color(0xFFF8FAFF),
    onSurface = Color(0xFF111827),
)

@Composable
fun SDTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
