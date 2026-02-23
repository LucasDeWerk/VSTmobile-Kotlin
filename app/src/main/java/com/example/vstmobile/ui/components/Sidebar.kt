package com.example.vstmobile.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import com.example.vstmobile.R
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.example.vstmobile.services.FilialService
import com.example.vstmobile.services.FilialState
import com.example.vstmobile.services.SessionManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.vstmobile.ui.theme.PrimaryBlue
import com.example.vstmobile.ui.theme.White
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class SidebarItem(
    val title: String,
    val iconRes: Int? = null,
    val icon: ImageVector? = null,
    val color: Color,
    val route: String,
    val isClickable: Boolean = true,
    val hasSubItems: Boolean = false,
    val subItems: List<SidebarSubItem> = emptyList()
)

data class SidebarSubItem(
    val title: String,
    val route: String,
    val description: String = ""
)

private val SIDEBAR_WIDTH: Dp = 280.dp

@Composable
fun Sidebar(
    modifier: Modifier = Modifier,
    isOpen: Boolean = true,
    onClose: () -> Unit = {},
    onItemClick: (String) -> Unit = {},
    currentRoute: String = ""
) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val fullName = session.userName.ifEmpty { "Usuário" }
    val companyCode = session.companyCode
    val coroutineScope = rememberCoroutineScope()

    // Estado local do seletor de filial
    var showFilialDialog by remember { mutableStateOf(false) }
    var isLoadingFiliais by remember { mutableStateOf(false) }

    // Carregar filiais ao abrir a sidebar pela primeira vez
    LaunchedEffect(Unit) {
        if (!FilialState.isLoaded && session.userToken.isNotEmpty()) {
            isLoadingFiliais = true
            coroutineScope.launch {
                try {
                    val result = FilialService().fetchUserFiliais(
                        userId = session.userId,
                        userToken = session.userToken
                    )
                    if (result.success) {
                        FilialState.loadFiliais(result.filiais)
                    }
                } catch (_: Exception) {
                } finally {
                    isLoadingFiliais = false
                }
            }
        }
    }

    val density = LocalDensity.current
    val sidebarWidthPx = with(density) { SIDEBAR_WIDTH.toPx() }

    // Animação com spring — física natural, ultra lisa
    val offsetX by animateFloatAsState(
        targetValue = if (isOpen) 0f else -sidebarWidthPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "SidebarOffsetX"
    )

    // Scrim (fundo escuro) animado
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isOpen) 0.4f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ScrimAlpha"
    )

    val sidebarItems = listOf(
        SidebarItem(
            title = "Home",
            iconRes = R.drawable.home_24px,
            color = White.copy(alpha = 0.7f),
            route = "home",
            hasSubItems = false
        ),
        SidebarItem(
            title = "Vendas",
            iconRes = R.drawable.shopping_cart_24px,
            color = White.copy(alpha = 0.7f),
            route = "Sales",
            hasSubItems = true,
            subItems = listOf(
                SidebarSubItem(
                    title = "Dashboard",
                    route = "Sales",
                    description = "Visão geral de vendas"
                ),
                SidebarSubItem(
                    title = "Análise por Produto",
                    route = "AnaliseVendaProduto",
                    description = "Análise detalhada por produto"
                ),
                SidebarSubItem(
                    title = "Limite de Crédito",
                    route = "LimiteCreditoCliente",
                    description = "Controle de limite dos clientes"
                ),
                SidebarSubItem(
                    title = "Aprovação Pedido/Orçamento",
                    route = "AprovacaoPedidoOrcamento",
                    description = "Aprovações pendentes"
                )
            )
        ),
        SidebarItem(
            title = "Financeiro",
            iconRes = R.drawable.attach_money_24px,
            color = White.copy(alpha = 0.7f),
            route = "Finance",
            hasSubItems = true,
            subItems = listOf(
                SidebarSubItem(
                    title = "Dashboard",
                    route = "Finance",
                    description = "Visão geral financeira"
                ),
                SidebarSubItem(
                    title = "Títulos a Pagar - Conferência",
                    route = "TitulosPagarConferencia",
                    description = "Conferência de títulos a pagar"
                ),
                SidebarSubItem(
                    title = "Títulos a Pagar - Local Cobrança",
                    route = "TitulosPagarLocalCobranca",
                    description = "Títulos por local de cobrança"
                ),
                SidebarSubItem(
                    title = "Títulos a Receber - Conferência",
                    route = "TitulosReceberConferencia",
                    description = "Conferência de títulos a receber"
                ),
                SidebarSubItem(
                    title = "Títulos a Receber - Local Cobrança",
                    route = "TitulosReceberLocalCobranca",
                    description = "Títulos por local de cobrança"
                ),
                SidebarSubItem(
                    title = "D.R.E",
                    route = "DreScreen",
                    description = "Demonstrativo de resultado"
                )
            )
        ),
        SidebarItem(
            title = "Estoque",
            iconRes = R.drawable.box_24px,
            color = White.copy(alpha = 0.7f),
            route = "Production",
            hasSubItems = false
        ),
        SidebarItem(
            title = "Inventário",
            iconRes = R.drawable.border_all_24px,
            color = White.copy(alpha = 0.7f),
            route = "Inventory",
            hasSubItems = false
        )
    )

    // Só renderiza quando não está completamente escondido
    if (offsetX > -sidebarWidthPx) {
        // Scrim — fundo escuro clicável para fechar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onClose() }
        )

        // Gradient elegante e sofisticado para o fundo
        val gradientBrush = Brush.linearGradient(
            colors = listOf(
                PrimaryBlue.copy(alpha = 0.99f),
                PrimaryBlue.copy(alpha = 0.96f),
                PrimaryBlue.copy(alpha = 0.98f)
            )
        )

        // Sidebar deslizando por cima com gradiente elegante
        Column(
            modifier = modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .width(SIDEBAR_WIDTH)
                .fillMaxHeight()
                .background(brush = gradientBrush),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Spacer para cobrir a status bar
            Column(
                modifier = Modifier
                    .width(SIDEBAR_WIDTH)
                    .height(24.dp)
                    .background(brush = gradientBrush)
            ) {}

            // Header com Avatar e botão de fechar
            Row(
                modifier = Modifier
                    .width(SIDEBAR_WIDTH)
                    .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar do usuário simples e elegante
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VT",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = White,
                            letterSpacing = 1.5.sp
                        )
                    )
                }

                // Espaçador
                Box(modifier = Modifier.weight(1f))

                // Botão fechar com efeito glass morphism
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(White.copy(alpha = 0.12f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onClose
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Fechar Menu",
                        tint = White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Informações do usuário com tipografia premium
            Column(
                modifier = Modifier
                    .width(SIDEBAR_WIDTH)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = fullName,
                    style = TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = White,
                        letterSpacing = 0.5.sp
                    )
                )
                Text(
                    text = companyCode.ifEmpty { "VST Solution" },
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Light,
                        color = White.copy(alpha = 0.65f),
                        letterSpacing = 0.4.sp
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Seletor de Filial
            Box(
                modifier = Modifier
                    .width(SIDEBAR_WIDTH)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(White.copy(alpha = 0.12f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            if (FilialState.filiais.isNotEmpty()) {
                                showFilialDialog = true
                            }
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Filial",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = White.copy(alpha = 0.55f),
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            )
                        )
                        if (isLoadingFiliais) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = White.copy(alpha = 0.7f),
                                    strokeWidth = 1.5.dp
                                )
                                Text(
                                    text = "Carregando...",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = White.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        } else {
                            Text(
                                text = FilialState.displayLabel(),
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Selecionar filial",
                        tint = White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier
                    .width(SIDEBAR_WIDTH)
                    .padding(horizontal = 16.dp),
                color = White.copy(alpha = 0.12f),
                thickness = 1.dp
            )

            // Menu Items com espaçamento luxuoso
            Column(
                modifier = Modifier
                    .width(SIDEBAR_WIDTH)
                    .weight(1f)
                    .padding(vertical = 14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sidebarItems.forEach { item ->
                    SidebarMenuItemCard(
                        item = item,
                        isSelected = currentRoute == item.route,
                        onClick = { route ->
                            onItemClick(route)
                            onClose()
                        }
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier
                    .width(SIDEBAR_WIDTH)
                    .padding(horizontal = 16.dp),
                color = White.copy(alpha = 0.12f),
                thickness = 1.dp
            )

            // Footer with Settings and Logout com design premium
            Column(
                modifier = Modifier
                    .width(SIDEBAR_WIDTH)
                    .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SidebarMenuItemCard(
                    item = SidebarItem(
                        title = "Configurações",
                        icon = Icons.Filled.Settings,
                        color = White.copy(alpha = 0.7f),
                        route = "settings"
                    ),
                    isSelected = currentRoute == "settings",
                    onClick = { route ->
                        onItemClick(route)
                        onClose()
                    }
                )

                SidebarMenuItemCard(
                    item = SidebarItem(
                        title = "Sair",
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        color = White.copy(alpha = 0.7f),
                        route = "logout"
                    ),
                    isSelected = false,
                    onClick = { route ->
                        onItemClick(route)
                        onClose()
                    }
                )
            }
        }

        // Dialog de seleção de filial (múltipla seleção com checkboxes)
        if (showFilialDialog) {
            Dialog(onDismissRequest = { showFilialDialog = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                ) {
                    // Header do dialog
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PrimaryBlue)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Column {
                            Text(
                                text = "Selecionar Filial",
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = White
                                )
                            )
                            if (FilialState.selectedFiliais.size > 1) {
                                Text(
                                    text = "${FilialState.selectedFiliais.size} filiais selecionadas",
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        color = White.copy(alpha = 0.75f)
                                    )
                                )
                            }
                        }
                    }

                    // Lista de filiais com checkboxes
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    ) {
                        items(FilialState.filiais) { filial ->
                            val isSelected = FilialState.isSelected(filial)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { FilialState.toggleFilial(filial) }
                                    .background(
                                        if (isSelected) PrimaryBlue.copy(alpha = 0.08f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Checkbox
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (isSelected) PrimaryBlue else Color.Transparent
                                        )
                                        .then(
                                            if (!isSelected) Modifier.background(
                                                Color(0xFFE2E8F0),
                                                RoundedCornerShape(4.dp)
                                            ) else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${filial.idEmpresa} | ${filial.idFilial} | ${filial.identificacaoInterna}",
                                        style = TextStyle(
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) PrimaryBlue else Color(0xFF1E293B)
                                        )
                                    )
                                    if (filial.nomeFilial.isNotEmpty()) {
                                        Text(
                                            text = filial.nomeFilial,
                                            style = TextStyle(
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B)
                                            ),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)
                        }
                    }

                    // Botão confirmar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showFilialDialog = false }) {
                            Text(
                                text = "Cancelar",
                                style = TextStyle(color = Color(0xFF64748B), fontSize = 14.sp)
                            )
                        }
                        TextButton(onClick = { showFilialDialog = false }) {
                            Text(
                                text = "Confirmar",
                                style = TextStyle(
                                    color = PrimaryBlue,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SidebarMenuItemCard(
    item: SidebarItem,
    isSelected: Boolean = false,
    onClick: (route: String) -> Unit = {}
) {
    val isHovered = remember { mutableStateOf(false) }
    val isExpanded = remember { mutableStateOf(false) }

    // Animação de rotação do ícone de expansão
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded.value) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "IconRotation"
    )

    Column(
        modifier = Modifier.width(SIDEBAR_WIDTH - 24.dp)
    ) {
        // Main item com design minimalista
        Row(
            modifier = Modifier
                .width(SIDEBAR_WIDTH - 24.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    color = when {
                        isSelected -> White.copy(alpha = 0.2f)
                        isHovered.value -> White.copy(alpha = 0.1f)
                        else -> Color.Transparent
                    }
                )
                .clickable(
                    enabled = item.isClickable,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {
                        if (item.hasSubItems) {
                            isExpanded.value = !isExpanded.value
                        } else {
                            onClick(item.route)
                        }
                    }
                )
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container neutro e elegante
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.iconRes != null) {
                    Icon(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = item.title,
                        tint = item.color,
                        modifier = Modifier.size(20.dp)
                    )
                } else if (item.icon != null) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = item.color,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Title com tipografia premium
            Text(
                text = item.title,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) White else White.copy(alpha = 0.85f),
                    letterSpacing = 0.4.sp
                ),
                modifier = Modifier.weight(1f)
            )

            // Indicador de expansão/seleção com animação
            if (item.hasSubItems) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Expandir",
                    tint = White.copy(alpha = if (isSelected) 0.9f else 0.6f),
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(rotationAngle)
                )
            } else if (isSelected) {
                // Indicador simples de seleção
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(White.copy(alpha = 0.8f))
                )
            }
        }

        // Sub-items com animação de expansão
        if (item.hasSubItems && isExpanded.value) {
            Column(
                modifier = Modifier
                    .width(SIDEBAR_WIDTH - 24.dp)
                    .padding(top = 8.dp, start = 20.dp, end = 0.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                item.subItems.forEach { subItem ->
                    SidebarSubItemCard(
                        subItem = subItem,
                        onClick = {
                            // Navegar para a rota do sub-item
                            onClick(subItem.route)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SidebarSubItemCard(
    subItem: SidebarSubItem,
    onClick: (route: String) -> Unit = {}
) {
    val isHovered = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(SIDEBAR_WIDTH - 60.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                color = if (isHovered.value) White.copy(alpha = 0.1f) else Color.Transparent
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onClick(subItem.route) }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = subItem.title,
            style = TextStyle(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = White.copy(alpha = 0.8f),
                letterSpacing = 0.3.sp
            )
        )
        if (subItem.description.isNotEmpty()) {
            Text(
                text = subItem.description,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Light,
                    color = White.copy(alpha = 0.5f),
                    letterSpacing = 0.2.sp
                ),
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 800)
@Composable
fun SidebarPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Sidebar(
            isOpen = true,
            onClose = {},
            onItemClick = {},
            currentRoute = "Dashboard"
        )
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 800)
@Composable
fun SidebarClosedPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Sidebar(
            isOpen = false,
            onClose = {},
            onItemClick = {},
            currentRoute = "Dashboard"
        )
    }
}

