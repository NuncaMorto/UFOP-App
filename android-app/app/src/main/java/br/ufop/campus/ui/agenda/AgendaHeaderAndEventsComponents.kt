package br.ufop.campus.ui.agenda

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.AcademicCalendarEvent
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.campusCardElevation
import br.ufop.campus.ui.theme.tabularNums
import java.time.LocalDate

@Composable
internal fun AgendaHeaderSection(
    campusCity: String,
    weatherTempC: Int,
    isDark: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Seu dia no campus",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Um dia de cada vez. Tudo por aqui.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        WeatherPillBadge(
            tempC = weatherTempC,
            isDark = isDark,
            modifier = Modifier
                .padding(top = 6.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Clima em $campusCity: $weatherTempC graus Celsius"
                }
        )
    }
}

@Composable
internal fun WeatherPillBadge(
    tempC: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = MaterialTheme.colorScheme.primaryContainer
    val textColor = MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bgColor,
        border = campusCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "☀ $tempC °C",
                style = MaterialTheme.typography.labelMedium.tabularNums(),
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
internal fun AgendaBannerCard(
    message: String,
    isError: Boolean,
    isDark: Boolean,
    largeTouchTargets: Boolean,
    dismissLabel: String,
    onDismiss: () -> Unit
) {
    val containerColor = if (isError) {
        StatusAbsentRed.copy(alpha = if (isDark) 0.20f else 0.12f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    }
    val borderColor = if (isError) {
        StatusAbsentRed.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    }
    val textColor = if (isError) {
        accessibleTextColor(StatusAbsentRed, isDark)
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val minTouch = if (largeTouchTargets) 56.dp else 48.dp

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = if (isError) BorderStroke(1.dp, borderColor) else campusCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(minTouch)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = dismissLabel,
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun AcademicEventsAccordionCard(
    events: List<AcademicCalendarEvent>,
    selectedIsoDate: String,
    isDark: Boolean,
    reduceMotion: Boolean,
    largeTouchTargets: Boolean
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(),
        label = "AcademicEventsChevron"
    )

    val referenceIso = remember(selectedIsoDate) {
        runCatching { LocalDate.parse(selectedIsoDate) }.getOrDefault(LocalDate.now()).toString()
    }
    val sortedEvents = remember(events) { events.sortedBy { it.eventDate } }
    val upcomingEvents = remember(sortedEvents, referenceIso) {
        sortedEvents.filter { it.eventDate >= referenceIso }
    }
    val displayedEvents = upcomingEvents.ifEmpty { sortedEvents }
    val badgeLabel = when (displayedEvents.size) {
        0 -> "UFOP"
        1 -> "1 evento"
        else -> "${displayedEvents.size} eventos"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CampusCardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = campusCardBorder(),
        shadowElevation = campusCardElevation()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = if (largeTouchTargets) 56.dp else 48.dp)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (expanded) "Recolher próximos eventos" else "Expandir próximos eventos"
                    ) { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Próximos eventos do Calendário Acadêmico",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Recolher eventos" else "Expandir eventos",
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (displayedEvents.isEmpty()) {
                        Text(
                            text = "Nenhum evento carregado ainda. Feriados, recessos e marcos do calendário acadêmico de referência aparecem aqui quando o calendário é atualizado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val rangeSummary = if (displayedEvents.size > 1) {
                            val first = displayedEvents.first()
                            val last = displayedEvents.last()
                            val scopeLabel = if (upcomingEvents.isNotEmpty()) "Próximos marcos" else "Calendário do semestre"
                            "$scopeLabel · ${first.dayNumber} ${first.monthShort} – ${last.dayNumber} ${last.monthShort}"
                        } else {
                            "Ordem cronológica do semestre"
                        }
                        Text(
                            text = rangeSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            displayedEvents.forEach { event ->
                                AcademicEventRow(event = event, isDark = isDark)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AcademicEventRow(
    event: AcademicCalendarEvent,
    isDark: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .width(52.dp)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = event.dayNumber,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = event.monthShort.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accessibleTextColor(MaterialTheme.colorScheme.primary, isDark),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (event.category.isNotBlank()) {
                Text(
                    text = event.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
