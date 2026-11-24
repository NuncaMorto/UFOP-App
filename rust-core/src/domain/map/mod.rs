pub mod assets;
pub mod occupancy;
pub mod overlay;
pub mod projection;
pub mod render;
pub mod resolver;
pub mod search;
pub mod simplify;
pub mod spatial;
pub mod types;

#[cfg(test)]
mod tests;

use std::sync::{Mutex, OnceLock, RwLock};

use serde::Deserialize;

use crate::CampusError;

use self::assets::{CAMPUS_MANIFEST_JSON, MAP_SCHEMA_CONTRACT_JSON, OCCUPANCY_2026_2_JSON};
use self::occupancy::OccupancyIndex;
use self::overlay::{LoadedCampus, default_floor_label, load_campus_with_overlays};
use self::render::build_render_batch;
use self::resolver::{MeetingResolver, find_feature_focus, find_matching_slot_for_meeting};
use self::search::search_features;
use self::spatial::CampusSpatialIndex;
use self::types::{
    CameraFocus, CampusMapSummary, FeatureInfo, FloorLabel, MapHit, MapLayer, OccupancyStatus,
    OverlayValidationReport, PoiType, Rect2D, RenderBatch, Resolution, RoomOccupancyItem,
    SearchResult,
};

#[derive(Debug, Deserialize)]
struct CampusManifestDoc {
    campuses: Vec<CampusManifestEntry>,
}

#[derive(Debug, Deserialize)]
struct CampusManifestEntry {
    #[serde(default)]
    campus_id: String,
    #[serde(default)]
    name: String,
    base_map: String,
    #[serde(default)]
    indoor_overlays: Vec<OverlayManifestEntry>,
}

#[derive(Debug, Deserialize)]
struct OverlayManifestEntry {
    file: String,
}

struct EmbeddedCampusSource {
    base_json: &'static str,
    overlay_jsons: Vec<&'static str>,
}

struct LoadedCampusEntry {
    campus: LoadedCampus,
    spatial: CampusSpatialIndex,
}

struct LazyCampusSlot {
    summary_stub: CampusMapSummary,
    source: Option<EmbeddedCampusSource>,
    init_lock: Mutex<()>,
    loaded: OnceLock<LoadedCampusEntry>,
}

impl LazyCampusSlot {
    fn from_loaded(campus: LoadedCampus) -> Self {
        let summary_stub = campus.summary.clone();
        let spatial = CampusSpatialIndex::build(&campus.features);
        let loaded = OnceLock::new();
        let _ = loaded.set(LoadedCampusEntry { campus, spatial });
        Self {
            summary_stub,
            source: None,
            init_lock: Mutex::new(()),
            loaded,
        }
    }

    fn from_manifest(
        entry: &CampusManifestEntry,
        base_json: &'static str,
        overlay_jsons: Vec<&'static str>,
    ) -> Self {
        let campus_id = entry.campus_id.trim().to_string();
        let city = infer_city_for_campus(&campus_id);
        let summary_stub = CampusMapSummary {
            campus_id,
            name: entry.name.trim().to_string(),
            city,
            default_floor: 0,
            available_floors: vec![0],
            floor_labels: vec![FloorLabel {
                floor: 0,
                label: default_floor_label(0),
            }],
            bounds: Rect2D {
                min_x: -100.0,
                min_y: -100.0,
                max_x: 100.0,
                max_y: 100.0,
            },
            attribution: "OpenStreetMap / UFOP".to_string(),
        };
        Self {
            summary_stub,
            source: Some(EmbeddedCampusSource {
                base_json,
                overlay_jsons,
            }),
            init_lock: Mutex::new(()),
            loaded: OnceLock::new(),
        }
    }

    fn campus_id(&self) -> &str {
        self.loaded
            .get()
            .map(|entry| entry.campus.summary.campus_id.as_str())
            .unwrap_or(&self.summary_stub.campus_id)
    }

    fn summary(&self) -> CampusMapSummary {
        self.loaded
            .get()
            .map(|entry| entry.campus.summary.clone())
            .unwrap_or_else(|| self.summary_stub.clone())
    }

    fn get_loaded(&self) -> Option<&LoadedCampusEntry> {
        self.loaded.get()
    }

    fn get_or_load(&self) -> Result<&LoadedCampusEntry, CampusError> {
        if let Some(entry) = self.loaded.get() {
            return Ok(entry);
        }
        let _guard = self
            .init_lock
            .lock()
            .map_err(|_| CampusError::InvalidInput("Falha ao adquirir lock do campus.".into()))?;
        if let Some(entry) = self.loaded.get() {
            return Ok(entry);
        }
        let Some(src) = &self.source else {
            return Err(CampusError::NotFound(format!(
                "Fonte do campus '{}' indisponível.",
                self.summary_stub.campus_id
            )));
        };
        let campus = load_campus_with_overlays(src.base_json, &src.overlay_jsons)?;
        let spatial = CampusSpatialIndex::build(&campus.features);
        let _ = self.loaded.set(LoadedCampusEntry { campus, spatial });
        self.loaded.get().ok_or_else(|| {
            CampusError::InvalidInput("Falha ao inicializar campus sob demanda.".into())
        })
    }
}

fn infer_city_for_campus(campus_id: &str) -> String {
    if campus_id.eq_ignore_ascii_case("JM_ICEA") {
        "João Monlevade".to_string()
    } else if campus_id.to_ascii_uppercase().starts_with("MA_") {
        "Mariana".to_string()
    } else {
        "Ouro Preto".to_string()
    }
}

/// Motor vetorial offline 2D para os campi da UFOP e ocupação multi-campus 2026/2.
pub struct CampusMapEngine {
    campuses: Vec<LazyCampusSlot>,
    occupancy: OccupancyIndex,
    resolver: MeetingResolver,
    active_campus_idx: RwLock<usize>,
}

impl CampusMapEngine {
    fn from_slots(
        campuses: Vec<LazyCampusSlot>,
        occupancy: OccupancyIndex,
        resolver: MeetingResolver,
    ) -> Result<Self, CampusError> {
        if campuses.is_empty() {
            return Err(CampusError::InvalidInput(
                "Nenhum campus foi carregado no motor de mapa.".into(),
            ));
        }
        // Materializa imediatamente apenas o campus ativo padrão; os demais são lazy sob demanda.
        campuses[0].get_or_load()?;
        Ok(Self {
            campuses,
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

    fn active_entry(&self) -> &LoadedCampusEntry {
        let idx = self.current_campus_idx();
        self.campuses[idx]
            .get_or_load()
            .expect("active campus must remain loadable")
    }

    fn loaded_campuses_active_first(&self) -> Vec<&LoadedCampus> {
        let active_idx = self.current_campus_idx();
        let mut out = Vec::with_capacity(self.campuses.len());
        if let Some(entry) = self.campuses[active_idx].get_loaded() {
            out.push(&entry.campus);
        }
        for (idx, slot) in self.campuses.iter().enumerate() {
            if idx != active_idx
                && let Some(entry) = slot.get_loaded()
            {
                out.push(&entry.campus);
            }
        }
        out
    }

    fn ensure_campus_loaded_by_id(&self, campus_id: &str) -> Option<&LoadedCampusEntry> {
        let target = campus_id.trim();
        if target.is_empty() {
            return None;
        }
        let slot = self
            .campuses
            .iter()
            .find(|slot| slot.campus_id().eq_ignore_ascii_case(target))?;
        slot.get_or_load().ok()
    }

    #[cfg(test)]
    pub(crate) fn loaded_campuses(&self) -> Vec<&LoadedCampus> {
        self.campuses
            .iter()
            .filter_map(|slot| slot.get_loaded().map(|entry| &entry.campus))
            .collect()
    }

    #[cfg(test)]
    pub(crate) fn loaded_campus_ids(&self) -> Vec<&str> {
        self.campuses
            .iter()
            .filter_map(|slot| {
                slot.get_loaded()
                    .map(|entry| entry.campus.summary.campus_id.as_str())
            })
            .collect()
    }

    /// Registra todos os 4 campi do manifesto e materializa apenas o campus inicial,
    /// carregando os demais sob demanda em `set_active_campus`, `resolve_meeting` ou consultas externas.
    pub fn new_bundled() -> Result<Self, CampusError> {
        let manifest: CampusManifestDoc =
            serde_json::from_str(CAMPUS_MANIFEST_JSON).map_err(|err| {
                CampusError::InvalidInput(format!("Manifesto de campi inválido: {err}"))
            })?;

        let mut slots = Vec::with_capacity(manifest.campuses.len());
        for entry in &manifest.campuses {
            let base_json = assets::get_embedded_asset(&entry.base_map).ok_or_else(|| {
                CampusError::NotFound(format!("Asset de mapa não embutido: '{}'.", entry.base_map))
            })?;
            let mut overlay_jsons = Vec::with_capacity(entry.indoor_overlays.len());
            for ov in &entry.indoor_overlays {
                let ov_json = assets::get_embedded_asset(&ov.file).ok_or_else(|| {
                    CampusError::NotFound(format!("Asset de overlay não embutido: '{}'.", ov.file))
                })?;
                overlay_jsons.push(ov_json);
            }
            slots.push(LazyCampusSlot::from_manifest(
                entry,
                base_json,
                overlay_jsons,
            ));
        }

        let occupancy = OccupancyIndex::from_json(OCCUPANCY_2026_2_JSON)?;
        let resolver = MeetingResolver::from_contract_json(MAP_SCHEMA_CONTRACT_JSON);
        Self::from_slots(slots, occupancy, resolver)
    }

    /// Carrega um mapa base GeoJSON aplicando overlays plug-and-play (usando ocupação embutida 2026/2).
    pub fn load_with_overlays(
        base_geojson: String,
        overlays_geojson: Vec<String>,
    ) -> Result<Self, CampusError> {
        Self::load_with_overlays_and_occupancy(base_geojson, overlays_geojson, None)
    }

    /// Carrega um mapa base GeoJSON com overlays e arquivo de ocupação customizado (ou embutido quando `None`).
    pub fn load_with_overlays_and_occupancy(
        base_geojson: String,
        overlays_geojson: Vec<String>,
        occupancy_json: Option<String>,
    ) -> Result<Self, CampusError> {
        let overlay_refs: Vec<&str> = overlays_geojson.iter().map(String::as_str).collect();
        let campus = load_campus_with_overlays(&base_geojson, &overlay_refs)?;
        let occupancy = match occupancy_json.as_deref() {
            Some(raw) if !raw.trim().is_empty() => OccupancyIndex::from_json(raw)?,
            Some(_) => OccupancyIndex::empty(),
            None => OccupancyIndex::from_json(OCCUPANCY_2026_2_JSON)?,
        };
        let resolver = MeetingResolver::from_contract_json(MAP_SCHEMA_CONTRACT_JSON);
        Self::from_slots(
            vec![LazyCampusSlot::from_loaded(campus)],
            occupancy,
            resolver,
        )
    }

    pub fn list_campuses(&self) -> Vec<CampusMapSummary> {
        self.campuses.iter().map(LazyCampusSlot::summary).collect()
    }

    pub fn get_active_campus_id(&self) -> String {
        let idx = self.current_campus_idx();
        self.campuses[idx].campus_id().to_string()
    }

    pub fn set_active_campus(&self, campus_id: String) -> Result<CampusMapSummary, CampusError> {
        let target = campus_id.trim();
        let idx = self
            .campuses
            .iter()
            .position(|slot| slot.campus_id().eq_ignore_ascii_case(target))
            .ok_or_else(|| {
                CampusError::NotFound(format!("Campus não carregado no mapa: '{campus_id}'."))
            })?;
        let entry = self.campuses[idx].get_or_load()?;
        if let Ok(mut guard) = self.active_campus_idx.write() {
            *guard = idx;
        }
        Ok(entry.campus.summary.clone())
    }

    pub fn get_feature(&self, feature_id: String) -> Option<FeatureInfo> {
        let target = feature_id.trim();
        for campus in self.loaded_campuses_active_first() {
            if let Some(found) = campus
                .features
                .iter()
                .find(|f| f.info.id.eq_ignore_ascii_case(target))
            {
                return Some(found.info.clone());
            }
        }
        for slot in &self.campuses {
            if slot.get_loaded().is_some() {
                continue;
            }
            if let Ok(entry) = slot.get_or_load()
                && let Some(found) = entry
                    .campus
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
        let entry = self.active_entry();
        entry
            .spatial
            .hit_test(&entry.campus.features, map_x, map_y, active_floor, zoom)
    }

    pub fn get_camera_focus(&self, feature_id: String) -> Option<CameraFocus> {
        let target = feature_id.trim();
        let loaded = self.loaded_campuses_active_first();
        if let Some(focus) = find_feature_focus(&loaded, target) {
            return Some(focus);
        }
        for slot in &self.campuses {
            if slot.get_loaded().is_some() {
                continue;
            }
            if let Ok(entry) = slot.get_or_load()
                && let Some(focus) = find_feature_focus(&[&entry.campus], target)
            {
                return Some(focus);
            }
        }
        None
    }

    pub fn search(
        &self,
        query: String,
        poi_type_filter: Option<PoiType>,
        active_floor: i32,
    ) -> Vec<SearchResult> {
        let entry = self.active_entry();
        search_features(
            &entry.campus.features,
            &query,
            poi_type_filter,
            active_floor,
        )
    }

    pub fn resolve_meeting(
        &self,
        course_code: String,
        section: String,
        weekday: u32,
        start_time: String,
        fallback_building_raw: String,
    ) -> Resolution {
        if let Some(matched_slot) = find_matching_slot_for_meeting(
            &self.occupancy,
            &course_code,
            &section,
            weekday,
            &start_time,
        ) {
            let _ = self.ensure_campus_loaded_by_id(&matched_slot.raw.campus_id);
            let loaded = self.loaded_campuses_active_first();
            if let Some(focus) = find_feature_focus(&loaded, &matched_slot.raw.room_id) {
                return Resolution::ExactRoom {
                    campus_id: focus.campus_id.clone(),
                    building_id: matched_slot.raw.building_id.clone(),
                    room_id: matched_slot.raw.room_id.clone(),
                    floor: focus.target_floor,
                    camera_focus: focus,
                };
            }
            if let Some(focus) = find_feature_focus(&loaded, &matched_slot.raw.building_id) {
                return Resolution::ExactRoom {
                    campus_id: matched_slot.raw.campus_id.clone(),
                    building_id: matched_slot.raw.building_id.clone(),
                    room_id: matched_slot.raw.room_id.clone(),
                    floor: focus.target_floor,
                    camera_focus: focus,
                };
            }
        }

        let loaded = self.loaded_campuses_active_first();
        if let Some(focus) = self
            .resolver
            .resolve_building_fallback(&loaded, &fallback_building_raw)
        {
            return Resolution::BuildingFallback {
                campus_id: focus.campus_id.clone(),
                building_id: focus.feature_id.clone(),
                camera_focus: focus,
            };
        }

        for slot in &self.campuses {
            if slot.get_loaded().is_some() {
                continue;
            }
            if let Ok(entry) = slot.get_or_load()
                && let Some(focus) = self
                    .resolver
                    .resolve_building_fallback(&[&entry.campus], &fallback_building_raw)
            {
                return Resolution::BuildingFallback {
                    campus_id: focus.campus_id.clone(),
                    building_id: focus.feature_id.clone(),
                    camera_focus: focus,
                };
            }
        }

        Resolution::Unmapped
    }

    pub fn get_room_occupancy(
        &self,
        room_id: String,
        timestamp_epoch_or_iso: String,
    ) -> Result<OccupancyStatus, CampusError> {
        let clean = room_id.trim();
        let mut found_feat = self
            .loaded_campuses_active_first()
            .into_iter()
            .flat_map(|c| c.features.iter())
            .find(|f| f.info.id.eq_ignore_ascii_case(clean));

        if found_feat.is_none()
            && let Some(target_campus) = self.occupancy.campus_for_room_or_building(clean)
            && let Some(entry) = self.ensure_campus_loaded_by_id(target_campus)
        {
            found_feat = entry
                .campus
                .features
                .iter()
                .find(|f| f.info.id.eq_ignore_ascii_case(clean));
        }

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
        let collect_rooms = |campus: &LoadedCampus| -> Vec<(String, String, i32, bool)> {
            campus
                .features
                .iter()
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
                .collect()
        };

        let mut rooms: Vec<(String, String, i32, bool)> = self
            .loaded_campuses_active_first()
            .into_iter()
            .flat_map(collect_rooms)
            .collect();

        if rooms.is_empty()
            && let Some(target_campus) = self.occupancy.campus_for_building(clean_bid)
            && let Some(entry) = self.ensure_campus_loaded_by_id(target_campus)
        {
            rooms = collect_rooms(&entry.campus);
        }

        rooms.sort_by(|a, b| a.2.cmp(&b.2).then_with(|| a.0.cmp(&b.0)));
        self.occupancy
            .list_building_occupancy(clean_bid, &rooms, &timestamp_epoch_or_iso)
    }

    pub fn get_render_batch(
        &self,
        active_floor: i32,
        timestamp_opt: Option<String>,
    ) -> Result<RenderBatch, CampusError> {
        let entry = self.active_entry();
        build_render_batch(
            &entry.campus,
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
