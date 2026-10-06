package com.chronotext.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.chronotext.app.MainActivity
import com.chronotext.app.R

/**
 * 通知：三个渠道分离重要性——
 * 发送失败（高）、发送成功（低）、发送前提醒（默认）。
 */
object Notifier {

    private const val CH_FAILURE = "send_failure"
    private const val CH_SUCCESS = "send_success"
    private const val CH_REMIND = "reminder"
    private const val ID_FAILURE = 2001
    private const val ID_SUCCESS = 2002
    private const val ID_REMIND_BASE = 3000

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_FAILURE, context.getString(R.string.channel_failure), NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SUCCESS, context.getString(R.string.channel_success), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, context.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /** 发送成功（低优先级，用于确认任务活着） */
    fun notifySuccess(context: Context, phone: String) {
        notify(context, CH_SUCCESS, ID_SUCCESS, context.getString(R.string.notif_success_title), context.getString(R.string.notif_success_text, phone))
    }

    /** 发送失败（高优先级，需要用户介入补发） */
    fun notifyFailure(context: Context, taskName: String, phone: String, reason: String) {
        notify(
            context, CH_FAILURE, ID_FAILURE,
            context.getString(R.string.notif_failure_title),
            context.getString(R.string.notif_failure_text, taskName, phone, reason),
        )
    }

    /** 发送前提醒 */
    fun notifyReminder(context: Context, taskId: Long, taskName: String, phone: String, triggerAt: Long) {
        val time = android.text.format.DateFormat.getDateFormat(context).format(triggerAt) + " " +
            android.text.format.DateFormat.getTimeFormat(context).format(triggerAt)
        notify(
            context, CH_REMIND, ID_REMIND_BASE + taskId.toInt(),
            context.getString(R.string.notif_remind_title),
            context.getString(R.string.notif_remind_text, taskName, phone, time),
        )
    }

    private fun notify(context: Context, channel: String, id: Int, title: String, text: String) {
        val pi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(id, notification)
        }
    }
}
