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

// MARK: - 9 Curated Tonal Accent Palettes (Preserving Declaration Order & storageKeys)

public enum CampusAccentColor: String, CaseIterable, Codable, Identifiable, Sendable {
    case green = "GREEN"
    case red = "RED"
    case orange = "ORANGE"
    case purple = "PURPLE"
    case yellow = "YELLOW"
    case cyan = "CYAN"
    case brown = "BROWN"
    case pink = "PINK"
    case blue = "BLUE"

    public var id: String { rawValue }
    public var storageKey: String { rawValue }

    public var displayName: String {
        switch self {
        case .green: return "Mata"
        case .red: return "Vinho"
        case .orange: return "Terracota"
        case .purple: return "Ametista"
        case .yellow: return "Ouro Velho"
        case .cyan: return "Mineral"
        case .brown: return "Cacau"
        case .pink: return "Rubi"
        case .blue: return "Cobalto"
        }
    }

    public var previewHex: String {
        switch self {
        case .green: return "#216B4A"
        case .red: return "#8C1D35"
        case .orange: return "#B54717"
        case .purple: return "#5F3DC4"
        case .yellow: return "#9A680E"
        case .cyan: return "#126E7B"
        case .brown: return "#734A34"
        case .pink: return "#A62660"
        case .blue: return "#2557B8"
        }
    }

    public func canvas(for scheme: ColorScheme) -> Color {
        switch (self, scheme) {
        case (.green, .dark): return Color(hex: 0x101513)
        case (.green, _): return Color(hex: 0xF3F9F6)
        case (.red, .dark): return Color(hex: 0x151112)
        case (.red, _): return Color(hex: 0xFBF5F6)
        case (.orange, .dark): return Color(hex: 0x161210)
        case (.orange, _): return Color(hex: 0xFBF6F3)
        case (.purple, .dark): return Color(hex: 0x131117)
        case (.purple, _): return Color(hex: 0xF7F5FC)
        case (.yellow, .dark): return Color(hex: 0x15130F)
        case (.yellow, _): return Color(hex: 0xFAF7F0)
        case (.cyan, .dark): return Color(hex: 0x101517)
        case (.cyan, _): return Color(hex: 0xF2F9FA)
        case (.brown, .dark): return Color(hex: 0x151210)
        case (.brown, _): return Color(hex: 0xFAF6F3)
        case (.pink, .dark): return Color(hex: 0x161114)
        case (.pink, _): return Color(hex: 0xFCF4F8)
        case (.blue, .dark): return Color(hex: 0x101318)
        case (.blue, _): return Color(hex: 0xF3F7FC)
        }
    }

    public func surface1(for scheme: ColorScheme) -> Color {
        if scheme != .dark {
            return Color(hex: 0xFFFFFF)
        }
        switch self {
        case .green: return Color(hex: 0x1A2520)
        case .red: return Color(hex: 0x231A1D)
        case .orange: return Color(hex: 0x261E19)
        case .purple: return Color(hex: 0x201C29)
        case .yellow: return Color(hex: 0x242017)
        case .cyan: return Color(hex: 0x192428)
        case .brown: return Color(hex: 0x241E1B)
        case .pink: return Color(hex: 0x261B21)
        case .blue: return Color(hex: 0x1A212D)
        }
    }

    public func surface2(for scheme: ColorScheme) -> Color {
        switch (self, scheme) {
        case (.green, .dark): return Color(hex: 0x25352E)
        case (.green, _): return Color(hex: 0xE1EFE8)
        case (.red, .dark): return Color(hex: 0x312428)
        case (.red, _): return Color(hex: 0xF2E4E7)
        case (.orange, .dark): return Color(hex: 0x362A23)
        case (.orange, _): return Color(hex: 0xF4E6DD)
        case (.purple, .dark): return Color(hex: 0x2D273B)
        case (.purple, _): return Color(hex: 0xEAE4F7)
        case (.yellow, .dark): return Color(hex: 0x332D20)
        case (.yellow, _): return Color(hex: 0xF0E7D1)
        case (.cyan, .dark): return Color(hex: 0x233339)
        case (.cyan, _): return Color(hex: 0xDFEEF1)
        case (.brown, .dark): return Color(hex: 0x332B26)
        case (.brown, _): return Color(hex: 0xEFE6E0)
        case (.pink, .dark): return Color(hex: 0x36252F)
        case (.pink, _): return Color(hex: 0xF4E2EB)
        case (.blue, .dark): return Color(hex: 0x252F40)
        case (.blue, _): return Color(hex: 0xE2EBF8)
        }
    }

    public func primary(for scheme: ColorScheme) -> Color {
        switch (self, scheme) {
        case (.green, .dark): return Color(hex: 0x267A54)
        case (.green, _): return Color(hex: 0x216B4A)
        case (.red, .dark): return Color(hex: 0xA82E42)
        case (.red, _): return Color(hex: 0x8C1D35)
        case (.orange, .dark): return Color(hex: 0xBD501E)
        case (.orange, _): return Color(hex: 0xB54717)
        case (.purple, .dark): return Color(hex: 0x7554D1)
        case (.purple, _): return Color(hex: 0x5F3DC4)
        case (.yellow, .dark): return Color(hex: 0xE0AE43)
        case (.yellow, _): return Color(hex: 0x9A680E)
        case (.cyan, .dark): return Color(hex: 0x177C8A)
        case (.cyan, _): return Color(hex: 0x126E7B)
        case (.brown, .dark): return Color(hex: 0x8F6249)
        case (.brown, _): return Color(hex: 0x734A34)
        case (.pink, .dark): return Color(hex: 0xC43E78)
        case (.pink, _): return Color(hex: 0xA62660)
        case (.blue, .dark): return Color(hex: 0x356AD1)
        case (.blue, _): return Color(hex: 0x2557B8)
        }
    }

    public func onPrimary(for scheme: ColorScheme) -> Color {
        switch (self, scheme) {
        case (.yellow, .dark): return Color(hex: 0x211602)
        default: return .white
        }
    }

    public func highlight(for scheme: ColorScheme) -> Color {
        if scheme != .dark {
            return primary(for: scheme)
        }
        switch self {
        case .green: return Color(hex: 0xA6DFC3)
        case .red: return Color(hex: 0xF2B8C1)
        case .orange: return Color(hex: 0xF7C4A8)
        case .purple: return Color(hex: 0xCFC0FA)
        case .yellow: return Color(hex: 0xF2D591)
        case .cyan: return Color(hex: 0x9EE0E8)
        case .brown: return Color(hex: 0xDFC2B2)
        case .pink: return Color(hex: 0xF5B8D2)
        case .blue: return Color(hex: 0xB3CCF8)
        }
    }

    public func ink(for scheme: ColorScheme) -> Color {
        if scheme == .dark {
            return Color(hex: 0xF6F4F5)
        }
        switch self {
        case .green: return Color(hex: 0x11291F)
        case .red: return Color(hex: 0x2B1217)
        case .orange: return Color(hex: 0x2E170D)
        case .purple: return Color(hex: 0x1E1438)
        case .yellow: return Color(hex: 0x2E2107)
        case .cyan: return Color(hex: 0x0D282D)
        case .brown: return Color(hex: 0x291A12)
        case .pink: return Color(hex: 0x300F1E)
        case .blue: return Color(hex: 0x121F3B)
        }
    }

    public func softTint(for scheme: ColorScheme) -> Color {
        surface2(for: scheme)
    }

    public func textOnHighlightSurface(for scheme: ColorScheme) -> Color {
        scheme == .dark ? highlight(for: scheme) : ink(for: scheme)
    }

    public static func fromKey(_ raw: String?) -> CampusAccentColor {
        guard let raw else { return .red }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return Self.allCases.first { $0.rawValue.caseInsensitiveCompare(trimmed) == .orderedSame } ?? .red
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
        accentColor = .red
        accessibility = CampusAccessibilitySettings()
    }
}

// MARK: - Campus UFOP Design System, Typography (Onest), WCAG Helpers & Tonal Elevation

public enum CampusTheme {

    // MARK: Typography (Onest)

    public static func fontName(for weight: Font.Weight, boldBoost: Bool = false) -> String {
        switch weight {
        case .black, .heavy:
            return "Onest-ExtraBold"
        case .bold:
            return boldBoost ? "Onest-ExtraBold" : "Onest-Bold"
        case .semibold:
            return boldBoost ? "Onest-Bold" : "Onest-SemiBold"
        case .medium:
            return boldBoost ? "Onest-SemiBold" : "Onest-Medium"
        default:
            return boldBoost ? "Onest-Medium" : "Onest-Regular"
        }
    }

    public static func font(size: CGFloat, weight: Font.Weight = .regular, boldBoost: Bool = false) -> Font {
        Font.custom(fontName(for: weight, boldBoost: boldBoost), size: size)
    }

    public static func tabularFont(size: CGFloat, weight: Font.Weight = .semibold, boldBoost: Bool = false) -> Font {
        Font.custom(fontName(for: weight, boldBoost: boldBoost), size: size).monospacedDigit()
    }

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

    // MARK: Light / Dark Adaptive Tonal Tokens

    public static func appBackground(
        for scheme: ColorScheme,
        highContrast: Bool = false,
        accent: CampusAccentColor = .red
    ) -> Color {
        if highContrast {
            return scheme == .dark ? Color(hex: 0x050607) : Color(hex: 0xFFFFFF)
        }
        return accent.canvas(for: scheme)
    }

    public static func cardBackground(
        for scheme: ColorScheme,
        highContrast: Bool = false,
        accent: CampusAccentColor = .red
    ) -> Color {
        if highContrast {
            return scheme == .dark ? Color(hex: 0x050607) : Color(hex: 0xFFFFFF)
        }
        return accent.surface1(for: scheme)
    }

    public static func secondarySurface(
        for scheme: ColorScheme,
        highContrast: Bool = false,
        accent: CampusAccentColor = .red
    ) -> Color {
        if highContrast {
            return scheme == .dark ? Color(hex: 0x181A1D) : Color(hex: 0xEEEEEE)
        }
        return accent.surface2(for: scheme)
    }

    public static func cardBorder(for scheme: ColorScheme, highContrast: Bool = false) -> Color {
        if highContrast {
            return scheme == .dark ? Color.white.opacity(0.90) : Color.black.opacity(0.90)
        }
        return scheme == .dark ? Color.white.opacity(0.10) : Color.black.opacity(0.06)
    }

    public static func primaryText(
        for scheme: ColorScheme,
        highContrast: Bool = false,
        accent: CampusAccentColor = .red
    ) -> Color {
        if highContrast {
            return scheme == .dark ? .white : .black
        }
        return accent.ink(for: scheme)
    }

    public static func secondaryText(for scheme: ColorScheme, highContrast: Bool = false) -> Color {
        if highContrast {
            return scheme == .dark ? Color(hex: 0xE0E0E0) : Color(hex: 0x212121)
        }
        return scheme == .dark ? Color(hex: 0xCCC6CA) : Color(hex: 0x524B4E)
    }

    public static func mutedText(for scheme: ColorScheme, highContrast: Bool = false) -> Color {
        if highContrast {
            return scheme == .dark ? Color(hex: 0xD4D4D4) : Color(hex: 0x333333)
        }
        return scheme == .dark ? Color(hex: 0xB8B0B5) : Color(hex: 0x625A5D)
    }

    public static func selectedPillBackground(for scheme: ColorScheme, accent: CampusAccentColor = .red) -> Color {
        accent.surface2(for: scheme)
    }

    public static func selectedPillText(for scheme: ColorScheme, accent: CampusAccentColor = .red) -> Color {
        accent.highlight(for: scheme)
    }

    public static func weatherBadgeBackground(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0x3A3112) : Color(hex: 0xFFE066)
    }

    public static func weatherBadgeText(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0xFDE047) : Color(hex: 0x5C4800)
    }

    public static func floatingButtonBackground(for scheme: ColorScheme, accent: CampusAccentColor = .red) -> Color {
        accent.surface1(for: scheme)
    }

    public static func cardShadow(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color.black.opacity(0.32) : Color.black.opacity(0.05)
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

    public static func accessibleTextColor(
        _ base: Color,
        scheme: ColorScheme,
        minContrast: Double = 4.5,
        accent: CampusAccentColor = .red
    ) -> Color {
        let surface = accent.surface1(for: scheme)
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

    // MARK: Layout Metrics (Fase 1 Organic Radii)

    public static let cardCornerRadius: CGFloat = 24
    public static let groupedCornerRadius: CGFloat = 26
    public static let sheetCornerRadius: CGFloat = 30
    public static let pillCornerRadius: CGFloat = 999
    public static let leftAccentBarWidth: CGFloat = 5
}

// MARK: - Atmospheric Tonal Backdrop & Tonal Card Modifiers

/// Fundo tonal derivado exclusivamente da cor de destaque ativa (`Canvas` + brilho sutil da paleta).
public struct LiquidGlassBackdrop: View {
    public let accent: CampusAccentColor
    public let highContrast: Bool
    @Environment(\.colorScheme) private var colorScheme

    public init(accent: CampusAccentColor = .red, highContrast: Bool = false) {
        self.accent = accent
        self.highContrast = highContrast
    }

    public var body: some View {
        ZStack {
            CampusTheme.appBackground(for: colorScheme, highContrast: highContrast, accent: accent)
                .ignoresSafeArea()

            if !highContrast {
                RadialGradient(
                    colors: [
                        accent.primary(for: colorScheme).opacity(colorScheme == .dark ? 0.16 : 0.08),
                        Color.clear
                    ],
                    center: .topTrailing,
                    startRadius: 24,
                    endRadius: 360
                )
                .ignoresSafeArea()

                RadialGradient(
                    colors: [
                        accent.highlight(for: colorScheme).opacity(colorScheme == .dark ? 0.08 : 0.06),
                        Color.clear
                    ],
                    center: .bottomLeading,
                    startRadius: 24,
                    endRadius: 340
                )
                .ignoresSafeArea()
            }
        }
    }
}

/// Agrupa múltiplos controles Liquid Glass flutuantes no iOS 26.
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
    let floating: Bool
    let useSurface2: Bool
    @Environment(\.colorScheme) private var colorScheme
    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?

    private var activeAccent: CampusAccentColor {
        appearance?.accentColor ?? .red
    }

    @ViewBuilder
    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        if highContrast {
            content
                .background(
                    shape.fill(CampusTheme.cardBackground(for: colorScheme, highContrast: true, accent: activeAccent))
                )
                .overlay(
                    shape.strokeBorder(CampusTheme.cardBorder(for: colorScheme, highContrast: true), lineWidth: 2)
                )
        } else if floating {
            #if compiler(>=6.2)
            if #available(iOS 26.0, *) {
                let glassBase = tint.map { Glass.regular.tint($0.opacity(0.16)) } ?? Glass.regular
                let configured = interactive ? glassBase.interactive() : glassBase
                content
                    .background(
                        shape.fill(activeAccent.surface1(for: colorScheme).opacity(colorScheme == .dark ? 0.60 : 0.76))
                    )
                    .glassEffect(configured, in: shape)
            } else {
                tonalCard(content: content, shape: shape)
            }
            #else
            tonalCard(content: content, shape: shape)
            #endif
        } else {
            tonalCard(content: content, shape: shape)
        }
    }

    private func tonalCard(content: Content, shape: RoundedRectangle) -> some View {
        let baseFill = useSurface2 ? activeAccent.surface2(for: colorScheme) : activeAccent.surface1(for: colorScheme)
        return content
            .background(
                ZStack {
                    shape.fill(baseFill)
                    if let tint {
                        shape.fill(tint.opacity(colorScheme == .dark ? 0.08 : 0.035))
                    }
                }
            )
            .shadow(
                color: colorScheme == .dark ? Color.clear : CampusTheme.cardShadow(for: colorScheme),
                radius: floating ? 10 : 3,
                x: 0,
                y: floating ? 4 : 1
            )
    }
}

private struct LiquidGlassCapsuleModifier: ViewModifier {
    let selected: Bool
    let accent: Color
    let interactive: Bool
    let highContrast: Bool
    let floating: Bool
    @Environment(\.colorScheme) private var colorScheme
    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?

    private var activeAccent: CampusAccentColor {
        appearance?.accentColor ?? .red
    }

    @ViewBuilder
    func body(content: Content) -> some View {
        if highContrast {
            content
                .background(
                    Capsule(style: .continuous)
                        .fill(selected ? accent : CampusTheme.cardBackground(for: colorScheme, highContrast: true, accent: activeAccent))
                )
                .overlay(
                    Capsule(style: .continuous)
                        .strokeBorder(CampusTheme.cardBorder(for: colorScheme, highContrast: true), lineWidth: selected ? 2 : 1.5)
                )
        } else if floating {
            #if compiler(>=6.2)
            if #available(iOS 26.0, *) {
                let baseGlass = selected ? Glass.regular.tint(accent.opacity(0.28)) : Glass.regular
                let configured = interactive ? baseGlass.interactive() : baseGlass
                content
                    .background(
                        Capsule(style: .continuous)
                            .fill(selected ? activeAccent.surface2(for: colorScheme).opacity(0.85) : activeAccent.surface1(for: colorScheme).opacity(0.70))
                    )
                    .glassEffect(configured, in: .capsule)
            } else {
                tonalCapsule(content: content)
            }
            #else
            tonalCapsule(content: content)
            #endif
        } else {
            tonalCapsule(content: content)
        }
    }

    private func tonalCapsule(content: Content) -> some View {
        content
            .background(
                Capsule(style: .continuous)
                    .fill(selected ? accent : activeAccent.surface2(for: colorScheme))
            )
    }
}

public extension View {
    func liquidGlassCard(
        cornerRadius: CGFloat = CampusTheme.cardCornerRadius,
        tint: Color? = nil,
        interactive: Bool = false,
        highContrast: Bool = false,
        floating: Bool = false,
        useSurface2: Bool = false
    ) -> some View {
        modifier(
            LiquidGlassCardModifier(
                cornerRadius: cornerRadius,
                tint: tint,
                interactive: interactive,
                highContrast: highContrast,
                floating: floating,
                useSurface2: useSurface2
            )
        )
    }

    func liquidGlassCapsule(
        selected: Bool = false,
        accent: Color = CampusAccentColor.red.primary(for: .light),
        interactive: Bool = true,
        highContrast: Bool = false,
        floating: Bool = false
    ) -> some View {
        modifier(
            LiquidGlassCapsuleModifier(
                selected: selected,
                accent: accent,
                interactive: interactive,
                highContrast: highContrast,
                floating: floating
            )
        )
    }

    /// Garante alvo tátil mínimo de >= 48pt sem inflar desnecessariamente a altura visual da pílula.
    func minimumTouchTarget(_ minHeight: CGFloat = 48) -> some View {
        frame(minHeight: minHeight)
            .contentShape(Rectangle())
    }

    /// Limita a largura máxima de leitura em telas largas (iPadOS Regular Size Class) centralizando o conteúdo.
    func campusReadableWidth(_ maxWidth: CGFloat = 820) -> some View {
        frame(maxWidth: maxWidth)
            .frame(maxWidth: .infinity)
    }

    /// Efeito de elevação suave para trackpad/ponteiro no iPadOS.
    @ViewBuilder
    func campusHoverLift() -> some View {
        #if os(iOS)
        self.hoverEffect(.lift)
        #else
        self
        #endif
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
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
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
            .font(CampusTheme.tabularFont(size: 12, weight: .semibold))
            .foregroundStyle(CampusTheme.weatherBadgeText(for: colorScheme))
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(
                Capsule(style: .continuous)
                    .fill(CampusTheme.weatherBadgeBackground(for: colorScheme))
            )
    }
}

public struct AccentBarCardContainer<Content: View>: View {
    public let accentColor: Color
    public let highContrast: Bool
    public let useSurface2: Bool
    public let content: Content

    public init(
        accentColor: Color,
        highContrast: Bool = false,
        useSurface2: Bool = false,
        @ViewBuilder content: () -> Content
    ) {
        self.accentColor = accentColor
        self.highContrast = highContrast
        self.useSurface2 = useSurface2
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
            highContrast: highContrast,
            useSurface2: useSurface2
        )
    }
}
