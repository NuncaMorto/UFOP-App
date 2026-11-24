import SwiftUI

// MARK: - Assessments & Deadlines Section

internal struct DisciplineAssessmentsSection: View {
    let courseName: String
    let colorKey: DepartmentColorKey
    let assessments: [AssessmentItem]
    let deptColor: Color
    let highContrast: Bool
    let minTouch: CGFloat
    @Binding var isAddingAssessment: Bool
    @Binding var newAssessmentTitle: String
    @Binding var newAssessmentKind: String
    @Binding var newAssessmentDueDate: Date
    @Binding var newAssessmentDueTime: String
    let onSubmitAssessment: () -> Void
    let onToggleAssessment: (String) -> Void
    let onDeleteAssessment: (String) -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center) {
                Text("Prazos e avaliações")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

                Spacer()

                Button {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                        isAddingAssessment.toggle()
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: isAddingAssessment ? "xmark" : "plus")
                            .font(.system(size: 11, weight: .bold))
                        Text(isAddingAssessment ? "Fechar" : "Novo prazo")
                            .font(.system(size: 12, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(
                        Capsule(style: .continuous)
                            .fill(CampusTheme.departmentSoftTint(for: colorKey, scheme: colorScheme))
                    )
                }
                .buttonStyle(.plain)
                .minimumTouchTarget(minTouch)
                .accessibilityLabel("Adicionar avaliação")
            }

            if isAddingAssessment {
                DisciplineAddAssessmentCard(
                    courseName: courseName,
                    deptColor: deptColor,
                    highContrast: highContrast,
                    newAssessmentTitle: $newAssessmentTitle,
                    newAssessmentKind: $newAssessmentKind,
                    newAssessmentDueDate: $newAssessmentDueDate,
                    newAssessmentDueTime: $newAssessmentDueTime,
                    onSubmit: onSubmitAssessment
                )
            }

            if assessments.isEmpty {
                Text("Nenhuma avaliação registrada. Use \"Novo prazo\" ou importe o Plano de Ensino.")
                    .font(.system(size: 13))
                    .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                    .liquidGlassCard(cornerRadius: 16, highContrast: highContrast)
            } else {
                VStack(spacing: 10) {
                    ForEach(assessments) { item in
                        DisciplineAssessmentRow(
                            item: item,
                            colorKey: colorKey,
                            deptColor: deptColor,
                            highContrast: highContrast,
                            minTouch: minTouch,
                            onToggle: { onToggleAssessment(item.id) },
                            onDelete: { onDeleteAssessment(item.id) }
                        )
                    }
                }
            }
        }
    }
}

private struct DisciplineAddAssessmentCard: View {
    let courseName: String
    let deptColor: Color
    let highContrast: Bool
    @Binding var newAssessmentTitle: String
    @Binding var newAssessmentKind: String
    @Binding var newAssessmentDueDate: Date
    @Binding var newAssessmentDueTime: String
    let onSubmit: () -> Void

    @Environment(\.colorScheme) private var colorScheme
    private let assessmentKinds = ["Trabalho", "Seminário", "Prova"]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Nova avaliação (\(courseName))")
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))

            HStack(spacing: 8) {
                ForEach(assessmentKinds, id: \.self) { kind in
                    let isSelected = newAssessmentKind == kind
                    Button {
                        newAssessmentKind = kind
                    } label: {
                        Text(kind)
                            .font(.system(size: 12, weight: .semibold, design: .rounded))
                            .foregroundStyle(isSelected ? .white : CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .liquidGlassCapsule(selected: isSelected, accent: deptColor, interactive: true, highContrast: highContrast)
                    }
                    .buttonStyle(.plain)
                }
            }

            DisciplineDetailTextField(placeholder: "Título (ex.: Prova 1, Seminário)", text: $newAssessmentTitle, highContrast: highContrast)

            HStack(spacing: 10) {
                DatePicker("Data", selection: $newAssessmentDueDate, displayedComponents: .date)
                    .labelsHidden()
                    .environment(\.locale, Locale(identifier: "pt_BR"))

                DisciplineDetailTextField(placeholder: "Horário (18:00)", text: $newAssessmentDueTime, highContrast: highContrast)
            }

            LiquidGlassPrimaryButton(tint: deptColor, foreground: .white, minHeight: 44) {
                onSubmit()
            } label: {
                Label("Adicionar em Prazos e Atividades", systemImage: "checkmark.circle.fill")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
            }
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: 16, tint: deptColor, highContrast: highContrast)
    }
}

private struct DisciplineAssessmentRow: View {
    let item: AssessmentItem
    let colorKey: DepartmentColorKey
    let deptColor: Color
    let highContrast: Bool
    let minTouch: CGFloat
    let onToggle: () -> Void
    let onDelete: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        HStack(spacing: 14) {
            VStack(spacing: 1) {
                Text(item.dayNumber)
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))

                Text(item.monthShort)
                    .font(.system(size: 10, weight: .bold, design: .rounded))
                    .tracking(0.8)
                    .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))
            }
            .frame(width: 48, height: 46)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(CampusTheme.departmentSoftTint(for: colorKey, scheme: colorScheme))
            )

            VStack(alignment: .leading, spacing: 3) {
                Text(item.title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: highContrast))
                    .strikethrough(item.completed, color: CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))

                HStack(spacing: 6) {
                    Text(item.kind)
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundStyle(CampusTheme.accessibleTextColor(deptColor, scheme: colorScheme))

                    if !item.subtitle.isEmpty {
                        Text("•")
                            .foregroundStyle(CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
                        Text(item.subtitle)
                            .font(.system(size: 12))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: highContrast))
                    }
                }
            }

            Spacer()

            Button {
                withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                    onToggle()
                }
            } label: {
                Image(systemName: item.completed ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(item.completed ? deptColor : CampusTheme.mutedText(for: colorScheme, highContrast: highContrast))
            }
            .buttonStyle(.plain)
            .minimumTouchTarget(minTouch)
            .accessibilityLabel(item.completed ? "Desmarcar conclusão" : "Marcar avaliação como concluída")
        }
        .padding(12)
        .liquidGlassCard(cornerRadius: 16, highContrast: highContrast)
        .contextMenu {
            Button {
                onToggle()
            } label: {
                Label(
                    item.completed ? "Marcar como pendente" : "Marcar como concluída",
                    systemImage: item.completed ? "arrow.uturn.backward.circle" : "checkmark.circle"
                )
            }

            Button(role: .destructive) {
                onDelete()
            } label: {
                Label("Excluir avaliação", systemImage: "trash")
            }
        }
    }
}
