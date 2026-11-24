import CoreGraphics
import Foundation
import Observation

#if canImport(CampusCoreFFI)
import CampusCoreFFI
#endif

// MARK: - Modelos Nativos do Mapa Offline 2D (Sem campo capacity)

public enum CampusMapLayer: Int, Sendable, Equatable, CaseIterable, Comparable {
    case campusGround = 0
    case campusZone = 1
    case greenArea = 2
    case parking = 3
    case sports = 4
    case walkway = 5
    case building = 6
    case room = 7
    case poi = 8

    public static func < (lhs: CampusMapLayer, rhs: CampusMapLayer) -> Bool {
        lhs.rawValue < rhs.rawValue
    }
}

public enum CampusIndoorStatus: String, Sendable, Equatable {
    case floorplan
    case partialFloorplan
    case provisional
    case exteriorOnly
}

public enum CampusPoiType: String, Sendable, Equatable, CaseIterable {
    case toilet
    case facultyOffice
    case entrance
    case dining
    case library
    case parking
    case academic
}

public enum CampusRenderGeomKind: Sendable, Equatable {
    case polygon
    case polyline
    case point
}

public enum CampusRenderOccupancyKind: Sendable, Equatable {
    case none
    case occupied
    case noRegularClass
    case noData
    case outOfTerm
}

public struct CampusMapRect2D: Sendable, Equatable {
    public let minX: Float
    public let minY: Float
    public let maxX: Float
    public let maxY: Float

    public init(minX: Float, minY: Float, maxX: Float, maxY: Float) {
        self.minX = minX
        self.minY = minY
        self.maxX = maxX
        self.maxY = maxY
    }
}

public struct CampusFloorLabel: Sendable, Equatable, Identifiable {
    public let floor: Int32
    public let label: String
    public var id: Int32 { floor }

    public init(floor: Int32, label: String) {
        self.floor = floor
        self.label = label
    }
}

public struct CampusMapSummaryModel: Sendable, Equatable, Identifiable {
    public let campusId: String
    public let name: String
    public let city: String
    public let defaultFloor: Int32
    public let availableFloors: [Int32]
    public let floorLabels: [CampusFloorLabel]
    public let bounds: CampusMapRect2D
    public let attribution: String

    public var id: String { campusId }
}

public struct CampusCameraFocus: Sendable, Equatable {
    public let campusId: String
    public let featureId: String
    public let targetFloor: Int32
    public let bounds: CampusMapRect2D

    public init(campusId: String, featureId: String, targetFloor: Int32, bounds: CampusMapRect2D) {
        self.campusId = campusId
        self.featureId = featureId
        self.targetFloor = targetFloor
        self.bounds = bounds
    }
}

public struct CampusFeatureInfo: Sendable, Equatable, Identifiable {
    public let id: String
    public let campusId: String
    public let layer: CampusMapLayer
    public let code: String?
    public let name: String
    public let category: String?
    public let buildingId: String?
    public let parentId: String?
    public let floor: Int32?
    public let availableFloors: [Int32]
    public let floorLabels: [CampusFloorLabel]
    public let defaultFloor: Int32?
    public let poiType: CampusPoiType?
    public let indoorStatus: CampusIndoorStatus
    public let isMock: Bool
    public let aliases: [String]
    public let departments: [String]
    public let notes: String?
    public let minZoom: Float
    public let bounds: CampusMapRect2D
    public let labelX: Float
    public let labelY: Float
}

public struct CampusMapHit: Sendable, Equatable {
    public let feature: CampusFeatureInfo
    public let distanceMeters: Float
}

public struct CampusSlotInfo: Sendable, Equatable {
    public let campusId: String
    public let buildingId: String
    public let roomId: String
    public let roomCode: String
    public let weekday: UInt32
    public let startTime: String
    public let endTime: String
    public let kind: String
    public let label: String
    public let title: String?
    public let courseCodes: [String]
}

public enum CampusOccupancyStatus: Sendable, Equatable {
    case occupied(slot: CampusSlotInfo)
    case noRegularClass
    case noData
    case outOfTerm

    public var displayTitle: String {
        switch self {
        case .occupied(let slot):
            return "\(slot.label) (\(slot.startTime)–\(slot.endTime))"
        case .noRegularClass:
            return "Sem aula regular agora"
        case .noData:
            return "Sem grade cadastrada para este dia/prédio"
        case .outOfTerm:
            return "Fora do período letivo / feriado"
        }
    }
}

public enum CampusMeetingResolution: Sendable, Equatable {
    case exactRoom(campusId: String, buildingId: String, roomId: String, floor: Int32, cameraFocus: CampusCameraFocus)
    case buildingFallback(campusId: String, buildingId: String, cameraFocus: CampusCameraFocus)
    case unmapped
}

public struct CampusRenderFeature: Sendable, Equatable, Identifiable {
    public let id: String
    public let layer: CampusMapLayer
    public let geomKind: CampusRenderGeomKind
    public let ringStartIdx: UInt32
    public let ringCount: UInt32
    public let pointX: Float
    public let pointY: Float
    public let labelX: Float
    public let labelY: Float
    public let bboxMinX: Float
    public let bboxMinY: Float
    public let bboxMaxX: Float
    public let bboxMaxY: Float
    public let minZoom: Float
    public let primaryLabel: String
    public let secondaryLabel: String
    public let category: String
    public let poiType: CampusPoiType?
    public let indoorStatus: CampusIndoorStatus
    public let isMock: Bool
    public let occupancy: CampusRenderOccupancyKind
    public let occupancyLabel: String?
}

public struct CampusRenderBatch: Sendable, Equatable {
    public let campusId: String
    public let campusName: String
    public let activeFloor: Int32
    public let availableFloors: [Int32]
    public let floorLabels: [CampusFloorLabel]
    public let bounds: CampusMapRect2D
    public let attribution: String
    public let coords: [Float]
    public let ringStarts: [UInt32]
    public let ringCounts: [UInt32]
    public let items: [CampusRenderFeature]
}

// MARK: - Matemática de Transformação de Viewport

public struct CampusMapViewportTransform: Equatable, Sendable {
    public var offsetX: CGFloat
    public var offsetY: CGFloat
    public var zoomPtPerMeter: CGFloat

    public init(offsetX: CGFloat = 0, offsetY: CGFloat = 0, zoomPtPerMeter: CGFloat = 1.2) {
        self.offsetX = offsetX
        self.offsetY = offsetY
        self.zoomPtPerMeter = zoomPtPerMeter
    }
}

public enum CampusMapTransformMath {
    public static let minZoomPtPerMeter: CGFloat = 0.18
    public static let maxZoomPtPerMeter: CGFloat = 18.0

    public static func clampZoom(_ zoom: CGFloat) -> CGFloat {
        guard zoom.isFinite else { return 1.2 }
        return min(max(zoom, minZoomPtPerMeter), maxZoomPtPerMeter)
    }

    public static func mapToScreen(
        mapX: CGFloat,
        mapY: CGFloat,
        transform: CampusMapViewportTransform
    ) -> CGPoint {
        let scale = clampZoom(transform.zoomPtPerMeter)
        return CGPoint(
            x: mapX * scale + transform.offsetX,
            y: mapY * scale + transform.offsetY
        )
    }

    public static func screenToMap(
        screenPoint: CGPoint,
        transform: CampusMapViewportTransform
    ) -> (x: Float, y: Float) {
        let scale = clampZoom(transform.zoomPtPerMeter)
        return (
            Float((screenPoint.x - transform.offsetX) / scale),
            Float((screenPoint.y - transform.offsetY) / scale)
        )
    }

    public static func applyPanAndZoom(
        current: CampusMapViewportTransform,
        panDelta: CGSize,
        zoomFactor: CGFloat,
        centroid: CGPoint
    ) -> CampusMapViewportTransform {
        let oldScale = clampZoom(current.zoomPtPerMeter)
        let newZoom = clampZoom(current.zoomPtPerMeter * zoomFactor)
        let anchorMapX = (centroid.x - current.offsetX) / oldScale
        let anchorMapY = (centroid.y - current.offsetY) / oldScale

        return CampusMapViewportTransform(
            offsetX: centroid.x - anchorMapX * newZoom + panDelta.width,
            offsetY: centroid.y - anchorMapY * newZoom + panDelta.height,
            zoomPtPerMeter: newZoom
        )
    }

    public static func computeCameraTarget(
        bounds: CampusMapRect2D,
        viewportSize: CGSize,
        paddingPt: CGFloat = 36
    ) -> CampusMapViewportTransform {
        let safeW = max(1, viewportSize.width)
        let safeH = max(1, viewportSize.height)
        let availW = max(24, safeW - paddingPt * 2)
        let availH = max(24, safeH - paddingPt * 2)

        let spanX = max(8, CGFloat(bounds.maxX - bounds.minX))
        let spanY = max(8, CGFloat(bounds.maxY - bounds.minY))
        let targetZoom = clampZoom(min(availW / spanX, availH / spanY))

        let centerMapX = CGFloat(bounds.minX + bounds.maxX) * 0.5
        let centerMapY = CGFloat(bounds.minY + bounds.maxY) * 0.5

        return CampusMapViewportTransform(
            offsetX: safeW * 0.5 - centerMapX * targetZoom,
            offsetY: safeH * 0.5 - centerMapY * targetZoom,
            zoomPtPerMeter: targetZoom
        )
    }

    public static func shouldShowSecondaryLabel(
        bboxMinX: Float,
        bboxMaxX: Float,
        zoomPtPerMeter: CGFloat,
        textWidthPt: CGFloat,
        marginPt: CGFloat = 8
    ) -> Bool {
        guard textWidthPt > 0 else { return false }
        let polyScreenWidth = CGFloat(bboxMaxX - bboxMinX) * clampZoom(zoomPtPerMeter)
        return polyScreenWidth >= (textWidthPt + marginPt)
    }
}

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

    #if canImport(CampusCoreFFI)
    private var nativeEngine: CampusMapEngine? = nil
    #endif

    public init(timestampIso: String? = nil) {
        self.timestampIso = timestampIso
        loadBundledEngine()
    }

    public func loadBundledEngine() {
        #if canImport(CampusCoreFFI)
        do {
            let engine = try CampusMapEngine.newBundled()
            nativeEngine = engine
            isNativeAvailable = true
            let loadedCampuses = engine.listCampuses().map { $0.toBridgeModel() }
            campuses = loadedCampuses
            activeCampusId = engine.getActiveCampusId()
            let currentCampus = loadedCampuses.first { $0.campusId == activeCampusId }
            activeFloor = currentCampus?.defaultFloor ?? 0
            errorMessage = nil
            refreshRenderBatch()
        } catch {
            isNativeAvailable = false
            errorMessage = "Falha ao inicializar motor vetorial: \(error.localizedDescription)"
        }
        #else
        isNativeAvailable = false
        campuses = []
        renderBatch = nil
        errorMessage = nil
        #endif
    }

    public func selectCampus(_ campusId: String) {
        #if canImport(CampusCoreFFI)
        guard let engine = nativeEngine else { return }
        do {
            let summary = try engine.setActiveCampus(campusId: campusId).toBridgeModel()
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
        #endif
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
        #if canImport(CampusCoreFFI)
        guard let engine = nativeEngine else { return }
        do {
            renderBatch = try engine.getRenderBatch(
                activeFloor: activeFloor,
                timestampOpt: timestampIso
            ).toBridgeModel()
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
        #endif
    }

    public func handleTap(mapX: Float, mapY: Float, zoom: Float) -> CampusMapHit? {
        #if canImport(CampusCoreFFI)
        guard let engine = nativeEngine else {
            selectedFeature = nil
            selectedOccupancy = nil
            return nil
        }
        guard let hit = engine.hitTest(
            mapX: mapX,
            mapY: mapY,
            activeFloor: activeFloor,
            zoom: zoom
        )?.toBridgeModel() else {
            selectedFeature = nil
            selectedOccupancy = nil
            return nil
        }
        selectedFeature = hit.feature
        refreshSelectedOccupancy()
        return hit
        #else
        selectedFeature = nil
        selectedOccupancy = nil
        return nil
        #endif
    }

    public func focusFeature(_ featureId: String) {
        #if canImport(CampusCoreFFI)
        guard let engine = nativeEngine,
              let focus = engine.getCameraFocus(featureId: featureId)?.toBridgeModel() else {
            return
        }
        applyCameraFocus(focus)
        selectedFeature = engine.getFeature(featureId: focus.featureId)?.toBridgeModel()
        refreshSelectedOccupancy()
        #endif
    }

    public func resolveMeeting(
        courseCode: String,
        section: String,
        weekday: UInt32,
        startTime: String,
        fallbackBuildingRaw: String
    ) -> CampusMeetingResolution {
        #if canImport(CampusCoreFFI)
        guard let engine = nativeEngine else { return .unmapped }
        let res = engine.resolveMeeting(
            courseCode: courseCode,
            section: section,
            weekday: weekday,
            startTime: startTime,
            fallbackBuildingRaw: fallbackBuildingRaw
        ).toBridgeModel()
        switch res {
        case .exactRoom(_, _, let roomId, _, let focus):
            applyCameraFocus(focus)
            selectedFeature = engine.getFeature(featureId: roomId)?.toBridgeModel()
            refreshSelectedOccupancy()
        case .buildingFallback(_, let buildingId, let focus):
            applyCameraFocus(focus)
            selectedFeature = engine.getFeature(featureId: buildingId)?.toBridgeModel()
            selectedOccupancy = nil
        case .unmapped:
            break
        }
        return res
        #else
        return .unmapped
        #endif
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
        #if canImport(CampusCoreFFI)
        return nativeEngine?.getFeature(featureId: featureId)?.toBridgeModel()
        #else
        return nil
        #endif
    }

    private func applyCameraFocus(_ focus: CampusCameraFocus) {
        #if canImport(CampusCoreFFI)
        if focus.campusId != activeCampusId, let engine = nativeEngine {
            _ = try? engine.setActiveCampus(campusId: focus.campusId)
            activeCampusId = focus.campusId
        }
        #endif
        activeFloor = focus.targetFloor
        refreshRenderBatch()
        pendingFocus = focus
    }

    private func refreshSelectedOccupancy() {
        #if canImport(CampusCoreFFI)
        guard let engine = nativeEngine,
              let feat = selectedFeature,
              feat.layer == .room else {
            selectedOccupancy = nil
            return
        }
        let ts = timestampIso ?? Self.currentIsoTimestamp()
        selectedOccupancy = try? engine.getRoomOccupancy(
            roomId: feat.id,
            timestampEpochOrIso: ts
        ).toBridgeModel()
        #else
        selectedOccupancy = nil
        #endif
    }

    private static func currentIsoTimestamp() -> String {
        let formatter = ISO8601DateFormatter()
        formatter.timeZone = TimeZone(identifier: "America/Sao_Paulo")
        return formatter.string(from: Date())
    }
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
