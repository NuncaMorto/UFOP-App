use geo::{Coord, LineString, MultiPolygon, Polygon};

use super::projection::{ProjectedGeometry, point_to_segment_distance_sq};
use super::types::MapLayer;

/// Tolerância máxima de simplificação geométrica para camadas externas extensas (`<= 0.35m`).
pub const MAX_SIMPLIFY_TOLERANCE_M: f64 = 0.30;
const DEDUP_EPSILON_SQ: f64 = 1e-6;

/// Simplifica de forma conservadora apenas camadas externas extensas (`CampusGround`, `GreenArea`,
/// `Walkway`), preservando integralmente contornos de prédios (`Building`), salas (`Room`) e POIs.
pub fn simplify_geometry_for_layer(
    layer: MapLayer,
    geometry: ProjectedGeometry,
) -> ProjectedGeometry {
    if !matches!(
        layer,
        MapLayer::CampusGround | MapLayer::GreenArea | MapLayer::Walkway
    ) {
        return geometry;
    }

    let tol_sq = MAX_SIMPLIFY_TOLERANCE_M * MAX_SIMPLIFY_TOLERANCE_M;
    match geometry {
        ProjectedGeometry::Polyline(lines) => {
            let simplified = lines
                .into_iter()
                .map(|line| simplify_open_linestring(&line, tol_sq))
                .collect();
            ProjectedGeometry::Polyline(simplified)
        }
        ProjectedGeometry::Polygon(mp) => {
            let simplified =
                mp.0.into_iter()
                    .map(|poly| simplify_polygon(&poly, tol_sq))
                    .collect();
            ProjectedGeometry::Polygon(MultiPolygon::new(simplified))
        }
        other @ ProjectedGeometry::Point(_) => other,
    }
}

fn dedup_consecutive(coords: &[Coord<f64>]) -> Vec<Coord<f64>> {
    let mut out: Vec<Coord<f64>> = Vec::with_capacity(coords.len());
    for &c in coords {
        if let Some(&prev) = out.last() {
            let dx = c.x - prev.x;
            let dy = c.y - prev.y;
            if dx * dx + dy * dy <= DEDUP_EPSILON_SQ {
                continue;
            }
        }
        out.push(c);
    }
    out
}

fn rdp_open(points: &[Coord<f64>], tol_sq: f64) -> Vec<Coord<f64>> {
    let n = points.len();
    if n <= 2 {
        return points.to_vec();
    }

    let mut keep = vec![false; n];
    keep[0] = true;
    keep[n - 1] = true;

    let mut stack = Vec::with_capacity(32);
    stack.push((0usize, n - 1));

    while let Some((start, end)) = stack.pop() {
        if end <= start + 1 {
            continue;
        }
        let a = points[start];
        let b = points[end];
        let mut max_dist_sq = -1.0f64;
        let mut max_idx = start;

        for (idx, p) in points.iter().enumerate().take(end).skip(start + 1) {
            let d_sq = point_to_segment_distance_sq(p.x, p.y, a.x, a.y, b.x, b.y);
            if d_sq > max_dist_sq {
                max_dist_sq = d_sq;
                max_idx = idx;
            }
        }

        if max_dist_sq > tol_sq && max_idx > start && max_idx < end {
            keep[max_idx] = true;
            stack.push((start, max_idx));
            stack.push((max_idx, end));
        }
    }

    points
        .iter()
        .enumerate()
        .filter_map(|(i, &p)| if keep[i] { Some(p) } else { None })
        .collect()
}

fn simplify_open_linestring(line: &LineString<f64>, tol_sq: f64) -> LineString<f64> {
    let deduped = dedup_consecutive(&line.0);
    if deduped.len() < 2 {
        return line.clone();
    }
    let simplified = rdp_open(&deduped, tol_sq);
    if simplified.len() >= 2 {
        LineString::new(simplified)
    } else {
        LineString::new(deduped)
    }
}

fn simplify_closed_ring(ring: &LineString<f64>, tol_sq: f64) -> LineString<f64> {
    let mut open = dedup_consecutive(&ring.0);
    if open.len() > 1 {
        let first = open[0];
        let last = open[open.len() - 1];
        let dx = last.x - first.x;
        let dy = last.y - first.y;
        if dx * dx + dy * dy <= DEDUP_EPSILON_SQ {
            open.pop();
        }
    }

    if open.len() < 3 {
        return ring.clone();
    }

    if open.len() == 3 {
        open.push(open[0]);
        return LineString::new(open);
    }

    let p0 = open[0];
    let mut far_idx = 1usize;
    let mut far_dist_sq = -1.0f64;
    for (idx, p) in open.iter().enumerate().skip(1) {
        let dx = p.x - p0.x;
        let dy = p.y - p0.y;
        let d_sq = dx * dx + dy * dy;
        if d_sq > far_dist_sq {
            far_dist_sq = d_sq;
            far_idx = idx;
        }
    }

    let arc1 = rdp_open(&open[0..=far_idx], tol_sq);
    let mut arc2_input = Vec::with_capacity(open.len() - far_idx + 1);
    arc2_input.extend_from_slice(&open[far_idx..]);
    arc2_input.push(p0);
    let arc2 = rdp_open(&arc2_input, tol_sq);

    let mut combined = Vec::with_capacity(arc1.len() + arc2.len());
    combined.extend_from_slice(&arc1);
    if arc2.len() > 2 {
        combined.extend_from_slice(&arc2[1..arc2.len() - 1]);
    }

    if combined.len() < 3 {
        open.push(open[0]);
        return LineString::new(open);
    }

    combined.push(combined[0]);
    LineString::new(combined)
}

fn simplify_polygon(poly: &Polygon<f64>, tol_sq: f64) -> Polygon<f64> {
    let exterior = simplify_closed_ring(poly.exterior(), tol_sq);
    let interiors = poly
        .interiors()
        .iter()
        .map(|hole| simplify_closed_ring(hole, tol_sq))
        .collect();
    Polygon::new(exterior, interiors)
}
