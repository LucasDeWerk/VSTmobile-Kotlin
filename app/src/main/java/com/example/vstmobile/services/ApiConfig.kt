package com.example.vstmobile.services

/**
 * Configuração centralizada das URLs base da API.
 * Altere aqui para mudar o ambiente (produção, homologação, etc.)
 * e todas as services serão atualizadas automaticamente.
 */
object ApiConfig {
    /** API principal (dados, autenticação, filiais, etc.) */
    const val BASE_API = "https://compras.vstsolution.com"

    /** Servidor de relatórios (PDF) */
    const val BASE_REPORT = "https://report.vstsolution.com"
}

