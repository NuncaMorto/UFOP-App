import SwiftUI

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
    @State private var showMatrixPicker: Bool = false
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

    public var body: some View {
        let matrix = activeMatrix
        let completedSet = store.dynamicGridCompletedSet(for: matrix.id)
        let summary = DynamicGridEngine.summarize(matrix: matrix, completedCodes: completedSet)
        let palette = DynamicGridSemanticPalette.resolve(for: colorScheme)

        VStack(alignment: .leading, spacing: 10) {
            DynamicGridHeaderCard(
                matrix: matrix,
                summary: summary,
                autoResolution: autoResolution,
                isCollapsed: isHeaderCollapsed,
                isVerticalOrientation: isVerticalOrientation,
                primaryAccent: primaryAccent,
                onPrimaryAccent: onPrimaryAccent,
                palette: palette,
                highContrast: hc,
                reduceMotion: reduceMotion,
                minTouch: minTouch,
                onOpenMatrixPicker: { showMatrixPicker = true },
                onToggleCollapsed: {
                    applyGridChange {
                        isHeaderCollapsed.toggle()
                    }
                },
                onSelectOrientation: { key in
                    applyGridChange {
                        store.dynamicGridOrientation = key
                    }
                },
                onRequestClear: { showClearConfirmation = true }
            )
            .padding(.horizontal, 20)

            if let feedbackBanner {
                DynamicGridFeedbackBannerView(
                    banner: feedbackBanner,
                    lockedBaseColor: palette.locked,
                    highContrast: hc,
                    onDismiss: {
                        applyGridChange {
                            self.feedbackBanner = nil
                        }
                    }
                )
                .padding(.horizontal, 20)
            }

            ScrollView([.horizontal, .vertical], showsIndicators: true) {
                DynamicGridContentView(
                    matrix: matrix,
                    completedSet: completedSet,
                    isVerticalOrientation: isVerticalOrientation,
                    isRegular: isRegular,
                    cardWidth: cardWidth,
                    cardHeight: cardHeight,
                    gridSpacing: gridSpacing,
                    primaryAccent: primaryAccent,
                    palette: palette,
                    highContrast: hc,
                    onTapCourse: { course in
                        handleCourseTap(course: course, matrix: matrix)
                    }
                )
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
                    earliestTranscriptSemester: earliestSemester,
                    campusHint: store.studentProfile.campusName
                )
                selectedMatrixId = resolution.matrix.id
                autoResolution = resolution
            }
        }
        .sheet(isPresented: $showMatrixPicker) {
            DynamicMatrixPickerSheet(
                availableMatrices: DynamicCurriculumCatalog.allMatrices,
                selectedMatrixId: activeMatrix.id,
                autoMatchedMatrixId: (autoResolution?.isCourseMatched == true) ? autoResolution?.matrix.id : nil,
                primaryAccent: primaryAccent,
                onPrimaryAccent: onPrimaryAccent
            ) { chosen in
                applyGridChange {
                    selectedMatrixId = chosen.id
                    feedbackBanner = nil
                }
                showMatrixPicker = false
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
