package br.ufop.campus.ui.disciplines

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.AbsenceHistoryEntry
import br.ufop.campus.core.ffi.AssessmentItem
import br.ufop.campus.core.ffi.AttendanceStatus
import br.ufop.campus.core.ffi.CourseScheduleSlotItem
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.accessibleTextColor

internal val EDIT_WORKLOAD_PRESETS = listOf(30, 45, 60, 72, 90, 120)
internal enum class SyllabusFeedbackTone {
    SUCCESS,
    WARNING,
    ERROR
}
@Composable
internal fun DisciplineInfoDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(10.dp),
            color = accentColor.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
@Composable
internal fun DisciplineAccordionCard(
    title: String,
    summaryPillText: String,
    accentColor: Color,
    accessibleAccent: Color = accentColor,
    expanded: Boolean,
    reduceMotion: Boolean = false,
    minTouchTarget: Dp = 48.dp,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(),
        label = "DisciplineAccordionChevron"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) {
                accentColor.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = minTouchTarget)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f))
                ) {
                    Text(
                        text = summaryPillText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accessibleAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Recolher $title" else "Expandir $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(chevronRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = if (reduceMotion) EnterTransition.None else expandVertically() + fadeIn(),
                exit = if (reduceMotion) ExitTransition.None else shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    content = content
                )
            }
        }
    }
}
@Composable
internal fun ScheduleSlotRowCard(
    slot: CourseScheduleSlotItem,
    accentColor: androidx.compose.ui.graphics.Color,
    accessibleAccent: androidx.compose.ui.graphics.Color = accentColor,
    isEditing: Boolean,
    minTouchTarget: Dp = 48.dp,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isEditing) {
            accentColor.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isEditing) {
                accentColor.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = accessibleAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val timeAndShort = if (slot.shortLocation.isNotBlank()) {
                    "${slot.dayLabel} ${slot.startTime}–${slot.endTime} · ${slot.shortLocation}"
                } else {
                    "${slot.dayLabel} ${slot.startTime}–${slot.endTime}"
                }
                val buildingAndRoomText = if (slot.room.isNotBlank()) {
                    "${slot.building} · ${slot.room}"
                } else {
                    slot.building
                }
                Text(
                    text = timeAndShort,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (buildingAndRoomText.isNotBlank()) {
                    Text(
                        text = buildingAndRoomText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(minTouchTarget)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Editar horário",
                    tint = if (isEditing) accessibleAccent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(minTouchTarget)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Remover horário",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
@Composable
internal fun AbsenceHistoryRowCard(
    entry: AbsenceHistoryEntry,
    isDark: Boolean = false
) {
    val isAbsent = entry.status == AttendanceStatus.ABSENT
    val pillColor = accessibleTextColor(
        baseColor = if (isAbsent) StatusAbsentRed else StatusAttendingGreen,
        isDarkSurface = isDark
    )
    val pillLabel = if (isAbsent) {
        "Falta (+${entry.classWeight})"
    } else {
        "Cancelada (0 faltas)"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.formattedDate,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${entry.startTime}–${entry.endTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = pillColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, pillColor.copy(alpha = 0.35f))
            ) {
                Text(
                    text = pillLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = pillColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
@Composable
internal fun AssessmentRowCard(
    item: AssessmentItem,
    accentColor: androidx.compose.ui.graphics.Color,
    courseBadgeText: String? = null,
    isDark: Boolean = LocalCampusThemeController.current.isDark,
    minTouchTarget: Dp = 48.dp,
    onToggleCompleted: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val isCompleted = item.completed
    val baseBadgeColor = if (isCompleted) StatusAttendingGreen else accentColor
    val badgeColor = accessibleTextColor(baseBadgeColor, isDark)

    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isCompleted) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isCompleted) {
                StatusAttendingGreen.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onToggleCompleted != null) {
                IconButton(
                    onClick = onToggleCompleted,
                    modifier = Modifier.size(minTouchTarget)
                ) {
                    Icon(
                        imageVector = if (isCompleted) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Outlined.RadioButtonUnchecked
                        },
                        contentDescription = if (isCompleted) {
                            "Marcar como pendente"
                        } else {
                            "Marcar como concluída"
                        },
                        tint = if (isCompleted) {
                            accessibleTextColor(StatusAttendingGreen, isDark)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bloco de data à esquerda em pílula quadrada (ex: 20 NOV, 24 NOV, 27 NOV)
                Surface(
                    modifier = Modifier.size(width = 52.dp, height = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = baseBadgeColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, baseBadgeColor.copy(alpha = 0.28f))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = item.dayNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                        Text(
                            text = item.monthShort.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (!courseBadgeText.isNullOrBlank()) {
                        Text(
                            text = courseBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = accessibleTextColor(accentColor, isDark),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (isCompleted) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val statusSuffix = if (isCompleted) " · Concluída" else ""
                    val timePart = if (item.dueTime.isNotBlank()) " · ${item.dueTime}" else ""
                    Text(
                        text = "${item.kind}$timePart$statusSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCompleted) {
                            accessibleTextColor(StatusAttendingGreen, isDark)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(minTouchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Remover avaliação",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
internal fun normalizeAssessmentDateInput(rawDate: String): String {
    val trimmed = rawDate.trim()
    if (trimmed.isEmpty()) return "2026-11-28"
    if (trimmed.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
        return trimmed
    }
    val parts = trimmed.uppercase().split(Regex("[\\s/.-]+")).filter { it.isNotBlank() }
    if (parts.size >= 2) {
        val day = parts[0].padStart(2, '0').takeLast(2)
        val monthMap = mapOf(
            "JAN" to "01", "FEV" to "02", "MAR" to "03", "ABR" to "04",
            "MAI" to "05", "JUN" to "06", "JUL" to "07", "AGO" to "08",
            "SET" to "09", "OUT" to "10", "NOV" to "11", "DEZ" to "12"
        )
        val monthToken = parts[1]
        val monthNum = monthMap[monthToken.take(3)] ?: monthToken.padStart(2, '0').takeLast(2)
        val year = if (parts.size >= 3 && parts[2].matches(Regex("\\d{4}"))) parts[2] else "2026"
        return "$year-$monthNum-$day"
    }
    return trimmed
}
