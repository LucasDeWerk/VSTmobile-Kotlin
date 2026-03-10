package com.example.vstmobile.ui.screens.pcp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.vstmobile.navigation.Screen
import com.example.vstmobile.ui.components.ScreenWithSidebar

private val PcpNavy  = Color(0xFF1E3A8A)
private val PcpBlue  = Color(0xFF3B82F6)
private val PcpLight = Color(0xFFF1F5F9)
private val PcpDark  = Color(0xFF1E293B)
private val PcpGreen = Color(0xFF059669)
private val PcpOrange = Color(0xFFF59E0B)
private val PcpRed   = Color(0xFFEF4444)

@Composable
fun PcpDashboardScreen(navController: NavHostController) {
    ScreenWithSidebar(
        navController = navController,
        title = "PCP - Dashboard",
        currentRoute = Screen.PcpDashboard.route
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PcpLight)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "Visão Geral da Produção",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PcpDark
                )
            )

            // Linha de cards de resumo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PcpSummaryCard(
                    modifier = Modifier.weight(1f),
                    label = "Ordens Abertas",
                    value = "—",
                    color = PcpBlue
                )
                PcpSummaryCard(
                    modifier = Modifier.weight(1f),
                    label = "Em Produção",
                    value = "—",
                    color = PcpGreen
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PcpSummaryCard(
                    modifier = Modifier.weight(1f),
                    label = "Atrasadas",
                    value = "—",
                    color = PcpRed
                )
                PcpSummaryCard(
                    modifier = Modifier.weight(1f),
                    label = "Concluídas Hoje",
                    value = "—",
                    color = PcpOrange
                )
            }

            // Placeholder de gráfico/lista
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Gráfico de produção em breve",
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PcpSummaryCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = value,
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
        }
    }
}

