import SwiftUI

// MARK: - Tela Completa do Mapa 2D Offline (HUD Modular sem Bloquear Pan/Zoom)

public struct CampusMapScreenView: View {
    @Bindable public var bridge: CampusMapBridge

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var isSearchResultsExpanded: Bool = false

    public init(bridge: CampusMapBridge) {
        self.bridge = bridge
    }

    private var isRegular: Bool { horizontalSizeClass == .regular }
    private var hc: Bool { appearance?.accessibility.highContrastMode ?? false }
    private var minTouch: CGFloat { appearance?.accessibility.minTouchHeight ?? 48 }
    private var accentColor: Color { (appearance?.accentColor ?? .green).primary(for: colorScheme) }
    private var bottomBarInset: CGFloat { isRegular ? 16 : 88 }
    private var leadingBarInset: CGFloat { isRegular ? 96 : 12 }

    private var activeFloorDisplayLabel: String {
        let floors = bridge.availableFloorsForUi()
        if let match = floors.first(where: { $0.floor == bridge.activeFloor }) {
            return match.label
        }
        return bridge.activeFloor == 0 ? "Térreo" : "\(bridge.activeFloor)º Pav"
    }

    public var body: some View {
        ZStack {
            CampusMapCanvasView(bridge: bridge, showBuiltInHud: false)
                .ignoresSafeArea()

            VStack(alignment: .leading, spacing: 0) {
                topHud
                    .frame(maxWidth: isRegular ? 520 : .infinity, alignment: .leading)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, leadingBarInset)
            .padding(.trailing, 12)
            .padding(.top, 8)

            VStack(alignment: .leading, spacing: 0) {
                Spacer(minLength: 0)
                bottomHud
                    .frame(maxWidth: isRegular ? 520 : .infinity, alignment: .leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, leadingBarInset)
            .padding(.trailing, 12)
            .padding(.bottom, bottomBarInset)
        }
        .onDisappear {
            bridge.cancelPendingBackgroundWork()
        }
    }

    // MARK: - HUD Superior (Busca, Campus, Filtros, Resultados & Aviso de Resolução)

    private var topHud: some View {
        VStack(alignment: .leading, spacing: 8) {
            CampusMapSearchAndFiltersView(
                bridge: bridge,
                accentColor: accentColor,
                minTouch: minTouch,
                highContrast: hc,
                isResultsExpanded: $isSearchResultsExpanded
            )

            if let notice = bridge.resolutionNotice, !notice.isEmpty {
                resolutionNoticeBanner(notice)
            }
        }
    }

    private func resolutionNoticeBanner(_ text: String) -> some View {
        HStack(alignment: .center, spacing: 8) {
            Image(systemName: "info.circle.fill")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(accentColor)

            Text(text)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 4)

            Button {
                bridge.clearResolutionNotice()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 16))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
            }
            .buttonStyle(.plain)
            .frame(minWidth: 32, minHeight: 32)
            .contentShape(Rectangle())
            .accessibilityLabel("Fechar aviso do mapa")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .liquidGlassCard(cornerRadius: 16, tint: accentColor, interactive: false, highContrast: hc)
    }

    // MARK: - HUD Inferior (Andares, Resumo/Detalhes e Atribuição Discreta)

    private var bottomHud: some View {
        let floorItems = bridge.availableFloorsForUi()

        return VStack(alignment: .leading, spacing: 8) {
            if !floorItems.isEmpty {
                MapFloorSelectorBar(
                    floors: floorItems,
                    activeFloor: bridge.activeFloor,
                    accentColor: accentColor,
                    minTouch: minTouch,
                    highContrast: hc,
                    onSelectFloor: { bridge.selectFloor($0) }
                )
            }

            if let selected = bridge.selectedFeature {
                CampusMapDetailSheetView(
                    feature: selected,
                    occupancy: bridge.selectedOccupancy,
                    buildingOccupancy: bridge.selectedBuildingOccupancy,
                    activeFloor: bridge.activeFloor,
                    accentColor: accentColor,
                    minTouch: minTouch,
                    highContrast: hc,
                    onFocusRoom: { bridge.focusFeature($0) },
                    onSelectFloor: { bridge.selectFloor($0) },
                    onDismiss: { bridge.clearSelection() }
                )
            } else if bridge.isFreeRoomsModeEnabled && bridge.isNativeAvailable {
                CampusMapFreeRoomsSummaryView(
                    floorLabel: activeFloorDisplayLabel,
                    summary: bridge.floorOccupancySummary,
                    freeRooms: bridge.freeRoomsOnActiveFloor,
                    accentColor: accentColor,
                    minTouch: minTouch,
                    highContrast: hc,
                    onSelectRoom: { bridge.focusFeature($0) },
                    onDismiss: { bridge.setFreeRoomsMode(false) }
                )
            } else if !bridge.isNativeAvailable && !bridge.isLoading {
                MapUnavailablePlaceholderCard(
                    errorMessage: bridge.errorMessage,
                    highContrast: hc
                )
            }

            if let attribution = bridge.renderBatch?.attribution, !attribution.isEmpty {
                Text(attribution)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(
                        Capsule(style: .continuous)
                            .fill(CampusTheme.cardBackground(for: colorScheme, highContrast: hc).opacity(0.72))
                    )
            }
        }
    }
}

#Preview("CampusMapScreenView") {
    CampusMapScreenView(bridge: CampusMapBridge())
        .environment(CampusAppearanceController())
}
