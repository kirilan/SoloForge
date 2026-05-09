package com.kbul.spicycrab.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

data class ChartPoint(val timeMs: Long, val value: Double)

@Composable
fun WeightChart(
    points: List<ChartPoint>,
    unitLabel: String,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
) {
    if (points.size < 2) {
        Box(modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Text(
                if (points.isEmpty()) "Log a weight to see your trend." else "Log one more entry to plot a trend.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val sorted = points.sortedBy { it.timeMs }
    val minT = sorted.first().timeMs.toDouble()
    val maxT = sorted.last().timeMs.toDouble()
    val tSpan = (maxT - minT).coerceAtLeast(1.0)
    val minV = sorted.minOf { it.value }
    val maxV = sorted.maxOf { it.value }
    val pad = (maxV - minV) * 0.1
    val yMin = (minV - pad).coerceAtLeast(0.0)
    val yMax = maxV + pad
    val ySpan = (yMax - yMin).coerceAtLeast(0.1)

    Box(modifier.fillMaxWidth().height(200.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val coords = sorted.map { p ->
                val x = ((p.timeMs - minT) / tSpan * w).toFloat()
                val y = (h - (p.value - yMin) / ySpan * h).toFloat()
                Offset(x, y)
            }

            val fillPath = Path().apply {
                moveTo(coords.first().x, h)
                coords.forEach { lineTo(it.x, it.y) }
                lineTo(coords.last().x, h)
                close()
            }
            drawPath(fillPath, color = fillColor)

            val linePath = Path().apply {
                moveTo(coords.first().x, coords.first().y)
                coords.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                linePath,
                color = lineColor,
                style = Stroke(width = 6f, cap = StrokeCap.Round),
            )

            coords.forEach { drawCircle(lineColor, radius = 6f, center = it) }
        }
        Text(
            "${"%.1f".format(yMax)} $unitLabel",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            "${"%.1f".format(yMin)} $unitLabel",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}
