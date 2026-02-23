package com.example.vstmobile.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.vstmobile.ui.components.ScreenWithSidebar
import com.example.vstmobile.navigation.Screen

@Composable
fun DashboardScreen(navController: NavHostController) {
    ScreenWithSidebar(
        navController = navController,
        title = "Dashboard",
        currentRoute = Screen.Dashboard.route
    ) { onMenuClick ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Dashboard - Home",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

