import SwiftUI
import UniformTypeIdentifiers

private struct SimulatedExtraSubject: Identifiable {
    let id = UUID()
    let name: String
    let ch: Int
    let grade: Double
}

public struct CurriculumMatrixView: View {
    @Bindable public var store: CampusStore
    public let onClose: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedCourseCode: String = ""
    @State private var searchQuery: String = ""
    @State private var showTranscriptPicker: Bool = false
    @State private var importError: String?
    @State private var simulatedExtras: [SimulatedExtraSubject] = []
    @State private var newSimCh: Int = 60
    @State private var newSimGrade: Double = 8.0

    public init(store: CampusStore, onClose: @escaping () -> Void) {
        self.store = store
        self.onClose = onClose
    }

    private var selectedCourse: UfopCourseCurriculum? {
        let courses = UfopCurriculumCatalog.allCourses
        if let exact = courses.first(where: { $0.code == selectedCourseCode }) {
            return exact
        }
        return UfopCurriculumCatalog.findCourse(matching: store.studentProfile.courseName) ?? courses.first
    }

    public var body: some View {
        let accent = appearance.accentColor.primary(for: colorScheme)
        let hc = appearance.accessibility.highContrastMode
        let course = selectedCourse
        let creReport = store.getAcademicCreReport(
            extraCourses: simulatedExtras.map { ($0.ch, $0.grade) }
        )

        ZStack {
            LiquidGlassBackdrop(accent: appearance.accentColor, highContrast: hc)

            VStack(spacing: 0) {
                topBar(accent: accent, highContrast: hc)
                    .padding(.horizontal, 20)
                    .padding(.top, 18)
                    .padding(.bottom, 10)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 16) {
                        if let importError {
                            Text(importError)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Color(hex: 0xEF4444))
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: 0xEF4444).opacity(0.14)))
                        }

                        courseSelectorCard(course: course, accent: accent, highContrast: hc)

                        if let course {
                            legacyCompatibilityBanner(for: course, highContrast: hc)
                        }

                        creAndSimulatorCard(report: creReport, accent: accent, highContrast: hc)

                        if let course {
                            subjectsListSection(course: course, accent: accent, highContrast: hc)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 30)
                }
            }
        }
        .onAppear {
            if selectedCourseCode.isEmpty {
                selectedCourseCode = UfopCurriculumCatalog.findCourse(matching: store.studentProfile.courseName)?.code
                    ?? UfopCurriculumCatalog.allCourses.first?.code
                    ?? ""
            }
        }
        .fileImporter(
            isPresented: $showTranscriptPicker,
            allowedContentTypes: [.pdf],
            allowsMultipleSelection: false
        ) { result in
            importError = nil
            do {
                guard let url = try result.get().first else { return }
                let text = try SecurePdfProcessor.extractTextAndDeleteTemp(from: url)
                let parsed = try UfopPdfParsers.parseTranscript(text: text)
                _ = store.applyTranscriptImport(parsed)
            } catch {
                importError = error.localizedDescription
            }
        }
    }

    private func topBar(accent: Color, highContrast: Bool) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("MATRIZ CURRICULAR · UFOP")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .tracking(1.3)
                    .foregroundStyle(accent)
                Text("Catálogo de Cursos & CRE")
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
            }

            Spacer()

            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                    .frame(width: 34, height: 34)
                    .liquidGlassCapsule(selected: false, accent: accent, highContrast: highContrast)
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(appearance.accessibility.minTouchHeight)
        }
    }

    private func courseSelectorCard(course: UfopCourseCurriculum?, accent: Color, highContrast: Bool) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Curso selecionado")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                Spacer()
                Text("\(UfopCurriculumCatalog.allCourses.count) cursos UFOP")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundStyle(accent)
            }

            Picker("Curso UFOP", selection: $selectedCourseCode) {
                ForEach(UfopCurriculumCatalog.allCourses) { c in
                    Text("\(c.name) · \(c.campus)").tag(c.code)
                }
            }
            .pickerStyle(.menu)
            .tint(accent)

            if let course {
                Text("\(course.degree) · \(course.curriculumVersion) · \(course.chTotal)h totais")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            }
        }
        .padding(16)
        .liquidGlassCard(tint: accent, highContrast: highContrast)
    }

    @ViewBuilder
    private func legacyCompatibilityBanner(for course: UfopCourseCurriculum, highContrast: Bool) -> some View {
        let earliestTx = store.transcriptEntries.map(\.semester).sorted().first
        let txCodes = Set(store.transcriptEntries.map(\.code))
        let status = UfopCurriculumCatalog.checkCompatibility(
            registrationNumber: store.studentProfile.registrationNumber,
            earliestTranscriptSemester: earliestTx,
            transcriptCourseCodes: txCodes,
            course: course
        )

        if status.isLegacyMatrixMismatch {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 16))
                    .foregroundStyle(Color(hex: 0xF59E0B))
                    .padding(.top, 2)

                VStack(alignment: .leading, spacing: 3) {
                    Text("Atenção: Matriz Curricular Legada (< 2026/1)")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                    Text("Ingresso detectado (\(status.studentEntryLabel ?? "anterior")) pode seguir PPC anterior ao currículo de referência \(status.matrixVersionLabel). Algumas disciplinas e pré-requisitos podem divergir.")
                        .font(.system(size: 12))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                }
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color(hex: 0xF59E0B).opacity(0.14)))
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color(hex: 0xF59E0B).opacity(0.4), lineWidth: 1))
        }
    }

    private func creAndSimulatorCard(report: AcademicCreReport, accent: Color, highContrast: Bool) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Rendimento (CRE)")
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                    Text("\(report.completedHours)h / \(report.totalRequiredHours)h integralizadas · \(report.totalApproved) aprov.")
                        .font(.system(size: 12))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                }

                Spacer()

                Text(String(format: "%.2f", report.overallCre))
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                    .foregroundStyle(accent)
            }

            HStack(spacing: 10) {
                Button {
                    showTranscriptPicker = true
                } label: {
                    Label("Importar Histórico (PDF)", systemImage: "doc.text.fill")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(accent)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .liquidGlassCapsule(selected: false, accent: accent, highContrast: highContrast)
                }
                .buttonStyle(.plain)

                Button {
                    simulatedExtras.append(
                        SimulatedExtraSubject(
                            name: "Simulada \(simulatedExtras.count + 1)",
                            ch: newSimCh,
                            grade: newSimGrade
                        )
                    )
                } label: {
                    Label("+ Simular nota", systemImage: "slider.horizontal.3")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.mathBlue)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .liquidGlassCapsule(selected: false, accent: CampusTheme.mathBlue, highContrast: highContrast)
                }
                .buttonStyle(.plain)
            }

            if !simulatedExtras.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(simulatedExtras) { sim in
                        HStack {
                            Text("\(sim.name) · \(sim.ch)h · Nota \(String(format: "%.1f", sim.grade))")
                                .font(.system(size: 12, weight: .medium))
                            Spacer()
                            Button(role: .destructive) {
                                simulatedExtras.removeAll { $0.id == sim.id }
                            } label: {
                                Image(systemName: "trash")
                                    .font(.system(size: 11))
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(RoundedRectangle(cornerRadius: 8).fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: highContrast)))
                    }
                }
            }
        }
        .padding(16)
        .liquidGlassCard(tint: accent, highContrast: highContrast)
    }

    private func subjectsListSection(course: UfopCourseCurriculum, accent: Color, highContrast: Bool) -> some View {
        let needle = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let filtered = course.subjects.filter {
            needle.isEmpty || $0.code.lowercased().contains(needle) || $0.name.lowercased().contains(needle)
        }
        let periods = Array(Set(filtered.map(\.period))).sorted { lhs, rhs in
            if lhs == 0 { return false }
            if rhs == 0 { return true }
            return lhs < rhs
        }

        let enrolledCodes = Set(store.filteredDisciplines.map { $0.code.uppercased() })
        let approvedCodes = Set(store.transcriptEntries.filter { ["AP", "AE", "DP"].contains($0.status) }.map { $0.code.uppercased() })

        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
                TextField("Filtrar código ou disciplina na matriz", text: $searchQuery)
                    .font(.system(size: 14))
            }
            .padding(12)
            .liquidGlassCard(cornerRadius: 14, highContrast: highContrast)

            ForEach(periods, id: \.self) { period in
                let items = filtered.filter { $0.period == period }
                VStack(alignment: .leading, spacing: 8) {
                    Text(period == 0 ? "Atividades Complementares / Estágio / TCC" : "\(period)º Período")
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .foregroundStyle(accent)

                    ForEach(items) { subject in
                        let codeUpper = subject.code.uppercased()
                        let statusBadge: (String, Color) = {
                            if approvedCodes.contains(codeUpper) { return ("Concluída", CampusTheme.algebraGreen) }
                            if enrolledCodes.contains(codeUpper) { return ("Cursando", CampusTheme.mathBlue) }
                            return ("\(subject.chs)h", CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                        }()

                        HStack(alignment: .top, spacing: 10) {
                            VStack(alignment: .leading, spacing: 3) {
                                Text("\(subject.code) · \(subject.name)")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                                if !subject.prerequisites.isEmpty {
                                    Text("Pré-req.: \(subject.prerequisites.joined(separator: ", "))")
                                        .font(.system(size: 11))
                                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
                                }
                            }

                            Spacer()

                            Text(statusBadge.0)
                                .font(.system(size: 11, weight: .bold, design: .rounded))
                                .foregroundStyle(statusBadge.1)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Capsule().fill(statusBadge.1.opacity(0.14)))
                        }
                        .padding(12)
                        .liquidGlassCard(cornerRadius: 14, highContrast: highContrast)
                    }
                }
            }
        }
    }
}
