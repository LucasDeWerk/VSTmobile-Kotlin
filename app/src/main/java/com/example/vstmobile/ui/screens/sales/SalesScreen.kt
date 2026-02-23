package com.example.vstmobile.ui.screens.sales

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.vstmobile.ui.components.ScreenWithSidebar
import com.example.vstmobile.navigation.Screen
import com.example.vstmobile.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// ── Cores ─────────────────────────────────────────────────────────────────────
private val SNavy    = Color(0xFF1E3A8A)
private val SBlue    = Color(0xFF3B82F6)
private val SSlate   = Color(0xFF64748B)
private val SLight   = Color(0xFFF1F5F9)
private val SRed     = Color(0xFFEF4444)
private val SAmber   = Color(0xFFF59E0B)
private val SDark    = Color(0xFF1E293B)
private val SWhite   = Color(0xFFFFFFFF)

private fun salFmt(v: Double): String = try {
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(v)
} catch (_: Exception) { "R$ 0,00" }

private fun salFmtPct(v: Double) = "${"%.2f".format(v)}%"

private fun nomeMes(m: Int) = listOf(
    "Janeiro","Fevereiro","Março","Abril","Maio","Junho",
    "Julho","Agosto","Setembro","Outubro","Novembro","Dezembro"
).getOrElse(m - 1) { "Mês $m" }

private fun formatDate(dt: String): String {
    if (dt.isEmpty()) return ""
    return try { val p = dt.split("-"); "${p[2]}/${p[1]}" } catch (_: Exception) { dt }
}

// ── Tela principal ─────────────────────────────────────────────────────────────
@Composable
fun SalesScreen(navController: NavHostController) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val scope   = rememberCoroutineScope()

    var selectedPeriod by remember { mutableStateOf("A") }
    var selectedCusto  by remember { mutableStateOf("A") }
    var isLoading      by remember { mutableStateOf(false) }

    var cardData       by remember { mutableStateOf(SalesCardData()) }
    var fatMensal      by remember { mutableStateOf<List<FaturamentoMes>>(emptyList()) }
    var impostos       by remember { mutableStateOf<List<Double>>(emptyList()) }
    var porFilial      by remember { mutableStateOf<List<FilialRanking>>(emptyList()) }
    var rankings       by remember { mutableStateOf<List<RankingDash>>(emptyList()) }
    var expandedRanking by remember { mutableStateOf<Int?>(null) }

    val filial = FilialState.selectedFilial

    // Buscar dados sempre que filial/período/custo mudar
    LaunchedEffect(filial?.idFilial, selectedPeriod, selectedCusto) {
        if (filial == null || session.userToken.isEmpty()) return@LaunchedEffect
        val svc = SalesService(session.userToken)
        val emp = filial.idEmpresa.toString()
        val fil = filial.idFilial.toString()
        isLoading = true
        scope.launch {
            launch { svc.fetchFaturamentoCard(emp, fil, selectedPeriod, selectedCusto).data?.let { cardData = it } }
            launch { svc.fetchFaturamento(emp, fil, selectedPeriod).data?.let { fatMensal = it } }
            launch { svc.fetchImposto(emp, fil, selectedPeriod).data?.let { impostos = it } }
            launch { svc.fetchFaturamentoPorFilial(emp, fil, selectedPeriod).data?.let { porFilial = it } }
            launch { svc.fetchRankingVendas(emp, fil, selectedPeriod).data?.let { rankings = it } }
            isLoading = false
        }
    }

    ScreenWithSidebar(
        navController = navController,
        title = "Vendas",
        currentRoute = Screen.Sales.route
    ) { _ ->
        if (filial == null) {
            // Sem filial selecionada
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🏢", fontSize = 48.sp)
                    Text("Selecione uma filial",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SDark))
                    Text("Abra o menu lateral e selecione\numa filial para ver os dados de vendas.",
                        style = TextStyle(fontSize = 14.sp, color = SSlate), textAlign = TextAlign.Center)
                }
            }
            return@ScreenWithSidebar
        }

        Column(modifier = Modifier.fillMaxSize().background(SLight).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            // Loading banner
            if (isLoading) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PrimaryBlue, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Carregando dados da filial...", style = TextStyle(fontSize = 13.sp, color = SSlate))
                }
            }

            // ── Info da Filial ────────────────────────────────────────────────
            SalesCard(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏢", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(filial.nomeFilial, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SDark))
                        Text("Código: ${filial.idFilial}", style = TextStyle(fontSize = 12.sp, color = SSlate))
                    }
                }
            }

            // ── Seletor de Período ────────────────────────────────────────────
            SalesCard(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
                Text("Período", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SDark))
                if (selectedPeriod == "M") {
                    Text("💡 Dica: Para melhor visualização, tente alternar para \"Ano\"",
                        style = TextStyle(fontSize = 12.sp, color = SAmber, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        modifier = Modifier.padding(top = 4.dp))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("M" to "Mês", "A" to "Ano").forEach { (key, label) ->
                        SalesToggleButton(label, selectedPeriod == key, modifier = Modifier.weight(1f)) { selectedPeriod = key }
                    }
                }
            }

            // ── Seletor de Custo ──────────────────────────────────────────────
            SalesCard(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
                Text("Custo", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SDark))
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("M" to "Médio", "A" to "Aquisição").forEach { (key, label) ->
                        SalesToggleButton(label, selectedCusto == key, modifier = Modifier.weight(1f)) { selectedCusto = key }
                    }
                }
            }

            // ── Cards de Resumo ───────────────────────────────────────────────
            Spacer(modifier = Modifier.height(12.dp))
            val salesCards = listOf(
                Triple("💰", "Faturamento Total",      salFmt(cardData.faturamento)),
                Triple("📈", "Lucro Bruto",            salFmt(cardData.lucroBruto)),
                Triple("↩️", "Devoluções",             salFmt(cardData.devolucoes)),
                Triple("🧮", "Faturamento Líquido",    salFmt(cardData.faturamentoLiquido)),
                Triple("🏷️", "Custo Faturamento",      salFmt(cardData.custoFaturamento)),
                Triple("🧾", "Imposto Total",          salFmt(cardData.impostoTotal)),
                Triple("🛒", "Quantidade Pedidos",     cardData.quantidadePedidos.toString()),
                Triple("💳", "Ticket Médio",           salFmt(cardData.ticketMedio)),
                Triple("📊", "Margem de Lucro",        salFmtPct(cardData.margemLucro)),
            )
            // Grid 2 colunas
            val chunked = salesCards.chunked(2)
            chunked.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (icon, title, value) ->
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SWhite),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(icon, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(title, style = TextStyle(fontSize = 11.sp, color = SSlate), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(value, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SDark))
                            }
                        }
                    }
                    // Se row tem só 1 elemento, completar
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }

            // ── Evolução de Vendas ────────────────────────────────────────────
            SalesCard(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp)) {
                Text("Evolução de Vendas ${if (selectedPeriod == "M") "Mensal" else "Anual"}",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SDark))

                if (fatMensal.isEmpty()) {
                    SalesEmptyState("📊", "Não há dados de evolução disponíveis para o período ${if (selectedPeriod == "M") "mensal" else "anual"}.")
                } else {
                    // Legenda
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(SBlue))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Faturamento", style = TextStyle(fontSize = 11.sp, color = SSlate))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(SRed))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Imposto", style = TextStyle(fontSize = 11.sp, color = SSlate))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedPeriod == "A") {
                        // Gráfico de barras verticais com scroll horizontal
                        val maxVal = (fatMensal.maxOfOrNull { it.faturamento } ?: 1.0)
                            .coerceAtLeast(impostos.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
                        val barMaxH = 150.dp
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            fatMensal.forEachIndexed { i, mes ->
                                val fat = mes.faturamento
                                val imp = impostos.getOrElse(i) { 0.0 }
                                val hFat = (fat / maxVal * 150).toInt()
                                val hImp = (imp / maxVal * 150).toInt()
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) {
                                    // Barras
                                    Box(modifier = Modifier.height(barMaxH), contentAlignment = Alignment.BottomCenter) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                                            Box(modifier = Modifier.width(14.dp).height(hFat.dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(SBlue))
                                            Box(modifier = Modifier.width(14.dp).height(hImp.dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(SRed))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(nomeMes(mes.mes).take(3), style = TextStyle(fontSize = 10.sp, color = SSlate), textAlign = TextAlign.Center)
                                    Text(salFmt(fat), style = TextStyle(fontSize = 9.sp, color = SBlue, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center, maxLines = 1)
                                    Text(salFmt(imp), style = TextStyle(fontSize = 9.sp, color = SRed), textAlign = TextAlign.Center, maxLines = 1)
                                }
                            }
                        }
                    } else {
                        // Barras horizontais (mensal)
                        val maxVal = (fatMensal.maxOfOrNull { it.faturamento } ?: 1.0)
                            .coerceAtLeast(impostos.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
                        fatMensal.forEachIndexed { i, mes ->
                            val fat = mes.faturamento
                            val imp = impostos.getOrElse(i) { 0.0 }
                            val pctFat = (fat / maxVal).toFloat().coerceIn(0f, 1f)
                            val pctImp = (imp / maxVal).toFloat().coerceIn(0f, 1f)
                            Column(modifier = Modifier.padding(bottom = 10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatDate(mes.dtCadastro), style = TextStyle(fontSize = 11.sp, color = SSlate))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                // Barra faturamento
                                Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(3.dp)).background(SLight)) {
                                    Box(modifier = Modifier.fillMaxWidth(pctFat).height(12.dp).clip(RoundedCornerShape(3.dp)).background(SBlue))
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                // Barra imposto
                                Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(3.dp)).background(SLight)) {
                                    Box(modifier = Modifier.fillMaxWidth(pctImp).height(12.dp).clip(RoundedCornerShape(3.dp)).background(SRed))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(salFmt(fat), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SBlue))
                                    Text(salFmt(imp), style = TextStyle(fontSize = 11.sp, color = SRed))
                                }
                            }
                        }
                    }
                }
            }

            // ── Ranking por Filial ────────────────────────────────────────────
            SalesCard(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
                Text("Ranking Faturamento por Filial",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SDark))
                Spacer(modifier = Modifier.height(12.dp))
                if (porFilial.isEmpty()) {
                    SalesEmptyState("🏢", "Não há dados de ranking por filial disponíveis.")
                } else {
                    val maxFat = porFilial.maxOfOrNull { it.faturamento }?.coerceAtLeast(1.0) ?: 1.0
                    porFilial.forEachIndexed { i, fil ->
                        val pct = (fil.faturamento / maxFat).toFloat().coerceIn(0f, 1f)
                        Column(modifier = Modifier.padding(bottom = 10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(fil.identificacao, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SDark), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(salFmt(fil.faturamento), style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SNavy))
                            }
                            Text("${fil.qtdVendas} vendas", style = TextStyle(fontSize = 11.sp, color = SSlate))
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(SLight)) {
                                Box(modifier = Modifier.fillMaxWidth(pct).height(8.dp).clip(RoundedCornerShape(4.dp)).background(SNavy))
                            }
                        }
                        if (i < porFilial.size - 1) HorizontalDivider(color = SLight, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }

            // ── Rankings de Vendas ────────────────────────────────────────────
            if (rankings.isEmpty() && !isLoading) {
                SalesCard(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
                    Text("Rankings de Faturamento",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SDark))
                    SalesEmptyState("🏆", "Não há dados de ranking disponíveis para o período selecionado.")
                }
            } else {
                rankings.forEachIndexed { idx, dash ->
                    val isExpanded = expandedRanking == idx
                    val itens = if (isExpanded) dash.registros else dash.registros.take(5)
                    val maxVal = dash.registros.maxOfOrNull { it.valor }?.coerceAtLeast(1.0) ?: 1.0

                    SalesCard(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
                        Text(dash.descDashboard,
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SDark))
                        Spacer(modifier = Modifier.height(12.dp))

                        if (dash.registros.isEmpty()) {
                            SalesEmptyState("📊", "Não há dados disponíveis para este ranking.")
                        } else {
                            itens.forEach { item ->
                                val pct = (item.valor / maxVal).toFloat().coerceIn(0f, 1f)
                                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                    Text(item.nome, style = TextStyle(fontSize = 12.sp, color = SSlate), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(SLight)) {
                                        Box(modifier = Modifier.fillMaxWidth(pct).height(14.dp).clip(RoundedCornerShape(4.dp)).background(SBlue))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(salFmt(item.valor), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SDark))
                                }
                            }
                            if (dash.registros.size > 5) {
                                TextButton(onClick = { expandedRanking = if (isExpanded) null else idx }) {
                                    Text(if (isExpanded) "Ocultar" else "Exibir todos",
                                        style = TextStyle(color = SBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Componentes auxiliares ─────────────────────────────────────────────────────

@Composable
fun SalesCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
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
fun SalesToggleButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) SNavy else SLight)
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else SSlate
        ))
    }
}

@Composable
fun SalesEmptyState(icon: String, message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 40.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, style = TextStyle(fontSize = 13.sp, color = SSlate), textAlign = TextAlign.Center)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SalesScreenPreview() {
    SalesScreen(rememberNavController())
}
