package br.ufop.campus.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.ufop.campus.ui.theme.CampusCardRadius
import br.ufop.campus.ui.theme.CampusPillRadius
import br.ufop.campus.ui.theme.campusCardBorder
import br.ufop.campus.ui.theme.campusCardElevation

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
private val GitHubOctocatIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "GitHubOctocat",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 0.3f)
            curveTo(5.37f, 0.3f, 0f, 5.67f, 0f, 12.3f)
            curveTo(0f, 17.6f, 3.44f, 22.1f, 8.21f, 23.69f)
            curveTo(8.81f, 23.8f, 9.03f, 23.43f, 9.03f, 23.11f)
            curveTo(9.03f, 22.82f, 9.02f, 22.06f, 9.01f, 21.05f)
            curveTo(5.67f, 21.78f, 4.97f, 19.44f, 4.97f, 19.44f)
            curveTo(4.42f, 18.05f, 3.64f, 17.68f, 3.64f, 17.68f)
            curveTo(2.55f, 16.93f, 3.72f, 16.95f, 3.72f, 16.95f)
            curveTo(4.92f, 17.03f, 5.56f, 18.19f, 5.56f, 18.19f)
            curveTo(6.63f, 20.02f, 8.37f, 19.49f, 9.05f, 19.19f)
            curveTo(9.16f, 18.41f, 9.47f, 17.89f, 9.81f, 17.59f)
            curveTo(7.14f, 17.29f, 4.34f, 16.26f, 4.34f, 11.66f)
            curveTo(4.34f, 10.35f, 4.81f, 9.28f, 5.58f, 8.44f)
            curveTo(5.46f, 8.14f, 5.04f, 6.92f, 5.70f, 5.26f)
            curveTo(5.70f, 5.26f, 6.71f, 4.94f, 9.00f, 6.49f)
            curveTo(9.96f, 6.22f, 10.98f, 6.09f, 12f, 6.09f)
            curveTo(13.02f, 6.09f, 14.04f, 6.23f, 15.0f, 6.49f)
            curveTo(17.29f, 4.94f, 18.30f, 5.26f, 18.30f, 5.26f)
            curveTo(18.95f, 6.91f, 18.54f, 8.13f, 18.42f, 8.44f)
            curveTo(19.19f, 9.28f, 19.66f, 10.35f, 19.66f, 11.66f)
            curveTo(19.66f, 16.27f, 16.85f, 17.28f, 14.18f, 17.58f)
            curveTo(14.61f, 17.95f, 14.99f, 18.68f, 14.99f, 19.80f)
            curveTo(14.99f, 21.40f, 14.98f, 22.70f, 14.98f, 23.09f)
            curveTo(14.98f, 23.41f, 15.20f, 23.79f, 15.81f, 23.67f)
            curveTo(20.56f, 22.1f, 24f, 17.6f, 24f, 12.3f)
            curveTo(24f, 5.67f, 18.63f, 0.3f, 12f, 0.3f)
            close()
        }
    }.build()
}

@Composable
internal fun ProfileFaqSubView() {
    val context = LocalContext.current
    val minTouchTarget = campusMinTouchTarget()
    val expandedIds = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CampusCardRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = campusCardElevation(2.dp),
            border = campusCardBorder()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = GitHubOctocatIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "AVISO DE INDEPENDÊNCIA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                Text(
                    text = "Projeto estudantil independente e de código aberto",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Este é um aplicativo independente, desenvolvido por estudantes sem qualquer vínculo oficial, endosso ou afiliação com a Universidade Federal de Ouro Preto (UFOP). O código-fonte é aberto, auditável e focado em privacidade local, rapidez offline e acessibilidade.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/NuncaMorto/Vida-Academica")
                        )
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget),
                    shape = RoundedCornerShape(CampusPillRadius)
                ) {
                    Icon(
                        imageVector = GitHubOctocatIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Acessar repositório no GitHub",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
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
                shape = RoundedCornerShape(CampusCardRadius),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = campusCardBorder()
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
