package com.sonnik.telemetry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.td.TelegramClient.AuthUiState

@Composable
fun AuthScreen() {
    val telegram = TelemetryApp.instance.telegram
    val state by telegram.authState.collectAsState()
    val error by telegram.lastError.collectAsState()
    val busy by telegram.busy.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Telemetry", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Статистика и экспорт чатов Telegram",
                style = MaterialTheme.typography.bodyMedium,
            )

            when (val s = state) {
                is AuthUiState.Initializing, AuthUiState.LoggingOut -> {
                    CircularProgressIndicator()
                    Text("Подключение к Telegram…")
                }
                is AuthUiState.NeedApiCredentials -> ApiCredentialsStep()
                is AuthUiState.WaitPhoneNumber -> PhoneStep(busy)
                is AuthUiState.WaitCode -> CodeStep(s.phoneNumber, busy)
                is AuthUiState.WaitPassword -> PasswordStep(s.hint, busy)
                is AuthUiState.Ready -> Text("Готово, входим…")
                is AuthUiState.Closed -> Text("Сессия закрыта. Перезапустите приложение.")
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ApiCredentialsStep() {
    val telegram = TelemetryApp.instance.telegram
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }

    Text(
        "Шаг 1. Получите api_id и api_hash на my.telegram.org → API development tools и введите их здесь. " +
            "Ключи сохраняются только на этом устройстве.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        value = apiId,
        onValueChange = { apiId = it.filter(Char::isDigit) },
        label = { Text("api_id") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = apiHash,
        onValueChange = { apiHash = it },
        label = { Text("api_hash") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { telegram.saveApiCredentials(apiId.toIntOrNull() ?: 0, apiHash) },
        enabled = (apiId.toIntOrNull() ?: 0) != 0 && apiHash.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Продолжить")
    }
}

@Composable
private fun PhoneStep(busy: Boolean) {
    val telegram = TelemetryApp.instance.telegram
    var phone by remember { mutableStateOf("") }

    Text("Шаг 2. Введите номер телефона аккаунта в международном формате.")
    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text("+7 900 000-00-00") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { telegram.submitPhoneNumber(phone) },
        enabled = !busy && phone.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Отправить код")
    }
}

@Composable
private fun CodeStep(phoneNumber: String, busy: Boolean) {
    val telegram = TelemetryApp.instance.telegram
    var code by remember { mutableStateOf("") }

    Text("Шаг 3. Код отправлен на $phoneNumber (в Telegram или по SMS).")
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter(Char::isDigit) },
        label = { Text("Код") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { telegram.submitCode(code) },
        enabled = !busy && code.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Войти")
    }
}

@Composable
private fun PasswordStep(hint: String, busy: Boolean) {
    val telegram = TelemetryApp.instance.telegram
    var password by remember { mutableStateOf("") }

    Text("Шаг 4. Введите пароль двухфакторной аутентификации." + if (hint.isNotBlank()) " Подсказка: $hint" else "")
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Пароль") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { telegram.submitPassword(password) },
        enabled = !busy && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Подтвердить")
    }
}
