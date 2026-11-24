import SwiftUI

public struct ReleaseNoteEntry: Identifiable, Sendable {
    public var id: String { version }
    public let version: String
    public let title: String
    public let badge: String
    public let isLatest: Bool
    public let items: [String]
}

public enum CampusReleaseNotesData {
    public static let currentVersionString = "1.9.14"

    public static let versions: [ReleaseNoteEntry] = [
        ReleaseNoteEntry(
            version: "v1.9.14",
            title: "Abertura Direta .ufopgrade, AirDrop, QR Code Offline & Mapa Mais Rápido",
            badge: "ATUAL",
            isLatest: true,
            items: [
                "Abertura direta de arquivos .ufopgrade recebidos pelo WhatsApp, Telegram, Arquivos ou AirDrop com confirmação segura antes de salvar em Grades de Amigos (respeitando o bloqueio por senha e Face ID / Touch ID).",
                "Interoperabilidade total ufopgrade-v1 entre iOS e Android, com Grades de Amigos na Grade Horária e AirDrop por aproximação (iOS 17+).",
                "Compartilhamento e leitura via QR Code 100% offline (UFOPG1:) entre iOS e Android diretamente pela Grade Horária.",
                "Otimizações de performance no Mapa 2D Offline com culling de viewport, cache de geometria/rótulos e carregamento sob demanda."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.13",
            title: "Grade Dinâmica para 48 Cursos da UFOP & Modularização",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Grade Dinâmica Interativa liberada automaticamente para os 48 cursos com disciplinas detalhadas no catálogo UFOP (como Farmácia, entre outros), resolvida pelo curso real e semestre de ingresso.",
                "Construção acíclica automática de pré-requisitos, carga horária mínima e slots optativos/eletivos, preservando as versões históricas curadas de Automação (2024/1 e 2026/1) e nota informativa para os 6 cursos pendentes.",
                "Novo seletor pesquisável na Grade Dinâmica com busca por nome, código ou campus, exibindo versão curricular e carga horária total.",
                "Modularização da ponte Swift (`CoreFFI/`) e sincronização completa de versão com o lançamento Android 1.9.13."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.12",
            title: "Adaptação iPadOS & Tablets + Matriz 2024/1 Completa",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Experiência nativa para iPadOS, tablets e dobráveis seguindo o Apple HIG: barra lateral adaptativa, painéis mestre-detalhe lado a lado em Agenda, Disciplinas e Perfil, e grade multi-colunas em Atividades.",
                "Suporte completo às 4 orientações no iPad, múltiplas janelas (Split View / Stage Manager), efeitos de ponteiro/trackpad (.hoverEffect) e atalhos de teclado (⌘1–⌘4).",
                "Matriz Curricular 2024/1 (Currículo 2) de Eng. de Controle e Automação completa no catálogo e simulador de CRE (49 obrigatórias, 2670h + 300h eletivas + 640h atividades = 3610h), com seleção automática para ingressantes 2024/1–2025/2 sem aviso falso de matriz legada.",
                "Sincronização completa de versão com o lançamento Android 1.9.12."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.11",
            title: "Matrizes por Ingresso, UI Alinhada & Aba Atualizações",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Detecção automática da matriz da Grade Dinâmica pelo semestre de ingresso: Automação 2024/1 · Currículo 2 (2024/1–2025/2) e 2026/1 · Currículo 3 (2026/1+).",
                "Botão de recolher/expandir apenas com ícone de seta e controles Horizontal/Vertical/Limpar alinhados e simétricos sem botão extra de tela cheia.",
                "Nova subaba Atualizações dedicada ao histórico de patches fora de FAQ e botão Refazer Tour Guiado movido para Ajustes.",
                "Sincronização completa de versão com o lançamento Android 1.9.11."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.10",
            title: "Cabeçalho Recolhível na Grade Dinâmica",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Novo botão Recolher / Expandir no cartão superior da Grade Dinâmica para liberar mais área de visualização sem perder o progresso visível.",
                "Sincronização completa de versão com a atualização Android 1.9.10."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.9",
            title: "Sincronização de Versão & Estabilidade",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Sincronização de versão com a correção do atualizador interno do Android (v1.9.9).",
                "Mantém integralmente a Grade Dinâmica Interativa (Matriz 2023 · Automação), Calendários UFOP e criptografia AES-256-GCM."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.8",
            title: "Grade Dinâmica Interativa (Matriz 2023)",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Nova Grade Dinâmica Interativa na aba Perfil com a matriz completa de Eng. de Controle e Automação (Matriz 2023 · 54 disciplinas, 2970h).",
                "Desbloqueio automático por pré-requisitos ao marcar disciplinas concluídas e remoção transitiva em cascata ao desmarcar dependentes.",
                "Alternância persistente entre modos Horizontal e Vertical com rolagem bidirecional livre (horizontal e vertical), tela cheia opcional e persistência criptografada (.enc / .ufopbackup)."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.7",
            title: "Licença GPLv3, Conformidade & Melhorias",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Adequação completa de nomes, identificadores e avisos de aplicativo independente para estudantes.",
                "Projeto publicado sob licença GNU GPLv3."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.6",
            title: "Paridade iOS Liquid Glass, Filtro Multi-Tipo & Disciplinas",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Visual Apple Liquid Glass nativo para iOS 26 com fallback translúcido em iOS 17–18, 7 cores de destaque e modo claro/escuro.",
                "Segurança e privacidade: criptografia AES-256-GCM com chave no Keychain, exclusão imediata de PDFs temporários, bloqueio por senha e Face ID / Touch ID.",
                "Cadastro em 4 etapas (Atestado PDF com detecção EAD, Planos de Ensino opcionais com validação, Personalização e Acessibilidade), Tour Guiado e Notas de Atualização.",
                "Salas 2026.2 do ICEB e Bloco de Salas, Disciplinas em Grade ou Barras, Agenda com 7 dias completos e Calendários Acadêmicos 2026.2, 2027.1 e 2027.2.",
                "Filtro multi-tipo em Atividades (Todos os tipos ▾) e ficha de avaliação alinhada com edição e confirmação antes de excluir."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.5",
            title: "Salas 2026.2, Disciplinas em Grade/Barras & Polimento",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Mapeamento automático de salas (ICEB e Bloco de Salas) a partir do Atestado de Matrícula.",
                "Alternância persistente entre Grade e Barras em Disciplinas e Vertical/Horizontal na Grade Horária."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.0",
            title: "Calendários Acadêmicos, Agenda Redesenhada & Acessibilidade",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Calendários Acadêmicos 2026.2, 2027.1 e 2027.2 integrados para consulta dos estudantes.",
                "Agenda com 7 dias visíveis, aviso Agora / Próxima aula e melhorias de acessibilidade WCAG AA."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.8.0",
            title: "Segurança, Acessibilidade, Backup & Novo Cadastro",
            badge: "HISTÓRICO",
            isLatest: false,
            items: [
                "Backup completo criptografado (.ufopbackup), fluxo de saída seguro e painel global de acessibilidade."
            ]
        )
    ]
}

public struct ReleaseNotesSheetView: View {
    public let onDismiss: () -> Void
    @Environment(CampusAppearanceController.self) private var appearance
    @Environment(\.colorScheme) private var colorScheme

    public init(onDismiss: @escaping () -> Void) {
        self.onDismiss = onDismiss
    }

    public var body: some View {
        let accent = appearance.accentColor.primary(for: colorScheme)
        let hc = appearance.accessibility.highContrastMode

        ZStack {
            LiquidGlassBackdrop(accent: appearance.accentColor, highContrast: hc)

            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(accent.opacity(colorScheme == .dark ? 0.24 : 0.16))
                            .frame(width: 40, height: 40)
                        Image(systemName: "sparkles")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(accent)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Notas de Atualização")
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))
                        Text("Novidades do Vida Acadêmica")
                            .font(.system(size: 12))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                    }

                    Spacer()

                    Button(action: onDismiss) {
                        Image(systemName: "xmark")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                            .frame(width: 34, height: 34)
                            .liquidGlassCapsule(selected: false, accent: accent, highContrast: hc)
                    }
                    .buttonStyle(.plain)
                    .minimumTouchTarget(appearance.accessibility.minTouchHeight)
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 12)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 14) {
                        ForEach(CampusReleaseNotesData.versions) { release in
                            VStack(alignment: .leading, spacing: 10) {
                                HStack {
                                    Text(release.version)
                                        .font(.system(size: 16, weight: .bold, design: .rounded))
                                        .foregroundStyle(release.isLatest ? accent : CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                                    Spacer()

                                    Text(release.badge)
                                        .font(.system(size: 10, weight: .bold, design: .rounded))
                                        .foregroundStyle(release.isLatest ? appearance.accentColor.onPrimary(for: colorScheme) : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 4)
                                        .background(
                                            Capsule(style: .continuous)
                                                .fill(release.isLatest ? accent : CampusTheme.secondarySurface(for: colorScheme, highContrast: hc))
                                        )
                                }

                                Text(release.title)
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                                ForEach(release.items, id: \.self) { bullet in
                                    HStack(alignment: .top, spacing: 8) {
                                        Image(systemName: "checkmark.circle.fill")
                                            .font(.system(size: 13))
                                            .foregroundStyle(release.isLatest ? accent : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                            .padding(.top, 2)

                                        Text(bullet)
                                            .font(.system(size: 13))
                                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                            .fixedSize(horizontal: false, vertical: true)
                                    }
                                }
                            }
                            .padding(16)
                            .liquidGlassCard(cornerRadius: 20, tint: release.isLatest ? accent : nil, highContrast: hc)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 20)
                }

                LiquidGlassPrimaryButton(
                    tint: accent,
                    foreground: appearance.accentColor.onPrimary(for: colorScheme),
                    minHeight: appearance.accessibility.minTouchHeight
                ) {
                    onDismiss()
                } label: {
                    Text("Entendi")
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 14)
            }
        }
    }
}
