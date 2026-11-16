package com.example.pardadns.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.SharedPreferences
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.text.format.DateFormat
import android.net.ConnectivityManager
import android.net.InetAddresses
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.annotation.SuppressLint
import android.util.Log
import com.example.pardadns.MainActivity
import com.example.pardadns.R
import com.example.pardadns.data.repository.AppFilterRepository
import com.example.pardadns.data.repository.BlocklistRepository
import com.example.pardadns.data.repository.FilterListRepository
import com.example.pardadns.data.repository.ProRepository
import com.example.pardadns.data.repository.RemoteConfigRepository
import com.example.pardadns.data.repository.StatsRepository
import com.example.pardadns.domain.event.VpnEvent
import com.example.pardadns.domain.event.VpnEventBus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date


/**
 * System-wide DNS tunnel using VpnService.
 *
 * Strategy: the TUN interface advertises its own virtual addresses (10.88.0.2 / fd88::2)
 * as the DNS servers for the device. Only routes for those addresses are added, so all
 * normal (non-DNS) traffic continues to flow untouched through the real network.
 *
 * When the OS sends a DNS query it is routed into the TUN fd. A coroutine loop reads
 * every packet, extracts the DNS payload, forwards it upstream to the real DNS server
 * via [DnsForwarder] (DoH/UDP forwarding with watchdog), then writes the response back
 * as a synthesised IP/UDP packet built by [DnsPacketUtils] into the TUN fd so the OS
 * delivers it to the requesting app.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface VpnServiceEntryPoint {
    fun vpnEventBus(): VpnEventBus
    fun statsRepository(): StatsRepository
    fun trafficMonitor(): TrafficMonitor
    fun blocklistRepository(): BlocklistRepository
    fun remoteConfigRepository(): RemoteConfigRepository
    fun proRepository(): ProRepository
    fun billingManager(): com.example.pardadns.billing.BillingManager
    fun filterListRepository(): FilterListRepository
    // Also used by the debug-only test hooks in testing/debug-hooks.
    fun automationRepository(): com.example.pardadns.data.repository.AutomationRepository
    fun dnsRepository(): com.example.pardadns.data.repository.DnsRepository
}

class PardaVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    /** Scope that survives individual connect/disconnect cycles; cancelled only in onDestroy. */
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** The main read loop for the current tunnel session. */
    private var forwarderJob: Job? = null

    /**
     * Partial wake lock held for the lifetime of the tunnel.
     * Prevents the CPU from sleeping so the coroutine read loop keeps running
     * even when the screen is off or the app is in the background.
     */
    private var wakeLock: PowerManager.WakeLock? = null

    /** Serialises concurrent writes back into the TUN fd. */
    private val writeMutex = Mutex()

    /** Serialises connect/disconnect transitions across callbacks and intents. */
    private val tunnelMutex = Mutex()

    /** Lazy so applicationContext is available (set after onCreate). */
    private val eventBus: VpnEventBus by lazy {
        EntryPointAccessors
            .fromApplication(applicationContext, VpnServiceEntryPoint::class.java)
            .vpnEventBus()
    }

    private val statsRepository: StatsRepository by lazy {
        EntryPointAccessors
            .fromApplication(applicationContext, VpnServiceEntryPoint::class.java)
            .statsRepository()
    }

    private val trafficMonitor: TrafficMonitor by lazy {
        EntryPointAccessors
            .fromApplication(applicationContext, VpnServiceEntryPoint::class.java)
            .trafficMonitor()
    }

    private val blocklistRepository: BlocklistRepository by lazy {
        EntryPointAccessors
            .fromApplication(applicationContext, VpnServiceEntryPoint::class.java)
            .blocklistRepository()
    }

    private val proRepository: ProRepository by lazy {
        EntryPointAccessors
            .fromApplication(applicationContext, VpnServiceEntryPoint::class.java)
            .proRepository()
    }

    private val filterListRepository: FilterListRepository by lazy {
        EntryPointAccessors
            .fromApplication(applicationContext, VpnServiceEntryPoint::class.java)
            .filterListRepository()
    }

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    /** Last-used provider; survives intentional disconnects (shared with the QS tile). */
    private val lastProviderPrefs: SharedPreferences by lazy {
        getSharedPreferences(PardaTileService.TILE_PREFS_NAME, MODE_PRIVATE)
    }

    /** Resumes protection when a pause ends while the process is awake (alarm is the backup). */
    private var pauseJob: Job? = null

    /** Debuggable builds log one line per query (tag PardaDNS/Query) so tests can assert on blocking. */
    private val logQueries: Boolean by lazy {
        applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    /** Handles DNS forwarding, watchdog timer, and Connected event signalling. */
    private val forwarder: DnsForwarder by lazy {
        DnsForwarder(
            protect      = ::protect,
            eventBus     = eventBus,
            onFatalError = {
                startService(Intent(this, PardaVpnService::class.java).apply {
                    action = ACTION_DISCONNECT
                    putExtra(EXTRA_NOTIFY_DISCONNECTED, false)
                })
            },
        )
    }

    /** Batches DB writes — flushes every 3 seconds instead of per-query transactions. */
    private val statsBatcher: StatsBatcher by lazy { StatsBatcher(statsRepository) }

    /**
     * Cached value of the dns_monitoring SharedPrefs flag.
     * Read once at connect() time; avoids per-query SharedPrefs lookup.
     */
    @Volatile private var dnsMonitoringEnabled = false

    /** Reconnects when the active network changes (wifi ↔ mobile). */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val primary = prefs.getString(PREF_PRIMARY, null) ?: return
            if (forwarder.isConnected) {
                Log.i(TAG, "Network changed — reconnecting")
                requestConnect(
                    primaryDns   = primary,
                    secondaryDns = prefs.getString(PREF_SECONDARY, primary)!!,
                    primaryDnsV6 = prefs.getString(PREF_IPV6, null),
                    dohUrl       = prefs.getString(PREF_DOH, null),
                    providerName = prefs.getString(PREF_NAME, "Unknown")!!,
                )
            }
        }
    }

    companion object {
        const val ACTION_CONNECT    = "com.example.pardadns.CONNECT"
        const val ACTION_DISCONNECT = "com.example.pardadns.DISCONNECT"
        const val EXTRA_DNS_PRIMARY   = "extra_dns_primary"
        const val EXTRA_DNS_SECONDARY = "extra_dns_secondary"
        const val EXTRA_DNS_IPV6      = "extra_dns_ipv6"
        const val EXTRA_DNS_DOH_URL   = "extra_dns_doh_url"
        const val EXTRA_PROVIDER_NAME = "extra_provider_name"
        const val EXTRA_NOTIFY_DISCONNECTED = "extra_notify_disconnected"
        /** Pause: tear the tunnel down but stay foreground until the pause ends. */
        const val ACTION_PAUSE  = "com.example.pardadns.PAUSE"
        /** Resume a pause, or reconnect to the last provider (schedule start / boot). */
        const val ACTION_RESUME = "com.example.pardadns.RESUME"
        const val EXTRA_PAUSE_MINUTES     = "extra_pause_minutes"
        const val EXTRA_PAUSE_UNTIL       = "extra_pause_until"
        const val EXTRA_PAUSE_BY_SCHEDULE = "extra_pause_by_schedule"
        private const val PREF_PAUSED_UNTIL       = "paused_until"
        private const val PREF_PAUSED_BY_SCHEDULE = "paused_by_schedule"
        private const val REQ_NOTIF_PAUSE  = 3001
        private const val REQ_NOTIF_RESUME = 3002
        private const val CHANNEL_ID = "parda_dns_vpn"
        private const val NOTIF_ID   = 1001
        private const val TAG        = "PardaDNS/Service"
        private const val QUERY_TAG  = "PardaDNS/Query"
        private const val PREFS_NAME  = "parda_vpn_prefs"
        private const val PREF_PRIMARY   = "last_primary"
        private const val PREF_SECONDARY = "last_secondary"
        private const val PREF_IPV6      = "last_ipv6"
        private const val PREF_DOH       = "last_doh"
        private const val PREF_NAME      = "last_name"

        // TUN interface's own addresses (these are NEVER the DNS server addresses —
        // traffic to the interface's own IP is treated as local and never enters the fd).
        private const val TUN_ADDR_IPV4        = "10.88.0.2"
        private const val TUN_ADDR_IPV4_PREFIX = 32
        private const val TUN_ADDR_IPV6        = "fd88::2"
        private const val TUN_ADDR_IPV6_PREFIX = 128

        // Virtual DNS proxy addresses — DIFFERENT from the TUN's own address.
        // The OS is told to send DNS here; addRoute routes them into the TUN fd;
        // our read loop then sees those packets and can forward them upstream.
        private const val DNS_PROXY_IPV4        = "10.88.0.1"
        private const val DNS_PROXY_IPV4_PREFIX = 32
        private const val DNS_PROXY_IPV6        = "fd88::1"
        private const val DNS_PROXY_IPV6_PREFIX = 128

        private const val DNS_PORT       = 53
        private const val PACKET_BUFSIZE = 32_767
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notif_channel_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        return when (intent?.action) {
            ACTION_CONNECT -> {
                val primary      = intent.getStringExtra(EXTRA_DNS_PRIMARY) ?: "1.1.1.1"
                val secondary    = intent.getStringExtra(EXTRA_DNS_SECONDARY) ?: "1.0.0.1"
                val ipv6         = intent.getStringExtra(EXTRA_DNS_IPV6)
                val dohUrl       = intent.getStringExtra(EXTRA_DNS_DOH_URL)
                val providerName = intent.getStringExtra(EXTRA_PROVIDER_NAME) ?: "Unknown"
                requestConnect(primary, secondary, ipv6, dohUrl, providerName)
                START_STICKY
            }
            ACTION_DISCONNECT -> {
                serviceScope.launch {
                    tunnelMutex.withLock {
                        disconnect(
                            notifyDisconnected = intent.getBooleanExtra(EXTRA_NOTIFY_DISCONNECTED, true),
                        )
                    }
                }
                START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                val until = intent.getLongExtra(EXTRA_PAUSE_UNTIL, 0L).takeIf { it > 0L }
                    ?: (System.currentTimeMillis() + intent.getIntExtra(EXTRA_PAUSE_MINUTES, 15) * 60_000L)
                val bySchedule = intent.getBooleanExtra(EXTRA_PAUSE_BY_SCHEDULE, false)
                serviceScope.launch { tunnelMutex.withLock { pause(until, bySchedule) } }
                START_STICKY
            }
            ACTION_RESUME -> {
                // May arrive via startForegroundService() from an alarm — satisfy that
                // contract synchronously before the async reconnect.
                val name = prefs.getString(PREF_NAME, null) ?: lastProviderPrefs.getString(PardaTileService.KEY_NAME, null)
                startForegroundCompat(buildNotification(name ?: getString(R.string.app_name)))
                serviceScope.launch { tunnelMutex.withLock { resume() } }
                START_STICKY
            }
            else -> {
                // null intent = Android restarted the service after killing it (START_STICKY).
                // Restore an active pause, otherwise re-connect using the last persisted provider.
                val primary = prefs.getString(PREF_PRIMARY, null)
                val pausedUntil = prefs.getLong(PREF_PAUSED_UNTIL, 0L)
                if (primary != null && pausedUntil > System.currentTimeMillis()) {
                    Log.i(TAG, "START_STICKY restart — restoring pause")
                    serviceScope.launch {
                        tunnelMutex.withLock { pause(pausedUntil, prefs.getBoolean(PREF_PAUSED_BY_SCHEDULE, false)) }
                    }
                    START_STICKY
                } else if (primary != null) {
                    Log.i(TAG, "START_STICKY restart — reconnecting to saved provider")
                    requestConnect(
                        primaryDns   = primary,
                        secondaryDns = prefs.getString(PREF_SECONDARY, primary)!!,
                        primaryDnsV6 = prefs.getString(PREF_IPV6, null),
                        dohUrl       = prefs.getString(PREF_DOH, null),
                        providerName = prefs.getString(PREF_NAME, "Unknown")!!,
                    )
                    START_STICKY
                } else {
                    START_NOT_STICKY
                }
            }
        }
    }

    private fun requestConnect(
        primaryDns: String,
        secondaryDns: String,
        primaryDnsV6: String?,
        dohUrl: String?,
        providerName: String,
    ) {
        serviceScope.launch {
            tunnelMutex.withLock {
                connect(primaryDns, secondaryDns, primaryDnsV6, dohUrl, providerName)
            }
        }
    }

    private fun connect(primaryDns: String, secondaryDns: String, primaryDnsV6: String?, dohUrl: String?, providerName: String) {
        Log.i(TAG, "connect: provider=$providerName primary=$primaryDns doh=${dohUrl ?: "none"}")

        // Any (re)connect ends a pause.
        cancelPauseTimers()

        // Persist so we can reconnect after a process restart or network change.
        prefs.edit()
            .putString(PREF_PRIMARY, primaryDns)
            .putString(PREF_SECONDARY, secondaryDns)
            .putString(PREF_IPV6, primaryDnsV6)
            .putString(PREF_DOH, dohUrl)
            .putString(PREF_NAME, providerName)
            .remove(PREF_PAUSED_UNTIL)
            .remove(PREF_PAUSED_BY_SCHEDULE)
            .apply()
        // Remember the provider beyond disconnects — used by the QS tile, auto-connect and schedules.
        lastProviderPrefs.edit()
            .putString(PardaTileService.KEY_PRIMARY, primaryDns)
            .putString(PardaTileService.KEY_SECONDARY, secondaryDns)
            .putString(PardaTileService.KEY_IPV6, primaryDnsV6)
            .putString(PardaTileService.KEY_DOH, dohUrl)
            .putString(PardaTileService.KEY_NAME, providerName)
            .apply()
        startForegroundCompat(buildNotification(providerName))

        // Acquire wake lock to keep the CPU running while the tunnel is active.
        if (wakeLock?.isHeld == true) wakeLock?.release()
        @SuppressLint("WakelockTimeout")   // intentionally indefinite — released in disconnect()
        val wl = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PardaDNS:TunnelWakeLock")
        wl.acquire()
        wakeLock = wl

        // Cancel any running forwarder and close the old interface before re-establishing.
        forwarderJob?.cancel()
        vpnInterface?.close()

        // Read the user's excluded-app list via the repository's shared constants.
        val excludedPackages = getSharedPreferences(AppFilterRepository.PREFS_NAME, MODE_PRIVATE)
            .getStringSet(AppFilterRepository.KEY_EXCLUDED, emptySet()) ?: emptySet()

        val builder = Builder()
            .setSession("Panda DNS")
            // TUN interface's own addresses
            .addAddress(TUN_ADDR_IPV4, TUN_ADDR_IPV4_PREFIX)
            .addAddress(TUN_ADDR_IPV6, TUN_ADDR_IPV6_PREFIX)
            // Route ONLY the DNS proxy virtual addresses through the TUN fd.
            // These are DIFFERENT from the interface addresses above, so the kernel
            // does NOT treat them as local — the packets enter the TUN fd as required.
            .addRoute(DNS_PROXY_IPV4, DNS_PROXY_IPV4_PREFIX)
            .addRoute(DNS_PROXY_IPV6, DNS_PROXY_IPV6_PREFIX)
            // Tell the OS to use the proxy virtual addresses for DNS.
            // With the routes above, every DNS query will flow into the TUN fd.
            .addDnsServer(DNS_PROXY_IPV4)
            .addDnsServer(DNS_PROXY_IPV6)
            .setConfigureIntent(buildConfigureIntent())

        // Always exclude ourselves so our own DoH HttpURLConnection doesn't loop.
        builder.addDisallowedApplication(packageName)
        // Exclude any apps the user has toggled off.
        for (pkg in excludedPackages) {
            runCatching { builder.addDisallowedApplication(pkg) }
                .onFailure { Log.w(TAG, "Could not exclude $pkg: ${it.message}") }
        }
        if (excludedPackages.isNotEmpty()) {
            Log.i(TAG, "App filter: excluding ${excludedPackages.size} app(s)")
        }

        vpnInterface = builder.establish()

        Log.i(TAG, "TUN interface established: ${vpnInterface?.fileDescriptor}")
        val tun = vpnInterface
        if (tun == null) {
            Log.e(TAG, "establish() returned null — another VPN app may be active")
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            eventBus.emit(VpnEvent.Error("Unable to connect. Another VPN may be active."))
            return
        }

        // Reset per-session in-memory traffic counters.
        trafficMonitor.resetSession()

        // Cache the monitoring flag once — avoids SharedPrefs lock on every query.
        dnsMonitoringEnabled = getSharedPreferences("parda_settings", MODE_PRIVATE)
            .getBoolean("dns_monitoring", false)

        // Start the batch writer (flushes accumulated stats every 3 seconds).
        statsBatcher.start(serviceScope)

        // Arm the watchdog and reset per-session state via the forwarder.
        forwarder.reset(providerName, serviceScope, dohUrl, primaryDns, secondaryDns)

        // Register network callback so we reconnect on wifi ↔ mobile switches.
        val cm = getSystemService(ConnectivityManager::class.java)
        runCatching { cm.unregisterNetworkCallback(networkCallback) }  // no-op if not registered
        cm.registerDefaultNetworkCallback(networkCallback)

        forwarderJob = serviceScope.launch {
            runDnsLoop(tun, primaryDns, secondaryDns, primaryDnsV6, dohUrl)
        }
    }

    private fun disconnect(notifyDisconnected: Boolean = true) {
        Log.i(TAG, "disconnect")
        cancelPauseTimers()
        tearDownTunnel()
        prefs.edit().clear().apply()   // forget saved provider (and any pause) on intentional disconnect
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        if (notifyDisconnected) {
            eventBus.emit(VpnEvent.Disconnected)
        }
        // Do NOT call stopSelf() here.  Keeping the service alive (non-foreground)
        // avoids a race where the OS hasn't fully torn down the old VPN session
        // when the user quickly reconnects (<1 s).  The next connect() reuses this
        // instance (hot-switch path), which always succeeds.  The OS will reclaim
        // the idle service naturally once memory pressure rises.
    }

    /** Stops the forwarding loop and closes the TUN fd — shared by disconnect and pause. */
    private fun tearDownTunnel() {
        // Drain any remaining stats records before tearing down.
        statsBatcher.stop(serviceScope)
        trafficMonitor.flush()
        runCatching {
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        }
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        forwarder.cancelTimeout()
        forwarderJob?.cancel()
        forwarderJob = null
        vpnInterface?.close()
        vpnInterface = null
    }

    /**
     * Temporarily turns protection off: DNS falls back to the network's resolver, but the
     * service stays in the foreground (no wake lock) so it can reliably resume at [until].
     * Extends an existing pause rather than shortening it.
     */
    private fun pause(until: Long, bySchedule: Boolean) {
        val providerName = prefs.getString(PREF_NAME, null) ?: return   // nothing to pause
        val currentUntil = prefs.getLong(PREF_PAUSED_UNTIL, 0L)
        if (vpnInterface == null && currentUntil == 0L) return            // not connected
        val effectiveUntil = maxOf(until, currentUntil)
        Log.i(TAG, "pause until $effectiveUntil (bySchedule=$bySchedule)")

        tearDownTunnel()
        prefs.edit()
            .putLong(PREF_PAUSED_UNTIL, effectiveUntil)
            .putBoolean(PREF_PAUSED_BY_SCHEDULE, bySchedule)
            .apply()
        startForegroundCompat(buildPausedNotification(effectiveUntil, bySchedule))

        pauseJob?.cancel()
        pauseJob = serviceScope.launch {
            delay(effectiveUntil - System.currentTimeMillis())
            tunnelMutex.withLock { resume() }
        }
        ProtectionScheduler.armResume(this, effectiveUntil)
        eventBus.emit(VpnEvent.Paused(providerName, effectiveUntil, bySchedule))
    }

    /** Ends a pause, or reconnects to the last provider if protection is fully off. */
    private fun resume() {
        if (vpnInterface != null && prefs.getLong(PREF_PAUSED_UNTIL, 0L) == 0L) return  // already protected
        val primary = prefs.getString(PREF_PRIMARY, null)
        if (primary != null) {
            connect(
                primaryDns   = primary,
                secondaryDns = prefs.getString(PREF_SECONDARY, primary)!!,
                primaryDnsV6 = prefs.getString(PREF_IPV6, null),
                dohUrl       = prefs.getString(PREF_DOH, null),
                providerName = prefs.getString(PREF_NAME, "Unknown")!!,
            )
            return
        }
        val last = lastProviderPrefs.getString(PardaTileService.KEY_PRIMARY, null)
        if (last != null && VpnService.prepare(this) == null) {
            connect(
                primaryDns   = last,
                secondaryDns = lastProviderPrefs.getString(PardaTileService.KEY_SECONDARY, last)!!,
                primaryDnsV6 = lastProviderPrefs.getString(PardaTileService.KEY_IPV6, null),
                dohUrl       = lastProviderPrefs.getString(PardaTileService.KEY_DOH, null),
                providerName = lastProviderPrefs.getString(PardaTileService.KEY_NAME, "Unknown")!!,
            )
            return
        }
        Log.w(TAG, "resume: no provider to reconnect to")
        @Suppress("DEPRECATION")
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun cancelPauseTimers() {
        pauseJob?.cancel()
        pauseJob = null
        ProtectionScheduler.cancelResume(this)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(providerName: String): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text, providerName))
            .setSmallIcon(R.drawable.ic_tile_shield)
            .setContentIntent(buildConfigureIntent())
            .setOngoing(true)
        if (proRepository.isPro.value) {
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_tile_shield),
                    getString(R.string.notif_action_pause),
                    serviceAction(ACTION_PAUSE, REQ_NOTIF_PAUSE) { putExtra(EXTRA_PAUSE_MINUTES, 15) },
                ).build(),
            )
        }
        return builder.build()
    }

    private fun buildPausedNotification(until: Long, bySchedule: Boolean): Notification {
        val time = DateFormat.getTimeFormat(this).format(Date(until))
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(if (bySchedule) R.string.notif_paused_schedule_title else R.string.notif_paused_title))
            .setContentText(getString(R.string.notif_paused_text, time))
            .setSmallIcon(R.drawable.ic_tile_shield)
            .setContentIntent(buildConfigureIntent())
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_tile_shield),
                    getString(R.string.notif_action_resume),
                    serviceAction(ACTION_RESUME, REQ_NOTIF_RESUME),
                ).build(),
            )
            .build()
    }

    private inline fun serviceAction(action: String, requestCode: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, PardaVpnService::class.java).setAction(action).apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun buildConfigureIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        wakeLock?.release()
        wakeLock = null
        serviceScope.cancel()
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }

    override fun onRevoke() {
        Log.w(TAG, "onRevoke")
        serviceScope.launch {
            tunnelMutex.withLock {
                disconnect(notifyDisconnected = true)
            }
        }
    }

    // ── DNS forwarding loop ───────────────────────────────────────────────────

    /**
     * Reads raw IP packets from the TUN fd in a tight loop. DNS packets (IPv4/IPv6 UDP
     * to port 53) are forwarded to [upstreamDns] via a protect()-ed socket and the
     * response is written back as a synthesised IP packet. All other packet types are
     * silently dropped (they won't appear because only the narrow TUN routes are set).
     */
    private suspend fun runDnsLoop(tun: ParcelFileDescriptor, upstreamDns: String, secondaryDns: String, upstreamDnsV6: String?, dohUrl: String?) = coroutineScope {
        val input  = FileInputStream(tun.fileDescriptor)
        val output = FileOutputStream(tun.fileDescriptor)
        val buf    = ByteArray(PACKET_BUFSIZE)
        Log.i(TAG, "DNS forwarding loop started → upstream $upstreamDns")
        try {
            while (true) {
                val len = withContext(Dispatchers.IO) { input.read(buf) }
                if (len < 1) continue
                val version = (buf[0].toInt() and 0xF0) ushr 4
                when (version) {
                    4 -> processIPv4(this, buf, len, output, upstreamDns, secondaryDns, dohUrl)
                    6 -> processIPv6(this, buf, len, output, upstreamDnsV6 ?: upstreamDns, secondaryDns, dohUrl)
                }
            }
        } catch (e: CancellationException) {
            throw e   // let coroutine machinery handle cancellation normally
        } catch (e: java.io.IOException) {
            val msg = e.message ?: ""
            if ("EBADF" in msg || "EINVAL" in msg) {
                Log.d(TAG, "TUN fd closed — stopping read loop")
            } else {
                Log.e(TAG, "TUN read loop crashed", e)
                eventBus.emit(VpnEvent.Error("Unable to connect."))
                startService(Intent(this@PardaVpnService, PardaVpnService::class.java).apply {
                    action = ACTION_DISCONNECT
                    putExtra(EXTRA_NOTIFY_DISCONNECTED, false)
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "TUN read loop crashed", e)
            eventBus.emit(VpnEvent.Error("Unable to connect."))
            startService(Intent(this@PardaVpnService, PardaVpnService::class.java).apply {
                action = ACTION_DISCONNECT
                putExtra(EXTRA_NOTIFY_DISCONNECTED, false)
            })
        }
        Log.i(TAG, "DNS forwarding loop ended")
    }

    private fun processIPv4(
        scope: CoroutineScope,
        pkt: ByteArray, len: Int,
        output: FileOutputStream,
        upstreamDns: String,
        secondaryDns: String,
        dohUrl: String?,
    ) {
        val ihl = (pkt[0].toInt() and 0x0F) * 4
        if (len < ihl + 8) return
        if (pkt[9].toInt() and 0xFF != 17) return              // not UDP
        val dstPort = DnsPacketUtils.readU16(pkt, ihl + 2)
        if (dstPort != DNS_PORT) return

        val srcIp         = pkt.copyOfRange(12, 16)
        val dstIp         = pkt.copyOfRange(16, 20)
        val srcPort       = DnsPacketUtils.readU16(pkt, ihl)
        val udpPayloadLen = DnsPacketUtils.readU16(pkt, ihl + 4) - 8
        if (udpPayloadLen < 1 || ihl + 8 + udpPayloadLen > len) return
        val query = pkt.copyOfRange(ihl + 8, ihl + 8 + udpPayloadLen)

        Log.v(TAG, "DNS/IPv4 query len=$udpPayloadLen srcPort=$srcPort → $upstreamDns")
        // Resolve UID → package synchronously while the socket entry is still in /proc.
        val callerPkg = resolvePackageName(srcIp, srcPort, dstIp, dstPort)
        scope.launch {
            // ── Custom blocklist + Pro filters: local NXDOMAIN without network round-trip ──
            val domain = DnsPacketUtils.extractQueryDomain(query)
            val blocked = domain != null && isLocallyBlocked(domain)
            if (logQueries) Log.d(QUERY_TAG, "${if (blocked) "BLOCKED" else "FORWARD"} $domain")
            if (blocked) {
                val nxResponse = DnsPacketUtils.buildNxdomainResponse(query)
                val nxReply = DnsPacketUtils.buildIPv4UdpPacket(
                    srcIp = dstIp, dstIp = srcIp,
                    srcPort = DNS_PORT, dstPort = srcPort,
                    payload = nxResponse,
                )
                try {
                    writeMutex.withLock { output.write(nxReply) }
                } catch (_: java.io.IOException) { return@launch }
                recordStats(query, nxResponse, callerPkg, latencyMs = 0) // Blocked locally, no latency
                return@launch
            }

            val startTime = System.currentTimeMillis()
            val response = forwarder.forward(query, upstreamDns, secondaryDns, dohUrl) ?: return@launch
            val latencyMs = (System.currentTimeMillis() - startTime).toInt()
            Log.v(TAG, "DNS/IPv4 response len=${response.size} latency=${latencyMs}ms")
            val reply = DnsPacketUtils.buildIPv4UdpPacket(
                srcIp = dstIp, dstIp = srcIp,
                srcPort = DNS_PORT, dstPort = srcPort,
                payload = response,
            )
            try {
                writeMutex.withLock { output.write(reply) }
                forwarder.onQuerySuccess()
            } catch (_: java.io.IOException) {
                // fd was closed (provider switched mid-query); discard stale response silently.
                return@launch
            }
            recordStats(query, response, callerPkg, latencyMs)
        }
    }

    /** Manual blocklist (O(1) hash set) first, then the Pro filter index (mmap binary search). */
    private fun isLocallyBlocked(domain: String): Boolean =
        blocklistRepository.isBlocked(domain) ||
            (proRepository.isPro.value && filterListRepository.isBlocked(domain))

    private fun recordStats(query: ByteArray, response: ByteArray, packageName: String? = null, latencyMs: Int = 0) {
        val blocked = DnsPacketUtils.isBlockedResponse(response)
        trafficMonitor.recordDnsQuery(blocked)

        // Gate detailed DB recording behind the cached monitoring flag.
        if (!dnsMonitoringEnabled) return

        val domain = DnsPacketUtils.extractQueryDomain(query) ?: return
        val now = LocalDateTime.now()
        val providerName = prefs.getString(PREF_NAME, "Unknown") ?: "Unknown"
        statsBatcher.enqueue(
            PendingRecord(
                domain       = domain,
                packageName  = packageName,
                blocked      = blocked,
                dateKey      = now.format(DATE_FMT),
                hourKey      = now.format(HOUR_FMT),
                latencyMs    = latencyMs,
                providerName = providerName,
                hasLatency   = latencyMs > 0,
            )
        )
    }

    /**
     * Resolves the UID that owns the UDP socket at (srcIp:srcPort → dstIp:dstPort)
     * and maps it to a package name. Returns null on API < 29 or if lookup fails.
     */
    private fun resolvePackageName(srcIp: ByteArray, srcPort: Int, dstIp: ByteArray, dstPort: Int): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val cm = getSystemService(ConnectivityManager::class.java)
            val local  = java.net.InetSocketAddress(java.net.InetAddress.getByAddress(srcIp), srcPort)
            val remote = java.net.InetSocketAddress(java.net.InetAddress.getByAddress(dstIp), dstPort)
            val uid = cm.getConnectionOwnerUid(/* protocol = */ 17 /* UDP */, local, remote)
            if (uid < 0) return null
            packageManager.getPackagesForUid(uid)?.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun processIPv6(
        scope: CoroutineScope,
        pkt: ByteArray, len: Int,
        output: FileOutputStream,
        upstreamDns: String,
        secondaryDns: String,
        dohUrl: String?,
    ) {
        if (len < 48) return                                    // 40 IP header + 8 UDP minimum
        if (pkt[6].toInt() and 0xFF != 17) return              // not UDP
        val dstPort = DnsPacketUtils.readU16(pkt, 42)          // 40 (IP) + 2 (src port offset)
        if (dstPort != DNS_PORT) return

        val srcIp         = pkt.copyOfRange(8, 24)
        val dstIp         = pkt.copyOfRange(24, 40)
        val srcPort       = DnsPacketUtils.readU16(pkt, 40)
        val udpPayloadLen = DnsPacketUtils.readU16(pkt, 44) - 8
        if (udpPayloadLen < 1 || 48 + udpPayloadLen > len) return
        val query = pkt.copyOfRange(48, 48 + udpPayloadLen)

        // Resolve UID → package synchronously while the socket entry is still in /proc.
        val callerPkg = resolvePackageName(srcIp, srcPort, dstIp, dstPort)
        scope.launch {
            // ── Custom blocklist + Pro filters: local NXDOMAIN without network round-trip ──
            val domain = DnsPacketUtils.extractQueryDomain(query)
            val blocked = domain != null && isLocallyBlocked(domain)
            if (logQueries) Log.d(QUERY_TAG, "${if (blocked) "BLOCKED" else "FORWARD"} $domain")
            if (blocked) {
                val nxResponse = DnsPacketUtils.buildNxdomainResponse(query)
                val nxReply = DnsPacketUtils.buildIPv6UdpPacket(
                    srcIp = dstIp, dstIp = srcIp,
                    srcPort = DNS_PORT, dstPort = srcPort,
                    payload = nxResponse,
                )
                try {
                    writeMutex.withLock { output.write(nxReply) }
                } catch (_: java.io.IOException) { return@launch }
                recordStats(query, nxResponse, callerPkg, latencyMs = 0) // Blocked locally, no latency
                return@launch
            }

            val startTime = System.currentTimeMillis()
            val response = forwarder.forward(query, upstreamDns, secondaryDns, dohUrl) ?: return@launch
            val latencyMs = (System.currentTimeMillis() - startTime).toInt()
            val reply = DnsPacketUtils.buildIPv6UdpPacket(
                srcIp = dstIp, dstIp = srcIp,
                srcPort = DNS_PORT, dstPort = srcPort,
                payload = response,
            )
            try {
                writeMutex.withLock { output.write(reply) }
                forwarder.onQuerySuccess()
            } catch (_: java.io.IOException) {
                // fd was closed (provider switched mid-query); discard stale response silently.
                return@launch
            }
            recordStats(query, response, callerPkg, latencyMs)
        }
    }
}

