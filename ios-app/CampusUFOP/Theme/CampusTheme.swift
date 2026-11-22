import SwiftUI

// MARK: - Hex Color Extension

public extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        let red = Double((hex >> 16) & 0xFF) / 255.0
        let green = Double((hex >> 8) & 0xFF) / 255.0
        let blue = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: red, green: green, blue: blue, opacity: alpha)
    }

    init(hexString: String, alpha: Double = 1.0) {
        let cleaned = hexString
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "#", with: "")
        var hexValue: UInt64 = 0
        Scanner(string: cleaned).scanHexInt64(&hexValue)
        self.init(hex: UInt32(hexValue & 0xFFFFFF), alpha: alpha)
    }
}

// MARK: - Campus UFOP Design System & Adaptive Theme Tokens

public enum CampusTheme {

    // MARK: Department Palette (PDF Spec)

    /// Azul Matemática (`#3B68FF`)
    public static let mathBlue = Color(hex: 0x3B68FF)

    /// Roxo Computação (`#6E56CF`)
    public static let compPurple = Color(hex: 0x6E56CF)

    /// Verde Álgebra Linear (`#2E7D32`)
    public static let algebraGreen = Color(hex: 0x2E7D32)

    /// Âmbar Física (`#8D5B16`)
    public static let physicsAmber = Color(hex: 0x8D5B16)

    /// Retorna a cor semântica para a chave de departamento do UniFFI.
    public static func departmentColor(for key: DepartmentColorKey) -> Color {
        switch key {
        case .mathBlue:
            return mathBlue
        case .compPurple:
            return compPurple
        case .algebraGreen:
            return algebraGreen
        case .physicsAmber:
            return physicsAmber
        }
    }

    /// Retorna um fundo suave (tint) derivado da cor do departamento.
    public static func departmentSoftTint(for key: DepartmentColorKey, scheme: ColorScheme) -> Color {
        let base = departmentColor(for: key)
        return base.opacity(scheme == .dark ? 0.22 : 0.12)
    }

    // MARK: Light / Dark Adaptive Tokens (PDF 9:41 Spec)

    /// Fundo principal da tela: Light `#F6F8FC` (off-white/gelo) | Dark `#0F1115` (carbono)
    public static func appBackground(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0x0F1115) : Color(hex: 0xF6F8FC)
    }

    /// Superfície de cards: Light `#FFFFFF` | Dark `#181B22`
    public static func cardBackground(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0x181B22) : Color(hex: 0xFFFFFF)
    }

    /// Superfície secundária sutil (ex.: pílulas não selecionadas, blocos internos, células vazias da grade)
    public static func secondarySurface(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0x1F2430) : Color(hex: 0xEEF2F9)
    }

    /// Borda sutil de cards e divisores: Light `#E3E8F2` | Dark `#262B35`
    public static func cardBorder(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0x262B35) : Color(hex: 0xE3E8F2)
    }

    /// Texto primário: Light `#0F172A` | Dark `#F8FAFC`
    public static func primaryText(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0xF8FAFC) : Color(hex: 0x0F172A)
    }

    /// Texto secundário: Light `#475569` | Dark `#94A3B8`
    public static func secondaryText(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0x94A3B8) : Color(hex: 0x475569)
    }

    /// Texto terciário / legendas: Light `#64748B` | Dark `#64748B`
    public static func mutedText(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0x64748B) : Color(hex: 0x64748B)
    }

    /// Pílula selecionada (ex.: `TER 17`): Light `#DCE7FF` | Dark `#1E2E52`
    public static func selectedPillBackground(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0x1E2E52) : Color(hex: 0xDCE7FF)
    }

    /// Texto da pílula selecionada: Light `#1D4ED8` | Dark `#93C5FD`
    public static func selectedPillText(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0x93C5FD) : Color(hex: 0x1D4ED8)
    }

    /// Badge de clima amarelo (`☀️ 22 °C`): `#FFE066` no Light e `#FACC15` suave no Dark
    public static func weatherBadgeBackground(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0x3A3112) : Color(hex: 0xFFE066)
    }

    /// Texto do badge de clima amarelo
    public static func weatherBadgeText(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0xFDE047) : Color(hex: 0x5C4800)
    }

    /// Botão flutuante escuro no canto inferior direito (`DisciplinesView`)
    public static func floatingButtonBackground(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0x2A3140) : Color(hex: 0x141821)
    }

    /// Cor de sombra de card
    public static func cardShadow(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color.black.opacity(0.35) : Color(hex: 0x0F172A, alpha: 0.05)
    }

    // MARK: Layout Metrics

    public static let cardCornerRadius: CGFloat = 18
    public static let pillCornerRadius: CGFloat = 14
    public static let leftAccentBarWidth: CGFloat = 5
}

// MARK: - Reusable Visual Components

/// Badge amarelo de clima (`☀️ 22 °C`, `☀️ 24 °C`, `☀️ 26 °C`).
public struct WeatherPillBadge: View {
    public let label: String
    @Environment(\.colorScheme) private var colorScheme

    public init(label: String) {
        self.label = label
    }

    public var body: some View {
        Text(label)
            .font(.system(size: 12, weight: .semibold, design: .rounded))
            .foregroundStyle(CampusTheme.weatherBadgeText(for: colorScheme))
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(
                Capsule(style: .continuous)
                    .fill(CampusTheme.weatherBadgeBackground(for: colorScheme))
            )
    }
}

/// Container de card com barra lateral esquerda colorida, borda adaptativa e sombra sutil.
public struct AccentBarCardContainer<Content: View>: View {
    public let accentColor: Color
    public let content: Content
    @Environment(\.colorScheme) private var colorScheme

    public init(
        accentColor: Color,
        @ViewBuilder content: () -> Content
    ) {
        self.accentColor = accentColor
        self.content = content()
    }

    public var body: some View {
        HStack(spacing: 0) {
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(accentColor)
                .frame(width: CampusTheme.leftAccentBarWidth)
                .padding(.vertical, 10)
                .padding(.leading, 10)

            content
                .padding(.horizontal, 14)
                .padding(.vertical, 14)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(
            RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                .fill(CampusTheme.cardBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
        )
        .shadow(
            color: CampusTheme.cardShadow(for: colorScheme),
            radius: 10,
            x: 0,
            y: 4
        )
    }
}
