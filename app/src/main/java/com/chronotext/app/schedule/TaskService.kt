package com.chronotext.app.schedule

import android.content.Context
import androidx.room.withTransaction
import com.chronotext.app.R
import com.chronotext.app.data.AppDatabase
import com.chronotext.app.data.SendLogEntity
import com.chronotext.app.data.SendStatus
import com.chronotext.app.data.TaskEntity
import com.chronotext.app.notify.Notifier
import com.chronotext.app.sms.SmsDispatcher

/**
 * 任务业务编排：保存/删除/启停、发送执行、发送终态处理、全局重排与自愈核对。
 */
object TaskService {

    /** 错过触发窗口阈值：超过则视为过期，跳过不补发（仅正常闹钟路径；自愈补发不受此限） */
    const val MISSED_THRESHOLD_MS = 60 * 60 * 1000L
    /** 最大尝试次数（含首次） */
    const val MAX_ATTEMPTS = 3
    /** 重试间隔 */
    const val RETRY_DELAY_MS = 5 * 60 * 1000L
    /** 发送链路卡死判定：距最近一次尝试超过该时长仍无回执，视为回执丢失 */
    const val PENDING_GRACE_MS = 10 * 60 * 1000L

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
     * 执行一次发送（首次 attempt=1 / 重试 attempt>1）。
     *
     * @param allowLate 自愈补发时为 true：即使错过时间较长也补发（迟到总比漏发好）；
     *                  正常闹钟路径为 false，错过超过阈值则记为跳过。
     * @return 是否实际发起了发送（同步失败已在此处走失败处理）
     */
    suspend fun executeSend(context: Context, taskId: Long, attempt: Int, logId: Long, allowLate: Boolean = false) {
        val db = AppDatabase.get(context)
        val task = db.taskDao().byId(taskId) ?: return
        val now = System.currentTimeMillis()

        val activeLogId: Long
        if (logId > 0) {
            // 重试：复用同一条发送记录
            val existing = db.sendLogDao().byId(logId) ?: return
            if (!task.enabled) {
                // 重试期间用户停用了任务：终止重试
                db.sendLogDao().update(
                    existing.copy(status = SendStatus.FAILED, actualAt = now, detail = "任务已停用，重试终止")
                )
                return
            }
            db.sendLogDao().update(
                existing.copy(attempt = attempt, detail = "第 $attempt 次尝试发送中…", lastActivityAt = now)
            )
            activeLogId = logId
        } else {
            if (!task.enabled) return
            // 防重 + 建记录放在同一事务：系统重排与自愈闹钟并发触发时也不会重复发送
            val newLogId = db.withTransaction {
                if (db.sendLogDao().latestByTaskAndScheduledAt(taskId, task.nextTriggerAt) != null) {
                    -1L
                } else if (!allowLate && task.nextTriggerAt > 0 &&
                    now - task.nextTriggerAt > MISSED_THRESHOLD_MS
                ) {
                    // 错过检查：实际触发比计划晚超过阈值则不发过期短信
                    db.sendLogDao().insert(
                        SendLogEntity(
                            taskId = task.id, taskName = task.name, recipientPhone = task.recipientPhone,
                            content = task.content, subscriptionId = task.subscriptionId,
                            scheduledAt = task.nextTriggerAt, actualAt = now,
                            status = SendStatus.SKIPPED, detail = "错过触发时间超过 1 小时，已跳过本次",
                        )
                    )
                    -2L
                } else {
                    db.sendLogDao().insert(
                        SendLogEntity(
                            taskId = task.id, taskName = task.name, recipientPhone = task.recipientPhone,
                            content = task.content, subscriptionId = task.subscriptionId,
                            scheduledAt = task.nextTriggerAt,
                            status = SendStatus.PENDING, detail = "发送中…", lastActivityAt = now,
                        )
                    )
                }
            }
            when (newLogId) {
                -1L -> return
                -2L -> {
                    advanceAfterSend(context, task.id)
                    return
                }
                else -> activeLogId = newLogId
            }
        }

        val syncError = SmsDispatcher.send(context, task, activeLogId, attempt)
        if (syncError != null) {
            // 同步失败（无权限/无卡等），不会有回执，直接走失败处理
            onSendFailed(context, activeLogId, attempt, syncError)
        }
    }

    /**
     * 自愈核对：计划时刻已过但任务未推进时，判断本次发送处于什么状态并补齐。
     * 由自愈闹钟（计划时刻 + [AlarmScheduler.HEAL_DELAY_MS]）、开机/时间变化重排、打开应用触发。
     */
    suspend fun healTask(context: Context, taskId: Long) {
        val db = AppDatabase.get(context)
        val task = db.taskDao().byId(taskId) ?: return
        if (!task.enabled) return
        val now = System.currentTimeMillis()
        // 没有待核对的计划（已推进到未来或单次已完成）
        if (task.nextTriggerAt <= 0 || task.nextTriggerAt > now) return

        val log = db.sendLogDao().latestByTaskAndScheduledAt(taskId, task.nextTriggerAt)
        when {
            log == null -> {
                // 发送记录不存在：主闹钟被系统清理或设备关机错过，补发。
                // 先注册核对闹钟，防止补发后回执丢失导致记录永久卡在发送中。
                AlarmScheduler.scheduleHealCheck(context, task.id, now + AlarmScheduler.HEAL_DELAY_MS)
                executeSend(context, task.id, attempt = 1, logId = -1, allowLate = true)
            }
            log.status == SendStatus.PENDING && now - log.lastActivityAt >= PENDING_GRACE_MS -> {
                // 回执丢失：发送结果未知，转失败终态并推进，由用户人工确认是否补发
                db.sendLogDao().update(
                    log.copy(
                        status = SendStatus.FAILED, actualAt = now,
                        detail = "发送回执超时，结果未知，请与对方确认是否收到",
                    )
                )
                Notifier.notifyFailure(
                    context, task.name, task.recipientPhone,
                    context.getString(R.string.fail_receipt_timeout),
                )
                advanceAfterSend(context, task.id)
            }
            log.status == SendStatus.PENDING -> {
                // 发送链路仍在进行（重试中/回执在途）：稍后再核对
                AlarmScheduler.scheduleHealCheck(context, task.id, now + AlarmScheduler.HEAL_DELAY_MS)
            }
            else -> {
                // 本次计划已有终态但未推进（异常兜底）：推进到下一次
                advanceAfterSend(context, task.id)
            }
        }
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
     * 全局重排：用于开机、覆盖安装、系统时间/时区变化、打开应用。
     * 计划时刻已过但未推进的任务进入自愈核对（含补发），
     * 其余任务重算下次触发时间并重新注册闹钟。
     */
    suspend fun rescheduleAll(context: Context) {
        val db = AppDatabase.get(context)
        val now = System.currentTimeMillis()
        db.taskDao().enabledTasks().forEach { task ->
            if (task.nextTriggerAt in 1..now) {
                healTask(context, task.id)
            } else {
                val next = NextRunCalculator.nextTrigger(task, now)
                val updated = if (next != task.nextTriggerAt) task.copy(nextTriggerAt = next) else task
                if (updated !== task) db.taskDao().update(updated)
                AlarmScheduler.scheduleTask(context, updated)
            }
        }
    }
}
