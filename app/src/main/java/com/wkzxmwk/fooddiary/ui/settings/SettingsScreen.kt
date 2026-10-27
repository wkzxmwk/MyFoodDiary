package com.wkzxmwk.fooddiary.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.wkzxmwk.fooddiary.BuildConfig
import com.wkzxmwk.fooddiary.domain.BodyMetricsCalculator
import com.wkzxmwk.fooddiary.domain.WeightCalculator
import com.wkzxmwk.fooddiary.domain.model.Gender
import com.wkzxmwk.fooddiary.domain.model.ReminderType
import com.wkzxmwk.fooddiary.ui.common.SectionCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    setTargetWeight: (Double?) -> Unit,
    setHeightCm: (Double?) -> Unit,
    setGender: (Gender?) -> Unit,
    setRemindersEnabled: (Boolean) -> Unit,
    setReminderTime: (ReminderType, LocalTime) -> Unit,
    setImageQuality: (Int) -> Unit,
    export: (android.net.Uri) -> Unit,
    import: (android.net.Uri) -> Unit,
    clearAll: () -> Unit,
    consumeMessage: () -> Unit,
) {
    var editTarget by remember { mutableStateOf(false) }; var editHeight by remember { mutableStateOf(false) }
    var editGender by remember { mutableStateOf(false) }; var editQuality by remember { mutableStateOf(false) }
    var editReminder by remember { mutableStateOf<ReminderType?>(null) }
    var confirmImport by remember { mutableStateOf(false) }; var confirmClear by remember { mutableStateOf(false) }; var about by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var notificationPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionGranted = granted
        if (!granted) {
            coroutineScope.launch { snackbar.showSnackbar("未授予通知权限，提醒暂时无法显示") }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(export) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(import) }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            if (message == "备份已导入") {
                val snackbarJob = launch {
                    snackbar.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Indefinite,
                    )
                }
                delay(1_500L)
                snackbar.currentSnackbarData?.dismiss()
                snackbarJob.join()
            } else {
                snackbar.showSnackbar(message)
            }
            consumeMessage()
        }
    }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(18.dp)); Text("🌿 设置", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("管理目标、图片和本地数据", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp))
            SectionCard(Modifier.fillMaxWidth()) {
                Column {
                    SettingRow("目标体重", state.settings.targetWeight?.let { String.format(Locale.US, "%.1f kg", it) } ?: "未设置", Icons.Default.Scale) { editTarget = true }
                    HorizontalDivider(); SettingRow("身高", state.settings.heightCm?.let { String.format(Locale.US, "%.1f cm", it) } ?: "未设置", Icons.Default.Straighten) { editHeight = true }
                    HorizontalDivider(); SettingRow("性别", state.settings.gender?.displayName ?: "未设置", Icons.Default.Person) { editGender = true }
                    HorizontalDivider(); SettingRow("默认体重单位", "kg（第一版固定）", Icons.Default.Scale) { }
                    HorizontalDivider(); SettingRow("图片压缩质量", "${state.settings.imageQuality}% · 保留原始尺寸", Icons.Default.Download) { editQuality = true }
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(Modifier.fillMaxWidth()) {
                Column {
                    ReminderSwitchRow(
                        enabled = state.settings.remindersEnabled,
                        permissionGranted = notificationPermissionGranted,
                        onChanged = { enabled ->
                            setRemindersEnabled(enabled)
                            if (
                                enabled &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !notificationPermissionGranted
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                    if (
                        state.settings.remindersEnabled &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !notificationPermissionGranted
                    ) {
                        HorizontalDivider()
                        SettingRow("通知权限", "点击允许系统显示提醒通知", Icons.Default.Notifications) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    ReminderType.entries.forEach { type ->
                        HorizontalDivider()
                        SettingRow(
                            type.displayName,
                            state.settings.reminderTime(type).format(DateTimeFormatter.ofPattern("HH:mm")),
                            Icons.Default.AccessTime,
                        ) { editReminder = type }
                    }
                    Text(
                        "系统可能根据省电策略对提醒时间进行少量调整。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(Modifier.fillMaxWidth()) { Column {
                SettingRow("导出数据", "ZIP备份，包含记录和图片", Icons.Default.Upload) { exportLauncher.launch("饮食体重记录-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.zip") }
                HorizontalDivider(); SettingRow("导入备份", "将替换当前全部数据", Icons.Default.Download) { confirmImport = true }
                HorizontalDivider(); SettingRow("清除全部数据", "永久删除记录、图片和设置", Icons.Default.DeleteForever, true) { confirmClear = true }
            } }
            Spacer(Modifier.height(12.dp))
            SectionCard(Modifier.fillMaxWidth()) {
                Column {
                    SettingRow("意见反馈", "通过邮箱 148098973@qq.com 联系我们", Icons.Default.Email) {
                        val feedbackIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:148098973@qq.com")
                            putExtra(Intent.EXTRA_SUBJECT, "FoodDiary 意见反馈")
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(feedbackIntent, "选择邮件应用"))
                        }.onFailure {
                            coroutineScope.launch {
                                snackbar.showSnackbar("未找到可用的邮件应用，请发送邮件至 148098973@qq.com")
                            }
                        }
                    }
                    HorizontalDivider()
                    SettingRow("关于应用", "版本 ${BuildConfig.VERSION_NAME}", Icons.Default.Info) { about = true }
                }
            }
            if (state.isWorking) Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Text("正在处理，请勿关闭应用", Modifier.padding(start = 12.dp)) }
            Spacer(Modifier.height(24.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (editTarget) TargetDialog(state.settings.targetWeight, { editTarget = false }) { setTargetWeight(it); editTarget = false }
    if (editHeight) HeightDialog(state.settings.heightCm, { editHeight = false }) { setHeightCm(it); editHeight = false }
    if (editGender) AlertDialog(
        onDismissRequest = { editGender = false },
        title = { Text("设置性别") },
        text = {
            Column {
                Text(
                    "性别仅作为本地个人资料保存，当前 BMI 计算公式不区分性别。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Gender.entries.forEach { gender ->
                    TextButton(
                        onClick = { setGender(gender); editGender = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(gender.displayName) }
                }
                TextButton(
                    onClick = { setGender(null); editGender = false },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("不设置") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { editGender = false }) { Text("取消") } },
    )
    editReminder?.let { type ->
        ReminderTimeDialog(
            type = type,
            initial = state.settings.reminderTime(type),
            dismiss = { editReminder = null },
            save = { time ->
                setReminderTime(type, time)
                editReminder = null
            },
        )
    }
    if (editQuality) AlertDialog(onDismissRequest = { editQuality = false }, title = { Text("图片压缩质量") }, text = { Column { listOf(60, 70, 80, 90).forEach { quality -> TextButton(onClick = { setImageQuality(quality); editQuality = false }, Modifier.fillMaxWidth()) { Text("$quality%${if (quality == 80) "（推荐）" else ""}") } } } }, confirmButton = {}, dismissButton = { TextButton(onClick = { editQuality = false }) { Text("取消") } })
    if (confirmImport) ConfirmDialog("导入并替换当前数据？", "导入前请先导出当前数据。选择备份后，现有记录和照片会被替换。", { confirmImport = false }) { confirmImport = false; importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
    if (confirmClear) ConfirmDialog("清除全部数据？", "所有体重、备注、照片和设置都会被永久删除，此操作无法撤销。", { confirmClear = false }) { confirmClear = false; clearAll() }
    if (about) AlertDialog(
        onDismissRequest = { about = false },
        title = { Text("饮食体重记录") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "版本 ${BuildConfig.VERSION_NAME}\n\n" +
                        "免责声明\n" +
                        "本应用仅用于个人饮食和体重记录，不提供医疗诊断、治疗建议或健康结论。如有健康疑问，请咨询具备资质的专业人员。\n\n" +
                        "用户应确保所记录、拍摄或导入的图片及内容具有合法使用权，不得侵犯第三方的著作权、肖像权、隐私权或其他合法权益。\n\n" +
                        "应用数据默认保存在本机，不会主动上传或共享。导出备份、通过其他应用发送数据以及设备交由他人使用后产生的隐私和数据安全风险，由用户自行管理。请妥善保管设备与备份文件，并定期备份重要数据。\n\n" +
                        "因设备故障、系统清理、卸载应用、误操作或不可抗力造成的数据丢失，本应用无法保证恢复。"
                )
            }
        },
        confirmButton = { TextButton(onClick = { about = false }) { Text("知道了") } },
    )
}

@Composable
private fun ReminderSwitchRow(
    enabled: Boolean,
    permissionGranted: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("饮食记录提醒", style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    !enabled -> "已关闭"
                    !permissionGranted -> "已开启，等待授予通知权限"
                    else -> "已开启，每天提醒"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onChanged)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    type: ReminderType,
    initial: LocalTime,
    dismiss: () -> Unit,
    save: (LocalTime) -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("设置${type.displayName}时间") },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            Button(
                onClick = {
                    save(LocalTime.of(pickerState.hour, pickerState.minute))
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } },
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(title, style = MaterialTheme.typography.bodyLarge, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Default.ChevronRight, null)
    }
}

@Composable
private fun TargetDialog(initial: Double?, dismiss: () -> Unit, save: (Double?) -> Unit) {
    var text by remember { mutableStateOf(initial?.toString().orEmpty()) }
    val value = text.toDoubleOrNull(); val valid = text.isBlank() || (Regex("^\\d{1,3}([.]\\d{0,2})?$").matches(text) && value?.let(WeightCalculator::isValid) == true)
    AlertDialog(onDismissRequest = dismiss, title = { Text("设置目标体重") }, text = { OutlinedTextField(text, { text = it }, label = { Text("目标体重（kg）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = !valid, supportingText = { Text("范围20–300 kg；留空可清除目标") }) }, confirmButton = { Button(onClick = { save(value) }, enabled = valid) { Text("保存") } }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

@Composable
private fun HeightDialog(initial: Double?, dismiss: () -> Unit, save: (Double?) -> Unit) {
    var text by remember { mutableStateOf(initial?.toString().orEmpty()) }
    val value = text.toDoubleOrNull()
    val valid = text.isBlank() || (
        Regex("^\\d{1,3}([.]\\d{0,1})?$").matches(text) &&
            value?.let(BodyMetricsCalculator::isValidHeight) == true
        )
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("设置身高") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("身高（cm）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = !valid,
                supportingText = { Text("范围50–250 cm，最多一位小数；留空可清除") },
            )
        },
        confirmButton = {
            Button(onClick = { save(value) }, enabled = valid) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, dismiss: () -> Unit, confirm: () -> Unit) = AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(message) }, confirmButton = { Button(onClick = confirm) { Text("继续") } }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
