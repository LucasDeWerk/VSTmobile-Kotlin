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
private val TrcNavy  = Color(0xFF1E3A8A)
private val TrcBlue  = Color(0xFF3B82F6)
private val TrcSlate = Color(0xFF64748B)
private val TrcLight = Color(0xFFF1F5F9)
private val TrcDark  = Color(0xFF1E293B)
private val TrcGreen = Color(0xFF059669)
private val TrcRed   = Color(0xFFEF4444)

private val trcOpcoesPeriodo = listOf(
    "DTVENCIMENTO" to "Data de Vencimento",
    "DTCADASTRO"   to "Data de Cadastro",
    "DTEMISSAO"    to "Data de Emissão"
)

private fun trcFmtApi(d: Date)     = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d)
private fun trcFmtDisplay(d: Date) = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d)

// ── Tela ──────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitulosReceberConferenciaScreen(navController: NavHostController) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val scope   = rememberCoroutineScope()

    val filial = FilialState.selectedFilial

    // Filtros
    var periodo               by remember { mutableStateOf("DTVENCIMENTO") }
    var impComp               by remember { mutableStateOf(false) }
    var impCheque             by remember { mutableStateOf(false) }
    var impAdt                by remember { mutableStateOf(false) }
    var valorDe               by remember { mutableStateOf("") }
    var valorAte              by remember { mutableStateOf("") }
    var dataInicio            by remember { mutableStateOf(Date()) }
    var dataFim               by remember { mutableStateOf(Date()) }

    // Cliente
    var searchCliente         by remember { mutableStateOf("") }
    var clienteSelecionado    by remember { mutableStateOf<ClienteTRC?>(null) }
    var clientes              by remember { mutableStateOf<List<ClienteTRC>>(emptyList()) }
    var loadingClientes       by remember { mutableStateOf(false) }

    // UI
    var showClienteModal      by remember { mutableStateOf(false) }
    var showPeriodoModal      by remember { mutableStateOf(false) }
    var showDateIni           by remember { mutableStateOf(false) }
    var showDateFim           by remember { mutableStateOf(false) }
    var loadingPdf            by remember { mutableStateOf(false) }
    var msgErro               by remember { mutableStateOf("") }
    var msgSucesso            by remember { mutableStateOf("") }

    fun buscarClientes(termo: String) {
        if (filial == null) return
        if (termo.trim().length < 3) { msgErro = "Digite pelo menos 3 caracteres"; return }
        msgErro = ""
        scope.launch {
            loadingClientes = true
            val svc = TitulosReceberConferenciaService(session.userToken)
            val r = svc.fetchClientes(filial.idEmpresa.toString(), termo.trim())
            clientes = r.data ?: emptyList()
            showClienteModal = true
            loadingClientes = false
        }
    }

    fun gerarPdf() {
        if (filial == null) { msgErro = "Selecione uma filial"; return }
        msgErro = ""; msgSucesso = ""
        scope.launch {
            loadingPdf = true
            val svc = TitulosReceberConferenciaService(session.userToken)
            val r = svc.fetchRelatorioPdf(
                idEmpresa = filial.idEmpresa.toString(),
                idFilial  = filial.idFilial.toString(),
                dtIni     = trcFmtApi(dataInicio),
                dtFim     = trcFmtApi(dataFim),
                periodo   = periodo,
                impComp   = impComp,
                impAdt    = impAdt,
                impCheque = impCheque,
                clienteId = clienteSelecionado?.idClifor ?: 0,
                valorDe   = valorDe,
                valorAte  = valorAte
            )
            if (r.success && r.data != null) {
                val nome = "titulos_receber_conferencia_${trcFmtApi(dataInicio)}_${trcFmtApi(dataFim)}.pdf"
                val ok = trcAbrirPdf(context, r.data, nome)
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
        title         = "Títulos a Receber - Conferência",
        currentRoute  = Screen.TitulosReceberConferencia.route
    ) { _ ->
        if (filial == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("📋", fontSize = 48.sp)
                    Text("Selecione uma filial",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TrcDark))
                    Text("Abra o menu lateral e selecione\numa filial para continuar.",
                        style = TextStyle(fontSize = 14.sp, color = TrcSlate), textAlign = TextAlign.Center)
                }
            }
            return@ScreenWithSidebar
        }

        Column(
            modifier = Modifier.fillMaxSize().background(TrcLight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TrcCard {

                // ── Cliente ───────────────────────────────────────────────────
                TrcLabel("Cliente (opcional)")
                Spacer(Modifier.height(6.dp))

                if (clienteSelecionado != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF0FDF4)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(clienteSelecionado!!.nomeRazao,
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TrcGreen))
                            if (clienteSelecionado!!.apelidoFantasia.isNotEmpty() &&
                                clienteSelecionado!!.apelidoFantasia != clienteSelecionado!!.nomeRazao) {
                                Text(clienteSelecionado!!.apelidoFantasia,
                                    style = TextStyle(fontSize = 12.sp, color = TrcGreen))
                            }
                            val doc = clienteSelecionado!!.cpfCnpj
                            Text(
                                "ID: ${clienteSelecionado!!.idClifor}" +
                                        if (doc.isNotEmpty()) " • $doc" else "",
                                style = TextStyle(fontSize = 12.sp, color = Color(0xFF065F46))
                            )
                        }
                        IconButton(onClick = {
                            clienteSelecionado = null
                            searchCliente = ""
                        }) {
                            Icon(Icons.Default.Close, null, tint = TrcSlate)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(Color.White),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = searchCliente,
                            onValueChange = { searchCliente = it; msgErro = "" },
                            placeholder = { Text("Nome, CPF, CNPJ ou código...",
                                style = TextStyle(fontSize = 13.sp, color = TrcSlate)) },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor   = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor   = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { buscarClientes(searchCliente) })
                        )
                        TextButton(
                            onClick  = { buscarClientes(searchCliente) },
                            enabled  = searchCliente.trim().length >= 3 && !loadingClientes
                        ) {
                            if (loadingClientes) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = TrcBlue, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, null, tint = TrcBlue, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Buscar",
                                    style = TextStyle(fontSize = 13.sp, color = TrcBlue, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                    Text("Mínimo 3 caracteres para buscar",
                        style = TextStyle(fontSize = 11.sp, color = TrcSlate),
                        modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(Modifier.height(20.dp))

                // ── Período ───────────────────────────────────────────────────
                TrcLabel("Período")
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(TrcLight).clickable { showPeriodoModal = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(trcOpcoesPeriodo.find { it.first == periodo }?.second ?: periodo,
                        style = TextStyle(fontSize = 13.sp, color = TrcDark))
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = TrcSlate, modifier = Modifier.size(20.dp))
                }

                Spacer(Modifier.height(20.dp))

                // ── Opções ────────────────────────────────────────────────────
                TrcLabel("Opções")
                Spacer(Modifier.height(8.dp))
                TrcCheckbox("Listar informações complementares?", impComp) { impComp = it }
                TrcCheckbox("Listar cheques emitidos?", impCheque) { impCheque = it }
                TrcCheckbox("Listar adiantamentos?", impAdt) { impAdt = it }

                Spacer(Modifier.height(20.dp))

                // ── Valores ────���──────────────────────────────────────────────
                TrcLabel("Valores (opcional)")
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
                TrcLabel("Data *")
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("De", style = TextStyle(fontSize = 11.sp, color = TrcSlate))
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(TrcLight).clickable { showDateIni = true }.padding(12.dp)
                        ) {
                            Text("📅 ${trcFmtDisplay(dataInicio)}",
                                style = TextStyle(fontSize = 13.sp, color = TrcDark))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Até", style = TextStyle(fontSize = 11.sp, color = TrcSlate))
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(TrcLight).clickable { showDateFim = true }.padding(12.dp)
                        ) {
                            Text("📅 ${trcFmtDisplay(dataFim)}",
                                style = TextStyle(fontSize = 13.sp, color = TrcDark))
                        }
                    }
                }
                Text("Toque nos campos para selecionar as datas",
                    style = TextStyle(fontSize = 11.sp, color = TrcSlate),
                    modifier = Modifier.padding(top = 4.dp))

                Spacer(Modifier.height(24.dp))

                // ── Mensagens ─────────────────────────────────────────────────
                if (msgErro.isNotEmpty()) {
                    Text(msgErro, style = TextStyle(fontSize = 13.sp, color = TrcRed))
                    Spacer(Modifier.height(8.dp))
                }
                if (msgSucesso.isNotEmpty()) {
                    Text(msgSucesso, style = TextStyle(fontSize = 13.sp, color = TrcGreen))
                    Spacer(Modifier.height(8.dp))
                }

                // ── Botão Gerar PDF ───────────────────────────────────────────
                Button(
                    onClick  = { gerarPdf() },
                    enabled  = !loadingPdf,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = TrcNavy)
                ) {
                    if (loadingPdf) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
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
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { dataInicio = Date(it) }
                        showDateIni = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDateIni = false }) { Text("Cancelar") } }
            ) { DatePicker(state = state) }
        }
        if (showDateFim) {
            val state = rememberDatePickerState(initialSelectedDateMillis = dataFim.time)
            DatePickerDialog(
                onDismissRequest = { showDateFim = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { dataFim = Date(it) }
                        showDateFim = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDateFim = false }) { Text("Cancelar") } }
            ) { DatePicker(state = state) }
        }

        // ── Modal Período ─────────────────────────────────────────────────────
        if (showPeriodoModal) {
            TrcSelectModal(
                title     = "Selecionar Período",
                items     = trcOpcoesPeriodo,
                selected  = periodo,
                onSelect  = { periodo = it; showPeriodoModal = false },
                onDismiss = { showPeriodoModal = false }
            )
        }

        // ── Modal de Clientes ─────────────────────────────────────────────────
        if (showClienteModal) {
            TrcClienteModal(
                searchTerm     = searchCliente,
                onSearchChange = { searchCliente = it },
                clientes       = clientes,
                loading        = loadingClientes,
                onBuscar       = { buscarClientes(searchCliente) },
                onSelecionar   = { c -> clienteSelecionado = c; showClienteModal = false; clientes = emptyList() },
                onDismiss      = { showClienteModal = false; clientes = emptyList() }
            )
        }
    }
}

// ── Modal de Clientes ─────────────────────────────────────────────────────────
@Composable
private fun TrcClienteModal(
    searchTerm: String,
    onSearchChange: (String) -> Unit,
    clientes: List<ClienteTRC>,
    loading: Boolean,
    onBuscar: () -> Unit,
    onSelecionar: (ClienteTRC) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp),
            shape    = RoundedCornerShape(20.dp),
            colors   = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.fillMaxWidth()) {

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Buscar Cliente",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TrcDark))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = TrcSlate)
                    }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Campo de busca
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                        .clip(RoundedCornerShape(12.dp)).background(TrcLight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = TrcSlate,
                        modifier = Modifier.padding(start = 12.dp))
                    TextField(
                        value         = searchTerm,
                        onValueChange = onSearchChange,
                        placeholder   = { Text("Nome, CPF, CNPJ (mín. 3)...",
                            style = TextStyle(fontSize = 13.sp, color = TrcSlate)) },
                        modifier      = Modifier.weight(1f).focusRequester(focusRequester),
                        colors        = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onBuscar() })
                    )
                    TextButton(
                        onClick  = onBuscar,
                        enabled  = searchTerm.trim().length >= 3 && !loading,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text("Buscar",
                            style = TextStyle(
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color      = if (searchTerm.trim().length >= 3) TrcBlue else TrcSlate
                            ))
                    }
                }

                // Contador
                if (clientes.isNotEmpty() && !loading) {
                    Text(
                        "${clientes.size} cliente(s) encontrado(s)",
                        style    = TextStyle(fontSize = 12.sp, color = TrcSlate, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Lista
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    when {
                        loading -> Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = TrcBlue)
                                Spacer(Modifier.height(8.dp))
                                Text("Buscando clientes...",
                                    style = TextStyle(fontSize = 13.sp, color = TrcSlate))
                            }
                        }
                        clientes.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                            Text(
                                if (searchTerm.trim().length < 3) "Digite pelo menos 3 caracteres para buscar"
                                else "Nenhum cliente encontrado",
                                style       = TextStyle(fontSize = 14.sp, color = TrcSlate),
                                textAlign   = TextAlign.Center
                            )
                        }
                        else -> clientes.forEach { c ->
                            TrcClienteItem(c) { onSelecionar(c) }
                            HorizontalDivider(color = TrcLight)
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Composable
private fun TrcClienteItem(c: ClienteTRC, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.nomeRazao.ifEmpty { "Nome não informado" },
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TrcDark))
            if (c.apelidoFantasia.isNotEmpty() && c.apelidoFantasia != c.nomeRazao) {
                Text(c.apelidoFantasia, style = TextStyle(fontSize = 12.sp, color = TrcBlue))
            }
            Text("ID: ${c.idClifor}", style = TextStyle(fontSize = 12.sp, color = TrcSlate))
            if (c.cpfCnpj.isNotEmpty()) {
                Text("CPF/CNPJ: ${c.cpfCnpj}", style = TextStyle(fontSize = 12.sp, color = TrcSlate))
            }
            if (c.telefone.isNotEmpty()) {
                Text("📞 ${c.telefone}", style = TextStyle(fontSize = 12.sp, color = TrcGreen))
            }
        }
        Text("›", style = TextStyle(fontSize = 24.sp, color = TrcBlue, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 8.dp))
    }
}

// ── Modal de seleção simples ──────────────────────────────────────────────────
@Composable
private fun TrcSelectModal(
    title: String,
    items: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(title,
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TrcDark))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = TrcSlate)
                    }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))
                items.forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(value) }
                            .background(if (value == selected) Color(0xFFEFF6FF) else Color.White)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(label,
                            style = TextStyle(
                                fontSize   = 14.sp,
                                color      = if (value == selected) TrcBlue else TrcDark,
                                fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal
                            ))
                        if (value == selected) {
                            Text("✓",
                                style = TextStyle(fontSize = 16.sp, color = TrcBlue, fontWeight = FontWeight.Bold))
                        }
                    }
                    HorizontalDivider(color = TrcLight)
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
@Composable
private fun TrcCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
private fun TrcLabel(text: String) {
    Text(text, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TrcDark))
}

@Composable
private fun TrcCheckbox(label: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheck(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked         = checked,
            onCheckedChange = onCheck,
            colors          = CheckboxDefaults.colors(checkedColor = TrcBlue)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = TextStyle(fontSize = 13.sp, color = TrcDark))
    }
}

private fun trcAbrirPdf(context: Context, bytes: ByteArray, fileName: String): Boolean = try {
    val dir  = File(context.cacheDir, "pdfs").also { it.mkdirs() }
    val file = File(dir, fileName).also { it.writeBytes(bytes) }
    val uri  = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    })
    true
} catch (e: Exception) { android.util.Log.e("TRC_PDF", "Erro: ${e.message}", e); false }

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun TitulosReceberConferenciaScreenPreview() {
    TitulosReceberConferenciaScreen(rememberNavController())
}
