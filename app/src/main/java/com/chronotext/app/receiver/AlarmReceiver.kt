package com.chronotext.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chronotext.app.data.AppDatabase
import com.chronotext.app.notify.Notifier
import com.chronotext.app.schedule.AlarmScheduler
import com.chronotext.app.schedule.TaskService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟触发入口：到点发送短信 / 发送前提醒 / 自愈核对。
 *
 * 发送流程：
 * 1. 首次触发（attempt=1）：防重与错过检查 → 建 PENDING 记录 → 发送；
 * 2. 重试触发（attempt>1）：复用同一条记录 → 再次发送；
 * 3. 回执由 [SmsResultReceiver] 处理成功/失败；
 * 4. 自愈触发：核对计划时刻已过的任务，闹钟丢失时补发、回执丢失时收尾。
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
                    ACTION_SEND -> TaskService.executeSend(
                        appContext, taskId,
                        attempt = intent.getIntExtra(AlarmScheduler.EXTRA_ATTEMPT, 1),
                        logId = intent.getLongExtra(AlarmScheduler.EXTRA_LOG_ID, -1),
                    )
                    ACTION_REMIND -> handleRemind(appContext, taskId)
                    ACTION_HEAL -> TaskService.healTask(appContext, taskId)
                }
            } finally {
                pending.finish()
            }
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
        const val ACTION_HEAL = "com.chronotext.app.action.HEAL"
    }
}
