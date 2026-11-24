package br.ufop.campus.core.map

import br.ufop.campus.core.uniffi.CameraFocus
import br.ufop.campus.core.uniffi.CampusMapSummary
import br.ufop.campus.core.uniffi.FeatureInfo
import br.ufop.campus.core.uniffi.FloorLabel
import br.ufop.campus.core.uniffi.IndoorStatus
import br.ufop.campus.core.uniffi.MapHit
import br.ufop.campus.core.uniffi.MapLayer
import br.ufop.campus.core.uniffi.OccupancyStatus
import br.ufop.campus.core.uniffi.Rect2D
import br.ufop.campus.core.uniffi.RenderBatch
import br.ufop.campus.core.uniffi.Resolution
import br.ufop.campus.core.uniffi.SlotInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusMapControllerTest {

    private class FakeCampusMapEngineGateway(
        initialCampusId: String = "OP_MC"
    ) : CampusMapEngineGateway {
        var currentCampusId: String = initialCampusId
        var closed: Boolean = false
        var renderBatchCalls: Int = 0
        var lastRequestedTimestamp: String? = null
        var occupancyCalls: Int = 0
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
            parentOnlyRoom.id to parentOnlyRoom,
            iceaRoomFeature.id to iceaRoomFeature
        )

        var nextHit: MapHit? = null
        var nextFocusByFeatureId: MutableMap<String, CameraFocus> = mutableMapOf(
            iceaRoomFeature.id to CameraFocus(
                campusId = "JM_ICEA",
                featureId = iceaRoomFeature.id,
                targetFloor = 2,
                bounds = iceaRoomFeature.bounds
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
                items = emptyList()
            )
        }

        override fun hitTest(mapX: Float, mapY: Float, floor: Int, zoom: Float): MapHit? = nextHit

        override fun getCameraFocus(featureId: String): CameraFocus? = nextFocusByFeatureId[featureId]

        override fun getFeature(featureId: String): FeatureInfo? = featuresById[featureId]

        override fun getRoomOccupancy(roomId: String, timestampIso: String): OccupancyStatus {
            occupancyCalls += 1
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
        assertEquals("JM_ICEA", afterCampus.pendingFocus?.campusId)
        assertEquals(2, afterCampus.pendingFocus?.targetFloor)
        assertEquals(3, fake.renderBatchCalls)

        controller.consumePendingFocus()
        assertNull(controller.state.value.pendingFocus)
        controller.close()
    }

    @Test
    fun handleTapAndUpdateTimestampResolveAndRefreshRoomOccupancy() {
        val fake = FakeCampusMapEngineGateway()
        val controller = CampusMapController(
            initialTimestampIso = "2026-09-01T10:00:00-03:00",
            engineFactory = { fake }
        )

        // Tap on a ROOM fetches occupancy
        fake.nextHit = MapHit(feature = fake.roomFeature, distanceM = 0f)
        val roomHit = controller.handleTap(15f, 15f, 2.0f)
        assertNotNull(roomHit)
        assertEquals(fake.roomFeature, controller.state.value.selectedFeature)
        assertTrue(controller.state.value.selectedOccupancy is OccupancyStatus.Occupied)
        assertEquals(1, fake.occupancyCalls)

        // Updating timestamp refreshes batch and selected room occupancy
        controller.updateTimestamp("2026-09-01T16:00:00-03:00")
        assertEquals("2026-09-01T16:00:00-03:00", controller.state.value.timestampIso)
        assertEquals(OccupancyStatus.NoRegularClass, controller.state.value.selectedOccupancy)
        assertEquals(2, fake.occupancyCalls)

        // Tap on a BUILDING does NOT fetch room occupancy
        fake.nextHit = MapHit(feature = fake.buildingFeature, distanceM = 0f)
        controller.handleTap(25f, 25f, 1.5f)
        assertEquals(fake.buildingFeature, controller.state.value.selectedFeature)
        assertNull(controller.state.value.selectedOccupancy)
        assertEquals(2, fake.occupancyCalls)

        // Tap miss clears selection
        fake.nextHit = null
        assertNull(controller.handleTap(999f, 999f, 1.5f))
        assertNull(controller.state.value.selectedFeature)
        assertNull(controller.state.value.selectedOccupancy)
        controller.close()
    }

    @Test
    fun focusFeatureSwitchesCampusAndFloorAndResolvesOccupancy() {
        val fake = FakeCampusMapEngineGateway(initialCampusId = "OP_MC")
        val controller = CampusMapController(
            initialTimestampIso = "2026-09-01T10:00:00-03:00",
            engineFactory = { fake }
        )

        // Unknown feature is a no-op
        controller.focusFeature("nonexistent")
        assertEquals("OP_MC", controller.state.value.activeCampusId)
        assertNull(controller.state.value.selectedFeature)

        // Cross-campus focus updates activeCampusId, floor, pendingFocus, selectedFeature & occupancy
        controller.focusFeature(fake.iceaRoomFeature.id)
        val afterFocus = controller.state.value
        assertEquals("JM_ICEA", afterFocus.activeCampusId)
        assertEquals(2, afterFocus.activeFloor)
        assertEquals(fake.iceaRoomFeature, afterFocus.selectedFeature)
        assertTrue(afterFocus.selectedOccupancy is OccupancyStatus.Occupied)
        assertEquals(fake.iceaRoomFeature.id, afterFocus.pendingFocus?.featureId)
        assertTrue("JM_ICEA" in fake.setActiveCampusCalls)
        controller.close()
    }

    @Test
    fun resolveMeetingCoversExactRoomBuildingFallbackAndUnmapped() {
        val fake = FakeCampusMapEngineGateway()
        val controller = CampusMapController(
            initialTimestampIso = "2026-09-01T10:00:00-03:00",
            engineFactory = { fake }
        )

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
        assertEquals(exactFocus, controller.state.value.pendingFocus)

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

        fake.nextResolution = Resolution.Unmapped
        val unmapped = controller.resolveMeeting("UNKNOWN", "01", 2, "08:00", "")
        assertEquals(Resolution.Unmapped, unmapped)
        assertEquals(fake.buildingFeature, controller.state.value.selectedFeature)

        controller.clearSelection()
        assertNull(controller.state.value.selectedFeature)
        assertNull(controller.state.value.selectedOccupancy)
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
}
