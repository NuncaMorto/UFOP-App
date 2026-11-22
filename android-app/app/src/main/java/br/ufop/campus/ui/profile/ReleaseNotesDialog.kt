package br.ufop.campus.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.ufop.campus.ui.theme.LocalCampusAccessibility

data class ReleaseNoteEntry(
    val version: String,
    val title: String,
    val badge: String,
    val isLatest: Boolean = false,
    val items: List<String>
)

object CampusReleaseNotes {
    val versions: List<ReleaseNoteEntry> = listOf(
        ReleaseNoteEntry(
            version = "v1.9.10",
            title = "Cabeçalho Recolhível na Grade Dinâmica",
            badge = "ATUAL",
            isLatest = true,
            items = listOf(
                "Removido o título duplicado dentro do cartão da Grade Dinâmica para deixar o topo mais limpo;",
                "Novo botão Recolher / Expandir no cabeçalho da Grade Dinâmica, liberando mais área visível para navegar pelas disciplinas sem perder o progresso em tempo real."
            )
        ),
        ReleaseNoteEntry(
            version = "v1.9.9",
            title = "Correção do Atualizador Interno Android",
            badge = "ANTERIOR",
            isLatest = false,
            items = listOf(
                "Corrigida a instalação direta pelo atualizador interno no Android 14/15: após verificar o SHA-256, o app entrega o pacote diretamente ao instalador nativo do sistema (com barra de progresso real e botão Abrir) preservando 100% dos seus dados;",
                "Cache verificado protegido durante a troca do app para permitir retomar sem baixar novamente e diagnóstico persistido em caso de erro no sistema;",
                "Modo alternativo via sessão PackageInstaller reforçado com confirmação explícita do usuário e limpeza de sessões antigas."
            )
        ),
        ReleaseNoteEntry(
            version = "v1.9.8",
            title = "Grade Dinâmica Interativa (Matriz 2023 de Automação)",
            badge = "ANTERIOR",
            isLatest = false,
            items = listOf(
                "Nova aba Grade Dinâmica no Perfil (logo abaixo de Métricas & CRE), iniciando com a Matriz 2023 (Currículo 2) de Eng. de Controle e Automação (54 disciplinas · 2970h);",
                "Toque para concluir disciplinas e liberar pré-requisitos em tempo real, com desmarcação automática em cascata para dependentes e aviso dos requisitos faltantes ao tocar em matérias bloqueadas;",
                "Alternância persistida entre visualização Horizontal (colunas por período) e Vertical (linhas por período), ambas com rolagem livre horizontal e vertical, modo tela cheia e salvamento local criptografado."
            )
        ),
        ReleaseNoteEntry(
            version = "v1.9.7",
            title = "Atualizador In-App, Licença GPLv3 & Página Web",
            badge = "ANTERIOR",
            isLatest = false,
            items = listOf(
                "Novo atualizador automático dentro do app (via GitHub Releases): baixa em segundo plano com barra de progresso, valida integridade via SHA-256 e reinicia preservando 100% dos seus dados;",
                "Nova opção Verificar atualizações (GitHub) em Perfil → FAQ e aviso discreto no topo quando houver nova versão;",
                "Repositório aberto sob licença GNU GPLv3 com nova página web de download direto para Android."
            )
        ),
        ReleaseNoteEntry(
            version = "v1.9.6",
            title = "Filtro Multi-Tipo em Atividades, Disciplinas & Alinhamento",
            badge = "ANTERIOR",
            isLatest = false,
            items = listOf(
                "Correção na aba Disciplinas ao alternar entre Grade e Barras (sem ocultar matérias ou fechar ao abrir detalhes) e leitura limpa de 12/18 · Atenção;",
                "Novo seletor multi-tipo em Atividades (Todos os tipos ▾) para filtrar um ou mais tipos (Provas, Trabalhos, Seminários e Concluídas) na mesma linha;",
                "Alinhamento corrigido do chip Pendente / Concluída na ficha da avaliação."
            )
        ),
        ReleaseNoteEntry(
            version = "v1.9.5",
            title = "Salas 2026.2, Disciplinas em Grade/Barras & Polimento Visual",
            badge = "ANTERIOR",
            isLatest = false,
            items = listOf(
                "Salas de aula 2026.2 (ICEB e Bloco de Salas): identificação automática da sala no atestado e na grade; outros prédios permanecem sem sala fixa até nova tabela;",
                "Disciplinas em Grade ou Barras: novo modo de exibição escolhido pelo aluno e cartões mais limpos, sem excesso de selos;",
                "Agenda sem redundância: aviso \"Agora / Próxima aula\" integrado diretamente no card da aula quando você já está no dia dela;",
                "Grade Horária com memória de orientação: o app lembra se você prefere Vertical ou Horizontal ao sair e voltar;",
                "Botões e filtros proporcionais: visual mais compacto e elegante mantendo conforto de toque."
            )
        ),
        ReleaseNoteEntry(
            version = "v1.9.0",
            title = "Calendários Acadêmicos, Agenda Redesenhada & Acessibilidade",
            badge = "ANTERIOR",
            isLatest = false,
            items = listOf(
                "Calendários Acadêmicos 2026.2, 2027.1 e 2027.2 integrados (com referência pública à Resolução CUNI 2945 e calendário presencial 2027).",
                "Agenda redesenhada: 7 dias da semana visíveis sem corte, card inteligente 'Agora / Próxima aula', aviso de período letivo/recesso e remoção do relógio analógico redundante.",
                "Seletores nativos Material 3 de Data e Hora com compensação de fuso horário UTC-3 (sem retroceder dias ao selecionar datas) e entrada por relógio ou teclado.",
                "Grade Horária semanal interativa: toque na aula para detalhes completos, filtro de horários vazios, modo compacto legível e suporte a rotação livre de tela.",
                "Auditoria completa de Acessibilidade (IHC/WCAG AA no tema escuro): contraste aprimorado, alvos de toque >= 48dp, navegação TalkBack aprimorada e confirmação com diálogo antes de excluir registros."
            )
        ),
        ReleaseNoteEntry(
            version = "v1.8.0",
            title = "Segurança, Acessibilidade & Novo Cadastro",
            badge = "ANTERIOR",
            isLatest = false,
            items = listOf(
                "Criptografia local AES-256-GCM (AndroidKeyStore) protegendo dados acadêmicos em disco e remoção imediata de PDFs residuais após cada importação.",
                "Bloqueio opcional do aplicativo por senha e biometria (digital/facial), proteção contra capturas de tela (FLAG_SECURE) e bloqueio progressivo contra tentativas incorretas.",
                "Backup completo criptografado (.ufopbackup) com restauração no cadastro inicial ou em Configurações, e fluxo seguro de saída da conta com aviso de backup.",
                "Sistema global de Acessibilidade: perfil rápido TalkBack, escala de fonte (100% a 145%), alto contraste WCAG, negrito reforçado, redução de movimento e áreas de toque de 56dp.",
                "Cadastro inicial em 4 etapas: atestado com detecção automática de disciplinas EAD, envio opcional de Planos de Ensino (PDF) por disciplina, 7 cores de destaque (incluindo Amarelo, Ciano e Marrom) e acessibilidade.",
                "Tour Guiado animado pelas abas principais, nova área de FAQ & Ajuda e histórico de Notas de Atualização."
            )
        ),
        ReleaseNoteEntry(
            version = "v1.7.5",
            title = "Personalização, Widget & Matriz Legada",
            badge = "ANTERIOR",
            items = listOf(
                "Personalização de cores de destaque com persistência durável e sincronização imediata com o widget da tela inicial.",
                "Correção definitiva do widget acadêmico com atualização automática ao salvar aulas ou alternar tema.",
                "Reorganização do Perfil em sub-abas dedicadas: Métricas & CRE, Personalização e Configurações.",
                "Melhorias de legibilidade no Modo Claro (barra de status transparente com ícones escuros e contraste de interruptores) e proteção para Matriz Curricular Legada (< 2026/1)."
            )
        ),
        ReleaseNoteEntry(
            version = "v1.7",
            title = "Catálogo Curricular, Matriz & Histórico",
            badge = "HISTÓRICO",
            items = listOf(
                "Catálogo com 54 cursos (ref. UFOP) para seleção rápida e acompanhamento detalhado da matriz curricular.",
                "Importação de Histórico Escolar (PDF) com cálculo automático de CRE, carga horária integralizada e simulação de notas.",
                "Exportação e compartilhamento de grades horárias (.ufopgrade) com amigos e escolha de pasta de armazenamento."
            )
        ),
        ReleaseNoteEntry(
            version = "v1.6.5",
            title = "Atestado PDF, Planos de Ensino & Calendário",
            badge = "HISTÓRICO",
            items = listOf(
                "Leitura inteligente do Atestado de Matrícula (PDF) compatível com o Minha UFOP para importar disciplinas, prédios e grade semanal.",
                "Importação de avaliações a partir de Planos de Ensino (PDF) e pacote de Calendário Acadêmico verificado via SHA-256.",
                "Controle completo de presenças, faltas e aulas canceladas com cores por departamento."
            )
        )
    )
}

@Composable
fun ReleaseNotesDialog(
    onDismiss: () -> Unit
) {
    val a11y = LocalCampusAccessibility.current.settings
    val buttonMinHeight = if (a11y.largeTouchTargets) 56.dp else 48.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(
                width = if (a11y.highContrastMode) 2.dp else 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Cabeçalho fixo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Notas de Atualização",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = "Novidades e evolução do Vida Acadêmica",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .defaultMinSize(minWidth = buttonMinHeight, minHeight = buttonMinHeight)
                            .semantics { contentDescription = "Fechar notas de atualização" }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Conteúdo rolável com todas as versões
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CampusReleaseNotes.versions.forEach { release ->
                        ReleaseVersionCard(
                            release = release,
                            highContrast = a11y.highContrastMode
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Rodapé fixo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = buttonMinHeight),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = "Entendi",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseVersionCard(
    release: ReleaseNoteEntry,
    highContrast: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (release.isLatest) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = if (release.isLatest || highContrast) 1.5.dp else 1.dp,
            color = if (release.isLatest) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (release.isLatest) {
                            Icons.Outlined.AutoAwesome
                        } else {
                            Icons.Outlined.History
                        },
                        contentDescription = null,
                        tint = if (release.isLatest) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = release.version,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (release.isLatest) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.semantics { heading() }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (release.isLatest) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = release.badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (release.isLatest) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = release.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                release.items.forEach { bullet ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = if (release.isLatest) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bullet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
