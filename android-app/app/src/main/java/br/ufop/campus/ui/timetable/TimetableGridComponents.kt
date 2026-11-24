package br.ufop.campus.ui.timetable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.ffi.DepartmentColorOption
import br.ufop.campus.core.ffi.TimetableCell
import br.ufop.campus.core.ffi.TimetableRow
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.parseHexColor

@Composable
internal fun TimetableWeeklyGrid(
    displayDays: List<Pair<Int, String>>,
    displayRows: List<TimetableRow>,
    isReducedMode: Boolean,
    largeTouchTargets: Boolean,
    isLandscape: Boolean,
    isDark: Boolean,
    horizontalGridScrollState: ScrollState,
    onCellClick: (cell: TimetableCell, dayOfWeek: Int, startTime: String, endTime: String) -> Unit
) {
    val rowSpacing = if (isReducedMode) 4.dp else 8.dp
    val colSpacing = if (isReducedMode) 5.dp else 8.dp
    val gridPadding = if (isReducedMode) 8.dp else 12.dp
    val headerRowHeight = if (isReducedMode) REDUCED_HEADER_ROW_HEIGHT else HEADER_ROW_HEIGHT
    val cellRowHeight = if (isReducedMode) {
        if (largeTouchTargets) 56.dp else REDUCED_CELL_ROW_HEIGHT
    } else {
        if (largeTouchTargets) 84.dp else CELL_ROW_HEIGHT
    }
    val timeColWidth = if (isReducedMode) REDUCED_TIME_COLUMN_WIDTH else TIME_COLUMN_WIDTH

    // Matriz da Grade: em Paisagem distribui SEG..SÁB em weight(1f); em Retrato permite rolagem horizontal confortável
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isReducedMode) 16.dp else 22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(gridPadding),
            horizontalArrangement = Arrangement.spacedBy(colSpacing)
        ) {
            // Coluna fixa à esquerda para faixas de horário (texto >= 11.sp mesmo em modo compacto)
            Column(
                modifier = Modifier.width(timeColWidth),
                verticalArrangement = Arrangement.spacedBy(rowSpacing)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerRowHeight),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = if (isReducedMode) "HORA" else "HORÁRIO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                displayRows.forEach { row ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cellRowHeight),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (isReducedMode) {
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = row.startTime,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        lineHeight = 13.sp
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = row.endTime,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        lineHeight = 13.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        } else {
                            Column {
                                Text(
                                    text = row.startTime,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = row.endTime,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 6 colunas da semana (SEG..SÁB)
            val daysRowModifier = if (isLandscape) {
                Modifier.weight(1f)
            } else {
                Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalGridScrollState)
            }
            Row(
                modifier = daysRowModifier,
                horizontalArrangement = Arrangement.spacedBy(colSpacing)
            ) {
                displayDays.forEach { (dayNumber, dayShort) ->
                    val dayColumnModifier = if (isLandscape) {
                        Modifier.weight(1f)
                    } else {
                        Modifier.width(if (isReducedMode) 82.dp else 112.dp)
                    }
                    val fullDayName = fullDayNamePt(dayNumber)
                    Column(
                        modifier = dayColumnModifier,
                        verticalArrangement = Arrangement.spacedBy(rowSpacing)
                    ) {
                        // Header do dia (SEG, TER, QUA, QUI, SEX, SÁB)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(headerRowHeight)
                                .semantics { contentDescription = fullDayName },
                            shape = RoundedCornerShape(if (isReducedMode) 8.dp else 12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = dayShort,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = if (isReducedMode) 11.sp else 12.sp
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                        }

                        // Células para cada faixa de horário neste dia
                        displayRows.forEach { row ->
                            val cell = row.cellsByDay[dayNumber]
                            if (cell != null) {
                                OccupiedTimetableCellCard(
                                    cell = cell,
                                    dayFullName = fullDayName,
                                    startTime = row.startTime,
                                    endTime = row.endTime,
                                    cellHeight = cellRowHeight,
                                    isReducedMode = isReducedMode,
                                    isDark = isDark,
                                    onClick = {
                                        onCellClick(cell, dayNumber, row.startTime, row.endTime)
                                    }
                                )
                            } else {
                                EmptyTimetableCellCard(
                                    cellHeight = cellRowHeight,
                                    isReducedMode = isReducedMode
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimetableDepartmentLegend(
    legend: List<DepartmentColorOption>,
    semester: String,
    isReducedMode: Boolean
) {
    // Legenda de departamentos sempre visível (inclusive no modo compacto/reduzido)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            legend.forEach { dept: DepartmentColorOption ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(dept.colorHex))
                    )
                    Text(
                        text = dept.name,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = if (isReducedMode) 11.sp else 12.sp
                        ),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "Semestre $semester",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = if (isReducedMode) 11.sp else 12.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun TimetableCellDetailDialog(
    detailInfo: SelectedTimetableCellDetail,
    isDark: Boolean,
    minTouchTarget: Dp,
    onDismiss: () -> Unit
) {
    // IHC-05: Diálogo de detalhes completos ao tocar em uma célula ocupada da grade
    val accentColor = parseHexColor(detailInfo.cell.colorHex)
    val readableAccent = accessibleTextColor(accentColor, isDark)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Text(
                        text = buildString {
                            append(detailInfo.cell.departmentName.ifBlank { "UFOP" })
                            if (detailInfo.cell.code.isNotBlank()) {
                                append(" · ${detailInfo.cell.code}")
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = readableAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = detailInfo.cell.courseName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${detailInfo.dayFullName} · ${detailInfo.startTime} às ${detailInfo.endTime}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Local: ${detailInfo.fullLocation}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (detailInfo.professor.isNotBlank()) {
                    Text(
                        text = "Professor(a): ${detailInfo.professor}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
            ) {
                Text(
                    text = "Fechar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun OccupiedTimetableCellCard(
    cell: TimetableCell,
    dayFullName: String,
    startTime: String,
    endTime: String,
    cellHeight: Dp,
    isReducedMode: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val accentColor = remember(cell.colorHex) { parseHexColor(cell.colorHex) }
    val readableAccent = remember(accentColor, isDark) { accessibleTextColor(accentColor, isDark) }
    val cleanLocation = remember(cell.shortLocation) { formatTimetableShortLocation(cell.shortLocation) }
    val classCountBadge = remember(startTime, endTime) {
        if (startTime.isNotBlank() && endTime.isNotBlank()) {
            formatBlockClassCountBadge(startTime, endTime)
        } else {
            null
        }
    }
    val cellDescription = remember(cell, dayFullName, startTime, endTime, cleanLocation) {
        buildString {
            append(cell.courseName)
            if (cell.code.isNotBlank()) append(", código ${cell.code}")
            append(", $dayFullName, $startTime às $endTime")
            append(", local $cleanLocation")
            append(". Toque para ver detalhes")
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(cellHeight)
            .clip(RoundedCornerShape(if (isReducedMode) 10.dp else 14.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = cellDescription
                role = Role.Button
            }
            .clickable(
                onClickLabel = "Ver detalhes de ${cell.courseName}",
                onClick = onClick
            ),
        shape = RoundedCornerShape(if (isReducedMode) 10.dp else 14.dp),
        color = accentColor.copy(alpha = if (isDark) 0.18f else 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Barra lateral na cor padronizada do departamento
            Box(
                modifier = Modifier
                    .width(if (isReducedMode) 4.dp else 5.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            if (isReducedMode) {
                val shortCode = cell.code.ifBlank { cell.courseName.take(10) }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = shortCode,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            lineHeight = 13.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = cleanLocation,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = cell.courseName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = cleanLocation,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (classCountBadge != null) {
                            Text(
                                text = classCountBadge,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = readableAccent,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTimetableCellCard(
    cellHeight: Dp,
    isReducedMode: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(cellHeight),
        shape = RoundedCornerShape(if (isReducedMode) 10.dp else 14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "—",
                style = if (isReducedMode) {
                    MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
                textAlign = TextAlign.Center
            )
        }
    }
}
