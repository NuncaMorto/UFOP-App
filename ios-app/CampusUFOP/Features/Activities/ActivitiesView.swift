import SwiftUI

public struct ActivitiesView: View {
    @Environment(CampusStore.self) private var store
    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var selectedTypes: Set<String> = []
    @State private var onlyCompleted: Bool = false
    @State private var selectedDisciplineFilter: String = "Todas as disciplinas"
    @State private var isAddAssessmentSheetPresented: Bool = false
    @State private var activeAssessmentDetail: AcademicActivityItem?
    @State private var assessmentPendingDelete: AcademicActivityItem?

    private let activityTypes = ["Prova", "Trabalho", "Seminário"]

    public init() {}

    private var isRegular: Bool {
        horizontalSizeClass == .regular
    }

    private var regularGridColumns: [GridItem] {
        [GridItem(.adaptive(minimum: 320), spacing: 14)]
    }

    private var accent: Color {
        (appearance?.accentColor ?? .green).primary(for: colorScheme)
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

    private var filteredActivities: [AcademicActivityItem] {
        store.academicActivities.filter { item in
            let matchesStatus = onlyCompleted ? item.isCompleted : !item.isCompleted
            let matchesType = selectedTypes.isEmpty
                || selectedTypes.contains(where: { $0.caseInsensitiveCompare(item.kind) == .orderedSame })
            let matchesCourse = selectedDisciplineFilter == "Todas as disciplinas"
                || item.disciplineName == selectedDisciplineFilter
            return matchesStatus && matchesType && matchesCourse
        }
    }

    public var body: some View {
        NavigationStack {
            ZStack {
                LiquidGlassBackdrop(accent: appearance?.accentColor ?? .green, highContrast: hc)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {
                        headerSection

                        singleRowFilterBar

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
                    disciplines: store.disciplines,
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
                .presentationDetents([.large, .fraction(0.86)])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
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
                .presentationCornerRadius(28)
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

        return VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .center) {
                Text("SEMESTRE \(store.selectedSemesterFilter)")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .tracking(1.5)
                    .foregroundStyle(accent)

                Spacer()

                Button {
                    isAddAssessmentSheetPresented = true
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "plus")
                            .font(.system(size: 11, weight: .bold))
                        Text("Novo prazo")
                            .font(.system(size: 12, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(accent)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .liquidGlassCapsule(selected: true, accent: accent, highContrast: hc)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
                .accessibilityLabel("Adicionar avaliação ou prazo")
            }

            Text("Atividades")
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                .accessibilityAddTraits(.isHeader)

            Text("\(pendingCount) pendentes · \(completedCount) concluídas")
                .font(.system(size: 14, weight: .regular))
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
                            .font(.system(size: 12, weight: .semibold, design: .rounded))
                            .foregroundStyle(hasActiveTypeFilter ? accent : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .lineLimit(1)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(hasActiveTypeFilter ? accent : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
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
                            .font(.system(size: 12, weight: .semibold, design: .rounded))
                            .foregroundStyle(hasActiveCourseFilter ? accent : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .lineLimit(1)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(hasActiveCourseFilter ? accent : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
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

    // MARK: Activities List

    @ViewBuilder
    private var activitiesListSection: some View {
        if filteredActivities.isEmpty {
            VStack(spacing: 12) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(accent)

                Text("Tudo em dia")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                Text("Nenhuma atividade encontrada nos filtros selecionados.")
                    .font(.system(size: 13))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

                Button {
                    isAddAssessmentSheetPresented = true
                } label: {
                    Label("Cadastrar Trabalho, Seminário ou Prova", systemImage: "plus.circle.fill")
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundStyle(accent)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 34)
            .padding(.horizontal, 18)
            .liquidGlassCard(highContrast: hc)
        } else if isRegular {
            LazyVGrid(columns: regularGridColumns, spacing: 14) {
                ForEach(filteredActivities) { item in
                    activityRow(item: item)
                }
            }
        } else {
            VStack(spacing: 12) {
                ForEach(filteredActivities) { item in
                    activityRow(item: item)
                }
            }
        }
    }

    private func activityRow(item: AcademicActivityItem) -> some View {
        let itemColor = CampusTheme.departmentColor(for: item.colorKey)

        return Button {
            activeAssessmentDetail = item
        } label: {
            AccentBarCardContainer(accentColor: itemColor, highContrast: hc) {
                HStack(spacing: 12) {
                    VStack(spacing: 1) {
                        Text(item.dueDay)
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .foregroundStyle(itemColor)
                        Text(item.dueMonth)
                            .font(.system(size: 10, weight: .bold, design: .rounded))
                            .foregroundStyle(itemColor)
                    }
                    .frame(width: 46, height: 46)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(CampusTheme.departmentSoftTint(for: item.colorKey, scheme: colorScheme))
                    )

                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            Text(item.disciplineName)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(CampusTheme.accessibleTextColor(itemColor, scheme: colorScheme))
                                .lineLimit(1)

                            Text("•")
                                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

                            Text(item.kind)
                                .font(.system(size: 11, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                        }

                        Text(item.title)
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .strikethrough(item.isCompleted)
                            .multilineTextAlignment(.leading)

                        Text(item.categoryLabel)
                            .font(.system(size: 12))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
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
        let accent = (appearance?.accentColor ?? .green).primary(for: colorScheme)
        let hc = appearance?.accessibility.highContrastMode ?? false
        let courseColor = CampusTheme.departmentColor(for: current.colorKey)

        ZStack {
            LiquidGlassBackdrop(accent: appearance?.accentColor ?? .green, highContrast: hc)

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                Circle().fill(courseColor).frame(width: 8, height: 8)
                                Text(current.disciplineName)
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(CampusTheme.accessibleTextColor(courseColor, scheme: colorScheme))
                            }
                            Text(current.title)
                                .font(.system(size: 22, weight: .bold, design: .rounded))
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
                                    .font(.system(size: 16, weight: .bold, design: .rounded))
                                    .foregroundStyle(courseColor)
                                Text(current.dueMonth)
                                    .font(.system(size: 10, weight: .bold, design: .rounded))
                                    .foregroundStyle(courseColor)
                            }
                            .frame(width: 48, height: 48)
                            .background(RoundedRectangle(cornerRadius: 12).fill(courseColor.opacity(0.16)))

                            VStack(alignment: .leading, spacing: 3) {
                                Label("Data: \(formattedDisplayDate(current.dueDate))", systemImage: "calendar")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                Label("Horário: \(current.dueTime)", systemImage: "clock")
                                    .font(.system(size: 13))
                                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            }
                        }

                        Divider()

                        HStack(alignment: .center, spacing: 8) {
                            Text(current.kind)
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                                .foregroundStyle(courseColor)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(Capsule().fill(courseColor.opacity(0.16)))

                            HStack(spacing: 5) {
                                Image(systemName: current.isCompleted ? "checkmark.circle.fill" : "circle")
                                    .font(.system(size: 12, weight: .semibold))
                                Text(current.isCompleted ? "Concluída" : "Pendente")
                                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                            }
                            .foregroundStyle(current.isCompleted ? accent : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
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
                                foreground: (appearance?.accentColor ?? .green).onPrimary(for: colorScheme),
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
                                    .font(.system(size: 14, weight: .bold, design: .rounded))
                            }
                        }
                        .padding(14)
                        .liquidGlassCard(cornerRadius: 16, highContrast: hc)
                    }

                    Button(role: .destructive) {
                        onRequestDelete(current)
                    } label: {
                        Label("Excluir avaliação", systemImage: "trash")
                            .font(.system(size: 14, weight: .semibold, design: .rounded))
                            .foregroundStyle(Color(hex: 0xEF4444))
                            .frame(maxWidth: .infinity, minHeight: appearance?.accessibility.minTouchHeight ?? 48)
                            .liquidGlassCapsule(selected: false, accent: Color(hex: 0xEF4444), highContrast: hc)
                    }
                    .buttonStyle(.plain)

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
                                .font(.system(size: 14, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                .frame(maxWidth: .infinity, minHeight: appearance?.accessibility.minTouchHeight ?? 48)
                                .liquidGlassCapsule(selected: false, accent: accent, highContrast: hc)
                        }
                        .buttonStyle(.plain)

                        LiquidGlassPrimaryButton(
                            tint: accent,
                            foreground: (appearance?.accentColor ?? .green).onPrimary(for: colorScheme),
                            minHeight: appearance?.accessibility.minTouchHeight ?? 48
                        ) {
                            _ = store.toggleAssessmentCompleted(assessmentId: current.id)
                            onClose()
                        } label: {
                            Text(current.isCompleted ? "Reabrir" : "Concluído")
                                .font(.system(size: 14, weight: .bold, design: .rounded))
                        }
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

// MARK: - Add Assessment Sheet (Native DatePicker + Liquid Glass)

public struct AddAssessmentSheetView: View {
    public let disciplines: [DisciplineSummaryCard]
    public let onSave: (String, String, String, String, String) -> Void
    public let onCancel: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedCourseId: String
    @State private var selectedKind: String = "Trabalho"
    @State private var title: String = ""
    @State private var dueDateTime: Date = Calendar.current.date(bySettingHour: 18, minute: 0, second: 0, of: Date()) ?? Date()

    private let kinds = ["Trabalho", "Seminário", "Prova"]

    public init(
        disciplines: [DisciplineSummaryCard],
        onSave: @escaping (String, String, String, String, String) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.disciplines = disciplines
        self.onSave = onSave
        self.onCancel = onCancel
        self._selectedCourseId = State(initialValue: disciplines.first?.id ?? "calc-ii")
    }

    public var body: some View {
        let accent = (appearance?.accentColor ?? .green).primary(for: colorScheme)
        let hc = appearance?.accessibility.highContrastMode ?? false

        ZStack {
            LiquidGlassBackdrop(accent: appearance?.accentColor ?? .green, highContrast: hc)

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    HStack {
                        Text("NOVA AVALIAÇÃO")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .tracking(1.4)
                            .foregroundStyle(accent)
                        Spacer()
                        Button(action: onCancel) {
                            Image(systemName: "xmark")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                .frame(width: 34, height: 34)
                                .liquidGlassCapsule(selected: false, accent: accent, highContrast: hc)
                        }
                        .buttonStyle(.plain)
                    }

                    Text("Cadastrar prazo acadêmico")
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(disciplines) { disc in
                                let isSelected = selectedCourseId == disc.id
                                let dColor = CampusTheme.departmentColor(for: disc.colorKey)
                                Button {
                                    selectedCourseId = disc.id
                                } label: {
                                    Text(disc.name)
                                        .font(.system(size: 12, weight: .semibold, design: .rounded))
                                        .foregroundStyle(isSelected ? .white : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 8)
                                        .liquidGlassCapsule(selected: isSelected, accent: dColor, highContrast: hc)
                                }
                                .buttonStyle(.plain)
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
                        .liquidGlassCard(cornerRadius: 12, highContrast: hc)

                    DatePicker("Data e hora de entrega", selection: $dueDateTime)
                        .tint(accent)
                        .padding(12)
                        .liquidGlassCard(cornerRadius: 14, highContrast: hc)

                    LiquidGlassPrimaryButton(
                        tint: accent,
                        foreground: (appearance?.accentColor ?? .green).onPrimary(for: colorScheme),
                        minHeight: appearance?.accessibility.minTouchHeight ?? 48
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
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                    }
                }
                .padding(20)
            }
        }
    }
}
