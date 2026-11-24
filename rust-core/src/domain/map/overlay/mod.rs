mod assembler;
mod parser;

use super::projection::{LocalProjector, ProjectedGeometry};
use super::types::{CampusMapSummary, FeatureInfo};

#[derive(Debug, Clone)]
pub struct LoadedFeature {
    pub info: FeatureInfo,
    pub geometry: ProjectedGeometry,
    pub area_m2: f64,
}

#[derive(Debug, Clone)]
pub struct LoadedCampus {
    pub summary: CampusMapSummary,
    pub projector: LocalProjector,
    pub features: Vec<LoadedFeature>,
}

pub use assembler::{load_campus_with_overlays, validate_overlay};
pub use parser::default_floor_label;
