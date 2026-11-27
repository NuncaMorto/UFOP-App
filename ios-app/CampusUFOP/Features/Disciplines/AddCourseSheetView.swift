import SwiftUI

public struct AddCourseSheetView: View {
    public let semester: String
    public let onSave: (
        _ departmentId: String,
        _ name: String,
        _ professor: String,
        _ workloadHours: Int,
        _ colorOverrideHex: String,
        _ isEad: Bool,
        _ initialSlot: InitialScheduleSlotInput?
    ) throws -> Void
    public let onCancel: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    @State private var name: String = ""
    @State private var professor: String = ""
    @State private var selectedDepartmentId: String = "comp"
    @State private var selectedColorKey: DepartmentColorKey = .compPurple
    @State private var hasCustomColorSelection: Bool = false
    @State private var workloadHours: Int = 60
    @State private var isEad: Bool = false

    @State private var includeInitialSlot: Bool = true
    @State private var dayOfWeek: Int = 5
    @State private var startTime: String = "08:00"
    @State private var endTime: String = "09:40"
    @State private var building: String = ""
    @State private var room: String = ""
    @State private var shortLocation: String = ""
    @State private var inlineErrorMessage: String?

    private let departmentOptions: [(id: String, label: String, defaultColor: DepartmentColorKey)] = [
        ("math", "Matemática", .mathBlue),
        ("comp", "Computação", .compPurple),
        ("phys", "Física", .physicsAmber)
    ]

    private let workloadOptions: [Int] = [30, 60, 90]

    private let weekdayOptions: [(day: Int, label: String)] = [
        (1, "SEG"), (2, "TER"), (3, "QUA"), (4, "QUI"), (5, "SEX"), (6, "SÁB")
    ]

    private let timeRangePresets: [(start: String, end: String)] = [
        ("08:00", "09:40"), ("10:00", "11:40"), ("14:00", "15:00"), ("16:00", "17:40")
    ]

    public init(
        semester: String = "2026.2",
        onSave: @escaping (
            _ departmentId: String,
            _ name: String,
            _ professor: String,
            _ workloadHours: Int,
            _ colorOverrideHex: String,
            _ isEad: Bool,
            _ initialSlot: InitialScheduleSlotInput?
        ) throws -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.semester = semester
        self.onSave = onSave
        self.onCancel = onCancel
    }

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
        self.onSave = { dept, name, prof, ch, color, _, slot in
            try onSave(dept, name, prof, ch, color, slot)
        }
        self.onCancel = onCancel
    }

    private var accentColor: Color {
        CampusTheme.departmentColor(for: selectedColorKey)
    }

    private var hc: Bool {
        appearance?.accessibility.highContrastMode ?? false
    }

    private var previewMaxAbsences: Int {
        CampusStore.calculateMaxAbsences(workloadHours: workloadHours)
    }

    public var body: some View {
        ZStack {
            LiquidGlassBackdrop(accent: appearance?.accentColor ?? .green, highContrast: hc)

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    topBar

                    Text("Cadastrar disciplina")
                        .font(CampusTheme.font(size: 26, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                    if let inlineErrorMessage {
                        Text(inlineErrorMessage)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Color(hex: 0xEF4444))
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: 0xEF4444).opacity(0.14)))
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        inputField(label: "Nome da disciplina", text: $name, placeholder: "Ex.: Estruturas de Dados I")
                        inputField(label: "Professor(a) (opcional)", text: $professor, placeholder: "Deixe vazio se ainda não souber")

                        Toggle("Disciplina a distância (EAD)", isOn: $isEad.animation(.snappy))
                            .tint(accentColor)
                            .padding(12)
                            .liquidGlassCard(cornerRadius: 14, highContrast: hc)
                    }
                    .onChange(of: name) { _, _ in inlineErrorMessage = nil }

                    departmentAndColorSection

                    workloadSection

                    initialSlotSection

                    LiquidGlassPrimaryButton(
                        tint: accentColor,
                        foreground: (appearance?.accentColor ?? .red).onPrimary(for: colorScheme),
                        minHeight: appearance?.accessibility.minTouchHeight ?? 48
                    ) {
                        submitCourse()
                    } label: {
                        Text("Salvar disciplina")
                            .font(CampusTheme.font(size: 15, weight: .bold))
                    }
                }
                .padding(20)
            }
        }
    }

    private var topBar: some View {
        HStack {
            Text("NOVA DISCIPLINA · \(semester)")
                .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                .tracking(1.4)
                .foregroundStyle(accentColor)

            Spacer()

            Button(action: onCancel) {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    .frame(width: 34, height: 34)
                    .liquidGlassCapsule(selected: false, accent: accentColor, highContrast: hc)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Fechar")
        }
    }

    private var departmentAndColorSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Área e cor")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

            HStack(spacing: 8) {
                ForEach(departmentOptions, id: \.id) { option in
                    let isSelected = selectedDepartmentId == option.id
                    Button {
                        selectedDepartmentId = option.id
                        if !hasCustomColorSelection { selectedColorKey = option.defaultColor }
                    } label: {
                        Text(option.label)
                            .font(CampusTheme.font(size: 13, weight: .bold))
                            .foregroundStyle(isSelected ? .white : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .liquidGlassCapsule(selected: isSelected, accent: CampusTheme.departmentColor(for: option.defaultColor), highContrast: hc)
                    }
                    .buttonStyle(.plain)
                }
            }

            HStack(spacing: 14) {
                ForEach(DepartmentColorKey.allCases) { key in
                    let isSelected = key == selectedColorKey
                    let swatch = CampusTheme.departmentColor(for: key)
                    Button {
                        selectedColorKey = key
                        hasCustomColorSelection = true
                    } label: {
                        Circle()
                            .fill(swatch)
                            .frame(width: 32, height: 32)
                            .overlay(
                                Image(systemName: isSelected ? "checkmark" : "")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundStyle(.white)
                            )
                            .padding(3)
                            .overlay(Circle().strokeBorder(isSelected ? swatch : .clear, lineWidth: 2))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var workloadSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Carga horária semestral")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

            HStack(spacing: 8) {
                ForEach(workloadOptions, id: \.self) { hours in
                    let isSelected = workloadHours == hours
                    Button {
                        workloadHours = hours
                    } label: {
                        Text("\(hours)h")
                            .font(CampusTheme.tabularFont(size: 14, weight: .bold))
                            .foregroundStyle(isSelected ? (appearance?.accentColor ?? .red).onPrimary(for: colorScheme) : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .liquidGlassCapsule(selected: isSelected, accent: accentColor, highContrast: hc)
                    }
                    .buttonStyle(.plain)
                }
            }

            Text("Limite de 25%: até \(previewMaxAbsences) faltas.")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
        }
    }

    private var initialSlotSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Toggle("Horário semanal inicial", isOn: $includeInitialSlot.animation(.snappy))
                .tint(accentColor)

            if includeInitialSlot {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 6) {
                        ForEach(weekdayOptions, id: \.day) { item in
                            let isSelected = dayOfWeek == item.day
                            Button {
                                dayOfWeek = item.day
                                inlineErrorMessage = nil
                            } label: {
                                Text(item.label)
                                    .font(CampusTheme.font(size: 12, weight: .bold))
                                    .foregroundStyle(isSelected ? (appearance?.accentColor ?? .red).onPrimary(for: colorScheme) : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 8)
                                    .liquidGlassCapsule(selected: isSelected, accent: accentColor, highContrast: hc)
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(timeRangePresets, id: \.start) { preset in
                                let isSelected = startTime == preset.start && endTime == preset.end
                                Button {
                                    startTime = preset.start
                                    endTime = preset.end
                                } label: {
                                    Text("\(preset.start)–\(preset.end)")
                                        .font(CampusTheme.tabularFont(size: 12, weight: .semibold))
                                        .foregroundStyle(isSelected ? (appearance?.accentColor ?? .red).onPrimary(for: colorScheme) : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .liquidGlassCapsule(selected: isSelected, accent: accentColor, highContrast: hc)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    HStack(spacing: 10) {
                        inputField(label: "Início", text: $startTime, placeholder: "08:00")
                        inputField(label: "Término", text: $endTime, placeholder: "09:40")
                    }

                    if !isEad {
                        HStack(spacing: 10) {
                            inputField(label: "Prédio", text: $building, placeholder: "Ex.: ICEB")
                            inputField(label: "Sala (opcional)", text: $room, placeholder: "Deixe vazio se sem tabela")
                        }
                    }
                }
                .padding(14)
                .liquidGlassCard(cornerRadius: 16, highContrast: hc)
            }
        }
    }

    private func submitCourse() {
        inlineErrorMessage = nil
        let slotInput: InitialScheduleSlotInput? = includeInitialSlot
            ? InitialScheduleSlotInput(
                dayOfWeek: dayOfWeek,
                startTime: startTime,
                endTime: endTime,
                building: isEad ? "A distância (EAD)" : building,
                room: isEad ? "" : room,
                shortLocation: isEad ? "EAD" : shortLocation
            )
            : nil

        do {
            try onSave(
                selectedDepartmentId,
                name,
                professor,
                workloadHours,
                selectedColorKey.hexString,
                isEad,
                slotInput
            )
        } catch {
            inlineErrorMessage = error.localizedDescription
        }
    }

    private func inputField(label: String, text: Binding<String>, placeholder: String) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
            TextField(placeholder, text: text)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .liquidGlassCard(cornerRadius: 12, highContrast: hc)
        }
    }
}
