import SwiftUI
import UniformTypeIdentifiers

public struct DisciplineDetailSheetView: View {
    public let detail: DisciplineDetail
    public let store: CampusStore?
    public let onUpdateNotes: (String) -> Void
    public let onDeleteCourse: (() -> Void)?
    public let onClose: () -> Void

    @Environment(CampusStore.self) private var environmentStore: CampusStore?
    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    @State private var notesText: String
    @State private var localAssessments: [AssessmentItem]
    @State private var localSlots: [CourseScheduleSlotItem] = []

    @State private var isAddingAssessment: Bool = false
    @State private var newAssessmentTitle: String = ""
    @State private var newAssessmentKind: String = "Trabalho"
    @State private var newAssessmentDueDate: Date = Date()
    @State private var newAssessmentDueTime: String = "18:00"

    @State private var isAddingSlot: Bool = false
    @State private var newSlotDayOfWeek: Int = 5
    @State private var newSlotStartTime: String = "08:00"
    @State private var newSlotEndTime: String = "09:40"
    @State private var newSlotBuilding: String = ""
    @State private var newSlotRoom: String = ""
    @State private var newSlotShortLocation: String = ""
    @State private var slotConflictError: String?

    @State private var showSyllabusImporter: Bool = false
    @State private var syllabusReportMessage: String?
    @State private var syllabusWarningMessage: String?
    @State private var syllabusErrorMessage: String?

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
        ("13:30", "15:10"),
        ("15:20", "17:00"),
        ("19:00", "20:40"),
        ("20:50", "22:30")
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

    private var activeStore: CampusStore? { store ?? environmentStore }
    private var resolvedAccent: CampusAccentColor { appearance?.accentColor ?? .green }
    private var hc: Bool { appearance?.accessibility.highContrastMode ?? false }
    private var minTouch: CGFloat { appearance?.accessibility.minTouchHeight ?? 48 }

    private var resolvedDetail: DisciplineDetail {
        if let activeStore, let live = activeStore.getDisciplineDetail(disciplineId: detail.summary.id) {
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

    private var deptColor: Color {
        CampusTheme.departmentColor(for: resolvedDetail.summary.colorKey)
    }

    private var isEadCourse: Bool {
        activeStore?.isCourseEad(resolvedDetail.summary.id) ?? false
    }

    public var body: some View {
        let currentDetail = resolvedDetail

        ZStack {
            LiquidGlassBackdrop(accent: resolvedAccent, highContrast: hc)

            VStack(spacing: 0) {
                topBar(summary: currentDetail.summary)
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 10)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {
                        headerSection(summary: currentDetail.summary)
                        absenceReportCard(summary: currentDetail.summary)
                        eadAndSyllabusSection(currentDetail: currentDetail)
                        absenceHistorySection(currentDetail: currentDetail)
                        weeklySlotsSection(currentDetail: currentDetail)
                        assessmentsSection(currentDetail: currentDetail)
                        notesSection
                        deleteCourseSection(summary: currentDetail.summary)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 6)
                    .padding(.bottom, 32)
                }
            }
        }
        .preferredColorScheme(appearance?.resolvedColorScheme)
        .onChange(of: notesText) { _, newValue in
            onUpdateNotes(newValue)
        }
        .fileImporter(
            isPresented: $showSyllabusImporter,
            allowedContentTypes: [.pdf],
            allowsMultipleSelection: false
        ) { result in
            handleSyllabusImport(result: result, currentDetail: resolvedDetail)
        }
        .confirmationDialog(
            "Remover disciplina?",
            isPresented: $isDeleteConfirmationPresented,
            titleVisibility: .visible
        ) {
            Button("Remover \(currentDetail.summary.name)", role: .destructive) {
                performDeleteCourse(courseId: currentDetail.summary.id)
            }
            Button("Cancelar", role: .cancel) {}
        } message: {
            Text("Esta ação remove a disciplina, seus horários na grade, aulas na agenda e prazos vinculados em Atividades.")
        }
    }

    // MARK: - Top Bar

    private func topBar(summary: DisciplineSummaryCard) -> some View {
        HStack(spacing: 8) {
            Text(summary.code)
                .font(.system(size: 12, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    Capsule(style: .continuous)
                        .fill(CampusTheme.departmentSoftTint(for: summary.colorKey, scheme: colorScheme))
                )

            Text(summary.semester)
                .font(.system(size: 12, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .liquidGlassCapsule(selected: false, accent: deptColor, interactive: false, highContrast: hc)

            if isEadCourse {
                Text("EAD")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.mathBlue)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 6)
                    .background(
                        Capsule(style: .continuous)
                            .fill(CampusTheme.mathBlue.opacity(colorScheme == .dark ? 0.22 : 0.14))
                    )
            }

            Spacer()

            Button {
                onUpdateNotes(notesText)
                onClose()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    .frame(width: 40, height: 40)
                    .liquidGlassCapsule(selected: false, accent: resolvedAccent.primary(for: colorScheme), highContrast: hc)
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
            .accessibilityLabel("Fechar")
        }
    }

    // MARK: - Header

    private func headerSection(summary: DisciplineSummaryCard) -> some View {
        let cleanProfessor = summary.professor.trimmingCharacters(in: .whitespacesAndNewlines)

        return VStack(alignment: .leading, spacing: 6) {
            Text(summary.name)
                .font(.system(size: 27, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

            if !cleanProfessor.isEmpty {
                Label(cleanProfessor, systemImage: "person.fill")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
            }

            Text("\(summary.workloadHours)h · Limite de \(summary.maxAbsences) faltas (25%)")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
        }
    }

    // MARK: - Absence Report Card

    private func absenceReportCard(summary: DisciplineSummaryCard) -> some View {
        let riskLevel = CampusTheme.semanticAbsenceRiskLevel(currentAbsences: summary.currentAbsences, maxAbsences: summary.maxAbsences)
        let riskColor = CampusTheme.semanticAbsenceRiskColor(currentAbsences: summary.currentAbsences, maxAbsences: summary.maxAbsences, scheme: colorScheme)
        let barColor = riskLevel > 0 ? riskColor : deptColor
        let progress = summary.maxAbsences > 0 ? min(1.0, max(0.0, Double(summary.currentAbsences) / Double(summary.maxAbsences))) : 0
        let remaining = max(0, summary.maxAbsences - summary.currentAbsences)

        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Controle de Faltas")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

                    HStack(alignment: .firstTextBaseline, spacing: 3) {
                        Text("\(summary.currentAbsences)")
                            .font(.system(size: 30, weight: .bold, design: .rounded))
                            .foregroundStyle(riskLevel > 0 ? riskColor : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                        Text("/ \(summary.maxAbsences) faltas")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    }
                }

                Spacer()

                HStack(spacing: 5) {
                    Image(systemName: riskLevel > 0
                        ? CampusTheme.semanticAbsenceRiskIcon(currentAbsences: summary.currentAbsences, maxAbsences: summary.maxAbsences)
                        : "checkmark.shield.fill")
                        .font(.system(size: 12, weight: .bold))
                    Text(riskLevel > 0
                        ? CampusTheme.semanticAbsenceRiskLabel(currentAbsences: summary.currentAbsences, maxAbsences: summary.maxAbsences)
                        : "Em dia")
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                }
                .foregroundStyle(riskLevel > 0 ? riskColor : CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))
                .padding(.horizontal, 11)
                .padding(.vertical, 6)
                .background(
                    Capsule(style: .continuous)
                        .fill((riskLevel > 0 ? riskColor : deptColor).opacity(colorScheme == .dark ? 0.22 : 0.14))
                )
            }

            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule(style: .continuous)
                        .fill(CampusTheme.cardBorder(for: colorScheme, highContrast: hc))
                        .frame(height: 8)

                    if progress > 0 {
                        Capsule(style: .continuous)
                            .fill(barColor)
                            .frame(width: max(12, proxy.size.width * progress), height: 8)
                    }
                }
            }
            .frame(height: 8)

            HStack {
                Text("Margem restante: \(remaining) falta(s)")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

                Spacer()

                Text("Canceladas: \(summary.cancelledClasses)")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
            }
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 20, tint: barColor, highContrast: hc)
    }

    // MARK: - EAD Modalidade & Plano de Ensino (PDF)

    private func eadAndSyllabusSection(currentDetail: DisciplineDetail) -> some View {
        let primary = resolvedAccent.primary(for: colorScheme)
        let eadBinding = Binding<Bool>(
            get: { activeStore?.isCourseEad(currentDetail.summary.id) ?? false },
            set: { newValue in
                activeStore?.setCourseEad(currentDetail.summary.id, isEad: newValue)
            }
        )

        return VStack(alignment: .leading, spacing: 12) {
            Toggle(isOn: eadBinding) {
                VStack(alignment: .leading, spacing: 2) {
                    Label("Disciplina a distância (EAD)", systemImage: "laptopcomputer")
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                    Text("Exibe \"A distância (EAD)\" em vez de prédio e sala.")
                        .font(.system(size: 12))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                }
            }
            .tint(primary)
            .disabled(activeStore == nil)

            Divider()
                .overlay(CampusTheme.cardBorder(for: colorScheme, highContrast: hc))

            VStack(alignment: .leading, spacing: 8) {
                Text("Plano de Ensino (PDF)")
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                Text("Importe o PDF do plano da UFOP para cadastrar provas, seminários e trabalhos automaticamente. O arquivo é processado em memória e excluído.")
                    .font(.system(size: 12))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

                Button {
                    syllabusErrorMessage = nil
                    syllabusWarningMessage = nil
                    syllabusReportMessage = nil
                    showSyllabusImporter = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "doc.text.magnifyingglass")
                            .font(.system(size: 14, weight: .bold))
                        Text("Importar Plano de Ensino (PDF)")
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(CampusTheme.accessibleTextColor(primary, scheme: colorScheme))
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .liquidGlassCapsule(selected: false, accent: primary, interactive: true, highContrast: hc)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
                .disabled(activeStore == nil)
            }

            if let syllabusErrorMessage {
                feedbackBanner(text: syllabusErrorMessage, color: Color(hex: 0xDC2626), icon: "xmark.octagon.fill")
            }

            if let syllabusWarningMessage {
                feedbackBanner(text: syllabusWarningMessage, color: Color(hex: 0xD97706), icon: "exclamationmark.triangle.fill")
            }

            if let syllabusReportMessage {
                feedbackBanner(text: syllabusReportMessage, color: primary, icon: "checkmark.seal.fill")
            }
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 18, tint: primary, highContrast: hc)
    }

    private func feedbackBanner(text: String, color: Color, icon: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(color)
                .padding(.top, 2)

            Text(text)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(color.opacity(colorScheme == .dark ? 0.20 : 0.12))
        )
    }

    private func handleSyllabusImport(result: Result<[URL], Error>, currentDetail: DisciplineDetail) {
        switch result {
        case .failure(let error):
            syllabusErrorMessage = error.localizedDescription
        case .success(let urls):
            guard let url = urls.first else { return }
            do {
                let text = try SecurePdfProcessor.extractTextAndDeleteTemp(from: url)
                let report = UfopPdfParsers.parseSyllabus(
                    text: text,
                    targetCourseCode: currentDetail.summary.code,
                    targetCourseName: currentDetail.summary.name,
                    semester: currentDetail.summary.semester
                )
                if let error = report.errorMessage {
                    syllabusErrorMessage = error
                    return
                }
                let added = activeStore?.applySyllabusImport(report, forCourseId: currentDetail.summary.id) ?? 0
                syllabusWarningMessage = report.warningMessage
                syllabusReportMessage = added > 0
                    ? "Plano importado: \(added) avaliação(ões) adicionada(s) em Prazos e Atividades."
                    : "Plano verificado sem novas datas/avaliações para adicionar."
            } catch {
                syllabusErrorMessage = error.localizedDescription
            }
        }
    }

    // MARK: - Absence History

    private func absenceHistorySection(currentDetail: DisciplineDetail) -> some View {
        let history = resolvedAbsenceHistory

        return VStack(alignment: .leading, spacing: 10) {
            Text("Histórico de faltas e cancelamentos")
                .font(.system(size: 17, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

            if history.isEmpty {
                Text("As marcações salvas no check-in da Agenda para \(currentDetail.summary.name) aparecem aqui.")
                    .font(.system(size: 13))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                    .liquidGlassCard(cornerRadius: 16, highContrast: hc)
            } else {
                VStack(spacing: 8) {
                    ForEach(history) { entry in
                        absenceHistoryRow(entry: entry)
                    }
                }
            }
        }
    }

    private func absenceHistoryRow(entry: AbsenceHistoryEntry) -> some View {
        let isAbsent = entry.status == .absent
        let badgeForeground = isAbsent ? Color(hex: 0xDC2626) : CampusTheme.secondaryText(for: colorScheme, highContrast: hc)
        let badgeBackground = isAbsent
            ? Color(hex: 0xDC2626).opacity(colorScheme == .dark ? 0.22 : 0.12)
            : CampusTheme.secondarySurface(for: colorScheme, highContrast: hc)

        return HStack(spacing: 12) {
            Image(systemName: isAbsent ? "xmark.circle.fill" : "slash.circle.fill")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(badgeForeground)

            VStack(alignment: .leading, spacing: 2) {
                Text(entry.formattedDate)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                Text(entry.timeRangeLabel)
                    .font(.system(size: 12))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
            }

            Spacer(minLength: 6)

            Text(entry.badgeTitle)
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .foregroundStyle(badgeForeground)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Capsule(style: .continuous).fill(badgeBackground))
                .lineLimit(1)
        }
        .padding(12)
        .liquidGlassCard(cornerRadius: 16, highContrast: hc)
        .accessibilityElement(children: .combine)
    }

    // MARK: - Weekly Schedule Slots

    private func weeklySlotsSection(currentDetail: DisciplineDetail) -> some View {
        let slots = resolvedSlots

        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center) {
                Text("Horários semanais")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

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
                            .fill(CampusTheme.departmentSoftTint(for: currentDetail.summary.colorKey, scheme: colorScheme))
                    )
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
                .accessibilityLabel("Adicionar horário semanal")
            }

            if isAddingSlot {
                addScheduleSlotCard(courseId: currentDetail.summary.id, colorKey: currentDetail.summary.colorKey)
            }

            if slots.isEmpty {
                Text("Nenhum horário semanal cadastrado para esta disciplina.")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                    .liquidGlassCard(cornerRadius: 16, highContrast: hc)
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
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

            if let slotConflictError {
                feedbackBanner(text: slotConflictError, color: Color(hex: 0xDC2626), icon: "exclamationmark.triangle.fill")
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
                            .foregroundStyle(isSelected ? .white : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            .frame(maxWidth: .infinity, minHeight: 36)
                            .liquidGlassCapsule(selected: isSelected, accent: deptColor, interactive: true, highContrast: hc)
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
                                .foregroundStyle(isSelected ? .white : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .liquidGlassCapsule(selected: isSelected, accent: deptColor, interactive: true, highContrast: hc)
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

            if !isEadCourse {
                HStack(spacing: 10) {
                    slotTextField(placeholder: "Prédio (ex.: ICEB)", text: $newSlotBuilding)
                    slotTextField(placeholder: "Sala (opcional)", text: $newSlotRoom)
                }

                slotTextField(placeholder: "Rótulo curto na grade (opcional)", text: $newSlotShortLocation)
            }

            LiquidGlassPrimaryButton(tint: deptColor, foreground: .white, minHeight: 44) {
                submitNewSlot(courseId: courseId)
            } label: {
                Label("Salvar horário na grade", systemImage: "calendar.badge.plus")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
            }
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: 16, tint: deptColor, highContrast: hc)
    }

    private func slotTextField(placeholder: String, text: Binding<String>) -> some View {
        TextField(placeholder, text: text)
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: hc))
            )
    }

    private func scheduleSlotRow(slot: CourseScheduleSlotItem, colorKey: DepartmentColorKey) -> some View {
        let cleanRoom = slot.room.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanBuilding = slot.building.trimmingCharacters(in: .whitespacesAndNewlines)
        let locationLabel: String = {
            if isEadCourse { return "A distância (EAD)" }
            if cleanBuilding.isEmpty && cleanRoom.isEmpty { return "Sala não informada" }
            if cleanRoom.isEmpty { return cleanBuilding }
            if cleanBuilding.isEmpty { return cleanRoom }
            return "\(cleanBuilding) · \(cleanRoom)"
        }()

        return HStack(spacing: 12) {
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
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                Text(locationLabel)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
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
        .liquidGlassCard(cornerRadius: 16, highContrast: hc)
    }

    private func submitNewSlot(courseId: String) {
        slotConflictError = nil
        let resolvedBuilding = isEadCourse ? "A distância (EAD)" : newSlotBuilding.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedRoom = isEadCourse ? "" : newSlotRoom.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedShort = isEadCourse
            ? "EAD"
            : (newSlotShortLocation.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? (resolvedRoom.isEmpty ? resolvedBuilding : resolvedRoom)
                : newSlotShortLocation.trimmingCharacters(in: .whitespacesAndNewlines))

        if let activeStore {
            do {
                _ = try activeStore.addScheduleSlot(
                    courseId: courseId,
                    dayOfWeek: newSlotDayOfWeek,
                    startTime: newSlotStartTime,
                    endTime: newSlotEndTime,
                    building: resolvedBuilding,
                    room: resolvedRoom,
                    shortLocation: resolvedShort
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
                building: resolvedBuilding,
                room: resolvedRoom,
                shortLocation: resolvedShort
            )
            localSlots.append(slot)
            withAnimation(.snappy) { isAddingSlot = false }
        }
    }

    private func deleteSlot(_ slotId: String) {
        if let activeStore {
            activeStore.deleteScheduleSlot(slotId: slotId)
        } else {
            localSlots.removeAll { $0.slotId == slotId }
        }
    }

    // MARK: - Assessments

    private func assessmentsSection(currentDetail: DisciplineDetail) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center) {
                Text("Prazos e avaliações")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

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
                    .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(
                        Capsule(style: .continuous)
                            .fill(CampusTheme.departmentSoftTint(for: currentDetail.summary.colorKey, scheme: colorScheme))
                    )
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
                .accessibilityLabel("Adicionar avaliação")
            }

            if isAddingAssessment {
                addAssessmentCard(courseId: currentDetail.summary.id)
            }

            if currentDetail.assessments.isEmpty {
                Text("Nenhuma avaliação registrada. Use \"Novo prazo\" ou importe o Plano de Ensino.")
                    .font(.system(size: 13))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                    .liquidGlassCard(cornerRadius: 16, highContrast: hc)
            } else {
                VStack(spacing: 10) {
                    ForEach(currentDetail.assessments) { item in
                        assessmentRow(item: item, colorKey: currentDetail.summary.colorKey)
                    }
                }
            }
        }
    }

    private func addAssessmentCard(courseId: String) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Nova avaliação (\(resolvedDetail.summary.name))")
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

            HStack(spacing: 8) {
                ForEach(assessmentKinds, id: \.self) { kind in
                    let isSelected = newAssessmentKind == kind
                    Button {
                        newAssessmentKind = kind
                    } label: {
                        Text(kind)
                            .font(.system(size: 12, weight: .semibold, design: .rounded))
                            .foregroundStyle(isSelected ? .white : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .liquidGlassCapsule(selected: isSelected, accent: deptColor, interactive: true, highContrast: hc)
                    }
                    .buttonStyle(.plain)
                }
            }

            slotTextField(placeholder: "Título (ex.: Prova 1, Seminário)", text: $newAssessmentTitle)

            HStack(spacing: 10) {
                DatePicker("Data", selection: $newAssessmentDueDate, displayedComponents: .date)
                    .labelsHidden()
                    .environment(\.locale, Locale(identifier: "pt_BR"))

                slotTextField(placeholder: "Horário (18:00)", text: $newAssessmentDueTime)
            }

            LiquidGlassPrimaryButton(tint: deptColor, foreground: .white, minHeight: 44) {
                submitNewAssessment(courseId: courseId)
            } label: {
                Label("Adicionar em Prazos e Atividades", systemImage: "checkmark.circle.fill")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
            }
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: 16, tint: deptColor, highContrast: hc)
    }

    private func assessmentRow(item: AssessmentItem, colorKey: DepartmentColorKey) -> some View {
        HStack(spacing: 14) {
            VStack(spacing: 1) {
                Text(item.dayNumber)
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))

                Text(item.monthShort)
                    .font(.system(size: 10, weight: .bold, design: .rounded))
                    .tracking(0.8)
                    .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))
            }
            .frame(width: 48, height: 46)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(CampusTheme.departmentSoftTint(for: colorKey, scheme: colorScheme))
            )

            VStack(alignment: .leading, spacing: 3) {
                Text(item.title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    .strikethrough(item.completed, color: CampusTheme.mutedText(for: colorScheme, highContrast: hc))

                HStack(spacing: 6) {
                    Text(item.kind)
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))

                    if !item.subtitle.isEmpty {
                        Text("•")
                            .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
                        Text(item.subtitle)
                            .font(.system(size: 12))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
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
                    .foregroundStyle(item.completed ? deptColor : CampusTheme.mutedText(for: colorScheme, highContrast: hc))
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
            .accessibilityLabel(item.completed ? "Desmarcar conclusão" : "Marcar avaliação como concluída")
        }
        .padding(12)
        .liquidGlassCard(cornerRadius: 16, highContrast: hc)
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
        let isoDate = CampusStore.isoString(from: newAssessmentDueDate)

        if let activeStore {
            _ = activeStore.createAssessment(
                courseId: courseId,
                title: finalTitle,
                kind: newAssessmentKind,
                dueDate: isoDate,
                dueTime: newAssessmentDueTime
            )
        } else {
            let item = AssessmentItem(
                id: UUID().uuidString,
                courseId: courseId,
                dayNumber: CampusStore.dayNumberString(from: newAssessmentDueDate),
                monthShort: "NOV",
                title: finalTitle,
                subtitle: "\(newAssessmentKind) · \(newAssessmentDueTime)",
                kind: newAssessmentKind,
                dueDate: isoDate,
                dueTime: newAssessmentDueTime,
                completed: false
            )
            localAssessments.append(item)
        }

        newAssessmentTitle = ""
        withAnimation(.snappy) { isAddingAssessment = false }
    }

    // MARK: - Editable Notes

    private var notesSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Anotações")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                Spacer()

                Text("Salvo automaticamente")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
            }

            TextEditor(text: $notesText)
                .font(.system(size: 15))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                .scrollContentBackground(.hidden)
                .frame(minHeight: 96)
                .padding(12)
                .liquidGlassCard(cornerRadius: 16, highContrast: hc)
        }
    }

    // MARK: - Destructive Course Removal

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
            .frame(maxWidth: .infinity, minHeight: max(48, minTouch))
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Color(hex: 0xDC2626).opacity(colorScheme == .dark ? 0.18 : 0.10))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
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
