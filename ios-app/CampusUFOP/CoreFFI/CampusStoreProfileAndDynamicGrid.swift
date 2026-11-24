import Foundation

// MARK: - Multi-Campus, Dynamic Curriculum Grid & Grade Share

private struct RustCrossPlatformCoursePayload: Codable {
    let id: String
    let code: String
    let name: String
    let department_name: String?
    let semester: String?
    let workload_hours: Int?
    let current_absences: Int?
    let max_absences: Int?
    let cancelled_classes: Int?
    let color_hex: String?
    let professor: String?
    let location: String?
}

private struct RustCrossPlatformSchedulePayload: Codable {
    let semester: String?
    let courses: [RustCrossPlatformCoursePayload]?
}


public extension CampusStore {
    // MARK: - Queries & Commands (Multi-Campus UFOP, Calendário & Export/Import JSON)

    public var activeCampus: UfopCampusInfo {
        campuses.first(where: { $0.isActive }) ?? UfopCampusInfo(
            id: "op_cruzeiro",
            name: "Morro do Cruzeiro",
            city: "Ouro Preto",
            weatherTempC: 22,
            weatherCondition: "Ensolarado",
            isActive: true
        )
    }

    /// Alterna o campus ativo entre `Ouro Preto · Morro do Cruzeiro`, `Mariana · ICHS/ICSA` e `João Monlevade · ICEA`.
    @discardableResult
    public func setActiveCampus(campusId: String) -> UfopCampusInfo {
        let normalized = campusId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

        let matchedIndex = campuses.firstIndex { campus in
            campus.id.lowercased() == normalized
                || campus.city.lowercased() == normalized
                || campus.fullLabel.lowercased() == normalized
        } ?? 0

        for idx in campuses.indices {
            campuses[idx].isActive = (idx == matchedIndex)
        }

        persistEncryptedState()
        return activeCampus
    }

    public func getAcademicProfileSummary(semester: String = "2026.2") -> AcademicProfileSummary {
        AcademicProfileSummary(
            semester: semester,
            activeCampus: activeCampus,
            campuses: campuses,
            totalCourses: disciplines.filter { $0.semester == semester }.count,
            totalAbsences: totalAbsencesInSemester,
            totalMaxAbsences: totalMaxAbsencesInSemester,
            totalCancelledClasses: totalCancelledInSemester,
            coursesAtRisk: coursesAtRiskCount,
            pendingAssessments: pendingAssessmentsCount,
            completedAssessments: completedAssessmentsCount,
            calendarEvents: calendarEvents.filter { $0.semester == semester }
        )
    }

    public var academicProfileSummary: AcademicProfileSummary {
        getAcademicProfileSummary(semester: selectedSemesterFilter)
    }

    // MARK: - Queries & Commands (Dynamic Interactive Curriculum Grid)

    public func dynamicGridCompletedSet(for matrixId: String) -> Set<String> {
        if let matrix = DynamicCurriculumCatalog.find(byId: matrixId) {
            let raw = DynamicCurriculumCatalog.loadCompletedCodesWithLegacyFallback(matrix: matrix) { id in
                Set(dynamicGridCompletedByMatrix[id] ?? [])
            }
            return DynamicGridEngine.sanitizeCompleted(matrix: matrix, completedCodes: raw)
        }
        return Set(
            (dynamicGridCompletedByMatrix[matrixId] ?? [])
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() }
                .filter { !$0.isEmpty }
        )
    }

    @discardableResult
    public func toggleDynamicGridCourse(matrixId: String, courseCode: String) -> DynamicGridToggleResult {
        guard let matrix = DynamicCurriculumCatalog.find(byId: matrixId) else {
            let cleanCode = courseCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            return DynamicGridToggleResult(
                updatedCompletedCodes: dynamicGridCompletedSet(for: matrixId),
                action: .noChange,
                affectedCode: cleanCode,
                cascadeRemovedCodes: [],
                missingPrerequisites: [],
                missingHours: 0
            )
        }

        let current = dynamicGridCompletedSet(for: matrix.id)
        let result = DynamicGridEngine.toggleCourse(
            matrix: matrix,
            courseCode: courseCode,
            completedCodes: current
        )

        if result.action == .markedCompleted || result.action == .unmarkedWithCascade {
            let sorted = result.updatedCompletedCodes.sorted()
            if sorted.isEmpty {
                dynamicGridCompletedByMatrix.removeValue(forKey: matrix.id)
            } else {
                dynamicGridCompletedByMatrix[matrix.id] = sorted
            }
            for legacyId in matrix.legacyIds {
                dynamicGridCompletedByMatrix.removeValue(forKey: legacyId)
            }
            persistEncryptedState()
        }

        return result
    }

    public func clearDynamicGrid(matrixId: String) {
        if let matrix = DynamicCurriculumCatalog.find(byId: matrixId) {
            dynamicGridCompletedByMatrix.removeValue(forKey: matrix.id)
            for legacyId in matrix.legacyIds {
                dynamicGridCompletedByMatrix.removeValue(forKey: legacyId)
            }
        } else {
            dynamicGridCompletedByMatrix.removeValue(forKey: matrixId)
        }
        persistEncryptedState()
    }

    // MARK: - Helpers de Departamento & Exportação / Importação (.ufopgrade / QR UFOPG1)

    public static func departmentColorKey(for departmentId: String) -> DepartmentColorKey {
        let normalized = departmentId
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: .diacriticInsensitive, locale: .current)
            .lowercased()

        if normalized.contains("comp")
            || normalized.contains("decom")
            || normalized.contains("decsi")
            || normalized.contains("bcc")
            || normalized.contains("csi") {
            return .compPurple
        }
        if normalized.contains("phys")
            || normalized.contains("defis")
            || normalized.contains("fis") {
            return .physicsAmber
        }
        if normalized.contains("algebra")
            || normalized.contains("alg")
            || normalized == "decat" {
            return .algebraGreen
        }
        return .mathBlue
    }

    /// Exporta a grade do estudante no formato canônico `ufopgrade-v1` com paridade ao Android.
    public func exportMyGradeBundleJson(semester: String? = nil) -> String {
        let requestedSemester = (semester ?? selectedSemesterFilter)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let fallbackSemester = studentProfile.activeSemester
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanSemester = requestedSemester.isEmpty
            ? (fallbackSemester.isEmpty ? "2026.2" : fallbackSemester)
            : requestedSemester

        let semDisciplines = disciplines.filter { $0.semester == cleanSemester }
        let targetDisciplines = semDisciplines.isEmpty ? disciplines : semDisciplines
        let courseIds = Set(targetDisciplines.map(\.id))

        let trimmedName = studentProfile.fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        let friendName = trimmedName.isEmpty ? "Estudante UFOP" : trimmedName
        let courseName = studentProfile.courseName.trimmingCharacters(in: .whitespacesAndNewlines)

        let rawScheduleSlots = courseScheduleSlots.filter { courseIds.isEmpty || courseIds.contains($0.courseId) }
        let slotsToExport: [CourseScheduleSlotItem]
        if !rawScheduleSlots.isEmpty {
            slotsToExport = rawScheduleSlots
        } else {
            slotsToExport = timetableSlots
                .filter { courseIds.isEmpty || courseIds.contains($0.disciplineId) }
                .map { cell in
                    let day = Self.weekdayIndex(fromShort: cell.weekdayShort)
                    let parts = cell.timeRange
                        .replacingOccurrences(of: "–", with: "-")
                        .split(separator: "-")
                        .map { String($0).trimmingCharacters(in: .whitespacesAndNewlines) }
                    let start = Self.normalizeTimeString(parts.first ?? "08:00")
                    let end = Self.normalizeTimeString(parts.count > 1 ? parts[1] : "09:40")
                    let loc = cell.roomLabel.trimmingCharacters(in: .whitespacesAndNewlines)
                    return CourseScheduleSlotItem(
                        slotId: cell.id,
                        courseId: cell.disciplineId,
                        dayOfWeek: day,
                        dayLabel: cell.weekdayShort,
                        startTime: start.isEmpty ? "08:00" : start,
                        endTime: end.isEmpty ? "09:40" : end,
                        building: loc.isEmpty ? "Campus UFOP" : loc,
                        room: "",
                        shortLocation: loc.isEmpty ? "UFOP" : loc
                    )
                }
        }

        let coursesArray: [[String: Any]] = targetDisciplines.map { disc in
            let deptId: String = {
                switch disc.colorKey {
                case .compPurple: return "comp"
                case .physicsAmber: return "phys"
                case .algebraGreen: return "algebra"
                case .mathBlue: return "math"
                }
            }()
            let firstSlotShort = slotsToExport
                .first(where: { $0.courseId == disc.id })?
                .shortLocation
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let shortLoc = firstSlotShort.isEmpty
                ? (isCourseEad(disc.id) ? "EAD" : Self.buildShortLocation(building: disc.location, room: ""))
                : firstSlotShort

            return [
                "courseId": disc.id,
                "code": disc.code,
                "semester": disc.semester,
                "name": disc.name,
                "departmentId": deptId,
                "departmentName": disc.department,
                "colorHex": disc.colorKey.hexString,
                "workloadHours": disc.workloadHours,
                "maxAbsences": disc.maxAbsences,
                "professor": disc.professor,
                "buildingAndRoom": disc.location,
                "shortLocation": shortLoc
            ]
        }

        let slotsArray: [[String: Any]] = slotsToExport.map { slot in
            [
                "slotId": slot.slotId,
                "courseId": slot.courseId,
                "dayOfWeek": slot.dayOfWeek,
                "startTime": slot.startTime,
                "endTime": slot.endTime,
                "building": slot.building,
                "room": slot.room,
                "shortLocation": slot.shortLocation,
                "classWeight": 1
            ]
        }

        let root: [String: Any] = [
            "format": UfopGradeCodec.formatV1,
            "friendName": friendName,
            "courseName": courseName,
            "semester": cleanSemester,
            "campusId": activeCampus.id,
            "courses": coursesArray,
            "slots": slotsArray
        ]

        guard let data = try? JSONSerialization.data(
            withJSONObject: root,
            options: [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        ),
              let jsonString = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return jsonString
    }

    /// Exporta a grade horária e disciplinas do semestre em formato JSON (`ufopgrade-v1`).
    public func exportSchedulePayload(semester: String = "2026.2") -> String {
        exportMyGradeBundleJson(semester: semester)
    }

    /// Importa um payload de grade horária (`UFOPG1:`, `ufopgrade-v1`, formato iOS legado ou payload Rust Core) e retorna a quantidade de disciplinas importadas.
    @discardableResult
    public func importSchedulePayload(json: String) -> Int {
        let trimmed = json.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return 0
        }

        let decoder = JSONDecoder()
        if !trimmed.hasPrefix(UfopGradeCodec.qrPrefix),
           let data = trimmed.data(using: .utf8),
           let payload = try? decoder.decode(ScheduleExportPayload.self, from: data),
           !payload.disciplines.isEmpty {
            for imported in payload.disciplines {
                if let idx = disciplines.firstIndex(where: { $0.id == imported.id }) {
                    disciplines[idx] = imported
                } else {
                    disciplines.append(imported)
                }
            }
            if !payload.timetableSlots.isEmpty {
                self.timetableSlots = payload.timetableSlots
            }
            if let importedSlots = payload.courseScheduleSlots {
                self.courseScheduleSlots = importedSlots
            }
            if let importedAssessments = payload.assessmentsByDiscipline {
                for (courseId, list) in importedAssessments {
                    self.assessmentsByDiscipline[courseId] = list
                }
            }
            if let importedNotes = payload.notesByDiscipline {
                for (courseId, note) in importedNotes {
                    self.notesByDiscipline[courseId] = note
                }
            }
            _ = setActiveCampus(campusId: payload.activeCampusId)
            rebuildAcademicActivitiesFromAssessments()
            persistEncryptedState()
            return payload.disciplines.count
        }

        if let bundle = try? UfopGradeCodec.parseScheduleBundle(trimmed) {
            for course in bundle.courses {
                let finalColorKey: DepartmentColorKey = {
                    let hex = course.colorHex.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
                    switch hex {
                    case "#6E56CF": return .compPurple
                    case "#2E7D32": return .algebraGreen
                    case "#8D5B16": return .physicsAmber
                    case "#3B68FF": return .mathBlue
                    default: return Self.departmentColorKey(for: course.departmentId)
                    }
                }()

                let card = DisciplineSummaryCard(
                    id: course.courseId,
                    code: course.code,
                    name: course.name,
                    department: course.departmentName,
                    semester: bundle.semester,
                    workloadHours: course.workloadHours,
                    currentAbsences: disciplines.first(where: { $0.id == course.courseId })?.currentAbsences ?? 0,
                    maxAbsences: course.maxAbsences,
                    cancelledClasses: disciplines.first(where: { $0.id == course.courseId })?.cancelledClasses ?? 0,
                    colorKey: finalColorKey,
                    professor: course.professor,
                    location: course.buildingAndRoom
                )
                if let idx = disciplines.firstIndex(where: { $0.id == card.id }) {
                    disciplines[idx] = card
                } else {
                    disciplines.append(card)
                }
                if assessmentsByDiscipline[card.id] == nil {
                    assessmentsByDiscipline[card.id] = []
                }
                if notesByDiscipline[card.id] == nil {
                    notesByDiscipline[card.id] = ""
                }
            }

            let courseMap = Dictionary(
                disciplines.map { ($0.id, $0) },
                uniquingKeysWith: { first, _ in first }
            )

            for slot in bundle.slots {
                let dayMeta = Self.weekdayMetadata(for: slot.dayOfWeek)
                let item = CourseScheduleSlotItem(
                    slotId: slot.slotId,
                    courseId: slot.courseId,
                    dayOfWeek: slot.dayOfWeek,
                    dayLabel: dayMeta.shortLabel,
                    startTime: slot.startTime,
                    endTime: slot.endTime,
                    building: slot.building,
                    room: slot.room,
                    shortLocation: slot.shortLocation
                )
                courseScheduleSlots.removeAll { $0.slotId == item.slotId }
                courseScheduleSlots.append(item)

                let matchedDisc = courseMap[slot.courseId]
                let ttCell = TimetableSlotCell(
                    id: slot.slotId,
                    weekdayShort: dayMeta.shortLabel,
                    timeRange: item.timeRange,
                    disciplineId: slot.courseId,
                    disciplineShortName: matchedDisc?.name ?? slot.courseId,
                    roomLabel: slot.shortLocation,
                    colorKey: matchedDisc?.colorKey ?? .mathBlue
                )
                timetableSlots.removeAll { $0.id == ttCell.id }
                timetableSlots.append(ttCell)
            }

            if !bundle.campusId.isEmpty {
                _ = setActiveCampus(campusId: bundle.campusId)
            }
            rebuildAcademicActivitiesFromAssessments()
            persistEncryptedState()
            return bundle.courses.isEmpty ? bundle.slots.count : bundle.courses.count
        }

        // Suporte complementar legado direto ao JSON do motor Rust (`courses` sem slots válidos)
        if let data = trimmed.data(using: .utf8),
           let rustPayload = try? decoder.decode(RustCrossPlatformSchedulePayload.self, from: data),
           let rustCourses = rustPayload.courses,
           !rustCourses.isEmpty {
            for course in rustCourses {
                let card = DisciplineSummaryCard(
                    id: course.id,
                    code: course.code,
                    name: course.name,
                    department: course.department_name ?? "Matemática",
                    semester: course.semester ?? rustPayload.semester ?? "2026.2",
                    workloadHours: course.workload_hours ?? 60,
                    currentAbsences: course.current_absences ?? 0,
                    maxAbsences: course.max_absences ?? 18,
                    cancelledClasses: course.cancelled_classes ?? 0,
                    colorKey: DepartmentColorKey.fromHex(course.color_hex ?? "#3B68FF"),
                    professor: course.professor ?? "Docente UFOP",
                    location: course.location ?? "Campus UFOP"
                )
                if let idx = disciplines.firstIndex(where: { $0.id == card.id }) {
                    disciplines[idx] = card
                } else {
                    disciplines.append(card)
                }
            }
            rebuildAcademicActivitiesFromAssessments()
            persistEncryptedState()
            return rustCourses.count
        }

        return 0
    }

    // MARK: - Friend Schedules (.ufopgrade / QR UFOPG1)

    @discardableResult
    public func importFriendSchedule(from preview: IncomingGradePreview) -> FriendSchedule {
        let bundle = try? UfopGradeCodec.parseScheduleBundle(preview.rawJsonPayload)

        let friendCourses: [FriendCourseEntry] = (bundle?.courses ?? []).map { c in
            FriendCourseEntry(
                courseId: c.courseId,
                code: c.code,
                name: c.name,
                departmentId: c.departmentId
            )
        }
        let friendSlots: [FriendSlotEntry] = (bundle?.slots ?? []).map { s in
            FriendSlotEntry(
                slotId: s.slotId,
                courseId: s.courseId,
                dayOfWeek: s.dayOfWeek,
                startTime: s.startTime,
                endTime: s.endTime,
                shortLocation: s.shortLocation
            )
        }

        let resolvedName = preview.friendName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? (bundle?.friendName ?? "Colega UFOP")
            : preview.friendName.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedCourse = preview.courseName.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedSemester = preview.semester.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "2026.2"
            : preview.semester.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedAvatar = preview.avatarBase64 ?? bundle?.avatarBase64

        friendSchedules.removeAll { existing in
            existing.friendName.localizedCaseInsensitiveCompare(resolvedName) == .orderedSame
                && existing.semester == resolvedSemester
        }

        let created = FriendSchedule(
            id: "friend-\(UUID().uuidString.prefix(8).lowercased())",
            friendName: resolvedName,
            courseName: resolvedCourse,
            semester: resolvedSemester,
            avatarBase64: resolvedAvatar,
            courses: friendCourses,
            slots: friendSlots
        )
        friendSchedules.append(created)
        persistEncryptedState()
        return created
    }

    @discardableResult
    public func importFriendScheduleJson(
        _ rawText: String,
        sourceLabel: String = "Arquivo .ufopgrade"
    ) throws -> FriendSchedule {
        let preview = try UfopGradeCodec.parseAndValidatePreview(rawText, sourceLabel: sourceLabel)
        return importFriendSchedule(from: preview)
    }

    public func removeFriendSchedule(id: String) {
        let hadSelected = (selectedFriendScheduleId == id)
        friendSchedules.removeAll { $0.id == id }
        if hadSelected {
            selectedFriendScheduleId = nil
        }
        persistEncryptedState()
    }

    public func weeklyTimetable(for friend: FriendSchedule) -> WeeklyTimetable {
        let courseById = Dictionary(
            friend.courses.map { ($0.courseId, $0) },
            uniquingKeysWith: { first, _ in first }
        )

        let cells: [TimetableSlotCell] = friend.slots.map { slot in
            let matchedCourse = courseById[slot.courseId]
            let dayMeta = Self.weekdayMetadata(for: slot.dayOfWeek)
            let courseTitle: String = {
                if let matchedCourse {
                    let cleanName = matchedCourse.name.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !cleanName.isEmpty { return cleanName }
                    if !matchedCourse.code.isEmpty { return matchedCourse.code }
                }
                return slot.courseId
            }()
            let colorKey = Self.departmentColorKey(for: matchedCourse?.departmentId ?? "math")
            let room = slot.shortLocation.trimmingCharacters(in: .whitespacesAndNewlines)
            return TimetableSlotCell(
                id: slot.slotId,
                weekdayShort: dayMeta.shortLabel,
                timeRange: "\(slot.startTime)–\(slot.endTime)",
                disciplineId: slot.courseId,
                disciplineShortName: courseTitle,
                roomLabel: room.isEmpty ? "UFOP" : room,
                colorKey: colorKey
            )
        }

        var weekdays = ["SEG", "TER", "QUA", "QUI", "SEX", "SÁB"]
        if cells.contains(where: { $0.weekdayShort == "DOM" }) {
            weekdays.append("DOM")
        }

        let baseTimeRanges = ["08:00–09:40", "10:00–11:40", "14:00–15:00"]
        let slotTimeRanges = Set(cells.map(\.timeRange))
        let mergedTimeRanges = Array(Set(baseTimeRanges).union(slotTimeRanges)).sorted { lhs, rhs in
            Self.minutesFromTimeRangeStart(lhs) < Self.minutesFromTimeRangeStart(rhs)
        }

        let subtitleCourse = friend.courseName.trimmingCharacters(in: .whitespacesAndNewlines)
        let subtitle = subtitleCourse.isEmpty
            ? "Grade compartilhada · \(friend.semester)"
            : "\(subtitleCourse) · \(friend.semester)"

        return WeeklyTimetable(
            title: "Grade de \(friend.friendName)",
            subtitle: subtitle,
            semester: friend.semester,
            selectedFilter: "Todas",
            availableFilters: ["Todas"],
            weekdays: weekdays,
            timeRanges: mergedTimeRanges,
            slots: cells
        )
    }

    public func timetableLegendEntries(
        for friend: FriendSchedule,
        slots: [TimetableSlotCell]
    ) -> [TimetableLegendEntry] {
        let visibleIds = Set(slots.map(\.disciplineId))
        let sourceCourses = visibleIds.isEmpty
            ? friend.courses
            : friend.courses.filter { visibleIds.contains($0.courseId) }

        if sourceCourses.isEmpty && !slots.isEmpty {
            return DepartmentColorKey.allCases.compactMap { key in
                let matchingSlots = slots.filter { $0.colorKey == key }
                guard !matchingSlots.isEmpty else { return nil }
                var seenNames: [String] = []
                for slot in matchingSlots where !seenNames.contains(slot.disciplineShortName) {
                    seenNames.append(slot.disciplineShortName)
                }
                return TimetableLegendEntry(
                    id: key.rawValue,
                    colorKey: key,
                    title: key.departmentLabel,
                    subtitle: seenNames.joined(separator: " · ")
                )
            }
        }

        return DepartmentColorKey.allCases.compactMap { key in
            let matching = sourceCourses.filter { Self.departmentColorKey(for: $0.departmentId) == key }
            guard !matching.isEmpty else { return nil }
            return TimetableLegendEntry(
                id: key.rawValue,
                colorKey: key,
                title: key.departmentLabel,
                subtitle: matching.map(\.name).joined(separator: " · ")
            )
        }
    }

    private static func weekdayIndex(fromShort raw: String) -> Int {
        switch raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: .diacriticInsensitive, locale: .current)
            .uppercased() {
        case "SEG": return 1
        case "TER": return 2
        case "QUA": return 3
        case "QUI": return 4
        case "SEX": return 5
        case "SAB": return 6
        case "DOM": return 7
        default: return 1
        }
    }

}
