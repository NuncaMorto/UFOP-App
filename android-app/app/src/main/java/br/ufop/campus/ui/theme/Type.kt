package br.ufop.campus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.ufop.campus.R

const val TABULAR_NUMS_FEATURE = "tnum"

val OnestFontFamily = FontFamily(
    Font(R.font.onest_regular, FontWeight.Normal),
    Font(R.font.onest_medium, FontWeight.Medium),
    Font(R.font.onest_semibold, FontWeight.SemiBold),
    Font(R.font.onest_bold, FontWeight.Bold),
    Font(R.font.onest_extrabold, FontWeight.ExtraBold)
)

/**
 * Aplica numerais tabulares (`tnum`) para alinhamento estável de horários,
 * contadores de faltas, porcentagens e CRE.
 */
fun TextStyle.tabularNums(): TextStyle = copy(fontFeatureSettings = TABULAR_NUMS_FEATURE)

val CampusTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = OnestFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = OnestFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = OnestFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = OnestFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = OnestFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = OnestFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = OnestFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = OnestFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = OnestFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = OnestFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = OnestFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = OnestFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = OnestFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp
    )
)

/**
 * Retorna a tipografia do Campus UFOP (família Onest), reforçando o peso das fontes
 * quando [boldTextBoost] estiver ativo para máxima legibilidade WCAG.
 */
fun buildCampusTypography(boldTextBoost: Boolean = false): Typography {
    if (!boldTextBoost) return CampusTypography
    return Typography(
        displayLarge = CampusTypography.displayLarge.copy(fontWeight = FontWeight.ExtraBold),
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
