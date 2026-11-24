import SwiftUI

public enum CampusTab: Hashable, CaseIterable, Sendable {
    case agenda
    case disciplines
    case activities
    case profile
    case map

    public static let primaryTabs: [CampusTab] = [.agenda, .disciplines, .activities, .profile]

    public var title: String {
        switch self {
        case .agenda: return "Agenda"
        case .disciplines: return "Disciplinas"
        case .activities: return "Atividades"
        case .profile: return "Perfil"
        case .map: return "Mapa"
        }
    }

    public var systemImage: String {
        switch self {
        case .agenda: return "calendar"
        case .disciplines: return "book.closed.fill"
        case .activities: return "checklist"
        case .profile: return "person.crop.circle"
        case .map: return "map"
        }
    }

    public var selectedSystemImage: String {
        switch self {
        case .map: return "map.fill"
        default: return systemImage
        }
    }
}

public struct MainTabView: View {
    @Environment(CampusStore.self) private var store
    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @Binding private var externalSelectedTab: CampusTab
    @State private var fallbackSelectedTab: CampusTab = .agenda
    private let usesExternalBinding: Bool

    @State private var agendaViewModel: AgendaViewModel?
    @State private var disciplinesViewModel: DisciplinesViewModel?
    @State private var mapBridge: CampusMapBridge?

    public init() {
        self._externalSelectedTab = .constant(.agenda)
        self.usesExternalBinding = false
    }

    public init(selectedTab: Binding<CampusTab>) {
        self._externalSelectedTab = selectedTab
        self.usesExternalBinding = true
    }

    private var activeTabBinding: Binding<CampusTab> {
        usesExternalBinding ? $externalSelectedTab : $fallbackSelectedTab
    }

    private var hc: Bool {
        appearance?.accessibility.highContrastMode ?? false
    }

    private var reduceMotion: Bool {
        appearance?.accessibility.reduceMotion ?? false
    }

    private var minTouch: CGFloat {
        appearance?.accessibility.minTouchHeight ?? 48
    }

    public var body: some View {
        let accentObj = appearance?.accentColor ?? .green
        let accent = accentObj.primary(for: colorScheme)
        let onPrimary = accentObj.onPrimary(for: colorScheme)

        Group {
            if horizontalSizeClass == .regular {
                NavigationSplitView {
                    List(CampusTab.allCases, id: \.self, selection: Binding<CampusTab?>(
                        get: { activeTabBinding.wrappedValue },
                        set: { newValue in
                            if let newValue {
                                if newValue == .map {
                                    _ = ensureMapBridge()
                                }
                                activeTabBinding.wrappedValue = newValue
                            }
                        }
                    )) { tab in
                        Label(tab.title, systemImage: activeTabBinding.wrappedValue == tab ? tab.selectedSystemImage : tab.systemImage)
                            .tag(tab)
                    }
                    .navigationTitle("Vida Acadêmica")
                } detail: {
                    tabDetailContent(for: activeTabBinding.wrappedValue)
                }
            } else {
                ZStack(alignment: .bottom) {
                    tabDetailContent(for: activeTabBinding.wrappedValue)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)

                    FloatingCampusTabBarView(
                        selectedTab: activeTabBinding,
                        accent: accent,
                        onPrimary: onPrimary,
                        highContrast: hc,
                        reduceMotion: reduceMotion,
                        minTouch: minTouch,
                        onBeforeSelectMap: {
                            _ = ensureMapBridge()
                        }
                    )
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)
                }
                .ignoresSafeArea(.keyboard, edges: .bottom)
            }
        }
        .tint(accent)
        .background(keyboardShortcutsLayer)
        .onAppear {
            ensureCoreViewModels()
            if activeTabBinding.wrappedValue == .map {
                _ = ensureMapBridge()
            }
        }
        .onChange(of: activeTabBinding.wrappedValue) { _, newTab in
            if newTab == .map {
                _ = ensureMapBridge()
            }
        }
    }

    @ViewBuilder
    private func tabDetailContent(for tab: CampusTab) -> some View {
        switch tab {
        case .agenda:
            AgendaView(
                viewModel: agendaViewModel ?? AgendaViewModel(store: store),
                onOpenLocationInMap: openMeetingInMap
            )
        case .disciplines:
            DisciplinesView(
                viewModel: disciplinesViewModel ?? DisciplinesViewModel(store: store),
                onOpenLocationInMap: openMeetingInMap
            )
        case .activities:
            ActivitiesView()
        case .profile:
            ProfileView()
        case .map:
            if let mapBridge {
                CampusMapScreenView(bridge: mapBridge)
            } else {
                LiquidGlassBackdrop(accent: appearance?.accentColor ?? .green, highContrast: hc)
            }
        }
    }

    @ViewBuilder
    private var keyboardShortcutsLayer: some View {
        #if os(iOS)
        VStack(spacing: 0) {
            Button("") { activeTabBinding.wrappedValue = .agenda }
                .keyboardShortcut("1", modifiers: .command)
            Button("") { activeTabBinding.wrappedValue = .disciplines }
                .keyboardShortcut("2", modifiers: .command)
            Button("") { activeTabBinding.wrappedValue = .activities }
                .keyboardShortcut("3", modifiers: .command)
            Button("") { activeTabBinding.wrappedValue = .profile }
                .keyboardShortcut("4", modifiers: .command)
            Button("") {
                _ = ensureMapBridge()
                activeTabBinding.wrappedValue = .map
            }
            .keyboardShortcut("5", modifiers: .command)
        }
        .frame(width: 0, height: 0)
        .opacity(0)
        .accessibilityHidden(true)
        #else
        EmptyView()
        #endif
    }

    private func ensureCoreViewModels() {
        if agendaViewModel == nil {
            agendaViewModel = AgendaViewModel(store: store)
        }
        if disciplinesViewModel == nil {
            disciplinesViewModel = DisciplinesViewModel(store: store)
        }
    }

    @discardableResult
    private func ensureMapBridge() -> CampusMapBridge {
        if let existing = mapBridge {
            return existing
        }
        let bridge = CampusMapBridge()
        let initialCampusId = mappedCampusEngineId(from: store.activeCampus)
        if initialCampusId != bridge.activeCampusId {
            bridge.selectCampus(initialCampusId)
            bridge.consumePendingFocus()
        }
        mapBridge = bridge
        return bridge
    }

    private func mappedCampusEngineId(from campus: UfopCampusInfo) -> String {
        let joined = "\(campus.id) \(campus.name) \(campus.city)".lowercased()
        if joined.contains("icea") || joined.contains("monlevade") {
            return "JM_ICEA"
        }
        if joined.contains("icsa") {
            return "MA_ICSA"
        }
        if joined.contains("ichs") || joined.contains("mariana") {
            return "MA_ICHS"
        }
        return "OP_MC"
    }

    public func openMeetingInMap(_ request: CampusMapMeetingRequest) {
        let bridge = ensureMapBridge()
        _ = bridge.resolveMeeting(
            courseCode: request.courseCode,
            section: request.section,
            weekday: request.weekday,
            startTime: request.startTime,
            fallbackBuildingRaw: request.fallbackBuildingRaw
        )
        if reduceMotion {
            activeTabBinding.wrappedValue = .map
        } else {
            withAnimation(.spring(response: 0.30, dampingFraction: 0.84)) {
                activeTabBinding.wrappedValue = .map
            }
        }
    }
}

#Preview("MainTabView") {
    MainTabView()
        .environment(CampusStore())
        .environment(CampusAppearanceController())
        .environment(AppLockManager())
}
