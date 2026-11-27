package br.ufop.campus.core.ffi


private val UFOP_LOWERCASE_PARTICLES = setOf(
    "de", "da", "do", "das", "dos", "e", "em", "na", "no", "nas", "nos",
    "para", "por", "com", "sem", "sob", "a", "o", "as", "os", "ao", "aos",
    "ou"
)

private val UFOP_ROMAN_NUMERALS = setOf(
    "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"
)

private val UFOP_PRESERVED_ACRONYMS = setOf(
    "UFOP", "ICEB", "ICEA", "ICHS", "ICSA", "EMED", "EFAR", "EDTM", "ENUT",
    "CEDUFOP", "CEAD", "MEC", "SISU", "ENADE", "DOU", "TCC", "CLP", "PLC",
    "CAD", "CAM", "CAE", "CNC", "IHM", "PID", "FPGA", "VHDL", "DSP", "IOT",
    "IA", "SO", "BD", "POO", "UML", "ERP", "SIG", "BIM", "TI",
    "EDO", "EDP", "SUB", "IC", "PIBIC", "PET", "EAD", "PDF", "API", "SQL",
    "ULA", "ALU", "CPU", "GPU", "RAM", "SGA",
    "DEABI", "DEACL", "DEALI", "DEAMB", "DEARQ", "DEART", "DEBIO", "DECAD",
    "DECAT", "DECBI", "DECEA", "DECGP", "DECIV", "DECOM", "DECPA", "DECSI",
    "DECSO", "DEDIR", "DEECO", "DEEDU", "DEEFD", "DEELT", "DEEMA", "DEENP",
    "DEEST", "DEETE", "DEFAR", "DEFIL", "DEFIS", "DEGEO", "DEGEP", "DEHIS",
    "DEISH", "DEJOR", "DELET", "DEMAT", "DEMEC", "DEMET", "DEMIN", "DEMSC",
    "DEMUL", "DEMUS", "DENCS", "DEPRO", "DEQUI", "DESSO", "DETUR", "DEURB"
)

private val UFOP_ABBREVIATION_TITLES = setOf(
    "dr", "dra", "me", "ma", "prof", "eng", "sr", "sra"
)

private val UFOP_PORTUGUESE_WORD_MAP: Map<String, String> = mapOf(
    "academica" to "acadêmica",
    "academicas" to "acadêmicas",
    "academico" to "acadêmico",
    "academicos" to "acadêmicos",
    "acao" to "ação",
    "acoes" to "ações",
    "acustica" to "acústica",
    "acustico" to "acústico",
    "administracao" to "administração",
    "administracoes" to "administrações",
    "administrativa" to "administrativa",
    "administrativas" to "administrativas",
    "aerodinamica" to "aerodinâmica",
    "aerodinamico" to "aerodinâmico",
    "algebra" to "álgebra",
    "algebrica" to "algébrica",
    "algebrico" to "algébrico",
    "analise" to "análise",
    "analises" to "análises",
    "analitica" to "analítica",
    "analiticas" to "analíticas",
    "analitico" to "analítico",
    "analiticos" to "analíticos",
    "analogica" to "analógica",
    "analogicas" to "analógicas",
    "analogico" to "analógico",
    "analogicos" to "analógicos",
    "aplicacao" to "aplicação",
    "aplicacoes" to "aplicações",
    "apresentacao" to "apresentação",
    "aquisicao" to "aquisição",
    "arquitetura" to "arquitetura",
    "astrofisica" to "astrofísica",
    "astronomia" to "astronomia",
    "automacao" to "automação",
    "automatos" to "autômatos",
    "avancada" to "avançada",
    "avancadas" to "avançadas",
    "avancado" to "avançado",
    "avancados" to "avançados",
    "avaliacao" to "avaliação",
    "avaliacoes" to "avaliações",
    "basica" to "básica",
    "basicas" to "básicas",
    "basico" to "básico",
    "basicos" to "básicos",
    "biologica" to "biológica",
    "biologicas" to "biológicas",
    "biologico" to "biológico",
    "biologicos" to "biológicos",
    "bioquimica" to "bioquímica",
    "botanica" to "botânica",
    "calculo" to "cálculo",
    "calculos" to "cálculos",
    "caracterizacao" to "caracterização",
    "cenicas" to "cênicas",
    "ceramica" to "cerâmica",
    "ciencia" to "ciência",
    "ciencias" to "ciências",
    "cientifica" to "científica",
    "cientifico" to "científico",
    "cinematica" to "cinemática",
    "cinetica" to "cinética",
    "cirurgica" to "cirúrgica",
    "cirurgicas" to "cirúrgicas",
    "cirurgico" to "cirúrgico",
    "cirurgicos" to "cirúrgicos",
    "classica" to "clássica",
    "classico" to "clássico",
    "clinica" to "clínica",
    "clinicas" to "clínicas",
    "clinico" to "clínico",
    "clinicos" to "clínicos",
    "codigo" to "código",
    "codigos" to "códigos",
    "combinatoria" to "combinatória",
    "combinatorio" to "combinatório",
    "computacao" to "computação",
    "computacional" to "computacional",
    "comunicacao" to "comunicação",
    "comunicacoes" to "comunicações",
    "conclusao" to "conclusão",
    "conducao" to "condução",
    "conservacao" to "conservação",
    "construcao" to "construção",
    "construcoes" to "construções",
    "contemporanea" to "contemporânea",
    "contemporaneo" to "contemporâneo",
    "conversao" to "conversão",
    "criptografia" to "criptografia",
    "curriculo" to "currículo",
    "danca" to "dança",
    "didatica" to "didática",
    "diferenciais" to "diferenciais",
    "dimensao" to "dimensão",
    "dinamica" to "dinâmica",
    "dinamicas" to "dinâmicas",
    "dinamico" to "dinâmico",
    "dinamicos" to "dinâmicos",
    "dispositivos" to "dispositivos",
    "dispositvos" to "dispositivos",
    "distribuicao" to "distribuição",
    "distribuida" to "distribuída",
    "distribuido" to "distribuído",
    "ecologia" to "ecologia",
    "economica" to "econômica",
    "economicas" to "econômicas",
    "economico" to "econômico",
    "economicos" to "econômicos",
    "edificacoes" to "edificações",
    "educacao" to "educação",
    "eletrica" to "elétrica",
    "eletricas" to "elétricas",
    "eletrico" to "elétrico",
    "eletricos" to "elétricos",
    "eletrodinamica" to "eletrodinâmica",
    "eletromagnetica" to "eletromagnética",
    "eletromagnetico" to "eletromagnético",
    "eletromagnetismo" to "eletromagnetismo",
    "eletronica" to "eletrônica",
    "eletronicas" to "eletrônicas",
    "eletronico" to "eletrônico",
    "eletronicos" to "eletrônicos",
    "eletroquimica" to "eletroquímica",
    "emergencia" to "emergência",
    "equacao" to "equação",
    "equacoes" to "equações",
    "equilibrio" to "equilíbrio",
    "espaco" to "espaço",
    "espacos" to "espaços",
    "estatistica" to "estatística",
    "estatisticas" to "estatísticas",
    "estatistico" to "estatístico",
    "estatisticos" to "estatísticos",
    "estatica" to "estática",
    "estatico" to "estático",
    "estagio" to "estágio",
    "etica" to "ética",
    "evolucao" to "evolução",
    "extensao" to "extensão",
    "extracao" to "extração",
    "familia" to "família",
    "farmaceutica" to "farmacêutica",
    "farmaceuticas" to "farmacêuticas",
    "farmaceutico" to "farmacêutico",
    "farmacia" to "farmácia",
    "fenomenos" to "fenômenos",
    "filosofia" to "filosofia",
    "fisica" to "física",
    "fisicas" to "físicas",
    "fisico" to "físico",
    "fisicos" to "físicos",
    "frances" to "francês",
    "funcao" to "função",
    "funcoes" to "funções",
    "fundicao" to "fundição",
    "genetica" to "genética",
    "geofisica" to "geofísica",
    "geologica" to "geológica",
    "geologico" to "geológico",
    "geoquimica" to "geoquímica",
    "gestao" to "gestão",
    "ginastica" to "ginástica",
    "grafica" to "gráfica",
    "graficas" to "gráficas",
    "grafico" to "gráfico",
    "graficos" to "gráficos",
    "hidraulica" to "hidráulica",
    "hidraulico" to "hidráulico",
    "hidrodinamica" to "hidrodinâmica",
    "historia" to "história",
    "historica" to "histórica",
    "historicas" to "históricas",
    "historico" to "histórico",
    "historicos" to "históricos",
    "indigenas" to "indígenas",
    "informacao" to "informação",
    "informatica" to "informática",
    "ingles" to "inglês",
    "inglesa" to "inglesa",
    "iniciacao" to "iniciação",
    "inovacao" to "inovação",
    "inorganica" to "inorgânica",
    "instalacao" to "instalação",
    "instalacoes" to "instalações",
    "instrumentacao" to "instrumentação",
    "integracao" to "integração",
    "inteligencia" to "inteligência",
    "introducao" to "introdução",
    "juridica" to "jurídica",
    "juridicas" to "jurídicas",
    "juridico" to "jurídico",
    "juridicos" to "jurídicos",
    "laboratorio" to "laboratório",
    "laboratorios" to "laboratórios",
    "legislacao" to "legislação",
    "linguistica" to "linguística",
    "logica" to "lógica",
    "logicas" to "lógicas",
    "logico" to "lógico",
    "logicos" to "lógicos",
    "manutencao" to "manutenção",
    "maquinas" to "máquinas",
    "matematica" to "matemática",
    "matematicas" to "matemáticas",
    "matematico" to "matemático",
    "matematicos" to "matemáticos",
    "mecanica" to "mecânica",
    "mecanicas" to "mecânicas",
    "mecanico" to "mecânico",
    "mecanicos" to "mecânicos",
    "media" to "média",
    "medica" to "médica",
    "medicas" to "médicas",
    "medicao" to "medição",
    "medico" to "médico",
    "medicos" to "médicos",
    "medio" to "médio",
    "memoria" to "memória",
    "metalurgica" to "metalúrgica",
    "metalurgicas" to "metalúrgicas",
    "metalurgico" to "metalúrgico",
    "metalurgicos" to "metalúrgicos",
    "metodologia" to "metodologia",
    "mineracao" to "mineração",
    "modulacao" to "modulação",
    "musica" to "música",
    "natacao" to "natação",
    "numerica" to "numérica",
    "numericas" to "numéricas",
    "numerico" to "numérico",
    "numericos" to "numéricos",
    "nutricao" to "nutrição",
    "obstetricia" to "obstetrícia",
    "operacao" to "operação",
    "operacoes" to "operações",
    "optica" to "óptica",
    "ordinaria" to "ordinária",
    "ordinarias" to "ordinárias",
    "ordinario" to "ordinário",
    "ordinarios" to "ordinários",
    "organica" to "orgânica",
    "organico" to "orgânico",
    "organizacao" to "organização",
    "orientacao" to "orientação",
    "oscilacao" to "oscilação",
    "oscilacoes" to "oscilações",
    "otica" to "ótica",
    "otimizacao" to "otimização",
    "padrao" to "padrão",
    "padroes" to "padrões",
    "patrimonio" to "patrimônio",
    "pavimentacao" to "pavimentação",
    "pediatrica" to "pediátrica",
    "pediatricas" to "pediátricas",
    "pediatrico" to "pediátrico",
    "pediatricos" to "pediátricos",
    "pneumatica" to "pneumática",
    "politica" to "política",
    "politicas" to "políticas",
    "portugues" to "português",
    "portuguesa" to "portuguesa",
    "potencia" to "potência",
    "pratica" to "prática",
    "praticas" to "práticas",
    "pratico" to "prático",
    "praticos" to "práticos",
    "pre" to "pré",
    "probabilidade" to "probabilidade",
    "producao" to "produção",
    "programacao" to "programação",
    "protecao" to "proteção",
    "publica" to "pública",
    "publicas" to "públicas",
    "publico" to "público",
    "publicos" to "públicos",
    "quantica" to "quântica",
    "quantico" to "quântico",
    "quimica" to "química",
    "quimicas" to "químicas",
    "quimico" to "químico",
    "quimicos" to "químicos",
    "reacao" to "reação",
    "reacoes" to "reações",
    "redacao" to "redação",
    "regulacao" to "regulação",
    "relacao" to "relação",
    "relacoes" to "relações",
    "relatorio" to "relatório",
    "relatorios" to "relatórios",
    "representacao" to "representação",
    "resistencia" to "resistência",
    "revisao" to "revisão",
    "robotica" to "robótica",
    "saude" to "saúde",
    "seguranca" to "segurança",
    "seminario" to "seminário",
    "seminarios" to "seminários",
    "serie" to "série",
    "series" to "séries",
    "servico" to "serviço",
    "servicos" to "serviços",
    "simulacao" to "simulação",
    "sistemas" to "sistemas",
    "solucao" to "solução",
    "solucoes" to "soluções",
    "tecnica" to "técnica",
    "tecnicas" to "técnicas",
    "tecnico" to "técnico",
    "tecnicos" to "técnicos",
    "tecnologia" to "tecnologia",
    "tecnologica" to "tecnológica",
    "tecnologico" to "tecnológico",
    "telecomunicacoes" to "telecomunicações",
    "teorica" to "teórica",
    "teoricas" to "teóricas",
    "teorico" to "teórico",
    "teoricos" to "teóricos",
    "termodinamica" to "termodinâmica",
    "termodinamico" to "termodinâmico",
    "termoquimica" to "termoquímica",
    "topicos" to "tópicos",
    "transmissao" to "transmissão",
    "urgencia" to "urgência",
    "variavel" to "variável",
    "variaveis" to "variáveis",
    // Docentes e nomes comuns em documentos da UFOP
    "andre" to "andré",
    "antonio" to "antônio",
    "araujo" to "araújo",
    "assuncao" to "assunção",
    "barbara" to "bárbara",
    "cesar" to "césar",
    "claudio" to "cláudio",
    "conceicao" to "conceição",
    "correa" to "corrêa",
    "fabio" to "fábio",
    "fabiola" to "fabíola",
    "flavio" to "flávio",
    "glicia" to "glícia",
    "goncalves" to "gonçalves",
    "ines" to "inês",
    "jessica" to "jéssica",
    "joao" to "joão",
    "jose" to "josé",
    "leticia" to "letícia",
    "lucio" to "lúcio",
    "marcio" to "márcio",
    "mario" to "mário",
    "mendonca" to "mendonça",
    "monica" to "mônica",
    "patricia" to "patrícia",
    "rogerio" to "rogério",
    "savio" to "sávio",
    "sergio" to "sérgio",
    "veronica" to "verônica",
    "vinicius" to "vinícius",
    "vitor" to "vítor"
)

private val DIACRITICS_REGEX = Regex("\\p{M}+")
private val WHITESPACE_SPLIT_REGEX = Regex("""\s+""")
private val COURSE_CODE_TOKEN_REGEX = Regex("""^[A-Z]{2,4}\d{2,4}[A-Z]?$""")
private val ASSESSMENT_CODE_TOKEN_REGEX = Regex("""^[A-Z]{1,5}\d{1,4}[A-Z]?$""")

internal fun stripDiacriticsLower(value: String): String =
    java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replace(DIACRITICS_REGEX, "")
        .lowercase()

private fun capitalizeFirstPt(word: String): String =
    word.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }

private fun applyPortugueseSuffixAccents(asciiLower: String): String {
    if (asciiLower.length < 6) return asciiLower
    return when {
        asciiLower.endsWith("acoes") && asciiLower.length >= 7 -> asciiLower.dropLast(5) + "ações"
        asciiLower.endsWith("acao") -> asciiLower.dropLast(4) + "ação"
        asciiLower.endsWith("icoes") && asciiLower.length >= 7 -> asciiLower.dropLast(5) + "ições"
        asciiLower.endsWith("icao") -> asciiLower.dropLast(4) + "ição"
        asciiLower.endsWith("ucoes") && asciiLower.length >= 7 -> asciiLower.dropLast(5) + "uções"
        asciiLower.endsWith("ucao") -> asciiLower.dropLast(4) + "ução"
        asciiLower.endsWith("ocoes") && asciiLower.length >= 7 -> asciiLower.dropLast(5) + "oções"
        asciiLower.endsWith("ocao") -> asciiLower.dropLast(4) + "oção"
        asciiLower.endsWith("ecoes") && asciiLower.length >= 7 -> asciiLower.dropLast(5) + "eções"
        asciiLower.endsWith("ecao") -> asciiLower.dropLast(4) + "eção"
        asciiLower.endsWith("encoes") && asciiLower.length >= 8 -> asciiLower.dropLast(6) + "enções"
        asciiLower.endsWith("encao") && asciiLower.length >= 7 -> asciiLower.dropLast(5) + "enção"
        else -> asciiLower
    }
}

private fun normalizeUfopWordCore(
    core: String,
    isFirstToken: Boolean,
    prevAsciiLower: String?
): String {
    if (core.isEmpty()) return core
    if (core.contains('-')) {
        return core.split('-').mapIndexed { subIdx, part ->
            if (part.isEmpty()) part
            else normalizeUfopWordCore(
                core = part,
                isFirstToken = (isFirstToken && subIdx == 0) || subIdx > 0,
                prevAsciiLower = null
            )
        }.joinToString("-")
    }
    val upper = core.uppercase()
    if (upper in UFOP_ROMAN_NUMERALS) return upper
    if (upper in UFOP_PRESERVED_ACRONYMS) return upper
    if (COURSE_CODE_TOKEN_REGEX.matches(upper)) return upper
    if (ASSESSMENT_CODE_TOKEN_REGEX.matches(upper)) return upper

    val asciiLower = stripDiacriticsLower(core)
    if (prevAsciiLower == "introducao" && asciiLower == "as") return "às"

    if (!isFirstToken && asciiLower in UFOP_LOWERCASE_PARTICLES) {
        return when (core.lowercase()) {
            "à" -> "à"
            "às" -> "às"
            else -> asciiLower
        }
    }

    UFOP_PORTUGUESE_WORD_MAP[asciiLower]?.let { mapped ->
        return capitalizeFirstPt(mapped)
    }

    val hasNonAsciiLetter = core.any { it.isLetter() && it.code > 127 }
    val isMixedInternalCaps = core.length > 1 &&
        core.any { it.isLowerCase() } &&
        core.drop(1).any { it.isUpperCase() }
    if (isMixedInternalCaps) return core
    if (hasNonAsciiLetter) return capitalizeFirstPt(core.lowercase())

    val withSuffix = applyPortugueseSuffixAccents(asciiLower)
    return capitalizeFirstPt(withSuffix)
}

/**
 * Normaliza textos acadêmicos da UFOP (Atestados, Históricos, nomes de cursos e disciplinas),
 * aplicando Title Case em português e restaurando acentuação, cedilhas e crases.
 */
fun normalizeUfopPortugueseText(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val tokens = trimmed.split(WHITESPACE_SPLIT_REGEX).filter { it.isNotEmpty() }
    var prevAsciiLower: String? = null
    return tokens.mapIndexed { idx, token ->
        if (token.equals("prof.ª", ignoreCase = true)) {
            prevAsciiLower = "prof"
            return@mapIndexed "Prof.ª"
        }
        val firstAlnum = token.indexOfFirst { it.isLetterOrDigit() }
        val lastAlnum = token.indexOfLast { it.isLetterOrDigit() }
        if (firstAlnum < 0 || lastAlnum < firstAlnum) {
            prevAsciiLower = null
            return@mapIndexed token
        }
        val prefix = token.substring(0, firstAlnum)
        val core = token.substring(firstAlnum, lastAlnum + 1)
        val suffix = token.substring(lastAlnum + 1)
        val coreAsciiLower = stripDiacriticsLower(core)

        val normalizedCore = if (suffix.startsWith(".") && coreAsciiLower in UFOP_ABBREVIATION_TITLES) {
            capitalizeFirstPt(coreAsciiLower)
        } else {
            normalizeUfopWordCore(core, isFirstToken = (idx == 0), prevAsciiLower = prevAsciiLower)
        }
        prevAsciiLower = coreAsciiLower
        "$prefix$normalizedCore$suffix"
    }.joinToString(" ")
}

internal fun deriveCourseCodeFallback(
    explicitCode: String?,
    notes: String,
    courseId: String,
    courseName: String
): String {
    val direct = explicitCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
    if (direct != null) return direct

    val fromNotes = Regex("""C[oó]digo:\s*([A-Z0-9]{3,10})""", RegexOption.IGNORE_CASE)
        .find(notes)
        ?.groupValues
        ?.getOrNull(1)
        ?.uppercase()
    if (!fromNotes.isNullOrBlank()) return fromNotes

    val fromName = Regex("""^\s*([A-Z]{2,4}\d{3,4}[A-Z]?)\b""", RegexOption.IGNORE_CASE)
        .find(courseName)
        ?.groupValues
        ?.getOrNull(1)
        ?.uppercase()
    if (!fromName.isNullOrBlank()) return fromName

    val fromId = Regex("""^([a-z]{2,4}\d{3,4}[a-z]?)(?:_|$)""", RegexOption.IGNORE_CASE)
        .find(courseId.trim())
        ?.groupValues
        ?.getOrNull(1)
        ?.uppercase()
    if (!fromId.isNullOrBlank()) return fromId

    val tokens = stripDiacriticsLower(courseName)
        .split(Regex("""[^a-z0-9]+"""))
        .filter { it.isNotEmpty() && it !in UFOP_LOWERCASE_PARTICLES }
    if (tokens.isNotEmpty()) {
        val lastUpper = tokens.last().uppercase()
        val hasTrailingNumeral = tokens.size >= 2 &&
            (lastUpper in UFOP_ROMAN_NUMERALS || lastUpper.all { it.isDigit() })
        val words = if (hasTrailingNumeral) tokens.dropLast(1) else tokens
        val suffix = if (hasTrailingNumeral) lastUpper else ""
        val base = when {
            words.size == 1 && suffix.isNotEmpty() -> words[0].uppercase().take((6 - suffix.length).coerceIn(3, 4))
            words.size == 1 -> words[0].uppercase().take(5)
            words.size == 2 -> (words[0].take(3) + words[1].take(3)).uppercase().take(if (suffix.isNotEmpty()) 4 else 6)
            words.size >= 4 && suffix.isEmpty() -> words.joinToString("") { it.take(1) }.uppercase().take(6)
            else -> words.joinToString("") { it.take(2) }.uppercase().take(if (suffix.isNotEmpty()) 4 else 6)
        }
        val combined = (base + suffix).take(6)
        if (combined.length >= 3) return combined
    }

    return courseId.uppercase().filter { it.isLetterOrDigit() }.take(6).ifBlank { "UFOP" }
}

private val DEPARTMENT_ACRONYM_SPLIT_REGEX = Regex("""[·\-–—/]""")

/**
 * Extrai apenas a sigla do departamento antes de separadores como `·`, `-` ou `/`.
 * Exemplo: `"DECOM · Ciência da Computação"` -> `"DECOM"`.
 */
fun extractDepartmentAcronym(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return trimmed
        .split(DEPARTMENT_ACRONYM_SPLIT_REGEX)
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?: trimmed
}

/**
 * Formata títulos de avaliações (provas, trabalhos, listas) para exibição visual amigável.
 * Converte CAIXA ALTA importada de PDFs (ex.: "TRABALHO CIRCUITOS DIGITAIS" ->
 * "Trabalho Circuitos Digitais") mantendo siglas (EDO, P1, P2, TP1, TCC, BCC721),
 * algarismos romanos (I..XII) e partículas em minúsculo (de, da, do, em).
 */
fun formatAcademicAssessmentTitle(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val hasLower = trimmed.any { it.isLetter() && it.isLowerCase() }
    val hasUpper = trimmed.any { it.isLetter() && it.isUpperCase() }
    if (hasLower && hasUpper) {
        // Texto digitado manualmente com maiúsculas/minúsculas mistas: preserva estilo do usuário,
        // garantindo apenas siglas/códigos acadêmicos explícitos quando em minúsculo.
        val tokens = trimmed.split(WHITESPACE_SPLIT_REGEX).filter { it.isNotEmpty() }
        return tokens.joinToString(" ") { token ->
            val firstAlnum = token.indexOfFirst { it.isLetterOrDigit() }
            val lastAlnum = token.indexOfLast { it.isLetterOrDigit() }
            if (firstAlnum < 0 || lastAlnum < firstAlnum) return@joinToString token
            val prefix = token.substring(0, firstAlnum)
            val core = token.substring(firstAlnum, lastAlnum + 1)
            val suffix = token.substring(lastAlnum + 1)
            val upper = core.uppercase()
            val fixedCore = when {
                upper in UFOP_PRESERVED_ACRONYMS || ASSESSMENT_CODE_TOKEN_REGEX.matches(upper) -> upper
                else -> core
            }
            "$prefix$fixedCore$suffix"
        }
    }
    return normalizeUfopPortugueseText(trimmed)
}
