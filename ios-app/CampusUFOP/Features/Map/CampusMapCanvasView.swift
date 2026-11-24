import SwiftUI

// MARK: - Renderizador Canvas 2D Offline Nativo (iOS / SwiftUI)

private struct CachedMapPathItem: Identifiable {
    let feature: CampusRenderFeature
    let path: Path
    var id: String { feature.id }
}

private struct MapCanvasPalette {
    let background: Color
    let campusGroundFill: Color
    let campusGroundStroke: Color
    let campusZoneFill: Color
    let campusZoneStroke: Color
    let greenAreaFill: Color
    let parkingFill: Color
    let sportsFill: Color
    let walkwayStroke: Color
    let buildingFill: Color
    let buildingStroke: Color
    let roomOccupiedFill: Color
    let roomFreeFill: Color
    let roomNoDataFill: Color
    let roomOutOfTermFill: Color
    let selectedFill: Color
    let roomStroke: Color
    let selectedStroke: Color
    let poiFill: Color
    let labelColor: Color
    let secondaryLabelColor: Color

    static func resolve(for scheme: ColorScheme, highContrast: Bool) -> MapCanvasPalette {
        if scheme == .dark {
            return MapCanvasPalette(
                background: highContrast ? Color(hex: 0x050607) : Color(hex: 0x0E1218),
                campusGroundFill: Color(hex: 0x151A22),
                campusGroundStroke: Color(hex: 0x2F3846),
                campusZoneFill: Color(hex: 0x1B2330),
                campusZoneStroke: Color(hex: 0x3E4C63),
                greenAreaFill: Color(hex: 0x163122),
                parkingFill: Color(hex: 0x242A35),
                sportsFill: Color(hex: 0x1A2E38),
                walkwayStroke: Color(hex: 0x3A4352),
                buildingFill: highContrast ? Color(hex: 0x202630) : Color(hex: 0x252C37),
                buildingStroke: highContrast ? Color.white.opacity(0.85) : Color(hex: 0x526075),
                roomOccupiedFill: Color(hex: 0x7A2727, alpha: 0.72),
                roomFreeFill: Color(hex: 0x1F5C3A, alpha: 0.65),
                roomNoDataFill: Color(hex: 0x353E4D, alpha: 0.75),
                roomOutOfTermFill: Color(hex: 0x2A303B, alpha: 0.72),
                selectedFill: Color(hex: 0x1E3A5F, alpha: 0.72),
                roomStroke: highContrast ? Color.white : Color(hex: 0x94A3B8),
                selectedStroke: Color(hex: 0x60A5FA),
                poiFill: Color(hex: 0xF59E0B),
                labelColor: highContrast ? .white : Color(hex: 0xF1F5F9),
                secondaryLabelColor: highContrast ? Color(hex: 0xE2E8F0) : Color(hex: 0xCBD5E1)
            )
        }
        return MapCanvasPalette(
            background: highContrast ? .white : Color(hex: 0xF5F3EE),
            campusGroundFill: Color(hex: 0xECEAE2),
            campusGroundStroke: Color(hex: 0xC8C3B8),
            campusZoneFill: Color(hex: 0xE4E8EE),
            campusZoneStroke: Color(hex: 0x9AA5B4),
            greenAreaFill: Color(hex: 0xD3E6CE),
            parkingFill: Color(hex: 0xDCDFE4),
            sportsFill: Color(hex: 0xCEE3EB),
            walkwayStroke: Color(hex: 0xB8B5AA),
            buildingFill: highContrast ? Color(hex: 0xEFEFEF) : Color(hex: 0xFFFFFF),
            buildingStroke: highContrast ? .black : Color(hex: 0x64748B),
            roomOccupiedFill: Color(hex: 0xFAD2CF),
            roomFreeFill: Color(hex: 0xCBEAD3),
            roomNoDataFill: Color(hex: 0xE2E8F0),
            roomOutOfTermFill: Color(hex: 0xECEFF3),
            selectedFill: Color(hex: 0xD6E4FF),
            roomStroke: highContrast ? .black : Color(hex: 0x475569),
            selectedStroke: Color(hex: 0x1D4ED8),
            poiFill: Color(hex: 0xD97706),
            labelColor: highContrast ? .black : Color(hex: 0x0F172A),
            secondaryLabelColor: highContrast ? Color(hex: 0x1F2937) : Color(hex: 0x475569)
        )
    }
}

public struct CampusMapCanvasView: View {
    @Bindable public var bridge: CampusMapBridge

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    @State private var transform = CampusMapViewportTransform()
    @State private var lastDragTranslation: CGSize = .zero
    @State private var lastMagnification: CGFloat = 1.0
    @State private var viewportSize: CGSize = CGSize(width: 390, height: 640)
    @State private var cachedBatchKey: String = ""
    @State private var cachedPaths: [CachedMapPathItem] = []
    @State private var hasAppliedInitialFit: Bool = false

    public init(bridge: CampusMapBridge) {
        self.bridge = bridge
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
                    applyInitialFitIfNeeded()
                }
                .onChange(of: geo.size) { _, newSize in
                    viewportSize = newSize
                    applyInitialFitIfNeeded()
                }
                .onChange(of: batchCacheToken) { _, _ in
                    rebuildPathCacheIfNeeded()
                    applyInitialFitIfNeeded()
                }
                .onChange(of: bridge.pendingFocus) { _, newFocus in
                    if let focus = newFocus {
                        animateToFocus(focus)
                        bridge.consumePendingFocus()
                    }
                }
            }

            VStack(spacing: 10) {
                HStack(alignment: .top) {
                    if !floorItems.isEmpty {
                        floorSelectorBar(floors: floorItems)
                    }
                    Spacer()
                }
                .padding(.horizontal, 12)

                Spacer()

                if let selected = bridge.selectedFeature {
                    HStack {
                        selectedFeatureCard(feature: selected)
                            .frame(maxWidth: 460)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 12)
                    .padding(.bottom, 12)
                } else if !bridge.isNativeAvailable {
                    HStack {
                        unavailablePlaceholderCard
                            .frame(maxWidth: 460)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 12)
                    .padding(.bottom, 12)
                }
            }
            .padding(.top, 12)
        }
    }

    private var batchCacheToken: String {
        guard let b = bridge.renderBatch else { return "empty" }
        return "\(b.campusId)|\(b.activeFloor)|\(b.items.count)|\(bridge.timestampIso ?? "")"
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

    private func applyInitialFitIfNeeded() {
        guard !hasAppliedInitialFit,
              viewportSize.width > 10,
              viewportSize.height > 10,
              let batch = bridge.renderBatch else {
            return
        }
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

            switch item.geomKind {
            case .polygon:
                let screenPath = entry.path.applying(matrix)
                let fill = fillColor(for: item, isSelected: isSelected, palette: palette)
                context.fill(screenPath, with: .color(fill), style: FillStyle(eoFill: true))

                let stroke = isSelected ? palette.selectedStroke : strokeColor(for: item, palette: palette)
                let lineWidth: CGFloat = isSelected ? 2.5 : (item.layer == .room ? 1.0 : 1.3)
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
                let radius: CGFloat = isSelected ? 7.5 : 5.5
                let dot = Path(ellipseIn: CGRect(
                    x: pt.x - radius,
                    y: pt.y - radius,
                    width: radius * 2,
                    height: radius * 2
                ))
                context.fill(dot, with: .color(palette.poiFill))
                context.stroke(dot, with: .color(.white), lineWidth: 1.5)
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

    private func fillColor(for item: CampusRenderFeature, isSelected: Bool, palette: MapCanvasPalette) -> Color {
        if isSelected && (item.layer == .room || item.layer == .building || item.layer == .campusZone) {
            return palette.selectedFill
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

    private func strokeColor(for item: CampusRenderFeature, palette: MapCanvasPalette) -> Color {
        switch item.layer {
        case .campusGround: return palette.campusGroundStroke
        case .campusZone: return palette.campusZoneStroke
        case .building: return palette.buildingStroke
        case .room: return palette.roomStroke
        default: return palette.buildingStroke.opacity(0.45)
        }
    }

    private func floorSelectorBar(floors: [CampusFloorLabel]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(floors) { item in
                    let isSelected = item.floor == bridge.activeFloor
                    Button {
                        bridge.selectFloor(item.floor)
                    } label: {
                        Text(item.label)
                            .font(.system(size: 13, weight: isSelected ? .bold : .medium, design: .rounded))
                            .foregroundStyle(
                                isSelected
                                    ? accentColor
                                    : CampusTheme.primaryText(for: colorScheme, highContrast: hc)
                            )
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .minimumTouchTarget(minTouch)
                            .liquidGlassCapsule(
                                selected: isSelected,
                                accent: accentColor,
                                interactive: true,
                                highContrast: hc
                            )
                    }
                    .buttonStyle(.plain)
                    .campusHoverLift()
                }
            }
        }
    }

    private func selectedFeatureCard(feature: CampusFeatureInfo) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .center, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(feature.name)
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    if let floor = feature.floor {
                        Text("Andar: \(floor == 0 ? "Térreo / 1º Pav" : "\(floor)º Pav")")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    }
                }
                Spacer()
                if feature.isMock || feature.indoorStatus == .provisional {
                    Text("Layout esquemático")
                        .font(.system(size: 11, weight: .semibold, design: .rounded))
                        .foregroundStyle(CampusTheme.weatherBadgeText(for: colorScheme))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(
                            Capsule(style: .continuous)
                                .fill(CampusTheme.weatherBadgeBackground(for: colorScheme))
                        )
                }
                Button {
                    bridge.clearSelection()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
                .campusHoverLift()
                .accessibilityLabel("Fechar detalhes do local")
            }

            if let occ = bridge.selectedOccupancy {
                Text(occ.displayTitle)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(accentColor)
            } else if let notes = feature.notes, !notes.isEmpty {
                Text(notes)
                    .font(.system(size: 12))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
            }
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: 18, tint: accentColor, interactive: false, highContrast: hc)
    }

    private var unavailablePlaceholderCard: some View {
        Text(bridge.errorMessage ?? "Motor vetorial offline pronto para integração nativa (XCFramework).")
            .font(.system(size: 12, weight: .medium))
            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .liquidGlassCard(cornerRadius: 16, interactive: false, highContrast: hc)
    }
}
