package com.example.pardadns.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pardadns.data.model.DnsProvider
import com.example.pardadns.data.repository.DnsRepository
import com.example.pardadns.domain.event.VpnEvent
import com.example.pardadns.domain.event.VpnEventBus
import com.example.pardadns.domain.model.VpnState
import com.example.pardadns.domain.model.VpnAction
import com.example.pardadns.domain.usecase.ToggleVpnUseCase
import com.example.pardadns.service.TrafficMonitor
import com.example.pardadns.service.TrafficSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject

private const val TAG = "PardaDNS/ViewModel"

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: DnsRepository,
    private val toggleVpnUseCase: ToggleVpnUseCase,
    private val eventBus: VpnEventBus,
    private val trafficMonitor: TrafficMonitor,
) : ViewModel() {

    private val _providers = MutableStateFlow<List<DnsProvider>>(emptyList())
    val providers: StateFlow<List<DnsProvider>> = _providers.asStateFlow()

    private val _availableProviders = MutableStateFlow<List<DnsProvider>>(emptyList())
    val availableProviders: StateFlow<List<DnsProvider>> = _availableProviders.asStateFlow()

    fun addProvider(provider: DnsProvider) {
        repository.addCustomProvider(provider)
        refreshProviderLists()
        measureLatencies()
    }

    fun removeProvider(id: String) {
        repository.removeCustomProvider(id)
        refreshProviderLists()
    }

    /** Remove a provider from the homepage — it becomes available on the custom page. */
    fun hideProvider(id: String) {
        repository.hideProvider(id)
        refreshProviderLists()
    }

    /** Move a provider from the custom page back to the homepage. */
    fun unhideProvider(id: String) {
        repository.unhideProvider(id)
        refreshProviderLists()
        measureLatencies()
    }

    private fun refreshProviderLists() {
        _providers.value = repository.getProviders()
        _availableProviders.value = repository.getAvailableProviders()
    }

    private val _vpnState = MutableStateFlow<VpnState>(VpnState.Disconnected)
    val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

    private val _selectedProvider = MutableStateFlow<DnsProvider?>(null)
    val selectedProvider: StateFlow<DnsProvider?> = _selectedProvider.asStateFlow()

    /** One-shot action consumed by MainActivity to start/stop the real service. */
    private val _pendingAction = MutableStateFlow<VpnAction?>(null)
    val pendingAction: StateFlow<VpnAction?> = _pendingAction.asStateFlow()

    /** Latency in ms per provider id, null = not yet measured / unreachable. */
    private val _latency = MutableStateFlow<Map<String, Long?>>(emptyMap())
    val latency: StateFlow<Map<String, Long?>> = _latency.asStateFlow()

    /** True while a latency refresh is in progress. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Live traffic stats — polls the atomic counters every 500 ms while subscribed. */
    val trafficStats: StateFlow<TrafficSnapshot> = trafficMonitor.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrafficSnapshot.EMPTY)

    init {
        // Load providers off the main thread — avoids synchronous SharedPrefs + JSON parse at VM creation.
        viewModelScope.launch(Dispatchers.IO) {
            refreshProviderLists()
            _selectedProvider.value = _providers.value.firstOrNull()
        }

        // React to Connected / Error signals emitted by the VPN service.
        viewModelScope.launch {
            eventBus.events.collect { event ->
                when (event) {
                    is VpnEvent.Connected -> {
                        Log.i(TAG, "VpnEvent.Connected: provider=${event.providerName}")
                        // Restore focus to the connected provider (handles replay on re-open).
                        _selectedProvider.value = _providers.value.firstOrNull { it.name == event.providerName }
                        onVpnConnected(event.providerName)
                    }
                    VpnEvent.Disconnected -> {
                        Log.i(TAG, "VpnEvent.Disconnected")
                        _selectedProvider.value = null
                        onVpnDisconnected()
                    }
                    is VpnEvent.Error     -> {
                        Log.w(TAG, "VpnEvent.Error: ${event.message}")
                        _selectedProvider.value = null
                        onVpnError(event.message)
                    }
                    is VpnEvent.Paused    -> {
                        Log.i(TAG, "VpnEvent.Paused until ${event.untilMillis}")
                        // Keep focus on the paused provider so tapping it resumes.
                        _selectedProvider.value = _providers.value.firstOrNull { it.name == event.providerName }
                        _vpnState.value = VpnState.Paused(event.providerName, event.untilMillis, event.bySchedule)
                    }
                }
            }
        }
    }

    /**
     * Called by the Activity directly on the main thread (synchronously from a user tap)
     * so that VpnService.prepare() / startActivity runs within the user-gesture window.
     * Returns the action the Activity should execute immediately.
     */
    fun selectAndDetermineAction(provider: DnsProvider): VpnAction {
        val action = toggleVpnUseCase(_vpnState.value, provider)
        if (action is VpnAction.Disconnect) {
            Log.i(TAG, "vpnState: ${_vpnState.value::class.simpleName} → Disconnected")
            _vpnState.value = VpnState.Disconnected
            _selectedProvider.value = null          // remove focus on disconnect
            _pendingAction.value = action
        } else {
            Log.i(TAG, "vpnState: ${_vpnState.value::class.simpleName} → Connecting(${provider.name})")
            _selectedProvider.value = provider      // apply focus only when connecting
            _vpnState.value = VpnState.Connecting(provider.name)
        }
        return action
    }

    fun onVpnConnected(providerName: String) {
        _vpnState.value = VpnState.Connected(providerName)
    }

    fun onVpnDisconnected() {
        _vpnState.value = VpnState.Disconnected
    }

    fun onVpnError(message: String) {
        _vpnState.value = VpnState.Error(message)
    }

    fun consumeAction() {
        _pendingAction.value = null
    }

    /** Pings each provider independently in the background — updates the map as results arrive. */
    fun measureLatencies() {
        _isRefreshing.value = true
        viewModelScope.launch {
            val jobs = _providers.value.map { provider ->
                launch(Dispatchers.IO) {
                    val avg = measureProvider(provider.primaryIp)
                    _latency.update { current ->
                        current + (provider.id to avg)
                    }
                }
            }
            jobs.forEach { it.join() }
            _isRefreshing.value = false
        }
    }

    /**
     * Measures latency to a DNS server with outlier-resistant averaging.
     *
     * 1. Single socket + InetAddress resolution up-front (avoids per-ping overhead).
     * 2. Warm-up ping (discarded) — absorbs ARP + DNS cache miss cost.
     * 3. 5 sequential measurement pings (sequential avoids radio contention on mobile).
     * 4. Drop fastest and slowest (trimmed mean of middle 3).
     * 5. Falls back to average for 2 samples, or direct value for 1.
     *
     * Returns null only when every single ping fails.
     */
    private suspend fun measureProvider(ip: String): Long? = withContext(Dispatchers.IO) {
        // Minimal valid DNS query: ID=0x0001, flags=RD, QDCOUNT=1, question "a" A IN
        val query = byteArrayOf(
            0x00, 0x01,             // ID
            0x01, 0x00,             // Flags: recursion desired
            0x00, 0x01,             // QDCOUNT
            0x00, 0x00, 0x00, 0x00, // ANCOUNT, NSCOUNT
            0x00, 0x00,             // ARCOUNT
            0x01, 0x61.toByte(),    // label "a"
            0x00,                   // root label
            0x00, 0x01,             // QTYPE A
            0x00, 0x01,             // QCLASS IN
        )

        var socket: DatagramSocket? = null
        return@withContext try {
            // Resolve IP and create socket ONCE — avoids ~100ms overhead per ping
            val addr = InetAddress.getByName(ip)
            socket = DatagramSocket().apply { soTimeout = 2_000 }
            val sendPacket = DatagramPacket(query, query.size, addr, 53)
            val recvPacket = DatagramPacket(ByteArray(64), 64)

            // Inline ping lambda reusing the socket
            fun ping(): Long? = try {
                val t0 = System.nanoTime()
                socket!!.send(sendPacket)
                socket!!.receive(recvPacket)
                val elapsedMs = (System.nanoTime() - t0) / 1_000_000
                if (elapsedMs in 1..1500) elapsedMs else null
            } catch (_: Exception) { null }

            // Warm-up — discard the result
            ping()

            // Sequential measurement pings
            val samples = buildList {
                repeat(5) { ping()?.let { add(it) } }
            }.sorted()

            when {
                samples.isEmpty() -> null
                samples.size == 1 -> samples[0]
                samples.size == 2 -> ((samples[0] + samples[1]) / 2)
                else -> {
                    // Trim: drop lowest and highest, average the rest
                    val trimmed = samples.subList(1, samples.size - 1)
                    trimmed.average().toLong()
                }
            }
        } catch (_: Exception) { null }
        finally { socket?.close() }
    }
}
