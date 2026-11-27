import SwiftUI
import UniformTypeIdentifiers

// MARK: - 4-Step Student Onboarding Overlay (v1.8.0 - v1.9.6 Parity)

public struct StudentOnboardingView: View {
    @Bindable public var store: CampusStore
    @Bindable public var appearance: CampusAppearanceController
    @Bindable public var lockManager: AppLockManager
    @Environment(\.colorScheme) private var colorScheme

    @State private var step: Int = 1
    @State private var setupMethod: OnboardingSetupMethod = .certificate

    // Passo 1: PDF Atestado
    @State private var showCertificatePicker: Bool = false
    @State private var importedCourses: [DisciplineSummaryCard] = []
    @State private var certificateStatusText: String?
    @State private var step1Error: String?

    // Passo 1: Manual
    @State private var manualName: String = ""
    @State private var manualRegistration: String = ""
    @State private var manualCourseName: String = "Ciência da Computação"
    @State private var manualCampus: String = "Ouro Preto"

    // Passo 1: Backup (.ufopbackup)
    @State private var showBackupPicker: Bool = false
    @State private var pendingBackupBundle: String?
    @State private var backupPassword: String = ""
    @State private var showBackupPasswordPrompt: Bool = false

    // Passo 2: Planos de ensino por disciplina
    @State private var showSyllabusPicker: Bool = false
    @State private var syllabusTargetCourse: DisciplineSummaryCard?
    @State private var syllabusReportsByCourseId: [String: SyllabusImportReport] = [:]

    public init(store: CampusStore, appearance: CampusAppearanceController, lockManager: AppLockManager) {
        self.store = store
        self.appearance = appearance
        self.lockManager = lockManager
    }

    public var body: some View {
        let accent = appearance.accentColor.primary(for: colorScheme)
        let hc = appearance.accessibility.highContrastMode

        ZStack {
            LiquidGlassBackdrop(accent: appearance.accentColor, highContrast: hc)

            VStack(spacing: 0) {
                OnboardingStepHeaderBar(
                    step: step,
                    accent: accent,
                    highContrast: hc,
                    onBack: {
                        withAnimation(.snappy) { step -= 1 }
                    }
                )
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 8)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {
                        switch step {
                        case 1:
                            OnboardingStep1View(
                                store: store,
                                appearance: appearance,
                                setupMethod: $setupMethod,
                                manualName: $manualName,
                                manualRegistration: $manualRegistration,
                                manualCourseName: $manualCourseName,
                                manualCampus: $manualCampus,
                                importedCourses: importedCourses,
                                certificateStatusText: certificateStatusText,
                                step1Error: step1Error,
                                accent: accent,
                                highContrast: hc,
                                onSelectCertificate: {
                                    step1Error = nil
                                    showCertificatePicker = true
                                },
                                onContinueToSyllabi: {
                                    withAnimation(.snappy) { step = 2 }
                                },
                                onSaveManualAndContinue: {
                                    store.studentProfile.fullName = manualName.trimmingCharacters(in: .whitespacesAndNewlines)
                                    store.studentProfile.registrationNumber = manualRegistration.trimmingCharacters(in: .whitespacesAndNewlines)
                                    store.studentProfile.courseName = manualCourseName
                                    store.studentProfile.campusName = manualCampus
                                    _ = store.setActiveCampus(campusId: manualCampus)
                                    importedCourses = []
                                    withAnimation(.snappy) { step = 3 }
                                },
                                onSelectBackup: {
                                    step1Error = nil
                                    showBackupPicker = true
                                }
                            )
                        case 2:
                            OnboardingStep2View(
                                courses: importedCourses.isEmpty ? store.filteredDisciplines : importedCourses,
                                syllabusReportsByCourseId: syllabusReportsByCourseId,
                                minTouchHeight: appearance.accessibility.minTouchHeight,
                                accent: accent,
                                onPrimaryAccent: appearance.accentColor.onPrimary(for: colorScheme),
                                highContrast: hc,
                                onSendSyllabus: { disc in
                                    syllabusTargetCourse = disc
                                    showSyllabusPicker = true
                                },
                                onContinue: {
                                    withAnimation(.snappy) { step = 3 }
                                }
                            )
                        case 3:
                            OnboardingStep3View(
                                appearance: appearance,
                                accent: accent,
                                highContrast: hc,
                                onConfigureAccessibility: {
                                    withAnimation(.snappy) { step = 4 }
                                },
                                onFinish: {
                                    finishOnboarding()
                                }
                            )
                        default:
                            OnboardingStep4View(
                                appearance: appearance,
                                accent: accent,
                                highContrast: hc,
                                onFinish: {
                                    finishOnboarding()
                                }
                            )
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, 32)
                }
            }
        }
        .fileImporter(
            isPresented: $showCertificatePicker,
            allowedContentTypes: [.pdf],
            allowsMultipleSelection: false
        ) { result in
            handleCertificateSelection(result)
        }
        .fileImporter(
            isPresented: $showSyllabusPicker,
            allowedContentTypes: [.pdf],
            allowsMultipleSelection: false
        ) { result in
            handleSyllabusSelection(result)
        }
        .fileImporter(
            isPresented: $showBackupPicker,
            allowedContentTypes: [.plainText, .data],
            allowsMultipleSelection: false
        ) { result in
            handleBackupFileSelection(result)
        }
        .alert("Senha do backup criptografado", isPresented: $showBackupPasswordPrompt) {
            SecureField("Senha do arquivo .ufopbackup", text: $backupPassword)
            Button("Restaurar") {
                restorePendingBackup()
            }
            Button("Cancelar", role: .cancel) {
                pendingBackupBundle = nil
                backupPassword = ""
            }
        } message: {
            Text("Informe a senha usada quando o backup foi criado.")
        }
    }

    // MARK: Handlers

    private func finishOnboarding() {
        store.studentProfile.onboardingCompleted = true
        store.guidedTourCompleted = false
        store.persistEncryptedState()
    }

    private func handleCertificateSelection(_ result: Result<[URL], Error>) {
        step1Error = nil
        do {
            guard let url = try result.get().first else { return }
            let rawText = try SecurePdfProcessor.extractTextAndDeleteTemp(from: url)
            let parsed = try UfopPdfParsers.parseEnrollmentCertificate(text: rawText)
            let created = store.applyCertificateImport(parsed, completeOnboarding: false)
            importedCourses = created
            certificateStatusText = "Atestado importado: \(created.count) disciplina(s) de \(parsed.semester)."
        } catch {
            step1Error = error.localizedDescription
        }
    }

    private func handleSyllabusSelection(_ result: Result<[URL], Error>) {
        guard let target = syllabusTargetCourse else { return }
        do {
            guard let url = try result.get().first else { return }
            let rawText = try SecurePdfProcessor.extractTextAndDeleteTemp(from: url)
            let report = UfopPdfParsers.parseSyllabus(
                text: rawText,
                targetCourseCode: target.code,
                targetCourseName: target.name,
                semester: target.semester
            )
            if report.matchedCourse {
                _ = store.applySyllabusImport(report, forCourseId: target.id)
                if let refreshed = store.disciplines.first(where: { $0.id == target.id }),
                   let idx = importedCourses.firstIndex(where: { $0.id == target.id }) {
                    importedCourses[idx] = refreshed
                }
            }
            syllabusReportsByCourseId[target.id] = report
        } catch {
            syllabusReportsByCourseId[target.id] = SyllabusImportReport(
                matchedCourse: false,
                matchedCourseCode: nil,
                matchedCourseTitle: nil,
                extractedProfessor: "",
                hasGradeDistribution: false,
                hasAssessmentDates: false,
                parsedAssessments: [],
                warningMessage: nil,
                errorMessage: error.localizedDescription
            )
        }
    }

    private func handleBackupFileSelection(_ result: Result<[URL], Error>) {
        step1Error = nil
        do {
            guard let url = try result.get().first else { return }
            let accessed = url.startAccessingSecurityScopedResource()
            defer { if accessed { url.stopAccessingSecurityScopedResource() } }
            pendingBackupBundle = try String(contentsOf: url, encoding: .utf8)
            showBackupPasswordPrompt = true
        } catch {
            step1Error = "Não foi possível ler o arquivo de backup."
        }
    }

    private func restorePendingBackup() {
        guard let bundle = pendingBackupBundle else { return }
        do {
            _ = try store.importEncryptedBackup(bundle, password: backupPassword)
            appearance.attendanceIndicatorStyle = store.attendanceIndicatorStyle
            pendingBackupBundle = nil
            backupPassword = ""
        } catch {
            step1Error = error.localizedDescription
        }
    }
}
