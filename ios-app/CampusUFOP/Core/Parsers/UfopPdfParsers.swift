import Foundation

// MARK: - Parsed Certificate Models

public struct ParsedCertificateSlot: Equatable, Sendable {
    public let courseCode: String
    public let turma: String
    public let kind: String
    public let dayOfWeek: Int
    public let startTime: String
    public let endTime: String
    public let building: String
    public let room: String
    public let isEad: Bool
}

public struct ParsedCertificateCourse: Equatable, Sendable {
    public let code: String
    public let turma: String
    public let name: String
    public let building: String
    public let isEad: Bool
    public let departmentId: String
    public let workloadHours: Int
    public let professor: String
    public let slots: [ParsedCertificateSlot]
}

public struct ParsedCertificateResult: Equatable, Sendable {
    public let studentName: String
    public let registrationNumber: String
    public let courseName: String
    public let campusId: String
    public let campusName: String
    public let semester: String
    public let semesterStartDate: String
    public let semesterEndDate: String
    public let courses: [ParsedCertificateCourse]
}

// MARK: - Syllabus Import Report

public struct ParsedSyllabusAssessment: Equatable, Sendable {
    public let title: String
    public let kind: String
    public let dueDate: String
    public let dueTime: String
}

public struct SyllabusImportReport: Equatable, Sendable {
    public let matchedCourse: Bool
    public let matchedCourseCode: String?
    public let matchedCourseTitle: String?
    public let extractedProfessor: String
    public let hasGradeDistribution: Bool
    public let hasAssessmentDates: Bool
    public let parsedAssessments: [ParsedSyllabusAssessment]
    public let warningMessage: String?
    public let errorMessage: String?
}

// MARK: - Transcript / Histórico Escolar Models

public struct TranscriptCourseEntry: Identifiable, Equatable, Codable, Sendable {
    public let id: String
    public let semester: String
    public let code: String
    public let name: String
    public let workloadHours: Int
    public var grade: Double?
    public var absences: Int
    public let totalClasses: Int
    public let status: String
    public let professor: String

    public init(
        id: String,
        semester: String,
        code: String,
        name: String,
        workloadHours: Int,
        grade: Double?,
        absences: Int,
        totalClasses: Int,
        status: String,
        professor: String = ""
    ) {
        self.id = id
        self.semester = semester
        self.code = code
        self.name = name
        self.workloadHours = workloadHours
        self.grade = grade
        self.absences = absences
        self.totalClasses = totalClasses
        self.status = status
        self.professor = professor
    }
}

public struct SemesterCreSummary: Identifiable, Equatable, Sendable {
    public var id: String { semester }
    public let semester: String
    public let cre: Double
    public let totalWorkloadHours: Int
    public let approvedCount: Int
    public let failedCount: Int
    public let inProgressCount: Int
    public let courses: [TranscriptCourseEntry]
}

public struct AcademicCreReport: Equatable, Sendable {
    public let overallCre: Double
    public let totalRequiredHours: Int
    public let completedHours: Int
    public let totalApproved: Int
    public let totalFailedByGrade: Int
    public let totalFailedByAbsence: Int
    public let semesters: [SemesterCreSummary]
}

public struct ParsedTranscriptResult: Equatable, Sendable {
    public let fullName: String?
    public let registrationNumber: String?
    public let courseName: String?
    public let totalRequiredHours: Int?
    public let completedHours: Int?
    public let entries: [TranscriptCourseEntry]
}

// MARK: - Parsers (Base & Helpers Compartilhados)

public enum UfopPdfParserError: LocalizedError {
    case certificateNoCourses
    case transcriptNoCourses

    public var errorDescription: String? {
        switch self {
        case .certificateNoCourses:
            return "Não foi possível identificar disciplinas neste Atestado de Matrícula da UFOP."
        case .transcriptNoCourses:
            return "Não foi possível identificar disciplinas neste Histórico Escolar da UFOP."
        }
    }
}

public enum UfopPdfParsers {

    internal static let nonCoursePrefixes: Set<String> = [
        "CEP", "CPF", "CNPJ", "UFOP", "ICEB", "ICEA", "ICHS", "ICSA",
        "MEC", "SISU", "ENEM", "SALA", "BLOC", "BLOCO", "LAB"
    ]

    public static func isEadIndicator(_ raw: String) -> Bool {
        let norm = normalizeSearch(raw)
        if norm.isEmpty { return false }
        return norm.contains("a distancia")
            || norm.contains("ensino a distancia")
            || norm.contains("cead")
            || norm.contains("educacao a distancia")
            || firstMatch(#"\be\.?\s*a\.?\s*d\.?\b"#, in: norm) != nil
            || norm.contains("remoto")
            || norm.contains("remota")
            || norm.contains("moodle")
    }

    internal static func departmentIdFromCode(_ code: String) -> String {
        let prefix = String(code.uppercased().prefix(while: \.isLetter))
        if ["BCC", "CSI"].contains(prefix) { return "comp" }
        if ["FIS", "CEA"].contains(prefix) { return "phys" }
        return "math"
    }

    internal static func resolveCampus(from raw: String) -> (id: String, city: String) {
        let norm = normalizeSearch(raw)
        if norm.contains("mariana") || norm.contains("ichs") || norm.contains("icsa") {
            return ("mar_ichs", "Mariana")
        }
        if norm.contains("monlevade") || norm.contains("icea") {
            return ("jm_icea", "João Monlevade")
        }
        return ("op_cruzeiro", "Ouro Preto")
    }

    internal static func normalizeBuildingName(_ raw: String) -> String {
        let upper = raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if upper.contains("ICEB") { return "ICEB" }
        if upper.contains("BLOCO DE SALAS") { return "Bloco de Salas" }
        if upper.contains("ESCOLA DE MINAS") || upper == "EM" { return "Escola de Minas" }
        return raw.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    internal static func parseLongDate(after prefixPattern: String, in text: String) -> String? {
        guard let caps = extractRegexGroups(prefixPattern + #"(\d{1,2})\s+de\s+([A-Za-zÀ-ú]+)\s+de\s+(\d{4})"#, in: text),
              caps.count >= 4,
              let day = Int(caps[1]), let year = Int(caps[3]) else {
            return nil
        }
        let m = normalizeSearch(caps[2])
        let months: [String: Int] = [
            "janeiro": 1, "fevereiro": 2, "marco": 3, "abril": 4, "maio": 5, "junho": 6,
            "julho": 7, "agosto": 8, "setembro": 9, "outubro": 10, "novembro": 11, "dezembro": 12
        ]
        guard let month = months[m] else { return nil }
        return String(format: "%04d-%02d-%02d", year, month, day)
    }

    internal static func normalizeSearch(_ text: String) -> String {
        text.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "pt_BR"))
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    internal static func ufopTitleCase(_ raw: String) -> String {
        let lowerWords: Set<String> = ["de", "da", "do", "das", "dos", "e", "em", "na", "no", "para", "por", "com", "a", "o"]
        let romans: Set<String> = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"]
        let words = raw.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: " ")
        return words.enumerated().map { idx, token in
            let s = String(token)
            let upper = s.uppercased()
            if romans.contains(upper) || ["UFOP", "ICEB", "ICEA", "EAD"].contains(upper) { return upper }
            let lower = s.lowercased()
            if idx > 0 && lowerWords.contains(lower) { return lower }
            return lower.prefix(1).uppercased() + lower.dropFirst()
        }.joined(separator: " ")
    }

    internal static func firstMatch(_ pattern: String, in text: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let range = Range(match.range, in: text) else {
            return nil
        }
        return String(text[range])
    }

    internal static func firstCapture(_ pattern: String, in text: String) -> String? {
        extractRegexGroups(pattern, in: text)?.dropFirst().first
    }

    internal static func extractCapture(_ pattern: String, in text: String, groups: [Int]) -> [String]? {
        guard let all = extractRegexGroups(pattern, in: text) else { return nil }
        var out: [String] = []
        for g in groups where g < all.count { out.append(all[g]) }
        return out.count == groups.count ? out : nil
    }

    internal static func extractRegexGroups(_ pattern: String, in text: String) -> [String]? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) else {
            return nil
        }
        var results: [String] = []
        for idx in 0..<match.numberOfRanges {
            let nsRange = match.range(at: idx)
            if nsRange.location != NSNotFound, let swiftRange = Range(nsRange, in: text) {
                results.append(String(text[swiftRange]))
            } else {
                results.append("")
            }
        }
        return results
    }
}

private extension UfopPdfParsers {
    struct RawCertificateRowPlaceholder {}
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
