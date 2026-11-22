package br.ufop.campus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

enum class CampusAccentColor(
    val storageKey: String,
    val displayName: String,
    val previewHex: String
) {
    GREEN("GREEN", "Verde", "#3B6939"),
    RED("RED", "Vermelho", "#B3261E"),
    ORANGE("ORANGE", "Laranja", "#C75B00"),
    PURPLE("PURPLE", "Roxo", "#6B3FA0"),
    YELLOW("YELLOW", "Amarelo", "#B58500"),
    CYAN("CYAN", "Ciano", "#00838F"),
    BROWN("BROWN", "Marrom", "#6D4C41");

    companion object {
        fun fromKey(raw: String?): CampusAccentColor =
            entries.firstOrNull { it.storageKey.equals(raw?.trim(), ignoreCase = true) } ?: GREEN
    }
}

/**
 * Configurações do Sistema Global de Acessibilidade UFOP.
 */
data class CampusAccessibilitySettings(
    val screenReaderOptimized: Boolean = false,
    val fontScaleMultiplier: Float = 1.0f,
    val highContrastMode: Boolean = false,
    val reduceMotion: Boolean = false,
    val largeTouchTargets: Boolean = false,
    val boldTextBoost: Boolean = false
)

data class CampusAccessibilityController(
    val settings: CampusAccessibilitySettings = CampusAccessibilitySettings(),
    val updateSettings: (CampusAccessibilitySettings) -> Unit = {}
)

val LocalCampusAccessibility = staticCompositionLocalOf { CampusAccessibilityController() }

data class CampusThemeController(
    val isDark: Boolean = false,
    val toggleTheme: () -> Unit = {},
    val accentColor: CampusAccentColor = CampusAccentColor.GREEN,
    val selectAccentColor: (CampusAccentColor) -> Unit = {}
)

val LocalCampusThemeController = staticCompositionLocalOf { CampusThemeController() }

private fun buildLightColorScheme(
    primary: Color,
    onPrimary: Color = LightOnPrimary,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    inversePrimary: Color,
    highContrast: Boolean = false
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = inversePrimary,
    secondary = DepartmentMathBlue,
    onSecondary = LightOnPrimary,
    secondaryContainer = LightWeatherBadgeBg,
    onSecondaryContainer = LightWeatherBadgeText,
    tertiary = DepartmentCompPurple,
    onTertiary = LightOnPrimary,
    background = if (highContrast) HighContrastLightBackground else LightBackground,
    onBackground = if (highContrast) HighContrastLightTextPrimary else LightTextPrimary,
    surface = if (highContrast) HighContrastLightSurface else LightSurface,
    onSurface = if (highContrast) HighContrastLightTextPrimary else LightTextPrimary,
    surfaceVariant = if (highContrast) HighContrastLightSurfaceVariant else LightSurfaceVariant,
    onSurfaceVariant = if (highContrast) HighContrastLightTextSecondary else LightTextSecondary,
    surfaceTint = primary,
    surfaceContainerLowest = if (highContrast) HighContrastLightSurface else LightSurfaceContainerLowest,
    surfaceContainerLow = if (highContrast) HighContrastLightSurface else LightSurfaceContainerLow,
    surfaceContainer = if (highContrast) HighContrastLightSurface else LightSurfaceContainer,
    surfaceContainerHigh = if (highContrast) HighContrastLightSurfaceVariant else LightSurfaceContainerHigh,
    surfaceContainerHighest = if (highContrast) HighContrastLightSurfaceVariant else LightSurfaceContainerHighest,
    outline = if (highContrast) HighContrastLightOutline else LightOutline,
    outlineVariant = if (highContrast) HighContrastLightOutlineVariant else LightCardBorder
)

private fun buildDarkColorScheme(
    primary: Color,
    onPrimary: Color = DarkOnPrimary,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    inversePrimary: Color,
    highContrast: Boolean = false
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = inversePrimary,
    secondary = DarkAccentBlue,
    onSecondary = DarkOnPrimary,
    secondaryContainer = DarkWeatherBadgeBg,
    onSecondaryContainer = DarkWeatherBadgeText,
    tertiary = DepartmentCompPurple,
    onTertiary = DarkOnPrimary,
    background = if (highContrast) HighContrastDarkBackground else DarkBackground,
    onBackground = if (highContrast) HighContrastDarkTextPrimary else DarkTextPrimary,
    surface = if (highContrast) HighContrastDarkSurface else DarkSurface,
    onSurface = if (highContrast) HighContrastDarkTextPrimary else DarkTextPrimary,
    surfaceVariant = if (highContrast) HighContrastDarkSurfaceVariant else DarkSurfaceVariant,
    onSurfaceVariant = if (highContrast) HighContrastDarkTextSecondary else DarkTextSecondary,
    surfaceTint = primary,
    surfaceContainerLowest = if (highContrast) HighContrastDarkBackground else DarkSurfaceContainerLowest,
    surfaceContainerLow = if (highContrast) HighContrastDarkBackground else DarkSurfaceContainerLow,
    surfaceContainer = if (highContrast) HighContrastDarkSurface else DarkSurfaceContainer,
    surfaceContainerHigh = if (highContrast) HighContrastDarkSurfaceVariant else DarkSurfaceContainerHigh,
    surfaceContainerHighest = if (highContrast) HighContrastDarkSurfaceVariant else DarkSurfaceContainerHighest,
    outline = if (highContrast) HighContrastDarkOutline else DarkOutline,
    outlineVariant = if (highContrast) HighContrastDarkOutlineVariant else DarkCardBorder
)

private fun campusColorSchemeFor(
    darkTheme: Boolean,
    accentColor: CampusAccentColor,
    highContrast: Boolean = false
): ColorScheme {
    return if (darkTheme) {
        when (accentColor) {
            CampusAccentColor.GREEN -> buildDarkColorScheme(
                primary = DarkPrimaryGreen,
                onPrimary = DarkOnPrimary,
                primaryContainer = DarkSelectedPillBg,
                onPrimaryContainer = DarkSelectedPillText,
                inversePrimary = DarkInversePrimaryGreen,
                highContrast = highContrast
            )
            CampusAccentColor.RED -> buildDarkColorScheme(
                primary = DarkPrimaryRed,
                onPrimary = DarkOnPrimary,
                primaryContainer = DarkPrimaryContainerRed,
                onPrimaryContainer = DarkOnPrimaryContainerRed,
                inversePrimary = DarkInversePrimaryRed,
                highContrast = highContrast
            )
            CampusAccentColor.ORANGE -> buildDarkColorScheme(
                primary = DarkPrimaryOrange,
                onPrimary = DarkOnPrimary,
                primaryContainer = DarkPrimaryContainerOrange,
                onPrimaryContainer = DarkOnPrimaryContainerOrange,
                inversePrimary = DarkInversePrimaryOrange,
                highContrast = highContrast
            )
            CampusAccentColor.PURPLE -> buildDarkColorScheme(
                primary = DarkPrimaryPurple,
                onPrimary = DarkOnPrimary,
                primaryContainer = DarkPrimaryContainerPurple,
                onPrimaryContainer = DarkOnPrimaryContainerPurple,
                inversePrimary = DarkInversePrimaryPurple,
                highContrast = highContrast
            )
            CampusAccentColor.YELLOW -> buildDarkColorScheme(
                primary = DarkPrimaryYellow,
                onPrimary = DarkOnPrimaryYellow,
                primaryContainer = DarkPrimaryContainerYellow,
                onPrimaryContainer = DarkOnPrimaryContainerYellow,
                inversePrimary = DarkInversePrimaryYellow,
                highContrast = highContrast
            )
            CampusAccentColor.CYAN -> buildDarkColorScheme(
                primary = DarkPrimaryCyan,
                onPrimary = DarkOnPrimaryCyan,
                primaryContainer = DarkPrimaryContainerCyan,
                onPrimaryContainer = DarkOnPrimaryContainerCyan,
                inversePrimary = DarkInversePrimaryCyan,
                highContrast = highContrast
            )
            CampusAccentColor.BROWN -> buildDarkColorScheme(
                primary = DarkPrimaryBrown,
                onPrimary = DarkOnPrimaryBrown,
                primaryContainer = DarkPrimaryContainerBrown,
                onPrimaryContainer = DarkOnPrimaryContainerBrown,
                inversePrimary = DarkInversePrimaryBrown,
                highContrast = highContrast
            )
        }
    } else {
        when (accentColor) {
            CampusAccentColor.GREEN -> buildLightColorScheme(
                primary = LightPrimaryGreen,
                onPrimary = LightOnPrimary,
                primaryContainer = LightSelectedPillBg,
                onPrimaryContainer = LightSelectedPillText,
                inversePrimary = LightInversePrimaryGreen,
                highContrast = highContrast
            )
            CampusAccentColor.RED -> buildLightColorScheme(
                primary = LightPrimaryRed,
                onPrimary = LightOnPrimary,
                primaryContainer = LightPrimaryContainerRed,
                onPrimaryContainer = LightOnPrimaryContainerRed,
                inversePrimary = LightInversePrimaryRed,
                highContrast = highContrast
            )
            CampusAccentColor.ORANGE -> buildLightColorScheme(
                primary = LightPrimaryOrange,
                onPrimary = LightOnPrimary,
                primaryContainer = LightPrimaryContainerOrange,
                onPrimaryContainer = LightOnPrimaryContainerOrange,
                inversePrimary = LightInversePrimaryOrange,
                highContrast = highContrast
            )
            CampusAccentColor.PURPLE -> buildLightColorScheme(
                primary = LightPrimaryPurple,
                onPrimary = LightOnPrimary,
                primaryContainer = LightPrimaryContainerPurple,
                onPrimaryContainer = LightOnPrimaryContainerPurple,
                inversePrimary = LightInversePrimaryPurple,
                highContrast = highContrast
            )
            CampusAccentColor.YELLOW -> buildLightColorScheme(
                primary = LightPrimaryYellow,
                onPrimary = LightOnPrimaryYellow,
                primaryContainer = LightPrimaryContainerYellow,
                onPrimaryContainer = LightOnPrimaryContainerYellow,
                inversePrimary = LightInversePrimaryYellow,
                highContrast = highContrast
            )
            CampusAccentColor.CYAN -> buildLightColorScheme(
                primary = LightPrimaryCyan,
                onPrimary = LightOnPrimaryCyan,
                primaryContainer = LightPrimaryContainerCyan,
                onPrimaryContainer = LightOnPrimaryContainerCyan,
                inversePrimary = LightInversePrimaryCyan,
                highContrast = highContrast
            )
            CampusAccentColor.BROWN -> buildLightColorScheme(
                primary = LightPrimaryBrown,
                onPrimary = LightOnPrimaryBrown,
                primaryContainer = LightPrimaryContainerBrown,
                onPrimaryContainer = LightOnPrimaryContainerBrown,
                inversePrimary = LightInversePrimaryBrown,
                highContrast = highContrast
            )
        }
    }
}

@Composable
fun CampusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: CampusAccentColor = CampusAccentColor.GREEN,
    accessibility: CampusAccessibilitySettings = CampusAccessibilitySettings(),
    onToggleDarkTheme: () -> Unit = {},
    onSelectAccentColor: (CampusAccentColor) -> Unit = {},
    onUpdateAccessibility: (CampusAccessibilitySettings) -> Unit = {},
    content: @Composable () -> Unit
) {
    val colorScheme = campusColorSchemeFor(
        darkTheme = darkTheme,
        accentColor = accentColor,
        highContrast = accessibility.highContrastMode
    )
    val controller = CampusThemeController(
        isDark = darkTheme,
        toggleTheme = onToggleDarkTheme,
        accentColor = accentColor,
        selectAccentColor = onSelectAccentColor
    )
    val accessibilityController = CampusAccessibilityController(
        settings = accessibility,
        updateSettings = onUpdateAccessibility
    )

    val currentDensity = LocalDensity.current
    val effectiveFontScaleMultiplier = accessibility.fontScaleMultiplier.coerceIn(0.85f, 1.5f)
    val modifiedDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * effectiveFontScaleMultiplier
    )
    val typography = buildCampusTypography(boldTextBoost = accessibility.boldTextBoost)

    CompositionLocalProvider(
        LocalCampusThemeController provides controller,
        LocalCampusAccessibility provides accessibilityController,
        LocalDensity provides modifiedDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
