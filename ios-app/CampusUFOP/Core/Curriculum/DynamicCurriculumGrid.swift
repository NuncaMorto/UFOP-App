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
        legacyIds: [String] = []
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
        legacyIds: ["eng_controle_automacao_2023"]
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
        effectiveUntil: nil
    )

    public static var engControleAutomacao2023: DynamicCurriculumMatrix {
        engControleAutomacao2024_1
    }

    public static let allMatrices: [DynamicCurriculumMatrix] = [
        engControleAutomacao2024_1,
        engControleAutomacao2026_1
    ]

    public static var defaultMatrix: DynamicCurriculumMatrix {
        engControleAutomacao2024_1
    }

    public static func find(byId id: String?) -> DynamicCurriculumMatrix? {
        guard let clean = id?.trimmingCharacters(in: .whitespacesAndNewlines), !clean.isEmpty else {
            return nil
        }
        return allMatrices.first { matrix in
            matrix.id.caseInsensitiveCompare(clean) == .orderedSame ||
                matrix.legacyIds.contains { $0.caseInsensitiveCompare(clean) == .orderedSame }
        }
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
        earliestTranscriptSemester: String? = nil
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

        let courseMatrices = findMatrices(forCourseName: courseName)
        guard !courseMatrices.isEmpty else {
            let cleanCourse = courseName.trimmingCharacters(in: .whitespacesAndNewlines)
            return DynamicMatrixResolution(
                matrix: defaultMatrix,
                studentEntryTerm: entryTerm,
                isCourseMatched: false,
                isExactVersionMatch: false,
                note: cleanCourse.isEmpty ? nil : "Matriz de \(cleanCourse) ainda não cadastrada na Grade Dinâmica"
            )
        }

        if let entryTerm {
            let exactMatches = courseMatrices.filter { matrix in
                matrix.effectiveFrom <= entryTerm &&
                    (matrix.effectiveUntil == nullOrFalse(entryTerm: entryTerm, until: matrix.effectiveUntil))
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

    private static func nullOrFalse(entryTerm: DynamicSemesterTerm, until: DynamicSemesterTerm?) -> Bool {
        guard let until else { return true }
        return entryTerm <= until
    }

    private static func findMatrices(forCourseName courseName: String) -> [DynamicCurriculumMatrix] {
        let normInput = normalizeLookupText(courseName)
        guard !normInput.isEmpty else { return [] }

        return allMatrices.filter { matrix in
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
                let lacksHours = course.minHoursRequired > currentHours
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
                    let lacksHours = candidate.minHoursRequired > currentHours
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
