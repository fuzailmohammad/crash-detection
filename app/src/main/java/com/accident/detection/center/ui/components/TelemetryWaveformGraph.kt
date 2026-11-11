package com.accident.detection.center.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accident.detection.center.ui.theme.AlertRed
import com.accident.detection.center.ui.theme.DarkBorder
import com.accident.detection.center.ui.theme.DarkCard
import com.accident.detection.center.ui.theme.EmeraldGreen
import com.accident.detection.center.ui.theme.InfoCyan
import com.accident.detection.center.ui.theme.TextMuted
import com.accident.detection.center.ui.theme.WarningAmber

data class WaveformPoint(
    val ax: Float,
    val ay: Float,
    val az: Float,
    val svmG: Float
)

/**
 * Real-time hardware oscilloscope waveform graph.
 * Renders 3-axis linear acceleration and resultant G-force waveforms with dynamic scaling.
 */
@Composable
fun TelemetryWaveformGraph(
    modifier: Modifier = Modifier,
    points: List<WaveformPoint>,
    maxGScale: Float = 6.0f,
    showLegend: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        if (showLegend) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REAL-TIME IMU OSCILLOSCOPE (G-FORCE WAVEFORM)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextMuted,
                    modifier = Modifier.weight(1f)
                )

                LegendItem(label = "SVM G", color = AlertRed)
                Spacer(modifier = Modifier.width(8.dp))
                LegendItem(label = "X", color = EmeraldGreen)
                Spacer(modifier = Modifier.width(6.dp))
                LegendItem(label = "Y", color = InfoCyan)
                Spacer(modifier = Modifier.width(6.dp))
                LegendItem(label = "Z", color = WarningAmber)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val width = size.width
                val height = size.height

                // 1. Draw Grid Lines (0G baseline and threshold markers)
                val zeroY = height * 0.70f // Zero G baseline
                val twoGY = zeroY - (2.0f / maxGScale) * (zeroY)
                val fourGY = zeroY - (4.0f / maxGScale) * (zeroY)

                // Grid 0G
                drawLine(
                    color = DarkBorder,
                    start = Offset(0f, zeroY),
                    end = Offset(width, zeroY),
                    strokeWidth = 1.dp.toPx()
                )
                // Grid 2G
                drawLine(
                    color = DarkBorder.copy(alpha = 0.5f),
                    start = Offset(0f, twoGY),
                    end = Offset(width, twoGY),
                    strokeWidth = 1.dp.toPx()
                )
                // Grid 4G (Critical Impact Threshold)
                drawLine(
                    color = AlertRed.copy(alpha = 0.35f),
                    start = Offset(0f, fourGY),
                    end = Offset(width, fourGY),
                    strokeWidth = 1.5.dp.toPx()
                )

                if (points.size < 2) return@Canvas

                val stepX = width / (points.size - 1).coerceAtLeast(1)

                val pathSvm = Path()
                val pathX = Path()
                val pathY = Path()
                val pathZ = Path()

                points.forEachIndexed { i, pt ->
                    val x = i * stepX
                    val ySvm = zeroY - (pt.svmG / maxGScale) * zeroY
                    val yAx = zeroY - (pt.ax / (maxGScale * 9.8f)) * zeroY
                    val yAy = zeroY - (pt.ay / (maxGScale * 9.8f)) * zeroY
                    val yAz = zeroY - (pt.az / (maxGScale * 9.8f)) * zeroY

                    if (i == 0) {
                        pathSvm.moveTo(x, ySvm.coerceIn(0f, height))
                        pathX.moveTo(x, yAx.coerceIn(0f, height))
                        pathY.moveTo(x, yAy.coerceIn(0f, height))
                        pathZ.moveTo(x, yAz.coerceIn(0f, height))
                    } else {
                        pathSvm.lineTo(x, ySvm.coerceIn(0f, height))
                        pathX.lineTo(x, yAx.coerceIn(0f, height))
                        pathY.lineTo(x, yAy.coerceIn(0f, height))
                        pathZ.lineTo(x, yAz.coerceIn(0f, height))
                    }
                }

                // Draw X, Y, Z axes lines
                drawPath(pathX, EmeraldGreen.copy(alpha = 0.7f), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
                drawPath(pathY, InfoCyan.copy(alpha = 0.7f), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
                drawPath(pathZ, WarningAmber.copy(alpha = 0.7f), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))

                // Draw Resultant SVM line with thick prominent red stroke
                drawPath(pathSvm, AlertRed, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = color, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
    }
}

