import Foundation
import Observation

#if canImport(CampusCoreFFI)
import CampusCoreFFI
#endif

// MARK: - Bridge @Observable sem chamadas FFI por frame

@Observable
public final class CampusMapBridge {
    public private(set) var campuses: [CampusMapSummaryModel] = []
    public private(set) var activeCampusId: String = "OP_MC"
    public private(set) var activeFloor: Int32 = 0
    public private(set) var timestampIso: String? = nil
    public private(set) var renderBatch: CampusRenderBatch? = nil
    public private(set) var selectedFeature: CampusFeatureInfo? = nil
    public private(set) var selectedOccupancy: CampusOccupancyStatus? = nil
    public private(set) var selectedBuildingOccupancy: [CampusRoomOccupancyItem] = []
    public private(set) var pendingFocus: CampusCameraFocus? = nil
    public private(set) var searchQuery: String = ""
    public private(set) var activePoiFilter: CampusPoiType? = nil
    public private(set) var searchResults: [CampusSearchResult] = []
    public private(set) var isFreeRoomsModeEnabled: Bool = false
    public private(set) var resolutionNotice: String? = nil
    public private(set) var isNativeAvailable: Bool = false
    public private(set) var renderBatchRevision: Int = 0
    public private(set) var errorMessage: String? = nil

    private let adapter = CampusMapNativeAdapter()

    public init(timestampIso: String? = nil) {
        self.timestampIso = timestampIso
        loadBundledEngine()
    }

    public var freeRoomsOnActiveFloor: [CampusRenderFeature] {
        guard let batch = renderBatch else { return [] }
        return batch.items
            .filter { $0.layer == .room && $0.occupancy == .noRegularClass }
            .sorted { lhs, rhs in
                lhs.primaryLabel.localizedCaseInsensitiveCompare(rhs.primaryLabel) == .orderedAscending
            }
    }

    public var floorOccupancySummary: CampusFloorOccupancySummary {
        guard let batch = renderBatch else {
            return CampusFloorOccupancySummary()
        }
        var freeCount = 0
        var occupiedCount = 0
        var noDataCount = 0
        var outOfTermCount = 0

        for item in batch.items where item.layer == .room {
            switch item.occupancy {
            case .noRegularClass:
                freeCount += 1
            case .occupied:
                occupiedCount += 1
            case .noData:
                noDataCount += 1
            case .outOfTerm:
                outOfTermCount += 1
            case .none:
                break
            }
        }
        return CampusFloorOccupancySummary(
            freeCount: freeCount,
            occupiedCount: occupiedCount,
            noDataCount: noDataCount,
            outOfTermCount: outOfTermCount
        )
    }

    public func effectiveTimestampIso() -> String {
        timestampIso ?? Self.currentIsoTimestamp()
    }

    public func loadBundledEngine() {
        do {
            guard let snapshot = try adapter.loadBundled() else {
                isNativeAvailable = false
                campuses = []
                renderBatch = nil
                errorMessage = nil
                return
            }
            isNativeAvailable = true
            campuses = snapshot.campuses
            activeCampusId = snapshot.activeCampusId
            activeFloor = snapshot.defaultFloor
            errorMessage = nil
            refreshRenderBatch()
        } catch {
            isNativeAvailable = false
            errorMessage = "Falha ao inicializar motor vetorial: \(error.localizedDescription)"
        }
    }

    public func selectCampus(_ campusId: String) {
        do {
            guard let summary = try adapter.setActiveCampus(campusId) else { return }
            activeCampusId = summary.campusId
            activeFloor = summary.defaultFloor
            selectedFeature = nil
            selectedOccupancy = nil
            selectedBuildingOccupancy = []
            resolutionNotice = nil
            refreshRenderBatch()
            refreshSearchResults()
            pendingFocus = CampusCameraFocus(
                campusId: summary.campusId,
                featureId: summary.campusId,
                targetFloor: summary.defaultFloor,
                bounds: summary.bounds
            )
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    public func selectFloor(_ floor: Int32) {
        guard floor != activeFloor else { return }
        activeFloor = floor
        refreshRenderBatch()
        refreshSearchResults()
    }

    public func updateTimestamp(_ newTimestampIso: String?) {
        timestampIso = newTimestampIso
        refreshRenderBatch()
        refreshSelectedOccupancy()
    }

    public func refreshRenderBatch() {
        do {
            guard let batch = try adapter.getRenderBatch(
                activeFloor: activeFloor,
                timestampIso: effectiveTimestampIso()
            ) else {
                return
            }
            renderBatch = batch
            renderBatchRevision &+= 1
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    public func handleTap(mapX: Float, mapY: Float, zoom: Float) -> CampusMapHit? {
        resolutionNotice = nil
        guard let hit = adapter.hitTest(
            mapX: mapX,
            mapY: mapY,
            activeFloor: activeFloor,
            zoom: zoom
        ) else {
            selectedFeature = nil
            selectedOccupancy = nil
            selectedBuildingOccupancy = []
            return nil
        }
        selectedFeature = hit.feature
        refreshSelectedOccupancy()
        return hit
    }

    public func focusFeature(_ featureId: String) {
        guard let focus = adapter.getCameraFocus(featureId: featureId) else { return }
        resolutionNotice = nil
        applyCameraFocus(focus)
        selectedFeature = adapter.getFeature(featureId: focus.featureId)
        refreshSelectedOccupancy()
    }

    public func updateSearchQuery(_ query: String) {
        searchQuery = query
        refreshSearchResults()
    }

    public func togglePoiFilter(_ poiType: CampusPoiType) {
        if activePoiFilter == poiType {
            activePoiFilter = nil
        } else {
            activePoiFilter = poiType
        }
        refreshSearchResults()
    }

    public func clearSearchAndFilters() {
        searchQuery = ""
        activePoiFilter = nil
        searchResults = []
    }

    public func selectSearchResult(_ result: CampusSearchResult) {
        resolutionNotice = nil
        let focus = adapter.getCameraFocus(featureId: result.feature.id)
            ?? CampusCameraFocus(
                campusId: result.feature.campusId,
                featureId: result.feature.id,
                targetFloor: result.feature.floor ?? result.feature.defaultFloor ?? activeFloor,
                bounds: result.feature.bounds
            )
        applyCameraFocus(focus)
        selectedFeature = adapter.getFeature(featureId: focus.featureId) ?? result.feature
        refreshSelectedOccupancy()
    }

    public func toggleFreeRoomsMode() {
        setFreeRoomsMode(!isFreeRoomsModeEnabled)
    }

    public func setFreeRoomsMode(_ enabled: Bool) {
        isFreeRoomsModeEnabled = enabled
        if enabled && timestampIso == nil {
            refreshRenderBatch()
        }
    }

    public func clearResolutionNotice() {
        resolutionNotice = nil
    }

    @discardableResult
    public func resolveMeeting(
        courseCode: String,
        section: String,
        weekday: UInt32,
        startTime: String,
        fallbackBuildingRaw: String
    ) -> CampusMeetingResolution {
        let res = adapter.resolveMeeting(
            courseCode: courseCode,
            section: section,
            weekday: weekday,
            startTime: startTime,
            fallbackBuildingRaw: fallbackBuildingRaw
        )
        switch res {
        case .exactRoom(_, _, let roomId, _, let focus):
            resolutionNotice = nil
            applyCameraFocus(focus)
            selectedFeature = adapter.getFeature(featureId: roomId)
            refreshSelectedOccupancy()
        case .buildingFallback(_, let buildingId, let focus):
            resolutionNotice = "Sala específica sem mapa interno — exibindo o prédio."
            applyCameraFocus(focus)
            selectedFeature = adapter.getFeature(featureId: buildingId)
            refreshSelectedOccupancy()
        case .unmapped:
            resolutionNotice = "Local não mapeado neste campus. Use a busca para encontrar o prédio."
        }
        return res
    }

    public func consumePendingFocus() {
        pendingFocus = nil
    }

    public func clearSelection() {
        selectedFeature = nil
        selectedOccupancy = nil
        selectedBuildingOccupancy = []
    }

    public func availableFloorsForUi() -> [CampusFloorLabel] {
        if let feat = selectedFeature, !feat.floorLabels.isEmpty {
            return feat.floorLabels
        }
        if let feat = selectedFeature,
           let parentId = feat.buildingId ?? feat.parentId,
           let parentFeat = featureInfo(parentId),
           !parentFeat.floorLabels.isEmpty {
            return parentFeat.floorLabels
        }
        return renderBatch?.floorLabels ?? []
    }

    private func featureInfo(_ featureId: String) -> CampusFeatureInfo? {
        adapter.getFeature(featureId: featureId)
    }

    private func applyCameraFocus(_ focus: CampusCameraFocus) {
        let campusChanged = focus.campusId != activeCampusId
        let floorChanged = focus.targetFloor != activeFloor
        if campusChanged {
            _ = try? adapter.setActiveCampus(focus.campusId)
            activeCampusId = focus.campusId
        }
        activeFloor = focus.targetFloor
        refreshRenderBatch()
        if campusChanged || floorChanged {
            refreshSearchResults()
        }
        pendingFocus = focus
    }

    private func refreshSearchResults() {
        let trimmed = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty || activePoiFilter != nil else {
            searchResults = []
            return
        }
        searchResults = adapter.search(
            query: trimmed,
            poiTypeFilter: activePoiFilter,
            activeFloor: activeFloor
        )
    }

    private func refreshSelectedOccupancy() {
        guard let feat = selectedFeature else {
            selectedOccupancy = nil
            selectedBuildingOccupancy = []
            return
        }
        let ts = effectiveTimestampIso()
        if feat.layer == .room {
            selectedOccupancy = adapter.getRoomOccupancy(roomId: feat.id, timestampIso: ts)
            selectedBuildingOccupancy = []
        } else if feat.layer == .building || feat.layer == .campusZone {
            selectedOccupancy = nil
            selectedBuildingOccupancy = (try? adapter.listBuildingOccupancy(buildingId: feat.id, timestampIso: ts)) ?? []
        } else {
            selectedOccupancy = nil
            selectedBuildingOccupancy = []
        }
    }

    private static func currentIsoTimestamp() -> String {
        let formatter = ISO8601DateFormatter()
        formatter.timeZone = TimeZone(identifier: "America/Sao_Paulo")
        return formatter.string(from: Date())
    }
}

// MARK: - Adaptador FFI Nativo / Fallback Stub

private struct CampusMapBundledSnapshot {
    let campuses: [CampusMapSummaryModel]
    let activeCampusId: String
    let defaultFloor: Int32
}

private final class CampusMapNativeAdapter {
    #if canImport(CampusCoreFFI)
    private var nativeEngine: CampusMapEngine? = nil

    func loadBundled() throws -> CampusMapBundledSnapshot? {
        let engine = try CampusMapEngine.newBundled()
        nativeEngine = engine
        let loadedCampuses = engine.listCampuses().map { $0.toBridgeModel() }
        let activeId = engine.getActiveCampusId()
        let defaultFloor = loadedCampuses.first { $0.campusId == activeId }?.defaultFloor ?? 0
        return CampusMapBundledSnapshot(
            campuses: loadedCampuses,
            activeCampusId: activeId,
            defaultFloor: defaultFloor
        )
    }

    func setActiveCampus(_ campusId: String) throws -> CampusMapSummaryModel? {
        guard let engine = nativeEngine else { return nil }
        return try engine.setActiveCampus(campusId: campusId).toBridgeModel()
    }

    func getRenderBatch(activeFloor: Int32, timestampIso: String?) throws -> CampusRenderBatch? {
        guard let engine = nativeEngine else { return nil }
        return try engine.getRenderBatch(
            activeFloor: activeFloor,
            timestampOpt: timestampIso
        ).toBridgeModel()
    }

    func hitTest(mapX: Float, mapY: Float, activeFloor: Int32, zoom: Float) -> CampusMapHit? {
        guard let engine = nativeEngine else { return nil }
        return engine.hitTest(
            mapX: mapX,
            mapY: mapY,
            activeFloor: activeFloor,
            zoom: zoom
        )?.toBridgeModel()
    }

    func getCameraFocus(featureId: String) -> CampusCameraFocus? {
        nativeEngine?.getCameraFocus(featureId: featureId)?.toBridgeModel()
    }

    func getFeature(featureId: String) -> CampusFeatureInfo? {
        nativeEngine?.getFeature(featureId: featureId)?.toBridgeModel()
    }

    func search(query: String, poiTypeFilter: CampusPoiType?, activeFloor: Int32) -> [CampusSearchResult] {
        guard let engine = nativeEngine else { return [] }
        return engine.search(
            query: query,
            poiTypeFilter: poiTypeFilter?.toFfi(),
            activeFloor: activeFloor
        ).map { $0.toBridgeModel() }
    }

    func resolveMeeting(
        courseCode: String,
        section: String,
        weekday: UInt32,
        startTime: String,
        fallbackBuildingRaw: String
    ) -> CampusMeetingResolution {
        guard let engine = nativeEngine else { return .unmapped }
        return engine.resolveMeeting(
            courseCode: courseCode,
            section: section,
            weekday: weekday,
            startTime: startTime,
            fallbackBuildingRaw: fallbackBuildingRaw
        ).toBridgeModel()
    }

    func getRoomOccupancy(roomId: String, timestampIso: String) -> CampusOccupancyStatus? {
        guard let engine = nativeEngine else { return nil }
        return try? engine.getRoomOccupancy(
            roomId: roomId,
            timestampEpochOrIso: timestampIso
        ).toBridgeModel()
    }

    func listBuildingOccupancy(buildingId: String, timestampIso: String) throws -> [CampusRoomOccupancyItem] {
        guard let engine = nativeEngine else { return [] }
        return try engine.listBuildingOccupancy(
            buildingId: buildingId,
            timestampEpochOrIso: timestampIso
        ).map { $0.toBridgeModel() }
    }
    #else
    func loadBundled() throws -> CampusMapBundledSnapshot? { nil }
    func setActiveCampus(_ campusId: String) throws -> CampusMapSummaryModel? { nil }
    func getRenderBatch(activeFloor: Int32, timestampIso: String?) throws -> CampusRenderBatch? { nil }
    func hitTest(mapX: Float, mapY: Float, activeFloor: Int32, zoom: Float) -> CampusMapHit? { nil }
    func getCameraFocus(featureId: String) -> CampusCameraFocus? { nil }
    func getFeature(featureId: String) -> CampusFeatureInfo? { nil }
    func search(query: String, poiTypeFilter: CampusPoiType?, activeFloor: Int32) -> [CampusSearchResult] { [] }
    func resolveMeeting(
        courseCode: String,
        section: String,
        weekday: UInt32,
        startTime: String,
        fallbackBuildingRaw: String
    ) -> CampusMeetingResolution {
        .unmapped
    }
    func getRoomOccupancy(roomId: String, timestampIso: String) -> CampusOccupancyStatus? { nil }
    func listBuildingOccupancy(buildingId: String, timestampIso: String) throws -> [CampusRoomOccupancyItem] { [] }
    #endif
}
