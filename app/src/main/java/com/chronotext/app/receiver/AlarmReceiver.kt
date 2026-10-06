package com.chronotext.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chronotext.app.data.AppDatabase
import com.chronotext.app.data.SendLogEntity
import com.chronotext.app.data.SendStatus
import com.chronotext.app.notify.Notifier
import com.chronotext.app.schedule.AlarmScheduler
import com.chronotext.app.schedule.TaskService
import com.chronotext.app.sms.SmsDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟触发入口：到点发送短信 / 发送前提醒。
 *
 * 发送流程：
 * 1. 首次触发（attempt=1）：错过检查 → 建 PENDING 记录 → 发送；
 * 2. 重试触发（attempt>1）：复用同一条记录 → 再次发送；
 * 3. 回执由 [SmsResultReceiver] 处理成功/失败。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_ID, -1)
        if (taskId <= 0) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_SEND -> handleSend(
                        appContext, taskId,
                        attempt = intent.getIntExtra(AlarmScheduler.EXTRA_ATTEMPT, 1),
                        logId = intent.getLongExtra(AlarmScheduler.EXTRA_LOG_ID, -1),
                    )
                    ACTION_REMIND -> handleRemind(appContext, taskId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleSend(context: Context, taskId: Long, attempt: Int, logId: Long) {
        val db = AppDatabase.get(context)
        val task = db.taskDao().byId(taskId) ?: return
        val now = System.currentTimeMillis()

        if (attempt == 1) {
            // 任务已停用但闹钟仍排队：忽略
            if (!task.enabled) return
            // 错过检查：实际触发比计划晚超过阈值则不发过期短信
            if (task.nextTriggerAt > 0 && now - task.nextTriggerAt > TaskService.MISSED_THRESHOLD_MS) {
                db.sendLogDao().insert(
                    SendLogEntity(
                        taskId = task.id, taskName = task.name, recipientPhone = task.recipientPhone,
                        content = task.content, subscriptionId = task.subscriptionId,
                        scheduledAt = task.nextTriggerAt, actualAt = now,
                        status = SendStatus.SKIPPED, detail = "错过触发时间超过 1 小时，已跳过本次",
                    )
                )
                TaskService.advanceAfterSend(context, task.id)
                return
            }
        }

        // 定位/创建本次发送计划的记录
        val activeLogId: Long = if (logId > 0) {
            val existing = db.sendLogDao().byId(logId)
            if (existing == null) return
            if (!task.enabled) {
                // 重试期间用户停用了任务：终止重试
                db.sendLogDao().update(
                    existing.copy(status = SendStatus.FAILED, actualAt = now, detail = "任务已停用，重试终止")
                )
                return
            }
            db.sendLogDao().update(existing.copy(attempt = attempt, detail = "第 $attempt 次尝试发送中…"))
            logId
        } else {
            if (!task.enabled) return
            db.sendLogDao().insert(
                SendLogEntity(
                    taskId = task.id, taskName = task.name, recipientPhone = task.recipientPhone,
                    content = task.content, subscriptionId = task.subscriptionId,
                    scheduledAt = task.nextTriggerAt, actualAt = now,
                    status = SendStatus.PENDING, detail = "发送中…",
                )
            )
        }

        val syncError = SmsDispatcher.send(context, task, activeLogId, attempt)
        if (syncError != null) {
            // 同步失败（无权限/无卡等），不会有回执，直接走失败处理
            TaskService.onSendFailed(context, activeLogId, attempt, syncError)
        }
    }

    private suspend fun handleRemind(context: Context, taskId: Long) {
        val task = AppDatabase.get(context).taskDao().byId(taskId) ?: return
        if (!task.enabled) return
        Notifier.notifyReminder(context, task.id, task.name, task.recipientPhone, task.nextTriggerAt)
    }

    companion object {
        const val ACTION_SEND = "com.chronotext.app.action.SEND"
        const val ACTION_REMIND = "com.chronotext.app.action.REMIND"
    }
}
