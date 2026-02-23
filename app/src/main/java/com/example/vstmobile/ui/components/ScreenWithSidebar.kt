package com.example.vstmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import com.example.vstmobile.navigation.Screen
import com.example.vstmobile.ui.theme.LightBlue

/**
 * Wrapper component that provides Sidebar functionality to any screen.
 * Use this to wrap your screen content to enable sidebar navigation.
 */
@Composable
fun ScreenWithSidebar(
    navController: NavHostController,
    title: String,
    currentRoute: String,
    backgroundColor: Color = LightBlue,
    content: @Composable (onMenuClick: () -> Unit) -> Unit
) {
    val isSidebarOpen = remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            TopBar(
                title = title,
                onMenuClick = { isSidebarOpen.value = !isSidebarOpen.value }
            )

            // Screen content
            content { isSidebarOpen.value = !isSidebarOpen.value }
        }

        // Sidebar overlay (por cima de tudo)
        Sidebar(
            isOpen = isSidebarOpen.value,
            onClose = { isSidebarOpen.value = false },
            onItemClick = { route ->
                isSidebarOpen.value = false
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
            currentRoute = currentRoute
        )
    }
}

