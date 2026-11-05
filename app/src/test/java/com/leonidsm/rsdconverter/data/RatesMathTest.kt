package com.leonidsm.rsdconverter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class RatesMathTest {

    private val utc = ZoneId.of("UTC")

    // --- sanitizeAmountInput ---

    @Test
    fun sanitize_keepsDigits() {
        assertEquals("1234", sanitizeAmountInput("1234"))
    }

    @Test
    fun sanitize_normalisesCommaToDot() {
        assertEquals("12.5", sanitizeAmountInput("12,5"))
    }

    @Test
    fun sanitize_keepsOnlyFirstSeparator() {
        // "1.2.3" and "1,2,3" must collapse to a single decimal point.
        assertEquals("1.23", sanitizeAmountInput("1.2.3"))
        assertEquals("1.23", sanitizeAmountInput("1,2,3"))
    }

    @Test
    fun sanitize_dropsLetters() {
        assertEquals("100", sanitizeAmountInput("1a0b0"))
    }

    @Test
    fun sanitize_allowsLeadingDot() {
        assertEquals(".5", sanitizeAmountInput(".5"))
    }

    @Test
    fun sanitize_empty() {
        assertEquals("", sanitizeAmountInput(""))
    }

    // --- percentChange ---

    @Test
    fun percent_rise() {
        assertEquals(10.0, percentChange(current = 110.0, previous = 100.0)!!, 1e-9)
    }

    @Test
    fun percent_fall() {
        assertEquals(-5.0, percentChange(current = 95.0, previous = 100.0)!!, 1e-9)
    }

    @Test
    fun percent_nullWhenNoBaseline() {
        assertNull(percentChange(current = 100.0, previous = null))
        assertNull(percentChange(current = null, previous = 100.0))
    }

    @Test
    fun percent_nullWhenPreviousZero() {
        assertNull(percentChange(current = 100.0, previous = 0.0))
    }

    // --- reverseRate ---

    @Test
    fun reverse_inverts() {
        assertEquals(0.25, reverseRate(4.0)!!, 1e-9)
    }

    @Test
    fun reverse_nullOnZeroOrNull() {
        assertNull(reverseRate(0.0))
        assertNull(reverseRate(null))
    }

    // --- buildRecord day rollover ---

    private fun snapshot(base: String, rate: Double, atMillis: Long) = RatesSnapshot(
        base = base,
        rates = mapOf("EUR" to rate),
        fetchedAtEpochMillis = atMillis,
    )

    private val day1Noon = 1_700_000_000_000L // some fixed instant
    private val day1Later = day1Noon + 3_600_000L // +1h, same UTC day
    private val day2 = day1Noon + 30L * 3_600_000L // +30h, next UTC day

    @Test
    fun buildRecord_firstEverHasNoBaseline() {
        val record = buildRecord(existing = null, newSnapshot = snapshot("RSD", 0.0085, day1Noon), zoneId = utc)
        assertNull(record.previousDayRates)
    }

    @Test
    fun buildRecord_sameDayKeepsExistingBaseline() {
        val first = buildRecord(null, snapshot("RSD", 0.0085, day1Noon), utc)
        val second = buildRecord(first, snapshot("RSD", 0.0086, day1Later), utc)
        // Still the same calendar day: baseline must not be overwritten with today's value.
        assertNull(second.previousDayRates)
        assertEquals(0.0086, second.current.rates.getValue("EUR"), 1e-9)
    }

    @Test
    fun buildRecord_newDayRollsPreviousIntoBaseline() {
        val first = buildRecord(null, snapshot("RSD", 0.0085, day1Noon), utc)
        val next = buildRecord(first, snapshot("RSD", 0.0090, day2), utc)
        assertEquals(0.0085, next.previousDayRates!!.getValue("EUR"), 1e-9)
        assertEquals(0.0090, next.current.rates.getValue("EUR"), 1e-9)
    }

    @Test
    fun buildRecord_baselineSurvivesMultipleSameDayRefreshesAfterRollover() {
        val d1 = buildRecord(null, snapshot("RSD", 0.0085, day1Noon), utc)
        val d2 = buildRecord(d1, snapshot("RSD", 0.0090, day2), utc)
        val d2Again = buildRecord(d2, snapshot("RSD", 0.0091, day2 + 3_600_000L), utc)
        // Yesterday's reference point stays put across intraday refreshes.
        assertEquals(0.0085, d2Again.previousDayRates!!.getValue("EUR"), 1e-9)
    }
}
