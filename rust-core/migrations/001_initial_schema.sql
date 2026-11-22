CREATE TABLE IF NOT EXISTS departments (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    color_hex   TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS courses (
    id                  TEXT PRIMARY KEY,
    semester            TEXT NOT NULL,
    department_id       TEXT NOT NULL REFERENCES departments(id),
    color_override_hex  TEXT,
    name                TEXT NOT NULL,
    professor           TEXT NOT NULL,
    workload_hours      INTEGER NOT NULL,
    max_absences        INTEGER NOT NULL,
    manual_absences     INTEGER NOT NULL DEFAULT 0,
    notes               TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS schedule_slots (
    id              TEXT PRIMARY KEY,
    course_id       TEXT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    day_of_week     INTEGER NOT NULL, -- 1=SEG, 2=TER, 3=QUA, 4=QUI, 5=SEX, 6=SAB, 7=DOM
    start_time      TEXT NOT NULL,
    end_time        TEXT NOT NULL,
    building        TEXT NOT NULL,
    room            TEXT NOT NULL,
    short_location  TEXT NOT NULL,
    class_weight    INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS class_sessions (
    id                  TEXT PRIMARY KEY,
    slot_id             TEXT NOT NULL REFERENCES schedule_slots(id) ON DELETE CASCADE,
    course_id           TEXT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    session_date        TEXT NOT NULL,
    status              TEXT NOT NULL CHECK (status IN ('ATTENDING', 'ABSENT', 'CANCELLED')),
    weather_temp_c      INTEGER,
    weather_condition   TEXT,
    UNIQUE(slot_id, session_date)
);

CREATE TABLE IF NOT EXISTS commitments (
    id                  TEXT PRIMARY KEY,
    title               TEXT NOT NULL,
    description         TEXT NOT NULL,
    commitment_date     TEXT NOT NULL,
    start_time          TEXT NOT NULL,
    end_time            TEXT NOT NULL,
    location            TEXT NOT NULL,
    weather_temp_c      INTEGER
);

CREATE TABLE IF NOT EXISTS assessments (
    id              TEXT PRIMARY KEY,
    course_id       TEXT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title           TEXT NOT NULL,
    kind            TEXT NOT NULL,
    due_date        TEXT NOT NULL,
    due_time        TEXT NOT NULL
);
