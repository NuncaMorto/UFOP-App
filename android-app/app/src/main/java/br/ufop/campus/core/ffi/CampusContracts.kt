package br.ufop.campus.core.ffi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import br.ufop.campus.core.curriculum.UfopRoomAllocationIndex
import br.ufop.campus.core.security.CampusCryptoEngine
import br.ufop.campus.core.security.SecurePdfProcessor
import br.ufop.campus.ui.theme.CampusAccessibilitySettings
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AttendanceStatus { ATTENDING, ABSENT, CANCELLED }
enum class TimelineItemKind { CLASS_SESSION, COMMITMENT }

data class DayPillItem(
    val isoDate: String,
    val dayShort: String,
    val dayNumber: Int
)

data class AgendaTimelineItem(
    val id: String,
    val slotId: String?,
    val courseId: String?,
    val kind: TimelineItemKind,
    val title: String,
    val startTime: String,
    val endTime: String,
    val location: String,
    val subtitle: String,
    val colorHex: String,
    val weatherTempC: Int?,
    val status: AttendanceStatus?
)

data class AgendaDayOverview(
    val isoDate: String,
    val monthYearLabel: String,
    val formattedDate: String,
    val weekNumber: Int,
    val weekDays: List<DayPillItem>,
    val classesCount: Int,
    val commitmentsCount: Int,
    val items: List<AgendaTimelineItem>
)

data class DepartmentColorOption(
    val id: String,
    val name: String,
    val colorHex: String
)

data class ClassCheckInDetail(
    val slotId: String,
    val courseId: String,
    val departmentId: String,
    val departmentName: String,
    val courseName: String,
    val formattedDate: String,
    val startTime: String,
    val endTime: String,
    val weatherTempC: Int,
    val weatherCondition: String,
    val buildingAndRoom: String,
    val professor: String,
    val selectedColorHex: String,
    val availableColors: List<DepartmentColorOption>,
    val status: AttendanceStatus
)

data class DisciplineSummaryCard(
    val courseId: String,
    val name: String,
    val departmentId: String,
    val departmentName: String,
    val colorHex: String,
    val workloadHours: Int,
    val currentAbsences: Int,
    val maxAbsences: Int,
    val cancelledClasses: Int,
    val code: String = ""
)

data class AssessmentItem(
    val id: String,
    val courseId: String,
    val dayNumber: String,
    val monthShort: String,
    val title: String,
    val kind: String,
    val dueTime: String,
    val dueDate: String = "2026-11-20",
    val completed: Boolean = false
)

data class DisciplineDetail(
    val summary: DisciplineSummaryCard,
    val semester: String,
    val assessments: List<AssessmentItem>,
    val notes: String,
    val professor: String = "",
    val buildingAndRoom: String = "",
    val shortLocation: String = "",
    val code: String = ""
)

data class TimetableCell(
    val slotId: String,
    val courseId: String,
    val courseName: String,
    val shortLocation: String,
    val departmentId: String,
    val departmentName: String,
    val colorHex: String,
    val code: String = ""
)

data class TimetableRow(
    val startTime: String,
    val endTime: String,
    val cellsByDay: Map<Int, TimetableCell?> // 1=SEG .. 6=SAB
)

data class WeeklyTimetable(
    val semester: String,
    val gridTitle: String,
    val days: List<Pair<Int, String>>, // (1, "SEG") .. (6, "SÁB")
    val rows: List<TimetableRow>,
    val legend: List<DepartmentColorOption>
)

data class AssessmentWithCourse(
    val assessment: AssessmentItem,
    val courseName: String,
    val departmentName: String,
    val colorHex: String
)

data class UfopCampusInfo(
    val id: String,
    val name: String,
    val city: String,
    val weatherTempC: Int,
    val weatherCondition: String,
    val isActive: Boolean
)

data class AcademicCalendarEvent(
    val id: String,
    val semester: String,
    val eventDate: String,
    val dayNumber: String,
    val monthShort: String,
    val title: String,
    val category: String
)

data class AcademicProfileSummary(
    val semester: String,
    val activeCampus: UfopCampusInfo,
    val campuses: List<UfopCampusInfo>,
    val totalCourses: Int,
    val totalAbsences: Int,
    val totalMaxAbsences: Int,
    val totalCancelledClasses: Int,
    val coursesAtRisk: Int,
    val pendingAssessments: Int,
    val completedAssessments: Int,
    val calendarEvents: List<AcademicCalendarEvent>
)

data class UfopCurriculumTemplate(
    val id: String,
    val code: String,
    val name: String,
    val departmentName: String,
    val campusName: String,
    val description: String,
    val coursesCount: Int
)

data class CourseScheduleSlotItem(
    val slotId: String,
    val courseId: String,
    val dayOfWeek: Int,
    val dayLabel: String,
    val startTime: String,
    val endTime: String,
    val building: String,
    val room: String,
    val shortLocation: String
)

// Sprint 4 models
data class AbsenceHistoryEntry(
    val sessionId: String,
    val slotId: String,
    val courseId: String,
    val sessionDate: String,
    val formattedDate: String,
    val startTime: String,
    val endTime: String,
    val status: AttendanceStatus,
    val classWeight: Int
)

data class CampusSearchResults(
    val query: String,
    val courses: List<DisciplineSummaryCard>,
    val assessments: List<AssessmentWithCourse>,
    val commitments: List<AgendaTimelineItem>
)

// Sprint 6 models
data class StudentProfile(
    val fullName: String = "",
    val registrationNumber: String = "",
    val courseName: String = "",
    val campusName: String = "Ouro Preto",
    val activeSemester: String = "2026.2",
    val semesterStartDate: String = "2026-08-24",
    val semesterEndDate: String = "2026-12-23",
    val avatarPath: String? = null,
    val onboardingCompleted: Boolean = false
)

data class FriendScheduleInfo(
    val id: String,
    val friendName: String,
    val courseName: String = "",
    val semester: String,
    val avatarBase64: String? = null,
    val timetable: WeeklyTimetable
)

data class DayActivityIndicator(
    val isoDate: String,
    val hasClasses: Boolean,
    val hasCommitments: Boolean,
    val hasAssessments: Boolean,
    val colorsHex: List<String>
)

// Sprint 7 models
data class UfopBuildingOption(
    val id: String,
    val campusId: String,
    val campusName: String,
    val shortName: String,
    val fullName: String
)

data class AcademicCalendarUpdatePackage(
    val version: String,
    val semester: String,
    val publishedBy: String,
    val publishedAt: String,
    val signatureSha256: String,
    val events: List<AcademicCalendarEvent>
)

// Sprint 8 models
data class TranscriptCourseEntry(
    val id: String,
    val semester: String,
    val code: String,
    val name: String,
    val workloadHours: Int,
    val grade: Double?,
    val absences: Int,
    val totalClasses: Int,
    val status: String,
    val professor: String = ""
)

data class SemesterCreSummary(
    val semester: String,
    val cre: Double,
    val totalWorkloadHours: Int,
    val approvedCount: Int,
    val failedCount: Int,
    val inProgressCount: Int,
    val courses: List<TranscriptCourseEntry>
)

data class AcademicCreReport(
    val overallCre: Double,
    val totalRequiredHours: Int = 3610,
    val completedHours: Int = 0,
    val totalApproved: Int = 0,
    val totalFailedByGrade: Int = 0,
    val totalFailedByAbsence: Int = 0,
    val semesters: List<SemesterCreSummary> = emptyList()
)

// Sprint 9 models
data class ExportFolderConfig(
    val treeUri: String? = null,
    val displayLabel: String = "Documentos/VidaAcademica/Grades"
)

// Sprint 12 models
data class SyllabusImportReport(
    val importedCount: Int,
    val matchedCourse: Boolean,
    val matchedCourseCode: String? = null,
    val matchedCourseTitle: String? = null,
    val extractedProfessor: String = "",
    val hasGradeDistribution: Boolean = false,
    val hasAssessmentDates: Boolean = false,
    val warningMessage: String? = null,
    val errorMessage: String? = null
)

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

private fun stripDiacriticsLower(value: String): String =
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

/**
 * Datas oficiais de início e término de aulas por semestre letivo da UFOP
 * (Resolução CUNI 2945 para 2026.2 e Calendário Presencial UFOP 2027 v5 para 2027.1 e 2027.2).
 */
fun resolveDefaultSemesterBounds(semester: String): Pair<String, String> =
    when (semester.trim().replace('/', '.')) {
        "2026.2" -> "2026-08-24" to "2026-12-23"
        "2027.1" -> "2027-02-22" to "2027-06-26"
        "2027.2" -> "2027-08-02" to "2027-12-11"
        else -> "2026-08-24" to "2026-12-23"
    }

interface CampusCoreRepository {
    val mutationVersion: StateFlow<Long>
    fun getAgendaDay(isoDate: String): AgendaDayOverview
    fun getClassCheckIn(slotId: String, isoDate: String): ClassCheckInDetail
    fun saveClassCheckIn(
        slotId: String,
        isoDate: String,
        status: AttendanceStatus,
        colorOverrideHex: String?
    ): DisciplineSummaryCard
    fun listDepartments(): List<DepartmentColorOption>
    fun listDisciplines(semester: String, departmentId: String?): List<DisciplineSummaryCard>
    fun getDisciplineDetail(courseId: String): DisciplineDetail
    fun updateDisciplineNotes(courseId: String, notes: String)
    fun getWeeklyTimetable(semester: String): WeeklyTimetable

    // Sprint 2 APIs
    fun createCommitment(
        title: String,
        description: String,
        commitmentDate: String,
        startTime: String,
        endTime: String,
        location: String,
        weatherTempC: Int? = 25
    ): AgendaTimelineItem

    fun deleteCommitment(commitmentId: String)

    fun createAssessment(
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String
    ): AssessmentItem

    fun toggleAssessmentCompleted(assessmentId: String, completed: Boolean): AssessmentItem

    fun deleteAssessment(assessmentId: String)

    fun listAllAssessments(
        semester: String,
        kindFilter: String? = null
    ): List<AssessmentWithCourse>

    fun getAcademicProfileSummary(semester: String = "2026.2"): AcademicProfileSummary

    fun setActiveCampus(campusId: String): UfopCampusInfo

    fun exportSchedulePayload(semester: String = "2026.2"): String

    fun importSchedulePayload(jsonPayload: String): Int

    // Sprint 3 APIs
    fun createCourse(
        semester: String,
        departmentId: String,
        name: String,
        professor: String,
        workloadHours: Int,
        colorOverrideHex: String? = null
    ): DisciplineSummaryCard

    fun deleteCourse(courseId: String)

    fun addScheduleSlot(
        courseId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ): CourseScheduleSlotItem

    fun deleteScheduleSlot(slotId: String)

    fun listCourseSlots(courseId: String): List<CourseScheduleSlotItem>

    fun listUfopTemplates(): List<UfopCurriculumTemplate>

    fun applyUfopTemplate(templateId: String, semester: String = "2026.2"): Int

    // Sprint 4 APIs
    fun listAbsenceHistory(courseId: String): List<AbsenceHistoryEntry>

    fun searchCampusItems(semester: String, query: String): CampusSearchResults

    fun resetSemesterToDefaultSeed(semester: String = "2026.2"): Int

    // Sprint 6 APIs
    /** Perfil do estudante (virgem/`onboardingCompleted = false` em instalação nova). */
    fun getStudentProfile(): StudentProfile

    /** Salva o perfil; se `campusName` for Ouro Preto/Mariana/João Monlevade sincroniza o campus ativo. */
    fun saveStudentProfile(profile: StudentProfile): StudentProfile

    /** Edita a disciplina; a cor é sempre derivada do departamento (paleta fixa UFOP). */
    fun updateCourse(
        courseId: String,
        name: String,
        professor: String,
        departmentId: String,
        workloadHours: Int
    ): DisciplineSummaryCard

    /** Edita um horário validando conflitos no mesmo semestre/dia (ignorando o próprio [slotId]). */
    fun updateScheduleSlot(
        slotId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ): CourseScheduleSlotItem

    fun updateCommitment(
        commitmentId: String,
        title: String,
        description: String,
        commitmentDate: String,
        startTime: String,
        endTime: String,
        location: String
    ): AgendaTimelineItem

    /** Indicadores por dia (1..N) do mês para o calendário semestral. */
    fun getMonthActivityIndicators(
        year: Int,
        month: Int,
        semester: String = getStudentProfile().activeSemester
    ): Map<Int, DayActivityIndicator>

    /** Semestres disponíveis (desc), ex.: `["2026.2", "2026.1"]`. */
    fun listAvailableSemesters(): List<String>

    /** Semestres disponíveis no Calendário Acadêmico UFOP (ordenados), ex.: `["2026.2", "2027.1", "2027.2"]`. */
    fun listAvailableCalendarSemesters(): List<String>

    /** Datas padrão (início, término) do período letivo oficial da UFOP para o semestre informado. */
    fun resolveDefaultSemesterBounds(semester: String): Pair<String, String> =
        br.ufop.campus.core.ffi.resolveDefaultSemesterBounds(semester)

    fun listFriendSchedules(): List<FriendScheduleInfo>

    /** Importa a grade de um amigo a partir do JSON gerado por [exportSchedulePayload]. */
    fun importFriendScheduleJson(friendName: String, jsonPayload: String): FriendScheduleInfo

    fun removeFriendSchedule(friendId: String)

    fun getFriendTimetable(friendId: String): WeeklyTimetable?

    /**
     * Atualização leve do clima (Open-Meteo), limitada a 1 requisição a cada 5 minutos.
     * Na main thread retorna o campus em cache imediatamente e atualiza em background
     * (incrementando [mutationVersion] quando o novo clima chega). Nunca falha offline.
     */
    fun refreshWeatherIfNeeded(force: Boolean = false): UfopCampusInfo

    /** Importa o `ATESTADO DE MATRÍCULA` (PDF) da UFOP. Retorna a quantidade de disciplinas importadas. */
    fun importEnrollmentCertificatePdf(pdfBytes: ByteArray, completeOnboarding: Boolean = true): Int

    /** Importa o texto bruto do `ATESTADO DE MATRÍCULA`. Retorna a quantidade de disciplinas importadas. */
    fun importEnrollmentCertificateText(rawText: String, completeOnboarding: Boolean = true): Int

    // Sprint 7 APIs
    /** Catálogo oficial dos prédios de ensino da UFOP (opcionalmente filtrado por campusId). */
    fun listUfopBuildings(campusId: String? = null): List<UfopBuildingOption>

    /** Importa datas de avaliações de um Plano de Ensino (PDF) para uma disciplina. */
    fun importCourseSyllabusPdf(courseId: String, pdfBytes: ByteArray): Int

    /** Importa datas de avaliações a partir do texto do Plano de Ensino para uma disciplina. */
    fun importCourseSyllabusText(courseId: String, rawText: String): Int

    /** Importa Plano de Ensino (PDF) com validação de disciplina, docente e critérios/datas. */
    fun importCourseSyllabusDetailedPdf(courseId: String, pdfBytes: ByteArray): SyllabusImportReport

    /** Importa Plano de Ensino (texto) com validação de disciplina, docente e critérios/datas. */
    fun importCourseSyllabusDetailedText(courseId: String, rawText: String): SyllabusImportReport

    /** Eventos do Calendário Acadêmico UFOP para o semestre informado. */
    fun getAcademicCalendarEvents(
        semester: String = getStudentProfile().activeSemester
    ): List<AcademicCalendarEvent>

    /** Indica se o modo administrador do calendário está desbloqueado. */
    fun isAdminUnlocked(): Boolean

    /** Desbloqueia o modo administrador usando o PIN oficial. */
    fun unlockAdminMode(pin: String): Boolean

    /** Publica e assina (SHA-256) um PDF oficial do Calendário Acadêmico UFOP. */
    fun adminPublishCalendarPdf(
        pdfBytes: ByteArray,
        semester: String = getStudentProfile().activeSemester
    ): AcademicCalendarUpdatePackage

    /** Serializa o pacote assinado ativo ou mais recente do Calendário Acadêmico. */
    fun exportCalendarUpdateJson(): String

    /** Valida a assinatura SHA-256 de um pacote `.ufopcal.json` e o prepara para aplicação. */
    fun importCalendarUpdateJson(jsonPayload: String): AcademicCalendarUpdatePackage

    /** Retorna o pacote de atualização de calendário pendente para o estudante aplicar, se houver. */
    fun getPendingCalendarUpdate(): AcademicCalendarUpdatePackage?

    /** Aplica os eventos do pacote pendente no calendário do semestre e retorna a quantidade aplicada. */
    fun applyPendingCalendarUpdate(): Int

    /** Exporta a grade do estudante no formato `.ufopgrade` (com nome, curso e avatar Base64). */
    fun exportMyGradeBundleJson(
        semester: String = getStudentProfile().activeSemester
    ): String

    // Sprint 8 APIs
    /** Retorna apenas os departamentos distintos que possuem disciplinas no semestre informado. */
    fun listDepartmentsForSemester(semester: String): List<DepartmentColorOption>

    /** Calcula e retorna o relatório oficial de CRE e histórico acadêmico por semestre. */
    fun getAcademicCreReport(): AcademicCreReport

    /** Atualiza ou simula nota (`0.0..10.0`) e/ou faltas de uma disciplina no Histórico ou semestre atual. */
    fun updateTranscriptOrCourseGrade(
        semester: String,
        codeOrCourseId: String,
        grade: Double?,
        absences: Int? = null
    ): AcademicCreReport

    /** Importa o Histórico Escolar oficial da UFOP (PDF) e atualiza CRE, cursos e docentes. */
    fun importAcademicTranscriptPdf(pdfBytes: ByteArray): AcademicCreReport

    /** Importa o texto bruto extraído de um Histórico Escolar da UFOP e atualiza CRE, cursos e docentes. */
    fun importAcademicTranscriptText(rawText: String): AcademicCreReport

    // Sprint 9 APIs
    /** Atualiza manualmente o total de faltas de uma disciplina (inclusive reduzindo abaixo das faltas de check-in). */
    fun updateCourseAbsences(courseId: String, totalAbsences: Int): DisciplineSummaryCard

    /** Preferência de tema escuro: "SYSTEM", "DARK" ou "LIGHT". */
    fun getDarkThemePreference(): String

    /** Persiste a preferência de tema escuro ("SYSTEM", "DARK" ou "LIGHT"). */
    fun setDarkThemePreference(mode: String)

    /** Retorna a pasta de exportação de grades (.ufopgrade). */
    fun getExportFolderConfig(): ExportFolderConfig

    /** Atualiza e persiste a pasta de exportação de grades (.ufopgrade). */
    fun setExportFolderConfig(treeUri: String?, displayLabel: String?): ExportFolderConfig

    // Sprint 10 APIs
    /** Edita os campos de uma avaliação (inclusive permitindo mover para outra disciplina). */
    fun updateAssessment(
        assessmentId: String,
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String,
        completed: Boolean
    ): AssessmentItem

    /** Atualiza e persiste a carga horária total exigida do curso (em horas). */
    fun setTotalRequiredHours(hours: Int)

    // Sprint 11 APIs
    /** Preferência de cor de destaque do app: "GREEN", "RED", "ORANGE", "PURPLE", "YELLOW", "CYAN" ou "BROWN". */
    fun getAccentColorPreference(): String

    /** Persiste a preferência de cor de destaque. */
    fun setAccentColorPreference(accentKey: String)

    // Sprint 12 APIs
    fun getAccessibilitySettings(): CampusAccessibilitySettings
    fun setAccessibilitySettings(settings: CampusAccessibilitySettings)
    fun isGuidedTourCompleted(): Boolean
    fun setGuidedTourCompleted(completed: Boolean)
    fun getLastSeenReleaseVersion(): String
    fun setLastSeenReleaseVersion(version: String)
    fun exportFullAccountSnapshotJson(): String
    fun restoreFullAccountSnapshotJson(rawJson: String): Boolean
    fun wipeAllUserDataForLogout()

    // Sprint 14 APIs
    fun applyRoomAllocationIndex(index: UfopRoomAllocationIndex, enrichExisting: Boolean = true): Int
}

typealias InMemoryCampusCoreRepository = InMemoryCampusRepository

/**
 * Implementação em memória (fallback seguro) do CAMPUS (UFOP).
 * Usada pelo [CampusEngineBridgeRepository] quando a biblioteca nativa `campus_core` (UniFFI/JNA)
 * não está disponível, mantendo os seeds de `2026.2` (`002_seed_ufop_2026_2.sql`, `003`, `004`).
 */
class InMemoryCampusRepository(
    private val stateFile: File? = null,
    private val onMutation: (() -> Unit)? = null,
    private val onStatePersisted: (() -> Unit)? = null
) : CampusCoreRepository {

    private companion object {
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
    }

    private val lock = Any()
    private val _mutationVersion = MutableStateFlow(0L)
    override val mutationVersion: StateFlow<Long> = _mutationVersion.asStateFlow()

    private data class CourseSeed(
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

    private data class SlotSeed(
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

    private data class TemplateSlotSeed(
        val dayOfWeek: Int,
        val startTime: String,
        val endTime: String,
        val building: String,
        val room: String,
        val shortLocation: String
    )

    private data class TemplateCourseSeed(
        val courseId: String,
        val name: String,
        val departmentId: String,
        val professor: String,
        val workloadHours: Int,
        val colorOverrideHex: String?,
        val notes: String,
        val slots: List<TemplateSlotSeed>
    )

    private data class UfopTemplateSeed(
        val id: String,
        val code: String,
        val name: String,
        val departmentName: String,
        val campusName: String,
        val description: String,
        val courses: List<TemplateCourseSeed>
    )

    private data class CommitmentSeed(
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

    /** Catálogo oficial dos 48 departamentos UFOP com paleta fixa de alto contraste. */
    private val departmentSwatches = listOf(
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

    private val departmentById = departmentSwatches.associateBy { it.id }

    private val legacyDepartmentAliases = mapOf(
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
    private val ufopBuildingsCatalog = listOf(
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

    private val courses = linkedMapOf(
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

    private val slots = mutableListOf(
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

    private val ufopTemplatesSeed = listOf(
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

    private val commitments = mutableListOf(
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

    private val ufopCampusesSeed = listOf(
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

    private val calendarEventsSeed = listOf(
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

    // Estado mutável em memória sincronizado com mutações do usuário
    private val courseColorOverrides = mutableMapOf<String, String>()
    private val departmentColorOverrides = mutableMapOf<String, String>()
    private val checkInStatusBySessionKey = mutableMapOf<String, AttendanceStatus>()
    private val notesByCourseId = mutableMapOf<String, String>()
    private val assessmentsByCourseId = linkedMapOf<String, MutableList<AssessmentItem>>()
    private var activeCampusId: String = "op_cruzeiro"
    private var generatedIdCounter: Long = 100L

    // Sprint 6 state
    private data class FriendCourseEntry(
        val courseId: String,
        val name: String,
        val departmentId: String,
        val code: String = ""
    )

    private data class FriendSlotEntry(
        val slotId: String,
        val courseId: String,
        val dayOfWeek: Int,
        val startTime: String,
        val endTime: String,
        val shortLocation: String
    )

    private data class FriendScheduleEntry(
        val id: String,
        val friendName: String,
        val courseName: String = "",
        val semester: String,
        val avatarBase64: String? = null,
        val courses: List<FriendCourseEntry>,
        val slots: List<FriendSlotEntry>
    )

    private data class CampusWeather(val tempC: Int, val condition: String)

    private var studentProfile: StudentProfile = StudentProfile(onboardingCompleted = false)
    private val friendSchedules = mutableListOf<FriendScheduleEntry>()
    private val campusWeatherById = mutableMapOf<String, CampusWeather>()
    private var lastWeatherFetchEpochMs: Long = 0L
    private var lastWeatherAttemptEpochMs: Long = 0L
    @Volatile
    private var weatherFetchInFlight: Boolean = false

    // Sprint 7 state
    private val calendarEventsBySemester = linkedMapOf<String, MutableList<AcademicCalendarEvent>>()
    private var adminUnlocked: Boolean = false
    private var pendingCalendarUpdate: AcademicCalendarUpdatePackage? = null
    private var latestPublishedCalendarUpdate: AcademicCalendarUpdatePackage? = null

    // Sprint 8 state
    private val transcriptEntries = mutableListOf<TranscriptCourseEntry>()
    private var totalRequiredHours: Int = 3610
    private var importedCompletedHours: Int = 0

    // Sprint 9 state
    private var darkThemePreference: String = "SYSTEM"
    private var accentColorPreference: String = "GREEN"
    private var exportFolderTreeUri: String? = null
    private var exportFolderDisplayLabel: String = DEFAULT_EXPORT_FOLDER_LABEL

    // Sprint 12 state
    private var accessibilitySettings: CampusAccessibilitySettings = CampusAccessibilitySettings()
    private var guidedTourCompleted: Boolean = false
    private var lastSeenReleaseVersion: String = ""

    // Sprint 14 state
    private var roomAllocationIndex: UfopRoomAllocationIndex? = null

    private val defaultCoursesSnapshot = LinkedHashMap(courses)
    private val defaultSlotsSnapshot = slots.toList()
    private val defaultCommitmentsSnapshot = commitments.toList()

    init {
        // Instalação nova (sem arquivo de estado): começa VIRGEM, sem disciplinas/horários/compromissos.
        applyVirginStateLocked()
        if (stateFile != null) {
            loadPersistedStateOrFallbackLocked(stateFile)
        }
    }

    /**
     * Estado virgem (primeira execução): sem cursos, horários, sessões, compromissos ou avaliações,
     * perfil com `onboardingCompleted = false`. Mantém catálogo dos 48 departamentos, prédios e campi UFOP.
     */
    private fun applyVirginStateLocked() {
        courses.clear()
        slots.clear()
        commitments.clear()
        courseColorOverrides.clear()
        departmentColorOverrides.clear()
        checkInStatusBySessionKey.clear()
        notesByCourseId.clear()
        assessmentsByCourseId.clear()
        activeCampusId = "op_cruzeiro"
        generatedIdCounter = 100L
        studentProfile = StudentProfile(onboardingCompleted = false)
        friendSchedules.clear()
        campusWeatherById.clear()
        lastWeatherFetchEpochMs = 0L
        calendarEventsBySemester.clear()
        calendarEventsSeed.groupBy { it.semester }.forEach { (sem, list) ->
            calendarEventsBySemester[sem] = list.toMutableList()
        }
        adminUnlocked = false
        pendingCalendarUpdate = null
        latestPublishedCalendarUpdate = null
        transcriptEntries.clear()
        totalRequiredHours = 3610
        importedCompletedHours = 0
        darkThemePreference = "SYSTEM"
        accentColorPreference = "GREEN"
        exportFolderTreeUri = null
        exportFolderDisplayLabel = DEFAULT_EXPORT_FOLDER_LABEL
        accessibilitySettings = CampusAccessibilitySettings()
        guidedTourCompleted = false
        lastSeenReleaseVersion = ""
        departmentSwatches.forEach { swatch ->
            departmentColorOverrides[swatch.id] = swatch.colorHex
        }
    }

    private fun applyDefaultSeedStateLocked() {
        courses.clear()
        courses.putAll(defaultCoursesSnapshot)
        slots.clear()
        slots.addAll(defaultSlotsSnapshot)
        commitments.clear()
        commitments.addAll(defaultCommitmentsSnapshot)
        courseColorOverrides.clear()
        departmentColorOverrides.clear()
        checkInStatusBySessionKey.clear()
        notesByCourseId.clear()
        assessmentsByCourseId.clear()
        activeCampusId = "op_cruzeiro"
        generatedIdCounter = 100L
        transcriptEntries.clear()
        totalRequiredHours = 3610
        importedCompletedHours = 0
        calendarEventsBySemester.clear()
        calendarEventsSeed.groupBy { it.semester }.forEach { (sem, list) ->
            calendarEventsBySemester[sem] = list.toMutableList()
        }
        courses.values.forEach { seed ->
            courseColorOverrides[seed.courseId] = seed.defaultColorHex
            notesByCourseId[seed.courseId] = seed.initialNotes
            assessmentsByCourseId[seed.courseId] = seed.assessments.toMutableList()
        }
        departmentSwatches.forEach { swatch ->
            departmentColorOverrides[swatch.id] = swatch.colorHex
        }
    }

    override fun resetSemesterToDefaultSeed(semester: String): Int = synchronized(lock) {
        applyDefaultSeedStateLocked()
        studentProfile = studentProfile.copy(
            activeSemester = "2026.2",
            semesterStartDate = "2026-08-24",
            semesterEndDate = "2026-12-23",
            onboardingCompleted = true
        )
        persistStateLocked()
        _mutationVersion.value += 1L
        courses.values.count { it.semester == "2026.2" }
    }

    override fun listAbsenceHistory(courseId: String): List<AbsenceHistoryEntry> = synchronized(lock) {
        val courseSlots = slots.filter { it.courseId == courseId }.associateBy { it.slotId }
        checkInStatusBySessionKey.entries
            .mapNotNull { (key, status) ->
                if (status == AttendanceStatus.ATTENDING) return@mapNotNull null
                val separator = key.lastIndexOf('_')
                if (separator <= 0) return@mapNotNull null
                val slotId = key.substring(0, separator)
                val isoDate = key.substring(separator + 1)
                val slot = courseSlots[slotId] ?: return@mapNotNull null
                AbsenceHistoryEntry(
                    sessionId = key,
                    slotId = slotId,
                    courseId = courseId,
                    sessionDate = isoDate,
                    formattedDate = formatPortugueseDate(isoDate),
                    startTime = slot.startTime,
                    endTime = slot.endTime,
                    status = status,
                    classWeight = if (status == AttendanceStatus.ABSENT) slot.classWeight.coerceAtLeast(1) else 0
                )
            }
            .sortedWith(
                compareByDescending<AbsenceHistoryEntry> { it.sessionDate }
                    .thenByDescending { it.startTime }
            )
    }

    private fun normalizeSearchText(value: String): String =
        java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .trim()

    override fun searchCampusItems(semester: String, query: String): CampusSearchResults {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return CampusSearchResults(query = trimmed, courses = emptyList(), assessments = emptyList(), commitments = emptyList())
        }
        val needle = normalizeSearchText(trimmed)
        fun matches(vararg fields: String): Boolean = fields.any { normalizeSearchText(it).contains(needle) }

        val matchedCourses = synchronized(lock) {
            courses.values
                .filter { it.semester == semester || semester.isBlank() }
                .filter { matches(it.name, it.professor, it.departmentName, it.courseId, resolveCourseCode(it)) }
                .map { buildDisciplineSummaryLocked(it) }
        }
        val matchedAssessments = listAllAssessments(semester, null)
            .filter { matches(it.assessment.title, it.assessment.kind, it.courseName, it.departmentName) }
        val matchedCommitments = synchronized(lock) {
            commitments
                .filter { matches(it.title, it.location, it.subtitle) }
                .map { comm ->
                    AgendaTimelineItem(
                        id = comm.id,
                        slotId = null,
                        courseId = null,
                        kind = TimelineItemKind.COMMITMENT,
                        title = comm.title,
                        startTime = comm.startTime,
                        endTime = comm.endTime,
                        location = comm.location,
                        subtitle = comm.subtitle,
                        colorHex = comm.colorHex,
                        weatherTempC = comm.weatherTempC,
                        status = null
                    )
                }
        }
        return CampusSearchResults(
            query = trimmed,
            courses = matchedCourses,
            assessments = matchedAssessments,
            commitments = matchedCommitments
        )
    }

    private fun sessionKey(slotId: String, isoDate: String): String = "${slotId}_$isoDate"

    private fun resolveCourseColor(course: CourseSeed): String {
        // Sprint 6/7: cor fixa derivada exclusivamente do departamento (ignora sobrescritas do usuário).
        return resolveDepartmentOption(course.departmentId).colorHex
    }

    private fun resolveDepartmentName(course: CourseSeed): String =
        resolveDepartmentOption(course.departmentId).name

    private fun resolveCourseCode(course: CourseSeed): String {
        val currentNotes = notesByCourseId[course.courseId] ?: course.initialNotes
        return deriveCourseCodeFallback(course.code, currentNotes, course.courseId, course.name)
    }

    private fun buildDisciplineSummaryLocked(course: CourseSeed): DisciplineSummaryCard {
        val courseSlotsById = slots.filter { it.courseId == course.courseId }.associateBy { it.slotId }
        var extraAbsences = 0
        var extraCancelled = 0

        for ((key, status) in checkInStatusBySessionKey) {
            val separator = key.lastIndexOf('_')
            if (separator <= 0) continue
            val matchedSlot = courseSlotsById[key.substring(0, separator)] ?: continue
            when (status) {
                AttendanceStatus.ATTENDING -> Unit
                AttendanceStatus.ABSENT -> extraAbsences += matchedSlot.classWeight.coerceAtLeast(1)
                AttendanceStatus.CANCELLED -> extraCancelled += 1
            }
        }

        val dept = resolveDepartmentOption(course.departmentId)
        return DisciplineSummaryCard(
            courseId = course.courseId,
            name = course.name,
            departmentId = dept.id,
            departmentName = dept.name,
            colorHex = dept.colorHex,
            workloadHours = course.workloadHours,
            currentAbsences = (course.baseAbsences + extraAbsences).coerceAtLeast(0),
            maxAbsences = course.maxAbsences,
            cancelledClasses = course.baseCancelledClasses + extraCancelled,
            code = resolveCourseCode(course)
        )
    }

    override fun getAgendaDay(isoDate: String): AgendaDayOverview = synchronized(lock) {
        val date = parseIsoDateOrToday(isoDate)
        val normalizedDate = date.toString()
        val dayOfWeek = date.dayOfWeek.value // 1=SEG .. 7=DOM
        val formattedDate = formatPortugueseDate(date)
        val activeSemester = studentProfile.activeSemester.ifBlank { "2026.2" }
        val campusWeather = activeCampusLocked()

        val semesterCourseIds = courses.values
            .filter { it.semester == activeSemester }
            .map { it.courseId }
            .toSet()
        val daySlots = slots.filter { it.dayOfWeek == dayOfWeek && it.courseId in semesterCourseIds }
        val classItems = daySlots.mapNotNull { slot ->
            val course = courses[slot.courseId] ?: return@mapNotNull null
            val status = checkInStatusBySessionKey[sessionKey(slot.slotId, normalizedDate)]
                ?: AttendanceStatus.ATTENDING
            val slotLocation = when {
                slot.building.isNotBlank() && slot.room.isNotBlank() -> "${slot.building} · ${slot.room}"
                slot.building.isNotBlank() -> slot.building
                slot.room.isNotBlank() -> slot.room
                else -> course.buildingAndRoom
            }
            AgendaTimelineItem(
                id = "${slot.slotId}_$normalizedDate",
                slotId = slot.slotId,
                courseId = course.courseId,
                kind = TimelineItemKind.CLASS_SESSION,
                title = course.name,
                startTime = slot.startTime,
                endTime = slot.endTime,
                location = slotLocation,
                subtitle = course.professor,
                colorHex = resolveCourseColor(course),
                weatherTempC = campusWeather.weatherTempC,
                status = status
            )
        }

        val commitmentItems = commitments
            .filter { it.isoDate == normalizedDate }
            .map { comm ->
                AgendaTimelineItem(
                    id = comm.id,
                    slotId = null,
                    courseId = null,
                    kind = TimelineItemKind.COMMITMENT,
                    title = comm.title,
                    startTime = comm.startTime,
                    endTime = comm.endTime,
                    location = comm.location,
                    subtitle = comm.subtitle,
                    colorHex = comm.colorHex,
                    weatherTempC = comm.weatherTempC,
                    status = null
                )
            }

        val combinedItems = (classItems + commitmentItems).sortedBy { it.startTime }
        val sundayStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val weekDays = (0 until 7).map { offset ->
            val d = sundayStart.plusDays(offset.toLong())
            DayPillItem(
                isoDate = d.toString(),
                dayShort = dayLabelFor(d.dayOfWeek.value),
                dayNumber = d.dayOfMonth
            )
        }

        AgendaDayOverview(
            isoDate = normalizedDate,
            monthYearLabel = formatMonthYearLabel(date),
            formattedDate = formattedDate,
            weekNumber = date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR),
            weekDays = weekDays,
            classesCount = classItems.size,
            commitmentsCount = commitmentItems.size,
            items = combinedItems
        )
    }

    override fun getClassCheckIn(slotId: String, isoDate: String): ClassCheckInDetail = synchronized(lock) {
        val normalizedDate = parseIsoDateOrToday(isoDate).toString()
        val slot = slots.find { it.slotId == slotId }
            ?: slots.firstOrNull()
        val course = slot?.let { courses[it.courseId] } ?: courses.values.firstOrNull()
        if (course == null) {
            val campus = activeCampusLocked()
            val fallbackDept = resolveDepartmentOption("decom")
            return@synchronized ClassCheckInDetail(
                slotId = slotId,
                courseId = "",
                departmentId = fallbackDept.id,
                departmentName = fallbackDept.name,
                courseName = "Nenhuma disciplina cadastrada",
                formattedDate = formatPortugueseDate(normalizedDate),
                startTime = "08:00",
                endTime = "09:40",
                weatherTempC = campus.weatherTempC,
                weatherCondition = campus.weatherCondition,
                buildingAndRoom = "Campus UFOP",
                professor = "",
                selectedColorHex = fallbackDept.colorHex,
                availableColors = departmentSwatches,
                status = AttendanceStatus.ATTENDING
            )
        }
        val resolvedSlotId = slot?.slotId ?: slotId
        val currentStatus = checkInStatusBySessionKey[sessionKey(resolvedSlotId, normalizedDate)]
            ?: AttendanceStatus.ATTENDING
        val slotLocation = when {
            slot != null && slot.building.isNotBlank() && slot.room.isNotBlank() -> "${slot.building} · ${slot.room}"
            slot != null && slot.building.isNotBlank() -> slot.building
            slot != null && slot.room.isNotBlank() -> slot.room
            else -> course.buildingAndRoom
        }

        val campusWeather = activeCampusLocked()
        val dept = resolveDepartmentOption(course.departmentId)
        ClassCheckInDetail(
            slotId = resolvedSlotId,
            courseId = course.courseId,
            departmentId = dept.id,
            departmentName = dept.name,
            courseName = course.name,
            formattedDate = formatPortugueseDate(normalizedDate),
            startTime = slot?.startTime ?: "08:00",
            endTime = slot?.endTime ?: "09:40",
            weatherTempC = campusWeather.weatherTempC,
            weatherCondition = campusWeather.weatherCondition,
            buildingAndRoom = slotLocation,
            professor = course.professor,
            selectedColorHex = dept.colorHex,
            availableColors = departmentSwatches,
            status = currentStatus
        )
    }

    override fun saveClassCheckIn(
        slotId: String,
        isoDate: String,
        status: AttendanceStatus,
        colorOverrideHex: String?
    ): DisciplineSummaryCard = synchronized(lock) {
        val normalizedDate = parseIsoDateOrToday(isoDate).toString()
        val slot = slots.find { it.slotId == slotId }
            ?: throw IllegalArgumentException("Horário não encontrado.")
        val course = courses[slot.courseId]
            ?: throw IllegalArgumentException("Disciplina não encontrada.")

        checkInStatusBySessionKey[sessionKey(slot.slotId, normalizedDate)] = status
        // Sprint 6: cores padronizadas por departamento — colorOverrideHex é ignorado.

        val updatedSummary = buildDisciplineSummaryLocked(course)
        persistStateLocked()
        _mutationVersion.value += 1L
        updatedSummary
    }

    override fun listDepartments(): List<DepartmentColorOption> = departmentSwatches

    override fun listDepartmentsForSemester(semester: String): List<DepartmentColorOption> = synchronized(lock) {
        val cleanSemester = semester.trim()
        val activeDeptIds = courses.values
            .filter { cleanSemester.isBlank() || it.semester == cleanSemester }
            .map { resolveDepartmentOption(it.departmentId).id }
            .toSet()
        if (activeDeptIds.isEmpty()) {
            emptyList()
        } else {
            departmentSwatches.filter { it.id in activeDeptIds }
        }
    }

    override fun listUfopBuildings(campusId: String?): List<UfopBuildingOption> {
        val raw = campusId?.trim()?.takeIf { it.isNotEmpty() } ?: return ufopBuildingsCatalog
        val resolvedId = when {
            ufopBuildingsCatalog.any { it.campusId.equals(raw, ignoreCase = true) } -> raw.lowercase()
            ufopBuildingsCatalog.any { it.campusName.equals(raw, ignoreCase = true) } ->
                ufopBuildingsCatalog.first { it.campusName.equals(raw, ignoreCase = true) }.campusId
            else -> resolveCampusIdFromText(raw)
        } ?: return emptyList()
        return ufopBuildingsCatalog.filter { it.campusId.equals(resolvedId, ignoreCase = true) }
    }

    override fun listDisciplines(
        semester: String,
        departmentId: String?
    ): List<DisciplineSummaryCard> = synchronized(lock) {
        val normalizedFilter = departmentId?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            normalizeKnownDepartmentId(raw) ?: raw.lowercase()
        }
        courses.values
            .filter { it.semester == semester || semester.isBlank() }
            .filter { normalizedFilter == null || resolveDepartmentOption(it.departmentId).id == normalizedFilter }
            .map { buildDisciplineSummaryLocked(it) }
    }

    override fun getDisciplineDetail(courseId: String): DisciplineDetail = synchronized(lock) {
        val course = courses[courseId] ?: courses.values.firstOrNull()
        if (course == null) {
            val fallbackDept = resolveDepartmentOption("decom")
            return@synchronized DisciplineDetail(
                summary = DisciplineSummaryCard(
                    courseId = courseId,
                    name = "Disciplina removida",
                    departmentId = fallbackDept.id,
                    departmentName = fallbackDept.name,
                    colorHex = fallbackDept.colorHex,
                    workloadHours = 60,
                    currentAbsences = 0,
                    maxAbsences = 18,
                    cancelledClasses = 0
                ),
                semester = "2026.2",
                assessments = emptyList(),
                notes = ""
            )
        }
        val courseAssessments = assessmentsByCourseId[course.courseId]
            ?.sortedWith(compareBy<AssessmentItem> { it.dueDate }.thenBy { it.dueTime })
            ?: course.assessments
        DisciplineDetail(
            summary = buildDisciplineSummaryLocked(course),
            semester = course.semester,
            assessments = courseAssessments,
            notes = notesByCourseId[course.courseId] ?: course.initialNotes,
            professor = course.professor,
            buildingAndRoom = course.buildingAndRoom,
            shortLocation = course.shortLocation,
            code = resolveCourseCode(course)
        )
    }

    override fun updateDisciplineNotes(courseId: String, notes: String) = synchronized(lock) {
        if (courseId in courses) {
            notesByCourseId[courseId] = notes
            persistStateLocked()
            _mutationVersion.value += 1L
        }
    }

    override fun getWeeklyTimetable(semester: String): WeeklyTimetable = synchronized(lock) {
        val cleanSemester = semester.trim().ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        val semesterCourses = courses.values
            .filter { it.semester == cleanSemester }
            .associateBy { it.courseId }
        val entries = slots.mapNotNull { slot ->
            val course = semesterCourses[slot.courseId] ?: return@mapNotNull null
            TimetableEntry(
                dayOfWeek = slot.dayOfWeek,
                startTime = slot.startTime,
                endTime = slot.endTime,
                cell = TimetableCell(
                    slotId = slot.slotId,
                    courseId = course.courseId,
                    courseName = course.name,
                    shortLocation = slot.shortLocation.ifBlank { course.shortLocation },
                    departmentId = course.departmentId,
                    departmentName = resolveDepartmentName(course),
                    colorHex = resolveCourseColor(course),
                    code = resolveCourseCode(course)
                )
            )
        }
        buildTimetable(cleanSemester, "Semana de aulas · $cleanSemester", entries)
    }

    private data class TimetableEntry(
        val dayOfWeek: Int,
        val startTime: String,
        val endTime: String,
        val cell: TimetableCell
    )

    private fun buildTimetable(
        semester: String,
        gridTitle: String,
        entries: List<TimetableEntry>
    ): WeeklyTimetable {
        val daysHeader = buildList {
            add(1 to "SEG")
            add(2 to "TER")
            add(3 to "QUA")
            add(4 to "QUI")
            add(5 to "SEX")
            if (entries.any { it.dayOfWeek == 6 }) {
                add(6 to "SÁB")
            }
        }
        val defaultIntervals = listOf(
            "08:00" to "09:40",
            "10:00" to "11:40",
            "14:00" to "15:00"
        )
        val baseIntervals = entries.map { it.startTime to it.endTime }
        val timeIntervals = (if (baseIntervals.isEmpty()) defaultIntervals else baseIntervals)
            .distinctBy { "${it.first}-${it.second}" }
            .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })

        val rows = timeIntervals.map { (start, end) ->
            val cellsMap = daysHeader.associate { (day, _) ->
                day to entries.find {
                    it.dayOfWeek == day && it.startTime == start && it.endTime == end
                }?.cell
            }
            TimetableRow(
                startTime = start,
                endTime = end,
                cellsByDay = cellsMap
            )
        }

        val activeLegendIds = entries.map { it.cell.departmentId }.toSet()
        val legendDepartments = if (activeLegendIds.isNotEmpty()) {
            departmentSwatches.filter { it.id in activeLegendIds }
        } else {
            emptyList()
        }

        return WeeklyTimetable(
            semester = semester,
            gridTitle = gridTitle,
            days = daysHeader,
            rows = rows,
            legend = legendDepartments
        )
    }

    override fun createCommitment(
        title: String,
        description: String,
        commitmentDate: String,
        startTime: String,
        endTime: String,
        location: String,
        weatherTempC: Int?
    ): AgendaTimelineItem = synchronized(lock) {
        val normalizedDate = parseIsoDateOrToday(commitmentDate).toString()
        val cleanTitle = title.trim().ifBlank { "Compromisso no campus" }
        val cleanLocation = location.trim().ifBlank { "Campus UFOP" }
        val cleanDescription = description.trim().ifBlank { "Compromisso acadêmico" }
        val cleanStart = startTime.trim().ifBlank { "14:00" }
        val cleanEnd = endTime.trim().ifBlank { "15:00" }
        val activeCampusTemp = activeCampusLocked().weatherTempC
        val resolvedTemp = weatherTempC ?: activeCampusTemp

        generatedIdCounter += 1L
        val newId = "comm_${normalizedDate}_$generatedIdCounter"
        val newSeed = CommitmentSeed(
            id = newId,
            isoDate = normalizedDate,
            title = cleanTitle,
            startTime = cleanStart,
            endTime = cleanEnd,
            location = cleanLocation,
            subtitle = cleanDescription,
            colorHex = COMMITMENT_COLOR_HEX,
            weatherTempC = resolvedTemp
        )
        commitments.add(newSeed)
        persistStateLocked()
        _mutationVersion.value += 1L

        toCommitmentTimelineItem(newSeed)
    }

    override fun updateCommitment(
        commitmentId: String,
        title: String,
        description: String,
        commitmentDate: String,
        startTime: String,
        endTime: String,
        location: String
    ): AgendaTimelineItem = synchronized(lock) {
        val idx = commitments.indexOfFirst { it.id == commitmentId }
        if (idx < 0) {
            throw IllegalArgumentException("Compromisso não encontrado: $commitmentId")
        }
        val existing = commitments[idx]
        val normalizedDate = parseIsoDateOrToday(commitmentDate).toString()
        val cleanTitle = title.trim().ifBlank { existing.title }
        val cleanDescription = description.trim().ifBlank { existing.subtitle }
        val cleanStart = startTime.trim().ifBlank { existing.startTime }
        val cleanEnd = endTime.trim().ifBlank { existing.endTime }
        val cleanLocation = location.trim().ifBlank { existing.location }

        val updated = existing.copy(
            isoDate = normalizedDate,
            title = cleanTitle,
            startTime = cleanStart,
            endTime = cleanEnd,
            location = cleanLocation,
            subtitle = cleanDescription
        )
        commitments[idx] = updated
        persistStateLocked()
        _mutationVersion.value += 1L
        toCommitmentTimelineItem(updated)
    }

    private fun toCommitmentTimelineItem(comm: CommitmentSeed): AgendaTimelineItem = AgendaTimelineItem(
        id = comm.id,
        slotId = null,
        courseId = null,
        kind = TimelineItemKind.COMMITMENT,
        title = comm.title,
        startTime = comm.startTime,
        endTime = comm.endTime,
        location = comm.location,
        subtitle = comm.subtitle,
        colorHex = comm.colorHex,
        weatherTempC = comm.weatherTempC,
        status = null
    )

    override fun deleteCommitment(commitmentId: String): Unit = synchronized(lock) {
        val removed = commitments.removeAll { it.id == commitmentId }
        if (removed) {
            persistStateLocked()
            _mutationVersion.value += 1L
        }
    }

    private fun parseDueDateParts(dueDateInput: String): Triple<String, String, String> {
        val trimmed = dueDateInput.trim()
        val monthNumToShort = mapOf(
            "01" to "JAN", "02" to "FEV", "03" to "MAR", "04" to "ABR",
            "05" to "MAI", "06" to "JUN", "07" to "JUL", "08" to "AGO",
            "09" to "SET", "10" to "OUT", "11" to "NOV", "12" to "DEZ"
        )
        val monthShortToNum = monthNumToShort.entries.associate { (k, v) -> v to k }

        // Formato ISO: YYYY-MM-DD
        val isoParts = trimmed.split("-")
        if (isoParts.size == 3 && isoParts[0].length == 4) {
            val monthNum = isoParts[1].padStart(2, '0')
            val dayNum = isoParts[2].take(2).padStart(2, '0')
            val monthShort = monthNumToShort[monthNum] ?: "NOV"
            return Triple("${isoParts[0]}-$monthNum-$dayNum", dayNum, monthShort)
        }

        // Formato DD/MM ou DD MMM
        val tokens = trimmed.replace("/", " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size >= 2) {
            val dayNum = tokens[0].filter { it.isDigit() }.padStart(2, '0').ifBlank { "28" }
            val second = tokens[1].uppercase()
            val monthShort = when {
                second in monthShortToNum -> second
                second.padStart(2, '0') in monthNumToShort -> monthNumToShort[second.padStart(2, '0')] ?: "NOV"
                second.startsWith("DEZ") -> "DEZ"
                second.startsWith("NOV") -> "NOV"
                second.startsWith("OUT") -> "OUT"
                else -> "NOV"
            }
            val monthNum = monthShortToNum[monthShort] ?: "11"
            return Triple("2026-$monthNum-$dayNum", dayNum, monthShort)
        }

        return Triple("2026-11-28", "28", "NOV")
    }

    override fun createAssessment(
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String
    ): AssessmentItem = synchronized(lock) {
        val targetCourse = courses[courseId] ?: courses.values.first()
        val (isoDueDate, dayNumber, monthShort) = parseDueDateParts(dueDate)
        val cleanTitle = title.trim().ifBlank { "Nova avaliação" }
        val cleanKind = kind.trim().ifBlank { "Trabalho" }
        val cleanTime = dueTime.trim().ifBlank { "18:00" }

        generatedIdCounter += 1L
        val newItem = AssessmentItem(
            id = "assess_${targetCourse.courseId}_$generatedIdCounter",
            courseId = targetCourse.courseId,
            dayNumber = dayNumber,
            monthShort = monthShort,
            title = cleanTitle,
            kind = cleanKind,
            dueTime = cleanTime,
            dueDate = isoDueDate,
            completed = false
        )

        val list = assessmentsByCourseId.getOrPut(targetCourse.courseId) { mutableListOf() }
        list.add(newItem)
        persistStateLocked()
        _mutationVersion.value += 1L
        newItem
    }

    override fun updateAssessment(
        assessmentId: String,
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String,
        completed: Boolean
    ): AssessmentItem = synchronized(lock) {
        var foundCourseId: String? = null
        var foundIdx = -1
        var existing: AssessmentItem? = null
        for ((cid, list) in assessmentsByCourseId) {
            val idx = list.indexOfFirst { it.id == assessmentId }
            if (idx >= 0) {
                foundCourseId = cid
                foundIdx = idx
                existing = list[idx]
                break
            }
        }
        if (existing == null || foundCourseId == null || foundIdx < 0) {
            throw IllegalArgumentException("Avaliação não encontrada: $assessmentId")
        }

        val targetCourseId = if (courseId in courses) {
            courseId
        } else if (foundCourseId in courses) {
            foundCourseId
        } else {
            courses.keys.firstOrNull() ?: existing.courseId
        }

        val (isoDueDate, dayNumber, monthShort) = parseDueDateParts(dueDate)
        val cleanTitle = title.trim().ifBlank { existing.title }
        val cleanKind = kind.trim().ifBlank { existing.kind }
        val cleanTime = dueTime.trim().ifBlank { existing.dueTime }

        val updated = existing.copy(
            courseId = targetCourseId,
            dayNumber = dayNumber,
            monthShort = monthShort,
            title = cleanTitle,
            kind = cleanKind,
            dueTime = cleanTime,
            dueDate = isoDueDate,
            completed = completed
        )

        if (targetCourseId == foundCourseId) {
            assessmentsByCourseId[foundCourseId]?.set(foundIdx, updated)
        } else {
            assessmentsByCourseId[foundCourseId]?.removeAt(foundIdx)
            val targetList = assessmentsByCourseId.getOrPut(targetCourseId) { mutableListOf() }
            targetList.add(updated)
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        updated
    }

    override fun toggleAssessmentCompleted(
        assessmentId: String,
        completed: Boolean
    ): AssessmentItem = synchronized(lock) {
        for (list in assessmentsByCourseId.values) {
            val idx = list.indexOfFirst { it.id == assessmentId }
            if (idx >= 0) {
                val updated = list[idx].copy(completed = completed)
                list[idx] = updated
                persistStateLocked()
                _mutationVersion.value += 1L
                return@synchronized updated
            }
        }
        val fallback = assessmentsByCourseId.values.flatten().first().copy(completed = completed)
        persistStateLocked()
        _mutationVersion.value += 1L
        fallback
    }

    override fun deleteAssessment(assessmentId: String): Unit = synchronized(lock) {
        var removedAny = false
        for (list in assessmentsByCourseId.values) {
            if (list.removeAll { it.id == assessmentId }) {
                removedAny = true
            }
        }
        if (removedAny) {
            persistStateLocked()
            _mutationVersion.value += 1L
        }
    }

    override fun listAllAssessments(
        semester: String,
        kindFilter: String?
    ): List<AssessmentWithCourse> = synchronized(lock) {
        val normalizedFilter = kindFilter?.trim()?.lowercase().orEmpty()
        val results = mutableListOf<AssessmentWithCourse>()

        for (course in courses.values) {
            if (semester.isNotBlank() && course.semester != semester) continue
            val color = resolveCourseColor(course)
            val list = assessmentsByCourseId[course.courseId].orEmpty()
            for (item in list) {
                val matchesFilter = when {
                    normalizedFilter.isEmpty() || normalizedFilter == "todas" || normalizedFilter == "all" -> true
                    normalizedFilter == "concluídas" || normalizedFilter == "concluidas" || normalizedFilter == "completed" -> item.completed
                    normalizedFilter == "pendentes" || normalizedFilter == "pending" -> !item.completed
                    normalizedFilter.startsWith("prova") -> item.kind.lowercase().startsWith("prova")
                    normalizedFilter.startsWith("trabalho") -> item.kind.lowercase().startsWith("trabalho")
                    normalizedFilter.startsWith("semin") -> item.kind.lowercase().startsWith("semin")
                    else -> item.kind.equals(normalizedFilter, ignoreCase = true)
                }
                if (matchesFilter) {
                    results.add(
                        AssessmentWithCourse(
                            assessment = item,
                            courseName = course.name,
                            departmentName = course.departmentName,
                            colorHex = color
                        )
                    )
                }
            }
        }

        results.sortedWith(
            compareBy<AssessmentWithCourse> { it.assessment.completed }
                .thenBy { it.assessment.dueDate }
                .thenBy { it.assessment.dueTime }
        )
    }

    private fun allCampusesLocked(): List<UfopCampusInfo> =
        ufopCampusesSeed.map { seed ->
            val live = campusWeatherById[seed.id]
            seed.copy(
                weatherTempC = live?.tempC ?: seed.weatherTempC,
                weatherCondition = live?.condition ?: seed.weatherCondition,
                isActive = seed.id == activeCampusId
            )
        }

    private fun activeCampusLocked(): UfopCampusInfo {
        val campuses = allCampusesLocked()
        return campuses.find { it.isActive } ?: campuses.first()
    }

    override fun getAcademicProfileSummary(semester: String): AcademicProfileSummary = synchronized(lock) {
        val cleanSemester = semester.ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        val campusesList = allCampusesLocked()
        val activeCampus = campusesList.find { it.isActive } ?: campusesList.first()

        val summaries = courses.values
            .filter { it.semester == cleanSemester }
            .map { buildDisciplineSummaryLocked(it) }

        val allAssessments = courses.values
            .filter { it.semester == cleanSemester }
            .flatMap { assessmentsByCourseId[it.courseId].orEmpty() }

        val atRiskCount = summaries.count { summary ->
            summary.maxAbsences > 0 &&
                (summary.currentAbsences.toDouble() / summary.maxAbsences.toDouble()) >= 0.75
        }

        val events = getAcademicCalendarEventsLocked(cleanSemester)

        AcademicProfileSummary(
            semester = cleanSemester,
            activeCampus = activeCampus,
            campuses = campusesList,
            totalCourses = summaries.size,
            totalAbsences = summaries.sumOf { it.currentAbsences },
            totalMaxAbsences = summaries.sumOf { it.maxAbsences },
            totalCancelledClasses = summaries.sumOf { it.cancelledClasses },
            coursesAtRisk = atRiskCount,
            pendingAssessments = allAssessments.count { !it.completed },
            completedAssessments = allAssessments.count { it.completed },
            calendarEvents = events
        )
    }

    override fun getAcademicCalendarEvents(semester: String): List<AcademicCalendarEvent> = synchronized(lock) {
        getAcademicCalendarEventsLocked(semester)
    }

    private fun getAcademicCalendarEventsLocked(semester: String): List<AcademicCalendarEvent> {
        val cleanSemester = semester.trim().ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        val custom = calendarEventsBySemester[cleanSemester]
        if (!custom.isNullOrEmpty()) {
            return custom.sortedBy { it.eventDate }
        }
        val seed = calendarEventsSeed.filter { it.semester == cleanSemester }
        if (seed.isNotEmpty()) {
            return seed.sortedBy { it.eventDate }
        }
        return buildSemesterBoundaryEventsLocked(cleanSemester)
    }

    private fun buildSemesterBoundaryEventsLocked(semester: String): List<AcademicCalendarEvent> {
        if (semester != studentProfile.activeSemester) return emptyList()
        val results = mutableListOf<AcademicCalendarEvent>()
        val startDate = parseIsoDateOrNull(studentProfile.semesterStartDate)
        if (startDate != null) {
            val (_, dayNum, monthShort) = parseDueDateParts(startDate.toString())
            results.add(
                AcademicCalendarEvent(
                    id = "cal_${semester}_inicio",
                    semester = semester,
                    eventDate = startDate.toString(),
                    dayNumber = dayNum,
                    monthShort = monthShort,
                    title = "Início das Aulas $semester",
                    category = "Semestre"
                )
            )
        }
        val endDate = parseIsoDateOrNull(studentProfile.semesterEndDate)
        if (endDate != null) {
            val (_, dayNum, monthShort) = parseDueDateParts(endDate.toString())
            results.add(
                AcademicCalendarEvent(
                    id = "cal_${semester}_termino",
                    semester = semester,
                    eventDate = endDate.toString(),
                    dayNumber = dayNum,
                    monthShort = monthShort,
                    title = "Término das Aulas $semester",
                    category = "Semestre"
                )
            )
        }
        return results
    }

    override fun setActiveCampus(campusId: String): UfopCampusInfo = synchronized(lock) {
        val target = ufopCampusesSeed.find { it.id == campusId } ?: ufopCampusesSeed.first()
        activeCampusId = target.id
        studentProfile = studentProfile.copy(campusName = target.city)
        persistStateLocked()
        _mutationVersion.value += 1L
        activeCampusLocked()
    }

    override fun exportSchedulePayload(semester: String): String = synchronized(lock) {
        val cleanSemester = semester.ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        buildScheduleJsonObjectLocked(cleanSemester).toString(2)
    }

    override fun exportMyGradeBundleJson(semester: String): String = synchronized(lock) {
        val cleanSemester = semester.ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        val base = buildScheduleJsonObjectLocked(cleanSemester)
        val avatarBase64 = encodeProfileAvatarBase64(studentProfile.avatarPath)
        base.apply {
            put("format", "ufopgrade-v1")
            put("friendName", studentProfile.fullName.trim().ifBlank { "Estudante UFOP" })
            put("courseName", studentProfile.courseName.trim())
            if (!avatarBase64.isNullOrBlank()) {
                put("avatarBase64", avatarBase64)
            }
        }.toString(2)
    }

    private fun buildScheduleJsonObjectLocked(cleanSemester: String): JSONObject {
        val semesterCourses = courses.values.filter { it.semester == cleanSemester }
        val courseIds = semesterCourses.map { it.courseId }.toSet()
        val semesterSlots = slots.filter { it.courseId in courseIds }

        val coursesArr = JSONArray()
        semesterCourses.forEach { c ->
            val dept = resolveDepartmentOption(c.departmentId)
            coursesArr.put(
                JSONObject().apply {
                    put("courseId", c.courseId)
                    put("code", resolveCourseCode(c))
                    put("semester", c.semester)
                    put("name", c.name)
                    put("departmentId", dept.id)
                    put("departmentName", dept.name)
                    put("colorHex", dept.colorHex)
                    put("workloadHours", c.workloadHours)
                    put("maxAbsences", c.maxAbsences)
                    put("professor", c.professor)
                    put("buildingAndRoom", c.buildingAndRoom)
                    put("shortLocation", c.shortLocation)
                }
            )
        }

        val slotsArr = JSONArray()
        semesterSlots.forEach { s ->
            slotsArr.put(
                JSONObject().apply {
                    put("slotId", s.slotId)
                    put("courseId", s.courseId)
                    put("dayOfWeek", s.dayOfWeek)
                    put("startTime", s.startTime)
                    put("endTime", s.endTime)
                    put("building", s.building)
                    put("room", s.room)
                    put("shortLocation", s.shortLocation)
                    put("classWeight", s.classWeight)
                }
            )
        }

        return JSONObject().apply {
            put("semester", cleanSemester)
            put("campusId", activeCampusId)
            put("courses", coursesArr)
            put("slots", slotsArr)
        }
    }

    private fun encodeProfileAvatarBase64(avatarPath: String?): String? {
        val path = avatarPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val file = File(path)
            if (!file.exists() || file.length() <= 0L) return null
            val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val squareSize = minOf(decoded.width, decoded.height)
            val cropX = (decoded.width - squareSize) / 2
            val cropY = (decoded.height - squareSize) / 2
            val cropped = if (decoded.width != decoded.height && squareSize > 0) {
                Bitmap.createBitmap(decoded, cropX, cropY, squareSize, squareSize)
            } else {
                decoded
            }
            val scaled = Bitmap.createScaledBitmap(cropped, 120, 120, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
            val bytes = out.toByteArray()
            if (scaled !== cropped && !scaled.isRecycled) scaled.recycle()
            if (cropped !== decoded && !cropped.isRecycled) cropped.recycle()
            if (!decoded.isRecycled) decoded.recycle()
            if (bytes.isEmpty()) null else Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Throwable) {
            null
        }
    }

    override fun importSchedulePayload(jsonPayload: String): Int = synchronized(lock) {
        val trimmed = jsonPayload.trim()
        if (trimmed.isBlank() || !trimmed.contains("{")) {
            return@synchronized 0
        }

        var importedCount = 0
        val parsedRoot = try { JSONObject(trimmed) } catch (_: Throwable) { null }
        if (parsedRoot != null) {
            val defaultSemester = parsedRoot.optNonNullString("semester", studentProfile.activeSemester)
                .ifBlank { "2026.2" }
            val coursesArr = parsedRoot.optJSONArray("courses")
            if (coursesArr != null) {
                for (i in 0 until coursesArr.length()) {
                    val obj = coursesArr.optJSONObject(i) ?: continue
                    val id = obj.optNonNullString("courseId")
                        .ifBlank { obj.optNonNullString("id") }
                        .trim()
                    val name = obj.optNonNullString("name").trim()
                    if (id.isEmpty() || name.isEmpty()) continue
                    val semester = obj.optNonNullString("semester", defaultSemester).ifBlank { defaultSemester }
                    val rawDeptId = obj.optNonNullString("departmentId")
                        .ifBlank { obj.optNonNullString("department_id", "comp") }
                        .ifBlank { "comp" }
                    val dept = resolveDepartmentOption(rawDeptId)
                    val workloadHours = obj.optInt("workloadHours", obj.optInt("workload_hours", 60))
                        .coerceAtLeast(1)
                    val maxAbsences = obj.optInt(
                        "maxAbsences",
                        obj.optInt("max_absences", (workloadHours * 3) / 10)
                    ).coerceAtLeast(0)
                    val professor = obj.optNonNullString("professor", "").trim()
                    val buildingAndRoom = obj.optNonNullString("buildingAndRoom")
                        .ifBlank { obj.optNonNullString("building_and_room", "Campus UFOP") }
                        .ifBlank { "Campus UFOP" }
                    val shortLocation = obj.optNonNullString("shortLocation")
                        .ifBlank { obj.optNonNullString("short_location", "UFOP") }
                        .ifBlank { "UFOP" }

                    val existing = courses[id]
                    val resolvedCode = deriveCourseCodeFallback(
                        explicitCode = obj.optNonNullString("code"),
                        notes = existing?.initialNotes.orEmpty(),
                        courseId = id,
                        courseName = name
                    )
                    courses[id] = CourseSeed(
                        courseId = id,
                        semester = semester,
                        name = name,
                        departmentId = dept.id,
                        departmentName = dept.name,
                        defaultColorHex = dept.colorHex,
                        workloadHours = workloadHours,
                        baseAbsences = existing?.baseAbsences ?: 0,
                        maxAbsences = maxAbsences,
                        baseCancelledClasses = existing?.baseCancelledClasses ?: 0,
                        professor = professor,
                        buildingAndRoom = buildingAndRoom,
                        shortLocation = shortLocation,
                        assessments = existing?.assessments ?: emptyList(),
                        initialNotes = existing?.initialNotes ?: "",
                        code = resolvedCode
                    )
                    if (id !in assessmentsByCourseId) {
                        assessmentsByCourseId[id] = mutableListOf()
                    }
                    importedCount += 1
                }
            }

            val slotsArr = parsedRoot.optJSONArray("slots")
            if (slotsArr != null) {
                val activeCampus = activeCampusLocked()
                for (i in 0 until slotsArr.length()) {
                    val obj = slotsArr.optJSONObject(i) ?: continue
                    val slotId = obj.optNonNullString("slotId")
                        .ifBlank { obj.optNonNullString("slot_id") }
                        .trim()
                    val courseId = obj.optNonNullString("courseId")
                        .ifBlank { obj.optNonNullString("course_id") }
                        .trim()
                    if (slotId.isEmpty() || courseId.isEmpty()) continue
                    val dayOfWeek = obj.optInt("dayOfWeek", obj.optInt("day_of_week", 2)).coerceIn(1, 7)
                    val startTime = obj.optNonNullString("startTime")
                        .ifBlank { obj.optNonNullString("start_time", "08:00") }
                        .ifBlank { "08:00" }
                    val endTime = obj.optNonNullString("endTime")
                        .ifBlank { obj.optNonNullString("end_time", "09:40") }
                        .ifBlank { "09:40" }
                    val building = obj.optNonNullString("building", "Campus UFOP").ifBlank { "Campus UFOP" }
                    val room = obj.optNonNullString("room", "").trim()
                    val shortLocation = obj.optNonNullString("shortLocation")
                        .ifBlank { obj.optNonNullString("short_location") }
                        .ifBlank { courses[courseId]?.shortLocation ?: buildShortLocation(building, room) }
                    val classWeight = obj.optInt("classWeight", obj.optInt("class_weight", 1)).coerceAtLeast(1)

                    slots.removeAll { it.slotId == slotId }
                    slots.add(
                        SlotSeed(
                            slotId = slotId,
                            courseId = courseId,
                            dayOfWeek = dayOfWeek,
                            startTime = startTime,
                            endTime = endTime,
                            building = building,
                            room = room,
                            shortLocation = shortLocation,
                            defaultTempC = activeCampus.weatherTempC,
                            weatherCondition = activeCampus.weatherCondition,
                            classWeight = classWeight
                        )
                    )
                }
            }

            val campusIdInJson = parsedRoot.optNonNullString("campusId")
            if (campusIdInJson.isNotBlank() && ufopCampusesSeed.any { it.id == campusIdInJson }) {
                activeCampusId = campusIdInJson
                ufopCampusesSeed.find { it.id == campusIdInJson }?.let { campus ->
                    studentProfile = studentProfile.copy(campusName = campus.city)
                }
            }
        }

        if (importedCount == 0 && trimmed.startsWith("{")) {
            importedCount = courses.size
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        importedCount
    }

    override fun createCourse(
        semester: String,
        departmentId: String,
        name: String,
        professor: String,
        workloadHours: Int,
        colorOverrideHex: String?
    ): DisciplineSummaryCard = synchronized(lock) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Informe o nome da disciplina." }

        val cleanSemester = semester.trim().ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        val cleanProfessor = professor.trim()
        val deptOption = resolveDepartmentOption(departmentId)
        val safeWorkload = if (workloadHours > 0) workloadHours else 60
        val maxAbsences = (safeWorkload * 3) / 10
        // Sprint 6: cor derivada exclusivamente do departamento (ignora colorOverrideHex).
        val resolvedColor = deptOption.colorHex

        generatedIdCounter += 1L
        val courseId = "course_$generatedIdCounter"
        val resolvedCode = deriveCourseCodeFallback(null, "", courseId, cleanName)
        val newCourse = CourseSeed(
            courseId = courseId,
            semester = cleanSemester,
            name = cleanName,
            departmentId = deptOption.id,
            departmentName = deptOption.name,
            defaultColorHex = resolvedColor,
            workloadHours = safeWorkload,
            baseAbsences = 0,
            maxAbsences = maxAbsences,
            baseCancelledClasses = 0,
            professor = cleanProfessor,
            buildingAndRoom = "Campus UFOP",
            shortLocation = "UFOP",
            assessments = emptyList(),
            initialNotes = "",
            code = resolvedCode
        )
        courses[courseId] = newCourse
        notesByCourseId[courseId] = ""
        assessmentsByCourseId[courseId] = mutableListOf()
        persistStateLocked()
        _mutationVersion.value += 1L
        buildDisciplineSummaryLocked(newCourse)
    }

    override fun updateCourse(
        courseId: String,
        name: String,
        professor: String,
        departmentId: String,
        workloadHours: Int
    ): DisciplineSummaryCard = synchronized(lock) {
        val existing = courses[courseId]
            ?: throw IllegalArgumentException("Disciplina não encontrada.")
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Informe o nome da disciplina." }
        val cleanProfessor = professor.trim()
        val deptOption = resolveDepartmentOption(departmentId)
        val safeWorkload = if (workloadHours > 0) workloadHours else existing.workloadHours
        val maxAbsences = (safeWorkload * 3) / 10

        val updated = existing.copy(
            name = cleanName,
            professor = cleanProfessor,
            departmentId = deptOption.id,
            departmentName = deptOption.name,
            defaultColorHex = deptOption.colorHex,
            workloadHours = safeWorkload,
            maxAbsences = maxAbsences,
            code = existing.code.ifBlank {
                deriveCourseCodeFallback(null, notesByCourseId[courseId] ?: existing.initialNotes, courseId, cleanName)
            }
        )
        courses[courseId] = updated
        courseColorOverrides.remove(courseId)
        persistStateLocked()
        _mutationVersion.value += 1L
        buildDisciplineSummaryLocked(updated)
    }

    override fun updateCourseAbsences(
        courseId: String,
        totalAbsences: Int
    ): DisciplineSummaryCard = synchronized(lock) {
        val course = courses[courseId]
            ?: throw IllegalArgumentException("Disciplina não encontrada.")
        val safeTotal = totalAbsences.coerceAtLeast(0)
        val sessionAbsences = computeExtraCheckInAbsencesLocked(courseId)
        val updatedCourse = course.copy(baseAbsences = safeTotal - sessionAbsences)
        courses[courseId] = updatedCourse

        val courseCode = resolveCourseCode(updatedCourse)
        val txIdx = transcriptEntries.indexOfFirst { entry ->
            entry.semester == updatedCourse.semester &&
                entry.status == "CS" && (
                    entry.id.equals(courseId, ignoreCase = true) ||
                        entry.code.equals(courseCode, ignoreCase = true) ||
                        normalizeDedupKey(entry.name) == normalizeDedupKey(updatedCourse.name)
                    )
        }
        if (txIdx >= 0) {
            transcriptEntries[txIdx] = transcriptEntries[txIdx].copy(absences = safeTotal)
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        buildDisciplineSummaryLocked(updatedCourse)
    }

    override fun getDarkThemePreference(): String = synchronized(lock) { darkThemePreference }

    override fun setDarkThemePreference(mode: String) = synchronized(lock) {
        val normalized = when (mode.trim().uppercase()) {
            "DARK" -> "DARK"
            "LIGHT" -> "LIGHT"
            else -> "SYSTEM"
        }
        darkThemePreference = normalized
        persistStateLocked()
        _mutationVersion.value += 1L
    }

    private fun normalizeAccentColorKey(raw: String?): String = when (raw?.trim()?.uppercase()) {
        "GREEN" -> "GREEN"
        "RED" -> "RED"
        "ORANGE" -> "ORANGE"
        "PURPLE" -> "PURPLE"
        "YELLOW" -> "YELLOW"
        "CYAN" -> "CYAN"
        "BROWN" -> "BROWN"
        else -> "GREEN"
    }

    override fun getAccentColorPreference(): String = synchronized(lock) { accentColorPreference }

    override fun setAccentColorPreference(accentKey: String) = synchronized(lock) {
        accentColorPreference = normalizeAccentColorKey(accentKey)
        persistStateLocked()
        _mutationVersion.value += 1L
    }

    private fun normalizeAccessibilitySettings(
        settings: CampusAccessibilitySettings
    ): CampusAccessibilitySettings {
        val safeScale = if (settings.fontScaleMultiplier.isFinite()) {
            settings.fontScaleMultiplier.coerceIn(0.85f, 2.0f)
        } else {
            1.0f
        }
        return settings.copy(fontScaleMultiplier = safeScale)
    }

    override fun getAccessibilitySettings(): CampusAccessibilitySettings = synchronized(lock) {
        accessibilitySettings
    }

    override fun setAccessibilitySettings(settings: CampusAccessibilitySettings) = synchronized(lock) {
        accessibilitySettings = normalizeAccessibilitySettings(settings)
        persistStateLocked()
        _mutationVersion.value += 1L
    }

    override fun isGuidedTourCompleted(): Boolean = synchronized(lock) { guidedTourCompleted }

    override fun setGuidedTourCompleted(completed: Boolean) = synchronized(lock) {
        guidedTourCompleted = completed
        persistStateLocked()
        _mutationVersion.value += 1L
    }

    override fun getLastSeenReleaseVersion(): String = synchronized(lock) { lastSeenReleaseVersion }

    override fun setLastSeenReleaseVersion(version: String) = synchronized(lock) {
        lastSeenReleaseVersion = version.trim()
        persistStateLocked()
        _mutationVersion.value += 1L
    }

    override fun exportFullAccountSnapshotJson(): String = synchronized(lock) {
        buildPersistedJsonLocked().toString()
    }

    override fun restoreFullAccountSnapshotJson(rawJson: String): Boolean = synchronized(lock) {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return@synchronized false
        val root = try {
            JSONObject(trimmed)
        } catch (_: Throwable) {
            return@synchronized false
        }
        val schemaVersion = root.optInt("schemaVersion", -1)
        val hasValidData = schemaVersion >= 1 ||
            root.has("courses") ||
            root.has("studentProfile") ||
            root.has("transcriptEntries")
        if (!hasValidData) return@synchronized false

        val restored = runCatching {
            restoreStateFromJsonLocked(root)
            true
        }.getOrDefault(false)
        if (!restored) return@synchronized false

        persistStateLocked()
        _mutationVersion.value += 1L
        true
    }

    override fun wipeAllUserDataForLogout() = synchronized(lock) {
        applyVirginStateLocked()
        stateFile?.let { targetFile ->
            val parentDir = targetFile.absoluteFile.parentFile
            runCatching { targetFile.delete() }
            if (parentDir != null) {
                runCatching { File(parentDir, "${targetFile.name}.bak").delete() }
                runCatching { File(parentDir, "${targetFile.name}.tmp").delete() }
            }
        }
        _mutationVersion.value += 1L
        runCatching { onStatePersisted?.invoke() }
        Unit
    }

    override fun getExportFolderConfig(): ExportFolderConfig = synchronized(lock) {
        ExportFolderConfig(
            treeUri = exportFolderTreeUri,
            displayLabel = exportFolderDisplayLabel
        )
    }

    override fun setExportFolderConfig(
        treeUri: String?,
        displayLabel: String?
    ): ExportFolderConfig = synchronized(lock) {
        val cleanUri = treeUri?.trim()?.takeIf { it.isNotEmpty() }
        val cleanLabel = if (cleanUri == null) {
            DEFAULT_EXPORT_FOLDER_LABEL
        } else {
            displayLabel?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_EXPORT_FOLDER_LABEL
        }
        exportFolderTreeUri = cleanUri
        exportFolderDisplayLabel = cleanLabel
        persistStateLocked()
        _mutationVersion.value += 1L
        ExportFolderConfig(treeUri = cleanUri, displayLabel = cleanLabel)
    }

    override fun setTotalRequiredHours(hours: Int): Unit = synchronized(lock) {
        totalRequiredHours = hours.coerceIn(100, 10000)
        persistStateLocked()
        _mutationVersion.value += 1L
    }

    override fun deleteCourse(courseId: String): Unit = synchronized(lock) {
        val removed = courses.remove(courseId) != null
        val removedSlotIds = slots.filter { it.courseId == courseId }.map { it.slotId }.toSet()
        val removedSlots = slots.removeAll { it.courseId == courseId }
        if (removedSlotIds.isNotEmpty()) {
            checkInStatusBySessionKey.keys.removeAll { key ->
                removedSlotIds.any { slotId -> key.startsWith("${slotId}_") }
            }
        }
        courseColorOverrides.remove(courseId)
        notesByCourseId.remove(courseId)
        assessmentsByCourseId.remove(courseId)
        if (removed || removedSlots) {
            persistStateLocked()
            _mutationVersion.value += 1L
        }
    }

    override fun addScheduleSlot(
        courseId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ): CourseScheduleSlotItem = synchronized(lock) {
        val course = courses[courseId]
            ?: throw IllegalArgumentException("Disciplina não encontrada.")
        require(dayOfWeek in 1..7) { "Dia da semana inválido." }

        val cleanStart = startTime.trim().ifBlank { "08:00" }
        val cleanEnd = endTime.trim().ifBlank { "09:40" }
        validateSlotTimes(cleanStart, cleanEnd)
        val cleanBuilding = building.trim().ifBlank { "Prédio A" }
        val cleanRoom = room.trim()
        val cleanShort = shortLocation.trim().ifBlank { buildShortLocation(cleanBuilding, cleanRoom) }

        checkSlotConflictLocked(course.semester, dayOfWeek, cleanStart, cleanEnd, ignoreSlotId = null)

        generatedIdCounter += 1L
        val slotId = "slot_${dayLabelFor(dayOfWeek).lowercase()}_${cleanStart.replace(":", "")}_$generatedIdCounter"
        val activeCampus = activeCampusLocked()
        val newSlot = SlotSeed(
            slotId = slotId,
            courseId = courseId,
            dayOfWeek = dayOfWeek,
            startTime = cleanStart,
            endTime = cleanEnd,
            building = cleanBuilding,
            room = cleanRoom,
            shortLocation = cleanShort,
            defaultTempC = activeCampus.weatherTempC,
            weatherCondition = activeCampus.weatherCondition,
            classWeight = computeClassWeightFromTimes(cleanStart, cleanEnd)
        )
        slots.add(newSlot)

        if (course.buildingAndRoom == "Campus UFOP" || course.shortLocation == "UFOP") {
            courses[courseId] = course.copy(
                buildingAndRoom = if (cleanRoom.isNotBlank()) "$cleanBuilding · $cleanRoom" else cleanBuilding,
                shortLocation = cleanShort
            )
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        toCourseScheduleSlotItem(newSlot)
    }

    override fun updateScheduleSlot(
        slotId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ): CourseScheduleSlotItem = synchronized(lock) {
        val idx = slots.indexOfFirst { it.slotId == slotId }
        if (idx < 0) {
            throw IllegalArgumentException("Horário não encontrado.")
        }
        val existing = slots[idx]
        val course = courses[existing.courseId]
            ?: throw IllegalArgumentException("Disciplina não encontrada.")
        require(dayOfWeek in 1..7) { "Dia da semana inválido." }

        val cleanStart = startTime.trim().ifBlank { existing.startTime }
        val cleanEnd = endTime.trim().ifBlank { existing.endTime }
        validateSlotTimes(cleanStart, cleanEnd)
        val cleanBuilding = building.trim().ifBlank { existing.building }
        val cleanRoom = room.trim()
        val cleanShort = shortLocation.trim().ifBlank { buildShortLocation(cleanBuilding, cleanRoom) }

        checkSlotConflictLocked(course.semester, dayOfWeek, cleanStart, cleanEnd, ignoreSlotId = slotId)

        val updated = existing.copy(
            dayOfWeek = dayOfWeek,
            startTime = cleanStart,
            endTime = cleanEnd,
            building = cleanBuilding,
            room = cleanRoom,
            shortLocation = cleanShort,
            classWeight = computeClassWeightFromTimes(cleanStart, cleanEnd)
        )
        slots[idx] = updated

        if (course.buildingAndRoom == "Campus UFOP" || course.shortLocation == "UFOP") {
            courses[course.courseId] = course.copy(
                buildingAndRoom = if (cleanRoom.isNotBlank()) "$cleanBuilding · $cleanRoom" else cleanBuilding,
                shortLocation = cleanShort
            )
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        toCourseScheduleSlotItem(updated)
    }

    private fun validateSlotTimes(startTime: String, endTime: String) {
        val startMin = parseMinutes(startTime)
        val endMin = parseMinutes(endTime)
        if (startMin != null && endMin != null && startMin >= endMin) {
            throw IllegalArgumentException("O horário de término deve ser após o início.")
        }
    }

    private fun computeClassWeightFromTimes(startTime: String, endTime: String): Int {
        val startMin = parseMinutes(startTime) ?: return 1
        val endMin = parseMinutes(endTime) ?: return 1
        val dur = endMin - startMin
        if (dur <= 0) return 1
        return when {
            dur <= 60 -> 1
            dur <= 115 -> 2
            dur <= 175 -> 3
            dur <= 235 -> 4
            else -> ((dur + 25) / 55).coerceIn(1, 6)
        }
    }

    private fun checkSlotConflictLocked(
        semester: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        ignoreSlotId: String?
    ) {
        val conflictSlot = slots.firstOrNull { existing ->
            if (ignoreSlotId != null && existing.slotId == ignoreSlotId) return@firstOrNull false
            val existingCourse = courses[existing.courseId] ?: return@firstOrNull false
            existingCourse.semester == semester &&
                existing.dayOfWeek == dayOfWeek &&
                (existing.startTime == startTime ||
                    timesOverlap(existing.startTime, existing.endTime, startTime, endTime))
        }
        if (conflictSlot != null) {
            val conflictName = courses[conflictSlot.courseId]?.name ?: "outra disciplina"
            throw IllegalStateException(
                "Conflito de horário: já existe $conflictName em ${dayLabelFor(dayOfWeek)} às ${conflictSlot.startTime}."
            )
        }
    }

    override fun deleteScheduleSlot(slotId: String): Unit = synchronized(lock) {
        val removed = slots.removeAll { it.slotId == slotId }
        checkInStatusBySessionKey.keys.removeAll { it.startsWith("${slotId}_") }
        if (removed) {
            persistStateLocked()
            _mutationVersion.value += 1L
        }
    }

    override fun listCourseSlots(courseId: String): List<CourseScheduleSlotItem> = synchronized(lock) {
        slots
            .filter { it.courseId == courseId }
            .sortedWith(compareBy<SlotSeed> { it.dayOfWeek }.thenBy { it.startTime })
            .map { toCourseScheduleSlotItem(it) }
    }

    override fun listUfopTemplates(): List<UfopCurriculumTemplate> = synchronized(lock) {
        ufopTemplatesSeed.map { template ->
            UfopCurriculumTemplate(
                id = template.id,
                code = template.code,
                name = template.name,
                departmentName = template.departmentName,
                campusName = template.campusName,
                description = template.description,
                coursesCount = template.courses.size
            )
        }
    }

    override fun applyUfopTemplate(templateId: String, semester: String): Int = synchronized(lock) {
        val template = ufopTemplatesSeed.find { it.id == templateId }
            ?: throw IllegalArgumentException("Template UFOP não encontrado: $templateId")
        val cleanSemester = semester.trim().ifBlank { "2026.2" }

        val removedCourseIds = courses.values
            .filter { it.semester == cleanSemester }
            .map { it.courseId }
            .toSet()

        if (removedCourseIds.isNotEmpty()) {
            val removedSlotIds = slots
                .filter { it.courseId in removedCourseIds }
                .map { it.slotId }
                .toSet()
            slots.removeAll { it.courseId in removedCourseIds }
            checkInStatusBySessionKey.keys.removeAll { key ->
                removedSlotIds.any { slotId -> key.startsWith("${slotId}_") }
            }
            removedCourseIds.forEach { oldId ->
                courses.remove(oldId)
                courseColorOverrides.remove(oldId)
                notesByCourseId.remove(oldId)
                assessmentsByCourseId.remove(oldId)
            }
        }

        val activeCampus = activeCampusLocked()

        template.courses.forEach { tplCourse ->
            val deptOption = resolveDepartmentOption(tplCourse.departmentId)
            val resolvedColor = deptOption.colorHex
            val firstSlot = tplCourse.slots.firstOrNull()
            val buildingAndRoom = if (firstSlot != null) {
                "${firstSlot.building} · ${firstSlot.room}"
            } else {
                "Campus UFOP"
            }
            val shortLocation = firstSlot?.shortLocation ?: "UFOP"
            val maxAbsences = (tplCourse.workloadHours * 3) / 10

            courses[tplCourse.courseId] = CourseSeed(
                courseId = tplCourse.courseId,
                semester = cleanSemester,
                name = tplCourse.name,
                departmentId = deptOption.id,
                departmentName = deptOption.name,
                defaultColorHex = resolvedColor,
                workloadHours = tplCourse.workloadHours,
                baseAbsences = 0,
                maxAbsences = maxAbsences,
                baseCancelledClasses = 0,
                professor = tplCourse.professor,
                buildingAndRoom = buildingAndRoom,
                shortLocation = shortLocation,
                assessments = emptyList(),
                initialNotes = tplCourse.notes,
                code = deriveCourseCodeFallback(null, tplCourse.notes, tplCourse.courseId, tplCourse.name)
            )
            notesByCourseId[tplCourse.courseId] = tplCourse.notes
            assessmentsByCourseId[tplCourse.courseId] = mutableListOf()

            tplCourse.slots.forEachIndexed { idx, tplSlot ->
                val slotId = "slot_${template.id}_${tplCourse.courseId}_${tplSlot.dayOfWeek}_${idx + 1}"
                slots.add(
                    SlotSeed(
                        slotId = slotId,
                        courseId = tplCourse.courseId,
                        dayOfWeek = tplSlot.dayOfWeek,
                        startTime = tplSlot.startTime,
                        endTime = tplSlot.endTime,
                        building = tplSlot.building,
                        room = tplSlot.room,
                        shortLocation = tplSlot.shortLocation,
                        defaultTempC = activeCampus.weatherTempC,
                        weatherCondition = activeCampus.weatherCondition,
                        classWeight = computeClassWeightFromTimes(tplSlot.startTime, tplSlot.endTime)
                    )
                )
            }
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        template.courses.size
    }

    // ── Sprint 6: Perfil do Estudante ────────────────────────────────────────────────────────────

    override fun getStudentProfile(): StudentProfile = synchronized(lock) { studentProfile }

    override fun saveStudentProfile(profile: StudentProfile): StudentProfile = synchronized(lock) {
        val cleanSemester = profile.activeSemester.trim().ifBlank { "2026.2" }
        val defaultBounds = resolveDefaultSemesterBounds(cleanSemester)
        val parsedStart = parseIsoDateOrNull(profile.semesterStartDate)?.toString()
        val parsedEnd = parseIsoDateOrNull(profile.semesterEndDate)?.toString()
        val knownDefaultPairs = setOf(
            resolveDefaultSemesterBounds("2026.2"),
            resolveDefaultSemesterBounds("2027.1"),
            resolveDefaultSemesterBounds("2027.2")
        )
        val isStaleDefaultFromOtherSemester = parsedStart != null && parsedEnd != null &&
            (parsedStart to parsedEnd) != defaultBounds && (
                (parsedStart to parsedEnd) in knownDefaultPairs ||
                    (cleanSemester != studentProfile.activeSemester &&
                        parsedStart == studentProfile.semesterStartDate &&
                        parsedEnd == studentProfile.semesterEndDate)
                )
        val cleanStart = if (parsedStart == null || isStaleDefaultFromOtherSemester) {
            defaultBounds.first
        } else {
            parsedStart
        }
        val cleanEnd = if (parsedEnd == null || isStaleDefaultFromOtherSemester) {
            defaultBounds.second
        } else {
            parsedEnd
        }
        val matchedCampusId = resolveCampusIdFromText(profile.campusName)
        if (matchedCampusId != null) {
            activeCampusId = matchedCampusId
        }
        val resolvedCampusCity = ufopCampusesSeed.find { it.id == activeCampusId }?.city
            ?: profile.campusName.trim().ifBlank { "Ouro Preto" }

        studentProfile = profile.copy(
            fullName = profile.fullName.trim(),
            registrationNumber = profile.registrationNumber.trim(),
            courseName = profile.courseName.trim(),
            campusName = resolvedCampusCity,
            activeSemester = cleanSemester,
            semesterStartDate = cleanStart,
            semesterEndDate = cleanEnd,
            avatarPath = profile.avatarPath?.trim()?.takeIf { it.isNotEmpty() }
        )
        persistStateLocked()
        _mutationVersion.value += 1L
        studentProfile
    }

    // ── Sprint 6: Calendário Mensal, Semestres & Grade de Amigos ─────────────────────────────────

    override fun getMonthActivityIndicators(
        year: Int,
        month: Int,
        semester: String
    ): Map<Int, DayActivityIndicator> = synchronized(lock) {
        if (year !in 1900..2100 || month !in 1..12) return@synchronized emptyMap()
        val ym = YearMonth.of(year, month)
        val daysInMonth = ym.lengthOfMonth()
        val cleanSemester = semester.trim().ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        val semesterCourses = courses.values
            .filter { it.semester == cleanSemester }
            .associateBy { it.courseId }
        val slotsByDay = slots
            .filter { it.courseId in semesterCourses }
            .groupBy { it.dayOfWeek }
        val assessmentsByDate = mutableMapOf<String, MutableList<CourseSeed>>()
        semesterCourses.values.forEach { course ->
            assessmentsByCourseId[course.courseId].orEmpty().forEach { assessment ->
                assessmentsByDate.getOrPut(assessment.dueDate) { mutableListOf() }.add(course)
            }
        }
        val commitmentsByDate = commitments.groupBy { it.isoDate }

        val startBound = parseIsoDateOrNull(studentProfile.semesterStartDate)
        val endBound = parseIsoDateOrNull(studentProfile.semesterEndDate)
        val enforceBounds = cleanSemester == studentProfile.activeSemester &&
            startBound != null &&
            endBound != null &&
            !endBound.isBefore(startBound)

        val result = linkedMapOf<Int, DayActivityIndicator>()
        for (day in 1..daysInMonth) {
            val date = ym.atDay(day)
            val iso = date.toString()
            val inBounds = if (enforceBounds) !date.isBefore(startBound) && !date.isAfter(endBound) else true
            val daySlots = if (inBounds) slotsByDay[date.dayOfWeek.value].orEmpty() else emptyList()
            val dayCommitments = commitmentsByDate[iso].orEmpty()
            val dayAssessmentCourses = assessmentsByDate[iso].orEmpty()

            val colors = buildList {
                daySlots.forEach { slot ->
                    semesterCourses[slot.courseId]?.let { add(resolveCourseColor(it)) }
                }
                dayAssessmentCourses.forEach { course ->
                    add(resolveCourseColor(course))
                }
                dayCommitments.forEach { comm ->
                    add(comm.colorHex)
                }
            }.distinct().take(3)

            result[day] = DayActivityIndicator(
                isoDate = iso,
                hasClasses = daySlots.isNotEmpty(),
                hasCommitments = dayCommitments.isNotEmpty(),
                hasAssessments = dayAssessmentCourses.isNotEmpty(),
                colorsHex = colors
            )
        }
        result
    }

    override fun listAvailableSemesters(): List<String> = synchronized(lock) {
        val all = linkedSetOf<String>()
        studentProfile.activeSemester.trim().takeIf { it.isNotEmpty() }?.let { all.add(it) }
        courses.values.forEach { c ->
            c.semester.trim().takeIf { it.isNotEmpty() }?.let { all.add(it) }
        }
        transcriptEntries.forEach { tx ->
            tx.semester.trim().takeIf { it.isNotEmpty() }?.let { all.add(it) }
        }
        if (all.isEmpty()) {
            all.add("2026.2")
        }
        all.sortedDescending()
    }

    override fun listAvailableCalendarSemesters(): List<String> = synchronized(lock) {
        (calendarEventsBySemester.keys +
            calendarEventsSeed.map { it.semester } +
            listOf(studentProfile.activeSemester.trim().ifBlank { "2026.2" }))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    override fun listFriendSchedules(): List<FriendScheduleInfo> = synchronized(lock) {
        friendSchedules.map { toFriendScheduleInfoLocked(it) }
    }

    override fun importFriendScheduleJson(
        friendName: String,
        jsonPayload: String
    ): FriendScheduleInfo = synchronized(lock) {
        val trimmed = jsonPayload.trim()
        require(trimmed.isNotEmpty()) { "Cole o JSON ou selecione um arquivo .ufopgrade." }

        val root = try {
            JSONObject(trimmed)
        } catch (_: Throwable) {
            throw IllegalArgumentException("JSON inválido. Use o arquivo .ufopgrade ou payload exportado pelo app.")
        }
        val jsonFriendName = root.optNonNullString("friendName")
            .ifBlank { root.optNonNullString("friend_name") }
            .ifBlank { root.optNonNullString("name") }
            .trim()
        val cleanName = friendName.trim().ifBlank { jsonFriendName }.ifBlank { "Amigo UFOP" }
        val courseName = root.optNonNullString("courseName")
            .ifBlank { root.optNonNullString("course_name") }
            .trim()
            .let { if (it.isBlank()) "" else normalizeUfopPortugueseText(it) }
        val avatarBase64 = root.optNonNullString("avatarBase64")
            .ifBlank { root.optNonNullString("avatar_base64") }
            .trim()
            .takeIf { it.isNotEmpty() }
        val semester = root.optNonNullString("semester", studentProfile.activeSemester)
            .ifBlank { "2026.2" }

        val parsedCourses = mutableListOf<FriendCourseEntry>()
        val coursesArr = root.optJSONArray("courses")
        if (coursesArr != null) {
            for (i in 0 until coursesArr.length()) {
                val obj = coursesArr.optJSONObject(i) ?: continue
                val id = obj.optNonNullString("courseId")
                    .ifBlank { obj.optNonNullString("id") }
                    .trim()
                val rawName = obj.optNonNullString("name").trim()
                if (id.isEmpty() || rawName.isEmpty()) continue
                val name = normalizeUfopPortugueseText(rawName)
                val deptId = resolveDepartmentOption(
                    obj.optNonNullString("departmentId")
                        .ifBlank { obj.optNonNullString("department_id", "decom") }
                ).id
                val code = deriveCourseCodeFallback(
                    explicitCode = obj.optNonNullString("code"),
                    notes = "",
                    courseId = id,
                    courseName = name
                )
                parsedCourses.add(FriendCourseEntry(courseId = id, name = name, departmentId = deptId, code = code))
            }
        }

        val parsedSlots = mutableListOf<FriendSlotEntry>()
        val slotsArr = root.optJSONArray("slots")
        if (slotsArr != null) {
            for (i in 0 until slotsArr.length()) {
                val obj = slotsArr.optJSONObject(i) ?: continue
                val slotId = obj.optNonNullString("slotId")
                    .ifBlank { obj.optNonNullString("slot_id", "fs_${i + 1}") }
                    .trim()
                val courseId = obj.optNonNullString("courseId")
                    .ifBlank { obj.optNonNullString("course_id") }
                    .trim()
                if (courseId.isEmpty()) continue
                val day = obj.optInt("dayOfWeek", obj.optInt("day_of_week", 1)).coerceIn(1, 7)
                val start = obj.optNonNullString("startTime")
                    .ifBlank { obj.optNonNullString("start_time", "08:00") }
                    .ifBlank { "08:00" }
                val end = obj.optNonNullString("endTime")
                    .ifBlank { obj.optNonNullString("end_time", "09:40") }
                    .ifBlank { "09:40" }
                val shortLoc = obj.optNonNullString("shortLocation")
                    .ifBlank { obj.optNonNullString("short_location") }
                    .ifBlank {
                        val b = obj.optNonNullString("building")
                        val r = obj.optNonNullString("room")
                        buildShortLocation(b, r)
                    }
                parsedSlots.add(
                    FriendSlotEntry(
                        slotId = slotId,
                        courseId = courseId,
                        dayOfWeek = day,
                        startTime = start,
                        endTime = end,
                        shortLocation = shortLoc
                    )
                )
            }
        }

        require(parsedCourses.isNotEmpty() || parsedSlots.isNotEmpty()) {
            "Nenhuma disciplina ou horário encontrado no arquivo .ufopgrade."
        }

        friendSchedules.removeAll {
            it.friendName.equals(cleanName, ignoreCase = true) && it.semester == semester
        }
        generatedIdCounter += 1L
        val entry = FriendScheduleEntry(
            id = "friend_$generatedIdCounter",
            friendName = cleanName,
            courseName = courseName,
            semester = semester,
            avatarBase64 = avatarBase64,
            courses = parsedCourses,
            slots = parsedSlots
        )
        friendSchedules.add(entry)
        persistStateLocked()
        _mutationVersion.value += 1L
        toFriendScheduleInfoLocked(entry)
    }

    override fun removeFriendSchedule(friendId: String): Unit = synchronized(lock) {
        val removed = friendSchedules.removeAll { it.id == friendId }
        if (removed) {
            persistStateLocked()
            _mutationVersion.value += 1L
        }
    }

    override fun getFriendTimetable(friendId: String): WeeklyTimetable? = synchronized(lock) {
        friendSchedules.find { it.id == friendId }?.let { toFriendScheduleInfoLocked(it).timetable }
    }

    private fun toFriendScheduleInfoLocked(entry: FriendScheduleEntry): FriendScheduleInfo {
        val courseById = entry.courses.associateBy { it.courseId }
        val entries = entry.slots.mapNotNull { slot ->
            val fc = courseById[slot.courseId]
            val dept = resolveDepartmentOption(fc?.departmentId ?: "decom")
            val resolvedName = fc?.name ?: slot.courseId
            val resolvedCode = deriveCourseCodeFallback(fc?.code, "", slot.courseId, resolvedName)
            TimetableEntry(
                dayOfWeek = slot.dayOfWeek,
                startTime = slot.startTime,
                endTime = slot.endTime,
                cell = TimetableCell(
                    slotId = slot.slotId,
                    courseId = slot.courseId,
                    courseName = resolvedName,
                    shortLocation = slot.shortLocation.ifBlank { "UFOP" },
                    departmentId = dept.id,
                    departmentName = dept.name,
                    colorHex = dept.colorHex,
                    code = resolvedCode
                )
            )
        }
        return FriendScheduleInfo(
            id = entry.id,
            friendName = entry.friendName,
            courseName = entry.courseName,
            semester = entry.semester,
            avatarBase64 = entry.avatarBase64,
            timetable = buildTimetable(
                semester = entry.semester,
                gridTitle = "Grade de ${entry.friendName} · ${entry.semester}",
                entries = entries
            )
        )
    }

    // ── Sprint 6: Clima Leve Online (Open-Meteo, Throttle >= 5 min) ──────────────────────────────

    override fun refreshWeatherIfNeeded(force: Boolean): UfopCampusInfo {
        val targetCampus = synchronized(lock) {
            val current = activeCampusLocked()
            val now = System.currentTimeMillis()
            val hasCachedCampus = current.id in campusWeatherById
            val withinCacheWindow = lastWeatherFetchEpochMs > 0L &&
                (now - lastWeatherFetchEpochMs) < WEATHER_REFRESH_INTERVAL_MS &&
                hasCachedCampus
            val withinFailureCooldown = !force &&
                lastWeatherAttemptEpochMs > 0L &&
                (now - lastWeatherAttemptEpochMs) < 15_000L

            if (weatherFetchInFlight || (!force && (withinCacheWindow || withinFailureCooldown))) {
                return current
            }
            weatherFetchInFlight = true
            lastWeatherAttemptEpochMs = now
            current
        }

        if (isMainThread()) {
            Thread(
                {
                    try {
                        performWeatherFetch(targetCampus.id)
                    } finally {
                        weatherFetchInFlight = false
                    }
                },
                "ufop-weather-refresh"
            ).apply {
                isDaemon = true
                start()
            }
            return synchronized(lock) { activeCampusLocked() }
        }

        return try {
            performWeatherFetch(targetCampus.id)
            synchronized(lock) { activeCampusLocked() }
        } finally {
            weatherFetchInFlight = false
        }
    }

    private fun performWeatherFetch(campusId: String) {
        val (lat, lon) = when (campusId) {
            "mar_ichs" -> -20.3778 to -43.4161
            "jm_icea" -> -19.8100 to -43.1736
            else -> -20.3855 to -43.5036
        }
        val endpoint = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&forecast_days=1"

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = WEATHER_TIMEOUT_MS
                readTimeout = WEATHER_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "CampusUFOP/1.0")
            }
            if (conn.responseCode !in 200..299) return
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val current = JSONObject(body).optJSONObject("current") ?: return
            if (!current.has("temperature_2m")) return
            val tempC = current.getDouble("temperature_2m").roundToInt()
            val condition = mapWmoWeatherCode(current.optInt("weather_code", 0))

            synchronized(lock) {
                campusWeatherById[campusId] = CampusWeather(tempC = tempC, condition = condition)
                lastWeatherFetchEpochMs = System.currentTimeMillis()
                persistStateLocked()
                _mutationVersion.value += 1L
            }
            onMutation?.invoke()
        } catch (_: Throwable) {
            // Falha silenciosa offline/timeout: mantém temperatura em cache.
        } finally {
            conn?.disconnect()
        }
    }

    private fun mapWmoWeatherCode(code: Int): String = when (code) {
        0 -> "Ensolarado"
        1, 2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Neblina"
        51, 53, 55, 56, 57 -> "Garoa"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Chuvoso"
        71, 73, 75, 77, 85, 86 -> "Neve"
        95, 96, 99 -> "Tempestade"
        else -> "Parcialmente nublado"
    }

    private fun isMainThread(): Boolean = try {
        android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
    } catch (_: Throwable) {
        false
    }

    // ── Sprint 6: Parser do ATESTADO DE MATRÍCULA UFOP (PDF com Coordenadas + Texto) ─────────────

    private data class PdfWord(
        val x0: Float,
        val x1: Float,
        val y: Float,
        val text: String
    ) {
        val xCenter: Float get() = (x0 + x1) * 0.5f
    }

    private data class PdfTimeRow(
        val y: Float,
        val startTime: String,
        val endTime: String
    )

    private fun isEadIndicatorText(raw: String): Boolean =
        raw.isNotBlank() && EAD_INDICATOR_REGEX.containsMatchIn(raw)

    private data class ParsedGridToken(
        val courseCode: String,
        val turma: String,
        val kind: String, // "T" ou "P"
        val dayOfWeek: Int,
        val startTime: String,
        val endTime: String,
        val hasEadMarker: Boolean = false
    )

    private data class ParsedCourseTableRow(
        val code: String,
        val turma: String,
        val rawTitle: String,
        val building: String,
        val language: String,
        val isEad: Boolean = false
    )

    private data class ParsedCertificateMeta(
        val semester: String,
        val fullName: String?,
        val registrationNumber: String?,
        val courseName: String?,
        val campusId: String?,
        val campusName: String?,
        val semesterStartDate: String?,
        val semesterEndDate: String?
    )

    override fun importEnrollmentCertificatePdf(pdfBytes: ByteArray, completeOnboarding: Boolean): Int {
        require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
        return SecurePdfProcessor.withZeroedPdfBytes(pdfBytes) { safeBytes ->
            val (lines, words) = extractPdfLinesAndWords(safeBytes)
            importCertificateFromLinesAndWords(lines, words, completeOnboarding)
        }
    }

    override fun importEnrollmentCertificateText(rawText: String, completeOnboarding: Boolean): Int {
        val trimmed = rawText.trim()
        require(trimmed.isNotEmpty()) { "Texto do atestado vazio." }
        val lines = trimmed.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        return importCertificateFromLinesAndWords(lines, emptyList(), completeOnboarding)
    }

    private fun extractPdfLinesAndWords(pdfBytes: ByteArray): Pair<List<String>, List<PdfWord>> {
        require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
        data class PdfGlyph(
            val x0: Float,
            val x1: Float,
            val y: Float,
            val spaceWidth: Float,
            val ch: String
        )

        val glyphs = mutableListOf<PdfGlyph>()
        val rawText = SecurePdfProcessor.loadDocumentInMemoryOnly(pdfBytes).use { doc ->
            val stripper = object : PDFTextStripper() {
                init {
                    sortByPosition = true
                }

                override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                    if (textPositions != null) {
                        val pageOffset = (currentPageNo - 1).coerceAtLeast(0) * 1000f
                        for (tp in textPositions) {
                            val ch = tp.unicode ?: continue
                            if (ch.isBlank()) continue
                            glyphs.add(
                                PdfGlyph(
                                    x0 = tp.xDirAdj,
                                    x1 = tp.xDirAdj + tp.widthDirAdj,
                                    y = tp.yDirAdj + pageOffset,
                                    spaceWidth = tp.widthOfSpace,
                                    ch = ch
                                )
                            )
                        }
                    }
                    super.writeString(text, textPositions)
                }
            }
            stripper.getText(doc)
        }

        if (glyphs.isEmpty()) {
            val fallbackLines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            return fallbackLines to emptyList()
        }

        // Agrupa glifos em linhas por coordenada Y e depois em palavras pelo espaçamento horizontal.
        val sortedGlyphs = glyphs.sortedWith(compareBy<PdfGlyph> { it.y }.thenBy { it.x0 })
        val rowBuckets = mutableListOf<MutableList<PdfGlyph>>()
        for (g in sortedGlyphs) {
            val lastRow = rowBuckets.lastOrNull()
            if (lastRow != null && abs(g.y - lastRow[0].y) <= 3.0f) {
                lastRow.add(g)
            } else {
                rowBuckets.add(mutableListOf(g))
            }
        }

        val words = mutableListOf<PdfWord>()
        val lines = mutableListOf<String>()
        for (row in rowBuckets) {
            row.sortBy { it.x0 }
            val rowWords = mutableListOf<PdfWord>()
            val currentWord = StringBuilder()
            var wordX0 = 0f
            var wordX1 = 0f
            var wordYSum = 0f
            var wordCount = 0

            fun flushWord() {
                if (currentWord.isNotEmpty() && wordCount > 0) {
                    rowWords.add(
                        PdfWord(
                            x0 = wordX0,
                            x1 = wordX1,
                            y = wordYSum / wordCount,
                            text = currentWord.toString()
                        )
                    )
                    currentWord.setLength(0)
                    wordCount = 0
                }
            }

            for (g in row) {
                if (wordCount == 0) {
                    wordX0 = g.x0
                    wordX1 = g.x1
                    wordYSum = g.y
                    wordCount = 1
                    currentWord.append(g.ch)
                } else {
                    val gapThreshold = (g.spaceWidth * 0.5f).coerceIn(1.2f, 2.2f)
                    if (g.x0 - wordX1 > gapThreshold) {
                        flushWord()
                        wordX0 = g.x0
                        wordX1 = g.x1
                        wordYSum = g.y
                        wordCount = 1
                        currentWord.append(g.ch)
                    } else {
                        wordX1 = maxOf(wordX1, g.x1)
                        wordYSum += g.y
                        wordCount += 1
                        currentWord.append(g.ch)
                    }
                }
            }
            flushWord()

            if (rowWords.isNotEmpty()) {
                words.addAll(rowWords)
                lines.add(rowWords.joinToString(" ") { it.text })
            }
        }
        return lines to words
    }

    private fun importCertificateFromLinesAndWords(
        lines: List<String>,
        words: List<PdfWord>,
        completeOnboarding: Boolean = true
    ): Int {
        val meta = parseCertificateMeta(lines)
        val tableRows = parseCourseTable(lines)
        val gridTokens = if (words.isNotEmpty()) {
            parseGridTokensByCoordinates(words).ifEmpty { parseGridTokensFromText(lines) }
        } else {
            parseGridTokensFromText(lines)
        }

        val effectiveRows = if (tableRows.isNotEmpty()) {
            tableRows
        } else {
            // Sintetiza disciplinas caso apenas os tokens da grade tenham sido extraídos.
            gridTokens.distinctBy { it.courseCode }.map { token ->
                ParsedCourseTableRow(
                    code = token.courseCode,
                    turma = token.turma,
                    rawTitle = token.courseCode,
                    building = "Campus UFOP",
                    language = "PORTUGUES"
                )
            }
        }

        require(effectiveRows.isNotEmpty()) {
            "Não foi possível identificar disciplinas neste atestado de matrícula da UFOP."
        }

        return synchronized(lock) {
            applyImportedCertificateLocked(meta, effectiveRows, gridTokens, completeOnboarding)
        }
    }

    private fun parseCertificateMeta(lines: List<String>): ParsedCertificateMeta {
        val joined = lines.joinToString("\n")
        val collapsed = lines.joinToString(" ").replace(Regex("\\s+"), " ").trim()

        val semesterMatch = Regex("""ATESTADO\s+DE\s+MATR[IÍ]CULA\s+(\d{4})[/.](\d)""", RegexOption.IGNORE_CASE)
            .find(collapsed)
        val semester = if (semesterMatch != null) {
            "${semesterMatch.groupValues[1]}.${semesterMatch.groupValues[2]}"
        } else {
            "2026.2"
        }

        val nameMatch = Regex("""Atestamos\s+que\s+(.+?)\s+matr[ií]cula\b""", RegexOption.IGNORE_CASE)
            .find(collapsed)
        val fullName = nameMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { toPortugueseTitleCase(it) }

        val regMatch = Regex("""matr[ií]cula\s+n[º°o.]+\s*([\d.\-/]+)""", RegexOption.IGNORE_CASE)
            .find(collapsed)
            ?: Regex("""\b(\d{2}\.\d\.\d{3,5})\b""").find(collapsed)
        val registrationNumber = regMatch?.groupValues?.getOrNull(1)?.trimEnd(',', '.', ';')
            ?.takeIf { it.isNotEmpty() }

        val courseMatch = Regex("""no\s+curso\s+de\s+(.+?),\s*cuja\b""", RegexOption.IGNORE_CASE)
            .find(collapsed)
        val courseName = courseMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { toPortugueseTitleCase(it) }

        val campusMatch = Regex(
            """no\s+campus\s+([A-Za-zÀ-ú\s]+?)(?:\.|,|\s+Hor[aá]rio|$)""",
            RegexOption.IGNORE_CASE
        ).find(collapsed)
        val rawCampus = campusMatch?.groupValues?.getOrNull(1)?.trim()
        val campusId = rawCampus?.let { resolveCampusIdFromText(it) }
        val campusName = campusId?.let { id -> ufopCampusesSeed.find { it.id == id }?.city }

        val startMatch = Regex(
            """In[ií]cio\s+das\s+Aulas:\s*(\d{1,2})\s+de\s+([A-Za-zÀ-ú]+)\s+de\s+(\d{4})""",
            RegexOption.IGNORE_CASE
        ).find(joined)
        val startDate = startMatch?.let { parsePortugueseLongDate(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }

        val endMatch = Regex(
            """T[eé]rmino\s+das\s+Aulas:\s*(\d{1,2})\s+de\s+([A-Za-zÀ-ú]+)\s+de\s+(\d{4})""",
            RegexOption.IGNORE_CASE
        ).find(joined)
        val endDate = endMatch?.let { parsePortugueseLongDate(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }

        return ParsedCertificateMeta(
            semester = semester,
            fullName = fullName,
            registrationNumber = registrationNumber,
            courseName = courseName,
            campusId = campusId,
            campusName = campusName,
            semesterStartDate = startDate,
            semesterEndDate = endDate
        )
    }

    private fun parseCourseTable(lines: List<String>): List<ParsedCourseTableRow> {
        val knownBuildings = "ENSINO\\s+[AÀ]\\s+DIST[AÂ]NCIA|[AÀ]\\s+DIST[AÂ]NCIA|E\\.A\\.D\\.|EAD|CEAD|AVA|MOODLE|ONLINE|VIRTUAL|REMOT[AO]|ICEB(?:\\s*\\(?(?:I|II|III)\\)?)?|ESCOLA\\s+DE\\s+MINAS|ICHS|ICSA|ICEA|EMED|EFAR|EDTM|ENUT|CEDUFOP|DECOM|DEFIS|DEMAT|DEGEO|DEPRO|DECAT|DECIV|DEURB|DEART|DEMUS|DELET|DEEDU|DEPED|DEFAR|DENUT|DEENF|DEMED|DEBIO|DEQUI|DEAMB|DEARQ|DEHIS|DEDIR|DEECO|DEJOR|DESSO|DETUR|DEELT|DEMEC|DEMET|DEMIN|DECSI|DECEA|DEENP|DEALI|DEFIL|EM"
        val strictRowRegex = Regex(
            """^([A-Z]{3}\d{3})\s+(\d{1,3})\s+(.+?)\s+($knownBuildings)\s+(PORTUGU[EÊ]S|INGL[EÊ]S|ESPANHOL)\b""",
            RegexOption.IGNORE_CASE
        )
        val fallbackRowRegex = Regex(
            """^([A-Z]{3}\d{3})\s+(\d{1,3})\s+(.+?)\s+([A-Z0-9]{2,12})\s+(PORTUGU[EÊ]S|INGL[EÊ]S|ESPANHOL)\b""",
            RegexOption.IGNORE_CASE
        )

        val results = mutableListOf<ParsedCourseTableRow>()
        val seenCodes = mutableSetOf<String>()
        for (rawLine in lines) {
            val line = rawLine.trim().replace(Regex("\\s+"), " ")
            val match = strictRowRegex.find(line) ?: fallbackRowRegex.find(line) ?: continue
            val code = match.groupValues[1].uppercase()
            if (!seenCodes.add(code)) continue
            val rawTitle = match.groupValues[3].trim()
            val building = match.groupValues[4].trim()
            val isEad = isEadIndicatorText(building) ||
                isEadIndicatorText(rawTitle) ||
                isEadIndicatorText(line)
            results.add(
                ParsedCourseTableRow(
                    code = code,
                    turma = match.groupValues[2].trim(),
                    rawTitle = rawTitle,
                    building = building,
                    language = match.groupValues[5].trim(),
                    isEad = isEad
                )
            )
        }
        return results
    }

    private fun parseGridTokensByCoordinates(words: List<PdfWord>): List<ParsedGridToken> {
        val headerCandidates = mutableListOf<Pair<Int, PdfWord>>()
        for (word in words) {
            val day = parseWeekdayHeaderWord(word.text) ?: continue
            headerCandidates.add(day to word)
        }
        if (headerCandidates.isEmpty()) return emptyList()

        // Seleciona a linha horizontal que concentra os cabeçalhos dos dias (Segunda..Sábado).
        val headerRows = mutableListOf<MutableList<Pair<Int, PdfWord>>>()
        for (candidate in headerCandidates.sortedBy { it.second.y }) {
            val bucket = headerRows.lastOrNull()
            if (bucket != null && abs(candidate.second.y - bucket[0].second.y) <= 8f) {
                bucket.add(candidate)
            } else {
                headerRows.add(mutableListOf(candidate))
            }
        }
        val bestHeaderRow = headerRows.maxByOrNull { row -> row.map { it.first }.distinct().size }
            ?: return emptyList()
        val dayColumns = linkedMapOf<Int, Float>()
        bestHeaderRow.forEach { (day, word) ->
            dayColumns.putIfAbsent(day, word.xCenter)
        }
        if (dayColumns.size < 3) return emptyList()

        // Agrupa palavras em linhas visuais para identificar os horários na coluna da esquerda.
        val rowBuckets = mutableListOf<MutableList<PdfWord>>()
        for (word in words.sortedWith(compareBy<PdfWord> { it.y }.thenBy { it.x0 })) {
            val last = rowBuckets.lastOrNull()
            if (last != null && abs(word.y - last[0].y) <= 3.5f) {
                last.add(word)
            } else {
                rowBuckets.add(mutableListOf(word))
            }
        }

        val timeRegex = Regex("""(\d{2}:\d{2})\s*-\s*(\d{2}:\d{2})""")
        val timeRows = mutableListOf<PdfTimeRow>()
        for (row in rowBuckets) {
            val rowText = row.sortedBy { it.x0 }.joinToString(" ") { it.text }
            val m = timeRegex.find(rowText) ?: continue
            val avgY = row.map { it.y }.average().toFloat()
            timeRows.add(PdfTimeRow(y = avgY, startTime = m.groupValues[1], endTime = m.groupValues[2]))
        }
        if (timeRows.isEmpty()) return emptyList()

        val tokenRegex = Regex("""([A-Z]{3}\d{3})\s*-\s*(\d{1,3})\s*([TP])""", RegexOption.IGNORE_CASE)
        val cellWords = linkedMapOf<Pair<Int, Int>, MutableList<PdfWord>>()
        for (word in words) {
            val nearestRowIdx = timeRows.indices.minByOrNull { abs(word.y - timeRows[it].y) } ?: continue
            if (abs(word.y - timeRows[nearestRowIdx].y) > 10f) continue
            val nearestCol = dayColumns.entries.minByOrNull { abs(word.xCenter - it.value) } ?: continue
            if (abs(word.xCenter - nearestCol.value) > 65f) continue
            cellWords.getOrPut(nearestRowIdx to nearestCol.key) { mutableListOf() }.add(word)
        }

        val tokens = mutableListOf<ParsedGridToken>()
        for ((cellKey, cellList) in cellWords) {
            val (rowIdx, dayOfWeek) = cellKey
            val row = timeRows[rowIdx]
            val cellText = cellList.sortedBy { it.x0 }.joinToString(" ") { it.text }
            val cellIsEad = isEadIndicatorText(cellText)
            tokenRegex.findAll(cellText).forEach { m ->
                tokens.add(
                    ParsedGridToken(
                        courseCode = m.groupValues[1].uppercase(),
                        turma = m.groupValues[2],
                        kind = m.groupValues[3].uppercase(),
                        dayOfWeek = dayOfWeek,
                        startTime = row.startTime,
                        endTime = row.endTime,
                        hasEadMarker = cellIsEad
                    )
                )
            }
        }
        return tokens
    }

    private fun parseGridTokensFromText(lines: List<String>): List<ParsedGridToken> {
        val headerLine = lines.firstOrNull { line ->
            val norm = normalizeSearchText(line)
            norm.contains("segunda") && norm.contains("terca")
        }
        val headerDayCenters = mutableMapOf<Int, Float>()
        if (headerLine != null) {
            val dayNames = listOf(
                1 to "segunda", 2 to "terca", 3 to "quarta",
                4 to "quinta", 5 to "sexta", 6 to "sabado"
            )
            // Preserva espaços iniciais do cabeçalho para alinhar colunas com as linhas de horário.
            val normHeader = stripDiacriticsLower(headerLine)
            for ((day, name) in dayNames) {
                val idx = normHeader.indexOf(name)
                if (idx >= 0) {
                    headerDayCenters[day] = idx + name.length * 0.5f
                }
            }
        }
        val hasAlignedColumns = headerLine != null && (headerLine.contains("  ") || headerLine.contains("\t"))

        val rowRegex = Regex("""^\s*(\d{2}:\d{2})\s*-\s*(\d{2}:\d{2})\b(.*)$""")
        val tokenRegex = Regex("""([A-Z]{3}\d{3})\s*-\s*(\d{1,3})\s*([TP])""", RegexOption.IGNORE_CASE)
        val tokens = mutableListOf<ParsedGridToken>()

        for (line in lines) {
            val rowMatch = rowRegex.find(line) ?: continue
            val startTime = rowMatch.groupValues[1]
            val endTime = rowMatch.groupValues[2]
            val matches = tokenRegex.findAll(line).toList()
            matches.forEachIndexed { idx, m ->
                val dayOfWeek = if (hasAlignedColumns && headerDayCenters.isNotEmpty()) {
                    val tokenCenter = (m.range.first + m.range.last) * 0.5f
                    headerDayCenters.entries.minByOrNull { abs(tokenCenter - it.value) }?.key
                        ?: (idx + 1).coerceIn(1, 6)
                } else {
                    (idx + 1).coerceIn(1, 6)
                }
                val sliceEnd = matches.getOrNull(idx + 1)?.range?.first ?: line.length
                val localSlice = line.substring(m.range.first, sliceEnd)
                tokens.add(
                    ParsedGridToken(
                        courseCode = m.groupValues[1].uppercase(),
                        turma = m.groupValues[2],
                        kind = m.groupValues[3].uppercase(),
                        dayOfWeek = dayOfWeek,
                        startTime = startTime,
                        endTime = endTime,
                        hasEadMarker = isEadIndicatorText(localSlice)
                    )
                )
            }
        }
        return tokens
    }

    private fun applyImportedCertificateLocked(
        meta: ParsedCertificateMeta,
        tableRows: List<ParsedCourseTableRow>,
        gridTokens: List<ParsedGridToken>,
        completeOnboarding: Boolean = true
    ): Int {
        val semester = meta.semester.ifBlank { "2026.2" }

        // Remove apenas disciplinas e horários do semestre sendo reimportado (preserva semestres anteriores).
        val removedCourseIds = courses.values
            .filter { it.semester == semester }
            .map { it.courseId }
            .toSet()
        if (removedCourseIds.isNotEmpty()) {
            val removedSlotIds = slots
                .filter { it.courseId in removedCourseIds }
                .map { it.slotId }
                .toSet()
            slots.removeAll { it.courseId in removedCourseIds }
            checkInStatusBySessionKey.keys.removeAll { key ->
                removedSlotIds.any { slotId -> key.startsWith("${slotId}_") }
            }
            removedCourseIds.forEach { oldId ->
                courses.remove(oldId)
                courseColorOverrides.remove(oldId)
                notesByCourseId.remove(oldId)
                assessmentsByCourseId.remove(oldId)
            }
        }

        if (meta.campusId != null) {
            activeCampusId = meta.campusId
        }
        val activeCampus = activeCampusLocked()

        val tokensByCourse = gridTokens.groupBy { it.courseCode }
        val semesterSlug = semester.replace(".", "_")

        for (row in tableRows) {
            val courseTokens = tokensByCourse[row.code].orEmpty()
            val weeklySlotsCount = courseTokens.size
            val workloadHours = if (weeklySlotsCount <= 0) {
                60
            } else {
                (((weeklySlotsCount * 15) + 7) / 15 * 15).coerceIn(15, 180)
            }
            val maxAbsences = (workloadHours * 3) / 10
            val dept = resolveDepartmentForCourseCode(row.code)
            val prettyName = normalizeUfopPortugueseText(row.rawTitle)
            val isEadCourse = row.isEad || courseTokens.any { it.hasEadMarker }
            val prettyBuilding = if (isEadCourse) {
                "Ensino a Distância (EAD)"
            } else {
                prettyBuildingName(row.building)
            }
            val shortLoc = if (isEadCourse) {
                "EAD"
            } else {
                buildCertificateShortLocation(prettyBuilding)
            }
            val courseId = "${row.code.lowercase()}_$semesterSlug"
            val knownTranscriptProfessor = transcriptEntries.firstOrNull {
                it.semester == semester && it.code.equals(row.code, ignoreCase = true) && it.professor.isNotBlank()
            }?.professor?.trim()
            val professorLabel = knownTranscriptProfessor.orEmpty()

            // Agrupa horários consecutivos de 50 min da mesma disciplina e mesmo tipo (T/P) no mesmo dia.
            val mergedSlots = mergeConsecutiveGridTokens(courseTokens)
            val activeAllocIndex = roomAllocationIndex?.takeIf { it.semester == semester }
            val createdCourseSlots = mutableListOf<SlotSeed>()

            mergedSlots.forEachIndexed { idx, merged ->
                val slotId = "slot_${courseId}_d${merged.dayOfWeek}_${merged.startTime.replace(":", "")}_${idx + 1}"
                val slotBuilding: String
                val slotRoom: String
                val slotShortLoc: String
                if (isEadCourse) {
                    slotBuilding = "Ensino a Distância (EAD)"
                    slotRoom = ""
                    slotShortLoc = "EAD"
                } else {
                    val match = activeAllocIndex?.lookup(
                        courseCode = row.code,
                        turma = row.turma,
                        dayOfWeek = merged.dayOfWeek,
                        startTime = merged.startTime,
                        kind = merged.kind
                    )
                    if (match != null) {
                        slotBuilding = match.building
                        slotRoom = match.room
                        slotShortLoc = buildRoomShortLocation(match.building, match.room)
                    } else {
                        slotBuilding = prettyBuilding
                        slotRoom = ""
                        slotShortLoc = buildCertificateShortLocation(prettyBuilding)
                    }
                }
                val newSlot = SlotSeed(
                    slotId = slotId,
                    courseId = courseId,
                    dayOfWeek = merged.dayOfWeek,
                    startTime = merged.startTime,
                    endTime = merged.endTime,
                    building = slotBuilding,
                    room = slotRoom,
                    shortLocation = slotShortLoc,
                    defaultTempC = activeCampus.weatherTempC,
                    weatherCondition = activeCampus.weatherCondition,
                    classWeight = merged.weight
                )
                createdCourseSlots.add(newSlot)
                slots.add(newSlot)
            }

            val buildingAndRoomLabel = if (isEadCourse) {
                "Ensino a Distância (EAD)"
            } else {
                buildCleanCourseBuildingAndRoom(prettyBuilding, createdCourseSlots)
            }
            val resolvedCourseShortLoc = if (isEadCourse) {
                "EAD"
            } else {
                createdCourseSlots.firstOrNull { it.room.isNotBlank() }?.shortLocation ?: shortLoc
            }

            courses[courseId] = CourseSeed(
                courseId = courseId,
                semester = semester,
                name = prettyName,
                departmentId = dept.id,
                departmentName = dept.name,
                defaultColorHex = dept.colorHex,
                workloadHours = workloadHours,
                baseAbsences = 0,
                maxAbsences = maxAbsences,
                baseCancelledClasses = 0,
                professor = professorLabel,
                buildingAndRoom = buildingAndRoomLabel,
                shortLocation = resolvedCourseShortLoc,
                assessments = emptyList(),
                initialNotes = "Código: ${row.code} · Turma ${row.turma} · ${normalizeUfopPortugueseText(row.language)}",
                code = row.code.uppercase()
            )
            notesByCourseId[courseId] = courses[courseId]?.initialNotes.orEmpty()
            assessmentsByCourseId[courseId] = mutableListOf()
        }

        val defaultBounds = resolveDefaultSemesterBounds(semester)
        studentProfile = studentProfile.copy(
            fullName = meta.fullName ?: studentProfile.fullName,
            registrationNumber = meta.registrationNumber ?: studentProfile.registrationNumber,
            courseName = meta.courseName ?: studentProfile.courseName,
            campusName = meta.campusName ?: activeCampus.city,
            activeSemester = semester,
            semesterStartDate = meta.semesterStartDate ?: defaultBounds.first,
            semesterEndDate = meta.semesterEndDate ?: defaultBounds.second,
            onboardingCompleted = if (completeOnboarding) true else studentProfile.onboardingCompleted
        )

        persistStateLocked()
        _mutationVersion.value += 1L
        return tableRows.size
    }

    private data class MergedGridSlot(
        val dayOfWeek: Int,
        val startTime: String,
        val endTime: String,
        val kind: String,
        val weight: Int
    )

    private fun mergeConsecutiveGridTokens(tokens: List<ParsedGridToken>): List<MergedGridSlot> {
        if (tokens.isEmpty()) return emptyList()
        val sorted = tokens
            .distinctBy { "${it.dayOfWeek}|${it.startTime}|${it.endTime}|${it.kind}" }
            .sortedWith(
                compareBy<ParsedGridToken> { it.dayOfWeek }
                    .thenBy { parseMinutes(it.startTime) ?: Int.MAX_VALUE }
                    .thenBy { it.startTime }
            )
        val merged = mutableListOf<MergedGridSlot>()
        for (token in sorted) {
            val prev = merged.lastOrNull()
            val prevEndMin = prev?.let { parseMinutes(it.endTime) }
            val nextStartMin = parseMinutes(token.startTime)
            // Intervalos padrão UFOP entre tempos de aula: 0 min, 10 min ou 20 min. Almoço/tarde >= 40 min.
            if (prev != null &&
                prev.dayOfWeek == token.dayOfWeek &&
                prev.kind == token.kind &&
                prevEndMin != null &&
                nextStartMin != null &&
                nextStartMin >= prevEndMin &&
                (nextStartMin - prevEndMin) <= 20
            ) {
                merged[merged.lastIndex] = prev.copy(
                    endTime = token.endTime,
                    weight = prev.weight + 1
                )
            } else {
                merged.add(
                    MergedGridSlot(
                        dayOfWeek = token.dayOfWeek,
                        startTime = token.startTime,
                        endTime = token.endTime,
                        kind = token.kind,
                        weight = 1
                    )
                )
            }
        }
        return merged
    }

    private fun parseWeekdayHeaderWord(word: String): Int? = when (normalizeSearchText(word)) {
        "segunda", "segunda-feira", "seg" -> 1
        "terca", "terca-feira", "ter" -> 2
        "quarta", "quarta-feira", "qua" -> 3
        "quinta", "quinta-feira", "qui" -> 4
        "sexta", "sexta-feira", "sex" -> 5
        "sabado", "sab" -> 6
        else -> null
    }

    private val courseCodePrefixToDeptId = mapOf(
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

    private fun resolveDepartmentForCourseCode(code: String): DepartmentColorOption {
        val upper = code.trim().uppercase()
        val prefix = upper.take(3)
        val mapped = courseCodePrefixToDeptId[prefix]
            ?: "de${prefix.lowercase()}".takeIf { it in departmentById }
            ?: "decom"
        return resolveDepartmentOption(mapped)
    }

    private fun normalizeKnownDepartmentId(departmentId: String): String? {
        val raw = departmentId.trim().lowercase()
        if (raw.isEmpty()) return null
        if (raw in departmentById) return raw
        legacyDepartmentAliases[raw]?.let { return it }
        if (raw.contains("·")) {
            val sigla = raw.substringBefore("·").trim()
            if (sigla in departmentById) return sigla
            legacyDepartmentAliases[sigla]?.let { return it }
        }
        return null
    }

    private fun resolveDepartmentOption(departmentId: String): DepartmentColorOption {
        val resolvedId = normalizeKnownDepartmentId(departmentId) ?: "decom"
        return departmentById[resolvedId] ?: departmentById.getValue("decom")
    }

    private fun resolveCampusIdFromText(text: String): String? {
        val norm = normalizeSearchText(text)
        return when {
            norm.contains("ouro preto") || norm.contains("cruzeiro") -> "op_cruzeiro"
            norm.contains("mariana") || norm.contains("ichs") || norm.contains("icsa") -> "mar_ichs"
            norm.contains("monlevade") || norm.contains("icea") -> "jm_icea"
            else -> null
        }
    }

    private fun prettyBuildingName(raw: String): String {
        val trimmed = raw.trim()
        return when (normalizeSearchText(trimmed)) {
            "iceb", "icea", "ichs", "icsa", "emed", "efar", "edtm", "enut", "cedufop",
            "decom", "defis", "demat", "degeo", "deart", "depro", "cead" -> trimmed.uppercase()
            "escola de minas" -> "Escola de Minas"
            else -> toPortugueseTitleCase(trimmed)
        }
    }

    private fun buildCertificateShortLocation(prettyBuilding: String): String = when {
        prettyBuilding.equals("Escola de Minas", ignoreCase = true) -> "EM"
        prettyBuilding.startsWith("Bloco de Salas", ignoreCase = true) ||
            prettyBuilding.startsWith("Blocos de Salas", ignoreCase = true) -> "Bloco"
        prettyBuilding.length <= 6 -> prettyBuilding
        else -> prettyBuilding.split(" ").joinToString("") { it.take(1).uppercase() }.take(4).ifBlank { "UFOP" }
    }

    private fun buildRoomShortLocation(building: String, room: String): String {
        if (isEadIndicatorText(building) ||
            building.contains("Ensino a Distância", ignoreCase = true) ||
            building.contains("Ensino a Distancia", ignoreCase = true)
        ) {
            return "EAD"
        }
        val cleanB = building.trim()
        val shortB = when {
            cleanB.startsWith("Bloco de Salas", ignoreCase = true) ||
                cleanB.startsWith("Blocos de Salas", ignoreCase = true) ||
                cleanB.equals("Bloco", ignoreCase = true) -> "Bloco"
            else -> buildCertificateShortLocation(cleanB)
        }
        val cleanR = room.trim()
        return when {
            cleanR.isEmpty() -> shortB
            shortB.isEmpty() -> cleanR
            else -> "$shortB · $cleanR"
        }
    }

    private fun buildCleanCourseBuildingAndRoom(
        fallbackBuilding: String,
        courseSlots: List<SlotSeed>
    ): String {
        if (isEadIndicatorText(fallbackBuilding) ||
            fallbackBuilding.contains("Ensino a Distância", ignoreCase = true) ||
            fallbackBuilding.contains("Ensino a Distancia", ignoreCase = true)
        ) {
            return "Ensino a Distância (EAD)"
        }
        val nonGenericSlots = courseSlots.filter { slot ->
            val r = slot.room.trim()
            r.isNotEmpty() && r.lowercase() !in GENERIC_REPLACEABLE_ROOMS
        }
        if (nonGenericSlots.isNotEmpty()) {
            val byBuilding = nonGenericSlots.groupBy { slot ->
                slot.building.trim().ifBlank { fallbackBuilding.trim().ifBlank { "Campus UFOP" } }
            }
            return byBuilding.entries.joinToString(" · ") { (b, bSlots) ->
                val distinctRooms = bSlots.map { it.room.trim() }.filter { it.isNotEmpty() }.distinct()
                when {
                    distinctRooms.isEmpty() -> b
                    distinctRooms.size == 1 -> "$b · ${distinctRooms.first()}"
                    distinctRooms.all { it.startsWith("Sala ", ignoreCase = true) } -> {
                        val nums = distinctRooms.map { it.substringAfter(" ").trim() }
                        "$b · Salas ${nums.joinToString(" / ")}"
                    }
                    else -> "$b · ${distinctRooms.joinToString(" / ")}"
                }
            }
        }
        val slotBuilding = courseSlots.firstOrNull()?.building?.trim()?.takeIf { it.isNotEmpty() }
        val cleanFallback = fallbackBuilding.substringBefore("·").trim()
        return (slotBuilding ?: cleanFallback).ifBlank { "Campus UFOP" }
    }

    override fun applyRoomAllocationIndex(
        index: UfopRoomAllocationIndex,
        enrichExisting: Boolean
    ): Int = synchronized(lock) {
        roomAllocationIndex = index
        if (!enrichExisting) {
            return@synchronized 0
        }
        val updatedCount = enrichExistingRoomAllocationsLocked(index)
        if (updatedCount > 0) {
            persistStateLocked()
            _mutationVersion.value += 1L
        }
        updatedCount
    }

    private fun enrichExistingRoomAllocationsLocked(index: UfopRoomAllocationIndex): Int {
        var updatedCount = 0
        val touchedCourseIds = mutableSetOf<String>()

        for (i in slots.indices) {
            val slot = slots[i]
            val course = courses[slot.courseId] ?: continue
            val isEad = isEadIndicatorText(slot.building) ||
                slot.building.contains("Ensino a Distância", ignoreCase = true) ||
                slot.building.contains("Ensino a Distancia", ignoreCase = true) ||
                isEadIndicatorText(course.buildingAndRoom) ||
                course.buildingAndRoom.contains("Ensino a Distância", ignoreCase = true)
            if (isEad) {
                val rLower = slot.room.trim().lowercase()
                if (rLower in GENERIC_REPLACEABLE_ROOMS && (slot.room.isNotEmpty() || slot.shortLocation != "EAD")) {
                    slots[i] = slot.copy(room = "", shortLocation = "EAD")
                    updatedCount += 1
                    touchedCourseIds.add(course.courseId)
                }
                continue
            }

            val roomLower = slot.room.trim().lowercase()
            if (roomLower !in GENERIC_REPLACEABLE_ROOMS) {
                continue
            }

            val courseCode = resolveCourseCode(course)
            val notes = notesByCourseId[course.courseId] ?: course.initialNotes
            val turma = NOTES_TURMA_REGEX.find(notes)?.groupValues?.getOrNull(1)?.trim()
            val kindHint = when (roomLower) {
                "aula prática", "aula pratica" -> "P"
                "aula teórica", "aula teorica" -> "T"
                else -> null
            }

            val match = if (course.semester == index.semester) {
                index.lookup(
                    courseCode = courseCode,
                    turma = turma,
                    dayOfWeek = slot.dayOfWeek,
                    startTime = slot.startTime,
                    kind = kindHint
                )
            } else {
                null
            }

            if (match != null) {
                val newBuilding = match.building
                val newRoom = match.room
                val newShort = buildRoomShortLocation(newBuilding, newRoom)
                if (slot.building != newBuilding || slot.room != newRoom || slot.shortLocation != newShort) {
                    slots[i] = slot.copy(
                        building = newBuilding,
                        room = newRoom,
                        shortLocation = newShort
                    )
                    updatedCount += 1
                }
                touchedCourseIds.add(course.courseId)
            } else {
                val cleanBuilding = slot.building.substringBefore("·").trim().ifBlank {
                    course.buildingAndRoom.substringBefore("·").trim().ifBlank { "Campus UFOP" }
                }
                val prettyB = prettyBuildingName(cleanBuilding)
                val newShort = buildCertificateShortLocation(prettyB)
                val cleanedSlot = slot.copy(
                    building = prettyB,
                    room = "",
                    shortLocation = newShort
                )
                if (cleanedSlot != slot) {
                    slots[i] = cleanedSlot
                    updatedCount += 1
                }
                touchedCourseIds.add(course.courseId)
            }
        }

        for ((courseId, course) in courses.entries.toList()) {
            val hasGenericCourseSuffix = course.buildingAndRoom.contains("·") &&
                course.buildingAndRoom.substringAfter("·").trim().lowercase() in GENERIC_REPLACEABLE_ROOMS
            if (courseId !in touchedCourseIds && !hasGenericCourseSuffix) {
                continue
            }
            val courseSlots = slots.filter { it.courseId == courseId }
            val isEad = isEadIndicatorText(course.buildingAndRoom) ||
                course.buildingAndRoom.contains("Ensino a Distância", ignoreCase = true)
            if (isEad) {
                if (course.buildingAndRoom != "Ensino a Distância (EAD)" || course.shortLocation != "EAD") {
                    courses[courseId] = course.copy(
                        buildingAndRoom = "Ensino a Distância (EAD)",
                        shortLocation = "EAD"
                    )
                }
                continue
            }
            val baseBuilding = prettyBuildingName(
                course.buildingAndRoom.substringBefore("·").trim().ifBlank {
                    courseSlots.firstOrNull()?.building?.trim().orEmpty().ifBlank { "Campus UFOP" }
                }
            )
            val newBuildingAndRoom = buildCleanCourseBuildingAndRoom(baseBuilding, courseSlots)
            val newShortLocation = courseSlots.firstOrNull { it.room.isNotBlank() }?.shortLocation
                ?: buildCertificateShortLocation(baseBuilding)
            if (course.buildingAndRoom != newBuildingAndRoom || course.shortLocation != newShortLocation) {
                courses[courseId] = course.copy(
                    buildingAndRoom = newBuildingAndRoom,
                    shortLocation = newShortLocation
                )
            }
        }

        return updatedCount
    }

    private fun toPortugueseTitleCase(raw: String): String =
        normalizeUfopPortugueseText(raw)

    private fun parsePortugueseMonthNumber(monthStr: String): Int? =
        when (normalizeSearchText(monthStr).trimEnd('.')) {
            "janeiro", "jan" -> 1
            "fevereiro", "fev" -> 2
            "marco", "mar" -> 3
            "abril", "abr" -> 4
            "maio", "mai" -> 5
            "junho", "jun" -> 6
            "julho", "jul" -> 7
            "agosto", "ago" -> 8
            "setembro", "set" -> 9
            "outubro", "out" -> 10
            "novembro", "nov" -> 11
            "dezembro", "dez" -> 12
            else -> null
        }

    private fun parsePortugueseLongDate(dayStr: String, monthStr: String, yearStr: String): String? {
        val day = dayStr.toIntOrNull() ?: return null
        val year = yearStr.toIntOrNull() ?: return null
        val month = parsePortugueseMonthNumber(monthStr) ?: return null
        return try {
            LocalDate.of(year, month, day).toString()
        } catch (_: Throwable) {
            null
        }
    }

    // ── Sprint 7/9: Importador de Plano de Ensino (PDF/Texto) ────────────────────────────────────

    private data class ParsedLineDate(
        val isoDate: String,
        val remainingTitle: String,
        val isBareDate: Boolean,
        val extractedTime: String?
    )

    private val syllabusEvalRegex = Regex(
        """\b(?:prova|provas|p[1-9]|exame|exames|teste|testes|quiz|quizzes|tp\d*|trabalho|trabalhos|lista|listas|projeto|projetos|relatorio|relatorios|seminario|seminarios|avaliacao|avaliacoes|substitutiva|entrega|entregas|apresentacao)\b""",
        RegexOption.IGNORE_CASE
    )

    private val syllabusHardNoiseRegex = Regex(
        """\b(?:bibliografia|referencias?|ementa|objetivos?|metodologia|criterios?|media\s+final|media\s+semestral|nota\s+minima|calculo\s+da\s+(?:media|nota)|frequencia|reprovad[oa]|reprovacao|aprovad[oa]|aprovacao|atestado|revisao|segunda\s+chamada|2[aª]?\s+chamada|duvidas?|plantao|atendimento\s+ao\s+aluno|vista\s+de\s+prova|devolucao\s+de\s+prova|divulgacao\s+de\s+notas|lancamento\s+de\s+notas|feriado|recesso|nao\s+havera\s+aula|sem\s+aula|suspensao|resolucao|cepe|prograd|regimento|plagio|plano\s+de\s+ensino|apresentacao\s+d[eao]s?\s+(?:disciplina|curso|ementa|programa|professor|professora|docente|turma|alunos|estudantes|calendario|cronograma)|sistema\s+de\s+avaliacao|forma\s+de\s+avaliacao|metodo\s+de\s+avaliacao|instrumentos?\s+de\s+avaliacao|distribuicao\s+de\s+pontos)\b""",
        RegexOption.IGNORE_CASE
    )

    private val syllabusLeadingObsNoiseRegex = Regex(
        """^\s*(?:observac(?:ao|oes)|obs\.?|atencao|importante|nota)\s*[:\-]""",
        RegexOption.IGNORE_CASE
    )

    private val syllabusProseClauseRegex = Regex(
        """\b(?:sera\s+calculad|sera\s+atribuid|sera\s+considerad|sera\s+realizad[oa]|serao\s+realizad[oa]s|devera\s+|deverao\s+|podera\s+|tera\s+direito|caso\s+o\s+aluno|em\s+caso\s+de|no\s+caso\s+de|o\s+aluno\s+que|o\s+estudante\s+que|os\s+alunos\s+que|nao\s+sera|mediante\s+|conforme\s+[oa]s?\b|nota\s+zero|totalizando\s+|composta\s+por|equivalente\s+a\s+nota|salvo\s+justificativa|peso\s+na\s+media)\b""",
        RegexOption.IGNORE_CASE
    )

    private val syllabusProseFragmentRegex = Regex(
        """\b(?:a\s+entrega|as\s+entregas|pelos\s+alunos|pelas\s+alunas|os\s+alunos|as\s+alunas|o\s+aluno|o\s+estudante|os\s+estudantes|devera\s+ser|deverao\s+ser|sera\s+realizad[oa]|serao\s+realizad[oa]s|no\s+caso\s+de|em\s+caso\s+de|conforme\s+[oa]s?)\b""",
        RegexOption.IGNORE_CASE
    )

    private val syllabusCoreEvalKeywordRegex = Regex(
        """\b(?:prova|provas|p[1-9]|exame|exames|teste|testes|quiz|quizzes|tp\d*|trabalho|trabalhos|lista|listas|projeto|projetos|relatorio|relatorios|seminario|seminarios|avaliacao|avaliacoes|substitutiva|reposicao|recuperacao|apresentacao)\b""",
        RegexOption.IGNORE_CASE
    )

    private val syllabusDanglingTailWords = setOf(
        "pelos", "pelas", "pelo", "pela",
        "das", "dos", "com", "para", "por",
        "de", "da", "do", "em", "na", "no", "nas", "nos",
        "a", "o", "as", "os", "ao", "aos",
        "que", "se", "ou", "e", "ate", "sobre", "entre", "sem", "sob"
    )

    private val syllabusStructuredEvalAnchorRegex = Regex(
        """^\s*(?:(?:aula|semana|encontro|unidade|modulo|cap\.?)\s*\d+[a-zº°ª]?\s*[-–—:|.]*\s*)?(?:p[1-9]\b|prova\b|exame\b|tp\d*\b|trabalho\b|seminario\b|lista\b|relatorio\b|projeto\b|teste\b|quiz\b|(?:1|2|3|4|5)[aªºo]?\s+(?:prova|avaliacao|trabalho|lista|relatorio|seminario|teste)\b|(?:primeira|segunda|terceira|quarta|quinta)\s+(?:prova|avaliacao|trabalho|lista|relatorio|seminario)\b|avaliacao\s*(?:\d|i{1,3}|iv|final|substitutiva)\b|entrega\s+d[eao]s?\s+(?:trabalho|tp|lista|relatorio|projeto)\b|apresentacao\s+d[eao]s?\s+(?:seminario|trabalho|projeto)\b)""",
        RegexOption.IGNORE_CASE
    )

    private val calendarKeepKeywordsRegex = Regex(
        """feriado|recesso|facultativo|ponto\s+facultativo|matricula|trancamento|exame|especial|especiais|inicio|termino|encerramento|prazo|ajuste|inscricao|colacao|semana|letivo|letiva|aula|aulas|suspensao|calendario|ferias|requerimento|solicitacao|divulgacao|sabado\s+letivo|natal|finados|tiradentes|carnaval|consciencia|republica|independencia|padroeira|pascoa|corpus""",
        RegexOption.IGNORE_CASE
    )

    private val calendarBoilerplateRegex = Regex(
        """^\s*(?:resolucao|portaria|edital|pagina|pag\.|cep\b|cnpj\b|documento\s+assinado|codigo\s+verificador|publicado\s+em|aprovado\s+em|universidade\s+federal|pro-?reitoria|conselho)""",
        RegexOption.IGNORE_CASE
    )

    override fun importCourseSyllabusPdf(courseId: String, pdfBytes: ByteArray): Int {
        require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
        return SecurePdfProcessor.withZeroedPdfBytes(pdfBytes) { safeBytes ->
            val rawText = extractPdfTextWithPdfBox(safeBytes)
            importCourseSyllabusText(courseId, rawText)
        }
    }

    override fun importCourseSyllabusText(courseId: String, rawText: String): Int {
        val trimmed = rawText.trim()
        require(trimmed.isNotEmpty()) { "Texto do plano de ensino vazio." }

        return synchronized(lock) {
            val course = courses[courseId]
                ?: throw IllegalArgumentException("Disciplina não encontrada: $courseId")
            val (importedCount, _) = importSyllabusAssessmentsLocked(course, trimmed)
            if (importedCount > 0) {
                persistStateLocked()
                _mutationVersion.value += 1L
            }
            importedCount
        }
    }

    override fun importCourseSyllabusDetailedPdf(
        courseId: String,
        pdfBytes: ByteArray
    ): SyllabusImportReport {
        require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
        return SecurePdfProcessor.withZeroedPdfBytes(pdfBytes) { safeBytes ->
            val rawText = extractPdfTextWithPdfBox(safeBytes)
            importCourseSyllabusDetailedText(courseId, rawText)
        }
    }

    override fun importCourseSyllabusDetailedText(
        courseId: String,
        rawText: String
    ): SyllabusImportReport {
        val trimmed = rawText.trim()
        require(trimmed.isNotEmpty()) { "Texto do plano de ensino vazio." }

        return synchronized(lock) {
            val initialCourse = courses[courseId]
                ?: throw IllegalArgumentException("Disciplina não encontrada: $courseId")

            val matchResult = matchSyllabusToCourse(initialCourse, trimmed)
            if (!matchResult.matched) {
                val detectedLabel = listOfNotNull(
                    matchResult.detectedCode,
                    matchResult.detectedTitle
                ).joinToString(" · ").ifBlank { "outra disciplina" }
                return@synchronized SyllabusImportReport(
                    importedCount = 0,
                    matchedCourse = false,
                    matchedCourseCode = matchResult.detectedCode,
                    matchedCourseTitle = matchResult.detectedTitle,
                    extractedProfessor = "",
                    hasGradeDistribution = false,
                    hasAssessmentDates = false,
                    warningMessage = null,
                    errorMessage = "Este PDF parece ser de outra disciplina ($detectedLabel) e não de ${initialCourse.name}. Nenhuma alteração foi feita."
                )
            }

            val extractedProfessor = extractProfessorFromSyllabus(trimmed)
            var currentCourse = initialCourse
            var professorUpdated = false
            if (extractedProfessor.isNotBlank() && extractedProfessor != initialCourse.professor) {
                currentCourse = initialCourse.copy(professor = extractedProfessor)
                courses[courseId] = currentCourse
                professorUpdated = true
            }

            val hasGradeDistribution = detectGradeDistributionInSyllabus(trimmed)
            val (importedCount, hasDatedAssessmentTokens) = importSyllabusAssessmentsLocked(currentCourse, trimmed)
            val existingCountAfter = assessmentsByCourseId[courseId]?.size ?: 0
            val hasAssessmentDates = importedCount > 0 || existingCountAfter > 0 || hasDatedAssessmentTokens

            val warningMessage = when {
                !hasGradeDistribution && !hasAssessmentDates ->
                    "Aviso: apenas a disciplina e o professor foram identificados neste plano (sem divisão de notas e sem datas de avaliações no semestre). Você precisará inserir as datas manualmente mais tarde."
                !hasGradeDistribution ->
                    "Aviso: foram encontradas datas, mas o plano não detalha a divisão de notas do semestre. Confira os pesos/datas manualmente mais tarde."
                !hasAssessmentDates ->
                    "Aviso: apenas a identificação da disciplina/professor (e critérios) foi achada — nenhuma data específica de avaliação constava no plano. Adicione as datas manualmente mais tarde."
                else -> null
            }

            if (professorUpdated || importedCount > 0) {
                persistStateLocked()
                _mutationVersion.value += 1L
            }

            SyllabusImportReport(
                importedCount = importedCount,
                matchedCourse = true,
                matchedCourseCode = matchResult.detectedCode ?: extractRealUfopCourseCode(currentCourse.code),
                matchedCourseTitle = matchResult.detectedTitle ?: currentCourse.name,
                extractedProfessor = extractedProfessor,
                hasGradeDistribution = hasGradeDistribution,
                hasAssessmentDates = hasAssessmentDates,
                warningMessage = warningMessage,
                errorMessage = null
            )
        }
    }

    private data class SyllabusCourseMatchResult(
        val matched: Boolean,
        val detectedCode: String?,
        val detectedTitle: String?
    )

    private fun extractRealUfopCourseCode(raw: String): String? {
        val clean = raw.trim().uppercase()
        if (!clean.matches(Regex("""^[A-Z]{2,4}\d{3,4}[A-Z]?$"""))) return null
        val prefix = clean.takeWhile { it.isLetter() }
        return if (prefix in NON_COURSE_CODE_PREFIXES) null else clean
    }

    private fun extractCourseLevelNumeral(raw: String): Int? {
        val cleaned = raw
            .replace(Regex("""^\s*[A-Z]{2,4}\d{3,4}[A-Z]?\s*[-–—:]?\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
        val norm = normalizeSearchText(cleaned)
        val match = Regex("""\b(iv|iii|ii|i|v|1|2|3|4|5)\s*$""").find(norm) ?: return null
        return when (match.groupValues[1]) {
            "i", "1" -> 1
            "ii", "2" -> 2
            "iii", "3" -> 3
            "iv", "4" -> 4
            "v", "5" -> 5
            else -> null
        }
    }

    private fun matchSyllabusToCourse(course: CourseSeed, rawText: String): SyllabusCourseMatchResult {
        val lines = rawText.lines()
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotEmpty() }
        val headerLines = lines.take(40)
        val nonPrereqLines = lines.filterNot { SYLLABUS_PREREQ_LINE_REGEX.containsMatchIn(normalizeSearchText(it)) }
        val nonPrereqHeaderLines = headerLines.filterNot { SYLLABUS_PREREQ_LINE_REGEX.containsMatchIn(normalizeSearchText(it)) }

        val notes = notesByCourseId[course.courseId] ?: course.initialNotes
        val targetRealCode = extractRealUfopCourseCode(course.code)
            ?: Regex("""C[oó]digo:\s*([A-Z]{2,4}\d{3,4}[A-Z]?)""", RegexOption.IGNORE_CASE)
                .find(notes)?.groupValues?.getOrNull(1)?.let { extractRealUfopCourseCode(it) }
            ?: UFOP_COURSE_CODE_PATTERN.find(course.name)?.groupValues?.getOrNull(1)?.let { extractRealUfopCourseCode(it) }
            ?: Regex("""^([a-z]{2,4}\d{3,4}[a-z]?)(?:_|$)""", RegexOption.IGNORE_CASE)
                .find(course.courseId.trim())?.groupValues?.getOrNull(1)?.let { extractRealUfopCourseCode(it) }

        fun findCodesIn(sourceLines: List<String>): List<String> = sourceLines
            .flatMap { line -> UFOP_COURSE_CODE_PATTERN.findAll(line).mapNotNull { extractRealUfopCourseCode(it.groupValues[1]) } }
            .distinct()

        val headerCodes = findCodesIn(nonPrereqHeaderLines)
        val bodyCodes = if (headerCodes.isNotEmpty()) headerCodes else findCodesIn(nonPrereqLines)

        val targetCodeMatched = targetRealCode != null && (
            headerCodes.any { it.equals(targetRealCode, ignoreCase = true) } ||
                bodyCodes.any { it.equals(targetRealCode, ignoreCase = true) }
            )

        var detectedHeaderCode: String? = headerCodes.firstOrNull() ?: bodyCodes.firstOrNull()
        var detectedHeaderTitle: String? = null

        val explicitLabelRegex = Regex(
            """^(?:disciplina|componente\s+curricular|mat[eé]ria|unidade\s+curricular|plano\s+de\s+ensino(?:\s+d[aeo]\s+disciplina)?)\s*[:\-–]\s*(.*)$""",
            RegexOption.IGNORE_CASE
        )
        val codeTitleLineRegex = Regex(
            """^([A-Z]{2,4}\d{3,4}[A-Z]?)\s*[-–—:]\s*(.+)$""",
            RegexOption.IGNORE_CASE
        )

        for (i in nonPrereqHeaderLines.indices) {
            val line = nonPrereqHeaderLines[i]
            val explicitMatch = explicitLabelRegex.find(line)
            if (explicitMatch != null) {
                var candidate = explicitMatch.groupValues[1].trim()
                if (candidate.isEmpty() && i + 1 < nonPrereqHeaderLines.size) {
                    val next = nonPrereqHeaderLines[i + 1]
                    if (!next.contains(":")) candidate = next
                }
                if (candidate.isNotEmpty()) {
                    val codeInCandidate = UFOP_COURSE_CODE_PATTERN.find(candidate)?.groupValues?.getOrNull(1)
                        ?.let { extractRealUfopCourseCode(it) }
                    if (codeInCandidate != null) detectedHeaderCode = codeInCandidate
                    val stripped = candidate
                        .replace(Regex("""\b[A-Z]{2,4}\d{3,4}[A-Z]?\b\s*[-–—:]?\s*""", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("""\s+(?:turma|carga\s+hor[áa]ria|semestre|per[íi]odo|ch\b)\b.*$""", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("""\s*\(\s*\d+\s*h\s*\)\s*$""", RegexOption.IGNORE_CASE), "")
                        .trim(' ', '-', '–', '—', ':', '|', ';')
                    if (stripped.length >= 3) {
                        detectedHeaderTitle = normalizeUfopPortugueseText(stripped)
                        break
                    }
                }
            }
            val codeTitleMatch = codeTitleLineRegex.find(line)
            if (codeTitleMatch != null) {
                val codeInLine = extractRealUfopCourseCode(codeTitleMatch.groupValues[1])
                if (codeInLine != null) {
                    detectedHeaderCode = codeInLine
                    val stripped = codeTitleMatch.groupValues[2]
                        .replace(Regex("""\s+(?:turma|carga\s+hor[áa]ria|semestre|per[íi]odo|ch\b)\b.*$""", RegexOption.IGNORE_CASE), "")
                        .trim(' ', '-', '–', '—', ':', '|', ';')
                    if (stripped.length >= 3) {
                        detectedHeaderTitle = normalizeUfopPortugueseText(stripped)
                        break
                    }
                }
            }
        }

        val detectedOtherCode = (headerCodes + bodyCodes).firstOrNull { code ->
            targetRealCode == null || !code.equals(targetRealCode, ignoreCase = true)
        }

        val strippedCourseName = course.name
            .replace(Regex("""^\s*[A-Z]{2,4}\d{3,4}[A-Z]?\s*[-–—:]?\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
        val nameStopwords = setOf(
            "das", "dos", "com", "para", "por", "que", "uma", "uns", "umas",
            "nos", "nas", "aos", "sobre", "entre", "sem", "sob"
        )
        val allNameTokens = normalizeSearchText(strippedCourseName)
            .split(Regex("""[^a-z0-9]+"""))
            .filter { it.isNotBlank() }
        val significantTokens = allNameTokens
            .filter { it.length >= 3 && it !in nameStopwords }
            .distinct()
            .ifEmpty { allNameTokens.distinct() }

        val searchableNorm = normalizeSearchText(nonPrereqLines.joinToString(" "))
        val matchedTokensCount = significantTokens.count { token ->
            Regex("""\b${Regex.escape(token)}\b""").containsMatchIn(searchableNorm)
        }
        val hasNameMatch = significantTokens.isNotEmpty() &&
            (matchedTokensCount * 2 >= significantTokens.size)

        val targetLevel = extractCourseLevelNumeral(course.name)
        val headerLevel = detectedHeaderTitle?.let { extractCourseLevelNumeral(it) }
        val hasLevelConflict = !targetCodeMatched &&
            targetLevel != null &&
            headerLevel != null &&
            targetLevel != headerLevel

        val declaredOtherCodeMismatch = targetRealCode != null && !targetCodeMatched && detectedOtherCode != null
        val neitherCodeNorNameMatched = !targetCodeMatched && !hasNameMatch

        val matched = !(declaredOtherCodeMismatch || neitherCodeNorNameMatched || hasLevelConflict)
        return SyllabusCourseMatchResult(
            matched = matched,
            detectedCode = if (matched) (targetRealCode ?: detectedHeaderCode) else (detectedOtherCode ?: detectedHeaderCode),
            detectedTitle = detectedHeaderTitle
        )
    }

    private fun extractProfessorFromSyllabus(rawText: String): String {
        val lines = rawText.lines()
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotEmpty() }
        val profLabelRegex = Regex(
            """^(?:professor(?:\(a\)|a)?(?:\s+(?:respons[áa]vel|ministrante))?|docente(?:\(s\)|(?:\s+(?:respons[áa]vel|ministrante)))?|ministrante|respons[áa]vel(?:\s+pela\s+disciplina)?)\s*[:\-–]\s*(.*)$""",
            RegexOption.IGNORE_CASE
        )
        val nextLineLabelGuard = Regex(
            """^(?:disciplina|c[óo]digo|turma|semestre|per[íi]odo|carga|ementa|departamento|hor[áa]rio|campus|objetivos?)\b""",
            RegexOption.IGNORE_CASE
        )
        val noiseProfValues = setOf(
            "a definir", "nao informado", "sem professor", "pendente", "n/a", "ufop",
            "departamento", "colegiado", "coordenacao", "assinatura", "chefia"
        )

        val scanLimit = minOf(lines.size, 60)
        for (i in 0 until scanLimit) {
            val match = profLabelRegex.find(lines[i]) ?: continue
            var candidate = match.groupValues[1].trim()
            if (candidate.isEmpty() && i + 1 < lines.size) {
                val next = lines[i + 1]
                if (!next.contains(":") && !nextLineLabelGuard.containsMatchIn(normalizeSearchText(next))) {
                    candidate = next
                }
            }
            candidate = candidate
                .replace(Regex("""\S+@\S+"""), " ")
                .replace(Regex("""\s+(?:e-?mail|siape|carga\s+hor[áa]ria|turma|semestre|per[íi]odo|hor[áa]rio|departamento|depto\.?|campus|sala)\b.*$""", RegexOption.IGNORE_CASE), "")
                .substringBefore("|")
                .substringBefore(";")
                .replace(Regex("""\([^)]*(?:@|depto|ufop|siape)[^)]*\)""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+"""), " ")
                .trim(' ', ':', '-', '–', '—', '|', ';', ',', '_')

            val normCandidate = normalizeSearchText(candidate)
            val letterCount = candidate.count { it.isLetter() }
            if (candidate.length in 3..80 &&
                letterCount >= 3 &&
                normCandidate !in noiseProfValues &&
                !normCandidate.startsWith("a definir") &&
                !normCandidate.startsWith("nao informado") &&
                !normCandidate.contains("assinatura")
            ) {
                return normalizeUfopPortugueseText(candidate)
            }
        }
        return ""
    }

    private fun detectGradeDistributionInSyllabus(rawText: String): Boolean {
        val norm = normalizeSearchText(rawText)
        if (Regex("""\b(?:distribuicao\s+d[eao]s?\s+(?:pontos|notas)|composicao\s+d[aeo]s?\s+(?:nota|notas|media)|divisao\s+d[eao]s?\s+(?:pontos|notas))\b""").containsMatchIn(norm)) {
            return true
        }
        if (Regex("""\b(?:peso\s*[:=]?\s*\d+(?:[.,]\d+)?|valendo\s+\d+(?:[.,]\d+)?|valor\s*[:=]?\s*\d+(?:[.,]\d+)?)\b""").containsMatchIn(norm)) {
            return true
        }
        if (Regex("""\b(?:p[1-9]|n[1-9]|t[1-9]|ap[1-9])\s*(?:\+|\*|=)\s*(?:p[1-9]|n[1-9]|t[1-9]|\d)|\bm[fs]?\s*=\s*""").containsMatchIn(norm)) {
            return true
        }
        // Pontos / % evitando falso positivo de frequência mínima (ex.: "frequencia minima de 75%")
        val scoreTokenRegex = Regex("""\b\d+(?:[.,]\d+)?\s*(?:pts?\.?|pontos?|%)\b""")
        for (m in scoreTokenRegex.findAll(norm)) {
            val ctxStart = (m.range.first - 36).coerceAtLeast(0)
            val ctxEnd = (m.range.last + 28).coerceAtMost(norm.length)
            val ctx = norm.substring(ctxStart, ctxEnd)
            val isAttendanceContext = ctx.contains("frequencia") ||
                ctx.contains("presenca") ||
                ctx.contains("faltas") ||
                ctx.contains("assiduidade")
            if (!isAttendanceContext) {
                return true
            }
        }
        if (Regex("""\b(?:criterios?\s+de\s+avaliacao|sistema\s+de\s+avaliacao|calculo\s+da\s+(?:media|nota)|media\s+(?:semestral|final))\b""").containsMatchIn(norm)) {
            val hasBreakdownDetails = Regex("""\b(?:p[1-9]|prova|trabalho|seminario|relatorio|lista|peso|pontos|valendo|igual\s+valor|media)\b""").containsMatchIn(norm) &&
                !Regex("""\b(?:criterios?\s+de\s+avaliacao|sistema\s+de\s+avaliacao)\s*[:\-]?\s*a\s+definir\b""").containsMatchIn(norm)
            if (hasBreakdownDetails) return true
        }
        return false
    }

    private fun importSyllabusAssessmentsLocked(
        course: CourseSeed,
        trimmedText: String
    ): Pair<Int, Boolean> {
        val courseId = course.courseId
        val defaultYear = resolveSemesterDefaultYear(course.semester)

        val segments = trimmedText.lines()
            .flatMap { rawLine -> splitSyllabusLineIntoSegments(rawLine, defaultYear) }
            .filter { it.isNotEmpty() }

        val existingList = assessmentsByCourseId.getOrPut(courseId) { mutableListOf() }
        val seenKeys = existingList.map { it.dueDate to normalizeDedupKey(it.title) }.toMutableSet()
        var importedCount = 0
        var hasDatedAssessmentTokens = false
        val consumedIndices = mutableSetOf<Int>()

        fun tryAddAssessment(isoDate: String, rawTitle: String, keywordSource: String, dueTime: String?) {
            val normKeySource = normalizeSearchText(keywordSource)
            if (!syllabusEvalRegex.containsMatchIn(normKeySource)) return
            if (isSyllabusNoiseOrProse(keywordSource, rawTitle)) return

            val (formattedTitle, kind) = formatSyllabusAssessmentTitleAndKind(
                rawTitle = rawTitle,
                keywordSource = keywordSource,
                course = course
            ) ?: return

            if (formattedTitle.isBlank() || formattedTitle.length > 64) return
            hasDatedAssessmentTokens = true
            val dedupKey = isoDate to normalizeDedupKey(formattedTitle)
            if (!seenKeys.add(dedupKey)) return

            val (normalizedIso, dayNum, monthShort) = parseDueDateParts(isoDate)
            generatedIdCounter += 1L
            existingList.add(
                AssessmentItem(
                    id = "assess_${courseId}_$generatedIdCounter",
                    courseId = courseId,
                    dayNumber = dayNum,
                    monthShort = monthShort,
                    title = formattedTitle,
                    kind = kind,
                    dueTime = dueTime ?: "18:00",
                    dueDate = normalizedIso,
                    completed = false
                )
            )
            importedCount += 1
        }

        for (i in segments.indices) {
            if (i in consumedIndices) continue
            val line = segments[i]
            val parsed = extractDateFromSegment(line, defaultYear, fallbackMonth = null)

            if (parsed != null && !parsed.isBareDate) {
                tryAddAssessment(parsed.isoDate, parsed.remainingTitle, line, parsed.extractedTime)
                consumedIndices.add(i)
            } else if (parsed != null && parsed.isBareDate) {
                val nextLine = segments.getOrNull(i + 1)
                val prevLine = segments.getOrNull(i - 1)
                when {
                    nextLine != null && (i + 1) !in consumedIndices &&
                        extractDateFromSegment(nextLine, defaultYear, null) == null &&
                        syllabusEvalRegex.containsMatchIn(normalizeSearchText(nextLine)) -> {
                        tryAddAssessment(parsed.isoDate, cleanExtractedTitle(nextLine), nextLine, parsed.extractedTime)
                        consumedIndices.add(i)
                        consumedIndices.add(i + 1)
                    }
                    prevLine != null && (i - 1) !in consumedIndices &&
                        extractDateFromSegment(prevLine, defaultYear, null) == null &&
                        syllabusEvalRegex.containsMatchIn(normalizeSearchText(prevLine)) -> {
                        tryAddAssessment(parsed.isoDate, cleanExtractedTitle(prevLine), prevLine, parsed.extractedTime)
                        consumedIndices.add(i)
                        consumedIndices.add(i - 1)
                    }
                }
            } else if (parsed == null && syllabusEvalRegex.containsMatchIn(normalizeSearchText(line))) {
                val nextLine = segments.getOrNull(i + 1)
                val nextParsed = nextLine?.let { extractDateFromSegment(it, defaultYear, null) }
                if (nextParsed != null && nextParsed.isBareDate && (i + 1) !in consumedIndices) {
                    tryAddAssessment(nextParsed.isoDate, cleanExtractedTitle(line), line, nextParsed.extractedTime)
                    consumedIndices.add(i)
                    consumedIndices.add(i + 1)
                }
            }
        }

        return importedCount to hasDatedAssessmentTokens
    }

    private fun buildCourseShortTag(course: CourseSeed): String {
        val strippedCode = course.name
            .replace(Regex("""^\s*[A-Z]{2,4}\d{3,4}[A-Z]?\s*[-–—:]?\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
        val norm = normalizeSearchText(strippedCode)

        fun extractTrailingRoman(): String? {
            val rawNum = Regex("""\b(i{1,3}|iv|v|1|2|3|4)\b""", RegexOption.IGNORE_CASE)
                .findAll(norm)
                .lastOrNull()
                ?.value
                ?.uppercase()
                ?: return null
            return when (rawNum) {
                "1" -> "I"
                "2" -> "II"
                "3" -> "III"
                "4" -> "IV"
                else -> rawNum
            }
        }

        when {
            norm.contains("equacoes diferenciais ordinarias") ||
                norm.contains("equacao diferencial ordinaria") ||
                norm == "edo" -> return "EDO"
            norm.contains("equacoes diferenciais parciais") || norm == "edp" -> return "EDP"
            norm.contains("geometria analitica e algebra linear") || norm == "gaal" -> return "GAAL"
            norm.contains("calculo diferencial e integral") -> {
                val roman = extractTrailingRoman()
                return if (!roman.isNullOrBlank()) "CÁLCULO $roman" else "CÁLCULO"
            }
            norm.contains("estruturas de dados") || norm.contains("estrutura de dados") -> {
                val roman = extractTrailingRoman()
                return if (!roman.isNullOrBlank()) "ED $roman" else "ED"
            }
            norm.contains("banco de dados") -> {
                val roman = extractTrailingRoman()
                return if (!roman.isNullOrBlank()) "BD $roman" else "BD"
            }
            norm.contains("sistemas operacionais") -> return "SO"
            norm.contains("inteligencia artificial") -> return "IA"
            norm.contains("programacao orientada a objetos") -> return "POO"
        }

        val trimmedPrefix = strippedCode
            .replace(
                Regex(
                    """^(?:introdu[çc][aã]o\s+(?:[àa]s?|[àa]os?|de|da|do)\s+|fundamentos\s+(?:de|da|do|das|dos)\s+|an[áa]lise\s+(?:de|da|do|das|dos)\s+|princ[íi]pios\s+(?:de|da|do|das|dos)\s+|no[çc][õo]es\s+(?:de|da|do|das|dos)\s+|elementos\s+(?:de|da|do|das|dos)\s+)""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { strippedCode }

        val upper = trimmedPrefix.uppercase(PT_BR_LOCALE)
        if (upper.length <= 24) return upper

        val compact = upper.split(Regex("""\s+""")).filter { it.isNotBlank() }.take(3).joinToString(" ")
        if (compact.length <= 24) return compact
        return resolveCourseCode(course).uppercase(PT_BR_LOCALE).ifBlank { compact.take(24).trimEnd() }
    }

    private fun findValidDateRangesInSegment(segment: String, defaultYear: Int): List<IntRange> {
        val matches = mutableListOf<IntRange>()

        val longDateRegex = Regex(
            """\b(\d{1,2})(?:\s*(?:a|e|at[eé]|-|–)\s*\d{1,2})?\s+de\s+([A-Za-zÀ-ú]{3,12})\.?(?:\s+de\s+(\d{2,4}))?\b""",
            RegexOption.IGNORE_CASE
        )
        longDateRegex.findAll(segment).forEach { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@forEach
            val month = parsePortugueseMonthNumber(m.groupValues[2]) ?: return@forEach
            val year = normalizeYear(m.groupValues[3].takeIf { it.isNotBlank() }, defaultYear)
            if (runCatching { LocalDate.of(year, month, day) }.isSuccess) {
                matches.add(m.range)
            }
        }

        val numericDateRegex = Regex("""(?<!\d)(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?(?!\d)""")
        val scoreSuffixRegex = Regex("""^\s*(?:pts?|pontos?|%)\b""", RegexOption.IGNORE_CASE)
        val scorePrefixRegex = Regex("""(?:nota|valor|peso|escore|pontua[çc][aã]o)\s*[:=]?\s*$""", RegexOption.IGNORE_CASE)
        val rangeTailRegex = Regex("""^\s*(?:a|at[eé]|-|–|—)\s*\d{1,2}/\d{1,2}(?:/\d{2,4})?\b""", RegexOption.IGNORE_CASE)

        var skipUntil = -1
        numericDateRegex.findAll(segment).forEach { m ->
            if (m.range.first < skipUntil) return@forEach
            val before = segment.substring(0, m.range.first)
            val after = segment.substring(m.range.last + 1)
            if (scorePrefixRegex.containsMatchIn(before) || scoreSuffixRegex.containsMatchIn(after)) {
                return@forEach
            }
            val day = m.groupValues[1].toIntOrNull() ?: return@forEach
            val month = m.groupValues[2].toIntOrNull() ?: return@forEach
            val year = normalizeYear(m.groupValues[3].takeIf { it.isNotBlank() }, defaultYear)
            if (runCatching { LocalDate.of(year, month, day) }.isFailure) return@forEach
            val rangeTail = rangeTailRegex.find(after)
            val endIdx = if (rangeTail != null) {
                m.range.last + rangeTail.value.length
            } else {
                m.range.last
            }
            skipUntil = endIdx + 1
            matches.add(m.range.first..endIdx)
        }

        val isoRegex = Regex("""\b(20\d{2})-(\d{2})-(\d{2})\b""")
        isoRegex.findAll(segment).forEach { m ->
            if (parseIsoDateOrNull(m.value) != null && matches.none { it.first <= m.range.last && m.range.first <= it.last }) {
                matches.add(m.range)
            }
        }

        return matches.sortedBy { it.first }
    }

    private fun splitSyllabusLineIntoSegments(rawLine: String, defaultYear: Int): List<String> {
        val cleaned = rawLine.trim().replace(Regex("""\s+"""), " ")
        if (cleaned.isEmpty()) return emptyList()

        val initialDateCount = findValidDateRangesInSegment(cleaned, defaultYear).size
        val normalizedPipes = if (cleaned.contains("|") && initialDateCount <= 1) {
            cleaned.replace("|", " - ")
        } else {
            cleaned
        }

        val primarySplitRegex = if (initialDateCount >= 2) {
            Regex("""[;•|]""")
        } else {
            Regex("""[;•]""")
        }

        val primaryPieces = primarySplitRegex.split(normalizedPipes)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val output = mutableListOf<String>()
        for (piece in primaryPieces) {
            output.addAll(splitMultiDateSegment(piece, defaultYear))
        }
        return output
    }

    private fun splitMultiDateSegment(piece: String, defaultYear: Int): List<String> {
        val dateRanges = findValidDateRangesInSegment(piece, defaultYear)
        if (dateRanges.size <= 1) return listOf(piece)

        // 1. Tenta dividir em separadores explícitos (`,`, `·`, ` e `) apenas quando ambos os lados contêm datas.
        val sepSplit = splitAtDateSeparators(piece, defaultYear)
        if (sepSplit.size > 1 && sepSplit.all { findValidDateRangesInSegment(it, defaultYear).size <= 1 }) {
            return sepSplit
        }

        // 2. Fallback ancorado em datas (Label-Data vs Data-Label)
        val leadingText = piece.substring(0, dateRanges.first().first)
            .replace(
                Regex(
                    """^\s*(?:avalia[çc][õo]es(?:\s+(?:pr[áa]ticas?|te[óo]ricas?|previstas?|parciais?|da\s+disciplina))?|provas(?:\s+previstas)?|datas(?:\s+d[eao]s?\s+(?:provas?|avalia[çc][õo]es))?|cronograma(?:\s+de\s+avalia[çc][õo]es)?|calend[áa]rio(?:\s+de\s+(?:provas?|avalia[çc][õo]es))?)\s*[:\-]\s*""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()
        val isLabelFirst = leadingText.isNotEmpty() && syllabusEvalRegex.containsMatchIn(normalizeSearchText(leadingText))

        val slices = mutableListOf<String>()
        if (isLabelFirst) {
            var cursor = 0
            for (idx in dateRanges.indices) {
                val range = dateRanges[idx]
                var end = range.last + 1
                // Absorve parêntese de fechamento ou pontuação imediata logo após a data, ex: "P1 (18/09)" ou "P1 18/09 (30 pts)"
                val after = piece.substring(end)
                val closingParen = Regex("""^\s*\)""").find(after)
                if (closingParen != null) {
                    end += closingParen.value.length
                } else {
                    val scoreParen = Regex("""^\s*\([^)]*(?:pts?|pontos?|peso|valor|%)[^)]*\)""", RegexOption.IGNORE_CASE).find(after)
                    if (scoreParen != null && (idx + 1 >= dateRanges.size || end + scoreParen.value.length <= dateRanges[idx + 1].first)) {
                        end += scoreParen.value.length
                    }
                }
                val slice = piece.substring(cursor, end).trim(' ', ',', ';', '-', '–', '—', '·', '|')
                if (slice.isNotEmpty()) slices.add(slice)
                cursor = end
            }
        } else {
            for (idx in dateRanges.indices) {
                val start = dateRanges[idx].first
                val end = if (idx + 1 < dateRanges.size) dateRanges[idx + 1].first else piece.length
                val slice = piece.substring(start, end).trim(' ', ',', ';', '-', '–', '—', '·', '|')
                if (slice.isNotEmpty()) slices.add(slice)
            }
        }
        return slices.ifEmpty { listOf(piece) }
    }

    private fun splitAtDateSeparators(piece: String, defaultYear: Int): List<String> {
        val separatorRegex = Regex("""(?<!\d)\s*,\s*(?!\d)|\s+·\s+|\s+e\s+""", RegexOption.IGNORE_CASE)
        val cutPoints = mutableListOf<Pair<Int, Int>>()
        for (m in separatorRegex.findAll(piece)) {
            val left = piece.substring(0, m.range.first)
            val right = piece.substring(m.range.last + 1)
            if (findValidDateRangesInSegment(left, defaultYear).isNotEmpty() &&
                findValidDateRangesInSegment(right, defaultYear).isNotEmpty()
            ) {
                cutPoints.add(m.range.first to (m.range.last + 1))
            }
        }
        if (cutPoints.isEmpty()) return listOf(piece)
        val result = mutableListOf<String>()
        var start = 0
        for ((cutStart, cutEnd) in cutPoints) {
            val part = piece.substring(start, cutStart).trim()
            if (part.isNotEmpty()) result.add(part)
            start = cutEnd
        }
        val tail = piece.substring(start).trim()
        if (tail.isNotEmpty()) result.add(tail)
        return result
    }

    private fun isSyllabusNoiseOrProse(rawSegment: String, cleanedWithoutDate: String): Boolean {
        val strippedLeadingRaw = rawSegment.replace(Regex("""^[\s.,;:\-–—•·|/]+"""), "")
        val strippedLeadingWithoutDate = cleanedWithoutDate.replace(Regex("""^[\s.,;:\-–—•·|/]+"""), "")

        val outsideParensRaw = strippedLeadingRaw
            .replace(Regex("""\([^)]*\)"""), " ")
            .replace(Regex("""\[[^\]]*]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val normOutside = normalizeSearchText(outsideParensRaw)
        val normWithoutDate = normalizeSearchText(
            strippedLeadingWithoutDate
                .replace(Regex("""\([^)]*\)"""), " ")
                .replace(Regex("""\[[^\]]*]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        )
        val normOutsideWords = normOutside.replace(Regex("""[^a-z0-9]+"""), " ").trim()
        val normWithoutDateWords = normWithoutDate.replace(Regex("""[^a-z0-9]+"""), " ").trim()
        val clutterStripped = stripSyllabusClutter(strippedLeadingWithoutDate)
        val normCandidateWords = normalizeSearchText(clutterStripped)
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()

        if (normCandidateWords.isBlank()) {
            return true
        }

        if (syllabusLeadingObsNoiseRegex.containsMatchIn(normOutside) ||
            syllabusLeadingObsNoiseRegex.containsMatchIn(normWithoutDate)
        ) {
            return true
        }

        if (syllabusHardNoiseRegex.containsMatchIn(normOutside) ||
            syllabusHardNoiseRegex.containsMatchIn(normWithoutDate) ||
            syllabusHardNoiseRegex.containsMatchIn(normOutsideWords) ||
            syllabusHardNoiseRegex.containsMatchIn(normWithoutDateWords)
        ) {
            return true
        }

        if (syllabusProseFragmentRegex.containsMatchIn(normOutsideWords) ||
            syllabusProseFragmentRegex.containsMatchIn(normWithoutDateWords) ||
            syllabusProseFragmentRegex.containsMatchIn(normCandidateWords)
        ) {
            return true
        }

        val candidateTokens = normCandidateWords.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (candidateTokens.isEmpty() || candidateTokens.last() in syllabusDanglingTailWords) {
            return true
        }

        // Rejeita fragmentos soltos de frase que não contêm palavra alfanumérica significativa de avaliação
        if (!syllabusCoreEvalKeywordRegex.containsMatchIn(normCandidateWords)) {
            return true
        }

        val hasStructuredEvalAnchor = syllabusStructuredEvalAnchorRegex.containsMatchIn(normWithoutDate) ||
            syllabusStructuredEvalAnchorRegex.containsMatchIn(
                normOutside.replace(Regex("""^\s*\d{1,2}/\d{1,2}(?:/\d{2,4})?\s*[-–—:|]*\s*"""), "")
            )

        val wordsWithoutDate = normWithoutDateWords.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if ((syllabusProseClauseRegex.containsMatchIn(normOutsideWords) ||
                syllabusProseClauseRegex.containsMatchIn(normWithoutDateWords)) &&
            !hasStructuredEvalAnchor
        ) {
            return true
        }
        if (wordsWithoutDate.size > 14 && !hasStructuredEvalAnchor) {
            return true
        }
        // Frases longas em prosa que terminam com ponto e não começam como evento de avaliação
        if (wordsWithoutDate.size >= 10 &&
            outsideParensRaw.trimEnd().endsWith(".") &&
            !hasStructuredEvalAnchor
        ) {
            return true
        }
        return false
    }

    private fun stripSyllabusClutter(raw: String): String {
        return raw
            .replace(Regex("""^[\s.,;:\-–—•·|/]+"""), "")
            // Remove parênteses/colchetes de pontuação, peso, horário, observações ou conteúdo genérico
            .replace(
                Regex(
                    """\(\s*[^)]*(?:pts?\.?|pontos?|peso|valor|valendo|%|horas?|h\b|aula|obs\.?|todo\s+o\s+conte[uú]do|sem\s+consulta|com\s+consulta|individual|em\s+grupo|dupla|unidades?|cap[ií]tulos?|seg(?:unda)?|ter(?:[çc]a)?|qua(?:rta)?|qui(?:nta)?|sex(?:ta)?|s[aá]b(?:ado)?|\d{1,2}:\d{2})[^)]*\)""",
                    RegexOption.IGNORE_CASE
                ),
                " "
            )
            .replace(
                Regex(
                    """\[\s*[^\]]*(?:pts?\.?|pontos?|peso|valor|%|horas?|obs\.?)[^\]]*]""",
                    RegexOption.IGNORE_CASE
                ),
                " "
            )
            // Remove pontuação / pesos / percentuais soltos
            .replace(
                Regex("""\b(?:valor|valendo|peso|nota|pontua[çc][aã]o)\s*[:=]?\s*\d+(?:[.,]\d+)?\s*(?:pts?\.?|pontos?|%)?\b""", RegexOption.IGNORE_CASE),
                " "
            )
            .replace(Regex("""\b\d+(?:[.,]\d+)?\s*(?:pts?\.?|pontos?|%)\b""", RegexOption.IGNORE_CASE), " ")
            // Remove horários
            .replace(Regex("""\b(?:[àa]s?\s*)?(?:[01]?\d|2[0-3]):[0-5]\d(?:\s*[-–a]\s*(?:[01]?\d|2[0-3]):[0-5]\d)?\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\b(?:[àa]s?\s*)?(?:[01]?\d|2[0-3])h(?:[0-5]\d)?\b""", RegexOption.IGNORE_CASE), " ")
            // Remove dias da semana
            .replace(Regex("""\b(?:segunda|ter[çc]a|quarta|quinta|sexta|s[aá]bado|domingo)(?:-feira)?\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\b(?:seg|ter|qua|qui|sex|s[aá]b|dom)\.\s*""", RegexOption.IGNORE_CASE), " ")
            // Remove prefixos "Aula 12 -", "Unidade 3 -", "Cronograma:", "Avaliações práticas:", numeração "1. "
            .replace(
                Regex(
                    """^\s*(?:avalia[çc][õo]es(?:\s+(?:pr[áa]ticas?|te[óo]ricas?|previstas?|parciais?|da\s+disciplina))?|provas(?:\s+previstas)?|datas(?:\s+d[eao]s?\s+(?:provas?|avalia[çc][õo]es))?|cronograma(?:\s+de\s+avalia[çc][õo]es)?|calend[áa]rio(?:\s+de\s+(?:provas?|avalia[çc][õo]es))?)\s*[:\-]\s*""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(Regex("""^\s*(?:aula|semana|encontro|unidade|m[óo]dulo|cap[ií]tulo|cap\.?)\s*\d+[a-zº°ª]?\s*[-–—:|.]*\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^\s*\d{1,2}\s*[.)]\s+"""), "")
            // Remove caudas de conteúdo/unidade após hífen ou dois-pontos
            .replace(Regex("""\s*[-–—|]?\s*\b(?:conte[uú]do|unidades?|cap[ií]tulos?|m[óo]dulos?|assunto|t[óo]picos?)\s*[:=\-].*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*[-–—|]\s*(?:unidades?|cap\.?|m[óo]dulos?)\s+\d+.*$""", RegexOption.IGNORE_CASE), "")
            // Remove preposições soltas deixadas pela remoção da data
            .replace(Regex("""\b(?:no\s+dia|dia|em)\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^\s*(?:no\s+dia|dia|data\s*:?)\s+""", RegexOption.IGNORE_CASE), "")
            .let { cleanExtractedTitle(it) }
    }

    private fun extractSyllabusEvalNumber(normText: String): Int? {
        Regex("""\b(?:p|tp)\s*([1-9])\b""").find(normText)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        Regex("""\b(?:prova|avaliacao|trabalho|lista|relatorio|seminario|projeto|teste|quiz|atividade)\s*(?:n[º°o.]?\s*)?([1-9])\b""")
            .find(normText)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        Regex("""\b(?:prova|avaliacao|trabalho|lista|relatorio|seminario|projeto|teste)\s+(i{1,3}|iv|v)\b""")
            .find(normText)?.groupValues?.getOrNull(1)?.let { roman ->
                return when (roman) {
                    "i" -> 1
                    "ii" -> 2
                    "iii" -> 3
                    "iv" -> 4
                    "v" -> 5
                    else -> null
                }
            }
        Regex("""\b([1-5])\s*[aªºo]\s*(?:prova|avaliacao|trabalho|lista|relatorio|seminario|projeto|teste)\b""")
            .find(normText)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        return when {
            Regex("""\bprimeir[ao]\b""").containsMatchIn(normText) -> 1
            Regex("""\bsegund[ao]\b""").containsMatchIn(normText) -> 2
            Regex("""\bterceir[ao]\b""").containsMatchIn(normText) -> 3
            Regex("""\bquart[ao]\b""").containsMatchIn(normText) -> 4
            Regex("""\bquint[ao]\b""").containsMatchIn(normText) -> 5
            else -> null
        }
    }

    private fun extractDescriptiveTopic(cleaned: String, kindKeywordRegex: Regex, courseTag: String, courseName: String): String? {
        var remainder = cleaned
            .replace(Regex("""^\s*(?:entrega\s+d[eao]s?\s+|apresenta[çc][aã]o\s+d[eao]s?\s+)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\btp\s*\d*\b""", RegexOption.IGNORE_CASE), " ")
            .replace(kindKeywordRegex, " ")
            .replace(Regex("""\b(?:pr[áa]tic[oa]s?|te[óo]ric[oa]s?|escrit[oa]s?|orais?|oral|final|parcial|avaliativ[oa]s?|em\s+grupo|individual|duplas?|equipes?|exerc[íi]cios?|moodle|online|presencial)\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\b(?:[1-5]\s*[aªºo]|primeir[ao]|segund[ao]|terceir[ao]|quart[ao]|quint[ao])\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""^\s*[-–—:|,.\d\s]+"""), "")
            .replace(Regex("""^\s*(?:sobre|acerca\s+de|de|da|do|das|dos)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^\s*[-–—:|,.\s]+"""), "")
            .replace(Regex("""[-–—:|,.\s]+$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (remainder.isBlank() || remainder.length > 34) return null

        val normRem = normalizeSearchText(remainder)
        if (normRem == normalizeSearchText(courseTag) || normRem == normalizeSearchText(courseName)) return null
        if (Regex("""\b(?:sera|deve|devera|entregar|enviar|realizar|fazer|valendo|nota|media|conteudo|unidade|capitulo|todo|disciplina|curso|turma|lab\s*\d+)\b""").containsMatchIn(normRem)) {
            return null
        }

        val stopwords = setOf("de", "da", "do", "das", "dos", "e", "em", "na", "no", "nas", "nos", "para", "com", "sobre", "a", "o", "as", "os", "pelos", "pelas", "pelo", "pela")
        val normTokens = normRem.replace(Regex("""[^a-z0-9]+"""), " ").trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (normTokens.isEmpty() || normTokens.last() in syllabusDanglingTailWords) return null
        val meaningfulWords = normTokens.filter { it.length >= 2 && it !in stopwords }
        if (meaningfulWords.isEmpty() || meaningfulWords.size > 4) return null

        // Remove ponto final ou aspas extras
        remainder = remainder.trim('"', '\'', '.', ',', ';', '-', '–', '—', ':')
        return remainder.takeIf { it.length in 3..32 }
    }

    private fun formatSyllabusAssessmentTitleAndKind(
        rawTitle: String,
        keywordSource: String,
        course: CourseSeed
    ): Pair<String, String>? {
        val cleaned = stripSyllabusClutter(rawTitle.ifBlank { keywordSource })
        if (cleaned.isBlank()) return null
        val normCleaned = normalizeSearchText(cleaned)
        val normCleanedWords = normCleaned.replace(Regex("""[^a-z0-9]+"""), " ").trim()
        if (normCleanedWords.isBlank() || !syllabusCoreEvalKeywordRegex.containsMatchIn(normCleanedWords)) {
            return null
        }
        val normTokens = normCleanedWords.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (normTokens.isEmpty() || normTokens.last() in syllabusDanglingTailWords) {
            return null
        }
        val normSource = normalizeSearchText(keywordSource)
        val norm = normCleaned
        val courseTag = buildCourseShortTag(course)
        val evalNumber = extractSyllabusEvalNumber(norm) ?: extractSyllabusEvalNumber(normSource)

        val (title, kind) = when {
            Regex("""\bexame\s+(?:especial|final)\b|\bexame\b""").containsMatchIn(norm) ->
                "EXAME ESPECIAL $courseTag" to "Prova"

            Regex("""\bsubstitutiva\b|\breposicao\b|\bprova\s+de\s+recuperacao\b""").containsMatchIn(norm) ->
                "PROVA SUBSTITUTIVA $courseTag" to "Prova"

            Regex("""\b(?:prova|avaliacao)\s+final\b""").containsMatchIn(norm) ->
                "PROVA FINAL $courseTag" to "Prova"

            Regex("""\bseminarios?\b""").containsMatchIn(norm) -> {
                val topic = extractDescriptiveTopic(
                    cleaned = cleaned,
                    kindKeywordRegex = Regex("""\bsemin[áa]rios?\b""", RegexOption.IGNORE_CASE),
                    courseTag = courseTag,
                    courseName = course.name
                )
                val formatted = when {
                    topic != null -> "SEMINÁRIO DE ${topic.uppercase(PT_BR_LOCALE)}"
                    evalNumber != null -> "SEMINÁRIO $evalNumber $courseTag"
                    else -> "SEMINÁRIO $courseTag"
                }
                formatted to "Seminário"
            }

            Regex("""\btp\d*\b|\btrabalhos?\b""").containsMatchIn(norm) -> {
                val topic = extractDescriptiveTopic(
                    cleaned = cleaned,
                    kindKeywordRegex = Regex("""\btrabalhos?\b""", RegexOption.IGNORE_CASE),
                    courseTag = courseTag,
                    courseName = course.name
                )
                val formatted = when {
                    topic != null -> "TRABALHO DE ${topic.uppercase(PT_BR_LOCALE)}"
                    evalNumber != null -> "TRABALHO $evalNumber $courseTag"
                    norm.contains("final") -> "TRABALHO FINAL $courseTag"
                    else -> "TRABALHO $courseTag"
                }
                formatted to "Trabalho"
            }

            Regex("""\bprojetos?\b""").containsMatchIn(norm) -> {
                val topic = extractDescriptiveTopic(
                    cleaned = cleaned,
                    kindKeywordRegex = Regex("""\bprojetos?\b""", RegexOption.IGNORE_CASE),
                    courseTag = courseTag,
                    courseName = course.name
                )
                val formatted = when {
                    topic != null -> "PROJETO DE ${topic.uppercase(PT_BR_LOCALE)}"
                    evalNumber != null -> "PROJETO $evalNumber $courseTag"
                    norm.contains("final") -> "PROJETO FINAL $courseTag"
                    else -> "PROJETO $courseTag"
                }
                formatted to "Trabalho"
            }

            Regex("""\blistas?\b""").containsMatchIn(norm) -> {
                val formatted = if (evalNumber != null) "LISTA $evalNumber $courseTag" else "LISTA $courseTag"
                formatted to "Trabalho"
            }

            Regex("""\brelatorios?\b""").containsMatchIn(norm) -> {
                val topic = if (evalNumber == null) {
                    extractDescriptiveTopic(
                        cleaned = cleaned,
                        kindKeywordRegex = Regex("""\brelat[óo]rios?\b""", RegexOption.IGNORE_CASE),
                        courseTag = courseTag,
                        courseName = course.name
                    )
                } else null
                val formatted = when {
                    evalNumber != null -> "RELATÓRIO $evalNumber $courseTag"
                    topic != null -> "RELATÓRIO DE ${topic.uppercase(PT_BR_LOCALE)}"
                    else -> "RELATÓRIO $courseTag"
                }
                formatted to "Trabalho"
            }

            Regex("""\bp[1-9]\b|\bprovas?\b""").containsMatchIn(norm) -> {
                val formatted = if (evalNumber != null) "PROVA $evalNumber $courseTag" else "PROVA $courseTag"
                formatted to "Prova"
            }

            Regex("""\b(?:testes?|quizzes?|quiz)\b""").containsMatchIn(norm) -> {
                val formatted = if (evalNumber != null) "TESTE $evalNumber $courseTag" else "TESTE $courseTag"
                formatted to "Prova"
            }

            Regex("""\bavaliac(?:ao|oes)\b""").containsMatchIn(norm) -> {
                val formatted = if (evalNumber != null) "PROVA $evalNumber $courseTag" else "AVALIAÇÃO $courseTag"
                formatted to "Prova"
            }

            Regex("""\bapresentacao\b""").containsMatchIn(norm) -> {
                val topic = extractDescriptiveTopic(
                    cleaned = cleaned,
                    kindKeywordRegex = Regex("""\bapresenta[çc][aã]o\b""", RegexOption.IGNORE_CASE),
                    courseTag = courseTag,
                    courseName = course.name
                )
                val formatted = when {
                    topic != null -> "SEMINÁRIO DE ${topic.uppercase(PT_BR_LOCALE)}"
                    evalNumber != null -> "SEMINÁRIO $evalNumber $courseTag"
                    else -> "SEMINÁRIO $courseTag"
                }
                formatted to "Seminário"
            }

            else -> return null
        }

        val finalTitle = title
            .uppercase(PT_BR_LOCALE)
            .replace(Regex("""\s+"""), " ")
            .trim()
            .let { if (it.length > 64) it.take(64).trimEnd() else it }

        if (finalTitle.isBlank()) return null
        return finalTitle to kind
    }

    // ── Sprint 7: Calendário Acadêmico Assinado (Admin SHA-256 & Sincronização) ───────────────────

    override fun isAdminUnlocked(): Boolean = synchronized(lock) { adminUnlocked }

    override fun unlockAdminMode(pin: String): Boolean = synchronized(lock) {
        val cleanPin = pin.trim()
        val ok = cleanPin == "UFOP2026" || cleanPin == "2421165"
        if (ok && !adminUnlocked) {
            adminUnlocked = true
            persistStateLocked()
            _mutationVersion.value += 1L
        }
        ok
    }

    override fun adminPublishCalendarPdf(pdfBytes: ByteArray, semester: String): AcademicCalendarUpdatePackage {
        require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
        val rawText = SecurePdfProcessor.withZeroedPdfBytes(pdfBytes) { safeBytes ->
            extractPdfTextWithPdfBox(safeBytes)
        }
        return synchronized(lock) {
            if (!adminUnlocked) {
                throw IllegalStateException("Modo administrador bloqueado. Informe o PIN de administração antes de publicar.")
            }
            val cleanSemester = semester.trim().ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
            val defaultYear = resolveSemesterDefaultYear(cleanSemester)
            val parsedEvents = parseOfficialCalendarEvents(rawText, cleanSemester, defaultYear)
            require(parsedEvents.isNotEmpty()) {
                "Nenhum feriado, recesso ou prazo acadêmico identificado no PDF."
            }

            generatedIdCounter += 1L
            val version = "$cleanSemester-v$generatedIdCounter"
            val publishedBy = studentProfile.fullName.trim()
                .takeIf { it.isNotEmpty() }
                ?.let { "Admin Local · $it" }
                ?: "Admin Local"
            val publishedAt = Instant.now().toString()
            val signature = computeCalendarSignatureSha256(
                version = version,
                semester = cleanSemester,
                publishedBy = publishedBy,
                publishedAt = publishedAt,
                events = parsedEvents
            )
            val pkg = AcademicCalendarUpdatePackage(
                version = version,
                semester = cleanSemester,
                publishedBy = publishedBy,
                publishedAt = publishedAt,
                signatureSha256 = signature,
                events = parsedEvents
            )
            pendingCalendarUpdate = pkg
            latestPublishedCalendarUpdate = pkg
            exportCalendarUpdateJsonLocked()
            persistStateLocked()
            _mutationVersion.value += 1L
            pkg
        }
    }

    override fun exportCalendarUpdateJson(): String = synchronized(lock) {
        exportCalendarUpdateJsonLocked()
    }

    private fun exportCalendarUpdateJsonLocked(): String {
        val pkg = pendingCalendarUpdate ?: latestPublishedCalendarUpdate ?: run {
            val cleanSemester = studentProfile.activeSemester.ifBlank { "2026.2" }
            val activeEvents = getAcademicCalendarEventsLocked(cleanSemester)
            val version = "$cleanSemester-v1"
            val publishedBy = "Catálogo do App"
            val publishedAt = Instant.now().toString()
            val signature = computeCalendarSignatureSha256(
                version = version,
                semester = cleanSemester,
                publishedBy = publishedBy,
                publishedAt = publishedAt,
                events = activeEvents
            )
            AcademicCalendarUpdatePackage(
                version = version,
                semester = cleanSemester,
                publishedBy = publishedBy,
                publishedAt = publishedAt,
                signatureSha256 = signature,
                events = activeEvents
            )
        }
        return calendarPackageToJsonLocked(pkg).toString(2)
    }

    override fun importCalendarUpdateJson(jsonPayload: String): AcademicCalendarUpdatePackage = synchronized(lock) {
        val trimmed = jsonPayload.trim()
        require(trimmed.isNotEmpty()) { "Pacote de calendário vazio." }
        val root = try {
            JSONObject(trimmed)
        } catch (_: Throwable) {
            throw IllegalArgumentException("Arquivo de calendário JSON inválido.")
        }
        val pkg = parseCalendarPackageFromJson(root)
            ?: throw IllegalArgumentException("Pacote de calendário incompleto ou sem eventos.")

        require(pkg.signatureSha256.length == 64 && pkg.signatureSha256.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "Assinatura SHA-256 ausente ou malformada."
        }
        val expectedSignature = computeCalendarSignatureSha256(
            version = pkg.version,
            semester = pkg.semester,
            publishedBy = pkg.publishedBy,
            publishedAt = pkg.publishedAt,
            events = pkg.events
        )
        if (!pkg.signatureSha256.equals(expectedSignature, ignoreCase = true)) {
            throw IllegalArgumentException(
                "Assinatura SHA-256 inválida: o pacote de calendário foi adulterado ou corrompido."
            )
        }

        pendingCalendarUpdate = pkg
        latestPublishedCalendarUpdate = pkg
        persistStateLocked()
        _mutationVersion.value += 1L
        pkg
    }

    override fun getPendingCalendarUpdate(): AcademicCalendarUpdatePackage? = synchronized(lock) {
        pendingCalendarUpdate
    }

    override fun applyPendingCalendarUpdate(): Int = synchronized(lock) {
        val pending = pendingCalendarUpdate ?: return@synchronized 0
        val cleanSemester = pending.semester.trim().ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        calendarEventsBySemester[cleanSemester] = pending.events.sortedBy { it.eventDate }.toMutableList()
        pendingCalendarUpdate = null
        persistStateLocked()
        _mutationVersion.value += 1L
        pending.events.size
    }

    private fun parseOfficialCalendarEvents(
        rawText: String,
        semester: String,
        defaultYear: Int
    ): List<AcademicCalendarEvent> {
        val lines = rawText.lines()
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotEmpty() }

        var sectionMonth: Int? = null
        var sectionYear: Int = defaultYear
        val monthHeaderRegex = Regex(
            """^(janeiro|fevereiro|mar[çc]o|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)(?:\s*(?:de|/|-)?\s*(\d{4}))?$""",
            RegexOption.IGNORE_CASE
        )

        data class CalendarCandidate(
            val isoDate: String,
            val title: String,
            val category: String,
            val matchesKeyword: Boolean
        )

        val candidates = mutableListOf<CalendarCandidate>()
        for (line in lines) {
            val headerMatch = monthHeaderRegex.matchEntire(line)
            if (headerMatch != null) {
                sectionMonth = parsePortugueseMonthNumber(headerMatch.groupValues[1])
                sectionYear = normalizeYear(headerMatch.groupValues[2].takeIf { it.isNotBlank() }, defaultYear)
                continue
            }

            val normLine = normalizeSearchText(line)
            if (calendarBoilerplateRegex.containsMatchIn(normLine) &&
                !calendarKeepKeywordsRegex.containsMatchIn(normLine)
            ) {
                continue
            }

            val parsed = extractDateFromSegment(line, sectionYear, fallbackMonth = sectionMonth) ?: continue
            if (parsed.isBareDate || parsed.remainingTitle.length < 3) continue

            val title = parsed.remainingTitle.let { if (it.length > 110) it.take(107).trimEnd() + "..." else it }
            val normTitle = normalizeSearchText(title)
            val isKeywordMatch = calendarKeepKeywordsRegex.containsMatchIn(normTitle)
            candidates.add(
                CalendarCandidate(
                    isoDate = parsed.isoDate,
                    title = title,
                    category = classifyCalendarEventCategory(normTitle),
                    matchesKeyword = isKeywordMatch
                )
            )
        }

        val filtered = if (candidates.any { it.matchesKeyword }) {
            candidates.filter { it.matchesKeyword }
        } else {
            candidates
        }

        val seen = mutableSetOf<Pair<String, String>>()
        val semesterSlug = semester.replace(".", "_")
        return filtered
            .filter { seen.add(it.isoDate to normalizeDedupKey(it.title)) }
            .sortedBy { it.isoDate }
            .mapIndexed { idx, item ->
                val (_, dayNum, monthShort) = parseDueDateParts(item.isoDate)
                AcademicCalendarEvent(
                    id = "cal_${semesterSlug}_${idx + 1}_${item.isoDate.replace("-", "")}",
                    semester = semester,
                    eventDate = item.isoDate,
                    dayNumber = dayNum,
                    monthShort = monthShort,
                    title = item.title,
                    category = item.category
                )
            }
    }

    private fun classifyCalendarEventCategory(normalizedTitle: String): String = when {
        Regex("""feriado|ponto\s+facultativo|carnaval|finados|tiradentes|natal|consciencia|republica|independencia|padroeira|pascoa|corpus""").containsMatchIn(normalizedTitle) -> "Feriado"
        Regex("""recesso|ferias|pausa""").containsMatchIn(normalizedTitle) -> "Recesso"
        Regex("""exame|prova|avaliac""").containsMatchIn(normalizedTitle) -> "Avaliação"
        Regex("""inicio|termino|encerramento|semestre|letivo|letiva""").containsMatchIn(normalizedTitle) -> "Semestre"
        Regex("""estudo|semana|mostra|seminario""").containsMatchIn(normalizedTitle) -> "Ensino"
        else -> "Acadêmico"
    }

    private fun computeCalendarSignatureSha256(
        version: String,
        semester: String,
        publishedBy: String,
        publishedAt: String,
        events: List<AcademicCalendarEvent>
    ): String {
        val canonical = buildString {
            append("ufop-cal-v1|")
            append(version).append('|')
            append(semester).append('|')
            append(publishedBy).append('|')
            append(publishedAt).append('|')
            events.sortedWith(
                compareBy<AcademicCalendarEvent> { it.eventDate }
                    .thenBy { it.id }
                    .thenBy { it.title }
            ).forEach { ev ->
                append(ev.id).append(':')
                append(ev.semester).append(':')
                append(ev.eventDate).append(':')
                append(ev.dayNumber).append(':')
                append(ev.monthShort).append(':')
                append(ev.title).append(':')
                append(ev.category).append(';')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun calendarPackageToJsonLocked(pkg: AcademicCalendarUpdatePackage): JSONObject {
        val eventsArr = JSONArray()
        pkg.events.forEach { ev ->
            eventsArr.put(
                JSONObject().apply {
                    put("id", ev.id)
                    put("semester", ev.semester)
                    put("eventDate", ev.eventDate)
                    put("dayNumber", ev.dayNumber)
                    put("monthShort", ev.monthShort)
                    put("title", ev.title)
                    put("category", ev.category)
                }
            )
        }
        return JSONObject().apply {
            put("format", "ufopcal-v1")
            put("version", pkg.version)
            put("semester", pkg.semester)
            put("publishedBy", pkg.publishedBy)
            put("publishedAt", pkg.publishedAt)
            put("signatureSha256", pkg.signatureSha256)
            put("events", eventsArr)
        }
    }

    private fun parseCalendarPackageFromJson(obj: JSONObject?): AcademicCalendarUpdatePackage? {
        if (obj == null) return null
        val version = obj.optNonNullString("version").trim()
        val semester = obj.optNonNullString("semester").trim()
        val publishedBy = obj.optNonNullString("publishedBy").trim()
        val publishedAt = obj.optNonNullString("publishedAt").trim()
        val signatureSha256 = obj.optNonNullString("signatureSha256").trim()
        if (version.isEmpty() || semester.isEmpty() || signatureSha256.isEmpty()) return null

        val eventsArr = obj.optJSONArray("events") ?: return null
        val parsedEvents = mutableListOf<AcademicCalendarEvent>()
        for (i in 0 until eventsArr.length()) {
            val evObj = eventsArr.optJSONObject(i) ?: continue
            val evId = evObj.optNonNullString("id").trim()
            val evSemester = evObj.optNonNullString("semester", semester).trim()
            val eventDate = evObj.optNonNullString("eventDate").trim()
            val title = evObj.optNonNullString("title").trim()
            if (evId.isEmpty() || eventDate.isEmpty() || title.isEmpty()) continue
            val (_, parsedDay, parsedMonth) = parseDueDateParts(eventDate)
            parsedEvents.add(
                AcademicCalendarEvent(
                    id = evId,
                    semester = evSemester,
                    eventDate = eventDate,
                    dayNumber = evObj.optNonNullString("dayNumber", parsedDay).ifBlank { parsedDay },
                    monthShort = evObj.optNonNullString("monthShort", parsedMonth).ifBlank { parsedMonth },
                    title = title,
                    category = evObj.optNonNullString("category", "Acadêmico").ifBlank { "Acadêmico" }
                )
            )
        }
        if (parsedEvents.isEmpty()) return null
        return AcademicCalendarUpdatePackage(
            version = version,
            semester = semester,
            publishedBy = publishedBy,
            publishedAt = publishedAt,
            signatureSha256 = signatureSha256,
            events = parsedEvents
        )
    }

    private fun extractPdfTextWithPdfBox(pdfBytes: ByteArray): String {
        require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
        return SecurePdfProcessor.loadDocumentInMemoryOnly(pdfBytes).use { doc ->
            PDFTextStripper().apply {
                sortByPosition = true
            }.getText(doc)
        }
    }

    private fun resolveSemesterDefaultYear(semester: String): Int {
        val fromSem = Regex("""(\d{4})""").find(semester)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (fromSem != null && fromSem in 1990..2100) return fromSem
        val fromActive = Regex("""(\d{4})""").find(studentProfile.activeSemester)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (fromActive != null && fromActive in 1990..2100) return fromActive
        return LocalDate.now().year
    }

    private fun normalizeYear(rawYear: String?, defaultYear: Int): Int {
        val parsed = rawYear?.trim()?.toIntOrNull() ?: return defaultYear
        return when {
            parsed in 0..99 -> 2000 + parsed
            parsed in 1900..2100 -> parsed
            else -> defaultYear
        }
    }

    private fun normalizeDedupKey(raw: String): String =
        normalizeSearchText(raw)
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()

    private fun extractDateFromSegment(
        segment: String,
        defaultYear: Int,
        fallbackMonth: Int?
    ): ParsedLineDate? {
        val extractedTime = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""").find(segment)?.let { m ->
            "${m.groupValues[1].padStart(2, '0')}:${m.groupValues[2]}"
        }

        // 1. Data por extenso em português (ex.: "18 de novembro de 2026", "10 a 12 de novembro")
        val longDateRegex = Regex(
            """\b(\d{1,2})(?:\s*(?:a|e|at[eé]|-|–)\s*\d{1,2})?\s+de\s+([A-Za-zÀ-ú]{3,12})\.?(?:\s+de\s+(\d{2,4}))?\b""",
            RegexOption.IGNORE_CASE
        )
        longDateRegex.findAll(segment).forEach { m ->
            val day = m.groupValues[1].toIntOrNull()
            val month = parsePortugueseMonthNumber(m.groupValues[2])
            val year = normalizeYear(m.groupValues[3].takeIf { it.isNotBlank() }, defaultYear)
            if (day != null && month != null) {
                val date = try { LocalDate.of(year, month, day) } catch (_: Throwable) { null }
                if (date != null) {
                    val stripped = segment.removeRange(m.range)
                    val title = cleanExtractedTitle(stripped)
                    return ParsedLineDate(date.toString(), title, isBareDateLine(title), extractedTime)
                }
            }
        }

        // 2. Data numérica DD/MM ou DD/MM/YYYY (incluindo faixas "02/12/2026 a 05/12/2026")
        val numericDateRegex = Regex("""(?<!\d)(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?(?!\d)""")
        val scoreSuffixRegex = Regex("""^\s*(?:pts?|pontos?|%)\b""", RegexOption.IGNORE_CASE)
        val scorePrefixRegex = Regex("""(?:nota|valor|peso|escore|pontua[çc][aã]o)\s*[:=]?\s*$""", RegexOption.IGNORE_CASE)

        numericDateRegex.findAll(segment).forEach { m ->
            val before = segment.substring(0, m.range.first)
            val after = segment.substring(m.range.last + 1)
            if (scorePrefixRegex.containsMatchIn(before) || scoreSuffixRegex.containsMatchIn(after)) {
                return@forEach
            }
            val day = m.groupValues[1].toIntOrNull() ?: return@forEach
            val month = m.groupValues[2].toIntOrNull() ?: return@forEach
            val year = normalizeYear(m.groupValues[3].takeIf { it.isNotBlank() }, defaultYear)
            val date = try { LocalDate.of(year, month, day) } catch (_: Throwable) { null } ?: return@forEach

            val rangeContinuation = Regex("""^\s*(?:a|at[eé]|-|–|—)\s*\d{1,2}/\d{1,2}(?:/\d{2,4})?\b""", RegexOption.IGNORE_CASE)
                .find(after)
            val removeEnd = if (rangeContinuation != null) {
                m.range.last + 1 + rangeContinuation.value.length
            } else {
                m.range.last + 1
            }
            val stripped = segment.removeRange(m.range.first, removeEnd)
            val title = cleanExtractedTitle(stripped)
            return ParsedLineDate(date.toString(), title, isBareDateLine(title), extractedTime)
        }

        // 3. Data ISO YYYY-MM-DD
        val isoRegex = Regex("""\b(20\d{2})-(\d{2})-(\d{2})\b""")
        isoRegex.find(segment)?.let { m ->
            val date = parseIsoDateOrNull(m.value)
            if (date != null) {
                val stripped = segment.removeRange(m.range)
                val title = cleanExtractedTitle(stripped)
                return ParsedLineDate(date.toString(), title, isBareDateLine(title), extractedTime)
            }
        }

        // 4. Linha sob cabeçalho mensal no calendário (ex.: "02 - Finados", "02 a 05 - Recesso")
        if (fallbackMonth != null) {
            val sectionDayRegex = Regex(
                """^\s*[•*-]?\s*(\d{1,2})(?:\s*(?:a|e|at[eé]|-|–)\s*\d{1,2})?\s*(?:[-–—:.|]|\s+)\s*(?!(?:horas?|dias?|vagas?|pontos?|meses|anos?|%)\b)([A-Za-zÀ-ú].+)$""",
                RegexOption.IGNORE_CASE
            )
            sectionDayRegex.find(segment)?.let { m ->
                val day = m.groupValues[1].toIntOrNull()
                val date = if (day != null) {
                    try { LocalDate.of(defaultYear, fallbackMonth, day) } catch (_: Throwable) { null }
                } else null
                if (date != null) {
                    val title = cleanExtractedTitle(m.groupValues[2])
                    return ParsedLineDate(date.toString(), title, isBareDateLine(title), extractedTime)
                }
            }
        }

        return null
    }

    private fun cleanExtractedTitle(raw: String): String = raw
        .replace(Regex("""\(\s*\)"""), " ")
        .replace(Regex("""\[\s*\]"""), " ")
        .replace(Regex("""^[\s.,;:\-–—•·|/*]+"""), "")
        .replace(Regex("""[\s.,;:\-–—•·|/*]+$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun isBareDateLine(cleanedTitle: String): Boolean {
        if (cleanedTitle.isBlank()) return true
        return cleanedTitle.matches(
            Regex(
                """^(?:seg(?:unda)?|ter(?:[çc]a)?|qua(?:rta)?|qui(?:nta)?|sex(?:ta)?|s[aá]b(?:ado)?|dom(?:ingo)?|\d{1,2}:\d{2})$""",
                RegexOption.IGNORE_CASE
            )
        )
    }

    // ── Sprint 8: Parser do Histórico Escolar UFOP (PDF/Texto) & Calculadora Oficial de CRE ───────

    private data class ParsedTranscriptMeta(
        val fullName: String?,
        val registrationNumber: String?,
        val courseName: String?,
        val totalRequiredHours: Int?,
        val completedHours: Int?
    )

    override fun getAcademicCreReport(): AcademicCreReport = synchronized(lock) {
        getAcademicCreReportLocked()
    }

    override fun updateTranscriptOrCourseGrade(
        semester: String,
        codeOrCourseId: String,
        grade: Double?,
        absences: Int?
    ): AcademicCreReport = synchronized(lock) {
        val cleanSemester = semester.trim().ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        val cleanTarget = codeOrCourseId.trim()
        require(cleanTarget.isNotEmpty()) { "Informe o código ou ID da disciplina." }
        if (grade != null) {
            require(grade.isFinite()) { "Nota inválida." }
        }
        val clampedGrade = grade?.coerceIn(0.0, 10.0)

        val txIdx = transcriptEntries.indexOfFirst { entry ->
            entry.semester == cleanSemester && (
                entry.id.equals(cleanTarget, ignoreCase = true) ||
                    entry.code.equals(cleanTarget, ignoreCase = true)
                )
        }
        val txEntry = if (txIdx >= 0) transcriptEntries[txIdx] else null

        val matchedCourse = courses.values.firstOrNull { c ->
            c.semester == cleanSemester && (
                c.courseId.equals(cleanTarget, ignoreCase = true) ||
                    resolveCourseCode(c).equals(cleanTarget, ignoreCase = true) ||
                    (txEntry != null && (
                        resolveCourseCode(c).equals(txEntry.code, ignoreCase = true) ||
                            normalizeDedupKey(c.name) == normalizeDedupKey(txEntry.name)
                        ))
                )
        }

        if (absences != null) {
            val safeAbsences = absences.coerceAtLeast(0)
            if (matchedCourse != null) {
                val extraAbsences = computeExtraCheckInAbsencesLocked(matchedCourse.courseId)
                courses[matchedCourse.courseId] = matchedCourse.copy(
                    baseAbsences = safeAbsences - extraAbsences
                )
            }
        }

        if (txIdx >= 0 && txEntry != null) {
            transcriptEntries[txIdx] = txEntry.copy(
                grade = clampedGrade,
                absences = absences?.coerceAtLeast(0) ?: txEntry.absences
            )
        } else if (matchedCourse != null) {
            val resolvedAbsences = absences?.coerceAtLeast(0)
                ?: buildDisciplineSummaryLocked(matchedCourse).currentAbsences
            transcriptEntries.add(
                TranscriptCourseEntry(
                    id = matchedCourse.courseId,
                    semester = matchedCourse.semester,
                    code = resolveCourseCode(matchedCourse),
                    name = matchedCourse.name,
                    workloadHours = matchedCourse.workloadHours,
                    grade = clampedGrade,
                    absences = resolvedAbsences,
                    totalClasses = (matchedCourse.workloadHours * 12) / 10,
                    status = "CS",
                    professor = matchedCourse.professor
                )
            )
        } else {
            val semSlug = cleanSemester.replace(".", "_")
            transcriptEntries.add(
                TranscriptCourseEntry(
                    id = "tx_${semSlug}_${cleanTarget.lowercase()}",
                    semester = cleanSemester,
                    code = cleanTarget.uppercase(),
                    name = cleanTarget.uppercase(),
                    workloadHours = 60,
                    grade = clampedGrade,
                    absences = absences?.coerceAtLeast(0) ?: 0,
                    totalClasses = 72,
                    status = "CS",
                    professor = ""
                )
            )
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        getAcademicCreReportLocked()
    }

    override fun importAcademicTranscriptPdf(pdfBytes: ByteArray): AcademicCreReport {
        require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
        val rawText = SecurePdfProcessor.withZeroedPdfBytes(pdfBytes) { safeBytes ->
            extractPdfTextWithPdfBox(safeBytes)
        }
        return importAcademicTranscriptText(rawText)
    }

    override fun importAcademicTranscriptText(rawText: String): AcademicCreReport {
        val trimmed = rawText.trim()
        require(trimmed.isNotEmpty()) { "Texto do Histórico Escolar vazio." }

        val lines = trimmed.lines()
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotEmpty() }
        val meta = parseTranscriptMetadata(lines)

        val rowRegex = Regex(
            """^(\d{4})[/.-]([1234])\s+([A-Z]{2,4}\d{3,4}[A-Z]?)\s*-\s*(.+?)\s+(\d+)/\d+(?:\s+(?:(\d+(?:[.,]\d+)?)(?!/)|--|-))?(?:\s+(\d+)/(\d+))?\s+(AP|RN|RF|NF|CS|TR|DP|AE|DE|DJ|EA|PF|X)\b""",
            RegexOption.IGNORE_CASE
        )
        val profRowRegex = Regex(
            """^(\d{4})[/.-]([1234])\s+([A-Z]{2,4}\d{3,4}[A-Z]?)\s+(.+?)$""",
            RegexOption.IGNORE_CASE
        )
        val pendingRowPrefixRegex = Regex(
            """^\d{4}[/.-][1234]\s+[A-Z]{2,4}\d{3,4}[A-Z]?\s*-.*""",
            RegexOption.IGNORE_CASE
        )

        val parsedRows = mutableListOf<TranscriptCourseEntry>()
        val professorsBySemAndCode = mutableMapOf<Pair<String, String>, String>()
        val seenIds = mutableSetOf<String>()
        var inProfessorsSection = false
        var idx = 0

        while (idx < lines.size) {
            val line = lines[idx]
            val normLine = normalizeSearchText(line)
            if (normLine.contains("professores ministrantes")) {
                inProfessorsSection = true
                idx += 1
                continue
            }
            if (inProfessorsSection && (
                    normLine.startsWith("abreviaturas usadas") ||
                        normLine.startsWith("situacao na disciplina") ||
                        normLine.startsWith("criterio de avaliacao")
                    )
            ) {
                inProfessorsSection = false
            }

            var rowMatch = rowRegex.find(line)
            var consumedNext = false
            if (rowMatch == null && !inProfessorsSection &&
                pendingRowPrefixRegex.matches(line) && idx + 1 < lines.size
            ) {
                val combined = "$line ${lines[idx + 1]}"
                val combinedMatch = rowRegex.find(combined)
                if (combinedMatch != null) {
                    rowMatch = combinedMatch
                    consumedNext = true
                }
            }

            if (rowMatch != null) {
                val semester = "${rowMatch.groupValues[1]}.${rowMatch.groupValues[2]}"
                val code = rowMatch.groupValues[3].uppercase()
                val name = normalizeUfopPortugueseText(rowMatch.groupValues[4])
                val workload = rowMatch.groupValues[5].toIntOrNull()?.coerceAtLeast(1) ?: 60
                val grade = rowMatch.groupValues[6]
                    .takeIf { it.isNotBlank() }
                    ?.replace(',', '.')
                    ?.toDoubleOrNull()
                    ?.coerceIn(0.0, 10.0)
                val absences = rowMatch.groupValues[7].toIntOrNull()?.coerceAtLeast(0) ?: 0
                val totalClasses = rowMatch.groupValues[8].toIntOrNull()?.coerceAtLeast(0)
                    ?: ((workload * 12) / 10)
                val status = rowMatch.groupValues[9].uppercase()

                val semSlug = semester.replace(".", "_")
                var entryId = "tx_${semSlug}_${code.lowercase()}"
                if (!seenIds.add(entryId)) {
                    entryId = "${entryId}_${parsedRows.size + 1}"
                    seenIds.add(entryId)
                }

                parsedRows.add(
                    TranscriptCourseEntry(
                        id = entryId,
                        semester = semester,
                        code = code,
                        name = name,
                        workloadHours = workload,
                        grade = grade,
                        absences = absences,
                        totalClasses = totalClasses,
                        status = status,
                        professor = ""
                    )
                )
                idx += if (consumedNext) 2 else 1
                continue
            }

            val profMatch = profRowRegex.find(line)
            if (profMatch != null) {
                val rawRest = profMatch.groupValues[4].trim()
                val looksLikePage1Row = rawRest.startsWith("-") ||
                    Regex("""\d+/\d+\s+(?:AP|RN|RF|NF|CS|TR|DP|AE|DE|DJ|EA|PF|X)\b""", RegexOption.IGNORE_CASE)
                        .containsMatchIn(rawRest)
                if (!looksLikePage1Row && (inProfessorsSection || Regex("""\(\s*(?:T\s*\+\s*P|T|P)\s*\)""", RegexOption.IGNORE_CASE).containsMatchIn(rawRest))) {
                    val semester = "${profMatch.groupValues[1]}.${profMatch.groupValues[2]}"
                    val code = profMatch.groupValues[3].uppercase()
                    val cleanProf = cleanTranscriptProfessorName(rawRest)
                    if (cleanProf.isNotEmpty()) {
                        professorsBySemAndCode[semester to code] = cleanProf
                    }
                }
            }
            idx += 1
        }

        require(parsedRows.isNotEmpty()) {
            "Não foi possível identificar disciplinas neste Histórico Escolar da UFOP."
        }

        val enrichedRows = parsedRows.map { entry ->
            val prof = professorsBySemAndCode[entry.semester to entry.code] ?: entry.professor
            if (prof.isNotBlank()) entry.copy(professor = prof) else entry
        }

        return synchronized(lock) {
            applyImportedTranscriptLocked(meta, enrichedRows)
        }
    }

    private fun parseTranscriptMetadata(lines: List<String>): ParsedTranscriptMeta {
        val collapsed = lines.joinToString(" ")

        val regMatch = Regex("""\b(\d{2}\.\d\.\d{3,5})\b""").find(collapsed)
            ?: Regex("""Matr[ií]cula\s*:?\s*([\d.\-/]+)""", RegexOption.IGNORE_CASE).find(collapsed)
        val registrationNumber = regMatch?.groupValues?.getOrNull(1)?.trimEnd(',', '.', ';')
            ?.takeIf { it.isNotEmpty() }

        var fullName: String? = null
        for (i in lines.indices) {
            val line = lines[i]
            val inlineNome = Regex("""^Nome\s*:\s*(.+)$""", RegexOption.IGNORE_CASE).find(line)
            if (inlineNome != null) {
                val candidate = inlineNome.groupValues[1]
                    .replace(Regex("""\bMatr[ií]cula\b.*$""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\b\d{2}\.\d\.\d{3,5}\b"""), "")
                    .trim()
                if (candidate.isNotEmpty()) {
                    fullName = normalizeUfopPortugueseText(candidate)
                    break
                }
            } else if (line.matches(Regex("""^Nome(?:\s+Matr[ií]cula)?\s*$""", RegexOption.IGNORE_CASE))) {
                val next = lines.getOrNull(i + 1).orEmpty()
                    .replace(Regex("""\b\d{2}\.\d\.\d{3,5}\b"""), "")
                    .replace(Regex("""\bMatr[ií]cula\b.*$""", RegexOption.IGNORE_CASE), "")
                    .trim()
                val normNext = normalizeSearchText(next)
                if (next.isNotEmpty() && normNext != "matricula" && normNext != "filiacao" && normNext != "historico") {
                    fullName = normalizeUfopPortugueseText(next)
                    break
                }
            }
        }

        val courseName = lines.asSequence()
            .mapNotNull { Regex("""CURSO:\s*(.+)$""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1) }
            .map {
                it.replace(Regex("""\s+Situa[çc][aã]o\b.*$""", RegexOption.IGNORE_CASE), "").trim()
            }
            .filter { it.isNotEmpty() }
            .map { normalizeUfopPortugueseText(it) }
            .firstOrNull()

        var totalRequiredHours: Int? = Regex("""CARGA\s+CURSO\s+(\d{3,5})\b""", RegexOption.IGNORE_CASE)
            .find(collapsed)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (totalRequiredHours == null) {
            for (i in lines.indices) {
                if (normalizeSearchText(lines[i]).contains("carga curso")) {
                    val sameLine = Regex("""CARGA\s+CURSO\s+(\d{3,5})\b""", RegexOption.IGNORE_CASE)
                        .find(lines[i])?.groupValues?.getOrNull(1)?.toIntOrNull()
                    if (sameLine != null) {
                        totalRequiredHours = sameLine
                        break
                    }
                    val next1 = lines.getOrNull(i + 1).orEmpty()
                    val next2 = lines.getOrNull(i + 2).orEmpty()
                    val fromNext = Regex("""^\s*(\d{3,5})\b""").find(next1)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: Regex("""^\s*(\d{3,5})\b""").find(next2)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    if (fromNext != null) {
                        totalRequiredHours = fromNext
                        break
                    }
                }
            }
        }

        val completedHours = lines.firstOrNull { normalizeSearchText(it).contains("obr:") }
            ?.let { Regex("""\bTOTAL\s*:\s*(\d{1,5})\b""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?: Regex("""\bTOTAL\s*:\s*(\d{1,5})\b""", RegexOption.IGNORE_CASE)
                .find(collapsed)?.groupValues?.getOrNull(1)?.toIntOrNull()

        return ParsedTranscriptMeta(
            fullName = fullName,
            registrationNumber = registrationNumber,
            courseName = courseName,
            totalRequiredHours = totalRequiredHours,
            completedHours = completedHours
        )
    }

    private fun cleanTranscriptProfessorName(raw: String): String {
        val strippedSuffix = raw
            .replace(Regex("""\s*\(\s*(?:T\s*\+\s*P|T|P)\s*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return normalizeUfopPortugueseText(strippedSuffix)
    }

    private fun applyImportedTranscriptLocked(
        meta: ParsedTranscriptMeta,
        entries: List<TranscriptCourseEntry>
    ): AcademicCreReport {
        transcriptEntries.clear()
        transcriptEntries.addAll(entries)

        if (meta.totalRequiredHours != null && meta.totalRequiredHours > 0) {
            totalRequiredHours = meta.totalRequiredHours
        }
        if (meta.completedHours != null && meta.completedHours >= 0) {
            importedCompletedHours = meta.completedHours
        }

        val latestCsSemester = entries
            .filter { it.status == "CS" }
            .maxOfOrNull { it.semester }
        val latestSemester = latestCsSemester ?: entries.maxOfOrNull { it.semester }

        studentProfile = studentProfile.copy(
            fullName = meta.fullName ?: studentProfile.fullName,
            registrationNumber = meta.registrationNumber ?: studentProfile.registrationNumber,
            courseName = meta.courseName ?: studentProfile.courseName,
            activeSemester = if (!studentProfile.onboardingCompleted || studentProfile.activeSemester.isBlank()) {
                latestSemester ?: studentProfile.activeSemester.ifBlank { "2026.2" }
            } else {
                studentProfile.activeSemester
            },
            onboardingCompleted = true
        )

        for (entry in entries) {
            val matchingCourse = courses.values.firstOrNull { c ->
                c.semester == entry.semester && (
                    resolveCourseCode(c).equals(entry.code, ignoreCase = true) ||
                        c.courseId.equals("${entry.code.lowercase()}_${entry.semester.replace(".", "_")}", ignoreCase = true) ||
                        normalizeDedupKey(c.name) == normalizeDedupKey(entry.name)
                    )
            }
            val maxAbs = if (entry.totalClasses > 0) {
                (entry.totalClasses / 4).coerceAtLeast(1)
            } else {
                ((entry.workloadHours * 3) / 10).coerceAtLeast(1)
            }

            if (matchingCourse != null) {
                val updatedProf = entry.professor.ifBlank { matchingCourse.professor }
                val updatedBaseAbsences = if (entry.status != "CS" || matchingCourse.baseAbsences == 0) {
                    val extraAbs = computeExtraCheckInAbsencesLocked(matchingCourse.courseId)
                    (entry.absences - extraAbs).coerceAtLeast(0)
                } else {
                    matchingCourse.baseAbsences
                }
                courses[matchingCourse.courseId] = matchingCourse.copy(
                    name = entry.name,
                    code = entry.code,
                    workloadHours = entry.workloadHours,
                    maxAbsences = maxAbs,
                    baseAbsences = updatedBaseAbsences,
                    professor = updatedProf
                )
            } else {
                val semSlug = entry.semester.replace(".", "_")
                var courseId = "${entry.code.lowercase()}_$semSlug"
                if (courseId in courses) {
                    generatedIdCounter += 1L
                    courseId = "${courseId}_$generatedIdCounter"
                }
                val dept = resolveDepartmentForCourseCode(entry.code)
                val gradeLabel = entry.grade?.let { " · Média: $it" } ?: ""
                val initialNote = "Código: ${entry.code} · Situação: ${entry.status}$gradeLabel"
                courses[courseId] = CourseSeed(
                    courseId = courseId,
                    semester = entry.semester,
                    name = entry.name,
                    departmentId = dept.id,
                    departmentName = dept.name,
                    defaultColorHex = dept.colorHex,
                    workloadHours = entry.workloadHours,
                    baseAbsences = entry.absences,
                    maxAbsences = maxAbs,
                    baseCancelledClasses = 0,
                    professor = entry.professor,
                    buildingAndRoom = "Campus UFOP",
                    shortLocation = "UFOP",
                    assessments = emptyList(),
                    initialNotes = initialNote,
                    code = entry.code
                )
                notesByCourseId[courseId] = initialNote
                assessmentsByCourseId[courseId] = mutableListOf()
            }
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        return getAcademicCreReportLocked()
    }

    private fun getAcademicCreReportLocked(): AcademicCreReport {
        val merged = mutableListOf<TranscriptCourseEntry>()

        for (entry in transcriptEntries) {
            val matchedCourse = courses.values.firstOrNull { c ->
                c.semester == entry.semester && (
                    c.courseId.equals(entry.id, ignoreCase = true) ||
                        resolveCourseCode(c).equals(entry.code, ignoreCase = true) ||
                        normalizeDedupKey(c.name) == normalizeDedupKey(entry.name)
                    )
            }
            if (matchedCourse != null) {
                val liveAbsences = if (entry.status == "CS") {
                    buildDisciplineSummaryLocked(matchedCourse).currentAbsences
                } else {
                    entry.absences
                }
                val liveProfessor = when {
                    entry.professor.isNotBlank() -> entry.professor
                    matchedCourse.professor.isNotBlank() && !matchedCourse.professor.startsWith("Turma ") -> matchedCourse.professor
                    else -> entry.professor
                }
                merged.add(
                    entry.copy(
                        absences = liveAbsences,
                        professor = liveProfessor
                    )
                )
            } else {
                merged.add(entry)
            }
        }

        for (course in courses.values) {
            val courseCode = resolveCourseCode(course)
            val alreadyPresent = merged.any { entry ->
                entry.semester == course.semester && (
                    entry.id.equals(course.courseId, ignoreCase = true) ||
                        entry.code.equals(courseCode, ignoreCase = true) ||
                        normalizeDedupKey(entry.name) == normalizeDedupKey(course.name)
                    )
            }
            if (!alreadyPresent) {
                merged.add(
                    TranscriptCourseEntry(
                        id = course.courseId,
                        semester = course.semester,
                        code = courseCode,
                        name = course.name,
                        workloadHours = course.workloadHours,
                        grade = null,
                        absences = buildDisciplineSummaryLocked(course).currentAbsences,
                        totalClasses = (course.workloadHours * 12) / 10,
                        status = "CS",
                        professor = course.professor
                    )
                )
            }
        }

        val semesters = merged
            .groupBy { it.semester }
            .entries
            .sortedByDescending { it.key }
            .map { (sem, list) ->
                val sortedCourses = list.sortedWith(
                    compareBy<TranscriptCourseEntry> { it.code }.thenBy { it.name }
                )
                val graded = sortedCourses.filter { it.grade != null && it.workloadHours > 0 }
                val gradedWorkload = graded.sumOf { it.workloadHours }
                val weightedSum = graded.sumOf { (it.grade ?: 0.0) * it.workloadHours }
                val semCre = if (gradedWorkload > 0) roundCre2(weightedSum / gradedWorkload) else 0.0
                SemesterCreSummary(
                    semester = sem,
                    cre = semCre,
                    totalWorkloadHours = sortedCourses.sumOf { it.workloadHours },
                    approvedCount = sortedCourses.count { it.status in APPROVED_STATUSES },
                    failedCount = sortedCourses.count { it.status in FAILED_STATUSES },
                    inProgressCount = sortedCourses.count { it.status == "CS" },
                    courses = sortedCourses
                )
            }

        val allGraded = merged.filter { it.grade != null && it.workloadHours > 0 }
        val allGradedWorkload = allGraded.sumOf { it.workloadHours }
        val allWeightedSum = allGraded.sumOf { (it.grade ?: 0.0) * it.workloadHours }
        val overallCre = if (allGradedWorkload > 0) roundCre2(allWeightedSum / allGradedWorkload) else 0.0

        val computedCompleted = merged
            .filter { it.status in APPROVED_STATUSES }
            .sumOf { it.workloadHours }
        val completedHours = if (computedCompleted > 0) computedCompleted else importedCompletedHours

        return AcademicCreReport(
            overallCre = overallCre,
            totalRequiredHours = totalRequiredHours,
            completedHours = completedHours,
            totalApproved = merged.count { it.status in APPROVED_STATUSES },
            totalFailedByGrade = merged.count { it.status == "RN" || it.status == "NF" },
            totalFailedByAbsence = merged.count { it.status == "RF" || it.status == "NF" },
            semesters = semesters
        )
    }

    private fun computeExtraCheckInAbsencesLocked(courseId: String): Int {
        val courseSlotsById = slots.filter { it.courseId == courseId }.associateBy { it.slotId }
        var extraAbsences = 0
        for ((key, status) in checkInStatusBySessionKey) {
            if (status != AttendanceStatus.ABSENT) continue
            val separator = key.lastIndexOf('_')
            if (separator <= 0) continue
            val matchedSlot = courseSlotsById[key.substring(0, separator)] ?: continue
            extraAbsences += matchedSlot.classWeight.coerceAtLeast(1)
        }
        return extraAbsences
    }

    private fun roundCre2(value: Double): Double =
        if (!value.isFinite()) 0.0 else (value * 100.0).roundToInt() / 100.0

    private fun parseIsoDateOrNull(isoDate: String): LocalDate? = try {
        val clean = isoDate.trim()
        if (clean.length < 10) null else LocalDate.parse(clean.take(10))
    } catch (_: Throwable) {
        null
    }

    private fun parseIsoDateOrToday(isoDate: String): LocalDate =
        parseIsoDateOrNull(isoDate) ?: LocalDate.now()

    private fun formatPortugueseDate(isoDate: String): String =
        parseIsoDateOrNull(isoDate)?.let { formatPortugueseDate(it) } ?: isoDate

    private fun formatPortugueseDate(date: LocalDate): String {
        val weekday = when (date.dayOfWeek.value) {
            1 -> "Segunda-feira"
            2 -> "Terça-feira"
            3 -> "Quarta-feira"
            4 -> "Quinta-feira"
            5 -> "Sexta-feira"
            6 -> "Sábado"
            7 -> "Domingo"
            else -> "Segunda-feira"
        }
        return "$weekday, ${date.dayOfMonth} de ${portugueseMonthLower(date.monthValue)}"
    }

    private fun formatMonthYearLabel(date: LocalDate): String {
        val monthCap = portugueseMonthLower(date.monthValue).replaceFirstChar { it.titlecase() }
        return "$monthCap ${date.year}"
    }

    private fun portugueseMonthLower(month: Int): String = when (month) {
        1 -> "janeiro"
        2 -> "fevereiro"
        3 -> "março"
        4 -> "abril"
        5 -> "maio"
        6 -> "junho"
        7 -> "julho"
        8 -> "agosto"
        9 -> "setembro"
        10 -> "outubro"
        11 -> "novembro"
        12 -> "dezembro"
        else -> "novembro"
    }

    private fun toCourseScheduleSlotItem(slot: SlotSeed): CourseScheduleSlotItem =
        CourseScheduleSlotItem(
            slotId = slot.slotId,
            courseId = slot.courseId,
            dayOfWeek = slot.dayOfWeek,
            dayLabel = dayLabelFor(slot.dayOfWeek),
            startTime = slot.startTime,
            endTime = slot.endTime,
            building = slot.building,
            room = slot.room,
            shortLocation = slot.shortLocation
        )

    private fun dayLabelFor(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "SEG"
        2 -> "TER"
        3 -> "QUA"
        4 -> "QUI"
        5 -> "SEX"
        6 -> "SÁB"
        7 -> "DOM"
        else -> "SEG"
    }

    private fun buildShortLocation(building: String, room: String): String {
        if (isEadIndicatorText(building) || building.contains("Ensino a Distância", ignoreCase = true) || building.contains("Ensino a Distancia", ignoreCase = true)) {
            return "EAD"
        }
        val shortB = building.trim()
            .removePrefix("Prédio ")
            .removePrefix("Bloco ")
            .trim()
        val shortR = room.trim()
            .removePrefix("Sala ")
            .replace("Laboratório ", "Lab ")
            .trim()
        return when {
            shortB.isNotEmpty() && shortR.isNotEmpty() -> "$shortB · $shortR"
            shortB.isNotEmpty() -> shortB
            shortR.isNotEmpty() -> shortR
            else -> "UFOP"
        }
    }

    private fun timesOverlap(startA: String, endA: String, startB: String, endB: String): Boolean {
        val a0 = parseMinutes(startA) ?: return startA == startB
        val a1 = parseMinutes(endA) ?: return startA == startB
        val b0 = parseMinutes(startB) ?: return startA == startB
        val b1 = parseMinutes(endB) ?: return startA == startB
        if (a0 >= a1 || b0 >= b1) return startA == startB
        return a0 < b1 && b0 < a1
    }

    private fun parseMinutes(hhmm: String): Int? {
        val parts = hhmm.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun JSONObject.optNonNullString(key: String, fallback: String = ""): String =
        if (isNull(key)) fallback else optString(key, fallback)

    private fun loadPersistedStateOrFallbackLocked(file: File) {
        val parentDir = file.absoluteFile.parentFile
        val candidates = listOf(
            file,
            File(parentDir, "${file.name}.bak"),
            File(parentDir, "${file.name}.tmp")
        )
        for (candidate in candidates) {
            if (!candidate.exists() || candidate.length() <= 2L) continue
            var wasPlaintext = false
            val root = runCatching {
                val raw = candidate.readText(Charsets.UTF_8)
                if (raw.isBlank()) return@runCatching null
                val decrypted = CampusCryptoEngine.decryptStatePayloadIfNeeded(raw)
                wasPlaintext = (decrypted == raw)
                JSONObject(decrypted)
            }.getOrNull() ?: continue

            val schemaVersion = root.optInt("schemaVersion", -1)
            val hasValidData = schemaVersion >= 1 ||
                root.has("courses") ||
                root.has("studentProfile") ||
                root.has("transcriptEntries")
            if (!hasValidData) continue

            val restored = runCatching {
                restoreStateFromJsonLocked(root)
                true
            }.getOrDefault(false)
            if (restored) {
                if (wasPlaintext) {
                    if (candidate != file) {
                        runCatching {
                            Files.copy(
                                candidate.toPath(),
                                file.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                            )
                        }
                    }
                    // Migração v1.7.5 -> v1.8.0: regrava imediatamente criptografado em disco.
                    persistStateLocked(encryptExistingPlaintextBackup = true)
                } else if (candidate != file) {
                    runCatching {
                        Files.copy(
                            candidate.toPath(),
                            file.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                }
                return
            }
        }
        applyVirginStateLocked()
    }

    private fun restoreStateFromJsonLocked(root: JSONObject) {
        val loadedCampusRaw = runCatching { root.optNonNullString("activeCampusId", "op_cruzeiro") }
            .getOrDefault("op_cruzeiro")
        val resolvedCampusId = if (ufopCampusesSeed.any { it.id == loadedCampusRaw }) {
            loadedCampusRaw
        } else {
            "op_cruzeiro"
        }
        val resolvedCounter = runCatching { root.optLong("generatedIdCounter", 100L) }
            .getOrDefault(100L)
            .coerceAtLeast(100L)
        val loadedWeatherFetchMs = runCatching { root.optLong("lastWeatherFetchEpochMs", 0L) }
            .getOrDefault(0L)
            .coerceAtLeast(0L)

        val loadedDarkPref = runCatching {
            val raw = root.optNonNullString("darkThemePreference", "SYSTEM").trim().uppercase()
            if (raw in setOf("SYSTEM", "DARK", "LIGHT")) raw else "SYSTEM"
        }.getOrDefault("SYSTEM")

        val loadedAccentColor = runCatching {
            val raw = root.optNonNullString("accentColorPreference")
                .ifBlank { root.optNonNullString("accent_color_key") }
            normalizeAccentColorKey(raw)
        }.getOrDefault("GREEN")

        val loadedExportUri = runCatching {
            root.optNonNullString("exportFolderTreeUri").trim().takeIf { it.isNotEmpty() }
        }.getOrNull()

        val loadedExportLabel = runCatching {
            if (loadedExportUri == null) {
                DEFAULT_EXPORT_FOLDER_LABEL
            } else {
                root.optNonNullString("exportFolderDisplayLabel", DEFAULT_EXPORT_FOLDER_LABEL)
                    .trim()
                    .ifBlank { DEFAULT_EXPORT_FOLDER_LABEL }
            }
        }.getOrDefault(DEFAULT_EXPORT_FOLDER_LABEL)

        val loadedDeptColors = mutableMapOf<String, String>()
        departmentSwatches.forEach { loadedDeptColors[it.id] = it.colorHex }

        val loadedCourses = linkedMapOf<String, CourseSeed>()
        val loadedNotes = mutableMapOf<String, String>()
        val loadedAssessmentsByCourse = linkedMapOf<String, MutableList<AssessmentItem>>()
        runCatching {
            val coursesArr = root.optJSONArray("courses") ?: return@runCatching
            for (i in 0 until coursesArr.length()) {
                runCatching {
                    val obj = coursesArr.optJSONObject(i) ?: return@runCatching
                    val courseId = obj.optNonNullString("id").ifBlank { obj.optNonNullString("courseId") }.trim()
                    val rawName = obj.optNonNullString("name").trim()
                    if (courseId.isEmpty() || rawName.isEmpty()) return@runCatching
                    val name = normalizeUfopPortugueseText(rawName)
                    val semester = obj.optNonNullString("semester", "2026.2").ifBlank { "2026.2" }
                    val rawDeptId = obj.optNonNullString("departmentId", "comp").ifBlank { "comp" }
                    val deptOption = resolveDepartmentOption(rawDeptId)
                    val workloadHours = obj.optInt("workloadHours", 60).coerceAtLeast(1)
                    val maxAbsences = obj.optInt("maxAbsences", (workloadHours * 3) / 10).coerceAtLeast(0)
                    // Pode ser negativo quando o aluno edita manualmente o total abaixo das faltas de check-in.
                    val manualAbsences = if (obj.has("manualAbsences")) {
                        obj.optInt("manualAbsences", 0)
                    } else {
                        obj.optInt("baseAbsences", 0)
                    }
                    val baseCancelledClasses = obj.optInt("baseCancelledClasses", 0).coerceAtLeast(0)
                    val professor = obj.optNonNullString("professor", "").trim()
                    val buildingAndRoom = obj.optNonNullString("buildingAndRoom", "Campus UFOP").ifBlank { "Campus UFOP" }
                    val shortLocation = obj.optNonNullString("shortLocation", "UFOP").ifBlank { "UFOP" }
                    val notes = obj.optNonNullString("notes", "")
                    val code = deriveCourseCodeFallback(
                        explicitCode = obj.optNonNullString("code"),
                        notes = notes,
                        courseId = courseId,
                        courseName = name
                    )

                    loadedCourses[courseId] = CourseSeed(
                        courseId = courseId,
                        semester = semester,
                        name = name,
                        departmentId = deptOption.id,
                        departmentName = deptOption.name,
                        defaultColorHex = deptOption.colorHex,
                        workloadHours = workloadHours,
                        baseAbsences = manualAbsences,
                        maxAbsences = maxAbsences,
                        baseCancelledClasses = baseCancelledClasses,
                        professor = professor,
                        buildingAndRoom = buildingAndRoom,
                        shortLocation = shortLocation,
                        assessments = emptyList(),
                        initialNotes = notes,
                        code = code
                    )
                    loadedNotes[courseId] = notes
                    loadedAssessmentsByCourse[courseId] = mutableListOf()
                }
            }
        }

        val loadedSlots = mutableListOf<SlotSeed>()
        runCatching {
            val slotsArr = root.optJSONArray("slots") ?: return@runCatching
            for (i in 0 until slotsArr.length()) {
                runCatching {
                    val obj = slotsArr.optJSONObject(i) ?: return@runCatching
                    val slotId = obj.optNonNullString("slotId").trim()
                    val courseId = obj.optNonNullString("courseId").trim()
                    if (slotId.isEmpty() || courseId.isEmpty()) return@runCatching
                    val building = obj.optNonNullString("building", "Campus UFOP").ifBlank { "Campus UFOP" }
                    val room = obj.optNonNullString("room", "").trim()
                    val shortLocation = obj.optNonNullString("shortLocation", buildShortLocation(building, room))
                        .ifBlank { buildShortLocation(building, room) }
                    loadedSlots.add(
                        SlotSeed(
                            slotId = slotId,
                            courseId = courseId,
                            dayOfWeek = obj.optInt("dayOfWeek", 1).coerceIn(1, 7),
                            startTime = obj.optNonNullString("startTime", "08:00").ifBlank { "08:00" },
                            endTime = obj.optNonNullString("endTime", "09:40").ifBlank { "09:40" },
                            building = building,
                            room = room,
                            shortLocation = shortLocation,
                            defaultTempC = obj.optInt("defaultTempC", 22),
                            weatherCondition = obj.optNonNullString("weatherCondition", "Ensolarado").ifBlank { "Ensolarado" },
                            classWeight = obj.optInt("classWeight", 1).coerceAtLeast(1)
                        )
                    )
                }
            }
        }

        val loadedSessions = mutableMapOf<String, AttendanceStatus>()
        runCatching {
            val sessionsArr = root.optJSONArray("sessions") ?: return@runCatching
            for (i in 0 until sessionsArr.length()) {
                runCatching {
                    val obj = sessionsArr.optJSONObject(i) ?: return@runCatching
                    val slotId = obj.optNonNullString("slotId").trim()
                    val sessionDate = obj.optNonNullString("sessionDate").trim()
                    val statusRaw = obj.optNonNullString("status").trim()
                    if (slotId.isEmpty() || sessionDate.isEmpty() || statusRaw.isEmpty()) return@runCatching
                    val parsedStatus = runCatching { AttendanceStatus.valueOf(statusRaw) }.getOrNull()
                    if (parsedStatus != null) {
                        loadedSessions[sessionKey(slotId, sessionDate)] = parsedStatus
                    }
                }
            }
        }

        val loadedCommitments = mutableListOf<CommitmentSeed>()
        runCatching {
            val commitmentsArr = root.optJSONArray("commitments") ?: return@runCatching
            for (i in 0 until commitmentsArr.length()) {
                runCatching {
                    val obj = commitmentsArr.optJSONObject(i) ?: return@runCatching
                    val id = obj.optNonNullString("id").trim()
                    if (id.isEmpty()) return@runCatching
                    loadedCommitments.add(
                        CommitmentSeed(
                            id = id,
                            isoDate = obj.optNonNullString("isoDate", "2026-11-17").ifBlank { "2026-11-17" },
                            title = obj.optNonNullString("title", "Compromisso no campus").ifBlank { "Compromisso no campus" },
                            startTime = obj.optNonNullString("startTime", "14:00").ifBlank { "14:00" },
                            endTime = obj.optNonNullString("endTime", "15:00").ifBlank { "15:00" },
                            location = obj.optNonNullString("location", "Campus UFOP").ifBlank { "Campus UFOP" },
                            subtitle = obj.optNonNullString("subtitle", ""),
                            colorHex = obj.optNonNullString("colorHex", COMMITMENT_COLOR_HEX).ifBlank { COMMITMENT_COLOR_HEX },
                            weatherTempC = obj.optInt("weatherTempC", 25)
                        )
                    )
                }
            }
        }

        runCatching {
            val assessmentsArr = root.optJSONArray("assessments") ?: return@runCatching
            for (i in 0 until assessmentsArr.length()) {
                runCatching {
                    val obj = assessmentsArr.optJSONObject(i) ?: return@runCatching
                    val id = obj.optNonNullString("id").trim()
                    val courseId = obj.optNonNullString("courseId").trim()
                    if (id.isEmpty() || courseId.isEmpty()) return@runCatching
                    val dueDate = obj.optNonNullString("dueDate", "2026-11-28").ifBlank { "2026-11-28" }
                    val (_, parsedDay, parsedMonth) = parseDueDateParts(dueDate)
                    val item = AssessmentItem(
                        id = id,
                        courseId = courseId,
                        dayNumber = obj.optNonNullString("dayNumber", parsedDay).ifBlank { parsedDay },
                        monthShort = obj.optNonNullString("monthShort", parsedMonth).ifBlank { parsedMonth },
                        title = obj.optNonNullString("title", "Avaliação").ifBlank { "Avaliação" },
                        kind = obj.optNonNullString("kind", "Trabalho").ifBlank { "Trabalho" },
                        dueTime = obj.optNonNullString("dueTime", "18:00").ifBlank { "18:00" },
                        dueDate = dueDate,
                        completed = obj.optBoolean("completed", false)
                    )
                    loadedAssessmentsByCourse.getOrPut(courseId) { mutableListOf() }.add(item)
                }
            }
        }

        val defaultCampusCity = ufopCampusesSeed.find { it.id == resolvedCampusId }?.city ?: "Ouro Preto"
        val loadedProfile = runCatching {
            val profileObj = root.optJSONObject("studentProfile")
            if (profileObj != null) {
                val rawCourseName = profileObj.optNonNullString("courseName").trim()
                val loadedActiveSemester = profileObj.optNonNullString("activeSemester", "2026.2").ifBlank { "2026.2" }
                val defaultBounds = resolveDefaultSemesterBounds(loadedActiveSemester)
                StudentProfile(
                    fullName = profileObj.optNonNullString("fullName"),
                    registrationNumber = profileObj.optNonNullString("registrationNumber"),
                    courseName = if (rawCourseName.isEmpty()) "" else normalizeUfopPortugueseText(rawCourseName),
                    campusName = profileObj.optNonNullString("campusName", defaultCampusCity).ifBlank { defaultCampusCity },
                    activeSemester = loadedActiveSemester,
                    semesterStartDate = profileObj.optNonNullString("semesterStartDate", defaultBounds.first).ifBlank { defaultBounds.first },
                    semesterEndDate = profileObj.optNonNullString("semesterEndDate", defaultBounds.second).ifBlank { defaultBounds.second },
                    avatarPath = profileObj.optNonNullString("avatarPath").takeIf { it.isNotBlank() },
                    onboardingCompleted = profileObj.optBoolean("onboardingCompleted", loadedCourses.isNotEmpty())
                )
            } else {
                // Migração limpa de schema v1: mantém usuário já ativo se já havia disciplinas salvas.
                StudentProfile(
                    campusName = defaultCampusCity,
                    onboardingCompleted = loadedCourses.isNotEmpty()
                )
            }
        }.getOrElse {
            StudentProfile(
                campusName = defaultCampusCity,
                onboardingCompleted = loadedCourses.isNotEmpty()
            )
        }

        val loadedFriends = mutableListOf<FriendScheduleEntry>()
        runCatching {
            val friendsArr = root.optJSONArray("friendSchedules") ?: return@runCatching
            for (i in 0 until friendsArr.length()) {
                runCatching {
                    val obj = friendsArr.optJSONObject(i) ?: return@runCatching
                    val id = obj.optNonNullString("id").trim()
                    val friendName = obj.optNonNullString("friendName").trim()
                    if (id.isEmpty() || friendName.isEmpty()) return@runCatching
                    val rawFriendCourseName = obj.optNonNullString("courseName").trim()
                    val friendCourseName = if (rawFriendCourseName.isEmpty()) "" else normalizeUfopPortugueseText(rawFriendCourseName)
                    val friendAvatarBase64 = obj.optNonNullString("avatarBase64").trim().takeIf { it.isNotEmpty() }
                    val semester = obj.optNonNullString("semester", "2026.2").ifBlank { "2026.2" }
                    val fCourses = mutableListOf<FriendCourseEntry>()
                    val fcArr = obj.optJSONArray("courses")
                    if (fcArr != null) {
                        for (j in 0 until fcArr.length()) {
                            runCatching {
                                val fc = fcArr.optJSONObject(j) ?: return@runCatching
                                val cId = fc.optNonNullString("courseId").trim()
                                val rawCName = fc.optNonNullString("name").trim()
                                if (cId.isEmpty() || rawCName.isEmpty()) return@runCatching
                                val cName = normalizeUfopPortugueseText(rawCName)
                                fCourses.add(
                                    FriendCourseEntry(
                                        courseId = cId,
                                        name = cName,
                                        departmentId = resolveDepartmentOption(fc.optNonNullString("departmentId", "decom")).id,
                                        code = deriveCourseCodeFallback(fc.optNonNullString("code"), "", cId, cName)
                                    )
                                )
                            }
                        }
                    }
                    val fSlots = mutableListOf<FriendSlotEntry>()
                    val fsArr = obj.optJSONArray("slots")
                    if (fsArr != null) {
                        for (j in 0 until fsArr.length()) {
                            runCatching {
                                val fs = fsArr.optJSONObject(j) ?: return@runCatching
                                val sId = fs.optNonNullString("slotId").trim()
                                val cId = fs.optNonNullString("courseId").trim()
                                if (sId.isEmpty() || cId.isEmpty()) return@runCatching
                                fSlots.add(
                                    FriendSlotEntry(
                                        slotId = sId,
                                        courseId = cId,
                                        dayOfWeek = fs.optInt("dayOfWeek", 1).coerceIn(1, 7),
                                        startTime = fs.optNonNullString("startTime", "08:00").ifBlank { "08:00" },
                                        endTime = fs.optNonNullString("endTime", "09:40").ifBlank { "09:40" },
                                        shortLocation = fs.optNonNullString("shortLocation", "UFOP").ifBlank { "UFOP" }
                                    )
                                )
                            }
                        }
                    }
                    loadedFriends.add(
                        FriendScheduleEntry(
                            id = id,
                            friendName = friendName,
                            courseName = friendCourseName,
                            semester = semester,
                            avatarBase64 = friendAvatarBase64,
                            courses = fCourses,
                            slots = fSlots
                        )
                    )
                }
            }
        }

        val loadedWeather = mutableMapOf<String, CampusWeather>()
        runCatching {
            val wObj = root.optJSONObject("campusWeather") ?: return@runCatching
            val keys = wObj.keys()
            while (keys.hasNext()) {
                runCatching {
                    val cId = keys.next()
                    val cw = wObj.optJSONObject(cId) ?: return@runCatching
                    if (cw.has("tempC")) {
                        loadedWeather[cId] = CampusWeather(
                            tempC = cw.optInt("tempC", 22),
                            condition = cw.optNonNullString("condition", "Ensolarado").ifBlank { "Ensolarado" }
                        )
                    }
                }
            }
        }

        val loadedCalendarBySemester = linkedMapOf<String, MutableList<AcademicCalendarEvent>>()
        runCatching {
            val calObj = root.optJSONObject("calendarEventsBySemester") ?: return@runCatching
            val semKeys = calObj.keys()
            while (semKeys.hasNext()) {
                runCatching {
                    val semKey = semKeys.next()
                    val arr = calObj.optJSONArray(semKey) ?: return@runCatching
                    val list = mutableListOf<AcademicCalendarEvent>()
                    for (i in 0 until arr.length()) {
                        runCatching {
                            val evObj = arr.optJSONObject(i) ?: return@runCatching
                            val evId = evObj.optNonNullString("id").trim()
                            val evDate = evObj.optNonNullString("eventDate").trim()
                            val evTitle = evObj.optNonNullString("title").trim()
                            if (evId.isEmpty() || evDate.isEmpty() || evTitle.isEmpty()) return@runCatching
                            val (_, parsedDay, parsedMonth) = parseDueDateParts(evDate)
                            list.add(
                                AcademicCalendarEvent(
                                    id = evId,
                                    semester = evObj.optNonNullString("semester", semKey).ifBlank { semKey },
                                    eventDate = evDate,
                                    dayNumber = evObj.optNonNullString("dayNumber", parsedDay).ifBlank { parsedDay },
                                    monthShort = evObj.optNonNullString("monthShort", parsedMonth).ifBlank { parsedMonth },
                                    title = evTitle,
                                    category = evObj.optNonNullString("category", "Acadêmico").ifBlank { "Acadêmico" }
                                )
                            )
                        }
                    }
                    if (list.isNotEmpty()) {
                        loadedCalendarBySemester[semKey] = list
                    }
                }
            }
        }
        val loadedAdminUnlocked = runCatching { root.optBoolean("adminUnlocked", false) }.getOrDefault(false)
        val loadedPendingCal = runCatching { parseCalendarPackageFromJson(root.optJSONObject("pendingCalendarUpdate")) }.getOrNull()
        val loadedLatestCal = runCatching { parseCalendarPackageFromJson(root.optJSONObject("latestPublishedCalendarUpdate")) }.getOrNull()

        val legacySeedIds = setOf(
            "cal_2026_2_trancamento",
            "cal_2026_2_autonomos",
            "cal_2026_2_encerramento",
            "cal_2026_2_exames",
            "cal_2026_2_estudos_autonomos",
            "cal_2026_2_exames_especiais"
        )
        val officialSeedBySemester = calendarEventsSeed.groupBy { it.semester }
        val existing20262 = loadedCalendarBySemester["2026.2"]
        if (!existing20262.isNullOrEmpty() &&
            existing20262.all { it.id in legacySeedIds } &&
            loadedLatestCal?.semester != "2026.2"
        ) {
            officialSeedBySemester["2026.2"]?.let { seedList ->
                loadedCalendarBySemester["2026.2"] = seedList.toMutableList()
            }
        }
        officialSeedBySemester.forEach { (sem, list) ->
            if (loadedCalendarBySemester[sem].isNullOrEmpty()) {
                loadedCalendarBySemester[sem] = list.toMutableList()
            }
        }

        val loadedTotalRequiredHours = runCatching { root.optInt("totalRequiredHours", 3610) }
            .getOrDefault(3610)
            .coerceAtLeast(1)
        val loadedImportedCompletedHours = runCatching { root.optInt("importedCompletedHours", 0) }
            .getOrDefault(0)
            .coerceAtLeast(0)
        val loadedTranscriptEntries = mutableListOf<TranscriptCourseEntry>()
        runCatching {
            val txArr = root.optJSONArray("transcriptEntries") ?: return@runCatching
            for (i in 0 until txArr.length()) {
                runCatching {
                    val txObj = txArr.optJSONObject(i) ?: return@runCatching
                    val txId = txObj.optNonNullString("id").trim()
                    val txSem = txObj.optNonNullString("semester").trim()
                    val txCode = txObj.optNonNullString("code").trim().uppercase()
                    val rawTxName = txObj.optNonNullString("name").trim()
                    if (txId.isEmpty() || txSem.isEmpty() || txCode.isEmpty() || rawTxName.isEmpty()) return@runCatching
                    val txGrade = if (txObj.isNull("grade") || !txObj.has("grade")) {
                        null
                    } else {
                        txObj.optDouble("grade").takeIf { it.isFinite() }?.coerceIn(0.0, 10.0)
                    }
                    val rawProf = txObj.optNonNullString("professor").trim()
                    loadedTranscriptEntries.add(
                        TranscriptCourseEntry(
                            id = txId,
                            semester = txSem,
                            code = txCode,
                            name = normalizeUfopPortugueseText(rawTxName),
                            workloadHours = txObj.optInt("workloadHours", 60).coerceAtLeast(1),
                            grade = txGrade,
                            absences = txObj.optInt("absences", 0).coerceAtLeast(0),
                            totalClasses = txObj.optInt("totalClasses", 72).coerceAtLeast(0),
                            status = txObj.optNonNullString("status", "CS").ifBlank { "CS" }.uppercase(),
                            professor = if (rawProf.isEmpty()) "" else normalizeUfopPortugueseText(rawProf)
                        )
                    )
                }
            }
        }

        val loadedGuidedTourCompleted = runCatching {
            root.optBoolean("guidedTourCompleted", false)
        }.getOrDefault(false)
        val loadedLastSeenReleaseVersion = runCatching {
            root.optNonNullString("lastSeenReleaseVersion", "").trim()
        }.getOrDefault("")
        val loadedAccessibilitySettings = runCatching {
            val a11yObj = root.optJSONObject("accessibilitySettings")
            if (a11yObj != null) {
                val rawScale = a11yObj.optDouble("fontScaleMultiplier", 1.0).toFloat()
                val safeScale = if (rawScale.isFinite()) rawScale.coerceIn(0.85f, 2.0f) else 1.0f
                CampusAccessibilitySettings(
                    screenReaderOptimized = a11yObj.optBoolean("screenReaderOptimized", false),
                    fontScaleMultiplier = safeScale,
                    highContrastMode = a11yObj.optBoolean("highContrastMode", false),
                    reduceMotion = a11yObj.optBoolean("reduceMotion", false),
                    largeTouchTargets = a11yObj.optBoolean("largeTouchTargets", false),
                    boldTextBoost = a11yObj.optBoolean("boldTextBoost", false)
                )
            } else {
                CampusAccessibilitySettings()
            }
        }.getOrDefault(CampusAccessibilitySettings())

        courses.clear()
        loadedCourses.forEach { (id, seed) ->
            courses[id] = seed.copy(assessments = loadedAssessmentsByCourse[id].orEmpty().toList())
        }
        slots.clear()
        slots.addAll(loadedSlots)
        commitments.clear()
        commitments.addAll(loadedCommitments)
        courseColorOverrides.clear()
        departmentColorOverrides.clear()
        departmentColorOverrides.putAll(loadedDeptColors)
        checkInStatusBySessionKey.clear()
        checkInStatusBySessionKey.putAll(loadedSessions)
        notesByCourseId.clear()
        notesByCourseId.putAll(loadedNotes)
        assessmentsByCourseId.clear()
        assessmentsByCourseId.putAll(loadedAssessmentsByCourse)
        friendSchedules.clear()
        friendSchedules.addAll(loadedFriends)
        campusWeatherById.clear()
        campusWeatherById.putAll(loadedWeather)
        calendarEventsBySemester.clear()
        calendarEventsBySemester.putAll(loadedCalendarBySemester)
        adminUnlocked = loadedAdminUnlocked
        pendingCalendarUpdate = loadedPendingCal
        latestPublishedCalendarUpdate = loadedLatestCal
        transcriptEntries.clear()
        transcriptEntries.addAll(loadedTranscriptEntries)
        totalRequiredHours = loadedTotalRequiredHours
        importedCompletedHours = loadedImportedCompletedHours
        studentProfile = loadedProfile
        activeCampusId = resolvedCampusId
        generatedIdCounter = resolvedCounter
        lastWeatherFetchEpochMs = loadedWeatherFetchMs
        darkThemePreference = loadedDarkPref
        accentColorPreference = loadedAccentColor
        exportFolderTreeUri = loadedExportUri
        exportFolderDisplayLabel = loadedExportLabel
        accessibilitySettings = loadedAccessibilitySettings
        guidedTourCompleted = loadedGuidedTourCompleted
        lastSeenReleaseVersion = loadedLastSeenReleaseVersion
        roomAllocationIndex?.let { index ->
            enrichExistingRoomAllocationsLocked(index)
        }
    }

    private fun buildPersistedJsonLocked(): JSONObject {
        val root = JSONObject()
        root.put("schemaVersion", PERSISTED_SCHEMA_VERSION)
        root.put("activeCampusId", activeCampusId)
        root.put("generatedIdCounter", generatedIdCounter)
        root.put("lastWeatherFetchEpochMs", lastWeatherFetchEpochMs)
        root.put("darkThemePreference", darkThemePreference)
        root.put("accentColorPreference", accentColorPreference)
        root.put("accent_color_key", accentColorPreference)
        if (exportFolderTreeUri != null) {
            root.put("exportFolderTreeUri", exportFolderTreeUri)
        } else {
            root.put("exportFolderTreeUri", JSONObject.NULL)
        }
        root.put("exportFolderDisplayLabel", exportFolderDisplayLabel)

        val profileJson = JSONObject().apply {
            put("fullName", studentProfile.fullName)
            put("registrationNumber", studentProfile.registrationNumber)
            put("courseName", studentProfile.courseName)
            put("campusName", studentProfile.campusName)
            put("activeSemester", studentProfile.activeSemester)
            put("semesterStartDate", studentProfile.semesterStartDate)
            put("semesterEndDate", studentProfile.semesterEndDate)
            if (studentProfile.avatarPath != null) {
                put("avatarPath", studentProfile.avatarPath)
            }
            put("onboardingCompleted", studentProfile.onboardingCompleted)
        }
        root.put("studentProfile", profileJson)

        val weatherJson = JSONObject()
        campusWeatherById.forEach { (cId, cw) ->
            weatherJson.put(cId, JSONObject().apply {
                put("tempC", cw.tempC)
                put("condition", cw.condition)
            })
        }
        root.put("campusWeather", weatherJson)

        val deptColorsJson = JSONObject()
        departmentColorOverrides.forEach { (deptId, colorHex) ->
            deptColorsJson.put(deptId, colorHex)
        }
        root.put("departmentColorOverrides", deptColorsJson)

        val coursesArr = JSONArray()
        courses.values.forEach { course ->
            val obj = JSONObject()
            obj.put("id", course.courseId)
            obj.put("courseId", course.courseId)
            obj.put("code", resolveCourseCode(course))
            obj.put("semester", course.semester)
            obj.put("departmentId", course.departmentId)
            obj.put("departmentName", resolveDepartmentName(course))
            obj.put("colorOverrideHex", resolveCourseColor(course))
            obj.put("defaultColorHex", resolveCourseColor(course))
            obj.put("name", course.name)
            obj.put("professor", course.professor)
            obj.put("workloadHours", course.workloadHours)
            obj.put("maxAbsences", course.maxAbsences)
            obj.put("manualAbsences", course.baseAbsences)
            obj.put("baseCancelledClasses", course.baseCancelledClasses)
            obj.put("buildingAndRoom", course.buildingAndRoom)
            obj.put("shortLocation", course.shortLocation)
            obj.put("notes", notesByCourseId[course.courseId] ?: course.initialNotes)
            coursesArr.put(obj)
        }
        root.put("courses", coursesArr)

        val slotsArr = JSONArray()
        slots.forEach { slot ->
            val obj = JSONObject()
            obj.put("slotId", slot.slotId)
            obj.put("courseId", slot.courseId)
            obj.put("dayOfWeek", slot.dayOfWeek)
            obj.put("startTime", slot.startTime)
            obj.put("endTime", slot.endTime)
            obj.put("building", slot.building)
            obj.put("room", slot.room)
            obj.put("shortLocation", slot.shortLocation)
            obj.put("defaultTempC", slot.defaultTempC)
            obj.put("weatherCondition", slot.weatherCondition)
            obj.put("classWeight", slot.classWeight)
            slotsArr.put(obj)
        }
        root.put("slots", slotsArr)

        val sessionsArr = JSONArray()
        checkInStatusBySessionKey.forEach { (key, status) ->
            val separator = key.lastIndexOf('_')
            if (separator > 0) {
                val slotId = key.substring(0, separator)
                val sessionDate = key.substring(separator + 1)
                val slot = slots.find { it.slotId == slotId }
                val obj = JSONObject()
                obj.put("slotId", slotId)
                obj.put("courseId", slot?.courseId ?: "")
                obj.put("sessionDate", sessionDate)
                obj.put("status", status.name)
                obj.put("weatherTempC", slot?.defaultTempC ?: 22)
                obj.put("weatherCondition", slot?.weatherCondition ?: "Ensolarado")
                obj.put("classWeight", if (status == AttendanceStatus.ABSENT) (slot?.classWeight ?: 1) else 0)
                obj.put("startTime", slot?.startTime ?: "08:00")
                obj.put("endTime", slot?.endTime ?: "09:40")
                sessionsArr.put(obj)
            }
        }
        root.put("sessions", sessionsArr)

        val commitmentsArr = JSONArray()
        commitments.forEach { comm ->
            val obj = JSONObject()
            obj.put("id", comm.id)
            obj.put("isoDate", comm.isoDate)
            obj.put("title", comm.title)
            obj.put("startTime", comm.startTime)
            obj.put("endTime", comm.endTime)
            obj.put("location", comm.location)
            obj.put("subtitle", comm.subtitle)
            obj.put("colorHex", comm.colorHex)
            obj.put("weatherTempC", comm.weatherTempC)
            commitmentsArr.put(obj)
        }
        root.put("commitments", commitmentsArr)

        val assessmentsArr = JSONArray()
        assessmentsByCourseId.values.forEach { list ->
            list.forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("courseId", item.courseId)
                obj.put("dayNumber", item.dayNumber)
                obj.put("monthShort", item.monthShort)
                obj.put("title", item.title)
                obj.put("kind", item.kind)
                obj.put("dueTime", item.dueTime)
                obj.put("dueDate", item.dueDate)
                obj.put("completed", item.completed)
                assessmentsArr.put(obj)
            }
        }
        root.put("assessments", assessmentsArr)

        val friendsArr = JSONArray()
        friendSchedules.forEach { friend ->
            val fObj = JSONObject()
            fObj.put("id", friend.id)
            fObj.put("friendName", friend.friendName)
            fObj.put("courseName", friend.courseName)
            fObj.put("semester", friend.semester)
            if (!friend.avatarBase64.isNullOrBlank()) {
                fObj.put("avatarBase64", friend.avatarBase64)
            }
            val fcArr = JSONArray()
            friend.courses.forEach { fc ->
                fcArr.put(JSONObject().apply {
                    put("courseId", fc.courseId)
                    put("code", deriveCourseCodeFallback(fc.code, "", fc.courseId, fc.name))
                    put("name", fc.name)
                    put("departmentId", fc.departmentId)
                })
            }
            fObj.put("courses", fcArr)
            val fsArr = JSONArray()
            friend.slots.forEach { fs ->
                fsArr.put(JSONObject().apply {
                    put("slotId", fs.slotId)
                    put("courseId", fs.courseId)
                    put("dayOfWeek", fs.dayOfWeek)
                    put("startTime", fs.startTime)
                    put("endTime", fs.endTime)
                    put("shortLocation", fs.shortLocation)
                })
            }
            fObj.put("slots", fsArr)
            friendsArr.put(fObj)
        }
        root.put("friendSchedules", friendsArr)

        val calEventsJson = JSONObject()
        calendarEventsBySemester.forEach { (sem, list) ->
            val arr = JSONArray()
            list.forEach { ev ->
                arr.put(JSONObject().apply {
                    put("id", ev.id)
                    put("semester", ev.semester)
                    put("eventDate", ev.eventDate)
                    put("dayNumber", ev.dayNumber)
                    put("monthShort", ev.monthShort)
                    put("title", ev.title)
                    put("category", ev.category)
                })
            }
            calEventsJson.put(sem, arr)
        }
        root.put("calendarEventsBySemester", calEventsJson)
        root.put("adminUnlocked", adminUnlocked)
        pendingCalendarUpdate?.let { root.put("pendingCalendarUpdate", calendarPackageToJsonLocked(it)) }
        latestPublishedCalendarUpdate?.let { root.put("latestPublishedCalendarUpdate", calendarPackageToJsonLocked(it)) }

        root.put("totalRequiredHours", totalRequiredHours)
        root.put("importedCompletedHours", importedCompletedHours)
        val txArr = JSONArray()
        transcriptEntries.forEach { entry ->
            txArr.put(JSONObject().apply {
                put("id", entry.id)
                put("semester", entry.semester)
                put("code", entry.code)
                put("name", entry.name)
                put("workloadHours", entry.workloadHours)
                if (entry.grade != null) {
                    put("grade", entry.grade)
                } else {
                    put("grade", JSONObject.NULL)
                }
                put("absences", entry.absences)
                put("totalClasses", entry.totalClasses)
                put("status", entry.status)
                put("professor", entry.professor)
            })
        }
        root.put("transcriptEntries", txArr)

        root.put("guidedTourCompleted", guidedTourCompleted)
        root.put("lastSeenReleaseVersion", lastSeenReleaseVersion)
        root.put(
            "accessibilitySettings",
            JSONObject().apply {
                put("screenReaderOptimized", accessibilitySettings.screenReaderOptimized)
                put("fontScaleMultiplier", accessibilitySettings.fontScaleMultiplier.toDouble())
                put("highContrastMode", accessibilitySettings.highContrastMode)
                put("reduceMotion", accessibilitySettings.reduceMotion)
                put("largeTouchTargets", accessibilitySettings.largeTouchTargets)
                put("boldTextBoost", accessibilitySettings.boldTextBoost)
            }
        )

        return root
    }

    private fun persistStateLocked(encryptExistingPlaintextBackup: Boolean = false) {
        val targetFile = stateFile ?: return
        var persisted = false
        try {
            val parentDir = targetFile.absoluteFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }
            val plaintextJson = buildPersistedJsonLocked().toString()
            val encryptedPayload = CampusCryptoEngine.encryptStatePayload(plaintextJson)
            val payloadBytes = encryptedPayload.toByteArray(Charsets.UTF_8)
            val tempFile = File(parentDir, "${targetFile.name}.tmp")
            val backupFile = File(parentDir, "${targetFile.name}.bak")

            FileOutputStream(tempFile).use { out ->
                out.write(payloadBytes)
                out.flush()
                runCatching { out.fd.sync() }
            }

            if (targetFile.exists() && targetFile.length() > 2L) {
                var backupWritten = false
                if (encryptExistingPlaintextBackup) {
                    backupWritten = runCatching {
                        val prevRaw = targetFile.readText(Charsets.UTF_8)
                        val prevDecrypted = CampusCryptoEngine.decryptStatePayloadIfNeeded(prevRaw)
                        val encryptedBackupBytes = CampusCryptoEngine
                            .encryptStatePayload(prevDecrypted)
                            .toByteArray(Charsets.UTF_8)
                        FileOutputStream(backupFile).use { out ->
                            out.write(encryptedBackupBytes)
                            out.flush()
                            runCatching { out.fd.sync() }
                        }
                        true
                    }.getOrDefault(false)
                }
                if (!backupWritten) {
                    runCatching {
                        Files.copy(
                            targetFile.toPath(),
                            backupFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }.onFailure {
                        runCatching {
                            targetFile.inputStream().use { input ->
                                FileOutputStream(backupFile).use { out ->
                                    input.copyTo(out)
                                    out.flush()
                                    runCatching { out.fd.sync() }
                                }
                            }
                        }
                    }
                }
            } else if (encryptExistingPlaintextBackup && backupFile.exists()) {
                runCatching {
                    val prevBakRaw = backupFile.readText(Charsets.UTF_8)
                    val prevBakDecrypted = CampusCryptoEngine.decryptStatePayloadIfNeeded(prevBakRaw)
                    if (prevBakRaw == prevBakDecrypted) {
                        val encryptedBakBytes = CampusCryptoEngine
                            .encryptStatePayload(prevBakDecrypted)
                            .toByteArray(Charsets.UTF_8)
                        FileOutputStream(backupFile).use { out ->
                            out.write(encryptedBakBytes)
                            out.flush()
                            runCatching { out.fd.sync() }
                        }
                    }
                }
            }

            val moved = runCatching {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
                true
            }.recoverCatching {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
                true
            }.getOrDefault(false)

            if (!moved) {
                FileOutputStream(targetFile).use { out ->
                    out.write(payloadBytes)
                    out.flush()
                    runCatching { out.fd.sync() }
                }
                runCatching { tempFile.delete() }
            }
            persisted = true
        } catch (_: Throwable) {
            // Persistência best-effort: mantém estado em memória se I/O falhar
        }
        if (persisted) {
            runCatching { onStatePersisted?.invoke() }
        }
    }
}

/**
 * Bridge FFI / Stateful Engine Repository para o CAMPUS (UFOP).
 * Instancia o [br.ufop.campus.core.uniffi.CampusEngine] nativo via UniFFI/JNA quando a biblioteca
 * `campus_core` está disponível no runtime e cai automaticamente para o [InMemoryCampusRepository]
 * quando executada sem biblioteca nativa (ex.: Compose Previews ou testes unitários JVM).
 */
class CampusEngineBridgeRepository(
    private val dbPath: String? = null,
    private val stateFile: File? = null,
    private val onStatePersisted: (() -> Unit)? = null
) : CampusCoreRepository {

    private val lock = Any()
    private val _mutationVersion = MutableStateFlow(0L)
    override val mutationVersion: StateFlow<Long> = _mutationVersion.asStateFlow()

    private var uniffiEngine: br.ufop.campus.core.uniffi.CampusEngine? = try {
        if (dbPath != null) {
            br.ufop.campus.core.uniffi.CampusEngine.newEmpty(dbPath)
        } else {
            br.ufop.campus.core.uniffi.CampusEngine.newInMemoryEmpty()
        }
    } catch (_: Throwable) {
        null
    }

    private val fallback = InMemoryCampusRepository(
        stateFile = stateFile,
        onMutation = { _mutationVersion.value += 1L },
        onStatePersisted = onStatePersisted
    )

    @Volatile
    private var roomAllocationIndex: UfopRoomAllocationIndex? = null

    val isNativeCoreActive: Boolean
        get() = uniffiEngine != null

    private inline fun <T> readOp(
        nativeBlock: (br.ufop.campus.core.uniffi.CampusEngine) -> T,
        fallbackBlock: () -> T
    ): T = synchronized(lock) {
        val engine = uniffiEngine
        if (engine != null) {
            try {
                nativeBlock(engine)
            } catch (err: br.ufop.campus.core.uniffi.CampusException) {
                throw mapCampusException(err)
            }
        } else {
            fallbackBlock()
        }
    }

    private inline fun <T> writeOp(
        nativeBlock: (br.ufop.campus.core.uniffi.CampusEngine) -> T,
        fallbackBlock: () -> T
    ): T = synchronized(lock) {
        val engine = uniffiEngine
        val result = if (engine != null) {
            try {
                nativeBlock(engine)
            } catch (err: br.ufop.campus.core.uniffi.CampusException) {
                throw mapCampusException(err)
            }
        } else {
            fallbackBlock()
        }
        _mutationVersion.value += 1L
        if (engine != null) {
            runCatching { onStatePersisted?.invoke() }
        }
        result
    }

    override fun getAgendaDay(isoDate: String): AgendaDayOverview = readOp(
        nativeBlock = { it.getAgendaDay(isoDate).toFfi() },
        fallbackBlock = { fallback.getAgendaDay(isoDate) }
    )

    override fun getClassCheckIn(slotId: String, isoDate: String): ClassCheckInDetail = readOp(
        nativeBlock = { it.getClassCheckIn(slotId, isoDate).toFfi() },
        fallbackBlock = { fallback.getClassCheckIn(slotId, isoDate) }
    )

    override fun saveClassCheckIn(
        slotId: String,
        isoDate: String,
        status: AttendanceStatus,
        colorOverrideHex: String?
    ): DisciplineSummaryCard = writeOp(
        nativeBlock = { it.saveClassCheckIn(slotId, isoDate, status.toUniffi(), null).toFfi() },
        fallbackBlock = { fallback.saveClassCheckIn(slotId, isoDate, status, null) }
    )

    override fun listDepartments(): List<DepartmentColorOption> = readOp(
        nativeBlock = { engine -> engine.listDepartments().map { it.toFfi() } },
        fallbackBlock = { fallback.listDepartments() }
    )

    override fun listDisciplines(
        semester: String,
        departmentId: String?
    ): List<DisciplineSummaryCard> = readOp(
        nativeBlock = { engine -> engine.listDisciplines(semester, departmentId).map { it.toFfi() } },
        fallbackBlock = { fallback.listDisciplines(semester, departmentId) }
    )

    override fun getDisciplineDetail(courseId: String): DisciplineDetail = readOp(
        nativeBlock = { it.getDisciplineDetail(courseId).toFfi() },
        fallbackBlock = { fallback.getDisciplineDetail(courseId) }
    )

    override fun updateDisciplineNotes(courseId: String, notes: String): Unit = writeOp(
        nativeBlock = { it.updateDisciplineNotes(courseId, notes); Unit },
        fallbackBlock = { fallback.updateDisciplineNotes(courseId, notes) }
    )

    override fun getWeeklyTimetable(semester: String): WeeklyTimetable = readOp(
        nativeBlock = { it.getWeeklyTimetable(semester).toFfi() },
        fallbackBlock = { fallback.getWeeklyTimetable(semester) }
    )

    override fun createCommitment(
        title: String,
        description: String,
        commitmentDate: String,
        startTime: String,
        endTime: String,
        location: String,
        weatherTempC: Int?
    ): AgendaTimelineItem = writeOp(
        nativeBlock = {
            it.createCommitment(
                title = title,
                description = description,
                commitmentDate = commitmentDate,
                startTime = startTime,
                endTime = endTime,
                location = location,
                weatherTempC = weatherTempC
            ).toFfi()
        },
        fallbackBlock = {
            fallback.createCommitment(
                title = title,
                description = description,
                commitmentDate = commitmentDate,
                startTime = startTime,
                endTime = endTime,
                location = location,
                weatherTempC = weatherTempC
            )
        }
    )

    override fun deleteCommitment(commitmentId: String): Unit = writeOp(
        nativeBlock = { it.deleteCommitment(commitmentId) },
        fallbackBlock = { fallback.deleteCommitment(commitmentId) }
    )

    override fun createAssessment(
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String
    ): AssessmentItem = writeOp(
        nativeBlock = { it.createAssessment(courseId, title, kind, dueDate, dueTime).toFfi() },
        fallbackBlock = { fallback.createAssessment(courseId, title, kind, dueDate, dueTime) }
    )

    override fun toggleAssessmentCompleted(
        assessmentId: String,
        completed: Boolean
    ): AssessmentItem = writeOp(
        nativeBlock = { it.toggleAssessmentCompleted(assessmentId, completed).toFfi() },
        fallbackBlock = { fallback.toggleAssessmentCompleted(assessmentId, completed) }
    )

    override fun deleteAssessment(assessmentId: String): Unit = writeOp(
        nativeBlock = { it.deleteAssessment(assessmentId) },
        fallbackBlock = { fallback.deleteAssessment(assessmentId) }
    )

    override fun listAllAssessments(
        semester: String,
        kindFilter: String?
    ): List<AssessmentWithCourse> = readOp(
        nativeBlock = { engine -> engine.listAllAssessments(semester, kindFilter).map { it.toFfi() } },
        fallbackBlock = { fallback.listAllAssessments(semester, kindFilter) }
    )

    override fun getAcademicProfileSummary(semester: String): AcademicProfileSummary = readOp(
        nativeBlock = { it.getAcademicProfileSummary(semester).toFfi() },
        fallbackBlock = { fallback.getAcademicProfileSummary(semester) }
    )

    override fun setActiveCampus(campusId: String): UfopCampusInfo = writeOp(
        nativeBlock = { it.setActiveCampus(campusId).toFfi() },
        fallbackBlock = { fallback.setActiveCampus(campusId) }
    )

    override fun exportSchedulePayload(semester: String): String = readOp(
        nativeBlock = { it.exportSchedulePayload(semester) },
        fallbackBlock = { fallback.exportSchedulePayload(semester) }
    )

    override fun importSchedulePayload(jsonPayload: String): Int = writeOp(
        nativeBlock = { it.importSchedulePayload(jsonPayload).toInt() },
        fallbackBlock = { fallback.importSchedulePayload(jsonPayload) }
    )

    override fun createCourse(
        semester: String,
        departmentId: String,
        name: String,
        professor: String,
        workloadHours: Int,
        colorOverrideHex: String?
    ): DisciplineSummaryCard = writeOp(
        nativeBlock = {
            it.createCourse(
                semester = semester,
                departmentId = departmentId,
                name = name,
                professor = professor,
                workloadHours = workloadHours.coerceAtLeast(0).toUInt(),
                colorOverrideHex = null
            ).toFfi()
        },
        fallbackBlock = {
            fallback.createCourse(
                semester = semester,
                departmentId = departmentId,
                name = name,
                professor = professor,
                workloadHours = workloadHours,
                colorOverrideHex = null
            )
        }
    )

    override fun deleteCourse(courseId: String): Unit = writeOp(
        nativeBlock = { it.deleteCourse(courseId) },
        fallbackBlock = { fallback.deleteCourse(courseId) }
    )

    override fun addScheduleSlot(
        courseId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ): CourseScheduleSlotItem = writeOp(
        nativeBlock = {
            it.addScheduleSlot(
                courseId = courseId,
                dayOfWeek = dayOfWeek.coerceAtLeast(0).toUInt(),
                startTime = startTime,
                endTime = endTime,
                building = building,
                room = room,
                shortLocation = shortLocation
            ).toFfi()
        },
        fallbackBlock = {
            fallback.addScheduleSlot(
                courseId = courseId,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                building = building,
                room = room,
                shortLocation = shortLocation
            )
        }
    )

    override fun deleteScheduleSlot(slotId: String): Unit = writeOp(
        nativeBlock = { it.deleteScheduleSlot(slotId) },
        fallbackBlock = { fallback.deleteScheduleSlot(slotId) }
    )

    override fun listCourseSlots(courseId: String): List<CourseScheduleSlotItem> = readOp(
        nativeBlock = { engine -> engine.listCourseSlots(courseId).map { it.toFfi() } },
        fallbackBlock = { fallback.listCourseSlots(courseId) }
    )

    override fun listUfopTemplates(): List<UfopCurriculumTemplate> = readOp(
        nativeBlock = { engine -> engine.listUfopTemplates().map { it.toFfi() } },
        fallbackBlock = { fallback.listUfopTemplates() }
    )

    override fun applyUfopTemplate(templateId: String, semester: String): Int = writeOp(
        nativeBlock = { it.applyUfopTemplate(templateId, semester).toInt() },
        fallbackBlock = { fallback.applyUfopTemplate(templateId, semester) }
    )

    override fun listAbsenceHistory(courseId: String): List<AbsenceHistoryEntry> = readOp(
        nativeBlock = { engine -> engine.listAbsenceHistory(courseId).map { it.toFfi() } },
        fallbackBlock = { fallback.listAbsenceHistory(courseId) }
    )

    override fun searchCampusItems(semester: String, query: String): CampusSearchResults = readOp(
        nativeBlock = { it.searchCampusItems(semester, query).toFfi() },
        fallbackBlock = { fallback.searchCampusItems(semester, query) }
    )

    override fun resetSemesterToDefaultSeed(semester: String): Int = writeOp(
        nativeBlock = { it.resetSemesterToDefaultSeed(semester).toInt() },
        fallbackBlock = { fallback.resetSemesterToDefaultSeed(semester) }
    )

    // Sprint 6 APIs
    override fun getStudentProfile(): StudentProfile = synchronized(lock) {
        fallback.getStudentProfile()
    }

    override fun saveStudentProfile(profile: StudentProfile): StudentProfile = synchronized(lock) {
        val saved = fallback.saveStudentProfile(profile)
        _mutationVersion.value += 1L
        saved
    }

    override fun updateCourse(
        courseId: String,
        name: String,
        professor: String,
        departmentId: String,
        workloadHours: Int
    ): DisciplineSummaryCard = writeOp(
        nativeBlock = {
            it.updateCourse(
                courseId = courseId,
                name = name,
                professor = professor,
                departmentId = departmentId,
                workloadHours = workloadHours.coerceAtLeast(0).toUInt()
            ).toFfi()
        },
        fallbackBlock = {
            fallback.updateCourse(
                courseId = courseId,
                name = name,
                professor = professor,
                departmentId = departmentId,
                workloadHours = workloadHours
            )
        }
    )

    override fun updateScheduleSlot(
        slotId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ): CourseScheduleSlotItem = writeOp(
        nativeBlock = {
            it.updateScheduleSlot(
                slotId = slotId,
                dayOfWeek = dayOfWeek.coerceAtLeast(0).toUInt(),
                startTime = startTime,
                endTime = endTime,
                building = building,
                room = room,
                shortLocation = shortLocation
            ).toFfi()
        },
        fallbackBlock = {
            fallback.updateScheduleSlot(
                slotId = slotId,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                building = building,
                room = room,
                shortLocation = shortLocation
            )
        }
    )

    override fun updateCommitment(
        commitmentId: String,
        title: String,
        description: String,
        commitmentDate: String,
        startTime: String,
        endTime: String,
        location: String
    ): AgendaTimelineItem = writeOp(
        nativeBlock = {
            it.updateCommitment(
                commitmentId = commitmentId,
                title = title,
                description = description,
                commitmentDate = commitmentDate,
                startTime = startTime,
                endTime = endTime,
                location = location
            ).toFfi()
        },
        fallbackBlock = {
            fallback.updateCommitment(
                commitmentId = commitmentId,
                title = title,
                description = description,
                commitmentDate = commitmentDate,
                startTime = startTime,
                endTime = endTime,
                location = location
            )
        }
    )

    override fun getMonthActivityIndicators(
        year: Int,
        month: Int,
        semester: String
    ): Map<Int, DayActivityIndicator> = synchronized(lock) {
        fallback.getMonthActivityIndicators(year, month, semester)
    }

    override fun listAvailableSemesters(): List<String> = synchronized(lock) {
        fallback.listAvailableSemesters()
    }

    override fun listAvailableCalendarSemesters(): List<String> = synchronized(lock) {
        fallback.listAvailableCalendarSemesters()
    }

    override fun listFriendSchedules(): List<FriendScheduleInfo> = synchronized(lock) {
        fallback.listFriendSchedules()
    }

    override fun importFriendScheduleJson(
        friendName: String,
        jsonPayload: String
    ): FriendScheduleInfo = synchronized(lock) {
        val result = fallback.importFriendScheduleJson(friendName, jsonPayload)
        _mutationVersion.value += 1L
        result
    }

    override fun removeFriendSchedule(friendId: String): Unit = synchronized(lock) {
        fallback.removeFriendSchedule(friendId)
        _mutationVersion.value += 1L
    }

    override fun getFriendTimetable(friendId: String): WeeklyTimetable? = synchronized(lock) {
        fallback.getFriendTimetable(friendId)
    }

    override fun refreshWeatherIfNeeded(force: Boolean): UfopCampusInfo =
        fallback.refreshWeatherIfNeeded(force)

    override fun importEnrollmentCertificatePdf(pdfBytes: ByteArray, completeOnboarding: Boolean): Int = synchronized(lock) {
        val count = fallback.importEnrollmentCertificatePdf(pdfBytes, completeOnboarding)
        _mutationVersion.value += 1L
        count
    }

    override fun importEnrollmentCertificateText(rawText: String, completeOnboarding: Boolean): Int = synchronized(lock) {
        val count = fallback.importEnrollmentCertificateText(rawText, completeOnboarding)
        _mutationVersion.value += 1L
        count
    }

    // Sprint 7 APIs
    override fun listUfopBuildings(campusId: String?): List<UfopBuildingOption> = synchronized(lock) {
        fallback.listUfopBuildings(campusId)
    }

    override fun importCourseSyllabusPdf(courseId: String, pdfBytes: ByteArray): Int = synchronized(lock) {
        val count = fallback.importCourseSyllabusPdf(courseId, pdfBytes)
        _mutationVersion.value += 1L
        count
    }

    override fun importCourseSyllabusText(courseId: String, rawText: String): Int = synchronized(lock) {
        val count = fallback.importCourseSyllabusText(courseId, rawText)
        _mutationVersion.value += 1L
        count
    }

    override fun importCourseSyllabusDetailedPdf(
        courseId: String,
        pdfBytes: ByteArray
    ): SyllabusImportReport = synchronized(lock) {
        val report = fallback.importCourseSyllabusDetailedPdf(courseId, pdfBytes)
        if (report.matchedCourse && (report.importedCount > 0 || report.extractedProfessor.isNotBlank())) {
            _mutationVersion.value += 1L
        }
        report
    }

    override fun importCourseSyllabusDetailedText(
        courseId: String,
        rawText: String
    ): SyllabusImportReport = synchronized(lock) {
        val report = fallback.importCourseSyllabusDetailedText(courseId, rawText)
        if (report.matchedCourse && (report.importedCount > 0 || report.extractedProfessor.isNotBlank())) {
            _mutationVersion.value += 1L
        }
        report
    }

    override fun getAcademicCalendarEvents(semester: String): List<AcademicCalendarEvent> = synchronized(lock) {
        fallback.getAcademicCalendarEvents(semester)
    }

    override fun isAdminUnlocked(): Boolean = synchronized(lock) {
        fallback.isAdminUnlocked()
    }

    override fun unlockAdminMode(pin: String): Boolean = synchronized(lock) {
        val unlocked = fallback.unlockAdminMode(pin)
        if (unlocked) {
            _mutationVersion.value += 1L
        }
        unlocked
    }

    override fun adminPublishCalendarPdf(
        pdfBytes: ByteArray,
        semester: String
    ): AcademicCalendarUpdatePackage = synchronized(lock) {
        val pkg = fallback.adminPublishCalendarPdf(pdfBytes, semester)
        _mutationVersion.value += 1L
        pkg
    }

    override fun exportCalendarUpdateJson(): String = synchronized(lock) {
        fallback.exportCalendarUpdateJson()
    }

    override fun importCalendarUpdateJson(jsonPayload: String): AcademicCalendarUpdatePackage = synchronized(lock) {
        val pkg = fallback.importCalendarUpdateJson(jsonPayload)
        _mutationVersion.value += 1L
        pkg
    }

    override fun getPendingCalendarUpdate(): AcademicCalendarUpdatePackage? = synchronized(lock) {
        fallback.getPendingCalendarUpdate()
    }

    override fun applyPendingCalendarUpdate(): Int = synchronized(lock) {
        val count = fallback.applyPendingCalendarUpdate()
        if (count > 0) {
            _mutationVersion.value += 1L
        }
        count
    }

    override fun exportMyGradeBundleJson(semester: String): String = synchronized(lock) {
        fallback.exportMyGradeBundleJson(semester)
    }

    // Sprint 8 APIs
    override fun listDepartmentsForSemester(semester: String): List<DepartmentColorOption> = synchronized(lock) {
        val engine = uniffiEngine
        if (engine != null) {
            val deptIds = try {
                engine.listDisciplines(semester, null).map { it.departmentId.lowercase() }.toSet()
            } catch (_: Throwable) {
                emptySet()
            }
            if (deptIds.isNotEmpty()) {
                fallback.listDepartments().filter { it.id in deptIds }
            } else {
                fallback.listDepartmentsForSemester(semester)
            }
        } else {
            fallback.listDepartmentsForSemester(semester)
        }
    }

    override fun getAcademicCreReport(): AcademicCreReport = synchronized(lock) {
        fallback.getAcademicCreReport()
    }

    override fun updateTranscriptOrCourseGrade(
        semester: String,
        codeOrCourseId: String,
        grade: Double?,
        absences: Int?
    ): AcademicCreReport = synchronized(lock) {
        val report = fallback.updateTranscriptOrCourseGrade(semester, codeOrCourseId, grade, absences)
        _mutationVersion.value += 1L
        report
    }

    override fun importAcademicTranscriptPdf(pdfBytes: ByteArray): AcademicCreReport = synchronized(lock) {
        val report = fallback.importAcademicTranscriptPdf(pdfBytes)
        _mutationVersion.value += 1L
        report
    }

    override fun importAcademicTranscriptText(rawText: String): AcademicCreReport = synchronized(lock) {
        val report = fallback.importAcademicTranscriptText(rawText)
        _mutationVersion.value += 1L
        report
    }

    // Sprint 9 APIs
    override fun updateCourseAbsences(
        courseId: String,
        totalAbsences: Int
    ): DisciplineSummaryCard = synchronized(lock) {
        val card = fallback.updateCourseAbsences(courseId, totalAbsences)
        _mutationVersion.value += 1L
        card
    }

    override fun getDarkThemePreference(): String = synchronized(lock) {
        fallback.getDarkThemePreference()
    }

    override fun setDarkThemePreference(mode: String): Unit = synchronized(lock) {
        fallback.setDarkThemePreference(mode)
        _mutationVersion.value += 1L
    }

    override fun getExportFolderConfig(): ExportFolderConfig = synchronized(lock) {
        fallback.getExportFolderConfig()
    }

    override fun setExportFolderConfig(
        treeUri: String?,
        displayLabel: String?
    ): ExportFolderConfig = synchronized(lock) {
        val config = fallback.setExportFolderConfig(treeUri, displayLabel)
        _mutationVersion.value += 1L
        config
    }

    // Sprint 10 APIs
    override fun updateAssessment(
        assessmentId: String,
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String,
        completed: Boolean
    ): AssessmentItem = synchronized(lock) {
        val updated = fallback.updateAssessment(
            assessmentId = assessmentId,
            courseId = courseId,
            title = title,
            kind = kind,
            dueDate = dueDate,
            dueTime = dueTime,
            completed = completed
        )
        _mutationVersion.value += 1L
        updated
    }

    override fun setTotalRequiredHours(hours: Int): Unit = synchronized(lock) {
        fallback.setTotalRequiredHours(hours)
        _mutationVersion.value += 1L
    }

    // Sprint 11 APIs
    override fun getAccentColorPreference(): String = synchronized(lock) {
        fallback.getAccentColorPreference()
    }

    override fun setAccentColorPreference(accentKey: String): Unit = synchronized(lock) {
        fallback.setAccentColorPreference(accentKey)
        _mutationVersion.value += 1L
    }

    // Sprint 12 APIs
    override fun getAccessibilitySettings(): CampusAccessibilitySettings = synchronized(lock) {
        fallback.getAccessibilitySettings()
    }

    override fun setAccessibilitySettings(settings: CampusAccessibilitySettings): Unit = synchronized(lock) {
        fallback.setAccessibilitySettings(settings)
        _mutationVersion.value += 1L
    }

    override fun isGuidedTourCompleted(): Boolean = synchronized(lock) {
        fallback.isGuidedTourCompleted()
    }

    override fun setGuidedTourCompleted(completed: Boolean): Unit = synchronized(lock) {
        fallback.setGuidedTourCompleted(completed)
        _mutationVersion.value += 1L
    }

    override fun getLastSeenReleaseVersion(): String = synchronized(lock) {
        fallback.getLastSeenReleaseVersion()
    }

    override fun setLastSeenReleaseVersion(version: String): Unit = synchronized(lock) {
        fallback.setLastSeenReleaseVersion(version)
        _mutationVersion.value += 1L
    }

    override fun exportFullAccountSnapshotJson(): String = synchronized(lock) {
        fallback.exportFullAccountSnapshotJson()
    }

    override fun restoreFullAccountSnapshotJson(rawJson: String): Boolean = synchronized(lock) {
        val restored = fallback.restoreFullAccountSnapshotJson(rawJson)
        if (restored) {
            _mutationVersion.value += 1L
        }
        restored
    }

    override fun wipeAllUserDataForLogout(): Unit = synchronized(lock) {
        fallback.wipeAllUserDataForLogout()
        val engine = uniffiEngine
        if (engine != null) {
            runCatching { engine.close() }
            uniffiEngine = null
        }
        dbPath?.let { path ->
            runCatching { File(path).delete() }
            runCatching { File("$path-wal").delete() }
            runCatching { File("$path-shm").delete() }
            runCatching { File("$path-journal").delete() }
        }
        stateFile?.parentFile?.let { dir ->
            runCatching { File(dir, "campus_ufop.db").delete() }
            runCatching { File(dir, "campus_ufop.db-wal").delete() }
            runCatching { File(dir, "campus_ufop.db-shm").delete() }
            runCatching { File(dir, "campus_ufop.db-journal").delete() }
        }
        _mutationVersion.value += 1L
        runCatching { onStatePersisted?.invoke() }
        Unit
    }

    // Sprint 14 APIs
    override fun applyRoomAllocationIndex(
        index: UfopRoomAllocationIndex,
        enrichExisting: Boolean
    ): Int = synchronized(lock) {
        roomAllocationIndex = index
        val count = fallback.applyRoomAllocationIndex(index, enrichExisting)
        if (count > 0) {
            _mutationVersion.value += 1L
        }
        count
    }

    private fun mapCampusException(
        err: br.ufop.campus.core.uniffi.CampusException
    ): RuntimeException = when (err) {
        is br.ufop.campus.core.uniffi.CampusException.InvalidInput ->
            IllegalArgumentException(err.message ?: "Entrada inválida.", err)
        is br.ufop.campus.core.uniffi.CampusException.NotFound ->
            IllegalArgumentException(err.message ?: "Registro não encontrado.", err)
        is br.ufop.campus.core.uniffi.CampusException.Database ->
            IllegalStateException(err.message ?: "Erro interno no banco de dados.", err)
    }
}

private fun AttendanceStatus.toUniffi(): br.ufop.campus.core.uniffi.AttendanceStatus = when (this) {
    AttendanceStatus.ATTENDING -> br.ufop.campus.core.uniffi.AttendanceStatus.ATTENDING
    AttendanceStatus.ABSENT -> br.ufop.campus.core.uniffi.AttendanceStatus.ABSENT
    AttendanceStatus.CANCELLED -> br.ufop.campus.core.uniffi.AttendanceStatus.CANCELLED
}

private fun br.ufop.campus.core.uniffi.AttendanceStatus.toFfi(): AttendanceStatus = when (this) {
    br.ufop.campus.core.uniffi.AttendanceStatus.ATTENDING -> AttendanceStatus.ATTENDING
    br.ufop.campus.core.uniffi.AttendanceStatus.ABSENT -> AttendanceStatus.ABSENT
    br.ufop.campus.core.uniffi.AttendanceStatus.CANCELLED -> AttendanceStatus.CANCELLED
}

private fun br.ufop.campus.core.uniffi.TimelineItemKind.toFfi(): TimelineItemKind = when (this) {
    br.ufop.campus.core.uniffi.TimelineItemKind.CLASS_SESSION -> TimelineItemKind.CLASS_SESSION
    br.ufop.campus.core.uniffi.TimelineItemKind.COMMITMENT -> TimelineItemKind.COMMITMENT
}

private fun br.ufop.campus.core.uniffi.DayPillItem.toFfi(): DayPillItem = DayPillItem(
    isoDate = isoDate,
    dayShort = dayShort,
    dayNumber = dayNumber.toInt()
)

private fun br.ufop.campus.core.uniffi.AgendaTimelineItem.toFfi(): AgendaTimelineItem = AgendaTimelineItem(
    id = id,
    slotId = slotId,
    courseId = courseId,
    kind = kind.toFfi(),
    title = title,
    startTime = startTime,
    endTime = endTime,
    location = location,
    subtitle = subtitle,
    colorHex = colorHex,
    weatherTempC = weatherTempC,
    status = status?.toFfi()
)

private fun br.ufop.campus.core.uniffi.AgendaDayOverview.toFfi(): AgendaDayOverview = AgendaDayOverview(
    isoDate = isoDate,
    monthYearLabel = monthYearLabel,
    formattedDate = formattedDate,
    weekNumber = weekNumber.toInt(),
    weekDays = weekDays.map { it.toFfi() },
    classesCount = classesCount.toInt(),
    commitmentsCount = commitmentsCount.toInt(),
    items = items.map { it.toFfi() }
)

private fun br.ufop.campus.core.uniffi.DepartmentColorOption.toFfi(): DepartmentColorOption = DepartmentColorOption(
    id = id,
    name = name,
    colorHex = colorHex
)

private fun br.ufop.campus.core.uniffi.ClassCheckInDetail.toFfi(): ClassCheckInDetail = ClassCheckInDetail(
    slotId = slotId,
    courseId = courseId,
    departmentId = departmentId,
    departmentName = departmentName,
    courseName = courseName,
    formattedDate = formattedDate,
    startTime = startTime,
    endTime = endTime,
    weatherTempC = weatherTempC,
    weatherCondition = weatherCondition,
    buildingAndRoom = buildingAndRoom,
    professor = professor,
    selectedColorHex = selectedColorHex,
    availableColors = availableColors.map { it.toFfi() },
    status = status.toFfi()
)

private fun br.ufop.campus.core.uniffi.DisciplineSummaryCard.toFfi(): DisciplineSummaryCard = DisciplineSummaryCard(
    courseId = courseId,
    name = name,
    departmentId = departmentId,
    departmentName = departmentName,
    colorHex = colorHex,
    workloadHours = workloadHours.toInt(),
    currentAbsences = currentAbsences.toInt(),
    maxAbsences = maxAbsences.toInt(),
    cancelledClasses = cancelledClasses.toInt(),
    code = deriveCourseCodeFallback(null, "", courseId, name)
)

private fun br.ufop.campus.core.uniffi.AssessmentItem.toFfi(): AssessmentItem = AssessmentItem(
    id = id,
    courseId = courseId,
    dayNumber = dayNumber,
    monthShort = monthShort,
    title = title,
    kind = kind,
    dueTime = dueTime,
    dueDate = dueDate,
    completed = completed
)

private fun br.ufop.campus.core.uniffi.DisciplineDetail.toFfi(): DisciplineDetail {
    val mappedSummary = summary.toFfi()
    return DisciplineDetail(
        summary = mappedSummary,
        semester = semester,
        assessments = assessments.map { it.toFfi() },
        notes = notes,
        code = mappedSummary.code
    )
}

private fun br.ufop.campus.core.uniffi.TimetableCell.toFfi(): TimetableCell = TimetableCell(
    slotId = slotId,
    courseId = courseId,
    courseName = courseName,
    shortLocation = shortLocation,
    departmentId = departmentId,
    departmentName = departmentName,
    colorHex = colorHex,
    code = deriveCourseCodeFallback(null, "", courseId, courseName)
)

private fun br.ufop.campus.core.uniffi.WeeklyTimetable.toFfi(): WeeklyTimetable {
    val mappedRows = rows.map { row ->
        TimetableRow(
            startTime = row.startTime,
            endTime = row.endTime,
            cellsByDay = row.cells.associate { it.dayOfWeek.toInt() to it.cell?.toFfi() }
        )
    }
    val mappedDays = rows.firstOrNull()?.cells?.map { it.dayOfWeek.toInt() to it.dayLabel }
        ?: days.mapIndexed { idx, label -> dayIndexForLabel(label, idx + 1) to label }
    return WeeklyTimetable(
        semester = semester,
        gridTitle = gridTitle,
        days = mappedDays,
        rows = mappedRows,
        legend = legend.map { it.toFfi() }
    )
}

private fun dayIndexForLabel(label: String, defaultIndex: Int): Int = when (label.uppercase()) {
    "SEG" -> 1
    "TER" -> 2
    "QUA" -> 3
    "QUI" -> 4
    "SEX" -> 5
    "SÁB", "SAB" -> 6
    "DOM" -> 7
    else -> defaultIndex
}

private fun br.ufop.campus.core.uniffi.AssessmentWithCourse.toFfi(): AssessmentWithCourse = AssessmentWithCourse(
    assessment = assessment.toFfi(),
    courseName = courseName,
    departmentName = departmentName,
    colorHex = colorHex
)

private fun br.ufop.campus.core.uniffi.UfopCampusInfo.toFfi(): UfopCampusInfo = UfopCampusInfo(
    id = id,
    name = name,
    city = city,
    weatherTempC = weatherTempC,
    weatherCondition = weatherCondition,
    isActive = isActive
)

private fun br.ufop.campus.core.uniffi.AcademicCalendarEvent.toFfi(): AcademicCalendarEvent = AcademicCalendarEvent(
    id = id,
    semester = semester,
    eventDate = eventDate,
    dayNumber = dayNumber,
    monthShort = monthShort,
    title = title,
    category = category
)

private fun br.ufop.campus.core.uniffi.AcademicProfileSummary.toFfi(): AcademicProfileSummary = AcademicProfileSummary(
    semester = semester,
    activeCampus = activeCampus.toFfi(),
    campuses = campuses.map { it.toFfi() },
    totalCourses = totalCourses.toInt(),
    totalAbsences = totalAbsences.toInt(),
    totalMaxAbsences = totalMaxAbsences.toInt(),
    totalCancelledClasses = totalCancelledClasses.toInt(),
    coursesAtRisk = coursesAtRisk.toInt(),
    pendingAssessments = pendingAssessments.toInt(),
    completedAssessments = completedAssessments.toInt(),
    calendarEvents = calendarEvents.map { it.toFfi() }
)

private fun br.ufop.campus.core.uniffi.UfopCurriculumTemplate.toFfi(): UfopCurriculumTemplate = UfopCurriculumTemplate(
    id = id,
    code = code,
    name = name,
    departmentName = departmentName,
    campusName = campusName,
    description = description,
    coursesCount = coursesCount.toInt()
)

private fun br.ufop.campus.core.uniffi.CourseScheduleSlotItem.toFfi(): CourseScheduleSlotItem = CourseScheduleSlotItem(
    slotId = slotId,
    courseId = courseId,
    dayOfWeek = dayOfWeek.toInt(),
    dayLabel = dayLabel,
    startTime = startTime,
    endTime = endTime,
    building = building,
    room = room,
    shortLocation = shortLocation
)

private fun br.ufop.campus.core.uniffi.AbsenceHistoryEntry.toFfi(): AbsenceHistoryEntry = AbsenceHistoryEntry(
    sessionId = sessionId,
    slotId = slotId,
    courseId = courseId,
    sessionDate = sessionDate,
    formattedDate = formattedDate,
    startTime = startTime,
    endTime = endTime,
    status = status.toFfi(),
    classWeight = classWeight.toInt()
)

private fun br.ufop.campus.core.uniffi.CampusSearchResults.toFfi(): CampusSearchResults = CampusSearchResults(
    query = query,
    courses = courses.map { it.toFfi() },
    assessments = assessments.map { it.toFfi() },
    commitments = commitments.map { it.toFfi() }
)
