package com.chronotext.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 国产 ROM 自启动设置引导。
 *
 * 各厂商的「自启动 / 后台运行」是私有开关，没有公开 API 可申请，
 * 只能识别厂商后引导用户手动开启；识别不到时兜底跳应用详情页。
 */
object AutoStartGuide {

    /** 已知存在独立自启动/后台管控开关的厂商与品牌（小写，contains 匹配以兼容大小写差异） */
    private val RESTRICTIVE = setOf(
        "xiaomi", "redmi", "poco", "blackshark",
        "huawei", "honor",
        "oppo", "realme", "oneplus",
        "vivo", "iqoo",
        "meizu", "lenovo", "zte", "nubia", "asus",
    )

    /** 各厂商自启动管理页候选组件（依次尝试，系统里存在该组件才使用） */
    private val CANDIDATES: List<Pair<String, ComponentName>> = listOf(
        // 小米系（MIUI/澎湃OS：小米、红米、POCO、黑鲨共用安全中心）
        "xiaomi" to ComponentName(
            "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        "redmi" to ComponentName(
            "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        "poco" to ComponentName(
            "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        "blackshark" to ComponentName(
            "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        // 华为（新版启动管理 / 旧版应用启动管理）
        "huawei" to ComponentName(
            "com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        "huawei" to ComponentName(
            "com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
        ),
        // 荣耀（独立后用 hihonor 包，早期共用华为系统管家）
        "honor" to ComponentName(
            "com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        "honor" to ComponentName(
            "com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        // OPPO/realme（ColorOS 安全中心）
        "oppo" to ComponentName(
            "com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        ),
        "oppo" to ComponentName(
            "com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"
        ),
        "realme" to ComponentName(
            "com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        ),
        // 一加（ColorOS 或氢 OS 安全中心）
        "oneplus" to ComponentName(
            "com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        ),
        "oneplus" to ComponentName(
            "com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
        ),
        // vivo/iQOO（权限管理器后台弹出管理）
        "vivo" to ComponentName(
            "com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
        ),
        "vivo" to ComponentName(
            "com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
        ),
        "iqoo" to ComponentName(
            "com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
        ),
        // 魅族（Flyme 安全中心）
        "meizu" to ComponentName(
            "com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"
        ),
        // 联想（联想管家）
        "lenovo" to ComponentName(
            "com.lenovo.security", "com.lenovo.security.purebackground.PureBackgroundActivity"
        ),
        // 中兴/努比亚
        "zte" to ComponentName(
            "com.zte.heartyservice", "com.zte.heartyservice.autorun.AppAutoRunManagerActivity"
        ),
        "nubia" to ComponentName(
            "com.zte.heartyservice", "com.zte.heartyservice.autorun.AppAutoRunManagerActivity"
        ),
        // 华硕（ROG 手机管家）
        "asus" to ComponentName(
            "com.asus.mobilemanager", "com.asus.mobilemanager.mainfunction.MobileManagerMainActivity"
        ),
    )

    /** 当前设备是否属于需要引导自启动设置的厂商 */
    fun isRestrictiveManufacturer(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase().trim()
        val brand = Build.BRAND.lowercase().trim()
        return RESTRICTIVE.any { manufacturer.contains(it) || brand.contains(it) }
    }

    /**
     * 返回本厂商的自启动设置页 Intent；候选组件均不存在时兜底跳应用详情页。
     */
    fun settingsIntent(context: Context): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase().trim()
        val brand = Build.BRAND.lowercase().trim()
        val candidates = CANDIDATES.filter { (key, _) ->
            manufacturer.contains(key) || brand.contains(key)
        }
        for ((_, component) in candidates) {
            try {
                val intent = Intent()
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (context.packageManager.resolveActivity(intent, 0) != null) return intent
            } catch (_: Exception) {
                // 该候选组件不可用，尝试下一个
            }
        }
        // 兜底：应用详情页（用户可从「电池/权限」入口进入自启动管理）
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
