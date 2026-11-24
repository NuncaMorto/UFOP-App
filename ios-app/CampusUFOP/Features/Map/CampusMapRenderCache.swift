import CoreGraphics
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Cache Geométrico e de Rótulos para Canvas 2D Offline (iOS)

internal struct CampusStaticLayerBatch {
    let layer: CampusMapLayer
    let path: Path
    let bounds: CampusMapRect2D
}

internal struct CampusCachedDynamicItem: Identifiable {
    let featureIndex: Int
    let id: String
    let layer: CampusMapLayer
    let geomKind: CampusRenderGeomKind
    let path: Path?
    let bboxMinX: Float
    let bboxMinY: Float
    let bboxMaxX: Float
    let bboxMaxY: Float
    let minZoom: Float
    let pointX: Float
    let pointY: Float
    let labelX: Float
    let labelY: Float
    let isMock: Bool
    let poiType: CampusPoiType?
    let categoryLower: String
}

internal struct CampusMapGeometryCache {
    let groundBatch: CampusStaticLayerBatch?
    let groundOverflow: [CampusCachedDynamicItem]
    let campusZones: [CampusCachedDynamicItem]
    let overlayStaticBatches: [CampusStaticLayerBatch]
    let overlayStaticOverflow: [CampusCachedDynamicItem]
    let walkwayPath: Path?
    let walkwayBounds: CampusMapRect2D?
    let walkwayOverflow: [CampusCachedDynamicItem]
    let foregroundItems: [CampusCachedDynamicItem]

    static func build(from batch: CampusRenderBatch) -> CampusMapGeometryCache {
        var staticPaths: [CampusMapLayer: Path] = [:]
        var staticBounds: [CampusMapLayer: CampusMapRect2D] = [:]
        var groundOverflow: [CampusCachedDynamicItem] = []
        var campusZones: [CampusCachedDynamicItem] = []
        var overlayStaticOverflow: [CampusCachedDynamicItem] = []
        var walkwayPath: Path? = nil
        var walkwayBounds: CampusMapRect2D? = nil
        var walkwayOverflow: [CampusCachedDynamicItem] = []
        var foregroundItems: [CampusCachedDynamicItem] = []

        for (index, item) in batch.items.enumerated() {
            switch item.layer {
            case .campusGround, .greenArea, .parking, .sports:
                if item.geomKind == .polygon && item.ringCount == 1 && item.minZoom <= 0 {
                    var layerPath = staticPaths[item.layer] ?? Path()
                    appendItemToPath(&layerPath, item: item, batch: batch)
                    staticPaths[item.layer] = layerPath

                    let itemBounds = CampusMapRect2D(
                        minX: item.bboxMinX,
                        minY: item.bboxMinY,
                        maxX: item.bboxMaxX,
                        maxY: item.bboxMaxY
                    )
                    if let prev = staticBounds[item.layer] {
                        staticBounds[item.layer] = CampusMapRect2D(
                            minX: min(prev.minX, itemBounds.minX),
                            minY: min(prev.minY, itemBounds.minY),
                            maxX: max(prev.maxX, itemBounds.maxX),
                            maxY: max(prev.maxY, itemBounds.maxY)
                        )
                    } else {
                        staticBounds[item.layer] = itemBounds
                    }
                } else {
                    let entry = makeDynamicItem(index: index, item: item, batch: batch)
                    if item.layer == .campusGround {
                        groundOverflow.append(entry)
                    } else {
                        overlayStaticOverflow.append(entry)
                    }
                }

            case .campusZone:
                campusZones.append(makeDynamicItem(index: index, item: item, batch: batch))

            case .walkway:
                if item.geomKind == .polyline && item.minZoom <= 0 {
                    var wPath = walkwayPath ?? Path()
                    appendItemToPath(&wPath, item: item, batch: batch)
                    walkwayPath = wPath

                    let itemBounds = CampusMapRect2D(
                        minX: item.bboxMinX,
                        minY: item.bboxMinY,
                        maxX: item.bboxMaxX,
                        maxY: item.bboxMaxY
                    )
                    if let prev = walkwayBounds {
                        walkwayBounds = CampusMapRect2D(
                            minX: min(prev.minX, itemBounds.minX),
                            minY: min(prev.minY, itemBounds.minY),
                            maxX: max(prev.maxX, itemBounds.maxX),
                            maxY: max(prev.maxY, itemBounds.maxY)
                        )
                    } else {
                        walkwayBounds = itemBounds
                    }
                } else {
                    walkwayOverflow.append(makeDynamicItem(index: index, item: item, batch: batch))
                }

            case .building, .room, .poi:
                foregroundItems.append(makeDynamicItem(index: index, item: item, batch: batch))
            }
        }

        let groundBatch: CampusStaticLayerBatch? = {
            guard let path = staticPaths[.campusGround], let bounds = staticBounds[.campusGround] else {
                return nil
            }
            return CampusStaticLayerBatch(layer: .campusGround, path: path, bounds: bounds)
        }()

        var overlayStaticBatches: [CampusStaticLayerBatch] = []
        for layer in [CampusMapLayer.greenArea, .parking, .sports] {
            if let path = staticPaths[layer], let bounds = staticBounds[layer] {
                overlayStaticBatches.append(
                    CampusStaticLayerBatch(layer: layer, path: path, bounds: bounds)
                )
            }
        }

        return CampusMapGeometryCache(
            groundBatch: groundBatch,
            groundOverflow: groundOverflow,
            campusZones: campusZones,
            overlayStaticBatches: overlayStaticBatches,
            overlayStaticOverflow: overlayStaticOverflow,
            walkwayPath: walkwayPath,
            walkwayBounds: walkwayBounds,
            walkwayOverflow: walkwayOverflow,
            foregroundItems: foregroundItems
        )
    }

    private static func makeDynamicItem(
        index: Int,
        item: CampusRenderFeature,
        batch: CampusRenderBatch
    ) -> CampusCachedDynamicItem {
        let localPath: Path?
        if item.geomKind == .point {
            localPath = nil
        } else {
            var p = Path()
            appendItemToPath(&p, item: item, batch: batch)
            localPath = p
        }
        return CampusCachedDynamicItem(
            featureIndex: index,
            id: item.id,
            layer: item.layer,
            geomKind: item.geomKind,
            path: localPath,
            bboxMinX: item.bboxMinX,
            bboxMinY: item.bboxMinY,
            bboxMaxX: item.bboxMaxX,
            bboxMaxY: item.bboxMaxY,
            minZoom: item.minZoom,
            pointX: item.pointX,
            pointY: item.pointY,
            labelX: item.labelX,
            labelY: item.labelY,
            isMock: item.isMock,
            poiType: item.poiType,
            categoryLower: item.category.lowercased()
        )
    }

    private static func appendItemToPath(
        _ path: inout Path,
        item: CampusRenderFeature,
        batch: CampusRenderBatch
    ) {
        guard item.geomKind != .point else { return }
        let startRing = Int(item.ringStartIdx)
        let endRing = min(startRing + Int(item.ringCount), batch.ringStarts.count)
        guard startRing < endRing else { return }

        for rIdx in startRing..<endRing {
            let vStart = Int(batch.ringStarts[rIdx])
            let vCount = Int(batch.ringCounts[rIdx])
            guard vCount >= 2, (vStart + vCount) * 2 <= batch.coords.count else { continue }
            let x0 = CGFloat(batch.coords[vStart * 2])
            let y0 = CGFloat(batch.coords[vStart * 2 + 1])
            path.move(to: CGPoint(x: x0, y: y0))
            for k in 1..<vCount {
                let x = CGFloat(batch.coords[(vStart + k) * 2])
                let y = CGFloat(batch.coords[(vStart + k) * 2 + 1])
                path.addLine(to: CGPoint(x: x, y: y))
            }
            if item.geomKind == .polygon {
                path.closeSubpath()
            }
        }
    }
}

internal struct CampusCachedLabelItem {
    let featureId: String
    let layer: CampusMapLayer
    let labelX: Float
    let labelY: Float
    let bboxMinX: Float
    let bboxMaxX: Float
    let minZoom: Float
    let primaryText: Text
    let primarySize: CGSize
    let secondaryText: Text?
    let secondarySize: CGSize
}

internal struct CampusMapLabelCache {
    let orderedLabels: [CampusCachedLabelItem]
    let byId: [String: CampusCachedLabelItem]

    static func build(from batch: CampusRenderBatch, palette: MapCanvasPalette) -> CampusMapLabelCache {
        var ordered: [CampusCachedLabelItem] = []
        var byId: [String: CampusCachedLabelItem] = [:]

        for item in batch.items {
            guard item.layer == .building || item.layer == .campusZone || item.layer == .room || item.layer == .poi else {
                continue
            }
            guard !item.primaryLabel.isEmpty else { continue }

            let primaryFontSize: CGFloat = item.layer == .room ? 10 : 12
            let primaryText = Text(item.primaryLabel)
                .font(.system(size: primaryFontSize, weight: .semibold, design: .rounded))
                .foregroundStyle(palette.labelColor)
            let primarySize = estimateTextSize(
                item.primaryLabel,
                fontSize: primaryFontSize,
                isBold: true
            )

            let secText: Text?
            let secSize: CGSize
            if item.layer == .building && !item.secondaryLabel.isEmpty {
                secText = Text(item.secondaryLabel)
                    .font(.system(size: 10, weight: .regular))
                    .foregroundStyle(palette.secondaryLabelColor)
                secSize = estimateTextSize(item.secondaryLabel, fontSize: 10, isBold: false)
            } else {
                secText = nil
                secSize = .zero
            }

            let entry = CampusCachedLabelItem(
                featureId: item.id,
                layer: item.layer,
                labelX: item.labelX,
                labelY: item.labelY,
                bboxMinX: item.bboxMinX,
                bboxMaxX: item.bboxMaxX,
                minZoom: item.minZoom,
                primaryText: primaryText,
                primarySize: primarySize,
                secondaryText: secText,
                secondarySize: secSize
            )
            ordered.append(entry)
            byId[item.id] = entry
        }

        ordered.sort { labelPriority($0.layer) < labelPriority($1.layer) }
        return CampusMapLabelCache(orderedLabels: ordered, byId: byId)
    }

    private static func labelPriority(_ layer: CampusMapLayer) -> Int {
        switch layer {
        case .poi: return 0
        case .room: return 1
        case .building: return 2
        case .campusZone: return 3
        default: return 4
        }
    }

    private static func estimateTextSize(
        _ string: String,
        fontSize: CGFloat,
        isBold: Bool
    ) -> CGSize {
        #if canImport(UIKit)
        let weight: UIFont.Weight = isBold ? .semibold : .regular
        let font = UIFont.systemFont(ofSize: fontSize, weight: weight)
        let measured = (string as NSString).size(withAttributes: [.font: font])
        return CGSize(width: ceil(measured.width) + 4, height: ceil(measured.height) + 2)
        #else
        return CGSize(
            width: CGFloat(string.count) * fontSize * 0.62 + 4,
            height: fontSize * 1.35 + 2
        )
        #endif
    }
}
