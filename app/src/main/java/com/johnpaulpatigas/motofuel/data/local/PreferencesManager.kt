package com.johnpaulpatigas.motofuel.data.local

import android.content.Context
import com.johnpaulpatigas.motofuel.data.model.AppTheme

/**
 * Persists application settings (e.g. AppTheme preference) in local SharedPreferences.
 */
class PreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences("motofuel_preferences", Context.MODE_PRIVATE)

    var appTheme: AppTheme
        get() {
            val savedName = prefs.getString(KEY_APP_THEME, AppTheme.DARK.name)
            return try {
                AppTheme.valueOf(savedName ?: AppTheme.DARK.name)
            } catch (e: Exception) {
                AppTheme.DARK
            }
        }
        set(value) {
            prefs.edit().putString(KEY_APP_THEME, value.name).apply()
        }

    var tankCapacityLiters: Double
        get() = prefs.getFloat(KEY_TANK_CAPACITY, 0.0f).toDouble()
        set(value) {
            prefs.edit().putFloat(KEY_TANK_CAPACITY, value.toFloat()).apply()
        }

    var targetKmLiters: Double
        get() = prefs.getFloat(KEY_TARGET_KM_LITERS, 0.0f).toDouble()
        set(value) {
            prefs.edit().putFloat(KEY_TARGET_KM_LITERS, value.toFloat()).apply()
        }

    var currencySymbol: String
        get() = prefs.getString(KEY_CURRENCY_SYMBOL, "₱") ?: "₱"
        set(value) {
            prefs.edit().putString(KEY_CURRENCY_SYMBOL, value).apply()
        }

    companion object {
        private const val KEY_APP_THEME = "key_app_theme"
        private const val KEY_TANK_CAPACITY = "key_tank_capacity"
        private const val KEY_TARGET_KM_LITERS = "key_target_km_liters"
        private const val KEY_CURRENCY_SYMBOL = "key_currency_symbol"
    }
}
