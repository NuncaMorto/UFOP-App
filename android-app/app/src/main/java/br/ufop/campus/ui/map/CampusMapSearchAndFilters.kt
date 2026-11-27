package br.ufop.campus.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.map.localizeCampusMapCategory
import br.ufop.campus.core.uniffi.CampusMapSummary
import br.ufop.campus.core.uniffi.FloorLabel
import br.ufop.campus.core.uniffi.PoiType
import br.ufop.campus.core.uniffi.SearchResult
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.campusCardElevation

private data class MapPoiFilterChipSpec(
    val poiType: PoiType,
    val label: String,
    val icon: ImageVector
)

private val MAP_POI_FILTER_CHIPS = listOf(
    MapPoiFilterChipSpec(PoiType.TOILET, "Banheiros", Icons.Outlined.Wc),
    MapPoiFilterChipSpec(PoiType.FACULTY_OFFICE, "Professores/Secretarias", Icons.Outlined.Badge),
    MapPoiFilterChipSpec(PoiType.ENTRANCE, "Acessos", Icons.AutoMirrored.Outlined.Login),
    MapPoiFilterChipSpec(PoiType.DINING, "Alimentação", Icons.Outlined.Restaurant),
    MapPoiFilterChipSpec(PoiType.LIBRARY, "Biblioteca", Icons.Outlined.LocalLibrary)
)

@Composable
internal fun CampusMapSearchBar(
    query: String,
    hasActiveFilters: Boolean,
    isDark: Boolean,
    onQueryChange: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    campuses: List<CampusMapSummary> = emptyList(),
    activeCampusId: String = "",
    onSelectCampus: (String) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val a11y = LocalCampusAccessibility.current.settings
    val barHeight = if (a11y.largeTouchTargets) 54.dp else 48.dp
    var campusMenuExpanded by remember { mutableStateOf(false) }
    val activeCampus = campuses.firstOrNull { it.campusId == activeCampusId } ?: campuses.firstOrNull()
    val activeCampusShortLabel = when (activeCampus?.campusId) {
        "OP_MC" -> "Morro"
        "JM_ICEA" -> "ICEA"
        "MA_ICHS" -> "ICHS"
        "MA_ICSA" -> "ICSA"
        else -> activeCampus?.name ?: "Campus"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = barHeight),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.95f else 0.98f),
        border = when {
            isFocused -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            else -> campusCardBorder()
        },
        shadowElevation = campusCardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = barHeight)
                .padding(start = 14.dp, end = if (campuses.isNotEmpty()) 6.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Buscar local ou sala...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Buscar no mapa do campus" },
                    singleLine = true,
                    interactionSource = interactionSource,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
            if (query.isNotEmpty() || hasActiveFilters) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        onClearAll()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpar busca e filtros",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (campuses.isNotEmpty()) {
                Box {
                    Surface(
                        onClick = { campusMenuExpanded = true },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = campusCardBorder()
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = activeCampusShortLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Alternar campus",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = campusMenuExpanded,
                        onDismissRequest = { campusMenuExpanded = false }
                    ) {
                        campuses.forEach { campus ->
                            val isSelected = campus.campusId == activeCampusId
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = campusDisplayLabel(campus),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                trailingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    campusMenuExpanded = false
                                    onSelectCampus(campus.campusId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CampusSelectorChipsRow(
    campuses: List<CampusMapSummary>,
    activeCampusId: String,
    isDark: Boolean,
    onSelectCampus: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        campuses.forEach { campus ->
            val selectedCampus = campus.campusId == activeCampusId
            val displayLabel = campusDisplayLabel(campus)
            MapCompactPillChip(
                label = displayLabel,
                isSelected = selectedCampus,
                isDark = isDark,
                onClick = { onSelectCampus(campus.campusId) }
            )
        }
    }
}

@Composable
internal fun CampusMapFilterChipsRow(
    isFreeRoomsEnabled: Boolean,
    activePoiFilter: PoiType?,
    isDark: Boolean,
    onToggleFreeRooms: () -> Unit,
    onTogglePoiFilter: (PoiType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MapCompactPillChip(
            label = "Salas livres",
            icon = Icons.Outlined.MeetingRoom,
            isSelected = isFreeRoomsEnabled,
            isDark = isDark,
            onClick = onToggleFreeRooms
        )

        MAP_POI_FILTER_CHIPS.forEach { spec ->
            val isSelected = activePoiFilter == spec.poiType
            MapCompactPillChip(
                label = spec.label,
                icon = spec.icon,
                isSelected = isSelected,
                isDark = isDark,
                onClick = { onTogglePoiFilter(spec.poiType) }
            )
        }
    }
}

@Composable
internal fun CampusMapSearchResultsCard(
    results: List<SearchResult>,
    searchQuery: String,
    activeFloor: Int,
    floorLabels: List<FloorLabel>,
    isDark: Boolean,
    onSelectResult: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CampusCardRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.96f else 0.98f),
        border = campusCardBorder(),
        shadowElevation = campusCardElevation(2.dp)
    ) {
        if (results.isEmpty()) {
            Text(
                text = "Nenhum local encontrado neste campus para \"$searchQuery\".",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
            ) {
                items(
                    items = results,
                    key = { "${it.feature.id}_${it.matchedField}" }
                ) { result ->
                    val feat = result.feature
                    val floorText = feat.floor?.let { fl ->
                        floorLabels.firstOrNull { it.floor == fl }?.label
                            ?: if (fl == 0) "Térreo" else "${fl + 1}º Pav"
                    }
                    val subtitleParts = buildList {
                        feat.code?.takeIf { it.isNotBlank() }?.let { add(it) }
                        localizeCampusMapCategory(feat.category)?.let { add(it) }
                        floorText?.let { add(it) }
                    }
                    val switchesFloor = feat.floor != null && feat.floor != activeFloor

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable(role = Role.Button) { onSelectResult(result) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = feat.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subtitleParts.isNotEmpty()) {
                                Text(
                                    text = subtitleParts.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (switchesFloor && floorText != null) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                            ) {
                                Text(
                                    text = floorText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
internal fun MapCompactPillChip(
    label: String,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selectedContainerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    selectedBorderColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    selectedContentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val a11y = LocalCampusAccessibility.current.settings
    val visualHeight = if (a11y.largeTouchTargets) 48.dp else 34.dp
    val containerColor = if (isSelected) {
        selectedContainerColor
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.88f else 0.94f)
    }
    val contentColor = if (isSelected) {
        selectedContentColor
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (isSelected) {
        selectedBorderColor
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                role = Role.Tab
                selected = isSelected
                contentDescription = label
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.heightIn(min = visualHeight),
            shape = CircleShape,
            color = containerColor,
            border = if (isSelected && a11y.highContrastMode) {
                BorderStroke(2.dp, borderColor)
            } else {
                campusCardBorder()
            },
            shadowElevation = campusCardElevation(1.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(androidx.compose.ui.graphics.Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

internal fun campusDisplayLabel(campus: CampusMapSummary): String = when (campus.campusId) {
    "OP_MC" -> "Morro do Cruzeiro"
    "JM_ICEA" -> "ICEA"
    "MA_ICHS" -> "ICHS"
    "MA_ICSA" -> "ICSA"
    else -> campus.name
}
