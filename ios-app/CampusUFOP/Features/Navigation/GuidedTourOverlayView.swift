import SwiftUI

private struct GuidedTourStepItem {
    let tab: CampusTab
    let icon: String
    let title: String
    let description: String
}

private let guidedTourSteps: [GuidedTourStepItem] = [
    GuidedTourStepItem(
        tab: .agenda,
        icon: "calendar",
        title: "Sua Agenda do Dia",
        description: "Acompanhe as aulas de hoje, horários, salas previstas do ICEB/Bloco de Salas ou marcação EAD, clima do campus e faça check-in de presença, falta ou aula cancelada."
    ),
    GuidedTourStepItem(
        tab: .disciplines,
        icon: "book.closed.fill",
        title: "Disciplinas & Grade Horária",
        description: "Alterne entre Grade e Barras, controle o limite de faltas (25%), envie Planos de Ensino em PDF e abra sua grade semanal vertical ou horizontal."
    ),
    GuidedTourStepItem(
        tab: .activities,
        icon: "checklist",
        title: "Atividades, Provas & Prazos",
        description: "Filtre múltiplos tipos de atividade na mesma linha (Provas, Trabalhos, Seminários, Concluídas), edite prazos e acompanhe detalhes com 1 toque."
    ),
    GuidedTourStepItem(
        tab: .profile,
        icon: "person.crop.circle",
        title: "Perfil, CRE & Matriz Curricular",
        description: "Acompanhe seu CRE pelo Histórico Escolar, simule notas futuras, explore as 54 matrizes curriculares de graduação e consulte os Calendários 2026.2 a 2027.2."
    ),
    GuidedTourStepItem(
        tab: .profile,
        icon: "lock.shield.fill",
        title: "Segurança, Estilo & FAQ",
        description: "Em Perfil você ativa Face ID / senha, acessibilidade, personalização com 7 cores, backup criptografado (.ufopbackup) e pode rever este tour a qualquer hora!"
    )
]

public struct GuidedTourOverlayView: View {
    @Binding public var selectedTab: CampusTab
    public let onFinish: () -> Void

    @Environment(CampusAppearanceController.self) private var appearance
    @Environment(\.colorScheme) private var colorScheme
    @State private var stepIndex: Int = 0

    public init(selectedTab: Binding<CampusTab>, onFinish: @escaping () -> Void) {
        self._selectedTab = selectedTab
        self.onFinish = onFinish
    }

    public var body: some View {
        let current = guidedTourSteps[stepIndex]
        let accent = appearance.accentColor.primary(for: colorScheme)
        let hc = appearance.accessibility.highContrastMode
        let isLast = stepIndex == guidedTourSteps.count - 1

        ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()

            VStack {
                Spacer()

                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        HStack(spacing: 8) {
                            Image(systemName: current.icon)
                                .font(.system(size: 16, weight: .bold))
                                .foregroundStyle(appearance.accentColor.onPrimary(for: colorScheme))
                                .frame(width: 34, height: 34)
                                .background(Circle().fill(accent))

                            Text("TOUR GUIADO · \(stepIndex + 1)/\(guidedTourSteps.count)")
                                .font(.system(size: 11, weight: .bold, design: .rounded))
                                .tracking(1.1)
                                .foregroundStyle(accent)
                        }

                        Spacer()

                        Button {
                            onFinish()
                        } label: {
                            Text("Pular")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                        }
                        .buttonStyle(.plain)
                        .minimumTouchTarget(40)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text(current.title)
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                        Text(current.description)
                            .font(.system(size: 14, weight: .regular))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .id(stepIndex)
                    .transition(appearance.accessibility.reduceMotion ? .opacity : .asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity), removal: .opacity))

                    HStack(spacing: 6) {
                        ForEach(guidedTourSteps.indices, id: \.self) { idx in
                            Capsule(style: .continuous)
                                .fill(idx == stepIndex ? accent : CampusTheme.secondarySurface(for: colorScheme, highContrast: hc))
                                .frame(width: idx == stepIndex ? 24 : 8, height: 6)
                        }
                    }

                    HStack(spacing: 10) {
                        if stepIndex > 0 {
                            Button {
                                changeStep(to: stepIndex - 1)
                            } label: {
                                Text("Anterior")
                                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                                    .frame(maxWidth: .infinity, minHeight: appearance.accessibility.minTouchHeight)
                                    .liquidGlassCapsule(selected: false, accent: accent, highContrast: hc)
                            }
                            .buttonStyle(.plain)
                        }

                        LiquidGlassPrimaryButton(
                            tint: accent,
                            foreground: appearance.accentColor.onPrimary(for: colorScheme),
                            minHeight: appearance.accessibility.minTouchHeight
                        ) {
                            if isLast {
                                onFinish()
                            } else {
                                changeStep(to: stepIndex + 1)
                            }
                        } label: {
                            Text(isLast ? "Concluir tour" : "Próximo")
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                        }
                    }
                }
                .padding(20)
                .liquidGlassCard(cornerRadius: 26, tint: accent, interactive: false, highContrast: hc)
                .padding(.horizontal, 18)
                .padding(.bottom, 86)
            }
        }
        .onAppear {
            selectedTab = guidedTourSteps[stepIndex].tab
        }
    }

    private func changeStep(to next: Int) {
        let clamped = max(0, min(guidedTourSteps.count - 1, next))
        if appearance.accessibility.reduceMotion {
            stepIndex = clamped
            selectedTab = guidedTourSteps[clamped].tab
        } else {
            withAnimation(.spring(response: 0.34, dampingFraction: 0.84)) {
                stepIndex = clamped
                selectedTab = guidedTourSteps[clamped].tab
            }
        }
    }
}
