package com.astra.pdf.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch

@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    maxScale: Float = 4.0f,
    minScale: Float = 1.0f,
    onScroll: ((Float) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val scaleAnim = remember { Animatable(1.0f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }

    LaunchedEffect(boxSize) {
        if (boxSize.width > 0 && boxSize.height > 0) {
            val curScale = scaleAnim.value
            if (curScale <= 1.05f) {
                offsetXAnim.snapTo(0f)
                offsetYAnim.snapTo(0f)
            } else {
                val maxOffsetX = (boxSize.width * (curScale - 1f)) / 2f
                val maxOffsetY = (boxSize.height * (curScale - 1f)) / 2f
                offsetXAnim.snapTo(offsetXAnim.value.coerceIn(-maxOffsetX, maxOffsetX))
                offsetYAnim.snapTo(offsetYAnim.value.coerceIn(-maxOffsetY, maxOffsetY))
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            .pointerInput(boxSize) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        val currentScale = scaleAnim.value
                        coroutineScope.launch {
                            val animSpec = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)
                            if (currentScale > 1.15f) {
                                launch { scaleAnim.animateTo(1.0f, animSpec) }
                                launch { offsetXAnim.animateTo(0f, animSpec) }
                                launch { offsetYAnim.animateTo(0f, animSpec) }
                            } else {
                                val targetScale = 2.5f
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                val center = Offset(width / 2f, height / 2f)

                                val maxOffsetX = (width * (targetScale - 1f)) / 2f
                                val maxOffsetY = (height * (targetScale - 1f)) / 2f

                                val targetOffsetX = ((center.x - tapOffset.x) * (targetScale - 1f))
                                    .coerceIn(-maxOffsetX, maxOffsetX)
                                val targetOffsetY = ((center.y - tapOffset.y) * (targetScale - 1f))
                                    .coerceIn(-maxOffsetY, maxOffsetY)

                                launch { scaleAnim.animateTo(targetScale, animSpec) }
                                launch { offsetXAnim.animateTo(targetOffsetX, animSpec) }
                                launch { offsetYAnim.animateTo(targetOffsetY, animSpec) }
                            }
                        }
                    }
                )
            }
            .pointerInput(boxSize) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)

                    do {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.filter { it.pressed }
                        val pointerCount = pressedPointers.size

                        if (pointerCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)

                            event.changes.forEach { it.consume() }

                            val currentScale = scaleAnim.value
                            val newScale = (currentScale * zoomChange).coerceIn(minScale, maxScale)

                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            val center = Offset(width / 2f, height / 2f)
                            val pivot = centroid - center

                            val maxOffsetX = (width * (newScale - 1f)) / 2f
                            val maxOffsetY = (height * (newScale - 1f)) / 2f

                            val newOffsetX = if (currentScale > 0f) {
                                ((offsetXAnim.value + pivot.x) * (newScale / currentScale) - pivot.x + panChange.x)
                                    .coerceIn(-maxOffsetX, maxOffsetX)
                            } else 0f

                            val newOffsetY = if (currentScale > 0f) {
                                ((offsetYAnim.value + pivot.y) * (newScale / currentScale) - pivot.y + panChange.y)
                                    .coerceIn(-maxOffsetY, maxOffsetY)
                            } else 0f

                            coroutineScope.launch {
                                scaleAnim.snapTo(newScale)
                                offsetXAnim.snapTo(newOffsetX)
                                offsetYAnim.snapTo(newOffsetY)
                            }
                        } else if (pointerCount == 1 && scaleAnim.value > 1.05f) {
                            val change = pressedPointers[0]
                            val pan = change.positionChange()
                            if (pan != Offset.Zero) {
                                change.consume()

                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                val curScale = scaleAnim.value

                                val maxOffsetX = (width * (curScale - 1f)) / 2f
                                val maxOffsetY = (height * (curScale - 1f)) / 2f

                                val targetX = offsetXAnim.value + pan.x
                                val clampedX = targetX.coerceIn(-maxOffsetX, maxOffsetX)

                                val targetY = offsetYAnim.value + pan.y
                                val clampedY = targetY.coerceIn(-maxOffsetY, maxOffsetY)
                                val overflowY = targetY - clampedY

                                coroutineScope.launch {
                                    offsetXAnim.snapTo(clampedX)
                                    offsetYAnim.snapTo(clampedY)
                                }

                                if (overflowY != 0f) {
                                    onScroll?.invoke(overflowY)
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (scaleAnim.value < 1.08f) {
                        coroutineScope.launch {
                            val animSpec = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
                            launch { scaleAnim.animateTo(1.0f, animSpec) }
                            launch { offsetXAnim.animateTo(0f, animSpec) }
                            launch { offsetYAnim.animateTo(0f, animSpec) }
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                translationX = offsetXAnim.value
                translationY = offsetYAnim.value
            }
    ) {
        content()
    }
}
