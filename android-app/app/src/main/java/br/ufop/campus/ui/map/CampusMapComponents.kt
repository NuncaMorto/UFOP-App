package br.ufop.campus.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.uniffi.FeatureInfo
import br.ufop.campus.core.uniffi.FloorLabel
import br.ufop.campus.core.uniffi.OccupancyStatus
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.DarkStatusAbsentRed
import br.ufop.campus.ui.theme.DarkStatusAttendingGreen
import br.ufop.campus.ui.theme.DarkWeatherBadgeBg
import br.ufop.campus.ui.theme.DarkWeatherBadgeText
import br.ufop.campus.ui.theme.LightWeatherBadgeBg
import br.ufop.campus.ui.theme.LightWeatherBadgeText
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.campusCardElevation

internal data class MapCanvasPalette(
    val background: Color,
    val campusGroundFill: Color,
    val campusGroundStroke: Color,
    val campusZoneFill: Color,
    val campusZoneStroke: Color,
    val greenAreaFill: Color,
    val parkingFill: Color,
    val sportsFill: Color,
    val walkwayStroke: Color,
    val buildingFill: Color,
    val buildingStroke: Color,
    val roomOccupiedFill: Color,
    val roomFreeFill: Color,
    val roomNoDataFill: Color,
    val roomOutOfTermFill: Color,
    val selectedFill: Color,
    val roomStroke: Color,
    val selectedStroke: Color,
    val poiFill: Color,
    val labelColor: Color,
    val secondaryLabelColor: Color
) {
    companion object {
        fun resolve(isDark: Boolean, highContrast: Boolean): MapCanvasPalette {
            return if (isDark) {
                MapCanvasPalette(
                    background = if (highContrast) Color(0xFF050607) else Color(0xFF0E1218),
                    campusGroundFill = Color(0xFF151A22),
                    campusGroundStroke = Color(0xFF2F3846),
                    campusZoneFill = Color(0xFF1B2330),
                    campusZoneStroke = Color(0xFF3E4C63),
                    greenAreaFill = Color(0xFF163122),
                    parkingFill = Color(0xFF242A35),
                    sportsFill = Color(0xFF1A2E38),
                    walkwayStroke = Color(0xFF3A4352),
                    buildingFill = if (highContrast) Color(0xFF202630) else Color(0xFF252C37),
                    buildingStroke = if (highContrast) Color.White.copy(alpha = 0.85f) else Color(0xFF526075),
                    roomOccupiedFill = Color(0xB87A2727),
                    roomFreeFill = Color(0xA61F5C3A),
                    roomNoDataFill = Color(0xBF353E4D),
                    roomOutOfTermFill = Color(0xB82A303B),
                    selectedFill = Color(0xB81E3A5F),
                    roomStroke = if (highContrast) Color.White else Color(0xFF94A3B8),
                    selectedStroke = Color(0xFF60A5FA),
                    poiFill = Color(0xFFF59E0B),
                    labelColor = if (highContrast) Color.White else Color(0xFFF1F5F9),
                    secondaryLabelColor = if (highContrast) Color(0xFFE2E8F0) else Color(0xFFCBD5E1)
                )
            } else {
                MapCanvasPalette(
                    background = if (highContrast) Color.White else Color(0xFFF5F3EE),
                    campusGroundFill = Color(0xFFECEAE2),
                    campusGroundStroke = Color(0xFFC8C3B8),
                    campusZoneFill = Color(0xFFE4E8EE),
                    campusZoneStroke = Color(0xFF9AA5B4),
                    greenAreaFill = Color(0xFFD3E6CE),
                    parkingFill = Color(0xFFDCDFE4),
                    sportsFill = Color(0xFFCEE3EB),
                    walkwayStroke = Color(0xFFB8B5AA),
                    buildingFill = if (highContrast) Color(0xFFEFEFEF) else Color.White,
                    buildingStroke = if (highContrast) Color.Black else Color(0xFF64748B),
                    roomOccupiedFill = Color(0xFFFAD2CF),
                    roomFreeFill = Color(0xFFCBEAD3),
                    roomNoDataFill = Color(0xFFE2E8F0),
                    roomOutOfTermFill = Color(0xFFECEFF3),
                    selectedFill = Color(0xFFD6E4FF),
                    roomStroke = if (highContrast) Color.Black else Color(0xFF475569),
                    selectedStroke = Color(0xFF1D4ED8),
                    poiFill = Color(0xFFD97706),
                    labelColor = if (highContrast) Color.Black else Color(0xFF0F172A),
                    secondaryLabelColor = if (highContrast) Color(0xFF1F2937) else Color(0xFF475569)
                )
            }
        }
    }
}

@Composable
internal fun FloorSelectorBar(
    floors: List<FloorLabel>,
    activeFloor: Int,
    onSelectFloor: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val highContrast = LocalCampusAccessibility.current.settings.highContrastMode
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        floors.forEach { item ->
            val isSelected = item.floor == activeFloor
            Surface(
                onClick = { onSelectFloor(item.floor) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics {
                        role = Role.Tab
                        selected = isSelected
                    },
                shape = CircleShape,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                },
                border = if (isSelected && highContrast) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                } else {
                    campusCardBorder()
                },
                shadowElevation = campusCardElevation(1.dp)
            ) {
                Text(
                    text = item.label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun SelectedFeatureSheetCard(
    feature: FeatureInfo,
    occupancy: OccupancyStatus?,
    isDark: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showSchematicBadge = feature.isMock

    Surface(
        modifier = modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(CampusCardRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        border = campusCardBorder(),
        shadowElevation = campusCardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = feature.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    feature.floor?.let { fl ->
                        val floorText = if (fl == 0) "Térreo / 1º Pav" else "${fl + 1}º Pav"
                        Text(
                            text = "Andar: $floorText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (showSchematicBadge) {
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

            if (occupancy != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val (statusText, statusColor) = when (occupancy) {
                    is OccupancyStatus.Occupied -> {
                        val s = occupancy.slot
                        "${s.label} (${s.startTime}–${s.endTime})" to
                            if (isDark) DarkStatusAbsentRed else StatusAbsentRed
                    }
                    OccupancyStatus.NoRegularClass -> {
                        "Sem aula regular agora" to
                            if (isDark) DarkStatusAttendingGreen else StatusAttendingGreen
                    }
                    OccupancyStatus.NoData -> {
                        "Sem grade cadastrada para este dia/prédio" to
                            MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    OccupancyStatus.OutOfTerm -> {
                        "Fora do período letivo / feriado" to
                            MaterialTheme.colorScheme.onSurfaceVariant
                    }
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = statusColor
                )
            } else if (!feature.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = feature.notes.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
