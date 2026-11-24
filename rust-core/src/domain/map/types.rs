use serde::{Deserialize, Serialize};

/// Camadas vetoriais ordenadas de baixo para cima conforme `layer_z_order` do contrato.
#[derive(
    uniffi::Enum, Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord, Serialize, Deserialize,
)]
pub enum MapLayer {
    CampusGround,
    CampusZone,
    GreenArea,
    Parking,
    Sports,
    Walkway,
    Building,
    Room,
    Poi,
}

impl MapLayer {
    /// Ordem fixa de renderização (`0` = fundo do campus, `8` = POIs no topo).
    pub const fn z_order(self) -> u8 {
        match self {
            Self::CampusGround => 0,
            Self::CampusZone => 1,
            Self::GreenArea => 2,
            Self::Parking => 3,
            Self::Sports => 4,
            Self::Walkway => 5,
            Self::Building => 6,
            Self::Room => 7,
            Self::Poi => 8,
        }
    }

    pub fn from_geojson_str(raw: &str) -> Option<Self> {
        match raw.trim().to_ascii_lowercase().as_str() {
            "campus_ground" => Some(Self::CampusGround),
            "campus_zone" => Some(Self::CampusZone),
            "green_area" => Some(Self::GreenArea),
            "parking" => Some(Self::Parking),
            "sports" => Some(Self::Sports),
            "walkway" => Some(Self::Walkway),
            "building" => Some(Self::Building),
            "room" => Some(Self::Room),
            "poi" => Some(Self::Poi),
            _ => None,
        }
    }
}

/// Estado de digitalização interna de um prédio ou zona acadêmica.
#[derive(uniffi::Enum, Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum IndoorStatus {
    Floorplan,
    PartialFloorplan,
    Provisional,
    ExteriorOnly,
}

impl IndoorStatus {
    pub fn from_geojson_str(raw: &str) -> Option<Self> {
        match raw.trim().to_ascii_lowercase().as_str() {
            "floorplan" => Some(Self::Floorplan),
            "partial_floorplan" => Some(Self::PartialFloorplan),
            "provisional" => Some(Self::Provisional),
            "exterior_only" => Some(Self::ExteriorOnly),
            _ => None,
        }
    }
}

/// Tipos suportados para pontos de interesse (POIs) nos campi.
#[derive(uniffi::Enum, Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum PoiType {
    Toilet,
    FacultyOffice,
    Entrance,
    Dining,
    Library,
    Parking,
    Academic,
}

impl PoiType {
    pub fn from_geojson_str(raw: &str) -> Option<Self> {
        match raw.trim().to_ascii_lowercase().as_str() {
            "toilet" => Some(Self::Toilet),
            "faculty_office" => Some(Self::FacultyOffice),
            "entrance" => Some(Self::Entrance),
            "dining" => Some(Self::Dining),
            "library" => Some(Self::Library),
            "parking" => Some(Self::Parking),
            "academic" => Some(Self::Academic),
            _ => None,
        }
    }
}

/// Caixa delimitadora 2D em metros no sistema de projeção local do campus.
#[derive(uniffi::Record, Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct Rect2D {
    pub min_x: f32,
    pub min_y: f32,
    pub max_x: f32,
    pub max_y: f32,
}

/// Rótulo legível associado a um índice de andar (`0 -> "Térreo"`, `1 -> "2º Pav"`, etc.).
#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct FloorLabel {
    pub floor: i32,
    pub label: String,
}

/// Resumo de um campus carregado pelo motor vetorial.
#[derive(uniffi::Record, Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct CampusMapSummary {
    pub campus_id: String,
    pub name: String,
    pub city: String,
    pub default_floor: i32,
    pub available_floors: Vec<i32>,
    pub floor_labels: Vec<FloorLabel>,
    pub bounds: Rect2D,
    pub attribution: String,
}

/// Alvo de foco de câmera retornado por buscas, resoluções de aula ou seleção direta.
#[derive(uniffi::Record, Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct CameraFocus {
    pub campus_id: String,
    pub feature_id: String,
    pub target_floor: i32,
    pub bounds: Rect2D,
}

/// Metadados detalhados de uma feição do mapa (sem qualquer campo de capacidade).
#[derive(uniffi::Record, Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct FeatureInfo {
    pub id: String,
    pub campus_id: String,
    pub layer: MapLayer,
    pub code: Option<String>,
    pub name: String,
    pub category: Option<String>,
    pub building_id: Option<String>,
    pub parent_id: Option<String>,
    pub floor: Option<i32>,
    pub available_floors: Vec<i32>,
    pub floor_labels: Vec<FloorLabel>,
    pub default_floor: Option<i32>,
    pub poi_type: Option<PoiType>,
    pub indoor_status: IndoorStatus,
    pub is_mock: bool,
    pub aliases: Vec<String>,
    pub departments: Vec<String>,
    pub notes: Option<String>,
    pub min_zoom: f32,
    pub bounds: Rect2D,
    pub label_x: f32,
    pub label_y: f32,
}

/// Resultado de `hit_test` em coordenadas locais do mapa.
#[derive(uniffi::Record, Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct MapHit {
    pub feature: FeatureInfo,
    pub distance_m: f32,
}

/// Resultado de busca textual sobre prédios, salas e POIs.
#[derive(uniffi::Record, Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct SearchResult {
    pub feature: FeatureInfo,
    pub score: u32,
    pub matched_field: String,
}

/// Informações de aula/reserva ativa em um intervalo de ocupação.
#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SlotInfo {
    pub campus_id: String,
    pub building_id: String,
    pub room_id: String,
    pub room_code: String,
    pub weekday: u32,
    pub start_time: String,
    pub end_time: String,
    pub kind: String,
    pub label: String,
    pub title: Option<String>,
    pub course_codes: Vec<String>,
}

/// Estado de ocupação de uma sala em um instante no fuso `America/Sao_Paulo`.
#[derive(uniffi::Enum, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[allow(clippy::large_enum_variant)]
pub enum OccupancyStatus {
    Occupied { slot: SlotInfo },
    NoRegularClass,
    NoData,
    OutOfTerm,
}

/// Item consolidado para listagem de ocupação por prédio.
#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct RoomOccupancyItem {
    pub room_id: String,
    pub room_code: String,
    pub floor: i32,
    pub is_mock: bool,
    pub status: OccupancyStatus,
}

/// Resultado da resolução de encontro aula <-> mapa.
#[derive(uniffi::Enum, Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum Resolution {
    ExactRoom {
        campus_id: String,
        building_id: String,
        room_id: String,
        floor: i32,
        camera_focus: CameraFocus,
    },
    BuildingFallback {
        campus_id: String,
        building_id: String,
        camera_focus: CameraFocus,
    },
    Unmapped,
}

/// Tipo geométrico primitivo achatado no `RenderBatch`.
#[derive(uniffi::Enum, Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum RenderGeomKind {
    Polygon,
    Polyline,
    Point,
}

/// Estado resumido de ocupação para estilização rápida na UI sem alocações por frame.
#[derive(uniffi::Enum, Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum RenderOccupancyKind {
    None,
    Occupied,
    NoRegularClass,
    NoData,
    OutOfTerm,
}

/// Feição pronta para desenho direto em Canvas 2D nativo.
#[derive(uniffi::Record, Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct RenderFeature {
    pub id: String,
    pub layer: MapLayer,
    pub geom_kind: RenderGeomKind,
    pub ring_start_idx: u32,
    pub ring_count: u32,
    pub point_x: f32,
    pub point_y: f32,
    pub label_x: f32,
    pub label_y: f32,
    pub bbox_min_x: f32,
    pub bbox_min_y: f32,
    pub bbox_max_x: f32,
    pub bbox_max_y: f32,
    pub min_zoom: f32,
    pub primary_label: String,
    pub secondary_label: String,
    pub category: String,
    pub poi_type: Option<PoiType>,
    pub indoor_status: IndoorStatus,
    pub is_mock: bool,
    pub occupancy: RenderOccupancyKind,
    pub occupancy_label: Option<String>,
}

/// Lote completo de renderização de um andar sem FFI por frame durante pan/zoom.
#[derive(uniffi::Record, Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct RenderBatch {
    pub campus_id: String,
    pub campus_name: String,
    pub active_floor: i32,
    pub available_floors: Vec<i32>,
    pub floor_labels: Vec<FloorLabel>,
    pub bounds: Rect2D,
    pub attribution: String,
    pub coords: Vec<f32>,
    pub ring_starts: Vec<u32>,
    pub ring_counts: Vec<u32>,
    pub items: Vec<RenderFeature>,
}

/// Relatório emitido por `validate_overlay`.
#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct OverlayValidationReport {
    pub building_id: String,
    pub checked_features: u32,
    pub replaced_floors: Vec<i32>,
}
