package com.chronotext.app.schedule

import android.content.Context
import com.chronotext.app.data.AppDatabase
import com.chronotext.app.data.SendLogEntity
import com.chronotext.app.data.SendStatus
import com.chronotext.app.data.TaskEntity
import com.chronotext.app.notify.Notifier
import com.chronotext.app.sms.SmsDispatcher

/**
 * 任务业务编排：保存/删除/启停、发送终态处理、全局重排。
 */
object TaskService {

    /** 错过触发窗口阈值：超过则视为过期，跳过不补发 */
    const val MISSED_THRESHOLD_MS = 60 * 60 * 1000L
    /** 最大尝试次数（含首次） */
    const val MAX_ATTEMPTS = 3
    /** 重试间隔 */
    const val RETRY_DELAY_MS = 5 * 60 * 1000L

    /** 新建或更新任务：计算下次触发时间并注册闹钟 */
    suspend fun saveTask(context: Context, task: TaskEntity): Long {
        val db = AppDatabase.get(context)
        val now = System.currentTimeMillis()
        val withNext = task.copy(
            nextTriggerAt = NextRunCalculator.nextTrigger(task, now),
            createdAt = if (task.createdAt == 0L) now else task.createdAt,
        )
        return if (task.id == 0L) {
            val id = db.taskDao().insert(withNext)
            AlarmScheduler.scheduleTask(context, withNext.copy(id = id))
            id
        } else {
            db.taskDao().update(withNext)
            AlarmScheduler.scheduleTask(context, withNext)
            task.id
        }
    }

    /** 删除任务：取消闹钟，发送记录保留（有快照） */
    suspend fun deleteTask(context: Context, task: TaskEntity) {
        AlarmScheduler.cancelTask(context, task.id)
        AppDatabase.get(context).taskDao().delete(task)
    }

    /** 启用/停用任务 */
    suspend fun setEnabled(context: Context, task: TaskEntity, enabled: Boolean) {
        val db = AppDatabase.get(context)
        val updated = task.copy(
            enabled = enabled,
            // 重新启用时若 nextTriggerAt 已过期则重算
            nextTriggerAt = if (enabled && task.nextTriggerAt <= System.currentTimeMillis()) {
                NextRunCalculator.nextTrigger(task, System.currentTimeMillis())
            } else task.nextTriggerAt,
        )
        db.taskDao().update(updated)
        AlarmScheduler.scheduleTask(context, updated)
    }

    /**
     * 发送成功终态：更新记录、发通知、推进到下一次。
     */
    suspend fun onSendSuccess(context: Context, logId: Long) {
        val db = AppDatabase.get(context)
        val log = db.sendLogDao().byId(logId) ?: return
        db.sendLogDao().update(log.copy(status = SendStatus.SUCCESS, actualAt = System.currentTimeMillis(), detail = "发送成功"))
        Notifier.notifySuccess(context, log.recipientPhone)
        advanceAfterSend(context, log.taskId)
    }

    /**
     * 发送失败：未达上限则安排重试，否则进入失败终态并通知。
     */
    suspend fun onSendFailed(context: Context, logId: Long, attempt: Int, reason: String) {
        val db = AppDatabase.get(context)
        val log = db.sendLogDao().byId(logId) ?: return
        val task = db.taskDao().byId(log.taskId)
        if (attempt < MAX_ATTEMPTS && task?.enabled == true) {
            db.sendLogDao().update(
                log.copy(
                    attempt = attempt,
                    detail = "第 $attempt 次发送失败（$reason），约 5 分钟后自动重试",
                )
            )
            AlarmScheduler.scheduleRetry(context, log.taskId, attempt + 1, logId, RETRY_DELAY_MS)
        } else {
            val retried = if (attempt > 1) "，已重试 ${attempt - 1} 次" else ""
            db.sendLogDao().update(
                log.copy(
                    status = SendStatus.FAILED,
                    attempt = attempt,
                    actualAt = System.currentTimeMillis(),
                    detail = "发送失败：$reason$retried",
                )
            )
            Notifier.notifyFailure(context, log.taskName, log.recipientPhone, reason)
            advanceAfterSend(context, log.taskId)
        }
    }

    /**
     * 一次发送计划结束（成功/最终失败/跳过）后推进：
     * 周期任务计算并注册下一次；单次任务自动停用。
     */
    suspend fun advanceAfterSend(context: Context, taskId: Long) {
        val db = AppDatabase.get(context)
        val task = db.taskDao().byId(taskId) ?: return
        val next = NextRunCalculator.nextTrigger(task, System.currentTimeMillis())
        val updated = task.copy(
            nextTriggerAt = next,
            // 单次任务没有下一次时自动停用
            enabled = task.enabled && next > 0,
        )
        db.taskDao().update(updated)
        AlarmScheduler.scheduleTask(context, updated)
    }

    /**
     * 全局重排：重算所有启用任务的下次触发时间并重新注册闹钟。
     * 用于开机、覆盖安装、系统时间/时区变化。
     */
    suspend fun rescheduleAll(context: Context) {
        val db = AppDatabase.get(context)
        val now = System.currentTimeMillis()
        db.taskDao().enabledTasks().forEach { task ->
            val next = NextRunCalculator.nextTrigger(task, now)
            val updated = if (next != task.nextTriggerAt) task.copy(nextTriggerAt = next) else task
            if (updated !== task) db.taskDao().update(updated)
            AlarmScheduler.scheduleTask(context, updated)
        }
    }
}
