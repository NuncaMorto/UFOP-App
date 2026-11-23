package br.ufop.campus.core.ffi

import br.ufop.campus.core.security.SecurePdfProcessor
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.time.LocalDate
import kotlin.math.abs

internal data class PdfWord(
    val x0: Float,
    val x1: Float,
    val y: Float,
    val text: String
) {
    val xCenter: Float get() = (x0 + x1) * 0.5f
}

internal data class PdfTimeRow(
    val y: Float,
    val startTime: String,
    val endTime: String
)

internal fun isEadIndicatorText(raw: String): Boolean =
    raw.isNotBlank() && EAD_INDICATOR_REGEX.containsMatchIn(raw)

internal data class ParsedGridToken(
    val courseCode: String,
    val turma: String,
    val kind: String, // "T" ou "P"
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val hasEadMarker: Boolean = false
)

internal data class ParsedCourseTableRow(
    val code: String,
    val turma: String,
    val rawTitle: String,
    val building: String,
    val language: String,
    val isEad: Boolean = false
)

internal data class ParsedCertificateMeta(
    val semester: String,
    val fullName: String?,
    val registrationNumber: String?,
    val courseName: String?,
    val campusId: String?,
    val campusName: String?,
    val semesterStartDate: String?,
    val semesterEndDate: String?
)

internal fun extractPdfLinesAndWords(pdfBytes: ByteArray): Pair<List<String>, List<PdfWord>> {
    require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
    data class PdfGlyph(
        val x0: Float,
        val x1: Float,
        val y: Float,
        val spaceWidth: Float,
        val ch: String
    )

    val glyphs = mutableListOf<PdfGlyph>()
    val rawText = SecurePdfProcessor.loadDocumentInMemoryOnly(pdfBytes).use { doc ->
        val stripper = object : PDFTextStripper() {
            init {
                sortByPosition = true
            }

            override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                if (textPositions != null) {
                    val pageOffset = (currentPageNo - 1).coerceAtLeast(0) * 1000f
                    for (tp in textPositions) {
                        val ch = tp.unicode ?: continue
                        if (ch.isBlank()) continue
                        glyphs.add(
                            PdfGlyph(
                                x0 = tp.xDirAdj,
                                x1 = tp.xDirAdj + tp.widthDirAdj,
                                y = tp.yDirAdj + pageOffset,
                                spaceWidth = tp.widthOfSpace,
                                ch = ch
                            )
                        )
                    }
                }
                super.writeString(text, textPositions)
            }
        }
        stripper.getText(doc)
    }

    if (glyphs.isEmpty()) {
        val fallbackLines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return fallbackLines to emptyList()
    }

    // Agrupa glifos em linhas por coordenada Y e depois em palavras pelo espaçamento horizontal.
    val sortedGlyphs = glyphs.sortedWith(compareBy<PdfGlyph> { it.y }.thenBy { it.x0 })
    val rowBuckets = mutableListOf<MutableList<PdfGlyph>>()
    for (g in sortedGlyphs) {
        val lastRow = rowBuckets.lastOrNull()
        if (lastRow != null && abs(g.y - lastRow[0].y) <= 3.0f) {
            lastRow.add(g)
        } else {
            rowBuckets.add(mutableListOf(g))
        }
    }

    val words = mutableListOf<PdfWord>()
    val lines = mutableListOf<String>()
    for (row in rowBuckets) {
        row.sortBy { it.x0 }
        val rowWords = mutableListOf<PdfWord>()
        val currentWord = StringBuilder()
        var wordX0 = 0f
        var wordX1 = 0f
        var wordYSum = 0f
        var wordCount = 0

        fun flushWord() {
            if (currentWord.isNotEmpty() && wordCount > 0) {
                rowWords.add(
                    PdfWord(
                        x0 = wordX0,
                        x1 = wordX1,
                        y = wordYSum / wordCount,
                        text = currentWord.toString()
                    )
                )
                currentWord.setLength(0)
                wordCount = 0
            }
        }

        for (g in row) {
            if (wordCount == 0) {
                wordX0 = g.x0
                wordX1 = g.x1
                wordYSum = g.y
                wordCount = 1
                currentWord.append(g.ch)
            } else {
                val gapThreshold = (g.spaceWidth * 0.5f).coerceIn(1.2f, 2.2f)
                if (g.x0 - wordX1 > gapThreshold) {
                    flushWord()
                    wordX0 = g.x0
                    wordX1 = g.x1
                    wordYSum = g.y
                    wordCount = 1
                    currentWord.append(g.ch)
                } else {
                    wordX1 = maxOf(wordX1, g.x1)
                    wordYSum += g.y
                    wordCount += 1
                    currentWord.append(g.ch)
                }
            }
        }
        flushWord()

        if (rowWords.isNotEmpty()) {
            words.addAll(rowWords)
            lines.add(rowWords.joinToString(" ") { it.text })
        }
    }
    return lines to words
}


internal fun parseCertificateMeta(lines: List<String>): ParsedCertificateMeta {
    val joined = lines.joinToString("\n")
    val collapsed = lines.joinToString(" ").replace(Regex("\\s+"), " ").trim()

    val semesterMatch = Regex("""ATESTADO\s+DE\s+MATR[IÍ]CULA\s+(\d{4})[/.](\d)""", RegexOption.IGNORE_CASE)
        .find(collapsed)
    val semester = if (semesterMatch != null) {
        "${semesterMatch.groupValues[1]}.${semesterMatch.groupValues[2]}"
    } else {
        "2026.2"
    }

    val nameMatch = Regex("""Atestamos\s+que\s+(.+?)\s+matr[ií]cula\b""", RegexOption.IGNORE_CASE)
        .find(collapsed)
    val fullName = nameMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { toPortugueseTitleCase(it) }

    val regMatch = Regex("""matr[ií]cula\s+n[º°o.]+\s*([\d.\-/]+)""", RegexOption.IGNORE_CASE)
        .find(collapsed)
        ?: Regex("""\b(\d{2}\.\d\.\d{3,5})\b""").find(collapsed)
    val registrationNumber = regMatch?.groupValues?.getOrNull(1)?.trimEnd(',', '.', ';')
        ?.takeIf { it.isNotEmpty() }

    val courseMatch = Regex("""no\s+curso\s+de\s+(.+?),\s*cuja\b""", RegexOption.IGNORE_CASE)
        .find(collapsed)
    val courseName = courseMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { toPortugueseTitleCase(it) }

    val campusMatch = Regex(
        """no\s+campus\s+([A-Za-zÀ-ú\s]+?)(?:\.|,|\s+Hor[aá]rio|$)""",
        RegexOption.IGNORE_CASE
    ).find(collapsed)
    val rawCampus = campusMatch?.groupValues?.getOrNull(1)?.trim()
    val campusId = rawCampus?.let { resolveCampusIdFromText(it) }
    val campusName = campusId?.let { id -> ufopCampusesSeed.find { it.id == id }?.city }

    val startMatch = Regex(
        """In[ií]cio\s+das\s+Aulas:\s*(\d{1,2})\s+de\s+([A-Za-zÀ-ú]+)\s+de\s+(\d{4})""",
        RegexOption.IGNORE_CASE
    ).find(joined)
    val startDate = startMatch?.let { parsePortugueseLongDate(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }

    val endMatch = Regex(
        """T[eé]rmino\s+das\s+Aulas:\s*(\d{1,2})\s+de\s+([A-Za-zÀ-ú]+)\s+de\s+(\d{4})""",
        RegexOption.IGNORE_CASE
    ).find(joined)
    val endDate = endMatch?.let { parsePortugueseLongDate(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }

    return ParsedCertificateMeta(
        semester = semester,
        fullName = fullName,
        registrationNumber = registrationNumber,
        courseName = courseName,
        campusId = campusId,
        campusName = campusName,
        semesterStartDate = startDate,
        semesterEndDate = endDate
    )
}

internal fun parseCourseTable(lines: List<String>): List<ParsedCourseTableRow> {
    val knownBuildings = "ENSINO\\s+[AÀ]\\s+DIST[AÂ]NCIA|[AÀ]\\s+DIST[AÂ]NCIA|E\\.A\\.D\\.|EAD|CEAD|AVA|MOODLE|ONLINE|VIRTUAL|REMOT[AO]|ICEB(?:\\s*\\(?(?:I|II|III)\\)?)?|ESCOLA\\s+DE\\s+MINAS|ICHS|ICSA|ICEA|EMED|EFAR|EDTM|ENUT|CEDUFOP|DECOM|DEFIS|DEMAT|DEGEO|DEPRO|DECAT|DECIV|DEURB|DEART|DEMUS|DELET|DEEDU|DEPED|DEFAR|DENUT|DEENF|DEMED|DEBIO|DEQUI|DEAMB|DEARQ|DEHIS|DEDIR|DEECO|DEJOR|DESSO|DETUR|DEELT|DEMEC|DEMET|DEMIN|DECSI|DECEA|DEENP|DEALI|DEFIL|EM"
    val strictRowRegex = Regex(
        """^([A-Z]{3}\d{3})\s+(\d{1,3})\s+(.+?)\s+($knownBuildings)\s+(PORTUGU[EÊ]S|INGL[EÊ]S|ESPANHOL)\b""",
        RegexOption.IGNORE_CASE
    )
    val fallbackRowRegex = Regex(
        """^([A-Z]{3}\d{3})\s+(\d{1,3})\s+(.+?)\s+([A-Z0-9]{2,12})\s+(PORTUGU[EÊ]S|INGL[EÊ]S|ESPANHOL)\b""",
        RegexOption.IGNORE_CASE
    )

    val results = mutableListOf<ParsedCourseTableRow>()
    val seenCodes = mutableSetOf<String>()
    for (rawLine in lines) {
        val line = rawLine.trim().replace(Regex("\\s+"), " ")
        val match = strictRowRegex.find(line) ?: fallbackRowRegex.find(line) ?: continue
        val code = match.groupValues[1].uppercase()
        if (!seenCodes.add(code)) continue
        val rawTitle = match.groupValues[3].trim()
        val building = match.groupValues[4].trim()
        val isEad = isEadIndicatorText(building) ||
            isEadIndicatorText(rawTitle) ||
            isEadIndicatorText(line)
        results.add(
            ParsedCourseTableRow(
                code = code,
                turma = match.groupValues[2].trim(),
                rawTitle = rawTitle,
                building = building,
                language = match.groupValues[5].trim(),
                isEad = isEad
            )
        )
    }
    return results
}

internal fun parseGridTokensByCoordinates(words: List<PdfWord>): List<ParsedGridToken> {
    val headerCandidates = mutableListOf<Pair<Int, PdfWord>>()
    for (word in words) {
        val day = parseWeekdayHeaderWord(word.text) ?: continue
        headerCandidates.add(day to word)
    }
    if (headerCandidates.isEmpty()) return emptyList()

    // Seleciona a linha horizontal que concentra os cabeçalhos dos dias (Segunda..Sábado).
    val headerRows = mutableListOf<MutableList<Pair<Int, PdfWord>>>()
    for (candidate in headerCandidates.sortedBy { it.second.y }) {
        val bucket = headerRows.lastOrNull()
        if (bucket != null && abs(candidate.second.y - bucket[0].second.y) <= 8f) {
            bucket.add(candidate)
        } else {
            headerRows.add(mutableListOf(candidate))
        }
    }
    val bestHeaderRow = headerRows.maxByOrNull { row -> row.map { it.first }.distinct().size }
        ?: return emptyList()
    val dayColumns = linkedMapOf<Int, Float>()
    bestHeaderRow.forEach { (day, word) ->
        dayColumns.putIfAbsent(day, word.xCenter)
    }
    if (dayColumns.size < 3) return emptyList()

    // Agrupa palavras em linhas visuais para identificar os horários na coluna da esquerda.
    val rowBuckets = mutableListOf<MutableList<PdfWord>>()
    for (word in words.sortedWith(compareBy<PdfWord> { it.y }.thenBy { it.x0 })) {
        val last = rowBuckets.lastOrNull()
        if (last != null && abs(word.y - last[0].y) <= 3.5f) {
            last.add(word)
        } else {
            rowBuckets.add(mutableListOf(word))
        }
    }

    val timeRegex = Regex("""(\d{2}:\d{2})\s*-\s*(\d{2}:\d{2})""")
    val timeRows = mutableListOf<PdfTimeRow>()
    for (row in rowBuckets) {
        val rowText = row.sortedBy { it.x0 }.joinToString(" ") { it.text }
        val m = timeRegex.find(rowText) ?: continue
        val avgY = row.map { it.y }.average().toFloat()
        timeRows.add(PdfTimeRow(y = avgY, startTime = m.groupValues[1], endTime = m.groupValues[2]))
    }
    if (timeRows.isEmpty()) return emptyList()

    val tokenRegex = Regex("""([A-Z]{3}\d{3})\s*-\s*(\d{1,3})\s*([TP])""", RegexOption.IGNORE_CASE)
    val cellWords = linkedMapOf<Pair<Int, Int>, MutableList<PdfWord>>()
    for (word in words) {
        val nearestRowIdx = timeRows.indices.minByOrNull { abs(word.y - timeRows[it].y) } ?: continue
        if (abs(word.y - timeRows[nearestRowIdx].y) > 10f) continue
        val nearestCol = dayColumns.entries.minByOrNull { abs(word.xCenter - it.value) } ?: continue
        if (abs(word.xCenter - nearestCol.value) > 65f) continue
        cellWords.getOrPut(nearestRowIdx to nearestCol.key) { mutableListOf() }.add(word)
    }

    val tokens = mutableListOf<ParsedGridToken>()
    for ((cellKey, cellList) in cellWords) {
        val (rowIdx, dayOfWeek) = cellKey
        val row = timeRows[rowIdx]
        val cellText = cellList.sortedBy { it.x0 }.joinToString(" ") { it.text }
        val cellIsEad = isEadIndicatorText(cellText)
        tokenRegex.findAll(cellText).forEach { m ->
            tokens.add(
                ParsedGridToken(
                    courseCode = m.groupValues[1].uppercase(),
                    turma = m.groupValues[2],
                    kind = m.groupValues[3].uppercase(),
                    dayOfWeek = dayOfWeek,
                    startTime = row.startTime,
                    endTime = row.endTime,
                    hasEadMarker = cellIsEad
                )
            )
        }
    }
    return tokens
}

internal fun parseGridTokensFromText(lines: List<String>): List<ParsedGridToken> {
    val headerLine = lines.firstOrNull { line ->
        val norm = normalizeSearchText(line)
        norm.contains("segunda") && norm.contains("terca")
    }
    val headerDayCenters = mutableMapOf<Int, Float>()
    if (headerLine != null) {
        val dayNames = listOf(
            1 to "segunda", 2 to "terca", 3 to "quarta",
            4 to "quinta", 5 to "sexta", 6 to "sabado"
        )
        // Preserva espaços iniciais do cabeçalho para alinhar colunas com as linhas de horário.
        val normHeader = stripDiacriticsLower(headerLine)
        for ((day, name) in dayNames) {
            val idx = normHeader.indexOf(name)
            if (idx >= 0) {
                headerDayCenters[day] = idx + name.length * 0.5f
            }
        }
    }
    val hasAlignedColumns = headerLine != null && (headerLine.contains("  ") || headerLine.contains("\t"))

    val rowRegex = Regex("""^\s*(\d{2}:\d{2})\s*-\s*(\d{2}:\d{2})\b(.*)$""")
    val tokenRegex = Regex("""([A-Z]{3}\d{3})\s*-\s*(\d{1,3})\s*([TP])""", RegexOption.IGNORE_CASE)
    val tokens = mutableListOf<ParsedGridToken>()

    for (line in lines) {
        val rowMatch = rowRegex.find(line) ?: continue
        val startTime = rowMatch.groupValues[1]
        val endTime = rowMatch.groupValues[2]
        val matches = tokenRegex.findAll(line).toList()
        matches.forEachIndexed { idx, m ->
            val dayOfWeek = if (hasAlignedColumns && headerDayCenters.isNotEmpty()) {
                val tokenCenter = (m.range.first + m.range.last) * 0.5f
                headerDayCenters.entries.minByOrNull { abs(tokenCenter - it.value) }?.key
                    ?: (idx + 1).coerceIn(1, 6)
            } else {
                (idx + 1).coerceIn(1, 6)
            }
            val sliceEnd = matches.getOrNull(idx + 1)?.range?.first ?: line.length
            val localSlice = line.substring(m.range.first, sliceEnd)
            tokens.add(
                ParsedGridToken(
                    courseCode = m.groupValues[1].uppercase(),
                    turma = m.groupValues[2],
                    kind = m.groupValues[3].uppercase(),
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime,
                    hasEadMarker = isEadIndicatorText(localSlice)
                )
            )
        }
    }
    return tokens
}


internal data class MergedGridSlot(
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val kind: String,
    val weight: Int
)

internal fun mergeConsecutiveGridTokens(tokens: List<ParsedGridToken>): List<MergedGridSlot> {
    if (tokens.isEmpty()) return emptyList()
    val sorted = tokens
        .distinctBy { "${it.dayOfWeek}|${it.startTime}|${it.endTime}|${it.kind}" }
        .sortedWith(
            compareBy<ParsedGridToken> { it.dayOfWeek }
                .thenBy { parseMinutes(it.startTime) ?: Int.MAX_VALUE }
                .thenBy { it.startTime }
        )
    val merged = mutableListOf<MergedGridSlot>()
    for (token in sorted) {
        val prev = merged.lastOrNull()
        val prevEndMin = prev?.let { parseMinutes(it.endTime) }
        val nextStartMin = parseMinutes(token.startTime)
        // Intervalos padrão UFOP entre tempos de aula: 0 min, 10 min ou 20 min. Almoço/tarde >= 40 min.
        if (prev != null &&
            prev.dayOfWeek == token.dayOfWeek &&
            prev.kind == token.kind &&
            prevEndMin != null &&
            nextStartMin != null &&
            nextStartMin >= prevEndMin &&
            (nextStartMin - prevEndMin) <= 20
        ) {
            merged[merged.lastIndex] = prev.copy(
                endTime = token.endTime,
                weight = prev.weight + 1
            )
        } else {
            merged.add(
                MergedGridSlot(
                    dayOfWeek = token.dayOfWeek,
                    startTime = token.startTime,
                    endTime = token.endTime,
                    kind = token.kind,
                    weight = 1
                )
            )
        }
    }
    return merged
}

internal fun parseWeekdayHeaderWord(word: String): Int? = when (normalizeSearchText(word)) {
    "segunda", "segunda-feira", "seg" -> 1
    "terca", "terca-feira", "ter" -> 2
    "quarta", "quarta-feira", "qua" -> 3
    "quinta", "quinta-feira", "qui" -> 4
    "sexta", "sexta-feira", "sex" -> 5
    "sabado", "sab" -> 6
    else -> null
}


internal fun resolveDepartmentForCourseCode(code: String): DepartmentColorOption {
    val upper = code.trim().uppercase()
    val prefix = upper.take(3)
    val mapped = courseCodePrefixToDeptId[prefix]
        ?: "de${prefix.lowercase()}".takeIf { it in departmentById }
        ?: "decom"
    return resolveDepartmentOption(mapped)
}

internal fun normalizeKnownDepartmentId(departmentId: String): String? {
    val raw = departmentId.trim().lowercase()
    if (raw.isEmpty()) return null
    if (raw in departmentById) return raw
    legacyDepartmentAliases[raw]?.let { return it }
    if (raw.contains("·")) {
        val sigla = raw.substringBefore("·").trim()
        if (sigla in departmentById) return sigla
        legacyDepartmentAliases[sigla]?.let { return it }
    }
    return null
}

internal fun resolveDepartmentOption(departmentId: String): DepartmentColorOption {
    val resolvedId = normalizeKnownDepartmentId(departmentId) ?: "decom"
    return departmentById[resolvedId] ?: departmentById.getValue("decom")
}

internal fun resolveCampusIdFromText(text: String): String? {
    val norm = normalizeSearchText(text)
    return when {
        norm.contains("ouro preto") || norm.contains("cruzeiro") -> "op_cruzeiro"
        norm.contains("mariana") || norm.contains("ichs") || norm.contains("icsa") -> "mar_ichs"
        norm.contains("monlevade") || norm.contains("icea") -> "jm_icea"
        else -> null
    }
}

internal fun prettyBuildingName(raw: String): String {
    val trimmed = raw.trim()
    return when (normalizeSearchText(trimmed)) {
        "iceb", "icea", "ichs", "icsa", "emed", "efar", "edtm", "enut", "cedufop",
        "decom", "defis", "demat", "degeo", "deart", "depro", "cead" -> trimmed.uppercase()
        "escola de minas" -> "Escola de Minas"
        else -> toPortugueseTitleCase(trimmed)
    }
}

internal fun buildCertificateShortLocation(prettyBuilding: String): String = when {
    prettyBuilding.equals("Escola de Minas", ignoreCase = true) -> "EM"
    prettyBuilding.startsWith("Bloco de Salas", ignoreCase = true) ||
        prettyBuilding.startsWith("Blocos de Salas", ignoreCase = true) -> "Bloco"
    prettyBuilding.length <= 6 -> prettyBuilding
    else -> prettyBuilding.split(" ").joinToString("") { it.take(1).uppercase() }.take(4).ifBlank { "UFOP" }
}

internal fun buildRoomShortLocation(building: String, room: String): String {
    if (isEadIndicatorText(building) ||
        building.contains("Ensino a Distância", ignoreCase = true) ||
        building.contains("Ensino a Distancia", ignoreCase = true)
    ) {
        return "EAD"
    }
    val cleanB = building.trim()
    val shortB = when {
        cleanB.startsWith("Bloco de Salas", ignoreCase = true) ||
            cleanB.startsWith("Blocos de Salas", ignoreCase = true) ||
            cleanB.equals("Bloco", ignoreCase = true) -> "Bloco"
        else -> buildCertificateShortLocation(cleanB)
    }
    val cleanR = room.trim()
    return when {
        cleanR.isEmpty() -> shortB
        shortB.isEmpty() -> cleanR
        else -> "$shortB · $cleanR"
    }
}

internal fun buildCleanCourseBuildingAndRoom(
    fallbackBuilding: String,
    courseSlots: List<SlotSeed>
): String {
    if (isEadIndicatorText(fallbackBuilding) ||
        fallbackBuilding.contains("Ensino a Distância", ignoreCase = true) ||
        fallbackBuilding.contains("Ensino a Distancia", ignoreCase = true)
    ) {
        return "Ensino a Distância (EAD)"
    }
    val nonGenericSlots = courseSlots.filter { slot ->
        val r = slot.room.trim()
        r.isNotEmpty() && r.lowercase() !in GENERIC_REPLACEABLE_ROOMS
    }
    if (nonGenericSlots.isNotEmpty()) {
        val byBuilding = nonGenericSlots.groupBy { slot ->
            slot.building.trim().ifBlank { fallbackBuilding.trim().ifBlank { "Campus UFOP" } }
        }
        return byBuilding.entries.joinToString(" · ") { (b, bSlots) ->
            val distinctRooms = bSlots.map { it.room.trim() }.filter { it.isNotEmpty() }.distinct()
            when {
                distinctRooms.isEmpty() -> b
                distinctRooms.size == 1 -> "$b · ${distinctRooms.first()}"
                distinctRooms.all { it.startsWith("Sala ", ignoreCase = true) } -> {
                    val nums = distinctRooms.map { it.substringAfter(" ").trim() }
                    "$b · Salas ${nums.joinToString(" / ")}"
                }
                else -> "$b · ${distinctRooms.joinToString(" / ")}"
            }
        }
    }
    val slotBuilding = courseSlots.firstOrNull()?.building?.trim()?.takeIf { it.isNotEmpty() }
    val cleanFallback = fallbackBuilding.substringBefore("·").trim()
    return (slotBuilding ?: cleanFallback).ifBlank { "Campus UFOP" }
}


internal fun toPortugueseTitleCase(raw: String): String =
    normalizeUfopPortugueseText(raw)

internal fun parsePortugueseMonthNumber(monthStr: String): Int? =
    when (normalizeSearchText(monthStr).trimEnd('.')) {
        "janeiro", "jan" -> 1
        "fevereiro", "fev" -> 2
        "marco", "mar" -> 3
        "abril", "abr" -> 4
        "maio", "mai" -> 5
        "junho", "jun" -> 6
        "julho", "jul" -> 7
        "agosto", "ago" -> 8
        "setembro", "set" -> 9
        "outubro", "out" -> 10
        "novembro", "nov" -> 11
        "dezembro", "dez" -> 12
        else -> null
    }

internal fun parsePortugueseLongDate(dayStr: String, monthStr: String, yearStr: String): String? {
    val day = dayStr.toIntOrNull() ?: return null
    val year = yearStr.toIntOrNull() ?: return null
    val month = parsePortugueseMonthNumber(monthStr) ?: return null
    return try {
        LocalDate.of(year, month, day).toString()
    } catch (_: Throwable) {
        null
    }
}
