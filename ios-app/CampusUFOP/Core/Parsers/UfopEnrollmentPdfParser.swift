import Foundation

// MARK: - Parser de Atestado de Matrícula UFOP

public extension UfopPdfParsers {

    static func parseEnrollmentCertificate(
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
}
