import SwiftUI

public struct DisciplinesView: View {
    @Bindable public var viewModel: DisciplinesViewModel
    @Environment(\.colorScheme) private var colorScheme
    @State private var searchText: String = ""

    private let gridColumns: [GridItem] = [
        GridItem(.flexible(), spacing: 14),
        GridItem(.flexible(), spacing: 14)
    ]

    public init(viewModel: DisciplinesViewModel) {
        self.viewModel = viewModel
    }

    private var trimmedSearchQuery: String {
        searchText.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var displayedDisciplines: [DisciplineSummaryCard] {
        let base = viewModel.disciplines
        guard !trimmedSearchQuery.isEmpty else { return base }
        let needle = Self.normalizedSearchToken(trimmedSearchQuery)
        return base.filter { card in
            Self.normalizedSearchToken(card.name).contains(needle)
                || Self.normalizedSearchToken(card.professor).contains(needle)
                || Self.normalizedSearchToken(card.code).contains(needle)
                || Self.normalizedSearchToken(card.department).contains(needle)
        }
    }

    private static func normalizedSearchToken(_ text: String) -> String {
        text.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
    }

    public var body: some View {
        NavigationStack {
            ZStack(alignment: .bottomTrailing) {
                CampusTheme.appBackground(for: colorScheme)
                    .ignoresSafeArea()

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 20) {
                        headerSection

                        searchBar

                        filtersBar

                        disciplinesGrid

                        footerInfoCard
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 96)
                }

                floatingTimetableButton
                    .padding(.trailing, 20)
                    .padding(.bottom, 22)
            }
            #if os(iOS)
            .navigationBarHidden(true)
            #endif
            .sheet(
                isPresented: Binding(
                    get: { viewModel.activeDisciplineDetail != nil },
                    set: { isPresented in
                        if !isPresented {
                            viewModel.dismissDisciplineDetail()
                        }
                    }
                )
            ) {
                if let detail = viewModel.activeDisciplineDetail {
                    DisciplineDetailSheetView(
                        detail: detail,
                        store: viewModel.store,
                        onUpdateNotes: { newNotes in
                            viewModel.saveDisciplineNotes(
                                disciplineId: detail.summary.id,
                                notes: newNotes
                            )
                        },
                        onDeleteCourse: {
                            viewModel.deleteCourse(courseId: detail.summary.id)
                        },
                        onClose: {
                            viewModel.dismissDisciplineDetail()
                        }
                    )
                    .presentationDetents([.large, .fraction(0.88)])
                    .presentationDragIndicator(.visible)
                    .presentationCornerRadius(28)
                }
            }
            .sheet(isPresented: $viewModel.isTimetablePresented) {
                TimetableGridView(
                    store: viewModel.store,
                    onClose: {
                        viewModel.dismissTimetable()
                    }
                )
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
            }
            .sheet(isPresented: $viewModel.isAddCoursePresented) {
                AddCourseSheetView(
                    semester: viewModel.semesterFilter,
                    onSave: { departmentId, name, professor, workloadHours, colorOverrideHex, initialSlot in
                        _ = try viewModel.createCourse(
                            semester: viewModel.semesterFilter,
                            departmentId: departmentId,
                            name: name,
                            professor: professor,
                            workloadHours: workloadHours,
                            colorOverrideHex: colorOverrideHex,
                            initialSlot: initialSlot
                        )
                    },
                    onCancel: {
                        viewModel.dismissAddCourseSheet()
                    }
                )
                .presentationDetents([.large, .fraction(0.92)])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
            }
        }
    }

    // MARK: - Header ("SEMESTRE 2026.2", "Disciplinas", "Seu acompanhamento de faltas" + "+ Disciplina")

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .center) {
                Text("SEMESTRE \(viewModel.semesterFilter)")
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .tracking(1.5)
                    .foregroundStyle(CampusTheme.mathBlue)

                Spacer()

                Button {
                    viewModel.openAddCourseSheet()
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "plus")
                            .font(.system(size: 11, weight: .bold))
                        Text("Disciplina")
                            .font(.system(size: 12, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(CampusTheme.selectedPillText(for: colorScheme))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(
                        Capsule(style: .continuous)
                            .fill(CampusTheme.selectedPillBackground(for: colorScheme))
                    )
                    .overlay(
                        Capsule(style: .continuous)
                            .strokeBorder(
                                CampusTheme.selectedPillText(for: colorScheme).opacity(0.25),
                                lineWidth: 1
                            )
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Adicionar disciplina")
            }

            Text("Disciplinas")
                .font(.system(size: 30, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

            Text("Seu acompanhamento de faltas")
                .font(.system(size: 15, weight: .regular))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
        }
    }

    // MARK: - Quick Search Bar ("Buscar disciplina ou professor")

    private var searchBar: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(CampusTheme.mutedText(for: colorScheme))

            TextField("Buscar disciplina ou professor", text: $searchText)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                .autocorrectionDisabled(true)
                #if os(iOS)
                .textInputAutocapitalization(.never)
                #endif
                .accessibilityLabel("Buscar disciplina ou professor")

            if !searchText.isEmpty {
                Button {
                    withAnimation(.snappy) {
                        searchText = ""
                    }
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Limpar busca")
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(CampusTheme.cardBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
        )
    }

    // MARK: - Filters ("Todos os departamentos" + "2026.2")

    private var filtersBar: some View {
        HStack(spacing: 10) {
            Menu {
                ForEach(viewModel.availableDepartments, id: \.self) { dept in
                    Button {
                        withAnimation(.snappy) {
                            viewModel.departmentFilter = dept
                        }
                    } label: {
                        HStack {
                            Text(dept)
                            if viewModel.departmentFilter == dept {
                                Image(systemName: "checkmark")
                            }
                        }
                    }
                }
            } label: {
                HStack(spacing: 8) {
                    Text(viewModel.departmentFilter)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                        .lineLimit(1)

                    Image(systemName: "chevron.down")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(
                    Capsule(style: .continuous)
                        .fill(CampusTheme.cardBackground(for: colorScheme))
                )
                .overlay(
                    Capsule(style: .continuous)
                        .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
                )
            }

            Menu {
                ForEach(viewModel.availableSemesters, id: \.self) { sem in
                    Button {
                        withAnimation(.snappy) {
                            viewModel.semesterFilter = sem
                        }
                    } label: {
                        HStack {
                            Text(sem)
                            if viewModel.semesterFilter == sem {
                                Image(systemName: "checkmark")
                            }
                        }
                    }
                }
            } label: {
                HStack(spacing: 6) {
                    Text(viewModel.semesterFilter)
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                    Image(systemName: "chevron.down")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(
                    Capsule(style: .continuous)
                        .fill(CampusTheme.cardBackground(for: colorScheme))
                )
                .overlay(
                    Capsule(style: .continuous)
                        .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
                )
            }

            Spacer(minLength: 0)
        }
    }

    // MARK: - 2x2 LazyVGrid

    @ViewBuilder
    private var disciplinesGrid: some View {
        let cards = displayedDisciplines

        if viewModel.disciplines.isEmpty {
            VStack(spacing: 12) {
                Image(systemName: "books.vertical.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(CampusTheme.mathBlue)

                Text("Nenhuma disciplina neste filtro")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Text("Cadastre uma disciplina em \(viewModel.semesterFilter) ou aplique um template oficial da UFOP na Grade de horários.")
                    .font(.system(size: 13))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                    .multilineTextAlignment(.center)

                HStack(spacing: 10) {
                    Button {
                        viewModel.openAddCourseSheet()
                    } label: {
                        Label("Nova disciplina", systemImage: "plus.circle.fill")
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 9)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(CampusTheme.mathBlue)
                            )
                    }
                    .buttonStyle(.plain)

                    Button {
                        viewModel.openTimetable()
                    } label: {
                        Label("Templates UFOP", systemImage: "square.grid.3x2.fill")
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.selectedPillText(for: colorScheme))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 9)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(CampusTheme.selectedPillBackground(for: colorScheme))
                            )
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, 4)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 32)
            .padding(.horizontal, 20)
            .background(
                RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                    .fill(CampusTheme.cardBackground(for: colorScheme))
            )
            .overlay(
                RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                    .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
            )
        } else if cards.isEmpty {
            VStack(spacing: 12) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(CampusTheme.mathBlue)

                Text("Nenhuma disciplina encontrada")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Text("Não encontramos resultados para \"\(trimmedSearchQuery)\" no filtro atual.")
                    .font(.system(size: 13))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                    .multilineTextAlignment(.center)

                Button {
                    withAnimation(.snappy) {
                        searchText = ""
                    }
                } label: {
                    Label("Limpar busca", systemImage: "xmark.circle.fill")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.selectedPillText(for: colorScheme))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .background(
                            Capsule(style: .continuous)
                                .fill(CampusTheme.selectedPillBackground(for: colorScheme))
                        )
                }
                .buttonStyle(.plain)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 28)
            .padding(.horizontal, 20)
            .background(
                RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                    .fill(CampusTheme.cardBackground(for: colorScheme))
            )
            .overlay(
                RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                    .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
            )
        } else {
            LazyVGrid(columns: gridColumns, spacing: 14) {
                ForEach(cards) { card in
                    disciplineGridCard(card: card)
                }
            }
        }
    }

    private func disciplineGridCard(card: DisciplineSummaryCard) -> some View {
        let accent = CampusTheme.departmentColor(for: card.colorKey)

        return Button {
            viewModel.openDisciplineDetail(card)
        } label: {
            HStack(spacing: 0) {
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(accent)
                    .frame(width: CampusTheme.leftAccentBarWidth)
                    .padding(.vertical, 12)
                    .padding(.leading, 10)

                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .center) {
                        Text(card.department)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(accent)
                            .lineLimit(1)

                        Spacer(minLength: 4)

                        Text(card.shortWorkloadLabel)
                            .font(.system(size: 11, weight: .semibold, design: .rounded))
                            .foregroundStyle(CampusTheme.mutedText(for: colorScheme))
                    }

                    Text(card.name)
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, minHeight: 38, alignment: .topLeading)

                    VStack(alignment: .leading, spacing: 6) {
                        HStack(alignment: .firstTextBaseline, spacing: 4) {
                            Text(card.compactRatioText)
                                .font(.system(size: 22, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                            Text("faltas")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                        }

                        GeometryReader { proxy in
                            ZStack(alignment: .leading) {
                                Capsule(style: .continuous)
                                    .fill(CampusTheme.secondarySurface(for: colorScheme))
                                    .frame(height: 6)

                                Capsule(style: .continuous)
                                    .fill(accent)
                                    .frame(
                                        width: max(6, proxy.size.width * card.absenceProgress),
                                        height: 6
                                    )
                            }
                        }
                        .frame(height: 6)

                        Text(
                            card.cancelledClasses > 0
                                ? "\(card.cancelledClasses) cancelada(s) · restam \(card.remainingAbsences)"
                                : "Restam \(card.remainingAbsences) faltas · \(card.shortWorkloadLabel)"
                        )
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(CampusTheme.mutedText(for: colorScheme))
                        .lineLimit(1)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 14)
            }
            .background(
                RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                    .fill(CampusTheme.cardBackground(for: colorScheme))
            )
            .overlay(
                RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                    .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
            )
            .shadow(
                color: CampusTheme.cardShadow(for: colorScheme),
                radius: 8,
                x: 0,
                y: 4
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Informative Footer

    private var footerInfoCard: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "checkmark.shield.fill")
                .font(.system(size: 16))
                .foregroundStyle(CampusTheme.mathBlue)
                .padding(.top, 2)

            VStack(alignment: .leading, spacing: 4) {
                Text("Limite de 25% da carga horária · UFOP")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Text("Toque em uma disciplina para consultar prazos, avaliações e anotações. Aulas canceladas não somam no contador de faltas.")
                    .font(.system(size: 12, weight: .regular))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                .fill(CampusTheme.cardBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
        )
    }

    // MARK: - Dark Floating Action Button (Bottom-Right -> Opens TimetableGridView)

    private var floatingTimetableButton: some View {
        Button {
            viewModel.openTimetable()
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "calendar.day.timeline.left")
                    .font(.system(size: 15, weight: .semibold))

                Text("Grade de horários")
                    .font(.system(size: 14, weight: .bold, design: .rounded))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
            .background(
                Capsule(style: .continuous)
                    .fill(CampusTheme.floatingButtonBackground(for: colorScheme))
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(Color.white.opacity(0.14), lineWidth: 1)
            )
            .shadow(
                color: Color.black.opacity(0.28),
                radius: 12,
                x: 0,
                y: 6
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Abrir Grade de horários")
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
