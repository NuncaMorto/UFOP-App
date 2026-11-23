import Foundation
import Observation

#if canImport(CampusCoreFFI)
import CampusCoreFFI
#endif

// MARK: - UniFFI Domain Enumerations

/// Paleta de cores de departamentos acadêmicos da UFOP (espelha `DepartmentColorKey` do UniFFI).
public enum DepartmentColorKey: String, CaseIterable, Codable, Identifiable, Sendable {
    case mathBlue = "MATH_BLUE"
    case compPurple = "COMP_PURPLE"
    case algebraGreen = "ALGEBRA_GREEN"
    case physicsAmber = "PHYSICS_AMBER"

    public var id: String { rawValue }

    public var hexString: String {
        switch self {
        case .mathBlue:
            return "#3B68FF"
        case .compPurple:
            return "#6E56CF"
        case .algebraGreen:
            return "#2E7D32"
        case .physicsAmber:
            return "#8D5B16"
        }
    }

    public var departmentLabel: String {
        switch self {
        case .mathBlue:
            return "Matemática"
        case .compPurple:
            return "Computação"
        case .algebraGreen:
            return "Álgebra Linear"
        case .physicsAmber:
            return "Física"
        }
    }

    public var accessibilityLabel: String {
        switch self {
        case .mathBlue:
            return "Azul Matemática"
        case .compPurple:
            return "Roxo Computação"
        case .algebraGreen:
            return "Verde Álgebra Linear"
        case .physicsAmber:
            return "Âmbar Física"
        }
    }

    public static func fromHex(_ hex: String) -> DepartmentColorKey {
        let normalized = hex.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        switch normalized {
        case "#6E56CF":
            return .compPurple
        case "#2E7D32":
            return .algebraGreen
        case "#8D5B16":
            return .physicsAmber
        default:
            return .mathBlue
        }
    }
}

/// Estado de presença de uma aula na Agenda (espelha `AttendanceStatus` do UniFFI).
/// Regra crítica: `.cancelled` incrementa `cancelledClasses` e NÃO soma falta em `currentAbsences`.
public enum AttendanceStatus: String, CaseIterable, Codable, Identifiable, Sendable {
    case attending = "ATTENDING"
    case absent = "ABSENT"
    case cancelled = "CANCELLED"

    public var id: String { rawValue }

    public var buttonTitle: String {
        switch self {
        case .attending:
            return "Vou à aula"
        case .absent:
            return "Vou faltar"
        case .cancelled:
            return "Aula cancelada"
        }
    }

    public var badgeTitle: String {
        switch self {
        case .attending:
            return "Presença confirmada"
        case .absent:
            return "Falta registrada"
        case .cancelled:
            return "Aula cancelada"
        }
    }

    public var systemImage: String {
        switch self {
        case .attending:
            return "checkmark.circle.fill"
        case .absent:
            return "xmark.circle.fill"
        case .cancelled:
            return "slash.circle.fill"
        }
    }
}

/// Tipo de item na linha do tempo da Agenda.
public enum TimelineItemKind: String, Codable, Sendable {
    case lecture = "LECTURE"
    case commitment = "COMMITMENT"
}

// MARK: - Sprint 2 UFOP Multi-Campus & Calendar Models

/// Informações de um campus da UFOP (`Ouro Preto · Morro do Cruzeiro`, `Mariana · ICHS/ICSA`, `João Monlevade · ICEA`).
public struct UfopCampusInfo: Identifiable, Equatable, Codable, Sendable {
    public let id: String
    public let name: String
    public let city: String
    public var weatherTempC: Int
    public var weatherCondition: String
    public var isActive: Bool

    /// Rótulo completo (`Ouro Preto · Morro do Cruzeiro`, `Mariana · ICHS/ICSA`, `João Monlevade · ICEA`).
    public var fullLabel: String {
        "\(city) · \(name)"
    }

    /// Badge compacto para o cabeçalho da Agenda (`Ouro Preto · Morro do Cruzeiro · 22 °C`).
    public var agendaHeaderBadgeText: String {
        "\(city) · \(name) · \(weatherTempC) °C"
    }

    /// Pílula curta de clima (`☀️ 22 °C`).
    public var weatherBadgeLabel: String {
        "☀️ \(weatherTempC) °C"
    }

    public init(
        id: String,
        name: String,
        city: String,
        weatherTempC: Int,
        weatherCondition: String,
        isActive: Bool
    ) {
        self.id = id
        self.name = name
        self.city = city
        self.weatherTempC = weatherTempC
        self.weatherCondition = weatherCondition
        self.isActive = isActive
    }
}

/// Marco oficial do Calendário Acadêmico UFOP 2026.2 (`18 NOV`, `02 DEZ`, `12 DEZ`, `15 DEZ`).
public struct AcademicCalendarEvent: Identifiable, Equatable, Codable, Sendable {
    public let id: String
    public let semester: String
    public let eventDate: String
    public let dayNumber: String
    public let monthShort: String
    public let title: String
    public let category: String

    public var dateBadgeText: String {
        "\(dayNumber) \(monthShort)"
    }

    public init(
        id: String,
        semester: String,
        eventDate: String,
        dayNumber: String,
        monthShort: String,
        title: String,
        category: String
    ) {
        self.id = id
        self.semester = semester
        self.eventDate = eventDate
        self.dayNumber = dayNumber
        self.monthShort = monthShort
        self.title = title
        self.category = category
    }
}

/// Resumo consolidado do perfil acadêmico UFOP 2026.2.
public struct AcademicProfileSummary: Equatable, Codable, Sendable {
    public let semester: String
    public let activeCampus: UfopCampusInfo
    public let campuses: [UfopCampusInfo]
    public let totalCourses: Int
    public let totalAbsences: Int
    public let totalMaxAbsences: Int
    public let totalCancelledClasses: Int
    public let coursesAtRisk: Int
    public let pendingAssessments: Int
    public let completedAssessments: Int
    public let calendarEvents: [AcademicCalendarEvent]

    public init(
        semester: String,
        activeCampus: UfopCampusInfo,
        campuses: [UfopCampusInfo],
        totalCourses: Int,
        totalAbsences: Int,
        totalMaxAbsences: Int,
        totalCancelledClasses: Int,
        coursesAtRisk: Int,
        pendingAssessments: Int,
        completedAssessments: Int,
        calendarEvents: [AcademicCalendarEvent]
    ) {
        self.semester = semester
        self.activeCampus = activeCampus
        self.campuses = campuses
        self.totalCourses = totalCourses
        self.totalAbsences = totalAbsences
        self.totalMaxAbsences = totalMaxAbsences
        self.totalCancelledClasses = totalCancelledClasses
        self.coursesAtRisk = coursesAtRisk
        self.pendingAssessments = pendingAssessments
        self.completedAssessments = completedAssessments
        self.calendarEvents = calendarEvents
    }
}

// MARK: - UniFFI Domain Records

/// Pílula diária na faixa horizontal da semana (`SEG 16` a `DOM 22`).
public struct AgendaDayChip: Identifiable, Equatable, Codable, Sendable {
    public let dateIso: String
    public let weekdayShort: String
    public let dayNumber: String
    public var isSelected: Bool
    public var hasEvents: Bool

    public var id: String { dateIso }

    public init(
        dateIso: String,
        weekdayShort: String,
        dayNumber: String,
        isSelected: Bool,
        hasEvents: Bool
    ) {
        self.dateIso = dateIso
        self.weekdayShort = weekdayShort
        self.dayNumber = dayNumber
        self.isSelected = isSelected
        self.hasEvents = hasEvents
    }
}

/// Card individual na timeline diária da aba Agenda.
public struct AgendaTimelineItem: Identifiable, Equatable, Codable, Sendable {
    public let id: String
    public let disciplineId: String?
    public let kind: TimelineItemKind
    public let startTime: String
    public let endTime: String
    public var title: String
    public var subtitle: String
    public var location: String
    public var professor: String
    public var department: String
    public var colorKey: DepartmentColorKey
    public var weatherTempC: Int
    public var weatherCondition: String
    public var attendanceStatus: AttendanceStatus

    public var timeRangeLabel: String {
        "\(startTime)–\(endTime)"
    }

    public var weatherBadgeLabel: String {
        "☀️ \(weatherTempC) °C"
    }

    public var weatherDetailLabel: String {
        "\(weatherTempC) °C · \(weatherCondition)"
    }

    public var isClassCheckInEnabled: Bool {
        kind == .lecture && disciplineId != nil
    }

    public var isCustomCommitment: Bool {
        kind == .commitment
    }

    public init(
        id: String,
        disciplineId: String?,
        kind: TimelineItemKind,
        startTime: String,
        endTime: String,
        title: String,
        subtitle: String,
        location: String,
        professor: String,
        department: String,
        colorKey: DepartmentColorKey,
        weatherTempC: Int,
        weatherCondition: String,
        attendanceStatus: AttendanceStatus
    ) {
        self.id = id
        self.disciplineId = disciplineId
        self.kind = kind
        self.startTime = startTime
        self.endTime = endTime
        self.title = title
        self.subtitle = subtitle
        self.location = location
        self.professor = professor
        self.department = department
        self.colorKey = colorKey
        self.weatherTempC = weatherTempC
        self.weatherCondition = weatherCondition
        self.attendanceStatus = attendanceStatus
    }
}

/// Visão consolidada do dia selecionado na aba Agenda.
public struct AgendaDayOverview: Equatable, Codable, Sendable {
    public let semesterTag: String
    public let monthLabel: String
    public let weekLabel: String
    public let selectedDateIso: String
    public let fullDateHeader: String
    public let summarySubtitle: String
    public let weekDays: [AgendaDayChip]
    public let timelineItems: [AgendaTimelineItem]

    public init(
        semesterTag: String,
        monthLabel: String,
        weekLabel: String,
        selectedDateIso: String,
        fullDateHeader: String,
        summarySubtitle: String,
        weekDays: [AgendaDayChip],
        timelineItems: [AgendaTimelineItem]
    ) {
        self.semesterTag = semesterTag
        self.monthLabel = monthLabel
        self.weekLabel = weekLabel
        self.selectedDateIso = selectedDateIso
        self.fullDateHeader = fullDateHeader
        self.summarySubtitle = summarySubtitle
        self.weekDays = weekDays
        self.timelineItems = timelineItems
    }
}

/// Modelo da folha modal `ClassCheckInSheetView` (Check-in de presença na aula).
public struct ClassCheckInDetail: Identifiable, Equatable, Codable, Sendable {
    public let agendaItemId: String
    public let disciplineId: String
    public let department: String
    public let disciplineName: String
    public let fullDateLabel: String
    public let timeRange: String
    public let weatherLabel: String
    public let location: String
    public let professor: String
    public var selectedColorKey: DepartmentColorKey
    public var selectedStatus: AttendanceStatus
    public let footnoteText: String

    public var id: String { agendaItemId }

    public init(
        agendaItemId: String,
        disciplineId: String,
        department: String,
        disciplineName: String,
        fullDateLabel: String,
        timeRange: String,
        weatherLabel: String,
        location: String,
        professor: String,
        selectedColorKey: DepartmentColorKey,
        selectedStatus: AttendanceStatus,
        footnoteText: String = "Aula cancelada não conta como falta. Salve para atualizar o relatório em Disciplinas."
    ) {
        self.agendaItemId = agendaItemId
        self.disciplineId = disciplineId
        self.department = department
        self.disciplineName = disciplineName
        self.fullDateLabel = fullDateLabel
        self.timeRange = timeRange
        self.weatherLabel = weatherLabel
        self.location = location
        self.professor = professor
        self.selectedColorKey = selectedColorKey
        self.selectedStatus = selectedStatus
        self.footnoteText = footnoteText
    }
}

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

// MARK: - Schedule Export / Import JSON Payload Models

public struct ScheduleExportPayload: Codable, Sendable {
    public let semester: String
    public let activeCampusId: String
    public let disciplines: [DisciplineSummaryCard]
    public let timetableSlots: [TimetableSlotCell]
    public let courseScheduleSlots: [CourseScheduleSlotItem]?
    public let assessmentsByDiscipline: [String: [AssessmentItem]]?
    public let notesByDiscipline: [String: String]?

    public init(
        semester: String,
        activeCampusId: String,
        disciplines: [DisciplineSummaryCard],
        timetableSlots: [TimetableSlotCell],
        courseScheduleSlots: [CourseScheduleSlotItem]? = nil,
        assessmentsByDiscipline: [String: [AssessmentItem]]? = nil,
        notesByDiscipline: [String: String]? = nil
    ) {
        self.semester = semester
        self.activeCampusId = activeCampusId
        self.disciplines = disciplines
        self.timetableSlots = timetableSlots
        self.courseScheduleSlots = courseScheduleSlots
        self.assessmentsByDiscipline = assessmentsByDiscipline
        self.notesByDiscipline = notesByDiscipline
    }
}


// MARK: - Student Onboarding Profile Model (v1.8.0+)

public struct StudentProfile: Equatable, Codable, Sendable {
    public var fullName: String
    public var registrationNumber: String
    public var courseName: String
    public var campusName: String
    public var activeSemester: String
    public var semesterStartDate: String
    public var semesterEndDate: String
    public var onboardingCompleted: Bool

    public init(
        fullName: String = "",
        registrationNumber: String = "",
        courseName: String = "",
        campusName: String = "Ouro Preto",
        activeSemester: String = "2026.2",
        semesterStartDate: String = "2026-08-24",
        semesterEndDate: String = "2026-12-23",
        onboardingCompleted: Bool = false
    ) {
        self.fullName = fullName
        self.registrationNumber = registrationNumber
        self.courseName = courseName
        self.campusName = campusName
        self.activeSemester = activeSemester
        self.semesterStartDate = semesterStartDate
        self.semesterEndDate = semesterEndDate
        self.onboardingCompleted = onboardingCompleted
    }
}
