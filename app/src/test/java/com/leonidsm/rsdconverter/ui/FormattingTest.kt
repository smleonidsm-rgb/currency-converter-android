package com.leonidsm.rsdconverter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    private val now = 1_700_000_000_000L
    private fun minutesAgo(n: Long) = formatRelativeTime(now - n * 60_000L, now)
    private fun hoursAgo(n: Long) = formatRelativeTime(now - n * 3_600_000L, now)

    @Test
    fun justNow() {
        assertEquals("только что", minutesAgo(0))
    }

    @Test
    fun minutes_russianPlurals() {
        assertEquals("1 минуту назад", minutesAgo(1))
        assertEquals("2 минуты назад", minutesAgo(2))
        assertEquals("5 минут назад", minutesAgo(5))
        assertEquals("11 минут назад", minutesAgo(11)) // 11-14 always "минут"
        assertEquals("21 минуту назад", minutesAgo(21))
    }

    @Test
    fun hours_russianPlurals() {
        assertEquals("1 час назад", hoursAgo(1))
        assertEquals("2 часа назад", hoursAgo(2))
        assertEquals("5 часов назад", hoursAgo(5))
        assertEquals("11 часов назад", hoursAgo(11))
        assertEquals("21 час назад", hoursAgo(21))
    }

    @Test
    fun futureTimestampClampsToJustNow() {
        // A clock skew that puts the fetch slightly in the future must not go negative.
        assertEquals("только что", formatRelativeTime(now + 5_000L, now))
    }
}
