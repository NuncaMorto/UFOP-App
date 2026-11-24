pub mod db;
pub mod domain;
pub mod models;
pub mod repository;

use std::sync::Arc;

use db::Database;
pub use domain::map::CampusMapEngine;
pub use models::{
    AbsenceHistoryEntry, AcademicCalendarEvent, AcademicProfileSummary, AgendaDayOverview,
    AgendaTimelineItem, AssessmentItem, AssessmentWithCourse, AttendanceStatus, CameraFocus,
    CampusMapSummary, CampusSearchResults, ClassCheckInDetail, CourseScheduleSlotItem, DayPillItem,
    DepartmentColorOption, DisciplineDetail, DisciplineSummaryCard, FeatureInfo, FloorLabel,
    IndoorStatus, MapHit, MapLayer, OccupancyStatus, OverlayValidationReport, PoiType, Rect2D,
    RenderBatch, RenderFeature, RenderGeomKind, RenderOccupancyKind, Resolution, RoomOccupancyItem,
    SearchResult, SlotInfo, TimelineItemKind, TimetableCell, TimetableDayCell, TimetableRow,
    UfopCampusInfo, UfopCurriculumTemplate, WeeklyTimetable,
};

uniffi::setup_scaffolding!();

/// Valida um overlay GeoJSON contra um mapa base GeoJSON sem integrá-lo ao motor.
#[uniffi::export]
pub fn validate_overlay(
    base_geojson: String,
    overlay_geojson: String,
) -> Result<OverlayValidationReport, CampusError> {
    domain::map::validate_overlay(base_geojson, overlay_geojson)
}

#[derive(uniffi::Error, thiserror::Error, Debug)]
#[uniffi(flat_error)]
pub enum CampusError {
    #[error("Erro de banco de dados SQLite: {0}")]
    Database(String),
    #[error("Registro não encontrado: {0}")]
    NotFound(String),
    #[error("Parâmetro inválido: {0}")]
    InvalidInput(String),
}

impl From<rusqlite::Error> for CampusError {
    fn from(err: rusqlite::Error) -> Self {
        Self::Database(err.to_string())
    }
}

/// Calcula o limite máximo de faltas no padrão UFOP (25% da carga horária em horas-aula de 50 min).
#[uniffi::export]
pub fn calculate_max_absences(workload_hours: u32) -> u32 {
    domain::attendance::calculate_max_absences(workload_hours)
}

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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ufop_absence_limits() {
        assert_eq!(calculate_max_absences(30), 9);
        assert_eq!(calculate_max_absences(60), 18);
        assert_eq!(calculate_max_absences(90), 27);
    }

    #[test]
    fn test_initial_seed_disciplines() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");
        let disciplines = engine
            .list_disciplines("2026.2".to_string(), None)
            .expect("list disciplines");

        assert_eq!(disciplines.len(), 4);

        let calc2 = &disciplines[0];
        assert_eq!(calc2.course_id, "calc2");
        assert_eq!(calc2.name, "Cálculo II");
        assert_eq!(calc2.workload_hours, 60);
        assert_eq!(calc2.current_absences, 4);
        assert_eq!(calc2.max_absences, 18);
        assert_eq!(calc2.cancelled_classes, 0);
        assert_eq!(calc2.color_hex, "#3B68FF");

        let alg = &disciplines[1];
        assert_eq!(alg.course_id, "alg");
        assert_eq!(alg.name, "Algoritmos");
        assert_eq!(alg.workload_hours, 60);
        assert_eq!(alg.current_absences, 2);
        assert_eq!(alg.max_absences, 18);
        assert_eq!(alg.cancelled_classes, 0);
        assert_eq!(alg.color_hex, "#6E56CF");

        let alglin = &disciplines[2];
        assert_eq!(alglin.course_id, "alglin");
        assert_eq!(alglin.name, "Álgebra Linear");
        assert_eq!(alglin.workload_hours, 90);
        assert_eq!(alglin.current_absences, 1);
        assert_eq!(alglin.max_absences, 27);
        assert_eq!(alglin.cancelled_classes, 0);
        assert_eq!(alglin.color_hex, "#2E7D32");

        let fis1 = &disciplines[3];
        assert_eq!(fis1.course_id, "fis1");
        assert_eq!(fis1.name, "Física I");
        assert_eq!(fis1.workload_hours, 30);
        assert_eq!(fis1.current_absences, 3);
        assert_eq!(fis1.max_absences, 9);
        assert_eq!(fis1.cancelled_classes, 0);
        assert_eq!(fis1.color_hex, "#8D5B16");
    }

    #[test]
    fn test_agenda_tuesday_nov_17() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");
        let overview = engine
            .get_agenda_day("2026-11-17".to_string())
            .expect("agenda day 2026-11-17");

        assert_eq!(overview.iso_date, "2026-11-17");
        assert_eq!(overview.month_year_label, "Novembro 2026");
        assert_eq!(overview.formatted_date, "Terça-feira, 17 de novembro");
        assert_eq!(overview.week_number, 47);
        assert_eq!(overview.week_days.len(), 7);
        assert_eq!(overview.classes_count, 2);
        assert_eq!(overview.commitments_count, 1);
        assert_eq!(overview.items.len(), 3);

        // Item 1: Cálculo II 08:00–09:40 22°C
        let item0 = &overview.items[0];
        assert_eq!(item0.kind, TimelineItemKind::ClassSession);
        assert_eq!(item0.title, "Cálculo II");
        assert_eq!(item0.start_time, "08:00");
        assert_eq!(item0.end_time, "09:40");
        assert_eq!(item0.weather_temp_c, Some(22));
        assert_eq!(item0.status, Some(AttendanceStatus::Attending));
        assert_eq!(item0.slot_id.as_deref(), Some("slot_ter_0800_calc2"));

        // Item 2: Algoritmos 10:00–11:40 24°C
        let item1 = &overview.items[1];
        assert_eq!(item1.kind, TimelineItemKind::ClassSession);
        assert_eq!(item1.title, "Algoritmos");
        assert_eq!(item1.start_time, "10:00");
        assert_eq!(item1.end_time, "11:40");
        assert_eq!(item1.weather_temp_c, Some(24));
        assert_eq!(item1.status, Some(AttendanceStatus::Attending));
        assert_eq!(item1.slot_id.as_deref(), Some("slot_ter_1000_alg"));

        // Item 3: Grupo de estudo 14:00–15:00 26°C
        let item2 = &overview.items[2];
        assert_eq!(item2.kind, TimelineItemKind::Commitment);
        assert_eq!(item2.title, "Grupo de estudo");
        assert_eq!(item2.start_time, "14:00");
        assert_eq!(item2.end_time, "15:00");
        assert_eq!(item2.weather_temp_c, Some(26));
        assert_eq!(item2.status, None);
    }

    #[test]
    fn test_check_in_state_transitions() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");
        let slot_id = "slot_ter_0800_calc2".to_string();
        let iso_date = "2026-11-17".to_string();

        // 1. Marcar Cálculo II em 2026-11-17 como Absent -> 4/18 vira 5/18, 0 canceladas
        let summary_absent = engine
            .save_class_check_in(
                slot_id.clone(),
                iso_date.clone(),
                AttendanceStatus::Absent,
                None,
            )
            .expect("save check-in absent");
        assert_eq!(summary_absent.current_absences, 5);
        assert_eq!(summary_absent.max_absences, 18);
        assert_eq!(summary_absent.cancelled_classes, 0);

        // 2. Mudar para Cancelled -> volta para 4/18 e cancelled_classes vira 1 ("Aula cancelada não conta como falta")
        let summary_cancelled = engine
            .save_class_check_in(
                slot_id.clone(),
                iso_date.clone(),
                AttendanceStatus::Cancelled,
                Some("#2E7D32".to_string()),
            )
            .expect("save check-in cancelled");
        assert_eq!(summary_cancelled.current_absences, 4);
        assert_eq!(summary_cancelled.max_absences, 18);
        assert_eq!(summary_cancelled.cancelled_classes, 1);
        assert_eq!(summary_cancelled.color_hex, "#2E7D32");

        // Verifica que o get_class_check_in reflete o status e a cor atualizados
        let detail = engine
            .get_class_check_in(slot_id.clone(), iso_date.clone())
            .expect("get check-in detail");
        assert_eq!(detail.status, AttendanceStatus::Cancelled);
        assert_eq!(detail.selected_color_hex, "#2E7D32");

        // 3. Mudar para Attending -> 4/18 e 0 canceladas
        let summary_attending = engine
            .save_class_check_in(slot_id, iso_date, AttendanceStatus::Attending, None)
            .expect("save check-in attending");
        assert_eq!(summary_attending.current_absences, 4);
        assert_eq!(summary_attending.max_absences, 18);
        assert_eq!(summary_attending.cancelled_classes, 0);
    }

    #[test]
    fn test_discipline_detail_and_notes() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");
        let detail = engine
            .get_discipline_detail("calc2".to_string())
            .expect("get discipline detail calc2");

        assert_eq!(detail.summary.course_id, "calc2");
        assert_eq!(detail.semester, "2026.2");
        assert_eq!(detail.assessments.len(), 3);

        let badge_dates: Vec<String> = detail
            .assessments
            .iter()
            .map(|a| format!("{} {}", a.day_number, a.month_short))
            .collect();
        assert_eq!(badge_dates, vec!["20 NOV", "24 NOV", "27 NOV"]);

        let updated = engine
            .update_discipline_notes(
                "calc2".to_string(),
                "Revisar substituição trigonométrica e série de Taylor.".to_string(),
            )
            .expect("update notes");
        assert_eq!(
            updated.notes,
            "Revisar substituição trigonométrica e série de Taylor."
        );
    }

    #[test]
    fn test_weekly_timetable_matrix() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");
        let timetable = engine
            .get_weekly_timetable("2026.2".to_string())
            .expect("weekly timetable 2026.2");

        assert_eq!(timetable.semester, "2026.2");
        assert_eq!(timetable.rows.len(), 3);

        let band_labels: Vec<String> = timetable
            .rows
            .iter()
            .map(|row| format!("{}–{}", row.start_time, row.end_time))
            .collect();
        assert_eq!(
            band_labels,
            vec!["08:00–09:40", "10:00–11:40", "14:00–15:00"]
        );

        let legend_names: Vec<String> = timetable.legend.iter().map(|d| d.name.clone()).collect();
        assert_eq!(legend_names, vec!["Matemática", "Computação", "Física"]);
    }

    #[test]
    fn test_commitment_create_and_delete() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");
        let created = engine
            .create_commitment(
                "Monitoria de Algoritmos".to_string(),
                "Resolução de exercícios de grafos".to_string(),
                "2026-11-17".to_string(),
                "16:00".to_string(),
                "17:30".to_string(),
                "ICEB · Sala 201".to_string(),
                Some(23),
            )
            .expect("create commitment");

        assert_eq!(created.kind, TimelineItemKind::Commitment);
        assert_eq!(created.title, "Monitoria de Algoritmos");
        assert_eq!(created.subtitle, "Resolução de exercícios de grafos");
        assert_eq!(created.start_time, "16:00");
        assert_eq!(created.end_time, "17:30");
        assert_eq!(created.location, "ICEB · Sala 201");
        assert_eq!(created.weather_temp_c, Some(23));

        let day_after_create = engine
            .get_agenda_day("2026-11-17".to_string())
            .expect("agenda after create");
        assert_eq!(day_after_create.commitments_count, 2);
        assert_eq!(day_after_create.items.len(), 4);

        engine
            .delete_commitment(created.id.clone())
            .expect("delete commitment");

        let day_after_delete = engine
            .get_agenda_day("2026-11-17".to_string())
            .expect("agenda after delete");
        assert_eq!(day_after_delete.commitments_count, 1);
        assert_eq!(day_after_delete.items.len(), 3);
    }

    #[test]
    fn test_assessment_crud_and_completion_toggle() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");

        let initial_all = engine
            .list_all_assessments("2026.2".to_string(), None)
            .expect("list all assessments");
        assert_eq!(initial_all.len(), 9);
        assert!(initial_all.iter().all(|a| !a.assessment.completed));

        let created = engine
            .create_assessment(
                "calc2".to_string(),
                "Simulado de Séries".to_string(),
                "Trabalho".to_string(),
                "2026-11-29".to_string(),
                "23:59".to_string(),
            )
            .expect("create assessment");
        assert_eq!(created.course_id, "calc2");
        assert_eq!(created.day_number, "29");
        assert_eq!(created.month_short, "NOV");
        assert_eq!(created.due_date, "2026-11-29");
        assert_eq!(created.due_time, "23:59");
        assert!(!created.completed);

        let toggled = engine
            .toggle_assessment_completed(created.id.clone(), true)
            .expect("toggle completed true");
        assert!(toggled.completed);

        let detail = engine
            .get_discipline_detail("calc2".to_string())
            .expect("calc2 detail");
        assert_eq!(detail.assessments.len(), 4);
        assert!(
            detail
                .assessments
                .iter()
                .any(|a| a.id == created.id && a.completed)
        );

        let provas = engine
            .list_all_assessments("2026.2".to_string(), Some("Prova".to_string()))
            .expect("filter Prova");
        assert_eq!(provas.len(), 4);

        engine
            .delete_assessment(created.id)
            .expect("delete assessment");

        let after_delete = engine
            .list_all_assessments("2026.2".to_string(), None)
            .expect("list after delete");
        assert_eq!(after_delete.len(), 9);
    }

    #[test]
    fn test_active_campus_switch_and_profile_summary() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");

        let summary = engine
            .get_academic_profile_summary("2026.2".to_string())
            .expect("initial profile summary");
        assert_eq!(summary.semester, "2026.2");
        assert_eq!(summary.active_campus.id, "op_cruzeiro");
        assert_eq!(summary.active_campus.city, "Ouro Preto");
        assert!(summary.active_campus.is_active);
        assert_eq!(summary.campuses.len(), 3);
        assert_eq!(summary.total_courses, 4);
        assert_eq!(summary.total_absences, 10);
        assert_eq!(summary.total_max_absences, 72);
        assert_eq!(summary.total_cancelled_classes, 0);
        assert_eq!(summary.courses_at_risk, 0);
        assert_eq!(summary.pending_assessments, 9);
        assert_eq!(summary.completed_assessments, 0);
        assert_eq!(summary.calendar_events.len(), 24);
        assert_eq!(summary.calendar_events[0].day_number, "14");
        assert_eq!(summary.calendar_events[0].month_short, "AGO");
        assert!(
            summary
                .calendar_events
                .iter()
                .any(|e| e.event_date == "2026-08-24")
        );
        assert!(
            summary
                .calendar_events
                .iter()
                .any(|e| e.event_date == "2026-12-23")
        );

        let summary_2027_1 = engine
            .get_academic_profile_summary("2027.1".to_string())
            .expect("2027.1 profile summary");
        assert_eq!(summary_2027_1.calendar_events.len(), 21);

        let summary_2027_2 = engine
            .get_academic_profile_summary("2027.2".to_string())
            .expect("2027.2 profile summary");
        assert_eq!(summary_2027_2.calendar_events.len(), 24);

        let mariana = engine
            .set_active_campus("mar_ichs".to_string())
            .expect("switch to Mariana");
        assert_eq!(mariana.id, "mar_ichs");
        assert_eq!(mariana.city, "Mariana");
        assert_eq!(mariana.weather_temp_c, 25);
        assert!(mariana.is_active);

        let _ = engine
            .toggle_assessment_completed("assess_calc2_1".to_string(), true)
            .expect("complete one assessment");

        let updated_summary = engine
            .get_academic_profile_summary("2026.2".to_string())
            .expect("updated profile summary");
        assert_eq!(updated_summary.active_campus.id, "mar_ichs");
        assert_eq!(updated_summary.pending_assessments, 8);
        assert_eq!(updated_summary.completed_assessments, 1);
    }

    #[test]
    fn test_schedule_export_import_roundtrip() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");

        let exported_json = engine
            .export_schedule_payload("2026.2".to_string())
            .expect("export schedule JSON");
        assert!(exported_json.contains("calc2"));
        assert!(exported_json.contains("slot_ter_0800_calc2"));

        let custom_json = r#"{
            "semester": "2026.2",
            "courses": [
                {
                    "id": "ed2",
                    "semester": "2026.2",
                    "department_id": "comp",
                    "color_override_hex": null,
                    "name": "Estrutura de Dados II",
                    "professor": "Prof. Daniel Santos",
                    "workload_hours": 60,
                    "max_absences": 18,
                    "manual_absences": 1,
                    "notes": "Árvores B e Hashing."
                }
            ],
            "schedule_slots": [
                {
                    "id": "slot_sex_1000_ed2",
                    "course_id": "ed2",
                    "day_of_week": 5,
                    "start_time": "10:00",
                    "end_time": "11:40",
                    "building": "Prédio C",
                    "room": "Laboratório 02",
                    "short_location": "C · Lab 02",
                    "class_weight": 1
                }
            ]
        }"#;

        let imported_count = engine
            .import_schedule_payload(custom_json.to_string())
            .expect("import custom schedule");
        assert_eq!(imported_count, 1);

        let disciplines = engine
            .list_disciplines("2026.2".to_string(), None)
            .expect("list disciplines after import");
        assert_eq!(disciplines.len(), 5);
        assert!(disciplines.iter().any(|d| d.course_id == "ed2"));

        let roundtrip_count = engine
            .import_schedule_payload(exported_json)
            .expect("re-import exported schedule");
        assert_eq!(roundtrip_count, 4);
    }

    #[test]
    fn test_create_and_delete_course_with_workload_limits() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");

        let c30 = engine
            .create_course(
                "2026.2".to_string(),
                "phys".to_string(),
                "FIS105 Física Experimental".to_string(),
                "Prof.ª Helena Costa".to_string(),
                30,
                None,
            )
            .expect("create 30h course");
        assert_eq!(c30.workload_hours, 30);
        assert_eq!(c30.max_absences, 9);
        assert_eq!(c30.current_absences, 0);
        assert_eq!(c30.color_hex, "#8D5B16");

        let c60 = engine
            .create_course(
                "2026.2".to_string(),
                "comp".to_string(),
                "BCC221 Programação Orientada a Objetos".to_string(),
                "Prof. Rafael Lima".to_string(),
                60,
                Some("#2E7D32".to_string()),
            )
            .expect("create 60h course");
        assert_eq!(c60.workload_hours, 60);
        assert_eq!(c60.max_absences, 18);
        assert_eq!(c60.color_hex, "#2E7D32");

        let c90 = engine
            .create_course(
                "2026.2".to_string(),
                "math".to_string(),
                "MTM124 Cálculo III".to_string(),
                "Prof.ª Ana Martins".to_string(),
                90,
                None,
            )
            .expect("create 90h course");
        assert_eq!(c90.workload_hours, 90);
        assert_eq!(c90.max_absences, 27);

        let disciplines = engine
            .list_disciplines("2026.2".to_string(), None)
            .expect("list disciplines");
        assert_eq!(disciplines.len(), 7);

        engine
            .delete_course(c30.course_id.clone())
            .expect("delete 30h course");
        assert!(engine.delete_course(c30.course_id).is_err());

        let after_delete = engine
            .list_disciplines("2026.2".to_string(), None)
            .expect("list disciplines after delete");
        assert_eq!(after_delete.len(), 6);
    }

    #[test]
    fn test_add_schedule_slot_conflict_and_delete() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");

        let new_course = engine
            .create_course(
                "2026.2".to_string(),
                "comp".to_string(),
                "BCC322 Engenharia de Software".to_string(),
                "Prof. Eduardo Luz".to_string(),
                60,
                None,
            )
            .expect("create course");

        // Terça 08:00 já pertence a Cálculo II no seed 2026.2 -> deve acusar conflito
        let conflict_err = engine
            .add_schedule_slot(
                new_course.course_id.clone(),
                2,
                "08:00".to_string(),
                "09:40".to_string(),
                "ICEB".to_string(),
                "Sala 201".to_string(),
                "ICEB · 201".to_string(),
            )
            .expect_err("conflict should fail");
        let conflict_msg = conflict_err.to_string();
        assert!(conflict_msg.contains("Conflito de horário"));
        assert!(conflict_msg.contains("Cálculo II"));
        assert!(conflict_msg.contains("08:00"));

        // Faixa sobreposta (09:00–10:30) também deve acusar conflito com Cálculo II (08:00–09:40)
        let overlap_err = engine
            .add_schedule_slot(
                new_course.course_id.clone(),
                2,
                "09:00".to_string(),
                "10:30".to_string(),
                "ICEB".to_string(),
                "Sala 201".to_string(),
                "ICEB · 201".to_string(),
            )
            .expect_err("overlapping slot should fail");
        assert!(overlap_err.to_string().contains("Conflito de horário"));

        // Terça 14:00 está livre na grade de schedule_slots -> deve funcionar
        let added_slot = engine
            .add_schedule_slot(
                new_course.course_id.clone(),
                2,
                "14:00".to_string(),
                "15:00".to_string(),
                "ICEB".to_string(),
                "Sala 201".to_string(),
                "ICEB · 201".to_string(),
            )
            .expect("add free slot");
        assert_eq!(added_slot.day_of_week, 2);
        assert_eq!(added_slot.day_label, "TER");
        assert_eq!(added_slot.start_time, "14:00");
        assert_eq!(added_slot.end_time, "15:00");

        let slots = engine
            .list_course_slots(new_course.course_id.clone())
            .expect("list course slots");
        assert_eq!(slots.len(), 1);
        assert_eq!(slots[0].slot_id, added_slot.slot_id);

        engine
            .delete_schedule_slot(added_slot.slot_id.clone())
            .expect("delete slot");
        assert!(engine.delete_schedule_slot(added_slot.slot_id).is_err());

        let slots_after = engine
            .list_course_slots(new_course.course_id)
            .expect("list slots after delete");
        assert!(slots_after.is_empty());
    }

    #[test]
    fn test_list_and_apply_ufop_templates() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");

        let templates = engine
            .list_ufop_templates()
            .expect("list official UFOP templates");
        assert_eq!(templates.len(), 3);
        assert_eq!(templates[0].id, "tpl_bcc_decom");
        assert_eq!(templates[0].code, "BCC · DECOM");
        assert_eq!(templates[0].courses_count, 4);
        assert_eq!(templates[1].id, "tpl_ec_decsi");
        assert_eq!(templates[1].code, "ENGC · DECSI");
        assert_eq!(templates[1].courses_count, 4);
        assert_eq!(templates[2].id, "tpl_eng_basico");
        assert_eq!(templates[2].code, "CICLO BÁSICO · EM");
        assert_eq!(templates[2].courses_count, 4);

        let applied_count = engine
            .apply_ufop_template("tpl_bcc_decom".to_string(), "2026.2".to_string())
            .expect("apply BCC template");
        assert_eq!(applied_count, 4);

        let disciplines = engine
            .list_disciplines("2026.2".to_string(), None)
            .expect("list disciplines after BCC template");
        assert_eq!(disciplines.len(), 4);
        assert!(disciplines.iter().any(|d| d.name.contains("BCC201")));
        assert!(disciplines.iter().any(|d| d.max_absences == 27));

        let timetable = engine
            .get_weekly_timetable("2026.2".to_string())
            .expect("weekly timetable after template");
        assert_eq!(timetable.rows.len(), 3);

        let agenda = engine
            .get_agenda_day("2026-11-17".to_string())
            .expect("agenda after template");
        assert_eq!(agenda.classes_count, 2);

        let ec_count = engine
            .apply_ufop_template("tpl_ec_decsi".to_string(), "2027.1".to_string())
            .expect("apply ENGC template in 2027.1");
        assert_eq!(ec_count, 4);

        let disciplines_2027 = engine
            .list_disciplines("2027.1".to_string(), None)
            .expect("list 2027.1 disciplines");
        assert_eq!(disciplines_2027.len(), 4);
        assert!(disciplines_2027.iter().any(|d| d.name.contains("CSI102")));
        assert!(disciplines_2027.iter().any(|d| d.max_absences == 9));

        // 2026.2 remains untouched when applying template to 2027.1
        let disciplines_2026 = engine
            .list_disciplines("2026.2".to_string(), None)
            .expect("2026.2 still intact");
        assert_eq!(disciplines_2026.len(), 4);
        assert!(disciplines_2026.iter().any(|d| d.name.contains("BCC201")));

        let em_count = engine
            .apply_ufop_template("tpl_eng_basico".to_string(), "2026.2".to_string())
            .expect("apply Escola de Minas template");
        assert_eq!(em_count, 4);
        let disciplines_em = engine
            .list_disciplines("2026.2".to_string(), None)
            .expect("list EM disciplines");
        assert!(
            disciplines_em
                .iter()
                .any(|d| d.name.contains("BCC701 Programação"))
        );
    }

    #[test]
    fn test_file_backed_db_migrations_are_idempotent() {
        let mut db_path = std::env::temp_dir();
        let unique = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time")
            .as_nanos();
        db_path.push(format!("campus_ufop_test_{unique}.sqlite"));
        let path_str = db_path.to_string_lossy().to_string();

        {
            let engine1 = CampusEngine::new(path_str.clone()).expect("first open");
            assert_eq!(engine1.list_ufop_templates().expect("templates").len(), 3);
        }
        {
            let engine2 = CampusEngine::new(path_str).expect("second open (idempotent)");
            assert_eq!(engine2.list_ufop_templates().expect("templates").len(), 3);
            assert_eq!(
                engine2
                    .list_disciplines("2026.2".to_string(), None)
                    .expect("disciplines")
                    .len(),
                4
            );
        }

        let _ = std::fs::remove_file(&db_path);
    }

    #[test]
    fn test_list_absence_history_tracks_absences_and_cancellations() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");

        assert!(engine.list_absence_history(String::new()).is_err());
        assert!(
            engine
                .list_absence_history("inexistente".to_string())
                .is_err()
        );

        // Seed inicial tem apenas sessões ATTENDING em 2026-11-17
        let initial_history = engine
            .list_absence_history("calc2".to_string())
            .expect("initial history");
        assert!(initial_history.is_empty());

        // Marca terça 2026-11-17 como ABSENT e quinta 2026-11-19 como CANCELLED
        let after_absent = engine
            .save_class_check_in(
                "slot_ter_0800_calc2".to_string(),
                "2026-11-17".to_string(),
                AttendanceStatus::Absent,
                None,
            )
            .expect("mark absent");
        assert_eq!(after_absent.current_absences, 5);

        let after_cancel = engine
            .save_class_check_in(
                "slot_qui_0800_calc2".to_string(),
                "2026-11-19".to_string(),
                AttendanceStatus::Cancelled,
                None,
            )
            .expect("mark cancelled");
        assert_eq!(after_cancel.current_absences, 5);
        assert_eq!(after_cancel.cancelled_classes, 1);

        let history = engine
            .list_absence_history("calc2".to_string())
            .expect("absence history");
        assert_eq!(history.len(), 2);

        // Ordenado da data mais recente para a mais antiga
        assert_eq!(history[0].slot_id, "slot_qui_0800_calc2");
        assert_eq!(history[0].course_id, "calc2");
        assert_eq!(history[0].session_date, "2026-11-19");
        assert_eq!(history[0].formatted_date, "Quinta-feira, 19 de novembro");
        assert_eq!(history[0].start_time, "08:00");
        assert_eq!(history[0].end_time, "09:40");
        assert_eq!(history[0].status, AttendanceStatus::Cancelled);
        assert_eq!(history[0].class_weight, 1);

        assert_eq!(history[1].slot_id, "slot_ter_0800_calc2");
        assert_eq!(history[1].session_date, "2026-11-17");
        assert_eq!(history[1].formatted_date, "Terça-feira, 17 de novembro");
        assert_eq!(history[1].status, AttendanceStatus::Absent);
        assert_eq!(history[1].class_weight, 1);

        // Reverter terça para ATTENDING remove a falta do histórico
        let _ = engine
            .save_class_check_in(
                "slot_ter_0800_calc2".to_string(),
                "2026-11-17".to_string(),
                AttendanceStatus::Attending,
                None,
            )
            .expect("revert to attending");
        let after_revert = engine
            .list_absence_history("calc2".to_string())
            .expect("history after revert");
        assert_eq!(after_revert.len(), 1);
        assert_eq!(after_revert[0].status, AttendanceStatus::Cancelled);
    }

    #[test]
    fn test_search_campus_items_across_courses_assessments_commitments() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");

        let empty_results = engine
            .search_campus_items("2026.2".to_string(), "   ".to_string())
            .expect("empty search");
        assert!(empty_results.query.is_empty());
        assert!(empty_results.courses.is_empty());
        assert!(empty_results.assessments.is_empty());
        assert!(empty_results.commitments.is_empty());

        // Busca por "cálculo" encontra disciplina Cálculo II e o compromisso "Grupo de estudo" (descrição)
        let calc_results = engine
            .search_campus_items("2026.2".to_string(), "  cálculo  ".to_string())
            .expect("search cálculo");
        assert_eq!(calc_results.query, "cálculo");
        assert_eq!(calc_results.courses.len(), 1);
        assert_eq!(calc_results.courses[0].course_id, "calc2");
        assert!(calc_results.assessments.is_empty());
        assert_eq!(calc_results.commitments.len(), 1);
        assert_eq!(calc_results.commitments[0].title, "Grupo de estudo");

        // Busca Unicode case-insensitive por "álgebra" encontra "Álgebra Linear"
        let algebra_results = engine
            .search_campus_items("2026.2".to_string(), "álgebra".to_string())
            .expect("search álgebra");
        assert_eq!(algebra_results.courses.len(), 1);
        assert_eq!(algebra_results.courses[0].course_id, "alglin");

        // Busca por professor ("rafael") encontra Algoritmos
        let prof_results = engine
            .search_campus_items("2026.2".to_string(), "rafael".to_string())
            .expect("search professor");
        assert_eq!(prof_results.courses.len(), 1);
        assert_eq!(prof_results.courses[0].course_id, "alg");

        // Busca por tipo de avaliação ("prova") encontra as 4 provas do seed
        let prova_results = engine
            .search_campus_items("2026.2".to_string(), "prova".to_string())
            .expect("search prova");
        assert_eq!(prova_results.assessments.len(), 4);

        // Busca por título de avaliação ("árvore") e localização de compromisso ("biblioteca")
        let tree_results = engine
            .search_campus_items("2026.2".to_string(), "árvore".to_string())
            .expect("search árvore");
        assert_eq!(tree_results.assessments.len(), 1);
        assert_eq!(
            tree_results.assessments[0].assessment.title,
            "Implementação de Árvore AVL"
        );

        let bib_results = engine
            .search_campus_items("2026.2".to_string(), "biblioteca".to_string())
            .expect("search biblioteca");
        assert_eq!(bib_results.commitments.len(), 1);
        assert_eq!(bib_results.commitments[0].location, "Biblioteca · Sala 02");
    }

    #[test]
    fn test_reset_semester_to_default_seed_restores_four_courses() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");

        // Substitui 2026.2 por um template, adiciona disciplina extra e altera compromissos do dia seed
        let _ = engine
            .apply_ufop_template("tpl_bcc_decom".to_string(), "2026.2".to_string())
            .expect("apply BCC template");
        let _ = engine
            .create_course(
                "2026.2".to_string(),
                "comp".to_string(),
                "Disciplina Extra".to_string(),
                "Prof. Teste".to_string(),
                60,
                None,
            )
            .expect("create extra course");
        engine
            .delete_commitment("comm_2026-11-17_estudo".to_string())
            .expect("delete seed commitment");
        let _ = engine
            .create_commitment(
                "Compromisso Temporário".to_string(),
                "Remover ao restaurar".to_string(),
                "2026-11-17".to_string(),
                "16:00".to_string(),
                "17:00".to_string(),
                "ICEB".to_string(),
                None,
            )
            .expect("create temp commitment");

        let restored_count = engine
            .reset_semester_to_default_seed("2026.2".to_string())
            .expect("reset semester to default seed");
        assert_eq!(restored_count, 4);

        let disciplines = engine
            .list_disciplines("2026.2".to_string(), None)
            .expect("list restored disciplines");
        assert_eq!(disciplines.len(), 4);
        assert_eq!(disciplines[0].course_id, "calc2");
        assert_eq!(disciplines[0].current_absences, 4);
        assert_eq!(disciplines[1].course_id, "alg");
        assert_eq!(disciplines[2].course_id, "alglin");
        assert_eq!(disciplines[3].course_id, "fis1");

        let assessments = engine
            .list_all_assessments("2026.2".to_string(), None)
            .expect("list restored assessments");
        assert_eq!(assessments.len(), 9);
        assert!(assessments.iter().all(|a| !a.assessment.completed));

        let agenda = engine
            .get_agenda_day("2026-11-17".to_string())
            .expect("agenda after reset");
        assert_eq!(agenda.classes_count, 2);
        assert_eq!(agenda.commitments_count, 1);
        assert_eq!(agenda.items[2].title, "Grupo de estudo");

        // Garante que resetar 2026.2 não apaga outro semestre (2027.1)
        let _ = engine
            .apply_ufop_template("tpl_ec_decsi".to_string(), "2027.1".to_string())
            .expect("apply ENGC template in 2027.1");
        let restored_again = engine
            .reset_semester_to_default_seed("2026.2".to_string())
            .expect("reset 2026.2 with 2027.1 present");
        assert_eq!(restored_again, 4);

        let disciplines_2027 = engine
            .list_disciplines("2027.1".to_string(), None)
            .expect("2027.1 remains intact");
        assert_eq!(disciplines_2027.len(), 4);
    }

    #[test]
    fn test_new_in_memory_empty_starts_virgin_with_standardized_departments() {
        let engine = CampusEngine::new_in_memory_empty().expect("empty engine in-memory");

        assert!(
            engine
                .list_disciplines("2026.2".to_string(), None)
                .expect("empty disciplines")
                .is_empty()
        );
        assert!(
            engine
                .list_all_assessments("2026.2".to_string(), None)
                .expect("empty assessments")
                .is_empty()
        );

        let agenda = engine
            .get_agenda_day("2026-11-17".to_string())
            .expect("empty agenda day");
        assert_eq!(agenda.classes_count, 0);
        assert_eq!(agenda.commitments_count, 0);
        assert!(agenda.items.is_empty());

        let profile = engine
            .get_academic_profile_summary("2026.2".to_string())
            .expect("empty profile summary");
        assert_eq!(profile.total_courses, 0);
        assert_eq!(profile.campuses.len(), 3);
        assert_eq!(profile.active_campus.id, "op_cruzeiro");
        assert_eq!(profile.calendar_events.len(), 24);
        assert_eq!(profile.calendar_events[0].day_number, "14");
        assert_eq!(profile.calendar_events[0].month_short, "AGO");
        assert!(
            profile
                .calendar_events
                .iter()
                .any(|e| e.event_date == "2026-08-24")
        );
        assert!(
            profile
                .calendar_events
                .iter()
                .any(|e| e.event_date == "2026-12-23")
        );
        assert_eq!(
            engine
                .get_academic_profile_summary("2027.1".to_string())
                .expect("empty 2027.1 summary")
                .calendar_events
                .len(),
            21
        );
        assert_eq!(
            engine
                .get_academic_profile_summary("2027.2".to_string())
                .expect("empty 2027.2 summary")
                .calendar_events
                .len(),
            24
        );

        let depts = engine.list_departments().expect("standardized departments");
        let dept_tuples: Vec<(&str, &str, &str)> = depts
            .iter()
            .map(|d| (d.id.as_str(), d.name.as_str(), d.color_hex.as_str()))
            .collect();
        assert_eq!(
            dept_tuples,
            vec![
                ("math", "MTM · Matemática", "#3B68FF"),
                ("comp", "BCC · Computação", "#6E56CF"),
                ("cat", "CAT · Controle e Automação", "#2E7D32"),
                ("phys", "FIS · Física", "#8D5B16"),
                ("em", "EM · Escola de Minas", "#00897B"),
            ]
        );

        let cat_course = engine
            .create_course(
                "2026.2".to_string(),
                "cat".to_string(),
                "CAT163 Controle Clássico".to_string(),
                "Prof. Carlos Silva".to_string(),
                60,
                None,
            )
            .expect("create CAT course in empty engine");
        assert_eq!(cat_course.color_hex, "#2E7D32");
        assert_eq!(cat_course.department_name, "CAT · Controle e Automação");
        assert_eq!(cat_course.max_absences, 18);

        let mut db_path = std::env::temp_dir();
        let unique = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time")
            .as_nanos();
        db_path.push(format!("campus_ufop_empty_test_{unique}.sqlite"));
        let path_str = db_path.to_string_lossy().to_string();

        {
            let file_engine = CampusEngine::new_empty(path_str.clone()).expect("empty file db");
            assert!(
                file_engine
                    .list_disciplines("2026.2".to_string(), None)
                    .expect("file empty disciplines")
                    .is_empty()
            );
            assert_eq!(file_engine.list_departments().expect("depts").len(), 5);
        }
        let _ = std::fs::remove_file(&db_path);
    }

    #[test]
    fn test_update_course_recalculates_limits_and_clears_color_override() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");

        // alglin começa no seed com department_id='math', color_override_hex='#2E7D32' e 90h (27 faltas)
        let updated = engine
            .update_course(
                "alglin".to_string(),
                "EM101 Mecânica dos Sólidos".to_string(),
                "Prof. Marcos Vieira Jr.".to_string(),
                "em".to_string(),
                60,
            )
            .expect("update course");

        assert_eq!(updated.course_id, "alglin");
        assert_eq!(updated.name, "EM101 Mecânica dos Sólidos");
        assert_eq!(updated.department_id, "em");
        assert_eq!(updated.department_name, "EM · Escola de Minas");
        assert_eq!(updated.color_hex, "#00897B");
        assert_eq!(updated.workload_hours, 60);
        assert_eq!(updated.max_absences, 18);
        assert_eq!(updated.current_absences, 1);

        // Atualizar para departamento 'math' mantém cor fixa do departamento (sem override anterior)
        let back_to_math = engine
            .update_course(
                "alglin".to_string(),
                "MTM112 Álgebra Linear".to_string(),
                "Prof. Marcos Vieira".to_string(),
                "math".to_string(),
                30,
            )
            .expect("update course to math 30h");
        assert_eq!(back_to_math.color_hex, "#3B68FF");
        assert_eq!(back_to_math.max_absences, 9);

        assert!(
            engine
                .update_course(
                    "inexistente".to_string(),
                    "Nome".to_string(),
                    "Prof".to_string(),
                    "comp".to_string(),
                    60,
                )
                .is_err()
        );
        assert!(
            engine
                .update_course(
                    "alglin".to_string(),
                    "Nome".to_string(),
                    "Prof".to_string(),
                    "dept_invalido".to_string(),
                    60,
                )
                .is_err()
        );
        assert!(
            engine
                .update_course(
                    "alglin".to_string(),
                    "Nome".to_string(),
                    "Prof".to_string(),
                    "comp".to_string(),
                    0,
                )
                .is_err()
        );
    }

    #[test]
    fn test_update_schedule_slot_excludes_self_and_detects_other_conflicts() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");

        // slot_ter_0800_calc2 é Terça (2) 08:00–09:40; atualizar para 08:30–09:50 sobrepõe a si mesmo,
        // mas deve ser permitido pois exclui o próprio slot_id da checagem de conflito.
        let updated_self = engine
            .update_schedule_slot(
                "slot_ter_0800_calc2".to_string(),
                2,
                "08:30".to_string(),
                "09:50".to_string(),
                "ICEB".to_string(),
                "Sala 301".to_string(),
                String::new(),
            )
            .expect("update slot overlapping only itself should succeed");

        assert_eq!(updated_self.slot_id, "slot_ter_0800_calc2");
        assert_eq!(updated_self.course_id, "calc2");
        assert_eq!(updated_self.day_of_week, 2);
        assert_eq!(updated_self.day_label, "TER");
        assert_eq!(updated_self.start_time, "08:30");
        assert_eq!(updated_self.end_time, "09:50");
        assert_eq!(updated_self.building, "ICEB");
        assert_eq!(updated_self.room, "Sala 301");
        assert_eq!(updated_self.short_location, "ICEB · Sala 301");

        // Terça (2) 10:00–11:40 pertence a Algoritmos (slot_ter_1000_alg).
        // Mover slot_ter_0800_calc2 para 09:30–10:30 deve acusar conflito com Algoritmos.
        let conflict_err = engine
            .update_schedule_slot(
                "slot_ter_0800_calc2".to_string(),
                2,
                "09:30".to_string(),
                "10:30".to_string(),
                "ICEB".to_string(),
                "Sala 301".to_string(),
                "ICEB · 301".to_string(),
            )
            .expect_err("overlapping another slot should fail");
        let conflict_msg = conflict_err.to_string();
        assert!(conflict_msg.contains("Conflito de horário"));
        assert!(conflict_msg.contains("Algoritmos"));

        // Mover para Sexta (5) 14:00–15:40 (livre) deve funcionar
        let moved_day = engine
            .update_schedule_slot(
                "slot_ter_0800_calc2".to_string(),
                5,
                "14:00".to_string(),
                "15:40".to_string(),
                "Prédio A".to_string(),
                "Sala 102".to_string(),
                "A · 102".to_string(),
            )
            .expect("move slot to free Friday band");
        assert_eq!(moved_day.day_of_week, 5);
        assert_eq!(moved_day.day_label, "SEX");
        assert_eq!(moved_day.short_location, "A · 102");

        assert!(
            engine
                .update_schedule_slot(
                    "slot_inexistente".to_string(),
                    1,
                    "14:00".to_string(),
                    "15:00".to_string(),
                    "ICEB".to_string(),
                    "101".to_string(),
                    "ICEB · 101".to_string(),
                )
                .is_err()
        );
    }

    #[test]
    fn test_update_commitment_updates_fields_and_preserves_weather() {
        let engine = CampusEngine::new_in_memory().expect("engine in-memory");

        let updated = engine
            .update_commitment(
                "comm_2026-11-17_estudo".to_string(),
                "Grupo de estudo avançado".to_string(),
                "Séries de potências e EDOs".to_string(),
                "2026-11-17".to_string(),
                "15:00".to_string(),
                "16:30".to_string(),
                "ICEB · Sala 105".to_string(),
            )
            .expect("update commitment");

        assert_eq!(updated.id, "comm_2026-11-17_estudo");
        assert_eq!(updated.kind, TimelineItemKind::Commitment);
        assert_eq!(updated.title, "Grupo de estudo avançado");
        assert_eq!(updated.subtitle, "Séries de potências e EDOs");
        assert_eq!(updated.start_time, "15:00");
        assert_eq!(updated.end_time, "16:30");
        assert_eq!(updated.location, "ICEB · Sala 105");
        assert_eq!(updated.weather_temp_c, Some(26));

        let day = engine
            .get_agenda_day("2026-11-17".to_string())
            .expect("agenda after commitment update");
        let found = day
            .items
            .iter()
            .find(|item| item.id == "comm_2026-11-17_estudo")
            .expect("updated commitment in agenda");
        assert_eq!(found.title, "Grupo de estudo avançado");
        assert_eq!(found.start_time, "15:00");
        assert_eq!(found.location, "ICEB · Sala 105");

        assert!(
            engine
                .update_commitment(
                    "comm_inexistente".to_string(),
                    "Título".to_string(),
                    "Desc".to_string(),
                    "2026-11-17".to_string(),
                    "15:00".to_string(),
                    "16:00".to_string(),
                    "ICEB".to_string(),
                )
                .is_err()
        );
    }
}
