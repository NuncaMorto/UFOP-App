pub mod assets;
pub mod occupancy;
pub mod overlay;
pub mod projection;
pub mod render;
pub mod resolver;
pub mod search;
pub mod spatial;
pub mod types;

#[cfg(test)]
mod tests;

use std::sync::{Arc, RwLock};

use serde::Deserialize;

use crate::CampusError;

use self::assets::{CAMPUS_MANIFEST_JSON, MAP_SCHEMA_CONTRACT_JSON, OCCUPANCY_2026_2_JSON};
use self::occupancy::OccupancyIndex;
use self::overlay::{LoadedCampus, load_campus_with_overlays};
use self::render::build_render_batch;
use self::resolver::{MeetingResolver, find_feature_focus};
use self::search::search_features;
use self::spatial::CampusSpatialIndex;
use self::types::{
    CameraFocus, CampusMapSummary, FeatureInfo, MapHit, MapLayer, OccupancyStatus,
    OverlayValidationReport, PoiType, RenderBatch, Resolution, RoomOccupancyItem, SearchResult,
};

#[derive(Debug, Deserialize)]
struct CampusManifestDoc {
    campuses: Vec<CampusManifestEntry>,
}

#[derive(Debug, Deserialize)]
struct CampusManifestEntry {
    base_map: String,
    #[serde(default)]
    indoor_overlays: Vec<OverlayManifestEntry>,
}

#[derive(Debug, Deserialize)]
struct OverlayManifestEntry {
    file: String,
}

/// Motor vetorial offline 2D para os campi da UFOP e ocupação multi-campus 2026/2.
#[derive(uniffi::Object)]
pub struct CampusMapEngine {
    campuses: Vec<LoadedCampus>,
    spatial_indices: Vec<CampusSpatialIndex>,
    occupancy: OccupancyIndex,
    resolver: MeetingResolver,
    active_campus_idx: RwLock<usize>,
}

impl CampusMapEngine {
    fn from_loaded_parts(
        campuses: Vec<LoadedCampus>,
        occupancy: OccupancyIndex,
        resolver: MeetingResolver,
    ) -> Result<Self, CampusError> {
        if campuses.is_empty() {
            return Err(CampusError::InvalidInput(
                "Nenhum campus foi carregado no motor de mapa.".into(),
            ));
        }
        let spatial_indices = campuses
            .iter()
            .map(|c| CampusSpatialIndex::build(&c.features))
            .collect();
        Ok(Self {
            campuses,
            spatial_indices,
            occupancy,
            resolver,
            active_campus_idx: RwLock::new(0),
        })
    }

    fn current_campus_idx(&self) -> usize {
        self.active_campus_idx
            .read()
            .map(|guard| *guard)
            .unwrap_or(0)
            .min(self.campuses.len().saturating_sub(1))
    }

    fn active_campus(&self) -> (&LoadedCampus, &CampusSpatialIndex) {
        let idx = self.current_campus_idx();
        (&self.campuses[idx], &self.spatial_indices[idx])
    }
}

#[uniffi::export]
impl CampusMapEngine {
    /// Carrega todos os 4 campi, seus overlays declarados em `campus_manifest.json` e a ocupação 2026/2 embutida.
    #[uniffi::constructor]
    pub fn new_bundled() -> Result<Arc<Self>, CampusError> {
        let manifest: CampusManifestDoc =
            serde_json::from_str(CAMPUS_MANIFEST_JSON).map_err(|err| {
                CampusError::InvalidInput(format!("Manifesto de campi inválido: {err}"))
            })?;

        let mut loaded = Vec::with_capacity(manifest.campuses.len());
        for entry in &manifest.campuses {
            let base = assets::get_embedded_asset(&entry.base_map).ok_or_else(|| {
                CampusError::NotFound(format!("Asset de mapa não embutido: '{}'.", entry.base_map))
            })?;
            let mut overlays = Vec::with_capacity(entry.indoor_overlays.len());
            for ov in &entry.indoor_overlays {
                let ov_json = assets::get_embedded_asset(&ov.file).ok_or_else(|| {
                    CampusError::NotFound(format!("Asset de overlay não embutido: '{}'.", ov.file))
                })?;
                overlays.push(ov_json);
            }
            loaded.push(load_campus_with_overlays(base, &overlays)?);
        }

        let occupancy = OccupancyIndex::from_json(OCCUPANCY_2026_2_JSON)?;
        let resolver = MeetingResolver::from_contract_json(MAP_SCHEMA_CONTRACT_JSON);
        Ok(Arc::new(Self::from_loaded_parts(
            loaded, occupancy, resolver,
        )?))
    }

    /// Carrega um mapa base GeoJSON aplicando overlays plug-and-play (usando ocupação embutida 2026/2).
    #[uniffi::constructor]
    pub fn load_with_overlays(
        base_geojson: String,
        overlays_geojson: Vec<String>,
    ) -> Result<Arc<Self>, CampusError> {
        Self::load_with_overlays_and_occupancy(base_geojson, overlays_geojson, None)
    }

    /// Carrega um mapa base GeoJSON com overlays e arquivo de ocupação customizado (ou embutido quando `None`).
    #[uniffi::constructor]
    pub fn load_with_overlays_and_occupancy(
        base_geojson: String,
        overlays_geojson: Vec<String>,
        occupancy_json: Option<String>,
    ) -> Result<Arc<Self>, CampusError> {
        let overlay_refs: Vec<&str> = overlays_geojson.iter().map(String::as_str).collect();
        let campus = load_campus_with_overlays(&base_geojson, &overlay_refs)?;
        let occupancy = match occupancy_json.as_deref() {
            Some(raw) if !raw.trim().is_empty() => OccupancyIndex::from_json(raw)?,
            Some(_) => OccupancyIndex::empty(),
            None => OccupancyIndex::from_json(OCCUPANCY_2026_2_JSON)?,
        };
        let resolver = MeetingResolver::from_contract_json(MAP_SCHEMA_CONTRACT_JSON);
        Ok(Arc::new(Self::from_loaded_parts(
            vec![campus],
            occupancy,
            resolver,
        )?))
    }

    pub fn list_campuses(&self) -> Vec<CampusMapSummary> {
        self.campuses.iter().map(|c| c.summary.clone()).collect()
    }

    pub fn get_active_campus_id(&self) -> String {
        self.active_campus().0.summary.campus_id.clone()
    }

    pub fn set_active_campus(&self, campus_id: String) -> Result<CampusMapSummary, CampusError> {
        let target = campus_id.trim();
        let idx = self
            .campuses
            .iter()
            .position(|c| c.summary.campus_id.eq_ignore_ascii_case(target))
            .ok_or_else(|| {
                CampusError::NotFound(format!("Campus não carregado no mapa: '{campus_id}'."))
            })?;
        if let Ok(mut guard) = self.active_campus_idx.write() {
            *guard = idx;
        }
        Ok(self.campuses[idx].summary.clone())
    }

    pub fn get_feature(&self, feature_id: String) -> Option<FeatureInfo> {
        let target = feature_id.trim();
        let (active, _) = self.active_campus();
        if let Some(found) = active
            .features
            .iter()
            .find(|f| f.info.id.eq_ignore_ascii_case(target))
        {
            return Some(found.info.clone());
        }
        for campus in &self.campuses {
            if let Some(found) = campus
                .features
                .iter()
                .find(|f| f.info.id.eq_ignore_ascii_case(target))
            {
                return Some(found.info.clone());
            }
        }
        None
    }

    pub fn hit_test(&self, map_x: f32, map_y: f32, active_floor: i32, zoom: f32) -> Option<MapHit> {
        let (campus, index) = self.active_campus();
        index.hit_test(&campus.features, map_x, map_y, active_floor, zoom)
    }

    pub fn get_camera_focus(&self, feature_id: String) -> Option<CameraFocus> {
        let target = feature_id.trim();
        let (active, _) = self.active_campus();
        if let Some(feat) = active
            .features
            .iter()
            .find(|f| f.info.id.eq_ignore_ascii_case(target))
        {
            let target_floor = feat
                .info
                .floor
                .or(feat.info.default_floor)
                .unwrap_or(active.summary.default_floor);
            return Some(resolver::focus_for_bounds(
                &active.summary.campus_id,
                &feat.info.id,
                target_floor,
                feat.info.bounds,
            ));
        }
        find_feature_focus(&self.campuses, target)
    }

    pub fn search(
        &self,
        query: String,
        poi_type_filter: Option<PoiType>,
        active_floor: i32,
    ) -> Vec<SearchResult> {
        let (campus, _) = self.active_campus();
        search_features(&campus.features, &query, poi_type_filter, active_floor)
    }

    pub fn resolve_meeting(
        &self,
        course_code: String,
        section: String,
        weekday: u32,
        start_time: String,
        fallback_building_raw: String,
    ) -> Resolution {
        self.resolver.resolve_meeting(
            &self.occupancy,
            &self.campuses,
            &course_code,
            &section,
            weekday,
            &start_time,
            &fallback_building_raw,
        )
    }

    pub fn get_room_occupancy(
        &self,
        room_id: String,
        timestamp_epoch_or_iso: String,
    ) -> Result<OccupancyStatus, CampusError> {
        let clean = room_id.trim();
        let found_feat = self
            .campuses
            .iter()
            .flat_map(|c| c.features.iter())
            .find(|f| f.info.id.eq_ignore_ascii_case(clean));

        let (mapped_building, exists) = match found_feat {
            Some(f) if f.info.layer == MapLayer::Room => (f.info.building_id.as_deref(), true),
            Some(f) if matches!(f.info.layer, MapLayer::Building | MapLayer::CampusZone) => {
                (Some(f.info.id.as_str()), false)
            }
            _ => (None, false),
        };

        self.occupancy
            .get_room_occupancy(clean, mapped_building, exists, &timestamp_epoch_or_iso)
    }

    pub fn list_building_occupancy(
        &self,
        building_id: String,
        timestamp_epoch_or_iso: String,
    ) -> Result<Vec<RoomOccupancyItem>, CampusError> {
        let clean_bid = building_id.trim();
        let mut rooms: Vec<(String, String, i32, bool)> = self
            .campuses
            .iter()
            .flat_map(|c| c.features.iter())
            .filter(|f| {
                f.info.layer == MapLayer::Room
                    && (f
                        .info
                        .building_id
                        .as_deref()
                        .is_some_and(|b| b.eq_ignore_ascii_case(clean_bid))
                        || f.info
                            .parent_id
                            .as_deref()
                            .is_some_and(|p| p.eq_ignore_ascii_case(clean_bid)))
            })
            .map(|f| {
                (
                    f.info.id.clone(),
                    f.info.code.clone().unwrap_or_else(|| f.info.id.clone()),
                    f.info.floor.unwrap_or(0),
                    f.info.is_mock,
                )
            })
            .collect();

        rooms.sort_by(|a, b| a.2.cmp(&b.2).then_with(|| a.0.cmp(&b.0)));
        self.occupancy
            .list_building_occupancy(clean_bid, &rooms, &timestamp_epoch_or_iso)
    }

    pub fn get_render_batch(
        &self,
        active_floor: i32,
        timestamp_opt: Option<String>,
    ) -> Result<RenderBatch, CampusError> {
        let (campus, _) = self.active_campus();
        build_render_batch(
            campus,
            &self.occupancy,
            active_floor,
            timestamp_opt.as_deref(),
        )
    }
}

/// Valida um overlay GeoJSON contra um mapa base GeoJSON sem integrá-lo ao motor.
pub fn validate_overlay(
    base_geojson: String,
    overlay_geojson: String,
) -> Result<OverlayValidationReport, CampusError> {
    overlay::validate_overlay(&base_geojson, &overlay_geojson)
}
