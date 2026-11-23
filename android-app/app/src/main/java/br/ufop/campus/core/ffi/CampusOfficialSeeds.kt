package br.ufop.campus.core.ffi

import java.util.Locale

/** v5 (Sprint 9): persistência atômica com backup .bak, darkThemePreference, exportFolderConfig e faltas negativas base. */
    const val PERSISTED_SCHEMA_VERSION = 5
    const val DEFAULT_EXPORT_FOLDER_LABEL = "Documentos/VidaAcademica/Grades"
    const val WEATHER_REFRESH_INTERVAL_MS = 300_000L
    const val WEATHER_TIMEOUT_MS = 2_500
    const val COMMITMENT_COLOR_HEX = "#8E95A2"
    val PT_BR_LOCALE: Locale = Locale("pt", "BR")
    val APPROVED_STATUSES = setOf("AP", "AE", "DP", "DE", "DJ", "EA", "PF")
    val FAILED_STATUSES = setOf("RN", "RF", "NF")
    val GENERIC_REPLACEABLE_ROOMS = setOf(
        "",
        "aula teórica",
        "aula teorica",
        "aula prática",
        "aula pratica",
        "sala",
        "sala 101"
    )
    val NOTES_TURMA_REGEX = Regex("""Turma\s+([0-9A-Za-z]+)""", RegexOption.IGNORE_CASE)
    val EAD_INDICATOR_REGEX = Regex(
        """\b(?:EAD|CEAD|AVA|REMOT[AO]|ONLINE|VIRTUAL|MOODLE)\b|\bE\.A\.D\.?(?!\w)|\b[AÀ]\s+DIST[AÂ]NCIA\b""",
        RegexOption.IGNORE_CASE
    )
    val UFOP_COURSE_CODE_PATTERN = Regex("""\b([A-Z]{2,4}\d{3,4}[A-Z]?)\b""", RegexOption.IGNORE_CASE)
    val NON_COURSE_CODE_PREFIXES = setOf(
        "ISO", "NBR", "UTF", "SHA", "AES", "API", "SDK", "JDK", "HTTP", "HTML",
        "XML", "JSON", "CSS", "SQL", "CEP", "CPF", "CNPJ", "MEC", "DOU", "CEPE", "PDF"
    )
    val SYLLABUS_PREREQ_LINE_REGEX = Regex(
        """\b(?:pre-?requisit|co-?requisit|equivalen|bibliografia|referencias|resolucao|portaria|edital|isbn|issn|doi)\b""",
        RegexOption.IGNORE_CASE
    )

internal data class CourseSeed(
    val courseId: String,
    val semester: String,
    val name: String,
    val departmentId: String,
    val departmentName: String,
    val defaultColorHex: String,
    val workloadHours: Int,
    val baseAbsences: Int,
    val maxAbsences: Int,
    val baseCancelledClasses: Int,
    val professor: String,
    val buildingAndRoom: String,
    val shortLocation: String,
    val assessments: List<AssessmentItem>,
    val initialNotes: String,
    val code: String = ""
)

internal data class SlotSeed(
    val slotId: String,
    val courseId: String,
    val dayOfWeek: Int, // 1=SEG .. 6=SAB
    val startTime: String,
    val endTime: String,
    val building: String,
    val room: String,
    val shortLocation: String,
    val defaultTempC: Int = 22,
    val weatherCondition: String = "Ensolarado",
    /** Quantidade de aulas de 50 min agrupadas neste horário (faltas por sessão). */
    val classWeight: Int = 1
)

internal data class TemplateSlotSeed(
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val building: String,
    val room: String,
    val shortLocation: String
)

internal data class TemplateCourseSeed(
    val courseId: String,
    val name: String,
    val departmentId: String,
    val professor: String,
    val workloadHours: Int,
    val colorOverrideHex: String?,
    val notes: String,
    val slots: List<TemplateSlotSeed>
)

internal data class UfopTemplateSeed(
    val id: String,
    val code: String,
    val name: String,
    val departmentName: String,
    val campusName: String,
    val description: String,
    val courses: List<TemplateCourseSeed>
)

internal data class CommitmentSeed(
    val id: String,
    val isoDate: String,
    val title: String,
    val startTime: String,
    val endTime: String,
    val location: String,
    val subtitle: String,
    val colorHex: String,
    val weatherTempC: Int
)

internal val departmentSwatches = listOf(
    DepartmentColorOption(id = "deabi", name = "DEABI · Estudos Africanos, Afro-Brasileiros e Indígenas", colorHex = "#C2410C"),
    DepartmentColorOption(id = "deacl", name = "DEACL · Análises Clínicas", colorHex = "#0284C7"),
    DepartmentColorOption(id = "deali", name = "DEALI · Alimentos", colorHex = "#16A34A"),
    DepartmentColorOption(id = "deamb", name = "DEAMB · Eng. Ambiental", colorHex = "#7C3AED"),
    DepartmentColorOption(id = "dearq", name = "DEARQ · Arquitetura e Urbanismo", colorHex = "#DB2777"),
    DepartmentColorOption(id = "deart", name = "DEART · Artes Cênicas", colorHex = "#D97706"),
    DepartmentColorOption(id = "debio", name = "DEBIO · Biodiversidade, Evolução e Meio Ambiente", colorHex = "#14B8A6"),
    DepartmentColorOption(id = "decad", name = "DECAD · Ciências Administrativas", colorHex = "#4338CA"),
    DepartmentColorOption(id = "decat", name = "DECAT · Eng. de Controle e Automação", colorHex = "#2E7D32"),
    DepartmentColorOption(id = "decbi", name = "DECBI · Ciências Biológicas", colorHex = "#E11D48"),
    DepartmentColorOption(id = "decea", name = "DECEA · Ciências Exatas e Aplicadas", colorHex = "#2563EB"),
    DepartmentColorOption(id = "decgp", name = "DECGP · Cirurgia, Ginecologia e Obstetrícia", colorHex = "#9333EA"),
    DepartmentColorOption(id = "deciv", name = "DECIV · Eng. Civil", colorHex = "#B45309"),
    DepartmentColorOption(id = "decom", name = "DECOM · Ciência da Computação", colorHex = "#6E56CF"),
    DepartmentColorOption(id = "decpa", name = "DECPA · Clínicas Pediátrica e do Adulto", colorHex = "#0891B2"),
    DepartmentColorOption(id = "decsi", name = "DECSI · Computação e Sistemas", colorHex = "#047857"),
    DepartmentColorOption(id = "decso", name = "DECSO · Ciências Sociais", colorHex = "#DC2626"),
    DepartmentColorOption(id = "dedir", name = "DEDIR · Direito", colorHex = "#1D4ED8"),
    DepartmentColorOption(id = "deeco", name = "DEECO · Ciências Econômicas", colorHex = "#CA8A04"),
    DepartmentColorOption(id = "deedu", name = "DEEDU · Educação", colorHex = "#BE185D"),
    DepartmentColorOption(id = "deefd", name = "DEEFD · Educação Física", colorHex = "#15803D"),
    DepartmentColorOption(id = "deelt", name = "DEELT · Eng. Elétrica", colorHex = "#EA580C"),
    DepartmentColorOption(id = "deema", name = "DEEMA · Educação Matemática", colorHex = "#4F46E5"),
    DepartmentColorOption(id = "deenp", name = "DEENP · Eng. de Produção (ICEA)", colorHex = "#0F766E"),
    DepartmentColorOption(id = "deest", name = "DEEST · Estatística", colorHex = "#8B5CF6"),
    DepartmentColorOption(id = "deete", name = "DEETE · Educação e Tecnologias", colorHex = "#0369A1"),
    DepartmentColorOption(id = "defar", name = "DEFAR · Farmácia", colorHex = "#65A30D"),
    DepartmentColorOption(id = "defil", name = "DEFIL · Filosofia", colorHex = "#A21CAF"),
    DepartmentColorOption(id = "defis", name = "DEFIS · Física", colorHex = "#8D5B16"),
    DepartmentColorOption(id = "degeo", name = "DEGEO · Geologia", colorHex = "#9F1239"),
    DepartmentColorOption(id = "degep", name = "DEGEP · Gestão Pública", colorHex = "#06B6D4"),
    DepartmentColorOption(id = "dehis", name = "DEHIS · História", colorHex = "#A16207"),
    DepartmentColorOption(id = "deish", name = "DEISH · Saúde e Humanidades", colorHex = "#1E3A8A"),
    DepartmentColorOption(id = "dejor", name = "DEJOR · Jornalismo", colorHex = "#F97316"),
    DepartmentColorOption(id = "delet", name = "DELET · Letras", colorHex = "#6D28D9"),
    DepartmentColorOption(id = "demat", name = "DEMAT · Matemática", colorHex = "#3B68FF"),
    DepartmentColorOption(id = "demec", name = "DEMEC · Eng. Mecânica", colorHex = "#475569"),
    DepartmentColorOption(id = "demet", name = "DEMET · Eng. Metalúrgica e de Materiais", colorHex = "#B91C1C"),
    DepartmentColorOption(id = "demin", name = "DEMIN · Eng. de Minas", colorHex = "#00897B"),
    DepartmentColorOption(id = "demsc", name = "DEMSC · Medicina de Família e Saúde Coletiva", colorHex = "#C026D3"),
    DepartmentColorOption(id = "demul", name = "DEMUL · Museologia", colorHex = "#7C2D12"),
    DepartmentColorOption(id = "demus", name = "DEMUS · Música", colorHex = "#38BDF8"),
    DepartmentColorOption(id = "dencs", name = "DENCS · Nutrição Clínica e Social", colorHex = "#4D7C0F"),
    DepartmentColorOption(id = "depro", name = "DEPRO · Eng. de Produção", colorHex = "#5B21B6"),
    DepartmentColorOption(id = "dequi", name = "DEQUI · Química", colorHex = "#EC4899"),
    DepartmentColorOption(id = "desso", name = "DESSO · Serviço Social", colorHex = "#006064"),
    DepartmentColorOption(id = "detur", name = "DETUR · Turismo", colorHex = "#F59E0B"),
    DepartmentColorOption(id = "deurb", name = "DEURB · Eng. Urbana", colorHex = "#166534")
)

internal val departmentById = departmentSwatches.associateBy { it.id }

internal val legacyDepartmentAliases = mapOf(
    "math" to "demat",
    "comp" to "decom",
    "cat" to "decat",
    "geom" to "decat",
    "phys" to "defis",
    "em" to "demin",
    "deedf" to "deefd",
    "eedf" to "deefd",
    "deped" to "deedu",
    "ped" to "deedu",
    "denut" to "dencs",
    "nut" to "dencs",
    "demed" to "demsc",
    "med" to "demsc",
    "deadm" to "decad",
    "adm" to "decad"
)

/** Prédios oficiais de ensino da UFOP (exclui DTI, Centro de Vivência e Proinfra). */
internal val ufopBuildingsCatalog = listOf(
    // Ouro Preto (op_cruzeiro)
    UfopBuildingOption("op_iceb_1", "op_cruzeiro", "Ouro Preto", "ICEB (I)", "Instituto de Ciências Exatas e Biológicas I"),
    UfopBuildingOption("op_iceb_2", "op_cruzeiro", "Ouro Preto", "ICEB (II)", "Instituto de Ciências Exatas e Biológicas II"),
    UfopBuildingOption("op_iceb_3", "op_cruzeiro", "Ouro Preto", "ICEB (III)", "Instituto de Ciências Exatas e Biológicas III"),
    UfopBuildingOption("op_escola_minas", "op_cruzeiro", "Ouro Preto", "Escola de Minas", "Escola de Minas (Prédio Principal)"),
    UfopBuildingOption("op_em_predio_14", "op_cruzeiro", "Ouro Preto", "Escola de Minas · Prédio 14", "Escola de Minas · Prédio 14 (DEPRO)"),
    UfopBuildingOption("op_degeo", "op_cruzeiro", "Ouro Preto", "DEGEO", "Departamento de Geologia (Mirante)"),
    UfopBuildingOption("op_emed", "op_cruzeiro", "Ouro Preto", "EMED", "Escola de Medicina"),
    UfopBuildingOption("op_efar", "op_cruzeiro", "Ouro Preto", "EFAR", "Escola de Farmácia"),
    UfopBuildingOption("op_edtm", "op_cruzeiro", "Ouro Preto", "EDTM", "Escola de Direito, Turismo e Museologia"),
    UfopBuildingOption("op_enut", "op_cruzeiro", "Ouro Preto", "ENUT", "Escola de Nutrição"),
    UfopBuildingOption("op_deart", "op_cruzeiro", "Ouro Preto", "DEART", "Departamento de Artes Cênicas"),
    UfopBuildingOption("op_blocos_salas", "op_cruzeiro", "Ouro Preto", "Blocos de Salas de Aula", "Blocos de Salas de Aula (Morro do Cruzeiro)"),
    UfopBuildingOption("op_cedufop", "op_cruzeiro", "Ouro Preto", "CEDUFOP", "Centro Desportivo (CEDUFOP)"),
    // Mariana (mar_ichs)
    UfopBuildingOption("mar_ichs", "mar_ichs", "Mariana", "ICHS", "Instituto de Ciências Humanas e Sociais (Rua do Seminário)"),
    UfopBuildingOption("mar_icsa", "mar_ichs", "Mariana", "ICSA", "Instituto de Ciências Sociais Aplicadas (Rua do Catete, 166)"),
    // João Monlevade (jm_icea)
    UfopBuildingOption("jm_icea_c", "jm_icea", "João Monlevade", "ICEA · Bloco C", "ICEA · Bloco C (João Monlevade)"),
    UfopBuildingOption("jm_icea_d", "jm_icea", "João Monlevade", "ICEA · Bloco D", "ICEA · Bloco D (João Monlevade)"),
    UfopBuildingOption("jm_icea_e", "jm_icea", "João Monlevade", "ICEA · Bloco E", "ICEA · Bloco E (João Monlevade)"),
    UfopBuildingOption("jm_icea_h", "jm_icea", "João Monlevade", "ICEA · Bloco H", "ICEA · Bloco H (João Monlevade)"),
    UfopBuildingOption("ufop_ead", "ead", "EAD", "Ensino a Distância (EAD)", "Ensino a Distância (AVA / Moodle / Remoto)")
)

internal val ufopCampusesSeed = listOf(
    UfopCampusInfo(
        id = "op_cruzeiro",
        name = "Campus Morro do Cruzeiro",
        city = "Ouro Preto",
        weatherTempC = 22,
        weatherCondition = "Ensolarado",
        isActive = true
    ),
    UfopCampusInfo(
        id = "mar_ichs",
        name = "Campus ICHS / ICSA",
        city = "Mariana",
        weatherTempC = 25,
        weatherCondition = "Parcialmente nublado",
        isActive = false
    ),
    UfopCampusInfo(
        id = "jm_icea",
        name = "Campus ICEA",
        city = "João Monlevade",
        weatherTempC = 26,
        weatherCondition = "Ensolarado",
        isActive = false
    )
)

internal val calendarEventsSeed = listOf(
    // ── 2026.2 (24 eventos · Resolução CUNI 2945) ─────────────────────────────────────────────
    AcademicCalendarEvent(
        id = "cal_2026_2_ajuste1",
        semester = "2026.2",
        eventDate = "2026-08-14",
        dayNumber = "14",
        monthShort = "AGO",
        title = "1º Atestado e 1º Ajuste de Matrícula (14 a 16/08)",
        category = "Matrícula"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_ajuste2",
        semester = "2026.2",
        eventDate = "2026-08-20",
        dayNumber = "20",
        monthShort = "AGO",
        title = "2º Atestado e 2º Ajuste de Matrícula",
        category = "Matrícula"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_inicio",
        semester = "2026.2",
        eventDate = "2026-08-24",
        dayNumber = "24",
        monthShort = "AGO",
        title = "Início das Aulas e do Período Letivo 2026.2 (3º Atestado)",
        category = "Semestre"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_ajuste3",
        semester = "2026.2",
        eventDate = "2026-08-27",
        dayNumber = "27",
        monthShort = "AGO",
        title = "3º Ajuste de Matrículas Online",
        category = "Matrícula"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_ajuste4",
        semester = "2026.2",
        eventDate = "2026-09-02",
        dayNumber = "02",
        monthShort = "SET",
        title = "4º e Último Ajuste de Matrículas Online",
        category = "Matrícula"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_definitivo",
        semester = "2026.2",
        eventDate = "2026-09-03",
        dayNumber = "03",
        monthShort = "SET",
        title = "Divulgação do Atestado Definitivo (4º Ajuste)",
        category = "Matrícula"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_independencia",
        semester = "2026.2",
        eventDate = "2026-09-07",
        dayNumber = "07",
        monthShort = "SET",
        title = "Feriado Nacional: Independência do Brasil",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_trancamento_oficial",
        semester = "2026.2",
        eventDate = "2026-09-22",
        dayNumber = "22",
        monthShort = "SET",
        title = "25% do Semestre · Último dia para Trancamento (2026.2)",
        category = "Prazo"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_aparecida",
        semester = "2026.2",
        eventDate = "2026-10-12",
        dayNumber = "12",
        monthShort = "OUT",
        title = "Feriado Nacional: Nossa Senhora Aparecida",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_saberes_jm",
        semester = "2026.2",
        eventDate = "2026-10-26",
        dayNumber = "26",
        monthShort = "OUT",
        title = "Encontro de Saberes e SNCT em João Monlevade (26 e 27/10 · sem avaliações)",
        category = "Evento"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_servidor",
        semester = "2026.2",
        eventDate = "2026-10-28",
        dayNumber = "28",
        monthShort = "OUT",
        title = "Recesso Acadêmico: Dia do Servidor Público",
        category = "Recesso"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_snct_op",
        semester = "2026.2",
        eventDate = "2026-10-29",
        dayNumber = "29",
        monthShort = "OUT",
        title = "Semana Nacional de Ciência e Tecnologia em Ouro Preto (sem avaliações)",
        category = "Evento"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_finados",
        semester = "2026.2",
        eventDate = "2026-11-02",
        dayNumber = "02",
        monthShort = "NOV",
        title = "Feriado Nacional: Dia de Finados",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_saberes",
        semester = "2026.2",
        eventDate = "2026-11-10",
        dayNumber = "10",
        monthShort = "NOV",
        title = "Encontro de Saberes UFOP (10 a 12/11 · sem avaliações)",
        category = "Evento"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_dois_tercos",
        semester = "2026.2",
        eventDate = "2026-11-13",
        dayNumber = "13",
        monthShort = "NOV",
        title = "Decorridos 2/3 do Semestre Letivo",
        category = "Semestre"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_republica",
        semester = "2026.2",
        eventDate = "2026-11-15",
        dayNumber = "15",
        monthShort = "NOV",
        title = "Feriado Nacional: Proclamação da República",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_consciencia",
        semester = "2026.2",
        eventDate = "2026-11-20",
        dayNumber = "20",
        monthShort = "NOV",
        title = "Feriado Nacional: Dia da Consciência Negra",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_75_pct",
        semester = "2026.2",
        eventDate = "2026-11-24",
        dayNumber = "24",
        monthShort = "NOV",
        title = "Decorridos 75% do Semestre Letivo",
        category = "Semestre"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_conceicao",
        semester = "2026.2",
        eventDate = "2026-12-08",
        dayNumber = "08",
        monthShort = "DEZ",
        title = "Dia de Nossa Senhora da Conceição",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_exame_bcc701",
        semester = "2026.2",
        eventDate = "2026-12-11",
        dayNumber = "11",
        monthShort = "DEZ",
        title = "Exame Especial de BCC701 (Programação de Computadores)",
        category = "Avaliação"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_exames_oficiais",
        semester = "2026.2",
        eventDate = "2026-12-17",
        dayNumber = "17",
        monthShort = "DEZ",
        title = "Período de Exames Especiais (17 a 23/12)",
        category = "Avaliação"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_termino",
        semester = "2026.2",
        eventDate = "2026-12-23",
        dayNumber = "23",
        monthShort = "DEZ",
        title = "Término do Período Letivo 2026.2 e Lançamento de Notas",
        category = "Semestre"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_recesso",
        semester = "2026.2",
        eventDate = "2026-12-24",
        dayNumber = "24",
        monthShort = "DEZ",
        title = "Recesso Acadêmico de Fim de Ano (24 a 31/12)",
        category = "Recesso"
    ),
    AcademicCalendarEvent(
        id = "cal_2026_2_alteracao_notas",
        semester = "2026.2",
        eventDate = "2027-01-11",
        dayNumber = "11",
        monthShort = "JAN",
        title = "Último dia para Alteração de Notas de 2026.2",
        category = "Prazo"
    ),

    // ── 2027.1 (21 eventos · Calendário Presencial UFOP 2027 v5) ──────────────────────────────
    AcademicCalendarEvent(
        id = "cal_2027_1_carnaval",
        semester = "2027.1",
        eventDate = "2027-02-08",
        dayNumber = "08",
        monthShort = "FEV",
        title = "Carnaval e Quarta-feira de Cinzas (08 a 10/02 · Feriado/Ponto Facultativo)",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_ajuste1",
        semester = "2027.1",
        eventDate = "2027-02-12",
        dayNumber = "12",
        monthShort = "FEV",
        title = "1º Atestado e 1º Ajuste de Matrícula (12 a 14/02)",
        category = "Matrícula"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_ajuste2",
        semester = "2027.1",
        eventDate = "2027-02-18",
        dayNumber = "18",
        monthShort = "FEV",
        title = "2º Atestado e 2º Ajuste de Matrícula",
        category = "Matrícula"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_inicio",
        semester = "2027.1",
        eventDate = "2027-02-22",
        dayNumber = "22",
        monthShort = "FEV",
        title = "Início das Aulas e do Período Letivo 2027.1 (3º Atestado)",
        category = "Semestre"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_ajuste3",
        semester = "2027.1",
        eventDate = "2027-02-26",
        dayNumber = "26",
        monthShort = "FEV",
        title = "3º Ajuste de Matrículas Online",
        category = "Matrícula"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_ajuste4",
        semester = "2027.1",
        eventDate = "2027-03-01",
        dayNumber = "01",
        monthShort = "MAR",
        title = "4º Atestado e 4º (Último) Ajuste de Matrículas Online",
        category = "Matrícula"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_definitivo",
        semester = "2027.1",
        eventDate = "2027-03-02",
        dayNumber = "02",
        monthShort = "MAR",
        title = "Divulgação do Atestado Definitivo de Matrícula",
        category = "Matrícula"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_trancamento",
        semester = "2027.1",
        eventDate = "2027-03-22",
        dayNumber = "22",
        monthShort = "MAR",
        title = "25% do Semestre · Último dia para Trancamento (2027.1)",
        category = "Prazo"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_semana_santa",
        semester = "2027.1",
        eventDate = "2027-03-25",
        dayNumber = "25",
        monthShort = "MAR",
        title = "Semana Santa: Quinta, Sexta-feira Santa e Sábado de Aleluia (25 a 27/03)",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_tiradentes",
        semester = "2027.1",
        eventDate = "2027-04-21",
        dayNumber = "21",
        monthShort = "ABR",
        title = "Feriado Nacional: Dia de Tiradentes",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_50_pct",
        semester = "2027.1",
        eventDate = "2027-04-24",
        dayNumber = "24",
        monthShort = "ABR",
        title = "Decorridos 50% do Semestre Letivo",
        category = "Semestre"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_aniv_jm_ip",
        semester = "2027.1",
        eventDate = "2027-04-29",
        dayNumber = "29",
        monthShort = "ABR",
        title = "Feriado Municipal em João Monlevade e Ipatinga (sem aulas nesses campi)",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_trabalhador",
        semester = "2027.1",
        eventDate = "2027-05-01",
        dayNumber = "01",
        monthShort = "MAI",
        title = "Feriado Nacional: Dia do Trabalhador",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_dois_tercos",
        semester = "2027.1",
        eventDate = "2027-05-15",
        dayNumber = "15",
        monthShort = "MAI",
        title = "Decorridos 2/3 do Semestre Letivo",
        category = "Semestre"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_aacc",
        semester = "2027.1",
        eventDate = "2027-05-17",
        dayNumber = "17",
        monthShort = "MAI",
        title = "Aproveitamento de Carga Horária (AACC/Extensão · 17 a 31/05)",
        category = "Prazo"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_75_pct",
        semester = "2027.1",
        eventDate = "2027-05-25",
        dayNumber = "25",
        monthShort = "MAI",
        title = "Decorridos 75% do Semestre Letivo",
        category = "Semestre"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_corpus",
        semester = "2027.1",
        eventDate = "2027-05-27",
        dayNumber = "27",
        monthShort = "MAI",
        title = "Corpus Christi e Recesso Acadêmico (27 a 29/05)",
        category = "Recesso"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_termino",
        semester = "2027.1",
        eventDate = "2027-06-26",
        dayNumber = "26",
        monthShort = "JUN",
        title = "Término do Período Letivo 2027.1 (e Exame Especial BCC104)",
        category = "Semestre"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_exames",
        semester = "2027.1",
        eventDate = "2027-06-28",
        dayNumber = "28",
        monthShort = "JUN",
        title = "Período de Exames Especiais (28/06 a 03/07)",
        category = "Avaliação"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_notas_recesso",
        semester = "2027.1",
        eventDate = "2027-07-05",
        dayNumber = "05",
        monthShort = "JUL",
        title = "Lançamento de Notas e Início do Recesso Acadêmico (05 a 31/07)",
        category = "Recesso"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_1_alteracao_notas",
        semester = "2027.1",
        eventDate = "2027-07-07",
        dayNumber = "07",
        monthShort = "JUL",
        title = "Último dia para Alteração de Notas de 2027.1",
        category = "Prazo"
    ),

    // ── 2027.2 (24 eventos · Calendário Presencial UFOP 2027 v5) ──────────────────────────────
    AcademicCalendarEvent(
        id = "cal_2027_2_ajuste1",
        semester = "2027.2",
        eventDate = "2027-07-23",
        dayNumber = "23",
        monthShort = "JUL",
        title = "1º Atestado e 1º Ajuste de Matrícula (23 a 25/07)",
        category = "Matrícula"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_ajuste2",
        semester = "2027.2",
        eventDate = "2027-07-29",
        dayNumber = "29",
        monthShort = "JUL",
        title = "2º Atestado e 2º Ajuste de Matrícula",
        category = "Matrícula"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_inicio",
        semester = "2027.2",
        eventDate = "2027-08-02",
        dayNumber = "02",
        monthShort = "AGO",
        title = "Início das Aulas e do Período Letivo 2027.2 (3º Atestado)",
        category = "Semestre"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_ajuste3",
        semester = "2027.2",
        eventDate = "2027-08-05",
        dayNumber = "05",
        monthShort = "AGO",
        title = "3º Ajuste de Matrículas Online",
        category = "Matrícula"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_ajuste4",
        semester = "2027.2",
        eventDate = "2027-08-06",
        dayNumber = "06",
        monthShort = "AGO",
        title = "4º Atestado e 4º (Último) Ajuste de Matrículas Online",
        category = "Matrícula"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_definitivo",
        semester = "2027.2",
        eventDate = "2027-08-09",
        dayNumber = "09",
        monthShort = "AGO",
        title = "Divulgação do Atestado Definitivo de Matrícula",
        category = "Matrícula"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_trancamento",
        semester = "2027.2",
        eventDate = "2027-08-30",
        dayNumber = "30",
        monthShort = "AGO",
        title = "25% do Semestre · Último dia para Trancamento (2027.2)",
        category = "Prazo"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_independencia",
        semester = "2027.2",
        eventDate = "2027-09-06",
        dayNumber = "06",
        monthShort = "SET",
        title = "Recesso Acadêmico e Independência do Brasil (06 e 07/09)",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_50_pct",
        semester = "2027.2",
        eventDate = "2027-09-30",
        dayNumber = "30",
        monthShort = "SET",
        title = "Decorridos 50% do Semestre Letivo",
        category = "Semestre"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_aparecida",
        semester = "2027.2",
        eventDate = "2027-10-11",
        dayNumber = "11",
        monthShort = "OUT",
        title = "Recesso Acadêmico e Nossa Senhora Aparecida (11 e 12/10)",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_saberes_jm",
        semester = "2027.2",
        eventDate = "2027-10-19",
        dayNumber = "19",
        monthShort = "OUT",
        title = "Encontro de Saberes em João Monlevade (19 e 20/10 · sem avaliações)",
        category = "Evento"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_dois_tercos",
        semester = "2027.2",
        eventDate = "2027-10-23",
        dayNumber = "23",
        monthShort = "OUT",
        title = "Decorridos 2/3 do Semestre Letivo",
        category = "Semestre"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_snct",
        semester = "2027.2",
        eventDate = "2027-10-26",
        dayNumber = "26",
        monthShort = "OUT",
        title = "Semana Nacional de Ciência e Tecnologia (26 e 27/10 · sem avaliações)",
        category = "Evento"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_servidor",
        semester = "2027.2",
        eventDate = "2027-10-28",
        dayNumber = "28",
        monthShort = "OUT",
        title = "Recesso Acadêmico e Dia do Servidor Público (28/10 a 01/11)",
        category = "Recesso"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_finados",
        semester = "2027.2",
        eventDate = "2027-11-02",
        dayNumber = "02",
        monthShort = "NOV",
        title = "Feriado Nacional: Dia de Finados",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_75_pct",
        semester = "2027.2",
        eventDate = "2027-11-06",
        dayNumber = "06",
        monthShort = "NOV",
        title = "Decorridos 75% do Semestre Letivo",
        category = "Semestre"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_saberes",
        semester = "2027.2",
        eventDate = "2027-11-09",
        dayNumber = "09",
        monthShort = "NOV",
        title = "Encontro de Saberes UFOP (09 a 11/11 · sem avaliações)",
        category = "Evento"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_republica",
        semester = "2027.2",
        eventDate = "2027-11-15",
        dayNumber = "15",
        monthShort = "NOV",
        title = "Feriado Nacional: Proclamação da República",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_consciencia",
        semester = "2027.2",
        eventDate = "2027-11-20",
        dayNumber = "20",
        monthShort = "NOV",
        title = "Feriado Nacional: Dia da Consciência Negra",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_conceicao",
        semester = "2027.2",
        eventDate = "2027-12-08",
        dayNumber = "08",
        monthShort = "DEZ",
        title = "Feriado: Dia de Nossa Senhora da Conceição",
        category = "Feriado"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_termino",
        semester = "2027.2",
        eventDate = "2027-12-11",
        dayNumber = "11",
        monthShort = "DEZ",
        title = "Término do Período Letivo 2027.2 (e Exame Especial BCC104)",
        category = "Semestre"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_exames",
        semester = "2027.2",
        eventDate = "2027-12-13",
        dayNumber = "13",
        monthShort = "DEZ",
        title = "Período de Exames Especiais (13 a 18/12)",
        category = "Avaliação"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_recesso_notas",
        semester = "2027.2",
        eventDate = "2027-12-20",
        dayNumber = "20",
        monthShort = "DEZ",
        title = "Início do Recesso Acadêmico e Último dia para Lançamento de Notas",
        category = "Recesso"
    ),
    AcademicCalendarEvent(
        id = "cal_2027_2_alteracao_notas",
        semester = "2027.2",
        eventDate = "2027-12-21",
        dayNumber = "21",
        monthShort = "DEZ",
        title = "Último dia para Alteração de Notas de 2027.2",
        category = "Prazo"
    )
)

internal val ufopTemplatesSeed = listOf(
    UfopTemplateSeed(
        id = "tpl_bcc_decom",
        code = "BCC · DECOM",
        name = "Ciência da Computação",
        departmentName = "DECOM",
        campusName = "Morro do Cruzeiro · Ouro Preto",
        description = "Grade base com Programação, Estruturas de Dados, Cálculo e Física.",
        courses = listOf(
            TemplateCourseSeed(
                courseId = "bcc201",
                name = "BCC201 Programação I",
                departmentId = "decom",
                professor = "Prof. Rafael Lima",
                workloadHours = 60,
                colorOverrideHex = null,
                notes = "Fundamentos de lógica e programação em C.",
                slots = listOf(
                    TemplateSlotSeed(1, "08:00", "09:40", "ICEB (I)", "Sala 102", "ICEB (I) · 102"),
                    TemplateSlotSeed(3, "08:00", "09:40", "ICEB (I)", "Sala 102", "ICEB (I) · 102")
                )
            ),
            TemplateCourseSeed(
                courseId = "bcc202",
                name = "BCC202 Estruturas de Dados I",
                departmentId = "decom",
                professor = "Prof.ª Camila Souza",
                workloadHours = 60,
                colorOverrideHex = null,
                notes = "Listas, pilhas, filas, árvores e complexidade.",
                slots = listOf(
                    TemplateSlotSeed(2, "10:00", "11:40", "ICEB (III)", "Laboratório 03", "ICEB (III) · Lab 03"),
                    TemplateSlotSeed(4, "10:00", "11:40", "ICEB (III)", "Laboratório 03", "ICEB (III) · Lab 03")
                )
            ),
            TemplateCourseSeed(
                courseId = "mtm122",
                name = "MTM122 Cálculo Diferencial e Integral I",
                departmentId = "demat",
                professor = "Prof.ª Ana Martins",
                workloadHours = 90,
                colorOverrideHex = null,
                notes = "Limites, derivadas e introdução a integrais.",
                slots = listOf(
                    TemplateSlotSeed(2, "08:00", "09:40", "ICEB (I)", "Sala 204", "ICEB (I) · 204"),
                    TemplateSlotSeed(4, "08:00", "09:40", "ICEB (I)", "Sala 204", "ICEB (I) · 204"),
                    TemplateSlotSeed(5, "08:00", "09:40", "ICEB (I)", "Sala 204", "ICEB (I) · 204")
                )
            ),
            TemplateCourseSeed(
                courseId = "fis105",
                name = "FIS105 Física I",
                departmentId = "defis",
                professor = "Prof.ª Helena Costa",
                workloadHours = 60,
                colorOverrideHex = null,
                notes = "Cinemática e mecânica clássica.",
                slots = listOf(
                    TemplateSlotSeed(1, "10:00", "11:40", "ICEB (II)", "Laboratório 01", "ICEB (II) · Lab 01"),
                    TemplateSlotSeed(3, "14:00", "15:00", "ICEB (II)", "Laboratório 01", "ICEB (II) · Lab 01")
                )
            )
        )
    ),
    UfopTemplateSeed(
        id = "tpl_ec_decsi",
        code = "ENGC · DECSI",
        name = "Engenharia de Computação",
        departmentName = "DECSI",
        campusName = "ICEA · João Monlevade",
        description = "Grade do ICEA com Circuitos Digitais, Algoritmos e Cálculo.",
        courses = listOf(
            TemplateCourseSeed(
                courseId = "csi102",
                name = "CSI102 Programação II",
                departmentId = "decsi",
                professor = "Prof. Bruno Oliveira",
                workloadHours = 60,
                colorOverrideHex = null,
                notes = "Estruturas lineares e recursão.",
                slots = listOf(
                    TemplateSlotSeed(1, "08:00", "09:40", "ICEA · Bloco C", "Laboratório 02", "ICEA C · Lab 02"),
                    TemplateSlotSeed(3, "08:00", "09:40", "ICEA · Bloco C", "Laboratório 02", "ICEA C · Lab 02")
                )
            ),
            TemplateCourseSeed(
                courseId = "csi203",
                name = "CSI203 Circuitos Digitais",
                departmentId = "decsi",
                professor = "Prof. Eduardo Mendes",
                workloadHours = 60,
                colorOverrideHex = null,
                notes = "Portas lógicas, mapas de Karnaugh e flip-flops.",
                slots = listOf(
                    TemplateSlotSeed(2, "10:00", "11:40", "ICEA · Bloco D", "Sala D12", "ICEA D · D12"),
                    TemplateSlotSeed(4, "10:00", "11:40", "ICEA · Bloco D", "Sala D12", "ICEA D · D12")
                )
            ),
            TemplateCourseSeed(
                courseId = "cea101",
                name = "CEA101 Cálculo I",
                departmentId = "decea",
                professor = "Prof.ª Fernanda Rocha",
                workloadHours = 90,
                colorOverrideHex = null,
                notes = "Funções reais, limites e derivadas.",
                slots = listOf(
                    TemplateSlotSeed(2, "08:00", "09:40", "ICEA · Bloco E", "Sala E04", "ICEA E · E04"),
                    TemplateSlotSeed(4, "08:00", "09:40", "ICEA · Bloco E", "Sala E04", "ICEA E · E04"),
                    TemplateSlotSeed(5, "08:00", "09:40", "ICEA · Bloco E", "Sala E04", "ICEA E · E04")
                )
            ),
            TemplateCourseSeed(
                courseId = "cea204",
                name = "CEA204 Física Experimental I",
                departmentId = "decea",
                professor = "Prof. Lucas Ferreira",
                workloadHours = 30,
                colorOverrideHex = null,
                notes = "Práticas experimentais de mecânica.",
                slots = listOf(
                    TemplateSlotSeed(1, "10:00", "11:40", "ICEA · Bloco H", "Lab Física", "ICEA H · Lab Fís"),
                    TemplateSlotSeed(3, "14:00", "15:00", "ICEA · Bloco H", "Lab Física", "ICEA H · Lab Fís")
                )
            )
        )
    ),
    UfopTemplateSeed(
        id = "tpl_eng_basico",
        code = "CICLO BÁSICO · EM",
        name = "Engenharias (Ciclo Básico)",
        departmentName = "Escola de Minas",
        campusName = "Morro do Cruzeiro · Ouro Preto",
        description = "Núcleo comum da Escola de Minas: Cálculo, Álgebra Linear, Física e Computação.",
        courses = listOf(
            TemplateCourseSeed(
                courseId = "mtm122_em",
                name = "MTM122 Cálculo I",
                departmentId = "demat",
                professor = "Prof.ª Ana Martins",
                workloadHours = 90,
                colorOverrideHex = null,
                notes = "Funções reais, derivadas e integrais.",
                slots = listOf(
                    TemplateSlotSeed(1, "08:00", "09:40", "Escola de Minas", "Sala 101", "EM · 101"),
                    TemplateSlotSeed(3, "08:00", "09:40", "Escola de Minas", "Sala 101", "EM · 101"),
                    TemplateSlotSeed(5, "08:00", "09:40", "Escola de Minas", "Sala 101", "EM · 101")
                )
            ),
            TemplateCourseSeed(
                courseId = "mtm112_em",
                name = "MTM112 Álgebra Linear",
                departmentId = "demat",
                professor = "Prof. Marcos Vieira",
                workloadHours = 60,
                colorOverrideHex = null,
                notes = "Matrizes, sistemas lineares e espaços vetoriais.",
                slots = listOf(
                    TemplateSlotSeed(2, "08:00", "09:40", "Blocos de Salas de Aula", "Sala 105", "Blocos · 105"),
                    TemplateSlotSeed(4, "08:00", "09:40", "Blocos de Salas de Aula", "Sala 105", "Blocos · 105")
                )
            ),
            TemplateCourseSeed(
                courseId = "fis105_em",
                name = "FIS105 Física I",
                departmentId = "defis",
                professor = "Prof.ª Helena Costa",
                workloadHours = 60,
                colorOverrideHex = null,
                notes = "Leis de Newton, trabalho e conservação de energia.",
                slots = listOf(
                    TemplateSlotSeed(1, "10:00", "11:40", "ICEB (II)", "Sala 02", "ICEB (II) · 02"),
                    TemplateSlotSeed(3, "10:00", "11:40", "ICEB (II)", "Sala 02", "ICEB (II) · 02")
                )
            ),
            TemplateCourseSeed(
                courseId = "bcc701_em",
                name = "BCC701 Programação de Computadores",
                departmentId = "decom",
                professor = "Prof. Rafael Lima",
                workloadHours = 60,
                colorOverrideHex = null,
                notes = "Algoritmos básicos e Python para engenharia.",
                slots = listOf(
                    TemplateSlotSeed(2, "10:00", "11:40", "ICEB (III)", "Laboratório 01", "ICEB (III) · Lab 01"),
                    TemplateSlotSeed(4, "14:00", "15:00", "ICEB (III)", "Laboratório 01", "ICEB (III) · Lab 01")
                )
            )
        )
    )
)

internal val courseCodePrefixToDeptId = mapOf(
    "ABI" to "deabi",
    "ACL" to "deacl",
    "ALI" to "deali",
    "AMB" to "deamb",
    "ARQ" to "dearq",
    "ART" to "deart",
    "BIO" to "debio",
    "CAD" to "decad",
    "ADM" to "decad",
    "CAT" to "decat",
    "CBI" to "decbi",
    "CEA" to "decea",
    "CGP" to "decgp",
    "CIV" to "deciv",
    "BCC" to "decom",
    "COM" to "decom",
    "CPA" to "decpa",
    "CSI" to "decsi",
    "CSO" to "decso",
    "DIR" to "dedir",
    "ECO" to "deeco",
    "PED" to "deedu",
    "EDU" to "deedu",
    "EFD" to "deefd",
    "EDF" to "deefd",
    "ELT" to "deelt",
    "EMA" to "deema",
    "ENP" to "deenp",
    "EST" to "deest",
    "ETE" to "deete",
    "FAR" to "defar",
    "FIL" to "defil",
    "FIS" to "defis",
    "GEO" to "degeo",
    "GEP" to "degep",
    "HIS" to "dehis",
    "ISH" to "deish",
    "JOR" to "dejor",
    "LET" to "delet",
    "MTM" to "demat",
    "MAT" to "demat",
    "MEC" to "demec",
    "MET" to "demet",
    "MIN" to "demin",
    "MED" to "demsc",
    "MSC" to "demsc",
    "MUL" to "demul",
    "MUS" to "demus",
    "NUT" to "dencs",
    "NCS" to "dencs",
    "PRO" to "depro",
    "EPR" to "depro",
    "QUI" to "dequi",
    "SSO" to "desso",
    "TUR" to "detur",
    "URB" to "deurb"
)

internal fun defaultCourseSeeds(): LinkedHashMap<String, CourseSeed> = linkedMapOf(
    "calc2" to CourseSeed(
        courseId = "calc2",
        semester = "2026.2",
        name = "Cálculo II",
        departmentId = "demat",
        departmentName = "DEMAT · Matemática",
        defaultColorHex = "#3B68FF",
        workloadHours = 60,
        baseAbsences = 4,
        maxAbsences = 18,
        baseCancelledClasses = 0,
        professor = "Prof.ª Ana Martins",
        buildingAndRoom = "Prédio A · Sala 204",
        shortLocation = "A · 204",
        assessments = listOf(
            AssessmentItem(
                id = "assess_calc2_1",
                courseId = "calc2",
                dayNumber = "20",
                monthShort = "NOV",
                title = "Lista de integrais",
                kind = "Trabalho",
                dueTime = "18:00",
                dueDate = "2026-11-20",
                completed = false
            ),
            AssessmentItem(
                id = "assess_calc2_2",
                courseId = "calc2",
                dayNumber = "24",
                monthShort = "NOV",
                title = "Aplicações de integrais",
                kind = "Seminário",
                dueTime = "16:00",
                dueDate = "2026-11-24",
                completed = false
            ),
            AssessmentItem(
                id = "assess_calc2_3",
                courseId = "calc2",
                dayNumber = "27",
                monthShort = "NOV",
                title = "Prova 2",
                kind = "Prova",
                dueTime = "18:00",
                dueDate = "2026-11-27",
                completed = false
            )
        ),
        initialNotes = "Revisar integrais por partes."
    ),
    "alg" to CourseSeed(
        courseId = "alg",
        semester = "2026.2",
        name = "Algoritmos",
        departmentId = "decom",
        departmentName = "DECOM · Ciência da Computação",
        defaultColorHex = "#6E56CF",
        workloadHours = 60,
        baseAbsences = 2,
        maxAbsences = 18,
        baseCancelledClasses = 0,
        professor = "Prof. Rafael Lima",
        buildingAndRoom = "Prédio C · Laboratório 03",
        shortLocation = "C · Lab 03",
        assessments = listOf(
            AssessmentItem(
                id = "assess_alg_1",
                courseId = "alg",
                dayNumber = "21",
                monthShort = "NOV",
                title = "Implementação de Árvore AVL",
                kind = "Trabalho",
                dueTime = "23:59",
                dueDate = "2026-11-21",
                completed = false
            ),
            AssessmentItem(
                id = "assess_alg_2",
                courseId = "alg",
                dayNumber = "01",
                monthShort = "DEZ",
                title = "Prova de Grafos",
                kind = "Prova",
                dueTime = "10:00",
                dueDate = "2026-12-01",
                completed = false
            )
        ),
        initialNotes = "Praticar árvores balanceadas e grafos."
    ),
    "alglin" to CourseSeed(
        courseId = "alglin",
        semester = "2026.2",
        name = "Álgebra Linear",
        departmentId = "demat",
        departmentName = "DEMAT · Matemática",
        defaultColorHex = "#3B68FF",
        workloadHours = 90,
        baseAbsences = 1,
        maxAbsences = 27,
        baseCancelledClasses = 0,
        professor = "Prof. Marcos Vieira",
        buildingAndRoom = "Prédio B · Sala 105",
        shortLocation = "B · 105",
        assessments = listOf(
            AssessmentItem(
                id = "assess_alglin_1",
                courseId = "alglin",
                dayNumber = "23",
                monthShort = "NOV",
                title = "Lista de Autovalores e Autovetores",
                kind = "Trabalho",
                dueTime = "18:00",
                dueDate = "2026-11-23",
                completed = false
            ),
            AssessmentItem(
                id = "assess_alglin_2",
                courseId = "alglin",
                dayNumber = "02",
                monthShort = "DEZ",
                title = "Prova 2 - Diagonalização",
                kind = "Prova",
                dueTime = "08:00",
                dueDate = "2026-12-02",
                completed = false
            )
        ),
        initialNotes = "Estudar autovalores, autovetores e diagonalização."
    ),
    "fis1" to CourseSeed(
        courseId = "fis1",
        semester = "2026.2",
        name = "Física I",
        departmentId = "defis",
        departmentName = "DEFIS · Física",
        defaultColorHex = "#8D5B16",
        workloadHours = 30,
        baseAbsences = 3,
        maxAbsences = 9,
        baseCancelledClasses = 0,
        professor = "Prof.ª Helena Costa",
        buildingAndRoom = "Prédio D · Laboratório 01",
        shortLocation = "D · Lab 01",
        assessments = listOf(
            AssessmentItem(
                id = "assess_fis1_1",
                courseId = "fis1",
                dayNumber = "25",
                monthShort = "NOV",
                title = "Relatório Lab 01 - Cinemática",
                kind = "Trabalho",
                dueTime = "18:00",
                dueDate = "2026-11-25",
                completed = false
            ),
            AssessmentItem(
                id = "assess_fis1_2",
                courseId = "fis1",
                dayNumber = "04",
                monthShort = "DEZ",
                title = "Prova de Leis de Newton",
                kind = "Prova",
                dueTime = "10:00",
                dueDate = "2026-12-04",
                completed = false
            )
        ),
        initialNotes = "Lista de cinemática e leis de Newton."
    )
)

internal fun defaultSlotSeeds(): MutableList<SlotSeed> = mutableListOf(
    // Segunda-feira (1)
    SlotSeed("slot_seg_0800_alglin", "alglin", 1, "08:00", "09:40", "Prédio B", "Sala 105", "B · 105", 21, "Ensolarado"),
    SlotSeed("slot_seg_1000_fis1", "fis1", 1, "10:00", "11:40", "Prédio D", "Laboratório 01", "D · Lab 01", 23, "Ensolarado"),
    // Terça-feira (2) - Dia principal do PDF (17 de novembro)
    SlotSeed("slot_ter_0800_calc2", "calc2", 2, "08:00", "09:40", "Prédio A", "Sala 204", "A · 204", 22, "Ensolarado"),
    SlotSeed("slot_ter_1000_alg", "alg", 2, "10:00", "11:40", "Prédio C", "Laboratório 03", "C · Lab 03", 24, "Ensolarado"),
    // Quarta-feira (3)
    SlotSeed("slot_qua_0800_alglin", "alglin", 3, "08:00", "09:40", "Prédio B", "Sala 105", "B · 105", 22, "Ensolarado"),
    SlotSeed("slot_qua_1000_fis1", "fis1", 3, "10:00", "11:40", "Prédio D", "Laboratório 01", "D · Lab 01", 24, "Ensolarado"),
    // Quinta-feira (4)
    SlotSeed("slot_qui_0800_calc2", "calc2", 4, "08:00", "09:40", "Prédio A", "Sala 204", "A · 204", 22, "Ensolarado"),
    SlotSeed("slot_qui_1000_alg", "alg", 4, "10:00", "11:40", "Prédio C", "Laboratório 03", "C · Lab 03", 24, "Ensolarado"),
    // Sexta-feira (5)
    SlotSeed("slot_sex_0800_alglin", "alglin", 5, "08:00", "09:40", "Prédio B", "Sala 105", "B · 105", 23, "Ensolarado")
)

internal fun defaultCommitmentSeeds(): MutableList<CommitmentSeed> = mutableListOf(
    CommitmentSeed(
        id = "comm_2026-11-17_estudo",
        isoDate = "2026-11-17",
        title = "Grupo de estudo",
        startTime = "14:00",
        endTime = "15:00",
        location = "Biblioteca · Sala 02",
        subtitle = "Revisão de Cálculo II com a turma",
        colorHex = "#8E95A2",
        weatherTempC = 26
    )
)
