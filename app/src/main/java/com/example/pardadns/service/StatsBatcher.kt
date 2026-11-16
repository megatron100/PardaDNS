package com.example.pardadns.service

import android.util.Log
import com.example.pardadns.data.repository.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

private const val TAG = "PardaDNS/Batcher"
private const val FLUSH_INTERVAL_MS = 3_000L

/**
 * Accumulates DNS query stat records in an in-memory queue and flushes
 * them to the Room database in a single burst every [FLUSH_INTERVAL_MS].
 *
 * **Hot path:** [enqueue] is lock-free (ConcurrentLinkedQueue.add) and zero-allocation
 * beyond the [PendingRecord] itself — safe to call from the VPN packet loop.
 *
 * **Flush:** A single coroutine drains the queue and issues Room transactions on IO.
 * This collapses hundreds of individual per-query transactions into one batch.
 *
 * Call [start] to begin the periodic flush loop, [stop] to drain remaining records
 * and cancel the loop.
 */
internal class StatsBatcher(
    private val statsRepository: StatsRepository,
) {
    private val queue = ConcurrentLinkedQueue<PendingRecord>()
    private var flushJob: Job? = null

    /** Start the periodic flush loop in the given scope. */
    fun start(scope: CoroutineScope) {
        flushJob?.cancel()
        flushJob = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    /** Stop the flush loop and drain any remaining records. */
    fun stop(scope: CoroutineScope) {
        flushJob?.cancel()
        flushJob = null
        // Final drain — don't lose data on disconnect
        scope.launch(Dispatchers.IO) { flush() }
    }

    /**
     * Enqueue a stat record for deferred persistence.
     * Lock-free, called from the VPN packet processing coroutines.
     */
    fun enqueue(record: PendingRecord) {
        queue.add(record)
    }

    /** Drain the queue and write all pending records in one pass. */
    private suspend fun flush() {
        val batch = mutableListOf<PendingRecord>()
        while (true) {
            val record = queue.poll() ?: break
            batch.add(record)
        }
        if (batch.isEmpty()) return

        Log.d(TAG, "Flushing ${batch.size} records")
        try {
            for (record in batch) {
                if (record.hasLatency) {
                    statsRepository.recordQueryWithLatency(
                        domain = record.domain,
                        packageName = record.packageName,
                        blocked = record.blocked,
                        dateKey = record.dateKey,
                        hourKey = record.hourKey,
                        latencyMs = record.latencyMs,
                        providerName = record.providerName,
                        queryType = record.queryType,
                    )
                } else {
                    statsRepository.recordQuery(
                        domain = record.domain,
                        packageName = record.packageName,
                        blocked = record.blocked,
                        dateKey = record.dateKey,
                        hourKey = record.hourKey,
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Flush failed: ${e.message}")
            // Records are already dequeued — acceptable loss for analytics data.
        }
    }
}

/**
 * Lightweight record queued for batch persistence.
 * One instance per DNS query that passes the monitoring gate.
 */
internal data class PendingRecord(
    val domain: String,
    val packageName: String?,
    val blocked: Boolean,
    val dateKey: String,
    val hourKey: String,
    val latencyMs: Int = 0,
    val providerName: String = "",
    val queryType: String = "A",
    val hasLatency: Boolean = false,
)
