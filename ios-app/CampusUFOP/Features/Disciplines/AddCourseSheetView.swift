import SwiftUI

public struct AddCourseSheetView: View {
    public let semester: String
    public let onSave: (
        _ departmentId: String,
        _ name: String,
        _ professor: String,
        _ workloadHours: Int,
        _ colorOverrideHex: String,
        _ initialSlot: InitialScheduleSlotInput?
    ) throws -> Void
    public let onCancel: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    @State private var name: String = ""
    @State private var professor: String = ""
    @State private var selectedDepartmentId: String = "comp"
    @State private var selectedColorKey: DepartmentColorKey = .compPurple
    @State private var hasCustomColorSelection: Bool = false
    @State private var workloadHours: Int = 60

    @State private var includeInitialSlot: Bool = true
    @State private var dayOfWeek: Int = 5 // SEX por padrão para facilitar adição sem conflito imediato
    @State private var startTime: String = "08:00"
    @State private var endTime: String = "09:40"
    @State private var building: String = "ICEB"
    @State private var room: String = "Sala 104"
    @State private var shortLocation: String = ""
    @State private var inlineErrorMessage: String?

    private let departmentOptions: [(id: String, label: String, defaultColor: DepartmentColorKey)] = [
        ("math", "Matemática", .mathBlue),
        ("comp", "Computação", .compPurple),
        ("phys", "Física", .physicsAmber)
    ]

    private let workloadOptions: [Int] = [30, 60, 90]

    private let weekdayOptions: [(day: Int, label: String)] = [
        (1, "SEG"),
        (2, "TER"),
        (3, "QUA"),
        (4, "QUI"),
        (5, "SEX"),
        (6, "SÁB")
    ]

    private let timeRangePresets: [(start: String, end: String)] = [
        ("08:00", "09:40"),
        ("10:00", "11:40"),
        ("14:00", "15:00"),
        ("16:00", "17:40")
    ]

    public init(
        semester: String = "2026.2",
        onSave: @escaping (
            _ departmentId: String,
            _ name: String,
            _ professor: String,
            _ workloadHours: Int,
            _ colorOverrideHex: String,
            _ initialSlot: InitialScheduleSlotInput?
        ) throws -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.semester = semester
        self.onSave = onSave
        self.onCancel = onCancel
    }

    private var accentColor: Color {
        CampusTheme.departmentColor(for: selectedColorKey)
    }

    private var previewMaxAbsences: Int {
        CampusStore.calculateMaxAbsences(workloadHours: workloadHours)
    }

    public var body: some View {
        ZStack {
            CampusTheme.cardBackground(for: colorScheme)
                .ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 20) {
                    topBar

                    headerSection

                    if let inlineErrorMessage {
                        errorBanner(message: inlineErrorMessage)
                    }

                    basicInfoSection

                    departmentPickerSection

                    colorSwatchSection

                    workloadSection

                    initialSlotSection

                    actionButtons
                }
                .padding(.horizontal, 22)
                .padding(.top, 18)
                .padding(.bottom, 32)
            }
        }
    }

    // MARK: - Top Bar & Header

    private var topBar: some View {
        HStack {
            Text("NOVA DISCIPLINA · \(semester)")
                .font(.system(size: 12, weight: .bold, design: .rounded))
                .tracking(1.4)
                .foregroundStyle(accentColor)

            Spacer()

            Button(action: onCancel) {
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

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Cadastrar disciplina")
                .font(.system(size: 26, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

            Text("Defina a carga horária para calcular automaticamente o limite de 25% da UFOP.")
                .font(.system(size: 14, weight: .regular))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
        }
    }

    private func errorBanner(message: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Color(hex: 0xDC2626))
                .padding(.top, 1)

            Text(message)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color(hex: 0xDC2626))
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 0)
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color(hex: 0xDC2626).opacity(colorScheme == .dark ? 0.18 : 0.10))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(Color(hex: 0xDC2626).opacity(0.35), lineWidth: 1)
        )
    }

    // MARK: - Basic Info

    private var basicInfoSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            inputField(
                label: "Nome da disciplina",
                text: $name,
                placeholder: "Ex.: Estruturas de Dados I"
            )

            inputField(
                label: "Docente",
                text: $professor,
                placeholder: "Ex.: Prof. Carlos Eduardo"
            )
        }
        .onChange(of: name) { _, _ in
            inlineErrorMessage = nil
        }
        .onChange(of: professor) { _, _ in
            inlineErrorMessage = nil
        }
    }

    // MARK: - Department & Color Swatch

    private var departmentPickerSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Departamento")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

            HStack(spacing: 8) {
                ForEach(departmentOptions, id: \.id) { option in
                    let isSelected = selectedDepartmentId == option.id
                    Button {
                        withAnimation(.snappy) {
                            selectedDepartmentId = option.id
                            if !hasCustomColorSelection {
                                selectedColorKey = option.defaultColor
                            }
                        }
                    } label: {
                        Text(option.label)
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .foregroundStyle(isSelected ? .white : CampusTheme.primaryText(for: colorScheme))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(
                                        isSelected
                                            ? CampusTheme.departmentColor(for: option.defaultColor)
                                            : CampusTheme.secondarySurface(for: colorScheme)
                                    )
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var colorSwatchSection: some View {
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
                            hasCustomColorSelection = true
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

    // MARK: - Workload Picker + Live Absence Limit Preview

    private var workloadSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Carga horária semestral")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

            HStack(spacing: 8) {
                ForEach(workloadOptions, id: \.self) { hours in
                    let isSelected = workloadHours == hours
                    Button {
                        withAnimation(.snappy) {
                            workloadHours = hours
                        }
                    } label: {
                        Text("\(hours) h")
                            .font(.system(size: 14, weight: .bold, design: .rounded))
                            .foregroundStyle(isSelected ? .white : CampusTheme.primaryText(for: colorScheme))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 11)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(isSelected ? accentColor : CampusTheme.secondarySurface(for: colorScheme))
                            )
                    }
                    .buttonStyle(.plain)
                }
            }

            HStack(spacing: 10) {
                Image(systemName: "checkmark.shield.fill")
                    .font(.system(size: 16))
                    .foregroundStyle(accentColor)

                VStack(alignment: .leading, spacing: 2) {
                    Text("Limite de faltas (25% UFOP): \(previewMaxAbsences) faltas")
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                    Text("Regra acadêmica: \(workloadHours) h permitem até \(previewMaxAbsences) faltas registradas.")
                        .font(.system(size: 12, weight: .regular))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                }

                Spacer(minLength: 0)
            }
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(CampusTheme.departmentSoftTint(for: selectedColorKey, scheme: colorScheme))
            )
        }
    }

    // MARK: - Initial Weekly Schedule Slot

    private var initialSlotSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Toggle(isOn: $includeInitialSlot.animation(.snappy)) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Horário semanal inicial")
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                    Text("Adiciona automaticamente à Grade horária e à Agenda")
                        .font(.system(size: 12, weight: .regular))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                }
            }
            .tint(accentColor)

            if includeInitialSlot {
                VStack(alignment: .leading, spacing: 12) {
                    // Dias da semana (SEG .. SÁB)
                    HStack(spacing: 6) {
                        ForEach(weekdayOptions, id: \.day) { item in
                            let isSelected = dayOfWeek == item.day
                            Button {
                                dayOfWeek = item.day
                                inlineErrorMessage = nil
                            } label: {
                                Text(item.label)
                                    .font(.system(size: 12, weight: .bold, design: .rounded))
                                    .foregroundStyle(isSelected ? .white : CampusTheme.secondaryText(for: colorScheme))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 8)
                                    .background(
                                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                                            .fill(isSelected ? accentColor : CampusTheme.secondarySurface(for: colorScheme))
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    // Atalhos rápidos de horário UFOP
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(timeRangePresets, id: \.start) { preset in
                                let isSelected = startTime == preset.start && endTime == preset.end
                                Button {
                                    startTime = preset.start
                                    endTime = preset.end
                                    inlineErrorMessage = nil
                                } label: {
                                    Text("\(preset.start)–\(preset.end)")
                                        .font(.system(size: 12, weight: .semibold, design: .rounded))
                                        .foregroundStyle(
                                            isSelected
                                                ? accentColor
                                                : CampusTheme.secondaryText(for: colorScheme)
                                        )
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(
                                            Capsule(style: .continuous)
                                                .fill(
                                                    isSelected
                                                        ? CampusTheme.departmentSoftTint(for: selectedColorKey, scheme: colorScheme)
                                                        : CampusTheme.secondarySurface(for: colorScheme)
                                                )
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    HStack(spacing: 10) {
                        inputField(label: "Início", text: $startTime, placeholder: "08:00")
                        inputField(label: "Término", text: $endTime, placeholder: "09:40")
                    }

                    HStack(spacing: 10) {
                        inputField(label: "Prédio", text: $building, placeholder: "ICEB")
                        inputField(label: "Sala", text: $room, placeholder: "Sala 104")
                    }

                    inputField(
                        label: "Local compacto na grade (opcional)",
                        text: $shortLocation,
                        placeholder: "Ex.: ICEB · 104"
                    )
                }
                .padding(14)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(CampusTheme.appBackground(for: colorScheme))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
                )
                .onChange(of: startTime) { _, _ in inlineErrorMessage = nil }
                .onChange(of: endTime) { _, _ in inlineErrorMessage = nil }
            }
        }
    }

    // MARK: - Actions

    private var actionButtons: some View {
        HStack(spacing: 12) {
            Button(action: onCancel) {
                Text("Cancelar")
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(CampusTheme.secondarySurface(for: colorScheme))
                    )
            }
            .buttonStyle(.plain)

            Button {
                submitCourse()
            } label: {
                Text("Salvar disciplina")
                    .font(.system(size: 15, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(accentColor)
                    )
            }
            .buttonStyle(.plain)
        }
        .padding(.top, 4)
    }

    private func submitCourse() {
        inlineErrorMessage = nil
        let slotInput: InitialScheduleSlotInput? = includeInitialSlot
            ? InitialScheduleSlotInput(
                dayOfWeek: dayOfWeek,
                startTime: startTime,
                endTime: endTime,
                building: building,
                room: room,
                shortLocation: shortLocation
            )
            : nil

        do {
            try onSave(
                selectedDepartmentId,
                name,
                professor,
                workloadHours,
                selectedColorKey.hexString,
                slotInput
            )
        } catch {
            withAnimation(.snappy) {
                inlineErrorMessage = error.localizedDescription
            }
        }
    }

    private func inputField(label: String, text: Binding<String>, placeholder: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

            TextField(placeholder, text: text)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                .padding(.horizontal, 14)
                .padding(.vertical, 11)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(CampusTheme.cardBackground(for: colorScheme))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
                )
        }
    }
}

#Preview("AddCourseSheetView - Light") {
    AddCourseSheetView(
        semester: "2026.2",
        onSave: { _, _, _, _, _, _ in },
        onCancel: {}
    )
    .preferredColorScheme(.light)
}

#Preview("AddCourseSheetView - Dark") {
    AddCourseSheetView(
        semester: "2026.2",
        onSave: { _, _, _, _, _, _ in },
        onCancel: {}
    )
    .preferredColorScheme(.dark)
}
