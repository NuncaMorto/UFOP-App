package br.ufop.campus.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.ffi.AgendaTimelineItem
import br.ufop.campus.core.ffi.AttendanceStatus
import br.ufop.campus.core.ffi.TimelineItemKind
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.CampusPillRadius
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.campusCardElevation
import br.ufop.campus.ui.theme.parseHexColor
import br.ufop.campus.ui.theme.tabularNums
import java.time.LocalTime
import java.util.Locale

@Composable
internal fun DaySummaryHeader(
    formattedDate: String,
    classesCount: Int,
    commitmentsCount: Int,
    largeTouchTargets: Boolean,
    onAddCommitmentClick: () -> Unit
) {
    val classesLabel = if (classesCount == 1) "1 aula" else "$classesCount aulas"
    val commitmentsLabel = if (commitmentsCount == 1) "1 compromisso" else "$commitmentsCount compromissos"
    val displayDate = remember(formattedDate) { formatDaySummaryDateNoOrphan(formattedDate) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = displayDate,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 23.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "$classesLabel • $commitmentsLabel",
                style = MaterialTheme.typography.bodyMedium.tabularNums(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Surface(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .defaultMinSize(minHeight = if (largeTouchTargets) 52.dp else 38.dp)
                .clip(RoundedCornerShape(CampusPillRadius))
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Adicionar compromisso",
                    onClick = onAddCommitmentClick
                ),
            shape = RoundedCornerShape(CampusPillRadius),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = campusCardBorder()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "+ Compromisso",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = if (largeTouchTargets) 12.dp else 7.dp
                    )
                )
            }
        }
    }
}

@Composable
internal fun NextClassHeroCard(
    preview: NextClassPreview,
    isSameSelectedDay: Boolean,
    isDark: Boolean,
    largeTouchTargets: Boolean,
    onJumpToNextClass: () -> Unit
) {
    val accentBase = if (preview.isHappeningNow) StatusAttendingGreen else MaterialTheme.colorScheme.primary
    val accentTextColor = accessibleTextColor(accentBase, isDark)
    val cleanLocation = remember(preview.location) { sanitizeAgendaLocation(preview.location) }
    val statusBadgeLabel = if (preview.isHappeningNow) {
        "Em andamento agora"
    } else {
        "Próxima aula · ${preview.dayLabel}"
    }
    val isNavigable = !isSameSelectedDay
    val cardDescription = buildString {
        append(statusBadgeLabel)
        append(": ${preview.courseName}")
        append(", das ${preview.startTime} às ${preview.endTime}")
        if (cleanLocation.isNotBlank()) append(", local $cleanLocation")
        if (isNavigable) append(". Toque para abrir este dia na agenda")
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (largeTouchTargets) 64.dp else 56.dp)
            .clip(RoundedCornerShape(CampusCardRadius))
            .semantics(mergeDescendants = true) {
                contentDescription = cardDescription
                if (isNavigable) role = Role.Button
            }
            .then(
                if (isNavigable) {
                    Modifier.clickable(
                        onClickLabel = "Ir para o dia da próxima aula",
                        onClick = onJumpToNextClass
                    )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(CampusCardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = campusCardBorder(1.5.dp),
        shadowElevation = campusCardElevation()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentTextColor)
                    )
                    Text(
                        text = statusBadgeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = accentTextColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = preview.courseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append("${preview.startTime}–${preview.endTime}")
                        if (cleanLocation.isNotBlank()) {
                            append(" · $cleanLocation")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.tabularNums(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isNavigable) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = accentTextColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
internal fun AgendaEmptyStateCard(
    hasCoursesInSemester: Boolean,
    nextClassPreview: NextClassPreview?,
    largeTouchTargets: Boolean,
    onJumpToNextClass: () -> Unit,
    onAddCommitmentClick: () -> Unit
) {
    val actionableNextClass = if (hasCoursesInSemester) nextClassPreview else null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CampusCardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = campusCardBorder(),
        shadowElevation = campusCardElevation()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (hasCoursesInSemester) {
                    "Dia livre no campus"
                } else {
                    "Sua agenda está pronta para começar"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasCoursesInSemester) {
                    "Nenhuma aula ou compromisso agendado para este dia."
                } else {
                    "Adicione um compromisso aqui ou importe seu Atestado de Matrícula (PDF) nas abas Disciplinas ou Perfil para preencher suas aulas."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .defaultMinSize(minHeight = if (largeTouchTargets) 54.dp else 42.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        role = Role.Button,
                        onClick = if (actionableNextClass != null) onJumpToNextClass else onAddCommitmentClick
                    ),
                shape = RoundedCornerShape(CampusPillRadius),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = campusCardBorder()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (actionableNextClass != null) {
                            "Ver próxima aula (${actionableNextClass.dayLabel})"
                        } else {
                            "+ Adicionar compromisso"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = if (largeTouchTargets) 12.dp else 9.dp
                        )
                    )
                }
            }
        }
    }
}

private fun sanitizeAgendaLocation(raw: String): String {
    return raw
        .replace('•', '·')
        .replace(Regex("\\s+[–—-]\\s+"), " · ")
        .split("·")
        .map { segment ->
            segment.trim().trim { ch ->
                ch == '-' || ch == '–' || ch == '—' || ch == '·' || ch == '/' || ch == ',' || ch == ':' || ch == '.'
            }.trim()
        }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" · ")
}

private fun sanitizeAgendaSubtitle(raw: String): String {
    return raw.split("·")
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals("Docente UFOP", ignoreCase = true) }
        .joinToString(" · ")
}

private fun attendanceStatusLabel(status: AttendanceStatus): String = when (status) {
    AttendanceStatus.ATTENDING -> "Vou à aula"
    AttendanceStatus.ABSENT -> "Vou faltar"
    AttendanceStatus.CANCELLED -> "Aula cancelada"
}

@Composable
internal fun AgendaTimelineCard(
    item: AgendaTimelineItem,
    isDark: Boolean,
    largeTouchTargets: Boolean,
    inlineNextClassLabel: String? = null,
    onClick: () -> Unit
) {
    val accentColor = parseHexColor(item.colorHex)
    val isClickable = (item.kind == TimelineItemKind.CLASS_SESSION && item.slotId != null) ||
        item.kind == TimelineItemKind.COMMITMENT
    val cleanLocation = remember(item.location) { sanitizeAgendaLocation(item.location) }
    val cleanSubtitle = remember(item.subtitle) { sanitizeAgendaSubtitle(item.subtitle) }

    val cardContentDescription = remember(item, cleanLocation, cleanSubtitle, inlineNextClassLabel) {
        buildString {
            if (item.kind == TimelineItemKind.COMMITMENT) {
                append("Compromisso: ")
            } else {
                append("Aula: ")
            }
            append(item.title)
            if (inlineNextClassLabel != null) append(". $inlineNextClassLabel")
            append(". Horário: ${item.startTime} às ${item.endTime}")
            item.status?.let { append(". Status: ${attendanceStatusLabel(it)}") }
            if (cleanLocation.isNotBlank()) append(". Local: $cleanLocation")
            if (cleanSubtitle.isNotBlank()) append(". $cleanSubtitle")
            item.weatherTempC?.let { append(". Previsão: $it graus Celsius") }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (largeTouchTargets) 56.dp else 0.dp)
            .clip(RoundedCornerShape(CampusCardRadius))
            .semantics(mergeDescendants = true) {
                contentDescription = cardContentDescription
                if (isClickable) role = Role.Button
            }
            .then(
                if (isClickable) {
                    Modifier.clickable(
                        onClickLabel = if (item.kind == TimelineItemKind.CLASS_SESSION) {
                            "Registrar presença ou editar aula"
                        } else {
                            "Abrir detalhes do compromisso"
                        },
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(CampusCardRadius),
        color = if (inlineNextClassLabel != null) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = campusCardBorder(if (inlineNextClassLabel != null) 1.5.dp else 1.dp),
        shadowElevation = campusCardElevation()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Barra vertical colorida à esquerda (4dp de largura, cantos arredondados)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(CampusPillRadius))
                    .background(accentColor)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Linha superior: Título com até 2 linhas para nomes longos
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                // Horário tabular + badge de status + pílula viva (AGORA / EM X MIN) ou compromisso
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${item.startTime}–${item.endTime}",
                        style = MaterialTheme.typography.labelLarge.tabularNums(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )

                    item.status?.let { status ->
                        AttendanceStatusBadge(status = status, isDark = isDark)
                    }

                    if (inlineNextClassLabel != null) {
                        val isNowBadge = inlineNextClassLabel.equals("AGORA", ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(CampusPillRadius),
                            color = if (isNowBadge) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        ) {
                            Text(
                                text = inlineNextClassLabel.uppercase(Locale("pt", "BR")),
                                style = MaterialTheme.typography.labelSmall.tabularNums(),
                                color = if (isNowBadge) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (item.kind == TimelineItemKind.COMMITMENT) {
                        Surface(
                            shape = RoundedCornerShape(CampusPillRadius),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "Compromisso",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Localização / EAD
                if (cleanLocation.isNotBlank()) {
                    Text(
                        text = cleanLocation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Subtítulo / Docente (oculta se vazio, sem separador solto nem fallback "Docente UFOP")
                if (cleanSubtitle.isNotBlank()) {
                    Text(
                        text = cleanSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AttendanceStatusBadge(
    status: AttendanceStatus,
    isDark: Boolean
) {
    val (label, badgeColor) = when (status) {
        AttendanceStatus.ATTENDING -> "Vou à aula" to StatusAttendingGreen
        AttendanceStatus.ABSENT -> "Vou faltar" to StatusAbsentRed
        AttendanceStatus.CANCELLED -> "Aula cancelada" to StatusCancelledAmber
    }
    val readableColor = accessibleTextColor(badgeColor, isDark)

    Surface(
        shape = RoundedCornerShape(50),
        color = badgeColor.copy(alpha = if (isDark) 0.20f else 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(readableColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = readableColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun FreeWindowDivider(gapMinutes: Int) {
    val label = remember(gapMinutes) { "Janela livre · ${formatFreeWindowDuration(gapMinutes)}" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
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
                text = label,
                style = MaterialTheme.typography.labelSmall.tabularNums(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}

internal fun resolveTodayClassHighlightLabel(
    item: AgendaTimelineItem,
    nextClassOnSelectedDay: NextClassPreview?
): String? {
    val nextClass = nextClassOnSelectedDay ?: return null
    if (item.kind != TimelineItemKind.CLASS_SESSION || item.status == AttendanceStatus.CANCELLED) {
        return null
    }
    val matches = (nextClass.slotId != null && item.slotId == nextClass.slotId) ||
        (item.title == nextClass.courseName && item.startTime == nextClass.startTime)
    if (!matches) return null
    if (nextClass.isHappeningNow) return "AGORA"

    val now = LocalTime.now()
    val nowMin = now.hour * 60 + now.minute
    val startMin = parseAgendaMinutesOrNull(item.startTime) ?: return "PRÓXIMA"
    val diff = startMin - nowMin
    return when {
        diff <= 0 -> "AGORA"
        diff < 60 -> "EM $diff MIN"
        diff < 360 -> {
            val h = diff / 60
            val m = diff % 60
            if (m == 0) "EM ${h}H" else String.format(Locale.ROOT, "EM %dH%02d", h, m)
        }
        else -> "PRÓXIMA"
    }
}

internal fun computeFreeWindowMinutesBetween(
    previousItem: AgendaTimelineItem?,
    currentItem: AgendaTimelineItem
): Int? {
    val prev = previousItem ?: return null
    if (prev.kind != TimelineItemKind.CLASS_SESSION || currentItem.kind != TimelineItemKind.CLASS_SESSION) {
        return null
    }
    if (prev.status == AttendanceStatus.CANCELLED || currentItem.status == AttendanceStatus.CANCELLED) {
        return null
    }
    val prevEnd = parseAgendaMinutesOrNull(prev.endTime) ?: return null
    val currStart = parseAgendaMinutesOrNull(currentItem.startTime) ?: return null
    val gap = currStart - prevEnd
    return if (gap >= 50) gap else null
}

internal fun formatFreeWindowDuration(gapMinutes: Int): String {
    val hours = gapMinutes / 60
    val mins = gapMinutes % 60
    return when {
        hours == 0 -> "${mins}min"
        mins == 0 -> "${hours}h"
        else -> String.format(Locale.ROOT, "%dh%02d", hours, mins)
    }
}

private fun parseAgendaMinutesOrNull(hhmm: String): Int? {
    val parts = hhmm.trim().split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

private fun formatDaySummaryDateNoOrphan(raw: String): String {
    val idx = raw.lastIndexOf(" de ")
    if (idx < 0) return raw
    return raw.substring(0, idx) + " de\u00A0" + raw.substring(idx + 4)
}
