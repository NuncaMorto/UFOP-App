import SwiftUI

// MARK: - Renderizador Canvas 2D Offline Nativo (iOS / SwiftUI)

public struct CampusMapCanvasView: View {
    @Bindable public var bridge: CampusMapBridge
    public let showBuiltInHud: Bool

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    public init(bridge: CampusMapBridge, showBuiltInHud: Bool = true) {
        self.bridge = bridge
        self.showBuiltInHud = showBuiltInHud
    }

    private var hc: Bool { appearance?.accessibility.highContrastMode ?? false }
    private var reduceMotion: Bool { appearance?.accessibility.reduceMotion ?? false }
    private var minTouch: CGFloat { appearance?.accessibility.minTouchHeight ?? 48 }
    private var accentColor: Color { (appearance?.accentColor ?? .green).primary(for: colorScheme) }

    public var body: some View {
        let palette = MapCanvasPalette.resolve(for: colorScheme, highContrast: hc)

        ZStack(alignment: .bottom) {
            CampusMapInteractiveCanvasView(
                renderBatch: bridge.renderBatch,
                geometryRevision: bridge.geometryRevision,
                selectedFeatureId: bridge.selectedFeature?.id,
                isFreeRoomsModeEnabled: bridge.isFreeRoomsModeEnabled,
                activePoiFilter: bridge.activePoiFilter,
                pendingFocus: bridge.pendingFocus,
                palette: palette,
                accentColor: accentColor,
                isDark: colorScheme == .dark,
                reduceMotion: reduceMotion,
                onTapMap: { mapX, mapY, zoom in
                    bridge.onMapTap(mapX: mapX, mapY: mapY, zoom: zoom)
                },
                onConsumePendingFocus: {
                    bridge.consumePendingFocus()
                }
            )

            if showBuiltInHud {
                MapHudOverlayView(
                    floors: bridge.availableFloorsForUi(),
                    activeFloor: bridge.activeFloor,
                    selectedFeature: bridge.selectedFeature,
                    selectedOccupancy: bridge.selectedOccupancy,
                    isNativeAvailable: bridge.isNativeAvailable && !bridge.isLoading,
                    errorMessage: bridge.errorMessage,
                    accentColor: accentColor,
                    minTouch: minTouch,
                    highContrast: hc,
                    onSelectFloor: { bridge.selectFloor($0) },
                    onClearSelection: { bridge.clearSelection() }
                )
            }
        }
    }
}

private struct CampusMapInteractiveCanvasView: View {
    let renderBatch: CampusRenderBatch?
    let geometryRevision: Int
    let selectedFeatureId: String?
    let isFreeRoomsModeEnabled: Bool
    let activePoiFilter: CampusPoiType?
    let pendingFocus: CampusCameraFocus?
    let palette: MapCanvasPalette
    let accentColor: Color
    let isDark: Bool
    let reduceMotion: Bool
    let onTapMap: (Float, Float, Float) -> Void
    let onConsumePendingFocus: () -> Void

    @State private var transform = CampusMapViewportTransform()
    @State private var lastDragTranslation: CGSize = .zero
    @State private var lastMagnification: CGFloat = 1.0
    @State private var viewportSize: CGSize = CGSize(width: 390, height: 640)
    @State private var cachedGeometryRevision: Int = -1
    @State private var cachedGeometry: CampusMapGeometryCache? = nil
    @State private var cachedLabelRevision: Int = -1
    @State private var cachedLabelIsDark: Bool? = nil
    @State private var cachedLabels: CampusMapLabelCache? = nil
    @State private var hasAppliedInitialFit: Bool = false

    var body: some View {
        GeometryReader { geo in
            Canvas { context, size in
                drawBatch(in: context, size: size)
            }
            .background(palette.background)
            .contentShape(Rectangle())
            .gesture(combinedPanZoomGesture)
            .simultaneousGesture(
                SpatialTapGesture()
                    .onEnded { value in
                        let (mx, my) = CampusMapTransformMath.screenToMap(
                            screenPoint: value.location,
                            transform: transform
                        )
                        onTapMap(mx, my, Float(transform.zoomPtPerMeter))
                    }
            )
            .onAppear {
                viewportSize = geo.size
                rebuildGeometryCacheIfNeeded()
                rebuildLabelCacheIfNeeded()
                applyInitialFitOrPendingFocus()
            }
            .onChange(of: geo.size) { _, newSize in
                viewportSize = newSize
                applyInitialFitOrPendingFocus()
            }
            .onChange(of: geometryRevision) { _, _ in
                rebuildGeometryCacheIfNeeded()
                rebuildLabelCacheIfNeeded()
                applyInitialFitOrPendingFocus()
            }
            .onChange(of: isDark) { _, _ in
                rebuildLabelCacheIfNeeded()
            }
            .onChange(of: pendingFocus) { _, newFocus in
                if let focus = newFocus, viewportSize.width > 10, viewportSize.height > 10 {
                    hasAppliedInitialFit = true
                    animateToFocus(focus)
                    onConsumePendingFocus()
                }
            }
        }
    }

    private var combinedPanZoomGesture: some Gesture {
        DragGesture(minimumDistance: 2)
            .onChanged { value in
                let deltaWidth = value.translation.width - lastDragTranslation.width
                let deltaHeight = value.translation.height - lastDragTranslation.height
                lastDragTranslation = value.translation
                transform = CampusMapViewportTransform(
                    offsetX: transform.offsetX + deltaWidth,
                    offsetY: transform.offsetY + deltaHeight,
                    zoomPtPerMeter: transform.zoomPtPerMeter
                )
            }
            .onEnded { _ in
                lastDragTranslation = .zero
            }
            .simultaneously(
                with: MagnifyGesture()
                    .onChanged { value in
                        let zoomFactor = lastMagnification > 0 ? (value.magnification / lastMagnification) : 1.0
                        lastMagnification = value.magnification
                        transform = CampusMapTransformMath.applyPanAndZoom(
                            current: transform,
                            panDelta: .zero,
                            zoomFactor: zoomFactor,
                            centroid: value.startLocation
                        )
                    }
                    .onEnded { _ in
                        lastMagnification = 1.0
                    }
            )
    }

    private func animateToFocus(_ focus: CampusCameraFocus) {
        let target = CampusMapTransformMath.computeCameraTarget(
            bounds: focus.bounds,
            viewportSize: viewportSize
        )
        if reduceMotion {
            transform = target
        } else {
            withAnimation(.easeInOut(duration: 0.35)) {
                transform = target
            }
        }
    }

    private func applyInitialFitOrPendingFocus() {
        guard viewportSize.width > 10, viewportSize.height > 10 else { return }

        if let focus = pendingFocus {
            let target = CampusMapTransformMath.computeCameraTarget(
                bounds: focus.bounds,
                viewportSize: viewportSize
            )
            if !hasAppliedInitialFit || reduceMotion {
                transform = target
            } else {
                withAnimation(.easeInOut(duration: 0.35)) {
                    transform = target
                }
            }
            hasAppliedInitialFit = true
            onConsumePendingFocus()
            return
        }

        guard !hasAppliedInitialFit, let batch = renderBatch else { return }
        transform = CampusMapTransformMath.computeCameraTarget(
            bounds: batch.bounds,
            viewportSize: viewportSize
        )
        hasAppliedInitialFit = true
    }

    private func rebuildGeometryCacheIfNeeded() {
        guard cachedGeometryRevision != geometryRevision || (renderBatch != nil && cachedGeometry == nil) else {
            return
        }
        cachedGeometryRevision = geometryRevision
        guard let batch = renderBatch else {
            cachedGeometry = nil
            return
        }
        cachedGeometry = CampusMapGeometryCache.build(from: batch)
    }

    private func rebuildLabelCacheIfNeeded() {
        guard cachedLabelRevision != geometryRevision || cachedLabelIsDark != isDark || (renderBatch != nil && cachedLabels == nil) else {
            return
        }
        cachedLabelRevision = geometryRevision
        cachedLabelIsDark = isDark
        guard let batch = renderBatch else {
            cachedLabels = nil
            return
        }
        cachedLabels = CampusMapLabelCache.build(from: batch, palette: palette)
    }

    private func drawBatch(in context: GraphicsContext, size: CGSize) {
        guard let geometry = cachedGeometry else { return }
        let zoom = CampusMapTransformMath.clampZoom(transform.zoomPtPerMeter)
        let visibleRect = CampusMapTransformMath.visibleMapRect(
            viewportSize: size,
            transform: transform,
            marginPt: 48
        )
        let matrix = CGAffineTransform(
            a: zoom,
            b: 0,
            c: 0,
            d: zoom,
            tx: transform.offsetX,
            ty: transform.offsetY
        )
        let batchItems = renderBatch?.items

        context.drawLayer { layerCtx in
            layerCtx.concatenate(matrix)

            if let ground = geometry.groundBatch {
                drawStaticLayerBatch(ground, in: &layerCtx, visibleRect: visibleRect, zoom: zoom)
            }
            drawDynamicVectorItems(
                geometry.groundOverflow,
                in: &layerCtx,
                batchItems: batchItems,
                visibleRect: visibleRect,
                zoom: zoom
            )

            drawDynamicVectorItems(
                geometry.campusZones,
                in: &layerCtx,
                batchItems: batchItems,
                visibleRect: visibleRect,
                zoom: zoom
            )

            for staticBatch in geometry.overlayStaticBatches {
                drawStaticLayerBatch(staticBatch, in: &layerCtx, visibleRect: visibleRect, zoom: zoom)
            }
            drawDynamicVectorItems(
                geometry.overlayStaticOverflow,
                in: &layerCtx,
                batchItems: batchItems,
                visibleRect: visibleRect,
                zoom: zoom
            )

            if let wPath = geometry.walkwayPath,
               let wBounds = geometry.walkwayBounds,
               CampusMapTransformMath.rectsIntersect(wBounds, visibleRect) {
                layerCtx.stroke(
                    wPath,
                    with: .color(palette.walkwayStroke),
                    style: StrokeStyle(
                        lineWidth: max(1.0 / zoom, 1.4),
                        lineCap: .round,
                        lineJoin: .round
                    )
                )
            }
            drawDynamicVectorItems(
                geometry.walkwayOverflow,
                in: &layerCtx,
                batchItems: batchItems,
                visibleRect: visibleRect,
                zoom: zoom
            )

            drawDynamicVectorItems(
                geometry.foregroundItems,
                in: &layerCtx,
                batchItems: batchItems,
                visibleRect: visibleRect,
                zoom: zoom
            )
        }

        // POIs pontuais e rótulos em coordenadas de tela após o drawLayer vetorial.
        drawVisiblePointItems(
            geometry.foregroundItems,
            in: context,
            visibleRect: visibleRect,
            zoom: zoom
        )

        if let labelCache = cachedLabels {
            drawVisibleLabels(
                labelCache: labelCache,
                in: context,
                viewportSize: size,
                visibleRect: visibleRect,
                zoom: zoom
            )
        }
    }

    private func drawStaticLayerBatch(
        _ staticBatch: CampusStaticLayerBatch,
        in layerCtx: inout GraphicsContext,
        visibleRect: CampusMapRect2D,
        zoom: CGFloat
    ) {
        guard CampusMapTransformMath.rectsIntersect(staticBatch.bounds, visibleRect) else { return }
        let isHighlightedParking = activePoiFilter == .parking && staticBatch.layer == .parking
        let fill: Color = isHighlightedParking
            ? accentColor.opacity(isDark ? 0.34 : 0.22)
            : fillColor(for: staticBatch.layer, occupancy: .none)
        layerCtx.fill(staticBatch.path, with: .color(fill), style: FillStyle(eoFill: false))

        let stroke: Color = isHighlightedParking ? accentColor : strokeColor(for: staticBatch.layer)
        let lineWidth: CGFloat = (isHighlightedParking ? 2.2 : 1.3) / zoom
        layerCtx.stroke(staticBatch.path, with: .color(stroke), lineWidth: lineWidth)
    }

    private func drawDynamicVectorItems(
        _ items: [CampusCachedDynamicItem],
        in layerCtx: inout GraphicsContext,
        batchItems: [CampusRenderFeature]?,
        visibleRect: CampusMapRect2D,
        zoom: CGFloat
    ) {
        for entry in items {
            guard Float(zoom) >= entry.minZoom else { continue }
            guard entry.geomKind != .point else { continue }
            guard CampusMapTransformMath.bboxIntersects(
                minX: entry.bboxMinX,
                minY: entry.bboxMinY,
                maxX: entry.bboxMaxX,
                maxY: entry.bboxMaxY,
                viewport: visibleRect
            ) else {
                continue
            }
            guard let path = entry.path else { continue }

            if entry.geomKind == .polyline {
                layerCtx.stroke(
                    path,
                    with: .color(palette.walkwayStroke),
                    style: StrokeStyle(
                        lineWidth: max(1.0 / zoom, 1.4),
                        lineCap: .round,
                        lineJoin: .round
                    )
                )
                continue
            }

            let isSelected = selectedFeatureId == entry.id
            let matchesPoi = activePoiFilter.map { matchesPoiFilter(entry, filter: $0) } ?? false
            let occupancy = (batchItems != nil && entry.featureIndex < batchItems!.count)
                ? batchItems![entry.featureIndex].occupancy
                : .none

            let fill = resolvePolygonFill(
                layer: entry.layer,
                occupancy: occupancy,
                isSelected: isSelected,
                matchesPoi: matchesPoi
            )
            layerCtx.fill(path, with: .color(fill), style: FillStyle(eoFill: true))

            let stroke = resolvePolygonStroke(
                layer: entry.layer,
                occupancy: occupancy,
                isSelected: isSelected,
                matchesPoi: matchesPoi
            )
            let screenLineWidth: CGFloat = {
                if isSelected { return 2.8 }
                if isFreeRoomsModeEnabled && entry.layer == .room && occupancy == .noRegularClass {
                    return 2.0
                }
                if matchesPoi { return 2.2 }
                return entry.layer == .room ? 1.0 : 1.3
            }()
            let metricLineWidth = screenLineWidth / zoom

            if entry.isMock && entry.layer == .room {
                layerCtx.stroke(
                    path,
                    with: .color(stroke),
                    style: StrokeStyle(
                        lineWidth: metricLineWidth,
                        dash: [5.0 / zoom, 3.5 / zoom]
                    )
                )
            } else {
                layerCtx.stroke(path, with: .color(stroke), lineWidth: metricLineWidth)
            }
        }
    }

    private func drawVisiblePointItems(
        _ items: [CampusCachedDynamicItem],
        in context: GraphicsContext,
        visibleRect: CampusMapRect2D,
        zoom: CGFloat
    ) {
        let pointMarginM = Float(18.0 / zoom)
        for entry in items where entry.geomKind == .point {
            guard Float(zoom) >= entry.minZoom else { continue }
            guard CampusMapTransformMath.pointInRect(
                x: entry.pointX,
                y: entry.pointY,
                rect: visibleRect,
                marginM: pointMarginM
            ) else {
                continue
            }

            let isSelected = selectedFeatureId == entry.id
            let matchesPoi = activePoiFilter.map { matchesPoiFilter(entry, filter: $0) } ?? false
            let pt = CampusMapTransformMath.mapToScreen(
                mapX: CGFloat(entry.pointX),
                mapY: CGFloat(entry.pointY),
                transform: transform
            )

            if matchesPoi {
                let haloRadius: CGFloat = isSelected ? 14 : 11.5
                let halo = Path(ellipseIn: CGRect(
                    x: pt.x - haloRadius,
                    y: pt.y - haloRadius,
                    width: haloRadius * 2,
                    height: haloRadius * 2
                ))
                context.fill(halo, with: .color(accentColor.opacity(0.28)))
            }

            let radius: CGFloat = isSelected ? 8.0 : (matchesPoi ? 7.0 : 5.5)
            let dot = Path(ellipseIn: CGRect(
                x: pt.x - radius,
                y: pt.y - radius,
                width: radius * 2,
                height: radius * 2
            ))
            let dotFill = matchesPoi ? accentColor : palette.poiFill
            let dotStroke: Color = isSelected ? palette.selectedStroke : .white
            context.fill(dot, with: .color(dotFill))
            context.stroke(dot, with: .color(dotStroke), lineWidth: isSelected ? 2.5 : 1.5)
        }
    }

    private func drawVisibleLabels(
        labelCache: CampusMapLabelCache,
        in context: GraphicsContext,
        viewportSize: CGSize,
        visibleRect: CampusMapRect2D,
        zoom: CGFloat
    ) {
        var collisionChecker = CampusLabelCollisionChecker()

        if let selectedFeatureId, let selectedEntry = labelCache.byId[selectedFeatureId] {
            tryDrawLabel(
                selectedEntry,
                in: context,
                viewportSize: viewportSize,
                visibleRect: visibleRect,
                zoom: zoom,
                collisionChecker: &collisionChecker
            )
        }

        for entry in labelCache.orderedLabels {
            if entry.featureId == selectedFeatureId { continue }
            tryDrawLabel(
                entry,
                in: context,
                viewportSize: viewportSize,
                visibleRect: visibleRect,
                zoom: zoom,
                collisionChecker: &collisionChecker
            )
        }
    }

    private func tryDrawLabel(
        _ entry: CampusCachedLabelItem,
        in context: GraphicsContext,
        viewportSize: CGSize,
        visibleRect: CampusMapRect2D,
        zoom: CGFloat,
        collisionChecker: inout CampusLabelCollisionChecker
    ) {
        guard Float(zoom) >= entry.minZoom else { return }
        guard CampusMapTransformMath.pointInRect(x: entry.labelX, y: entry.labelY, rect: visibleRect) else {
            return
        }

        if entry.layer == .room {
            let fits = CampusMapTransformMath.shouldShowSecondaryLabel(
                bboxMinX: entry.bboxMinX,
                bboxMaxX: entry.bboxMaxX,
                zoomPtPerMeter: zoom,
                textWidthPt: entry.primarySize.width
            )
            if !fits { return }
        }

        let pt = CampusMapTransformMath.mapToScreen(
            mapX: CGFloat(entry.labelX),
            mapY: CGFloat(entry.labelY),
            transform: transform
        )
        let primaryRect = CGRect(
            x: pt.x - entry.primarySize.width * 0.5,
            y: pt.y - entry.primarySize.height * 0.5,
            width: entry.primarySize.width,
            height: entry.primarySize.height
        )
        guard primaryRect.maxX >= 0,
              primaryRect.minX <= viewportSize.width,
              primaryRect.maxY >= 0,
              primaryRect.minY <= viewportSize.height else {
            return
        }
        guard collisionChecker.tryPlace(primaryRect, padding: 4) else { return }

        let resolvedPrimary = context.resolve(entry.primaryText)
        context.draw(resolvedPrimary, at: pt, anchor: .center)

        if entry.layer == .building,
           let secText = entry.secondaryText,
           CampusMapTransformMath.shouldShowSecondaryLabel(
               bboxMinX: entry.bboxMinX,
               bboxMaxX: entry.bboxMaxX,
               zoomPtPerMeter: zoom,
               textWidthPt: entry.secondarySize.width
           ) {
            let secCenter = CGPoint(x: pt.x, y: pt.y + entry.primarySize.height * 0.85)
            let secRect = CGRect(
                x: secCenter.x - entry.secondarySize.width * 0.5,
                y: secCenter.y - entry.secondarySize.height * 0.5,
                width: entry.secondarySize.width,
                height: entry.secondarySize.height
            )
            if secRect.maxX >= 0,
               secRect.minX <= viewportSize.width,
               secRect.maxY >= 0,
               secRect.minY <= viewportSize.height,
               collisionChecker.tryPlace(secRect, padding: 4) {
                let resolvedSec = context.resolve(secText)
                context.draw(resolvedSec, at: secCenter, anchor: .center)
            }
        }
    }

    private func matchesPoiFilter(_ item: CampusCachedDynamicItem, filter: CampusPoiType) -> Bool {
        if item.poiType == filter { return true }
        switch filter {
        case .library: return item.categoryLower == "library"
        case .dining: return item.categoryLower == "dining"
        case .parking: return item.layer == .parking || item.categoryLower == "parking"
        case .facultyOffice: return item.categoryLower == "faculty_office"
        case .academic:
            return (item.layer == .building || item.layer == .campusZone || item.layer == .room)
                && (item.categoryLower == "academic"
                    || item.categoryLower == "academic_zone"
                    || item.categoryLower == "classroom"
                    || item.categoryLower == "lab")
        case .toilet, .entrance:
            return false
        }
    }

    private func resolvePolygonFill(
        layer: CampusMapLayer,
        occupancy: CampusRenderOccupancyKind,
        isSelected: Bool,
        matchesPoi: Bool
    ) -> Color {
        if isSelected && (layer == .room || layer == .building || layer == .campusZone) {
            return palette.selectedFill
        }
        if matchesPoi && layer != .poi {
            return accentColor.opacity(isDark ? 0.34 : 0.22)
        }
        if isFreeRoomsModeEnabled && layer == .room {
            if occupancy == .noRegularClass {
                return isDark
                    ? Color(hex: 0x16A34A, alpha: 0.80)
                    : Color(hex: 0x86EFAC, alpha: 0.92)
            }
            return palette.roomNoDataFill.opacity(0.45)
        }
        return fillColor(for: layer, occupancy: occupancy)
    }

    private func fillColor(
        for layer: CampusMapLayer,
        occupancy: CampusRenderOccupancyKind
    ) -> Color {
        switch layer {
        case .campusGround: return palette.campusGroundFill
        case .campusZone: return palette.campusZoneFill
        case .greenArea: return palette.greenAreaFill
        case .parking: return palette.parkingFill
        case .sports: return palette.sportsFill
        case .walkway: return .clear
        case .building: return palette.buildingFill
        case .room:
            switch occupancy {
            case .occupied: return palette.roomOccupiedFill
            case .noRegularClass: return palette.roomFreeFill
            case .noData: return palette.roomNoDataFill
            case .outOfTerm: return palette.roomOutOfTermFill
            case .none: return palette.buildingFill
            }
        case .poi: return palette.poiFill
        }
    }

    private func resolvePolygonStroke(
        layer: CampusMapLayer,
        occupancy: CampusRenderOccupancyKind,
        isSelected: Bool,
        matchesPoi: Bool
    ) -> Color {
        if isSelected { return palette.selectedStroke }
        if matchesPoi { return accentColor }
        if isFreeRoomsModeEnabled && layer == .room {
            if occupancy == .noRegularClass {
                return isDark ? Color(hex: 0x4ADE80) : Color(hex: 0x15803D)
            }
            return palette.roomStroke.opacity(0.42)
        }
        return strokeColor(for: layer)
    }

    private func strokeColor(for layer: CampusMapLayer) -> Color {
        switch layer {
        case .campusGround: return palette.campusGroundStroke
        case .campusZone: return palette.campusZoneStroke
        case .building: return palette.buildingStroke
        case .room: return palette.roomStroke
        default: return palette.buildingStroke.opacity(0.45)
        }
    }
}
