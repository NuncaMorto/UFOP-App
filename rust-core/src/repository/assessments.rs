use rusqlite::{Connection, OptionalExtension, params};

use crate::CampusError;
use crate::domain::agenda::parse_assessment_due_date;
use crate::models::{AssessmentItem, AssessmentWithCourse};

use super::helpers::{ensure_course_exists, next_entity_seq, normalize_semester};

fn get_assessment_by_id(
    conn: &Connection,
    assessment_id: &str,
) -> Result<AssessmentItem, CampusError> {
    let row_opt = conn
        .query_row(
            "SELECT id, course_id, title, kind, due_date, due_time, completed
             FROM assessments
             WHERE id = ?1",
            params![assessment_id],
            |row| {
                let id: String = row.get(0)?;
                let course_id: String = row.get(1)?;
                let title: String = row.get(2)?;
                let kind: String = row.get(3)?;
                let due_date: String = row.get(4)?;
                let due_time: String = row.get(5)?;
                let completed_int: i32 = row.get(6)?;
                let (day_number, month_short) = parse_assessment_due_date(&due_date);

                Ok(AssessmentItem {
                    id,
                    course_id,
                    day_number,
                    month_short,
                    title,
                    kind,
                    due_date,
                    due_time,
                    completed: completed_int != 0,
                })
            },
        )
        .optional()?;

    row_opt.ok_or_else(|| {
        CampusError::NotFound(format!("Avaliação não encontrada: '{assessment_id}'"))
    })
}

pub fn create_assessment(
    conn: &Connection,
    course_id: &str,
    title: &str,
    kind: &str,
    due_date: &str,
    due_time: &str,
) -> Result<AssessmentItem, CampusError> {
    let clean_course_id = course_id.trim();
    if clean_course_id.is_empty() {
        return Err(CampusError::InvalidInput(
            "O identificador da disciplina (course_id) não pode ser vazio".into(),
        ));
    }

    let clean_title = title.trim();
    if clean_title.is_empty() {
        return Err(CampusError::InvalidInput(
            "O título da avaliação não pode ser vazio".into(),
        ));
    }

    let clean_kind = kind.trim();
    if clean_kind.is_empty() {
        return Err(CampusError::InvalidInput(
            "O tipo da avaliação (kind) não pode ser vazio".into(),
        ));
    }

    let clean_due_date = due_date.trim();
    if clean_due_date.is_empty() {
        return Err(CampusError::InvalidInput(
            "A data de entrega da avaliação (due_date) não pode ser vazia".into(),
        ));
    }

    let clean_due_time = if due_time.trim().is_empty() {
        "18:00"
    } else {
        due_time.trim()
    };

    let clean_course_id = ensure_course_exists(conn, clean_course_id)?;

    let id = format!("assess_{}_{}", clean_course_id, next_entity_seq());
    conn.execute(
        "INSERT INTO assessments (
            id,
            course_id,
            title,
            kind,
            due_date,
            due_time,
            completed
        ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, 0)",
        params![
            &id,
            clean_course_id,
            clean_title,
            clean_kind,
            clean_due_date,
            clean_due_time,
        ],
    )?;

    get_assessment_by_id(conn, &id)
}

pub fn toggle_assessment_completed(
    conn: &Connection,
    assessment_id: &str,
    completed: bool,
) -> Result<AssessmentItem, CampusError> {
    let clean_id = assessment_id.trim();
    if clean_id.is_empty() {
        return Err(CampusError::InvalidInput(
            "O identificador da avaliação (assessment_id) não pode ser vazio".into(),
        ));
    }

    let affected = conn.execute(
        "UPDATE assessments SET completed = ?1 WHERE id = ?2",
        params![i32::from(completed), clean_id],
    )?;

    if affected == 0 {
        return Err(CampusError::NotFound(format!(
            "Avaliação não encontrada: '{clean_id}'"
        )));
    }

    get_assessment_by_id(conn, clean_id)
}

pub fn delete_assessment(conn: &Connection, assessment_id: &str) -> Result<(), CampusError> {
    let clean_id = assessment_id.trim();
    if clean_id.is_empty() {
        return Err(CampusError::InvalidInput(
            "O identificador da avaliação (assessment_id) não pode ser vazio".into(),
        ));
    }

    let affected = conn.execute("DELETE FROM assessments WHERE id = ?1", params![clean_id])?;
    if affected == 0 {
        return Err(CampusError::NotFound(format!(
            "Avaliação não encontrada: '{clean_id}'"
        )));
    }

    Ok(())
}

pub fn list_all_assessments(
    conn: &Connection,
    semester: &str,
    kind_filter: Option<&str>,
) -> Result<Vec<AssessmentWithCourse>, CampusError> {
    let clean_semester = normalize_semester(semester);

    let raw_filter = kind_filter.map(str::trim).filter(|val| !val.is_empty());

    let mut stmt = conn.prepare(
        "SELECT
            a.id,
            a.course_id,
            a.title,
            a.kind,
            a.due_date,
            a.due_time,
            a.completed,
            c.name AS course_name,
            d.name AS department_name,
            COALESCE(c.color_override_hex, d.color_hex) AS color_hex
         FROM assessments a
         JOIN courses c ON c.id = a.course_id
         JOIN departments d ON d.id = c.department_id
         WHERE c.semester = ?1
         ORDER BY a.due_date ASC, a.due_time ASC, a.id ASC",
    )?;

    let rows = stmt.query_map(params![clean_semester], |row| {
        let id: String = row.get(0)?;
        let course_id: String = row.get(1)?;
        let title: String = row.get(2)?;
        let kind: String = row.get(3)?;
        let due_date: String = row.get(4)?;
        let due_time: String = row.get(5)?;
        let completed_int: i32 = row.get(6)?;
        let course_name: String = row.get(7)?;
        let department_name: String = row.get(8)?;
        let color_hex: String = row.get(9)?;
        let (day_number, month_short) = parse_assessment_due_date(&due_date);

        Ok(AssessmentWithCourse {
            assessment: AssessmentItem {
                id,
                course_id,
                day_number,
                month_short,
                title,
                kind,
                due_date,
                due_time,
                completed: completed_int != 0,
            },
            course_name,
            department_name,
            color_hex,
        })
    })?;

    let mut items = Vec::new();
    for row_res in rows {
        let item = row_res?;
        if let Some(filter) = raw_filter {
            let filter_lower = filter.to_lowercase();
            let matches = match filter_lower.as_str() {
                "todas" | "all" => true,
                "concluídas" | "concluidas" | "concluída" | "concluida" | "completed" => {
                    item.assessment.completed
                }
                "provas" | "prova" => item.assessment.kind.eq_ignore_ascii_case("Prova"),
                "trabalhos" | "trabalho" => item.assessment.kind.eq_ignore_ascii_case("Trabalho"),
                "seminários" | "seminarios" | "seminário" | "seminario" => {
                    item.assessment.kind.to_lowercase() == "seminário"
                        || item.assessment.kind.eq_ignore_ascii_case("Seminario")
                }
                _ => item.assessment.kind.to_lowercase() == filter_lower,
            };
            if !matches {
                continue;
            }
        }
        items.push(item);
    }

    Ok(items)
}
