package com.chronotext.app

import com.chronotext.app.data.RepeatType
import com.chronotext.app.data.TaskEntity
import com.chronotext.app.schedule.NextRunCalculator
import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * 调度计算单元测试。
 *
 * 农历用例采用「与 lunar 库直接换算结果对照」的方式断言，
 * 避免把对农历日历的记忆硬编码进测试；
 * 其中 2026 年春节（公历 2026-02-17）作为固定事实校验，防止库行为异常。
 */
class NextRunCalculatorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Before
    fun setup() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    // ---------- 工具 ----------

    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun local(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    private fun makeTask(
        repeatType: Int,
        oneShotYear: Int = 0,
        month: Int = 1,
        day: Int = 1,
        hour: Int = 9,
        minute: Int = 0,
        leapMonth: Boolean = false,
        leapFallToRegular: Boolean = true,
        intervalDays: Int = 1,
        anchorEpochDay: Long = 0,
        enabled: Boolean = true,
    ) = TaskEntity(
        id = 1, name = "测试任务", recipientPhone = "10086", content = "hi",
        repeatType = repeatType, oneShotYear = oneShotYear, month = month, day = day,
        leapMonth = leapMonth, leapFallToRegular = leapFallToRegular,
        intervalDays = intervalDays, anchorEpochDay = anchorEpochDay,
        hour = hour, minute = minute, enabled = enabled,
    )

    /** 库对照：农历 y-m-d（m 负数闰月）对应公历日期的 h:min */
    private fun solarOf(lunarY: Int, lunarM: Int, lunarD: Int, h: Int = 9, min: Int = 0): LocalDateTime {
        val s: Solar = Lunar.fromYmd(lunarY, lunarM, lunarD).solar
        return LocalDateTime.of(s.year, s.month, s.day, h, min)
    }

    // ---------- 固定事实校验 ----------

    @Test
    fun `lunar库 sanity - 2026年春节为公历2月17日`() {
        assertEquals(LocalDate.of(2026, 2, 17), solarOf(2026, 1, 1).toLocalDate())
    }

    // ---------- 单次 ----------

    @Test
    fun `单次 - 未来时刻返回该时刻`() {
        val now = millis(2026, 10, 6, 10, 0)
        val task = makeTask(RepeatType.ONCE, oneShotYear = 2026, month = 10, day = 7, hour = 9)
        assertEquals(LocalDateTime.of(2026, 10, 7, 9, 0), local(NextRunCalculator.nextTrigger(task, now)))
    }

    @Test
    fun `单次 - 已过去返回NONE`() {
        val now = millis(2026, 10, 6, 10, 0)
        val task = makeTask(RepeatType.ONCE, oneShotYear = 2026, month = 10, day = 5, hour = 9)
        assertEquals(NextRunCalculator.NONE, NextRunCalculator.nextTrigger(task, now))
    }

    // ---------- 每年公历 ----------

    @Test
    fun `公历 - 今年已过顺延明年`() {
        val now = millis(2026, 10, 6, 10, 0)
        val task = makeTask(RepeatType.YEARLY_SOLAR, month = 2, day = 14)
        assertEquals(LocalDateTime.of(2027, 2, 14, 9, 0), local(NextRunCalculator.nextTrigger(task, now)))
    }

    @Test
    fun `公历 - 今年未过取今年`() {
        val now = millis(2026, 10, 6, 10, 0)
        val task = makeTask(RepeatType.YEARLY_SOLAR, month = 12, day = 25)
        assertEquals(LocalDateTime.of(2026, 12, 25, 9, 0), local(NextRunCalculator.nextTrigger(task, now)))
    }

    @Test
    fun `公历 - 平年2月29落到2月28`() {
        val now = millis(2026, 10, 6, 10, 0)
        val task = makeTask(RepeatType.YEARLY_SOLAR, month = 2, day = 29)
        // 2027 为平年 → 2/28；不跳到 2028
        assertEquals(LocalDateTime.of(2027, 2, 28, 9, 0), local(NextRunCalculator.nextTrigger(task, now)))
    }

    // ---------- 每年农历 ----------

    @Test
    fun `农历 - 正月初一取下一个春节`() {
        val now = millis(2026, 10, 6, 10, 0)
        val task = makeTask(RepeatType.YEARLY_LUNAR, month = 1, day = 1)
        val expected = solarOf(2027, 1, 1) // 2027 春节（对照库）
        assertEquals(expected, local(NextRunCalculator.nextTrigger(task, now)))
    }

    @Test
    fun `农历 - 春节当天未到时刻取当天`() {
        val now = millis(2026, 2, 17, 7, 0)
        val task = makeTask(RepeatType.YEARLY_LUNAR, month = 1, day = 1)
        assertEquals(LocalDateTime.of(2026, 2, 17, 9, 0), local(NextRunCalculator.nextTrigger(task, now)))
    }

    @Test
    fun `农历 - 指定闰月且当年有该闰月则用闰月`() {
        // 2025 年有闰六月
        val now = millis(2025, 1, 1, 0, 0)
        val task = makeTask(RepeatType.YEARLY_LUNAR, month = 6, day = 1, leapMonth = true)
        val expected = solarOf(2025, -6, 1) // 闰六月初一（对照库）
        assertEquals(expected, local(NextRunCalculator.nextTrigger(task, now)))
    }

    @Test
    fun `农历 - 指定闰月当年无该闰月且允许则落普通月`() {
        // 2026 年无闰六月：落普通六月初一
        val now = millis(2026, 1, 1, 0, 0)
        val task = makeTask(RepeatType.YEARLY_LUNAR, month = 6, day = 1, leapMonth = true, leapFallToRegular = true)
        val expected = solarOf(2026, 6, 1)
        assertEquals(expected, local(NextRunCalculator.nextTrigger(task, now)))
    }

    @Test
    fun `农历 - 指定闰月当年无该闰月且不允许则跳到下一个有闰月的年份`() {
        val now = millis(2026, 1, 1, 0, 0)
        val task = makeTask(RepeatType.YEARLY_LUNAR, month = 6, day = 1, leapMonth = true, leapFallToRegular = false)
        // 从 2027 起找第一个有闰六月的年份（对照库）
        var y = 2027
        while (com.nlf.calendar.LunarYear.fromYear(y).leapMonth != 6) y++
        val expected = solarOf(y, -6, 1)
        assertEquals(expected, local(NextRunCalculator.nextTrigger(task, now)))
    }

    // ---------- 每 N 天 ----------

    @Test
    fun `N天 - 锚点未来取锚点日`() {
        val now = millis(2026, 10, 6, 10, 0)
        val anchor = LocalDate.of(2026, 11, 1).toEpochDay()
        val task = makeTask(RepeatType.EVERY_N_DAYS, intervalDays = 7, anchorEpochDay = anchor)
        assertEquals(LocalDateTime.of(2026, 11, 1, 9, 0), local(NextRunCalculator.nextTrigger(task, now)))
    }

    @Test
    fun `N天 - 今天恰为槽位且时刻未过取今天`() {
        val anchor = LocalDate.of(2026, 1, 1).toEpochDay()
        // 2026-10-04 距 2026-01-01 为 276 天，276 % 12 == 0 → 今天是槽位
        val today = LocalDate.of(2026, 10, 4)
        assertEquals(0, (today.toEpochDay() - anchor) % 12)
        val now = millis(2026, 10, 4, 8, 0)
        val task = makeTask(RepeatType.EVERY_N_DAYS, intervalDays = 12, anchorEpochDay = anchor)
        assertEquals(LocalDateTime.of(2026, 10, 4, 9, 0), local(NextRunCalculator.nextTrigger(task, now)))
    }

    @Test
    fun `N天 - 今天槽位时刻已过取下一槽位`() {
        val anchor = LocalDate.of(2026, 1, 1).toEpochDay()
        val today = LocalDate.of(2026, 10, 4)
        assertEquals(0, (today.toEpochDay() - anchor) % 12)
        val now = millis(2026, 10, 4, 10, 0)
        val task = makeTask(RepeatType.EVERY_N_DAYS, intervalDays = 12, anchorEpochDay = anchor)
        val next = local(NextRunCalculator.nextTrigger(task, now))
        assertEquals(today.plusDays(12).atTime(9, 0), next)
    }

    @Test
    fun `N天 - 任意槽位与锚点等差对齐`() {
        val anchor = LocalDate.of(2026, 1, 1).toEpochDay()
        val now = millis(2026, 10, 6, 10, 0)
        val task = makeTask(RepeatType.EVERY_N_DAYS, intervalDays = 7, anchorEpochDay = anchor)
        val result = NextRunCalculator.nextTrigger(task, now)
        assertTrue(result > now)
        val resultDay = local(result).toLocalDate().toEpochDay()
        assertEquals(0, (resultDay - anchor) % 7)
        // 且是第一个满足的槽位：前一个槽位应已过去
        assertTrue(resultDay - 7 <= local(now).toLocalDate().toEpochDay())
    }
}
