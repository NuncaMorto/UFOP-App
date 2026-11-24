import SwiftUI

// MARK: - Weekly Schedule Slots Editor

internal struct DisciplineWeeklySlotsSection: View {
    let courseName: String
    let colorKey: DepartmentColorKey
    let slots: [CourseScheduleSlotItem]
    let isEadCourse: Bool
    let deptColor: Color
    let highContrast: Bool
    let minTouch: CGFloat
    @Binding var isAddingSlot: Bool
    @Binding var newSlotDayOfWeek: Int
    @Binding var newSlotStartTime: String
    @Binding var newSlotEndTime: String
    @Binding var newSlotBuilding: String
    @Binding var newSlotRoom: String
    @Binding var newSlotShortLocation: String
    @Binding var slotConflictError: String?
    let onSubmitSlot: () -> Void
    let onDeleteSlot: (String) -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center) {
                Text("Horários semanais")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                Spacer()

                Button {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                        isAddingSlot.toggle()
                        slotConflictError = nil
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: isAddingSlot ? "xmark" : "plus")
                            .font(.system(size: 11, weight: .bold))
                        Text(isAddingSlot ? "Fechar" : "Adicionar horário")
                            .font(.system(size: 12, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(
                        Capsule(style: .continuous)
                            .fill(CampusTheme.departmentSoftTint(for: colorKey, scheme: colorScheme))
                    )
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
                .accessibilityLabel("Adicionar horário semanal")
            }

            if isAddingSlot {
                DisciplineAddSlotCard(
                    courseName: courseName,
                    isEadCourse: isEadCourse,
                    deptColor: deptColor,
                    highContrast: highContrast,
                    newSlotDayOfWeek: $newSlotDayOfWeek,
                    newSlotStartTime: $newSlotStartTime,
                    newSlotEndTime: $newSlotEndTime,
                    newSlotBuilding: $newSlotBuilding,
                    newSlotRoom: $newSlotRoom,
                    newSlotShortLocation: $newSlotShortLocation,
                    slotConflictError: $slotConflictError,
                    onSubmit: onSubmitSlot
                )
            }

            if slots.isEmpty {
                Text("Nenhum horário semanal cadastrado para esta disciplina.")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                    .liquidGlassCard(cornerRadius: 16, highContrast: highContrast)
            } else {
                VStack(spacing: 10) {
                    ForEach(slots) { slot in
                        DisciplineScheduleSlotRow(
                            slot: slot,
                            colorKey: colorKey,
                            isEadCourse: isEadCourse,
                            deptColor: deptColor,
                            highContrast: highContrast,
                            minTouch: minTouch,
                            onDelete: {
                                withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                                    onDeleteSlot(slot.slotId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private struct DisciplineAddSlotCard: View {
    let courseName: String
    let isEadCourse: Bool
    let deptColor: Color
    let highContrast: Bool
    @Binding var newSlotDayOfWeek: Int
    @Binding var newSlotStartTime: String
    @Binding var newSlotEndTime: String
    @Binding var newSlotBuilding: String
    @Binding var newSlotRoom: String
    @Binding var newSlotShortLocation: String
    @Binding var slotConflictError: String?
    let onSubmit: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    private let weekdayOptions: [(day: Int, label: String)] = [
        (1, "SEG"),
        (2, "TER"),
        (3, "QUA"),
        (4, "QUI"),
        (5, "SEX"),
        (6, "SÁB")
    ]
    private let slotTimePresets: [(start: String, end: String)] = [
        ("08:00", "09:40"),
        ("10:00", "11:40"),
        ("13:30", "15:10"),
        ("15:20", "17:00"),
        ("19:00", "20:40"),
        ("20:50", "22:30")
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Novo horário na grade (\(courseName))")
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

            if let slotConflictError {
                DisciplineDetailFeedbackBanner(text: slotConflictError, color: Color(hex: 0xDC2626), icon: "exclamationmark.triangle.fill", highContrast: highContrast)
            }

            HStack(spacing: 6) {
                ForEach(weekdayOptions, id: \.day) { option in
                    let isSelected = newSlotDayOfWeek == option.day
                    Button {
                        newSlotDayOfWeek = option.day
                        slotConflictError = nil
                    } label: {
                        Text(option.label)
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundStyle(isSelected ? .white : CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                            .frame(maxWidth: .infinity, minHeight: 36)
                            .liquidGlassCapsule(selected: isSelected, accent: deptColor, interactive: true, highContrast: highContrast)
                    }
                    .buttonStyle(.plain)
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(slotTimePresets, id: \.start) { preset in
                        let isSelected = newSlotStartTime == preset.start && newSlotEndTime == preset.end
                        Button {
                            newSlotStartTime = preset.start
                            newSlotEndTime = preset.end
                            slotConflictError = nil
                        } label: {
                            Text("\(preset.start)–\(preset.end)")
                                .font(.system(size: 11, weight: .semibold, design: .rounded))
                                .foregroundStyle(isSelected ? .white : CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .liquidGlassCapsule(selected: isSelected, accent: deptColor, interactive: true, highContrast: highContrast)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            HStack(spacing: 10) {
                DisciplineDetailTextField(placeholder: "Início (08:00)", text: $newSlotStartTime, highContrast: highContrast)
                DisciplineDetailTextField(placeholder: "Término (09:40)", text: $newSlotEndTime, highContrast: highContrast)
            }
            .onChange(of: newSlotStartTime) { _, _ in slotConflictError = nil }
            .onChange(of: newSlotEndTime) { _, _ in slotConflictError = nil }

            if !isEadCourse {
                HStack(spacing: 10) {
                    DisciplineDetailTextField(placeholder: "Prédio (ex.: ICEB)", text: $newSlotBuilding, highContrast: highContrast)
                    DisciplineDetailTextField(placeholder: "Sala (opcional)", text: $newSlotRoom, highContrast: highContrast)
                }

                DisciplineDetailTextField(placeholder: "Rótulo curto na grade (opcional)", text: $newSlotShortLocation, highContrast: highContrast)
            }

            LiquidGlassPrimaryButton(tint: deptColor, foreground: .white, minHeight: 44) {
                onSubmit()
            } label: {
                Label("Salvar horário na grade", systemImage: "calendar.badge.plus")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
            }
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: 16, tint: deptColor, highContrast: highContrast)
    }
}

private struct DisciplineScheduleSlotRow: View {
    let slot: CourseScheduleSlotItem
    let colorKey: DepartmentColorKey
    let isEadCourse: Bool
    let deptColor: Color
    let highContrast: Bool
    let minTouch: CGFloat
    let onDelete: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let cleanRoom = slot.room.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanBuilding = slot.building.trimmingCharacters(in: .whitespacesAndNewlines)
        let locationLabel: String = {
            if isEadCourse { return "A distância (EAD)" }
            if cleanBuilding.isEmpty && cleanRoom.isEmpty { return "Sala não informada" }
            if cleanRoom.isEmpty { return cleanBuilding }
            if cleanBuilding.isEmpty { return cleanRoom }
            return "\(cleanBuilding) · \(cleanRoom)"
        }()

        HStack(spacing: 12) {
            Text(slot.dayLabel)
                .font(.system(size: 12, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))
                .frame(width: 46, height: 38)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(CampusTheme.departmentSoftTint(for: colorKey, scheme: colorScheme))
                )

            VStack(alignment: .leading, spacing: 2) {
                Text(slot.timeRange)
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                Text(locationLabel)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                    .lineLimit(1)
            }

            Spacer()

            Button(role: .destructive) {
                onDelete()
            } label: {
                Image(systemName: "trash")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color(hex: 0xDC2626))
                    .frame(width: 34, height: 34)
                    .background(
                        Circle()
                            .fill(Color(hex: 0xDC2626).opacity(colorScheme == .dark ? 0.18 : 0.10))
                    )
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
            .accessibilityLabel("Remover horário \(slot.dayLabel) \(slot.startTime)")
        }
        .padding(12)
        .liquidGlassCard(cornerRadius: 16, highContrast: highContrast)
    }
}
