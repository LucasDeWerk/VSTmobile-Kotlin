package com.example.vstmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.example.vstmobile.navigation.NavGraph
import com.example.vstmobile.ui.theme.VSTmobileTheme
import com.example.vstmobile.ui.theme.PrimaryBlue
import android.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar para fullscreen verdadeiro
        enableEdgeToEdge()

        // Configurar cores da barra de status e navegação
        window.statusBarColor = Color.parseColor("#1E40AF") // PrimaryBlue
        window.navigationBarColor = Color.WHITE

        setContent {
            VSTmobileTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainActivityPreview() {
    VSTmobileTheme {
        val navController = rememberNavController()
        NavGraph(navController = navController)
    }
}