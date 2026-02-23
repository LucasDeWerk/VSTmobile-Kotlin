package com.example.vstmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vstmobile.ui.theme.PrimaryBlue
import com.example.vstmobile.ui.theme.White

@Composable
fun TopBar(
    title: String,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column {
        // Spacer para cobrir a status bar (aproximadamente 24dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(PrimaryBlue)
        ) {
            // Espaço para status bar
        }

        // TopBar principal
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(PrimaryBlue)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HamburgerMenuButton(onClick = onMenuClick)

            Text(
                text = title,
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

