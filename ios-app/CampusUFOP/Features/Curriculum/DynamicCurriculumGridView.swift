import SwiftUI

private struct DynamicGridFeedbackBanner: Equatable {
    enum Kind: Equatable {
        case blocked
        case cascade
    }

    let kind: Kind
    let title: String
    let detail: String?
}

public struct DynamicCurriculumGridView: View {
    @Bindable public var store: CampusStore

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var selectedMatrixId: String = DynamicCurriculumCatalog.defaultMatrix.id
    @State private var didAutoResolveMatrix: Bool = false
    @State private var autoResolution: DynamicMatrixResolution?
    @State private var feedbackBanner: DynamicGridFeedbackBanner?
    @State private var showClearConfirmation: Bool = false
    @State private var isHeaderCollapsed: Bool = false

    private var isRegular: Bool { horizontalSizeClass == .regular }
    private var cardWidth: CGFloat { isRegular ? 156 : 136 }
    private var cardHeight: CGFloat { isRegular ? 120 : 112 }
    private var gridSpacing: CGFloat { isRegular ? 12 : 8 }

    public init(store: CampusStore) {
        self.store = store
    }

    private var resolvedAccent: CampusAccentColor { appearance?.accentColor ?? .green }
    private var primaryAccent: Color { resolvedAccent.primary(for: colorScheme) }
    private var onPrimaryAccent: Color { resolvedAccent.onPrimary(for: colorScheme) }
    private var hc: Bool { appearance?.accessibility.highContrastMode ?? false }
    private var reduceMotion: Bool { appearance?.accessibility.reduceMotion ?? false }
    private var minTouch: CGFloat { appearance?.accessibility.minTouchHeight ?? 48 }
    private var isVerticalOrientation: Bool { store.dynamicGridOrientation == "vertical" }

    private var activeMatrix: DynamicCurriculumMatrix {
        DynamicCurriculumCatalog.find(byId: selectedMatrixId) ?? DynamicCurriculumCatalog.defaultMatrix
    }

    // Accessible semantic colors for the 3 course states
    private var completedBaseColor: Color {
        colorScheme == .dark ? Color(hex: 0x34D399) : Color(hex: 0x15803D)
    }

    private var availableBaseColor: Color {
        colorScheme == .dark ? Color(hex: 0x60A5FA) : Color(hex: 0x1D4ED8)
    }

    private var lockedBaseColor: Color {
        colorScheme == .dark ? Color(hex: 0xFB7185) : Color(hex: 0xC2410C)
    }

    public var body: some View {
        let matrix = activeMatrix
        let completedSet = store.dynamicGridCompletedSet(for: matrix.id)
        let summary = DynamicGridEngine.summarize(matrix: matrix, completedCodes: completedSet)

        VStack(alignment: .leading, spacing: 10) {
            compactHeaderCard(matrix: matrix, summary: summary)
                .padding(.horizontal, 20)

            if let feedbackBanner {
                feedbackBannerView(feedbackBanner)
                    .padding(.horizontal, 20)
            }

            ScrollView([.horizontal, .vertical], showsIndicators: true) {
                gridContent(matrix: matrix, completedSet: completedSet)
                    .padding(.horizontal, 20)
                    .padding(.top, 2)
                    .padding(.bottom, 88)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .onAppear {
            if !didAutoResolveMatrix {
                didAutoResolveMatrix = true
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
                let resolution = DynamicCurriculumCatalog.resolveMatrixForStudent(
                    courseName: store.studentProfile.courseName,
                    registrationNumber: store.studentProfile.registrationNumber,
                    earliestTranscriptSemester: earliestSemester
                )
                selectedMatrixId = resolution.matrix.id
                autoResolution = resolution
            }
        }
        .confirmationDialog(
            "Limpar progresso da grade?",
            isPresented: $showClearConfirmation,
            titleVisibility: .visible
        ) {
            Button("Zerar todas as disciplinas", role: .destructive) {
                applyGridChange {
                    store.clearDynamicGrid(matrixId: matrix.id)
                    feedbackBanner = nil
                }
            }
            Button("Cancelar", role: .cancel) {}
        } message: {
            Text("Todas as disciplinas marcadas em \(matrix.courseName) serão desmarcadas.")
        }
    }

    // MARK: - Compact Liquid Glass Header Card

    private func compactHeaderCard(
        matrix: DynamicCurriculumMatrix,
        summary: DynamicGridSummary
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            if isHeaderCollapsed {
                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(summary.completedHours)/\(summary.totalGridHours)h (\(summary.completionPercent)%) · \(summary.completedCount)/\(summary.totalCount)")
                            .font(.system(size: 12, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .lineLimit(1)

                        GeometryReader { proxy in
                            ZStack(alignment: .leading) {
                                Capsule(style: .continuous)
                                    .fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: hc))

                                Capsule(style: .continuous)
                                    .fill(primaryAccent)
                                    .frame(
                                        width: max(
                                            summary.completedHours > 0 ? 6 : 0,
                                            proxy.size.width * summary.progressFraction
                                        )
                                    )
                            }
                        }
                        .frame(height: 5)
                        .accessibilityHidden(true)
                    }

                    Button {
                        applyGridChange {
                            isHeaderCollapsed = false
                        }
                    } label: {
                        Image(systemName: "chevron.down")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .frame(width: 40, height: 40)
                            .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: hc)
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(minTouch)
                    .accessibilityLabel("Expandir controles da grade dinâmica")
                }
            } else {
                HStack(spacing: 8) {
                    Menu {
                        ForEach(DynamicCurriculumCatalog.allMatrices) { item in
                            Button {
                                selectedMatrixId = item.id
                                feedbackBanner = nil
                            } label: {
                                if item.id == matrix.id {
                                    Label("\(item.courseName) · \(item.curriculumLabel)", systemImage: "checkmark")
                                } else {
                                    Text("\(item.courseName) · \(item.curriculumLabel)")
                                }
                            }
                        }
                    } label: {
                        HStack(spacing: 6) {
                            VStack(alignment: .leading, spacing: 1) {
                                Text(matrix.courseName)
                                    .font(.system(size: 14, weight: .bold, design: .rounded))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                    .lineLimit(1)

                                Text(matrix.curriculumLabel)
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                                    .lineLimit(1)
                            }

                            Spacer(minLength: 4)

                            Image(systemName: "chevron.down")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: hc)
                    }
                    .minimumTouchTarget(minTouch)
                    .accessibilityLabel("Selecionar matriz curricular, atual: \(matrix.courseName), \(matrix.curriculumLabel)")

                    Button {
                        applyGridChange {
                            isHeaderCollapsed = true
                        }
                    } label: {
                        Image(systemName: "chevron.up")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .frame(width: 40, height: 40)
                            .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: hc)
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(minTouch)
                    .accessibilityLabel("Recolher controles da grade dinâmica")
                }

                VStack(alignment: .leading, spacing: 5) {
                    Text("\(summary.completedHours)/\(summary.totalGridHours)h (\(summary.completionPercent)%) · \(summary.completedCount)/\(summary.totalCount) disciplinas")
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                    GeometryReader { proxy in
                        ZStack(alignment: .leading) {
                            Capsule(style: .continuous)
                                .fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: hc))

                            Capsule(style: .continuous)
                                .fill(primaryAccent)
                                .frame(
                                    width: max(
                                        summary.completedHours > 0 ? 8 : 0,
                                        proxy.size.width * summary.progressFraction
                                    )
                                )
                                .animation(
                                    reduceMotion ? nil : .spring(response: 0.3, dampingFraction: 0.85),
                                    value: summary.completedHours
                                )
                        }
                    }
                    .frame(height: 8)
                    .accessibilityHidden(true)

                    Text("Atividades/TCC/Estágio: \(matrix.requiredActivitiesHours)h · Total do curso: \(matrix.totalCourseHours)h")
                        .font(.system(size: 10.5, weight: .medium))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
                        .lineLimit(1)

                    if let resolution = autoResolution, resolution.matrix.id == matrix.id, let note = resolution.note {
                        Text(note)
                            .font(.system(size: 10.5, weight: .semibold))
                            .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                            .lineLimit(2)
                    }
                }

                LiquidGlassGroup(spacing: 8) {
                    HStack(spacing: 8) {
                        HStack(spacing: 3) {
                            orientationPill(key: "horizontal", label: "Horizontal", icon: "rectangle.split.3x1")
                            orientationPill(key: "vertical", label: "Vertical", icon: "rectangle.split.1x2")
                        }
                        .padding(3)
                        .frame(height: 38)
                        .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: false, highContrast: hc)

                        Spacer(minLength: 4)

                        Button {
                            showClearConfirmation = true
                        } label: {
                            Label("Limpar", systemImage: "trash")
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                                .foregroundStyle(
                                    summary.completedCount > 0
                                        ? CampusTheme.accessibleTextColor(lockedBaseColor, scheme: colorScheme)
                                        : CampusTheme.mutedText(for: colorScheme, highContrast: hc)
                                )
                                .padding(.horizontal, 14)
                                .frame(height: 38)
                                .liquidGlassCapsule(selected: false, accent: lockedBaseColor, interactive: summary.completedCount > 0, highContrast: hc)
                        }
                        .buttonStyle(.plain)
                        .disabled(summary.completedCount == 0)
                        .minimumTouchTarget(minTouch)
                        .accessibilityLabel("Limpar disciplinas marcadas na grade")
                    }
                }

                HStack(spacing: 12) {
                    legendDot(label: "Concluída", color: completedBaseColor, icon: "checkmark.circle.fill")
                    legendDot(label: "Disponível", color: availableBaseColor, icon: "circle")
                    legendDot(label: "Bloqueada", color: lockedBaseColor, icon: "lock.fill")
                    Spacer(minLength: 0)
                }
            }
        }
        .padding(isHeaderCollapsed ? 10 : 14)
        .liquidGlassCard(cornerRadius: isHeaderCollapsed ? 16 : 20, tint: primaryAccent, highContrast: hc)
    }

    private func orientationPill(key: String, label: String, icon: String) -> some View {
        let isSelected = (key == "vertical") ? isVerticalOrientation : !isVerticalOrientation

        return Button {
            applyGridChange {
                store.dynamicGridOrientation = key
            }
        } label: {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 11, weight: .bold))
                Text(label)
                    .font(.system(size: 11.5, weight: .bold, design: .rounded))
            }
            .foregroundStyle(
                isSelected
                    ? onPrimaryAccent
                    : CampusTheme.primaryText(for: colorScheme, highContrast: hc)
            )
            .padding(.horizontal, 12)
            .frame(height: 32)
            .background(
                Capsule(style: .continuous)
                    .fill(isSelected ? primaryAccent : Color.clear)
            )
        }
        .buttonStyle(.plain)
        .minimumTouchTarget(minTouch)
        .accessibilityLabel("Modo \(label)")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func legendDot(label: String, color: Color, icon: String) -> some View {
        let accessible = CampusTheme.accessibleTextColor(color, scheme: colorScheme)
        return HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 10.5, weight: .bold))
                .foregroundStyle(accessible)
            Text(label)
                .font(.system(size: 11, weight: .semibold, design: .rounded))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
        }
    }

    // MARK: - Inline Feedback Banner

    private func feedbackBannerView(_ banner: DynamicGridFeedbackBanner) -> some View {
        let tint = banner.kind == .blocked ? lockedBaseColor : Color(hex: colorScheme == .dark ? 0xFBBF24 : 0xB45309)
        let accessibleTint = CampusTheme.accessibleTextColor(tint, scheme: colorScheme)

        return HStack(alignment: .top, spacing: 10) {
            Image(systemName: banner.kind == .blocked ? "lock.fill" : "arrow.uturn.backward.circle.fill")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(accessibleTint)
                .padding(.top, 1)

            VStack(alignment: .leading, spacing: 2) {
                Text(banner.title)
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    .fixedSize(horizontal: false, vertical: true)

                if let detail = banner.detail, !detail.isEmpty {
                    Text(detail)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            Spacer(minLength: 4)

            Button {
                applyGridChange {
                    feedbackBanner = nil
                }
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
                    .frame(width: 28, height: 28)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Fechar aviso")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .liquidGlassCard(cornerRadius: 16, tint: tint, highContrast: hc)
    }

    // MARK: - 2D Scrollable Grid (Horizontal & Vertical Modes)

    @ViewBuilder
    private func gridContent(
        matrix: DynamicCurriculumMatrix,
        completedSet: Set<String>
    ) -> some View {
        if isVerticalOrientation {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(1...max(1, matrix.totalPeriods), id: \.self) { period in
                    let periodCourses = matrix.courses(inPeriod: period)
                    let doneCount = periodCourses.filter { completedSet.contains($0.code) }.count
                    let periodHours = periodCourses.reduce(0) { $0 + $1.workloadHours }

                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 8) {
                            Text("\(period)º Período")
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                            Text("\(doneCount)/\(periodCourses.count) · \(periodHours)h")
                                .font(.system(size: 11, weight: .semibold, design: .rounded))
                                .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: false, highContrast: hc)

                        HStack(alignment: .top, spacing: gridSpacing) {
                            ForEach(periodCourses) { course in
                                courseCard(
                                    course: course,
                                    matrix: matrix,
                                    completedSet: completedSet
                                )
                            }
                        }
                    }
                }
            }
        } else {
            HStack(alignment: .top, spacing: isRegular ? 14 : 10) {
                ForEach(1...max(1, matrix.totalPeriods), id: \.self) { period in
                    let periodCourses = matrix.courses(inPeriod: period)
                    let doneCount = periodCourses.filter { completedSet.contains($0.code) }.count

                    VStack(alignment: .leading, spacing: gridSpacing) {
                        HStack {
                            Text("\(period)º Per.")
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            Spacer(minLength: 4)
                            Text("\(doneCount)/\(periodCourses.count)")
                                .font(.system(size: 11, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                        }
                        .padding(.horizontal, 12)
                        .frame(width: cardWidth, height: 34)
                        .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: false, highContrast: hc)

                        VStack(spacing: gridSpacing) {
                            ForEach(periodCourses) { course in
                                courseCard(
                                    course: course,
                                    matrix: matrix,
                                    completedSet: completedSet
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // MARK: - Course Node Card

    private func courseCard(
        course: DynamicCourseNode,
        matrix: DynamicCurriculumMatrix,
        completedSet: Set<String>
    ) -> some View {
        let status = DynamicGridEngine.status(for: course, matrix: matrix, completedCodes: completedSet)
        let missing = DynamicGridEngine.missingPrerequisites(for: course, completedCodes: completedSet)

        let stateColor: Color = {
            switch status {
            case .completed: return completedBaseColor
            case .available: return availableBaseColor
            case .locked: return lockedBaseColor
            }
        }()

        let accessibleStateColor = CampusTheme.accessibleTextColor(stateColor, scheme: colorScheme)

        let statusIcon: String = {
            switch status {
            case .completed: return "checkmark.circle.fill"
            case .available: return "circle"
            case .locked: return "lock.fill"
            }
        }()

        let statusLabel: String = {
            switch status {
            case .completed: return "Concluída"
            case .available: return "Disponível"
            case .locked: return "Bloqueada"
            }
        }()

        let cardSurface: Color = {
            if hc {
                return CampusTheme.cardBackground(for: colorScheme, highContrast: true)
            }
            switch status {
            case .completed:
                return stateColor.opacity(colorScheme == .dark ? 0.22 : 0.13)
            case .available:
                return CampusTheme.cardBackground(for: colorScheme).opacity(colorScheme == .dark ? 0.85 : 0.92)
            case .locked:
                return stateColor.opacity(colorScheme == .dark ? 0.14 : 0.08)
            }
        }()

        let borderStroke: Color = {
            if hc {
                return stateColor
            }
            switch status {
            case .completed:
                return stateColor.opacity(colorScheme == .dark ? 0.78 : 0.65)
            case .available:
                return stateColor.opacity(colorScheme == .dark ? 0.50 : 0.38)
            case .locked:
                return stateColor.opacity(colorScheme == .dark ? 0.44 : 0.34)
            }
        }()

        return Button {
            handleCourseTap(course: course, matrix: matrix)
        } label: {
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 4) {
                    Text(course.code)
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundStyle(accessibleStateColor)
                        .lineLimit(1)

                    Spacer(minLength: 2)

                    Image(systemName: statusIcon)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(accessibleStateColor)
                }

                Text(course.name)
                    .font(.system(size: 11.5, weight: .semibold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    .lineLimit(3)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Spacer(minLength: 0)

                HStack(spacing: 4) {
                    Text("\(course.workloadHours)h")
                        .font(.system(size: 10.5, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

                    Spacer(minLength: 2)

                    if course.isElective {
                        Text("Eletiva")
                            .font(.system(size: 9.5, weight: .bold, design: .rounded))
                            .foregroundStyle(accessibleStateColor)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(stateColor.opacity(colorScheme == .dark ? 0.24 : 0.14))
                            )
                    } else if status == .locked && !missing.isEmpty {
                        Text("\(missing.count) pré")
                            .font(.system(size: 9.5, weight: .bold, design: .rounded))
                            .foregroundStyle(accessibleStateColor)
                    }
                }
            }
            .padding(10)
            .frame(width: cardWidth, height: cardHeight, alignment: .topLeading)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(cardSurface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(borderStroke, lineWidth: hc ? 2 : (status == .completed ? 1.5 : 1))
            )
        }
        .buttonStyle(.plain)
        .campusHoverLift()
        .accessibilityLabel(
            "\(course.code), \(course.name), \(course.period)º período, \(course.workloadHours) horas\(course.isElective ? ", eletiva" : ""), \(statusLabel)"
        )
        .accessibilityHint(
            status == .locked && !missing.isEmpty
                ? "Bloqueada. Conclua primeiro: \(missing.joined(separator: ", "))."
                : (status == .completed ? "Toque para desmarcar." : "Toque para marcar como concluída.")
        )
    }

    // MARK: - Actions

    private func handleCourseTap(course: DynamicCourseNode, matrix: DynamicCurriculumMatrix) {
        applyGridChange {
            let result = store.toggleDynamicGridCourse(matrixId: matrix.id, courseCode: course.code)
            switch result.action {
            case .blockedByPrerequisites:
                var parts: [String] = []
                if !result.missingPrerequisites.isEmpty {
                    parts.append(result.missingPrerequisites.joined(separator: ", "))
                }
                if result.missingHours > 0 {
                    parts.append("+\(result.missingHours)h")
                }
                let missingList = parts.joined(separator: " · ")
                let missingNames = result.missingPrerequisites.compactMap { code in
                    matrix.course(forCode: code).map { "\($0.code): \($0.name)" }
                }
                feedbackBanner = DynamicGridFeedbackBanner(
                    kind: .blocked,
                    title: "Bloqueada · Conclua primeiro: \(missingList)",
                    detail: missingNames.isEmpty ? nil : missingNames.joined(separator: " • ")
                )
            case .unmarkedWithCascade:
                if !result.cascadeRemovedCodes.isEmpty {
                    feedbackBanner = DynamicGridFeedbackBanner(
                        kind: .cascade,
                        title: "\(result.affectedCode) desmarcada · \(result.cascadeRemovedCodes.count) dependente(s) removida(s) em cascata",
                        detail: "Também desmarcadas: \(result.cascadeRemovedCodes.joined(separator: ", "))"
                    )
                } else {
                    feedbackBanner = nil
                }
            case .markedCompleted, .noChange:
                feedbackBanner = nil
            }
        }
    }

    private func applyGridChange(_ updates: () -> Void) {
        if reduceMotion {
            updates()
        } else {
            withAnimation(.spring(response: 0.25, dampingFraction: 0.86)) {
                updates()
            }
        }
    }
}

#Preview("DynamicCurriculumGridView - Light") {
    ZStack {
        LiquidGlassBackdrop(accent: .green, highContrast: false)
        DynamicCurriculumGridView(store: CampusStore())
    }
    .environment(CampusAppearanceController())
    .preferredColorScheme(.light)
}

#Preview("DynamicCurriculumGridView - Dark") {
    ZStack {
        LiquidGlassBackdrop(accent: .green, highContrast: false)
        DynamicCurriculumGridView(store: CampusStore())
    }
    .environment(CampusAppearanceController())
    .preferredColorScheme(.dark)
}
