package br.ufop.campus.ui.theme

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val CampusCardRadius = 24.dp
val CampusGroupedRadius = 26.dp
val CampusSheetRadius = 30.dp
val CampusPillRadius = 999.dp

enum class CampusAccentColor(
    val storageKey: String,
    val displayName: String,
    val previewHex: String
) {
    GREEN("GREEN", "Mata", "#216B4A"),
    RED("RED", "Vinho", "#8C1D35"),
    ORANGE("ORANGE", "Terracota", "#B54717"),
    PURPLE("PURPLE", "Ametista", "#5F3DC4"),
    YELLOW("YELLOW", "Ouro Velho", "#9A680E"),
    CYAN("CYAN", "Mineral", "#126E7B"),
    BROWN("BROWN", "Cacau", "#734A34"),
    PINK("PINK", "Rubi", "#A62660"),
    BLUE("BLUE", "Cobalto", "#2557B8");

    fun palette(isDark: Boolean): CampusTonalPalette = when (this to isDark) {
        GREEN to true -> CampusTonalPalette(
            canvas = Color(0xFF101513),
            surface1 = Color(0xFF1A2520),
            surface2 = DarkSelectedPillBg,
            primary = DarkPrimaryGreen,
            onPrimary = Color.White,
            highlight = DarkSelectedPillText,
            ink = DarkTextPrimary,
            onSurfaceVariant = DarkTextSecondary,
            outline = Color(0xFF43594F),
            outlineVariant = Color(0xFF2D3F36)
        )
        GREEN to false -> CampusTonalPalette(
            canvas = Color(0xFFF3F9F6),
            surface1 = Color.White,
            surface2 = LightSelectedPillBg,
            primary = LightPrimaryGreen,
            onPrimary = Color.White,
            highlight = LightSelectedPillText,
            ink = LightSelectedPillText,
            onSurfaceVariant = LightTextSecondary,
            outline = Color(0xFFA7C1B4),
            outlineVariant = Color(0xFFD0E2D8)
        )
        RED to true -> CampusTonalPalette(
            canvas = DarkBackground,
            surface1 = DarkSurface,
            surface2 = DarkPrimaryContainerRed,
            primary = DarkPrimaryRed,
            onPrimary = Color.White,
            highlight = DarkOnPrimaryContainerRed,
            ink = DarkTextPrimary,
            onSurfaceVariant = DarkTextSecondary,
            outline = DarkOutline,
            outlineVariant = DarkCardBorder
        )
        RED to false -> CampusTonalPalette(
            canvas = LightBackground,
            surface1 = Color.White,
            surface2 = LightPrimaryContainerRed,
            primary = LightPrimaryRed,
            onPrimary = Color.White,
            highlight = LightOnPrimaryContainerRed,
            ink = LightOnPrimaryContainerRed,
            onSurfaceVariant = LightTextSecondary,
            outline = LightOutline,
            outlineVariant = LightCardBorder
        )
        ORANGE to true -> CampusTonalPalette(
            canvas = Color(0xFF161210),
            surface1 = Color(0xFF261E19),
            surface2 = DarkPrimaryContainerOrange,
            primary = DarkPrimaryOrange,
            onPrimary = Color.White,
            highlight = DarkOnPrimaryContainerOrange,
            ink = DarkTextPrimary,
            onSurfaceVariant = DarkTextSecondary,
            outline = Color(0xFF614D41),
            outlineVariant = Color(0xFF45362D)
        )
        ORANGE to false -> CampusTonalPalette(
            canvas = Color(0xFFFBF6F3),
            surface1 = Color.White,
            surface2 = LightPrimaryContainerOrange,
            primary = LightPrimaryOrange,
            onPrimary = Color.White,
            highlight = LightOnPrimaryContainerOrange,
            ink = LightOnPrimaryContainerOrange,
            onSurfaceVariant = LightTextSecondary,
            outline = Color(0xFFD4BAAB),
            outlineVariant = Color(0xFFEBD9CE)
        )
        PURPLE to true -> CampusTonalPalette(
            canvas = Color(0xFF131117),
            surface1 = Color(0xFF201C29),
            surface2 = DarkPrimaryContainerPurple,
            primary = DarkPrimaryPurple,
            onPrimary = Color.White,
            highlight = DarkOnPrimaryContainerPurple,
            ink = DarkTextPrimary,
            onSurfaceVariant = DarkTextSecondary,
            outline = Color(0xFF53486B),
            outlineVariant = Color(0xFF3A324D)
        )
        PURPLE to false -> CampusTonalPalette(
            canvas = Color(0xFFF7F5FC),
            surface1 = Color.White,
            surface2 = LightPrimaryContainerPurple,
            primary = LightPrimaryPurple,
            onPrimary = Color.White,
            highlight = LightOnPrimaryContainerPurple,
            ink = LightOnPrimaryContainerPurple,
            onSurfaceVariant = LightTextSecondary,
            outline = Color(0xFFC1B6DD),
            outlineVariant = Color(0xFFDED6F0)
        )
        YELLOW to true -> CampusTonalPalette(
            canvas = Color(0xFF15130F),
            surface1 = Color(0xFF242017),
            surface2 = DarkPrimaryContainerYellow,
            primary = DarkPrimaryYellow,
            onPrimary = DarkOnPrimaryYellow,
            highlight = DarkOnPrimaryContainerYellow,
            ink = DarkTextPrimary,
            onSurfaceVariant = DarkTextSecondary,
            outline = Color(0xFF5D533C),
            outlineVariant = Color(0xFF423A2A)
        )
        YELLOW to false -> CampusTonalPalette(
            canvas = Color(0xFFFAF7F0),
            surface1 = Color.White,
            surface2 = LightPrimaryContainerYellow,
            primary = LightPrimaryYellow,
            onPrimary = LightOnPrimaryYellow,
            highlight = LightOnPrimaryContainerYellow,
            ink = LightOnPrimaryContainerYellow,
            onSurfaceVariant = LightTextSecondary,
            outline = Color(0xFFCFC0A0),
            outlineVariant = Color(0xFFE7DEC6)
        )
        CYAN to true -> CampusTonalPalette(
            canvas = Color(0xFF101517),
            surface1 = Color(0xFF192428),
            surface2 = DarkPrimaryContainerCyan,
            primary = DarkPrimaryCyan,
            onPrimary = DarkOnPrimaryCyan,
            highlight = DarkOnPrimaryContainerCyan,
            ink = DarkTextPrimary,
            onSurfaceVariant = DarkTextSecondary,
            outline = Color(0xFF3F5963),
            outlineVariant = Color(0xFF2C4047)
        )
        CYAN to false -> CampusTonalPalette(
            canvas = Color(0xFFF2F9FA),
            surface1 = Color.White,
            surface2 = LightPrimaryContainerCyan,
            primary = LightPrimaryCyan,
            onPrimary = LightOnPrimaryCyan,
            highlight = LightOnPrimaryContainerCyan,
            ink = LightOnPrimaryContainerCyan,
            onSurfaceVariant = LightTextSecondary,
            outline = Color(0xFFA7C7CD),
            outlineVariant = Color(0xFFCDE2E6)
        )
        BROWN to true -> CampusTonalPalette(
            canvas = Color(0xFF151210),
            surface1 = Color(0xFF241E1B),
            surface2 = DarkPrimaryContainerBrown,
            primary = DarkPrimaryBrown,
            onPrimary = DarkOnPrimaryBrown,
            highlight = DarkOnPrimaryContainerBrown,
            ink = DarkTextPrimary,
            onSurfaceVariant = DarkTextSecondary,
            outline = Color(0xFF5C4E46),
            outlineVariant = Color(0xFF423832)
        )
        BROWN to false -> CampusTonalPalette(
            canvas = Color(0xFFFAF6F3),
            surface1 = Color.White,
            surface2 = LightPrimaryContainerBrown,
            primary = LightPrimaryBrown,
            onPrimary = LightOnPrimaryBrown,
            highlight = LightOnPrimaryContainerBrown,
            ink = LightOnPrimaryContainerBrown,
            onSurfaceVariant = LightTextSecondary,
            outline = Color(0xFFCBBBAE),
            outlineVariant = Color(0xFFE5DAD2)
        )
        PINK to true -> CampusTonalPalette(
            canvas = Color(0xFF161114),
            surface1 = Color(0xFF261B21),
            surface2 = DarkPrimaryContainerPink,
            primary = DarkPrimaryPink,
            onPrimary = DarkOnPrimaryPink,
            highlight = DarkOnPrimaryContainerPink,
            ink = DarkTextPrimary,
            onSurfaceVariant = DarkTextSecondary,
            outline = Color(0xFF614353),
            outlineVariant = Color(0xFF452F3B)
        )
        PINK to false -> CampusTonalPalette(
            canvas = Color(0xFFFCF4F8),
            surface1 = Color.White,
            surface2 = LightPrimaryContainerPink,
            primary = LightPrimaryPink,
            onPrimary = LightOnPrimaryPink,
            highlight = LightOnPrimaryContainerPink,
            ink = LightOnPrimaryContainerPink,
            onSurfaceVariant = LightTextSecondary,
            outline = Color(0xFFD6B4C4),
            outlineVariant = Color(0xFFECD5E0)
        )
        BLUE to true -> CampusTonalPalette(
            canvas = Color(0xFF101318),
            surface1 = Color(0xFF1A212D),
            surface2 = DarkPrimaryContainerBlue,
            primary = DarkPrimaryBlue,
            onPrimary = DarkOnPrimaryBlue,
            highlight = DarkOnPrimaryContainerBlue,
            ink = DarkTextPrimary,
            onSurfaceVariant = DarkTextSecondary,
            outline = Color(0xFF435370),
            outlineVariant = Color(0xFF2E3A4F)
        )
        else -> CampusTonalPalette(
            canvas = Color(0xFFF3F7FC),
            surface1 = Color.White,
            surface2 = LightPrimaryContainerBlue,
            primary = LightPrimaryBlue,
            onPrimary = LightOnPrimaryBlue,
            highlight = LightOnPrimaryContainerBlue,
            ink = LightOnPrimaryContainerBlue,
            onSurfaceVariant = LightTextSecondary,
            outline = Color(0xFFAEBEE0),
            outlineVariant = Color(0xFFD2DFF2)
        )
    }

    companion object {
        fun fromKey(raw: String?): CampusAccentColor =
            entries.firstOrNull { it.storageKey.equals(raw?.trim(), ignoreCase = true) } ?: RED
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
    val accentColor: CampusAccentColor = CampusAccentColor.RED,
    val selectAccentColor: (CampusAccentColor) -> Unit = {}
)

val LocalCampusThemeController = staticCompositionLocalOf { CampusThemeController() }

/**
 * Retorna borda apenas quando `highContrastMode == true` (WCAG AAA);
 * no modo padrão privilegia degraus tonais (`Canvas -> Surface 1 -> Surface 2`).
 */
@Composable
fun campusCardBorder(strokeWidth: Dp = 1.5.dp): BorderStroke? {
    val highContrast = LocalCampusAccessibility.current.settings.highContrastMode
    return if (highContrast) BorderStroke(strokeWidth, MaterialTheme.colorScheme.outline) else null
}

/**
 * Elevação tonal sutil no Modo Claro (`1.dp`) sobre o Canvas pastel.
 */
@Composable
fun campusCardElevation(lightElevation: Dp = 1.dp): Dp {
    val isDark = LocalCampusThemeController.current.isDark
    val highContrast = LocalCampusAccessibility.current.settings.highContrastMode
    return if (!isDark && !highContrast) lightElevation else 0.dp
}

/**
 * Cor de destaque segura para texto/ícones pequenos sobre Surface 1 / Canvas.
 */
@Composable
fun campusAccentTextColor(): Color {
    val controller = LocalCampusThemeController.current
    val palette = controller.accentColor.palette(controller.isDark)
    return if (controller.isDark) palette.highlight else palette.primary
}

private fun campusColorSchemeFor(
    darkTheme: Boolean,
    accentColor: CampusAccentColor,
    highContrast: Boolean = false
): ColorScheme {
    val pal = accentColor.palette(darkTheme)
    val inverse = accentColor.palette(!darkTheme).primary

    return if (darkTheme) {
        darkColorScheme(
            primary = pal.primary,
            onPrimary = pal.onPrimary,
            primaryContainer = pal.surface2,
            onPrimaryContainer = pal.highlight,
            inversePrimary = inverse,
            secondary = pal.primary,
            onSecondary = pal.onPrimary,
            secondaryContainer = pal.surface2,
            onSecondaryContainer = pal.highlight,
            tertiary = DepartmentCompPurple,
            onTertiary = Color.White,
            background = if (highContrast) HighContrastDarkBackground else pal.canvas,
            onBackground = if (highContrast) HighContrastDarkTextPrimary else pal.ink,
            surface = if (highContrast) HighContrastDarkSurface else pal.surface1,
            onSurface = if (highContrast) HighContrastDarkTextPrimary else pal.ink,
            surfaceVariant = if (highContrast) HighContrastDarkSurfaceVariant else pal.surface2,
            onSurfaceVariant = if (highContrast) HighContrastDarkTextSecondary else pal.onSurfaceVariant,
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = if (highContrast) HighContrastDarkBackground else pal.canvas,
            surfaceContainerLow = if (highContrast) HighContrastDarkBackground else pal.surface1,
            surfaceContainer = if (highContrast) HighContrastDarkSurface else pal.surface1,
            surfaceContainerHigh = if (highContrast) HighContrastDarkSurfaceVariant else pal.surface2,
            surfaceContainerHighest = if (highContrast) HighContrastDarkSurfaceVariant else pal.surface2,
            outline = if (highContrast) HighContrastDarkOutline else pal.outline,
            outlineVariant = if (highContrast) HighContrastDarkOutlineVariant else pal.outlineVariant
        )
    } else {
        lightColorScheme(
            primary = pal.primary,
            onPrimary = pal.onPrimary,
            primaryContainer = pal.surface2,
            onPrimaryContainer = pal.ink,
            inversePrimary = inverse,
            secondary = pal.primary,
            onSecondary = pal.onPrimary,
            secondaryContainer = pal.surface2,
            onSecondaryContainer = pal.ink,
            tertiary = DepartmentCompPurple,
            onTertiary = Color.White,
            background = if (highContrast) HighContrastLightBackground else pal.canvas,
            onBackground = if (highContrast) HighContrastLightTextPrimary else pal.ink,
            surface = if (highContrast) HighContrastLightSurface else pal.surface1,
            onSurface = if (highContrast) HighContrastLightTextPrimary else pal.ink,
            surfaceVariant = if (highContrast) HighContrastLightSurfaceVariant else pal.surface2,
            onSurfaceVariant = if (highContrast) HighContrastLightTextSecondary else pal.onSurfaceVariant,
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = if (highContrast) HighContrastLightSurface else pal.surface1,
            surfaceContainerLow = if (highContrast) HighContrastLightSurface else pal.canvas,
            surfaceContainer = if (highContrast) HighContrastLightSurface else pal.surface1,
            surfaceContainerHigh = if (highContrast) HighContrastLightSurfaceVariant else pal.surface2,
            surfaceContainerHighest = if (highContrast) HighContrastLightSurfaceVariant else pal.surface2,
            outline = if (highContrast) HighContrastLightOutline else pal.outline,
            outlineVariant = if (highContrast) HighContrastLightOutlineVariant else pal.outlineVariant
        )
    }
}

@Composable
fun CampusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: CampusAccentColor = CampusAccentColor.RED,
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
