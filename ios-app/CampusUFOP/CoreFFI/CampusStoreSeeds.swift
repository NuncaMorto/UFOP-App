import Foundation

// MARK: - UFOP 2026.2 Seeds & Activity Sync

public extension CampusStore {
    // MARK: - UFOP 2026.2 Deterministic Seeds (PDF Spec + Sprint 2 Scale)

    public func seedUFOP2026_2Data() {
        self.selectedDepartmentFilter = "Todos os departamentos"
        self.selectedSemesterFilter = "2026.2"
        self.selectedTimetableFilter = "Minha grade"
        self.activeTemplateId = nil

        self.campuses = [
            UfopCampusInfo(
                id: "op_cruzeiro",
                name: "Morro do Cruzeiro",
                city: "Ouro Preto",
                weatherTempC: 22,
                weatherCondition: "Ensolarado",
                isActive: true
            ),
            UfopCampusInfo(
                id: "mar_ichs",
                name: "ICHS/ICSA",
                city: "Mariana",
                weatherTempC: 25,
                weatherCondition: "Parcialmente nublado",
                isActive: false
            ),
            UfopCampusInfo(
                id: "jm_icea",
                name: "ICEA",
                city: "João Monlevade",
                weatherTempC: 26,
                weatherCondition: "Ensolarado",
                isActive: false
            )
        ]

        self.calendarEvents = Self.officialCalendarEventsSeed()

        self.disciplines = []
        self.assessmentsByDiscipline = [:]
        self.notesByDiscipline = [:]
        self.agendaItemsByDate = [:]
        self.recordedStatusByAgendaItem = [:]
        self.absenceHistoryBySession = [:]
        self.timetableSlots = []
        self.courseScheduleSlots = []

        restoreDefault2026_2SeedData()
    }

    /// Sprint 4: Restaura o semestre padrão UFOP 2026.2 (`Cálculo II`, `Algoritmos`, `Álgebra Linear`, `Física I`),
    /// seus horários semanais, sessões da agenda e avaliações, preservando cursos de outros semestres.
    @discardableResult
    public func resetSemesterToDefaultSeed(semester: String = "2026.2") -> Int {
        let trimmed = semester.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanSemester = trimmed.isEmpty ? "2026.2" : trimmed

        let existingIds = disciplines
            .filter { $0.semester == cleanSemester }
            .map(\.id)
        for existingId in existingIds {
            deleteCourse(courseId: existingId)
        }

        if cleanSemester == "2026.2" {
            timetableSlots.removeAll { $0.id == "slot-ter-14" }
            restoreDefault2026_2SeedData()
        } else {
            selectedDepartmentFilter = "Todos os departamentos"
            selectedSemesterFilter = cleanSemester
            selectedTimetableFilter = "Minha grade"
            activeTemplateId = nil
            rebuildAcademicActivitiesFromAssessments()
        }

        return disciplines.filter { $0.semester == cleanSemester }.count
    }

    private func restoreDefault2026_2SeedData() {
        self.selectedDepartmentFilter = "Todos os departamentos"
        self.selectedSemesterFilter = "2026.2"
        self.selectedTimetableFilter = "Minha grade"
        self.activeTemplateId = nil

        let defaultDisciplines: [DisciplineSummaryCard] = [
            DisciplineSummaryCard(
                id: "calc-ii",
                code: "MAT202",
                name: "Cálculo II",
                department: "Matemática",
                semester: "2026.2",
                workloadHours: 60,
                currentAbsences: 4,
                maxAbsences: 18,
                cancelledClasses: 0,
                colorKey: .mathBlue,
                professor: "Prof.ª Ana Martins",
                location: "Prédio A · Sala 204"
            ),
            DisciplineSummaryCard(
                id: "algoritmos",
                code: "BCC201",
                name: "Algoritmos",
                department: "Computação",
                semester: "2026.2",
                workloadHours: 60,
                currentAbsences: 2,
                maxAbsences: 18,
                cancelledClasses: 0,
                colorKey: .compPurple,
                professor: "Prof. Carlos Eduardo",
                location: "ICEB · Lab 302"
            ),
            DisciplineSummaryCard(
                id: "algebra-linear",
                code: "MAT215",
                name: "Álgebra Linear",
                department: "Matemática",
                semester: "2026.2",
                workloadHours: 90,
                currentAbsences: 1,
                maxAbsences: 27,
                cancelledClasses: 0,
                colorKey: .algebraGreen,
                professor: "Prof. Roberto Menezes",
                location: "Prédio A · Sala 108"
            ),
            DisciplineSummaryCard(
                id: "fisica-i",
                code: "FIS101",
                name: "Física I",
                department: "Física",
                semester: "2026.2",
                workloadHours: 30,
                currentAbsences: 3,
                maxAbsences: 9,
                cancelledClasses: 0,
                colorKey: .physicsAmber,
                professor: "Prof.ª Helena Costa",
                location: "Bauxita · Pavilhão 105"
            )
        ]

        let defaultIds = Set(defaultDisciplines.map(\.id))
        self.disciplines.removeAll { defaultIds.contains($0.id) || $0.semester == "2026.2" }
        self.disciplines = defaultDisciplines + self.disciplines

        let defaultAssessments: [String: [AssessmentItem]] = [
            "calc-ii": [
                AssessmentItem(
                    id: "calc-ii-a1",
                    courseId: "calc-ii",
                    dayNumber: "20",
                    monthShort: "NOV",
                    title: "Lista de integrais",
                    subtitle: "Trabalho · 23:59",
                    kind: "Trabalho",
                    dueDate: "2026-11-20",
                    dueTime: "23:59",
                    completed: false
                ),
                AssessmentItem(
                    id: "calc-ii-a2",
                    courseId: "calc-ii",
                    dayNumber: "24",
                    monthShort: "NOV",
                    title: "Aplicações de integrais",
                    subtitle: "Seminário · 10:00",
                    kind: "Seminário",
                    dueDate: "2026-11-24",
                    dueTime: "10:00",
                    completed: false
                ),
                AssessmentItem(
                    id: "calc-ii-a3",
                    courseId: "calc-ii",
                    dayNumber: "27",
                    monthShort: "NOV",
                    title: "Prova 2",
                    subtitle: "Prova · 08:00",
                    kind: "Prova",
                    dueDate: "2026-11-27",
                    dueTime: "08:00",
                    completed: false
                )
            ],
            "algoritmos": [
                AssessmentItem(
                    id: "alg-a1",
                    courseId: "algoritmos",
                    dayNumber: "19",
                    monthShort: "NOV",
                    title: "Lab de Árvores Balanceadas",
                    subtitle: "Trabalho · 10:00",
                    kind: "Trabalho",
                    dueDate: "2026-11-19",
                    dueTime: "10:00",
                    completed: false
                ),
                AssessmentItem(
                    id: "alg-a2",
                    courseId: "algoritmos",
                    dayNumber: "26",
                    monthShort: "NOV",
                    title: "Trabalho Prático 2",
                    subtitle: "Trabalho · 23:59",
                    kind: "Trabalho",
                    dueDate: "2026-11-26",
                    dueTime: "23:59",
                    completed: false
                ),
                AssessmentItem(
                    id: "alg-a3",
                    courseId: "algoritmos",
                    dayNumber: "03",
                    monthShort: "DEZ",
                    title: "Prova Teórica 2",
                    subtitle: "Prova · 10:00",
                    kind: "Prova",
                    dueDate: "2026-12-03",
                    dueTime: "10:00",
                    completed: false
                )
            ],
            "algebra-linear": [
                AssessmentItem(
                    id: "alg-lin-a1",
                    courseId: "algebra-linear",
                    dayNumber: "21",
                    monthShort: "NOV",
                    title: "Lista de Transformações Lineares",
                    subtitle: "Trabalho · 18:00",
                    kind: "Trabalho",
                    dueDate: "2026-11-21",
                    dueTime: "18:00",
                    completed: false
                ),
                AssessmentItem(
                    id: "alg-lin-a2",
                    courseId: "algebra-linear",
                    dayNumber: "28",
                    monthShort: "NOV",
                    title: "Teste de Autovalores",
                    subtitle: "Seminário · 08:00",
                    kind: "Seminário",
                    dueDate: "2026-11-28",
                    dueTime: "08:00",
                    completed: false
                ),
                AssessmentItem(
                    id: "alg-lin-a3",
                    courseId: "algebra-linear",
                    dayNumber: "05",
                    monthShort: "DEZ",
                    title: "Prova 2",
                    subtitle: "Prova · 08:00",
                    kind: "Prova",
                    dueDate: "2026-12-05",
                    dueTime: "08:00",
                    completed: false
                )
            ],
            "fisica-i": [
                AssessmentItem(
                    id: "fis-a1",
                    courseId: "fisica-i",
                    dayNumber: "18",
                    monthShort: "NOV",
                    title: "Relatório de Conservação de Energia",
                    subtitle: "Trabalho · 14:00",
                    kind: "Trabalho",
                    dueDate: "2026-11-18",
                    dueTime: "14:00",
                    completed: false
                ),
                AssessmentItem(
                    id: "fis-a2",
                    courseId: "fisica-i",
                    dayNumber: "25",
                    monthShort: "NOV",
                    title: "Lista de Dinâmica Rotacional",
                    subtitle: "Trabalho · 18:00",
                    kind: "Trabalho",
                    dueDate: "2026-11-25",
                    dueTime: "18:00",
                    completed: false
                ),
                AssessmentItem(
                    id: "fis-a3",
                    courseId: "fisica-i",
                    dayNumber: "02",
                    monthShort: "DEZ",
                    title: "Prova 2",
                    subtitle: "Prova · 14:00",
                    kind: "Prova",
                    dueDate: "2026-12-02",
                    dueTime: "14:00",
                    completed: false
                )
            ]
        ]

        for (courseId, items) in defaultAssessments {
            self.assessmentsByDiscipline[courseId] = items
        }

        let defaultNotes: [String: String] = [
            "calc-ii": "Revisar integrais por partes.",
            "algoritmos": "Praticar complexidade assintótica e rotação de árvores AVL.",
            "algebra-linear": "Conferir teorema do núcleo e da imagem para a lista de sexta.",
            "fisica-i": "Levar papel milimetrado e calculadora científica para o laboratório."
        ]
        for (courseId, note) in defaultNotes {
            self.notesByDiscipline[courseId] = note
        }

        // Agenda da Semana 47 (16 a 22 de novembro de 2026)
        let defaultAgendaItemsByDate: [String: [AgendaTimelineItem]] = [
            "2026-11-16": [
                AgendaTimelineItem(
                    id: "seg-16-alg-linear",
                    disciplineId: "algebra-linear",
                    kind: .lecture,
                    startTime: "08:00",
                    endTime: "09:40",
                    title: "Álgebra Linear",
                    subtitle: "Prédio A · Sala 108",
                    location: "Prédio A · Sala 108",
                    professor: "Prof. Roberto Menezes",
                    department: "Matemática",
                    colorKey: .algebraGreen,
                    weatherTempC: 21,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                ),
                AgendaTimelineItem(
                    id: "seg-16-fisica",
                    disciplineId: "fisica-i",
                    kind: .lecture,
                    startTime: "10:00",
                    endTime: "11:40",
                    title: "Física I",
                    subtitle: "Bauxita · Pavilhão 105",
                    location: "Bauxita · Pavilhão 105",
                    professor: "Prof.ª Helena Costa",
                    department: "Física",
                    colorKey: .physicsAmber,
                    weatherTempC: 23,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                )
            ],
            "2026-11-17": [
                AgendaTimelineItem(
                    id: "ter-17-calc-ii",
                    disciplineId: "calc-ii",
                    kind: .lecture,
                    startTime: "08:00",
                    endTime: "09:40",
                    title: "Cálculo II",
                    subtitle: "Prédio A · Sala 204",
                    location: "Prédio A · Sala 204",
                    professor: "Prof.ª Ana Martins",
                    department: "Matemática",
                    colorKey: .mathBlue,
                    weatherTempC: 22,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                ),
                AgendaTimelineItem(
                    id: "ter-17-algoritmos",
                    disciplineId: "algoritmos",
                    kind: .lecture,
                    startTime: "10:00",
                    endTime: "11:40",
                    title: "Algoritmos",
                    subtitle: "ICEB · Lab 302",
                    location: "ICEB · Lab 302",
                    professor: "Prof. Carlos Eduardo",
                    department: "Computação",
                    colorKey: .compPurple,
                    weatherTempC: 24,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                ),
                AgendaTimelineItem(
                    id: "ter-17-monitoria",
                    disciplineId: nil,
                    kind: .commitment,
                    startTime: "14:00",
                    endTime: "15:00",
                    title: "Grupo de estudos · Cálculo II",
                    subtitle: "Biblioteca Central · Sala 03",
                    location: "Biblioteca Central · Sala 03",
                    professor: "Compromisso acadêmico",
                    department: "Compromisso",
                    colorKey: .algebraGreen,
                    weatherTempC: 26,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                )
            ],
            "2026-11-18": [
                AgendaTimelineItem(
                    id: "qua-18-alg-linear",
                    disciplineId: "algebra-linear",
                    kind: .lecture,
                    startTime: "08:00",
                    endTime: "09:40",
                    title: "Álgebra Linear",
                    subtitle: "Prédio A · Sala 108",
                    location: "Prédio A · Sala 108",
                    professor: "Prof. Roberto Menezes",
                    department: "Matemática",
                    colorKey: .algebraGreen,
                    weatherTempC: 22,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                ),
                AgendaTimelineItem(
                    id: "qua-18-fisica",
                    disciplineId: "fisica-i",
                    kind: .lecture,
                    startTime: "14:00",
                    endTime: "15:00",
                    title: "Física I",
                    subtitle: "Bauxita · Pavilhão 105",
                    location: "Bauxita · Pavilhão 105",
                    professor: "Prof.ª Helena Costa",
                    department: "Física",
                    colorKey: .physicsAmber,
                    weatherTempC: 25,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                )
            ],
            "2026-11-19": [
                AgendaTimelineItem(
                    id: "qui-19-calc-ii",
                    disciplineId: "calc-ii",
                    kind: .lecture,
                    startTime: "08:00",
                    endTime: "09:40",
                    title: "Cálculo II",
                    subtitle: "Prédio A · Sala 204",
                    location: "Prédio A · Sala 204",
                    professor: "Prof.ª Ana Martins",
                    department: "Matemática",
                    colorKey: .mathBlue,
                    weatherTempC: 22,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                ),
                AgendaTimelineItem(
                    id: "qui-19-algoritmos",
                    disciplineId: "algoritmos",
                    kind: .lecture,
                    startTime: "10:00",
                    endTime: "11:40",
                    title: "Algoritmos",
                    subtitle: "ICEB · Lab 302",
                    location: "ICEB · Lab 302",
                    professor: "Prof. Carlos Eduardo",
                    department: "Computação",
                    colorKey: .compPurple,
                    weatherTempC: 24,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                )
            ],
            "2026-11-20": [
                AgendaTimelineItem(
                    id: "sex-20-alg-linear",
                    disciplineId: "algebra-linear",
                    kind: .lecture,
                    startTime: "10:00",
                    endTime: "11:40",
                    title: "Álgebra Linear",
                    subtitle: "Prédio A · Sala 108",
                    location: "Prédio A · Sala 108",
                    professor: "Prof. Roberto Menezes",
                    department: "Matemática",
                    colorKey: .algebraGreen,
                    weatherTempC: 23,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                )
            ],
            "2026-11-21": [
                AgendaTimelineItem(
                    id: "sab-21-revisao",
                    disciplineId: nil,
                    kind: .commitment,
                    startTime: "08:00",
                    endTime: "09:40",
                    title: "Revisão · Lista de Integrais",
                    subtitle: "Centro de Convivência · UFOP",
                    location: "Centro de Convivência · UFOP",
                    professor: "Estudo autônomo",
                    department: "Compromisso",
                    colorKey: .mathBlue,
                    weatherTempC: 21,
                    weatherCondition: "Ensolarado",
                    attendanceStatus: .attending
                )
            ],
            "2026-11-22": []
        ]

        // Mescla e inicializa mapa de status registrados sem duplicar sessões
        for (dateIso, defaultItems) in defaultAgendaItemsByDate {
            var merged = self.agendaItemsByDate[dateIso] ?? []
            let restoredIds = Set(defaultItems.map(\.id))
            merged.removeAll { restoredIds.contains($0.id) }
            merged.append(contentsOf: defaultItems)
            merged.sort { $0.startTime < $1.startTime }
            self.agendaItemsByDate[dateIso] = merged

            for item in defaultItems {
                recordedStatusByAgendaItem[item.id] = item.attendanceStatus
                absenceHistoryBySession.removeValue(forKey: item.id)
            }
        }

        // Grade de horários semanal (SEG a SÁB × 08:00–09:40, 10:00–11:40, 14:00–15:00)
        let defaultTimetableSlots: [TimetableSlotCell] = [
            // 08:00–09:40
            TimetableSlotCell(
                id: "slot-seg-08",
                weekdayShort: "SEG",
                timeRange: "08:00–09:40",
                disciplineId: "algebra-linear",
                disciplineShortName: "Álgebra Linear",
                roomLabel: "Sala 108",
                colorKey: .algebraGreen
            ),
            TimetableSlotCell(
                id: "slot-ter-08",
                weekdayShort: "TER",
                timeRange: "08:00–09:40",
                disciplineId: "calc-ii",
                disciplineShortName: "Cálculo II",
                roomLabel: "Sala 204",
                colorKey: .mathBlue
            ),
            TimetableSlotCell(
                id: "slot-qua-08",
                weekdayShort: "QUA",
                timeRange: "08:00–09:40",
                disciplineId: "algebra-linear",
                disciplineShortName: "Álgebra Linear",
                roomLabel: "Sala 108",
                colorKey: .algebraGreen
            ),
            TimetableSlotCell(
                id: "slot-qui-08",
                weekdayShort: "QUI",
                timeRange: "08:00–09:40",
                disciplineId: "calc-ii",
                disciplineShortName: "Cálculo II",
                roomLabel: "Sala 204",
                colorKey: .mathBlue
            ),

            // 10:00–11:40
            TimetableSlotCell(
                id: "slot-seg-10",
                weekdayShort: "SEG",
                timeRange: "10:00–11:40",
                disciplineId: "fisica-i",
                disciplineShortName: "Física I",
                roomLabel: "Pav. 105",
                colorKey: .physicsAmber
            ),
            TimetableSlotCell(
                id: "slot-ter-10",
                weekdayShort: "TER",
                timeRange: "10:00–11:40",
                disciplineId: "algoritmos",
                disciplineShortName: "Algoritmos",
                roomLabel: "Lab 302",
                colorKey: .compPurple
            ),
            TimetableSlotCell(
                id: "slot-qui-10",
                weekdayShort: "QUI",
                timeRange: "10:00–11:40",
                disciplineId: "algoritmos",
                disciplineShortName: "Algoritmos",
                roomLabel: "Lab 302",
                colorKey: .compPurple
            ),
            TimetableSlotCell(
                id: "slot-sex-10",
                weekdayShort: "SEX",
                timeRange: "10:00–11:40",
                disciplineId: "algebra-linear",
                disciplineShortName: "Álgebra Linear",
                roomLabel: "Sala 108",
                colorKey: .algebraGreen
            ),

            // 14:00–15:00
            TimetableSlotCell(
                id: "slot-ter-14",
                weekdayShort: "TER",
                timeRange: "14:00–15:00",
                disciplineId: "calc-ii",
                disciplineShortName: "Monitoria Cálc. II",
                roomLabel: "Bibl. 03",
                colorKey: .mathBlue
            ),
            TimetableSlotCell(
                id: "slot-qua-14",
                weekdayShort: "QUA",
                timeRange: "14:00–15:00",
                disciplineId: "fisica-i",
                disciplineShortName: "Física I",
                roomLabel: "Pav. 105",
                colorKey: .physicsAmber
            )
        ]

        let restoredTimetableIds = Set(defaultTimetableSlots.map(\.id))
        self.timetableSlots.removeAll { restoredTimetableIds.contains($0.id) }
        self.timetableSlots = defaultTimetableSlots + self.timetableSlots

        let defaultCourseScheduleSlots: [CourseScheduleSlotItem] = [
            CourseScheduleSlotItem(
                slotId: "slot-seg-08",
                courseId: "algebra-linear",
                dayOfWeek: 1,
                dayLabel: "SEG",
                startTime: "08:00",
                endTime: "09:40",
                building: "Prédio A",
                room: "Sala 108",
                shortLocation: "Sala 108"
            ),
            CourseScheduleSlotItem(
                slotId: "slot-seg-10",
                courseId: "fisica-i",
                dayOfWeek: 1,
                dayLabel: "SEG",
                startTime: "10:00",
                endTime: "11:40",
                building: "Bauxita",
                room: "Pavilhão 105",
                shortLocation: "Pav. 105"
            ),
            CourseScheduleSlotItem(
                slotId: "slot-ter-08",
                courseId: "calc-ii",
                dayOfWeek: 2,
                dayLabel: "TER",
                startTime: "08:00",
                endTime: "09:40",
                building: "Prédio A",
                room: "Sala 204",
                shortLocation: "Sala 204"
            ),
            CourseScheduleSlotItem(
                slotId: "slot-ter-10",
                courseId: "algoritmos",
                dayOfWeek: 2,
                dayLabel: "TER",
                startTime: "10:00",
                endTime: "11:40",
                building: "ICEB",
                room: "Lab 302",
                shortLocation: "Lab 302"
            ),
            CourseScheduleSlotItem(
                slotId: "slot-qua-08",
                courseId: "algebra-linear",
                dayOfWeek: 3,
                dayLabel: "QUA",
                startTime: "08:00",
                endTime: "09:40",
                building: "Prédio A",
                room: "Sala 108",
                shortLocation: "Sala 108"
            ),
            CourseScheduleSlotItem(
                slotId: "slot-qua-14",
                courseId: "fisica-i",
                dayOfWeek: 3,
                dayLabel: "QUA",
                startTime: "14:00",
                endTime: "15:00",
                building: "Bauxita",
                room: "Pavilhão 105",
                shortLocation: "Pav. 105"
            ),
            CourseScheduleSlotItem(
                slotId: "slot-qui-08",
                courseId: "calc-ii",
                dayOfWeek: 4,
                dayLabel: "QUI",
                startTime: "08:00",
                endTime: "09:40",
                building: "Prédio A",
                room: "Sala 204",
                shortLocation: "Sala 204"
            ),
            CourseScheduleSlotItem(
                slotId: "slot-qui-10",
                courseId: "algoritmos",
                dayOfWeek: 4,
                dayLabel: "QUI",
                startTime: "10:00",
                endTime: "11:40",
                building: "ICEB",
                room: "Lab 302",
                shortLocation: "Lab 302"
            ),
            CourseScheduleSlotItem(
                slotId: "slot-sex-10",
                courseId: "algebra-linear",
                dayOfWeek: 5,
                dayLabel: "SEX",
                startTime: "10:00",
                endTime: "11:40",
                building: "Prédio A",
                room: "Sala 108",
                shortLocation: "Sala 108"
            )
        ]

        let restoredCourseSlotIds = Set(defaultCourseScheduleSlots.map(\.slotId))
        self.courseScheduleSlots.removeAll { restoredCourseSlotIds.contains($0.slotId) }
        self.courseScheduleSlots = defaultCourseScheduleSlots + self.courseScheduleSlots

        rebuildAcademicActivitiesFromAssessments()
    }

    // MARK: - Reactive Sync Between Assessments & Activities

    internal func rebuildAcademicActivitiesFromAssessments() {
        var combined: [AcademicActivityItem] = []

        for discipline in disciplines {
            let items = assessmentsByDiscipline[discipline.id] ?? []
            for item in items {
                let categoryText = item.subtitle.isEmpty
                    ? "\(item.kind) · \(item.dueTime)"
                    : item.subtitle
                combined.append(
                    AcademicActivityItem(
                        id: item.id,
                        disciplineId: discipline.id,
                        disciplineName: discipline.name,
                        department: discipline.department,
                        colorKey: discipline.colorKey,
                        dueDay: item.dayNumber,
                        dueMonth: item.monthShort,
                        title: item.title,
                        categoryLabel: categoryText,
                        kind: item.kind,
                        dueDate: item.dueDate,
                        dueTime: item.dueTime,
                        isCompleted: item.completed
                    )
                )
            }
        }

        combined.sort { lhs, rhs in
            if lhs.dueDate != rhs.dueDate {
                return lhs.dueDate < rhs.dueDate
            }
            return lhs.dueTime < rhs.dueTime
        }

        self.academicActivities = combined
    }

}
