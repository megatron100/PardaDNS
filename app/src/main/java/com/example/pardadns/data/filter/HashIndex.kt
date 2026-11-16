package com.example.pardadns.data.filter

import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.LongBuffer
import java.nio.channels.FileChannel

/**
 * 64-bit FNV-1a over a char range, ASCII-lowercased on the fly so callers never
 * allocate substrings or lowercase copies. 0 is reserved as "no hash".
 */
internal object DomainHash {
    private const val OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    private const val PRIME = 0x100000001b3L

    fun hash(s: CharSequence, start: Int = 0, end: Int = s.length): Long {
        var h = OFFSET
        for (i in start until end) {
            var c = s[i].code
            if (c in 65..90) c += 32
            h = (h xor c.toLong()) * PRIME
        }
        return if (h == 0L) 1L else h
    }
}

/**
 * A read-only set of domain hashes stored as a sorted array of longs in a file and
 * memory-mapped, so even a 500k-domain list costs ~4 MB of reclaimable page cache and
 * zero Java heap. Lookups are a binary search (~19 probes for 500k entries).
 */
internal class HashIndex private constructor(private val buf: LongBuffer?, val size: Int) {

    fun at(i: Int): Long = buf!!.get(i)

    fun contains(h: Long): Boolean {
        val b = buf ?: return false
        var lo = 0
        var hi = size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = b.get(mid)
            when {
                v < h -> lo = mid + 1
                v > h -> hi = mid - 1
                else -> return true
            }
        }
        return false
    }

    /**
     * True if [domain] or any parent domain (excluding the bare TLD) is in the set,
     * so listing `example.com` also blocks `ads.example.com`.
     */
    fun containsDomainOrParent(domain: String): Boolean {
        if (size == 0) return false
        var end = domain.length
        if (end > 0 && domain[end - 1] == '.') end--
        var start = 0
        while (true) {
            val dot = domain.indexOf('.', start)
            if (dot < 0 || dot >= end) return false
            if (contains(DomainHash.hash(domain, start, end))) return true
            start = dot + 1
        }
    }

    companion object {
        val EMPTY = HashIndex(null, 0)

        fun open(file: File): HashIndex {
            if (!file.exists() || file.length() < 8) return EMPTY
            RandomAccessFile(file, "r").use { raf ->
                val channel = raf.channel
                val length = channel.size() - channel.size() % 8
                // The mapping stays valid after the channel is closed.
                val mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, length)
                return HashIndex(mapped.asLongBuffer(), (length / 8).toInt())
            }
        }

        /** Writes sorted [values] (first [count] entries) to [out] atomically. */
        fun write(values: LongArray, count: Int, out: File) {
            writeAtomically(out) { o -> for (i in 0 until count) o.writeLong(values[i]) }
        }

        /**
         * Streams the sorted, de-duplicated union of [sources] into [out] with a k-way
         * merge over the memory-mapped inputs — no full list is ever loaded on the heap.
         * Returns the number of unique entries written.
         */
        fun merge(sources: List<File>, out: File): Int {
            val parts = sources.map(::open).filter { it.size > 0 }
            val pos = IntArray(parts.size)
            var count = 0
            writeAtomically(out) { o ->
                var hasLast = false
                var last = 0L
                while (true) {
                    var minIndex = -1
                    var minValue = 0L
                    for (i in parts.indices) {
                        if (pos[i] < parts[i].size) {
                            val v = parts[i].at(pos[i])
                            if (minIndex < 0 || v < minValue) { minIndex = i; minValue = v }
                        }
                    }
                    if (minIndex < 0) break
                    pos[minIndex]++
                    if (!hasLast || minValue != last) {
                        o.writeLong(minValue)
                        last = minValue
                        hasLast = true
                        count++
                    }
                }
            }
            return count
        }

        private inline fun writeAtomically(out: File, block: (DataOutputStream) -> Unit) {
            out.parentFile?.mkdirs()
            val tmp = File(out.parentFile, out.name + ".tmp")
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 1 shl 16)).use(block)
            // rename() replaces atomically; readers still holding the old mapping keep the old inode.
            if (!tmp.renameTo(out)) {
                out.delete()
                tmp.renameTo(out)
            }
        }
    }
}
