package com.chronotext.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chronotext.app.schedule.TaskService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 系统事件重排：开机、覆盖安装、系统时间或时区变化后，
 * 重新计算并注册所有启用任务的闹钟（闹钟在重启后会全部丢失）。
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val needsReschedule = action in setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
        if (!needsReschedule) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TaskService.rescheduleAll(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
