import SwiftUI

// MARK: - Profile Summary, Identity & Campus Cards

internal struct ProfileStudentIdentityCard: View {
    let profile: StudentProfile
    let summary: AcademicProfileSummary
    let primaryAccent: Color
    let onPrimaryAccent: Color
    let highContrast: Bool

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let displayName = profile.fullName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Estudante"
            : profile.fullName
        let initials = displayName
            .split(separator: " ")
            .prefix(2)
            .compactMap(\.first)
            .map { String($0).uppercased() }
            .joined()

        HStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(primaryAccent)
                    .frame(width: 54, height: 54)
                Text(initials.isEmpty ? "VA" : initials)
                    .font(.system(size: 19, weight: .bold, design: .rounded))
                    .foregroundStyle(onPrimaryAccent)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(displayName)
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                if !profile.courseName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text(profile.courseName)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                }

                HStack(spacing: 8) {
                    if !profile.registrationNumber.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        Text("Mat. \(profile.registrationNumber)")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                    }
                    Text("• \(summary.semester)")
                        .font(.system(size: 12, weight: .semibold, design: .rounded))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                }
            }

            Spacer(minLength: 0)
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 22, tint: primaryAccent, highContrast: highContrast)
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
                    .font(.system(size: 15, weight: .bold, design: .rounded))
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
                                .font(.system(size: 12, weight: .bold, design: .rounded))
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

internal struct ProfileSummarySectionView: View {
    @Bindable var store: CampusStore
    @Bindable var appearance: CampusAppearanceController
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

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let summary = store.academicProfileSummary
        let profile = store.studentProfile
        let creReport = store.getAcademicCreReport()

        if !isRegular {
            ProfileStudentIdentityCard(
                profile: profile,
                summary: summary,
                primaryAccent: primaryAccent,
                onPrimaryAccent: onPrimaryAccent,
                highContrast: highContrast
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

        metricsGrid(summary: summary)
        creAndTranscriptCard(creReport: creReport)
        dynamicGridShortcutCard
        shareScheduleCard
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
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            Text(value)
                .font(.system(size: 22, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
            Text(subtitle)
                .font(.system(size: 11))
                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .liquidGlassCard(cornerRadius: 18, tint: tint, highContrast: highContrast)
    }

    private func creAndTranscriptCard(creReport: AcademicCreReport) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Coeficiente de Rendimento (CRE)")
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                    Text("Integralizado: \(creReport.completedHours)h / \(creReport.totalRequiredHours)h · Aprovadas: \(creReport.totalApproved)")
                        .font(.system(size: 12))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                }
                Spacer()
                Text(String(format: "%.2f", creReport.overallCre))
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
            }

            if let transcriptMessage {
                Text(transcriptMessage)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
            }
            if let transcriptError {
                Text(transcriptError)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color(hex: 0xDC2626))
            }

            HStack(spacing: 10) {
                Button {
                    onImportTranscript()
                } label: {
                    Label("Histórico PDF", systemImage: "doc.badge.plus")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                        .frame(maxWidth: .infinity, minHeight: 42)
                        .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: highContrast)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)

                Button {
                    onOpenMatrix()
                } label: {
                    Label("Matriz & Simulador", systemImage: "slider.horizontal.3")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(onPrimaryAccent)
                        .frame(maxWidth: .infinity, minHeight: 42)
                        .liquidGlassCapsule(selected: true, accent: primaryAccent, interactive: true, highContrast: highContrast)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
            }
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, highContrast: highContrast)
    }

    private var resolvedDynamicMatrixResolution: DynamicMatrixResolution {
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

    private var dynamicGridShortcutCard: some View {
        let resolution = resolvedDynamicMatrixResolution
        let matrix = resolution.matrix
        let completed = store.dynamicGridCompletedSet(for: matrix.id)
        let gridSummary = DynamicGridEngine.summarize(matrix: matrix, completedCodes: completed)
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
                            .font(.system(size: 15, weight: .bold, design: .rounded))
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
                        .font(.system(size: 14, weight: .bold, design: .rounded))
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
            .liquidGlassCard(cornerRadius: 20, tint: primaryAccent, interactive: true, highContrast: highContrast)
        }
        .buttonStyle(.plain)
        .minimumTouchTarget(minTouch)
    }
}
