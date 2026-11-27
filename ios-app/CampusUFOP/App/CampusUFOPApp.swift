import SwiftUI

@main
public struct CampusUFOPApp: App {
    @State private var store = CampusStore()
    @State private var appearance = CampusAppearanceController()
    @State private var lockManager = AppLockManager()

    public init() {}

    public var body: some Scene {
        WindowGroup {
            CampusRootCoordinatorView(
                store: store,
                appearance: appearance,
                lockManager: lockManager
            )
            .environment(store)
            .environment(appearance)
            .environment(lockManager)
            .preferredColorScheme(appearance.resolvedColorScheme)
        }
    }
}

private struct CampusRootCoordinatorView: View {
    @Bindable var store: CampusStore
    @Bindable var appearance: CampusAppearanceController
    @Bindable var lockManager: AppLockManager

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.scenePhase) private var scenePhase

    @State private var selectedTab: CampusTab = .agenda
    @State private var isReleaseNotesPresented: Bool = false
    @State private var pendingIncomingGrade: IncomingGradePreview?
    @State private var incomingGradeError: String?

    private var canPresentIncomingGrade: Bool {
        (!lockManager.isLockEnabled || lockManager.isUnlocked) && store.studentProfile.onboardingCompleted
    }

    private var incomingGradeSheetBinding: Binding<IncomingGradePreview?> {
        Binding(
            get: { canPresentIncomingGrade ? pendingIncomingGrade : nil },
            set: { newValue in
                if newValue == nil {
                    pendingIncomingGrade = nil
                }
            }
        )
    }

    private var incomingGradeErrorBinding: Binding<Bool> {
        Binding(
            get: { canPresentIncomingGrade && incomingGradeError != nil },
            set: { isPresented in
                if !isPresented {
                    incomingGradeError = nil
                }
            }
        )
    }

    var body: some View {
        let accent = appearance.accentColor.primary(for: colorScheme)

        ZStack {
            if lockManager.isLockEnabled && !lockManager.isUnlocked {
                AppLockOverlayView(lockManager: lockManager)
            } else if !store.studentProfile.onboardingCompleted {
                StudentOnboardingView(
                    store: store,
                    appearance: appearance,
                    lockManager: lockManager
                )
            } else {
                MainTabView(selectedTab: $selectedTab)
                    .overlay {
                        if !store.guidedTourCompleted {
                            GuidedTourOverlayView(selectedTab: $selectedTab) {
                                store.guidedTourCompleted = true
                            }
                        }
                    }
                    .onAppear {
                        if store.guidedTourCompleted && store.lastSeenReleaseVersion != CampusReleaseNotesData.currentVersionString {
                            isReleaseNotesPresented = true
                        }
                    }
                    .onChange(of: store.guidedTourCompleted) { _, completed in
                        if completed && store.lastSeenReleaseVersion != CampusReleaseNotesData.currentVersionString {
                            isReleaseNotesPresented = true
                        }
                    }
            }

            if lockManager.isLockEnabled && scenePhase != .active && lockManager.isUnlocked {
                PrivacyBlurOverlayView()
            }
        }
        .tint(accent)
        .modifier(AccessibilityRootModifier(accessibility: appearance.accessibility))
        .task {
            Task.detached(priority: .utility) {
                _ = UfopCurriculumCatalog.allCourses
            }
        }
        .onOpenURL { url in
            handleIncomingUrl(url)
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .background {
                store.persistEncryptedState()
                appearance.reloadWidgetTimelines()
                lockManager.lockNowIfEnabled()
            }
        }
        .sheet(isPresented: $isReleaseNotesPresented, onDismiss: {
            store.lastSeenReleaseVersion = CampusReleaseNotesData.currentVersionString
        }) {
            ReleaseNotesSheetView {
                store.lastSeenReleaseVersion = CampusReleaseNotesData.currentVersionString
                isReleaseNotesPresented = false
            }
            .presentationDetents([.large])
            .presentationCornerRadius(28)
        }
        .sheet(item: incomingGradeSheetBinding) { preview in
            IncomingGradeConfirmSheetView(
                preview: preview,
                onConfirm: {
                    let imported = store.importFriendSchedule(from: preview)
                    store.selectedFriendScheduleId = imported.id
                    pendingIncomingGrade = nil
                    selectedTab = .disciplines
                    store.requestedTimetablePresentation = true
                },
                onCancel: {
                    pendingIncomingGrade = nil
                }
            )
        }
        .alert("Não foi possível ler a grade", isPresented: incomingGradeErrorBinding) {
            Button("OK", role: .cancel) {
                incomingGradeError = nil
            }
        } message: {
            Text(incomingGradeError ?? "Arquivo .ufopgrade inválido.")
        }
    }

    private func handleIncomingUrl(_ url: URL) {
        if url.scheme?.lowercased() == "vidaacademica" {
            let route = (url.host ?? url.path.replacingOccurrences(of: "/", with: ""))
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .lowercased()
            switch route {
            case "agenda", "aulas":
                selectedTab = .agenda
                return
            case "activities", "atividades", "prazos":
                selectedTab = .activities
                return
            case "disciplines", "disciplinas":
                selectedTab = .disciplines
                return
            case "map", "mapa":
                selectedTab = .map
                return
            case "profile", "perfil":
                selectedTab = .profile
                return
            default:
                break
            }
        }
        do {
            let rawText: String
            if url.isFileURL {
                rawText = try UfopGradeCodec.readFileSafely(from: url)
            } else {
                rawText = url.absoluteString
            }
            let label = url.lastPathComponent.isEmpty ? "Arquivo .ufopgrade" : url.lastPathComponent
            let preview = try UfopGradeCodec.parseAndValidatePreview(rawText, sourceLabel: label)
            pendingIncomingGrade = preview
            incomingGradeError = nil
        } catch {
            incomingGradeError = error.localizedDescription
        }
    }
}

private struct AccessibilityRootModifier: ViewModifier {
    let accessibility: CampusAccessibilitySettings

    @ViewBuilder
    func body(content: Content) -> some View {
        let base = content
            .environment(\.legibilityWeight, accessibility.boldTextBoost ? .bold : nil)
        if let overrideSize = accessibility.dynamicTypeOverride {
            base.dynamicTypeSize(overrideSize)
        } else {
            base
        }
    }
}
