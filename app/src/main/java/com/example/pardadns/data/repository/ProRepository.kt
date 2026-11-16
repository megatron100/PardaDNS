package com.example.pardadns.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import com.example.pardadns.billing.Ownership
import com.example.pardadns.data.repository.RemoteConfigRepository.Companion.proEnabledForAll
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the Pro entitlement.
 *
 * [isPro] combines the last Play purchase result (written by BillingManager and cached in prefs, so
 * it holds offline and right at startup), the Remote Config switches, and — in debug builds only —
 * a manual override used by the Settings switch and the `PRO` debug hook. Rules: [ProEntitlement].
 */
@Singleton
class ProRepository @Inject constructor(
    @ApplicationContext context: Context,
    remoteConfig: RemoteConfigRepository,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val debuggable = isDebuggable(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // The old local "unlock" was never a purchase — drop it.
        if (prefs.contains(LEGACY_KEY_IS_PRO)) prefs.edit().remove(LEGACY_KEY_IS_PRO).apply()
    }

    private val _ownership = MutableStateFlow(readOwnership(prefs))
    /** Last known Play purchase state of the Pro product. */
    val ownership: StateFlow<Ownership> = _ownership.asStateFlow()

    private val _debugOverride = MutableStateFlow(readOverride(prefs))
    val debugOverride: StateFlow<Boolean?> = _debugOverride.asStateFlow()

    val isPro: StateFlow<Boolean> = combine(
        _ownership, _debugOverride, remoteConfig.proEnabledForAll,
    ) { owned, override, forAll ->
        ProEntitlement.isPro(owned, forAll, override, debuggable)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        ProEntitlement.isPro(_ownership.value, remoteConfig.proEnabledForAll.value, _debugOverride.value, debuggable),
    )

    /** Called by BillingManager after a successful Play query. */
    fun setPlayOwnership(ownership: Ownership) {
        _ownership.value = ownership
        prefs.edit().putString(KEY_OWNERSHIP, ownership.name).apply()
    }

    /** Debug builds only: force Pro on/off, or null to use the real result. */
    fun setDebugOverride(on: Boolean?) {
        if (!debuggable) return
        _debugOverride.value = on
        prefs.edit().apply { if (on == null) remove(KEY_DEBUG_OVERRIDE) else putBoolean(KEY_DEBUG_OVERRIDE, on) }.apply()
    }

    companion object {
        const val PREFS_NAME = "parda_pro"
        private const val KEY_OWNERSHIP = "play_ownership"
        private const val KEY_DEBUG_OVERRIDE = "debug_override"
        private const val LEGACY_KEY_IS_PRO = "is_pro"

        private fun isDebuggable(context: Context) =
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

        private fun readOwnership(prefs: SharedPreferences): Ownership =
            prefs.getString(KEY_OWNERSHIP, null)
                ?.let { runCatching { Ownership.valueOf(it) }.getOrNull() } ?: Ownership.NONE

        private fun readOverride(prefs: SharedPreferences): Boolean? =
            if (prefs.contains(KEY_DEBUG_OVERRIDE)) prefs.getBoolean(KEY_DEBUG_OVERRIDE, false) else null

        /** Synchronous read for receivers and alarms that run without Hilt injection. */
        fun isPro(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return ProEntitlement.isPro(
                ownership = readOwnership(prefs),
                enabledForAll = FirebaseRemoteConfig.getInstance().proEnabledForAll(),
                debugOverride = readOverride(prefs),
                debuggable = isDebuggable(context),
            )
        }
    }
}
