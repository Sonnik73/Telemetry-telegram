package com.sonnik.telemetry

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sonnik.telemetry.td.TelegramClient.AuthUiState
import com.sonnik.telemetry.ui.AccountScreen
import com.sonnik.telemetry.ui.ArchiveScreen
import com.sonnik.telemetry.ui.AuthScreen
import com.sonnik.telemetry.ui.BirthdaysScreen
import com.sonnik.telemetry.ui.ChatListScreen
import com.sonnik.telemetry.ui.DossierScreen
import com.sonnik.telemetry.ui.CalendarScreen
import com.sonnik.telemetry.ui.CapturedMediaScreen
import com.sonnik.telemetry.ui.ChatStatsScreen
import com.sonnik.telemetry.ui.CleanupScreen
import com.sonnik.telemetry.ui.CoPresenceScreen
import com.sonnik.telemetry.ui.ContactGraphScreen
import com.sonnik.telemetry.ui.PrivateStatsScreen
import com.sonnik.telemetry.ui.ContactStatusScreen
import com.sonnik.telemetry.ui.DialogScreen
import com.sonnik.telemetry.ui.LastSeenScreen
import com.sonnik.telemetry.ui.PhoneLookupScreen
import com.sonnik.telemetry.ui.TypingLogScreen
import com.sonnik.telemetry.ui.ExportScreen
import com.sonnik.telemetry.ui.GalleryScreen
import com.sonnik.telemetry.ui.GlobalSearchScreen
import com.sonnik.telemetry.ui.KeywordsScreen
import com.sonnik.telemetry.ui.LockScreen
import com.sonnik.telemetry.ui.OverviewScreen
import com.sonnik.telemetry.ui.SettingsScreen
import com.sonnik.telemetry.ui.StoryFeedScreen
import com.sonnik.telemetry.ui.StoryViewersScreen
import com.sonnik.telemetry.ui.GeoScreen
import com.sonnik.telemetry.ui.GeoMapScreen
import com.sonnik.telemetry.ui.TrackerScreen
import com.sonnik.telemetry.ui.TrackerStatsScreen
import com.sonnik.telemetry.ui.theme.TelemetryTheme

class MainActivity : FragmentActivity() {

    // Lock gate state, driven by the activity lifecycle so it re-locks on background.
    private val locked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Drop plaintext scratch copies left behind by external viewers in the
        // previous session, so decrypted media never outlives the session that
        // opened it. Done at start (not on stop) so an open viewer keeps working.
        com.sonnik.telemetry.security.SecureCache.wipeSharedMedia(this)
        locked.value = TelemetryApp.instance.lock.isEnabled()
        setContent {
            TelemetryTheme {
                if (locked.value) {
                    LockScreen(onUnlocked = { locked.value = false })
                } else {
                    TelemetryNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // When the app lock is on, mark the window secure so its contents are hidden
        // from screenshots and the recent-apps preview (which would otherwise bypass
        // the lock). Applied while foreground so the recents thumbnail is protected.
        if (TelemetryApp.instance.lock.isEnabled()) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onStop() {
        super.onStop()
        // Re-lock when the app leaves the foreground, so returning requires unlock.
        if (TelemetryApp.instance.lock.isEnabled()) locked.value = true
    }
}

@Composable
private fun TelemetryNavHost() {
    val navController = rememberNavController()
    val telegram = TelemetryApp.instance.telegram
    val authState by telegram.authState.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(authState) {
        when (authState) {
            is AuthUiState.Ready -> {
                navController.navigate("chats") {
                    popUpTo("auth") { inclusive = true }
                }
                // Resume background presence tracking if any contacts are watched.
                val app = TelemetryApp.instance
                app.geo.start()
                app.intel.start()
                app.mediaAuto.start()
                if (app.presence.store.watchedIds().isNotEmpty() ||
                    app.mediaAuto.store.anyEnabled() ||
                    app.intel.keywords().isNotEmpty() ||
                    app.intel.storyAutoUsers().isNotEmpty() ||
                    app.intel.calendarReminders()
                ) {
                    app.presence.start()
                    com.sonnik.telemetry.presence.PresenceService.start(context)
                }
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
                onOpenTracker = { navController.navigate("tracker") },
                onOpenGeo = { navController.navigate("geo") },
                onOpenArchive = { navController.navigate("archive") },
                onOpenBirthdays = { navController.navigate("birthdays") },
                onOpenSearch = { navController.navigate("search") },
                onOpenKeywords = { navController.navigate("keywords") },
                onOpenContactStatus = { navController.navigate("contactstatus") },
                onOpenStories = { navController.navigate("stories") },
                onOpenContactStories = { navController.navigate("contactstories") },
                onOpenGraph = { navController.navigate("graph") },
                onOpenTyping = { navController.navigate("typinglog") },
                onOpenPhoneLookup = { navController.navigate("phonelookup") },
                onOpenLastSeen = { navController.navigate("lastseen") },
                onOpenCleanup = { navController.navigate("cleanup") },
                onOpenCaptured = { navController.navigate("captured") },
                onOpenCalendar = { navController.navigate("calendar") },
                onOpenPrivateStats = { navController.navigate("privatestats") },
                onOpenCoPresence = { navController.navigate("copresence") },
                onOpenSettings = { navController.navigate("settings") },
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
                onOpenDialog = { navController.navigate("chat/$chatId/dialog") },
                onOpenGallery = { navController.navigate("chat/$chatId/gallery") },
            )
        }
        composable(
            route = "chat/{chatId}/dialog",
            arguments = listOf(navArgument("chatId") { type = NavType.LongType }),
        ) { entry ->
            val chatId = entry.arguments?.getLong("chatId") ?: return@composable
            DialogScreen(chatId = chatId, onBack = { navController.popBackStack() })
        }
        composable(
            route = "chat/{chatId}/gallery",
            arguments = listOf(navArgument("chatId") { type = NavType.LongType }),
        ) { entry ->
            val chatId = entry.arguments?.getLong("chatId") ?: return@composable
            GalleryScreen(chatId = chatId, onBack = { navController.popBackStack() })
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
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("contactstatus") {
            ContactStatusScreen(
                onBack = { navController.popBackStack() },
                onOpenDossier = { userId -> navController.navigate("dossier/$userId") },
            )
        }
        composable("stories") {
            StoryViewersScreen(onBack = { navController.popBackStack() })
        }
        composable("contactstories") {
            StoryFeedScreen(onBack = { navController.popBackStack() })
        }
        composable("graph") {
            ContactGraphScreen(
                onBack = { navController.popBackStack() },
                onOpenDossier = { userId -> navController.navigate("dossier/$userId") },
            )
        }
        composable("typinglog") {
            TypingLogScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId -> navController.navigate("chat/$chatId/dialog") },
            )
        }
        composable("phonelookup") {
            PhoneLookupScreen(
                onBack = { navController.popBackStack() },
                onOpenDossier = { userId -> navController.navigate("dossier/$userId") },
            )
        }
        composable("lastseen") {
            LastSeenScreen(
                onBack = { navController.popBackStack() },
                onOpenDossier = { userId -> navController.navigate("dossier/$userId") },
            )
        }
        composable("copresence") {
            CoPresenceScreen(onBack = { navController.popBackStack() })
        }
        composable("cleanup") {
            CleanupScreen(onBack = { navController.popBackStack() })
        }
        composable("captured") {
            CapturedMediaScreen(onBack = { navController.popBackStack() })
        }
        composable("calendar") {
            CalendarScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId -> navController.navigate("chat/$chatId/dialog") },
            )
        }
        composable("privatestats") {
            PrivateStatsScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
            )
        }
        composable("search") {
            GlobalSearchScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId -> navController.navigate("chat/$chatId/dialog") },
            )
        }
        composable("keywords") {
            KeywordsScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId -> navController.navigate("chat/$chatId/dialog") },
            )
        }
        composable("overview") {
            OverviewScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
            )
        }
        composable("tracker") {
            TrackerScreen(
                onBack = { navController.popBackStack() },
                onOpenUser = { userId -> navController.navigate("tracker/$userId") },
            )
        }
        composable(
            route = "tracker/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.LongType }),
        ) { entry ->
            val userId = entry.arguments?.getLong("userId") ?: return@composable
            TrackerStatsScreen(
                userId = userId,
                onBack = { navController.popBackStack() },
                onOpenDossier = { navController.navigate("dossier/$userId") },
            )
        }
        composable("archive") {
            ArchiveScreen(onBack = { navController.popBackStack() })
        }
        composable("birthdays") {
            BirthdaysScreen(
                onBack = { navController.popBackStack() },
                onOpenDossier = { userId -> navController.navigate("dossier/$userId") },
            )
        }
        composable(
            route = "dossier/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.LongType }),
        ) { entry ->
            val userId = entry.arguments?.getLong("userId") ?: return@composable
            DossierScreen(userId = userId, onBack = { navController.popBackStack() })
        }
        composable("geo") {
            GeoScreen(
                onBack = { navController.popBackStack() },
                onOpenShare = { chatId, messageId -> navController.navigate("geo/$chatId/$messageId") },
            )
        }
        composable(
            route = "geo/{chatId}/{messageId}",
            arguments = listOf(
                navArgument("chatId") { type = NavType.LongType },
                navArgument("messageId") { type = NavType.LongType },
            ),
        ) { entry ->
            val chatId = entry.arguments?.getLong("chatId") ?: return@composable
            val messageId = entry.arguments?.getLong("messageId") ?: return@composable
            GeoMapScreen(chatId = chatId, messageId = messageId, onBack = { navController.popBackStack() })
        }
    }
}
