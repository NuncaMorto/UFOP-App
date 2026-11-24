package br.ufop.campus.core.ffi

import br.ufop.campus.core.security.SecurePdfProcessor
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.math.roundToInt

private val COMBINING_MARKS_REGEX = Regex("\\p{M}+")
private val NON_ALNUM_REGEX = Regex("""[^a-z0-9]+""")

internal fun normalizeSearchText(value: String): String =
    java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replace(COMBINING_MARKS_REGEX, "")
        .lowercase()
        .trim()

internal fun parseDueDateParts(dueDateInput: String): Triple<String, String, String> {
    val trimmed = dueDateInput.trim()
    val monthNumToShort = mapOf(
        "01" to "JAN", "02" to "FEV", "03" to "MAR", "04" to "ABR",
        "05" to "MAI", "06" to "JUN", "07" to "JUL", "08" to "AGO",
        "09" to "SET", "10" to "OUT", "11" to "NOV", "12" to "DEZ"
    )
    val monthShortToNum = monthNumToShort.entries.associate { (k, v) -> v to k }

    // Formato ISO: YYYY-MM-DD
    val isoParts = trimmed.split("-")
    if (isoParts.size == 3 && isoParts[0].length == 4) {
        val monthNum = isoParts[1].padStart(2, '0')
        val dayNum = isoParts[2].take(2).padStart(2, '0')
        val monthShort = monthNumToShort[monthNum] ?: "NOV"
        return Triple("${isoParts[0]}-$monthNum-$dayNum", dayNum, monthShort)
    }

    // Formato DD/MM ou DD MMM
    val tokens = trimmed.replace("/", " ").split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.size >= 2) {
        val dayNum = tokens[0].filter { it.isDigit() }.padStart(2, '0').ifBlank { "28" }
        val second = tokens[1].uppercase()
        val monthShort = when {
            second in monthShortToNum -> second
            second.padStart(2, '0') in monthNumToShort -> monthNumToShort[second.padStart(2, '0')] ?: "NOV"
            second.startsWith("DEZ") -> "DEZ"
            second.startsWith("NOV") -> "NOV"
            second.startsWith("OUT") -> "OUT"
            else -> "NOV"
        }
        val monthNum = monthShortToNum[monthShort] ?: "11"
        return Triple("2026-$monthNum-$dayNum", dayNum, monthShort)
    }

    return Triple("2026-11-28", "28", "NOV")
}

internal val calendarKeepKeywordsRegex = Regex(
    """feriado|recesso|facultativo|ponto\s+facultativo|matricula|trancamento|exame|especial|especiais|inicio|termino|encerramento|prazo|ajuste|inscricao|colacao|semana|letivo|letiva|aula|aulas|suspensao|calendario|ferias|requerimento|solicitacao|divulgacao|sabado\s+letivo|natal|finados|tiradentes|carnaval|consciencia|republica|independencia|padroeira|pascoa|corpus""",
    RegexOption.IGNORE_CASE
)

internal val calendarBoilerplateRegex = Regex(
    """^\s*(?:resolucao|portaria|edital|pagina|pag\.|cep\b|cnpj\b|documento\s+assinado|codigo\s+verificador|publicado\s+em|aprovado\s+em|universidade\s+federal|pro-?reitoria|conselho)""",
    RegexOption.IGNORE_CASE
)


internal fun parseOfficialCalendarEvents(
    rawText: String,
    semester: String,
    defaultYear: Int
): List<AcademicCalendarEvent> {
    val lines = rawText.lines()
        .map { it.trim().replace(Regex("""\s+"""), " ") }
        .filter { it.isNotEmpty() }

    var sectionMonth: Int? = null
    var sectionYear: Int = defaultYear
    val monthHeaderRegex = Regex(
        """^(janeiro|fevereiro|mar[çc]o|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)(?:\s*(?:de|/|-)?\s*(\d{4}))?$""",
        RegexOption.IGNORE_CASE
    )

    data class CalendarCandidate(
        val isoDate: String,
        val title: String,
        val category: String,
        val matchesKeyword: Boolean
    )

    val candidates = mutableListOf<CalendarCandidate>()
    for (line in lines) {
        val headerMatch = monthHeaderRegex.matchEntire(line)
        if (headerMatch != null) {
            sectionMonth = parsePortugueseMonthNumber(headerMatch.groupValues[1])
            sectionYear = normalizeYear(headerMatch.groupValues[2].takeIf { it.isNotBlank() }, defaultYear)
            continue
        }

        val normLine = normalizeSearchText(line)
        if (calendarBoilerplateRegex.containsMatchIn(normLine) &&
            !calendarKeepKeywordsRegex.containsMatchIn(normLine)
        ) {
            continue
        }

        val parsed = extractDateFromSegment(line, sectionYear, fallbackMonth = sectionMonth) ?: continue
        if (parsed.isBareDate || parsed.remainingTitle.length < 3) continue

        val title = parsed.remainingTitle.let { if (it.length > 110) it.take(107).trimEnd() + "..." else it }
        val normTitle = normalizeSearchText(title)
        val isKeywordMatch = calendarKeepKeywordsRegex.containsMatchIn(normTitle)
        candidates.add(
            CalendarCandidate(
                isoDate = parsed.isoDate,
                title = title,
                category = classifyCalendarEventCategory(normTitle),
                matchesKeyword = isKeywordMatch
            )
        )
    }

    val filtered = if (candidates.any { it.matchesKeyword }) {
        candidates.filter { it.matchesKeyword }
    } else {
        candidates
    }

    val seen = mutableSetOf<Pair<String, String>>()
    val semesterSlug = semester.replace(".", "_")
    return filtered
        .filter { seen.add(it.isoDate to normalizeDedupKey(it.title)) }
        .sortedBy { it.isoDate }
        .mapIndexed { idx, item ->
            val (_, dayNum, monthShort) = parseDueDateParts(item.isoDate)
            AcademicCalendarEvent(
                id = "cal_${semesterSlug}_${idx + 1}_${item.isoDate.replace("-", "")}",
                semester = semester,
                eventDate = item.isoDate,
                dayNumber = dayNum,
                monthShort = monthShort,
                title = item.title,
                category = item.category
            )
        }
}

internal fun classifyCalendarEventCategory(normalizedTitle: String): String = when {
    Regex("""feriado|ponto\s+facultativo|carnaval|finados|tiradentes|natal|consciencia|republica|independencia|padroeira|pascoa|corpus""").containsMatchIn(normalizedTitle) -> "Feriado"
    Regex("""recesso|ferias|pausa""").containsMatchIn(normalizedTitle) -> "Recesso"
    Regex("""exame|prova|avaliac""").containsMatchIn(normalizedTitle) -> "Avaliação"
    Regex("""inicio|termino|encerramento|semestre|letivo|letiva""").containsMatchIn(normalizedTitle) -> "Semestre"
    Regex("""estudo|semana|mostra|seminario""").containsMatchIn(normalizedTitle) -> "Ensino"
    else -> "Acadêmico"
}

internal fun computeCalendarSignatureSha256(
    version: String,
    semester: String,
    publishedBy: String,
    publishedAt: String,
    events: List<AcademicCalendarEvent>
): String {
    val canonical = buildString {
        append("ufop-cal-v1|")
        append(version).append('|')
        append(semester).append('|')
        append(publishedBy).append('|')
        append(publishedAt).append('|')
        events.sortedWith(
            compareBy<AcademicCalendarEvent> { it.eventDate }
                .thenBy { it.id }
                .thenBy { it.title }
        ).forEach { ev ->
            append(ev.id).append(':')
            append(ev.semester).append(':')
            append(ev.eventDate).append(':')
            append(ev.dayNumber).append(':')
            append(ev.monthShort).append(':')
            append(ev.title).append(':')
            append(ev.category).append(';')
        }
    }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

internal fun calendarPackageToJson(pkg: AcademicCalendarUpdatePackage): JSONObject {
    val eventsArr = JSONArray()
    pkg.events.forEach { ev ->
        eventsArr.put(
            JSONObject().apply {
                put("id", ev.id)
                put("semester", ev.semester)
                put("eventDate", ev.eventDate)
                put("dayNumber", ev.dayNumber)
                put("monthShort", ev.monthShort)
                put("title", ev.title)
                put("category", ev.category)
            }
        )
    }
    return JSONObject().apply {
        put("format", "ufopcal-v1")
        put("version", pkg.version)
        put("semester", pkg.semester)
        put("publishedBy", pkg.publishedBy)
        put("publishedAt", pkg.publishedAt)
        put("signatureSha256", pkg.signatureSha256)
        put("events", eventsArr)
    }
}

internal fun parseCalendarPackageFromJson(obj: JSONObject?): AcademicCalendarUpdatePackage? {
    if (obj == null) return null
    val version = obj.optNonNullString("version").trim()
    val semester = obj.optNonNullString("semester").trim()
    val publishedBy = obj.optNonNullString("publishedBy").trim()
    val publishedAt = obj.optNonNullString("publishedAt").trim()
    val signatureSha256 = obj.optNonNullString("signatureSha256").trim()
    if (version.isEmpty() || semester.isEmpty() || signatureSha256.isEmpty()) return null

    val eventsArr = obj.optJSONArray("events") ?: return null
    val parsedEvents = mutableListOf<AcademicCalendarEvent>()
    for (i in 0 until eventsArr.length()) {
        val evObj = eventsArr.optJSONObject(i) ?: continue
        val evId = evObj.optNonNullString("id").trim()
        val evSemester = evObj.optNonNullString("semester", semester).trim()
        val eventDate = evObj.optNonNullString("eventDate").trim()
        val title = evObj.optNonNullString("title").trim()
        if (evId.isEmpty() || eventDate.isEmpty() || title.isEmpty()) continue
        val (_, parsedDay, parsedMonth) = parseDueDateParts(eventDate)
        parsedEvents.add(
            AcademicCalendarEvent(
                id = evId,
                semester = evSemester,
                eventDate = eventDate,
                dayNumber = evObj.optNonNullString("dayNumber", parsedDay).ifBlank { parsedDay },
                monthShort = evObj.optNonNullString("monthShort", parsedMonth).ifBlank { parsedMonth },
                title = title,
                category = evObj.optNonNullString("category", "Acadêmico").ifBlank { "Acadêmico" }
            )
        )
    }
    if (parsedEvents.isEmpty()) return null
    return AcademicCalendarUpdatePackage(
        version = version,
        semester = semester,
        publishedBy = publishedBy,
        publishedAt = publishedAt,
        signatureSha256 = signatureSha256,
        events = parsedEvents
    )
}

internal fun extractPdfTextWithPdfBox(pdfBytes: ByteArray): String {
    require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
    return SecurePdfProcessor.loadDocumentInMemoryOnly(pdfBytes).use { doc ->
        PDFTextStripper().apply {
            sortByPosition = true
        }.getText(doc)
    }
}


internal fun normalizeYear(rawYear: String?, defaultYear: Int): Int {
    val parsed = rawYear?.trim()?.toIntOrNull() ?: return defaultYear
    return when {
        parsed in 0..99 -> 2000 + parsed
        parsed in 1900..2100 -> parsed
        else -> defaultYear
    }
}

internal fun normalizeDedupKey(raw: String): String =
    normalizeSearchText(raw)
        .replace(NON_ALNUM_REGEX, " ")
        .trim()

internal fun extractDateFromSegment(
    segment: String,
    defaultYear: Int,
    fallbackMonth: Int?
): ParsedLineDate? {
    val extractedTime = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""").find(segment)?.let { m ->
        "${m.groupValues[1].padStart(2, '0')}:${m.groupValues[2]}"
    }

    // 1. Data por extenso em português (ex.: "18 de novembro de 2026", "10 a 12 de novembro")
    val longDateRegex = Regex(
        """\b(\d{1,2})(?:\s*(?:a|e|at[eé]|-|–)\s*\d{1,2})?\s+de\s+([A-Za-zÀ-ú]{3,12})\.?(?:\s+de\s+(\d{2,4}))?\b""",
        RegexOption.IGNORE_CASE
    )
    longDateRegex.findAll(segment).forEach { m ->
        val day = m.groupValues[1].toIntOrNull()
        val month = parsePortugueseMonthNumber(m.groupValues[2])
        val year = normalizeYear(m.groupValues[3].takeIf { it.isNotBlank() }, defaultYear)
        if (day != null && month != null) {
            val date = try { LocalDate.of(year, month, day) } catch (_: Throwable) { null }
            if (date != null) {
                val stripped = segment.removeRange(m.range)
                val title = cleanExtractedTitle(stripped)
                return ParsedLineDate(date.toString(), title, isBareDateLine(title), extractedTime)
            }
        }
    }

    // 2. Data numérica DD/MM ou DD/MM/YYYY (incluindo faixas "02/12/2026 a 05/12/2026")
    val numericDateRegex = Regex("""(?<!\d)(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?(?!\d)""")
    val scoreSuffixRegex = Regex("""^\s*(?:pts?|pontos?|%)\b""", RegexOption.IGNORE_CASE)
    val scorePrefixRegex = Regex("""(?:nota|valor|peso|escore|pontua[çc][aã]o)\s*[:=]?\s*$""", RegexOption.IGNORE_CASE)

    numericDateRegex.findAll(segment).forEach { m ->
        val before = segment.substring(0, m.range.first)
        val after = segment.substring(m.range.last + 1)
        if (scorePrefixRegex.containsMatchIn(before) || scoreSuffixRegex.containsMatchIn(after)) {
            return@forEach
        }
        val day = m.groupValues[1].toIntOrNull() ?: return@forEach
        val month = m.groupValues[2].toIntOrNull() ?: return@forEach
        val year = normalizeYear(m.groupValues[3].takeIf { it.isNotBlank() }, defaultYear)
        val date = try { LocalDate.of(year, month, day) } catch (_: Throwable) { null } ?: return@forEach

        val rangeContinuation = Regex("""^\s*(?:a|at[eé]|-|–|—)\s*\d{1,2}/\d{1,2}(?:/\d{2,4})?\b""", RegexOption.IGNORE_CASE)
            .find(after)
        val removeEnd = if (rangeContinuation != null) {
            m.range.last + 1 + rangeContinuation.value.length
        } else {
            m.range.last + 1
        }
        val stripped = segment.removeRange(m.range.first, removeEnd)
        val title = cleanExtractedTitle(stripped)
        return ParsedLineDate(date.toString(), title, isBareDateLine(title), extractedTime)
    }

    // 3. Data ISO YYYY-MM-DD
    val isoRegex = Regex("""\b(20\d{2})-(\d{2})-(\d{2})\b""")
    isoRegex.find(segment)?.let { m ->
        val date = parseIsoDateOrNull(m.value)
        if (date != null) {
            val stripped = segment.removeRange(m.range)
            val title = cleanExtractedTitle(stripped)
            return ParsedLineDate(date.toString(), title, isBareDateLine(title), extractedTime)
        }
    }

    // 4. Linha sob cabeçalho mensal no calendário (ex.: "02 - Finados", "02 a 05 - Recesso")
    if (fallbackMonth != null) {
        val sectionDayRegex = Regex(
            """^\s*[•*-]?\s*(\d{1,2})(?:\s*(?:a|e|at[eé]|-|–)\s*\d{1,2})?\s*(?:[-–—:.|]|\s+)\s*(?!(?:horas?|dias?|vagas?|pontos?|meses|anos?|%)\b)([A-Za-zÀ-ú].+)$""",
            RegexOption.IGNORE_CASE
        )
        sectionDayRegex.find(segment)?.let { m ->
            val day = m.groupValues[1].toIntOrNull()
            val date = if (day != null) {
                try { LocalDate.of(defaultYear, fallbackMonth, day) } catch (_: Throwable) { null }
            } else null
            if (date != null) {
                val title = cleanExtractedTitle(m.groupValues[2])
                return ParsedLineDate(date.toString(), title, isBareDateLine(title), extractedTime)
            }
        }
    }

    return null
}

internal fun cleanExtractedTitle(raw: String): String = raw
    .replace(Regex("""\(\s*\)"""), " ")
    .replace(Regex("""\[\s*\]"""), " ")
    .replace(Regex("""^[\s.,;:\-–—•·|/*]+"""), "")
    .replace(Regex("""[\s.,;:\-–—•·|/*]+$"""), "")
    .replace(Regex("""\s+"""), " ")
    .trim()

internal fun isBareDateLine(cleanedTitle: String): Boolean {
    if (cleanedTitle.isBlank()) return true
    return cleanedTitle.matches(
        Regex(
            """^(?:seg(?:unda)?|ter(?:[çc]a)?|qua(?:rta)?|qui(?:nta)?|sex(?:ta)?|s[aá]b(?:ado)?|dom(?:ingo)?|\d{1,2}:\d{2})$""",
            RegexOption.IGNORE_CASE
        )
    )
}

// ── Sprint 8: Parser do Histórico Escolar UFOP (PDF/Texto) & Calculadora Oficial de CRE ───────

internal data class ParsedTranscriptMeta(
    val fullName: String?,
    val registrationNumber: String?,
    val courseName: String?,
    val totalRequiredHours: Int?,
    val completedHours: Int?
)


internal fun parseTranscriptMetadata(lines: List<String>): ParsedTranscriptMeta {
    val collapsed = lines.joinToString(" ")

    val regMatch = Regex("""\b(\d{2}\.\d\.\d{3,5})\b""").find(collapsed)
        ?: Regex("""Matr[ií]cula\s*:?\s*([\d.\-/]+)""", RegexOption.IGNORE_CASE).find(collapsed)
    val registrationNumber = regMatch?.groupValues?.getOrNull(1)?.trimEnd(',', '.', ';')
        ?.takeIf { it.isNotEmpty() }

    var fullName: String? = null
    for (i in lines.indices) {
        val line = lines[i]
        val inlineNome = Regex("""^Nome\s*:\s*(.+)$""", RegexOption.IGNORE_CASE).find(line)
        if (inlineNome != null) {
            val candidate = inlineNome.groupValues[1]
                .replace(Regex("""\bMatr[ií]cula\b.*$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\b\d{2}\.\d\.\d{3,5}\b"""), "")
                .trim()
            if (candidate.isNotEmpty()) {
                fullName = normalizeUfopPortugueseText(candidate)
                break
            }
        } else if (line.matches(Regex("""^Nome(?:\s+Matr[ií]cula)?\s*$""", RegexOption.IGNORE_CASE))) {
            val next = lines.getOrNull(i + 1).orEmpty()
                .replace(Regex("""\b\d{2}\.\d\.\d{3,5}\b"""), "")
                .replace(Regex("""\bMatr[ií]cula\b.*$""", RegexOption.IGNORE_CASE), "")
                .trim()
            val normNext = normalizeSearchText(next)
            if (next.isNotEmpty() && normNext != "matricula" && normNext != "filiacao" && normNext != "historico") {
                fullName = normalizeUfopPortugueseText(next)
                break
            }
        }
    }

    val courseName = lines.asSequence()
        .mapNotNull { Regex("""CURSO:\s*(.+)$""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1) }
        .map {
            it.replace(Regex("""\s+Situa[çc][aã]o\b.*$""", RegexOption.IGNORE_CASE), "").trim()
        }
        .filter { it.isNotEmpty() }
        .map { normalizeUfopPortugueseText(it) }
        .firstOrNull()

    var totalRequiredHours: Int? = Regex("""CARGA\s+CURSO\s+(\d{3,5})\b""", RegexOption.IGNORE_CASE)
        .find(collapsed)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    if (totalRequiredHours == null) {
        for (i in lines.indices) {
            if (normalizeSearchText(lines[i]).contains("carga curso")) {
                val sameLine = Regex("""CARGA\s+CURSO\s+(\d{3,5})\b""", RegexOption.IGNORE_CASE)
                    .find(lines[i])?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (sameLine != null) {
                    totalRequiredHours = sameLine
                    break
                }
                val next1 = lines.getOrNull(i + 1).orEmpty()
                val next2 = lines.getOrNull(i + 2).orEmpty()
                val fromNext = Regex("""^\s*(\d{3,5})\b""").find(next1)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: Regex("""^\s*(\d{3,5})\b""").find(next2)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (fromNext != null) {
                    totalRequiredHours = fromNext
                    break
                }
            }
        }
    }

    val completedHours = lines.firstOrNull { normalizeSearchText(it).contains("obr:") }
        ?.let { Regex("""\bTOTAL\s*:\s*(\d{1,5})\b""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
        ?: Regex("""\bTOTAL\s*:\s*(\d{1,5})\b""", RegexOption.IGNORE_CASE)
            .find(collapsed)?.groupValues?.getOrNull(1)?.toIntOrNull()

    return ParsedTranscriptMeta(
        fullName = fullName,
        registrationNumber = registrationNumber,
        courseName = courseName,
        totalRequiredHours = totalRequiredHours,
        completedHours = completedHours
    )
}

internal fun cleanTranscriptProfessorName(raw: String): String {
    val strippedSuffix = raw
        .replace(Regex("""\s*\(\s*(?:T\s*\+\s*P|T|P)\s*\)""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
    return normalizeUfopPortugueseText(strippedSuffix)
}


internal fun roundCre2(value: Double): Double =
    if (!value.isFinite()) 0.0 else (value * 100.0).roundToInt() / 100.0

internal fun parseIsoDateOrNull(isoDate: String): LocalDate? = try {
    val clean = isoDate.trim()
    if (clean.length < 10) null else LocalDate.parse(clean.take(10))
} catch (_: Throwable) {
    null
}

internal fun parseIsoDateOrToday(isoDate: String): LocalDate =
    parseIsoDateOrNull(isoDate) ?: LocalDate.now()

internal fun formatPortugueseDate(isoDate: String): String =
    parseIsoDateOrNull(isoDate)?.let { formatPortugueseDate(it) } ?: isoDate

internal fun formatPortugueseDate(date: LocalDate): String {
    val weekday = when (date.dayOfWeek.value) {
        1 -> "Segunda-feira"
        2 -> "Terça-feira"
        3 -> "Quarta-feira"
        4 -> "Quinta-feira"
        5 -> "Sexta-feira"
        6 -> "Sábado"
        7 -> "Domingo"
        else -> "Segunda-feira"
    }
    return "$weekday, ${date.dayOfMonth} de ${portugueseMonthLower(date.monthValue)}"
}

internal fun formatMonthYearLabel(date: LocalDate): String {
    val monthCap = portugueseMonthLower(date.monthValue).replaceFirstChar { it.titlecase() }
    return "$monthCap ${date.year}"
}

internal fun portugueseMonthLower(month: Int): String = when (month) {
    1 -> "janeiro"
    2 -> "fevereiro"
    3 -> "março"
    4 -> "abril"
    5 -> "maio"
    6 -> "junho"
    7 -> "julho"
    8 -> "agosto"
    9 -> "setembro"
    10 -> "outubro"
    11 -> "novembro"
    12 -> "dezembro"
    else -> "novembro"
}

internal fun toCourseScheduleSlotItem(slot: SlotSeed): CourseScheduleSlotItem =
    CourseScheduleSlotItem(
        slotId = slot.slotId,
        courseId = slot.courseId,
        dayOfWeek = slot.dayOfWeek,
        dayLabel = dayLabelFor(slot.dayOfWeek),
        startTime = slot.startTime,
        endTime = slot.endTime,
        building = slot.building,
        room = slot.room,
        shortLocation = slot.shortLocation
    )

internal fun dayLabelFor(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "SEG"
    2 -> "TER"
    3 -> "QUA"
    4 -> "QUI"
    5 -> "SEX"
    6 -> "SÁB"
    7 -> "DOM"
    else -> "SEG"
}

internal fun buildShortLocation(building: String, room: String): String {
    if (isEadIndicatorText(building) || building.contains("Ensino a Distância", ignoreCase = true) || building.contains("Ensino a Distancia", ignoreCase = true)) {
        return "EAD"
    }
    val shortB = building.trim()
        .removePrefix("Prédio ")
        .removePrefix("Bloco ")
        .trim()
    val shortR = room.trim()
        .removePrefix("Sala ")
        .replace("Laboratório ", "Lab ")
        .trim()
    return when {
        shortB.isNotEmpty() && shortR.isNotEmpty() -> "$shortB · $shortR"
        shortB.isNotEmpty() -> shortB
        shortR.isNotEmpty() -> shortR
        else -> "UFOP"
    }
}

internal fun timesOverlap(startA: String, endA: String, startB: String, endB: String): Boolean {
    val a0 = parseMinutes(startA) ?: return startA == startB
    val a1 = parseMinutes(endA) ?: return startA == startB
    val b0 = parseMinutes(startB) ?: return startA == startB
    val b1 = parseMinutes(endB) ?: return startA == startB
    if (a0 >= a1 || b0 >= b1) return startA == startB
    return a0 < b1 && b0 < a1
}

internal fun parseMinutes(hhmm: String): Int? {
    val parts = hhmm.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

internal fun JSONObject.optNonNullString(key: String, fallback: String = ""): String =
    if (isNull(key)) fallback else optString(key, fallback)


internal fun resolveCourseCode(course: CourseSeed, notesByCourseId: Map<String, String>): String {
    val currentNotes = notesByCourseId[course.courseId] ?: course.initialNotes
    return deriveCourseCodeFallback(course.code, currentNotes, course.courseId, course.name)
}
