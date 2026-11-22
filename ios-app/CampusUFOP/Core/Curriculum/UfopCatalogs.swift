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

    public var id: String { code }
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
    private static let cutoffYear = 2026
    private static let cutoffSemester = 1

    public static let allCourses: [UfopCourseCurriculum] = {
        guard let url = Bundle.main.url(forResource: "ufop_matrizes_2026_2", withExtension: "json", subdirectory: "Data")
            ?? Bundle.main.url(forResource: "ufop_matrizes_2026_2", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode(UfopCurriculumRoot.self, from: data) else {
            return []
        }
        return decoded.courses
    }()

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
            $0.year < cutoffYear || ($0.year == cutoffYear && $0.semester < cutoffSemester)
        } ?? false

        let normTxCodes = Set(transcriptCourseCodes.map { $0.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() }.filter { !$0.isEmpty })
        let matrixCodes = Set(course.subjects.map { $0.code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() }.filter { !$0.isEmpty })

        let unmatched = (matrixCodes.isEmpty || normTxCodes.isEmpty) ? 0 : normTxCodes.filter { !matrixCodes.contains($0) }.count
        let ratio = normTxCodes.isEmpty ? 0.0 : Double(unmatched) / Double(normTxCodes.count)
        let divergence = !matrixCodes.isEmpty && ((unmatched >= 3 && ratio >= 0.50) || (unmatched >= 5 && ratio >= 0.35))

        return CurriculumCompatibilityStatus(
            isLegacyMatrixMismatch: isLegacyByEntry || divergence,
            studentEntryLabel: entryLabel,
            matrixVersionLabel: referenceVersionLabel,
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
