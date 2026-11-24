import Foundation

#if canImport(CampusCoreFFI)
import CampusCoreFFI

// MARK: - Mappers FFI (CampusCoreFFI -> Modelos Nativos Swift)

internal extension CampusCoreFFI.MapLayer {
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

internal extension CampusCoreFFI.IndoorStatus {
    func toBridgeModel() -> CampusIndoorStatus {
        switch self {
        case .floorplan: return .floorplan
        case .partialFloorplan: return .partialFloorplan
        case .provisional: return .provisional
        case .exteriorOnly: return .exteriorOnly
        }
    }
}

internal extension CampusCoreFFI.PoiType {
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

internal extension CampusPoiType {
    func toFfi() -> CampusCoreFFI.PoiType {
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

internal extension CampusCoreFFI.RenderGeomKind {
    func toBridgeModel() -> CampusRenderGeomKind {
        switch self {
        case .polygon: return .polygon
        case .polyline: return .polyline
        case .point: return .point
        }
    }
}

internal extension CampusCoreFFI.RenderOccupancyKind {
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

internal extension CampusCoreFFI.Rect2D {
    func toBridgeModel() -> CampusMapRect2D {
        CampusMapRect2D(minX: minX, minY: minY, maxX: maxX, maxY: maxY)
    }
}

internal extension CampusCoreFFI.FloorLabel {
    func toBridgeModel() -> CampusFloorLabel {
        CampusFloorLabel(floor: floor, label: label)
    }
}

internal extension CampusCoreFFI.CampusMapSummary {
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

internal extension CampusCoreFFI.CameraFocus {
    func toBridgeModel() -> CampusCameraFocus {
        CampusCameraFocus(
            campusId: campusId,
            featureId: featureId,
            targetFloor: targetFloor,
            bounds: bounds.toBridgeModel()
        )
    }
}

internal extension CampusCoreFFI.FeatureInfo {
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

internal extension CampusCoreFFI.MapHit {
    func toBridgeModel() -> CampusMapHit {
        CampusMapHit(feature: feature.toBridgeModel(), distanceMeters: distanceM)
    }
}

internal extension CampusCoreFFI.SearchResult {
    func toBridgeModel() -> CampusSearchResult {
        CampusSearchResult(
            feature: feature.toBridgeModel(),
            score: score,
            matchedField: matchedField
        )
    }
}

internal extension CampusCoreFFI.SlotInfo {
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

internal extension CampusCoreFFI.OccupancyStatus {
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

internal extension CampusCoreFFI.RoomOccupancyItem {
    func toBridgeModel() -> CampusRoomOccupancyItem {
        CampusRoomOccupancyItem(
            roomId: roomId,
            roomCode: roomCode,
            floor: floor,
            isMock: isMock,
            status: status.toBridgeModel()
        )
    }
}

internal extension CampusCoreFFI.Resolution {
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

internal extension CampusCoreFFI.RenderFeature {
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

internal extension CampusCoreFFI.RenderBatch {
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
