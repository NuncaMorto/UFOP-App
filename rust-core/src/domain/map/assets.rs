//! Fonte única de assets vetoriais e grade de ocupação embutidos no núcleo Rust.

/// Manifesto declarativo dos 4 campi e seus overlays internos.
pub const CAMPUS_MANIFEST_JSON: &str =
    include_str!("../../../assets/map_data/maps/campus_manifest.json");

/// Contrato de esquema e tabela oficial de aliases do Atestado UFOP.
pub const MAP_SCHEMA_CONTRACT_JSON: &str =
    include_str!("../../../assets/map_data/schema/map_schema_contract.json");

/// Base vetorial do Campus Ouro Preto — Morro do Cruzeiro.
pub const MORRO_DO_CRUZEIRO_GEOJSON: &str =
    include_str!("../../../assets/map_data/maps/morro_do_cruzeiro.geojson");

/// Base vetorial do Campus João Monlevade — ICEA.
pub const ICEA_GEOJSON: &str =
    include_str!("../../../assets/map_data/maps/icea_joao_monlevade.geojson");

/// Base vetorial do Campus Mariana — ICHS.
pub const ICHS_GEOJSON: &str = include_str!("../../../assets/map_data/maps/ichs_mariana.geojson");

/// Base vetorial do Campus Mariana — ICSA.
pub const ICSA_GEOJSON: &str = include_str!("../../../assets/map_data/maps/icsa_mariana.geojson");

/// Overlay indoor da Escola de Minas.
pub const OVERLAY_EM_GEOJSON: &str =
    include_str!("../../../assets/map_data/maps/indoor/EM.geojson");

/// Overlay indoor do ICEB.
pub const OVERLAY_ICEB_GEOJSON: &str =
    include_str!("../../../assets/map_data/maps/indoor/ICEB.geojson");

/// Overlay indoor do Bloco de Salas de Aula (BSA).
pub const OVERLAY_BSA_GEOJSON: &str =
    include_str!("../../../assets/map_data/maps/indoor/BSA.geojson");

/// Ocupação semestral real 2026/2 (Ouro Preto e João Monlevade).
pub const OCCUPANCY_2026_2_JSON: &str =
    include_str!("../../../assets/map_data/schedule/occupancy_2026_2.json");

/// Resolve um caminho relativo declarado em `campus_manifest.json` para o asset embutido.
pub fn get_embedded_asset(rel_path: &str) -> Option<&'static str> {
    let normalized = rel_path.trim().replace('\\', "/");
    let clean = normalized.strip_prefix("./").unwrap_or(&normalized);
    match clean {
        "maps/campus_manifest.json" => Some(CAMPUS_MANIFEST_JSON),
        "schema/map_schema_contract.json" => Some(MAP_SCHEMA_CONTRACT_JSON),
        "maps/morro_do_cruzeiro.geojson" => Some(MORRO_DO_CRUZEIRO_GEOJSON),
        "maps/icea_joao_monlevade.geojson" => Some(ICEA_GEOJSON),
        "maps/ichs_mariana.geojson" => Some(ICHS_GEOJSON),
        "maps/icsa_mariana.geojson" => Some(ICSA_GEOJSON),
        "maps/indoor/EM.geojson" => Some(OVERLAY_EM_GEOJSON),
        "maps/indoor/ICEB.geojson" => Some(OVERLAY_ICEB_GEOJSON),
        "maps/indoor/BSA.geojson" => Some(OVERLAY_BSA_GEOJSON),
        "schedule/occupancy_2026_2.json" => Some(OCCUPANCY_2026_2_JSON),
        _ => None,
    }
}
