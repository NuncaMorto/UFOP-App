-- Sprint 3: Templates Oficiais de Cursos UFOP e Catálogo de Horários

CREATE TABLE IF NOT EXISTS ufop_templates (
    id              TEXT PRIMARY KEY,
    code            TEXT NOT NULL,
    name            TEXT NOT NULL,
    department_name TEXT NOT NULL,
    campus_name     TEXT NOT NULL,
    description     TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ufop_template_courses (
    id                  TEXT PRIMARY KEY,
    template_id         TEXT NOT NULL REFERENCES ufop_templates(id) ON DELETE CASCADE,
    course_id           TEXT NOT NULL,
    department_id       TEXT NOT NULL,
    color_override_hex  TEXT,
    name                TEXT NOT NULL,
    professor           TEXT NOT NULL,
    workload_hours      INTEGER NOT NULL,
    day_of_week         INTEGER NOT NULL,
    start_time          TEXT NOT NULL,
    end_time            TEXT NOT NULL,
    building            TEXT NOT NULL,
    room                TEXT NOT NULL,
    short_location      TEXT NOT NULL
);

INSERT OR IGNORE INTO ufop_templates (
    id,
    code,
    name,
    department_name,
    campus_name,
    description
) VALUES
    (
        'tpl_bcc_decom',
        'BCC · DECOM',
        'Ciência da Computação',
        'DECOM',
        'Morro do Cruzeiro · Ouro Preto',
        'Grade base com Programação, Estruturas de Dados, Cálculo e Física.'
    ),
    (
        'tpl_ec_decsi',
        'ENGC · DECSI',
        'Engenharia de Computação',
        'DECSI',
        'ICEA · João Monlevade',
        'Grade do ICEA com Circuitos Digitais, Algoritmos e Cálculo.'
    ),
    (
        'tpl_eng_basico',
        'CICLO BÁSICO · EM',
        'Engenharias (Ciclo Básico)',
        'Escola de Minas',
        'Morro do Cruzeiro · Ouro Preto',
        'Núcleo comum da Escola de Minas: Cálculo, Álgebra Linear, Física e Computação.'
    );

-- Template 1: Ciência da Computação (BCC · DECOM) — 4 disciplinas, sem colisões
INSERT OR IGNORE INTO ufop_template_courses (
    id,
    template_id,
    course_id,
    department_id,
    color_override_hex,
    name,
    professor,
    workload_hours,
    day_of_week,
    start_time,
    end_time,
    building,
    room,
    short_location
) VALUES
    ('tc_bcc_prog1_seg', 'tpl_bcc_decom', 'bcc201', 'comp', NULL, 'BCC201 Programação I', 'Prof. Rafael Lima', 60, 1, '08:00', '09:40', 'ICEB', 'Sala 102', 'ICEB · 102'),
    ('tc_bcc_prog1_qua', 'tpl_bcc_decom', 'bcc201', 'comp', NULL, 'BCC201 Programação I', 'Prof. Rafael Lima', 60, 3, '08:00', '09:40', 'ICEB', 'Sala 102', 'ICEB · 102'),
    ('tc_bcc_ed1_ter', 'tpl_bcc_decom', 'bcc202', 'comp', '#2E7D32', 'BCC202 Estruturas de Dados I', 'Prof.ª Camila Souza', 60, 2, '10:00', '11:40', 'ICEB', 'Laboratório 03', 'ICEB · Lab 03'),
    ('tc_bcc_ed1_qui', 'tpl_bcc_decom', 'bcc202', 'comp', '#2E7D32', 'BCC202 Estruturas de Dados I', 'Prof.ª Camila Souza', 60, 4, '10:00', '11:40', 'ICEB', 'Laboratório 03', 'ICEB · Lab 03'),
    ('tc_bcc_calc1_ter', 'tpl_bcc_decom', 'mtm122', 'math', NULL, 'MTM122 Cálculo Diferencial e Integral I', 'Prof.ª Ana Martins', 90, 2, '08:00', '09:40', 'Prédio A', 'Sala 204', 'A · 204'),
    ('tc_bcc_calc1_qui', 'tpl_bcc_decom', 'mtm122', 'math', NULL, 'MTM122 Cálculo Diferencial e Integral I', 'Prof.ª Ana Martins', 90, 4, '08:00', '09:40', 'Prédio A', 'Sala 204', 'A · 204'),
    ('tc_bcc_calc1_sex', 'tpl_bcc_decom', 'mtm122', 'math', NULL, 'MTM122 Cálculo Diferencial e Integral I', 'Prof.ª Ana Martins', 90, 5, '08:00', '09:40', 'Prédio A', 'Sala 204', 'A · 204'),
    ('tc_bcc_fis1_seg', 'tpl_bcc_decom', 'fis105', 'phys', NULL, 'FIS105 Física I', 'Prof.ª Helena Costa', 60, 1, '10:00', '11:40', 'Prédio D', 'Laboratório 01', 'D · Lab 01'),
    ('tc_bcc_fis1_qua', 'tpl_bcc_decom', 'fis105', 'phys', NULL, 'FIS105 Física I', 'Prof.ª Helena Costa', 60, 3, '14:00', '15:00', 'Prédio D', 'Laboratório 01', 'D · Lab 01');

-- Template 2: Engenharia de Computação (ENGC · DECSI · ICEA) — 4 disciplinas, sem colisões
INSERT OR IGNORE INTO ufop_template_courses (
    id,
    template_id,
    course_id,
    department_id,
    color_override_hex,
    name,
    professor,
    workload_hours,
    day_of_week,
    start_time,
    end_time,
    building,
    room,
    short_location
) VALUES
    ('tc_ec_prog2_seg', 'tpl_ec_decsi', 'csi102', 'comp', NULL, 'CSI102 Programação II', 'Prof. Bruno Oliveira', 60, 1, '08:00', '09:40', 'ICEA', 'Laboratório 02', 'ICEA · Lab 02'),
    ('tc_ec_prog2_qua', 'tpl_ec_decsi', 'csi102', 'comp', NULL, 'CSI102 Programação II', 'Prof. Bruno Oliveira', 60, 3, '08:00', '09:40', 'ICEA', 'Laboratório 02', 'ICEA · Lab 02'),
    ('tc_ec_cd_ter', 'tpl_ec_decsi', 'csi203', 'comp', '#2E7D32', 'CSI203 Circuitos Digitais', 'Prof. Eduardo Mendes', 60, 2, '10:00', '11:40', 'ICEA', 'Sala B12', 'ICEA · B12'),
    ('tc_ec_cd_qui', 'tpl_ec_decsi', 'csi203', 'comp', '#2E7D32', 'CSI203 Circuitos Digitais', 'Prof. Eduardo Mendes', 60, 4, '10:00', '11:40', 'ICEA', 'Sala B12', 'ICEA · B12'),
    ('tc_ec_calc_ter', 'tpl_ec_decsi', 'cea101', 'math', NULL, 'CEA101 Cálculo I', 'Prof.ª Fernanda Rocha', 90, 2, '08:00', '09:40', 'ICEA', 'Sala A04', 'ICEA · A04'),
    ('tc_ec_calc_qui', 'tpl_ec_decsi', 'cea101', 'math', NULL, 'CEA101 Cálculo I', 'Prof.ª Fernanda Rocha', 90, 4, '08:00', '09:40', 'ICEA', 'Sala A04', 'ICEA · A04'),
    ('tc_ec_calc_sex', 'tpl_ec_decsi', 'cea101', 'math', NULL, 'CEA101 Cálculo I', 'Prof.ª Fernanda Rocha', 90, 5, '08:00', '09:40', 'ICEA', 'Sala A04', 'ICEA · A04'),
    ('tc_ec_fis_seg', 'tpl_ec_decsi', 'cea204', 'phys', NULL, 'CEA204 Física Experimental I', 'Prof. Lucas Ferreira', 30, 1, '10:00', '11:40', 'ICEA', 'Lab Física', 'ICEA · Lab Fís'),
    ('tc_ec_fis_qua', 'tpl_ec_decsi', 'cea204', 'phys', NULL, 'CEA204 Física Experimental I', 'Prof. Lucas Ferreira', 30, 3, '14:00', '15:00', 'ICEA', 'Lab Física', 'ICEA · Lab Fís');

-- Template 3: Engenharias (Ciclo Básico · Escola de Minas) — 4 disciplinas, sem colisões
INSERT OR IGNORE INTO ufop_template_courses (
    id,
    template_id,
    course_id,
    department_id,
    color_override_hex,
    name,
    professor,
    workload_hours,
    day_of_week,
    start_time,
    end_time,
    building,
    room,
    short_location
) VALUES
    ('tc_em_calc1_seg', 'tpl_eng_basico', 'mtm122_em', 'math', NULL, 'MTM122 Cálculo I', 'Prof.ª Ana Martins', 90, 1, '08:00', '09:40', 'Escola de Minas', 'Sala 101', 'EM · 101'),
    ('tc_em_calc1_qua', 'tpl_eng_basico', 'mtm122_em', 'math', NULL, 'MTM122 Cálculo I', 'Prof.ª Ana Martins', 90, 3, '08:00', '09:40', 'Escola de Minas', 'Sala 101', 'EM · 101'),
    ('tc_em_calc1_sex', 'tpl_eng_basico', 'mtm122_em', 'math', NULL, 'MTM122 Cálculo I', 'Prof.ª Ana Martins', 90, 5, '08:00', '09:40', 'Escola de Minas', 'Sala 101', 'EM · 101'),
    ('tc_em_gaal_ter', 'tpl_eng_basico', 'mtm112_em', 'math', '#2E7D32', 'MTM112 Álgebra Linear', 'Prof. Marcos Vieira', 60, 2, '08:00', '09:40', 'Prédio B', 'Sala 105', 'B · 105'),
    ('tc_em_gaal_qui', 'tpl_eng_basico', 'mtm112_em', 'math', '#2E7D32', 'MTM112 Álgebra Linear', 'Prof. Marcos Vieira', 60, 4, '08:00', '09:40', 'Prédio B', 'Sala 105', 'B · 105'),
    ('tc_em_fis1_seg', 'tpl_eng_basico', 'fis105_em', 'phys', NULL, 'FIS105 Física I', 'Prof.ª Helena Costa', 60, 1, '10:00', '11:40', 'Prédio D', 'Sala 02', 'D · 02'),
    ('tc_em_fis1_qua', 'tpl_eng_basico', 'fis105_em', 'phys', NULL, 'FIS105 Física I', 'Prof.ª Helena Costa', 60, 3, '10:00', '11:40', 'Prédio D', 'Sala 02', 'D · 02'),
    ('tc_em_comp_ter', 'tpl_eng_basico', 'bcc701_em', 'comp', NULL, 'BCC701 Programação de Computadores', 'Prof. Rafael Lima', 60, 2, '10:00', '11:40', 'ICEB', 'Laboratório 01', 'ICEB · Lab 01'),
    ('tc_em_comp_qui', 'tpl_eng_basico', 'bcc701_em', 'comp', NULL, 'BCC701 Programação de Computadores', 'Prof. Rafael Lima', 60, 4, '14:00', '15:00', 'ICEB', 'Laboratório 01', 'ICEB · Lab 01');
