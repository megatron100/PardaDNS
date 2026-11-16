package com.example.pardadns.data.filter

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Streams a blocklist line by line and compiles it into a [HashIndex] file.
 *
 * Understands the common formats so ready-made lists and user imports both work:
 * - hosts files: `0.0.0.0 ads.example.com` / `127.0.0.1\tads.example.com`
 * - plain domains: `ads.example.com`
 * - wildcards: `*.ads.example.com`
 * - AdBlock-style DNS rules: `||ads.example.com^` (exceptions and cosmetic rules are skipped)
 *
 * Memory use is one growable LongArray (8 bytes per domain), never the raw text.
 */
internal object BlocklistParser {

    /** Hard cap per list so a huge or bogus file can't exhaust a low-end device. */
    const val MAX_ENTRIES = 1_000_000
    private const val MAX_BYTES = 64L * 1024 * 1024

    class Result(val entries: Int, val truncated: Boolean)

    fun compile(input: InputStream, out: File, maxEntries: Int = MAX_ENTRIES): Result {
        var values = LongArray(16_384)
        var n = 0
        var truncated = false
        LimitedInputStream(input, MAX_BYTES).bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val h = parseLine(line)
                if (h == 0L) continue
                if (n == values.size) {
                    if (n >= maxEntries) { truncated = true; break }
                    values = values.copyOf(minOf(n * 2, maxEntries))
                }
                values[n++] = h
            }
        }
        java.util.Arrays.sort(values, 0, n)
        var unique = 0
        for (i in 0 until n) {
            if (unique == 0 || values[i] != values[unique - 1]) values[unique++] = values[i]
        }
        HashIndex.write(values, unique, out)
        return Result(unique, truncated)
    }

    /** Returns the domain hash for a rule line, or 0 when the line isn't a blockable domain. */
    fun parseLine(line: String): Long {
        var s = 0
        var e = line.length
        while (s < e && line[s].isWhitespace()) s++
        while (e > s && line[e - 1].isWhitespace()) e--
        if (s >= e) return 0
        when (line[s]) { '#', '!', '[' -> return 0 }
        if (line.startsWith("@@", s)) return 0

        if (line.startsWith("||", s)) {
            s += 2
            val caret = line.indexOf('^', s)
            if (caret < 0 || caret >= e) return 0
            val rest = e - (caret + 1)
            if (rest != 0 && !(rest == IMPORTANT.length && line.startsWith(IMPORTANT, caret + 1))) return 0
            e = caret
        } else {
            val comment = line.indexOf('#', s)
            if (comment in s until e) {
                e = comment
                while (e > s && line[e - 1].isWhitespace()) e--
            }
            val ws = indexOfWhitespace(line, s, e)
            if (ws >= 0) {
                // hosts format: first token must be an IP, the domain is the second token.
                if (!looksLikeIp(line, s, ws)) return 0
                s = ws
                while (s < e && line[s].isWhitespace()) s++
                val next = indexOfWhitespace(line, s, e)
                if (next >= 0) e = next
            }
        }

        if (line.startsWith("*.", s)) s += 2
        while (s < e && line[s] == '.') s++
        if (e > s && line[e - 1] == '.') e--
        if (!isValidDomain(line, s, e)) return 0
        return DomainHash.hash(line, s, e)
    }

    private const val IMPORTANT = "\$important"

    private val LOCAL_NAMES = setOf("localhost.localdomain", "local", "broadcasthost", "ip6-localhost", "ip6-loopback")

    private fun indexOfWhitespace(s: String, from: Int, to: Int): Int {
        for (i in from until to) if (s[i] == ' ' || s[i] == '\t') return i
        return -1
    }

    private fun looksLikeIp(s: String, from: Int, to: Int): Boolean {
        var hasColon = false
        for (i in from until to) {
            val c = s[i]
            if (c == ':') hasColon = true
            else if (!(c.isDigit() || c == '.' || (hasColon && (c in 'a'..'f' || c in 'A'..'F' || c == '%')))) return false
        }
        return true
    }

    private fun isValidDomain(s: String, from: Int, to: Int): Boolean {
        val len = to - from
        if (len < 3 || len > 253) return false
        var hasDot = false
        var allNumeric = true
        for (i in from until to) {
            val c = s[i]
            when {
                c == '.' -> hasDot = true
                c in 'a'..'z' || c in 'A'..'Z' || c == '-' || c == '_' -> allNumeric = false
                c.isDigit() -> Unit
                else -> return false
            }
        }
        if (!hasDot || allNumeric) return false   // bare labels and IP literals
        // Only allocate for the rare lines that could be a local hostname.
        return when (s[from]) {
            'l', 'L', 'b', 'B', 'i', 'I' -> s.substring(from, to).lowercase() !in LOCAL_NAMES
            else -> true
        }
    }

    /** Fails the download instead of silently reading an endless or enormous stream. */
    private class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L
        override fun read(): Int {
            val b = super.read()
            if (b >= 0) check(1)
            return b
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) check(n.toLong())
            return n
        }
        private fun check(n: Long) {
            count += n
            if (count > limit) throw IOException("List is larger than ${limit / (1024 * 1024)} MB")
        }
    }
}
