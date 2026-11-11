package com.johnpaulpatigas.motofuel.util

import kotlin.math.roundToInt

/**
 * Standard numerical and string formatting utilities for MotoFuel.
 */
object FormatUtils {

    /**
     * Format a double to one decimal place, e.g. 35.4
     */
    fun formatOneDecimal(value: Double): String {
        return ((value * 10).roundToInt() / 10.0).toString()
    }

    /**
     * Format kilometer reading, e.g. 12500 or 12500.5
     */
    fun formatKm(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }

    /**
     * Format numbers cleanly without trailing .0 if integer, e.g. 15 instead of 15.0
     */
    fun formatClean(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    /**
     * Format currency amount with two decimal places, e.g. 250.00
     */
    fun formatCurrencyAmount(value: Double): String {
        val rounded = (java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP))
        return String.format(java.util.Locale.US, "%.2f", rounded.toDouble())
    }

    /**
     * Format a double to one decimal place, omitting trailing .0 if integer, e.g. 30 instead of 30.0
     */
    fun formatOneDecimalClean(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }
}
