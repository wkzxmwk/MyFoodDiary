package com.wkzxmwk.fooddiary.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.wkzxmwk.fooddiary.domain.model.MealPhoto
import com.wkzxmwk.fooddiary.domain.model.MealType
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalImageManager(private val context: Context) {
    fun createCameraUri(): Uri {
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(directory, "capture-${UUID.randomUUID()}.jpg").apply { createNewFile() }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    suspend fun importImage(source: Uri, date: LocalDate, quality: Int): String = withContext(Dispatchers.IO) {
        require(quality in 50..95)
        val stagingDirectory = File(context.cacheDir, "image-import").apply { mkdirs() }
        val stagingFile = File(stagingDirectory, "${UUID.randomUUID()}.source")
        val directory = File(context.filesDir, "meal_photos/$date").apply { mkdirs() }
        val finalFile = File(directory, "${UUID.randomUUID()}.jpg")
        val tempFile = File(directory, ".${finalFile.name}.tmp")
        var bitmap: Bitmap? = null
        try {
            copySourceToStaging(source, stagingFile)

            val orientation = runCatching { ExifInterface(stagingFile).rotationDegrees }.getOrDefault(0)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(stagingFile.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("图片格式不受支持")

            val decoded = BitmapFactory.decodeFile(
                stagingFile.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, 2048)
                },
            ) ?: error("图片解码失败")
            bitmap = decoded

            val scaled = scaleDown(decoded, 2048)
            if (scaled !== decoded) {
                decoded.recycle()
                bitmap = scaled
            }
            if (orientation != 0) {
                val rotated = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    Matrix().apply { postRotate(orientation.toFloat()) },
                    true,
                )
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }

            FileOutputStream(tempFile).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "图片压缩失败" }
            }
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            check(finalFile.isFile && finalFile.length() > 0L) { "图片保存失败" }
            finalFile.absolutePath
        } catch (throwable: Throwable) {
            tempFile.delete()
            finalFile.delete()
            throw throwable
        } finally {
            bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            stagingFile.delete()
            deleteCameraCacheIfNeeded(source)
        }
    }

    /** 为裁剪界面读取已纠正 EXIF 方向的预览图，尚不写入长期存储。 */
    suspend fun loadCropPreview(source: Uri, maxEdge: Int = 2048): Bitmap = withContext(Dispatchers.IO) {
        val stagingDirectory = File(context.cacheDir, "image-crop-preview").apply { mkdirs() }
        val stagingFile = File(stagingDirectory, "${UUID.randomUUID()}.source")
        var bitmap: Bitmap? = null
        try {
            copySourceToStaging(source, stagingFile)
            val orientation = runCatching { ExifInterface(stagingFile).rotationDegrees }.getOrDefault(0)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(stagingFile.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("图片格式不受支持")
            bitmap = BitmapFactory.decodeFile(
                stagingFile.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
                },
            ) ?: error("图片解码失败")
            val scaled = scaleDown(bitmap, maxEdge)
            if (scaled !== bitmap) {
                bitmap.recycle()
                bitmap = scaled
            }
            if (orientation != 0) {
                val rotated = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    Matrix().apply { postRotate(orientation.toFloat()) },
                    true,
                )
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }
            bitmap
        } catch (throwable: Throwable) {
            bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            throw throwable
        } finally {
            stagingFile.delete()
        }
    }

    /**
     * 主文件保留来源图片的完整像素尺寸，仅按设置的 JPEG 质量压缩；
     * 用户选择的方形区域只生成缩略图，不会裁剪主文件。
     */
    suspend fun importCroppedImage(
        source: Uri,
        croppedBitmap: Bitmap,
        date: LocalDate,
        quality: Int,
    ): String = withContext(Dispatchers.IO) {
        require(quality in 50..95)
        require(croppedBitmap.width > 0 && croppedBitmap.height > 0) { "裁剪结果无效" }
        val stagingDirectory = File(context.cacheDir, "image-import").apply { mkdirs() }
        val stagingFile = File(stagingDirectory, "${UUID.randomUUID()}.source")
        val directory = File(context.filesDir, "meal_photos/$date").apply { mkdirs() }
        val finalFile = File(directory, "${UUID.randomUUID()}.jpg")
        val tempFile = File(directory, ".${finalFile.name}.tmp")
        val thumbnailFile = thumbnailFileFor(finalFile)
        val tempThumbnailFile = File(directory, ".${thumbnailFile.name}.tmp")
        var originalBitmap: Bitmap? = null
        var thumbnailBitmap: Bitmap? = null
        try {
            copySourceToStaging(source, stagingFile)
            val orientation = runCatching { ExifInterface(stagingFile).rotationDegrees }.getOrDefault(0)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(stagingFile.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("图片格式不受支持")

            originalBitmap = BitmapFactory.decodeFile(stagingFile.absolutePath)
                ?: error("图片解码失败")
            if (orientation != 0) {
                val rotated = Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.width,
                    originalBitmap.height,
                    Matrix().apply { postRotate(orientation.toFloat()) },
                    true,
                )
                if (rotated !== originalBitmap) originalBitmap.recycle()
                originalBitmap = rotated
            }

            FileOutputStream(tempFile).use { output ->
                check(originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "原图压缩失败" }
            }

            thumbnailBitmap = scaleDown(croppedBitmap, 512)
            FileOutputStream(tempThumbnailFile).use { output ->
                check(thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "缩略图压缩失败" }
            }

            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            if (!tempThumbnailFile.renameTo(thumbnailFile)) {
                tempThumbnailFile.copyTo(thumbnailFile, overwrite = true)
                tempThumbnailFile.delete()
            }
            check(finalFile.isFile && finalFile.length() > 0L) { "原图保存失败" }
            check(thumbnailFile.isFile && thumbnailFile.length() > 0L) { "缩略图保存失败" }
            finalFile.absolutePath
        } catch (throwable: Throwable) {
            tempFile.delete()
            tempThumbnailFile.delete()
            finalFile.delete()
            thumbnailFile.delete()
            throw throwable
        } finally {
            originalBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            if (thumbnailBitmap !== croppedBitmap) {
                thumbnailBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            }
            croppedBitmap.takeUnless(Bitmap::isRecycled)?.recycle()
            stagingFile.delete()
            deleteCameraCacheIfNeeded(source)
        }
    }

    suspend fun discardPendingImage(source: Uri) = withContext(Dispatchers.IO) {
        deleteCameraCacheIfNeeded(source)
    }

    /** 生成仅用于系统分享的缓存拼图，不修改或复制数据库记录。 */
    suspend fun createDailyShareImage(date: LocalDate, photos: List<MealPhoto>): Uri = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "shared_images").apply { mkdirs() }
        directory.listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > 24L * 60L * 60L * 1000L) file.delete()
        }
        val outputFile = File(directory, "food-diary-$date-${System.currentTimeMillis()}.jpg")
        val tempFile = File(directory, ".${outputFile.name}.tmp")
        val bitmap = Bitmap.createBitmap(1080, 1240, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            drawShareHeader(canvas, date)

            val meals = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK)
            val outer = 48f
            val gap = 24f
            val cardTop = 190f
            val cardWidth = (bitmap.width - outer * 2f - gap) / 2f
            val cardHeight = 460f
            meals.forEachIndexed { index, meal ->
                val column = index % 2
                val row = index / 2
                val left = outer + column * (cardWidth + gap)
                val top = cardTop + row * (cardHeight + gap)
                drawShareMealCard(
                    canvas = canvas,
                    bounds = RectF(left, top, left + cardWidth, top + cardHeight),
                    meal = meal,
                    photos = photos.filter { it.mealType == meal },
                )
            }

            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 110, 102)
                textSize = 24f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("FoodDiary · 记录饮食，关注健康每一天", bitmap.width / 2f, 1192f, footerPaint)

            FileOutputStream(tempFile).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) { "分享图片生成失败" }
            }
            if (!tempFile.renameTo(outputFile)) {
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
            }
            check(outputFile.isFile && outputFile.length() > 0L) { "分享图片保存失败" }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
        } catch (throwable: Throwable) {
            tempFile.delete()
            outputFile.delete()
            throw throwable
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        val originalDeleted = !file.exists() || file.delete()
        val thumbnail = thumbnailFileFor(file)
        val thumbnailDeleted = !thumbnail.exists() || thumbnail.delete()
        originalDeleted && thumbnailDeleted
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        File(context.filesDir, "meal_photos").deleteRecursively()
        File(context.cacheDir, "camera").deleteRecursively()
    }

    private fun deleteCameraCacheIfNeeded(uri: Uri) {
        if (uri.authority == "${context.packageName}.fileprovider") {
            File(context.cacheDir, "camera").listFiles()?.forEach(File::delete)
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (max(width / sample, height / sample) > maxEdge * 2) sample *= 2
        return sample
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / largest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }

    private fun thumbnailFileFor(originalFile: File): File =
        File(originalFile.parentFile, "${originalFile.nameWithoutExtension}.thumb.jpg")

    private fun drawShareHeader(canvas: Canvas, date: LocalDate) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 132, 54)
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 61, 56)
            textSize = 34f
        }
        canvas.drawText("今日饮食记录", 48f, 78f, titlePaint)
        canvas.drawText(
            "${date.year}年${date.monthValue}月${date.dayOfMonth}日",
            48f,
            137f,
            datePaint,
        )
    }

    private fun drawShareMealCard(
        canvas: Canvas,
        bounds: RectF,
        meal: MealType,
        photos: List<MealPhoto>,
    ) {
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(252, 253, 252)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(212, 224, 214)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(bounds, 28f, 28f, cardPaint)
        canvas.drawRoundRect(bounds, 28f, 28f, borderPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 39, 35)
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(91, 101, 93)
            textSize = 25f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(meal.displayName, bounds.left + 22f, bounds.top + 52f, titlePaint)
        canvas.drawText(
            if (photos.isEmpty()) "尚未添加" else "共 ${photos.size} 张",
            bounds.right - 22f,
            bounds.top + 50f,
            countPaint,
        )

        val imageBounds = RectF(
            bounds.left + 20f,
            bounds.top + 76f,
            bounds.right - 20f,
            bounds.bottom - 20f,
        )
        if (photos.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(239, 245, 240) }
            canvas.drawRoundRect(imageBounds, 18f, 18f, emptyPaint)
            val emptyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(105, 122, 108)
                textSize = 30f
                textAlign = Paint.Align.CENTER
            }
            val baseline = imageBounds.centerY() - (emptyTextPaint.ascent() + emptyTextPaint.descent()) / 2f
            canvas.drawText("暂无照片", imageBounds.centerX(), baseline, emptyTextPaint)
            return
        }

        val visiblePhotos = photos.take(4)
        val cells = shareImageCells(imageBounds, visiblePhotos.size)
        visiblePhotos.forEachIndexed { index, photo ->
            val original = File(photo.localPath)
            val displayFile = thumbnailFileFor(original).takeIf(File::isFile) ?: original
            val image = decodeSampledBitmap(displayFile, 640)
            if (image != null) {
                drawCenterCrop(canvas, image, cells[index])
                image.recycle()
            } else {
                val unavailablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(232, 232, 232) }
                canvas.drawRoundRect(cells[index], 12f, 12f, unavailablePaint)
            }
        }
        if (photos.size > 4) {
            val lastCell = cells.last()
            val overlay = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(145, 0, 0, 0) }
            canvas.drawRoundRect(lastCell, 12f, 12f, overlay)
            val morePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 42f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val baseline = lastCell.centerY() - (morePaint.ascent() + morePaint.descent()) / 2f
            canvas.drawText("+${photos.size - 4}", lastCell.centerX(), baseline, morePaint)
        }
    }

    private fun shareImageCells(bounds: RectF, count: Int): List<RectF> {
        val gap = 8f
        if (count == 1) return listOf(RectF(bounds))
        if (count == 2) {
            val width = (bounds.width() - gap) / 2f
            return listOf(
                RectF(bounds.left, bounds.top, bounds.left + width, bounds.bottom),
                RectF(bounds.left + width + gap, bounds.top, bounds.right, bounds.bottom),
            )
        }
        val width = (bounds.width() - gap) / 2f
        val height = (bounds.height() - gap) / 2f
        return List(count.coerceAtMost(4)) { index ->
            val column = index % 2
            val row = index / 2
            val left = bounds.left + column * (width + gap)
            val top = bounds.top + row * (height + gap)
            RectF(left, top, left + width, top + height)
        }
    }

    private fun decodeSampledBitmap(file: File, maxEdge: Int): Bitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            },
        )
    }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, destination: RectF) {
        val sourceRatio = bitmap.width.toFloat() / bitmap.height
        val destinationRatio = destination.width() / destination.height()
        val source = if (sourceRatio > destinationRatio) {
            val width = (bitmap.height * destinationRatio).toInt().coerceAtLeast(1)
            val left = (bitmap.width - width) / 2
            Rect(left, 0, left + width, bitmap.height)
        } else {
            val height = (bitmap.width / destinationRatio).toInt().coerceAtLeast(1)
            val top = (bitmap.height - height) / 2
            Rect(0, top, bitmap.width, top + height)
        }
        canvas.save()
        canvas.clipRect(destination)
        canvas.drawBitmap(bitmap, source, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    private fun copyWithLimit(input: java.io.InputStream, output: java.io.OutputStream, limit: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "图片文件过大" }
            output.write(buffer, 0, count)
        }
    }

    /** 部分厂商的图片提供器只实现了其中一种文件访问方式，因此按官方接口逐级回退。 */
    private fun copySourceToStaging(source: Uri, destination: File) {
        val resolver = context.contentResolver
        var lastFailure: Throwable? = null

        fun attempt(open: () -> java.io.InputStream?): Boolean {
            destination.delete()
            return try {
                val input = open() ?: return false
                input.use { stream ->
                    FileOutputStream(destination).use { output ->
                        copyWithLimit(stream, output, 50L * 1024 * 1024)
                    }
                }
                destination.isFile && destination.length() > 0L
            } catch (throwable: Throwable) {
                lastFailure = throwable
                false
            }
        }

        if (attempt { resolver.openInputStream(source) }) return
        if (attempt { resolver.openAssetFileDescriptor(source, "r")?.createInputStream() }) return
        if (attempt {
                resolver.openFileDescriptor(source, "r")?.let {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(it)
                }
            }
        ) return
        if (attempt { resolver.openTypedAssetFileDescriptor(source, "image/*", null)?.createInputStream() }) return

        destination.delete()
        throw IllegalStateException("无法读取所选图片，请确认图片仍存在并重试", lastFailure)
    }
}
