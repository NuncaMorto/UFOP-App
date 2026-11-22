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
        .tint(accent)
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
