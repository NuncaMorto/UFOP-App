import SwiftUI

public struct DisciplineDetailSheetView: View {
    public let detail: DisciplineDetail
    public let store: CampusStore?
    public let onUpdateNotes: (String) -> Void
    public let onDeleteCourse: (() -> Void)?
    public let onClose: () -> Void

    @Environment(CampusStore.self) private var environmentStore: CampusStore?
    @Environment(\.colorScheme) private var colorScheme

    @State private var notesText: String
    @State private var localAssessments: [AssessmentItem]
    @State private var localSlots: [CourseScheduleSlotItem] = []

    @State private var isAddingAssessment: Bool = false
    @State private var newAssessmentTitle: String = ""
    @State private var newAssessmentKind: String = "Trabalho"
    @State private var newAssessmentDueDate: String = "29 NOV"
    @State private var newAssessmentDueTime: String = "18:00"

    @State private var isAddingSlot: Bool = false
    @State private var newSlotDayOfWeek: Int = 5
    @State private var newSlotStartTime: String = "08:00"
    @State private var newSlotEndTime: String = "09:40"
    @State private var newSlotBuilding: String = "Prédio A"
    @State private var newSlotRoom: String = "Sala 204"
    @State private var newSlotShortLocation: String = ""
    @State private var slotConflictError: String?

    @State private var isDeleteConfirmationPresented: Bool = false

    private let assessmentKinds = ["Trabalho", "Seminário", "Prova"]
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
        ("14:00", "15:00"),
        ("16:00", "17:40")
    ]

    public init(
        detail: DisciplineDetail,
        store: CampusStore? = nil,
        onUpdateNotes: @escaping (String) -> Void,
        onDeleteCourse: (() -> Void)? = nil,
        onClose: @escaping () -> Void
    ) {
        self.detail = detail
        self.store = store
        self.onUpdateNotes = onUpdateNotes
        self.onDeleteCourse = onDeleteCourse
        self.onClose = onClose
        self._notesText = State(initialValue: detail.notes)
        self._localAssessments = State(initialValue: detail.assessments)
    }

    private var activeStore: CampusStore? {
        store ?? environmentStore
    }

    private var resolvedDetail: DisciplineDetail {
        if let activeStore,
           let live = activeStore.getDisciplineDetail(disciplineId: detail.summary.id) {
            return live
        }
        return DisciplineDetail(
            summary: detail.summary,
            assessments: localAssessments,
            notes: notesText
        )
    }

    private var resolvedSlots: [CourseScheduleSlotItem] {
        if let activeStore {
            return activeStore.listCourseSlots(courseId: resolvedDetail.summary.id)
        }
        return localSlots
    }

    private var resolvedAbsenceHistory: [AbsenceHistoryEntry] {
        activeStore?.listAbsenceHistory(courseId: resolvedDetail.summary.id) ?? []
    }

    private var accentColor: Color {
        CampusTheme.departmentColor(for: resolvedDetail.summary.colorKey)
    }

    public var body: some View {
        let currentDetail = resolvedDetail

        ZStack {
            CampusTheme.cardBackground(for: colorScheme)
                .ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 22) {
                    topBar(summary: currentDetail.summary)

                    headerSection(summary: currentDetail.summary)

                    absenceReportCard(summary: currentDetail.summary)

                    absenceHistorySection(currentDetail: currentDetail)

                    weeklySlotsSection(currentDetail: currentDetail)

                    assessmentsSection(currentDetail: currentDetail)

                    notesSection

                    deleteCourseSection(summary: currentDetail.summary)
                }
                .padding(.horizontal, 22)
                .padding(.top, 18)
                .padding(.bottom, 30)
            }
        }
        .onChange(of: notesText) { _, newValue in
            onUpdateNotes(newValue)
        }
        .confirmationDialog(
            "Remover \(currentDetail.summary.name)?",
            isPresented: $isDeleteConfirmationPresented,
            titleVisibility: .visible
        ) {
            Button("Remover disciplina", role: .destructive) {
                performDeleteCourse(courseId: currentDetail.summary.id)
            }
            Button("Cancelar", role: .cancel) {}
        } message: {
            Text("Esta ação removerá a disciplina, seus horários semanais da grade e suas avaliações associadas.")
        }
    }

    // MARK: - Top Bar ("Matemática" + Close)

    private func topBar(summary: DisciplineSummaryCard) -> some View {
        HStack {
            Text(summary.department)
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundStyle(accentColor)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    Capsule(style: .continuous)
                        .fill(CampusTheme.departmentSoftTint(for: summary.colorKey, scheme: colorScheme))
                )

            Spacer()

            Button {
                onUpdateNotes(notesText)
                onClose()
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

    // MARK: - Header ("Cálculo II", "60 h · 2026.2")

    private func headerSection(summary: DisciplineSummaryCard) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(summary.name)
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

            HStack(spacing: 10) {
                Text(summary.workloadAndSemesterLabel)
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

                Text("•")
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme))

                Text(summary.professor)
                    .font(.system(size: 14, weight: .regular))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
            }
        }
    }

    // MARK: - Absence Card ("4 / 18 faltas", "0 canceladas · não somam faltas")

    private func absenceReportCard(summary: DisciplineSummaryCard) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text(summary.expandedAbsenceText)
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Spacer()

                Text("Restam \(summary.remainingAbsences)")
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundStyle(accentColor)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(
                        Capsule(style: .continuous)
                            .fill(CampusTheme.departmentSoftTint(for: summary.colorKey, scheme: colorScheme))
                    )
            }

            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule(style: .continuous)
                        .fill(CampusTheme.secondarySurface(for: colorScheme))
                        .frame(height: 8)

                    Capsule(style: .continuous)
                        .fill(accentColor)
                        .frame(
                            width: max(8, proxy.size.width * summary.absenceProgress),
                            height: 8
                        )
                }
            }
            .frame(height: 8)

            HStack(spacing: 6) {
                Image(systemName: "slash.circle")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

                Text(summary.cancelledSummaryLabel)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
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

    // MARK: - Absence & Cancellation Check-in History ("Histórico de marcações")

    private func absenceHistorySection(currentDetail: DisciplineDetail) -> some View {
        let history = resolvedAbsenceHistory

        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center) {
                Text("Histórico de marcações")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Spacer()

                Text("\(history.count) \(history.count == 1 ? "registro" : "registros")")
                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(
                        Capsule(style: .continuous)
                            .fill(CampusTheme.secondarySurface(for: colorScheme))
                    )
            }

            if history.isEmpty {
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: "clock.arrow.circlepath")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(accentColor)
                        .padding(.top, 2)

                    VStack(alignment: .leading, spacing: 3) {
                        Text("Nenhuma falta ou aula cancelada registrada")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                        Text("As marcações salvas no check-in da Agenda para \(currentDetail.summary.name) aparecem aqui.")
                            .font(.system(size: 12, weight: .regular))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    Spacer(minLength: 0)
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
            } else {
                VStack(spacing: 10) {
                    ForEach(history) { entry in
                        absenceHistoryRow(entry: entry)
                    }
                }
            }
        }
    }

    private func absenceHistoryRow(entry: AbsenceHistoryEntry) -> some View {
        let isAbsent = entry.status == .absent
        let badgeForeground: Color = isAbsent
            ? (colorScheme == .dark ? Color(hex: 0xFCA5A5) : Color(hex: 0xDC2626))
            : CampusTheme.secondaryText(for: colorScheme)
        let badgeBackground: Color = isAbsent
            ? Color(hex: 0xDC2626).opacity(colorScheme == .dark ? 0.22 : 0.12)
            : CampusTheme.secondarySurface(for: colorScheme)
        let iconName = isAbsent ? "exclamationmark.circle.fill" : "slash.circle.fill"

        return HStack(spacing: 12) {
            Image(systemName: iconName)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(badgeForeground)
                .frame(width: 36, height: 36)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(badgeBackground)
                )

            VStack(alignment: .leading, spacing: 2) {
                Text(entry.formattedDate)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                    .lineLimit(1)

                Text(entry.timeRangeLabel)
                    .font(.system(size: 12, weight: .medium, design: .rounded))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
            }

            Spacer(minLength: 6)

            Text(entry.badgeTitle)
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .foregroundStyle(badgeForeground)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(
                    Capsule(style: .continuous)
                        .fill(badgeBackground)
                )
                .lineLimit(1)
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(CampusTheme.appBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
    }

    // MARK: - Weekly Schedule Slots ("Horários semanais" + "+ Adicionar horário" + Conflict Banner)

    private func weeklySlotsSection(currentDetail: DisciplineDetail) -> some View {
        let slots = resolvedSlots

        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center) {
                Text("Horários semanais")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

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
                    .foregroundStyle(accentColor)
                    .padding(.horizontal, 11)
                    .padding(.vertical, 6)
                    .background(
                        Capsule(style: .continuous)
                            .fill(CampusTheme.departmentSoftTint(for: currentDetail.summary.colorKey, scheme: colorScheme))
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Adicionar horário semanal")
            }

            if isAddingSlot {
                addScheduleSlotCard(courseId: currentDetail.summary.id, colorKey: currentDetail.summary.colorKey)
            }

            if slots.isEmpty {
                Text("Nenhum horário semanal cadastrado para esta disciplina.")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(CampusTheme.appBackground(for: colorScheme))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
                    )
            } else {
                VStack(spacing: 10) {
                    ForEach(slots) { slot in
                        scheduleSlotRow(slot: slot, colorKey: currentDetail.summary.colorKey)
                    }
                }
            }
        }
    }

    private func addScheduleSlotCard(courseId: String, colorKey: DepartmentColorKey) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Novo horário na grade (\(resolvedDetail.summary.name))")
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

            if let slotConflictError {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color(hex: 0xDC2626))
                        .padding(.top, 1)

                    Text(slotConflictError)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color(hex: 0xDC2626))
                        .fixedSize(horizontal: false, vertical: true)

                    Spacer(minLength: 0)
                }
                .padding(10)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(Color(hex: 0xDC2626).opacity(colorScheme == .dark ? 0.18 : 0.10))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .strokeBorder(Color(hex: 0xDC2626).opacity(0.35), lineWidth: 1)
                )
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
                            .foregroundStyle(isSelected ? .white : CampusTheme.secondaryText(for: colorScheme))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(
                                RoundedRectangle(cornerRadius: 9, style: .continuous)
                                    .fill(isSelected ? accentColor : CampusTheme.secondarySurface(for: colorScheme))
                            )
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
                                .foregroundStyle(isSelected ? accentColor : CampusTheme.secondaryText(for: colorScheme))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(
                                    Capsule(style: .continuous)
                                        .fill(
                                            isSelected
                                                ? CampusTheme.departmentSoftTint(for: colorKey, scheme: colorScheme)
                                                : CampusTheme.secondarySurface(for: colorScheme)
                                        )
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            HStack(spacing: 10) {
                slotTextField(placeholder: "Início (08:00)", text: $newSlotStartTime)
                slotTextField(placeholder: "Término (09:40)", text: $newSlotEndTime)
            }
            .onChange(of: newSlotStartTime) { _, _ in slotConflictError = nil }
            .onChange(of: newSlotEndTime) { _, _ in slotConflictError = nil }

            HStack(spacing: 10) {
                slotTextField(placeholder: "Prédio (ex.: Prédio A)", text: $newSlotBuilding)
                slotTextField(placeholder: "Sala (ex.: Sala 204)", text: $newSlotRoom)
            }

            slotTextField(placeholder: "Rótulo curto na grade (opcional, ex.: A · 204)", text: $newSlotShortLocation)

            HStack {
                Spacer()

                Button {
                    submitNewSlot(courseId: courseId)
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "calendar.badge.plus")
                            .font(.system(size: 13, weight: .bold))
                        Text("Salvar horário na grade")
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(
                        Capsule(style: .continuous)
                            .fill(accentColor)
                    )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(CampusTheme.appBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(accentColor.opacity(0.35), lineWidth: 1)
        )
    }

    private func slotTextField(placeholder: String, text: Binding<String>) -> some View {
        TextField(placeholder, text: text)
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(CampusTheme.cardBackground(for: colorScheme))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
            )
    }

    private func scheduleSlotRow(slot: CourseScheduleSlotItem, colorKey: DepartmentColorKey) -> some View {
        HStack(spacing: 12) {
            Text(slot.dayLabel)
                .font(.system(size: 12, weight: .bold, design: .rounded))
                .foregroundStyle(accentColor)
                .frame(width: 46, height: 38)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(CampusTheme.departmentSoftTint(for: colorKey, scheme: colorScheme))
                )

            VStack(alignment: .leading, spacing: 2) {
                Text(slot.timeRange)
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Text("\(slot.building) · \(slot.room) (\(slot.shortLocation))")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                    .lineLimit(1)
            }

            Spacer()

            Button(role: .destructive) {
                withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                    deleteSlot(slot.slotId)
                }
            } label: {
                Image(systemName: "trash")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color(hex: 0xDC2626))
                    .frame(width: 32, height: 32)
                    .background(
                        Circle()
                            .fill(Color(hex: 0xDC2626).opacity(colorScheme == .dark ? 0.18 : 0.10))
                    )
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Remover horário \(slot.dayLabel) \(slot.startTime)")
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(CampusTheme.appBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
        )
    }

    private func submitNewSlot(courseId: String) {
        slotConflictError = nil
        if let activeStore {
            do {
                _ = try activeStore.addScheduleSlot(
                    courseId: courseId,
                    dayOfWeek: newSlotDayOfWeek,
                    startTime: newSlotStartTime,
                    endTime: newSlotEndTime,
                    building: newSlotBuilding,
                    room: newSlotRoom,
                    shortLocation: newSlotShortLocation
                )
                withAnimation(.snappy) {
                    isAddingSlot = false
                    newSlotShortLocation = ""
                }
            } catch {
                withAnimation(.snappy) {
                    slotConflictError = error.localizedDescription
                }
            }
        } else {
            let dayLabels = [1: "SEG", 2: "TER", 3: "QUA", 4: "QUI", 5: "SEX", 6: "SÁB"]
            let slot = CourseScheduleSlotItem(
                slotId: UUID().uuidString,
                courseId: courseId,
                dayOfWeek: newSlotDayOfWeek,
                dayLabel: dayLabels[newSlotDayOfWeek] ?? "SEG",
                startTime: newSlotStartTime,
                endTime: newSlotEndTime,
                building: newSlotBuilding,
                room: newSlotRoom,
                shortLocation: newSlotShortLocation.isEmpty ? newSlotRoom : newSlotShortLocation
            )
            localSlots.append(slot)
            withAnimation(.snappy) {
                isAddingSlot = false
            }
        }
    }

    private func deleteSlot(_ slotId: String) {
        if let activeStore {
            activeStore.deleteScheduleSlot(slotId: slotId)
        } else {
            localSlots.removeAll { $0.slotId == slotId }
        }
    }

    // MARK: - Assessments ("Prazos e avaliações" + Check/Uncheck + Add Trabalho/Seminário/Prova)

    private func assessmentsSection(currentDetail: DisciplineDetail) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center) {
                Text("Prazos e avaliações")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Spacer()

                Button {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                        isAddingAssessment.toggle()
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: isAddingAssessment ? "xmark" : "plus")
                            .font(.system(size: 11, weight: .bold))
                        Text(isAddingAssessment ? "Fechar" : "Novo prazo")
                            .font(.system(size: 12, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(accentColor)
                    .padding(.horizontal, 11)
                    .padding(.vertical, 6)
                    .background(
                        Capsule(style: .continuous)
                            .fill(CampusTheme.departmentSoftTint(for: currentDetail.summary.colorKey, scheme: colorScheme))
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Adicionar avaliação")
            }

            if isAddingAssessment {
                addAssessmentCard(courseId: currentDetail.summary.id)
            }

            VStack(spacing: 10) {
                ForEach(currentDetail.assessments) { item in
                    assessmentRow(item: item, colorKey: currentDetail.summary.colorKey)
                }
            }
        }
    }

    private func addAssessmentCard(courseId: String) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Nova avaliação (\(resolvedDetail.summary.name))")
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

            // Tipo: Trabalho / Seminário / Prova
            HStack(spacing: 8) {
                ForEach(assessmentKinds, id: \.self) { kind in
                    let isSelected = newAssessmentKind == kind
                    Button {
                        newAssessmentKind = kind
                    } label: {
                        Text(kind)
                            .font(.system(size: 12, weight: .semibold, design: .rounded))
                            .foregroundStyle(isSelected ? .white : CampusTheme.secondaryText(for: colorScheme))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(isSelected ? accentColor : CampusTheme.secondarySurface(for: colorScheme))
                            )
                    }
                    .buttonStyle(.plain)
                }
            }

            TextField("Título (ex.: Seminário de Séries, Prova 3)", text: $newAssessmentTitle)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(CampusTheme.cardBackground(for: colorScheme))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
                )

            HStack(spacing: 10) {
                TextField("Data (ex.: 29 NOV)", text: $newAssessmentDueDate)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(CampusTheme.cardBackground(for: colorScheme))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
                    )

                TextField("Horário (18:00)", text: $newAssessmentDueTime)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(CampusTheme.cardBackground(for: colorScheme))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
                    )
            }

            HStack {
                Spacer()

                Button {
                    submitNewAssessment(courseId: courseId)
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 13, weight: .bold))
                        Text("Adicionar em Prazos e Atividades")
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(
                        Capsule(style: .continuous)
                            .fill(accentColor)
                    )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(CampusTheme.appBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(accentColor.opacity(0.35), lineWidth: 1)
        )
    }

    private func assessmentRow(item: AssessmentItem, colorKey: DepartmentColorKey) -> some View {
        HStack(spacing: 14) {
            VStack(spacing: 1) {
                Text(item.dayNumber)
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(accentColor)

                Text(item.monthShort)
                    .font(.system(size: 10, weight: .bold, design: .rounded))
                    .tracking(0.8)
                    .foregroundStyle(accentColor)
            }
            .frame(width: 48, height: 46)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(CampusTheme.departmentSoftTint(for: colorKey, scheme: colorScheme))
            )

            VStack(alignment: .leading, spacing: 3) {
                Text(item.title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                    .strikethrough(item.completed, color: CampusTheme.mutedText(for: colorScheme))

                HStack(spacing: 6) {
                    Text(item.kind)
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundStyle(accentColor)

                    if !item.subtitle.isEmpty {
                        Text("•")
                            .foregroundStyle(CampusTheme.mutedText(for: colorScheme))
                        Text(item.subtitle)
                            .font(.system(size: 12, weight: .regular))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                    }
                }
            }

            Spacer()

            Button {
                withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                    toggleAssessment(item.id)
                }
            } label: {
                Image(systemName: item.completed ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(
                        item.completed
                            ? accentColor
                            : CampusTheme.mutedText(for: colorScheme)
                    )
            }
            .buttonStyle(.plain)
            .accessibilityLabel(item.completed ? "Desmarcar conclusão" : "Marcar avaliação como concluída")
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(CampusTheme.appBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
        )
        .contextMenu {
            Button {
                toggleAssessment(item.id)
            } label: {
                Label(
                    item.completed ? "Marcar como pendente" : "Marcar como concluída",
                    systemImage: item.completed ? "arrow.uturn.backward.circle" : "checkmark.circle"
                )
            }

            Button(role: .destructive) {
                deleteAssessment(item.id)
            } label: {
                Label("Excluir avaliação", systemImage: "trash")
            }
        }
    }

    private func toggleAssessment(_ assessmentId: String) {
        if let activeStore {
            _ = activeStore.toggleAssessmentCompleted(assessmentId: assessmentId)
        } else if let idx = localAssessments.firstIndex(where: { $0.id == assessmentId }) {
            localAssessments[idx].completed.toggle()
        }
    }

    private func deleteAssessment(_ assessmentId: String) {
        if let activeStore {
            activeStore.deleteAssessment(assessmentId: assessmentId)
        } else {
            localAssessments.removeAll { $0.id == assessmentId }
        }
    }

    private func submitNewAssessment(courseId: String) {
        let finalTitle = newAssessmentTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "\(newAssessmentKind) · \(resolvedDetail.summary.name)"
            : newAssessmentTitle

        if let activeStore {
            _ = activeStore.createAssessment(
                courseId: courseId,
                title: finalTitle,
                kind: newAssessmentKind,
                dueDate: newAssessmentDueDate,
                dueTime: newAssessmentDueTime
            )
        } else {
            let item = AssessmentItem(
                id: UUID().uuidString,
                courseId: courseId,
                dayNumber: "29",
                monthShort: "NOV",
                title: finalTitle,
                subtitle: "\(newAssessmentKind) · \(newAssessmentDueTime)",
                kind: newAssessmentKind,
                dueDate: "2026-11-29",
                dueTime: newAssessmentDueTime,
                completed: false
            )
            localAssessments.append(item)
        }

        newAssessmentTitle = ""
        withAnimation(.snappy) {
            isAddingAssessment = false
        }
    }

    // MARK: - Editable Notes ("Anotações" -> "Revisar integrais por partes.")

    private var notesSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Anotações")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Spacer()

                Text("Salvo automaticamente")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme))
            }

            TextEditor(text: $notesText)
                .font(.system(size: 15, weight: .regular))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                .scrollContentBackground(.hidden)
                .frame(minHeight: 96)
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(CampusTheme.appBackground(for: colorScheme))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
                )
        }
    }

    // MARK: - Destructive Course Removal ("Remover disciplina" + confirmation)

    private func deleteCourseSection(summary: DisciplineSummaryCard) -> some View {
        Button(role: .destructive) {
            isDeleteConfirmationPresented = true
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "trash.fill")
                    .font(.system(size: 14, weight: .semibold))
                Text("Remover disciplina")
                    .font(.system(size: 15, weight: .bold, design: .rounded))
            }
            .foregroundStyle(Color(hex: 0xDC2626))
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color(hex: 0xDC2626).opacity(colorScheme == .dark ? 0.18 : 0.10))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(Color(hex: 0xDC2626).opacity(0.32), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Remover disciplina \(summary.name)")
    }

    private func performDeleteCourse(courseId: String) {
        if let onDeleteCourse {
            onDeleteCourse()
        } else {
            activeStore?.deleteCourse(courseId: courseId)
        }
        onClose()
    }
}

#Preview("DisciplineDetailSheetView - Light") {
    let store = CampusStore()
    if let detail = store.getDisciplineDetail(disciplineId: "calc-ii") {
        DisciplineDetailSheetView(
            detail: detail,
            store: store,
            onUpdateNotes: { _ in },
            onClose: {}
        )
        .preferredColorScheme(.light)
    }
}

#Preview("DisciplineDetailSheetView - Dark") {
    let store = CampusStore()
    if let detail = store.getDisciplineDetail(disciplineId: "calc-ii") {
        DisciplineDetailSheetView(
            detail: detail,
            store: store,
            onUpdateNotes: { _ in },
            onClose: {}
        )
        .preferredColorScheme(.dark)
    }
}
