package com.example.vstmobile.ui.screens.production

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.vstmobile.navigation.Screen
import com.example.vstmobile.services.*
import com.example.vstmobile.ui.components.ScreenWithSidebar
import com.example.vstmobile.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val PNavy  = Color(0xFF1E3A8A)
private val PBlue  = Color(0xFF3B82F6)
private val PSlate = Color(0xFF64748B)
private val PLight = Color(0xFFF1F5F9)
private val PDark  = Color(0xFF1E293B)

private fun prodFmt(v: Double): String = try {
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(v)
} catch (_: Exception) { "R$ 0,00" }

@Composable
fun ProductionScreen(navController: NavHostController) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val scope   = rememberCoroutineScope()

    var rankings    by remember { mutableStateOf<List<ProdRankingDash>>(emptyList()) }
    var isLoading   by remember { mutableStateOf(false) }
    var expandedIdx by remember { mutableStateOf<Int?>(null) }

    val filial = FilialState.selectedFilial

    LaunchedEffect(filial?.idFilial) {
        if (filial == null || session.userToken.isEmpty()) return@LaunchedEffect
        val svc = ProductionService(session.userToken)
        isLoading = true
        scope.launch {
            svc.fetchRankingEstoque(
                filial.idEmpresa.toString(),
                filial.idFilial.toString()
            ).data?.let { rankings = it }
            isLoading = false
        }
    }

    ScreenWithSidebar(
        navController = navController,
        title = "Estoque",
        currentRoute = Screen.Production.route
    ) { _ ->
        if (filial == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("📦", fontSize = 48.sp)
                    Text("Selecione uma filial",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PDark))
                    Text("Abra o menu lateral e selecione\numa filial para ver os dados de estoque.",
                        style = TextStyle(fontSize = 14.sp, color = PSlate),
                        textAlign = TextAlign.Center)
                }
            }
            return@ScreenWithSidebar
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PLight)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // Loading
            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PrimaryBlue, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Carregando dados...", style = TextStyle(fontSize = 13.sp, color = PSlate))
                }
            }

            // ── Card da filial ────────────────────────────────────────────────
            ProdCard(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏢", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(filial.nomeFilial,
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PDark),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Código: ${filial.idFilial}",
                            style = TextStyle(fontSize = 12.sp, color = PSlate))
                    }
                }
            }

            // ── Sem dados ─────────────────────────────────────────────────────
            if (!isLoading && rankings.isEmpty()) {
                ProdCard(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📦", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Nenhum dado de estoque disponível.",
                            style = TextStyle(fontSize = 13.sp, color = PSlate),
                            textAlign = TextAlign.Center)
                    }
                }
            }

            // ── Rankings ──────────────────────────────────────────────────────
            rankings.forEachIndexed { idx, dash ->
                val isExpanded = expandedIdx == idx
                val max = dash.registros.maxOfOrNull { it.totalGrupo }?.coerceAtLeast(1.0) ?: 1.0
                val exibidos = if (isExpanded) dash.registros else dash.registros.take(5)

                ProdCard(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    // Título
                    Text(dash.descDashboard,
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PDark))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Linhas de barra
                    exibidos.forEach { item ->
                        val pct = (item.totalGrupo / max).toFloat().coerceIn(0f, 1f)
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            Text(item.descProduto,
                                style = TextStyle(fontSize = 12.sp, color = PSlate),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(5.dp))
                            // Barra
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(15.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PLight)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(pct)
                                        .height(15.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(PBlue)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(prodFmt(item.totalGrupo),
                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PDark))
                        }
                    }

                    // Botão expandir/ocultar
                    if (dash.registros.size > 5) {
                        TextButton(onClick = { expandedIdx = if (isExpanded) null else idx }) {
                            Text(
                                if (isExpanded) "Ocultar" else "Exibir todos",
                                style = TextStyle(color = PBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProdCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ProductionScreenPreview() {
    ProductionScreen(rememberNavController())
}
