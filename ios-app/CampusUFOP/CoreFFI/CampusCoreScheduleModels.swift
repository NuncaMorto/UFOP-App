import Foundation

// MARK: - Disciplines, Assessments, Timetable & Schedule Validation Models

/// Card resumido de uma disciplina no Grid 2x2 da aba `Disciplinas`.
public struct DisciplineSummaryCard: Identifiable, Equatable, Codable, Sendable {
    public let id: String
    public let code: String
    public let name: String
    public let department: String
    public let semester: String
    public let workloadHours: Int
    public var currentAbsences: Int
    public let maxAbsences: Int
    public var cancelledClasses: Int
    public var colorKey: DepartmentColorKey
    public let professor: String
    public var location: String

    /// Proporção compacta exibida no card (`4/18`, `2/18`, `1/27`, `3/9`).
    public var compactRatioText: String {
        "\(currentAbsences)/\(maxAbsences)"
    }

    /// Rótulo expandido (`4 / 18 faltas`).
    public var expandedAbsenceText: String {
        "\(currentAbsences) / \(maxAbsences) faltas"
    }

    /// Carga horária + semestre (`60 h · 2026.2`).
    public var workloadAndSemesterLabel: String {
        "\(workloadHours) h · \(semester)"
    }

    /// Rótulo curto de carga horária (`60h`).
    public var shortWorkloadLabel: String {
        "\(workloadHours)h"
    }

    /// Rótulo de aulas canceladas (`0 canceladas · não somam faltas`).
    public var cancelledSummaryLabel: String {
        let word = cancelledClasses == 1 ? "cancelada" : "canceladas"
        return "\(cancelledClasses) \(word) · não somam faltas"
    }

    /// Fração de faltas consumidas (`0.0 ... 1.0`).
    public var absenceProgress: Double {
        guard maxAbsences > 0 else { return 0 }
        return min(1.0, max(0.0, Double(currentAbsences) / Double(maxAbsences)))
    }

    /// Quantidade de faltas restantes antes do limite de 25% da UFOP.
    public var remainingAbsences: Int {
        max(0, maxAbsences - currentAbsences)
    }

    public init(
        id: String,
        code: String,
        name: String,
        department: String,
        semester: String,
        workloadHours: Int,
        currentAbsences: Int,
        maxAbsences: Int,
        cancelledClasses: Int,
        colorKey: DepartmentColorKey,
        professor: String,
        location: String
    ) {
        self.id = id
        self.code = code
        self.name = name
        self.department = department
        self.semester = semester
        self.workloadHours = workloadHours
        self.currentAbsences = currentAbsences
        self.maxAbsences = maxAbsences
        self.cancelledClasses = cancelledClasses
        self.colorKey = colorKey
        self.professor = professor
        self.location = location
    }
}

/// Item da lista `Prazos e avaliações` dentro de `DisciplineDetailSheetView` e `ActivitiesView`.
public struct AssessmentItem: Identifiable, Equatable, Codable, Sendable {
    public let id: String
    public let courseId: String
    public let dayNumber: String
    public let monthShort: String
    public let title: String
    public let subtitle: String
    public let kind: String
    public let dueDate: String
    public let dueTime: String
    public var completed: Bool

    /// Ex: `20 NOV`
    public var dateBadgeText: String {
        "\(dayNumber) \(monthShort)"
    }

    public init(
        id: String,
        courseId: String = "",
        dayNumber: String,
        monthShort: String,
        title: String,
        subtitle: String = "",
        kind: String = "Trabalho",
        dueDate: String = "2026-11-20",
        dueTime: String = "18:00",
        completed: Bool = false
    ) {
        self.id = id
        self.courseId = courseId
        self.dayNumber = dayNumber
        self.monthShort = monthShort
        self.title = title
        self.subtitle = subtitle
        self.kind = kind
        self.dueDate = dueDate
        self.dueTime = dueTime
        self.completed = completed
    }
}

/// Modelo completo da folha modal `DisciplineDetailSheetView`.
public struct DisciplineDetail: Identifiable, Equatable, Codable, Sendable {
    public var summary: DisciplineSummaryCard
    public var assessments: [AssessmentItem]
    public var notes: String

    public var id: String { summary.id }

    public init(
        summary: DisciplineSummaryCard,
        assessments: [AssessmentItem],
        notes: String
    ) {
        self.summary = summary
        self.assessments = assessments
        self.notes = notes
    }
}

/// Célula de aula na grade horária semanal (`TimetableGridView`).
public struct TimetableSlotCell: Identifiable, Equatable, Codable, Sendable {
    public let id: String
    public let weekdayShort: String
    public let timeRange: String
    public let disciplineId: String
    public let disciplineShortName: String
    public let roomLabel: String
    public var colorKey: DepartmentColorKey

    public init(
        id: String,
        weekdayShort: String,
        timeRange: String,
        disciplineId: String,
        disciplineShortName: String,
        roomLabel: String,
        colorKey: DepartmentColorKey
    ) {
        self.id = id
        self.weekdayShort = weekdayShort
        self.timeRange = timeRange
        self.disciplineId = disciplineId
        self.disciplineShortName = disciplineShortName
        self.roomLabel = roomLabel
        self.colorKey = colorKey
    }
}

/// Grade de horários semanal (`Semana de aulas · 2026.2`).
public struct WeeklyTimetable: Equatable, Codable, Sendable {
    public let title: String
    public let subtitle: String
    public let semester: String
    public let selectedFilter: String
    public let availableFilters: [String]
    public let weekdays: [String]
    public let timeRanges: [String]
    public var slots: [TimetableSlotCell]

    public func slot(forWeekday weekday: String, timeRange: String) -> TimetableSlotCell? {
        slots.first { $0.weekdayShort == weekday && $0.timeRange == timeRange }
    }

    public init(
        title: String,
        subtitle: String,
        semester: String,
        selectedFilter: String,
        availableFilters: [String],
        weekdays: [String],
        timeRanges: [String],
        slots: [TimetableSlotCell]
    ) {
        self.title = title
        self.subtitle = subtitle
        self.semester = semester
        self.selectedFilter = selectedFilter
        self.availableFilters = availableFilters
        self.weekdays = weekdays
        self.timeRanges = timeRanges
        self.slots = slots
    }
}

/// Atividade acadêmica complementar para a aba `Atividades`.
public struct AcademicActivityItem: Identifiable, Equatable, Codable, Sendable {
    public let id: String
    public let disciplineId: String
    public let disciplineName: String
    public let department: String
    public var colorKey: DepartmentColorKey
    public let dueDay: String
    public let dueMonth: String
    public let title: String
    public let categoryLabel: String
    public let kind: String
    public let dueDate: String
    public let dueTime: String
    public var isCompleted: Bool

    public init(
        id: String,
        disciplineId: String,
        disciplineName: String,
        department: String,
        colorKey: DepartmentColorKey,
        dueDay: String,
        dueMonth: String,
        title: String,
        categoryLabel: String,
        kind: String = "Trabalho",
        dueDate: String = "2026-11-20",
        dueTime: String = "18:00",
        isCompleted: Bool = false
    ) {
        self.id = id
        self.disciplineId = disciplineId
        self.disciplineName = disciplineName
        self.department = department
        self.colorKey = colorKey
        self.dueDay = dueDay
        self.dueMonth = dueMonth
        self.title = title
        self.categoryLabel = categoryLabel
        self.kind = kind
        self.dueDate = dueDate
        self.dueTime = dueTime
        self.isCompleted = isCompleted
    }
}

// MARK: - Sprint 3 Course Templates & Weekly Schedule Slots

/// Template curricular oficial de curso da UFOP (`BCC · DECOM`, `ENGC · DECSI`, `CICLO BÁSICO · EM`).
public struct UfopCurriculumTemplate: Identifiable, Equatable, Codable, Sendable {
    public let id: String
    public let code: String
    public let name: String
    public let departmentName: String
    public let campusName: String
    public let description: String
    public let coursesCount: Int

    public init(
        id: String,
        code: String,
        name: String,
        departmentName: String,
        campusName: String,
        description: String,
        coursesCount: Int
    ) {
        self.id = id
        self.code = code
        self.name = name
        self.departmentName = departmentName
        self.campusName = campusName
        self.description = description
        self.coursesCount = coursesCount
    }
}

/// Horário semanal vinculado a uma disciplina na grade da UFOP (`dayOfWeek`: 1=SEG .. 6=SÁB).
public struct CourseScheduleSlotItem: Identifiable, Equatable, Codable, Sendable {
    public let slotId: String
    public let courseId: String
    public let dayOfWeek: Int
    public let dayLabel: String
    public let startTime: String
    public let endTime: String
    public let building: String
    public let room: String
    public let shortLocation: String

    public var id: String { slotId }

    public var timeRange: String {
        "\(startTime)–\(endTime)"
    }

    public init(
        slotId: String,
        courseId: String,
        dayOfWeek: Int,
        dayLabel: String,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ) {
        self.slotId = slotId
        self.courseId = courseId
        self.dayOfWeek = dayOfWeek
        self.dayLabel = dayLabel
        self.startTime = startTime
        self.endTime = endTime
        self.building = building
        self.room = room
        self.shortLocation = shortLocation
    }
}

/// Sprint 4: Registro histórico de uma marcação de check-in (`.absent` ou `.cancelled`) por sessão de aula.
/// Espelha o modelo `class_session` do Rust Core (`class_weight` padrão = 1).
public struct AbsenceHistoryEntry: Identifiable, Equatable, Codable, Sendable {
    public let sessionId: String
    public let slotId: String
    public let courseId: String
    /// Data ISO da sessão (`yyyy-MM-dd`).
    public let sessionDate: String
    /// Data formatada para exibição (`Ter, 17 nov`).
    public let formattedDate: String
    public let startTime: String
    public let endTime: String
    public var status: AttendanceStatus
    public var classWeight: Int

    public var id: String { sessionId }

    public var timeRangeLabel: String {
        "\(startTime)–\(endTime)"
    }

    /// `true` somente para faltas; aulas canceladas nunca somam falta.
    public var countsAsAbsence: Bool {
        status == .absent
    }

    /// Selo visual: `Falta (+1)` ou `Cancelada · não soma falta`.
    public var badgeTitle: String {
        switch status {
        case .absent:
            return "Falta (+\(max(1, classWeight)))"
        case .cancelled:
            return "Cancelada · não soma falta"
        case .attending:
            return "Presente"
        }
    }

    public init(
        sessionId: String,
        slotId: String,
        courseId: String,
        sessionDate: String,
        formattedDate: String,
        startTime: String,
        endTime: String,
        status: AttendanceStatus,
        classWeight: Int = 1
    ) {
        self.sessionId = sessionId
        self.slotId = slotId
        self.courseId = courseId
        self.sessionDate = sessionDate
        self.formattedDate = formattedDate
        self.startTime = startTime
        self.endTime = endTime
        self.status = status
        self.classWeight = classWeight
    }
}

/// Entrada dinâmica da legenda da grade semanal.
public struct TimetableLegendEntry: Identifiable, Equatable, Sendable {
    public let id: String
    public let colorKey: DepartmentColorKey
    public let title: String
    public let subtitle: String

    public init(
        id: String,
        colorKey: DepartmentColorKey,
        title: String,
        subtitle: String
    ) {
        self.id = id
        self.colorKey = colorKey
        self.title = title
        self.subtitle = subtitle
    }
}

/// Erros de validação e conflito de horário na grade semanal da UFOP.
public enum CampusScheduleError: LocalizedError, Equatable, Sendable {
    case emptyField(String)
    case invalidDayOfWeek(Int)
    case invalidTimeRange(String, String)
    case courseNotFound(String)
    case scheduleConflict(existingCourseName: String, dayLabel: String, startTime: String)

    public var errorDescription: String? {
        switch self {
        case .emptyField(let field):
            return "Preencha o campo obrigatório: \(field)."
        case .invalidDayOfWeek(let day):
            return "Dia da semana inválido (\(day)). Selecione entre SEG (1) e SÁB (6)."
        case .invalidTimeRange(let start, let end):
            return "Horário inválido (\(start)–\(end)). O término deve ser após o início."
        case .courseNotFound(let id):
            return "Disciplina não encontrada (\(id))."
        case .scheduleConflict(let existingCourseName, let dayLabel, let startTime):
            return "Conflito de horário: já existe '\(existingCourseName)' em \(dayLabel) às \(startTime)."
        }
    }
}
