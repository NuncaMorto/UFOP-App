import SwiftUI

public struct TimetableGridView: View {
    @Bindable public var store: CampusStore
    public let onClose: () -> Void
    @Environment(\.colorScheme) private var colorScheme
    @State private var appliedTemplateFeedback: String?
    @State private var pendingTemplate: UfopCurriculumTemplate?
    @State private var isResetConfirmationPresented: Bool = false

    private let timeColumnWidth: CGFloat = 86
    private let dayColumnWidth: CGFloat = 108

    public init(store: CampusStore, onClose: @escaping () -> Void) {
        self.store = store
        self.onClose = onClose
    }

    private var availableTemplates: [UfopCurriculumTemplate] {
        store.listUfopTemplates()
    }

    private var activeTemplate: UfopCurriculumTemplate? {
        guard let activeId = store.activeTemplateId else { return nil }
        return availableTemplates.first(where: { $0.id == activeId })
    }

    public var body: some View {
        let timetable = store.currentWeeklyTimetable
        let legendEntries = store.timetableLegendEntries(for: timetable.slots)

        ZStack {
            CampusTheme.cardBackground(for: colorScheme)
                .ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 20) {
                    headerSection(timetable: timetable)

                    filterSelectorBar(timetable: timetable)

                    if let templateFeedbackBannerText = resolvedTemplateBannerText {
                        templateStatusBanner(text: templateFeedbackBannerText)
                    }

                    horizontalScheduleGrid(timetable: timetable)

                    departmentLegendSection(entries: legendEntries)
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 30)
            }
        }
        .confirmationDialog(
            pendingTemplate.map { "Aplicar template \($0.code)?" } ?? "Aplicar template oficial UFOP?",
            isPresented: Binding(
                get: { pendingTemplate != nil },
                set: { isPresented in
                    if !isPresented {
                        pendingTemplate = nil
                    }
                }
            ),
            titleVisibility: .visible,
            presenting: pendingTemplate
        ) { template in
            Button("Aplicar \(template.code)", role: .destructive) {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.82)) {
                    let count = store.applyUfopTemplate(
                        templateId: template.id,
                        semester: timetable.semester
                    )
                    appliedTemplateFeedback = "Template \(template.code) aplicado · \(count) disciplinas em \(timetable.semester)"
                }
                pendingTemplate = nil
            }
            Button("Cancelar", role: .cancel) {
                pendingTemplate = nil
            }
        } message: { template in
            Text("Aplicar \(template.name) substituirá as disciplinas, marcações de check-in e avaliações atuais do semestre \(timetable.semester).")
        }
        .confirmationDialog(
            "Restaurar grade padrão 2026.2?",
            isPresented: $isResetConfirmationPresented,
            titleVisibility: .visible
        ) {
            Button("Restaurar grade padrão 2026.2", role: .destructive) {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.82)) {
                    let restoredCount = store.resetSemesterToDefaultSeed(semester: "2026.2")
                    appliedTemplateFeedback = "Grade padrão 2026.2 restaurada · \(restoredCount) disciplinas"
                }
            }
            Button("Cancelar", role: .cancel) {}
        } message: {
            Text("Esta ação restaurará as 4 disciplinas padrão de 2026.2 (Cálculo II, Algoritmos, Álgebra Linear e Física I), seus horários, sessões e avaliações.")
        }
    }

    private var resolvedTemplateBannerText: String? {
        if let appliedTemplateFeedback {
            return appliedTemplateFeedback
        }
        if let activeTemplate {
            return "\(activeTemplate.code) · \(activeTemplate.name) (\(activeTemplate.campusName))"
        }
        return nil
    }

    // MARK: - Header ("Grade de horários", "Semana de aulas · 2026.2")

    private func headerSection(timetable: WeeklyTimetable) -> some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 5) {
                Text(timetable.title)
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Text(timetable.subtitle)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
            }

            Spacer()

            Button {
                onClose()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                    .frame(width: 34, height: 34)
                    .background(
                        Circle()
                            .fill(CampusTheme.secondarySurface(for: colorScheme))
                    )
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Fechar Grade de horários")
        }
    }

    // MARK: - Filter & UFOP Template Selector ("Minha grade" + BCC · DECOM / ENGC · DECSI / CICLO BÁSICO · EM)

    private func filterSelectorBar(timetable: WeeklyTimetable) -> some View {
        let pickerLabel: String = {
            if timetable.selectedFilter == "Minha grade", let activeTemplate {
                return activeTemplate.code
            }
            return timetable.selectedFilter
        }()

        return HStack(spacing: 10) {
            Menu {
                Section("Filtrar grade") {
                    ForEach(timetable.availableFilters, id: \.self) { option in
                        Button {
                            withAnimation(.snappy) {
                                store.selectedTimetableFilter = option
                            }
                        } label: {
                            HStack {
                                Text(option)
                                if timetable.selectedFilter == option {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                }

                Section("Templates oficiais UFOP") {
                    ForEach(availableTemplates) { template in
                        Button {
                            pendingTemplate = template
                        } label: {
                            HStack {
                                Text("\(template.code) · \(template.name)")
                                if store.activeTemplateId == template.id {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                }

                Section {
                    Button {
                        isResetConfirmationPresented = true
                    } label: {
                        Label("Restaurar grade padrão 2026.2", systemImage: "arrow.counterclockwise")
                    }
                }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "square.grid.3x2.fill")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CampusTheme.mathBlue)

                    Text(pickerLabel)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                        .lineLimit(1)

                    Image(systemName: "chevron.down")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(
                    Capsule(style: .continuous)
                        .fill(CampusTheme.appBackground(for: colorScheme))
                )
                .overlay(
                    Capsule(style: .continuous)
                        .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
                )
            }
            .accessibilityLabel("Minha grade e templates UFOP")

            Spacer()

            Text("Deslize para os lados →")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(CampusTheme.mutedText(for: colorScheme))
        }
    }

    private func templateStatusBanner(text: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "graduationcap.fill")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CampusTheme.mathBlue)

            Text(text)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 4)

            if appliedTemplateFeedback != nil {
                Button {
                    withAnimation(.snappy) {
                        appliedTemplateFeedback = nil
                    }
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(CampusTheme.selectedPillBackground(for: colorScheme).opacity(0.7))
        )
    }

    // MARK: - Horizontal Scrollable Grid (SEG..SÁB × 08:00–09:40, 10:00–11:40, 14:00–15:00)

    private func horizontalScheduleGrid(timetable: WeeklyTimetable) -> some View {
        ScrollView(.horizontal, showsIndicators: true) {
            VStack(alignment: .leading, spacing: 10) {
                // Header Row: Horário + SEG .. SÁB
                HStack(spacing: 8) {
                    Text("HORÁRIO")
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .tracking(0.8)
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme))
                        .frame(width: timeColumnWidth, alignment: .leading)

                    ForEach(timetable.weekdays, id: \.self) { weekday in
                        let isHighlighted = weekday == "TER"
                        Text(weekday)
                            .font(.system(size: 12, weight: .bold, design: .rounded))
                            .foregroundStyle(
                                isHighlighted
                                    ? CampusTheme.selectedPillText(for: colorScheme)
                                    : CampusTheme.secondaryText(for: colorScheme)
                            )
                            .frame(width: dayColumnWidth, height: 34)
                            .background(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(
                                        isHighlighted
                                            ? CampusTheme.selectedPillBackground(for: colorScheme)
                                            : CampusTheme.appBackground(for: colorScheme)
                                    )
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .strokeBorder(
                                        CampusTheme.cardBorder(for: colorScheme),
                                        lineWidth: 1
                                    )
                            )
                    }
                }

                // Time Range Rows: 08:00–09:40, 10:00–11:40, 14:00–15:00
                ForEach(timetable.timeRanges, id: \.self) { timeRange in
                    HStack(alignment: .center, spacing: 8) {
                        timeRangeLabelCell(timeRange: timeRange)

                        ForEach(timetable.weekdays, id: \.self) { weekday in
                            if let slot = timetable.slot(forWeekday: weekday, timeRange: timeRange) {
                                occupiedSlotCell(slot: slot)
                            } else {
                                emptySlotCell()
                            }
                        }
                    }
                }
            }
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(CampusTheme.appBackground(for: colorScheme))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
            )
        }
    }

    private func timeRangeLabelCell(timeRange: String) -> some View {
        let parts = timeRange.components(separatedBy: "–")
        let start = parts.first ?? timeRange
        let end = parts.count > 1 ? parts[1] : ""

        return VStack(alignment: .leading, spacing: 2) {
            Text(start)
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

            if !end.isEmpty {
                Text("às \(end)")
                    .font(.system(size: 11, weight: .medium, design: .rounded))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme))
            }
        }
        .frame(width: timeColumnWidth, height: 74, alignment: .leading)
    }

    private func occupiedSlotCell(slot: TimetableSlotCell) -> some View {
        let accent = CampusTheme.departmentColor(for: slot.colorKey)

        return HStack(spacing: 0) {
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .fill(accent)
                .frame(width: 4)
                .padding(.vertical, 8)
                .padding(.leading, 6)

            VStack(alignment: .leading, spacing: 4) {
                Text(slot.disciplineShortName)
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                    .lineLimit(2)

                Text(slot.roomLabel)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(accent)
                    .lineLimit(1)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(width: dayColumnWidth, height: 74)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(CampusTheme.cardBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(accent.opacity(0.35), lineWidth: 1)
        )
    }

    private func emptySlotCell() -> some View {
        RoundedRectangle(cornerRadius: 12, style: .continuous)
            .fill(CampusTheme.secondarySurface(for: colorScheme).opacity(0.55))
            .frame(width: dayColumnWidth, height: 74)
            .overlay(
                Text("—")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme).opacity(0.45))
            )
    }

    // MARK: - Bottom Department Legend

    private func departmentLegendSection(entries: [TimetableLegendEntry]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Legenda de departamentos")
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

            LazyVGrid(
                columns: [
                    GridItem(.flexible(), spacing: 10),
                    GridItem(.flexible(), spacing: 10)
                ],
                spacing: 10
            ) {
                ForEach(entries) { entry in
                    HStack(spacing: 10) {
                        Circle()
                            .fill(CampusTheme.departmentColor(for: entry.colorKey))
                            .frame(width: 12, height: 12)

                        VStack(alignment: .leading, spacing: 1) {
                            Text(entry.title)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                                .lineLimit(1)

                            Text(entry.subtitle)
                                .font(.system(size: 11, weight: .regular))
                                .foregroundStyle(CampusTheme.mutedText(for: colorScheme))
                                .lineLimit(1)
                        }

                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(CampusTheme.appBackground(for: colorScheme))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
                    )
                }
            }
        }
    }
}

#Preview("TimetableGridView - Light") {
    TimetableGridView(store: CampusStore(), onClose: {})
        .preferredColorScheme(.light)
}

#Preview("TimetableGridView - Dark") {
    TimetableGridView(store: CampusStore(), onClose: {})
        .preferredColorScheme(.dark)
}
