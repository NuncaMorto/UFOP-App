import SwiftUI

public struct AgendaView: View {
    @Bindable public var viewModel: AgendaViewModel
    public let onOpenLocationInMap: ((CampusMapMeetingRequest) -> Void)?

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var commitmentToDelete: AgendaTimelineItem?

    public init(
        viewModel: AgendaViewModel,
        onOpenLocationInMap: ((CampusMapMeetingRequest) -> Void)? = nil
    ) {
        self.viewModel = viewModel
        self.onOpenLocationInMap = onOpenLocationInMap
    }

    private var isRegular: Bool {
        horizontalSizeClass == .regular
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

    public var body: some View {
        let overview = viewModel.overview
        let activeCampus = viewModel.activeCampus
        let spotlight = viewModel.store.currentOrNextClassInfo()
        let termBanner = viewModel.store.academicTermBanner(for: overview.selectedDateIso)

        NavigationStack {
            ZStack {
                LiquidGlassBackdrop(accent: appearance?.accentColor ?? .red, highContrast: hc)

                ScrollView(.vertical, showsIndicators: false) {
                    if isRegular {
                        HStack(alignment: .top, spacing: 22) {
                            VStack(alignment: .leading, spacing: 18) {
                                headerSection(activeCampus: activeCampus, overview: overview)

                                weekStripCard(overview: overview)

                                academicTermCard(banner: termBanner)

                                if let spotlight, spotlight.dateIso != overview.selectedDateIso {
                                    nextClassRedirectBanner(spotlight: spotlight)
                                }
                            }
                            .frame(width: 380)

                            VStack(alignment: .leading, spacing: 18) {
                                dayHeaderSection(overview: overview)

                                timelineSection(items: overview.timelineItems, spotlight: spotlight, selectedDateIso: overview.selectedDateIso)
                            }
                            .frame(maxWidth: .infinity, alignment: .topLeading)
                        }
                        .padding(.horizontal, 24)
                        .padding(.top, 16)
                        .padding(.bottom, 32)
                        .campusReadableWidth(1160)
                    } else {
                        LazyVStack(alignment: .leading, spacing: 18) {
                            headerSection(activeCampus: activeCampus, overview: overview)

                            weekStripCard(overview: overview)

                            academicTermCard(banner: termBanner)

                            // Só exibe o anúncio separado se a próxima aula NÃO estiver no dia já visível abaixo (IHC Sprint 14)
                            if let spotlight, spotlight.dateIso != overview.selectedDateIso {
                                nextClassRedirectBanner(spotlight: spotlight)
                            }

                            dayHeaderSection(overview: overview)

                            timelineSection(items: overview.timelineItems, spotlight: spotlight, selectedDateIso: overview.selectedDateIso)
                        }
                        .padding(.horizontal, 18)
                        .padding(.top, 12)
                        .padding(.bottom, 108)
                    }
                }
            }
            #if os(iOS)
            .navigationBarHidden(true)
            #endif
            .sheet(item: $viewModel.activeCheckInDetail) { detail in
                let isEad = viewModel.store.isCourseEad(detail.disciplineId) || CampusStore.isEadText(detail.location)
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
                    },
                    onOpenLocationInMap: (!isEad && onOpenLocationInMap != nil) ? {
                        let req = meetingRequest(for: detail)
                        viewModel.dismissCheckInSheet()
                        onOpenLocationInMap?(req)
                    } : nil
                )
                .presentationDetents([.large, .fraction(0.88)])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(30)
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
                .presentationDetents([.large, .fraction(0.86)])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(30)
            }
            .confirmationDialog(
                "Excluir compromisso?",
                isPresented: Binding(
                    get: { commitmentToDelete != nil },
                    set: { if !$0 { commitmentToDelete = nil } }
                ),
                titleVisibility: .visible,
                presenting: commitmentToDelete
            ) { item in
                Button("Excluir compromisso", role: .destructive) {
                    withAnimation(.spring(response: 0.26, dampingFraction: 0.82)) {
                        viewModel.deleteCommitment(id: item.id)
                    }
                    commitmentToDelete = nil
                }
                Button("Cancelar", role: .cancel) {
                    commitmentToDelete = nil
                }
            } message: { item in
                Text("Deseja remover '\(item.title)' da sua agenda?")
            }
        }
    }

    // MARK: Header

    private func headerSection(activeCampus: UfopCampusInfo, overview: AgendaDayOverview) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .center, spacing: 8) {
                Text("AGENDA · \(overview.semesterTag)")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .tracking(1.5)
                    .foregroundStyle(accent)

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
                    let accentObj = appearance?.accentColor ?? .red
                    HStack(spacing: 5) {
                        Image(systemName: "mappin.and.ellipse")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(CampusTheme.weatherBadgeText(for: colorScheme, accent: accentObj))

                        Text("\(activeCampus.city) · \(activeCampus.weatherTempC)°C")
                            .font(CampusTheme.tabularFont(size: 11, weight: .semibold))
                            .foregroundStyle(CampusTheme.weatherBadgeText(for: colorScheme, accent: accentObj))
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(
                        Capsule(style: .continuous)
                            .fill(CampusTheme.weatherBadgeBackground(for: colorScheme, accent: accentObj))
                    )
                    .overlay {
                        if hc {
                            Capsule(style: .continuous)
                                .strokeBorder(CampusTheme.cardBorder(for: colorScheme, highContrast: true), lineWidth: 1.5)
                        }
                    }
                }
                .accessibilityLabel("Campus ativo: \(activeCampus.fullLabel)")
            }

            Text("Seu dia no campus")
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                .accessibilityAddTraits(.isHeader)

            if !viewModel.store.studentProfile.fullName.isEmpty {
                Text("\(viewModel.store.studentProfile.fullName) · \(viewModel.store.studentProfile.courseName)")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    .lineLimit(1)
            } else {
                Text("Um dia de cada vez. Tudo por aqui.")
                    .font(.system(size: 14, weight: .regular))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
            }
        }
    }

    // MARK: 7-Day Full-Width Week Strip (SEG..DOM without clipping)

    private func weekStripCard(overview: AgendaDayOverview) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 8) {
                Text(overview.monthLabel)
                    .font(CampusTheme.font(size: 15, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                LiquidGlassGroup(spacing: 4) {
                    HStack(spacing: 4) {
                        Button {
                            withAnimation(.snappy) { viewModel.store.shiftAgendaWeek(by: -1) }
                        } label: {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                .frame(width: 28, height: 28)
                                .liquidGlassCapsule(selected: false, accent: accent, highContrast: hc)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Semana anterior")

                        Button {
                            withAnimation(.snappy) { viewModel.store.shiftAgendaWeek(by: 1) }
                        } label: {
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                .frame(width: 28, height: 28)
                                .liquidGlassCapsule(selected: false, accent: accent, highContrast: hc)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Próxima semana")
                    }
                }

                Spacer()

                Button {
                    withAnimation(.snappy) {
                        viewModel.store.jumpAgendaToToday()
                    }
                } label: {
                    Text("Hoje")
                        .font(CampusTheme.font(size: 11, weight: .bold))
                        .foregroundStyle(CampusTheme.accessibleTextColor(accent, scheme: colorScheme, accent: appearance?.accentColor ?? .red))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .liquidGlassCapsule(selected: false, accent: accent, highContrast: hc)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Ir para o dia de hoje")
            }

            HStack(spacing: 4) {
                ForEach(overview.weekDays) { chip in
                    dayChipButton(chip: chip)
                }
            }
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: 22, tint: accent, highContrast: hc)
    }

    private func dayChipButton(chip: AgendaDayChip) -> some View {
        Button {
            withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                viewModel.selectDay(chip)
            }
        } label: {
            VStack(spacing: 4) {
                Text(chip.weekdayShort)
                    .font(CampusTheme.font(size: 10, weight: .bold))
                    .foregroundStyle(chip.isSelected ? accent : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)

                Text(chip.dayNumber)
                    .font(CampusTheme.tabularFont(size: 15, weight: .bold))
                    .foregroundStyle(chip.isSelected ? accent : CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                Circle()
                    .fill(
                        chip.isSelected
                            ? accent
                            : (chip.hasEvents ? CampusTheme.mutedText(for: colorScheme).opacity(0.55) : .clear)
                    )
                    .frame(width: 4, height: 4)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(chip.isSelected ? (appearance?.accentColor ?? .red).surface2(for: colorScheme) : Color.clear)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(hc ? CampusTheme.cardBorder(for: colorScheme, highContrast: true) : .clear, lineWidth: chip.isSelected ? 1.5 : 1)
            )
        }
        .buttonStyle(.plain)
        .campusHoverLift()
        .minimumTouchTarget(minTouch)
        .accessibilityLabel("\(chip.weekdayShort) \(chip.dayNumber)\(chip.isSelected ? ", selecionado" : "")")
    }

    // MARK: Term Banner & Off-Day Next Class Pill

    private func academicTermCard(banner: AcademicTermBannerInfo) -> some View {
        let bannerColor = banner.isRecessOrBreak ? Color(hex: 0xF59E0B) : accent
        return HStack(spacing: 10) {
            Image(systemName: banner.isRecessOrBreak ? "calendar.badge.exclamationmark" : "graduationcap.fill")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(bannerColor)

            VStack(alignment: .leading, spacing: 2) {
                Text(banner.title)
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                Text(banner.detail)
                    .font(.system(size: 11))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
            }

            Spacer()

            Text(banner.badge)
                .font(.system(size: 9, weight: .bold, design: .rounded))
                .foregroundStyle(bannerColor)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Capsule().fill(bannerColor.opacity(0.14)))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .liquidGlassCard(cornerRadius: 16, tint: bannerColor, highContrast: hc)
    }

    private func nextClassRedirectBanner(spotlight: AgendaClassSpotlight) -> some View {
        Button {
            withAnimation(.snappy) {
                viewModel.store.selectAgendaDate(spotlight.dateIso)
            }
        } label: {
            HStack(spacing: 10) {
                Image(systemName: spotlight.isOngoing ? "bolt.fill" : "clock.fill")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(accent)

                VStack(alignment: .leading, spacing: 1) {
                    Text("\(spotlight.isOngoing ? "Agora" : "Próxima aula · \(spotlight.dayLabel)"): \(spotlight.item.title)")
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                        .lineLimit(1)
                    Text("\(spotlight.item.startTime)–\(spotlight.item.endTime) · \(spotlight.item.location)")
                        .font(.system(size: 11))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                        .lineLimit(1)
                }

                Spacer()

                Image(systemName: "arrow.right.circle.fill")
                    .font(.system(size: 16))
                    .foregroundStyle(accent)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .liquidGlassCard(cornerRadius: 16, tint: accent, interactive: true, highContrast: hc)
        }
        .buttonStyle(.plain)
        .minimumTouchTarget(minTouch)
    }

    // MARK: Day Summary Header

    private func dayHeaderSection(overview: AgendaDayOverview) -> some View {
        let safeDateHeader = Self.formatDateHeaderNoOrphan(overview.fullDateHeader)
        return HStack(alignment: .center, spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                Text(safeDateHeader)
                    .font(CampusTheme.font(size: 18, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                    .lineLimit(2)
                    .minimumScaleFactor(0.92)

                Text(overview.summarySubtitle)
                    .font(CampusTheme.tabularFont(size: 13, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
            }

            Spacer()

            Button {
                viewModel.openAddCommitmentSheet()
            } label: {
                HStack(spacing: 5) {
                    Image(systemName: "plus")
                        .font(.system(size: 11, weight: .bold))
                    Text("Compromisso")
                        .font(CampusTheme.font(size: 12, weight: .bold))
                }
                .foregroundStyle((appearance?.accentColor ?? .red).textOnHighlightSurface(for: colorScheme))
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .liquidGlassCapsule(selected: false, accent: accent, highContrast: hc)
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
            .accessibilityLabel("Adicionar compromisso")
        }
    }

    // MARK: Timeline Items

    @ViewBuilder
    private func timelineSection(items: [AgendaTimelineItem], spotlight: AgendaClassSpotlight?, selectedDateIso: String) -> some View {
        if items.isEmpty {
            VStack(spacing: 12) {
                Image(systemName: "sun.max.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

                Text("Dia livre no campus")
                    .font(CampusTheme.font(size: 16, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                Text("Nenhuma aula ou compromisso agendado para este dia.")
                    .font(CampusTheme.font(size: 13, weight: .regular))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    .multilineTextAlignment(.center)

                Button {
                    viewModel.openAddCommitmentSheet()
                } label: {
                    Label("Agendar grupo de estudos ou monitoria", systemImage: "plus.circle.fill")
                        .font(CampusTheme.font(size: 13, weight: .semibold))
                        .foregroundStyle(accent)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 32)
            .padding(.horizontal, 18)
            .liquidGlassCard(highContrast: hc)
        } else {
            LazyVStack(spacing: 12) {
                ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                    let previousItem = index > 0 ? items[index - 1] : nil
                    if let freeGap = Self.freeWindowMinutesBetween(previous: previousItem, current: item) {
                        freeWindowDivider(gapMinutes: freeGap)
                    }
                    let highlightLabel = Self.resolveTodayHighlightLabel(
                        item: item,
                        spotlight: spotlight,
                        selectedDateIso: selectedDateIso
                    )
                    timelineRow(item: item, inlineSpotlightBadge: highlightLabel)
                }
            }
        }
    }

    private func freeWindowDivider(gapMinutes: Int) -> some View {
        HStack(spacing: 10) {
            Rectangle()
                .fill(CampusTheme.mutedText(for: colorScheme, highContrast: hc).opacity(0.28))
                .frame(height: 1)

            HStack(spacing: 4) {
                Image(systemName: "clock")
                    .font(.system(size: 10, weight: .semibold))
                Text("Janela livre · \(Self.formatFreeWindowDuration(gapMinutes))")
                    .font(CampusTheme.tabularFont(size: 11, weight: .medium))
            }
            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
            .fixedSize(horizontal: true, vertical: false)

            Rectangle()
                .fill(CampusTheme.mutedText(for: colorScheme, highContrast: hc).opacity(0.28))
                .frame(height: 1)
        }
        .padding(.vertical, 2)
    }

    private func timelineRow(item: AgendaTimelineItem, inlineSpotlightBadge: String?) -> some View {
        let deptColor = CampusTheme.accessibleTextColor(
            CampusTheme.departmentColor(for: item.colorKey),
            scheme: colorScheme
        )
        let isHighlightedClass = inlineSpotlightBadge != nil
        let isEad = (item.disciplineId.map { viewModel.store.isCourseEad($0) } ?? false)
            || item.location.lowercased().contains("ead")
            || item.location.lowercased().contains("distância")

        let indicatorStyle = appearance?.attendanceIndicatorStyle ?? viewModel.store.attendanceIndicatorStyle
        let useBorderIndicator = !item.isCustomCommitment && indicatorStyle == "CARD_BORDER"
        let cardBorderColor: Color? = useBorderIndicator ? attendanceSemanticColor(for: item.attendanceStatus) : nil
        let a11yDescription: String = {
            var parts = [
                item.isCustomCommitment ? "Compromisso: \(item.title)" : "Aula: \(item.title)",
                "Horário: \(item.timeRangeLabel)"
            ]
            if !item.isCustomCommitment {
                parts.append("Status: \(item.attendanceStatus.buttonTitle)")
            }
            if !item.location.isEmpty {
                parts.append("Local: \(isEad ? "A distância (EAD)" : item.location)")
            }
            return parts.joined(separator: ". ")
        }()

        return HStack(alignment: .top, spacing: 10) {
            VStack(alignment: .trailing, spacing: 3) {
                Text(item.startTime)
                    .font(CampusTheme.tabularFont(size: 14, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                Text(item.endTime)
                    .font(CampusTheme.tabularFont(size: 12, weight: .medium))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
            }
            .frame(width: 44, alignment: .trailing)
            .padding(.top, 14)

            Button {
                if item.isClassCheckInEnabled {
                    viewModel.openCheckInSheet(for: item)
                }
            } label: {
                AccentBarCardContainer(
                    accentColor: CampusTheme.departmentColor(for: item.colorKey),
                    highContrast: hc,
                    useSurface2: isHighlightedClass,
                    borderOverrideColor: cardBorderColor
                ) {
                    VStack(alignment: .leading, spacing: 8) {
                        VStack(alignment: .leading, spacing: 3) {
                            HStack(spacing: 6) {
                                Text(item.department)
                                    .font(CampusTheme.font(size: 12, weight: .semibold))
                                    .foregroundStyle(deptColor)

                                if let inlineSpotlightBadge {
                                    let isNow = inlineSpotlightBadge == "AGORA"
                                    Text(inlineSpotlightBadge)
                                        .font(CampusTheme.tabularFont(size: 9, weight: .bold))
                                        .foregroundStyle(isNow ? (appearance?.accentColor ?? .red).onPrimary(for: colorScheme) : (appearance?.accentColor ?? .red).textOnHighlightSurface(for: colorScheme))
                                        .padding(.horizontal, 7)
                                        .padding(.vertical, 2)
                                        .background(
                                            Capsule().fill(
                                                isNow ? accent : (appearance?.accentColor ?? .red).highlight(for: colorScheme).opacity(colorScheme == .dark ? 0.22 : 0.35)
                                            )
                                        )
                                }

                                if isEad {
                                    Text("EAD")
                                        .font(CampusTheme.font(size: 9, weight: .bold))
                                        .foregroundStyle(CampusTheme.mathBlue)
                                        .padding(.horizontal, 7)
                                        .padding(.vertical, 2)
                                        .background(Capsule().fill(CampusTheme.selectedPillBackground(for: colorScheme)))
                                }
                            }

                            Text(item.title)
                                .font(CampusTheme.font(size: 16, weight: .bold))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                .lineLimit(2)
                                .multilineTextAlignment(.leading)
                        }

                        HStack(spacing: 8) {
                            Label(
                                isEad ? "A distância (EAD)" : (item.location.isEmpty ? "Local não definido" : item.location),
                                systemImage: isEad ? "laptopcomputer" : "mappin.and.ellipse"
                            )
                            .font(CampusTheme.font(size: 13, weight: .medium))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))

                            Spacer()

                            Text(item.timeRangeLabel)
                                .font(CampusTheme.tabularFont(size: 12, weight: .medium))
                                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
                        }

                        HStack(spacing: 8) {
                            if !item.professor.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                Text(item.professor)
                                    .font(CampusTheme.font(size: 12, weight: .regular))
                                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: hc))
                            }

                            Spacer()

                            if item.isCustomCommitment {
                                Button(role: .destructive) {
                                    commitmentToDelete = item
                                } label: {
                                    HStack(spacing: 4) {
                                        Image(systemName: "trash")
                                            .font(.system(size: 11, weight: .semibold))
                                        Text("Excluir")
                                            .font(CampusTheme.font(size: 11, weight: .semibold))
                                    }
                                    .foregroundStyle(Color(hex: 0xEF4444))
                                    .padding(.horizontal, 9)
                                    .padding(.vertical, 5)
                                    .background(Capsule().fill(Color(hex: 0xEF4444).opacity(0.12)))
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("Excluir compromisso \(item.title)")
                            } else if !useBorderIndicator {
                                statusBadge(for: item.attendanceStatus)
                            }
                        }
                    }
                }
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .combine)
            .accessibilityLabel(a11yDescription)
            .campusHoverLift()
        }
    }

    private static func formatDateHeaderNoOrphan(_ raw: String) -> String {
        guard let range = raw.range(of: " de ", options: .backwards) else {
            return raw
        }
        return raw.replacingCharacters(in: range, with: " de\u{00A0}")
    }

    private static func parseMinutes(_ hhmm: String) -> Int? {
        let parts = hhmm.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: ":")
        guard parts.count >= 2, let h = Int(parts[0]), let m = Int(parts[1]) else { return nil }
        return h * 60 + m
    }

    private static func freeWindowMinutesBetween(previous: AgendaTimelineItem?, current: AgendaTimelineItem) -> Int? {
        guard let previous,
              previous.kind == .lecture,
              current.kind == .lecture,
              previous.attendanceStatus != .cancelled,
              current.attendanceStatus != .cancelled,
              let prevEnd = parseMinutes(previous.endTime),
              let currStart = parseMinutes(current.startTime) else {
            return nil
        }
        let gap = currStart - prevEnd
        return gap >= 50 ? gap : nil
    }

    private static func formatFreeWindowDuration(_ gapMinutes: Int) -> String {
        let hours = gapMinutes / 60
        let mins = gapMinutes % 60
        if hours == 0 { return "\(mins)min" }
        if mins == 0 { return "\(hours)h" }
        return String(format: "%dh%02d", hours, mins)
    }

    private static func resolveTodayHighlightLabel(
        item: AgendaTimelineItem,
        spotlight: AgendaClassSpotlight?,
        selectedDateIso: String
    ) -> String? {
        guard let spotlight,
              spotlight.dateIso == selectedDateIso,
              item.kind == .lecture,
              item.attendanceStatus != .cancelled,
              spotlight.item.disciplineId == item.disciplineId,
              spotlight.item.startTime == item.startTime else {
            return nil
        }
        if spotlight.isOngoing {
            return "AGORA"
        }
        let now = Calendar.current.dateComponents([.hour, .minute], from: Date())
        let nowMin = (now.hour ?? 0) * 60 + (now.minute ?? 0)
        guard let startMin = parseMinutes(item.startTime) else {
            return "PRÓXIMA"
        }
        let diff = startMin - nowMin
        if diff <= 0 { return "AGORA" }
        if diff < 60 { return "EM \(diff) MIN" }
        if diff < 360 {
            let h = diff / 60
            let m = diff % 60
            return m == 0 ? "EM \(h)H" : String(format: "EM %dH%02d", h, m)
        }
        return "PRÓXIMA"
    }

    private func attendanceSemanticColor(for status: AttendanceStatus) -> Color {
        switch status {
        case .attending:
            return colorScheme == .dark ? Color(hex: 0x81C784) : Color(hex: 0x2E7D32)
        case .absent:
            return colorScheme == .dark ? Color(hex: 0xEF4444) : Color(hex: 0xC62828)
        case .cancelled:
            return colorScheme == .dark ? Color(hex: 0xF59E0B) : Color(hex: 0xD97706)
        }
    }

    private func statusBadge(for status: AttendanceStatus) -> some View {
        let color = attendanceSemanticColor(for: status)

        return HStack(spacing: 4) {
            Image(systemName: status.systemImage)
                .font(.system(size: 11, weight: .semibold))
            Text(status.buttonTitle)
                .font(.system(size: 11, weight: .semibold))
        }
        .foregroundStyle(color)
        .padding(.horizontal, 9)
        .padding(.vertical, 4)
        .background(Capsule().fill(color.opacity(colorScheme == .dark ? 0.20 : 0.12)))
    }

    private func meetingRequest(for detail: ClassCheckInDetail) -> CampusMapMeetingRequest {
        let disc = viewModel.store.disciplines.first(where: { $0.id == detail.disciplineId })
        let courseCode = disc?.code ?? ""
        let section = viewModel.store.turmaByCourseId[detail.disciplineId] ?? ""
        let parsedDate = CampusStore.parseIsoDate(viewModel.overview.selectedDateIso) ?? Date()
        let weekday = UInt32(CampusStore.isoWeekdayIndex(from: parsedDate))
        let startTime = CampusStore.normalizeTimeString(
            detail.timeRange.components(separatedBy: "–").first ?? detail.timeRange
        )
        let matchingSlot = viewModel.store.courseScheduleSlots.first {
            $0.courseId == detail.disciplineId && $0.dayOfWeek == Int(weekday) && $0.startTime == startTime
        }
        let trimmedLocation = detail.location.trimmingCharacters(in: .whitespacesAndNewlines)
        let fallback = trimmedLocation.isEmpty ? (matchingSlot?.building ?? "") : trimmedLocation
        return CampusMapMeetingRequest(
            courseCode: courseCode,
            section: section,
            weekday: weekday,
            startTime: startTime,
            fallbackBuildingRaw: fallback
        )
    }
}

// MARK: - Add Commitment Sheet (Native Date/Time Pickers + Liquid Glass)

public struct AddCommitmentSheetView: View {
    private static let timeFormatter: DateFormatter = {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.dateFormat = "HH:mm"
        return fmt
    }()

    public let selectedDateLabel: String
    public let activeCampus: UfopCampusInfo
    public let onSave: (String, String, String, String, String) -> Void
    public let onCancel: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @Environment(\.colorScheme) private var colorScheme

    @State private var title: String = "Grupo de estudos · Cálculo II"
    @State private var descriptionText: String = "Monitoria / Estudo em grupo"
    @State private var startDate: Date = Calendar.current.date(bySettingHour: 16, minute: 0, second: 0, of: Date()) ?? Date()
    @State private var endDate: Date = Calendar.current.date(bySettingHour: 17, minute: 30, second: 0, of: Date()) ?? Date()
    @State private var location: String = "Biblioteca Central · Sala 03"

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
        let accentObj = appearance?.accentColor ?? .red
        let accent = accentObj.primary(for: colorScheme)
        let hc = appearance?.accessibility.highContrastMode ?? false

        ZStack {
            LiquidGlassBackdrop(accent: accentObj, highContrast: hc)

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    HStack {
                        Text("NOVO COMPROMISSO")
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

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Agendar no campus")
                            .font(.system(size: 24, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                        Text("\(selectedDateLabel) · \(activeCampus.city)")
                            .font(.system(size: 13))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    }

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
                                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 8)
                                        .liquidGlassCapsule(selected: false, accent: accent, highContrast: hc)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        fieldGroup(label: "Título do compromisso", text: $title, placeholder: "Ex.: Grupo de estudos", highContrast: hc)

                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Início")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                DatePicker("", selection: $startDate, displayedComponents: .hourAndMinute)
                                    .labelsHidden()
                                    .tint(accent)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(10)
                            .liquidGlassCard(cornerRadius: 12, highContrast: hc)

                            VStack(alignment: .leading, spacing: 4) {
                                Text("Término")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                DatePicker("", selection: $endDate, displayedComponents: .hourAndMinute)
                                    .labelsHidden()
                                    .tint(accent)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(10)
                            .liquidGlassCard(cornerRadius: 12, highContrast: hc)
                        }

                        fieldGroup(label: "Local no campus", text: $location, placeholder: "Ex.: Biblioteca Central", highContrast: hc)
                        fieldGroup(label: "Observação", text: $descriptionText, placeholder: "Ex.: Monitoria", highContrast: hc)
                    }

                    LiquidGlassPrimaryButton(
                        tint: accent,
                        foreground: accentObj.onPrimary(for: colorScheme),
                        minHeight: appearance?.accessibility.minTouchHeight ?? 48
                    ) {
                        onSave(
                            title,
                            descriptionText,
                            Self.timeFormatter.string(from: startDate),
                            Self.timeFormatter.string(from: endDate),
                            location
                        )
                    } label: {
                        Text("Salvar compromisso")
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                    }
                }
                .padding(20)
            }
        }
    }

    private func fieldGroup(label: String, text: Binding<String>, placeholder: String, highContrast: Bool) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            TextField(placeholder, text: text)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .liquidGlassCard(cornerRadius: 12, highContrast: highContrast)
        }
    }
}
