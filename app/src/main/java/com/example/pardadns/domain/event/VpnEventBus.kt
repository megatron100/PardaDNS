package com.example.pardadns.domain.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class VpnEvent {
    data class Connected(val providerName: String) : VpnEvent()
    data object Disconnected : VpnEvent()
    data class Error(val message: String) : VpnEvent()
    /** Protection temporarily off until [untilMillis]; [bySchedule] = outside schedule hours. */
    data class Paused(val providerName: String, val untilMillis: Long, val bySchedule: Boolean) : VpnEvent()
}

@Singleton
class VpnEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<VpnEvent>(
        replay = 1,
        extraBufferCapacity = 8,
    )
    val events: SharedFlow<VpnEvent> = _events.asSharedFlow()

    fun emit(event: VpnEvent) {
        _events.tryEmit(event)
    }
}
