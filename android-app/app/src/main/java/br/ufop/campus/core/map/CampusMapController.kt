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
import br.ufop.campus.core.uniffi.Resolution
import br.ufop.campus.core.uniffi.RoomOccupancyItem
import br.ufop.campus.core.uniffi.SearchResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

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
 * mantendo pan e zoom 100% livres de chamadas FFI por frame e executando operações FFI
 * fora da Main Thread quando configurado com [workDispatcher].
 */
class CampusMapController private constructor(
    initialTimestampIso: String?,
    private val engineFactory: () -> CampusMapEngineGateway,
    workDispatcher: CoroutineDispatcher?,
    private val searchDebounceMs: Long,
    @Suppress("UNUSED_PARAMETER") marker: Boolean
) : AutoCloseable {

    constructor(initialTimestampIso: String? = null) : this(
        initialTimestampIso = initialTimestampIso,
        engineFactory = { NativeCampusMapEngineGateway(CampusMapEngine.newBundled()) },
        workDispatcher = Dispatchers.Default,
        searchDebounceMs = DEFAULT_SEARCH_DEBOUNCE_MS,
        marker = true
    )

    internal constructor(
        initialTimestampIso: String? = null,
        engineFactory: () -> CampusMapEngineGateway
    ) : this(
        initialTimestampIso = initialTimestampIso,
        engineFactory = engineFactory,
        workDispatcher = null,
        searchDebounceMs = 0L,
        marker = true
    )

    internal constructor(
        initialTimestampIso: String? = null,
        engineFactory: () -> CampusMapEngineGateway,
        workDispatcher: CoroutineDispatcher?,
        searchDebounceMs: Long
    ) : this(
        initialTimestampIso = initialTimestampIso,
        engineFactory = engineFactory,
        workDispatcher = workDispatcher,
        searchDebounceMs = searchDebounceMs.coerceAtLeast(0L),
        marker = true
    )

    private val engineLock = Any()
    private val stateLock = Any()
    private var nativeEngine: CampusMapEngineGateway? = null
    @Volatile
    private var closed: Boolean = false

    private val controllerScope: CoroutineScope? = workDispatcher?.let {
        CoroutineScope(SupervisorJob() + it)
    }
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var mapActionJob: Job? = null
    private val actionGeneration = AtomicLong(0L)

    private val _state = MutableStateFlow(
        CampusMapUiState(
            timestampIso = initialTimestampIso,
            isLoading = controllerScope != null
        )
    )
    val state: StateFlow<CampusMapUiState> = _state.asStateFlow()

    private val _canvasState = MutableStateFlow(_state.value.toCanvasState())
    val canvasState: StateFlow<CampusMapCanvasState> = _canvasState.asStateFlow()

    private val _topHudState = MutableStateFlow(_state.value.toTopHudState())
    val topHudState: StateFlow<CampusMapTopHudState> = _topHudState.asStateFlow()

    private val _bottomHudState = MutableStateFlow(_state.value.toBottomHudState())
    val bottomHudState: StateFlow<CampusMapBottomHudState> = _bottomHudState.asStateFlow()

    init {
        if (controllerScope != null) {
            loadJob = controllerScope.launch {
                loadBundledEngineLocked(initialTimestampIso)
            }
        } else {
            loadBundledEngineLocked(initialTimestampIso)
        }
    }

    private fun commitState(transform: (CampusMapUiState) -> CampusMapUiState) {
        synchronized(stateLock) {
            val updated = transform(_state.value)
            _state.value = updated
            _canvasState.value = updated.toCanvasState()
            _topHudState.value = updated.toTopHudState()
            _bottomHudState.value = updated.toBottomHudState()
        }
    }

    private fun loadBundledEngineLocked(initialTimestampIso: String?) {
        synchronized(engineLock) {
            if (closed) return
            try {
                val engine = engineFactory()
                if (closed) {
                    runCatching { engine.close() }
                    return
                }
                nativeEngine = engine
                val campuses = engine.listCampuses()
                val activeId = engine.getActiveCampusId()
                val defaultFloor = campuses.firstOrNull { it.campusId == activeId }?.defaultFloor ?: 0
                val batch = engine.getRenderBatch(defaultFloor, effectiveTimestampIso(initialTimestampIso))
                val floors = resolveAvailableFloorsLocked(engine, null, batch)
                commitState { prev ->
                    val nextGeoRev = nextGeometryRevision(prev.renderBatch, batch, prev.geometryRevision)
                    CampusMapUiState(
                        campuses = campuses,
                        activeCampusId = activeId,
                        activeFloor = defaultFloor,
                        timestampIso = initialTimestampIso,
                        renderBatch = batch,
                        geometryRevision = nextGeoRev,
                        availableFloors = floors,
                        isNativeAvailable = true,
                        isLoading = false
                    )
                }
            } catch (err: Throwable) {
                nativeEngine = null
                commitState {
                    CampusMapUiState(
                        timestampIso = initialTimestampIso,
                        isNativeAvailable = false,
                        isLoading = false,
                        errorMessage = err.message ?: "Biblioteca nativa do mapa indisponível."
                    )
                }
            }
        }
    }

    fun selectCampus(campusId: String) {
        searchJob?.cancel()
        val scope = controllerScope
        if (scope == null) {
            selectCampusLocked(campusId)
            return
        }
        val gen = actionGeneration.incrementAndGet()
        mapActionJob?.cancel()
        mapActionJob = scope.launch {
            loadJob?.join()
            if (!isActive || closed || actionGeneration.get() != gen) return@launch
            selectCampusLocked(campusId, expectedGeneration = gen)
        }
    }

    private fun selectCampusLocked(campusId: String, expectedGeneration: Long? = null) {
        synchronized(engineLock) {
            if (closed) return
            if (expectedGeneration != null && actionGeneration.get() != expectedGeneration) return
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
                val floors = resolveAvailableFloorsLocked(engine, null, batch)
                commitState { prev ->
                    val nextGeoRev = nextGeometryRevision(prev.renderBatch, batch, prev.geometryRevision)
                    prev.copy(
                        activeCampusId = summary.campusId,
                        activeFloor = summary.defaultFloor,
                        renderBatch = batch,
                        geometryRevision = nextGeoRev,
                        availableFloors = floors,
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
                commitState { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun selectFloor(floor: Int) {
        val scope = controllerScope
        if (scope == null) {
            selectFloorLocked(floor)
            return
        }
        val gen = actionGeneration.incrementAndGet()
        mapActionJob?.cancel()
        mapActionJob = scope.launch {
            loadJob?.join()
            if (!isActive || closed || actionGeneration.get() != gen) return@launch
            selectFloorLocked(floor, expectedGeneration = gen)
        }
    }

    private fun selectFloorLocked(floor: Int, expectedGeneration: Long? = null) {
        synchronized(engineLock) {
            if (closed) return
            if (expectedGeneration != null && actionGeneration.get() != expectedGeneration) return
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
                val floors = resolveAvailableFloorsLocked(engine, current.selectedFeature, batch)
                commitState { prev ->
                    val nextGeoRev = nextGeometryRevision(prev.renderBatch, batch, prev.geometryRevision)
                    prev.copy(
                        activeFloor = floor,
                        renderBatch = batch,
                        geometryRevision = nextGeoRev,
                        availableFloors = floors,
                        searchResults = refreshedResults,
                        errorMessage = null
                    )
                }
            } catch (err: Throwable) {
                commitState { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun updateTimestamp(timestampIso: String?) {
        val scope = controllerScope
        if (scope == null) {
            updateTimestampLocked(timestampIso)
            return
        }
        val gen = actionGeneration.incrementAndGet()
        scope.launch {
            loadJob?.join()
            if (!isActive || closed || actionGeneration.get() != gen) return@launch
            updateTimestampLocked(timestampIso)
        }
    }

    private fun updateTimestampLocked(timestampIso: String?) {
        synchronized(engineLock) {
            if (closed) return
            val engine = nativeEngine ?: return
            try {
                val current = _state.value
                val floor = current.activeFloor
                val effectiveTs = effectiveTimestampIso(timestampIso)
                val batch = engine.getRenderBatch(floor, effectiveTs)
                val selected = current.selectedFeature
                val occ = resolveOccupancyLocked(engine, selected, effectiveTs)
                val buildingOcc = resolveBuildingOccupancyLocked(engine, selected, effectiveTs)
                val floors = resolveAvailableFloorsLocked(engine, selected, batch)
                commitState { prev ->
                    val nextGeoRev = nextGeometryRevision(prev.renderBatch, batch, prev.geometryRevision)
                    prev.copy(
                        timestampIso = timestampIso,
                        renderBatch = batch,
                        geometryRevision = nextGeoRev,
                        availableFloors = floors,
                        selectedOccupancy = occ,
                        selectedBuildingOccupancy = buildingOcc,
                        errorMessage = null
                    )
                }
            } catch (err: Throwable) {
                commitState { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchJob?.cancel()
        val scope = controllerScope
        if (scope == null) {
            synchronized(engineLock) {
                if (closed) return
                val engine = nativeEngine
                if (engine == null) {
                    commitState { it.copy(searchQuery = query, searchResults = emptyList()) }
                    return
                }
                val current = _state.value
                val results = runSearchIfActiveLocked(
                    engine = engine,
                    query = query,
                    poiFilter = current.activePoiFilter,
                    activeFloor = current.activeFloor
                )
                commitState {
                    it.copy(
                        searchQuery = query,
                        searchResults = results
                    )
                }
            }
            return
        }

        val currentFilter = _state.value.activePoiFilter
        if (query.isBlank() && currentFilter == null) {
            commitState { it.copy(searchQuery = query, searchResults = emptyList()) }
            return
        }
        commitState { it.copy(searchQuery = query) }
        searchJob = scope.launch {
            if (searchDebounceMs > 0L) {
                delay(searchDebounceMs)
            }
            loadJob?.join()
            if (!isActive || closed) return@launch
            synchronized(engineLock) {
                if (!isActive || closed) return@synchronized
                val engine = nativeEngine
                if (engine == null) {
                    commitState { it.copy(searchResults = emptyList()) }
                    return@synchronized
                }
                val current = _state.value
                if (current.searchQuery != query) return@synchronized
                val results = runSearchIfActiveLocked(
                    engine = engine,
                    query = query,
                    poiFilter = current.activePoiFilter,
                    activeFloor = current.activeFloor
                )
                commitState { latest ->
                    if (latest.searchQuery == query) {
                        latest.copy(searchResults = results)
                    } else {
                        latest
                    }
                }
            }
        }
    }

    fun togglePoiFilter(poiType: PoiType) {
        searchJob?.cancel()
        val scope = controllerScope
        if (scope == null) {
            synchronized(engineLock) {
                if (closed) return
                val current = _state.value
                val nextFilter = if (current.activePoiFilter == poiType) null else poiType
                val engine = nativeEngine
                if (engine == null) {
                    commitState { it.copy(activePoiFilter = nextFilter, searchResults = emptyList()) }
                    return
                }
                val results = runSearchIfActiveLocked(
                    engine = engine,
                    query = current.searchQuery,
                    poiFilter = nextFilter,
                    activeFloor = current.activeFloor
                )
                commitState {
                    it.copy(
                        activePoiFilter = nextFilter,
                        searchResults = results
                    )
                }
            }
            return
        }

        var queryNow = ""
        var nextFilterNow: PoiType? = null
        commitState { current ->
            val nextFilter = if (current.activePoiFilter == poiType) null else poiType
            queryNow = current.searchQuery
            nextFilterNow = nextFilter
            if (nextFilter == null && queryNow.isBlank()) {
                current.copy(activePoiFilter = null, searchResults = emptyList())
            } else {
                current.copy(activePoiFilter = nextFilter)
            }
        }
        if (nextFilterNow == null && queryNow.isBlank()) {
            return
        }
        searchJob = scope.launch {
            loadJob?.join()
            if (!isActive || closed) return@launch
            synchronized(engineLock) {
                if (!isActive || closed) return@synchronized
                val engine = nativeEngine ?: return@synchronized
                val current = _state.value
                val results = runSearchIfActiveLocked(
                    engine = engine,
                    query = current.searchQuery,
                    poiFilter = current.activePoiFilter,
                    activeFloor = current.activeFloor
                )
                commitState { latest ->
                    if (latest.activePoiFilter == current.activePoiFilter &&
                        latest.searchQuery == current.searchQuery
                    ) {
                        latest.copy(searchResults = results)
                    } else {
                        latest
                    }
                }
            }
        }
    }

    fun clearSearchAndFilters() {
        searchJob?.cancel()
        commitState {
            it.copy(
                searchQuery = "",
                activePoiFilter = null,
                searchResults = emptyList()
            )
        }
    }

    fun selectSearchResult(result: SearchResult) {
        searchJob?.cancel()
        val scope = controllerScope
        if (scope == null) {
            synchronized(engineLock) {
                if (closed) return
                focusFeatureLocked(result.feature.id)
                commitState {
                    it.copy(
                        searchQuery = "",
                        searchResults = emptyList()
                    )
                }
            }
            return
        }

        commitState {
            it.copy(
                searchQuery = "",
                searchResults = emptyList()
            )
        }
        focusFeature(result.feature.id)
    }

    fun toggleFreeRoomsMode() {
        setFreeRoomsMode(!_state.value.isFreeRoomsModeEnabled)
    }

    fun setFreeRoomsMode(enabled: Boolean) {
        val scope = controllerScope
        if (scope == null) {
            setFreeRoomsModeLocked(enabled)
            return
        }
        commitState { it.copy(isFreeRoomsModeEnabled = enabled) }
        if (!enabled) return
        val current = _state.value
        if (current.timestampIso != null && current.renderBatch != null) return

        val gen = actionGeneration.incrementAndGet()
        scope.launch {
            loadJob?.join()
            if (!isActive || closed || actionGeneration.get() != gen) return@launch
            synchronized(engineLock) {
                if (!isActive || closed) return@synchronized
                val engine = nativeEngine ?: return@synchronized
                val latest = _state.value
                if (!latest.isFreeRoomsModeEnabled) return@synchronized
                val refreshedBatch = runCatching {
                    engine.getRenderBatch(latest.activeFloor, effectiveTimestampIso(latest.timestampIso))
                }.getOrNull() ?: return@synchronized
                val floors = resolveAvailableFloorsLocked(engine, latest.selectedFeature, refreshedBatch)
                commitState { prev ->
                    val nextGeoRev = nextGeometryRevision(prev.renderBatch, refreshedBatch, prev.geometryRevision)
                    prev.copy(
                        renderBatch = refreshedBatch,
                        geometryRevision = nextGeoRev,
                        availableFloors = floors
                    )
                }
            }
        }
    }

    private fun setFreeRoomsModeLocked(enabled: Boolean) {
        synchronized(engineLock) {
            if (closed) return
            val engine = nativeEngine
            if (engine == null) {
                commitState { it.copy(isFreeRoomsModeEnabled = enabled) }
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
            commitState { prev ->
                val targetBatch = refreshedBatch ?: prev.renderBatch
                val nextGeoRev = if (refreshedBatch != null) {
                    nextGeometryRevision(prev.renderBatch, refreshedBatch, prev.geometryRevision)
                } else {
                    prev.geometryRevision
                }
                prev.copy(
                    isFreeRoomsModeEnabled = enabled,
                    renderBatch = targetBatch,
                    geometryRevision = nextGeoRev
                )
            }
        }
    }

    fun clearResolutionNotice() {
        commitState { it.copy(resolutionNotice = null) }
    }

    fun onMapTabActiveChanged(isActive: Boolean) {
        if (!isActive) {
            searchJob?.cancel()
        }
    }

    fun onMapTap(mapX: Float, mapY: Float, zoom: Float) {
        val scope = controllerScope
        if (scope == null) {
            handleTap(mapX, mapY, zoom)
            return
        }
        val gen = actionGeneration.incrementAndGet()
        scope.launch {
            loadJob?.join()
            if (!isActive || closed || actionGeneration.get() != gen) return@launch
            handleTap(mapX, mapY, zoom)
        }
    }

    fun handleTap(mapX: Float, mapY: Float, zoom: Float): MapHit? {
        synchronized(engineLock) {
            if (closed) return null
            val engine = nativeEngine ?: return null
            val current = _state.value
            val hit = engine.hitTest(mapX, mapY, current.activeFloor, zoom)
            if (hit == null) {
                val floors = resolveAvailableFloorsLocked(engine, null, current.renderBatch)
                commitState {
                    it.copy(
                        selectedFeature = null,
                        selectedOccupancy = null,
                        selectedBuildingOccupancy = emptyList(),
                        availableFloors = floors
                    )
                }
                return null
            }
            val effectiveTs = effectiveTimestampIso(current.timestampIso)
            val occ = resolveOccupancyLocked(engine, hit.feature, effectiveTs)
            val buildingOcc = resolveBuildingOccupancyLocked(engine, hit.feature, effectiveTs)
            val floors = resolveAvailableFloorsLocked(engine, hit.feature, current.renderBatch)
            commitState {
                it.copy(
                    selectedFeature = hit.feature,
                    selectedOccupancy = occ,
                    selectedBuildingOccupancy = buildingOcc,
                    availableFloors = floors,
                    errorMessage = null
                )
            }
            return hit
        }
    }

    fun focusFeature(featureId: String) {
        val scope = controllerScope
        if (scope == null) {
            synchronized(engineLock) {
                if (closed) return
                focusFeatureLocked(featureId)
            }
            return
        }
        val gen = actionGeneration.incrementAndGet()
        mapActionJob?.cancel()
        mapActionJob = scope.launch {
            loadJob?.join()
            if (!isActive || closed || actionGeneration.get() != gen) return@launch
            synchronized(engineLock) {
                if (!isActive || closed || actionGeneration.get() != gen) return@synchronized
                focusFeatureLocked(featureId)
            }
        }
    }

    private fun focusFeatureLocked(featureId: String) {
        val engine = nativeEngine ?: return
        val focus = engine.getCameraFocus(featureId) ?: return
        val batch = applyCameraFocusLocked(engine, focus)
        val feat = engine.getFeature(focus.featureId)
        val effectiveTs = effectiveTimestampIso(_state.value.timestampIso)
        val occ = resolveOccupancyLocked(engine, feat, effectiveTs)
        val buildingOcc = resolveBuildingOccupancyLocked(engine, feat, effectiveTs)
        val floors = resolveAvailableFloorsLocked(engine, feat, batch ?: _state.value.renderBatch)
        commitState { prev ->
            prev.copy(
                selectedFeature = feat,
                selectedOccupancy = occ,
                selectedBuildingOccupancy = buildingOcc,
                availableFloors = floors
            )
        }
    }

    fun resolveMeetingAsync(
        courseCode: String,
        section: String,
        weekday: Int,
        startTime: String,
        fallbackBuildingRaw: String
    ) {
        val scope = controllerScope
        if (scope == null) {
            resolveMeeting(courseCode, section, weekday, startTime, fallbackBuildingRaw)
            return
        }
        val gen = actionGeneration.incrementAndGet()
        mapActionJob?.cancel()
        mapActionJob = scope.launch {
            loadJob?.join()
            if (!isActive || closed || actionGeneration.get() != gen) return@launch
            resolveMeeting(courseCode, section, weekday, startTime, fallbackBuildingRaw)
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
            if (closed) return Resolution.Unmapped
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
                    val batch = applyCameraFocusLocked(engine, res.cameraFocus)
                    val feat = engine.getFeature(res.roomId)
                    val occ = resolveOccupancyLocked(engine, feat, effectiveTs)
                    val floors = resolveAvailableFloorsLocked(engine, feat, batch ?: _state.value.renderBatch)
                    commitState {
                        it.copy(
                            selectedFeature = feat,
                            selectedOccupancy = occ,
                            selectedBuildingOccupancy = emptyList(),
                            availableFloors = floors,
                            resolutionNotice = null,
                            errorMessage = null
                        )
                    }
                }
                is Resolution.BuildingFallback -> {
                    val batch = applyCameraFocusLocked(engine, res.cameraFocus)
                    val feat = engine.getFeature(res.buildingId)
                    val buildingOcc = if (feat != null) {
                        resolveBuildingOccupancyLocked(engine, feat, effectiveTs)
                    } else {
                        runCatching { engine.listBuildingOccupancy(res.buildingId, effectiveTs) }
                            .getOrDefault(emptyList())
                    }
                    val floors = resolveAvailableFloorsLocked(engine, feat, batch ?: _state.value.renderBatch)
                    commitState {
                        it.copy(
                            selectedFeature = feat,
                            selectedOccupancy = null,
                            selectedBuildingOccupancy = buildingOcc,
                            availableFloors = floors,
                            resolutionNotice = "Sala específica sem mapa interno — exibindo o prédio.",
                            errorMessage = null
                        )
                    }
                }
                Resolution.Unmapped -> {
                    commitState {
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
        commitState { it.copy(pendingFocus = null) }
    }

    fun clearSelection() {
        val current = _state.value
        val fallbackFloors = current.renderBatch?.floorLabels.orEmpty()
        commitState {
            it.copy(
                selectedFeature = null,
                selectedOccupancy = null,
                selectedBuildingOccupancy = emptyList(),
                availableFloors = fallbackFloors
            )
        }
    }

    fun availableFloorsForUi(): List<FloorLabel> = _state.value.availableFloors

    private fun resolveAvailableFloorsLocked(
        engine: CampusMapEngineGateway?,
        selected: FeatureInfo?,
        batch: RenderBatch?
    ): List<FloorLabel> {
        if (selected != null && selected.floorLabels.isNotEmpty()) {
            return selected.floorLabels
        }
        val parentId = selected?.buildingId ?: selected?.parentId
        if (!parentId.isNullOrBlank() && engine != null) {
            val parentLabels = runCatching { engine.getFeature(parentId)?.floorLabels }.getOrNull()
            if (!parentLabels.isNullOrEmpty()) {
                return parentLabels
            }
        }
        return batch?.floorLabels.orEmpty()
    }

    private fun applyCameraFocusLocked(
        engine: CampusMapEngineGateway,
        focus: CameraFocus
    ): RenderBatch? {
        if (focus.campusId != _state.value.activeCampusId) {
            runCatching { engine.setActiveCampus(focus.campusId) }
        }
        val batch = runCatching {
            engine.getRenderBatch(focus.targetFloor, effectiveTimestampIso(_state.value.timestampIso))
        }.getOrNull()
        commitState { prev ->
            val targetBatch = batch ?: prev.renderBatch
            val nextGeoRev = if (batch != null) {
                nextGeometryRevision(prev.renderBatch, batch, prev.geometryRevision)
            } else {
                prev.geometryRevision
            }
            prev.copy(
                activeCampusId = focus.campusId,
                activeFloor = focus.targetFloor,
                renderBatch = targetBatch,
                geometryRevision = nextGeoRev,
                pendingFocus = focus
            )
        }
        return batch
    }

    private fun nextGeometryRevision(
        previous: RenderBatch?,
        next: RenderBatch,
        currentRevision: Long
    ): Long = if (hasSameBatchGeometry(previous, next)) {
        currentRevision
    } else {
        currentRevision + 1L
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
        closed = true
        searchJob?.cancel()
        mapActionJob?.cancel()
        loadJob?.cancel()
        controllerScope?.cancel()
        synchronized(engineLock) {
            nativeEngine?.close()
            nativeEngine = null
        }
    }

    private companion object {
        const val DEFAULT_SEARCH_DEBOUNCE_MS = 135L
        val SAO_PAULO_ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")
    }
}
