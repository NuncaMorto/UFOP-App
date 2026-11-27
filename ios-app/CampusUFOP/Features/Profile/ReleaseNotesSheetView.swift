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
    public static let currentVersionString = "1.9.22"

    public static let versions: [ReleaseNoteEntry] = [
        ReleaseNoteEntry(
            version: "v1.9.22",
            title: "Widgets Corrigidos & Notificações Locais Offline",
            badge: "ATUAL",
            isLatest: true,
            items: [
                "Widgets 4×2 e 2×2 na Tela Inicial com extensão WidgetKit dedicada e leitura local compartilhada, garantindo atualização confiável e suporte às paletas do app.",
                "Sistema completo de notificações 100% local e offline no iPhone/iPad (sem servidor, sem push externo e sem rastreamento), com preferências persistidas localmente e no backup .ufopbackup.",
                "5 tipos de alertas inteligentes: Cartão ao vivo (Live Activity) na tela de bloqueio com contagem regressiva e progresso; Aviso pré-aula (10, 15 ou 30 min) com Vou à aula, Vou faltar e Ver no mapa; Prazos 3d e 1d às 09:00 (Marcar concluída / Lembrar à noite); Alerta de faltas em 65% e 85%; e Resumo matinal às 07:30 em dias letivos.",
                "Nova seção Configurações → Notificações com controles independentes, seletor de antecedência (10/15/30 min), status de permissão e card informativo offline."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.21",
            title: "Navegação Fluida, Persistência & Otimização de Performance",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Indicador configurável de presença na Agenda (Badge no card ou Borda colorida contínua) em Personalização, sincronizado no backup .ufopbackup, com cor âmbar alinhada para aulas canceladas e badge de clima tematizado pelas 9 paletas.",
                "Cabeçalhos de modais mais limpos mostrando apenas a sigla do departamento (ex.: DECOM), botão circular de edição no Perfil, matrícula tabular sem prefixo e dropdown tonal de campus no Editar Perfil.",
                "Matriz Curricular com cabeçalho limpo, seletor dropdown tonal de versão persistido por curso (sem reset automático) e botões alinhados; Grade Horária compacta com controles icon-only; Mapa 2D com rótulo \"Ouro Preto\"; card unificado de Independência & GitHub no topo do FAQ.",
                "Barra de abas com ícones SF Symbols arredondados harmônicos com Onest e animação deslizante refinada, além de cache thread-safe de contraste WCAG, LazyVStack nas listas e formatadores de data estáticos."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.20",
            title: "Widgets Renovados, Novo Site & Fluxos Inteligentes",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Widgets 4×2 e 2×2 redesenhados para leitura rápida no corredor, com contagem regressiva tabular, sala em destaque, próximo prazo no rodapé, estado \"Dia livre\", toque contextual (Agenda/Atividades) e sincronismo com as 9 paletas e Claro/Escuro.",
                "Página de download reconstruída em Onest + JetBrains Mono com seletor interativo das 9 paletas sincronizado ao mockup do app e verificação recolhível de SHA-256.",
                "Mapa 2D com seletor de campus integrado à busca e barra inferior alinhada acima da barra flutuante; Grade Horária e modais de disciplina com superfícies tonais limpas.",
                "Matriz Curricular com resumo colapsável e troca de curso via Configurações; Nova Avaliação com importação de Plano de Ensino (PDF) no topo detectando a disciplina automaticamente; card \"Sobre o Vida Acadêmica & Código Aberto\" no FAQ."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.19",
            title: "Redesign Completo de UI/UX, Onest & 9 Escadas Tonais",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Tipografia Onest offline (400–800) com dígitos tabulares em horários, faltas, cargas horárias, porcentagens e CRE no iOS e Android.",
                "9 escadas tonais WCAG 2.1 AA (Mata, Vinho padrão, Terracota, Ametista, Ouro Velho, Mineral, Cacau, Rubi e Cobalto) com degraus Canvas -> Surface 1 -> Surface 2.",
                "Agenda, Atividades, Disciplinas, Matriz e Grade Dinâmica renovadas com destaque ao vivo de aulas, janelas livres, check-in em 3 cartões, agrupamento temporal e seção agrupada de 26pt.",
                "Perfil horizontal com edição, 3 blocos Bento, alerta âmbar (>= 50% faltas), CRE \"Em curso\" simulável, grade 3x3 com pré-visualização e Mapa 2D com créditos discretos e busca PT-BR."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.18",
            title: "Ajuste no Ícone Monocromático & Favicon",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Sincronização da versão 1.9.18 com a correção do ícone monocromático (Material You / Android 13+) e atualização do favicon oficial na página de download."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.17",
            title: "Novo Ícone Oficial do Vida Acadêmica",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Novo ícone oficial do Vida Acadêmica em alta resolução (1024×1024) no catálogo AppIcon do iOS, sincronizado com Android e página de download.",
                "Identidade visual unificada gerada a partir da arte oficial de marca sem canal alfa para total conformidade com a App Store.",
                "Sincronização completa de versão com a atualização de ícone adaptativo e monocromático do Android 1.9.17."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.16",
            title: "Proteção Reforçada de Dados & Abertura Mais Rápida",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Blindagem do Keychain: evita apagar ou substituir a chave de proteção em falhas temporárias, gerando cópia de segurança local (.bak) antes de cada atualização.",
                "Proteção contra perda de dados: caso os arquivos locais não possam ser lidos ou descriptografados, as cópias originais são preservadas em quarentena e gravações futuras ficam travadas até recuperação.",
                "Compatibilidade garantida para restauração e leitura de estados legados sem schemaVersion.",
                "Sincronização completa de versão com as melhorias de inicialização e otimização do Android 1.9.16."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.15",
            title: "Matrizes Históricas, Resolução por Ingresso & PPCs Oficiais",
            badge: "ANTERIOR",
            isLatest: false,
            items: [
                "Integração de 33 matrizes curriculares históricas completas de 21 cursos (1.650 componentes), preservando as matrizes curadas de Eng. de Controle e Automação (AUT 2024/1 e 2026/1).",
                "Resolução automática por ano e semestre de ingresso na Matriz Curricular e na Grade Dinâmica (FAR, MED, AMB, LTP, EFB/EFL, ECO e outros), sem falso aviso de matriz legada.",
                "Desbloqueio de 4 cursos que estavam sem disciplinas em 2026/2 (D06, EST, LTI e LTT), totalizando 82 matrizes com IDs únicos na Grade Dinâmica.",
                "42 referências oficiais e links de PPCs na seção expansível Matrizes anteriores e PPCs oficiais, além de acesso direto no navegador para cursos ainda pendentes (IA e D05)."
            ]
        ),
        ReleaseNoteEntry(
            version: "v1.9.14",
            title: "Abertura Direta .ufopgrade, AirDrop, QR Code Offline & Mapa Mais Rápido",
            badge: "ANTERIOR",
            isLatest: false,
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
        let greenCheckTint = Color(hex: colorScheme == .dark ? 0x81C784 : 0x2E7D32)

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
                                        .font(CampusTheme.tabularFont(size: 16, weight: .bold))
                                        .foregroundStyle(release.isLatest ? accent : CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                                    Spacer()

                                    Text(release.badge)
                                        .font(CampusTheme.font(size: 10, weight: .bold))
                                        .foregroundStyle(release.isLatest ? accent : CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 4)
                                        .background(
                                            Capsule(style: .continuous)
                                                .fill(release.isLatest ? accent.opacity(colorScheme == .dark ? 0.22 : 0.14) : CampusTheme.secondarySurface(for: colorScheme, highContrast: hc))
                                        )
                                }

                                Text(release.title)
                                    .font(CampusTheme.font(size: 14, weight: .semibold))
                                    .foregroundStyle(CampusTheme.primaryText(for: colorScheme, highContrast: hc))

                                ForEach(release.items, id: \.self) { bullet in
                                    HStack(alignment: .top, spacing: 8) {
                                        Image(systemName: "checkmark.circle.fill")
                                            .font(.system(size: 13))
                                            .foregroundStyle(greenCheckTint)
                                            .padding(.top, 2)

                                        Text(bullet)
                                            .font(CampusTheme.font(size: 13))
                                            .foregroundStyle(CampusTheme.secondaryText(for: colorScheme, highContrast: hc))
                                            .fixedSize(horizontal: false, vertical: true)
                                    }
                                }
                            }
                            .padding(16)
                            .liquidGlassCard(cornerRadius: 24, tint: nil, highContrast: hc)
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
