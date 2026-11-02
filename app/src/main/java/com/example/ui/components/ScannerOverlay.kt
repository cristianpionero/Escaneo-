package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * Official Colombia Flag badge with 50% Yellow, 25% Blue, 25% Red proportions.
 */
@Composable
fun ColombiaFlag(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 30.dp, height = 20.dp)
            .clip(RoundedCornerShape(3.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.50f)
                    .background(Color(0xFFFFCD00)) // Amarillo Colombia
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.25f)
                    .background(Color(0xFF003087)) // Azul Colombia
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.25f)
                    .background(Color(0xFFC8102E)) // Rojo Colombia
            )
        }
    }
}

/**
 * Cyan holographic laser scanner overlay with 4 corner brackets, center crosshair,
 * and animated scanning laser line.
 */
@Composable
fun ScannerOverlay(
    modifier: Modifier = Modifier,
    laserColor: Color = Color(0xFF00E5FF),
    bracketColor: Color = Color(0xFF00E5FF)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_progress"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Scan box margins matching the screenshot
        val marginX = width * 0.10f
        val topY = height * 0.16f
        val bottomY = height * 0.88f
        val boxWidth = width - (marginX * 2)
        val boxHeight = bottomY - topY

        val bracketLen = 42.dp.toPx()
        val strokeW = 4.dp.toPx()

        // 1. Top-Left Corner Bracket
        drawLine(
            color = bracketColor,
            start = Offset(marginX, topY),
            end = Offset(marginX + bracketLen, topY),
            strokeWidth = strokeW,
            cap = StrokeCap.Square
        )
        drawLine(
            color = bracketColor,
            start = Offset(marginX, topY),
            end = Offset(marginX, topY + bracketLen),
            strokeWidth = strokeW,
            cap = StrokeCap.Square
        )

        // 2. Top-Right Corner Bracket
        drawLine(
            color = bracketColor,
            start = Offset(width - marginX, topY),
            end = Offset(width - marginX - bracketLen, topY),
            strokeWidth = strokeW,
            cap = StrokeCap.Square
        )
        drawLine(
            color = bracketColor,
            start = Offset(width - marginX, topY),
            end = Offset(width - marginX, topY + bracketLen),
            strokeWidth = strokeW,
            cap = StrokeCap.Square
        )

        // 3. Bottom-Left Corner Bracket
        drawLine(
            color = bracketColor,
            start = Offset(marginX, bottomY),
            end = Offset(marginX + bracketLen, bottomY),
            strokeWidth = strokeW,
            cap = StrokeCap.Square
        )
        drawLine(
            color = bracketColor,
            start = Offset(marginX, bottomY),
            end = Offset(marginX, bottomY - bracketLen),
            strokeWidth = strokeW,
            cap = StrokeCap.Square
        )

        // 4. Bottom-Right Corner Bracket
        drawLine(
            color = bracketColor,
            start = Offset(width - marginX, bottomY),
            end = Offset(width - marginX - bracketLen, bottomY),
            strokeWidth = strokeW,
            cap = StrokeCap.Square
        )
        drawLine(
            color = bracketColor,
            start = Offset(width - marginX, bottomY),
            end = Offset(width - marginX, bottomY - bracketLen),
            strokeWidth = strokeW,
            cap = StrokeCap.Square
        )

        // 5. Center Crosshair (+)
        val centerX = width / 2f
        val centerY = topY + (boxHeight / 2f)
        val crossHalfLen = 9.dp.toPx()
        val crossStroke = 1.8.dp.toPx()

        drawLine(
            color = bracketColor.copy(alpha = 0.85f),
            start = Offset(centerX - crossHalfLen, centerY),
            end = Offset(centerX + crossHalfLen, centerY),
            strokeWidth = crossStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = bracketColor.copy(alpha = 0.85f),
            start = Offset(centerX, centerY - crossHalfLen),
            end = Offset(centerX, centerY + crossHalfLen),
            strokeWidth = crossStroke,
            cap = StrokeCap.Round
        )

        // 6. Laser Scanning Line with Cyan Glow
        val currentLaserY = topY + (boxHeight * laserProgress)
        val glowHeight = 16.dp.toPx()

        // Subtle glow rectangle above and below the laser line
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    laserColor.copy(alpha = 0.25f),
                    laserColor.copy(alpha = 0.0f)
                ),
                startY = currentLaserY - glowHeight,
                endY = currentLaserY + glowHeight
            ),
            topLeft = Offset(marginX, currentLaserY - glowHeight),
            size = Size(boxWidth, glowHeight * 2)
        )

        // Bright laser center line
        drawLine(
            color = Color.White,
            start = Offset(marginX + 2.dp.toPx(), currentLaserY),
            end = Offset(width - marginX - 2.dp.toPx(), currentLaserY),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = laserColor,
            start = Offset(marginX, currentLaserY),
            end = Offset(width - marginX, currentLaserY),
            strokeWidth = 3.8.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
