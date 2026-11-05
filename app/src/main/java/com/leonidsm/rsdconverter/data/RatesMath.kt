package com.leonidsm.rsdconverter.data

import java.time.Instant
import java.time.ZoneId

/**
 * Pure helpers behind the rate cache and the day-over-day indicator.
 * Kept free of Android and network types so they can be unit tested directly.
 */

fun localDateOf(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) =
    Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()

/**
 * Folds a freshly fetched [newSnapshot] into the cached [existing] record.
 *
 * When the cached snapshot comes from an earlier calendar day it becomes the
 * "previous day" baseline for the change indicator; otherwise the existing
 * baseline is carried over untouched, so several refreshes within one day do
 * not erase yesterday's reference point.
 */
fun buildRecord(
    existing: RatesRecord?,
    newSnapshot: RatesSnapshot,
    zoneId: ZoneId = ZoneId.systemDefault(),
): RatesRecord {
    val isNewDay = existing != null &&
        localDateOf(existing.current.fetchedAtEpochMillis, zoneId) !=
        localDateOf(newSnapshot.fetchedAtEpochMillis, zoneId)

    return if (isNewDay) {
        RatesRecord(
            current = newSnapshot,
            previousDayRates = existing!!.current.rates,
            previousDayEpochMillis = existing.current.fetchedAtEpochMillis,
        )
    } else {
        RatesRecord(
            current = newSnapshot,
            previousDayRates = existing?.previousDayRates,
            previousDayEpochMillis = existing?.previousDayEpochMillis,
        )
    }
}

/**
 * Day-over-day change in percent, or null when there is no usable baseline.
 */
fun percentChange(current: Double?, previous: Double?): Double? {
    if (current == null || previous == null || previous == 0.0) return null
    return (current - previous) / previous * 100.0
}

/** How much of [base] one unit of the target currency is worth. */
fun reverseRate(rate: Double?): Double? {
    if (rate == null || rate == 0.0) return null
    return 1.0 / rate
}

/**
 * Keeps only digits and a single decimal separator, normalising ',' to '.'.
 * Used to constrain the amount field without fighting the soft keyboard.
 */
fun sanitizeAmountInput(raw: String): String {
    val normalized = raw.replace(',', '.')
    val firstDotIndex = normalized.indexOf('.')
    return normalized.filterIndexed { index, c ->
        c.isDigit() || (c == '.' && index == firstDotIndex)
    }
}
