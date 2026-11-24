use std::collections::HashMap;

use serde::Deserialize;
use serde_json::{Map as JsonMap, Value as JsonValue};

use crate::CampusError;

use super::super::projection::{LocalProjector, ProjectedGeometry, project_geojson_geometry};
use super::super::types::{FeatureInfo, FloorLabel, IndoorStatus, MapLayer, PoiType};
use super::LoadedFeature;

#[derive(Debug, Deserialize)]
pub(super) struct BaseMetadata {
    pub(super) campus_id: String,
    pub(super) name: String,
    #[serde(default)]
    pub(super) city: String,
    pub(super) origin_lat: f64,
    pub(super) origin_lon: f64,
    #[serde(default)]
    pub(super) available_floors: Vec<i32>,
    #[serde(default)]
    pub(super) default_floor: i32,
    #[serde(default)]
    pub(super) floor_labels: HashMap<String, String>,
    #[serde(default)]
    pub(super) attribution: String,
}

#[derive(Debug, Deserialize)]
pub(super) struct OverlayMetadata {
    #[serde(default)]
    pub(super) campus_id: String,
    pub(super) building_id: String,
    #[serde(default)]
    pub(super) replaces_mock_floors: Vec<i32>,
}

pub(super) struct ParsedRawFeature {
    pub(super) id: String,
    pub(super) layer: MapLayer,
    code: Option<String>,
    name: String,
    category: Option<String>,
    pub(super) building_id: Option<String>,
    parent_id: Option<String>,
    pub(super) floor: Option<i32>,
    available_floors: Vec<i32>,
    floor_labels: Vec<FloorLabel>,
    default_floor: Option<i32>,
    poi_type: Option<PoiType>,
    explicit_indoor_status: Option<IndoorStatus>,
    is_mock: bool,
    aliases: Vec<String>,
    departments: Vec<String>,
    notes: Option<String>,
    min_zoom: f32,
    pub(super) geometry: ProjectedGeometry,
    area_m2: f64,
}

pub fn default_floor_label(floor: i32) -> String {
    match floor {
        -1 => "Subsolo".to_string(),
        0 => "Térreo".to_string(),
        n if n > 0 => format!("{n}º Andar"),
        n => format!("Subsolo {}", n.abs()),
    }
}

fn parse_floor_labels_map(raw: Option<&JsonValue>) -> Vec<FloorLabel> {
    let Some(JsonValue::Object(obj)) = raw else {
        return Vec::new();
    };
    let mut entries: Vec<FloorLabel> = obj
        .iter()
        .filter_map(|(k, v)| {
            let floor = k.trim().parse::<i32>().ok()?;
            let label = v.as_str()?.trim().to_string();
            Some(FloorLabel { floor, label })
        })
        .collect();
    entries.sort_by_key(|entry| entry.floor);
    entries
}

fn parse_string_list(raw: Option<&JsonValue>) -> Vec<String> {
    let Some(JsonValue::Array(arr)) = raw else {
        return Vec::new();
    };
    arr.iter()
        .filter_map(|v| v.as_str().map(|s| s.trim().to_string()))
        .filter(|s| !s.is_empty())
        .collect()
}

fn parse_i32_list(raw: Option<&JsonValue>) -> Vec<i32> {
    let Some(JsonValue::Array(arr)) = raw else {
        return Vec::new();
    };
    arr.iter()
        .filter_map(|v| v.as_i64().and_then(|n| i32::try_from(n).ok()))
        .collect()
}

fn get_str_prop<'a>(props: &'a JsonMap<String, JsonValue>, key: &str) -> Option<&'a str> {
    props
        .get(key)
        .and_then(|v| v.as_str())
        .map(str::trim)
        .filter(|s| !s.is_empty())
}

fn extract_feature_id(feature: &geojson::Feature, props: &JsonMap<String, JsonValue>) -> String {
    if let Some(id) = &feature.id {
        match id {
            geojson::feature::Id::String(s) if !s.trim().is_empty() => return s.trim().to_string(),
            geojson::feature::Id::Number(n) => return n.to_string(),
            _ => {}
        }
    }
    get_str_prop(props, "id").unwrap_or_default().to_string()
}

pub(super) fn parse_feature_collection(
    raw_geojson: &str,
) -> Result<geojson::FeatureCollection, CampusError> {
    let parsed: geojson::GeoJson = raw_geojson
        .parse()
        .map_err(|err| CampusError::InvalidInput(format!("GeoJSON inválido: {err}")))?;
    match parsed {
        geojson::GeoJson::FeatureCollection(fc) => Ok(fc),
        _ => Err(CampusError::InvalidInput(
            "GeoJSON raiz deve ser um FeatureCollection.".to_string(),
        )),
    }
}

pub(super) fn extract_metadata_value(
    fc: &geojson::FeatureCollection,
) -> Result<&JsonValue, CampusError> {
    fc.foreign_members
        .as_ref()
        .and_then(|fm| fm.get("metadata"))
        .ok_or_else(|| {
            CampusError::InvalidInput("FeatureCollection GeoJSON sem objeto 'metadata'.".into())
        })
}

pub(super) fn parse_raw_feature(
    feature: &geojson::Feature,
    projector: &LocalProjector,
    building_override: Option<&str>,
) -> Result<Option<ParsedRawFeature>, CampusError> {
    let empty_props = JsonMap::new();
    let props = feature.properties.as_ref().unwrap_or(&empty_props);
    let Some(layer_raw) = get_str_prop(props, "layer") else {
        return Ok(None);
    };
    let Some(layer) = MapLayer::from_geojson_str(layer_raw) else {
        return Err(CampusError::InvalidInput(format!(
            "Layer GeoJSON desconhecida: '{layer_raw}'."
        )));
    };
    let Some(geom_raw) = feature.geometry.as_ref() else {
        return Ok(None);
    };
    let Some(projected_geometry) = project_geojson_geometry(geom_raw, projector) else {
        return Ok(None);
    };
    let geometry = super::super::simplify::simplify_geometry_for_layer(layer, projected_geometry);

    let id = extract_feature_id(feature, props);
    if id.is_empty() {
        return Err(CampusError::InvalidInput(
            "Feição GeoJSON sem identificador ('id').".into(),
        ));
    }

    let code = get_str_prop(props, "code").map(ToString::to_string);
    let name = get_str_prop(props, "name")
        .or(code.as_deref())
        .unwrap_or(&id)
        .to_string();
    let category = get_str_prop(props, "category").map(ToString::to_string);
    let parent_id = get_str_prop(props, "parent_id").map(ToString::to_string);
    let building_id = get_str_prop(props, "building_id")
        .map(ToString::to_string)
        .or_else(|| building_override.map(ToString::to_string))
        .or_else(|| {
            if matches!(layer, MapLayer::Room | MapLayer::Poi) {
                parent_id.clone()
            } else {
                None
            }
        });
    let floor = props
        .get("floor")
        .and_then(|v| v.as_i64())
        .and_then(|n| i32::try_from(n).ok());
    let available_floors = parse_i32_list(props.get("floors"));
    let floor_labels = parse_floor_labels_map(props.get("floor_labels"));
    let default_floor = props
        .get("default_floor")
        .and_then(|v| v.as_i64())
        .and_then(|n| i32::try_from(n).ok());
    let poi_type = get_str_prop(props, "poi_type").and_then(PoiType::from_geojson_str);
    let explicit_indoor_status =
        get_str_prop(props, "indoor_status").and_then(IndoorStatus::from_geojson_str);
    let is_mock = props
        .get("is_mock")
        .and_then(|v| v.as_bool())
        .unwrap_or(false);
    let aliases = parse_string_list(props.get("aliases"));
    let departments = parse_string_list(props.get("departments"));
    let notes = get_str_prop(props, "notes").map(ToString::to_string);
    let min_zoom = props
        .get("min_zoom")
        .and_then(|v| v.as_f64())
        .map(|v| v as f32)
        .unwrap_or(match layer {
            MapLayer::Room => 1.8,
            MapLayer::Poi => 1.2,
            _ => 0.0,
        });
    let area_m2 = geometry.unsigned_area();

    Ok(Some(ParsedRawFeature {
        id,
        layer,
        code,
        name,
        category,
        building_id,
        parent_id,
        floor,
        available_floors,
        floor_labels,
        default_floor,
        poi_type,
        explicit_indoor_status,
        is_mock,
        aliases,
        departments,
        notes,
        min_zoom,
        geometry,
        area_m2,
    }))
}

pub(super) fn finalize_feature(raw: ParsedRawFeature, campus_id: &str) -> LoadedFeature {
    let bounds = raw.geometry.bounds();
    let (lx, ly) = raw.geometry.compute_label_anchor();
    let indoor_status = raw.explicit_indoor_status.unwrap_or(if raw.is_mock {
        IndoorStatus::Provisional
    } else if raw.layer == MapLayer::Room {
        IndoorStatus::Floorplan
    } else {
        IndoorStatus::ExteriorOnly
    });

    LoadedFeature {
        info: FeatureInfo {
            id: raw.id,
            campus_id: campus_id.to_string(),
            layer: raw.layer,
            code: raw.code,
            name: raw.name,
            category: raw.category,
            building_id: raw.building_id,
            parent_id: raw.parent_id,
            floor: raw.floor,
            available_floors: raw.available_floors,
            floor_labels: raw.floor_labels,
            default_floor: raw.default_floor,
            poi_type: raw.poi_type,
            indoor_status,
            is_mock: raw.is_mock,
            aliases: raw.aliases,
            departments: raw.departments,
            notes: raw.notes,
            min_zoom: raw.min_zoom,
            bounds,
            label_x: lx as f32,
            label_y: ly as f32,
        },
        geometry: raw.geometry,
        area_m2: raw.area_m2,
    }
}
