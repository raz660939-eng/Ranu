package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.live.SessionState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HolographicViolet
import com.example.ui.theme.NeonMagenta
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HolographicOrb(
    sessionState: SessionState,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbPulseTransition")

    // Rotation angle for orbit rings
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbitRotation"
    )

    // Breathing pulse for idle & speaking states
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathScale"
    )

    // Dynamic colors based on session state
    val primaryGlowColor by animateColorAsState(
        targetValue = when (sessionState) {
            SessionState.DISCONNECTED -> HolographicViolet.copy(alpha = 0.5f)
            SessionState.CONNECTING -> ElectricCyan
            SessionState.LISTENING -> ElectricCyan
            SessionState.SPEAKING -> NeonMagenta
            SessionState.ERROR -> Color(0xFFEF4444)
        },
        animationSpec = tween(500),
        label = "PrimaryGlowColor"
    )

    val secondaryGlowColor by animateColorAsState(
        targetValue = when (sessionState) {
            SessionState.DISCONNECTED -> Color(0xFF3B1E54)
            SessionState.CONNECTING -> NeonMagenta
            SessionState.LISTENING -> HolographicViolet
            SessionState.SPEAKING -> ElectricCyan
            SessionState.ERROR -> Color(0xFF7F1D1D)
        },
        animationSpec = tween(500),
        label = "SecondaryGlowColor"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(240.dp)
            .testTag("holographic_orb")
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 120.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.28f

            // Dynamic scale combining breathing and audio amplitude
            val ampBoost = amplitude * 0.35f
            val activeScale = breathScale + ampBoost
            val coreRadius = baseRadius * activeScale

            // 1. Outermost soft diffuse ambient aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlowColor.copy(alpha = 0.35f),
                        secondaryGlowColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius * 2.1f
                ),
                radius = coreRadius * 2.1f,
                center = center
            )

            // 2. Animated waveform sound ripples when listening or speaking
            if (sessionState == SessionState.LISTENING || sessionState == SessionState.SPEAKING) {
                val waveCount = 12
                for (i in 0 until waveCount) {
                    val angleDeg = (i * (360f / waveCount) + rotation * 0.5f)
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val barHeight = (baseRadius * 0.25f) + (amplitude * baseRadius * 0.8f * ((i % 3 + 1) / 3f))
                    val startDist = coreRadius * 1.05f
                    val endDist = startDist + barHeight

                    val p1 = Offset(
                        (center.x + cos(angleRad) * startDist).toFloat(),
                        (center.y + sin(angleRad) * startDist).toFloat()
                    )
                    val p2 = Offset(
                        (center.x + cos(angleRad) * endDist).toFloat(),
                        (center.y + sin(angleRad) * endDist).toFloat()
                    )

                    drawLine(
                        color = if (i % 2 == 0) primaryGlowColor.copy(alpha = 0.8f) else secondaryGlowColor.copy(alpha = 0.8f),
                        start = p1,
                        end = p2,
                        strokeWidth = 4f
                    )
                }
            }

            // 3. Orbital thin neon rings
            drawCircle(
                color = secondaryGlowColor.copy(alpha = 0.45f),
                radius = coreRadius * 1.32f,
                center = center,
                style = Stroke(width = 2.5f)
            )

            drawCircle(
                color = primaryGlowColor.copy(alpha = 0.65f),
                radius = coreRadius * 1.15f,
                center = center,
                style = Stroke(width = 3.5f)
            )

            // 4. Inner Radiant Core with gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        primaryGlowColor,
                        secondaryGlowColor.copy(alpha = 0.85f),
                        Color(0xFF0F172A)
                    ),
                    center = center - Offset(coreRadius * 0.2f, coreRadius * 0.2f),
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            // 5. Specular highlight for liquid crystal / orb depth
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = coreRadius * 0.22f,
                center = center - Offset(coreRadius * 0.35f, coreRadius * 0.35f)
            )
        }
    }
}
