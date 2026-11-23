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

    /// Exporta a grade horária e disciplinas do semestre em formato JSON.
    public func exportSchedulePayload(semester: String = "2026.2") -> String {
        let semDisciplines = disciplines.filter { $0.semester == semester }
        let semIds = Set(semDisciplines.map(\.id))
        let payload = ScheduleExportPayload(
            semester: semester,
            activeCampusId: activeCampus.id,
            disciplines: semDisciplines.isEmpty ? disciplines : semDisciplines,
            timetableSlots: timetableSlots.filter { semIds.isEmpty || semIds.contains($0.disciplineId) },
            courseScheduleSlots: courseScheduleSlots.filter { semIds.isEmpty || semIds.contains($0.courseId) },
            assessmentsByDiscipline: assessmentsByDiscipline,
            notesByDiscipline: notesByDiscipline
        )

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        guard let data = try? encoder.encode(payload),
              let jsonString = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return jsonString
    }

    /// Importa um payload JSON de grade horária (compatível com o formato iOS e com o payload Rust Core) e retorna a quantidade de disciplinas importadas.
    @discardableResult
    public func importSchedulePayload(json: String) -> Int {
        let trimmed = json.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let data = trimmed.data(using: .utf8), !trimmed.isEmpty else {
            return 0
        }

        let decoder = JSONDecoder()

        if let payload = try? decoder.decode(ScheduleExportPayload.self, from: data),
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

        // Suporte complementar ao JSON exportado pelo motor Rust (`courses` + `schedule_slots`)
        if let rustPayload = try? decoder.decode(RustCrossPlatformSchedulePayload.self, from: data),
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
            return rustCourses.count
        }

        return 0
    }

}
