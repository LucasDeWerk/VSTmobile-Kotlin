package com.example.vstmobile.ui.screens.inventory

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.vstmobile.navigation.Screen
import com.example.vstmobile.services.*
import kotlinx.coroutines.launch

// ── Paleta ────────────────────────────────────────────────────────────────────
private val CntNavy  = Color(0xFF1E3A8A)
private val CntBlue  = Color(0xFF3B82F6)
private val CntSlate = Color(0xFF64748B)
private val CntLight = Color(0xFFF1F5F9)
private val CntDark  = Color(0xFF1E293B)
private val CntGreen = Color(0xFF059669)
private val CntAmber = Color(0xFFF59E0B)
private val CntRed   = Color(0xFFEF4444)
private val CntEven  = Color(0xFFF8FAFC)

// ── Modelo local de produto com estado de contagem ────────────────────────────
data class ProdutoContagem(
    val idProduto: Int,
    val descricao: String,
    val qtdContada: String,           // "Não Contado" ou valor
    val idAlmoxarifado: Int = 1,
    val quantidade: Double = 0.0,    // estoque
    val abreviatura: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountingScreen(
    navController: NavHostController,
    idEmpresa: Int,
    idFilial: Int,
    idInventario: Int
) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val scope   = rememberCoroutineScope()
    val svc     = remember(session.userToken) { CountingService(session.userToken) }

    // ── Estado ────────────────────────────────────────────────────────────────
    var produtos        by remember { mutableStateOf<List<ProdutoContagem>>(emptyList()) }
    var loading         by remember { mutableStateOf(true) }
    var salvando        by remember { mutableStateOf<Int?>(null) }   // idProduto sendo salvo

    // ── Modal entrada manual ──────────────────────────────────────────────────
    var produtoSel      by remember { mutableStateOf<ProdutoContagem?>(null) }
    var showManual      by remember { mutableStateOf(false) }
    var qtdManual       by remember { mutableStateOf("") }

    // ── Modal escolha de método ───────────────────────────────────────────────
    var showMetodoModal by remember { mutableStateOf(false) }

    // ── Modal seleção de formato (IA) ─────────────────────────────────────────
    var showFormatoModal by remember { mutableStateOf(false) }
    var formatoSelecionado by remember { mutableStateOf("") }

    // ── Carregar produtos ─────────────────────────────────────────────────────
    fun carregarProdutos(idInv: Int = idInventario) {
        scope.launch {
            loading = true
            val r = svc.fetchProdutosInventario(idEmpresa, idFilial, idInv)
            if (r.success && r.data != null) {
                // A API retorna ProdutoItemInventario; convertemos para ProdutoContagem
                // Note: a API de contagem pode retornar campos extras (QUANTIDADE, ID_ALMOXARIFADO)
                // então fazemos o parse direto no CountingService via JSON bruto.
                // Aqui recebemos o modelo simplificado e completamos com defaults.
                produtos = r.data.map { p ->
                    ProdutoContagem(
                        idProduto      = p.idProduto,
                        descricao      = p.descricao,
                        qtdContada     = p.qtdContada,
                        idAlmoxarifado = 1,
                        quantidade     = 0.0,
                        abreviatura    = "un"
                    )
                }
            } else {
                Toast.makeText(context, "Erro ao carregar produtos", Toast.LENGTH_SHORT).show()
            }
            loading = false
        }
    }

    LaunchedEffect(idInventario) { carregarProdutos() }

    // ── Verificar contagem completa ───────────────────────────────────────────
    fun verificarCompleto(lista: List<ProdutoContagem>) {
        val pendentes = lista.count { it.qtdContada == "Não Contado" }
        if (lista.isNotEmpty() && pendentes == 0) {
            Toast.makeText(context, "🎉 Todos os produtos foram contados!", Toast.LENGTH_LONG).show()
        }
    }

    // ── Salvar contagem individual ────────────────────────────────────────────
    fun salvarContagem(produto: ProdutoContagem, qtd: Double) {
        scope.launch {
            salvando = produto.idProduto
            val r = svc.salvarContagem(
                idEmpresa      = idEmpresa,
                idFilial       = idFilial,
                idInventario   = idInventario,
                idProduto      = produto.idProduto,
                idAlmoxarifado = produto.idAlmoxarifado,
                qtdContada     = qtd,
                qtdEstoque     = produto.quantidade
            )
            salvando = null
            if (r.success) {
                // Atualizar localmente
                produtos = produtos.map { p ->
                    if (p.idProduto == produto.idProduto) p.copy(qtdContada = qtd.toInt().toString())
                    else p
                }
                Toast.makeText(context, "✅ Contagem salva!", Toast.LENGTH_SHORT).show()
                // Recarregar se a API retornou novo ID
                val novoId = r.inventarioId
                if (novoId != null && novoId != idInventario) carregarProdutos(novoId)
                else verificarCompleto(produtos)
                showManual = false
                qtdManual  = ""
                produtoSel = null
            } else {
                Toast.makeText(context, "❌ Erro ao salvar: ${r.error}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────
    val contados  = produtos.count { it.qtdContada != "Não Contado" }
    val pendentes = produtos.count { it.qtdContada == "Não Contado" }
    val total     = produtos.size
    val progresso = if (total > 0) contados.toFloat() / total.toFloat() else 0f

    // ── Layout ────────────────────────────────────────────────────────────────
    Box(Modifier.fillMaxSize().background(CntLight)) {
        Column(Modifier.fillMaxSize()) {

            // ── TopBar ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CntNavy)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "Contagem de Produtos",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CntNavy),
                    modifier = Modifier.weight(1f)
                )
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = CntBlue, strokeWidth = 2.dp)
                }
            }
            HorizontalDivider(color = Color(0xFFE5E7EB))

            // ── Conteúdo scrollável ───────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ── Card info inventário ──────────────────────────────────────
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(12.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Parâmetros do Inventário",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CntNavy))
                        CntInfoRow("Código",     "$idInventario")
                        CntInfoRow("ID Empresa", "$idEmpresa")
                        CntInfoRow("ID Filial",  "$idFilial")
                        if (total > 0) CntInfoRow("Total de Produtos", "$total")
                    }
                }

                // ── Card progresso ────────────────────────────────────────────
                if (total > 0) {
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(12.dp),
                        colors    = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = CntNavy, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Progresso da Contagem",
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CntNavy))
                            }
                            // Stats
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                CntStat("$contados",  "Contados",  CntGreen)
                                Box(Modifier.width(1.dp).height(40.dp).background(CntLight))
                                CntStat("$pendentes", "Pendentes", CntAmber)
                                Box(Modifier.width(1.dp).height(40.dp).background(CntLight))
                                CntStat("$total",     "Total",     CntSlate)
                            }
                            // Barra de progresso
                            Box(
                                modifier = Modifier.fillMaxWidth().height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)).background(CntLight)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progresso)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CntGreen)
                                )
                            }
                            Text(
                                "%.0f%%".format(progresso * 100),
                                style = TextStyle(fontSize = 12.sp, color = CntSlate, fontWeight = FontWeight.Bold),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }

                // ── Lista de produtos ─────────────────────────────────────────
                when {
                    loading -> {
                        Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = CntBlue)
                                Spacer(Modifier.height(8.dp))
                                Text("Carregando produtos...",
                                    style = TextStyle(fontSize = 13.sp, color = CntSlate))
                            }
                        }
                    }
                    produtos.isEmpty() -> {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📦", fontSize = 40.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Nenhum produto encontrado",
                                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CntDark))
                                    Text("Verifique os parâmetros do inventário.",
                                        style = TextStyle(fontSize = 13.sp, color = CntSlate),
                                        textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                    else -> {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)) {
                            Column(Modifier.fillMaxWidth()) {
                                // Header lista
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(CntNavy)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Produtos do Inventário",
                                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White))
                                    Text("Selecione para contar",
                                        style = TextStyle(fontSize = 11.sp, color = Color.White.copy(alpha = .7f)))
                                }

                                produtos.forEachIndexed { i, p ->
                                    val contado   = p.qtdContada != "Não Contado"
                                    val isSaving  = salvando == p.idProduto

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                when {
                                                    contado -> Color(0xFFECFDF5)
                                                    i % 2 == 0 -> CntEven
                                                    else -> Color.White
                                                }
                                            )
                                        .clickable(enabled = !isSaving) {
                                            produtoSel  = p
                                            showMetodoModal = true
                                        }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Ícone status
                                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                            if (isSaving) {
                                                CircularProgressIndicator(Modifier.size(20.dp), color = CntBlue, strokeWidth = 2.dp)
                                            } else if (contado) {
                                                Box(
                                                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(14.dp))
                                                        .background(CntGreen),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(14.dp))
                                                        .background(CntLight),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Edit, null, tint = CntSlate, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }

                                        Spacer(Modifier.width(10.dp))

                                        // Informações
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "ID: ${p.idProduto}",
                                                style = TextStyle(fontSize = 11.sp, color = CntSlate)
                                            )
                                            Text(
                                                p.descricao.ifEmpty { "Sem descrição" },
                                                style = TextStyle(
                                                    fontSize   = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color      = CntDark
                                                ),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                // Qtd contada
                                                Column {
                                                    Text("Qtd. Contada",
                                                        style = TextStyle(fontSize = 10.sp, color = CntSlate))
                                                    Text(
                                                        if (contado) p.qtdContada else "Pendente",
                                                        style = TextStyle(
                                                            fontSize   = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color      = if (contado) CntGreen else CntAmber
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        // Seta
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            null,
                                            tint     = CntSlate,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    if (i < produtos.lastIndex) HorizontalDivider(color = CntLight)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Botão fixo "Finalizar Contagem" ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(CntLight)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Button(
                onClick  = {
                    navController.navigate(Screen.Inventory.route) {
                        popUpTo(Screen.Inventory.route) { inclusive = false }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = CntNavy)
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Finalizar Contagem",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White))
            }
        }

        // ── Modal entrada manual ──────────────────────────────────────────────
        if (showManual && produtoSel != null) {
            val p = produtoSel!!
            val contado = p.qtdContada != "Não Contado"
            val diff = qtdManual.toDoubleOrNull()?.minus(p.quantidade) ?: 0.0

            Dialog(onDismissRequest = { showManual = false; qtdManual = ""; produtoSel = null }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(20.dp),
                    colors   = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC))
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Entrada Manual",
                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CntNavy))
                            IconButton(onClick = { showManual = false; qtdManual = ""; produtoSel = null }) {
                                Icon(Icons.Default.Close, null, tint = CntSlate)
                            }
                        }
                        HorizontalDivider(color = Color(0xFFE5E7EB))
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = CntLight)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Produto Selecionado",
                                        style = TextStyle(fontSize = 11.sp, color = CntSlate, fontWeight = FontWeight.Bold))
                                    Text(p.descricao.ifEmpty { "Sem descrição" },
                                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CntDark))
                                    Text("ID: ${p.idProduto}",
                                        style = TextStyle(fontSize = 12.sp, color = CntSlate))
                                    HorizontalDivider(color = Color(0xFFE5E7EB), modifier = Modifier.padding(vertical = 4.dp))
                                    if (contado) {
                                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                            Text("Qtd. Contada:", style = TextStyle(fontSize = 12.sp, color = CntSlate))
                                            Text(p.qtdContada, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CntGreen))
                                        }
                                    }
                                }
                            }
                            Text("Quantidade de Unidades:",
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CntDark))
                            OutlinedTextField(
                                value         = qtdManual,
                                onValueChange = { qtdManual = it.filter { c -> c.isDigit() || c == '.' } },
                                modifier      = Modifier.fillMaxWidth(),
                                placeholder   = { Text("Digite o número de unidades...", style = TextStyle(fontSize = 13.sp, color = CntSlate)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine    = true,
                                shape         = RoundedCornerShape(10.dp),
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = CntBlue,
                                    unfocusedBorderColor = Color(0xFFD1D5DB)
                                )
                            )
                            if (qtdManual.isNotBlank() && qtdManual.toDoubleOrNull() != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (diff >= 0) Color(0xFFECFDF5) else Color(0xFFFEF2F2))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Diferença calculada:", style = TextStyle(fontSize = 12.sp, color = CntSlate))
                                    Text(
                                        "${if (diff >= 0) "+" else ""}${"%.0f".format(diff)} un",
                                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            color = if (diff >= 0) CntGreen else CntRed)
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick  = { showManual = false; qtdManual = ""; produtoSel = null },
                                    modifier = Modifier.weight(1f),
                                    shape    = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Cancelar", style = TextStyle(fontSize = 13.sp, color = CntSlate))
                                }
                                Button(
                                    onClick  = {
                                        val qtd = qtdManual.toDoubleOrNull()
                                        if (qtd == null) {
                                            Toast.makeText(context, "Digite uma quantidade válida", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        salvarContagem(p, qtd)
                                    },
                                    enabled  = qtdManual.isNotBlank() && qtdManual.toDoubleOrNull() != null && salvando == null,
                                    modifier = Modifier.weight(1f),
                                    shape    = RoundedCornerShape(10.dp),
                                    colors   = ButtonDefaults.buttonColors(containerColor = CntNavy)
                                ) {
                                    if (salvando == p.idProduto) {
                                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Text("Salvar Quantidade",
                                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Modal escolha de método ───────────────────────────────────────────
        if (showMetodoModal && produtoSel != null) {
            val p = produtoSel!!
            Dialog(onDismissRequest = { showMetodoModal = false }) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC))
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Método de Contagem",
                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CntNavy))
                            IconButton(onClick = { showMetodoModal = false }) {
                                Icon(Icons.Default.Close, null, tint = CntSlate)
                            }
                        }
                        HorizontalDivider(color = Color(0xFFE5E7EB))
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(p.descricao.ifEmpty { "Sem descrição" },
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CntDark),
                                maxLines = 2)
                            Text("ID: ${p.idProduto}", style = TextStyle(fontSize = 11.sp, color = CntSlate))
                            HorizontalDivider(color = Color(0xFFE5E7EB))
                            Button(
                                onClick = {
                                    showMetodoModal = false
                                    qtdManual = if (p.qtdContada != "Não Contado") p.qtdContada else ""
                                    showManual = true
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CntBlue)
                            ) {
                                Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Inserir Manualmente",
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White))
                            }
                            Button(
                                onClick = {
                                    showMetodoModal = false
                                    formatoSelecionado = ""
                                    showFormatoModal = true
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CntNavy)
                            ) {
                                Text("🤖", fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Text("Contagem por IA",
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White))
                            }
                            OutlinedButton(
                                onClick = { showMetodoModal = false },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancelar", style = TextStyle(fontSize = 13.sp, color = CntSlate))
                            }
                        }
                    }
                }
            }
        }

        // ── Modal seleção de formato (para IA) ───────────────────────────────
        if (showFormatoModal && produtoSel != null) {
            val p = produtoSel!!
            val formatos = listOf(
                Triple("redondo",  "Tubo Redondo",   "⭕"),
                Triple("quadrado", "Tubo Quadrado",  "⬛"),
                Triple("barra",    "Barra de Metal", "➖")
            )
            Dialog(onDismissRequest = { showFormatoModal = false }) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC))
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Selecionar Formato",
                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CntNavy))
                            IconButton(onClick = { showFormatoModal = false }) {
                                Icon(Icons.Default.Close, null, tint = CntSlate)
                            }
                        }
                        HorizontalDivider(color = Color(0xFFE5E7EB))
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            formatos.forEach { (id, label, emoji) ->
                                val selected = formatoSelecionado == id
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) CntNavy else CntLight)
                                        .clickable { formatoSelecionado = id }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(emoji, fontSize = 24.sp)
                                    Text(label, style = TextStyle(
                                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                        color = if (selected) Color.White else CntDark),
                                        modifier = Modifier.weight(1f))
                                    if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            HorizontalDivider(color = Color(0xFFE5E7EB))
                            Button(
                                onClick = {
                                    if (formatoSelecionado.isBlank()) return@Button
                                    showFormatoModal = false
                                    val route = "ai_counting/$idEmpresa/$idFilial/$idInventario/${p.idProduto}/${p.idAlmoxarifado}/${p.quantidade}/$formatoSelecionado?desc=${java.net.URLEncoder.encode(p.descricao, "UTF-8")}"
                                    navController.navigate(route)
                                },
                                enabled = formatoSelecionado.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CntNavy)
                            ) {
                                Text("🤖", fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Text("Iniciar Contagem por IA",
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White))
                            }
                        }
                    }
                }
            }
        }
    } // fecha Box principal
} // fecha CountingScreen

// ── Helpers de UI ─────────────────────────────────────────────────────────────
@Composable
private fun CntInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = TextStyle(fontSize = 12.sp, color = CntSlate))
        Text(value, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CntDark))
    }
}

@Composable
private fun CntStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color))
        Text(label, style = TextStyle(fontSize = 11.sp, color = CntSlate))
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CountingScreenPreview() {
    CountingScreen(
        navController = rememberNavController(),
        idEmpresa    = 1,
        idFilial     = 1,
        idInventario = 123
    )
}

