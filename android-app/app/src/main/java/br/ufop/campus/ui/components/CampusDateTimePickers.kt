package br.ufop.campus.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.tabularNums
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_MONTHS_PT_BR = arrayOf(
    "JAN", "FEV", "MAR", "ABR", "MAI", "JUN",
    "JUL", "AGO", "SET", "OUT", "NOV", "DEZ"
)

/**
 * Retorna a data de hoje no formato ISO-8601 ("AAAA-MM-DD").
 */
fun defaultTodayIsoDate(): String = LocalDate.now().toString()

/**
 * Converte data ISO ("2026-11-21") para formato brasileiro ("21/11/2026").
 * Retorna o valor original em caso de erro na análise.
 */
fun formatIsoToBrDate(isoDate: String): String {
    return try {
        val parsed = LocalDate.parse(isoDate.trim())
        parsed.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch (_: Exception) {
        isoDate
    }
}

/**
 * Converte data ISO ("2026-11-21") para formato abreviado legível ("21 NOV").
 * Retorna o valor original em caso de erro na análise.
 */
fun formatIsoToShortPtBr(isoDate: String): String {
    return try {
        val parsed = LocalDate.parse(isoDate.trim())
        val day = String.format(Locale.ROOT, "%02d", parsed.dayOfMonth)
        val month = SHORT_MONTHS_PT_BR[parsed.monthValue - 1]
        "$day $month"
    } catch (_: Exception) {
        isoDate
    }
}

/**
 * Converte data ISO ("AAAA-MM-DD") para milissegundos UTC no início do dia (00:00 UTC).
 * Necessário para compatibilidade com o Material 3 DatePicker sem retroceder o dia no fuso horário do Brasil (UTC-3).
 */
fun isoDateToUtcMillis(isoDate: String): Long? {
    return try {
        LocalDate.parse(isoDate.trim())
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    } catch (_: Exception) {
        null
    }
}

/**
 * Converte milissegundos UTC para string de data ISO ("AAAA-MM-DD") interpretada em UTC.
 * Evita perdas de dia causadas pela diferença de fuso horário.
 */
fun utcMillisToIsoDate(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toString()
}

/**
 * Faz o parse de string no formato "HH:mm" retornando Pair(hora, minuto) ou null se inválida.
 */
fun parseHourMinuteOrNull(time24h: String): Pair<Int, Int>? {
    val parts = time24h.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return Pair(h, m)
}

/**
 * Retorna true se o horário de término for estritamente posterior ao horário de início.
 */
fun isEndTimeAfterStart(startTime: String, endTime: String): Boolean {
    val start = parseHourMinuteOrNull(startTime) ?: return false
    val end = parseHourMinuteOrNull(endTime) ?: return false
    val startMinutes = start.first * 60 + start.second
    val endMinutes = end.first * 60 + end.second
    return endMinutes > startMinutes
}

/**
 * Campo seletor de data Material 3 clicável com DatePickerDialog nativo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusDatePickerField(
    label: String,
    isoDate: String,
    onIsoDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
    minHeight: Dp = 52.dp
) {
    var showDialog by remember { mutableStateOf(false) }

    val highContrast = LocalCampusAccessibility.current.settings.highContrastMode

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Selecionar $label"
            ) {
                showDialog = true
            },
        shape = RoundedCornerShape(14.dp),
        border = if (highContrast) campusCardBorder(1.5.dp) else null,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isoDate.isNotBlank()) formatIsoToBrDate(isoDate) else "Selecione a data",
                    style = MaterialTheme.typography.bodyMedium.tabularNums(),
                    fontWeight = FontWeight.Medium,
                    color = if (isoDate.isNotBlank()) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (!supportingText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }

    if (showDialog) {
        val initialMillis = remember(isoDate) { isoDateToUtcMillis(isoDate) }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis
        )

        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onIsoDateSelected(utcMillisToIsoDate(millis))
                        }
                        showDialog = false
                    }
                ) {
                    Text(
                        text = "Confirmar",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text(
                        text = "Cancelar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * Campo seletor de horário Material 3 clicável com AlertDialog, TimePicker nativo e alternância para TimeInput (teclado).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusTimePickerField(
    label: String,
    time24h: String,
    onTimeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    minHeight: Dp = 52.dp
) {
    var showDialog by remember { mutableStateOf(false) }

    val highContrast = LocalCampusAccessibility.current.settings.highContrastMode
    val fieldBorder = when {
        isError -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
        highContrast -> campusCardBorder(1.5.dp)
        else -> null
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Selecionar $label"
            ) {
                showDialog = true
            },
        shape = RoundedCornerShape(14.dp),
        border = fieldBorder,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (time24h.isNotBlank()) time24h else "Selecione o horário",
                    style = MaterialTheme.typography.bodyMedium.tabularNums(),
                    fontWeight = FontWeight.Medium,
                    color = when {
                        isError -> MaterialTheme.colorScheme.error
                        time24h.isNotBlank() -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = when {
                    isError -> MaterialTheme.colorScheme.error
                    enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(24.dp)
            )
        }
    }

    if (showDialog) {
        val parsedTime = remember(time24h) { parseHourMinuteOrNull(time24h) }
        val initialHour = parsedTime?.first ?: 8
        val initialMinute = parsedTime?.second ?: 0
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )
        var showingPicker by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val formatted = String.format(Locale.ROOT, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                        onTimeSelected(formatted)
                        showDialog = false
                    }
                ) {
                    Text(
                        text = "Confirmar",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showingPicker = !showingPicker }
                    ) {
                        Icon(
                            imageVector = if (showingPicker) Icons.Outlined.Edit else Icons.Outlined.Schedule,
                            contentDescription = if (showingPicker) "Alternar para entrada por teclado" else "Alternar para relógio",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = { showDialog = false }
                    ) {
                        Text(
                            text = "Cancelar",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            title = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (showingPicker) {
                        TimePicker(state = timePickerState)
                    } else {
                        TimeInput(state = timePickerState)
                    }
                }
            }
        )
    }
}
