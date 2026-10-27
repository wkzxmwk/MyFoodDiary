package com.wkzxmwk.fooddiary.ui.detail

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.wkzxmwk.fooddiary.domain.WeightCalculator
import com.wkzxmwk.fooddiary.domain.model.MealPhoto
import com.wkzxmwk.fooddiary.domain.model.MealType
import com.wkzxmwk.fooddiary.ui.common.EmptyContent
import com.wkzxmwk.fooddiary.ui.common.LoadingContent
import com.wkzxmwk.fooddiary.ui.common.LocalFileImage
import com.wkzxmwk.fooddiary.ui.common.ImageCropDialog
import com.wkzxmwk.fooddiary.ui.common.ZoomableLocalImage
import com.wkzxmwk.fooddiary.ui.common.SectionCard
import com.wkzxmwk.fooddiary.ui.theme.HealthGreenLight
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RecordDetailScreen(
    state: RecordDetailUiState,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    updateWeights: (Double?, Double?) -> Unit,
    updateNote: (String) -> Unit,
    loadCropPreview: suspend (Uri) -> Bitmap,
    addCroppedPhoto: (MealType, Uri, Bitmap) -> Unit,
    discardPendingImage: (Uri) -> Unit,
    createCameraUri: () -> Uri,
    deletePhoto: (MealPhoto) -> Unit,
    deleteRecord: () -> Unit,
) {
    var editWeights by remember { mutableStateOf(false) }
    var editNote by remember { mutableStateOf(false) }
    var confirmDeleteRecord by remember { mutableStateOf(false) }
    var selectedMeal by remember { mutableStateOf<MealType?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPhoto by remember { mutableStateOf<MealPhoto?>(null) }
    var cropQueue by remember { mutableStateOf<List<Pair<MealType, Uri>>>(emptyList()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        selectedMeal?.let { meal -> cropQueue = cropQueue + uris.distinct().map { uri -> meal to uri } }
        selectedMeal = null
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val meal = selectedMeal; val uri = cameraUri
        if (ok && meal != null && uri != null) cropQueue = cropQueue + (meal to uri)
        else if (uri != null) discardPendingImage(uri)
        selectedMeal = null; cameraUri = null
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    when {
        state.isLoading -> LoadingContent()
        state.record == null -> EmptyContent("记录不存在", "该日期的记录可能已经删除")
        else -> {
            val record = state.record
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.errorMessage?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(8.dp)) }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                        Column {
                            Text("记录详情", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            Text(record.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)))
                        }
                    }
                }
                items(MealType.entries) { meal ->
                    SectionCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(meal.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { selectedMeal = meal }) { Icon(Icons.Default.Add, null); Text("添加") }
                            }
                            val photos = record.photos.filter { it.mealType == meal }
                            if (photos.isEmpty()) Text("尚未添加照片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                photos.take(5).forEach { photo ->
                                    OutlinedCard(onClick = { selectedPhoto = photo }, modifier = Modifier.size(64.dp)) { LocalFileImage(photo.localPath, "${meal.displayName}照片") }
                                }
                            }
                        }
                    }
                }
                item {
                    SectionCard(Modifier.fillMaxWidth(), HealthGreenLight) {
                        Column(Modifier.padding(16.dp)) {
                            Row {
                                Text("体重", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { editWeights = true }) { Icon(Icons.Default.Edit, null); Text("编辑") }
                            }
                            Text("早晨：${formatWeight(record.morningWeight)}")
                            Text("晚上：${formatWeight(record.eveningWeight)}")
                            Text("日平均：${formatWeight(record.averageWeight)}", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item {
                    SectionCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row {
                                Text("备注", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { editNote = true }) { Icon(Icons.Default.Edit, null); Text("编辑") }
                            }
                            Text(record.note.ifBlank { "暂无备注" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { confirmDeleteRecord = true }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Delete, null); Text("删除当天记录", Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (editWeights) WeightEditor(record.morningWeight, record.eveningWeight, { editWeights = false }) { m, e -> updateWeights(m, e); editWeights = false }
            if (editNote) NoteEditor(record.note, { editNote = false }) { updateNote(it); editNote = false }
        }
    }

    selectedMeal?.let { meal ->
        AlertDialog(
            onDismissRequest = { selectedMeal = null }, title = { Text("添加${meal.displayName}照片") },
            text = { Column {
                TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth()) { Text("从相册选择") }
                TextButton(onClick = { createCameraUri().also { cameraUri = it; camera.launch(it) } }, Modifier.fillMaxWidth()) { Text("拍照") }
            } }, confirmButton = {}, dismissButton = { TextButton(onClick = { selectedMeal = null }) { Text("取消") } },
        )
    }
    selectedPhoto?.let { photo ->
        AlertDialog(
            onDismissRequest = { selectedPhoto = null }, title = { Text(photo.mealType.displayName) },
            text = { ZoomableLocalImage(photo.localPath, "餐食大图", Modifier.fillMaxWidth().height(320.dp)) },
            confirmButton = { TextButton(onClick = { deletePhoto(photo); selectedPhoto = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { selectedPhoto = null }) { Text("关闭") } },
        )
    }
    cropQueue.firstOrNull()?.let { (meal, uri) ->
        ImageCropDialog(
            source = uri,
            loadPreview = loadCropPreview,
            onConfirm = { bitmap ->
                addCroppedPhoto(meal, uri, bitmap)
                cropQueue = cropQueue.drop(1)
            },
            onCancel = {
                discardPendingImage(uri)
                cropQueue = cropQueue.drop(1)
            },
        )
    }
    if (confirmDeleteRecord) AlertDialog(
        onDismissRequest = { confirmDeleteRecord = false }, title = { Text("删除当天记录？") },
        text = { Text("当天的体重、备注和所有照片将被永久删除，此操作无法撤销。") },
        confirmButton = { Button(onClick = { confirmDeleteRecord = false; deleteRecord() }, enabled = !state.isSaving) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { confirmDeleteRecord = false }) { Text("取消") } },
    )
}

@Composable
private fun WeightEditor(morning: Double?, evening: Double?, dismiss: () -> Unit, save: (Double?, Double?) -> Unit) {
    var m by remember { mutableStateOf(morning?.toString().orEmpty()) }; var e by remember { mutableStateOf(evening?.toString().orEmpty()) }
    fun valid(text: String) = text.isBlank() || (Regex("^\\d{1,3}([.]\\d{0,2})?$").matches(text) && text.toDoubleOrNull()?.let(WeightCalculator::isValid) == true)
    AlertDialog(onDismissRequest = dismiss, title = { Text("编辑体重") }, text = { Column {
        OutlinedTextField(m, { m = it }, label = { Text("早晨体重（kg）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = !valid(m))
        OutlinedTextField(e, { e = it }, label = { Text("晚上体重（kg）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = !valid(e), modifier = Modifier.padding(top = 8.dp))
    } }, confirmButton = { Button(onClick = { save(m.toDoubleOrNull(), e.toDoubleOrNull()) }, enabled = valid(m) && valid(e)) { Text("保存") } }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

@Composable
private fun NoteEditor(initial: String, dismiss: () -> Unit, save: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("编辑备注") }, text = { OutlinedTextField(text, { if (it.length <= 500) text = it }, minLines = 4, supportingText = { Text("${text.length}/500") }) }, confirmButton = { Button(onClick = { save(text) }) { Text("保存") } }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

private fun formatWeight(value: Double?) = value?.let { String.format(Locale.US, "%.1f kg", it) } ?: "暂无"
