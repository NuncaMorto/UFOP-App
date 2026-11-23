import Foundation

// MARK: - UFOP 54-Course Curriculum Catalog (ufop_matrizes_2026_2.json)

public struct UfopCurriculumSubject: Codable, Identifiable, Equatable, Sendable {
    public let code: String
    public let name: String
    public let departmentId: String
    public let departmentCode: String
    public let period: Int
    public let type: String
    public let chs: Int
    public let che: Int
    public let cha: Int
    public let weeklyTheory: Int
    public let weeklyPractice: Int
    public let prerequisites: [String]

    public var id: String { "\(period)-\(code)" }

    public init(
        code: String,
        name: String,
        departmentId: String,
        departmentCode: String,
        period: Int,
        type: String = "OBR",
        chs: Int,
        che: Int = 0,
        cha: Int = 0,
        weeklyTheory: Int = 4,
        weeklyPractice: Int = 0,
        prerequisites: [String] = []
    ) {
        self.code = code
        self.name = name
        self.departmentId = departmentId
        self.departmentCode = departmentCode
        self.period = period
        self.type = type
        self.chs = chs
        self.che = che
        self.cha = cha > 0 ? cha : (chs * 60 / 50)
        self.weeklyTheory = weeklyTheory
        self.weeklyPractice = weeklyPractice
        self.prerequisites = prerequisites
    }
}

public struct UfopCourseCurriculum: Codable, Identifiable, Equatable, Sendable {
    public let code: String
    public let name: String
    public let degree: String
    public let campus: String
    public let curriculumVersion: String
    public let chMandatory: Int
    public let chOptional: Int
    public let chElective: Int
    public let chExtension: Int
    public let chActivities: Int
    public let chTotal: Int
    public let status: String
    public let subjects: [UfopCurriculumSubject]
    public let effectiveFromYear: Int
    public let effectiveFromSemester: Int
    public let effectiveUntilYear: Int?
    public let effectiveUntilSemester: Int?

    public var id: String { code }

    enum CodingKeys: String, CodingKey {
        case code
        case name
        case degree
        case campus
        case curriculumVersion
        case chMandatory
        case chOptional
        case chElective
        case chExtension
        case chActivities
        case chTotal
        case status
        case subjects
        case effectiveFromYear
        case effectiveFromSemester
        case effectiveUntilYear
        case effectiveUntilSemester
    }

    public init(
        code: String,
        name: String,
        degree: String,
        campus: String,
        curriculumVersion: String,
        chMandatory: Int,
        chOptional: Int,
        chElective: Int,
        chExtension: Int,
        chActivities: Int,
        chTotal: Int,
        status: String,
        subjects: [UfopCurriculumSubject],
        effectiveFromYear: Int = 2026,
        effectiveFromSemester: Int = 1,
        effectiveUntilYear: Int? = nil,
        effectiveUntilSemester: Int? = nil
    ) {
        self.code = code
        self.name = name
        self.degree = degree
        self.campus = campus
        self.curriculumVersion = curriculumVersion
        self.chMandatory = chMandatory
        self.chOptional = chOptional
        self.chElective = chElective
        self.chExtension = chExtension
        self.chActivities = chActivities
        self.chTotal = chTotal
        self.status = status
        self.subjects = subjects
        self.effectiveFromYear = effectiveFromYear
        self.effectiveFromSemester = effectiveFromSemester
        self.effectiveUntilYear = effectiveUntilYear
        self.effectiveUntilSemester = effectiveUntilSemester
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.code = try container.decode(String.self, forKey: .code)
        self.name = try container.decode(String.self, forKey: .name)
        self.degree = try container.decode(String.self, forKey: .degree)
        self.campus = try container.decode(String.self, forKey: .campus)
        self.curriculumVersion = try container.decode(String.self, forKey: .curriculumVersion)
        self.chMandatory = try container.decode(Int.self, forKey: .chMandatory)
        self.chOptional = try container.decode(Int.self, forKey: .chOptional)
        self.chElective = try container.decode(Int.self, forKey: .chElective)
        self.chExtension = try container.decode(Int.self, forKey: .chExtension)
        self.chActivities = try container.decode(Int.self, forKey: .chActivities)
        self.chTotal = try container.decode(Int.self, forKey: .chTotal)
        self.status = try container.decode(String.self, forKey: .status)
        self.subjects = try container.decode([UfopCurriculumSubject].self, forKey: .subjects)
        self.effectiveFromYear = try container.decodeIfPresent(Int.self, forKey: .effectiveFromYear) ?? 2026
        self.effectiveFromSemester = try container.decodeIfPresent(Int.self, forKey: .effectiveFromSemester) ?? 1
        self.effectiveUntilYear = try container.decodeIfPresent(Int.self, forKey: .effectiveUntilYear)
        self.effectiveUntilSemester = try container.decodeIfPresent(Int.self, forKey: .effectiveUntilSemester)
    }

    public func containsEntryTerm(year: Int, semester: Int) -> Bool {
        let afterStart = (year > effectiveFromYear) || (year == effectiveFromYear && semester >= effectiveFromSemester)
        guard afterStart else { return false }
        if let untilYear = effectiveUntilYear, let untilSem = effectiveUntilSemester {
            return (year < untilYear) || (year == untilYear && semester <= untilSem)
        }
        return true
    }

    public func isEntryBeforeEffectiveStart(year: Int, semester: Int) -> Bool {
        (year < effectiveFromYear) || (year == effectiveFromYear && semester < effectiveFromSemester)
    }
}

private struct UfopCurriculumRoot: Codable {
    let courses: [UfopCourseCurriculum]
}

public struct CurriculumCompatibilityStatus: Equatable, Sendable {
    public let isLegacyMatrixMismatch: Bool
    public let studentEntryLabel: String?
    public let matrixVersionLabel: String
    public let unmatchedTranscriptCodesCount: Int
}

public enum UfopCurriculumCatalog {
    public static let referenceVersionLabel = "2026/2"

    private static func mapDepartmentCode(forSubjectCode code: String) -> String {
        let prefix = String(code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased().prefix(3))
        switch prefix {
        case "BCC": return "DECOM"
        case "ARQ": return "DEARQ"
        case "CSO": return "DECSO"
        case "MTM": return "DEMAT"
        case "QUI": return "DEQUI"
        case "EST": return "DEEST"
        case "FIS": return "DEFIS"
        case "CAT": return "DECAT"
        case "MEC": return "DEMEC"
        case "CIV": return "DECIV"
        case "MET": return "DEMET"
        case "MIN": return "DEMIN"
        case "DIR": return "DEDIR"
        case "PRO": return "DEPRO"
        case "AMB": return "DEAMB"
        default: return "DECAT"
        }
    }

    private static func makeAut2024Subject(
        code: String,
        name: String,
        chs: Int,
        period: Int,
        prerequisites: [String] = []
    ) -> UfopCurriculumSubject {
        let deptCode = mapDepartmentCode(forSubjectCode: code)
        return UfopCurriculumSubject(
            code: code,
            name: name,
            departmentId: deptCode.lowercased(),
            departmentCode: deptCode,
            period: period,
            type: "OBR",
            chs: chs,
            che: 0,
            cha: chs * 60 / 50,
            weeklyTheory: max(1, chs / 15),
            weeklyPractice: 0,
            prerequisites: prerequisites
        )
    }

    public static let aut2024_1Curriculum: UfopCourseCurriculum = {
        let mandatorySubjects: [UfopCurriculumSubject] = [
            // Período 1
            makeAut2024Subject(code: "BCC701", name: "Programação de Computadores I", chs: 60, period: 1),
            makeAut2024Subject(code: "CSO117", name: "Metodologia de Pesquisa", chs: 30, period: 1),
            makeAut2024Subject(code: "MTM122", name: "Cálculo Diferencial e Integral I", chs: 90, period: 1),
            makeAut2024Subject(code: "MTM730", name: "Geometria Analítica e Álgebra Linear", chs: 60, period: 1),
            makeAut2024Subject(code: "QUI701", name: "Química Fundamental", chs: 60, period: 1),

            // Período 2
            makeAut2024Subject(code: "ARQ205", name: "Representação Gráfica", chs: 60, period: 2),
            makeAut2024Subject(code: "BCC702", name: "Programação de Computadores II", chs: 60, period: 2, prerequisites: ["BCC701"]),
            makeAut2024Subject(code: "EST202", name: "Estatística e Probabilidade", chs: 60, period: 2),
            makeAut2024Subject(code: "FIS106", name: "Fundamentos de Mecânica", chs: 60, period: 2, prerequisites: ["MTM122", "MTM730"]),
            makeAut2024Subject(code: "MTM123", name: "Cálculo Diferencial e Integral II", chs: 60, period: 2, prerequisites: ["MTM122"]),

            // Período 3
            makeAut2024Subject(code: "BCC760", name: "Cálculo Numérico", chs: 60, period: 3),
            makeAut2024Subject(code: "FIS107", name: "Fundamentos de Termodinâmica", chs: 30, period: 3, prerequisites: ["FIS106", "MTM123"]),
            makeAut2024Subject(code: "FIS108", name: "Fundamentos de Fluidos, Oscilações e Ondas", chs: 30, period: 3, prerequisites: ["FIS106", "MTM123"]),
            makeAut2024Subject(code: "FIS109", name: "Fundamentos de Eletromagnetismo", chs: 60, period: 3, prerequisites: ["FIS106", "MTM123"]),
            makeAut2024Subject(code: "MTM124", name: "Cálculo Diferencial e Integral III", chs: 60, period: 3, prerequisites: ["MTM123"]),
            makeAut2024Subject(code: "MTM125", name: "Introdução às Equações Diferenciais Ordinárias", chs: 60, period: 3, prerequisites: ["MTM123"]),

            // Período 4
            makeAut2024Subject(code: "BCC720", name: "Sistemas de Computação para Controle e Automação", chs: 60, period: 4, prerequisites: ["BCC702"]),
            makeAut2024Subject(code: "CAT176", name: "Análise de Circuitos Elétricos", chs: 60, period: 4),
            makeAut2024Subject(code: "FIS105", name: "Fundamentos de Física Experimental", chs: 30, period: 4, prerequisites: ["FIS109", "MTM124"]),
            makeAut2024Subject(code: "FIS110", name: "Fundamentos de Óptica e Quântica", chs: 30, period: 4, prerequisites: ["FIS109", "MTM124"]),
            makeAut2024Subject(code: "MEC124", name: "Fenômenos de Transporte", chs: 60, period: 4, prerequisites: ["FIS107", "FIS108"]),
            makeAut2024Subject(code: "MTM146", name: "Matemática Aplicada à Eng. de Controle e Automação", chs: 60, period: 4, prerequisites: ["MTM124", "MTM125", "MTM730"]),

            // Período 5
            makeAut2024Subject(code: "BCC721", name: "Circuitos Digitais", chs: 60, period: 5, prerequisites: ["BCC720"]),
            makeAut2024Subject(code: "CAT141", name: "Teoria de Controle I", chs: 60, period: 5),
            makeAut2024Subject(code: "CAT164", name: "Eletrotécnica para Controle e Automação", chs: 60, period: 5),
            makeAut2024Subject(code: "CAT165", name: "Circuitos e Dispositivos Eletrônicos", chs: 60, period: 5),
            makeAut2024Subject(code: "CIV107", name: "Resistência dos Materiais e Estruturas", chs: 60, period: 5),

            // Período 6
            makeAut2024Subject(code: "BCC342", name: "Introdução à Otimização", chs: 60, period: 6, prerequisites: ["BCC702", "BCC760"]),
            makeAut2024Subject(code: "CAT163", name: "Instrumentação", chs: 60, period: 6),
            makeAut2024Subject(code: "CAT169", name: "Acionamentos Elétricos", chs: 60, period: 6),
            makeAut2024Subject(code: "CAT183", name: "Teoria de Controle II", chs: 60, period: 6),
            makeAut2024Subject(code: "MET702", name: "Engenharia nos Processos de Metalurgia", chs: 60, period: 6),

            // Período 7
            makeAut2024Subject(code: "BCC425", name: "Sistemas Embutidos", chs: 60, period: 7, prerequisites: ["BCC721"]),
            makeAut2024Subject(code: "BCC740", name: "Inteligência Artificial", chs: 60, period: 7, prerequisites: ["BCC702"]),
            makeAut2024Subject(code: "CAT166", name: "Teoria de Controle III", chs: 60, period: 7),
            makeAut2024Subject(code: "CAT175", name: "Informática Industrial", chs: 60, period: 7),
            makeAut2024Subject(code: "MIN107", name: "Engenharia nos Processos de Mineração", chs: 60, period: 7),

            // Período 8
            makeAut2024Subject(code: "BCC722", name: "Programação de Sistemas em Tempo Real", chs: 60, period: 8, prerequisites: ["BCC425"]),
            makeAut2024Subject(code: "CAT167", name: "Sistemas Integrados de Manufatura", chs: 60, period: 8),
            makeAut2024Subject(code: "CAT181", name: "Elementos de Robótica", chs: 60, period: 8),
            makeAut2024Subject(code: "CAT490", name: "Trabalho Final de Curso I", chs: 30, period: 8),
            makeAut2024Subject(code: "DIR250", name: "Introdução ao Direito e Legislação", chs: 30, period: 8),
            makeAut2024Subject(code: "MEC142", name: "Acionamentos Fluidomecânicos", chs: 60, period: 8),

            // Período 9
            makeAut2024Subject(code: "CAT168", name: "Interfaceamento de Sistemas", chs: 60, period: 9),
            makeAut2024Subject(code: "CAT491", name: "Trabalho Final de Curso II", chs: 30, period: 9, prerequisites: ["CAT490"]),
            makeAut2024Subject(code: "PRO243", name: "Organização e Administração I", chs: 30, period: 9),

            // Período 10
            makeAut2024Subject(code: "AMB111", name: "Engenharia Ambiental Básica M", chs: 30, period: 10),
            makeAut2024Subject(code: "PRO215", name: "Planejamento e Controle da Produção I", chs: 60, period: 10),
            makeAut2024Subject(code: "PRO224", name: "Economia da Engenharia", chs: 60, period: 10)
        ]

        return UfopCourseCurriculum(
            code: "AUT",
            name: "Engenharia de Controle e Automação",
            degree: "Bacharelado",
            campus: "Ouro Preto",
            curriculumVersion: "2024/1 (Currículo 2)",
            chMandatory: 2670,
            chOptional: 0,
            chElective: 300,
            chExtension: 0,
            chActivities: 640,
            chTotal: 3610,
            status: "Ativo",
            subjects: mandatorySubjects,
            effectiveFromYear: 2024,
            effectiveFromSemester: 1,
            effectiveUntilYear: 2025,
            effectiveUntilSemester: 2
        )
    }()

    public static let historicalCourseVersions: [String: [UfopCourseCurriculum]] = [
        "AUT": [aut2024_1Curriculum]
    ]

    public static let allCourses: [UfopCourseCurriculum] = {
        guard let url = Bundle.main.url(forResource: "ufop_matrizes_2026_2", withExtension: "json", subdirectory: "Data")
            ?? Bundle.main.url(forResource: "ufop_matrizes_2026_2", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode(UfopCurriculumRoot.self, from: data) else {
            return []
        }
        return decoded.courses
    }()

    public static func courseVersions(for courseCode: String) -> [UfopCourseCurriculum] {
        let upper = courseCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        var versions: [UfopCourseCurriculum] = []
        if let base = allCourses.first(where: { $0.code.uppercased() == upper }) {
            versions.append(base)
        }
        if let historical = historicalCourseVersions[upper] {
            for item in historical where !versions.contains(where: { $0.curriculumVersion == item.curriculumVersion }) {
                versions.append(item)
            }
        }
        return versions.sorted { lhs, rhs in
            if lhs.effectiveFromYear != rhs.effectiveFromYear {
                return lhs.effectiveFromYear > rhs.effectiveFromYear
            }
            return lhs.effectiveFromSemester > rhs.effectiveFromSemester
        }
    }

    public static func courseVersions(for course: UfopCourseCurriculum) -> [UfopCourseCurriculum] {
        let byCode = courseVersions(for: course.code)
        return byCode.isEmpty ? [course] : byCode
    }

    public static func resolveCourseForStudent(
        baseCourse: UfopCourseCurriculum,
        registrationNumber: String,
        earliestTranscriptSemester: String?
    ) -> UfopCourseCurriculum {
        let versions = courseVersions(for: baseCourse)
        guard versions.count > 1 else { return baseCourse }

        let regEntry = parseEnrollmentYearSemester(registrationNumber)
        let txEntry = earliestTranscriptSemester.flatMap { parseSemesterTerm($0) }

        let effectiveEntry: (year: Int, semester: Int)? = {
            switch (regEntry, txEntry) {
            case let (.some(a), .some(b)):
                return (a.year, a.semester) <= (b.year, b.semester) ? a : b
            case let (.some(a), .none):
                return a
            case let (.none, .some(b)):
                return b
            case (.none, .none):
                return nil
            }
        }()

        guard let entry = effectiveEntry else { return baseCourse }

        if let exactMatch = versions.first(where: { $0.containsEntryTerm(year: entry.year, semester: entry.semester) }) {
            return exactMatch
        }

        if let oldest = versions.min(by: { ($0.effectiveFromYear, $0.effectiveFromSemester) < ($1.effectiveFromYear, $1.effectiveFromSemester) }),
           oldest.isEntryBeforeEffectiveStart(year: entry.year, semester: entry.semester) {
            return oldest
        }

        return baseCourse
    }

    public static func findCourse(matching query: String) -> UfopCourseCurriculum? {
        let normQuery = normalizeToken(query)
        guard !normQuery.isEmpty else { return allCourses.first }

        if let exactCode = allCourses.first(where: { normalizeToken($0.code) == normQuery }) {
            return exactCode
        }
        if let exactName = allCourses.first(where: { normalizeToken($0.name) == normQuery }) {
            return exactName
        }

        let tokens = normQuery.split(separator: " ").map(String.init).filter { $0.count >= 3 }
        var best: UfopCourseCurriculum?
        var bestScore = 0
        for course in allCourses {
            let normName = normalizeToken(course.name)
            let score = tokens.filter { normName.contains($0) }.count
            if score > bestScore {
                bestScore = score
                best = course
            }
        }
        return bestScore > 0 ? best : allCourses.first
    }

    public static func parseEnrollmentYearSemester(_ registrationNumber: String) -> (year: Int, semester: Int)? {
        let trimmed = registrationNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        let patterns = [
            #"(?:^|\D)(\d{2}|\d{4})\s*[./-]\s*([12])\s*[./-]\s*\d{2,6}(?:\D|$)"#,
            #"^\s*((?:19|20)\d{2})([12])\d{4}\s*$"#,
            #"^\s*(\d{2})([12])\d{4}\s*$"#
        ]

        for pattern in patterns {
            guard let regex = try? NSRegularExpression(pattern: pattern),
                  let match = regex.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)),
                  let r1 = Range(match.range(at: 1), in: trimmed),
                  let r2 = Range(match.range(at: 2), in: trimmed),
                  let sem = Int(trimmed[r2]), (sem == 1 || sem == 2),
                  let year = normalizeYear(String(trimmed[r1])) else {
                continue
            }
            return (year, sem)
        }
        return nil
    }

    public static func checkCompatibility(
        registrationNumber: String,
        earliestTranscriptSemester: String?,
        transcriptCourseCodes: Set<String>,
        course: UfopCourseCurriculum
    ) -> CurriculumCompatibilityStatus {
        let regEntry = parseEnrollmentYearSemester(registrationNumber)
        let txEntry = earliestTranscriptSemester.flatMap { parseSemesterTerm($0) }

        let effectiveEntry: (year: Int, semester: Int)? = {
            switch (regEntry, txEntry) {
            case let (.some(a), .some(b)):
                return (a.year, a.semester) <= (b.year, b.semester) ? a : b
            case let (.some(a), .none):
                return a
            case let (.none, .some(b)):
                return b
            case (.none, .none):
                return nil
            }
        }()

        let entryLabel = effectiveEntry.map { "\($0.year)/\($0.semester)" }
        let isLegacyByEntry = effectiveEntry.map {
            course.isEntryBeforeEffectiveStart(year: $0.year, semester: $0.semester)
        } ?? false

        let normTxCodes = Set(transcriptCourseCodes.map { $0.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() }.filter { !$0.isEmpty })
        let matrixCodes = Set(course.subjects.map { $0.code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() }.filter { !$0.isEmpty })

        let unmatched = (matrixCodes.isEmpty || normTxCodes.isEmpty) ? 0 : normTxCodes.filter { !matrixCodes.contains($0) }.count
        let ratio = normTxCodes.isEmpty ? 0.0 : Double(unmatched) / Double(normTxCodes.count)
        let divergence = !matrixCodes.isEmpty && ((unmatched >= 3 && ratio >= 0.50) || (unmatched >= 5 && ratio >= 0.35))
        let resolvedVersionLabel = course.curriculumVersion.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? referenceVersionLabel
            : course.curriculumVersion

        return CurriculumCompatibilityStatus(
            isLegacyMatrixMismatch: isLegacyByEntry || divergence,
            studentEntryLabel: entryLabel,
            matrixVersionLabel: resolvedVersionLabel,
            unmatchedTranscriptCodesCount: unmatched
        )
    }

    private static func normalizeYear(_ raw: String) -> Int? {
        guard let parsed = Int(raw) else { return nil }
        let full = raw.count == 4 ? parsed : (parsed >= 69 ? 1900 + parsed : 2000 + parsed)
        return (1969...2099).contains(full) ? full : nil
    }

    private static func parseSemesterTerm(_ raw: String) -> (year: Int, semester: Int)? {
        let pattern = #"(?:^|\D)(\d{4}|\d{2})\s*[./-]\s*([12])(?:\D|$)"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: raw, range: NSRange(raw.startIndex..., in: raw)),
              let r1 = Range(match.range(at: 1), in: raw),
              let r2 = Range(match.range(at: 2), in: raw),
              let year = normalizeYear(String(raw[r1])),
              let sem = Int(raw[r2]), (sem == 1 || sem == 2) else {
            return nil
        }
        return (year, sem)
    }

    private static func normalizeToken(_ raw: String) -> String {
        raw.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "pt_BR"))
            .replacingOccurrences(of: #"[^a-z0-9]+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

// MARK: - UFOP 2026.2 Official Room Allocations (ufop_room_allocations_2026_2.json)

public struct UfopRoomAllocationEntry: Codable, Equatable, Sendable {
    public let c: String
    public let t: [String]
    public let d: Int
    public let s: String
    public let e: String
    public let b: String
    public let r: String
    public let k: String

    public var courseCode: String { c }
    public var turmas: [String] { t }
    public var dayOfWeek: Int { d }
    public var startTime: String { s }
    public var endTime: String { e }
    public var building: String { b }
    public var room: String { r }
    public var kind: String { k }
}

private struct UfopRoomAllocationRoot: Codable {
    let semester: String
    let buildingsCovered: [String]
    let allocations: [UfopRoomAllocationEntry]
}

public final class UfopRoomAllocationIndex: Sendable {
    public let semester: String
    public let buildingsCovered: Set<String>
    public let entries: [UfopRoomAllocationEntry]
    private let byCourseDayStart: [String: [UfopRoomAllocationEntry]]

    public init(semester: String = "2026.2", buildingsCovered: Set<String> = [], entries: [UfopRoomAllocationEntry] = []) {
        self.semester = semester
        self.buildingsCovered = buildingsCovered
        self.entries = entries
        var grouped: [String: [UfopRoomAllocationEntry]] = [:]
        for entry in entries {
            let key = Self.buildKey(courseCode: entry.courseCode, dayOfWeek: entry.dayOfWeek, startTime: entry.startTime)
            grouped[key, default: []].append(entry)
        }
        self.byCourseDayStart = grouped
    }

    public func lookup(
        courseCode: String,
        turma: String?,
        dayOfWeek: Int,
        startTime: String,
        kind: String? = nil
    ) -> UfopRoomAllocationEntry? {
        let key = Self.buildKey(courseCode: courseCode, dayOfWeek: dayOfWeek, startTime: startTime)
        guard let candidates = byCourseDayStart[key], !candidates.isEmpty else {
            return nil
        }

        let normTurma = Self.normalizeTurma(turma)
        let normKind = Self.normalizeKind(kind)

        // 1. Turma + Tipo (T/P)
        if !normTurma.isEmpty && !normKind.isEmpty {
            if let hit = candidates.first(where: { entry in
                entry.turmas.contains(where: { Self.normalizeTurma($0) == normTurma }) &&
                    Self.normalizeKind(entry.kind) == normKind
            }) {
                return hit
            }
        }

        // 2. Turma
        if !normTurma.isEmpty {
            if let hit = candidates.first(where: { entry in
                entry.turmas.contains(where: { Self.normalizeTurma($0) == normTurma })
            }) {
                return hit
            }
        }

        // 3. Sem turma específica
        let untyped = candidates.filter { $0.turmas.isEmpty }
        if !untyped.isEmpty {
            if !normKind.isEmpty,
               let kindHit = untyped.first(where: { Self.normalizeKind($0.kind) == normKind }) {
                return kindHit
            }
            return untyped.first
        }

        // 4. Turma vazia e todas as candidatas apontam para a mesma sala/prédio
        if normTurma.isEmpty {
            if !normKind.isEmpty {
                let kindFiltered = candidates.filter { Self.normalizeKind($0.kind) == normKind }
                if !kindFiltered.isEmpty && Set(kindFiltered.map { "\($0.building)|\($0.room)" }).count == 1 {
                    return kindFiltered.first
                }
            }
            if Set(candidates.map { "\($0.building)|\($0.room)" }).count == 1 {
                return candidates.first
            }
        }

        return nil
    }

    private static func buildKey(courseCode: String, dayOfWeek: Int, startTime: String) -> String {
        "\(courseCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased())|\(dayOfWeek)|\(startTime.trimmingCharacters(in: .whitespacesAndNewlines))"
    }

    private static func normalizeTurma(_ raw: String?) -> String {
        let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if trimmed.isEmpty { return "" }
        let stripped = String(trimmed.drop(while: { $0 == "0" }))
        return (stripped.isEmpty ? "0" : stripped).uppercased()
    }

    private static func normalizeKind(_ raw: String?) -> String {
        let upper = raw?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() ?? ""
        return (upper == "T" || upper == "P") ? upper : ""
    }
}

public enum UfopRoomAllocationCatalog {
    public static let shared: UfopRoomAllocationIndex = {
        guard let url = Bundle.main.url(forResource: "ufop_room_allocations_2026_2", withExtension: "json", subdirectory: "Data")
            ?? Bundle.main.url(forResource: "ufop_room_allocations_2026_2", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let root = try? JSONDecoder().decode(UfopRoomAllocationRoot.self, from: data) else {
            return UfopRoomAllocationIndex()
        }
        return UfopRoomAllocationIndex(
            semester: root.semester.isEmpty ? "2026.2" : root.semester,
            buildingsCovered: Set(root.buildingsCovered),
            entries: root.allocations
        )
    }()
}
