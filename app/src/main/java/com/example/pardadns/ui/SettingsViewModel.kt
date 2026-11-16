package com.example.pardadns.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pardadns.data.model.AppInfo
import com.example.pardadns.data.repository.AppFilterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appFilterRepository: AppFilterRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _excluded = MutableStateFlow<Set<String>>(emptySet())
    val excluded: StateFlow<Set<String>> = _excluded.asStateFlow()

    private val _enforceDoh = MutableStateFlow(prefs.getBoolean(KEY_ENFORCE_DOH, false))
    val enforceDoh: StateFlow<Boolean> = _enforceDoh.asStateFlow()

    private val _dnsMonitoring = MutableStateFlow(prefs.getBoolean(KEY_DNS_MONITORING, false))
    val dnsMonitoring: StateFlow<Boolean> = _dnsMonitoring.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _excluded.value = appFilterRepository.getExcludedPackages()
            _apps.value = appFilterRepository.getInstalledApps()
        }
    }

    fun toggleExclusion(packageName: String) {
        val isNowExcluded = packageName !in _excluded.value
        appFilterRepository.setExcluded(packageName, isNowExcluded)
        _excluded.value = appFilterRepository.getExcludedPackages()
    }

    fun setEnforceDoh(enabled: Boolean) {
        _enforceDoh.value = enabled
        prefs.edit().putBoolean(KEY_ENFORCE_DOH, enabled).apply()
    }

    fun setDnsMonitoring(enabled: Boolean) {
        _dnsMonitoring.value = enabled
        prefs.edit().putBoolean(KEY_DNS_MONITORING, enabled).apply()
    }

    companion object {
        const val PREFS_NAME = "parda_settings"
        const val KEY_ENFORCE_DOH = "enforce_doh"
        const val KEY_DNS_MONITORING = "dns_monitoring"
    }
}
