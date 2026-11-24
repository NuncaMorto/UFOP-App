use crate::CampusError;

use super::occupancy::OccupancyIndex;
use super::overlay::LoadedCampus;
use super::projection::ProjectedGeometry;
use super::types::{
    MapLayer, OccupancyStatus, RenderBatch, RenderFeature, RenderGeomKind, RenderOccupancyKind,
};

pub fn build_render_batch(
    campus: &LoadedCampus,
    occupancy: &OccupancyIndex,
    active_floor: i32,
    timestamp_opt: Option<&str>,
) -> Result<RenderBatch, CampusError> {
    let mut active_indices: Vec<usize> = campus
        .features
        .iter()
        .enumerate()
        .filter(|(_, feat)| match feat.info.layer {
            MapLayer::Room | MapLayer::Poi => feat.info.floor.is_none_or(|f| f == active_floor),
            _ => true,
        })
        .map(|(idx, _)| idx)
        .collect();

    active_indices.sort_by_key(|&idx| {
        let feat = &campus.features[idx];
        (feat.info.layer.z_order(), feat.info.id.as_str())
    });

    let mut coords: Vec<f32> = Vec::new();
    let mut ring_starts: Vec<u32> = Vec::new();
    let mut ring_counts: Vec<u32> = Vec::new();
    let mut items: Vec<RenderFeature> = Vec::with_capacity(active_indices.len());

    for idx in active_indices {
        let feat = &campus.features[idx];
        let ring_start_idx = ring_starts.len() as u32;
        let mut ring_count = 0u32;
        let mut point_x = feat.info.label_x;
        let mut point_y = feat.info.label_y;

        let geom_kind = match &feat.geometry {
            ProjectedGeometry::Polygon(mp) => {
                for poly in &mp.0 {
                    for ring in std::iter::once(poly.exterior()).chain(poly.interiors().iter()) {
                        let start_vertex = (coords.len() / 2) as u32;
                        for c in &ring.0 {
                            coords.push(c.x as f32);
                            coords.push(c.y as f32);
                        }
                        let vertex_count = (coords.len() / 2) as u32 - start_vertex;
                        if vertex_count >= 3 {
                            ring_starts.push(start_vertex);
                            ring_counts.push(vertex_count);
                            ring_count += 1;
                        }
                    }
                }
                RenderGeomKind::Polygon
            }
            ProjectedGeometry::Polyline(lines) => {
                for line in lines {
                    let start_vertex = (coords.len() / 2) as u32;
                    for c in &line.0 {
                        coords.push(c.x as f32);
                        coords.push(c.y as f32);
                    }
                    let vertex_count = (coords.len() / 2) as u32 - start_vertex;
                    if vertex_count >= 2 {
                        ring_starts.push(start_vertex);
                        ring_counts.push(vertex_count);
                        ring_count += 1;
                    }
                }
                RenderGeomKind::Polyline
            }
            ProjectedGeometry::Point(pt) => {
                point_x = pt.x() as f32;
                point_y = pt.y() as f32;
                RenderGeomKind::Point
            }
        };

        let (occ_kind, occ_label) = match (feat.info.layer, timestamp_opt) {
            (MapLayer::Room, Some(ts)) => {
                let status = occupancy.get_room_occupancy(
                    &feat.info.id,
                    feat.info.building_id.as_deref(),
                    true,
                    ts,
                )?;
                match status {
                    OccupancyStatus::Occupied { slot } => {
                        (RenderOccupancyKind::Occupied, Some(slot.label))
                    }
                    OccupancyStatus::NoRegularClass => (RenderOccupancyKind::NoRegularClass, None),
                    OccupancyStatus::NoData => (RenderOccupancyKind::NoData, None),
                    OccupancyStatus::OutOfTerm => (RenderOccupancyKind::OutOfTerm, None),
                }
            }
            _ => (RenderOccupancyKind::None, None),
        };

        let primary_label = feat
            .info
            .code
            .clone()
            .unwrap_or_else(|| feat.info.name.clone());
        let secondary_label = if feat.info.code.is_some() {
            feat.info.name.clone()
        } else {
            String::new()
        };

        items.push(RenderFeature {
            id: feat.info.id.clone(),
            layer: feat.info.layer,
            geom_kind,
            ring_start_idx,
            ring_count,
            point_x,
            point_y,
            label_x: feat.info.label_x,
            label_y: feat.info.label_y,
            bbox_min_x: feat.info.bounds.min_x,
            bbox_min_y: feat.info.bounds.min_y,
            bbox_max_x: feat.info.bounds.max_x,
            bbox_max_y: feat.info.bounds.max_y,
            min_zoom: feat.info.min_zoom,
            primary_label,
            secondary_label,
            category: feat.info.category.clone().unwrap_or_default(),
            poi_type: feat.info.poi_type,
            indoor_status: feat.info.indoor_status,
            is_mock: feat.info.is_mock,
            occupancy: occ_kind,
            occupancy_label: occ_label,
        });
    }

    Ok(RenderBatch {
        campus_id: campus.summary.campus_id.clone(),
        campus_name: campus.summary.name.clone(),
        active_floor,
        available_floors: campus.summary.available_floors.clone(),
        floor_labels: campus.summary.floor_labels.clone(),
        bounds: campus.summary.bounds,
        attribution: campus.summary.attribution.clone(),
        coords,
        ring_starts,
        ring_counts,
        items,
    })
}
