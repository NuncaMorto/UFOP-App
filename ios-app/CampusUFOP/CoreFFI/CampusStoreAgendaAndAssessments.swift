import Foundation

// MARK: - Agenda, Check-In & Assessments

public extension CampusStore {
    // MARK: - Queries & Commands (Agenda & Custom Commitments)

    public var currentAgendaOverview: AgendaDayOverview {
        getAgendaOverview(for: selectedDateIso)
    }

    public func selectAgendaDate(_ dateIso: String) {
        let trimmed = dateIso.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        selectedDateIso = trimmed
    }

    public func getAgendaOverview(for dateIso: String) -> AgendaDayOverview {
        let parsedSelected = Self.parseIsoDate(dateIso) ?? Date()
        let selectedIsoNormalized = Self.isoString(from: parsedSelected)
        let weekDates = Self.mondayToSundayDates(containing: parsedSelected)

        let chips: [AgendaDayChip] = weekDates.enumerated().map { index, date in
            let iso = Self.isoString(from: date)
            let weekdayNum = index + 1
            let hasEvents = !timelineItems(for: iso, weekdayIndex: weekdayNum).isEmpty
            return AgendaDayChip(
                dateIso: iso,
                weekdayShort: Self.weekdayMetadata(for: weekdayNum).shortLabel,
                dayNumber: Self.dayNumberString(from: date),
                isSelected: iso == selectedIsoNormalized,
                hasEvents: hasEvents
            )
        }

        let selectedWeekday = Self.isoWeekdayIndex(from: parsedSelected)
        let items = timelineItems(for: selectedIsoNormalized, weekdayIndex: selectedWeekday)
        let fullHeader = Self.fullPortugueseDateLabel(for: selectedIsoNormalized)

        let lectureCount = items.filter { $0.kind == .lecture }.count
        let commitmentCount = items.filter { $0.kind == .commitment }.count

        let summarySubtitle: String
        if lectureCount == 0 && commitmentCount == 0 {
            summarySubtitle = "Sem atividades programadas"
        } else {
            var parts: [String] = []
            if lectureCount > 0 {
                parts.append("\(lectureCount) \(lectureCount == 1 ? "aula" : "aulas")")
            }
            if commitmentCount > 0 {
                parts.append("\(commitmentCount) \(commitmentCount == 1 ? "compromisso" : "compromissos")")
            }
            summarySubtitle = parts.joined(separator: " • ")
        }

        let monthLabel = Self.portugueseMonthYearLabel(from: parsedSelected)
        let weekNumber = Calendar(identifier: .iso8601).component(.weekOfYear, from: parsedSelected)

        return AgendaDayOverview(
            semesterTag: selectedSemesterFilter,
            monthLabel: monthLabel,
            weekLabel: "SEMANA \(weekNumber)",
            selectedDateIso: selectedIsoNormalized,
            fullDateHeader: fullHeader,
            summarySubtitle: summarySubtitle,
            weekDays: chips,
            timelineItems: items
        )
    }

    public func getClassCheckInDetail(agendaItemId: String) -> ClassCheckInDetail? {
        // Procura primeiro na timeline dinâmica da data selecionada (funciona em qualquer semana do ano)
        if let liveItem = getAgendaOverview(for: selectedDateIso).timelineItems.first(where: { $0.id == agendaItemId }) {
            let disciplineId = liveItem.disciplineId ?? "calc-ii"
            let overview = getAgendaOverview(for: selectedDateIso)
            return ClassCheckInDetail(
                agendaItemId: liveItem.id,
                disciplineId: disciplineId,
                department: liveItem.department,
                disciplineName: liveItem.title,
                fullDateLabel: overview.fullDateHeader,
                timeRange: liveItem.timeRangeLabel,
                weatherLabel: liveItem.weatherDetailLabel,
                location: liveItem.location,
                professor: liveItem.professor,
                selectedColorKey: liveItem.colorKey,
                selectedStatus: liveItem.attendanceStatus,
                footnoteText: "Aula cancelada não conta como falta. Salve para atualizar o relatório em Disciplinas."
            )
        }
        for (dateIso, items) in agendaItemsByDate {
            if let item = items.first(where: { $0.id == agendaItemId }) {
                let disciplineId = item.disciplineId ?? "calc-ii"
                let overview = getAgendaOverview(for: dateIso)
                return ClassCheckInDetail(
                    agendaItemId: item.id,
                    disciplineId: disciplineId,
                    department: item.department,
                    disciplineName: item.title,
                    fullDateLabel: overview.fullDateHeader,
                    timeRange: item.timeRangeLabel,
                    weatherLabel: item.weatherDetailLabel,
                    location: item.location,
                    professor: item.professor,
                    selectedColorKey: item.colorKey,
                    selectedStatus: item.attendanceStatus,
                    footnoteText: "Aula cancelada não conta como falta. Salve para atualizar o relatório em Disciplinas."
                )
            }
        }
        return nil
    }

    /// Cria um novo compromisso acadêmico (grupo de estudos, monitoria, laboratório) no dia selecionado.
    @discardableResult
    public func createCommitment(
        title: String,
        description: String = "Compromisso acadêmico",
        isoDate: String? = nil,
        startTime: String,
        endTime: String,
        location: String,
        weatherTempC: Int? = nil
    ) -> AgendaTimelineItem {
        let targetDate = isoDate ?? selectedDateIso
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanLocation = location.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDescription = description.trimmingCharacters(in: .whitespacesAndNewlines)

        let resolvedTemp = weatherTempC ?? activeCampus.weatherTempC
        let resolvedCondition = activeCampus.weatherCondition

        let newItem = AgendaTimelineItem(
            id: "commitment-\(UUID().uuidString.prefix(8).lowercased())",
            disciplineId: nil,
            kind: .commitment,
            startTime: startTime.isEmpty ? "14:00" : startTime,
            endTime: endTime.isEmpty ? "15:00" : endTime,
            title: cleanTitle.isEmpty ? "Grupo de estudos · UFOP" : cleanTitle,
            subtitle: cleanLocation.isEmpty ? activeCampus.fullLabel : cleanLocation,
            location: cleanLocation.isEmpty ? activeCampus.fullLabel : cleanLocation,
            professor: cleanDescription.isEmpty ? "Compromisso acadêmico" : cleanDescription,
            department: "Compromisso",
            colorKey: .algebraGreen,
            weatherTempC: resolvedTemp,
            weatherCondition: resolvedCondition,
            attendanceStatus: .attending
        )

        var dayItems = agendaItemsByDate[targetDate] ?? []
        dayItems.append(newItem)
        dayItems.sort { $0.startTime < $1.startTime }
        agendaItemsByDate[targetDate] = dayItems
        recordedStatusByAgendaItem[newItem.id] = .attending
        persistEncryptedState()

        return newItem
    }

    /// Remove um compromisso personalizado pelo identificador.
    public func deleteCommitment(id: String) {
        for (dateIso, var items) in agendaItemsByDate {
            if let idx = items.firstIndex(where: { $0.id == id && $0.kind == .commitment }) {
                items.remove(at: idx)
                agendaItemsByDate[dateIso] = items
                recordedStatusByAgendaItem.removeValue(forKey: id)
                break
            }
        }
        persistEncryptedState()
    }

    /// Salva o check-in da aula aplicando a regra crítica:
    /// - `.cancelled` incrementa `cancelledClasses` e NÃO soma falta em `currentAbsences`.
    /// - `.absent` incrementa `currentAbsences`.
    /// - `.attending` reverte falta/cancelamento anterior se houver mudança de estado.
    /// Atualiza também a cor do departamento na agenda, na disciplina e na grade horária.
    public func saveClassCheckIn(
        agendaItemId: String,
        status: AttendanceStatus,
        colorKey: DepartmentColorKey
    ) {
        var targetDisciplineId: String?
        var targetSession: (dateIso: String, startTime: String, endTime: String)?

        for (dateIso, var items) in agendaItemsByDate {
            if let index = items.firstIndex(where: { $0.id == agendaItemId }) {
                items[index].attendanceStatus = status
                items[index].colorKey = colorKey
                targetDisciplineId = items[index].disciplineId
                targetSession = (dateIso, items[index].startTime, items[index].endTime)
                agendaItemsByDate[dateIso] = items
                break
            }
        }

        if targetDisciplineId == nil, agendaItemId.hasPrefix("lecture-") {
            let withoutPrefix = String(agendaItemId.dropFirst("lecture-".count))
            let parts = withoutPrefix.components(separatedBy: "@")
            let slotId = parts.first ?? withoutPrefix
            let sessionDate = parts.count > 1 ? parts[1] : selectedDateIso
            if let slot = courseScheduleSlots.first(where: { $0.slotId == slotId }) {
                targetDisciplineId = slot.courseId
                targetSession = (sessionDate, slot.startTime, slot.endTime)
            }
        }

        let previousStatus = recordedStatusByAgendaItem[agendaItemId] ?? .attending
        recordedStatusByAgendaItem[agendaItemId] = status

        // Sprint 4: registra a marcação no histórico da disciplina (somente `.absent` / `.cancelled`)
        if let disciplineId = targetDisciplineId, let session = targetSession {
            recordAbsenceHistory(
                sessionId: agendaItemId,
                courseId: disciplineId,
                dateIso: session.dateIso,
                startTime: session.startTime,
                endTime: session.endTime,
                status: status
            )
        }

        if let disciplineId = targetDisciplineId,
           let discIndex = disciplines.firstIndex(where: { $0.id == disciplineId }) {
            // Reverte impacto do estado anterior
            switch previousStatus {
            case .attending:
                break
            case .absent:
                disciplines[discIndex].currentAbsences = max(0, disciplines[discIndex].currentAbsences - 1)
            case .cancelled:
                disciplines[discIndex].cancelledClasses = max(0, disciplines[discIndex].cancelledClasses - 1)
            }

            // Aplica impacto do novo estado
            switch status {
            case .attending:
                break
            case .absent:
                disciplines[discIndex].currentAbsences = min(
                    disciplines[discIndex].maxAbsences,
                    disciplines[discIndex].currentAbsences + 1
                )
            case .cancelled:
                // Regra crítica: Aula cancelada incrementa `cancelledClasses` e NÃO soma falta
                disciplines[discIndex].cancelledClasses += 1
            }

            // Propaga a cor selecionada para a disciplina, itens de agenda e grade horária
            updateDisciplineColor(disciplineId: disciplineId, colorKey: colorKey)
        }
        persistEncryptedState()
    }

    /// Sprint 4: Lista o histórico de check-ins marcados como falta (`.absent`) ou aula cancelada (`.cancelled`)
    /// para a disciplina informada, ordenado do registro mais recente para o mais antigo.
    public func listAbsenceHistory(courseId: String) -> [AbsenceHistoryEntry] {
        absenceHistoryBySession.values
            .filter { $0.courseId == courseId && ($0.status == .absent || $0.status == .cancelled) }
            .sorted { lhs, rhs in
                if lhs.sessionDate != rhs.sessionDate {
                    return lhs.sessionDate > rhs.sessionDate
                }
                if lhs.startTime != rhs.startTime {
                    return lhs.startTime > rhs.startTime
                }
                return lhs.sessionId > rhs.sessionId
            }
    }

    private func recordAbsenceHistory(
        sessionId: String,
        courseId: String,
        dateIso: String,
        startTime: String,
        endTime: String,
        status: AttendanceStatus
    ) {
        guard status == .absent || status == .cancelled else {
            absenceHistoryBySession.removeValue(forKey: sessionId)
            return
        }

        let resolvedSlotId = resolveScheduleSlotId(
            agendaItemId: sessionId,
            courseId: courseId,
            dateIso: dateIso,
            startTime: startTime
        )

        absenceHistoryBySession[sessionId] = AbsenceHistoryEntry(
            sessionId: sessionId,
            slotId: resolvedSlotId,
            courseId: courseId,
            sessionDate: dateIso,
            formattedDate: Self.fullPortugueseDateLabel(for: dateIso),
            startTime: startTime,
            endTime: endTime,
            status: status,
            classWeight: 1
        )
    }

    private func resolveScheduleSlotId(
        agendaItemId: String,
        courseId: String,
        dateIso: String,
        startTime: String
    ) -> String {
        if agendaItemId.hasPrefix("lecture-") {
            let candidate = String(agendaItemId.dropFirst("lecture-".count))
            if !candidate.isEmpty {
                return candidate
            }
        }

        if let exactSlot = courseScheduleSlots.first(where: { slot in
            slot.courseId == courseId
                && slot.startTime == startTime
                && Self.weekdayMetadata(for: slot.dayOfWeek).isoDate == dateIso
        }) {
            return exactSlot.slotId
        }

        if let timeMatchedSlot = courseScheduleSlots.first(where: { slot in
            slot.courseId == courseId && slot.startTime == startTime
        }) {
            return timeMatchedSlot.slotId
        }

        return agendaItemId
    }

    // MARK: - Queries & Commands (Assessments & Activities)

    /// Cria uma nova avaliação ou prazo (`Trabalho`, `Seminário`, `Prova`) sincronizando Disciplinas e Atividades.
    @discardableResult
    public func createAssessment(
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String = "18:00"
    ) -> AssessmentItem {
        let targetCourseId = disciplines.contains(where: { $0.id == courseId })
            ? courseId
            : (disciplines.first?.id ?? "calc-ii")

        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanKind = kind.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Trabalho"
            : kind.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanTime = dueTime.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "18:00"
            : dueTime.trimmingCharacters(in: .whitespacesAndNewlines)

        let parsed = Self.parseDueDateComponents(from: dueDate)

        let newItem = AssessmentItem(
            id: "\(targetCourseId)-a-\(UUID().uuidString.prefix(6).lowercased())",
            courseId: targetCourseId,
            dayNumber: parsed.dayNumber,
            monthShort: parsed.monthShort,
            title: cleanTitle.isEmpty ? "Nova avaliação · \(cleanKind)" : cleanTitle,
            subtitle: "\(cleanKind) · \(cleanTime)",
            kind: cleanKind,
            dueDate: parsed.isoDate,
            dueTime: cleanTime,
            completed: false
        )

        var list = assessmentsByDiscipline[targetCourseId] ?? []
        list.append(newItem)
        list.sort { $0.dueDate < $1.dueDate }
        assessmentsByDiscipline[targetCourseId] = list

        rebuildAcademicActivitiesFromAssessments()
        persistEncryptedState()
        return newItem
    }

    /// Alterna o estado `completed` de uma avaliação e sincroniza imediatamente `DisciplineDetailSheetView` e `ActivitiesView`.
    @discardableResult
    public func toggleAssessmentCompleted(assessmentId: String) -> AssessmentItem? {
        for (courseId, var items) in assessmentsByDiscipline {
            if let idx = items.firstIndex(where: { $0.id == assessmentId }) {
                items[idx].completed.toggle()
                let updated = items[idx]
                assessmentsByDiscipline[courseId] = items
                rebuildAcademicActivitiesFromAssessments()
                persistEncryptedState()
                return updated
            }
        }
        return nil
    }

    /// Define explicitamente o estado `completed` de uma avaliação (idempotente para ações inline de notificação).
    @discardableResult
    public func setAssessmentCompleted(assessmentId: String, completed: Bool) -> AssessmentItem? {
        for (courseId, var items) in assessmentsByDiscipline {
            if let idx = items.firstIndex(where: { $0.id == assessmentId }) {
                items[idx].completed = completed
                let updated = items[idx]
                assessmentsByDiscipline[courseId] = items
                rebuildAcademicActivitiesFromAssessments()
                persistEncryptedState()
                return updated
            }
        }
        return nil
    }

    /// Remove uma avaliação de uma disciplina e sincroniza `ActivitiesView`.
    public func deleteAssessment(assessmentId: String) {
        for (courseId, var items) in assessmentsByDiscipline {
            if let idx = items.firstIndex(where: { $0.id == assessmentId }) {
                items.remove(at: idx)
                assessmentsByDiscipline[courseId] = items
                rebuildAcademicActivitiesFromAssessments()
                persistEncryptedState()
                return
            }
        }
    }

    public func toggleActivityCompletion(activityId: String) {
        _ = toggleAssessmentCompleted(assessmentId: activityId)
    }

    private static func parseDueDateComponents(from rawDate: String) -> (isoDate: String, dayNumber: String, monthShort: String) {
        let trimmed = rawDate.trimmingCharacters(in: .whitespacesAndNewlines)
        let monthAbbrevByNum: [String: String] = [
            "01": "JAN", "02": "FEV", "03": "MAR", "04": "ABR",
            "05": "MAI", "06": "JUN", "07": "JUL", "08": "AGO",
            "09": "SET", "10": "OUT", "11": "NOV", "12": "DEZ"
        ]
        let monthNumByAbbrev: [String: String] = [
            "JAN": "01", "FEV": "02", "MAR": "03", "ABR": "04",
            "MAI": "05", "JUN": "06", "JUL": "07", "AGO": "08",
            "SET": "09", "OUT": "10", "NOV": "11", "DEZ": "12"
        ]

        // Formato YYYY-MM-DD
        let dashParts = trimmed.split(separator: "-").map(String.init)
        if dashParts.count == 3, dashParts[0].count == 4 {
            let monthNum = String(format: "%02d", Int(dashParts[1]) ?? 11)
            let dayNum = String(format: "%02d", Int(dashParts[2]) ?? 20)
            let monthShort = monthAbbrevByNum[monthNum] ?? "NOV"
            return ("\(dashParts[0])-\(monthNum)-\(dayNum)", dayNum, monthShort)
        }

        // Formato DD/MM ou DD MMM
        let tokens = trimmed
            .replacingOccurrences(of: "/", with: " ")
            .split(separator: " ")
            .map(String.init)
        if tokens.count >= 2 {
            let dayNum = String(format: "%02d", Int(tokens[0]) ?? 25)
            let secondUpper = tokens[1].uppercased()
            if let monthNum = monthNumByAbbrev[secondUpper] {
                return ("2026-\(monthNum)-\(dayNum)", dayNum, secondUpper)
            } else if let mInt = Int(secondUpper) {
                let monthNum = String(format: "%02d", max(1, min(12, mInt)))
                let monthShort = monthAbbrevByNum[monthNum] ?? "NOV"
                return ("2026-\(monthNum)-\(dayNum)", dayNum, monthShort)
            }
        }

        return ("2026-11-30", "30", "NOV")
    }

}
