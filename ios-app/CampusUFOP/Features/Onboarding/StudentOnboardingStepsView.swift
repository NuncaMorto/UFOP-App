import SwiftUI

internal enum OnboardingSetupMethod: String, CaseIterable {
    case certificate = "Atestado (PDF)"
    case manual = "Manual"
    case backup = "Backup"
}

// MARK: - Step 1: Identidade, Atestado e Backup

internal struct OnboardingStep1View: View {
    @Bindable var store: CampusStore
    @Bindable var appearance: CampusAppearanceController
    @Binding var setupMethod: OnboardingSetupMethod
    @Binding var manualName: String
    @Binding var manualRegistration: String
    @Binding var manualCourseName: String
    @Binding var manualCampus: String
    let importedCourses: [DisciplineSummaryCard]
    let certificateStatusText: String?
    let step1Error: String?
    let accent: Color
    let highContrast: Bool
    let onSelectCertificate: () -> Void
    let onContinueToSyllabi: () -> Void
    let onSaveManualAndContinue: () -> Void
    let onSelectBackup: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
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
                ForEach(OnboardingSetupMethod.allCases, id: \.self) { method in
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
                        onSelectCertificate()
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
                            onContinueToSyllabi()
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

                    OnboardingTextField(title: "Nome completo", placeholder: "Ex.: Lucas Almeida", text: $manualName, highContrast: highContrast)
                    OnboardingTextField(title: "Matrícula (opcional)", placeholder: "Ex.: 24.2.1165", text: $manualRegistration, highContrast: highContrast)

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
                        OnboardingTextField(title: "Curso", placeholder: "Ex.: Ciência da Computação", text: $manualCourseName, highContrast: highContrast)
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
                        onSaveManualAndContinue()
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
                        onSelectBackup()
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
}

// MARK: - Step 2: Planos de Ensino

internal struct OnboardingStep2View: View {
    let courses: [DisciplineSummaryCard]
    let syllabusReportsByCourseId: [String: SyllabusImportReport]
    let minTouchHeight: CGFloat
    let accent: Color
    let onPrimaryAccent: Color
    let highContrast: Bool
    let onSendSyllabus: (DisciplineSummaryCard) -> Void
    let onContinue: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
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
                            onSendSyllabus(disc)
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
                    onContinue()
                } label: {
                    Text("Pular por enquanto")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                        .frame(maxWidth: .infinity, minHeight: minTouchHeight)
                        .liquidGlassCapsule(selected: false, accent: accent, highContrast: highContrast)
                }
                .buttonStyle(.plain)

                LiquidGlassPrimaryButton(
                    tint: accent,
                    foreground: onPrimaryAccent,
                    minHeight: minTouchHeight
                ) {
                    onContinue()
                } label: {
                    Text("Continuar")
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                }
            }
        }
    }
}

// MARK: - Step 3: Personalização & Acessibilidade Prompt

internal struct OnboardingStep3View: View {
    @Bindable var appearance: CampusAppearanceController
    let accent: Color
    let highContrast: Bool
    let onConfigureAccessibility: () -> Void
    let onFinish: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
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
                        onConfigureAccessibility()
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
                        onFinish()
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
}

// MARK: - Step 4: Acessibilidade

internal struct OnboardingStep4View: View {
    @Bindable var appearance: CampusAppearanceController
    let accent: Color
    let highContrast: Bool
    let onFinish: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
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
                onFinish()
            } label: {
                Label("Concluir e iniciar Tour Guiado", systemImage: "checkmark.circle.fill")
                    .font(.system(size: 15, weight: .bold, design: .rounded))
            }
        }
    }
}
