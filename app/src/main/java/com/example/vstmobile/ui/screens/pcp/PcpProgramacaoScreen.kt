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
import com.example.vstmobile.utils.buildProgramacaoHtml
import com.example.vstmobile.utils.generateAndOpenPdf
import kotlinx.coroutines.launch
import java.util.*

// ── Cores ─────────────────────────────────────────────────────────────────────
private val ProgNavy  = Color(0xFF1E3A8A)
private val ProgBlue  = Color(0xFF3B82F6)
private val ProgSlate = Color(0xFF64748B)
private val ProgLight = Color(0xFFF1F5F9)
private val ProgDark  = Color(0xFF1E293B)
private val ProgGreen = Color(0xFF059669)
private val ProgRed   = Color(0xFFEF4444)

// ── Helpers de data ────────────────────────────────────────────────────────────
private fun progHoje(): String {
    val cal = Calendar.getInstance()
    return "%02d/%02d/%04d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
}

private fun progSeteDiasAtras(): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_MONTH, -7)
    return "%02d/%02d/%04d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
}

/** dd/MM/yyyy → yyyy-MM-dd */
private fun progToApiDate(ddmmyyyy: String): String {
    if (ddmmyyyy.length < 10) return ddmmyyyy
    val parts = ddmmyyyy.split("/")
    return "${parts[2]}-${parts[1]}-${parts[0]}"
}

/** ISO "2026-03-15T00:00:00.000Z" → "15/03/2026" */
private fun progIsoToDdMmYyyy(iso: String): String {
    if (iso.length < 10) return iso
    val parts = iso.substring(0, 10).split("-")
    return "${parts[2]}/${parts[1]}/${parts[0]}"
}

// ── Tela ───────────────────────────────────────────────────────────────────────
@Composable
fun PcpProgramacaoScreen(navController: NavHostController) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val scope   = rememberCoroutineScope()

    // ── Filtros ───────────────────────────────────────────────────────────────
    var dataInicio by remember { mutableStateOf(progSeteDiasAtras()) }
    var dataFim    by remember { mutableStateOf(progHoje()) }

    // ── Linhas de produção ────────────────────────────────────────────────────
    var linhas          by remember { mutableStateOf<List<PCPLinha>>(emptyList()) }
    var linhaSelecionada by remember { mutableStateOf<PCPLinha?>(null) }
    var showLinhaModal  by remember { mutableStateOf(false) }

    // ── Dados ─────────────────────────────────────────────────────────────────
    var dataList       by remember { mutableStateOf<List<PCPProgramacaoItem>>(emptyList()) }
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
            val p = filiaisSelecionadas.take(2).joinToString(", ") { it.nomeFilial.ifEmpty { "Filial ${it.idFilial}" } }
            "$p e mais ${filiaisSelecionadas.size - 2} filial(is)"
        }
    }

    // ── Carregar linhas quando filial muda ────────────────────────────────────
    LaunchedEffect(filiaisSelecionadas) {
        if (filiaisSelecionadas.isNotEmpty() && linhas.isEmpty()) {
            val filial = filiaisSelecionadas.first()
            val svc = PcpProgramacaoService(session.userToken)
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
            val svc = PcpProgramacaoService(session.userToken)
            val r = svc.fetchData(
                idEmpresa = filial.idEmpresa,
                idFilial  = filial.idFilial,
                dtIni     = progToApiDate(dataInicio),
                dtFim     = progToApiDate(dataFim),
                idLinha   = linhaSelecionada?.idLinhaProducao ?: 0
            )
            if (r.success && r.data != null) {
                dataList = r.data
                buscaRealizada = true
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
            val html = buildProgramacaoHtml(dataList, dataInicio, dataFim, linhaLabel)
            val fileName = "PCP_Programacao_${System.currentTimeMillis()}.pdf"
            val ok = generateAndOpenPdf(context, html, fileName)
            if (ok) msgSucesso = "✅ Relatório gerado e aberto com sucesso!"
            else    msgErro    = "Erro ao gerar PDF no dispositivo."
            loadingPdf = false
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    ScreenWithSidebar(
        navController = navController,
        title         = "Programação PCP",
        currentRoute  = Screen.PcpProgramacao.route
    ) { _ ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ProgLight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ════════════════════════════════════════════════════════════════════
            // CARD DE FILTROS
            // ════════════════════════════════════════════════════════════════════
            ProgCard {

                // ── Filiais ───────────────────────────────────────────────────
                ProgLabel("Filiais *")
                Spacer(Modifier.height(6.dp))
                ProgSelectRow(label = labelFiliais(), onClick = { showFilialModal = true })
                if (filiaisSelecionadas.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    ProgChipGroup(
                        labels   = filiaisSelecionadas.map { it.nomeFilial.ifEmpty { "Filial ${it.idFilial}" } },
                        onRemove = { idx -> filiaisSelecionadas.getOrNull(idx)?.let { FilialState.toggleFilial(it) } }
                    )
                }

                ProgDivider()

                // ── Linha de Produção ─────────────────────────────────────────
                ProgLabel("Linha de Produção")
                Spacer(Modifier.height(6.dp))
                ProgSelectRow(
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
                            style = TextStyle(fontSize = 11.sp, color = ProgBlue),
                            modifier = Modifier.weight(1f), maxLines = 1
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Close, null, tint = ProgBlue,
                            modifier = Modifier.size(14.dp).clickable { linhaSelecionada = null })
                    }
                }

                ProgDivider()

                // ── Período ───────────────────────────────────────────────────
                ProgLabel("Período de Movimentação *")
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("De", style = TextStyle(fontSize = 12.sp, color = ProgSlate, fontWeight = FontWeight.Medium))
                        Spacer(Modifier.height(4.dp))
                        ProgDateField(value = dataInicio, onValue = { dataInicio = it }, modifier = Modifier.fillMaxWidth())
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Até", style = TextStyle(fontSize = 12.sp, color = ProgSlate, fontWeight = FontWeight.Medium))
                        Spacer(Modifier.height(4.dp))
                        ProgDateField(value = dataFim, onValue = { dataFim = it }, modifier = Modifier.fillMaxWidth())
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Mensagens ─────────────────────────────────────────────────
                if (msgErro.isNotEmpty()) {
                    Text(msgErro, style = TextStyle(fontSize = 13.sp, color = ProgRed))
                    Spacer(Modifier.height(8.dp))
                }
                if (msgSucesso.isNotEmpty()) {
                    Text(msgSucesso, style = TextStyle(fontSize = 13.sp, color = ProgGreen))
                    Spacer(Modifier.height(8.dp))
                }

                // ── Botão Buscar ──────────────────────────────────────────────
                Button(
                    onClick  = { buscarDados() },
                    enabled  = !loadingData,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = ProgNavy)
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
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = ProgNavy)
                    ) {
                        if (loadingPdf) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = ProgNavy, strokeWidth = 2.dp)
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
                    style = TextStyle(fontSize = 13.sp, color = ProgSlate, fontWeight = FontWeight.Medium)
                )

                // Agrupar por LP
                val lpGroups = dataList.groupBy { it.idLinhaProducaoPosse to it.descLinhaProducao }
                    .toSortedMap(compareBy { it.first })

                lpGroups.forEach { (lpKey, lpItems) ->
                    val (lpId, lpDesc) = lpKey
                    val lpExpanded = expandedLps.contains(lpId)

                    ProgCard {
                        // Header LP
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ProgDark)
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
                                val dayGroups = lpItems.groupBy { progIsoToDdMmYyyy(it.dtEncerramento) }.toSortedMap()

                                dayGroups.forEach { (day, dayItems) ->
                                    val dayKey = "$lpId-$day"
                                    val dayExpanded = expandedDays.contains(dayKey)

                                    // Day header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(ProgSlate)
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
                                                ProgItemCard(item)
                                            }

                                            // Total do dia
                                            val totalQtdDia = dayItems.sumOf { it.qtdProduzir }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFE2E8F0))
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Total do dia", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ProgDark))
                                                Text(
                                                    "${progFmtNumBr(totalQtdDia)} ${dayItems.firstOrNull()?.abreviatura ?: ""}",
                                                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ProgDark)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Total da LP
                                val totalQtdLp = lpItems.sumOf { it.qtdProduzir }
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
                                        "${progFmtNumBr(totalQtdLp)} ${lpItems.firstOrNull()?.abreviatura ?: ""}",
                                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Total Geral ───────────────────────────────────────────────
                val totalGeral = dataList.sumOf { it.qtdProduzir }
                ProgCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ProgNavy)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "TOTAL GERAL [${dataList.size}]",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        Text(
                            "${progFmtNumBr(totalGeral)} ${dataList.firstOrNull()?.abreviatura ?: ""}",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        )
                    }
                }
            }
        }

        // ── Modal Filiais ─────────────────────────────────────────────────────
        if (showFilialModal) {
            ProgFilialModal(
                filiais      = todasFiliais,
                selecionadas = filiaisSelecionadas,
                onToggle     = { FilialState.toggleFilial(it) },
                onDismiss    = { showFilialModal = false }
            )
        }

        // ── Modal Linhas ──────────────────────────────────────────────────────
        if (showLinhaModal) {
            ProgLinhaModal(
                linhas    = linhas,
                selected  = linhaSelecionada,
                onSelect  = { linhaSelecionada = it; showLinhaModal = false },
                onClear   = { linhaSelecionada = null; showLinhaModal = false },
                onDismiss = { showLinhaModal = false }
            )
        }
    }
}

// ── Card de item individual (Programação) ─────────────────────────────────────
@Composable
private fun ProgItemCard(item: PCPProgramacaoItem) {
    val pedOp = item.idOp.ifBlank { item.idSaida }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(8.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            // Linha 1: PV/OP + Quantidade
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PV/OP: $pedOp",
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ProgDark)
                )
                Text(
                    "${progFmtNumBr(item.qtdProduzir)} ${item.abreviatura}",
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ProgBlue)
                )
            }
            Spacer(Modifier.height(4.dp))

            // Linha 2: Produto
            Text(
                item.descProduto,
                style = TextStyle(fontSize = 12.sp, color = ProgSlate),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))

            // Linha 3: Data prevista
            Text(
                "📅 Data: ${progIsoToDdMmYyyy(item.dtEncerramento)}",
                style = TextStyle(fontSize = 11.sp, color = ProgSlate)
            )

            // Linha 4: Cliente
            if (item.cliente.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "👤 ${item.cliente}",
                    style = TextStyle(fontSize = 11.sp, color = ProgDark),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Linha 5: Vendedor
            if (item.nomeVendedor.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "🏷 ${item.nomeVendedor}",
                    style = TextStyle(fontSize = 11.sp, color = ProgSlate),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Componentes privados ───────────────────────────────────────────────────────

@Composable
private fun ProgCard(content: @Composable ColumnScope.() -> Unit) {
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
private fun ProgLabel(text: String) {
    Text(text, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ProgDark))
}

@Composable
private fun ProgDivider() {
    Spacer(Modifier.height(20.dp))
    HorizontalDivider(color = Color(0xFFE2E8F0))
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun ProgSelectRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ProgLight)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, style = TextStyle(fontSize = 13.sp, color = ProgDark), modifier = Modifier.weight(1f))
        Icon(Icons.Default.KeyboardArrowDown, null, tint = ProgSlate, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ProgDateField(
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value         = value,
        onValueChange = { input ->
            val digits    = input.filter { it.isDigit() }.take(8)
            val formatted = buildString {
                digits.forEachIndexed { i, c ->
                    if (i == 2 || i == 4) append('/')
                    append(c)
                }
            }
            onValue(formatted)
        },
        placeholder     = { Text("dd/mm/aaaa", style = TextStyle(fontSize = 13.sp, color = ProgSlate)) },
        modifier        = modifier,
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors          = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = ProgBlue,
            unfocusedBorderColor = Color(0xFFD1D5DB)
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun ProgChipGroup(labels: List<String>, onRemove: (Int) -> Unit) {
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
                        Text(label, style = TextStyle(fontSize = 11.sp, color = ProgBlue), modifier = Modifier.weight(1f), maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Close, null, tint = ProgBlue,
                            modifier = Modifier.size(14.dp).clickable { onRemove(idx) })
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ── Modal Filiais ─────────────────────────────────────────────────────────────
@Composable
private fun ProgFilialModal(
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
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ProgDark))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = ProgSlate) }
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
                                        color = if (sel) ProgBlue else ProgDark))
                                Text("ID: ${filial.idFilial}", style = TextStyle(fontSize = 12.sp, color = ProgSlate))
                            }
                            Icon(if (sel) Icons.Default.Check else Icons.Default.KeyboardArrowDown,
                                null, tint = if (sel) ProgBlue else Color(0xFFCCCCCC), modifier = Modifier.size(24.dp))
                        }
                        HorizontalDivider(color = ProgLight)
                    }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("${selecionadas.size} selecionada(s)", style = TextStyle(fontSize = 13.sp, color = ProgSlate))
                    Button(
                        onClick = onDismiss, shape = RoundedCornerShape(8.dp),
                        colors  = ButtonDefaults.buttonColors(containerColor = ProgBlue)
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
private fun ProgLinhaModal(
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
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ProgDark))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = ProgSlate) }
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
                        ProgRadioBullet(checked = selected == null)
                        Text("Todas as linhas", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            color = if (selected == null) ProgBlue else ProgDark))
                    }
                    HorizontalDivider(color = ProgLight)
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
                            ProgRadioBullet(checked = sel)
                            Text(
                                "${linha.idLinhaProducao} - ${linha.descLinhaProducao}",
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                    color = if (sel) ProgBlue else ProgDark)
                            )
                        }
                        HorizontalDivider(color = ProgLight)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgRadioBullet(checked: Boolean) {
    Box(
        modifier         = Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape    = RoundedCornerShape(10.dp),
            colors   = CardDefaults.cardColors(containerColor = Color.Transparent),
            border   = androidx.compose.foundation.BorderStroke(2.dp, if (checked) ProgBlue else Color(0xFFD1D5DB))
        ) {}
        if (checked) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(ProgBlue))
        }
    }
}

// ── Formatação BR ─────────────────────────────────────────────────────────────
private fun progFmtNumBr(value: Double): String {
    if (value == 0.0) return "0"
    return String.format(Locale("pt", "BR"), "%,.2f", value)
}
