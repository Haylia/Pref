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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.preferans.scorer.R
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

/**
 * Preferans scoresheet rendered as a polygon "board":
 *
 *  - For 3 players: an equilateral triangle with the player at each vertex.
 *  - For 4 players: a diamond (rotated square) with players at top/right/bottom/left.
 *
 * Each player owns a kite-shaped sector: from the polygon centre, out to the
 * midpoints of their two adjacent edges, and out to their vertex.
 *
 *  - The pulja "fills" radially outward from the centre, proportional to bullets/target.
 *  - The gora appears as a thicker red band hugging the outer edges of the sector,
 *    thickness proportional to mountain points.
 *  - Sector boundaries (radial lines to edge midpoints) and the outer polygon are
 *    drawn as crisp lines.
 */
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
            .heightIn(max = 400.dp),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val sideDp = if (maxWidth < maxHeight) maxWidth else maxHeight
        val sidePx = with(density) { sideDp.toPx() }
        val center = Offset(sidePx / 2, sidePx / 2)

        // Outer-polygon circumradius. Reserve room around the polygon for labels.
        val rOuter = sidePx * 0.38f
        val labelScale = 1.22f // place labels outside the polygon along the radial direction

        // Polygon vertices: -90° = top.
        val verts = remember(n, sidePx) {
            List(n) { i ->
                val a = (-90f + i * 360f / n) * (PI.toFloat() / 180f)
                Offset(center.x + rOuter * cos(a), center.y + rOuter * sin(a))
            }
        }
        // Midpoint of the polygon edge between vertex i and vertex i+1.
        val midpoints = remember(verts) {
            List(n) { i ->
                val a = verts[i]
                val b = verts[(i + 1) % n]
                Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            }
        }

        Canvas(
            modifier = Modifier
                .size(sideDp)
                .pointerInput(seats, sidePx) {
                    detectTapGestures(
                        onTap = { offset ->
                            sectorForOffset(offset, sidePx, n, rOuter)
                                ?.let { onSeatTap(seats[it]) }
                        },
                        onLongPress = { offset ->
                            sectorForOffset(offset, sidePx, n, rOuter)
                                ?.let { onSeatLongPress(seats[it]) }
                        },
                    )
                },
        ) {
            // Per-player kite sector.
            for (i in 0 until n) {
                val seat = seats[i]
                val score = game.sheet.scores[seat]
                val color = PlayerPalette[i % PlayerPalette.size]
                val vi = verts[i]
                val mLeft = midpoints[(i + n - 1) % n]
                val mRight = midpoints[i]

                // Background kite (light tint of the player colour).
                val sectorPath = Path().apply {
                    moveTo(center.x, center.y)
                    lineTo(mLeft.x, mLeft.y)
                    lineTo(vi.x, vi.y)
                    lineTo(mRight.x, mRight.y)
                    close()
                }
                drawPath(sectorPath, color = color.copy(alpha = 0.14f))

                // Pulja: scale the kite radially outward by bullet/target.
                val bullets = score?.bullet ?: 0
                val fillFrac = (bullets.toFloat() / target).coerceIn(0f, 1f)
                if (fillFrac > 0f) {
                    val pml = lerpOffset(center, mLeft, fillFrac)
                    val pvi = lerpOffset(center, vi, fillFrac)
                    val pmr = lerpOffset(center, mRight, fillFrac)
                    val fillPath = Path().apply {
                        moveTo(center.x, center.y)
                        lineTo(pml.x, pml.y)
                        lineTo(pvi.x, pvi.y)
                        lineTo(pmr.x, pmr.y)
                        close()
                    }
                    drawPath(fillPath, color = color)
                }

                // Gora: red band along the outer perimeter of the kite, thickness
                // proportional to mountain. Capped at half the radius so it never
                // visually swallows the pulja.
                val mountain = score?.mountain ?: 0
                if (mountain > 0) {
                    val mountFrac = (mountain.toFloat() / maxMountain).coerceIn(0f, 1f)
                    val innerFrac = (1f - mountFrac * 0.5f).coerceIn(0.5f, 1f)
                    val iml = lerpOffset(center, mLeft, innerFrac)
                    val ivi = lerpOffset(center, vi, innerFrac)
                    val imr = lerpOffset(center, mRight, innerFrac)
                    val goraPath = Path().apply {
                        moveTo(mLeft.x, mLeft.y)
                        lineTo(vi.x, vi.y)
                        lineTo(mRight.x, mRight.y)
                        lineTo(imr.x, imr.y)
                        lineTo(ivi.x, ivi.y)
                        lineTo(iml.x, iml.y)
                        close()
                    }
                    drawPath(goraPath, color = GoraColor.copy(alpha = 0.88f))
                }

                // Radial divider lines (centre → edge midpoints).
                drawLine(
                    color = Color.Gray.copy(alpha = 0.55f),
                    start = center,
                    end = mRight,
                    strokeWidth = 1.5f,
                )
            }

            // Outer polygon outline drawn last so it sits on top.
            val outerPath = Path().apply {
                moveTo(verts[0].x, verts[0].y)
                for (i in 1 until n) lineTo(verts[i].x, verts[i].y)
                close()
            }
            drawPath(
                outerPath,
                color = Color.DarkGray,
                style = Stroke(width = 3f),
            )

            // Faint diagonals for 4-player so opposite-pair whist badges have
            // a visual anchor across the diamond.
            if (n == 4) {
                drawLine(
                    color = Color.Gray.copy(alpha = 0.25f),
                    start = verts[0],
                    end = verts[2],
                    strokeWidth = 1f,
                )
                drawLine(
                    color = Color.Gray.copy(alpha = 0.25f),
                    start = verts[1],
                    end = verts[3],
                    strokeWidth = 1f,
                )
            }
        }

        // Player labels positioned just outside each vertex.
        verts.forEachIndexed { i, v ->
            val seat = seats[i]
            val score = game.sheet.scores[seat]
            val color = PlayerPalette[i % PlayerPalette.size]
            val xDp = with(density) { ((v.x - center.x) * labelScale).toDp() }
            val yDp = with(density) { ((v.y - center.y) * labelScale).toDp() }

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
                        stringResource(R.string.visual_pulja_fmt, score?.bullet ?: 0, target),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if ((score?.mountain ?: 0) > 0) {
                        Text(
                            stringResource(R.string.visual_gora_fmt, score?.mountain ?: 0),
                            style = MaterialTheme.typography.bodySmall,
                            color = GoraColor,
                        )
                    }
                    val w = score?.totalWhist() ?: 0
                    if (w > 0) {
                        Text(
                            stringResource(R.string.visual_whist_fmt, w),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        // Pairwise whist badges.
        // For every pair (i, j) with i < j, place a small badge on or near the
        // line connecting their vertices. Adjacent pairs sit just outside the
        // polygon edge between them; opposite pairs (4-player diagonals) sit
        // inside the polygon, offset from the centre so the two diagonals don't
        // overlap.
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val seatI = seats[i]
                val seatJ = seats[j]
                val whistIJ = game.sheet.scores[seatI]?.whistAgainst?.get(seatJ) ?: 0
                val whistJI = game.sheet.scores[seatJ]?.whistAgainst?.get(seatI) ?: 0
                // Always render the badge so the board layout is visible even
                // before any whist has been recorded.

                val adjacent = (j == i + 1) || (i == 0 && j == n - 1)
                val (px, py) = if (adjacent) {
                    // Midpoint of the polygon edge between V_i and V_j.
                    val midIdx = if (i == 0 && j == n - 1) (n - 1) else i
                    val m = midpoints[midIdx]
                    val outwardScale = 1.22f
                    val mx = center.x + (m.x - center.x) * outwardScale
                    val my = center.y + (m.y - center.y) * outwardScale
                    (mx - center.x) to (my - center.y)
                } else {
                    // Diagonal pair (4-player only). Centre with a small offset
                    // perpendicular to the diagonal so the two diagonals don't
                    // overlap each other or the centre of the chart.
                    val dx = verts[j].x - verts[i].x
                    val dy = verts[j].y - verts[i].y
                    val len = sqrt(dx * dx + dy * dy)
                    // Unit perpendicular to the diagonal.
                    val perpX = -dy / len
                    val perpY = dx / len
                    val offset = sidePx * 0.05f
                    (perpX * offset) to (perpY * offset)
                }

                val xDp = with(density) { px.toDp() }
                val yDp = with(density) { py.toDp() }
                val colorI = PlayerPalette[i % PlayerPalette.size]
                val colorJ = PlayerPalette[j % PlayerPalette.size]

                Box(
                    modifier = Modifier
                        .offset(x = xDp, y = yDp)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(8.dp),
                        )
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            whistIJ.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorI,
                        )
                        Text(
                            " | ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            whistJI.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorJ,
                        )
                    }
                }
            }
        }
    }
}

private fun lerpOffset(a: Offset, b: Offset, frac: Float): Offset =
    Offset(a.x + (b.x - a.x) * frac, a.y + (b.y - a.y) * frac)

/**
 * Hit-test: returns the sector index for a tap, or null if outside the polygon's
 * circumradius. Each player owns the angular slice [θ - π/N, θ + π/N] centred on
 * their vertex.
 */
private fun sectorForOffset(
    offset: Offset,
    sidePx: Float,
    n: Int,
    rOuter: Float,
): Int? {
    val cx = sidePx / 2f
    val cy = sidePx / 2f
    val dx = offset.x - cx
    val dy = offset.y - cy
    val dist = sqrt(dx * dx + dy * dy)
    // Be generous on the outer bound so taps near vertices still register.
    if (dist > rOuter * 1.1f) return null
    val degFromRight = atan2(dy, dx) * 180f / PI.toFloat()
    val degFromTop = (degFromRight + 90f + 360f) % 360f
    val sweep = 360f / n
    return ((degFromTop + sweep / 2f) % 360f / sweep).toInt() % n
}
