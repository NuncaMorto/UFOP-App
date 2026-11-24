import Foundation

// MARK: - .ufopgrade (ufopgrade-v1) & Compact QR Codec (UFOPG1:)

public struct IncomingGradePreview: Identifiable, Equatable, Sendable {
    public let id: UUID
    public let friendName: String
    public let courseName: String
    public let semester: String
    public let campusId: String
    public let courseCount: Int
    public let slotCount: Int
    public let avatarBase64: String?
    public let rawJsonPayload: String
    public let sourceLabel: String

    public init(
        id: UUID = UUID(),
        friendName: String,
        courseName: String,
        semester: String,
        campusId: String,
        courseCount: Int,
        slotCount: Int,
        avatarBase64: String? = nil,
        rawJsonPayload: String,
        sourceLabel: String
    ) {
        self.id = id
        self.friendName = friendName
        self.courseName = courseName
        self.semester = semester
        self.campusId = campusId
        self.courseCount = courseCount
        self.slotCount = slotCount
        self.avatarBase64 = avatarBase64
        self.rawJsonPayload = rawJsonPayload
        self.sourceLabel = sourceLabel
    }
}

public enum UfopGradeCodecError: LocalizedError, Equatable, Sendable {
    case emptyPayload
    case payloadTooLarge
    case invalidEncoding
    case invalidFormat
    case missingCoursesAndSlots
    case compressionFailed
    case decompressionFailed

    public var errorDescription: String? {
        switch self {
        case .emptyPayload:
            return "O conteúdo da grade está vazio."
        case .payloadTooLarge:
            return "O arquivo ou código excede o limite máximo de 5 MB."
        case .invalidEncoding:
            return "Não foi possível decodificar o texto UTF-8 do arquivo."
        case .invalidFormat:
            return "Formato inválido. Selecione um arquivo .ufopgrade, JSON ou QR Code UFOPG1."
        case .missingCoursesAndSlots:
            return "Nenhuma disciplina ou horário válido encontrado nesta grade."
        case .compressionFailed:
            return "Não foi possível compactar os dados da grade para QR Code."
        case .decompressionFailed:
            return "Não foi possível ler o QR Code compacto (UFOPG1)."
        }
    }
}

public struct ParsedScheduleCourse: Equatable, Sendable {
    public let courseId: String
    public let code: String
    public let name: String
    public let departmentId: String
    public let departmentName: String
    public let colorHex: String
    public let workloadHours: Int
    public let maxAbsences: Int
    public let professor: String
    public let buildingAndRoom: String
    public let shortLocation: String
}

public struct ParsedScheduleSlot: Equatable, Sendable {
    public let slotId: String
    public let courseId: String
    public let dayOfWeek: Int
    public let startTime: String
    public let endTime: String
    public let building: String
    public let room: String
    public let shortLocation: String
    public let classWeight: Int
}

public struct ParsedScheduleBundle: Equatable, Sendable {
    public let friendName: String
    public let courseName: String
    public let semester: String
    public let campusId: String
    public let avatarBase64: String?
    public let courses: [ParsedScheduleCourse]
    public let slots: [ParsedScheduleSlot]
    public let canonicalJson: String
}

public enum UfopGradeCodec {
    public static let formatV1 = "ufopgrade-v1"
    public static let qrPrefix = "UFOPG1:"
    public static let maxPayloadBytes = 5 * 1024 * 1024

    public static func readFileSafely(from url: URL) throws -> String {
        let accessed = url.startAccessingSecurityScopedResource()
        defer {
            if accessed {
                url.stopAccessingSecurityScopedResource()
            }
        }

        if let values = try? url.resourceValues(forKeys: [.fileSizeKey]),
           let fileSize = values.fileSize,
           fileSize > maxPayloadBytes {
            throw UfopGradeCodecError.payloadTooLarge
        }

        let data = try Data(contentsOf: url, options: .mappedIfSafe)
        guard data.count <= maxPayloadBytes else {
            throw UfopGradeCodecError.payloadTooLarge
        }
        guard !data.isEmpty else {
            throw UfopGradeCodecError.emptyPayload
        }
        guard let text = String(data: data, encoding: .utf8) else {
            throw UfopGradeCodecError.invalidEncoding
        }
        return text
    }

    public static func parseAndValidatePreview(
        _ rawText: String,
        sourceLabel: String = "Arquivo .ufopgrade"
    ) throws -> IncomingGradePreview {
        let bundle = try parseScheduleBundle(rawText)
        return IncomingGradePreview(
            friendName: bundle.friendName,
            courseName: bundle.courseName,
            semester: bundle.semester,
            campusId: bundle.campusId,
            courseCount: bundle.courses.count,
            slotCount: bundle.slots.count,
            avatarBase64: bundle.avatarBase64,
            rawJsonPayload: bundle.canonicalJson,
            sourceLabel: sourceLabel
        )
    }

    public static func encodeCompactQr(from bundleJson: String) throws -> String {
        let bundle = try parseScheduleBundle(bundleJson)

        let compactCourses: [[String: Any]] = bundle.courses.map { course in
            [
                "i": course.courseId,
                "k": course.code,
                "t": course.name,
                "p": course.departmentId
            ]
        }
        let compactSlots: [[String: Any]] = bundle.slots.map { slot in
            [
                "c": slot.courseId,
                "w": slot.dayOfWeek,
                "a": slot.startTime,
                "b": slot.endTime,
                "l": slot.shortLocation
            ]
        }

        let compactPayload: [String: Any] = [
            "v": 1,
            "n": bundle.friendName,
            "c": bundle.courseName,
            "s": bundle.semester,
            "m": bundle.campusId,
            "d": compactCourses,
            "h": compactSlots
        ]

        let jsonData: Data
        do {
            jsonData = try JSONSerialization.data(
                withJSONObject: compactPayload,
                options: [.withoutEscapingSlashes]
            )
        } catch {
            throw UfopGradeCodecError.compressionFailed
        }

        guard jsonData.count <= maxPayloadBytes else {
            throw UfopGradeCodecError.payloadTooLarge
        }

        let compressedData: Data
        do {
            compressedData = try (jsonData as NSData).compressed(using: .zlib) as Data
        } catch {
            throw UfopGradeCodecError.compressionFailed
        }

        let base64Url = compressedData.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")

        return qrPrefix + base64Url
    }

    public static func decodeCompactQr(_ qrText: String) throws -> String {
        let trimmed = qrText
            .replacingOccurrences(of: "\u{FEFF}", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw UfopGradeCodecError.emptyPayload
        }
        guard trimmed.utf8.count <= maxPayloadBytes else {
            throw UfopGradeCodecError.payloadTooLarge
        }
        guard trimmed.hasPrefix(qrPrefix) else {
            throw UfopGradeCodecError.invalidFormat
        }

        let encodedBody = String(trimmed.dropFirst(qrPrefix.count))
            .components(separatedBy: .whitespacesAndNewlines)
            .joined()
        guard !encodedBody.isEmpty else {
            throw UfopGradeCodecError.decompressionFailed
        }

        var base64 = encodedBody
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")

        let remainder = base64.count % 4
        if remainder == 1 {
            throw UfopGradeCodecError.decompressionFailed
        } else if remainder > 0 {
            base64 += String(repeating: "=", count: 4 - remainder)
        }

        guard let compressedData = Data(base64Encoded: base64),
              compressedData.count <= maxPayloadBytes else {
            throw UfopGradeCodecError.decompressionFailed
        }

        let decompressedData: Data
        do {
            decompressedData = try (compressedData as NSData).decompressed(using: .zlib) as Data
        } catch {
            throw UfopGradeCodecError.decompressionFailed
        }

        guard decompressedData.count <= maxPayloadBytes else {
            throw UfopGradeCodecError.payloadTooLarge
        }

        guard let root = (try? JSONSerialization.jsonObject(with: decompressedData)) as? [String: Any] else {
            throw UfopGradeCodecError.decompressionFailed
        }

        let version = intValue(root["v"]) ?? 1
        guard version == 1 else {
            throw UfopGradeCodecError.decompressionFailed
        }

        let friendName = nonEmptyString(root["n"]) ?? "Colega UFOP"
        let courseName = stringValue(root["c"])
        let semester = nonEmptyString(root["s"]) ?? "2026.2"
        let campusId = nonEmptyString(root["m"]) ?? "op_cruzeiro"

        let rawCourses = root["d"] as? [[String: Any]] ?? []
        var parsedCourses: [ParsedScheduleCourse] = []
        for item in rawCourses {
            let courseId = nonEmptyString(item["i"]) ?? nonEmptyString(item["k"]) ?? ""
            let name = nonEmptyString(item["t"]) ?? courseId
            guard !courseId.isEmpty, !name.isEmpty else { continue }
            let rawCode = nonEmptyString(item["k"]) ?? ""
            let code = deriveCourseCode(explicit: rawCode, courseId: courseId, courseName: name)
            let rawDept = stringValue(item["p"])
            let deptId = normalizeDepartmentId(rawDepartment: rawDept, colorHex: "", code: code)
            let deptMeta = departmentDisplayInfo(for: deptId)
            parsedCourses.append(
                ParsedScheduleCourse(
                    courseId: courseId,
                    code: code,
                    name: name,
                    departmentId: deptId,
                    departmentName: deptMeta.label,
                    colorHex: deptMeta.colorHex,
                    workloadHours: 60,
                    maxAbsences: 18,
                    professor: "",
                    buildingAndRoom: "Campus UFOP",
                    shortLocation: "UFOP"
                )
            )
        }

        let rawSlots = root["h"] as? [[String: Any]] ?? []
        var parsedSlots: [ParsedScheduleSlot] = []
        for (idx, item) in rawSlots.enumerated() {
            let courseId = nonEmptyString(item["c"]) ?? ""
            guard !courseId.isEmpty else { continue }
            let day = clampWeekday(intValue(item["w"]) ?? 1)
            let start = normalizeTime(nonEmptyString(item["a"]) ?? "08:00")
            let end = normalizeTime(nonEmptyString(item["b"]) ?? "09:40")
            let shortLoc = nonEmptyString(item["l"]) ?? "UFOP"
            let slug = CampusStore.weekdayMetadata(for: day).slug
            let slotId = "qr_\(slug)_\(start.replacingOccurrences(of: ":", with: ""))_\(idx + 1)"
            parsedSlots.append(
                ParsedScheduleSlot(
                    slotId: slotId,
                    courseId: courseId,
                    dayOfWeek: day,
                    startTime: start,
                    endTime: end,
                    building: shortLoc,
                    room: "",
                    shortLocation: shortLoc,
                    classWeight: 1
                )
            )
        }

        guard !parsedCourses.isEmpty || !parsedSlots.isEmpty else {
            throw UfopGradeCodecError.missingCoursesAndSlots
        }

        return try buildCanonicalJson(
            friendName: friendName,
            courseName: courseName,
            semester: semester,
            campusId: campusId,
            avatarBase64: nil,
            courses: parsedCourses,
            slots: parsedSlots
        )
    }

    public static func sanitizedFilenameToken(_ raw: String) -> String {
        let folded = raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: .diacriticInsensitive, locale: .current)
            .lowercased()

        var tokens: [Character] = []
        var lastWasUnderscore = false
        for scalar in folded.unicodeScalars {
            if (scalar.value >= 97 && scalar.value <= 122) || (scalar.value >= 48 && scalar.value <= 57) {
                tokens.append(Character(scalar))
                lastWasUnderscore = false
            } else if !lastWasUnderscore && !tokens.isEmpty {
                tokens.append("_")
                lastWasUnderscore = true
            }
        }
        while tokens.last == "_" {
            tokens.removeLast()
        }
        let result = String(tokens)
        return result.isEmpty ? "estudante_ufop" : result
    }

    public static func suggestedFilename(forStudentName name: String) -> String {
        "grade_\(sanitizedFilenameToken(name)).ufopgrade"
    }

    public static func parseScheduleBundle(_ rawText: String) throws -> ParsedScheduleBundle {
        guard rawText.utf8.count <= maxPayloadBytes else {
            throw UfopGradeCodecError.payloadTooLarge
        }
        let trimmed = rawText
            .replacingOccurrences(of: "\u{FEFF}", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw UfopGradeCodecError.emptyPayload
        }

        let jsonText: String
        if trimmed.hasPrefix(qrPrefix) {
            jsonText = try decodeCompactQr(trimmed)
        } else {
            jsonText = trimmed
        }

        guard let jsonData = jsonText.data(using: .utf8),
              jsonData.count <= maxPayloadBytes,
              let root = (try? JSONSerialization.jsonObject(with: jsonData)) as? [String: Any] else {
            throw UfopGradeCodecError.invalidFormat
        }

        if let declaredFormat = nonEmptyString(root["format"]),
           declaredFormat != formatV1 {
            throw UfopGradeCodecError.invalidFormat
        }

        let friendName = nonEmptyString(root["friendName"])
            ?? nonEmptyString(root["friend_name"])
            ?? nonEmptyString(root["studentName"])
            ?? nonEmptyString(root["name"])
            ?? "Colega UFOP"

        let courseName = nonEmptyString(root["courseName"])
            ?? nonEmptyString(root["course_name"])
            ?? ""

        let semester = nonEmptyString(root["semester"]) ?? "2026.2"
        let campusId = nonEmptyString(root["campusId"])
            ?? nonEmptyString(root["activeCampusId"])
            ?? nonEmptyString(root["campus_id"])
            ?? "op_cruzeiro"

        let avatarBase64 = nonEmptyString(root["avatarBase64"])
            ?? nonEmptyString(root["avatar_base64"])

        var parsedCourses: [ParsedScheduleCourse] = []
        var parsedSlots: [ParsedScheduleSlot] = []

        if let coursesArray = root["courses"] as? [[String: Any]] {
            for item in coursesArray {
                let rawId = nonEmptyString(item["courseId"])
                    ?? nonEmptyString(item["course_id"])
                    ?? nonEmptyString(item["id"])
                    ?? nonEmptyString(item["code"])
                    ?? ""
                let rawName = nonEmptyString(item["name"])
                    ?? nonEmptyString(item["title"])
                    ?? nonEmptyString(item["code"])
                    ?? rawId
                guard !rawId.isEmpty, !rawName.isEmpty else { continue }

                let code = deriveCourseCode(
                    explicit: nonEmptyString(item["code"]) ?? "",
                    courseId: rawId,
                    courseName: rawName
                )
                let rawDept = nonEmptyString(item["departmentId"])
                    ?? nonEmptyString(item["department_id"])
                    ?? nonEmptyString(item["department"])
                    ?? nonEmptyString(item["departmentName"])
                    ?? nonEmptyString(item["department_name"])
                    ?? ""
                let colorHex = nonEmptyString(item["colorHex"])
                    ?? nonEmptyString(item["color_hex"])
                    ?? ""
                let deptId = normalizeDepartmentId(rawDepartment: rawDept, colorHex: colorHex, code: code)
                let deptMeta = departmentDisplayInfo(for: deptId)
                let workload = max(1, intValue(item["workloadHours"]) ?? intValue(item["workload_hours"]) ?? 60)
                let maxAbsences = max(0, intValue(item["maxAbsences"]) ?? intValue(item["max_absences"]) ?? CampusStore.calculateMaxAbsences(workloadHours: workload))
                let professor = stringValue(item["professor"])
                let buildingAndRoom = nonEmptyString(item["buildingAndRoom"])
                    ?? nonEmptyString(item["building_and_room"])
                    ?? nonEmptyString(item["location"])
                    ?? "Campus UFOP"
                let shortLocation = nonEmptyString(item["shortLocation"])
                    ?? nonEmptyString(item["short_location"])
                    ?? CampusStore.buildShortLocation(building: buildingAndRoom, room: "")

                parsedCourses.append(
                    ParsedScheduleCourse(
                        courseId: rawId,
                        code: code,
                        name: rawName,
                        departmentId: deptId,
                        departmentName: nonEmptyString(item["departmentName"]) ?? nonEmptyString(item["department_name"]) ?? deptMeta.label,
                        colorHex: colorHex.isEmpty ? deptMeta.colorHex : colorHex,
                        workloadHours: workload,
                        maxAbsences: maxAbsences,
                        professor: professor,
                        buildingAndRoom: buildingAndRoom,
                        shortLocation: shortLocation
                    )
                )
            }

            let rawSlots = (root["slots"] as? [[String: Any]])
                ?? (root["schedule_slots"] as? [[String: Any]])
                ?? []
            parsedSlots = parseSlotsArray(rawSlots, defaultPrefix: "fs")
        } else if let disciplinesArray = root["disciplines"] as? [[String: Any]] {
            for item in disciplinesArray {
                let rawId = nonEmptyString(item["id"])
                    ?? nonEmptyString(item["courseId"])
                    ?? nonEmptyString(item["code"])
                    ?? ""
                let rawName = nonEmptyString(item["name"])
                    ?? nonEmptyString(item["code"])
                    ?? rawId
                guard !rawId.isEmpty, !rawName.isEmpty else { continue }

                let code = deriveCourseCode(
                    explicit: nonEmptyString(item["code"]) ?? "",
                    courseId: rawId,
                    courseName: rawName
                )
                let rawColorKey = nonEmptyString(item["colorKey"]) ?? ""
                let rawDept = nonEmptyString(item["department"]) ?? ""
                let mappedColorHex = colorHexFromColorKeyRaw(rawColorKey)
                let deptId = normalizeDepartmentId(rawDepartment: rawDept, colorHex: mappedColorHex, code: code)
                let deptMeta = departmentDisplayInfo(for: deptId)
                let workload = max(1, intValue(item["workloadHours"]) ?? 60)
                let maxAbsences = max(0, intValue(item["maxAbsences"]) ?? CampusStore.calculateMaxAbsences(workloadHours: workload))
                let professor = stringValue(item["professor"])
                let location = nonEmptyString(item["location"]) ?? "Campus UFOP"

                parsedCourses.append(
                    ParsedScheduleCourse(
                        courseId: rawId,
                        code: code,
                        name: rawName,
                        departmentId: deptId,
                        departmentName: rawDept.isEmpty ? deptMeta.label : rawDept,
                        colorHex: mappedColorHex.isEmpty ? deptMeta.colorHex : mappedColorHex,
                        workloadHours: workload,
                        maxAbsences: maxAbsences,
                        professor: professor,
                        buildingAndRoom: location,
                        shortLocation: CampusStore.buildShortLocation(building: location, room: "")
                    )
                )
            }

            if let courseSlots = root["courseScheduleSlots"] as? [[String: Any]], !courseSlots.isEmpty {
                parsedSlots = parseSlotsArray(courseSlots, defaultPrefix: "ios")
            } else if let timetableSlots = root["timetableSlots"] as? [[String: Any]] {
                parsedSlots = parseLegacyTimetableSlots(timetableSlots)
            }
        } else {
            let fallbackSlots = (root["slots"] as? [[String: Any]])
                ?? (root["schedule_slots"] as? [[String: Any]])
                ?? (root["courseScheduleSlots"] as? [[String: Any]])
                ?? []
            parsedSlots = parseSlotsArray(fallbackSlots, defaultPrefix: "fs")
            if parsedSlots.isEmpty, let timetableSlots = root["timetableSlots"] as? [[String: Any]] {
                parsedSlots = parseLegacyTimetableSlots(timetableSlots)
            }
        }

        guard !parsedCourses.isEmpty || !parsedSlots.isEmpty else {
            throw UfopGradeCodecError.missingCoursesAndSlots
        }

        let canonicalJson = try buildCanonicalJson(
            friendName: friendName,
            courseName: courseName,
            semester: semester,
            campusId: campusId,
            avatarBase64: avatarBase64,
            courses: parsedCourses,
            slots: parsedSlots
        )

        return ParsedScheduleBundle(
            friendName: friendName,
            courseName: courseName,
            semester: semester,
            campusId: campusId,
            avatarBase64: avatarBase64,
            courses: parsedCourses,
            slots: parsedSlots,
            canonicalJson: canonicalJson
        )
    }

    // MARK: - Private Helpers

    private static func buildCanonicalJson(
        friendName: String,
        courseName: String,
        semester: String,
        campusId: String,
        avatarBase64: String?,
        courses: [ParsedScheduleCourse],
        slots: [ParsedScheduleSlot]
    ) throws -> String {
        let coursesJson: [[String: Any]] = courses.map { course in
            [
                "courseId": course.courseId,
                "code": course.code,
                "semester": semester,
                "name": course.name,
                "departmentId": course.departmentId,
                "departmentName": course.departmentName,
                "colorHex": course.colorHex,
                "workloadHours": course.workloadHours,
                "maxAbsences": course.maxAbsences,
                "professor": course.professor,
                "buildingAndRoom": course.buildingAndRoom,
                "shortLocation": course.shortLocation
            ]
        }

        let slotsJson: [[String: Any]] = slots.map { slot in
            [
                "slotId": slot.slotId,
                "courseId": slot.courseId,
                "dayOfWeek": slot.dayOfWeek,
                "startTime": slot.startTime,
                "endTime": slot.endTime,
                "building": slot.building,
                "room": slot.room,
                "shortLocation": slot.shortLocation,
                "classWeight": slot.classWeight
            ]
        }

        var root: [String: Any] = [
            "format": formatV1,
            "friendName": friendName,
            "courseName": courseName,
            "semester": semester,
            "campusId": campusId,
            "courses": coursesJson,
            "slots": slotsJson
        ]
        if let avatarBase64, !avatarBase64.isEmpty {
            root["avatarBase64"] = avatarBase64
        }

        let data = try JSONSerialization.data(
            withJSONObject: root,
            options: [.sortedKeys, .withoutEscapingSlashes]
        )
        guard let str = String(data: data, encoding: .utf8) else {
            throw UfopGradeCodecError.invalidEncoding
        }
        return str
    }

    private static func parseSlotsArray(_ items: [[String: Any]], defaultPrefix: String) -> [ParsedScheduleSlot] {
        var result: [ParsedScheduleSlot] = []
        for (idx, item) in items.enumerated() {
            let courseId = nonEmptyString(item["courseId"])
                ?? nonEmptyString(item["course_id"])
                ?? nonEmptyString(item["disciplineId"])
                ?? ""
            guard !courseId.isEmpty else { continue }

            let slotId = nonEmptyString(item["slotId"])
                ?? nonEmptyString(item["slot_id"])
                ?? nonEmptyString(item["id"])
                ?? "\(defaultPrefix)_\(idx + 1)"

            let day = clampWeekday(
                intValue(item["dayOfWeek"])
                    ?? intValue(item["day_of_week"])
                    ?? weekdayInt(fromShort: nonEmptyString(item["dayLabel"]) ?? nonEmptyString(item["weekdayShort"]) ?? "")
                    ?? 1
            )
            let start = normalizeTime(
                nonEmptyString(item["startTime"])
                    ?? nonEmptyString(item["start_time"])
                    ?? "08:00"
            )
            let end = normalizeTime(
                nonEmptyString(item["endTime"])
                    ?? nonEmptyString(item["end_time"])
                    ?? "09:40"
            )
            let building = nonEmptyString(item["building"]) ?? "Campus UFOP"
            let room = stringValue(item["room"])
            let shortLoc = nonEmptyString(item["shortLocation"])
                ?? nonEmptyString(item["short_location"])
                ?? nonEmptyString(item["roomLabel"])
                ?? CampusStore.buildShortLocation(building: building, room: room)
            let weight = max(1, intValue(item["classWeight"]) ?? intValue(item["class_weight"]) ?? 1)

            result.append(
                ParsedScheduleSlot(
                    slotId: slotId,
                    courseId: courseId,
                    dayOfWeek: day,
                    startTime: start,
                    endTime: end,
                    building: building,
                    room: room,
                    shortLocation: shortLoc.isEmpty ? "UFOP" : shortLoc,
                    classWeight: weight
                )
            )
        }
        return result
    }

    private static func parseLegacyTimetableSlots(_ items: [[String: Any]]) -> [ParsedScheduleSlot] {
        var result: [ParsedScheduleSlot] = []
        for (idx, item) in items.enumerated() {
            let courseId = nonEmptyString(item["disciplineId"])
                ?? nonEmptyString(item["courseId"])
                ?? ""
            guard !courseId.isEmpty else { continue }

            let slotId = nonEmptyString(item["id"]) ?? "tt_\(idx + 1)"
            let weekdayShort = nonEmptyString(item["weekdayShort"]) ?? "SEG"
            let day = weekdayInt(fromShort: weekdayShort) ?? 1
            let timeRange = nonEmptyString(item["timeRange"]) ?? "08:00–09:40"
            let parts = timeRange
                .replacingOccurrences(of: "–", with: "-")
                .split(separator: "-")
                .map { String($0).trimmingCharacters(in: .whitespacesAndNewlines) }
            let start = normalizeTime(parts.first ?? "08:00")
            let end = normalizeTime(parts.count > 1 ? parts[1] : "09:40")
            let roomLabel = nonEmptyString(item["roomLabel"]) ?? "UFOP"

            result.append(
                ParsedScheduleSlot(
                    slotId: slotId,
                    courseId: courseId,
                    dayOfWeek: day,
                    startTime: start,
                    endTime: end,
                    building: roomLabel,
                    room: "",
                    shortLocation: roomLabel,
                    classWeight: 1
                )
            )
        }
        return result
    }

    private static func normalizeDepartmentId(rawDepartment: String, colorHex: String, code: String) -> String {
        let norm = rawDepartment
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: .diacriticInsensitive, locale: .current)
            .lowercased()
        let hex = colorHex.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()

        if norm.contains("algebra") || norm.contains("alg") || norm == "decat" || hex == "#2E7D32" {
            return "algebra"
        }
        if norm.contains("comp") || norm.contains("decom") || norm.contains("decsi") || norm.contains("bcc") || norm.contains("csi") || hex == "#6E56CF" {
            return "comp"
        }
        if norm.contains("phys") || norm.contains("defis") || norm.contains("fis") || hex == "#8D5B16" {
            return "phys"
        }
        if norm.contains("math") || norm.contains("demat") || norm.contains("mat") || norm.contains("mtm") || hex == "#3B68FF" {
            return "math"
        }
        if !norm.isEmpty {
            return norm
        }

        let upperCode = code.uppercased()
        if upperCode.hasPrefix("BCC") || upperCode.hasPrefix("CSI") {
            return "comp"
        }
        if upperCode.hasPrefix("FIS") {
            return "phys"
        }
        return "math"
    }

    private static func departmentDisplayInfo(for departmentId: String) -> (label: String, colorHex: String) {
        let key = CampusStore.departmentColorKey(for: departmentId)
        return (key.departmentLabel, key.hexString)
    }

    private static func colorHexFromColorKeyRaw(_ raw: String) -> String {
        switch raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "COMP_PURPLE": return DepartmentColorKey.compPurple.hexString
        case "ALGEBRA_GREEN": return DepartmentColorKey.algebraGreen.hexString
        case "PHYSICS_AMBER": return DepartmentColorKey.physicsAmber.hexString
        case "MATH_BLUE": return DepartmentColorKey.mathBlue.hexString
        default: return ""
        }
    }

    private static func deriveCourseCode(explicit: String, courseId: String, courseName: String) -> String {
        let cleanExplicit = explicit.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if !cleanExplicit.isEmpty {
            return cleanExplicit
        }
        for candidate in [courseName, courseId] {
            if let range = candidate.range(of: #"[A-Za-z]{2,5}[0-9]{2,4}"#, options: .regularExpression) {
                return String(candidate[range]).uppercased()
            }
        }
        let cleaned = courseId.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return cleaned.isEmpty ? "UFOP" : String(cleaned.prefix(8))
    }

    private static func weekdayInt(fromShort raw: String) -> Int? {
        let norm = raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: .diacriticInsensitive, locale: .current)
            .uppercased()
        switch norm {
        case "SEG", "SEGUNDA": return 1
        case "TER", "TERCA": return 2
        case "QUA", "QUARTA": return 3
        case "QUI", "QUINTA": return 4
        case "SEX", "SEXTA": return 5
        case "SAB", "SABADO": return 6
        case "DOM", "DOMINGO": return 7
        default: return nil
        }
    }

    private static func clampWeekday(_ day: Int) -> Int {
        min(7, max(1, day))
    }

    private static func normalizeTime(_ raw: String) -> String {
        let normalized = CampusStore.normalizeTimeString(raw)
        return normalized.isEmpty ? "08:00" : normalized
    }

    private static func nonEmptyString(_ raw: Any?) -> String? {
        guard let str = raw as? String else { return nil }
        let trimmed = str.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private static func stringValue(_ raw: Any?) -> String {
        (raw as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    private static func intValue(_ raw: Any?) -> Int? {
        if let intVal = raw as? Int {
            return intVal
        }
        if let num = raw as? NSNumber {
            return num.intValue
        }
        if let str = raw as? String {
            return Int(str.trimmingCharacters(in: .whitespacesAndNewlines))
        }
        return nil
    }
}
