package br.ufop.campus.ui.disciplines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.DepartmentColorOption
import br.ufop.campus.core.ffi.UfopBuildingOption
import br.ufop.campus.ui.theme.parseHexColor

internal val LEGACY_DEPARTMENT_ALIASES = mapOf(
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

@Composable
internal fun DepartmentSelectorCard(
    selectedDepartment: DepartmentColorOption,
    totalDepartments: Int,
    accentColor: Color,
    accessibleAccent: Color,
    minTouchTarget: Dp,
    onOpenPicker: () -> Unit
) {
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
                text = "$totalDepartments departamentos disponíveis",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            onClick = onOpenPicker,
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
                            text = "Toque para pesquisar entre $totalDepartments departamentos",
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
}

@Composable
internal fun UfopDepartmentPickerDialog(
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

@Composable
internal fun UfopBuildingPickerDialog(
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
