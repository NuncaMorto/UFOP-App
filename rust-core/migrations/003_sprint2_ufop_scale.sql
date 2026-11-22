-- Sprint 2: Escala UFOP (Campi, Calendário Acadêmico 2026.2 e Conclusão de Avaliações)

ALTER TABLE assessments ADD COLUMN completed INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS ufop_campuses (
    id                  TEXT PRIMARY KEY,
    name                TEXT NOT NULL,
    city                TEXT NOT NULL,
    weather_temp_c      INTEGER NOT NULL,
    weather_condition   TEXT NOT NULL,
    is_active           INTEGER NOT NULL DEFAULT 0
);

INSERT OR IGNORE INTO ufop_campuses (
    id,
    name,
    city,
    weather_temp_c,
    weather_condition,
    is_active
) VALUES
    ('op_cruzeiro', 'Campus Morro do Cruzeiro', 'Ouro Preto', 22, 'Ensolarado', 1),
    ('mar_ichs', 'Campus ICHS / ICSA', 'Mariana', 25, 'Parcialmente nublado', 0),
    ('jm_icea', 'Campus ICEA', 'João Monlevade', 26, 'Ensolarado', 0);

CREATE TABLE IF NOT EXISTS academic_calendar_events (
    id          TEXT PRIMARY KEY,
    semester    TEXT NOT NULL,
    event_date  TEXT NOT NULL,
    day_number  TEXT NOT NULL,
    month_short TEXT NOT NULL,
    title       TEXT NOT NULL,
    category    TEXT NOT NULL
);

INSERT OR IGNORE INTO academic_calendar_events (
    id,
    semester,
    event_date,
    day_number,
    month_short,
    title,
    category
) VALUES
    ('cal_2026_2_trancamento', '2026.2', '2026-11-18', '18', 'NOV', 'Período de Trancamento', 'Secretaria'),
    ('cal_2026_2_estudos_autonomos', '2026.2', '2026-12-02', '02', 'DEZ', 'Semana de Estudos Autônomos', 'Acadêmico'),
    ('cal_2026_2_encerramento', '2026.2', '2026-12-12', '12', 'DEZ', 'Encerramento das Aulas 2026.2', 'Calendário Oficial'),
    ('cal_2026_2_exames_especiais', '2026.2', '2026-12-15', '15', 'DEZ', 'Exames Especiais', 'Avaliação');
