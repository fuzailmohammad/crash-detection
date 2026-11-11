package com.accident.detection.center.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.accident.detection.center.ui.theme.SlideTrackColor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Anti-pocket-tap swipe slider for emergency alarm cancellation.
 * Requires an intentional deliberate horizontal drag across >85% of track width.
 */
@Composable
fun SlideToCancelSlider(
    modifier: Modifier = Modifier,
    text: String = "SLIDE TO CANCEL (I AM SAFE)",
    onCancelled: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var isConfirmed by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(SlideTrackColor)
            .padding(6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val thumbSizePx = with(density) { 56.dp.toPx() }
        val maxDragDistancePx = constraints.maxWidth.toFloat() - thumbSizePx - with(density) { 12.dp.toPx() }
        val offsetX = remember { Animatable(0f) }

        // Background Track Text
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        // Draggable Thumb
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isConfirmed) EmeraldGreen else Color.White)
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
                            if (offsetX.value >= maxDragDistancePx * 0.85f) {
                                // Successful deliberate cancel
                                offsetX.animateTo(maxDragDistancePx, tween(150))
                                isConfirmed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCancelled()
                            } else {
                                // Snap back
                                offsetX.animateTo(0f, tween(250))
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isConfirmed) Icons.Default.Done else Icons.Default.ChevronRight,
                contentDescription = "Slide thumb",
                tint = if (isConfirmed) Color.Black else AlertRedDark,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

