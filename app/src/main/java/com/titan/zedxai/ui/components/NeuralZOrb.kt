package com.titan.zedxai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.titan.zedxai.ui.theme.LocalCrystalTokens
import com.titan.zedxai.voice.OrbVisualState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun NeuralZOrb(
    orbState: OrbVisualState,
    amplitude: Float = 0f,
    size: Dp = 156.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LocalCrystalTokens.current
    val primary = MaterialTheme.colorScheme.primary
    val cyan = tokens.glowCyan
    val violet = tokens.glowViolet

    val transition = rememberInfiniteTransition(label = "zedx_orb")
    val spinDeg by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (orbState) {
                    OrbVisualState.IDLE -> 11000
                    OrbVisualState.LISTENING -> 4200
                    OrbVisualState.THINKING -> 2200
                    OrbVisualState.SPEAKING -> 3400
                },
                easing = LinearEasing
            )
        ),
        label = "orb_spin"
    )

    val breathe by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (orbState) {
                    OrbVisualState.IDLE -> 2600
                    OrbVisualState.LISTENING -> 900
                    OrbVisualState.THINKING -> 750
                    OrbVisualState.SPEAKING -> 650
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breathe"
    )

    val smoothAmp by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = tween(140),
        label = "orb_amp"
    )

    val boxMod = modifier
        .size(size)
        .semantics {
            contentDescription = "ZEDX Neural Orb state ${orbState.name.lowercase()}"
        }
        .let { m ->
            if (onClick != null) {
                m.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
            } else m
        }

    Box(modifier = boxMod, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseR = (minOf(this.size.width, this.size.height) * 0.34f) * (breathe + smoothAmp * 0.14f)

            // Outer ambient refraction halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cyan.copy(alpha = if (orbState == OrbVisualState.IDLE) 0.24f else 0.42f),
                        violet.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseR * 1.65f
                ),
                radius = baseR * 1.65f,
                center = center
            )

            // Multi-axis orbital crystal rings
            rotate(degrees = spinDeg, pivot = center) {
                drawOval(
                    brush = Brush.sweepGradient(listOf(cyan, violet, primary, cyan), center = center),
                    topLeft = Offset(center.x - baseR * 1.24f, center.y - baseR * 0.88f),
                    size = androidx.compose.ui.geometry.Size(baseR * 2.48f, baseR * 1.76f),
                    style = Stroke(width = 2.6f)
                )
            }
            rotate(degrees = -spinDeg * 1.35f + 55f, pivot = center) {
                drawOval(
                    brush = Brush.sweepGradient(listOf(violet, primary, cyan, violet), center = center),
                    topLeft = Offset(center.x - baseR * 0.92f, center.y - baseR * 1.24f),
                    size = androidx.compose.ui.geometry.Size(baseR * 1.84f, baseR * 2.48f),
                    style = Stroke(width = 2.2f)
                )
            }

            // Orbiting photon nodes
            for (i in 0 until 6) {
                val angle = Math.toRadians((spinDeg * 1.2 + i * 60).toDouble())
                val orbitDist = baseR * (1.14f + 0.08f * sin(angle * 2).toFloat())
                val nx = center.x + (orbitDist * cos(angle)).toFloat()
                val ny = center.y + (orbitDist * sin(angle)).toFloat()
                drawCircle(
                    color = if (i % 2 == 0) cyan else violet,
                    radius = if (orbState == OrbVisualState.THINKING) 5.5f else 4f,
                    center = Offset(nx, ny)
                )
            }

            // Inner frosted crystal core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.96f),
                        primary.copy(alpha = 0.35f),
                        violet.copy(alpha = 0.25f)
                    ),
                    center = Offset(center.x - baseR * 0.2f, center.y - baseR * 0.25f),
                    radius = baseR * 1.05f
                ),
                radius = baseR * 0.86f,
                center = center
            )

            drawCircle(
                brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), cyan.copy(alpha = 0.7f))),
                radius = baseR * 0.86f,
                center = center,
                style = Stroke(width = 3f)
            )

            // Geometric Z Crest inside the Orb
            val zHalf = baseR * 0.38f
            val zPath = Path().apply {
                moveTo(center.x - zHalf, center.y - zHalf * 0.85f)
                lineTo(center.x + zHalf, center.y - zHalf * 0.85f)
                lineTo(center.x - zHalf, center.y + zHalf * 0.85f)
                lineTo(center.x + zHalf, center.y + zHalf * 0.85f)
            }
            drawPath(
                path = zPath,
                brush = Brush.linearGradient(listOf(primary, cyan, violet)),
                style = Stroke(width = 5.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
