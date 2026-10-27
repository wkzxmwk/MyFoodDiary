package com.wkzxmwk.fooddiary.ui.common

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ImageCropDialog(
    source: Uri,
    loadPreview: suspend (Uri) -> Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val previewResult by produceState<Result<Bitmap>?>(null, source) {
        value = runCatching { loadPreview(source) }
    }
    val bitmap = previewResult?.getOrNull()
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(source) { mutableFloatStateOf(1f) }
    var translation by remember(source) { mutableStateOf(Offset.Zero) }

    DisposableEffect(bitmap) {
        onDispose { bitmap?.takeUnless(Bitmap::isRecycled)?.recycle() }
    }

    fun clampedOffset(candidate: Offset, targetScale: Float): Offset {
        val image = bitmap ?: return Offset.Zero
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return Offset.Zero
        val baseScale = min(
            viewportSize.width.toFloat() / image.width,
            viewportSize.height.toFloat() / image.height,
        )
        val cropFrameSize = min(image.width * baseScale, image.height * baseScale)
        val renderedWidth = image.width * baseScale * targetScale
        val renderedHeight = image.height * baseScale * targetScale
        val maxX = ((renderedWidth - cropFrameSize) / 2f).coerceAtLeast(0f)
        val maxY = ((renderedHeight - cropFrameSize) / 2f).coerceAtLeast(0f)
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun createCrop(): Bitmap? {
        val image = bitmap ?: return null
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return null
        val baseScale = min(
            viewportSize.width.toFloat() / image.width,
            viewportSize.height.toFloat() / image.height,
        )
        val cropFrameSize = min(image.width * baseScale, image.height * baseScale)
        val actualScale = baseScale * scale
        val renderedWidth = image.width * actualScale
        val renderedHeight = image.height * actualScale
        val cropLeft = ((renderedWidth - cropFrameSize) / 2f - translation.x) / actualScale
        val cropTop = ((renderedHeight - cropFrameSize) / 2f - translation.y) / actualScale
        val cropSize = (cropFrameSize / actualScale).roundToInt()
            .coerceAtLeast(1)
            .coerceAtMost(minOf(image.width, image.height))
        val left = cropLeft.roundToInt().coerceIn(0, image.width - cropSize)
        val top = cropTop.roundToInt().coerceIn(0, image.height - cropSize)
        return Bitmap.createBitmap(image, left, top, cropSize, cropSize)
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("选择缩略图区域", style = MaterialTheme.typography.titleLarge)
                Text(
                    "完整原图会按原始尺寸保存。双指缩放或拖动图片，方框中的区域仅用于列表和餐食卡片的缩略图。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clipToBounds()
                        .background(Color.Black)
                        .onSizeChanged { viewportSize = it },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        previewResult == null -> CircularProgressIndicator()
                        bitmap == null -> Text(
                            previewResult?.exceptionOrNull()?.message ?: "无法读取图片",
                            color = Color.White,
                            modifier = Modifier.padding(16.dp),
                        )
                        else -> {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "待裁剪照片",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = translation.x,
                                        translationY = translation.y,
                                    )
                                    .pointerInput(bitmap, viewportSize) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                                            scale = newScale
                                            translation = clampedOffset(translation + pan, newScale)
                                        }
                                    },
                            )
                            Canvas(Modifier.fillMaxSize()) {
                                val lineColor = Color.White.copy(alpha = 0.72f)
                                val fitScale = min(size.width / bitmap.width, size.height / bitmap.height)
                                val frameSize = min(bitmap.width * fitScale, bitmap.height * fitScale)
                                val frameLeft = (size.width - frameSize) / 2f
                                val frameTop = (size.height - frameSize) / 2f
                                val dimColor = Color.Black.copy(alpha = 0.52f)
                                drawRect(dimColor, Offset.Zero, Size(size.width, frameTop))
                                drawRect(dimColor, Offset(0f, frameTop + frameSize), Size(size.width, size.height - frameTop - frameSize))
                                drawRect(dimColor, Offset(0f, frameTop), Size(frameLeft, frameSize))
                                drawRect(dimColor, Offset(frameLeft + frameSize, frameTop), Size(size.width - frameLeft - frameSize, frameSize))
                                drawRect(
                                    lineColor,
                                    topLeft = Offset(frameLeft, frameTop),
                                    size = Size(frameSize, frameSize),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                                )
                                drawLine(lineColor, Offset(frameLeft + frameSize / 3f, frameTop), Offset(frameLeft + frameSize / 3f, frameTop + frameSize), 1.dp.toPx())
                                drawLine(lineColor, Offset(frameLeft + frameSize * 2f / 3f, frameTop), Offset(frameLeft + frameSize * 2f / 3f, frameTop + frameSize), 1.dp.toPx())
                                drawLine(lineColor, Offset(frameLeft, frameTop + frameSize / 3f), Offset(frameLeft + frameSize, frameTop + frameSize / 3f), 1.dp.toPx())
                                drawLine(lineColor, Offset(frameLeft, frameTop + frameSize * 2f / 3f), Offset(frameLeft + frameSize, frameTop + frameSize * 2f / 3f), 1.dp.toPx())
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            scale = 1f
                            translation = Offset.Zero
                        },
                        enabled = bitmap != null,
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Text("重置")
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onCancel) { Text("取消") }
                        Button(
                            onClick = { createCrop()?.let(onConfirm) },
                            enabled = bitmap != null && viewportSize != IntSize.Zero,
                        ) { Text("保存原图") }
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomableLocalImage(
    path: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }

    fun updateScale(target: Float) {
        scale = target.coerceIn(1f, 5f)
        val maxX = viewportSize.width * (scale - 1f) / 2f
        val maxY = viewportSize.height * (scale - 1f) / 2f
        offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color.Black)
            .onSizeChanged { viewportSize = it }
            .pointerInput(path, viewportSize) {
                detectTransformGestures { _, pan, zoom, _ ->
                    updateScale(scale * zoom)
                    val maxX = viewportSize.width * (scale - 1f) / 2f
                    val maxY = viewportSize.height * (scale - 1f) / 2f
                    offset = Offset(
                        (offset.x + pan.x).coerceIn(-maxX, maxX),
                        (offset.y + pan.y).coerceIn(-maxY, maxY),
                    )
                }
            },
    ) {
        LocalFileImage(
            path = path,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            ),
            contentScale = ContentScale.Fit,
            maxEdge = 2048,
        )
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.55f)) {
                IconButton(onClick = { updateScale(scale / 1.5f) }, enabled = scale > 1f) {
                    Icon(Icons.Default.ZoomOut, "缩小", tint = Color.White)
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.55f)) {
                IconButton(onClick = { updateScale(scale * 1.5f) }, enabled = scale < 5f) {
                    Icon(Icons.Default.ZoomIn, "放大", tint = Color.White)
                }
            }
        }
    }
}
