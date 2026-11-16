package com.example.pardadns.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import com.example.pardadns.data.repository.BlocklistRepository
import com.example.pardadns.data.repository.ProRepository
import com.example.pardadns.billing.BillingManager
import android.content.pm.ApplicationInfo
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pardadns.data.model.DnsProvider
import com.example.pardadns.domain.model.VpnState
import com.example.pardadns.service.TrafficSnapshot
import com.example.pardadns.ui.theme.ThemeMode
import com.example.pardadns.ui.theme.StatusGreen
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton

@Composable
fun AppNavHost(
    vpnState: VpnState,
    providers: List<DnsProvider>,
    selectedProvider: DnsProvider?,
    latency: Map<String, Long?>,
    isRefreshing: Boolean,
    trafficStats: TrafficSnapshot = TrafficSnapshot.EMPTY,
    availableProviders: List<DnsProvider> = emptyList(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onSelectProvider: (DnsProvider) -> Unit,
    onRefresh: () -> Unit,
    onAddProvider: (DnsProvider) -> Unit,
    onRemoveProvider: (String) -> Unit = {},
    onUnhideProvider: (String) -> Unit = {},
    blocklistRepository: BlocklistRepository,
    proRepository: ProRepository,
    billingManager: BillingManager,
    onPause: (minutes: Int) -> Unit = {},
    onResume: () -> Unit = {},
) {
    // Tab indices: 0=Connect, 1=Settings hub, 2=Analytics, 3=AddProvider, 4=AppFilter, 5=Custom domains, 6=About
    // 7=All Queries, 8=All Blocked Queries, 9=All Blocked Apps, 10=Pro Paywall
    // 11=DNS Performance, 12=Query Log, 13=Blocklists & Filters, 14=Schedule & Auto-connect
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // Where the paywall goes after unlocking / on back. Defaults keep the legacy Stats flow.
    var paywallReturnTab by rememberSaveable { mutableIntStateOf(2) }
    var paywallBackTab by rememberSaveable { mutableIntStateOf(1) }

    val settingsVm: SettingsViewModel = hiltViewModel()
    val dnsMonitoring by settingsVm.dnsMonitoring.collectAsState()
    val isPro by proRepository.isPro.collectAsState()
    val price by billingManager.price.collectAsState()
    val purchasePending by billingManager.pending.collectAsState()
    val hostContext = LocalContext.current
    val activity = remember(hostContext) {
        generateSequence(hostContext) { (it as? ContextWrapper)?.baseContext }.firstNotNullOfOrNull { it as? Activity }
    }

    /** Opens the paywall from a Pro feature and comes back to it afterwards. */
    val openPaywall: (Int) -> Unit = { from ->
        paywallReturnTab = from
        paywallBackTab = from
        selectedTab = 10
    }

    // A purchase just went through: go back to the feature that opened the paywall.
    var wasPro by rememberSaveable { mutableStateOf(isPro) }
    LaunchedEffect(isPro) {
        if (isPro && !wasPro && selectedTab == 10) selectedTab = paywallReturnTab
        wasPro = isPro
    }

    val isConnected = vpnState is VpnState.Connected
    val connectGreen = StatusGreen

    // Animate the icon/label tint — persists regardless of which tab is active.
    val connectIconColor by animateColorAsState(
        targetValue = if (isConnected) connectGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(400),
        label = "connectIconColor",
    )
    val connectLabelColor by animateColorAsState(
        targetValue = if (isConnected) connectGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(400),
        label = "connectLabelColor",
    )

    // Navigate non-home tabs back to Connect on system back press.
    // Sub-settings screens navigate back to settings hub.
    if (selectedTab == 4 || selectedTab == 6 || selectedTab == 13 || selectedTab == 14) {
        BackHandler { selectedTab = 1 }
    } else if (selectedTab == 5) {
        BackHandler { selectedTab = 13 }
    } else if (selectedTab == 10) {
        BackHandler { selectedTab = paywallBackTab }
    } else if (selectedTab == 7 || selectedTab == 8 || selectedTab == 9 || selectedTab == 11 || selectedTab == 12) {
        BackHandler { selectedTab = 2 }
    } else if (selectedTab != 0) {
        BackHandler { selectedTab = 0 }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { selectedTab = 3 },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add DNS Provider")
                }
            }
        },
        bottomBar = {
            val haptic = LocalHapticFeedback.current

            // Interaction sources for press-scale animation
            val connectInteraction  = remember { MutableInteractionSource() }
            val statsInteraction    = remember { MutableInteractionSource() }
            val settingsInteraction = remember { MutableInteractionSource() }

            val connectPressed  by connectInteraction.collectIsPressedAsState()
            val statsPressed    by statsInteraction.collectIsPressedAsState()
            val settingsPressed by settingsInteraction.collectIsPressedAsState()

            // Google-like spring bounce on press
            val connectScale by animateFloatAsState(
                targetValue = if (connectPressed) 0.85f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "connectScale",
            )
            val statsScale by animateFloatAsState(
                targetValue = if (statsPressed) 0.85f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "statsScale",
            )
            val settingsScale by animateFloatAsState(
                targetValue = if (settingsPressed) 0.85f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "settingsScale",
            )

            NavigationBar {

                // ── Connect tab ─────────────────────────────────────────────
                NavigationBarItem(
                    selected = selectedTab == 0 || selectedTab == 3,
                    onClick  = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedTab = 0
                    },
                    interactionSource = connectInteraction,
                    icon = {
                        Icon(
                            modifier           = Modifier
                                .size(26.dp)
                                .scale(connectScale),
                            imageVector        = Icons.Default.Shield,
                            contentDescription = "Connect",
                            tint = connectIconColor,
                        )
                    },
                    label = {
                        Text(
                            text  = "Connect",
                            color = connectLabelColor,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )

                // ── Ad Stats tab ────────────────────────────────────────────
                NavigationBarItem(
                    selected = selectedTab == 2 || selectedTab == 10,
                    onClick  = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedTab = 2
                    },
                    interactionSource = statsInteraction,
                    icon = {
                        Icon(
                            modifier           = Modifier
                                .size(26.dp)
                                .scale(statsScale),
                            imageVector        = Icons.Outlined.BarChart,
                            contentDescription = "Stats",
                        )
                    },
                    label = {
                        Text(
                            text  = "Stats",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )

                // ── Settings tab ─────────────────────────────────────────────
                NavigationBarItem(
                    selected = selectedTab in listOf(1, 4, 5, 6, 13, 14),
                    onClick  = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedTab = 1
                    },
                    interactionSource = settingsInteraction,
                    icon = {
                        Icon(
                            modifier           = Modifier
                                .size(26.dp)
                                .scale(settingsScale),
                            imageVector        = Icons.Default.Settings,
                            contentDescription = "Settings",
                        )
                    },
                    label = {
                        Text(
                            text  = "Settings",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
        },
    ) { innerPadding ->
        // Derive blocklist set once — shared by Analytics + AllQueries screens.
        val blockedDomainsSet by blocklistRepository.domains.collectAsState()
        val blockedSet = remember(blockedDomainsSet) { blockedDomainsSet.toSet() }
        // Stats are free; blocking a domain from them is Pro.
        val onBlockToggle: (String, Boolean) -> Unit = { domain, block ->
            when {
                !isPro -> openPaywall(selectedTab)
                block -> blocklistRepository.addDomain(domain)
                else -> blocklistRepository.removeDomain(domain)
            }
        }
        val paywall: @Composable (String, () -> Unit) -> Unit = { title, onBack ->
            ProPaywallScreen(
                title          = title,
                onBack         = onBack,
                onUnlockPro    = { activity?.let(billingManager::buy) },
                isPro          = isPro,
                price          = price,
                pending        = purchasePending,
                onRestore      = { billingManager.refresh(userInitiated = true) },
                messages       = billingManager.messages,
                contentPadding = innerPadding,
            )
        }

        when (selectedTab) {
            0 -> PardaMainScreen(
                vpnState         = vpnState,
                providers        = providers,
                selectedProvider = selectedProvider,
                latency          = latency,
                isRefreshing     = isRefreshing,
                trafficStats     = trafficStats,
                onSelectProvider = { provider ->
                    if (provider.proOnly && !isPro) openPaywall(0) else onSelectProvider(provider)
                },
                onRefresh        = onRefresh,
                onAnalyticsClick = { selectedTab = 2 },
                onRemoveProvider = onRemoveProvider,
                pauseUnlocked    = isPro,
                onPause          = onPause,
                onResume         = onResume,
                onProRequired    = { openPaywall(0) },
                contentPadding   = innerPadding,
            )
            2 -> AnalyticsScreen(
                onBack                  = { selectedTab = 0 },
                onViewAllQueries        = { selectedTab = 7 },
                onViewAllBlockedQueries = { selectedTab = 8 },
                onViewAllBlockedApps    = { selectedTab = 9 },
                onViewDnsPerformance    = { selectedTab = 11 },
                onViewQueryLog          = { selectedTab = 12 },
                blockedDomains          = blockedSet,
                onBlockToggle           = onBlockToggle,
                isMonitoring            = dnsMonitoring,
                onToggleMonitoring      = settingsVm::setDnsMonitoring,
                contentPadding          = innerPadding,
            )
            3 -> AddProviderScreen(
                onBack = { selectedTab = 0 },
                onSave = { provider ->
                    onAddProvider(provider)
                    selectedTab = 0
                },
                onSaveWithoutConnect = { provider ->
                    onAddProvider(provider)
                    selectedTab = 0
                },
                availableProviders = availableProviders,
                onUnhideProvider = { id ->
                    onUnhideProvider(id)
                    selectedTab = 0
                },
                existingProviderNames = providers.map { it.name }.toSet(),
                proProvidersUnlocked = isPro,
                onProRequired = { openPaywall(3) },
                contentPadding = innerPadding,
            )
            4 -> if (!isPro) {
                paywall("App Filter") { selectedTab = 1 }
            } else {
                val settingsVm: SettingsViewModel = hiltViewModel()
                val apps     by settingsVm.apps.collectAsState()
                val excluded by settingsVm.excluded.collectAsState()
                AppFilterScreen(
                    apps           = apps,
                    excluded       = excluded,
                    onToggle       = settingsVm::toggleExclusion,
                    onBack         = { selectedTab = 1 },
                    contentPadding = innerPadding,
                )
            }
            5 -> {
                if (!isPro) {
                    paywall("Custom Blocklist") { selectedTab = 13 }
                } else {
                    val blockedDomains by blocklistRepository.domains.collectAsState()
                    BlocklistScreen(
                        domains        = blockedDomains,
                        onAddDomain    = blocklistRepository::addDomain,
                        onRemoveDomain = blocklistRepository::removeDomain,
                        onClearAll     = blocklistRepository::clearAll,
                        onBack         = { selectedTab = 13 },
                        contentPadding = innerPadding,
                    )
                }
            }
            6 -> AboutScreen(
                onBack         = { selectedTab = 1 },
                contentPadding = innerPadding,
            )
            7 -> AllQueriesScreen(
                type           = AllQueriesType.QUERIES,
                onBack         = { selectedTab = 2 },
                blockedDomains = blockedSet,
                onBlockToggle  = onBlockToggle,
                contentPadding = innerPadding,
            )
            8 -> AllQueriesScreen(
                type           = AllQueriesType.BLOCKED_QUERIES,
                onBack         = { selectedTab = 2 },
                blockedDomains = blockedSet,
                onBlockToggle  = onBlockToggle,
                contentPadding = innerPadding,
            )
            9 -> AllQueriesScreen(
                type           = AllQueriesType.BLOCKED_APPS,
                onBack         = { selectedTab = 2 },
                contentPadding = innerPadding,
            )
            10 -> paywall("Panda DNS Pro") { selectedTab = paywallBackTab }
            11 -> if (!isPro) {
                paywall("DNS Performance") { selectedTab = 2 }
            } else {
                DnsPerformanceScreen(
                    onBack         = { selectedTab = 2 },
                    contentPadding = innerPadding,
                )
            }
            12 -> if (!isPro) {
                paywall("Query Log") { selectedTab = 2 }
            } else {
                QueryLogScreen(
                    onBack         = { selectedTab = 2 },
                    contentPadding = innerPadding,
                )
            }
            13 -> FiltersScreen(
                viewModel           = hiltViewModel(),
                onBack              = { selectedTab = 1 },
                onOpenCustomDomains = { selectedTab = 5 },
                onProRequired       = { openPaywall(13) },
                contentPadding      = innerPadding,
            )
            14 -> AutomationScreen(
                viewModel      = hiltViewModel(),
                vpnState       = vpnState,
                onPause        = onPause,
                onResume       = onResume,
                onBack         = { selectedTab = 1 },
                onProRequired  = { openPaywall(14) },
                contentPadding = innerPadding,
            )
            else -> {
                val enforceDoh by settingsVm.enforceDoh.collectAsState()
                val context = LocalContext.current
                val debuggable = remember {
                    context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                }
                SettingsScreen(
                    onNavigateToAppFilter  = { selectedTab = 4 },
                    onNavigateToBlocklist  = { selectedTab = 13 },
                    onNavigateToAbout      = { selectedTab = 6 },
                    onNavigateToPro        = { openPaywall(1) },
                    onNavigateToAutomation = { selectedTab = 14 },
                    showProButton         = !isPro,
                    isPro                 = isPro,
                    onDebugProChange      = if (debuggable) { on -> proRepository.setDebugOverride(on) } else null,
                    themeMode             = themeMode,
                    onThemeModeChange     = onThemeModeChange,
                    enforceDoh            = enforceDoh,
                    onEnforceDohChange    = settingsVm::setEnforceDoh,
                    dnsMonitoring         = dnsMonitoring,
                    onDnsMonitoringChange = settingsVm::setDnsMonitoring,
                    contentPadding        = innerPadding,
                )
            }
        }
    }
}
