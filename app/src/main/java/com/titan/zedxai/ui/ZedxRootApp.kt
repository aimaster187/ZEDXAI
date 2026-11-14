package com.titan.zedxai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.titan.zedxai.agents.TitanCrewOrchestrator
import com.titan.zedxai.ai.MultiProviderAiEngine
import com.titan.zedxai.data.local.AppPreferencesRepository
import com.titan.zedxai.data.local.AppPreferencesState
import com.titan.zedxai.data.local.ZedxDatabase
import com.titan.zedxai.firebase.GoogleAuthHelper
import com.titan.zedxai.firebase.ZedxFirestoreRepository
import com.titan.zedxai.firebase.authStateFlow
import com.titan.zedxai.security.KeyVaultManager
import com.titan.zedxai.ui.navigation.ZedxRoute
import com.titan.zedxai.ui.screens.AboutScreen
import com.titan.zedxai.ui.screens.ApkBuilderScreen
import com.titan.zedxai.ui.screens.AutomationCenterScreen
import com.titan.zedxai.ui.screens.BrowserScreen
import com.titan.zedxai.ui.screens.ChatHistoryScreen
import com.titan.zedxai.ui.screens.CodingStudioScreen
import com.titan.zedxai.ui.screens.DeleteAccountScreen
import com.titan.zedxai.ui.screens.ExploreToolsScreen
import com.titan.zedxai.ui.screens.FileManagerScreen
import com.titan.zedxai.ui.screens.HelpScreen
import com.titan.zedxai.ui.screens.HomeDashboardScreen
import com.titan.zedxai.ui.screens.MemoryManagerScreen
import com.titan.zedxai.ui.screens.ModelSelectorScreen
import com.titan.zedxai.ui.screens.NotificationsScreen
import com.titan.zedxai.ui.screens.OnboardingScreen
import com.titan.zedxai.ui.screens.PdfDocumentViewerScreen
import com.titan.zedxai.ui.screens.PermissionsScreen
import com.titan.zedxai.ui.screens.PhoneAssistantScreen
import com.titan.zedxai.ui.screens.PrivacyScreen
import com.titan.zedxai.ui.screens.ProfileScreen
import com.titan.zedxai.ui.screens.ProviderManagerScreen
import com.titan.zedxai.ui.screens.SafeTerminalScreen
import com.titan.zedxai.ui.screens.SettingsScreen
import com.titan.zedxai.ui.screens.SignInScreen
import com.titan.zedxai.ui.screens.SplashScreen
import com.titan.zedxai.ui.screens.TitanChatScreen
import com.titan.zedxai.ui.screens.TitanCrewScreen
import com.titan.zedxai.ui.screens.VisionScannerScreen
import com.titan.zedxai.ui.screens.VoiceAssistantScreen
import com.titan.zedxai.ui.screens.WebsiteBuilderScreen
import com.titan.zedxai.ui.theme.CrystalBackdrop
import com.titan.zedxai.ui.theme.LocalCrystalTokens
import com.titan.zedxai.ui.theme.ZedxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ZedxRootApp() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val prefsRepo = remember(appContext) { AppPreferencesRepository(appContext) }
    val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = AppPreferencesState())
    val authFlow = remember { Firebase.auth.authStateFlow() }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    var isBootstrapping by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val credentialManager = CredentialManager.create(context)
        GoogleAuthHelper.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { isBootstrapping = false },
            onUnauthenticated = { isBootstrapping = false },
            scope = this
        )
        delay(600L)
        isBootstrapping = false
    }

    ZedxTheme(themeMode = prefs.themeMode) {
        when {
            isBootstrapping -> SplashScreen()
            !prefs.onboardingCompleted -> OnboardingScreen(
                onFinished = {
                    scope.launch { prefsRepo.setOnboardingCompleted(true) }
                }
            )
            currentUser == null -> SignInScreen(onAuthSuccess = {})
            else -> AuthenticatedZedxHost(
                user = currentUser!!,
                prefsRepo = prefsRepo
            )
        }
    }
}

private data class DrawerNavItem(
    val route: ZedxRoute,
    val icon: ImageVector,
    val group: String
)

@Composable
private fun AuthenticatedZedxHost(
    user: FirebaseUser,
    prefsRepo: AppPreferencesRepository
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()

    val viewModel: ZedxViewModel = viewModel(
        key = "zedx_vm_${user.uid}",
        factory = viewModelFactory {
            initializer {
                val aiEngine = MultiProviderAiEngine()
                ZedxViewModel(
                    db = ZedxDatabase.getInstance(appContext),
                    firestoreRepo = ZedxFirestoreRepository(appContext),
                    prefsRepo = prefsRepo,
                    keyVault = KeyVaultManager(appContext),
                    aiEngine = aiEngine,
                    crewOrchestrator = TitanCrewOrchestrator(aiEngine),
                    currentUserId = user.uid,
                    userEmail = user.email.orEmpty(),
                    userName = user.displayName?.takeIf { it.isNotBlank() }
                        ?: user.email?.substringBefore("@")
                        ?: "Titan User",
                    photoUrl = user.photoUrl?.toString()
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRouteStr = backStackEntry?.destination?.route ?: ZedxRoute.HOME.route
    val currentRoute = ZedxRoute.entries.firstOrNull { it.route == currentRouteStr } ?: ZedxRoute.HOME

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    val unreadCount = remember(notifications) { notifications.count { !it.isRead } }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val navigateTo: (ZedxRoute) -> Unit = { target ->
        if (target.route != currentRouteStr) {
            navController.navigate(target.route) {
                launchSingleTop = true
                restoreState = true
            }
        }
        scope.launch { drawerState.close() }
    }

    val primaryTabs = remember {
        listOf(
            ZedxRoute.HOME to Icons.Rounded.Home,
            ZedxRoute.CHAT to Icons.Rounded.Chat,
            ZedxRoute.VOICE to Icons.Rounded.Mic,
            ZedxRoute.AGENTS to Icons.Rounded.Psychology,
            ZedxRoute.EXPLORE to Icons.Rounded.Apps
        )
    }

    val drawerItems = remember {
        listOf(
            DrawerNavItem(ZedxRoute.HOME, Icons.Rounded.Home, "Core"),
            DrawerNavItem(ZedxRoute.CHAT, Icons.Rounded.Chat, "Core"),
            DrawerNavItem(ZedxRoute.HISTORY, Icons.Rounded.History, "Core"),
            DrawerNavItem(ZedxRoute.AGENTS, Icons.Rounded.Psychology, "Core"),
            DrawerNavItem(ZedxRoute.VOICE, Icons.Rounded.Mic, "Core"),
            DrawerNavItem(ZedxRoute.EXPLORE, Icons.Rounded.Apps, "Core"),

            DrawerNavItem(ZedxRoute.PROVIDERS, Icons.Rounded.Key, "AI Engine"),
            DrawerNavItem(ZedxRoute.MODEL_SELECTOR, Icons.Rounded.Hub, "AI Engine"),
            DrawerNavItem(ZedxRoute.MEMORY, Icons.Rounded.Memory, "AI Engine"),

            DrawerNavItem(ZedxRoute.BROWSER, Icons.Rounded.Language, "Developer Studio"),
            DrawerNavItem(ZedxRoute.CODING_STUDIO, Icons.Rounded.Code, "Developer Studio"),
            DrawerNavItem(ZedxRoute.TERMINAL, Icons.Rounded.Terminal, "Developer Studio"),
            DrawerNavItem(ZedxRoute.WEBSITE_BUILDER, Icons.Rounded.Web, "Developer Studio"),
            DrawerNavItem(ZedxRoute.APK_BUILDER, Icons.Rounded.Android, "Developer Studio"),

            DrawerNavItem(ZedxRoute.VISION_SCANNER, Icons.Rounded.CameraAlt, "Vision & Docs"),
            DrawerNavItem(ZedxRoute.PDF_VIEWER, Icons.Rounded.PictureAsPdf, "Vision & Docs"),
            DrawerNavItem(ZedxRoute.FILE_MANAGER, Icons.Rounded.FolderOpen, "Vision & Docs"),

            DrawerNavItem(ZedxRoute.AUTOMATION, Icons.Rounded.AccountTree, "Device & Comms"),
            DrawerNavItem(ZedxRoute.PHONE_ASSISTANT, Icons.Rounded.PhoneAndroid, "Device & Comms"),
            DrawerNavItem(ZedxRoute.NOTIFICATIONS, Icons.Rounded.Notifications, "Device & Comms"),

            DrawerNavItem(ZedxRoute.PROFILE, Icons.Rounded.Person, "Account"),
            DrawerNavItem(ZedxRoute.SETTINGS, Icons.Rounded.Settings, "Account"),
            DrawerNavItem(ZedxRoute.PRIVACY, Icons.Rounded.Security, "Account"),
            DrawerNavItem(ZedxRoute.PERMISSIONS, Icons.Rounded.VerifiedUser, "Account"),
            DrawerNavItem(ZedxRoute.HELP, Icons.Rounded.HelpOutline, "Account"),
            DrawerNavItem(ZedxRoute.ABOUT, Icons.Rounded.Info, "Account"),
            DrawerNavItem(ZedxRoute.DELETE_ACCOUNT, Icons.Rounded.DeleteForever, "Account")
        )
    }

    val primaryRouteIds = remember { primaryTabs.map { it.first.route }.toSet() }
    val tokens = LocalCrystalTokens.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = "ZEDX AI",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Text(
                        text = "BY THE TITAN AI • v1.0.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.height(12.dp))

                    val groups = drawerItems.groupBy { it.group }
                    groups.forEach { (groupName, entries) ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        Text(
                            text = groupName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        entries.forEach { item ->
                            NavigationDrawerItem(
                                label = { Text(item.route.title) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                selected = currentRoute == item.route,
                                onClick = { navigateTo(item.route) },
                                modifier = Modifier
                                    .padding(vertical = 1.dp)
                                    .testTag("drawer_nav_${item.route.route}")
                            )
                        }
                    }
                }
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 840.dp

            CrystalBackdrop(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        Surface(
                            color = tokens.glassSurface,
                            border = BorderStroke(1.dp, tokens.glassBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentRoute.route !in primaryRouteIds) {
                                    IconButton(
                                        onClick = { navController.popBackStack() },
                                        modifier = Modifier.testTag("topbar_back_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                                    }
                                } else {
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier.testTag("topbar_menu_button")
                                    ) {
                                        Icon(Icons.Rounded.Menu, contentDescription = "Open menu")
                                    }
                                }

                                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                    Text(
                                        text = currentRoute.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${prefs.activeProviderId} • ${prefs.activeModelId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                AssistChip(
                                    onClick = { navigateTo(ZedxRoute.MODEL_SELECTOR) },
                                    label = { Text("Models", maxLines = 1) },
                                    leadingIcon = { Icon(Icons.Rounded.Hub, null, Modifier.size(16.dp)) },
                                    modifier = Modifier.testTag("topbar_models_chip")
                                )

                                IconButton(
                                    onClick = { navigateTo(ZedxRoute.NOTIFICATIONS) },
                                    modifier = Modifier.testTag("topbar_notifications_button")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (unreadCount > 0) {
                                                Badge { Text(unreadCount.coerceAtMost(99).toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Notifications, contentDescription = "Notifications")
                                    }
                                }

                                IconButton(
                                    onClick = { navigateTo(ZedxRoute.PROFILE) },
                                    modifier = Modifier.testTag("topbar_profile_button")
                                ) {
                                    Icon(Icons.Rounded.Person, contentDescription = "Profile")
                                }
                            }
                        }
                    },
                    bottomBar = {
                        if (!useRail) {
                            Surface(
                                color = tokens.glassSurface,
                                border = BorderStroke(1.dp, tokens.glassBorder),
                                shape = RoundedCornerShape(26.dp),
                                tonalElevation = 6.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                NavigationBar(
                                    containerColor = Color.Transparent,
                                    windowInsets = WindowInsets(0, 0, 0, 0)
                                ) {
                                    primaryTabs.forEach { (route, icon) ->
                                        NavigationBarItem(
                                            selected = currentRoute == route,
                                            onClick = { navigateTo(route) },
                                            icon = { Icon(icon, contentDescription = route.title) },
                                            label = {
                                                Text(
                                                    route.title.substringAfterLast(" "),
                                                    maxLines = 1
                                                )
                                            },
                                            modifier = Modifier.testTag("bottom_nav_${route.route}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useRail) {
                            NavigationRail(
                                containerColor = tokens.glassSurface,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 8.dp)
                            ) {
                                primaryTabs.forEach { (route, icon) ->
                                    NavigationRailItem(
                                        selected = currentRoute == route,
                                        onClick = { navigateTo(route) },
                                        icon = { Icon(icon, contentDescription = route.title) },
                                        label = { Text(route.title.substringAfterLast(" ")) }
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = ZedxRoute.HOME.route,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable(ZedxRoute.HOME.route) {
                                    HomeDashboardScreen(viewModel = viewModel, onNavigate = navigateTo)
                                }
                                composable(ZedxRoute.CHAT.route) {
                                    TitanChatScreen(viewModel = viewModel, onNavigate = navigateTo)
                                }
                                composable(ZedxRoute.HISTORY.route) {
                                    ChatHistoryScreen(
                                        viewModel = viewModel,
                                        onOpenChat = { navigateTo(ZedxRoute.CHAT) }
                                    )
                                }
                                composable(ZedxRoute.AGENTS.route) {
                                    TitanCrewScreen(viewModel = viewModel)
                                }
                                composable(ZedxRoute.VOICE.route) {
                                    VoiceAssistantScreen(viewModel = viewModel)
                                }
                                composable(ZedxRoute.EXPLORE.route) {
                                    ExploreToolsScreen(onNavigate = navigateTo)
                                }
                                composable(ZedxRoute.PROVIDERS.route) {
                                    ProviderManagerScreen(viewModel = viewModel)
                                }
                                composable(ZedxRoute.MODEL_SELECTOR.route) {
                                    ModelSelectorScreen(
                                        viewModel = viewModel,
                                        onDone = { navController.popBackStack() }
                                    )
                                }
                                composable(ZedxRoute.BROWSER.route) {
                                    BrowserScreen(viewModel = viewModel)
                                }
                                composable(ZedxRoute.CODING_STUDIO.route) {
                                    CodingStudioScreen(viewModel = viewModel)
                                }
                                composable(ZedxRoute.TERMINAL.route) {
                                    SafeTerminalScreen(viewModel = viewModel)
                                }
                                composable(ZedxRoute.WEBSITE_BUILDER.route) {
                                    WebsiteBuilderScreen(viewModel = viewModel)
                                }
                                composable(ZedxRoute.APK_BUILDER.route) {
                                    ApkBuilderScreen(viewModel = viewModel)
                                }
                                composable(ZedxRoute.AUTOMATION.route) {
                                    AutomationCenterScreen(viewModel = viewModel)
                                }
                                composable(ZedxRoute.FILE_MANAGER.route) {
                                    FileManagerScreen(viewModel = viewModel)
                                }
                                composable(ZedxRoute.VISION_SCANNER.route) {
                                    VisionScannerScreen(viewModel = viewModel)
                                }
                                composable(ZedxRoute.PDF_VIEWER.route) {
                                    PdfDocumentViewerScreen(viewModel = viewModel)
                                }
                                composable(ZedxRoute.PHONE_ASSISTANT.route) {
                                    PhoneAssistantScreen(viewModel = viewModel)
                                }
                                composable(ZedxRoute.NOTIFICATIONS.route) {
                                    NotificationsScreen(viewModel = viewModel)
                                }
                                composable(ZedxRoute.PROFILE.route) {
                                    ProfileScreen(
                                        viewModel = viewModel,
                                        onSignOut = {
                                            GoogleAuthHelper.signOut(credentialManager, scope)
                                        },
                                        onNavigate = navigateTo
                                    )
                                }
                                composable(ZedxRoute.MEMORY.route) {
                                    MemoryManagerScreen(viewModel = viewModel)
                                }
                                composable(ZedxRoute.SETTINGS.route) {
                                    SettingsScreen(viewModel = viewModel, onNavigate = navigateTo)
                                }
                                composable(ZedxRoute.PRIVACY.route) {
                                    PrivacyScreen(
                                        viewModel = viewModel,
                                        onNavigateDelete = { navigateTo(ZedxRoute.DELETE_ACCOUNT) }
                                    )
                                }
                                composable(ZedxRoute.PERMISSIONS.route) {
                                    PermissionsScreen()
                                }
                                composable(ZedxRoute.HELP.route) {
                                    HelpScreen()
                                }
                                composable(ZedxRoute.ABOUT.route) {
                                    AboutScreen()
                                }
                                composable(ZedxRoute.DELETE_ACCOUNT.route) {
                                    DeleteAccountScreen(
                                        viewModel = viewModel,
                                        onDeleted = {
                                            GoogleAuthHelper.signOut(credentialManager, scope)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
