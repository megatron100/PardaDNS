package com.example.pardadns

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import com.example.pardadns.domain.model.VpnState
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.pardadns.data.repository.ThemeRepository
import com.example.pardadns.data.repository.RemoteConfigRepository
import com.example.pardadns.data.repository.BlocklistRepository
import com.example.pardadns.data.repository.AutomationRepository
import com.example.pardadns.data.repository.ProRepository
import com.example.pardadns.billing.BillingManager
import com.example.pardadns.data.model.DnsProvider
import com.example.pardadns.domain.event.VpnEvent
import com.example.pardadns.domain.event.VpnEventBus
import com.example.pardadns.service.PardaTileService
import com.example.pardadns.service.ProtectionScheduler
import java.time.LocalDateTime
import com.example.pardadns.ui.theme.ThemeMode
import com.example.pardadns.domain.model.VpnAction
import com.example.pardadns.service.PardaVpnService
import com.example.pardadns.ui.AppNavHost
import com.example.pardadns.ui.BatteryOptimizationDialog
import com.example.pardadns.ui.DohWarningDialog
import com.example.pardadns.ui.VpnPermissionDialog
import com.example.pardadns.ui.MainViewModel
import com.example.pardadns.ui.SplashScreen
import com.example.pardadns.ui.theme.PardaDNSTheme
import com.example.pardadns.ui.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val themeRepository by lazy { ThemeRepository(this) }
    @javax.inject.Inject lateinit var remoteConfig: RemoteConfigRepository
    @javax.inject.Inject lateinit var blocklistRepository: BlocklistRepository
    @javax.inject.Inject lateinit var proRepository: ProRepository
    @javax.inject.Inject lateinit var automationRepository: AutomationRepository
    @javax.inject.Inject lateinit var eventBus: VpnEventBus
    @javax.inject.Inject lateinit var billingManager: BillingManager


    /** Held while we wait for the system VPN-permission dialog to resolve. */
    private var pendingConnectAction: VpnAction.Connect? = null

    /** Compose state for the "VPN permission needed" explanation dialog. */
    private var showVpnPermissionDialog by mutableStateOf(false)

    /** Compose state for the battery-optimization explanation dialog. */
    private var showBatteryDialog by mutableStateOf(false)

    /** Compose state for the DoH enforcement warning dialog. */
    private var showDohWarningDialog by mutableStateOf(false)
    private var pendingNonDohAction: VpnAction.Connect? = null

    /** Whether the splash screen has finished its animation. */
    private var splashFinished by mutableStateOf(false)

    private val settingsPrefs by lazy {
        getSharedPreferences(SettingsViewModel.PREFS_NAME, MODE_PRIVATE)
    }

    private val isEnforceDohEnabled: Boolean
        get() = settingsPrefs.getBoolean(SettingsViewModel.KEY_ENFORCE_DOH, false)

    /** The user turned down the system VPN dialog and hasn't granted it since. */
    private var vpnPermissionDeclined: Boolean
        get() = settingsPrefs.getBoolean(KEY_VPN_PERMISSION_DECLINED, false)
        set(value) {
            if (value != vpnPermissionDeclined) {
                settingsPrefs.edit().putBoolean(KEY_VPN_PERMISSION_DECLINED, value).apply()
            }
        }

    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)

    /** Prevents the dialog from reappearing after the user has already responded this session. */
    private var batteryDialogDismissedThisSession = false

    private val batteryPrefs by lazy {
        getSharedPreferences("parda_battery_prefs", MODE_PRIVATE)
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        Log.d(TAG, "VPN permission result: resultCode=${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            val action = pendingConnectAction
            Log.i(TAG, "VPN permission GRANTED — starting service for ${action?.provider?.name}")
            vpnPermissionDeclined = false
            action?.let { startVpnService(it) }
        } else {
            // Permission denied — explain why it's needed and offer a retry instead of re-asking.
            Log.w(TAG, "VPN permission DENIED — showing explanation")
            vpnPermissionDeclined = true
            if (pendingConnectAction != null) showVpnPermissionDialog = true
            else viewModel.onVpnDisconnected()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Read theme off the main thread — SYSTEM is the correct visual default until prefs load.
        lifecycleScope.launch(Dispatchers.IO) {
            val mode = themeRepository.getThemeMode()
            withContext(Dispatchers.Main) { themeMode = mode }
            // Re-arm the schedule alarm (cheap; covers app updates and cleared alarms).
            ProtectionScheduler.reschedule(this@MainActivity)
        }

        // Observe one-shot disconnect commands from the ViewModel.
        // Connect actions are handled synchronously below — not through the flow.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pendingAction.collect { action ->
                    action ?: return@collect
                    if (action is VpnAction.Disconnect) stopVpnService()
                    viewModel.consumeAction()
                }
            }
        }

        setContent {
            PardaDNSTheme(themeMode = themeMode) {
                if (!splashFinished) {
                    SplashScreen(onFinished = { splashFinished = true })
                    return@PardaDNSTheme
                }

                val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
                val providers by viewModel.providers.collectAsStateWithLifecycle()
                val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
                val latency by viewModel.latency.collectAsStateWithLifecycle()
                val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                val trafficStats by viewModel.trafficStats.collectAsStateWithLifecycle()
                val availableProviders by viewModel.availableProviders.collectAsStateWithLifecycle()

                // Pro: connect to the last provider when the app opens.
                LaunchedEffect(providers.isNotEmpty()) {
                    if (providers.isNotEmpty()) maybeAutoConnect(providers)
                }

                // Trigger battery-optimization dialog 10 s after VPN connects.
                val isConnected = vpnState is VpnState.Connected
                LaunchedEffect(isConnected) {
                    if (isConnected && shouldShowBatteryDialog()) {
                        delay(10_000L)
                        showBatteryDialog = true
                    }
                }

                if (showBatteryDialog) {
                    BatteryOptimizationDialog(
                        onConfirm = { dontAskAgain ->
                            showBatteryDialog = false
                            batteryDialogDismissedThisSession = true
                            if (dontAskAgain) persistDontAskAgain()
                            launchBatteryOptimizationSettings()
                        },
                        onDismiss = { dontAskAgain ->
                            showBatteryDialog = false
                            batteryDialogDismissedThisSession = true
                            if (dontAskAgain) persistDontAskAgain()
                        },
                    )
                }

                if (showDohWarningDialog && pendingNonDohAction != null) {
                    DohWarningDialog(
                        providerName = pendingNonDohAction!!.provider.name,
                        onConnectAnyway = {
                            showDohWarningDialog = false
                            pendingNonDohAction?.let { requestVpnPermission(it) }
                            pendingNonDohAction = null
                        },
                        onCancel = {
                            showDohWarningDialog = false
                            // Revert the Connecting state back to Disconnected
                            viewModel.onVpnDisconnected()
                            pendingNonDohAction = null
                        },
                    )
                }

                if (showVpnPermissionDialog && pendingConnectAction != null) {
                    VpnPermissionDialog(
                        onRetry = {
                            showVpnPermissionDialog = false
                            pendingConnectAction?.let { launchVpnPermission(it) }
                        },
                        onDismiss = {
                            showVpnPermissionDialog = false
                            // Revert the Connecting state; asked again on the next connect tap.
                            viewModel.onVpnDisconnected()
                            pendingConnectAction = null
                        },
                    )
                }

                AppNavHost(
                    vpnState         = vpnState,
                    providers        = providers,
                    selectedProvider = selectedProvider,
                    latency          = latency,
                    isRefreshing     = isRefreshing,
                    trafficStats     = trafficStats,
                    availableProviders = availableProviders,
                    themeMode        = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        themeRepository.setThemeMode(mode)
                    },
                    onSelectProvider = { provider ->
                        Log.d(TAG, "Provider tapped: ${provider.name}")
                        val action = viewModel.selectAndDetermineAction(provider)
                        Log.d(TAG, "Action determined: $action")
                        if (action is VpnAction.Connect) {
                            if (isEnforceDohEnabled && provider.dohUrl == null) {
                                Log.w(TAG, "Enforce DoH is ON but ${provider.name} has no DoH — showing warning")
                                pendingNonDohAction = action
                                showDohWarningDialog = true
                            } else {
                                requestVpnPermission(action)
                            }
                        }
                    },
                    onRefresh = { viewModel.measureLatencies() },
                    onAddProvider = { provider ->
                        Log.d(TAG, "Adding custom provider: ${provider.name}")
                        viewModel.addProvider(provider)
                    },
                    onRemoveProvider = { id ->
                        Log.d(TAG, "Hiding provider: $id")
                        viewModel.hideProvider(id)
                    },
                    onUnhideProvider = { id ->
                        Log.d(TAG, "Unhiding provider: $id")
                        viewModel.unhideProvider(id)
                    },
                    blocklistRepository = blocklistRepository,
                    proRepository = proRepository,
                    billingManager = billingManager,
                    onPause = { minutes -> sendServiceCommand(PardaVpnService.ACTION_PAUSE) {
                        putExtra(PardaVpnService.EXTRA_PAUSE_MINUTES, minutes)
                    } },
                    onResume = { sendServiceCommand(PardaVpnService.ACTION_RESUME) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up purchases made elsewhere, refunds, and Remote Config changes.
        billingManager.refresh()
        remoteConfig.fetchAndActivate()
    }

    // ── VPN lifecycle helpers ────────────────────────────────────────────────

    private fun requestVpnPermission(action: VpnAction.Connect) {
        if (vpnPermissionDeclined && VpnService.prepare(this) != null) {
            // Declined last time: explain first; "Try again" opens the system dialog.
            pendingConnectAction = action
            showVpnPermissionDialog = true
        } else {
            launchVpnPermission(action)
        }
    }

    /** Shows the system VPN dialog when needed, otherwise connects straight away. */
    private fun launchVpnPermission(action: VpnAction.Connect) {
        val prepareIntent = VpnService.prepare(this)
        Log.d(TAG, "VpnService.prepare() returned: ${if (prepareIntent != null) "non-null (needs permission dialog)" else "null (already granted)"}")
        if (prepareIntent != null) {
            // System needs explicit user approval first.
            pendingConnectAction = action
            Log.i(TAG, "Launching VPN permission dialog for ${action.provider.name}")
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            // Already approved (perhaps from system settings) — connect immediately.
            vpnPermissionDeclined = false
            startVpnService(action)
        }
    }

    private fun startVpnService(action: VpnAction.Connect) {
        Log.i(TAG, "startVpnService: primary=${action.provider.primaryIp} secondary=${action.provider.secondaryIp}")
        val intent = Intent(this, PardaVpnService::class.java).apply {
            this.action = PardaVpnService.ACTION_CONNECT
            putExtra(PardaVpnService.EXTRA_DNS_PRIMARY, action.provider.primaryIp)
            putExtra(PardaVpnService.EXTRA_DNS_SECONDARY, action.provider.secondaryIp)
            putExtra(PardaVpnService.EXTRA_DNS_IPV6, action.provider.primaryIpV6)
            putExtra(PardaVpnService.EXTRA_DNS_DOH_URL, action.provider.dohUrl)
            putExtra(PardaVpnService.EXTRA_PROVIDER_NAME, action.provider.name)
        }
        startForegroundService(intent)
        // State is now updated by MainViewModel collecting VpnEventBus events emitted
        // by the service when the first real DNS response arrives (or on 10 s timeout).
    }

    /** Pause / resume commands — the app is in the foreground, so a plain startService is allowed. */
    private inline fun sendServiceCommand(action: String, extras: Intent.() -> Unit = {}) {
        startService(Intent(this, PardaVpnService::class.java).setAction(action).apply(extras))
    }

    private fun maybeAutoConnect(providers: List<DnsProvider>) {
        if (autoConnectAttempted) return
        autoConnectAttempted = true
        if (!proRepository.isPro.value || !automationRepository.connectOnLaunch.value) return
        // Already protected (or paused on purpose) — leave it alone.
        val last = eventBus.events.replayCache.firstOrNull()
        if (last is VpnEvent.Connected || last is VpnEvent.Paused) return
        if (viewModel.vpnState.value !is VpnState.Disconnected) return
        val schedule = automationRepository.schedule.value
        if (schedule.enabled && !schedule.isActiveAt(LocalDateTime.now())) return
        // Never pop the consent dialog on launch; auto-connect only once VPN access is granted.
        if (VpnService.prepare(this) != null) return

        val lastName = getSharedPreferences(PardaTileService.TILE_PREFS_NAME, MODE_PRIVATE)
            .getString(PardaTileService.KEY_NAME, null)
        val provider = providers.firstOrNull { it.name == lastName } ?: providers.first()
        if (isEnforceDohEnabled && provider.dohUrl == null) return
        Log.i(TAG, "Auto-connect on launch → ${provider.name}")
        val action = viewModel.selectAndDetermineAction(provider)
        if (action is VpnAction.Connect) startVpnService(action)
    }

    private fun stopVpnService() {
        Log.i(TAG, "stopVpnService called")
        val intent = Intent(this, PardaVpnService::class.java).apply {
            action = PardaVpnService.ACTION_DISCONNECT
        }
        startService(intent)
    }

    /** True if the app is NOT yet exempt from battery optimizations. */
    private fun needsBatteryExemption(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(packageName)
    }

    /** Checks all conditions: not already exempt, not dismissed this session, not "don't ask again". */
    private fun shouldShowBatteryDialog(): Boolean {
        if (!needsBatteryExemption()) return false
        if (batteryDialogDismissedThisSession) return false
        if (batteryPrefs.getBoolean(KEY_DONT_ASK_BATTERY, false)) return false
        return true
    }

    private fun persistDontAskAgain() {
        batteryPrefs.edit().putBoolean(KEY_DONT_ASK_BATTERY, true).apply()
    }

    /** Launches the system dialog to request Doze exemption. */
    @android.annotation.SuppressLint("BatteryLife")
    private fun launchBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not launch battery optimization dialog", e)
        }
    }

    companion object {
        private const val TAG = "PardaDNS/Activity"
        /** Auto-connect-on-launch runs at most once per process (survives rotation). */
        private var autoConnectAttempted = false
        private const val KEY_DONT_ASK_BATTERY = "dont_ask_battery_opt"
        private const val KEY_VPN_PERMISSION_DECLINED = "vpn_permission_declined"
    }
}