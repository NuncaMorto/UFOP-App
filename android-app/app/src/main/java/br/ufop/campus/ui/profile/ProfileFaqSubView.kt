package br.ufop.campus.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class FaqAccordionItem(
    val id: String,
    val question: String,
    val answer: String
)
private val PROFILE_FAQ_ITEMS = listOf(
    FaqAccordionItem(
        id = "ufop_absences_25",
        question = "Como funciona o limite de 25% de faltas na UFOP?",
        answer = "Na UFOP, cada hora-aula tem 50 minutos e a aprovação exige no mínimo 75% de frequência (limite de 25% de faltas). Por exemplo, uma disciplina de 60h corresponde a 72 aulas de 50 min, resultando no limite de 18 faltas no semestre (30h = 36 aulas -> 9 faltas; 90h = 108 aulas -> 27 faltas). Aulas marcadas como canceladas não contam como falta."
    ),
    FaqAccordionItem(
        id = "android_widget",
        question = "Como adicionar o widget de horários na tela inicial do Android?",
        answer = "Mantenha pressionado um espaço vazio na tela inicial do seu Android, toque em \"Widgets\" e procure por \"Vida Acadêmica\". Arraste o widget de horários para a tela inicial para acompanhar suas próximas aulas do dia sem abrir o app — ele se adapta automaticamente ao seu tema, cor de destaque e mudanças na grade."
    ),
    FaqAccordionItem(
        id = "share_compare_schedule",
        question = "Como compartilhar minha grade e comparar horários com amigos?",
        answer = "Na aba Disciplinas, abra a Grade Horária e use a opção de compartilhar para enviar seu arquivo .ufopgrade, ou copie o código da grade em Perfil → Configurações → Armazenamento & Compartilhamento. Na Grade Horária, importe a grade de um amigo para comparar horários livres e aulas em comum."
    ),
    FaqAccordionItem(
        id = "official_calendar_2026_2027",
        question = "Como consultar o calendário acadêmico de 2026.2 e 2027.1?",
        answer = "Acesse Perfil → Configurações → Calendário Acadêmico. No topo da tela, alterne entre os semestres 2026.2, 2027.1 e 2027.2 (com referência às datas públicas divulgadas para o calendário acadêmico) e use os filtros de categoria ou a busca para encontrar qualquer marco."
    ),
    FaqAccordionItem(
        id = "security_pdfs",
        question = "Como funciona a segurança e a exclusão imediata de PDFs?",
        answer = "Todos os seus dados acadêmicos (disciplinas, faltas, notas, histórico e agenda) ficam salvos apenas no seu aparelho e protegidos com criptografia ligada à segurança do próprio Android. Ao importar Atestado de Matrícula, Histórico Escolar, Planos de Ensino ou Calendário em PDF, o aplicativo lê as informações na hora e apaga imediatamente qualquer arquivo temporário usado na leitura."
    ),
    FaqAccordionItem(
        id = "app_lock",
        question = "Como ativar o bloqueio por senha e digital?",
        answer = "Acesse Perfil → Configurações → Segurança & Privacidade e toque em \"Ativar bloqueio por senha\". Defina uma senha com pelo menos 4 caracteres e, se o seu celular possuir biometria cadastrada, habilite o desbloqueio por digital. Após 5 tentativas incorretas de senha, o app aplica um intervalo de proteção progressivo."
    ),
    FaqAccordionItem(
        id = "ead_syllabus",
        question = "Como funcionam os planos de ensino e disciplinas EAD?",
        answer = "Na aba Disciplinas, ao tocar em uma matéria você pode anexar o Plano de Ensino em PDF para extrair cronograma e avaliações automaticamente. Turmas identificadas como EAD ou remotas exibem a indicação EAD e não exibem textos genéricos ou separadores vazios quando não houver sala física ou docente informado."
    ),
    FaqAccordionItem(
        id = "personalization_a11y",
        question = "Onde ficam Personalização e Acessibilidade?",
        answer = "Ambas ficam dentro de Perfil → Configurações → Aparência, Segurança & Acessibilidade. Em Personalização você escolhe Modo Claro/Escuro e uma das 9 cores de destaque (incluindo rosa e azul). Em Acessibilidade há perfil rápido para TalkBack, escala de fonte até 145%, alto contraste, texto em negrito, redução de animações e alvos de toque de 56dp."
    ),
    FaqAccordionItem(
        id = "encrypted_backup_logout",
        question = "Como funciona o backup protegido ao sair da conta?",
        answer = "Ao tocar em \"Sair da conta\" na Central do Perfil, o app oferece primeiro a criação de um arquivo de backup (.ufopbackup) protegido por uma senha escolhida por você. Depois, um segundo aviso confirma se você deseja apagar os dados deste aparelho e fechar o app. Você pode restaurar seu backup a qualquer momento em Configurações → Segurança & Privacidade."
    ),
    FaqAccordionItem(
        id = "legacy_curriculum",
        question = "Entrei na UFOP antes de 2026/1: como fica minha matriz?",
        answer = "O catálogo integrado traz a matriz vigente da UFOP (2026/2). Quem entrou na UFOP antes de 2026/1 pode ter equivalências ou cargas horárias específicas — o app sinaliza essa condição na Matriz Curricular e permite importar seu próprio Histórico Escolar (PDF) para calcular o CRE e acompanhar horas cursadas com precisão."
    )
)
@Composable
internal fun ProfileFaqSubView() {
    val minTouchTarget = campusMinTouchTarget()
    val expandedIds = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "AVISO DE INDEPENDÊNCIA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Projeto estudantil sem vínculo institucional",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Este é um aplicativo independente, desenvolvido por estudantes e sem qualquer vínculo oficial, endosso ou afiliação com a Universidade Federal de Ouro Preto (UFOP).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "PERGUNTAS FREQUENTES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .semantics { heading() }
        )

        PROFILE_FAQ_ITEMS.forEach { item ->
            val isExpanded = expandedIds[item.id] == true
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget)
                        .clickable { expandedIds[item.id] = !isExpanded }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = item.question,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isExpanded) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = if (isExpanded) "Recolher resposta" else "Expandir resposta",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    CampusAnimatedVisibility(visible = isExpanded) {
                        Text(
                            text = item.answer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
