use crate::CampusError;
use crate::models::DayPillItem;

pub const DEFAULT_WEATHER_TEMP_C: i32 = 22;
pub const DEFAULT_WEATHER_CONDITION: &str = "Ensolarado";
pub const DEFAULT_COMMITMENT_COLOR_HEX: &str = "#8E95A2";
pub const DEFAULT_WEEK_NUMBER: u32 = 47;

/// Retorna as pílulas diárias da Semana 47 (`2026-11-16` SEG a `2026-11-22` DOM).
pub fn build_week_47_days() -> Vec<DayPillItem> {
    vec![
        DayPillItem {
            iso_date: "2026-11-16".to_string(),
            day_short: "SEG".to_string(),
            day_number: 16,
        },
        DayPillItem {
            iso_date: "2026-11-17".to_string(),
            day_short: "TER".to_string(),
            day_number: 17,
        },
        DayPillItem {
            iso_date: "2026-11-18".to_string(),
            day_short: "QUA".to_string(),
            day_number: 18,
        },
        DayPillItem {
            iso_date: "2026-11-19".to_string(),
            day_short: "QUI".to_string(),
            day_number: 19,
        },
        DayPillItem {
            iso_date: "2026-11-20".to_string(),
            day_short: "SEX".to_string(),
            day_number: 20,
        },
        DayPillItem {
            iso_date: "2026-11-21".to_string(),
            day_short: "SÁB".to_string(),
            day_number: 21,
        },
        DayPillItem {
            iso_date: "2026-11-22".to_string(),
            day_short: "DOM".to_string(),
            day_number: 22,
        },
    ]
}

/// Faz o parse de uma data ISO `YYYY-MM-DD` em `(year, month, day)`.
pub fn parse_iso_date(iso_date: &str) -> Result<(i32, u32, u32), CampusError> {
    let trimmed = iso_date.trim();
    let mut parts = trimmed.split('-');
    let (Some(y_str), Some(m_str), Some(d_str), None) =
        (parts.next(), parts.next(), parts.next(), parts.next())
    else {
        return Err(CampusError::InvalidInput(format!(
            "Data ISO inválida (esperado YYYY-MM-DD): '{iso_date}'"
        )));
    };

    let year: i32 = y_str.parse().map_err(|_| {
        CampusError::InvalidInput(format!("Ano inválido na data ISO: '{iso_date}'"))
    })?;
    let month: u32 = m_str.parse().map_err(|_| {
        CampusError::InvalidInput(format!("Mês inválido na data ISO: '{iso_date}'"))
    })?;
    let day: u32 = d_str.parse().map_err(|_| {
        CampusError::InvalidInput(format!("Dia inválido na data ISO: '{iso_date}'"))
    })?;

    if !(1..=12).contains(&month) || !(1..=31).contains(&day) {
        return Err(CampusError::InvalidInput(format!(
            "Intervalo de mês/dia inválido na data ISO: '{iso_date}'"
        )));
    }

    Ok((year, month, day))
}

/// Determina o dia da semana no padrão do schema (`1=SEG .. 7=DOM`) a partir de `YYYY-MM-DD`.
pub fn resolve_day_of_week(iso_date: &str) -> Result<u32, CampusError> {
    let (year, month, day) = parse_iso_date(iso_date)?;
    // Algoritmo de Sakamoto: 0 = Domingo, 1 = Segunda, ..., 6 = Sábado
    let t = [0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4];
    let mut y = year;
    if month < 3 {
        y -= 1;
    }
    let w = (y + y / 4 - y / 100 + y / 400 + t[(month - 1) as usize] + day as i32).rem_euclid(7);
    let iso_weekday = if w == 0 { 7 } else { w as u32 };
    Ok(iso_weekday)
}

/// Formata o cabeçalho por extenso em português (ex.: `"Terça-feira, 17 de novembro"`).
pub fn format_pt_br_full_date(iso_date: &str) -> Result<String, CampusError> {
    let (_, month, day) = parse_iso_date(iso_date)?;
    let day_of_week = resolve_day_of_week(iso_date)?;

    let weekday_name = match day_of_week {
        1 => "Segunda-feira",
        2 => "Terça-feira",
        3 => "Quarta-feira",
        4 => "Quinta-feira",
        5 => "Sexta-feira",
        6 => "Sábado",
        7 => "Domingo",
        _ => "Dia",
    };

    let month_name = match month {
        1 => "janeiro",
        2 => "fevereiro",
        3 => "março",
        4 => "abril",
        5 => "maio",
        6 => "junho",
        7 => "julho",
        8 => "agosto",
        9 => "setembro",
        10 => "outubro",
        11 => "novembro",
        12 => "dezembro",
        _ => "mês",
    };

    Ok(format!("{weekday_name}, {day} de {month_name}"))
}

/// Formata o rótulo de mês e ano (ex.: `"Novembro 2026"`).
pub fn format_month_year_label(iso_date: &str) -> Result<String, CampusError> {
    let (year, month, _) = parse_iso_date(iso_date)?;
    let month_title = match month {
        1 => "Janeiro",
        2 => "Fevereiro",
        3 => "Março",
        4 => "Abril",
        5 => "Maio",
        6 => "Junho",
        7 => "Julho",
        8 => "Agosto",
        9 => "Setembro",
        10 => "Outubro",
        11 => "Novembro",
        12 => "Dezembro",
        _ => "Mês",
    };
    Ok(format!("{month_title} {year}"))
}

/// Extrai `(day_number, month_short)` a partir de `due_date` (`YYYY-MM-DD` ou `DD MMM`),
/// por exemplo `"2026-11-20"` -> `("20", "NOV")` ou `"20 NOV"` -> `("20", "NOV")`.
pub fn parse_assessment_due_date(due_date: &str) -> (String, String) {
    if let Ok((_, month, day)) = parse_iso_date(due_date) {
        let month_short = match month {
            1 => "JAN",
            2 => "FEV",
            3 => "MAR",
            4 => "ABR",
            5 => "MAI",
            6 => "JUN",
            7 => "JUL",
            8 => "AGO",
            9 => "SET",
            10 => "OUT",
            11 => "NOV",
            12 => "DEZ",
            _ => "---",
        };
        (format!("{day:02}"), month_short.to_string())
    } else {
        let mut parts = due_date.split_whitespace();
        if let (Some(day_raw), Some(month_raw), None) = (parts.next(), parts.next(), parts.next())
            && let Ok(day) = day_raw.parse::<u32>()
            && (1..=31).contains(&day)
        {
            return (format!("{day:02}"), month_raw.to_ascii_uppercase());
        }
        (due_date.trim().to_string(), String::new())
    }
}

/// Concatena prédio e sala no formato `"Prédio A · Sala 204"`.
pub fn format_building_and_room(building: &str, room: &str) -> String {
    format!("{} · {}", building.trim(), room.trim())
}
