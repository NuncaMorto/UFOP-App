package br.ufop.campus.core.ffi

import android.util.Base64
import br.ufop.campus.core.curriculum.UfopRoomAllocationIndex
import br.ufop.campus.ui.theme.CampusAccessibilitySettings
import kotlinx.coroutines.flow.StateFlow

data class PersistenceLoadErrorState(
    val hasError: Boolean = false,
    val reason: String? = null,
    val quarantinedFiles: List<String> = emptyList(),
    val detectedAtEpochMs: Long = 0L
)

interface CampusCoreRepository {
    val mutationVersion: StateFlow<Long>
    val persistenceLoadError: StateFlow<PersistenceLoadErrorState>
    fun isPersistenceBlockedDueToLoadError(): Boolean
    fun retryLoadPersistedState(): Boolean
    fun resetStateAfterLoadErrorAndUnlockPersistence()
    fun getAgendaDay(isoDate: String): AgendaDayOverview
    fun getClassCheckIn(slotId: String, isoDate: String): ClassCheckInDetail
    fun saveClassCheckIn(
        slotId: String,
        isoDate: String,
        status: AttendanceStatus,
        colorOverrideHex: String?
    ): DisciplineSummaryCard
    fun listDepartments(): List<DepartmentColorOption>
    fun listDisciplines(semester: String, departmentId: String?): List<DisciplineSummaryCard>
    fun getDisciplineDetail(courseId: String): DisciplineDetail
    fun updateDisciplineNotes(courseId: String, notes: String)
    fun getWeeklyTimetable(semester: String): WeeklyTimetable

    // Sprint 2 APIs
    fun createCommitment(
        title: String,
        description: String,
        commitmentDate: String,
        startTime: String,
        endTime: String,
        location: String,
        weatherTempC: Int? = 25
    ): AgendaTimelineItem

    fun deleteCommitment(commitmentId: String)

    fun createAssessment(
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String
    ): AssessmentItem

    fun toggleAssessmentCompleted(assessmentId: String, completed: Boolean): AssessmentItem

    fun deleteAssessment(assessmentId: String)

    fun listAllAssessments(
        semester: String,
        kindFilter: String? = null
    ): List<AssessmentWithCourse>

    fun getAcademicProfileSummary(semester: String = "2026.2"): AcademicProfileSummary

    fun setActiveCampus(campusId: String): UfopCampusInfo

    fun exportSchedulePayload(semester: String = "2026.2"): String

    fun importSchedulePayload(jsonPayload: String): Int

    // Sprint 3 APIs
    fun createCourse(
        semester: String,
        departmentId: String,
        name: String,
        professor: String,
        workloadHours: Int,
        colorOverrideHex: String? = null
    ): DisciplineSummaryCard

    fun deleteCourse(courseId: String)

    fun addScheduleSlot(
        courseId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ): CourseScheduleSlotItem

    fun deleteScheduleSlot(slotId: String)

    fun listCourseSlots(courseId: String): List<CourseScheduleSlotItem>

    fun listUfopTemplates(): List<UfopCurriculumTemplate>

    fun applyUfopTemplate(templateId: String, semester: String = "2026.2"): Int

    // Sprint 4 APIs
    fun listAbsenceHistory(courseId: String): List<AbsenceHistoryEntry>

    fun searchCampusItems(semester: String, query: String): CampusSearchResults

    fun resetSemesterToDefaultSeed(semester: String = "2026.2"): Int

    // Sprint 6 APIs
    /** Perfil do estudante (virgem/`onboardingCompleted = false` em instalação nova). */
    fun getStudentProfile(): StudentProfile

    /** Salva o perfil; se `campusName` for Ouro Preto/Mariana/João Monlevade sincroniza o campus ativo. */
    fun saveStudentProfile(profile: StudentProfile): StudentProfile

    /** Edita a disciplina; a cor é sempre derivada do departamento (paleta fixa UFOP). */
    fun updateCourse(
        courseId: String,
        name: String,
        professor: String,
        departmentId: String,
        workloadHours: Int
    ): DisciplineSummaryCard

    /** Edita um horário validando conflitos no mesmo semestre/dia (ignorando o próprio [slotId]). */
    fun updateScheduleSlot(
        slotId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ): CourseScheduleSlotItem

    fun updateCommitment(
        commitmentId: String,
        title: String,
        description: String,
        commitmentDate: String,
        startTime: String,
        endTime: String,
        location: String
    ): AgendaTimelineItem

    /** Indicadores por dia (1..N) do mês para o calendário semestral. */
    fun getMonthActivityIndicators(
        year: Int,
        month: Int,
        semester: String = getStudentProfile().activeSemester
    ): Map<Int, DayActivityIndicator>

    /** Semestres disponíveis (desc), ex.: `["2026.2", "2026.1"]`. */
    fun listAvailableSemesters(): List<String>

    /** Semestres disponíveis no Calendário Acadêmico UFOP (ordenados), ex.: `["2026.2", "2027.1", "2027.2"]`. */
    fun listAvailableCalendarSemesters(): List<String>

    /** Datas padrão (início, término) do período letivo oficial da UFOP para o semestre informado. */
    fun resolveDefaultSemesterBounds(semester: String): Pair<String, String> =
        br.ufop.campus.core.ffi.resolveDefaultSemesterBounds(semester)

    fun listFriendSchedules(): List<FriendScheduleInfo>

    /** Importa a grade de um amigo a partir do JSON gerado por [exportSchedulePayload]. */
    fun importFriendScheduleJson(friendName: String, jsonPayload: String): FriendScheduleInfo

    fun removeFriendSchedule(friendId: String)

    fun getFriendTimetable(friendId: String): WeeklyTimetable?

    /**
     * Atualização leve do clima (Open-Meteo), limitada a 1 requisição a cada 5 minutos.
     * Na main thread retorna o campus em cache imediatamente e atualiza em background
     * (incrementando [mutationVersion] quando o novo clima chega). Nunca falha offline.
     */
    fun refreshWeatherIfNeeded(force: Boolean = false): UfopCampusInfo

    /** Importa o `ATESTADO DE MATRÍCULA` (PDF) da UFOP. Retorna a quantidade de disciplinas importadas. */
    fun importEnrollmentCertificatePdf(pdfBytes: ByteArray, completeOnboarding: Boolean = true): Int

    /** Importa o texto bruto do `ATESTADO DE MATRÍCULA`. Retorna a quantidade de disciplinas importadas. */
    fun importEnrollmentCertificateText(rawText: String, completeOnboarding: Boolean = true): Int

    // Sprint 7 APIs
    /** Catálogo oficial dos prédios de ensino da UFOP (opcionalmente filtrado por campusId). */
    fun listUfopBuildings(campusId: String? = null): List<UfopBuildingOption>

    /** Importa datas de avaliações de um Plano de Ensino (PDF) para uma disciplina. */
    fun importCourseSyllabusPdf(courseId: String, pdfBytes: ByteArray): Int

    /** Importa datas de avaliações a partir do texto do Plano de Ensino para uma disciplina. */
    fun importCourseSyllabusText(courseId: String, rawText: String): Int

    /** Importa Plano de Ensino (PDF) com validação de disciplina, docente e critérios/datas. */
    fun importCourseSyllabusDetailedPdf(courseId: String, pdfBytes: ByteArray): SyllabusImportReport

    /** Importa Plano de Ensino (texto) com validação de disciplina, docente e critérios/datas. */
    fun importCourseSyllabusDetailedText(courseId: String, rawText: String): SyllabusImportReport

    /** Eventos do Calendário Acadêmico UFOP para o semestre informado. */
    fun getAcademicCalendarEvents(
        semester: String = getStudentProfile().activeSemester
    ): List<AcademicCalendarEvent>

    /** Indica se o modo administrador do calendário está desbloqueado. */
    fun isAdminUnlocked(): Boolean

    /** Desbloqueia o modo administrador usando o PIN oficial. */
    fun unlockAdminMode(pin: String): Boolean

    /** Publica e assina (SHA-256) um PDF oficial do Calendário Acadêmico UFOP. */
    fun adminPublishCalendarPdf(
        pdfBytes: ByteArray,
        semester: String = getStudentProfile().activeSemester
    ): AcademicCalendarUpdatePackage

    /** Serializa o pacote assinado ativo ou mais recente do Calendário Acadêmico. */
    fun exportCalendarUpdateJson(): String

    /** Valida a assinatura SHA-256 de um pacote `.ufopcal.json` e o prepara para aplicação. */
    fun importCalendarUpdateJson(jsonPayload: String): AcademicCalendarUpdatePackage

    /** Retorna o pacote de atualização de calendário pendente para o estudante aplicar, se houver. */
    fun getPendingCalendarUpdate(): AcademicCalendarUpdatePackage?

    /** Aplica os eventos do pacote pendente no calendário do semestre e retorna a quantidade aplicada. */
    fun applyPendingCalendarUpdate(): Int

    /** Exporta a grade do estudante no formato `.ufopgrade` (com nome, curso e avatar Base64). */
    fun exportMyGradeBundleJson(
        semester: String = getStudentProfile().activeSemester
    ): String

    // Sprint 8 APIs
    /** Retorna apenas os departamentos distintos que possuem disciplinas no semestre informado. */
    fun listDepartmentsForSemester(semester: String): List<DepartmentColorOption>

    /** Calcula e retorna o relatório oficial de CRE e histórico acadêmico por semestre. */
    fun getAcademicCreReport(): AcademicCreReport

    /** Atualiza ou simula nota (`0.0..10.0`) e/ou faltas de uma disciplina no Histórico ou semestre atual. */
    fun updateTranscriptOrCourseGrade(
        semester: String,
        codeOrCourseId: String,
        grade: Double?,
        absences: Int? = null
    ): AcademicCreReport

    /** Importa o Histórico Escolar oficial da UFOP (PDF) e atualiza CRE, cursos e docentes. */
    fun importAcademicTranscriptPdf(pdfBytes: ByteArray): AcademicCreReport

    /** Importa o texto bruto extraído de um Histórico Escolar da UFOP e atualiza CRE, cursos e docentes. */
    fun importAcademicTranscriptText(rawText: String): AcademicCreReport

    // Sprint 9 APIs
    /** Atualiza manualmente o total de faltas de uma disciplina (inclusive reduzindo abaixo das faltas de check-in). */
    fun updateCourseAbsences(courseId: String, totalAbsences: Int): DisciplineSummaryCard

    /** Preferência de tema escuro: "SYSTEM", "DARK" ou "LIGHT". */
    fun getDarkThemePreference(): String

    /** Persiste a preferência de tema escuro ("SYSTEM", "DARK" ou "LIGHT"). */
    fun setDarkThemePreference(mode: String)

    /** Retorna a pasta de exportação de grades (.ufopgrade). */
    fun getExportFolderConfig(): ExportFolderConfig

    /** Atualiza e persiste a pasta de exportação de grades (.ufopgrade). */
    fun setExportFolderConfig(treeUri: String?, displayLabel: String?): ExportFolderConfig

    // Sprint 10 APIs
    /** Edita os campos de uma avaliação (inclusive permitindo mover para outra disciplina). */
    fun updateAssessment(
        assessmentId: String,
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String,
        completed: Boolean
    ): AssessmentItem

    /** Atualiza e persiste a carga horária total exigida do curso (em horas). */
    fun setTotalRequiredHours(hours: Int)

    // Sprint 11 APIs
    /** Preferência de cor de destaque do app: "GREEN", "RED", "ORANGE", "PURPLE", "YELLOW", "CYAN", "BROWN", "PINK" ou "BLUE". */
    fun getAccentColorPreference(): String

    /** Persiste a preferência de cor de destaque. */
    fun setAccentColorPreference(accentKey: String)

    // Sprint 12 APIs
    fun getAccessibilitySettings(): CampusAccessibilitySettings
    fun setAccessibilitySettings(settings: CampusAccessibilitySettings)
    fun isGuidedTourCompleted(): Boolean
    fun setGuidedTourCompleted(completed: Boolean)
    fun getLastSeenReleaseVersion(): String
    fun setLastSeenReleaseVersion(version: String)
    fun exportFullAccountSnapshotJson(): String
    fun restoreFullAccountSnapshotJson(rawJson: String): Boolean
    fun wipeAllUserDataForLogout()

    // Sprint 14 APIs
    fun applyRoomAllocationIndex(index: UfopRoomAllocationIndex, enrichExisting: Boolean = true): Int

    // v1.9.8: Grade Dinâmica Interativa
    fun getDynamicGridCompletedCodes(matrixId: String): Set<String>
    fun saveDynamicGridCompletedCodes(matrixId: String, completedCodes: Set<String>)
    fun getDynamicGridAllCompleted(): Map<String, Set<String>>
}

typealias InMemoryCampusCoreRepository = InMemoryCampusRepository
