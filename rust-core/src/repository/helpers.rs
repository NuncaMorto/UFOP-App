use std::sync::atomic::{AtomicU64, Ordering};

use rusqlite::{Connection, params};

use crate::CampusError;

static NEXT_ENTITY_SEQ: AtomicU64 = AtomicU64::new(1000);

pub(crate) fn next_entity_seq() -> u64 {
    NEXT_ENTITY_SEQ.fetch_add(1, Ordering::Relaxed)
}

pub(crate) fn parse_time_hhmm(raw: &str) -> Result<(u32, String), CampusError> {
    let trimmed = raw.trim();
    let mut parts = trimmed.split(':');
    let (Some(h_str), Some(m_str), None) = (parts.next(), parts.next(), parts.next()) else {
        return Err(CampusError::InvalidInput(format!(
            "Horário inválido (esperado HH:MM): '{raw}'"
        )));
    };

    let hour: u32 = h_str
        .parse()
        .map_err(|_| CampusError::InvalidInput(format!("Hora inválida no horário: '{raw}'")))?;
    let minute: u32 = m_str
        .parse()
        .map_err(|_| CampusError::InvalidInput(format!("Minuto inválido no horário: '{raw}'")))?;

    if hour >= 24 || minute >= 60 {
        return Err(CampusError::InvalidInput(format!(
            "Horário fora do intervalo 00:00–23:59: '{raw}'"
        )));
    }

    Ok((hour * 60 + minute, format!("{hour:02}:{minute:02}")))
}

pub(crate) fn ensure_department_exists<'a>(
    conn: &Connection,
    department_id: &'a str,
) -> Result<&'a str, CampusError> {
    let clean_dept = department_id.trim();
    if clean_dept.is_empty() {
        return Err(CampusError::InvalidInput(
            "O departamento (department_id) não pode ser vazio".into(),
        ));
    }

    let dept_exists: bool = conn
        .prepare("SELECT 1 FROM departments WHERE id = ?1")?
        .exists(params![clean_dept])?;
    if !dept_exists {
        return Err(CampusError::NotFound(format!(
            "Departamento não encontrado: '{clean_dept}'"
        )));
    }

    Ok(clean_dept)
}

pub(crate) fn ensure_course_exists<'a>(
    conn: &Connection,
    course_id: &'a str,
) -> Result<&'a str, CampusError> {
    let clean_course_id = course_id.trim();
    if clean_course_id.is_empty() {
        return Err(CampusError::InvalidInput(
            "O identificador da disciplina (course_id) não pode ser vazio".into(),
        ));
    }

    let course_exists: bool = conn
        .prepare("SELECT 1 FROM courses WHERE id = ?1")?
        .exists(params![clean_course_id])?;
    if !course_exists {
        return Err(CampusError::NotFound(format!(
            "Disciplina não encontrada: '{clean_course_id}'"
        )));
    }

    Ok(clean_course_id)
}

pub(crate) fn normalize_semester(semester: &str) -> &str {
    let trimmed = semester.trim();
    if trimmed.is_empty() {
        "2026.2"
    } else {
        trimmed
    }
}
