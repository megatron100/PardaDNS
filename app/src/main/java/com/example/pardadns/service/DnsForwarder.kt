package com.example.pardadns.service

import android.util.Log
import com.example.pardadns.domain.event.VpnEvent
import com.example.pardadns.domain.event.VpnEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val TAG              = "PardaDNS/Service"
private const val DNS_PORT         = 53
private const val DNS_TIMEOUT_MS   = 5_000
private const val RESPONSE_BUFSIZE = 4_096
private const val MAX_FAILURES     = 5
private const val WATCHDOG_MS      = 10_000L
private const val SILENCE_MS       = 30_000L

/**
 * Handles DNS query forwarding for a single tunnel session.
 *
 * Responsibilities:
 * - DoH-first forwarding with plain-UDP fallback
 * - 10-second watchdog that fires if no query succeeds at start-up
 * - Consecutive-failure tracking that calls [onFatalError] after [MAX_FAILURES] errors
 * - Emitting [VpnEvent.Connected] on the first successful round-trip
 *
 * Call [reset] at the start of each new session, then [forward] for each DNS query,
 * then [onQuerySuccess] after the response is successfully written back to the TUN fd.
 */
internal class DnsForwarder(
    /** VpnService.protect — keeps forwarding sockets off the TUN interface. */
    private val protect: (DatagramSocket) -> Boolean,
    private val eventBus: VpnEventBus,
    /** Called when the watchdog fires or consecutive failures exceed the threshold. */
    private val onFatalError: () -> Unit,
) {
    private val connectedSignalled  = AtomicBoolean(false)
    private val fatalFired          = AtomicBoolean(false)
    private val consecutiveFailures = AtomicInteger(0)
    @Volatile private var lastSuccessTime = 0L
    private var providerName        = ""
    private var timeoutJob: Job?    = null

    /**
     * Maps DoH hostnames to their known IPs so OkHttp never needs a real DNS
     * lookup (which would loop through our own TUN).  Updated in [reset].
     */
    private val bootstrapDns = object : Dns {
        @Volatile var mapping: Map<String, List<InetAddress>> = emptyMap()
        override fun lookup(hostname: String): List<InetAddress> =
            mapping[hostname] ?: Dns.SYSTEM.lookup(hostname)
    }

    /** Shared HTTP client — connection pool is reused across all DoH queries. */
    private val okHttpClient = OkHttpClient.Builder()
        .dns(bootstrapDns)
        .connectTimeout(DNS_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(DNS_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .build()

    /** True after the first successful DNS round-trip for the current session. */
    val isConnected: Boolean get() = connectedSignalled.get()

    /**
     * Resets per-session state and arms the watchdog timer in [scope].
     * Must be called before the DNS loop begins for each new connection.
     */
    fun reset(newProviderName: String, scope: CoroutineScope, dohUrl: String?, bootstrapIp: String?, secondaryBootstrapIp: String? = null) {
        connectedSignalled.set(false)
        fatalFired.set(false)
        consecutiveFailures.set(0)
        lastSuccessTime = 0L
        providerName = newProviderName

        // Map the DoH hostname to the provider's known IPs so OkHttp skips DNS.
        // Including both primary and secondary ensures DoH works even if the primary is unreachable.
        if (dohUrl != null && bootstrapIp != null) {
            val host = java.net.URL(dohUrl).host
            val ips = buildList {
                add(InetAddress.getByName(bootstrapIp))
                if (secondaryBootstrapIp != null && secondaryBootstrapIp != bootstrapIp) {
                    add(InetAddress.getByName(secondaryBootstrapIp))
                }
            }
            bootstrapDns.mapping = mapOf(host to ips)
        } else {
            bootstrapDns.mapping = emptyMap()
        }

        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(WATCHDOG_MS)
            if (!connectedSignalled.get()) {
                Log.w(TAG, "No DNS response within ${WATCHDOG_MS / 1_000} s — emitting error")
                eventBus.emit(VpnEvent.Error("Unable to connect."))
                onFatalError()
            }
        }
    }

    /** Cancels the watchdog timer. Call on intentional disconnects. */
    fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    /**
     * Forwards [query] to [upstreamDns] — DoH first if [dohUrl] is non-null, then
     * plain UDP fallback. Returns the raw DNS response bytes, or null on failure.
     */
    suspend fun forward(query: ByteArray, upstreamDns: String, secondaryDns: String, dohUrl: String?): ByteArray? =
        withContext(Dispatchers.IO) {
            if (dohUrl != null) {
                val result = forwardDoH(query, dohUrl)
                if (result != null) return@withContext result
                Log.d(TAG, "DoH failed, falling back to UDP for $upstreamDns")
            }
            val primary = forwardUdp(query, upstreamDns)
            if (primary != null) return@withContext primary
            if (secondaryDns != upstreamDns) {
                Log.d(TAG, "Primary UDP failed, trying secondary $secondaryDns")
                forwardUdp(query, secondaryDns)
            } else null
        }

    /**
     * Must be called after a DNS response is successfully written back into the TUN fd.
     * Resets the consecutive-failure counter and emits [VpnEvent.Connected] on the first call.
     */
    fun onQuerySuccess() {
        consecutiveFailures.set(0)
        lastSuccessTime = System.currentTimeMillis()
        if (connectedSignalled.compareAndSet(false, true)) {
            timeoutJob?.cancel()
            Log.i(TAG, "First DNS response received — Connected($providerName)")
            eventBus.emit(VpnEvent.Connected(providerName))
        }
    }

    // ── Private: DNS-over-HTTPS ───────────────────────────────────────────────

    private fun forwardDoH(query: ByteArray, dohUrl: String): ByteArray? {
        val body = query.toRequestBody("application/dns-message".toMediaType())
        val request = Request.Builder()
            .url(dohUrl)
            .post(body)
            .header("Accept", "application/dns-message")
            .build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "DoH HTTP ${response.code}")
                    return null
                }
                response.body?.bytes()
            }
        } catch (e: Exception) {
            Log.w(TAG, "DoH error: ${e.message}")
            null
        }
    }

    // ── Private: plain UDP ────────────────────────────────────────────────────

    private fun forwardUdp(query: ByteArray, upstreamDns: String): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            if (!protect(socket)) {
                Log.e(TAG, "Unable to protect UDP socket from VPN tunnel")
                return null
            }
            socket.soTimeout = DNS_TIMEOUT_MS
            val addr = InetAddress.getByName(upstreamDns)
            socket.send(DatagramPacket(query, query.size, addr, DNS_PORT))
            val buf = ByteArray(RESPONSE_BUFSIZE)
            val rp  = DatagramPacket(buf, buf.size)
            socket.receive(rp)
            buf.copyOf(rp.length)
        } catch (e: Exception) {
            Log.w(TAG, "UDP DNS failed ($upstreamDns): ${e.message}")
            // Track consecutive failures only once the tunnel is fully established —
            // the watchdog covers the pre-connection window.
            if (connectedSignalled.get()) {
                val failures = consecutiveFailures.incrementAndGet()
                val silentFor = System.currentTimeMillis() - lastSuccessTime
                if (failures >= MAX_FAILURES && silentFor >= SILENCE_MS
                    && fatalFired.compareAndSet(false, true)) {
                    Log.e(TAG, "$failures consecutive failures, silent for ${silentFor / 1_000}s — closing tunnel")
                    eventBus.emit(VpnEvent.Error("DNS not responding."))
                    onFatalError()
                }
            }
            null
        } finally {
            socket?.close()
        }
    }
}
