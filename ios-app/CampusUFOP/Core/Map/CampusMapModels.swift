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
