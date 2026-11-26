import SwiftUI
import UniformTypeIdentifiers

internal enum ProfileSubtab: String, CaseIterable, Identifiable {
    case summary = "Resumo"
    case dynamicGrid = "Grade Dinâmica"
    case calendar = "Calendário"
    case settings = "Ajustes"
    case updates = "Atualizações"
    case faq = "FAQ"

    var id: String { rawValue }

    var systemImage: String {
        switch self {
        case .summary: return "person.text.rectangle"
        case .dynamicGrid: return "square.grid.3x3.topleft.filled"
        case .calendar: return "calendar"
        case .settings: return "slider.horizontal.3"
        case .updates: return "sparkles"
        case .faq: return "questionmark.circle"
        }
    }
}

public struct ProfileView: View {
    @Environment(CampusStore.self) private var store
    @Environment(CampusAppearanceController.self) private var appearance
    @Environment(AppLockManager.self) private var lockManager
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var selectedSubtab: ProfileSubtab = .summary
    @State private var selectedCalendarSemester: String = "2026.2"
    @State private var selectedCalendarCategory: String = "Todos"

    // Sheets & Importers
    @State private var isEditProfilePresented: Bool = false
    @State private var isMatrixPresented: Bool = false
    @State private var isSharePresented: Bool = false
    @State private var isReleaseNotesPresented: Bool = false
    @State private var showTranscriptPicker: Bool = false
    @State private var transcriptMessage: String?
    @State private var transcriptError: String?

    // Segurança & Backup (.ufopbackup)
    @State private var newPinPassword: String = ""
    @State private var disablePinPassword: String = ""
    @State private var lockFeedback: String?
    @State private var backupPassword: String = ""
    @State private var backupDocument: UfopTextDocument?
    @State private var showBackupExporter: Bool = false
    @State private var showBackupImporter: Bool = false
    @State private var backupFeedback: String?
    @State private var backupError: String?

    // Manutenção & Logout
    @State private var showResetSeedConfirmation: Bool = false
    @State private var showSignOutFirstAlert: Bool = false
    @State private var showSignOutSecondAlert: Bool = false
    @State private var expandedFaqIds: Set<String> = []

    public init() {}

    private var hc: Bool { appearance.accessibility.highContrastMode }
    private var minTouch: CGFloat { appearance.accessibility.minTouchHeight }
    private var primaryAccent: Color { appearance.accentColor.primary(for: colorScheme) }
    private var onPrimaryAccent: Color { appearance.accentColor.onPrimary(for: colorScheme) }
    private var isRegular: Bool { horizontalSizeClass == .regular }

    public var body: some View {
        let summary = store.academicProfileSummary

        ZStack {
            LiquidGlassBackdrop(accent: appearance.accentColor, highContrast: hc)

            if isRegular {
                HStack(alignment: .top, spacing: 20) {
                    ScrollView(.vertical, showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 16) {
                            headerSection
                            ProfileStudentIdentityCard(
                                profile: store.studentProfile,
                                summary: summary,
                                primaryAccent: primaryAccent,
                                onPrimaryAccent: onPrimaryAccent,
                                highContrast: hc,
                                minTouch: minTouch,
                                onEditProfile: {
                                    isEditProfilePresented = true
                                }
                            )
                            verticalSubtabPickerSection
                            ProfileCampusSelectorCard(
                                store: store,
                                summary: summary,
                                primaryAccent: primaryAccent,
                                onPrimaryAccent: onPrimaryAccent,
                                highContrast: hc,
                                minTouch: minTouch
                            )
                        }
                        .padding(.leading, 20)
                        .padding(.top, 18)
                        .padding(.bottom, 40)
                    }
                    .frame(width: 320)

                    Group {
                        if selectedSubtab == .dynamicGrid {
                            DynamicCurriculumGridView(store: store)
                                .padding(.top, 18)
                                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                        } else {
                            ScrollView(.vertical, showsIndicators: false) {
                                VStack(alignment: .leading, spacing: 18) {
                                    activeSubtabContent(summary: summary)
                                }
                                .padding(.trailing, 20)
                                .padding(.top, 18)
                                .padding(.bottom, 32)
                                .campusReadableWidth(860)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                }
            } else if selectedSubtab == .dynamicGrid {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 18) {
                        headerSection
                        subtabPickerSection
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 18)

                    DynamicCurriculumGridView(store: store)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {
                        headerSection
                        subtabPickerSection
                        activeSubtabContent(summary: summary)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 18)
                    .padding(.bottom, 108)
                }
            }
        }
        .sheet(isPresented: $isEditProfilePresented) {
            ProfileEditSheetView(
                store: store,
                appearance: appearance
            ) {
                isEditProfilePresented = false
            }
            .presentationCornerRadius(30)
        }
        .sheet(isPresented: $isMatrixPresented) {
            CurriculumMatrixView(store: store) {
                isMatrixPresented = false
            }
            .presentationCornerRadius(30)
        }
        .sheet(isPresented: $isSharePresented) {
            ScheduleShareImportSheetView(store: store) {
                isSharePresented = false
            }
            .presentationCornerRadius(30)
        }
        .sheet(isPresented: $isReleaseNotesPresented) {
            ReleaseNotesSheetView {
                isReleaseNotesPresented = false
            }
            .presentationCornerRadius(30)
        }
        .fileImporter(
            isPresented: $showTranscriptPicker,
            allowedContentTypes: [.pdf],
            allowsMultipleSelection: false
        ) { result in
            handleTranscriptImport(result)
        }
        .fileExporter(
            isPresented: $showBackupExporter,
            document: backupDocument,
            contentType: .plainText,
            defaultFilename: "ufop-backup-\(CampusStore.todayIsoDateString()).ufopbackup"
        ) { result in
            switch result {
            case .success:
                backupFeedback = "Backup criptografado (.ufopbackup) salvo com sucesso."
                backupError = nil
            case .failure(let err):
                backupError = err.localizedDescription
            }
        }
        .fileImporter(
            isPresented: $showBackupImporter,
            allowedContentTypes: [.data, .plainText, .json],
            allowsMultipleSelection: false
        ) { result in
            handleBackupRestore(result)
        }
        .confirmationDialog(
            "Restaurar grade padrão 2026.2?",
            isPresented: $showResetSeedConfirmation,
            titleVisibility: .visible
        ) {
            Button("Restaurar disciplinas padrão", role: .destructive) {
                _ = store.resetSemesterToDefaultSeed(semester: store.selectedSemesterFilter)
            }
            Button("Cancelar", role: .cancel) {}
        } message: {
            Text("Substitui as disciplinas atuais do semestre pelo conjunto padrão de demonstração 2026.2.")
        }
        .alert("Deseja baixar backup antes de sair?", isPresented: $showSignOutFirstAlert) {
            Button("Fazer backup antes") {
                withAnimation(.snappy) {
                    selectedSubtab = .settings
                    backupFeedback = "Defina uma senha na seção Backup (.ufopbackup) e toque em Exportar antes de sair."
                }
            }
            Button("Sair sem backup", role: .destructive) {
                showSignOutSecondAlert = true
            }
            Button("Cancelar", role: .cancel) {}
        } message: {
            Text("Todos os dados salvos localmente neste aparelho serão removidos após a confirmação.")
        }
        .alert("Confirmar saída da conta?", isPresented: $showSignOutSecondAlert) {
            Button("Apagar dados locais e sair", role: .destructive) {
                store.signOutAndResetAllData(lockManager: lockManager, appearance: appearance)
            }
            Button("Cancelar", role: .cancel) {}
        } message: {
            Text("O aplicativo voltará para a tela de boas-vindas e os arquivos locais criptografados serão apagados.")
        }
    }

    // MARK: - Header & Segmented Subtabs

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("MEU PERFIL")
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .tracking(1.0)
                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

            Text("Perfil Acadêmico")
                .font(.system(size: 30, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
        }
    }

    private var subtabPickerSection: some View {
        LiquidGlassGroup(spacing: 6) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(ProfileSubtab.allCases) { tab in
                        let isSelected = selectedSubtab == tab
                        Button {
                            withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                                selectedSubtab = tab
                            }
                        } label: {
                            Text(tab.rawValue)
                                .font(.system(size: 13, weight: .bold, design: .rounded))
                                .foregroundStyle(isSelected ? onPrimaryAccent : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                .padding(.horizontal, 16)
                                .frame(minHeight: 40)
                                .liquidGlassCapsule(selected: isSelected, accent: primaryAccent, interactive: true, highContrast: hc)
                        }
                        .buttonStyle(.plain)
                        .minimumTouchTarget(minTouch)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func activeSubtabContent(summary: AcademicProfileSummary) -> some View {
        switch selectedSubtab {
        case .summary:
            ProfileSummarySectionView(
                store: store,
                appearance: appearance,
                summary: summary,
                isRegular: isRegular,
                primaryAccent: primaryAccent,
                onPrimaryAccent: onPrimaryAccent,
                highContrast: hc,
                minTouch: minTouch,
                transcriptMessage: transcriptMessage,
                transcriptError: transcriptError,
                onImportTranscript: {
                    transcriptError = nil
                    showTranscriptPicker = true
                },
                onOpenMatrix: {
                    isMatrixPresented = true
                },
                onOpenDynamicGrid: {
                    if appearance.accessibility.reduceMotion {
                        selectedSubtab = .dynamicGrid
                    } else {
                        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                            selectedSubtab = .dynamicGrid
                        }
                    }
                },
                onOpenShare: {
                    isSharePresented = true
                },
                onEditProfile: {
                    isEditProfilePresented = true
                }
            )
        case .dynamicGrid:
            EmptyView()
        case .calendar:
            ProfileCalendarSectionView(
                calendarEvents: store.calendarEvents,
                accentColor: appearance.accentColor,
                primaryAccent: primaryAccent,
                onPrimaryAccent: onPrimaryAccent,
                highContrast: hc,
                minTouch: minTouch,
                selectedSemester: $selectedCalendarSemester,
                selectedCategory: $selectedCalendarCategory
            )
        case .settings:
            ProfileSettingsSectionView(
                store: store,
                appearance: appearance,
                lockManager: lockManager,
                primaryAccent: primaryAccent,
                onPrimaryAccent: onPrimaryAccent,
                highContrast: hc,
                minTouch: minTouch,
                newPinPassword: $newPinPassword,
                disablePinPassword: $disablePinPassword,
                lockFeedback: $lockFeedback,
                backupPassword: $backupPassword,
                backupDocument: $backupDocument,
                showBackupExporter: $showBackupExporter,
                showBackupImporter: $showBackupImporter,
                backupFeedback: $backupFeedback,
                backupError: $backupError,
                showResetSeedConfirmation: $showResetSeedConfirmation
            )
        case .updates:
            ProfileUpdatesSectionView(
                primaryAccent: primaryAccent,
                onPrimaryAccent: onPrimaryAccent,
                highContrast: hc,
                minTouch: minTouch,
                onOpenReleaseNotes: {
                    isReleaseNotesPresented = true
                }
            )
        case .faq:
            ProfileFaqSectionView(
                primaryAccent: primaryAccent,
                highContrast: hc,
                minTouch: minTouch,
                expandedFaqIds: $expandedFaqIds,
                onRequestSignOut: {
                    showSignOutFirstAlert = true
                }
            )
        }
    }

    private var verticalSubtabPickerSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(ProfileSubtab.allCases) { tab in
                let isSelected = selectedSubtab == tab
                Button {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                        selectedSubtab = tab
                    }
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: tab.systemImage)
                            .font(.system(size: 14, weight: .bold))
                            .frame(width: 22)
                        Text(tab.rawValue)
                            .font(.system(size: 14, weight: .bold, design: .rounded))
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.system(size: 11, weight: .bold))
                            .opacity(isSelected ? 1.0 : 0.45)
                    }
                    .foregroundStyle(isSelected ? onPrimaryAccent : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    .padding(.horizontal, 14)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .liquidGlassCapsule(selected: isSelected, accent: primaryAccent, interactive: true, highContrast: hc)
                }
                .buttonStyle(.plain)
                .campusHoverLift()
                .minimumTouchTarget(minTouch)
            }
        }
    }

    // MARK: - Helpers

    private func handleTranscriptImport(_ result: Result<[URL], Error>) {
        switch result {
        case .failure(let err):
            transcriptError = err.localizedDescription
        case .success(let urls):
            guard let url = urls.first else { return }
            do {
                let text = try SecurePdfProcessor.extractTextAndDeleteTemp(from: url)
                let parsed = try UfopPdfParsers.parseTranscript(text: text)
                let report = store.applyTranscriptImport(parsed)
                transcriptMessage = "Histórico processado! CRE: \(String(format: "%.2f", report.overallCre)) · \(report.totalApproved) aprovadas."
                transcriptError = nil
            } catch {
                transcriptError = error.localizedDescription
            }
        }
    }

    private func handleBackupRestore(_ result: Result<[URL], Error>) {
        switch result {
        case .failure(let err):
            backupError = err.localizedDescription
        case .success(let urls):
            guard let url = urls.first else { return }
            let accessed = url.startAccessingSecurityScopedResource()
            defer { if accessed { url.stopAccessingSecurityScopedResource() } }
            do {
                let text = try String(contentsOf: url, encoding: .utf8)
                _ = try store.importEncryptedBackup(text, password: backupPassword)
                backupFeedback = "Backup restaurado com sucesso!"
                backupError = nil
            } catch {
                backupError = error.localizedDescription
                backupFeedback = nil
            }
        }
    }
}

#Preview("ProfileView - Light") {
    ProfileView()
        .environment(CampusStore())
        .environment(CampusAppearanceController())
        .environment(AppLockManager())
        .preferredColorScheme(.light)
}

#Preview("ProfileView - Dark") {
    ProfileView()
        .environment(CampusStore())
        .environment(CampusAppearanceController())
        .environment(AppLockManager())
        .preferredColorScheme(.dark)
}
