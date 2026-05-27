package com.preferans.scorer.ui.scoresheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.preferans.scorer.domain.GameState
import com.preferans.scorer.domain.SeatId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val PlayerPalette = listOf(
    Color(0xFF66BB6A), // green
    Color(0xFF42A5F5), // blue
    Color(0xFFFFA726), // orange
    Color(0xFFAB47BC), // purple
)

private val GoraColor = Color(0xFFD32F2F)

@Composable
fun VisualScoresheet(
    game: GameState,
    modifier: Modifier = Modifier,
    onSeatTap: (SeatId) -> Unit = {},
    onSeatLongPress: (SeatId) -> Unit = {},
) {
    val seats = game.config.seats
    val n = seats.size
    val target = game.config.bulletTarget
    val maxMountain = (seats.maxOfOrNull { game.sheet.scores[it]?.mountain ?: 0 } ?: 0)
        .coerceAtLeast(20)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val sideDp = if (maxWidth < maxHeight) maxWidth else maxHeight
        val sidePx = with(density) { sideDp.toPx() }
        val center = Offset(sidePx / 2, sidePx / 2)
        val innerR = sidePx * 0.30f
        val ringStart = innerR + with(density) { 6.dp.toPx() }
        val ringEnd = sidePx * 0.42f
        val labelR = sidePx * 0.47f
        val tapRadius = ringEnd

        Canvas(
            modifier = Modifier
                .size(sideDp)
                .pointerInput(seats, sidePx) {
                    detectTapGestures(
                        onTap = { offset ->
                            sectorForOffset(offset, sidePx, n, tapRadius)
                                ?.let { onSeatTap(seats[it]) }
                        },
                        onLongPress = { offset ->
                            sectorForOffset(offset, sidePx, n, tapRadius)
                                ?.let { onSeatLongPress(seats[it]) }
                        },
                    )
                },
        ) {
            val sweep = 360f / n

            for (i in 0 until n) {
                val seat = seats[i]
                val startAngle = -90f + i * sweep - sweep / 2
                val score = game.sheet.scores[seat]
                val color = PlayerPalette[i % PlayerPalette.size]

                drawArc(
                    color = color.copy(alpha = 0.15f),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset(center.x - innerR, center.y - innerR),
                    size = Size(innerR * 2, innerR * 2),
                )

                val bullets = score?.bullet ?: 0
                val fillFrac = (bullets.toFloat() / target).coerceIn(0f, 1f)
                if (fillFrac > 0f) {
                    val fillR = innerR * fillFrac
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(center.x - fillR, center.y - fillR),
                        size = Size(fillR * 2, fillR * 2),
                    )
                }

                drawArc(
                    color = Color.Gray.copy(alpha = 0.6f),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset(center.x - innerR, center.y - innerR),
                    size = Size(innerR * 2, innerR * 2),
                    style = Stroke(width = 2f),
                )

                val mountain = score?.mountain ?: 0
                if (mountain > 0) {
                    val mountFrac = (mountain.toFloat() / maxMountain).coerceIn(0f, 1f)
                    val thickness = (ringEnd - ringStart) * mountFrac
                    val radiusMid = ringStart + thickness / 2
                    drawArc(
                        color = GoraColor.copy(alpha = 0.85f),
                        startAngle = startAngle + 2f,
                        sweepAngle = sweep - 4f,
                        useCenter = false,
                        style = Stroke(width = thickness),
                        topLeft = Offset(center.x - radiusMid, center.y - radiusMid),
                        size = Size(radiusMid * 2, radiusMid * 2),
                    )
                }
            }
        }

        // Labels overlay — also tappable (in case sector tap misses)
        seats.forEachIndexed { i, seat ->
            val angleDeg = -90f + i * (360f / n)
            val angleRad = angleDeg * (PI.toFloat() / 180f)
            val xPx = labelR * cos(angleRad)
            val yPx = labelR * sin(angleRad)
            val xDp = with(density) { xPx.toDp() }
            val yDp = with(density) { yPx.toDp() }
            val score = game.sheet.scores[seat]
            val color = PlayerPalette[i % PlayerPalette.size]

            Box(
                modifier = Modifier
                    .offset(x = xDp, y = yDp)
                    .clickable { onSeatTap(seat) }
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(10.dp),
                    )
                    .border(
                        BorderStroke(1.5.dp, color),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        game.config.nameOf(seat),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Pulja ${score?.bullet ?: 0}/$target",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if ((score?.mountain ?: 0) > 0) {
                        Text(
                            "Gora ${score?.mountain}",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoraColor,
                        )
                    }
                    val w = score?.totalWhist() ?: 0
                    if (w > 0) {
                        Text(
                            "Whist $w",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Returns the sector index (0..n-1) under a tap offset, or null if outside the
 * interactive area. Sector 0 is centered at the top; sectors rotate clockwise.
 */
private fun sectorForOffset(offset: Offset, sidePx: Float, n: Int, tapRadius: Float): Int? {
    val cx = sidePx / 2f
    val cy = sidePx / 2f
    val dx = offset.x - cx
    val dy = offset.y - cy
    val dist = sqrt(dx * dx + dy * dy)
    if (dist > tapRadius) return null
    // atan2 here returns radians where 0 = right axis, positive going down.
    val degFromRight = atan2(dy, dx) * 180f / PI.toFloat()
    val degFromTop = (degFromRight + 90f + 360f) % 360f
    val sweep = 360f / n
    return ((degFromTop + sweep / 2f) % 360f / sweep).toInt() % n
}
