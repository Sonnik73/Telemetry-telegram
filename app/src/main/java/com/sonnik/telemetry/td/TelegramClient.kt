package com.sonnik.telemetry.td

import android.content.Context
import android.os.Build
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationState
import dev.g000sha256.tdl.dto.AuthorizationStateClosed
import dev.g000sha256.tdl.dto.AuthorizationStateLoggingOut
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the TDLib client instance and drives the authorization state machine.
 *
 * The api_id/api_hash pair is obtained by the user at https://my.telegram.org
 * and persisted locally; TDLib parameters are sent as soon as both the
 * WaitTdlibParameters state arrives and credentials are available.
 */
class TelegramClient(context: Context) {

    sealed interface AuthUiState {
        data object Initializing : AuthUiState
        data object NeedApiCredentials : AuthUiState
        data object WaitPhoneNumber : AuthUiState
        data class WaitCode(val phoneNumber: String) : AuthUiState
        data class WaitPassword(val hint: String) : AuthUiState
        data object Ready : AuthUiState
        data object LoggingOut : AuthUiState
        data object Closed : AuthUiState
    }

    val client: TdlClient = TdlClient.create()

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("telemetry", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Initializing)
    val authState: StateFlow<AuthUiState> = _authState

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    val hasApiCredentials: Boolean
        get() = prefs.getInt(KEY_API_ID, 0) != 0 && !prefs.getString(KEY_API_HASH, null).isNullOrBlank()

    init {
        scope.launch {
            client.authorizationStateUpdates.collect { update ->
                onAuthorizationState(update.authorizationState)
            }
        }
    }

    fun saveApiCredentials(apiId: Int, apiHash: String) {
        prefs.edit().putInt(KEY_API_ID, apiId).putString(KEY_API_HASH, apiHash.trim()).apply()
        if (_authState.value == AuthUiState.NeedApiCredentials) {
            scope.launch { sendTdlibParameters() }
        }
    }

    fun clearError() {
        _lastError.value = null
    }

    fun submitPhoneNumber(phoneNumber: String) = runAuthRequest {
        client.setAuthenticationPhoneNumber(phoneNumber.trim())
    }

    fun submitCode(code: String) = runAuthRequest {
        client.checkAuthenticationCode(code.trim())
    }

    fun submitPassword(password: String) = runAuthRequest {
        client.checkAuthenticationPassword(password)
    }

    fun logOut() = runAuthRequest {
        client.logOut()
    }

    private fun <T> runAuthRequest(block: suspend () -> TdlResult<T>) {
        scope.launch {
            _busy.value = true
            when (val result = block()) {
                is TdlResult.Success -> _lastError.value = null
                is TdlResult.Failure -> _lastError.value = "${result.code}: ${result.message}"
            }
            _busy.value = false
        }
    }

    private suspend fun onAuthorizationState(state: AuthorizationState) {
        when (state) {
            is AuthorizationStateWaitTdlibParameters -> {
                if (hasApiCredentials) {
                    sendTdlibParameters()
                } else {
                    _authState.value = AuthUiState.NeedApiCredentials
                }
            }
            is AuthorizationStateWaitPhoneNumber -> _authState.value = AuthUiState.WaitPhoneNumber
            is AuthorizationStateWaitCode ->
                _authState.value = AuthUiState.WaitCode(state.codeInfo.phoneNumber)
            is AuthorizationStateWaitPassword ->
                _authState.value = AuthUiState.WaitPassword(state.passwordHint)
            is AuthorizationStateReady -> _authState.value = AuthUiState.Ready
            is AuthorizationStateLoggingOut -> _authState.value = AuthUiState.LoggingOut
            is AuthorizationStateClosed -> _authState.value = AuthUiState.Closed
            else -> Unit
        }
    }

    private suspend fun sendTdlibParameters() {
        val apiId = prefs.getInt(KEY_API_ID, 0)
        val apiHash = prefs.getString(KEY_API_HASH, null) ?: return
        val databaseDirectory = File(appContext.filesDir, "tdlib-db")
        val filesDirectory = File(appContext.filesDir, "tdlib-files")
        databaseDirectory.mkdirs()
        filesDirectory.mkdirs()
        val result = client.setTdlibParameters(
            useTestDc = false,
            databaseDirectory = databaseDirectory.absolutePath,
            filesDirectory = filesDirectory.absolutePath,
            databaseEncryptionKey = ByteArray(0),
            useFileDatabase = true,
            useChatInfoDatabase = true,
            useMessageDatabase = true,
            useSecretChats = false,
            apiId = apiId,
            apiHash = apiHash,
            systemLanguageCode = "en",
            deviceModel = Build.MODEL.ifBlank { "Android" },
            systemVersion = "Android ${Build.VERSION.RELEASE}",
            applicationVersion = "1.0",
        )
        if (result is TdlResult.Failure) {
            // Wrong api_id/api_hash is the most likely cause; let the user re-enter them.
            _lastError.value = "${result.code}: ${result.message}"
            _authState.value = AuthUiState.NeedApiCredentials
        }
    }

    private companion object {
        const val KEY_API_ID = "api_id"
        const val KEY_API_HASH = "api_hash"
    }
}
