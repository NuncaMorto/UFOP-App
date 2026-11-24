use std::cmp::Ordering;
use std::collections::BinaryHeap;

use geo::{
    Area, BoundingRect, Centroid, Contains, Coord, InteriorPoint, LineString, MultiPolygon, Point,
    Polygon,
};

use super::types::Rect2D;

const METERS_PER_DEG_LAT: f64 = 110_540.0;
const METERS_PER_DEG_LON_AT_EQUATOR: f64 = 111_320.0;

/// Projetor planar local em metros com eixo Y apontando para o Sul (Y-down para Canvas).
#[derive(Debug, Clone, Copy)]
pub struct LocalProjector {
    origin_lat: f64,
    origin_lon: f64,
    cos_lat0: f64,
}

impl LocalProjector {
    pub fn new(origin_lat: f64, origin_lon: f64) -> Self {
        Self {
            origin_lat,
            origin_lon,
            cos_lat0: origin_lat.to_radians().cos(),
        }
    }

    #[inline]
    pub fn project(&self, lon: f64, lat: f64) -> (f64, f64) {
        let x = (lon - self.origin_lon) * METERS_PER_DEG_LON_AT_EQUATOR * self.cos_lat0;
        let y = -(lat - self.origin_lat) * METERS_PER_DEG_LAT;
        (x, y)
    }

    pub fn project_ring(&self, coords: &[geojson::Position]) -> Option<LineString<f64>> {
        let mut pts: Vec<Coord<f64>> = Vec::with_capacity(coords.len() + 1);
        for pair in coords {
            if pair.len() < 2 {
                continue;
            }
            let (x, y) = self.project(pair[0], pair[1]);
            pts.push(Coord { x, y });
        }
        if pts.len() < 3 {
            return None;
        }
        if pts.first() != pts.last()
            && let Some(first) = pts.first().copied()
        {
            pts.push(first);
        }
        Some(LineString::new(pts))
    }

    pub fn project_polygon(&self, rings: &[Vec<geojson::Position>]) -> Option<Polygon<f64>> {
        let exterior = self.project_ring(rings.first()?)?;
        let mut interiors = Vec::new();
        for hole_raw in rings.iter().skip(1) {
            if let Some(hole) = self.project_ring(hole_raw) {
                interiors.push(hole);
            }
        }
        Some(Polygon::new(exterior, interiors))
    }
}

/// Geometria projetada mantida em memória para hit-test, busca espacial e achatamento de render.
#[derive(Debug, Clone)]
pub enum ProjectedGeometry {
    Polygon(MultiPolygon<f64>),
    Polyline(Vec<LineString<f64>>),
    Point(Point<f64>),
}

impl ProjectedGeometry {
    pub fn bounds(&self) -> Rect2D {
        match self {
            Self::Polygon(mp) => mp
                .bounding_rect()
                .map(|r| Rect2D {
                    min_x: r.min().x as f32,
                    min_y: r.min().y as f32,
                    max_x: r.max().x as f32,
                    max_y: r.max().y as f32,
                })
                .unwrap_or(Rect2D {
                    min_x: 0.0,
                    min_y: 0.0,
                    max_x: 0.0,
                    max_y: 0.0,
                }),
            Self::Polyline(lines) => {
                let mut min_x = f64::INFINITY;
                let mut min_y = f64::INFINITY;
                let mut max_x = f64::NEG_INFINITY;
                let mut max_y = f64::NEG_INFINITY;
                for line in lines {
                    for c in &line.0 {
                        min_x = min_x.min(c.x);
                        min_y = min_y.min(c.y);
                        max_x = max_x.max(c.x);
                        max_y = max_y.max(c.y);
                    }
                }
                if min_x.is_finite() {
                    Rect2D {
                        min_x: min_x as f32,
                        min_y: min_y as f32,
                        max_x: max_x as f32,
                        max_y: max_y as f32,
                    }
                } else {
                    Rect2D {
                        min_x: 0.0,
                        min_y: 0.0,
                        max_x: 0.0,
                        max_y: 0.0,
                    }
                }
            }
            Self::Point(p) => Rect2D {
                min_x: p.x() as f32,
                min_y: p.y() as f32,
                max_x: p.x() as f32,
                max_y: p.y() as f32,
            },
        }
    }

    pub fn unsigned_area(&self) -> f64 {
        match self {
            Self::Polygon(mp) => mp.unsigned_area(),
            Self::Polyline(_) | Self::Point(_) => 0.0,
        }
    }

    pub fn contains_point(&self, x: f64, y: f64) -> bool {
        match self {
            Self::Polygon(mp) => mp.contains(&Point::new(x, y)),
            Self::Polyline(_) | Self::Point(_) => false,
        }
    }

    /// Calcula a melhor âncora interior para texto (`polylabel` em polígonos, ponto médio em vias).
    pub fn compute_label_anchor(&self) -> (f64, f64) {
        match self {
            Self::Point(p) => (p.x(), p.y()),
            Self::Polyline(lines) => {
                if let Some(first) = lines.first()
                    && !first.0.is_empty()
                {
                    let mid = first.0[first.0.len() / 2];
                    return (mid.x, mid.y);
                }
                (0.0, 0.0)
            }
            Self::Polygon(mp) => {
                let best_poly = mp.0.iter().max_by(|a, b| {
                    a.unsigned_area()
                        .partial_cmp(&b.unsigned_area())
                        .unwrap_or(Ordering::Equal)
                });
                match best_poly {
                    Some(poly) => compute_polylabel(poly, 0.25),
                    None => (0.0, 0.0),
                }
            }
        }
    }
}

/// Distância euclidiana de um ponto até um segmento `AB`.
#[inline]
pub fn point_to_segment_distance_sq(px: f64, py: f64, ax: f64, ay: f64, bx: f64, by: f64) -> f64 {
    let dx = bx - ax;
    let dy = by - ay;
    let len_sq = dx * dx + dy * dy;
    if len_sq <= f64::EPSILON {
        let ex = px - ax;
        let ey = py - ay;
        return ex * ex + ey * ey;
    }
    let t = (((px - ax) * dx + (py - ay) * dy) / len_sq).clamp(0.0, 1.0);
    let proj_x = ax + t * dx;
    let proj_y = ay + t * dy;
    let rx = px - proj_x;
    let ry = py - proj_y;
    rx * rx + ry * ry
}

/// Distância mínima em metros de `(px, py)` à borda de todos os anéis (ou `0.0` se contido).
pub fn distance_to_polygon_outline(px: f64, py: f64, poly: &Polygon<f64>) -> f64 {
    let mut min_sq = f64::INFINITY;
    for ring in std::iter::once(poly.exterior()).chain(poly.interiors().iter()) {
        for seg in ring.0.windows(2) {
            let d = point_to_segment_distance_sq(px, py, seg[0].x, seg[0].y, seg[1].x, seg[1].y);
            if d < min_sq {
                min_sq = d;
            }
        }
    }
    if min_sq.is_finite() {
        min_sq.sqrt()
    } else {
        f64::INFINITY
    }
}

/// Distância assinada em metros ao polígono (positiva dentro, negativa fora/em holes).
fn signed_distance_to_polygon(px: f64, py: f64, poly: &Polygon<f64>) -> f64 {
    let dist = distance_to_polygon_outline(px, py, poly);
    if poly.contains(&Point::new(px, py)) {
        dist
    } else {
        -dist
    }
}

#[derive(Clone, Copy)]
struct PolyCell {
    cx: f64,
    cy: f64,
    half: f64,
    dist: f64,
    max_dist: f64,
}

impl PartialEq for PolyCell {
    fn eq(&self, other: &Self) -> bool {
        self.max_dist == other.max_dist
    }
}

impl Eq for PolyCell {}

impl PartialOrd for PolyCell {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for PolyCell {
    fn cmp(&self, other: &Self) -> Ordering {
        self.max_dist
            .partial_cmp(&other.max_dist)
            .unwrap_or(Ordering::Equal)
    }
}

impl PolyCell {
    fn new(cx: f64, cy: f64, half: f64, poly: &Polygon<f64>) -> Self {
        let dist = signed_distance_to_polygon(cx, cy, poly);
        let max_dist = dist + half * std::f64::consts::SQRT_2;
        Self {
            cx,
            cy,
            half,
            dist,
            max_dist,
        }
    }
}

/// Calcula o Polo de Inacessibilidade (`polylabel`) respeitando holes internos do polígono.
pub fn compute_polylabel(poly: &Polygon<f64>, precision: f64) -> (f64, f64) {
    let Some(rect) = poly.bounding_rect() else {
        return (0.0, 0.0);
    };
    let min_x = rect.min().x;
    let min_y = rect.min().y;
    let max_x = rect.max().x;
    let max_y = rect.max().y;
    let width = max_x - min_x;
    let height = max_y - min_y;
    let cell_size = width.min(height).max(0.05);

    if width <= f64::EPSILON || height <= f64::EPSILON {
        return ((min_x + max_x) * 0.5, (min_y + max_y) * 0.5);
    }

    let mut heap = BinaryHeap::new();
    let half = cell_size * 0.5;
    let mut x = min_x;
    while x < max_x {
        let mut y = min_y;
        while y < max_y {
            heap.push(PolyCell::new(x + half, y + half, half, poly));
            y += cell_size;
        }
        x += cell_size;
    }

    // Seed inicial pelo centro da bounding box e pelos pontos médios de cada anel externo.
    let mut best = PolyCell::new((min_x + max_x) * 0.5, (min_y + max_y) * 0.5, 0.0, poly);
    if !poly.exterior().0.is_empty() {
        let coords = &poly.exterior().0;
        let n = coords.len() as f64;
        let avg_x = coords.iter().map(|c| c.x).sum::<f64>() / n;
        let avg_y = coords.iter().map(|c| c.y).sum::<f64>() / n;
        let cand = PolyCell::new(avg_x, avg_y, 0.0, poly);
        if cand.dist > best.dist {
            best = cand;
        }
    }

    let mut iterations = 0usize;
    while let Some(cell) = heap.pop() {
        if cell.dist > best.dist {
            best = cell;
        }
        if cell.max_dist - best.dist <= precision || iterations > 4096 {
            break;
        }
        iterations += 1;
        let h = cell.half * 0.5;
        if h <= precision * 0.25 {
            continue;
        }
        heap.push(PolyCell::new(cell.cx - h, cell.cy - h, h, poly));
        heap.push(PolyCell::new(cell.cx + h, cell.cy - h, h, poly));
        heap.push(PolyCell::new(cell.cx - h, cell.cy + h, h, poly));
        heap.push(PolyCell::new(cell.cx + h, cell.cy + h, h, poly));
    }

    if best.dist < 0.0
        && let Some(fallback_pt) = poly.interior_point().or_else(|| poly.centroid())
    {
        return (fallback_pt.x(), fallback_pt.y());
    }

    (best.cx, best.cy)
}

/// Converte uma `geojson::Geometry` para `ProjectedGeometry` preservando holes e multipolígonos.
pub fn project_geojson_geometry(
    geom: &geojson::Geometry,
    projector: &LocalProjector,
) -> Option<ProjectedGeometry> {
    match &geom.value {
        geojson::GeometryValue::Point {
            coordinates: coords,
        } => {
            if coords.len() < 2 {
                return None;
            }
            let (x, y) = projector.project(coords[0], coords[1]);
            Some(ProjectedGeometry::Point(Point::new(x, y)))
        }
        geojson::GeometryValue::LineString {
            coordinates: coords,
        } => projector
            .project_ring_open(coords)
            .map(|line| ProjectedGeometry::Polyline(vec![line])),
        geojson::GeometryValue::MultiLineString {
            coordinates: lines_raw,
        } => {
            let lines: Vec<_> = lines_raw
                .iter()
                .filter_map(|raw| projector.project_ring_open(raw))
                .collect();
            if lines.is_empty() {
                None
            } else {
                Some(ProjectedGeometry::Polyline(lines))
            }
        }
        geojson::GeometryValue::Polygon { coordinates: rings } => projector
            .project_polygon(rings)
            .map(|p| ProjectedGeometry::Polygon(MultiPolygon::new(vec![p]))),
        geojson::GeometryValue::MultiPolygon {
            coordinates: polys_raw,
        } => {
            let polys: Vec<_> = polys_raw
                .iter()
                .filter_map(|rings| projector.project_polygon(rings))
                .collect();
            if polys.is_empty() {
                None
            } else {
                Some(ProjectedGeometry::Polygon(MultiPolygon::new(polys)))
            }
        }
        _ => None,
    }
}

impl LocalProjector {
    fn project_ring_open(&self, coords: &[geojson::Position]) -> Option<LineString<f64>> {
        let pts: Vec<Coord<f64>> = coords
            .iter()
            .filter(|pair| pair.len() >= 2)
            .map(|pair| {
                let (x, y) = self.project(pair[0], pair[1]);
                Coord { x, y }
            })
            .collect();
        if pts.len() < 2 {
            None
        } else {
            Some(LineString::new(pts))
        }
    }
}
