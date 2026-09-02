package com.sonnik.telemetry.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.text.KeyboardOptions
import com.sonnik.telemetry.TelemetryApp

/** Full-screen gate shown before the app content when a PIN lock is enabled. */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val app = TelemetryApp.instance
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var lockoutMs by remember { mutableStateOf(app.lock.lockoutRemainingMs()) }

    // Tick down the brute-force lockout so the button re-enables on its own.
    LaunchedEffect(lockoutMs > 0) {
        while (lockoutMs > 0) {
            kotlinx.coroutines.delay(1000)
            lockoutMs = app.lock.lockoutRemainingMs()
        }
    }

    val canBiometric = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK,
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val useBiometric = app.lock.biometricEnabled() && canBiometric

    fun promptBiometric() {
        val activity = context as? FragmentActivity ?: return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Telemetry заблокировано")
            .setSubtitle("Подтвердите, чтобы войти")
            .setNegativeButtonText("Ввести PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    LaunchedEffect(Unit) {
        if (useBiometric) promptBiometric()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.padding(8.dp))
        Text("Приложение заблокировано", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = pin,
            onValueChange = { new -> pin = new.filter { it.isDigit() }; error = false },
            label = { Text("PIN-код") },
            singleLine = true,
            isError = error,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        if (lockoutMs > 0) {
            Text(
                "Слишком много попыток. Повторите через ${formatLockout(lockoutMs)}.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
            )
        } else if (error) {
            Text("Неверный PIN", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        }
        Button(
            onClick = {
                if (app.lock.check(pin)) {
                    onUnlocked()
                } else {
                    error = true
                    pin = ""
                    lockoutMs = app.lock.lockoutRemainingMs()
                }
            },
            enabled = pin.length >= 4 && lockoutMs == 0L,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Войти")
        }
        if (useBiometric) {
            OutlinedButton(onClick = { promptBiometric() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Text("  Отпечаток / лицо")
            }
        }
    }
}

private fun formatLockout(ms: Long): String {
    val seconds = (ms + 999) / 1000
    return if (seconds < 60) "$seconds с" else "${seconds / 60} мин ${seconds % 60} с"
}
