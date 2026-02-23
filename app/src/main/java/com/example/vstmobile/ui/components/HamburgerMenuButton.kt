package com.example.vstmobile.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.vstmobile.ui.theme.White

@Composable
fun HamburgerMenuButton(
    onClick: () -> Unit,
    tint: Color = White
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.Menu,
            contentDescription = "Abrir Menu",
            tint = tint
        )
    }
}

