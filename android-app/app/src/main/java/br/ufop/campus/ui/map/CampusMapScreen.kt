package br.ufop.campus.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.map.CampusMapController
import br.ufop.campus.ui.theme.LocalCampusThemeController

@Composable
fun CampusMapScreen(
    controller: CampusMapController,
    bottomBarClearanceDp: Dp,
    startBarClearanceDp: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val uiState by controller.state.collectAsState()
    val isDark = LocalCampusThemeController.current.isDark
    val hudStartPadding = maxOf(12.dp, startBarClearanceDp)
    val floorItems = remember(
        uiState.activeCampusId,
        uiState.activeFloor,
        uiState.selectedFeature,
        uiState.renderBatch
    ) {
        controller.availableFloorsForUi()
    }
    val activeFloorLabel = remember(uiState.activeFloor, floorItems) {
        resolveFloorDisplayLabel(uiState.activeFloor, floorItems)
    }
    val attribution = uiState.renderBatch?.attribution
        ?: uiState.campuses.firstOrNull { it.campusId == uiState.activeCampusId }?.attribution

    Box(modifier = modifier.fillMaxSize()) {
        CampusMapCanvas(
            controller = controller,
            showBuiltInHud = false,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = hudStartPadding, top = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CampusMapSearchBar(
                query = uiState.searchQuery,
                hasActiveFilters = uiState.activePoiFilter != null || uiState.isFreeRoomsModeEnabled,
                isDark = isDark,
                onQueryChange = controller::updateSearchQuery,
                onClearAll = {
                    controller.clearSearchAndFilters()
                    controller.setFreeRoomsMode(false)
                }
            )

            if (uiState.campuses.isNotEmpty()) {
                CampusSelectorChipsRow(
                    campuses = uiState.campuses,
                    activeCampusId = uiState.activeCampusId,
                    isDark = isDark,
                    onSelectCampus = controller::selectCampus
                )
            }

            CampusMapFilterChipsRow(
                isFreeRoomsEnabled = uiState.isFreeRoomsModeEnabled,
                activePoiFilter = uiState.activePoiFilter,
                isDark = isDark,
                onToggleFreeRooms = {
                    if (!uiState.isFreeRoomsModeEnabled) {
                        controller.clearSelection()
                    }
                    controller.toggleFreeRoomsMode()
                },
                onTogglePoiFilter = controller::togglePoiFilter
            )

            if (uiState.searchResults.isNotEmpty() || uiState.searchQuery.isNotBlank()) {
                CampusMapSearchResultsCard(
                    results = uiState.searchResults,
                    searchQuery = uiState.searchQuery,
                    activeFloor = uiState.activeFloor,
                    floorLabels = floorItems,
                    isDark = isDark,
                    onSelectResult = controller::selectSearchResult
                )
            }

            uiState.resolutionNotice?.let { notice ->
                MapResolutionNoticeBanner(
                    message = notice,
                    isDark = isDark,
                    onDismiss = controller::clearResolutionNotice
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = hudStartPadding, end = 12.dp, bottom = bottomBarClearanceDp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (floorItems.isNotEmpty()) {
                FloorSelectorBar(
                    floors = floorItems,
                    activeFloor = uiState.activeFloor,
                    onSelectFloor = controller::selectFloor
                )
            }

            val selected = uiState.selectedFeature
            when {
                selected != null -> {
                    CampusMapDetailSheet(
                        feature = selected,
                        occupancy = uiState.selectedOccupancy,
                        buildingOccupancy = uiState.selectedBuildingOccupancy,
                        floorLabels = floorItems,
                        isDark = isDark,
                        onSelectRoomId = controller::focusFeature,
                        onDismiss = controller::clearSelection
                    )
                }
                !uiState.isNativeAvailable -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Motor vetorial offline pronto para carga nativa.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.isFreeRoomsModeEnabled -> {
                    FreeRoomsSummaryCard(
                        freeRooms = uiState.freeRoomsOnActiveFloor,
                        summary = uiState.floorOccupancySummary,
                        activeFloorLabel = activeFloorLabel,
                        isDark = isDark,
                        onSelectRoomId = controller::focusFeature,
                        onCloseMode = { controller.setFreeRoomsMode(false) }
                    )
                }
            }

            if (!attribution.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.align(Alignment.End),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.72f else 0.80f)
                ) {
                    Text(
                        text = attribution,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MapResolutionNoticeBanner(
    message: String,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.92f else 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dispensar aviso",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
