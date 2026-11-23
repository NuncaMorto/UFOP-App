import Foundation

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


    internal static func resolveDepartmentMetadata(from rawDepartment: String) -> (id: String, label: String, codePrefix: String, defaultColorKey: DepartmentColorKey) {
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

    internal static let weekMetadata: [(iso: String, short: String, num: String, fullHeader: String)] = [
        ("2026-11-16", "SEG", "16", "Segunda-feira, 16 de novembro"),
        ("2026-11-17", "TER", "17", "Terça-feira, 17 de novembro"),
        ("2026-11-18", "QUA", "18", "Quarta-feira, 18 de novembro"),
        ("2026-11-19", "QUI", "19", "Quinta-feira, 19 de novembro"),
        ("2026-11-20", "SEX", "20", "Sexta-feira, 20 de novembro"),
        ("2026-11-21", "SÁB", "21", "Sábado, 21 de novembro"),
        ("2026-11-22", "DOM", "22", "Domingo, 22 de novembro")
    ]

    internal static func fullPortugueseDateLabel(for dateIso: String) -> String {
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

    internal static func weekdayMetadata(for dayOfWeek: Int) -> (shortLabel: String, slug: String, isoDate: String) {
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

    internal static func normalizeTimeString(_ raw: String) -> String {
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

    internal static func minutesFromTime(_ time: String) -> Int? {
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

    internal static func minutesFromTimeRangeStart(_ timeRange: String) -> Int {
        let start = timeRange.components(separatedBy: "–").first ?? timeRange
        return minutesFromTime(normalizeTimeString(start)) ?? 9999
    }

    internal static func timeRangesOverlap(startA: String, endA: String, startB: String, endB: String) -> Bool {
        guard let aStart = minutesFromTime(startA),
              let aEnd = minutesFromTime(endA),
              let bStart = minutesFromTime(startB),
              let bEnd = minutesFromTime(endB) else {
            return startA == startB
        }
        return aStart < bEnd && bStart < aEnd
    }

    internal static func buildShortLocation(building: String, room: String) -> String {
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

    internal static func isEadText(_ text: String) -> Bool {
        let lower = text.lowercased()
        return lower.contains("ead") || lower.contains("distância") || lower.contains("distancia") || lower.contains("remoto")
    }

}
