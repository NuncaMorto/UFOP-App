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
    public private(set) var pendingFocus: CampusCameraFocus? = nil
    public private(set) var isNativeAvailable: Bool = false
    public private(set) var errorMessage: String? = nil

    private let adapter = CampusMapNativeAdapter()

    public init(timestampIso: String? = nil) {
        self.timestampIso = timestampIso
        loadBundledEngine()
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
            refreshRenderBatch()
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
                timestampIso: timestampIso
            ) else {
                return
            }
            renderBatch = batch
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    public func handleTap(mapX: Float, mapY: Float, zoom: Float) -> CampusMapHit? {
        guard let hit = adapter.hitTest(
            mapX: mapX,
            mapY: mapY,
            activeFloor: activeFloor,
            zoom: zoom
        ) else {
            selectedFeature = nil
            selectedOccupancy = nil
            return nil
        }
        selectedFeature = hit.feature
        refreshSelectedOccupancy()
        return hit
    }

    public func focusFeature(_ featureId: String) {
        guard let focus = adapter.getCameraFocus(featureId: featureId) else { return }
        applyCameraFocus(focus)
        selectedFeature = adapter.getFeature(featureId: focus.featureId)
        refreshSelectedOccupancy()
    }

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
            applyCameraFocus(focus)
            selectedFeature = adapter.getFeature(featureId: roomId)
            refreshSelectedOccupancy()
        case .buildingFallback(_, let buildingId, let focus):
            applyCameraFocus(focus)
            selectedFeature = adapter.getFeature(featureId: buildingId)
            selectedOccupancy = nil
        case .unmapped:
            break
        }
        return res
    }

    public func consumePendingFocus() {
        pendingFocus = nil
    }

    public func clearSelection() {
        selectedFeature = nil
        selectedOccupancy = nil
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
        if focus.campusId != activeCampusId {
            _ = try? adapter.setActiveCampus(focus.campusId)
            activeCampusId = focus.campusId
        }
        activeFloor = focus.targetFloor
        refreshRenderBatch()
        pendingFocus = focus
    }

    private func refreshSelectedOccupancy() {
        guard let feat = selectedFeature,
              feat.layer == .room else {
            selectedOccupancy = nil
            return
        }
        let ts = timestampIso ?? Self.currentIsoTimestamp()
        selectedOccupancy = adapter.getRoomOccupancy(roomId: feat.id, timestampIso: ts)
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
    #else
    func loadBundled() throws -> CampusMapBundledSnapshot? { nil }
    func setActiveCampus(_ campusId: String) throws -> CampusMapSummaryModel? { nil }
    func getRenderBatch(activeFloor: Int32, timestampIso: String?) throws -> CampusRenderBatch? { nil }
    func hitTest(mapX: Float, mapY: Float, activeFloor: Int32, zoom: Float) -> CampusMapHit? { nil }
    func getCameraFocus(featureId: String) -> CampusCameraFocus? { nil }
    func getFeature(featureId: String) -> CampusFeatureInfo? { nil }
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
    #endif
}

#if canImport(CampusCoreFFI)
private extension CampusCoreFFI.MapLayer {
    func toBridgeModel() -> CampusMapLayer {
        switch self {
        case .campusGround: return .campusGround
        case .campusZone: return .campusZone
        case .greenArea: return .greenArea
        case .parking: return .parking
        case .sports: return .sports
        case .walkway: return .walkway
        case .building: return .building
        case .room: return .room
        case .poi: return .poi
        }
    }
}

private extension CampusCoreFFI.IndoorStatus {
    func toBridgeModel() -> CampusIndoorStatus {
        switch self {
        case .floorplan: return .floorplan
        case .partialFloorplan: return .partialFloorplan
        case .provisional: return .provisional
        case .exteriorOnly: return .exteriorOnly
        }
    }
}

private extension CampusCoreFFI.PoiType {
    func toBridgeModel() -> CampusPoiType {
        switch self {
        case .toilet: return .toilet
        case .facultyOffice: return .facultyOffice
        case .entrance: return .entrance
        case .dining: return .dining
        case .library: return .library
        case .parking: return .parking
        case .academic: return .academic
        }
    }
}

private extension CampusCoreFFI.RenderGeomKind {
    func toBridgeModel() -> CampusRenderGeomKind {
        switch self {
        case .polygon: return .polygon
        case .polyline: return .polyline
        case .point: return .point
        }
    }
}

private extension CampusCoreFFI.RenderOccupancyKind {
    func toBridgeModel() -> CampusRenderOccupancyKind {
        switch self {
        case .none: return .none
        case .occupied: return .occupied
        case .noRegularClass: return .noRegularClass
        case .noData: return .noData
        case .outOfTerm: return .outOfTerm
        }
    }
}

private extension CampusCoreFFI.Rect2D {
    func toBridgeModel() -> CampusMapRect2D {
        CampusMapRect2D(minX: minX, minY: minY, maxX: maxX, maxY: maxY)
    }
}

private extension CampusCoreFFI.FloorLabel {
    func toBridgeModel() -> CampusFloorLabel {
        CampusFloorLabel(floor: floor, label: label)
    }
}

private extension CampusCoreFFI.CampusMapSummary {
    func toBridgeModel() -> CampusMapSummaryModel {
        CampusMapSummaryModel(
            campusId: campusId,
            name: name,
            city: city,
            defaultFloor: defaultFloor,
            availableFloors: availableFloors,
            floorLabels: floorLabels.map { $0.toBridgeModel() },
            bounds: bounds.toBridgeModel(),
            attribution: attribution
        )
    }
}

private extension CampusCoreFFI.CameraFocus {
    func toBridgeModel() -> CampusCameraFocus {
        CampusCameraFocus(
            campusId: campusId,
            featureId: featureId,
            targetFloor: targetFloor,
            bounds: bounds.toBridgeModel()
        )
    }
}

private extension CampusCoreFFI.FeatureInfo {
    func toBridgeModel() -> CampusFeatureInfo {
        CampusFeatureInfo(
            id: id,
            campusId: campusId,
            layer: layer.toBridgeModel(),
            code: code,
            name: name,
            category: category,
            buildingId: buildingId,
            parentId: parentId,
            floor: floor,
            availableFloors: availableFloors,
            floorLabels: floorLabels.map { $0.toBridgeModel() },
            defaultFloor: defaultFloor,
            poiType: poiType?.toBridgeModel(),
            indoorStatus: indoorStatus.toBridgeModel(),
            isMock: isMock,
            aliases: aliases,
            departments: departments,
            notes: notes,
            minZoom: minZoom,
            bounds: bounds.toBridgeModel(),
            labelX: labelX,
            labelY: labelY
        )
    }
}

private extension CampusCoreFFI.MapHit {
    func toBridgeModel() -> CampusMapHit {
        CampusMapHit(feature: feature.toBridgeModel(), distanceMeters: distanceM)
    }
}

private extension CampusCoreFFI.SlotInfo {
    func toBridgeModel() -> CampusSlotInfo {
        CampusSlotInfo(
            campusId: campusId,
            buildingId: buildingId,
            roomId: roomId,
            roomCode: roomCode,
            weekday: weekday,
            startTime: startTime,
            endTime: endTime,
            kind: kind,
            label: label,
            title: title,
            courseCodes: courseCodes
        )
    }
}

private extension CampusCoreFFI.OccupancyStatus {
    func toBridgeModel() -> CampusOccupancyStatus {
        switch self {
        case .occupied(let slot):
            return .occupied(slot: slot.toBridgeModel())
        case .noRegularClass:
            return .noRegularClass
        case .noData:
            return .noData
        case .outOfTerm:
            return .outOfTerm
        }
    }
}

private extension CampusCoreFFI.Resolution {
    func toBridgeModel() -> CampusMeetingResolution {
        switch self {
        case .exactRoom(let campusId, let buildingId, let roomId, let floor, let cameraFocus):
            return .exactRoom(
                campusId: campusId,
                buildingId: buildingId,
                roomId: roomId,
                floor: floor,
                cameraFocus: cameraFocus.toBridgeModel()
            )
        case .buildingFallback(let campusId, let buildingId, let cameraFocus):
            return .buildingFallback(
                campusId: campusId,
                buildingId: buildingId,
                cameraFocus: cameraFocus.toBridgeModel()
            )
        case .unmapped:
            return .unmapped
        }
    }
}

private extension CampusCoreFFI.RenderFeature {
    func toBridgeModel() -> CampusRenderFeature {
        CampusRenderFeature(
            id: id,
            layer: layer.toBridgeModel(),
            geomKind: geomKind.toBridgeModel(),
            ringStartIdx: ringStartIdx,
            ringCount: ringCount,
            pointX: pointX,
            pointY: pointY,
            labelX: labelX,
            labelY: labelY,
            bboxMinX: bboxMinX,
            bboxMinY: bboxMinY,
            bboxMaxX: bboxMaxX,
            bboxMaxY: bboxMaxY,
            minZoom: minZoom,
            primaryLabel: primaryLabel,
            secondaryLabel: secondaryLabel,
            category: category,
            poiType: poiType?.toBridgeModel(),
            indoorStatus: indoorStatus.toBridgeModel(),
            isMock: isMock,
            occupancy: occupancy.toBridgeModel(),
            occupancyLabel: occupancyLabel
        )
    }
}

private extension CampusCoreFFI.RenderBatch {
    func toBridgeModel() -> CampusRenderBatch {
        CampusRenderBatch(
            campusId: campusId,
            campusName: campusName,
            activeFloor: activeFloor,
            availableFloors: availableFloors,
            floorLabels: floorLabels.map { $0.toBridgeModel() },
            bounds: bounds.toBridgeModel(),
            attribution: attribution,
            coords: coords,
            ringStarts: ringStarts,
            ringCounts: ringCounts,
            items: items.map { $0.toBridgeModel() }
        )
    }
}
#endif
