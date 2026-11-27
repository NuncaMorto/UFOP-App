package br.ufop.campus.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.ufop.campus.core.map.CampusMapController
import br.ufop.campus.core.map.sanitizeMapAttributionText
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.campusCardElevation

@Composable
fun CampusMapScreen(
    controller: CampusMapController,
    bottomBarClearanceDp: Dp,
    startBarClearanceDp: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val isDark = LocalCampusThemeController.current.isDark
    val hudStartPadding = maxOf(12.dp, startBarClearanceDp)

    Box(modifier = modifier.fillMaxSize()) {
        CampusMapCanvas(
            controller = controller,
            showBuiltInHud = false,
            topHudInsetDp = 112.dp,
            bottomHudInsetDp = bottomBarClearanceDp + 52.dp,
            startHudInsetDp = startBarClearanceDp,
            modifier = Modifier.fillMaxSize()
        )

        CampusMapTopHud(
            controller = controller,
            hudStartPadding = hudStartPadding,
            isDark = isDark,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        CampusMapBottomHud(
            controller = controller,
            hudStartPadding = hudStartPadding,
            bottomBarClearanceDp = bottomBarClearanceDp,
            isDark = isDark,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CampusMapTopHud(
    controller: CampusMapController,
    hudStartPadding: Dp,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val topState by controller.topHudState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = hudStartPadding, top = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CampusMapSearchBar(
            query = topState.searchQuery,
            hasActiveFilters = topState.activePoiFilter != null || topState.isFreeRoomsModeEnabled,
            isDark = isDark,
            onQueryChange = controller::updateSearchQuery,
            onClearAll = {
                controller.clearSearchAndFilters()
                controller.setFreeRoomsMode(false)
            },
            campuses = topState.campuses,
            activeCampusId = topState.activeCampusId,
            onSelectCampus = controller::selectCampus
        )

        CampusMapFilterChipsRow(
            isFreeRoomsEnabled = topState.isFreeRoomsModeEnabled,
            activePoiFilter = topState.activePoiFilter,
            isDark = isDark,
            onToggleFreeRooms = {
                if (!topState.isFreeRoomsModeEnabled) {
                    controller.clearSelection()
                }
                controller.toggleFreeRoomsMode()
            },
            onTogglePoiFilter = controller::togglePoiFilter
        )

        if (topState.searchResults.isNotEmpty() || topState.searchQuery.isNotBlank()) {
            CampusMapSearchResultsCard(
                results = topState.searchResults,
                searchQuery = topState.searchQuery,
                activeFloor = topState.activeFloor,
                floorLabels = topState.availableFloors,
                isDark = isDark,
                onSelectResult = controller::selectSearchResult
            )
        }

        topState.resolutionNotice?.let { notice ->
            MapResolutionNoticeBanner(
                message = notice,
                isDark = isDark,
                onDismiss = controller::clearResolutionNotice
            )
        }
    }
}

@Composable
private fun CampusMapBottomHud(
    controller: CampusMapController,
    hudStartPadding: Dp,
    bottomBarClearanceDp: Dp,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val bottomState by controller.bottomHudState.collectAsState()
    var showCreditsDialog by remember { mutableStateOf(false) }
    val activeFloorLabel = remember(bottomState.activeFloor, bottomState.availableFloors) {
        resolveFloorDisplayLabel(bottomState.activeFloor, bottomState.availableFloors)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = hudStartPadding, end = 12.dp, bottom = bottomBarClearanceDp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val selected = bottomState.selectedFeature
        when {
            selected != null -> {
                CampusMapDetailSheet(
                    feature = selected,
                    occupancy = bottomState.selectedOccupancy,
                    buildingOccupancy = bottomState.selectedBuildingOccupancy,
                    floorLabels = bottomState.availableFloors,
                    isDark = isDark,
                    onSelectRoomId = controller::focusFeature,
                    onDismiss = controller::clearSelection
                )
            }
            !bottomState.isNativeAvailable && !bottomState.isLoading -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CampusCardRadius),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    border = campusCardBorder(),
                    shadowElevation = campusCardElevation(2.dp)
                ) {
                    Text(
                        text = bottomState.errorMessage ?: "Motor vetorial offline pronto para carga nativa.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            bottomState.isFreeRoomsModeEnabled -> {
                FreeRoomsSummaryCard(
                    freeRooms = bottomState.freeRoomsOnActiveFloor,
                    summary = bottomState.floorOccupancySummary,
                    activeFloorLabel = activeFloorLabel,
                    isDark = isDark,
                    onSelectRoomId = controller::focusFeature,
                    onCloseMode = { controller.setFreeRoomsMode(false) }
                )
            }
        }

        if (bottomState.availableFloors.isNotEmpty() || !bottomState.attribution.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (bottomState.availableFloors.isNotEmpty()) {
                    FloorSelectorBar(
                        floors = bottomState.availableFloors,
                        activeFloor = bottomState.activeFloor,
                        onSelectFloor = controller::selectFloor,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (!bottomState.attribution.isNullOrBlank()) {
                    Surface(
                        onClick = { showCreditsDialog = true },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.90f else 0.94f),
                        border = campusCardBorder(),
                        shadowElevation = campusCardElevation(1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "© OSM · UFOP",
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Créditos cartográficos",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreditsDialog) {
        val sanitizedCredits = sanitizeMapAttributionText(bottomState.attribution)
        AlertDialog(
            onDismissRequest = { showCreditsDialog = false },
            shape = RoundedCornerShape(CampusCardRadius),
            title = {
                Text(
                    text = "Créditos Cartográficos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "$sanitizedCredits\n\nMapa vetorial 100% offline. Layouts de salas são representações esquemáticas para orientação estudantil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showCreditsDialog = false }) {
                    Text("Fechar")
                }
            }
        )
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.95f else 0.97f),
        border = campusCardBorder(),
        shadowElevation = campusCardElevation(1.dp)
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
