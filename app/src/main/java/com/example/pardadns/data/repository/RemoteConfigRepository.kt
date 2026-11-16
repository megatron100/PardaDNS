package com.example.pardadns.data.repository

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConfigRepository @Inject constructor() {

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    /** Promo switch: true makes every Pro feature free for everyone. */
    private val _proEnabledForAll = MutableStateFlow(DEFAULTS[KEY_PRO_ENABLED_FOR_ALL] as Boolean)
    val proEnabledForAll: StateFlow<Boolean> = _proEnabledForAll.asStateFlow()

    /** Play Console product ID of the one-time (lifetime) Pro purchase. */
    private val _proProductId = MutableStateFlow(DEFAULTS[KEY_PRO_PRODUCT_ID] as String)
    val proProductId: StateFlow<String> = _proProductId.asStateFlow()

    init {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 // 1 hour in production
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(DEFAULTS)

        // Apply cached values immediately
        applyValues()

        // Fetch fresh values in background
        fetchAndActivate()
    }

    fun fetchAndActivate() {
        remoteConfig.fetchAndActivate().addOnCompleteListener { applyValues() }
    }

    private fun applyValues() {
        _proEnabledForAll.value = remoteConfig.proEnabledForAll()
        _proProductId.value = remoteConfig.getString(KEY_PRO_PRODUCT_ID).trim()
            .ifEmpty { DEFAULTS[KEY_PRO_PRODUCT_ID] as String }
    }

    companion object {
        const val KEY_PRO_ENABLED_FOR_ALL = "pro_enabled_for_all"
        const val KEY_PRO_PRODUCT_ID = "pro_product_id"

        /** In-app defaults, used until (and unless) a fetched value overrides them. */
        val DEFAULTS: Map<String, Any> = mapOf(
            KEY_PRO_ENABLED_FOR_ALL to false,
            KEY_PRO_PRODUCT_ID to "pro_lifetime",
        )

        /**
         * Also works before [RemoteConfigRepository] exists in this process (boot receiver, alarms):
         * activated values are persisted by Firebase, and an unset key falls back to [DEFAULTS].
         */
        fun FirebaseRemoteConfig.proEnabledForAll(): Boolean {
            val v = getValue(KEY_PRO_ENABLED_FOR_ALL)
            return if (v.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) {
                DEFAULTS[KEY_PRO_ENABLED_FOR_ALL] as Boolean
            } else {
                v.asBoolean()
            }
        }
    }
}
