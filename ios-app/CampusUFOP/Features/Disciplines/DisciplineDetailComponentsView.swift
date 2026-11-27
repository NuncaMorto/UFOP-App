import SwiftUI

// MARK: - Shared Components

internal struct DisciplineDetailFeedbackBanner: View {
    let text: String
    let color: Color
    let icon: String
    let highContrast: Bool

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(color)
                .padding(.top, 2)

            Text(text)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(color.opacity(colorScheme == .dark ? 0.20 : 0.12))
        )
    }
}

internal struct DisciplineDetailTextField: View {
    let placeholder: String
    @Binding var text: String
    let highContrast: Bool

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        TextField(placeholder, text: $text)
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(CampusTheme.secondarySurface(for: colorScheme, highContrast: highContrast))
            )
    }
}

// MARK: - Top Bar & Header

internal struct DisciplineDetailTopBar: View {
    let summary: DisciplineSummaryCard
    let deptColor: Color
    let primaryAccent: Color
    let isEadCourse: Bool
    let highContrast: Bool
    let minTouch: CGFloat
    let onClose: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        HStack(spacing: 8) {
            Text(summary.code)
                .font(CampusTheme.tabularFont(size: 12, weight: .bold))
                .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    Capsule(style: .continuous)
                        .fill(CampusTheme.departmentSoftTint(for: summary.colorKey, scheme: colorScheme))
                )

            Text(summary.semester)
                .font(CampusTheme.tabularFont(size: 12, weight: .bold))
                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .liquidGlassCapsule(selected: false, accent: deptColor, interactive: false, highContrast: highContrast)

            if isEadCourse {
                Text("EAD")
                    .font(CampusTheme.font(size: 11, weight: .bold))
                    .foregroundStyle(CampusTheme.mathBlue)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 6)
                    .background(
                        Capsule(style: .continuous)
                            .fill(CampusTheme.mathBlue.opacity(colorScheme == .dark ? 0.22 : 0.14))
                    )
            }

            Spacer()

            Button {
                onClose()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                    .frame(width: 40, height: 40)
                    .liquidGlassCapsule(selected: false, accent: primaryAccent, highContrast: highContrast)
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
            .accessibilityLabel("Fechar")
        }
    }
}

internal struct DisciplineDetailHeaderSection: View {
    let summary: DisciplineSummaryCard
    let highContrast: Bool

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let cleanProfessor = summary.professor.trimmingCharacters(in: .whitespacesAndNewlines)

        VStack(alignment: .leading, spacing: 6) {
            Text(summary.name)
                .font(CampusTheme.font(size: 27, weight: .bold))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

            if !cleanProfessor.isEmpty {
                Label(cleanProfessor, systemImage: "person.fill")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            }

            Text("\(summary.workloadHours)h · Limite de \(summary.maxAbsences) faltas (25%)")
                .font(CampusTheme.tabularFont(size: 13, weight: .medium))
                .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
        }
    }
}

internal struct DisciplineDetailLocationShortcutRow: View {
    let locationText: String
    let scheduleLabel: String?
    let deptColor: Color
    let primaryAccent: Color
    let highContrast: Bool
    let minTouch: CGFloat
    let onTap: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Button {
            onTap()
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "mappin.and.ellipse")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(primaryAccent)
                    .frame(width: 36, height: 36)
                    .background(
                        Circle()
                            .fill(primaryAccent.opacity(colorScheme == .dark ? 0.20 : 0.12))
                    )

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text("Prédio / Sala")
                            .font(CampusTheme.font(size: 11, weight: .bold))
                            .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))

                        if let scheduleLabel, !scheduleLabel.isEmpty {
                            Text("· \(scheduleLabel)")
                                .font(CampusTheme.tabularFont(size: 11, weight: .semibold))
                                .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))
                        }
                    }

                    Text(locationText)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }

                Spacer(minLength: 6)

                HStack(spacing: 4) {
                    Text("Ver no mapa")
                        .font(CampusTheme.font(size: 12, weight: .bold))
                    Image(systemName: "chevron.right")
                        .font(.system(size: 11, weight: .bold))
                }
                .foregroundStyle(primaryAccent)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .liquidGlassCard(cornerRadius: 18, tint: primaryAccent, interactive: true, highContrast: highContrast)
        }
        .buttonStyle(.plain)
        .minimumTouchTarget(minTouch)
        .campusHoverLift()
        .accessibilityLabel("Prédio / Sala: \(locationText). Ver no mapa")
    }
}

// MARK: - Absence Report Card

internal struct DisciplineAbsenceReportCard: View {
    let summary: DisciplineSummaryCard
    let deptColor: Color
    let highContrast: Bool
    var onUpdateAbsences: ((Int) -> Void)? = nil

    @Environment(\.colorScheme) private var colorScheme
    @Environment(CampusAppearanceController.self) private var appearance: CampusAppearanceController?
    @State private var showManualAlert: Bool = false
    @State private var manualDraft: String = ""

    var body: some View {
        let riskLevel = CampusTheme.semanticAbsenceRiskLevel(currentAbsences: summary.currentAbsences, maxAbsences: summary.maxAbsences)
        let riskColor = CampusTheme.semanticAbsenceRiskColor(currentAbsences: summary.currentAbsences, maxAbsences: summary.maxAbsences, scheme: colorScheme)
        let barColor = riskLevel > 0 ? riskColor : deptColor
        let progress = summary.maxAbsences > 0 ? min(1.0, max(0.0, Double(summary.currentAbsences) / Double(summary.maxAbsences))) : 0
        let remaining = max(0, summary.maxAbsences - summary.currentAbsences)
        let accentObj = appearance?.accentColor ?? .red

        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Controle de Faltas")
                        .font(CampusTheme.font(size: 13, weight: .semibold))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

                    Button {
                        manualDraft = "\(summary.currentAbsences)"
                        showManualAlert = true
                    } label: {
                        HStack(alignment: .firstTextBaseline, spacing: 3) {
                            Text("\(summary.currentAbsences)")
                                .font(CampusTheme.tabularFont(size: 30, weight: .bold))
                                .foregroundStyle(riskLevel > 0 ? riskColor : CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                            Text("/ \(summary.maxAbsences) faltas")
                                .font(CampusTheme.tabularFont(size: 15, weight: .semibold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                            if onUpdateAbsences != nil {
                                Image(systemName: "pencil")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(accentObj.primary(for: colorScheme))
                                    .padding(.leading, 3)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .disabled(onUpdateAbsences == nil)
                    .accessibilityLabel("Editar total de faltas manualmente: \(summary.currentAbsences) de \(summary.maxAbsences)")
                }

                Spacer()

                if let onUpdateAbsences {
                    HStack(spacing: 8) {
                        Button {
                            onUpdateAbsences(max(0, summary.currentAbsences - 1))
                        } label: {
                            Image(systemName: "minus")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                                .frame(width: 40, height: 40)
                                .background(Circle().fill(accentObj.surface1(for: colorScheme)))
                        }
                        .buttonStyle(.plain)
                        .disabled(summary.currentAbsences <= 0)
                        .opacity(summary.currentAbsences <= 0 ? 0.45 : 1)
                        .minimumTouchTarget(48)
                        .accessibilityLabel("Diminuir falta")

                        Button {
                            onUpdateAbsences(summary.currentAbsences + 1)
                        } label: {
                            Image(systemName: "plus")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(accentObj.textOnHighlightSurface(for: colorScheme))
                                .frame(width: 40, height: 40)
                                .background(Circle().fill(accentObj.surface2(for: colorScheme)))
                        }
                        .buttonStyle(.plain)
                        .minimumTouchTarget(48)
                        .accessibilityLabel("Adicionar falta")
                    }
                } else {
                    HStack(spacing: 5) {
                        Image(systemName: riskLevel > 0
                            ? CampusTheme.semanticAbsenceRiskIcon(currentAbsences: summary.currentAbsences, maxAbsences: summary.maxAbsences)
                            : "checkmark.shield.fill")
                            .font(.system(size: 12, weight: .bold))
                        Text(riskLevel > 0
                            ? CampusTheme.semanticAbsenceRiskLabel(currentAbsences: summary.currentAbsences, maxAbsences: summary.maxAbsences)
                            : "Em dia")
                            .font(CampusTheme.font(size: 12, weight: .bold))
                    }
                    .foregroundStyle(riskLevel > 0 ? riskColor : CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))
                    .padding(.horizontal, 11)
                    .padding(.vertical, 6)
                    .background(
                        Capsule(style: .continuous)
                            .fill((riskLevel > 0 ? riskColor : deptColor).opacity(colorScheme == .dark ? 0.22 : 0.14))
                    )
                }
            }

            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule(style: .continuous)
                        .fill(CampusTheme.cardBorder(for: colorScheme, highContrast: highContrast))
                        .frame(height: 8)

                    if progress > 0 {
                        Capsule(style: .continuous)
                            .fill(barColor)
                            .frame(width: max(12, proxy.size.width * progress), height: 8)
                    }
                }
            }
            .frame(height: 8)

            HStack {
                Text("Margem restante: \(remaining) falta(s)")
                    .font(CampusTheme.tabularFont(size: 12, weight: .medium))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

                Spacer()

                Text("Canceladas: \(summary.cancelledClasses)")
                    .font(CampusTheme.tabularFont(size: 12, weight: .medium))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
            }
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: CampusTheme.cardCornerRadius, tint: barColor, highContrast: highContrast, useSurface2: true)
        .alert("Editar faltas", isPresented: $showManualAlert) {
            TextField("Quantidade de faltas", text: $manualDraft)
                #if os(iOS)
                .keyboardType(.numberPad)
                #endif
            Button("Salvar") {
                if let parsed = Int(manualDraft.trimmingCharacters(in: .whitespacesAndNewlines)) {
                    onUpdateAbsences?(max(0, parsed))
                }
            }
            Button("Cancelar", role: .cancel) {}
        } message: {
            Text("Informe o total de faltas acumuladas em \(summary.name).")
        }
    }
}

// MARK: - EAD Modalidade & Plano de Ensino (PDF)

internal struct DisciplineEadAndSyllabusCard: View {
    @Binding var isEadBinding: Bool
    let isStoreAvailable: Bool
    let primaryAccent: Color
    let highContrast: Bool
    let minTouch: CGFloat
    let syllabusErrorMessage: String?
    let syllabusWarningMessage: String?
    let syllabusReportMessage: String?
    let onImportSyllabus: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Toggle(isOn: $isEadBinding) {
                VStack(alignment: .leading, spacing: 2) {
                    Label("Disciplina a distância (EAD)", systemImage: "laptopcomputer")
                        .font(CampusTheme.font(size: 14, weight: .bold))
                        .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                    Text("Exibe \"A distância (EAD)\" em vez de prédio e sala.")
                        .font(.system(size: 12))
                        .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                }
            }
            .tint(primaryAccent)
            .disabled(!isStoreAvailable)

            Divider()
                .overlay(CampusTheme.cardBorder(for: colorScheme, highContrast: highContrast))

            VStack(alignment: .leading, spacing: 8) {
                Text("Plano de Ensino (PDF)")
                    .font(CampusTheme.font(size: 14, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                Text("Importe o PDF do plano de ensino para cadastrar provas, seminários e trabalhos automaticamente. O arquivo é processado em memória e excluído.")
                    .font(.system(size: 12))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))

                Button {
                    onImportSyllabus()
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "doc.text.magnifyingglass")
                            .font(.system(size: 14, weight: .bold))
                        Text("Importar Plano de Ensino (PDF)")
                            .font(CampusTheme.font(size: 13, weight: .bold))
                    }
                    .foregroundStyle(CampusTheme.accessibleTextColor(primaryAccent, scheme: colorScheme))
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .liquidGlassCapsule(selected: false, accent: primaryAccent, interactive: true, highContrast: highContrast)
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
                .disabled(!isStoreAvailable)
            }

            if let syllabusErrorMessage {
                DisciplineDetailFeedbackBanner(text: syllabusErrorMessage, color: Color(hex: 0xDC2626), icon: "xmark.octagon.fill", highContrast: highContrast)
            }

            if let syllabusWarningMessage {
                DisciplineDetailFeedbackBanner(text: syllabusWarningMessage, color: Color(hex: 0xD97706), icon: "exclamationmark.triangle.fill", highContrast: highContrast)
            }

            if let syllabusReportMessage {
                DisciplineDetailFeedbackBanner(text: syllabusReportMessage, color: primaryAccent, icon: "checkmark.seal.fill", highContrast: highContrast)
            }
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 18, tint: primaryAccent, highContrast: highContrast)
    }
}

// MARK: - Absence History

internal struct DisciplineAbsenceHistorySection: View {
    let courseName: String
    let history: [AbsenceHistoryEntry]
    let highContrast: Bool

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Histórico de faltas e cancelamentos")
                .font(CampusTheme.font(size: 17, weight: .bold))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

            if history.isEmpty {
                Text("As marcações salvas no check-in da Agenda para \(courseName) aparecem aqui.")
                    .font(.system(size: 13))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                    .liquidGlassCard(cornerRadius: 16, highContrast: highContrast)
            } else {
                VStack(spacing: 8) {
                    ForEach(history) { entry in
                        DisciplineAbsenceHistoryRow(entry: entry, highContrast: highContrast)
                    }
                }
            }
        }
    }
}

private struct DisciplineAbsenceHistoryRow: View {
    let entry: AbsenceHistoryEntry
    let highContrast: Bool

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let isAbsent = entry.status == .absent
        let badgeForeground = isAbsent ? Color(hex: 0xDC2626) : CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast)
        let badgeBackground = isAbsent
            ? Color(hex: 0xDC2626).opacity(colorScheme == .dark ? 0.22 : 0.12)
            : CampusTheme.secondarySurface(for: colorScheme, highContrast: highContrast)

        HStack(spacing: 12) {
            Image(systemName: isAbsent ? "xmark.circle.fill" : "slash.circle.fill")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(badgeForeground)

            VStack(alignment: .leading, spacing: 2) {
                Text(entry.formattedDate)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                Text(entry.timeRangeLabel)
                    .font(.system(size: 12))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
            }

            Spacer(minLength: 6)

            Text(entry.badgeTitle)
                .font(CampusTheme.font(size: 11, weight: .bold))
                .foregroundStyle(badgeForeground)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Capsule(style: .continuous).fill(badgeBackground))
                .lineLimit(1)
        }
        .padding(12)
        .liquidGlassCard(cornerRadius: 16, highContrast: highContrast)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Editable Notes & Destructive Course Removal

internal struct DisciplineNotesSection: View {
    @Binding var notesText: String
    let highContrast: Bool

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Anotações")
                    .font(CampusTheme.font(size: 17, weight: .bold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                Spacer()

                Text("Salvo automaticamente")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
            }

            TextEditor(text: $notesText)
                .font(.system(size: 15))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                .scrollContentBackground(.hidden)
                .frame(minHeight: 96)
                .padding(12)
                .liquidGlassCard(cornerRadius: 16, highContrast: highContrast)
        }
    }
}

internal struct DisciplineDeleteCourseButton: View {
    let courseName: String
    let minTouch: CGFloat
    let onRequestDelete: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Button(role: .destructive) {
            onRequestDelete()
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "trash.fill")
                    .font(.system(size: 14, weight: .semibold))
                Text("Remover disciplina")
                    .font(CampusTheme.font(size: 15, weight: .bold))
            }
            .foregroundStyle(Color(hex: 0xDC2626))
            .frame(maxWidth: .infinity, minHeight: max(48, minTouch))
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Color(hex: 0xDC2626).opacity(colorScheme == .dark ? 0.18 : 0.10))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(Color(hex: 0xDC2626).opacity(0.32), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Remover disciplina \(courseName)")
    }
}
