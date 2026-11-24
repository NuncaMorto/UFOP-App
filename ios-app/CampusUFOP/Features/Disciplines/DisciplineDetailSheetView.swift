import SwiftUI
import UniformTypeIdentifiers

public struct DisciplineDetailSheetView: View {
    public let detail: DisciplineDetail
    public let store: CampusStore?
    public let onUpdateNotes: (String) -> Void
    public let onDeleteCourse: (() -> Void)?
    public let onOpenLocationInMap: ((CampusMapMeetingRequest) -> Void)?
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

    public init(
        detail: DisciplineDetail,
        store: CampusStore? = nil,
        onUpdateNotes: @escaping (String) -> Void,
        onDeleteCourse: (() -> Void)? = nil,
        onOpenLocationInMap: ((CampusMapMeetingRequest) -> Void)? = nil,
        onClose: @escaping () -> Void
    ) {
        self.detail = detail
        self.store = store
        self.onUpdateNotes = onUpdateNotes
        self.onDeleteCourse = onDeleteCourse
        self.onOpenLocationInMap = onOpenLocationInMap
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
        let primaryAccent = resolvedAccent.primary(for: colorScheme)

        ZStack {
            LiquidGlassBackdrop(accent: resolvedAccent, highContrast: hc)

            VStack(spacing: 0) {
                DisciplineDetailTopBar(
                    summary: currentDetail.summary,
                    deptColor: deptColor,
                    primaryAccent: primaryAccent,
                    isEadCourse: isEadCourse,
                    highContrast: hc,
                    minTouch: minTouch,
                    onClose: {
                        onUpdateNotes(notesText)
                        onClose()
                    }
                )
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 10)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {
                        DisciplineDetailHeaderSection(
                            summary: currentDetail.summary,
                            highContrast: hc
                        )

                        if let onOpenLocationInMap, let target = nextLocationTarget(for: currentDetail) {
                            DisciplineDetailLocationShortcutRow(
                                locationText: target.location,
                                scheduleLabel: target.schedule,
                                deptColor: deptColor,
                                primaryAccent: primaryAccent,
                                highContrast: hc,
                                minTouch: minTouch,
                                onTap: {
                                    onUpdateNotes(notesText)
                                    onClose()
                                    onOpenLocationInMap(target.request)
                                }
                            )
                        }

                        DisciplineAbsenceReportCard(
                            summary: currentDetail.summary,
                            deptColor: deptColor,
                            highContrast: hc
                        )

                        DisciplineEadAndSyllabusCard(
                            isEadBinding: Binding<Bool>(
                                get: { activeStore?.isCourseEad(currentDetail.summary.id) ?? false },
                                set: { newValue in
                                    activeStore?.setCourseEad(currentDetail.summary.id, isEad: newValue)
                                }
                            ),
                            isStoreAvailable: activeStore != nil,
                            primaryAccent: primaryAccent,
                            highContrast: hc,
                            minTouch: minTouch,
                            syllabusErrorMessage: syllabusErrorMessage,
                            syllabusWarningMessage: syllabusWarningMessage,
                            syllabusReportMessage: syllabusReportMessage,
                            onImportSyllabus: {
                                syllabusErrorMessage = nil
                                syllabusWarningMessage = nil
                                syllabusReportMessage = nil
                                showSyllabusImporter = true
                            }
                        )

                        DisciplineAbsenceHistorySection(
                            courseName: currentDetail.summary.name,
                            history: resolvedAbsenceHistory,
                            highContrast: hc
                        )

                        DisciplineWeeklySlotsSection(
                            courseName: currentDetail.summary.name,
                            colorKey: currentDetail.summary.colorKey,
                            slots: resolvedSlots,
                            isEadCourse: isEadCourse,
                            deptColor: deptColor,
                            highContrast: hc,
                            minTouch: minTouch,
                            isAddingSlot: $isAddingSlot,
                            newSlotDayOfWeek: $newSlotDayOfWeek,
                            newSlotStartTime: $newSlotStartTime,
                            newSlotEndTime: $newSlotEndTime,
                            newSlotBuilding: $newSlotBuilding,
                            newSlotRoom: $newSlotRoom,
                            newSlotShortLocation: $newSlotShortLocation,
                            slotConflictError: $slotConflictError,
                            onSubmitSlot: {
                                submitNewSlot(courseId: currentDetail.summary.id)
                            },
                            onDeleteSlot: { slotId in
                                deleteSlot(slotId)
                            }
                        )

                        DisciplineAssessmentsSection(
                            courseName: currentDetail.summary.name,
                            colorKey: currentDetail.summary.colorKey,
                            assessments: currentDetail.assessments,
                            deptColor: deptColor,
                            highContrast: hc,
                            minTouch: minTouch,
                            isAddingAssessment: $isAddingAssessment,
                            newAssessmentTitle: $newAssessmentTitle,
                            newAssessmentKind: $newAssessmentKind,
                            newAssessmentDueDate: $newAssessmentDueDate,
                            newAssessmentDueTime: $newAssessmentDueTime,
                            onSubmitAssessment: {
                                submitNewAssessment(courseId: currentDetail.summary.id)
                            },
                            onToggleAssessment: { assessmentId in
                                toggleAssessment(assessmentId)
                            },
                            onDeleteAssessment: { assessmentId in
                                deleteAssessment(assessmentId)
                            }
                        )

                        DisciplineNotesSection(
                            notesText: $notesText,
                            highContrast: hc
                        )

                        DisciplineDeleteCourseButton(
                            courseName: currentDetail.summary.name,
                            minTouch: minTouch,
                            onRequestDelete: {
                                isDeleteConfirmationPresented = true
                            }
                        )
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

    // MARK: - Handlers

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

    private func performDeleteCourse(courseId: String) {
        if let onDeleteCourse {
            onDeleteCourse()
        } else {
            activeStore?.deleteCourse(courseId: courseId)
        }
        onClose()
    }

    private func nextLocationTarget(
        for currentDetail: DisciplineDetail
    ) -> (location: String, schedule: String?, request: CampusMapMeetingRequest)? {
        guard !isEadCourse else { return nil }
        let section = activeStore?.turmaByCourseId[currentDetail.summary.id] ?? ""
        let nonEadSlots = resolvedSlots.filter {
            !CampusStore.isEadText($0.building) && !CampusStore.isEadText($0.shortLocation)
        }

        if !nonEadSlots.isEmpty {
            let now = Date()
            let todayWd = CampusStore.isoWeekdayIndex(from: now)
            let comps = Calendar(identifier: .gregorian).dateComponents([.hour, .minute], from: now)
            let nowMinutes = (comps.hour ?? 8) * 60 + (comps.minute ?? 0)

            let chosen = nonEadSlots.first { slot in
                if slot.dayOfWeek > todayWd { return true }
                if slot.dayOfWeek == todayWd {
                    let endMin = CampusStore.minutesFromTime(slot.endTime)
                        ?? (CampusStore.minutesFromTime(slot.startTime) ?? 0)
                    return endMin >= nowMinutes
                }
                return false
            } ?? nonEadSlots[0]

            let cleanBuilding = chosen.building.trimmingCharacters(in: .whitespacesAndNewlines)
            let cleanRoom = chosen.room.trimmingCharacters(in: .whitespacesAndNewlines)
            let displayLoc: String = {
                if !cleanBuilding.isEmpty && !cleanRoom.isEmpty {
                    return "\(cleanBuilding) · \(cleanRoom)"
                }
                if !cleanBuilding.isEmpty { return cleanBuilding }
                if !cleanRoom.isEmpty { return cleanRoom }
                return currentDetail.summary.location
            }()
            let request = CampusMapMeetingRequest(
                courseCode: currentDetail.summary.code,
                section: section,
                weekday: UInt32(chosen.dayOfWeek),
                startTime: chosen.startTime,
                fallbackBuildingRaw: displayLoc
            )
            return (displayLoc, "\(chosen.dayLabel) \(chosen.startTime)", request)
        }

        let cleanSummaryLoc = currentDetail.summary.location.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanSummaryLoc.isEmpty, !CampusStore.isEadText(cleanSummaryLoc) else {
            return nil
        }
        let request = CampusMapMeetingRequest(
            courseCode: currentDetail.summary.code,
            section: section,
            weekday: 1,
            startTime: "",
            fallbackBuildingRaw: cleanSummaryLoc
        )
        return (cleanSummaryLoc, nil, request)
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
