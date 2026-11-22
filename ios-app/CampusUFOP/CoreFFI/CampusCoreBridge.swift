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

// MARK: - Reactive CampusStore (@Observable UniFFI Bridge + UFOP 2026.2+ Engine)

@Observable
public final class CampusStore {
    // Estado global compartilhado entre as abas Agenda, Disciplinas, Atividades e Perfil
    public var selectedDateIso: String = "2026-11-17"
    public var selectedDepartmentFilter: String = "Todos os departamentos"
    public var selectedSemesterFilter: String = "2026.2"
    public var selectedTimetableFilter: String = "Minha grade"

    public var studentProfile: StudentProfile = StudentProfile()
    public var transcriptEntries: [TranscriptCourseEntry] = []
    public var isEadByCourseId: [String: Bool] = [:]
    public var turmaByCourseId: [String: String] = [:]
    public var dynamicGridCompletedByMatrix: [String: [String]] = [:]

    public var disciplinesLayoutMode: String {
        didSet { UserDefaults.standard.set(disciplinesLayoutMode, forKey: "campus.ufop.disciplinesLayoutMode") }
    }

    public var timetableOrientation: String {
        didSet { UserDefaults.standard.set(timetableOrientation, forKey: "campus.ufop.timetableOrientation") }
    }

    public var dynamicGridOrientation: String {
        didSet { UserDefaults.standard.set(dynamicGridOrientation, forKey: "campus.ufop.dynamicGridOrientation") }
    }

    public var guidedTourCompleted: Bool {
        didSet { UserDefaults.standard.set(guidedTourCompleted, forKey: "campus.ufop.guidedTourCompleted") }
    }

    public var lastSeenReleaseVersion: String {
        didSet { UserDefaults.standard.set(lastSeenReleaseVersion, forKey: "campus.ufop.lastSeenReleaseVersion") }
    }

    public internal(set) var disciplines: [DisciplineSummaryCard] = []
    public internal(set) var assessmentsByDiscipline: [String: [AssessmentItem]] = [:]
    public internal(set) var notesByDiscipline: [String: String] = [:]
    public internal(set) var agendaItemsByDate: [String: [AgendaTimelineItem]] = [:]
    public internal(set) var timetableSlots: [TimetableSlotCell] = []
    public internal(set) var courseScheduleSlots: [CourseScheduleSlotItem] = []
    public internal(set) var activeTemplateId: String? = nil
    public internal(set) var academicActivities: [AcademicActivityItem] = []
    public internal(set) var campuses: [UfopCampusInfo] = []
    public internal(set) var calendarEvents: [AcademicCalendarEvent] = []

    /// Rastreia o status anteriormente salvo por item de agenda para garantir idempotência
    /// na transição entre `.attending`, `.absent` e `.cancelled`.
    internal var recordedStatusByAgendaItem: [String: AttendanceStatus] = [:]

    /// Sprint 4: Histórico de marcações (`.absent` / `.cancelled`) por sessão (`agendaItemId`).
    public internal(set) var absenceHistoryBySession: [String: AbsenceHistoryEntry] = [:]

    internal var hasCompletedInit: Bool = false

    public init() {
        let defaults = UserDefaults.standard
        self.disciplinesLayoutMode = defaults.string(forKey: "campus.ufop.disciplinesLayoutMode") ?? "grid"
        self.timetableOrientation = defaults.string(forKey: "campus.ufop.timetableOrientation") ?? "vertical"
        self.dynamicGridOrientation = defaults.string(forKey: "campus.ufop.dynamicGridOrientation") ?? "horizontal"
        self.guidedTourCompleted = defaults.bool(forKey: "campus.ufop.guidedTourCompleted")
        self.lastSeenReleaseVersion = defaults.string(forKey: "campus.ufop.lastSeenReleaseVersion") ?? ""

        seedUFOP2026_2Data()
        loadPersistedEncryptedStateIfAvailable()
        ensureOfficialCalendarEventsLoaded()
        _ = applyRoomAllocationCatalog()
        self.selectedDateIso = Self.todayIsoDateString()
        self.hasCompletedInit = true
    }

    // MARK: - UFOP 2026.2 Deterministic Seeds (PDF Spec + Sprint 2 Scale)

    public func seedUFOP2026_2Data() {
        self.selectedDepartmentFilter = "Todos os departamentos"
        self.selectedSemesterFilter = "2026.2"
        self.selectedTimetableFilter = "Minha grade"
        self.activeTemplateId = nil

        self.campuses = [
            UfopCampusInfo(
                id: "op_cruzeiro",
                name: "Morro do Cruzeiro",
                city: "Ouro Preto",
                weatherTempC: 22,
                weatherCondition: "Ensolarado",
                isActive: true
            ),
            UfopCampusInfo(
                id: "mar_ichs",
                name: "ICHS/ICSA",
                city: "Mariana",
                weatherTempC: 25,
                weatherCondition: "Parcialmente nublado",
                isActive: false
            ),
            UfopCampusInfo(
                id: "jm_icea",
                name: "ICEA",
                city: "João Monlevade",
                weatherTempC: 26,
                weatherCondition: "Ensolarado",
                isActive: false
            )
        ]

        self.calendarEvents = Self.officialCalendarEventsSeed()

        self.disciplines = []
        self.assessmentsByDiscipline = [:]
        self.notesByDiscipline = [:]
        self.agendaItemsByDate = [:]
        self.recordedStatusByAgendaItem = [:]
        self.absenceHistoryBySession = [:]
        self.timetableSlots = []
        self.courseScheduleSlots = []

        restoreDefault2026_2SeedData()
    }

    /// Sprint 4: Restaura o semestre padrão UFOP 2026.2 (`Cálculo II`, `Algoritmos`, `Álgebra Linear`, `Física I`),
    /// seus horários semanais, sessões da agenda e avaliações, preservando cursos de outros semestres.
    @discardableResult
    public func resetSemesterToDefaultSeed(semester: String = "2026.2") -> Int {
        let trimmed = semester.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanSemester = trimmed.isEmpty ? "2026.2" : trimmed

        let existingIds = disciplines
            .filter { $0.semester == cleanSemester }
            .map(\.id)
        for existingId in existingIds {
            deleteCourse(courseId: existingId)
        }

        if cleanSemester == "2026.2" {
            timetableSlots.removeAll { $0.id == "slot-ter-14" }
            restoreDefault2026_2SeedData()
        } else {
            selectedDepartmentFilter = "Todos os departamentos"
            selectedSemesterFilter = cleanSemester
            selectedTimetableFilter = "Minha grade"
            activeTemplateId = nil
            rebuildAcademicActivitiesFromAssessments()
        }

        return disciplines.filter { $0.semester == cleanSemester }.count
    }

    private func restoreDefault2026_2SeedData() {
        self.selectedDepartmentFilter = "Todos os departamentos"
        self.selectedSemesterFilter = "2026.2"
        self.selectedTimetableFilter = "Minha grade"
        self.activeTemplateId = nil

        let defaultDisciplines: [DisciplineSummaryCard] = [
            DisciplineSummaryCard(
                id: "calc-ii",
                code: "MAT202",
                name: "Cálculo II",
                department: "Matemática",
                semester: "2026.2",
                workloadHours: 60,
                currentAbsences: 4,
                maxAbsences: 18,
                cancelledClasses: 0,
                colorKey: .mathBlue,
                professor: "Prof.ª Ana Martins",
                location: "Prédio A · Sala 204"
            ),
            DisciplineSummaryCard(
                id: "algoritmos",
                code: "BCC201",
                name: "Algoritmos",
                department: "Computação",
                semester: "2026.2",
                workloadHours: 60,
                currentAbsences: 2,
                maxAbsences: 18,
                cancelledClasses: 0,
                colorKey: .compPurple,
                professor: "Prof. Carlos Eduardo",
                location: "ICEB · Lab 302"
            ),
            DisciplineSummaryCard(
                id: "algebra-linear",
                code: "MAT215",
                name: "Álgebra Linear",
                department: "Matemática",
                semester: "2026.2",
                workloadHours: 90,
                currentAbsences: 1,
                maxAbsences: 27,
                cancelledClasses: 0,
                colorKey: .algebraGreen,
                professor: "Prof. Roberto Menezes",
                location: "Prédio A · Sala 108"
            ),
            DisciplineSummaryCard(
                id: "fisica-i",
                code: "FIS101",
                name: "Física I",
                department: "Física",
                semester: "2026.2",
                workloadHours: 30,
                currentAbsences: 3,
                maxAbsences: 9,
                cancelledClasses: 0,
                colorKey: .physicsAmber,
                professor: "Prof.ª Helena Costa",
                location: "Bauxita · Pavilhão 105"
            )
        ]

        let defaultIds = Set(defaultDisciplines.map(\.id))
        self.disciplines.removeAll { defaultIds.contains($0.id) || $0.semester == "2026.2" }
        self.disciplines = defaultDisciplines + self.disciplines

        let defaultAssessments: [String: [AssessmentItem]] = [
            "calc-ii": [
                AssessmentItem(
                    id: "calc-ii-a1",
                    courseId: "calc-ii",
                    dayNumber: "20",
                    monthShort: "NOV",
                    title: "Lista de integrais",
                    subtitle: "Trabalho · 23:59",
                    kind: "Trabalho",
                    dueDate: "2026-11-20",
                    dueTime: "23:59",
                    completed: false
                ),
                AssessmentItem(
                    id: "calc-ii-a2",
                    courseId: "calc-ii",
                    dayNumber: "24",
                    monthShort: "NOV",
                    title: "Aplicações de integrais",
                    subtitle: "Seminário · 10:00",
                    kind: "Seminário",
                    dueDate: "2026-11-24",
                    dueTime: "10:00",
                    completed: false
                ),
                AssessmentItem(
                    id: "calc-ii-a3",
                    courseId: "calc-ii",
                    dayNumber: "27",
                    monthShort: "NOV",
                    title: "Prova 2",
                    subtitle: "Prova · 08:00",
                    kind: "Prova",
                    dueDate: "2026-11-27",
                    dueTime: "08:00",
                    completed: false
                )
            ],
            "algoritmos": [
                AssessmentItem(
                    id: "alg-a1",
                    courseId: "algoritmos",
                    dayNumber: "19",
                    monthShort: "NOV",
                    title: "Lab de Árvores Balanceadas",
                    subtitle: "Trabalho · 10:00",
                    kind: "Trabalho",
                    dueDate: "2026-11-19",
                    dueTime: "10:00",
                    completed: false
                ),
                AssessmentItem(
                    id: "alg-a2",
                    courseId: "algoritmos",
                    dayNumber: "26",
                    monthShort: "NOV",
                    title: "Trabalho Prático 2",
                    subtitle: "Trabalho · 23:59",
                    kind: "Trabalho",
                    dueDate: "2026-11-26",
                    dueTime: "23:59",
                    completed: false
                ),
                AssessmentItem(
                    id: "alg-a3",
                    courseId: "algoritmos",
                    dayNumber: "03",
                    monthShort: "DEZ",
                    title: "Prova Teórica 2",
                    subtitle: "Prova · 10:00",
                    kind: "Prova",
                    dueDate: "2026-12-03",
                    dueTime: "10:00",
                    completed: false
                )
            ],
            "algebra-linear": [
                AssessmentItem(
                    id: "alg-lin-a1",
                    courseId: "algebra-linear",
                    dayNumber: "21",
                    monthShort: "NOV",
                    title: "Lista de Transformações Lineares",
                    subtitle: "Trabalho · 18:00",
                    kind: "Trabalho",
                    dueDate: "2026-11-21",
                    dueTime: "18:00",
                    completed: false
                ),
                AssessmentItem(
                    id: "alg-lin-a2",
                    courseId: "algebra-linear",
                    dayNumber: "28",
                    monthShort: "NOV",
                    title: "Teste de Autovalores",
                    subtitle: "Seminário · 08:00",
                    kind: "Seminário",
                    dueDate: "2026-11-28",
                    dueTime: "08:00",
                    completed: false
                ),
                AssessmentItem(
                    id: "alg-lin-a3",
                    courseId: "algebra-linear",
                    dayNumber: "05",
                    monthShort: "DEZ",
                    title: "Prova 2",
                    subtitle: "Prova · 08:00",
                    kind: "Prova",
                    dueDate: "2026-12-05",
                    dueTime: "08:00",
                    completed: false
                )
            ],
            "fisica-i": [
                AssessmentItem(
                    id: "fis-a1",
                    courseId: "fisica-i",
                    dayNumber: "18",
                    monthShort: "NOV",
                    title: "Relatório de Conservação de Energia",
                    subtitle: "Trabalho · 14:00",
                    kind: "Trabalho",
                    dueDate: "2026-11-18",
                    dueTime: "14:00",
                    completed: false
                ),
                AssessmentItem(
                    id: "fis-a2",
                    courseId: "fisica-i",
                    dayNumber: "25",
                    monthShort: "NOV",
                    title: "Lista de Dinâmica Rotacional",
                    subtitle: "Trabalho · 18:00",
                    kind: "Trabalho",
                    dueDate: "2026-11-25",
                    dueTime: "18:00",
                    completed: false
                ),
                AssessmentItem(
                    id: "fis-a3",
                    courseId: "fisica-i",
                    dayNumber: "02",
                    monthShort: "DEZ",
                    title: "Prova 2",
                    subtitle: "Prova · 14:00",
                    kind: "Prova",
                    dueDate: "2026-12-02",
                    dueTime: "14:00",
                    completed: false
                )
            ]
        ]

        for (courseId, items) in defaultAssessments {
            self.assessmentsByDiscipline[courseId] = items
        }

        let defaultNotes: [String: String] = [
            "calc-ii": "Revisar integrais por partes.",
            "algoritmos": "Praticar complexidade assintótica e rotação de árvores AVL.",
            "algebra-linear": "Conferir teorema do núcleo e da imagem para a lista de sexta.",
            "fisica-i": "Levar papel milimetrado e calculadora científica para o laboratório."
        ]
        for (courseId, note) in defaultNotes {
            self.notesByDiscipline[courseId] = note
        }

        // Agenda da Semana 47 (16 a 22 de novembro de 2026)
        let defaultAgendaItemsByDate: [String: [AgendaTimelineItem]] = [
            "2026-11-16": [
                AgendaTimelineItem(
                    id: "seg-16-alg-linear",
                    disciplineId: "algebra-linear",
                    kind: .lecture,
                    startTime: "08:00",
                    endTime: "09:40",
                    title: "Álgebra Linear",
                    subtitle: "Prédio A · Sala 108",
                    location: "Prédio A · Sala 108",
                    professor: "Prof. Roberto Menezes",
                    department: "Matemática",
                    colorKey: .algebraGreen,
                    weatherTempC: 21,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                ),
                AgendaTimelineItem(
                    id: "seg-16-fisica",
                    disciplineId: "fisica-i",
                    kind: .lecture,
                    startTime: "10:00",
                    endTime: "11:40",
                    title: "Física I",
                    subtitle: "Bauxita · Pavilhão 105",
                    location: "Bauxita · Pavilhão 105",
                    professor: "Prof.ª Helena Costa",
                    department: "Física",
                    colorKey: .physicsAmber,
                    weatherTempC: 23,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                )
            ],
            "2026-11-17": [
                AgendaTimelineItem(
                    id: "ter-17-calc-ii",
                    disciplineId: "calc-ii",
                    kind: .lecture,
                    startTime: "08:00",
                    endTime: "09:40",
                    title: "Cálculo II",
                    subtitle: "Prédio A · Sala 204",
                    location: "Prédio A · Sala 204",
                    professor: "Prof.ª Ana Martins",
                    department: "Matemática",
                    colorKey: .mathBlue,
                    weatherTempC: 22,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                ),
                AgendaTimelineItem(
                    id: "ter-17-algoritmos",
                    disciplineId: "algoritmos",
                    kind: .lecture,
                    startTime: "10:00",
                    endTime: "11:40",
                    title: "Algoritmos",
                    subtitle: "ICEB · Lab 302",
                    location: "ICEB · Lab 302",
                    professor: "Prof. Carlos Eduardo",
                    department: "Computação",
                    colorKey: .compPurple,
                    weatherTempC: 24,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                ),
                AgendaTimelineItem(
                    id: "ter-17-monitoria",
                    disciplineId: nil,
                    kind: .commitment,
                    startTime: "14:00",
                    endTime: "15:00",
                    title: "Grupo de estudos · Cálculo II",
                    subtitle: "Biblioteca Central · Sala 03",
                    location: "Biblioteca Central · Sala 03",
                    professor: "Compromisso acadêmico",
                    department: "Compromisso",
                    colorKey: .algebraGreen,
                    weatherTempC: 26,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                )
            ],
            "2026-11-18": [
                AgendaTimelineItem(
                    id: "qua-18-alg-linear",
                    disciplineId: "algebra-linear",
                    kind: .lecture,
                    startTime: "08:00",
                    endTime: "09:40",
                    title: "Álgebra Linear",
                    subtitle: "Prédio A · Sala 108",
                    location: "Prédio A · Sala 108",
                    professor: "Prof. Roberto Menezes",
                    department: "Matemática",
                    colorKey: .algebraGreen,
                    weatherTempC: 22,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                ),
                AgendaTimelineItem(
                    id: "qua-18-fisica",
                    disciplineId: "fisica-i",
                    kind: .lecture,
                    startTime: "14:00",
                    endTime: "15:00",
                    title: "Física I",
                    subtitle: "Bauxita · Pavilhão 105",
                    location: "Bauxita · Pavilhão 105",
                    professor: "Prof.ª Helena Costa",
                    department: "Física",
                    colorKey: .physicsAmber,
                    weatherTempC: 25,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                )
            ],
            "2026-11-19": [
                AgendaTimelineItem(
                    id: "qui-19-calc-ii",
                    disciplineId: "calc-ii",
                    kind: .lecture,
                    startTime: "08:00",
                    endTime: "09:40",
                    title: "Cálculo II",
                    subtitle: "Prédio A · Sala 204",
                    location: "Prédio A · Sala 204",
                    professor: "Prof.ª Ana Martins",
                    department: "Matemática",
                    colorKey: .mathBlue,
                    weatherTempC: 22,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                ),
                AgendaTimelineItem(
                    id: "qui-19-algoritmos",
                    disciplineId: "algoritmos",
                    kind: .lecture,
                    startTime: "10:00",
                    endTime: "11:40",
                    title: "Algoritmos",
                    subtitle: "ICEB · Lab 302",
                    location: "ICEB · Lab 302",
                    professor: "Prof. Carlos Eduardo",
                    department: "Computação",
                    colorKey: .compPurple,
                    weatherTempC: 24,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                )
            ],
            "2026-11-20": [
                AgendaTimelineItem(
                    id: "sex-20-alg-linear",
                    disciplineId: "algebra-linear",
                    kind: .lecture,
                    startTime: "10:00",
                    endTime: "11:40",
                    title: "Álgebra Linear",
                    subtitle: "Prédio A · Sala 108",
                    location: "Prédio A · Sala 108",
                    professor: "Prof. Roberto Menezes",
                    department: "Matemática",
                    colorKey: .algebraGreen,
                    weatherTempC: 23,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                )
            ],
            "2026-11-21": [
                AgendaTimelineItem(
                    id: "sab-21-revisao",
                    disciplineId: nil,
                    kind: .commitment,
                    startTime: "08:00",
                    endTime: "09:40",
                    title: "Revisão · Lista de Integrais",
                    subtitle: "Centro de Convivência · UFOP",
                    location: "Centro de Convivência · UFOP",
                    professor: "Estudo autônomo",
                    department: "Compromisso",
                    colorKey: .mathBlue,
                    weatherTempC: 21,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                )
            ],
            "2026-11-22": []
        ]

        // Mescla e inicializa mapa de status registrados sem duplicar sessões
        for (dateIso, defaultItems) in defaultAgendaItemsByDate {
            var merged = self.agendaItemsByDate[dateIso] ?? []
            let restoredIds = Set(defaultItems.map(\.id))
            merged.removeAll { restoredIds.contains($0.id) }
            merged.append(contentsOf: defaultItems)
            merged.sort { $0.startTime < $1.startTime }
            self.agendaItemsByDate[dateIso] = merged

            for item in defaultItems {
                recordedStatusByAgendaItem[item.id] = item.attendanceStatus
                absenceHistoryBySession.removeValue(forKey: item.id)
            }
        }

        // Grade de horários semanal (SEG a SÁB × 08:00–09:40, 10:00–11:40, 14:00–15:00)
        let defaultTimetableSlots: [TimetableSlotCell] = [
            // 08:00–09:40
            TimetableSlotCell(
                id: "slot-seg-08",
                weekdayShort: "SEG",
                timeRange: "08:00–09:40",
                disciplineId: "algebra-linear",
                disciplineShortName: "Álgebra Linear",
                roomLabel: "Sala 108",
                colorKey: .algebraGreen
            ),
            TimetableSlotCell(
                id: "slot-ter-08",
                weekdayShort: "TER",
                timeRange: "08:00–09:40",
                disciplineId: "calc-ii",
                disciplineShortName: "Cálculo II",
                roomLabel: "Sala 204",
                colorKey: .mathBlue
            ),
            TimetableSlotCell(
                id: "slot-qua-08",
                weekdayShort: "QUA",
                timeRange: "08:00–09:40",
                disciplineId: "algebra-linear",
                disciplineShortName: "Álgebra Linear",
                roomLabel: "Sala 108",
                colorKey: .algebraGreen
            ),
            TimetableSlotCell(
                id: "slot-qui-08",
                weekdayShort: "QUI",
                timeRange: "08:00–09:40",
                disciplineId: "calc-ii",
                disciplineShortName: "Cálculo II",
                roomLabel: "Sala 204",
                colorKey: .mathBlue
            ),

            // 10:00–11:40
            TimetableSlotCell(
                id: "slot-seg-10",
                weekdayShort: "SEG",
                timeRange: "10:00–11:40",
                disciplineId: "fisica-i",
                disciplineShortName: "Física I",
                roomLabel: "Pav. 105",
                colorKey: .physicsAmber
            ),
            TimetableSlotCell(
                id: "slot-ter-10",
                weekdayShort: "TER",
                timeRange: "10:00–11:40",
                disciplineId: "algoritmos",
                disciplineShortName: "Algoritmos",
                roomLabel: "Lab 302",
                colorKey: .compPurple
            ),
            TimetableSlotCell(
                id: "slot-qui-10",
                weekdayShort: "QUI",
                timeRange: "10:00–11:40",
                disciplineId: "algoritmos",
                disciplineShortName: "Algoritmos",
                roomLabel: "Lab 302",
                colorKey: .compPurple
            ),
            TimetableSlotCell(
                id: "slot-sex-10",
                weekdayShort: "SEX",
                timeRange: "10:00–11:40",
                disciplineId: "algebra-linear",
                disciplineShortName: "Álgebra Linear",
                roomLabel: "Sala 108",
                colorKey: .algebraGreen
            ),

            // 14:00–15:00
            TimetableSlotCell(
                id: "slot-ter-14",
                weekdayShort: "TER",
                timeRange: "14:00–15:00",
                disciplineId: "calc-ii",
                disciplineShortName: "Monitoria Cálc. II",
                roomLabel: "Bibl. 03",
                colorKey: .mathBlue
            ),
            TimetableSlotCell(
                id: "slot-qua-14",
                weekdayShort: "QUA",
                timeRange: "14:00–15:00",
                disciplineId: "fisica-i",
                disciplineShortName: "Física I",
                roomLabel: "Pav. 105",
                colorKey: .physicsAmber
            )
        ]

        let restoredTimetableIds = Set(defaultTimetableSlots.map(\.id))
        self.timetableSlots.removeAll { restoredTimetableIds.contains($0.id) }
        self.timetableSlots = defaultTimetableSlots + self.timetableSlots

        let defaultCourseScheduleSlots: [CourseScheduleSlotItem] = [
            CourseScheduleSlotItem(
                slotId: "slot-seg-08",
                courseId: "algebra-linear",
                dayOfWeek: 1,
                dayLabel: "SEG",
                startTime: "08:00",
                endTime: "09:40",
                building: "Prédio A",
                room: "Sala 108",
                shortLocation: "Sala 108"
            ),
            CourseScheduleSlotItem(
                slotId: "slot-seg-10",
                courseId: "fisica-i",
                dayOfWeek: 1,
                dayLabel: "SEG",
                startTime: "10:00",
                endTime: "11:40",
                building: "Bauxita",
                room: "Pavilhão 105",
                shortLocation: "Pav. 105"
            ),
            CourseScheduleSlotItem(
                slotId: "slot-ter-08",
                courseId: "calc-ii",
                dayOfWeek: 2,
                dayLabel: "TER",
                startTime: "08:00",
                endTime: "09:40",
                building: "Prédio A",
                room: "Sala 204",
                shortLocation: "Sala 204"
            ),
            CourseScheduleSlotItem(
                slotId: "slot-ter-10",
                courseId: "algoritmos",
                dayOfWeek: 2,
                dayLabel: "TER",
                startTime: "10:00",
                endTime: "11:40",
                building: "ICEB",
                room: "Lab 302",
                shortLocation: "Lab 302"
            ),
            CourseScheduleSlotItem(
                slotId: "slot-qua-08",
                courseId: "algebra-linear",
                dayOfWeek: 3,
                dayLabel: "QUA",
                startTime: "08:00",
                endTime: "09:40",
                building: "Prédio A",
                room: "Sala 108",
                shortLocation: "Sala 108"
            ),
            CourseScheduleSlotItem(
                slotId: "slot-qua-14",
                courseId: "fisica-i",
                dayOfWeek: 3,
                dayLabel: "QUA",
                startTime: "14:00",
                endTime: "15:00",
                building: "Bauxita",
                room: "Pavilhão 105",
                shortLocation: "Pav. 105"
            ),
            CourseScheduleSlotItem(
                slotId: "slot-qui-08",
                courseId: "calc-ii",
                dayOfWeek: 4,
                dayLabel: "QUI",
                startTime: "08:00",
                endTime: "09:40",
                building: "Prédio A",
                room: "Sala 204",
                shortLocation: "Sala 204"
            ),
            CourseScheduleSlotItem(
                slotId: "slot-qui-10",
                courseId: "algoritmos",
                dayOfWeek: 4,
                dayLabel: "QUI",
                startTime: "10:00",
                endTime: "11:40",
                building: "ICEB",
                room: "Lab 302",
                shortLocation: "Lab 302"
            ),
            CourseScheduleSlotItem(
                slotId: "slot-sex-10",
                courseId: "algebra-linear",
                dayOfWeek: 5,
                dayLabel: "SEX",
                startTime: "10:00",
                endTime: "11:40",
                building: "Prédio A",
                room: "Sala 108",
                shortLocation: "Sala 108"
            )
        ]

        let restoredCourseSlotIds = Set(defaultCourseScheduleSlots.map(\.slotId))
        self.courseScheduleSlots.removeAll { restoredCourseSlotIds.contains($0.slotId) }
        self.courseScheduleSlots = defaultCourseScheduleSlots + self.courseScheduleSlots

        rebuildAcademicActivitiesFromAssessments()
    }

    // MARK: - Reactive Sync Between Assessments & Activities

    internal func rebuildAcademicActivitiesFromAssessments() {
        var combined: [AcademicActivityItem] = []

        for discipline in disciplines {
            let items = assessmentsByDiscipline[discipline.id] ?? []
            for item in items {
                let categoryText = item.subtitle.isEmpty
                    ? "\(item.kind) · \(item.dueTime)"
                    : item.subtitle
                combined.append(
                    AcademicActivityItem(
                        id: item.id,
                        disciplineId: discipline.id,
                        disciplineName: discipline.name,
                        department: discipline.department,
                        colorKey: discipline.colorKey,
                        dueDay: item.dayNumber,
                        dueMonth: item.monthShort,
                        title: item.title,
                        categoryLabel: categoryText,
                        kind: item.kind,
                        dueDate: item.dueDate,
                        dueTime: item.dueTime,
                        isCompleted: item.completed
                    )
                )
            }
        }

        combined.sort { lhs, rhs in
            if lhs.dueDate != rhs.dueDate {
                return lhs.dueDate < rhs.dueDate
            }
            return lhs.dueTime < rhs.dueTime
        }

        self.academicActivities = combined
    }

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
            let raw = Set(dynamicGridCompletedByMatrix[matrix.id] ?? dynamicGridCompletedByMatrix[matrixId] ?? [])
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
            persistEncryptedState()
        }

        return result
    }

    public func clearDynamicGrid(matrixId: String) {
        let canonicalId = DynamicCurriculumCatalog.find(byId: matrixId)?.id ?? matrixId
        dynamicGridCompletedByMatrix.removeValue(forKey: canonicalId)
        if canonicalId != matrixId {
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

    private static func resolveDepartmentMetadata(from rawDepartment: String) -> (id: String, label: String, codePrefix: String, defaultColorKey: DepartmentColorKey) {
        let normalized = rawDepartment
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: .diacriticInsensitive, locale: .current)
            .lowercased()

        if normalized == "comp" || normalized.contains("comput") || normalized.contains("decom") || normalized.contains("decsi") {
            return ("comp", "Computação", "BCC", .compPurple)
        }
        if normalized == "phys" || normalized.contains("fis") || normalized.contains("phys") {
            return ("phys", "Física", "FIS", .physicsAmber)
        }
        return ("math", "Matemática", "MAT", .mathBlue)
    }

    private static let weekMetadata: [(iso: String, short: String, num: String, fullHeader: String)] = [
        ("2026-11-16", "SEG", "16", "Segunda-feira, 16 de novembro"),
        ("2026-11-17", "TER", "17", "Terça-feira, 17 de novembro"),
        ("2026-11-18", "QUA", "18", "Quarta-feira, 18 de novembro"),
        ("2026-11-19", "QUI", "19", "Quinta-feira, 19 de novembro"),
        ("2026-11-20", "SEX", "20", "Sexta-feira, 20 de novembro"),
        ("2026-11-21", "SÁB", "21", "Sábado, 21 de novembro"),
        ("2026-11-22", "DOM", "22", "Domingo, 22 de novembro")
    ]

    private static func fullPortugueseDateLabel(for dateIso: String) -> String {
        if let match = weekMetadata.first(where: { $0.iso == dateIso }) {
            return match.fullHeader
        }
        let parser = DateFormatter()
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.dateFormat = "yyyy-MM-dd"
        if let parsed = parser.date(from: dateIso) {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "pt_BR")
            formatter.dateFormat = "EEEE, dd 'de' MMMM"
            let raw = formatter.string(from: parsed)
            return raw.prefix(1).uppercased() + raw.dropFirst()
        }
        return dateIso.isEmpty ? "Terça-feira, 17 de novembro" : dateIso
    }

    private static func weekdayMetadata(for dayOfWeek: Int) -> (shortLabel: String, slug: String, isoDate: String) {
        switch dayOfWeek {
        case 1:
            return ("SEG", "seg", "2026-11-16")
        case 2:
            return ("TER", "ter", "2026-11-17")
        case 3:
            return ("QUA", "qua", "2026-11-18")
        case 4:
            return ("QUI", "qui", "2026-11-19")
        case 5:
            return ("SEX", "sex", "2026-11-20")
        case 6:
            return ("SÁB", "sab", "2026-11-21")
        default:
            return ("DOM", "dom", "2026-11-22")
        }
    }

    private static func normalizeTimeString(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        let parts = trimmed.split(separator: ":")
        if parts.count == 2,
           let hour = Int(parts[0]),
           let minute = Int(parts[1]),
           (0...23).contains(hour),
           (0...59).contains(minute) {
            return String(format: "%02d:%02d", hour, minute)
        }
        return trimmed
    }

    private static func minutesFromTime(_ time: String) -> Int? {
        let parts = time.split(separator: ":")
        guard parts.count == 2,
              let hour = Int(parts[0]),
              let minute = Int(parts[1]),
              (0...23).contains(hour),
              (0...59).contains(minute) else {
            return nil
        }
        return hour * 60 + minute
    }

    private static func minutesFromTimeRangeStart(_ timeRange: String) -> Int {
        let start = timeRange.components(separatedBy: "–").first ?? timeRange
        return minutesFromTime(normalizeTimeString(start)) ?? 9999
    }

    private static func timeRangesOverlap(startA: String, endA: String, startB: String, endB: String) -> Bool {
        guard let aStart = minutesFromTime(startA),
              let aEnd = minutesFromTime(endA),
              let bStart = minutesFromTime(startB),
              let bEnd = minutesFromTime(endB) else {
            return startA == startB
        }
        return aStart < bEnd && bStart < aEnd
    }

    private static func buildShortLocation(building: String, room: String) -> String {
        if isEadText(building) {
            return "EAD"
        }
        let cleanRoom = room.replacingOccurrences(of: "Sala ", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanBuilding = building
            .replacingOccurrences(of: "Prédio ", with: "")
            .replacingOccurrences(of: "Escola de Minas", with: "EM")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanRoom.isEmpty {
            return cleanBuilding
        }
        if cleanBuilding.count <= 8 {
            return "\(cleanBuilding) · \(cleanRoom)"
        }
        return cleanRoom
    }

    private static func isEadText(_ text: String) -> Bool {
        let lower = text.lowercased()
        return lower.contains("ead") || lower.contains("distância") || lower.contains("distancia") || lower.contains("remoto")
    }
}
