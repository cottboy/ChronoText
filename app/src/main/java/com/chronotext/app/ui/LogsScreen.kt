package com.chronotext.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.chronotext.app.R
import com.chronotext.app.data.AppDatabase
import com.chronotext.app.data.SendLogEntity
import com.chronotext.app.data.SendStatus
import com.chronotext.app.util.Format
import kotlinx.coroutines.launch

/**
 * 发送记录页：所有发送尝试的历史（含重试与跳过）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<SendLogEntity>?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        logs = AppDatabase.get(context).sendLogDao().recent()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_logs)) },
                actions = {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.Block, contentDescription = stringResource(R.string.logs_clear))
                    }
                },
            )
        },
    ) { padding ->
        when {
            logs == null -> {}
            logs!!.isEmpty() -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.Inbox,
                    contentDescription = null,
                    Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.empty_logs),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(logs!!, key = { it.id }) { log ->
                    LogCard(log)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.logs_clear)) },
            text = { Text(stringResource(R.string.logs_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        AppDatabase.get(context).sendLogDao().clearAll()
                        refreshKey++
                    }
                }) { Text(stringResource(R.string.dialog_ok), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

@Composable
private fun LogCard(log: SendLogEntity) {
    val (icon, tint, statusText) = when (log.status) {
        SendStatus.SUCCESS -> Triple(
            Icons.Filled.CheckCircle,
            MaterialTheme.colorScheme.primary,
            stringResource(R.string.status_success),
        )
        SendStatus.FAILED -> Triple(
            Icons.Filled.Close,
            MaterialTheme.colorScheme.error,
            stringResource(R.string.status_failed),
        )
        SendStatus.PENDING -> Triple(
            Icons.Filled.HourglassTop,
            MaterialTheme.colorScheme.tertiary,
            stringResource(R.string.status_pending),
        )
        else -> Triple(
            Icons.Filled.Block,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.status_skipped),
        )
    }

    Card {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = statusText, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(log.taskName, style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        append(log.recipientPhone)
                        append(" · ")
                        append(stringResource(R.string.logs_attempt, log.attempt))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.logs_scheduled_at, Format.dateTime(log.scheduledAt)),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (log.detail.isNotBlank()) {
                    Text(
                        log.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                statusText,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
    }
}
