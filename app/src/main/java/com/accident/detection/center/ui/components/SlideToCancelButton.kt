package com.accident.detection.center.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accident.detection.center.ui.theme.AlertRedDark
import com.accident.detection.center.ui.theme.EmeraldGreen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Production anti-pocket-tap cancellation slider.
 *
 * Safety Specification:
 * - Requires a deliberate continuous horizontal drag across >85% of track width.
 * - Random touches, pocket friction, or sub-85% releases snap back to 0 instantaneously.
 * - Visual chevron pulse encourages intentional left-to-right gesture.
 */
@Composable
fun SlideToCancelButton(
    modifier: Modifier = Modifier,
    text: String = "SLIDE TO CANCEL (I AM SAFE)",
    onCancelled: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var isConfirmed by remember { mutableStateOf(false) }

    val chevronTransition = rememberInfiniteTransition(label = "chevron_pulse")
    val chevronAlpha by chevronTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1E2024),
                        Color(0xFF2D3139),
                        Color(0xFF1E2024)
                    )
                )
            )
            .border(
                width = 2.dp,
                color = if (isConfirmed) EmeraldGreen else Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(36.dp)
            )
            .padding(6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val thumbSizePx = with(density) { 60.dp.toPx() }
        val maxDragDistancePx = constraints.maxWidth.toFloat() - thumbSizePx - with(density) { 12.dp.toPx() }
        val offsetX = remember { Animatable(0f) }

        // Track Background Label with Pulsing Chevrons
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 48.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    ),
                    color = Color.White.copy(alpha = 0.9f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = chevronAlpha),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Draggable Thumb Knob
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    if (isConfirmed) EmeraldGreen else Color.White
                )
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !isConfirmed,
                    state = rememberDraggableState { delta ->
                        coroutineScope.launch {
                            val target = (offsetX.value + delta).coerceIn(0f, maxDragDistancePx)
                            offsetX.snapTo(target)
                        }
                    },
                    onDragStopped = {
                        coroutineScope.launch {
                            // Strict 85% drag threshold requirement
                            if (offsetX.value >= maxDragDistancePx * 0.85f) {
                                offsetX.animateTo(maxDragDistancePx, tween(120))
                                isConfirmed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCancelled()
                            } else {
                                // Sub-threshold touch: Snap back instantly to prevent pocket release
                                offsetX.animateTo(0f, tween(200))
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isConfirmed) Icons.Default.Done else Icons.Default.Shield,
                contentDescription = "Cancel thumb",
                tint = if (isConfirmed) Color.Black else AlertRedDark,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

