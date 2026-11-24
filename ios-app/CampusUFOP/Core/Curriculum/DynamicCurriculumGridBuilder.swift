import Foundation

// MARK: - Dynamic Curriculum Matrix Builder

public extension DynamicCurriculumCatalog {

    static func fromCourseCurriculum(_ course: UfopCourseCurriculum) -> DynamicCurriculumMatrix? {
        guard !course.subjects.isEmpty else { return nil }
        let cleanCourseCode = course.code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let cleanCourseName = course.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanCourseCode.isEmpty, !cleanCourseName.isEmpty else { return nil }

        // Exclui atividades curriculares (já contabilizadas em requiredActivitiesHours) e deduplica códigos
        var seenCodes: Set<String> = []
        let curriculumSubjects = course.subjects.filter { subject in
            let typeNorm = subject.type.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            if typeNorm == "atividade" || typeNorm == "atv" { return false }
            let code = subject.code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            guard !code.isEmpty else { return false }
            return seenCodes.insert(code).inserted
        }
        guard !curriculumSubjects.isEmpty else { return nil }

        let maxSubjectPeriod = curriculumSubjects.map(\.period).max() ?? 1
        let totalPeriods = maxSubjectPeriod > 0 ? maxSubjectPeriod : 1
        let validSubjectCodes = Set(curriculumSubjects.map { $0.code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() })

        // Constrói grafo acíclico de pré-requisitos via verificação incremental de alcançabilidade
        var acceptedPrereqs: [String: [String]] = [:]
        func canReach(from start: String, target: String) -> Bool {
            var stack: [String] = [start]
            var visited: Set<String> = []
            while let current = stack.popLast() {
                if current == target { return true }
                if !visited.insert(current).inserted { continue }
                for next in (acceptedPrereqs[current] ?? []) where !visited.contains(next) {
                    stack.append(next)
                }
            }
            return false
        }

        let orderedSubjects = curriculumSubjects
            .enumerated()
            .map { index, subject -> (period: Int, index: Int, subject: UfopCurriculumSubject) in
                let resolved = subject.period > 0 ? min(subject.period, totalPeriods) : totalPeriods
                return (resolved, index, subject)
            }
            .sorted { lhs, rhs in
                if lhs.period != rhs.period { return lhs.period < rhs.period }
                return lhs.index < rhs.index
            }

        let prereqCodeRegex = try? NSRegularExpression(pattern: #"\b([A-Z]{2,5}\d{3,4}[A-Z]?)\b"#)
        let minHoursRegex = try? NSRegularExpression(pattern: #"\b(\d{1,5})\s*(?:h|hs|hora|horas)\b"#, options: [.caseInsensitive])

        var nodes: [DynamicCourseNode] = []
        nodes.reserveCapacity(orderedSubjects.count + 8)

        for item in orderedSubjects {
            let subject = item.subject
            let nodeCode = subject.code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            let workloadHours = max(0, subject.chs > 0 ? subject.chs : subject.cha)
            var minHoursRequired = 0
            var candidatePrereqs: [String] = []
            var candidateSet: Set<String> = []

            for rawPrereq in subject.prerequisites {
                let upper = rawPrereq.uppercased()
                if let regex = prereqCodeRegex {
                    let nsRange = NSRange(upper.startIndex..., in: upper)
                    for match in regex.matches(in: upper, range: nsRange) {
                        if let r1 = Range(match.range(at: 1), in: upper) {
                            let code = String(upper[r1])
                            if code != nodeCode && validSubjectCodes.contains(code) && candidateSet.insert(code).inserted {
                                candidatePrereqs.append(code)
                            }
                        }
                    }
                }
                if let hoursRegex = minHoursRegex {
                    let nsRange = NSRange(rawPrereq.startIndex..., in: rawPrereq)
                    for match in hoursRegex.matches(in: rawPrereq, range: nsRange) {
                        if let r1 = Range(match.range(at: 1), in: rawPrereq),
                           let parsedHours = Int(rawPrereq[r1]), parsedHours > minHoursRequired {
                            minHoursRequired = parsedHours
                        }
                    }
                }
            }

            var acceptedForNode = acceptedPrereqs[nodeCode] ?? []
            for prereq in candidatePrereqs {
                if !canReach(from: prereq, target: nodeCode) {
                    acceptedForNode.append(prereq)
                }
            }
            acceptedPrereqs[nodeCode] = acceptedForNode

            nodes.append(
                DynamicCourseNode(
                    code: nodeCode,
                    name: subject.name.trimmingCharacters(in: .whitespacesAndNewlines),
                    workloadHours: workloadHours,
                    period: item.period,
                    prerequisites: acceptedForNode,
                    minHoursRequired: minHoursRequired,
                    isElective: false
                )
            )
        }

        // Sintetiza blocos de optativas (OPT001..) e eletivas (ELT001..) distribuídos nos períodos finais
        var usedNodeCodes = Set(nodes.map(\.code))
        var synthesizedSpecs: [(code: String, title: String, hours: Int)] = []

        if course.chOptional > 0 {
            let blocks = splitWorkloadIntoSlots(course.chOptional)
            var nextNum = 1
            for (idx, hours) in blocks.enumerated() {
                var code = ""
                repeat {
                    code = String(format: "OPT%03d", nextNum)
                    nextNum += 1
                } while usedNodeCodes.contains(code)
                usedNodeCodes.insert(code)
                synthesizedSpecs.append((code, "Disciplina Optativa \(romanOrDecimal(idx + 1))", hours))
            }
        }

        if course.chElective > 0 {
            let blocks = splitWorkloadIntoSlots(course.chElective)
            var nextNum = 1
            for (idx, hours) in blocks.enumerated() {
                var code = ""
                repeat {
                    code = String(format: "ELT%03d", nextNum)
                    nextNum += 1
                } while usedNodeCodes.contains(code)
                usedNodeCodes.insert(code)
                synthesizedSpecs.append((code, "Disciplina Eletiva \(romanOrDecimal(idx + 1))", hours))
            }
        }

        if !synthesizedSpecs.isEmpty {
            let windowSize: Int = {
                if synthesizedSpecs.count <= 2 { return min(2, totalPeriods) }
                if synthesizedSpecs.count <= 6 { return min(3, totalPeriods) }
                return min(4, totalPeriods)
            }()
            let startPeriod = max(1, totalPeriods - windowSize + 1)
            for (index, spec) in synthesizedSpecs.enumerated() {
                let rawPeriod = startPeriod + (index * windowSize) / synthesizedSpecs.count
                let targetPeriod = min(totalPeriods, max(1, rawPeriod))
                nodes.append(
                    DynamicCourseNode(
                        code: spec.code,
                        name: spec.title,
                        workloadHours: spec.hours,
                        period: targetPeriod,
                        isElective: true
                    )
                )
            }
        }

        let computedGridHours = nodes.reduce(0) { $0 + $1.workloadHours }
        let requiredActivitiesHours: Int
        let totalCourseHours: Int
        if course.chTotal > computedGridHours {
            if course.chActivities > 0 && computedGridHours + course.chActivities == course.chTotal {
                requiredActivitiesHours = course.chActivities
            } else {
                requiredActivitiesHours = course.chTotal - computedGridHours
            }
            totalCourseHours = computedGridHours + requiredActivitiesHours
        } else {
            requiredActivitiesHours = max(0, course.chActivities)
            totalCourseHours = computedGridHours + requiredActivitiesHours
        }

        let versionTerm = parseSemesterTerm(course.curriculumVersion)
        let versionSlug: String = {
            if let versionTerm {
                return "\(versionTerm.year)_\(versionTerm.semester)"
            }
            let slug = normalizeLookupText(course.curriculumVersion).replacingOccurrences(of: " ", with: "_")
            return slug.isEmpty ? "2026_2" : slug
        }()
        let matrixId = "ufop_\(cleanCourseCode.lowercased())_\(versionSlug)"
        let curriculumLabel = formatCurriculumLabel(course.curriculumVersion)
        let effectiveFrom: DynamicSemesterTerm = {
            if let versionTerm, versionTerm.year < course.effectiveFromYear {
                return DynamicSemesterTerm(year: versionTerm.year, semester: versionTerm.semester)
            }
            return DynamicSemesterTerm(year: course.effectiveFromYear, semester: course.effectiveFromSemester)
        }()
        let effectiveUntil: DynamicSemesterTerm? = course.effectiveUntilYear.map { untilYear in
            DynamicSemesterTerm(year: untilYear, semester: course.effectiveUntilSemester ?? 2)
        }

        return DynamicCurriculumMatrix(
            id: matrixId,
            courseName: cleanCourseName,
            curriculumLabel: curriculumLabel,
            totalPeriods: totalPeriods,
            requiredActivitiesHours: requiredActivitiesHours,
            totalCourseHours: totalCourseHours,
            courses: nodes,
            courseCode: cleanCourseCode,
            courseAliases: buildCourseAliases(courseCode: cleanCourseCode, courseName: cleanCourseName),
            effectiveFrom: effectiveFrom,
            effectiveUntil: effectiveUntil,
            campus: course.campus.trimmingCharacters(in: .whitespacesAndNewlines)
        )
    }

    static func buildAllMatrices(from catalogCourses: [UfopCourseCurriculum]) -> [DynamicCurriculumMatrix] {
        guard !catalogCourses.isEmpty else { return curatedMatrices }
        let curatedCodes = Set(
            curatedMatrices
                .map { $0.courseCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() }
                .filter { !$0.isEmpty }
        )

        let generated = catalogCourses.compactMap { course -> DynamicCurriculumMatrix? in
            let code = course.code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            guard !curatedCodes.contains(code) else { return nil }
            return fromCourseCurriculum(course)
        }

        return (curatedMatrices + generated).sorted { lhs, rhs in
            let lName = normalizeLookupText(lhs.courseName)
            let rName = normalizeLookupText(rhs.courseName)
            if lName != rName { return lName < rName }
            return lhs.effectiveFrom < rhs.effectiveFrom
        }
    }

    private static func splitWorkloadIntoSlots(_ totalHours: Int) -> [Int] {
        guard totalHours > 0 else { return [] }
        var slots: [Int] = []
        var remaining = totalHours
        while remaining >= 60 {
            slots.append(60)
            remaining -= 60
        }
        if remaining > 0 {
            slots.append(remaining)
        }
        return slots
    }

    private static func romanOrDecimal(_ n: Int) -> String {
        switch n {
        case 1: return "I"
        case 2: return "II"
        case 3: return "III"
        case 4: return "IV"
        case 5: return "V"
        case 6: return "VI"
        case 7: return "VII"
        case 8: return "VIII"
        case 9: return "IX"
        case 10: return "X"
        case 11: return "XI"
        case 12: return "XII"
        case 13: return "XIII"
        case 14: return "XIV"
        case 15: return "XV"
        case 16: return "XVI"
        case 17: return "XVII"
        case 18: return "XVIII"
        case 19: return "XIX"
        case 20: return "XX"
        default: return "\(n)"
        }
    }

    private static func formatCurriculumLabel(_ rawVersion: String) -> String {
        let trimmed = rawVersion.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "2026/2" }
        let formatted = trimmed.replacingOccurrences(
            of: #"\(\s*curr[ií]culo\s+(\d+)\s*\)"#,
            with: "· Currículo $1",
            options: [.regularExpression, .caseInsensitive]
        )
        return formatted
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func buildCourseAliases(courseCode: String, courseName: String) -> [String] {
        var aliases: [String] = []
        var seen: Set<String> = []
        func appendUnique(_ item: String) {
            let clean = item.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !clean.isEmpty else { return }
            if seen.insert(clean.lowercased()).inserted {
                aliases.append(clean)
            }
        }
        appendUnique(courseCode)
        appendUnique(courseName)
        let baseName = courseName
            .replacingOccurrences(of: #"\s*\([^)]*\)"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        appendUnique(baseName)
        if courseName.lowercased().hasPrefix("engenharia ") {
            let suffix = String(courseName.dropFirst("engenharia ".count)).trimmingCharacters(in: .whitespacesAndNewlines)
            appendUnique("eng. \(suffix)")
        }
        if baseName.lowercased().hasPrefix("engenharia ") {
            let suffix = String(baseName.dropFirst("engenharia ".count)).trimmingCharacters(in: .whitespacesAndNewlines)
            appendUnique("eng. \(suffix)")
        }
        return aliases
    }
}
