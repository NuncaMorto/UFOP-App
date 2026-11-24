import SwiftUI

// MARK: - Barra de Navegação Flutuante Liquid Glass (4 Abas + Botão Circular do Mapa)

public struct FloatingCampusTabBarView: View {
    @Binding public var selectedTab: CampusTab
    public let accent: Color
    public let onPrimary: Color
    public let highContrast: Bool
    public let reduceMotion: Bool
    public let minTouch: CGFloat
    public let axis: Axis
    public let onBeforeSelectMap: (() -> Void)?

    @Environment(\.colorScheme) private var colorScheme
    @Namespace private var tabHighlightNamespace

    public init(
        selectedTab: Binding<CampusTab>,
        accent: Color,
        onPrimary: Color = .white,
        highContrast: Bool = false,
        reduceMotion: Bool = false,
        minTouch: CGFloat = 48,
        axis: Axis = .horizontal,
        onBeforeSelectMap: (() -> Void)? = nil
    ) {
        self._selectedTab = selectedTab
        self.accent = accent
        self.onPrimary = onPrimary
        self.highContrast = highContrast
        self.reduceMotion = reduceMotion
        self.minTouch = minTouch
        self.axis = axis
        self.onBeforeSelectMap = onBeforeSelectMap
    }

    public var body: some View {
        LiquidGlassGroup(spacing: 10) {
            if axis == .vertical {
                VStack(spacing: 10) {
                    verticalPrimaryTabsCapsule

                    mapFloatingCircleButton
                }
            } else {
                HStack(spacing: 10) {
                    primaryTabsCapsule

                    mapFloatingCircleButton
                }
            }
        }
        .accessibilityElement(children: .contain)
    }

    // MARK: - Cápsula Principal (Agenda, Disciplinas, Atividades, Perfil)

    private var primaryTabsCapsule: some View {
        HStack(spacing: 2) {
            ForEach(CampusTab.primaryTabs, id: \.self) { tab in
                tabItemButton(for: tab)
            }
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 6)
        .frame(height: 64)
        .frame(maxWidth: .infinity)
        .modifier(FloatingBarGlassSurface(shape: Capsule(style: .continuous), highContrast: highContrast))
    }

    private var verticalPrimaryTabsCapsule: some View {
        VStack(spacing: 4) {
            ForEach(CampusTab.primaryTabs, id: \.self) { tab in
                tabItemButton(for: tab)
                    .frame(height: 58)
            }
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 6)
        .frame(width: 72)
        .modifier(FloatingBarGlassSurface(shape: Capsule(style: .continuous), highContrast: highContrast))
    }

    private func tabItemButton(for tab: CampusTab) -> some View {
        let isSelected = selectedTab == tab
        return Button {
            selectTab(tab)
        } label: {
            VStack(spacing: 3) {
                Image(systemName: isSelected ? tab.selectedSystemImage : tab.systemImage)
                    .font(.system(size: 18, weight: isSelected ? .semibold : .medium))

                Text(tab.title)
                    .font(.system(size: 10, weight: isSelected ? .bold : .medium, design: .rounded))
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                    .allowsTightening(true)
            }
            .foregroundStyle(foregroundColor(isSelected: isSelected))
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background {
                if isSelected {
                    Capsule(style: .continuous)
                        .fill(highlightFillColor)
                        .matchedGeometryEffect(id: "floatingTabHighlight", in: tabHighlightNamespace)
                }
            }
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .campusHoverLift()
        .accessibilityLabel(tab.title)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }

    // MARK: - Botão Circular Flutuante Dedicado ao Mapa

    private var mapFloatingCircleButton: some View {
        let isMapSelected = selectedTab == .map

        return Button {
            onBeforeSelectMap?()
            selectTab(.map)
        } label: {
            ZStack {
                if isMapSelected {
                    Circle()
                        .fill(highlightFillColor)
                        .padding(6)
                        .matchedGeometryEffect(id: "floatingTabHighlight", in: tabHighlightNamespace)
                }

                Image(systemName: isMapSelected ? "map.fill" : "map")
                    .font(.system(size: 21, weight: isMapSelected ? .bold : .semibold))
                    .foregroundStyle(foregroundColor(isSelected: isMapSelected))
            }
            .frame(width: 64, height: 64)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .campusHoverLift()
        .modifier(FloatingBarGlassSurface(shape: Circle(), highContrast: highContrast))
        .accessibilityLabel("Mapa do campus")
        .accessibilityAddTraits(isMapSelected ? [.isSelected] : [])
    }

    private var highlightFillColor: Color {
        if highContrast {
            return accent
        }
        return accent.opacity(colorScheme == .dark ? 0.28 : 0.16)
    }

    private func foregroundColor(isSelected: Bool) -> Color {
        if isSelected {
            return highContrast ? onPrimary : accent
        }
        return CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast)
    }

    private func selectTab(_ tab: CampusTab) {
        if reduceMotion {
            selectedTab = tab
        } else {
            withAnimation(.spring(response: 0.30, dampingFraction: 0.84)) {
                selectedTab = tab
            }
        }
    }
}

private struct FloatingBarGlassSurface<S: InsettableShape>: ViewModifier {
    let shape: S
    let highContrast: Bool

    @Environment(\.colorScheme) private var colorScheme

    @ViewBuilder
    func body(content: Content) -> some View {
        if highContrast {
            content
                .background(shape.fill(CampusTheme.cardBackground(for: colorScheme, highContrast: true)))
                .overlay(shape.strokeBorder(CampusTheme.cardBorder(for: colorScheme, highContrast: true), lineWidth: 2))
        } else {
            #if compiler(>=6.2)
            if #available(iOS 26.0, *) {
                content
                    .background(
                        shape.fill(
                            CampusTheme.cardBackground(for: colorScheme)
                                .opacity(colorScheme == .dark ? 0.46 : 0.62)
                        )
                    )
                    .glassEffect(.regular.interactive(), in: shape)
            } else {
                fallbackSurface(content: content)
            }
            #else
            fallbackSurface(content: content)
            #endif
        }
    }

    private func fallbackSurface(content: Content) -> some View {
        content
            .background(
                ZStack {
                    shape.fill(.ultraThinMaterial)
                    shape.fill(
                        CampusTheme.cardBackground(for: colorScheme)
                            .opacity(colorScheme == .dark ? 0.54 : 0.66)
                    )
                }
            )
            .overlay(
                shape.strokeBorder(
                    LinearGradient(
                        colors: [
                            Color.white.opacity(colorScheme == .dark ? 0.26 : 0.76),
                            CampusTheme.cardBorder(for: colorScheme)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 1
                )
            )
            .shadow(color: CampusTheme.cardShadow(for: colorScheme), radius: 12, x: 0, y: 6)
    }
}

#Preview("FloatingCampusTabBarView - Horizontal") {
    ZStack(alignment: .bottom) {
        LiquidGlassBackdrop(accent: .green, highContrast: false)
        FloatingCampusTabBarView(
            selectedTab: .constant(.agenda),
            accent: CampusAccentColor.green.primary(for: .light)
        )
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }
}

#Preview("FloatingCampusTabBarView - Vertical") {
    ZStack(alignment: .leading) {
        LiquidGlassBackdrop(accent: .green, highContrast: false)
        FloatingCampusTabBarView(
            selectedTab: .constant(.agenda),
            accent: CampusAccentColor.green.primary(for: .light),
            axis: .vertical
        )
        .padding(.leading, 12)
    }
}
