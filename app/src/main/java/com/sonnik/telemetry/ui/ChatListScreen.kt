package com.sonnik.telemetry.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.data.ChatKind
import com.sonnik.telemetry.data.ChatSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenChat: (Long) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenOverview: () -> Unit,
    onOpenTracker: () -> Unit,
    onOpenGeo: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenBirthdays: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenKeywords: () -> Unit,
    onOpenContactStatus: () -> Unit,
    onOpenStories: () -> Unit,
    onOpenContactStories: () -> Unit,
    onOpenGraph: () -> Unit,
    onOpenTyping: () -> Unit,
    onOpenPhoneLookup: () -> Unit,
    onOpenLastSeen: () -> Unit,
    onOpenCleanup: () -> Unit,
    onOpenCaptured: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenPrivateStats: () -> Unit,
    onOpenCoPresence: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val repository = TelemetryApp.instance.chats
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var chats by remember { mutableStateOf<List<ChatSummary>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        chats = null
        error = null
        repository.loadAllChats()
            .onSuccess { chats = it }
            .onFailure { error = it.message }
    }

    var crashText by remember { mutableStateOf(TelemetryApp.instance.readLastCrash()) }

    fun go(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Telemetry",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
                HorizontalDivider()
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.QueryStats, contentDescription = null) },
                    label = { Text("Сводка по аккаунту") },
                    selected = false,
                    onClick = { go(onOpenOverview) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.People, contentDescription = null) },
                    label = { Text("Онлайн-трекер") },
                    selected = false,
                    onClick = { go(onOpenTracker) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Place, contentDescription = null) },
                    label = { Text("Геотрекинг") },
                    selected = false,
                    onClick = { go(onOpenGeo) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    label = { Text("Архив: удалённое и правки") },
                    selected = false,
                    onClick = { go(onOpenArchive) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Cake, contentDescription = null) },
                    label = { Text("Дни рождения контактов") },
                    selected = false,
                    onClick = { go(onOpenBirthdays) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.PersonOff, contentDescription = null) },
                    label = { Text("Контакты: удалили/заблокировали") },
                    selected = false,
                    onClick = { go(onOpenContactStatus) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                    label = { Text("Кто смотрел мои истории") },
                    selected = false,
                    onClick = { go(onOpenStories) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                    label = { Text("Истории контактов") },
                    selected = false,
                    onClick = { go(onOpenContactStories) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Hub, contentDescription = null) },
                    label = { Text("Граф связей") },
                    selected = false,
                    onClick = { go(onOpenGraph) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Keyboard, contentDescription = null) },
                    label = { Text("Кто печатает") },
                    selected = false,
                    onClick = { go(onOpenTyping) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    label = { Text("Последний онлайн") },
                    selected = false,
                    onClick = { go(onOpenLastSeen) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                    label = { Text("Поиск по номеру") },
                    selected = false,
                    onClick = { go(onOpenPhoneLookup) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.CleaningServices, contentDescription = null) },
                    label = { Text("Чистильщик следов") },
                    selected = false,
                    onClick = { go(onOpenCleanup) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                    label = { Text("Перехваченные медиа") },
                    selected = false,
                    onClick = { go(onOpenCaptured) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Event, contentDescription = null) },
                    label = { Text("Календарь из чатов") },
                    selected = false,
                    onClick = { go(onOpenCalendar) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.GroupWork, contentDescription = null) },
                    label = { Text("Со-присутствие") },
                    selected = false,
                    onClick = { go(onOpenCoPresence) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Insights, contentDescription = null) },
                    label = { Text("Статистика личных чатов") },
                    selected = false,
                    onClick = { go(onOpenPrivateStats) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
                    label = { Text("Отслеживание слов") },
                    selected = false,
                    onClick = { go(onOpenKeywords) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Поиск по всем чатам") },
                    selected = false,
                    onClick = { go(onOpenSearch) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Настройки") },
                    selected = false,
                    onClick = { go(onOpenSettings) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    label = { Text("Аккаунт") },
                    selected = false,
                    onClick = { go(onOpenAccount) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        },
    ) {
    Scaffold(
        // Open the drawer with a left-edge swipe (the built-in gesture is often
        // swallowed by the system back-gesture, so detect it explicitly here).
        modifier = Modifier.pointerInput(Unit) {
            val edge = 32.dp.toPx()
            val trigger = 48.dp.toPx()
            var startX = 0f
            var accum = 0f
            var fired = false
            detectHorizontalDragGestures(
                onDragStart = { offset -> startX = offset.x; accum = 0f; fired = false },
            ) { _, dragAmount ->
                accum += dragAmount
                if (!fired && startX <= edge && accum >= trigger && drawerState.isClosed) {
                    fired = true
                    scope.launch { drawerState.open() }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Чаты") },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Меню")
                    }
                },
                actions = {
                    InfoButton(
                        "Список чатов",
                        listOf(
                            "Все диалоги, группы и каналы аккаунта с поиском по названию.",
                            "Тап по чату — статистика, экспорт, диалог со скрытым чтением и галерея медиа.",
                            "Меню слева (☰) — сводка, онлайн-трекер, геотрекинг, архив удалённого, дни рождения, поиск, аккаунт.",
                            "Лупа — глобальный поиск сообщений по всем чатам.",
                        ),
                    )
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Поиск по всем чатам")
                    }
                    IconButton(onClick = { reloadKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            crashText?.let { crash ->
                CrashCard(
                    crash = crash,
                    onDismiss = {
                        TelemetryApp.instance.clearLastCrash()
                        crashText = null
                    },
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск по названию") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Ошибка: $error", color = MaterialTheme.colorScheme.error)
                }
                chats == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Загрузка чатов…", modifier = Modifier.padding(top = 12.dp))
                    }
                }
                else -> {
                    val visible = chats!!.filter { it.title.contains(query.trim(), ignoreCase = true) }
                    Text(
                        "Всего: ${visible.size}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(visible, key = { it.id }) { chat ->
                            ChatRow(chat = chat, onClick = { onOpenChat(chat.id) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun CrashCard(crash: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "В прошлый раз приложение аварийно завершилось",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                crash.lineSequence().take(4).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
            )
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(crash)) }) {
                    Text("Скопировать отчёт")
                }
                TextButton(onClick = onDismiss) {
                    Text("Скрыть")
                }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatSummary, onClick: () -> Unit) {
    val kindLabel = when (chat.kind) {
        ChatKind.PRIVATE -> "Личный чат"
        ChatKind.SECRET -> "Секретный чат"
        ChatKind.GROUP -> "Группа"
        ChatKind.CHANNEL -> "Канал"
    }
    val details = buildList {
        add(kindLabel)
        chat.memberCount?.let { add("${formatCount(it)} участн.") }
        if (chat.lastMessageDate > 0) add(formatDate(chat.lastMessageDate))
    }
    ListItem(
        headlineContent = { Text(chat.title) },
        supportingContent = { Text(details.joinToString(" · ")) },
        trailingContent = {
            if (chat.unreadCount > 0) Text(formatCount(chat.unreadCount))
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
