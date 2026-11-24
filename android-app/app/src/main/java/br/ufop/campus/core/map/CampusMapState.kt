package br.ufop.campus.core.map

import br.ufop.campus.core.uniffi.CameraFocus
import br.ufop.campus.core.uniffi.CampusMapSummary
import br.ufop.campus.core.uniffi.FeatureInfo
import br.ufop.campus.core.uniffi.FloorLabel
import br.ufop.campus.core.uniffi.MapLayer
import br.ufop.campus.core.uniffi.OccupancyStatus
import br.ufop.campus.core.uniffi.PoiType
import br.ufop.campus.core.uniffi.RenderBatch
import br.ufop.campus.core.uniffi.RenderFeature
import br.ufop.campus.core.uniffi.RenderOccupancyKind
import br.ufop.campus.core.uniffi.RoomOccupancyItem
import br.ufop.campus.core.uniffi.SearchResult

data class MapMeetingFocusRequest(
    val courseCode: String,
    val section: String = "",
    val weekday: Int,
    val startTime: String,
    val fallbackBuildingRaw: String
)

data class FloorOccupancySummary(
    val freeCount: Int,
    val occupiedCount: Int,
    val noDataCount: Int,
    val outOfTermCount: Int
)

fun isEadMapLocation(raw: String): Boolean {
    val lower = raw.lowercase()
    return lower.contains("ead") ||
        lower.contains("distância") ||
        lower.contains("distancia") ||
        lower.contains("remoto") ||
        lower.contains("online")
}

internal fun extractCourseSectionFromNotes(notes: String): String =
    COURSE_TURMA_REGEX.find(notes)?.groupValues?.getOrNull(1)?.trim().orEmpty()

private val COURSE_TURMA_REGEX = Regex("""Turma\s+([0-9A-Za-z]+)""", RegexOption.IGNORE_CASE)

data class CampusMapUiState(
    val campuses: List<CampusMapSummary> = emptyList(),
    val activeCampusId: String = "OP_MC",
    val activeFloor: Int = 0,
    val timestampIso: String? = null,
    val renderBatch: RenderBatch? = null,
    val geometryRevision: Long = 0L,
    val availableFloors: List<FloorLabel> = emptyList(),
    val selectedFeature: FeatureInfo? = null,
    val selectedOccupancy: OccupancyStatus? = null,
    val selectedBuildingOccupancy: List<RoomOccupancyItem> = emptyList(),
    val searchQuery: String = "",
    val activePoiFilter: PoiType? = null,
    val searchResults: List<SearchResult> = emptyList(),
    val isFreeRoomsModeEnabled: Boolean = false,
    val resolutionNotice: String? = null,
    val pendingFocus: CameraFocus? = null,
    val isNativeAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val freeRoomsOnActiveFloor: List<RenderFeature>
        get() = renderBatch?.items
            ?.filter { it.layer == MapLayer.ROOM && it.occupancy == RenderOccupancyKind.NO_REGULAR_CLASS }
            .orEmpty()

    val floorOccupancySummary: FloorOccupancySummary
        get() = computeFloorOccupancySummary(renderBatch)
}

/** Slice exclusivo para o `CampusMapCanvas`: evita recomposição quando busca/resultados/detalhes mudam. */
data class CampusMapCanvasState(
    val activeCampusId: String = "OP_MC",
    val activeFloor: Int = 0,
    val renderBatch: RenderBatch? = null,
    val geometryRevision: Long = 0L,
    val selectedFeatureId: String? = null,
    val isFreeRoomsModeEnabled: Boolean = false,
    val activePoiFilter: PoiType? = null,
    val pendingFocus: CameraFocus? = null,
    val isNativeAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/** Slice exclusivo para o HUD superior (busca, campi, chips e resultados). */
data class CampusMapTopHudState(
    val campuses: List<CampusMapSummary> = emptyList(),
    val activeCampusId: String = "OP_MC",
    val activeFloor: Int = 0,
    val availableFloors: List<FloorLabel> = emptyList(),
    val searchQuery: String = "",
    val activePoiFilter: PoiType? = null,
    val searchResults: List<SearchResult> = emptyList(),
    val isFreeRoomsModeEnabled: Boolean = false,
    val resolutionNotice: String? = null
)

/** Slice exclusivo para o HUD inferior (andares, detalhes da sala/prédio e resumo de salas livres). */
data class CampusMapBottomHudState(
    val activeFloor: Int = 0,
    val availableFloors: List<FloorLabel> = emptyList(),
    val selectedFeature: FeatureInfo? = null,
    val selectedOccupancy: OccupancyStatus? = null,
    val selectedBuildingOccupancy: List<RoomOccupancyItem> = emptyList(),
    val isFreeRoomsModeEnabled: Boolean = false,
    val freeRoomsOnActiveFloor: List<RenderFeature> = emptyList(),
    val floorOccupancySummary: FloorOccupancySummary = FloorOccupancySummary(0, 0, 0, 0),
    val isNativeAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val attribution: String? = null
)

internal fun computeFloorOccupancySummary(renderBatch: RenderBatch?): FloorOccupancySummary {
    val items = renderBatch?.items ?: return FloorOccupancySummary(0, 0, 0, 0)
    var free = 0
    var occupied = 0
    var noData = 0
    var outOfTerm = 0
    for (item in items) {
        if (item.layer != MapLayer.ROOM) continue
        when (item.occupancy) {
            RenderOccupancyKind.NO_REGULAR_CLASS -> free += 1
            RenderOccupancyKind.OCCUPIED -> occupied += 1
            RenderOccupancyKind.NO_DATA -> noData += 1
            RenderOccupancyKind.OUT_OF_TERM -> outOfTerm += 1
            RenderOccupancyKind.NONE -> Unit
        }
    }
    return FloorOccupancySummary(
        freeCount = free,
        occupiedCount = occupied,
        noDataCount = noData,
        outOfTermCount = outOfTerm
    )
}

internal fun hasSameBatchGeometry(previous: RenderBatch?, next: RenderBatch): Boolean {
    if (previous === next) return true
    if (previous == null) return false
    if (previous.campusId != next.campusId || previous.activeFloor != next.activeFloor) {
        return false
    }
    if (previous.coords.size != next.coords.size ||
        previous.ringStarts.size != next.ringStarts.size ||
        previous.ringCounts.size != next.ringCounts.size ||
        previous.items.size != next.items.size
    ) {
        return false
    }
    for (i in previous.items.indices) {
        val a = previous.items[i]
        val b = next.items[i]
        if (a.id != b.id ||
            a.layer != b.layer ||
            a.geomKind != b.geomKind ||
            a.ringStartIdx != b.ringStartIdx ||
            a.ringCount != b.ringCount ||
            a.isMock != b.isMock ||
            a.primaryLabel != b.primaryLabel ||
            a.secondaryLabel != b.secondaryLabel
        ) {
            return false
        }
    }
    return true
}

internal fun CampusMapUiState.toCanvasState(): CampusMapCanvasState = CampusMapCanvasState(
    activeCampusId = activeCampusId,
    activeFloor = activeFloor,
    renderBatch = renderBatch,
    geometryRevision = geometryRevision,
    selectedFeatureId = selectedFeature?.id,
    isFreeRoomsModeEnabled = isFreeRoomsModeEnabled,
    activePoiFilter = activePoiFilter,
    pendingFocus = pendingFocus,
    isNativeAvailable = isNativeAvailable,
    isLoading = isLoading,
    errorMessage = errorMessage
)

internal fun CampusMapUiState.toTopHudState(): CampusMapTopHudState = CampusMapTopHudState(
    campuses = campuses,
    activeCampusId = activeCampusId,
    activeFloor = activeFloor,
    availableFloors = availableFloors,
    searchQuery = searchQuery,
    activePoiFilter = activePoiFilter,
    searchResults = searchResults,
    isFreeRoomsModeEnabled = isFreeRoomsModeEnabled,
    resolutionNotice = resolutionNotice
)

internal fun CampusMapUiState.toBottomHudState(): CampusMapBottomHudState {
    val attributionText = renderBatch?.attribution
        ?: campuses.firstOrNull { it.campusId == activeCampusId }?.attribution
    return CampusMapBottomHudState(
        activeFloor = activeFloor,
        availableFloors = availableFloors,
        selectedFeature = selectedFeature,
        selectedOccupancy = selectedOccupancy,
        selectedBuildingOccupancy = selectedBuildingOccupancy,
        isFreeRoomsModeEnabled = isFreeRoomsModeEnabled,
        freeRoomsOnActiveFloor = freeRoomsOnActiveFloor,
        floorOccupancySummary = floorOccupancySummary,
        isNativeAvailable = isNativeAvailable,
        isLoading = isLoading,
        errorMessage = errorMessage,
        attribution = attributionText
    )
}
