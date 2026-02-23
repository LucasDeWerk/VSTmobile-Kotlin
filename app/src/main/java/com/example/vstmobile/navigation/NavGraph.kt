package com.example.vstmobile.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.vstmobile.ui.screens.LoginScreen
import com.example.vstmobile.ui.screens.HomeScreen
import com.example.vstmobile.ui.screens.RegisterDeviceScreen
import com.example.vstmobile.ui.screens.dashboard.DashboardScreen
import com.example.vstmobile.ui.screens.sales.SalesScreen
import com.example.vstmobile.ui.screens.sales.AnaliseVendaProdutoScreen
import com.example.vstmobile.ui.screens.sales.LimiteCreditoClienteScreen
import com.example.vstmobile.ui.screens.sales.AprovacaoPedidoOrcamentoScreen
import com.example.vstmobile.ui.screens.finance.FinanceScreen
import com.example.vstmobile.ui.screens.finance.TitulosPagarConferenciaScreen
import com.example.vstmobile.ui.screens.finance.TitulosPagarLocalCobrancaScreen
import com.example.vstmobile.ui.screens.finance.TitulosReceberConferenciaScreen
import com.example.vstmobile.ui.screens.finance.TitulosReceberLocalCobrancaScreen
import com.example.vstmobile.ui.screens.finance.DreScreen
import com.example.vstmobile.ui.screens.production.ProductionScreen
import com.example.vstmobile.ui.screens.inventory.InventoryScreen
import com.example.vstmobile.ui.screens.inventory.CountingScreen
import com.example.vstmobile.ui.screens.inventory.AICountingScreen
import com.example.vstmobile.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object RegisterDevice : Screen("register_device")
    object Dashboard : Screen("Dashboard")
    object Sales : Screen("Sales")
    object AnaliseVendaProduto : Screen("AnaliseVendaProduto")
    object LimiteCreditoCliente : Screen("LimiteCreditoCliente")
    object AprovacaoPedidoOrcamento : Screen("AprovacaoPedidoOrcamento")
    object Finance : Screen("Finance")
    object TitulosPagarConferencia : Screen("TitulosPagarConferencia")
    object TitulosPagarLocalCobranca : Screen("TitulosPagarLocalCobranca")
    object TitulosReceberConferencia : Screen("TitulosReceberConferencia")
    object TitulosReceberLocalCobranca : Screen("TitulosReceberLocalCobranca")
    object Dre : Screen("DreScreen")
    object Production : Screen("Production")
    object Inventory : Screen("Inventory")
    object Counting : Screen("counting/{idEmpresa}/{idFilial}/{idInventario}") {
        fun createRoute(idEmpresa: Int, idFilial: Int, idInventario: Int) =
            "counting/$idEmpresa/$idFilial/$idInventario"
    }
    object AICounting : Screen("ai_counting/{idEmpresa}/{idFilial}/{idInventario}/{idProduto}/{idAlmox}/{qtdEstoque}/{format}") {
        fun createRoute(
            idEmpresa: Int, idFilial: Int, idInventario: Int,
            idProduto: Int, descProduto: String, idAlmox: Int,
            qtdEstoque: Double, format: String
        ) = "ai_counting/$idEmpresa/$idFilial/$idInventario/$idProduto/$idAlmox/$qtdEstoque/$format?desc=${java.net.URLEncoder.encode(descProduto, "UTF-8")}"
    }
    object Settings : Screen("settings")
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Login.route) {
        composable(route = Screen.Login.route) {
            LoginScreen(navController = navController)
        }
        composable(route = Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(route = Screen.RegisterDevice.route) {
            RegisterDeviceScreen(navController = navController)
        }

        // Sidebar Screens
        composable(route = Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }
        composable(route = Screen.Sales.route) {
            SalesScreen(navController = navController)
        }
        composable(route = Screen.AnaliseVendaProduto.route) {
            AnaliseVendaProdutoScreen(navController = navController)
        }
        composable(route = Screen.LimiteCreditoCliente.route) {
            LimiteCreditoClienteScreen(navController = navController)
        }
        composable(route = Screen.AprovacaoPedidoOrcamento.route) {
            AprovacaoPedidoOrcamentoScreen(navController = navController)
        }
        composable(route = Screen.Finance.route) {
            FinanceScreen(navController = navController)
        }
        composable(route = Screen.TitulosPagarConferencia.route) {
            TitulosPagarConferenciaScreen(navController = navController)
        }
        composable(route = Screen.TitulosPagarLocalCobranca.route) {
            TitulosPagarLocalCobrancaScreen(navController = navController)
        }
        composable(route = Screen.TitulosReceberConferencia.route) {
            TitulosReceberConferenciaScreen(navController = navController)
        }
        composable(route = Screen.TitulosReceberLocalCobranca.route) {
            TitulosReceberLocalCobrancaScreen(navController = navController)
        }
        composable(route = Screen.Dre.route) {
            DreScreen(navController = navController)
        }
        composable(route = Screen.Production.route) {
            ProductionScreen(navController = navController)
        }
        composable(route = Screen.Inventory.route) {
            InventoryScreen(navController = navController)
        }
        composable(
            route = Screen.Counting.route,
            arguments = listOf(
                androidx.navigation.navArgument("idEmpresa")    { type = androidx.navigation.NavType.IntType },
                androidx.navigation.navArgument("idFilial")     { type = androidx.navigation.NavType.IntType },
                androidx.navigation.navArgument("idInventario") { type = androidx.navigation.NavType.IntType }
            )
        ) { backStackEntry ->
            val idEmpresa    = backStackEntry.arguments?.getInt("idEmpresa")    ?: 0
            val idFilial     = backStackEntry.arguments?.getInt("idFilial")     ?: 0
            val idInventario = backStackEntry.arguments?.getInt("idInventario") ?: 0
            CountingScreen(
                navController  = navController,
                idEmpresa      = idEmpresa,
                idFilial       = idFilial,
                idInventario   = idInventario
            )
        }
        composable(route = Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(
            route = "ai_counting/{idEmpresa}/{idFilial}/{idInventario}/{idProduto}/{idAlmox}/{qtdEstoque}/{format}?desc={desc}",
            arguments = listOf(
                androidx.navigation.navArgument("idEmpresa")    { type = androidx.navigation.NavType.IntType },
                androidx.navigation.navArgument("idFilial")     { type = androidx.navigation.NavType.IntType },
                androidx.navigation.navArgument("idInventario") { type = androidx.navigation.NavType.IntType },
                androidx.navigation.navArgument("idProduto")    { type = androidx.navigation.NavType.IntType },
                androidx.navigation.navArgument("idAlmox")      { type = androidx.navigation.NavType.IntType },
                androidx.navigation.navArgument("qtdEstoque")   { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("format")       { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("desc")         { type = androidx.navigation.NavType.StringType; defaultValue = "" }
            )
        ) { back ->
            AICountingScreen(
                navController  = navController,
                idEmpresa      = back.arguments?.getInt("idEmpresa")    ?: 0,
                idFilial       = back.arguments?.getInt("idFilial")     ?: 0,
                idInventario   = back.arguments?.getInt("idInventario") ?: 0,
                idProduto      = back.arguments?.getInt("idProduto")    ?: 0,
                descProduto    = java.net.URLDecoder.decode(back.arguments?.getString("desc") ?: "", "UTF-8"),
                idAlmoxarifado = back.arguments?.getInt("idAlmox")      ?: 1,
                qtdEstoque     = back.arguments?.getString("qtdEstoque")?.toDoubleOrNull() ?: 0.0,
                format         = back.arguments?.getString("format")    ?: "redondo"
            )
        }
    }
}

