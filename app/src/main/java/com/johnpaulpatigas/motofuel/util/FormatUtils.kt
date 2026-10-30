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
     * Format kilometer reading, e.g. 12500.5
     */
    fun formatKm(value: Double): String {
        return ((value * 10).roundToInt() / 10.0).toString()
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
}
