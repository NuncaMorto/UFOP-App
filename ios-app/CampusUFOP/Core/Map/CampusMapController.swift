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
    public let pendingFocus: CampusCameraFocus?
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
        pendingFocus: CampusCameraFocus? = nil,
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
        self.pendingFocus = pendingFocus
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
            pendingFocus: bridge.pendingFocus,
            isNativeAvailable: bridge.isNativeAvailable,
            errorMessage: bridge.errorMessage
        )
    }
}
