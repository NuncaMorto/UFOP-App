pub mod campus_engine;
pub mod map_engine;

use crate::domain;

pub use campus_engine::CampusEngine;
pub use map_engine::{CampusMapEngine, validate_overlay};

#[derive(uniffi::Error, thiserror::Error, Debug)]
#[uniffi(flat_error)]
pub enum CampusError {
    #[error("Erro de banco de dados SQLite: {0}")]
    Database(String),
    #[error("Registro não encontrado: {0}")]
    NotFound(String),
    #[error("Parâmetro inválido: {0}")]
    InvalidInput(String),
}

impl From<rusqlite::Error> for CampusError {
    fn from(err: rusqlite::Error) -> Self {
        Self::Database(err.to_string())
    }
}

/// Calcula o limite máximo de faltas no padrão UFOP (25% da carga horária em horas-aula de 50 min).
#[uniffi::export]
pub fn calculate_max_absences(workload_hours: u32) -> u32 {
    domain::attendance::calculate_max_absences(workload_hours)
}
