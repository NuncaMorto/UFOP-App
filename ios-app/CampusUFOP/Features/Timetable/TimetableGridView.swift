import SwiftUI
import UniformTypeIdentifiers

public struct TimetableGridView: View {
    @Bindable public var store: CampusStore
    public let onOpenLocationInMap: ((CampusMapMeetingRequest) -> Void)?
    public let onClose: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var selectedFilter: String = "Todas"
    @State private var hideEmptyHours: Bool = true
    @State private var pendingTemplateConfirm: UfopCurriculumTemplate?
    @State private var selectedCell: TimetableSlotCell?
    @State private var selectedFriendCell: TimetableSlotCell?
    @State private var activeCheckInDetail: ClassCheckInDetail?
    @State private var openedDisciplineDetail: DisciplineDetail?
    @State private var qrSheetMode: GradeQrMode?
    @State private var pendingIncomingGrade: IncomingGradePreview?
    @State private var showFileImporter: Bool = false
    @State private var importErrorMessage: String?
    @State private var pendingRemoveFriend: FriendSchedule?

    public init(
        store: CampusStore,
        onOpenLocationInMap: ((CampusMapMeetingRequest) -> Void)? = nil,
        onClose: @escaping () -> Void
    ) {
        self.store = store
        self.onOpenLocationInMap = onOpenLocationInMap
        self.onClose = onClose
    }

    private var isRegular: Bool { horizontalSizeClass == .regular }
    private var resolvedAccent: CampusAccentColor { appearance?.accentColor ?? .red }
    private var hc: Bool { appearance?.accessibility.highContrastMode ?? false }
    private var minTouch: CGFloat { appearance?.accessibility.minTouchHeight ?? 48 }
    private var isHorizontalOrientation: Bool { store.timetableOrientation == "horizontal" }
    private var verticalColWidth: CGFloat { isRegular ? 136 : 112 }
    private var horizontalColWidth: CGFloat { isRegular ? 144 : 124 }
    private var cellSpacing: CGFloat { isRegular ? 10 : 8 }

    private var activeFriend: FriendSchedule? {
        guard let selectedId = store.selectedFriendScheduleId else { return nil }
        return store.friendSchedules.first { $0.id == selectedId }
    }

    private var studentDisplayName: String {
        let trimmed = store.studentProfile.fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "Estudante UFOP" : trimmed
    }

    private var myBundleJson: String {
        store.exportMyGradeBundleJson(semester: store.selectedSemesterFilter)
    }

    private var mySuggestedFilename: String {
        UfopGradeCodec.suggestedFilename(forStudentName: studentDisplayName)
    }

    private var myShareTitle: String {
        "Grade de \(studentDisplayName) · \(store.selectedSemesterFilter)"
    }

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
        let friend = activeFriend
        let timetable = friend.map { store.weeklyTimetable(for: $0) } ?? store.currentWeeklyTimetable
        let filteredSlots: [TimetableSlotCell] = {
            guard friend == nil, selectedFilter != "Todas", selectedFilter != "Minha grade" else {
                return timetable.slots
            }
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
        let legendEntries: [TimetableLegendEntry] = {
            if let friend {
                return store.timetableLegendEntries(for: friend, slots: filteredSlots)
            }
            return store.timetableLegendEntries(for: filteredSlots)
        }()

        ZStack {
            LiquidGlassBackdrop(accent: resolvedAccent, highContrast: hc)

            VStack(spacing: 0) {
                topBar(timetable: timetable, activeFriend: friend)
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 10)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 16) {
                        controlsBar(timetable: timetable, activeFriend: friend)

                        if filteredSlots.isEmpty {
                            emptyTimetableCard(isFriendMode: friend != nil)
                        } else if isHorizontalOrientation {
                            horizontalGridCard(
                                weekdays: timetable.weekdays,
                                timeRanges: displayedTimeRanges,
                                slots: filteredSlots,
                                isFriendMode: friend != nil
                            )
                        } else {
                            verticalGridCard(
                                weekdays: timetable.weekdays,
                                timeRanges: displayedTimeRanges,
                                slots: filteredSlots,
                                isFriendMode: friend != nil
                            )
                        }

                        if !legendEntries.isEmpty {
                            legendSection(entries: legendEntries)
                        }

                        if let friend {
                            friendBottomCard(friend: friend)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 4)
                    .padding(.bottom, 32)
                }
            }
        }
        .background(
            AirDropProximityBridge(
                isActive: friend == nil,
                jsonPayload: myBundleJson,
                filename: mySuggestedFilename,
                shareTitle: myShareTitle
            )
            .frame(width: 0, height: 0)
            .allowsHitTesting(false)
        )
        .preferredColorScheme(appearance?.resolvedColorScheme)
        .sheet(item: $selectedCell) { cell in
            let isEad = store.isCourseEad(cell.disciplineId) || CampusStore.isEadText(cell.roomLabel)
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
                onOpenLocationInMap: (!isEad && onOpenLocationInMap != nil) ? {
                    let req = meetingRequest(for: cell)
                    selectedCell = nil
                    onClose()
                    onOpenLocationInMap?(req)
                } : nil,
                onClose: {
                    selectedCell = nil
                }
            )
            .presentationDetents([.medium])
        }
        .sheet(item: $selectedFriendCell) { cell in
            if let friend = activeFriend {
                let isEad = CampusStore.isEadText(cell.roomLabel)
                let matchedCourse = friend.courses.first { $0.courseId == cell.disciplineId }
                FriendTimetableCellDetailSheet(
                    cell: cell,
                    friend: friend,
                    course: matchedCourse,
                    onOpenLocationInMap: (!isEad && onOpenLocationInMap != nil) ? {
                        let req = friendMeetingRequest(for: cell, course: matchedCourse)
                        selectedFriendCell = nil
                        onClose()
                        onOpenLocationInMap?(req)
                    } : nil,
                    onClose: {
                        selectedFriendCell = nil
                    }
                )
                .presentationDetents([.medium])
            }
        }
        .sheet(item: $activeCheckInDetail) { detail in
            let isEad = store.isCourseEad(detail.disciplineId) || CampusStore.isEadText(detail.location)
            ClassCheckInSheetView(
                detail: detail,
                onSave: { status, colorKey in
                    store.saveClassCheckIn(agendaItemId: detail.agendaItemId, status: status, colorKey: colorKey)
                    activeCheckInDetail = nil
                },
                onCancel: {
                    activeCheckInDetail = nil
                },
                onOpenLocationInMap: (!isEad && onOpenLocationInMap != nil) ? {
                    let req = meetingRequest(for: detail)
                    activeCheckInDetail = nil
                    onClose()
                    onOpenLocationInMap?(req)
                } : nil
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
                onOpenLocationInMap: onOpenLocationInMap.map { handler in
                    { req in
                        openedDisciplineDetail = nil
                        onClose()
                        handler(req)
                    }
                },
                onClose: {
                    openedDisciplineDetail = nil
                }
            )
        }
        .sheet(item: $qrSheetMode) { mode in
            GradeQrCodeSheetView(
                myBundleJson: myBundleJson,
                studentDisplayName: studentDisplayName,
                semesterLabel: store.selectedSemesterFilter,
                initialMode: mode,
                onScannedPreview: { preview in
                    qrSheetMode = nil
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                        pendingIncomingGrade = preview
                    }
                },
                onClose: {
                    qrSheetMode = nil
                }
            )
        }
        .sheet(item: $pendingIncomingGrade) { preview in
            IncomingGradeConfirmSheetView(
                preview: preview,
                onConfirm: {
                    let imported = store.importFriendSchedule(from: preview)
                    store.selectedFriendScheduleId = imported.id
                    pendingIncomingGrade = nil
                },
                onCancel: {
                    pendingIncomingGrade = nil
                }
            )
        }
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [.ufopGrade, .json, .plainText, .data],
            allowsMultipleSelection: false
        ) { result in
            handleFileImportResult(result)
        }
        .confirmationDialog(
            "Aplicar modelo de grade?",
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
            Text("Isso substituirá as disciplinas do semestre \(store.selectedSemesterFilter) pelo modelo de grade escolhido.")
        }
        .confirmationDialog(
            "Remover grade de amigo?",
            isPresented: Binding(
                get: { pendingRemoveFriend != nil },
                set: { if !$0 { pendingRemoveFriend = nil } }
            ),
            titleVisibility: .visible
        ) {
            if let friendToDelete = pendingRemoveFriend {
                Button("Remover \(friendToDelete.friendName)", role: .destructive) {
                    store.removeFriendSchedule(id: friendToDelete.id)
                    pendingRemoveFriend = nil
                }
            }
            Button("Cancelar", role: .cancel) {
                pendingRemoveFriend = nil
            }
        } message: {
            Text("A grade salva deste colega será removida do aplicativo.")
        }
        .alert(
            "Não foi possível importar",
            isPresented: Binding(
                get: { importErrorMessage != nil },
                set: { if !$0 { importErrorMessage = nil } }
            )
        ) {
            Button("OK", role: .cancel) {
                importErrorMessage = nil
            }
        } message: {
            Text(importErrorMessage ?? "Arquivo .ufopgrade inválido.")
        }
    }

    // MARK: - Top Bar

    private func topBar(timetable: WeeklyTimetable, activeFriend: FriendSchedule?) -> some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text("SEMESTRE \(timetable.semester)")
                    .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                    .tracking(0.9)
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

                Text(activeFriend == nil ? "Grade Horária" : "Grade de \(activeFriend?.friendName ?? "")")
                    .font(CampusTheme.font(size: 26, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
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

    // MARK: - Controls (Owner, Filter, Templates, Share, QR, Orientation, Hide Empty)

    private func controlsBar(timetable: WeeklyTimetable, activeFriend: FriendSchedule?) -> some View {
        let primary = resolvedAccent.primary(for: colorScheme)
        let templates = store.listUfopTemplates()

        return VStack(alignment: .leading, spacing: 10) {
            LiquidGlassGroup(spacing: 8) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ownerPickerMenu(primary: primary, activeFriend: activeFriend)

                        if activeFriend == nil {
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
                            .minimumTouchTarget(minTouch)

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
                            .minimumTouchTarget(minTouch)
                        }

                        ShareLink(
                            item: UfopGradeTransferable(
                                jsonPayload: myBundleJson,
                                suggestedFilename: mySuggestedFilename,
                                displayTitle: myShareTitle
                            ),
                            preview: SharePreview(
                                mySuggestedFilename,
                                icon: Image(systemName: "calendar.badge.plus")
                            )
                        ) {
                            HStack(spacing: 5) {
                                Image(systemName: "square.and.arrow.up")
                                    .font(.system(size: 11, weight: .semibold))
                                Text("Compartilhar")
                                    .font(.system(size: 12, weight: .semibold))
                            }
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .padding(.horizontal, 12)
                            .frame(minHeight: 38)
                            .liquidGlassCapsule(selected: false, accent: primary, interactive: true, highContrast: hc)
                        }
                        .buttonStyle(.plain)
                        .minimumTouchTarget(minTouch)
                        .accessibilityLabel("Compartilhar minha grade em arquivo .ufopgrade")

                        Button {
                            qrSheetMode = activeFriend == nil ? .myQr : .scanQr
                        } label: {
                            HStack(spacing: 5) {
                                Image(systemName: "qrcode")
                                    .font(.system(size: 12, weight: .semibold))
                                Text("QR Code")
                                    .font(.system(size: 12, weight: .semibold))
                            }
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .padding(.horizontal, 12)
                            .frame(minHeight: 38)
                            .liquidGlassCapsule(selected: false, accent: primary, interactive: true, highContrast: hc)
                        }
                        .buttonStyle(.plain)
                        .minimumTouchTarget(minTouch)
                        .accessibilityLabel("Abrir QR Code da grade")
                    }
                }
            }

            HStack(spacing: 12) {
                Toggle(isOn: $hideEmptyHours) {
                    Text("Ocultar horários vazios")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                }
                .tint(primary)

                HStack(spacing: 4) {
                    orientationButton(key: "vertical", icon: "rectangle.split.3x1", label: "Grade vertical")
                    orientationButton(key: "horizontal", icon: "rectangle.split.1x2", label: "Grade horizontal")
                }
                .padding(3)
                .liquidGlassCapsule(selected: false, accent: primary, interactive: false, highContrast: hc)
            }
        }
    }

    private func ownerPickerMenu(primary: Color, activeFriend: FriendSchedule?) -> some View {
        Menu {
            Button {
                store.selectedFriendScheduleId = nil
            } label: {
                if activeFriend == nil {
                    Label("Minha grade", systemImage: "checkmark")
                } else {
                    Label("Minha grade", systemImage: "person.fill")
                }
            }

            if !store.friendSchedules.isEmpty {
                Section("Grades de amigos") {
                    ForEach(store.friendSchedules) { friend in
                        Button {
                            store.selectedFriendScheduleId = friend.id
                        } label: {
                            if friend.id == activeFriend?.id {
                                Label("\(friend.friendName) (\(friend.semester))", systemImage: "checkmark")
                            } else {
                                Label("\(friend.friendName) (\(friend.semester))", systemImage: "person.crop.circle")
                            }
                        }
                    }
                }
            }

            Divider()

            Button {
                showFileImporter = true
            } label: {
                Label("Importar arquivo (.ufopgrade)", systemImage: "doc.badge.plus")
            }

            Button {
                qrSheetMode = .scanQr
            } label: {
                Label("Ler QR Code", systemImage: "qrcode.viewfinder")
            }
        } label: {
            HStack(spacing: 6) {
                if let activeFriend {
                    FriendAvatarCircleView(
                        name: activeFriend.friendName,
                        avatarBase64: activeFriend.avatarBase64,
                        size: 20,
                        accent: primary,
                        onAccent: resolvedAccent.onPrimary(for: colorScheme)
                    )
                    Text(activeFriend.friendName)
                        .font(CampusTheme.font(size: 12, weight: .bold))
                        .lineLimit(1)
                } else {
                    Image(systemName: "person.crop.circle.fill")
                        .font(.system(size: 13, weight: .semibold))
                    Text("Minha grade")
                        .font(CampusTheme.font(size: 12, weight: .bold))
                }
                Image(systemName: "chevron.down")
                    .font(.system(size: 10, weight: .bold))
            }
            .foregroundStyle(
                activeFriend == nil
                    ? CampusTheme.accessibleTextColor(primary, scheme: colorScheme)
                    : resolvedAccent.onPrimary(for: colorScheme)
            )
            .padding(.horizontal, 12)
            .frame(minHeight: 38)
            .liquidGlassCapsule(selected: activeFriend != nil, accent: primary, interactive: true, highContrast: hc)
        }
        .minimumTouchTarget(minTouch)
        .accessibilityLabel("Selecionar grade exibida")
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

    private func verticalGridCard(
        weekdays: [String],
        timeRanges: [String],
        slots: [TimetableSlotCell],
        isFriendMode: Bool
    ) -> some View {
        let primary = resolvedAccent.primary(for: colorScheme)

        return ScrollView(.horizontal, showsIndicators: false) {
            VStack(spacing: cellSpacing) {
                HStack(spacing: cellSpacing) {
                    Text("HORÁRIO")
                        .font(CampusTheme.font(size: 10, weight: .bold))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
                        .frame(width: 72, alignment: .leading)

                    ForEach(weekdays, id: \.self) { day in
                        let isToday = day == todayWeekdayShort
                        Text(isToday ? "\(day) •" : day)
                            .font(CampusTheme.font(size: 12, weight: .bold))
                            .foregroundStyle(isToday ? resolvedAccent.onPrimary(for: colorScheme) : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .frame(width: verticalColWidth, height: 32)
                            .liquidGlassCapsule(selected: isToday, accent: primary, interactive: false, highContrast: hc)
                    }
                }

                ForEach(timeRanges, id: \.self) { range in
                    HStack(spacing: cellSpacing) {
                        Text(range.replacingOccurrences(of: "–", with: "\n"))
                            .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            .frame(width: 72, alignment: .leading)

                        ForEach(weekdays, id: \.self) { day in
                            let match = slots.first { $0.weekdayShort == day && $0.timeRange == range }
                            gridCellView(cell: match, isTodayColumn: day == todayWeekdayShort, isFriendMode: isFriendMode)
                                .frame(width: verticalColWidth, height: 74)
                        }
                    }
                }
            }
            .padding(14)
        }
        .liquidGlassCard(cornerRadius: CampusTheme.cardCornerRadius, tint: primary, highContrast: hc)
    }

    // MARK: - Horizontal Grid (Weekday rows x Time columns)

    private func horizontalGridCard(
        weekdays: [String],
        timeRanges: [String],
        slots: [TimetableSlotCell],
        isFriendMode: Bool
    ) -> some View {
        let primary = resolvedAccent.primary(for: colorScheme)

        return ScrollView(.horizontal, showsIndicators: false) {
            VStack(spacing: cellSpacing) {
                HStack(spacing: cellSpacing) {
                    Text("DIA")
                        .font(CampusTheme.font(size: 10, weight: .bold))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
                        .frame(width: 58, alignment: .leading)

                    ForEach(timeRanges, id: \.self) { range in
                        Text(range)
                            .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .frame(width: horizontalColWidth, height: 32)
                            .liquidGlassCapsule(selected: false, accent: primary, interactive: false, highContrast: hc)
                    }
                }

                ForEach(weekdays, id: \.self) { day in
                    let isToday = day == todayWeekdayShort
                    HStack(spacing: cellSpacing) {
                        Text(isToday ? "\(day)\nHOJE" : day)
                            .font(CampusTheme.font(size: 11, weight: .bold))
                            .multilineTextAlignment(.center)
                            .foregroundStyle(isToday ? resolvedAccent.onPrimary(for: colorScheme) : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .frame(width: 58, height: 74)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(isToday ? primary : CampusTheme.secondarySurface(for: colorScheme, highContrast: hc))
                            )

                        ForEach(timeRanges, id: \.self) { range in
                            let match = slots.first { $0.weekdayShort == day && $0.timeRange == range }
                            gridCellView(cell: match, isTodayColumn: isToday, isFriendMode: isFriendMode)
                                .frame(width: horizontalColWidth, height: 74)
                        }
                    }
                }
            }
            .padding(14)
        }
        .liquidGlassCard(cornerRadius: CampusTheme.cardCornerRadius, tint: primary, highContrast: hc)
    }

    @ViewBuilder
    private func gridCellView(cell: TimetableSlotCell?, isTodayColumn: Bool, isFriendMode: Bool) -> some View {
        if let cell {
            let deptColor = CampusTheme.departmentColor(for: cell.colorKey)
            let isEad = isFriendMode
                ? CampusStore.isEadText(cell.roomLabel)
                : store.isCourseEad(cell.disciplineId)
            let roomSub = isEad ? "EAD" : (cell.roomLabel.isEmpty ? "Sem sala" : cell.roomLabel)

            Button {
                if isFriendMode {
                    selectedFriendCell = cell
                } else {
                    selectedCell = cell
                }
            } label: {
                VStack(alignment: .leading, spacing: 4) {
                    Text(cell.disciplineShortName)
                        .font(CampusTheme.font(size: 12, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)

                    Spacer(minLength: 0)

                    Text(roomSub)
                        .font(CampusTheme.font(size: 10, weight: .semibold))
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
            .campusHoverLift()
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
                        .strokeBorder(hc ? CampusTheme.cardBorder(for: colorScheme, highContrast: true) : .clear, lineWidth: hc ? 1 : 0)
                )
        }
    }

    private func emptyTimetableCard(isFriendMode: Bool) -> some View {
        VStack(spacing: 8) {
            Image(systemName: "calendar.badge.exclamationmark")
                .font(.system(size: 28, weight: .light))
                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

            Text(isFriendMode ? "Sem horários nesta grade compartilhada" : "Sem horários cadastrados neste filtro")
                .font(CampusTheme.font(size: 15, weight: .bold))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

            Text(
                isFriendMode
                    ? "O arquivo importado não possui horários cadastrados para este semestre."
                    : "Adicione horários nas disciplinas ou escolha um modelo de grade acima."
            )
            .font(.system(size: 13))
            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
            .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .liquidGlassCard(cornerRadius: 20, highContrast: hc)
    }

    private func friendBottomCard(friend: FriendSchedule) -> some View {
        let primary = resolvedAccent.primary(for: colorScheme)
        let onPrimary = resolvedAccent.onPrimary(for: colorScheme)
        let cleanCourse = friend.courseName.trimmingCharacters(in: .whitespacesAndNewlines)

        return VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                FriendAvatarCircleView(
                    name: friend.friendName,
                    avatarBase64: friend.avatarBase64,
                    size: 46,
                    accent: primary,
                    onAccent: onPrimary
                )

                VStack(alignment: .leading, spacing: 3) {
                    Text(friend.friendName)
                        .font(CampusTheme.font(size: 16, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                    if !cleanCourse.isEmpty {
                        Text(cleanCourse)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(CampusTheme.accessibleTextColor(primary, scheme: colorScheme))
                            .lineLimit(1)
                    }

                    Text("\(friend.semester) · \(friend.courses.count) disciplina(s) · \(friend.slots.count) horário(s)")
                        .font(CampusTheme.tabularFont(size: 11, weight: .medium))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                }

                Spacer(minLength: 0)
            }

            HStack(spacing: 10) {
                Button {
                    store.selectedFriendScheduleId = nil
                } label: {
                    Label("Minha grade", systemImage: "person.fill")
                        .font(CampusTheme.font(size: 13, weight: .bold))
                        .foregroundStyle(onPrimary)
                        .frame(maxWidth: .infinity, minHeight: 42)
                        .liquidGlassCapsule(selected: true, accent: primary, interactive: true, highContrast: hc)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)

                Button(role: .destructive) {
                    pendingRemoveFriend = friend
                } label: {
                    Label("Remover", systemImage: "trash")
                        .font(CampusTheme.font(size: 13, weight: .bold))
                        .foregroundStyle(Color(hex: 0xDC2626))
                        .frame(maxWidth: .infinity, minHeight: 42)
                        .liquidGlassCapsule(selected: false, accent: Color(hex: 0xDC2626), interactive: true, highContrast: hc)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
            }
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 20, tint: primary, highContrast: hc)
    }

    private func handleFileImportResult(_ result: Result<[URL], Error>) {
        switch result {
        case .failure(let error):
            importErrorMessage = error.localizedDescription
        case .success(let urls):
            guard let url = urls.first else { return }
            do {
                let rawText = try UfopGradeCodec.readFileSafely(from: url)
                let label = url.lastPathComponent.isEmpty ? "Arquivo .ufopgrade" : url.lastPathComponent
                let preview = try UfopGradeCodec.parseAndValidatePreview(rawText, sourceLabel: label)
                pendingIncomingGrade = preview
            } catch {
                importErrorMessage = error.localizedDescription
            }
        }
    }

    private func friendMeetingRequest(for cell: TimetableSlotCell, course: FriendCourseEntry?) -> CampusMapMeetingRequest {
        let weekdayMap: [String: UInt32] = ["SEG": 1, "TER": 2, "QUA": 3, "QUI": 4, "SEX": 5, "SÁB": 6, "DOM": 7]
        let weekday = weekdayMap[cell.weekdayShort] ?? 1
        let startTime = CampusStore.normalizeTimeString(
            cell.timeRange.components(separatedBy: "–").first ?? ""
        )
        return CampusMapMeetingRequest(
            courseCode: course?.code ?? "",
            section: "",
            weekday: weekday,
            startTime: startTime,
            fallbackBuildingRaw: cell.roomLabel
        )
    }

    // MARK: - Legend Section

    private func legendSection(entries: [TimetableLegendEntry]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Legenda das disciplinas")
                .font(CampusTheme.font(size: 15, weight: .bold))
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

    private func meetingRequest(for cell: TimetableSlotCell) -> CampusMapMeetingRequest {
        let detail = store.getDisciplineDetail(disciplineId: cell.disciplineId)
        let slotMeta = store.courseScheduleSlots.first { $0.slotId == cell.id }
        let weekdayMap: [String: UInt32] = ["SEG": 1, "TER": 2, "QUA": 3, "QUI": 4, "SEX": 5, "SÁB": 6, "DOM": 7]
        let weekday = UInt32(slotMeta?.dayOfWeek ?? Int(weekdayMap[cell.weekdayShort] ?? 1))
        let startTime = CampusStore.normalizeTimeString(
            slotMeta?.startTime ?? cell.timeRange.components(separatedBy: "–").first ?? ""
        )
        let fallback: String = {
            if let slotMeta {
                let r = slotMeta.room.trimmingCharacters(in: .whitespacesAndNewlines)
                if r.isEmpty { return slotMeta.building }
                return slotMeta.building.isEmpty ? r : "\(slotMeta.building) · \(r)"
            }
            return cell.roomLabel
        }()
        return CampusMapMeetingRequest(
            courseCode: detail?.summary.code ?? "",
            section: store.turmaByCourseId[cell.disciplineId] ?? "",
            weekday: weekday,
            startTime: startTime,
            fallbackBuildingRaw: fallback
        )
    }

    private func meetingRequest(for detail: ClassCheckInDetail) -> CampusMapMeetingRequest {
        let disc = store.disciplines.first(where: { $0.id == detail.disciplineId })
        let parsedDate = CampusStore.parseIsoDate(store.selectedDateIso) ?? Date()
        let weekday = UInt32(CampusStore.isoWeekdayIndex(from: parsedDate))
        let startTime = CampusStore.normalizeTimeString(
            detail.timeRange.components(separatedBy: "–").first ?? detail.timeRange
        )
        return CampusMapMeetingRequest(
            courseCode: disc?.code ?? "",
            section: store.turmaByCourseId[detail.disciplineId] ?? "",
            weekday: weekday,
            startTime: startTime,
            fallbackBuildingRaw: detail.location
        )
    }
}

// MARK: - Cell Detail Modal Sheet

private struct TimetableCellDetailSheet: View {
    let cell: TimetableSlotCell
    let store: CampusStore
    let onOpenDiscipline: () -> Void
    let onQuickCheckIn: () -> Void
    let onOpenLocationInMap: (() -> Void)?
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
                        .font(CampusTheme.tabularFont(size: 12, weight: .bold))
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
                    .font(CampusTheme.font(size: 23, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                VStack(alignment: .leading, spacing: 8) {
                    Label("\(cell.weekdayShort) · \(cell.timeRange)", systemImage: "clock.fill")
                        .font(CampusTheme.tabularFont(size: 14, weight: .semibold))

                    if !isEad, let onOpenLocationInMap {
                        Button {
                            onOpenLocationInMap()
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "mappin.and.ellipse")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(accent.primary(for: colorScheme))

                                Text(roomDesc)
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                    .lineLimit(2)
                                    .multilineTextAlignment(.leading)

                                Spacer(minLength: 6)

                                HStack(spacing: 4) {
                                    Text("Ver no mapa")
                                        .font(CampusTheme.font(size: 12, weight: .bold))
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 11, weight: .bold))
                                }
                                .foregroundStyle(accent.primary(for: colorScheme))
                            }
                            .frame(minHeight: 36)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .campusHoverLift()
                        .accessibilityLabel("Ver \(roomDesc) no mapa")
                    } else {
                        Label(roomDesc, systemImage: isEad ? "laptopcomputer" : "building.2.fill")
                            .font(.system(size: 14, weight: .medium))
                    }

                    if !prof.isEmpty {
                        Label(prof, systemImage: "person.fill")
                            .font(.system(size: 14, weight: .medium))
                    }
                }
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .liquidGlassCard(cornerRadius: 18, tint: deptColor, highContrast: hc)

                Spacer()

                VStack(spacing: 10) {
                    LiquidGlassPrimaryButton(tint: accent.primary(for: colorScheme), foreground: accent.onPrimary(for: colorScheme), minHeight: 48) {
                        onQuickCheckIn()
                    } label: {
                        Label("Fazer check-in desta aula", systemImage: "checkmark.circle.fill")
                            .font(CampusTheme.font(size: 15, weight: .bold))
                    }

                    Button {
                        onOpenDiscipline()
                    } label: {
                        Text("Abrir detalhes da disciplina")
                            .font(CampusTheme.font(size: 14, weight: .bold))
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

// MARK: - Read-Only Friend Cell Detail Sheet

private struct FriendTimetableCellDetailSheet: View {
    let cell: TimetableSlotCell
    let friend: FriendSchedule
    let course: FriendCourseEntry?
    let onOpenLocationInMap: (() -> Void)?
    let onClose: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let accent = appearance?.accentColor ?? .green
        let hc = appearance?.accessibility.highContrastMode ?? false
        let deptColor = CampusTheme.departmentColor(for: cell.colorKey)
        let isEad = CampusStore.isEadText(cell.roomLabel)
        let roomDesc = isEad ? "A distância (EAD)" : (cell.roomLabel.isEmpty ? "Local não informado" : cell.roomLabel)
        let badgeText = (course?.code.isEmpty == false) ? (course?.code ?? cell.weekdayShort) : cell.weekdayShort

        ZStack {
            LiquidGlassBackdrop(accent: accent, highContrast: hc)

            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text(badgeText)
                        .font(CampusTheme.tabularFont(size: 12, weight: .bold))
                        .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Capsule().fill(CampusTheme.departmentSoftTint(for: cell.colorKey, scheme: colorScheme)))

                    Text("Grade de \(friend.friendName)")
                        .font(CampusTheme.font(size: 11, weight: .semibold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .liquidGlassCapsule(selected: false, accent: deptColor, interactive: false, highContrast: hc)

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

                Text(course?.name ?? cell.disciplineShortName)
                    .font(CampusTheme.font(size: 22, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                VStack(alignment: .leading, spacing: 10) {
                    Label("\(cell.weekdayShort) · \(cell.timeRange)", systemImage: "clock.fill")
                        .font(CampusTheme.tabularFont(size: 14, weight: .semibold))

                    if !isEad, let onOpenLocationInMap {
                        Button {
                            onOpenLocationInMap()
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "mappin.and.ellipse")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(accent.primary(for: colorScheme))

                                Text(roomDesc)
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                    .lineLimit(2)
                                    .multilineTextAlignment(.leading)

                                Spacer(minLength: 6)

                                HStack(spacing: 4) {
                                    Text("Ver no mapa")
                                        .font(CampusTheme.font(size: 12, weight: .bold))
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 11, weight: .bold))
                                }
                                .foregroundStyle(accent.primary(for: colorScheme))
                            }
                            .frame(minHeight: 36)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .campusHoverLift()
                    } else {
                        Label(roomDesc, systemImage: isEad ? "laptopcomputer" : "building.2.fill")
                            .font(.system(size: 14, weight: .medium))
                    }

                    Label("Modo somente leitura (grade de amigo)", systemImage: "eye")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
                }
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .liquidGlassCard(cornerRadius: 16, tint: deptColor, highContrast: hc)

                Spacer()

                Button {
                    onClose()
                } label: {
                    Text("Fechar")
                        .font(CampusTheme.font(size: 14, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .liquidGlassCapsule(selected: false, accent: deptColor, interactive: true, highContrast: hc)
                }
                .buttonStyle(.plain)
            }
            .padding(20)
        }
    }
}

#Preview("TimetableGridView") {
    TimetableGridView(store: CampusStore(), onClose: {})
}
