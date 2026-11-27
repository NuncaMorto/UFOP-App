import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Friend Avatar Helper & Incoming .ufopgrade Confirmation Sheet

internal struct FriendAvatarCircleView: View {
    let name: String
    let avatarBase64: String?
    let size: CGFloat
    let accent: Color
    let onAccent: Color

    private var initials: String {
        let parts = name
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .split(separator: " ")
            .prefix(2)
            .compactMap(\.first)
            .map { String($0).uppercased() }
            .joined()
        return parts.isEmpty ? "UF" : parts
    }

    #if canImport(UIKit)
    private var decodedImage: UIImage? {
        guard let raw = avatarBase64?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty else {
            return nil
        }
        let cleanBase64: String
        if let range = raw.range(of: "base64,") {
            cleanBase64 = String(raw[range.upperBound...])
        } else {
            cleanBase64 = raw
        }
        guard let data = Data(base64Encoded: cleanBase64, options: .ignoreUnknownCharacters) else {
            return nil
        }
        return UIImage(data: data)
    }
    #endif

    var body: some View {
        ZStack {
            #if canImport(UIKit)
            if let uiImage = decodedImage {
                Image(uiImage: uiImage)
                    .resizable()
                    .scaledToFill()
                    .frame(width: size, height: size)
                    .clipShape(Circle())
            } else {
                fallbackCircle
            }
            #else
            fallbackCircle
            #endif
        }
        .frame(width: size, height: size)
        .overlay(
            Circle()
                .strokeBorder(accent.opacity(0.35), lineWidth: 1.5)
        )
        .accessibilityHidden(true)
    }

    private var fallbackCircle: some View {
        Circle()
            .fill(accent)
            .frame(width: size, height: size)
            .overlay {
                Text(initials)
                    .font(CampusTheme.font(size: max(12, size * 0.36), weight: .bold))
                    .foregroundStyle(onAccent)
            }
    }
}

public struct IncomingGradeConfirmSheetView: View {
    public let preview: IncomingGradePreview
    public let confirmButtonTitle: String
    public let onConfirm: () -> Void
    public let onCancel: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    public init(
        preview: IncomingGradePreview,
        confirmButtonTitle: String = "Importar grade de amigo",
        onConfirm: @escaping () -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.preview = preview
        self.confirmButtonTitle = confirmButtonTitle
        self.onConfirm = onConfirm
        self.onCancel = onCancel
    }

    public var body: some View {
        let accent = appearance?.accentColor ?? .green
        let primary = accent.primary(for: colorScheme)
        let onPrimary = accent.onPrimary(for: colorScheme)
        let hc = appearance?.accessibility.highContrastMode ?? false
        let minTouch = max(48, appearance?.accessibility.minTouchHeight ?? 48)

        ZStack {
            LiquidGlassBackdrop(accent: accent, highContrast: hc)

            VStack(spacing: 0) {
                HStack(alignment: .center, spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("GRADE COMPARTILHADA")
                            .font(CampusTheme.font(size: 11, weight: .bold))
                            .tracking(0.9)
                            .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

                        Text("Confirmar Importação")
                            .font(CampusTheme.font(size: 22, weight: .bold))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    }

                    Spacer()

                    Button {
                        onCancel()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            .frame(width: 38, height: 38)
                            .liquidGlassCapsule(selected: false, accent: primary, highContrast: hc)
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(minTouch)
                    .accessibilityLabel("Fechar confirmação de importação")
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 12)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 16) {
                        friendIdentityCard(primary: primary, onPrimary: onPrimary, hc: hc)
                        metricsRow(primary: primary, hc: hc)
                        infoNoticeCard(primary: primary, hc: hc)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 4)
                    .padding(.bottom, 16)
                }

                VStack(spacing: 10) {
                    LiquidGlassPrimaryButton(
                        tint: primary,
                        foreground: onPrimary,
                        minHeight: minTouch
                    ) {
                        onConfirm()
                    } label: {
                        Label(confirmButtonTitle, systemImage: "person.badge.plus")
                            .font(CampusTheme.font(size: 15, weight: .bold))
                    }

                    Button {
                        onCancel()
                    } label: {
                        Text("Cancelar")
                            .font(CampusTheme.font(size: 14, weight: .bold))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .frame(maxWidth: .infinity, minHeight: minTouch)
                            .liquidGlassCapsule(selected: false, accent: primary, interactive: true, highContrast: hc)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 22)
                .padding(.top, 6)
            }
        }
        .preferredColorScheme(appearance?.resolvedColorScheme)
    }

    private func friendIdentityCard(primary: Color, onPrimary: Color, hc: Bool) -> some View {
        HStack(spacing: 14) {
            FriendAvatarCircleView(
                name: preview.friendName,
                avatarBase64: preview.avatarBase64,
                size: 62,
                accent: primary,
                onAccent: onPrimary
            )

            VStack(alignment: .leading, spacing: 5) {
                Text(preview.friendName)
                    .font(CampusTheme.font(size: 19, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    .lineLimit(2)

                if !preview.courseName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text(preview.courseName)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(CampusTheme.accessibleTextColor(primary, scheme: colorScheme))
                        .lineLimit(2)
                }

                HStack(spacing: 8) {
                    Text("Semestre \(preview.semester)")
                        .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(
                            Capsule(style: .continuous)
                                .fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: hc))
                        )

                    Text(preview.sourceLabel)
                        .font(CampusTheme.font(size: 11, weight: .semibold))
                        .foregroundStyle(CampusTheme.accessibleTextColor(primary, scheme: colorScheme))
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 0)
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 22, tint: primary, highContrast: hc)
    }

    private func metricsRow(primary: Color, hc: Bool) -> some View {
        HStack(spacing: 12) {
            statTile(
                value: "\(preview.courseCount)",
                label: preview.courseCount == 1 ? "Disciplina" : "Disciplinas",
                icon: "book.closed.fill",
                tint: CampusTheme.compPurple,
                hc: hc
            )
            statTile(
                value: "\(preview.slotCount)",
                label: preview.slotCount == 1 ? "Horário" : "Horários",
                icon: "clock.fill",
                tint: primary,
                hc: hc
            )
        }
    }

    private func statTile(value: String, label: String, icon: String, tint: Color, hc: Bool) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(CampusTheme.accessibleTextColor(tint, scheme: colorScheme))
                Text(label)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
            }

            Text(value)
                .font(CampusTheme.tabularFont(size: 26, weight: .bold))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .liquidGlassCard(cornerRadius: CampusTheme.cardCornerRadius, tint: tint, highContrast: hc)
    }

    private func infoNoticeCard(primary: Color, hc: Bool) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "checkmark.shield.fill")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(CampusTheme.accessibleTextColor(primary, scheme: colorScheme))
                .padding(.top, 1)

            Text("A grade deste amigo ficará salva de forma independente no seu aparelho, sem alterar suas próprias disciplinas, faltas ou agenda.")
                .font(.system(size: 13))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .liquidGlassCard(cornerRadius: 16, tint: primary, highContrast: hc)
    }
}
