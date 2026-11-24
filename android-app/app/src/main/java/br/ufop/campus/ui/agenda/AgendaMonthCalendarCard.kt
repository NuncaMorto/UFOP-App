package br.ufop.campus.ui.agenda

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.DayActivityIndicator
import br.ufop.campus.ui.theme.CommitmentNeutralGray
import br.ufop.campus.ui.theme.parseHexColor
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun ExpandableSemesterCalendarCard(
    displayedYearMonth: YearMonth,
    selectedIsoDate: String,
    monthIndicators: Map<Int, DayActivityIndicator>,
    reduceMotion: Boolean,
    largeTouchTargets: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onSelectDay: (String) -> Unit
) {
    val today = LocalDate.now()
    val selectedBg = MaterialTheme.colorScheme.primaryContainer
    val selectedText = MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Navegação < Mês Ano > (alvos >= 48.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPreviousMonth,
                    modifier = Modifier
                        .size(if (largeTouchTargets) 56.dp else 48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = "Mês anterior",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = formatYearMonthTitle(displayedYearMonth),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )

                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier
                        .size(if (largeTouchTargets) 56.dp else 48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Próximo mês",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Pílulas 6/6 do semestre: Jan..Jun (meses 1..6) ou Jul..Dez (meses 7..12),
            // sempre preservando o ano atualmente exibido (displayedYearMonth.year).
            val halfStartMonth = if (displayedYearMonth.monthValue in 1..6) 1 else 7
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (monthValue in halfStartMonth until halfStartMonth + 6) {
                    val isCurrentMonth = monthValue == displayedYearMonth.monthValue
                    val pillBg by animateColorAsState(
                        targetValue = if (isCurrentMonth) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        animationSpec = if (reduceMotion) snap() else spring(),
                        label = "SemesterMonthPillBg"
                    )

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = if (largeTouchTargets) 56.dp else 48.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(role = Role.Button) {
                                onSelectMonth(YearMonth.of(displayedYearMonth.year, monthValue))
                            },
                        shape = RoundedCornerShape(50),
                        color = pillBg,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isCurrentMonth) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                            }
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = shortMonthPt(monthValue),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCurrentMonth) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (isCurrentMonth) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // Cabeçalho dos dias da semana, domingo primeiro (D S T Q Q S S) com descrição TalkBack completa
            val weekLettersWithFullNames = listOf(
                "D" to "Domingo",
                "S" to "Segunda-feira",
                "T" to "Terça-feira",
                "Q" to "Quarta-feira",
                "Q" to "Quinta-feira",
                "S" to "Sexta-feira",
                "S" to "Sábado"
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                weekLettersWithFullNames.forEach { (letter, fullName) ->
                    Text(
                        text = letter,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = fullName }
                    )
                }
            }

            // Grade completa de 7 colunas do mês com indicadores coloridos por dia
            val firstDayOfMonth = displayedYearMonth.atDay(1)
            // Domingo = 0, Segunda = 1, ..., Sábado = 6
            val leadingEmpty = firstDayOfMonth.dayOfWeek.value % 7
            val daysInMonth = displayedYearMonth.lengthOfMonth()
            val totalWeeks = (leadingEmpty + daysInMonth + 6) / 7

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (weekIdx in 0 until totalWeeks) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (colIdx in 0 until 7) {
                            val cellIndex = weekIdx * 7 + colIdx
                            val dayNumber = cellIndex - leadingEmpty + 1
                            if (dayNumber !in 1..daysInMonth) {
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                val cellDate = displayedYearMonth.atDay(dayNumber)
                                val iso = cellDate.toString()
                                val isSelected = iso == selectedIsoDate
                                val isToday = cellDate == today
                                val indicator = monthIndicators[dayNumber]

                                CalendarDayCell(
                                    dayNumber = dayNumber,
                                    isSelected = isSelected,
                                    isToday = isToday,
                                    indicator = indicator,
                                    selectedBg = selectedBg,
                                    selectedText = selectedText,
                                    reduceMotion = reduceMotion,
                                    largeTouchTargets = largeTouchTargets,
                                    onClick = { onSelectDay(iso) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayNumber: Int,
    isSelected: Boolean,
    isToday: Boolean,
    indicator: DayActivityIndicator?,
    selectedBg: Color,
    selectedText: Color,
    reduceMotion: Boolean,
    largeTouchTargets: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cellBg by animateColorAsState(
        targetValue = if (isSelected) selectedBg else Color.Transparent,
        animationSpec = if (reduceMotion) snap() else spring(),
        label = "CalendarDayBg"
    )
    val cellTextColor = when {
        isSelected -> selectedText
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    val dotColors = buildList {
        if (indicator != null) {
            indicator.colorsHex.forEach { hex ->
                add(parseHexColor(hex))
            }
            if (isEmpty()) {
                if (indicator.hasClasses || indicator.hasAssessments) {
                    add(MaterialTheme.colorScheme.primary)
                }
                if (indicator.hasCommitments) {
                    add(CommitmentNeutralGray)
                }
            }
        }
    }.take(3)

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = if (largeTouchTargets) 56.dp else 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) {
                selected = isSelected
                contentDescription = buildString {
                    append("Dia $dayNumber")
                    if (isToday) append(", hoje")
                    if (isSelected) append(", selecionado")
                    if (indicator?.hasClasses == true) append(", tem aulas")
                    if (indicator?.hasAssessments == true) append(", tem avaliações")
                    if (indicator?.hasCommitments == true) append(", tem compromissos")
                }
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(cellBg)
                .then(
                    if (isToday && !isSelected) {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dayNumber.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = cellTextColor,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
            )
        }

        // Pontos/barras indicadoras abaixo do número do dia
        Row(
            modifier = Modifier.height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dotColors.forEach { dotColor ->
                Box(
                    modifier = Modifier
                        .size(if (indicator?.hasAssessments == true && dotColors.size == 1) 6.dp else 4.5.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

private fun shortMonthPt(month: Int): String = when (month) {
    1 -> "Jan"
    2 -> "Fev"
    3 -> "Mar"
    4 -> "Abr"
    5 -> "Mai"
    6 -> "Jun"
    7 -> "Jul"
    8 -> "Ago"
    9 -> "Set"
    10 -> "Out"
    11 -> "Nov"
    12 -> "Dez"
    else -> "Mês"
}

internal fun formatYearMonthTitle(yearMonth: YearMonth): String {
    val monthName = when (yearMonth.monthValue) {
        1 -> "Janeiro"
        2 -> "Fevereiro"
        3 -> "Março"
        4 -> "Abril"
        5 -> "Maio"
        6 -> "Junho"
        7 -> "Julho"
        8 -> "Agosto"
        9 -> "Setembro"
        10 -> "Outubro"
        11 -> "Novembro"
        12 -> "Dezembro"
        else -> "Mês"
    }
    return "$monthName ${yearMonth.year}"
}
