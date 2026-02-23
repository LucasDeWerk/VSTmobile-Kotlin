package com.example.vstmobile.ui.screens.sales

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.vstmobile.navigation.Screen
import com.example.vstmobile.services.*
import com.example.vstmobile.ui.components.ScreenWithSidebar
import com.example.vstmobile.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ── Cores ─────────────────────────────────────────────────────────────────────
private val AvpNavy  = Color(0xFF1E3A8A)
private val AvpBlue  = Color(0xFF3B82F6)
private val AvpSlate = Color(0xFF64748B)
private val AvpLight = Color(0xFFF1F5F9)
private val AvpDark  = Color(0xFF1E293B)
private val AvpGreen = Color(0xFF10B981)
private val AvpRed   = Color(0xFFEF4444)

// ── Opções estáticas ──────────────────────────────────────────────────────────
private val opcoesCusto = listOf(
    "AQUISICAO" to "Aquisição",
    "MEDIO"     to "Médio",
    "COMPRA"    to "Compra"
)
private val opcoesTipo = listOf(
    "PRODUTO"   to "Produto",
    "SERVICO"   to "Serviço",
    "AMBOS"     to "Ambos"
)
private val opcoesSituacao = listOf(
    "TODAS"     to "Todas",
    "ABERTAS"   to "Abertas",
    "FECHADAS"  to "Fechadas"
)
private val opcoesPeso = listOf(
    "S" to "Sim",
    "N" to "Não"
)

private fun mapCusto(v: String) = mapOf("AQUISICAO" to "0", "MEDIO" to "1", "COMPRA" to "2")[v] ?: ""
private fun mapTipo(v: String)  = mapOf("PRODUTO" to "1", "SERVICO" to "2", "AMBOS" to "0")[v] ?: ""
private fun mapSit(v: String)   = mapOf("TODAS" to "0", "ABERTAS" to "1", "FECHADAS" to "2")[v] ?: ""

private fun formatApiDate(date: Date): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
private fun formatDisplayDate(date: Date): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)

// ── Tela ──────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnaliseVendaProdutoScreen(navController: NavHostController) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val scope   = rememberCoroutineScope()

    // Filtrros
    var grupoProduto    by remember { mutableStateOf("") }
    var grupoId         by remember { mutableStateOf<Int?>(null) }
    var subGrupoProduto by remember { mutableStateOf("") }
    var marca           by remember { mutableStateOf("") }
    var classe          by remember { mutableStateOf("") }
    var vendedor        by remember { mutableStateOf("") }
    var custo           by remember { mutableStateOf("") }
    var tipo            by remember { mutableStateOf("") }
    var situacao        by remember { mutableStateOf("") }
    var mostrarPeso     by remember { mutableStateOf("") }
    var dataInicio      by remember { mutableStateOf(Date()) }
    var dataFim         by remember { mutableStateOf(Date()) }

    // Dados das APIs
    var grupos          by remember { mutableStateOf<List<GrupoProduto>>(emptyList()) }
    var subGrupos       by remember { mutableStateOf<List<SubGrupoProduto>>(emptyList()) }
    var marcas          by remember { mutableStateOf<List<MarcaProduto>>(emptyList()) }
    var classes         by remember { mutableStateOf<List<ClasseProduto>>(emptyList()) }
    var vendedores      by remember { mutableStateOf<List<Vendedor>>(emptyList()) }

    // Loading
    var loadingGrupos   by remember { mutableStateOf(false) }
    var loadingSubGrupos by remember { mutableStateOf(false) }
    var loadingMarcas   by remember { mutableStateOf(false) }
    var loadingClasses  by remember { mutableStateOf(false) }
    var loadingVendedores by remember { mutableStateOf(false) }
    var loadingPdf      by remember { mutableStateOf(false) }
    var pdfError        by remember { mutableStateOf("") }
    var pdfSuccess      by remember { mutableStateOf(false) }

    // Modais
    var showGrupo       by remember { mutableStateOf(false) }
    var showSubGrupo    by remember { mutableStateOf(false) }
    var showMarca       by remember { mutableStateOf(false) }
    var showClasse      by remember { mutableStateOf(false) }
    var showVendedor    by remember { mutableStateOf(false) }
    var showCusto       by remember { mutableStateOf(false) }
    var showTipo        by remember { mutableStateOf(false) }
    var showSituacao    by remember { mutableStateOf(false) }
    var showPeso        by remember { mutableStateOf(false) }
    var showFiliais     by remember { mutableStateOf(false) }
    var showDateIni     by remember { mutableStateOf(false) }
    var showDateFim     by remember { mutableStateOf(false) }

    val filial          = FilialState.selectedFilial
    val filiais         = FilialState.filiais        // recompõe porque é mutableStateOf
    val selectedFiliais = FilialState.selectedFiliais // idem

    // Carregar dados ao abrir / trocar filial
    LaunchedEffect(filial?.idEmpresa) {
        if (filial == null || session.userToken.isEmpty()) return@LaunchedEffect
        val svc = AnaliseVendaProdutoService(session.userToken)
        val emp = filial.idEmpresa.toString()
        loadingGrupos = true; loadingMarcas = true; loadingClasses = true; loadingVendedores = true
        launch { svc.fetchGruposProduto(emp).data?.let  { grupos    = it }; loadingGrupos    = false }
        launch { svc.fetchMarcas(emp).data?.let         { marcas    = it }; loadingMarcas    = false }
        launch { svc.fetchClasses(emp).data?.let        { classes   = it }; loadingClasses   = false }
        launch { svc.fetchVendedores(emp).data?.let     { vendedores = it}; loadingVendedores = false }
    }

    // Carregar sub-grupos ao trocar grupo
    LaunchedEffect(grupoId) {
        val gId = grupoId ?: return@LaunchedEffect
        if (filial == null || session.userToken.isEmpty()) return@LaunchedEffect
        val svc = AnaliseVendaProdutoService(session.userToken)
        loadingSubGrupos = true
        svc.fetchSubGruposProduto(filial.idEmpresa.toString(), gId).data?.let { subGrupos = it }
        loadingSubGrupos = false
    }

    ScreenWithSidebar(
        navController = navController,
        title = "Análise por Produto",
        currentRoute = Screen.AnaliseVendaProduto.route
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AvpLight)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {

            if (filial == null) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("📊", fontSize = 48.sp)
                        Text("Selecione uma filial", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AvpDark))
                        Text("Abra o menu lateral e selecione uma filial.", style = TextStyle(fontSize = 14.sp, color = AvpSlate), textAlign = TextAlign.Center)
                    }
                }
                return@ScreenWithSidebar
            }

            // ── Card de Filtros ───────────────────────────────────────────────
            AvpCard(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                Text("Análise de Venda por Produto",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AvpDark))
                Spacer(modifier = Modifier.height(16.dp))

                // Filiais
                AvpFilterRow(
                    label = "Filiais",
                    value = when {
                        selectedFiliais.isEmpty() -> ""
                        selectedFiliais.size == 1 -> {
                            val f = selectedFiliais.first()
                            if (f.identificacaoInterna.isNotEmpty()) "${f.identificacaoInterna} | ID: ${f.idFilial}"
                            else "ID: ${f.idFilial}"
                        }
                        else -> {
                            val f = selectedFiliais.first()
                            val first = if (f.identificacaoInterna.isNotEmpty()) f.identificacaoInterna else "ID: ${f.idFilial}"
                            "$first e mais ${selectedFiliais.size - 1}"
                        }
                    },
                    placeholder = "Selecionar filiais",
                    onClick = { showFiliais = true }
                )

                // Grupo de Produtos
                AvpFilterRow(
                    label = "Grupo de Produtos",
                    value = grupos.find { it.id.toString() == grupoProduto }?.descGrupo ?: grupoProduto,
                    placeholder = "Selecionar grupo de produtos",
                    loading = loadingGrupos,
                    onClick = { showGrupo = true }
                )

                // Sub-Grupo
                AvpFilterRow(
                    label = "Sub-Grupo de Produtos",
                    value = subGrupos.find { it.id.toString() == subGrupoProduto }?.descSubGrupo ?: subGrupoProduto,
                    placeholder = if (grupoProduto.isEmpty() || grupoProduto == "TODOS") "Selecione um grupo primeiro" else "Selecionar sub-grupo",
                    loading = loadingSubGrupos,
                    enabled = grupoProduto.isNotEmpty() && grupoProduto != "TODOS",
                    onClick = { if (grupoProduto.isNotEmpty() && grupoProduto != "TODOS") showSubGrupo = true }
                )

                // Marca
                AvpFilterRow(
                    label = "Marca",
                    value = marcas.find { it.id.toString() == marca }?.descMarca ?: marca,
                    placeholder = "Selecionar marca",
                    loading = loadingMarcas,
                    onClick = { showMarca = true }
                )

                // Classe
                AvpFilterRow(
                    label = "Classe",
                    value = classes.find { it.id.toString() == classe }?.descClasse ?: classe,
                    placeholder = "Selecionar classe",
                    loading = loadingClasses,
                    onClick = { showClasse = true }
                )

                // Vendedor
                AvpFilterRow(
                    label = "Vendedor",
                    value = vendedores.find { it.id.toString() == vendedor }?.nomeFuncionario ?: vendedor,
                    placeholder = "Selecionar vendedor",
                    loading = loadingVendedores,
                    onClick = { showVendedor = true }
                )

                // Custo
                AvpFilterRow(
                    label = "Custo",
                    value = opcoesCusto.find { it.first == custo }?.second ?: custo,
                    placeholder = "Selecionar tipo de custo",
                    onClick = { showCusto = true }
                )

                // Tipo
                AvpFilterRow(
                    label = "Tipo",
                    value = opcoesTipo.find { it.first == tipo }?.second ?: tipo,
                    placeholder = "Selecionar tipo",
                    onClick = { showTipo = true }
                )

                // Situação
                AvpFilterRow(
                    label = "Situação",
                    value = opcoesSituacao.find { it.first == situacao }?.second ?: situacao,
                    placeholder = "Selecionar situação",
                    onClick = { showSituacao = true }
                )

                // Mostrar Peso
                AvpFilterRow(
                    label = "Mostrar Peso",
                    value = opcoesPeso.find { it.first == mostrarPeso }?.second ?: mostrarPeso,
                    placeholder = "Selecionar se mostra peso",
                    onClick = { showPeso = true }
                )

                // Período
                Spacer(modifier = Modifier.height(4.dp))
                Text("Período de Vendas", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AvpDark))
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("De", style = TextStyle(fontSize = 11.sp, color = AvpSlate))
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(AvpLight).clickable { showDateIni = true }.padding(12.dp)
                        ) {
                            Text("📅 ${formatDisplayDate(dataInicio)}", style = TextStyle(fontSize = 13.sp, color = AvpDark))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Até", style = TextStyle(fontSize = 11.sp, color = AvpSlate))
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(AvpLight).clickable { showDateFim = true }.padding(12.dp)
                        ) {
                            Text("📅 ${formatDisplayDate(dataFim)}", style = TextStyle(fontSize = 13.sp, color = AvpDark))
                        }
                    }
                }

                // Erro / Sucesso
                if (pdfError.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(pdfError, style = TextStyle(fontSize = 12.sp, color = AvpRed))
                }
                if (pdfSuccess) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("✅ PDF gerado e aberto com sucesso!", style = TextStyle(fontSize = 12.sp, color = AvpGreen))
                }

                // Botão Gerar
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (loadingPdf) return@Button
                        pdfError = ""; pdfSuccess = false
                        scope.launch {
                            loadingPdf = true
                            val svc = AnaliseVendaProdutoService(session.userToken)
                            val emp = filial.idEmpresa.toString()
                            val fil = selectedFiliais.firstOrNull()?.idFilial?.toString() ?: filial.idFilial.toString()
                            val filiaisIds = selectedFiliais.joinToString(",") { it.idFilial.toString() }.ifEmpty { fil }
                            val result = svc.fetchRelatorioPdf(
                                idEmpresa    = emp,
                                idFilial     = fil,
                                dtIni        = formatApiDate(dataInicio),
                                dtFim        = formatApiDate(dataFim),
                                filiaisIds   = filiaisIds,
                                grupo        = if (grupoProduto.isNotEmpty() && grupoProduto != "TODOS") grupoProduto else "",
                                subGrupo     = if (subGrupoProduto.isNotEmpty() && subGrupoProduto != "TODOS") subGrupoProduto else "",
                                marca        = if (marca.isNotEmpty() && marca != "TODAS") marca else "",
                                classe       = if (classe.isNotEmpty() && classe != "TODAS") classe else "",
                                vendedor     = if (vendedor.isNotEmpty() && vendedor != "TODOS") vendedor else "",
                                custo        = mapCusto(custo),
                                venTipo      = mapTipo(tipo),
                                venSituacao  = mapSit(situacao),
                                mostraPeso   = mostrarPeso
                            )
                            if (result.success && result.data != null) {
                                val ok = salvarEAbrirPdf(context, result.data, "analise_venda_produto_${formatApiDate(dataInicio)}_${formatApiDate(dataFim)}.pdf")
                                if (ok) pdfSuccess = true else pdfError = "Erro ao abrir o PDF no dispositivo."
                            } else {
                                pdfError = result.error ?: "Erro ao gerar relatório."
                            }
                            loadingPdf = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AvpNavy)
                ) {
                    if (loadingPdf) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (loadingPdf) "Gerando PDF..." else "📄 Gerar Relatório PDF",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White))
                }
            }
        }

        // ── Date Pickers ──────────────────────────────────────────────────────
        if (showDateIni) {
            AvpDateDialog(initial = dataInicio, onDismiss = { showDateIni = false }) {
                dataInicio = it; showDateIni = false
            }
        }
        if (showDateFim) {
            AvpDateDialog(initial = dataFim, onDismiss = { showDateFim = false }) {
                dataFim = it; showDateFim = false
            }
        }

        // ── Modal Filiais ─────────────────────────────────────────────────────
        if (showFiliais) {
            AvpSelectModal(
                title = "Selecionar Filiais",
                items = filiais.map { f ->
                    val label = if (f.identificacaoInterna.isNotEmpty())
                        "${f.identificacaoInterna} | ID: ${f.idFilial}"
                    else
                        "ID: ${f.idFilial}"
                    f.idFilial.toString() to label
                },
                selectedValues = selectedFiliais.map { it.idFilial.toString() }.toSet(),
                multiSelect = true,
                onDismiss = { showFiliais = false },
                onSelect = { value ->
                    val f = filiais.find { it.idFilial.toString() == value }
                    if (f != null) FilialState.toggleFilial(f)
                }
            )
        }

        // ── Modais simples (single select) ────────────────────────────────────
        if (showGrupo) {
            AvpSelectModal(
                title = "Grupo de Produtos",
                items = listOf("TODOS" to "Todos os Grupos") + grupos.map { it.id.toString() to it.descGrupo },
                selectedValues = setOf(grupoProduto),
                loading = loadingGrupos,
                onDismiss = { showGrupo = false },
                onSelect = { value ->
                    grupoProduto = value
                    grupoId = grupos.find { it.id.toString() == value }?.id
                    subGrupoProduto = ""
                    showGrupo = false
                }
            )
        }
        if (showSubGrupo) {
            AvpSelectModal(
                title = "Sub-Grupo de Produtos",
                items = listOf("TODOS" to "Todos os Sub-Grupos") + subGrupos.map { it.id.toString() to it.descSubGrupo },
                selectedValues = setOf(subGrupoProduto),
                loading = loadingSubGrupos,
                onDismiss = { showSubGrupo = false },
                onSelect = { value -> subGrupoProduto = value; showSubGrupo = false }
            )
        }
        if (showMarca) {
            AvpSelectModal(
                title = "Marca",
                items = listOf("TODAS" to "Todas as Marcas") + marcas.map { it.id.toString() to it.descMarca },
                selectedValues = setOf(marca),
                loading = loadingMarcas,
                onDismiss = { showMarca = false },
                onSelect = { value -> marca = value; showMarca = false }
            )
        }
        if (showClasse) {
            AvpSelectModal(
                title = "Classe",
                items = listOf("TODAS" to "Todas as Classes") + classes.map { it.id.toString() to it.descClasse },
                selectedValues = setOf(classe),
                loading = loadingClasses,
                onDismiss = { showClasse = false },
                onSelect = { value -> classe = value; showClasse = false }
            )
        }
        if (showVendedor) {
            AvpSelectModal(
                title = "Vendedor",
                items = listOf("TODOS" to "Todos os Vendedores") + vendedores.map { it.id.toString() to it.nomeFuncionario },
                selectedValues = setOf(vendedor),
                loading = loadingVendedores,
                onDismiss = { showVendedor = false },
                onSelect = { value -> vendedor = value; showVendedor = false }
            )
        }
        if (showCusto) {
            AvpSelectModal(title = "Custo", items = opcoesCusto, selectedValues = setOf(custo),
                onDismiss = { showCusto = false }, onSelect = { custo = it; showCusto = false })
        }
        if (showTipo) {
            AvpSelectModal(title = "Tipo", items = opcoesTipo, selectedValues = setOf(tipo),
                onDismiss = { showTipo = false }, onSelect = { tipo = it; showTipo = false })
        }
        if (showSituacao) {
            AvpSelectModal(title = "Situação", items = opcoesSituacao, selectedValues = setOf(situacao),
                onDismiss = { showSituacao = false }, onSelect = { situacao = it; showSituacao = false })
        }
        if (showPeso) {
            AvpSelectModal(title = "Mostrar Peso", items = opcoesPeso, selectedValues = setOf(mostrarPeso),
                onDismiss = { showPeso = false }, onSelect = { mostrarPeso = it; showPeso = false })
        }
    }
}

// ── Salvar e abrir PDF ────────────────────────────────────────────────────────
private fun salvarEAbrirPdf(context: Context, bytes: ByteArray, fileName: String): Boolean {
    return try {
        val dir = File(context.cacheDir, "pdfs").also { it.mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        android.util.Log.e("AVP_PDF", "Erro ao abrir PDF: ${e.message}", e)
        false
    }
}

// ── Componentes auxiliares ────────────────────────────────────────────────────

@Composable
fun AvpCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
fun AvpFilterRow(
    label: String,
    value: String,
    placeholder: String,
    loading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(label, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AvpDark))
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (enabled) AvpLight else Color(0xFFE2E8F0))
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AvpBlue, strokeWidth = 2.dp)
            } else {
                Text(
                    text = if (value.isNotEmpty()) value else placeholder,
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = if (value.isNotEmpty() && enabled) AvpDark else AvpSlate
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = if (enabled) AvpSlate else Color(0xFFCBD5E1), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun AvpSelectModal(
    title: String,
    items: List<Pair<String, String>>,
    selectedValues: Set<String>,
    multiSelect: Boolean = false,
    loading: Boolean = false,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = if (search.isEmpty()) items else items.filter { it.second.contains(search, ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                // Header
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AvpDark))
                    TextButton(onClick = onDismiss) { Text("Fechar", color = AvpBlue) }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Busca
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Buscar...", style = TextStyle(fontSize = 13.sp, color = AvpSlate)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Lista
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (loading) {
                        Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PrimaryBlue)
                        }
                    } else if (filtered.isEmpty()) {
                        Text("Nenhum resultado.", style = TextStyle(fontSize = 13.sp, color = AvpSlate), modifier = Modifier.padding(16.dp))
                    } else {
                        filtered.forEach { (value, label) ->
                            val isSel = selectedValues.contains(value)
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color(0xFFEFF6FF) else Color.Transparent)
                                    .clickable { onSelect(value) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, style = TextStyle(fontSize = 13.sp, color = if (isSel) AvpBlue else AvpDark), modifier = Modifier.weight(1f))
                                if (isSel) Icon(Icons.Default.Check, contentDescription = null, tint = AvpBlue, modifier = Modifier.size(16.dp))
                            }
                            HorizontalDivider(color = AvpLight)
                        }
                    }
                }

                if (multiSelect) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AvpNavy)
                    ) { Text("Confirmar", color = Color.White) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvpDateDialog(initial: Date, onDismiss: () -> Unit, onConfirm: (Date) -> Unit) {
    val cal = Calendar.getInstance().apply { time = initial }
    val state = rememberDatePickerState(initialSelectedDateMillis = cal.timeInMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis
                if (ms != null) onConfirm(Date(ms)) else onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    ) {
        DatePicker(state = state)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AnaliseVendaProdutoScreenPreview() {
    AnaliseVendaProdutoScreen(rememberNavController())
}
