import Foundation

// MARK: - Dynamic Curriculum Matrix Resolution & Engine

private enum DynamicGridEngineRegex {
    static let semesterTerm: NSRegularExpression? = try? NSRegularExpression(
        pattern: #"(?:^|\D)(\d{4}|\d{2})\s*[./-]\s*([12])(?:\D|$)"#
    )
}

private enum DynamicGridSingleMatrixCache {
    private static let lock = NSLock()
    private static var cache: [String: DynamicCurriculumMatrix] = [:]

    static func matrix(for course: UfopCourseCurriculum) -> DynamicCurriculumMatrix? {
        let untilKey = course.effectiveUntilYear.map(String.init) ?? "open"
        let key = "\(course.code.uppercased())|\(course.curriculumVersion)|\(course.effectiveFromYear)/\(course.effectiveFromSemester)|\(untilKey)|\(course.subjects.count)|\(course.chTotal)"
        lock.lock()
        if let hit = cache[key] {
            lock.unlock()
            return hit
        }
        lock.unlock()

        guard let built = DynamicCurriculumCatalog.fromCourseCurriculum(course) else {
            return nil
        }

        lock.lock()
        if cache.count > 256 {
            cache.removeAll(keepingCapacity: true)
        }
        cache[key] = built
        lock.unlock()
        return built
    }
}

public extension DynamicCurriculumCatalog {

    static func find(
        byId id: String?,
        in matrices: [DynamicCurriculumMatrix] = allMatrices
    ) -> DynamicCurriculumMatrix? {
        guard let clean = id?.trimmingCharacters(in: .whitespacesAndNewlines), !clean.isEmpty else {
            return nil
        }
        return matrices.first { matrix in
            matrix.id.caseInsensitiveCompare(clean) == .orderedSame ||
                matrix.legacyIds.contains { $0.caseInsensitiveCompare(clean) == .orderedSame }
        }
    }

    static func searchMatrices(
        _ matrices: [DynamicCurriculumMatrix],
        query: String
    ) -> [DynamicCurriculumMatrix] {
        let normQuery = normalizeLookupText(query)
        guard !normQuery.isEmpty else { return matrices }
        let tokens = normQuery.split(separator: " ").map(String.init).filter { !$0.isEmpty }

        return matrices.compactMap { matrix -> (matrix: DynamicCurriculumMatrix, score: Int)? in
            let normCode = normalizeLookupText(matrix.courseCode)
            let normName = normalizeLookupText(matrix.courseName)
            let normCampus = normalizeLookupText(matrix.campus)
            let normLabel = normalizeLookupText(matrix.curriculumLabel)
            let normAliases = matrix.courseAliases.map { normalizeLookupText($0) }.joined(separator: " ")
            let haystack = "\(normName) \(normCode) \(normCampus) \(normLabel) \(normAliases)"

            let score: Int = {
                if normCode == normQuery { return 1000 }
                if normName.hasPrefix(normQuery) { return 800 }
                if normCode.hasPrefix(normQuery) { return 750 }
                if normName.contains(normQuery) { return 600 }
                if normCampus.contains(normQuery) { return 450 }
                if !tokens.isEmpty && tokens.allSatisfy({ haystack.contains($0) }) { return 350 }
                return 0
            }()
            return score > 0 ? (matrix, score) : nil
        }
        .sorted { lhs, rhs in
            if lhs.score != rhs.score { return lhs.score > rhs.score }
            let lName = normalizeLookupText(lhs.matrix.courseName)
            let rName = normalizeLookupText(rhs.matrix.courseName)
            if lName != rName { return lName < rName }
            if lhs.matrix.effectiveFrom != rhs.matrix.effectiveFrom {
                return lhs.matrix.effectiveFrom < rhs.matrix.effectiveFrom
            }
            let lNum = extractCurriculumNumber(lhs.matrix.curriculumLabel) ?? Int.max
            let rNum = extractCurriculumNumber(rhs.matrix.curriculumLabel) ?? Int.max
            if lNum != rNum { return lNum < rNum }
            return lhs.matrix.curriculumLabel < rhs.matrix.curriculumLabel
        }
        .map(\.matrix)
    }

    private static func isLowerMatrixVersion(_ lhs: DynamicCurriculumMatrix, _ rhs: DynamicCurriculumMatrix) -> Bool {
        if lhs.effectiveFrom != rhs.effectiveFrom {
            return lhs.effectiveFrom < rhs.effectiveFrom
        }
        let lNum = extractCurriculumNumber(lhs.curriculumLabel) ?? 0
        let rNum = extractCurriculumNumber(rhs.curriculumLabel) ?? 0
        if lNum != rNum {
            return lNum < rNum
        }
        return lhs.curriculumLabel < rhs.curriculumLabel
    }

    static func loadCompletedCodesWithLegacyFallback(
        matrix: DynamicCurriculumMatrix,
        reader: (String) -> Set<String>
    ) -> Set<String> {
        let primary = reader(matrix.id)
        if !primary.isEmpty { return primary }
        for legacyId in matrix.legacyIds {
            let legacy = reader(legacyId)
            if !legacy.isEmpty { return legacy }
        }
        return []
    }

    static func resolveMatrixForStudent(
        courseName: String,
        registrationNumber: String,
        earliestTranscriptSemester: String? = nil,
        catalogCourses: [UfopCourseCurriculum] = UfopCurriculumCatalog.allCourses,
        campusHint: String? = nil
    ) -> DynamicMatrixResolution {
        let regTuple = UfopCurriculumCatalog.parseEnrollmentYearSemester(registrationNumber)
        let txTuple = earliestTranscriptSemester.flatMap { parseSemesterTerm($0) }

        let entryTuple: (year: Int, semester: Int)? = {
            switch (regTuple, txTuple) {
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
        let entryTerm = entryTuple.map { DynamicSemesterTerm(year: $0.year, semester: $0.semester) }

        let trimmedCourseName = courseName.trimmingCharacters(in: .whitespacesAndNewlines)
        let matchedCatalog: UfopCourseCurriculum? = (!catalogCourses.isEmpty && !trimmedCourseName.isEmpty)
            ? UfopCurriculumCatalog.findCourseExactOrBest(in: catalogCourses, query: trimmedCourseName, campusHint: campusHint)
            : nil

        let courseMatrices: [DynamicCurriculumMatrix]
        if let matchedCatalog {
            let curatedMatches = curatedMatrices.filter {
                $0.courseCode.caseInsensitiveCompare(matchedCatalog.code) == .orderedSame
            }
            let byCatalogCode: [DynamicCurriculumMatrix]
            if !curatedMatches.isEmpty {
                byCatalogCode = curatedMatches
            } else {
                var seenIds: Set<String> = []
                var expanded: [DynamicCurriculumMatrix] = []
                for version in UfopCurriculumCatalog.courseVersions(for: matchedCatalog) {
                    if let matrix = DynamicGridSingleMatrixCache.matrix(for: version),
                       seenIds.insert(matrix.id).inserted {
                        expanded.append(matrix)
                    }
                }
                for course in catalogCourses where course.code.caseInsensitiveCompare(matchedCatalog.code) == .orderedSame {
                    for version in UfopCurriculumCatalog.courseVersions(for: course) {
                        if let matrix = DynamicGridSingleMatrixCache.matrix(for: version),
                           seenIds.insert(matrix.id).inserted {
                            expanded.append(matrix)
                        }
                    }
                }
                byCatalogCode = expanded
            }
            if byCatalogCode.isEmpty {
                return DynamicMatrixResolution(
                    matrix: defaultMatrix,
                    studentEntryTerm: entryTerm,
                    isCourseMatched: false,
                    isExactVersionMatch: false,
                    note: "O detalhamento curricular de \(matchedCatalog.name) ainda está pendente no catálogo. Escolha outro curso no seletor acima para explorar."
                )
            }
            courseMatrices = byCatalogCode
        } else if trimmedCourseName.isEmpty {
            courseMatrices = []
        } else {
            let curatedMatches = findMatrices(forCourseName: trimmedCourseName, in: curatedMatrices)
            if !curatedMatches.isEmpty || catalogCourses.isEmpty {
                courseMatrices = curatedMatches
            } else {
                let defaultCatalog = UfopCurriculumCatalog.allCourses
                let usesDefaultCatalog = catalogCourses.count == defaultCatalog.count
                    && catalogCourses.first?.code == defaultCatalog.first?.code
                    && catalogCourses.last?.code == defaultCatalog.last?.code
                let matrices = usesDefaultCatalog ? Self.allMatrices : buildAllMatrices(from: catalogCourses)
                courseMatrices = findMatrices(forCourseName: trimmedCourseName, in: matrices)
            }
        }

        guard !courseMatrices.isEmpty else {
            return DynamicMatrixResolution(
                matrix: defaultMatrix,
                studentEntryTerm: entryTerm,
                isCourseMatched: false,
                isExactVersionMatch: false,
                note: trimmedCourseName.isEmpty ? nil : "Matriz de \(trimmedCourseName) ainda não cadastrada na Grade Dinâmica"
            )
        }

        if let entryTerm {
            let exactMatches = courseMatrices.filter { matrix in
                matrix.effectiveFrom <= entryTerm &&
                    (matrix.effectiveUntil.map { entryTerm <= $0 } ?? true)
            }
            if let bestExact = exactMatches.max(by: isLowerMatrixVersion) {
                return DynamicMatrixResolution(
                    matrix: bestExact,
                    studentEntryTerm: entryTerm,
                    isCourseMatched: true,
                    isExactVersionMatch: true,
                    note: "Ingresso \(entryTerm.formatLabel()) · Selecionada automaticamente"
                )
            }

            if let oldest = courseMatrices.min(by: isLowerMatrixVersion),
               entryTerm < oldest.effectiveFrom {
                return DynamicMatrixResolution(
                    matrix: oldest,
                    studentEntryTerm: entryTerm,
                    isCourseMatched: true,
                    isExactVersionMatch: false,
                    note: "Ingresso \(entryTerm.formatLabel()) · Matriz antiga não cadastrada; exibindo \(oldest.curriculumLabel)"
                )
            }
        }

        let latest = courseMatrices.filter { $0.effectiveUntil == nil }.max(by: isLowerMatrixVersion)
            ?? courseMatrices.max(by: isLowerMatrixVersion)
            ?? defaultMatrix

        return DynamicMatrixResolution(
            matrix: latest,
            studentEntryTerm: entryTerm,
            isCourseMatched: true,
            isExactVersionMatch: entryTerm != nil,
            note: entryTerm.map { "Ingresso \($0.formatLabel()) · \(latest.curriculumLabel)" }
        )
    }

    private static func findMatrices(
        forCourseName courseName: String,
        in matrices: [DynamicCurriculumMatrix]
    ) -> [DynamicCurriculumMatrix] {
        let normInput = normalizeLookupText(courseName)
        guard !normInput.isEmpty else { return [] }

        return matrices.filter { matrix in
            let normCourse = normalizeLookupText(matrix.courseName)
            let normCode = normalizeLookupText(matrix.courseCode)
            let normAliases = matrix.courseAliases.map { normalizeLookupText($0) }
            if normInput == normCode || normInput == normCourse {
                return true
            }
            return normAliases.contains { alias in
                !alias.isEmpty && (
                    normInput == alias ||
                        normInput.contains(alias) ||
                        (normInput.count >= 6 && alias.contains(normInput))
                )
            }
        }
    }

    static func parseSemesterTerm(_ raw: String) -> (year: Int, semester: Int)? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        guard let regex = DynamicGridEngineRegex.semesterTerm,
              let match = regex.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)),
              let r1 = Range(match.range(at: 1), in: trimmed),
              let r2 = Range(match.range(at: 2), in: trimmed),
              let rawYearInt = Int(trimmed[r1]),
              let sem = Int(trimmed[r2]), (sem == 1 || sem == 2) else {
            return nil
        }
        let yearStr = String(trimmed[r1])
        let fullYear = yearStr.count == 4 ? rawYearInt : (rawYearInt >= 69 ? 1900 + rawYearInt : 2000 + rawYearInt)
        guard (1969...2099).contains(fullYear) else { return nil }
        return (fullYear, sem)
    }

    internal static func normalizeLookupText(_ raw: String) -> String {
        let folded = raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "pt_BR"))
            .lowercased()
            .replacingOccurrences(of: "eng.", with: "engenharia")
        let parts = folded
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
        return parts.joined(separator: " ")
    }
}

// MARK: - Dynamic Grid Engine

public enum DynamicGridEngine {
    private static func normalizedSet(_ codes: Set<String>) -> Set<String> {
        Set(
            codes
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() }
                .filter { !$0.isEmpty }
        )
    }

    public static func completedHours(
        matrix: DynamicCurriculumMatrix,
        completedCodes: Set<String>
    ) -> Int {
        let normalized = normalizedSet(completedCodes)
        return matrix.courses.reduce(0) { sum, course in
            normalized.contains(course.code.uppercased()) ? sum + course.workloadHours : sum
        }
    }

    public static func missingPrerequisites(
        for course: DynamicCourseNode,
        completedCodes: Set<String>
    ) -> [String] {
        let normalized = normalizedSet(completedCodes)
        return course.prerequisites.filter { !normalized.contains($0.uppercased()) }
    }

    public static func status(
        for course: DynamicCourseNode,
        matrix: DynamicCurriculumMatrix,
        completedCodes: Set<String>
    ) -> DynamicCourseStatus {
        let sanitized = sanitizeCompleted(matrix: matrix, completedCodes: completedCodes)
        if sanitized.contains(course.code) {
            return .completed
        }
        let missingPrereqs = missingPrerequisites(for: course, completedCodes: sanitized)
        let currentHours = completedHours(matrix: matrix, completedCodes: sanitized)
        if missingPrereqs.isEmpty && currentHours >= course.minHoursRequired {
            return .available
        }
        return .locked
    }

    public static func sanitizeCompleted(
        matrix: DynamicCurriculumMatrix,
        completedCodes: Set<String>
    ) -> Set<String> {
        let normalizedInput = normalizedSet(completedCodes)
        var validSet = Set(
            matrix.courses
                .filter { normalizedInput.contains($0.code.uppercased()) }
                .map(\.code)
        )

        var didPrune = true
        while didPrune {
            didPrune = false
            let currentHours = completedHours(matrix: matrix, completedCodes: validSet)
            for course in matrix.courses where validSet.contains(course.code) {
                let hasMissingPrereq = course.prerequisites.contains { !validSet.contains($0) }
                let hoursExcludingSelf = max(0, currentHours - course.workloadHours)
                let lacksHours = course.minHoursRequired > hoursExcludingSelf
                if hasMissingPrereq || lacksHours {
                    validSet.remove(course.code)
                    didPrune = true
                }
            }
        }
        return validSet
    }

    public static func toggleCourse(
        matrix: DynamicCurriculumMatrix,
        courseCode: String,
        completedCodes: Set<String>
    ) -> DynamicGridToggleResult {
        let cleanCode = courseCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let sanitized = sanitizeCompleted(matrix: matrix, completedCodes: completedCodes)

        guard let course = matrix.course(forCode: cleanCode) else {
            return DynamicGridToggleResult(
                updatedCompletedCodes: sanitized,
                action: .noChange,
                affectedCode: cleanCode,
                cascadeRemovedCodes: [],
                missingPrerequisites: [],
                missingHours: 0
            )
        }

        if sanitized.contains(course.code) {
            var remaining = sanitized
            remaining.remove(course.code)

            var cascadeSet: Set<String> = []
            var didRemove = true
            while didRemove {
                didRemove = false
                let currentHours = completedHours(matrix: matrix, completedCodes: remaining)
                for candidate in matrix.courses where remaining.contains(candidate.code) {
                    let missingPrereq = candidate.prerequisites.contains { !remaining.contains($0) }
                    let hoursExcludingSelf = max(0, currentHours - candidate.workloadHours)
                    let lacksHours = candidate.minHoursRequired > hoursExcludingSelf
                    if missingPrereq || lacksHours {
                        remaining.remove(candidate.code)
                        cascadeSet.insert(candidate.code)
                        didRemove = true
                    }
                }
            }

            let orderedCascade = matrix.courses
                .map(\.code)
                .filter { cascadeSet.contains($0) }

            return DynamicGridToggleResult(
                updatedCompletedCodes: remaining,
                action: .unmarkedWithCascade,
                affectedCode: course.code,
                cascadeRemovedCodes: orderedCascade,
                missingPrerequisites: [],
                missingHours: 0
            )
        }

        let missingPrereqs = missingPrerequisites(for: course, completedCodes: sanitized)
        let currentHours = completedHours(matrix: matrix, completedCodes: sanitized)
        let missingHours = max(0, course.minHoursRequired - currentHours)

        if !missingPrereqs.isEmpty || missingHours > 0 {
            return DynamicGridToggleResult(
                updatedCompletedCodes: sanitized,
                action: .blockedByPrerequisites,
                affectedCode: course.code,
                cascadeRemovedCodes: [],
                missingPrerequisites: missingPrereqs,
                missingHours: missingHours
            )
        }

        var updated = sanitized
        updated.insert(course.code)
        return DynamicGridToggleResult(
            updatedCompletedCodes: updated,
            action: .markedCompleted,
            affectedCode: course.code,
            cascadeRemovedCodes: [],
            missingPrerequisites: [],
            missingHours: 0
        )
    }

    public static func summarize(
        matrix: DynamicCurriculumMatrix,
        completedCodes: Set<String>,
        alreadySanitized: Bool = false
    ) -> DynamicGridSummary {
        let sanitized = alreadySanitized
            ? completedCodes
            : sanitizeCompleted(matrix: matrix, completedCodes: completedCodes)
        let completedHrs = completedHours(matrix: matrix, completedCodes: sanitized)
        let totalHrs = matrix.totalGridHours
        let percent: Int = {
            guard totalHrs > 0 else { return 0 }
            let raw = (Double(completedHrs) * 100.0 / Double(totalHrs)).rounded()
            return min(100, max(0, Int(raw)))
        }()

        return DynamicGridSummary(
            completedHours: completedHrs,
            totalGridHours: totalHrs,
            completionPercent: percent,
            completedCount: sanitized.count,
            totalCount: matrix.courses.count
        )
    }
}
