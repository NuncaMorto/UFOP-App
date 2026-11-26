package br.ufop.campus.core.map

import java.util.Locale

/**
 * Traduz categorias técnicas de feições GeoJSON do Mapa 2D para rótulos limpos em Português (PT-BR).
 */
fun localizeCampusMapCategory(raw: String?): String? {
    val key = raw?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
    return when (key) {
        "classroom" -> "Sala de aula"
        "lab", "laboratory" -> "Laboratório"
        "library" -> "Biblioteca"
        "dining" -> "Alimentação"
        "toilet", "wc" -> "Banheiro"
        "faculty_office" -> "Secretaria / Docentes"
        "administrative" -> "Administrativo"
        "auditorium" -> "Auditório"
        "entrance" -> "Acesso"
        "parking", "parking_aisle" -> "Estacionamento"
        "academic" -> "Bloco acadêmico"
        "academic_zone" -> "Setor acadêmico"
        "campus" -> "Campus"
        "health" -> "Saúde"
        "housing" -> "Moradia estudantil"
        "sports" -> "Esportes"
        "sports_building" -> "Ginásio / Esportes"
        "soccer" -> "Campo de futebol"
        "beachvolleyball" -> "Quadra de areia"
        "park", "grass" -> "Área verde / Praça"
        "footpath" -> "Passarela"
        "steps" -> "Escadaria"
        "service_road" -> "Via interna"
        "support" -> "Apoio / Serviços"
        else -> key.replace('_', ' ').replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.forLanguageTag("pt-BR")) else ch.toString()
        }
    }
}

/**
 * Remove metadados técnicos internos (como `is_mock=true`) do texto exibido nos créditos do mapa.
 */
fun sanitizeMapAttributionText(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) {
        return "Dados cartográficos © colaboradores do OpenStreetMap (ODbL) e mapeamento acadêmico UFOP. Layouts internos de salas são representações esquemáticas para orientação estudantil."
    }
    return trimmed
        .replace(Regex("""\s*\(\s*is_mock\s*=\s*true\s*\)""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""is_mock\s*=\s*true""", RegexOption.IGNORE_CASE), "modo esquemático")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
}
