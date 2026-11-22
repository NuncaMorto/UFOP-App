use serde::{Deserialize, Serialize};

#[derive(uniffi::Enum, Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum AttendanceStatus {
    Attending,
    Absent,
    Cancelled,
}

impl AttendanceStatus {
    pub fn as_db_str(&self) -> &'static str {
        match self {
            Self::Attending => "ATTENDING",
            Self::Absent => "ABSENT",
            Self::Cancelled => "CANCELLED",
        }
    }

    pub fn from_db_str(raw: &str) -> Option<Self> {
        match raw.trim().to_ascii_uppercase().as_str() {
            "ATTENDING" => Some(Self::Attending),
            "ABSENT" => Some(Self::Absent),
            "CANCELLED" => Some(Self::Cancelled),
            _ => None,
        }
    }
}

#[derive(uniffi::Enum, Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum TimelineItemKind {
    ClassSession,
    Commitment,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DayPillItem {
    pub iso_date: String,
    pub day_short: String,
    pub day_number: u32,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AgendaTimelineItem {
    pub id: String,
    pub slot_id: Option<String>,
    pub course_id: Option<String>,
    pub kind: TimelineItemKind,
    pub title: String,
    pub start_time: String,
    pub end_time: String,
    pub location: String,
    pub subtitle: String,
    pub color_hex: String,
    pub weather_temp_c: Option<i32>,
    pub status: Option<AttendanceStatus>,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AgendaDayOverview {
    pub iso_date: String,
    pub month_year_label: String,
    pub formatted_date: String,
    pub week_number: u32,
    pub week_days: Vec<DayPillItem>,
    pub classes_count: u32,
    pub commitments_count: u32,
    pub items: Vec<AgendaTimelineItem>,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DepartmentColorOption {
    pub id: String,
    pub name: String,
    pub color_hex: String,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ClassCheckInDetail {
    pub slot_id: String,
    pub course_id: String,
    pub department_id: String,
    pub department_name: String,
    pub course_name: String,
    pub formatted_date: String,
    pub start_time: String,
    pub end_time: String,
    pub weather_temp_c: i32,
    pub weather_condition: String,
    pub building_and_room: String,
    pub professor: String,
    pub selected_color_hex: String,
    pub available_colors: Vec<DepartmentColorOption>,
    pub status: AttendanceStatus,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DisciplineSummaryCard {
    pub course_id: String,
    pub name: String,
    pub department_id: String,
    pub department_name: String,
    pub color_hex: String,
    pub workload_hours: u32,
    pub current_absences: u32,
    pub max_absences: u32,
    pub cancelled_classes: u32,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AssessmentItem {
    pub id: String,
    pub course_id: String,
    pub day_number: String,
    pub month_short: String,
    pub title: String,
    pub kind: String,
    pub due_date: String,
    pub due_time: String,
    pub completed: bool,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AssessmentWithCourse {
    pub assessment: AssessmentItem,
    pub course_name: String,
    pub department_name: String,
    pub color_hex: String,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct UfopCampusInfo {
    pub id: String,
    pub name: String,
    pub city: String,
    pub weather_temp_c: i32,
    pub weather_condition: String,
    pub is_active: bool,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AcademicCalendarEvent {
    pub id: String,
    pub semester: String,
    pub event_date: String,
    pub day_number: String,
    pub month_short: String,
    pub title: String,
    pub category: String,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AcademicProfileSummary {
    pub semester: String,
    pub active_campus: UfopCampusInfo,
    pub campuses: Vec<UfopCampusInfo>,
    pub total_courses: u32,
    pub total_absences: u32,
    pub total_max_absences: u32,
    pub total_cancelled_classes: u32,
    pub courses_at_risk: u32,
    pub pending_assessments: u32,
    pub completed_assessments: u32,
    pub calendar_events: Vec<AcademicCalendarEvent>,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DisciplineDetail {
    pub summary: DisciplineSummaryCard,
    pub semester: String,
    pub assessments: Vec<AssessmentItem>,
    pub notes: String,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct TimetableCell {
    pub slot_id: String,
    pub course_id: String,
    pub course_name: String,
    pub short_location: String,
    pub department_id: String,
    pub department_name: String,
    pub color_hex: String,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct TimetableDayCell {
    pub day_of_week: u32,
    pub day_label: String,
    pub cell: Option<TimetableCell>,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct TimetableRow {
    pub start_time: String,
    pub end_time: String,
    pub cells: Vec<TimetableDayCell>,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct WeeklyTimetable {
    pub semester: String,
    pub grid_title: String,
    pub days: Vec<String>,
    pub rows: Vec<TimetableRow>,
    pub legend: Vec<DepartmentColorOption>,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct UfopCurriculumTemplate {
    pub id: String,
    pub code: String,
    pub name: String,
    pub department_name: String,
    pub campus_name: String,
    pub description: String,
    pub courses_count: u32,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CourseScheduleSlotItem {
    pub slot_id: String,
    pub course_id: String,
    pub day_of_week: u32,
    pub day_label: String,
    pub start_time: String,
    pub end_time: String,
    pub building: String,
    pub room: String,
    pub short_location: String,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AbsenceHistoryEntry {
    pub session_id: String,
    pub slot_id: String,
    pub course_id: String,
    pub session_date: String,
    pub formatted_date: String,
    pub start_time: String,
    pub end_time: String,
    pub status: AttendanceStatus,
    pub class_weight: u32,
}

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CampusSearchResults {
    pub query: String,
    pub courses: Vec<DisciplineSummaryCard>,
    pub assessments: Vec<AssessmentWithCourse>,
    pub commitments: Vec<AgendaTimelineItem>,
}

fn default_class_weight() -> u32 {
    1
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ExportedCourseRecord {
    pub id: String,
    pub semester: String,
    pub department_id: String,
    #[serde(default)]
    pub color_override_hex: Option<String>,
    pub name: String,
    pub professor: String,
    pub workload_hours: u32,
    #[serde(default)]
    pub max_absences: u32,
    #[serde(default)]
    pub manual_absences: u32,
    #[serde(default)]
    pub notes: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ExportedScheduleSlotRecord {
    pub id: String,
    pub course_id: String,
    pub day_of_week: u32,
    pub start_time: String,
    pub end_time: String,
    pub building: String,
    pub room: String,
    pub short_location: String,
    #[serde(default = "default_class_weight")]
    pub class_weight: u32,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ScheduleExportPayload {
    pub semester: String,
    #[serde(default)]
    pub departments: Vec<DepartmentColorOption>,
    pub courses: Vec<ExportedCourseRecord>,
    #[serde(default)]
    pub schedule_slots: Vec<ExportedScheduleSlotRecord>,
}
