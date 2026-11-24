use std::cmp::Ordering;

use rstar::{AABB, RTree, RTreeObject};

use super::overlay::LoadedFeature;
use super::types::{MapHit, MapLayer};

#[derive(Debug, Clone)]
struct SpatialEntry {
    feature_idx: usize,
    envelope: AABB<[f64; 2]>,
}

impl RTreeObject for SpatialEntry {
    type Envelope = AABB<[f64; 2]>;

    fn envelope(&self) -> Self::Envelope {
        self.envelope
    }
}

#[derive(Debug, Clone)]
pub struct CampusSpatialIndex {
    tree: RTree<SpatialEntry>,
}

impl CampusSpatialIndex {
    pub fn build(features: &[LoadedFeature]) -> Self {
        let entries: Vec<SpatialEntry> = features
            .iter()
            .enumerate()
            .filter(|(_, feat)| {
                matches!(
                    feat.info.layer,
                    MapLayer::Poi | MapLayer::Room | MapLayer::Building | MapLayer::CampusZone
                )
            })
            .map(|(idx, feat)| {
                let b = feat.info.bounds;
                SpatialEntry {
                    feature_idx: idx,
                    envelope: AABB::from_corners(
                        [f64::from(b.min_x), f64::from(b.min_y)],
                        [f64::from(b.max_x), f64::from(b.max_y)],
                    ),
                }
            })
            .collect();
        Self {
            tree: RTree::bulk_load(entries),
        }
    }

    /// Executa `hit_test` com prioridade estrita `Poi -> Room -> Building -> CampusZone`.
    pub fn hit_test(
        &self,
        features: &[LoadedFeature],
        map_x: f32,
        map_y: f32,
        active_floor: i32,
        zoom: f32,
    ) -> Option<MapHit> {
        let x = f64::from(map_x);
        let y = f64::from(map_y);
        let safe_zoom = zoom.max(0.5);
        // Raio de toque enxuto em metros para POIs pontuais (evita cobrir salas vizinhas).
        let poi_radius_m = f64::from((10.0 / safe_zoom).clamp(1.5, 2.5));
        let search_box = AABB::from_corners(
            [x - poi_radius_m, y - poi_radius_m],
            [x + poi_radius_m, y + poi_radius_m],
        );

        let mut best_poi: Option<(usize, f64)> = None;
        let mut best_room: Option<(usize, f64)> = None;
        let mut best_building: Option<(usize, f64)> = None;
        let mut best_zone: Option<(usize, f64)> = None;

        for entry in self.tree.locate_in_envelope_intersecting(search_box) {
            let feat = &features[entry.feature_idx];
            if zoom < feat.info.min_zoom {
                continue;
            }
            match feat.info.layer {
                MapLayer::Poi => {
                    if let Some(fl) = feat.info.floor
                        && fl != active_floor
                    {
                        continue;
                    }
                    let dist = match &feat.geometry {
                        super::projection::ProjectedGeometry::Point(pt) => {
                            let dx = x - pt.x();
                            let dy = y - pt.y();
                            (dx * dx + dy * dy).sqrt()
                        }
                        _ => {
                            if feat.geometry.contains_point(x, y) {
                                0.0
                            } else {
                                f64::INFINITY
                            }
                        }
                    };
                    if dist <= poi_radius_m
                        && best_poi.as_ref().is_none_or(|(_, best_d)| dist < *best_d)
                    {
                        best_poi = Some((entry.feature_idx, dist));
                    }
                }
                MapLayer::Room => {
                    if feat.info.floor != Some(active_floor) {
                        continue;
                    }
                    if feat.geometry.contains_point(x, y)
                        && best_room.as_ref().is_none_or(|(_, area)| {
                            feat.area_m2.partial_cmp(area) == Some(Ordering::Less)
                        })
                    {
                        best_room = Some((entry.feature_idx, feat.area_m2));
                    }
                }
                MapLayer::Building => {
                    if feat.geometry.contains_point(x, y)
                        && best_building.as_ref().is_none_or(|(_, area)| {
                            feat.area_m2.partial_cmp(area) == Some(Ordering::Less)
                        })
                    {
                        best_building = Some((entry.feature_idx, feat.area_m2));
                    }
                }
                MapLayer::CampusZone
                    if feat.geometry.contains_point(x, y)
                        && best_zone.as_ref().is_none_or(|(_, area)| {
                            feat.area_m2.partial_cmp(area) == Some(Ordering::Less)
                        }) =>
                {
                    best_zone = Some((entry.feature_idx, feat.area_m2));
                }
                _ => {}
            }
        }

        if let Some((idx, dist)) = best_poi {
            return Some(MapHit {
                feature: features[idx].info.clone(),
                distance_m: dist as f32,
            });
        }
        if let Some((idx, _)) = best_room {
            return Some(MapHit {
                feature: features[idx].info.clone(),
                distance_m: 0.0,
            });
        }
        if let Some((idx, _)) = best_building {
            return Some(MapHit {
                feature: features[idx].info.clone(),
                distance_m: 0.0,
            });
        }
        if let Some((idx, _)) = best_zone {
            return Some(MapHit {
                feature: features[idx].info.clone(),
                distance_m: 0.0,
            });
        }

        None
    }
}
