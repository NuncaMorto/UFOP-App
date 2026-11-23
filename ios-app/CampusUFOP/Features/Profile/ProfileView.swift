import SwiftUI
import UniformTypeIdentifiers

private enum ProfileSubtab: String, CaseIterable, Identifiable {
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

private struct FaqEntry: Identifiable {
    let id: String
    let question: String
    let answer: String
}

public struct ProfileView: View {
    @Environment(CampusStore.self) private var environmentStore: CampusStore?
    @Environment(CampusAppearanceController.self) private var environmentAppearance: CampusAppearanceController?
    @Environment(AppLockManager.self) private var environmentLockManager: AppLockManager?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var fallbackStore = CampusStore()
    @State private var fallbackAppearance = CampusAppearanceController()
    @State private var fallbackLockManager = AppLockManager()

    @State private var selectedSubtab: ProfileSubtab = .summary
    @State private var selectedCalendarSemester: String = "2026.2"
    @State private var selectedCalendarCategory: String = "Todos"

    // Sheets & Importers
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

    private var activeStore: CampusStore { environmentStore ?? fallbackStore }
    private var activeAppearance: CampusAppearanceController { environmentAppearance ?? fallbackAppearance }
    private var activeLock: AppLockManager { environmentLockManager ?? fallbackLockManager }

    private var hc: Bool { activeAppearance.accessibility.highContrastMode }
    private var minTouch: CGFloat { activeAppearance.accessibility.minTouchHeight }
    private var primaryAccent: Color { activeAppearance.accentColor.primary(for: colorScheme) }
    private var onPrimaryAccent: Color { activeAppearance.accentColor.onPrimary(for: colorScheme) }
    private var isRegular: Bool { horizontalSizeClass == .regular }

    public var body: some View {
        ZStack {
            LiquidGlassBackdrop(accent: activeAppearance.accentColor, highContrast: hc)

            if isRegular {
                HStack(alignment: .top, spacing: 20) {
                    ScrollView(.vertical, showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 16) {
                            headerSection
                            studentIdentityCard(profile: activeStore.studentProfile, summary: activeStore.academicProfileSummary)
                            verticalSubtabPickerSection
                            campusSelectorCard(summary: activeStore.academicProfileSummary)
                        }
                        .padding(.leading, 20)
                        .padding(.top, 18)
                        .padding(.bottom, 40)
                    }
                    .frame(width: 320)

                    Group {
                        if selectedSubtab == .dynamicGrid {
                            DynamicCurriculumGridView(store: activeStore)
                                .padding(.top, 18)
                                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                        } else {
                            ScrollView(.vertical, showsIndicators: false) {
                                VStack(alignment: .leading, spacing: 18) {
                                    activeSubtabContent
                                }
                                .padding(.trailing, 20)
                                .padding(.top, 18)
                                .padding(.bottom, 108)
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

                    DynamicCurriculumGridView(store: activeStore)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {
                        headerSection
                        subtabPickerSection
                        activeSubtabContent
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 18)
                    .padding(.bottom, 108)
                }
            }
        }
        .sheet(isPresented: $isMatrixPresented) {
            CurriculumMatrixView(store: activeStore) {
                isMatrixPresented = false
            }
        }
        .sheet(isPresented: $isSharePresented) {
            ScheduleShareImportSheetView(store: activeStore) {
                isSharePresented = false
            }
        }
        .sheet(isPresented: $isReleaseNotesPresented) {
            ReleaseNotesSheetView {
                isReleaseNotesPresented = false
            }
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
                _ = activeStore.resetSemesterToDefaultSeed(semester: activeStore.selectedSemesterFilter)
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
                activeStore.signOutAndResetAllData(lockManager: activeLock, appearance: activeAppearance)
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
    private var activeSubtabContent: some View {
        switch selectedSubtab {
        case .summary:
            summarySubtabContent
        case .dynamicGrid:
            EmptyView()
        case .calendar:
            calendarSubtabContent
        case .settings:
            settingsSubtabContent
        case .updates:
            updatesSubtabContent
        case .faq:
            faqSubtabContent
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

    // MARK: - 1. Resumo

    @ViewBuilder
    private var summarySubtabContent: some View {
        let summary = activeStore.academicProfileSummary
        let profile = activeStore.studentProfile
        let creReport = activeStore.getAcademicCreReport()

        if !isRegular {
            studentIdentityCard(profile: profile, summary: summary)
            campusSelectorCard(summary: summary)
        }
        metricsGrid(summary: summary)
        creAndTranscriptCard(creReport: creReport)
        dynamicGridShortcutCard
        shareScheduleCard
    }

    private func studentIdentityCard(profile: StudentProfile, summary: AcademicProfileSummary) -> some View {
        let displayName = profile.fullName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Estudante"
            : profile.fullName
        let initials = displayName
            .split(separator: " ")
            .prefix(2)
            .compactMap(\.first)
            .map { String($0).uppercased() }
            .joined()

        return HStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(primaryAccent)
                    .frame(width: 54, height: 54)
                Text(initials.isEmpty ? "VA" : initials)
                    .font(.system(size: 19, weight: .bold, design: .rounded))
                    .foregroundStyle(onPrimaryAccent)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(displayName)
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                if !profile.courseName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text(profile.courseName)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                }

                HStack(spacing: 8) {
                    if !profile.registrationNumber.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        Text("Mat. \(profile.registrationNumber)")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    }
                    Text("• \(summary.semester)")
                        .font(.system(size: 12, weight: .semibold, design: .rounded))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                }
            }

            Spacer(minLength: 0)
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 22, tint: primaryAccent, highContrast: hc)
    }

    private func campusSelectorCard(summary: AcademicProfileSummary) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Campus")
                    .font(.system(size: 15, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                Spacer()
                WeatherPillBadge(label: summary.activeCampus.weatherBadgeLabel)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(summary.campuses) { campus in
                        let isSelected = campus.id == summary.activeCampus.id
                        Button {
                            withAnimation(.snappy) {
                                _ = activeStore.setActiveCampus(campusId: campus.id)
                            }
                        } label: {
                            Text(campus.city)
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                                .foregroundStyle(isSelected ? onPrimaryAccent : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                .padding(.horizontal, 14)
                                .frame(minHeight: 38)
                                .liquidGlassCapsule(selected: isSelected, accent: primaryAccent, interactive: true, highContrast: hc)
                        }
                        .buttonStyle(.plain)
                        .minimumTouchTarget(minTouch)
                    }
                }
            }

            Text(summary.activeCampus.fullLabel)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, highContrast: hc)
    }

    private func metricsGrid(summary: AcademicProfileSummary) -> some View {
        let columns = [GridItem(.adaptive(minimum: isRegular ? 180 : 150), spacing: 10)]
        return LazyVGrid(columns: columns, spacing: 10) {
            metricTile(title: "Disciplinas", value: "\(summary.totalCourses)", subtitle: "Em \(summary.semester)", tint: primaryAccent)
            metricTile(title: "Faltas acumuladas", value: "\(summary.totalAbsences)/\(summary.totalMaxAbsences)", subtitle: "\(summary.coursesAtRisk) em risco", tint: CampusTheme.mathBlue)
            metricTile(title: "Canceladas", value: "\(summary.totalCancelledClasses)", subtitle: "Não somam falta", tint: CampusTheme.compPurple)
            metricTile(title: "Avaliações", value: "\(summary.pendingAssessments)", subtitle: "\(summary.completedAssessments) concluídas", tint: CampusTheme.algebraGreen)
        }
    }

    private func metricTile(title: String, value: String, subtitle: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
            Text(value)
                .font(.system(size: 22, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
            Text(subtitle)
                .font(.system(size: 11))
                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .liquidGlassCard(cornerRadius: 18, tint: tint, highContrast: hc)
    }

    private func creAndTranscriptCard(creReport: AcademicCreReport) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Coeficiente de Rendimento (CRE)")
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    Text("Integralizado: \(creReport.completedHours)h / \(creReport.totalRequiredHours)h · Aprovadas: \(creReport.totalApproved)")
                        .font(.system(size: 12))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                }
                Spacer()
                Text(String(format: "%.2f", creReport.overallCre))
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
            }

            if let transcriptMessage {
                Text(transcriptMessage)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
            }
            if let transcriptError {
                Text(transcriptError)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color(hex: 0xDC2626))
            }

            HStack(spacing: 10) {
                Button {
                    transcriptError = nil
                    showTranscriptPicker = true
                } label: {
                    Label("Histórico PDF", systemImage: "doc.badge.plus")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                        .frame(maxWidth: .infinity, minHeight: 42)
                        .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: hc)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)

                Button {
                    isMatrixPresented = true
                } label: {
                    Label("Matriz & Simulador", systemImage: "slider.horizontal.3")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(onPrimaryAccent)
                        .frame(maxWidth: .infinity, minHeight: 42)
                        .liquidGlassCapsule(selected: true, accent: primaryAccent, interactive: true, highContrast: hc)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
            }
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, highContrast: hc)
    }

    private var resolvedDynamicMatrixResolution: DynamicMatrixResolution {
        let earliestSemester = activeStore.transcriptEntries
            .map { $0.semester.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .min { lhs, rhs in
                let l = DynamicCurriculumCatalog.parseSemesterTerm(lhs)
                let r = DynamicCurriculumCatalog.parseSemesterTerm(rhs)
                switch (l, r) {
                case let (.some(a), .some(b)): return (a.year, a.semester) < (b.year, b.semester)
                case (.some, .none): return true
                case (.none, .some): return false
                case (.none, .none): return lhs < rhs
                }
            }
        return DynamicCurriculumCatalog.resolveMatrixForStudent(
            courseName: activeStore.studentProfile.courseName,
            registrationNumber: activeStore.studentProfile.registrationNumber,
            earliestTranscriptSemester: earliestSemester
        )
    }

    private var dynamicGridShortcutCard: some View {
        let resolution = resolvedDynamicMatrixResolution
        let matrix = resolution.matrix
        let completed = activeStore.dynamicGridCompletedSet(for: matrix.id)
        let gridSummary = DynamicGridEngine.summarize(matrix: matrix, completedCodes: completed)

        return Button {
            if activeAppearance.accessibility.reduceMotion {
                selectedSubtab = .dynamicGrid
            } else {
                withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                    selectedSubtab = .dynamicGrid
                }
            }
        } label: {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 12) {
                    Image(systemName: "square.grid.3x3.topleft.filled")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Grade Dinâmica")
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                        Text("\(matrix.curriculumLabel) · \(gridSummary.completedHours)/\(gridSummary.totalGridHours)h · \(gridSummary.completionPercent)%")
                            .font(.system(size: 12))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    }

                    Spacer()

                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
                }

                GeometryReader { proxy in
                    ZStack(alignment: .leading) {
                        Capsule(style: .continuous)
                            .fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: hc))
                        Capsule(style: .continuous)
                            .fill(primaryAccent)
                            .frame(
                                width: max(
                                    gridSummary.completedHours > 0 ? 8 : 0,
                                    proxy.size.width * gridSummary.progressFraction
                                )
                            )
                    }
                }
                .frame(height: 6)
                .accessibilityHidden(true)
            }
            .padding(16)
            .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, interactive: true, highContrast: hc)
        }
        .buttonStyle(.plain)
        .minimumTouchTarget(minTouch)
        .accessibilityLabel("Grade Dinâmica, \(matrix.courseName), \(gridSummary.completedHours) de \(gridSummary.totalGridHours) horas, \(gridSummary.completionPercent) por cento concluído")
    }

    private var shareScheduleCard: some View {
        Button {
            isSharePresented = true
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "square.and.arrow.up.on.square.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))

                VStack(alignment: .leading, spacing: 2) {
                    Text("Compartilhar ou importar grade (.ufopgrade)")
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    Text("Exporte para JSON/arquivo e envie para colegas da turma.")
                        .font(.system(size: 12))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
            }
            .padding(16)
            .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, interactive: true, highContrast: hc)
        }
        .buttonStyle(.plain)
        .minimumTouchTarget(minTouch)
    }

    // MARK: - 2. Calendário Oficial (2026.2, 2027.1, 2027.2)

    @ViewBuilder
    private var calendarSubtabContent: some View {
        let semesters = ["2026.2", "2027.1", "2027.2"]
        let semEvents = activeStore.calendarEvents.filter { $0.semester == selectedCalendarSemester }
        let categories = ["Todos"] + Array(Set(semEvents.map(\.category))).sorted()
        let filtered = semEvents.filter {
            selectedCalendarCategory == "Todos" || $0.category == selectedCalendarCategory
        }

        VStack(alignment: .leading, spacing: 12) {
            Text("Calendários Acadêmicos")
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

            HStack(spacing: 8) {
                ForEach(semesters, id: \.self) { sem in
                    let isSelected = selectedCalendarSemester == sem
                    Button {
                        withAnimation(.snappy) {
                            selectedCalendarSemester = sem
                            selectedCalendarCategory = "Todos"
                        }
                    } label: {
                        Text(sem)
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .foregroundStyle(isSelected ? onPrimaryAccent : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .frame(maxWidth: .infinity, minHeight: 40)
                            .liquidGlassCapsule(selected: isSelected, accent: primaryAccent, interactive: true, highContrast: hc)
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(minTouch)
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(categories, id: \.self) { cat in
                        let isSelected = selectedCalendarCategory == cat
                        Button {
                            selectedCalendarCategory = cat
                        } label: {
                            Text(cat)
                                .font(.system(size: 12, weight: .semibold, design: .rounded))
                                .foregroundStyle(isSelected ? onPrimaryAccent : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                .padding(.horizontal, 12)
                                .frame(minHeight: 34)
                                .liquidGlassCapsule(selected: isSelected, accent: CampusTheme.mathBlue, interactive: true, highContrast: hc)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            Text("\(filtered.count) evento(s) em \(selectedCalendarSemester)")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

            VStack(spacing: 10) {
                ForEach(filtered) { ev in
                    HStack(spacing: 12) {
                        VStack(spacing: 1) {
                            Text(ev.dayNumber)
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                            Text(ev.monthShort)
                                .font(.system(size: 9, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                        }
                        .frame(width: 44, height: 42)
                        .background(RoundedRectangle(cornerRadius: 11).fill(activeAppearance.accentColor.softTint(for: colorScheme)))

                        VStack(alignment: .leading, spacing: 3) {
                            Text(ev.title)
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            Text(ev.category)
                                .font(.system(size: 11, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(12)
                    .liquidGlassCard(cornerRadius: 16, highContrast: hc)
                }
            }
        }
    }

    // MARK: - 3. Ajustes (Personalização, Segurança, Acessibilidade, Manutenção)

    @ViewBuilder
    private var settingsSubtabContent: some View {
        appearanceCustomizationCard
        securityAndLockCard
        encryptedBackupCard
        AccessibilityControlsSection(appearance: activeAppearance)
        guidedTourSettingsCard
        maintenanceCard
    }

    private var appearanceCustomizationCard: some View {
        let modes: [(key: String, label: String)] = [("system", "Sistema"), ("light", "Claro"), ("dark", "Escuro")]

        return VStack(alignment: .leading, spacing: 14) {
            Text("Personalização & Tema")
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

            HStack(spacing: 8) {
                ForEach(modes, id: \.key) { mode in
                    let isSelected = activeAppearance.appearanceModeRaw == mode.key
                    Button {
                        withAnimation(.snappy) {
                            activeAppearance.appearanceModeRaw = mode.key
                        }
                    } label: {
                        Text(mode.label)
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .foregroundStyle(isSelected ? onPrimaryAccent : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .frame(maxWidth: .infinity, minHeight: 38)
                            .liquidGlassCapsule(selected: isSelected, accent: primaryAccent, interactive: true, highContrast: hc)
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(minTouch)
                }
            }

            Text("Cor de destaque")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(CampusAccentColor.allCases) { accentOption in
                        let isSelected = activeAppearance.accentColor == accentOption
                        let swatch = accentOption.primary(for: colorScheme)
                        Button {
                            withAnimation(.spring(response: 0.25, dampingFraction: 0.85)) {
                                activeAppearance.accentColor = accentOption
                            }
                        } label: {
                            HStack(spacing: 6) {
                                Circle()
                                    .fill(swatch)
                                    .frame(width: 18, height: 18)
                                Text(accentOption.displayName)
                                    .font(.system(size: 12, weight: .bold, design: .rounded))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            }
                            .padding(.horizontal, 12)
                            .frame(minHeight: 38)
                            .liquidGlassCapsule(selected: isSelected, accent: swatch, interactive: true, highContrast: hc)
                        }
                        .buttonStyle(.plain)
                        .minimumTouchTarget(minTouch)
                    }
                }
            }
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, highContrast: hc)
    }

    private var securityAndLockCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                Text("Segurança & Privacidade")
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
            }

            Text("AES-256-GCM ativo para dados locais · PDFs processados em memória sem retenção de arquivos temporários.")
                .font(.system(size: 12))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

            if activeLock.isLockEnabled {
                Label("Bloqueio do aplicativo ativado", systemImage: "checkmark.seal.fill")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))

                if activeLock.biometricAvailable {
                    Toggle(
                        "Usar \(activeLock.biometricLabel)",
                        isOn: Binding(
                            get: { activeLock.isBiometricEnabled },
                            set: { newVal in
                                lockFeedback = activeLock.setBiometricEnabled(newVal)
                            }
                        )
                    )
                    .tint(primaryAccent)
                }

                HStack(spacing: 8) {
                    SecureField("Senha atual para desativar", text: $disablePinPassword)
                        .textFieldStyle(.roundedBorder)
                    Button("Desativar") {
                        if let err = activeLock.disableLock(currentPassword: disablePinPassword) {
                            lockFeedback = err
                        } else {
                            disablePinPassword = ""
                            lockFeedback = "Bloqueio desativado."
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color(hex: 0xDC2626))
                }
            } else {
                HStack(spacing: 8) {
                    SecureField("Definir senha (mín. 4 dígitos)", text: $newPinPassword)
                        .textFieldStyle(.roundedBorder)
                    Button("Ativar") {
                        if let err = activeLock.enableLock(password: newPinPassword, allowBiometric: activeLock.biometricAvailable) {
                            lockFeedback = err
                        } else {
                            newPinPassword = ""
                            lockFeedback = "Bloqueio ativado com sucesso."
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(primaryAccent)
                }
            }

            if let lockFeedback {
                Text(lockFeedback)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
            }
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, highContrast: hc)
    }

    private var encryptedBackupCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Backup Portátil (.ufopbackup)")
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

            Text("Exporte ou restaure disciplinas, horários, faltas, notas e avaliações usando criptografia PBKDF2 + AES-GCM.")
                .font(.system(size: 12))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

            SecureField("Senha do backup", text: $backupPassword)
                .textFieldStyle(.roundedBorder)

            HStack(spacing: 10) {
                Button {
                    do {
                        let content = try activeStore.exportEncryptedBackup(password: backupPassword)
                        backupDocument = UfopTextDocument(text: content)
                        showBackupExporter = true
                    } catch {
                        backupError = error.localizedDescription
                        backupFeedback = nil
                    }
                } label: {
                    Label("Exportar", systemImage: "square.and.arrow.up")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(onPrimaryAccent)
                        .frame(maxWidth: .infinity, minHeight: 42)
                        .liquidGlassCapsule(selected: true, accent: primaryAccent, interactive: true, highContrast: hc)
                }
                .buttonStyle(.plain)

                Button {
                    backupError = nil
                    showBackupImporter = true
                } label: {
                    Label("Restaurar", systemImage: "arrow.down.doc")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                        .frame(maxWidth: .infinity, minHeight: 42)
                        .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: hc)
                }
                .buttonStyle(.plain)
            }

            if let backupFeedback {
                Text(backupFeedback)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
            }
            if let backupError {
                Text(backupError)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color(hex: 0xDC2626))
            }
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, highContrast: hc)
    }

    private var guidedTourSettingsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Guia Interativo")
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

            Text("Reveja a apresentação inicial com as principais ferramentas do Vida Acadêmica.")
                .font(.system(size: 12))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

            Button {
                activeStore.guidedTourCompleted = false
            } label: {
                Label("Refazer Tour Guiado do App", systemImage: "map.fill")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    .frame(maxWidth: .infinity, minHeight: max(44, minTouch))
                    .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: hc)
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, highContrast: hc)
    }

    private var maintenanceCard: some View {
        Button {
            showResetSeedConfirmation = true
        } label: {
            Label("Restaurar grade de demonstração 2026.2", systemImage: "arrow.counterclockwise")
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                .frame(maxWidth: .infinity, minHeight: 44)
                .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: hc)
        }
        .buttonStyle(.plain)
        .minimumTouchTarget(minTouch)
    }

    // MARK: - 4. Atualizações & Histórico de Patches

    @ViewBuilder
    private var updatesSubtabContent: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Versão Atual")
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    Text("Vida Acadêmica v\(CampusReleaseNotesData.currentVersionString)")
                        .font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                }

                Spacer()

                Text("v\(CampusReleaseNotesData.currentVersionString)")
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .foregroundStyle(onPrimaryAccent)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Capsule(style: .continuous).fill(primaryAccent))
            }

            Button {
                isReleaseNotesPresented = true
            } label: {
                Label("Abrir resumo em janela", systemImage: "sparkles")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .foregroundStyle(onPrimaryAccent)
                    .frame(maxWidth: .infinity, minHeight: max(44, minTouch))
                    .liquidGlassCapsule(selected: true, accent: primaryAccent, interactive: true, highContrast: hc)
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, highContrast: hc)

        Text("HISTÓRICO DE PATCHES")
            .font(.system(size: 11, weight: .bold, design: .rounded))
            .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
            .padding(.horizontal, 4)

        VStack(spacing: 10) {
            ForEach(CampusReleaseNotesData.versions) { entry in
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("\(entry.version) · \(entry.title)")
                            .font(.system(size: 14, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                        Spacer()

                        Text(entry.badge)
                            .font(.system(size: 10, weight: .bold, design: .rounded))
                            .foregroundStyle(
                                entry.isLatest
                                    ? onPrimaryAccent
                                    : CampusTheme.secondaryText(for: colorScheme, highContrast: hc)
                            )
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(
                                Capsule(style: .continuous).fill(
                                    entry.isLatest
                                        ? primaryAccent
                                        : CampusTheme.secondarySurface(for: colorScheme, highContrast: hc)
                                )
                            )
                    }

                    ForEach(entry.items, id: \.self) { item in
                        HStack(alignment: .top, spacing: 8) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 12))
                                .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                                .padding(.top, 2)

                            Text(item)
                                .font(.system(size: 12.5))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .padding(14)
                .liquidGlassCard(cornerRadius: 18, tint: entry.isLatest ? primaryAccent : nil, highContrast: hc)
            }
        }
    }

    // MARK: - 5. FAQ & Sign Out

    private var faqEntries: [FaqEntry] {
        [
            FaqEntry(
                id: "offline",
                question: "Meus dados ficam salvos apenas no celular?",
                answer: "Sim. Tudo funciona offline-first com criptografia AES-256-GCM usando chave protegida no Keychain do iOS."
            ),
            FaqEntry(
                id: "pdfs",
                question: "O aplicativo guarda os PDFs que eu importo?",
                answer: "Não. O texto do Atestado de Matrícula, Plano de Ensino e Histórico Escolar é extraído em memória e qualquer cópia temporária é removida imediatamente."
            ),
            FaqEntry(
                id: "absences",
                question: "Como funciona o cálculo de faltas e aulas canceladas?",
                answer: "A referência de 25% da carga horária corresponde ao limite padrão (ex.: 60h = 18 faltas). Marcar uma aula como cancelada na Agenda nunca adiciona falta."
            ),
            FaqEntry(
                id: "cre",
                question: "Como calcular meu CRE e verificar a matriz?",
                answer: "Na aba Disciplinas (botão Matriz) ou no Resumo do Perfil, importe seu Histórico Escolar em PDF e simule notas futuras."
            ),
            FaqEntry(
                id: "backup",
                question: "Como transferir meus dados para outro aparelho?",
                answer: "Vá em Perfil → Ajustes → Backup Portátil (.ufopbackup), defina uma senha forte e exporte o arquivo criptografado."
            )
        ]
    }

    @ViewBuilder
    private var faqSubtabContent: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("AVISO DE INDEPENDÊNCIA")
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))

            Text("Projeto estudantil sem vínculo institucional")
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

            Text("Este é um aplicativo independente, desenvolvido por estudantes e sem qualquer vínculo oficial, endosso ou afiliação com a Universidade Federal de Ouro Preto (UFOP).")
                .font(.system(size: 12))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, highContrast: hc)

        VStack(spacing: 10) {
            ForEach(faqEntries) { entry in
                DisclosureGroup(
                    isExpanded: Binding(
                        get: { expandedFaqIds.contains(entry.id) },
                        set: { expanded in
                            if expanded { expandedFaqIds.insert(entry.id) }
                            else { expandedFaqIds.remove(entry.id) }
                        }
                    )
                ) {
                    Text(entry.answer)
                        .font(.system(size: 13))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 6)
                } label: {
                    Text(entry.question)
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                        .multilineTextAlignment(.leading)
                }
                .tint(primaryAccent)
                .padding(14)
                .liquidGlassCard(cornerRadius: 16, highContrast: hc)
            }
        }

        Button(role: .destructive) {
            showSignOutFirstAlert = true
        } label: {
            Label("Sair da conta e apagar dados locais", systemImage: "rectangle.portrait.and.arrow.right")
                .font(.system(size: 14, weight: .bold, design: .rounded))
                .foregroundStyle(Color(hex: 0xDC2626))
                .frame(maxWidth: .infinity, minHeight: max(48, minTouch))
                .background(RoundedRectangle(cornerRadius: 16).fill(Color(hex: 0xDC2626).opacity(colorScheme == .dark ? 0.18 : 0.10)))
                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color(hex: 0xDC2626).opacity(0.32), lineWidth: 1))
        }
        .buttonStyle(.plain)
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
                let report = activeStore.applyTranscriptImport(parsed)
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
                _ = try activeStore.importEncryptedBackup(text, password: backupPassword)
                backupFeedback = "Backup restaurado com sucesso!"
                backupError = nil
            } catch {
                backupError = error.localizedDescription
                backupFeedback = nil
            }
        }
    }
}

// MARK: - Schedule Share & Import Sheet (.ufopgrade)

public struct ScheduleShareImportSheetView: View {
    @Bindable public var store: CampusStore
    public let onClose: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    @State private var exportedJson: String = ""
    @State private var importJson: String = ""
    @State private var statusBanner: String?
    @State private var showFileImporter: Bool = false
    @State private var showFileExporter: Bool = false

    public init(store: CampusStore, onClose: @escaping () -> Void) {
        self.store = store
        self.onClose = onClose
    }

    public var body: some View {
        let accent = appearance?.accentColor ?? .green
        let hc = appearance?.accessibility.highContrastMode ?? false
        let primary = accent.primary(for: colorScheme)

        ZStack {
            LiquidGlassBackdrop(accent: accent, highContrast: hc)

            VStack(spacing: 0) {
                HStack {
                    Text("Compartilhar Grade")
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                    Spacer()
                    Button {
                        onClose()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .bold))
                            .frame(width: 38, height: 38)
                            .liquidGlassCapsule(selected: false, accent: primary, highContrast: hc)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 10)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 14) {
                        if let statusBanner {
                            Text(statusBanner)
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(CampusTheme.accessibleTextColor(primary, scheme: colorScheme))
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .liquidGlassCard(cornerRadius: 12, tint: primary, highContrast: hc)
                        }

                        VStack(alignment: .leading, spacing: 10) {
                            Text("Exportar grade (\(store.selectedSemesterFilter))")
                                .font(.system(size: 15, weight: .bold, design: .rounded))

                            HStack(spacing: 8) {
                                Button("Gerar & Copiar JSON") {
                                    exportedJson = store.exportSchedulePayload(semester: store.selectedSemesterFilter)
                                    UIPasteboard.general.string = exportedJson
                                    statusBanner = "Grade copiada para a área de transferência!"
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(primary)

                                Button("Salvar .ufopgrade") {
                                    exportedJson = store.exportSchedulePayload(semester: store.selectedSemesterFilter)
                                    showFileExporter = true
                                }
                                .buttonStyle(.bordered)
                            }
                        }
                        .padding(14)
                        .liquidGlassCard(cornerRadius: 18, tint: primary, highContrast: hc)

                        VStack(alignment: .leading, spacing: 10) {
                            Text("Importar grade de colega")
                                .font(.system(size: 15, weight: .bold, design: .rounded))

                            TextEditor(text: $importJson)
                                .font(.system(size: 12, design: .monospaced))
                                .scrollContentBackground(.hidden)
                                .frame(minHeight: 110)
                                .padding(10)
                                .background(RoundedRectangle(cornerRadius: 10).fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: hc)))

                            HStack(spacing: 8) {
                                Button("Importar JSON") {
                                    let count = store.importSchedulePayload(json: importJson)
                                    statusBanner = count > 0
                                        ? "\(count) disciplina(s) importada(s)!"
                                        : "Conteúdo inválido ou vazio."
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(primary)

                                Button("Abrir arquivo") {
                                    showFileImporter = true
                                }
                                .buttonStyle(.bordered)
                            }
                        }
                        .padding(14)
                        .liquidGlassCard(cornerRadius: 18, tint: primary, highContrast: hc)
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 28)
                }
            }
        }
        .fileExporter(
            isPresented: $showFileExporter,
            document: UfopTextDocument(text: exportedJson),
            contentType: .plainText,
            defaultFilename: "grade-ufop-\(store.selectedSemesterFilter).ufopgrade"
        ) { result in
            if case .success = result {
                statusBanner = "Arquivo .ufopgrade exportado!"
            }
        }
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [.data, .plainText, .json],
            allowsMultipleSelection: false
        ) { result in
            if case .success(let urls) = result, let url = urls.first {
                let accessed = url.startAccessingSecurityScopedResource()
                defer { if accessed { url.stopAccessingSecurityScopedResource() } }
                if let txt = try? String(contentsOf: url, encoding: .utf8) {
                    importJson = txt
                    let count = store.importSchedulePayload(json: txt)
                    statusBanner = count > 0 ? "\(count) disciplina(s) importada(s) do arquivo!" : "Arquivo inválido."
                }
            }
        }
    }
}

#Preview("ProfileView - Light") {
    ProfileView()
        .preferredColorScheme(.light)
}

#Preview("ProfileView - Dark") {
    ProfileView()
        .preferredColorScheme(.dark)
}
