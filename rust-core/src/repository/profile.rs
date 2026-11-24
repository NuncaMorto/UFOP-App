use rusqlite::{Connection, params};

use crate::CampusError;
use crate::domain::agenda::{DEFAULT_WEATHER_CONDITION, DEFAULT_WEATHER_TEMP_C};
use crate::domain::attendance::calculate_max_absences;
use crate::models::{
    AcademicCalendarEvent, AcademicProfileSummary, ExportedCourseRecord,
    ExportedScheduleSlotRecord, ScheduleExportPayload, UfopCampusInfo,
};

use super::assessments::list_all_assessments;
use super::disciplines::{list_departments, list_disciplines};
use super::helpers::normalize_semester;

fn list_campuses(conn: &Connection) -> Result<Vec<UfopCampusInfo>, CampusError> {
    let mut stmt = conn.prepare(
        "SELECT id, name, city, weather_temp_c, weather_condition, is_active
         FROM ufop_campuses
         ORDER BY rowid ASC",
    )?;

    let rows = stmt.query_map([], |row| {
        let is_active_int: i32 = row.get(5)?;
        Ok(UfopCampusInfo {
            id: row.get(0)?,
            name: row.get(1)?,
            city: row.get(2)?,
            weather_temp_c: row.get(3)?,
            weather_condition: row.get(4)?,
            is_active: is_active_int != 0,
        })
    })?;

    let mut campuses = Vec::new();
    for item in rows {
        campuses.push(item?);
    }
    Ok(campuses)
}

pub fn set_active_campus(
    conn: &Connection,
    campus_id: &str,
) -> Result<UfopCampusInfo, CampusError> {
    let clean_id = campus_id.trim();
    if clean_id.is_empty() {
        return Err(CampusError::InvalidInput(
            "O identificador do campus (campus_id) não pode ser vazio".into(),
        ));
    }

    let exists: bool = conn
        .prepare("SELECT 1 FROM ufop_campuses WHERE id = ?1")?
        .exists(params![clean_id])?;
    if !exists {
        return Err(CampusError::NotFound(format!(
            "Campus UFOP não encontrado: '{clean_id}'"
        )));
    }

    conn.execute(
        "UPDATE ufop_campuses
         SET is_active = CASE WHEN id = ?1 THEN 1 ELSE 0 END",
        params![clean_id],
    )?;

    let campuses = list_campuses(conn)?;
    campuses
        .into_iter()
        .find(|c| c.id == clean_id)
        .ok_or_else(|| CampusError::NotFound(format!("Campus UFOP não encontrado: '{clean_id}'")))
}

pub fn get_academic_profile_summary(
    conn: &Connection,
    semester: &str,
) -> Result<AcademicProfileSummary, CampusError> {
    let clean_semester = normalize_semester(semester);

    let campuses = list_campuses(conn)?;
    let active_campus = campuses
        .iter()
        .find(|c| c.is_active)
        .cloned()
        .or_else(|| campuses.first().cloned())
        .unwrap_or(UfopCampusInfo {
            id: "op_cruzeiro".to_string(),
            name: "Campus Morro do Cruzeiro".to_string(),
            city: "Ouro Preto".to_string(),
            weather_temp_c: DEFAULT_WEATHER_TEMP_C,
            weather_condition: DEFAULT_WEATHER_CONDITION.to_string(),
            is_active: true,
        });

    let disciplines = list_disciplines(conn, clean_semester, None)?;
    let total_courses = disciplines.len() as u32;
    let total_absences: u32 = disciplines.iter().map(|d| d.current_absences).sum();
    let total_max_absences: u32 = disciplines.iter().map(|d| d.max_absences).sum();
    let total_cancelled_classes: u32 = disciplines.iter().map(|d| d.cancelled_classes).sum();
    let courses_at_risk: u32 = disciplines
        .iter()
        .filter(|d| d.max_absences > 0 && d.current_absences * 4 >= d.max_absences * 3)
        .count() as u32;

    let all_assessments = list_all_assessments(conn, clean_semester, None)?;
    let completed_assessments = all_assessments
        .iter()
        .filter(|item| item.assessment.completed)
        .count() as u32;
    let pending_assessments = (all_assessments.len() as u32).saturating_sub(completed_assessments);

    let mut cal_stmt = conn.prepare(
        "SELECT id, semester, event_date, day_number, month_short, title, category
         FROM academic_calendar_events
         WHERE semester = ?1
         ORDER BY event_date ASC, id ASC",
    )?;

    let cal_rows = cal_stmt.query_map(params![clean_semester], |row| {
        Ok(AcademicCalendarEvent {
            id: row.get(0)?,
            semester: row.get(1)?,
            event_date: row.get(2)?,
            day_number: row.get(3)?,
            month_short: row.get(4)?,
            title: row.get(5)?,
            category: row.get(6)?,
        })
    })?;

    let mut calendar_events = Vec::new();
    for event_res in cal_rows {
        calendar_events.push(event_res?);
    }

    Ok(AcademicProfileSummary {
        semester: clean_semester.to_string(),
        active_campus,
        campuses,
        total_courses,
        total_absences,
        total_max_absences,
        total_cancelled_classes,
        courses_at_risk,
        pending_assessments,
        completed_assessments,
        calendar_events,
    })
}

pub fn export_schedule_payload(conn: &Connection, semester: &str) -> Result<String, CampusError> {
    let clean_semester = normalize_semester(semester);

    let departments = list_departments(conn)?;

    let mut course_stmt = conn.prepare(
        "SELECT
            id,
            semester,
            department_id,
            color_override_hex,
            name,
            professor,
            workload_hours,
            max_absences,
            manual_absences,
            notes
         FROM courses
         WHERE semester = ?1
         ORDER BY rowid ASC",
    )?;

    let course_rows = course_stmt.query_map(params![clean_semester], |row| {
        Ok(ExportedCourseRecord {
            id: row.get(0)?,
            semester: row.get(1)?,
            department_id: row.get(2)?,
            color_override_hex: row.get(3)?,
            name: row.get(4)?,
            professor: row.get(5)?,
            workload_hours: row.get(6)?,
            max_absences: row.get(7)?,
            manual_absences: row.get(8)?,
            notes: row.get(9)?,
        })
    })?;

    let mut courses = Vec::new();
    for item in course_rows {
        courses.push(item?);
    }

    let mut slot_stmt = conn.prepare(
        "SELECT
            s.id,
            s.course_id,
            s.day_of_week,
            s.start_time,
            s.end_time,
            s.building,
            s.room,
            s.short_location,
            s.class_weight
         FROM schedule_slots s
         JOIN courses c ON c.id = s.course_id
         WHERE c.semester = ?1
         ORDER BY s.rowid ASC",
    )?;

    let slot_rows = slot_stmt.query_map(params![clean_semester], |row| {
        Ok(ExportedScheduleSlotRecord {
            id: row.get(0)?,
            course_id: row.get(1)?,
            day_of_week: row.get(2)?,
            start_time: row.get(3)?,
            end_time: row.get(4)?,
            building: row.get(5)?,
            room: row.get(6)?,
            short_location: row.get(7)?,
            class_weight: row.get(8)?,
        })
    })?;

    let mut schedule_slots = Vec::new();
    for item in slot_rows {
        schedule_slots.push(item?);
    }

    let payload = ScheduleExportPayload {
        semester: clean_semester.to_string(),
        departments,
        courses,
        schedule_slots,
    };

    serde_json::to_string_pretty(&payload)
        .map_err(|err| CampusError::InvalidInput(format!("Falha ao serializar grade JSON: {err}")))
}

pub fn import_schedule_payload(conn: &Connection, json_payload: &str) -> Result<u32, CampusError> {
    let trimmed = json_payload.trim();
    if trimmed.is_empty() {
        return Err(CampusError::InvalidInput(
            "O payload JSON de importação da grade não pode ser vazio".into(),
        ));
    }

    let payload: ScheduleExportPayload = serde_json::from_str(trimmed).map_err(|err| {
        CampusError::InvalidInput(format!("JSON de grade horária inválido: {err}"))
    })?;

    let tx = conn.unchecked_transaction()?;

    for dept in &payload.departments {
        let dept_id = dept.id.trim();
        if !dept_id.is_empty() {
            tx.execute(
                "INSERT INTO departments (id, name, color_hex)
                 VALUES (?1, ?2, ?3)
                 ON CONFLICT(id) DO UPDATE SET
                     name = excluded.name,
                     color_hex = excluded.color_hex",
                params![dept_id, dept.name.trim(), dept.color_hex.trim()],
            )?;
        }
    }

    let mut imported_courses: u32 = 0;

    for course in &payload.courses {
        let course_id = course.id.trim();
        if course_id.is_empty() {
            return Err(CampusError::InvalidInput(
                "Disciplina no JSON possui id vazio".into(),
            ));
        }
        let dept_id = if course.department_id.trim().is_empty() {
            "comp"
        } else {
            course.department_id.trim()
        };

        tx.execute(
            "INSERT OR IGNORE INTO departments (id, name, color_hex)
             VALUES (?1, ?2, '#3B68FF')",
            params![dept_id, dept_id],
        )?;

        let semester = if course.semester.trim().is_empty() {
            if payload.semester.trim().is_empty() {
                "2026.2"
            } else {
                payload.semester.trim()
            }
        } else {
            course.semester.trim()
        };

        let max_absences = if course.max_absences == 0 {
            calculate_max_absences(course.workload_hours)
        } else {
            course.max_absences
        };

        tx.execute(
            "INSERT INTO courses (
                id,
                semester,
                department_id,
                color_override_hex,
                name,
                professor,
                workload_hours,
                max_absences,
                manual_absences,
                notes
            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)
            ON CONFLICT(id) DO UPDATE SET
                semester = excluded.semester,
                department_id = excluded.department_id,
                color_override_hex = excluded.color_override_hex,
                name = excluded.name,
                professor = excluded.professor,
                workload_hours = excluded.workload_hours,
                max_absences = excluded.max_absences,
                manual_absences = excluded.manual_absences,
                notes = excluded.notes",
            params![
                course_id,
                semester,
                dept_id,
                course.color_override_hex.as_deref(),
                course.name.trim(),
                course.professor.trim(),
                course.workload_hours,
                max_absences,
                course.manual_absences,
                course.notes,
            ],
        )?;

        imported_courses += 1;
    }

    for slot in &payload.schedule_slots {
        let slot_id = slot.id.trim();
        let course_id = slot.course_id.trim();
        if slot_id.is_empty() || course_id.is_empty() {
            continue;
        }
        let weight = if slot.class_weight == 0 {
            1
        } else {
            slot.class_weight
        };

        tx.execute(
            "INSERT INTO schedule_slots (
                id,
                course_id,
                day_of_week,
                start_time,
                end_time,
                building,
                room,
                short_location,
                class_weight
            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)
            ON CONFLICT(id) DO UPDATE SET
                course_id = excluded.course_id,
                day_of_week = excluded.day_of_week,
                start_time = excluded.start_time,
                end_time = excluded.end_time,
                building = excluded.building,
                room = excluded.room,
                short_location = excluded.short_location,
                class_weight = excluded.class_weight",
            params![
                slot_id,
                course_id,
                slot.day_of_week,
                slot.start_time.trim(),
                slot.end_time.trim(),
                slot.building.trim(),
                slot.room.trim(),
                slot.short_location.trim(),
                weight,
            ],
        )?;
    }

    tx.commit()?;
    Ok(imported_courses)
}
