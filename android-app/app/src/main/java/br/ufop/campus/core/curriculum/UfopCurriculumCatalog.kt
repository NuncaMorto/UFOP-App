package br.ufop.campus.core.curriculum

import android.content.Context
import br.ufop.campus.core.ffi.normalizeUfopPortugueseText
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

data class UfopCurriculumSubject(
    val code: String,
    val name: String,
    val departmentId: String,
    val departmentCode: String,
    val period: Int, // 1..12; 0 = Atividades Curriculares / Estágio / TCC
    val type: String, // "Obrigatória" or "Atividade"
    val chs: Int,
    val che: Int,
    val cha: Int,
    val weeklyTheory: Int,
    val weeklyPractice: Int,
    val prerequisites: List<String>
)

data class UfopCourseCurriculum(
    val code: String,
    val name: String,
    val degree: String,
    val campus: String,
    val curriculumVersion: String,
    val chMandatory: Int,
    val chOptional: Int,
    val chElective: Int,
    val chExtension: Int,
    val chActivities: Int,
    val chTotal: Int,
    val status: String,
    val subjects: List<UfopCurriculumSubject>
)

data class CurriculumCompatibilityStatus(
    val isLegacyMatrixMismatch: Boolean,
    val studentEntryLabel: String?,
    val matrixVersionLabel: String,
    val unmatchedTranscriptCodesCount: Int
)

private const val MATRIX_REFERENCE_VERSION_LABEL = "2026/2"
private const val MATRIX_CUTOFF_YEAR = 2026
private const val MATRIX_CUTOFF_SEMESTER = 1

private val ANCHORED_ENROLLMENT_3_PART_REGEX =
    Regex("""^\s*(\d{2}|\d{4})\s*[./-]\s*([12])\s*[./-]\s*\d{2,6}\s*$""")
private val EMBEDDED_ENROLLMENT_3_PART_REGEX =
    Regex("""(?:^|\D)(\d{2}|\d{4})\s*[./-]\s*([12])\s*[./-]\s*\d{2,6}(?:\D|$)""")
private val COMPACT_ENROLLMENT_9_DIGIT_REGEX =
    Regex("""^\s*((?:19|20)\d{2})([12])\d{4}\s*$""")
private val COMPACT_ENROLLMENT_7_DIGIT_REGEX =
    Regex("""^\s*(\d{2})([12])\d{4}\s*$""")
private val SEMESTER_LABEL_REGEX =
    Regex("""(?:^|\D)(\d{4}|\d{2})\s*[./-]\s*([12])(?:\D|$)""")

private fun normalizeEnrollmentYear(rawYear: String): Int? {
    val parsed = rawYear.toIntOrNull() ?: return null
    val fullYear = when (rawYear.length) {
        4 -> parsed
        2 -> if (parsed >= 69) 1900 + parsed else 2000 + parsed
        else -> return null
    }
    return fullYear.takeIf { it in 1969..2099 }
}

private fun parseSemesterYearTerm(raw: String?): Pair<Int, Int>? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    val match = SEMESTER_LABEL_REGEX.find(trimmed) ?: return null
    val year = normalizeEnrollmentYear(match.groupValues[1]) ?: return null
    val semester = match.groupValues[2].toIntOrNull()?.takeIf { it == 1 || it == 2 } ?: return null
    return year to semester
}

fun parseEnrollmentYearSemester(registrationNumber: String): Pair<Int, Int>? {
    val trimmed = registrationNumber.trim()
    if (trimmed.isEmpty()) return null

    val match = ANCHORED_ENROLLMENT_3_PART_REGEX.matchEntire(trimmed)
        ?: EMBEDDED_ENROLLMENT_3_PART_REGEX.find(trimmed)
        ?: COMPACT_ENROLLMENT_9_DIGIT_REGEX.matchEntire(trimmed)
        ?: COMPACT_ENROLLMENT_7_DIGIT_REGEX.matchEntire(trimmed)
        ?: return null

    val year = normalizeEnrollmentYear(match.groupValues[1]) ?: return null
    val semester = match.groupValues[2].toIntOrNull()?.takeIf { it == 1 || it == 2 } ?: return null
    return year to semester
}

fun checkCurriculumCompatibility(
    registrationNumber: String,
    earliestTranscriptSemester: String?,
    transcriptCourseCodes: Set<String>,
    course: UfopCourseCurriculum
): CurriculumCompatibilityStatus {
    val regEntry = parseEnrollmentYearSemester(registrationNumber)
    val txEntry = parseSemesterYearTerm(earliestTranscriptSemester)

    val effectiveEntry = listOfNotNull(regEntry, txEntry)
        .minWithOrNull(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })

    val studentEntryLabel = effectiveEntry?.let { (year, sem) -> "$year/$sem" }

    val isLegacyByEntry = effectiveEntry != null && (
        effectiveEntry.first < MATRIX_CUTOFF_YEAR ||
            (effectiveEntry.first == MATRIX_CUTOFF_YEAR && effectiveEntry.second < MATRIX_CUTOFF_SEMESTER)
        )

    val normalizedTranscriptCodes = transcriptCourseCodes
        .asSequence()
        .map { it.trim().uppercase() }
        .filter { it.isNotEmpty() }
        .toSet()

    val matrixCodes = course.subjects
        .asSequence()
        .map { it.code.trim().uppercase() }
        .filter { it.isNotEmpty() }
        .toSet()

    val unmatchedCount = if (matrixCodes.isEmpty() || normalizedTranscriptCodes.isEmpty()) {
        0
    } else {
        normalizedTranscriptCodes.count { it !in matrixCodes }
    }

    val unmatchedRatio = if (normalizedTranscriptCodes.isEmpty()) {
        0.0
    } else {
        unmatchedCount.toDouble() / normalizedTranscriptCodes.size.toDouble()
    }

    val hasRelevantCodeDivergence = matrixCodes.isNotEmpty() && (
        (unmatchedCount >= 3 && unmatchedRatio >= 0.50) ||
            (unmatchedCount >= 5 && unmatchedRatio >= 0.35)
        )

    return CurriculumCompatibilityStatus(
        isLegacyMatrixMismatch = isLegacyByEntry || hasRelevantCodeDivergence,
        studentEntryLabel = studentEntryLabel,
        matrixVersionLabel = MATRIX_REFERENCE_VERSION_LABEL,
        unmatchedTranscriptCodesCount = unmatchedCount
    )
}

object UfopCurriculumCatalog {

    private const val ASSET_FILE_NAME = "ufop_matrizes_2026_2.json"

    private val DIACRITICS_REGEX = Regex("\\p{M}+")
    private val NON_ALNUM_REGEX = Regex("[^a-z0-9]+")
    private val PAREN_SUFFIX_REGEX = Regex("\\s*\\([^)]*\\)")
    private val STOP_WORDS = setOf(
        "de", "da", "do", "das", "dos", "e", "em", "na", "no", "nas", "nos",
        "para", "por", "com", "a", "o", "as", "os", "curso"
    )

    private val loadLock = Any()

    @Volatile
    private var cachedCourses: List<UfopCourseCurriculum>? = null

    fun getAllCourses(context: Context): List<UfopCourseCurriculum> {
        cachedCourses?.let { return it }
        return synchronized(loadLock) {
            cachedCourses?.let { return@synchronized it }
            val rawJson = runCatching {
                context.assets.open(ASSET_FILE_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrDefault("")
            val parsed = if (rawJson.isBlank()) emptyList() else loadFromJsonString(rawJson)
            cachedCourses = parsed
            parsed
        }
    }

    fun findCourse(
        context: Context,
        courseNameOrCode: String,
        campusHint: String? = null
    ): UfopCourseCurriculum? = findCourse(getAllCourses(context), courseNameOrCode, campusHint)

    fun searchCourses(
        context: Context,
        query: String
    ): List<UfopCourseCurriculum> = searchCourses(getAllCourses(context), query)

    fun parseEnrollmentYearSemester(registrationNumber: String): Pair<Int, Int>? =
        br.ufop.campus.core.curriculum.parseEnrollmentYearSemester(registrationNumber)

    fun checkCurriculumCompatibility(
        registrationNumber: String,
        earliestTranscriptSemester: String?,
        transcriptCourseCodes: Set<String>,
        course: UfopCourseCurriculum
    ): CurriculumCompatibilityStatus =
        br.ufop.campus.core.curriculum.checkCurriculumCompatibility(
            registrationNumber = registrationNumber,
            earliestTranscriptSemester = earliestTranscriptSemester,
            transcriptCourseCodes = transcriptCourseCodes,
            course = course
        )

    fun loadFromJsonString(rawJson: String): List<UfopCourseCurriculum> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return emptyList()
        val coursesArray: JSONArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            root.optJSONArray("courses") ?: JSONArray()
        }

        val result = ArrayList<UfopCourseCurriculum>(coursesArray.length())
        for (i in 0 until coursesArray.length()) {
            val obj = coursesArray.optJSONObject(i) ?: continue
            val code = obj.optString("code", "").trim().uppercase()
            val name = obj.optString("name", "").trim()
            if (code.isEmpty() || name.isEmpty()) continue

            val subjectsArr = obj.optJSONArray("subjects")
            val subjects = if (subjectsArr != null && subjectsArr.length() > 0) {
                ArrayList<UfopCurriculumSubject>(subjectsArr.length()).apply {
                    for (j in 0 until subjectsArr.length()) {
                        val sObj = subjectsArr.optJSONObject(j) ?: continue
                        val sCode = sObj.optString("code", "").trim().uppercase()
                        val rawSubjectName = sObj.optString("name", "").trim()
                        if (sCode.isEmpty() || rawSubjectName.isEmpty()) continue
                        val normalizedSubjectName = normalizeUfopPortugueseText(rawSubjectName)
                        val prereqArr = sObj.optJSONArray("prerequisites")
                        val prereqs = if (prereqArr != null && prereqArr.length() > 0) {
                            List(prereqArr.length()) { k -> prereqArr.optString(k, "").trim() }
                                .filter { it.isNotEmpty() }
                        } else {
                            emptyList()
                        }
                        add(
                            UfopCurriculumSubject(
                                code = sCode,
                                name = normalizedSubjectName,
                                departmentId = sObj.optString("departmentId", "ufop").trim().lowercase()
                                    .ifEmpty { "ufop" },
                                departmentCode = sObj.optString("departmentCode", "UFOP").trim().uppercase()
                                    .ifEmpty { "UFOP" },
                                period = sObj.optInt("period", 0).coerceIn(0, 12),
                                type = sObj.optString("type", "Obrigatória").trim().ifEmpty { "Obrigatória" },
                                chs = sObj.optInt("chs", 0).coerceAtLeast(0),
                                che = sObj.optInt("che", 0).coerceAtLeast(0),
                                cha = sObj.optInt("cha", 0).coerceAtLeast(0),
                                weeklyTheory = sObj.optInt("weeklyTheory", 0).coerceAtLeast(0),
                                weeklyPractice = sObj.optInt("weeklyPractice", 0).coerceAtLeast(0),
                                prerequisites = prereqs
                            )
                        )
                    }
                }
            } else {
                emptyList()
            }

            result.add(
                UfopCourseCurriculum(
                    code = code,
                    name = name,
                    degree = obj.optString("degree", "").trim(),
                    campus = obj.optString("campus", "").trim(),
                    curriculumVersion = obj.optString("curriculumVersion", "").trim(),
                    chMandatory = obj.optInt("chMandatory", 0).coerceAtLeast(0),
                    chOptional = obj.optInt("chOptional", 0).coerceAtLeast(0),
                    chElective = obj.optInt("chElective", 0).coerceAtLeast(0),
                    chExtension = obj.optInt("chExtension", 0).coerceAtLeast(0),
                    chActivities = obj.optInt("chActivities", 0).coerceAtLeast(0),
                    chTotal = obj.optInt("chTotal", 0).coerceAtLeast(0),
                    status = obj.optString("status", "").trim(),
                    subjects = subjects
                )
            )
        }
        return result
    }

    fun findCourse(
        courses: List<UfopCourseCurriculum>,
        courseNameOrCode: String,
        campusHint: String? = null
    ): UfopCourseCurriculum? {
        if (courses.isEmpty()) return null
        val rawQuery = courseNameOrCode.trim()
        if (rawQuery.isEmpty()) return null

        // 1. Exact course code match (e.g. "AUT", "COM", "PJM", "D04", "IA")
        courses.firstOrNull { it.code.equals(rawQuery, ignoreCase = true) }?.let { return it }

        val normQuery = normalizeForMatch(normalizeUfopPortugueseText(rawQuery))
        val normQueryRaw = normalizeForMatch(rawQuery)
        if (normQuery.isEmpty() && normQueryRaw.isEmpty()) return null
        val effectiveQuery = normQuery.ifEmpty { normQueryRaw }
        val queryBase = normalizeForMatch(stripParentheses(rawQuery))

        val effectiveCampusKey = resolveCampusKey(campusHint) ?: extractCampusFromText(effectiveQuery)
        val queryWantsEad = effectiveQuery.contains("ead") || effectiveQuery.contains("distancia")
        val queryWantsLic = effectiveQuery.contains("licenciatura")
        val queryWantsBach = effectiveQuery.contains("bacharelado")

        fun tieBreakScore(course: UfopCourseCurriculum): Int {
            var score = 0
            if (effectiveCampusKey != null && campusMatches(course, effectiveCampusKey)) score += 100
            val courseIsEad = isEadCourse(course)
            if (queryWantsEad && courseIsEad) score += 45
            if (!queryWantsEad && !courseIsEad) score += 20
            val normDegree = normalizeForMatch(course.degree)
            if (queryWantsLic && normDegree.contains("licenciatura")) score += 35
            if (queryWantsBach && normDegree.contains("bacharelado")) score += 35
            if (course.subjects.isNotEmpty()) score += 10
            return score
        }

        // 2. Exact full normalized name match
        val exactFullMatches = courses.filter { course ->
            val normName = normalizeForMatch(course.name)
            normName == effectiveQuery || normName == normQueryRaw
        }
        if (exactFullMatches.isNotEmpty()) {
            return exactFullMatches.maxByOrNull { tieBreakScore(it) }
        }

        // 3. Exact base-name match (ignoring parenthetical suffixes like "(Ouro Preto)" / "(Bacharelado)")
        val exactBaseMatches = courses.filter { course ->
            val normBase = normalizeForMatch(stripParentheses(course.name))
            normBase == effectiveQuery || normBase == normQueryRaw ||
                (queryBase.isNotEmpty() && normBase == queryBase)
        }
        if (exactBaseMatches.isNotEmpty()) {
            return exactBaseMatches.maxByOrNull { tieBreakScore(it) }
        }

        // 4. Token-overlap & substring match with campus & degree disambiguation
        val queryTokens = tokenize(effectiveQuery)
        if (queryTokens.isEmpty()) return null

        var bestCourse: UfopCourseCurriculum? = null
        var bestScore = 0.0

        for (course in courses) {
            val normName = normalizeForMatch(course.name)
            val normBase = normalizeForMatch(stripParentheses(course.name))
            val normDegree = normalizeForMatch(course.degree)
            val normCampus = normalizeForMatch(course.campus)
            val nameTokens = tokenize(normName)
            val allCourseTokens = (nameTokens + tokenize(normDegree) + tokenize(normCampus) + course.code.lowercase()).toSet()
            val nameTokenSet = nameTokens.toSet()

            val matchedQueryTokens = queryTokens.count { qt ->
                qt in allCourseTokens || allCourseTokens.any { ct ->
                    (qt.length >= 4 && ct.startsWith(qt)) || (ct.length >= 4 && qt.startsWith(ct))
                }
            }
            val matchedNameTokens = nameTokenSet.count { nt ->
                nt in queryTokens || queryTokens.any { qt ->
                    (qt.length >= 4 && nt.startsWith(qt)) || (nt.length >= 4 && qt.startsWith(nt))
                }
            }

            val queryCoverage = matchedQueryTokens.toDouble() / queryTokens.size.toDouble()
            val nameCoverage = if (nameTokenSet.isNotEmpty()) {
                matchedNameTokens.toDouble() / nameTokenSet.size.toDouble()
            } else {
                0.0
            }

            val directSubstring = normName.contains(effectiveQuery) ||
                effectiveQuery.contains(normName) ||
                (normBase.length >= 4 && effectiveQuery.contains(normBase))

            if (!directSubstring && (matchedQueryTokens == 0 || queryCoverage < 0.6)) continue
            if (!directSubstring && matchedNameTokens == 0) continue

            var score = (queryCoverage * 100.0) + (nameCoverage * 65.0)
            if (directSubstring) score += 35.0
            if (effectiveQuery.contains(normBase) && normBase.length >= 4) score += 20.0
            if (effectiveCampusKey != null && campusMatches(course, effectiveCampusKey)) score += 60.0
            if (queryWantsLic && normDegree.contains("licenciatura")) score += 35.0
            if (queryWantsBach && normDegree.contains("bacharelado")) score += 35.0
            val courseIsEad = isEadCourse(course)
            if (queryWantsEad && courseIsEad) score += 30.0
            if (!queryWantsEad && courseIsEad) score -= 18.0
            if (course.subjects.isNotEmpty()) score += 5.0

            if (score > bestScore) {
                bestScore = score
                bestCourse = course
            }
        }

        return if (bestScore >= 65.0) bestCourse else null
    }

    fun searchCourses(
        courses: List<UfopCourseCurriculum>,
        query: String
    ): List<UfopCourseCurriculum> {
        val raw = query.trim()
        if (raw.isEmpty()) return courses
        val normQuery = normalizeForMatch(raw)
        if (normQuery.isEmpty()) return courses
        val queryTokens = tokenize(normQuery)

        return courses.mapNotNull { course ->
            val normCode = course.code.lowercase()
            val normName = normalizeForMatch(course.name)
            val normCampus = normalizeForMatch(course.campus)
            val normDegree = normalizeForMatch(course.degree)
            val haystack = "$normName $normCode $normCampus $normDegree"

            val score = when {
                normCode == normQuery -> 1000
                normName.startsWith(normQuery) -> 800
                normCode.startsWith(normQuery) -> 750
                normName.contains(normQuery) -> 600
                normCampus.contains(normQuery) -> 450
                normDegree.contains(normQuery) -> 400
                queryTokens.isNotEmpty() && queryTokens.all { tok -> haystack.contains(tok) } -> 350
                else -> 0
            }
            if (score > 0) course to score else null
        }
            .sortedWith(compareByDescending<Pair<UfopCourseCurriculum, Int>> { it.second }.thenBy { it.first.name })
            .map { it.first }
    }

    private fun stripParentheses(text: String): String =
        PAREN_SUFFIX_REGEX.replace(text, " ").trim()

    private fun normalizeForMatch(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
            .lowercase()
            .replace(NON_ALNUM_REGEX, " ")
            .trim()

    private fun tokenize(normText: String): List<String> =
        normText.split(' ')
            .filter { it.isNotEmpty() && it !in STOP_WORDS }

    private fun resolveCampusKey(campusHint: String?): String? {
        if (campusHint.isNullOrBlank()) return null
        return extractCampusFromText(normalizeForMatch(campusHint))
    }

    private fun extractCampusFromText(normText: String): String? = when {
        normText.contains("monlevade") || normText.contains("icea") || normText == "jm" -> "joao monlevade"
        normText.contains("mariana") || normText.contains("ichs") || normText.contains("icsa") -> "mariana"
        normText.contains("ouro preto") || normText.contains("cruzeiro") || normText == "op" -> "ouro preto"
        normText.contains("ead") || normText.contains("cead") -> "ead"
        else -> null
    }

    private fun isEadCourse(course: UfopCourseCurriculum): Boolean {
        val combined = normalizeForMatch("${course.name} ${course.degree} ${course.campus}")
        return combined.contains("ead")
    }

    private fun campusMatches(course: UfopCourseCurriculum, campusKey: String): Boolean {
        if (campusKey == "ead") return isEadCourse(course)
        val normCampus = normalizeForMatch(course.campus)
        val normName = normalizeForMatch(course.name)
        return normCampus.contains(campusKey) || normName.contains(campusKey)
    }
}
