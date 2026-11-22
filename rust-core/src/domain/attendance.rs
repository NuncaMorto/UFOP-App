/// Calcula o limite máximo de faltas de acordo com o padrão universitário da UFOP:
/// 25% da carga horária convertida em horas-aula de 50 minutos (`(workload_hours * 3) / 10`).
///
/// Exemplos:
/// - `30 h -> 9 faltas`
/// - `60 h -> 18 faltas`
/// - `90 h -> 27 faltas`
pub const fn calculate_max_absences(workload_hours: u32) -> u32 {
    (workload_hours * 3) / 10
}

/// Calcula o total de faltas atuais somando o acumulado inicial (`manual_absences`)
/// com a soma dos pesos das aulas marcadas como `ABSENT`.
/// Aulas marcadas como `CANCELLED` NUNCA entram nesta soma.
pub const fn compute_current_absences(manual_absences: u32, absent_session_weights: u32) -> u32 {
    manual_absences.saturating_add(absent_session_weights)
}
