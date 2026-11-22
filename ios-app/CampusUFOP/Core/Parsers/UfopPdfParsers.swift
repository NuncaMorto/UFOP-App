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

// MARK: - Parsers (Atestado de Matrícula, Plano de Ensino, Histórico Escolar)

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

    private static let nonCoursePrefixes: Set<String> = [
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

    public static func parseEnrollmentCertificate(
        text: String,
        roomIndex: UfopRoomAllocationIndex = UfopRoomAllocationCatalog.shared
    ) throws -> ParsedCertificateResult {
        let lines = text.components(separatedBy: .newlines)
            .map { $0.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression).trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        let collapsed = lines.joined(separator: " ")

        let semester = extractCapture(#"ATESTADO\s+DE\s+MATR[IÍ]CULA\s+(\d{4})[/.](\d)"#, in: collapsed, groups: [1, 2])
            .map { "\($0[0]).\($0[1])" } ?? "2026.2"

        let fullName = firstCapture(#"Atestamos\s+que\s+(.+?)\s+matr[ií]cula\b"#, in: collapsed)
            .map { ufopTitleCase($0) } ?? ""

        let registrationNumber = firstCapture(#"matr[ií]cula\s+n[º°o.]+\s*([\d.\-/]+)"#, in: collapsed)
            ?? firstCapture(#"\b(\d{2}\.\d\.\d{3,5})\b"#, in: collapsed)
            ?? ""

        let courseName = firstCapture(#"no\s+curso\s+de\s+(.+?),\s*cuja\b"#, in: collapsed)
            .map { ufopTitleCase($0) } ?? ""

        let rawCampus = firstCapture(#"no\s+campus\s+([A-Za-zÀ-ú\s]+?)(?:\.|,|\s+Hor[aá]rio|$)"#, in: collapsed) ?? "Ouro Preto"
        let (campusId, campusCity) = resolveCampus(from: rawCampus)

        let startDate = parseLongDate(after: #"In[ií]cio\s+das\s+Aulas:\s*"#, in: text) ?? "2026-08-24"
        let endDate = parseLongDate(after: #"T[eé]rmino\s+das\s+Aulas:\s*"#, in: text) ?? "2026-12-23"

        // Tabela de disciplinas
        let knownBuildings = #"ENSINO\s+[AÀ]\s+DIST[AÂ]NCIA|[AÀ]\s+DIST[AÂ]NCIA|E\.A\.D\.|EAD|CEAD|AVA|MOODLE|ONLINE|VIRTUAL|REMOT[AO]|ICEB(?:\s*\(?(?:I|II|III)\)?)?|BLOCO\s+DE\s+SALAS|ESCOLA\s+DE\s+MINAS|ICHS|ICSA|ICEA|EMED|EFAR|EDTM|ENUT|CEDUFOP|DECOM|DEFIS|DEMAT|DEGEO|DEPRO|DECAT|DECIV|DEURB|DEART|DEMUS|DELET|DEEDU|DEPED|DEFAR|DENUT|DEENF|DEMED|DEBIO|DEQUI|DEAMB|DEARQ|DEHIS|DEDIR|DEECO|DEJOR|DESSO|DETUR|DEELT|DEMEC|DEMET|DEMIN|DECSI|DECEA|DEENP|DEALI|DEFIL|EM"#
        let strictRowPattern = #"^([A-Z]{3}\d{3}[A-Z]?)\s+(\d{1,3})\s+(.+?)\s+("# + knownBuildings + #")\s+(PORTUGU[EÊ]S|INGL[EÊ]S|ESPANHOL)\b"#
        let fallbackRowPattern = #"^([A-Z]{3}\d{3}[A-Z]?)\s+(\d{1,3})\s+(.+?)\s+([A-Z0-9]{2,12})\s+(PORTUGU[EÊ]S|INGL[EÊ]S|ESPANHOL)\b"#

        struct RawRow {
            let code: String
            let turma: String
            let title: String
            let building: String
            let isEad: Bool
        }

        var rows: [RawRow] = []
        var seenCodes: Set<String> = []
        for line in lines {
            let caps = extractRegexGroups(strictRowPattern, in: line) ?? extractRegexGroups(fallbackRowPattern, in: line)
            guard let caps, caps.count >= 5 else { continue }
            let code = caps[1].uppercased()
            guard seenCodes.insert(code).inserted else { continue }
            let turma = caps[2].trimmingCharacters(in: .whitespacesAndNewlines)
            let rawTitle = caps[3].trimmingCharacters(in: .whitespacesAndNewlines)
            let rawBuilding = caps[4].trimmingCharacters(in: .whitespacesAndNewlines)
            let ead = isEadIndicator(rawBuilding) || isEadIndicator(rawTitle) || isEadIndicator(line)
            let resolvedBuilding = ead ? "A distância (EAD)" : normalizeBuildingName(rawBuilding)
            rows.append(RawRow(code: code, turma: turma, title: ufopTitleCase(rawTitle), building: resolvedBuilding, isEad: ead))
        }

        // Slots da grade no texto do atestado
        let gridTokens = parseCertificateGridTokens(lines: text.components(separatedBy: .newlines))

        if rows.isEmpty && !gridTokens.isEmpty {
            var uniqueFromGrid: [String] = []
            for token in gridTokens where !seenCodes.contains(token.courseCode) {
                seenCodes.insert(token.courseCode)
                uniqueFromGrid.append(token.courseCode)
                rows.append(
                    RawRow(
                        code: token.courseCode,
                        turma: token.turma,
                        title: token.courseCode,
                        building: token.isEad ? "A distância (EAD)" : "Campus UFOP",
                        isEad: token.isEad
                    )
                )
            }
            _ = uniqueFromGrid
        }

        guard !rows.isEmpty else {
            throw UfopPdfParserError.certificateNoCourses
        }

        let catalogCourses = UfopCurriculumCatalog.allCourses

        var courses: [ParsedCertificateCourse] = []
        for row in rows {
            let rowTokens = gridTokens.filter { $0.courseCode == row.code }
            var slots: [ParsedCertificateSlot] = []
            for token in rowTokens {
                let slotIsEad = row.isEad || token.isEad
                let buildingName: String
                let roomName: String
                if slotIsEad {
                    buildingName = "A distância (EAD)"
                    roomName = ""
                } else if let alloc = roomIndex.lookup(
                    courseCode: row.code,
                    turma: row.turma.isEmpty ? token.turma : row.turma,
                    dayOfWeek: token.dayOfWeek,
                    startTime: token.startTime,
                    kind: token.kind
                ) {
                    buildingName = alloc.building
                    roomName = alloc.room
                } else {
                    buildingName = row.building
                    roomName = ""
                }
                slots.append(
                    ParsedCertificateSlot(
                        courseCode: row.code,
                        turma: row.turma.isEmpty ? token.turma : row.turma,
                        kind: token.kind,
                        dayOfWeek: token.dayOfWeek,
                        startTime: token.startTime,
                        endTime: token.endTime,
                        building: buildingName,
                        room: roomName,
                        isEad: slotIsEad
                    )
                )
            }

            let catalogSubject = catalogCourses.lazy.flatMap(\.subjects).first(where: { $0.code.uppercased() == row.code })
            let workload = catalogSubject?.chs ?? 60

            courses.append(
                ParsedCertificateCourse(
                    code: row.code,
                    turma: row.turma,
                    name: row.title,
                    building: row.building,
                    isEad: row.isEad,
                    departmentId: departmentIdFromCode(row.code),
                    workloadHours: workload > 0 ? workload : 60,
                    professor: "", // Deixar vazio se não houver plano de ensino (regra do usuário)
                    slots: slots
                )
            )
        }

        return ParsedCertificateResult(
            studentName: fullName,
            registrationNumber: registrationNumber,
            courseName: courseName,
            campusId: campusId,
            campusName: campusCity,
            semester: semester,
            semesterStartDate: startDate,
            semesterEndDate: endDate,
            courses: courses
        )
    }

    public static func parseSyllabus(
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

    public static func parseTranscript(text: String) throws -> ParsedTranscriptResult {
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

    public static func computeCreReport(
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

    // MARK: Private Helpers

    private struct GridToken {
        let courseCode: String
        let turma: String
        let kind: String
        let dayOfWeek: Int
        let startTime: String
        let endTime: String
        let isEad: Bool
    }

    private static func parseCertificateGridTokens(lines: [String]) -> [GridToken] {
        let headerLine = lines.first { line in
            let norm = normalizeSearch(line)
            return norm.contains("segunda") && norm.contains("terca")
        }
        var dayCenters: [Int: Double] = [:]
        if let headerLine {
            let normHeader = headerLine.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "pt_BR"))
            let days: [(Int, String)] = [(1, "segunda"), (2, "terca"), (3, "quarta"), (4, "quinta"), (5, "sexta"), (6, "sabado")]
            for (day, name) in days {
                if let range = normHeader.range(of: name) {
                    let offset = normHeader.distance(from: normHeader.startIndex, to: range.lowerBound)
                    dayCenters[day] = Double(offset) + Double(name.count) * 0.5
                }
            }
        }
        let hasAligned = headerLine != nil && (headerLine!.contains("  ") || headerLine!.contains("\t"))

        var tokens: [GridToken] = []
        let rowPattern = #"^\s*(\d{2}:\d{2})\s*-\s*(\d{2}:\d{2})\b"#
        let tokenPattern = #"([A-Z]{3}\d{3}[A-Z]?)\s*-\s*(\d{1,3})\s*([TP])"#

        for line in lines {
            guard let timeGroups = extractRegexGroups(rowPattern, in: line), timeGroups.count >= 3 else { continue }
            let start = timeGroups[1]
            let end = timeGroups[2]
            guard let regex = try? NSRegularExpression(pattern: tokenPattern, options: .caseInsensitive) else { continue }
            let matches = regex.matches(in: line, range: NSRange(line.startIndex..., in: line))
            for (idx, match) in matches.enumerated() {
                guard let r1 = Range(match.range(at: 1), in: line),
                      let r2 = Range(match.range(at: 2), in: line),
                      let r3 = Range(match.range(at: 3), in: line) else { continue }
                let day: Int
                if hasAligned && !dayCenters.isEmpty {
                    let center = Double(match.range.location) + Double(match.range.length) * 0.5
                    day = dayCenters.min(by: { abs($0.value - center) < abs($1.value - center) })?.key ?? min(6, idx + 1)
                } else {
                    day = min(6, idx + 1)
                }
                let localEnd = idx + 1 < matches.count ? matches[idx + 1].range.location : line.utf16.count
                let slice = (line as NSString).substring(with: NSRange(location: match.range.location, length: max(0, localEnd - match.range.location)))
                tokens.append(
                    GridToken(
                        courseCode: String(line[r1]).uppercased(),
                        turma: String(line[r2]),
                        kind: String(line[r3]).uppercased(),
                        dayOfWeek: day,
                        startTime: start,
                        endTime: end,
                        isEad: isEadIndicator(slice)
                    )
                )
            }
        }
        return tokens
    }

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

    private static func departmentIdFromCode(_ code: String) -> String {
        let prefix = String(code.uppercased().prefix(while: \.isLetter))
        if ["BCC", "CSI"].contains(prefix) { return "comp" }
        if ["FIS", "CEA"].contains(prefix) { return "phys" }
        return "math"
    }

    private static func resolveCampus(from raw: String) -> (id: String, city: String) {
        let norm = normalizeSearch(raw)
        if norm.contains("mariana") || norm.contains("ichs") || norm.contains("icsa") {
            return ("mar_ichs", "Mariana")
        }
        if norm.contains("monlevade") || norm.contains("icea") {
            return ("jm_icea", "João Monlevade")
        }
        return ("op_cruzeiro", "Ouro Preto")
    }

    private static func normalizeBuildingName(_ raw: String) -> String {
        let upper = raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if upper.contains("ICEB") { return "ICEB" }
        if upper.contains("BLOCO DE SALAS") { return "Bloco de Salas" }
        if upper.contains("ESCOLA DE MINAS") || upper == "EM" { return "Escola de Minas" }
        return raw.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func parseLongDate(after prefixPattern: String, in text: String) -> String? {
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

    private static func normalizeSearch(_ text: String) -> String {
        text.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "pt_BR"))
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func ufopTitleCase(_ raw: String) -> String {
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

    private static func firstMatch(_ pattern: String, in text: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let range = Range(match.range, in: text) else {
            return nil
        }
        return String(text[range])
    }

    private static func firstCapture(_ pattern: String, in text: String) -> String? {
        extractRegexGroups(pattern, in: text)?.dropFirst().first
    }

    private static func extractCapture(_ pattern: String, in text: String, groups: [Int]) -> [String]? {
        guard let all = extractRegexGroups(pattern, in: text) else { return nil }
        var out: [String] = []
        for g in groups where g < all.count { out.append(all[g]) }
        return out.count == groups.count ? out : nil
    }

    private static func extractRegexGroups(_ pattern: String, in text: String) -> [String]? {
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

private extension ParsedCertificateCourse {
    init(code: String, turma: String, name: String, building: String, isEad: Bool, departmentId: String, workloadHours: Int, professor: String, slots: [ParsedCertificateSlot]) {
        self.code = code
        self.turma = turma
        self.name = name
        self.building = building
        self.isEad = isEad
        self.departmentId = departmentId
        self.workloadHours = workloadHours
        self.professor = professor
        self.slots = slots
    }
}
