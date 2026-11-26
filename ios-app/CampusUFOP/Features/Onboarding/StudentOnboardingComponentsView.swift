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
                        .font(CampusTheme.tabularFont(size: 13, weight: .bold))
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

internal struct OnboardingStepHeaderBar: View {
    let step: Int
    let accent: Color
    let highContrast: Bool
    let onBack: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("CONFIGURAÇÃO INICIAL · ETAPA \(step) DE 4")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .tracking(1.3)
                    .foregroundStyle(accent)

                Spacer()

                if step > 1 {
                    Button {
                        onBack()
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
}

internal struct OnboardingTextField: View {
    let title: String
    let placeholder: String
    @Binding var text: String
    let highContrast: Bool

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            TextField(placeholder, text: $text)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(RoundedRectangle(cornerRadius: 12).fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: highContrast)))
        }
    }
}

// MARK: - Shared Live Mini-Preview & 3x3 Color Sphere Grid

internal struct CampusAccentPalettePreviewGrid: View {
    @Bindable var appearance: CampusAppearanceController
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let selected = appearance.accentColor
        let hc = appearance.accessibility.highContrastMode
        let boldBoost = appearance.accessibility.boldTextBoost
        let minTouch = appearance.accessibility.minTouchHeight
        let primarySwatch = selected.primary(for: colorScheme)
        let previewHighlight = selected.highlight(for: colorScheme)

        VStack(alignment: .leading, spacing: 14) {
            // Live Mini-Preview
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Label(
                        "PRÉ-VISUALIZAÇÃO · \(selected.displayName.uppercased())",
                        systemImage: "paintpalette"
                    )
                    .font(CampusTheme.font(size: 11, weight: .bold, boldBoost: boldBoost))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

                    Spacer()

                    Text(colorScheme == .dark ? "Escuro" : "Claro")
                        .font(CampusTheme.font(size: 11, weight: .bold, boldBoost: boldBoost))
                        .foregroundStyle(selected.onPrimary(for: colorScheme))
                        .padding(.horizontal, 9)
                        .padding(.vertical, 3)
                        .background(Capsule(style: .continuous).fill(primarySwatch))
                }

                HStack(spacing: 10) {
                    Text("14:00")
                        .font(CampusTheme.tabularFont(size: 12, weight: .bold, boldBoost: boldBoost))
                        .foregroundStyle(previewHighlight)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(selected.surface2(for: colorScheme))
                        )

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Cálculo I · ICEB")
                            .font(CampusTheme.font(size: 13, weight: .bold, boldBoost: boldBoost))
                            .foregroundStyle(selected.ink(for: colorScheme))
                            .lineLimit(1)
                        Text("Em andamento · Frequência 92%")
                            .font(CampusTheme.tabularFont(size: 11, weight: .medium, boldBoost: boldBoost))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            .lineLimit(1)
                    }

                    Spacer(minLength: 4)

                    Text("AGORA")
                        .font(CampusTheme.font(size: 10, weight: .heavy, boldBoost: boldBoost))
                        .foregroundStyle(previewHighlight)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Capsule(style: .continuous).fill(selected.surface2(for: colorScheme)))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(selected.surface1(for: colorScheme))
                        .shadow(
                            color: colorScheme == .dark ? .clear : Color.black.opacity(0.05),
                            radius: 4,
                            x: 0,
                            y: 2
                        )
                )
            }
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(selected.canvas(for: colorScheme))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(
                        hc ? CampusTheme.cardBorder(for: colorScheme, highContrast: true) : primarySwatch.opacity(0.22),
                        lineWidth: hc ? 1.5 : 1
                    )
            )

            // 3x3 Color Sphere Grid
            let columns = Array(repeating: GridItem(.flexible(), spacing: 10), count: 3)
            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(CampusAccentColor.allCases) { option in
                    let isSelected = selected == option
                    let swatch = option.primary(for: colorScheme)
                    let ringColor = colorScheme == .dark ? option.highlight(for: colorScheme) : swatch

                    Button {
                        withAnimation(.snappy) {
                            appearance.accentColor = option
                        }
                    } label: {
                        VStack(spacing: 6) {
                            ZStack {
                                Circle()
                                    .strokeBorder(isSelected ? ringColor : .clear, lineWidth: 2)
                                    .frame(width: 48, height: 48)
                                Circle()
                                    .fill(swatch)
                                    .frame(width: 38, height: 38)
                                    .overlay(
                                        Image(systemName: isSelected ? "checkmark" : "")
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundStyle(option.onPrimary(for: colorScheme))
                                    )
                            }
                            Text(option.displayName)
                                .font(CampusTheme.font(size: 11.5, weight: isSelected ? .bold : .medium, boldBoost: boldBoost))
                                .foregroundStyle(
                                    isSelected
                                        ? CampusTheme.primaryText(for: colorScheme, highContrast: hc)
                                        : CampusTheme.secondaryText(for: colorScheme, highContrast: hc)
                                )
                                .lineLimit(1)
                                .minimumScaleFactor(0.85)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(minTouch)
                    .accessibilityLabel("\(option.displayName)\(isSelected ? ", selecionada" : "")")
                }
            }
        }
    }
}
