package com.chronotext.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chronotext.app.R
import com.chronotext.app.data.AppDatabase
import com.chronotext.app.data.TaskEntity
import com.chronotext.app.schedule.TaskService
import com.chronotext.app.util.AutoStartGuide
import com.chronotext.app.util.Format
import com.chronotext.app.util.SimHelper
import kotlinx.coroutines.launch

/**
 * 任务列表页：权限体检卡 + 任务卡片列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(onEdit: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf<List<TaskEntity>?>(null) }
    var pendingDelete by remember { mutableStateOf<TaskEntity?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        tasks = AppDatabase.get(context).taskDao().all()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            PermissionHealthCard()

            when {
                tasks == null -> {}
                tasks!!.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(tasks!!, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onClick = { onEdit(task.id) },
                            onToggle = { enabled ->
                                scope.launch {
                                    TaskService.setEnabled(context, task, enabled)
                                    refreshKey++
                                }
                            },
                            onDelete = { pendingDelete = task },
                        )
                    }
                }
            }
        }
    }

    // 删除确认
    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_confirm, task.name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        TaskService.deleteTask(context, task)
                        refreshKey++
                    }
                }) { Text(stringResource(R.string.delete_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.empty_tasks_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_tasks_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCard(task: TaskEntity, onClick: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (task.enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = task.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete_action),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                Format.describePeriod(task),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(stringResource(R.string.task_recipient_prefix))
                    if (task.recipientName.isNotBlank()) append(task.recipientName).append(" ")
                    append(task.recipientPhone)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (task.enabled && task.nextTriggerAt > 0) {
                    stringResource(R.string.task_next_run, Format.dateTime(task.nextTriggerAt))
                } else {
                    stringResource(R.string.task_disabled)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 权限体检卡（自治组件）：
 * 检查运行时权限、精确闹钟、电池优化白名单；从系统设置返回时自动重新检查。
 * 国产厂商额外引导自启动设置（弹窗 + 卡片区块），可永久关闭。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionHealthCard() {
    val context = LocalContext.current
    var checkKey by remember { mutableIntStateOf(0) }

    // 运行时权限集合
    val runtimePermissions = remember {
        buildList {
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { checkKey++ }

    // 从系统设置页返回（如精确闹钟/电池优化授权）时重新检查
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val runtimeMissing = runtimePermissions.any { !isGranted(it) }
    val exactAlarmMissing = Build.VERSION.SDK_INT >= 31 &&
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() != true
    val batteryMissing = !SimHelper.isIgnoringBatteryOptimizations(context)
    // checkKey 参与读取，权限状态变化时触发重组刷新
    val allGranted = !runtimeMissing && !exactAlarmMissing && !batteryMissing && checkKey >= 0

    // 自启动引导：国产厂商且用户未选「不再提醒」时展示；弹窗每次冷启动最多出现一次
    var autoStartDismissed by rememberSaveable { mutableStateOf(AutoStartGuide.isDismissed(context)) }
    var autoStartDialogShown by rememberSaveable { mutableStateOf(false) }
    var showAutoStartDialog by rememberSaveable { mutableStateOf(false) }
    val showAutoStartGuide = AutoStartGuide.isRestrictiveManufacturer() && !autoStartDismissed
    LaunchedEffect(showAutoStartGuide) {
        if (showAutoStartGuide && !autoStartDialogShown) {
            autoStartDialogShown = true
            showAutoStartDialog = true
        }
    }

    if (showAutoStartDialog) {
        AlertDialog(
            // 点击弹窗外部关闭：下次冷启动再提醒
            onDismissRequest = { showAutoStartDialog = false },
            title = { Text(stringResource(R.string.autostart_dialog_title)) },
            text = { Text(stringResource(R.string.autostart_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showAutoStartDialog = false
                    AutoStartGuide.settingsIntent(context)?.let { intent ->
                        runCatching { context.startActivity(intent) }
                    }
                }) { Text(stringResource(R.string.autostart_dialog_go)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAutoStartDialog = false
                    autoStartDismissed = true
                    AutoStartGuide.dismiss(context)
                }) { Text(stringResource(R.string.autostart_dialog_never)) }
            },
        )
    }

    Card(Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(if (allGranted) R.string.perm_all_ok else R.string.perm_need_title),
                style = MaterialTheme.typography.titleSmall,
            )
            if (!allGranted) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.perm_need_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (runtimeMissing) {
                        TextButton(onClick = { permLauncher.launch(runtimePermissions.toTypedArray()) }) {
                            Icon(Icons.Filled.Warning, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.perm_runtime))
                        }
                    }
                    if (exactAlarmMissing) {
                        TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= 31) {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                                )
                            }
                        }) {
                            Icon(Icons.Filled.Warning, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.perm_exact_alarm))
                        }
                    }
                    if (batteryMissing) {
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                            )
                        }) {
                            Icon(Icons.Filled.Warning, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.perm_battery))
                        }
                    }
                }
            }
            // 国产 ROM 的自启动/后台运行是厂商私有开关，无法检测状态，常驻显示引导（可关闭）
            if (showAutoStartGuide) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.perm_autostart_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        AutoStartGuide.settingsIntent(context)?.let { intent ->
                            runCatching { context.startActivity(intent) }
                        }
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.perm_autostart_go))
                    }
                    TextButton(onClick = {
                        autoStartDismissed = true
                        AutoStartGuide.dismiss(context)
                    }) {
                        Text(
                            stringResource(R.string.perm_autostart_dismiss),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
