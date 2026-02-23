package com.example.vstmobile.ui.screens.finance

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ── Cores ─────────────────────────────────────────────────────────────────────
private val LcNavy  = Color(0xFF1E3A8A)
private val LcBlue  = Color(0xFF3B82F6)
private val LcSlate = Color(0xFF64748B)
private val LcLight = Color(0xFFF1F5F9)
private val LcDark  = Color(0xFF1E293B)
private val LcGreen = Color(0xFF059669)
private val LcRed   = Color(0xFFEF4444)

private val lcOpcoesPeriodo = listOf(
    "DTVENCIMENTO" to "Data de Vencimento",
    "DTCADASTRO"   to "Data de Cadastro",
    "DTEMISSAO"    to "Data de Emissão"
)

private fun lcFmtApi(d: Date)     = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d)
private fun lcFmtDisplay(d: Date) = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d)

// ── Tela ──────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitulosPagarLocalCobrancaScreen(navController: NavHostController) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val scope   = rememberCoroutineScope()

    val filial = FilialState.selectedFilial

    // Filtros
    var locaisSelecionados      by remember { mutableStateOf<List<LocalCobranca>>(emptyList()) }
    var periodo                 by remember { mutableStateOf("DTVENCIMENTO") }
    var impComp                 by remember { mutableStateOf(false) }
    var impCheque               by remember { mutableStateOf(false) }
    var impAdt                  by remember { mutableStateOf(false) }
    var valorDe                 by remember { mutableStateOf("") }
    var valorAte                by remember { mutableStateOf("") }
    var dataInicio              by remember { mutableStateOf(Date()) }
    var dataFim                 by remember { mutableStateOf(Date()) }

    // Locais de cobrança
    var opcoesLocal             by remember { mutableStateOf<List<LocalCobranca>>(emptyList()) }
    var loadingLocais           by remember { mutableStateOf(false) }

    // Fornecedores
    var searchFornecedor        by remember { mutableStateOf("") }
    var fornecedorSelecionado   by remember { mutableStateOf<FornecedorTPC?>(null) }
    var fornecedores            by remember { mutableStateOf<List<FornecedorTPC>>(emptyList()) }
    var totalRecords            by remember { mutableStateOf(0) }
    var totalPages              by remember { mutableStateOf(0) }
    var currentPage             by remember { mutableStateOf(0) }
    var loadingFornecedores     by remember { mutableStateOf(false) }

    // UI
    var showLocalModal          by remember { mutableStateOf(false) }
    var showPeriodoModal        by remember { mutableStateOf(false) }
    var showFornecedorModal     by remember { mutableStateOf(false) }
    var showDateIni             by remember { mutableStateOf(false) }
    var showDateFim             by remember { mutableStateOf(false) }
    var loadingPdf              by remember { mutableStateOf(false) }
    var msgErro                 by remember { mutableStateOf("") }
    var msgSucesso              by remember { mutableStateOf("") }

    // Carregar locais ao abrir / trocar filial
    LaunchedEffect(filial?.idEmpresa) {
        if (filial == null || session.userToken.isEmpty()) return@LaunchedEffect
        loadingLocais = true
        val svc = TitulosPagarLocalCobrancaService(session.userToken)
        val r = svc.fetchLocaisCobranca(filial.idEmpresa.toString())
        opcoesLocal = r.data ?: emptyList()
        loadingLocais = false
    }

    fun toggleLocal(local: LocalCobranca) {
        locaisSelecionados = if (locaisSelecionados.any { it.idLocalCobranca == local.idLocalCobranca })
            locaisSelecionados.filter { it.idLocalCobranca != local.idLocalCobranca }
        else
            locaisSelecionados + local
    }

    fun buscarFornecedores(termo: String, page: Int, reset: Boolean) {
        if (filial == null) return
        scope.launch {
            loadingFornecedores = true
            val svc = TitulosPagarLocalCobrancaService(session.userToken)
            val r = svc.fetchFornecedores(filial.idEmpresa.toString(), termo, page)
            r.data?.let { result ->
                fornecedores = if (reset) result.fornecedores else fornecedores + result.fornecedores
                totalRecords = result.totalRecords
                totalPages   = result.totalPages
                currentPage  = page
            }
            loadingFornecedores = false
        }
    }

    fun abrirBuscaFornecedor() {
        fornecedores = emptyList(); currentPage = 0; totalRecords = 0; totalPages = 0
        showFornecedorModal = true
        buscarFornecedores(searchFornecedor.trim(), 0, true)
    }

    fun gerarPdf() {
        if (filial == null) { msgErro = "Selecione uma filial"; return }
        if (locaisSelecionados.isEmpty()) { msgErro = "Selecione pelo menos um local de cobrança"; return }
        msgErro = ""; msgSucesso = ""
        scope.launch {
            loadingPdf = true
            val svc = TitulosPagarLocalCobrancaService(session.userToken)
            val r = svc.fetchRelatorioPdf(
                idEmpresa    = filial.idEmpresa.toString(),
                idFilial     = filial.idFilial.toString(),
                dtIni        = lcFmtApi(dataInicio),
                dtFim        = lcFmtApi(dataFim),
                periodo      = periodo,
                idsLocais    = locaisSelecionados.map { it.idLocalCobranca },
                impComp      = impComp,
                impAdt       = impAdt,
                impCheque    = impCheque,
                fornecedorId = fornecedorSelecionado?.idClifor ?: 0,
                valorDe      = valorDe,
                valorAte     = valorAte
            )
            if (r.success && r.data != null) {
                val nome = "titulos_pagar_local_cobranca_${lcFmtApi(dataInicio)}_${lcFmtApi(dataFim)}.pdf"
                val ok = lcAbrirPdf(context, r.data, nome)
                if (ok) msgSucesso = "✅ PDF gerado e aberto com sucesso!"
                else    msgErro    = "Erro ao abrir PDF no dispositivo."
            } else {
                msgErro = r.error ?: "Erro ao gerar relatório"
            }
            loadingPdf = false
        }
    }

    ScreenWithSidebar(
        navController = navController,
        title         = "Títulos a Pagar - Local Cobrança",
        currentRoute  = Screen.TitulosPagarLocalCobranca.route
    ) { _ ->

        if (filial == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("📋", fontSize = 48.sp)
                    Text("Selecione uma filial", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LcDark))
                    Text("Abra o menu lateral e selecione\numa filial para continuar.",
                        style = TextStyle(fontSize = 14.sp, color = LcSlate), textAlign = TextAlign.Center)
                }
            }
            return@ScreenWithSidebar
        }

        Column(
            modifier = Modifier.fillMaxSize().background(LcLight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LcCard {
                // ── Local de Cobrança ─────────────────────────────────────────
                LcLabel("Local de Cobrança *")
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(LcLight).clickable { showLocalModal = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when {
                            loadingLocais             -> "Carregando locais..."
                            locaisSelecionados.isEmpty() -> "Selecionar local de cobrança"
                            locaisSelecionados.size == 1 -> locaisSelecionados.first().descLocalCobranca
                            else -> "${locaisSelecionados.size} local(is) selecionado(s)"
                        },
                        style = TextStyle(fontSize = 13.sp,
                            color = if (locaisSelecionados.isNotEmpty()) LcDark else LcSlate)
                    )
                    if (loadingLocais) CircularProgressIndicator(Modifier.size(18.dp), color = LcBlue, strokeWidth = 2.dp)
                    else Icon(Icons.Default.KeyboardArrowDown, null, tint = LcSlate, modifier = Modifier.size(20.dp))
                }
                // Tags dos locais selecionados
                if (locaisSelecionados.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    LcChipGroup(locaisSelecionados.map { it.descLocalCobranca }) { idx -> toggleLocal(locaisSelecionados[idx]) }
                }

                Spacer(Modifier.height(20.dp))

                // ── Período ───────────────────────────────────────────────────
                LcLabel("Período")
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(LcLight).clickable { showPeriodoModal = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(lcOpcoesPeriodo.find { it.first == periodo }?.second ?: periodo,
                        style = TextStyle(fontSize = 13.sp, color = LcDark))
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = LcSlate, modifier = Modifier.size(20.dp))
                }

                Spacer(Modifier.height(20.dp))

                // ── Opções (checkboxes) ───────────────────────────────────────
                LcLabel("Opções")
                Spacer(Modifier.height(8.dp))
                LcCheckbox("Listar informações complementares?", impComp) { impComp = it }
                LcCheckbox("Listar cheques emitidos?", impCheque) { impCheque = it }
                LcCheckbox("Listar adiantamentos?", impAdt) { impAdt = it }

                Spacer(Modifier.height(20.dp))

                // ── Fornecedor ────────────────────────────────────────────────
                LcLabel("Fornecedor (opcional)")
                Spacer(Modifier.height(6.dp))
                if (fornecedorSelecionado != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF0FDF4)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(fornecedorSelecionado!!.nomeRazao,
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LcGreen))
                            if (fornecedorSelecionado!!.apelidoFantasia.isNotEmpty() &&
                                fornecedorSelecionado!!.apelidoFantasia != fornecedorSelecionado!!.nomeRazao) {
                                Text(fornecedorSelecionado!!.apelidoFantasia, style = TextStyle(fontSize = 12.sp, color = LcGreen))
                            }
                            Text("ID: ${fornecedorSelecionado!!.idClifor}" +
                                    if (fornecedorSelecionado!!.cpfCnpj.isNotEmpty()) " • ${fornecedorSelecionado!!.cpfCnpj}" else "",
                                style = TextStyle(fontSize = 12.sp, color = Color(0xFF065F46)))
                        }
                        IconButton(onClick = { fornecedorSelecionado = null; searchFornecedor = "" }) {
                            Icon(Icons.Default.Close, null, tint = LcSlate)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(Color.White),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = searchFornecedor,
                            onValueChange = { searchFornecedor = it },
                            placeholder = { Text("Nome, CPF, CNPJ ou código...", style = TextStyle(fontSize = 13.sp, color = LcSlate)) },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { abrirBuscaFornecedor() })
                        )
                        TextButton(onClick = { abrirBuscaFornecedor() },
                            enabled = searchFornecedor.trim().length >= 2 && !loadingFornecedores) {
                            Icon(Icons.Default.Search, null, tint = LcBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Buscar", style = TextStyle(color = LcBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Valores ───────────────────────────────────────────────────
                LcLabel("Valores (opcional)")
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = valorDe, onValueChange = { valorDe = it },
                        label = { Text("De") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = valorAte, onValueChange = { valorAte = it },
                        label = { Text("Até") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))

                // ── Datas ─────────────────────────────────────────────────────
                LcLabel("Data *")
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("De", style = TextStyle(fontSize = 11.sp, color = LcSlate)); Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(LcLight).clickable { showDateIni = true }.padding(12.dp)) {
                            Text("📅 ${lcFmtDisplay(dataInicio)}", style = TextStyle(fontSize = 13.sp, color = LcDark))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Até", style = TextStyle(fontSize = 11.sp, color = LcSlate)); Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(LcLight).clickable { showDateFim = true }.padding(12.dp)) {
                            Text("📅 ${lcFmtDisplay(dataFim)}", style = TextStyle(fontSize = 13.sp, color = LcDark))
                        }
                    }
                }
                Text("Toque nos campos para selecionar as datas",
                    style = TextStyle(fontSize = 11.sp, color = LcSlate), modifier = Modifier.padding(top = 4.dp))

                Spacer(Modifier.height(24.dp))

                // ── Mensagens ─────────────────────────────────────────────────
                if (msgErro.isNotEmpty()) {
                    Text(msgErro, style = TextStyle(fontSize = 13.sp, color = LcRed))
                    Spacer(Modifier.height(8.dp))
                }
                if (msgSucesso.isNotEmpty()) {
                    Text(msgSucesso, style = TextStyle(fontSize = 13.sp, color = LcGreen))
                    Spacer(Modifier.height(8.dp))
                }

                // ── Botão Gerar PDF ───────────────────────────────────────────
                Button(
                    onClick = { gerarPdf() }, enabled = !loadingPdf,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LcNavy)
                ) {
                    if (loadingPdf) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (loadingPdf) "Gerando PDF..." else "📄 Gerar Relatório PDF",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White))
                }
            }
        }

        // ── DatePickers ───────────────────────────────────────────────────────
        if (showDateIni) {
            val state = rememberDatePickerState(initialSelectedDateMillis = dataInicio.time)
            DatePickerDialog(onDismissRequest = { showDateIni = false },
                confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { dataInicio = Date(it) }; showDateIni = false }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { showDateIni = false }) { Text("Cancelar") } }
            ) { DatePicker(state = state) }
        }
        if (showDateFim) {
            val state = rememberDatePickerState(initialSelectedDateMillis = dataFim.time)
            DatePickerDialog(onDismissRequest = { showDateFim = false },
                confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { dataFim = Date(it) }; showDateFim = false }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { showDateFim = false }) { Text("Cancelar") } }
            ) { DatePicker(state = state) }
        }

        // ── Modal Local de Cobrança ───────────────────────────────────────────
        if (showLocalModal) {
            LcLocalModal(
                locais          = opcoesLocal,
                selecionados    = locaisSelecionados,
                loading         = loadingLocais,
                onToggle        = { toggleLocal(it) },
                onDismiss       = { showLocalModal = false }
            )
        }

        // ── Modal Período ─────────────────────────────────────────────────────
        if (showPeriodoModal) {
            LcSimpleSelectModal(
                title    = "Selecionar Período",
                items    = lcOpcoesPeriodo,
                selected = periodo,
                onSelect = { periodo = it; showPeriodoModal = false },
                onDismiss = { showPeriodoModal = false }
            )
        }

        // ── Modal Fornecedores ────���───────────────────────────────────────────
        if (showFornecedorModal) {
            LcFornecedorModal(
                searchTerm     = searchFornecedor,
                onSearchChange = { searchFornecedor = it },
                fornecedores   = fornecedores,
                totalRecords   = totalRecords,
                totalPages     = totalPages,
                currentPage    = currentPage,
                loading        = loadingFornecedores,
                onBuscar       = { buscarFornecedores(searchFornecedor.trim(), 0, true) },
                onPagina       = { pg -> buscarFornecedores(searchFornecedor.trim(), pg, true) },
                onSelecionar   = { f -> fornecedorSelecionado = f; showFornecedorModal = false },
                onDismiss      = { showFornecedorModal = false }
            )
        }
    }
}

// ── Modal Local de Cobrança (multi-select) ────────────────────────────────────
@Composable
private fun LcLocalModal(
    locais: List<LocalCobranca>,
    selecionados: List<LocalCobranca>,
    loading: Boolean,
    onToggle: (LocalCobranca) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth()) {
                // Header
                Row(Modifier.fillMaxWidth().background(Color(0xFFF8FAFC)).padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Selecionar Local de Cobrança",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LcDark))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = LcSlate) }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Lista
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
                    when {
                        loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = LcBlue)
                                Spacer(Modifier.height(8.dp))
                                Text("Carregando locais...", style = TextStyle(fontSize = 13.sp, color = LcSlate))
                            }
                        }
                        locais.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("Nenhum local de cobrança encontrado",
                                style = TextStyle(fontSize = 14.sp, color = LcSlate), textAlign = TextAlign.Center)
                        }
                        else -> locais.forEach { local ->
                            val selected = selecionados.any { it.idLocalCobranca == local.idLocalCobranca }
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onToggle(local) }
                                    .background(if (selected) Color(0xFFEFF6FF) else Color.White)
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = selected, onCheckedChange = { onToggle(local) },
                                    colors = CheckboxDefaults.colors(checkedColor = LcBlue))
                                Spacer(Modifier.width(8.dp))
                                Text(local.descLocalCobranca,
                                    style = TextStyle(fontSize = 14.sp, color = if (selected) LcBlue else LcDark,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal))
                            }
                            HorizontalDivider(color = LcLight)
                        }
                    }
                }

                // Footer
                HorizontalDivider(color = Color(0xFFE5E7EB))
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${selecionados.size} selecionado(s)", style = TextStyle(fontSize = 13.sp, color = LcSlate))
                    Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LcBlue)) {
                        Text("Confirmar", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White))
                    }
                }
            }
        }
    }
}

// ── Modal de seleção simples ──────────────────────────────────────────────────
@Composable
private fun LcSimpleSelectModal(
    title: String,
    items: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().background(Color(0xFFF8FAFC)).padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LcDark))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = LcSlate) }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))
                items.forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(value) }
                            .background(if (value == selected) Color(0xFFEFF6FF) else Color.White)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = TextStyle(fontSize = 14.sp,
                            color = if (value == selected) LcBlue else LcDark,
                            fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal))
                        if (value == selected) Text("✓", style = TextStyle(fontSize = 16.sp, color = LcBlue, fontWeight = FontWeight.Bold))
                    }
                    HorizontalDivider(color = LcLight)
                }
            }
        }
    }
}

// ── Modal de Fornecedores ─────────────────────────────────────────────────────
@Composable
private fun LcFornecedorModal(
    searchTerm: String,
    onSearchChange: (String) -> Unit,
    fornecedores: List<FornecedorTPC>,
    totalRecords: Int,
    totalPages: Int,
    currentPage: Int,
    loading: Boolean,
    onBuscar: () -> Unit,
    onPagina: (Int) -> Unit,
    onSelecionar: (FornecedorTPC) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp),
            shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().background(Color(0xFFF8FAFC)).padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Buscar Fornecedor", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LcDark))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = LcSlate) }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))

                Row(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(12.dp)).background(LcLight),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = LcSlate, modifier = Modifier.padding(start = 12.dp))
                    TextField(value = searchTerm, onValueChange = onSearchChange,
                        placeholder = { Text("Nome do fornecedor (min. 2)...", style = TextStyle(fontSize = 13.sp, color = LcSlate)) },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onBuscar() }))
                    TextButton(onClick = onBuscar, enabled = searchTerm.trim().length >= 2 && !loading,
                        modifier = Modifier.padding(end = 4.dp)) {
                        Text("Buscar", style = TextStyle(color = if (searchTerm.trim().length >= 2) LcBlue else LcSlate,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp))
                    }
                }

                if (totalRecords > 0 && !loading) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$totalRecords fornecedor(es)", style = TextStyle(fontSize = 12.sp, color = LcSlate, fontWeight = FontWeight.SemiBold))
                        Text("Pág. ${currentPage + 1} de $totalPages", style = TextStyle(fontSize = 12.sp, color = LcSlate))
                    }
                }

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    when {
                        loading && fornecedores.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = LcBlue)
                                Spacer(Modifier.height(8.dp))
                                Text("Buscando fornecedores...", style = TextStyle(fontSize = 13.sp, color = LcSlate))
                            }
                        }
                        fornecedores.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                            Text(if (searchTerm.trim().length < 2) "Digite pelo menos 2 caracteres" else "Nenhum fornecedor encontrado",
                                style = TextStyle(fontSize = 14.sp, color = LcSlate), textAlign = TextAlign.Center)
                        }
                        else -> fornecedores.forEach { f ->
                            Row(Modifier.fillMaxWidth().clickable { onSelecionar(f) }.padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(f.nomeRazao.ifEmpty { "Nome não informado" },
                                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LcDark))
                                    if (f.apelidoFantasia.isNotEmpty() && f.apelidoFantasia != f.nomeRazao)
                                        Text(f.apelidoFantasia, style = TextStyle(fontSize = 12.sp, color = LcBlue))
                                    Text("ID: ${f.idClifor}", style = TextStyle(fontSize = 12.sp, color = LcSlate))
                                    if (f.cpfCnpj.isNotEmpty()) Text("CPF/CNPJ: ${f.cpfCnpj}", style = TextStyle(fontSize = 12.sp, color = LcSlate))
                                    if (f.telefone.isNotEmpty()) Text("📞 ${f.telefone}", style = TextStyle(fontSize = 12.sp, color = LcGreen))
                                }
                                Text("›", style = TextStyle(fontSize = 24.sp, color = LcBlue, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 8.dp))
                            }
                            HorizontalDivider(color = LcLight)
                        }
                    }
                }

                // Paginação
                if (totalPages > 1) {
                    val start   = maxOf(0, currentPage - 1)
                    val end     = minOf(totalPages - 1, currentPage + 1)
                    val visible = (start..end).toList()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text("Página: ", style = TextStyle(fontSize = 12.sp, color = LcSlate))
                        if (currentPage > 1) {
                            LcPageBtn(1, false, !loading) { onPagina(0) }
                            if (currentPage > 2) Text("...", style = TextStyle(fontSize = 12.sp, color = LcSlate), modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        visible.forEach { pg -> LcPageBtn(pg + 1, pg == currentPage, !loading && pg != currentPage) { onPagina(pg) } }
                        if (currentPage < totalPages - 2 && totalPages > 3) {
                            if (currentPage < totalPages - 3) Text("...", style = TextStyle(fontSize = 12.sp, color = LcSlate), modifier = Modifier.padding(horizontal = 4.dp))
                            LcPageBtn(totalPages, false, !loading) { onPagina(totalPages - 1) }
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Composable
private fun LcPageBtn(label: Int, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.padding(horizontal = 2.dp).size(32.dp).clip(RoundedCornerShape(6.dp))
        .background(if (active) LcBlue else Color(0xFFE2E8F0)).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center) {
        Text("$label", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else LcDark))
    }
}

// ── Chips para locais selecionados ────────────────────────────────────────────
@Composable
private fun LcChipGroup(labels: List<String>, onRemove: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.chunked(2).forEachIndexed { rowIdx, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { colIdx, label ->
                    val idx = rowIdx * 2 + colIdx
                    Row(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFEFF6FF)).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, style = TextStyle(fontSize = 11.sp, color = LcBlue), modifier = Modifier.weight(1f), maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Close, null, tint = LcBlue,
                            modifier = Modifier.size(14.dp).clickable { onRemove(idx) })
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
@Composable
private fun LcCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
private fun LcLabel(text: String) {
    Text(text, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LcDark))
}

@Composable
private fun LcCheckbox(label: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCheck(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheck,
            colors = CheckboxDefaults.colors(checkedColor = LcBlue))
        Spacer(Modifier.width(8.dp))
        Text(label, style = TextStyle(fontSize = 13.sp, color = LcDark))
    }
}

private fun lcAbrirPdf(context: Context, bytes: ByteArray, fileName: String): Boolean = try {
    val dir  = File(context.cacheDir, "pdfs").also { it.mkdirs() }
    val file = File(dir, fileName).also { it.writeBytes(bytes) }
    val uri  = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    })
    true
} catch (e: Exception) { android.util.Log.e("LC_PDF", "Erro: ${e.message}", e); false }

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun TitulosPagarLocalCobrancaScreenPreview() {
    TitulosPagarLocalCobrancaScreen(rememberNavController())
}
