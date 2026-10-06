package com.chronotext.app.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.chronotext.app.data.TaskEntity
import com.chronotext.app.receiver.SmsResultReceiver

/**
 * 短信发送：按任务指定的订阅 ID（双卡）发送。
 *
 * 返回 null 表示已成功发起、等待回执；
 * 返回非 null 字符串表示同步失败（无权限/无卡/号码非法等），
 * 调用方应直接按失败处理。
 */
object SmsDispatcher {

    fun send(context: Context, task: TaskEntity, logId: Long, attempt: Int): String? {
        return try {
            val sm = if (task.subscriptionId >= 0) {
                // 任务指定了发信卡；若卡已被拔出/失效，回执会报错并走重试→失败通知
                SmsManager.getSmsManagerForSubscriptionId(task.subscriptionId)
            } else {
                SmsManager.getDefault()
            }
            val sentIntent = PendingIntent.getBroadcast(
                context,
                logId.toInt(),
                Intent(context, SmsResultReceiver::class.java)
                    .setAction(SmsResultReceiver.ACTION_SMS_SENT)
                    .putExtra(SmsResultReceiver.EXTRA_LOG_ID, logId)
                    .putExtra(SmsResultReceiver.EXTRA_ATTEMPT, attempt),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            sm.sendTextMessage(task.recipientPhone, null, task.content, sentIntent, null)
            null
        } catch (e: Exception) {
            e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        }
    }
}
