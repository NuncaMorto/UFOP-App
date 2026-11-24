package br.ufop.campus.ui.profile

import android.content.Context
import br.ufop.campus.core.curriculum.DynamicCurriculumCatalog
import br.ufop.campus.core.curriculum.DynamicGridEngine
import br.ufop.campus.core.curriculum.DynamicGridSummary
import br.ufop.campus.core.curriculum.DynamicMatrixResolution
import br.ufop.campus.core.curriculum.UfopCourseCurriculum
import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.ffi.AcademicCalendarUpdatePackage
import br.ufop.campus.core.ffi.AcademicCreReport
import br.ufop.campus.core.ffi.AcademicProfileSummary
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.DisciplineSummaryCard
import br.ufop.campus.core.ffi.ExportFolderConfig
import br.ufop.campus.core.ffi.StudentProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val mutationVersion: Long,
    val studentProfile: StudentProfile,
    val activeSemester: String,
    val profileSummary: AcademicProfileSummary,
    val creReport: AcademicCreReport,
    val pendingCalendarUpdate: AcademicCalendarUpdatePackage?,
    val catalogCourses: List<UfopCourseCurriculum>,
    val isCatalogLoaded: Boolean,
    val dynamicGridResolution: DynamicMatrixResolution,
    val dynamicGridSummary: DynamicGridSummary,
    val currentDisciplines: List<DisciplineSummaryCard> = emptyList(),
    val exportFolderConfig: ExportFolderConfig = ExportFolderConfig(),
    val isAdminUnlocked: Boolean = false
)

class ProfileViewModel(
    private val repository: CampusCoreRepository,
    private val catalogProvider: () -> List<UfopCourseCurriculum>? = { UfopCurriculumCatalog.peekCachedCourses() },
    private val catalogLoader: (Context) -> List<UfopCourseCurriculum> = { ctx ->
        UfopCurriculumCatalog.getAllCourses(ctx)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private data class MatrixResolutionKey(
        val courseName: String,
        val registrationNumber: String,
        val campusName: String,
        val earliestSemester: String?,
        val catalogIdentity: Int,
        val catalogSize: Int
    )

    private val scope = CoroutineScope(SupervisorJob() + computeDispatcher)
    private val refreshMutex = Mutex()

    private var lastProcessedMutationVersion: Long = Long.MIN_VALUE
    private var lastMatrixKey: MatrixResolutionKey? = null
    private var lastSummaryMatrixId: String? = null
    private var lastRawCompletedCodes: Set<String>? = null
    private var currentDisciplinesVersion: Long = Long.MIN_VALUE
    private var settingsDataVersion: Long = Long.MIN_VALUE

    @Volatile
    private var visibleDestination: ProfileDestination? = null

    @Volatile
    private var isTwoPaneLayout: Boolean = false

    private var catalogCourses: List<UfopCourseCurriculum>
    private var isCatalogLoaded: Boolean
    private var cachedResolution: DynamicMatrixResolution
    private var cachedGridSummary: DynamicGridSummary

    @Volatile
    internal var matrixResolutionCount: Int = 0
        private set

    @Volatile
    internal var gridSummaryComputationCount: Int = 0
        private set

    private val _uiState: MutableStateFlow<ProfileUiState>
    val uiState: StateFlow<ProfileUiState>

    init {
        val initialVersion = repository.mutationVersion.value
        val initialProfile = repository.getStudentProfile()
        val initialSemester = initialProfile.activeSemester.ifBlank { DEFAULT_SEMESTER }
        val initialSummary = repository.getAcademicProfileSummary(initialSemester)
        val initialCre = repository.getAcademicCreReport()
        val initialPendingCal = repository.getPendingCalendarUpdate()
        val initialCatalog = catalogProvider()

        catalogCourses = initialCatalog.orEmpty()
        isCatalogLoaded = initialCatalog != null

        val defaultMatrix = DynamicCurriculumCatalog.defaultMatrix
        cachedResolution = DynamicMatrixResolution(
            matrix = defaultMatrix,
            studentEntryTerm = null,
            isCourseMatched = false,
            isExactVersionMatch = false,
            note = null
        )
        cachedGridSummary = DynamicGridSummary(
            completedHours = 0,
            totalGridHours = defaultMatrix.totalGridHours,
            completionPercent = 0,
            completedCount = 0,
            totalCount = defaultMatrix.courses.size
        )

        _uiState = MutableStateFlow(
            ProfileUiState(
                mutationVersion = initialVersion,
                studentProfile = initialProfile,
                activeSemester = initialSemester,
                profileSummary = initialSummary,
                creReport = initialCre,
                pendingCalendarUpdate = initialPendingCal,
                catalogCourses = catalogCourses,
                isCatalogLoaded = isCatalogLoaded,
                dynamicGridResolution = cachedResolution,
                dynamicGridSummary = cachedGridSummary
            )
        )
        uiState = _uiState.asStateFlow()

        scope.launch {
            repository.mutationVersion.collectLatest { version ->
                refreshForMutation(version = version, force = false)
            }
        }
    }

    fun ensureCatalogLoaded(context: Context) {
        if (isCatalogLoaded) return
        val appContext = context.applicationContext ?: context
        scope.launch {
            val cached = catalogProvider()
            val loaded = if (cached != null) {
                cached
            } else {
                withContext(ioDispatcher) {
                    runCatching { catalogLoader(appContext) }.getOrDefault(emptyList())
                }
            }
            var shouldRefresh = false
            refreshMutex.withLock {
                if (!isCatalogLoaded || catalogCourses !== loaded) {
                    catalogCourses = loaded
                    isCatalogLoaded = true
                    shouldRefresh = true
                }
            }
            if (shouldRefresh) {
                refreshForMutation(version = repository.mutationVersion.value, force = true)
            }
        }
    }

    internal fun setVisibleDestination(destination: ProfileDestination?, useTwoPane: Boolean) {
        visibleDestination = destination
        isTwoPaneLayout = useTwoPane
        val needsMetrics = useTwoPane || destination == ProfileDestination.METRICS_CRE
        val needsSettings = destination == ProfileDestination.SETTINGS
        if (!needsMetrics && !needsSettings) return

        val targetVersion = repository.mutationVersion.value
        if ((!needsMetrics || currentDisciplinesVersion == targetVersion) &&
            (!needsSettings || settingsDataVersion == targetVersion)
        ) {
            return
        }

        scope.launch {
            loadSubScreenDataIfNeeded(
                version = targetVersion,
                needsMetrics = needsMetrics,
                needsSettings = needsSettings
            )
        }
    }

    /** Atualiza de forma determinística (útil em testes JVM e sincronizações manuais). */
    suspend fun refreshNow() {
        withContext(computeDispatcher) {
            refreshForMutation(version = repository.mutationVersion.value, force = true)
        }
    }

    fun close() {
        scope.cancel()
    }

    private suspend fun loadSubScreenDataIfNeeded(
        version: Long,
        needsMetrics: Boolean,
        needsSettings: Boolean
    ) {
        refreshMutex.withLock {
            val current = _uiState.value
            val activeSemester = current.activeSemester
            var updatedDisciplines = current.currentDisciplines
            var updatedExportConfig = current.exportFolderConfig
            var updatedAdminUnlocked = current.isAdminUnlocked
            var changed = false

            if (needsMetrics && currentDisciplinesVersion != version) {
                updatedDisciplines = repository.listDisciplines(activeSemester, null)
                currentDisciplinesVersion = version
                changed = true
            }
            if (needsSettings && settingsDataVersion != version) {
                updatedExportConfig = repository.getExportFolderConfig()
                updatedAdminUnlocked = repository.isAdminUnlocked()
                settingsDataVersion = version
                changed = true
            }
            if (changed) {
                _uiState.value = current.copy(
                    currentDisciplines = updatedDisciplines,
                    exportFolderConfig = updatedExportConfig,
                    isAdminUnlocked = updatedAdminUnlocked
                )
            }
        }
    }

    private suspend fun refreshForMutation(version: Long, force: Boolean) {
        refreshMutex.withLock {
            if (!force && version == lastProcessedMutationVersion && lastMatrixKey != null) {
                return@withLock
            }

            if (!isCatalogLoaded) {
                catalogProvider()?.let { cached ->
                    catalogCourses = cached
                    isCatalogLoaded = true
                }
            }

            val studentProfile = repository.getStudentProfile()
            val activeSemester = studentProfile.activeSemester.ifBlank { DEFAULT_SEMESTER }
            val profileSummary = repository.getAcademicProfileSummary(activeSemester)
            val creReport = repository.getAcademicCreReport()
            val pendingCalendarUpdate = repository.getPendingCalendarUpdate()

            val earliestSemester = resolveEarliestSemesterFromReport(creReport)
            val activeCatalog = catalogCourses
            val matrixKey = MatrixResolutionKey(
                courseName = studentProfile.courseName,
                registrationNumber = studentProfile.registrationNumber,
                campusName = studentProfile.campusName,
                earliestSemester = earliestSemester,
                catalogIdentity = System.identityHashCode(activeCatalog),
                catalogSize = activeCatalog.size
            )

            if (matrixKey != lastMatrixKey) {
                cachedResolution = DynamicCurriculumCatalog.resolveMatrixForStudent(
                    courseName = studentProfile.courseName,
                    registrationNumber = studentProfile.registrationNumber,
                    earliestTranscriptSemester = earliestSemester,
                    catalogCourses = activeCatalog,
                    campusHint = studentProfile.campusName
                )
                lastMatrixKey = matrixKey
                matrixResolutionCount += 1
            }

            val activeMatrix = cachedResolution.matrix
            val rawCompleted = DynamicCurriculumCatalog.loadCompletedCodesWithLegacyFallback(
                matrix = activeMatrix,
                reader = repository::getDynamicGridCompletedCodes
            )
            if (activeMatrix.id != lastSummaryMatrixId || rawCompleted != lastRawCompletedCodes) {
                cachedGridSummary = DynamicGridEngine.summarize(activeMatrix, rawCompleted)
                lastSummaryMatrixId = activeMatrix.id
                lastRawCompletedCodes = rawCompleted
                gridSummaryComputationCount += 1
            }

            val current = _uiState.value
            val needsMetrics = isTwoPaneLayout || visibleDestination == ProfileDestination.METRICS_CRE
            val needsSettings = visibleDestination == ProfileDestination.SETTINGS

            val disciplines = if (needsMetrics && currentDisciplinesVersion != version) {
                currentDisciplinesVersion = version
                repository.listDisciplines(activeSemester, null)
            } else {
                current.currentDisciplines
            }

            val exportConfig = if (needsSettings && settingsDataVersion != version) {
                repository.getExportFolderConfig()
            } else {
                current.exportFolderConfig
            }
            val adminUnlocked = if (needsSettings && settingsDataVersion != version) {
                settingsDataVersion = version
                repository.isAdminUnlocked()
            } else {
                current.isAdminUnlocked
            }

            lastProcessedMutationVersion = version
            _uiState.value = ProfileUiState(
                mutationVersion = version,
                studentProfile = studentProfile,
                activeSemester = activeSemester,
                profileSummary = profileSummary,
                creReport = creReport,
                pendingCalendarUpdate = pendingCalendarUpdate,
                catalogCourses = activeCatalog,
                isCatalogLoaded = isCatalogLoaded,
                dynamicGridResolution = cachedResolution,
                dynamicGridSummary = cachedGridSummary,
                currentDisciplines = disciplines,
                exportFolderConfig = exportConfig,
                isAdminUnlocked = adminUnlocked
            )
        }
    }

    private fun resolveEarliestSemesterFromReport(creReport: AcademicCreReport): String? =
        creReport.semesters
            .asSequence()
            .map { it.semester.trim() }
            .filter { it.isNotEmpty() }
            .minByOrNull { raw ->
                DynamicCurriculumCatalog.parseSemesterTerm(raw)
                    ?.let { (year, sem) -> year * 10 + sem }
                    ?: Int.MAX_VALUE
            }

    private companion object {
        const val DEFAULT_SEMESTER = "2026.2"
    }
}
