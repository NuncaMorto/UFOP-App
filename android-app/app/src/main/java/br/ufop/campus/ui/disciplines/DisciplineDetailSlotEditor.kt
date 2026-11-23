package br.ufop.campus.ui.disciplines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.UfopBuildingOption

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailScheduleSlotEditorFields(
    draft: ScheduleSlotDraft,
    accentColor: Color,
    chipMinHeight: Dp,
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
            !isEadBuildingLabel(selectedShortName) && draft.room == "Online" -> ""
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
        DetailUfopBuildingPickerDialog(
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
                    modifier = Modifier.heightIn(min = chipMinHeight),
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TIME_SLOT_PRESETS.forEach { (start, end) ->
                val isSelected = draft.startTime == start && draft.endTime == end
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onDraftChange(draft.copy(startTime = start, endTime = end))
                    },
                    modifier = Modifier.heightIn(min = chipMinHeight),
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
                        modifier = Modifier.heightIn(min = chipMinHeight),
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
                        modifier = Modifier.heightIn(min = chipMinHeight),
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
                placeholder = {
                    Text(
                        text = if (isEadBuildingLabel(draft.building)) {
                            "AVA / Moodle (opcional)"
                        } else {
                            "Sala (opcional, ex.: Sala 104)"
                        }
                    )
                },
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
@Composable
internal fun DetailUfopBuildingPickerDialog(
    buildings: List<UfopBuildingOption>,
    selectedBuildingShortName: String,
    accentColor: Color,
    onSelectBuilding: (UfopBuildingOption) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(buildings, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            buildings
        } else {
            buildings.filter {
                it.shortName.contains(q, ignoreCase = true) ||
                    it.fullName.contains(q, ignoreCase = true) ||
                    it.campusName.contains(q, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Prédios e Unidades",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ouro Preto · Mariana · João Monlevade",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar prédio (ex.: ICEB, Escola de Minas, ICEA)...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Buscar prédio",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = filtered,
                        key = { it.id }
                    ) { b ->
                        val isSelected = b.shortName.equals(selectedBuildingShortName.trim(), ignoreCase = true)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectBuilding(b) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                accentColor.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) {
                                    accentColor.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                }
                            )
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
                                        text = "${b.shortName} · ${b.campusName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = b.fullName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selecionado",
                                        tint = accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Fechar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
