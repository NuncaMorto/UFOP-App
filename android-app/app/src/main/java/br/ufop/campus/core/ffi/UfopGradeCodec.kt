package br.ufop.campus.core.ffi

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

data class IncomingGradePreview(
    val friendName: String,
    val courseName: String,
    val semester: String,
    val courseCount: Int,
    val slotCount: Int,
    val avatarBase64: String?,
    val rawJsonPayload: String,
    val sourceLabel: String
)

object UfopGradeCodec {
    const val FORMAT_V1 = "ufopgrade-v1"
    const val QR_PREFIX = "UFOPG1:"
    const val MAX_PAYLOAD_BYTES = 5 * 1024 * 1024

    private const val DEFAULT_SOURCE_LABEL = "Arquivo .ufopgrade"
    private const val DEFAULT_FRIEND_NAME = "Amigo UFOP"
    private const val DEFAULT_SEMESTER = "2026.2"
    private const val DEFAULT_CAMPUS_ID = "op_cruzeiro"
    private const val DEFAULT_START_TIME = "08:00"
    private const val DEFAULT_END_TIME = "09:40"
    private const val DEFAULT_SHORT_LOCATION = "UFOP"

    private data class NormalizedCourse(
        val courseId: String,
        val code: String,
        val name: String,
        val departmentId: String
    )

    private data class NormalizedSlot(
        val slotId: String,
        val courseId: String,
        val dayOfWeek: Int,
        val startTime: String,
        val endTime: String,
        val shortLocation: String
    )

    private data class NormalizedBundle(
        val friendName: String,
        val courseName: String,
        val semester: String,
        val campusId: String,
        val avatarBase64: String?,
        val courses: List<NormalizedCourse>,
        val slots: List<NormalizedSlot>
    )

    fun parseAndValidatePreview(
        rawText: String,
        sourceLabel: String = DEFAULT_SOURCE_LABEL
    ): Result<IncomingGradePreview> = runCatching {
        require(rawText.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) {
            "O arquivo ou código excede o limite máximo de 5 MB."
        }
        val trimmed = rawText.removePrefix("\uFEFF").trim()
        require(trimmed.isNotEmpty()) {
            "O conteúdo da grade está vazio."
        }

        val isQrPayload = trimmed.startsWith(QR_PREFIX)
        val effectiveSourceLabel = when {
            isQrPayload && (sourceLabel == DEFAULT_SOURCE_LABEL || sourceLabel.isBlank()) -> "QR Code"
            else -> sourceLabel.trim().ifBlank { DEFAULT_SOURCE_LABEL }
        }

        val normalizedJson = if (isQrPayload) {
            decodeCompactQr(trimmed).getOrThrow()
        } else {
            val root = try {
                JSONObject(trimmed)
            } catch (_: Throwable) {
                throw IllegalArgumentException(
                    "JSON inválido. Use um arquivo .ufopgrade ou QR Code válido do Vida Acadêmica."
                )
            }
            val bundle = parseJsonToNormalizedBundle(root)
            toCanonicalJsonString(bundle)
        }

        val canonicalRoot = JSONObject(normalizedJson)
        val canonicalBundle = parseJsonToNormalizedBundle(canonicalRoot)

        IncomingGradePreview(
            friendName = canonicalBundle.friendName,
            courseName = canonicalBundle.courseName,
            semester = canonicalBundle.semester,
            courseCount = canonicalBundle.courses.size,
            slotCount = canonicalBundle.slots.size,
            avatarBase64 = canonicalBundle.avatarBase64,
            rawJsonPayload = normalizedJson,
            sourceLabel = effectiveSourceLabel
        )
    }

    fun encodeCompactQr(bundleJson: String): String {
        require(bundleJson.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) {
            "O conteúdo da grade excede o limite máximo de 5 MB."
        }
        val trimmed = bundleJson.removePrefix("\uFEFF").trim()
        require(trimmed.isNotEmpty()) {
            "JSON da grade não pode ser vazio."
        }

        val root = try {
            JSONObject(trimmed)
        } catch (_: Throwable) {
            throw IllegalArgumentException("JSON inválido para geração do QR Code.")
        }
        val bundle = parseJsonToNormalizedBundle(root)

        val compactCourses = JSONArray().apply {
            bundle.courses.forEach { course ->
                put(
                    JSONObject().apply {
                        put("i", course.courseId)
                        put("k", course.code)
                        put("t", course.name)
                        put("p", course.departmentId)
                    }
                )
            }
        }
        val compactSlots = JSONArray().apply {
            bundle.slots.forEach { slot ->
                put(
                    JSONObject().apply {
                        put("c", slot.courseId)
                        put("w", slot.dayOfWeek)
                        put("a", slot.startTime)
                        put("b", slot.endTime)
                        put("l", slot.shortLocation)
                    }
                )
            }
        }
        val compactRoot = JSONObject().apply {
            put("v", 1)
            put("n", bundle.friendName)
            put("c", bundle.courseName)
            put("s", bundle.semester)
            put("m", bundle.campusId)
            put("d", compactCourses)
            put("h", compactSlots)
        }

        val compactBytes = compactRoot.toString().toByteArray(Charsets.UTF_8)
        val compressedBytes = deflateRaw(compactBytes)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(compressedBytes)
        return "$QR_PREFIX$encoded"
    }

    fun decodeCompactQr(qrText: String): Result<String> = runCatching {
        require(qrText.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) {
            "Código QR excede o limite máximo de 5 MB."
        }
        val trimmed = qrText.removePrefix("\uFEFF").trim()
        require(trimmed.startsWith(QR_PREFIX)) {
            "Código QR inválido: prefixo $QR_PREFIX ausente."
        }
        val encoded = trimmed.substring(QR_PREFIX.length).trim()
        require(encoded.isNotEmpty()) {
            "Código QR vazio após prefixo $QR_PREFIX."
        }

        val compressedBytes = try {
            Base64.getUrlDecoder().decode(encoded)
        } catch (_: Throwable) {
            throw IllegalArgumentException("Código QR corrompido (Base64URL inválido).")
        }
        val decompressedBytes = inflateRawSafely(compressedBytes)
        val decompressedText = decompressedBytes.toString(Charsets.UTF_8)

        val compactRoot = try {
            JSONObject(decompressedText)
        } catch (_: Throwable) {
            throw IllegalArgumentException("Conteúdo interno do QR Code não é um JSON válido.")
        }
        val version = compactRoot.optInt("v", -1)
        require(version == 1) {
            "Versão do QR Code não suportada: $version."
        }

        val bundle = parseCompactQrToNormalizedBundle(compactRoot)
        toCanonicalJsonString(bundle)
    }

    private fun parseJsonToNormalizedBundle(root: JSONObject): NormalizedBundle {
        val format = optSafeString(root, "format").trim()
        require(format.isEmpty() || format == FORMAT_V1) {
            "Formato de grade não suportado: $format."
        }

        val friendName = optSafeString(root, "friendName")
            .ifBlank { optSafeString(root, "friend_name") }
            .ifBlank { optSafeString(root, "name") }
            .trim()
            .ifBlank { DEFAULT_FRIEND_NAME }
        val courseName = optSafeString(root, "courseName")
            .ifBlank { optSafeString(root, "course_name") }
            .trim()
        val semester = optSafeString(root, "semester")
            .trim()
            .ifBlank { DEFAULT_SEMESTER }
        val campusId = optSafeString(root, "campusId")
            .ifBlank { optSafeString(root, "campus_id") }
            .ifBlank { optSafeString(root, "activeCampusId") }
            .ifBlank { optSafeString(root, "active_campus_id") }
            .trim()
            .ifBlank { DEFAULT_CAMPUS_ID }
        val avatarBase64 = optSafeString(root, "avatarBase64")
            .ifBlank { optSafeString(root, "avatar_base64") }
            .trim()
            .takeIf { it.isNotEmpty() }

        val courses = parseCoursesArray(root)
        val slots = parseSlotsArray(root)

        require(courses.isNotEmpty() || slots.isNotEmpty()) {
            "Nenhuma disciplina ou horário encontrado na grade."
        }

        return NormalizedBundle(
            friendName = friendName,
            courseName = courseName,
            semester = semester,
            campusId = campusId,
            avatarBase64 = avatarBase64,
            courses = courses,
            slots = slots
        )
    }

    private fun parseCoursesArray(root: JSONObject): List<NormalizedCourse> {
        val coursesArr = root.optJSONArray("courses")
        if (coursesArr != null) {
            return buildList {
                for (i in 0 until coursesArr.length()) {
                    val obj = coursesArr.optJSONObject(i) ?: continue
                    val courseId = optSafeString(obj, "courseId")
                        .ifBlank { optSafeString(obj, "id") }
                        .trim()
                    val name = optSafeString(obj, "name").trim()
                    if (courseId.isEmpty() || name.isEmpty()) continue
                    val notes = optSafeString(obj, "notes").trim()
                    val code = deriveCourseCodeFallback(
                        explicitCode = optSafeString(obj, "code"),
                        notes = notes,
                        courseId = courseId,
                        courseName = name
                    )
                    val rawDept = optSafeString(obj, "departmentId")
                        .ifBlank { optSafeString(obj, "department_id") }
                        .ifBlank { optSafeString(obj, "department") }
                        .trim()
                    val departmentId = resolveNormalizedDepartmentId(
                        rawDepartment = rawDept,
                        courseCode = code
                    )
                    add(
                        NormalizedCourse(
                            courseId = courseId,
                            code = code,
                            name = name,
                            departmentId = departmentId
                        )
                    )
                }
            }
        }

        val disciplinesArr = root.optJSONArray("disciplines") ?: return emptyList()
        return buildList {
            for (i in 0 until disciplinesArr.length()) {
                val obj = disciplinesArr.optJSONObject(i) ?: continue
                val courseId = optSafeString(obj, "id")
                    .ifBlank { optSafeString(obj, "courseId") }
                    .trim()
                val name = optSafeString(obj, "name").trim()
                if (courseId.isEmpty() || name.isEmpty()) continue
                val code = deriveCourseCodeFallback(
                    explicitCode = optSafeString(obj, "code"),
                    notes = "",
                    courseId = courseId,
                    courseName = name
                )
                val rawDept = optSafeString(obj, "department")
                    .ifBlank { optSafeString(obj, "departmentId") }
                    .trim()
                val colorKey = optSafeString(obj, "colorKey").trim()
                val departmentId = resolveNormalizedDepartmentId(
                    rawDepartment = rawDept,
                    courseCode = code,
                    fallbackColorKey = colorKey
                )
                add(
                    NormalizedCourse(
                        courseId = courseId,
                        code = code,
                        name = name,
                        departmentId = departmentId
                    )
                )
            }
        }
    }

    private fun parseSlotsArray(root: JSONObject): List<NormalizedSlot> {
        val standardArr = root.optJSONArray("slots") ?: root.optJSONArray("schedule_slots")
        if (standardArr != null) {
            return parseStandardSlots(standardArr)
        }

        val iosCourseSlotsArr = root.optJSONArray("courseScheduleSlots")
        if (iosCourseSlotsArr != null) {
            val mappedCourseSlots = parseStandardSlots(iosCourseSlotsArr)
            if (mappedCourseSlots.isNotEmpty()) {
                return mappedCourseSlots
            }
        }

        val timetableArr = root.optJSONArray("timetableSlots") ?: return emptyList()
        return buildList {
            for (i in 0 until timetableArr.length()) {
                val obj = timetableArr.optJSONObject(i) ?: continue
                val courseId = optSafeString(obj, "disciplineId")
                    .ifBlank { optSafeString(obj, "courseId") }
                    .trim()
                if (courseId.isEmpty()) continue
                val slotId = optSafeString(obj, "id")
                    .ifBlank { optSafeString(obj, "slotId") }
                    .trim()
                    .ifBlank { "slot_${i + 1}" }
                val dayOfWeek = parseWeekdayShort(optSafeString(obj, "weekdayShort"))
                val (startTime, endTime) = parseTimeRange(optSafeString(obj, "timeRange"))
                val shortLocation = optSafeString(obj, "roomLabel")
                    .ifBlank { optSafeString(obj, "shortLocation") }
                    .trim()
                    .ifBlank { DEFAULT_SHORT_LOCATION }
                add(
                    NormalizedSlot(
                        slotId = slotId,
                        courseId = courseId,
                        dayOfWeek = dayOfWeek,
                        startTime = startTime,
                        endTime = endTime,
                        shortLocation = shortLocation
                    )
                )
            }
        }
    }

    private fun parseStandardSlots(slotsArr: JSONArray): List<NormalizedSlot> = buildList {
        for (i in 0 until slotsArr.length()) {
            val obj = slotsArr.optJSONObject(i) ?: continue
            val courseId = optSafeString(obj, "courseId")
                .ifBlank { optSafeString(obj, "course_id") }
                .trim()
            if (courseId.isEmpty()) continue
            val slotId = optSafeString(obj, "slotId")
                .ifBlank { optSafeString(obj, "slot_id") }
                .ifBlank { optSafeString(obj, "id") }
                .trim()
                .ifBlank { "slot_${i + 1}" }
            val dayOfWeek = readDayOfWeek(obj)
            val startTime = optSafeString(obj, "startTime")
                .ifBlank { optSafeString(obj, "start_time") }
                .trim()
                .ifBlank { DEFAULT_START_TIME }
            val endTime = optSafeString(obj, "endTime")
                .ifBlank { optSafeString(obj, "end_time") }
                .trim()
                .ifBlank { DEFAULT_END_TIME }
            val shortLocation = resolveSlotShortLocation(obj)
            add(
                NormalizedSlot(
                    slotId = slotId,
                    courseId = courseId,
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime,
                    shortLocation = shortLocation
                )
            )
        }
    }

    private fun parseCompactQrToNormalizedBundle(compactRoot: JSONObject): NormalizedBundle {
        val friendName = optSafeString(compactRoot, "n").trim().ifBlank { DEFAULT_FRIEND_NAME }
        val courseName = optSafeString(compactRoot, "c").trim()
        val semester = optSafeString(compactRoot, "s").trim().ifBlank { DEFAULT_SEMESTER }
        val campusId = optSafeString(compactRoot, "m").trim().ifBlank { DEFAULT_CAMPUS_ID }

        val courses = buildList {
            val coursesArr = compactRoot.optJSONArray("d")
            if (coursesArr != null) {
                for (i in 0 until coursesArr.length()) {
                    val obj = coursesArr.optJSONObject(i) ?: continue
                    val courseId = optSafeString(obj, "i").trim()
                    val rawCode = optSafeString(obj, "k").trim()
                    val name = optSafeString(obj, "t").trim().ifBlank { rawCode }
                    if (courseId.isEmpty() || name.isEmpty()) continue
                    val code = deriveCourseCodeFallback(
                        explicitCode = rawCode,
                        notes = "",
                        courseId = courseId,
                        courseName = name
                    )
                    val departmentId = resolveNormalizedDepartmentId(
                        rawDepartment = optSafeString(obj, "p").trim(),
                        courseCode = code
                    )
                    add(
                        NormalizedCourse(
                            courseId = courseId,
                            code = code,
                            name = name,
                            departmentId = departmentId
                        )
                    )
                }
            }
        }

        val slots = buildList {
            val slotsArr = compactRoot.optJSONArray("h")
            if (slotsArr != null) {
                for (i in 0 until slotsArr.length()) {
                    val obj = slotsArr.optJSONObject(i) ?: continue
                    val courseId = optSafeString(obj, "c").trim()
                    if (courseId.isEmpty()) continue
                    val dayOfWeek = obj.optInt("w", 1).coerceIn(1, 7)
                    val startTime = optSafeString(obj, "a").trim().ifBlank { DEFAULT_START_TIME }
                    val endTime = optSafeString(obj, "b").trim().ifBlank { DEFAULT_END_TIME }
                    val shortLocation = optSafeString(obj, "l").trim().ifBlank { DEFAULT_SHORT_LOCATION }
                    add(
                        NormalizedSlot(
                            slotId = "qr_${i + 1}",
                            courseId = courseId,
                            dayOfWeek = dayOfWeek,
                            startTime = startTime,
                            endTime = endTime,
                            shortLocation = shortLocation
                        )
                    )
                }
            }
        }

        require(courses.isNotEmpty() || slots.isNotEmpty()) {
            "Nenhuma disciplina ou horário encontrado no QR Code."
        }

        return NormalizedBundle(
            friendName = friendName,
            courseName = courseName,
            semester = semester,
            campusId = campusId,
            avatarBase64 = null,
            courses = courses,
            slots = slots
        )
    }

    private fun toCanonicalJsonString(bundle: NormalizedBundle): String {
        val coursesArr = JSONArray().apply {
            bundle.courses.forEach { course ->
                put(
                    JSONObject().apply {
                        put("courseId", course.courseId)
                        put("code", course.code)
                        put("name", course.name)
                        put("departmentId", course.departmentId)
                    }
                )
            }
        }
        val slotsArr = JSONArray().apply {
            bundle.slots.forEach { slot ->
                put(
                    JSONObject().apply {
                        put("slotId", slot.slotId)
                        put("courseId", slot.courseId)
                        put("dayOfWeek", slot.dayOfWeek)
                        put("startTime", slot.startTime)
                        put("endTime", slot.endTime)
                        put("shortLocation", slot.shortLocation)
                    }
                )
            }
        }
        return JSONObject().apply {
            put("format", FORMAT_V1)
            put("friendName", bundle.friendName)
            put("courseName", bundle.courseName)
            put("semester", bundle.semester)
            put("campusId", bundle.campusId)
            if (!bundle.avatarBase64.isNullOrBlank()) {
                put("avatarBase64", bundle.avatarBase64)
            }
            put("courses", coursesArr)
            put("slots", slotsArr)
        }.toString()
    }

    private fun resolveNormalizedDepartmentId(
        rawDepartment: String,
        courseCode: String,
        fallbackColorKey: String = ""
    ): String {
        for (candidate in listOf(rawDepartment, fallbackColorKey)) {
            val clean = candidate.trim()
            if (clean.isEmpty()) continue
            normalizeKnownDepartmentId(clean)?.let { return it }
            mapIosDepartmentOrColorKey(clean)?.let { mapped ->
                return resolveDepartmentOption(mapped).id
            }
        }
        if (courseCode.isNotBlank()) {
            return resolveDepartmentForCourseCode(courseCode).id
        }
        return "decom"
    }

    private fun mapIosDepartmentOrColorKey(raw: String): String? =
        when (stripDiacriticsLower(raw.trim())) {
            "comp_purple", "comp", "computacao", "ciencia da computacao" -> "decom"
            "math_blue", "math", "matematica" -> "demat"
            "physics_amber", "phys", "fisica" -> "defis"
            "algebra_green", "algebra", "algebra linear" -> "decat"
            else -> null
        }

    private fun readDayOfWeek(obj: JSONObject): Int {
        val raw = when {
            obj.has("dayOfWeek") && !obj.isNull("dayOfWeek") -> obj.optInt("dayOfWeek", 1)
            obj.has("day_of_week") && !obj.isNull("day_of_week") -> obj.optInt("day_of_week", 1)
            else -> 1
        }
        return raw.coerceIn(1, 7)
    }

    private fun resolveSlotShortLocation(obj: JSONObject): String {
        val explicit = optSafeString(obj, "shortLocation")
            .ifBlank { optSafeString(obj, "short_location") }
            .ifBlank { optSafeString(obj, "roomLabel") }
            .trim()
        if (explicit.isNotEmpty()) return explicit
        val building = optSafeString(obj, "building").trim()
        val room = optSafeString(obj, "room").trim()
        return buildShortLocation(building, room).ifBlank { DEFAULT_SHORT_LOCATION }
    }

    private fun parseWeekdayShort(raw: String): Int = when (stripDiacriticsLower(raw.trim())) {
        "seg", "segunda", "segunda-feira" -> 1
        "ter", "terca", "terca-feira" -> 2
        "qua", "quarta", "quarta-feira" -> 3
        "qui", "quinta", "quinta-feira" -> 4
        "sex", "sexta", "sexta-feira" -> 5
        "sab", "sabado" -> 6
        "dom", "domingo" -> 7
        else -> 1
    }

    private fun parseTimeRange(raw: String): Pair<String, String> {
        val parts = raw.split(Regex("""\s*[–—-]\s*"""), limit = 2)
        val start = parts.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_START_TIME
        val end = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_END_TIME
        return start to end
    }

    private fun optSafeString(obj: JSONObject, key: String, fallback: String = ""): String {
        if (!obj.has(key) || obj.isNull(key)) return fallback
        val raw = obj.optString(key, fallback)
        return if (raw.equals("null", ignoreCase = true)) fallback else raw
    }

    private fun deflateRaw(inputBytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        return try {
            deflater.setInput(inputBytes)
            deflater.finish()
            val out = ByteArrayOutputStream(inputBytes.size)
            val buffer = ByteArray(4096)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                if (count > 0) {
                    out.write(buffer, 0, count)
                } else if (deflater.needsInput()) {
                    break
                }
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflateRawSafely(compressed: ByteArray): ByteArray {
        require(compressed.isNotEmpty()) {
            "Payload QR comprimido está vazio."
        }
        val inflater = Inflater(true)
        return try {
            // Dummy trailing byte exigido pelo Javadoc de Inflater(nowrap=true) para streams RFC 1951 (compatível com Apple zlib).
            val inputWithDummy = ByteArray(compressed.size + 1)
            System.arraycopy(compressed, 0, inputWithDummy, 0, compressed.size)
            inflater.setInput(inputWithDummy)

            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var totalBytes = 0
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count > 0) {
                    totalBytes += count
                    if (totalBytes > MAX_PAYLOAD_BYTES) {
                        throw IllegalArgumentException(
                            "Payload QR descomprimido excede o limite máximo de 5 MB."
                        )
                    }
                    out.write(buffer, 0, count)
                } else if (inflater.needsDictionary() || inflater.needsInput() || !inflater.finished()) {
                    throw IllegalArgumentException("Payload QR comprimido inválido ou truncado.")
                }
            }
            out.toByteArray()
        } finally {
            inflater.end()
        }
    }
}
