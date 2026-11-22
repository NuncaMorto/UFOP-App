import SwiftUI

public struct ProfileView: View {
    @Environment(CampusStore.self) private var store
    @Environment(\.colorScheme) private var colorScheme
    @AppStorage("campus.ufop.appearanceMode") private var appearanceModeRaw: String = "system"
    @State private var isScheduleShareSheetPresented: Bool = false

    public init() {}

    public var body: some View {
        let profile = store.academicProfileSummary

        NavigationStack {
            ZStack {
                CampusTheme.appBackground(for: colorScheme)
                    .ignoresSafeArea()

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 20) {
                        headerSection

                        studentIdentityCard(activeCampus: profile.activeCampus)

                        campusPickerCard(profile: profile)

                        semesterMetricsCard(profile: profile)

                        academicCalendarSection(events: profile.calendarEvents)

                        scheduleShareImportCard

                        appearancePreferenceCard

                        resetDemoDataButton
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 32)
                }
            }
            #if os(iOS)
            .navigationBarHidden(true)
            #endif
            .sheet(isPresented: $isScheduleShareSheetPresented) {
                ScheduleShareImportSheetView(
                    store: store,
                    onClose: {
                        isScheduleShareSheetPresented = false
                    }
                )
                .presentationDetents([.large, .fraction(0.88)])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
            }
        }
    }

    // MARK: - Header

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("CAMPUS · UFOP")
                .font(.system(size: 12, weight: .bold, design: .rounded))
                .tracking(1.5)
                .foregroundStyle(CampusTheme.mathBlue)

            Text("Perfil acadêmico")
                .font(.system(size: 30, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

            Text("Universidade Federal de Ouro Preto · 2026.2")
                .font(.system(size: 15, weight: .regular))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
        }
    }

    // MARK: - Student Card

    private func studentIdentityCard(activeCampus: UfopCampusInfo) -> some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(CampusTheme.selectedPillBackground(for: colorScheme))
                    .frame(width: 58, height: 58)

                Text("UF")
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.selectedPillText(for: colorScheme))
            }

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text("Estudante UFOP")
                        .font(.system(size: 18, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                    Spacer()

                    WeatherPillBadge(label: activeCampus.weatherBadgeLabel)
                }

                Text(activeCampus.fullLabel)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))

                Text("Matrícula ativa · Semestre 2026.2 · \(activeCampus.weatherCondition)")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(CampusTheme.mathBlue)
            }
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                .fill(CampusTheme.cardBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
        )
    }

    // MARK: - Campus da UFOP Picker (Ouro Preto · Morro do Cruzeiro, Mariana · ICHS/ICSA, João Monlevade · ICEA)

    private func campusPickerCard(profile: AcademicProfileSummary) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Campus da UFOP")
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                    Text("Altera o clima e a referência de localização na Agenda")
                        .font(.system(size: 12, weight: .regular))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                }

                Spacer()
            }

            VStack(spacing: 8) {
                ForEach(profile.campuses) { campus in
                    let isSelected = campus.id == profile.activeCampus.id
                    Button {
                        withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                            _ = store.setActiveCampus(campusId: campus.id)
                        }
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundStyle(
                                    isSelected
                                        ? CampusTheme.mathBlue
                                        : CampusTheme.mutedText(for: colorScheme)
                                )

                            VStack(alignment: .leading, spacing: 2) {
                                Text(campus.fullLabel)
                                    .font(.system(size: 14, weight: .bold, design: .rounded))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                                Text("\(campus.weatherCondition) · \(campus.weatherTempC) °C")
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                            }

                            Spacer()

                            WeatherPillBadge(label: campus.weatherBadgeLabel)
                        }
                        .padding(12)
                        .background(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(
                                    isSelected
                                        ? CampusTheme.selectedPillBackground(for: colorScheme)
                                        : CampusTheme.appBackground(for: colorScheme)
                                )
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .strokeBorder(
                                    isSelected
                                        ? CampusTheme.selectedPillText(for: colorScheme).opacity(0.32)
                                        : CampusTheme.cardBorder(for: colorScheme),
                                    lineWidth: 1
                                )
                        )
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Selecionar \(campus.fullLabel)")
                }
            }
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                .fill(CampusTheme.cardBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
        )
    }

    // MARK: - Semester Metrics Card

    private func semesterMetricsCard(profile: AcademicProfileSummary) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Resumo em tempo real · 2026.2")
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                Spacer()

                Text("\(profile.pendingAssessments) pendentes")
                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                    .foregroundStyle(CampusTheme.mathBlue)
            }

            HStack(spacing: 10) {
                metricBox(
                    value: "\(profile.totalCourses)",
                    label: "Disciplinas",
                    tint: CampusTheme.mathBlue
                )

                metricBox(
                    value: "\(profile.totalAbsences)/\(profile.totalMaxAbsences)",
                    label: "Faltas totais",
                    tint: CampusTheme.compPurple
                )

                metricBox(
                    value: "\(profile.totalCancelledClasses)",
                    label: "Canceladas",
                    tint: CampusTheme.algebraGreen
                )
            }

            HStack(spacing: 6) {
                Image(systemName: profile.coursesAtRisk > 0 ? "exclamationmark.triangle.fill" : "checkmark.shield.fill")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(profile.coursesAtRisk > 0 ? Color(hex: 0xDC2626) : CampusTheme.algebraGreen)

                Text(
                    profile.coursesAtRisk > 0
                        ? "Atenção: \(profile.coursesAtRisk) disciplina(s) acima de 75% do limite de faltas."
                        : "\(profile.totalCancelledClasses) canceladas · não somam faltas no limite de 25% da UFOP."
                )
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
            }
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                .fill(CampusTheme.cardBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
        )
    }

    private func metricBox(value: String, label: String, tint: Color) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.system(size: 22, weight: .bold, design: .rounded))
                .foregroundStyle(tint)

            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(CampusTheme.appBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
        )
    }

    // MARK: - Calendário Acadêmico UFOP 2026.2 (18 NOV, 02 DEZ, 12 DEZ, 15 DEZ)

    private func academicCalendarSection(events: [AcademicCalendarEvent]) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Calendário Acadêmico UFOP 2026.2")
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                    Text("Datas oficiais PROGRAD · 2º semestre letivo")
                        .font(.system(size: 12, weight: .regular))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                }

                Spacer()
            }

            VStack(spacing: 10) {
                ForEach(events) { event in
                    HStack(spacing: 12) {
                        VStack(spacing: 1) {
                            Text(event.dayNumber)
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.mathBlue)

                            Text(event.monthShort)
                                .font(.system(size: 10, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.mathBlue)
                        }
                        .frame(width: 46, height: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 11, style: .continuous)
                                .fill(CampusTheme.selectedPillBackground(for: colorScheme))
                        )

                        VStack(alignment: .leading, spacing: 2) {
                            Text(event.title)
                                .font(.system(size: 14, weight: .bold, design: .rounded))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                            Text(event.category)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                        }

                        Spacer()
                    }
                    .padding(11)
                    .background(
                        RoundedRectangle(cornerRadius: 13, style: .continuous)
                            .fill(CampusTheme.appBackground(for: colorScheme))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 13, style: .continuous)
                            .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
                    )
                }
            }
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                .fill(CampusTheme.cardBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
        )
    }

    // MARK: - Compartilhar / Importar Grade (JSON)

    private var scheduleShareImportCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "square.and.arrow.up.on.square.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(CampusTheme.mathBlue)

                VStack(alignment: .leading, spacing: 2) {
                    Text("Compartilhar / Importar Grade (JSON)")
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                    Text("Exporte sua grade 2026.2 ou importe horários de colegas da UFOP")
                        .font(.system(size: 12, weight: .regular))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                }

                Spacer()
            }

            Button {
                isScheduleShareSheetPresented = true
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "curlybraces")
                        .font(.system(size: 13, weight: .bold))

                    Text("Abrir exportação e importação JSON")
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 46)
                .background(
                    RoundedRectangle(cornerRadius: 13, style: .continuous)
                        .fill(CampusTheme.mathBlue)
                )
            }
            .buttonStyle(.plain)
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                .fill(CampusTheme.cardBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
        )
    }

    // MARK: - Appearance Card (Light / Dark / System)

    private var appearancePreferenceCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Aparência do aplicativo")
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

            Picker("Aparência", selection: $appearanceModeRaw) {
                Text("Sistema").tag("system")
                Text("Claro").tag("light")
                Text("Escuro").tag("dark")
            }
            .pickerStyle(.segmented)
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                .fill(CampusTheme.cardBackground(for: colorScheme))
        )
        .overlay(
            RoundedRectangle(cornerRadius: CampusTheme.cardCornerRadius, style: .continuous)
                .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
        )
    }

    // MARK: - Reset Seed Data Button

    private var resetDemoDataButton: some View {
        Button {
            withAnimation(.spring(response: 0.3, dampingFraction: 0.82)) {
                store.seedUFOP2026_2Data()
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "arrow.counterclockwise")
                    .font(.system(size: 14, weight: .semibold))

                Text("Restaurar dados iniciais de 2026.2")
                    .font(.system(size: 14, weight: .semibold, design: .rounded))
            }
            .foregroundStyle(CampusTheme.mathBlue)
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(CampusTheme.selectedPillBackground(for: colorScheme))
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Schedule Share / Import JSON Sheet (Sprint 2)

public struct ScheduleShareImportSheetView: View {
    public let store: CampusStore
    public let onClose: () -> Void

    @State private var jsonPayloadText: String = ""
    @State private var statusFeedback: String?
    @Environment(\.colorScheme) private var colorScheme

    public init(store: CampusStore, onClose: @escaping () -> Void) {
        self.store = store
        self.onClose = onClose
        self._jsonPayloadText = State(initialValue: store.exportSchedulePayload(semester: "2026.2"))
    }

    public var body: some View {
        ZStack {
            CampusTheme.cardBackground(for: colorScheme)
                .ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    HStack {
                        Text("GRADE HORÁRIA · JSON")
                            .font(.system(size: 12, weight: .bold, design: .rounded))
                            .tracking(1.4)
                            .foregroundStyle(CampusTheme.mathBlue)

                        Spacer()

                        Button(action: onClose) {
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
                        Text("Compartilhar ou Importar Grade")
                            .font(.system(size: 24, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme))

                        Text("Copie o JSON da sua grade 2026.2 ou cole um payload válido para sincronizar disciplinas e horários.")
                            .font(.system(size: 14, weight: .regular))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme))
                    }

                    if let statusFeedback {
                        HStack(spacing: 8) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(CampusTheme.algebraGreen)
                            Text(statusFeedback)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(CampusTheme.secondarySurface(for: colorScheme))
                        )
                    }

                    TextEditor(text: $jsonPayloadText)
                        .font(.system(size: 12, weight: .regular, design: .monospaced))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                        .scrollContentBackground(.hidden)
                        .frame(minHeight: 240)
                        .padding(12)
                        .background(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(CampusTheme.appBackground(for: colorScheme))
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .strokeBorder(CampusTheme.cardBorder(for: colorScheme), lineWidth: 1)
                        )

                    HStack(spacing: 12) {
                        Button {
                            jsonPayloadText = store.exportSchedulePayload(semester: "2026.2")
                            statusFeedback = "Payload JSON de 2026.2 atualizado com sucesso."
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "arrow.clockwise")
                                    .font(.system(size: 13, weight: .bold))
                                Text("Regerar JSON")
                                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                            }
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme))
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(CampusTheme.secondarySurface(for: colorScheme))
                            )
                        }
                        .buttonStyle(.plain)

                        Button {
                            let importedCount = store.importSchedulePayload(json: jsonPayloadText)
                            if importedCount > 0 {
                                statusFeedback = "\(importedCount) disciplina(s) importada(s) para 2026.2."
                            } else {
                                statusFeedback = "JSON inválido ou sem disciplinas compatíveis."
                            }
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "square.and.arrow.down")
                                    .font(.system(size: 13, weight: .bold))
                                Text("Importar JSON")
                                    .font(.system(size: 14, weight: .bold, design: .rounded))
                            }
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(CampusTheme.mathBlue)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 22)
                .padding(.top, 18)
                .padding(.bottom, 30)
            }
        }
    }
}

#Preview("ProfileView - Light") {
    ProfileView()
        .environment(CampusStore())
        .preferredColorScheme(.light)
}

#Preview("ProfileView - Dark") {
    ProfileView()
        .environment(CampusStore())
        .preferredColorScheme(.dark)
}
