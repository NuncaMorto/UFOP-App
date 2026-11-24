use std::sync::Arc;

use crate::db::Database;
use crate::models::{
    AbsenceHistoryEntry, AcademicProfileSummary, AgendaDayOverview, AgendaTimelineItem,
    AssessmentItem, AssessmentWithCourse, AttendanceStatus, CampusSearchResults,
    ClassCheckInDetail, CourseScheduleSlotItem, DepartmentColorOption, DisciplineDetail,
    DisciplineSummaryCard, UfopCampusInfo, UfopCurriculumTemplate, WeeklyTimetable,
};
use crate::{CampusError, repository};

#[derive(uniffi::Object)]
pub struct CampusEngine {
    db: Database,
}

#[uniffi::export]
impl CampusEngine {
    #[uniffi::constructor]
    pub fn new(db_path: String) -> Result<Arc<Self>, CampusError> {
        let db = Database::open(&db_path)?;
        Ok(Arc::new(Self { db }))
    }

    #[uniffi::constructor]
    pub fn new_empty(db_path: String) -> Result<Arc<Self>, CampusError> {
        let db = Database::open_empty(&db_path)?;
        Ok(Arc::new(Self { db }))
    }

    #[uniffi::constructor]
    pub fn new_in_memory() -> Result<Arc<Self>, CampusError> {
        let db = Database::open_in_memory()?;
        Ok(Arc::new(Self { db }))
    }

    #[uniffi::constructor]
    pub fn new_in_memory_empty() -> Result<Arc<Self>, CampusError> {
        let db = Database::open_in_memory_empty()?;
        Ok(Arc::new(Self { db }))
    }

    pub fn get_agenda_day(&self, iso_date: String) -> Result<AgendaDayOverview, CampusError> {
        self.db
            .with_conn(|conn| repository::get_agenda_day(conn, &iso_date))
    }

    pub fn get_class_check_in(
        &self,
        slot_id: String,
        iso_date: String,
    ) -> Result<ClassCheckInDetail, CampusError> {
        self.db
            .with_conn(|conn| repository::get_class_check_in(conn, &slot_id, &iso_date))
    }

    pub fn save_class_check_in(
        &self,
        slot_id: String,
        iso_date: String,
        status: AttendanceStatus,
        color_override_hex: Option<String>,
    ) -> Result<DisciplineSummaryCard, CampusError> {
        self.db.with_conn(|conn| {
            repository::save_class_check_in(
                conn,
                &slot_id,
                &iso_date,
                status,
                color_override_hex.as_deref(),
            )
        })
    }

    pub fn list_departments(&self) -> Result<Vec<DepartmentColorOption>, CampusError> {
        self.db.with_conn(repository::list_departments)
    }

    pub fn list_disciplines(
        &self,
        semester: String,
        department_id: Option<String>,
    ) -> Result<Vec<DisciplineSummaryCard>, CampusError> {
        self.db.with_conn(|conn| {
            repository::list_disciplines(conn, &semester, department_id.as_deref())
        })
    }

    pub fn get_discipline_detail(
        &self,
        course_id: String,
    ) -> Result<DisciplineDetail, CampusError> {
        self.db
            .with_conn(|conn| repository::get_discipline_detail(conn, &course_id))
    }

    pub fn update_discipline_notes(
        &self,
        course_id: String,
        notes: String,
    ) -> Result<DisciplineDetail, CampusError> {
        self.db
            .with_conn(|conn| repository::update_discipline_notes(conn, &course_id, &notes))
    }

    pub fn get_weekly_timetable(&self, semester: String) -> Result<WeeklyTimetable, CampusError> {
        self.db
            .with_conn(|conn| repository::get_weekly_timetable(conn, &semester))
    }

    #[allow(clippy::too_many_arguments)]
    pub fn create_commitment(
        &self,
        title: String,
        description: String,
        commitment_date: String,
        start_time: String,
        end_time: String,
        location: String,
        weather_temp_c: Option<i32>,
    ) -> Result<AgendaTimelineItem, CampusError> {
        self.db.with_conn(|conn| {
            repository::create_commitment(
                conn,
                &title,
                &description,
                &commitment_date,
                &start_time,
                &end_time,
                &location,
                weather_temp_c,
            )
        })
    }

    pub fn delete_commitment(&self, commitment_id: String) -> Result<(), CampusError> {
        self.db
            .with_conn(|conn| repository::delete_commitment(conn, &commitment_id))
    }

    #[allow(clippy::too_many_arguments)]
    pub fn update_commitment(
        &self,
        commitment_id: String,
        title: String,
        description: String,
        commitment_date: String,
        start_time: String,
        end_time: String,
        location: String,
    ) -> Result<AgendaTimelineItem, CampusError> {
        self.db.with_conn(|conn| {
            repository::update_commitment(
                conn,
                &commitment_id,
                &title,
                &description,
                &commitment_date,
                &start_time,
                &end_time,
                &location,
            )
        })
    }

    pub fn create_assessment(
        &self,
        course_id: String,
        title: String,
        kind: String,
        due_date: String,
        due_time: String,
    ) -> Result<AssessmentItem, CampusError> {
        self.db.with_conn(|conn| {
            repository::create_assessment(conn, &course_id, &title, &kind, &due_date, &due_time)
        })
    }

    pub fn toggle_assessment_completed(
        &self,
        assessment_id: String,
        completed: bool,
    ) -> Result<AssessmentItem, CampusError> {
        self.db.with_conn(|conn| {
            repository::toggle_assessment_completed(conn, &assessment_id, completed)
        })
    }

    pub fn delete_assessment(&self, assessment_id: String) -> Result<(), CampusError> {
        self.db
            .with_conn(|conn| repository::delete_assessment(conn, &assessment_id))
    }

    pub fn list_all_assessments(
        &self,
        semester: String,
        kind_filter: Option<String>,
    ) -> Result<Vec<AssessmentWithCourse>, CampusError> {
        self.db.with_conn(|conn| {
            repository::list_all_assessments(conn, &semester, kind_filter.as_deref())
        })
    }

    pub fn get_academic_profile_summary(
        &self,
        semester: String,
    ) -> Result<AcademicProfileSummary, CampusError> {
        self.db
            .with_conn(|conn| repository::get_academic_profile_summary(conn, &semester))
    }

    pub fn set_active_campus(&self, campus_id: String) -> Result<UfopCampusInfo, CampusError> {
        self.db
            .with_conn(|conn| repository::set_active_campus(conn, &campus_id))
    }

    pub fn export_schedule_payload(&self, semester: String) -> Result<String, CampusError> {
        self.db
            .with_conn(|conn| repository::export_schedule_payload(conn, &semester))
    }

    pub fn import_schedule_payload(&self, json_payload: String) -> Result<u32, CampusError> {
        self.db
            .with_conn(|conn| repository::import_schedule_payload(conn, &json_payload))
    }

    pub fn create_course(
        &self,
        semester: String,
        department_id: String,
        name: String,
        professor: String,
        workload_hours: u32,
        color_override_hex: Option<String>,
    ) -> Result<DisciplineSummaryCard, CampusError> {
        self.db.with_conn(|conn| {
            repository::create_course(
                conn,
                &semester,
                &department_id,
                &name,
                &professor,
                workload_hours,
                color_override_hex.as_deref(),
            )
        })
    }

    pub fn delete_course(&self, course_id: String) -> Result<(), CampusError> {
        self.db
            .with_conn(|conn| repository::delete_course(conn, &course_id))
    }

    pub fn update_course(
        &self,
        course_id: String,
        name: String,
        professor: String,
        department_id: String,
        workload_hours: u32,
    ) -> Result<DisciplineSummaryCard, CampusError> {
        self.db.with_conn(|conn| {
            repository::update_course(
                conn,
                &course_id,
                &name,
                &professor,
                &department_id,
                workload_hours,
            )
        })
    }

    #[allow(clippy::too_many_arguments)]
    pub fn add_schedule_slot(
        &self,
        course_id: String,
        day_of_week: u32,
        start_time: String,
        end_time: String,
        building: String,
        room: String,
        short_location: String,
    ) -> Result<CourseScheduleSlotItem, CampusError> {
        self.db.with_conn(|conn| {
            repository::add_schedule_slot(
                conn,
                &course_id,
                day_of_week,
                &start_time,
                &end_time,
                &building,
                &room,
                &short_location,
            )
        })
    }

    #[allow(clippy::too_many_arguments)]
    pub fn update_schedule_slot(
        &self,
        slot_id: String,
        day_of_week: u32,
        start_time: String,
        end_time: String,
        building: String,
        room: String,
        short_location: String,
    ) -> Result<CourseScheduleSlotItem, CampusError> {
        self.db.with_conn(|conn| {
            repository::update_schedule_slot(
                conn,
                &slot_id,
                day_of_week,
                &start_time,
                &end_time,
                &building,
                &room,
                &short_location,
            )
        })
    }

    pub fn delete_schedule_slot(&self, slot_id: String) -> Result<(), CampusError> {
        self.db
            .with_conn(|conn| repository::delete_schedule_slot(conn, &slot_id))
    }

    pub fn list_course_slots(
        &self,
        course_id: String,
    ) -> Result<Vec<CourseScheduleSlotItem>, CampusError> {
        self.db
            .with_conn(|conn| repository::list_course_slots(conn, &course_id))
    }

    pub fn list_ufop_templates(&self) -> Result<Vec<UfopCurriculumTemplate>, CampusError> {
        self.db.with_conn(repository::list_ufop_templates)
    }

    pub fn apply_ufop_template(
        &self,
        template_id: String,
        semester: String,
    ) -> Result<u32, CampusError> {
        self.db
            .with_conn(|conn| repository::apply_ufop_template(conn, &template_id, &semester))
    }

    pub fn list_absence_history(
        &self,
        course_id: String,
    ) -> Result<Vec<AbsenceHistoryEntry>, CampusError> {
        self.db
            .with_conn(|conn| repository::list_absence_history(conn, &course_id))
    }

    pub fn search_campus_items(
        &self,
        semester: String,
        query: String,
    ) -> Result<CampusSearchResults, CampusError> {
        self.db
            .with_conn(|conn| repository::search_campus_items(conn, &semester, &query))
    }

    pub fn reset_semester_to_default_seed(&self, semester: String) -> Result<u32, CampusError> {
        self.db
            .with_conn(|conn| repository::reset_semester_to_default_seed(conn, &semester))
    }
}
