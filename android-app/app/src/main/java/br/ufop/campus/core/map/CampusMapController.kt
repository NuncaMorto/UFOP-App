package br.ufop.campus.core.map

import br.ufop.campus.core.uniffi.CameraFocus
import br.ufop.campus.core.uniffi.CampusMapEngine
import br.ufop.campus.core.uniffi.CampusMapSummary
import br.ufop.campus.core.uniffi.FeatureInfo
import br.ufop.campus.core.uniffi.FloorLabel
import br.ufop.campus.core.uniffi.MapHit
import br.ufop.campus.core.uniffi.MapLayer
import br.ufop.campus.core.uniffi.OccupancyStatus
import br.ufop.campus.core.uniffi.RenderBatch
import br.ufop.campus.core.uniffi.Resolution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class CampusMapUiState(
    val campuses: List<CampusMapSummary> = emptyList(),
    val activeCampusId: String = "OP_MC",
    val activeFloor: Int = 0,
    val timestampIso: String? = null,
    val renderBatch: RenderBatch? = null,
    val selectedFeature: FeatureInfo? = null,
    val selectedOccupancy: OccupancyStatus? = null,
    val pendingFocus: CameraFocus? = null,
    val isNativeAvailable: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Controlador stateful do motor vetorial offline 2D (`CampusMapEngine`).
 * Gera o [RenderBatch] apenas em trocas discretas de campus, andar ou horário,
 * mantendo pan e zoom 100% livres de chamadas FFI por frame.
 */
class CampusMapController(
    initialTimestampIso: String? = null
) : AutoCloseable {

    private val engineLock = Any()
    private var nativeEngine: CampusMapEngine? = null

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
                val engine = CampusMapEngine.newBundled()
                nativeEngine = engine
                val campuses = engine.listCampuses()
                val activeId = engine.getActiveCampusId()
                val defaultFloor = campuses.firstOrNull { it.campusId == activeId }?.defaultFloor ?: 0
                val batch = engine.getRenderBatch(defaultFloor, initialTimestampIso)
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
                val ts = _state.value.timestampIso
                val batch = engine.getRenderBatch(summary.defaultFloor, ts)
                _state.update {
                    it.copy(
                        activeCampusId = summary.campusId,
                        activeFloor = summary.defaultFloor,
                        renderBatch = batch,
                        selectedFeature = null,
                        selectedOccupancy = null,
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
            if (_state.value.activeFloor == floor) return
            val engine = nativeEngine ?: return
            try {
                val batch = engine.getRenderBatch(floor, _state.value.timestampIso)
                _state.update {
                    it.copy(
                        activeFloor = floor,
                        renderBatch = batch,
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
                val batch = engine.getRenderBatch(floor, timestampIso)
                val selected = _state.value.selectedFeature
                val occ = resolveOccupancyLocked(engine, selected, timestampIso)
                _state.update {
                    it.copy(
                        timestampIso = timestampIso,
                        renderBatch = batch,
                        selectedOccupancy = occ,
                        errorMessage = null
                    )
                }
            } catch (err: Throwable) {
                _state.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun handleTap(mapX: Float, mapY: Float, zoom: Float): MapHit? {
        synchronized(engineLock) {
            val engine = nativeEngine ?: return null
            val current = _state.value
            val hit = engine.hitTest(mapX, mapY, current.activeFloor, zoom)
            if (hit == null) {
                _state.update { it.copy(selectedFeature = null, selectedOccupancy = null) }
                return null
            }
            val occ = resolveOccupancyLocked(engine, hit.feature, current.timestampIso)
            _state.update {
                it.copy(
                    selectedFeature = hit.feature,
                    selectedOccupancy = occ,
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
            val occ = resolveOccupancyLocked(engine, feat, _state.value.timestampIso)
            _state.update {
                it.copy(selectedFeature = feat, selectedOccupancy = occ)
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
            when (res) {
                is Resolution.ExactRoom -> {
                    applyCameraFocusLocked(engine, res.cameraFocus)
                    val feat = engine.getFeature(res.roomId)
                    val occ = resolveOccupancyLocked(engine, feat, _state.value.timestampIso)
                    _state.update { it.copy(selectedFeature = feat, selectedOccupancy = occ) }
                }
                is Resolution.BuildingFallback -> {
                    applyCameraFocusLocked(engine, res.cameraFocus)
                    val feat = engine.getFeature(res.buildingId)
                    _state.update { it.copy(selectedFeature = feat, selectedOccupancy = null) }
                }
                Resolution.Unmapped -> Unit
            }
            return res
        }
    }

    fun consumePendingFocus() {
        _state.update { it.copy(pendingFocus = null) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedFeature = null, selectedOccupancy = null) }
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

    private fun applyCameraFocusLocked(engine: CampusMapEngine, focus: CameraFocus) {
        if (focus.campusId != _state.value.activeCampusId) {
            runCatching { engine.setActiveCampus(focus.campusId) }
        }
        val batch = runCatching {
            engine.getRenderBatch(focus.targetFloor, _state.value.timestampIso)
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

    private fun resolveOccupancyLocked(
        engine: CampusMapEngine,
        feature: FeatureInfo?,
        timestampIso: String?
    ): OccupancyStatus? {
        if (feature == null || feature.layer != MapLayer.ROOM) return null
        val ts = timestampIso ?: ZonedDateTime.now(SAO_PAULO_ZONE)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        return runCatching { engine.getRoomOccupancy(feature.id, ts) }.getOrNull()
    }

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
