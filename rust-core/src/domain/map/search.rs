use super::overlay::LoadedFeature;
use super::resolver::normalize_lookup_key;
use super::types::{MapLayer, PoiType, SearchResult};

fn matches_poi_filter(feat: &LoadedFeature, filter: PoiType) -> bool {
    if feat.info.poi_type == Some(filter) {
        return true;
    }
    let cat = feat
        .info
        .category
        .as_deref()
        .unwrap_or("")
        .to_ascii_lowercase();
    match filter {
        PoiType::Library => cat == "library",
        PoiType::Dining => cat == "dining",
        PoiType::Parking => feat.info.layer == MapLayer::Parking || cat == "parking",
        PoiType::FacultyOffice => cat == "faculty_office",
        PoiType::Academic => {
            matches!(
                feat.info.layer,
                MapLayer::Building | MapLayer::CampusZone | MapLayer::Room
            ) && (cat == "academic" || cat == "academic_zone" || cat == "classroom" || cat == "lab")
        }
        PoiType::Toilet | PoiType::Entrance => false,
    }
}

pub fn search_features(
    features: &[LoadedFeature],
    query: &str,
    poi_type_filter: Option<PoiType>,
    active_floor: i32,
) -> Vec<SearchResult> {
    let norm_query = normalize_lookup_key(query);
    if norm_query.is_empty() && poi_type_filter.is_none() {
        return Vec::new();
    }
    let mut results: Vec<SearchResult> = Vec::new();

    for feat in features {
        if !matches!(
            feat.info.layer,
            MapLayer::Poi | MapLayer::Room | MapLayer::Building | MapLayer::CampusZone
        ) && poi_type_filter != Some(PoiType::Parking)
        {
            continue;
        }
        if let Some(filter) = poi_type_filter
            && !matches_poi_filter(feat, filter)
        {
            continue;
        }

        if norm_query.is_empty() {
            let floor_bonus = match feat.info.floor {
                Some(floor) if floor == active_floor => 30,
                None => 20,
                Some(_) => 0,
            };
            results.push(SearchResult {
                feature: feat.info.clone(),
                score: 50 + floor_bonus,
                matched_field: "poi_type".to_string(),
            });
            continue;
        }

        let mut best: Option<(u32, &'static str)> = None;

        if let Some(code) = &feat.info.code {
            let nc = normalize_lookup_key(code);
            if nc == norm_query {
                best = Some((1000, "code"));
            } else if nc.starts_with(&norm_query) {
                best = Some((850, "code"));
            } else if nc.contains(&norm_query) {
                best = Some((650, "code"));
            }
        }

        let nid = normalize_lookup_key(&feat.info.id);
        if nid == norm_query {
            best = Some(best.map_or((980, "id"), |b| b.max((980, "id"))));
        }

        let nname = normalize_lookup_key(&feat.info.name);
        if nname == norm_query {
            best = Some(best.map_or((920, "name"), |b| b.max((920, "name"))));
        } else if nname.contains(&norm_query) {
            best = Some(best.map_or((700, "name"), |b| b.max((700, "name"))));
        }

        for alias in &feat.info.aliases {
            let na = normalize_lookup_key(alias);
            if na == norm_query {
                best = Some(best.map_or((880, "aliases"), |b| b.max((880, "aliases"))));
            } else if na.contains(&norm_query) {
                best = Some(best.map_or((620, "aliases"), |b| b.max((620, "aliases"))));
            }
        }

        for dept in &feat.info.departments {
            let nd = normalize_lookup_key(dept);
            if nd == norm_query {
                best = Some(best.map_or((820, "departments"), |b| b.max((820, "departments"))));
            } else if nd.contains(&norm_query) {
                best = Some(best.map_or((580, "departments"), |b| b.max((580, "departments"))));
            }
        }

        if let Some((mut score, field)) = best {
            if feat.info.floor == Some(active_floor) {
                score += 25;
            }
            if feat.info.layer == MapLayer::Room {
                score += 10;
            }
            results.push(SearchResult {
                feature: feat.info.clone(),
                score,
                matched_field: field.to_string(),
            });
        }
    }

    results.sort_by(|a, b| {
        b.score
            .cmp(&a.score)
            .then_with(|| a.feature.name.cmp(&b.feature.name))
    });
    results.truncate(30);
    results
}
