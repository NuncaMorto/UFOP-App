package br.ufop.campus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val CampusTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp
    )
)

/**
 * Retorna a tipografia do Campus UFOP, reforçando o peso das fontes
 * quando [boldTextBoost] estiver ativo para máxima legibilidade WCAG.
 */
fun buildCampusTypography(boldTextBoost: Boolean = false): Typography {
    if (!boldTextBoost) return CampusTypography
    return Typography(
        headlineLarge = CampusTypography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = CampusTypography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = CampusTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = CampusTypography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
        titleMedium = CampusTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = CampusTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = CampusTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        bodyMedium = CampusTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        bodySmall = CampusTypography.bodySmall.copy(fontWeight = FontWeight.Medium),
        labelLarge = CampusTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
        labelMedium = CampusTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
        labelSmall = CampusTypography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
    )
}
