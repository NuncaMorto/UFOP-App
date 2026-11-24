use std::collections::HashSet;

use serde::Deserialize;

use super::assets::{
    MORRO_DO_CRUZEIRO_GEOJSON, OVERLAY_BSA_GEOJSON, OVERLAY_EM_GEOJSON, OVERLAY_ICEB_GEOJSON,
};
use super::occupancy::expand_section_tokens;
use super::types::{
    IndoorStatus, MapLayer, OccupancyStatus, PoiType, RenderOccupancyKind, Resolution,
};
use super::{CampusMapEngine, validate_overlay};

const TEST_RESOLUTIONS_JSON: &str =
    include_str!("../../../assets/map_data/schema/test_schedule_resolutions_2026_2.json");

#[derive(Debug, Deserialize)]
struct TestResolutionDoc {
    test_cases_expected_resolutions: Vec<TestResolutionCase>,
}

#[derive(Debug, Deserialize)]
struct TestResolutionCase {
    course_code: String,
    section: String,
    atestado_building: String,
    weekday: u32,
    start: String,
    expected_building: String,
    expected_room_id: String,
    expected_floor: i32,
}

#[test]
fn loads_all_four_campuses_and_overlays_without_duplicates() {
    let engine = CampusMapEngine::new_bundled().expect("bundled map engine should load");
    let campuses = engine.list_campuses();
    let ids: Vec<&str> = campuses.iter().map(|c| c.campus_id.as_str()).collect();
    assert_eq!(ids, vec!["OP_MC", "JM_ICEA", "MA_ICHS", "MA_ICSA"]);

    // Verifica que todas as feições do campus ativo têm IDs únicos após merge dos overlays.
    let batch_floor1 = engine
        .get_render_batch(1, Some("2026-11-17T21:15:00-03:00".to_string()))
        .expect("render batch floor 1");
    let mut seen = HashSet::new();
    for item in &batch_floor1.items {
        assert!(
            seen.insert(item.id.clone()),
            "ID duplicado no render batch: {}",
            item.id
        );
    }
    assert_eq!(batch_floor1.floor_labels.len(), 2);

    // Verifica indoor_status calculado dinamicamente.
    let em = engine.get_feature("EM".to_string()).expect("EM feature");
    assert_eq!(em.indoor_status, IndoorStatus::PartialFloorplan);

    let iceb = engine
        .get_feature("ICEB".to_string())
        .expect("ICEB feature");
    assert_eq!(iceb.indoor_status, IndoorStatus::Provisional);

    let bsa = engine.get_feature("BSA".to_string()).expect("BSA feature");
    assert_eq!(bsa.indoor_status, IndoorStatus::Provisional);

    let efar = engine
        .get_feature("EFAR".to_string())
        .expect("EFAR feature");
    assert_eq!(efar.indoor_status, IndoorStatus::ExteriorOnly);

    // Salas do 2º Pav da EM são reais; do 1º Pav da EM são provisórias.
    let em_11 = engine.get_feature("EM_11".to_string()).expect("EM_11");
    assert!(!em_11.is_mock);
    assert_eq!(em_11.floor, Some(1));

    let em_04 = engine.get_feature("EM_04".to_string()).expect("EM_04");
    assert!(em_04.is_mock);
    assert_eq!(em_04.floor, Some(0));

    // Ocupação no batch de EM_11 às 21:15 de terça (CAT163-T21/22).
    let em_11_render = batch_floor1
        .items
        .iter()
        .find(|i| i.id == "EM_11")
        .expect("EM_11 in floor 1 batch");
    assert_eq!(em_11_render.occupancy, RenderOccupancyKind::Occupied);
    assert!(
        em_11_render
            .occupancy_label
            .as_deref()
            .is_some_and(|l| l.contains("CAT163"))
    );

    // #14: Verifica preservação de holes (EM com 1 exterior + 2 pátios = 3 anéis) e eixo Y invertido.
    let em_render = batch_floor1
        .items
        .iter()
        .find(|i| i.id == "EM")
        .expect("EM in floor 1 batch");
    assert_eq!(em_render.ring_count, 3);
    assert!(
        em.label_y > 0.0,
        "EM ao sul da origem deve ter Y positivo para Canvas"
    );
    assert!(
        iceb.label_y < 0.0,
        "ICEB ao norte da origem deve ter Y negativo para Canvas"
    );

    // Trocar campus para JM_ICEA e validar andares e sala ICEA_H102.
    let jm_summary = engine
        .set_active_campus("JM_ICEA".to_string())
        .expect("switch to JM_ICEA");
    assert_eq!(jm_summary.available_floors, vec![0, 1, 2, 3]);
    let icea_h102 = engine
        .get_feature("ICEA_H102".to_string())
        .expect("ICEA_H102");
    assert_eq!(icea_h102.floor, Some(1));
    assert!(icea_h102.is_mock);
}

#[test]
fn plug_and_play_overlay_replaces_mock_floor_without_code_changes() {
    // Simula adição futura de planta real para o 1º andar (floor 0) da EM.
    let custom_em_floor0_overlay = r#"{
      "type": "FeatureCollection",
      "metadata": {
        "schema_version": "2.0.0",
        "campus_id": "OP_MC",
        "building_id": "EM",
        "replaces_mock_floors": [0]
      },
      "features": [
        {
          "type": "Feature",
          "id": "EM_04",
          "properties": {
            "id": "EM_04",
            "layer": "room",
            "parent_id": "EM",
            "building_id": "EM",
            "code": "04",
            "name": "Sala 04 — Escola de Minas",
            "category": "classroom",
            "floor": 0,
            "min_zoom": 1.8,
            "is_mock": false
          },
          "geometry": {
            "type": "Polygon",
            "coordinates": [[
              [-43.509149, -20.398496],
              [-43.509149, -20.398421],
              [-43.509225, -20.398421],
              [-43.509225, -20.398496],
              [-43.509149, -20.398496]
            ]]
          }
        }
      ]
    }"#;

    let engine = CampusMapEngine::load_with_overlays(
        MORRO_DO_CRUZEIRO_GEOJSON.to_string(),
        vec![
            OVERLAY_EM_GEOJSON.to_string(),
            custom_em_floor0_overlay.to_string(),
        ],
    )
    .expect("load_with_overlays");

    // EM_04 agora deve ser real (is_mock = false), EM_01 (do mock antigo do floor 0) foi removida e EM passa a Floorplan.
    let em_04 = engine.get_feature("EM_04".to_string()).expect("EM_04");
    assert!(!em_04.is_mock);
    assert_eq!(em_04.indoor_status, IndoorStatus::Floorplan);
    assert!(engine.get_feature("EM_01".to_string()).is_none());

    let em = engine.get_feature("EM".to_string()).expect("EM");
    assert_eq!(em.indoor_status, IndoorStatus::Floorplan);
}

#[test]
fn validates_all_11_real_schedule_resolutions_and_fallbacks() {
    let engine = CampusMapEngine::new_bundled().expect("bundled engine");
    let doc: TestResolutionDoc =
        serde_json::from_str(TEST_RESOLUTIONS_JSON).expect("parse test resolutions");
    assert_eq!(doc.test_cases_expected_resolutions.len(), 11);

    for case in &doc.test_cases_expected_resolutions {
        let res = engine.resolve_meeting(
            case.course_code.clone(),
            case.section.clone(),
            case.weekday,
            case.start.clone(),
            case.atestado_building.clone(),
        );
        match res {
            Resolution::ExactRoom {
                building_id,
                room_id,
                floor,
                camera_focus,
                ..
            } => {
                assert_eq!(
                    building_id, case.expected_building,
                    "building mismatch for {}-{}",
                    case.course_code, case.section
                );
                assert_eq!(
                    room_id, case.expected_room_id,
                    "room_id mismatch for {}-{}",
                    case.course_code, case.section
                );
                assert_eq!(
                    floor, case.expected_floor,
                    "floor mismatch for {}-{}",
                    case.course_code, case.section
                );
                assert_eq!(camera_focus.target_floor, case.expected_floor);
                assert_eq!(camera_focus.feature_id, case.expected_room_id);
            }
            other => panic!(
                "Expected ExactRoom for {}-{} (weekday={} {}), got {:?}",
                case.course_code, case.section, case.weekday, case.start, other
            ),
        }
    }

    // Fallback por alias do Atestado quando não houver alocação na grade.
    let fallback = engine.resolve_meeting(
        "FAR999".to_string(),
        "11".to_string(),
        2,
        "08:20".to_string(),
        "Escola de Farmácia".to_string(),
    );
    match fallback {
        Resolution::BuildingFallback {
            campus_id,
            building_id,
            camera_focus,
        } => {
            assert_eq!(campus_id, "OP_MC");
            assert_eq!(building_id, "EFAR");
            assert_eq!(camera_focus.feature_id, "EFAR");
        }
        other => panic!("Expected BuildingFallback to EFAR, got {other:?}"),
    }

    // Caso desconhecido -> Unmapped.
    let unmapped = engine.resolve_meeting(
        "XXX999".to_string(),
        "11".to_string(),
        2,
        "08:20".to_string(),
        "Prédio Inexistente".to_string(),
    );
    assert_eq!(unmapped, Resolution::Unmapped);

    // #15: Expansão de turmas agrupadas ("21/22", "T21|22", "41 a 44", "31a33").
    let expanded = expand_section_tokens(&[
        "21/22".to_string(),
        "T21|22".to_string(),
        "41 a 44".to_string(),
        "31a33".to_string(),
    ]);
    assert!(expanded.contains(&"41".to_string()));
    assert!(expanded.contains(&"42".to_string()));
    assert!(expanded.contains(&"43".to_string()));
    assert!(expanded.contains(&"44".to_string()));
    assert!(expanded.contains(&"31".to_string()));
    assert!(expanded.contains(&"32".to_string()));
    assert!(expanded.contains(&"33".to_string()));

    // Múltiplas disciplinas na mesma célula (CBI197 e EFD131 quarta 19:00 -> BSA_103).
    match engine.resolve_meeting(
        "EFD131".to_string(),
        "".to_string(),
        3,
        "19:00".to_string(),
        "BSA".to_string(),
    ) {
        Resolution::ExactRoom { room_id, .. } => assert_eq!(room_id, "BSA_103"),
        other => panic!("Expected BSA_103 for EFD131, got {other:?}"),
    }

    // Aliases do Atestado: ESCOLA DE MINAS -> EM, PAVILHAO DE AULAS -> BSA, CEDUFOP -> EEF.
    for (raw_alias, expected_bid) in [
        ("ESCOLA DE MINAS", "EM"),
        ("PAVILHAO DE AULAS", "BSA"),
        ("CEDUFOP", "EEF"),
    ] {
        match engine.resolve_meeting(
            "ZZZ999".to_string(),
            "11".to_string(),
            2,
            "08:20".to_string(),
            raw_alias.to_string(),
        ) {
            Resolution::BuildingFallback { building_id, .. } => {
                assert_eq!(building_id, expected_bid);
            }
            other => {
                panic!("Expected BuildingFallback {expected_bid} for '{raw_alias}', got {other:?}")
            }
        }
    }
}

#[test]
fn hit_test_handles_real_em_rooms_bibem_courtyard_holes_and_bsa() {
    let engine = CampusMapEngine::new_bundled().expect("bundled engine");

    // 1. EM_11 (2º Pav real, floor = 1).
    let em_11 = engine.get_feature("EM_11".to_string()).expect("EM_11");
    let hit_11 = engine
        .hit_test(em_11.label_x, em_11.label_y, 1, 3.0)
        .expect("hit EM_11");
    assert_eq!(hit_11.feature.id, "EM_11");
    assert!(!hit_11.feature.is_mock);

    // 2. EM_BIBEM em ponto dentro do polígono longe do pino POI_EM_1_BIBEM.
    let bibem = engine
        .get_feature("EM_BIBEM".to_string())
        .expect("EM_BIBEM");
    let hit_bibem = engine
        .hit_test(bibem.label_x, bibem.bounds.min_y + 4.0, 1, 3.0)
        .expect("hit EM_BIBEM");
    assert_eq!(hit_bibem.feature.id, "EM_BIBEM");
    assert!(!hit_bibem.feature.is_mock);

    // 3. Prioridade POI -> Room sobre o pino da BIBEM.
    let poi_bibem = engine
        .get_feature("POI_EM_1_BIBEM".to_string())
        .expect("POI_EM_1_BIBEM");
    let hit_poi = engine
        .hit_test(poi_bibem.label_x, poi_bibem.label_y, 1, 3.0)
        .expect("hit POI_EM_1_BIBEM");
    assert_eq!(hit_poi.feature.id, "POI_EM_1_BIBEM");
    assert_eq!(hit_poi.feature.layer, MapLayer::Poi);

    // 4. Pátio interno leste da EM (hole): NÃO deve dar hit em sala nem no prédio EM.
    assert!(
        engine.hit_test(71.6, 165.6, 1, 3.0).is_none(),
        "pátio interno leste da EM não pode retornar hit"
    );
    assert!(
        engine.hit_test(-20.0, 166.0, 1, 3.0).is_none(),
        "pátio interno oeste da EM não pode retornar hit"
    );

    // 5. BSA_205 (2º andar provisório).
    let bsa_205 = engine.get_feature("BSA_205".to_string()).expect("BSA_205");
    let hit_bsa = engine
        .hit_test(bsa_205.label_x, bsa_205.label_y, 1, 3.0)
        .expect("hit BSA_205");
    assert_eq!(hit_bsa.feature.id, "BSA_205");
    assert!(hit_bsa.feature.is_mock);

    // 6. #20: Filtro de min_zoom (zoom = 0.5 < 1.8 ignora EM_11 e acerta o prédio EM) + CameraFocus.
    let hit_zoom_out = engine
        .hit_test(em_11.label_x, em_11.label_y, 1, 0.5)
        .expect("hit building EM when zoomed out");
    assert_eq!(hit_zoom_out.feature.id, "EM");
    assert_eq!(hit_zoom_out.feature.layer, MapLayer::Building);

    let focus = engine
        .get_camera_focus("EM_11".to_string())
        .expect("camera focus EM_11");
    assert_eq!(focus.target_floor, 1);
    assert!(focus.bounds.max_x > focus.bounds.min_x);
    assert!(focus.bounds.max_y > focus.bounds.min_y);
}

#[test]
fn room_occupancy_covers_op_jm_free_nodata_and_out_of_term() {
    let engine = CampusMapEngine::new_bundled().expect("bundled engine");

    // 1. Sala ocupada em Ouro Preto (EM_11, terça 2026-11-17 21:15 -03:00 -> CAT163-T21/22 21:00..21:50).
    let op_occ = engine
        .get_room_occupancy("EM_11".to_string(), "2026-11-17T21:15:00-03:00".to_string())
        .expect("OP occupancy");
    match op_occ {
        OccupancyStatus::Occupied { slot } => {
            assert_eq!(slot.room_id, "EM_11");
            assert!(slot.course_codes.contains(&"CAT163".to_string()));
        }
        other => panic!("Expected Occupied for EM_11, got {other:?}"),
    }

    // Mesma consulta via epoch UTC em segundos (2026-11-17 21:15 -03:00 == 2026-11-18T00:15:00Z = 1794960900).
    let op_epoch = engine
        .get_room_occupancy("EM_11".to_string(), "1794960900".to_string())
        .expect("OP occupancy epoch");
    assert!(matches!(op_epoch, OccupancyStatus::Occupied { .. }));

    // 2. Sala ocupada em João Monlevade (ICEA_C103, segunda 2026-11-16 16:00 -03:00 -> bloco 15:25-17:05).
    let jm_occ = engine
        .get_room_occupancy(
            "ICEA_C103".to_string(),
            "2026-11-16T16:00:00-03:00".to_string(),
        )
        .expect("JM occupancy");
    match jm_occ {
        OccupancyStatus::Occupied { slot } => {
            assert_eq!(slot.room_id, "ICEA_C103");
            assert_eq!(slot.start_time, "15:25");
            assert_eq!(slot.end_time, "17:05");
            assert!(slot.course_codes.contains(&"ELT562".to_string()));
        }
        other => panic!("Expected Occupied for ICEA_C103, got {other:?}"),
    }

    // 3. Sala sem aula regular no horário de almoço em dia coberto.
    let op_free = engine
        .get_room_occupancy("EM_11".to_string(), "2026-11-17T12:15:00-03:00".to_string())
        .expect("EM_11 lunch");
    assert_eq!(op_free, OccupancyStatus::NoRegularClass);

    // 4. Sábado no BSA (BSA só cobre seg-sex) -> obrigatoriamente NoData.
    let bsa_sat = engine
        .get_room_occupancy(
            "BSA_205".to_string(),
            "2026-11-21T10:00:00-03:00".to_string(),
        )
        .expect("BSA saturday");
    assert_eq!(bsa_sat, OccupancyStatus::NoData);

    // 5. Prédio sem grade importada (EFAR / EMED) em dia útil -> NoData.
    let efar_tue = engine
        .get_room_occupancy(
            "EFAR_01".to_string(),
            "2026-11-17T10:00:00-03:00".to_string(),
        )
        .expect("EFAR tuesday");
    assert_eq!(efar_tue, OccupancyStatus::NoData);

    // 6. Feriado (2026-11-20 Consciência Negra), domingo (2026-11-22) e fora do período letivo -> OutOfTerm.
    assert_eq!(
        engine
            .get_room_occupancy("EM_11".to_string(), "2026-11-20T14:00:00-03:00".to_string())
            .expect("holiday"),
        OccupancyStatus::OutOfTerm
    );
    assert_eq!(
        engine
            .get_room_occupancy("EM_11".to_string(), "2026-11-22T14:00:00-03:00".to_string())
            .expect("sunday"),
        OccupancyStatus::OutOfTerm
    );
    assert_eq!(
        engine
            .get_room_occupancy("EM_11".to_string(), "2026-08-10T14:00:00-03:00".to_string())
            .expect("before term"),
        OccupancyStatus::OutOfTerm
    );
    assert_eq!(
        engine
            .get_room_occupancy("EM_11".to_string(), "2026-12-28T14:00:00-03:00".to_string())
            .expect("after term"),
        OccupancyStatus::OutOfTerm
    );

    // 7. list_building_occupancy em BSA terça às 15:30 deve incluir BSA_205 como Occupied (BCC721-11).
    let bsa_list = engine
        .list_building_occupancy("BSA".to_string(), "2026-11-17T15:30:00-03:00".to_string())
        .expect("list BSA occupancy");
    assert_eq!(bsa_list.len(), 20);
    let item_205 = bsa_list
        .iter()
        .find(|i| i.room_id == "BSA_205")
        .expect("BSA_205 in building list");
    assert!(matches!(item_205.status, OccupancyStatus::Occupied { .. }));

    // 8. #16: Reserva institucional (BSA_102 quarta 13:30-14:20 -> kind="reservation", label="EFD").
    let reservation = engine
        .get_room_occupancy(
            "BSA_102".to_string(),
            "2026-11-18T13:45:00-03:00".to_string(),
        )
        .expect("reservation occupancy");
    match reservation {
        OccupancyStatus::Occupied { slot } => {
            assert_eq!(slot.kind, "reservation");
            assert_eq!(slot.label, "EFD");
        }
        other => panic!("Expected Occupied reservation EFD for BSA_102, got {other:?}"),
    }
}

#[test]
fn search_supports_accents_aliases_and_poi_filters() {
    let engine = CampusMapEngine::new_bundled().expect("bundled engine");
    let libs = engine.search("biblioteca".to_string(), Some(PoiType::Library), 1);
    assert!(
        libs.iter()
            .any(|r| r.feature.id == "EM_BIBEM" || r.feature.id == "POI_EM_1_BIBEM"),
        "search for 'biblioteca' with Library filter should match BIBEM"
    );

    let cg_labs = engine.search("computacao".to_string(), None, 1);
    assert!(
        cg_labs.iter().any(|r| r.feature.id == "EM_LAB_DEARQ_1"),
        "search for 'computacao' without diacritics should match EM_LAB_DEARQ_1"
    );

    let depts = engine.search("decom".to_string(), None, 0);
    assert!(
        depts.iter().any(|r| r.feature.id == "ICEB"),
        "search for 'decom' should match ICEB via departments/aliases"
    );
}

#[test]
fn validate_overlay_accepts_official_overlays_and_rejects_broken_ones() {
    for ov in [
        OVERLAY_EM_GEOJSON,
        OVERLAY_ICEB_GEOJSON,
        OVERLAY_BSA_GEOJSON,
    ] {
        let rep = validate_overlay(MORRO_DO_CRUZEIRO_GEOJSON.to_string(), ov.to_string())
            .expect("official overlay must pass validation");
        assert!(rep.checked_features > 0);
    }

    // Rejeita ID duplicado.
    let duplicate_id_overlay = r#"{
      "type": "FeatureCollection",
      "metadata": {
        "schema_version": "2.0.0",
        "campus_id": "OP_MC",
        "building_id": "BSA",
        "replaces_mock_floors": [0]
      },
      "features": [
        {
          "type": "Feature",
          "id": "BSA_101",
          "properties": { "id": "BSA_101", "layer": "room", "building_id": "BSA", "code": "101", "floor": 0, "is_mock": false },
          "geometry": { "type": "Polygon", "coordinates": [[[-43.511828,-20.397508],[-43.511828,-20.397385],[-43.511954,-20.397385],[-43.511954,-20.397508],[-43.511828,-20.397508]]] }
        },
        {
          "type": "Feature",
          "id": "BSA_101",
          "properties": { "id": "BSA_101", "layer": "room", "building_id": "BSA", "code": "101", "floor": 0, "is_mock": false },
          "geometry": { "type": "Polygon", "coordinates": [[[-43.511828,-20.397508],[-43.511828,-20.397385],[-43.511954,-20.397385],[-43.511954,-20.397508],[-43.511828,-20.397508]]] }
        }
      ]
    }"#;
    let dup_err = validate_overlay(
        MORRO_DO_CRUZEIRO_GEOJSON.to_string(),
        duplicate_id_overlay.to_string(),
    )
    .expect_err("duplicate ID must be rejected");
    assert!(dup_err.to_string().contains("duplicado"));

    // Rejeita geometria fora do polígono do prédio (coordenadas no outro extremo do campus).
    let outside_overlay = r#"{
      "type": "FeatureCollection",
      "metadata": {
        "schema_version": "2.0.0",
        "campus_id": "OP_MC",
        "building_id": "BSA",
        "replaces_mock_floors": [0]
      },
      "features": [
        {
          "type": "Feature",
          "id": "BSA_999",
          "properties": { "id": "BSA_999", "layer": "room", "building_id": "BSA", "code": "999", "floor": 0, "is_mock": false },
          "geometry": { "type": "Polygon", "coordinates": [[[-43.5089,-20.3984],[-43.5089,-20.3983],[-43.5088,-20.3983],[-43.5088,-20.3984],[-43.5089,-20.3984]]] }
        }
      ]
    }"#;
    let out_err = validate_overlay(
        MORRO_DO_CRUZEIRO_GEOJSON.to_string(),
        outside_overlay.to_string(),
    )
    .expect_err("out-of-bounds room must be rejected");
    assert!(out_err.to_string().contains("fora do polígono"));

    // Rejeita andar inválido.
    let bad_floor_overlay = r#"{
      "type": "FeatureCollection",
      "metadata": {
        "schema_version": "2.0.0",
        "campus_id": "OP_MC",
        "building_id": "BSA",
        "replaces_mock_floors": [9]
      },
      "features": [
        {
          "type": "Feature",
          "id": "BSA_901",
          "properties": { "id": "BSA_901", "layer": "room", "building_id": "BSA", "code": "901", "floor": 9, "is_mock": false },
          "geometry": { "type": "Polygon", "coordinates": [[[-43.511828,-20.397508],[-43.511828,-20.397385],[-43.511954,-20.397385],[-43.511954,-20.397508],[-43.511828,-20.397508]]] }
        }
      ]
    }"#;
    let floor_err = validate_overlay(
        MORRO_DO_CRUZEIRO_GEOJSON.to_string(),
        bad_floor_overlay.to_string(),
    )
    .expect_err("invalid floor must be rejected");
    assert!(floor_err.to_string().contains("Andar inválido"));
}

#[test]
fn resolve_meeting_handles_composite_building_fallbacks() {
    let engine = CampusMapEngine::new_bundled().expect("bundled engine");

    for (raw_building, expected_campus, expected_building) in [
        ("Bloco de Salas · Sala 205", "OP_MC", "BSA"),
        ("ICEB (III) · Sala 105", "OP_MC", "ICEB"),
        ("Escola de Minas - Sala 11", "OP_MC", "EM"),
        ("Pavilhão de Aulas, Sala 101", "OP_MC", "BSA"),
        ("CEDUFOP • Quadra", "OP_MC", "EEF"),
    ] {
        match engine.resolve_meeting(
            "ZZZ999".to_string(),
            "11".to_string(),
            2,
            "08:20".to_string(),
            raw_building.to_string(),
        ) {
            Resolution::BuildingFallback {
                campus_id,
                building_id,
                camera_focus,
            } => {
                assert_eq!(
                    campus_id, expected_campus,
                    "campus mismatch for '{raw_building}'"
                );
                assert_eq!(
                    building_id, expected_building,
                    "building mismatch for '{raw_building}'"
                );
                assert_eq!(camera_focus.feature_id, expected_building);
            }
            other => panic!(
                "Expected BuildingFallback {expected_building} for '{raw_building}', got {other:?}"
            ),
        }
    }

    // Garante que find_building_focus normaliza underscores/espaços ao casar id, code, name e aliases.
    let loaded = engine.loaded_campuses();
    let bsa = engine.get_feature("BSA".to_string()).expect("BSA feature");
    let norm_name_with_underscore = bsa.name.to_ascii_uppercase().replace(' ', "_");
    let by_name = super::resolver::find_building_focus(&loaded, &norm_name_with_underscore)
        .expect("find_building_focus by underscore-normalized name");
    assert_eq!(by_name.feature_id, "BSA");

    if let Some(spaced_alias) = bsa.aliases.iter().find(|a| a.contains(' ')) {
        let alias_with_underscore = spaced_alias.to_ascii_uppercase().replace(' ', "_");
        let by_alias = super::resolver::find_building_focus(&loaded, &alias_with_underscore)
            .expect("find_building_focus by underscore-normalized spaced alias");
        assert_eq!(by_alias.feature_id, "BSA");
    }
}

#[test]
fn search_empty_query_behavior_and_poi_floor_ranking() {
    let engine = CampusMapEngine::new_bundled().expect("bundled engine");

    // Busca vazia sem filtro não deve despejar resultados arbitrários.
    assert!(engine.search("".to_string(), None, 0).is_empty());
    assert!(engine.search("   ".to_string(), None, 1).is_empty());

    let floor_tier = |floor: Option<i32>, active: i32| match floor {
        Some(f) if f == active => 0u8,
        None => 1,
        Some(_) => 2,
    };

    for filter in [PoiType::Toilet, PoiType::Library] {
        let results = engine.search("".to_string(), Some(filter), 1);
        assert!(
            !results.is_empty(),
            "search(\"\", Some({filter:?}), 1) should return results"
        );
        for item in &results {
            let expected_score = match item.feature.floor {
                Some(1) => 80,
                None => 70,
                Some(_) => 50,
            };
            assert_eq!(
                item.score, expected_score,
                "unexpected score for {:?} ({}) on floor {:?}",
                filter, item.feature.id, item.feature.floor
            );
        }
        for pair in results.windows(2) {
            assert!(pair[0].score >= pair[1].score);
            assert!(
                floor_tier(pair[0].feature.floor, 1) <= floor_tier(pair[1].feature.floor, 1),
                "ranking order violated for {filter:?}: {:?} ({:?}) before {:?} ({:?})",
                pair[0].feature.id,
                pair[0].feature.floor,
                pair[1].feature.id,
                pair[1].feature.floor
            );
        }
    }

    // Valida que bibliotecas do andar ativo (1) ficam à frente das demais.
    let libs_floor1 = engine.search("".to_string(), Some(PoiType::Library), 1);
    assert_eq!(libs_floor1[0].feature.floor, Some(1));
}

#[test]
fn list_building_occupancy_distinguishes_all_occupancy_statuses() {
    let engine = CampusMapEngine::new_bundled().expect("bundled engine");

    // Dia letivo com salas ocupadas e salas sem aula regular no mesmo prédio.
    let bsa_tue = engine
        .list_building_occupancy("BSA".to_string(), "2026-11-17T15:30:00-03:00".to_string())
        .expect("BSA tuesday occupancy");
    assert_eq!(bsa_tue.len(), 20);
    assert!(
        bsa_tue
            .iter()
            .any(|i| matches!(i.status, OccupancyStatus::Occupied { .. })),
        "BSA tuesday 15:30 should have at least one Occupied room"
    );
    assert!(
        bsa_tue
            .iter()
            .any(|i| matches!(i.status, OccupancyStatus::NoRegularClass)),
        "BSA tuesday 15:30 should have at least one NoRegularClass room"
    );

    // Sábado no BSA (cobertura apenas seg-sex) -> todas NoData.
    let bsa_sat = engine
        .list_building_occupancy("BSA".to_string(), "2026-11-21T10:00:00-03:00".to_string())
        .expect("BSA saturday occupancy");
    assert_eq!(bsa_sat.len(), 20);
    assert!(
        bsa_sat
            .iter()
            .all(|i| matches!(i.status, OccupancyStatus::NoData)),
        "BSA saturday should report NoData for all rooms"
    );

    // Feriado oficial (2026-11-20 Consciência Negra) -> todas OutOfTerm.
    let bsa_holiday = engine
        .list_building_occupancy("BSA".to_string(), "2026-11-20T14:00:00-03:00".to_string())
        .expect("BSA holiday occupancy");
    assert_eq!(bsa_holiday.len(), 20);
    assert!(
        bsa_holiday
            .iter()
            .all(|i| matches!(i.status, OccupancyStatus::OutOfTerm)),
        "BSA holiday should report OutOfTerm for all rooms"
    );
}

#[test]
fn lazy_loading_materializes_only_active_campus_and_resolves_cross_campus_on_demand() {
    let engine = CampusMapEngine::new_bundled().expect("bundled engine");
    assert_eq!(engine.loaded_campus_ids(), vec!["OP_MC"]);

    // list_campuses permanece leve e já enumera os 4 campi sem materializar os demais.
    let summaries = engine.list_campuses();
    let ids: Vec<&str> = summaries.iter().map(|c| c.campus_id.as_str()).collect();
    assert_eq!(ids, vec!["OP_MC", "JM_ICEA", "MA_ICHS", "MA_ICSA"]);
    assert_eq!(engine.loaded_campus_ids(), vec!["OP_MC"]);

    // resolve_meeting com fallback em outro campus carrega sob demanda apenas até encontrar (JM_ICEA).
    match engine.resolve_meeting(
        "ZZZ999".to_string(),
        "11".to_string(),
        2,
        "08:20".to_string(),
        "ICEA".to_string(),
    ) {
        Resolution::BuildingFallback {
            campus_id,
            building_id,
            ..
        } => {
            assert_eq!(campus_id, "JM_ICEA");
            assert_eq!(building_id, "CAMPUS_ICEA");
        }
        other => panic!("Expected BuildingFallback to JM_ICEA, got {other:?}"),
    }
    assert_eq!(engine.loaded_campus_ids(), vec!["OP_MC", "JM_ICEA"]);

    // Trocar campus para MA_ICHS materializa somente esse campus adicional.
    let ichs_summary = engine
        .set_active_campus("MA_ICHS".to_string())
        .expect("switch to MA_ICHS");
    assert_eq!(ichs_summary.campus_id, "MA_ICHS");
    assert_eq!(
        engine.loaded_campus_ids(),
        vec!["OP_MC", "JM_ICEA", "MA_ICHS"]
    );
    let ichs_batch = engine
        .get_render_batch(
            ichs_summary.default_floor,
            Some("2026-11-17T21:15:00-03:00".to_string()),
        )
        .expect("render batch MA_ICHS");
    assert_eq!(ichs_batch.campus_id, "MA_ICHS");
    assert!(!ichs_batch.items.is_empty());

    // Consulta direta de sala em JM_ICEA num motor novo carrega somente JM_ICEA sem tocar Mariana.
    let fresh_engine = CampusMapEngine::new_bundled().expect("fresh bundled engine");
    assert_eq!(fresh_engine.loaded_campus_ids(), vec!["OP_MC"]);
    let jm_occ = fresh_engine
        .get_room_occupancy(
            "ICEA_C103".to_string(),
            "2026-11-16T16:00:00-03:00".to_string(),
        )
        .expect("ICEA_C103 occupancy");
    assert!(matches!(jm_occ, OccupancyStatus::Occupied { .. }));
    assert_eq!(fresh_engine.loaded_campus_ids(), vec!["OP_MC", "JM_ICEA"]);
}

#[test]
fn render_batch_is_idempotent_presorted_and_matches_occupancy() {
    let engine = CampusMapEngine::new_bundled().expect("bundled engine");
    let ts = "2026-11-17T21:15:00-03:00".to_string();

    let batch_a = engine
        .get_render_batch(1, Some(ts.clone()))
        .expect("first batch");
    let batch_b = engine
        .get_render_batch(1, Some(ts.clone()))
        .expect("second batch");
    assert_eq!(batch_a, batch_b);

    // Verifica pré-ordenação estrita por (layer.z_order(), id).
    for pair in batch_a.items.windows(2) {
        let lhs = (pair[0].layer.z_order(), pair[0].id.as_str());
        let rhs = (pair[1].layer.z_order(), pair[1].id.as_str());
        assert!(lhs <= rhs, "batch order violated: {lhs:?} > {rhs:?}");
    }

    // Verifica paridade exata entre RenderBatch (com parse único) e get_room_occupancy individual.
    let mut checked_rooms = 0usize;
    for item in batch_a.items.iter().filter(|i| i.layer == MapLayer::Room) {
        checked_rooms += 1;
        let expected = engine
            .get_room_occupancy(item.id.clone(), ts.clone())
            .expect("room occupancy");
        match expected {
            OccupancyStatus::Occupied { slot } => {
                assert_eq!(item.occupancy, RenderOccupancyKind::Occupied);
                assert_eq!(item.occupancy_label.as_deref(), Some(slot.label.as_str()));
            }
            OccupancyStatus::NoRegularClass => {
                assert_eq!(item.occupancy, RenderOccupancyKind::NoRegularClass);
            }
            OccupancyStatus::NoData => {
                assert_eq!(item.occupancy, RenderOccupancyKind::NoData);
            }
            OccupancyStatus::OutOfTerm => {
                assert_eq!(item.occupancy, RenderOccupancyKind::OutOfTerm);
            }
        }
    }
    assert!(checked_rooms > 0);

    // Timestamp inválido deve falhar imediatamente antes do loop.
    assert!(engine.get_render_batch(1, Some(String::new())).is_err());
}

#[test]
fn simplify_reduces_external_redundancy_and_preserves_building_and_room_contours() {
    use geo::{Coord, LineString, MultiPolygon, Polygon};

    use super::projection::{ProjectedGeometry, point_to_segment_distance_sq};
    use super::simplify::{MAX_SIMPLIFY_TOLERANCE_M, simplify_geometry_for_layer};

    const { assert!(MAX_SIMPLIFY_TOLERANCE_M <= 0.35) };

    // Polyline extensa em Walkway com pontos duplicados e quase colineares (<= 0.05m).
    let walkway_pts = vec![
        Coord { x: 0.0, y: 0.0 },
        Coord { x: 0.0, y: 0.0 },
        Coord { x: 25.0, y: 0.05 },
        Coord { x: 50.0, y: 0.0 },
        Coord { x: 50.0, y: 0.0001 },
        Coord { x: 75.0, y: 12.0 },
    ];
    let walkway_geom = ProjectedGeometry::Polyline(vec![LineString::new(walkway_pts.clone())]);
    let simplified_walkway = simplify_geometry_for_layer(MapLayer::Walkway, walkway_geom);
    match simplified_walkway {
        ProjectedGeometry::Polyline(lines) => {
            assert_eq!(lines.len(), 1);
            assert!(
                lines[0].0.len() < walkway_pts.len(),
                "expected walkway vertices to be reduced, got {}",
                lines[0].0.len()
            );
            assert_eq!(lines[0].0.first().copied(), Some(Coord { x: 0.0, y: 0.0 }));
            assert_eq!(lines[0].0.last().copied(), Some(Coord { x: 75.0, y: 12.0 }));
        }
        other => panic!("Expected Polyline, got {other:?}"),
    }

    // Polígono em GreenArea mantém anel fechado e distância máxima <= MAX_SIMPLIFY_TOLERANCE_M.
    let green_ring = LineString::new(vec![
        Coord { x: 0.0, y: 0.0 },
        Coord { x: 10.0, y: 0.05 },
        Coord { x: 20.0, y: 0.0 },
        Coord { x: 20.0, y: 20.0 },
        Coord { x: 10.0, y: 20.02 },
        Coord { x: 0.0, y: 20.0 },
        Coord { x: 0.0, y: 0.0 },
    ]);
    let green_poly = ProjectedGeometry::Polygon(MultiPolygon::new(vec![Polygon::new(
        green_ring.clone(),
        vec![],
    )]));
    match simplify_geometry_for_layer(MapLayer::GreenArea, green_poly) {
        ProjectedGeometry::Polygon(mp) => {
            let ext = &mp.0[0].exterior().0;
            assert!(ext.len() < green_ring.0.len());
            assert_eq!(ext.first(), ext.last());
            assert!(ext.len() >= 4);
            for orig in &green_ring.0 {
                let mut best_sq = f64::INFINITY;
                for seg in ext.windows(2) {
                    let d_sq = point_to_segment_distance_sq(
                        orig.x, orig.y, seg[0].x, seg[0].y, seg[1].x, seg[1].y,
                    );
                    best_sq = best_sq.min(d_sq);
                }
                assert!(best_sq.sqrt() <= MAX_SIMPLIFY_TOLERANCE_M + 1e-6);
            }
        }
        other => panic!("Expected Polygon, got {other:?}"),
    }

    // Contornos de Building e Room devem permanecer 100% intactos (mesmos vértices exatos).
    let room_ring = LineString::new(vec![
        Coord { x: 0.0, y: 0.0 },
        Coord { x: 5.0, y: 0.01 },
        Coord { x: 10.0, y: 0.0 },
        Coord { x: 10.0, y: 8.0 },
        Coord { x: 0.0, y: 8.0 },
        Coord { x: 0.0, y: 0.0 },
    ]);
    for preserved_layer in [MapLayer::Building, MapLayer::Room] {
        let geom = ProjectedGeometry::Polygon(MultiPolygon::new(vec![Polygon::new(
            room_ring.clone(),
            vec![],
        )]));
        match simplify_geometry_for_layer(preserved_layer, geom) {
            ProjectedGeometry::Polygon(mp) => {
                assert_eq!(mp.0[0].exterior().0, room_ring.0);
            }
            other => panic!("Expected Polygon for {preserved_layer:?}, got {other:?}"),
        }
    }
}
