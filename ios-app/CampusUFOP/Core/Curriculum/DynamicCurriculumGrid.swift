import Foundation

// MARK: - Curriculum Matrices Curated Catalog

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
}
