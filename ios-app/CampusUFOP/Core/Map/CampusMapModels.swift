import CoreGraphics
import Foundation

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

public enum CampusPoiType: String, Sendable, Equatable, CaseIterable, Identifiable {
    case toilet
    case facultyOffice
    case entrance
    case dining
    case library
    case parking
    case academic

    public var id: String { rawValue }

    public static let quickFilterCases: [CampusPoiType] = [
        .toilet,
        .facultyOffice,
        .entrance,
        .dining,
        .library
    ]

    public var title: String {
        switch self {
        case .toilet: return "Banheiros"
        case .facultyOffice: return "Professores/Secretarias"
        case .entrance: return "Acessos"
        case .dining: return "Alimentação"
        case .library: return "Biblioteca"
        case .parking: return "Estacionamento"
        case .academic: return "Acadêmico"
        }
    }

    public var systemImage: String {
        switch self {
        case .toilet: return "toilet.fill"
        case .facultyOffice: return "person.text.rectangle.fill"
        case .entrance: return "door.left.hand.open"
        case .dining: return "fork.knife"
        case .library: return "books.vertical.fill"
        case .parking: return "parkingsign.circle.fill"
        case .academic: return "building.columns.fill"
        }
    }
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

    public var shortDisplayName: String {
        switch campusId {
        case "OP_MC": return "Morro do Cruzeiro"
        case "JM_ICEA": return "ICEA"
        case "MA_ICHS": return "ICHS"
        case "MA_ICSA": return "ICSA"
        default: return name
        }
    }

    public init(
        campusId: String,
        name: String,
        city: String,
        defaultFloor: Int32,
        availableFloors: [Int32],
        floorLabels: [CampusFloorLabel],
        bounds: CampusMapRect2D,
        attribution: String
    ) {
        self.campusId = campusId
        self.name = name
        self.city = city
        self.defaultFloor = defaultFloor
        self.availableFloors = availableFloors
        self.floorLabels = floorLabels
        self.bounds = bounds
        self.attribution = attribution
    }
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

    public var isOccupied: Bool {
        if case .occupied = self { return true }
        return false
    }

    public var isFree: Bool {
        self == .noRegularClass
    }

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

public struct CampusSearchResult: Sendable, Equatable, Identifiable {
    public let feature: CampusFeatureInfo
    public let score: UInt32
    public let matchedField: String

    public var id: String { feature.id }

    public init(feature: CampusFeatureInfo, score: UInt32, matchedField: String) {
        self.feature = feature
        self.score = score
        self.matchedField = matchedField
    }
}

public struct CampusRoomOccupancyItem: Sendable, Equatable, Identifiable {
    public let roomId: String
    public let roomCode: String
    public let floor: Int32
    public let isMock: Bool
    public let status: CampusOccupancyStatus

    public var id: String { roomId }

    public init(
        roomId: String,
        roomCode: String,
        floor: Int32,
        isMock: Bool,
        status: CampusOccupancyStatus
    ) {
        self.roomId = roomId
        self.roomCode = roomCode
        self.floor = floor
        self.isMock = isMock
        self.status = status
    }
}

public struct CampusMapMeetingRequest: Sendable, Equatable {
    public let courseCode: String
    public let section: String
    public let weekday: UInt32
    public let startTime: String
    public let fallbackBuildingRaw: String

    public init(
        courseCode: String,
        section: String,
        weekday: UInt32,
        startTime: String,
        fallbackBuildingRaw: String
    ) {
        self.courseCode = courseCode
        self.section = section
        self.weekday = weekday
        self.startTime = startTime
        self.fallbackBuildingRaw = fallbackBuildingRaw
    }
}

public struct CampusFloorOccupancySummary: Sendable, Equatable {
    public let freeCount: Int
    public let occupiedCount: Int
    public let noDataCount: Int
    public let outOfTermCount: Int

    public var totalRooms: Int {
        freeCount + occupiedCount + noDataCount + outOfTermCount
    }

    public var hasAnyRooms: Bool {
        totalRooms > 0
    }

    public init(
        freeCount: Int = 0,
        occupiedCount: Int = 0,
        noDataCount: Int = 0,
        outOfTermCount: Int = 0
    ) {
        self.freeCount = freeCount
        self.occupiedCount = occupiedCount
        self.noDataCount = noDataCount
        self.outOfTermCount = outOfTermCount
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

    public static func visibleMapRect(
        viewportSize: CGSize,
        transform: CampusMapViewportTransform,
        marginPt: CGFloat = 48
    ) -> CampusMapRect2D {
        let safeW = max(1, viewportSize.width)
        let safeH = max(1, viewportSize.height)
        let safeMargin = max(0, marginPt)
        let topLeft = screenToMap(
            screenPoint: CGPoint(x: -safeMargin, y: -safeMargin),
            transform: transform
        )
        let bottomRight = screenToMap(
            screenPoint: CGPoint(x: safeW + safeMargin, y: safeH + safeMargin),
            transform: transform
        )
        return CampusMapRect2D(
            minX: min(topLeft.x, bottomRight.x),
            minY: min(topLeft.y, bottomRight.y),
            maxX: max(topLeft.x, bottomRight.x),
            maxY: max(topLeft.y, bottomRight.y)
        )
    }

    public static func rectsIntersect(_ a: CampusMapRect2D, _ b: CampusMapRect2D) -> Bool {
        a.maxX >= b.minX && a.minX <= b.maxX && a.maxY >= b.minY && a.minY <= b.maxY
    }

    public static func bboxIntersects(
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float,
        viewport: CampusMapRect2D
    ) -> Bool {
        maxX >= viewport.minX && minX <= viewport.maxX && maxY >= viewport.minY && minY <= viewport.maxY
    }

    public static func pointInRect(
        x: Float,
        y: Float,
        rect: CampusMapRect2D,
        marginM: Float = 0
    ) -> Bool {
        x >= (rect.minX - marginM) &&
            x <= (rect.maxX + marginM) &&
            y >= (rect.minY - marginM) &&
            y <= (rect.maxY + marginM)
    }
}

public struct CampusLabelCollisionChecker {
    private var placedRects: [CGRect]

    public init(initialCapacity: Int = 64) {
        self.placedRects = []
        self.placedRects.reserveCapacity(max(8, initialCapacity))
    }

    public mutating func reset() {
        placedRects.removeAll(keepingCapacity: true)
    }

    public mutating func tryPlace(_ rect: CGRect, padding: CGFloat = 4) -> Bool {
        guard rect.width > 0,
              rect.height > 0,
              rect.minX.isFinite,
              rect.minY.isFinite,
              rect.width.isFinite,
              rect.height.isFinite else {
            return false
        }
        let pad = max(0, padding)
        let padded = rect.insetBy(dx: -pad, dy: -pad)
        for existing in placedRects where padded.intersects(existing) {
            return false
        }
        placedRects.append(rect)
        return true
    }
}
