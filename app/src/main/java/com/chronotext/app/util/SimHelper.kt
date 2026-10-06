package com.chronotext.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * SIM 卡信息（双卡支持）。
 */
object SimHelper {

    data class SimInfo(val subscriptionId: Int, val label: String)

    /** 列出当前可用的 SIM 卡（需要 READ_PHONE_STATE 权限；无权限或无卡返回空列表） */
    fun availableSims(context: Context): List<SimInfo> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return emptyList()
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val list = try {
            sm.activeSubscriptionInfoList
        } catch (e: SecurityException) {
            emptyList()
        } ?: return emptyList()
        return list.sortedBy { it.simSlotIndex }.map {
            val name = it.displayName?.toString()?.takeIf { n -> n.isNotBlank() } ?: ""
            SimInfo(
                subscriptionId = it.subscriptionId,
                label = if (name.isBlank()) "SIM ${it.simSlotIndex + 1}" else "SIM ${it.simSlotIndex + 1} · $name",
            )
        }
    }

    /** 是否已加入电池优化白名单 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
