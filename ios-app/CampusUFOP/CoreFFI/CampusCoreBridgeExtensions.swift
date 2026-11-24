import Foundation

// MARK: - Persistent Encrypted State & Agenda Intelligence Models

private struct PersistedCampusState: Codable {
    var schemaVersion: Int = 5
    var studentProfile: StudentProfile
    var selectedSemesterFilter: String
    var activeCampusId: String
    var disciplines: [DisciplineSummaryCard]
    var courseScheduleSlots: [CourseScheduleSlotItem]
    var timetableSlots: [TimetableSlotCell]
    var assessmentsByDiscipline: [String: [AssessmentItem]]
    var notesByDiscipline: [String: String]
    var agendaItemsByDate: [String: [AgendaTimelineItem]]
    var recordedStatusByAgendaItem: [String: AttendanceStatus]
    var absenceHistoryBySession: [String: AbsenceHistoryEntry]
    var transcriptEntries: [TranscriptCourseEntry]
    var isEadByCourseId: [String: Bool]
    var turmaByCourseId: [String: String]
    var dynamicGridCompletedByMatrix: [String: [String]]?
    var friendSchedules: [FriendSchedule]?
}

public struct AgendaClassSpotlight: Equatable, Sendable {
    public let item: AgendaTimelineItem
    public let dateIso: String
    public let dayLabel: String
    public let isOngoing: Bool
}

public struct AcademicTermBannerInfo: Equatable, Sendable {
    public let badge: String
    public let title: String
    public let detail: String
    public let isRecessOrBreak: Bool
}

// MARK: - CampusStore Extended Features (Security, Parsers, Rooms, Real Calendar, CRE)

public extension CampusStore {

    // MARK: Encrypted Persistence & Portable Backups (.ufopbackup)

    private static var encryptedStateFileUrl: URL {
        let fm = FileManager.default
        let appSupport = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first ?? fm.temporaryDirectory
        let dir = appSupport.appendingPathComponent("VidaAcademicaUFOP", isDirectory: true)
        if !fm.fileExists(atPath: dir.path) {
            try? fm.createDirectory(at: dir, withIntermediateDirectories: true)
        }
        return dir.appendingPathComponent("campus_ufop_state.enc")
    }

    func persistEncryptedState() {
        guard hasCompletedInit else { return }
        let snapshot = PersistedCampusState(
            studentProfile: studentProfile,
            selectedSemesterFilter: selectedSemesterFilter,
            activeCampusId: activeCampus.id,
            disciplines: disciplines,
            courseScheduleSlots: courseScheduleSlots,
            timetableSlots: timetableSlots,
            assessmentsByDiscipline: assessmentsByDiscipline,
            notesByDiscipline: notesByDiscipline,
            agendaItemsByDate: agendaItemsByDate,
            recordedStatusByAgendaItem: recordedStatusByAgendaItem,
            absenceHistoryBySession: absenceHistoryBySession,
            transcriptEntries: transcriptEntries,
            isEadByCourseId: isEadByCourseId,
            turmaByCourseId: turmaByCourseId,
            dynamicGridCompletedByMatrix: dynamicGridCompletedByMatrix,
            friendSchedules: friendSchedules
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        guard let data = try? encoder.encode(snapshot),
              let json = String(data: data, encoding: .utf8),
              let encrypted = try? CampusCryptoEngine.encryptStatePayload(json) else {
            return
        }
        try? encrypted.write(to: Self.encryptedStateFileUrl, atomically: true, encoding: .utf8)
        #if os(iOS)
        try? (Self.encryptedStateFileUrl as NSURL).setResourceValue(
            URLFileProtection.completeUntilFirstUserAuthentication,
            forKey: .fileProtectionKey
        )
        #endif
    }

    func loadPersistedEncryptedStateIfAvailable() {
        let url = Self.encryptedStateFileUrl
        guard FileManager.default.fileExists(atPath: url.path),
              let raw = try? String(contentsOf: url, encoding: .utf8),
              let plainJson = try? CampusCryptoEngine.decryptStatePayloadIfNeeded(raw),
              let data = plainJson.data(using: .utf8),
              let decoded = try? JSONDecoder().decode(PersistedCampusState.self, from: data) else {
            return
        }
        applySnapshot(decoded)
    }

    func exportEncryptedBackup(password: String) throws -> String {
        let snapshot = PersistedCampusState(
            studentProfile: studentProfile,
            selectedSemesterFilter: selectedSemesterFilter,
            activeCampusId: activeCampus.id,
            disciplines: disciplines,
            courseScheduleSlots: courseScheduleSlots,
            timetableSlots: timetableSlots,
            assessmentsByDiscipline: assessmentsByDiscipline,
            notesByDiscipline: notesByDiscipline,
            agendaItemsByDate: agendaItemsByDate,
            recordedStatusByAgendaItem: recordedStatusByAgendaItem,
            absenceHistoryBySession: absenceHistoryBySession,
            transcriptEntries: transcriptEntries,
            isEadByCourseId: isEadByCourseId,
            turmaByCourseId: turmaByCourseId,
            dynamicGridCompletedByMatrix: dynamicGridCompletedByMatrix,
            friendSchedules: friendSchedules
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(snapshot)
        guard let json = String(data: data, encoding: .utf8) else {
            throw CampusCryptoError.encryptionFailed
        }
        return try CampusCryptoEngine.encryptBackupPayload(json, password: password)
    }

    @discardableResult
    func importEncryptedBackup(_ bundle: String, password: String) throws -> StudentProfile {
        let json = try CampusCryptoEngine.decryptBackupPayload(bundle, password: password)
        guard let data = json.data(using: .utf8) else {
            throw CampusCryptoError.invalidBackupFormat
        }
        let decoded = try JSONDecoder().decode(PersistedCampusState.self, from: data)
        applySnapshot(decoded)
        studentProfile.onboardingCompleted = true
        ensureOfficialCalendarEventsLoaded()
        _ = applyRoomAllocationCatalog()
        persistEncryptedState()
        return studentProfile
    }

    func signOutAndResetAllData(lockManager: AppLockManager, appearance: CampusAppearanceController) {
        try? FileManager.default.removeItem(at: Self.encryptedStateFileUrl)
        SecurePdfProcessor.purgeResidualTemporaryPdfs()
        lockManager.clearAllLockData()
        appearance.resetDefaults()
        studentProfile = StudentProfile(onboardingCompleted: false)
        transcriptEntries = []
        isEadByCourseId = [:]
        turmaByCourseId = [:]
        dynamicGridCompletedByMatrix = [:]
        friendSchedules = []
        selectedFriendScheduleId = nil
        requestedTimetablePresentation = false
        guidedTourCompleted = false
        seedUFOP2026_2Data()
        ensureOfficialCalendarEventsLoaded()
        _ = applyRoomAllocationCatalog()
    }

    private func applySnapshot(_ snapshot: PersistedCampusState) {
        self.studentProfile = snapshot.studentProfile
        self.selectedSemesterFilter = snapshot.selectedSemesterFilter.isEmpty ? "2026.2" : snapshot.selectedSemesterFilter
        _ = setActiveCampus(campusId: snapshot.activeCampusId)
        self.disciplines = snapshot.disciplines
        self.courseScheduleSlots = snapshot.courseScheduleSlots
        self.timetableSlots = snapshot.timetableSlots
        self.assessmentsByDiscipline = snapshot.assessmentsByDiscipline
        self.notesByDiscipline = snapshot.notesByDiscipline
        self.agendaItemsByDate = snapshot.agendaItemsByDate
        self.recordedStatusByAgendaItem = snapshot.recordedStatusByAgendaItem
        self.absenceHistoryBySession = snapshot.absenceHistoryBySession
        self.transcriptEntries = snapshot.transcriptEntries
        self.isEadByCourseId = snapshot.isEadByCourseId
        self.turmaByCourseId = snapshot.turmaByCourseId
        self.dynamicGridCompletedByMatrix = snapshot.dynamicGridCompletedByMatrix ?? [:]
        self.friendSchedules = snapshot.friendSchedules ?? []
        if let activeFriendId = selectedFriendScheduleId,
           !self.friendSchedules.contains(where: { $0.id == activeFriendId }) {
            self.selectedFriendScheduleId = nil
        }
        rebuildAcademicActivitiesFromAssessments()
    }

    // MARK: EAD State, Assessment Updates & Certificate / Syllabus / Transcript Import

    func isCourseEad(_ courseId: String) -> Bool {
        if let flag = isEadByCourseId[courseId], flag { return true }
        if let disc = disciplines.first(where: { $0.id == courseId }) {
            let loc = disc.location.lowercased()
            return loc.contains("ead") || loc.contains("distância") || loc.contains("distancia")
        }
        return false
    }

    func setCourseEad(_ courseId: String, isEad: Bool) {
        isEadByCourseId[courseId] = isEad
        if isEad, let idx = disciplines.firstIndex(where: { $0.id == courseId }) {
            disciplines[idx].location = "A distância (EAD)"
        }
        persistEncryptedState()
    }

    func updateAssessment(
        id: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String
    ) {
        let iso = Self.normalizeIsoDateInput(dueDate)
        let (dayNum, monthShort) = Self.dayAndMonthBadge(fromIso: iso)
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanKind = kind.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanTime = dueTime.trimmingCharacters(in: .whitespacesAndNewlines)

        for (courseId, var items) in assessmentsByDiscipline {
            if let idx = items.firstIndex(where: { $0.id == id }) {
                let updated = AssessmentItem(
                    id: items[idx].id,
                    courseId: courseId,
                    dayNumber: dayNum,
                    monthShort: monthShort,
                    title: cleanTitle.isEmpty ? items[idx].title : cleanTitle,
                    subtitle: "\(cleanKind.isEmpty ? items[idx].kind : cleanKind) · \(cleanTime.isEmpty ? items[idx].dueTime : cleanTime)",
                    kind: cleanKind.isEmpty ? items[idx].kind : cleanKind,
                    dueDate: iso,
                    dueTime: cleanTime.isEmpty ? items[idx].dueTime : cleanTime,
                    completed: items[idx].completed
                )
                items[idx] = updated
                assessmentsByDiscipline[courseId] = items
                rebuildAcademicActivitiesFromAssessments()
                persistEncryptedState()
                return
            }
        }
    }

    func completeManualOnboarding(
        fullName: String,
        registrationNumber: String,
        courseName: String,
        campusName: String,
        semester: String = "2026.2"
    ) {
        let cleanSem = semester.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "2026.2" : semester
        studentProfile = StudentProfile(
            fullName: fullName.trimmingCharacters(in: .whitespacesAndNewlines),
            registrationNumber: registrationNumber.trimmingCharacters(in: .whitespacesAndNewlines),
            courseName: courseName.trimmingCharacters(in: .whitespacesAndNewlines),
            campusName: campusName.trimmingCharacters(in: .whitespacesAndNewlines),
            activeSemester: cleanSem,
            onboardingCompleted: true
        )
        _ = setActiveCampus(campusId: campusName)
        persistEncryptedState()
    }

    func applyCertificateImport(_ parsed: ParsedCertificateResult, completeOnboarding: Bool = true) -> [DisciplineSummaryCard] {
        let targetSem = parsed.semester.isEmpty ? "2026.2" : parsed.semester
        selectedSemesterFilter = targetSem
        studentProfile = StudentProfile(
            fullName: parsed.studentName,
            registrationNumber: parsed.registrationNumber,
            courseName: parsed.courseName,
            campusName: parsed.campusName,
            activeSemester: targetSem,
            semesterStartDate: parsed.semesterStartDate,
            semesterEndDate: parsed.semesterEndDate,
            onboardingCompleted: completeOnboarding
        )
        _ = setActiveCampus(campusId: parsed.campusId)

        let existingIds = disciplines.filter { $0.semester == targetSem }.map(\.id)
        for existingId in existingIds {
            deleteCourse(courseId: existingId)
        }

        var createdCards: [DisciplineSummaryCard] = []
        for item in parsed.courses {
            let colorKey: DepartmentColorKey = {
                switch item.departmentId {
                case "comp": return .compPurple
                case "phys": return .physicsAmber
                default: return .mathBlue
                }
            }()
            let courseId = "\(item.code.lowercased())_\(targetSem.replacingOccurrences(of: ".", with: "_"))"
            let maxAbs = Self.calculateMaxAbsences(workloadHours: item.workloadHours)
            let defaultLocation: String = {
                if item.isEad { return "A distância (EAD)" }
                if let firstSlot = item.slots.first {
                    return firstSlot.room.isEmpty ? firstSlot.building : "\(firstSlot.building) · \(firstSlot.room)"
                }
                return item.building
            }()

            let deptLabel: String = {
                switch item.departmentId {
                case "comp": return "Computação"
                case "phys": return "Física"
                default: return "Matemática"
                }
            }()

            let card = DisciplineSummaryCard(
                id: courseId,
                code: item.code,
                name: item.name,
                department: deptLabel,
                semester: targetSem,
                workloadHours: item.workloadHours,
                currentAbsences: 0,
                maxAbsences: maxAbs,
                cancelledClasses: 0,
                colorKey: colorKey,
                professor: item.professor, // Vazio até o aluno informar ou subir Plano de Ensino
                location: defaultLocation
            )

            disciplines.append(card)
            createdCards.append(card)
            assessmentsByDiscipline[courseId] = []
            notesByDiscipline[courseId] = "Código: \(item.code)" + (item.turma.isEmpty ? "" : " · Turma \(item.turma)")
            isEadByCourseId[courseId] = item.isEad
            if !item.turma.isEmpty {
                turmaByCourseId[courseId] = item.turma
            }

            for slot in item.slots {
                let shortLoc: String
                if slot.isEad {
                    shortLoc = "EAD"
                } else if slot.room.isEmpty {
                    shortLoc = slot.building
                } else {
                    shortLoc = "\(slot.building) · \(slot.room.replacingOccurrences(of: "Sala ", with: ""))"
                }
                _ = try? addScheduleSlot(
                    courseId: courseId,
                    dayOfWeek: slot.dayOfWeek,
                    startTime: slot.startTime,
                    endTime: slot.endTime,
                    building: slot.building,
                    room: slot.room,
                    shortLocation: shortLoc
                )
            }
        }

        _ = applyRoomAllocationCatalog()
        rebuildAcademicActivitiesFromAssessments()
        persistEncryptedState()
        return createdCards
    }

    @discardableResult
    func applySyllabusImport(_ report: SyllabusImportReport, forCourseId courseId: String) -> Int {
        guard report.matchedCourse else { return 0 }
        if !report.extractedProfessor.isEmpty,
           let idx = disciplines.firstIndex(where: { $0.id == courseId }) {
            let old = disciplines[idx]
            disciplines[idx] = DisciplineSummaryCard(
                id: old.id,
                code: old.code,
                name: old.name,
                department: old.department,
                semester: old.semester,
                workloadHours: old.workloadHours,
                currentAbsences: old.currentAbsences,
                maxAbsences: old.maxAbsences,
                cancelledClasses: old.cancelledClasses,
                colorKey: old.colorKey,
                professor: report.extractedProfessor,
                location: old.location
            )
        }
        var added = 0
        let existing = assessmentsByDiscipline[courseId] ?? []
        for parsedItem in report.parsedAssessments {
            let dup = existing.contains {
                $0.dueDate == parsedItem.dueDate &&
                $0.title.localizedCaseInsensitiveCompare(parsedItem.title) == .orderedSame
            }
            if !dup {
                _ = createAssessment(
                    courseId: courseId,
                    title: parsedItem.title,
                    kind: parsedItem.kind,
                    dueDate: parsedItem.dueDate,
                    dueTime: parsedItem.dueTime
                )
                added += 1
            }
        }
        persistEncryptedState()
        return added
    }

    @discardableResult
    func applyTranscriptImport(_ parsed: ParsedTranscriptResult) -> AcademicCreReport {
        if let name = parsed.fullName, !name.isEmpty, studentProfile.fullName.isEmpty {
            studentProfile.fullName = name
        }
        if let reg = parsed.registrationNumber, !reg.isEmpty, studentProfile.registrationNumber.isEmpty {
            studentProfile.registrationNumber = reg
        }
        if let course = parsed.courseName, !course.isEmpty, studentProfile.courseName.isEmpty {
            studentProfile.courseName = course
        }
        transcriptEntries = parsed.entries
        persistEncryptedState()
        return getAcademicCreReport(extraCourses: [])
    }

    func getAcademicCreReport(extraCourses: [(workloadHours: Int, grade: Double)] = []) -> AcademicCreReport {
        let matchedCourse = UfopCurriculumCatalog.findCourse(matching: studentProfile.courseName)
        let reqHours = matchedCourse?.chTotal ?? 3610
        return UfopPdfParsers.computeCreReport(
            entries: transcriptEntries,
            totalRequiredHours: reqHours > 0 ? reqHours : 3610,
            extraCourses: extraCourses
        )
    }

    // MARK: UFOP Room Allocations Enrichment (ICEB & Bloco de Salas 2026.2)

    @discardableResult
    func applyRoomAllocationCatalog() -> Int {
        let index = UfopRoomAllocationCatalog.shared
        guard !index.entries.isEmpty else { return 0 }
        var updatedCount = 0

        for idx in courseScheduleSlots.indices {
            let slot = courseScheduleSlots[idx]
            guard let discIdx = disciplines.firstIndex(where: { $0.id == slot.courseId }) else { continue }
            let disc = disciplines[discIdx]
            if isCourseEad(disc.id) || slot.building.lowercased().contains("ead") || slot.building.lowercased().contains("distância") {
                continue
            }
            let turma = turmaByCourseId[disc.id]
            if let hit = index.lookup(courseCode: disc.code, turma: turma, dayOfWeek: slot.dayOfWeek, startTime: slot.startTime) {
                let shortLoc = "\(hit.building) · \(hit.room.replacingOccurrences(of: "Sala ", with: ""))"
                let fullLoc = hit.room.isEmpty ? hit.building : "\(hit.building) · \(hit.room)"
                courseScheduleSlots[idx] = CourseScheduleSlotItem(
                    slotId: slot.slotId,
                    courseId: slot.courseId,
                    dayOfWeek: slot.dayOfWeek,
                    dayLabel: slot.dayLabel,
                    startTime: slot.startTime,
                    endTime: slot.endTime,
                    building: hit.building,
                    room: hit.room,
                    shortLocation: shortLoc
                )
                if let ttIdx = timetableSlots.firstIndex(where: { $0.id == slot.slotId }) {
                    timetableSlots[ttIdx] = TimetableSlotCell(
                        id: timetableSlots[ttIdx].id,
                        weekdayShort: timetableSlots[ttIdx].weekdayShort,
                        timeRange: timetableSlots[ttIdx].timeRange,
                        disciplineId: disc.id,
                        disciplineShortName: disc.name,
                        roomLabel: shortLoc,
                        colorKey: disc.colorKey
                    )
                }
                disciplines[discIdx].location = fullLoc
                updatedCount += 1
            }
        }
        return updatedCount
    }

    // MARK: Real Calendar Projection, Next-Class Spotlight & Official Term Banners

    func shiftAgendaWeek(by deltaWeeks: Int) {
        let current = Self.parseIsoDate(selectedDateIso) ?? Date()
        if let next = Calendar(identifier: .gregorian).date(byAdding: .day, value: deltaWeeks * 7, to: current) {
            selectedDateIso = Self.isoString(from: next)
        }
    }

    func jumpAgendaToToday() {
        selectedDateIso = Self.todayIsoDateString()
    }

    func academicTermBanner(for dateIso: String) -> AcademicTermBannerInfo {
        if let exactEvent = calendarEvents.first(where: { $0.eventDate == dateIso }) {
            let isBreak = ["Feriado", "Recesso"].contains(exactEvent.category)
            return AcademicTermBannerInfo(
                badge: exactEvent.category.uppercased(),
                title: exactEvent.title,
                detail: "\(exactEvent.dateBadgeText) · Semestre \(exactEvent.semester)",
                isRecessOrBreak: isBreak
            )
        }
        if dateIso >= "2026-08-24" && dateIso <= "2026-12-23" {
            return AcademicTermBannerInfo(
                badge: "PERÍODO LETIVO",
                title: "Semestre 2026.2 em andamento",
                detail: "Aulas regulares · Calendário acadêmico",
                isRecessOrBreak: false
            )
        }
        if dateIso >= "2026-12-24" && dateIso <= "2027-02-21" {
            return AcademicTermBannerInfo(
                badge: "RECESSO",
                title: "Recesso Acadêmico",
                detail: "Início de 2027.1 em 22/02/2027",
                isRecessOrBreak: true
            )
        }
        if dateIso >= "2027-02-22" && dateIso <= "2027-06-26" {
            return AcademicTermBannerInfo(
                badge: "PERÍODO LETIVO",
                title: "Semestre 2027.1 em andamento",
                detail: "Calendário acadêmico 2027 (ref.)",
                isRecessOrBreak: false
            )
        }
        if dateIso >= "2027-08-02" && dateIso <= "2027-12-11" {
            return AcademicTermBannerInfo(
                badge: "PERÍODO LETIVO",
                title: "Semestre 2027.2 em andamento",
                detail: "Calendário acadêmico 2027 (ref.)",
                isRecessOrBreak: false
            )
        }
        return AcademicTermBannerInfo(
            badge: "CALENDÁRIO",
            title: "Fora do período letivo regular",
            detail: "Consulte a aba Perfil → Calendário para datas de referência",
            isRecessOrBreak: true
        )
    }

    func currentOrNextClassInfo(now: Date = Date()) -> AgendaClassSpotlight? {
        let cal = Calendar(identifier: .gregorian)
        let nowComps = cal.dateComponents([.hour, .minute], from: now)
        let nowMinutes = (nowComps.hour ?? 8) * 60 + (nowComps.minute ?? 0)

        for offset in 0..<7 {
            guard let candidateDate = cal.date(byAdding: .day, value: offset, to: now) else { continue }
            let candidateIso = Self.isoString(from: candidateDate)
            let weekdayIdx = Self.isoWeekdayIndex(from: candidateDate)
            let lectures = timelineItems(for: candidateIso, weekdayIndex: weekdayIdx)
                .filter { $0.kind == .lecture }
                .sorted { $0.startTime < $1.startTime }

            for item in lectures {
                guard let startMin = Self.minutesFromHHMM(item.startTime),
                      let endMin = Self.minutesFromHHMM(item.endTime) else {
                    continue
                }
                if offset == 0 && nowMinutes >= startMin && nowMinutes < endMin {
                    return AgendaClassSpotlight(item: item, dateIso: candidateIso, dayLabel: "Hoje", isOngoing: true)
                }
                if offset > 0 || nowMinutes < startMin {
                    let dayLabel: String = {
                        switch offset {
                        case 0: return "Hoje"
                        case 1: return "Amanhã"
                        default: return Self.weekdayLabel(for: weekdayIdx)
                        }
                    }()
                    return AgendaClassSpotlight(item: item, dateIso: candidateIso, dayLabel: dayLabel, isOngoing: false)
                }
            }
        }
        return nil
    }

    func timelineItems(for dateIso: String, weekdayIndex: Int) -> [AgendaTimelineItem] {
        var result: [AgendaTimelineItem] = []
        let explicit = agendaItemsByDate[dateIso] ?? []

        let activeSem = selectedSemesterFilter
        let semCourseIds = Set(disciplines.filter { $0.semester == activeSem }.map(\.id))

        for slot in courseScheduleSlots where slot.dayOfWeek == weekdayIndex && semCourseIds.contains(slot.courseId) {
            guard let disc = disciplines.first(where: { $0.id == slot.courseId }) else { continue }

            if let seeded = explicit.first(where: { $0.kind == .lecture && $0.disciplineId == slot.courseId && $0.startTime == slot.startTime }) {
                var copy = seeded
                copy.colorKey = disc.colorKey
                copy.attendanceStatus = recordedStatusByAgendaItem[seeded.id] ?? seeded.attendanceStatus
                result.append(copy)
            } else {
                let dynamicId = "lecture-\(slot.slotId)@\(dateIso)"
                let ead = isCourseEad(disc.id)
                let loc: String = {
                    if ead { return "A distância (EAD)" }
                    return slot.room.isEmpty ? slot.building : "\(slot.building) · \(slot.room)"
                }()
                let status = recordedStatusByAgendaItem[dynamicId] ?? .attending
                result.append(
                    AgendaTimelineItem(
                        id: dynamicId,
                        disciplineId: disc.id,
                        kind: .lecture,
                        startTime: slot.startTime,
                        endTime: slot.endTime,
                        title: disc.name,
                        subtitle: loc,
                        location: loc,
                        professor: disc.professor,
                        department: disc.department,
                        colorKey: disc.colorKey,
                        weatherTempC: activeCampus.weatherTempC,
                        weatherCondition: activeCampus.weatherCondition,
                        attendanceStatus: status
                    )
                )
            }
        }

        // Compromissos personalizados salvos na data
        for item in explicit where item.kind == .commitment {
            result.append(item)
        }

        // Itens sem correspondência direta nos slots (fallback seguro)
        for item in explicit where item.kind == .lecture {
            let alreadyAdded = result.contains { $0.disciplineId == item.disciplineId && $0.startTime == item.startTime }
            if !alreadyAdded {
                result.append(item)
            }
        }

        return result.sorted { $0.startTime < $1.startTime }
    }

    // MARK: Official 69-Event UFOP Academic Calendars (2026.2 + 2027.1 + 2027.2)

    func ensureOfficialCalendarEventsLoaded() {
        let seed = Self.officialCalendarEventsSeed()
        let existingIds = Set(calendarEvents.map(\.id))
        let has2027 = calendarEvents.contains { $0.semester == "2027.1" }
        if !has2027 || calendarEvents.count < 60 {
            for ev in seed where !existingIds.contains(ev.id) {
                calendarEvents.append(ev)
            }
            calendarEvents.removeAll { $0.id.hasPrefix("cal-2026-2-") }
            calendarEvents.sort { $0.eventDate < $1.eventDate }
        }
    }

    static func officialCalendarEventsSeed() -> [AcademicCalendarEvent] {
        func e(_ id: String, _ sem: String, _ date: String, _ d: String, _ m: String, _ title: String, _ cat: String) -> AcademicCalendarEvent {
            AcademicCalendarEvent(id: id, semester: sem, eventDate: date, dayNumber: d, monthShort: m, title: title, category: cat)
        }
        return [
            // 2026.2 (24 eventos — Resolução CUNI 2945)
            e("cal_2026_2_ajuste1", "2026.2", "2026-08-14", "14", "AGO", "1º Atestado e 1º Ajuste de Matrícula (14 a 16/08)", "Matrícula"),
            e("cal_2026_2_ajuste2", "2026.2", "2026-08-20", "20", "AGO", "2º Atestado e 2º Ajuste de Matrícula", "Matrícula"),
            e("cal_2026_2_inicio", "2026.2", "2026-08-24", "24", "AGO", "Início das Aulas e do Período Letivo 2026.2 (3º Atestado)", "Semestre"),
            e("cal_2026_2_ajuste3", "2026.2", "2026-08-27", "27", "AGO", "3º Ajuste de Matrículas Online", "Matrícula"),
            e("cal_2026_2_ajuste4", "2026.2", "2026-09-02", "02", "SET", "4º e Último Ajuste de Matrículas Online", "Matrícula"),
            e("cal_2026_2_definitivo", "2026.2", "2026-09-03", "03", "SET", "Divulgação do Atestado Definitivo (4º Ajuste)", "Matrícula"),
            e("cal_2026_2_independencia", "2026.2", "2026-09-07", "07", "SET", "Feriado Nacional: Independência do Brasil", "Feriado"),
            e("cal_2026_2_trancamento_oficial", "2026.2", "2026-09-22", "22", "SET", "25% do Semestre · Último dia para Trancamento (2026.2)", "Prazo"),
            e("cal_2026_2_aparecida", "2026.2", "2026-10-12", "12", "OUT", "Feriado Nacional: Nossa Senhora Aparecida", "Feriado"),
            e("cal_2026_2_saberes_jm", "2026.2", "2026-10-26", "26", "OUT", "Encontro de Saberes e SNCT em João Monlevade (26 e 27/10 · sem avaliações)", "Evento"),
            e("cal_2026_2_servidor", "2026.2", "2026-10-28", "28", "OUT", "Recesso Acadêmico: Dia do Servidor Público", "Recesso"),
            e("cal_2026_2_snct_op", "2026.2", "2026-10-29", "29", "OUT", "Semana Nacional de Ciência e Tecnologia em Ouro Preto (sem avaliações)", "Evento"),
            e("cal_2026_2_finados", "2026.2", "2026-11-02", "02", "NOV", "Feriado Nacional: Dia de Finados", "Feriado"),
            e("cal_2026_2_saberes", "2026.2", "2026-11-10", "10", "NOV", "Encontro de Saberes UFOP (10 a 12/11 · sem avaliações)", "Evento"),
            e("cal_2026_2_dois_tercos", "2026.2", "2026-11-13", "13", "NOV", "Decorridos 2/3 do Semestre Letivo", "Semestre"),
            e("cal_2026_2_republica", "2026.2", "2026-11-15", "15", "NOV", "Feriado Nacional: Proclamação da República", "Feriado"),
            e("cal_2026_2_consciencia", "2026.2", "2026-11-20", "20", "NOV", "Feriado Nacional: Dia da Consciência Negra", "Feriado"),
            e("cal_2026_2_75_pct", "2026.2", "2026-11-24", "24", "NOV", "Decorridos 75% do Semestre Letivo", "Semestre"),
            e("cal_2026_2_conceicao", "2026.2", "2026-12-08", "08", "DEZ", "Dia de Nossa Senhora da Conceição", "Feriado"),
            e("cal_2026_2_exame_bcc701", "2026.2", "2026-12-11", "11", "DEZ", "Exame Especial de BCC701 (Programação de Computadores)", "Avaliação"),
            e("cal_2026_2_exames_oficiais", "2026.2", "2026-12-17", "17", "DEZ", "Período de Exames Especiais (17 a 23/12)", "Avaliação"),
            e("cal_2026_2_termino", "2026.2", "2026-12-23", "23", "DEZ", "Término do Período Letivo 2026.2 e Lançamento de Notas", "Semestre"),
            e("cal_2026_2_recesso", "2026.2", "2026-12-24", "24", "DEZ", "Recesso Acadêmico de Fim de Ano (24 a 31/12)", "Recesso"),
            e("cal_2026_2_alteracao_notas", "2026.2", "2027-01-11", "11", "JAN", "Último dia para Alteração de Notas de 2026.2", "Prazo"),

            // 2027.1 (21 eventos)
            e("cal_2027_1_carnaval", "2027.1", "2027-02-08", "08", "FEV", "Carnaval e Quarta-feira de Cinzas (08 a 10/02 · Feriado/Ponto Facultativo)", "Feriado"),
            e("cal_2027_1_ajuste1", "2027.1", "2027-02-12", "12", "FEV", "1º Atestado e 1º Ajuste de Matrícula (12 a 14/02)", "Matrícula"),
            e("cal_2027_1_ajuste2", "2027.1", "2027-02-18", "18", "FEV", "2º Atestado e 2º Ajuste de Matrícula", "Matrícula"),
            e("cal_2027_1_inicio", "2027.1", "2027-02-22", "22", "FEV", "Início das Aulas e do Período Letivo 2027.1 (3º Atestado)", "Semestre"),
            e("cal_2027_1_ajuste3", "2027.1", "2027-02-26", "26", "FEV", "3º Ajuste de Matrículas Online", "Matrícula"),
            e("cal_2027_1_ajuste4", "2027.1", "2027-03-01", "01", "MAR", "4º Atestado e 4º (Último) Ajuste de Matrículas Online", "Matrícula"),
            e("cal_2027_1_definitivo", "2027.1", "2027-03-02", "02", "MAR", "Divulgação do Atestado Definitivo de Matrícula", "Matrícula"),
            e("cal_2027_1_trancamento", "2027.1", "2027-03-22", "22", "MAR", "25% do Semestre · Último dia para Trancamento (2027.1)", "Prazo"),
            e("cal_2027_1_semana_santa", "2027.1", "2027-03-25", "25", "MAR", "Semana Santa: Quinta, Sexta-feira Santa e Sábado de Aleluia (25 a 27/03)", "Feriado"),
            e("cal_2027_1_tiradentes", "2027.1", "2027-04-21", "21", "ABR", "Feriado Nacional: Dia de Tiradentes", "Feriado"),
            e("cal_2027_1_50_pct", "2027.1", "2027-04-24", "24", "ABR", "Decorridos 50% do Semestre Letivo", "Semestre"),
            e("cal_2027_1_aniv_jm_ip", "2027.1", "2027-04-29", "29", "ABR", "Feriado Municipal em João Monlevade e Ipatinga (sem aulas nesses campi)", "Feriado"),
            e("cal_2027_1_trabalhador", "2027.1", "2027-05-01", "01", "MAI", "Feriado Nacional: Dia do Trabalhador", "Feriado"),
            e("cal_2027_1_dois_tercos", "2027.1", "2027-05-15", "15", "MAI", "Decorridos 2/3 do Semestre Letivo", "Semestre"),
            e("cal_2027_1_aacc", "2027.1", "2027-05-17", "17", "MAI", "Aproveitamento de Carga Horária (AACC/Extensão · 17 a 31/05)", "Prazo"),
            e("cal_2027_1_75_pct", "2027.1", "2027-05-25", "25", "MAI", "Decorridos 75% do Semestre Letivo", "Semestre"),
            e("cal_2027_1_corpus", "2027.1", "2027-05-27", "27", "MAI", "Corpus Christi e Recesso Acadêmico (27 a 29/05)", "Recesso"),
            e("cal_2027_1_termino", "2027.1", "2027-06-26", "26", "JUN", "Término do Período Letivo 2027.1 (e Exame Especial BCC104)", "Semestre"),
            e("cal_2027_1_exames", "2027.1", "2027-06-28", "28", "JUN", "Período de Exames Especiais (28/06 a 03/07)", "Avaliação"),
            e("cal_2027_1_notas_recesso", "2027.1", "2027-07-05", "05", "JUL", "Lançamento de Notas e Início do Recesso Acadêmico (05 a 31/07)", "Recesso"),
            e("cal_2027_1_alteracao_notas", "2027.1", "2027-07-07", "07", "JUL", "Último dia para Alteração de Notas de 2027.1", "Prazo"),

            // 2027.2 (24 eventos)
            e("cal_2027_2_ajuste1", "2027.2", "2027-07-23", "23", "JUL", "1º Atestado e 1º Ajuste de Matrícula (23 a 25/07)", "Matrícula"),
            e("cal_2027_2_ajuste2", "2027.2", "2027-07-29", "29", "JUL", "2º Atestado e 2º Ajuste de Matrícula", "Matrícula"),
            e("cal_2027_2_inicio", "2027.2", "2027-08-02", "02", "AGO", "Início das Aulas e do Período Letivo 2027.2 (3º Atestado)", "Semestre"),
            e("cal_2027_2_ajuste3", "2027.2", "2027-08-05", "05", "AGO", "3º Ajuste de Matrículas Online", "Matrícula"),
            e("cal_2027_2_ajuste4", "2027.2", "2027-08-06", "06", "AGO", "4º Atestado e 4º (Último) Ajuste de Matrículas Online", "Matrícula"),
            e("cal_2027_2_definitivo", "2027.2", "2027-08-09", "09", "AGO", "Divulgação do Atestado Definitivo de Matrícula", "Matrícula"),
            e("cal_2027_2_trancamento", "2027.2", "2027-08-30", "30", "AGO", "25% do Semestre · Último dia para Trancamento (2027.2)", "Prazo"),
            e("cal_2027_2_independencia", "2027.2", "2027-09-06", "06", "SET", "Recesso Acadêmico e Independência do Brasil (06 e 07/09)", "Feriado"),
            e("cal_2027_2_50_pct", "2027.2", "2027-09-30", "30", "SET", "Decorridos 50% do Semestre Letivo", "Semestre"),
            e("cal_2027_2_aparecida", "2027.2", "2027-10-11", "11", "OUT", "Recesso Acadêmico e Nossa Senhora Aparecida (11 e 12/10)", "Feriado"),
            e("cal_2027_2_saberes_jm", "2027.2", "2027-10-19", "19", "OUT", "Encontro de Saberes em João Monlevade (19 e 20/10 · sem avaliações)", "Evento"),
            e("cal_2027_2_dois_tercos", "2027.2", "2027-10-23", "23", "OUT", "Decorridos 2/3 do Semestre Letivo", "Semestre"),
            e("cal_2027_2_snct", "2027.2", "2027-10-26", "26", "OUT", "Semana Nacional de Ciência e Tecnologia (26 e 27/10 · sem avaliações)", "Evento"),
            e("cal_2027_2_servidor", "2027.2", "2027-10-28", "28", "OUT", "Recesso Acadêmico e Dia do Servidor Público (28/10 a 01/11)", "Recesso"),
            e("cal_2027_2_finados", "2027.2", "2027-11-02", "02", "NOV", "Feriado Nacional: Dia de Finados", "Feriado"),
            e("cal_2027_2_75_pct", "2027.2", "2027-11-06", "06", "NOV", "Decorridos 75% do Semestre Letivo", "Semestre"),
            e("cal_2027_2_saberes", "2027.2", "2027-11-09", "09", "NOV", "Encontro de Saberes UFOP (09 a 11/11 · sem avaliações)", "Evento"),
            e("cal_2027_2_republica", "2027.2", "2027-11-15", "15", "NOV", "Feriado Nacional: Proclamação da República", "Feriado"),
            e("cal_2027_2_consciencia", "2027.2", "2027-11-20", "20", "NOV", "Feriado Nacional: Dia da Consciência Negra", "Feriado"),
            e("cal_2027_2_conceicao", "2027.2", "2027-12-08", "08", "DEZ", "Feriado: Dia de Nossa Senhora da Conceição", "Feriado"),
            e("cal_2027_2_termino", "2027.2", "2027-12-11", "11", "DEZ", "Término do Período Letivo 2027.2 (e Exame Especial BCC104)", "Semestre"),
            e("cal_2027_2_exames", "2027.2", "2027-12-13", "13", "DEZ", "Período de Exames Especiais (13 a 18/12)", "Avaliação"),
            e("cal_2027_2_recesso_notas", "2027.2", "2027-12-20", "20", "DEZ", "Início do Recesso Acadêmico e Último dia para Lançamento de Notas", "Recesso"),
            e("cal_2027_2_alteracao_notas", "2027.2", "2027-12-21", "21", "DEZ", "Último dia para Alteração de Notas de 2027.2", "Prazo")
        ]
    }

    // MARK: Calendar & Date Helpers

    private static let isoDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private static let portugueseMonthYearFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "pt_BR")
        formatter.dateFormat = "MMMM yyyy"
        return formatter
    }()

    static func todayIsoDateString() -> String {
        isoString(from: Date())
    }

    static func isoString(from date: Date) -> String {
        isoDateFormatter.string(from: date)
    }

    static func parseIsoDate(_ iso: String) -> Date? {
        isoDateFormatter.date(from: iso.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    static func mondayToSundayDates(containing date: Date) -> [Date] {
        let cal = Calendar(identifier: .gregorian)
        let idx = isoWeekdayIndex(from: date) // 1=Mon .. 7=Sun
        guard let monday = cal.date(byAdding: .day, value: -(idx - 1), to: cal.startOfDay(for: date)) else {
            return [date]
        }
        return (0..<7).compactMap { cal.date(byAdding: .day, value: $0, to: monday) }
    }

    static func isoWeekdayIndex(from date: Date) -> Int {
        let weekday = Calendar(identifier: .gregorian).component(.weekday, from: date) // 1=Sun, 2=Mon .. 7=Sat
        return weekday == 1 ? 7 : (weekday - 1)
    }

    static func dayNumberString(from date: Date) -> String {
        let day = Calendar(identifier: .gregorian).component(.day, from: date)
        return String(format: "%02d", day)
    }

    static func portugueseMonthYearLabel(from date: Date) -> String {
        let raw = portugueseMonthYearFormatter.string(from: date)
        return raw.prefix(1).uppercased() + raw.dropFirst()
    }

    static func weekdayLabel(for weekday: Int) -> String {
        switch weekday {
        case 1: return "Segunda"
        case 2: return "Terça"
        case 3: return "Quarta"
        case 4: return "Quinta"
        case 5: return "Sexta"
        case 6: return "Sábado"
        default: return "Domingo"
        }
    }

    private static func minutesFromHHMM(_ text: String) -> Int? {
        let parts = text.split(separator: ":")
        guard parts.count == 2, let h = Int(parts[0]), let m = Int(parts[1]) else { return nil }
        return h * 60 + m
    }

    private static func normalizeIsoDateInput(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if parseIsoDate(trimmed) != nil { return trimmed }
        let slashParts = trimmed.split(separator: "/").map(String.init)
        if slashParts.count >= 2, let d = Int(slashParts[0]), let m = Int(slashParts[1]) {
            let y = slashParts.count >= 3 ? (Int(slashParts[2]) ?? 2026) : 2026
            let fullY = y < 100 ? 2000 + y : y
            return String(format: "%04d-%02d-%02d", fullY, m, d)
        }
        return "2026-11-25"
    }

    private static func dayAndMonthBadge(fromIso iso: String) -> (String, String) {
        let months = ["01": "JAN", "02": "FEV", "03": "MAR", "04": "ABR", "05": "MAI", "06": "JUN",
                      "07": "JUL", "08": "AGO", "09": "SET", "10": "OUT", "11": "NOV", "12": "DEZ"]
        let parts = iso.split(separator: "-").map(String.init)
        guard parts.count == 3 else { return ("25", "NOV") }
        return (parts[2], months[parts[1]] ?? "NOV")
    }
}
