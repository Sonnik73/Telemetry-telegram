package com.sonnik.telemetry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sonnik.telemetry.td.TelegramClient.AuthUiState
import com.sonnik.telemetry.ui.AccountScreen
import com.sonnik.telemetry.ui.AuthScreen
import com.sonnik.telemetry.ui.ChatListScreen
import com.sonnik.telemetry.ui.ChatStatsScreen
import com.sonnik.telemetry.ui.ExportScreen
import com.sonnik.telemetry.ui.OverviewScreen
import com.sonnik.telemetry.ui.theme.TelemetryTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TelemetryTheme {
                TelemetryNavHost()
            }
        }
    }
}

@Composable
private fun TelemetryNavHost() {
    val navController = rememberNavController()
    val telegram = TelemetryApp.instance.telegram
    val authState by telegram.authState.collectAsState()

    LaunchedEffect(authState) {
        when (authState) {
            is AuthUiState.Ready -> navController.navigate("chats") {
                popUpTo("auth") { inclusive = true }
            }
            is AuthUiState.WaitPhoneNumber,
            is AuthUiState.NeedApiCredentials -> navController.navigate("auth") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
            else -> Unit
        }
    }

    NavHost(navController = navController, startDestination = "auth") {
        composable("auth") {
            AuthScreen()
        }
        composable("chats") {
            ChatListScreen(
                onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
                onOpenAccount = { navController.navigate("account") },
                onOpenOverview = { navController.navigate("overview") },
            )
        }
        composable(
            route = "chat/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.LongType }),
        ) { entry ->
            val chatId = entry.arguments?.getLong("chatId") ?: return@composable
            ChatStatsScreen(
                chatId = chatId,
                onBack = { navController.popBackStack() },
                onExport = { navController.navigate("chat/$chatId/export") },
            )
        }
        composable(
            route = "chat/{chatId}/export",
            arguments = listOf(navArgument("chatId") { type = NavType.LongType }),
        ) { entry ->
            val chatId = entry.arguments?.getLong("chatId") ?: return@composable
            ExportScreen(chatId = chatId, onBack = { navController.popBackStack() })
        }
        composable("account") {
            AccountScreen(onBack = { navController.popBackStack() })
        }
        composable("overview") {
            OverviewScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
            )
        }
    }
}
