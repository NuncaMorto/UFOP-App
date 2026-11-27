import SwiftUI

public struct ClassCheckInSheetView: View {
    public let detail: ClassCheckInDetail
    public let onSave: (AttendanceStatus, DepartmentColorKey) -> Void
    public let onCancel: () -> Void
    public let onOpenLocationInMap: (() -> Void)?

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedStatus: AttendanceStatus
    @State private var selectedColorKey: DepartmentColorKey

    public init(
        detail: ClassCheckInDetail,
        onSave: @escaping (AttendanceStatus, DepartmentColorKey) -> Void,
        onCancel: @escaping () -> Void,
        onOpenLocationInMap: (() -> Void)? = nil
    ) {
        self.detail = detail
        self.onSave = onSave
        self.onCancel = onCancel
        self.onOpenLocationInMap = onOpenLocationInMap
        self._selectedStatus = State(initialValue: detail.selectedStatus)
        self._selectedColorKey = State(initialValue: detail.selectedColorKey)
    }

    private var hc: Bool { appearance?.accessibility.highContrastMode ?? false }
    private var minTouch: CGFloat { appearance?.accessibility.minTouchHeight ?? 48 }
    private var accentColorObj: CampusAccentColor { appearance?.accentColor ?? .red }

    public var body: some View {
        ZStack {
            LiquidGlassBackdrop(accent: accentColorObj, highContrast: hc)

            VStack(spacing: 0) {
                topBar
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 10)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {
                        headerSection
                        metadataCard
                        colorPickerSection
                        verticalStatusPillsSection
                        footnoteBanner
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, 24)
                }

                VStack(spacing: 0) {
                    saveButton
                        .padding(.horizontal, 20)
                        .padding(.top, 12)
                        .padding(.bottom, 24)
                }
                .background(.ultraThinMaterial.opacity(0.6))
            }
        }
        .preferredColorScheme(appearance?.resolvedColorScheme)
    }

    // MARK: - Top Bar

    private var topBar: some View {
        let deptColor = CampusTheme.departmentColor(for: selectedColorKey)
        let accessibleDeptColor = CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme, accent: accentColorObj)
        let deptAcronym = CampusStore.departmentAcronym(from: detail.department)

        return HStack {
            HStack(spacing: 6) {
                Circle()
                    .fill(deptColor)
                    .frame(width: 8, height: 8)
                Text(deptAcronym)
                    .font(CampusTheme.font(size: 13, weight: .semibold))
                    .foregroundStyle(accessibleDeptColor)
                    .lineLimit(1)
            }

            Spacer()

            Button {
                onCancel()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    .frame(width: 40, height: 40)
                    .liquidGlassCapsule(selected: false, accent: accentColorObj.primary(for: colorScheme), highContrast: hc)
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
            .accessibilityLabel("Fechar")
        }
    }

    // MARK: - Header

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(detail.disciplineName)
                .font(.system(size: 27, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

            Text(detail.fullDateLabel)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
        }
    }

    // MARK: - Metadata Card

    private var metadataCard: some View {
        let deptColor = CampusTheme.departmentColor(for: selectedColorKey)
        let cleanProfessor = detail.professor.trimmingCharacters(in: .whitespacesAndNewlines)

        return VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Label(detail.timeRange, systemImage: "clock.fill")
                    .font(CampusTheme.tabularFont(size: 14, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                Spacer()
            }

            Divider()
                .overlay(CampusTheme.cardBorder(for: colorScheme, highContrast: hc))

            if !CampusStore.isEadText(detail.location), let onOpenLocationInMap {
                Button {
                    onOpenLocationInMap()
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "mappin.and.ellipse")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(accentColorObj.primary(for: colorScheme))
                            .frame(width: 20)

                        Text(detail.location.isEmpty ? "Local não informado" : detail.location)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .multilineTextAlignment(.leading)

                        Spacer(minLength: 6)

                        HStack(spacing: 4) {
                            Text("Ver no mapa")
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                            Image(systemName: "chevron.right")
                                .font(.system(size: 11, weight: .bold))
                        }
                        .foregroundStyle(accentColorObj.primary(for: colorScheme))
                    }
                    .frame(minHeight: 36)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .campusHoverLift()
                .accessibilityLabel("Ver \(detail.location) no mapa")
            } else {
                HStack(spacing: 10) {
                    Image(systemName: CampusStore.isEadText(detail.location) ? "laptopcomputer" : "mappin.and.ellipse")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(deptColor)
                        .frame(width: 20)

                    Text(detail.location.isEmpty ? "Local não informado" : detail.location)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                    Spacer()
                }
            }

            if !cleanProfessor.isEmpty {
                HStack(spacing: 10) {
                    Image(systemName: "person.fill")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(deptColor)
                        .frame(width: 20)

                    Text(cleanProfessor)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

                    Spacer()
                }
            }
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 18, tint: deptColor, highContrast: hc)
    }

    // MARK: - Department Color Swatches

    private var colorPickerSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Cor da disciplina")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

            HStack(spacing: 14) {
                ForEach(DepartmentColorKey.allCases) { key in
                    let isSelected = key == selectedColorKey
                    let swatchColor = CampusTheme.departmentColor(for: key)

                    Button {
                        withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                            selectedColorKey = key
                        }
                    } label: {
                        ZStack {
                            Circle()
                                .fill(swatchColor)
                                .frame(width: 34, height: 34)

                            if isSelected {
                                Circle()
                                    .strokeBorder(Color.white, lineWidth: 2.5)
                                    .frame(width: 28, height: 28)

                                Image(systemName: "checkmark")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundStyle(.white)
                            }
                        }
                        .padding(4)
                        .overlay(
                            Circle()
                                .strokeBorder(isSelected ? swatchColor : Color.clear, lineWidth: 2)
                        )
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(minTouch)
                    .accessibilityLabel(key.accessibilityLabel)
                }

                Spacer()
            }
        }
    }

    // MARK: - 3 Semantic Horizontal Presence Cards

    private var verticalStatusPillsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Como será sua presença hoje?")
                .font(CampusTheme.font(size: 13, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

            HStack(spacing: 8) {
                ForEach(AttendanceStatus.allCases) { status in
                    statusPillButton(for: status)
                }
            }
        }
    }

    private func statusPillButton(for status: AttendanceStatus) -> some View {
        let isSelected = selectedStatus == status

        let semanticColor: Color = {
            switch status {
            case .attending:
                return colorScheme == .dark ? Color(hex: 0x34D399) : Color(hex: 0x166534)
            case .absent:
                return colorScheme == .dark ? Color(hex: 0xFB7185) : Color(hex: 0xBE123C)
            case .cancelled:
                return colorScheme == .dark ? Color(hex: 0xFBBF24) : Color(hex: 0xB45309)
            }
        }()

        let fillColor: Color = isSelected
            ? semanticColor.opacity(colorScheme == .dark ? 0.22 : 0.14)
            : accentColorObj.surface2(for: colorScheme)

        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)

        return Button {
            withAnimation(.spring(response: 0.24, dampingFraction: 0.82)) {
                selectedStatus = status
            }
        } label: {
            VStack(spacing: 6) {
                Image(systemName: status.systemImage)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(isSelected ? semanticColor : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

                Text(status.buttonTitle)
                    .font(CampusTheme.font(size: 12, weight: isSelected ? .bold : .medium))
                    .foregroundStyle(isSelected ? semanticColor : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: max(78, minTouch + 24))
            .background(shape.fill(fillColor))
            .overlay(
                shape.strokeBorder(
                    isSelected ? semanticColor : (hc ? CampusTheme.cardBorder(for: colorScheme, highContrast: true) : .clear),
                    lineWidth: isSelected ? 1.5 : (hc ? 1.5 : 0)
                )
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }

    // MARK: - Footnote

    private var footnoteBanner: some View {
        let primary = accentColorObj.primary(for: colorScheme)

        return HStack(alignment: .top, spacing: 10) {
            Image(systemName: "info.circle.fill")
                .font(.system(size: 15))
                .foregroundStyle(primary)
                .padding(.top, 1)

            Text(detail.footnoteText)
                .font(.system(size: 13, weight: .regular))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .liquidGlassCard(cornerRadius: 16, tint: primary, highContrast: hc)
    }

    // MARK: - Save Button

    private var saveButton: some View {
        let primary = accentColorObj.primary(for: colorScheme)
        let onPrimary = accentColorObj.onPrimary(for: colorScheme)

        return LiquidGlassPrimaryButton(
            tint: primary,
            foreground: onPrimary,
            minHeight: max(52, minTouch)
        ) {
            onSave(selectedStatus, selectedColorKey)
        } label: {
            Text("Salvar")
                .font(.system(size: 17, weight: .bold, design: .rounded))
        }
    }
}

#Preview("ClassCheckInSheetView - Light") {
    let store = CampusStore()
    if let detail = store.getClassCheckInDetail(agendaItemId: "ter-17-calc-ii") {
        ClassCheckInSheetView(detail: detail, onSave: { _, _ in }, onCancel: {})
            .preferredColorScheme(.light)
    }
}

#Preview("ClassCheckInSheetView - Dark") {
    let store = CampusStore()
    if let detail = store.getClassCheckInDetail(agendaItemId: "ter-17-calc-ii") {
        ClassCheckInSheetView(detail: detail, onSave: { _, _ in }, onCancel: {})
            .preferredColorScheme(.dark)
    }
}
