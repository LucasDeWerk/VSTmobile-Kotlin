package com.example.vstmobile.ui.utils

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Utilitário para aplicar padding de system bars (status bar e navigation bar)
 *
 * Este arquivo fornece helpers para garantir que o conteúdo não seja sobreposto
 * pela barra de status do Android enquanto mantém o app em fullscreen (edge-to-edge).
 *
 * Uso:
 * - Aplique statusBarPadding() ao topo das suas telas
 * - Ou use navigationBarPadding() na parte inferior
 * - Use ambos quando necessário cobrir toda a tela
 */

/**
 * Padding para a status bar (barra de status do Android)
 * Aplique isso no topo da sua tela quando usar edge-to-edge
 */
@Suppress("unused")
fun Modifier.statusBarPadding(paddingPx: Int = 24): Modifier {
    return this.padding(top = paddingPx.dp)
}

/**
 * Padding para a navigation bar (barra de navegação do Android)
 * Aplique isso na parte inferior da sua tela quando usar edge-to-edge
 */
@Suppress("unused")
fun Modifier.navigationBarPadding(paddingPx: Int = 48): Modifier {
    return this.padding(bottom = paddingPx.dp)
}

/**
 * Combina status bar e navigation bar padding
 */
@Suppress("unused")
fun Modifier.systemBarsPadding(statusBarPaddingPx: Int = 24, navBarPaddingPx: Int = 48): Modifier {
    return this.padding(top = statusBarPaddingPx.dp, bottom = navBarPaddingPx.dp)
}

