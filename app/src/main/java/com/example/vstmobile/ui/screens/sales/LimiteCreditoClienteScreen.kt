package com.example.vstmobile.ui.screens.sales

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
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.vstmobile.navigation.Screen
import com.example.vstmobile.services.*
import com.example.vstmobile.ui.components.ScreenWithSidebar
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ── Cores ─────────────────────────────────────────────────────────────────────
private val LcBlue  = Color(0xFF3B82F6)
private val LcSlate = Color(0xFF64748B)
private val LcLight = Color(0xFFF1F5F9)
private val LcDark  = Color(0xFF1E293B)
private val LcGreen = Color(0xFF10B981)
private val LcRed   = Color(0xFFEF4444)

private fun fmtMoeda(v: Double): String = try {
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(v)
} catch (_: Exception) { "R$ 0,00" }

private fun fmtApiDate(d: Date): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d)

private fun fmtDisplayDate(d: Date): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d)

// Converte "DD-MM-YYYY" da API para Date
private fun parseApiDate(s: String): Date? = try {
    val parts = s.split("-")
    if (parts.size == 3) {
        val cal = Calendar.getInstance()
        cal.set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt())
        cal.time
    } else null
} catch (_: Exception) { null }

// ── Tela ──────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LimiteCreditoClienteScreen(navController: NavHostController) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val scope   = rememberCoroutineScope()

    val filial = FilialState.selectedFilial

    // Form state
    var pesquisa            by remember { mutableStateOf("") }
    var clienteSelecionado  by remember { mutableStateOf<ClienteLC?>(null) }
    var limiteCredito       by remember { mutableStateOf(0.0) }
    var limiteMaximo        by remember { mutableStateOf(1_000_000.0) }
    var vencimento          by remember { mutableStateOf(Date()) }
    var limiteInput         by remember { mutableStateOf("") }

    // Listas / UI
    var clientes            by remember { mutableStateOf<List<ClienteLC>>(emptyList()) }
    var showModal           by remember { mutableStateOf(false) }
    var showDatePicker      by remember { mutableStateOf(false) }
    var loadingClientes     by remember { mutableStateOf(false) }
    var loadingLimite       by remember { mutableStateOf(false) }
    var loadingSalvar       by remember { mutableStateOf(false) }
    var msgSucesso          by remember { mutableStateOf("") }
    var msgErro             by remember { mutableStateOf("") }
    var showConfirmDialog   by remember { mutableStateOf(false) }

    // Carregar limite máximo ao abrir / trocar filial
    LaunchedEffect(filial?.idFilial) {
        if (filial == null || session.userToken.isEmpty()) return@LaunchedEffect
        val svc = LimiteCreditoService(session.userToken)
        svc.fetchLimiteMaximo(filial.idEmpresa.toString(), filial.idFilial.toString())
            .data?.let { limiteMaximo = it }
    }

    fun limparFormulario() {
        clienteSelecionado = null
        pesquisa = ""
        limiteCredito = 0.0
        limiteInput = ""
        vencimento = Date()
        clientes = emptyList()
        msgSucesso = ""
        msgErro = ""
    }

    fun buscarClientes() {
        if (filial == null) { msgErro = "Selecione uma filial primeiro"; return }
        if (pesquisa.trim().length < 3) { msgErro = "Digite pelo menos 3 caracteres"; return }
        msgErro = ""
        scope.launch {
            loadingClientes = true
            val svc = LimiteCreditoService(session.userToken)
            val result = svc.fetchClientes(filial.idEmpresa.toString(), pesquisa.trim())
            clientes = result.data ?: emptyList()
            if (!result.success) msgErro = result.error ?: "Erro ao buscar clientes"
            showModal = true
            loadingClientes = false
        }
    }

    fun selecionarCliente(c: ClienteLC) {
        clienteSelecionado = c
        showModal = false
        clientes = emptyList()
        pesquisa = ""
        msgErro = ""
        // Carregar limite atual
        scope.launch {
            if (filial == null) return@launch
            loadingLimite = true
            val svc = LimiteCreditoService(session.userToken)
            val result = svc.fetchLimiteAtual(filial.idEmpresa.toString(), c.idClifor)
            result.data?.let { lc ->
                limiteCredito = lc.limiteCredito
                limiteInput = fmtMoedaInput(lc.limiteCredito)
                parseApiDate(lc.venctoLimite)?.let { vencimento = it }
            }
            loadingLimite = false
        }
    }

    fun salvarLimite() {
        if (filial == null || clienteSelecionado == null) return
        scope.launch {
            loadingSalvar = true
            msgErro = ""; msgSucesso = ""
            val svc = LimiteCreditoService(session.userToken)
            val result = svc.salvarLimite(
                idEmpresa     = filial.idEmpresa.toString(),
                idFilial      = filial.idFilial.toString(),
                idClifor      = clienteSelecionado!!.idClifor,
                limiteCredito = limiteCredito,
                venctoLimite  = fmtApiDate(vencimento)
            )
            if (result.success) {
                msgSucesso = "Limite de crédito atualizado com sucesso!"
                limparFormulario()
            } else {
                msgErro = result.error ?: "Erro ao salvar limite"
            }
            loadingSalvar = false
        }
    }

    ScreenWithSidebar(
        navController = navController,
        title = "Limite de Crédito",
        currentRoute = Screen.LimiteCreditoCliente.route
    ) { _ ->

        if (filial == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("💳", fontSize = 48.sp)
                    Text("Selecione uma filial",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LcDark))
                    Text("Abra o menu lateral e selecione\numa filial para configurar limites.",
                        style = TextStyle(fontSize = 14.sp, color = LcSlate), textAlign = TextAlign.Center)
                }
            }
            return@ScreenWithSidebar
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LcLight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Card principal ────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

                    Text("Configurar Limite de Crédito",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LcDark))
                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Campo de cliente ──────────────────────────────────────
                    Text("Cliente", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LcDark))
                    Spacer(modifier = Modifier.height(6.dp))

                    if (clienteSelecionado != null) {
                        // Cliente selecionado — exibir info + botão limpar
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF0FFF4))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(clienteSelecionado!!.nomeRazao,
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LcDark))
                                if (clienteSelecionado!!.apelidoFantasia.isNotEmpty() &&
                                    clienteSelecionado!!.apelidoFantasia != clienteSelecionado!!.nomeRazao) {
                                    Text(clienteSelecionado!!.apelidoFantasia,
                                        style = TextStyle(fontSize = 12.sp, color = LcBlue))
                                }
                                Text("${clienteSelecionado!!.cpfCnpj} • Cód: ${clienteSelecionado!!.idClifor}",
                                    style = TextStyle(fontSize = 12.sp, color = LcSlate))
                                if (loadingLimite) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = LcBlue, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Carregando limite atual...", style = TextStyle(fontSize = 11.sp, color = LcSlate))
                                    }
                                }
                            }
                            IconButton(onClick = { limparFormulario() }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpar", tint = LcSlate)
                            }
                        }
                    } else {
                        // Campo de busca
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(LcLight),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = pesquisa,
                                onValueChange = { pesquisa = it; msgErro = "" },
                                placeholder = { Text("Nome, CPF, CNPJ ou código...", style = TextStyle(fontSize = 13.sp, color = LcSlate)) },
                                modifier = Modifier.weight(1f),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { buscarClientes() })
                            )
                            IconButton(
                                onClick = { buscarClientes() },
                                enabled = !loadingClientes && pesquisa.trim().length >= 3
                            ) {
                                if (loadingClientes) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = LcBlue, strokeWidth = 2.dp)
                                else Icon(Icons.Default.Search, contentDescription = "Buscar", tint = if (pesquisa.trim().length >= 3) LcBlue else LcSlate)
                            }
                        }
                        if (msgErro.isNotEmpty()) {
                            Text(msgErro, style = TextStyle(fontSize = 12.sp, color = LcRed), modifier = Modifier.padding(top = 4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── Limite de Crédito ─────────────────────────────────────
                    Text("Limite de Crédito: ${fmtMoeda(limiteCredito)}",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LcDark))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Input manual
                    OutlinedTextField(
                        value = limiteInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }
                            val valor = (digits.toLongOrNull() ?: 0L) / 100.0
                            val limitado = valor.coerceAtMost(limiteMaximo)
                            limiteCredito = limitado
                            limiteInput = fmtMoedaInput(limitado)
                        },
                        label = { Text("Digite o valor") },
                        placeholder = { Text("R$ 0,00") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LcBlue, textAlign = TextAlign.Center)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Slider
                    Slider(
                        value = limiteCredito.toFloat(),
                        onValueChange = { v ->
                            limiteCredito = v.toDouble()
                            limiteInput = fmtMoedaInput(v.toDouble())
                        },
                        valueRange = 0f..limiteMaximo.toFloat(),
                        steps = 0,
                        colors = SliderDefaults.colors(
                            thumbColor = LcBlue,
                            activeTrackColor = LcBlue,
                            inactiveTrackColor = Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("R$ 0", style = TextStyle(fontSize = 11.sp, color = LcSlate))
                        Text(fmtMoeda(limiteMaximo), style = TextStyle(fontSize = 11.sp, color = LcSlate))
                    }
                    Text("Valor atual: ${fmtMoeda(limiteCredito)}",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LcBlue, textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth())
                    Text("Limite máximo permitido: ${fmtMoeda(limiteMaximo)}",
                        style = TextStyle(fontSize = 11.sp, color = LcSlate, textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── Vencimento ────────────────────────────────────────────
                    Text("Vencimento do Limite de Crédito",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LcDark))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(LcLight)
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📅 ${fmtDisplayDate(vencimento)}", style = TextStyle(fontSize = 14.sp, color = LcDark))
                        Text("Alterar", style = TextStyle(fontSize = 12.sp, color = LcBlue))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Mensagens ─────────────────────────────────────────────
                    if (msgSucesso.isNotEmpty()) {
                        Text("✅ $msgSucesso", style = TextStyle(fontSize = 13.sp, color = LcGreen))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (msgErro.isNotEmpty() && clienteSelecionado != null) {
                        Text("⚠️ $msgErro", style = TextStyle(fontSize = 13.sp, color = LcRed))
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // ── Botão Confirmar ───────────────────────────────────────
                    Button(
                        onClick = { showConfirmDialog = true },
                        enabled = clienteSelecionado != null && !loadingSalvar,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LcGreen,
                            disabledContainerColor = Color(0xFFCBD5E1)
                        )
                    ) {
                        if (loadingSalvar) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("✓ Confirmar", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White))
                        }
                    }
                }
            }
        }

        // ── Modal de busca de clientes ────────────────────────────────────────
        if (showModal) {
            LcClienteModal(
                pesquisa        = pesquisa,
                onPesquisaChange = { pesquisa = it },
                clientes        = clientes,
                loading         = loadingClientes,
                onBuscar        = { buscarClientes() },
                onSelecionar    = { selecionarCliente(it) },
                onDismiss       = { showModal = false; clientes = emptyList() }
            )
        }

        // ── DatePicker ────────────────────────────────────────────────────────
        if (showDatePicker) {
            val state = rememberDatePickerState(initialSelectedDateMillis = vencimento.time)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { vencimento = Date(it) }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
            ) { DatePicker(state = state) }
        }

        // ── Dialog de confirmação ─────────────────────────────────────────────
        if (showConfirmDialog && clienteSelecionado != null) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("Confirmar Limite") },
                text = {
                    Text("Deseja confirmar o limite de ${fmtMoeda(limiteCredito)} para o cliente ${clienteSelecionado!!.nomeRazao}?")
                },
                confirmButton = {
                    TextButton(onClick = { showConfirmDialog = false; salvarLimite() }) {
                        Text("Confirmar", color = LcGreen, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) { Text("Cancelar") }
                }
            )
        }
    }
}

// ── Modal de clientes ─────────────────────────────────────────────────────────
@Composable
private fun LcClienteModal(
    pesquisa: String,
    onPesquisaChange: (String) -> Unit,
    clientes: List<ClienteLC>,
    loading: Boolean,
    onBuscar: () -> Unit,
    onSelecionar: (ClienteLC) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Buscar Cliente",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LcDark))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = LcSlate)
                    }
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Campo de busca
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LcLight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = LcSlate,
                        modifier = Modifier.padding(start = 12.dp))
                    TextField(
                        value = pesquisa,
                        onValueChange = onPesquisaChange,
                        placeholder = { Text("Nome, CPF, CNPJ ou código (min. 3)...", style = TextStyle(fontSize = 13.sp, color = LcSlate)) },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onBuscar() })
                    )
                    TextButton(
                        onClick = onBuscar,
                        enabled = pesquisa.trim().length >= 3,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text("Buscar", style = TextStyle(color = if (pesquisa.trim().length >= 3) LcBlue else LcSlate, fontWeight = FontWeight.Bold))
                    }
                }

                // Resultado
                if (clientes.isNotEmpty()) {
                    Text("${clientes.size} cliente(s) encontrado(s)",
                        style = TextStyle(fontSize = 12.sp, color = LcSlate, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }

                // Lista
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .heightIn(max = 400.dp)
                ) {
                    when {
                        loading -> {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = LcBlue)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Buscando clientes...", style = TextStyle(fontSize = 13.sp, color = LcSlate))
                                }
                            }
                        }
                        clientes.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (pesquisa.trim().length < 3) "Digite pelo menos 3 caracteres para buscar"
                                    else "Nenhum cliente encontrado",
                                    style = TextStyle(fontSize = 14.sp, color = LcSlate),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        else -> {
                            clientes.forEach { c ->
                                LcClienteItem(cliente = c, onClick = { onSelecionar(c) })
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Composable
private fun LcClienteItem(cliente: ClienteLC, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(cliente.nomeRazao,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LcDark))
                if (cliente.apelidoFantasia.isNotEmpty() && cliente.apelidoFantasia != cliente.nomeRazao) {
                    Text(cliente.apelidoFantasia,
                        style = TextStyle(fontSize = 13.sp, color = LcBlue))
                }
                Text("${cliente.cpfCnpj.ifEmpty { "" }} • Cód: ${cliente.idClifor}",
                    style = TextStyle(fontSize = 12.sp, color = LcSlate))
                if (cliente.telefone.isNotEmpty()) {
                    Text("📞 ${cliente.telefone}",
                        style = TextStyle(fontSize = 12.sp, color = LcGreen))
                }
            }
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFEFF6FF)),
                contentAlignment = Alignment.Center
            ) {
                Text("›", style = TextStyle(fontSize = 20.sp, color = LcBlue, fontWeight = FontWeight.Bold))
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun fmtMoedaInput(v: Double): String {
    val cents = (v * 100).toLong()
    return "%.2f".format(cents / 100.0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun LimiteCreditoClienteScreenPreview() {
    LimiteCreditoClienteScreen(rememberNavController())
}
