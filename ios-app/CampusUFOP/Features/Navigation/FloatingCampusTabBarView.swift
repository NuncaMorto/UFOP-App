import SwiftUI

// MARK: - Barra de Navegação Flutuante Tonal/Liquid Glass (4 Abas + Avatar/Iniciais + Mapa)

public struct FloatingCampusTabBarView: View {
    @Binding public var selectedTab: CampusTab
    public let accent: Color
    public let onPrimary: Color
    public let studentName: String
    public let highContrast: Bool
    public let reduceMotion: Bool
    public let minTouch: CGFloat
    public let axis: Axis
    public let onBeforeSelectMap: (() -> Void)?

    @Environment(\.colorScheme) private var colorScheme
    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Namespace private var tabHighlightNamespace

    public init(
        selectedTab: Binding<CampusTab>,
        accent: Color,
        onPrimary: Color = .white,
        studentName: String = "",
        highContrast: Bool = false,
        reduceMotion: Bool = false,
        minTouch: CGFloat = 48,
        axis: Axis = .horizontal,
        onBeforeSelectMap: (() -> Void)? = nil
    ) {
        self._selectedTab = selectedTab
        self.accent = accent
        self.onPrimary = onPrimary
        self.studentName = studentName
        self.highContrast = highContrast
        self.reduceMotion = reduceMotion
        self.minTouch = minTouch
        self.axis = axis
        self.onBeforeSelectMap = onBeforeSelectMap
    }

    private var activeAccentColor: CampusAccentColor {
        appearance?.accentColor ?? .red
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
                if tab == .profile {
                    profileAvatarBadge(isSelected: isSelected)
                } else {
                    Image(systemName: isSelected ? tab.selectedSystemImage : tab.systemImage)
                        .font(.system(size: 18, weight: isSelected ? .semibold : .medium))
                        .scaleEffect(isSelected && !reduceMotion ? 1.08 : 1.0)
                        .animation(reduceMotion ? nil : .spring(response: 0.30, dampingFraction: 0.80), value: isSelected)
                }

                Text(tab.title)
                    .font(CampusTheme.font(size: 10, weight: isSelected ? .bold : .medium))
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

    @ViewBuilder
    private func profileAvatarBadge(isSelected: Bool) -> some View {
        let initials = Self.extractInitials(from: studentName)
        let fg = foregroundColor(isSelected: isSelected)
        if initials.isEmpty {
            Image(systemName: isSelected ? "person.crop.circle.fill" : "person.crop.circle")
                .font(.system(size: 19, weight: isSelected ? .semibold : .medium))
        } else {
            ZStack {
                Circle()
                    .fill(
                        isSelected
                            ? activeAccentColor.primary(for: colorScheme).opacity(0.18)
                            : activeAccentColor.surface2(for: colorScheme)
                    )
                Circle()
                    .strokeBorder(
                        isSelected
                            ? fg
                            : CampusTheme.mutedText(for: colorScheme, highContrast: highContrast).opacity(0.45),
                        lineWidth: isSelected ? 1.8 : 1.0
                    )
                Text(initials)
                    .font(CampusTheme.font(size: 10, weight: .bold))
                    .foregroundStyle(fg)
            }
            .frame(width: 24, height: 24)
        }
    }

    private static func extractInitials(from rawName: String) -> String {
        let parts = rawName
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .split(whereSeparator: { $0.isWhitespace })
        guard let first = parts.first else { return "" }
        if parts.count == 1 {
            return String(first.prefix(2)).uppercased()
        }
        guard let last = parts.last, let f = first.first, let l = last.first else {
            return String(first.prefix(2)).uppercased()
        }
        return "\(f)\(l)".uppercased()
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

                Image(systemName: isMapSelected ? CampusTab.map.selectedSystemImage : CampusTab.map.systemImage)
                    .font(.system(size: 21, weight: isMapSelected ? .bold : .semibold))
                    .foregroundStyle(foregroundColor(isSelected: isMapSelected))
                    .scaleEffect(isMapSelected && !reduceMotion ? 1.08 : 1.0)
                    .animation(reduceMotion ? nil : .spring(response: 0.30, dampingFraction: 0.80), value: isMapSelected)
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
        return activeAccentColor.surface2(for: colorScheme)
    }

    private func foregroundColor(isSelected: Bool) -> Color {
        if isSelected {
            if highContrast {
                return onPrimary
            }
            return colorScheme == .dark ? activeAccentColor.highlight(for: .dark) : accent
        }
        return CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast)
    }

    private func selectTab(_ tab: CampusTab) {
        if reduceMotion {
            selectedTab = tab
        } else {
            withAnimation(.spring(response: 0.30, dampingFraction: 0.80)) {
                selectedTab = tab
            }
        }
    }
}

private struct FloatingBarGlassSurface<S: InsettableShape>: ViewModifier {
    let shape: S
    let highContrast: Bool

    @Environment(\.colorScheme) private var colorScheme
    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?

    private var activeAccentColor: CampusAccentColor {
        appearance?.accentColor ?? .red
    }

    @ViewBuilder
    func body(content: Content) -> some View {
        if highContrast {
            content
                .background(shape.fill(CampusTheme.cardBackground(for: colorScheme, highContrast: true, accent: activeAccentColor)))
                .overlay(shape.strokeBorder(CampusTheme.cardBorder(for: colorScheme, highContrast: true), lineWidth: 2))
        } else {
            #if compiler(>=6.2)
            if #available(iOS 26.0, *) {
                content
                    .background(
                        shape.fill(activeAccentColor.surface1(for: colorScheme).opacity(0.92))
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
                shape.fill(activeAccentColor.surface1(for: colorScheme).opacity(0.96))
            )
            .shadow(color: CampusTheme.cardShadow(for: colorScheme), radius: 12, x: 0, y: 5)
    }
}

#Preview("FloatingCampusTabBarView - Horizontal") {
    ZStack(alignment: .bottom) {
        LiquidGlassBackdrop(accent: .red, highContrast: false)
        FloatingCampusTabBarView(
            selectedTab: .constant(.agenda),
            accent: CampusAccentColor.red.primary(for: .light),
            studentName: "Ana Carolina"
        )
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }
}

#Preview("FloatingCampusTabBarView - Vertical") {
    ZStack(alignment: .leading) {
        LiquidGlassBackdrop(accent: .red, highContrast: false)
        FloatingCampusTabBarView(
            selectedTab: .constant(.agenda),
            accent: CampusAccentColor.red.primary(for: .light),
            studentName: "Ana Carolina",
            axis: .vertical
        )
        .padding(.leading, 12)
    }
}
