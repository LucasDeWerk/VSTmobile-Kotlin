package com.example.vstmobile.ui.screens.pcp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.example.vstmobile.navigation.Screen
import com.example.vstmobile.services.*
import com.example.vstmobile.ui.components.ScreenWithSidebar
import com.example.vstmobile.utils.buildAcompanhamentoHtml
import com.example.vstmobile.utils.generateAndOpenPdf
import kotlinx.coroutines.launch
import java.util.*

// ── Cores ─────────────────────────────────────────────────────────────────────
private val PcpNavy   = Color(0xFF1E3A8A)
private val PcpBlue   = Color(0xFF3B82F6)
private val PcpSlate  = Color(0xFF64748B)
private val PcpLight  = Color(0xFFF1F5F9)
private val PcpDark   = Color(0xFF1E293B)
private val PcpGreen  = Color(0xFF059669)
private val PcpRed    = Color(0xFFEF4444)

// ── Helpers de data ────────────────────────────────────────────────────────────
private fun hoje(): String {
    val cal = Calendar.getInstance()
    return "%02d/%02d/%04d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
}

private fun primeiroDiaMes(): String {
    val cal = Calendar.getInstance()
    return "01/%02d/%04d".format(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
}

/** dd/MM/yyyy → yyyy-MM-dd */
private fun toApiDate(ddmmyyyy: String): String {
    if (ddmmyyyy.length < 10) return ddmmyyyy
    val parts = ddmmyyyy.split("/")
    return "${parts[2]}-${parts[1]}-${parts[0]}"
}

/** ISO "2026-03-08T00:00:00.000Z" → "08/03/2026" */
private fun isoToDdMmYyyy(iso: String): String {
    if (iso.length < 10) return iso
    val parts = iso.substring(0, 10).split("-")
    return "${parts[2]}/${parts[1]}/${parts[0]}"
}

// ── Tela ───────────────────────────────────────────────────────────────────────
@Composable
fun PcpAcompanhamentoProducaoScreen(navController: NavHostController) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val scope   = rememberCoroutineScope()

    // ── Filtros ───────────────────────────────────────────────────────────────
    var produto    by remember { mutableStateOf("") }
    var pedido     by remember { mutableStateOf("") }
    var dataInicio by remember { mutableStateOf(primeiroDiaMes()) }
    var dataFim    by remember { mutableStateOf(hoje()) }

    // ── Linhas de produção ────────────────────────────────────────────────────
    var linhas          by remember { mutableStateOf<List<PCPLinha>>(emptyList()) }
    var linhaSelecionada by remember { mutableStateOf<PCPLinha?>(null) }
    var showLinhaModal  by remember { mutableStateOf(false) }

    // ── Dados ─────────────────────────────────────────────────────────────────
    var dataList       by remember { mutableStateOf<List<PCPAcompanhamentoItem>>(emptyList()) }
    var buscaRealizada by remember { mutableStateOf(false) }
    var loadingData    by remember { mutableStateOf(false) }
    var loadingPdf     by remember { mutableStateOf(false) }
    var msgErro        by remember { mutableStateOf("") }
    var msgSucesso     by remember { mutableStateOf("") }

    // ── Expansão de grupos ────────────────────────────────────────────────────
    var expandedLps  by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var expandedDays by remember { mutableStateOf<Set<String>>(emptySet()) }

    // ── Filiais ───────────────────────────────────────────────────────────────
    val todasFiliais        = FilialState.filiais
    val filiaisSelecionadas = FilialState.selectedFiliais
    var showFilialModal     by remember { mutableStateOf(false) }

    fun labelFiliais(): String = when {
        filiaisSelecionadas.isEmpty() -> "Selecionar filiais"
        filiaisSelecionadas.size == 1 -> {
            val f = filiaisSelecionadas.first()
            f.nomeFilial.ifEmpty { "Filial ${f.idFilial}" }
        }
        filiaisSelecionadas.size <= 3 ->
            filiaisSelecionadas.joinToString(", ") { it.nomeFilial.ifEmpty { "Filial ${it.idFilial}" } }
        else -> {
            val primeiros = filiaisSelecionadas.take(2).joinToString(", ") { it.nomeFilial.ifEmpty { "Filial ${it.idFilial}" } }
            "$primeiros e mais ${filiaisSelecionadas.size - 2} filial(is)"
        }
    }

    // ── Carregar linhas quando filial muda ────────────────────────────────────
    LaunchedEffect(filiaisSelecionadas) {
        if (filiaisSelecionadas.isNotEmpty() && linhas.isEmpty()) {
            val filial = filiaisSelecionadas.first()
            val svc = PcpAcompanhamentoService(session.userToken)
            val r = svc.fetchLinhas(filial.idEmpresa, filial.idFilial)
            if (r.success && r.data != null) {
                linhas = r.data
            }
        }
    }

    // ── Buscar dados ──────────────────────────────────────────────────────────
    fun buscarDados() {
        if (filiaisSelecionadas.isEmpty()) { msgErro = "Selecione pelo menos uma filial"; return }
        if (dataInicio.length < 10) { msgErro = "Informe a data de início"; return }
        if (dataFim.length < 10)    { msgErro = "Informe a data de fim";     return }
        msgErro = ""; msgSucesso = ""
        scope.launch {
            loadingData = true
            val filial = filiaisSelecionadas.first()
            val svc = PcpAcompanhamentoService(session.userToken)
            val r = svc.fetchData(
                idEmpresa = filial.idEmpresa,
                idFilial  = filial.idFilial,
                dtIni     = toApiDate(dataInicio),
                dtFim     = toApiDate(dataFim),
                produto   = produto,
                idLinha   = linhaSelecionada?.idLinhaProducao ?: 0,
                pedido    = pedido
            )
            if (r.success && r.data != null) {
                dataList = r.data
                buscaRealizada = true
                // Expand all LPs by default
                expandedLps = r.data.map { it.idLinhaProducaoPosse }.toSet()
                expandedDays = emptySet()
                if (r.data.isEmpty()) msgErro = "Nenhum registro encontrado no período"
            } else {
                msgErro = r.error ?: "Erro ao buscar dados"
                dataList = emptyList()
            }
            loadingData = false
        }
    }

    // ── Gerar PDF local ───────────────────────────────────────────────────────
    fun gerarPdfLocal() {
        if (dataList.isEmpty()) { msgErro = "Busque os dados antes de gerar o PDF"; return }
        msgErro = ""; msgSucesso = ""
        scope.launch {
            loadingPdf = true
            val linhaLabel = linhaSelecionada?.let { "${it.idLinhaProducao} - ${it.descLinhaProducao}" } ?: "Todas as linhas"
            val html = buildAcompanhamentoHtml(dataList, dataInicio, dataFim, linhaLabel)
            val fileName = "PCP_Acompanhamento_${System.currentTimeMillis()}.pdf"
            val ok = generateAndOpenPdf(context, html, fileName)
            if (ok) msgSucesso = "✅ Relatório gerado e aberto com sucesso!"
            else    msgErro    = "Erro ao gerar PDF no dispositivo."
            loadingPdf = false
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    ScreenWithSidebar(
        navController = navController,
        title         = "Acompanhamento de Produção",
        currentRoute  = Screen.PcpAcompanhamentoProducao.route
    ) { _ ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PcpLight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ════════════════════════════════════════════════════════════════════
            // CARD DE FILTROS
            // ════════════════════════════════════════════════════════════════════
            PcpCard {

                // ── Filiais ───────────────────────────────────────────────────
                PcpLabel("Filiais *")
                Spacer(Modifier.height(6.dp))
                PcpSelectRow(label = labelFiliais(), onClick = { showFilialModal = true })
                if (filiaisSelecionadas.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    PcpChipGroup(
                        labels   = filiaisSelecionadas.map { it.nomeFilial.ifEmpty { "Filial ${it.idFilial}" } },
                        onRemove = { idx -> filiaisSelecionadas.getOrNull(idx)?.let { FilialState.toggleFilial(it) } }
                    )
                }

                PcpDivider()

                // ── Produto ───────────────────────────────────────────────────
                PcpLabel("Produto")
                Spacer(Modifier.height(8.dp))
                PcpTextField(
                    value    = produto,
                    onValue  = { produto = it },
                    label    = "Filtrar por produto",
                    modifier = Modifier.fillMaxWidth()
                )

                PcpDivider()

                // ── Linha de Produção ─────────────────────────────────────────
                PcpLabel("Linha de Produção")
                Spacer(Modifier.height(6.dp))
                PcpSelectRow(
                    label   = linhaSelecionada?.let { "${it.idLinhaProducao} - ${it.descLinhaProducao}" } ?: "Todas as linhas",
                    onClick = { showLinhaModal = true }
                )
                if (linhaSelecionada != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFEFF6FF))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${linhaSelecionada!!.idLinhaProducao} - ${linhaSelecionada!!.descLinhaProducao}",
                            style = TextStyle(fontSize = 11.sp, color = PcpBlue),
                            modifier = Modifier.weight(1f), maxLines = 1
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Close, null, tint = PcpBlue,
                            modifier = Modifier.size(14.dp).clickable { linhaSelecionada = null })
                    }
                }

                PcpDivider()

                // ── Pedido ────────────────────────────────────────────────────
                PcpLabel("Pedido / OP")
                Spacer(Modifier.height(8.dp))
                PcpTextField(
                    value    = pedido,
                    onValue  = { pedido = it },
                    label    = "Nº do Pedido ou OP",
                    modifier = Modifier.fillMaxWidth()
                )

                PcpDivider()

                // ── Período ───────────────────────────────────────────────────
                PcpLabel("Período de Movimentação *")
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("De", style = TextStyle(fontSize = 12.sp, color = PcpSlate, fontWeight = FontWeight.Medium))
                        Spacer(Modifier.height(4.dp))
                        PcpDateField(value = dataInicio, onValue = { dataInicio = it }, modifier = Modifier.fillMaxWidth())
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Até", style = TextStyle(fontSize = 12.sp, color = PcpSlate, fontWeight = FontWeight.Medium))
                        Spacer(Modifier.height(4.dp))
                        PcpDateField(value = dataFim, onValue = { dataFim = it }, modifier = Modifier.fillMaxWidth())
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Mensagens ─────────────────────────────────────────────────
                if (msgErro.isNotEmpty()) {
                    Text(msgErro, style = TextStyle(fontSize = 13.sp, color = PcpRed))
                    Spacer(Modifier.height(8.dp))
                }
                if (msgSucesso.isNotEmpty()) {
                    Text(msgSucesso, style = TextStyle(fontSize = 13.sp, color = PcpGreen))
                    Spacer(Modifier.height(8.dp))
                }

                // ── Botão Buscar ──────────────────────────────────────────────
                Button(
                    onClick  = { buscarDados() },
                    enabled  = !loadingData,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = PcpNavy)
                ) {
                    if (loadingData) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (loadingData) "Buscando..." else "🔍 Buscar",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    )
                }

                // ── Botão Exportar PDF ────────────────────────────────────────
                if (dataList.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick  = { gerarPdfLocal() },
                        enabled  = !loadingPdf,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = PcpNavy)
                    ) {
                        if (loadingPdf) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = PcpNavy, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (loadingPdf) "Gerando PDF..." else "📄 Exportar PDF",
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // ════════════════════════════════════════════════════════════════════
            // RESULTADOS
            // ════════════════════════════════════════════════════════════════════
            if (buscaRealizada && dataList.isNotEmpty()) {

                Text(
                    "${dataList.size} registro(s) encontrado(s)",
                    style = TextStyle(fontSize = 13.sp, color = PcpSlate, fontWeight = FontWeight.Medium)
                )

                // Agrupar por LP
                val lpGroups = dataList.groupBy { it.idLinhaProducaoPosse to it.descLinhaProducao }
                    .toSortedMap(compareBy { it.first })

                lpGroups.forEach { (lpKey, lpItems) ->
                    val (lpId, lpDesc) = lpKey
                    val lpExpanded = expandedLps.contains(lpId)

                    PcpCard {
                        // Header LP
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(PcpDark)
                                .clickable {
                                    expandedLps = if (lpExpanded) expandedLps - lpId else expandedLps + lpId
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "LP: $lpId - $lpDesc",
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (lpExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                null, tint = Color.White, modifier = Modifier.size(22.dp)
                            )
                        }

                        AnimatedVisibility(visible = lpExpanded) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Agrupar por dia dentro da LP
                                val dayGroups = lpItems.groupBy { isoToDdMmYyyy(it.dtEncerramento) }.toSortedMap()

                                dayGroups.forEach { (day, dayItems) ->
                                    val dayKey = "$lpId-$day"
                                    val dayExpanded = expandedDays.contains(dayKey)

                                    // Day header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(PcpSlate)
                                            .clickable {
                                                expandedDays = if (dayExpanded) expandedDays - dayKey else expandedDays + dayKey
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "📅 $day (${dayItems.size} itens)",
                                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        )
                                        Icon(
                                            if (dayExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            null, tint = Color.White, modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    AnimatedVisibility(visible = dayExpanded) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            dayItems.forEach { item ->
                                                PcpItemCard(item)
                                            }

                                            // Total do dia
                                            val totalQtdDia = dayItems.sumOf { it.qtdProduzida }
                                            val totalProduzirDia = dayItems.sumOf { it.qtdProduzir }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFE2E8F0))
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Total do dia", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PcpDark))
                                                Text(
                                                    "Prod: ${fmtNumBr(totalQtdDia)} / ${fmtNumBr(totalProduzirDia)} ${dayItems.firstOrNull()?.abreviatura ?: ""}",
                                                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PcpDark)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Total da LP
                                val totalQtdLp = lpItems.sumOf { it.qtdProduzida }
                                val totalKgLp = lpItems.sumOf { it.qtdProduzida * it.pesoProdutoCad }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF334155))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total LP", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White))
                                    Text(
                                        "${fmtNumBr(totalQtdLp)} ${lpItems.firstOrNull()?.abreviatura ?: ""} | ${fmtNumBr(totalKgLp)} KG",
                                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Total Geral ───────────────────────────────────────────────
                val totalGeralProd = dataList.sumOf { it.qtdProduzida }
                val totalGeralKg   = dataList.sumOf { it.qtdProduzida * it.pesoProdutoCad }
                PcpCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PcpNavy)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "TOTAL GERAL [${dataList.size}]",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        Text(
                            "${fmtNumBr(totalGeralProd)} ${dataList.firstOrNull()?.abreviatura ?: ""} | ${fmtNumBr(totalGeralKg)} KG",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        )
                    }
                }
            }
        }

        // ── Modal Filiais ─────────────────────────────────────────────────────
        if (showFilialModal) {
            PcpFilialModal(
                filiais      = todasFiliais,
                selecionadas = filiaisSelecionadas,
                onToggle     = { FilialState.toggleFilial(it) },
                onDismiss    = { showFilialModal = false }
            )
        }

        // ── Modal Linhas ──────────────────────────────────────────────────────
        if (showLinhaModal) {
            PcpLinhaModal(
                linhas    = linhas,
                selected  = linhaSelecionada,
                onSelect  = { linhaSelecionada = it; showLinhaModal = false },
                onClear   = { linhaSelecionada = null; showLinhaModal = false },
                onDismiss = { showLinhaModal = false }
            )
        }
    }
}

// ── Card de item individual ───────────────────────────────────────────────────
@Composable
private fun PcpItemCard(item: PCPAcompanhamentoItem) {
    val progresso = if (item.qtdProduzir > 0) (item.qtdProduzida / item.qtdProduzir).coerceIn(0.0, 1.0) else 0.0
    val status = when {
        progresso >= 1.0  -> "Finalizado"
        progresso > 0.0   -> "Em Produção"
        else              -> "Pendente"
    }
    val statusColor = when (status) {
        "Finalizado"   -> Color(0xFF059669)
        "Em Produção"  -> Color(0xFFF59E0B)
        else           -> Color(0xFFEF4444)
    }
    val pedOp = item.idOp.ifBlank { item.idSaida }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(8.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    pedOp,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PcpDark)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(status, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = statusColor))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                item.descProduto,
                style = TextStyle(fontSize = 12.sp, color = PcpSlate),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progresso.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = Color(0xFFE2E8F0),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Prod: ${fmtNumBr(item.qtdProduzida)} / ${fmtNumBr(item.qtdProduzir)} ${item.abreviatura}",
                    style = TextStyle(fontSize = 10.sp, color = PcpSlate)
                )
                Text(
                    "${(progresso * 100).toInt()}%",
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
                )
            }
        }
    }
}

// ── Componentes privados ───────────────────────────────────────────────────────

@Composable
private fun PcpCard(content: @Composable ColumnScope.() -> Unit) {
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
private fun PcpLabel(text: String) {
    Text(text, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PcpDark))
}

@Composable
private fun PcpDivider() {
    Spacer(Modifier.height(20.dp))
    HorizontalDivider(color = Color(0xFFE2E8F0))
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun PcpSelectRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PcpLight)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, style = TextStyle(fontSize = 13.sp, color = PcpDark), modifier = Modifier.weight(1f))
        Icon(Icons.Default.KeyboardArrowDown, null, tint = PcpSlate, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PcpTextField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValue,
        label         = { Text(label, style = TextStyle(fontSize = 12.sp)) },
        modifier      = modifier,
        singleLine    = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = PcpBlue,
            unfocusedBorderColor = Color(0xFFD1D5DB),
            focusedLabelColor    = PcpBlue,
            unfocusedLabelColor  = PcpSlate
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun PcpDateField(
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value         = value,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }.take(8)
            val formatted = buildString {
                digits.forEachIndexed { i, c ->
                    if (i == 2 || i == 4) append('/')
                    append(c)
                }
            }
            onValue(formatted)
        },
        placeholder   = { Text("dd/mm/aaaa", style = TextStyle(fontSize = 13.sp, color = PcpSlate)) },
        modifier      = modifier,
        singleLine    = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = PcpBlue,
            unfocusedBorderColor = Color(0xFFD1D5DB)
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun PcpChipGroup(labels: List<String>, onRemove: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.chunked(2).forEachIndexed { rowIdx, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { colIdx, label ->
                    val idx = rowIdx * 2 + colIdx
                    Row(
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFEFF6FF))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, style = TextStyle(fontSize = 11.sp, color = PcpBlue), modifier = Modifier.weight(1f), maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Close, null, tint = PcpBlue, modifier = Modifier.size(14.dp).clickable { onRemove(idx) })
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ── Modal Filiais ─────────────────────────────────────────────────────────────
@Composable
private fun PcpFilialModal(
    filiais: List<Filial>,
    selecionadas: List<Filial>,
    onToggle: (Filial) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
            shape    = RoundedCornerShape(20.dp),
            colors   = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Selecionar Filiais",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PcpDark))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = PcpSlate) }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(8.dp)) {
                    filiais.forEach { filial ->
                        val sel = selecionadas.any { it.idFilial == filial.idFilial }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onToggle(filial) }
                                .background(if (sel) Color(0xFFEFF6FF) else Color.White)
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(filial.nomeFilial.ifEmpty { "Filial ${filial.idFilial}" },
                                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                        color = if (sel) PcpBlue else PcpDark))
                                Text("ID: ${filial.idFilial}", style = TextStyle(fontSize = 12.sp, color = PcpSlate))
                            }
                            Icon(if (sel) Icons.Default.Check else Icons.Default.KeyboardArrowDown,
                                null, tint = if (sel) PcpBlue else Color(0xFFCCCCCC), modifier = Modifier.size(24.dp))
                        }
                        HorizontalDivider(color = PcpLight)
                    }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("${selecionadas.size} selecionada(s)", style = TextStyle(fontSize = 13.sp, color = PcpSlate))
                    Button(
                        onClick = onDismiss, shape = RoundedCornerShape(8.dp),
                        colors  = ButtonDefaults.buttonColors(containerColor = PcpBlue)
                    ) {
                        Text("Confirmar", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White))
                    }
                }
            }
        }
    }
}

// ── Modal Linhas de Produção ──────────────────────────────────────────────────
@Composable
private fun PcpLinhaModal(
    linhas: List<PCPLinha>,
    selected: PCPLinha?,
    onSelect: (PCPLinha) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
            shape    = RoundedCornerShape(20.dp),
            colors   = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Linha de Produção",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PcpDark))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = PcpSlate) }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    // Opção "Todas"
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onClear() }
                            .background(if (selected == null) Color(0xFFEFF6FF) else Color.White)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PcpRadioBullet(checked = selected == null)
                        Text("Todas as linhas", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            color = if (selected == null) PcpBlue else PcpDark))
                    }
                    HorizontalDivider(color = PcpLight)
                    linhas.forEach { linha ->
                        val sel = selected?.idLinhaProducao == linha.idLinhaProducao
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onSelect(linha) }
                                .background(if (sel) Color(0xFFEFF6FF) else Color.White)
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PcpRadioBullet(checked = sel)
                            Text(
                                "${linha.idLinhaProducao} - ${linha.descLinhaProducao}",
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                    color = if (sel) PcpBlue else PcpDark)
                            )
                        }
                        HorizontalDivider(color = PcpLight)
                    }
                }
            }
        }
    }
}

@Composable
private fun PcpRadioBullet(checked: Boolean) {
    Box(
        modifier         = Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape    = RoundedCornerShape(10.dp),
            colors   = CardDefaults.cardColors(containerColor = Color.Transparent),
            border   = androidx.compose.foundation.BorderStroke(2.dp, if (checked) PcpBlue else Color(0xFFD1D5DB))
        ) {}
        if (checked) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(PcpBlue))
        }
    }
}

// ── Formatação BR ─────────────────────────────────────────────────────────────
private fun fmtNumBr(value: Double): String {
    if (value == 0.0) return "0"
    return String.format(Locale("pt", "BR"), "%,.2f", value)
}
