import Foundation

// MARK: - UniFFI Domain Enumerations

/// Paleta de cores de departamentos acadêmicos da UFOP (espelha `DepartmentColorKey` do UniFFI).
public enum DepartmentColorKey: String, CaseIterable, Codable, Identifiable, Sendable {
    case mathBlue = "MATH_BLUE"
    case compPurple = "COMP_PURPLE"
    case algebraGreen = "ALGEBRA_GREEN"
    case physicsAmber = "PHYSICS_AMBER"

    public var id: String { rawValue }

    public var hexString: String {
        switch self {
        case .mathBlue:
            return "#3B68FF"
        case .compPurple:
            return "#6E56CF"
        case .algebraGreen:
            return "#2E7D32"
        case .physicsAmber:
            return "#8D5B16"
        }
    }

    public var departmentLabel: String {
        switch self {
        case .mathBlue:
            return "Matemática"
        case .compPurple:
            return "Computação"
        case .algebraGreen:
            return "Álgebra Linear"
        case .physicsAmber:
            return "Física"
        }
    }

    public var accessibilityLabel: String {
        switch self {
        case .mathBlue:
            return "Azul Matemática"
        case .compPurple:
            return "Roxo Computação"
        case .algebraGreen:
            return "Verde Álgebra Linear"
        case .physicsAmber:
            return "Âmbar Física"
        }
    }

    public static func fromHex(_ hex: String) -> DepartmentColorKey {
        let normalized = hex.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        switch normalized {
        case "#6E56CF":
            return .compPurple
        case "#2E7D32":
            return .algebraGreen
        case "#8D5B16":
            return .physicsAmber
        default:
            return .mathBlue
        }
    }
}

/// Estado de presença de uma aula na Agenda (espelha `AttendanceStatus` do UniFFI).
/// Regra crítica: `.cancelled` incrementa `cancelledClasses` e NÃO soma falta em `currentAbsences`.
public enum AttendanceStatus: String, CaseIterable, Codable, Identifiable, Sendable {
    case attending = "ATTENDING"
    case absent = "ABSENT"
    case cancelled = "CANCELLED"

    public var id: String { rawValue }

    public var buttonTitle: String {
        switch self {
        case .attending:
            return "Vou à aula"
        case .absent:
            return "Vou faltar"
        case .cancelled:
            return "Aula cancelada"
        }
    }

    public var badgeTitle: String {
        switch self {
        case .attending:
            return "Presença confirmada"
        case .absent:
            return "Falta registrada"
        case .cancelled:
            return "Aula cancelada"
        }
    }

    public var systemImage: String {
        switch self {
        case .attending:
            return "checkmark.circle.fill"
        case .absent:
            return "xmark.circle.fill"
        case .cancelled:
            return "slash.circle.fill"
        }
    }
}

/// Tipo de item na linha do tempo da Agenda.
public enum TimelineItemKind: String, Codable, Sendable {
    case lecture = "LECTURE"
    case commitment = "COMMITMENT"
}

// MARK: - Agenda Domain Records

/// Pílula diária na faixa horizontal da semana (`SEG 16` a `DOM 22`).
public struct AgendaDayChip: Identifiable, Equatable, Codable, Sendable {
    public let dateIso: String
    public let weekdayShort: String
    public let dayNumber: String
    public var isSelected: Bool
    public var hasEvents: Bool

    public var id: String { dateIso }

    public init(
        dateIso: String,
        weekdayShort: String,
        dayNumber: String,
        isSelected: Bool,
        hasEvents: Bool
    ) {
        self.dateIso = dateIso
        self.weekdayShort = weekdayShort
        self.dayNumber = dayNumber
        self.isSelected = isSelected
        self.hasEvents = hasEvents
    }
}

/// Card individual na timeline diária da aba Agenda.
public struct AgendaTimelineItem: Identifiable, Equatable, Codable, Sendable {
    public let id: String
    public let disciplineId: String?
    public let kind: TimelineItemKind
    public let startTime: String
    public let endTime: String
    public var title: String
    public var subtitle: String
    public var location: String
    public var professor: String
    public var department: String
    public var colorKey: DepartmentColorKey
    public var weatherTempC: Int
    public var weatherCondition: String
    public var attendanceStatus: AttendanceStatus

    public var timeRangeLabel: String {
        "\(startTime)–\(endTime)"
    }

    public var weatherBadgeLabel: String {
        "☀️ \(weatherTempC) °C"
    }

    public var weatherDetailLabel: String {
        "\(weatherTempC) °C · \(weatherCondition)"
    }

    public var isClassCheckInEnabled: Bool {
        kind == .lecture && disciplineId != nil
    }

    public var isCustomCommitment: Bool {
        kind == .commitment
    }

    public init(
        id: String,
        disciplineId: String?,
        kind: TimelineItemKind,
        startTime: String,
        endTime: String,
        title: String,
        subtitle: String,
        location: String,
        professor: String,
        department: String,
        colorKey: DepartmentColorKey,
        weatherTempC: Int,
        weatherCondition: String,
        attendanceStatus: AttendanceStatus
    ) {
        self.id = id
        self.disciplineId = disciplineId
        self.kind = kind
        self.startTime = startTime
        self.endTime = endTime
        self.title = title
        self.subtitle = subtitle
        self.location = location
        self.professor = professor
        self.department = department
        self.colorKey = colorKey
        self.weatherTempC = weatherTempC
        self.weatherCondition = weatherCondition
        self.attendanceStatus = attendanceStatus
    }
}

/// Visão consolidada do dia selecionado na aba Agenda.
public struct AgendaDayOverview: Equatable, Codable, Sendable {
    public let semesterTag: String
    public let monthLabel: String
    public let weekLabel: String
    public let selectedDateIso: String
    public let fullDateHeader: String
    public let summarySubtitle: String
    public let weekDays: [AgendaDayChip]
    public let timelineItems: [AgendaTimelineItem]

    public init(
        semesterTag: String,
        monthLabel: String,
        weekLabel: String,
        selectedDateIso: String,
        fullDateHeader: String,
        summarySubtitle: String,
        weekDays: [AgendaDayChip],
        timelineItems: [AgendaTimelineItem]
    ) {
        self.semesterTag = semesterTag
        self.monthLabel = monthLabel
        self.weekLabel = weekLabel
        self.selectedDateIso = selectedDateIso
        self.fullDateHeader = fullDateHeader
        self.summarySubtitle = summarySubtitle
        self.weekDays = weekDays
        self.timelineItems = timelineItems
    }
}

/// Modelo da folha modal `ClassCheckInSheetView` (Check-in de presença na aula).
public struct ClassCheckInDetail: Identifiable, Equatable, Codable, Sendable {
    public let agendaItemId: String
    public let disciplineId: String
    public let department: String
    public let disciplineName: String
    public let fullDateLabel: String
    public let timeRange: String
    public let weatherLabel: String
    public let location: String
    public let professor: String
    public var selectedColorKey: DepartmentColorKey
    public var selectedStatus: AttendanceStatus
    public let footnoteText: String

    public var id: String { agendaItemId }

    public init(
        agendaItemId: String,
        disciplineId: String,
        department: String,
        disciplineName: String,
        fullDateLabel: String,
        timeRange: String,
        weatherLabel: String,
        location: String,
        professor: String,
        selectedColorKey: DepartmentColorKey,
        selectedStatus: AttendanceStatus,
        footnoteText: String = "Aula cancelada não conta como falta. Salve para atualizar o relatório em Disciplinas."
    ) {
        self.agendaItemId = agendaItemId
        self.disciplineId = disciplineId
        self.department = department
        self.disciplineName = disciplineName
        self.fullDateLabel = fullDateLabel
        self.timeRange = timeRange
        self.weatherLabel = weatherLabel
        self.location = location
        self.professor = professor
        self.selectedColorKey = selectedColorKey
        self.selectedStatus = selectedStatus
        self.footnoteText = footnoteText
    }
}

/// Formata títulos de avaliações importados em CAIXA ALTA para Title Case amigável em PT-BR,
/// preservando siglas acadêmicas (EDO, P1, P2, TP1, TCC, códigos de disciplina) e algarismos romanos.
public func formatAcademicAssessmentTitle(_ raw: String) -> String {
    let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else { return "" }

    let hasLower = trimmed.contains { $0.isLetter && $0.isLowercase }
    let hasUpper = trimmed.contains { $0.isLetter && $0.isUppercase }
    let preservedAcronyms: Set<String> = [
        "UFOP", "ICEB", "ICEA", "ICHS", "ICSA", "EMED", "EFAR", "EDTM", "ENUT",
        "TCC", "EDO", "EDP", "SUB", "IC", "PIBIC", "PET", "EAD", "PDF", "API", "SQL",
        "ULA", "ALU", "CPU", "GPU", "RAM", "VHDL", "FPGA", "CLP", "PLC", "PID",
        "POO", "UML", "IA", "BD", "SO", "TI", "SGA", "DECOM", "DECSI", "DECEA",
        "DECAT", "DECBI", "DEMAT", "DEFIS", "DEQUI", "DECIV", "DEPRO", "DEELT"
    ]
    let romans: Set<String> = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"]
    let particles: Set<String> = [
        "de", "da", "do", "das", "dos", "e", "em", "na", "no", "nas", "nos",
        "para", "por", "com", "sem", "sob", "a", "o", "as", "os", "ao", "aos", "ou"
    ]
    let accentsMap: [String: String] = [
        "avaliacao": "avaliação", "revisao": "revisão", "apresentacao": "apresentação",
        "seminario": "seminário", "relatorio": "relatório", "pratica": "prática",
        "teorica": "teórica", "equacoes": "equações", "algebra": "álgebra",
        "calculo": "cálculo", "fisica": "física", "quimica": "química",
        "mecanica": "mecânica", "eletrica": "elétrica", "eletronica": "eletrônica",
        "logica": "lógica", "analise": "análise", "programacao": "programação",
        "computacao": "computação", "estatistica": "estatística", "metodo": "método",
        "metodos": "métodos", "numerico": "numérico", "numericos": "numéricos",
        "exercicio": "exercício", "exercicios": "exercícios", "questao": "questão",
        "questoes": "questões", "codigo": "código", "topicos": "tópicos",
        "basico": "básico", "basica": "básica", "integracao": "integração",
        "automacao": "automação", "otimizacao": "otimização", "producao": "produção",
        "introducao": "introdução"
    ]

    func isExamOrCourseCode(_ upper: String) -> Bool {
        upper.range(of: "^[A-Z]{1,5}\\d{1,4}[A-Z]?$", options: .regularExpression) != nil
    }

    let tokens = trimmed.components(separatedBy: .whitespacesAndNewlines).filter { !$0.isEmpty }

    if hasLower && hasUpper {
        return tokens.map { token in
            let upper = token.uppercased()
            if preservedAcronyms.contains(upper) || isExamOrCourseCode(upper) {
                return upper
            }
            return token
        }.joined(separator: " ")
    }

    return tokens.enumerated().map { index, token in
        let upper = token.uppercased()
        if romans.contains(upper) || preservedAcronyms.contains(upper) || isExamOrCourseCode(upper) {
            return upper
        }
        let asciiLower = token
            .folding(options: .diacriticInsensitive, locale: Locale(identifier: "pt_BR"))
            .lowercased()
        if index > 0 && particles.contains(asciiLower) {
            return asciiLower
        }
        let base = accentsMap[asciiLower] ?? token.lowercased()
        return base.prefix(1).uppercased() + base.dropFirst()
    }.joined(separator: " ")
}
