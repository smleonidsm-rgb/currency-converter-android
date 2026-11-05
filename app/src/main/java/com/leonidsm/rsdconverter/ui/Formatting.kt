package com.leonidsm.rsdconverter.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val amountFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("ru")))
private val rateFormat = DecimalFormat("#,##0.0000", DecimalFormatSymbols(Locale("ru")))
private val percentFormat = DecimalFormat("0.00", DecimalFormatSymbols(Locale("ru")))

fun formatAmount(value: Double): String = amountFormat.format(value)

fun formatRate(value: Double): String = rateFormat.format(value)

fun formatPercent(value: Double): String = percentFormat.format(value) + "%"

private fun pluralize(n: Long, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1L -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

fun formatRelativeTime(fetchedAtEpochMillis: Long, nowEpochMillis: Long = System.currentTimeMillis()): String {
    val diffMinutes = ((nowEpochMillis - fetchedAtEpochMillis) / 60000L).coerceAtLeast(0)
    return when {
        diffMinutes < 1 -> "только что"
        diffMinutes < 60 -> "$diffMinutes " + pluralize(diffMinutes, "минуту", "минуты", "минут") + " назад"
        else -> {
            val hours = diffMinutes / 60
            "$hours " + pluralize(hours, "час", "часа", "часов") + " назад"
        }
    }
}
