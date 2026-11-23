package br.ufop.campus.core.curriculum

import java.text.Normalizer
import kotlin.math.roundToInt

/**
 * Ano e semestre de ingresso / vigência curricular (ex.: 2024/1).
 */
data class DynamicSemesterTerm(
    val year: Int,
    val semester: Int
) : Comparable<DynamicSemesterTerm> {
    override fun compareTo(other: DynamicSemesterTerm): Int =
        compareValuesBy(this, other, { it.year }, { it.semester })

    fun formatLabel(): String = "$year/$semester"
}

/**
 * Nó de disciplina na Grade Dinâmica interativa com pré-requisitos e carga horária.
 */
data class DynamicCourseNode(
    val code: String,
    val name: String,
    val workloadHours: Int,
    val period: Int,
    val prerequisites: List<String> = emptyList(),
    val minHoursRequired: Int = 0,
    val isElective: Boolean = false
)

/**
 * Definição completa de uma matriz curricular compatível com a Grade Dinâmica interativa.
 */
data class DynamicCurriculumMatrix(
    val id: String,
    val courseName: String,
    val curriculumLabel: String,
    val totalPeriods: Int,
    val requiredActivitiesHours: Int,
    val totalCourseHours: Int,
    val courses: List<DynamicCourseNode>,
    val courseCode: String = "",
    val courseAliases: List<String> = emptyList(),
    val effectiveFrom: DynamicSemesterTerm = DynamicSemesterTerm(2024, 1),
    val effectiveUntil: DynamicSemesterTerm? = null,
    val legacyIds: List<String> = emptyList()
) {
    val totalGridHours: Int
        get() = courses.sumOf { it.workloadHours }

    fun validityLabel(): String = if (effectiveUntil != null) {
        "${effectiveFrom.formatLabel()}–${effectiveUntil.formatLabel()}"
    } else {
        "${effectiveFrom.formatLabel()}+"
    }
}

/**
 * Resultado da seleção automática de matriz conforme curso e semestre de ingresso do estudante.
 */
data class DynamicMatrixResolution(
    val matrix: DynamicCurriculumMatrix,
    val studentEntryTerm: DynamicSemesterTerm?,
    val isCourseMatched: Boolean,
    val isExactVersionMatch: Boolean,
    val note: String?
)

enum class DynamicCourseStatus {
    COMPLETED,
    AVAILABLE,
    LOCKED
}

enum class DynamicGridToggleAction {
    MARKED_COMPLETED,
    UNMARKED_WITH_CASCADE,
    BLOCKED_BY_PREREQUISITES,
    NO_CHANGE
}

data class DynamicGridToggleResult(
    val updatedCompletedCodes: Set<String>,
    val action: DynamicGridToggleAction,
    val affectedCode: String,
    val cascadeRemovedCodes: List<String> = emptyList(),
    val missingPrerequisites: List<String> = emptyList(),
    val missingHours: Int = 0
)

data class DynamicGridSummary(
    val completedHours: Int,
    val totalGridHours: Int,
    val completionPercent: Int,
    val completedCount: Int,
    val totalCount: Int
)

/**
 * Catálogo das matrizes curriculares disponíveis para simulação na Grade Dinâmica.
 */
object DynamicCurriculumCatalog {

    private val DIACRITICS_REGEX = Regex("\\p{M}+")
    private val SEMESTER_TERM_REGEX = Regex("""(?:^|\D)(\d{4}|\d{2})\s*[./-]\s*([12])(?:\D|$)""")

    private val AUTOMACAO_ALIASES = listOf(
        "engenharia de controle e automacao",
        "eng. de controle e automacao",
        "controle e automacao",
        "aut"
    )

    val engControleAutomacao2024_1: DynamicCurriculumMatrix = DynamicCurriculumMatrix(
        id = "eng_controle_automacao_2024_1",
        courseName = "Eng. de Controle e Automação",
        curriculumLabel = "2024/1 · Currículo 2",
        totalPeriods = 10,
        requiredActivitiesHours = 640,
        totalCourseHours = 3610,
        courseCode = "AUT",
        courseAliases = AUTOMACAO_ALIASES,
        effectiveFrom = DynamicSemesterTerm(2024, 1),
        effectiveUntil = DynamicSemesterTerm(2025, 2),
        legacyIds = listOf("eng_controle_automacao_2023"),
        courses = listOf(
            // Período 1
            DynamicCourseNode("BCC701", "Programação de Computadores I", 60, 1),
            DynamicCourseNode("CSO117", "Metodologia de Pesquisa", 30, 1),
            DynamicCourseNode("MTM122", "Cálculo Diferencial e Integral I", 90, 1),
            DynamicCourseNode("MTM730", "Geometria Analítica e Álgebra Linear", 60, 1),
            DynamicCourseNode("QUI701", "Química Fundamental", 60, 1),

            // Período 2
            DynamicCourseNode("ARQ205", "Representação Gráfica", 60, 2),
            DynamicCourseNode("BCC702", "Programação de Computadores II", 60, 2, listOf("BCC701")),
            DynamicCourseNode("EST202", "Estatística e Probabilidade", 60, 2),
            DynamicCourseNode("FIS106", "Fundamentos de Mecânica", 60, 2, listOf("MTM122", "MTM730")),
            DynamicCourseNode("MTM123", "Cálculo Diferencial e Integral II", 60, 2, listOf("MTM122")),

            // Período 3
            DynamicCourseNode("BCC760", "Cálculo Numérico", 60, 3),
            DynamicCourseNode("FIS107", "Fundamentos de Termodinâmica", 30, 3, listOf("FIS106", "MTM123")),
            DynamicCourseNode("FIS108", "Fundamentos de Fluidos, Oscilações e Ondas", 30, 3, listOf("FIS106", "MTM123")),
            DynamicCourseNode("FIS109", "Fundamentos de Eletromagnetismo", 60, 3, listOf("FIS106", "MTM123")),
            DynamicCourseNode("MTM124", "Cálculo Diferencial e Integral III", 60, 3, listOf("MTM123")),
            DynamicCourseNode("MTM125", "Introdução às Equações Diferenciais Ordinárias", 60, 3, listOf("MTM123")),

            // Período 4
            DynamicCourseNode("BCC720", "Sistemas de Computação para Controle e Automação", 60, 4, listOf("BCC702")),
            DynamicCourseNode("CAT176", "Análise de Circuitos Elétricos", 60, 4),
            DynamicCourseNode("FIS105", "Fundamentos de Física Experimental", 30, 4, listOf("FIS109", "MTM124")),
            DynamicCourseNode("FIS110", "Fundamentos de Óptica e Quântica", 30, 4, listOf("FIS109", "MTM124")),
            DynamicCourseNode("MEC124", "Fenômenos de Transporte", 60, 4, listOf("FIS107", "FIS108")),
            DynamicCourseNode("MTM146", "Matemática Aplicada à Eng. de Controle e Automação", 60, 4, listOf("MTM124", "MTM125", "MTM730")),

            // Período 5
            DynamicCourseNode("BCC721", "Circuitos Digitais", 60, 5, listOf("BCC720")),
            DynamicCourseNode("CAT141", "Teoria de Controle I", 60, 5),
            DynamicCourseNode("CAT164", "Eletrotécnica para Controle e Automação", 60, 5),
            DynamicCourseNode("CAT165", "Circuitos e Dispositivos Eletrônicos", 60, 5),
            DynamicCourseNode("CIV107", "Resistência dos Materiais e Estruturas", 60, 5),

            // Período 6
            DynamicCourseNode("BCC342", "Introdução à Otimização", 60, 6, listOf("BCC702", "BCC760")),
            DynamicCourseNode("CAT163", "Instrumentação", 60, 6),
            DynamicCourseNode("CAT169", "Acionamentos Elétricos", 60, 6),
            DynamicCourseNode("CAT183", "Teoria de Controle II", 60, 6),
            DynamicCourseNode("MET702", "Engenharia nos Processos de Metalurgia", 60, 6),

            // Período 7
            DynamicCourseNode("BCC425", "Sistemas Embutidos", 60, 7, listOf("BCC721")),
            DynamicCourseNode("BCC740", "Inteligência Artificial", 60, 7, listOf("BCC702")),
            DynamicCourseNode("CAT166", "Teoria de Controle III", 60, 7),
            DynamicCourseNode("CAT175", "Informática Industrial", 60, 7),
            DynamicCourseNode("MIN107", "Engenharia nos Processos de Mineração", 60, 7),

            // Período 8
            DynamicCourseNode("BCC722", "Programação de Sistemas em Tempo Real", 60, 8, listOf("BCC425")),
            DynamicCourseNode("CAT167", "Sistemas Integrados de Manufatura", 60, 8),
            DynamicCourseNode("CAT181", "Elementos de Robótica", 60, 8),
            DynamicCourseNode("CAT490", "Trabalho Final de Curso I", 30, 8),
            DynamicCourseNode("DIR250", "Introdução ao Direito e Legislação", 30, 8),
            DynamicCourseNode("MEC142", "Acionamentos Fluidomecânicos", 60, 8),

            // Período 9
            DynamicCourseNode("CAT168", "Interfaceamento de Sistemas", 60, 9),
            DynamicCourseNode("CAT491", "Trabalho Final de Curso II", 30, 9, listOf("CAT490")),
            DynamicCourseNode("PRO243", "Organização e Administração I", 30, 9),
            DynamicCourseNode("ELT001", "Disciplina Eletiva I", 60, 9, isElective = true),
            DynamicCourseNode("ELT002", "Disciplina Eletiva II", 60, 9, isElective = true),

            // Período 10
            DynamicCourseNode("AMB111", "Engenharia Ambiental Básica M", 30, 10),
            DynamicCourseNode("PRO215", "Planejamento e Controle da Produção I", 60, 10),
            DynamicCourseNode("PRO224", "Economia da Engenharia", 60, 10),
            DynamicCourseNode("ELT003", "Disciplina Eletiva III", 60, 10, isElective = true),
            DynamicCourseNode("ELT004", "Disciplina Eletiva IV", 60, 10, isElective = true),
            DynamicCourseNode("ELT005", "Disciplina Eletiva V", 60, 10, isElective = true)
        )
    )

    val engControleAutomacao2026_1: DynamicCurriculumMatrix = DynamicCurriculumMatrix(
        id = "eng_controle_automacao_2026_1",
        courseName = "Eng. de Controle e Automação",
        curriculumLabel = "2026/1 · Currículo 3",
        totalPeriods = 10,
        requiredActivitiesHours = 510,
        totalCourseHours = 3390,
        courseCode = "AUT",
        courseAliases = AUTOMACAO_ALIASES,
        effectiveFrom = DynamicSemesterTerm(2026, 1),
        effectiveUntil = null,
        courses = listOf(
            // Período 1
            DynamicCourseNode("ARQ700", "Expressão Gráfica", 60, 1),
            DynamicCourseNode("BCC703", "Fundamentos de Programação", 60, 1),
            DynamicCourseNode("CAT410", "Introdução à Eng. de Controle e Automação", 30, 1),
            DynamicCourseNode("MTM122", "Cálculo Diferencial e Integral I", 90, 1),
            DynamicCourseNode("MTM730", "Geometria Analítica e Álgebra Linear", 60, 1),

            // Período 2
            DynamicCourseNode("BCC704", "Algoritmos e Estruturas de Dados", 60, 2, listOf("BCC703")),
            DynamicCourseNode("CAT411", "Introdução à Aquisição de Dados e Controle", 30, 2, listOf("BCC703")),
            DynamicCourseNode("FIS106", "Fundamentos de Mecânica", 60, 2, listOf("MTM122", "MTM730")),
            DynamicCourseNode("FIS107", "Fundamentos de Termodinâmica", 30, 2),
            DynamicCourseNode("MTM123", "Cálculo Diferencial e Integral II", 60, 2, listOf("MTM122")),
            DynamicCourseNode("QUI022", "Química Geral Experimental AB", 30, 2),
            DynamicCourseNode("QUI075", "Química Geral B", 30, 2),

            // Período 3
            DynamicCourseNode("AMB111", "Engenharia Ambiental Básica M", 30, 3),
            DynamicCourseNode("BCC721", "Circuitos Digitais", 60, 3),
            DynamicCourseNode("BCC760", "Cálculo Numérico", 60, 3, listOf("BCC704")),
            DynamicCourseNode("FIS108", "Fundamentos de Fluidos, Oscilações e Ondas", 30, 3, listOf("FIS106")),
            DynamicCourseNode("MTM124", "Cálculo Diferencial e Integral III", 60, 3, listOf("MTM123")),
            DynamicCourseNode("MTM125", "Introdução às Equações Diferenciais Ordinárias", 60, 3, listOf("MTM122")),

            // Período 4
            DynamicCourseNode("BCC705", "Sistemas Computacionais para Engenharia", 30, 4, listOf("BCC721")),
            DynamicCourseNode("CAT176", "Análise de Circuitos Elétricos", 60, 4),
            DynamicCourseNode("FIS105", "Fundamentos de Física Experimental", 30, 4, listOf("FIS106", "FIS107", "FIS108")),
            DynamicCourseNode("FIS109", "Fundamentos de Eletromagnetismo", 60, 4, listOf("MTM123")),
            DynamicCourseNode("MEC124", "Fenômenos de Transporte", 60, 4, listOf("FIS108")),
            DynamicCourseNode("MTM146", "Matemática Aplicada à Eng. de Controle e Automação", 60, 4, listOf("MTM125")),

            // Período 5
            DynamicCourseNode("BCC425", "Sistemas Embutidos", 60, 5, listOf("BCC704")),
            DynamicCourseNode("CAT412", "Teoria de Controle I", 60, 5, listOf("MTM125")),
            DynamicCourseNode("CAT413", "Eletrotécnica para Controle e Automação", 60, 5, listOf("CAT176")),
            DynamicCourseNode("CAT414", "Circuitos e Dispositivos Eletrônicos", 60, 5, listOf("CAT176")),
            DynamicCourseNode("EST202", "Estatística e Probabilidade", 60, 5),

            // Período 6
            DynamicCourseNode("CAT163", "Instrumentação", 60, 6, listOf("CAT176")),
            DynamicCourseNode("CAT415", "Máquinas Elétricas", 60, 6, listOf("CAT413")),
            DynamicCourseNode("CAT416", "Acionamentos Elétricos", 60, 6, listOf("CAT413", "CAT414")),
            DynamicCourseNode("CAT417", "Teoria de Controle II", 60, 6, listOf("CAT412")),
            DynamicCourseNode("CAT418", "Laboratório de Controle I", 60, 6, listOf("CAT412")),

            // Período 7
            DynamicCourseNode("BCC740", "Inteligência Artificial", 60, 7, listOf("BCC703")),
            DynamicCourseNode("CAT182", "Elementos de Robótica", 60, 7, listOf("CAT418", "MTM730")),
            DynamicCourseNode("CAT420", "Informática Industrial", 60, 7, listOf("BCC721", "CAT416")),
            DynamicCourseNode("CAT421", "Teoria de Controle III", 60, 7, listOf("CAT417")),
            DynamicCourseNode("CAT422", "Sistemas Integrados de Manufatura", 30, 7),
            DynamicCourseNode("CIV049", "Resistência dos Materiais para Controle e Automação", 30, 7, listOf("FIS105")),

            // Período 8
            DynamicCourseNode("BCC722", "Programação de Sistemas em Tempo Real", 60, 8, listOf("BCC703", "BCC704", "BCC721")),
            DynamicCourseNode("CAT423", "Instrumentação Inteligente", 60, 8, listOf("CAT163")),
            DynamicCourseNode("CAT424", "Redes Industriais", 60, 8, listOf("CAT420")),
            DynamicCourseNode("MEC142", "Acionamentos Fluidomecânicos", 60, 8),
            DynamicCourseNode("ELT001", "Disciplina Eletiva I", 60, 8, isElective = true),

            // Período 9
            DynamicCourseNode("CAT425", "Trabalho Final de Curso I", 30, 9, minHoursRequired = 2000),
            DynamicCourseNode("DIR250", "Introdução ao Direito e Legislação", 30, 9),
            DynamicCourseNode("MET702", "Engenharia nos Processos de Metalurgia", 60, 9),
            DynamicCourseNode("MIN107", "Engenharia nos Processos de Mineração", 60, 9),
            DynamicCourseNode("PRO224", "Economia da Engenharia", 60, 9),
            DynamicCourseNode("ELT002", "Disciplina Eletiva II", 60, 9, isElective = true),

            // Período 10
            DynamicCourseNode("CAT491", "Trabalho Final de Curso II", 30, 10, listOf("CAT425")),
            DynamicCourseNode("PRO215", "Planejamento e Controle da Produção I", 60, 10),
            DynamicCourseNode("PRO243", "Organização e Administração I", 30, 10),
            DynamicCourseNode("ELT003", "Disciplina Eletiva III", 60, 10, isElective = true)
        )
    )

    val engControleAutomacao2023: DynamicCurriculumMatrix
        get() = engControleAutomacao2024_1

    val allMatrices: List<DynamicCurriculumMatrix> = listOf(
        engControleAutomacao2024_1,
        engControleAutomacao2026_1
    )

    val defaultMatrix: DynamicCurriculumMatrix = engControleAutomacao2024_1

    fun findById(id: String?): DynamicCurriculumMatrix? {
        val cleanId = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return allMatrices.firstOrNull { matrix ->
            matrix.id.equals(cleanId, ignoreCase = true) ||
                matrix.legacyIds.any { it.equals(cleanId, ignoreCase = true) }
        }
    }

    fun loadCompletedCodesWithLegacyFallback(
        matrix: DynamicCurriculumMatrix,
        reader: (String) -> Set<String>
    ): Set<String> {
        val primary = reader(matrix.id)
        if (primary.isNotEmpty()) return primary
        for (legacyId in matrix.legacyIds) {
            val legacy = reader(legacyId)
            if (legacy.isNotEmpty()) return legacy
        }
        return emptySet()
    }

    fun resolveMatrixForStudent(
        courseName: String,
        registrationNumber: String,
        earliestTranscriptSemester: String? = null
    ): DynamicMatrixResolution {
        val regPair = parseEnrollmentYearSemester(registrationNumber)
        val txPair = parseSemesterTerm(earliestTranscriptSemester)
        val entryPair = listOfNotNull(regPair, txPair)
            .minWithOrNull(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
        val entryTerm = entryPair?.let { (year, sem) -> DynamicSemesterTerm(year, sem) }

        val courseMatrices = findMatricesForCourse(courseName)
        if (courseMatrices.isEmpty()) {
            val cleanCourse = courseName.trim()
            return DynamicMatrixResolution(
                matrix = defaultMatrix,
                studentEntryTerm = entryTerm,
                isCourseMatched = false,
                isExactVersionMatch = false,
                note = if (cleanCourse.isNotEmpty()) {
                    "Matriz de $cleanCourse ainda não cadastrada na Grade Dinâmica"
                } else {
                    null
                }
            )
        }

        if (entryTerm != null) {
            val exactMatch = courseMatrices
                .filter { m ->
                    m.effectiveFrom <= entryTerm &&
                        (m.effectiveUntil == null || entryTerm <= m.effectiveUntil)
                }
                .maxByOrNull { it.effectiveFrom }

            if (exactMatch != null) {
                return DynamicMatrixResolution(
                    matrix = exactMatch,
                    studentEntryTerm = entryTerm,
                    isCourseMatched = true,
                    isExactVersionMatch = true,
                    note = "Ingresso ${entryTerm.formatLabel()} · Selecionada automaticamente"
                )
            }

            val oldest = courseMatrices.minBy { it.effectiveFrom }
            if (entryTerm < oldest.effectiveFrom) {
                return DynamicMatrixResolution(
                    matrix = oldest,
                    studentEntryTerm = entryTerm,
                    isCourseMatched = true,
                    isExactVersionMatch = false,
                    note = "Ingresso ${entryTerm.formatLabel()} · Matriz antiga não cadastrada; exibindo ${oldest.curriculumLabel}"
                )
            }
        }

        val latest = courseMatrices.firstOrNull { it.effectiveUntil == null }
            ?: courseMatrices.maxBy { it.effectiveFrom }
        return DynamicMatrixResolution(
            matrix = latest,
            studentEntryTerm = entryTerm,
            isCourseMatched = true,
            isExactVersionMatch = entryTerm != null,
            note = entryTerm?.let { "Ingresso ${it.formatLabel()} · ${latest.curriculumLabel}" }
        )
    }

    private fun findMatricesForCourse(courseName: String): List<DynamicCurriculumMatrix> {
        val normInput = normalizeLookupText(courseName)
        if (normInput.isEmpty()) return emptyList()

        return allMatrices.filter { matrix ->
            val normCourse = normalizeLookupText(matrix.courseName)
            val normCode = normalizeLookupText(matrix.courseCode)
            val normAliases = matrix.courseAliases.map { normalizeLookupText(it) }
            normInput == normCode ||
                normInput == normCourse ||
                normAliases.any { alias ->
                    alias.isNotEmpty() && (
                        normInput == alias ||
                            normInput.contains(alias) ||
                            (normInput.length >= 6 && alias.contains(normInput))
                        )
                }
        }
    }

    fun parseSemesterTerm(raw: String?): Pair<Int, Int>? {
        val clean = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val match = SEMESTER_TERM_REGEX.find(clean) ?: return null
        val rawYear = match.groupValues[1]
        val parsedYear = rawYear.toIntOrNull() ?: return null
        val fullYear = when (rawYear.length) {
            4 -> parsedYear
            2 -> if (parsedYear >= 69) 1900 + parsedYear else 2000 + parsedYear
            else -> return null
        }.takeIf { it in 1969..2099 } ?: return null
        val sem = match.groupValues[2].toIntOrNull()?.takeIf { it == 1 || it == 2 } ?: return null
        return fullYear to sem
    }

    private fun normalizeLookupText(raw: String): String {
        val nfd = Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD)
        return DIACRITICS_REGEX.replace(nfd, "")
            .replace("eng.", "engenharia")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }
}

/**
 * Motor determinístico de cálculo de pré-requisitos, desbloqueios, remoção em cascata e resumo
 * da Grade Dinâmica interativa.
 */
object DynamicGridEngine {

    private fun normalizeCode(raw: String): String = raw.trim().uppercase()

    private fun normalizeCompletedSet(
        matrix: DynamicCurriculumMatrix,
        completedCodes: Set<String>
    ): LinkedHashSet<String> {
        val validCodes = matrix.courses.mapTo(HashSet()) { normalizeCode(it.code) }
        val result = linkedSetOf<String>()
        completedCodes.forEach { raw ->
            val code = normalizeCode(raw)
            if (code in validCodes) {
                result.add(code)
            }
        }
        return result
    }

    fun completedHours(matrix: DynamicCurriculumMatrix, completedCodes: Set<String>): Int {
        val normalized = completedCodes.mapTo(HashSet()) { normalizeCode(it) }
        return matrix.courses
            .filter { normalizeCode(it.code) in normalized }
            .sumOf { it.workloadHours }
    }

    fun missingPrerequisites(
        matrix: DynamicCurriculumMatrix,
        courseCode: String,
        completedCodes: Set<String>
    ): List<String> {
        val cleanCode = normalizeCode(courseCode)
        val node = matrix.courses.firstOrNull { normalizeCode(it.code) == cleanCode } ?: return emptyList()
        val normalizedCompleted = completedCodes.mapTo(HashSet()) { normalizeCode(it) }
        return node.prerequisites
            .map { normalizeCode(it) }
            .filter { it.isNotEmpty() && it !in normalizedCompleted }
    }

    fun statusOf(
        matrix: DynamicCurriculumMatrix,
        courseCode: String,
        completedCodes: Set<String>
    ): DynamicCourseStatus {
        val cleanCode = normalizeCode(courseCode)
        val node = matrix.courses.firstOrNull { normalizeCode(it.code) == cleanCode }
            ?: return DynamicCourseStatus.LOCKED
        val normalizedCompleted = completedCodes.mapTo(HashSet()) { normalizeCode(it) }
        if (cleanCode in normalizedCompleted) {
            return DynamicCourseStatus.COMPLETED
        }
        val missingPrereqs = missingPrerequisites(matrix, cleanCode, normalizedCompleted)
        val currentHours = completedHours(matrix, normalizedCompleted)
        return if (missingPrereqs.isEmpty() && currentHours >= node.minHoursRequired) {
            DynamicCourseStatus.AVAILABLE
        } else {
            DynamicCourseStatus.LOCKED
        }
    }

    fun sanitizeCompleted(
        matrix: DynamicCurriculumMatrix,
        completedCodes: Set<String>
    ): Set<String> {
        val byCode = matrix.courses.associateBy { normalizeCode(it.code) }
        val working = normalizeCompletedSet(matrix, completedCodes)
        var changed: Boolean
        do {
            changed = false
            val iterator = working.iterator()
            while (iterator.hasNext()) {
                val code = iterator.next()
                val node = byCode[code]
                if (node == null) {
                    iterator.remove()
                    changed = true
                    continue
                }
                val prereqsSatisfied = node.prerequisites.all { normalizeCode(it) in working }
                val hoursExcludingSelf = if (node.minHoursRequired > 0) {
                    working.sumOf { otherCode ->
                        if (otherCode == code) 0 else (byCode[otherCode]?.workloadHours ?: 0)
                    }
                } else {
                    0
                }
                val hoursSatisfied = hoursExcludingSelf >= node.minHoursRequired
                if (!prereqsSatisfied || !hoursSatisfied) {
                    iterator.remove()
                    changed = true
                }
            }
        } while (changed)

        return matrix.courses
            .map { normalizeCode(it.code) }
            .filterTo(linkedSetOf()) { it in working }
    }

    fun toggleCourse(
        matrix: DynamicCurriculumMatrix,
        courseCode: String,
        currentCompleted: Set<String>
    ): DynamicGridToggleResult {
        val cleanCode = normalizeCode(courseCode)
        val sanitizedCurrent = sanitizeCompleted(matrix, currentCompleted)
        val node = matrix.courses.firstOrNull { normalizeCode(it.code) == cleanCode }
            ?: return DynamicGridToggleResult(
                updatedCompletedCodes = sanitizedCurrent,
                action = DynamicGridToggleAction.NO_CHANGE,
                affectedCode = cleanCode
            )

        if (cleanCode in sanitizedCurrent) {
            val withoutTarget = sanitizedCurrent.toMutableSet().apply { remove(cleanCode) }
            val afterCascade = sanitizeCompleted(matrix, withoutTarget)
            val cascadeRemoved = withoutTarget
                .filter { it !in afterCascade }
                .sorted()
            return DynamicGridToggleResult(
                updatedCompletedCodes = afterCascade,
                action = DynamicGridToggleAction.UNMARKED_WITH_CASCADE,
                affectedCode = cleanCode,
                cascadeRemovedCodes = cascadeRemoved
            )
        }

        val missingPrereqs = missingPrerequisites(matrix, cleanCode, sanitizedCurrent)
        val currentHours = completedHours(matrix, sanitizedCurrent)
        val missingHours = (node.minHoursRequired - currentHours).coerceAtLeast(0)
        if (missingPrereqs.isNotEmpty() || missingHours > 0) {
            return DynamicGridToggleResult(
                updatedCompletedCodes = sanitizedCurrent,
                action = DynamicGridToggleAction.BLOCKED_BY_PREREQUISITES,
                affectedCode = cleanCode,
                missingPrerequisites = missingPrereqs,
                missingHours = missingHours
            )
        }

        val updated = matrix.courses
            .map { normalizeCode(it.code) }
            .filterTo(linkedSetOf()) { it == cleanCode || it in sanitizedCurrent }
        return DynamicGridToggleResult(
            updatedCompletedCodes = updated,
            action = DynamicGridToggleAction.MARKED_COMPLETED,
            affectedCode = cleanCode
        )
    }

    fun summarize(
        matrix: DynamicCurriculumMatrix,
        completedCodes: Set<String>
    ): DynamicGridSummary {
        val validCompleted = sanitizeCompleted(matrix, completedCodes)
        val completedNodes = matrix.courses.filter { normalizeCode(it.code) in validCompleted }
        val doneHours = completedNodes.sumOf { it.workloadHours }
        val totalHours = matrix.totalGridHours
        val percent = if (totalHours > 0) {
            ((doneHours * 100.0) / totalHours.toDouble()).roundToInt().coerceIn(0, 100)
        } else {
            0
        }
        return DynamicGridSummary(
            completedHours = doneHours,
            totalGridHours = totalHours,
            completionPercent = percent,
            completedCount = completedNodes.size,
            totalCount = matrix.courses.size
        )
    }
}
