import SwiftUI

public struct ClassCheckInSheetView: View {
    public let detail: ClassCheckInDetail
    public let onSave: (AttendanceStatus, DepartmentColorKey) -> Void
    public let onCancel: () -> Void

    @State private var selectedStatus: AttendanceStatus
    @State private var selectedColorKey: DepartmentColorKey
    @Environment(\.colorScheme) private var colorScheme

    public init(
        detail: ClassCheckInDetail,
        onSave: @escaping (AttendanceStatus, DepartmentColorKey) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.detail = detail
        self.onSave = onSave
        self.onCancel = onCancel
        self._selectedStatus = State(initialValue: detail.selectedStatus)
        self._selectedColorKey = State(initialValue: detail.selectedColorKey)
    }

    public var body: some View {
        ZStack {
            CampusTheme.cardBackground(for: colorScheme)
                .ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 20) {
                    sheetTopBar

                    headerSection

                    metadataCard

                    colorPickerSection

                    verticalStatusPillsSection

                    footnoteBanner

                    saveButton
                }
                .padding(.horizontal, 22)
                .padding(.top, 18)
                .padding(.bottom, 28)
            }
        }
    }

    // MARK: - Top Bar

    private var sheetTopBar: some View {
        HStack {
            Text(detail.department)
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.departmentColor(for: selectedColorKey))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    Capsule(style: .continuous)
                        .fill(CampusTheme.departmentSoftTint(for: selectedColorKey, scheme: colorScheme))
                )

            Spacer()

            Button {
                onCancel()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                    .frame(width: 32, height: 32)
                    .background(
                        Circle()
                            .fill(CampusTheme.secondarySurface(for: colorScheme))
                    )
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Fechar")
        }
    }

    // MARK: - Header ("Cálculo II", "Terça-feira, 17 de novembro")

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(detail.disciplineName)
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

            Text(detail.fullDateLabel)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
        }
    }

    // MARK: - Metadata Card ("08:00–09:40" + "22 °C · Ensolarado", "Prédio A · Sala 204", "Prof.ª Ana Martins")

    private var metadataCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Label(detail.timeRange, systemImage: "clock.fill")
                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Spacer()

                HStack(spacing: 5) {
                    Text("☀️")
                        .font(.system(size: 13))
                    Text(detail.weatherLabel)
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundStyle(CampusTheme.weatherBadgeText(for: colorScheme))
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(
                    Capsule(style: .continuous)
                        .fill(CampusTheme.weatherBadgeBackground(for: colorScheme))
                )
            }

            Divider()
                .overlay(CampusTheme.cardBorder(for: colorScheme))

            HStack(spacing: 10) {
                Image(systemName: "building.2.fill")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(CampusTheme.departmentColor(for: selectedColorKey))
                    .frame(width: 20)

                Text(detail.location)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Spacer()
            }

            HStack(spacing: 10) {
                Image(systemName: "person.fill")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(CampusTheme.departmentColor(for: selectedColorKey))
                    .frame(width: 20)

                Text(detail.professor)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

                Spacer()
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(CampusTheme.appBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
        )
    }

    // MARK: - 4 Department Color Swatches

    private var colorPickerSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Cor da disciplina")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

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
                        .padding(3)
                        .overlay(
                            Circle()
                                .strokeBorder(
                                    isSelected ? swatchColor : Color.clear,
                                    lineWidth: 2
                                )
                        )
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(key.accessibilityLabel)
                }

                Spacer()
            }
        }
    }

    // MARK: - 3 Vertically Stacked Wide Pill Buttons ("Vou à aula", "Vou faltar", "Aula cancelada")

    private var verticalStatusPillsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Como será sua presença hoje?")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

            VStack(spacing: 10) {
                ForEach(AttendanceStatus.allCases) { status in
                    statusPillButton(for: status)
                }
            }
        }
    }

    private func statusPillButton(for status: AttendanceStatus) -> some View {
        let isSelected = selectedStatus == status

        let fillColor: Color = {
            guard isSelected else {
                return CampusTheme.secondarySurface(for: colorScheme)
            }
            switch status {
            case .attending:
                // Azul sólido quando selecionado (conforme especificação do PDF)
                return CampusTheme.mathBlue
            case .absent:
                return Color(hex: 0xDC2626)
            case .cancelled:
                return colorScheme == .dark ? Color(hex: 0x334155) : Color(hex: 0x1E293B)
            }
        }()

        let foregroundColor: Color = {
            if isSelected {
                return .white
            }
            return CampusTheme.primaryText(for: colorScheme)
        }()

        return Button {
            withAnimation(.spring(response: 0.24, dampingFraction: 0.82)) {
                selectedStatus = status
            }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: status.systemImage)
                    .font(.system(size: 17, weight: .semibold))

                Text(status.buttonTitle)
                    .font(.system(size: 16, weight: .semibold, design: .rounded))

                Spacer()

                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 14, weight: .bold))
                }
            }
            .foregroundStyle(foregroundColor)
            .padding(.horizontal, 20)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(
                Capsule(style: .continuous)
                    .fill(fillColor)
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(
                        isSelected ? Color.clear : CampusTheme.cardBorder(for: colorScheme),
                        lineWidth: 1
                    )
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Footnote ("Aula cancelada não conta como falta. Salve para atualizar o relatório em Disciplinas.")

    private var footnoteBanner: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "info.circle.fill")
                .font(.system(size: 15))
                .foregroundStyle(CampusTheme.mathBlue)
                .padding(.top, 1)

            Text(detail.footnoteText)
                .font(.system(size: 13, weight: .regular))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(CampusTheme.selectedPillBackground(for: colorScheme).opacity(0.55))
        )
    }

    // MARK: - Primary "Salvar" Button

    private var saveButton: some View {
        Button {
            onSave(selectedStatus, selectedColorKey)
        } label: {
            Text("Salvar")
                .font(.system(size: 17, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 54)
                .background(
                    Capsule(style: .continuous)
                        .fill(CampusTheme.mathBlue)
                )
                .shadow(
                    color: CampusTheme.mathBlue.opacity(0.28),
                    radius: 10,
                    x: 0,
                    y: 4
                )
        }
        .buttonStyle(.plain)
    }
}

#Preview("ClassCheckInSheetView - Light") {
    let store = CampusStore()
    if let detail = store.getClassCheckInDetail(agendaItemId: "ter-17-calc-ii") {
        ClassCheckInSheetView(
            detail: detail,
            onSave: { _, _ in },
            onCancel: {}
        )
        .preferredColorScheme(.light)
    }
}

#Preview("ClassCheckInSheetView - Dark") {
    let store = CampusStore()
    if let detail = store.getClassCheckInDetail(agendaItemId: "ter-17-calc-ii") {
        ClassCheckInSheetView(
            detail: detail,
            onSave: { _, _ in },
            onCancel: {}
        )
        .preferredColorScheme(.dark)
    }
}
