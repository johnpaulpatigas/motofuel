package com.johnpaulpatigas.motofuel.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class FormatUtilsTest {

    @Test
    fun formatCurrencyAmount_formatsTwoDecimalsProperly() {
        assertEquals("250.00", FormatUtils.formatCurrencyAmount(250.0))
        assertEquals("62.50", FormatUtils.formatCurrencyAmount(62.5))
        assertEquals("124.99", FormatUtils.formatCurrencyAmount(124.99))
        assertEquals("125.00", FormatUtils.formatCurrencyAmount(124.995))
    }

    @Test
    fun formatTwoDecimals_formatsCleanly() {
        assertEquals("1.85", FormatUtils.formatTwoDecimals(1.854))
        assertEquals("1.86", FormatUtils.formatTwoDecimals(1.856))
        assertEquals("2.0", FormatUtils.formatTwoDecimals(2.0))
        assertEquals("3.80", FormatUtils.formatTwoDecimalsFixed(3.8))
        assertEquals("15.25", FormatUtils.formatTwoDecimalsFixed(15.25))
    }

    @Test
    fun formatOneDecimal_and_formatKm_behaveConsistently() {
        assertEquals("35.4", FormatUtils.formatOneDecimal(35.42))
        assertEquals("35.5", FormatUtils.formatOneDecimal(35.47))
        assertEquals("12500", FormatUtils.formatKm(12500.0))
        assertEquals("12500.5", FormatUtils.formatKm(12500.5))
        assertEquals("15", FormatUtils.formatClean(15.0))
        assertEquals("15.2", FormatUtils.formatClean(15.2))
        assertEquals("30", FormatUtils.formatOneDecimalClean(30.0))
        assertEquals("30.5", FormatUtils.formatOneDecimalClean(30.54))
    }

    @Test
    fun calculateTotalCost_precisionAndEdgeCases() {
        val testCalculation = { litersStr: String, priceStr: String ->
            val liters = litersStr.toDoubleOrNull()
            val price = priceStr.toDoubleOrNull()
            if (liters != null && price != null && liters > 0.0 && price > 0.0) {
                val litersBd = BigDecimal.valueOf(liters)
                val priceBd = BigDecimal.valueOf(price)
                litersBd.multiply(priceBd).setScale(2, RoundingMode.HALF_UP).toDouble()
            } else {
                null
            }
        }

        // Standard positive input
        assertEquals(237.50, testCalculation("3.8", "62.50")!!, 0.001)

        // Floating point accuracy check (3.8 * 62.55 = 237.69)
        assertEquals(237.69, testCalculation("3.8", "62.55")!!, 0.001)

        // Zero, negative, invalid, or empty checks
        assertEquals(null, testCalculation("", "62.50"))
        assertEquals(null, testCalculation("3.8", ""))
        assertEquals(null, testCalculation("0", "62.50"))
        assertEquals(null, testCalculation("3.8", "0"))
        assertEquals(null, testCalculation("-3.8", "62.50"))
        assertEquals(null, testCalculation("3.8", "-62.50"))
        assertEquals(null, testCalculation("abc", "62.50"))
    }
}
