package br.ufop.campus.core.ffi


enum class AttendanceStatus { ATTENDING, ABSENT, CANCELLED }
enum class TimelineItemKind { CLASS_SESSION, COMMITMENT }

data class DayPillItem(
    val isoDate: String,
    val dayShort: String,
    val dayNumber: Int
)

data class AgendaTimelineItem(
    val id: String,
    val slotId: String?,
    val courseId: String?,
    val kind: TimelineItemKind,
    val title: String,
    val startTime: String,
    val endTime: String,
    val location: String,
    val subtitle: String,
    val colorHex: String,
    val weatherTempC: Int?,
    val status: AttendanceStatus?
)

data class AgendaDayOverview(
    val isoDate: String,
    val monthYearLabel: String,
    val formattedDate: String,
    val weekNumber: Int,
    val weekDays: List<DayPillItem>,
    val classesCount: Int,
    val commitmentsCount: Int,
    val items: List<AgendaTimelineItem>
)

data class DepartmentColorOption(
    val id: String,
    val name: String,
    val colorHex: String
)

data class ClassCheckInDetail(
    val slotId: String,
    val courseId: String,
    val departmentId: String,
    val departmentName: String,
    val courseName: String,
    val formattedDate: String,
    val startTime: String,
    val endTime: String,
    val weatherTempC: Int,
    val weatherCondition: String,
    val buildingAndRoom: String,
    val professor: String,
    val selectedColorHex: String,
    val availableColors: List<DepartmentColorOption>,
    val status: AttendanceStatus
)

data class DisciplineSummaryCard(
    val courseId: String,
    val name: String,
    val departmentId: String,
    val departmentName: String,
    val colorHex: String,
    val workloadHours: Int,
    val currentAbsences: Int,
    val maxAbsences: Int,
    val cancelledClasses: Int,
    val code: String = ""
)

data class AssessmentItem(
    val id: String,
    val courseId: String,
    val dayNumber: String,
    val monthShort: String,
    val title: String,
    val kind: String,
    val dueTime: String,
    val dueDate: String = "2026-11-20",
    val completed: Boolean = false
)

data class DisciplineDetail(
    val summary: DisciplineSummaryCard,
    val semester: String,
    val assessments: List<AssessmentItem>,
    val notes: String,
    val professor: String = "",
    val buildingAndRoom: String = "",
    val shortLocation: String = "",
    val code: String = ""
)

data class TimetableCell(
    val slotId: String,
    val courseId: String,
    val courseName: String,
    val shortLocation: String,
    val departmentId: String,
    val departmentName: String,
    val colorHex: String,
    val code: String = ""
)

data class TimetableRow(
    val startTime: String,
    val endTime: String,
    val cellsByDay: Map<Int, TimetableCell?> // 1=SEG .. 6=SAB
)

data class WeeklyTimetable(
    val semester: String,
    val gridTitle: String,
    val days: List<Pair<Int, String>>, // (1, "SEG") .. (6, "SÁB")
    val rows: List<TimetableRow>,
    val legend: List<DepartmentColorOption>
)

data class AssessmentWithCourse(
    val assessment: AssessmentItem,
    val courseName: String,
    val departmentName: String,
    val colorHex: String
)

data class UfopCampusInfo(
    val id: String,
    val name: String,
    val city: String,
    val weatherTempC: Int,
    val weatherCondition: String,
    val isActive: Boolean
)

data class AcademicCalendarEvent(
    val id: String,
    val semester: String,
    val eventDate: String,
    val dayNumber: String,
    val monthShort: String,
    val title: String,
    val category: String
)

data class AcademicProfileSummary(
    val semester: String,
    val activeCampus: UfopCampusInfo,
    val campuses: List<UfopCampusInfo>,
    val totalCourses: Int,
    val totalAbsences: Int,
    val totalMaxAbsences: Int,
    val totalCancelledClasses: Int,
    val coursesAtRisk: Int,
    val pendingAssessments: Int,
    val completedAssessments: Int,
    val calendarEvents: List<AcademicCalendarEvent>
)

data class UfopCurriculumTemplate(
    val id: String,
    val code: String,
    val name: String,
    val departmentName: String,
    val campusName: String,
    val description: String,
    val coursesCount: Int
)

data class CourseScheduleSlotItem(
    val slotId: String,
    val courseId: String,
    val dayOfWeek: Int,
    val dayLabel: String,
    val startTime: String,
    val endTime: String,
    val building: String,
    val room: String,
    val shortLocation: String
)

// Sprint 4 models
data class AbsenceHistoryEntry(
    val sessionId: String,
    val slotId: String,
    val courseId: String,
    val sessionDate: String,
    val formattedDate: String,
    val startTime: String,
    val endTime: String,
    val status: AttendanceStatus,
    val classWeight: Int
)

data class CampusSearchResults(
    val query: String,
    val courses: List<DisciplineSummaryCard>,
    val assessments: List<AssessmentWithCourse>,
    val commitments: List<AgendaTimelineItem>
)

// Sprint 6 models
data class StudentProfile(
    val fullName: String = "",
    val registrationNumber: String = "",
    val courseName: String = "",
    val campusName: String = "Ouro Preto",
    val activeSemester: String = "2026.2",
    val semesterStartDate: String = "2026-08-24",
    val semesterEndDate: String = "2026-12-23",
    val avatarPath: String? = null,
    val onboardingCompleted: Boolean = false
)

data class FriendScheduleInfo(
    val id: String,
    val friendName: String,
    val courseName: String = "",
    val semester: String,
    val avatarBase64: String? = null,
    val timetable: WeeklyTimetable
)

data class DayActivityIndicator(
    val isoDate: String,
    val hasClasses: Boolean,
    val hasCommitments: Boolean,
    val hasAssessments: Boolean,
    val colorsHex: List<String>
)

// Sprint 7 models
data class UfopBuildingOption(
    val id: String,
    val campusId: String,
    val campusName: String,
    val shortName: String,
    val fullName: String
)

data class AcademicCalendarUpdatePackage(
    val version: String,
    val semester: String,
    val publishedBy: String,
    val publishedAt: String,
    val signatureSha256: String,
    val events: List<AcademicCalendarEvent>
)

// Sprint 8 models
data class TranscriptCourseEntry(
    val id: String,
    val semester: String,
    val code: String,
    val name: String,
    val workloadHours: Int,
    val grade: Double?,
    val absences: Int,
    val totalClasses: Int,
    val status: String,
    val professor: String = ""
)

data class SemesterCreSummary(
    val semester: String,
    val cre: Double,
    val totalWorkloadHours: Int,
    val approvedCount: Int,
    val failedCount: Int,
    val inProgressCount: Int,
    val courses: List<TranscriptCourseEntry>
)

data class AcademicCreReport(
    val overallCre: Double,
    val totalRequiredHours: Int = 3610,
    val completedHours: Int = 0,
    val totalApproved: Int = 0,
    val totalFailedByGrade: Int = 0,
    val totalFailedByAbsence: Int = 0,
    val semesters: List<SemesterCreSummary> = emptyList()
)

// Sprint 9 models
data class ExportFolderConfig(
    val treeUri: String? = null,
    val displayLabel: String = "Documentos/VidaAcademica/Grades"
)

// Sprint 12 models
data class SyllabusImportReport(
    val importedCount: Int,
    val matchedCourse: Boolean,
    val matchedCourseId: String? = null,
    val matchedCourseCode: String? = null,
    val matchedCourseTitle: String? = null,
    val extractedProfessor: String = "",
    val hasGradeDistribution: Boolean = false,
    val hasAssessmentDates: Boolean = false,
    val warningMessage: String? = null,
    val errorMessage: String? = null
)


/**
 * Datas oficiais de início e término de aulas por semestre letivo da UFOP
 * (Resolução CUNI 2945 para 2026.2 e Calendário Presencial UFOP 2027 v5 para 2027.1 e 2027.2).
 */
fun resolveDefaultSemesterBounds(semester: String): Pair<String, String> =
    when (semester.trim().replace('/', '.')) {
        "2026.2" -> "2026-08-24" to "2026-12-23"
        "2027.1" -> "2027-02-22" to "2027-06-26"
        "2027.2" -> "2027-08-02" to "2027-12-11"
        else -> "2026-08-24" to "2026-12-23"
    }
