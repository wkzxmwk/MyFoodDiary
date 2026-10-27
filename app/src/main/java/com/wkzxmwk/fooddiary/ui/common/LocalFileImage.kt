package com.wkzxmwk.fooddiary.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LocalFileImage(
    path: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxEdge: Int = 512,
    preferThumbnail: Boolean = maxEdge <= 512,
) {
    val displayPath = if (preferThumbnail) thumbnailPath(path).takeIf { File(it).isFile } ?: path else path
    val bitmap by produceState<android.graphics.Bitmap?>(null, displayPath, maxEdge) {
        value = withContext(Dispatchers.IO) { decodeSampled(displayPath, maxEdge) }
    }
    if (bitmap == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("图片不可用", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Image(bitmap!!.asImageBitmap(), contentDescription, modifier.fillMaxSize(), contentScale = contentScale)
    }
}

private fun thumbnailPath(originalPath: String): String {
    val original = File(originalPath)
    return File(original.parentFile, "${original.nameWithoutExtension}.thumb.jpg").absolutePath
}

private fun decodeSampled(path: String, maxEdge: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxEdge * 2) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}
