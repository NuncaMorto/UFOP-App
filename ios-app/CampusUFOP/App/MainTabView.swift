import SwiftUI

public enum CampusTab: Hashable, CaseIterable, Sendable {
    case agenda
    case disciplines
    case activities
    case profile

    public var title: String {
        switch self {
        case .agenda:
            return "Agenda"
        case .disciplines:
            return "Disciplinas"
        case .activities:
            return "Atividades"
        case .profile:
            return "Perfil"
        }
    }

    public var systemImage: String {
        switch self {
        case .agenda:
            return "calendar"
        case .disciplines:
            return "book.closed.fill"
        case .activities:
            return "checklist"
        case .profile:
            return "person.crop.circle"
        }
    }
}

public struct MainTabView: View {
    @Environment(CampusStore.self) private var store
    @State private var selectedTab: CampusTab = .agenda
    @State private var agendaViewModel: AgendaViewModel?
    @State private var disciplinesViewModel: DisciplinesViewModel?

    public init() {}

    public var body: some View {
        TabView(selection: $selectedTab) {
            AgendaView(
                viewModel: resolvedAgendaViewModel
            )
            .tabItem {
                Label(CampusTab.agenda.title, systemImage: CampusTab.agenda.systemImage)
            }
            .tag(CampusTab.agenda)

            DisciplinesView(
                viewModel: resolvedDisciplinesViewModel
            )
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
        .tint(CampusTheme.mathBlue)
    }

    private var resolvedAgendaViewModel: AgendaViewModel {
        if let agendaViewModel {
            return agendaViewModel
        }
        let vm = AgendaViewModel(store: store)
        DispatchQueue.main.async {
            self.agendaViewModel = vm
        }
        return vm
    }

    private var resolvedDisciplinesViewModel: DisciplinesViewModel {
        if let disciplinesViewModel {
            return disciplinesViewModel
        }
        let vm = DisciplinesViewModel(store: store)
        DispatchQueue.main.async {
            self.disciplinesViewModel = vm
        }
        return vm
    }
}

#Preview("MainTabView - Light") {
    MainTabView()
        .environment(CampusStore())
        .preferredColorScheme(.light)
}

#Preview("MainTabView - Dark") {
    MainTabView()
        .environment(CampusStore())
        .preferredColorScheme(.dark)
}
