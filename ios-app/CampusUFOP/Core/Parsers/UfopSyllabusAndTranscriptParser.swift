import Foundation

// MARK: - Parsers de Plano de Ensino e Histórico Escolar UFOP

public extension UfopPdfParsers {

    static func parseSyllabus(
        text: String,
        targetCourseCode: String,
        targetCourseName: String,
        semester: String = "2026.2"
    ) -> SyllabusImportReport {
        let lines = text.components(separatedBy: .newlines)
            .map { $0.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression).trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }

        guard !lines.isEmpty else {
            return SyllabusImportReport(
                matchedCourse: false,
                matchedCourseCode: nil,
                matchedCourseTitle: nil,
                extractedProfessor: "",
                hasGradeDistribution: false,
                hasAssessmentDates: false,
                parsedAssessments: [],
                warningMessage: nil,
                errorMessage: "O PDF do plano de ensino está vazio ou ilegível."
            )
        }

        let headerLines = Array(lines.prefix(45)).filter { !normalizeSearch($0).contains("pre-requisito") && !normalizeSearch($0).contains("prerequisito") }
        let nonPrereqLines = lines.filter { !normalizeSearch($0).contains("pre-requisito") && !normalizeSearch($0).contains("prerequisito") }

        let targetCode = extractRealCourseCode(targetCourseCode)
            ?? extractRealCourseCode(firstCapture(#"\b([A-Z]{2,4}\d{3,4}[A-Z]?)\b"#, in: targetCourseName) ?? "")

        let headerCodes = findCourseCodes(in: headerLines)
        let bodyCodes = headerCodes.isEmpty ? findCourseCodes(in: nonPrereqLines) : headerCodes
        let targetCodeMatched = targetCode != nil && (
            headerCodes.contains(where: { $0.caseInsensitiveCompare(targetCode!) == .orderedSame }) ||
            bodyCodes.contains(where: { $0.caseInsensitiveCompare(targetCode!) == .orderedSame })
        )

        let strippedTargetName = targetCourseName.replacingOccurrences(of: #"^\s*[A-Z]{2,4}\d{3,4}[A-Z]?\s*[-–—:]?\s*"#, with: "", options: .regularExpression)
        let stopwords: Set<String> = ["das", "dos", "com", "para", "por", "que", "uma", "uns", "umas", "nos", "nas", "sobre", "entre"]
        let allTokens = normalizeSearch(strippedTargetName).split(whereSeparator: { !$0.isLetter && !$0.isNumber }).map(String.init)
        let significant = Array(Set(allTokens.filter { $0.count >= 3 && !stopwords.contains($0) }))
        let searchableNorm = normalizeSearch(nonPrereqLines.joined(separator: " "))
        let matchedTokens = significant.filter { tok in
            firstMatch(#"\b"# + NSRegularExpression.escapedPattern(for: tok) + #"\b"#, in: searchableNorm) != nil
        }
        let hasNameMatch = !significant.isEmpty && (matchedTokens.count * 2 >= significant.count)

        let detectedOtherCode = (headerCodes + bodyCodes).first(where: { c in
            targetCode == nil || c.caseInsensitiveCompare(targetCode!) != .orderedSame
        })

        let matched = targetCodeMatched || (hasNameMatch && detectedOtherCode == nil) || (targetCode == nil && hasNameMatch)
        guard matched else {
            let detectedInfo = [detectedOtherCode, headerLines.first].compactMap { $0 }.joined(separator: " · ")
            return SyllabusImportReport(
                matchedCourse: false,
                matchedCourseCode: detectedOtherCode,
                matchedCourseTitle: headerLines.first,
                extractedProfessor: "",
                hasGradeDistribution: false,
                hasAssessmentDates: false,
                parsedAssessments: [],
                warningMessage: nil,
                errorMessage: "Este plano de ensino parece ser de outra disciplina (\(detectedInfo)), e não de \(targetCourseName)."
            )
        }

        let extractedProfessor = extractProfessorFromSyllabus(lines: lines)

        let fullNorm = normalizeSearch(lines.joined(separator: " "))
        let hasGradeDistribution = (fullNorm.contains("avaliac") || fullNorm.contains("distribuic") || fullNorm.contains("criterio") || fullNorm.contains("nota"))
            && (fullNorm.contains("prova") || fullNorm.contains("trabalho") || fullNorm.contains("seminario") || fullNorm.contains("ponto") || fullNorm.contains("%") || fullNorm.contains("peso"))

        let assessments = extractSyllabusAssessments(lines: lines, semester: semester)
        let hasDateToken = firstMatch(#"\b\d{1,2}/\d{1,2}(?:/\d{2,4})?\b"#, in: lines.joined(separator: "\n")) != nil
        let hasAssessmentDates = hasDateToken || !assessments.isEmpty

        let warningMessage: String? = {
            if !hasGradeDistribution && !hasAssessmentDates {
                return "Apenas a informação de disciplina e professor foi achada neste plano (sem divisão de notas nem datas de avaliações). Adicione as datas manualmente depois."
            } else if !hasGradeDistribution {
                return "Apenas professor, disciplina e algumas datas foram achadas, sem detalhamento claro da divisão de notas. Revise manualmente se necessário."
            } else if !hasAssessmentDates {
                return "Apenas a informação do professor, disciplina e divisão de notas foi achada, sem datas explícitas. Adicione as datas manualmente depois."
            }
            return nil
        }()

        return SyllabusImportReport(
            matchedCourse: true,
            matchedCourseCode: targetCode ?? headerCodes.first,
            matchedCourseTitle: targetCourseName,
            extractedProfessor: extractedProfessor,
            hasGradeDistribution: hasGradeDistribution,
            hasAssessmentDates: hasAssessmentDates,
            parsedAssessments: assessments,
            warningMessage: warningMessage,
            errorMessage: nil
        )
    }

    static func parseSyllabusAutoDetect(
        text: String,
        courses: [DisciplineSummaryCard],
        semester: String = "2026.2"
    ) -> SyllabusImportReport {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return SyllabusImportReport(
                matchedCourse: false,
                matchedCourseId: nil,
                matchedCourseCode: nil,
                matchedCourseTitle: nil,
                extractedProfessor: "",
                hasGradeDistribution: false,
                hasAssessmentDates: false,
                parsedAssessments: [],
                warningMessage: nil,
                errorMessage: "O PDF do plano de ensino está vazio ou ilegível."
            )
        }

        let targetSem = semester.trimmingCharacters(in: .whitespacesAndNewlines)
        let activeCandidates = courses.filter {
            targetSem.isEmpty || $0.semester.caseInsensitiveCompare(targetSem) == .orderedSame
        }

        guard !activeCandidates.isEmpty else {
            return SyllabusImportReport(
                matchedCourse: false,
                matchedCourseId: nil,
                matchedCourseCode: nil,
                matchedCourseTitle: nil,
                extractedProfessor: "",
                hasGradeDistribution: false,
                hasAssessmentDates: false,
                parsedAssessments: [],
                warningMessage: nil,
                errorMessage: "Nenhuma disciplina matriculada neste semestre para vincular o plano."
            )
        }

        let evals = activeCandidates.map { course in
            (course: course, report: parseSyllabus(text: trimmed, targetCourseCode: course.code, targetCourseName: course.name, semester: targetSem))
        }
        let matched = evals.filter { $0.report.matchedCourse }

        let codeMatched = matched.filter { pair in
            guard let courseCode = extractRealCourseCode(pair.course.code)
                ?? extractRealCourseCode(firstCapture(#"\b([A-Z]{2,4}\d{3,4}[A-Z]?)\b"#, in: pair.course.name) ?? ""),
                  let detectedCode = pair.report.matchedCourseCode else {
                return false
            }
            return courseCode.caseInsensitiveCompare(detectedCode) == .orderedSame
        }

        let winner: (course: DisciplineSummaryCard, report: SyllabusImportReport)
        if codeMatched.count == 1 {
            winner = codeMatched[0]
        } else if matched.count == 1 {
            winner = matched[0]
        } else if matched.isEmpty {
            let sample = evals[0].report
            let info = [sample.matchedCourseCode, sample.matchedCourseTitle]
                .compactMap { $0 }
                .joined(separator: " · ")
            let infoLabel = info.isEmpty ? "disciplina não reconhecida" : info
            return SyllabusImportReport(
                matchedCourse: false,
                matchedCourseId: nil,
                matchedCourseCode: sample.matchedCourseCode,
                matchedCourseTitle: sample.matchedCourseTitle,
                extractedProfessor: "",
                hasGradeDistribution: false,
                hasAssessmentDates: false,
                parsedAssessments: [],
                warningMessage: nil,
                errorMessage: "Não foi possível vincular este Plano de Ensino (\(infoLabel)) a nenhuma disciplina matriculada. Nenhuma alteração foi feita."
            )
        } else {
            return SyllabusImportReport(
                matchedCourse: false,
                matchedCourseId: nil,
                matchedCourseCode: matched[0].report.matchedCourseCode,
                matchedCourseTitle: matched[0].report.matchedCourseTitle,
                extractedProfessor: "",
                hasGradeDistribution: false,
                hasAssessmentDates: false,
                parsedAssessments: [],
                warningMessage: nil,
                errorMessage: "Mais de uma disciplina matriculada corresponde a este plano. Selecione a disciplina manualmente abaixo."
            )
        }

        return SyllabusImportReport(
            matchedCourse: true,
            matchedCourseId: winner.course.id,
            matchedCourseCode: winner.report.matchedCourseCode ?? extractRealCourseCode(winner.course.code),
            matchedCourseTitle: winner.course.name,
            extractedProfessor: winner.report.extractedProfessor,
            hasGradeDistribution: winner.report.hasGradeDistribution,
            hasAssessmentDates: winner.report.hasAssessmentDates,
            parsedAssessments: winner.report.parsedAssessments,
            warningMessage: winner.report.warningMessage,
            errorMessage: nil
        )
    }

    static func parseTranscript(text: String) throws -> ParsedTranscriptResult {
        let lines = text.components(separatedBy: .newlines)
            .map { $0.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression).trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }

        let collapsed = lines.joined(separator: " ")
        let registrationNumber = firstCapture(#"\b(\d{2}\.\d\.\d{3,5})\b"#, in: collapsed)
            ?? firstCapture(#"Matr[ií]cula\s*:?\s*([\d.\-/]+)"#, in: collapsed)

        var fullName: String?
        for (idx, line) in lines.enumerated() {
            if let inline = firstCapture(#"^Nome\s*:\s*(.+)$"#, in: line) {
                let cleaned = inline.replacingOccurrences(of: #"\bMatr[ií]cula\b.*$"#, with: "", options: .regularExpression)
                    .replacingOccurrences(of: #"\b\d{2}\.\d\.\d{3,5}\b"#, with: "", options: .regularExpression)
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                if !cleaned.isEmpty {
                    fullName = ufopTitleCase(cleaned)
                    break
                }
            } else if firstMatch(#"^Nome(?:\s+Matr[ií]cula)?\s*$"#, in: line) != nil, idx + 1 < lines.count {
                let next = lines[idx + 1].replacingOccurrences(of: #"\b\d{2}\.\d\.\d{3,5}\b"#, with: "", options: .regularExpression)
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                if !next.isEmpty && !normalizeSearch(next).hasPrefix("matricula") {
                    fullName = ufopTitleCase(next)
                    break
                }
            }
        }

        let courseName = lines.compactMap { firstCapture(#"CURSO:\s*(.+)$"#, in: $0) }
            .map { $0.replacingOccurrences(of: #"\s+Situa[çc][aã]o\b.*$"#, with: "", options: .regularExpression).trimmingCharacters(in: .whitespacesAndNewlines) }
            .first(where: { !$0.isEmpty })
            .map { ufopTitleCase($0) }

        let totalRequired = firstCapture(#"CARGA\s+CURSO\s+(\d{3,5})\b"#, in: collapsed).flatMap(Int.init)
        let completedHours = firstCapture(#"\bTOTAL\s*:\s*(\d{1,5})\b"#, in: collapsed).flatMap(Int.init)

        let rowPattern = #"^(\d{4})[/.-]([1234])\s+([A-Z]{2,4}\d{3,4}[A-Z]?)\s*-\s*(.+?)\s+(\d+)/\d+(?:\s+(?:(\d+(?:[.,]\d+)?)(?!/)|--|-))?(?:\s+(\d+)/(\d+))?\s+(AP|RN|RF|NF|CS|TR|DP|AE|DE|DJ|EA|PF|X)\b"#
        var entries: [TranscriptCourseEntry] = []
        var seenIds: Set<String> = []
        var i = 0

        while i < lines.count {
            var line = lines[i]
            var caps = extractRegexGroups(rowPattern, in: line)
            var step = 1
            if caps == nil, i + 1 < lines.count, firstMatch(#"^\d{4}[/.-][1234]\s+[A-Z]{2,4}\d{3,4}[A-Z]?\s*-"#, in: line) != nil {
                let combined = "\(line) \(lines[i + 1])"
                if let combCaps = extractRegexGroups(rowPattern, in: combined) {
                    line = combined
                    caps = combCaps
                    step = 2
                }
            }

            if let caps, caps.count >= 10 {
                let sem = "\(caps[1]).\(caps[2])"
                let code = caps[3].uppercased()
                let name = ufopTitleCase(caps[4])
                let workload = max(1, Int(caps[5]) ?? 60)
                let gradeStr = caps[6].replacingOccurrences(of: ",", with: ".")
                let grade = Double(gradeStr).map { min(10.0, max(0.0, $0)) }
                let absences = max(0, Int(caps[7]) ?? 0)
                let totalClasses = Int(caps[8]) ?? ((workload * 12) / 10)
                let status = caps[9].uppercased()

                let semSlug = sem.replacingOccurrences(of: ".", with: "_")
                var entryId = "tx_\(semSlug)_\(code.lowercased())"
                if !seenIds.insert(entryId).inserted {
                    entryId = "\(entryId)_\(entries.count + 1)"
                    seenIds.insert(entryId)
                }

                entries.append(
                    TranscriptCourseEntry(
                        id: entryId,
                        semester: sem,
                        code: code,
                        name: name,
                        workloadHours: workload,
                        grade: grade,
                        absences: absences,
                        totalClasses: totalClasses,
                        status: status
                    )
                )
            }
            i += step
        }

        guard !entries.isEmpty else {
            throw UfopPdfParserError.transcriptNoCourses
        }

        return ParsedTranscriptResult(
            fullName: fullName,
            registrationNumber: registrationNumber,
            courseName: courseName,
            totalRequiredHours: totalRequired,
            completedHours: completedHours,
            entries: entries
        )
    }

    static func computeCreReport(
        entries: [TranscriptCourseEntry],
        totalRequiredHours: Int = 3610,
        extraCourses: [(workloadHours: Int, grade: Double)] = []
    ) -> AcademicCreReport {
        var weightedSum = 0.0
        var weightedHours = 0
        var completed = 0
        var approved = 0
        var failedGrade = 0
        var failedAbs = 0

        for entry in entries {
            let countsAsApproved = entry.status == "AP" || entry.status == "AE" || entry.status == "DP"
            if countsAsApproved {
                completed += entry.workloadHours
                approved += 1
            } else if entry.status == "RN" || entry.status == "NF" {
                failedGrade += 1
            } else if entry.status == "RF" {
                failedAbs += 1
            }

            if let g = entry.grade, entry.status != "CS" && entry.status != "TR" && entry.status != "AE" {
                weightedSum += g * Double(entry.workloadHours)
                weightedHours += entry.workloadHours
            }
        }

        for extra in extraCourses where extra.workloadHours > 0 {
            weightedSum += min(10.0, max(0.0, extra.grade)) * Double(extra.workloadHours)
            weightedHours += extra.workloadHours
        }

        let overallCre = weightedHours > 0 ? (weightedSum / Double(weightedHours)) : 0.0
        let grouped = Dictionary(grouping: entries, by: \.semester)
        let summaries: [SemesterCreSummary] = grouped.keys.sorted(by: >).map { sem in
            let list = grouped[sem] ?? []
            var sSum = 0.0
            var sHours = 0
            var sApp = 0
            var sFail = 0
            var sProg = 0
            for e in list {
                if ["AP", "AE", "DP"].contains(e.status) { sApp += 1 }
                else if ["RN", "RF", "NF"].contains(e.status) { sFail += 1 }
                else if e.status == "CS" { sProg += 1 }
                if let g = e.grade, e.status != "CS" && e.status != "TR" && e.status != "AE" {
                    sSum += g * Double(e.workloadHours)
                    sHours += e.workloadHours
                }
            }
            return SemesterCreSummary(
                semester: sem,
                cre: sHours > 0 ? sSum / Double(sHours) : 0.0,
                totalWorkloadHours: list.reduce(0) { $0 + $1.workloadHours },
                approvedCount: sApp,
                failedCount: sFail,
                inProgressCount: sProg,
                courses: list
            )
        }

        return AcademicCreReport(
            overallCre: overallCre,
            totalRequiredHours: totalRequiredHours > 0 ? totalRequiredHours : 3610,
            completedHours: completed,
            totalApproved: approved,
            totalFailedByGrade: failedGrade,
            totalFailedByAbsence: failedAbs,
            semesters: summaries
        )
    }

    // MARK: Private Syllabus Helpers

    private static func extractProfessorFromSyllabus(lines: [String]) -> String {
        let pattern = #"^(?:professor(?:\(a\)|a)?(?:\s+(?:respons[áa]vel|ministrante))?|docente(?:\(s\)|(?:\s+(?:respons[áa]vel|ministrante)))?|ministrante|respons[áa]vel(?:\s+pela\s+disciplina)?)\s*[:\-–]\s*(.*)$"#
        let noise: Set<String> = ["a definir", "nao informado", "sem professor", "pendente", "n/a", "ufop", "departamento", "colegiado", "assinatura"]
        let limit = min(lines.count, 60)
        for i in 0..<limit {
            guard let caps = extractRegexGroups(pattern, in: lines[i]), caps.count >= 2 else { continue }
            var candidate = caps[1].trimmingCharacters(in: .whitespacesAndNewlines)
            if candidate.isEmpty && i + 1 < lines.count && !lines[i + 1].contains(":") {
                candidate = lines[i + 1]
            }
            candidate = candidate
                .replacingOccurrences(of: #"\S+@\S+"#, with: " ", options: .regularExpression)
                .replacingOccurrences(of: #"\s+(?:e-?mail|siape|carga\s+hor[áa]ria|turma|semestre|per[íi]odo|hor[áa]rio|departamento|campus|sala)\b.*$"#, with: "", options: [.regularExpression, .caseInsensitive])
                .trimmingCharacters(in: CharacterSet(charactersIn: " :-–—|;,_"))
            let norm = normalizeSearch(candidate)
            if candidate.count >= 3 && candidate.count <= 80 && !noise.contains(norm) && !norm.hasPrefix("a definir") && !norm.hasPrefix("nao informado") {
                return ufopTitleCase(candidate)
            }
        }
        return ""
    }

    private static func extractSyllabusAssessments(lines: [String], semester: String) -> [ParsedSyllabusAssessment] {
        let yearPrefix = semester.split(separator: ".").first.map(String.init) ?? "2026"
        var items: [ParsedSyllabusAssessment] = []
        var seen: Set<String> = []
        let datePattern = #"\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b"#

        for line in lines {
            let norm = normalizeSearch(line)
            let detectedKind: String? = {
                if norm.contains("prova") || norm.contains("exame") || norm.contains("avaliacao") { return "Prova" }
                if norm.contains("seminario") || norm.contains("apresentacao") { return "Seminário" }
                if norm.contains("trabalho") || norm.contains("projeto") || norm.contains("lista") || norm.contains("relatorio") { return "Trabalho" }
                return nil
            }()
            guard let kind = detectedKind,
                  let caps = extractRegexGroups(datePattern, in: line), caps.count >= 3,
                  let day = Int(caps[1]), let month = Int(caps[2]),
                  (1...31).contains(day), (1...12).contains(month) else {
                continue
            }
            let year: String = {
                if caps.count >= 4 && !caps[3].isEmpty {
                    return caps[3].count == 2 ? "20\(caps[3])" : caps[3]
                }
                return yearPrefix
            }()
            let isoDate = String(format: "%@-%02d-%02d", year, month, day)
            let cleanedTitle = line.replacingOccurrences(of: datePattern, with: "", options: .regularExpression)
                .trimmingCharacters(in: CharacterSet(charactersIn: " :-–—|;,"))
            let title = cleanedTitle.isEmpty ? "\(kind) \(String(format: "%02d/%02d", day, month))" : String(cleanedTitle.prefix(64))
            let dedup = "\(isoDate)|\(normalizeSearch(title))"
            if seen.insert(dedup).inserted {
                items.append(ParsedSyllabusAssessment(title: title, kind: kind, dueDate: isoDate, dueTime: "18:00"))
            }
        }
        return items
    }

    private static func findCourseCodes(in lines: [String]) -> [String] {
        var result: [String] = []
        for line in lines {
            guard let regex = try? NSRegularExpression(pattern: #"\b([A-Z]{2,4}\d{3,4}[A-Z]?)\b"#) else { continue }
            for match in regex.matches(in: line, range: NSRange(line.startIndex..., in: line)) {
                if let range = Range(match.range(at: 1), in: line),
                   let code = extractRealCourseCode(String(line[range])),
                   !result.contains(code) {
                    result.append(code)
                }
            }
        }
        return result
    }

    private static func extractRealCourseCode(_ raw: String) -> String? {
        let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard firstMatch(#"^[A-Z]{2,4}\d{3,4}[A-Z]?$"#, in: clean) != nil else { return nil }
        let prefix = String(clean.prefix(while: \.isLetter))
        return nonCoursePrefixes.contains(prefix) ? nil : clean
    }
}
