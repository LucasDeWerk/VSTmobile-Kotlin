package com.example.vstmobile.services

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Estado global de filial — persiste enquanto o app está aberto (em memória)
 * Suporta múltiplas filiais selecionadas
 */
object FilialState {
    var filiais: List<Filial> by mutableStateOf(emptyList())
    var selectedFiliais: List<Filial> by mutableStateOf(emptyList())
    var isLoaded: Boolean by mutableStateOf(false)

    // Compatibilidade — retorna a primeira selecionada
    val selectedFilial: Filial? get() = selectedFiliais.firstOrNull()

    fun isSelected(filial: Filial): Boolean {
        return selectedFiliais.any { it.idFilial == filial.idFilial }
    }

    fun toggleFilial(filial: Filial) {
        selectedFiliais = if (isSelected(filial)) {
            // Remove — mas garante que ao menos 1 fique selecionada
            val nova = selectedFiliais.filter { it.idFilial != filial.idFilial }
            if (nova.isEmpty()) selectedFiliais else nova
        } else {
            selectedFiliais + filial
        }
    }

    fun loadFiliais(lista: List<Filial>) {
        filiais = lista
        isLoaded = true
        // Seleciona a primeira automaticamente se não há nenhuma selecionada
        if (selectedFiliais.isEmpty() && lista.isNotEmpty()) {
            selectedFiliais = listOf(lista.first())
        }
    }

    fun reset() {
        filiais = emptyList()
        selectedFiliais = emptyList()
        isLoaded = false
    }

    fun displayLabel(): String {
        return when {
            selectedFiliais.isEmpty() -> "Selecionar filial"
            selectedFiliais.size == 1 -> {
                val f = selectedFiliais.first()
                "${f.idEmpresa} | ${f.idFilial} | ${f.identificacaoInterna}"
            }
            else -> "${selectedFiliais.size} filiais selecionadas"
        }
    }

    // IDs das filiais selecionadas para uso nas APIs
    fun selectedFilialIds(): List<Int> = selectedFiliais.map { it.idFilial }
}
