package com.chronotext.app.util

import com.chronotext.app.data.RepeatType
import com.chronotext.app.data.TaskEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 文案格式化工具 */
object Format {

    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
    private val TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm")

    fun dateTime(millis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(DATE_TIME)

    fun time(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

    /** 周期描述，如「每年农历五月初五 09:00」 */
    fun describePeriod(task: TaskEntity): String = when (task.repeatType) {
        RepeatType.ONCE -> "单次 · %d年%d月%d日 %s".format(task.oneShotYear, task.month, task.day, time(task.hour, task.minute))
        RepeatType.YEARLY_SOLAR -> "每年公历 %d月%d日 %s".format(task.month, task.day, time(task.hour, task.minute))
        RepeatType.YEARLY_LUNAR -> {
            val leap = if (task.leapMonth) "闰" else ""
            "每年农历 $leap${lunarMonth(task.month)}月${lunarDay(task.day)} ${time(task.hour, task.minute)}"
        }
        RepeatType.EVERY_N_DAYS -> "每 ${task.intervalDays} 天 ${time(task.hour, task.minute)}"
        else -> ""
    }

    /** 提前提醒描述 */
    fun describeReminder(daysBefore: Int): String = when (daysBefore) {
        0 -> "不提醒"
        else -> "提前 $daysBefore 天提醒"
    }

    /** 农历月：正、二、三…冬、腊 */
    fun lunarMonth(month: Int): String = when (month) {
        1 -> "正"
        11 -> "冬"
        12 -> "腊"
        else -> cnNumber(month)
    }

    /** 农历日：初一…初十、十一…十九、二十、廿一…廿九、三十 */
    fun lunarDay(day: Int): String = when {
        day == 10 -> "初十"
        day == 20 -> "二十"
        day == 30 -> "三十"
        day < 10 -> "初${cnNumber(day)}"
        day < 20 -> "十${cnNumber(day - 10)}"
        else -> "廿${cnNumber(day - 20)}"
    }

    /** 一~十 的中文数字 */
    private fun cnNumber(n: Int): String =
        "一二三四五六七八九十".getOrElse(n - 1) { '?' }.toString()
}
