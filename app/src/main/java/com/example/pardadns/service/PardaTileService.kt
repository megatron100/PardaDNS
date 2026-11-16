package com.example.pardadns.service

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.example.pardadns.MainActivity
import com.example.pardadns.R
import com.example.pardadns.domain.event.VpnEvent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile that toggles the PardaDNS VPN on/off
 * directly from the notification shade.
 *
 * - If the VPN is active → sends ACTION_DISCONNECT.
 * - If the VPN is inactive and a previous provider is remembered → reconnects.
 * - If no provider has ever been used or VPN permission is missing → opens MainActivity.
 */
class PardaTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var listenerJob: Job? = null

    private val eventBus by lazy {
        EntryPointAccessors
            .fromApplication(applicationContext, VpnServiceEntryPoint::class.java)
            .vpnEventBus()
    }

    /** Tile-specific prefs that remember the last-used provider across disconnects. */
    private val tilePrefs by lazy {
        getSharedPreferences(TILE_PREFS_NAME, MODE_PRIVATE)
    }

    // ── Tile lifecycle ──────────────────────────────────────────────────

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
        listenerJob = scope.launch {
            eventBus.events.collect { updateTile() }
        }
    }

    override fun onStopListening() {
        listenerJob?.cancel()
        listenerJob = null
        super.onStopListening()
    }

    // ── Tap handler ─────────────────────────────────────────────────────

    override fun onClick() {
        super.onClick()

        if (isVpnActive()) {
            Log.d(TAG, "Tile tap → disconnect")
            val intent = Intent(this, PardaVpnService::class.java).apply {
                action = PardaVpnService.ACTION_DISCONNECT
            }
            startService(intent)
            return
        }

        // Attempt to reconnect with the last-known provider.
        val primary = tilePrefs.getString(KEY_PRIMARY, null)
        if (primary == null) {
            Log.d(TAG, "Tile tap → no saved provider, opening app")
            launchApp()
            return
        }

        // VPN permission check — prepare() returns null when already granted.
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            Log.d(TAG, "Tile tap → VPN permission needed, opening app")
            launchApp()
            return
        }

        Log.d(TAG, "Tile tap → connect with saved provider")
        val intent = Intent(this, PardaVpnService::class.java).apply {
            action = PardaVpnService.ACTION_CONNECT
            putExtra(PardaVpnService.EXTRA_DNS_PRIMARY, primary)
            putExtra(PardaVpnService.EXTRA_DNS_SECONDARY, tilePrefs.getString(KEY_SECONDARY, primary))
            putExtra(PardaVpnService.EXTRA_DNS_IPV6, tilePrefs.getString(KEY_IPV6, null))
            putExtra(PardaVpnService.EXTRA_DNS_DOH_URL, tilePrefs.getString(KEY_DOH, null))
            putExtra(PardaVpnService.EXTRA_PROVIDER_NAME, tilePrefs.getString(KEY_NAME, "Unknown"))
        }
        startForegroundService(intent)
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun isVpnActive(): Boolean =
        eventBus.events.replayCache.firstOrNull() is VpnEvent.Connected

    private fun updateTile() {
        val tile = qsTile ?: return
        val active = isVpnActive()
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (active) {
            val name = tilePrefs.getString(KEY_NAME, null)
            name?.let { getString(R.string.tile_subtitle_active, it) }
        } else {
            getString(R.string.tile_subtitle_inactive)
        }
        tile.updateTile()
    }

    private fun launchApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PardaDNS/Tile"

        /** SharedPreferences file that survives VPN disconnect cycles. */
        const val TILE_PREFS_NAME = "parda_tile_prefs"
        const val KEY_PRIMARY   = "tile_primary"
        const val KEY_SECONDARY = "tile_secondary"
        const val KEY_IPV6      = "tile_ipv6"
        const val KEY_DOH       = "tile_doh"
        const val KEY_NAME      = "tile_name"
    }
}
