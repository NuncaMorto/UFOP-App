package br.ufop.campus.ui.agenda

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.ChevronRight
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
import br.ufop.campus.core.ffi.AgendaTimelineItem
import br.ufop.campus.core.ffi.AttendanceStatus
import br.ufop.campus.core.ffi.TimelineItemKind
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.parseHexColor

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
                text = formattedDate,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "$classesLabel • $commitmentsLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Surface(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .defaultMinSize(minHeight = if (largeTouchTargets) 52.dp else 38.dp)
                .clip(RoundedCornerShape(50))
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Adicionar compromisso",
                    onClick = onAddCommitmentClick
                ),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
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
            .clip(RoundedCornerShape(20.dp))
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
        shape = RoundedCornerShape(20.dp),
        color = if (preview.isHappeningNow) {
            StatusAttendingGreen.copy(alpha = if (isDark) 0.16f else 0.10f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.35f else 0.48f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = accentBase.copy(alpha = 0.40f)
        )
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
                    style = MaterialTheme.typography.bodySmall,
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
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
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
            .clip(RoundedCornerShape(20.dp))
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (inlineNextClassLabel != null) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
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
                    .clip(RoundedCornerShape(50))
                    .background(accentColor)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Linha superior: Título + Badge de clima (☀ 22 °C) no canto superior direito
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    item.weatherTempC?.let { tempC ->
                        Spacer(modifier = Modifier.width(8.dp))
                        WeatherPillBadge(
                            tempC = tempC,
                            isDark = isDark
                        )
                    }
                }

                // Horário + badge de status (aula) + destaque inline (Agora / Próxima aula) ou tag discreta (compromisso)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${item.startTime}–${item.endTime}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )

                    item.status?.let { status ->
                        AttendanceStatusBadge(status = status, isDark = isDark)
                    }

                    if (inlineNextClassLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "• $inlineNextClassLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant
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
