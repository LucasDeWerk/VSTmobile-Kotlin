package com.example.vstmobile.ui.screens.finance

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
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
import kotlinx.coroutines.launch
import java.io.File

// ── Cores ─────────────────────────────────────────────────────────────────────
private val DreNavy  = Color(0xFF1E3A8A)
private val DreBlue  = Color(0xFF3B82F6)
private val DreSlate = Color(0xFF64748B)
private val DreLight = Color(0xFFF1F5F9)
private val DreDark  = Color(0xFF1E293B)
private val DreGreen = Color(0xFF059669)
private val DreRed   = Color(0xFFEF4444)

// ── Tela DRE ──────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DreScreen(navController: NavHostController) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val scope   = rememberCoroutineScope()

    // ── Filtros ───────────────────────────────────────────────────────────────
    var periodoEmissao        by remember { mutableStateOf("") }   // "0"=Mensal, "1"=Anual
    var anoSelecionado        by remember { mutableStateOf("") }
    var mesSelecionado        by remember { mutableStateOf("") }
    var modeloSelecionadoId   by remember { mutableStateOf(-1) }
    var modeloSelecionadoDesc by remember { mutableStateOf("") }
    var estruturaPronta       by remember { mutableStateOf(false) }

    // ── Dados ─────────────────────────────────────────────────────────────────
    var modelosDre      by remember { mutableStateOf<List<ModeloDre>>(emptyList()) }
    var loadingModelos  by remember { mutableStateOf(false) }

    // ── Filiais (multi-select via FilialState global) ─────────────────────────
    val todasFiliais       = FilialState.filiais
    val filiaisSelecionadas = FilialState.selectedFiliais

    // ── UI ────────────────────────────────────────────────────────────────────
    var showFilialModal   by remember { mutableStateOf(false) }
    var showPeriodoModal  by remember { mutableStateOf(false) }
    var showAnoModal      by remember { mutableStateOf(false) }
    var showMesModal      by remember { mutableStateOf(false) }
    var showModeloModal   by remember { mutableStateOf(false) }
    var searchModelo      by remember { mutableStateOf("") }
    var loadingPdf        by remember { mutableStateOf(false) }
    var msgErro           by remember { mutableStateOf("") }
    var msgSucesso        by remember { mutableStateOf("") }

    // ── Carregar modelos ao ter filial selecionada ────────────────────────────
    LaunchedEffect(filiaisSelecionadas) {
        val filial = filiaisSelecionadas.firstOrNull() ?: return@LaunchedEffect
        if (session.userToken.isEmpty()) return@LaunchedEffect
        loadingModelos = true
        val svc = DreService(session.userToken)
        val r = svc.fetchModelosDre(filial.idEmpresa)
        modelosDre = r.data ?: emptyList()
        loadingModelos = false
    }

    // ── Labels de exibição ────────────────────────────────────────────────────
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

    // ── Gerar PDF ─────────────────────────────────────────────────────────────
    fun gerarPdf() {
        if (filiaisSelecionadas.isEmpty()) { msgErro = "Selecione pelo menos uma filial"; return }
        if (periodoEmissao.isEmpty())      { msgErro = "Selecione o período de emissão"; return }
        if (anoSelecionado.isEmpty())      { msgErro = "Selecione o ano"; return }
        if (periodoEmissao == "0" && mesSelecionado.isEmpty()) { msgErro = "Selecione o mês"; return }
        if (modeloSelecionadoId < 0)       { msgErro = "Selecione o modelo DRE"; return }
        msgErro = ""; msgSucesso = ""
        scope.launch {
            loadingPdf = true
            val svc = DreService(session.userToken)
            val r = svc.fetchRelatorioPdf(
                idFilialPrincipal = filiaisSelecionadas.first().idFilial,
                filiaisIds        = filiaisSelecionadas.map { it.idFilial },
                idDre             = modeloSelecionadoId,
                ano               = anoSelecionado,
                periodo           = periodoEmissao,
                mes               = if (periodoEmissao == "0") mesSelecionado else "",
                estruturaPronta   = estruturaPronta
            )
            if (r.success && r.data != null) {
                val tipoPeriodo = if (periodoEmissao == "0") "Mensal" else "Anual"
                val sufMes = if (periodoEmissao == "0") "_$mesSelecionado" else ""
                val nome = "DRE_${tipoPeriodo}_${anoSelecionado}${sufMes}_${System.currentTimeMillis()}.pdf"
                val ok = dreAbrirPdf(context, r.data, nome)
                if (ok) msgSucesso = "✅ PDF DRE gerado e aberto com sucesso!"
                else    msgErro    = "Erro ao abrir PDF no dispositivo."
            } else {
                msgErro = r.error ?: "Erro ao gerar relatório DRE"
            }
            loadingPdf = false
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    ScreenWithSidebar(
        navController = navController,
        title         = "D.R.E",
        currentRoute  = Screen.Dre.route
    ) { _ ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DreLight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DreCard {

                // ── Filiais ───────────────────────────────────────────────────
                DreLabel("Filiais *")
                Spacer(Modifier.height(6.dp))
                DreSelectRow(label = labelFiliais(), onClick = { showFilialModal = true })

                // Chips das filiais selecionadas
                if (filiaisSelecionadas.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    DreChipGroup(
                        labels   = filiaisSelecionadas.map { it.nomeFilial.ifEmpty { "Filial ${it.idFilial}" } },
                        onRemove = { idx ->
                            val filial = filiaisSelecionadas.getOrNull(idx)
                            filial?.let { FilialState.toggleFilial(it) }
                        }
                    )
                }

                Spacer(Modifier.height(20.dp))

                // ── Período de Emissão ────────────────────────────────────────
                DreLabel("Período de Emissão *")
                Spacer(Modifier.height(6.dp))
                DreSelectRow(
                    label   = DRE_PERIODOS.find { it.first == periodoEmissao }?.second ?: "Selecionar período",
                    isEmpty = periodoEmissao.isEmpty(),
                    onClick = { showPeriodoModal = true }
                )

                Spacer(Modifier.height(20.dp))

                // ── Ano ───────────────────────────────────────────────────────
                DreLabel("Selecione o Ano *")
                Spacer(Modifier.height(6.dp))
                DreSelectRow(
                    label   = anoSelecionado.ifEmpty { "Selecionar ano" },
                    isEmpty = anoSelecionado.isEmpty(),
                    onClick = { showAnoModal = true }
                )

                // ── Mês (apenas Mensal) ───────────────────────────────────────
                if (periodoEmissao == "0") {
                    Spacer(Modifier.height(20.dp))
                    DreLabel("Selecione o Mês *")
                    Spacer(Modifier.height(6.dp))
                    DreSelectRow(
                        label   = DRE_MESES.find { it.first == mesSelecionado }?.second ?: "Selecionar mês",
                        isEmpty = mesSelecionado.isEmpty(),
                        onClick = { showMesModal = true }
                    )
                }

                Spacer(Modifier.height(20.dp))

                // ── Modelo DRE ────────────────────────────────────────────────
                DreLabel("Modelo D.R.E *")
                Spacer(Modifier.height(6.dp))
                DreSelectRow(
                    label   = when {
                        loadingModelos         -> "Carregando modelos..."
                        modeloSelecionadoId < 0 -> "Selecionar modelo"
                        else                   -> modeloSelecionadoDesc
                    },
                    isEmpty = modeloSelecionadoId < 0,
                    onClick = { if (!loadingModelos) showModeloModal = true }
                )

                Spacer(Modifier.height(20.dp))

                // ── Estrutura Pronta ──────────────────────────────────────────
                DreLabel("Utilizar Estrutura Pronta?")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { estruturaPronta = !estruturaPronta }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (estruturaPronta) DreBlue else Color.White)
                            .then(
                                if (!estruturaPronta) Modifier.background(Color.White) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!estruturaPronta) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Transparent)
                            ) {
                                // borda simulada
                                Card(
                                    modifier = Modifier.fillMaxSize(),
                                    shape    = RoundedCornerShape(4.dp),
                                    colors   = CardDefaults.cardColors(containerColor = Color.Transparent),
                                    border   = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFD1D5DB))
                                ) {}
                            }
                        } else {
                            Icon(Icons.Default.Check, null, tint = Color.White,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Sim, utilizar estrutura pronta",
                        style = TextStyle(fontSize = 14.sp, color = DreDark))
                }

                Spacer(Modifier.height(24.dp))

                // ── Mensagens ─────────────────────────────────────────────────
                if (msgErro.isNotEmpty()) {
                    Text(msgErro, style = TextStyle(fontSize = 13.sp, color = DreRed))
                    Spacer(Modifier.height(8.dp))
                }
                if (msgSucesso.isNotEmpty()) {
                    Text(msgSucesso, style = TextStyle(fontSize = 13.sp, color = DreGreen))
                    Spacer(Modifier.height(8.dp))
                }

                // ── Botão Gerar PDF ───────────────────────────────────────────
                Button(
                    onClick  = { gerarPdf() },
                    enabled  = !loadingPdf,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = DreNavy)
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

        // ── Modal Filiais (multi-select) ──────────────────────────────────────
        if (showFilialModal) {
            DreFilialModal(
                filiais          = todasFiliais,
                selecionadas     = filiaisSelecionadas,
                onToggle         = { FilialState.toggleFilial(it) },
                onLimpar         = { /* mantém ao menos 1 */ },
                onDismiss        = { showFilialModal = false }
            )
        }

        // ── Modal Período ─────────────────────────────────────────────────────
        if (showPeriodoModal) {
            DreSimpleSelectModal(
                title    = "Período de Emissão",
                items    = DRE_PERIODOS,
                selected = periodoEmissao,
                onSelect = { v ->
                    periodoEmissao = v
                    if (v == "1") mesSelecionado = ""  // Anual → limpa mês
                    showPeriodoModal = false
                },
                onDismiss = { showPeriodoModal = false }
            )
        }

        // ── Modal Ano ─────────────────────────────────────────────────────────
        if (showAnoModal) {
            DreSimpleSelectModal(
                title     = "Selecione o Ano",
                items     = DRE_ANOS.map { it to it },
                selected  = anoSelecionado,
                onSelect  = { anoSelecionado = it; showAnoModal = false },
                onDismiss = { showAnoModal = false }
            )
        }

        // ── Modal Mês ─────────────────────────────────────────────────────────
        if (showMesModal) {
            DreSimpleSelectModal(
                title     = "Selecione o Mês",
                items     = DRE_MESES,
                selected  = mesSelecionado,
                onSelect  = { mesSelecionado = it; showMesModal = false },
                onDismiss = { showMesModal = false }
            )
        }

        // ── Modal Modelo DRE ──────────────────────────────────────────────────
        if (showModeloModal) {
            DreModeloModal(
                modelos      = modelosDre,
                loading      = loadingModelos,
                search       = searchModelo,
                onSearch     = { searchModelo = it },
                selected     = modeloSelecionadoId,
                onSelect     = { m ->
                    modeloSelecionadoId   = m.id
                    modeloSelecionadoDesc = m.descricao
                    showModeloModal = false
                    searchModelo    = ""
                },
                onDismiss    = { showModeloModal = false; searchModelo = "" }
            )
        }
    }
}

// ── Modal de filiais (multi-select) ───────────────────────────────────────────
@Composable
private fun DreFilialModal(
    filiais: List<Filial>,
    selecionadas: List<Filial>,
    onToggle: (Filial) -> Unit,
    onLimpar: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
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
                    Text("Selecionar Filiais",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DreDark))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = DreSlate)
                    }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))

                if (filiais.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                        Text("Nenhuma filial disponível",
                            style = TextStyle(fontSize = 14.sp, color = DreSlate),
                            textAlign = TextAlign.Center)
                    }
                } else {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(8.dp)
                    ) {
                        filiais.forEach { filial ->
                            val sel = selecionadas.any { it.idFilial == filial.idFilial }
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onToggle(filial) }
                                    .background(if (sel) Color(0xFFEFF6FF) else Color.White)
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        filial.nomeFilial.ifEmpty { "Filial ${filial.idFilial}" },
                                        style = TextStyle(
                                            fontSize   = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color      = if (sel) DreBlue else DreDark
                                        )
                                    )
                                    Text(
                                        buildString {
                                            if (filial.identificacaoInterna.isNotEmpty())
                                                append("${filial.identificacaoInterna} • ")
                                            append("ID: ${filial.idFilial}")
                                        },
                                        style = TextStyle(fontSize = 12.sp, color = DreSlate)
                                    )
                                }
                                Icon(
                                    if (sel) Icons.Default.Check else Icons.Default.KeyboardArrowDown,
                                    null,
                                    tint     = if (sel) DreBlue else Color(0xFFCCCCCC),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            HorizontalDivider(color = DreLight)
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("${selecionadas.size} selecionada(s)",
                        style = TextStyle(fontSize = 13.sp, color = DreSlate))
                    Button(
                        onClick = onDismiss,
                        shape   = RoundedCornerShape(8.dp),
                        colors  = ButtonDefaults.buttonColors(containerColor = DreBlue)
                    ) {
                        Text("Confirmar",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White))
                    }
                }
            }
        }
    }
}

// ── Modal simples radio (período, ano, mês) ───────────────────────────────────
@Composable
private fun DreSimpleSelectModal(
    title: String,
    items: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
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
                    Text(title,
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DreDark))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = DreSlate)
                    }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    items.forEach { (value, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onSelect(value) }
                                .background(if (value == selected) Color(0xFFEFF6FF) else Color.White)
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Radio visual
                            Box(
                                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxSize(),
                                    shape    = RoundedCornerShape(10.dp),
                                    colors   = CardDefaults.cardColors(containerColor = Color.Transparent),
                                    border   = androidx.compose.foundation.BorderStroke(
                                        2.dp,
                                        if (value == selected) DreBlue else Color(0xFFD1D5DB)
                                    )
                                ) {}
                                if (value == selected) {
                                    Box(
                                        Modifier.size(10.dp).clip(RoundedCornerShape(5.dp))
                                            .background(DreBlue)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                label,
                                style    = TextStyle(
                                    fontSize   = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color      = if (value == selected) DreBlue else DreDark
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HorizontalDivider(color = DreLight)
                    }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(
                        onClick  = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = DreBlue)
                    ) {
                        Text("Confirmar",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White))
                    }
                }
            }
        }
    }
}

// ── Modal de Modelos DRE (com busca) ─────────────────────────────────────────
@Composable
private fun DreModeloModal(
    modelos: List<ModeloDre>,
    loading: Boolean,
    search: String,
    onSearch: (String) -> Unit,
    selected: Int,
    onSelect: (ModeloDre) -> Unit,
    onDismiss: () -> Unit
) {
    val filtrados = remember(modelos, search) {
        if (search.isBlank()) modelos
        else modelos.filter { it.descricao.contains(search, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 580.dp),
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
                    Text("Modelo D.R.E",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DreDark))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = DreSlate)
                    }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Campo de busca
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                        .clip(RoundedCornerShape(12.dp)).background(DreLight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = DreSlate,
                        modifier = Modifier.padding(start = 12.dp))
                    TextField(
                        value         = search,
                        onValueChange = onSearch,
                        placeholder   = { Text("Buscar modelo...",
                            style = TextStyle(fontSize = 13.sp, color = DreSlate)) },
                        modifier      = Modifier.weight(1f),
                        colors        = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { onSearch("") }) {
                            Icon(Icons.Default.Close, null, tint = DreSlate, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Lista
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    when {
                        loading -> Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = DreBlue)
                                Spacer(Modifier.height(8.dp))
                                Text("Carregando modelos...",
                                    style = TextStyle(fontSize = 13.sp, color = DreSlate))
                            }
                        }
                        filtrados.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                            Text(
                                if (modelos.isEmpty()) "Nenhum modelo disponível" else "Nenhum resultado encontrado",
                                style = TextStyle(fontSize = 14.sp, color = DreSlate),
                                textAlign = TextAlign.Center
                            )
                        }
                        else -> filtrados.forEach { modelo ->
                            val sel = modelo.id == selected
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onSelect(modelo) }
                                    .background(if (sel) Color(0xFFEFF6FF) else Color.White)
                                    .padding(horizontal = 8.dp, vertical = 14.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(modelo.descricao,
                                        style = TextStyle(
                                            fontSize   = 14.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                            color      = if (sel) DreBlue else DreDark
                                        ))
                                    Text("ID: ${modelo.id}",
                                        style = TextStyle(fontSize = 12.sp, color = DreSlate))
                                }
                                if (sel) Icon(Icons.Default.Check, null, tint = DreBlue,
                                    modifier = Modifier.size(20.dp).padding(start = 8.dp))
                            }
                            HorizontalDivider(color = DreLight)
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(
                        onClick  = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = DreBlue)
                    ) {
                        Text("Fechar",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White))
                    }
                }
            }
        }
    }
}

// ── Chips das filiais selecionadas ────────────────────────────────────────────
@Composable
private fun DreChipGroup(labels: List<String>, onRemove: (Int) -> Unit) {
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
                        Text(label,
                            style    = TextStyle(fontSize = 11.sp, color = DreBlue),
                            modifier = Modifier.weight(1f), maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Close, null, tint = DreBlue,
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
private fun DreCard(content: @Composable ColumnScope.() -> Unit) {
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
private fun DreLabel(text: String) {
    Text(text, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DreDark))
}

@Composable
private fun DreSelectRow(label: String, isEmpty: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(Color.White)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DreLight)
                    .clickable { onClick() }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style    = TextStyle(fontSize = 13.sp,
                        color = if (isEmpty) DreSlate else DreDark),
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.KeyboardArrowDown, null, tint = DreSlate,
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun dreAbrirPdf(context: Context, bytes: ByteArray, fileName: String): Boolean = try {
    val dir  = File(context.cacheDir, "pdfs").also { it.mkdirs() }
    val file = File(dir, fileName).also { it.writeBytes(bytes) }
    val uri  = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    })
    true
} catch (e: Exception) {
    android.util.Log.e("DRE_PDF", "Erro: ${e.message}", e)
    false
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DreScreenPreview() {
    DreScreen(rememberNavController())
}
