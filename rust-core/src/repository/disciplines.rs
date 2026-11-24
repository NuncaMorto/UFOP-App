use std::collections::HashSet;

use rusqlite::{Connection, OptionalExtension, params};

use crate::CampusError;
use crate::domain::agenda::{
    format_building_and_room, format_pt_br_full_date, parse_assessment_due_date,
};
use crate::domain::attendance::{calculate_max_absences, compute_current_absences};
use crate::domain::timetable::{
    DEFAULT_TIMETABLE_BANDS, TIMETABLE_WEEKDAYS, day_of_week_short_label,
    format_timetable_grid_title,
};
use crate::models::{
    AbsenceHistoryEntry, AssessmentItem, AttendanceStatus, CourseScheduleSlotItem,
    DepartmentColorOption, DisciplineDetail, DisciplineSummaryCard, TimetableCell,
    TimetableDayCell, TimetableRow, UfopCurriculumTemplate, WeeklyTimetable,
};

use super::helpers::{
    ensure_course_exists, ensure_department_exists, next_entity_seq, normalize_semester,
    parse_time_hhmm,
};

const SEED_002_SQL: &str = include_str!("../../migrations/002_seed_ufop_2026_2.sql");
const SEED_003_SQL: &str = include_str!("../../migrations/003_sprint2_ufop_scale.sql");
const SEED_005_SQL: &str = include_str!("../../migrations/005_sprint13_official_calendars.sql");

pub fn list_departments(conn: &Connection) -> Result<Vec<DepartmentColorOption>, CampusError> {
    let mut stmt = conn.prepare(
        "SELECT id, name, color_hex
         FROM departments
         ORDER BY rowid ASC",
    )?;

    let rows = stmt.query_map([], |row| {
        Ok(DepartmentColorOption {
            id: row.get(0)?,
            name: row.get(1)?,
            color_hex: row.get(2)?,
        })
    })?;

    let mut departments = Vec::new();
    for item in rows {
        departments.push(item?);
    }
    Ok(departments)
}

pub fn get_discipline_summary_by_id(
    conn: &Connection,
    course_id: &str,
) -> Result<DisciplineSummaryCard, CampusError> {
    let trimmed_id = course_id.trim();
    if trimmed_id.is_empty() {
        return Err(CampusError::InvalidInput(
            "O identificador da disciplina (course_id) não pode ser vazio".into(),
        ));
    }

    let row_opt = conn
        .query_row(
            "SELECT
                c.id,
                c.name,
                d.id AS department_id,
                d.name AS department_name,
                COALESCE(c.color_override_hex, d.color_hex) AS resolved_color_hex,
                c.workload_hours,
                c.manual_absences
             FROM courses c
             JOIN departments d ON d.id = c.department_id
             WHERE c.id = ?1",
            params![trimmed_id],
            |row| {
                let c_id: String = row.get(0)?;
                let name: String = row.get(1)?;
                let department_id: String = row.get(2)?;
                let department_name: String = row.get(3)?;
                let color_hex: String = row.get(4)?;
                let workload_hours: u32 = row.get(5)?;
                let manual_absences: u32 = row.get(6)?;
                Ok((
                    c_id,
                    name,
                    department_id,
                    department_name,
                    color_hex,
                    workload_hours,
                    manual_absences,
                ))
            },
        )
        .optional()?;

    let Some((
        c_id,
        name,
        department_id,
        department_name,
        color_hex,
        workload_hours,
        manual_absences,
    )) = row_opt
    else {
        return Err(CampusError::NotFound(format!(
            "Disciplina não encontrada: '{trimmed_id}'"
        )));
    };

    let absent_session_weights: u32 = conn.query_row(
        "SELECT COALESCE(SUM(s.class_weight), 0)
         FROM class_sessions cs
         JOIN schedule_slots s ON s.id = cs.slot_id
         WHERE cs.course_id = ?1 AND cs.status = 'ABSENT'",
        params![&c_id],
        |row| row.get(0),
    )?;

    let cancelled_classes: u32 = conn.query_row(
        "SELECT COUNT(*)
         FROM class_sessions
         WHERE course_id = ?1 AND status = 'CANCELLED'",
        params![&c_id],
        |row| row.get(0),
    )?;

    let max_absences = calculate_max_absences(workload_hours);
    let current_absences = compute_current_absences(manual_absences, absent_session_weights);

    Ok(DisciplineSummaryCard {
        course_id: c_id,
        name,
        department_id,
        department_name,
        color_hex,
        workload_hours,
        current_absences,
        max_absences,
        cancelled_classes,
    })
}

pub fn list_disciplines(
    conn: &Connection,
    semester: &str,
    department_id: Option<&str>,
) -> Result<Vec<DisciplineSummaryCard>, CampusError> {
    let clean_semester = normalize_semester(semester);
    let clean_dept = department_id.map(str::trim).filter(|val| !val.is_empty());

    let mut stmt = conn.prepare(
        "SELECT c.id
         FROM courses c
         WHERE c.semester = ?1
           AND (?2 IS NULL OR c.department_id = ?2)
         ORDER BY c.rowid ASC",
    )?;

    let course_ids = stmt.query_map(params![clean_semester, clean_dept], |row| {
        row.get::<_, String>(0)
    })?;

    let mut cards = Vec::new();
    for id_res in course_ids {
        let course_id = id_res?;
        cards.push(get_discipline_summary_by_id(conn, &course_id)?);
    }

    Ok(cards)
}

pub fn get_discipline_detail(
    conn: &Connection,
    course_id: &str,
) -> Result<DisciplineDetail, CampusError> {
    let summary = get_discipline_summary_by_id(conn, course_id)?;

    let (semester, notes): (String, String) = conn.query_row(
        "SELECT semester, notes FROM courses WHERE id = ?1",
        params![&summary.course_id],
        |row| Ok((row.get(0)?, row.get(1)?)),
    )?;

    let mut stmt = conn.prepare(
        "SELECT id, course_id, title, kind, due_date, due_time, completed
         FROM assessments
         WHERE course_id = ?1
         ORDER BY due_date ASC, due_time ASC, id ASC",
    )?;

    let rows = stmt.query_map(params![&summary.course_id], |row| {
        let id: String = row.get(0)?;
        let c_id: String = row.get(1)?;
        let title: String = row.get(2)?;
        let kind: String = row.get(3)?;
        let due_date: String = row.get(4)?;
        let due_time: String = row.get(5)?;
        let completed_int: i32 = row.get(6)?;
        let (day_number, month_short) = parse_assessment_due_date(&due_date);

        Ok(AssessmentItem {
            id,
            course_id: c_id,
            day_number,
            month_short,
            title,
            kind,
            due_date,
            due_time,
            completed: completed_int != 0,
        })
    })?;

    let mut assessments = Vec::new();
    for item in rows {
        assessments.push(item?);
    }

    Ok(DisciplineDetail {
        summary,
        semester,
        assessments,
        notes,
    })
}

pub fn update_discipline_notes(
    conn: &Connection,
    course_id: &str,
    notes: &str,
) -> Result<DisciplineDetail, CampusError> {
    let clean_course_id = course_id.trim();
    if clean_course_id.is_empty() {
        return Err(CampusError::InvalidInput(
            "O identificador da disciplina (course_id) não pode ser vazio".into(),
        ));
    }

    let affected = conn.execute(
        "UPDATE courses SET notes = ?1 WHERE id = ?2",
        params![notes, clean_course_id],
    )?;

    if affected == 0 {
        return Err(CampusError::NotFound(format!(
            "Disciplina não encontrada: '{clean_course_id}'"
        )));
    }

    get_discipline_detail(conn, clean_course_id)
}

pub fn get_weekly_timetable(
    conn: &Connection,
    semester: &str,
) -> Result<WeeklyTimetable, CampusError> {
    let clean_semester = normalize_semester(semester);

    let mut stmt = conn.prepare(
        "SELECT
            s.id AS slot_id,
            s.day_of_week,
            s.start_time,
            s.end_time,
            s.short_location,
            c.id AS course_id,
            c.name AS course_name,
            d.id AS department_id,
            d.name AS department_name,
            COALESCE(c.color_override_hex, d.color_hex) AS color_hex
         FROM schedule_slots s
         JOIN courses c ON c.id = s.course_id
         JOIN departments d ON d.id = c.department_id
         WHERE c.semester = ?1
         ORDER BY s.start_time ASC, s.day_of_week ASC",
    )?;

    struct SlotEntry {
        day_of_week: u32,
        start_time: String,
        end_time: String,
        cell: TimetableCell,
    }

    let slot_rows = stmt.query_map(params![clean_semester], |row| {
        let slot_id: String = row.get(0)?;
        let day_of_week: u32 = row.get(1)?;
        let start_time: String = row.get(2)?;
        let end_time: String = row.get(3)?;
        let short_location: String = row.get(4)?;
        let course_id: String = row.get(5)?;
        let course_name: String = row.get(6)?;
        let department_id: String = row.get(7)?;
        let department_name: String = row.get(8)?;
        let color_hex: String = row.get(9)?;

        Ok(SlotEntry {
            day_of_week,
            start_time,
            end_time,
            cell: TimetableCell {
                slot_id,
                course_id,
                course_name,
                short_location,
                department_id,
                department_name,
                color_hex,
            },
        })
    })?;

    let mut entries = Vec::new();
    for entry in slot_rows {
        entries.push(entry?);
    }

    let mut bands: Vec<(String, String)> = DEFAULT_TIMETABLE_BANDS
        .iter()
        .map(|(s, e)| ((*s).to_string(), (*e).to_string()))
        .collect();

    for entry in &entries {
        if !bands
            .iter()
            .any(|(s, e)| s == &entry.start_time && e == &entry.end_time)
        {
            bands.push((entry.start_time.clone(), entry.end_time.clone()));
        }
    }
    bands.sort_by(|a, b| a.0.cmp(&b.0).then_with(|| a.1.cmp(&b.1)));

    let mut active_weekdays: Vec<(u32, &str)> = TIMETABLE_WEEKDAYS.to_vec();
    if entries.iter().any(|e| e.day_of_week == 6) {
        active_weekdays.push((6, day_of_week_short_label(6)));
    }
    if entries.iter().any(|e| e.day_of_week == 7) {
        active_weekdays.push((7, day_of_week_short_label(7)));
    }

    let rows = bands
        .into_iter()
        .map(|(start_time, end_time)| {
            let cells = active_weekdays
                .iter()
                .map(|&(day_of_week, day_label)| {
                    let cell = entries
                        .iter()
                        .find(|entry| {
                            entry.day_of_week == day_of_week
                                && entry.start_time == start_time
                                && entry.end_time == end_time
                        })
                        .map(|entry| entry.cell.clone());

                    TimetableDayCell {
                        day_of_week,
                        day_label: day_label.to_string(),
                        cell,
                    }
                })
                .collect();

            TimetableRow {
                start_time,
                end_time,
                cells,
            }
        })
        .collect();

    let days = active_weekdays
        .iter()
        .map(|&(_, label)| label.to_string())
        .collect();

    let legend = {
        let mut legend_stmt = conn.prepare(
            "SELECT d.id, d.name, d.color_hex
             FROM departments d
             WHERE EXISTS (
                 SELECT 1
                 FROM courses c
                 WHERE c.semester = ?1 AND c.department_id = d.id
             )
             ORDER BY d.rowid ASC",
        )?;
        let legend_rows = legend_stmt.query_map(params![clean_semester], |row| {
            Ok(DepartmentColorOption {
                id: row.get(0)?,
                name: row.get(1)?,
                color_hex: row.get(2)?,
            })
        })?;
        let mut active_legend = Vec::new();
        for item in legend_rows {
            active_legend.push(item?);
        }
        if active_legend.is_empty() {
            list_departments(conn)?
        } else {
            active_legend
        }
    };

    Ok(WeeklyTimetable {
        semester: clean_semester.to_string(),
        grid_title: format_timetable_grid_title(clean_semester),
        days,
        rows,
        legend,
    })
}

fn validate_course_fields<'a>(
    name: &'a str,
    professor: &'a str,
    workload_hours: u32,
) -> Result<(&'a str, &'a str, u32), CampusError> {
    let clean_name = name.trim();
    if clean_name.is_empty() {
        return Err(CampusError::InvalidInput(
            "O nome da disciplina não pode ser vazio".into(),
        ));
    }

    let clean_professor = professor.trim();
    if clean_professor.is_empty() {
        return Err(CampusError::InvalidInput(
            "O nome do professor não pode ser vazio".into(),
        ));
    }

    if workload_hours == 0 {
        return Err(CampusError::InvalidInput(
            "A carga horária da disciplina deve ser maior que zero".into(),
        ));
    }

    Ok((
        clean_name,
        clean_professor,
        calculate_max_absences(workload_hours),
    ))
}

pub fn create_course(
    conn: &Connection,
    semester: &str,
    department_id: &str,
    name: &str,
    professor: &str,
    workload_hours: u32,
    color_override_hex: Option<&str>,
) -> Result<DisciplineSummaryCard, CampusError> {
    let clean_semester = semester.trim();
    if clean_semester.is_empty() {
        return Err(CampusError::InvalidInput(
            "O semestre da disciplina não pode ser vazio".into(),
        ));
    }

    let clean_dept = department_id.trim();
    if clean_dept.is_empty() {
        return Err(CampusError::InvalidInput(
            "O departamento (department_id) não pode ser vazio".into(),
        ));
    }

    let (clean_name, clean_professor, max_absences) =
        validate_course_fields(name, professor, workload_hours)?;
    let clean_dept = ensure_department_exists(conn, clean_dept)?;

    let mut course_id = format!("course_{}", next_entity_seq());
    while conn
        .prepare("SELECT 1 FROM courses WHERE id = ?1")?
        .exists(params![&course_id])?
    {
        course_id = format!("course_{}", next_entity_seq());
    }

    let clean_color = color_override_hex
        .map(str::trim)
        .filter(|val| !val.is_empty());

    conn.execute(
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
        ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, 0, '')",
        params![
            &course_id,
            clean_semester,
            clean_dept,
            clean_color,
            clean_name,
            clean_professor,
            workload_hours,
            max_absences,
        ],
    )?;

    get_discipline_summary_by_id(conn, &course_id)
}

pub fn update_course(
    conn: &Connection,
    course_id: &str,
    name: &str,
    professor: &str,
    department_id: &str,
    workload_hours: u32,
) -> Result<DisciplineSummaryCard, CampusError> {
    let clean_course_id = ensure_course_exists(conn, course_id)?;
    let (clean_name, clean_professor, max_absences) =
        validate_course_fields(name, professor, workload_hours)?;
    let clean_dept = ensure_department_exists(conn, department_id)?;

    conn.execute(
        "UPDATE courses
         SET name = ?1,
             professor = ?2,
             department_id = ?3,
             workload_hours = ?4,
             max_absences = ?5,
             color_override_hex = NULL
         WHERE id = ?6",
        params![
            clean_name,
            clean_professor,
            clean_dept,
            workload_hours,
            max_absences,
            clean_course_id,
        ],
    )?;

    get_discipline_summary_by_id(conn, clean_course_id)
}

pub fn delete_course(conn: &Connection, course_id: &str) -> Result<(), CampusError> {
    let clean_id = course_id.trim();
    if clean_id.is_empty() {
        return Err(CampusError::InvalidInput(
            "O identificador da disciplina (course_id) não pode ser vazio".into(),
        ));
    }

    let affected = conn.execute("DELETE FROM courses WHERE id = ?1", params![clean_id])?;
    if affected == 0 {
        return Err(CampusError::NotFound(format!(
            "Disciplina não encontrada: '{clean_id}'"
        )));
    }

    Ok(())
}

struct ValidatedSlotInput<'a> {
    start_mins: u32,
    end_mins: u32,
    clean_start: String,
    clean_end: String,
    clean_building: &'a str,
    clean_room: &'a str,
    final_short: String,
}

fn validate_schedule_slot_fields<'a>(
    day_of_week: u32,
    start_time: &str,
    end_time: &str,
    building: &'a str,
    room: &'a str,
    short_location: &str,
) -> Result<ValidatedSlotInput<'a>, CampusError> {
    if !(1..=7).contains(&day_of_week) {
        return Err(CampusError::InvalidInput(format!(
            "Dia da semana inválido ({day_of_week}): use 1 (SEG) a 7 (DOM)."
        )));
    }

    let (start_mins, clean_start) = parse_time_hhmm(start_time)?;
    let (end_mins, clean_end) = parse_time_hhmm(end_time)?;
    if start_mins >= end_mins {
        return Err(CampusError::InvalidInput(
            "O horário de término deve ser posterior ao horário de início.".into(),
        ));
    }

    let clean_building = building.trim();
    let clean_room = room.trim();
    let clean_short_raw = short_location.trim();
    if clean_building.is_empty() && clean_room.is_empty() && clean_short_raw.is_empty() {
        return Err(CampusError::InvalidInput(
            "Informe o prédio ou a sala do horário de aula.".into(),
        ));
    }

    let final_short = if !clean_short_raw.is_empty() {
        clean_short_raw.to_string()
    } else if !clean_building.is_empty() && !clean_room.is_empty() {
        format_building_and_room(clean_building, clean_room)
    } else if !clean_building.is_empty() {
        clean_building.to_string()
    } else {
        clean_room.to_string()
    };

    Ok(ValidatedSlotInput {
        start_mins,
        end_mins,
        clean_start,
        clean_end,
        clean_building,
        clean_room,
        final_short,
    })
}

fn check_schedule_slot_conflict(
    conn: &Connection,
    semester: &str,
    day_of_week: u32,
    input: &ValidatedSlotInput<'_>,
    exclude_slot_id: Option<&str>,
) -> Result<(), CampusError> {
    let mut conflict_stmt = conn.prepare(
        "SELECT c.name, s.start_time, s.end_time
         FROM schedule_slots s
         JOIN courses c ON c.id = s.course_id
         WHERE c.semester = ?1
           AND s.day_of_week = ?2
           AND (?3 IS NULL OR s.id != ?3)
         ORDER BY s.start_time ASC, s.id ASC",
    )?;
    let existing_rows =
        conflict_stmt.query_map(params![semester, day_of_week, exclude_slot_id], |row| {
            Ok((
                row.get::<_, String>(0)?,
                row.get::<_, String>(1)?,
                row.get::<_, String>(2)?,
            ))
        })?;
    for row_res in existing_rows {
        let (existing_course_name, existing_start, existing_end) = row_res?;
        let exact_same_start = existing_start.trim() == input.clean_start;
        let overlaps = if let (Ok((ex_start_mins, _)), Ok((ex_end_mins, _))) = (
            parse_time_hhmm(&existing_start),
            parse_time_hhmm(&existing_end),
        ) {
            ex_start_mins < input.end_mins && input.start_mins < ex_end_mins
        } else {
            false
        };
        if exact_same_start || overlaps {
            return Err(CampusError::InvalidInput(format!(
                "Conflito de horário: já existe '{}' neste dia às {}.",
                existing_course_name,
                existing_start.trim()
            )));
        }
    }

    Ok(())
}

#[allow(clippy::too_many_arguments)]
pub fn add_schedule_slot(
    conn: &Connection,
    course_id: &str,
    day_of_week: u32,
    start_time: &str,
    end_time: &str,
    building: &str,
    room: &str,
    short_location: &str,
) -> Result<CourseScheduleSlotItem, CampusError> {
    let clean_course_id = course_id.trim();
    if clean_course_id.is_empty() {
        return Err(CampusError::InvalidInput(
            "O identificador da disciplina (course_id) não pode ser vazio".into(),
        ));
    }

    let validated = validate_schedule_slot_fields(
        day_of_week,
        start_time,
        end_time,
        building,
        room,
        short_location,
    )?;

    let semester_opt: Option<String> = conn
        .query_row(
            "SELECT semester FROM courses WHERE id = ?1",
            params![clean_course_id],
            |row| row.get(0),
        )
        .optional()?;
    let Some(semester) = semester_opt else {
        return Err(CampusError::NotFound(format!(
            "Disciplina não encontrada: '{clean_course_id}'"
        )));
    };

    check_schedule_slot_conflict(conn, &semester, day_of_week, &validated, None)?;

    let mut slot_id = format!(
        "slot_{}_{}_{}",
        clean_course_id,
        day_of_week,
        next_entity_seq()
    );
    while conn
        .prepare("SELECT 1 FROM schedule_slots WHERE id = ?1")?
        .exists(params![&slot_id])?
    {
        slot_id = format!(
            "slot_{}_{}_{}",
            clean_course_id,
            day_of_week,
            next_entity_seq()
        );
    }

    conn.execute(
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
        ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, 1)",
        params![
            &slot_id,
            clean_course_id,
            day_of_week,
            &validated.clean_start,
            &validated.clean_end,
            validated.clean_building,
            validated.clean_room,
            &validated.final_short,
        ],
    )?;

    Ok(CourseScheduleSlotItem {
        slot_id,
        course_id: clean_course_id.to_string(),
        day_of_week,
        day_label: day_of_week_short_label(day_of_week).to_string(),
        start_time: validated.clean_start,
        end_time: validated.clean_end,
        building: validated.clean_building.to_string(),
        room: validated.clean_room.to_string(),
        short_location: validated.final_short,
    })
}

#[allow(clippy::too_many_arguments)]
pub fn update_schedule_slot(
    conn: &Connection,
    slot_id: &str,
    day_of_week: u32,
    start_time: &str,
    end_time: &str,
    building: &str,
    room: &str,
    short_location: &str,
) -> Result<CourseScheduleSlotItem, CampusError> {
    let clean_slot_id = slot_id.trim();
    if clean_slot_id.is_empty() {
        return Err(CampusError::InvalidInput(
            "O identificador do horário (slot_id) não pode ser vazio".into(),
        ));
    }

    let validated = validate_schedule_slot_fields(
        day_of_week,
        start_time,
        end_time,
        building,
        room,
        short_location,
    )?;

    let slot_meta_opt: Option<(String, String)> = conn
        .query_row(
            "SELECT s.course_id, c.semester
             FROM schedule_slots s
             JOIN courses c ON c.id = s.course_id
             WHERE s.id = ?1",
            params![clean_slot_id],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )
        .optional()?;
    let Some((course_id, semester)) = slot_meta_opt else {
        return Err(CampusError::NotFound(format!(
            "Horário de aula não encontrado: '{clean_slot_id}'"
        )));
    };

    check_schedule_slot_conflict(
        conn,
        &semester,
        day_of_week,
        &validated,
        Some(clean_slot_id),
    )?;

    conn.execute(
        "UPDATE schedule_slots
         SET day_of_week = ?1,
             start_time = ?2,
             end_time = ?3,
             building = ?4,
             room = ?5,
             short_location = ?6
         WHERE id = ?7",
        params![
            day_of_week,
            &validated.clean_start,
            &validated.clean_end,
            validated.clean_building,
            validated.clean_room,
            &validated.final_short,
            clean_slot_id,
        ],
    )?;

    Ok(CourseScheduleSlotItem {
        slot_id: clean_slot_id.to_string(),
        course_id,
        day_of_week,
        day_label: day_of_week_short_label(day_of_week).to_string(),
        start_time: validated.clean_start,
        end_time: validated.clean_end,
        building: validated.clean_building.to_string(),
        room: validated.clean_room.to_string(),
        short_location: validated.final_short,
    })
}

pub fn delete_schedule_slot(conn: &Connection, slot_id: &str) -> Result<(), CampusError> {
    let clean_id = slot_id.trim();
    if clean_id.is_empty() {
        return Err(CampusError::InvalidInput(
            "O identificador do horário (slot_id) não pode ser vazio".into(),
        ));
    }

    let affected = conn.execute(
        "DELETE FROM schedule_slots WHERE id = ?1",
        params![clean_id],
    )?;
    if affected == 0 {
        return Err(CampusError::NotFound(format!(
            "Horário de aula não encontrado: '{clean_id}'"
        )));
    }

    Ok(())
}

pub fn list_course_slots(
    conn: &Connection,
    course_id: &str,
) -> Result<Vec<CourseScheduleSlotItem>, CampusError> {
    let clean_course_id = ensure_course_exists(conn, course_id)?;

    let mut stmt = conn.prepare(
        "SELECT
            id,
            course_id,
            day_of_week,
            start_time,
            end_time,
            building,
            room,
            short_location
         FROM schedule_slots
         WHERE course_id = ?1
         ORDER BY day_of_week ASC, start_time ASC, id ASC",
    )?;

    let rows = stmt.query_map(params![clean_course_id], |row| {
        let day_of_week: u32 = row.get(2)?;
        Ok(CourseScheduleSlotItem {
            slot_id: row.get(0)?,
            course_id: row.get(1)?,
            day_of_week,
            day_label: day_of_week_short_label(day_of_week).to_string(),
            start_time: row.get(3)?,
            end_time: row.get(4)?,
            building: row.get(5)?,
            room: row.get(6)?,
            short_location: row.get(7)?,
        })
    })?;

    let mut slots = Vec::new();
    for item in rows {
        slots.push(item?);
    }
    Ok(slots)
}

pub fn list_ufop_templates(conn: &Connection) -> Result<Vec<UfopCurriculumTemplate>, CampusError> {
    let mut stmt = conn.prepare(
        "SELECT
            t.id,
            t.code,
            t.name,
            t.department_name,
            t.campus_name,
            t.description,
            COUNT(DISTINCT tc.course_id) AS courses_count
         FROM ufop_templates t
         LEFT JOIN ufop_template_courses tc ON tc.template_id = t.id
         GROUP BY t.id, t.code, t.name, t.department_name, t.campus_name, t.description
         ORDER BY t.rowid ASC",
    )?;

    let rows = stmt.query_map([], |row| {
        Ok(UfopCurriculumTemplate {
            id: row.get(0)?,
            code: row.get(1)?,
            name: row.get(2)?,
            department_name: row.get(3)?,
            campus_name: row.get(4)?,
            description: row.get(5)?,
            courses_count: row.get(6)?,
        })
    })?;

    let mut templates = Vec::new();
    for item in rows {
        templates.push(item?);
    }
    Ok(templates)
}

pub fn apply_ufop_template(
    conn: &Connection,
    template_id: &str,
    semester: &str,
) -> Result<u32, CampusError> {
    let clean_template_id = template_id.trim();
    if clean_template_id.is_empty() {
        return Err(CampusError::InvalidInput(
            "O identificador do template (template_id) não pode ser vazio".into(),
        ));
    }

    let clean_semester = normalize_semester(semester);

    let template_exists: bool = conn
        .prepare("SELECT 1 FROM ufop_templates WHERE id = ?1")?
        .exists(params![clean_template_id])?;
    if !template_exists {
        return Err(CampusError::NotFound(format!(
            "Template oficial UFOP não encontrado: '{clean_template_id}'"
        )));
    }

    struct TemplateCourseSlotRow {
        id: String,
        course_id: String,
        department_id: String,
        color_override_hex: Option<String>,
        name: String,
        professor: String,
        workload_hours: u32,
        day_of_week: u32,
        start_time: String,
        end_time: String,
        building: String,
        room: String,
        short_location: String,
    }

    let template_rows: Vec<TemplateCourseSlotRow> = {
        let mut stmt = conn.prepare(
            "SELECT
                id,
                course_id,
                department_id,
                color_override_hex,
                name,
                professor,
                workload_hours,
                day_of_week,
                start_time,
                end_time,
                building,
                room,
                short_location
             FROM ufop_template_courses
             WHERE template_id = ?1
             ORDER BY rowid ASC",
        )?;

        let mapped = stmt.query_map(params![clean_template_id], |row| {
            Ok(TemplateCourseSlotRow {
                id: row.get(0)?,
                course_id: row.get(1)?,
                department_id: row.get(2)?,
                color_override_hex: row.get(3)?,
                name: row.get(4)?,
                professor: row.get(5)?,
                workload_hours: row.get(6)?,
                day_of_week: row.get(7)?,
                start_time: row.get(8)?,
                end_time: row.get(9)?,
                building: row.get(10)?,
                room: row.get(11)?,
                short_location: row.get(12)?,
            })
        })?;

        let mut list = Vec::new();
        for item in mapped {
            list.push(item?);
        }
        list
    };

    let semester_slug: String = clean_semester
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() { c } else { '_' })
        .collect();

    let tx = conn.unchecked_transaction()?;

    tx.execute(
        "DELETE FROM courses WHERE semester = ?1",
        params![clean_semester],
    )?;

    let mut inserted_courses = HashSet::new();
    let mut applied_courses_count: u32 = 0;

    for row in &template_rows {
        let target_course_id = format!("{}_{}", row.course_id, semester_slug);
        if inserted_courses.insert(target_course_id.clone()) {
            let max_absences = calculate_max_absences(row.workload_hours);
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
                ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, 0, '')",
                params![
                    &target_course_id,
                    clean_semester,
                    &row.department_id,
                    row.color_override_hex.as_deref(),
                    &row.name,
                    &row.professor,
                    row.workload_hours,
                    max_absences,
                ],
            )?;
            applied_courses_count += 1;
        }

        let target_slot_id = format!("slot_{}_{}", row.id, semester_slug);
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
            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, 1)",
            params![
                &target_slot_id,
                &target_course_id,
                row.day_of_week,
                &row.start_time,
                &row.end_time,
                &row.building,
                &row.room,
                &row.short_location,
            ],
        )?;
    }

    tx.commit()?;
    Ok(applied_courses_count)
}

pub fn list_absence_history(
    conn: &Connection,
    course_id: &str,
) -> Result<Vec<AbsenceHistoryEntry>, CampusError> {
    let clean_course_id = ensure_course_exists(conn, course_id)?;

    let mut stmt = conn.prepare(
        "SELECT
            cs.id,
            cs.slot_id,
            cs.course_id,
            cs.session_date,
            s.start_time,
            s.end_time,
            cs.status,
            s.class_weight
         FROM class_sessions cs
         JOIN schedule_slots s ON s.id = cs.slot_id
         WHERE cs.course_id = ?1
           AND cs.status IN ('ABSENT', 'CANCELLED')
         ORDER BY cs.session_date DESC, s.start_time DESC, cs.id DESC",
    )?;

    let rows = stmt.query_map(params![clean_course_id], |row| {
        Ok((
            row.get::<_, String>(0)?,
            row.get::<_, String>(1)?,
            row.get::<_, String>(2)?,
            row.get::<_, String>(3)?,
            row.get::<_, String>(4)?,
            row.get::<_, String>(5)?,
            row.get::<_, String>(6)?,
            row.get::<_, u32>(7)?,
        ))
    })?;

    let mut history = Vec::new();
    for row_res in rows {
        let (
            session_id,
            slot_id,
            c_id,
            session_date,
            start_time,
            end_time,
            status_raw,
            class_weight,
        ) = row_res?;

        let status = AttendanceStatus::from_db_str(&status_raw).ok_or_else(|| {
            CampusError::Database(format!("Status de presença inválido: '{status_raw}'"))
        })?;
        let formatted_date =
            format_pt_br_full_date(&session_date).unwrap_or_else(|_| session_date.clone());

        history.push(AbsenceHistoryEntry {
            session_id,
            slot_id,
            course_id: c_id,
            session_date,
            formatted_date,
            start_time,
            end_time,
            status,
            class_weight,
        });
    }

    Ok(history)
}

pub fn reset_semester_to_default_seed(
    conn: &Connection,
    semester: &str,
) -> Result<u32, CampusError> {
    let clean_semester = normalize_semester(semester);
    let tx = conn.unchecked_transaction()?;

    tx.execute(
        "DELETE FROM courses WHERE semester = ?1",
        params![clean_semester],
    )?;

    if clean_semester == "2026.2" {
        tx.execute(
            "DELETE FROM commitments WHERE commitment_date = '2026-11-17'",
            [],
        )?;
    }

    tx.execute_batch(SEED_002_SQL)?;

    let has_completed_column: bool = tx
        .prepare("SELECT 1 FROM pragma_table_info('assessments') WHERE name = 'completed'")?
        .exists([])?;
    if !has_completed_column {
        tx.execute_batch(SEED_003_SQL)?;
    } else {
        let without_alter = SEED_003_SQL
            .lines()
            .filter(|line| {
                !line
                    .trim_start()
                    .to_ascii_uppercase()
                    .starts_with("ALTER TABLE ASSESSMENTS")
            })
            .collect::<Vec<_>>()
            .join("\n");
        tx.execute_batch(&without_alter)?;
    }

    tx.execute_batch(SEED_005_SQL)?;

    let count: u32 = tx.query_row(
        "SELECT COUNT(*) FROM courses WHERE semester = ?1",
        params![clean_semester],
        |row| row.get(0),
    )?;

    tx.commit()?;
    Ok(count)
}
