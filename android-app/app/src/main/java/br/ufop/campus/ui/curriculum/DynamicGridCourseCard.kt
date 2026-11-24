package br.ufop.campus.ui.curriculum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.curriculum.DynamicCourseNode
import br.ufop.campus.core.curriculum.DynamicCourseStatus
import br.ufop.campus.ui.theme.DarkDepartmentBlueText
import br.ufop.campus.ui.theme.DarkStatusAttendingGreen
import br.ufop.campus.ui.theme.DarkTextPrimary
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.HighContrastDarkTextPrimary
import br.ufop.campus.ui.theme.HighContrastLightTextPrimary
import br.ufop.campus.ui.theme.LightTextPrimary
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.accessibleTextColor

private data class DynamicCardPalette(
    val container: Color,
    val border: Color,
    val accent: Color,
    val titleColor: Color,
    val badgeBg: Color
)

@Composable
private fun dynamicCardPaletteFor(
    status: DynamicCourseStatus,
    isDark: Boolean,
    highContrast: Boolean
): DynamicCardPalette {
    val titleColor = when {
        isDark && highContrast -> HighContrastDarkTextPrimary
        !isDark && highContrast -> HighContrastLightTextPrimary
        isDark -> DarkTextPrimary
        else -> LightTextPrimary
    }

    return when (status) {
        DynamicCourseStatus.COMPLETED -> {
            val container = if (isDark) Color(0xFF173320) else Color(0xFFE0F2E2)
            val baseAccent = if (isDark) DarkStatusAttendingGreen else StatusAttendingGreen
            val accent = accessibleTextColor(baseAccent, isDark, container)
            val border = if (highContrast) accent else accent.copy(alpha = 0.72f)
            DynamicCardPalette(
                container = container,
                border = border,
                accent = accent,
                titleColor = titleColor,
                badgeBg = accent.copy(alpha = if (isDark) 0.22f else 0.14f)
            )
        }
        DynamicCourseStatus.AVAILABLE -> {
            val container = if (isDark) Color(0xFF19273D) else Color(0xFFE3EEFD)
            val baseAccent = if (isDark) DarkDepartmentBlueText else DepartmentMathBlue
            val accent = accessibleTextColor(baseAccent, isDark, container)
            val border = if (highContrast) accent else accent.copy(alpha = 0.65f)
            DynamicCardPalette(
                container = container,
                border = border,
                accent = accent,
                titleColor = titleColor,
                badgeBg = accent.copy(alpha = if (isDark) 0.22f else 0.13f)
            )
        }
        DynamicCourseStatus.LOCKED -> {
            val container = if (isDark) Color(0xFF391F1C) else Color(0xFFFCE5E0)
            val baseCoral = if (isDark) Color(0xFFFFA99B) else Color(0xFFC44D3A)
            val accent = accessibleTextColor(baseCoral, isDark, container)
            val border = if (highContrast) accent else accent.copy(alpha = 0.48f)
            DynamicCardPalette(
                container = container,
                border = border,
                accent = accent,
                titleColor = titleColor,
                badgeBg = accent.copy(alpha = if (isDark) 0.22f else 0.12f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DynamicGridLegendRow(
    isDark: Boolean,
    highContrast: Boolean
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DynamicLegendChip(
            label = "Concluída",
            palette = dynamicCardPaletteFor(DynamicCourseStatus.COMPLETED, isDark, highContrast)
        )
        DynamicLegendChip(
            label = "Disponível",
            palette = dynamicCardPaletteFor(DynamicCourseStatus.AVAILABLE, isDark, highContrast)
        )
        DynamicLegendChip(
            label = "Bloqueada",
            palette = dynamicCardPaletteFor(DynamicCourseStatus.LOCKED, isDark, highContrast)
        )
    }
}

@Composable
private fun DynamicLegendChip(
    label: String,
    palette: DynamicCardPalette
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = palette.container,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(palette.accent)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.titleColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
internal fun DynamicCourseCard(
    course: DynamicCourseNode,
    status: DynamicCourseStatus,
    missingPrerequisites: List<String>,
    isDark: Boolean,
    highContrast: Boolean,
    fontScaleMultiplier: Float,
    onClick: () -> Unit
) {
    val palette = dynamicCardPaletteFor(status = status, isDark = isDark, highContrast = highContrast)
    val statusDescription = when (status) {
        DynamicCourseStatus.COMPLETED -> "Concluída. Toque para desmarcar"
        DynamicCourseStatus.AVAILABLE -> "Disponível. Toque para marcar como concluída"
        DynamicCourseStatus.LOCKED -> {
            val missing = missingPrerequisites.joinToString(", ")
            if (missing.isNotBlank()) "Bloqueada. Pré-requisitos pendentes: $missing" else "Bloqueada"
        }
    }
    val electiveText = if (course.isElective) ", eletiva" else ""
    val a11yDescription =
        "${course.code}, ${course.name}, ${course.workloadHours} horas, ${course.period}º período$electiveText. $statusDescription"
    val clickActionLabel = when (status) {
        DynamicCourseStatus.COMPLETED -> "Desmarcar ${course.code}"
        DynamicCourseStatus.AVAILABLE -> "Marcar ${course.code} como concluída"
        DynamicCourseStatus.LOCKED -> "Ver pré-requisitos de ${course.code}"
    }

    val sizeModifier = if (fontScaleMultiplier > 1.05f) {
        Modifier
            .width(136.dp)
            .heightIn(min = 114.dp)
    } else {
        Modifier
            .width(136.dp)
            .height(114.dp)
    }

    Surface(
        modifier = sizeModifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = clickActionLabel, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = a11yDescription
                onClick(label = clickActionLabel) {
                    onClick()
                    true
                }
            },
        shape = RoundedCornerShape(16.dp),
        color = palette.container,
        border = BorderStroke(if (status == DynamicCourseStatus.COMPLETED || highContrast) 1.5.dp else 1.dp, palette.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = course.code,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.accent,
                    maxLines = 1,
                    softWrap = false
                )
                Icon(
                    imageVector = when (status) {
                        DynamicCourseStatus.COMPLETED -> Icons.Filled.CheckCircle
                        DynamicCourseStatus.AVAILABLE -> Icons.Outlined.CheckCircleOutline
                        DynamicCourseStatus.LOCKED -> Icons.Rounded.Lock
                    },
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = course.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp
                ),
                fontWeight = FontWeight.SemiBold,
                color = palette.titleColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = palette.badgeBg
                ) {
                    Text(
                        text = "${course.workloadHours}h",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                if (course.isElective) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.35f else 0.72f)
                    ) {
                        Text(
                            text = "Eletiva",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.titleColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
