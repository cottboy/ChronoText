package com.chronotext.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.chronotext.app.receiver.AlarmReceiver

/**
 * 精确闹钟调度器。
 *
 * 请求码约定（同一任务的两个闹钟互不冲突）：
 * - 发送闹钟：requestCode = taskId
 * - 提醒闹钟：requestCode = taskId + [REMINDER_REQUEST_OFFSET]
 */
object AlarmScheduler {

    private const val REMINDER_REQUEST_OFFSET = 100_000_000
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_ATTEMPT = "attempt"
    const val EXTRA_LOG_ID = "log_id"

    /** 注册任务的发送闹钟与提前提醒闹钟（调用前需先算好 nextTriggerAt） */
    fun scheduleTask(context: Context, task: com.chronotext.app.data.TaskEntity) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelTask(context, task.id)
        if (!task.enabled || task.nextTriggerAt <= 0) return
        val now = System.currentTimeMillis()

        setExactCompat(am, task.nextTriggerAt, sendPendingIntent(context, task.id, attempt = 1, logId = -1))
        if (task.reminderDaysBefore > 0) {
            val remindAt = task.nextTriggerAt - task.reminderDaysBefore * DAY_MS
            if (remindAt > now) {
                setExactCompat(am, remindAt, remindPendingIntent(context, task.id))
            }
        }
    }

    /** 发送失败后的自动重试闹钟 */
    fun scheduleRetry(context: Context, taskId: Long, attempt: Int, logId: Long, delayMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        setExactCompat(am, System.currentTimeMillis() + delayMs, sendPendingIntent(context, taskId, attempt, logId))
    }

    fun cancelTask(context: Context, taskId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(sendPendingIntent(context, taskId, attempt = 1, logId = -1))
        am.cancel(remindPendingIntent(context, taskId))
    }

    private fun setExactCompat(am: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (e: SecurityException) {
            // 精确闹钟权限未授予（Android 14 起默认拒绝）时降级为非精确，宁可迟到不发不出去
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun sendPendingIntent(context: Context, taskId: Long, attempt: Int, logId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_SEND)
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_ATTEMPT, attempt)
                .putExtra(EXTRA_LOG_ID, logId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun remindPendingIntent(context: Context, taskId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (taskId + REMINDER_REQUEST_OFFSET).toInt(),
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_REMIND)
                .putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
