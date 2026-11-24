import Combine
import Foundation

public struct CampusMapUiState: Equatable, Sendable {
    public let campuses: [CampusMapSummaryModel]
    public let activeCampusId: String
    public let activeFloor: Int32
    public let timestampIso: String?
    public let renderBatch: CampusRenderBatch?
    public let selectedFeature: CampusFeatureInfo?
    public let selectedOccupancy: CampusOccupancyStatus?
    public let selectedBuildingOccupancy: [CampusRoomOccupancyItem]
    public let pendingFocus: CampusCameraFocus?
    public let searchQuery: String
    public let activePoiFilter: CampusPoiType?
    public let searchResults: [CampusSearchResult]
    public let isFreeRoomsModeEnabled: Bool
    public let resolutionNotice: String?
    public let freeRoomsOnActiveFloor: [CampusRenderFeature]
    public let floorOccupancySummary: CampusFloorOccupancySummary
    public let isNativeAvailable: Bool
    public let errorMessage: String?

    public init(
        campuses: [CampusMapSummaryModel] = [],
        activeCampusId: String = "OP_MC",
        activeFloor: Int32 = 0,
        timestampIso: String? = nil,
        renderBatch: CampusRenderBatch? = nil,
        selectedFeature: CampusFeatureInfo? = nil,
        selectedOccupancy: CampusOccupancyStatus? = nil,
        selectedBuildingOccupancy: [CampusRoomOccupancyItem] = [],
        pendingFocus: CampusCameraFocus? = nil,
        searchQuery: String = "",
        activePoiFilter: CampusPoiType? = nil,
        searchResults: [CampusSearchResult] = [],
        isFreeRoomsModeEnabled: Bool = false,
        resolutionNotice: String? = nil,
        freeRoomsOnActiveFloor: [CampusRenderFeature] = [],
        floorOccupancySummary: CampusFloorOccupancySummary = CampusFloorOccupancySummary(),
        isNativeAvailable: Bool = false,
        errorMessage: String? = nil
    ) {
        self.campuses = campuses
        self.activeCampusId = activeCampusId
        self.activeFloor = activeFloor
        self.timestampIso = timestampIso
        self.renderBatch = renderBatch
        self.selectedFeature = selectedFeature
        self.selectedOccupancy = selectedOccupancy
        self.selectedBuildingOccupancy = selectedBuildingOccupancy
        self.pendingFocus = pendingFocus
        self.searchQuery = searchQuery
        self.activePoiFilter = activePoiFilter
        self.searchResults = searchResults
        self.isFreeRoomsModeEnabled = isFreeRoomsModeEnabled
        self.resolutionNotice = resolutionNotice
        self.freeRoomsOnActiveFloor = freeRoomsOnActiveFloor
        self.floorOccupancySummary = floorOccupancySummary
        self.isNativeAvailable = isNativeAvailable
        self.errorMessage = errorMessage
    }
}

/// Controlador stateful do motor vetorial offline 2D (`CampusMapBridge`),
/// espelhando o `CampusMapController` do Android com `ObservableObject` para telas SwiftUI.
@MainActor
public final class CampusMapController: ObservableObject {
    public let bridge: CampusMapBridge
    @Published public private(set) var state: CampusMapUiState

    public init(initialTimestampIso: String? = nil) {
        let mapBridge = CampusMapBridge(timestampIso: initialTimestampIso)
        self.bridge = mapBridge
        self.state = Self.snapshot(from: mapBridge)
    }

    public init(bridge: CampusMapBridge) {
        self.bridge = bridge
        self.state = Self.snapshot(from: bridge)
    }

    public func syncState() {
        state = Self.snapshot(from: bridge)
    }

    public func effectiveTimestampIso() -> String {
        bridge.effectiveTimestampIso()
    }

    public func loadBundledEngine() {
        bridge.loadBundledEngine()
        syncState()
    }

    public func selectCampus(_ campusId: String) {
        bridge.selectCampus(campusId)
        syncState()
    }

    public func selectFloor(_ floor: Int32) {
        bridge.selectFloor(floor)
        syncState()
    }

    public func updateTimestamp(_ timestampIso: String?) {
        bridge.updateTimestamp(timestampIso)
        syncState()
    }

    public func refreshRenderBatch() {
        bridge.refreshRenderBatch()
        syncState()
    }

    @discardableResult
    public func handleTap(mapX: Float, mapY: Float, zoom: Float) -> CampusMapHit? {
        let hit = bridge.handleTap(mapX: mapX, mapY: mapY, zoom: zoom)
        syncState()
        return hit
    }

    public func focusFeature(_ featureId: String) {
        bridge.focusFeature(featureId)
        syncState()
    }

    public func updateSearchQuery(_ query: String) {
        bridge.updateSearchQuery(query)
        syncState()
    }

    public func togglePoiFilter(_ poiType: CampusPoiType) {
        bridge.togglePoiFilter(poiType)
        syncState()
    }

    public func clearSearchAndFilters() {
        bridge.clearSearchAndFilters()
        syncState()
    }

    public func selectSearchResult(_ result: CampusSearchResult) {
        bridge.selectSearchResult(result)
        syncState()
    }

    public func toggleFreeRoomsMode() {
        bridge.toggleFreeRoomsMode()
        syncState()
    }

    public func setFreeRoomsMode(_ enabled: Bool) {
        bridge.setFreeRoomsMode(enabled)
        syncState()
    }

    public func clearResolutionNotice() {
        bridge.clearResolutionNotice()
        syncState()
    }

    @discardableResult
    public func resolveMeeting(
        courseCode: String,
        section: String,
        weekday: UInt32,
        startTime: String,
        fallbackBuildingRaw: String
    ) -> CampusMeetingResolution {
        let resolution = bridge.resolveMeeting(
            courseCode: courseCode,
            section: section,
            weekday: weekday,
            startTime: startTime,
            fallbackBuildingRaw: fallbackBuildingRaw
        )
        syncState()
        return resolution
    }

    public func consumePendingFocus() {
        bridge.consumePendingFocus()
        syncState()
    }

    public func clearSelection() {
        bridge.clearSelection()
        syncState()
    }

    public func availableFloorsForUi() -> [CampusFloorLabel] {
        bridge.availableFloorsForUi()
    }

    private static func snapshot(from bridge: CampusMapBridge) -> CampusMapUiState {
        CampusMapUiState(
            campuses: bridge.campuses,
            activeCampusId: bridge.activeCampusId,
            activeFloor: bridge.activeFloor,
            timestampIso: bridge.timestampIso,
            renderBatch: bridge.renderBatch,
            selectedFeature: bridge.selectedFeature,
            selectedOccupancy: bridge.selectedOccupancy,
            selectedBuildingOccupancy: bridge.selectedBuildingOccupancy,
            pendingFocus: bridge.pendingFocus,
            searchQuery: bridge.searchQuery,
            activePoiFilter: bridge.activePoiFilter,
            searchResults: bridge.searchResults,
            isFreeRoomsModeEnabled: bridge.isFreeRoomsModeEnabled,
            resolutionNotice: bridge.resolutionNotice,
            freeRoomsOnActiveFloor: bridge.freeRoomsOnActiveFloor,
            floorOccupancySummary: bridge.floorOccupancySummary,
            isNativeAvailable: bridge.isNativeAvailable,
            errorMessage: bridge.errorMessage
        )
    }
}
