package com.example

import com.example.util.LunarCalendarUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LunarCalendarTest {

    @Test
    fun testSolarToLunarConversion() {
        // Test Mid-Autumn Festival 2026: 2026-09-25 is Lunar 8-15
        val lunar = LunarCalendarUtil.solarToLunar(2026, 9, 25)
        assertEquals(8, lunar.month)
        assertEquals(15, lunar.day)

        val festival = LunarCalendarUtil.getLunarFestival(lunar.month, lunar.day)
        assertEquals("中秋节", festival)
    }

    @Test
    fun testLunarNextSolarDate() {
        // Lunar 8-15 should calculate a valid next solar date in future or current year
        val nextSolar = LunarCalendarUtil.getNextSolarDateForLunar(8, 15)
        assertTrue(nextSolar.first >= 2026)
        assertTrue(nextSolar.second in 1..12)
        assertTrue(nextSolar.third in 1..31)
    }
}
