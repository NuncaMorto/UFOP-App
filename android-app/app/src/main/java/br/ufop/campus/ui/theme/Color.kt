package br.ufop.campus.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Escada tonal curada por tema/cor de destaque com contraste WCAG 2.1 AA garantido.
 */
data class CampusTonalPalette(
    val canvas: Color,
    val surface1: Color,
    val surface2: Color,
    val primary: Color,
    val onPrimary: Color,
    val highlight: Color,
    val ink: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color
)

// Default Neutral / Vinho Base Tokens (compatibilidade com referências existentes)
val LightBackground = Color(0xFFFBF5F6)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF2E4E7)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFBF5F6)
val LightSurfaceContainer = Color(0xFFFFFFFF)
val LightSurfaceContainerHigh = Color(0xFFF2E4E7)
val LightSurfaceContainerHighest = Color(0xFFF2E4E7)
val LightCardBorder = Color(0xFFE7D6DA)
val LightOutline = Color(0xFFCBB6BC)
val LightSelectedPillBg = Color(0xFFE1EFE8)
val LightSelectedPillText = Color(0xFF11291F)
val LightPrimaryGreen = Color(0xFF216B4A)
val LightInversePrimaryGreen = Color(0xFFA6DFC3)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightTextPrimary = Color(0xFF2B1217)
val LightTextSecondary = Color(0xFF524B4E)
val LightWeatherBadgeBg = Color(0xFFD9E9F9)
val LightWeatherBadgeText = Color(0xFF1A436B)
val LightWeatherSunAccent = Color(0xFFFFE066)

// Light Mode — 9 Curated Accent Palettes (WCAG AA >= 4.5:1 / >= 4.8:1)
val LightPrimaryRed = Color(0xFF8C1D35)
val LightPrimaryContainerRed = Color(0xFFF2E4E7)
val LightOnPrimaryContainerRed = Color(0xFF2B1217)
val LightInversePrimaryRed = Color(0xFFF2B8C1)

val LightPrimaryOrange = Color(0xFFB54717)
val LightPrimaryContainerOrange = Color(0xFFF4E6DD)
val LightOnPrimaryContainerOrange = Color(0xFF2E170D)
val LightInversePrimaryOrange = Color(0xFFF7C4A8)

val LightPrimaryPurple = Color(0xFF5F3DC4)
val LightPrimaryContainerPurple = Color(0xFFEAE4F7)
val LightOnPrimaryContainerPurple = Color(0xFF1E1438)
val LightInversePrimaryPurple = Color(0xFFCFC0FA)

val LightPrimaryYellow = Color(0xFF9A680E)
val LightPrimaryContainerYellow = Color(0xFFF0E7D1)
val LightOnPrimaryContainerYellow = Color(0xFF2E2107)
val LightInversePrimaryYellow = Color(0xFFF2D591)
val LightOnPrimaryYellow = Color(0xFFFFFFFF)

val LightPrimaryCyan = Color(0xFF126E7B)
val LightPrimaryContainerCyan = Color(0xFFDFEEF1)
val LightOnPrimaryContainerCyan = Color(0xFF0D282D)
val LightInversePrimaryCyan = Color(0xFF9EE0E8)
val LightOnPrimaryCyan = Color(0xFFFFFFFF)

val LightPrimaryBrown = Color(0xFF734A34)
val LightPrimaryContainerBrown = Color(0xFFEFE6E0)
val LightOnPrimaryContainerBrown = Color(0xFF291A12)
val LightInversePrimaryBrown = Color(0xFFDFC2B2)
val LightOnPrimaryBrown = Color(0xFFFFFFFF)

val LightPrimaryPink = Color(0xFFA62660)
val LightPrimaryContainerPink = Color(0xFFF4E2EB)
val LightOnPrimaryContainerPink = Color(0xFF300F1E)
val LightInversePrimaryPink = Color(0xFFF5B8D2)
val LightOnPrimaryPink = Color(0xFFFFFFFF)

val LightPrimaryBlue = Color(0xFF2557B8)
val LightPrimaryContainerBlue = Color(0xFFE2EBF8)
val LightOnPrimaryContainerBlue = Color(0xFF121F3B)
val LightInversePrimaryBlue = Color(0xFFB3CCF8)
val LightOnPrimaryBlue = Color(0xFFFFFFFF)

// Dark Mode — Carbon & Vinho Tonal Base Tokens
val DarkBackground = Color(0xFF151112)
val DarkSurface = Color(0xFF231A1D)
val DarkSurfaceVariant = Color(0xFF312428)
val DarkSurfaceContainerLowest = Color(0xFF151112)
val DarkSurfaceContainerLow = Color(0xFF231A1D)
val DarkSurfaceContainer = Color(0xFF231A1D)
val DarkSurfaceContainerHigh = Color(0xFF312428)
val DarkSurfaceContainerHighest = Color(0xFF312428)
val DarkCardBorder = Color(0xFF3F3135)
val DarkOutline = Color(0xFF5C4B50)
val DarkSelectedPillBg = Color(0xFF25352E)
val DarkSelectedPillText = Color(0xFFA6DFC3)
val DarkAccentBlue = Color(0xFF356AD1)
val DarkPrimaryGreen = Color(0xFF267A54)
val DarkInversePrimaryGreen = Color(0xFF216B4A)
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkTextPrimary = Color(0xFFF6F4F5)
val DarkTextSecondary = Color(0xFFCCC6CA)
val DarkWeatherBadgeBg = Color(0xFF1E2D3D)
val DarkWeatherBadgeText = Color(0xFF99C8F2)

// Dark Mode — 9 Curated Accent Palettes (WCAG AA >= 4.8:1)
val DarkPrimaryRed = Color(0xFFA82E42)
val DarkPrimaryContainerRed = Color(0xFF312428)
val DarkOnPrimaryContainerRed = Color(0xFFF2B8C1)
val DarkInversePrimaryRed = Color(0xFF8C1D35)

val DarkPrimaryOrange = Color(0xFFBD501E)
val DarkPrimaryContainerOrange = Color(0xFF362A23)
val DarkOnPrimaryContainerOrange = Color(0xFFF7C4A8)
val DarkInversePrimaryOrange = Color(0xFFB54717)

val DarkPrimaryPurple = Color(0xFF7554D1)
val DarkPrimaryContainerPurple = Color(0xFF2D273B)
val DarkOnPrimaryContainerPurple = Color(0xFFCFC0FA)
val DarkInversePrimaryPurple = Color(0xFF5F3DC4)

val DarkPrimaryYellow = Color(0xFFE0AE43)
val DarkPrimaryContainerYellow = Color(0xFF332D20)
val DarkOnPrimaryContainerYellow = Color(0xFFF2D591)
val DarkInversePrimaryYellow = Color(0xFF9A680E)
val DarkOnPrimaryYellow = Color(0xFF211602)

val DarkPrimaryCyan = Color(0xFF177C8A)
val DarkPrimaryContainerCyan = Color(0xFF233339)
val DarkOnPrimaryContainerCyan = Color(0xFF9EE0E8)
val DarkInversePrimaryCyan = Color(0xFF126E7B)
val DarkOnPrimaryCyan = Color(0xFFFFFFFF)

val DarkPrimaryBrown = Color(0xFF8F6249)
val DarkPrimaryContainerBrown = Color(0xFF332B26)
val DarkOnPrimaryContainerBrown = Color(0xFFDFC2B2)
val DarkInversePrimaryBrown = Color(0xFF734A34)
val DarkOnPrimaryBrown = Color(0xFFFFFFFF)

val DarkPrimaryPink = Color(0xFFC43E78)
val DarkPrimaryContainerPink = Color(0xFF36252F)
val DarkOnPrimaryContainerPink = Color(0xFFF5B8D2)
val DarkInversePrimaryPink = Color(0xFFA62660)
val DarkOnPrimaryPink = Color(0xFFFFFFFF)

val DarkPrimaryBlue = Color(0xFF356AD1)
val DarkPrimaryContainerBlue = Color(0xFF252F40)
val DarkOnPrimaryContainerBlue = Color(0xFFB3CCF8)
val DarkInversePrimaryBlue = Color(0xFF2557B8)
val DarkOnPrimaryBlue = Color(0xFFFFFFFF)

// High Contrast Mode Overrides (WCAG AAA >= 7:1)
val HighContrastLightBackground = Color(0xFFFFFFFF)
val HighContrastLightSurface = Color(0xFFFFFFFF)
val HighContrastLightSurfaceVariant = Color(0xFFEEEEEE)
val HighContrastLightTextPrimary = Color(0xFF000000)
val HighContrastLightTextSecondary = Color(0xFF212121)
val HighContrastLightOutline = Color(0xFF000000)
val HighContrastLightOutlineVariant = Color(0xFF424242)

val HighContrastDarkBackground = Color(0xFF050607)
val HighContrastDarkSurface = Color(0xFF050607)
val HighContrastDarkSurfaceVariant = Color(0xFF181A1D)
val HighContrastDarkTextPrimary = Color(0xFFFFFFFF)
val HighContrastDarkTextSecondary = Color(0xFFE0E0E0)
val HighContrastDarkOutline = Color(0xFFFFFFFF)
val HighContrastDarkOutlineVariant = Color(0xFFCCCCCC)

// Department Swatches & Timeline Accents
val DepartmentMathBlue = Color(0xFF3B68FF)
val DepartmentCompPurple = Color(0xFF6E56CF)
val DepartmentGeomGreen = Color(0xFF2E7D32)
val DepartmentPhysAmber = Color(0xFF8D5B16)
val CommitmentNeutralGray = Color(0xFF8E95A2)

// Semantic Status Colors
val StatusAttendingGreen = Color(0xFF2E7D32)
val StatusAbsentRed = Color(0xFFC62828)
val StatusCancelledAmber = Color(0xFFEF6C00)

// Dark Mode Status & Department Text Colors (WCAG AA on DarkSurface)
val DarkStatusAttendingGreen = Color(0xFF81C784)
val DarkStatusAbsentRed = Color(0xFFEF9A9A)
val DarkStatusCancelledAmber = Color(0xFFFFB74D)
val DarkDepartmentBlueText = Color(0xFF8AB4F8)
val DarkDepartmentPurpleText = Color(0xFFC5B3FF)
val DarkDepartmentGreenText = Color(0xFF81C784)
val DarkDepartmentAmberText = Color(0xFFFFCC80)

/**
 * Calcula a luminância relativa sRGB de uma cor conforme especificação WCAG 2.1.
 */
fun relativeLuminance(color: Color): Double {
    fun channel(c: Float): Double {
        val cd = c.toDouble()
        return if (cd <= 0.04045) cd / 12.92 else Math.pow((cd + 0.055) / 1.055, 2.4)
    }
    val r = channel(color.red)
    val g = channel(color.green)
    val b = channel(color.blue)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/**
 * Calcula a taxa de contraste WCAG 2.1 entre duas cores (foreground e background).
 */
fun contrastRatio(foreground: Color, background: Color): Double {
    val l1 = relativeLuminance(foreground)
    val l2 = relativeLuminance(background)
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}

/**
 * Retorna branco ou carvão escuro garantindo contraste WCAG AA (>= 4.5:1) sobre [background].
 */
fun accessibleContentColorOn(background: Color): Color =
    if (contrastRatio(Color.White, background) >= 4.5) Color.White else Color(0xFF111315)

/**
 * Retorna uma cor de texto acessível para a superfície dada, interpolando na direção
 * de branco (modo escuro) ou preto carvão (modo claro) até atingir contrastRatio >= minContrast.
 */
fun accessibleTextColor(
    baseColor: Color,
    isDarkSurface: Boolean,
    surfaceColor: Color = if (isDarkSurface) DarkSurface else LightSurface,
    minContrast: Double = 4.5
): Color {
    if (contrastRatio(baseColor, surfaceColor) >= minContrast) return baseColor
    val target = if (isDarkSurface) Color.White else Color(0xFF111315)
    for (i in 1..100) {
        val t = i / 100f
        val r = baseColor.red + (target.red - baseColor.red) * t
        val g = baseColor.green + (target.green - baseColor.green) * t
        val b = baseColor.blue + (target.blue - baseColor.blue) * t
        val candidate = Color(r, g, b, 1f)
        if (contrastRatio(candidate, surfaceColor) >= minContrast) {
            return candidate
        }
    }
    return target
}

/**
 * Retorna o nível de risco de faltas:
 * 0 se < 50% ou maxAbsences <= 0
 * 1 se 50%..74%
 * 2 se >= 75%
 */
fun semanticAbsenceRiskLevel(currentAbsences: Int, maxAbsences: Int): Int {
    if (maxAbsences <= 0 || currentAbsences <= 0) return 0
    val ratio = currentAbsences.toDouble() / maxAbsences.toDouble()
    return when {
        ratio < 0.50 -> 0
        ratio < 0.75 -> 1
        else -> 2
    }
}

/**
 * Retorna a etiqueta semântica do nível de faltas:
 * 0 -> "Controlada", 1 -> "Atenção", 2 -> "Risco"
 */
fun semanticAbsenceRiskLabel(currentAbsences: Int, maxAbsences: Int): String {
    return when (semanticAbsenceRiskLevel(currentAbsences, maxAbsences)) {
        2 -> "Risco"
        1 -> "Atenção"
        else -> "Controlada"
    }
}

/**
 * Retorna a cor semântica do nível de faltas respeitando o modo claro/escuro.
 */
fun semanticAbsenceRiskColor(currentAbsences: Int, maxAbsences: Int, isDark: Boolean): Color {
    return when (semanticAbsenceRiskLevel(currentAbsences, maxAbsences)) {
        2 -> if (isDark) DarkStatusAbsentRed else StatusAbsentRed
        1 -> if (isDark) DarkStatusCancelledAmber else StatusCancelledAmber
        else -> if (isDark) DarkStatusAttendingGreen else StatusAttendingGreen
    }
}

fun parseHexColor(colorHex: String, fallback: Color = DepartmentMathBlue): Color {
    val clean = colorHex.trim().removePrefix("#")
    return try {
        when (clean.length) {
            6 -> Color(clean.toLong(16) or 0xFF000000L)
            8 -> Color(clean.toLong(16))
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}
