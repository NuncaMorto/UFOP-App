package br.ufop.campus.core.ffi

import java.time.LocalDate

internal data class ParsedLineDate(
    val isoDate: String,
    val remainingTitle: String,
    val isBareDate: Boolean,
    val extractedTime: String?
)

internal val syllabusEvalRegex = Regex(
    """\b(?:prova|provas|p[1-9]|exame|exames|teste|testes|quiz|quizzes|tp\d*|trabalho|trabalhos|lista|listas|projeto|projetos|relatorio|relatorios|seminario|seminarios|avaliacao|avaliacoes|substitutiva|entrega|entregas|apresentacao)\b""",
    RegexOption.IGNORE_CASE
)

internal val syllabusHardNoiseRegex = Regex(
    """\b(?:bibliografia|referencias?|ementa|objetivos?|metodologia|criterios?|media\s+final|media\s+semestral|nota\s+minima|calculo\s+da\s+(?:media|nota)|frequencia|reprovad[oa]|reprovacao|aprovad[oa]|aprovacao|atestado|revisao|segunda\s+chamada|2[aª]?\s+chamada|duvidas?|plantao|atendimento\s+ao\s+aluno|vista\s+de\s+prova|devolucao\s+de\s+prova|divulgacao\s+de\s+notas|lancamento\s+de\s+notas|feriado|recesso|nao\s+havera\s+aula|sem\s+aula|suspensao|resolucao|cepe|prograd|regimento|plagio|plano\s+de\s+ensino|apresentacao\s+d[eao]s?\s+(?:disciplina|curso|ementa|programa|professor|professora|docente|turma|alunos|estudantes|calendario|cronograma)|sistema\s+de\s+avaliacao|forma\s+de\s+avaliacao|metodo\s+de\s+avaliacao|instrumentos?\s+de\s+avaliacao|distribuicao\s+de\s+pontos)\b""",
    RegexOption.IGNORE_CASE
)

internal val syllabusLeadingObsNoiseRegex = Regex(
    """^\s*(?:observac(?:ao|oes)|obs\.?|atencao|importante|nota)\s*[:\-]""",
    RegexOption.IGNORE_CASE
)

internal val syllabusProseClauseRegex = Regex(
    """\b(?:sera\s+calculad|sera\s+atribuid|sera\s+considerad|sera\s+realizad[oa]|serao\s+realizad[oa]s|devera\s+|deverao\s+|podera\s+|tera\s+direito|caso\s+o\s+aluno|em\s+caso\s+de|no\s+caso\s+de|o\s+aluno\s+que|o\s+estudante\s+que|os\s+alunos\s+que|nao\s+sera|mediante\s+|conforme\s+[oa]s?\b|nota\s+zero|totalizando\s+|composta\s+por|equivalente\s+a\s+nota|salvo\s+justificativa|peso\s+na\s+media)\b""",
    RegexOption.IGNORE_CASE
)

internal val syllabusProseFragmentRegex = Regex(
    """\b(?:a\s+entrega|as\s+entregas|pelos\s+alunos|pelas\s+alunas|os\s+alunos|as\s+alunas|o\s+aluno|o\s+estudante|os\s+estudantes|devera\s+ser|deverao\s+ser|sera\s+realizad[oa]|serao\s+realizad[oa]s|no\s+caso\s+de|em\s+caso\s+de|conforme\s+[oa]s?)\b""",
    RegexOption.IGNORE_CASE
)

internal val syllabusCoreEvalKeywordRegex = Regex(
    """\b(?:prova|provas|p[1-9]|exame|exames|teste|testes|quiz|quizzes|tp\d*|trabalho|trabalhos|lista|listas|projeto|projetos|relatorio|relatorios|seminario|seminarios|avaliacao|avaliacoes|substitutiva|reposicao|recuperacao|apresentacao)\b""",
    RegexOption.IGNORE_CASE
)

internal val syllabusDanglingTailWords = setOf(
    "pelos", "pelas", "pelo", "pela",
    "das", "dos", "com", "para", "por",
    "de", "da", "do", "em", "na", "no", "nas", "nos",
    "a", "o", "as", "os", "ao", "aos",
    "que", "se", "ou", "e", "ate", "sobre", "entre", "sem", "sob"
)

internal val syllabusStructuredEvalAnchorRegex = Regex(
    """^\s*(?:(?:aula|semana|encontro|unidade|modulo|cap\.?)\s*\d+[a-zº°ª]?\s*[-–—:|.]*\s*)?(?:p[1-9]\b|prova\b|exame\b|tp\d*\b|trabalho\b|seminario\b|lista\b|relatorio\b|projeto\b|teste\b|quiz\b|(?:1|2|3|4|5)[aªºo]?\s+(?:prova|avaliacao|trabalho|lista|relatorio|seminario|teste)\b|(?:primeira|segunda|terceira|quarta|quinta)\s+(?:prova|avaliacao|trabalho|lista|relatorio|seminario)\b|avaliacao\s*(?:\d|i{1,3}|iv|final|substitutiva)\b|entrega\s+d[eao]s?\s+(?:trabalho|tp|lista|relatorio|projeto)\b|apresentacao\s+d[eao]s?\s+(?:seminario|trabalho|projeto)\b)""",
    RegexOption.IGNORE_CASE
)


internal data class SyllabusCourseMatchResult(
    val matched: Boolean,
    val detectedCode: String?,
    val detectedTitle: String?
)

internal fun extractRealUfopCourseCode(raw: String): String? {
    val clean = raw.trim().uppercase()
    if (!clean.matches(Regex("""^[A-Z]{2,4}\d{3,4}[A-Z]?$"""))) return null
    val prefix = clean.takeWhile { it.isLetter() }
    return if (prefix in NON_COURSE_CODE_PREFIXES) null else clean
}

internal fun extractCourseLevelNumeral(raw: String): Int? {
    val cleaned = raw
        .replace(Regex("""^\s*[A-Z]{2,4}\d{3,4}[A-Z]?\s*[-–—:]?\s*""", RegexOption.IGNORE_CASE), "")
        .trim()
    val norm = normalizeSearchText(cleaned)
    val match = Regex("""\b(iv|iii|ii|i|v|1|2|3|4|5)\s*$""").find(norm) ?: return null
    return when (match.groupValues[1]) {
        "i", "1" -> 1
        "ii", "2" -> 2
        "iii", "3" -> 3
        "iv", "4" -> 4
        "v", "5" -> 5
        else -> null
    }
}


internal fun extractProfessorFromSyllabus(rawText: String): String {
    val lines = rawText.lines()
        .map { it.trim().replace(Regex("""\s+"""), " ") }
        .filter { it.isNotEmpty() }
    val profLabelRegex = Regex(
        """^(?:professor(?:\(a\)|a)?(?:\s+(?:respons[áa]vel|ministrante))?|docente(?:\(s\)|(?:\s+(?:respons[áa]vel|ministrante)))?|ministrante|respons[áa]vel(?:\s+pela\s+disciplina)?)\s*[:\-–]\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )
    val nextLineLabelGuard = Regex(
        """^(?:disciplina|c[óo]digo|turma|semestre|per[íi]odo|carga|ementa|departamento|hor[áa]rio|campus|objetivos?)\b""",
        RegexOption.IGNORE_CASE
    )
    val noiseProfValues = setOf(
        "a definir", "nao informado", "sem professor", "pendente", "n/a", "ufop",
        "departamento", "colegiado", "coordenacao", "assinatura", "chefia"
    )

    val scanLimit = minOf(lines.size, 60)
    for (i in 0 until scanLimit) {
        val match = profLabelRegex.find(lines[i]) ?: continue
        var candidate = match.groupValues[1].trim()
        if (candidate.isEmpty() && i + 1 < lines.size) {
            val next = lines[i + 1]
            if (!next.contains(":") && !nextLineLabelGuard.containsMatchIn(normalizeSearchText(next))) {
                candidate = next
            }
        }
        candidate = candidate
            .replace(Regex("""\S+@\S+"""), " ")
            .replace(Regex("""\s+(?:e-?mail|siape|carga\s+hor[áa]ria|turma|semestre|per[íi]odo|hor[áa]rio|departamento|depto\.?|campus|sala)\b.*$""", RegexOption.IGNORE_CASE), "")
            .substringBefore("|")
            .substringBefore(";")
            .replace(Regex("""\([^)]*(?:@|depto|ufop|siape)[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ':', '-', '–', '—', '|', ';', ',', '_')

        val normCandidate = normalizeSearchText(candidate)
        val letterCount = candidate.count { it.isLetter() }
        if (candidate.length in 3..80 &&
            letterCount >= 3 &&
            normCandidate !in noiseProfValues &&
            !normCandidate.startsWith("a definir") &&
            !normCandidate.startsWith("nao informado") &&
            !normCandidate.contains("assinatura")
        ) {
            return normalizeUfopPortugueseText(candidate)
        }
    }
    return ""
}

internal fun detectGradeDistributionInSyllabus(rawText: String): Boolean {
    val norm = normalizeSearchText(rawText)
    if (Regex("""\b(?:distribuicao\s+d[eao]s?\s+(?:pontos|notas)|composicao\s+d[aeo]s?\s+(?:nota|notas|media)|divisao\s+d[eao]s?\s+(?:pontos|notas))\b""").containsMatchIn(norm)) {
        return true
    }
    if (Regex("""\b(?:peso\s*[:=]?\s*\d+(?:[.,]\d+)?|valendo\s+\d+(?:[.,]\d+)?|valor\s*[:=]?\s*\d+(?:[.,]\d+)?)\b""").containsMatchIn(norm)) {
        return true
    }
    if (Regex("""\b(?:p[1-9]|n[1-9]|t[1-9]|ap[1-9])\s*(?:\+|\*|=)\s*(?:p[1-9]|n[1-9]|t[1-9]|\d)|\bm[fs]?\s*=\s*""").containsMatchIn(norm)) {
        return true
    }
    // Pontos / % evitando falso positivo de frequência mínima (ex.: "frequencia minima de 75%")
    val scoreTokenRegex = Regex("""\b\d+(?:[.,]\d+)?\s*(?:pts?\.?|pontos?|%)\b""")
    for (m in scoreTokenRegex.findAll(norm)) {
        val ctxStart = (m.range.first - 36).coerceAtLeast(0)
        val ctxEnd = (m.range.last + 28).coerceAtMost(norm.length)
        val ctx = norm.substring(ctxStart, ctxEnd)
        val isAttendanceContext = ctx.contains("frequencia") ||
            ctx.contains("presenca") ||
            ctx.contains("faltas") ||
            ctx.contains("assiduidade")
        if (!isAttendanceContext) {
            return true
        }
    }
    if (Regex("""\b(?:criterios?\s+de\s+avaliacao|sistema\s+de\s+avaliacao|calculo\s+da\s+(?:media|nota)|media\s+(?:semestral|final))\b""").containsMatchIn(norm)) {
        val hasBreakdownDetails = Regex("""\b(?:p[1-9]|prova|trabalho|seminario|relatorio|lista|peso|pontos|valendo|igual\s+valor|media)\b""").containsMatchIn(norm) &&
            !Regex("""\b(?:criterios?\s+de\s+avaliacao|sistema\s+de\s+avaliacao)\s*[:\-]?\s*a\s+definir\b""").containsMatchIn(norm)
        if (hasBreakdownDetails) return true
    }
    return false
}


internal fun findValidDateRangesInSegment(segment: String, defaultYear: Int): List<IntRange> {
    val matches = mutableListOf<IntRange>()

    val longDateRegex = Regex(
        """\b(\d{1,2})(?:\s*(?:a|e|at[eé]|-|–)\s*\d{1,2})?\s+de\s+([A-Za-zÀ-ú]{3,12})\.?(?:\s+de\s+(\d{2,4}))?\b""",
        RegexOption.IGNORE_CASE
    )
    longDateRegex.findAll(segment).forEach { m ->
        val day = m.groupValues[1].toIntOrNull() ?: return@forEach
        val month = parsePortugueseMonthNumber(m.groupValues[2]) ?: return@forEach
        val year = normalizeYear(m.groupValues[3].takeIf { it.isNotBlank() }, defaultYear)
        if (runCatching { LocalDate.of(year, month, day) }.isSuccess) {
            matches.add(m.range)
        }
    }

    val numericDateRegex = Regex("""(?<!\d)(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?(?!\d)""")
    val scoreSuffixRegex = Regex("""^\s*(?:pts?|pontos?|%)\b""", RegexOption.IGNORE_CASE)
    val scorePrefixRegex = Regex("""(?:nota|valor|peso|escore|pontua[çc][aã]o)\s*[:=]?\s*$""", RegexOption.IGNORE_CASE)
    val rangeTailRegex = Regex("""^\s*(?:a|at[eé]|-|–|—)\s*\d{1,2}/\d{1,2}(?:/\d{2,4})?\b""", RegexOption.IGNORE_CASE)

    var skipUntil = -1
    numericDateRegex.findAll(segment).forEach { m ->
        if (m.range.first < skipUntil) return@forEach
        val before = segment.substring(0, m.range.first)
        val after = segment.substring(m.range.last + 1)
        if (scorePrefixRegex.containsMatchIn(before) || scoreSuffixRegex.containsMatchIn(after)) {
            return@forEach
        }
        val day = m.groupValues[1].toIntOrNull() ?: return@forEach
        val month = m.groupValues[2].toIntOrNull() ?: return@forEach
        val year = normalizeYear(m.groupValues[3].takeIf { it.isNotBlank() }, defaultYear)
        if (runCatching { LocalDate.of(year, month, day) }.isFailure) return@forEach
        val rangeTail = rangeTailRegex.find(after)
        val endIdx = if (rangeTail != null) {
            m.range.last + rangeTail.value.length
        } else {
            m.range.last
        }
        skipUntil = endIdx + 1
        matches.add(m.range.first..endIdx)
    }

    val isoRegex = Regex("""\b(20\d{2})-(\d{2})-(\d{2})\b""")
    isoRegex.findAll(segment).forEach { m ->
        if (parseIsoDateOrNull(m.value) != null && matches.none { it.first <= m.range.last && m.range.first <= it.last }) {
            matches.add(m.range)
        }
    }

    return matches.sortedBy { it.first }
}

internal fun splitSyllabusLineIntoSegments(rawLine: String, defaultYear: Int): List<String> {
    val cleaned = rawLine.trim().replace(Regex("""\s+"""), " ")
    if (cleaned.isEmpty()) return emptyList()

    val initialDateCount = findValidDateRangesInSegment(cleaned, defaultYear).size
    val normalizedPipes = if (cleaned.contains("|") && initialDateCount <= 1) {
        cleaned.replace("|", " - ")
    } else {
        cleaned
    }

    val primarySplitRegex = if (initialDateCount >= 2) {
        Regex("""[;•|]""")
    } else {
        Regex("""[;•]""")
    }

    val primaryPieces = primarySplitRegex.split(normalizedPipes)
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val output = mutableListOf<String>()
    for (piece in primaryPieces) {
        output.addAll(splitMultiDateSegment(piece, defaultYear))
    }
    return output
}

internal fun splitMultiDateSegment(piece: String, defaultYear: Int): List<String> {
    val dateRanges = findValidDateRangesInSegment(piece, defaultYear)
    if (dateRanges.size <= 1) return listOf(piece)

    // 1. Tenta dividir em separadores explícitos (`,`, `·`, ` e `) apenas quando ambos os lados contêm datas.
    val sepSplit = splitAtDateSeparators(piece, defaultYear)
    if (sepSplit.size > 1 && sepSplit.all { findValidDateRangesInSegment(it, defaultYear).size <= 1 }) {
        return sepSplit
    }

    // 2. Fallback ancorado em datas (Label-Data vs Data-Label)
    val leadingText = piece.substring(0, dateRanges.first().first)
        .replace(
            Regex(
                """^\s*(?:avalia[çc][õo]es(?:\s+(?:pr[áa]ticas?|te[óo]ricas?|previstas?|parciais?|da\s+disciplina))?|provas(?:\s+previstas)?|datas(?:\s+d[eao]s?\s+(?:provas?|avalia[çc][õo]es))?|cronograma(?:\s+de\s+avalia[çc][õo]es)?|calend[áa]rio(?:\s+de\s+(?:provas?|avalia[çc][õo]es))?)\s*[:\-]\s*""",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        .trim()
    val isLabelFirst = leadingText.isNotEmpty() && syllabusEvalRegex.containsMatchIn(normalizeSearchText(leadingText))

    val slices = mutableListOf<String>()
    if (isLabelFirst) {
        var cursor = 0
        for (idx in dateRanges.indices) {
            val range = dateRanges[idx]
            var end = range.last + 1
            // Absorve parêntese de fechamento ou pontuação imediata logo após a data, ex: "P1 (18/09)" ou "P1 18/09 (30 pts)"
            val after = piece.substring(end)
            val closingParen = Regex("""^\s*\)""").find(after)
            if (closingParen != null) {
                end += closingParen.value.length
            } else {
                val scoreParen = Regex("""^\s*\([^)]*(?:pts?|pontos?|peso|valor|%)[^)]*\)""", RegexOption.IGNORE_CASE).find(after)
                if (scoreParen != null && (idx + 1 >= dateRanges.size || end + scoreParen.value.length <= dateRanges[idx + 1].first)) {
                    end += scoreParen.value.length
                }
            }
            val slice = piece.substring(cursor, end).trim(' ', ',', ';', '-', '–', '—', '·', '|')
            if (slice.isNotEmpty()) slices.add(slice)
            cursor = end
        }
    } else {
        for (idx in dateRanges.indices) {
            val start = dateRanges[idx].first
            val end = if (idx + 1 < dateRanges.size) dateRanges[idx + 1].first else piece.length
            val slice = piece.substring(start, end).trim(' ', ',', ';', '-', '–', '—', '·', '|')
            if (slice.isNotEmpty()) slices.add(slice)
        }
    }
    return slices.ifEmpty { listOf(piece) }
}

internal fun splitAtDateSeparators(piece: String, defaultYear: Int): List<String> {
    val separatorRegex = Regex("""(?<!\d)\s*,\s*(?!\d)|\s+·\s+|\s+e\s+""", RegexOption.IGNORE_CASE)
    val cutPoints = mutableListOf<Pair<Int, Int>>()
    for (m in separatorRegex.findAll(piece)) {
        val left = piece.substring(0, m.range.first)
        val right = piece.substring(m.range.last + 1)
        if (findValidDateRangesInSegment(left, defaultYear).isNotEmpty() &&
            findValidDateRangesInSegment(right, defaultYear).isNotEmpty()
        ) {
            cutPoints.add(m.range.first to (m.range.last + 1))
        }
    }
    if (cutPoints.isEmpty()) return listOf(piece)
    val result = mutableListOf<String>()
    var start = 0
    for ((cutStart, cutEnd) in cutPoints) {
        val part = piece.substring(start, cutStart).trim()
        if (part.isNotEmpty()) result.add(part)
        start = cutEnd
    }
    val tail = piece.substring(start).trim()
    if (tail.isNotEmpty()) result.add(tail)
    return result
}

internal fun isSyllabusNoiseOrProse(rawSegment: String, cleanedWithoutDate: String): Boolean {
    val strippedLeadingRaw = rawSegment.replace(Regex("""^[\s.,;:\-–—•·|/]+"""), "")
    val strippedLeadingWithoutDate = cleanedWithoutDate.replace(Regex("""^[\s.,;:\-–—•·|/]+"""), "")

    val outsideParensRaw = strippedLeadingRaw
        .replace(Regex("""\([^)]*\)"""), " ")
        .replace(Regex("""\[[^\]]*]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
    val normOutside = normalizeSearchText(outsideParensRaw)
    val normWithoutDate = normalizeSearchText(
        strippedLeadingWithoutDate
            .replace(Regex("""\([^)]*\)"""), " ")
            .replace(Regex("""\[[^\]]*]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    )
    val normOutsideWords = normOutside.replace(Regex("""[^a-z0-9]+"""), " ").trim()
    val normWithoutDateWords = normWithoutDate.replace(Regex("""[^a-z0-9]+"""), " ").trim()
    val clutterStripped = stripSyllabusClutter(strippedLeadingWithoutDate)
    val normCandidateWords = normalizeSearchText(clutterStripped)
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()

    if (normCandidateWords.isBlank()) {
        return true
    }

    if (syllabusLeadingObsNoiseRegex.containsMatchIn(normOutside) ||
        syllabusLeadingObsNoiseRegex.containsMatchIn(normWithoutDate)
    ) {
        return true
    }

    if (syllabusHardNoiseRegex.containsMatchIn(normOutside) ||
        syllabusHardNoiseRegex.containsMatchIn(normWithoutDate) ||
        syllabusHardNoiseRegex.containsMatchIn(normOutsideWords) ||
        syllabusHardNoiseRegex.containsMatchIn(normWithoutDateWords)
    ) {
        return true
    }

    if (syllabusProseFragmentRegex.containsMatchIn(normOutsideWords) ||
        syllabusProseFragmentRegex.containsMatchIn(normWithoutDateWords) ||
        syllabusProseFragmentRegex.containsMatchIn(normCandidateWords)
    ) {
        return true
    }

    val candidateTokens = normCandidateWords.split(Regex("""\s+""")).filter { it.isNotBlank() }
    if (candidateTokens.isEmpty() || candidateTokens.last() in syllabusDanglingTailWords) {
        return true
    }

    // Rejeita fragmentos soltos de frase que não contêm palavra alfanumérica significativa de avaliação
    if (!syllabusCoreEvalKeywordRegex.containsMatchIn(normCandidateWords)) {
        return true
    }

    val hasStructuredEvalAnchor = syllabusStructuredEvalAnchorRegex.containsMatchIn(normWithoutDate) ||
        syllabusStructuredEvalAnchorRegex.containsMatchIn(
            normOutside.replace(Regex("""^\s*\d{1,2}/\d{1,2}(?:/\d{2,4})?\s*[-–—:|]*\s*"""), "")
        )

    val wordsWithoutDate = normWithoutDateWords.split(Regex("""\s+""")).filter { it.isNotBlank() }
    if ((syllabusProseClauseRegex.containsMatchIn(normOutsideWords) ||
            syllabusProseClauseRegex.containsMatchIn(normWithoutDateWords)) &&
        !hasStructuredEvalAnchor
    ) {
        return true
    }
    if (wordsWithoutDate.size > 14 && !hasStructuredEvalAnchor) {
        return true
    }
    // Frases longas em prosa que terminam com ponto e não começam como evento de avaliação
    if (wordsWithoutDate.size >= 10 &&
        outsideParensRaw.trimEnd().endsWith(".") &&
        !hasStructuredEvalAnchor
    ) {
        return true
    }
    return false
}

internal fun stripSyllabusClutter(raw: String): String {
    return raw
        .replace(Regex("""^[\s.,;:\-–—•·|/]+"""), "")
        // Remove parênteses/colchetes de pontuação, peso, horário, observações ou conteúdo genérico
        .replace(
            Regex(
                """\(\s*[^)]*(?:pts?\.?|pontos?|peso|valor|valendo|%|horas?|h\b|aula|obs\.?|todo\s+o\s+conte[uú]do|sem\s+consulta|com\s+consulta|individual|em\s+grupo|dupla|unidades?|cap[ií]tulos?|seg(?:unda)?|ter(?:[çc]a)?|qua(?:rta)?|qui(?:nta)?|sex(?:ta)?|s[aá]b(?:ado)?|\d{1,2}:\d{2})[^)]*\)""",
                RegexOption.IGNORE_CASE
            ),
            " "
        )
        .replace(
            Regex(
                """\[\s*[^\]]*(?:pts?\.?|pontos?|peso|valor|%|horas?|obs\.?)[^\]]*]""",
                RegexOption.IGNORE_CASE
            ),
            " "
        )
        // Remove pontuação / pesos / percentuais soltos
        .replace(
            Regex("""\b(?:valor|valendo|peso|nota|pontua[çc][aã]o)\s*[:=]?\s*\d+(?:[.,]\d+)?\s*(?:pts?\.?|pontos?|%)?\b""", RegexOption.IGNORE_CASE),
            " "
        )
        .replace(Regex("""\b\d+(?:[.,]\d+)?\s*(?:pts?\.?|pontos?|%)\b""", RegexOption.IGNORE_CASE), " ")
        // Remove horários
        .replace(Regex("""\b(?:[àa]s?\s*)?(?:[01]?\d|2[0-3]):[0-5]\d(?:\s*[-–a]\s*(?:[01]?\d|2[0-3]):[0-5]\d)?\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\b(?:[àa]s?\s*)?(?:[01]?\d|2[0-3])h(?:[0-5]\d)?\b""", RegexOption.IGNORE_CASE), " ")
        // Remove dias da semana
        .replace(Regex("""\b(?:segunda|ter[çc]a|quarta|quinta|sexta|s[aá]bado|domingo)(?:-feira)?\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\b(?:seg|ter|qua|qui|sex|s[aá]b|dom)\.\s*""", RegexOption.IGNORE_CASE), " ")
        // Remove prefixos "Aula 12 -", "Unidade 3 -", "Cronograma:", "Avaliações práticas:", numeração "1. "
        .replace(
            Regex(
                """^\s*(?:avalia[çc][õo]es(?:\s+(?:pr[áa]ticas?|te[óo]ricas?|previstas?|parciais?|da\s+disciplina))?|provas(?:\s+previstas)?|datas(?:\s+d[eao]s?\s+(?:provas?|avalia[çc][õo]es))?|cronograma(?:\s+de\s+avalia[çc][õo]es)?|calend[áa]rio(?:\s+de\s+(?:provas?|avalia[çc][õo]es))?)\s*[:\-]\s*""",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        .replace(Regex("""^\s*(?:aula|semana|encontro|unidade|m[óo]dulo|cap[ií]tulo|cap\.?)\s*\d+[a-zº°ª]?\s*[-–—:|.]*\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^\s*\d{1,2}\s*[.)]\s+"""), "")
        // Remove caudas de conteúdo/unidade após hífen ou dois-pontos
        .replace(Regex("""\s*[-–—|]?\s*\b(?:conte[uú]do|unidades?|cap[ií]tulos?|m[óo]dulos?|assunto|t[óo]picos?)\s*[:=\-].*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*[-–—|]\s*(?:unidades?|cap\.?|m[óo]dulos?)\s+\d+.*$""", RegexOption.IGNORE_CASE), "")
        // Remove preposições soltas deixadas pela remoção da data
        .replace(Regex("""\b(?:no\s+dia|dia|em)\s*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^\s*(?:no\s+dia|dia|data\s*:?)\s+""", RegexOption.IGNORE_CASE), "")
        .let { cleanExtractedTitle(it) }
}

internal fun extractSyllabusEvalNumber(normText: String): Int? {
    Regex("""\b(?:p|tp)\s*([1-9])\b""").find(normText)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
    Regex("""\b(?:prova|avaliacao|trabalho|lista|relatorio|seminario|projeto|teste|quiz|atividade)\s*(?:n[º°o.]?\s*)?([1-9])\b""")
        .find(normText)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
    Regex("""\b(?:prova|avaliacao|trabalho|lista|relatorio|seminario|projeto|teste)\s+(i{1,3}|iv|v)\b""")
        .find(normText)?.groupValues?.getOrNull(1)?.let { roman ->
            return when (roman) {
                "i" -> 1
                "ii" -> 2
                "iii" -> 3
                "iv" -> 4
                "v" -> 5
                else -> null
            }
        }
    Regex("""\b([1-5])\s*[aªºo]\s*(?:prova|avaliacao|trabalho|lista|relatorio|seminario|projeto|teste)\b""")
        .find(normText)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
    return when {
        Regex("""\bprimeir[ao]\b""").containsMatchIn(normText) -> 1
        Regex("""\bsegund[ao]\b""").containsMatchIn(normText) -> 2
        Regex("""\bterceir[ao]\b""").containsMatchIn(normText) -> 3
        Regex("""\bquart[ao]\b""").containsMatchIn(normText) -> 4
        Regex("""\bquint[ao]\b""").containsMatchIn(normText) -> 5
        else -> null
    }
}

internal fun extractDescriptiveTopic(cleaned: String, kindKeywordRegex: Regex, courseTag: String, courseName: String): String? {
    var remainder = cleaned
        .replace(Regex("""^\s*(?:entrega\s+d[eao]s?\s+|apresenta[çc][aã]o\s+d[eao]s?\s+)""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\btp\s*\d*\b""", RegexOption.IGNORE_CASE), " ")
        .replace(kindKeywordRegex, " ")
        .replace(Regex("""\b(?:pr[áa]tic[oa]s?|te[óo]ric[oa]s?|escrit[oa]s?|orais?|oral|final|parcial|avaliativ[oa]s?|em\s+grupo|individual|duplas?|equipes?|exerc[íi]cios?|moodle|online|presencial)\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\b(?:[1-5]\s*[aªºo]|primeir[ao]|segund[ao]|terceir[ao]|quart[ao]|quint[ao])\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""^\s*[-–—:|,.\d\s]+"""), "")
        .replace(Regex("""^\s*(?:sobre|acerca\s+de|de|da|do|das|dos)\s+""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^\s*[-–—:|,.\s]+"""), "")
        .replace(Regex("""[-–—:|,.\s]+$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    if (remainder.isBlank() || remainder.length > 34) return null

    val normRem = normalizeSearchText(remainder)
    if (normRem == normalizeSearchText(courseTag) || normRem == normalizeSearchText(courseName)) return null
    if (Regex("""\b(?:sera|deve|devera|entregar|enviar|realizar|fazer|valendo|nota|media|conteudo|unidade|capitulo|todo|disciplina|curso|turma|lab\s*\d+)\b""").containsMatchIn(normRem)) {
        return null
    }

    val stopwords = setOf("de", "da", "do", "das", "dos", "e", "em", "na", "no", "nas", "nos", "para", "com", "sobre", "a", "o", "as", "os", "pelos", "pelas", "pelo", "pela")
    val normTokens = normRem.replace(Regex("""[^a-z0-9]+"""), " ").trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
    if (normTokens.isEmpty() || normTokens.last() in syllabusDanglingTailWords) return null
    val meaningfulWords = normTokens.filter { it.length >= 2 && it !in stopwords }
    if (meaningfulWords.isEmpty() || meaningfulWords.size > 4) return null

    // Remove ponto final ou aspas extras
    remainder = remainder.trim('"', '\'', '.', ',', ';', '-', '–', '—', ':')
    return remainder.takeIf { it.length in 3..32 }
}

internal fun formatSyllabusAssessmentTitleAndKind(
    rawTitle: String,
    keywordSource: String,
    course: CourseSeed
): Pair<String, String>? {
    val cleaned = stripSyllabusClutter(rawTitle.ifBlank { keywordSource })
    if (cleaned.isBlank()) return null
    val normCleaned = normalizeSearchText(cleaned)
    val normCleanedWords = normCleaned.replace(Regex("""[^a-z0-9]+"""), " ").trim()
    if (normCleanedWords.isBlank() || !syllabusCoreEvalKeywordRegex.containsMatchIn(normCleanedWords)) {
        return null
    }
    val normTokens = normCleanedWords.split(Regex("""\s+""")).filter { it.isNotBlank() }
    if (normTokens.isEmpty() || normTokens.last() in syllabusDanglingTailWords) {
        return null
    }
    val normSource = normalizeSearchText(keywordSource)
    val norm = normCleaned
    val courseTag = buildCourseShortTag(course)
    val evalNumber = extractSyllabusEvalNumber(norm) ?: extractSyllabusEvalNumber(normSource)

    val (title, kind) = when {
        Regex("""\bexame\s+(?:especial|final)\b|\bexame\b""").containsMatchIn(norm) ->
            "EXAME ESPECIAL $courseTag" to "Prova"

        Regex("""\bsubstitutiva\b|\breposicao\b|\bprova\s+de\s+recuperacao\b""").containsMatchIn(norm) ->
            "PROVA SUBSTITUTIVA $courseTag" to "Prova"

        Regex("""\b(?:prova|avaliacao)\s+final\b""").containsMatchIn(norm) ->
            "PROVA FINAL $courseTag" to "Prova"

        Regex("""\bseminarios?\b""").containsMatchIn(norm) -> {
            val topic = extractDescriptiveTopic(
                cleaned = cleaned,
                kindKeywordRegex = Regex("""\bsemin[áa]rios?\b""", RegexOption.IGNORE_CASE),
                courseTag = courseTag,
                courseName = course.name
            )
            val formatted = when {
                topic != null -> "SEMINÁRIO DE ${topic.uppercase(PT_BR_LOCALE)}"
                evalNumber != null -> "SEMINÁRIO $evalNumber $courseTag"
                else -> "SEMINÁRIO $courseTag"
            }
            formatted to "Seminário"
        }

        Regex("""\btp\d*\b|\btrabalhos?\b""").containsMatchIn(norm) -> {
            val topic = extractDescriptiveTopic(
                cleaned = cleaned,
                kindKeywordRegex = Regex("""\btrabalhos?\b""", RegexOption.IGNORE_CASE),
                courseTag = courseTag,
                courseName = course.name
            )
            val formatted = when {
                topic != null -> "TRABALHO DE ${topic.uppercase(PT_BR_LOCALE)}"
                evalNumber != null -> "TRABALHO $evalNumber $courseTag"
                norm.contains("final") -> "TRABALHO FINAL $courseTag"
                else -> "TRABALHO $courseTag"
            }
            formatted to "Trabalho"
        }

        Regex("""\bprojetos?\b""").containsMatchIn(norm) -> {
            val topic = extractDescriptiveTopic(
                cleaned = cleaned,
                kindKeywordRegex = Regex("""\bprojetos?\b""", RegexOption.IGNORE_CASE),
                courseTag = courseTag,
                courseName = course.name
            )
            val formatted = when {
                topic != null -> "PROJETO DE ${topic.uppercase(PT_BR_LOCALE)}"
                evalNumber != null -> "PROJETO $evalNumber $courseTag"
                norm.contains("final") -> "PROJETO FINAL $courseTag"
                else -> "PROJETO $courseTag"
            }
            formatted to "Trabalho"
        }

        Regex("""\blistas?\b""").containsMatchIn(norm) -> {
            val formatted = if (evalNumber != null) "LISTA $evalNumber $courseTag" else "LISTA $courseTag"
            formatted to "Trabalho"
        }

        Regex("""\brelatorios?\b""").containsMatchIn(norm) -> {
            val topic = if (evalNumber == null) {
                extractDescriptiveTopic(
                    cleaned = cleaned,
                    kindKeywordRegex = Regex("""\brelat[óo]rios?\b""", RegexOption.IGNORE_CASE),
                    courseTag = courseTag,
                    courseName = course.name
                )
            } else null
            val formatted = when {
                evalNumber != null -> "RELATÓRIO $evalNumber $courseTag"
                topic != null -> "RELATÓRIO DE ${topic.uppercase(PT_BR_LOCALE)}"
                else -> "RELATÓRIO $courseTag"
            }
            formatted to "Trabalho"
        }

        Regex("""\bp[1-9]\b|\bprovas?\b""").containsMatchIn(norm) -> {
            val formatted = if (evalNumber != null) "PROVA $evalNumber $courseTag" else "PROVA $courseTag"
            formatted to "Prova"
        }

        Regex("""\b(?:testes?|quizzes?|quiz)\b""").containsMatchIn(norm) -> {
            val formatted = if (evalNumber != null) "TESTE $evalNumber $courseTag" else "TESTE $courseTag"
            formatted to "Prova"
        }

        Regex("""\bavaliac(?:ao|oes)\b""").containsMatchIn(norm) -> {
            val formatted = if (evalNumber != null) "PROVA $evalNumber $courseTag" else "AVALIAÇÃO $courseTag"
            formatted to "Prova"
        }

        Regex("""\bapresentacao\b""").containsMatchIn(norm) -> {
            val topic = extractDescriptiveTopic(
                cleaned = cleaned,
                kindKeywordRegex = Regex("""\bapresenta[çc][aã]o\b""", RegexOption.IGNORE_CASE),
                courseTag = courseTag,
                courseName = course.name
            )
            val formatted = when {
                topic != null -> "SEMINÁRIO DE ${topic.uppercase(PT_BR_LOCALE)}"
                evalNumber != null -> "SEMINÁRIO $evalNumber $courseTag"
                else -> "SEMINÁRIO $courseTag"
            }
            formatted to "Seminário"
        }

        else -> return null
    }

    val finalTitle = title
        .uppercase(PT_BR_LOCALE)
        .replace(Regex("""\s+"""), " ")
        .trim()
        .let { if (it.length > 64) it.take(64).trimEnd() else it }

    if (finalTitle.isBlank()) return null
    return finalTitle to kind
}


internal fun matchSyllabusToCourse(course: CourseSeed, rawText: String, notesByCourseId: Map<String, String>): SyllabusCourseMatchResult {
    val lines = rawText.lines()
        .map { it.trim().replace(Regex("""\s+"""), " ") }
        .filter { it.isNotEmpty() }
    val headerLines = lines.take(40)
    val nonPrereqLines = lines.filterNot { SYLLABUS_PREREQ_LINE_REGEX.containsMatchIn(normalizeSearchText(it)) }
    val nonPrereqHeaderLines = headerLines.filterNot { SYLLABUS_PREREQ_LINE_REGEX.containsMatchIn(normalizeSearchText(it)) }

    val notes = notesByCourseId[course.courseId] ?: course.initialNotes
    val targetRealCode = extractRealUfopCourseCode(course.code)
        ?: Regex("""C[oó]digo:\s*([A-Z]{2,4}\d{3,4}[A-Z]?)""", RegexOption.IGNORE_CASE)
            .find(notes)?.groupValues?.getOrNull(1)?.let { extractRealUfopCourseCode(it) }
        ?: UFOP_COURSE_CODE_PATTERN.find(course.name)?.groupValues?.getOrNull(1)?.let { extractRealUfopCourseCode(it) }
        ?: Regex("""^([a-z]{2,4}\d{3,4}[a-z]?)(?:_|$)""", RegexOption.IGNORE_CASE)
            .find(course.courseId.trim())?.groupValues?.getOrNull(1)?.let { extractRealUfopCourseCode(it) }

    fun findCodesIn(sourceLines: List<String>): List<String> = sourceLines
        .flatMap { line -> UFOP_COURSE_CODE_PATTERN.findAll(line).mapNotNull { extractRealUfopCourseCode(it.groupValues[1]) } }
        .distinct()

    val headerCodes = findCodesIn(nonPrereqHeaderLines)
    val bodyCodes = if (headerCodes.isNotEmpty()) headerCodes else findCodesIn(nonPrereqLines)

    val targetCodeMatched = targetRealCode != null && (
        headerCodes.any { it.equals(targetRealCode, ignoreCase = true) } ||
            bodyCodes.any { it.equals(targetRealCode, ignoreCase = true) }
        )

    var detectedHeaderCode: String? = headerCodes.firstOrNull() ?: bodyCodes.firstOrNull()
    var detectedHeaderTitle: String? = null

    val explicitLabelRegex = Regex(
        """^(?:disciplina|componente\s+curricular|mat[eé]ria|unidade\s+curricular|plano\s+de\s+ensino(?:\s+d[aeo]\s+disciplina)?)\s*[:\-–]\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )
    val codeTitleLineRegex = Regex(
        """^([A-Z]{2,4}\d{3,4}[A-Z]?)\s*[-–—:]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )

    for (i in nonPrereqHeaderLines.indices) {
        val line = nonPrereqHeaderLines[i]
        val explicitMatch = explicitLabelRegex.find(line)
        if (explicitMatch != null) {
            var candidate = explicitMatch.groupValues[1].trim()
            if (candidate.isEmpty() && i + 1 < nonPrereqHeaderLines.size) {
                val next = nonPrereqHeaderLines[i + 1]
                if (!next.contains(":")) candidate = next
            }
            if (candidate.isNotEmpty()) {
                val codeInCandidate = UFOP_COURSE_CODE_PATTERN.find(candidate)?.groupValues?.getOrNull(1)
                    ?.let { extractRealUfopCourseCode(it) }
                if (codeInCandidate != null) detectedHeaderCode = codeInCandidate
                val stripped = candidate
                    .replace(Regex("""\b[A-Z]{2,4}\d{3,4}[A-Z]?\b\s*[-–—:]?\s*""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\s+(?:turma|carga\s+hor[áa]ria|semestre|per[íi]odo|ch\b)\b.*$""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\s*\(\s*\d+\s*h\s*\)\s*$""", RegexOption.IGNORE_CASE), "")
                    .trim(' ', '-', '–', '—', ':', '|', ';')
                if (stripped.length >= 3) {
                    detectedHeaderTitle = normalizeUfopPortugueseText(stripped)
                    break
                }
            }
        }
        val codeTitleMatch = codeTitleLineRegex.find(line)
        if (codeTitleMatch != null) {
            val codeInLine = extractRealUfopCourseCode(codeTitleMatch.groupValues[1])
            if (codeInLine != null) {
                detectedHeaderCode = codeInLine
                val stripped = codeTitleMatch.groupValues[2]
                    .replace(Regex("""\s+(?:turma|carga\s+hor[áa]ria|semestre|per[íi]odo|ch\b)\b.*$""", RegexOption.IGNORE_CASE), "")
                    .trim(' ', '-', '–', '—', ':', '|', ';')
                if (stripped.length >= 3) {
                    detectedHeaderTitle = normalizeUfopPortugueseText(stripped)
                    break
                }
            }
        }
    }

    val detectedOtherCode = (headerCodes + bodyCodes).firstOrNull { code ->
        targetRealCode == null || !code.equals(targetRealCode, ignoreCase = true)
    }

    val strippedCourseName = course.name
        .replace(Regex("""^\s*[A-Z]{2,4}\d{3,4}[A-Z]?\s*[-–—:]?\s*""", RegexOption.IGNORE_CASE), "")
        .trim()
    val nameStopwords = setOf(
        "das", "dos", "com", "para", "por", "que", "uma", "uns", "umas",
        "nos", "nas", "aos", "sobre", "entre", "sem", "sob"
    )
    val allNameTokens = normalizeSearchText(strippedCourseName)
        .split(Regex("""[^a-z0-9]+"""))
        .filter { it.isNotBlank() }
    val significantTokens = allNameTokens
        .filter { it.length >= 3 && it !in nameStopwords }
        .distinct()
        .ifEmpty { allNameTokens.distinct() }

    val searchableNorm = normalizeSearchText(nonPrereqLines.joinToString(" "))
    val matchedTokensCount = significantTokens.count { token ->
        Regex("""\b${Regex.escape(token)}\b""").containsMatchIn(searchableNorm)
    }
    val hasNameMatch = significantTokens.isNotEmpty() &&
        (matchedTokensCount * 2 >= significantTokens.size)

    val targetLevel = extractCourseLevelNumeral(course.name)
    val headerLevel = detectedHeaderTitle?.let { extractCourseLevelNumeral(it) }
    val hasLevelConflict = !targetCodeMatched &&
        targetLevel != null &&
        headerLevel != null &&
        targetLevel != headerLevel

    val declaredOtherCodeMismatch = targetRealCode != null && !targetCodeMatched && detectedOtherCode != null
    val neitherCodeNorNameMatched = !targetCodeMatched && !hasNameMatch

    val matched = !(declaredOtherCodeMismatch || neitherCodeNorNameMatched || hasLevelConflict)
    return SyllabusCourseMatchResult(
        matched = matched,
        detectedCode = if (matched) (targetRealCode ?: detectedHeaderCode) else (detectedOtherCode ?: detectedHeaderCode),
        detectedTitle = detectedHeaderTitle
    )
}


internal fun buildCourseShortTag(
    course: CourseSeed,
    resolvedCourseCode: String = deriveCourseCodeFallback(course.code, course.initialNotes, course.courseId, course.name)
): String {
    val strippedCode = course.name
        .replace(Regex("""^\s*[A-Z]{2,4}\d{3,4}[A-Z]?\s*[-–—:]?\s*""", RegexOption.IGNORE_CASE), "")
        .trim()
    val norm = normalizeSearchText(strippedCode)

    fun extractTrailingRoman(): String? {
        val rawNum = Regex("""\b(i{1,3}|iv|v|1|2|3|4)\b""", RegexOption.IGNORE_CASE)
            .findAll(norm)
            .lastOrNull()
            ?.value
            ?.uppercase()
            ?: return null
        return when (rawNum) {
            "1" -> "I"
            "2" -> "II"
            "3" -> "III"
            "4" -> "IV"
            else -> rawNum
        }
    }

    when {
        norm.contains("equacoes diferenciais ordinarias") ||
            norm.contains("equacao diferencial ordinaria") ||
            norm == "edo" -> return "EDO"
        norm.contains("equacoes diferenciais parciais") || norm == "edp" -> return "EDP"
        norm.contains("geometria analitica e algebra linear") || norm == "gaal" -> return "GAAL"
        norm.contains("calculo diferencial e integral") -> {
            val roman = extractTrailingRoman()
            return if (!roman.isNullOrBlank()) "CÁLCULO $roman" else "CÁLCULO"
        }
        norm.contains("estruturas de dados") || norm.contains("estrutura de dados") -> {
            val roman = extractTrailingRoman()
            return if (!roman.isNullOrBlank()) "ED $roman" else "ED"
        }
        norm.contains("banco de dados") -> {
            val roman = extractTrailingRoman()
            return if (!roman.isNullOrBlank()) "BD $roman" else "BD"
        }
        norm.contains("sistemas operacionais") -> return "SO"
        norm.contains("inteligencia artificial") -> return "IA"
        norm.contains("programacao orientada a objetos") -> return "POO"
    }

    val trimmedPrefix = strippedCode
        .replace(
            Regex(
                """^(?:introdu[çc][aã]o\s+(?:[àa]s?|[àa]os?|de|da|do)\s+|fundamentos\s+(?:de|da|do|das|dos)\s+|an[áa]lise\s+(?:de|da|do|das|dos)\s+|princ[íi]pios\s+(?:de|da|do|das|dos)\s+|no[çc][õo]es\s+(?:de|da|do|das|dos)\s+|elementos\s+(?:de|da|do|das|dos)\s+)""",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifBlank { strippedCode }

    val upper = trimmedPrefix.uppercase(PT_BR_LOCALE)
    if (upper.length <= 24) return upper

    val compact = upper.split(Regex("""\s+""")).filter { it.isNotBlank() }.take(3).joinToString(" ")
    if (compact.length <= 24) return compact
    return resolvedCourseCode.uppercase(PT_BR_LOCALE).ifBlank { compact.take(24).trimEnd() }
}
