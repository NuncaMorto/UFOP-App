import Foundation

// MARK: - UFOP Multi-Campus, Calendar, Schedule Share & Student Profile Models

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

// MARK: - Friend Shared Schedules (.ufopgrade / QR UFOPG1)

public struct FriendCourseEntry: Identifiable, Codable, Equatable, Sendable {
    public let courseId: String
    public let code: String
    public let name: String
    public let departmentId: String

    public var id: String { courseId }

    public init(
        courseId: String,
        code: String,
        name: String,
        departmentId: String
    ) {
        self.courseId = courseId
        self.code = code
        self.name = name
        self.departmentId = departmentId
    }
}

public struct FriendSlotEntry: Identifiable, Codable, Equatable, Sendable {
    public let slotId: String
    public let courseId: String
    public let dayOfWeek: Int
    public let startTime: String
    public let endTime: String
    public let shortLocation: String

    public var id: String { slotId }

    public init(
        slotId: String,
        courseId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        shortLocation: String
    ) {
        self.slotId = slotId
        self.courseId = courseId
        self.dayOfWeek = dayOfWeek
        self.startTime = startTime
        self.endTime = endTime
        self.shortLocation = shortLocation
    }
}

public struct FriendSchedule: Identifiable, Codable, Equatable, Sendable {
    public let id: String
    public let friendName: String
    public let courseName: String
    public let semester: String
    public let avatarBase64: String?
    public let courses: [FriendCourseEntry]
    public let slots: [FriendSlotEntry]

    public init(
        id: String,
        friendName: String,
        courseName: String,
        semester: String,
        avatarBase64: String? = nil,
        courses: [FriendCourseEntry],
        slots: [FriendSlotEntry]
    ) {
        self.id = id
        self.friendName = friendName
        self.courseName = courseName
        self.semester = semester
        self.avatarBase64 = avatarBase64
        self.courses = courses
        self.slots = slots
    }
}
