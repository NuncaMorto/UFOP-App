import SwiftUI

public struct ActivitiesView: View {
    @Environment(CampusStore.self) private var store
    @Environment(\.colorScheme) private var colorScheme
    @State private var selectedFilter: String = "Pendentes"
    @State private var selectedKindFilter: String = "Todos"
    @State private var isAddAssessmentSheetPresented: Bool = false

    private let filterOptions = ["Pendentes", "Concluídas", "Todas"]
    private let kindFilterOptions = ["Todos", "Trabalho", "Seminário", "Prova"]

    public init() {}

    private var filteredActivities: [AcademicActivityItem] {
        store.academicActivities.filter { item in
            let matchesStatus: Bool
            switch selectedFilter {
            case "Pendentes":
                matchesStatus = !item.isCompleted
            case "Concluídas":
                matchesStatus = item.isCompleted
            default:
                matchesStatus = true
            }

            let matchesKind = selectedKindFilter == "Todos"
                || item.kind.localizedCaseInsensitiveCompare(selectedKindFilter) == .orderedSame

            return matchesStatus && matchesKind
        }
    }

    public var body: some View {
        NavigationStack {
            ZStack {
                CampusTheme.appBackground(for: colorScheme)
                    .ignoresSafeArea()

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 20) {
                        headerSection

                        filterPills

                        kindFilterPills

                        activitiesListSection
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 32)
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
                .presentationDetents([.large, .fraction(0.84)])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
            }
        }
    }

    // MARK: - Header

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .center) {
                Text("SEMESTRE 2026.2")
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .tracking(1.5)
                    .foregroundStyle(CampusTheme.mathBlue)

                Spacer()

                Button {
                    isAddAssessmentSheetPresented = true
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "plus")
                            .font(.system(size: 11, weight: .bold))
                        Text("Nova avaliação")
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
                .accessibilityLabel("Adicionar avaliação")
            }

            Text("Atividades")
                .font(.system(size: 30, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

            Text("Prazos, listas e avaliações sincronizados com suas disciplinas")
                .font(.system(size: 15, weight: .regular))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
        }
    }

    // MARK: - Filter Pills (Pendentes / Concluídas / Todas)

    private var filterPills: some View {
        HStack(spacing: 8) {
            ForEach(filterOptions, id: \.self) { option in
                let isSelected = selectedFilter == option
                Button {
                    withAnimation(.snappy) {
                        selectedFilter = option
                    }
                } label: {
                    Text(option)
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundStyle(
                            isSelected
                                ? CampusTheme.selectedPillText(for: colorScheme)
                                : CampusTheme.secondaryText(for: colorScheme)
                        )
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(
                            Capsule(style: .continuous)
                                .fill(
                                    isSelected
                                        ? CampusTheme.selectedPillBackground(for: colorScheme)
                                        : CampusTheme.cardBackground(for: colorScheme)
                                )
                        )
                        .overlay(
                            Capsule(style: .continuous)
                                .strokeBorder(
                                    isSelected
                                        ? CampusTheme.selectedPillText(for: colorScheme).opacity(0.28)
                                        : CampusTheme.cardBorder(for: colorScheme),
                                    lineWidth: 1
                                )
                        )
                }
                .buttonStyle(.plain)
            }

            Spacer()
        }
    }

    // MARK: - Kind Filter Pills (Todos / Trabalho / Seminário / Prova)

    private var kindFilterPills: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(kindFilterOptions, id: \.self) { kind in
                    let isSelected = selectedKindFilter == kind
                    Button {
                        withAnimation(.snappy) {
                            selectedKindFilter = kind
                        }
                    } label: {
                        Text(kind == "Todos" ? "Todos os tipos" : kind)
                            .font(.system(size: 12, weight: .semibold, design: .rounded))
                            .foregroundStyle(
                                isSelected
                                    ? .white
                                    : CampusTheme.secondaryText(for: colorScheme)
                            )
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(
                                        isSelected
                                            ? CampusTheme.mathBlue
                                            : CampusTheme.secondarySurface(for: colorScheme)
                                    )
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - Activities List

    @ViewBuilder
    private var activitiesListSection: some View {
        if filteredActivities.isEmpty {
            VStack(spacing: 12) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(CampusTheme.mathBlue)

                Text("Tudo em dia")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Text("Nenhuma atividade encontrada neste filtro.")
                    .font(.system(size: 14))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

                Button {
                    isAddAssessmentSheetPresented = true
                } label: {
                    Label("Cadastrar Trabalho, Seminário ou Prova", systemImage: "plus.circle.fill")
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundStyle(CampusTheme.mathBlue)
                        .padding(.top, 2)
                }
                .buttonStyle(.plain)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 36)
            .background(
                RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                    .fill(CampusTheme.cardBackground(for: colorScheme))
            )
            .overlay(
                RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                    .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
            )
        } else {
            VStack(spacing: 12) {
                ForEach(filteredActivities) { item in
                    activityRow(item: item)
                }
            }
        }
    }

    private func activityRow(item: AcademicActivityItem) -> some View {
        let accent = CampusTheme.departmentColor(for: item.colorKey)

        return AccentBarCardContainer(accentColor: accent) {
            HStack(spacing: 14) {
                VStack(spacing: 1) {
                    Text(item.dueDay)
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(accent)

                    Text(item.dueMonth)
                        .font(.system(size: 10, weight: .bold, design: .rounded))
                        .foregroundStyle(accent)
                }
                .frame(width: 46, height: 46)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(CampusTheme.departmentSoftTint(for: item.colorKey, scheme: colorScheme))
                )

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(item.disciplineName)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(accent)

                        Text("•")
                            .foregroundStyle(CampusTheme.mutedText(for: colorScheme))

                        Text(item.kind)
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                    }

                    Text(item.title)
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                        .strikethrough(item.isCompleted, color: CampusTheme.mutedText(for: colorScheme))

                    Text(item.categoryLabel)
                        .font(.system(size: 12, weight: .regular))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                }

                Spacer()

                Button {
                    withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                        _ = store.toggleAssessmentCompleted(assessmentId: item.id)
                    }
                } label: {
                    Image(systemName: item.isCompleted ? "checkmark.circle.fill" : "circle")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(
                            item.isCompleted
                                ? CampusTheme.mathBlue
                                : CampusTheme.mutedText(for: colorScheme)
                        )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(item.isCompleted ? "Marcar como pendente" : "Marcar como concluída")
            }
        }
        .contextMenu {
            Button {
                withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                    _ = store.toggleAssessmentCompleted(assessmentId: item.id)
                }
            } label: {
                Label(
                    item.isCompleted ? "Marcar como pendente" : "Marcar como concluída",
                    systemImage: item.isCompleted ? "arrow.uturn.backward.circle" : "checkmark.circle"
                )
            }

            Button(role: .destructive) {
                withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                    store.deleteAssessment(assessmentId: item.id)
                }
            } label: {
                Label("Excluir avaliação", systemImage: "trash")
            }
        }
    }
}

// MARK: - Add Assessment Modal Sheet (Sprint 2)

public struct AddAssessmentSheetView: View {
    public let disciplines: [DisciplineSummaryCard]
    public let onSave: (String, String, String, String, String) -> Void
    public let onCancel: () -> Void

    @State private var selectedCourseId: String
    @State private var selectedKind: String = "Trabalho"
    @State private var title: String = ""
    @State private var dueDate: String = "29 NOV"
    @State private var dueTime: String = "18:00"
    @Environment(\.colorScheme) private var colorScheme

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
        ZStack {
            CampusTheme.cardBackground(for: colorScheme)
                .ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 20) {
                    HStack {
                        Text("NOVA AVALIAÇÃO · 2026.2")
                            .font(.system(size: 12, weight: .bold, design: .rounded))
                            .tracking(1.4)
                            .foregroundStyle(CampusTheme.mathBlue)

                        Spacer()

                        Button(action: onCancel) {
                            Image(systemName: "xmark")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                                .frame(width: 32, height: 32)
                                .background(
                                    Circle()
                                        .fill(CampusTheme.secondarySurface(for: colorScheme))
                                )
                        }
                        .buttonStyle(.plain)
                    }

                    Text("Cadastrar prazo acadêmico")
                        .font(.system(size: 26, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                    // Seleção de disciplina
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Disciplina")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(disciplines) { disc in
                                    let isSelected = selectedCourseId == disc.id
                                    let accent = CampusTheme.departmentColor(for: disc.colorKey)
                                    Button {
                                        selectedCourseId = disc.id
                                    } label: {
                                        Text(disc.name)
                                            .font(.system(size: 13, weight: .semibold, design: .rounded))
                                            .foregroundStyle(isSelected ? .white : CampusTheme.primaryText(for: colorScheme))
                                            .padding(.horizontal, 14)
                                            .padding(.vertical, 9)
                                            .background(
                                                Capsule(style: .continuous)
                                                    .fill(isSelected ? accent : CampusTheme.secondarySurface(for: colorScheme))
                                            )
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }

                    // Seleção de tipo: Trabalho / Seminário / Prova
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Categoria da avaliação")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

                        HStack(spacing: 8) {
                            ForEach(kinds, id: \.self) { kind in
                                let isSelected = selectedKind == kind
                                Button {
                                    selectedKind = kind
                                } label: {
                                    Text(kind)
                                        .font(.system(size: 13, weight: .bold, design: .rounded))
                                        .foregroundStyle(isSelected ? .white : CampusTheme.primaryText(for: colorScheme))
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 10)
                                        .background(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .fill(isSelected ? CampusTheme.mathBlue : CampusTheme.secondarySurface(for: colorScheme))
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 14) {
                        inputGroup(label: "Título", text: $title, placeholder: "Ex.: Trabalho Prático · Grafos")

                        HStack(spacing: 12) {
                            inputGroup(label: "Data (DD MMM)", text: $dueDate, placeholder: "29 NOV")
                            inputGroup(label: "Horário", text: $dueTime, placeholder: "18:00")
                        }
                    }

                    HStack(spacing: 12) {
                        Button(action: onCancel) {
                            Text("Cancelar")
                                .font(.system(size: 15, weight: .semibold, design: .rounded))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .fill(CampusTheme.secondarySurface(for: colorScheme))
                                )
                        }
                        .buttonStyle(.plain)

                        Button {
                            let finalTitle = title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                ? "\(selectedKind) avaliativo"
                                : title
                            onSave(selectedCourseId, finalTitle, selectedKind, dueDate, dueTime)
                        } label: {
                            Text("Salvar avaliação")
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .fill(CampusTheme.mathBlue)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.top, 4)
                }
                .padding(.horizontal, 22)
                .padding(.top, 18)
                .padding(.bottom, 30)
            }
        }
    }

    private func inputGroup(label: String, text: Binding<String>, placeholder: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

            TextField(placeholder, text: text)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(CampusTheme.appBackground(for: colorScheme))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
                )
        }
    }
}

#Preview("ActivitiesView - Light") {
    ActivitiesView()
        .environment(CampusStore())
        .preferredColorScheme(.light)
}

#Preview("ActivitiesView - Dark") {
    ActivitiesView()
        .environment(CampusStore())
        .preferredColorScheme(.dark)
}
