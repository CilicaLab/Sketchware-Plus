package sketchware.plus.ai

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

enum class OrbState {
    WORKING, SEARCHING, WEAVING, THINKING, BUILDING, ERROR, SUCCESS
}

object ThinkingOrbHelper {
    @JvmStatic
    fun setStatusContent(view: ComposeView, state: OrbState, statusText: String?) {
        view.setContent {
            ThinkingOrbStatus(state = state, statusText = statusText)
        }
    }
}

@Composable
fun ThinkingOrbStatus(
    modifier: Modifier = Modifier,
    state: OrbState = OrbState.WORKING,
    statusText: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    val effectiveDotColor = when (state) {
        OrbState.WORKING -> Color(0xFF2C9BA8)   // Electric Cyan
        OrbState.SEARCHING -> Color(0xFFFFB300) // Golden Amber
        OrbState.WEAVING -> Color(0xFF7C4DFF)   // Deep Indigo/Violet
        OrbState.THINKING -> Color(0xFFAB47BC)  // Vibrant Purple
        OrbState.BUILDING -> Color(0xFF2196F3)  // Electric Blue
        OrbState.ERROR -> Color(0xFFFF5252)     // Alert Coral Red
        OrbState.SUCCESS -> Color(0xFF4CAF50)   // Emerald Green
    }

    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2.2f

            when (state) {
                OrbState.WORKING -> drawWorkingOrb(time, effectiveDotColor, center, radius)
                OrbState.SEARCHING -> drawSearchingOrb(time, effectiveDotColor, center, radius)
                OrbState.WEAVING -> drawWeavingOrb(time, effectiveDotColor, center, radius)
                OrbState.THINKING -> drawThinkingOrb(time, effectiveDotColor, center, radius)
                OrbState.BUILDING -> drawBuildingOrb(time, effectiveDotColor, center, radius)
                OrbState.ERROR -> drawErrorOrb(time, effectiveDotColor, center, radius)
                OrbState.SUCCESS -> drawSuccessOrb(time, effectiveDotColor, center, radius)
            }
        }

        if (statusText != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                color = effectiveDotColor.copy(alpha = shimmerAlpha),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun DrawScope.drawWorkingOrb(time: Float, color: Color, center: Offset, radius: Float) {
    val dotsPerOrbit = 8
    val orbits = 3
    for (i in 0 until orbits) {
        val tilt = (i * PI / orbits).toFloat()
        for (j in 0 until dotsPerOrbit) {
            val angle = time + (j * 2 * PI / dotsPerOrbit).toFloat()
            
            // 3D rotation math
            val x3d = radius * cos(angle)
            val y3d = radius * sin(angle)
            
            val x2d = x3d
            val y2d = y3d * sin(tilt)
            val z3d = y3d * cos(tilt)
            
            val scale = (z3d / radius + 2) / 2 // 0.5 to 1.5
            val alpha = (z3d / radius + 1) / 2 * 0.8f + 0.2f // 0.2 to 1.0
            
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = 1.2.dp.toPx() * scale,
                center = center + Offset(x2d, y2d)
            )
        }
    }
}

private fun DrawScope.drawSearchingOrb(time: Float, color: Color, center: Offset, radius: Float) {
    val lines = 6
    val dotsPerLine = 10
    for (i in 0 until lines) {
        val lat = (i * PI / (lines - 1) - PI / 2).toFloat()
        val rLat = radius * cos(lat)
        val y = radius * sin(lat)
        
        for (j in 0 until dotsPerLine) {
            val lon = (j * 2 * PI / dotsPerLine + time).toFloat()
            val x = rLat * cos(lon)
            val z = rLat * sin(lon)
            
            // Only draw if sweeping (meridian sweep effect)
            val sweep = (lon % (2 * PI)) / (2 * PI)
            val alpha = if (abs(sweep - 0.5) < 0.2) 1f else 0.2f
            
            val zScale = (z / radius + 2) / 2
            
            drawCircle(
                color = color.copy(alpha = alpha * ((z / radius + 1) / 2 * 0.7f + 0.3f)),
                radius = 1.0.dp.toPx() * zScale,
                center = center + Offset(x, y)
            )
        }
    }
}

private fun DrawScope.drawWeavingOrb(time: Float, color: Color, center: Offset, radius: Float) {
    val bands = 5
    val dotsPerBand = 12
    for (i in 0 until bands) {
        val yOffset = (i.toFloat() / (bands - 1) * 2 - 1) * radius * 0.7f
        val bandTime = time * (1 + i * 0.2f)
        
        for (j in 0 until dotsPerBand) {
            val angle = (j * 2 * PI / dotsPerBand + bandTime).toFloat()
            val x = radius * cos(angle)
            val z = radius * sin(angle)
            
            val scrambleFactor = sin(time * 2 + i) * 5.dp.toPx()
            val finalX = x + scrambleFactor * cos(angle)
            
            val alpha = (z / radius + 1) / 2 * 0.8f + 0.2f
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = 1.2.dp.toPx(),
                center = center + Offset(finalX, yOffset)
            )
        }
    }
}

private fun DrawScope.drawThinkingOrb(time: Float, color: Color, center: Offset, radius: Float) {
    val dots = 16
    for (i in 0 until dots) {
        val yNormalized = (i.toFloat() / (dots - 1)) * 2f - 1f
        val y = yNormalized * radius * 0.8f
        val phase = time + yNormalized * PI.toFloat() * 1.5f
        
        val x1 = radius * 0.8f * cos(phase)
        val z1 = radius * 0.8f * sin(phase)
        val alpha1 = (z1 / radius + 1) / 2 * 0.8f + 0.2f
        
        val x2 = radius * 0.8f * cos(phase + PI.toFloat())
        val z2 = radius * 0.8f * sin(phase + PI.toFloat())
        val alpha2 = (z2 / radius + 1) / 2 * 0.8f + 0.2f
        
        drawCircle(
            color = color.copy(alpha = alpha1),
            radius = 1.2.dp.toPx(),
            center = center + Offset(x1, y)
        )
        drawCircle(
            color = color.copy(alpha = alpha2),
            radius = 1.2.dp.toPx(),
            center = center + Offset(x2, y)
        )
    }
}

private fun DrawScope.drawBuildingOrb(time: Float, color: Color, center: Offset, radius: Float) {
    val pulse = (sin(time * 2) + 1) / 2 // 0.0 to 1.0
    val currentRadius = radius * (0.6f + 0.4f * pulse)
    val grid = 4
    
    for (i in 0 until grid) {
        for (j in 0 until grid) {
            val nx = (i.toFloat() / (grid - 1)) * 2f - 1f
            val ny = (j.toFloat() / (grid - 1)) * 2f - 1f
            val dist = sqrt(nx * nx + ny * ny)
            
            if (dist <= 1.2f) {
                val x = nx * currentRadius
                val y = ny * currentRadius
                val alpha = (1f - (dist / 1.4f)).coerceIn(0.2f, 1f)
                
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = 1.1.dp.toPx(),
                    center = center + Offset(x, y)
                )
            }
        }
    }
}

private fun DrawScope.drawErrorOrb(time: Float, color: Color, center: Offset, radius: Float) {
    val dots = 12
    val jitterMagnitude = 2.dp.toPx()
    for (i in 0 until dots) {
        val angle = i * 2 * PI / dots + time * 3
        val jitter = sin(time * 10 + i * 5) * jitterMagnitude
        val currentRadius = radius * 0.7f + jitter
        
        val x = currentRadius * cos(angle).toFloat()
        val y = currentRadius * sin(angle).toFloat()
        val alpha = if (i % 2 == 0) (sin(time * 6 + i) * 0.4f + 0.6f) else 0.4f
        
        drawCircle(
            color = color.copy(alpha = alpha.coerceIn(0.2f, 1f)),
            radius = 1.3.dp.toPx(),
            center = center + Offset(x, y)
        )
    }
}

private fun DrawScope.drawSuccessOrb(time: Float, color: Color, center: Offset, radius: Float) {
    val petals = 8
    val dotsPerPetal = 3
    val breathe = (sin(time) * 0.15f) + 0.85f
    
    for (i in 0 until petals) {
        val baseAngle = i * 2 * PI / petals
        for (j in 1..dotsPerPetal) {
            val dist = (j.toFloat() / dotsPerPetal) * radius * breathe
            val x = dist * cos(baseAngle).toFloat()
            val y = dist * sin(baseAngle).toFloat()
            val alpha = 1f - (j.toFloat() / (dotsPerPetal + 1)) * 0.5f
            
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = (1.5f - j * 0.2f).dp.toPx().coerceAtLeast(0.8.dp.toPx()),
                center = center + Offset(x, y)
            )
        }
    }
}
