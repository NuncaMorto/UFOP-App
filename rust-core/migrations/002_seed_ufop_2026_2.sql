-- Seed inicial do projeto CAMPUS (UFOP 2026.2)

INSERT OR IGNORE INTO departments (id, name, color_hex) VALUES
    ('math', 'Matemática', '#3B68FF'),
    ('comp', 'Computação', '#6E56CF'),
    ('phys', 'Física', '#8D5B16');

INSERT OR IGNORE INTO courses (
    id,
    semester,
    department_id,
    color_override_hex,
    name,
    professor,
    workload_hours,
    max_absences,
    manual_absences,
    notes
) VALUES
    ('calc2', '2026.2', 'math', NULL, 'Cálculo II', 'Prof.ª Ana Martins', 60, 18, 4, 'Revisar integrais por partes.'),
    ('alg', '2026.2', 'comp', NULL, 'Algoritmos', 'Prof. Rafael Lima', 60, 18, 2, 'Praticar árvores balanceadas e grafos.'),
    ('alglin', '2026.2', 'math', '#2E7D32', 'Álgebra Linear', 'Prof. Marcos Vieira', 90, 27, 1, 'Estudar autovalores, autovetores e diagonalização.'),
    ('fis1', '2026.2', 'phys', NULL, 'Física I', 'Prof.ª Helena Costa', 30, 9, 3, 'Lista de cinemática e leis de Newton.');

INSERT OR IGNORE INTO schedule_slots (
    id,
    course_id,
    day_of_week,
    start_time,
    end_time,
    building,
    room,
    short_location,
    class_weight
) VALUES
    ('slot_seg_0800_alglin', 'alglin', 1, '08:00', '09:40', 'Prédio B', 'Sala 105', 'B · 105', 1),
    ('slot_seg_1000_fis1', 'fis1', 1, '10:00', '11:40', 'Prédio D', 'Laboratório 01', 'D · Lab 01', 1),
    ('slot_ter_0800_calc2', 'calc2', 2, '08:00', '09:40', 'Prédio A', 'Sala 204', 'A · 204', 1),
    ('slot_ter_1000_alg', 'alg', 2, '10:00', '11:40', 'Prédio C', 'Laboratório 03', 'C · Lab 03', 1),
    ('slot_qua_0800_alglin', 'alglin', 3, '08:00', '09:40', 'Prédio B', 'Sala 105', 'B · 105', 1),
    ('slot_qua_1000_fis1', 'fis1', 3, '10:00', '11:40', 'Prédio D', 'Laboratório 01', 'D · Lab 01', 1),
    ('slot_qui_0800_calc2', 'calc2', 4, '08:00', '09:40', 'Prédio A', 'Sala 204', 'A · 204', 1),
    ('slot_qui_1000_alg', 'alg', 4, '10:00', '11:40', 'Prédio C', 'Laboratório 03', 'C · Lab 03', 1),
    ('slot_sex_0800_alglin', 'alglin', 5, '08:00', '09:40', 'Prédio B', 'Sala 105', 'B · 105', 1);

INSERT OR IGNORE INTO class_sessions (
    id,
    slot_id,
    course_id,
    session_date,
    status,
    weather_temp_c,
    weather_condition
) VALUES
    ('slot_ter_0800_calc2_2026-11-17', 'slot_ter_0800_calc2', 'calc2', '2026-11-17', 'ATTENDING', 22, 'Ensolarado'),
    ('slot_ter_1000_alg_2026-11-17', 'slot_ter_1000_alg', 'alg', '2026-11-17', 'ATTENDING', 24, 'Ensolarado');

INSERT OR IGNORE INTO commitments (
    id,
    title,
    description,
    commitment_date,
    start_time,
    end_time,
    location,
    weather_temp_c
) VALUES
    ('comm_2026-11-17_estudo', 'Grupo de estudo', 'Revisão de Cálculo II com a turma', '2026-11-17', '14:00', '15:00', 'Biblioteca · Sala 02', 26);

INSERT OR IGNORE INTO assessments (
    id,
    course_id,
    title,
    kind,
    due_date,
    due_time
) VALUES
    ('assess_calc2_1', 'calc2', 'Lista de integrais', 'Trabalho', '2026-11-20', '18:00'),
    ('assess_calc2_2', 'calc2', 'Aplicações de integrais', 'Seminário', '2026-11-24', '16:00'),
    ('assess_calc2_3', 'calc2', 'Prova 2', 'Prova', '2026-11-27', '18:00'),
    ('assess_alg_1', 'alg', 'Implementação de Árvore AVL', 'Trabalho', '2026-11-21', '23:59'),
    ('assess_alg_2', 'alg', 'Prova de Grafos', 'Prova', '2026-12-01', '10:00'),
    ('assess_alglin_1', 'alglin', 'Lista de Autovalores e Autovetores', 'Trabalho', '2026-11-23', '18:00'),
    ('assess_alglin_2', 'alglin', 'Prova 2 - Diagonalização', 'Prova', '2026-12-02', '08:00'),
    ('assess_fis1_1', 'fis1', 'Relatório Lab 01 - Cinemática', 'Trabalho', '2026-11-25', '18:00'),
    ('assess_fis1_2', 'fis1', 'Prova de Leis de Newton', 'Prova', '2026-12-04', '10:00');
