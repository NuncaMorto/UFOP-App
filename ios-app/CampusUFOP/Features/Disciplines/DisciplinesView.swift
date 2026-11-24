import SwiftUI

public struct DisciplinesView: View {
    @Bindable public var viewModel: DisciplinesViewModel
    public let onOpenLocationInMap: ((CampusMapMeetingRequest) -> Void)?

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var searchQuery: String = ""
    @State private var isMatrixPresented: Bool = false

    private var isRegular: Bool {
        horizontalSizeClass == .regular
    }

    private var gridColumns: [GridItem] {
        [GridItem(.adaptive(minimum: isRegular ? 200 : 140), spacing: 12)]
    }

    public init(
        viewModel: DisciplinesViewModel,
        onOpenLocationInMap: ((CampusMapMeetingRequest) -> Void)? = nil
    ) {
        self.viewModel = viewModel
        self.onOpenLocationInMap = onOpenLocationInMap
    }

    private var resolvedAccent: CampusAccentColor { appearance?.accentColor ?? .green }
    private var hc: Bool { appearance?.accessibility.highContrastMode ?? false }
    private var minTouch: CGFloat { appearance?.accessibility.minTouchHeight ?? 48 }
    private var isBarsMode: Bool { viewModel.store.disciplinesLayoutMode == "bars" }

    private var displayedDisciplines: [DisciplineSummaryCard] {
        let needle = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let base = viewModel.disciplines
        guard !needle.isEmpty else { return base }
        return base.filter { card in
            card.name.lowercased().contains(needle)
                || card.code.lowercased().contains(needle)
                || card.professor.lowercased().contains(needle)
                || card.department.lowercased().contains(needle)
        }
    }

    public var body: some View {
        ZStack {
            LiquidGlassBackdrop(accent: resolvedAccent, highContrast: hc)

            HStack(alignment: .top, spacing: 0) {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 16) {
                        headerSection
                        quickActionsRow
                        filtersAndLayoutRow
                        searchBarSection
                        disciplinesCollectionSection
                        footerLegendSection
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 18)
                    .padding(.bottom, isRegular ? 32 : 108)
                    .frame(maxWidth: .infinity, alignment: .topLeading)
                }

                if isRegular, let detail = viewModel.activeDisciplineDetail {
                    Divider()
                        .overlay(CampusTheme.cardBorder(for: colorScheme, highContrast: hc))

                    DisciplineDetailSheetView(
                        detail: detail,
                        store: viewModel.store,
                        onUpdateNotes: { updatedNotes in
                            viewModel.saveDisciplineNotes(disciplineId: detail.summary.id, notes: updatedNotes)
                        },
                        onDeleteCourse: {
                            viewModel.deleteCourse(courseId: detail.summary.id)
                        },
                        onOpenLocationInMap: onOpenLocationInMap.map { handler in
                            { request in
                                viewModel.dismissDisciplineDetail()
                                handler(request)
                            }
                        },
                        onClose: {
                            viewModel.dismissDisciplineDetail()
                        }
                    )
                    .frame(width: 420)
                    .transition(.move(edge: .trailing).combined(with: .opacity))
                }
            }
        }
        .sheet(item: selectedDisciplineBinding) { detail in
            DisciplineDetailSheetView(
                detail: detail,
                store: viewModel.store,
                onUpdateNotes: { updatedNotes in
                    viewModel.saveDisciplineNotes(disciplineId: detail.summary.id, notes: updatedNotes)
                },
                onDeleteCourse: {
                    viewModel.deleteCourse(courseId: detail.summary.id)
                },
                onOpenLocationInMap: onOpenLocationInMap.map { handler in
                    { request in
                        viewModel.dismissDisciplineDetail()
                        handler(request)
                    }
                },
                onClose: {
                    viewModel.dismissDisciplineDetail()
                }
            )
        }
        .sheet(isPresented: $viewModel.isTimetablePresented) {
            TimetableGridView(
                store: viewModel.store,
                onOpenLocationInMap: onOpenLocationInMap.map { handler in
                    { request in
                        viewModel.dismissTimetable()
                        handler(request)
                    }
                },
                onClose: {
                    viewModel.dismissTimetable()
                }
            )
        }
        .sheet(isPresented: $isMatrixPresented) {
            CurriculumMatrixView(
                store: viewModel.store,
                onClose: {
                    isMatrixPresented = false
                }
            )
        }
        .sheet(isPresented: $viewModel.isAddCoursePresented) {
            AddCourseSheetView(
                semester: viewModel.semesterFilter,
                onSave: { departmentId, name, professor, workloadHours, colorHex, isEad, initialSlot in
                    _ = try viewModel.createCourse(
                        semester: viewModel.semesterFilter,
                        departmentId: departmentId,
                        name: name,
                        professor: professor,
                        workloadHours: workloadHours,
                        colorOverrideHex: colorHex,
                        isEad: isEad,
                        initialSlot: initialSlot
                    )
                },
                onCancel: {
                    viewModel.dismissAddCourseSheet()
                }
            )
        }
        .onAppear {
            consumePendingTimetableRequest()
        }
        .onChange(of: viewModel.store.requestedTimetablePresentation) { _, requested in
            if requested {
                consumePendingTimetableRequest()
            }
        }
    }

    private func consumePendingTimetableRequest() {
        guard viewModel.store.requestedTimetablePresentation else { return }
        viewModel.store.requestedTimetablePresentation = false
        viewModel.openTimetable()
    }

    private var selectedDisciplineBinding: Binding<DisciplineDetail?> {
        Binding(
            get: { isRegular ? nil : viewModel.activeDisciplineDetail },
            set: { newValue in
                if newValue == nil {
                    viewModel.dismissDisciplineDetail()
                }
            }
        )
    }

    // MARK: - Header

    private var headerSection: some View {
        let primary = resolvedAccent.primary(for: colorScheme)
        let onPrimary = resolvedAccent.onPrimary(for: colorScheme)

        return HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                Text("SEMESTRE \(viewModel.semesterFilter)")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .tracking(1.0)
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

                Text("Disciplinas")
                    .font(.system(size: 32, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                Text("Seu acompanhamento de faltas")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
            }

            Spacer(minLength: 8)

            Button {
                viewModel.openAddCourseSheet()
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "plus")
                        .font(.system(size: 13, weight: .bold))
                    Text("Disciplina")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                }
                .foregroundStyle(onPrimary)
                .padding(.horizontal, 14)
                .frame(minHeight: 42)
                .liquidGlassCapsule(selected: true, accent: primary, interactive: true, highContrast: hc)
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
            .padding(.top, 10)
            .accessibilityLabel("Adicionar nova disciplina")
        }
    }

    // MARK: - Quick Actions ("Matriz" + "Grade")

    private var quickActionsRow: some View {
        let primary = resolvedAccent.primary(for: colorScheme)

        return LiquidGlassGroup(spacing: 10) {
            HStack(spacing: 10) {
                Button {
                    isMatrixPresented = true
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "book.pages.fill")
                            .font(.system(size: 13, weight: .semibold))
                        Text("Matriz")
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    .padding(.horizontal, 14)
                    .frame(minHeight: 40)
                    .liquidGlassCapsule(selected: false, accent: primary, interactive: true, highContrast: hc)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
                .accessibilityLabel("Abrir matriz curricular e cálculo de CRE")

                Button {
                    viewModel.openTimetable()
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "tablecells.fill")
                            .font(.system(size: 13, weight: .semibold))
                        Text("Grade")
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(resolvedAccent.onPrimary(for: colorScheme))
                    .padding(.horizontal, 14)
                    .frame(minHeight: 40)
                    .liquidGlassCapsule(selected: true, accent: primary, interactive: true, highContrast: hc)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
                .accessibilityLabel("Abrir grade horária semanal")

                Spacer(minLength: 0)
            }
        }
    }

    // MARK: - Filter Pills + Layout Switcher

    private var filtersAndLayoutRow: some View {
        let primary = resolvedAccent.primary(for: colorScheme)

        return LiquidGlassGroup(spacing: 8) {
            HStack(spacing: 8) {
                Menu {
                    ForEach(viewModel.availableDepartments, id: \.self) { department in
                        Button {
                            withAnimation(.snappy) {
                                viewModel.departmentFilter = department
                            }
                        } label: {
                            if viewModel.departmentFilter == department {
                                Label(department, systemImage: "checkmark")
                            } else {
                                Text(department)
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 6) {
                        Text(viewModel.departmentFilter)
                            .font(.system(size: 12, weight: .semibold))
                            .lineLimit(1)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 10, weight: .bold))
                    }
                    .foregroundStyle(
                        viewModel.departmentFilter == "Todos os departamentos"
                            ? CampusTheme.primaryText(for: colorScheme, highContrast: hc)
                            : CampusTheme.accessibleTextColor(primary, scheme: colorScheme)
                    )
                    .padding(.horizontal, 12)
                    .frame(minHeight: 38)
                    .liquidGlassCapsule(selected: false, accent: primary, interactive: true, highContrast: hc)
                }
                .minimumTouchTarget(minTouch)
                .accessibilityLabel("Filtrar departamento: \(viewModel.departmentFilter)")

                Menu {
                    ForEach(viewModel.availableSemesters, id: \.self) { semester in
                        Button {
                            withAnimation(.snappy) {
                                viewModel.semesterFilter = semester
                            }
                        } label: {
                            if viewModel.semesterFilter == semester {
                                Label(semester, systemImage: "checkmark")
                            } else {
                                Text(semester)
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 5) {
                        Text(viewModel.semesterFilter)
                            .font(.system(size: 12, weight: .semibold, design: .rounded))
                        Image(systemName: "chevron.down")
                            .font(.system(size: 10, weight: .bold))
                    }
                    .foregroundStyle(CampusTheme.accessibleTextColor(primary, scheme: colorScheme))
                    .padding(.horizontal, 12)
                    .frame(minHeight: 38)
                    .liquidGlassCapsule(selected: false, accent: primary, interactive: true, highContrast: hc)
                }
                .minimumTouchTarget(minTouch)
                .accessibilityLabel("Filtrar semestre: \(viewModel.semesterFilter)")

                Spacer(minLength: 4)

                // Segmented Layout Toggle: Bloco (grid) vs Barras (bars)
                HStack(spacing: 4) {
                    layoutToggleButton(mode: "grid", systemImage: "square.grid.2x2.fill", label: "Visualização em blocos")
                    layoutToggleButton(mode: "bars", systemImage: "list.bullet", label: "Visualização em barras")
                }
                .padding(3)
                .liquidGlassCapsule(selected: false, accent: primary, interactive: false, highContrast: hc)
            }
        }
    }

    private func layoutToggleButton(mode: String, systemImage: String, label: String) -> some View {
        let primary = resolvedAccent.primary(for: colorScheme)
        let isSelected = viewModel.store.disciplinesLayoutMode == mode

        return Button {
            withAnimation(.spring(response: 0.25, dampingFraction: 0.85)) {
                viewModel.store.disciplinesLayoutMode = mode
            }
        } label: {
            Image(systemName: systemImage)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(isSelected ? resolvedAccent.onPrimary(for: colorScheme) : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                .frame(width: 34, height: 32)
                .background(
                    Capsule(style: .continuous)
                        .fill(isSelected ? primary : Color.clear)
                )
        }
        .buttonStyle(.plain)
        .frame(minWidth: 40, minHeight: 36)
        .contentShape(Rectangle())
        .accessibilityLabel(label)
    }

    // MARK: - Search Bar

    private var searchBarSection: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

            TextField("Buscar disciplina ou professor...", text: $searchQuery)
                .font(.system(size: 14, weight: .regular))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()

            if !searchQuery.isEmpty {
                Button {
                    searchQuery = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 15))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
                }
                .buttonStyle(.plain)
                .frame(minWidth: 32, minHeight: 32)
                .accessibilityLabel("Limpar busca")
            }
        }
        .padding(.horizontal, 14)
        .frame(minHeight: 46)
        .liquidGlassCapsule(selected: false, accent: resolvedAccent.primary(for: colorScheme), interactive: false, highContrast: hc)
    }

    // MARK: - Disciplines Collection (Grid vs Bars with distinct keys)

    @ViewBuilder
    private var disciplinesCollectionSection: some View {
        let items = displayedDisciplines
        if items.isEmpty {
            VStack(spacing: 8) {
                Image(systemName: "books.vertical")
                    .font(.system(size: 28, weight: .light))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

                Text("Nenhuma disciplina encontrada")
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                Text("Ajuste os filtros ou toque em + Disciplina.")
                    .font(.system(size: 13))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
            }
            .frame(maxWidth: .infinity)
            .padding(28)
            .liquidGlassCard(cornerRadius: 20, highContrast: hc)
        } else if isBarsMode {
            LazyVStack(spacing: 12) {
                ForEach(items) { card in
                    Button {
                        viewModel.openDisciplineDetail(card)
                    } label: {
                        disciplineBarCard(card)
                    }
                    .buttonStyle(.plain)
                    .campusHoverLift()
                    .id("bars-\(card.id)")
                }
            }
        } else {
            LazyVGrid(columns: gridColumns, spacing: 12) {
                ForEach(items) { card in
                    Button {
                        viewModel.openDisciplineDetail(card)
                    } label: {
                        disciplineGridCard(card)
                    }
                    .buttonStyle(.plain)
                    .campusHoverLift()
                    .id("grid-\(card.id)")
                }
            }
        }
    }

    // MARK: - Grid Card (2 columns, compact, no "f..." clipping or "Controlada" clutter)

    private func disciplineGridCard(_ card: DisciplineSummaryCard) -> some View {
        let deptColor = CampusTheme.departmentColor(for: card.colorKey)
        let riskLevel = CampusTheme.semanticAbsenceRiskLevel(currentAbsences: card.currentAbsences, maxAbsences: card.maxAbsences)
        let riskColor = CampusTheme.semanticAbsenceRiskColor(currentAbsences: card.currentAbsences, maxAbsences: card.maxAbsences, scheme: colorScheme)
        let barColor = riskLevel > 0 ? riskColor : deptColor
        let progress = card.maxAbsences > 0 ? min(1.0, max(0.0, Double(card.currentAbsences) / Double(card.maxAbsences))) : 0
        let isEad = viewModel.store.isCourseEad(card.id)

        return HStack(spacing: 0) {
            RoundedRectangle(cornerRadius: 3, style: .continuous)
                .fill(deptColor)
                .frame(width: 4)
                .padding(.vertical, 10)
                .padding(.leading, 4)

            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 5) {
                    Text(card.code)
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(
                            Capsule(style: .continuous)
                                .fill(CampusTheme.departmentSoftTint(for: card.colorKey, scheme: colorScheme))
                        )

                    if isEad {
                        Text("EAD")
                            .font(.system(size: 10, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.mathBlue)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(CampusTheme.mathBlue.opacity(colorScheme == .dark ? 0.22 : 0.14))
                            )
                    }

                    Spacer(minLength: 0)
                }

                Text(card.name)
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                    .minimumScaleFactor(0.9)
                    .frame(maxWidth: .infinity, minHeight: 40, alignment: .topLeading)

                VStack(alignment: .leading, spacing: 6) {
                    HStack(alignment: .firstTextBaseline, spacing: 2) {
                        Text("\(card.currentAbsences)")
                            .font(.system(size: 21, weight: .bold, design: .rounded))
                            .foregroundStyle(riskLevel > 0 ? riskColor : CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                        Text("/\(card.maxAbsences) faltas")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)

                        Spacer(minLength: 2)
                    }

                    if riskLevel > 0 {
                        HStack(spacing: 4) {
                            Image(systemName: CampusTheme.semanticAbsenceRiskIcon(currentAbsences: card.currentAbsences, maxAbsences: card.maxAbsences))
                                .font(.system(size: 10, weight: .bold))
                            Text(CampusTheme.semanticAbsenceRiskLabel(currentAbsences: card.currentAbsences, maxAbsences: card.maxAbsences))
                                .font(.system(size: 10, weight: .bold, design: .rounded))
                        }
                        .foregroundStyle(riskColor)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(
                            Capsule(style: .continuous)
                                .fill(riskColor.opacity(colorScheme == .dark ? 0.22 : 0.14))
                        )
                    }

                    GeometryReader { proxy in
                        ZStack(alignment: .leading) {
                            Capsule(style: .continuous)
                                .fill(CampusTheme.cardBorder(for: colorScheme, highContrast: hc))
                                .frame(height: 5)

                            if progress > 0 {
                                Capsule(style: .continuous)
                                    .fill(barColor)
                                    .frame(width: max(8, proxy.size.width * progress), height: 5)
                            }
                        }
                    }
                    .frame(height: 5)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 14)
        }
        .frame(maxWidth: .infinity, minHeight: 162, alignment: .topLeading)
        .liquidGlassCard(cornerRadius: 20, tint: deptColor, interactive: true, highContrast: hc)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(card.name), código \(card.code), \(card.currentAbsences) de \(card.maxAbsences) faltas")
    }

    // MARK: - Bar Card (Full width)

    private func disciplineBarCard(_ card: DisciplineSummaryCard) -> some View {
        let deptColor = CampusTheme.departmentColor(for: card.colorKey)
        let riskLevel = CampusTheme.semanticAbsenceRiskLevel(currentAbsences: card.currentAbsences, maxAbsences: card.maxAbsences)
        let riskColor = CampusTheme.semanticAbsenceRiskColor(currentAbsences: card.currentAbsences, maxAbsences: card.maxAbsences, scheme: colorScheme)
        let barColor = riskLevel > 0 ? riskColor : deptColor
        let progress = card.maxAbsences > 0 ? min(1.0, max(0.0, Double(card.currentAbsences) / Double(card.maxAbsences))) : 0
        let isEad = viewModel.store.isCourseEad(card.id)
        let cleanProf = card.professor.trimmingCharacters(in: .whitespacesAndNewlines)

        return HStack(spacing: 0) {
            RoundedRectangle(cornerRadius: 3, style: .continuous)
                .fill(deptColor)
                .frame(width: 4)
                .padding(.vertical, 10)
                .padding(.leading, 4)

            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 5) {
                        HStack(spacing: 6) {
                            Text(card.code)
                                .font(.system(size: 11, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(
                                    Capsule(style: .continuous)
                                        .fill(CampusTheme.departmentSoftTint(for: card.colorKey, scheme: colorScheme))
                                )

                            if isEad {
                                Text("A distância (EAD)")
                                    .font(.system(size: 10, weight: .bold, design: .rounded))
                                    .foregroundStyle(CampusTheme.mathBlue)
                                    .padding(.horizontal, 7)
                                    .padding(.vertical, 3)
                                    .background(
                                        Capsule(style: .continuous)
                                            .fill(CampusTheme.mathBlue.opacity(colorScheme == .dark ? 0.22 : 0.14))
                                    )
                            }
                        }

                        Text(card.name)
                            .font(.system(size: 17, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)

                        if !card.location.isEmpty || !cleanProf.isEmpty {
                            HStack(spacing: 6) {
                                Image(systemName: isEad ? "laptopcomputer" : "clock")
                                    .font(.system(size: 11))
                                Text(cleanProf.isEmpty ? card.location : "\(card.location) · \(cleanProf)")
                                    .font(.system(size: 12, weight: .medium))
                                    .lineLimit(1)
                            }
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                        }
                    }

                    Spacer(minLength: 6)

                    VStack(alignment: .trailing, spacing: 4) {
                        HStack(alignment: .firstTextBaseline, spacing: 1) {
                            Text("\(card.currentAbsences)")
                                .font(.system(size: 22, weight: .bold, design: .rounded))
                                .foregroundStyle(riskLevel > 0 ? riskColor : CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                            Text("/\(card.maxAbsences)")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                        }
                        Text("faltas")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))

                        if riskLevel > 0 {
                            HStack(spacing: 4) {
                                Image(systemName: CampusTheme.semanticAbsenceRiskIcon(currentAbsences: card.currentAbsences, maxAbsences: card.maxAbsences))
                                    .font(.system(size: 10, weight: .bold))
                                Text(CampusTheme.semanticAbsenceRiskLabel(currentAbsences: card.currentAbsences, maxAbsences: card.maxAbsences))
                                    .font(.system(size: 10, weight: .bold, design: .rounded))
                            }
                            .foregroundStyle(riskColor)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 3)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(riskColor.opacity(colorScheme == .dark ? 0.22 : 0.14))
                            )
                        }
                    }
                }

                GeometryReader { proxy in
                    ZStack(alignment: .leading) {
                        Capsule(style: .continuous)
                            .fill(CampusTheme.cardBorder(for: colorScheme, highContrast: hc))
                            .frame(height: 5)

                        if progress > 0 {
                            Capsule(style: .continuous)
                                .fill(barColor)
                                .frame(width: max(8, proxy.size.width * progress), height: 5)
                        }
                    }
                }
                .frame(height: 5)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 14)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .liquidGlassCard(cornerRadius: 20, tint: deptColor, interactive: true, highContrast: hc)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(card.name), \(card.currentAbsences) de \(card.maxAbsences) faltas")
    }

    // MARK: - Footer

    private var footerLegendSection: some View {
        let totalCancelled = viewModel.disciplines.reduce(0) { $0 + $1.cancelledClasses }

        return VStack(alignment: .leading, spacing: 6) {
            Text("Aulas canceladas (\(totalCancelled)) não somam faltas. As marcações salvas na Agenda atualizam estes relatórios.")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

            Text("Cores por departamento · semáforo acessível de faltas.")
                .font(.system(size: 11, weight: .regular))
                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
        }
        .padding(.top, 4)
    }
}

#Preview("DisciplinesView - Light") {
    let store = CampusStore()
    DisciplinesView(viewModel: DisciplinesViewModel(store: store))
        .preferredColorScheme(.light)
}

#Preview("DisciplinesView - Dark") {
    let store = CampusStore()
    DisciplinesView(viewModel: DisciplinesViewModel(store: store))
        .preferredColorScheme(.dark)
}
