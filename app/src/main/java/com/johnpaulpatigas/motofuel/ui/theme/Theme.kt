package com.johnpaulpatigas.motofuel.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class AppSemanticColors(
    val good: Color,
    val onGood: Color,
    val goodContainer: Color,
    val onGoodContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val critical: Color,
    val onCritical: Color,
    val criticalContainer: Color,
    val onCriticalContainer: Color,
)

val lightSemanticColors = AppSemanticColors(
    good = semanticGoodLight,
    onGood = onSemanticGoodLight,
    goodContainer = semanticGoodContainerLight,
    onGoodContainer = onSemanticGoodContainerLight,
    warning = semanticWarningLight,
    onWarning = onSemanticWarningLight,
    warningContainer = semanticWarningContainerLight,
    onWarningContainer = onSemanticWarningContainerLight,
    critical = semanticCriticalLight,
    onCritical = onSemanticCriticalLight,
    criticalContainer = semanticCriticalContainerLight,
    onCriticalContainer = onSemanticCriticalContainerLight,
)

val darkSemanticColors = AppSemanticColors(
    good = semanticGoodDark,
    onGood = onSemanticGoodDark,
    goodContainer = semanticGoodContainerDark,
    onGoodContainer = onSemanticGoodContainerDark,
    warning = semanticWarningDark,
    onWarning = onSemanticWarningDark,
    warningContainer = semanticWarningContainerDark,
    onWarningContainer = onSemanticWarningContainerDark,
    critical = semanticCriticalDark,
    onCritical = onSemanticCriticalDark,
    criticalContainer = semanticCriticalContainerDark,
    onCriticalContainer = onSemanticCriticalContainerDark,
)

/**
 * Creates dynamic AppSemanticColors derived from the active dynamic [ColorScheme].
 * Maps dynamic tertiary to 'good', dynamic error to 'critical', and harmonized
 * tones for 'warning', ensuring full harmony with Material You wallpaper palettes.
 */
fun dynamicSemanticColors(
    colorScheme: androidx.compose.material3.ColorScheme,
    darkTheme: Boolean
): AppSemanticColors {
    return if (darkTheme) {
        AppSemanticColors(
            good = colorScheme.tertiary,
            onGood = colorScheme.onTertiary,
            goodContainer = colorScheme.tertiaryContainer,
            onGoodContainer = colorScheme.onTertiaryContainer,
            warning = semanticWarningDark,
            onWarning = onSemanticWarningDark,
            warningContainer = semanticWarningContainerDark,
            onWarningContainer = onSemanticWarningContainerDark,
            critical = colorScheme.error,
            onCritical = colorScheme.onError,
            criticalContainer = colorScheme.errorContainer,
            onCriticalContainer = colorScheme.onErrorContainer
        )
    } else {
        AppSemanticColors(
            good = colorScheme.tertiary,
            onGood = colorScheme.onTertiary,
            goodContainer = colorScheme.tertiaryContainer,
            onGoodContainer = colorScheme.onTertiaryContainer,
            warning = semanticWarningLight,
            onWarning = onSemanticWarningLight,
            warningContainer = semanticWarningContainerLight,
            onWarningContainer = onSemanticWarningContainerLight,
            critical = colorScheme.error,
            onCritical = colorScheme.onError,
            criticalContainer = colorScheme.errorContainer,
            onCriticalContainer = colorScheme.onErrorContainer
        )
    }
}

val LocalAppSemanticColors = staticCompositionLocalOf { lightSemanticColors }

val MaterialTheme.semanticColors: AppSemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppSemanticColors.current

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

@Composable
fun MotoFuelTheme(
    appTheme: com.johnpaulpatigas.motofuel.data.model.AppTheme = com.johnpaulpatigas.motofuel.data.model.AppTheme.DARK,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        com.johnpaulpatigas.motofuel.data.model.AppTheme.SYSTEM -> isSystemInDarkTheme()
        com.johnpaulpatigas.motofuel.data.model.AppTheme.LIGHT -> false
        com.johnpaulpatigas.motofuel.data.model.AppTheme.DARK -> true
    }

    val isDynamicActive = dynamicColor

    val colorScheme = when {
        isDynamicActive -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkScheme
        else -> lightScheme
    }

    val semanticColors = when {
        isDynamicActive -> dynamicSemanticColors(colorScheme, darkTheme)
        darkTheme -> darkSemanticColors
        else -> lightSemanticColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalAppSemanticColors provides semanticColors,
        LocalAppSpacing provides AppSpacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

@Composable
fun AppTheme(
    appTheme: com.johnpaulpatigas.motofuel.data.model.AppTheme = com.johnpaulpatigas.motofuel.data.model.AppTheme.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    MotoFuelTheme(
        appTheme = appTheme,
        dynamicColor = dynamicColor,
        content = content
    )
}
