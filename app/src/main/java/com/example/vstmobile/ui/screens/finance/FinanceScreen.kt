package com.example.vstmobile.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.vstmobile.services.*
import com.example.vstmobile.ui.components.SidebarLayout
import com.example.vstmobile.ui.components.TopBar
import com.example.vstmobile.ui.theme.LightBlue
import com.example.vstmobile.ui.theme.PrimaryBlue
import com.example.vstmobile.ui.theme.White
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val FinGreen = Color(0xFF10B981)
private val FinGreenLight = Color(0xFFDCFCE7)
private val FinGreenDark = Color(0xFF065F46)
private val FinRed = Color(0xFFEF4444)
private val FinRedLight = Color(0xFFFEE2E2)
private val FinRedDark = Color(0xFF7F1D1D)
private val FinSlateLight = Color(0xFFF8FAFC)
private val FinSlate = Color(0xFF64748B)
private val FinNavy = Color(0xFF1E3A8A)

fun formatCurrency(value: Double): String = try {
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value)
} catch (_: Exception) { "R$ 0,00" }

@Composable
fun FinanceScreen(navController: NavHostController) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val coroutineScope = rememberCoroutineScope()

    var saldoTotal by remember { mutableStateOf(0.0) }
    var bancos by remember { mutableStateOf<List<BancoSaldo>>(emptyList()) }
    var dashboard by remember { mutableStateOf(DashboardData()) }
    var fluxoCaixa by remember { mutableStateOf<List<FluxoCaixaMes>>(emptyList()) }
    var graficos by remember { mutableStateOf(GraficosData()) }
    var ranking by remember { mutableStateOf<RankingInadimplencia?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showAllClientes by remember { mutableStateOf(false) }
    var showAllVendedores by remember { mutableStateOf(false) }

    val filial = FilialState.selectedFilial

    LaunchedEffect(filial?.idFilial) {
        if (filial == null || session.userToken.isEmpty()) return@LaunchedEffect
        val service = FinanceService(session.userToken)
        val idEmpresa = filial.idEmpresa
        val idFilial = filial.idFilial
        isLoading = true
        coroutineScope.launch {
            service.fetchSaldosBancarios(idEmpresa).data?.let {
                saldoTotal = it.first
                bancos = it.second
            }
            service.fetchPagarReceber(idEmpresa, idFilial).data?.let { dashboard = it }
            launch { service.fetchFluxoCaixa(idEmpresa, idFilial).data?.let { fluxoCaixa = it } }
            launch { service.fetchGraficos(idEmpresa, idFilial).data?.let { graficos = it } }
            launch { service.fetchRankingInadimplencia(idEmpresa, idFilial).data?.let { ranking = it } }
            isLoading = false
        }
    }

    SidebarLayout(navController = navController, currentRoute = "Finance") { onMenuClick ->
        Column(modifier = Modifier.fillMaxSize().background(LightBlue)) {
            TopBar(title = "Financeiro", onMenuClick = onMenuClick)

            if (filial == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📊", fontSize = 48.sp)
                        Text(
                            "Selecione uma filial",
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                        )
                        Text(
                            "Abra o menu lateral e selecione\numa filial para ver os dados.",
                            style = TextStyle(fontSize = 14.sp, color = FinSlate),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Resumo
                    FinSectionTitle("Resumo Financeiro")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FinBanner("A Receber Vencido", dashboard.recebervencido, FinGreenLight, FinGreen, FinGreenDark, modifier = Modifier.weight(1f))
                        FinBanner("A Receber Hoje", dashboard.receberdodia, FinGreenLight, FinGreen, FinGreenDark, modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FinBanner("A Pagar Vencido", dashboard.pagarvencido, FinRedLight, FinRed, FinRedDark, modifier = Modifier.weight(1f))
                        FinBanner("A Pagar Hoje", dashboard.pagardodia, FinRedLight, FinRed, FinRedDark, modifier = Modifier.weight(1f))
                    }

                    // Saldo em Banco
                    FinCard {
                        FinSectionTitle("Saldo em Banco", padded = false)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            formatCurrency(saldoTotal),
                            style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = FinNavy),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            "Saldo Total",
                            style = TextStyle(fontSize = 12.sp, color = FinSlate),
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
                        )
                        if (bancos.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            bancos.chunked(2).forEach { rowBancos ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowBancos.forEach { banco ->
                                        Column(
                                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(FinSlateLight).padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(banco.descBanco, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinNavy), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                            Text(formatCurrency(banco.saldo), style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3B82F6)), modifier = Modifier.padding(top = 4.dp))
                                            Text("Saldo", style = TextStyle(fontSize = 10.sp, color = FinSlate))
                                        }
                                    }
                                    if (rowBancos.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    // Pagar/Receber
                    FinCard {
                        FinSectionTitle("Contas a Pagar e Receber", padded = false)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(FinRedLight).padding(12.dp)) {
                                Text("A Pagar", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinRed))
                                Spacer(modifier = Modifier.height(8.dp))
                                FinPagarReceberRow("Hoje", dashboard.pagardodia, FinRed)
                                FinPagarReceberRow("Restante mes", dashboard.pagarrestantemes, FinRed)
                                FinPagarReceberRow("Vencidos", dashboard.pagarvencido, FinRed)
                            }
                            Column(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(FinGreenLight).padding(12.dp)) {
                                Text("A Receber", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinGreen))
                                Spacer(modifier = Modifier.height(8.dp))
                                FinPagarReceberRow("Hoje", dashboard.receberdodia, FinGreen)
                                FinPagarReceberRow("Restante mes", dashboard.receberrestantemes, FinGreen)
                                FinPagarReceberRow("Vencidos", dashboard.recebervencido, FinGreen)
                            }
                        }
                    }

                    // Fluxo de Caixa
                    FinCard {
                        FinSectionTitle("Fluxo de Caixa Mensal", padded = false)
                        Spacer(modifier = Modifier.height(12.dp))
                        when {
                            isLoading -> FinLoadingRow()
                            fluxoCaixa.isEmpty() -> FinEmptyRow("Nenhum dado de fluxo mensal")
                            else -> Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                fluxoCaixa.forEach { mes ->
                                    Column(modifier = Modifier.width(180.dp).clip(RoundedCornerShape(12.dp)).background(FinSlateLight).padding(14.dp)) {
                                        Text(mes.nomeMes, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinNavy))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        FinFluxoRow("Saldo Inicial", mes.saldoInicial, FinSlate)
                                        FinFluxoRow("Receber", mes.receber, FinGreen)
                                        FinFluxoRow("Pagar", mes.pagar, FinRed)
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color(0xFFE2E8F0))
                                        FinFluxoRow("Saldo Final", mes.saldoFinal, FinNavy, bold = true)
                                    }
                                }
                            }
                        }
                    }

                    // Ranking Inadimplencia
                    val rankingData = ranking
                    if (isLoading) {
                        FinCard {
                            FinSectionTitle("Inadimplencia", padded = false)
                            Spacer(modifier = Modifier.height(8.dp))
                            FinLoadingRow()
                        }
                    } else if (rankingData != null && (rankingData.clientes.isNotEmpty() || rankingData.vendedores.isNotEmpty())) {
                        FinCard {
                            FinSectionTitle("Inadimplencia", padded = false)
                            if (rankingData.clientes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Top Clientes Inadimplentes", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinRed))
                                val listaClientes = if (showAllClientes) rankingData.clientes else rankingData.clientes.take(5)
                                listaClientes.forEachIndexed { i, c ->
                                    FinInadimplenciaRow(c.nome, "${c.qtd} titulos  #${i + 1}", formatCurrency(c.saldo), FinRed)
                                }
                                if (rankingData.clientes.size > 5) {
                                    TextButton(onClick = { showAllClientes = !showAllClientes }) {
                                        Text(if (showAllClientes) "Mostrar menos" else "Ver todos os clientes", color = Color(0xFF3B82F6))
                                    }
                                }
                            }
                            if (rankingData.vendedores.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Top Vendedores com Inadimplencia", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B)))
                                val listaVend = if (showAllVendedores) rankingData.vendedores else rankingData.vendedores.take(5)
                                listaVend.forEachIndexed { i, v ->
                                    FinInadimplenciaRow(v.nome, "${v.qtd} titulos  #${i + 1}", formatCurrency(v.saldo), Color(0xFFF59E0B))
                                }
                                if (rankingData.vendedores.size > 5) {
                                    TextButton(onClick = { showAllVendedores = !showAllVendedores }) {
                                        Text(if (showAllVendedores) "Mostrar menos" else "Ver todos os vendedores", color = Color(0xFF3B82F6))
                                    }
                                }
                            }
                        }
                    }

                    // Analise por Tipo de Documento
                    if (isLoading) {
                        FinCard {
                            FinSectionTitle("Analise por Tipo de Documento", padded = false)
                            Spacer(modifier = Modifier.height(8.dp))
                            FinLoadingRow()
                        }
                    } else if (graficos.receberTipoDocto.isNotEmpty() || graficos.pagarTipoDocto.isNotEmpty()) {
                        FinCard {
                            FinSectionTitle("Analise por Tipo de Documento", padded = false)
                            if (graficos.receberTipoDocto.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Titulos a Receber por Tipo", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinGreen))
                                Spacer(modifier = Modifier.height(8.dp))
                                FinBarList(graficos.receberTipoDocto.take(10).map { it.tipo to it.saldo }, FinGreen)
                            }
                            if (graficos.pagarTipoDocto.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Titulos a Pagar por Tipo", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinRed))
                                Spacer(modifier = Modifier.height(8.dp))
                                FinBarList(graficos.pagarTipoDocto.take(10).map { it.tipo to it.saldo }, FinRed)
                            }
                        }
                    }

                    // Analise por Local de Cobranca
                    if (isLoading) {
                        FinCard {
                            FinSectionTitle("Analise por Local de Cobranca", padded = false)
                            Spacer(modifier = Modifier.height(8.dp))
                            FinLoadingRow()
                        }
                    } else if (graficos.receberLocalCobranca.isNotEmpty() || graficos.pagarLocalCobranca.isNotEmpty()) {
                        FinCard {
                            FinSectionTitle("Analise por Local de Cobranca", padded = false)
                            if (graficos.receberLocalCobranca.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Titulos a Receber por Local", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinGreen))
                                Spacer(modifier = Modifier.height(8.dp))
                                FinBarList(graficos.receberLocalCobranca.take(10).map { it.local to it.saldo }, FinGreen)
                            }
                            if (graficos.pagarLocalCobranca.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Titulos a Pagar por Local", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinRed))
                                Spacer(modifier = Modifier.height(8.dp))
                                FinBarList(graficos.pagarLocalCobranca.take(10).map { it.local to it.saldo }, FinRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers top-level ─────────────────────────────────────────────────────────

@Composable
fun FinSectionTitle(text: String, padded: Boolean = true) {
    Text(
        text,
        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)),
        modifier = if (padded) Modifier.padding(horizontal = 16.dp, vertical = 4.dp) else Modifier
    )
}

@Composable
fun FinCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
fun FinBanner(label: String, titulo: TituloInfo, bg: Color, accent: Color, textDark: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(14.dp)) {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(accent).height(3.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(label, style = TextStyle(fontSize = 11.sp, color = textDark))
        Text(formatCurrency(titulo.saldo), style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = accent), modifier = Modifier.padding(vertical = 4.dp))
        Text("${titulo.qtdTitulos} titulos", style = TextStyle(fontSize = 11.sp, color = textDark))
    }
}

@Composable
fun FinPagarReceberRow(label: String, titulo: TituloInfo, color: Color) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = TextStyle(fontSize = 11.sp, color = FinSlate))
        Text("${titulo.qtdTitulos} titulos", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color))
        Text(formatCurrency(titulo.saldo), style = TextStyle(fontSize = 13.sp, color = color))
    }
}

@Composable
fun FinFluxoRow(label: String, value: Double, color: Color, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = TextStyle(fontSize = 11.sp, color = FinSlate))
        Text(formatCurrency(value), style = TextStyle(fontSize = 12.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    }
}

@Composable
fun FinInadimplenciaRow(nome: String, sub: String, valor: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(nome, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = TextStyle(fontSize = 11.sp, color = FinSlate))
        }
        Text(valor, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color))
    }
    HorizontalDivider(color = Color(0xFFF1F5F9))
}

@Composable
fun FinBarList(items: List<Pair<String, Double>>, color: Color) {
    val total = items.sumOf { it.second }.takeIf { it > 0 } ?: 1.0
    val cores = listOf(color, Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFF59E0B), Color(0xFF06B6D4),
        Color(0xFF84CC16), Color(0xFFF97316), Color(0xFFEC4899), Color(0xFF6366F1), Color(0xFF14B8A6))
    items.forEachIndexed { index, (label, valor) ->
        val pct = (valor / total * 100).coerceAtLeast(5.0)
        Column(modifier = Modifier.padding(bottom = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = TextStyle(fontSize = 12.sp, color = Color(0xFF1E293B)), modifier = Modifier.weight(1f))
                Text(formatCurrency(valor), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B)))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFF1F5F9))) {
                Box(modifier = Modifier.fillMaxWidth(pct.toFloat() / 100f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(cores[index % cores.size]))
            }
        }
    }
}

@Composable
fun FinLoadingRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PrimaryBlue, strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Carregando dados...", style = TextStyle(fontSize = 13.sp, color = FinSlate))
    }
}

@Composable
fun FinEmptyRow(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontSize = 13.sp, color = FinSlate))
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun FinanceScreenPreview() {
    FinanceScreen(rememberNavController())
}
