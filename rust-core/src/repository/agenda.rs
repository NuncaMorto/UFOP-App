use rusqlite::{Connection, OptionalExtension, params};

use crate::CampusError;
use crate::domain::agenda::{
    DEFAULT_COMMITMENT_COLOR_HEX, DEFAULT_WEATHER_CONDITION, DEFAULT_WEATHER_TEMP_C,
    DEFAULT_WEEK_NUMBER, build_week_47_days, format_building_and_room, format_month_year_label,
    format_pt_br_full_date, parse_iso_date, resolve_day_of_week,
};
use crate::models::{
    AgendaDayOverview, AgendaTimelineItem, AttendanceStatus, CampusSearchResults,
    ClassCheckInDetail, DisciplineSummaryCard, TimelineItemKind,
};

use super::assessments::list_all_assessments;
use super::disciplines::{get_discipline_summary_by_id, list_departments};
use super::helpers::{next_entity_seq, normalize_semester};

pub fn get_agenda_day(conn: &Connection, iso_date: &str) -> Result<AgendaDayOverview, CampusError> {
    let clean_date = iso_date.trim();
    let day_of_week = resolve_day_of_week(clean_date)?;
    let formatted_date = format_pt_br_full_date(clean_date)?;
    let month_year_label = format_month_year_label(clean_date)?;

    let mut slot_stmt = conn.prepare(
        "SELECT
            s.id AS slot_id,
            s.course_id,
            c.name AS course_name,
            s.start_time,
            s.end_time,
            s.building,
            s.room,
            c.professor,
            COALESCE(c.color_override_hex, d.color_hex) AS color_hex,
            cs.id AS session_id,
            cs.status AS session_status,
            cs.weather_temp_c
         FROM schedule_slots s
         JOIN courses c ON c.id = s.course_id
         JOIN departments d ON d.id = c.department_id
         LEFT JOIN class_sessions cs
                ON cs.slot_id = s.id AND cs.session_date = ?2
         WHERE s.day_of_week = ?1
         ORDER BY s.start_time ASC, s.id ASC",
    )?;

    let slot_rows = slot_stmt.query_map(params![day_of_week, clean_date], |row| {
        let slot_id: String = row.get(0)?;
        let course_id: String = row.get(1)?;
        let course_name: String = row.get(2)?;
        let start_time: String = row.get(3)?;
        let end_time: String = row.get(4)?;
        let building: String = row.get(5)?;
        let room: String = row.get(6)?;
        let professor: String = row.get(7)?;
        let color_hex: String = row.get(8)?;
        let session_id: Option<String> = row.get(9)?;
        let session_status: Option<String> = row.get(10)?;
        let weather_temp_c: Option<i32> = row.get(11)?;

        let id = session_id.unwrap_or_else(|| format!("{slot_id}_{clean_date}"));
        let status = session_status
            .as_deref()
            .and_then(AttendanceStatus::from_db_str)
            .unwrap_or(AttendanceStatus::Attending);

        Ok(AgendaTimelineItem {
            id,
            slot_id: Some(slot_id),
            course_id: Some(course_id),
            kind: TimelineItemKind::ClassSession,
            title: course_name,
            start_time,
            end_time,
            location: format_building_and_room(&building, &room),
            subtitle: professor,
            color_hex,
            weather_temp_c: Some(weather_temp_c.unwrap_or(DEFAULT_WEATHER_TEMP_C)),
            status: Some(status),
        })
    })?;

    let mut class_items = Vec::new();
    for item in slot_rows {
        class_items.push(item?);
    }

    let mut comm_stmt = conn.prepare(
        "SELECT
            id,
            title,
            description,
            start_time,
            end_time,
            location,
            weather_temp_c
         FROM commitments
         WHERE commitment_date = ?1
         ORDER BY start_time ASC, id ASC",
    )?;

    let comm_rows = comm_stmt.query_map(params![clean_date], |row| {
        let id: String = row.get(0)?;
        let title: String = row.get(1)?;
        let description: String = row.get(2)?;
        let start_time: String = row.get(3)?;
        let end_time: String = row.get(4)?;
        let location: String = row.get(5)?;
        let weather_temp_c: Option<i32> = row.get(6)?;

        Ok(AgendaTimelineItem {
            id,
            slot_id: None,
            course_id: None,
            kind: TimelineItemKind::Commitment,
            title,
            start_time,
            end_time,
            location,
            subtitle: description,
            color_hex: DEFAULT_COMMITMENT_COLOR_HEX.to_string(),
            weather_temp_c,
            status: None,
        })
    })?;

    let mut commitment_items = Vec::new();
    for item in comm_rows {
        commitment_items.push(item?);
    }

    let classes_count = class_items.len() as u32;
    let commitments_count = commitment_items.len() as u32;

    let mut items = Vec::with_capacity(class_items.len() + commitment_items.len());
    items.extend(class_items);
    items.extend(commitment_items);
    items.sort_by(|a, b| {
        a.start_time
            .cmp(&b.start_time)
            .then_with(|| a.end_time.cmp(&b.end_time))
            .then_with(|| a.id.cmp(&b.id))
    });

    Ok(AgendaDayOverview {
        iso_date: clean_date.to_string(),
        month_year_label,
        formatted_date,
        week_number: DEFAULT_WEEK_NUMBER,
        week_days: build_week_47_days(),
        classes_count,
        commitments_count,
        items,
    })
}

pub fn get_class_check_in(
    conn: &Connection,
    slot_id: &str,
    iso_date: &str,
) -> Result<ClassCheckInDetail, CampusError> {
    let clean_slot_id = slot_id.trim();
    if clean_slot_id.is_empty() {
        return Err(CampusError::InvalidInput(
            "O identificador de horário (slot_id) não pode ser vazio".into(),
        ));
    }
    let clean_date = iso_date.trim();
    let formatted_date = format_pt_br_full_date(clean_date)?;
    let available_colors = list_departments(conn)?;

    let row_opt = conn
        .query_row(
            "SELECT
                s.id,
                c.id AS course_id,
                d.id AS department_id,
                d.name AS department_name,
                c.name AS course_name,
                s.start_time,
                s.end_time,
                s.building,
                s.room,
                c.professor,
                COALESCE(c.color_override_hex, d.color_hex) AS selected_color_hex,
                cs.weather_temp_c,
                cs.weather_condition,
                cs.status
             FROM schedule_slots s
             JOIN courses c ON c.id = s.course_id
             JOIN departments d ON d.id = c.department_id
             LEFT JOIN class_sessions cs
                    ON cs.slot_id = s.id AND cs.session_date = ?2
             WHERE s.id = ?1",
            params![clean_slot_id, clean_date],
            |row| {
                let s_id: String = row.get(0)?;
                let course_id: String = row.get(1)?;
                let department_id: String = row.get(2)?;
                let department_name: String = row.get(3)?;
                let course_name: String = row.get(4)?;
                let start_time: String = row.get(5)?;
                let end_time: String = row.get(6)?;
                let building: String = row.get(7)?;
                let room: String = row.get(8)?;
                let professor: String = row.get(9)?;
                let selected_color_hex: String = row.get(10)?;
                let weather_temp_c: Option<i32> = row.get(11)?;
                let weather_condition: Option<String> = row.get(12)?;
                let status_raw: Option<String> = row.get(13)?;

                let status = status_raw
                    .as_deref()
                    .and_then(AttendanceStatus::from_db_str)
                    .unwrap_or(AttendanceStatus::Attending);

                Ok(ClassCheckInDetail {
                    slot_id: s_id,
                    course_id,
                    department_id,
                    department_name,
                    course_name,
                    formatted_date: formatted_date.clone(),
                    start_time,
                    end_time,
                    weather_temp_c: weather_temp_c.unwrap_or(DEFAULT_WEATHER_TEMP_C),
                    weather_condition: weather_condition
                        .unwrap_or_else(|| DEFAULT_WEATHER_CONDITION.to_string()),
                    building_and_room: format_building_and_room(&building, &room),
                    professor,
                    selected_color_hex,
                    available_colors: available_colors.clone(),
                    status,
                })
            },
        )
        .optional()?;

    row_opt.ok_or_else(|| {
        CampusError::NotFound(format!("Horário de aula não encontrado: '{clean_slot_id}'"))
    })
}

pub fn save_class_check_in(
    conn: &Connection,
    slot_id: &str,
    iso_date: &str,
    status: AttendanceStatus,
    color_override_hex: Option<&str>,
) -> Result<DisciplineSummaryCard, CampusError> {
    let clean_slot_id = slot_id.trim();
    if clean_slot_id.is_empty() {
        return Err(CampusError::InvalidInput(
            "O identificador de horário (slot_id) não pode ser vazio".into(),
        ));
    }
    let clean_date = iso_date.trim();
    let _ = parse_iso_date(clean_date)?;

    let course_id_opt: Option<String> = conn
        .query_row(
            "SELECT course_id FROM schedule_slots WHERE id = ?1",
            params![clean_slot_id],
            |row| row.get(0),
        )
        .optional()?;

    let Some(course_id) = course_id_opt else {
        return Err(CampusError::NotFound(format!(
            "Horário de aula não encontrado: '{clean_slot_id}'"
        )));
    };

    let session_id = format!("{clean_slot_id}_{clean_date}");
    conn.execute(
        "INSERT INTO class_sessions (
            id,
            slot_id,
            course_id,
            session_date,
            status,
            weather_temp_c,
            weather_condition
        ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
        ON CONFLICT(slot_id, session_date) DO UPDATE SET
            status = excluded.status",
        params![
            session_id,
            clean_slot_id,
            &course_id,
            clean_date,
            status.as_db_str(),
            DEFAULT_WEATHER_TEMP_C,
            DEFAULT_WEATHER_CONDITION,
        ],
    )?;

    if let Some(color_hex) = color_override_hex {
        let trimmed_color = color_hex.trim();
        if !trimmed_color.is_empty() {
            conn.execute(
                "UPDATE courses SET color_override_hex = ?1 WHERE id = ?2",
                params![trimmed_color, &course_id],
            )?;
        }
    }

    get_discipline_summary_by_id(conn, &course_id)
}

struct ValidatedCommitmentInput<'a> {
    title: &'a str,
    description: &'a str,
    date: &'a str,
    start_time: &'a str,
    end_time: &'a str,
    location: &'a str,
}

fn validate_commitment_fields<'a>(
    title: &'a str,
    description: &'a str,
    commitment_date: &'a str,
    start_time: &'a str,
    end_time: &'a str,
    location: &'a str,
) -> Result<ValidatedCommitmentInput<'a>, CampusError> {
    let clean_title = title.trim();
    if clean_title.is_empty() {
        return Err(CampusError::InvalidInput(
            "O título do compromisso não pode ser vazio".into(),
        ));
    }

    let clean_date = commitment_date.trim();
    let _ = parse_iso_date(clean_date)?;

    let clean_start = start_time.trim();
    let clean_end = end_time.trim();
    if clean_start.is_empty() || clean_end.is_empty() {
        return Err(CampusError::InvalidInput(
            "Os horários de início e término do compromisso não podem ser vazios".into(),
        ));
    }

    Ok(ValidatedCommitmentInput {
        title: clean_title,
        description: description.trim(),
        date: clean_date,
        start_time: clean_start,
        end_time: clean_end,
        location: location.trim(),
    })
}

#[allow(clippy::too_many_arguments)]
pub fn create_commitment(
    conn: &Connection,
    title: &str,
    description: &str,
    commitment_date: &str,
    start_time: &str,
    end_time: &str,
    location: &str,
    weather_temp_c: Option<i32>,
) -> Result<AgendaTimelineItem, CampusError> {
    let validated = validate_commitment_fields(
        title,
        description,
        commitment_date,
        start_time,
        end_time,
        location,
    )?;

    let id = format!("comm_{}_{}", validated.date, next_entity_seq());
    conn.execute(
        "INSERT INTO commitments (
            id,
            title,
            description,
            commitment_date,
            start_time,
            end_time,
            location,
            weather_temp_c
        ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
        params![
            &id,
            validated.title,
            validated.description,
            validated.date,
            validated.start_time,
            validated.end_time,
            validated.location,
            weather_temp_c,
        ],
    )?;

    Ok(AgendaTimelineItem {
        id,
        slot_id: None,
        course_id: None,
        kind: TimelineItemKind::Commitment,
        title: validated.title.to_string(),
        start_time: validated.start_time.to_string(),
        end_time: validated.end_time.to_string(),
        location: validated.location.to_string(),
        subtitle: validated.description.to_string(),
        color_hex: DEFAULT_COMMITMENT_COLOR_HEX.to_string(),
        weather_temp_c,
        status: None,
    })
}

#[allow(clippy::too_many_arguments)]
pub fn update_commitment(
    conn: &Connection,
    commitment_id: &str,
    title: &str,
    description: &str,
    commitment_date: &str,
    start_time: &str,
    end_time: &str,
    location: &str,
) -> Result<AgendaTimelineItem, CampusError> {
    let clean_id = commitment_id.trim();
    if clean_id.is_empty() {
        return Err(CampusError::InvalidInput(
            "O identificador do compromisso (commitment_id) não pode ser vazio".into(),
        ));
    }

    let validated = validate_commitment_fields(
        title,
        description,
        commitment_date,
        start_time,
        end_time,
        location,
    )?;

    let existing_weather: Option<Option<i32>> = conn
        .query_row(
            "SELECT weather_temp_c FROM commitments WHERE id = ?1",
            params![clean_id],
            |row| row.get(0),
        )
        .optional()?;
    let Some(weather_temp_c) = existing_weather else {
        return Err(CampusError::NotFound(format!(
            "Compromisso não encontrado: '{clean_id}'"
        )));
    };

    conn.execute(
        "UPDATE commitments
         SET title = ?1,
             description = ?2,
             commitment_date = ?3,
             start_time = ?4,
             end_time = ?5,
             location = ?6
         WHERE id = ?7",
        params![
            validated.title,
            validated.description,
            validated.date,
            validated.start_time,
            validated.end_time,
            validated.location,
            clean_id,
        ],
    )?;

    Ok(AgendaTimelineItem {
        id: clean_id.to_string(),
        slot_id: None,
        course_id: None,
        kind: TimelineItemKind::Commitment,
        title: validated.title.to_string(),
        start_time: validated.start_time.to_string(),
        end_time: validated.end_time.to_string(),
        location: validated.location.to_string(),
        subtitle: validated.description.to_string(),
        color_hex: DEFAULT_COMMITMENT_COLOR_HEX.to_string(),
        weather_temp_c,
        status: None,
    })
}

pub fn delete_commitment(conn: &Connection, commitment_id: &str) -> Result<(), CampusError> {
    let clean_id = commitment_id.trim();
    if clean_id.is_empty() {
        return Err(CampusError::InvalidInput(
            "O identificador do compromisso (commitment_id) não pode ser vazio".into(),
        ));
    }

    let affected = conn.execute("DELETE FROM commitments WHERE id = ?1", params![clean_id])?;
    if affected == 0 {
        return Err(CampusError::NotFound(format!(
            "Compromisso não encontrado: '{clean_id}'"
        )));
    }

    Ok(())
}

pub fn search_campus_items(
    conn: &Connection,
    semester: &str,
    query: &str,
) -> Result<CampusSearchResults, CampusError> {
    let clean_semester = normalize_semester(semester);
    let clean_query = query.trim();

    if clean_query.is_empty() {
        return Ok(CampusSearchResults {
            query: String::new(),
            courses: Vec::new(),
            assessments: Vec::new(),
            commitments: Vec::new(),
        });
    }

    let needle = clean_query.to_lowercase();

    let mut course_stmt = conn.prepare(
        "SELECT id, name, professor
         FROM courses
         WHERE semester = ?1
         ORDER BY rowid ASC",
    )?;
    let course_rows = course_stmt.query_map(params![clean_semester], |row| {
        Ok((
            row.get::<_, String>(0)?,
            row.get::<_, String>(1)?,
            row.get::<_, String>(2)?,
        ))
    })?;

    let mut courses = Vec::new();
    for row_res in course_rows {
        let (course_id, name, professor) = row_res?;
        if name.to_lowercase().contains(&needle) || professor.to_lowercase().contains(&needle) {
            courses.push(get_discipline_summary_by_id(conn, &course_id)?);
        }
    }

    let assessments = list_all_assessments(conn, clean_semester, None)?
        .into_iter()
        .filter(|item| {
            item.assessment.title.to_lowercase().contains(&needle)
                || item.assessment.kind.to_lowercase().contains(&needle)
        })
        .collect();

    let mut comm_stmt = conn.prepare(
        "SELECT
            id,
            title,
            description,
            start_time,
            end_time,
            location,
            weather_temp_c
         FROM commitments
         ORDER BY commitment_date ASC, start_time ASC, id ASC",
    )?;
    let comm_rows = comm_stmt.query_map([], |row| {
        Ok(AgendaTimelineItem {
            id: row.get(0)?,
            slot_id: None,
            course_id: None,
            kind: TimelineItemKind::Commitment,
            title: row.get(1)?,
            start_time: row.get(3)?,
            end_time: row.get(4)?,
            location: row.get(5)?,
            subtitle: row.get(2)?,
            color_hex: DEFAULT_COMMITMENT_COLOR_HEX.to_string(),
            weather_temp_c: row.get(6)?,
            status: None,
        })
    })?;

    let mut commitments = Vec::new();
    for row_res in comm_rows {
        let item = row_res?;
        if item.title.to_lowercase().contains(&needle)
            || item.subtitle.to_lowercase().contains(&needle)
            || item.location.to_lowercase().contains(&needle)
        {
            commitments.push(item);
        }
    }

    Ok(CampusSearchResults {
        query: clean_query.to_string(),
        courses,
        assessments,
        commitments,
    })
}
