package com.tuned.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Black = Color(0xFF000000)
val Surface = Color(0xFF0D0D0F)
val Surface2 = Color(0xFF16161A)
val Line = Color(0xFF221F26)
val TextPrimary = Color(0xFFF2F2F2)
val TextMuted = Color(0xFF7A7A7E)
val Violet = Color(0xFF8B5CF6)
val Magenta = Color(0xFFEC4899)
val Cyan = Color(0xFF22D3EE)
val Amber = Color(0xFFFBBF24)

val VisualizerColors = listOf(Violet, Magenta, Cyan, Amber)

private val TunedColorScheme = darkColorScheme(
    background = Black,
    surface = Surface,
    surfaceVariant = Surface2,
    primary = Violet,
    secondary = Cyan,
    tertiary = Magenta,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = Line
)

@Composable
fun TunedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TunedColorScheme,
        content = content
    )
}
