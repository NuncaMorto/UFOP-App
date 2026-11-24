package br.ufop.campus.ui.disciplines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.ffi.CourseScheduleSlotItem
import br.ufop.campus.core.ffi.DisciplineSummaryCard
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.parseHexColor
import br.ufop.campus.ui.theme.semanticAbsenceRiskColor
import br.ufop.campus.ui.theme.semanticAbsenceRiskLabel
import br.ufop.campus.ui.theme.semanticAbsenceRiskLevel

private data class DisciplineCardDisplayState(
    val accentColor: Color,
    val accessibleDeptTextColor: Color,
    val codeChipLabel: String,
    val scheduleSummary: String?,
    val progress: Float,
    val riskLevel: Int,
    val riskLabel: String,
    val riskColor: Color,
    val cardDescription: String
)

@Composable
private fun rememberDisciplineCardDisplayState(
    card: DisciplineSummaryCard,
    slots: List<CourseScheduleSlotItem>,
    isDark: Boolean
): DisciplineCardDisplayState {
    val accentColor = remember(card.colorHex) { parseHexColor(card.colorHex) }
    val accessibleDeptTextColor = remember(accentColor, isDark) {
        accessibleTextColor(accentColor, isDark)
    }
    val deptAcronym = remember(card.departmentId, card.departmentName) {
        card.departmentId.trim().uppercase()
            .ifBlank { card.departmentName.substringBefore(" ·").trim() }
    }
    val codeChipLabel = remember(deptAcronym, card.code) {
        card.code.trim().ifBlank { deptAcronym }
    }
    val scheduleSummary = remember(slots) { formatCourseSlotsSummary(slots) }
    val progress = (card.currentAbsences.toFloat() / card.maxAbsences.coerceAtLeast(1).toFloat())
        .coerceIn(0f, 1f)
    val riskLevel = semanticAbsenceRiskLevel(card.currentAbsences, card.maxAbsences)
    val riskLabel = semanticAbsenceRiskLabel(card.currentAbsences, card.maxAbsences)
    val riskColor = semanticAbsenceRiskColor(card.currentAbsences, card.maxAbsences, isDark)
    val cardDescription = buildString {
        append(card.name)
        append(", ")
        append(codeChipLabel)
        if (!scheduleSummary.isNullOrBlank()) {
            append(", horário ")
            append(scheduleSummary)
        }
        append(", ${card.currentAbsences} de ${card.maxAbsences} faltas, situação $riskLabel")
    }
    return DisciplineCardDisplayState(
        accentColor = accentColor,
        accessibleDeptTextColor = accessibleDeptTextColor,
        codeChipLabel = codeChipLabel,
        scheduleSummary = scheduleSummary,
        progress = progress,
        riskLevel = riskLevel,
        riskLabel = riskLabel,
        riskColor = riskColor,
        cardDescription = cardDescription
    )
}

@Composable
internal fun DisciplinesEmptyStateCard(
    searchQuery: String,
    minTouchTarget: Dp,
    onClearSearch: () -> Unit,
    onAddCourse: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (searchQuery.isNotBlank()) {
                    "Nenhuma disciplina encontrada para \"$searchQuery\""
                } else {
                    "Nenhuma disciplina cadastrada"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (searchQuery.isNotBlank()) {
                    "Tente outro termo de busca ou limpe o filtro."
                } else {
                    "Adicione uma disciplina manualmente ou importe seu Atestado de Matrícula (PDF) na aba Perfil."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            FilledTonalButton(
                onClick = {
                    if (searchQuery.isNotBlank()) {
                        onClearSearch()
                    } else {
                        onAddCourse()
                    }
                },
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                if (searchQuery.isBlank()) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = if (searchQuery.isNotBlank()) {
                        "Limpar busca"
                    } else {
                        "Adicionar disciplina"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatCourseSlotsSummary(slots: List<CourseScheduleSlotItem>): String? {
    if (slots.isEmpty()) return null
    val allEad = slots.all {
        it.shortLocation.equals("EAD", ignoreCase = true) ||
            it.building.contains("EAD", ignoreCase = true) ||
            it.building.contains("Distância", ignoreCase = true) ||
            it.building.contains("Distancia", ignoreCase = true)
    }
    if (allEad) {
        return "EAD · Ensino a distância"
    }
    val sorted = slots.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))
    val daysJoined = sorted.map {
        it.dayLabel.trim().lowercase().replaceFirstChar { c -> c.uppercase() }.take(3)
    }.distinct().joinToString("/")
    val primary = sorted.first()
    val cleanRoom = primary.room.trim()
    val cleanBuilding = primary.building.trim()
    val cleanShort = primary.shortLocation.trim().trimEnd('·', ' ')
    val locationLabel = if (cleanRoom.isNotBlank()) {
        if (cleanShort.contains(cleanRoom, ignoreCase = true) && cleanShort.isNotBlank()) {
            cleanShort
        } else {
            val shortBuilding = when {
                cleanBuilding.startsWith("Bloco de Salas", ignoreCase = true) ||
                    cleanBuilding.startsWith("Blocos de Salas", ignoreCase = true) -> "Bloco"
                else -> cleanBuilding
            }
            listOf(shortBuilding, cleanRoom).filter { it.isNotBlank() }.joinToString(" · ")
        }
    } else {
        cleanBuilding.ifBlank { cleanShort }
    }
    return if (locationLabel.isNotBlank()) {
        "$daysJoined ${primary.startTime} · $locationLabel"
    } else {
        "$daysJoined ${primary.startTime}"
    }
}

@Composable
private fun DisciplineCodeChip(
    label: String,
    accentColor: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
internal fun DisciplineGridCard(
    card: DisciplineSummaryCard,
    slots: List<CourseScheduleSlotItem>,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val state = rememberDisciplineCardDisplayState(card = card, slots = slots, isDark = isDark)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .heightIn(min = 128.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = state.cardDescription
            },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(state.accentColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DisciplineCodeChip(
                        label = state.codeChipLabel,
                        accentColor = state.accentColor,
                        textColor = state.accessibleDeptTextColor
                    )
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    append("${card.currentAbsences}")
                                }
                                withStyle(
                                    SpanStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    append("/${card.maxAbsences}")
                                }
                                if (state.riskLevel == 0) {
                                    withStyle(
                                        SpanStyle(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        append(" faltas")
                                    }
                                }
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (state.riskLevel >= 1) {
                            Spacer(modifier = Modifier.width(4.dp))
                            AbsenceRiskBadge(
                                riskLevel = state.riskLevel,
                                riskLabel = state.riskLabel,
                                riskColor = state.riskColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = state.riskColor,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun DisciplineListCard(
    card: DisciplineSummaryCard,
    slots: List<CourseScheduleSlotItem>,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val state = rememberDisciplineCardDisplayState(card = card, slots = slots, isDark = isDark)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = state.cardDescription
            },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(state.accentColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DisciplineCodeChip(
                            label = state.codeChipLabel,
                            accentColor = state.accentColor,
                            textColor = state.accessibleDeptTextColor
                        )
                        Text(
                            text = card.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = state.scheduleSummary ?: "Horário a definir",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    append("${card.currentAbsences}")
                                }
                                withStyle(
                                    SpanStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    append("/${card.maxAbsences}")
                                }
                            }
                        )
                        Text(
                            text = "faltas",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.riskLevel >= 1) {
                            Spacer(modifier = Modifier.height(2.dp))
                            AbsenceRiskBadge(
                                riskLevel = state.riskLevel,
                                riskLabel = state.riskLabel,
                                riskColor = state.riskColor
                            )
                        }
                    }
                }

                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = state.riskColor,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun AbsenceRiskBadge(
    riskLevel: Int,
    riskLabel: String,
    riskColor: Color
) {
    val riskIcon = if (riskLevel >= 2) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = riskColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, riskColor.copy(alpha = 0.32f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = riskIcon,
                contentDescription = null,
                tint = riskColor,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = riskLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = riskColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
