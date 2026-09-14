package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class LunarDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeap: Boolean = false
) {
    fun toChineseString(): String {
        val monthStr = (if (isLeap) "闰" else "") + LunarCalendarUtil.getLunarMonthName(month)
        val dayStr = LunarCalendarUtil.getLunarDayName(day)
        return "农历$monthStr$dayStr"
    }

    fun toShortChineseString(): String {
        return if (day == 1) {
            (if (isLeap) "闰" else "") + LunarCalendarUtil.getLunarMonthName(month)
        } else {
            LunarCalendarUtil.getLunarDayName(day)
        }
    }
}

object LunarCalendarUtil {
    // Standard astronomical lunar information array for years 1900 to 2100
    // Format: 0x0xxxx: 4 bits leap month, 12 bits days of months (1: 30 days, 0: 29 days), 4 bits leap month days (1: 30, 0: 29)
    private val LUNAR_INFO = longArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950, 0x06aa0,
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b5a0, 0x195a6,
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0,
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
        0x05aa0, 0x076a3, 0x096d0, 0x04bd7, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0
    )

    private val MONTH_NAMES = arrayOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    )

    private val DAY_NAMES = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    fun getLunarMonthName(month: Int): String {
        return if (month in 1..12) MONTH_NAMES[month - 1] else "${month}月"
    }

    fun getLunarDayName(day: Int): String {
        return if (day in 1..30) DAY_NAMES[day - 1] else "${day}日"
    }

    // Number of days in a lunar year
    private fun lYearDays(y: Int): Int {
        var sum = 348
        val info = LUNAR_INFO[y - 1900]
        var i = 0x8000
        while (i > 0x8) {
            if ((info and i.toLong()) != 0L) sum += 1
            i = i shr 1
        }
        return sum + leapDays(y)
    }

    // Leap month in a lunar year (0 if none)
    private fun leapMonth(y: Int): Int {
        return (LUNAR_INFO[y - 1900] and 0xf).toInt()
    }

    // Days in leap month of lunar year
    private fun leapDays(y: Int): Int {
        return if (leapMonth(y) != 0) {
            if ((LUNAR_INFO[y - 1900] and 0x10000) != 0L) 30 else 29
        } else 0
    }

    // Days in a specific lunar month
    private fun monthDays(y: Int, m: Int): Int {
        return if ((LUNAR_INFO[y - 1900] and (0x10000 shr m).toLong()) == 0L) 29 else 30
    }

    /**
     * Converts Solar (Gregorian) date to Lunar date
     */
    fun solarToLunar(year: Int, month: Int, day: Int): LunarDate {
        val baseCalendar = Calendar.getInstance().apply {
            set(1900, 0, 31, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetCalendar = Calendar.getInstance().apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var offset = ((targetCalendar.timeInMillis - baseCalendar.timeInMillis) / 86400000L).toInt()

        var lYear = 1900
        var daysInYear = 0
        while (lYear < 2050 && offset > 0) {
            daysInYear = lYearDays(lYear)
            if (offset < daysInYear) break
            offset -= daysInYear
            lYear++
        }

        val leap = leapMonth(lYear)
        var isLeap = false
        var lMonth = 1
        while (lMonth <= 12 && offset > 0) {
            val daysInMonth = monthDays(lYear, lMonth)
            if (leap > 0 && lMonth == (leap + 1) && !isLeap) {
                --lMonth
                isLeap = true
                val leapMonthDays = leapDays(lYear)
                if (offset < leapMonthDays) break
                offset -= leapMonthDays
            } else {
                if (offset < daysInMonth) break
                offset -= daysInMonth
            }
            if (isLeap && lMonth == (leap + 1)) isLeap = false
            lMonth++
        }

        val lDay = offset + 1
        return LunarDate(lYear, lMonth, lDay, isLeap)
    }

    /**
     * Converts a Lunar date (year, month, day) to Gregorian (solar) Calendar
     */
    fun lunarToSolar(lYear: Int, lMonth: Int, lDay: Int, isLeap: Boolean = false): Triple<Int, Int, Int> {
        val baseCalendar = Calendar.getInstance().apply {
            set(1900, 0, 31, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var offset = 0L

        for (y in 1900 until lYear) {
            offset += lYearDays(y)
        }

        val leap = leapMonth(lYear)
        for (m in 1 until lMonth) {
            offset += monthDays(lYear, m)
            if (leap in 1 until lMonth && m == leap) {
                offset += leapDays(lYear)
            }
        }

        if (isLeap && leap == lMonth) {
            offset += monthDays(lYear, lMonth)
        }

        offset += (lDay - 1)

        val targetTime = baseCalendar.timeInMillis + offset * 86400000L
        val resultCal = Calendar.getInstance().apply {
            timeInMillis = targetTime
        }
        return Triple(
            resultCal.get(Calendar.YEAR),
            resultCal.get(Calendar.MONTH) + 1,
            resultCal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Finds the next Gregorian date for a recurring lunar birthday.
     * Checks current Gregorian year, or next Gregorian year if already passed.
     */
    fun getNextSolarDateForLunar(lunarMonth: Int, lunarDay: Int): Triple<Int, Int, Int> {
        val today = Calendar.getInstance()
        val currentYear = today.get(Calendar.YEAR)

        // Try this year first
        val thisYearSolar = lunarToSolar(currentYear, lunarMonth, lunarDay)
        val candidateCal = Calendar.getInstance().apply {
            set(thisYearSolar.first, thisYearSolar.second - 1, thisYearSolar.third, 23, 59, 59)
        }

        return if (candidateCal.timeInMillis >= today.timeInMillis) {
            thisYearSolar
        } else {
            // Next lunar year
            lunarToSolar(currentYear + 1, lunarMonth, lunarDay)
        }
    }

    /**
     * Calculates remaining days to a target date string "YYYY-MM-DD"
     */
    fun calculateDaysUntil(year: Int, month: Int, day: Int): Int {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val target = Calendar.getInstance().apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diff = target.timeInMillis - today.timeInMillis
        return (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    }

    /**
     * Traditional festival lookup by lunar date
     */
    fun getLunarFestival(month: Int, day: Int): String? {
        return when {
            month == 1 && day == 1 -> "春节"
            month == 1 && day == 15 -> "元宵节"
            month == 5 && day == 5 -> "端午节"
            month == 7 && day == 7 -> "七夕节"
            month == 7 && day == 15 -> "中元节"
            month == 8 && day == 15 -> "中秋节"
            month == 9 && day == 9 -> "重阳节"
            month == 12 && day == 8 -> "腊八节"
            month == 12 && (day == 29 || day == 30) -> "除夕"
            else -> null
        }
    }
}
