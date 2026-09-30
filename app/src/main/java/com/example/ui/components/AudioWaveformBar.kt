package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.live.SessionState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HolographicViolet
import com.example.ui.theme.NeonMagenta
import kotlin.math.sin

@Composable
fun AudioWaveformBar(
    amplitude: Float,
    sessionState: SessionState,
    modifier: Modifier = Modifier,
    barCount: Int = 18,
    barWidth: Dp = 3.dp,
    maxHeight: Dp = 38.dp
) {
    val barColor = when (sessionState) {
        SessionState.SPEAKING -> Brush.verticalGradient(listOf(NeonMagenta, HolographicViolet))
        SessionState.LISTENING -> Brush.verticalGradient(listOf(ElectricCyan, HolographicViolet))
        SessionState.CONNECTING -> Brush.verticalGradient(listOf(ElectricCyan, NeonMagenta))
        else -> Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
    }

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val factor = if (sessionState == SessionState.SPEAKING || sessionState == SessionState.LISTENING) {
                val wave = (sin((i.toDouble() / barCount) * Math.PI) * 0.7 + 0.3).toFloat()
                (amplitude * wave).coerceIn(0.12f, 1f)
            } else if (sessionState == SessionState.CONNECTING) {
                (sin((i * 0.5) + System.currentTimeMillis() * 0.005) * 0.35 + 0.45).toFloat().coerceIn(0.15f, 0.9f)
            } else {
                0.1f
            }

            val animatedHeightFraction by animateFloatAsState(
                targetValue = factor,
                animationSpec = tween(durationMillis = 80),
                label = "BarHeight_$i"
            )

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(animatedHeightFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}
