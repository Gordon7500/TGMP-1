package com.tuned.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun Visualizer(amplitude: Int, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val barCount = 24
    val levels = remember { List(barCount) { Animatable(0.05f) } }
    val scope = rememberCoroutineScope()

    LaunchedEffect(amplitude, isPlaying) {
        val target = if (isPlaying) (amplitude / 32767f).coerceIn(0.05f, 1f) else 0.05f
        levels.forEachIndexed { i, anim ->
            val jitter = (0.65f + (i % 5) * 0.09f)
            scope.launch {
                anim.animateTo(target * jitter, animationSpec = tween(120))
            }
        }
    }

    Canvas(modifier = modifier.fillMaxWidth().height(30.dp)) {
        val barWidth = size.width / barCount
        levels.forEachIndexed { i, anim ->
            val color = VisualizerColors[i % VisualizerColors.size]
            val barHeight = size.height * anim.value
            drawRoundBar(
                x = i * barWidth + 1.5f,
                width = barWidth - 3f,
                height = barHeight,
                totalHeight = size.height,
                color = color
            )
        }
    }
}

private fun DrawScope.drawRoundBar(x: Float, width: Float, height: Float, totalHeight: Float, color: androidx.compose.ui.graphics.Color) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x, totalHeight - height),
        size = Size(width, height),
        cornerRadius = CornerRadius(width / 2, width / 2),
        alpha = 0.9f
    )
}
