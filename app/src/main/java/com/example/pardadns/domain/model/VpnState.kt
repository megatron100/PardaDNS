package com.example.pardadns.domain.model

sealed class VpnState {
    data object Disconnected : VpnState()
    data class Connecting(val providerName: String) : VpnState()
    data class Connected(val providerName: String) : VpnState()
    data class Error(val message: String) : VpnState()
    data class Paused(val providerName: String, val untilMillis: Long, val bySchedule: Boolean) : VpnState()
}
