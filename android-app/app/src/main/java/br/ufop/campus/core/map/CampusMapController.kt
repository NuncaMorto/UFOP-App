package br.ufop.campus.core.map

import br.ufop.campus.core.uniffi.CameraFocus
import br.ufop.campus.core.uniffi.CampusMapEngine
import br.ufop.campus.core.uniffi.CampusMapSummary
import br.ufop.campus.core.uniffi.FeatureInfo
import br.ufop.campus.core.uniffi.FloorLabel
import br.ufop.campus.core.uniffi.MapHit
import br.ufop.campus.core.uniffi.MapLayer
import br.ufop.campus.core.uniffi.OccupancyStatus
import br.ufop.campus.core.uniffi.PoiType
import br.ufop.campus.core.uniffi.RenderBatch
import br.ufop.campus.core.uniffi.RenderFeature
import br.ufop.campus.core.uniffi.RenderOccupancyKind
import br.ufop.campus.core.uniffi.Resolution
import br.ufop.campus.core.uniffi.RoomOccupancyItem
import br.ufop.campus.core.uniffi.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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
    val errorMessage: String? = null
) {
    val freeRoomsOnActiveFloor: List<RenderFeature>
        get() = renderBatch?.items
            ?.filter { it.layer == MapLayer.ROOM && it.occupancy == RenderOccupancyKind.NO_REGULAR_CLASS }
            .orEmpty()

    val floorOccupancySummary: FloorOccupancySummary
        get() {
            val rooms = renderBatch?.items?.filter { it.layer == MapLayer.ROOM }.orEmpty()
            var free = 0
            var occupied = 0
            var noData = 0
            var outOfTerm = 0
            rooms.forEach { room ->
                when (room.occupancy) {
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
}

internal interface CampusMapEngineGateway : AutoCloseable {
    fun listCampuses(): List<CampusMapSummary>
    fun getActiveCampusId(): String
    fun setActiveCampus(campusId: String): CampusMapSummary
    fun getRenderBatch(floor: Int, timestampIso: String?): RenderBatch
    fun hitTest(mapX: Float, mapY: Float, floor: Int, zoom: Float): MapHit?
    fun getCameraFocus(featureId: String): CameraFocus?
    fun getFeature(featureId: String): FeatureInfo?
    fun search(query: String, poiTypeFilter: PoiType?, activeFloor: Int): List<SearchResult>
    fun getRoomOccupancy(roomId: String, timestampIso: String): OccupancyStatus
    fun listBuildingOccupancy(buildingId: String, timestampIso: String): List<RoomOccupancyItem>
    fun resolveMeeting(
        courseCode: String,
        section: String,
        weekday: UInt,
        startTime: String,
        fallbackBuildingRaw: String
    ): Resolution
}

private class NativeCampusMapEngineGateway(
    private val delegate: CampusMapEngine
) : CampusMapEngineGateway {
    override fun listCampuses(): List<CampusMapSummary> = delegate.listCampuses()

    override fun getActiveCampusId(): String = delegate.getActiveCampusId()

    override fun setActiveCampus(campusId: String): CampusMapSummary =
        delegate.setActiveCampus(campusId)

    override fun getRenderBatch(floor: Int, timestampIso: String?): RenderBatch =
        delegate.getRenderBatch(floor, timestampIso)

    override fun hitTest(mapX: Float, mapY: Float, floor: Int, zoom: Float): MapHit? =
        delegate.hitTest(mapX, mapY, floor, zoom)

    override fun getCameraFocus(featureId: String): CameraFocus? =
        delegate.getCameraFocus(featureId)

    override fun getFeature(featureId: String): FeatureInfo? = delegate.getFeature(featureId)

    override fun search(query: String, poiTypeFilter: PoiType?, activeFloor: Int): List<SearchResult> =
        delegate.search(query, poiTypeFilter, activeFloor)

    override fun getRoomOccupancy(roomId: String, timestampIso: String): OccupancyStatus =
        delegate.getRoomOccupancy(roomId, timestampIso)

    override fun listBuildingOccupancy(buildingId: String, timestampIso: String): List<RoomOccupancyItem> =
        delegate.listBuildingOccupancy(buildingId, timestampIso)

    override fun resolveMeeting(
        courseCode: String,
        section: String,
        weekday: UInt,
        startTime: String,
        fallbackBuildingRaw: String
    ): Resolution = delegate.resolveMeeting(
        courseCode = courseCode,
        section = section,
        weekday = weekday,
        startTime = startTime,
        fallbackBuildingRaw = fallbackBuildingRaw
    )

    override fun close() = delegate.close()
}

/**
 * Controlador stateful do motor vetorial offline 2D (`CampusMapEngine`).
 * Gera o [RenderBatch] apenas em trocas discretas de campus, andar ou horário,
 * mantendo pan e zoom 100% livres de chamadas FFI por frame.
 */
class CampusMapController private constructor(
    initialTimestampIso: String?,
    private val engineFactory: () -> CampusMapEngineGateway,
    @Suppress("UNUSED_PARAMETER") marker: Boolean
) : AutoCloseable {

    constructor(initialTimestampIso: String? = null) : this(
        initialTimestampIso = initialTimestampIso,
        engineFactory = { NativeCampusMapEngineGateway(CampusMapEngine.newBundled()) },
        marker = true
    )

    internal constructor(
        initialTimestampIso: String? = null,
        engineFactory: () -> CampusMapEngineGateway
    ) : this(
        initialTimestampIso = initialTimestampIso,
        engineFactory = engineFactory,
        marker = true
    )

    private val engineLock = Any()
    private var nativeEngine: CampusMapEngineGateway? = null

    private val _state = MutableStateFlow(
        CampusMapUiState(timestampIso = initialTimestampIso)
    )
    val state: StateFlow<CampusMapUiState> = _state.asStateFlow()

    init {
        loadBundledEngine(initialTimestampIso)
    }

    private fun loadBundledEngine(initialTimestampIso: String?) {
        synchronized(engineLock) {
            try {
                val engine = engineFactory()
                nativeEngine = engine
                val campuses = engine.listCampuses()
                val activeId = engine.getActiveCampusId()
                val defaultFloor = campuses.firstOrNull { it.campusId == activeId }?.defaultFloor ?: 0
                val batch = engine.getRenderBatch(defaultFloor, effectiveTimestampIso(initialTimestampIso))
                _state.value = CampusMapUiState(
                    campuses = campuses,
                    activeCampusId = activeId,
                    activeFloor = defaultFloor,
                    timestampIso = initialTimestampIso,
                    renderBatch = batch,
                    isNativeAvailable = true
                )
            } catch (err: Throwable) {
                nativeEngine = null
                _state.value = CampusMapUiState(
                    timestampIso = initialTimestampIso,
                    isNativeAvailable = false,
                    errorMessage = err.message ?: "Biblioteca nativa do mapa indisponível."
                )
            }
        }
    }

    fun selectCampus(campusId: String) {
        synchronized(engineLock) {
            val engine = nativeEngine ?: return
            try {
                val summary = engine.setActiveCampus(campusId)
                val current = _state.value
                val effectiveTs = effectiveTimestampIso(current.timestampIso)
                val batch = engine.getRenderBatch(summary.defaultFloor, effectiveTs)
                val refreshedResults = runSearchIfActiveLocked(
                    engine = engine,
                    query = current.searchQuery,
                    poiFilter = current.activePoiFilter,
                    activeFloor = summary.defaultFloor
                )
                _state.update {
                    it.copy(
                        activeCampusId = summary.campusId,
                        activeFloor = summary.defaultFloor,
                        renderBatch = batch,
                        selectedFeature = null,
                        selectedOccupancy = null,
                        selectedBuildingOccupancy = emptyList(),
                        searchResults = refreshedResults,
                        pendingFocus = CameraFocus(
                            campusId = summary.campusId,
                            featureId = summary.campusId,
                            targetFloor = summary.defaultFloor,
                            bounds = summary.bounds
                        ),
                        errorMessage = null
                    )
                }
            } catch (err: Throwable) {
                _state.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun selectFloor(floor: Int) {
        synchronized(engineLock) {
            val current = _state.value
            if (current.activeFloor == floor) return
            val engine = nativeEngine ?: return
            try {
                val effectiveTs = effectiveTimestampIso(current.timestampIso)
                val batch = engine.getRenderBatch(floor, effectiveTs)
                val refreshedResults = runSearchIfActiveLocked(
                    engine = engine,
                    query = current.searchQuery,
                    poiFilter = current.activePoiFilter,
                    activeFloor = floor
                )
                _state.update {
                    it.copy(
                        activeFloor = floor,
                        renderBatch = batch,
                        searchResults = refreshedResults,
                        errorMessage = null
                    )
                }
            } catch (err: Throwable) {
                _state.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun updateTimestamp(timestampIso: String?) {
        synchronized(engineLock) {
            val engine = nativeEngine ?: return
            try {
                val floor = _state.value.activeFloor
                val effectiveTs = effectiveTimestampIso(timestampIso)
                val batch = engine.getRenderBatch(floor, effectiveTs)
                val selected = _state.value.selectedFeature
                val occ = resolveOccupancyLocked(engine, selected, effectiveTs)
                val buildingOcc = resolveBuildingOccupancyLocked(engine, selected, effectiveTs)
                _state.update {
                    it.copy(
                        timestampIso = timestampIso,
                        renderBatch = batch,
                        selectedOccupancy = occ,
                        selectedBuildingOccupancy = buildingOcc,
                        errorMessage = null
                    )
                }
            } catch (err: Throwable) {
                _state.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        synchronized(engineLock) {
            val engine = nativeEngine
            if (engine == null) {
                _state.update { it.copy(searchQuery = query, searchResults = emptyList()) }
                return
            }
            val current = _state.value
            val results = runSearchIfActiveLocked(
                engine = engine,
                query = query,
                poiFilter = current.activePoiFilter,
                activeFloor = current.activeFloor
            )
            _state.update {
                it.copy(
                    searchQuery = query,
                    searchResults = results
                )
            }
        }
    }

    fun togglePoiFilter(poiType: PoiType) {
        synchronized(engineLock) {
            val current = _state.value
            val nextFilter = if (current.activePoiFilter == poiType) null else poiType
            val engine = nativeEngine
            if (engine == null) {
                _state.update { it.copy(activePoiFilter = nextFilter, searchResults = emptyList()) }
                return
            }
            val results = runSearchIfActiveLocked(
                engine = engine,
                query = current.searchQuery,
                poiFilter = nextFilter,
                activeFloor = current.activeFloor
            )
            _state.update {
                it.copy(
                    activePoiFilter = nextFilter,
                    searchResults = results
                )
            }
        }
    }

    fun clearSearchAndFilters() {
        synchronized(engineLock) {
            _state.update {
                it.copy(
                    searchQuery = "",
                    activePoiFilter = null,
                    searchResults = emptyList()
                )
            }
        }
    }

    fun selectSearchResult(result: SearchResult) {
        synchronized(engineLock) {
            focusFeature(result.feature.id)
            _state.update {
                it.copy(
                    searchQuery = "",
                    searchResults = emptyList()
                )
            }
        }
    }

    fun toggleFreeRoomsMode() {
        setFreeRoomsMode(!_state.value.isFreeRoomsModeEnabled)
    }

    fun setFreeRoomsMode(enabled: Boolean) {
        synchronized(engineLock) {
            val engine = nativeEngine
            if (engine == null) {
                _state.update { it.copy(isFreeRoomsModeEnabled = enabled) }
                return
            }
            val current = _state.value
            val refreshedBatch = if (enabled && current.timestampIso == null) {
                runCatching {
                    engine.getRenderBatch(current.activeFloor, effectiveTimestampIso(null))
                }.getOrNull()
            } else {
                null
            }
            _state.update {
                it.copy(
                    isFreeRoomsModeEnabled = enabled,
                    renderBatch = refreshedBatch ?: it.renderBatch
                )
            }
        }
    }

    fun clearResolutionNotice() {
        _state.update { it.copy(resolutionNotice = null) }
    }

    fun handleTap(mapX: Float, mapY: Float, zoom: Float): MapHit? {
        synchronized(engineLock) {
            val engine = nativeEngine ?: return null
            val current = _state.value
            val hit = engine.hitTest(mapX, mapY, current.activeFloor, zoom)
            if (hit == null) {
                _state.update {
                    it.copy(
                        selectedFeature = null,
                        selectedOccupancy = null,
                        selectedBuildingOccupancy = emptyList()
                    )
                }
                return null
            }
            val effectiveTs = effectiveTimestampIso(current.timestampIso)
            val occ = resolveOccupancyLocked(engine, hit.feature, effectiveTs)
            val buildingOcc = resolveBuildingOccupancyLocked(engine, hit.feature, effectiveTs)
            _state.update {
                it.copy(
                    selectedFeature = hit.feature,
                    selectedOccupancy = occ,
                    selectedBuildingOccupancy = buildingOcc,
                    errorMessage = null
                )
            }
            return hit
        }
    }

    fun focusFeature(featureId: String) {
        synchronized(engineLock) {
            val engine = nativeEngine ?: return
            val focus = engine.getCameraFocus(featureId) ?: return
            applyCameraFocusLocked(engine, focus)
            val feat = engine.getFeature(focus.featureId)
            val effectiveTs = effectiveTimestampIso(_state.value.timestampIso)
            val occ = resolveOccupancyLocked(engine, feat, effectiveTs)
            val buildingOcc = resolveBuildingOccupancyLocked(engine, feat, effectiveTs)
            _state.update {
                it.copy(
                    selectedFeature = feat,
                    selectedOccupancy = occ,
                    selectedBuildingOccupancy = buildingOcc
                )
            }
        }
    }

    fun resolveMeeting(
        courseCode: String,
        section: String,
        weekday: Int,
        startTime: String,
        fallbackBuildingRaw: String
    ): Resolution {
        synchronized(engineLock) {
            val engine = nativeEngine ?: return Resolution.Unmapped
            val res = engine.resolveMeeting(
                courseCode = courseCode,
                section = section,
                weekday = weekday.coerceAtLeast(1).toUInt(),
                startTime = startTime,
                fallbackBuildingRaw = fallbackBuildingRaw
            )
            val effectiveTs = effectiveTimestampIso(_state.value.timestampIso)
            when (res) {
                is Resolution.ExactRoom -> {
                    applyCameraFocusLocked(engine, res.cameraFocus)
                    val feat = engine.getFeature(res.roomId)
                    val occ = resolveOccupancyLocked(engine, feat, effectiveTs)
                    _state.update {
                        it.copy(
                            selectedFeature = feat,
                            selectedOccupancy = occ,
                            selectedBuildingOccupancy = emptyList(),
                            resolutionNotice = null,
                            errorMessage = null
                        )
                    }
                }
                is Resolution.BuildingFallback -> {
                    applyCameraFocusLocked(engine, res.cameraFocus)
                    val feat = engine.getFeature(res.buildingId)
                    val buildingOcc = if (feat != null) {
                        resolveBuildingOccupancyLocked(engine, feat, effectiveTs)
                    } else {
                        runCatching { engine.listBuildingOccupancy(res.buildingId, effectiveTs) }
                            .getOrDefault(emptyList())
                    }
                    _state.update {
                        it.copy(
                            selectedFeature = feat,
                            selectedOccupancy = null,
                            selectedBuildingOccupancy = buildingOcc,
                            resolutionNotice = "Sala específica sem mapa interno — exibindo o prédio.",
                            errorMessage = null
                        )
                    }
                }
                Resolution.Unmapped -> {
                    _state.update {
                        it.copy(
                            resolutionNotice = "Local não mapeado neste campus. Use a busca para encontrar o prédio."
                        )
                    }
                }
            }
            return res
        }
    }

    fun consumePendingFocus() {
        _state.update { it.copy(pendingFocus = null) }
    }

    fun clearSelection() {
        _state.update {
            it.copy(
                selectedFeature = null,
                selectedOccupancy = null,
                selectedBuildingOccupancy = emptyList()
            )
        }
    }

    fun availableFloorsForUi(): List<FloorLabel> {
        val current = _state.value
        val selected = current.selectedFeature
        if (selected != null && selected.floorLabels.isNotEmpty()) {
            return selected.floorLabels
        }
        val parentId = selected?.buildingId ?: selected?.parentId
        if (!parentId.isNullOrBlank()) {
            synchronized(engineLock) {
                val parentLabels = nativeEngine?.getFeature(parentId)?.floorLabels
                if (!parentLabels.isNullOrEmpty()) {
                    return parentLabels
                }
            }
        }
        return current.renderBatch?.floorLabels.orEmpty()
    }

    private fun applyCameraFocusLocked(engine: CampusMapEngineGateway, focus: CameraFocus) {
        if (focus.campusId != _state.value.activeCampusId) {
            runCatching { engine.setActiveCampus(focus.campusId) }
        }
        val batch = runCatching {
            engine.getRenderBatch(focus.targetFloor, effectiveTimestampIso(_state.value.timestampIso))
        }.getOrNull()
        _state.update {
            it.copy(
                activeCampusId = focus.campusId,
                activeFloor = focus.targetFloor,
                renderBatch = batch ?: it.renderBatch,
                pendingFocus = focus
            )
        }
    }

    private fun runSearchIfActiveLocked(
        engine: CampusMapEngineGateway,
        query: String,
        poiFilter: PoiType?,
        activeFloor: Int
    ): List<SearchResult> {
        if (query.isBlank() && poiFilter == null) return emptyList()
        return runCatching {
            engine.search(query.trim(), poiFilter, activeFloor)
        }.getOrDefault(emptyList())
    }

    private fun resolveOccupancyLocked(
        engine: CampusMapEngineGateway,
        feature: FeatureInfo?,
        effectiveTs: String
    ): OccupancyStatus? {
        if (feature == null || feature.layer != MapLayer.ROOM) return null
        return runCatching { engine.getRoomOccupancy(feature.id, effectiveTs) }.getOrNull()
    }

    private fun resolveBuildingOccupancyLocked(
        engine: CampusMapEngineGateway,
        feature: FeatureInfo?,
        effectiveTs: String
    ): List<RoomOccupancyItem> {
        if (feature == null) return emptyList()
        if (feature.layer != MapLayer.BUILDING && feature.layer != MapLayer.CAMPUS_ZONE) {
            return emptyList()
        }
        return runCatching { engine.listBuildingOccupancy(feature.id, effectiveTs) }
            .getOrDefault(emptyList())
    }

    private fun effectiveTimestampIso(timestampIso: String?): String =
        timestampIso ?: ZonedDateTime.now(SAO_PAULO_ZONE)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    override fun close() {
        synchronized(engineLock) {
            nativeEngine?.close()
            nativeEngine = null
        }
    }

    private companion object {
        val SAO_PAULO_ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")
    }
}
