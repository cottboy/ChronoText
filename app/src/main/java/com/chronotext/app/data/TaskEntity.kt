package com.chronotext.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 定时任务
 *
 * 周期类型 [repeatType] 的字段复用约定：
 * - 单次：使用 oneShotYear + month + day + hour/minute
 * - 每年公历：month + day + hour/minute（2月29日在平年自动落到2月28日）
 * - 每年农历：month + day + hour/minute，[leapMonth] 表示农历闰月，
 *   [leapFallToRegular] 表示当年没有对应闰月时是否落到普通月
 * - 每 N 天：anchorEpochDay（锚点日）+ intervalDays + hour/minute
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 任务名称 */
    val name: String,
    /** 收件人姓名（通讯录选择时快照，可空串） */
    val recipientName: String = "",
    /** 收件人号码 */
    val recipientPhone: String,
    /** 短信内容 */
    val content: String,
    /** 发信卡订阅 ID，-1 表示系统默认卡 */
    val subscriptionId: Int = -1,
    /** 周期类型，取值见 [RepeatType] */
    val repeatType: Int,
    /** 单次任务：年 */
    val oneShotYear: Int = 0,
    /** 单次/每年公历/每年农历：月 */
    val month: Int = 1,
    /** 单次/每年公历/每年农历：日 */
    val day: Int = 1,
    /** 每年农历：是否闰月 */
    val leapMonth: Boolean = false,
    /** 每年农历：当年无该闰月时落到普通月（false 则跳过当年） */
    val leapFallToRegular: Boolean = true,
    /** 每 N 天：间隔天数 */
    val intervalDays: Int = 1,
    /** 每 N 天：锚点日期（epoch day） */
    val anchorEpochDay: Long = 0,
    /** 触发时刻 */
    val hour: Int = 9,
    /** 触发分钟 */
    val minute: Int = 0,
    /** 发送前提醒天数：0 不提醒 / 1 提前1天 / 3 提前3天 */
    val reminderDaysBefore: Int = 0,
    /** 是否启用 */
    val enabled: Boolean = true,
    /** 下次发送时间戳（毫秒），<=0 表示当前无下一次 */
    val nextTriggerAt: Long = -1,
    /** 创建时间 */
    val createdAt: Long = 0,
)

/** 周期类型常量 */
object RepeatType {
    /** 单次 */
    const val ONCE = 0
    /** 每年公历 */
    const val YEARLY_SOLAR = 1
    /** 每年农历 */
    const val YEARLY_LUNAR = 2
    /** 每 N 天 */
    const val EVERY_N_DAYS = 3
}

/** 发送记录状态 */
object SendStatus {
    /** 发送成功 */
    const val SUCCESS = 0
    /** 最终失败 */
    const val FAILED = 1
    /** 已发起，等待回执 / 重试中 */
    const val PENDING = 2
    /** 错过触发窗口，已跳过 */
    const val SKIPPED = 3
}
