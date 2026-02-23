package com.example.vstmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.vstmobile.services.SessionManager
import com.example.vstmobile.R
import com.example.vstmobile.navigation.Screen
import com.example.vstmobile.ui.components.Sidebar
import com.example.vstmobile.ui.components.TopBar
import com.example.vstmobile.ui.theme.LightBlue
import com.example.vstmobile.ui.theme.White
import com.example.vstmobile.ui.theme.DarkGray

data class DashboardItem(
    val title: String,
    val iconRes: Int,
    val color: Color,
    val screen: String,
    val description: String
)

@Composable
fun HomeScreen(navController: NavHostController) {
    val context = LocalContext.current
    val session = remember { SessionManager(context).getSession() }
    val firstName = session.userName.split(" ").firstOrNull()?.ifEmpty { "Usuário" } ?: "Usuário"

    val isSidebarOpen = remember { mutableStateOf(false) }

    val dashboardItems = listOf(
        DashboardItem(
            title = "Vendas",
            iconRes = R.drawable.shopping_cart_24px,
            color = Color(0xFF94A3B8),
            screen = Screen.Sales.route,
            description = "Dashboard de vendas"
        ),
        DashboardItem(
            title = "Financeiro",
            iconRes = R.drawable.attach_money_24px,
            color = Color(0xFF94A3B8),
            screen = Screen.Finance.route,
            description = "Dashboard financeiro"
        ),
        DashboardItem(
            title = "Estoque",
            iconRes = R.drawable.box_24px,
            color = Color(0xFF94A3B8),
            screen = Screen.Production.route,
            description = "Monitoramento de estoque"
        ),
        DashboardItem(
            title = "Inventário",
            iconRes = R.drawable.border_all_24px,
            color = Color(0xFF94A3B8),
            screen = Screen.Inventory.route,
            description = "Controle de inventário"
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBlue)
    ) {
        // Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightBlue)
        ) {
            TopBar(
                title = "Dashboard",
                onMenuClick = { isSidebarOpen.value = !isSidebarOpen.value }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightBlue)
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Welcome Header - Mais refinado
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Bem-vindo, $firstName!",
                        style = TextStyle(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DarkGray,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Acesse os dashboards do seu negócio",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.3.sp
                        )
                    )
                }

                // Espaçamento dinâmico
                Spacer(modifier = Modifier.weight(0.04f))

                // Dashboard Cards Grid - CENTRALIZADO
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .weight(0.75f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Módulos Principais",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569),
                            letterSpacing = 0.3.sp
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                    )

                    // Grid 2x2 de Cards
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        items(dashboardItems) { item ->
                            DashboardCard(
                                item = item,
                                onClick = { navController.navigate(item.screen) }
                            )
                        }
                    }
                }

                // Espaçamento dinâmico
                Spacer(modifier = Modifier.weight(0.21f))

                // Footer Info - Subtil
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(
                            color = White.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💡 Clique em qualquer módulo para explorar",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Sidebar overlay
        Sidebar(
            isOpen = isSidebarOpen.value,
            onClose = { isSidebarOpen.value = false },
            onItemClick = { route ->
                when (route) {
                    "logout" -> {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0)
                        }
                    }
                    else -> {
                        navController.navigate(route) {
                            launchSingleTop = true
                        }
                    }
                }
            },
            currentRoute = "home"
        )
    }
}

@Composable
fun DashboardCard(
    item: DashboardItem,
    onClick: () -> Unit
) {
    val isHovered = remember { mutableStateOf(false) }

    // Cores dinâmicas baseadas no módulo
    val cardColors = when (item.title) {
        "Vendas" -> mapOf(
            "bg" to Color(0xFF10B981),
            "light" to Color(0xFFA7F3D0)
        )
        "Financeiro" -> mapOf(
            "bg" to Color(0xFF3B82F6),
            "light" to Color(0xFFBFDBFE)
        )
        "Estoque" -> mapOf(
            "bg" to Color(0xFF8B5CF6),
            "light" to Color(0xFFE9D5FF)
        )
        "Inventário" -> mapOf(
            "bg" to Color(0xFFF59E0B),
            "light" to Color(0xFFFEF3C7)
        )
        else -> mapOf(
            "bg" to Color(0xFF64748B),
            "light" to Color(0xFFE2E8F0)
        )
    }

    val animatedBgColor = animateColorAsState(
        targetValue = if (isHovered.value) cardColors["light"]!! else White,
        animationSpec = spring(),
        label = "CardBgColor"
    )

    val animatedIconBgColor = animateColorAsState(
        targetValue = if (isHovered.value) cardColors["bg"]!! else cardColors["bg"]!!.copy(alpha = 0.1f),
        animationSpec = spring(),
        label = "IconBgColor"
    )

    val animatedIconTint = animateColorAsState(
        targetValue = if (isHovered.value) White else cardColors["bg"]!!,
        animationSpec = spring(),
        label = "IconTint"
    )

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onClick()
            }
            .onHover { isHovered.value = it },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = animatedBgColor.value),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isHovered.value) 8.dp else 3.dp,
            pressedElevation = 12.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon Container - Com animação de cor
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = animatedIconBgColor.value,
                        shape = RoundedCornerShape(13.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = item.iconRes),
                    contentDescription = item.title,
                    tint = animatedIconTint.value,
                    modifier = Modifier.size(30.dp)
                )
            }

            // Title - Com destaque melhorado
            Text(
                text = item.title,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHovered.value) cardColors["bg"]!! else DarkGray,
                    letterSpacing = 0.3.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            // Description - Mais suave
            Text(
                text = item.description,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = if (isHovered.value) cardColors["bg"]!!.copy(alpha = 0.7f) else Color(0xFF94A3B8),
                    fontWeight = FontWeight.Normal
                ),
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

// Extensão para detectar hover
@Suppress("UNUSED_PARAMETER")
private fun Modifier.onHover(onHoverChanged: (Boolean) -> Unit): Modifier = this

@Preview
@Composable
fun HomeScreenPreview() {
    HomeScreen(rememberNavController())
}

