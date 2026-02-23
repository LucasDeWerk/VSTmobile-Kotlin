package com.example.vstmobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Arquivo de índice para os componentes de navegação e layout
 *
 * COMPONENTES DISPONÍVEIS:
 *
 * 1. Sidebar
 *    - Componente de menu lateral deslizável
 *    - Propriedades:
 *      - isOpen: Boolean (controla se está aberto/fechado)
 *      - onClose: () -> Unit (callback quando fechar)
 *      - onItemClick: (String) -> Unit (callback quando clicar em item)
 *      - currentRoute: String (rota atual para highlight)
 *
 * 2. TopBar
 *    - Barra de navegação no topo
 *    - Propriedades:
 *      - title: String (título exibido)
 *      - onMenuClick: () -> Unit (callback do botão menu)
 *
 * 3. HamburgerMenuButton
 *    - Botão de menu hamburger
 *    - Propriedades:
 *      - onClick: () -> Unit (callback do clique)
 *      - tint: Color (cor do ícone)
 *
 * 4. SidebarLayout
 *    - Wrapper completo que integra Sidebar + TopBar
 *    - Útil para aplicar em múltiplas telas
 *    - Propriedades:
 *      - navController: NavHostController
 *      - currentRoute: String
 *      - topBarTitle: String
 *      - content: @Composable () -> Unit (conteúdo da tela)
 *
 * EXEMPLO DE USO EM UMA NOVA TELA:
 *
 * @Composable
 * fun MinhaTelaScreen(navController: NavHostController) {
 *     val isSidebarOpen = remember { mutableStateOf(false) }
 *
 *     Row(modifier = Modifier.fillMaxSize()) {
 *         // Sidebar
 *         Sidebar(
 *             isOpen = isSidebarOpen.value,
 *             onClose = { isSidebarOpen.value = false },
 *             onItemClick = { route ->
 *                 when (route) {
 *                     "logout" -> navController.navigate("login") { popUpTo(0) }
 *                     "dashboard" -> navController.navigate("home")
 *                     // ... outras rotas
 *                 }
 *             },
 *             currentRoute = "minha_rota"
 *         )
 *
 *         // Main Content
 *         Column(modifier = Modifier.fillMaxSize()) {
 *             TopBar(
 *                 title = "Minha Tela",
 *                 onMenuClick = { isSidebarOpen.value = !isSidebarOpen.value }
 *             )
 *
 *             // Seu conteúdo aqui
 *         }
 *     }
 * }
 *
 * OU USANDO SIDEBARLEAYOUT:
 *
 * @Composable
 * fun MinhaTelaScreen(navController: NavHostController) {
 *     SidebarLayout(
 *         navController = navController,
 *         currentRoute = "minha_rota",
 *         topBarTitle = "Minha Tela",
 *         content = {
 *             // Seu conteúdo aqui
 *         }
 *     )
 * }
 */

