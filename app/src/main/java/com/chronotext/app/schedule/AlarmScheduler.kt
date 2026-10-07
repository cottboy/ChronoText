package com.chronotext.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.chronotext.app.receiver.AlarmReceiver

/**
 * 精确闹钟调度器。
 *
 * 请求码约定（同一任务的多个闹钟互不冲突）：
 * - 发送闹钟：requestCode = taskId（重试复用同一请求码，仅更新 extras）
 * - 提醒闹钟：requestCode = taskId + [REMINDER_REQUEST_OFFSET]
 * - 自愈核对闹钟：requestCode = taskId + [HEAL_REQUEST_OFFSET]
 */
object AlarmScheduler {

    private const val REMINDER_REQUEST_OFFSET = 100_000_000
    private const val HEAL_REQUEST_OFFSET = 200_000_000
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_ATTEMPT = "attempt"
    const val EXTRA_LOG_ID = "log_id"

    /** 自愈核对相对计划时刻的延迟：主闹钟被系统清理时，最迟此时自动补发 */
    const val HEAL_DELAY_MS = 15 * 60 * 1000L

    /** 注册任务的发送闹钟、提前提醒闹钟与自愈核对闹钟（调用前需先算好 nextTriggerAt） */
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
        // 自愈兜底：计划时刻 15 分钟后核对本次发送是否完成，
        // 精确闹钟被厂商 ROM 清理/设备关机错过时自动补发
        setExactCompat(am, task.nextTriggerAt + HEAL_DELAY_MS, healPendingIntent(context, task.id))
    }

    /** 发送失败后的自动重试闹钟 */
    fun scheduleRetry(context: Context, taskId: Long, attempt: Int, logId: Long, delayMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        setExactCompat(am, System.currentTimeMillis() + delayMs, sendPendingIntent(context, taskId, attempt, logId))
    }

    /** 自愈核对闹钟（指定时刻，用于发送链路进行中的再次核对） */
    fun scheduleHealCheck(context: Context, taskId: Long, triggerAt: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        setExactCompat(am, triggerAt, healPendingIntent(context, taskId))
    }

    fun cancelTask(context: Context, taskId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(sendPendingIntent(context, taskId, attempt = 1, logId = -1))
        am.cancel(remindPendingIntent(context, taskId))
        am.cancel(healPendingIntent(context, taskId))
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

    private fun healPendingIntent(context: Context, taskId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (taskId + HEAL_REQUEST_OFFSET).toInt(),
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_HEAL)
                .putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
