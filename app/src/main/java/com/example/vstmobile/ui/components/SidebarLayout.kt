package com.example.vstmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController

@Composable
fun SidebarLayout(
    navController: NavHostController,
    currentRoute: String,
    content: @Composable (onMenuClick: () -> Unit) -> Unit
) {
    val isSidebarOpen = remember { mutableStateOf(false) }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (isSidebarOpen.value) 0.4f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "ScrimAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Conteúdo principal — recebe onMenuClick para abrir sidebar ────
        content { isSidebarOpen.value = true }

        // ── Scrim escuro por cima do conteúdo ────────────────────────────
        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { isSidebarOpen.value = false }
            )
        }

        // ── Sidebar flutuando por cima de tudo ───────────────────────────
        Sidebar(
            isOpen = isSidebarOpen.value,
            onClose = { isSidebarOpen.value = false },
            onItemClick = { route ->
                isSidebarOpen.value = false
                when (route) {
                    "logout" -> navController.navigate("login") { popUpTo(0) }
                    "settings" -> { /* TODO */ }
                    else -> navController.navigate(route) { launchSingleTop = true }
                }
            },
            currentRoute = currentRoute
        )
    }
}
