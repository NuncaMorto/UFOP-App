import SwiftUI

// MARK: - 3. Ajustes (Personalização, Segurança, Acessibilidade, Manutenção)

internal struct ProfileSettingsSectionView: View {
    @Bindable var store: CampusStore
    @Bindable var appearance: CampusAppearanceController
    @Bindable var lockManager: AppLockManager
    let primaryAccent: Color
    let onPrimaryAccent: Color
    let highContrast: Bool
    let minTouch: CGFloat

    @Binding var newPinPassword: String
    @Binding var disablePinPassword: String
    @Binding var lockFeedback: String?
    @Binding var backupPassword: String
    @Binding var backupDocument: UfopTextDocument?
    @Binding var showBackupExporter: Bool
    @Binding var showBackupImporter: Bool
    @Binding var backupFeedback: String?
    @Binding var backupError: String?
    @Binding var showResetSeedConfirmation: Bool
    let onOpenMatrix: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        appearanceCustomizationCard
        securityAndLockCard
        encryptedBackupCard
        AccessibilityControlsSection(appearance: appearance)
        curriculumCatalogSettingsCard
        guidedTourSettingsCard
        maintenanceCard
    }

    private var appearanceCustomizationCard: some View {
        let modes: [(key: String, label: String)] = [("system", "Sistema"), ("light", "Claro"), ("dark", "Escuro")]
        let boldBoost = appearance.accessibility.boldTextBoost

        return VStack(alignment: .leading, spacing: 14) {
            Text("Personalização & Tema")
                .font(CampusTheme.font(size: 16, weight: .bold, boldBoost: boldBoost))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

            HStack(spacing: 8) {
                ForEach(modes, id: \.key) { mode in
                    let isSelected = appearance.appearanceModeRaw == mode.key
                    Button {
                        withAnimation(.snappy) {
                            appearance.appearanceModeRaw = mode.key
                        }
                    } label: {
                        Text(mode.label)
                            .font(CampusTheme.font(size: 13, weight: .bold, boldBoost: boldBoost))
                            .foregroundStyle(isSelected ? onPrimaryAccent : CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                            .frame(maxWidth: .infinity, minHeight: 38)
                            .liquidGlassCapsule(selected: isSelected, accent: primaryAccent, interactive: true, highContrast: highContrast)
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(minTouch)
                }
            }

            Text("Cor de destaque e pré-visualização")
                .font(CampusTheme.font(size: 13, weight: .semibold, boldBoost: boldBoost))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

            CampusAccentPalettePreviewGrid(appearance: appearance)

            Divider()
                .opacity(0.35)

            VStack(alignment: .leading, spacing: 8) {
                Text("Indicador de presença na Agenda")
                    .font(CampusTheme.font(size: 13, weight: .semibold, boldBoost: boldBoost))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

                let indicatorOptions: [(key: String, label: String, icon: String)] = [
                    ("BADGE", "Badge no card", "tag.fill"),
                    ("CARD_BORDER", "Borda colorida", "square")
                ]

                HStack(spacing: 8) {
                    ForEach(indicatorOptions, id: \.key) { option in
                        let isSelected = appearance.attendanceIndicatorStyle == option.key
                        Button {
                            withAnimation(.snappy) {
                                appearance.attendanceIndicatorStyle = option.key
                                store.setAttendanceIndicatorStyle(option.key)
                            }
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: option.icon)
                                    .font(.system(size: 12, weight: .semibold))
                                Text(option.label)
                                    .font(CampusTheme.font(size: 12, weight: .bold, boldBoost: boldBoost))
                                    .lineLimit(1)
                            }
                            .foregroundStyle(isSelected ? onPrimaryAccent : CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                            .frame(maxWidth: .infinity, minHeight: max(42, minTouch))
                            .liquidGlassCapsule(selected: isSelected, accent: primaryAccent, interactive: true, highContrast: highContrast)
                        }
                        .buttonStyle(.plain)
                        .minimumTouchTarget(minTouch)
                        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
                    }
                }

                Text(
                    appearance.attendanceIndicatorStyle == "CARD_BORDER"
                        ? "Remove a pílula interna e contorna o card na cor semântica do status (verde, âmbar ou vermelho)."
                        : "Padrão: exibe a pílula colorida de status (Vou à aula, Cancelada ou Vou faltar) dentro do card."
                )
                .font(CampusTheme.font(size: 11))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 24, tint: primaryAccent, highContrast: highContrast)
    }

    private var securityAndLockCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                Text("Segurança & Privacidade")
                    .font(CampusTheme.font(size: 16, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
            }

            Text("AES-256-GCM ativo para dados locais · PDFs processados em memória sem retenção de arquivos temporários.")
                .font(.system(size: 12))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

            if lockManager.isLockEnabled {
                Label("Bloqueio do aplicativo ativado", systemImage: "checkmark.seal.fill")
                    .font(CampusTheme.font(size: 13, weight: .bold))
                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))

                if lockManager.biometricAvailable {
                    Toggle(
                        "Usar \(lockManager.biometricLabel)",
                        isOn: Binding(
                            get: { lockManager.isBiometricEnabled },
                            set: { newVal in
                                lockFeedback = lockManager.setBiometricEnabled(newVal)
                            }
                        )
                    )
                    .tint(primaryAccent)
                }

                HStack(spacing: 8) {
                    SecureField("Senha atual para desativar", text: $disablePinPassword)
                        .textFieldStyle(.roundedBorder)
                    Button("Desativar") {
                        if let err = lockManager.disableLock(currentPassword: disablePinPassword) {
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
                        if let err = lockManager.enableLock(password: newPinPassword, allowBiometric: lockManager.biometricAvailable) {
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
        .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, highContrast: highContrast)
    }

    private var encryptedBackupCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Backup Portátil (.ufopbackup)")
                .font(CampusTheme.font(size: 16, weight: .bold))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

            Text("Exporte ou restaure disciplinas, horários, faltas, notas e avaliações usando criptografia PBKDF2 + AES-GCM.")
                .font(CampusTheme.font(size: 12))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

            SecureField("Senha do backup", text: $backupPassword)
                .textFieldStyle(.roundedBorder)

            HStack(spacing: 10) {
                Button {
                    do {
                        let content = try store.exportEncryptedBackup(password: backupPassword)
                        backupDocument = UfopTextDocument(text: content)
                        showBackupExporter = true
                    } catch {
                        backupError = error.localizedDescription
                        backupFeedback = nil
                    }
                } label: {
                    Label("Exportar", systemImage: "square.and.arrow.up")
                        .font(CampusTheme.font(size: 13, weight: .bold))
                        .foregroundStyle(onPrimaryAccent)
                        .frame(maxWidth: .infinity, minHeight: max(44, minTouch))
                        .liquidGlassCapsule(selected: true, accent: primaryAccent, interactive: true, highContrast: highContrast)
                }
                .buttonStyle(.plain)

                Button {
                    backupError = nil
                    showBackupImporter = true
                } label: {
                    Label("Restaurar", systemImage: "arrow.down.doc")
                        .font(CampusTheme.font(size: 13, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                        .frame(maxWidth: .infinity, minHeight: max(44, minTouch))
                        .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: highContrast)
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
        .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, highContrast: highContrast)
    }

    private var curriculumCatalogSettingsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Matriz Curricular & Catálogo UFOP")
                .font(CampusTheme.font(size: 16, weight: .bold))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

            Text("Consulte os \(UfopCurriculumCatalog.allCourses.count) cursos da UFOP ou altere a matriz de referência exibida.")
                .font(.system(size: 12))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

            Button {
                onOpenMatrix()
            } label: {
                Label("Abrir catálogo de cursos", systemImage: "books.vertical.fill")
                    .font(CampusTheme.font(size: 13, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                    .frame(maxWidth: .infinity, minHeight: max(48, minTouch))
                    .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: highContrast)
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: CampusTheme.cardCornerRadius, tint: primaryAccent, highContrast: highContrast)
    }

    private var guidedTourSettingsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Guia Interativo")
                .font(CampusTheme.font(size: 16, weight: .bold))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

            Text("Reveja a apresentação inicial com as principais ferramentas do Vida Acadêmica.")
                .font(.system(size: 12))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

            Button {
                store.guidedTourCompleted = false
            } label: {
                Label("Refazer Tour Guiado do App", systemImage: "map.fill")
                    .font(CampusTheme.font(size: 13, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                    .frame(maxWidth: .infinity, minHeight: max(44, minTouch))
                    .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: highContrast)
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, highContrast: highContrast)
    }

    private var maintenanceCard: some View {
        Button {
            showResetSeedConfirmation = true
        } label: {
            Label("Restaurar grade de demonstração 2026.2", systemImage: "arrow.counterclockwise")
                .font(CampusTheme.font(size: 13, weight: .bold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                .frame(maxWidth: .infinity, minHeight: 44)
                .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: highContrast)
        }
        .buttonStyle(.plain)
        .minimumTouchTarget(minTouch)
    }
}

// MARK: - 5. FAQ & Sign Out

private struct ProfileFaqEntry: Identifiable {
    let id: String
    let question: String
    let answer: String
}

internal struct ProfileFaqSectionView: View {
    let primaryAccent: Color
    let highContrast: Bool
    let minTouch: CGFloat
    @Binding var expandedFaqIds: Set<String>
    let onRequestSignOut: () -> Void

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.openURL) private var openURL

    private let faqEntries: [ProfileFaqEntry] = [
        ProfileFaqEntry(
            id: "offline",
            question: "Meus dados ficam salvos apenas no celular?",
            answer: "Sim. Tudo funciona offline-first com criptografia AES-256-GCM usando chave protegida no Keychain do iOS."
        ),
        ProfileFaqEntry(
            id: "pdfs",
            question: "O aplicativo guarda os PDFs que eu importo?",
            answer: "Não. O texto do Atestado de Matrícula, Plano de Ensino e Histórico Escolar é extraído em memória e qualquer cópia temporária é removida imediatamente."
        ),
        ProfileFaqEntry(
            id: "absences",
            question: "Como funciona o cálculo de faltas e aulas canceladas?",
            answer: "A referência de 25% da carga horária corresponde ao limite padrão (ex.: 60h = 18 faltas). Marcar uma aula como cancelada na Agenda nunca adiciona falta."
        ),
        ProfileFaqEntry(
            id: "cre",
            question: "Como calcular meu CRE e verificar a matriz?",
            answer: "Na aba Disciplinas (botão Matriz) ou no Resumo do Perfil, importe seu Histórico Escolar em PDF e simule notas futuras."
        ),
        ProfileFaqEntry(
            id: "backup",
            question: "Como transferir meus dados para outro aparelho?",
            answer: "Vá em Perfil → Ajustes → Backup Portátil (.ufopbackup), defina uma senha forte e exporte o arquivo criptografado."
        )
    ]

    var body: some View {
        let accessibleAccent = CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme)

        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                GitHubOctocatShape()
                    .fill(accessibleAccent)
                    .frame(width: 20, height: 20)
                    .accessibilityHidden(true)

                Text("AVISO DE INDEPENDÊNCIA")
                    .font(CampusTheme.font(size: 11, weight: .bold))
                    .foregroundStyle(accessibleAccent)
            }

            Text("Projeto estudantil independente e de código aberto")
                .font(CampusTheme.font(size: 15, weight: .bold))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

            Text("Este é um aplicativo independente, desenvolvido por estudantes sem qualquer vínculo oficial, endosso ou afiliação com a Universidade Federal de Ouro Preto (UFOP). O código-fonte é aberto, auditável e focado em privacidade local e rapidez offline.")
                .font(CampusTheme.font(size: 12))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                .fixedSize(horizontal: false, vertical: true)

            Button {
                if let url = URL(string: "https://github.com/NuncaMorto/Vida-Academica") {
                    openURL(url)
                }
            } label: {
                HStack(spacing: 8) {
                    GitHubOctocatShape()
                        .fill(accessibleAccent)
                        .frame(width: 16, height: 16)
                        .accessibilityHidden(true)
                    Text("Acessar repositório no GitHub")
                        .font(CampusTheme.font(size: 13, weight: .bold))
                        .foregroundStyle(accessibleAccent)
                }
                .frame(maxWidth: .infinity, minHeight: max(48, minTouch))
                .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: highContrast)
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
            .accessibilityLabel("Acessar repositório no GitHub")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .liquidGlassCard(cornerRadius: 24, tint: primaryAccent, highContrast: highContrast)

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
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 6)
                } label: {
                    Text(entry.question)
                        .font(CampusTheme.font(size: 14, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                        .multilineTextAlignment(.leading)
                }
                .tint(primaryAccent)
                .padding(14)
                .liquidGlassCard(cornerRadius: 20, highContrast: highContrast)
            }
        }

        Button(role: .destructive) {
            onRequestSignOut()
        } label: {
            Label("Sair da conta e apagar dados locais", systemImage: "rectangle.portrait.and.arrow.right")
                .font(CampusTheme.font(size: 14, weight: .bold))
                .foregroundStyle(Color(hex: 0xDC2626))
                .frame(maxWidth: .infinity, minHeight: max(48, minTouch))
                .background(RoundedRectangle(cornerRadius: 16).fill(Color(hex: 0xDC2626).opacity(colorScheme == .dark ? 0.18 : 0.10)))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .strokeBorder(
                            Color(hex: 0xDC2626).opacity(highContrast ? 0.65 : 0),
                            lineWidth: highContrast ? 1.5 : 0
                        )
                )
        }
        .buttonStyle(.plain)
    }
}

private struct GitHubOctocatShape: Shape {
    func path(in rect: CGRect) -> Path {
        let sx = rect.width / 24.0
        let sy = rect.height / 24.0
        func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: rect.minX + x * sx, y: rect.minY + y * sy)
        }

        var path = Path()
        path.move(to: pt(12.0, 0.3))
        path.addCurve(to: pt(0.0, 12.3), control1: pt(5.37, 0.3), control2: pt(0.0, 5.67))
        path.addCurve(to: pt(8.21, 23.69), control1: pt(0.0, 17.6), control2: pt(3.44, 22.1))
        path.addCurve(to: pt(9.03, 23.11), control1: pt(8.81, 23.8), control2: pt(9.03, 23.43))
        path.addCurve(to: pt(9.01, 21.05), control1: pt(9.03, 22.82), control2: pt(9.02, 22.06))
        path.addCurve(to: pt(4.97, 19.44), control1: pt(5.67, 21.78), control2: pt(4.97, 19.44))
        path.addCurve(to: pt(3.64, 17.68), control1: pt(4.42, 18.05), control2: pt(3.64, 17.68))
        path.addCurve(to: pt(3.72, 16.95), control1: pt(2.55, 16.93), control2: pt(3.72, 16.95))
        path.addCurve(to: pt(5.56, 18.19), control1: pt(4.92, 17.03), control2: pt(5.56, 18.19))
        path.addCurve(to: pt(9.05, 19.19), control1: pt(6.63, 20.02), control2: pt(8.37, 19.49))
        path.addCurve(to: pt(9.81, 17.59), control1: pt(9.16, 18.41), control2: pt(9.47, 17.89))
        path.addCurve(to: pt(4.34, 11.66), control1: pt(7.14, 17.29), control2: pt(4.34, 16.26))
        path.addCurve(to: pt(5.58, 8.44), control1: pt(4.34, 10.35), control2: pt(4.81, 9.28))
        path.addCurve(to: pt(5.70, 5.26), control1: pt(5.46, 8.14), control2: pt(5.04, 6.92))
        path.addCurve(to: pt(9.00, 6.49), control1: pt(5.70, 5.26), control2: pt(6.71, 4.94))
        path.addCurve(to: pt(12.0, 6.09), control1: pt(9.96, 6.22), control2: pt(10.98, 6.09))
        path.addCurve(to: pt(15.0, 6.49), control1: pt(13.02, 6.09), control2: pt(14.04, 6.23))
        path.addCurve(to: pt(18.30, 5.26), control1: pt(17.29, 4.94), control2: pt(18.30, 5.26))
        path.addCurve(to: pt(18.42, 8.44), control1: pt(18.95, 6.91), control2: pt(18.54, 8.13))
        path.addCurve(to: pt(19.66, 11.66), control1: pt(19.19, 9.28), control2: pt(19.66, 10.35))
        path.addCurve(to: pt(14.18, 17.58), control1: pt(19.66, 16.27), control2: pt(16.85, 17.28))
        path.addCurve(to: pt(14.99, 19.80), control1: pt(14.61, 17.95), control2: pt(14.99, 18.68))
        path.addCurve(to: pt(14.98, 23.09), control1: pt(14.99, 21.40), control2: pt(14.98, 22.70))
        path.addCurve(to: pt(15.81, 23.67), control1: pt(14.98, 23.41), control2: pt(15.20, 23.79))
        path.addCurve(to: pt(24.0, 12.3), control1: pt(20.56, 22.1), control2: pt(24.0, 17.6))
        path.addCurve(to: pt(12.0, 0.3), control1: pt(24.0, 5.67), control2: pt(18.63, 0.3))
        path.closeSubpath()
        return path
    }
}
