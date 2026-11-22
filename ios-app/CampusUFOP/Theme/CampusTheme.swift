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

    var srgbComponents: (r: Double, g: Double, b: Double) {
        #if canImport(UIKit)
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        UIColor(self).getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return (Double(red), Double(green), Double(blue))
        #else
        return (0.2, 0.4, 0.2)
        #endif
    }
}

// MARK: - 7 Accent Palettes (v1.8.0+ Parity: Verde, Vermelho, Laranja, Roxo, Amarelo, Ciano, Marrom)

public enum CampusAccentColor: String, CaseIterable, Codable, Identifiable, Sendable {
    case green = "GREEN"
    case red = "RED"
    case orange = "ORANGE"
    case purple = "PURPLE"
    case yellow = "YELLOW"
    case cyan = "CYAN"
    case brown = "BROWN"

    public var id: String { rawValue }
    public var storageKey: String { rawValue }

    public var displayName: String {
        switch self {
        case .green: return "Verde"
        case .red: return "Vermelho"
        case .orange: return "Laranja"
        case .purple: return "Roxo"
        case .yellow: return "Amarelo"
        case .cyan: return "Ciano"
        case .brown: return "Marrom"
        }
    }

    public func primary(for scheme: ColorScheme) -> Color {
        switch (self, scheme) {
        case (.green, .dark): return Color(hex: 0x4E854B)
        case (.green, _): return Color(hex: 0x3B6939)
        case (.red, .dark): return Color(hex: 0xCF443C)
        case (.red, _): return Color(hex: 0xB3261E)
        case (.orange, .dark): return Color(hex: 0xD06A14)
        case (.orange, _): return Color(hex: 0xC75B00)
        case (.purple, .dark): return Color(hex: 0x9368C8)
        case (.purple, _): return Color(hex: 0x6B3FA0)
        case (.yellow, .dark): return Color(hex: 0xFFD54F)
        case (.yellow, _): return Color(hex: 0xB58500)
        case (.cyan, .dark): return Color(hex: 0x4DD0E1)
        case (.cyan, _): return Color(hex: 0x006874)
        case (.brown, .dark): return Color(hex: 0xD7CCC8)
        case (.brown, _): return Color(hex: 0x6D4C41)
        }
    }

    public func onPrimary(for scheme: ColorScheme) -> Color {
        switch (self, scheme) {
        case (.yellow, _): return Color(hex: 0x1A1500)
        case (.cyan, .dark): return Color(hex: 0x002022)
        case (.brown, .dark): return Color(hex: 0x271B17)
        default: return .white
        }
    }

    public func softTint(for scheme: ColorScheme) -> Color {
        primary(for: scheme).opacity(scheme == .dark ? 0.24 : 0.14)
    }

    public static func fromKey(_ raw: String?) -> CampusAccentColor {
        guard let raw else { return .green }
        return Self.allCases.first { $0.rawValue.caseInsensitiveCompare(raw.trimmingCharacters(in: .whitespacesAndNewlines)) == .orderedSame } ?? .green
    }
}

// MARK: - Global Accessibility Settings (v1.8.0+)

public struct CampusAccessibilitySettings: Equatable, Codable, Sendable {
    public var screenReaderOptimized: Bool
    public var fontScaleMultiplier: Double
    public var highContrastMode: Bool
    public var reduceMotion: Bool
    public var largeTouchTargets: Bool
    public var boldTextBoost: Bool

    public init(
        screenReaderOptimized: Bool = false,
        fontScaleMultiplier: Double = 1.0,
        highContrastMode: Bool = false,
        reduceMotion: Bool = false,
        largeTouchTargets: Bool = false,
        boldTextBoost: Bool = false
    ) {
        self.screenReaderOptimized = screenReaderOptimized
        self.fontScaleMultiplier = fontScaleMultiplier
        self.highContrastMode = highContrastMode
        self.reduceMotion = reduceMotion
        self.largeTouchTargets = largeTouchTargets
        self.boldTextBoost = boldTextBoost
    }

    public var dynamicTypeOverride: DynamicTypeSize? {
        if fontScaleMultiplier >= 1.40 { return .accessibility2 }
        if fontScaleMultiplier >= 1.28 { return .accessibility1 }
        if fontScaleMultiplier >= 1.18 { return .xxxLarge }
        if fontScaleMultiplier >= 1.08 { return .xxLarge }
        return nil
    }

    public var minTouchHeight: CGFloat {
        largeTouchTargets ? 56 : 48
    }

    public var compactControlHeight: CGFloat {
        largeTouchTargets ? 48 : 36
    }
}

@Observable
public final class CampusAppearanceController {
    private static let keyAppearanceMode = "campus.ufop.appearanceMode"
    private static let keyAccentColor = "campus.ufop.accentColor"
    private static let keyAccessibility = "campus.ufop.accessibilitySettings"

    public var appearanceModeRaw: String {
        didSet {
            UserDefaults.standard.set(appearanceModeRaw, forKey: Self.keyAppearanceMode)
        }
    }

    public var accentColor: CampusAccentColor {
        didSet {
            UserDefaults.standard.set(accentColor.storageKey, forKey: Self.keyAccentColor)
        }
    }

    public var accessibility: CampusAccessibilitySettings {
        didSet {
            if let encoded = try? JSONEncoder().encode(accessibility) {
                UserDefaults.standard.set(encoded, forKey: Self.keyAccessibility)
            }
        }
    }

    public init() {
        let defaults = UserDefaults.standard
        self.appearanceModeRaw = defaults.string(forKey: Self.keyAppearanceMode) ?? "system"
        self.accentColor = CampusAccentColor.fromKey(defaults.string(forKey: Self.keyAccentColor))
        if let data = defaults.data(forKey: Self.keyAccessibility),
           let decoded = try? JSONDecoder().decode(CampusAccessibilitySettings.self, from: data) {
            self.accessibility = decoded
        } else {
            self.accessibility = CampusAccessibilitySettings()
        }
    }

    public var resolvedColorScheme: ColorScheme? {
        switch appearanceModeRaw {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }

    public func applyVoiceOverQuickPreset() {
        accessibility = CampusAccessibilitySettings(
            screenReaderOptimized: true,
            fontScaleMultiplier: max(accessibility.fontScaleMultiplier, 1.15),
            highContrastMode: true,
            reduceMotion: true,
            largeTouchTargets: true,
            boldTextBoost: true
        )
    }

    public func resetDefaults() {
        appearanceModeRaw = "system"
        accentColor = .green
        accessibility = CampusAccessibilitySettings()
    }
}

// MARK: - Campus UFOP Design System, WCAG Helpers & Apple Liquid Glass Tokens

public enum CampusTheme {

    // MARK: Department Palette

    public static let mathBlue = Color(hex: 0x3B68FF)
    public static let compPurple = Color(hex: 0x6E56CF)
    public static let algebraGreen = Color(hex: 0x2E7D32)
    public static let physicsAmber = Color(hex: 0x8D5B16)

    public static func departmentColor(for key: DepartmentColorKey) -> Color {
        switch key {
        case .mathBlue: return mathBlue
        case .compPurple: return compPurple
        case .algebraGreen: return algebraGreen
        case .physicsAmber: return physicsAmber
        }
    }

    public static func departmentSoftTint(for key: DepartmentColorKey, scheme: ColorScheme) -> Color {
        let base = departmentColor(for: key)
        return base.opacity(scheme == .dark ? 0.24 : 0.14)
    }

    // MARK: Light / Dark Adaptive Tokens

    public static func appBackground(for scheme: ColorScheme, highContrast: Bool = false) -> Color {
        if highContrast {
            return scheme == .dark ? Color(hex: 0x050607) : Color(hex: 0xFFFFFF)
        }
        return scheme == .dark ? Color(hex: 0x0C0F14) : Color(hex: 0xF4F7FB)
    }

    public static func cardBackground(for scheme: ColorScheme, highContrast: Bool = false) -> Color {
        if highContrast {
            return scheme == .dark ? Color(hex: 0x050607) : Color(hex: 0xFFFFFF)
        }
        return scheme == .dark ? Color(hex: 0x161B23) : Color(hex: 0xFFFFFF)
    }

    public static func secondarySurface(for scheme: ColorScheme, highContrast: Bool = false) -> Color {
        if highContrast {
            return scheme == .dark ? Color(hex: 0x181A1D) : Color(hex: 0xEEEEEE)
        }
        return scheme == .dark ? Color(hex: 0x1E2532) : Color(hex: 0xEAF0F7)
    }

    public static func cardBorder(for scheme: ColorScheme, highContrast: Bool = false) -> Color {
        if highContrast {
            return scheme == .dark ? Color.white.opacity(0.85) : Color.black.opacity(0.85)
        }
        return scheme == .dark ? Color.white.opacity(0.14) : Color(hex: 0xD9E2EF)
    }

    public static func primaryText(for scheme: ColorScheme, highContrast: Bool = false) -> Color {
        if highContrast {
            return scheme == .dark ? .white : .black
        }
        return scheme == .dark ? Color(hex: 0xF8FAFC) : Color(hex: 0x0F172A)
    }

    public static func secondaryText(for scheme: ColorScheme, highContrast: Bool = false) -> Color {
        if highContrast {
            return scheme == .dark ? Color(hex: 0xE0E0E0) : Color(hex: 0x212121)
        }
        return scheme == .dark ? Color(hex: 0xB8C2D1) : Color(hex: 0x475569)
    }

    public static func mutedText(for scheme: ColorScheme, highContrast: Bool = false) -> Color {
        if highContrast {
            return scheme == .dark ? Color(hex: 0xD4D4D4) : Color(hex: 0x333333)
        }
        return scheme == .dark ? Color(hex: 0x8F9BB0) : Color(hex: 0x64748B)
    }

    public static func selectedPillBackground(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0x1E2E52) : Color(hex: 0xDCE7FF)
    }

    public static func selectedPillText(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0x93C5FD) : Color(hex: 0x1D4ED8)
    }

    public static func weatherBadgeBackground(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0x3A3112) : Color(hex: 0xFFE066)
    }

    public static func weatherBadgeText(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0xFDE047) : Color(hex: 0x5C4800)
    }

    public static func floatingButtonBackground(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0x2A3140) : Color(hex: 0x141821)
    }

    public static func cardShadow(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color.black.opacity(0.38) : Color(hex: 0x0F172A, alpha: 0.06)
    }

    // MARK: WCAG Contrast & Absence Semantics (IHC Sprint 13+)

    public static func relativeLuminance(_ color: Color) -> Double {
        let c = color.srgbComponents
        func channel(_ v: Double) -> Double {
            v <= 0.04045 ? (v / 12.92) : pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b)
    }

    public static func contrastRatio(_ fg: Color, against bg: Color) -> Double {
        let l1 = relativeLuminance(fg)
        let l2 = relativeLuminance(bg)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    public static func accessibleTextColor(_ base: Color, scheme: ColorScheme, minContrast: Double = 4.5) -> Color {
        let surface = cardBackground(for: scheme)
        if contrastRatio(base, against: surface) >= minContrast {
            return base
        }
        let target: (r: Double, g: Double, b: Double) = scheme == .dark ? (1, 1, 1) : (0.07, 0.08, 0.09)
        let c = base.srgbComponents
        for step in 1...100 {
            let t = Double(step) / 100.0
            let candidate = Color(
                .sRGB,
                red: c.r + (target.r - c.r) * t,
                green: c.g + (target.g - c.g) * t,
                blue: c.b + (target.b - c.b) * t,
                opacity: 1.0
            )
            if contrastRatio(candidate, against: surface) >= minContrast {
                return candidate
            }
        }
        return scheme == .dark ? .white : Color(hex: 0x111315)
    }

    public static func semanticAbsenceRiskLevel(currentAbsences: Int, maxAbsences: Int) -> Int {
        guard maxAbsences > 0, currentAbsences > 0 else { return 0 }
        let ratio = Double(currentAbsences) / Double(maxAbsences)
        if ratio < 0.50 { return 0 }
        if ratio < 0.75 { return 1 }
        return 2
    }

    public static func semanticAbsenceRiskLabel(currentAbsences: Int, maxAbsences: Int) -> String {
        switch semanticAbsenceRiskLevel(currentAbsences: currentAbsences, maxAbsences: maxAbsences) {
        case 2: return "Risco"
        case 1: return "Atenção"
        default: return "Controlada"
        }
    }

    public static func semanticAbsenceRiskIcon(currentAbsences: Int, maxAbsences: Int) -> String {
        switch semanticAbsenceRiskLevel(currentAbsences: currentAbsences, maxAbsences: maxAbsences) {
        case 2: return "exclamationmark.octagon.fill"
        case 1: return "exclamationmark.triangle.fill"
        default: return "checkmark.circle.fill"
        }
    }

    public static func semanticAbsenceRiskColor(currentAbsences: Int, maxAbsences: Int, scheme: ColorScheme) -> Color {
        switch semanticAbsenceRiskLevel(currentAbsences: currentAbsences, maxAbsences: maxAbsences) {
        case 2: return scheme == .dark ? Color(hex: 0xEF9A9A) : Color(hex: 0xC62828)
        case 1: return scheme == .dark ? Color(hex: 0xFFB74D) : Color(hex: 0xEF6C00)
        default: return scheme == .dark ? Color(hex: 0x81C784) : Color(hex: 0x2E7D32)
        }
    }

    // MARK: Layout Metrics

    public static let cardCornerRadius: CGFloat = 20
    public static let pillCornerRadius: CGFloat = 14
    public static let leftAccentBarWidth: CGFloat = 5
}

// MARK: - Apple Liquid Glass Containers & Modifiers

/// Fundo atmosférico com reflexos suaves da cor de destaque para valorizar o Liquid Glass do iOS 26.
public struct LiquidGlassBackdrop: View {
    public let accent: CampusAccentColor
    public let highContrast: Bool
    @Environment(\.colorScheme) private var colorScheme

    public init(accent: CampusAccentColor = .green, highContrast: Bool = false) {
        self.accent = accent
        self.highContrast = highContrast
    }

    public var body: some View {
        ZStack {
            CampusTheme.appBackground(for: colorScheme, highContrast: highContrast)
                .ignoresSafeArea()

            if !highContrast {
                RadialGradient(
                    colors: [
                        accent.primary(for: colorScheme).opacity(colorScheme == .dark ? 0.22 : 0.14),
                        Color.clear
                    ],
                    center: .topTrailing,
                    startRadius: 20,
                    endRadius: 360
                )
                .ignoresSafeArea()

                RadialGradient(
                    colors: [
                        CampusTheme.mathBlue.opacity(colorScheme == .dark ? 0.14 : 0.08),
                        Color.clear
                    ],
                    center: .bottomLeading,
                    startRadius: 20,
                    endRadius: 380
                )
                .ignoresSafeArea()
            }
        }
    }
}

/// Agrupa múltiplos controles Liquid Glass no iOS 26 para mesclagem/morphing fluido.
public struct LiquidGlassGroup<Content: View>: View {
    public let spacing: CGFloat?
    public let content: Content

    public init(spacing: CGFloat? = nil, @ViewBuilder content: () -> Content) {
        self.spacing = spacing
        self.content = content()
    }

    public var body: some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            GlassEffectContainer(spacing: spacing) {
                content
            }
        } else {
            content
        }
        #else
        content
        #endif
    }
}

private struct LiquidGlassCardModifier: ViewModifier {
    let cornerRadius: CGFloat
    let tint: Color?
    let interactive: Bool
    let highContrast: Bool
    @Environment(\.colorScheme) private var colorScheme

    @ViewBuilder
    func body(content: Content) -> some View {
        if highContrast {
            content
                .background(
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .fill(CampusTheme.cardBackground(for: colorScheme, highContrast: true))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .strokeBorder(CampusTheme.cardBorder(for: colorScheme, highContrast: true), lineWidth: 2)
                )
        } else {
            #if compiler(>=6.2)
            if #available(iOS 26.0, *) {
                let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                let tinted = tint.map { Glass.regular.tint($0.opacity(0.18)) } ?? Glass.regular
                let configured = interactive ? tinted.interactive() : tinted
                content
                    .background(
                        shape
                            .fill(CampusTheme.cardBackground(for: colorScheme).opacity(colorScheme == .dark ? 0.42 : 0.56))
                    )
                    .glassEffect(configured, in: shape)
            } else {
                fallbackGlassCard(content: content)
            }
            #else
            fallbackGlassCard(content: content)
            #endif
        }
    }

    private func fallbackGlassCard(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        return content
            .background(
                ZStack {
                    shape.fill(.ultraThinMaterial)
                    if let tint {
                        shape.fill(tint.opacity(colorScheme == .dark ? 0.12 : 0.07))
                    } else {
                        shape.fill(CampusTheme.cardBackground(for: colorScheme).opacity(colorScheme == .dark ? 0.55 : 0.65))
                    }
                }
            )
            .overlay(
                shape.strokeBorder(
                    LinearGradient(
                        colors: [
                            Color.white.opacity(colorScheme == .dark ? 0.24 : 0.72),
                            CampusTheme.cardBorder(for: colorScheme)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 1
                )
            )
            .shadow(color: CampusTheme.cardShadow(for: colorScheme), radius: 10, x: 0, y: 4)
    }
}

private struct LiquidGlassCapsuleModifier: ViewModifier {
    let selected: Bool
    let accent: Color
    let interactive: Bool
    let highContrast: Bool
    @Environment(\.colorScheme) private var colorScheme

    @ViewBuilder
    func body(content: Content) -> some View {
        if highContrast {
            content
                .background(
                    Capsule(style: .continuous)
                        .fill(selected ? accent : CampusTheme.cardBackground(for: colorScheme, highContrast: true))
                )
                .overlay(
                    Capsule(style: .continuous)
                        .strokeBorder(CampusTheme.cardBorder(for: colorScheme, highContrast: true), lineWidth: selected ? 2 : 1.5)
                )
        } else {
            #if compiler(>=6.2)
            if #available(iOS 26.0, *) {
                let baseGlass = selected ? Glass.regular.tint(accent.opacity(0.32)) : Glass.regular
                let configured = interactive ? baseGlass.interactive() : baseGlass
                content
                    .background(
                        Capsule(style: .continuous)
                            .fill(selected ? accent.opacity(colorScheme == .dark ? 0.28 : 0.18) : Color.clear)
                    )
                    .glassEffect(configured, in: .capsule)
            } else {
                fallbackCapsule(content: content)
            }
            #else
            fallbackCapsule(content: content)
            #endif
        }
    }

    private func fallbackCapsule(content: Content) -> some View {
        content
            .background(
                ZStack {
                    Capsule(style: .continuous).fill(.ultraThinMaterial)
                    Capsule(style: .continuous).fill(
                        selected
                            ? accent.opacity(colorScheme == .dark ? 0.30 : 0.18)
                            : CampusTheme.cardBackground(for: colorScheme).opacity(colorScheme == .dark ? 0.45 : 0.55)
                    )
                }
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(
                        selected ? accent.opacity(0.45) : CampusTheme.cardBorder(for: colorScheme),
                        lineWidth: 1
                    )
            )
    }
}

public extension View {
    func liquidGlassCard(
        cornerRadius: CGFloat = CampusTheme.cardCornerRadius,
        tint: Color? = nil,
        interactive: Bool = false,
        highContrast: Bool = false
    ) -> some View {
        modifier(
            LiquidGlassCardModifier(
                cornerRadius: cornerRadius,
                tint: tint,
                interactive: interactive,
                highContrast: highContrast
            )
        )
    }

    func liquidGlassCapsule(
        selected: Bool = false,
        accent: Color = CampusTheme.mathBlue,
        interactive: Bool = true,
        highContrast: Bool = false
    ) -> some View {
        modifier(
            LiquidGlassCapsuleModifier(
                selected: selected,
                accent: accent,
                interactive: interactive,
                highContrast: highContrast
            )
        )
    }

    /// Garante alvo tátil mínimo de >= 48pt sem inflar desnecessariamente a altura visual da pílula.
    func minimumTouchTarget(_ minHeight: CGFloat = 48) -> some View {
        frame(minHeight: minHeight)
            .contentShape(Rectangle())
    }
}

public struct LiquidGlassPrimaryButton<Label: View>: View {
    public let tint: Color
    public let foreground: Color
    public let minHeight: CGFloat
    public let action: () -> Void
    public let label: Label

    public init(
        tint: Color,
        foreground: Color = .white,
        minHeight: CGFloat = 48,
        action: @escaping () -> Void,
        @ViewBuilder label: () -> Label
    ) {
        self.tint = tint
        self.foreground = foreground
        self.minHeight = minHeight
        self.action = action
        self.label = label()
    }

    public var body: some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            Button(action: action) {
                label
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: max(38, minHeight - 8))
            }
            .buttonStyle(.glassProminent)
            .tint(tint)
            .foregroundStyle(foreground)
            .frame(minHeight: minHeight)
        } else {
            fallbackButton
        }
        #else
        fallbackButton
        #endif
    }

    private var fallbackButton: some View {
        Button(action: action) {
            label
                .foregroundStyle(foreground)
                .frame(maxWidth: .infinity)
                .frame(minHeight: minHeight)
                .background(
                    RoundedRectangle(cornerRadius: 15, style: .continuous)
                        .fill(tint)
                )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Reusable Visual Components

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
            .liquidGlassCapsule(
                selected: true,
                accent: Color(hex: 0xFACC15),
                interactive: false,
                highContrast: false
            )
    }
}

public struct AccentBarCardContainer<Content: View>: View {
    public let accentColor: Color
    public let highContrast: Bool
    public let content: Content

    public init(
        accentColor: Color,
        highContrast: Bool = false,
        @ViewBuilder content: () -> Content
    ) {
        self.accentColor = accentColor
        self.highContrast = highContrast
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
        .liquidGlassCard(
            cornerRadius: CampusTheme.cardCornerRadius,
            tint: accentColor,
            interactive: false,
            highContrast: highContrast
        )
    }
}
