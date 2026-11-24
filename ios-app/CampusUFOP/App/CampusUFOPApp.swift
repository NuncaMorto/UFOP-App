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
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .background {
                store.persistEncryptedState()
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
