package com.example.vstmobile.ui.screens.inventory

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.vstmobile.navigation.Screen
import com.example.vstmobile.services.*import com.example.vstmobile.ui.components.ScreenWithSidebar
import kotlinx.coroutines.launch

// ── Paleta ────────────────────────────────────────────────────────────────────
private val InvNavy  = Color(0xFF1E3A8A)
private val InvBlue  = Color(0xFF3B82F6)
private val InvSlate = Color(0xFF64748B)
private val InvLight = Color(0xFFF1F5F9)
private val InvDark  = Color(0xFF1E293B)
private val InvGreen = Color(0xFF059669)
private val InvRed   = Color(0xFFEF4444)
private val InvAmber = Color(0xFFF59E0B)
private val InvEven  = Color(0xFFF8FAFC)

// ── Tela principal ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(navController: NavHostController) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val scope   = rememberCoroutineScope()
    val filial  = FilialState.selectedFilial

    // ── Filtros de formulário ─────────────────────────────────────────────────
    var almoxarifadoId    by remember { mutableStateOf<Int?>(null) }
    var almoxarifadoDesc  by remember { mutableStateOf("") }
    var produtoNome       by remember { mutableStateOf("") }
    var produtoSelecionado by remember { mutableStateOf<ProdutoInventario?>(null) }
    var grupoNome         by remember { mutableStateOf("") }
    var grupoSelecionado  by remember { mutableStateOf<GrupoInventario?>(null) }
    var subgrupoNome      by remember { mutableStateOf("") }
    var subgrupoSelecionado by remember { mutableStateOf<SubgrupoInventario?>(null) }
    var marcaNome         by remember { mutableStateOf("") }
    var marcaSelecionada  by remember { mutableStateOf<MarcaInventario?>(null) }
    var codigoSelecionado by remember { mutableStateOf<CodigoInventario?>(null) }

    // ── Dados ─────────────────────────────────────────────────────────────────
    var almoxarifados     by remember { mutableStateOf<List<Almoxarifado>>(emptyList()) }
    var codigosSalvos     by remember { mutableStateOf<List<CodigoInventario>>(emptyList()) }
    var resultadosBusca   by remember { mutableStateOf<List<ProdutoInventario>>(emptyList()) }
    var produtosInventario by remember { mutableStateOf<List<ProdutoItemInventario>>(emptyList()) }
    var produtosPesquisa  by remember { mutableStateOf<List<ProdutoInventario>>(emptyList()) }
    var gruposPesquisa    by remember { mutableStateOf<List<GrupoInventario>>(emptyList()) }
    var subgruposPesquisa by remember { mutableStateOf<List<SubgrupoInventario>>(emptyList()) }
    var marcasPesquisa    by remember { mutableStateOf<List<MarcaInventario>>(emptyList()) }

    // ── Seleção de checkboxes ─────────────────────────────────────────────────
    var selecionados      by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var paginaAtual       by remember { mutableStateOf(1) }
    val itensPorPagina    = 6

    // ── Loading ───────────────────────────────────────────────────────────────
    var loadingAlmox      by remember { mutableStateOf(false) }
    var loadingCodigos    by remember { mutableStateOf(false) }
    var loadingItens      by remember { mutableStateOf(false) }
    var loadingProdutos   by remember { mutableStateOf(false) }
    var loadingGrupos     by remember { mutableStateOf(false) }
    var loadingSubgrupos  by remember { mutableStateOf(false) }
    var loadingMarcas     by remember { mutableStateOf(false) }
    var loadingBusca      by remember { mutableStateOf(false) }
    var loadingSalvar     by remember { mutableStateOf(false) }

    // ── Modais ────────────────────────────────────────────────────────────────
    var showAlmoxModal    by remember { mutableStateOf(false) }
    var showCodigoModal   by remember { mutableStateOf(false) }
    var showProdutoModal  by remember { mutableStateOf(false) }
    var showGrupoModal    by remember { mutableStateOf(false) }
    var showSubgrupoModal by remember { mutableStateOf(false) }
    var showMarcaModal    by remember { mutableStateOf(false) }

    // ── Filtros internos dos modais ───────────────────────────────────────────
    var filtroAlmox       by remember { mutableStateOf("") }
    var filtroCodigo      by remember { mutableStateOf("") }
    var filtroProduto     by remember { mutableStateOf("") }
    var filtroGrupo       by remember { mutableStateOf("") }
    var filtroSubgrupo    by remember { mutableStateOf("") }
    var filtroMarca       by remember { mutableStateOf("") }

    val svc = remember(session.userToken) { InventoryService(session.userToken) }

    // ── Paginação ─────────────────────────────────────────────────────────────
    val totalPaginas  = maxOf(1, (resultadosBusca.size + itensPorPagina - 1) / itensPorPagina)
    val itensAtual    = resultadosBusca.drop((paginaAtual - 1) * itensPorPagina).take(itensPorPagina)

    // ── Limpar tudo ───────────────────────────────────────────────────────────
    fun limpar() {
        almoxarifadoId = null; almoxarifadoDesc = ""
        produtoNome = ""; produtoSelecionado = null
        grupoNome = ""; grupoSelecionado = null
        subgrupoNome = ""; subgrupoSelecionado = null
        marcaNome = ""; marcaSelecionada = null
        codigoSelecionado = null; codigosSalvos = emptyList()
        resultadosBusca = emptyList(); produtosInventario = emptyList()
        selecionados = emptySet(); paginaAtual = 1
    }

    // ── Buscar almoxarifados ──────────────────────────────────────────────────
    fun abrirAlmoxarifados() {
        if (filial == null) { Toast.makeText(context, "Selecione uma filial", Toast.LENGTH_SHORT).show(); return }
        scope.launch {
            loadingAlmox = true
            val r = svc.fetchAlmoxarifados(filial.idEmpresa, filial.idFilial)
            almoxarifados = r.data ?: emptyList()
            loadingAlmox = false
            showAlmoxModal = true
        }
    }

    // ── Buscar códigos de inventário ──────────────────────────────────────────
    fun abrirCodigos() {
        if (filial == null) { Toast.makeText(context, "Selecione uma filial", Toast.LENGTH_SHORT).show(); return }
        showCodigoModal = true
        if (codigosSalvos.isEmpty()) {
            scope.launch {
                loadingCodigos = true
                val r = svc.fetchCodigosInventario(filial.idEmpresa, filial.idFilial)
                codigosSalvos = r.data ?: emptyList()
                loadingCodigos = false
            }
        }
    }

    // ── Selecionar código e buscar itens ──────────────────────────────────────
    fun selecionarCodigo(codigo: CodigoInventario) {
        codigoSelecionado = codigo
        showCodigoModal = false
        scope.launch {
            loadingItens = true
            val r = svc.fetchItensInventario(codigo.idEmpresa, codigo.idFilial, codigo.idInventario)
            produtosInventario = r.data ?: emptyList()
            loadingItens = false
        }
    }

    // ── Buscar produtos ───────────────────────────────────────────────────────
    fun buscarProdutos(q: String) {
        if (filial == null) return
        scope.launch {
            loadingProdutos = true
            val r = svc.fetchProdutos(filial.idEmpresa, filial.idFilial, q)
            produtosPesquisa = r.data ?: emptyList()
            loadingProdutos = false
            showProdutoModal = true
        }
    }

    // ── Buscar grupos ─────────────────────────────────────────────────────────
    fun buscarGrupos(q: String) {
        if (filial == null) return
        scope.launch {
            loadingGrupos = true
            val r = svc.fetchGrupos(filial.idEmpresa, q)
            gruposPesquisa = r.data ?: emptyList()
            loadingGrupos = false
            showGrupoModal = true
        }
    }

    // ── Buscar subgrupos ──────────────────────────────────────────────────────
    fun buscarSubgrupos(q: String) {
        val g = grupoSelecionado ?: run {
            Toast.makeText(context, "Selecione um grupo primeiro", Toast.LENGTH_SHORT).show(); return
        }
        if (filial == null) return
        scope.launch {
            loadingSubgrupos = true
            val r = svc.fetchSubgrupos(filial.idEmpresa, g.id, q)
            subgruposPesquisa = r.data ?: emptyList()
            loadingSubgrupos = false
            showSubgrupoModal = true
        }
    }

    // ── Buscar marcas ─────────────────────────────────────────────────────────
    fun buscarMarcas(q: String) {
        if (filial == null) return
        scope.launch {
            loadingMarcas = true
            val r = svc.fetchMarcas(filial.idEmpresa, q)
            marcasPesquisa = r.data ?: emptyList()
            loadingMarcas = false
            showMarcaModal = true
        }
    }

    // ── Buscar inventário ─────────────────────────────────────────────────────
    fun buscarInventario() {
        val alm = almoxarifadoId ?: run {
            Toast.makeText(context, "Selecione um almoxarifado", Toast.LENGTH_SHORT).show(); return
        }
        if (filial == null) return
        scope.launch {
            loadingBusca = true; paginaAtual = 1
            val r = svc.buscarInventario(
                filial.idEmpresa, filial.idFilial, alm,
                idProduto  = produtoSelecionado?.idProduto,
                idGrupo    = grupoSelecionado?.id,
                idSubgrupo = subgrupoSelecionado?.id,
                idMarca    = marcaSelecionada?.id
            )
            val novos = r.data ?: emptyList()
            // manter selecionados + adicionar novos sem duplicar
            resultadosBusca = (resultadosBusca.filter { it.idProduto in selecionados } +
                    novos.filter { n -> resultadosBusca.none { it.idProduto == n.idProduto && it.idProduto in selecionados } })
                .distinctBy { it.idProduto }
                .let { prev -> (novos + prev).distinctBy { it.idProduto } }
            // limpar filtros específicos
            produtoSelecionado = null; produtoNome = ""
            grupoSelecionado = null; grupoNome = ""
            subgrupoSelecionado = null; subgrupoNome = ""
            marcaSelecionada = null; marcaNome = ""
            loadingBusca = false
            Toast.makeText(context, "${novos.size} produto(s) encontrado(s)", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Salvar inventário ─────────────────────────────────────────────────────
    fun salvar() {
        val alm = almoxarifadoId ?: run {
            Toast.makeText(context, "Selecione um almoxarifado", Toast.LENGTH_SHORT).show(); return
        }
        if (selecionados.isEmpty()) {
            Toast.makeText(context, "Selecione pelo menos um produto", Toast.LENGTH_SHORT).show(); return
        }
        if (filial == null) return
        scope.launch {
            loadingSalvar = true
            val r = svc.salvarInventario(filial.idEmpresa, filial.idFilial, alm, selecionados.toList())
            loadingSalvar = false
            if (r.success) {
                val msg = "Inventário salvo!\n${selecionados.size} produto(s)" +
                        if (r.data != null) "\nCódigo: ${r.data}" else ""
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                limpar()
            } else {
                Toast.makeText(context, "Erro: ${r.error}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    ScreenWithSidebar(
        navController = navController,
        title         = "Inventário",
        currentRoute  = Screen.Inventory.route
    ) { _ ->

        if (filial == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🏭", fontSize = 48.sp)
                    Text("Selecione uma filial",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = InvDark))
                    Text("Abra o menu lateral e selecione\numa filial para continuar.",
                        style = TextStyle(fontSize = 14.sp, color = InvSlate), textAlign = TextAlign.Center)
                }
            }
            return@ScreenWithSidebar
        }

        Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(InvLight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = 80.dp),   // espaço para o botão fixo
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Barra de ações ─────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { limpar() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Limpar", style = TextStyle(fontSize = 13.sp, color = InvSlate)) }

                Button(
                    onClick  = { buscarInventario() },
                    enabled  = !loadingBusca,
                    modifier = Modifier.weight(2f),
                    shape    = RoundedCornerShape(8.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = InvBlue)
                ) {
                    if (loadingBusca) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Buscando...", style = TextStyle(fontSize = 13.sp, color = Color.White))
                    } else {
                        Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Buscar Produtos", style = TextStyle(fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold))
                    }
                }
            }

            // ── Card de formulário ─────────────────────────────────────────────
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(12.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // ── Código ─────────────────────────────────────────────────
                    InvLabel("Código de Inventário")
                    InvPickerRow(
                        text      = codigoSelecionado?.let { "Código ${it.idInventario}" } ?: "Selecione um código...",
                        isEmpty   = codigoSelecionado == null,
                        loading   = loadingCodigos,
                        onClick   = { abrirCodigos() }
                    )

                    // ── Almoxarifado ───────────────────────────────────────────
                    InvLabel("Almoxarifado *")
                    InvPickerRow(
                        text    = almoxarifadoDesc.ifEmpty { "Selecione um almoxarifado..." },
                        isEmpty = almoxarifadoId == null,
                        loading = loadingAlmox,
                        onClick = { abrirAlmoxarifados() }
                    )

                    // ── Produto ────────────────────────────────────────────────
                    InvSearchField(
                        label   = "Produto",
                        value   = produtoNome,
                        onChange = { produtoNome = it; produtoSelecionado = null },
                        onSearch = { buscarProdutos(produtoNome) },
                        loading = loadingProdutos,
                        placeholder = "Digite o nome do produto..."
                    )

                    // ── Grupo ──────────────────────────────────────────────────
                    InvSearchField(
                        label   = "Grupo",
                        value   = grupoNome,
                        onChange = { grupoNome = it; grupoSelecionado = null },
                        onSearch = { buscarGrupos(grupoNome) },
                        loading = loadingGrupos,
                        placeholder = "Digite o nome do grupo..."
                    )

                    // ── Subgrupo ───────────────────────────────────────────────
                    InvLabel("Subgrupo")
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (grupoSelecionado != null) InvLight else Color(0xFFE5E7EB)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value         = subgrupoNome,
                            onValueChange = { if (grupoSelecionado != null) { subgrupoNome = it; subgrupoSelecionado = null } },
                            placeholder   = {
                                Text(
                                    if (grupoSelecionado != null) "Digite o nome do subgrupo..." else "Selecione um grupo primeiro",
                                    style = TextStyle(fontSize = 13.sp, color = InvSlate)
                                )
                            },
                            enabled = grupoSelecionado != null,
                            modifier = Modifier.weight(1f),
                            colors   = TextFieldDefaults.colors(
                                focusedContainerColor   = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor  = Color.Transparent,
                                focusedIndicatorColor   = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor  = Color.Transparent
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { buscarSubgrupos(subgrupoNome) })
                        )
                        IconButton(
                            onClick  = { if (grupoSelecionado != null) buscarSubgrupos(subgrupoNome) },
                            enabled  = grupoSelecionado != null && !loadingSubgrupos
                        ) {
                            if (loadingSubgrupos)
                                CircularProgressIndicator(Modifier.size(18.dp), color = InvBlue, strokeWidth = 2.dp)
                            else
                                Icon(Icons.Default.Search, null,
                                    tint = if (grupoSelecionado != null) InvBlue else Color(0xFFCCCCCC))
                        }
                    }

                    // ── Marca ──────────────────────────────────────────────────
                    InvSearchField(
                        label   = "Marca",
                        value   = marcaNome,
                        onChange = { marcaNome = it; marcaSelecionada = null },
                        onSearch = { buscarMarcas(marcaNome) },
                        loading = loadingMarcas,
                        placeholder = "Digite o nome da marca..."
                    )
                }
            }

            // ── Produtos do inventário selecionado ─────────────────────────────
            if (loadingItens) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = InvBlue)
                            Spacer(Modifier.height(8.dp))
                            Text("Carregando itens do inventário...",
                                style = TextStyle(fontSize = 13.sp, color = InvSlate))
                        }
                    }
                }
            } else if (produtosInventario.isNotEmpty()) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Produtos do Inventário (${produtosInventario.size} itens)",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = InvDark))
                        Text("Código: ${codigoSelecionado?.idInventario}",
                            style = TextStyle(fontSize = 12.sp, color = InvSlate))
                        Spacer(Modifier.height(8.dp))
                        // Cabeçalho
                        Row(Modifier.fillMaxWidth().background(InvNavy).padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text("ID",   style = TextStyle(fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold), modifier = Modifier.weight(0.2f))
                            Text("Nome", style = TextStyle(fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold), modifier = Modifier.weight(0.6f))
                            Text("Qtd.", style = TextStyle(fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold), modifier = Modifier.weight(0.2f), textAlign = TextAlign.Center)
                        }
                        produtosInventario.take(10).forEachIndexed { i, p ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(if (i % 2 == 0) InvEven else Color.White)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${p.idProduto}", style = TextStyle(fontSize = 12.sp, color = InvDark), modifier = Modifier.weight(0.2f))
                                Text(p.descricao.ifEmpty { "Sem descrição" }, style = TextStyle(fontSize = 12.sp, color = InvDark), modifier = Modifier.weight(0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(if (p.qtdContada == "Não Contado") "Pendente" else p.qtdContada,
                                    style = TextStyle(fontSize = 11.sp, color = if (p.qtdContada == "Não Contado") InvAmber else InvGreen, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(0.2f), textAlign = TextAlign.Center)
                            }
                        }
                        if (produtosInventario.size > 10) {
                            Box(Modifier.fillMaxWidth().padding(8.dp)) {
                                Text("... e mais ${produtosInventario.size - 10} produtos",
                                    style = TextStyle(fontSize = 12.sp, color = InvSlate, fontWeight = FontWeight.SemiBold),
                                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }

            // ── Resultados da busca ────────────────────────────────────────────
            if (resultadosBusca.isNotEmpty()) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Resultados (${resultadosBusca.size})",
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = InvDark))
                            TextButton(onClick = { resultadosBusca = emptyList(); selecionados = emptySet(); paginaAtual = 1 }) {
                                Text("Limpar lista", style = TextStyle(fontSize = 12.sp, color = InvRed))
                            }
                        }
                        Spacer(Modifier.height(4.dp))

                        // Cabeçalho tabela
                        Row(Modifier.fillMaxWidth().background(InvNavy).padding(horizontal = 6.dp, vertical = 6.dp)) {
                            Spacer(Modifier.width(28.dp))
                            Text("ID",     style = TextStyle(fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold), modifier = Modifier.weight(0.18f))
                            Text("Nome",   style = TextStyle(fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold), modifier = Modifier.weight(0.38f))
                            Text("Custo",  style = TextStyle(fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold), modifier = Modifier.weight(0.22f), textAlign = TextAlign.End)
                            Text("Estq.",  style = TextStyle(fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold), modifier = Modifier.weight(0.14f), textAlign = TextAlign.End)
                            Spacer(Modifier.width(24.dp))
                        }

                        // Linhas
                        itensAtual.forEachIndexed { i, p ->
                            val isSel = p.idProduto in selecionados
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(if (isSel) Color(0xFFEFF6FF) else if (i % 2 == 0) InvEven else Color.White)
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Checkbox
                                Box(
                                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                                        .background(if (isSel) InvBlue else Color.White)
                                        .clickable {
                                            selecionados = if (isSel) selecionados - p.idProduto else selecionados + p.idProduto
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!isSel) Card(Modifier.fillMaxSize(), shape = RoundedCornerShape(4.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFD1D5DB))) {}
                                    if (isSel) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("${p.idProduto}", style = TextStyle(fontSize = 11.sp, color = InvDark), modifier = Modifier.weight(0.18f), maxLines = 1)
                                Text(p.descricao.ifEmpty { "Sem nome" }, style = TextStyle(fontSize = 11.sp, color = InvDark), modifier = Modifier.weight(0.38f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(if (p.custoCompra > 0) "R$%.2f".format(p.custoCompra) else "N/A",
                                    style = TextStyle(fontSize = 10.sp, color = InvSlate), modifier = Modifier.weight(0.22f), textAlign = TextAlign.End, maxLines = 1)
                                Text("${p.estoque.toInt()} ${p.abreviatura}",
                                    style = TextStyle(fontSize = 10.sp, color = InvSlate), modifier = Modifier.weight(0.14f), textAlign = TextAlign.End, maxLines = 1)
                                // Remover
                                IconButton(
                                    onClick = { resultadosBusca = resultadosBusca.filter { it.idProduto != p.idProduto }; selecionados = selecionados - p.idProduto },
                                    modifier = Modifier.size(24.dp)
                                ) { Icon(Icons.Default.Delete, null, tint = InvRed, modifier = Modifier.size(16.dp)) }
                            }
                            if (i < itensAtual.lastIndex) HorizontalDivider(color = InvLight)
                        }

                        // Paginação
                        if (totalPaginas > 1) {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { if (paginaAtual > 1) paginaAtual-- }, enabled = paginaAtual > 1) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = if (paginaAtual > 1) InvBlue else Color(0xFFCCCCCC))
                                }
                                Text("$paginaAtual de $totalPaginas", style = TextStyle(fontSize = 13.sp, color = InvDark, fontWeight = FontWeight.Bold))
                                IconButton(onClick = { if (paginaAtual < totalPaginas) paginaAtual++ }, enabled = paginaAtual < totalPaginas) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = if (paginaAtual < totalPaginas) InvBlue else Color(0xFFCCCCCC))
                                }
                            }
                        }
                    }
                }
            }

        } // fim Column scrollável

        // ── Botão fixo na base ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(InvLight)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Button(
                onClick  = { if (produtosInventario.isNotEmpty()) {
                val c = codigoSelecionado ?: return@Button
                navController.navigate(
                    Screen.Counting.createRoute(c.idEmpresa, c.idFilial, c.idInventario)
                )
            } else salvar() },
                enabled  = !loadingSalvar,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = InvNavy)
            ) {
                if (loadingSalvar) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Salvando...", style = TextStyle(fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold))
                } else {
                    val icon = if (produtosInventario.isNotEmpty()) Icons.AutoMirrored.Filled.List else Icons.Default.CheckCircle
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (produtosInventario.isNotEmpty()) "Contar Itens" else "Salvar Inventário",
                        style = TextStyle(fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold))
                }
            }
        }

        } // fim Box principal

        // ═══════════════ MODAIS ═══════════════════════════════════════════════

        // ── Modal Almoxarifado ───��─────────────────────────────────────────────
        if (showAlmoxModal) {
            InvSelectModal(
                title      = "Selecionar Almoxarifado",
                filtro     = filtroAlmox,
                onFiltro   = { filtroAlmox = it },
                placeholder = "Buscar por ID ou nome...",
                onDismiss  = { showAlmoxModal = false; filtroAlmox = "" }
            ) {
                almoxarifados.filter {
                    filtroAlmox.isBlank() ||
                    it.descricao.contains(filtroAlmox, ignoreCase = true) ||
                    it.id.toString().contains(filtroAlmox)
                }.forEach { a ->
                    InvModalItem(
                        title    = a.descricao.ifEmpty { "Almoxarifado sem nome" },
                        subtitle = "ID: ${a.id}",
                        onClick  = { almoxarifadoId = a.id; almoxarifadoDesc = a.descricao; showAlmoxModal = false; filtroAlmox = "" }
                    )
                }
            }
        }

        // ── Modal Códigos ──────────────────────────────────────────────────────
        if (showCodigoModal) {
            InvSelectModal(
                title       = "Selecionar Código",
                filtro      = filtroCodigo,
                onFiltro    = { filtroCodigo = it },
                placeholder = "Buscar código...",
                onDismiss   = { showCodigoModal = false; filtroCodigo = "" }
            ) {
                if (loadingCodigos) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = InvBlue)
                            Spacer(Modifier.height(8.dp))
                            Text("Carregando códigos...", style = TextStyle(fontSize = 13.sp, color = InvSlate))
                        }
                    }
                } else {
                    val filtrados = codigosSalvos.filter {
                        filtroCodigo.isBlank() || it.idInventario.toString().contains(filtroCodigo)
                    }
                    if (filtrados.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                            Text("Nenhum código de inventário encontrado",
                                style = TextStyle(fontSize = 14.sp, color = InvSlate), textAlign = TextAlign.Center)
                        }
                    } else filtrados.forEach { c ->
                        InvModalItem(
                            title    = "Código ${c.idInventario}",
                            subtitle = "Empresa: ${c.idEmpresa} • Filial: ${c.idFilial}",
                            onClick  = { selecionarCodigo(c); filtroCodigo = "" }
                        )
                    }
                }
            }
        }

        // ── Modal Produtos ─────────────────────────────────────────────────────
        if (showProdutoModal) {
            InvSelectModal(
                title       = "Selecionar Produto",
                filtro      = filtroProduto,
                onFiltro    = { filtroProduto = it },
                placeholder = "Buscar por ID ou nome...",
                onDismiss   = { showProdutoModal = false; filtroProduto = "" }
            ) {
                produtosPesquisa.filter {
                    filtroProduto.isBlank() ||
                    it.descricao.contains(filtroProduto, ignoreCase = true) ||
                    it.idProduto.toString().contains(filtroProduto)
                }.forEach { p ->
                    InvModalItem(
                        title    = p.descricao.ifEmpty { "Produto sem nome" },
                        subtitle = "ID: ${p.idProduto} • Estoque: ${p.estoque.toInt()} ${p.abreviatura}" +
                                if (p.custoCompra > 0) " • Custo: R$%.2f".format(p.custoCompra) else "",
                        onClick  = { produtoSelecionado = p; produtoNome = p.descricao; showProdutoModal = false; filtroProduto = "" }
                    )
                }
            }
        }

        // ── Modal Grupos ───────────────────────────────────────────────────────
        if (showGrupoModal) {
            InvSelectModal(
                title       = "Selecionar Grupo",
                filtro      = filtroGrupo,
                onFiltro    = { filtroGrupo = it },
                placeholder = "Buscar por ID ou nome...",
                onDismiss   = { showGrupoModal = false; filtroGrupo = "" }
            ) {
                gruposPesquisa.filter {
                    filtroGrupo.isBlank() ||
                    it.descricao.contains(filtroGrupo, ignoreCase = true) ||
                    it.id.toString().contains(filtroGrupo)
                }.forEach { g ->
                    InvModalItem(
                        title    = g.descricao.ifEmpty { "Grupo sem nome" },
                        subtitle = "ID: ${g.id}",
                        onClick  = {
                            grupoSelecionado = g; grupoNome = g.descricao
                            subgrupoSelecionado = null; subgrupoNome = ""
                            showGrupoModal = false; filtroGrupo = ""
                        }
                    )
                }
            }
        }

        // ── Modal Subgrupos ────────────────────────────────────────────────────
        if (showSubgrupoModal) {
            InvSelectModal(
                title       = "Selecionar Subgrupo",
                filtro      = filtroSubgrupo,
                onFiltro    = { filtroSubgrupo = it },
                placeholder = "Buscar por ID ou nome...",
                onDismiss   = { showSubgrupoModal = false; filtroSubgrupo = "" }
            ) {
                subgruposPesquisa.filter {
                    filtroSubgrupo.isBlank() ||
                    it.descricao.contains(filtroSubgrupo, ignoreCase = true) ||
                    it.id.toString().contains(filtroSubgrupo)
                }.forEach { s ->
                    InvModalItem(
                        title    = s.descricao.ifEmpty { "Subgrupo sem nome" },
                        subtitle = "ID: ${s.id} • Grupo: ${s.idGrupo}",
                        onClick  = { subgrupoSelecionado = s; subgrupoNome = s.descricao; showSubgrupoModal = false; filtroSubgrupo = "" }
                    )
                }
            }
        }

        // ── Modal Marcas ───────────────────────────────────────────────────────
        if (showMarcaModal) {
            InvSelectModal(
                title       = "Selecionar Marca",
                filtro      = filtroMarca,
                onFiltro    = { filtroMarca = it },
                placeholder = "Buscar por ID ou nome...",
                onDismiss   = { showMarcaModal = false; filtroMarca = "" }
            ) {
                marcasPesquisa.filter {
                    filtroMarca.isBlank() ||
                    it.descricao.contains(filtroMarca, ignoreCase = true) ||
                    it.id.toString().contains(filtroMarca)
                }.forEach { m ->
                    InvModalItem(
                        title    = m.descricao.ifEmpty { "Marca sem nome" },
                        subtitle = "ID: ${m.id}",
                        onClick  = { marcaSelecionada = m; marcaNome = m.descricao; showMarcaModal = false; filtroMarca = "" }
                    )
                }
            }
        }
    }
}

// ── Componentes reutilizáveis ─────────────────────────────────────────────────

@Composable
private fun InvLabel(text: String) =
    Text(text, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InvDark))

@Composable
private fun InvPickerRow(text: String, isEmpty: Boolean, loading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(InvLight).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(text, style = TextStyle(fontSize = 13.sp, color = if (isEmpty) InvSlate else InvDark), modifier = Modifier.weight(1f))
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = InvBlue, strokeWidth = 2.dp)
        else Icon(Icons.Default.KeyboardArrowDown, null, tint = InvSlate, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvSearchField(
    label: String, value: String, onChange: (String) -> Unit,
    onSearch: () -> Unit, loading: Boolean, placeholder: String
) {
    InvLabel(label)
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(InvLight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value         = value,
            onValueChange = onChange,
            placeholder   = { Text(placeholder, style = TextStyle(fontSize = 13.sp, color = InvSlate)) },
            modifier      = Modifier.weight(1f),
            colors        = TextFieldDefaults.colors(
                focusedContainerColor   = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor   = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine      = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() })
        )
        IconButton(onClick = onSearch, enabled = !loading) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = InvBlue, strokeWidth = 2.dp)
            else Icon(Icons.Default.Search, null, tint = InvBlue)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvSelectModal(
    title: String, filtro: String, onFiltro: (String) -> Unit,
    placeholder: String, onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp),
            shape  = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InvDark))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = InvSlate) }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))
                // Busca
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(10.dp)).background(InvLight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = InvSlate, modifier = Modifier.padding(start = 10.dp))
                    TextField(
                        value         = filtro,
                        onValueChange = onFiltro,
                        placeholder   = { Text(placeholder, style = TextStyle(fontSize = 12.sp, color = InvSlate)) },
                        modifier      = Modifier.weight(1f),
                        colors        = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                    if (filtro.isNotEmpty()) {
                        IconButton(onClick = { onFiltro("") }) {
                            Icon(Icons.Default.Close, null, tint = InvSlate, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                // Conteúdo scrollável
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun InvModalItem(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title,    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = InvDark))
            Text(subtitle, style = TextStyle(fontSize = 12.sp, color = InvSlate))
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = InvSlate)
    }
    HorizontalDivider(color = InvLight)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun InventoryScreenPreview() {
    InventoryScreen(rememberNavController())
}
