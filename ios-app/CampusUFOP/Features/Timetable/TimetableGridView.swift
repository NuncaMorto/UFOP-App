import SwiftUI

public struct TimetableGridView: View {
    @Bindable public var store: CampusStore
    public let onClose: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedFilter: String = "Todas"
    @State private var hideEmptyHours: Bool = true
    @State private var pendingTemplateConfirm: UfopCurriculumTemplate?
    @State private var selectedCell: TimetableSlotCell?
    @State private var activeCheckInDetail: ClassCheckInDetail?
    @State private var openedDisciplineDetail: DisciplineDetail?

    public init(store: CampusStore, onClose: @escaping () -> Void) {
        self.store = store
        self.onClose = onClose
    }

    private var resolvedAccent: CampusAccentColor { appearance?.accentColor ?? .green }
    private var hc: Bool { appearance?.accessibility.highContrastMode ?? false }
    private var minTouch: CGFloat { appearance?.accessibility.minTouchHeight ?? 48 }
    private var isHorizontalOrientation: Bool { store.timetableOrientation == "horizontal" }

    private var todayWeekdayShort: String? {
        let wd = Calendar.current.component(.weekday, from: Date())
        switch wd {
        case 2: return "SEG"
        case 3: return "TER"
        case 4: return "QUA"
        case 5: return "QUI"
        case 6: return "SEX"
        case 7: return "SÁB"
        default: return nil
        }
    }

    public var body: some View {
        let timetable = store.currentWeeklyTimetable
        let filteredSlots: [TimetableSlotCell] = {
            guard selectedFilter != "Todas" else { return timetable.slots }
            let matchingIds = Set(
                store.disciplines
                    .filter { $0.semester == store.selectedSemesterFilter && $0.department == selectedFilter }
                    .map(\.id)
            )
            return timetable.slots.filter { matchingIds.contains($0.disciplineId) }
        }()
        let displayedTimeRanges: [String] = {
            guard hideEmptyHours else { return timetable.timeRanges }
            let nonEmpty = timetable.timeRanges.filter { timeRange in
                filteredSlots.contains { $0.timeRange == timeRange }
            }
            return nonEmpty.isEmpty ? timetable.timeRanges : nonEmpty
        }()
        let legendEntries = store.timetableLegendEntries(for: filteredSlots)

        ZStack {
            LiquidGlassBackdrop(accent: resolvedAccent, highContrast: hc)

            VStack(spacing: 0) {
                topBar(timetable: timetable)
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 10)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 16) {
                        controlsBar(timetable: timetable)

                        if filteredSlots.isEmpty {
                            emptyTimetableCard
                        } else if isHorizontalOrientation {
                            horizontalGridCard(
                                weekdays: timetable.weekdays,
                                timeRanges: displayedTimeRanges,
                                slots: filteredSlots
                            )
                        } else {
                            verticalGridCard(
                                weekdays: timetable.weekdays,
                                timeRanges: displayedTimeRanges,
                                slots: filteredSlots
                            )
                        }

                        if !legendEntries.isEmpty {
                            legendSection(entries: legendEntries)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 4)
                    .padding(.bottom, 32)
                }
            }
        }
        .preferredColorScheme(appearance?.resolvedColorScheme)
        .sheet(item: $selectedCell) { cell in
            TimetableCellDetailSheet(
                cell: cell,
                store: store,
                onOpenDiscipline: {
                    let discId = cell.disciplineId
                    selectedCell = nil
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                        openedDisciplineDetail = store.getDisciplineDetail(disciplineId: discId)
                    }
                },
                onQuickCheckIn: {
                    if let detail = quickCheckInDetail(for: cell) {
                        selectedCell = nil
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                            activeCheckInDetail = detail
                        }
                    }
                },
                onClose: {
                    selectedCell = nil
                }
            )
            .presentationDetents([.medium])
        }
        .sheet(item: $activeCheckInDetail) { detail in
            ClassCheckInSheetView(
                detail: detail,
                onSave: { status, colorKey in
                    store.saveClassCheckIn(agendaItemId: detail.agendaItemId, status: status, colorKey: colorKey)
                    activeCheckInDetail = nil
                },
                onCancel: {
                    activeCheckInDetail = nil
                }
            )
        }
        .sheet(item: $openedDisciplineDetail) { detail in
            DisciplineDetailSheetView(
                detail: detail,
                store: store,
                onUpdateNotes: { notes in
                    store.updateDisciplineNotes(disciplineId: detail.summary.id, notes: notes)
                },
                onDeleteCourse: {
                    store.deleteCourse(courseId: detail.summary.id)
                },
                onClose: {
                    openedDisciplineDetail = nil
                }
            )
        }
        .confirmationDialog(
            "Aplicar modelo UFOP?",
            isPresented: Binding(
                get: { pendingTemplateConfirm != nil },
                set: { if !$0 { pendingTemplateConfirm = nil } }
            ),
            titleVisibility: .visible
        ) {
            if let template = pendingTemplateConfirm {
                Button("Aplicar \(template.code) · \(template.name)", role: .destructive) {
                    _ = store.applyUfopTemplate(templateId: template.id, semester: store.selectedSemesterFilter)
                    pendingTemplateConfirm = nil
                }
            }
            Button("Cancelar", role: .cancel) {
                pendingTemplateConfirm = nil
            }
        } message: {
            Text("Isso substituirá as disciplinas do semestre \(store.selectedSemesterFilter) pelo modelo oficial escolhido.")
        }
    }

    // MARK: - Top Bar

    private func topBar(timetable: WeeklyTimetable) -> some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text("SEMESTRE \(timetable.semester)")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .tracking(0.9)
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

                Text("Grade Horária")
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
            }

            Spacer()

            Button {
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
            .accessibilityLabel("Fechar grade")
        }
    }

    // MARK: - Controls (Filter, Templates, Orientation, Hide Empty)

    private func controlsBar(timetable: WeeklyTimetable) -> some View {
        let primary = resolvedAccent.primary(for: colorScheme)
        let templates = store.listUfopTemplates()

        return VStack(alignment: .leading, spacing: 10) {
            LiquidGlassGroup(spacing: 8) {
                HStack(spacing: 8) {
                    Menu {
                        ForEach(timetable.availableFilters, id: \.self) { filter in
                            Button {
                                selectedFilter = filter
                            } label: {
                                if selectedFilter == filter {
                                    Label(filter, systemImage: "checkmark")
                                } else {
                                    Text(filter)
                                }
                            }
                        }
                    } label: {
                        HStack(spacing: 5) {
                            Text(selectedFilter)
                                .font(.system(size: 12, weight: .semibold))
                            Image(systemName: "chevron.down")
                                .font(.system(size: 10, weight: .bold))
                        }
                        .foregroundStyle(CampusTheme.accessibleTextColor(primary, scheme: colorScheme))
                        .padding(.horizontal, 12)
                        .frame(minHeight: 38)
                        .liquidGlassCapsule(selected: false, accent: primary, interactive: true, highContrast: hc)
                    }

                    Menu {
                        ForEach(templates) { tpl in
                            Button {
                                pendingTemplateConfirm = tpl
                            } label: {
                                Text("\(tpl.code) · \(tpl.name)")
                            }
                        }
                    } label: {
                        HStack(spacing: 5) {
                            Image(systemName: "wand.and.stars")
                                .font(.system(size: 11, weight: .semibold))
                            Text("Modelos")
                                .font(.system(size: 12, weight: .semibold))
                        }
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                        .padding(.horizontal, 12)
                        .frame(minHeight: 38)
                        .liquidGlassCapsule(selected: false, accent: primary, interactive: true, highContrast: hc)
                    }

                    Spacer(minLength: 4)

                    // Vertical / Horizontal persisted toggle
                    HStack(spacing: 4) {
                        orientationButton(key: "vertical", icon: "rectangle.split.3x1", label: "Grade vertical")
                        orientationButton(key: "horizontal", icon: "rectangle.split.1x2", label: "Grade horizontal")
                    }
                    .padding(3)
                    .liquidGlassCapsule(selected: false, accent: primary, interactive: false, highContrast: hc)
                }
            }

            Toggle(isOn: $hideEmptyHours) {
                Text("Ocultar horários vazios")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
            }
            .tint(primary)
        }
    }

    private func orientationButton(key: String, icon: String, label: String) -> some View {
        let primary = resolvedAccent.primary(for: colorScheme)
        let isSelected = store.timetableOrientation == key

        return Button {
            withAnimation(.spring(response: 0.25, dampingFraction: 0.85)) {
                store.timetableOrientation = key
            }
        } label: {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(isSelected ? resolvedAccent.onPrimary(for: colorScheme) : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                .frame(width: 34, height: 32)
                .background(Capsule(style: .continuous).fill(isSelected ? primary : Color.clear))
        }
        .buttonStyle(.plain)
        .frame(minWidth: 40, minHeight: 36)
        .accessibilityLabel(label)
    }

    // MARK: - Vertical Grid (Time rows x Weekday columns)

    private func verticalGridCard(weekdays: [String], timeRanges: [String], slots: [TimetableSlotCell]) -> some View {
        let primary = resolvedAccent.primary(for: colorScheme)

        return ScrollView(.horizontal, showsIndicators: false) {
            VStack(spacing: 8) {
                HStack(spacing: 8) {
                    Text("HORÁRIO")
                        .font(.system(size: 10, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
                        .frame(width: 72, alignment: .leading)

                    ForEach(weekdays, id: \.self) { day in
                        let isToday = day == todayWeekdayShort
                        Text(isToday ? "\(day) •" : day)
                            .font(.system(size: 12, weight: .bold, design: .rounded))
                            .foregroundStyle(isToday ? resolvedAccent.onPrimary(for: colorScheme) : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .frame(width: 112, height: 32)
                            .liquidGlassCapsule(selected: isToday, accent: primary, interactive: false, highContrast: hc)
                    }
                }

                ForEach(timeRanges, id: \.self) { range in
                    HStack(spacing: 8) {
                        Text(range.replacingOccurrences(of: "–", with: "\n"))
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            .frame(width: 72, alignment: .leading)

                        ForEach(weekdays, id: \.self) { day in
                            let match = slots.first { $0.weekdayShort == day && $0.timeRange == range }
                            gridCellView(cell: match, isTodayColumn: day == todayWeekdayShort)
                                .frame(width: 112, height: 74)
                        }
                    }
                }
            }
            .padding(14)
        }
        .liquidGlassCard(cornerRadius: 20, tint: primary, highContrast: hc)
    }

    // MARK: - Horizontal Grid (Weekday rows x Time columns)

    private func horizontalGridCard(weekdays: [String], timeRanges: [String], slots: [TimetableSlotCell]) -> some View {
        let primary = resolvedAccent.primary(for: colorScheme)

        return ScrollView(.horizontal, showsIndicators: false) {
            VStack(spacing: 8) {
                HStack(spacing: 8) {
                    Text("DIA")
                        .font(.system(size: 10, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
                        .frame(width: 58, alignment: .leading)

                    ForEach(timeRanges, id: \.self) { range in
                        Text(range)
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .frame(width: 124, height: 32)
                            .liquidGlassCapsule(selected: false, accent: primary, interactive: false, highContrast: hc)
                    }
                }

                ForEach(weekdays, id: \.self) { day in
                    let isToday = day == todayWeekdayShort
                    HStack(spacing: 8) {
                        Text(isToday ? "\(day)\nHOJE" : day)
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .multilineTextAlignment(.center)
                            .foregroundStyle(isToday ? resolvedAccent.onPrimary(for: colorScheme) : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .frame(width: 58, height: 74)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(isToday ? primary : CampusTheme.secondarySurface(for: colorScheme, highContrast: hc))
                            )

                        ForEach(timeRanges, id: \.self) { range in
                            let match = slots.first { $0.weekdayShort == day && $0.timeRange == range }
                            gridCellView(cell: match, isTodayColumn: isToday)
                                .frame(width: 124, height: 74)
                        }
                    }
                }
            }
            .padding(14)
        }
        .liquidGlassCard(cornerRadius: 20, tint: primary, highContrast: hc)
    }

    @ViewBuilder
    private func gridCellView(cell: TimetableSlotCell?, isTodayColumn: Bool) -> some View {
        if let cell {
            let deptColor = CampusTheme.departmentColor(for: cell.colorKey)
            let isEad = store.isCourseEad(cell.disciplineId)
            let roomSub = isEad ? "EAD" : (cell.roomLabel.isEmpty ? "Sem sala" : cell.roomLabel)

            Button {
                selectedCell = cell
            } label: {
                VStack(alignment: .leading, spacing: 4) {
                    Text(cell.disciplineShortName)
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)

                    Spacer(minLength: 0)

                    Text(roomSub)
                        .font(.system(size: 10, weight: .semibold, design: .rounded))
                        .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))
                        .lineLimit(1)
                }
                .padding(8)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(CampusTheme.departmentSoftTint(for: cell.colorKey, scheme: colorScheme))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(deptColor.opacity(isTodayColumn ? 0.85 : 0.40), lineWidth: isTodayColumn ? 1.5 : 1)
                )
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(cell.disciplineShortName), \(cell.weekdayShort) \(cell.timeRange), \(roomSub)")
        } else {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(
                    isTodayColumn
                        ? resolvedAccent.primary(for: colorScheme).opacity(colorScheme == .dark ? 0.08 : 0.05)
                        : CampusTheme.secondarySurface(for: colorScheme, highContrast: hc).opacity(0.45)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(CampusTheme.cardBorder(for: colorScheme, highContrast: hc).opacity(0.5), lineWidth: 0.75)
                )
        }
    }

    private var emptyTimetableCard: some View {
        VStack(spacing: 8) {
            Image(systemName: "calendar.badge.exclamationmark")
                .font(.system(size: 28, weight: .light))
                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

            Text("Sem horários cadastrados neste filtro")
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

            Text("Adicione horários nas disciplinas ou escolha um modelo oficial UFOP acima.")
                .font(.system(size: 13))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .liquidGlassCard(cornerRadius: 20, highContrast: hc)
    }

    // MARK: - Legend Section

    private func legendSection(entries: [TimetableLegendEntry]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Legenda das disciplinas")
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

            VStack(spacing: 8) {
                ForEach(entries) { entry in
                    HStack(spacing: 10) {
                        Circle()
                            .fill(CampusTheme.departmentColor(for: entry.colorKey))
                            .frame(width: 10, height: 10)

                        Text(entry.title)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                        Spacer()

                        Text(entry.subtitle)
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .liquidGlassCard(cornerRadius: 14, highContrast: hc)
                }
            }
        }
    }

    private func quickCheckInDetail(for cell: TimetableSlotCell) -> ClassCheckInDetail? {
        let dayMap = ["SEG": 0, "TER": 1, "QUA": 2, "QUI": 3, "SEX": 4, "SÁB": 5]
        let offset = dayMap[cell.weekdayShort] ?? 0
        let weekDates = CampusStore.mondayToSundayDates(containing: Date())
        let targetIso = offset < weekDates.count ? CampusStore.isoString(from: weekDates[offset]) : CampusStore.todayIsoDateString()
        store.selectAgendaDate(targetIso)
        return store.getClassCheckInDetail(agendaItemId: "lecture-\(cell.id)@\(targetIso)")
            ?? store.getClassCheckInDetail(agendaItemId: "lecture-\(cell.id)")
    }
}

// MARK: - Cell Detail Modal Sheet

private struct TimetableCellDetailSheet: View {
    let cell: TimetableSlotCell
    let store: CampusStore
    let onOpenDiscipline: () -> Void
    let onQuickCheckIn: () -> Void
    let onClose: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let accent = appearance?.accentColor ?? .green
        let hc = appearance?.accessibility.highContrastMode ?? false
        let deptColor = CampusTheme.departmentColor(for: cell.colorKey)
        let detail = store.getDisciplineDetail(disciplineId: cell.disciplineId)
        let isEad = store.isCourseEad(cell.disciplineId)
        let slotMeta = store.courseScheduleSlots.first { $0.slotId == cell.id }
        let prof = detail?.summary.professor.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let roomDesc: String = {
            if isEad { return "A distância (EAD)" }
            if let slotMeta {
                let r = slotMeta.room.trimmingCharacters(in: .whitespacesAndNewlines)
                if r.isEmpty { return slotMeta.building.isEmpty ? "Local não informado" : slotMeta.building }
                return slotMeta.building.isEmpty ? r : "\(slotMeta.building) · \(r)"
            }
            return cell.roomLabel.isEmpty ? "Local não informado" : cell.roomLabel
        }()

        ZStack {
            LiquidGlassBackdrop(accent: accent, highContrast: hc)

            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text(detail?.summary.code ?? cell.weekdayShort)
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Capsule().fill(CampusTheme.departmentSoftTint(for: cell.colorKey, scheme: colorScheme)))

                    Spacer()

                    Button {
                        onClose()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .bold))
                            .frame(width: 38, height: 38)
                            .liquidGlassCapsule(selected: false, accent: deptColor, highContrast: hc)
                    }
                    .buttonStyle(.plain)
                }

                Text(detail?.summary.name ?? cell.disciplineShortName)
                    .font(.system(size: 23, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                VStack(alignment: .leading, spacing: 8) {
                    Label("\(cell.weekdayShort) · \(cell.timeRange)", systemImage: "clock.fill")
                        .font(.system(size: 14, weight: .semibold))
                    Label(roomDesc, systemImage: isEad ? "laptopcomputer" : "building.2.fill")
                        .font(.system(size: 14, weight: .medium))
                    if !prof.isEmpty {
                        Label(prof, systemImage: "person.fill")
                            .font(.system(size: 14, weight: .medium))
                    }
                }
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .liquidGlassCard(cornerRadius: 16, tint: deptColor, highContrast: hc)

                Spacer()

                VStack(spacing: 10) {
                    LiquidGlassPrimaryButton(tint: accent.primary(for: colorScheme), foreground: accent.onPrimary(for: colorScheme), minHeight: 48) {
                        onQuickCheckIn()
                    } label: {
                        Label("Fazer check-in desta aula", systemImage: "checkmark.circle.fill")
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                    }

                    Button {
                        onOpenDiscipline()
                    } label: {
                        Text("Abrir detalhes da disciplina")
                            .font(.system(size: 14, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .frame(maxWidth: .infinity, minHeight: 46)
                            .liquidGlassCapsule(selected: false, accent: deptColor, interactive: true, highContrast: hc)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(20)
        }
    }
}

#Preview("TimetableGridView") {
    TimetableGridView(store: CampusStore(), onClose: {})
}
