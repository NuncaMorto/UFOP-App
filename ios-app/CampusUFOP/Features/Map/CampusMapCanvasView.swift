import SwiftUI

// MARK: - Renderizador Canvas 2D Offline Nativo (iOS / SwiftUI)

private struct CachedMapPathItem: Identifiable {
    let feature: CampusRenderFeature
    let path: Path
    var id: String { feature.id }
}

public struct CampusMapCanvasView: View {
    @Bindable public var bridge: CampusMapBridge
    public let showBuiltInHud: Bool

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    @State private var transform = CampusMapViewportTransform()
    @State private var lastDragTranslation: CGSize = .zero
    @State private var lastMagnification: CGFloat = 1.0
    @State private var viewportSize: CGSize = CGSize(width: 390, height: 640)
    @State private var cachedBatchKey: String = ""
    @State private var cachedPaths: [CachedMapPathItem] = []
    @State private var hasAppliedInitialFit: Bool = false

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
        let floorItems = bridge.availableFloorsForUi()

        ZStack(alignment: .bottom) {
            GeometryReader { geo in
                Canvas { context, _ in
                    drawBatch(in: context, palette: palette)
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
                            _ = bridge.handleTap(
                                mapX: mx,
                                mapY: my,
                                zoom: Float(transform.zoomPtPerMeter)
                            )
                        }
                )
                .onAppear {
                    viewportSize = geo.size
                    rebuildPathCacheIfNeeded()
                    applyInitialFitOrPendingFocus()
                }
                .onChange(of: geo.size) { _, newSize in
                    viewportSize = newSize
                    applyInitialFitOrPendingFocus()
                }
                .onChange(of: batchCacheToken) { _, _ in
                    rebuildPathCacheIfNeeded()
                    applyInitialFitOrPendingFocus()
                }
                .onChange(of: bridge.pendingFocus) { _, newFocus in
                    if let focus = newFocus, viewportSize.width > 10, viewportSize.height > 10 {
                        hasAppliedInitialFit = true
                        animateToFocus(focus)
                        bridge.consumePendingFocus()
                    }
                }
            }

            if showBuiltInHud {
                MapHudOverlayView(
                    floors: floorItems,
                    activeFloor: bridge.activeFloor,
                    selectedFeature: bridge.selectedFeature,
                    selectedOccupancy: bridge.selectedOccupancy,
                    isNativeAvailable: bridge.isNativeAvailable,
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

    private var batchCacheToken: String {
        guard let b = bridge.renderBatch else { return "empty" }
        return "\(b.campusId)|\(b.activeFloor)|\(b.items.count)|\(bridge.renderBatchRevision)|\(bridge.timestampIso ?? "")"
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

    public func animateToFocus(_ focus: CampusCameraFocus) {
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

        if let focus = bridge.pendingFocus {
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
            bridge.consumePendingFocus()
            return
        }

        guard !hasAppliedInitialFit, let batch = bridge.renderBatch else { return }
        transform = CampusMapTransformMath.computeCameraTarget(
            bounds: batch.bounds,
            viewportSize: viewportSize
        )
        hasAppliedInitialFit = true
    }

    private func rebuildPathCacheIfNeeded() {
        let token = batchCacheToken
        guard token != cachedBatchKey else { return }
        cachedBatchKey = token
        guard let batch = bridge.renderBatch else {
            cachedPaths = []
            return
        }
        cachedPaths = batch.items.map { item in
            CachedMapPathItem(
                feature: item,
                path: buildLocalPath(item: item, batch: batch)
            )
        }
    }

    private func buildLocalPath(item: CampusRenderFeature, batch: CampusRenderBatch) -> Path {
        var path = Path()
        guard item.geomKind != .point else { return path }
        let startRing = Int(item.ringStartIdx)
        let endRing = min(startRing + Int(item.ringCount), batch.ringStarts.count)
        guard startRing < endRing else { return path }

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
        return path
    }

    private func drawBatch(in context: GraphicsContext, palette: MapCanvasPalette) {
        let zoom = transform.zoomPtPerMeter
        let freeMode = bridge.isFreeRoomsModeEnabled
        let poiFilter = bridge.activePoiFilter
        let matrix = CGAffineTransform(
            a: zoom,
            b: 0,
            c: 0,
            d: zoom,
            tx: transform.offsetX,
            ty: transform.offsetY
        )

        for entry in cachedPaths {
            let item = entry.feature
            guard Float(zoom) >= item.minZoom else { continue }
            let isSelected = bridge.selectedFeature?.id == item.id
            let matchesPoi = poiFilter.map { matchesPoiFilter(item, filter: $0) } ?? false

            switch item.geomKind {
            case .polygon:
                let screenPath = entry.path.applying(matrix)
                let fill = fillColor(
                    for: item,
                    isSelected: isSelected,
                    freeMode: freeMode,
                    matchesPoi: matchesPoi,
                    palette: palette
                )
                context.fill(screenPath, with: .color(fill), style: FillStyle(eoFill: true))

                let stroke = strokeColor(
                    for: item,
                    isSelected: isSelected,
                    freeMode: freeMode,
                    matchesPoi: matchesPoi,
                    palette: palette
                )
                let lineWidth: CGFloat = {
                    if isSelected { return 2.8 }
                    if freeMode && item.layer == .room && item.occupancy == .noRegularClass { return 2.0 }
                    if matchesPoi { return 2.2 }
                    return item.layer == .room ? 1.0 : 1.3
                }()
                if item.isMock && item.layer == .room {
                    context.stroke(
                        screenPath,
                        with: .color(stroke),
                        style: StrokeStyle(lineWidth: lineWidth, dash: [5, 3.5])
                    )
                } else {
                    context.stroke(screenPath, with: .color(stroke), lineWidth: lineWidth)
                }

            case .polyline:
                let screenPath = entry.path.applying(matrix)
                context.stroke(
                    screenPath,
                    with: .color(palette.walkwayStroke),
                    style: StrokeStyle(lineWidth: max(1.0, zoom * 1.4), lineCap: .round, lineJoin: .round)
                )

            case .point:
                let pt = CampusMapTransformMath.mapToScreen(
                    mapX: CGFloat(item.pointX),
                    mapY: CGFloat(item.pointY),
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

        // Rótulos em tamanho fixo de fonte (sem escalar no zoom), ocultando secundários menores que o polígono.
        for entry in cachedPaths {
            let item = entry.feature
            guard Float(zoom) >= item.minZoom else { continue }
            guard item.layer == .building || item.layer == .campusZone || item.layer == .room || item.layer == .poi else {
                continue
            }
            guard !item.primaryLabel.isEmpty else { continue }

            let pt = CampusMapTransformMath.mapToScreen(
                mapX: CGFloat(item.labelX),
                mapY: CGFloat(item.labelY),
                transform: transform
            )

            let primaryText = Text(item.primaryLabel)
                .font(.system(size: item.layer == .room ? 10 : 12, weight: .semibold, design: .rounded))
                .foregroundStyle(palette.labelColor)
            let resolvedPrimary = context.resolve(primaryText)
            let primarySize = resolvedPrimary.measure(in: CGSize(width: 220, height: 40))

            if item.layer == .room {
                let fits = CampusMapTransformMath.shouldShowSecondaryLabel(
                    bboxMinX: item.bboxMinX,
                    bboxMaxX: item.bboxMaxX,
                    zoomPtPerMeter: zoom,
                    textWidthPt: primarySize.width
                )
                if !fits { continue }
            }

            context.draw(resolvedPrimary, at: pt, anchor: .center)

            if item.layer == .building && !item.secondaryLabel.isEmpty {
                let secText = Text(item.secondaryLabel)
                    .font(.system(size: 10, weight: .regular))
                    .foregroundStyle(palette.secondaryLabelColor)
                let resolvedSec = context.resolve(secText)
                let secSize = resolvedSec.measure(in: CGSize(width: 240, height: 32))
                if CampusMapTransformMath.shouldShowSecondaryLabel(
                    bboxMinX: item.bboxMinX,
                    bboxMaxX: item.bboxMaxX,
                    zoomPtPerMeter: zoom,
                    textWidthPt: secSize.width
                ) {
                    context.draw(
                        resolvedSec,
                        at: CGPoint(x: pt.x, y: pt.y + primarySize.height * 0.85),
                        anchor: .center
                    )
                }
            }
        }
    }

    private func matchesPoiFilter(_ item: CampusRenderFeature, filter: CampusPoiType) -> Bool {
        if item.poiType == filter { return true }
        let cat = item.category.lowercased()
        switch filter {
        case .library: return cat == "library"
        case .dining: return cat == "dining"
        case .parking: return item.layer == .parking || cat == "parking"
        case .facultyOffice: return cat == "faculty_office"
        case .academic:
            return (item.layer == .building || item.layer == .campusZone || item.layer == .room)
                && (cat == "academic" || cat == "academic_zone" || cat == "classroom" || cat == "lab")
        case .toilet, .entrance:
            return false
        }
    }

    private func fillColor(
        for item: CampusRenderFeature,
        isSelected: Bool,
        freeMode: Bool,
        matchesPoi: Bool,
        palette: MapCanvasPalette
    ) -> Color {
        if isSelected && (item.layer == .room || item.layer == .building || item.layer == .campusZone) {
            return palette.selectedFill
        }
        if matchesPoi && item.layer != .poi {
            return accentColor.opacity(colorScheme == .dark ? 0.34 : 0.22)
        }
        switch item.layer {
        case .campusGround: return palette.campusGroundFill
        case .campusZone: return palette.campusZoneFill
        case .greenArea: return palette.greenAreaFill
        case .parking: return palette.parkingFill
        case .sports: return palette.sportsFill
        case .walkway: return .clear
        case .building: return palette.buildingFill
        case .room:
            if freeMode {
                if item.occupancy == .noRegularClass {
                    return colorScheme == .dark
                        ? Color(hex: 0x16A34A, alpha: 0.80)
                        : Color(hex: 0x86EFAC, alpha: 0.92)
                }
                return palette.roomNoDataFill.opacity(0.45)
            }
            switch item.occupancy {
            case .occupied: return palette.roomOccupiedFill
            case .noRegularClass: return palette.roomFreeFill
            case .noData: return palette.roomNoDataFill
            case .outOfTerm: return palette.roomOutOfTermFill
            case .none: return palette.buildingFill
            }
        case .poi: return palette.poiFill
        }
    }

    private func strokeColor(
        for item: CampusRenderFeature,
        isSelected: Bool,
        freeMode: Bool,
        matchesPoi: Bool,
        palette: MapCanvasPalette
    ) -> Color {
        if isSelected { return palette.selectedStroke }
        if matchesPoi { return accentColor }
        if freeMode && item.layer == .room {
            if item.occupancy == .noRegularClass {
                return colorScheme == .dark ? Color(hex: 0x4ADE80) : Color(hex: 0x15803D)
            }
            return palette.roomStroke.opacity(0.42)
        }
        switch item.layer {
        case .campusGround: return palette.campusGroundStroke
        case .campusZone: return palette.campusZoneStroke
        case .building: return palette.buildingStroke
        case .room: return palette.roomStroke
        default: return palette.buildingStroke.opacity(0.45)
        }
    }
}
