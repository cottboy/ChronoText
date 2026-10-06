package com.chronotext.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chronotext.app.R
import com.chronotext.app.data.AppDatabase
import com.chronotext.app.data.RepeatType
import com.chronotext.app.data.TaskEntity
import com.chronotext.app.schedule.TaskService
import com.chronotext.app.util.ContactHelper
import com.chronotext.app.util.Format
import com.chronotext.app.util.SimHelper
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 任务编辑页：新建（taskId <= 0）或编辑既有任务。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditScreen(taskId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---------- 表单状态 ----------
    var loaded by remember { mutableStateOf(taskId <= 0) }
    var name by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var sims by remember { mutableStateOf(listOf<SimHelper.SimInfo>()) }
    var simIndex by remember { mutableIntStateOf(0) } // 0 = 默认卡，1..n = 具体卡
    var repeatType by remember { mutableIntStateOf(RepeatType.ONCE) }
    var oneShotDate by remember { mutableStateOf<LocalDate?>(null) }
    var month by remember { mutableIntStateOf(1) }
    var day by remember { mutableIntStateOf(1) }
    var isLeap by remember { mutableStateOf(false) }
    var leapFallback by remember { mutableStateOf(true) }
    var intervalDays by remember { mutableStateOf("7") }
    var anchorDate by remember { mutableStateOf<LocalDate?>(null) }
    var time by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var reminderIndex by remember { mutableIntStateOf(0) } // 0 不提醒 / 1 提前1天 / 2 提前3天
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // 对话框开关
    var showTimePicker by remember { mutableStateOf(false) }
    var dateTarget by remember { mutableStateOf<DateTarget?>(null) }

    LaunchedEffect(taskId) {
        sims = SimHelper.availableSims(context)
        if (taskId > 0) {
            AppDatabase.get(context).taskDao().byId(taskId)?.let { t ->
                name = t.name
                contactName = t.recipientName
                phone = t.recipientPhone
                content = t.content
                repeatType = t.repeatType
                month = t.month
                day = t.day
                isLeap = t.leapMonth
                leapFallback = t.leapFallToRegular
                intervalDays = t.intervalDays.toString()
                time = LocalTime.of(t.hour, t.minute)
                reminderIndex = when (t.reminderDaysBefore) {
                    1 -> 1
                    3 -> 2
                    else -> 0
                }
                simIndex = sims.indexOfFirst { it.subscriptionId == t.subscriptionId }.let {
                    if (t.subscriptionId >= 0 && it >= 0) it + 1 else 0
                }
                when (t.repeatType) {
                    RepeatType.ONCE ->
                        oneShotDate = if (t.oneShotYear > 0) LocalDate.of(t.oneShotYear, t.month, t.day) else LocalDate.now()
                    RepeatType.EVERY_N_DAYS ->
                        anchorDate = LocalDate.ofEpochDay(t.anchorEpochDay)
                }
            }
        } else {
            oneShotDate = LocalDate.now().plusDays(1)
            anchorDate = LocalDate.now()
        }
        loaded = true
    }

    // ---------- 通讯录选择（先权限后选择器） ----------
    val contactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            ContactHelper.readContact(context, uri)?.let { (n, p) ->
                contactName = n
                phone = p
            }
        }
    }
    val contactPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            contactLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI))
        }
    }

    // ---------- 保存 ----------
    fun buildTask(): TaskEntity {
        val reminderDays = when (reminderIndex) {
            1 -> 1
            2 -> 3
            else -> 0
        }
        val subId = if (simIndex in 1..sims.size) sims[simIndex - 1].subscriptionId else -1
        return when (repeatType) {
            RepeatType.ONCE -> TaskEntity(
                id = taskId.coerceAtLeast(0), name = name.trim(), recipientName = contactName.trim(),
                recipientPhone = phone, content = content.trim(), subscriptionId = subId,
                repeatType = repeatType, oneShotYear = oneShotDate!!.year,
                month = oneShotDate!!.monthValue, day = oneShotDate!!.dayOfMonth,
                hour = time.hour, minute = time.minute, reminderDaysBefore = reminderDays,
            )
            RepeatType.EVERY_N_DAYS -> TaskEntity(
                id = taskId.coerceAtLeast(0), name = name.trim(), recipientName = contactName.trim(),
                recipientPhone = phone, content = content.trim(), subscriptionId = subId,
                repeatType = repeatType, intervalDays = intervalDays.toInt(),
                anchorEpochDay = anchorDate!!.toEpochDay(),
                hour = time.hour, minute = time.minute, reminderDaysBefore = reminderDays,
            )
            else -> TaskEntity(
                id = taskId.coerceAtLeast(0), name = name.trim(), recipientName = contactName.trim(),
                recipientPhone = phone, content = content.trim(), subscriptionId = subId,
                repeatType = repeatType, month = month, day = day,
                leapMonth = isLeap && repeatType == RepeatType.YEARLY_LUNAR,
                leapFallToRegular = leapFallback,
                hour = time.hour, minute = time.minute, reminderDaysBefore = reminderDays,
            )
        }
    }

    fun validate(): String? {
        if (name.isBlank()) return context.getString(R.string.err_name_empty)
        val clean = phone.replace(" ", "").replace("-", "")
        if (!Regex("^\\+?[0-9]{5,20}$").matches(clean)) return context.getString(R.string.err_phone_invalid)
        if (content.isBlank()) return context.getString(R.string.err_content_empty)
        when (repeatType) {
            RepeatType.ONCE -> {
                val d = oneShotDate ?: return context.getString(R.string.err_date_required)
                if (d.atTime(time) <= java.time.LocalDateTime.now()) {
                    return context.getString(R.string.err_once_past)
                }
            }
            RepeatType.EVERY_N_DAYS -> {
                if (anchorDate == null) return context.getString(R.string.err_date_required)
                val d = intervalDays.toIntOrNull()
                if (d == null || d < 1) return context.getString(R.string.err_interval_invalid)
            }
        }
        return null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (taskId > 0) R.string.task_edit_title else R.string.task_new_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.dialog_cancel))
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 任务名称
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 收件人 + 通讯录按钮
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = {
                        Text(
                            if (contactName.isBlank()) stringResource(R.string.field_phone)
                            else stringResource(R.string.field_phone_with_name, contactName)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_CONTACTS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        contactLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI))
                    } else {
                        contactPermLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                }) { Text(stringResource(R.string.field_pick_contact)) }
            }

            // 短信内容
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.field_content)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            // 发信卡
            DropdownField(
                label = stringResource(R.string.field_sim),
                options = listOf(stringResource(R.string.sim_default)) + sims.map { it.label },
                selectedIndex = simIndex,
                display = { it },
                onSelect = { simIndex = it },
            )

            // 周期类型
            Text(stringResource(R.string.field_repeat), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val typeOptions = listOf(
                    RepeatType.ONCE to stringResource(R.string.repeat_once),
                    RepeatType.YEARLY_SOLAR to stringResource(R.string.repeat_yearly_solar),
                    RepeatType.YEARLY_LUNAR to stringResource(R.string.repeat_yearly_lunar),
                    RepeatType.EVERY_N_DAYS to stringResource(R.string.repeat_every_n_days),
                )
                typeOptions.forEach { (type, label) ->
                    FilterChip(
                        selected = repeatType == type,
                        onClick = { repeatType = type },
                        label = { Text(label) },
                    )
                }
            }

            // 按周期类型展示对应字段
            when (repeatType) {
                RepeatType.ONCE -> DateField(
                    label = stringResource(R.string.field_once_date),
                    date = oneShotDate,
                    onClick = { dateTarget = DateTarget.ONCE },
                )

                RepeatType.YEARLY_SOLAR -> {
                    val maxDay = remember(month) { solarMaxDay(month) }
                    // 字符串资源先在组合上下文解析，避免在普通 lambda 中调用 stringResource
                    val monthLabels = (1..12).map { stringResource(R.string.month_n, it) }
                    val dayLabels = (1..maxDay).map { stringResource(R.string.day_n, it) }
                    DropdownField(
                        label = stringResource(R.string.field_month),
                        options = monthLabels,
                        selectedIndex = month - 1,
                        display = { it },
                        onSelect = { idx ->
                            month = idx + 1
                            if (day > maxDay) day = maxDay
                        },
                    )
                    DropdownField(
                        label = stringResource(R.string.field_day),
                        options = dayLabels,
                        selectedIndex = day - 1,
                        display = { it },
                        onSelect = { day = it + 1 },
                    )
                }

                RepeatType.YEARLY_LUNAR -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.field_leap_month), Modifier.weight(1f))
                        Switch(checked = isLeap, onCheckedChange = { isLeap = it })
                    }
                    val leapPrefix = stringResource(R.string.leap_prefix)
                    val monthLabels = (1..12).map { "${if (isLeap) leapPrefix else ""}${Format.lunarMonth(it)}月" }
                    val dayLabels = (1..30).map { Format.lunarDay(it) }
                    DropdownField(
                        label = stringResource(R.string.field_month),
                        options = monthLabels,
                        selectedIndex = month - 1,
                        display = { it },
                        onSelect = { month = it + 1 },
                    )
                    DropdownField(
                        label = stringResource(R.string.field_day),
                        options = dayLabels,
                        selectedIndex = day - 1,
                        display = { it },
                        onSelect = { day = it + 1 },
                    )
                    if (isLeap) {
                        val fallbackOptions = listOf(
                            stringResource(R.string.leap_fallback_regular),
                            stringResource(R.string.leap_fallback_skip),
                        )
                        DropdownField(
                            label = stringResource(R.string.field_leap_fallback),
                            options = fallbackOptions,
                            selectedIndex = if (leapFallback) 0 else 1,
                            display = { it },
                            onSelect = { leapFallback = it == 0 },
                        )
                    }
                }

                RepeatType.EVERY_N_DAYS -> {
                    DateField(
                        label = stringResource(R.string.field_anchor_date),
                        date = anchorDate,
                        onClick = { dateTarget = DateTarget.ANCHOR },
                    )
                    OutlinedTextField(
                        value = intervalDays,
                        onValueChange = { intervalDays = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text(stringResource(R.string.field_interval)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 发送时刻（所有类型共用）
            DateField(
                label = stringResource(R.string.field_time),
                text = time.format(DateTimeFormatter.ofPattern("HH:mm")),
                onClick = { showTimePicker = true },
            )

            // 提前提醒
            DropdownField(
                label = stringResource(R.string.field_reminder),
                options = listOf(0, 1, 3),
                selectedIndex = reminderIndex,
                display = { Format.describeReminder(it) },
                onSelect = { reminderIndex = it },
            )

            // 错误提示
            errorMsg?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    val error = validate()
                    errorMsg = error
                    if (error == null) {
                        scope.launch {
                            try {
                                TaskService.saveTask(context, buildTask())
                                onDone()
                            } catch (e: Exception) {
                                errorMsg = e.message ?: context.getString(R.string.err_save_failed)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save_action)) }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ---------- 对话框 ----------
    if (showTimePicker) {
        TimePickerDialogM3(
            initial = time,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                time = it
                showTimePicker = false
            },
        )
    }
    when (dateTarget) {
        DateTarget.ONCE -> DatePickerDialogM3(
            initial = oneShotDate,
            onDismiss = { dateTarget = null },
            onConfirm = {
                oneShotDate = it
                dateTarget = null
            },
        )

        DateTarget.ANCHOR -> DatePickerDialogM3(
            initial = anchorDate,
            onDismiss = { dateTarget = null },
            onConfirm = {
                anchorDate = it
                dateTarget = null
            },
        )

        null -> {}
    }
}

private enum class DateTarget { ONCE, ANCHOR }

@Composable
private fun DateField(label: String, date: LocalDate?, onClick: () -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
    DateField(
        label = label,
        text = date?.format(formatter) ?: "",
        onClick = onClick,
    )
}

@Composable
private fun DateField(label: String, text: String, onClick: () -> Unit) {
    // 外层承接点击，输入框仅作展示（禁用态颜色覆盖为正常观感）
    Box(Modifier.clickable(onClick = onClick)) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

/** 公历每月天数（2 月固定给 29，支持 2/29 生日） */
private fun solarMaxDay(month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    2 -> 29
    else -> 30
}
