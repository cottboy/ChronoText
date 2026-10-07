package com.chronotext.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 发送记录。收件人、内容、任务名均做快照存储，
 * 任务删除后历史记录依然完整可读。
 */
@Entity(tableName = "send_logs")
data class SendLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 关联任务 ID */
    val taskId: Long,
    /** 任务名快照 */
    val taskName: String,
    /** 收件人快照 */
    val recipientPhone: String,
    /** 内容快照 */
    val content: String,
    /** 发信卡订阅 ID */
    val subscriptionId: Int,
    /** 计划发送时间 */
    val scheduledAt: Long,
    /** 实际发送/进入终态时间 */
    val actualAt: Long = 0,
    /** 状态，取值见 [SendStatus] */
    val status: Int,
    /** 结果说明（成功/失败原因/重试说明） */
    val detail: String = "",
    /** 第几次尝试（1 起） */
    val attempt: Int = 1,
    /** 最近一次活动时间（建记录或开始新尝试），自愈核对据此判断发送链路是否卡死 */
    val lastActivityAt: Long = 0,
)
