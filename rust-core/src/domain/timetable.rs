/// Colunas de dias da semana exibidas na grade horária semanal (`1=SEG .. 5=SEX`).
pub const TIMETABLE_WEEKDAYS: &[(u32, &str)] =
    &[(1, "SEG"), (2, "TER"), (3, "QUA"), (4, "QUI"), (5, "SEX")];

/// Faixas de horário padrão da grade semanal UFOP (`08:00–09:40`, `10:00–11:40`, `14:00–15:00`).
pub const DEFAULT_TIMETABLE_BANDS: &[(&str, &str)] =
    &[("08:00", "09:40"), ("10:00", "11:40"), ("14:00", "15:00")];

/// Rótulo abreviado do dia da semana (`1=SEG .. 7=DOM`).
pub const fn day_of_week_short_label(day_of_week: u32) -> &'static str {
    match day_of_week {
        1 => "SEG",
        2 => "TER",
        3 => "QUA",
        4 => "QUI",
        5 => "SEX",
        6 => "SÁB",
        7 => "DOM",
        _ => "---",
    }
}

/// Formata o título da grade horária semanal para o semestre informado.
pub fn format_timetable_grid_title(semester: &str) -> String {
    let clean = if semester.trim().is_empty() {
        "2026.2"
    } else {
        semester.trim()
    };
    format!("Semana de aulas · {clean}")
}
