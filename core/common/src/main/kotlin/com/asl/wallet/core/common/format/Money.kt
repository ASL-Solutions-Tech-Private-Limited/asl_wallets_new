package com.asl.wallet.core.common.format

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Money helpers. Amounts are always handled as [BigDecimal] (never Double) so rupees and
 * paise never drift through floating-point rounding.
 */
object Money {

    /** Parses user or API input such as "5,000", "₹ 48,250.75", "10" into rupees. Returns null if invalid. */
    fun parse(raw: String?): BigDecimal? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace("₹", "").replace(",", "").replace(" ", "").trim()
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null
        if (!cleaned.all { it.isDigit() || it == '.' || it == '-' }) return null
        return cleaned.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Indian digit grouping: 1,23,45,678.50
     * @param withSymbol prefix "₹ "
     * @param showPaise always show 2 decimals; when false, decimals are shown only if non-zero
     */
    fun format(amount: BigDecimal, withSymbol: Boolean = true, showPaise: Boolean = true): String {
        val scaled = amount.setScale(2, RoundingMode.HALF_UP)
        val negative = scaled.signum() < 0
        val abs = scaled.abs()
        val whole = abs.toBigInteger().toString()
        val paise = abs.remainder(BigDecimal.ONE).movePointRight(2).toInt()

        val grouped = groupIndian(whole)
        val decimals = when {
            showPaise -> "." + paise.toString().padStart(2, '0')
            paise != 0 -> "." + paise.toString().padStart(2, '0')
            else -> ""
        }
        val sign = if (negative) "−" else ""
        val symbol = if (withSymbol) "₹ " else ""
        return "$sign$symbol$grouped$decimals"
    }

    fun format(amount: Long, withSymbol: Boolean = true): String =
        format(BigDecimal.valueOf(amount), withSymbol, showPaise = false)

    internal fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val sb = StringBuilder()
        rest.reversed().chunked(2).forEachIndexed { i, chunk ->
            if (i > 0) sb.append(',')
            sb.append(chunk)
        }
        return sb.reverse().toString() + "," + last3
    }
}
