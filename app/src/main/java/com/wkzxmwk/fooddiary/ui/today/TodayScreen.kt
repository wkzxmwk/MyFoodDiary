package com.wkzxmwk.fooddiary.ui.today

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.wkzxmwk.fooddiary.domain.WeightCalculator
import com.wkzxmwk.fooddiary.domain.model.DailyRecord
import com.wkzxmwk.fooddiary.domain.model.MealType
import com.wkzxmwk.fooddiary.domain.model.MealPhoto
import com.wkzxmwk.fooddiary.ui.common.LoadingContent
import com.wkzxmwk.fooddiary.ui.common.SectionCard
import com.wkzxmwk.fooddiary.ui.common.LocalFileImage
import com.wkzxmwk.fooddiary.ui.common.ImageCropDialog
import com.wkzxmwk.fooddiary.ui.common.ZoomableLocalImage
import com.wkzxmwk.fooddiary.ui.theme.EveningBlue
import com.wkzxmwk.fooddiary.ui.theme.HealthGreenLight
import com.wkzxmwk.fooddiary.ui.theme.MorningOrange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    onSelectDate: (LocalDate) -> Unit,
    onSaveWeight: (Boolean, Double?) -> Unit,
    onLoadCropPreview: suspend (Uri) -> Bitmap,
    onAddCroppedPhoto: (MealType, Uri, Bitmap) -> Unit,
    onDiscardPendingImage: (Uri) -> Unit,
    onCreateCameraUri: () -> Uri,
    onCreateDailyShareImage: suspend () -> Uri,
    onDeletePhoto: (MealPhoto) -> Unit,
) {
    var weightEditor by remember { mutableStateOf<Boolean?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedMeal by remember { mutableStateOf<MealType?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var viewedPhoto by remember { mutableStateOf<MealPhoto?>(null) }
    var cropQueue by remember { mutableStateOf<List<Pair<MealType, Uri>>>(emptyList()) }
    var isSharing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        selectedMeal?.let { meal ->
            cropQueue = cropQueue + uris.distinct().map { uri -> meal to uri }
        }
        selectedMeal = null
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val meal = selectedMeal
        val uri = cameraUri
        if (success && meal != null && uri != null) cropQueue = cropQueue + (meal to uri)
        else if (uri != null) onDiscardPendingImage(uri)
        selectedMeal = null
        cameraUri = null
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize()) {
        if (state.isLoading) {
            LoadingContent()
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(10.dp))
                TodayHeader(
                    date = state.date,
                    pick = { showDatePicker = true },
                    sharing = isSharing,
                    share = {
                        if (!isSharing) {
                            coroutineScope.launch {
                                isSharing = true
                                try {
                                    val uri = onCreateDailyShareImage()
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "${state.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))}饮食记录",
                                        )
                                        clipData = ClipData.newUri(context.contentResolver, "今日饮食记录", uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "分享今日饮食记录"))
                                } catch (throwable: Throwable) {
                                    snackbar.showSnackbar(throwable.message ?: "生成分享图片失败")
                                } finally {
                                    isSharing = false
                                }
                            }
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                MealType.entries.forEach { meal ->
                    MealCard(
                        meal = meal,
                        record = state.record,
                        onAddPhoto = { selectedMeal = meal },
                        onViewPhoto = { viewedPhoto = it },
                    )
                    Spacer(Modifier.height(7.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeightCard(true, state.record?.morningWeight, Modifier.weight(1f)) { weightEditor = true }
                    WeightCard(false, state.record?.eveningWeight, Modifier.weight(1f)) { weightEditor = false }
                }
                Spacer(Modifier.height(8.dp))
                AverageCard(state.record?.averageWeight)
                Spacer(Modifier.height(12.dp))
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    selectedMeal?.let { meal ->
        AlertDialog(
            onDismissRequest = { selectedMeal = null },
            title = { Text("添加${meal.displayName}照片") },
            text = {
                Column {
                    TextButton(
                        onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("从相册选择（可多选）") }
                    HorizontalDivider()
                    TextButton(
                        onClick = {
                            val uri = onCreateCameraUri()
                            cameraUri = uri
                            camera.launch(uri)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("拍照") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { selectedMeal = null }) { Text("取消") } },
        )
    }

    viewedPhoto?.let { photo ->
        PhotoViewerDialog(
            photo = photo,
            onDismiss = { viewedPhoto = null },
            onDelete = { onDeletePhoto(photo); viewedPhoto = null },
        )
    }

    cropQueue.firstOrNull()?.let { (meal, uri) ->
        ImageCropDialog(
            source = uri,
            loadPreview = onLoadCropPreview,
            onConfirm = { bitmap ->
                onAddCroppedPhoto(meal, uri, bitmap)
                cropQueue = cropQueue.drop(1)
            },
            onCancel = {
                onDiscardPendingImage(uri)
                cropQueue = cropQueue.drop(1)
            },
        )
    }

    weightEditor?.let { isMorning ->
        WeightDialog(
            isMorning = isMorning,
            initialWeight = if (isMorning) state.record?.morningWeight else state.record?.eveningWeight,
            saving = state.isSaving,
            onDismiss = { weightEditor = null },
            onSave = { value -> onSaveWeight(isMorning, value); weightEditor = null },
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onSelectDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) { DatePicker(pickerState, title = { Text("选择记录日期", Modifier.padding(16.dp)) }) }
    }
}

@Composable
private fun TodayHeader(
    date: LocalDate,
    pick: () -> Unit,
    sharing: Boolean,
    share: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "🌿 今日记录",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                "记录饮食，关注健康每一天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        OutlinedButton(
            onClick = pick,
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = HealthGreenLight),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 7.dp),
        ) {
            Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp))
            Text(
                date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)),
                Modifier.padding(start = 4.dp),
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = share, enabled = !sharing, modifier = Modifier.size(40.dp)) {
            if (sharing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Share, contentDescription = "分享今日饮食记录", modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun MealCard(
    meal: MealType,
    record: DailyRecord?,
    onAddPhoto: () -> Unit,
    onViewPhoto: (MealPhoto) -> Unit,
) {
    val photos = record?.photos?.filter { it.mealType == meal }.orEmpty()
    SectionCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.size(width = 82.dp, height = 58.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalDining, null, Modifier.size(18.dp), tint = mealColor(meal))
                    Text(meal.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                }
                Text(if (photos.isEmpty()) "尚未添加" else "已记录 ${photos.size} 张", style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                photos.forEach { photo ->
                    OutlinedCard(onClick = { onViewPhoto(photo) }, modifier = Modifier.size(56.dp)) {
                        LocalFileImage(photo.localPath, "${meal.displayName}照片")
                    }
                }
                OutlinedCard(
                    onClick = onAddPhoto,
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                    colors = CardDefaults.outlinedCardColors(containerColor = HealthGreenLight.copy(alpha = 0.5f)),
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.Add, "添加${meal.displayName}照片", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("添加", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoViewerDialog(photo: MealPhoto, onDismiss: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(photo.mealType.displayName) },
        text = {
            Column {
                ZoomableLocalImage(photo.localPath, "${photo.mealType.displayName}大图", Modifier.fillMaxWidth().aspectRatio(1f))
                Text("创建时间：${java.time.Instant.ofEpochMilli(photo.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}", Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = { TextButton(onClick = { confirmDelete = true }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除照片？") },
            text = { Text("本地图片文件也会被永久删除。") },
            confirmButton = { Button(onClick = onDelete) { Text("确认删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun WeightCard(isMorning: Boolean, weight: Double?, modifier: Modifier, edit: () -> Unit) {
    val color = if (isMorning) MorningOrange else EveningBlue
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(0.75.dp, Color(0xFF333333)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MonitorWeight, null, Modifier.size(18.dp), tint = color)
                Text(if (isMorning) "早晨体重" else "晚上体重", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
            }
            Text(
                weight?.let { String.format(Locale.US, "%.2f kg", it).trimEnd('0').trimEnd('.') } ?: "暂无",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 5.dp),
            )
            OutlinedButton(
                onClick = edit,
                modifier = Modifier.fillMaxWidth().height(32.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Icon(Icons.Default.Edit, null, Modifier.size(14.dp))
                Text("记录体重", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AverageCard(average: Double?) {
    SectionCard(Modifier.fillMaxWidth(), HealthGreenLight) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("当日平均", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text("早晚体重都存在时自动计算", style = MaterialTheme.typography.bodySmall)
            }
            Text(average?.let { String.format(Locale.US, "%.1f kg", it) } ?: "暂无", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun WeightDialog(
    isMorning: Boolean,
    initialWeight: Double?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (Double?) -> Unit,
) {
    var text by remember(initialWeight) { mutableStateOf(initialWeight?.toString().orEmpty()) }
    val parsed = text.toDoubleOrNull()
    val decimalValid = Regex("^\\d{0,3}([.]\\d{0,2})?$").matches(text)
    val valid = text.isBlank() || (decimalValid && parsed != null && WeightCalculator.isValid(parsed))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isMorning) "记录早晨体重" else "记录晚上体重") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 6) text = it },
                    label = { Text("体重（kg）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = !valid,
                    supportingText = { Text(if (valid) "可输入一位或两位小数；留空可删除" else "请输入20–300 kg，最多两位小数") },
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(if (text.isBlank()) null else parsed) }, enabled = valid && !saving) { Text(if (saving) "保存中" else "保存") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } },
    )
}

private fun mealColor(meal: MealType): Color = when (meal) {
    MealType.BREAKFAST -> MorningOrange
    MealType.LUNCH -> MorningOrange
    MealType.DINNER -> EveningBlue
    MealType.SNACK -> Color(0xFF7CB342)
}
