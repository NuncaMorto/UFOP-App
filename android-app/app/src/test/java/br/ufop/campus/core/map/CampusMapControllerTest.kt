package br.ufop.campus.core.map

import br.ufop.campus.core.uniffi.CameraFocus
import br.ufop.campus.core.uniffi.CampusMapSummary
import br.ufop.campus.core.uniffi.FeatureInfo
import br.ufop.campus.core.uniffi.FloorLabel
import br.ufop.campus.core.uniffi.IndoorStatus
import br.ufop.campus.core.uniffi.MapHit
import br.ufop.campus.core.uniffi.MapLayer
import br.ufop.campus.core.uniffi.OccupancyStatus
import br.ufop.campus.core.uniffi.PoiType
import br.ufop.campus.core.uniffi.Rect2D
import br.ufop.campus.core.uniffi.RenderBatch
import br.ufop.campus.core.uniffi.RenderFeature
import br.ufop.campus.core.uniffi.RenderGeomKind
import br.ufop.campus.core.uniffi.RenderOccupancyKind
import br.ufop.campus.core.uniffi.Resolution
import br.ufop.campus.core.uniffi.RoomOccupancyItem
import br.ufop.campus.core.uniffi.SearchResult
import br.ufop.campus.core.uniffi.SlotInfo
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class CampusMapControllerTest {

    private class FakeCampusMapEngineGateway(
        initialCampusId: String = "OP_MC"
    ) : CampusMapEngineGateway {
        var currentCampusId: String = initialCampusId
        var closed: Boolean = false
        var renderBatchCalls: Int = 0
        var lastRequestedTimestamp: String? = null
        var occupancyCalls: Int = 0
        var buildingOccupancyCalls: Int = 0
        var lastBuildingOccupancyId: String? = null
        var lastBuildingOccupancyTimestamp: String? = null
        var searchCalls: MutableList<Triple<String, PoiType?, Int>> = mutableListOf()
        var setActiveCampusCalls: MutableList<String> = mutableListOf()
        var throwOnSetCampus: Throwable? = null
        var throwOnGetRenderBatch: Throwable? = null

        val mcBounds = Rect2D(-100f, -100f, 100f, 100f)
        val iceaBounds = Rect2D(-80f, -60f, 80f, 60f)
        val roomBounds = Rect2D(10f, 10f, 20f, 20f)
        val buildingBounds = Rect2D(0f, 0f, 50f, 50f)

        val mcFloors = listOf(FloorLabel(0, "Térreo"), FloorLabel(1, "2º Pav"))
        val iceaFloors = listOf(FloorLabel(0, "Térreo"), FloorLabel(2, "3º Pav"))
        val parentBuildingFloors = listOf(FloorLabel(0, "Térreo ICEB"), FloorLabel(1, "2º Pav ICEB"))

        val campusesList = listOf(
            CampusMapSummary(
                campusId = "OP_MC",
                name = "Morro do Cruzeiro",
                city = "Ouro Preto",
                defaultFloor = 0,
                availableFloors = listOf(0, 1),
                floorLabels = mcFloors,
                bounds = mcBounds,
                attribution = "UFOP"
            ),
            CampusMapSummary(
                campusId = "JM_ICEA",
                name = "ICEA",
                city = "João Monlevade",
                defaultFloor = 2,
                availableFloors = listOf(0, 2),
                floorLabels = iceaFloors,
                bounds = iceaBounds,
                attribution = "UFOP"
            )
        )

        val roomFeature = FeatureInfo(
            id = "room_iceb_h104",
            campusId = "OP_MC",
            layer = MapLayer.ROOM,
            code = "H104",
            name = "Sala H104",
            category = "Sala",
            buildingId = "bldg_iceb",
            parentId = "bldg_iceb",
            floor = 1,
            availableFloors = emptyList(),
            floorLabels = emptyList(),
            defaultFloor = null,
            poiType = null,
            indoorStatus = IndoorStatus.FLOORPLAN,
            isMock = false,
            aliases = listOf("H104"),
            departments = listOf("DECOM"),
            notes = null,
            minZoom = 0.5f,
            bounds = roomBounds,
            labelX = 15f,
            labelY = 15f
        )

        val buildingFeature = FeatureInfo(
            id = "bldg_iceb",
            campusId = "OP_MC",
            layer = MapLayer.BUILDING,
            code = "ICEB",
            name = "ICEB",
            category = "Prédio",
            buildingId = null,
            parentId = null,
            floor = null,
            availableFloors = listOf(0, 1),
            floorLabels = parentBuildingFloors,
            defaultFloor = 0,
            poiType = null,
            indoorStatus = IndoorStatus.FLOORPLAN,
            isMock = false,
            aliases = listOf("ICEB"),
            departments = listOf("DECOM"),
            notes = "Instituto de Ciências Exatas e Biológicas",
            minZoom = 0.2f,
            bounds = buildingBounds,
            labelX = 25f,
            labelY = 25f
        )

        val zoneFeature = FeatureInfo(
            id = "zone_icea",
            campusId = "JM_ICEA",
            layer = MapLayer.CAMPUS_ZONE,
            code = "ICEA",
            name = "Campus ICEA",
            category = "Zona",
            buildingId = null,
            parentId = null,
            floor = null,
            availableFloors = listOf(0, 2),
            floorLabels = iceaFloors,
            defaultFloor = 2,
            poiType = null,
            indoorStatus = IndoorStatus.PROVISIONAL,
            isMock = true,
            aliases = listOf("ICEA"),
            departments = listOf("DECSI"),
            notes = null,
            minZoom = 0.2f,
            bounds = iceaBounds,
            labelX = 0f,
            labelY = 0f
        )

        val toiletFloor1Feature = FeatureInfo(
            id = "poi_iceb_wc_1",
            campusId = "OP_MC",
            layer = MapLayer.POI,
            code = null,
            name = "Banheiro 2º Pav ICEB",
            category = "Banheiro",
            buildingId = "bldg_iceb",
            parentId = "bldg_iceb",
            floor = 1,
            availableFloors = emptyList(),
            floorLabels = emptyList(),
            defaultFloor = null,
            poiType = PoiType.TOILET,
            indoorStatus = IndoorStatus.FLOORPLAN,
            isMock = false,
            aliases = emptyList(),
            departments = emptyList(),
            notes = null,
            minZoom = 0.3f,
            bounds = roomBounds,
            labelX = 12f,
            labelY = 12f
        )

        val toiletIceaFeature = toiletFloor1Feature.copy(
            id = "poi_icea_wc_2",
            campusId = "JM_ICEA",
            name = "Banheiro ICEA",
            buildingId = "bldg_icea",
            parentId = "bldg_icea",
            floor = 2
        )

        val parentOnlyRoom = roomFeature.copy(
            id = "room_parent_only",
            buildingId = null,
            parentId = "bldg_iceb",
            floorLabels = emptyList()
        )

        val iceaRoomFeature = FeatureInfo(
            id = "room_icea_b202",
            campusId = "JM_ICEA",
            layer = MapLayer.ROOM,
            code = "B202",
            name = "Sala B202",
            category = "Sala",
            buildingId = "bldg_icea",
            parentId = "bldg_icea",
            floor = 2,
            availableFloors = listOf(0, 2),
            floorLabels = iceaFloors,
            defaultFloor = 2,
            poiType = null,
            indoorStatus = IndoorStatus.PROVISIONAL,
            isMock = true,
            aliases = emptyList(),
            departments = listOf("DECSI"),
            notes = null,
            minZoom = 0.5f,
            bounds = roomBounds,
            labelX = 15f,
            labelY = 15f
        )

        val featuresById = mutableMapOf(
            roomFeature.id to roomFeature,
            buildingFeature.id to buildingFeature,
            zoneFeature.id to zoneFeature,
            toiletFloor1Feature.id to toiletFloor1Feature,
            toiletIceaFeature.id to toiletIceaFeature,
            parentOnlyRoom.id to parentOnlyRoom,
            iceaRoomFeature.id to iceaRoomFeature
        )

        var renderItemsOverride: List<RenderFeature>? = null
        var occupancyStatusOverride: OccupancyStatus? = null
        var buildingOccupancyById: MutableMap<String, List<RoomOccupancyItem>> = mutableMapOf(
            buildingFeature.id to listOf(
                RoomOccupancyItem("room_iceb_h104", "H104", 1, false, OccupancyStatus.NoRegularClass),
                RoomOccupancyItem("room_iceb_h105", "H105", 1, false, OccupancyStatus.NoData)
            ),
            zoneFeature.id to listOf(
                RoomOccupancyItem("room_icea_b202", "B202", 2, true, OccupancyStatus.OutOfTerm)
            )
        )

        var nextHit: MapHit? = null
        var nextFocusByFeatureId: MutableMap<String, CameraFocus> = mutableMapOf(
            iceaRoomFeature.id to CameraFocus(
                campusId = "JM_ICEA",
                featureId = iceaRoomFeature.id,
                targetFloor = 2,
                bounds = iceaRoomFeature.bounds
            ),
            buildingFeature.id to CameraFocus(
                campusId = "OP_MC",
                featureId = buildingFeature.id,
                targetFloor = 0,
                bounds = buildingFeature.bounds
            ),
            zoneFeature.id to CameraFocus(
                campusId = "JM_ICEA",
                featureId = zoneFeature.id,
                targetFloor = 2,
                bounds = zoneFeature.bounds
            ),
            toiletFloor1Feature.id to CameraFocus(
                campusId = "OP_MC",
                featureId = toiletFloor1Feature.id,
                targetFloor = 1,
                bounds = toiletFloor1Feature.bounds
            )
        )
        var nextResolution: Resolution = Resolution.Unmapped

        override fun listCampuses(): List<CampusMapSummary> = campusesList

        override fun getActiveCampusId(): String = currentCampusId

        override fun setActiveCampus(campusId: String): CampusMapSummary {
            throwOnSetCampus?.let { throw it }
            setActiveCampusCalls.add(campusId)
            currentCampusId = campusId
            return campusesList.first { it.campusId == campusId }
        }

        override fun getRenderBatch(floor: Int, timestampIso: String?): RenderBatch {
            throwOnGetRenderBatch?.let { throw it }
            renderBatchCalls += 1
            lastRequestedTimestamp = timestampIso
            val summary = campusesList.first { it.campusId == currentCampusId }
            return RenderBatch(
                campusId = summary.campusId,
                campusName = summary.name,
                activeFloor = floor,
                availableFloors = summary.availableFloors,
                floorLabels = summary.floorLabels,
                bounds = summary.bounds,
                attribution = summary.attribution,
                coords = emptyList(),
                ringStarts = emptyList(),
                ringCounts = emptyList(),
                items = renderItemsOverride ?: emptyList()
            )
        }

        override fun hitTest(mapX: Float, mapY: Float, floor: Int, zoom: Float): MapHit? = nextHit

        override fun getCameraFocus(featureId: String): CameraFocus? = nextFocusByFeatureId[featureId]

        override fun getFeature(featureId: String): FeatureInfo? = featuresById[featureId]

        override fun search(query: String, poiTypeFilter: PoiType?, activeFloor: Int): List<SearchResult> {
            searchCalls.add(Triple(query, poiTypeFilter, activeFloor))
            val q = query.trim().lowercase()
            return featuresById.values
                .filter { it.campusId == currentCampusId }
                .filter { poiTypeFilter == null || it.poiType == poiTypeFilter }
                .filter {
                    q.isEmpty() ||
                        it.name.lowercase().contains(q) ||
                        it.code?.lowercase()?.contains(q) == true
                }
                .sortedByDescending { if (it.floor == activeFloor) 30 else 0 }
                .map {
                    SearchResult(
                        feature = it,
                        score = if (it.floor == activeFloor) 90u else 50u,
                        matchedField = if (q.isNotEmpty()) "name" else "poi_type"
                    )
                }
        }

        override fun getRoomOccupancy(roomId: String, timestampIso: String): OccupancyStatus {
            occupancyCalls += 1
            occupancyStatusOverride?.let { return it }
            return if (timestampIso.contains("10:00")) {
                OccupancyStatus.Occupied(
                    SlotInfo(
                        campusId = currentCampusId,
                        buildingId = "bldg_iceb",
                        roomId = roomId,
                        roomCode = "H104",
                        weekday = 1u,
                        startTime = "10:00",
                        endTime = "11:40",
                        kind = "T",
                        label = "BCC221 - Turma 11",
                        title = "Programação Orientada a Objetos",
                        courseCodes = listOf("BCC221")
                    )
                )
            } else {
                OccupancyStatus.NoRegularClass
            }
        }

        override fun listBuildingOccupancy(buildingId: String, timestampIso: String): List<RoomOccupancyItem> {
            buildingOccupancyCalls += 1
            lastBuildingOccupancyId = buildingId
            lastBuildingOccupancyTimestamp = timestampIso
            return buildingOccupancyById[buildingId].orEmpty()
        }

        override fun resolveMeeting(
            courseCode: String,
            section: String,
            weekday: UInt,
            startTime: String,
            fallbackBuildingRaw: String
        ): Resolution {
            assertTrue(weekday >= 1u)
            return nextResolution
        }

        override fun close() {
            closed = true
        }
    }

    private fun makeRenderRoom(
        id: String,
        label: String,
        occupancy: RenderOccupancyKind
    ): RenderFeature = RenderFeature(
        id = id,
        layer = MapLayer.ROOM,
        geomKind = RenderGeomKind.POLYGON,
        ringStartIdx = 0u,
        ringCount = 0u,
        pointX = 0f,
        pointY = 0f,
        labelX = 10f,
        labelY = 10f,
        bboxMinX = 0f,
        bboxMinY = 0f,
        bboxMaxX = 20f,
        bboxMaxY = 20f,
        minZoom = 0.5f,
        primaryLabel = label,
        secondaryLabel = "",
        category = "Sala",
        poiType = null,
        indoorStatus = IndoorStatus.FLOORPLAN,
        isMock = false,
        occupancy = occupancy,
        occupancyLabel = null
    )

    @Test
    fun unavailableNativeLibraryFallsBackCleanly() {
        val controller = CampusMapController(
            initialTimestampIso = "2026-09-01T10:00:00-03:00",
            engineFactory = { throw UnsatisfiedLinkError("libcampus_core.so not found") }
        )

        val state = controller.state.value
        assertFalse(state.isNativeAvailable)
        assertNull(state.renderBatch)
        assertEquals("libcampus_core.so not found", state.errorMessage)
        assertEquals("2026-09-01T10:00:00-03:00", state.timestampIso)

        // All interactions gracefully no-op when native engine is unavailable
        controller.selectCampus("JM_ICEA")
        controller.selectFloor(2)
        controller.updateTimestamp("2026-09-01T14:00:00-03:00")
        controller.updateSearchQuery("ICEB")
        controller.togglePoiFilter(PoiType.TOILET)
        controller.toggleFreeRoomsMode()
        assertNull(controller.handleTap(10f, 10f, 1.5f))
        controller.focusFeature("bldg_iceb")
        assertEquals(
            Resolution.Unmapped,
            controller.resolveMeeting("BCC221", "11", 0, "10:00", "ICEB")
        )
        assertTrue(controller.availableFloorsForUi().isEmpty())
        controller.close()
    }

    @Test
    fun initialLoadSelectCampusAndSelectFloorRespectIdempotency() {
        val fake = FakeCampusMapEngineGateway()
        val controller = CampusMapController(
            initialTimestampIso = "2026-09-01T10:00:00-03:00",
            engineFactory = { fake }
        )

        val initial = controller.state.value
        assertTrue(initial.isNativeAvailable)
        assertEquals("OP_MC", initial.activeCampusId)
        assertEquals(0, initial.activeFloor)
        assertEquals(2, initial.campuses.size)
        assertNotNull(initial.renderBatch)
        assertEquals(1, fake.renderBatchCalls)
        assertEquals("2026-09-01T10:00:00-03:00", fake.lastRequestedTimestamp)

        // Idempotent floor selection does not hit gateway again
        controller.selectFloor(0)
        assertEquals(1, fake.renderBatchCalls)

        // Switching to floor 1 fetches a fresh RenderBatch
        controller.selectFloor(1)
        assertEquals(1, controller.state.value.activeFloor)
        assertEquals(2, fake.renderBatchCalls)

        // Selecting a new campus resets selection, updates floor, and sets pendingFocus
        controller.selectCampus("JM_ICEA")
        val afterCampus = controller.state.value
        assertEquals("JM_ICEA", afterCampus.activeCampusId)
        assertEquals(2, afterCampus.activeFloor)
        assertNull(afterCampus.selectedFeature)
        assertNull(afterCampus.selectedOccupancy)
        assertTrue(afterCampus.selectedBuildingOccupancy.isEmpty())
        assertEquals("JM_ICEA", afterCampus.pendingFocus?.campusId)
        assertEquals(2, afterCampus.pendingFocus?.targetFloor)
        assertEquals(3, fake.renderBatchCalls)

        controller.consumePendingFocus()
        assertNull(controller.state.value.pendingFocus)
        controller.close()
    }

    @Test
    fun handleTapAndUpdateTimestampResolveAndRefreshRoomAndBuildingOccupancy() {
        val fake = FakeCampusMapEngineGateway()
        val controller = CampusMapController(
            initialTimestampIso = "2026-09-01T10:00:00-03:00",
            engineFactory = { fake }
        )

        // Tap on a ROOM fetches room occupancy and clears building occupancy
        fake.nextHit = MapHit(feature = fake.roomFeature, distanceM = 0f)
        val roomHit = controller.handleTap(15f, 15f, 2.0f)
        assertNotNull(roomHit)
        assertEquals(fake.roomFeature, controller.state.value.selectedFeature)
        assertTrue(controller.state.value.selectedOccupancy is OccupancyStatus.Occupied)
        assertTrue(controller.state.value.selectedBuildingOccupancy.isEmpty())
        assertEquals(1, fake.occupancyCalls)

        // Updating timestamp refreshes batch and selected room occupancy
        controller.updateTimestamp("2026-09-01T16:00:00-03:00")
        assertEquals("2026-09-01T16:00:00-03:00", controller.state.value.timestampIso)
        assertEquals(OccupancyStatus.NoRegularClass, controller.state.value.selectedOccupancy)
        assertEquals(2, fake.occupancyCalls)

        // Tap on a BUILDING does NOT fetch room occupancy, but loads building occupancy
        fake.nextHit = MapHit(feature = fake.buildingFeature, distanceM = 0f)
        controller.handleTap(25f, 25f, 1.5f)
        assertEquals(fake.buildingFeature, controller.state.value.selectedFeature)
        assertNull(controller.state.value.selectedOccupancy)
        assertEquals(2, fake.occupancyCalls)
        assertEquals(2, controller.state.value.selectedBuildingOccupancy.size)
        assertEquals("bldg_iceb", fake.lastBuildingOccupancyId)

        // Updating timestamp refreshes building occupancy as well
        controller.updateTimestamp("2026-09-01T18:00:00-03:00")
        assertEquals("2026-09-01T18:00:00-03:00", fake.lastBuildingOccupancyTimestamp)

        // Tap miss clears selection and building occupancy
        fake.nextHit = null
        assertNull(controller.handleTap(999f, 999f, 1.5f))
        assertNull(controller.state.value.selectedFeature)
        assertNull(controller.state.value.selectedOccupancy)
        assertTrue(controller.state.value.selectedBuildingOccupancy.isEmpty())
        controller.close()
    }

    @Test
    fun focusFeatureSwitchesCampusAndFloorAndResolvesOccupancyForRoomAndZone() {
        val fake = FakeCampusMapEngineGateway(initialCampusId = "OP_MC")
        val controller = CampusMapController(
            initialTimestampIso = "2026-09-01T10:00:00-03:00",
            engineFactory = { fake }
        )

        // Unknown feature is a no-op
        controller.focusFeature("nonexistent")
        assertEquals("OP_MC", controller.state.value.activeCampusId)
        assertNull(controller.state.value.selectedFeature)

        // Cross-campus focus on ROOM updates activeCampusId, floor, pendingFocus, selectedFeature & occupancy
        controller.focusFeature(fake.iceaRoomFeature.id)
        val afterFocus = controller.state.value
        assertEquals("JM_ICEA", afterFocus.activeCampusId)
        assertEquals(2, afterFocus.activeFloor)
        assertEquals(fake.iceaRoomFeature, afterFocus.selectedFeature)
        assertTrue(afterFocus.selectedOccupancy is OccupancyStatus.Occupied)
        assertTrue(afterFocus.selectedBuildingOccupancy.isEmpty())
        assertEquals(fake.iceaRoomFeature.id, afterFocus.pendingFocus?.featureId)
        assertTrue("JM_ICEA" in fake.setActiveCampusCalls)

        // Focus on CAMPUS_ZONE loads buildingOccupancy
        controller.focusFeature(fake.zoneFeature.id)
        val afterZoneFocus = controller.state.value
        assertEquals(fake.zoneFeature, afterZoneFocus.selectedFeature)
        assertNull(afterZoneFocus.selectedOccupancy)
        assertEquals(1, afterZoneFocus.selectedBuildingOccupancy.size)
        assertEquals(OccupancyStatus.OutOfTerm, afterZoneFocus.selectedBuildingOccupancy.first().status)
        controller.close()
    }

    @Test
    fun resolveMeetingCoversExactRoomBuildingFallbackAndUnmappedWithNotices() {
        val fake = FakeCampusMapEngineGateway()
        val controller = CampusMapController(
            initialTimestampIso = "2026-09-01T10:00:00-03:00",
            engineFactory = { fake }
        )

        val bldgFocus = CameraFocus(
            campusId = "OP_MC",
            featureId = fake.buildingFeature.id,
            targetFloor = 0,
            bounds = fake.buildingFeature.bounds
        )
        fake.nextResolution = Resolution.BuildingFallback(
            campusId = "OP_MC",
            buildingId = fake.buildingFeature.id,
            cameraFocus = bldgFocus
        )
        val fallbackRes = controller.resolveMeeting("BCC999", "21", 3, "14:00", "ICEB")
        assertTrue(fallbackRes is Resolution.BuildingFallback)
        assertEquals("OP_MC", controller.state.value.activeCampusId)
        assertEquals(0, controller.state.value.activeFloor)
        assertEquals(fake.buildingFeature, controller.state.value.selectedFeature)
        assertNull(controller.state.value.selectedOccupancy)
        assertEquals(2, controller.state.value.selectedBuildingOccupancy.size)
        assertEquals(
            "Sala específica sem mapa interno — exibindo o prédio.",
            controller.state.value.resolutionNotice
        )

        // ExactRoom clears resolutionNotice and building occupancy
        val exactFocus = CameraFocus(
            campusId = "JM_ICEA",
            featureId = fake.iceaRoomFeature.id,
            targetFloor = 2,
            bounds = fake.iceaRoomFeature.bounds
        )
        fake.nextResolution = Resolution.ExactRoom(
            campusId = "JM_ICEA",
            buildingId = "bldg_icea",
            roomId = fake.iceaRoomFeature.id,
            floor = 2,
            cameraFocus = exactFocus
        )
        val exactRes = controller.resolveMeeting("CEA001", "11", 0, "10:00", "ICEA")
        assertTrue(exactRes is Resolution.ExactRoom)
        assertEquals("JM_ICEA", controller.state.value.activeCampusId)
        assertEquals(2, controller.state.value.activeFloor)
        assertEquals(fake.iceaRoomFeature, controller.state.value.selectedFeature)
        assertTrue(controller.state.value.selectedOccupancy is OccupancyStatus.Occupied)
        assertTrue(controller.state.value.selectedBuildingOccupancy.isEmpty())
        assertNull(controller.state.value.resolutionNotice)
        assertEquals(exactFocus, controller.state.value.pendingFocus)

        // Unmapped preserves existing selection and sets unmapped notice
        fake.nextResolution = Resolution.Unmapped
        val unmapped = controller.resolveMeeting("UNKNOWN", "01", 2, "08:00", "")
        assertEquals(Resolution.Unmapped, unmapped)
        assertEquals(fake.iceaRoomFeature, controller.state.value.selectedFeature)
        assertEquals(
            "Local não mapeado neste campus. Use a busca para encontrar o prédio.",
            controller.state.value.resolutionNotice
        )

        controller.clearResolutionNotice()
        assertNull(controller.state.value.resolutionNotice)

        controller.clearSelection()
        assertNull(controller.state.value.selectedFeature)
        assertNull(controller.state.value.selectedOccupancy)
        assertTrue(controller.state.value.selectedBuildingOccupancy.isEmpty())
        controller.close()
    }

    @Test
    fun searchPoiFiltersAndSelectionRefreshAcrossFloorsAndCampuses() {
        val fake = FakeCampusMapEngineGateway()
        val controller = CampusMapController(
            initialTimestampIso = "2026-09-01T10:00:00-03:00",
            engineFactory = { fake }
        )

        // Blank search without POI filter does not hit engine.search
        controller.updateSearchQuery("   ")
        assertTrue(controller.state.value.searchResults.isEmpty())
        assertTrue(fake.searchCalls.isEmpty())

        // Text search hits engine and returns matching features
        controller.updateSearchQuery("ICEB")
        assertTrue(controller.state.value.searchResults.any { it.feature.id == fake.buildingFeature.id })

        // POI filter toggles on, respects active floor, and re-runs on floor/campus switch
        controller.clearSearchAndFilters()
        assertEquals("", controller.state.value.searchQuery)
        assertNull(controller.state.value.activePoiFilter)
        assertTrue(controller.state.value.searchResults.isEmpty())

        controller.togglePoiFilter(PoiType.TOILET)
        assertEquals(PoiType.TOILET, controller.state.value.activePoiFilter)
        assertEquals(listOf(fake.toiletFloor1Feature), controller.state.value.searchResults.map { it.feature })
        assertEquals(0, fake.searchCalls.last().third)

        // Floor change re-runs active POI search with new activeFloor
        controller.selectFloor(1)
        assertEquals(1, fake.searchCalls.last().third)
        assertEquals(listOf(fake.toiletFloor1Feature), controller.state.value.searchResults.map { it.feature })

        // Selecting search result focuses feature and clears query & results
        val targetResult = controller.state.value.searchResults.first()
        controller.selectSearchResult(targetResult)
        assertEquals("", controller.state.value.searchQuery)
        assertTrue(controller.state.value.searchResults.isEmpty())
        assertEquals(fake.toiletFloor1Feature, controller.state.value.selectedFeature)
        assertEquals(fake.toiletFloor1Feature.id, controller.state.value.pendingFocus?.featureId)

        // Campus change re-runs search because activePoiFilter is still active
        controller.selectCampus("JM_ICEA")
        assertEquals("JM_ICEA", controller.state.value.activeCampusId)
        assertEquals(listOf(fake.toiletIceaFeature), controller.state.value.searchResults.map { it.feature })

        // Toggling the same POI filter again clears it and results
        controller.togglePoiFilter(PoiType.TOILET)
        assertNull(controller.state.value.activePoiFilter)
        assertTrue(controller.state.value.searchResults.isEmpty())
        controller.close()
    }

    @Test
    fun freeRoomsModeUsesSaoPauloTimestampAndComputesHonestFloorSummary() {
        val fake = FakeCampusMapEngineGateway()
        fake.renderItemsOverride = listOf(
            makeRenderRoom("r1", "101", RenderOccupancyKind.NO_REGULAR_CLASS),
            makeRenderRoom("r2", "102", RenderOccupancyKind.NO_REGULAR_CLASS),
            makeRenderRoom("r3", "103", RenderOccupancyKind.OCCUPIED),
            makeRenderRoom("r4", "104", RenderOccupancyKind.NO_DATA),
            makeRenderRoom("r5", "105", RenderOccupancyKind.OUT_OF_TERM)
        )
        // Live mode: initialTimestampIso = null
        val controller = CampusMapController(
            initialTimestampIso = null,
            engineFactory = { fake }
        )

        // State preserves null while gateway always receives effective Sao Paulo ISO timestamp
        assertNull(controller.state.value.timestampIso)
        val initialTs = fake.lastRequestedTimestamp
        assertNotNull(initialTs)
        assertEquals("-03:00", OffsetDateTime.parse(initialTs).offset.id)

        val summary = controller.state.value.floorOccupancySummary
        assertEquals(2, summary.freeCount)
        assertEquals(1, summary.occupiedCount)
        assertEquals(1, summary.noDataCount)
        assertEquals(1, summary.outOfTermCount)
        assertEquals(listOf("r1", "r2"), controller.state.value.freeRoomsOnActiveFloor.map { it.id })

        // Toggling free rooms mode updates flag and refreshes live render batch with Sao Paulo ts
        val callsBefore = fake.renderBatchCalls
        controller.toggleFreeRoomsMode()
        assertTrue(controller.state.value.isFreeRoomsModeEnabled)
        assertEquals(callsBefore + 1, fake.renderBatchCalls)
        assertNull(controller.state.value.timestampIso)
        assertEquals("-03:00", OffsetDateTime.parse(fake.lastRequestedTimestamp).offset.id)

        // Verify all 4 OccupancyStatus variants are exposed cleanly on tap
        fake.nextHit = MapHit(feature = fake.roomFeature, distanceM = 0f)
        for (status in listOf(
            OccupancyStatus.NoRegularClass,
            OccupancyStatus.Occupied(
                SlotInfo("OP_MC", "bldg_iceb", "room_iceb_h104", "H104", 1u, "10:00", "11:40", "T", "BCC221", null, listOf("BCC221"))
            ),
            OccupancyStatus.NoData,
            OccupancyStatus.OutOfTerm
        )) {
            fake.occupancyStatusOverride = status
            controller.handleTap(15f, 15f, 2.0f)
            assertEquals(status, controller.state.value.selectedOccupancy)
            assertEquals("-03:00", OffsetDateTime.parse(fake.lastRequestedTimestamp).offset.id)
        }

        controller.setFreeRoomsMode(false)
        assertFalse(controller.state.value.isFreeRoomsModeEnabled)
        controller.close()
    }

    @Test
    fun availableFloorsForUiFollowsPrecedenceAndCloseStopsEngine() {
        val fake = FakeCampusMapEngineGateway()
        val controller = CampusMapController(engineFactory = { fake })

        // 1) No selection -> renderBatch floorLabels
        assertEquals(fake.mcFloors, controller.availableFloorsForUi())

        // 2) Selected feature with empty floorLabels + buildingId -> parent building floorLabels
        fake.nextHit = MapHit(feature = fake.roomFeature, distanceM = 0f)
        controller.handleTap(15f, 15f, 2.0f)
        assertEquals(fake.parentBuildingFloors, controller.availableFloorsForUi())

        // 3) Selected feature with empty floorLabels + null buildingId + non-null parentId -> parentId floorLabels
        fake.nextHit = MapHit(feature = fake.parentOnlyRoom, distanceM = 0f)
        controller.handleTap(15f, 15f, 2.0f)
        assertEquals(fake.parentBuildingFloors, controller.availableFloorsForUi())

        // 4) Selected feature with own non-empty floorLabels -> feature's own floorLabels
        fake.nextHit = MapHit(feature = fake.iceaRoomFeature, distanceM = 0f)
        controller.handleTap(15f, 15f, 2.0f)
        assertEquals(fake.iceaFloors, controller.availableFloorsForUi())

        // Error branches propagate errorMessage cleanly without crashing
        fake.throwOnGetRenderBatch = IllegalStateException("batch error")
        controller.selectFloor(1)
        assertEquals("batch error", controller.state.value.errorMessage)
        fake.throwOnGetRenderBatch = null

        fake.throwOnSetCampus = IllegalArgumentException("campus error")
        controller.selectCampus("INVALID")
        assertEquals("campus error", controller.state.value.errorMessage)
        fake.throwOnSetCampus = null

        // Close marks fake closed and prevents further gateway calls
        controller.close()
        assertTrue(fake.closed)
        val callsBeforeAfterClose = fake.renderBatchCalls
        controller.selectFloor(1)
        controller.close()
        assertEquals(callsBeforeAfterClose, fake.renderBatchCalls)
    }

    @Test
    fun geometryRevisionAndCanvasSliceStayIsolatedAcrossOccupancySearchAndFilters() {
        val fake = FakeCampusMapEngineGateway()
        fake.renderItemsOverride = listOf(
            makeRenderRoom("r1", "101", RenderOccupancyKind.NO_REGULAR_CLASS)
        )
        val controller = CampusMapController(
            initialTimestampIso = "2026-09-01T10:00:00-03:00",
            engineFactory = { fake }
        )

        val initialCanvas = controller.canvasState.value
        val initialRev = initialCanvas.geometryRevision

        // Digitar busca altera apenas topHudState, mantendo canvasState intacto
        controller.updateSearchQuery("ICEB")
        assertEquals(initialCanvas, controller.canvasState.value)
        assertTrue(controller.topHudState.value.searchResults.isNotEmpty())

        // Atualizar ocupação/timestamp no mesmo andar preserva geometryRevision sem reconstruir paths
        fake.renderItemsOverride = listOf(
            makeRenderRoom("r1", "101", RenderOccupancyKind.OCCUPIED)
        )
        controller.updateTimestamp("2026-09-01T14:00:00-03:00")
        assertEquals(initialRev, controller.canvasState.value.geometryRevision)

        // Troca de andar e troca de campus incrementam geometryRevision
        controller.selectFloor(1)
        val revFloor1 = controller.canvasState.value.geometryRevision
        assertTrue(revFloor1 > initialRev)

        controller.selectCampus("JM_ICEA")
        val revIcea = controller.canvasState.value.geometryRevision
        assertTrue(revIcea > revFloor1)
        controller.close()
    }

    @Test
    fun asyncModeDebouncesRapidSearchCancelsStaleWorkAndStopsOnTabExit() {
        val fake = FakeCampusMapEngineGateway()
        val controller = CampusMapController(
            initialTimestampIso = "2026-09-01T10:00:00-03:00",
            engineFactory = { fake },
            workDispatcher = Dispatchers.Default,
            searchDebounceMs = 80L
        )

        waitUntil(timeoutMs = 3000L) {
            !controller.state.value.isLoading && controller.state.value.isNativeAvailable
        }

        controller.updateSearchQuery("I")
        controller.updateSearchQuery("IC")
        controller.updateSearchQuery("ICE")
        controller.updateSearchQuery("ICEB")
        assertEquals("ICEB", controller.topHudState.value.searchQuery)

        waitUntil(timeoutMs = 3000L) {
            controller.topHudState.value.searchResults.isNotEmpty()
        }
        assertEquals(1, fake.searchCalls.size)
        assertEquals("ICEB", fake.searchCalls.single().first)

        // Sair da aba MAP cancela debounce pendente antes de chamar FFI
        controller.updateSearchQuery("Sala")
        controller.onMapTabActiveChanged(false)
        Thread.sleep(160L)
        assertEquals(1, fake.searchCalls.size)

        controller.close()
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() >= deadline) {
                throw AssertionError("Timed out waiting for async controller condition")
            }
            Thread.sleep(10L)
        }
    }
}
