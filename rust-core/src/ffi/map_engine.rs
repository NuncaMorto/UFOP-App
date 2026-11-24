use std::sync::Arc;

use crate::models::{
    CameraFocus, CampusMapSummary, FeatureInfo, MapHit, OccupancyStatus, OverlayValidationReport,
    PoiType, RenderBatch, Resolution, RoomOccupancyItem, SearchResult,
};
use crate::{CampusError, domain};

/// Motor vetorial offline 2D para os campi da UFOP e ocupação multi-campus 2026/2.
#[derive(uniffi::Object)]
pub struct CampusMapEngine {
    inner: domain::map::CampusMapEngine,
}

#[uniffi::export]
impl CampusMapEngine {
    /// Carrega todos os 4 campi, seus overlays declarados em `campus_manifest.json` e a ocupação 2026/2 embutida.
    #[uniffi::constructor]
    pub fn new_bundled() -> Result<Arc<Self>, CampusError> {
        Ok(Arc::new(Self {
            inner: domain::map::CampusMapEngine::new_bundled()?,
        }))
    }

    /// Carrega um mapa base GeoJSON aplicando overlays plug-and-play (usando ocupação embutida 2026/2).
    #[uniffi::constructor]
    pub fn load_with_overlays(
        base_geojson: String,
        overlays_geojson: Vec<String>,
    ) -> Result<Arc<Self>, CampusError> {
        Ok(Arc::new(Self {
            inner: domain::map::CampusMapEngine::load_with_overlays(
                base_geojson,
                overlays_geojson,
            )?,
        }))
    }

    /// Carrega um mapa base GeoJSON com overlays e arquivo de ocupação customizado (ou embutido quando `None`).
    #[uniffi::constructor]
    pub fn load_with_overlays_and_occupancy(
        base_geojson: String,
        overlays_geojson: Vec<String>,
        occupancy_json: Option<String>,
    ) -> Result<Arc<Self>, CampusError> {
        Ok(Arc::new(Self {
            inner: domain::map::CampusMapEngine::load_with_overlays_and_occupancy(
                base_geojson,
                overlays_geojson,
                occupancy_json,
            )?,
        }))
    }

    pub fn list_campuses(&self) -> Vec<CampusMapSummary> {
        self.inner.list_campuses()
    }

    pub fn get_active_campus_id(&self) -> String {
        self.inner.get_active_campus_id()
    }

    pub fn set_active_campus(&self, campus_id: String) -> Result<CampusMapSummary, CampusError> {
        self.inner.set_active_campus(campus_id)
    }

    pub fn get_feature(&self, feature_id: String) -> Option<FeatureInfo> {
        self.inner.get_feature(feature_id)
    }

    pub fn hit_test(&self, map_x: f32, map_y: f32, active_floor: i32, zoom: f32) -> Option<MapHit> {
        self.inner.hit_test(map_x, map_y, active_floor, zoom)
    }

    pub fn get_camera_focus(&self, feature_id: String) -> Option<CameraFocus> {
        self.inner.get_camera_focus(feature_id)
    }

    pub fn search(
        &self,
        query: String,
        poi_type_filter: Option<PoiType>,
        active_floor: i32,
    ) -> Vec<SearchResult> {
        self.inner.search(query, poi_type_filter, active_floor)
    }

    pub fn resolve_meeting(
        &self,
        course_code: String,
        section: String,
        weekday: u32,
        start_time: String,
        fallback_building_raw: String,
    ) -> Resolution {
        self.inner.resolve_meeting(
            course_code,
            section,
            weekday,
            start_time,
            fallback_building_raw,
        )
    }

    pub fn get_room_occupancy(
        &self,
        room_id: String,
        timestamp_epoch_or_iso: String,
    ) -> Result<OccupancyStatus, CampusError> {
        self.inner
            .get_room_occupancy(room_id, timestamp_epoch_or_iso)
    }

    pub fn list_building_occupancy(
        &self,
        building_id: String,
        timestamp_epoch_or_iso: String,
    ) -> Result<Vec<RoomOccupancyItem>, CampusError> {
        self.inner
            .list_building_occupancy(building_id, timestamp_epoch_or_iso)
    }

    pub fn get_render_batch(
        &self,
        active_floor: i32,
        timestamp_opt: Option<String>,
    ) -> Result<RenderBatch, CampusError> {
        self.inner.get_render_batch(active_floor, timestamp_opt)
    }
}

/// Valida um overlay GeoJSON contra um mapa base GeoJSON sem integrá-lo ao motor.
#[uniffi::export]
pub fn validate_overlay(
    base_geojson: String,
    overlay_geojson: String,
) -> Result<OverlayValidationReport, CampusError> {
    domain::map::validate_overlay(base_geojson, overlay_geojson)
}
