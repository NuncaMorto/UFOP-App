import SwiftUI

// MARK: - Profile Summary, Identity & Campus Cards

internal struct ProfileStudentIdentityCard: View {
    let profile: StudentProfile
    let summary: AcademicProfileSummary
    let primaryAccent: Color
    let onPrimaryAccent: Color
    let highContrast: Bool
    var minTouch: CGFloat = 48
    var onEditProfile: (() -> Void)? = nil

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let displayName = profile.fullName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Estudante"
            : profile.fullName
        let courseLabel = profile.courseName.trimmingCharacters(in: .whitespacesAndNewlines)
        let regLabel = profile.registrationNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let initials = displayName
            .split(separator: " ")
            .prefix(2)
            .compactMap(\.first)
            .map { String($0).uppercased() }
            .joined()

        HStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(primaryAccent.opacity(colorScheme == .dark ? 0.28 : 0.16))
                    .frame(width: 58, height: 58)
                    .overlay(
                        Circle()
                            .strokeBorder(primaryAccent.opacity(0.35), lineWidth: 1.5)
                    )
                Text(initials.isEmpty ? "VA" : initials)
                    .font(CampusTheme.font(size: 19, weight: .bold))
                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(displayName)
                    .font(CampusTheme.font(size: 17, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                    .lineLimit(2)

                if !courseLabel.isEmpty {
                    Text(courseLabel)
                        .font(CampusTheme.font(size: 13, weight: .medium))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                        .lineLimit(2)
                }

                HStack(spacing: 6) {
                    if !regLabel.isEmpty {
                        Text("Mat. \(regLabel)")
                            .font(CampusTheme.tabularFont(size: 12, weight: .semibold))
                            .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                    }
                    Text("· \(summary.semester) · \(summary.activeCampus.city)")
                        .font(CampusTheme.tabularFont(size: 12, weight: .semibold))
                        .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                }
                .lineLimit(1)
            }

            Spacer(minLength: 4)

            if let onEditProfile {
                Button {
                    onEditProfile()
                } label: {
                    Label("Editar", systemImage: "pencil")
                        .font(CampusTheme.font(size: 12, weight: .bold))
                        .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                        .padding(.horizontal, 12)
                        .frame(minHeight: 36)
                        .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: highContrast)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
                .accessibilityLabel("Editar perfil acadêmico")
            }
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 24, tint: primaryAccent, highContrast: highContrast)
    }
}

internal struct ProfileCampusSelectorCard: View {
    @Bindable var store: CampusStore
    let summary: AcademicProfileSummary
    let primaryAccent: Color
    let onPrimaryAccent: Color
    let highContrast: Bool
    let minTouch: CGFloat

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Campus")
                    .font(CampusTheme.font(size: 15, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                Spacer()
                WeatherPillBadge(label: summary.activeCampus.weatherBadgeLabel)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(summary.campuses) { campus in
                        let isSelected = campus.id == summary.activeCampus.id
                        Button {
                            withAnimation(.snappy) {
                                _ = store.setActiveCampus(campusId: campus.id)
                            }
                        } label: {
                            Text(campus.city)
                                .font(CampusTheme.font(size: 12, weight: .bold))
                                .foregroundStyle(isSelected ? onPrimaryAccent : CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                                .padding(.horizontal, 14)
                                .frame(minHeight: 38)
                                .liquidGlassCapsule(selected: isSelected, accent: primaryAccent, interactive: true, highContrast: highContrast)
                        }
                        .buttonStyle(.plain)
                        .minimumTouchTarget(minTouch)
                    }
                }
            }

            Text(summary.activeCampus.fullLabel)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, highContrast: highContrast)
    }
}

private struct ProfileSummaryCacheKey: Equatable {
    let courseName: String
    let registrationNumber: String
    let campusName: String
    let transcriptEntries: [TranscriptCourseEntry]
    let dynamicGridCompletedByMatrix: [String: [String]]
}

internal struct ProfileSummarySectionView: View {
    @Bindable var store: CampusStore
    @Bindable var appearance: CampusAppearanceController
    let summary: AcademicProfileSummary
    let isRegular: Bool
    let primaryAccent: Color
    let onPrimaryAccent: Color
    let highContrast: Bool
    let minTouch: CGFloat
    let transcriptMessage: String?
    let transcriptError: String?
    let onImportTranscript: () -> Void
    let onOpenMatrix: () -> Void
    let onOpenDynamicGrid: () -> Void
    let onOpenShare: () -> Void
    var onEditProfile: () -> Void = {}

    @Environment(\.colorScheme) private var colorScheme

    @State private var cachedCreReport: AcademicCreReport = AcademicCreReport(
        overallCre: 0,
        totalRequiredHours: 3610,
        completedHours: 0,
        totalApproved: 0,
        totalFailedByGrade: 0,
        totalFailedByAbsence: 0,
        semesters: []
    )
    @State private var cachedResolution: DynamicMatrixResolution = DynamicMatrixResolution(
        matrix: DynamicCurriculumCatalog.defaultMatrix,
        studentEntryTerm: nil,
        isCourseMatched: false,
        isExactVersionMatch: false,
        note: nil
    )
    @State private var cachedGridSummary: DynamicGridSummary = DynamicGridSummary(
        completedHours: 0,
        totalGridHours: DynamicCurriculumCatalog.defaultMatrix.totalGridHours,
        completionPercent: 0,
        completedCount: 0,
        totalCount: DynamicCurriculumCatalog.defaultMatrix.courses.count
    )

    private var summaryCacheKey: ProfileSummaryCacheKey {
        ProfileSummaryCacheKey(
            courseName: store.studentProfile.courseName,
            registrationNumber: store.studentProfile.registrationNumber,
            campusName: store.studentProfile.campusName,
            transcriptEntries: store.transcriptEntries,
            dynamicGridCompletedByMatrix: store.dynamicGridCompletedByMatrix
        )
    }

    var body: some View {
        let profile = store.studentProfile

        Group {
            if !isRegular {
                ProfileStudentIdentityCard(
                    profile: profile,
                    summary: summary,
                    primaryAccent: primaryAccent,
                    onPrimaryAccent: onPrimaryAccent,
                    highContrast: highContrast,
                    minTouch: minTouch,
                    onEditProfile: onEditProfile
                )
                ProfileCampusSelectorCard(
                    store: store,
                    summary: summary,
                    primaryAccent: primaryAccent,
                    onPrimaryAccent: onPrimaryAccent,
                    highContrast: highContrast,
                    minTouch: minTouch
                )
            }

            bentoOverviewRow(summary: summary, creReport: cachedCreReport, gridSummary: cachedGridSummary)
            absenceRiskOrAttentionBanner(summary: summary)
            metricsGrid(summary: summary)
            creAndTranscriptCard(creReport: cachedCreReport)
            dynamicGridShortcutCard(
                resolution: cachedResolution,
                gridSummary: cachedGridSummary
            )
            shareScheduleCard
        }
        .task(id: summaryCacheKey) {
            recomputeCachedMetrics()
        }
    }

    private func recomputeCachedMetrics() {
        cachedCreReport = store.getAcademicCreReport()
        let resolution = computeDynamicMatrixResolution()
        let completed = store.dynamicGridCompletedSet(for: resolution.matrix.id)
        cachedResolution = resolution
        cachedGridSummary = DynamicGridEngine.summarize(
            matrix: resolution.matrix,
            completedCodes: completed,
            alreadySanitized: true
        )
    }

    private func bentoOverviewRow(
        summary: AcademicProfileSummary,
        creReport: AcademicCreReport,
        gridSummary: DynamicGridSummary
    ) -> some View {
        let isCreInProgressOnly = creReport.overallCre == 0
            && !creReport.semesters.isEmpty
            && creReport.semesters.allSatisfy { $0.approvedCount == 0 && $0.failedCount == 0 && $0.inProgressCount > 0 }
        let creValue = isCreInProgressOnly ? "Em curso" : String(format: "%.2f", creReport.overallCre)
        let integralPercent: Int = {
            if creReport.completedHours > 0 {
                return min(100, max(0, (creReport.completedHours * 100) / max(1, creReport.totalRequiredHours)))
            }
            return gridSummary.completionPercent
        }()
        let integralHours = creReport.completedHours > 0
            ? "\(creReport.completedHours)/\(creReport.totalRequiredHours)h"
            : "\(gridSummary.completedHours)/\(gridSummary.totalGridHours)h"

        let semesterDisciplines = store.disciplines.filter { $0.semester == summary.semester }
        let attentionCount = semesterDisciplines.filter { disc in
            guard disc.maxAbsences > 0 else { return false }
            let ratio = Double(disc.currentAbsences) / Double(disc.maxAbsences)
            return ratio >= 0.50 && ratio < 0.75
        }.count
        let freqStatus: String = {
            if summary.coursesAtRisk > 0 { return "\(summary.coursesAtRisk) em risco" }
            if attentionCount > 0 { return "\(attentionCount) em atenção" }
            return "Controlada"
        }()
        let freqTint: Color = summary.coursesAtRisk > 0
            ? Color(hex: colorScheme == .dark ? 0xEF9A9A : 0xC62828)
            : (attentionCount > 0
                ? Color(hex: colorScheme == .dark ? 0xFFB74D : 0xEF6C00)
                : Color(hex: colorScheme == .dark ? 0x81C784 : 0x2E7D32))

        return HStack(spacing: 10) {
            Button {
                onOpenMatrix()
            } label: {
                VStack(alignment: .leading, spacing: 4) {
                    Text("CRE Geral")
                        .font(CampusTheme.font(size: 11, weight: .semibold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                    Text(creValue)
                        .font(CampusTheme.tabularFont(size: isCreInProgressOnly ? 16 : 20, weight: .bold))
                        .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                        .lineLimit(1)
                    Text("\(creReport.totalApproved) aprov.")
                        .font(CampusTheme.tabularFont(size: 11, weight: .medium))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, minHeight: 74, alignment: .leading)
                .padding(12)
                .liquidGlassCard(cornerRadius: 24, tint: primaryAccent, interactive: true, highContrast: highContrast)
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)

            Button {
                onOpenDynamicGrid()
            } label: {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Integralizado")
                        .font(CampusTheme.font(size: 11, weight: .semibold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                    Text("\(integralPercent)%")
                        .font(CampusTheme.tabularFont(size: 20, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                        .lineLimit(1)
                    Text(integralHours)
                        .font(CampusTheme.tabularFont(size: 11, weight: .medium))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, minHeight: 74, alignment: .leading)
                .padding(12)
                .liquidGlassCard(cornerRadius: 24, tint: nil, interactive: true, highContrast: highContrast)
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)

            Button {
                onOpenMatrix()
            } label: {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Frequência")
                        .font(CampusTheme.font(size: 11, weight: .semibold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                    Text("\(summary.totalAbsences)/\(summary.totalMaxAbsences)")
                        .font(CampusTheme.tabularFont(size: 20, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                        .lineLimit(1)
                    Text(freqStatus)
                        .font(CampusTheme.font(size: 11, weight: .bold))
                        .foregroundStyle(freqTint)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, minHeight: 74, alignment: .leading)
                .padding(12)
                .liquidGlassCard(
                    cornerRadius: 24,
                    tint: summary.coursesAtRisk > 0 || attentionCount > 0 ? freqTint : nil,
                    interactive: true,
                    highContrast: highContrast
                )
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
        }
    }

    @ViewBuilder
    private func absenceRiskOrAttentionBanner(summary: AcademicProfileSummary) -> some View {
        let semesterDisciplines = store.disciplines.filter { $0.semester == summary.semester }
        let attentionCount = semesterDisciplines.filter { disc in
            guard disc.maxAbsences > 0 else { return false }
            let ratio = Double(disc.currentAbsences) / Double(disc.maxAbsences)
            return ratio >= 0.50 && ratio < 0.75
        }.count

        if summary.coursesAtRisk > 0 {
            let riskTint = Color(hex: colorScheme == .dark ? 0xEF9A9A : 0xC62828)
            HStack(spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(riskTint)
                Text("Alerta de risco: \(summary.coursesAtRisk) disciplina(s) atingiu(ram) >= 75% do limite máximo de faltas.")
                    .font(CampusTheme.font(size: 12.5, weight: .semibold))
                    .foregroundStyle(riskTint)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(riskTint.opacity(colorScheme == .dark ? 0.18 : 0.11))
            )
        } else if attentionCount > 0 {
            let amberTint = Color(hex: colorScheme == .dark ? 0xFFB74D : 0xEF6C00)
            HStack(spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(amberTint)
                Text("Atenção: \(attentionCount) disciplina(s) atingiu(ram) >= 50% do limite máximo de faltas.")
                    .font(CampusTheme.font(size: 12.5, weight: .semibold))
                    .foregroundStyle(amberTint)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(amberTint.opacity(colorScheme == .dark ? 0.18 : 0.12))
            )
        }
    }

    private func metricsGrid(summary: AcademicProfileSummary) -> some View {
        let columns = [GridItem(.adaptive(minimum: isRegular ? 180 : 150), spacing: 10)]
        return LazyVGrid(columns: columns, spacing: 10) {
            metricTile(title: "Disciplinas", value: "\(summary.totalCourses)", subtitle: "Em \(summary.semester)", tint: primaryAccent)
            metricTile(title: "Faltas acumuladas", value: "\(summary.totalAbsences)/\(summary.totalMaxAbsences)", subtitle: "\(summary.coursesAtRisk) em risco", tint: CampusTheme.mathBlue)
            metricTile(title: "Canceladas", value: "\(summary.totalCancelledClasses)", subtitle: "Não somam falta", tint: CampusTheme.compPurple)
            metricTile(title: "Avaliações", value: "\(summary.pendingAssessments)", subtitle: "\(summary.completedAssessments) concluídas", tint: CampusTheme.algebraGreen)
        }
    }

    private func metricTile(title: String, value: String, subtitle: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(CampusTheme.font(size: 11, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            Text(value)
                .font(CampusTheme.tabularFont(size: 22, weight: .bold))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
            Text(subtitle)
                .font(CampusTheme.tabularFont(size: 11, weight: .medium))
                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .liquidGlassCard(cornerRadius: 24, tint: tint, highContrast: highContrast)
    }

    private func creAndTranscriptCard(creReport: AcademicCreReport) -> some View {
        let isAllInProgressWithoutGrades = creReport.overallCre == 0
            && !creReport.semesters.isEmpty
            && creReport.semesters.allSatisfy { $0.approvedCount == 0 && $0.failedCount == 0 && $0.inProgressCount > 0 }

        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Coeficiente de Rendimento (CRE)")
                        .font(CampusTheme.font(size: 15, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                    Text("Integralizado: \(creReport.completedHours)h / \(creReport.totalRequiredHours)h · Aprovadas: \(creReport.totalApproved)")
                        .font(CampusTheme.tabularFont(size: 12, weight: .medium))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                }
                Spacer()
                if isAllInProgressWithoutGrades {
                    Text("Em curso")
                        .font(CampusTheme.font(size: 12, weight: .bold))
                        .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(
                            Capsule(style: .continuous)
                                .fill(primaryAccent.opacity(colorScheme == .dark ? 0.22 : 0.14))
                        )
                } else {
                    Text(String(format: "%.2f", creReport.overallCre))
                        .font(CampusTheme.tabularFont(size: 26, weight: .bold))
                        .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                }
            }

            if isAllInProgressWithoutGrades {
                Text("Semestre atual sem notas lançadas. Abra o simulador para projetar seu CRE.")
                    .font(CampusTheme.font(size: 12))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            }

            if let transcriptMessage {
                Text(transcriptMessage)
                    .font(CampusTheme.font(size: 12, weight: .semibold))
                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
            }
            if let transcriptError {
                Text(transcriptError)
                    .font(CampusTheme.font(size: 12, weight: .semibold))
                    .foregroundStyle(Color(hex: 0xDC2626))
            }

            HStack(spacing: 10) {
                Button {
                    onImportTranscript()
                } label: {
                    Label("Histórico PDF", systemImage: "doc.badge.plus")
                        .font(CampusTheme.font(size: 13, weight: .bold))
                        .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                        .frame(maxWidth: .infinity, minHeight: 42)
                        .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: highContrast)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)

                Button {
                    onOpenMatrix()
                } label: {
                    Label(isAllInProgressWithoutGrades ? "Simular CRE" : "Matriz & Simulador", systemImage: "slider.horizontal.3")
                        .font(CampusTheme.font(size: 13, weight: .bold))
                        .foregroundStyle(onPrimaryAccent)
                        .frame(maxWidth: .infinity, minHeight: 42)
                        .liquidGlassCapsule(selected: true, accent: primaryAccent, interactive: true, highContrast: highContrast)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
            }
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 24, tint: primaryAccent, highContrast: highContrast)
    }

    private func computeDynamicMatrixResolution() -> DynamicMatrixResolution {
        let earliestSemester = store.transcriptEntries
            .map { $0.semester.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .min { lhs, rhs in
                let l = DynamicCurriculumCatalog.parseSemesterTerm(lhs)
                let r = DynamicCurriculumCatalog.parseSemesterTerm(rhs)
                switch (l, r) {
                case let (.some(a), .some(b)): return (a.year, a.semester) < (b.year, b.semester)
                case (.some, .none): return true
                case (.none, .some): return false
                case (.none, .none): return lhs < rhs
                }
            }
        return DynamicCurriculumCatalog.resolveMatrixForStudent(
            courseName: store.studentProfile.courseName,
            registrationNumber: store.studentProfile.registrationNumber,
            earliestTranscriptSemester: earliestSemester,
            campusHint: store.studentProfile.campusName
        )
    }

    private func dynamicGridShortcutCard(
        resolution: DynamicMatrixResolution,
        gridSummary: DynamicGridSummary
    ) -> some View {
        let matrix = resolution.matrix
        let trimmedCourseName = store.studentProfile.courseName.trimmingCharacters(in: .whitespacesAndNewlines)
        let subtitleText: String = {
            if resolution.isCourseMatched {
                return "\(matrix.courseName) · \(matrix.curriculumLabel) · \(gridSummary.completedHours)/\(gridSummary.totalGridHours)h · \(gridSummary.completionPercent)%"
            }
            if !trimmedCourseName.isEmpty {
                return "Detalhamento pendente para \(trimmedCourseName) · Toque para explorar cursos"
            }
            return "\(matrix.courseName) · \(gridSummary.completedHours)/\(gridSummary.totalGridHours)h · \(gridSummary.completionPercent)%"
        }()

        return Button {
            onOpenDynamicGrid()
        } label: {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 12) {
                    Image(systemName: "square.grid.3x3.topleft.filled")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Grade Dinâmica")
                            .font(CampusTheme.font(size: 15, weight: .bold))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                        Text(subtitleText)
                            .font(.system(size: 12))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                            .lineLimit(2)
                    }

                    Spacer()

                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
                }

                GeometryReader { proxy in
                    ZStack(alignment: .leading) {
                        Capsule(style: .continuous)
                            .fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: highContrast))
                        Capsule(style: .continuous)
                            .fill(primaryAccent)
                            .frame(
                                width: max(
                                    gridSummary.completedHours > 0 ? 8 : 0,
                                    proxy.size.width * gridSummary.progressFraction
                                )
                            )
                    }
                }
                .frame(height: 6)
                .accessibilityHidden(true)
            }
            .padding(16)
            .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, interactive: true, highContrast: highContrast)
        }
        .buttonStyle(.plain)
        .minimumTouchTarget(minTouch)
        .accessibilityLabel("Grade Dinâmica, \(matrix.courseName), \(gridSummary.completedHours) de \(gridSummary.totalGridHours) horas, \(gridSummary.completionPercent) por cento concluído")
    }

    private var shareScheduleCard: some View {
        Button {
            onOpenShare()
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "square.and.arrow.up.on.square.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))

                VStack(alignment: .leading, spacing: 2) {
                    Text("Compartilhar ou importar grade (.ufopgrade)")
                        .font(CampusTheme.font(size: 14, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                    Text("Exporte para JSON/arquivo e envie para colegas da turma.")
                        .font(.system(size: 12))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
            }
            .padding(16)
            .liquidGlassCard(cornerRadius: 24, tint: primaryAccent, interactive: true, highContrast: highContrast)
        }
        .buttonStyle(.plain)
        .minimumTouchTarget(minTouch)
    }
}

// MARK: - Edit Profile Modal Sheet (iOS)

internal struct ProfileEditSheetView: View {
    @Bindable var store: CampusStore
    @Bindable var appearance: CampusAppearanceController
    let onDismiss: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    @State private var fullName: String
    @State private var registrationNumber: String
    @State private var courseName: String
    @State private var activeSemester: String
    @State private var selectedCampus: String

    init(
        store: CampusStore,
        appearance: CampusAppearanceController,
        onDismiss: @escaping () -> Void
    ) {
        self.store = store
        self.appearance = appearance
        self.onDismiss = onDismiss
        _fullName = State(initialValue: store.studentProfile.fullName)
        _registrationNumber = State(initialValue: store.studentProfile.registrationNumber)
        _courseName = State(initialValue: store.studentProfile.courseName)
        _activeSemester = State(initialValue: store.studentProfile.activeSemester.isEmpty ? "2026.2" : store.studentProfile.activeSemester)
        let savedCampus = store.studentProfile.campusName.trimmingCharacters(in: .whitespacesAndNewlines)
        _selectedCampus = State(initialValue: savedCampus.isEmpty ? store.activeCampus.city : savedCampus)
    }

    var body: some View {
        let hc = appearance.accessibility.highContrastMode
        let accent = appearance.accentColor.primary(for: colorScheme)
        let onPrimary = appearance.accentColor.onPrimary(for: colorScheme)
        let minTouch = appearance.accessibility.minTouchHeight

        NavigationStack {
            ZStack {
                LiquidGlassBackdrop(accent: appearance.accentColor, highContrast: hc)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 14) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Nome completo")
                                .font(CampusTheme.font(size: 13, weight: .semibold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            TextField("Ex.: Lucas Almeida", text: $fullName)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 10)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: hc))
                                )
                        }

                        VStack(alignment: .leading, spacing: 6) {
                            Text("Matrícula")
                                .font(CampusTheme.font(size: 13, weight: .semibold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            TextField("Ex.: 24.2.1165", text: $registrationNumber)
                                .font(CampusTheme.tabularFont(size: 14, weight: .medium))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 10)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: hc))
                                )
                        }

                        if !UfopCurriculumCatalog.allCourses.isEmpty {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Curso")
                                    .font(CampusTheme.font(size: 13, weight: .semibold))
                                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                Picker("Curso", selection: $courseName) {
                                    if !courseName.isEmpty && !UfopCurriculumCatalog.allCourses.contains(where: { $0.name == courseName }) {
                                        Text(courseName).tag(courseName)
                                    }
                                    ForEach(UfopCurriculumCatalog.allCourses) { course in
                                        Text("\(course.name) (\(course.campus))").tag(course.name)
                                    }
                                }
                                .pickerStyle(.menu)
                                .tint(accent)
                            }
                        } else {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Curso")
                                    .font(CampusTheme.font(size: 13, weight: .semibold))
                                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                TextField("Ex.: Ciência da Computação", text: $courseName)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 10)
                                    .background(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: hc))
                                    )
                            }
                        }

                        VStack(alignment: .leading, spacing: 6) {
                            Text("Semestre letivo")
                                .font(CampusTheme.font(size: 13, weight: .semibold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            Picker("Semestre", selection: $activeSemester) {
                                Text("2026.2").tag("2026.2")
                                Text("2027.1").tag("2027.1")
                                Text("2027.2").tag("2027.2")
                            }
                            .pickerStyle(.segmented)
                        }

                        VStack(alignment: .leading, spacing: 6) {
                            Text("Campus")
                                .font(CampusTheme.font(size: 13, weight: .semibold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            Picker("Campus", selection: $selectedCampus) {
                                Text("Ouro Preto").tag("Ouro Preto")
                                Text("Mariana").tag("Mariana")
                                Text("João Monlevade").tag("João Monlevade")
                            }
                            .pickerStyle(.segmented)
                        }

                        LiquidGlassPrimaryButton(
                            tint: accent,
                            foreground: onPrimary,
                            minHeight: minTouch
                        ) {
                            let cleanName = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
                            if !cleanName.isEmpty {
                                store.studentProfile.fullName = cleanName
                            }
                            store.studentProfile.registrationNumber = registrationNumber.trimmingCharacters(in: .whitespacesAndNewlines)
                            store.studentProfile.courseName = courseName.trimmingCharacters(in: .whitespacesAndNewlines)
                            store.studentProfile.activeSemester = activeSemester
                            store.studentProfile.campusName = selectedCampus
                            _ = store.setActiveCampus(campusId: selectedCampus)
                            store.persistEncryptedState()
                            onDismiss()
                        } label: {
                            Label("Salvar alterações", systemImage: "checkmark.circle.fill")
                                .font(CampusTheme.font(size: 15, weight: .bold))
                        }
                        .padding(.top, 8)
                    }
                    .padding(18)
                    .liquidGlassCard(cornerRadius: 24, tint: accent, highContrast: hc)
                    .padding(20)
                }
            }
            .navigationTitle("Editar perfil")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar", action: onDismiss)
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                }
            }
        }
    }
}
