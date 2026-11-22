import SwiftUI

public struct AppLockOverlayView: View {
    @Bindable public var lockManager: AppLockManager
    @Environment(CampusAppearanceController.self) private var appearance
    @Environment(\.colorScheme) private var colorScheme

    @State private var passwordInput: String = ""
    @State private var errorMessage: String?
    @State private var didAttemptAutoBiometric: Bool = false

    public init(lockManager: AppLockManager) {
        self.lockManager = lockManager
    }

    public var body: some View {
        let accent = appearance.accentColor.primary(for: colorScheme)
        let highContrast = appearance.accessibility.highContrastMode

        ZStack {
            LiquidGlassBackdrop(accent: appearance.accentColor, highContrast: highContrast)

            VStack(spacing: 24) {
                Spacer(minLength: 16)

                VStack(spacing: 18) {
                    ZStack {
                        Circle()
                            .fill(accent.opacity(colorScheme == .dark ? 0.25 : 0.16))
                            .frame(width: 72, height: 72)

                        Image(systemName: "lock.shield.fill")
                            .font(.system(size: 32, weight: .semibold))
                            .foregroundStyle(accent)
                    }

                    VStack(spacing: 6) {
                        Text("Vida Acadêmica UFOP")
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                        Text("Autentique-se para visualizar seus horários, notas e dados locais criptografados.")
                            .font(.system(size: 14, weight: .regular))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                            .multilineTextAlignment(.center)
                    }

                    TimelineView(.periodic(from: .now, by: 1.0)) { _ in
                        EmptyView()
                    }
                    .frame(height: 0)
                    .onChange(of: Date()) { _, _ in
                        lockManager.refreshLockoutCountdown()
                    }

                    if lockManager.lockoutRemainingSeconds > 0 {
                        HStack(spacing: 8) {
                            Image(systemName: "timer")
                                .foregroundStyle(Color(hex: 0xEF4444))
                            Text("Bloqueio temporário: aguarde \(lockManager.lockoutRemainingSeconds)s")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Color(hex: 0xEF4444))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .frame(maxWidth: .infinity)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(Color(hex: 0xEF4444).opacity(0.14))
                        )
                    } else if let errorMessage {
                        Text(errorMessage)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Color(hex: 0xEF4444))
                            .multilineTextAlignment(.center)
                    }

                    SecureField("Senha de acesso", text: $passwordInput)
                        .textContentType(.password)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .frame(minHeight: appearance.accessibility.minTouchHeight)
                        .background(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: highContrast))
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .strokeBorder(CampusTheme.cardBorder(for: colorScheme, highContrast: highContrast), lineWidth: 1)
                        )
                        .onSubmit(attemptPasswordUnlock)

                    LiquidGlassPrimaryButton(
                        tint: accent,
                        foreground: appearance.accentColor.onPrimary(for: colorScheme),
                        minHeight: appearance.accessibility.minTouchHeight
                    ) {
                        attemptPasswordUnlock()
                    } label: {
                        Label("Desbloquear", systemImage: "lock.open.fill")
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                    }

                    if lockManager.isBiometricEnabled && lockManager.biometricAvailable {
                        Button {
                            triggerBiometricUnlock()
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: lockManager.biometricLabel == "Face ID" ? "faceid" : "touchid")
                                    .font(.system(size: 16, weight: .semibold))
                                Text("Usar \(lockManager.biometricLabel)")
                                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                            }
                            .foregroundStyle(accent)
                            .padding(.horizontal, 18)
                            .padding(.vertical, 11)
                            .frame(maxWidth: .infinity, minHeight: appearance.accessibility.minTouchHeight)
                            .liquidGlassCapsule(
                                selected: false,
                                accent: accent,
                                interactive: true,
                                highContrast: highContrast
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(24)
                .liquidGlassCard(
                    cornerRadius: 26,
                    tint: accent,
                    interactive: false,
                    highContrast: highContrast
                )
                .padding(.horizontal, 20)

                Spacer(minLength: 16)
            }
        }
        .onAppear {
            if !didAttemptAutoBiometric && lockManager.isBiometricEnabled && lockManager.biometricAvailable && lockManager.lockoutRemainingSeconds == 0 {
                didAttemptAutoBiometric = true
                triggerBiometricUnlock()
            }
        }
    }

    private func attemptPasswordUnlock() {
        if let err = lockManager.verifyPassword(passwordInput) {
            errorMessage = err
        } else {
            passwordInput = ""
            errorMessage = nil
        }
    }

    private func triggerBiometricUnlock() {
        errorMessage = nil
        lockManager.authenticateWithBiometrics { success, msg in
            if !success, let msg, !msg.lowercased().contains("cancel") {
                errorMessage = msg
            }
        }
    }
}

public struct PrivacyBlurOverlayView: View {
    public init() {}

    public var body: some View {
        ZStack {
            Rectangle()
                .fill(.ultraThinMaterial)
                .ignoresSafeArea()

            VStack(spacing: 12) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 40, weight: .semibold))
                    .foregroundStyle(.secondary)
                Text("Vida Acadêmica UFOP")
                    .font(.headline)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
