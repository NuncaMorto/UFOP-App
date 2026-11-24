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

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        appearanceCustomizationCard
        securityAndLockCard
        encryptedBackupCard
        AccessibilityControlsSection(appearance: appearance)
        guidedTourSettingsCard
        maintenanceCard
    }

    private var appearanceCustomizationCard: some View {
        let modes: [(key: String, label: String)] = [("system", "Sistema"), ("light", "Claro"), ("dark", "Escuro")]

        return VStack(alignment: .leading, spacing: 14) {
            Text("Personalização & Tema")
                .font(.system(size: 16, weight: .bold, design: .rounded))
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
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .foregroundStyle(isSelected ? onPrimaryAccent : CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                            .frame(maxWidth: .infinity, minHeight: 38)
                            .liquidGlassCapsule(selected: isSelected, accent: primaryAccent, interactive: true, highContrast: highContrast)
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(minTouch)
                }
            }

            Text("Cor de destaque")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(CampusAccentColor.allCases) { accentOption in
                        let isSelected = appearance.accentColor == accentOption
                        let swatch = accentOption.primary(for: colorScheme)
                        Button {
                            withAnimation(.spring(response: 0.25, dampingFraction: 0.85)) {
                                appearance.accentColor = accentOption
                            }
                        } label: {
                            HStack(spacing: 6) {
                                Circle()
                                    .fill(swatch)
                                    .frame(width: 18, height: 18)
                                Text(accentOption.displayName)
                                    .font(.system(size: 12, weight: .bold, design: .rounded))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                            }
                            .padding(.horizontal, 12)
                            .frame(minHeight: 38)
                            .liquidGlassCapsule(selected: isSelected, accent: swatch, interactive: true, highContrast: highContrast)
                        }
                        .buttonStyle(.plain)
                        .minimumTouchTarget(minTouch)
                    }
                }
            }
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, highContrast: highContrast)
    }

    private var securityAndLockCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                Text("Segurança & Privacidade")
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
            }

            Text("AES-256-GCM ativo para dados locais · PDFs processados em memória sem retenção de arquivos temporários.")
                .font(.system(size: 12))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

            if lockManager.isLockEnabled {
                Label("Bloqueio do aplicativo ativado", systemImage: "checkmark.seal.fill")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
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
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

            Text("Exporte ou restaure disciplinas, horários, faltas, notas e avaliações usando criptografia PBKDF2 + AES-GCM.")
                .font(.system(size: 12))
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
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(onPrimaryAccent)
                        .frame(maxWidth: .infinity, minHeight: 42)
                        .liquidGlassCapsule(selected: true, accent: primaryAccent, interactive: true, highContrast: highContrast)
                }
                .buttonStyle(.plain)

                Button {
                    backupError = nil
                    showBackupImporter = true
                } label: {
                    Label("Restaurar", systemImage: "arrow.down.doc")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                        .frame(maxWidth: .infinity, minHeight: 42)
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

    private var guidedTourSettingsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Guia Interativo")
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

            Text("Reveja a apresentação inicial com as principais ferramentas do Vida Acadêmica.")
                .font(.system(size: 12))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

            Button {
                store.guidedTourCompleted = false
            } label: {
                Label("Refazer Tour Guiado do App", systemImage: "map.fill")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
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
                .font(.system(size: 13, weight: .bold, design: .rounded))
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
        VStack(alignment: .leading, spacing: 6) {
            Text("AVISO DE INDEPENDÊNCIA")
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))

            Text("Projeto estudantil sem vínculo institucional")
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

            Text("Este é um aplicativo independente, desenvolvido por estudantes e sem qualquer vínculo oficial, endosso ou afiliação com a Universidade Federal de Ouro Preto (UFOP).")
                .font(.system(size: 12))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, highContrast: highContrast)

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
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                        .multilineTextAlignment(.leading)
                }
                .tint(primaryAccent)
                .padding(14)
                .liquidGlassCard(cornerRadius: 16, highContrast: highContrast)
            }
        }

        Button(role: .destructive) {
            onRequestSignOut()
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
}
