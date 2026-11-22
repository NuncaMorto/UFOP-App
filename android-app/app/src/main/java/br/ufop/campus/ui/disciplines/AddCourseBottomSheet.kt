package br.ufop.campus.ui.disciplines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.DepartmentColorOption
import br.ufop.campus.core.ffi.DisciplineSummaryCard
import br.ufop.campus.core.ffi.UfopBuildingOption
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.accessibleTextColor
import br.ufop.campus.ui.theme.parseHexColor

internal data class ScheduleSlotDraft(
    val dayOfWeek: Int = 1,
    val startTime: String = "08:00",
    val endTime: String = "09:40",
    val building: String = "ICEB (III)",
    val room: String = "Sala 204"
) {
    val shortLocationPreview: String
        get() = buildShortLocationPreview(building, room)
}

internal val WEEK_DAY_OPTIONS = listOf(
    1 to "SEG",
    2 to "TER",
    3 to "QUA",
    4 to "QUI",
    5 to "SEX",
    6 to "SÁB"
)

internal val TIME_SLOT_PRESETS = listOf(
    "07:30" to "09:10",
    "08:00" to "09:40",
    "09:30" to "11:10",
    "10:00" to "11:40",
    "13:30" to "15:10",
    "15:20" to "17:00",
    "17:10" to "18:50",
    "19:00" to "20:40",
    "20:50" to "22:40"
)

private val WORKLOAD_OPTIONS = listOf(30, 45, 60, 72, 90)

private val LEGACY_DEPARTMENT_ALIASES = mapOf(
    "math" to "demat",
    "comp" to "decom",
    "cat" to "decat",
    "phys" to "defis",
    "em" to "demin"
)

internal val DEFAULT_48_UFOP_DEPARTMENTS = listOf(
    DepartmentColorOption("deabi", "DEABI · Estudos Africanos, Afro-Brasileiros e Indígenas", "#C2410C"),
    DepartmentColorOption("deacl", "DEACL · Análises Clínicas", "#0284C7"),
    DepartmentColorOption("deali", "DEALI · Alimentos", "#16A34A"),
    DepartmentColorOption("deamb", "DEAMB · Eng. Ambiental", "#7C3AED"),
    DepartmentColorOption("dearq", "DEARQ · Arquitetura e Urbanismo", "#DB2777"),
    DepartmentColorOption("deart", "DEART · Artes Cênicas", "#D97706"),
    DepartmentColorOption("debio", "DEBIO · Biodiversidade, Evolução e Meio Ambiente", "#14B8A6"),
    DepartmentColorOption("decad", "DECAD · Ciências Administrativas", "#4338CA"),
    DepartmentColorOption("decat", "DECAT · Eng. de Controle e Automação", "#2E7D32"),
    DepartmentColorOption("decbi", "DECBI · Ciências Biológicas", "#E11D48"),
    DepartmentColorOption("decea", "DECEA · Ciências Exatas e Aplicadas", "#2563EB"),
    DepartmentColorOption("decgp", "DECGP · Cirurgia, Ginecologia e Obstetrícia", "#9333EA"),
    DepartmentColorOption("deciv", "DECIV · Eng. Civil", "#B45309"),
    DepartmentColorOption("decom", "DECOM · Ciência da Computação", "#6E56CF"),
    DepartmentColorOption("decpa", "DECPA · Clínicas Pediátrica e do Adulto", "#0891B2"),
    DepartmentColorOption("decsi", "DECSI · Computação e Sistemas", "#047857"),
    DepartmentColorOption("decso", "DECSO · Ciências Sociais", "#DC2626"),
    DepartmentColorOption("dedir", "DEDIR · Direito", "#1D4ED8"),
    DepartmentColorOption("deeco", "DEECO · Ciências Econômicas", "#CA8A04"),
    DepartmentColorOption("deedu", "DEEDU · Educação", "#BE185D"),
    DepartmentColorOption("deefd", "DEEFD · Educação Física", "#15803D"),
    DepartmentColorOption("deelt", "DEELT · Eng. Elétrica", "#EA580C"),
    DepartmentColorOption("deema", "DEEMA · Educação Matemática", "#4F46E5"),
    DepartmentColorOption("deenp", "DEENP · Eng. de Produção (ICEA)", "#0F766E"),
    DepartmentColorOption("deest", "DEEST · Estatística", "#8B5CF6"),
    DepartmentColorOption("deete", "DEETE · Educação e Tecnologias", "#0369A1"),
    DepartmentColorOption("defar", "DEFAR · Farmácia", "#65A30D"),
    DepartmentColorOption("defil", "DEFIL · Filosofia", "#A21CAF"),
    DepartmentColorOption("defis", "DEFIS · Física", "#8D5B16"),
    DepartmentColorOption("degeo", "DEGEO · Geologia", "#9F1239"),
    DepartmentColorOption("degep", "DEGEP · Gestão Pública", "#06B6D4"),
    DepartmentColorOption("dehis", "DEHIS · História", "#A16207"),
    DepartmentColorOption("deish", "DEISH · Saúde e Humanidades", "#1E3A8A"),
    DepartmentColorOption("dejor", "DEJOR · Jornalismo", "#F97316"),
    DepartmentColorOption("delet", "DELET · Letras", "#6D28D9"),
    DepartmentColorOption("demat", "DEMAT · Matemática", "#3B68FF"),
    DepartmentColorOption("demec", "DEMEC · Eng. Mecânica", "#475569"),
    DepartmentColorOption("demet", "DEMET · Eng. Metalúrgica e de Materiais", "#B91C1C"),
    DepartmentColorOption("demin", "DEMIN · Eng. de Minas", "#00897B"),
    DepartmentColorOption("demsc", "DEMSC · Medicina de Família e Saúde Coletiva", "#C026D3"),
    DepartmentColorOption("demul", "DEMUL · Museologia", "#7C2D12"),
    DepartmentColorOption("demus", "DEMUS · Música", "#38BDF8"),
    DepartmentColorOption("dencs", "DENCS · Nutrição Clínica e Social", "#4D7C0F"),
    DepartmentColorOption("depro", "DEPRO · Eng. de Produção", "#5B21B6"),
    DepartmentColorOption("dequi", "DEQUI · Química", "#EC4899"),
    DepartmentColorOption("desso", "DESSO · Serviço Social", "#006064"),
    DepartmentColorOption("detur", "DETUR · Turismo", "#F59E0B"),
    DepartmentColorOption("deurb", "DEURB · Eng. Urbana", "#166534")
)

internal val DEFAULT_UFOP_BUILDINGS = listOf(
    UfopBuildingOption("op_iceb_1", "op_cruzeiro", "Ouro Preto", "ICEB (I)", "Instituto de Ciências Exatas e Biológicas I"),
    UfopBuildingOption("op_iceb_2", "op_cruzeiro", "Ouro Preto", "ICEB (II)", "Instituto de Ciências Exatas e Biológicas II"),
    UfopBuildingOption("op_iceb_3", "op_cruzeiro", "Ouro Preto", "ICEB (III)", "Instituto de Ciências Exatas e Biológicas III"),
    UfopBuildingOption("op_escola_minas", "op_cruzeiro", "Ouro Preto", "Escola de Minas", "Escola de Minas (Prédio Principal)"),
    UfopBuildingOption("op_em_predio_14", "op_cruzeiro", "Ouro Preto", "Escola de Minas · Prédio 14", "Escola de Minas · Prédio 14 (DEPRO)"),
    UfopBuildingOption("op_degeo", "op_cruzeiro", "Ouro Preto", "DEGEO", "Departamento de Geologia (Mirante)"),
    UfopBuildingOption("op_emed", "op_cruzeiro", "Ouro Preto", "EMED", "Escola de Medicina"),
    UfopBuildingOption("op_efar", "op_cruzeiro", "Ouro Preto", "EFAR", "Escola de Farmácia"),
    UfopBuildingOption("op_edtm", "op_cruzeiro", "Ouro Preto", "EDTM", "Escola de Direito, Turismo e Museologia"),
    UfopBuildingOption("op_enut", "op_cruzeiro", "Ouro Preto", "ENUT", "Escola de Nutrição"),
    UfopBuildingOption("op_deart", "op_cruzeiro", "Ouro Preto", "DEART", "Departamento de Artes Cênicas"),
    UfopBuildingOption("op_blocos_salas", "op_cruzeiro", "Ouro Preto", "Blocos de Salas de Aula", "Blocos de Salas de Aula (Morro do Cruzeiro)"),
    UfopBuildingOption("op_cedufop", "op_cruzeiro", "Ouro Preto", "CEDUFOP", "Centro Desportivo (CEDUFOP)"),
    UfopBuildingOption("mar_ichs", "mar_ichs", "Mariana", "ICHS", "Instituto de Ciências Humanas e Sociais (Rua do Seminário)"),
    UfopBuildingOption("mar_icsa", "mar_ichs", "Mariana", "ICSA", "Instituto de Ciências Sociais Aplicadas (Rua do Catete, 166)"),
    UfopBuildingOption("jm_icea_c", "jm_icea", "João Monlevade", "ICEA · Bloco C", "ICEA · Bloco C (João Monlevade)"),
    UfopBuildingOption("jm_icea_d", "jm_icea", "João Monlevade", "ICEA · Bloco D", "ICEA · Bloco D (João Monlevade)"),
    UfopBuildingOption("jm_icea_e", "jm_icea", "João Monlevade", "ICEA · Bloco E", "ICEA · Bloco E (João Monlevade)"),
    UfopBuildingOption("jm_icea_h", "jm_icea", "João Monlevade", "ICEA · Bloco H", "ICEA · Bloco H (João Monlevade)"),
    UfopBuildingOption("ufop_ead", "ead", "EAD", "Ensino a Distância (EAD)", "Ensino a Distância (AVA / Moodle / Remoto)")
)

internal fun isEadBuildingLabel(building: String): Boolean {
    val norm = building.trim()
    return norm.equals("EAD", ignoreCase = true) ||
        norm.equals("E.A.D.", ignoreCase = true) ||
        norm.equals("CEAD", ignoreCase = true) ||
        norm.contains("Ensino a Distância", ignoreCase = true) ||
        norm.contains("Ensino a Distancia", ignoreCase = true) ||
        norm.contains("(EAD)", ignoreCase = true)
}

internal fun buildShortLocationPreview(building: String, room: String): String {
    if (isEadBuildingLabel(building)) {
        return "EAD"
    }
    val shortB = building.trim()
        .removePrefix("Prédio ")
        .removePrefix("Bloco ")
        .trim()
    val shortR = room.trim()
        .removePrefix("Sala ")
        .replace("Laboratório ", "Lab ")
        .trim()
    return when {
        shortB.isNotEmpty() && shortR.isNotEmpty() -> "$shortB · $shortR"
        shortB.isNotEmpty() -> shortB
        shortR.isNotEmpty() -> shortR
        else -> "UFOP"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddCourseBottomSheet(
    semester: String,
    departments: List<DepartmentColorOption>,
    initialDepartmentId: String,
    onDismiss: () -> Unit,
    onSaveCourse: (
        departmentId: String,
        name: String,
        professor: String,
        workloadHours: Int,
        colorOverrideHex: String?,
        initialSlot: InitialCourseSlotInput?
    ) -> Result<DisciplineSummaryCard>
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp

    val resolvedDepartments = remember(departments) {
        departments
            .ifEmpty { DisciplinesViewModel.sharedRepository?.listDepartments().orEmpty() }
            .ifEmpty { DEFAULT_48_UFOP_DEPARTMENTS }
    }

    val normalizedInitialDeptId = remember(initialDepartmentId, resolvedDepartments) {
        val clean = initialDepartmentId.trim().lowercase()
        val mapped = LEGACY_DEPARTMENT_ALIASES[clean] ?: clean
        resolvedDepartments.find { it.id.equals(mapped, ignoreCase = true) }?.id
            ?: resolvedDepartments.find { it.id == "decom" }?.id
            ?: resolvedDepartments.first().id
    }

    var name by remember { mutableStateOf("") }
    var professor by remember { mutableStateOf("") }
    var selectedDepartmentId by remember { mutableStateOf(normalizedInitialDeptId) }
    var showDepartmentPickerDialog by remember { mutableStateOf(false) }
    var selectedWorkloadHours by remember { mutableIntStateOf(60) }
    var isCustomWorkload by remember { mutableStateOf(false) }
    var customWorkloadText by remember { mutableStateOf("60") }
    var includeInitialSlot by remember { mutableStateOf(false) }
    var slotDraft by remember { mutableStateOf(ScheduleSlotDraft()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val selectedDepartment = resolvedDepartments.find { it.id == selectedDepartmentId }
        ?: resolvedDepartments.first()
    val accentColor = parseHexColor(selectedDepartment.colorHex)
    val accessibleAccent = remember(accentColor, isDark) {
        accessibleTextColor(accentColor, isDark)
    }
    val parsedCustomHours = customWorkloadText.toIntOrNull()
    val isCustomWorkloadValid = !isCustomWorkload || (parsedCustomHours != null && parsedCustomHours in 15..360)
    val effectiveWorkloadHours = if (isCustomWorkload) {
        (parsedCustomHours ?: selectedWorkloadHours).coerceIn(15, 360)
    } else {
        selectedWorkloadHours
    }
    val previewMaxAbsences = (effectiveWorkloadHours * 3) / 10
    val previewTotalClasses = ((effectiveWorkloadHours * 60) / 50).coerceAtLeast(1)

    if (showDepartmentPickerDialog) {
        UfopDepartmentPickerDialog(
            departments = resolvedDepartments,
            selectedDepartmentId = selectedDepartmentId,
            onSelectDepartment = { chosenId ->
                selectedDepartmentId = chosenId
                errorMessage = null
                showDepartmentPickerDialog = false
            },
            onDismiss = { showDepartmentPickerDialog = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SEMESTRE $semester",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Nova disciplina",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(minTouchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar cadastro de disciplina",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Nome e Professor
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome") },
                placeholder = { Text("Ex.: Engenharia de Software I") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            OutlinedTextField(
                value = professor,
                onValueChange = {
                    professor = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Professor(a)") },
                placeholder = { Text("Opcional · deixe em branco para preencher depois") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            // Seletor Material 3 Expressive para os 48 Departamentos Oficiais da UFOP
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Departamento",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${resolvedDepartments.size} departamentos disponíveis",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    onClick = { showDepartmentPickerDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = minTouchTarget),
                    shape = RoundedCornerShape(18.dp),
                    color = accentColor.copy(alpha = 0.10f),
                    border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedDepartment.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Toque para pesquisar entre ${resolvedDepartments.size} departamentos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = accentColor.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Trocar",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accessibleAccent
                                )
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Selecionar departamento",
                                    tint = accessibleAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Carga horária + Prévia em tempo real do limite de faltas (30h, 45h, 60h, 72h, 90h + Outra)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Carga horária",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WORKLOAD_OPTIONS.forEach { hours ->
                        val isSelected = !isCustomWorkload && selectedWorkloadHours == hours
                        val limit = (hours * 3) / 10
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                isCustomWorkload = false
                                selectedWorkloadHours = hours
                            },
                            modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                            label = {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${hours}h",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = "$limit faltas",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.16f),
                                selectedLabelColor = accessibleAccent
                            )
                        )
                    }

                    FilterChip(
                        selected = isCustomWorkload,
                        onClick = {
                            isCustomWorkload = true
                            if (customWorkloadText.isBlank()) {
                                customWorkloadText = selectedWorkloadHours.toString()
                            }
                        },
                        modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                        label = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Outra",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "15–360h",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.16f),
                            selectedLabelColor = accessibleAccent
                        )
                    )
                }

                if (isCustomWorkload) {
                    val customInvalid = parsedCustomHours == null || parsedCustomHours !in 15..360
                    OutlinedTextField(
                        value = customWorkloadText,
                        onValueChange = { raw ->
                            customWorkloadText = raw.filter { it.isDigit() }.take(3)
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Carga horária personalizada (horas)") },
                        placeholder = { Text("Ex.: 75") },
                        isError = customInvalid,
                        supportingText = {
                            Text(
                                text = if (customInvalid) {
                                    "Informe um valor inteiro entre 15h e 360h."
                                } else {
                                    "Limite automático: $previewMaxAbsences faltas (25%)"
                                }
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                val dept = selectedDepartment
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = accentColor.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Limite automático: $previewMaxAbsences faltas (25%)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = accessibleAccent
                        )
                        Text(
                            text = "$effectiveWorkloadHours h ($previewTotalClasses aulas de 50 min) · ${dept.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Horário semanal inicial (opcional)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Horário semanal inicial",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Opcional · valida conflitos na grade",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = includeInitialSlot,
                            onCheckedChange = { enabled ->
                                includeInitialSlot = enabled
                                errorMessage = null
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = accentColor,
                                checkedBorderColor = accentColor,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    if (includeInitialSlot) {
                        ScheduleSlotEditorFields(
                            draft = slotDraft,
                            accentColor = accentColor,
                            onDraftChange = { updated ->
                                slotDraft = updated
                                errorMessage = null
                            }
                        )
                    }
                }
            }

            if (errorMessage != null) {
                InlineErrorBanner(message = errorMessage.orEmpty())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Cancelar")
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        val initialSlot = if (includeInitialSlot) {
                            val cleanBuilding = slotDraft.building.trim().ifBlank { "ICEB (III)" }
                            val cleanRoom = slotDraft.room.trim().ifBlank {
                                if (isEadBuildingLabel(cleanBuilding)) "Online" else "Sala 101"
                            }
                            InitialCourseSlotInput(
                                dayOfWeek = slotDraft.dayOfWeek,
                                startTime = slotDraft.startTime.trim().ifBlank { "08:00" },
                                endTime = slotDraft.endTime.trim().ifBlank { "09:40" },
                                building = cleanBuilding,
                                room = cleanRoom,
                                shortLocation = slotDraft.shortLocationPreview
                            )
                        } else {
                            null
                        }

                        val result = onSaveCourse(
                            selectedDepartmentId,
                            name.trim(),
                            professor.trim(),
                            effectiveWorkloadHours,
                            null,
                            initialSlot
                        )
                        result.onFailure { throwable ->
                            errorMessage = throwable.message
                                ?: "Não foi possível criar a disciplina."
                        }
                    },
                    enabled = name.trim().isNotEmpty() && isCustomWorkloadValid,
                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salvar disciplina")
                }
            }
        }
    }
}

@Composable
private fun UfopDepartmentPickerDialog(
    departments: List<DepartmentColorOption>,
    selectedDepartmentId: String,
    onSelectDepartment: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredDepartments = remember(departments, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            departments
        } else {
            departments.filter { dept ->
                dept.name.contains(q, ignoreCase = true) ||
                    dept.id.contains(q, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Departamento",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${filteredDepartments.size} de ${departments.size} departamentos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar sigla ou nome (ex.: DECAT, DECOM, DEMAT)...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Pesquisar departamento",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpar pesquisa",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                if (filteredDepartments.isEmpty()) {
                    Text(
                        text = "Nenhum departamento encontrado para \"$searchQuery\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = filteredDepartments,
                            key = { it.id }
                        ) { dept ->
                            val isSelected = dept.id == selectedDepartmentId
                            val deptColor = parseHexColor(dept.colorHex)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectDepartment(dept.id) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    deptColor.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) {
                                        deptColor.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(deptColor)
                                        )
                                        Text(
                                            text = dept.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) deptColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selecionado",
                                            tint = deptColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Fechar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleSlotEditorFields(
    draft: ScheduleSlotDraft,
    accentColor: Color,
    onDraftChange: (ScheduleSlotDraft) -> Unit,
    ufopBuildings: List<UfopBuildingOption>? = null
) {
    val resolvedBuildings = remember(ufopBuildings) {
        val base = (ufopBuildings ?: DisciplinesViewModel.sharedRepository?.listUfopBuildings())
            .orEmpty()
            .ifEmpty { DEFAULT_UFOP_BUILDINGS }
        if (base.any { isEadBuildingLabel(it.shortName) }) {
            base
        } else {
            base + UfopBuildingOption(
                id = "ufop_ead",
                campusId = "ead",
                campusName = "EAD",
                shortName = "Ensino a Distância (EAD)",
                fullName = "Ensino a Distância (AVA / Moodle / Remoto)"
            )
        }
    }

    val campusOptions = listOf("Todos", "Ouro Preto", "Mariana", "João Monlevade", "EAD")
    var selectedCampusFilter by remember { mutableStateOf("Todos") }
    var showBuildingPickerDialog by remember { mutableStateOf(false) }

    val applySelectedBuilding: (String) -> Unit = { selectedShortName ->
        val nextRoom = when {
            isEadBuildingLabel(selectedShortName) &&
                (draft.room.isBlank() || draft.room == "Sala 204") -> "Online"
            !isEadBuildingLabel(selectedShortName) && draft.room == "Online" -> "Sala 204"
            else -> draft.room
        }
        onDraftChange(draft.copy(building = selectedShortName, room = nextRoom))
    }

    val filteredBuildings = remember(resolvedBuildings, selectedCampusFilter) {
        if (selectedCampusFilter == "Todos") {
            resolvedBuildings
        } else {
            resolvedBuildings.filter { it.campusName.equals(selectedCampusFilter, ignoreCase = true) }
        }
    }

    val matchedBuilding = remember(resolvedBuildings, draft.building) {
        resolvedBuildings.find {
            it.shortName.equals(draft.building.trim(), ignoreCase = true) ||
                it.fullName.equals(draft.building.trim(), ignoreCase = true)
        }
    }

    if (showBuildingPickerDialog) {
        UfopBuildingPickerDialog(
            buildings = resolvedBuildings,
            selectedBuildingShortName = draft.building,
            accentColor = accentColor,
            onSelectBuilding = { chosen ->
                applySelectedBuilding(chosen.shortName)
                showBuildingPickerDialog = false
            },
            onDismiss = { showBuildingPickerDialog = false }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Dia da semana",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WEEK_DAY_OPTIONS.forEach { (dayNum, label) ->
                val isSelected = draft.dayOfWeek == dayNum
                FilterChip(
                    selected = isSelected,
                    onClick = { onDraftChange(draft.copy(dayOfWeek = dayNum)) },
                    label = { Text(label) },
                    shape = RoundedCornerShape(22.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.18f),
                        selectedLabelColor = accentColor
                    )
                )
            }
        }

        Text(
            text = "Horário",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TIME_SLOT_PRESETS.forEach { (start, end) ->
                val isSelected = draft.startTime == start && draft.endTime == end
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onDraftChange(draft.copy(startTime = start, endTime = end))
                    },
                    label = { Text("$start–$end") },
                    shape = RoundedCornerShape(22.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.18f),
                        selectedLabelColor = accentColor
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = draft.startTime,
                onValueChange = { onDraftChange(draft.copy(startTime = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("Início") },
                placeholder = { Text("08:00") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = draft.endTime,
                onValueChange = { onDraftChange(draft.copy(endTime = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("Término") },
                placeholder = { Text("09:40") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }

        // Seletor Expressivo de Prédios Oficiais da UFOP
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Prédio / Unidade",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = matchedBuilding?.let { "${it.campusName} · ${it.fullName}" }
                        ?: "Ouro Preto · Mariana · João Monlevade",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Botão Seletor Expressivo que abre o catálogo completo agrupado por campus
            Surface(
                onClick = { showBuildingPickerDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = matchedBuilding?.shortName ?: draft.building.ifBlank { "Selecionar prédio ou local" },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = matchedBuilding?.let { "${it.fullName} (${it.campusName})" }
                                    ?: "Toque para escolher prédio ou unidade",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Escolher prédio",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Filtro rápido por campus + Chips expressivos dos prédios oficiais UFOP
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                campusOptions.forEach { campusLabel ->
                    val isCampusSelected = selectedCampusFilter == campusLabel
                    FilterChip(
                        selected = isCampusSelected,
                        onClick = { selectedCampusFilter = campusLabel },
                        label = {
                            Text(
                                text = campusLabel,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        shape = RoundedCornerShape(22.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filteredBuildings.forEach { buildingOption ->
                    val isSelected = draft.building.trim().equals(buildingOption.shortName, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            applySelectedBuilding(buildingOption.shortName)
                        },
                        label = { Text(buildingOption.shortName) },
                        shape = RoundedCornerShape(22.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.18f),
                            selectedLabelColor = accentColor
                        )
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = draft.building,
                onValueChange = { onDraftChange(draft.copy(building = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("Prédio") },
                placeholder = { Text("ICEB (III)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = draft.room,
                onValueChange = { onDraftChange(draft.copy(room = it)) },
                modifier = Modifier.weight(1f),
                label = { Text(if (isEadBuildingLabel(draft.building)) "Ambiente" else "Sala") },
                placeholder = { Text(if (isEadBuildingLabel(draft.building)) "AVA / Moodle" else "Sala 204") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }

        Text(
            text = "Exibição na grade: ${draft.shortLocationPreview}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UfopBuildingPickerDialog(
    buildings: List<UfopBuildingOption>,
    selectedBuildingShortName: String,
    accentColor: Color,
    onSelectBuilding: (UfopBuildingOption) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(buildings, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            buildings
        } else {
            buildings.filter {
                it.shortName.contains(q, ignoreCase = true) ||
                    it.fullName.contains(q, ignoreCase = true) ||
                    it.campusName.contains(q, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Prédios e Unidades",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ouro Preto · Mariana · João Monlevade",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar prédio (ex.: ICEB, Escola de Minas, ICEA)...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Buscar prédio",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = filtered,
                        key = { it.id }
                    ) { b ->
                        val isSelected = b.shortName.equals(selectedBuildingShortName.trim(), ignoreCase = true)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectBuilding(b) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                accentColor.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) {
                                    accentColor.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${b.shortName} · ${b.campusName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = b.fullName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selecionado",
                                        tint = accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Fechar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
internal fun InlineErrorBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = StatusAbsentRed.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, StatusAbsentRed.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = "Erro de horário",
                tint = StatusAbsentRed,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = StatusAbsentRed
            )
        }
    }
}
