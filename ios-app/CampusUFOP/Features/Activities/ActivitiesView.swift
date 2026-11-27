import SwiftUI
import UniformTypeIdentifiers

public struct ActivitiesView: View {
    @Environment(CampusStore.self) private var store
    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var selectedTypes: Set<String> = []
    @State private var onlyCompleted: Bool = false
    @State private var selectedDisciplineFilter: String = "Todas as disciplinas"
    @State private var isCompletedExpanded: Bool = false
    @State private var isAddAssessmentSheetPresented: Bool = false
    @State private var syllabusBannerMessage: String? = nil
    @State private var syllabusBannerIsWarning: Bool = false
    @State private var activeAssessmentDetail: AcademicActivityItem?
    @State private var assessmentPendingDelete: AcademicActivityItem?

    private enum ActivityHorizon: String, CaseIterable {
        case overdue = "Em atraso"
        case thisWeek = "Esta semana"
        case upcoming = "Próximos prazos"
    }

    private let activityTypes = ["Prova", "Trabalho", "Seminário"]

    public init() {}

    private var isRegular: Bool {
        horizontalSizeClass == .regular
    }

    private var regularGridColumns: [GridItem] {
        [GridItem(.adaptive(minimum: 320), spacing: 14)]
    }

    private var accent: Color {
        (appearance?.accentColor ?? .red).primary(for: colorScheme)
    }

    private var hc: Bool {
        appearance?.accessibility.highContrastMode ?? false
    }

    private var minTouch: CGFloat {
        appearance?.accessibility.minTouchHeight ?? 48
    }

    private var availableDisciplineNames: [String] {
        let names = Array(Set(store.academicActivities.map(\.disciplineName))).sorted()
        return ["Todas as disciplinas"] + names
    }

    private var typeFilterButtonLabel: String {
        var parts: [String] = activityTypes.filter { selectedTypes.contains($0) }.map {
            switch $0 {
            case "Prova": return "Provas"
            case "Trabalho": return "Trabalhos"
            default: return "Seminários"
            }
        }
        if onlyCompleted {
            parts.append("Concluídas")
        }
        if parts.isEmpty { return "Todos os tipos" }
        if parts.count == 1 { return parts[0] }
        return "\(parts.count) filtros"
    }

    private var baseFilteredActivities: [AcademicActivityItem] {
        store.academicActivities.filter { item in
            let matchesType = selectedTypes.isEmpty
                || selectedTypes.contains(where: { $0.caseInsensitiveCompare(item.kind) == .orderedSame })
            let matchesCourse = selectedDisciplineFilter == "Todas as disciplinas"
                || item.disciplineName == selectedDisciplineFilter
            return matchesType && matchesCourse
        }
    }

    private var filteredActivities: [AcademicActivityItem] {
        baseFilteredActivities.filter { item in
            onlyCompleted ? item.isCompleted : !item.isCompleted
        }
    }

    private var completedMatchingActivities: [AcademicActivityItem] {
        baseFilteredActivities.filter(\.isCompleted)
    }

    private var groupedPendingHorizons: [(horizon: ActivityHorizon, items: [AcademicActivityItem])] {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let weekCutoff = calendar.date(byAdding: .day, value: 7, to: today) ?? today
        var overdue: [AcademicActivityItem] = []
        var thisWeek: [AcademicActivityItem] = []
        var upcoming: [AcademicActivityItem] = []

        for item in filteredActivities {
            if let due = CampusStore.parseIsoDate(item.dueDate) {
                let dueStart = calendar.startOfDay(for: due)
                if dueStart < today {
                    overdue.append(item)
                } else if dueStart <= weekCutoff {
                    thisWeek.append(item)
                } else {
                    upcoming.append(item)
                }
            } else {
                upcoming.append(item)
            }
        }

        var result: [(horizon: ActivityHorizon, items: [AcademicActivityItem])] = []
        if !overdue.isEmpty { result.append((.overdue, overdue)) }
        if !thisWeek.isEmpty { result.append((.thisWeek, thisWeek)) }
        if !upcoming.isEmpty { result.append((.upcoming, upcoming)) }
        return result
    }

    private func overdueDays(for item: AcademicActivityItem) -> Int? {
        guard !item.isCompleted, let due = CampusStore.parseIsoDate(item.dueDate) else { return nil }
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let dueStart = calendar.startOfDay(for: due)
        guard dueStart < today else { return nil }
        let diff = calendar.dateComponents([.day], from: dueStart, to: today).day ?? 0
        return diff > 0 ? diff : nil
    }

    public var body: some View {
        NavigationStack {
            ZStack {
                LiquidGlassBackdrop(accent: appearance?.accentColor ?? .red, highContrast: hc)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {
                        headerSection

                        singleRowFilterBar

                        if let syllabusBannerMessage {
                            syllabusFeedbackBannerView(message: syllabusBannerMessage, isWarning: syllabusBannerIsWarning)
                        }

                        activitiesListSection
                    }
                    .padding(.horizontal, isRegular ? 24 : 18)
                    .padding(.top, 12)
                    .padding(.bottom, isRegular ? 32 : 108)
                    .campusReadableWidth(isRegular ? 1040 : .infinity)
                }
            }
            #if os(iOS)
            .navigationBarHidden(true)
            #endif
            .sheet(isPresented: $isAddAssessmentSheetPresented) {
                AddAssessmentSheetView(
                    store: store,
                    disciplines: store.disciplines,
                    onSyllabusImported: { message, isWarning in
                        syllabusBannerMessage = message
                        syllabusBannerIsWarning = isWarning
                        isAddAssessmentSheetPresented = false
                    },
                    onSave: { courseId, title, kind, dueDate, dueTime in
                        withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                            _ = store.createAssessment(
                                courseId: courseId,
                                title: title,
                                kind: kind,
                                dueDate: dueDate,
                                dueTime: dueTime
                            )
                        }
                        isAddAssessmentSheetPresented = false
                    },
                    onCancel: {
                        isAddAssessmentSheetPresented = false
                    }
                )
                .presentationDetents([.large, .fraction(0.88)])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(30)
            }
            .sheet(item: $activeAssessmentDetail) { item in
                AssessmentDetailSheetView(
                    activity: item,
                    store: store,
                    onRequestDelete: { target in
                        activeAssessmentDetail = nil
                        assessmentPendingDelete = target
                    },
                    onClose: {
                        activeAssessmentDetail = nil
                    }
                )
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(30)
            }
            .confirmationDialog(
                "Excluir avaliação?",
                isPresented: Binding(
                    get: { assessmentPendingDelete != nil },
                    set: { if !$0 { assessmentPendingDelete = nil } }
                ),
                titleVisibility: .visible,
                presenting: assessmentPendingDelete
            ) { item in
                Button("Excluir avaliação", role: .destructive) {
                    withAnimation(.snappy) {
                        store.deleteAssessment(assessmentId: item.id)
                    }
                    assessmentPendingDelete = nil
                }
                Button("Cancelar", role: .cancel) {
                    assessmentPendingDelete = nil
                }
            } message: { item in
                Text("Deseja remover '\(item.title)' de \(item.disciplineName)?")
            }
        }
    }

    // MARK: Header

    private var headerSection: some View {
        let pendingCount = store.pendingAssessmentsCount
        let completedCount = store.completedAssessmentsCount
        let onPrimary = (appearance?.accentColor ?? .red).onPrimary(for: colorScheme)

        return VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .center) {
                Text("SEMESTRE \(store.selectedSemesterFilter)")
                    .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                    .tracking(1.5)
                    .foregroundStyle(CampusTheme.accessibleTextColor(accent, scheme: colorScheme, accent: appearance?.accentColor ?? .red))

                Spacer()

                Button {
                    isAddAssessmentSheetPresented = true
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "plus")
                            .font(.system(size: 11, weight: .bold))
                        Text("Novo prazo")
                            .font(CampusTheme.font(size: 12, weight: .bold))
                    }
                    .foregroundStyle(onPrimary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .liquidGlassCapsule(selected: true, accent: accent, highContrast: hc)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
                .accessibilityLabel("Adicionar avaliação ou prazo")
            }

            Text("Atividades")
                .font(CampusTheme.font(size: 28, weight: .bold))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                .accessibilityAddTraits(.isHeader)

            Text("\(pendingCount) pendentes · \(completedCount) concluídas")
                .font(CampusTheme.tabularFont(size: 14, weight: .regular))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
        }
    }

    // MARK: Compact Single-Row Filter Bar ("Todos os tipos ▾" + "Todas as disciplinas ▾" — Sprint 15)

    private var singleRowFilterBar: some View {
        let hasActiveTypeFilter = !selectedTypes.isEmpty || onlyCompleted
        let hasActiveCourseFilter = selectedDisciplineFilter != "Todas as disciplinas"

        return LiquidGlassGroup(spacing: 8) {
            HStack(spacing: 8) {
                Menu {
                    Section("Tipos (selecione um ou mais)") {
                        ForEach(activityTypes, id: \.self) { type in
                            Button {
                                if selectedTypes.contains(type) {
                                    selectedTypes.remove(type)
                                } else {
                                    selectedTypes.insert(type)
                                }
                            } label: {
                                Label(
                                    type == "Prova" ? "Provas" : (type == "Trabalho" ? "Trabalhos" : "Seminários"),
                                    systemImage: selectedTypes.contains(type) ? "checkmark.square.fill" : "square"
                                )
                            }
                        }
                    }

                    Section("Estado") {
                        Button {
                            onlyCompleted.toggle()
                        } label: {
                            Label("Concluídas", systemImage: onlyCompleted ? "checkmark.square.fill" : "square")
                        }
                    }

                    if hasActiveTypeFilter {
                        Button(role: .destructive) {
                            selectedTypes.removeAll()
                            onlyCompleted = false
                        } label: {
                            Label("Mostrar todos os tipos", systemImage: "arrow.counterclockwise")
                        }
                    }
                } label: {
                    HStack(spacing: 6) {
                        Text(typeFilterButtonLabel)
                            .font(CampusTheme.font(size: 12, weight: .semibold))
                            .foregroundStyle(hasActiveTypeFilter ? (appearance?.accentColor ?? .red).onPrimary(for: colorScheme) : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .lineLimit(1)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(hasActiveTypeFilter ? (appearance?.accentColor ?? .red).onPrimary(for: colorScheme) : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .liquidGlassCapsule(selected: hasActiveTypeFilter, accent: accent, highContrast: hc)
                }
                .minimumTouchTarget(minTouch)

                Menu {
                    ForEach(availableDisciplineNames, id: \.self) { name in
                        Button {
                            selectedDisciplineFilter = name
                        } label: {
                            HStack {
                                Text(name)
                                if selectedDisciplineFilter == name {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 6) {
                        Text(selectedDisciplineFilter)
                            .font(CampusTheme.font(size: 12, weight: .semibold))
                            .foregroundStyle(hasActiveCourseFilter ? (appearance?.accentColor ?? .red).onPrimary(for: colorScheme) : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .lineLimit(1)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(hasActiveCourseFilter ? (appearance?.accentColor ?? .red).onPrimary(for: colorScheme) : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .liquidGlassCapsule(selected: hasActiveCourseFilter, accent: accent, highContrast: hc)
                }
                .minimumTouchTarget(minTouch)

                Spacer(minLength: 0)
            }
        }
    }

    private func syllabusFeedbackBannerView(message: String, isWarning: Bool) -> some View {
        let toneColor = isWarning ? Color(hex: 0xD97706) : CampusTheme.algebraGreen
        return HStack(spacing: 10) {
            Image(systemName: isWarning ? "exclamationmark.triangle.fill" : "checkmark.seal.fill")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(toneColor)
            Text(message)
                .font(CampusTheme.font(size: 12, weight: .semibold))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 4)
            Button {
                syllabusBannerMessage = nil
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 16))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Fechar aviso de importação")
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: 18, tint: toneColor, highContrast: hc)
    }

    // MARK: Activities List

    @ViewBuilder
    private var activitiesListSection: some View {
        let hasAnyToShow = onlyCompleted ? !filteredActivities.isEmpty : (!filteredActivities.isEmpty || !completedMatchingActivities.isEmpty)
        if !hasAnyToShow {
            VStack(spacing: 12) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(accent)

                Text("Tudo em dia")
                    .font(CampusTheme.font(size: 16, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                Text("Nenhuma atividade encontrada nos filtros selecionados.")
                    .font(CampusTheme.font(size: 13))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

                Button {
                    isAddAssessmentSheetPresented = true
                } label: {
                    Label("Cadastrar Trabalho, Seminário ou Prova", systemImage: "plus.circle.fill")
                        .font(CampusTheme.font(size: 13, weight: .semibold))
                        .foregroundStyle(accent)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 34)
            .padding(.horizontal, 18)
            .liquidGlassCard(highContrast: hc)
        } else if onlyCompleted {
            activitiesCollectionView(items: filteredActivities)
        } else {
            VStack(alignment: .leading, spacing: 14) {
                ForEach(groupedPendingHorizons, id: \.horizon.rawValue) { group in
                    VStack(alignment: .leading, spacing: 10) {
                        horizonSectionHeader(horizon: group.horizon, count: group.items.count)
                        activitiesCollectionView(items: group.items)
                    }
                }

                if !completedMatchingActivities.isEmpty {
                    Button {
                        withAnimation(.snappy) {
                            isCompletedExpanded.toggle()
                        }
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(colorScheme == .dark ? Color(hex: 0x34D399) : Color(hex: 0x166534))

                            Text("Concluídas")
                                .font(CampusTheme.font(size: 14, weight: .bold))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                            Text("\(completedMatchingActivities.count)")
                                .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                .background(Capsule().fill((appearance?.accentColor ?? .red).surface1(for: colorScheme)))

                            Spacer()

                            Image(systemName: isCompletedExpanded ? "chevron.up" : "chevron.down")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .frame(maxWidth: .infinity)
                        .liquidGlassCard(highContrast: hc, useSurface2: true)
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(minTouch)

                    if isCompletedExpanded {
                        activitiesCollectionView(items: completedMatchingActivities)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func activitiesCollectionView(items: [AcademicActivityItem]) -> some View {
        if isRegular {
            LazyVGrid(columns: regularGridColumns, spacing: 14) {
                ForEach(items) { item in
                    activityRow(item: item)
                }
            }
        } else {
            VStack(spacing: 12) {
                ForEach(items) { item in
                    activityRow(item: item)
                }
            }
        }
    }

    private func horizonSectionHeader(horizon: ActivityHorizon, count: Int) -> some View {
        let isOverdue = horizon == .overdue
        let overdueTint = colorScheme == .dark ? Color(hex: 0xFB7185) : Color(hex: 0xBE123C)
        let dotColor: Color = {
            switch horizon {
            case .overdue: return overdueTint
            case .thisWeek: return accent
            case .upcoming: return CampusTheme.secondaryText(for: colorScheme, highContrast: hc)
            }
        }()

        return HStack(spacing: 8) {
            Circle()
                .fill(dotColor)
                .frame(width: 8, height: 8)

            Text(horizon.rawValue)
                .font(CampusTheme.font(size: 14, weight: .bold))
                .foregroundStyle(isOverdue ? overdueTint : CampusTheme.primaryText(for: colorScheme, highContrast: hc))

            Text("\(count)")
                .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                .foregroundStyle(isOverdue ? overdueTint : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                .padding(.horizontal, 8)
                .padding(.vertical, 2)
                .background(
                    Capsule().fill(
                        isOverdue ? overdueTint.opacity(colorScheme == .dark ? 0.22 : 0.14) : (appearance?.accentColor ?? .red).surface2(for: colorScheme)
                    )
                )
        }
        .padding(.top, 4)
    }

    private func activityRow(item: AcademicActivityItem) -> some View {
        let itemColor = CampusTheme.departmentColor(for: item.colorKey)
        let overdueDaysCount = overdueDays(for: item)
        let isOverdue = overdueDaysCount != nil
        let overdueRed = colorScheme == .dark ? Color(hex: 0xFB7185) : Color(hex: 0xBE123C)
        let badgeTint = isOverdue ? overdueRed : itemColor
        let displayTitle = formatAcademicAssessmentTitle(item.title)

        return Button {
            activeAssessmentDetail = item
        } label: {
            AccentBarCardContainer(
                accentColor: badgeTint,
                highContrast: hc,
                useSurface2: isOverdue
            ) {
                HStack(spacing: 12) {
                    VStack(spacing: 1) {
                        Text(item.dueDay)
                            .font(CampusTheme.tabularFont(size: 16, weight: .bold))
                            .foregroundStyle(badgeTint)
                        Text(item.dueMonth)
                            .font(CampusTheme.font(size: 10, weight: .bold))
                            .foregroundStyle(badgeTint)
                    }
                    .frame(width: 46, height: 46)
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(isOverdue ? overdueRed.opacity(0.14) : CampusTheme.departmentSoftTint(for: item.colorKey, scheme: colorScheme))
                    )

                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            Text(item.disciplineName)
                                .font(CampusTheme.font(size: 12, weight: .semibold))
                                .foregroundStyle(CampusTheme.accessibleTextColor(itemColor, scheme: colorScheme))
                                .lineLimit(1)

                            Text("•")
                                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

                            Text(item.kind)
                                .font(CampusTheme.font(size: 11, weight: .bold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

                            if let overdueDaysCount {
                                Text("Há \(overdueDaysCount)d")
                                    .font(CampusTheme.tabularFont(size: 10, weight: .bold))
                                    .foregroundStyle(overdueRed)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 1)
                                    .background(Capsule().fill(overdueRed.opacity(0.14)))
                            }
                        }

                        Text(displayTitle)
                            .font(CampusTheme.font(size: 15, weight: .bold))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .strikethrough(item.isCompleted)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)

                        Text(item.categoryLabel)
                            .font(CampusTheme.tabularFont(size: 12, weight: .regular))
                            .foregroundStyle(isOverdue ? overdueRed : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    }

                    Spacer()

                    Button {
                        withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                            _ = store.toggleAssessmentCompleted(assessmentId: item.id)
                        }
                    } label: {
                        Image(systemName: item.isCompleted ? "checkmark.circle.fill" : "circle")
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundStyle(item.isCompleted ? accent : CampusTheme.mutedText(for: colorScheme, highContrast: hc))
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(minTouch)
                    .accessibilityLabel(item.isCompleted ? "Marcar como pendente" : "Marcar como concluída")
                }
            }
        }
        .buttonStyle(.plain)
        .campusHoverLift()
    }
}

// MARK: - Assessment Detail & Edit Sheet (Vertically Aligned Chips + Native Picker + Confirmation)

public struct AssessmentDetailSheetView: View {
    public let activity: AcademicActivityItem
    @Bindable public var store: CampusStore
    public let onRequestDelete: (AcademicActivityItem) -> Void
    public let onClose: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    @State private var isEditing: Bool = false
    @State private var editTitle: String = ""
    @State private var editKind: String = "Prova"
    @State private var editDate: Date = Date()

    private let kinds = ["Prova", "Trabalho", "Seminário"]

    public init(
        activity: AcademicActivityItem,
        store: CampusStore,
        onRequestDelete: @escaping (AcademicActivityItem) -> Void,
        onClose: @escaping () -> Void
    ) {
        self.activity = activity
        self.store = store
        self.onRequestDelete = onRequestDelete
        self.onClose = onClose
    }

    private var liveActivity: AcademicActivityItem {
        store.academicActivities.first(where: { $0.id == activity.id }) ?? activity
    }

    public var body: some View {
        let current = liveActivity
        let accentObj = appearance?.accentColor ?? .red
        let accent = accentObj.primary(for: colorScheme)
        let hc = appearance?.accessibility.highContrastMode ?? false
        let courseColor = CampusTheme.departmentColor(for: current.colorKey)

        ZStack {
            LiquidGlassBackdrop(accent: accentObj, highContrast: hc)

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                Circle().fill(courseColor).frame(width: 8, height: 8)
                                Text(current.disciplineName)
                                    .font(CampusTheme.font(size: 13, weight: .semibold))
                                    .foregroundStyle(CampusTheme.accessibleTextColor(courseColor, scheme: colorScheme))
                            }
                            Text(formatAcademicAssessmentTitle(current.title))
                                .font(CampusTheme.font(size: 22, weight: .bold))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                        }

                        Spacer()

                        Button(action: onClose) {
                            Image(systemName: "xmark")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                .frame(width: 34, height: 34)
                                .liquidGlassCapsule(selected: false, accent: accent, highContrast: hc)
                        }
                        .buttonStyle(.plain)
                    }

                    // Card com data e chips perfeitamente alinhados na mesma altura vertical (v1.9.6 fix)
                    VStack(alignment: .leading, spacing: 14) {
                        HStack(spacing: 12) {
                            VStack(spacing: 1) {
                                Text(current.dueDay)
                                    .font(CampusTheme.tabularFont(size: 16, weight: .bold))
                                    .foregroundStyle(courseColor)
                                Text(current.dueMonth)
                                    .font(CampusTheme.font(size: 10, weight: .bold))
                                    .foregroundStyle(courseColor)
                            }
                            .frame(width: 48, height: 48)
                            .background(RoundedRectangle(cornerRadius: 12).fill(courseColor.opacity(0.16)))

                            VStack(alignment: .leading, spacing: 3) {
                                Label("Data: \(formattedDisplayDate(current.dueDate))", systemImage: "calendar")
                                    .font(CampusTheme.tabularFont(size: 14, weight: .semibold))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                Label("Horário: \(current.dueTime)", systemImage: "clock")
                                    .font(CampusTheme.tabularFont(size: 13, weight: .regular))
                                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            }
                        }

                        Divider()

                        HStack(alignment: .center, spacing: 8) {
                            Text(current.kind)
                                .font(CampusTheme.font(size: 12, weight: .bold))
                                .foregroundStyle(courseColor)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(Capsule().fill(courseColor.opacity(0.16)))

                            HStack(spacing: 5) {
                                Image(systemName: current.isCompleted ? "checkmark.circle.fill" : "circle")
                                    .font(.system(size: 12, weight: .semibold))
                                Text(current.isCompleted ? "Concluída" : "Pendente")
                                    .font(CampusTheme.font(size: 12, weight: .semibold))
                            }
                            .foregroundStyle(current.isCompleted ? (appearance?.accentColor ?? .red).onPrimary(for: colorScheme) : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .liquidGlassCapsule(selected: current.isCompleted, accent: accent, interactive: false, highContrast: hc)
                        }
                    }
                    .padding(16)
                    .liquidGlassCard(cornerRadius: 18, tint: courseColor, highContrast: hc)

                    if isEditing {
                        VStack(alignment: .leading, spacing: 12) {
                            TextField("Título", text: $editTitle)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 10)
                                .liquidGlassCard(cornerRadius: 12, highContrast: hc)

                            Picker("Tipo", selection: $editKind) {
                                ForEach(kinds, id: \.self) { Text($0).tag($0) }
                            }
                            .pickerStyle(.segmented)

                            DatePicker("Data e horário", selection: $editDate)
                                .tint(accent)

                            LiquidGlassPrimaryButton(
                                tint: accent,
                                foreground: accentObj.onPrimary(for: colorScheme),
                                minHeight: appearance?.accessibility.minTouchHeight ?? 48
                            ) {
                                let df = DateFormatter()
                                df.locale = Locale(identifier: "en_US_POSIX")
                                df.dateFormat = "yyyy-MM-dd"
                                let tf = DateFormatter()
                                tf.locale = Locale(identifier: "en_US_POSIX")
                                tf.dateFormat = "HH:mm"
                                store.updateAssessment(
                                    id: current.id,
                                    title: editTitle,
                                    kind: editKind,
                                    dueDate: df.string(from: editDate),
                                    dueTime: tf.string(from: editDate)
                                )
                                isEditing = false
                            } label: {
                                Text("Salvar alterações")
                                    .font(CampusTheme.font(size: 14, weight: .bold))
                            }
                        }
                        .padding(14)
                        .liquidGlassCard(cornerRadius: 16, highContrast: hc)
                    }

                    HStack(spacing: 10) {
                        Button {
                            if !isEditing {
                                editTitle = current.title
                                editKind = current.kind
                                editDate = parsedDate(iso: current.dueDate, hhmm: current.dueTime)
                            }
                            isEditing.toggle()
                        } label: {
                            Label(isEditing ? "Cancelar edição" : "Editar", systemImage: "pencil")
                                .font(CampusTheme.font(size: 14, weight: .bold))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                .frame(maxWidth: .infinity, minHeight: appearance?.accessibility.minTouchHeight ?? 48)
                                .liquidGlassCapsule(selected: false, accent: accent, highContrast: hc)
                        }
                        .buttonStyle(.plain)

                        Button(role: .destructive) {
                            onRequestDelete(current)
                        } label: {
                            Label("Excluir", systemImage: "trash")
                                .font(CampusTheme.font(size: 14, weight: .semibold))
                                .foregroundStyle(colorScheme == .dark ? Color(hex: 0xFB7185) : Color(hex: 0xBE123C))
                                .frame(maxWidth: .infinity, minHeight: appearance?.accessibility.minTouchHeight ?? 48)
                                .liquidGlassCapsule(selected: false, accent: accent, highContrast: hc)
                        }
                        .buttonStyle(.plain)
                    }

                    LiquidGlassPrimaryButton(
                        tint: accent,
                        foreground: accentObj.onPrimary(for: colorScheme),
                        minHeight: appearance?.accessibility.minTouchHeight ?? 48
                    ) {
                        _ = store.toggleAssessmentCompleted(assessmentId: current.id)
                        onClose()
                    } label: {
                        Text(current.isCompleted ? "Reabrir atividade" : "Marcar como concluída")
                            .font(CampusTheme.font(size: 15, weight: .bold))
                    }
                }
                .padding(20)
            }
        }
    }

    private func formattedDisplayDate(_ iso: String) -> String {
        let p = iso.split(separator: "-")
        guard p.count == 3 else { return iso }
        return "\(p[2])/\(p[1])/\(p[0])"
    }

    private func parsedDate(iso: String, hhmm: String) -> Date {
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.dateFormat = "yyyy-MM-dd HH:mm"
        return df.date(from: "\(iso) \(hhmm)") ?? Date()
    }
}

// MARK: - Add Assessment Sheet (Auto-Detect Syllabus Top + Manual Section Below)

public struct AddAssessmentSheetView: View {
    public let store: CampusStore?
    public let disciplines: [DisciplineSummaryCard]
    public let onSyllabusImported: ((String, Bool) -> Void)?
    public let onSave: (String, String, String, String, String) -> Void
    public let onCancel: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedCourseId: String
    @State private var selectedKind: String = "Trabalho"
    @State private var title: String = ""
    @State private var dueDateTime: Date = Calendar.current.date(bySettingHour: 18, minute: 0, second: 0, of: Date()) ?? Date()
    @State private var showSyllabusPicker: Bool = false
    @State private var syllabusFeedbackMessage: String? = nil
    @State private var syllabusFeedbackIsError: Bool = false

    private let kinds = ["Trabalho", "Seminário", "Prova"]

    public init(
        store: CampusStore? = nil,
        disciplines: [DisciplineSummaryCard],
        onSyllabusImported: ((String, Bool) -> Void)? = nil,
        onSave: @escaping (String, String, String, String, String) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.store = store
        self.disciplines = disciplines
        self.onSyllabusImported = onSyllabusImported
        self.onSave = onSave
        self.onCancel = onCancel
        self._selectedCourseId = State(initialValue: disciplines.first?.id ?? "calc-ii")
    }

    public var body: some View {
        let accentObj = appearance?.accentColor ?? .red
        let accent = accentObj.primary(for: colorScheme)
        let hc = appearance?.accessibility.highContrastMode ?? false
        let minTouch = appearance?.accessibility.minTouchHeight ?? 48
        let semesterLabel = store?.selectedSemesterFilter ?? "2026.2"

        ZStack {
            LiquidGlassBackdrop(accent: accentObj, highContrast: hc)

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    HStack {
                        Text("NOVO PRAZO · \(semesterLabel)")
                            .font(CampusTheme.tabularFont(size: 11, weight: .bold))
                            .tracking(1.4)
                            .foregroundStyle(CampusTheme.accessibleTextColor(accent, scheme: colorScheme, accent: accentObj))
                        Spacer()
                        Button(action: onCancel) {
                            Image(systemName: "xmark")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                .frame(width: 34, height: 34)
                                .liquidGlassCapsule(selected: false, accent: accent, highContrast: hc)
                        }
                        .buttonStyle(.plain)
                        .minimumTouchTarget(minTouch)
                        .accessibilityLabel("Fechar")
                    }

                    Text("Adicionar avaliação")
                        .font(CampusTheme.font(size: 24, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                    // Topo exclusivo: Importar Plano de Ensino (PDF) com detecção automática
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 10) {
                            Image(systemName: "doc.text.magnifyingglass")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundStyle(CampusTheme.accessibleTextColor(accent, scheme: colorScheme, accent: accentObj))

                            VStack(alignment: .leading, spacing: 2) {
                                Text("Importar Plano de Ensino (PDF)")
                                    .font(CampusTheme.font(size: 14, weight: .bold))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                Text("Identifica automaticamente sua disciplina de \(semesterLabel)")
                                    .font(.system(size: 12))
                                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            }

                            Spacer(minLength: 4)

                            Button {
                                showSyllabusPicker = true
                            } label: {
                                Text("Importar PDF")
                                    .font(CampusTheme.font(size: 12, weight: .bold))
                                    .foregroundStyle(accentObj.onPrimary(for: colorScheme))
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 9)
                                    .liquidGlassCapsule(selected: true, accent: accent, highContrast: hc)
                            }
                            .buttonStyle(.plain)
                            .minimumTouchTarget(minTouch)
                            .disabled(store == nil)
                        }

                        if let syllabusFeedbackMessage {
                            let tone = syllabusFeedbackIsError ? Color(hex: 0xDC2626) : Color(hex: 0xD97706)
                            Text(syllabusFeedbackMessage)
                                .font(CampusTheme.font(size: 12, weight: .semibold))
                                .foregroundStyle(tone)
                                .fixedSize(horizontal: false, vertical: true)
                                .padding(10)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(RoundedRectangle(cornerRadius: 12).fill(tone.opacity(0.12)))
                        }
                    }
                    .padding(14)
                    .liquidGlassCard(cornerRadius: CampusTheme.cardCornerRadius, tint: accent, highContrast: hc, useSurface2: true)

                    // Separador antes do cadastro manual
                    HStack(spacing: 10) {
                        Rectangle()
                            .fill(CampusTheme.mutedText(for: colorScheme, highContrast: hc).opacity(0.25))
                            .frame(height: 1)
                        Text("ou cadastre manualmente")
                            .font(CampusTheme.font(size: 11, weight: .semibold))
                            .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
                            .fixedSize()
                        Rectangle()
                            .fill(CampusTheme.mutedText(for: colorScheme, highContrast: hc).opacity(0.25))
                            .frame(height: 1)
                    }

                    // Seletor de disciplina movido para a seção manual
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Disciplina")
                            .font(CampusTheme.font(size: 13, weight: .semibold))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(disciplines) { disc in
                                    let isSelected = selectedCourseId == disc.id
                                    let dColor = CampusTheme.departmentColor(for: disc.colorKey)
                                    Button {
                                        selectedCourseId = disc.id
                                    } label: {
                                        Text(disc.name)
                                            .font(CampusTheme.font(size: 12, weight: .semibold))
                                            .foregroundStyle(isSelected ? .white : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 8)
                                            .liquidGlassCapsule(selected: isSelected, accent: dColor, highContrast: hc)
                                    }
                                    .buttonStyle(.plain)
                                    .minimumTouchTarget(minTouch)
                                }
                            }
                        }
                    }

                    Picker("Categoria", selection: $selectedKind) {
                        ForEach(kinds, id: \.self) { Text($0).tag($0) }
                    }
                    .pickerStyle(.segmented)

                    TextField("Título (ex.: Prova 1, Trabalho Prático)", text: $title)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .liquidGlassCard(cornerRadius: 14, highContrast: hc)

                    DatePicker("Data e hora de entrega", selection: $dueDateTime)
                        .tint(accent)
                        .padding(12)
                        .liquidGlassCard(cornerRadius: 14, highContrast: hc)

                    LiquidGlassPrimaryButton(
                        tint: accent,
                        foreground: accentObj.onPrimary(for: colorScheme),
                        minHeight: minTouch
                    ) {
                        let df = DateFormatter()
                        df.locale = Locale(identifier: "en_US_POSIX")
                        df.dateFormat = "yyyy-MM-dd"
                        let tf = DateFormatter()
                        tf.locale = Locale(identifier: "en_US_POSIX")
                        tf.dateFormat = "HH:mm"
                        let finalTitle = title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                            ? "\(selectedKind) avaliativo"
                            : title
                        onSave(selectedCourseId, finalTitle, selectedKind, df.string(from: dueDateTime), tf.string(from: dueDateTime))
                    } label: {
                        Text("Salvar avaliação")
                            .font(CampusTheme.font(size: 15, weight: .bold))
                    }
                }
                .padding(20)
            }
        }
        .fileImporter(
            isPresented: $showSyllabusPicker,
            allowedContentTypes: [.pdf],
            allowsMultipleSelection: false
        ) { result in
            guard let store else { return }
            syllabusFeedbackMessage = nil
            syllabusFeedbackIsError = false
            do {
                guard let url = try result.get().first else { return }
                let text = try SecurePdfProcessor.extractTextAndDeleteTemp(from: url)
                let (report, importedCount) = store.importSyllabusAutoDetectCourse(text: text, semester: store.selectedSemesterFilter)
                if !report.matchedCourse {
                    syllabusFeedbackIsError = true
                    syllabusFeedbackMessage = report.errorMessage
                        ?? "Não foi possível vincular este Plano de Ensino a nenhuma disciplina matriculada."
                } else {
                    if let cid = report.matchedCourseId {
                        selectedCourseId = cid
                    }
                    let courseName = report.matchedCourseTitle ?? "disciplina identificada"
                    var bannerParts: [String] = []
                    if importedCount > 0 {
                        bannerParts.append("\(importedCount) avaliação(ões) importada(s) do Plano de Ensino de \(courseName).")
                    } else {
                        bannerParts.append("Plano de Ensino de \(courseName) analisado (nenhuma nova data pendente).")
                    }
                    if !report.extractedProfessor.isEmpty {
                        bannerParts.append("Docente: \(report.extractedProfessor).")
                    }
                    if let warn = report.warningMessage, !warn.isEmpty {
                        bannerParts.append(warn)
                    }
                    let msg = bannerParts.joined(separator: " ")
                    let hasWarning = report.warningMessage != nil || !report.hasGradeDistribution || !report.hasAssessmentDates
                    if importedCount > 0, let onSyllabusImported {
                        onSyllabusImported(msg, hasWarning)
                    } else {
                        syllabusFeedbackIsError = false
                        syllabusFeedbackMessage = msg
                    }
                }
            } catch {
                syllabusFeedbackIsError = true
                syllabusFeedbackMessage = error.localizedDescription
            }
        }
    }
}
