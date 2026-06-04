package cn.edu.shmtu.terminal.android.ui.statistics

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * 统计时间段枚举 - 对齐 Tauri StatisticsDialog buildParams
 *
 * 11 个固定区间 + 自定义:
 * today / recent7days / week / month / recent30days / quarter / halfYear / year / 3years / 4years / all / custom
 */
enum class StatisticsPeriod(val label: String) {
    TODAY("今天"),
    RECENT_7_DAYS("最近7天"),
    WEEK("本周"),
    MONTH("本月"),
    RECENT_30_DAYS("最近30天"),
    QUARTER("最近3个月"),
    HALF_YEAR("最近半年"),
    YEAR("最近一年"),
    THREE_YEARS("最近3年"),
    FOUR_YEARS("最近4年"),
    ALL("全部时间"),
    CUSTOM("自定义");

    /**
     * 解析为 (startDate, endDate) 字符串:
     * - startDate 使用 "yyyy-MM-dd"
     * - endDate 使用 "yyyy-MM-dd 23:59:59"
     */
    fun resolve(now: LocalDate = LocalDate.now()): Pair<String, String> {
        val end = now.format(DATE_END)
        val start = when (this) {
            TODAY -> now.format(DATE_ONLY)
            RECENT_7_DAYS -> now.minusDays(6).format(DATE_ONLY)
            WEEK -> now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).format(DATE_ONLY)
            MONTH -> YearMonth.from(now).atDay(1).format(DATE_ONLY)
            RECENT_30_DAYS -> now.minusDays(29).format(DATE_ONLY)
            QUARTER -> now.minusMonths(3).plusDays(1).format(DATE_ONLY)
            HALF_YEAR -> now.minusMonths(6).plusDays(1).format(DATE_ONLY)
            YEAR -> now.minusYears(1).plusDays(1).format(DATE_ONLY)
            THREE_YEARS -> now.minusYears(3).plusDays(1).format(DATE_ONLY)
            FOUR_YEARS -> now.minusYears(4).plusDays(1).format(DATE_ONLY)
            ALL -> "0001-01-01"
            CUSTOM -> now.format(DATE_ONLY)
        }
        return start to end
    }
}

private val DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val DATE_END = DateTimeFormatter.ofPattern("yyyy-MM-dd 23:59:59")
