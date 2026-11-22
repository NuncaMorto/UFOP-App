import SwiftUI
import UniformTypeIdentifiers

// MARK: - Shared Reusable Accessibility Controls Panel

public struct AccessibilityControlsSection: View {
    @Bindable public var appearance: CampusAppearanceController
    @Environment(\.colorScheme) private var colorScheme

    public init(appearance: CampusAppearanceController) {
        self.appearance = appearance
    }

    public var body: some View {
        let accent = appearance.accentColor.primary(for: colorScheme)
        let hc = appearance.accessibility.highContrastMode

        VStack(alignment: .leading, spacing: 14) {
            Button {
                appearance.applyVoiceOverQuickPreset()
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "accessibility")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(accent)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Ativar perfil otimizado para VoiceOver")
                            .font(.system(size: 14, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                        Text("Combina descrições claras, alto contraste, fonte ampliada e alvos táteis de 56pt.")
                            .font(.system(size: 12))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    }

                    Spacer()
                }
                .padding(14)
                .liquidGlassCard(cornerRadius: 16, tint: accent, interactive: true, highContrast: hc)
            }
            .buttonStyle(.plain)

            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("Tamanho da fonte")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    Spacer()
                    Text("\(Int(round(appearance.accessibility.fontScaleMultiplier * 100)))%")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(accent)
                }

                Slider(
                    value: Binding(
                        get: { appearance.accessibility.fontScaleMultiplier },
                        set: { appearance.accessibility.fontScaleMultiplier = $0 }
                    ),
                    in: 1.0...1.45,
                    step: 0.05
                )
                .tint(accent)
            }
            .padding(14)
            .liquidGlassCard(cornerRadius: 16, highContrast: hc)

            toggleRow(
                title: "Alto contraste (WCAG AAA)",
                subtitle: "Bordas nítidas e texto em contraste máximo",
                isOn: Binding(
                    get: { appearance.accessibility.highContrastMode },
                    set: { appearance.accessibility.highContrastMode = $0 }
                ),
                accent: accent,
                highContrast: hc
            )

            toggleRow(
                title: "Texto em negrito reforçado",
                subtitle: "Melhora legibilidade em leitura rápida pelo campus",
                isOn: Binding(
                    get: { appearance.accessibility.boldTextBoost },
                    set: { appearance.accessibility.boldTextBoost = $0 }
                ),
                accent: accent,
                highContrast: hc
            )

            toggleRow(
                title: "Reduzir animações e movimento",
                subtitle: "Substitui transições expansivas por trocas suaves",
                isOn: Binding(
                    get: { appearance.accessibility.reduceMotion },
                    set: { appearance.accessibility.reduceMotion = $0 }
                ),
                accent: accent,
                highContrast: hc
            )

            toggleRow(
                title: "Alvos táteis ampliados (56pt)",
                subtitle: "Aumenta botões e pílulas interativas para toque confortável",
                isOn: Binding(
                    get: { appearance.accessibility.largeTouchTargets },
                    set: { appearance.accessibility.largeTouchTargets = $0 }
                ),
                accent: accent,
                highContrast: hc
            )
        }
    }

    private func toggleRow(
        title: String,
        subtitle: String,
        isOn: Binding<Bool>,
        accent: Color,
        highContrast: Bool
    ) -> some View {
        Toggle(isOn: isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            }
        }
        .tint(accent)
        .padding(14)
        .liquidGlassCard(cornerRadius: 16, highContrast: highContrast)
    }
}

// MARK: - 4-Step Student Onboarding Overlay (v1.8.0 - v1.9.6 Parity)

public struct StudentOnboardingView: View {
    @Bindable public var store: CampusStore
    @Bindable public var appearance: CampusAppearanceController
    @Bindable public var lockManager: AppLockManager
    @Environment(\.colorScheme) private var colorScheme

    private enum SetupMethod: String, CaseIterable {
        case certificate = "Atestado (PDF)"
        case manual = "Manual"
        case backup = "Backup"
    }

    @State private var step: Int = 1
    @State private var setupMethod: SetupMethod = .certificate

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
                stepHeaderBar(accent: accent, highContrast: hc)
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 8)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {
                        switch step {
                        case 1:
                            step1IdentityAndCertificate(accent: accent, highContrast: hc)
                        case 2:
                            step2SyllabiUpload(accent: accent, highContrast: hc)
                        case 3:
                            step3PersonalizationAndA11yPrompt(accent: accent, highContrast: hc)
                        default:
                            step4Accessibility(accent: accent, highContrast: hc)
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

    private func stepHeaderBar(accent: Color, highContrast: Bool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("CONFIGURAÇÃO INICIAL · ETAPA \(step) DE 4")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .tracking(1.3)
                    .foregroundStyle(accent)

                Spacer()

                if step > 1 {
                    Button {
                        withAnimation(.snappy) { step -= 1 }
                    } label: {
                        Text("Voltar")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(40)
                }
            }

            HStack(spacing: 6) {
                ForEach(1...4, id: \.self) { idx in
                    Capsule(style: .continuous)
                        .fill(idx <= step ? accent : CampusTheme.secondarySurface(for: colorScheme, highContrast: highContrast))
                        .frame(height: 5)
                }
            }
        }
    }

    // MARK: Step 1

    private func step1IdentityAndCertificate(accent: Color, highContrast: Bool) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Bem-vindo ao Vida Acadêmica")
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                Text("Importe seu Atestado de Matrícula (PDF), configure manualmente ou restaure um backup criptografado.")
                    .font(.system(size: 14))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

                Text("Aplicativo independente para estudantes, sem vínculo oficial ou afiliação com a UFOP.")
                    .font(.system(size: 12))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
            }

            Picker("Método de cadastro", selection: $setupMethod) {
                ForEach(SetupMethod.allCases, id: \.self) { method in
                    Text(method.rawValue).tag(method)
                }
            }
            .pickerStyle(.segmented)

            if let step1Error {
                Text(step1Error)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color(hex: 0xEF4444))
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: 0xEF4444).opacity(0.14)))
            }

            switch setupMethod {
            case .certificate:
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 10) {
                        Image(systemName: "doc.text.magnifyingglass")
                            .font(.system(size: 22))
                            .foregroundStyle(accent)

                        VStack(alignment: .leading, spacing: 3) {
                            Text("Atestado de Matrícula (PDF)")
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                            Text("O PDF é lido localmente em memória e descartado logo em seguida. Disciplinas EAD e salas do ICEB / Bloco de Salas são detectadas automaticamente.")
                                .font(.system(size: 12))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                        }
                    }

                    LiquidGlassPrimaryButton(
                        tint: accent,
                        foreground: appearance.accentColor.onPrimary(for: colorScheme),
                        minHeight: appearance.accessibility.minTouchHeight
                    ) {
                        step1Error = nil
                        showCertificatePicker = true
                    } label: {
                        Label("Selecionar Atestado (PDF)", systemImage: "arrow.up.doc.fill")
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                    }

                    if let certificateStatusText {
                        Text(certificateStatusText)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(accent)
                    }

                    if !importedCourses.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            ForEach(importedCourses) { disc in
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("\(disc.code) · \(disc.name)")
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                                        Text(store.isCourseEad(disc.id) ? "A distância (EAD) · \(disc.workloadHours)h" : "\(disc.location) · \(disc.workloadHours)h")
                                            .font(.system(size: 11))
                                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                                    }
                                    Spacer()
                                    if store.isCourseEad(disc.id) {
                                        Text("EAD")
                                            .font(.system(size: 10, weight: .bold, design: .rounded))
                                            .foregroundStyle(CampusTheme.mathBlue)
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 3)
                                            .background(Capsule().fill(CampusTheme.selectedPillBackground(for: colorScheme)))
                                    }
                                }
                                .padding(10)
                                .background(RoundedRectangle(cornerRadius: 12).fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: highContrast)))
                            }
                        }

                        LiquidGlassPrimaryButton(
                            tint: CampusTheme.mathBlue,
                            foreground: .white,
                            minHeight: appearance.accessibility.minTouchHeight
                        ) {
                            withAnimation(.snappy) { step = 2 }
                        } label: {
                            Label("Continuar para Planos de Ensino", systemImage: "arrow.right")
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                        }
                    }
                }
                .padding(18)
                .liquidGlassCard(tint: accent, highContrast: highContrast)

            case .manual:
                VStack(alignment: .leading, spacing: 14) {
                    Text("Configure seu perfil e adicione suas disciplinas depois (com nome de professor opcional).")
                        .font(.system(size: 13))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

                    onboardingField(title: "Nome completo", placeholder: "Ex.: Lucas Almeida", text: $manualName, highContrast: highContrast)
                    onboardingField(title: "Matrícula (opcional)", placeholder: "Ex.: 24.2.1165", text: $manualRegistration, highContrast: highContrast)

                    if !UfopCurriculumCatalog.allCourses.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Curso")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

                            Picker("Curso", selection: $manualCourseName) {
                                ForEach(UfopCurriculumCatalog.allCourses) { course in
                                    Text("\(course.name) (\(course.campus))").tag(course.name)
                                }
                            }
                            .pickerStyle(.menu)
                            .tint(accent)
                        }
                    } else {
                        onboardingField(title: "Curso", placeholder: "Ex.: Ciência da Computação", text: $manualCourseName, highContrast: highContrast)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Campus")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                        Picker("Campus", selection: $manualCampus) {
                            Text("Ouro Preto").tag("Ouro Preto")
                            Text("Mariana").tag("Mariana")
                            Text("João Monlevade").tag("João Monlevade")
                        }
                        .pickerStyle(.segmented)
                    }

                    LiquidGlassPrimaryButton(
                        tint: accent,
                        foreground: appearance.accentColor.onPrimary(for: colorScheme),
                        minHeight: appearance.accessibility.minTouchHeight
                    ) {
                        store.studentProfile.fullName = manualName.trimmingCharacters(in: .whitespacesAndNewlines)
                        store.studentProfile.registrationNumber = manualRegistration.trimmingCharacters(in: .whitespacesAndNewlines)
                        store.studentProfile.courseName = manualCourseName
                        store.studentProfile.campusName = manualCampus
                        _ = store.setActiveCampus(campusId: manualCampus)
                        importedCourses = []
                        withAnimation(.snappy) { step = 3 }
                    } label: {
                        Label("Continuar para Personalização", systemImage: "arrow.right")
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                    }
                }
                .padding(18)
                .liquidGlassCard(tint: accent, highContrast: highContrast)

            case .backup:
                VStack(alignment: .leading, spacing: 14) {
                    Text("Importe um arquivo `.ufopbackup` criptografado gerado anteriormente neste aparelho ou no Android.")
                        .font(.system(size: 13))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

                    LiquidGlassPrimaryButton(
                        tint: accent,
                        foreground: appearance.accentColor.onPrimary(for: colorScheme),
                        minHeight: appearance.accessibility.minTouchHeight
                    ) {
                        step1Error = nil
                        showBackupPicker = true
                    } label: {
                        Label("Selecionar arquivo .ufopbackup", systemImage: "key.fill")
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                    }
                }
                .padding(18)
                .liquidGlassCard(tint: accent, highContrast: highContrast)
            }
        }
    }

    // MARK: Step 2

    private func step2SyllabiUpload(accent: Color, highContrast: Bool) -> some View {
        let courses = importedCourses.isEmpty ? store.filteredDisciplines : importedCourses

        return VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Planos de Ensino (Opcional)")
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                Text("Envie o PDF do plano de ensino em cada disciplina correspondente. O app valida se é o plano correto e verifica divisão de notas e datas.")
                    .font(.system(size: 14))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            }

            ForEach(courses) { disc in
                let report = syllabusReportsByCourseId[disc.id]
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(disc.code)
                                .font(.system(size: 11, weight: .bold, design: .rounded))
                                .foregroundStyle(accent)
                            Text(disc.name)
                                .font(.system(size: 15, weight: .bold))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                            if !disc.professor.isEmpty {
                                Text("Prof.: \(disc.professor)")
                                    .font(.system(size: 12))
                                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                            }
                        }

                        Spacer()

                        Button {
                            syllabusTargetCourse = disc
                            showSyllabusPicker = true
                        } label: {
                            Label(report?.matchedCourse == true ? "Reenviar" : "Enviar PDF", systemImage: "doc.badge.plus")
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                                .foregroundStyle(accent)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .liquidGlassCapsule(selected: false, accent: accent, highContrast: highContrast)
                        }
                        .buttonStyle(.plain)
                    }

                    if let report {
                        if let err = report.errorMessage {
                            Text(err)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(Color(hex: 0xEF4444))
                        } else if let warn = report.warningMessage {
                            HStack(alignment: .top, spacing: 8) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundStyle(Color(hex: 0xF59E0B))
                                Text(warn)
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                            }
                            .padding(10)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color(hex: 0xF59E0B).opacity(0.14)))
                        } else {
                            HStack(spacing: 6) {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(CampusTheme.algebraGreen)
                                Text("Plano validado · \(report.parsedAssessments.count) avaliação(ões) importada(s)")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(CampusTheme.algebraGreen)
                            }
                        }
                    }
                }
                .padding(14)
                .liquidGlassCard(cornerRadius: 18, tint: accent, highContrast: highContrast)
            }

            HStack(spacing: 12) {
                Button {
                    withAnimation(.snappy) { step = 3 }
                } label: {
                    Text("Pular por enquanto")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                        .frame(maxWidth: .infinity, minHeight: appearance.accessibility.minTouchHeight)
                        .liquidGlassCapsule(selected: false, accent: accent, highContrast: highContrast)
                }
                .buttonStyle(.plain)

                LiquidGlassPrimaryButton(
                    tint: accent,
                    foreground: appearance.accentColor.onPrimary(for: colorScheme),
                    minHeight: appearance.accessibility.minTouchHeight
                ) {
                    withAnimation(.snappy) { step = 3 }
                } label: {
                    Text("Continuar")
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                }
            }
        }
    }

    // MARK: Step 3

    private func step3PersonalizationAndA11yPrompt(accent: Color, highContrast: Bool) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Personalização")
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                Text("Escolha tema claro ou escuro e uma das 7 cores de destaque. Você poderá alterar tudo em Perfil → Configurações.")
                    .font(.system(size: 14))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            }

            VStack(alignment: .leading, spacing: 12) {
                Text("Aparência")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                Picker("Modo", selection: $appearance.appearanceModeRaw) {
                    Text("Sistema").tag("system")
                    Text("Claro").tag("light")
                    Text("Escuro").tag("dark")
                }
                .pickerStyle(.segmented)

                Text("Cor principal")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                    .padding(.top, 4)

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 84), spacing: 10)], spacing: 10) {
                    ForEach(CampusAccentColor.allCases) { option in
                        let isSelected = appearance.accentColor == option
                        let swatch = option.primary(for: colorScheme)
                        Button {
                            withAnimation(.snappy) { appearance.accentColor = option }
                        } label: {
                            VStack(spacing: 6) {
                                Circle()
                                    .fill(swatch)
                                    .frame(width: 28, height: 28)
                                    .overlay(
                                        Image(systemName: isSelected ? "checkmark" : "")
                                            .font(.system(size: 12, weight: .bold))
                                            .foregroundStyle(option.onPrimary(for: colorScheme))
                                    )
                                Text(option.displayName)
                                    .font(.system(size: 12, weight: isSelected ? .bold : .medium, design: .rounded))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .liquidGlassCard(cornerRadius: 14, tint: isSelected ? swatch : nil, interactive: true, highContrast: highContrast)
                            .overlay(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .strokeBorder(isSelected ? swatch : .clear, lineWidth: 2)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(16)
            .liquidGlassCard(tint: accent, highContrast: highContrast)

            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    Image(systemName: "accessibility")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(accent)

                    VStack(alignment: .leading, spacing: 3) {
                        Text("Precisa configurar acessibilidade?")
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                        Text("Oferecemos ajustes para leitor de tela (VoiceOver), fonte ampliada, alto contraste, negrito e alvos táteis de 56pt.")
                            .font(.system(size: 12))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                    }
                }

                HStack(spacing: 10) {
                    Button {
                        withAnimation(.snappy) { step = 4 }
                    } label: {
                        Text("Sim, configurar agora")
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .foregroundStyle(accent)
                            .frame(maxWidth: .infinity, minHeight: appearance.accessibility.minTouchHeight)
                            .liquidGlassCapsule(selected: false, accent: accent, highContrast: highContrast)
                    }
                    .buttonStyle(.plain)

                    LiquidGlassPrimaryButton(
                        tint: accent,
                        foreground: appearance.accentColor.onPrimary(for: colorScheme),
                        minHeight: appearance.accessibility.minTouchHeight
                    ) {
                        finishOnboarding()
                    } label: {
                        Text("Não · Concluir")
                            .font(.system(size: 14, weight: .bold, design: .rounded))
                    }
                }
            }
            .padding(16)
            .liquidGlassCard(tint: accent, highContrast: highContrast)
        }
    }

    // MARK: Step 4

    private func step4Accessibility(accent: Color, highContrast: Bool) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Acessibilidade")
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                Text("Ajuste conforme sua preferência visual e motora.")
                    .font(.system(size: 14))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            }

            AccessibilityControlsSection(appearance: appearance)

            LiquidGlassPrimaryButton(
                tint: accent,
                foreground: appearance.accentColor.onPrimary(for: colorScheme),
                minHeight: appearance.accessibility.minTouchHeight
            ) {
                finishOnboarding()
            } label: {
                Label("Concluir e iniciar Tour Guiado", systemImage: "checkmark.circle.fill")
                    .font(.system(size: 15, weight: .bold, design: .rounded))
            }
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
            pendingBackupBundle = nil
            backupPassword = ""
        } catch {
            step1Error = error.localizedDescription
        }
    }

    private func onboardingField(title: String, placeholder: String, text: Binding<String>, highContrast: Bool) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            TextField(placeholder, text: text)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(RoundedRectangle(cornerRadius: 12).fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: highContrast)))
        }
    }
}
