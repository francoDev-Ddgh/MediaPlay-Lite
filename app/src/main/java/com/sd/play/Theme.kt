package com.sd.play

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF3A5F94),
    background = Color(0xFFF8FAFF),
    surface = Color(0xFFF8FAFF),
    onSurface = Color(0xFF111827),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9EC5FF),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF004A8F),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFA9C4EA),
    background = Color(0xFF0E1420),
    surface = Color(0xFF0E1420),
    onSurface = Color(0xFFE3E8F3),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SDTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        shapes = AppShapes,
        content = {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                content = content
            )
        }
    )
}
