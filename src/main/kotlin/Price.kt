package cc.makin.coinmonitor

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private val FORMAT_LOCALE: Locale = Locale.US

data class Price(
    val value: Double,
    val currency: Currency,
)

operator fun Price.compareTo(other: Price): Int =
    if (other.currency != this.currency) {
        throw IllegalStateException("Different currencies")
    } else {
        this.value.compareTo(other.value)
    }

operator fun Price.plus(other: Price): Price =
    if (other.currency != this.currency) {
        throw IllegalStateException("Different currencies")
    } else {
        Price(this.value + other.value, this.currency)
    }

fun Price.format(locale: Locale = FORMAT_LOCALE): String = this
    .getFormatter(locale)
    .format(this.value)

private fun Price.getFormatter(locale: Locale = FORMAT_LOCALE) = run {
    val format = NumberFormat.getCurrencyInstance(locale)
    format.currency = currency
    format.maximumFractionDigits = 12
    format
}