package com.example.vstmobile.ui.screens.sales

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private val ApoNavy  = Color(0xFF1E3A8A)
private val ApoBlue  = Color(0xFF3B82F6)
private val ApoSlate = Color(0xFF64748B)
private val ApoLight = Color(0xFFF1F5F9)
private val ApoDark  = Color(0xFF1E293B)
private val ApoGreen = Color(0xFF10B981)
private val ApoRed   = Color(0xFFEF4444)
private val ApoAmber = Color(0xFFF59E0B)
private val ApoGray  = Color(0xFF9E9E9E)

private fun apoBRL(v: Double) = try {
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(v)
} catch (_: Exception) { "R$ 0,00" }

private fun apoFmtApiDate(d: Date) = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d)
private fun apoFmtDisplay(d: Date) = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d)

private fun statusColor(s: String) = when (s) {
    "Aprovada"  -> Color(0xFF4CAF50)
    "Pendente"  -> Color(0xFFFF9800)
    "Rejeitada" -> Color(0xFFF44336)
    else        -> Color(0xFF9E9E9E)
}

private fun statusLabel(s: String) = when (s) {
    "Aprovada"  -> "✅ Aprovada"
    "Pendente"  -> "⏳ Pendente"
    "Rejeitada" -> "❌ Rejeitada"
    else        -> "❓ Indefinido"
}

private val statusOpcoes = listOf("Todas", "Aprovadas", "Pendentes", "Rejeitadas")

// ── Tela ──────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AprovacaoPedidoOrcamentoScreen(navController: NavHostController) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val scope   = rememberCoroutineScope()

    // Filtros
    var status              by remember { mutableStateOf("Todas") }
    var vendedorSelecionado by remember { mutableStateOf("Todos") }
    var dataInicio          by remember { mutableStateOf(Date()) }
    var dataFim             by remember { mutableStateOf(Date()) }
    var filtrosExpandidos   by remember { mutableStateOf(true) }

    // Dados
    var pedidos             by remember { mutableStateOf<List<PedidoOrcamento>>(emptyList()) }
    var vendedores          by remember { mutableStateOf<List<Vendedor>>(emptyList()) }
    var itemExpandido       by remember { mutableStateOf<String?>(null) }
    var buscaRealizada      by remember { mutableStateOf(false) }

    // Loading / feedback
    var loading             by remember { mutableStateOf(false) }
    var msgErro             by remember { mutableStateOf("") }

    // Modais
    var showStatus          by remember { mutableStateOf(false) }
    var showVendedor        by remember { mutableStateOf(false) }
    var showFiliais         by remember { mutableStateOf(false) }
    var showDateIni         by remember { mutableStateOf(false) }
    var showDateFim         by remember { mutableStateOf(false) }
    var showConfirmAprovar  by remember { mutableStateOf(false) }
    var showConfirmRejeitar by remember { mutableStateOf(false) }
    var pedidoSelecionado   by remember { mutableStateOf<PedidoOrcamento?>(null) }

    val filiais          = FilialState.filiais
    val selectedFiliais  = FilialState.selectedFiliais
    val filial           = FilialState.selectedFilial

    // Carregar vendedores ao trocar filial
    LaunchedEffect(filial?.idEmpresa) {
        if (filial == null || session.userToken.isEmpty()) return@LaunchedEffect
        val svc = AnaliseVendaProdutoService(session.userToken)
        svc.fetchVendedores(filial.idEmpresa.toString()).data?.let { vendedores = it }
    }

    fun aplicarFiltros(lista: List<PedidoOrcamento>): List<PedidoOrcamento> {
        var f = lista
        if (status != "Todas") {
            f = f.filter { p ->
                (status == "Aprovadas"  && p.status == "Aprovada")  ||
                (status == "Pendentes"  && p.status == "Pendente")  ||
                (status == "Rejeitadas" && p.status == "Rejeitada")
            }
        }
        if (vendedorSelecionado != "Todos") {
            f = f.filter { it.vendedor.contains(vendedorSelecionado) }
        }
        return f
    }

    fun buscar() {
        if (selectedFiliais.isEmpty()) { msgErro = "Selecione pelo menos uma filial"; return }
        msgErro = ""
        scope.launch {
            loading = true
            buscaRealizada = true
            val svc = AprovacaoService(session.userToken)
            val todos = mutableListOf<PedidoOrcamento>()
            for (f in selectedFiliais) {
                val r = svc.fetchPedidos(f.idEmpresa, f.idFilial, apoFmtApiDate(dataInicio), apoFmtApiDate(dataFim))
                r.data?.let { todos.addAll(it) }
            }
            pedidos = aplicarFiltros(todos)
            loading = false
        }
    }

    fun aprovar() {
        val p = pedidoSelecionado ?: return
        scope.launch {
            loading = true
            val svc = AprovacaoService(session.userToken)
            val r = svc.aprovarPedido(p.idEmpresa, p.idFilial, p.idSaida)
            showConfirmAprovar = false
            pedidoSelecionado = null
            if (r.success) buscar() else msgErro = r.error ?: "Erro ao aprovar"
            loading = false
        }
    }

    fun rejeitar() {
        val p = pedidoSelecionado ?: return
        scope.launch {
            loading = true
            val svc = AprovacaoService(session.userToken)
            val r = svc.rejeitarPedido(p.idEmpresa, p.idFilial, p.idSaida)
            showConfirmRejeitar = false
            pedidoSelecionado = null
            if (r.success) buscar() else msgErro = r.error ?: "Erro ao rejeitar"
            loading = false
        }
    }

    ScreenWithSidebar(
        navController = navController,
        title = "Aprovação de Pedidos",
        currentRoute = Screen.AprovacaoPedidoOrcamento.route
    ) { _ ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ApoLight)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {

            // ── Card Filtros ──────────────────────────────────────────────────
            ApoCard(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {

                // Header expansível
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { filtrosExpandidos = !filtrosExpandidos },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filtros", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ApoDark))
                    Icon(
                        if (filtrosExpandidos) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null, tint = ApoBlue
                    )
                }

                if (filtrosExpandidos) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Filiais
                    ApoFilterRow(
                        label = "Filiais",
                        value = when {
                            selectedFiliais.isEmpty() -> ""
                            selectedFiliais.size == 1 -> {
                                val f = selectedFiliais.first()
                                if (f.identificacaoInterna.isNotEmpty()) "${f.identificacaoInterna} | ID: ${f.idFilial}"
                                else "ID: ${f.idFilial}"
                            }
                            else -> "${selectedFiliais.size} filiais selecionadas"
                        },
                        placeholder = "Selecionar filiais",
                        onClick = { showFiliais = true }
                    )

                    // Status
                    ApoFilterRow(
                        label = "Status",
                        value = status,
                        placeholder = "Selecionar status",
                        onClick = { showStatus = true }
                    )

                    // Vendedor
                    ApoFilterRow(
                        label = "Vendedor",
                        value = if (vendedorSelecionado == "Todos") "Todos os Vendedores"
                                else vendedores.find { it.id.toString() == vendedorSelecionado }?.nomeFuncionario ?: vendedorSelecionado,
                        placeholder = "Selecionar vendedor",
                        onClick = { showVendedor = true }
                    )

                    // Período
                    Text("Período", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ApoDark))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("De", style = TextStyle(fontSize = 11.sp, color = ApoSlate))
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(ApoLight).clickable { showDateIni = true }.padding(12.dp)) {
                                Text("📅 ${apoFmtDisplay(dataInicio)}", style = TextStyle(fontSize = 13.sp, color = ApoDark))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Até", style = TextStyle(fontSize = 11.sp, color = ApoSlate))
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(ApoLight).clickable { showDateFim = true }.padding(12.dp)) {
                                Text("📅 ${apoFmtDisplay(dataFim)}", style = TextStyle(fontSize = 13.sp, color = ApoDark))
                            }
                        }
                    }

                    if (msgErro.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(msgErro, style = TextStyle(fontSize = 12.sp, color = ApoRed))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { buscar() },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ApoNavy)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (loading) "Buscando..." else "🔍 Buscar",
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White))
                    }
                }
            }

            // ── Resultados ────────────────────────────────────────────────────
            ApoCard(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {

                if (!buscaRealizada) {
                    // Estado inicial
                    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔍", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Nenhuma busca realizada",
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ApoDark))
                        Text("Configure os filtros e clique em Buscar",
                            style = TextStyle(fontSize = 13.sp, color = ApoSlate), textAlign = TextAlign.Center)
                    }
                } else if (pedidos.isEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📭", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Nenhum pedido encontrado",
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ApoDark))
                        Text("Tente ajustar os filtros e buscar novamente.",
                            style = TextStyle(fontSize = 13.sp, color = ApoSlate), textAlign = TextAlign.Center)
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Resultados", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ApoDark))
                        Text("${pedidos.size} item(ns)", style = TextStyle(fontSize = 13.sp, color = ApoSlate))
                    }
                    Text("Toque para expandir detalhes",
                        style = TextStyle(fontSize = 11.sp, color = ApoSlate),
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))

                    // Cabeçalho da tabela
                    ApoTableHeader()
                    HorizontalDivider(color = ApoLight)

                    pedidos.forEach { pedido ->
                        ApoPedidoRow(
                            pedido = pedido,
                            isExpanded = itemExpandido == pedido.idSaida.toString(),
                            onToggle = {
                                itemExpandido = if (itemExpandido == pedido.idSaida.toString()) null
                                               else pedido.idSaida.toString()
                            },
                            onAprovar = { pedidoSelecionado = pedido; showConfirmAprovar = true },
                            onRejeitar = { pedidoSelecionado = pedido; showConfirmRejeitar = true }
                        )
                        HorizontalDivider(color = ApoLight)
                    }
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

        // ── Modal Filiais ─────────────────────────────────────────────────────
        if (showFiliais) {
            AvpSelectModal(
                title = "Selecionar Filiais",
                items = filiais.map { f ->
                    val lbl = if (f.identificacaoInterna.isNotEmpty()) "${f.identificacaoInterna} | ID: ${f.idFilial}" else "ID: ${f.idFilial}"
                    f.idFilial.toString() to lbl
                },
                selectedValues = selectedFiliais.map { it.idFilial.toString() }.toSet(),
                multiSelect = true,
                onDismiss = { showFiliais = false },
                onSelect = { value -> filiais.find { it.idFilial.toString() == value }?.let { FilialState.toggleFilial(it) } }
            )
        }

        // ── Modal Status ──────────────────────────────────────────────────────
        if (showStatus) {
            AvpSelectModal(
                title = "Status",
                items = statusOpcoes.map { it to it },
                selectedValues = setOf(status),
                onDismiss = { showStatus = false },
                onSelect = { status = it; showStatus = false }
            )
        }

        // ── Modal Vendedor ────────────────────────────────────────────────────
        if (showVendedor) {
            AvpSelectModal(
                title = "Vendedor",
                items = listOf("Todos" to "Todos os Vendedores") + vendedores.map { it.id.toString() to it.nomeFuncionario },
                selectedValues = setOf(vendedorSelecionado),
                onDismiss = { showVendedor = false },
                onSelect = { vendedorSelecionado = it; showVendedor = false }
            )
        }

        // ── Confirm Aprovar ───────────────────────────────────────────────────
        if (showConfirmAprovar && pedidoSelecionado != null) {
            AlertDialog(
                onDismissRequest = { showConfirmAprovar = false },
                title = { Text("✅ Confirmar Aprovação") },
                text = { Text("Deseja aprovar o pedido ${pedidoSelecionado!!.numero}?\n\nEsta ação não poderá ser desfeita.") },
                confirmButton = {
                    TextButton(onClick = { aprovar() }) {
                        Text("Aprovar", color = ApoGreen, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = { TextButton(onClick = { showConfirmAprovar = false }) { Text("Cancelar") } }
            )
        }

        // ── Confirm Rejeitar ──────────────────────────────────────────────────
        if (showConfirmRejeitar && pedidoSelecionado != null) {
            AlertDialog(
                onDismissRequest = { showConfirmRejeitar = false },
                title = { Text("❌ Confirmar Rejeição") },
                text = { Text("Deseja rejeitar o pedido ${pedidoSelecionado!!.numero}?\n\nEsta ação não poderá ser desfeita.") },
                confirmButton = {
                    TextButton(onClick = { rejeitar() }) {
                        Text("Rejeitar", color = ApoRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = { TextButton(onClick = { showConfirmRejeitar = false }) { Text("Cancelar") } }
            )
        }
    }
}

// ── Componentes ───────────────────────────────────────────────────────────────

@Composable
private fun ApoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) { Column(modifier = Modifier.fillMaxWidth().padding(16.dp), content = content) }
}

@Composable
private fun ApoFilterRow(label: String, value: String, placeholder: String, onClick: () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(label, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ApoDark))
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(ApoLight).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (value.isNotEmpty()) value else placeholder,
                style = TextStyle(fontSize = 13.sp, color = if (value.isNotEmpty()) ApoDark else ApoSlate),
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = ApoSlate, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ApoTableHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Número",   modifier = Modifier.weight(1.4f), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ApoSlate))
        Text("Cliente",  modifier = Modifier.weight(2f),   style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ApoSlate))
        Text("Valor",    modifier = Modifier.weight(1.5f), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ApoSlate))
        Text("Tipo",     modifier = Modifier.weight(1.2f), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ApoSlate))
        Text("Status",   modifier = Modifier.weight(0.6f), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ApoSlate))
        Spacer(modifier = Modifier.width(24.dp))
    }
}

@Composable
private fun ApoPedidoRow(
    pedido: PedidoOrcamento,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onAprovar: () -> Unit,
    onRejeitar: () -> Unit
) {
    Column {
        // ── Linha da tabela ───────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(if (isExpanded) Color(0xFFEFF6FF) else Color.White)
                .clickable { onToggle() }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(pedido.numero, modifier = Modifier.weight(1.4f),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ApoDark))
            Text(pedido.cliente, modifier = Modifier.weight(2f),
                style = TextStyle(fontSize = 11.sp, color = ApoDark), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(apoBRL(pedido.valor), modifier = Modifier.weight(1.5f),
                style = TextStyle(fontSize = 11.sp, color = ApoDark))
            Text(pedido.tipo.take(10), modifier = Modifier.weight(1.2f),
                style = TextStyle(fontSize = 11.sp, color = ApoSlate), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                modifier = Modifier.weight(0.6f).size(12.dp)
                    .clip(RoundedCornerShape(6.dp)).background(statusColor(pedido.status))
            )
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, tint = ApoSlate, modifier = Modifier.size(20.dp)
            )
        }

        // ── Detalhes expandidos ───────────────────────────────────────────────
        if (isExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(Color(0xFFF8FAFC))
                    .padding(12.dp)
            ) {
                // Valores financeiros em destaque
                val lucroBruto = pedido.valor - pedido.custoAquisicao
                val margem = if (pedido.valor > 0) (lucroBruto / pedido.valor) * 100 else 0.0
                val valorDesconto = pedido.valor * (pedido.percDesconto / 100)

                Card(shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ApoDetalheItem("Sub-Total", apoBRL(pedido.valorSubtotal))
                        ApoDetalheItem("Desconto", apoBRL(valorDesconto))
                        ApoDetalheItem("Desc. %", "%.1f%%".format(pedido.percDesconto))
                        ApoDetalheItem("Valor Venda", apoBRL(pedido.valor), isDestaque = true)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Lucro / Margem
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApoInfoCard("Custo Total", apoBRL(pedido.custoAquisicao), modifier = Modifier.weight(1f))
                    ApoInfoCard("Lucro Bruto", apoBRL(lucroBruto), color = if (lucroBruto >= 0) ApoGreen else ApoRed, modifier = Modifier.weight(1f))
                    ApoInfoCard("Margem", "%.1f%%".format(margem), color = if (margem >= 0) ApoGreen else ApoRed, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Desconto participante / não participante
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApoInfoCard("c/ Desc.", apoBRL(pedido.vlrParticipaDesc), modifier = Modifier.weight(1f))
                    ApoInfoCard("s/ Desc.", apoBRL(pedido.vlrNaoParticipaDesc), modifier = Modifier.weight(1f))
                    ApoInfoCard("Forma Desc.", pedido.formaDesconto, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Plano / Desc. Plano
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApoInfoCard("Plano Receb.", pedido.planoPagamento, modifier = Modifier.weight(2f))
                    ApoInfoCard("Desc. Plano", "%.1f%%".format(pedido.percEntrada), modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Info extra
                Column(modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)).background(Color.White).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ApoInfoLine("Vendedor", pedido.vendedor)
                    ApoInfoLine("Filial", pedido.filial)
                    ApoInfoLine("Data", pedido.data)
                    ApoInfoLine("Status", statusLabel(pedido.status))
                    if (pedido.observacao.isNotEmpty()) ApoInfoLine("Observação", pedido.observacao)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Botões de ação
                val isPendente = pedido.resultadoAprovacao == "1"
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAprovar,
                        enabled = isPendente,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ApoGreen,
                            disabledContainerColor = ApoLight
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aprovar", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (isPendente) Color.White else ApoSlate))
                    }
                    Button(
                        onClick = onRejeitar,
                        enabled = isPendente,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ApoRed,
                            disabledContainerColor = ApoLight
                        )
                    ) {
                        Text("✕", style = TextStyle(fontSize = 13.sp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rejeitar", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (isPendente) Color.White else ApoSlate))
                    }
                }
            }
        }
    }
}

@Composable
private fun ApoDetalheItem(label: String, value: String, isDestaque: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = TextStyle(fontSize = 10.sp, color = ApoSlate))
        Text(value, style = TextStyle(fontSize = if (isDestaque) 13.sp else 12.sp,
            fontWeight = if (isDestaque) FontWeight.Bold else FontWeight.Normal,
            color = if (isDestaque) ApoBlue else ApoDark))
    }
}

@Composable
private fun ApoInfoCard(label: String, value: String, color: Color = ApoDark, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, style = TextStyle(fontSize = 10.sp, color = ApoSlate))
            Text(value, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ApoInfoLine(label: String, value: String) {
    Row {
        Text("$label: ", style = TextStyle(fontSize = 12.sp, color = ApoSlate, fontWeight = FontWeight.SemiBold))
        Text(value, style = TextStyle(fontSize = 12.sp, color = ApoDark))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AprovacaoPedidoOrcamentoScreenPreview() {
    AprovacaoPedidoOrcamentoScreen(rememberNavController())
}
