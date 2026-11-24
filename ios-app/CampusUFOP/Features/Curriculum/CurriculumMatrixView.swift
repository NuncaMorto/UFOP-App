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
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.openURL) private var openURL

    @State private var selectedCourseCode: String = ""
    @State private var selectedCurriculumVersion: String = ""
    @State private var searchQuery: String = ""
    @State private var showTranscriptPicker: Bool = false
    @State private var showLegacyReferencesExpanded: Bool = false
    @State private var importError: String?
    @State private var simulatedExtras: [SimulatedExtraSubject] = []
    @State private var newSimCh: Int = 60
    @State private var newSimGrade: Double = 8.0

    public init(store: CampusStore, onClose: @escaping () -> Void) {
        self.store = store
        self.onClose = onClose
    }

    private var isRegular: Bool {
        horizontalSizeClass == .regular
    }

    private var earliestTranscriptSemester: String? {
        store.transcriptEntries
            .map { $0.semester.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .min { lhs, rhs in
                let l = DynamicCurriculumCatalog.parseSemesterTerm(lhs)
                let r = DynamicCurriculumCatalog.parseSemesterTerm(rhs)
                switch (l, r) {
                case let (.some(a), .some(b)):
                    return (a.year, a.semester) < (b.year, b.semester)
                case (.some, .none):
                    return true
                case (.none, .some):
                    return false
                case (.none, .none):
                    return lhs < rhs
                }
            }
    }

    private var baseCourse: UfopCourseCurriculum? {
        let courses = UfopCurriculumCatalog.allCourses
        if let exact = courses.first(where: { $0.code == selectedCourseCode }) {
            return exact
        }
        return UfopCurriculumCatalog.findCourse(matching: store.studentProfile.courseName) ?? courses.first
    }

    private var availableVersions: [UfopCourseCurriculum] {
        guard let baseCourse else { return [] }
        return UfopCurriculumCatalog.courseVersions(for: baseCourse)
    }

    private var selectedCourse: UfopCourseCurriculum? {
        guard let baseCourse else { return nil }
        if !selectedCurriculumVersion.isEmpty,
           let matched = availableVersions.first(where: { $0.curriculumVersion == selectedCurriculumVersion }) {
            return matched
        }
        return UfopCurriculumCatalog.resolveCourseForStudent(
            baseCourse: baseCourse,
            registrationNumber: store.studentProfile.registrationNumber,
            earliestTranscriptSemester: earliestTranscriptSemester
        )
    }

    private func resolveInitialVersion(for course: UfopCourseCurriculum?) {
        guard let course else {
            selectedCurriculumVersion = ""
            return
        }
        let resolved = UfopCurriculumCatalog.resolveCourseForStudent(
            baseCourse: course,
            registrationNumber: store.studentProfile.registrationNumber,
            earliestTranscriptSemester: earliestTranscriptSemester
        )
        selectedCurriculumVersion = resolved.curriculumVersion
    }

    private var studentEntryTerm: (year: Int, semester: Int)? {
        let regEntry = UfopCurriculumCatalog.parseEnrollmentYearSemester(store.studentProfile.registrationNumber)
        let txEntry = earliestTranscriptSemester.flatMap { DynamicCurriculumCatalog.parseSemesterTerm($0) }
        switch (regEntry, txEntry) {
        case let (.some(a), .some(b)):
            return (a.year, a.semester) <= (b.year, b.semester) ? a : b
        case let (.some(a), .none):
            return a
        case let (.none, .some(b)):
            return b
        case (.none, .none):
            return nil
        }
    }

    private func compatibilityStatus(for course: UfopCourseCurriculum) -> CurriculumCompatibilityStatus {
        let earliestTx = earliestTranscriptSemester
        let txCodes = Set(store.transcriptEntries.map(\.code))
        return UfopCurriculumCatalog.checkCompatibility(
            registrationNumber: store.studentProfile.registrationNumber,
            earliestTranscriptSemester: earliestTx,
            transcriptCourseCodes: txCodes,
            course: course
        )
    }

    private func openExternalUrl(_ rawUrl: String) {
        let trimmed = rawUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty,
              let url = URL(string: trimmed),
              let scheme = url.scheme?.lowercased(),
              scheme == "http" || scheme == "https" else {
            return
        }
        openURL(url)
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
                    .campusReadableWidth(isRegular ? 960 : .infinity)

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
                            let status = compatibilityStatus(for: course)
                            if status.isLegacyMatrixMismatch {
                                legacyCompatibilityBanner(status: status, for: course, highContrast: hc)
                            }
                            if !course.subjects.isEmpty {
                                legacyReferencesSection(
                                    for: course,
                                    isLegacyMismatch: status.isLegacyMatrixMismatch,
                                    accent: accent,
                                    highContrast: hc
                                )
                            }
                        }

                        creAndSimulatorCard(report: creReport, accent: accent, highContrast: hc)

                        if let course {
                            subjectsListSection(course: course, accent: accent, highContrast: hc)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 30)
                    .campusReadableWidth(isRegular ? 960 : .infinity)
                }
            }
        }
        .onAppear {
            if selectedCourseCode.isEmpty {
                let matched = UfopCurriculumCatalog.findCourse(matching: store.studentProfile.courseName)
                    ?? UfopCurriculumCatalog.allCourses.first
                selectedCourseCode = matched?.code ?? ""
                resolveInitialVersion(for: matched)
            } else if selectedCurriculumVersion.isEmpty {
                resolveInitialVersion(for: baseCourse)
            }
        }
        .onChange(of: selectedCourseCode) { _, newCode in
            let matched = UfopCurriculumCatalog.allCourses.first(where: { $0.code == newCode })
            showLegacyReferencesExpanded = false
            resolveInitialVersion(for: matched)
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
                resolveInitialVersion(for: baseCourse)
            } catch {
                importError = error.localizedDescription
            }
        }
    }

    private func topBar(accent: Color, highContrast: Bool) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("MATRIZ CURRICULAR · GRADUAÇÃO")
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
                Text("\(UfopCurriculumCatalog.allCourses.count) cursos no catálogo")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundStyle(accent)
            }

            Picker("Curso de graduação", selection: $selectedCourseCode) {
                ForEach(UfopCurriculumCatalog.allCourses) { c in
                    Text("\(c.name) · \(c.campus)").tag(c.code)
                }
            }
            .pickerStyle(.menu)
            .tint(accent)

            if availableVersions.count > 1 {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Versão da matriz curricular")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(availableVersions, id: \.curriculumVersion) { version in
                                let isSelected = course?.curriculumVersion == version.curriculumVersion
                                Button {
                                    withAnimation(.snappy) {
                                        selectedCurriculumVersion = version.curriculumVersion
                                    }
                                } label: {
                                    let versionLabel = version.curriculumVersion.isEmpty
                                        ? "\(version.effectiveFromYear)/\(version.effectiveFromSemester)"
                                        : version.curriculumVersion
                                    Text(versionLabel)
                                        .font(.system(size: 12, weight: .bold, design: .rounded))
                                        .foregroundStyle(
                                            isSelected
                                                ? appearance.accentColor.onPrimary(for: colorScheme)
                                                : CampusTheme.primaryText(for: colorScheme, highContrast: highContrast)
                                        )
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 7)
                                        .liquidGlassCapsule(selected: isSelected, accent: accent, interactive: true, highContrast: highContrast)
                                }
                                .buttonStyle(.plain)
                                .campusHoverLift()
                                .minimumTouchTarget(appearance.accessibility.minTouchHeight)
                            }
                        }
                    }
                }
            }

            if let course {
                let versionText = course.curriculumVersion.isEmpty
                    ? "\(course.effectiveFromYear)/\(course.effectiveFromSemester)"
                    : course.curriculumVersion
                Text("\(course.degree) · \(versionText) · \(course.chTotal)h totais")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            }
        }
        .padding(16)
        .liquidGlassCard(tint: accent, highContrast: highContrast)
    }

    private func legacyCompatibilityBanner(
        status: CurriculumCompatibilityStatus,
        for course: UfopCourseCurriculum,
        highContrast: Bool
    ) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 16))
                .foregroundStyle(Color(hex: 0xF59E0B))
                .padding(.top, 2)

            VStack(alignment: .leading, spacing: 3) {
                Text("Atenção: Matriz Curricular Legada (< \(course.effectiveFromYear)/\(course.effectiveFromSemester))")
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

    @ViewBuilder
    private func legacyReferencesSection(
        for course: UfopCourseCurriculum,
        isLegacyMismatch: Bool,
        accent: Color,
        highContrast: Bool
    ) -> some View {
        let allRefs = UfopCurriculumCatalog.legacyReferences(for: course.code)
        let entryRefs: [UfopLegacyReference] = {
            if isLegacyMismatch, let entry = studentEntryTerm {
                let filtered = UfopCurriculumCatalog.legacyReferences(
                    for: course.code,
                    entryYear: entry.year,
                    entrySemester: entry.semester
                )
                return filtered.isEmpty ? allRefs : filtered
            }
            return allRefs
        }()
        let officialLinks = UfopCurriculumCatalog.officialLinks(for: course.code)
        let hasOfficialUrl = !(officialLinks?.officialMatrixUrl ?? "").isEmpty || !(officialLinks?.drivePdfUrl ?? "").isEmpty

        if !entryRefs.isEmpty || (isLegacyMismatch && hasOfficialUrl) {
            let expanded = isLegacyMismatch || showLegacyReferencesExpanded
            VStack(alignment: .leading, spacing: 10) {
                Button {
                    if !isLegacyMismatch {
                        withAnimation(.snappy) {
                            showLegacyReferencesExpanded.toggle()
                        }
                    }
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Matrizes anteriores e PPCs oficiais")
                                .font(.system(size: 14, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                            Text(
                                entryRefs.isEmpty
                                    ? "Links oficiais do curso na UFOP"
                                    : "\(entryRefs.count) referência(s) oficial(is) para consulta"
                            )
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                        }
                        Spacer()
                        if !isLegacyMismatch {
                            Image(systemName: expanded ? "chevron.up" : "chevron.down")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                        }
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(appearance.accessibility.minTouchHeight)

                if expanded {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(entryRefs) { ref in
                            legacyReferenceRow(ref, accent: accent, highContrast: highContrast)
                        }

                        if let matrixUrl = officialLinks?.officialMatrixUrl, !matrixUrl.isEmpty {
                            Button {
                                openExternalUrl(matrixUrl)
                            } label: {
                                Label("Portal oficial UFOP", systemImage: "safari")
                                    .font(.system(size: 12, weight: .bold, design: .rounded))
                                    .foregroundStyle(accent)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .liquidGlassCapsule(selected: false, accent: accent, highContrast: highContrast)
                            }
                            .buttonStyle(.plain)
                            .minimumTouchTarget(appearance.accessibility.minTouchHeight)
                        }
                    }
                }
            }
            .padding(14)
            .liquidGlassCard(cornerRadius: 16, tint: isLegacyMismatch ? Color(hex: 0xF59E0B) : accent, highContrast: highContrast)
        }
    }

    private func legacyReferenceRow(
        _ ref: UfopLegacyReference,
        accent: Color,
        highContrast: Bool
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top, spacing: 8) {
                Text(ref.versionTitle)
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                Spacer(minLength: 6)
                if !ref.accessType.isEmpty {
                    Text(ref.accessType)
                        .font(.system(size: 10, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.accessibleTextColor(accent, scheme: colorScheme))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(accent.opacity(0.14)))
                }
            }

            if !ref.entryRuleSummary.isEmpty {
                Text(ref.entryRuleSummary)
                    .font(.system(size: 12))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            }

            Button {
                openExternalUrl(ref.url)
            } label: {
                Label("Abrir oficial", systemImage: "arrow.up.right.square")
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .foregroundStyle(accent)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .liquidGlassCapsule(selected: false, accent: accent, highContrast: highContrast)
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(appearance.accessibility.minTouchHeight)
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: highContrast))
        )
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
                        .foregroundStyle(CampusTheme.accessibleTextColor(accent, scheme: colorScheme))
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .liquidGlassCapsule(selected: false, accent: accent, highContrast: highContrast)
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
        let approvedCodes = Set(store.transcriptEntries.filter { ["AP", "AE", "DE", "DJ"].contains($0.status) }.map { $0.code.uppercased() })
        let officialLinks = UfopCurriculumCatalog.officialLinks(for: course.code)
        let courseLegacyRefs = UfopCurriculumCatalog.legacyReferences(for: course.code)

        return VStack(alignment: .leading, spacing: 12) {
            if course.subjects.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 8) {
                        Image(systemName: "info.circle.fill")
                            .foregroundStyle(Color(hex: 0xF59E0B))
                        Text("Resumo 2026/2 · Detalhamento PDF pendente na fonte pública")
                            .font(.system(size: 14, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                    }

                    Text("As cargas horárias totais de \(course.name) já estão sincronizadas (\(course.chTotal)h totais). Você pode consultar a matriz completa diretamente nas páginas oficiais da UFOP.")
                        .font(.system(size: 12))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

                    if let matrixUrl = officialLinks?.officialMatrixUrl, !matrixUrl.isEmpty {
                        Button {
                            openExternalUrl(matrixUrl)
                        } label: {
                            Label("Abrir matriz oficial no navegador", systemImage: "safari")
                                .font(.system(size: 13, weight: .bold, design: .rounded))
                                .foregroundStyle(appearance.accentColor.onPrimary(for: colorScheme))
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .liquidGlassCapsule(selected: true, accent: accent, interactive: true, highContrast: highContrast)
                        }
                        .buttonStyle(.plain)
                        .minimumTouchTarget(appearance.accessibility.minTouchHeight)
                    }

                    if let driveUrl = officialLinks?.drivePdfUrl, !driveUrl.isEmpty {
                        Button {
                            openExternalUrl(driveUrl)
                        } label: {
                            Label("Abrir PDF oficial", systemImage: "doc.text")
                                .font(.system(size: 13, weight: .bold, design: .rounded))
                                .foregroundStyle(accent)
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .liquidGlassCapsule(selected: false, accent: accent, highContrast: highContrast)
                        }
                        .buttonStyle(.plain)
                        .minimumTouchTarget(appearance.accessibility.minTouchHeight)
                    }

                    ForEach(courseLegacyRefs) { ref in
                        legacyReferenceRow(ref, accent: accent, highContrast: highContrast)
                    }
                }
                .padding(16)
                .liquidGlassCard(tint: accent, highContrast: highContrast)
            } else {
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
}
