package com.example.vstmobile.ui.screens.finance

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
private val TrlcNavy  = Color(0xFF1E3A8A)
private val TrlcBlue  = Color(0xFF3B82F6)
private val TrlcSlate = Color(0xFF64748B)
private val TrlcLight = Color(0xFFF1F5F9)
private val TrlcDark  = Color(0xFF1E293B)
private val TrlcGreen = Color(0xFF059669)
private val TrlcRed   = Color(0xFFEF4444)

private fun trlcFmtApi(d: Date)     = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d)
private fun trlcFmtDisplay(d: Date) = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d)

// ── Tela ──────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitulosReceberLocalCobrancaScreen(navController: NavHostController) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val scope   = rememberCoroutineScope()

    val filial = FilialState.selectedFilial

    // Filtros
    var locaisSelecionados  by remember { mutableStateOf<List<LocalCobrancaReceber>>(emptyList()) }
    var impComp             by remember { mutableStateOf(false) }
    var impCheque           by remember { mutableStateOf(false) }
    var impAdt              by remember { mutableStateOf(false) }
    var valorDe             by remember { mutableStateOf("") }
    var valorAte            by remember { mutableStateOf("") }
    var ordenarPor          by remember { mutableStateOf("DTVENCIMENTO") }
    var periodo             by remember { mutableStateOf("DTVENCIMENTO") }
    var dataInicio          by remember { mutableStateOf(Date()) }
    var dataFim             by remember { mutableStateOf(Date()) }

    // UI
    var showLocalModal      by remember { mutableStateOf(false) }
    var showOrdenacaoModal  by remember { mutableStateOf(false) }
    var showPeriodoModal    by remember { mutableStateOf(false) }
    var showDateIni         by remember { mutableStateOf(false) }
    var showDateFim         by remember { mutableStateOf(false) }
    var loadingPdf          by remember { mutableStateOf(false) }
    var msgErro             by remember { mutableStateOf("") }
    var msgSucesso          by remember { mutableStateOf("") }

    fun toggleLocal(local: LocalCobrancaReceber) {
        locaisSelecionados =
            if (locaisSelecionados.any { it.id == local.id })
                locaisSelecionados.filter { it.id != local.id }
            else
                locaisSelecionados + local
    }

    fun gerarPdf() {
        if (filial == null)               { msgErro = "Selecione uma filial"; return }
        if (locaisSelecionados.isEmpty()) { msgErro = "Selecione pelo menos um local de cobrança"; return }
        msgErro = ""; msgSucesso = ""
        scope.launch {
            loadingPdf = true
            val svc = TitulosReceberLocalCobrancaService(session.userToken)
            val r = svc.fetchRelatorioPdf(
                idEmpresa  = filial.idEmpresa.toString(),
                idFilial   = filial.idFilial.toString(),
                dtIni      = trlcFmtApi(dataInicio),
                dtFim      = trlcFmtApi(dataFim),
                periodo    = periodo,
                ordenarPor = ordenarPor,
                idsLocais  = locaisSelecionados.map { it.id },
                impComp    = impComp,
                impAdt     = impAdt,
                impCheque  = impCheque,
                valorDe    = valorDe,
                valorAte   = valorAte
            )
            if (r.success && r.data != null) {
                val nome = "titulos_receber_local_cobranca_${trlcFmtApi(dataInicio)}_${trlcFmtApi(dataFim)}.pdf"
                val ok = trlcAbrirPdf(context, r.data, nome)
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
        title         = "Títulos a Receber - Local Cobrança",
        currentRoute  = Screen.TitulosReceberLocalCobranca.route
    ) { _ ->

        // ── Sem filial ────────────────────────────────────────────────────────
        if (filial == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("📋", fontSize = 48.sp)
                    Text("Selecione uma filial",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TrlcDark))
                    Text("Abra o menu lateral e selecione\numa filial para continuar.",
                        style = TextStyle(fontSize = 14.sp, color = TrlcSlate),
                        textAlign = TextAlign.Center)
                }
            }
            return@ScreenWithSidebar
        }

        // ── Conteúdo ──────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TrlcLight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TrlcCard {

                // ── Local de Cobrança ─────────────────────────────────────────
                TrlcLabel("Local de Cobrança *")
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TrlcLight)
                        .clickable { showLocalModal = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        when {
                            locaisSelecionados.isEmpty() -> "Selecionar local(is) de cobrança"
                            locaisSelecionados.size == 1 -> locaisSelecionados.first().descricao
                            else -> "${locaisSelecionados.size} local(is) selecionado(s)"
                        },
                        style = TextStyle(
                            fontSize = 13.sp,
                            color    = if (locaisSelecionados.isNotEmpty()) TrlcDark else TrlcSlate
                        )
                    )
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = TrlcSlate,
                        modifier = Modifier.size(20.dp))
                }

                // Chips dos locais selecionados
                if (locaisSelecionados.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    TrlcChipGroup(locaisSelecionados.map { it.descricao }) { idx ->
                        toggleLocal(locaisSelecionados[idx])
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Opções ────────────────────────────────────────────────────
                TrlcLabel("Opções")
                Spacer(Modifier.height(8.dp))
                TrlcCheckbox("Listar informações complementares?", impComp)  { impComp   = it }
                TrlcCheckbox("Listar cheques emitidos?",           impCheque) { impCheque = it }
                TrlcCheckbox("Listar adiantamentos?",              impAdt)    { impAdt    = it }

                Spacer(Modifier.height(20.dp))

                // ── Valores ───────────────────────────────────────────────────
                TrlcLabel("Valores (opcional)")
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

                // ── Ordenar por ───────────────────────────────────────────────
                TrlcLabel("Ordenar por")
                Spacer(Modifier.height(6.dp))
                TrlcSelectRow(
                    label    = OPCOES_ORDENACAO_RECEBER.find { it.first == ordenarPor }?.second ?: ordenarPor,
                    onClick  = { showOrdenacaoModal = true }
                )

                Spacer(Modifier.height(20.dp))

                // ── Período ───────────────────────────────────────────────────
                TrlcLabel("Período")
                Spacer(Modifier.height(6.dp))
                TrlcSelectRow(
                    label   = OPCOES_PERIODO_RECEBER.find { it.first == periodo }?.second ?: periodo,
                    onClick = { showPeriodoModal = true }
                )

                Spacer(Modifier.height(20.dp))

                // ── Informe o Período (datas) ─────────────────────────────────
                TrlcLabel("Informe o Período *")
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("De", style = TextStyle(fontSize = 11.sp, color = TrlcSlate))
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(TrlcLight)
                                .clickable { showDateIni = true }
                                .padding(12.dp)
                        ) {
                            Text("📅 ${trlcFmtDisplay(dataInicio)}",
                                style = TextStyle(fontSize = 13.sp, color = TrlcDark))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Até", style = TextStyle(fontSize = 11.sp, color = TrlcSlate))
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(TrlcLight)
                                .clickable { showDateFim = true }
                                .padding(12.dp)
                        ) {
                            Text("📅 ${trlcFmtDisplay(dataFim)}",
                                style = TextStyle(fontSize = 13.sp, color = TrlcDark))
                        }
                    }
                }
                Text("Toque nos campos para selecionar as datas",
                    style    = TextStyle(fontSize = 11.sp, color = TrlcSlate),
                    modifier = Modifier.padding(top = 4.dp))

                Spacer(Modifier.height(24.dp))

                // ── Mensagens ─────────────────────────────────────────────────
                if (msgErro.isNotEmpty()) {
                    Text(msgErro, style = TextStyle(fontSize = 13.sp, color = TrlcRed))
                    Spacer(Modifier.height(8.dp))
                }
                if (msgSucesso.isNotEmpty()) {
                    Text(msgSucesso, style = TextStyle(fontSize = 13.sp, color = TrlcGreen))
                    Spacer(Modifier.height(8.dp))
                }

                // ── Botão PDF ─────────────────────────────────────────────────
                Button(
                    onClick  = { gerarPdf() },
                    enabled  = !loadingPdf,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = TrlcNavy)
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

        // ── Modal Local de Cobrança (multi-select) ────────────────────────────
        if (showLocalModal) {
            TrlcMultiSelectModal(
                title        = "Selecionar Local de Cobrança",
                items        = LOCAIS_COBRANCA_RECEBER.map { it.id.toString() to it.descricao },
                selectedKeys = locaisSelecionados.map { it.id.toString() }.toSet(),
                onToggle     = { key ->
                    val local = LOCAIS_COBRANCA_RECEBER.find { it.id.toString() == key }
                    local?.let { toggleLocal(it) }
                },
                onDismiss    = { showLocalModal = false },
                selectedCount = locaisSelecionados.size
            )
        }

        // ── Modal Ordenação (radio) ───────────────────────────────────────────
        if (showOrdenacaoModal) {
            TrlcSimpleSelectModal(
                title     = "Ordenar por",
                items     = OPCOES_ORDENACAO_RECEBER,
                selected  = ordenarPor,
                onSelect  = { ordenarPor = it; showOrdenacaoModal = false },
                onDismiss = { showOrdenacaoModal = false }
            )
        }

        // ── Modal Período (radio) ─────────────────────────────────────────────
        if (showPeriodoModal) {
            TrlcSimpleSelectModal(
                title     = "Selecionar Período",
                items     = OPCOES_PERIODO_RECEBER,
                selected  = periodo,
                onSelect  = { periodo = it; showPeriodoModal = false },
                onDismiss = { showPeriodoModal = false }
            )
        }
    }
}

// ── Modal multi-select ────────────────────────────────────────────────────────
@Composable
private fun TrlcMultiSelectModal(
    title: String,
    items: List<Pair<String, String>>,
    selectedKeys: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    selectedCount: Int
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
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
                    Text(title,
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TrlcDark))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = TrlcSlate)
                    }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Lista
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)
                ) {
                    items.forEach { (key, label) ->
                        val selected = key in selectedKeys
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onToggle(key) }
                                .background(if (selected) Color(0xFFEFF6FF) else Color.White)
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked         = selected,
                                onCheckedChange = { onToggle(key) },
                                colors          = CheckboxDefaults.colors(checkedColor = TrlcBlue)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                label,
                                style = TextStyle(
                                    fontSize   = 14.sp,
                                    color      = if (selected) TrlcBlue else TrlcDark,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                        HorizontalDivider(color = TrlcLight)
                    }
                }

                // Footer
                HorizontalDivider(color = Color(0xFFE5E7EB))
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("$selectedCount selecionado(s)",
                        style = TextStyle(fontSize = 13.sp, color = TrlcSlate))
                    Button(
                        onClick = onDismiss,
                        shape   = RoundedCornerShape(8.dp),
                        colors  = ButtonDefaults.buttonColors(containerColor = TrlcBlue)
                    ) {
                        Text("Confirmar",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White))
                    }
                }
            }
        }
    }
}

// ── Modal radio (single select) ───────────────────────────────────────────────
@Composable
private fun TrlcSimpleSelectModal(
    title: String,
    items: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(title,
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TrlcDark))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = TrlcSlate)
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
                        Text(
                            label,
                            style = TextStyle(
                                fontSize   = 14.sp,
                                color      = if (value == selected) TrlcBlue else TrlcDark,
                                fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                        if (value == selected)
                            Text("✓", style = TextStyle(fontSize = 16.sp, color = TrlcBlue, fontWeight = FontWeight.Bold))
                    }
                    HorizontalDivider(color = TrlcLight)
                }
            }
        }
    }
}

// ── Chips dos locais selecionados ─────────────────────────────────────────────
@Composable
private fun TrlcChipGroup(labels: List<String>, onRemove: (Int) -> Unit) {
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
                            style    = TextStyle(fontSize = 11.sp, color = TrlcBlue),
                            modifier = Modifier.weight(1f),
                            maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Close, null, tint = TrlcBlue,
                            modifier = Modifier.size(14.dp).clickable { onRemove(idx) }
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
@Composable
private fun TrlcCard(content: @Composable ColumnScope.() -> Unit) {
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
private fun TrlcLabel(text: String) {
    Text(text, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TrlcDark))
}

@Composable
private fun TrlcSelectRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TrlcLight)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, style = TextStyle(fontSize = 13.sp, color = TrlcDark))
        Icon(Icons.Default.KeyboardArrowDown, null, tint = TrlcSlate, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TrlcCheckbox(label: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheck(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked         = checked,
            onCheckedChange = onCheck,
            colors          = CheckboxDefaults.colors(checkedColor = TrlcBlue)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = TextStyle(fontSize = 13.sp, color = TrlcDark))
    }
}

private fun trlcAbrirPdf(context: Context, bytes: ByteArray, fileName: String): Boolean = try {
    val dir  = File(context.cacheDir, "pdfs").also { it.mkdirs() }
    val file = File(dir, fileName).also { it.writeBytes(bytes) }
    val uri  = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    })
    true
} catch (e: Exception) {
    android.util.Log.e("TRLC_PDF", "Erro: ${e.message}", e)
    false
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun TitulosReceberLocalCobrancaScreenPreview() {
    TitulosReceberLocalCobrancaScreen(rememberNavController())
}
