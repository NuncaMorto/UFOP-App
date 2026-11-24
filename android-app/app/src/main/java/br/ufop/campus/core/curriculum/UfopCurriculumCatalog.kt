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
    val subjects: List<UfopCurriculumSubject>,
    val effectiveFromYear: Int = 2026,
    val effectiveFromSemester: Int = 1,
    val effectiveUntilYear: Int? = null,
    val effectiveUntilSemester: Int? = null,
    val sourceUrl: String? = null
) {
    fun containsEntryTerm(year: Int, semester: Int): Boolean {
        val entryKey = year * 10 + semester
        val fromKey = effectiveFromYear * 10 + effectiveFromSemester
        if (entryKey < fromKey) return false
        val untilYear = effectiveUntilYear ?: return true
        val untilSem = effectiveUntilSemester ?: 2
        val untilKey = untilYear * 10 + untilSem
        return entryKey <= untilKey
    }

    fun isEntryBeforeEffectiveStart(year: Int, semester: Int): Boolean {
        val entryKey = year * 10 + semester
        val fromKey = effectiveFromYear * 10 + effectiveFromSemester
        return entryKey < fromKey
    }
}

data class UfopLegacyReference(
    val id: String,
    val courseCodes: List<String>,
    val courseName: String,
    val academicUnit: String,
    val versionTitle: String,
    val details: String,
    val accessType: String,
    val url: String,
    val effectiveFromYear: Int? = null,
    val effectiveFromSemester: Int? = null,
    val effectiveUntilYear: Int? = null,
    val effectiveUntilSemester: Int? = null,
    val entryRuleSummary: String = ""
) {
    fun matchesEntryTerm(entryYear: Int, entrySemester: Int? = null): Boolean {
        if (effectiveFromYear != null) {
            if (entrySemester != null) {
                val entryKey = entryYear * 10 + entrySemester
                val fromKey = effectiveFromYear * 10 + (effectiveFromSemester ?: 1)
                if (entryKey < fromKey) return false
            } else if (entryYear < effectiveFromYear) {
                return false
            }
        }
        if (effectiveUntilYear != null) {
            if (entrySemester != null) {
                val entryKey = entryYear * 10 + entrySemester
                val untilKey = effectiveUntilYear * 10 + (effectiveUntilSemester ?: 2)
                if (entryKey > untilKey) return false
            } else if (entryYear > effectiveUntilYear) {
                return false
            }
        }
        return true
    }
}

data class UfopCourseOfficialLinks(
    val courseCode: String,
    val officialMatrixUrl: String? = null,
    val drivePdfUrl: String? = null,
    val sheetStatus: String = ""
)

data class UfopLegacyCatalogData(
    val schemaVersion: Int = 1,
    val historicalCurricula: List<UfopCourseCurriculum> = emptyList(),
    val legacyReferences: List<UfopLegacyReference> = emptyList(),
    val courseOfficialLinks: Map<String, UfopCourseOfficialLinks> = emptyMap()
)

data class CurriculumCompatibilityStatus(
    val isLegacyMatrixMismatch: Boolean,
    val studentEntryLabel: String?,
    val matrixVersionLabel: String,
    val unmatchedTranscriptCodesCount: Int
)

private const val MATRIX_REFERENCE_VERSION_LABEL = "2026/2"

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

    val isLegacyByEntry = effectiveEntry != null &&
        course.isEntryBeforeEffectiveStart(effectiveEntry.first, effectiveEntry.second)

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
        matrixVersionLabel = course.curriculumVersion.ifBlank { MATRIX_REFERENCE_VERSION_LABEL },
        unmatchedTranscriptCodesCount = unmatchedCount
    )
}

object UfopCurriculumCatalog {

    private const val ASSET_FILE_NAME = "ufop_matrizes_2026_2.json"
    private const val LEGACY_ASSET_FILE_NAME = "ufop_matrizes_antigas.json"

    private val DIACRITICS_REGEX = Regex("\\p{M}+")
    private val NON_ALNUM_REGEX = Regex("[^a-z0-9]+")
    private val PAREN_SUFFIX_REGEX = Regex("\\s*\\([^)]*\\)")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val CURRICULUM_NUMBER_REGEX = Regex("""curr[ií]culo\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val STOP_WORDS = setOf(
        "de", "da", "do", "das", "dos", "e", "em", "na", "no", "nas", "nos",
        "para", "por", "com", "a", "o", "as", "os", "curso"
    )

    private val COURSE_VERSION_COMPARATOR: Comparator<UfopCourseCurriculum> =
        compareBy<UfopCourseCurriculum> { it.effectiveFromYear }
            .thenBy { it.effectiveFromSemester }
            .thenBy { extractCurriculumNumber(it.curriculumVersion) ?: Int.MAX_VALUE }
            .thenBy { it.curriculumVersion }

    private val loadLock = Any()

    @Volatile
    private var cachedCourses: List<UfopCourseCurriculum>? = null

    @Volatile
    private var cachedLegacyCatalog: UfopLegacyCatalogData? = null

    @Volatile
    private var cachedMergedHistoricalByCode: Map<String, List<UfopCourseCurriculum>>? = null

    @Volatile
    internal var legacyCatalogRevision: Int = 0
        private set

    internal fun extractCurriculumNumber(rawVersion: String): Int? =
        CURRICULUM_NUMBER_REGEX.find(rawVersion)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun normalizeVersionKey(rawVersion: String): String =
        rawVersion.trim().lowercase().replace(WHITESPACE_REGEX, " ")

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key, "").trim()
        return value.takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return when (val raw = opt(key)) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull()
            else -> null
        }
    }

    private fun mapDepartmentCodeForSubjectCode(subjectCode: String): String {
        val prefix = subjectCode.trim().takeWhile { it.isLetter() }.uppercase()
        return when (prefix) {
            "BCC" -> "DECOM"
            "ARQ" -> "DEARQ"
            "CSO" -> "DECSO"
            "MTM" -> "DEMAT"
            "QUI" -> "DEQUI"
            "EST" -> "DEEST"
            "FIS" -> "DEFIS"
            "CAT" -> "DECAT"
            "MEC" -> "DEMEC"
            "CIV" -> "DECIV"
            "MET" -> "DEMET"
            "MIN" -> "DEMIN"
            "DIR" -> "DEDIR"
            "PRO" -> "DEPRO"
            "AMB" -> "DEAMB"
            else -> "UFOP"
        }
    }

    val engControleAutomacao2024_1: UfopCourseCurriculum by lazy {
        val subjects = DynamicCurriculumCatalog.engControleAutomacao2024_1.courses
            .filter { !it.isElective }
            .map { node ->
                val deptCode = mapDepartmentCodeForSubjectCode(node.code)
                UfopCurriculumSubject(
                    code = node.code,
                    name = node.name,
                    departmentId = deptCode.lowercase(),
                    departmentCode = deptCode,
                    period = node.period,
                    type = "Obrigatória",
                    chs = node.workloadHours,
                    che = 0,
                    cha = node.workloadHours,
                    weeklyTheory = (node.workloadHours / 15).coerceAtLeast(1),
                    weeklyPractice = 0,
                    prerequisites = node.prerequisites
                )
            }

        UfopCourseCurriculum(
            code = "AUT",
            name = "Engenharia de Controle e Automação",
            degree = "Bacharelado",
            campus = "Ouro Preto",
            curriculumVersion = "2024/1 (Currículo 2)",
            chMandatory = 2670,
            chOptional = 0,
            chElective = 300,
            chExtension = 0,
            chActivities = 640,
            chTotal = 3610,
            status = "Vigente para ingressantes 2024/1–2025/2",
            subjects = subjects,
            effectiveFromYear = 2024,
            effectiveFromSemester = 1,
            effectiveUntilYear = 2025,
            effectiveUntilSemester = 2
        )
    }

    private val curatedHistoricalCourseVersions: Map<String, List<UfopCourseCurriculum>> by lazy {
        mapOf(
            "AUT" to listOf(engControleAutomacao2024_1)
        )
    }

    val historicalCourseVersions: Map<String, List<UfopCourseCurriculum>>
        get() {
            cachedMergedHistoricalByCode?.let { return it }
            return synchronized(loadLock) {
                cachedMergedHistoricalByCode?.let { return@synchronized it }
                val merged = buildMergedHistoricalMap(cachedLegacyCatalog)
                cachedMergedHistoricalByCode = merged
                merged
            }
        }

    private fun buildMergedHistoricalMap(
        legacyData: UfopLegacyCatalogData?
    ): Map<String, List<UfopCourseCurriculum>> {
        val grouped = LinkedHashMap<String, MutableList<UfopCourseCurriculum>>()
        curatedHistoricalCourseVersions.forEach { (code, versions) ->
            val key = code.trim().uppercase()
            val list = grouped.getOrPut(key) { mutableListOf() }
            versions.forEach { version ->
                val verKey = normalizeVersionKey(version.curriculumVersion)
                if (list.none { normalizeVersionKey(it.curriculumVersion) == verKey }) {
                    list.add(version)
                }
            }
        }
        legacyData?.historicalCurricula.orEmpty().forEach { version ->
            val key = version.code.trim().uppercase()
            if (key.isEmpty()) return@forEach
            val list = grouped.getOrPut(key) { mutableListOf() }
            val verKey = normalizeVersionKey(version.curriculumVersion)
            if (list.none { normalizeVersionKey(it.curriculumVersion) == verKey }) {
                list.add(version)
            }
        }
        return grouped.mapValues { (_, list) ->
            list.sortedWith(COURSE_VERSION_COMPARATOR)
        }
    }

    fun getCourseVersions(baseCourse: UfopCourseCurriculum): List<UfopCourseCurriculum> {
        val codeKey = baseCourse.code.trim().uppercase()
        val historical = historicalCourseVersions[codeKey].orEmpty()
        if (historical.isEmpty()) return listOf(baseCourse)

        val combined = ArrayList<UfopCourseCurriculum>(historical.size + 1)
        val seenKeys = HashSet<String>(historical.size + 1)
        historical.forEach { version ->
            val key = normalizeVersionKey(version.curriculumVersion)
            if (seenKeys.add(key)) {
                combined.add(version)
            }
        }
        val hasDetailedHistorical = combined.any { it.subjects.isNotEmpty() }
        val shouldIncludeBase = baseCourse.subjects.isNotEmpty() || !hasDetailedHistorical
        if (shouldIncludeBase) {
            val baseKey = normalizeVersionKey(baseCourse.curriculumVersion)
            if (seenKeys.add(baseKey)) {
                combined.add(baseCourse)
            }
        }
        if (combined.isEmpty()) return listOf(baseCourse)
        return combined.sortedWith(COURSE_VERSION_COMPARATOR)
    }

    fun resolveCourseForStudent(
        baseCourse: UfopCourseCurriculum,
        registrationNumber: String,
        earliestTranscriptSemester: String? = null
    ): UfopCourseCurriculum {
        val versions = getCourseVersions(baseCourse)
        if (versions.isEmpty()) return baseCourse
        if (versions.size == 1) return versions.first()

        val latestVersion = versions.lastOrNull { it.effectiveUntilYear == null } ?: versions.last()

        val regEntry = parseEnrollmentYearSemester(registrationNumber)
        val txEntry = parseSemesterYearTerm(earliestTranscriptSemester)
        val effectiveEntry = listOfNotNull(regEntry, txEntry)
            .minWithOrNull(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
            ?: return latestVersion

        val exactMatch = versions.lastOrNull {
            it.containsEntryTerm(effectiveEntry.first, effectiveEntry.second)
        }
        if (exactMatch != null) return exactMatch

        val oldest = versions.first()
        if (oldest.isEntryBeforeEffectiveStart(effectiveEntry.first, effectiveEntry.second)) {
            return oldest
        }

        return latestVersion
    }

    fun peekCachedCourses(): List<UfopCourseCurriculum>? = cachedCourses

    fun peekCachedLegacyCatalog(): UfopLegacyCatalogData? = cachedLegacyCatalog

    internal fun setCachedCoursesForTesting(courses: List<UfopCourseCurriculum>?) {
        synchronized(loadLock) {
            cachedCourses = courses
        }
    }

    internal fun setCachedLegacyCatalogForTesting(data: UfopLegacyCatalogData?) {
        synchronized(loadLock) {
            cachedLegacyCatalog = data
            cachedMergedHistoricalByCode = null
            legacyCatalogRevision += 1
        }
    }

    fun getAllCourses(context: Context): List<UfopCourseCurriculum> {
        val courses = cachedCourses ?: synchronized(loadLock) {
            cachedCourses ?: run {
                val rawJson = runCatching {
                    context.assets.open(ASSET_FILE_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }.getOrDefault("")
                val parsed = if (rawJson.isBlank()) emptyList() else loadFromJsonString(rawJson)
                cachedCourses = parsed
                parsed
            }
        }
        if (cachedLegacyCatalog == null) {
            getLegacyCatalog(context)
        }
        return courses
    }

    fun getLegacyCatalog(context: Context): UfopLegacyCatalogData {
        cachedLegacyCatalog?.let { return it }
        return synchronized(loadLock) {
            cachedLegacyCatalog?.let { return@synchronized it }
            val rawJson = runCatching {
                context.assets.open(LEGACY_ASSET_FILE_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrDefault("")
            val parsed = if (rawJson.isBlank()) UfopLegacyCatalogData() else loadLegacyCatalogFromJsonString(rawJson)
            cachedLegacyCatalog = parsed
            cachedMergedHistoricalByCode = null
            legacyCatalogRevision += 1
            parsed
        }
    }

    fun getLegacyReferences(
        courseCode: String,
        entryYear: Int? = null,
        entrySemester: Int? = null
    ): List<UfopLegacyReference> {
        val codeKey = courseCode.trim().uppercase()
        if (codeKey.isEmpty()) return emptyList()
        val refs = cachedLegacyCatalog?.legacyReferences.orEmpty()
            .filter { ref -> ref.courseCodes.any { it.equals(codeKey, ignoreCase = true) } }
        return if (entryYear != null) {
            refs.filter { it.matchesEntryTerm(entryYear = entryYear, entrySemester = entrySemester) }
        } else {
            refs
        }
    }

    fun getLegacyReferences(
        context: Context,
        courseCode: String,
        entryYear: Int? = null,
        entrySemester: Int? = null
    ): List<UfopLegacyReference> {
        if (cachedLegacyCatalog == null) {
            getLegacyCatalog(context)
        }
        return getLegacyReferences(courseCode = courseCode, entryYear = entryYear, entrySemester = entrySemester)
    }

    fun getOfficialLinks(courseCode: String): UfopCourseOfficialLinks? {
        val codeKey = courseCode.trim().uppercase()
        if (codeKey.isEmpty()) return null
        return cachedLegacyCatalog?.courseOfficialLinks?.get(codeKey)
    }

    fun getOfficialLinks(context: Context, courseCode: String): UfopCourseOfficialLinks? {
        if (cachedLegacyCatalog == null) {
            getLegacyCatalog(context)
        }
        return getOfficialLinks(courseCode)
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
            parseCourseCurriculumObject(obj)?.let(result::add)
        }
        return result
    }

    fun loadLegacyCatalogFromJsonString(rawJson: String): UfopLegacyCatalogData {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return UfopLegacyCatalogData()
        val root = JSONObject(trimmed)
        val schemaVersion = root.optNullableInt("schemaVersion") ?: 1

        val historicalArray = root.optJSONArray("historicalCurricula") ?: JSONArray()
        val historical = ArrayList<UfopCourseCurriculum>(historicalArray.length())
        for (i in 0 until historicalArray.length()) {
            val obj = historicalArray.optJSONObject(i) ?: continue
            parseCourseCurriculumObject(obj)?.let(historical::add)
        }

        val refsArray = root.optJSONArray("legacyReferences") ?: JSONArray()
        val legacyRefs = ArrayList<UfopLegacyReference>(refsArray.length())
        for (i in 0 until refsArray.length()) {
            val obj = refsArray.optJSONObject(i) ?: continue
            val id = obj.optNullableString("id") ?: continue
            val versionTitle = obj.optNullableString("versionTitle") ?: continue
            val url = obj.optNullableString("url") ?: continue
            val codesArray = obj.optJSONArray("courseCodes") ?: JSONArray()
            val courseCodes = ArrayList<String>(codesArray.length())
            for (k in 0 until codesArray.length()) {
                val rawCode = codesArray.optString(k, "").trim().uppercase()
                if (rawCode.isNotEmpty() && !codesArray.isNull(k)) {
                    courseCodes.add(rawCode)
                }
            }
            if (courseCodes.isEmpty()) continue
            legacyRefs.add(
                UfopLegacyReference(
                    id = id,
                    courseCodes = courseCodes,
                    courseName = obj.optNullableString("courseName") ?: "",
                    academicUnit = obj.optNullableString("academicUnit") ?: "",
                    versionTitle = versionTitle,
                    details = obj.optNullableString("details") ?: "",
                    accessType = obj.optNullableString("accessType") ?: "",
                    url = url,
                    effectiveFromYear = obj.optNullableInt("effectiveFromYear"),
                    effectiveFromSemester = obj.optNullableInt("effectiveFromSemester")?.takeIf { it == 1 || it == 2 },
                    effectiveUntilYear = obj.optNullableInt("effectiveUntilYear"),
                    effectiveUntilSemester = obj.optNullableInt("effectiveUntilSemester")?.takeIf { it == 1 || it == 2 },
                    entryRuleSummary = obj.optNullableString("entryRuleSummary") ?: ""
                )
            )
        }

        val linksObj = root.optJSONObject("courseOfficialLinks")
        val officialLinks = LinkedHashMap<String, UfopCourseOfficialLinks>(linksObj?.length() ?: 0)
        if (linksObj != null) {
            val keys = linksObj.keys()
            while (keys.hasNext()) {
                val rawCode = keys.next()
                val code = rawCode.trim().uppercase()
                if (code.isEmpty()) continue
                val entry = linksObj.optJSONObject(rawCode) ?: continue
                officialLinks[code] = UfopCourseOfficialLinks(
                    courseCode = code,
                    officialMatrixUrl = entry.optNullableString("officialMatrixUrl"),
                    drivePdfUrl = entry.optNullableString("drivePdfUrl"),
                    sheetStatus = entry.optNullableString("sheetStatus") ?: ""
                )
            }
        }

        return UfopLegacyCatalogData(
            schemaVersion = schemaVersion,
            historicalCurricula = historical,
            legacyReferences = legacyRefs,
            courseOfficialLinks = officialLinks
        )
    }

    private fun parseCourseCurriculumObject(obj: JSONObject): UfopCourseCurriculum? {
        val code = obj.optNullableString("code")?.uppercase() ?: return null
        val name = obj.optNullableString("name") ?: return null
        val curriculumVersion = obj.optNullableString("curriculumVersion") ?: ""

        val explicitFromYear = obj.optNullableInt("effectiveFromYear")
        val explicitFromSemester = obj.optNullableInt("effectiveFromSemester")?.takeIf { it == 1 || it == 2 }
        val (effectiveFromYear, effectiveFromSemester) = if (explicitFromYear != null) {
            explicitFromYear to (explicitFromSemester ?: 1)
        } else {
            val parsedTerm = parseSemesterYearTerm(curriculumVersion)
            if (parsedTerm != null) {
                // AUT Currículo 3 entrou em vigor em 2026/1 (logo após 2024/1–2025/2), apesar do rótulo 2026/2 no catálogo base.
                if (code == "AUT" && parsedTerm.first == 2026 && parsedTerm.second == 2) {
                    2026 to 1
                } else {
                    parsedTerm
                }
            } else {
                2026 to 1
            }
        }

        val effectiveUntilYear = obj.optNullableInt("effectiveUntilYear")
        val effectiveUntilSemester = obj.optNullableInt("effectiveUntilSemester")?.takeIf { it == 1 || it == 2 }
        val sourceUrl = obj.optNullableString("sourceUrl")

        val subjectsArr = obj.optJSONArray("subjects")
        val subjects = if (subjectsArr != null && subjectsArr.length() > 0) {
            ArrayList<UfopCurriculumSubject>(subjectsArr.length()).apply {
                for (j in 0 until subjectsArr.length()) {
                    val sObj = subjectsArr.optJSONObject(j) ?: continue
                    val sCode = sObj.optNullableString("code")?.uppercase() ?: continue
                    val rawSubjectName = sObj.optNullableString("name") ?: continue
                    val normalizedSubjectName = normalizeUfopPortugueseText(rawSubjectName)
                    val prereqArr = sObj.optJSONArray("prerequisites")
                    val prereqs = if (prereqArr != null && prereqArr.length() > 0) {
                        ArrayList<String>(prereqArr.length()).apply {
                            for (k in 0 until prereqArr.length()) {
                                if (!prereqArr.isNull(k)) {
                                    val p = prereqArr.optString(k, "").trim()
                                    if (p.isNotEmpty()) add(p)
                                }
                            }
                        }
                    } else {
                        emptyList()
                    }
                    add(
                        UfopCurriculumSubject(
                            code = sCode,
                            name = normalizedSubjectName,
                            departmentId = sObj.optNullableString("departmentId")?.lowercase() ?: "ufop",
                            departmentCode = sObj.optNullableString("departmentCode")?.uppercase() ?: "UFOP",
                            period = sObj.optInt("period", 0).coerceIn(0, 12),
                            type = sObj.optNullableString("type") ?: "Obrigatória",
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

        return UfopCourseCurriculum(
            code = code,
            name = name,
            degree = obj.optNullableString("degree") ?: "",
            campus = obj.optNullableString("campus") ?: "",
            curriculumVersion = curriculumVersion,
            chMandatory = obj.optInt("chMandatory", 0).coerceAtLeast(0),
            chOptional = obj.optInt("chOptional", 0).coerceAtLeast(0),
            chElective = obj.optInt("chElective", 0).coerceAtLeast(0),
            chExtension = obj.optInt("chExtension", 0).coerceAtLeast(0),
            chActivities = obj.optInt("chActivities", 0).coerceAtLeast(0),
            chTotal = obj.optInt("chTotal", 0).coerceAtLeast(0),
            status = obj.optNullableString("status") ?: "",
            subjects = subjects,
            effectiveFromYear = effectiveFromYear,
            effectiveFromSemester = effectiveFromSemester,
            effectiveUntilYear = effectiveUntilYear,
            effectiveUntilSemester = effectiveUntilSemester,
            sourceUrl = sourceUrl
        )
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
