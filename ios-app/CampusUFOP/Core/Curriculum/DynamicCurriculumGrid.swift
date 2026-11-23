import Foundation

// MARK: - Dynamic Interactive Curriculum Grid Models & Engine

public struct DynamicSemesterTerm: Comparable, Equatable, Codable, Sendable {
    public let year: Int
    public let semester: Int

    public init(year: Int, semester: Int) {
        self.year = year
        self.semester = semester
    }

    public static func < (lhs: DynamicSemesterTerm, rhs: DynamicSemesterTerm) -> Bool {
        if lhs.year != rhs.year { return lhs.year < rhs.year }
        return lhs.semester < rhs.semester
    }

    public func formatLabel() -> String {
        "\(year)/\(semester)"
    }
}

public struct DynamicCourseNode: Identifiable, Equatable, Codable, Sendable {
    public let code: String
    public let name: String
    public let workloadHours: Int
    public let period: Int
    public let prerequisites: [String]
    public let minHoursRequired: Int
    public let isElective: Bool

    public var id: String { code }

    public init(
        code: String,
        name: String,
        workloadHours: Int,
        period: Int,
        prerequisites: [String] = [],
        minHoursRequired: Int = 0,
        isElective: Bool = false
    ) {
        self.code = code
        self.name = name
        self.workloadHours = workloadHours
        self.period = period
        self.prerequisites = prerequisites
        self.minHoursRequired = minHoursRequired
        self.isElective = isElective
    }
}

public struct DynamicCurriculumMatrix: Identifiable, Equatable, Codable, Sendable {
    public let id: String
    public let courseName: String
    public let curriculumLabel: String
    public let totalPeriods: Int
    public let requiredActivitiesHours: Int
    public let totalCourseHours: Int
    public let courses: [DynamicCourseNode]
    public let courseCode: String
    public let courseAliases: [String]
    public let effectiveFrom: DynamicSemesterTerm
    public let effectiveUntil: DynamicSemesterTerm?
    public let legacyIds: [String]
    public let campus: String

    public var totalGridHours: Int {
        courses.reduce(0) { $0 + $1.workloadHours }
    }

    public func validityLabel() -> String {
        if let effectiveUntil {
            return "\(effectiveFrom.formatLabel())–\(effectiveUntil.formatLabel())"
        }
        return "\(effectiveFrom.formatLabel())+"
    }

    public init(
        id: String,
        courseName: String,
        curriculumLabel: String,
        totalPeriods: Int,
        requiredActivitiesHours: Int,
        totalCourseHours: Int,
        courses: [DynamicCourseNode],
        courseCode: String = "",
        courseAliases: [String] = [],
        effectiveFrom: DynamicSemesterTerm = DynamicSemesterTerm(year: 2024, semester: 1),
        effectiveUntil: DynamicSemesterTerm? = nil,
        legacyIds: [String] = [],
        campus: String = ""
    ) {
        self.id = id
        self.courseName = courseName
        self.curriculumLabel = curriculumLabel
        self.totalPeriods = totalPeriods
        self.requiredActivitiesHours = requiredActivitiesHours
        self.totalCourseHours = totalCourseHours
        self.courses = courses
        self.courseCode = courseCode
        self.courseAliases = courseAliases
        self.effectiveFrom = effectiveFrom
        self.effectiveUntil = effectiveUntil
        self.legacyIds = legacyIds
        self.campus = campus
    }

    public func course(forCode code: String) -> DynamicCourseNode? {
        let normalized = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return courses.first { $0.code.uppercased() == normalized }
    }

    public func courses(inPeriod period: Int) -> [DynamicCourseNode] {
        courses.filter { $0.period == period }
    }
}

public struct DynamicMatrixResolution: Equatable, Sendable {
    public let matrix: DynamicCurriculumMatrix
    public let studentEntryTerm: DynamicSemesterTerm?
    public let isCourseMatched: Bool
    public let isExactVersionMatch: Bool
    public let note: String?

    public init(
        matrix: DynamicCurriculumMatrix,
        studentEntryTerm: DynamicSemesterTerm?,
        isCourseMatched: Bool,
        isExactVersionMatch: Bool,
        note: String?
    ) {
        self.matrix = matrix
        self.studentEntryTerm = studentEntryTerm
        self.isCourseMatched = isCourseMatched
        self.isExactVersionMatch = isExactVersionMatch
        self.note = note
    }
}

public enum DynamicCourseStatus: String, Codable, Equatable, Sendable {
    case completed
    case available
    case locked
}

public enum DynamicGridToggleAction: String, Codable, Equatable, Sendable {
    case markedCompleted
    case unmarkedWithCascade
    case blockedByPrerequisites
    case noChange
}

public struct DynamicGridToggleResult: Equatable, Sendable {
    public let updatedCompletedCodes: Set<String>
    public let action: DynamicGridToggleAction
    public let affectedCode: String
    public let cascadeRemovedCodes: [String]
    public let missingPrerequisites: [String]
    public let missingHours: Int

    public init(
        updatedCompletedCodes: Set<String>,
        action: DynamicGridToggleAction,
        affectedCode: String,
        cascadeRemovedCodes: [String],
        missingPrerequisites: [String],
        missingHours: Int
    ) {
        self.updatedCompletedCodes = updatedCompletedCodes
        self.action = action
        self.affectedCode = affectedCode
        self.cascadeRemovedCodes = cascadeRemovedCodes
        self.missingPrerequisites = missingPrerequisites
        self.missingHours = missingHours
    }
}

public struct DynamicGridSummary: Equatable, Sendable {
    public let completedHours: Int
    public let totalGridHours: Int
    public let completionPercent: Int
    public let completedCount: Int
    public let totalCount: Int

    public var progressFraction: Double {
        guard totalGridHours > 0 else { return 0 }
        return min(1.0, max(0.0, Double(completedHours) / Double(totalGridHours)))
    }

    public init(
        completedHours: Int,
        totalGridHours: Int,
        completionPercent: Int,
        completedCount: Int,
        totalCount: Int
    ) {
        self.completedHours = completedHours
        self.totalGridHours = totalGridHours
        self.completionPercent = completionPercent
        self.completedCount = completedCount
        self.totalCount = totalCount
    }
}

// MARK: - Curriculum Matrices Catalog

public enum DynamicCurriculumCatalog {
    private static let automacaoAliases: [String] = [
        "engenharia de controle e automacao",
        "eng. de controle e automacao",
        "controle e automacao",
        "aut"
    ]

    public static let engControleAutomacao2024_1 = DynamicCurriculumMatrix(
        id: "eng_controle_automacao_2024_1",
        courseName: "Eng. de Controle e Automação",
        curriculumLabel: "2024/1 · Currículo 2",
        totalPeriods: 10,
        requiredActivitiesHours: 640,
        totalCourseHours: 3610,
        courses: [
            // Período 1
            DynamicCourseNode(code: "BCC701", name: "Programação de Computadores I", workloadHours: 60, period: 1),
            DynamicCourseNode(code: "CSO117", name: "Metodologia de Pesquisa", workloadHours: 30, period: 1),
            DynamicCourseNode(code: "MTM122", name: "Cálculo Diferencial e Integral I", workloadHours: 90, period: 1),
            DynamicCourseNode(code: "MTM730", name: "Geometria Analítica e Álgebra Linear", workloadHours: 60, period: 1),
            DynamicCourseNode(code: "QUI701", name: "Química Fundamental", workloadHours: 60, period: 1),

            // Período 2
            DynamicCourseNode(code: "ARQ205", name: "Representação Gráfica", workloadHours: 60, period: 2),
            DynamicCourseNode(code: "BCC702", name: "Programação de Computadores II", workloadHours: 60, period: 2, prerequisites: ["BCC701"]),
            DynamicCourseNode(code: "EST202", name: "Estatística e Probabilidade", workloadHours: 60, period: 2),
            DynamicCourseNode(code: "FIS106", name: "Fundamentos de Mecânica", workloadHours: 60, period: 2, prerequisites: ["MTM122", "MTM730"]),
            DynamicCourseNode(code: "MTM123", name: "Cálculo Diferencial e Integral II", workloadHours: 60, period: 2, prerequisites: ["MTM122"]),

            // Período 3
            DynamicCourseNode(code: "BCC760", name: "Cálculo Numérico", workloadHours: 60, period: 3),
            DynamicCourseNode(code: "FIS107", name: "Fundamentos de Termodinâmica", workloadHours: 30, period: 3, prerequisites: ["FIS106", "MTM123"]),
            DynamicCourseNode(code: "FIS108", name: "Fundamentos de Fluidos, Oscilações e Ondas", workloadHours: 30, period: 3, prerequisites: ["FIS106", "MTM123"]),
            DynamicCourseNode(code: "FIS109", name: "Fundamentos de Eletromagnetismo", workloadHours: 60, period: 3, prerequisites: ["FIS106", "MTM123"]),
            DynamicCourseNode(code: "MTM124", name: "Cálculo Diferencial e Integral III", workloadHours: 60, period: 3, prerequisites: ["MTM123"]),
            DynamicCourseNode(code: "MTM125", name: "Introdução às Equações Diferenciais Ordinárias", workloadHours: 60, period: 3, prerequisites: ["MTM123"]),

            // Período 4
            DynamicCourseNode(code: "BCC720", name: "Sistemas de Computação para Controle e Automação", workloadHours: 60, period: 4, prerequisites: ["BCC702"]),
            DynamicCourseNode(code: "CAT176", name: "Análise de Circuitos Elétricos", workloadHours: 60, period: 4),
            DynamicCourseNode(code: "FIS105", name: "Fundamentos de Física Experimental", workloadHours: 30, period: 4, prerequisites: ["FIS109", "MTM124"]),
            DynamicCourseNode(code: "FIS110", name: "Fundamentos de Óptica e Quântica", workloadHours: 30, period: 4, prerequisites: ["FIS109", "MTM124"]),
            DynamicCourseNode(code: "MEC124", name: "Fenômenos de Transporte", workloadHours: 60, period: 4, prerequisites: ["FIS107", "FIS108"]),
            DynamicCourseNode(code: "MTM146", name: "Matemática Aplicada à Eng. de Controle e Automação", workloadHours: 60, period: 4, prerequisites: ["MTM124", "MTM125", "MTM730"]),

            // Período 5
            DynamicCourseNode(code: "BCC721", name: "Circuitos Digitais", workloadHours: 60, period: 5, prerequisites: ["BCC720"]),
            DynamicCourseNode(code: "CAT141", name: "Teoria de Controle I", workloadHours: 60, period: 5),
            DynamicCourseNode(code: "CAT164", name: "Eletrotécnica para Controle e Automação", workloadHours: 60, period: 5),
            DynamicCourseNode(code: "CAT165", name: "Circuitos e Dispositivos Eletrônicos", workloadHours: 60, period: 5),
            DynamicCourseNode(code: "CIV107", name: "Resistência dos Materiais e Estruturas", workloadHours: 60, period: 5),

            // Período 6
            DynamicCourseNode(code: "BCC342", name: "Introdução à Otimização", workloadHours: 60, period: 6, prerequisites: ["BCC702", "BCC760"]),
            DynamicCourseNode(code: "CAT163", name: "Instrumentação", workloadHours: 60, period: 6),
            DynamicCourseNode(code: "CAT169", name: "Acionamentos Elétricos", workloadHours: 60, period: 6),
            DynamicCourseNode(code: "CAT183", name: "Teoria de Controle II", workloadHours: 60, period: 6),
            DynamicCourseNode(code: "MET702", name: "Engenharia nos Processos de Metalurgia", workloadHours: 60, period: 6),

            // Período 7
            DynamicCourseNode(code: "BCC425", name: "Sistemas Embutidos", workloadHours: 60, period: 7, prerequisites: ["BCC721"]),
            DynamicCourseNode(code: "BCC740", name: "Inteligência Artificial", workloadHours: 60, period: 7, prerequisites: ["BCC702"]),
            DynamicCourseNode(code: "CAT166", name: "Teoria de Controle III", workloadHours: 60, period: 7),
            DynamicCourseNode(code: "CAT175", name: "Informática Industrial", workloadHours: 60, period: 7),
            DynamicCourseNode(code: "MIN107", name: "Engenharia nos Processos de Mineração", workloadHours: 60, period: 7),

            // Período 8
            DynamicCourseNode(code: "BCC722", name: "Programação de Sistemas em Tempo Real", workloadHours: 60, period: 8, prerequisites: ["BCC425"]),
            DynamicCourseNode(code: "CAT167", name: "Sistemas Integrados de Manufatura", workloadHours: 60, period: 8),
            DynamicCourseNode(code: "CAT181", name: "Elementos de Robótica", workloadHours: 60, period: 8),
            DynamicCourseNode(code: "CAT490", name: "Trabalho Final de Curso I", workloadHours: 30, period: 8),
            DynamicCourseNode(code: "DIR250", name: "Introdução ao Direito e Legislação", workloadHours: 30, period: 8),
            DynamicCourseNode(code: "MEC142", name: "Acionamentos Fluidomecânicos", workloadHours: 60, period: 8),

            // Período 9
            DynamicCourseNode(code: "CAT168", name: "Interfaceamento de Sistemas", workloadHours: 60, period: 9),
            DynamicCourseNode(code: "CAT491", name: "Trabalho Final de Curso II", workloadHours: 30, period: 9, prerequisites: ["CAT490"]),
            DynamicCourseNode(code: "PRO243", name: "Organização e Administração I", workloadHours: 30, period: 9),
            DynamicCourseNode(code: "ELT001", name: "Disciplina Eletiva I", workloadHours: 60, period: 9, isElective: true),
            DynamicCourseNode(code: "ELT002", name: "Disciplina Eletiva II", workloadHours: 60, period: 9, isElective: true),

            // Período 10
            DynamicCourseNode(code: "AMB111", name: "Engenharia Ambiental Básica M", workloadHours: 30, period: 10),
            DynamicCourseNode(code: "PRO215", name: "Planejamento e Controle da Produção I", workloadHours: 60, period: 10),
            DynamicCourseNode(code: "PRO224", name: "Economia da Engenharia", workloadHours: 60, period: 10),
            DynamicCourseNode(code: "ELT003", name: "Disciplina Eletiva III", workloadHours: 60, period: 10, isElective: true),
            DynamicCourseNode(code: "ELT004", name: "Disciplina Eletiva IV", workloadHours: 60, period: 10, isElective: true),
            DynamicCourseNode(code: "ELT005", name: "Disciplina Eletiva V", workloadHours: 60, period: 10, isElective: true)
        ],
        courseCode: "AUT",
        courseAliases: automacaoAliases,
        effectiveFrom: DynamicSemesterTerm(year: 2024, semester: 1),
        effectiveUntil: DynamicSemesterTerm(year: 2025, semester: 2),
        legacyIds: ["eng_controle_automacao_2023"],
        campus: "Ouro Preto"
    )

    public static let engControleAutomacao2026_1 = DynamicCurriculumMatrix(
        id: "eng_controle_automacao_2026_1",
        courseName: "Eng. de Controle e Automação",
        curriculumLabel: "2026/1 · Currículo 3",
        totalPeriods: 10,
        requiredActivitiesHours: 510,
        totalCourseHours: 3390,
        courses: [
            // Período 1
            DynamicCourseNode(code: "ARQ700", name: "Expressão Gráfica", workloadHours: 60, period: 1),
            DynamicCourseNode(code: "BCC703", name: "Fundamentos de Programação", workloadHours: 60, period: 1),
            DynamicCourseNode(code: "CAT410", name: "Introdução à Eng. de Controle e Automação", workloadHours: 30, period: 1),
            DynamicCourseNode(code: "MTM122", name: "Cálculo Diferencial e Integral I", workloadHours: 90, period: 1),
            DynamicCourseNode(code: "MTM730", name: "Geometria Analítica e Álgebra Linear", workloadHours: 60, period: 1),

            // Período 2
            DynamicCourseNode(code: "BCC704", name: "Algoritmos e Estruturas de Dados", workloadHours: 60, period: 2, prerequisites: ["BCC703"]),
            DynamicCourseNode(code: "CAT411", name: "Introdução à Aquisição de Dados e Controle", workloadHours: 30, period: 2, prerequisites: ["BCC703"]),
            DynamicCourseNode(code: "FIS106", name: "Fundamentos de Mecânica", workloadHours: 60, period: 2, prerequisites: ["MTM122", "MTM730"]),
            DynamicCourseNode(code: "FIS107", name: "Fundamentos de Termodinâmica", workloadHours: 30, period: 2),
            DynamicCourseNode(code: "MTM123", name: "Cálculo Diferencial e Integral II", workloadHours: 60, period: 2, prerequisites: ["MTM122"]),
            DynamicCourseNode(code: "QUI022", name: "Química Geral Experimental AB", workloadHours: 30, period: 2),
            DynamicCourseNode(code: "QUI075", name: "Química Geral B", workloadHours: 30, period: 2),

            // Período 3
            DynamicCourseNode(code: "AMB111", name: "Engenharia Ambiental Básica M", workloadHours: 30, period: 3),
            DynamicCourseNode(code: "BCC721", name: "Circuitos Digitais", workloadHours: 60, period: 3),
            DynamicCourseNode(code: "BCC760", name: "Cálculo Numérico", workloadHours: 60, period: 3, prerequisites: ["BCC704"]),
            DynamicCourseNode(code: "FIS108", name: "Fundamentos de Fluidos, Oscilações e Ondas", workloadHours: 30, period: 3, prerequisites: ["FIS106"]),
            DynamicCourseNode(code: "MTM124", name: "Cálculo Diferencial e Integral III", workloadHours: 60, period: 3, prerequisites: ["MTM123"]),
            DynamicCourseNode(code: "MTM125", name: "Introdução às Equações Diferenciais Ordinárias", workloadHours: 60, period: 3, prerequisites: ["MTM122"]),

            // Período 4
            DynamicCourseNode(code: "BCC705", name: "Sistemas Computacionais para Engenharia", workloadHours: 30, period: 4, prerequisites: ["BCC721"]),
            DynamicCourseNode(code: "CAT176", name: "Análise de Circuitos Elétricos", workloadHours: 60, period: 4),
            DynamicCourseNode(code: "FIS105", name: "Fundamentos de Física Experimental", workloadHours: 30, period: 4, prerequisites: ["FIS106", "FIS107", "FIS108"]),
            DynamicCourseNode(code: "FIS109", name: "Fundamentos de Eletromagnetismo", workloadHours: 60, period: 4, prerequisites: ["MTM123"]),
            DynamicCourseNode(code: "MEC124", name: "Fenômenos de Transporte", workloadHours: 60, period: 4, prerequisites: ["FIS108"]),
            DynamicCourseNode(code: "MTM146", name: "Matemática Aplicada à Eng. de Controle e Automação", workloadHours: 60, period: 4, prerequisites: ["MTM125"]),

            // Período 5
            DynamicCourseNode(code: "BCC425", name: "Sistemas Embutidos", workloadHours: 60, period: 5, prerequisites: ["BCC704"]),
            DynamicCourseNode(code: "CAT412", name: "Teoria de Controle I", workloadHours: 60, period: 5, prerequisites: ["MTM125"]),
            DynamicCourseNode(code: "CAT413", name: "Eletrotécnica para Controle e Automação", workloadHours: 60, period: 5, prerequisites: ["CAT176"]),
            DynamicCourseNode(code: "CAT414", name: "Circuitos e Dispositivos Eletrônicos", workloadHours: 60, period: 5, prerequisites: ["CAT176"]),
            DynamicCourseNode(code: "EST202", name: "Estatística e Probabilidade", workloadHours: 60, period: 5),

            // Período 6
            DynamicCourseNode(code: "CAT163", name: "Instrumentação", workloadHours: 60, period: 6, prerequisites: ["CAT176"]),
            DynamicCourseNode(code: "CAT415", name: "Máquinas Elétricas", workloadHours: 60, period: 6, prerequisites: ["CAT413"]),
            DynamicCourseNode(code: "CAT416", name: "Acionamentos Elétricos", workloadHours: 60, period: 6, prerequisites: ["CAT413", "CAT414"]),
            DynamicCourseNode(code: "CAT417", name: "Teoria de Controle II", workloadHours: 60, period: 6, prerequisites: ["CAT412"]),
            DynamicCourseNode(code: "CAT418", name: "Laboratório de Controle I", workloadHours: 60, period: 6, prerequisites: ["CAT412"]),

            // Período 7
            DynamicCourseNode(code: "BCC740", name: "Inteligência Artificial", workloadHours: 60, period: 7, prerequisites: ["BCC703"]),
            DynamicCourseNode(code: "CAT182", name: "Elementos de Robótica", workloadHours: 60, period: 7, prerequisites: ["CAT418", "MTM730"]),
            DynamicCourseNode(code: "CAT420", name: "Informática Industrial", workloadHours: 60, period: 7, prerequisites: ["BCC721", "CAT416"]),
            DynamicCourseNode(code: "CAT421", name: "Teoria de Controle III", workloadHours: 60, period: 7, prerequisites: ["CAT417"]),
            DynamicCourseNode(code: "CAT422", name: "Sistemas Integrados de Manufatura", workloadHours: 30, period: 7),
            DynamicCourseNode(code: "CIV049", name: "Resistência dos Materiais para Controle e Automação", workloadHours: 30, period: 7, prerequisites: ["FIS105"]),

            // Período 8
            DynamicCourseNode(code: "BCC722", name: "Programação de Sistemas em Tempo Real", workloadHours: 60, period: 8, prerequisites: ["BCC703", "BCC704", "BCC721"]),
            DynamicCourseNode(code: "CAT423", name: "Instrumentação Inteligente", workloadHours: 60, period: 8, prerequisites: ["CAT163"]),
            DynamicCourseNode(code: "CAT424", name: "Redes Industriais", workloadHours: 60, period: 8, prerequisites: ["CAT420"]),
            DynamicCourseNode(code: "MEC142", name: "Acionamentos Fluidomecânicos", workloadHours: 60, period: 8),
            DynamicCourseNode(code: "ELT001", name: "Disciplina Eletiva I", workloadHours: 60, period: 8, isElective: true),

            // Período 9
            DynamicCourseNode(code: "CAT425", name: "Trabalho Final de Curso I", workloadHours: 30, period: 9, minHoursRequired: 2000),
            DynamicCourseNode(code: "DIR250", name: "Introdução ao Direito e Legislação", workloadHours: 30, period: 9),
            DynamicCourseNode(code: "MET702", name: "Engenharia nos Processos de Metalurgia", workloadHours: 60, period: 9),
            DynamicCourseNode(code: "MIN107", name: "Engenharia nos Processos de Mineração", workloadHours: 60, period: 9),
            DynamicCourseNode(code: "PRO224", name: "Economia da Engenharia", workloadHours: 60, period: 9),
            DynamicCourseNode(code: "ELT002", name: "Disciplina Eletiva II", workloadHours: 60, period: 9, isElective: true),

            // Período 10
            DynamicCourseNode(code: "CAT491", name: "Trabalho Final de Curso II", workloadHours: 30, period: 10, prerequisites: ["CAT425"]),
            DynamicCourseNode(code: "PRO215", name: "Planejamento e Controle da Produção I", workloadHours: 60, period: 10),
            DynamicCourseNode(code: "PRO243", name: "Organização e Administração I", workloadHours: 30, period: 10),
            DynamicCourseNode(code: "ELT003", name: "Disciplina Eletiva III", workloadHours: 60, period: 10, isElective: true)
        ],
        courseCode: "AUT",
        courseAliases: automacaoAliases,
        effectiveFrom: DynamicSemesterTerm(year: 2026, semester: 1),
        effectiveUntil: nil,
        campus: "Ouro Preto"
    )

    public static var engControleAutomacao2023: DynamicCurriculumMatrix {
        engControleAutomacao2024_1
    }

    public static let curatedMatrices: [DynamicCurriculumMatrix] = [
        engControleAutomacao2024_1,
        engControleAutomacao2026_1
    ]

    public static let allMatrices: [DynamicCurriculumMatrix] = buildAllMatrices(from: UfopCurriculumCatalog.allCourses)

    public static var defaultMatrix: DynamicCurriculumMatrix {
        engControleAutomacao2024_1
    }

    public static func fromCourseCurriculum(_ course: UfopCourseCurriculum) -> DynamicCurriculumMatrix? {
        guard !course.subjects.isEmpty else { return nil }
        let cleanCourseCode = course.code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let cleanCourseName = course.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanCourseCode.isEmpty, !cleanCourseName.isEmpty else { return nil }

        // Exclui atividades curriculares (já contabilizadas em requiredActivitiesHours) e deduplica códigos
        var seenCodes: Set<String> = []
        let curriculumSubjects = course.subjects.filter { subject in
            let typeNorm = subject.type.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            if typeNorm == "atividade" || typeNorm == "atv" { return false }
            let code = subject.code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            guard !code.isEmpty else { return false }
            return seenCodes.insert(code).inserted
        }
        guard !curriculumSubjects.isEmpty else { return nil }

        let maxSubjectPeriod = curriculumSubjects.map(\.period).max() ?? 1
        let totalPeriods = maxSubjectPeriod > 0 ? maxSubjectPeriod : 1
        let validSubjectCodes = Set(curriculumSubjects.map { $0.code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() })

        // Constrói grafo acíclico de pré-requisitos via verificação incremental de alcançabilidade
        var acceptedPrereqs: [String: [String]] = [:]
        func canReach(from start: String, target: String) -> Bool {
            var stack: [String] = [start]
            var visited: Set<String> = []
            while let current = stack.popLast() {
                if current == target { return true }
                if !visited.insert(current).inserted { continue }
                for next in (acceptedPrereqs[current] ?? []) where !visited.contains(next) {
                    stack.append(next)
                }
            }
            return false
        }

        let orderedSubjects = curriculumSubjects
            .enumerated()
            .map { index, subject -> (period: Int, index: Int, subject: UfopCurriculumSubject) in
                let resolved = subject.period > 0 ? min(subject.period, totalPeriods) : totalPeriods
                return (resolved, index, subject)
            }
            .sorted { lhs, rhs in
                if lhs.period != rhs.period { return lhs.period < rhs.period }
                return lhs.index < rhs.index
            }

        let prereqCodeRegex = try? NSRegularExpression(pattern: #"\b([A-Z]{2,5}\d{3,4}[A-Z]?)\b"#)
        let minHoursRegex = try? NSRegularExpression(pattern: #"\b(\d{1,5})\s*(?:h|hs|hora|horas)\b"#, options: [.caseInsensitive])

        var nodes: [DynamicCourseNode] = []
        nodes.reserveCapacity(orderedSubjects.count + 8)

        for item in orderedSubjects {
            let subject = item.subject
            let nodeCode = subject.code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            let workloadHours = max(0, subject.chs > 0 ? subject.chs : subject.cha)
            var minHoursRequired = 0
            var candidatePrereqs: [String] = []
            var candidateSet: Set<String> = []

            for rawPrereq in subject.prerequisites {
                let upper = rawPrereq.uppercased()
                if let regex = prereqCodeRegex {
                    let nsRange = NSRange(upper.startIndex..., in: upper)
                    for match in regex.matches(in: upper, range: nsRange) {
                        if let r1 = Range(match.range(at: 1), in: upper) {
                            let code = String(upper[r1])
                            if code != nodeCode && validSubjectCodes.contains(code) && candidateSet.insert(code).inserted {
                                candidatePrereqs.append(code)
                            }
                        }
                    }
                }
                if let hoursRegex = minHoursRegex {
                    let nsRange = NSRange(rawPrereq.startIndex..., in: rawPrereq)
                    for match in hoursRegex.matches(in: rawPrereq, range: nsRange) {
                        if let r1 = Range(match.range(at: 1), in: rawPrereq),
                           let parsedHours = Int(rawPrereq[r1]), parsedHours > minHoursRequired {
                            minHoursRequired = parsedHours
                        }
                    }
                }
            }

            var acceptedForNode = acceptedPrereqs[nodeCode] ?? []
            for prereq in candidatePrereqs {
                if !canReach(from: prereq, target: nodeCode) {
                    acceptedForNode.append(prereq)
                }
            }
            acceptedPrereqs[nodeCode] = acceptedForNode

            nodes.append(
                DynamicCourseNode(
                    code: nodeCode,
                    name: subject.name.trimmingCharacters(in: .whitespacesAndNewlines),
                    workloadHours: workloadHours,
                    period: item.period,
                    prerequisites: acceptedForNode,
                    minHoursRequired: minHoursRequired,
                    isElective: false
                )
            )
        }

        // Sintetiza blocos de optativas (OPT001..) e eletivas (ELT001..) distribuídos nos períodos finais
        var usedNodeCodes = Set(nodes.map(\.code))
        var synthesizedSpecs: [(code: String, title: String, hours: Int)] = []

        if course.chOptional > 0 {
            let blocks = splitWorkloadIntoSlots(course.chOptional)
            var nextNum = 1
            for (idx, hours) in blocks.enumerated() {
                var code = ""
                repeat {
                    code = String(format: "OPT%03d", nextNum)
                    nextNum += 1
                } while usedNodeCodes.contains(code)
                usedNodeCodes.insert(code)
                synthesizedSpecs.append((code, "Disciplina Optativa \(romanOrDecimal(idx + 1))", hours))
            }
        }

        if course.chElective > 0 {
            let blocks = splitWorkloadIntoSlots(course.chElective)
            var nextNum = 1
            for (idx, hours) in blocks.enumerated() {
                var code = ""
                repeat {
                    code = String(format: "ELT%03d", nextNum)
                    nextNum += 1
                } while usedNodeCodes.contains(code)
                usedNodeCodes.insert(code)
                synthesizedSpecs.append((code, "Disciplina Eletiva \(romanOrDecimal(idx + 1))", hours))
            }
        }

        if !synthesizedSpecs.isEmpty {
            let windowSize: Int = {
                if synthesizedSpecs.count <= 2 { return min(2, totalPeriods) }
                if synthesizedSpecs.count <= 6 { return min(3, totalPeriods) }
                return min(4, totalPeriods)
            }()
            let startPeriod = max(1, totalPeriods - windowSize + 1)
            for (index, spec) in synthesizedSpecs.enumerated() {
                let rawPeriod = startPeriod + (index * windowSize) / synthesizedSpecs.count
                let targetPeriod = min(totalPeriods, max(1, rawPeriod))
                nodes.append(
                    DynamicCourseNode(
                        code: spec.code,
                        name: spec.title,
                        workloadHours: spec.hours,
                        period: targetPeriod,
                        isElective: true
                    )
                )
            }
        }

        let computedGridHours = nodes.reduce(0) { $0 + $1.workloadHours }
        let requiredActivitiesHours: Int
        let totalCourseHours: Int
        if course.chTotal > computedGridHours {
            if course.chActivities > 0 && computedGridHours + course.chActivities == course.chTotal {
                requiredActivitiesHours = course.chActivities
            } else {
                requiredActivitiesHours = course.chTotal - computedGridHours
            }
            totalCourseHours = computedGridHours + requiredActivitiesHours
        } else {
            requiredActivitiesHours = max(0, course.chActivities)
            totalCourseHours = computedGridHours + requiredActivitiesHours
        }

        let versionTerm = parseSemesterTerm(course.curriculumVersion)
        let versionSlug: String = {
            if let versionTerm {
                return "\(versionTerm.year)_\(versionTerm.semester)"
            }
            let slug = normalizeLookupText(course.curriculumVersion).replacingOccurrences(of: " ", with: "_")
            return slug.isEmpty ? "2026_2" : slug
        }()
        let matrixId = "ufop_\(cleanCourseCode.lowercased())_\(versionSlug)"
        let curriculumLabel = formatCurriculumLabel(course.curriculumVersion)
        let effectiveFrom: DynamicSemesterTerm = {
            if let versionTerm, versionTerm.year < course.effectiveFromYear {
                return DynamicSemesterTerm(year: versionTerm.year, semester: versionTerm.semester)
            }
            return DynamicSemesterTerm(year: course.effectiveFromYear, semester: course.effectiveFromSemester)
        }()
        let effectiveUntil: DynamicSemesterTerm? = course.effectiveUntilYear.map { untilYear in
            DynamicSemesterTerm(year: untilYear, semester: course.effectiveUntilSemester ?? 2)
        }

        return DynamicCurriculumMatrix(
            id: matrixId,
            courseName: cleanCourseName,
            curriculumLabel: curriculumLabel,
            totalPeriods: totalPeriods,
            requiredActivitiesHours: requiredActivitiesHours,
            totalCourseHours: totalCourseHours,
            courses: nodes,
            courseCode: cleanCourseCode,
            courseAliases: buildCourseAliases(courseCode: cleanCourseCode, courseName: cleanCourseName),
            effectiveFrom: effectiveFrom,
            effectiveUntil: effectiveUntil,
            campus: course.campus.trimmingCharacters(in: .whitespacesAndNewlines)
        )
    }

    public static func buildAllMatrices(from catalogCourses: [UfopCourseCurriculum]) -> [DynamicCurriculumMatrix] {
        guard !catalogCourses.isEmpty else { return curatedMatrices }
        let curatedCodes = Set(
            curatedMatrices
                .map { $0.courseCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() }
                .filter { !$0.isEmpty }
        )

        let generated = catalogCourses.compactMap { course -> DynamicCurriculumMatrix? in
            let code = course.code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            guard !curatedCodes.contains(code) else { return nil }
            return fromCourseCurriculum(course)
        }

        return (curatedMatrices + generated).sorted { lhs, rhs in
            let lName = normalizeLookupText(lhs.courseName)
            let rName = normalizeLookupText(rhs.courseName)
            if lName != rName { return lName < rName }
            return lhs.effectiveFrom < rhs.effectiveFrom
        }
    }

    public static func find(
        byId id: String?,
        in matrices: [DynamicCurriculumMatrix] = allMatrices
    ) -> DynamicCurriculumMatrix? {
        guard let clean = id?.trimmingCharacters(in: .whitespacesAndNewlines), !clean.isEmpty else {
            return nil
        }
        return matrices.first { matrix in
            matrix.id.caseInsensitiveCompare(clean) == .orderedSame ||
                matrix.legacyIds.contains { $0.caseInsensitiveCompare(clean) == .orderedSame }
        }
    }

    public static func searchMatrices(
        _ matrices: [DynamicCurriculumMatrix],
        query: String
    ) -> [DynamicCurriculumMatrix] {
        let normQuery = normalizeLookupText(query)
        guard !normQuery.isEmpty else { return matrices }
        let tokens = normQuery.split(separator: " ").map(String.init).filter { !$0.isEmpty }

        return matrices.compactMap { matrix -> (matrix: DynamicCurriculumMatrix, score: Int)? in
            let normCode = normalizeLookupText(matrix.courseCode)
            let normName = normalizeLookupText(matrix.courseName)
            let normCampus = normalizeLookupText(matrix.campus)
            let normLabel = normalizeLookupText(matrix.curriculumLabel)
            let normAliases = matrix.courseAliases.map { normalizeLookupText($0) }.joined(separator: " ")
            let haystack = "\(normName) \(normCode) \(normCampus) \(normLabel) \(normAliases)"

            let score: Int = {
                if normCode == normQuery { return 1000 }
                if normName.hasPrefix(normQuery) { return 800 }
                if normCode.hasPrefix(normQuery) { return 750 }
                if normName.contains(normQuery) { return 600 }
                if normCampus.contains(normQuery) { return 450 }
                if !tokens.isEmpty && tokens.allSatisfy({ haystack.contains($0) }) { return 350 }
                return 0
            }()
            return score > 0 ? (matrix, score) : nil
        }
        .sorted { lhs, rhs in
            if lhs.score != rhs.score { return lhs.score > rhs.score }
            let lName = normalizeLookupText(lhs.matrix.courseName)
            let rName = normalizeLookupText(rhs.matrix.courseName)
            if lName != rName { return lName < rName }
            return lhs.matrix.effectiveFrom < rhs.matrix.effectiveFrom
        }
        .map(\.matrix)
    }

    public static func loadCompletedCodesWithLegacyFallback(
        matrix: DynamicCurriculumMatrix,
        reader: (String) -> Set<String>
    ) -> Set<String> {
        let primary = reader(matrix.id)
        if !primary.isEmpty { return primary }
        for legacyId in matrix.legacyIds {
            let legacy = reader(legacyId)
            if !legacy.isEmpty { return legacy }
        }
        return []
    }

    public static func resolveMatrixForStudent(
        courseName: String,
        registrationNumber: String,
        earliestTranscriptSemester: String? = nil,
        catalogCourses: [UfopCourseCurriculum] = UfopCurriculumCatalog.allCourses,
        campusHint: String? = nil
    ) -> DynamicMatrixResolution {
        let regTuple = UfopCurriculumCatalog.parseEnrollmentYearSemester(registrationNumber)
        let txTuple = earliestTranscriptSemester.flatMap { parseSemesterTerm($0) }

        let entryTuple: (year: Int, semester: Int)? = {
            switch (regTuple, txTuple) {
            case let (.some(a), .some(b)):
                return (a.year, a.semester) <= (b.year, b.semester) ? a : b
            case let (.some(a), .none):
                return a
            case let (.none, .some(b)):
                return b
            case (.none, .none):
                return nil
            }
        }()
        let entryTerm = entryTuple.map { DynamicSemesterTerm(year: $0.year, semester: $0.semester) }

        let matrices = buildAllMatrices(from: catalogCourses)
        let trimmedCourseName = courseName.trimmingCharacters(in: .whitespacesAndNewlines)
        let matchedCatalog: UfopCourseCurriculum? = (!catalogCourses.isEmpty && !trimmedCourseName.isEmpty)
            ? UfopCurriculumCatalog.findCourseExactOrBest(in: catalogCourses, query: trimmedCourseName, campusHint: campusHint)
            : nil

        let courseMatrices: [DynamicCurriculumMatrix]
        if let matchedCatalog {
            let byCatalogCode = matrices.filter {
                $0.courseCode.caseInsensitiveCompare(matchedCatalog.code) == .orderedSame
            }
            if byCatalogCode.isEmpty {
                return DynamicMatrixResolution(
                    matrix: defaultMatrix,
                    studentEntryTerm: entryTerm,
                    isCourseMatched: false,
                    isExactVersionMatch: false,
                    note: "O detalhamento curricular de \(matchedCatalog.name) ainda está pendente no catálogo. Escolha outro curso no seletor acima para explorar."
                )
            }
            courseMatrices = byCatalogCode
        } else {
            courseMatrices = findMatrices(forCourseName: courseName, in: matrices)
        }

        guard !courseMatrices.isEmpty else {
            return DynamicMatrixResolution(
                matrix: defaultMatrix,
                studentEntryTerm: entryTerm,
                isCourseMatched: false,
                isExactVersionMatch: false,
                note: trimmedCourseName.isEmpty ? nil : "Matriz de \(trimmedCourseName) ainda não cadastrada na Grade Dinâmica"
            )
        }

        if let entryTerm {
            let exactMatches = courseMatrices.filter { matrix in
                matrix.effectiveFrom <= entryTerm &&
                    (matrix.effectiveUntil.map { entryTerm <= $0 } ?? true)
            }
            if let bestExact = exactMatches.max(by: { $0.effectiveFrom < $1.effectiveFrom }) {
                return DynamicMatrixResolution(
                    matrix: bestExact,
                    studentEntryTerm: entryTerm,
                    isCourseMatched: true,
                    isExactVersionMatch: true,
                    note: "Ingresso \(entryTerm.formatLabel()) · Selecionada automaticamente"
                )
            }

            if let oldest = courseMatrices.min(by: { $0.effectiveFrom < $1.effectiveFrom }),
               entryTerm < oldest.effectiveFrom {
                return DynamicMatrixResolution(
                    matrix: oldest,
                    studentEntryTerm: entryTerm,
                    isCourseMatched: true,
                    isExactVersionMatch: false,
                    note: "Ingresso \(entryTerm.formatLabel()) · Matriz antiga não cadastrada; exibindo \(oldest.curriculumLabel)"
                )
            }
        }

        let latest = courseMatrices.first(where: { $0.effectiveUntil == nil })
            ?? courseMatrices.max(by: { $0.effectiveFrom < $1.effectiveFrom })
            ?? defaultMatrix

        return DynamicMatrixResolution(
            matrix: latest,
            studentEntryTerm: entryTerm,
            isCourseMatched: true,
            isExactVersionMatch: entryTerm != nil,
            note: entryTerm.map { "Ingresso \($0.formatLabel()) · \(latest.curriculumLabel)" }
        )
    }

    private static func findMatrices(
        forCourseName courseName: String,
        in matrices: [DynamicCurriculumMatrix]
    ) -> [DynamicCurriculumMatrix] {
        let normInput = normalizeLookupText(courseName)
        guard !normInput.isEmpty else { return [] }

        return matrices.filter { matrix in
            let normCourse = normalizeLookupText(matrix.courseName)
            let normCode = normalizeLookupText(matrix.courseCode)
            let normAliases = matrix.courseAliases.map { normalizeLookupText($0) }
            if normInput == normCode || normInput == normCourse {
                return true
            }
            return normAliases.contains { alias in
                !alias.isEmpty && (
                    normInput == alias ||
                        normInput.contains(alias) ||
                        (normInput.count >= 6 && alias.contains(normInput))
                )
            }
        }
    }

    private static func splitWorkloadIntoSlots(_ totalHours: Int) -> [Int] {
        guard totalHours > 0 else { return [] }
        var slots: [Int] = []
        var remaining = totalHours
        while remaining >= 60 {
            slots.append(60)
            remaining -= 60
        }
        if remaining > 0 {
            slots.append(remaining)
        }
        return slots
    }

    private static func romanOrDecimal(_ n: Int) -> String {
        switch n {
        case 1: return "I"
        case 2: return "II"
        case 3: return "III"
        case 4: return "IV"
        case 5: return "V"
        case 6: return "VI"
        case 7: return "VII"
        case 8: return "VIII"
        case 9: return "IX"
        case 10: return "X"
        case 11: return "XI"
        case 12: return "XII"
        case 13: return "XIII"
        case 14: return "XIV"
        case 15: return "XV"
        case 16: return "XVI"
        case 17: return "XVII"
        case 18: return "XVIII"
        case 19: return "XIX"
        case 20: return "XX"
        default: return "\(n)"
        }
    }

    private static func formatCurriculumLabel(_ rawVersion: String) -> String {
        let trimmed = rawVersion.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "2026/2" }
        let formatted = trimmed.replacingOccurrences(
            of: #"\(\s*curr[ií]culo\s+(\d+)\s*\)"#,
            with: "· Currículo $1",
            options: [.regularExpression, .caseInsensitive]
        )
        return formatted
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func buildCourseAliases(courseCode: String, courseName: String) -> [String] {
        var aliases: [String] = []
        var seen: Set<String> = []
        func appendUnique(_ item: String) {
            let clean = item.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !clean.isEmpty else { return }
            if seen.insert(clean.lowercased()).inserted {
                aliases.append(clean)
            }
        }
        appendUnique(courseCode)
        appendUnique(courseName)
        let baseName = courseName
            .replacingOccurrences(of: #"\s*\([^)]*\)"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        appendUnique(baseName)
        if courseName.lowercased().hasPrefix("engenharia ") {
            let suffix = String(courseName.dropFirst("engenharia ".count)).trimmingCharacters(in: .whitespacesAndNewlines)
            appendUnique("eng. \(suffix)")
        }
        if baseName.lowercased().hasPrefix("engenharia ") {
            let suffix = String(baseName.dropFirst("engenharia ".count)).trimmingCharacters(in: .whitespacesAndNewlines)
            appendUnique("eng. \(suffix)")
        }
        return aliases
    }

    public static func parseSemesterTerm(_ raw: String) -> (year: Int, semester: Int)? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let pattern = #"(?:^|\D)(\d{4}|\d{2})\s*[./-]\s*([12])(?:\D|$)"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)),
              let r1 = Range(match.range(at: 1), in: trimmed),
              let r2 = Range(match.range(at: 2), in: trimmed),
              let rawYearInt = Int(trimmed[r1]),
              let sem = Int(trimmed[r2]), (sem == 1 || sem == 2) else {
            return nil
        }
        let yearStr = String(trimmed[r1])
        let fullYear = yearStr.count == 4 ? rawYearInt : (rawYearInt >= 69 ? 1900 + rawYearInt : 2000 + rawYearInt)
        guard (1969...2099).contains(fullYear) else { return nil }
        return (fullYear, sem)
    }

    private static func normalizeLookupText(_ raw: String) -> String {
        let folded = raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "pt_BR"))
            .lowercased()
            .replacingOccurrences(of: "eng.", with: "engenharia")
        let parts = folded
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
        return parts.joined(separator: " ")
    }
}

// MARK: - Dynamic Grid Engine

public enum DynamicGridEngine {
    private static func normalizedSet(_ codes: Set<String>) -> Set<String> {
        Set(
            codes
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() }
                .filter { !$0.isEmpty }
        )
    }

    public static func completedHours(
        matrix: DynamicCurriculumMatrix,
        completedCodes: Set<String>
    ) -> Int {
        let normalized = normalizedSet(completedCodes)
        return matrix.courses.reduce(0) { sum, course in
            normalized.contains(course.code.uppercased()) ? sum + course.workloadHours : sum
        }
    }

    public static func missingPrerequisites(
        for course: DynamicCourseNode,
        completedCodes: Set<String>
    ) -> [String] {
        let normalized = normalizedSet(completedCodes)
        return course.prerequisites.filter { !normalized.contains($0.uppercased()) }
    }

    public static func status(
        for course: DynamicCourseNode,
        matrix: DynamicCurriculumMatrix,
        completedCodes: Set<String>
    ) -> DynamicCourseStatus {
        let sanitized = sanitizeCompleted(matrix: matrix, completedCodes: completedCodes)
        if sanitized.contains(course.code) {
            return .completed
        }
        let missingPrereqs = missingPrerequisites(for: course, completedCodes: sanitized)
        let currentHours = completedHours(matrix: matrix, completedCodes: sanitized)
        if missingPrereqs.isEmpty && currentHours >= course.minHoursRequired {
            return .available
        }
        return .locked
    }

    public static func sanitizeCompleted(
        matrix: DynamicCurriculumMatrix,
        completedCodes: Set<String>
    ) -> Set<String> {
        let normalizedInput = normalizedSet(completedCodes)
        var validSet = Set(
            matrix.courses
                .filter { normalizedInput.contains($0.code.uppercased()) }
                .map(\.code)
        )

        var didPrune = true
        while didPrune {
            didPrune = false
            let currentHours = completedHours(matrix: matrix, completedCodes: validSet)
            for course in matrix.courses where validSet.contains(course.code) {
                let hasMissingPrereq = course.prerequisites.contains { !validSet.contains($0) }
                let hoursExcludingSelf = max(0, currentHours - course.workloadHours)
                let lacksHours = course.minHoursRequired > hoursExcludingSelf
                if hasMissingPrereq || lacksHours {
                    validSet.remove(course.code)
                    didPrune = true
                }
            }
        }
        return validSet
    }

    public static func toggleCourse(
        matrix: DynamicCurriculumMatrix,
        courseCode: String,
        completedCodes: Set<String>
    ) -> DynamicGridToggleResult {
        let cleanCode = courseCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let sanitized = sanitizeCompleted(matrix: matrix, completedCodes: completedCodes)

        guard let course = matrix.course(forCode: cleanCode) else {
            return DynamicGridToggleResult(
                updatedCompletedCodes: sanitized,
                action: .noChange,
                affectedCode: cleanCode,
                cascadeRemovedCodes: [],
                missingPrerequisites: [],
                missingHours: 0
            )
        }

        if sanitized.contains(course.code) {
            var remaining = sanitized
            remaining.remove(course.code)

            var cascadeSet: Set<String> = []
            var didRemove = true
            while didRemove {
                didRemove = false
                let currentHours = completedHours(matrix: matrix, completedCodes: remaining)
                for candidate in matrix.courses where remaining.contains(candidate.code) {
                    let missingPrereq = candidate.prerequisites.contains { !remaining.contains($0) }
                    let hoursExcludingSelf = max(0, currentHours - candidate.workloadHours)
                    let lacksHours = candidate.minHoursRequired > hoursExcludingSelf
                    if missingPrereq || lacksHours {
                        remaining.remove(candidate.code)
                        cascadeSet.insert(candidate.code)
                        didRemove = true
                    }
                }
            }

            let orderedCascade = matrix.courses
                .map(\.code)
                .filter { cascadeSet.contains($0) }

            return DynamicGridToggleResult(
                updatedCompletedCodes: remaining,
                action: .unmarkedWithCascade,
                affectedCode: course.code,
                cascadeRemovedCodes: orderedCascade,
                missingPrerequisites: [],
                missingHours: 0
            )
        }

        let missingPrereqs = missingPrerequisites(for: course, completedCodes: sanitized)
        let currentHours = completedHours(matrix: matrix, completedCodes: sanitized)
        let missingHours = max(0, course.minHoursRequired - currentHours)

        if !missingPrereqs.isEmpty || missingHours > 0 {
            return DynamicGridToggleResult(
                updatedCompletedCodes: sanitized,
                action: .blockedByPrerequisites,
                affectedCode: course.code,
                cascadeRemovedCodes: [],
                missingPrerequisites: missingPrereqs,
                missingHours: missingHours
            )
        }

        var updated = sanitized
        updated.insert(course.code)
        return DynamicGridToggleResult(
            updatedCompletedCodes: updated,
            action: .markedCompleted,
            affectedCode: course.code,
            cascadeRemovedCodes: [],
            missingPrerequisites: [],
            missingHours: 0
        )
    }

    public static func summarize(
        matrix: DynamicCurriculumMatrix,
        completedCodes: Set<String>
    ) -> DynamicGridSummary {
        let sanitized = sanitizeCompleted(matrix: matrix, completedCodes: completedCodes)
        let completedHrs = completedHours(matrix: matrix, completedCodes: sanitized)
        let totalHrs = matrix.totalGridHours
        let percent: Int = {
            guard totalHrs > 0 else { return 0 }
            let raw = (Double(completedHrs) * 100.0 / Double(totalHrs)).rounded()
            return min(100, max(0, Int(raw)))
        }()

        return DynamicGridSummary(
            completedHours: completedHrs,
            totalGridHours: totalHrs,
            completionPercent: percent,
            completedCount: sanitized.count,
            totalCount: matrix.courses.count
        )
    }
}
