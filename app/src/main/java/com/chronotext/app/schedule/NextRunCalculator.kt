package com.chronotext.app.schedule

import com.chronotext.app.data.RepeatType
import com.chronotext.app.data.TaskEntity
import com.nlf.calendar.Lunar
import com.nlf.calendar.LunarYear
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.DateTimeException

/**
 * 「下一次触发时间」计算器。纯函数，可 JVM 单测。
 *
 * 设计要点：
 * - 全部以本地时区的墙钟时刻为准，换算成绝对时间戳（epoch millis）；
 * - 周期任务在每次发送结束后由调用方传入"当前时间"再算下一次，
 *   因此系统时间被调整后重算也天然正确；
 * - 农历换算使用 lunar 库（纯离线，支持 0001—9999 年）；
 * - 「月末日」统一向前一天落：公历 2/29 在平年落 2/28，
 *   农历三十遇到小月落廿九，保证每年都发、绝不跳过。
 */
object NextRunCalculator {

    /** 表示没有下一次（单次已完成） */
    const val NONE = -1L

    /**
     * 计算任务在 [nowMillis] 之后的下一次触发时间戳。
     * 若任务未启用或不存在下一次，返回 [NONE]。
     */
    fun nextTrigger(task: TaskEntity, nowMillis: Long): Long {
        if (!task.enabled) return NONE
        return when (task.repeatType) {
            RepeatType.ONCE -> nextOneShot(task, nowMillis)
            RepeatType.YEARLY_SOLAR -> nextYearlySolar(task, nowMillis)
            RepeatType.YEARLY_LUNAR -> nextYearlyLunar(task, nowMillis)
            RepeatType.EVERY_N_DAYS -> nextEveryNDays(task, nowMillis)
            else -> NONE
        }
    }

    /** 单次：指定时刻尚未到达则返回该时刻，否则视为已完成 */
    fun nextOneShot(task: TaskEntity, nowMillis: Long): Long {
        val t = atTime(task.oneShotYear, task.month, task.day, task.hour, task.minute) ?: return NONE
        return if (t.toInstant().toEpochMilli() > nowMillis) t.toInstant().toEpochMilli() else NONE
    }

    /** 每年公历：今年的时刻已过则顺延到明年 */
    fun nextYearlySolar(task: TaskEntity, nowMillis: Long): Long {
        val currentYear = Instant.ofEpochMilli(nowMillis).atZone(currentZone()).year
        for (offset in 0..200) {
            val year = currentYear + offset
            val date = resolveSolarDate(year, task.month, task.day) ?: return NONE
            val t = atTime(date.year, date.monthValue, date.dayOfMonth, task.hour, task.minute) ?: return NONE
            if (t.toInstant().toEpochMilli() > nowMillis) return t.toInstant().toEpochMilli()
        }
        return NONE
    }

    /**
     * 每年农历：从当年开始逐农历年向后找。
     * 闰月处理：任务指定闰月但当年没有该闰月时，
     * - [TaskEntity.leapFallToRegular] 为 true：落到同序号普通月；
     * - 为 false：跳过该农历年。
     */
    fun nextYearlyLunar(task: TaskEntity, nowMillis: Long): Long {
        val currentYear = Instant.ofEpochMilli(nowMillis).atZone(currentZone()).year
        for (offset in 0..200) {
            val lunarYear = currentYear + offset
            val leapInYear = LunarYear.fromYear(lunarYear).leapMonth // 0 表示该年无闰月
            val lunarMonth: Int = when {
                !task.leapMonth -> task.month
                leapInYear == task.month -> -task.month // lunar 库约定：负数表示闰月
                task.leapFallToRegular -> task.month
                else -> continue // 指定闰月、当年没有、又不落普通月 → 跳过该年
            }
            val solar = lunarToSolar(lunarYear, lunarMonth, task.day, task.hour, task.minute) ?: continue
            if (solar.toInstant().toEpochMilli() > nowMillis) return solar.toInstant().toEpochMilli()
        }
        return NONE
    }

    /**
     * 每 N 天：以锚点日为基准的等差序列，
     * 取第一个「日期不早于今天且时刻尚未过去」的触发点。
     */
    fun nextEveryNDays(task: TaskEntity, nowMillis: Long): Long {
        if (task.intervalDays <= 0) return NONE
        val zone = currentZone()
        val todayEpochDay = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()
        var k = 0L
        if (todayEpochDay > task.anchorEpochDay) {
            // 直接解出使 anchor + k*interval >= today 的最小 k
            k = (todayEpochDay - task.anchorEpochDay + task.intervalDays - 1) / task.intervalDays
        }
        // 正常情况循环不超过 2 次，上限仅防御非法数据
        repeat(100_000) {
            val t = atTimeOfEpochDay(task.anchorEpochDay + k * task.intervalDays, task.hour, task.minute, zone)
            if (t != null && t.toInstant().toEpochMilli() > nowMillis) return t.toInstant().toEpochMilli()
            k++
        }
        return NONE
    }

    // ---------- 内部工具 ----------

    private fun currentZone(): ZoneId = ZoneId.systemDefault()

    /** 指定年月日时刻；日期非法（如 2/30）返回 null */
    private fun atTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime? =
        runCatching { LocalDateTime.of(year, month, day, hour, minute).atZone(currentZone()) }.getOrNull()

    private fun atTimeOfEpochDay(epochDay: Long, hour: Int, minute: Int, zone: ZoneId): ZonedDateTime? {
        val date = LocalDate.ofEpochDay(epochDay)
        return runCatching { LocalDateTime.of(date.year, date.monthValue, date.dayOfMonth, hour, minute).atZone(zone) }.getOrNull()
    }

    /** 公历日期；2/29 在平年落 2/28，其余非法日期返回 null */
    private fun resolveSolarDate(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            if (month == 2 && day == 29) LocalDate.of(year, 2, 28) else null
        }

    /** 农历（month 为负表示闰月）转公历时刻；三十遇到小月落廿九，无法换算返回 null */
    private fun lunarToSolar(lunarYear: Int, lunarMonth: Int, lunarDay: Int, hour: Int, minute: Int): ZonedDateTime? =
        runCatching {
            val solar = try {
                Lunar.fromYmd(lunarYear, lunarMonth, lunarDay).solar
            } catch (e: Exception) {
                if (lunarDay == 30) Lunar.fromYmd(lunarYear, lunarMonth, 29).solar else throw e
            }
            LocalDateTime.of(solar.year, solar.month, solar.day, hour, minute).atZone(currentZone())
        }.getOrNull()
}
