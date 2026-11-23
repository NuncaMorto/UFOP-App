import Foundation

// MARK: - Disciplines, Weekly Slots & UFOP Templates

public extension CampusStore {
    // MARK: - Queries & Commands (Disciplinas & Grade)

    public static func calculateMaxAbsences(workloadHours: Int) -> Int {
        max(1, (max(0, workloadHours) * 3) / 10)
    }

    public var availableDepartments: [String] {
        var result = ["Todos os departamentos", "Matemática", "Computação", "Física"]
        for disc in disciplines where !result.contains(disc.department) {
            result.append(disc.department)
        }
        return result
    }

    public var availableSemesters: [String] {
        var result = ["2026.2", "2026.1"]
        if !result.contains(selectedSemesterFilter) {
            result.append(selectedSemesterFilter)
        }
        for disc in disciplines where !result.contains(disc.semester) {
            result.append(disc.semester)
        }
        return result
    }

    public var filteredDisciplines: [DisciplineSummaryCard] {
        disciplines.filter { card in
            let matchesDept = selectedDepartmentFilter == "Todos os departamentos"
                || card.department.localizedCaseInsensitiveContains(selectedDepartmentFilter)
            let matchesSemester = card.semester == selectedSemesterFilter
            return matchesDept && matchesSemester
        }
    }

    public var totalAbsencesInSemester: Int {
        disciplines
            .filter { $0.semester == selectedSemesterFilter }
            .reduce(0) { $0 + $1.currentAbsences }
    }

    public var totalMaxAbsencesInSemester: Int {
        disciplines
            .filter { $0.semester == selectedSemesterFilter }
            .reduce(0) { $0 + $1.maxAbsences }
    }

    public var totalCancelledInSemester: Int {
        disciplines
            .filter { $0.semester == selectedSemesterFilter }
            .reduce(0) { $0 + $1.cancelledClasses }
    }

    public var coursesAtRiskCount: Int {
        disciplines
            .filter { $0.semester == selectedSemesterFilter && $0.absenceProgress >= 0.75 }
            .count
    }

    public var pendingAssessmentsCount: Int {
        academicActivities.filter { !$0.isCompleted }.count
    }

    public var completedAssessmentsCount: Int {
        academicActivities.filter { $0.isCompleted }.count
    }

    public func getDisciplineDetail(disciplineId: String) -> DisciplineDetail? {
        guard let summary = disciplines.first(where: { $0.id == disciplineId }) else {
            return nil
        }
        let assessments = assessmentsByDiscipline[disciplineId] ?? []
        let notes = notesByDiscipline[disciplineId] ?? ""
        return DisciplineDetail(
            summary: summary,
            assessments: assessments,
            notes: notes
        )
    }

    public func updateDisciplineNotes(disciplineId: String, notes: String) {
        notesByDiscipline[disciplineId] = notes
        persistEncryptedState()
    }

    public func updateDisciplineColor(disciplineId: String, colorKey: DepartmentColorKey) {
        if let idx = disciplines.firstIndex(where: { $0.id == disciplineId }) {
            disciplines[idx].colorKey = colorKey
        }
        for (dateIso, var items) in agendaItemsByDate {
            var changed = false
            for i in items.indices where items[i].disciplineId == disciplineId {
                items[i].colorKey = colorKey
                changed = true
            }
            if changed {
                agendaItemsByDate[dateIso] = items
            }
        }
        for i in timetableSlots.indices where timetableSlots[i].disciplineId == disciplineId {
            timetableSlots[i].colorKey = colorKey
        }
        for i in academicActivities.indices where academicActivities[i].disciplineId == disciplineId {
            academicActivities[i].colorKey = colorKey
        }
    }

    // MARK: - Sprint 3: Courses, Schedule Slots & UFOP Curriculum Templates

    /// Cria uma nova disciplina no semestre, calculando `maxAbsences = (workloadHours * 3) / 10` (`30h -> 9`, `60h -> 18`, `90h -> 27`).
    @discardableResult
    public func createCourse(
        semester: String,
        departmentId: String,
        name: String,
        professor: String,
        workloadHours: Int,
        colorOverrideHex: String? = nil
    ) -> DisciplineSummaryCard {
        let cleanSemester = semester.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? selectedSemesterFilter
            : semester.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanProfessor = professor.trimmingCharacters(in: .whitespacesAndNewlines)

        let deptMeta = Self.resolveDepartmentMetadata(from: departmentId)
        let resolvedColorKey: DepartmentColorKey = {
            if let overrideHex = colorOverrideHex?.trimmingCharacters(in: .whitespacesAndNewlines),
               !overrideHex.isEmpty {
                return DepartmentColorKey.fromHex(overrideHex)
            }
            return deptMeta.defaultColorKey
        }()

        let validWorkload = workloadHours > 0 ? workloadHours : 60
        let maxAbsences = Self.calculateMaxAbsences(workloadHours: validWorkload)
        let generatedId = "\(deptMeta.id)-\(UUID().uuidString.prefix(8).lowercased())"
        let generatedCode = "\(deptMeta.codePrefix)\(100 + (disciplines.count * 17) % 899)"

        let card = DisciplineSummaryCard(
            id: generatedId,
            code: generatedCode,
            name: cleanName.isEmpty ? "Nova disciplina" : cleanName,
            department: deptMeta.label,
            semester: cleanSemester,
            workloadHours: validWorkload,
            currentAbsences: 0,
            maxAbsences: maxAbsences,
            cancelledClasses: 0,
            colorKey: resolvedColorKey,
            professor: cleanProfessor,
            location: "Campus UFOP"
        )

        disciplines.append(card)
        assessmentsByDiscipline[card.id] = []
        notesByDiscipline[card.id] = ""
        activeTemplateId = nil
        rebuildAcademicActivitiesFromAssessments()
        return card
    }

    /// Remove uma disciplina, seus horários semanais, aulas na agenda e avaliações associadas.
    public func deleteCourse(courseId: String) {
        disciplines.removeAll { $0.id == courseId }
        courseScheduleSlots.removeAll { $0.courseId == courseId }
        timetableSlots.removeAll { $0.disciplineId == courseId }

        for (dateIso, items) in agendaItemsByDate {
            let removedIds = items.filter { $0.disciplineId == courseId && $0.kind == .lecture }.map(\.id)
            if !removedIds.isEmpty {
                agendaItemsByDate[dateIso] = items.filter { !($0.disciplineId == courseId && $0.kind == .lecture) }
                for id in removedIds {
                    recordedStatusByAgendaItem.removeValue(forKey: id)
                    absenceHistoryBySession.removeValue(forKey: id)
                }
            }
        }

        absenceHistoryBySession = absenceHistoryBySession.filter { $0.value.courseId != courseId }
        assessmentsByDiscipline.removeValue(forKey: courseId)
        notesByDiscipline.removeValue(forKey: courseId)
        activeTemplateId = nil
        rebuildAcademicActivitiesFromAssessments()
        persistEncryptedState()
    }

    /// Adiciona um horário semanal para a disciplina, detectando conflitos no mesmo `dayOfWeek` e `startTime` (ou sobreposição) no semestre ativo.
    @discardableResult
    public func addScheduleSlot(
        courseId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ) throws -> CourseScheduleSlotItem {
        guard (1...7).contains(dayOfWeek) else {
            throw CampusScheduleError.invalidDayOfWeek(dayOfWeek)
        }
        guard let courseIndex = disciplines.firstIndex(where: { $0.id == courseId }) else {
            throw CampusScheduleError.courseNotFound(courseId)
        }

        let course = disciplines[courseIndex]
        let dayInfo = Self.weekdayMetadata(for: dayOfWeek)
        let normalizedStart = Self.normalizeTimeString(startTime)
        let normalizedEnd = Self.normalizeTimeString(endTime)

        guard !normalizedStart.isEmpty else {
            throw CampusScheduleError.emptyField("Horário de início")
        }
        guard !normalizedEnd.isEmpty else {
            throw CampusScheduleError.emptyField("Horário de término")
        }

        guard let startMin = Self.minutesFromTime(normalizedStart),
              let endMin = Self.minutesFromTime(normalizedEnd),
              endMin > startMin else {
            throw CampusScheduleError.invalidTimeRange(normalizedStart, normalizedEnd)
        }

        // Verifica conflito de horário no mesmo dia da semana e semestre
        let semesterCourseIds = Set(disciplines.filter { $0.semester == course.semester }.map(\.id))
        for existing in courseScheduleSlots where semesterCourseIds.contains(existing.courseId) && existing.dayOfWeek == dayOfWeek {
            let isSameStart = existing.startTime == normalizedStart
            let isOverlapping = Self.timeRangesOverlap(
                startA: normalizedStart,
                endA: normalizedEnd,
                startB: existing.startTime,
                endB: existing.endTime
            )
            if isSameStart || isOverlapping {
                let conflictCourseName = disciplines.first(where: { $0.id == existing.courseId })?.name ?? "outra disciplina"
                throw CampusScheduleError.scheduleConflict(
                    existingCourseName: conflictCourseName,
                    dayLabel: dayInfo.shortLabel,
                    startTime: existing.startTime
                )
            }
        }

        let cleanBuilding = building.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Campus UFOP"
            : building.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanRoom = room.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedShort = shortLocation.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedShort = trimmedShort.isEmpty
            ? Self.buildShortLocation(building: cleanBuilding, room: cleanRoom)
            : trimmedShort

        let slotId = "slot-\(dayInfo.slug)-\(normalizedStart.replacingOccurrences(of: ":", with: ""))-\(UUID().uuidString.prefix(6).lowercased())"
        let slot = CourseScheduleSlotItem(
            slotId: slotId,
            courseId: courseId,
            dayOfWeek: dayOfWeek,
            dayLabel: dayInfo.shortLabel,
            startTime: normalizedStart,
            endTime: normalizedEnd,
            building: cleanBuilding,
            room: cleanRoom,
            shortLocation: resolvedShort
        )

        courseScheduleSlots.append(slot)

        let fullSlotLocation = cleanRoom.isEmpty ? cleanBuilding : "\(cleanBuilding) · \(cleanRoom)"
        if course.location == "Campus UFOP" || course.location.isEmpty {
            disciplines[courseIndex].location = fullSlotLocation
        }

        timetableSlots.append(
            TimetableSlotCell(
                id: slotId,
                weekdayShort: dayInfo.shortLabel,
                timeRange: slot.timeRange,
                disciplineId: course.id,
                disciplineShortName: course.name,
                roomLabel: resolvedShort,
                colorKey: course.colorKey
            )
        )

        if course.semester == selectedSemesterFilter || course.semester == "2026.2" {
            let lectureItem = AgendaTimelineItem(
                id: "lecture-\(slotId)",
                disciplineId: course.id,
                kind: .lecture,
                startTime: normalizedStart,
                endTime: normalizedEnd,
                title: course.name,
                subtitle: fullSlotLocation,
                location: fullSlotLocation,
                professor: course.professor,
                department: course.department,
                colorKey: course.colorKey,
                weatherTempC: activeCampus.weatherTempC,
                weatherCondition: activeCampus.weatherCondition,
                attendanceStatus: .attending
            )
            var dayItems = agendaItemsByDate[dayInfo.isoDate] ?? []
            dayItems.append(lectureItem)
            dayItems.sort { $0.startTime < $1.startTime }
            agendaItemsByDate[dayInfo.isoDate] = dayItems
            recordedStatusByAgendaItem[lectureItem.id] = .attending
        }

        persistEncryptedState()
        return slot
    }

    /// Remove um horário semanal da disciplina e atualiza a grade e a agenda.
    public func deleteScheduleSlot(slotId: String) {
        guard let idx = courseScheduleSlots.firstIndex(where: { $0.slotId == slotId }) else {
            timetableSlots.removeAll { $0.id == slotId }
            return
        }

        let removed = courseScheduleSlots.remove(at: idx)
        timetableSlots.removeAll { $0.id == slotId }

        let isoDate = Self.weekdayMetadata(for: removed.dayOfWeek).isoDate
        if var dayItems = agendaItemsByDate[isoDate] {
            let matchingIds = dayItems.filter {
                $0.id == "lecture-\(slotId)"
                    || ($0.kind == .lecture && $0.disciplineId == removed.courseId && $0.startTime == removed.startTime)
            }.map(\.id)

            if !matchingIds.isEmpty {
                dayItems.removeAll { matchingIds.contains($0.id) }
                agendaItemsByDate[isoDate] = dayItems
                for id in matchingIds {
                    recordedStatusByAgendaItem.removeValue(forKey: id)
                    absenceHistoryBySession.removeValue(forKey: id)
                }
            }
        }
        persistEncryptedState()
    }

    /// Lista os horários semanais cadastrados para uma disciplina em ordem cronológica.
    public func listCourseSlots(courseId: String) -> [CourseScheduleSlotItem] {
        courseScheduleSlots
            .filter { $0.courseId == courseId }
            .sorted { lhs, rhs in
                if lhs.dayOfWeek != rhs.dayOfWeek {
                    return lhs.dayOfWeek < rhs.dayOfWeek
                }
                return lhs.startTime < rhs.startTime
            }
    }

    /// Lista os templates curriculares oficiais da UFOP (`BCC · DECOM`, `ENGC · DECSI`, `CICLO BÁSICO · EM`).
    public func listUfopTemplates() -> [UfopCurriculumTemplate] {
        Self.ufopTemplateCatalog.map { blueprint in
            UfopCurriculumTemplate(
                id: blueprint.id,
                code: blueprint.code,
                name: blueprint.name,
                departmentName: blueprint.departmentName,
                campusName: blueprint.campusName,
                description: blueprint.description,
                coursesCount: blueprint.courses.count
            )
        }
    }

    /// Aplica um template oficial de curso da UFOP no semestre, atualizando disciplinas, horários, agenda e grade reativamente.
    @discardableResult
    public func applyUfopTemplate(templateId: String, semester: String = "2026.2") -> Int {
        guard let blueprint = Self.ufopTemplateCatalog.first(where: { $0.id == templateId }) else {
            return 0
        }

        let cleanSemester = semester.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? selectedSemesterFilter
            : semester.trimmingCharacters(in: .whitespacesAndNewlines)

        // Remove disciplinas anteriores do semestre alvo (preservando compromissos customizados)
        let existingIdsInSemester = disciplines
            .filter { $0.semester == cleanSemester }
            .map(\.id)
        for existingId in existingIdsInSemester {
            deleteCourse(courseId: existingId)
        }
        // Limpa eventual slot de monitoria residual do seed anterior no semestre selecionado
        if cleanSemester == selectedSemesterFilter {
            let validCourseIds = Set(disciplines.map(\.id))
            timetableSlots.removeAll { !validCourseIds.contains($0.disciplineId) || $0.id == "slot-ter-14" }
        }

        selectedSemesterFilter = cleanSemester
        selectedDepartmentFilter = "Todos os departamentos"
        selectedTimetableFilter = "Minha grade"

        var appliedCount = 0
        for templateCourse in blueprint.courses {
            let deptMeta = Self.resolveDepartmentMetadata(from: templateCourse.departmentId)
            let maxAbsences = Self.calculateMaxAbsences(workloadHours: templateCourse.workloadHours)
            let courseId = "\(blueprint.id)-\(templateCourse.code.lowercased())"

            let card = DisciplineSummaryCard(
                id: courseId,
                code: templateCourse.code,
                name: templateCourse.name,
                department: deptMeta.label,
                semester: cleanSemester,
                workloadHours: templateCourse.workloadHours,
                currentAbsences: 0,
                maxAbsences: maxAbsences,
                cancelledClasses: 0,
                colorKey: templateCourse.colorKey,
                professor: templateCourse.professor,
                location: templateCourse.location
            )

            disciplines.append(card)
            assessmentsByDiscipline[courseId] = []
            notesByDiscipline[courseId] = "Grade sugerida \(blueprint.code) · \(blueprint.campusName)."

            for slotSpec in templateCourse.slots {
                _ = try? addScheduleSlot(
                    courseId: courseId,
                    dayOfWeek: slotSpec.dayOfWeek,
                    startTime: slotSpec.startTime,
                    endTime: slotSpec.endTime,
                    building: slotSpec.building,
                    room: slotSpec.room,
                    shortLocation: slotSpec.shortLocation
                )
            }

            appliedCount += 1
        }

        activeTemplateId = blueprint.id
        rebuildAcademicActivitiesFromAssessments()
        persistEncryptedState()
        return appliedCount
    }

    public var currentWeeklyTimetable: WeeklyTimetable {
        let semesterDisciplines = disciplines.filter { $0.semester == selectedSemesterFilter }
        let semesterDisciplineIds = Set(semesterDisciplines.map(\.id))
        let semesterSlots = timetableSlots.filter { semesterDisciplineIds.contains($0.disciplineId) }

        var filters = ["Minha grade"]
        for dept in ["Matemática", "Computação", "Física"] {
            if semesterDisciplines.contains(where: { $0.department == dept }) {
                filters.append(dept)
            }
        }
        for disc in semesterDisciplines where !filters.contains(disc.department) {
            filters.append(disc.department)
        }

        let effectiveFilter = filters.contains(selectedTimetableFilter) ? selectedTimetableFilter : "Minha grade"
        let filteredSlots: [TimetableSlotCell]
        if effectiveFilter == "Minha grade" {
            filteredSlots = semesterSlots
        } else {
            let matchingIds = Set(
                semesterDisciplines
                    .filter { $0.department == effectiveFilter }
                    .map(\.id)
            )
            filteredSlots = semesterSlots.filter { matchingIds.contains($0.disciplineId) }
        }

        let baseTimeRanges = ["08:00–09:40", "10:00–11:40", "14:00–15:00"]
        let slotTimeRanges = Set(semesterSlots.map(\.timeRange))
        let mergedTimeRanges = Array(Set(baseTimeRanges).union(slotTimeRanges)).sorted { lhs, rhs in
            Self.minutesFromTimeRangeStart(lhs) < Self.minutesFromTimeRangeStart(rhs)
        }

        return WeeklyTimetable(
            title: "Grade de horários",
            subtitle: "Semana de aulas · \(selectedSemesterFilter)",
            semester: selectedSemesterFilter,
            selectedFilter: effectiveFilter,
            availableFilters: filters,
            weekdays: ["SEG", "TER", "QUA", "QUI", "SEX", "SÁB"],
            timeRanges: mergedTimeRanges,
            slots: filteredSlots
        )
    }

    /// Gera as entradas da legenda da grade sincronizadas com os departamentos e cores das disciplinas exibidas.
    public func timetableLegendEntries(for slots: [TimetableSlotCell]) -> [TimetableLegendEntry] {
        let semesterDisciplines = disciplines.filter { $0.semester == selectedSemesterFilter }
        let visibleIds = Set(slots.map(\.disciplineId))
        let sourceDisciplines = visibleIds.isEmpty
            ? semesterDisciplines
            : semesterDisciplines.filter { visibleIds.contains($0.id) }

        guard !sourceDisciplines.isEmpty else {
            return DepartmentColorKey.allCases.map { key in
                TimetableLegendEntry(
                    id: key.rawValue,
                    colorKey: key,
                    title: key.departmentLabel,
                    subtitle: "Sem disciplinas"
                )
            }
        }

        return DepartmentColorKey.allCases.compactMap { key in
            let matching = sourceDisciplines.filter { $0.colorKey == key }
            guard !matching.isEmpty else { return nil }

            let primaryDepartment = matching.first?.department ?? key.departmentLabel
            let canonicalColor = Self.resolveDepartmentMetadata(from: primaryDepartment).defaultColorKey
            let departmentHasMultipleColors = sourceDisciplines.contains {
                $0.department == primaryDepartment && $0.colorKey != key
            }

            let title: String
            if departmentHasMultipleColors && key != canonicalColor {
                title = matching.map(\.name).joined(separator: " · ")
            } else {
                title = primaryDepartment
            }

            let subtitle = matching.map(\.name).joined(separator: " · ")
            return TimetableLegendEntry(
                id: key.rawValue,
                colorKey: key,
                title: title,
                subtitle: subtitle
            )
        }
    }

    // MARK: - Private Sprint 3 Helpers & Official UFOP Template Catalog

    private struct TemplateSlotBlueprint {
        let dayOfWeek: Int
        let startTime: String
        let endTime: String
        let building: String
        let room: String
        let shortLocation: String
    }

    private struct TemplateCourseBlueprint {
        let code: String
        let name: String
        let departmentId: String
        let colorKey: DepartmentColorKey
        let workloadHours: Int
        let professor: String
        let location: String
        let slots: [TemplateSlotBlueprint]
    }

    private struct UfopTemplateBlueprint {
        let id: String
        let code: String
        let name: String
        let departmentName: String
        let campusName: String
        let description: String
        let courses: [TemplateCourseBlueprint]
    }

    private static let ufopTemplateCatalog: [UfopTemplateBlueprint] = [
        UfopTemplateBlueprint(
            id: "tpl_bcc_decom",
            code: "BCC · DECOM",
            name: "Ciência da Computação",
            departmentName: "DECOM",
            campusName: "Morro do Cruzeiro · Ouro Preto",
            description: "Grade base com Programação, Estruturas de Dados, Cálculo e Física.",
            courses: [
                TemplateCourseBlueprint(
                    code: "BCC201",
                    name: "BCC201 Programação I",
                    departmentId: "comp",
                    colorKey: .compPurple,
                    workloadHours: 60,
                    professor: "Prof. Rafael Lima",
                    location: "ICEB · Sala 102",
                    slots: [
                        TemplateSlotBlueprint(dayOfWeek: 1, startTime: "08:00", endTime: "09:40", building: "ICEB", room: "Sala 102", shortLocation: "ICEB · 102"),
                        TemplateSlotBlueprint(dayOfWeek: 3, startTime: "08:00", endTime: "09:40", building: "ICEB", room: "Sala 102", shortLocation: "ICEB · 102")
                    ]
                ),
                TemplateCourseBlueprint(
                    code: "BCC202",
                    name: "BCC202 Estruturas de Dados I",
                    departmentId: "comp",
                    colorKey: .algebraGreen,
                    workloadHours: 60,
                    professor: "Prof.ª Camila Souza",
                    location: "ICEB · Laboratório 03",
                    slots: [
                        TemplateSlotBlueprint(dayOfWeek: 2, startTime: "10:00", endTime: "11:40", building: "ICEB", room: "Laboratório 03", shortLocation: "ICEB · Lab 03"),
                        TemplateSlotBlueprint(dayOfWeek: 4, startTime: "10:00", endTime: "11:40", building: "ICEB", room: "Laboratório 03", shortLocation: "ICEB · Lab 03")
                    ]
                ),
                TemplateCourseBlueprint(
                    code: "MTM122",
                    name: "MTM122 Cálculo Diferencial e Integral I",
                    departmentId: "math",
                    colorKey: .mathBlue,
                    workloadHours: 90,
                    professor: "Prof.ª Ana Martins",
                    location: "Prédio A · Sala 204",
                    slots: [
                        TemplateSlotBlueprint(dayOfWeek: 2, startTime: "08:00", endTime: "09:40", building: "Prédio A", room: "Sala 204", shortLocation: "A · 204"),
                        TemplateSlotBlueprint(dayOfWeek: 4, startTime: "08:00", endTime: "09:40", building: "Prédio A", room: "Sala 204", shortLocation: "A · 204"),
                        TemplateSlotBlueprint(dayOfWeek: 5, startTime: "08:00", endTime: "09:40", building: "Prédio A", room: "Sala 204", shortLocation: "A · 204")
                    ]
                ),
                TemplateCourseBlueprint(
                    code: "FIS105",
                    name: "FIS105 Física I",
                    departmentId: "phys",
                    colorKey: .physicsAmber,
                    workloadHours: 60,
                    professor: "Prof.ª Helena Costa",
                    location: "Prédio D · Laboratório 01",
                    slots: [
                        TemplateSlotBlueprint(dayOfWeek: 1, startTime: "10:00", endTime: "11:40", building: "Prédio D", room: "Laboratório 01", shortLocation: "D · Lab 01"),
                        TemplateSlotBlueprint(dayOfWeek: 3, startTime: "14:00", endTime: "15:00", building: "Prédio D", room: "Laboratório 01", shortLocation: "D · Lab 01")
                    ]
                )
            ]
        ),
        UfopTemplateBlueprint(
            id: "tpl_ec_decsi",
            code: "ENGC · DECSI",
            name: "Engenharia de Computação",
            departmentName: "DECSI",
            campusName: "ICEA · João Monlevade",
            description: "Grade do ICEA com Circuitos Digitais, Algoritmos e Cálculo.",
            courses: [
                TemplateCourseBlueprint(
                    code: "CSI102",
                    name: "CSI102 Programação II",
                    departmentId: "comp",
                    colorKey: .compPurple,
                    workloadHours: 60,
                    professor: "Prof. Bruno Oliveira",
                    location: "ICEA · Laboratório 02",
                    slots: [
                        TemplateSlotBlueprint(dayOfWeek: 1, startTime: "08:00", endTime: "09:40", building: "ICEA", room: "Laboratório 02", shortLocation: "ICEA · Lab 02"),
                        TemplateSlotBlueprint(dayOfWeek: 3, startTime: "08:00", endTime: "09:40", building: "ICEA", room: "Laboratório 02", shortLocation: "ICEA · Lab 02")
                    ]
                ),
                TemplateCourseBlueprint(
                    code: "CSI203",
                    name: "CSI203 Circuitos Digitais",
                    departmentId: "comp",
                    colorKey: .algebraGreen,
                    workloadHours: 60,
                    professor: "Prof. Eduardo Mendes",
                    location: "ICEA · Sala B12",
                    slots: [
                        TemplateSlotBlueprint(dayOfWeek: 2, startTime: "10:00", endTime: "11:40", building: "ICEA", room: "Sala B12", shortLocation: "ICEA · B12"),
                        TemplateSlotBlueprint(dayOfWeek: 4, startTime: "10:00", endTime: "11:40", building: "ICEA", room: "Sala B12", shortLocation: "ICEA · B12")
                    ]
                ),
                TemplateCourseBlueprint(
                    code: "CEA101",
                    name: "CEA101 Cálculo I",
                    departmentId: "math",
                    colorKey: .mathBlue,
                    workloadHours: 90,
                    professor: "Prof.ª Fernanda Rocha",
                    location: "ICEA · Sala A04",
                    slots: [
                        TemplateSlotBlueprint(dayOfWeek: 2, startTime: "08:00", endTime: "09:40", building: "ICEA", room: "Sala A04", shortLocation: "ICEA · A04"),
                        TemplateSlotBlueprint(dayOfWeek: 4, startTime: "08:00", endTime: "09:40", building: "ICEA", room: "Sala A04", shortLocation: "ICEA · A04"),
                        TemplateSlotBlueprint(dayOfWeek: 5, startTime: "08:00", endTime: "09:40", building: "ICEA", room: "Sala A04", shortLocation: "ICEA · A04")
                    ]
                ),
                TemplateCourseBlueprint(
                    code: "CEA204",
                    name: "CEA204 Física Experimental I",
                    departmentId: "phys",
                    colorKey: .physicsAmber,
                    workloadHours: 30,
                    professor: "Prof. Lucas Ferreira",
                    location: "ICEA · Lab Física",
                    slots: [
                        TemplateSlotBlueprint(dayOfWeek: 1, startTime: "10:00", endTime: "11:40", building: "ICEA", room: "Lab Física", shortLocation: "ICEA · Lab Fís"),
                        TemplateSlotBlueprint(dayOfWeek: 3, startTime: "14:00", endTime: "15:00", building: "ICEA", room: "Lab Física", shortLocation: "ICEA · Lab Fís")
                    ]
                )
            ]
        ),
        UfopTemplateBlueprint(
            id: "tpl_eng_basico",
            code: "CICLO BÁSICO · EM",
            name: "Engenharias (Ciclo Básico)",
            departmentName: "Escola de Minas",
            campusName: "Morro do Cruzeiro · Ouro Preto",
            description: "Núcleo comum da Escola de Minas: Cálculo, Álgebra Linear, Física e Computação.",
            courses: [
                TemplateCourseBlueprint(
                    code: "MTM122",
                    name: "MTM122 Cálculo I",
                    departmentId: "math",
                    colorKey: .mathBlue,
                    workloadHours: 90,
                    professor: "Prof.ª Ana Martins",
                    location: "Escola de Minas · Sala 101",
                    slots: [
                        TemplateSlotBlueprint(dayOfWeek: 1, startTime: "08:00", endTime: "09:40", building: "Escola de Minas", room: "Sala 101", shortLocation: "EM · 101"),
                        TemplateSlotBlueprint(dayOfWeek: 3, startTime: "08:00", endTime: "09:40", building: "Escola de Minas", room: "Sala 101", shortLocation: "EM · 101"),
                        TemplateSlotBlueprint(dayOfWeek: 5, startTime: "08:00", endTime: "09:40", building: "Escola de Minas", room: "Sala 101", shortLocation: "EM · 101")
                    ]
                ),
                TemplateCourseBlueprint(
                    code: "MTM112",
                    name: "MTM112 Álgebra Linear",
                    departmentId: "math",
                    colorKey: .algebraGreen,
                    workloadHours: 60,
                    professor: "Prof. Marcos Vieira",
                    location: "Prédio B · Sala 105",
                    slots: [
                        TemplateSlotBlueprint(dayOfWeek: 2, startTime: "08:00", endTime: "09:40", building: "Prédio B", room: "Sala 105", shortLocation: "B · 105"),
                        TemplateSlotBlueprint(dayOfWeek: 4, startTime: "08:00", endTime: "09:40", building: "Prédio B", room: "Sala 105", shortLocation: "B · 105")
                    ]
                ),
                TemplateCourseBlueprint(
                    code: "FIS105",
                    name: "FIS105 Física I",
                    departmentId: "phys",
                    colorKey: .physicsAmber,
                    workloadHours: 60,
                    professor: "Prof.ª Helena Costa",
                    location: "Prédio D · Sala 02",
                    slots: [
                        TemplateSlotBlueprint(dayOfWeek: 1, startTime: "10:00", endTime: "11:40", building: "Prédio D", room: "Sala 02", shortLocation: "D · 02"),
                        TemplateSlotBlueprint(dayOfWeek: 3, startTime: "10:00", endTime: "11:40", building: "Prédio D", room: "Sala 02", shortLocation: "D · 02")
                    ]
                ),
                TemplateCourseBlueprint(
                    code: "BCC701",
                    name: "BCC701 Programação de Computadores",
                    departmentId: "comp",
                    colorKey: .compPurple,
                    workloadHours: 60,
                    professor: "Prof. Rafael Lima",
                    location: "ICEB · Laboratório 01",
                    slots: [
                        TemplateSlotBlueprint(dayOfWeek: 2, startTime: "10:00", endTime: "11:40", building: "ICEB", room: "Laboratório 01", shortLocation: "ICEB · Lab 01"),
                        TemplateSlotBlueprint(dayOfWeek: 4, startTime: "14:00", endTime: "15:00", building: "ICEB", room: "Laboratório 01", shortLocation: "ICEB · Lab 01")
                    ]
                )
            ]
        )
    ]

}
