use std::path::Path;
use std::sync::Mutex;

use rusqlite::Connection;

use crate::CampusError;

const MIGRATION_001_SCHEMA: &str = include_str!("../migrations/001_initial_schema.sql");
const MIGRATION_002_SEED: &str = include_str!("../migrations/002_seed_ufop_2026_2.sql");
const MIGRATION_003_SPRINT2: &str = include_str!("../migrations/003_sprint2_ufop_scale.sql");
const MIGRATION_004_SPRINT3: &str = include_str!("../migrations/004_sprint3_course_templates.sql");
const MIGRATION_005_SPRINT13: &str =
    include_str!("../migrations/005_sprint13_official_calendars.sql");

const STANDARDIZED_UFOP_DEPARTMENTS_SQL: &str = "
INSERT INTO departments (id, name, color_hex) VALUES
    ('math', 'MTM · Matemática', '#3B68FF'),
    ('comp', 'BCC · Computação', '#6E56CF'),
    ('cat', 'CAT · Controle e Automação', '#2E7D32'),
    ('phys', 'FIS · Física', '#8D5B16'),
    ('em', 'EM · Escola de Minas', '#00897B')
ON CONFLICT(id) DO UPDATE SET
    name = excluded.name,
    color_hex = excluded.color_hex;
";

const SEED_EXTRA_DEPARTMENTS_SQL: &str = "
INSERT INTO departments (id, name, color_hex) VALUES
    ('cat', 'CAT · Controle e Automação', '#2E7D32'),
    ('em', 'EM · Escola de Minas', '#00897B')
ON CONFLICT(id) DO UPDATE SET
    name = excluded.name,
    color_hex = excluded.color_hex;
";

pub struct Database {
    conn: Mutex<Connection>,
}

impl Database {
    pub fn open(db_path: &str) -> Result<Self, CampusError> {
        Self::open_with_mode(db_path, true)
    }

    pub fn open_empty(db_path: &str) -> Result<Self, CampusError> {
        Self::open_with_mode(db_path, false)
    }

    pub fn open_in_memory() -> Result<Self, CampusError> {
        let conn = Connection::open_in_memory()?;
        Self::from_connection(conn, true)
    }

    pub fn open_in_memory_empty() -> Result<Self, CampusError> {
        let conn = Connection::open_in_memory()?;
        Self::from_connection(conn, false)
    }

    fn open_with_mode(db_path: &str, seed_examples: bool) -> Result<Self, CampusError> {
        let trimmed = db_path.trim();
        let conn = if trimmed.is_empty() || trimmed == ":memory:" {
            Connection::open_in_memory()?
        } else {
            if let Some(parent) = Path::new(trimmed).parent()
                && !parent.as_os_str().is_empty()
            {
                std::fs::create_dir_all(parent).map_err(|err| {
                    CampusError::Database(format!(
                        "Falha ao criar diretório para banco SQLite ({}): {}",
                        parent.display(),
                        err
                    ))
                })?;
            }
            Connection::open(trimmed)?
        };

        Self::from_connection(conn, seed_examples)
    }

    fn from_connection(conn: Connection, seed_examples: bool) -> Result<Self, CampusError> {
        conn.execute_batch("PRAGMA foreign_keys = ON;")?;
        conn.execute_batch(MIGRATION_001_SCHEMA)?;
        if seed_examples {
            conn.execute_batch(MIGRATION_002_SEED)?;
            conn.execute_batch(SEED_EXTRA_DEPARTMENTS_SQL)?;
        } else {
            conn.execute_batch(STANDARDIZED_UFOP_DEPARTMENTS_SQL)?;
        }

        let has_completed_column: bool = conn
            .prepare("SELECT 1 FROM pragma_table_info('assessments') WHERE name = 'completed'")?
            .exists([])?;
        if !has_completed_column {
            conn.execute_batch(MIGRATION_003_SPRINT2)?;
        } else {
            let without_alter = MIGRATION_003_SPRINT2
                .lines()
                .filter(|line| {
                    !line
                        .trim_start()
                        .to_ascii_uppercase()
                        .starts_with("ALTER TABLE ASSESSMENTS")
                })
                .collect::<Vec<_>>()
                .join("\n");
            conn.execute_batch(&without_alter)?;
        }

        conn.execute_batch(MIGRATION_004_SPRINT3)?;
        conn.execute_batch(MIGRATION_005_SPRINT13)?;

        Ok(Self {
            conn: Mutex::new(conn),
        })
    }

    pub fn with_conn<T, F>(&self, op: F) -> Result<T, CampusError>
    where
        F: FnOnce(&Connection) -> Result<T, CampusError>,
    {
        let guard = self
            .conn
            .lock()
            .map_err(|_| CampusError::Database("Lock do SQLite envenenado (poisoned)".into()))?;
        op(&guard)
    }
}
