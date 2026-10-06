package com.chronotext.app.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.chronotext.app.data.AppDatabase
import com.chronotext.app.schedule.TaskService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 短信发送结果回执：成功 → 终态并推进下一次；
 * 失败 → 未达上限则定时重试，否则失败终态并通知。
 */
class SmsResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_SENT) return
        val logId = intent.getLongExtra(EXTRA_LOG_ID, -1)
        if (logId <= 0) return
        val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 1)
        val resultCode = resultCode
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (resultCode == Activity.RESULT_OK) {
                    TaskService.onSendSuccess(appContext, logId)
                } else {
                    TaskService.onSendFailed(appContext, logId, attempt, describeResult(resultCode))
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** 把运营商回执错误码转成可读文案 */
    private fun describeResult(resultCode: Int): String = when (resultCode) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "未知错误"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "手机无信号服务"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "处于飞行模式或无线已关闭"
        SmsManager.RESULT_ERROR_NULL_PDU -> "短信数据包为空"
        else -> "错误码 $resultCode"
    }

    companion object {
        const val ACTION_SMS_SENT = "com.chronotext.app.action.SMS_SENT"
        const val EXTRA_LOG_ID = "log_id"
        const val EXTRA_ATTEMPT = "attempt"
    }
}
