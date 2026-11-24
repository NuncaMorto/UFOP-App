use std::collections::{HashMap, HashSet};

use geo::{Contains, MultiPolygon, Point, Polygon};

use crate::CampusError;

use super::super::projection::{LocalProjector, ProjectedGeometry, distance_to_polygon_outline};
use super::super::types::{
    CampusMapSummary, FloorLabel, IndoorStatus, MapLayer, OverlayValidationReport, Rect2D,
};
use super::parser::{
    BaseMetadata, OverlayMetadata, default_floor_label, extract_metadata_value, finalize_feature,
    parse_feature_collection, parse_raw_feature,
};
use super::{LoadedCampus, LoadedFeature};

/// Carrega um campus GeoJSON e aplica overlays declarados (plug-and-play para plantas futuras).
pub fn load_campus_with_overlays(
    base_geojson: &str,
    overlays_geojson: &[&str],
) -> Result<LoadedCampus, CampusError> {
    let base_fc = parse_feature_collection(base_geojson)?;
    let base_meta_val = extract_metadata_value(&base_fc)?;
    let base_meta: BaseMetadata = serde_json::from_value(base_meta_val.clone())
        .map_err(|err| CampusError::InvalidInput(format!("Metadata do campus inválida: {err}")))?;

    let projector = LocalProjector::new(base_meta.origin_lat, base_meta.origin_lon);
    let mut features: Vec<LoadedFeature> = Vec::with_capacity(base_fc.features.len());

    for raw_feat in &base_fc.features {
        if let Some(parsed) = parse_raw_feature(raw_feat, &projector, None)? {
            features.push(finalize_feature(parsed, &base_meta.campus_id));
        }
    }

    let mut floor_set: HashSet<i32> = base_meta.available_floors.iter().copied().collect();
    if floor_set.is_empty() {
        floor_set.insert(base_meta.default_floor);
    }

    for overlay_raw in overlays_geojson {
        let ov_fc = parse_feature_collection(overlay_raw)?;
        let ov_meta_val = extract_metadata_value(&ov_fc)?;
        let ov_meta: OverlayMetadata =
            serde_json::from_value(ov_meta_val.clone()).map_err(|err| {
                CampusError::InvalidInput(format!("Metadata do overlay inválida: {err}"))
            })?;

        if !ov_meta.campus_id.is_empty() && ov_meta.campus_id != base_meta.campus_id {
            continue;
        }

        let target_building = ov_meta.building_id.trim();
        let replaced_floors: HashSet<i32> = ov_meta.replaces_mock_floors.iter().copied().collect();

        let mut ov_features: Vec<LoadedFeature> = Vec::with_capacity(ov_fc.features.len());
        let mut ov_ids: HashSet<String> = HashSet::with_capacity(ov_fc.features.len());
        for raw_feat in &ov_fc.features {
            if let Some(parsed) = parse_raw_feature(raw_feat, &projector, Some(target_building))? {
                if let Some(fl) = parsed.floor {
                    floor_set.insert(fl);
                }
                ov_ids.insert(parsed.id.clone());
                ov_features.push(finalize_feature(parsed, &base_meta.campus_id));
            }
        }

        // Remove features provisórias nos andares substituídos E quaisquer IDs redefinidos pelo overlay.
        features.retain(|feat| {
            if ov_ids.contains(&feat.info.id) {
                return false;
            }
            if !feat.info.is_mock {
                return true;
            }
            let belongs_to_building = feat.info.building_id.as_deref() == Some(target_building)
                || feat.info.parent_id.as_deref() == Some(target_building);
            if !belongs_to_building {
                return true;
            }
            match feat.info.floor {
                Some(fl) => !replaced_floors.contains(&fl),
                None => true,
            }
        });

        features.extend(ov_features);
    }

    recompute_building_indoor_statuses(&mut features);

    let mut available_floors: Vec<i32> = floor_set.into_iter().collect();
    available_floors.sort_unstable();

    let mut label_map: HashMap<i32, String> = HashMap::new();
    for (k, v) in &base_meta.floor_labels {
        if let Ok(fl) = k.trim().parse::<i32>() {
            label_map.insert(fl, v.trim().to_string());
        }
    }
    let floor_labels: Vec<FloorLabel> = available_floors
        .iter()
        .map(|&fl| FloorLabel {
            floor: fl,
            label: label_map
                .get(&fl)
                .cloned()
                .unwrap_or_else(|| default_floor_label(fl)),
        })
        .collect();

    let bounds = compute_campus_bounds(&features);

    Ok(LoadedCampus {
        summary: CampusMapSummary {
            campus_id: base_meta.campus_id,
            name: base_meta.name,
            city: base_meta.city,
            default_floor: base_meta.default_floor,
            available_floors,
            floor_labels,
            bounds,
            attribution: base_meta.attribution,
        },
        projector,
        features,
    })
}

fn recompute_building_indoor_statuses(features: &mut [LoadedFeature]) {
    let mut stats_by_building: HashMap<String, (usize, usize)> = HashMap::new();

    for feat in features.iter() {
        if feat.info.layer != MapLayer::Room {
            continue;
        }
        let mut keys = HashSet::new();
        if let Some(bid) = &feat.info.building_id {
            keys.insert(bid.clone());
        }
        if let Some(pid) = &feat.info.parent_id {
            keys.insert(pid.clone());
        }
        for key in keys {
            let entry = stats_by_building.entry(key).or_insert((0, 0));
            if feat.info.is_mock {
                entry.1 += 1;
            } else {
                entry.0 += 1;
            }
        }
    }

    for feat in features.iter_mut() {
        if !matches!(feat.info.layer, MapLayer::Building | MapLayer::CampusZone) {
            continue;
        }
        let (real_count, mock_count) = stats_by_building
            .get(&feat.info.id)
            .copied()
            .unwrap_or((0, 0));
        feat.info.indoor_status = match (real_count, mock_count) {
            (r, 0) if r > 0 => IndoorStatus::Floorplan,
            (r, m) if r > 0 && m > 0 => IndoorStatus::PartialFloorplan,
            (0, m) if m > 0 => IndoorStatus::Provisional,
            _ => IndoorStatus::ExteriorOnly,
        };
    }
}

fn compute_campus_bounds(features: &[LoadedFeature]) -> Rect2D {
    let mut min_x = f32::INFINITY;
    let mut min_y = f32::INFINITY;
    let mut max_x = f32::NEG_INFINITY;
    let mut max_y = f32::NEG_INFINITY;

    for feat in features {
        min_x = min_x.min(feat.info.bounds.min_x);
        min_y = min_y.min(feat.info.bounds.min_y);
        max_x = max_x.max(feat.info.bounds.max_x);
        max_y = max_y.max(feat.info.bounds.max_y);
    }

    if min_x.is_finite() {
        Rect2D {
            min_x,
            min_y,
            max_x,
            max_y,
        }
    } else {
        Rect2D {
            min_x: -100.0,
            min_y: -100.0,
            max_x: 100.0,
            max_y: 100.0,
        }
    }
}

/// Valida integridade geométrica e contratual de um novo arquivo de overlay indoor.
pub fn validate_overlay(
    base_geojson: &str,
    overlay_geojson: &str,
) -> Result<OverlayValidationReport, CampusError> {
    let base_fc = parse_feature_collection(base_geojson)?;
    let base_meta_val = extract_metadata_value(&base_fc)?;
    let base_meta: BaseMetadata = serde_json::from_value(base_meta_val.clone()).map_err(|err| {
        CampusError::InvalidInput(format!("Metadata do mapa base inválida: {err}"))
    })?;
    let projector = LocalProjector::new(base_meta.origin_lat, base_meta.origin_lon);

    let ov_fc = parse_feature_collection(overlay_geojson)?;
    let ov_meta_val = extract_metadata_value(&ov_fc)?;
    let ov_meta: OverlayMetadata = serde_json::from_value(ov_meta_val.clone())
        .map_err(|err| CampusError::InvalidInput(format!("Metadata do overlay inválida: {err}")))?;

    let building_id = ov_meta.building_id.trim();
    if building_id.is_empty() || building_id.contains('<') {
        return Err(CampusError::InvalidInput(
            "Metadata do overlay precisa definir 'building_id' válido.".into(),
        ));
    }
    if !ov_meta.campus_id.is_empty() && ov_meta.campus_id != base_meta.campus_id {
        return Err(CampusError::InvalidInput(format!(
            "Campus do overlay ('{}') difere do mapa base ('{}').",
            ov_meta.campus_id, base_meta.campus_id
        )));
    }
    if ov_fc.features.is_empty() {
        return Err(CampusError::InvalidInput(
            "Overlay não contém nenhuma feição.".into(),
        ));
    }

    // Localizar prédio/zona alvo no mapa base e extrair polígonos de contorno externo (sem holes).
    let mut target_building: Option<LoadedFeature> = None;
    for raw_feat in &base_fc.features {
        if let Some(parsed) = parse_raw_feature(raw_feat, &projector, None)?
            && parsed.id == building_id
            && matches!(parsed.layer, MapLayer::Building | MapLayer::CampusZone)
        {
            target_building = Some(finalize_feature(parsed, &base_meta.campus_id));
            break;
        }
    }
    let building = target_building.ok_or_else(|| {
        CampusError::NotFound(format!(
            "Prédio ou zona '{building_id}' não encontrado no mapa base '{}'.",
            base_meta.campus_id
        ))
    })?;

    let building_outer_polys: Vec<Polygon<f64>> = match &building.geometry {
        ProjectedGeometry::Polygon(mp) => {
            mp.0.iter()
                .map(|p| Polygon::new(p.exterior().clone(), Vec::new()))
                .collect()
        }
        _ => {
            return Err(CampusError::InvalidInput(format!(
                "Prédio '{building_id}' não possui geometria poligonal."
            )));
        }
    };
    let building_outer_mp = MultiPolygon::new(building_outer_polys.clone());

    let valid_floors: HashSet<i32> = if !building.info.available_floors.is_empty() {
        building.info.available_floors.iter().copied().collect()
    } else if !base_meta.available_floors.is_empty() {
        base_meta.available_floors.iter().copied().collect()
    } else {
        [building
            .info
            .default_floor
            .unwrap_or(base_meta.default_floor)]
        .into_iter()
        .collect()
    };

    let expected_prefix = format!("{building_id}_");
    let mut seen_ids: HashSet<String> = HashSet::with_capacity(ov_fc.features.len());

    for raw_feat in &ov_fc.features {
        let Some(parsed) = parse_raw_feature(raw_feat, &projector, Some(building_id))? else {
            return Err(CampusError::InvalidInput(
                "Feição inválida ou sem camada/geometria no overlay.".into(),
            ));
        };

        if !seen_ids.insert(parsed.id.clone()) {
            return Err(CampusError::InvalidInput(format!(
                "ID duplicado no overlay '{}': '{}'.",
                building_id, parsed.id
            )));
        }

        if parsed.layer == MapLayer::Room {
            if !parsed.id.starts_with(&expected_prefix) || parsed.id.len() <= expected_prefix.len()
            {
                return Err(CampusError::InvalidInput(format!(
                    "room_id '{}' deve seguir a convenção '{building_id}_<SALA>'.",
                    parsed.id
                )));
            }
            let suffix = &parsed.id[expected_prefix.len()..];
            if !suffix
                .chars()
                .all(|c| c.is_ascii_uppercase() || c.is_ascii_digit() || c == '-' || c == '_')
            {
                return Err(CampusError::InvalidInput(format!(
                    "Sufixo inválido em room_id '{}'.",
                    parsed.id
                )));
            }
            if parsed.building_id.as_deref() != Some(building_id) {
                return Err(CampusError::InvalidInput(format!(
                    "Feição '{}' possui building_id divergente de '{}'.",
                    parsed.id, building_id
                )));
            }
        }

        let Some(floor) = parsed.floor else {
            return Err(CampusError::InvalidInput(format!(
                "Feição '{}' está sem campo inteiro 'floor'.",
                parsed.id
            )));
        };
        if !valid_floors.contains(&floor) {
            return Err(CampusError::InvalidInput(format!(
                "Andar inválido ({floor}) em '{}'; válidos para '{}': {:?}.",
                parsed.id, building_id, valid_floors
            )));
        }

        validate_geometry_inside_building(
            &parsed.id,
            parsed.layer,
            &parsed.geometry,
            &building_outer_mp,
            &building_outer_polys,
            building_id,
        )?;
    }

    Ok(OverlayValidationReport {
        building_id: building_id.to_string(),
        checked_features: seen_ids.len() as u32,
        replaced_floors: ov_meta.replaces_mock_floors,
    })
}

fn min_distance_to_outer_polys(x: f64, y: f64, polys: &[Polygon<f64>]) -> f64 {
    let pt = Point::new(x, y);
    let mut best = f64::INFINITY;
    for poly in polys {
        if poly.contains(&pt) {
            return 0.0;
        }
        best = best.min(distance_to_polygon_outline(x, y, poly));
    }
    best
}

fn validate_geometry_inside_building(
    feature_id: &str,
    layer: MapLayer,
    geom: &ProjectedGeometry,
    outer_mp: &MultiPolygon<f64>,
    outer_polys: &[Polygon<f64>],
    building_id: &str,
) -> Result<(), CampusError> {
    match (layer, geom) {
        (MapLayer::Poi, ProjectedGeometry::Point(pt)) => {
            let dist = min_distance_to_outer_polys(pt.x(), pt.y(), outer_polys);
            if dist > 10.0 {
                return Err(CampusError::InvalidInput(format!(
                    "POI '{feature_id}' está fora do polígono de '{building_id}' ({dist:.1}m de distância)."
                )));
            }
        }
        (_, ProjectedGeometry::Polygon(mp)) => {
            let (ax, ay) = geom.compute_label_anchor();
            let center_dist = min_distance_to_outer_polys(ax, ay, outer_polys);
            if !outer_mp.contains(&Point::new(ax, ay)) && center_dist > 1.5 {
                return Err(CampusError::InvalidInput(format!(
                    "Geometria de '{feature_id}' está fora do polígono de '{building_id}' (centro a {center_dist:.1}m)."
                )));
            }
            for poly in &mp.0 {
                for coord in &poly.exterior().0 {
                    let vdist = min_distance_to_outer_polys(coord.x, coord.y, outer_polys);
                    if vdist > 3.0 {
                        return Err(CampusError::InvalidInput(format!(
                            "Vértice de '{feature_id}' extrapola o polígono de '{building_id}' em {vdist:.1}m."
                        )));
                    }
                }
            }
        }
        _ => {}
    }
    Ok(())
}
