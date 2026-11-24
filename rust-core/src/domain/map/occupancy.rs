use std::collections::{HashMap, HashSet};

use chrono::{DateTime, Datelike, NaiveDate, NaiveDateTime, TimeZone, Timelike};
use chrono_tz::America::Sao_Paulo;
use serde::Deserialize;

use crate::CampusError;

use super::types::{OccupancyStatus, RoomOccupancyItem, SlotInfo};

#[derive(Debug, Clone, Deserialize)]
pub struct OccupancyCourseRef {
    pub code: String,
    #[serde(default)]
    pub sections: Vec<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct RawOccupancySlot {
    pub campus_id: String,
    pub building_id: String,
    pub room_id: String,
    pub room_code: String,
    pub weekday: u32,
    pub start: String,
    pub end: String,
    #[serde(default)]
    pub kind: String,
    #[serde(default)]
    pub courses: Vec<OccupancyCourseRef>,
    #[serde(default)]
    pub label: String,
    #[serde(default)]
    pub title: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct BuildingCoverage {
    pub campus_id: String,
    #[serde(default)]
    pub covered_weekdays: Vec<u32>,
}

#[derive(Debug, Clone, Deserialize)]
struct RawOccupancyFile {
    #[serde(default)]
    term: String,
    term_start: String,
    term_end: String,
    #[serde(default)]
    holidays: Vec<String>,
    #[serde(default)]
    coverage: HashMap<String, BuildingCoverage>,
    #[serde(default)]
    slots: Vec<RawOccupancySlot>,
}

#[derive(Debug, Clone)]
pub struct IndexedSlot {
    pub start_min: u32,
    pub end_min: u32,
    pub raw: RawOccupancySlot,
    pub info: SlotInfo,
    pub expanded_courses: Vec<(String, Vec<String>)>,
}

#[derive(Debug, Clone)]
pub struct OccupancyIndex {
    pub term_start: NaiveDate,
    pub term_end: NaiveDate,
    pub holidays: HashSet<NaiveDate>,
    pub coverage_weekdays: HashMap<String, HashSet<u32>>,
    pub known_buildings: Vec<String>,
    pub known_rooms: HashSet<String>,
    pub by_room_weekday: HashMap<(String, u32), Vec<IndexedSlot>>,
    pub by_course_weekday: HashMap<(String, u32), Vec<IndexedSlot>>,
}

/// Feriados e recessos oficiais 2026.2 alinhados a `migrations/005_sprint13_official_calendars.sql`.
const OFFICIAL_2026_2_HOLIDAYS: &[&str] = &[
    "2026-09-07",
    "2026-10-12",
    "2026-10-28",
    "2026-11-02",
    "2026-11-15",
    "2026-11-20",
    "2026-12-08",
];

pub fn parse_hhmm_to_minutes(raw: &str) -> Option<u32> {
    let clean = raw.trim();
    let (h_str, m_str) = clean.split_once(':')?;
    let h: u32 = h_str.trim().parse().ok()?;
    let m: u32 = m_str.trim().parse().ok()?;
    if h >= 24 || m >= 60 {
        return None;
    }
    Some(h * 60 + m)
}

/// Normaliza tokens de turma tratando agrupamentos como `21/22`, `T21|22`, `41 a 44`, `06`.
pub fn expand_section_tokens(raw_tokens: &[String]) -> Vec<String> {
    let mut result = Vec::new();
    for raw in raw_tokens {
        let upper = raw.trim().to_ascii_uppercase();
        if upper.is_empty() {
            continue;
        }
        // Detectar intervalos do tipo "41 A 44" ou "31A33" usando separador sem letras.
        let normalized_range = if upper.contains(" A ") {
            upper.replace(" A ", "~")
        } else {
            let mut buf = String::with_capacity(upper.len());
            let chars: Vec<char> = upper.chars().collect();
            for (idx, &ch) in chars.iter().enumerate() {
                let prev_digit = idx > 0 && chars[idx - 1].is_ascii_digit();
                let next_digit = idx + 1 < chars.len() && chars[idx + 1].is_ascii_digit();
                if ch == 'A' && prev_digit && next_digit {
                    buf.push('~');
                } else {
                    buf.push(ch);
                }
            }
            buf
        };
        if normalized_range.contains('~')
            && let Some((left_raw, right_raw)) = normalized_range.split_once('~')
        {
            let left_clean = normalize_single_section(left_raw);
            let right_clean = normalize_single_section(right_raw);
            if let (Ok(start), Ok(end)) = (left_clean.parse::<u32>(), right_clean.parse::<u32>())
                && start <= end
                && (end - start) <= 30
            {
                for n in start..=end {
                    result.push(n.to_string());
                }
                continue;
            }
        }

        for part in upper.split(['/', '|', '-', ',', ';', ' ']) {
            let trimmed = part.trim();
            if trimmed.is_empty() {
                continue;
            }
            let norm = normalize_single_section(trimmed);
            if !norm.is_empty() {
                result.push(norm);
            }
        }
    }
    result
}

pub fn normalize_single_section(raw: &str) -> String {
    let trimmed = raw.trim().to_ascii_uppercase();
    if trimmed.is_empty() {
        return String::new();
    }
    let without_t = trimmed
        .strip_prefix("TURMA")
        .unwrap_or(&trimmed)
        .trim()
        .strip_prefix('T')
        .unwrap_or(trimmed.strip_prefix("TURMA").unwrap_or(&trimmed).trim())
        .trim();
    if without_t.chars().all(|c| c.is_ascii_digit()) {
        let stripped = without_t.trim_start_matches('0');
        if stripped.is_empty() {
            "0".to_string()
        } else {
            stripped.to_string()
        }
    } else {
        without_t.to_string()
    }
}

impl OccupancyIndex {
    pub fn empty() -> Self {
        Self {
            term_start: NaiveDate::from_ymd_opt(2026, 8, 24).expect("valid date"),
            term_end: NaiveDate::from_ymd_opt(2026, 12, 23).expect("valid date"),
            holidays: HashSet::new(),
            coverage_weekdays: HashMap::new(),
            known_buildings: Vec::new(),
            known_rooms: HashSet::new(),
            by_room_weekday: HashMap::new(),
            by_course_weekday: HashMap::new(),
        }
    }

    pub fn from_json(raw_json: &str) -> Result<Self, CampusError> {
        let doc: RawOccupancyFile = serde_json::from_str(raw_json).map_err(|err| {
            CampusError::InvalidInput(format!("JSON de ocupação inválido: {err}"))
        })?;

        let term_start = NaiveDate::parse_from_str(doc.term_start.trim(), "%Y-%m-%d")
            .map_err(|err| CampusError::InvalidInput(format!("term_start inválido: {err}")))?;
        let term_end = NaiveDate::parse_from_str(doc.term_end.trim(), "%Y-%m-%d")
            .map_err(|err| CampusError::InvalidInput(format!("term_end inválido: {err}")))?;

        let mut holidays = HashSet::new();
        for h in &doc.holidays {
            if let Ok(d) = NaiveDate::parse_from_str(h.trim(), "%Y-%m-%d") {
                holidays.insert(d);
            }
        }
        if doc.term.trim() == "2026/2" {
            for h in OFFICIAL_2026_2_HOLIDAYS {
                if let Ok(d) = NaiveDate::parse_from_str(h, "%Y-%m-%d") {
                    holidays.insert(d);
                }
            }
        }

        let mut coverage_weekdays: HashMap<String, HashSet<u32>> = HashMap::new();
        let mut known_buildings: Vec<String> = Vec::with_capacity(doc.coverage.len());
        for (bid, cov) in doc.coverage {
            let clean_bid = bid.trim().to_ascii_uppercase();
            known_buildings.push(clean_bid.clone());
            coverage_weekdays.insert(clean_bid, cov.covered_weekdays.into_iter().collect());
        }
        known_buildings.sort_by_key(|b| std::cmp::Reverse(b.len()));

        let mut known_rooms = HashSet::new();
        let mut by_room_weekday: HashMap<(String, u32), Vec<IndexedSlot>> = HashMap::new();
        let mut by_course_weekday: HashMap<(String, u32), Vec<IndexedSlot>> = HashMap::new();

        for slot in doc.slots {
            let Some(start_min) = parse_hhmm_to_minutes(&slot.start) else {
                continue;
            };
            let Some(end_min) = parse_hhmm_to_minutes(&slot.end) else {
                continue;
            };
            known_rooms.insert(slot.room_id.clone());

            let course_codes: Vec<String> = slot
                .courses
                .iter()
                .map(|c| c.code.trim().to_ascii_uppercase())
                .filter(|c| !c.is_empty())
                .collect();

            let expanded_courses: Vec<(String, Vec<String>)> = slot
                .courses
                .iter()
                .map(|c| {
                    (
                        c.code.trim().to_ascii_uppercase(),
                        expand_section_tokens(&c.sections),
                    )
                })
                .filter(|(code, _)| !code.is_empty())
                .collect();

            let info = SlotInfo {
                campus_id: slot.campus_id.clone(),
                building_id: slot.building_id.clone(),
                room_id: slot.room_id.clone(),
                room_code: slot.room_code.clone(),
                weekday: slot.weekday,
                start_time: slot.start.clone(),
                end_time: slot.end.clone(),
                kind: slot.kind.clone(),
                label: slot.label.clone(),
                title: slot.title.clone(),
                course_codes: course_codes.clone(),
            };

            let weekday = slot.weekday;
            let indexed = IndexedSlot {
                start_min,
                end_min,
                raw: slot,
                info,
                expanded_courses,
            };

            by_room_weekday
                .entry((indexed.raw.room_id.clone(), weekday))
                .or_default()
                .push(indexed.clone());

            for code in course_codes {
                by_course_weekday
                    .entry((code, weekday))
                    .or_default()
                    .push(indexed.clone());
            }
        }

        for list in by_room_weekday.values_mut() {
            list.sort_by_key(|s| s.start_min);
        }

        Ok(Self {
            term_start,
            term_end,
            holidays,
            coverage_weekdays,
            known_buildings,
            known_rooms,
            by_room_weekday,
            by_course_weekday,
        })
    }

    /// Converte timestamp (epoch em segundos/milissegundos ou ISO 8601) para `(NaiveDate, weekday 1..=7, minute_of_day)` em `America/Sao_Paulo`.
    pub fn parse_timestamp_sao_paulo(
        timestamp_epoch_or_iso: &str,
    ) -> Result<(NaiveDate, u32, u32), CampusError> {
        let trimmed = timestamp_epoch_or_iso.trim();
        if trimmed.is_empty() {
            return Err(CampusError::InvalidInput(
                "Timestamp vazio para consulta de ocupação.".into(),
            ));
        }

        // 1. Epoch numérico (segundos ou milissegundos).
        if let Ok(raw_num) = trimmed.parse::<i64>() {
            let secs = if raw_num.abs() >= 1_000_000_000_000 {
                raw_num / 1000
            } else {
                raw_num
            };
            let utc = DateTime::from_timestamp(secs, 0).ok_or_else(|| {
                CampusError::InvalidInput(format!("Timestamp epoch fora da faixa: '{trimmed}'."))
            })?;
            let sp = utc.with_timezone(&Sao_Paulo);
            return Ok((
                sp.date_naive(),
                sp.weekday().number_from_monday(),
                sp.hour() * 60 + sp.minute(),
            ));
        }

        // 2. RFC 3339 / ISO com offset explícito (convertido para America/Sao_Paulo).
        if let Ok(dt) = DateTime::parse_from_rfc3339(trimmed) {
            let sp = dt.with_timezone(&Sao_Paulo);
            return Ok((
                sp.date_naive(),
                sp.weekday().number_from_monday(),
                sp.hour() * 60 + sp.minute(),
            ));
        }

        // 3. ISO local sem offset, interpretado diretamente em America/Sao_Paulo.
        let normalized_space = trimmed.replace(' ', "T");
        for fmt in [
            "%Y-%m-%dT%H:%M:%S",
            "%Y-%m-%dT%H:%M",
            "%Y-%m-%dT%H:%M:%S%.f",
        ] {
            if let Ok(naive) = NaiveDateTime::parse_from_str(&normalized_space, fmt) {
                let sp = Sao_Paulo
                    .from_local_datetime(&naive)
                    .earliest()
                    .ok_or_else(|| {
                        CampusError::InvalidInput(format!(
                            "Data/hora inválida em America/Sao_Paulo: '{trimmed}'."
                        ))
                    })?;
                return Ok((
                    sp.date_naive(),
                    sp.weekday().number_from_monday(),
                    sp.hour() * 60 + sp.minute(),
                ));
            }
        }

        Err(CampusError::InvalidInput(format!(
            "Formato de timestamp não suportado: '{trimmed}'."
        )))
    }

    pub fn infer_building_id(
        &self,
        room_or_building_id: &str,
        mapped_building: Option<&str>,
    ) -> String {
        if let Some(bid) = mapped_building
            && !bid.trim().is_empty()
        {
            return bid.trim().to_ascii_uppercase();
        }
        let upper = room_or_building_id.trim().to_ascii_uppercase();
        if self.coverage_weekdays.contains_key(&upper) {
            return upper;
        }
        for known in &self.known_buildings {
            if upper.starts_with(&format!("{known}_")) {
                return known.clone();
            }
        }
        upper.split('_').next().unwrap_or(&upper).to_string()
    }

    pub fn get_room_occupancy(
        &self,
        room_id: &str,
        mapped_building_id: Option<&str>,
        room_exists_in_map: bool,
        timestamp_epoch_or_iso: &str,
    ) -> Result<OccupancyStatus, CampusError> {
        let (date, weekday, minute_of_day) =
            Self::parse_timestamp_sao_paulo(timestamp_epoch_or_iso)?;

        if date < self.term_start
            || date > self.term_end
            || weekday == 7
            || self.holidays.contains(&date)
        {
            return Ok(OccupancyStatus::OutOfTerm);
        }

        let building_id = self.infer_building_id(room_id, mapped_building_id);
        let Some(covered_days) = self.coverage_weekdays.get(&building_id) else {
            return Ok(OccupancyStatus::NoData);
        };
        if !covered_days.contains(&weekday) {
            return Ok(OccupancyStatus::NoData);
        }

        let clean_room_id = room_id.trim();
        let known_in_schedule = self.known_rooms.contains(clean_room_id);
        if !room_exists_in_map && !known_in_schedule {
            return Ok(OccupancyStatus::NoData);
        }

        if let Some(slots) = self
            .by_room_weekday
            .get(&(clean_room_id.to_string(), weekday))
        {
            for slot in slots {
                if minute_of_day >= slot.start_min && minute_of_day < slot.end_min {
                    return Ok(OccupancyStatus::Occupied {
                        slot: slot.info.clone(),
                    });
                }
            }
        }

        Ok(OccupancyStatus::NoRegularClass)
    }

    pub fn list_building_occupancy(
        &self,
        building_id: &str,
        rooms: &[(String, String, i32, bool)],
        timestamp_epoch_or_iso: &str,
    ) -> Result<Vec<RoomOccupancyItem>, CampusError> {
        let mut results = Vec::with_capacity(rooms.len());
        for (room_id, room_code, floor, is_mock) in rooms {
            let status =
                self.get_room_occupancy(room_id, Some(building_id), true, timestamp_epoch_or_iso)?;
            results.push(RoomOccupancyItem {
                room_id: room_id.clone(),
                room_code: room_code.clone(),
                floor: *floor,
                is_mock: *is_mock,
                status,
            });
        }
        Ok(results)
    }
}
