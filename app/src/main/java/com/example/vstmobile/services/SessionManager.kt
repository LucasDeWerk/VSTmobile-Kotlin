package com.example.vstmobile.services

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/**
 * Gerencia a sessão do usuário logado
 * Salva e recupera dados do usuário, tokens e empresa em SharedPreferences
 */
class SessionManager(private val context: Context) {

    companion object {
        private const val PREF_NAME = "vst_session"
        private const val TAG = "VST_SESSION"

        // Sessão
        private const val KEY_USER_TOKEN = "userToken"
        private const val KEY_COMPANY_TOKEN = "companyToken"
        private const val KEY_COMPANY_CODE = "companyCode"
        private const val KEY_USER_ID = "userId"
        private const val KEY_USER_NAME = "userName"
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"

        // Credenciais salvas (auto-login)
        private const val KEY_SAVED_COMPANY_CODE = "saved_companyCode"
        private const val KEY_SAVED_USER_ID = "saved_userId"
        private const val KEY_SAVED_PASSWORD = "saved_password"
        private const val KEY_SAVE_LOGIN = "saveLogin"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Deve ser chamado ao iniciar o app.
     * Se há sessão salva mas sem "Salvar Login" marcado, limpa a sessão.
     */
    fun init() {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val saveLoginEnabled = prefs.getBoolean(KEY_SAVE_LOGIN, false)

        if (isLoggedIn && !saveLoginEnabled) {
            // Sessão antiga sem saveLogin — limpar
            prefs.edit {
                remove(KEY_USER_TOKEN)
                remove(KEY_COMPANY_TOKEN)
                remove(KEY_COMPANY_CODE)
                remove(KEY_USER_ID)
                remove(KEY_USER_NAME)
                putBoolean(KEY_IS_LOGGED_IN, false)
            }
            Log.i(TAG, "⚠️ Sessão antiga sem 'Salvar Login' encontrada — limpando")
        }
    }

    /**
     * Salvar sessão completa após login bem-sucedido
     */
    fun saveSession(
        userToken: String,
        companyToken: String,
        companyCode: String,
        userId: String,
        userName: String
    ) {
        prefs.edit {
            putString(KEY_USER_TOKEN, userToken)
            putString(KEY_COMPANY_TOKEN, companyToken)
            putString(KEY_COMPANY_CODE, companyCode)
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_NAME, userName)
            putBoolean(KEY_IS_LOGGED_IN, true)
        }

        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "💾 SESSÃO SALVA")
        Log.i(TAG, "  ├─ Empresa: $companyCode")
        Log.i(TAG, "  ├─ Usuário: $userName (ID: $userId)")
        Log.i(TAG, "  ├─ userToken: ${userToken.take(20)}...")
        Log.i(TAG, "  └─ companyToken: ${companyToken.take(20)}...")
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    /**
     * Verificar se há credenciais salvas para auto-login.
     * NUNCA pula a autenticação — sempre refaz os 3 passos de API.
     */
    fun isLoggedIn(): Boolean {
        return false // Sempre exige autenticação — use getSavedCredentials() para auto-login
    }

    /**
     * Verificar se tem credenciais salvas (para decidir se faz auto-login)
     */
    fun hasSavedCredentials(): Boolean {
        return prefs.getBoolean(KEY_SAVE_LOGIN, false) &&
                prefs.getString(KEY_SAVED_COMPANY_CODE, "")?.isNotEmpty() == true &&
                prefs.getString(KEY_SAVED_USER_ID, "")?.isNotEmpty() == true &&
                prefs.getString(KEY_SAVED_PASSWORD, "")?.isNotEmpty() == true
    }

    /**
     * Recuperar token do usuário (para chamadas de API)
     */
    fun getUserToken(): String {
        return prefs.getString(KEY_USER_TOKEN, "") ?: ""
    }

    /**
     * Recuperar token da empresa
     */
    fun getCompanyToken(): String {
        return prefs.getString(KEY_COMPANY_TOKEN, "") ?: ""
    }

    /**
     * Recuperar código da empresa
     */
    fun getCompanyCode(): String {
        return prefs.getString(KEY_COMPANY_CODE, "") ?: ""
    }

    /**
     * Recuperar ID do usuário
     */
    fun getUserId(): String {
        return prefs.getString(KEY_USER_ID, "") ?: ""
    }

    /**
     * Recuperar nome do usuário
     */
    fun getUserName(): String {
        return prefs.getString(KEY_USER_NAME, "") ?: ""
    }

    /**
     * Recuperar todos os dados da sessão
     */
    fun getSession(): SessionData {
        return SessionData(
            userToken = getUserToken(),
            companyToken = getCompanyToken(),
            companyCode = getCompanyCode(),
            userId = getUserId(),
            userName = getUserName(),
            isLoggedIn = isLoggedIn()
        )
    }

    /**
     * Salvar credenciais para auto-login
     */
    fun saveCredentials(companyCode: String, userId: String, password: String) {
        prefs.edit {
            putString(KEY_SAVED_COMPANY_CODE, companyCode)
            putString(KEY_SAVED_USER_ID, userId)
            putString(KEY_SAVED_PASSWORD, password)
            putBoolean(KEY_SAVE_LOGIN, true)
        }
        Log.i(TAG, "🔑 Credenciais salvas para auto-login (empresa: $companyCode, usuário: $userId)")
    }

    /**
     * Verificar se o "Salvar Login" está ativado
     */
    fun isSaveLoginEnabled(): Boolean {
        return prefs.getBoolean(KEY_SAVE_LOGIN, false)
    }

    /**
     * Recuperar credenciais salvas
     */
    fun getSavedCredentials(): SavedCredentials? {
        if (!isSaveLoginEnabled()) return null
        val companyCode = prefs.getString(KEY_SAVED_COMPANY_CODE, "") ?: ""
        val userId = prefs.getString(KEY_SAVED_USER_ID, "") ?: ""
        val password = prefs.getString(KEY_SAVED_PASSWORD, "") ?: ""
        if (companyCode.isEmpty() || userId.isEmpty() || password.isEmpty()) return null
        return SavedCredentials(companyCode = companyCode, userId = userId, password = password)
    }

    /**
     * Limpar credenciais salvas (quando usuário desmarca "Salvar Login")
     */
    fun clearCredentials() {
        prefs.edit {
            remove(KEY_SAVED_COMPANY_CODE)
            remove(KEY_SAVED_USER_ID)
            remove(KEY_SAVED_PASSWORD)
            putBoolean(KEY_SAVE_LOGIN, false)
        }
        Log.i(TAG, "🗑️ Credenciais removidas")
    }

    /**
     * Limpar sessão (logout)
     * Se clearCredentials=true, também remove o salvar login
     */
    fun clearSession(clearCredentials: Boolean = false) {
        prefs.edit {
            remove(KEY_USER_TOKEN)
            remove(KEY_COMPANY_TOKEN)
            remove(KEY_COMPANY_CODE)
            remove(KEY_USER_ID)
            remove(KEY_USER_NAME)
            putBoolean(KEY_IS_LOGGED_IN, false)
            if (clearCredentials) {
                remove(KEY_SAVED_COMPANY_CODE)
                remove(KEY_SAVED_USER_ID)
                remove(KEY_SAVED_PASSWORD)
                putBoolean(KEY_SAVE_LOGIN, false)
            }
        }
        Log.i(TAG, "🔓 SESSÃO ENCERRADA (logout) — credenciais removidas: $clearCredentials")
    }
}

data class SessionData(
    val userToken: String,
    val companyToken: String,
    val companyCode: String,
    val userId: String,
    val userName: String,
    val isLoggedIn: Boolean
)

data class SavedCredentials(
    val companyCode: String,
    val userId: String,
    val password: String
)

