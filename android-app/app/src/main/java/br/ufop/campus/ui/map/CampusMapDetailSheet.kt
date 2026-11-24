package br.ufop.campus.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.map.FloorOccupancySummary
import br.ufop.campus.core.uniffi.FeatureInfo
import br.ufop.campus.core.uniffi.FloorLabel
import br.ufop.campus.core.uniffi.IndoorStatus
import br.ufop.campus.core.uniffi.MapLayer
import br.ufop.campus.core.uniffi.OccupancyStatus
import br.ufop.campus.core.uniffi.RenderFeature
import br.ufop.campus.core.uniffi.RoomOccupancyItem
import br.ufop.campus.ui.theme.DarkStatusAbsentRed
import br.ufop.campus.ui.theme.DarkStatusAttendingGreen
import br.ufop.campus.ui.theme.DarkWeatherBadgeBg
import br.ufop.campus.ui.theme.DarkWeatherBadgeText
import br.ufop.campus.ui.theme.LightWeatherBadgeBg
import br.ufop.campus.ui.theme.LightWeatherBadgeText
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen

@Composable
internal fun CampusMapDetailSheet(
    feature: FeatureInfo,
    occupancy: OccupancyStatus?,
    buildingOccupancy: List<RoomOccupancyItem>,
    floorLabels: List<FloorLabel>,
    isDark: Boolean,
    onSelectRoomId: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val freeAccent = if (isDark) DarkStatusAttendingGreen else StatusAttendingGreen
    val floorText = feature.floor?.let { resolveFloorDisplayLabel(it, floorLabels) }
    val metaSubtitle = buildList {
        feature.code?.takeIf { it.isNotBlank() }?.let { add(it) }
        feature.category?.takeIf { it.isNotBlank() }?.let { add(it) }
        floorText?.let { add("Andar: $it") }
    }.joinToString(" · ")

    Surface(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .heightIn(max = 320.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.96f else 0.98f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = feature.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (metaSubtitle.isNotBlank()) {
                        Text(
                            text = metaSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (feature.isMock) {
                    Surface(
                        shape = CircleShape,
                        color = if (isDark) DarkWeatherBadgeBg else LightWeatherBadgeBg
                    ) {
                        Text(
                            text = "Layout esquemático",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) DarkWeatherBadgeText else LightWeatherBadgeText
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar detalhes do local",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (feature.departments.isNotEmpty()) {
                Text(
                    text = "Departamentos: ${feature.departments.joinToString(" · ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!feature.notes.isNullOrBlank()) {
                Text(
                    text = feature.notes.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (feature.layer == MapLayer.ROOM && occupancy != null) {
                val (statusTitle, statusDetail, statusColor) = resolveOccupancyStatusDisplay(
                    occupancy = occupancy,
                    isDark = isDark,
                    neutralColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.30f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                        if (!statusDetail.isNullOrBlank()) {
                            Text(
                                text = statusDetail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (feature.layer == MapLayer.BUILDING || feature.layer == MapLayer.CAMPUS_ZONE) {
                BuildingOccupancySection(
                    feature = feature,
                    buildingOccupancy = buildingOccupancy,
                    freeAccent = freeAccent,
                    isDark = isDark,
                    onSelectRoomId = onSelectRoomId
                )
            }
        }
    }
}

@Composable
private fun BuildingOccupancySection(
    feature: FeatureInfo,
    buildingOccupancy: List<RoomOccupancyItem>,
    freeAccent: Color,
    isDark: Boolean,
    onSelectRoomId: (String) -> Unit
) {
    val freeRooms = buildingOccupancy.filter { it.status == OccupancyStatus.NoRegularClass }
    val occupiedCount = buildingOccupancy.count { it.status is OccupancyStatus.Occupied }
    val noDataCount = buildingOccupancy.count { it.status == OccupancyStatus.NoData }
    val outOfTermCount = buildingOccupancy.count { it.status == OccupancyStatus.OutOfTerm }

    val summaryText = when {
        buildingOccupancy.isEmpty() -> {
            if (feature.indoorStatus == IndoorStatus.EXTERIOR_ONLY) {
                "Prédio mapeado externamente — sem salas internas cadastradas."
            } else {
                "Sem salas com grade cadastrada para este prédio."
            }
        }
        outOfTermCount == buildingOccupancy.size ->
            "Fora do período letivo / feriado (${buildingOccupancy.size} salas)."
        noDataCount == buildingOccupancy.size ->
            "Sem grade cadastrada para este dia/prédio (${buildingOccupancy.size} salas)."
        else -> buildList {
            add("${freeRooms.size} sem aula regular")
            if (occupiedCount > 0) add("$occupiedCount ocupada(s)")
            if (noDataCount > 0) add("$noDataCount sem grade")
            if (outOfTermCount > 0) add("$outOfTermCount fora do período")
        }.joinToString(" · ")
    }

    Text(
        text = summaryText,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = if (freeRooms.isNotEmpty()) freeAccent else MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (freeRooms.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            freeRooms.forEach { roomItem ->
                MapCompactPillChip(
                    label = roomItem.roomCode,
                    isSelected = true,
                    isDark = isDark,
                    selectedContainerColor = freeAccent.copy(alpha = if (isDark) 0.22f else 0.14f),
                    selectedBorderColor = freeAccent.copy(alpha = 0.65f),
                    selectedContentColor = freeAccent,
                    onClick = { onSelectRoomId(roomItem.roomId) }
                )
            }
        }
    }
}

@Composable
internal fun FreeRoomsSummaryCard(
    freeRooms: List<RenderFeature>,
    summary: FloorOccupancySummary,
    activeFloorLabel: String,
    isDark: Boolean,
    onSelectRoomId: (String) -> Unit,
    onCloseMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val freeAccent = if (isDark) DarkStatusAttendingGreen else StatusAttendingGreen
    val totalRooms = summary.freeCount + summary.occupiedCount + summary.noDataCount + summary.outOfTermCount
    val statusLine = when {
        totalRooms == 0 -> "Nenhuma sala interna mapeada neste andar."
        summary.outOfTermCount == totalRooms ->
            "Fora do período letivo / feriado ($totalRooms salas neste andar)."
        summary.noDataCount == totalRooms ->
            "Sem grade cadastrada para este dia/prédio ($totalRooms salas neste andar)."
        else -> buildList {
            add("${summary.freeCount} sem aula regular agora")
            if (summary.occupiedCount > 0) add("${summary.occupiedCount} ocupada(s)")
            if (summary.noDataCount > 0) add("${summary.noDataCount} sem grade")
            if (summary.outOfTermCount > 0) add("${summary.outOfTermCount} fora do período")
        }.joinToString(" · ")
    }

    Surface(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.96f else 0.98f),
        border = BorderStroke(1.dp, freeAccent.copy(alpha = 0.45f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Salas livres · $activeFloorLabel",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = statusLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (freeRooms.isNotEmpty()) freeAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onCloseMode,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Desativar modo salas livres",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (freeRooms.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    freeRooms.forEach { room ->
                        val roomLabel = room.primaryLabel.ifBlank { room.id }
                        MapCompactPillChip(
                            label = roomLabel,
                            isSelected = true,
                            isDark = isDark,
                            selectedContainerColor = freeAccent.copy(alpha = if (isDark) 0.22f else 0.14f),
                            selectedBorderColor = freeAccent.copy(alpha = 0.65f),
                            selectedContentColor = freeAccent,
                            onClick = { onSelectRoomId(room.id) }
                        )
                    }
                }
            }
        }
    }
}

private fun resolveOccupancyStatusDisplay(
    occupancy: OccupancyStatus,
    isDark: Boolean,
    neutralColor: Color
): Triple<String, String?, Color> = when (occupancy) {
    is OccupancyStatus.Occupied -> {
        val slot = occupancy.slot
        val detailLine = buildList {
            slot.title?.takeIf { it.isNotBlank() }?.let { add(it) }
            add("${slot.label} (${slot.startTime}–${slot.endTime})")
        }.joinToString(" · ")
        Triple(
            "Ocupada agora",
            detailLine,
            if (isDark) DarkStatusAbsentRed else StatusAbsentRed
        )
    }
    OccupancyStatus.NoRegularClass -> Triple(
        "Sem aula regular agora",
        null,
        if (isDark) DarkStatusAttendingGreen else StatusAttendingGreen
    )
    OccupancyStatus.NoData -> Triple(
        "Sem grade cadastrada para este dia/prédio",
        null,
        neutralColor
    )
    OccupancyStatus.OutOfTerm -> Triple(
        "Fora do período letivo / feriado",
        null,
        neutralColor
    )
}

internal fun resolveFloorDisplayLabel(floor: Int, floorLabels: List<FloorLabel>): String =
    floorLabels.firstOrNull { it.floor == floor }?.label
        ?: if (floor == 0) "Térreo" else "${floor + 1}º Pav"
