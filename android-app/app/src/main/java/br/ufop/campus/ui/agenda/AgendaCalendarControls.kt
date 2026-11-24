package br.ufop.campus.ui.agenda

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.DayPillItem

@Composable
internal fun MonthAndWeekSelectorRow(
    monthYearLabel: String,
    weekNumber: Int,
    isCalendarExpanded: Boolean,
    showTodayButton: Boolean,
    reduceMotion: Boolean,
    largeTouchTargets: Boolean,
    onToggleCalendar: () -> Unit,
    onJumpToToday: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isCalendarExpanded) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(),
        label = "MonthChevronRotation"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .weight(1f, fill = false)
                .minimumInteractiveComponentSize()
                .defaultMinSize(minHeight = if (largeTouchTargets) 52.dp else 36.dp)
                .clip(RoundedCornerShape(50))
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (isCalendarExpanded) "Recolher calendário mensal" else "Expandir calendário mensal",
                    onClick = onToggleCalendar
                ),
            shape = RoundedCornerShape(50),
            color = if (isCalendarExpanded) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                Color.Transparent
            }
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (isCalendarExpanded) 12.dp else 4.dp,
                    vertical = if (largeTouchTargets) 10.dp else 6.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = monthYearLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isCalendarExpanded) "Recolher calendário" else "Expandir calendário mensal",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = showTodayButton,
                enter = if (reduceMotion) EnterTransition.None else fadeIn() + scaleIn(),
                exit = if (reduceMotion) ExitTransition.None else fadeOut() + scaleOut()
            ) {
                TodayFloatingPill(
                    largeTouchTargets = largeTouchTargets,
                    onClick = onJumpToToday
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = "SEMANA $weekNumber",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** Pílula compacta "Hoje" para voltar ao dia atual com um toque. */
@Composable
private fun TodayFloatingPill(
    largeTouchTargets: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = if (largeTouchTargets) 50.dp else 34.dp)
            .clip(RoundedCornerShape(50))
            .clickable(
                role = Role.Button,
                onClickLabel = "Ir para o dia de hoje",
                onClick = onClick
            ),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 2.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (largeTouchTargets) 14.dp else 10.dp,
                vertical = if (largeTouchTargets) 12.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Today,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Hoje",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

private fun fullWeekdayNameFromShort(dayShort: String): String = when (dayShort.trim().uppercase()) {
    "DOM" -> "Domingo"
    "SEG" -> "Segunda-feira"
    "TER" -> "Terça-feira"
    "QUA" -> "Quarta-feira"
    "QUI" -> "Quinta-feira"
    "SEX" -> "Sexta-feira"
    "SÁB", "SAB" -> "Sábado"
    else -> dayShort
}

@Composable
internal fun WeekDayPillsRow(
    days: List<DayPillItem>,
    selectedIsoDate: String,
    isDark: Boolean,
    largeTouchTargets: Boolean,
    onSelectDay: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        days.forEach { day ->
            val isSelected = day.isoDate == selectedIsoDate
            val containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            val secondaryTextColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (isDark) 0.85f else 0.8f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val fullDayName = fullWeekdayNameFromShort(day.dayShort)

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = if (largeTouchTargets) 64.dp else 56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .semantics(mergeDescendants = true) {
                        selected = isSelected
                        role = Role.Tab
                        contentDescription = "$fullDayName, dia ${day.dayNumber}" +
                            if (isSelected) ", selecionado" else ""
                    }
                    .clickable { onSelectDay(day.isoDate) },
                shape = RoundedCornerShape(20.dp),
                color = containerColor,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = day.dayShort,
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryTextColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = day.dayNumber.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
