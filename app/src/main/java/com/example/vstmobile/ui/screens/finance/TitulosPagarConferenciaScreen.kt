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
private val TpcNavy  = Color(0xFF1E3A8A)
private val TpcBlue  = Color(0xFF3B82F6)
private val TpcSlate = Color(0xFF64748B)
private val TpcLight = Color(0xFFF1F5F9)
private val TpcDark  = Color(0xFF1E293B)
private val TpcGreen = Color(0xFF059669)
private val TpcRed   = Color(0xFFEF4444)

private val opcoesOrdenacao = listOf(
    "DTVENCIMENTO" to "Data de Vencimento",
    "DTCADASTRO"   to "Data de Cadastro",
    "DTEMISSAO"    to "Data de Emissão"
)

private fun tpcFmtApi(d: Date)     = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d)
private fun tpcFmtDisplay(d: Date) = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d)

// ── Tela ──────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitulosPagarConferenciaScreen(navController: NavHostController) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val scope   = rememberCoroutineScope()

    val filial = FilialState.selectedFilial

    // Filtros
    var searchFornecedor        by remember { mutableStateOf("") }
    var fornecedorSelecionado   by remember { mutableStateOf<FornecedorTPC?>(null) }
    var impComp                 by remember { mutableStateOf(false) }
    var impCheque               by remember { mutableStateOf(false) }
    var impAdt                  by remember { mutableStateOf(false) }
    var ordenarPor              by remember { mutableStateOf("DTVENCIMENTO") }
    var dataInicio              by remember { mutableStateOf(Date()) }
    var dataFim                 by remember { mutableStateOf(Date()) }

    // Fornecedores paginados
    var fornecedores            by remember { mutableStateOf<List<FornecedorTPC>>(emptyList()) }
    var totalRecords            by remember { mutableStateOf(0) }
    var totalPages              by remember { mutableStateOf(0) }
    var currentPage             by remember { mutableStateOf(0) }
    var loadingFornecedores     by remember { mutableStateOf(false) }

    // UI
    var showFornecedorModal     by remember { mutableStateOf(false) }
    var showOrdenacaoModal      by remember { mutableStateOf(false) }
    var showDateIni             by remember { mutableStateOf(false) }
    var showDateFim             by remember { mutableStateOf(false) }
    var loadingPdf              by remember { mutableStateOf(false) }
    var msgErro                 by remember { mutableStateOf("") }
    var msgSucesso              by remember { mutableStateOf("") }

    fun buscarFornecedores(termo: String, page: Int, reset: Boolean) {
        if (filial == null) return
        scope.launch {
            loadingFornecedores = true
            val svc = TitulosPagarConferenciaService(session.userToken)
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

    fun abrirBusca() {
        if (searchFornecedor.trim().length < 2) { msgErro = "Digite pelo menos 2 caracteres"; return }
        fornecedores = emptyList(); currentPage = 0
        showFornecedorModal = true
        buscarFornecedores(searchFornecedor.trim(), 0, true)
    }

    fun gerarPdf() {
        if (filial == null) { msgErro = "Selecione uma filial"; return }
        msgErro = ""; msgSucesso = ""
        scope.launch {
            loadingPdf = true
            val svc = TitulosPagarConferenciaService(session.userToken)
            val r = svc.fetchRelatorioPdf(
                idEmpresa    = filial.idEmpresa.toString(),
                idFilial     = filial.idFilial.toString(),
                dtIni        = tpcFmtApi(dataInicio),
                dtFim        = tpcFmtApi(dataFim),
                impComp      = impComp,
                impAdt       = impAdt,
                impCheque    = impCheque,
                fornecedorId = fornecedorSelecionado?.idClifor ?: 0,
                periodo      = ordenarPor
            )
            if (r.success && r.data != null) {
                val nome = "titulos_pagar_conferencia_${tpcFmtApi(dataInicio)}_${tpcFmtApi(dataFim)}.pdf"
                val ok = tpcAbrirPdf(context, r.data, nome)
                if (ok) msgSucesso = "✅ PDF gerado e aberto com sucesso!"
                else    msgErro    = "Erro ao abrir PDF no dispositivo."
            } else {
                msgErro = r.error ?: "Erro ao gerar relatório"
            }
            loadingPdf = false
        }
    }

    ScreenWithSidebar(
        navController  = navController,
        title          = "Títulos a Pagar - Conferência",
        currentRoute   = Screen.TitulosPagarConferencia.route
    ) { _ ->

        if (filial == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("📋", fontSize = 48.sp)
                    Text("Selecione uma filial",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TpcDark))
                    Text("Abra o menu lateral e selecione\numa filial para continuar.",
                        style = TextStyle(fontSize = 14.sp, color = TpcSlate), textAlign = TextAlign.Center)
                }
            }
            return@ScreenWithSidebar
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TpcLight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TpcCard {
                // ── Fornecedor ────────────────────────────────────────────────
                TpcLabel("Fornecedor")
                Spacer(modifier = Modifier.height(6.dp))

                if (fornecedorSelecionado != null) {
                    // Card verde com fornecedor selecionado
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF0FDF4))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fornecedorSelecionado!!.nomeRazao,
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TpcGreen))
                            if (fornecedorSelecionado!!.apelidoFantasia.isNotEmpty() &&
                                fornecedorSelecionado!!.apelidoFantasia != fornecedorSelecionado!!.nomeRazao) {
                                Text(fornecedorSelecionado!!.apelidoFantasia,
                                    style = TextStyle(fontSize = 12.sp, color = TpcGreen))
                            }
                            Text("ID: ${fornecedorSelecionado!!.idClifor}" +
                                    if (fornecedorSelecionado!!.cpfCnpj.isNotEmpty()) " • ${fornecedorSelecionado!!.cpfCnpj}" else "",
                                style = TextStyle(fontSize = 12.sp, color = Color(0xFF065F46)))
                        }
                        IconButton(onClick = {
                            fornecedorSelecionado = null
                            searchFornecedor = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpar", tint = TpcSlate)
                        }
                    }
                } else {
                    // Campo busca + botão
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .padding(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = searchFornecedor,
                            onValueChange = { searchFornecedor = it; msgErro = "" },
                            placeholder = { Text("Nome, CPF, CNPJ ou código...", style = TextStyle(fontSize = 13.sp, color = TpcSlate)) },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { abrirBusca() })
                        )
                        TextButton(
                            onClick = { abrirBusca() },
                            enabled = searchFornecedor.trim().length >= 2 && !loadingFornecedores
                        ) {
                            if (loadingFornecedores) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TpcBlue, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, tint = TpcBlue, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Buscar", style = TextStyle(color = TpcBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Opções (checkboxes) ────────────────────────────────────────
                TpcLabel("Opções")
                Spacer(modifier = Modifier.height(8.dp))
                TpcCheckbox("Listar informações complementares?", impComp) { impComp = it }
                TpcCheckbox("Listar cheques emitidos?", impCheque) { impCheque = it }
                TpcCheckbox("Listar adiantamentos?", impAdt) { impAdt = it }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Ordenar por ───────────────────────────────────────────────
                TpcLabel("Período / Ordenar por")
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TpcLight)
                        .clickable { showOrdenacaoModal = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        opcoesOrdenacao.find { it.first == ordenarPor }?.second ?: ordenarPor,
                        style = TextStyle(fontSize = 13.sp, color = TpcDark)
                    )
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TpcSlate, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Datas ─────────────────────────────────────────────────────
                TpcLabel("Data *")
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("De", style = TextStyle(fontSize = 11.sp, color = TpcSlate))
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(TpcLight).clickable { showDateIni = true }.padding(12.dp)) {
                            Text("📅 ${tpcFmtDisplay(dataInicio)}", style = TextStyle(fontSize = 13.sp, color = TpcDark))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Até", style = TextStyle(fontSize = 11.sp, color = TpcSlate))
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(TpcLight).clickable { showDateFim = true }.padding(12.dp)) {
                            Text("📅 ${tpcFmtDisplay(dataFim)}", style = TextStyle(fontSize = 13.sp, color = TpcDark))
                        }
                    }
                }
                Text("Toque nos campos para selecionar as datas",
                    style = TextStyle(fontSize = 11.sp, color = TpcSlate),
                    modifier = Modifier.padding(top = 4.dp))

                Spacer(modifier = Modifier.height(24.dp))

                // ── Mensagens ─────────────────────────────────────────────────
                if (msgErro.isNotEmpty()) {
                    Text(msgErro, style = TextStyle(fontSize = 13.sp, color = TpcRed))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (msgSucesso.isNotEmpty()) {
                    Text(msgSucesso, style = TextStyle(fontSize = 13.sp, color = TpcGreen))
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ── Botão Gerar PDF ────────────────────────────────────────────
                Button(
                    onClick = { gerarPdf() },
                    enabled = !loadingPdf,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TpcNavy)
                ) {
                    if (loadingPdf) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (loadingPdf) "Gerando PDF..." else "📄 Gerar Relatório PDF",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    )
                }
            }
        }

        // ── DatePickers ───────────────────────────────────────────────────────
        if (showDateIni) {
            val state = rememberDatePickerState(initialSelectedDateMillis = dataInicio.time)
            DatePickerDialog(
                onDismissRequest = { showDateIni = false },
                confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { dataInicio = Date(it) }; showDateIni = false }) { Text("OK") } },
                dismissButton  = { TextButton(onClick = { showDateIni = false }) { Text("Cancelar") } }
            ) { DatePicker(state = state) }
        }
        if (showDateFim) {
            val state = rememberDatePickerState(initialSelectedDateMillis = dataFim.time)
            DatePickerDialog(
                onDismissRequest = { showDateFim = false },
                confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { dataFim = Date(it) }; showDateFim = false }) { Text("OK") } },
                dismissButton  = { TextButton(onClick = { showDateFim = false }) { Text("Cancelar") } }
            ) { DatePicker(state = state) }
        }

        // ── Modal Ordenação ───────────────────────────────────────────────────
        if (showOrdenacaoModal) {
            TpcSelectModal(
                title     = "Ordenar por",
                items     = opcoesOrdenacao,
                selected  = ordenarPor,
                onDismiss = { showOrdenacaoModal = false },
                onSelect  = { v -> ordenarPor = v; showOrdenacaoModal = false }
            )
        }

        // ── Modal de Fornecedores ─────────────────────────────────────────────
        if (showFornecedorModal) {
            TpcFornecedorModal(
                searchTerm       = searchFornecedor,
                onSearchChange   = { searchFornecedor = it },
                fornecedores     = fornecedores,
                totalRecords     = totalRecords,
                totalPages       = totalPages,
                currentPage      = currentPage,
                loading          = loadingFornecedores,
                onBuscar         = { buscarFornecedores(searchFornecedor.trim(), 0, true) },
                onPagina         = { pg -> buscarFornecedores(searchFornecedor.trim(), pg, true) },
                onSelecionar     = { f -> fornecedorSelecionado = f; showFornecedorModal = false },
                onDismiss        = { showFornecedorModal = false }
            )
        }
    }
}

// ── Modal de Fornecedores ─────────────────────────────────────────────────────
@Composable
private fun TpcFornecedorModal(
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
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp),
            shape    = RoundedCornerShape(20.dp),
            colors   = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Buscar Fornecedor",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TpcDark))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TpcSlate)
                    }
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Campo busca
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                        .clip(RoundedCornerShape(12.dp)).background(TpcLight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TpcSlate,
                        modifier = Modifier.padding(start = 12.dp))
                    TextField(
                        value         = searchTerm,
                        onValueChange = onSearchChange,
                        placeholder   = { Text("Nome do fornecedor (min. 2)...", style = TextStyle(fontSize = 13.sp, color = TpcSlate)) },
                        modifier      = Modifier.weight(1f).focusRequester(focusRequester),
                        colors        = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine        = true,
                        keyboardOptions   = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions   = KeyboardActions(onSearch = { onBuscar() })
                    )
                    TextButton(onClick = onBuscar, enabled = searchTerm.trim().length >= 2 && !loading,
                        modifier = Modifier.padding(end = 4.dp)) {
                        Text("Buscar", style = TextStyle(
                            color = if (searchTerm.trim().length >= 2) TpcBlue else TpcSlate,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp))
                    }
                }

                // Contador de resultados
                if (totalRecords > 0 && !loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("$totalRecords fornecedor(es) encontrado(s)",
                            style = TextStyle(fontSize = 12.sp, color = TpcSlate, fontWeight = FontWeight.SemiBold))
                        Text("Pág. ${currentPage + 1} de $totalPages",
                            style = TextStyle(fontSize = 12.sp, color = TpcSlate))
                    }
                }

                // Lista
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    when {
                        loading && fornecedores.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = TpcBlue)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Buscando fornecedores...", style = TextStyle(fontSize = 13.sp, color = TpcSlate))
                                }
                            }
                        }
                        fornecedores.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (searchTerm.trim().length < 2) "Digite pelo menos 2 caracteres para buscar"
                                    else "Nenhum fornecedor encontrado",
                                    style = TextStyle(fontSize = 14.sp, color = TpcSlate), textAlign = TextAlign.Center
                                )
                            }
                        }
                        else -> {
                            fornecedores.forEach { f ->
                                TpcFornecedorItem(f) { onSelecionar(f) }
                                HorizontalDivider(color = TpcLight)
                            }
                        }
                    }
                }

                // Paginação
                if (totalPages > 1) {
                    TpcPaginacao(
                        currentPage = currentPage,
                        totalPages  = totalPages,
                        loading     = loading,
                        onPagina    = onPagina
                    )
                }
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Composable
private fun TpcFornecedorItem(f: FornecedorTPC, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(f.nomeRazao.ifEmpty { "Nome não informado" },
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TpcDark))
            if (f.apelidoFantasia.isNotEmpty() && f.apelidoFantasia != f.nomeRazao) {
                Text(f.apelidoFantasia, style = TextStyle(fontSize = 12.sp, color = TpcBlue))
            }
            Text("ID: ${f.idClifor}", style = TextStyle(fontSize = 12.sp, color = TpcSlate))
            if (f.cpfCnpj.isNotEmpty()) {
                Text("CPF/CNPJ: ${f.cpfCnpj}", style = TextStyle(fontSize = 12.sp, color = TpcSlate))
            }
            if (f.telefone.isNotEmpty()) {
                Text("📞 ${f.telefone}", style = TextStyle(fontSize = 12.sp, color = TpcGreen))
            }
        }
        Text("›", style = TextStyle(fontSize = 24.sp, color = TpcBlue, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun TpcPaginacao(currentPage: Int, totalPages: Int, loading: Boolean, onPagina: (Int) -> Unit) {
    // Calcular páginas visíveis (até 3 centrais)
    val start = maxOf(0, currentPage - 1)
    val end   = minOf(totalPages - 1, currentPage + 1)
    val visible = (start..end).toList()

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text("Página: ", style = TextStyle(fontSize = 12.sp, color = TpcSlate))

        // Primeira página
        if (currentPage > 1) {
            TpcPageBtn(1, active = false, enabled = !loading) { onPagina(0) }
            if (currentPage > 2) Text("...", style = TextStyle(fontSize = 12.sp, color = TpcSlate), modifier = Modifier.padding(horizontal = 4.dp))
        }

        visible.forEach { pg ->
            TpcPageBtn(pg + 1, active = pg == currentPage, enabled = !loading && pg != currentPage) { onPagina(pg) }
        }

        // Última página
        if (currentPage < totalPages - 2 && totalPages > 3) {
            if (currentPage < totalPages - 3) Text("...", style = TextStyle(fontSize = 12.sp, color = TpcSlate), modifier = Modifier.padding(horizontal = 4.dp))
            TpcPageBtn(totalPages, active = false, enabled = !loading) { onPagina(totalPages - 1) }
        }
    }
}

@Composable
private fun TpcPageBtn(label: Int, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.padding(horizontal = 2.dp).size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) TpcBlue else Color(0xFFE2E8F0))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text("$label", style = TextStyle(
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            color      = if (active) Color.White else TpcDark
        ))
    }
}

// ── Modal de seleção simples (local) ─────────────────────────────────────────
@Composable
private fun TpcSelectModal(
    title: String,
    items: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TpcDark))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TpcSlate)
                    }
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
                        Text(label, style = TextStyle(fontSize = 14.sp, color = if (value == selected) TpcBlue else TpcDark,
                            fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal))
                        if (value == selected) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = TpcBlue, modifier = Modifier.size(0.dp))
                            Text("✓", style = TextStyle(fontSize = 16.sp, color = TpcBlue, fontWeight = FontWeight.Bold))
                        }
                    }
                    HorizontalDivider(color = TpcLight)
                }
            }
        }
    }
}

// ── Componentes internos reutilizáveis ────────────────────────────────────────
@Composable
private fun TpcCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier   = Modifier.fillMaxWidth(),
        shape      = RoundedCornerShape(12.dp),
        colors     = CardDefaults.cardColors(containerColor = Color.White),
        elevation  = CardDefaults.cardElevation(2.dp)
    ) { Column(modifier = Modifier.fillMaxWidth().padding(16.dp), content = content) }
}

@Composable
private fun TpcLabel(text: String) {
    Text(text, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TpcDark))
}

@Composable
private fun TpcCheckbox(label: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheck(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked  = checked,
            onCheckedChange = onCheck,
            colors   = CheckboxDefaults.colors(checkedColor = TpcBlue)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = TextStyle(fontSize = 13.sp, color = TpcDark))
    }
}

// ── Abrir PDF ─────────────────────────────────────────────────────────────────
private fun tpcAbrirPdf(context: Context, bytes: ByteArray, fileName: String): Boolean {
    return try {
        val dir  = File(context.cacheDir, "pdfs").also { it.mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        val uri  = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        android.util.Log.e("TPC_PDF", "Erro ao abrir PDF: ${e.message}", e)
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun TitulosPagarConferenciaScreenPreview() {
    TitulosPagarConferenciaScreen(rememberNavController())
}
