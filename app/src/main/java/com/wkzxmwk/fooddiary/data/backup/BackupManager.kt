package com.wkzxmwk.fooddiary.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.wkzxmwk.fooddiary.data.local.FoodDiaryDatabase
import com.wkzxmwk.fooddiary.data.local.entity.DailyRecordEntity
import com.wkzxmwk.fooddiary.data.local.entity.MealPhotoEntity
import com.wkzxmwk.fooddiary.domain.WeightCalculator
import com.wkzxmwk.fooddiary.domain.BodyMetricsCalculator
import com.wkzxmwk.fooddiary.domain.model.Gender
import com.wkzxmwk.fooddiary.domain.model.MealType
import com.wkzxmwk.fooddiary.domain.model.ReminderType
import com.wkzxmwk.fooddiary.domain.repository.FoodDiaryRepository
import com.wkzxmwk.fooddiary.domain.repository.SettingsRepository
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BackupManager(
    private val context: Context,
    private val database: FoodDiaryDatabase,
    private val repository: FoodDiaryRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val records = repository.observeAll().first()
        val settings = settingsRepository.settings.first()
        val root = JSONObject().put("version", 1).put("exportedAt", System.currentTimeMillis())
            .put(
                "settings",
                JSONObject()
                    .putNullable("targetWeight", settings.targetWeight)
                    .putNullable("heightCm", settings.heightCm)
                    .put("gender", settings.gender?.name ?: JSONObject.NULL)
                    .put("remindersEnabled", settings.remindersEnabled)
                    .put("breakfastReminderTime", settings.breakfastReminderTime.toString())
                    .put("lunchReminderTime", settings.lunchReminderTime.toString())
                    .put("dinnerReminderTime", settings.dinnerReminderTime.toString())
                    .put("imageQuality", settings.imageQuality)
                    .put("weightUnit", "kg"),
            )
        val recordArray = JSONArray()
        records.forEach { record ->
            val photos = JSONArray()
            record.photos.forEach { photo ->
                val thumbnail = thumbnailFileFor(File(photo.localPath))
                photos.put(JSONObject().put("id", photo.id).put("mealType", photo.mealType.name)
                    .put("file", "images/${photo.id}.jpg")
                    .put("thumbnail", if (thumbnail.isFile) "thumbnails/${photo.id}.jpg" else JSONObject.NULL)
                    .put("createdAt", photo.createdAt))
            }
            recordArray.put(JSONObject().put("date", record.date.toString()).putNullable("morningWeight", record.morningWeight)
                .putNullable("eveningWeight", record.eveningWeight).put("note", record.note).put("createdAt", record.createdAt).put("updatedAt", record.updatedAt).put("photos", photos))
        }
        root.put("records", recordArray)
        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            ZipOutputStream(output.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("backup.json")); zip.write(root.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
                records.flatMap { it.photos }.forEach { photo ->
                    val file = File(photo.localPath)
                    require(file.isFile) { "照片文件不存在，无法完成备份：${photo.id}" }
                    zip.putNextEntry(ZipEntry("images/${photo.id}.jpg")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    val thumbnail = thumbnailFileFor(file)
                    if (thumbnail.isFile) {
                        zip.putNextEntry(ZipEntry("thumbnails/${photo.id}.jpg")); thumbnail.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                }
            }
        } ?: error("无法创建备份文件")
    }

    suspend fun importFrom(uri: Uri) = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "backup-import-${UUID.randomUUID()}").apply { mkdirs() }
        var generation: File? = null
        var databaseCommitted = false
        try {
            var manifest: String? = null
            var totalBytes = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        require(!entry.isDirectory && (entry.name == "backup.json" || Regex("(images|thumbnails)/[A-Za-z0-9-]+[.]jpg").matches(entry.name))) { "备份包含不安全的文件路径" }
                        val bytes = zip.readBytesLimited(55L * 1024 * 1024)
                        totalBytes += bytes.size
                        require(totalBytes <= 500L * 1024 * 1024) { "备份文件过大" }
                        if (entry.name == "backup.json") manifest = bytes.toString(Charsets.UTF_8)
                        else File(temp, entry.name.replace('/', '_')).writeBytes(bytes)
                        zip.closeEntry(); entry = zip.nextEntry
                    }
                }
            } ?: error("无法读取备份文件")
            val root = JSONObject(manifest ?: error("备份缺少 backup.json"))
            require(root.getInt("version") == 1) { "不支持的备份版本" }
            val newGeneration = File(context.filesDir, "meal_photos/restore-${UUID.randomUUID()}").apply { mkdirs() }
            generation = newGeneration
            val records = mutableListOf<DailyRecordEntity>(); val photos = mutableListOf<MealPhotoEntity>()
            val array = root.getJSONArray("records")
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i); val date = LocalDate.parse(item.getString("date")); val epoch = date.toEpochDay()
                val morning = item.optNullableDouble("morningWeight"); val evening = item.optNullableDouble("eveningWeight")
                require(morning == null || WeightCalculator.isValid(morning)); require(evening == null || WeightCalculator.isValid(evening))
                records += DailyRecordEntity(epoch, morning, evening, item.optString("note", ""), item.getLong("createdAt"), item.getLong("updatedAt"))
                val photoArray = item.getJSONArray("photos")
                for (p in 0 until photoArray.length()) {
                    val photo = photoArray.getJSONObject(p); val id = photo.getString("id"); require(Regex("[A-Za-z0-9-]+").matches(id))
                    val source = File(temp, "images_$id.jpg"); require(source.isFile) { "备份缺少图片：$id" }
                    val destination = File(newGeneration, "$id.jpg"); source.copyTo(destination)
                    val thumbnailSource = File(temp, "thumbnails_$id.jpg")
                    if (thumbnailSource.isFile) thumbnailSource.copyTo(thumbnailFileFor(destination))
                    photos += MealPhotoEntity(id, epoch, MealType.valueOf(photo.getString("mealType")), destination.absolutePath, photo.getLong("createdAt"))
                }
            }
            database.withTransaction {
                database.dailyRecordDao().deleteAll()
                database.dailyRecordDao().upsertAll(records)
                database.mealPhotoDao().upsertAll(photos)
            }
            databaseCommitted = true
            val settings = root.getJSONObject("settings")
            settingsRepository.setTargetWeight(settings.optNullableDouble("targetWeight"))
            val heightCm = settings.optNullableDouble("heightCm")
            require(heightCm == null || BodyMetricsCalculator.isValidHeight(heightCm)) {
                "备份中的身高数据无效"
            }
            settingsRepository.setHeightCm(heightCm)
            val gender = if (settings.isNull("gender") || !settings.has("gender")) {
                null
            } else {
                runCatching { Gender.valueOf(settings.getString("gender")) }
                    .getOrElse { error("备份中的性别数据无效") }
            }
            settingsRepository.setGender(gender)
            settingsRepository.setRemindersEnabled(settings.optBoolean("remindersEnabled", true))
            ReminderType.entries.forEach { type ->
                val key = when (type) {
                    ReminderType.BREAKFAST -> "breakfastReminderTime"
                    ReminderType.LUNCH -> "lunchReminderTime"
                    ReminderType.DINNER -> "dinnerReminderTime"
                }
                val defaultTime = type.defaultTime
                val time = settings.optString(key, defaultTime.toString())
                    .let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
                    ?: error("备份中的提醒时间无效")
                settingsRepository.setReminderTime(type, time)
            }
            settingsRepository.setImageQuality(settings.optInt("imageQuality", 80).coerceIn(50, 95))
            File(context.filesDir, "meal_photos").listFiles()?.filter { it != newGeneration }?.forEach { it.deleteRecursively() }
        } finally {
            temp.deleteRecursively()
            if (!databaseCommitted) generation?.deleteRecursively()
        }
    }

    private fun ZipInputStream.readBytesLimited(limit: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(16 * 1024); var total = 0L
        while (true) { val count = read(buffer); if (count < 0) break; total += count; require(total <= limit) { "备份中的单个文件过大" }; output.write(buffer, 0, count) }
        return output.toByteArray()
    }
}

private fun thumbnailFileFor(originalFile: File): File =
    File(originalFile.parentFile, "${originalFile.nameWithoutExtension}.thumb.jpg")

private fun JSONObject.putNullable(key: String, value: Double?): JSONObject = put(key, value ?: JSONObject.NULL)
private fun JSONObject.optNullableDouble(key: String): Double? = if (isNull(key) || !has(key)) null else getDouble(key)
