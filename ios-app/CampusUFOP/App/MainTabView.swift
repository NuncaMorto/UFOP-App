import SwiftUI

public enum CampusTab: Hashable, CaseIterable, Sendable {
    case agenda
    case disciplines
    case activities
    case profile

    public var title: String {
        switch self {
        case .agenda: return "Agenda"
        case .disciplines: return "Disciplinas"
        case .activities: return "Atividades"
        case .profile: return "Perfil"
        }
    }

    public var systemImage: String {
        switch self {
        case .agenda: return "calendar"
        case .disciplines: return "book.closed.fill"
        case .activities: return "checklist"
        case .profile: return "person.crop.circle"
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

    public var body: some View {
        let accent = (appearance?.accentColor ?? .green).primary(for: colorScheme)

        Group {
            if #available(iOS 18.0, *) {
                TabView(selection: activeTabBinding) {
                    Tab(CampusTab.agenda.title, systemImage: CampusTab.agenda.systemImage, value: CampusTab.agenda) {
                        AgendaView(viewModel: resolvedAgendaViewModel)
                    }

                    Tab(CampusTab.disciplines.title, systemImage: CampusTab.disciplines.systemImage, value: CampusTab.disciplines) {
                        DisciplinesView(viewModel: resolvedDisciplinesViewModel)
                    }

                    Tab(CampusTab.activities.title, systemImage: CampusTab.activities.systemImage, value: CampusTab.activities) {
                        ActivitiesView()
                    }

                    Tab(CampusTab.profile.title, systemImage: CampusTab.profile.systemImage, value: CampusTab.profile) {
                        ProfileView()
                    }
                }
                .tabViewStyle(.sidebarAdaptable)
            } else {
                legacyAdaptiveContainer
            }
        }
        .tint(accent)
        .background(keyboardShortcutsLayer)
    }

    @ViewBuilder
    private var legacyAdaptiveContainer: some View {
        if horizontalSizeClass == .regular {
            NavigationSplitView {
                List(CampusTab.allCases, id: \.self, selection: Binding<CampusTab?>(
                    get: { activeTabBinding.wrappedValue },
                    set: { newValue in
                        if let newValue {
                            activeTabBinding.wrappedValue = newValue
                        }
                    }
                )) { tab in
                    Label(tab.title, systemImage: tab.systemImage)
                        .tag(tab)
                }
                .navigationTitle("Vida Acadêmica")
            } detail: {
                tabDetailContent(for: activeTabBinding.wrappedValue)
            }
        } else {
            TabView(selection: activeTabBinding) {
                AgendaView(viewModel: resolvedAgendaViewModel)
                    .tabItem {
                        Label(CampusTab.agenda.title, systemImage: CampusTab.agenda.systemImage)
                    }
                    .tag(CampusTab.agenda)

                DisciplinesView(viewModel: resolvedDisciplinesViewModel)
                    .tabItem {
                        Label(CampusTab.disciplines.title, systemImage: CampusTab.disciplines.systemImage)
                    }
                    .tag(CampusTab.disciplines)

                ActivitiesView()
                    .tabItem {
                        Label(CampusTab.activities.title, systemImage: CampusTab.activities.systemImage)
                    }
                    .tag(CampusTab.activities)

                ProfileView()
                    .tabItem {
                        Label(CampusTab.profile.title, systemImage: CampusTab.profile.systemImage)
                    }
                    .tag(CampusTab.profile)
            }
        }
    }

    @ViewBuilder
    private func tabDetailContent(for tab: CampusTab) -> some View {
        switch tab {
        case .agenda:
            AgendaView(viewModel: resolvedAgendaViewModel)
        case .disciplines:
            DisciplinesView(viewModel: resolvedDisciplinesViewModel)
        case .activities:
            ActivitiesView()
        case .profile:
            ProfileView()
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
        }
        .frame(width: 0, height: 0)
        .opacity(0)
        .accessibilityHidden(true)
        #else
        EmptyView()
        #endif
    }

    private var resolvedAgendaViewModel: AgendaViewModel {
        if let agendaViewModel { return agendaViewModel }
        let vm = AgendaViewModel(store: store)
        DispatchQueue.main.async {
            self.agendaViewModel = vm
        }
        return vm
    }

    private var resolvedDisciplinesViewModel: DisciplinesViewModel {
        if let disciplinesViewModel { return disciplinesViewModel }
        let vm = DisciplinesViewModel(store: store)
        DispatchQueue.main.async {
            self.disciplinesViewModel = vm
        }
        return vm
    }
}

#Preview("MainTabView") {
    MainTabView()
        .environment(CampusStore())
        .environment(CampusAppearanceController())
        .environment(AppLockManager())
}
