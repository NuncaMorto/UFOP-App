package br.ufop.campus.core.curriculum

import kotlin.math.roundToInt

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
    val courses: List<DynamicCourseNode>
) {
    val totalGridHours: Int
        get() = courses.sumOf { it.workloadHours }
}

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

    val engControleAutomacao2023: DynamicCurriculumMatrix = DynamicCurriculumMatrix(
        id = "eng_controle_automacao_2023",
        courseName = "Eng. de Controle e Automação",
        curriculumLabel = "Matriz 2023 (Currículo 2)",
        totalPeriods = 10,
        requiredActivitiesHours = 640,
        totalCourseHours = 3610,
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

    val allMatrices: List<DynamicCurriculumMatrix> = listOf(engControleAutomacao2023)

    val defaultMatrix: DynamicCurriculumMatrix = engControleAutomacao2023

    fun findById(id: String?): DynamicCurriculumMatrix? {
        val cleanId = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return allMatrices.firstOrNull { it.id.equals(cleanId, ignoreCase = true) }
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
