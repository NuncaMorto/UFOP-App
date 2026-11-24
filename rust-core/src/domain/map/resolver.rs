use std::collections::HashMap;

use serde::Deserialize;

use super::occupancy::{IndexedSlot, OccupancyIndex, expand_section_tokens, parse_hhmm_to_minutes};
use super::overlay::LoadedCampus;
use super::types::{CameraFocus, MapLayer, Rect2D, Resolution};

#[derive(Debug, Deserialize)]
struct SchemaContractDoc {
    #[serde(default)]
    atestado_building_aliases: HashMap<String, String>,
}

#[derive(Debug, Clone)]
pub struct MeetingResolver {
    atestado_aliases: HashMap<String, String>,
}

pub fn normalize_lookup_key(raw: &str) -> String {
    let mut out = String::with_capacity(raw.len());
    let mut prev_space = true;
    for ch in raw.trim().chars() {
        let mapped = match ch {
            'á' | 'à' | 'â' | 'ã' | 'ä' | 'Á' | 'À' | 'Â' | 'Ã' | 'Ä' => 'A',
            'é' | 'è' | 'ê' | 'ë' | 'É' | 'È' | 'Ê' | 'Ë' => 'E',
            'í' | 'ì' | 'î' | 'ï' | 'Í' | 'Ì' | 'Î' | 'Ï' => 'I',
            'ó' | 'ò' | 'ô' | 'õ' | 'ö' | 'Ó' | 'Ò' | 'Ô' | 'Õ' | 'Ö' => 'O',
            'ú' | 'ù' | 'û' | 'ü' | 'Ú' | 'Ù' | 'Û' | 'Ü' => 'U',
            'ç' | 'Ç' => 'C',
            'ñ' | 'Ñ' => 'N',
            '-' | '_' | '/' | '.' | ',' => ' ',
            c => c.to_ascii_uppercase(),
        };
        if mapped.is_whitespace() {
            if !prev_space {
                out.push(' ');
                prev_space = true;
            }
        } else {
            out.push(mapped);
            prev_space = false;
        }
    }
    out.trim().to_string()
}

pub fn focus_for_bounds(
    campus_id: &str,
    feature_id: &str,
    target_floor: i32,
    bounds: Rect2D,
) -> CameraFocus {
    let min_span = 16.0f32;
    let width = bounds.max_x - bounds.min_x;
    let height = bounds.max_y - bounds.min_y;
    let adjusted = if width < min_span && height < min_span {
        let cx = (bounds.min_x + bounds.max_x) * 0.5;
        let cy = (bounds.min_y + bounds.max_y) * 0.5;
        let half = min_span * 0.5;
        Rect2D {
            min_x: cx - half,
            min_y: cy - half,
            max_x: cx + half,
            max_y: cy + half,
        }
    } else {
        bounds
    };

    CameraFocus {
        campus_id: campus_id.to_string(),
        feature_id: feature_id.to_string(),
        target_floor,
        bounds: adjusted,
    }
}

impl MeetingResolver {
    pub fn from_contract_json(raw_json: &str) -> Self {
        let parsed: SchemaContractDoc =
            serde_json::from_str(raw_json).unwrap_or(SchemaContractDoc {
                atestado_building_aliases: HashMap::new(),
            });
        let mut atestado_aliases = HashMap::with_capacity(parsed.atestado_building_aliases.len());
        for (raw_alias, target) in parsed.atestado_building_aliases {
            let norm = normalize_lookup_key(&raw_alias);
            if !norm.is_empty() {
                atestado_aliases.insert(norm, target.trim().to_ascii_uppercase());
            }
        }
        Self { atestado_aliases }
    }

    #[allow(clippy::too_many_arguments)]
    pub fn resolve_meeting(
        &self,
        occupancy: &OccupancyIndex,
        campuses: &[LoadedCampus],
        course_code: &str,
        section: &str,
        weekday: u32,
        start_time: &str,
        fallback_building_raw: &str,
    ) -> Resolution {
        let clean_course = course_code.trim().to_ascii_uppercase();
        let target_secs = expand_section_tokens(&[section.to_string()]);

        if !clean_course.is_empty()
            && let Some(start_min) = parse_hhmm_to_minutes(start_time)
            && let Some(candidates) = occupancy
                .by_course_weekday
                .get(&(clean_course.clone(), weekday))
            && let Some(matched_slot) =
                pick_matching_slot(candidates, &clean_course, &target_secs, start_min)
        {
            if let Some(focus) = find_feature_focus(campuses, &matched_slot.raw.room_id) {
                return Resolution::ExactRoom {
                    campus_id: focus.campus_id.clone(),
                    building_id: matched_slot.raw.building_id.clone(),
                    room_id: matched_slot.raw.room_id.clone(),
                    floor: focus.target_floor,
                    camera_focus: focus,
                };
            }
            if let Some(focus) = find_feature_focus(campuses, &matched_slot.raw.building_id) {
                return Resolution::ExactRoom {
                    campus_id: matched_slot.raw.campus_id.clone(),
                    building_id: matched_slot.raw.building_id.clone(),
                    room_id: matched_slot.raw.room_id.clone(),
                    floor: focus.target_floor,
                    camera_focus: focus,
                };
            }
        }

        // Fallback pelo nome/alias do prédio no Atestado.
        let norm_building = normalize_lookup_key(fallback_building_raw);
        if !norm_building.is_empty() {
            let resolved_id = self
                .atestado_aliases
                .get(&norm_building)
                .cloned()
                .unwrap_or_else(|| norm_building.replace(' ', "_"));

            if let Some(focus) = find_building_focus(campuses, &resolved_id) {
                return Resolution::BuildingFallback {
                    campus_id: focus.campus_id.clone(),
                    building_id: focus.feature_id.clone(),
                    camera_focus: focus,
                };
            }
        }

        Resolution::Unmapped
    }
}

fn pick_matching_slot<'a>(
    candidates: &'a [IndexedSlot],
    course_code: &str,
    target_sections: &[String],
    start_min: u32,
) -> Option<&'a IndexedSlot> {
    let exact_time: Vec<&IndexedSlot> = candidates
        .iter()
        .filter(|s| s.start_min == start_min)
        .collect();
    let pool: Vec<&IndexedSlot> = if !exact_time.is_empty() {
        exact_time
    } else {
        candidates
            .iter()
            .filter(|s| start_min >= s.start_min && start_min < s.end_min)
            .collect()
    };
    if pool.is_empty() {
        return None;
    }

    // 1. Correspondência explícita de turma (incluindo agrupadas como 21/22 e 41 a 44).
    if !target_sections.is_empty() {
        for slot in &pool {
            if slot.expanded_courses.iter().any(|(code, secs)| {
                code == course_code && secs.iter().any(|s| target_sections.contains(s))
            }) {
                return Some(*slot);
            }
        }
    }

    // 2. Entrada sem turma específica (sections vazio), comum em disciplinas de turma única.
    for slot in &pool {
        if slot
            .expanded_courses
            .iter()
            .any(|(code, secs)| code == course_code && secs.is_empty())
        {
            return Some(*slot);
        }
    }

    // 3. Se o caller não informou turma, aceitar se todas as candidatas apontam para a mesma sala.
    if target_sections.is_empty() {
        let first_room = &pool[0].raw.room_id;
        if pool.iter().all(|s| &s.raw.room_id == first_room) {
            return Some(pool[0]);
        }
    }

    None
}

pub fn find_feature_focus(campuses: &[LoadedCampus], feature_id: &str) -> Option<CameraFocus> {
    for campus in campuses {
        if let Some(feat) = campus
            .features
            .iter()
            .find(|f| f.info.id.eq_ignore_ascii_case(feature_id))
        {
            let target_floor = feat
                .info
                .floor
                .or(feat.info.default_floor)
                .unwrap_or(campus.summary.default_floor);
            return Some(focus_for_bounds(
                &campus.summary.campus_id,
                &feat.info.id,
                target_floor,
                feat.info.bounds,
            ));
        }
    }
    None
}

fn find_building_focus(campuses: &[LoadedCampus], building_id: &str) -> Option<CameraFocus> {
    // Priorizar Building -> CampusZone -> CampusGround -> match por código/alias.
    for target_layer in [
        MapLayer::Building,
        MapLayer::CampusZone,
        MapLayer::CampusGround,
    ] {
        for campus in campuses {
            if let Some(feat) = campus.features.iter().find(|f| {
                f.info.layer == target_layer && f.info.id.eq_ignore_ascii_case(building_id)
            }) {
                let target_floor = feat
                    .info
                    .floor
                    .or(feat.info.default_floor)
                    .unwrap_or(campus.summary.default_floor);
                return Some(focus_for_bounds(
                    &campus.summary.campus_id,
                    &feat.info.id,
                    target_floor,
                    feat.info.bounds,
                ));
            }
        }
    }

    for campus in campuses {
        if let Some(feat) = campus.features.iter().find(|f| {
            matches!(
                f.info.layer,
                MapLayer::Building | MapLayer::CampusZone | MapLayer::CampusGround
            ) && (f
                .info
                .code
                .as_deref()
                .is_some_and(|c| c.eq_ignore_ascii_case(building_id))
                || f.info
                    .aliases
                    .iter()
                    .any(|a| normalize_lookup_key(a) == building_id))
        }) {
            let target_floor = feat
                .info
                .floor
                .or(feat.info.default_floor)
                .unwrap_or(campus.summary.default_floor);
            return Some(focus_for_bounds(
                &campus.summary.campus_id,
                &feat.info.id,
                target_floor,
                feat.info.bounds,
            ));
        }
    }

    None
}
