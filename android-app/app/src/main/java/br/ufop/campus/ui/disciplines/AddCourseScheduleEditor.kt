package br.ufop.campus.ui.disciplines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.UfopBuildingOption

internal data class ScheduleSlotDraft(
    val dayOfWeek: Int = 1,
    val startTime: String = "08:00",
    val endTime: String = "09:40",
    val building: String = "ICEB (III)",
    val room: String = "Sala 204"
) {
    val shortLocationPreview: String
        get() = buildShortLocationPreview(building, room)
}

internal val WEEK_DAY_OPTIONS = listOf(
    1 to "SEG",
    2 to "TER",
    3 to "QUA",
    4 to "QUI",
    5 to "SEX",
    6 to "SÁB"
)

internal val TIME_SLOT_PRESETS = listOf(
    "07:30" to "09:10",
    "08:00" to "09:40",
    "09:30" to "11:10",
    "10:00" to "11:40",
    "13:30" to "15:10",
    "15:20" to "17:00",
    "17:10" to "18:50",
    "19:00" to "20:40",
    "20:50" to "22:40"
)

@Composable
internal fun InitialScheduleSlotSection(
    includeInitialSlot: Boolean,
    slotDraft: ScheduleSlotDraft,
    accentColor: Color,
    onToggleInclude: (Boolean) -> Unit,
    onDraftChange: (ScheduleSlotDraft) -> Unit
) {
    // Horário semanal inicial (opcional)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(br.ufop.campus.ui.theme.CampusCardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = br.ufop.campus.ui.theme.campusCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Horário semanal inicial",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Opcional · valida conflitos na grade",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = includeInitialSlot,
                    onCheckedChange = onToggleInclude,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = accentColor,
                        checkedBorderColor = accentColor,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            if (includeInitialSlot) {
                ScheduleSlotEditorFields(
                    draft = slotDraft,
                    accentColor = accentColor,
                    onDraftChange = onDraftChange
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleSlotEditorFields(
    draft: ScheduleSlotDraft,
    accentColor: Color,
    onDraftChange: (ScheduleSlotDraft) -> Unit,
    ufopBuildings: List<UfopBuildingOption>? = null
) {
    val resolvedBuildings = remember(ufopBuildings) {
        val base = (ufopBuildings ?: DisciplinesViewModel.sharedRepository?.listUfopBuildings())
            .orEmpty()
            .ifEmpty { DEFAULT_UFOP_BUILDINGS }
        if (base.any { isEadBuildingLabel(it.shortName) }) {
            base
        } else {
            base + UfopBuildingOption(
                id = "ufop_ead",
                campusId = "ead",
                campusName = "EAD",
                shortName = "Ensino a Distância (EAD)",
                fullName = "Ensino a Distância (AVA / Moodle / Remoto)"
            )
        }
    }

    val campusOptions = listOf("Todos", "Ouro Preto", "Mariana", "João Monlevade", "EAD")
    var selectedCampusFilter by remember { mutableStateOf("Todos") }
    var showBuildingPickerDialog by remember { mutableStateOf(false) }

    val applySelectedBuilding: (String) -> Unit = { selectedShortName ->
        val nextRoom = when {
            isEadBuildingLabel(selectedShortName) &&
                (draft.room.isBlank() || draft.room == "Sala 204") -> "Online"
            !isEadBuildingLabel(selectedShortName) && draft.room == "Online" -> "Sala 204"
            else -> draft.room
        }
        onDraftChange(draft.copy(building = selectedShortName, room = nextRoom))
    }

    val filteredBuildings = remember(resolvedBuildings, selectedCampusFilter) {
        if (selectedCampusFilter == "Todos") {
            resolvedBuildings
        } else {
            resolvedBuildings.filter { it.campusName.equals(selectedCampusFilter, ignoreCase = true) }
        }
    }

    val matchedBuilding = remember(resolvedBuildings, draft.building) {
        resolvedBuildings.find {
            it.shortName.equals(draft.building.trim(), ignoreCase = true) ||
                it.fullName.equals(draft.building.trim(), ignoreCase = true)
        }
    }

    if (showBuildingPickerDialog) {
        UfopBuildingPickerDialog(
            buildings = resolvedBuildings,
            selectedBuildingShortName = draft.building,
            accentColor = accentColor,
            onSelectBuilding = { chosen ->
                applySelectedBuilding(chosen.shortName)
                showBuildingPickerDialog = false
            },
            onDismiss = { showBuildingPickerDialog = false }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Dia da semana",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WEEK_DAY_OPTIONS.forEach { (dayNum, label) ->
                val isSelected = draft.dayOfWeek == dayNum
                FilterChip(
                    selected = isSelected,
                    onClick = { onDraftChange(draft.copy(dayOfWeek = dayNum)) },
                    label = { Text(label) },
                    shape = RoundedCornerShape(22.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.18f),
                        selectedLabelColor = accentColor
                    )
                )
            }
        }

        Text(
            text = "Horário",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TIME_SLOT_PRESETS.forEach { (start, end) ->
                val isSelected = draft.startTime == start && draft.endTime == end
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onDraftChange(draft.copy(startTime = start, endTime = end))
                    },
                    label = { Text("$start–$end") },
                    shape = RoundedCornerShape(22.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.18f),
                        selectedLabelColor = accentColor
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = draft.startTime,
                onValueChange = { onDraftChange(draft.copy(startTime = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("Início") },
                placeholder = { Text("08:00") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = draft.endTime,
                onValueChange = { onDraftChange(draft.copy(endTime = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("Término") },
                placeholder = { Text("09:40") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }

        // Seletor Expressivo de Prédios Oficiais da UFOP
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Prédio / Unidade",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = matchedBuilding?.let { "${it.campusName} · ${it.fullName}" }
                        ?: "Ouro Preto · Mariana · João Monlevade",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Botão Seletor Expressivo que abre o catálogo completo agrupado por campus
            Surface(
                onClick = { showBuildingPickerDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = matchedBuilding?.shortName ?: draft.building.ifBlank { "Selecionar prédio ou local" },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = matchedBuilding?.let { "${it.fullName} (${it.campusName})" }
                                    ?: "Toque para escolher prédio ou unidade",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Escolher prédio",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Filtro rápido por campus + Chips expressivos dos prédios oficiais UFOP
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                campusOptions.forEach { campusLabel ->
                    val isCampusSelected = selectedCampusFilter == campusLabel
                    FilterChip(
                        selected = isCampusSelected,
                        onClick = { selectedCampusFilter = campusLabel },
                        label = {
                            Text(
                                text = campusLabel,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        shape = RoundedCornerShape(22.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filteredBuildings.forEach { buildingOption ->
                    val isSelected = draft.building.trim().equals(buildingOption.shortName, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            applySelectedBuilding(buildingOption.shortName)
                        },
                        label = { Text(buildingOption.shortName) },
                        shape = RoundedCornerShape(22.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.18f),
                            selectedLabelColor = accentColor
                        )
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = draft.building,
                onValueChange = { onDraftChange(draft.copy(building = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("Prédio") },
                placeholder = { Text("ICEB (III)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = draft.room,
                onValueChange = { onDraftChange(draft.copy(room = it)) },
                modifier = Modifier.weight(1f),
                label = { Text(if (isEadBuildingLabel(draft.building)) "Ambiente" else "Sala") },
                placeholder = { Text(if (isEadBuildingLabel(draft.building)) "AVA / Moodle" else "Sala 204") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }

        Text(
            text = "Exibição na grade: ${draft.shortLocationPreview}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
