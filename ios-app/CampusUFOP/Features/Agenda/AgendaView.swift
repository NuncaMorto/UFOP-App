import SwiftUI

public struct AgendaView: View {
    @Bindable public var viewModel: AgendaViewModel
    @Environment(\.colorScheme) private var colorScheme

    public init(viewModel: AgendaViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        let overview = viewModel.overview
        let activeCampus = viewModel.activeCampus

        NavigationStack {
            ZStack {
                CampusTheme.appBackground(for: colorScheme)
                    .ignoresSafeArea()

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 20) {
                        headerSection(activeCampus: activeCampus)

                        weekStripCard(overview: overview)

                        dayHeaderSection(overview: overview)

                        timelineSection(items: overview.timelineItems)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 32)
                }
            }
            #if os(iOS)
            .navigationBarHidden(true)
            #endif
            .sheet(item: $viewModel.activeCheckInDetail) { detail in
                ClassCheckInSheetView(
                    detail: detail,
                    onSave: { status, colorKey in
                        viewModel.saveCheckIn(
                            agendaItemId: detail.agendaItemId,
                            status: status,
                            colorKey: colorKey
                        )
                    },
                    onCancel: {
                        viewModel.dismissCheckInSheet()
                    }
                )
                .presentationDetents([.large, .fraction(0.88)])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
            }
            .sheet(isPresented: $viewModel.isAddCommitmentPresented) {
                AddCommitmentSheetView(
                    selectedDateLabel: overview.fullDateHeader,
                    activeCampus: activeCampus,
                    onSave: { title, description, startTime, endTime, location in
                        withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                            viewModel.addCommitment(
                                title: title,
                                description: description,
                                startTime: startTime,
                                endTime: endTime,
                                location: location,
                                weatherTempC: activeCampus.weatherTempC
                            )
                        }
                    },
                    onCancel: {
                        viewModel.dismissAddCommitmentSheet()
                    }
                )
                .presentationDetents([.large, .fraction(0.82)])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
            }
        }
    }

    // MARK: - Header ("CAMPUS", "Seu dia no campus", "Um dia de cada vez. Tudo por aqui." + Active UFOP Campus Badge)

    private func headerSection(activeCampus: UfopCampusInfo) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .center, spacing: 8) {
                Text("CAMPUS")
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .tracking(1.6)
                    .foregroundStyle(CampusTheme.mathBlue)

                Spacer()

                Menu {
                    ForEach(viewModel.campuses) { campus in
                        Button {
                            withAnimation(.snappy) {
                                viewModel.setActiveCampus(campus.id)
                            }
                        } label: {
                            HStack {
                                Text("\(campus.fullLabel) · \(campus.weatherTempC) °C")
                                if campus.id == activeCampus.id {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "mappin.and.ellipse")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(CampusTheme.mathBlue)

                        Text(activeCampus.agendaHeaderBadgeText)
                            .font(.system(size: 11, weight: .semibold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(
                        Capsule(style: .continuous)
                            .fill(CampusTheme.selectedPillBackground(for: colorScheme))
                    )
                }
                .accessibilityLabel("Campus ativo: \(activeCampus.fullLabel)")

                Text("UFOP · 2026.2")
                    .font(.system(size: 11, weight: .semibold, design: .rounded))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(
                        Capsule(style: .continuous)
                            .fill(CampusTheme.secondarySurface(for: colorScheme))
                    )
            }

            Text("Seu dia no campus")
                .font(.system(size: 30, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

            Text("Um dia de cada vez. Tudo por aqui.")
                .font(.system(size: 15, weight: .regular))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
        }
    }

    // MARK: - Week Strip ("Novembro 2026" / "SEMANA 47" + SEG 16 .. DOM 22)

    private func weekStripCard(overview: AgendaDayOverview) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .center) {
                Text(overview.monthLabel)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Spacer()

                Text(overview.weekLabel)
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .tracking(1.0)
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(
                        Capsule(style: .continuous)
                            .fill(CampusTheme.secondarySurface(for: colorScheme))
                    )
            }

            HStack(spacing: 6) {
                ForEach(overview.weekDays) { chip in
                    dayChipButton(chip: chip)
                }
            }
        }
        .padding(16)
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
            radius: 10,
            x: 0,
            y: 4
        )
    }

    private func dayChipButton(chip: AgendaDayChip) -> some View {
        Button {
            withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                viewModel.selectDay(chip)
            }
        } label: {
            VStack(spacing: 6) {
                Text(chip.weekdayShort)
                    .font(.system(size: 11, weight: .semibold, design: .rounded))
                    .foregroundStyle(
                        chip.isSelected
                            ? CampusTheme.selectedPillText(for: colorScheme)
                            : CampusTheme.secondaryText(for: colorScheme)
                    )

                Text(chip.dayNumber)
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(
                        chip.isSelected
                            ? CampusTheme.selectedPillText(for: colorScheme)
                            : CampusTheme.primaryText(for: colorScheme)
                    )

                Circle()
                    .fill(
                        chip.isSelected
                            ? CampusTheme.selectedPillText(for: colorScheme)
                            : (chip.hasEvents ? CampusTheme.mutedText(for: colorScheme).opacity(0.45) : Color.clear)
                    )
                    .frame(width: 5, height: 5)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: CampusTheme.pillCornerRadius, style: .continuous)
                    .fill(
                        chip.isSelected
                            ? CampusTheme.selectedPillBackground(for: colorScheme)
                            : Color.clear
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: CampusTheme.pillCornerRadius, style: .continuous)
                    .strokeBorder(
                        chip.isSelected
                            ? CampusTheme.selectedPillText(for: colorScheme).opacity(0.28)
                            : Color.clear,
                        lineWidth: 1
                    )
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(chip.weekdayShort) \(chip.dayNumber)")
    }

    // MARK: - Day Summary ("Terça-feira, 17 de novembro", "2 aulas • 1 compromisso" + "+ Compromisso")

    private func dayHeaderSection(overview: AgendaDayOverview) -> some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(overview.fullDateHeader)
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Text(overview.summarySubtitle)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
            }

            Spacer()

            Button {
                viewModel.openAddCommitmentSheet()
            } label: {
                HStack(spacing: 5) {
                    Image(systemName: "plus")
                        .font(.system(size: 12, weight: .bold))

                    Text("Compromisso")
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                }
                .foregroundStyle(CampusTheme.selectedPillText(for: colorScheme))
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
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
            .accessibilityLabel("Adicionar compromisso")
        }
        .padding(.top, 2)
    }

    // MARK: - Timeline Items

    @ViewBuilder
    private func timelineSection(items: [AgendaTimelineItem]) -> some View {
        if items.isEmpty {
            VStack(spacing: 12) {
                Image(systemName: "sun.max.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

                Text("Dia livre no campus")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Text("Nenhuma aula ou compromisso agendado para este dia.")
                    .font(.system(size: 14))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                    .multilineTextAlignment(.center)

                Button {
                    viewModel.openAddCommitmentSheet()
                } label: {
                    Label("Agendar grupo de estudos ou monitoria", systemImage: "plus.circle.fill")
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundStyle(CampusTheme.mathBlue)
                        .padding(.top, 4)
                }
                .buttonStyle(.plain)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 36)
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
            VStack(spacing: 14) {
                ForEach(items) { item in
                    timelineRow(item: item)
                }
            }
        }
    }

    private func timelineRow(item: AgendaTimelineItem) -> some View {
        HStack(alignment: .top, spacing: 12) {
            // Coluna de horário à esquerda (08:00 / 09:40)
            VStack(alignment: .trailing, spacing: 3) {
                Text(item.startTime)
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Text(item.endTime)
                    .font(.system(size: 12, weight: .medium, design: .rounded))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme))
            }
            .frame(width: 46, alignment: .trailing)
            .padding(.top, 12)

            // Card da aula/compromisso com barra lateral colorida e badge amarelo ☀️ XX °C
            Button {
                if item.isClassCheckInEnabled {
                    viewModel.openCheckInSheet(for: item)
                }
            } label: {
                AccentBarCardContainer(
                    accentColor: CampusTheme.departmentColor(for: item.colorKey)
                ) {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(alignment: .top, spacing: 8) {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(item.department)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(CampusTheme.departmentColor(for: item.colorKey))

                                Text(item.title)
                                    .font(.system(size: 17, weight: .bold, design: .rounded))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                                    .multilineTextAlignment(.leading)
                            }

                            Spacer(minLength: 4)

                            WeatherPillBadge(label: item.weatherBadgeLabel)
                        }

                        HStack(spacing: 10) {
                            Label(item.location, systemImage: "mappin.and.ellipse")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

                            Spacer()

                            Text(item.timeRangeLabel)
                                .font(.system(size: 12, weight: .medium, design: .rounded))
                                .foregroundStyle(CampusTheme.mutedText(for: colorScheme))
                        }

                        HStack(spacing: 8) {
                            Text(item.professor)
                                .font(.system(size: 12, weight: .regular))
                                .foregroundStyle(CampusTheme.mutedText(for: colorScheme))

                            Spacer()

                            if item.isCustomCommitment {
                                Button(role: .destructive) {
                                    withAnimation(.spring(response: 0.26, dampingFraction: 0.82)) {
                                        viewModel.deleteCommitment(id: item.id)
                                    }
                                } label: {
                                    HStack(spacing: 4) {
                                        Image(systemName: "trash")
                                            .font(.system(size: 11, weight: .semibold))
                                        Text("Excluir")
                                            .font(.system(size: 11, weight: .semibold))
                                    }
                                    .foregroundStyle(Color(hex: 0xDC2626))
                                    .padding(.horizontal, 9)
                                    .padding(.vertical, 4)
                                    .background(
                                        Capsule(style: .continuous)
                                            .fill(Color(hex: 0xDC2626).opacity(colorScheme == .dark ? 0.18 : 0.10))
                                    )
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("Excluir compromisso \(item.title)")
                            } else {
                                statusBadge(for: item.attendanceStatus)
                            }
                        }
                    }
                }
            }
            .buttonStyle(.plain)
            .contextMenu {
                if item.isCustomCommitment {
                    Button(role: .destructive) {
                        withAnimation(.spring(response: 0.26, dampingFraction: 0.82)) {
                            viewModel.deleteCommitment(id: item.id)
                        }
                    } label: {
                        Label("Excluir compromisso", systemImage: "trash")
                    }
                } else if item.isClassCheckInEnabled {
                    Button {
                        viewModel.openCheckInSheet(for: item)
                    } label: {
                        Label("Registrar presença na aula", systemImage: "checkmark.circle")
                    }
                }
            }
        }
    }

    private func statusBadge(for status: AttendanceStatus) -> some View {
        let color: Color = {
            switch status {
            case .attending:
                return CampusTheme.mathBlue
            case .absent:
                return Color(hex: 0xDC2626)
            case .cancelled:
                return CampusTheme.secondaryText(for: colorScheme)
            }
        }()

        return HStack(spacing: 4) {
            Image(systemName: status.systemImage)
                .font(.system(size: 11, weight: .semibold))
            Text(status.buttonTitle)
                .font(.system(size: 11, weight: .semibold))
        }
        .foregroundStyle(color)
        .padding(.horizontal, 9)
        .padding(.vertical, 4)
        .background(
            Capsule(style: .continuous)
                .fill(color.opacity(colorScheme == .dark ? 0.18 : 0.10))
        )
    }
}

// MARK: - Add Commitment Modal Sheet (Sprint 2)

public struct AddCommitmentSheetView: View {
    public let selectedDateLabel: String
    public let activeCampus: UfopCampusInfo
    public let onSave: (String, String, String, String, String) -> Void
    public let onCancel: () -> Void

    @State private var title: String = "Grupo de estudos · Cálculo II"
    @State private var descriptionText: String = "Monitoria / Estudo em grupo"
    @State private var startTime: String = "16:00"
    @State private var endTime: String = "17:30"
    @State private var location: String = "Biblioteca Central · Sala 03"
    @Environment(\.colorScheme) private var colorScheme

    private let quickPresets: [(label: String, title: String, location: String, desc: String)] = [
        ("Grupo de estudos", "Grupo de estudos · Cálculo II", "Biblioteca Central · Sala 03", "Estudo colaborativo"),
        ("Monitoria", "Monitoria · Algoritmos", "ICEB · Lab 302", "Atendimento com monitor"),
        ("Laboratório", "Prática extra · Física I", "Bauxita · Pavilhão 105", "Experimento de laboratório"),
        ("IC / Reunião", "Reunião de Iniciação Científica", "DECOM · Sala de Seminários", "Orientação acadêmica")
    ]

    public init(
        selectedDateLabel: String,
        activeCampus: UfopCampusInfo,
        onSave: @escaping (String, String, String, String, String) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.selectedDateLabel = selectedDateLabel
        self.activeCampus = activeCampus
        self.onSave = onSave
        self.onCancel = onCancel
    }

    public var body: some View {
        ZStack {
            CampusTheme.cardBackground(for: colorScheme)
                .ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 20) {
                    HStack {
                        Text("NOVO COMPROMISSO")
                            .font(.system(size: 12, weight: .bold, design: .rounded))
                            .tracking(1.4)
                            .foregroundStyle(CampusTheme.algebraGreen)

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

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Agendar no campus")
                            .font(.system(size: 26, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                        HStack(spacing: 8) {
                            Text(selectedDateLabel)
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

                            Text("•")
                                .foregroundStyle(CampusTheme.mutedText(for: colorScheme))

                            WeatherPillBadge(label: activeCampus.weatherBadgeLabel)
                        }
                    }

                    // Modelos rápidos
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Sugestões rápidas")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(quickPresets, id: \.label) { preset in
                                    Button {
                                        title = preset.title
                                        location = preset.location
                                        descriptionText = preset.desc
                                    } label: {
                                        Text(preset.label)
                                            .font(.system(size: 12, weight: .semibold, design: .rounded))
                                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 8)
                                            .background(
                                                Capsule(style: .continuous)
                                                    .fill(CampusTheme.secondarySurface(for: colorScheme))
                                            )
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 14) {
                        fieldGroup(label: "Título do compromisso", text: $title, placeholder: "Ex.: Grupo de estudos · Cálculo II")

                        HStack(spacing: 12) {
                            fieldGroup(label: "Início", text: $startTime, placeholder: "14:00")
                            fieldGroup(label: "Término", text: $endTime, placeholder: "15:00")
                        }

                        fieldGroup(label: "Local no campus (\(activeCampus.city))", text: $location, placeholder: "Ex.: Biblioteca Central · Sala 03")

                        fieldGroup(label: "Categoria / Observação", text: $descriptionText, placeholder: "Ex.: Monitoria acadêmica")
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
                            onSave(title, descriptionText, startTime, endTime, location)
                        } label: {
                            Text("Salvar compromisso")
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
                    .padding(.top, 6)
                }
                .padding(.horizontal, 22)
                .padding(.top, 18)
                .padding(.bottom, 30)
            }
        }
    }

    private func fieldGroup(label: String, text: Binding<String>, placeholder: String) -> some View {
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

#Preview("AgendaView - Light") {
    let store = CampusStore()
    AgendaView(viewModel: AgendaViewModel(store: store))
        .preferredColorScheme(.light)
}

#Preview("AgendaView - Dark") {
    let store = CampusStore()
    AgendaView(viewModel: AgendaViewModel(store: store))
        .preferredColorScheme(.dark)
}
