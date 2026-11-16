package com.example.pardadns.service

/**
 * Stateless utilities for building raw IPv4/IPv6 UDP DNS response packets.
 *
 * These functions are pure (no side effects, no state) and can be unit-tested
 * independently of the VPN service.
 */
internal object DnsPacketUtils {

    fun readU16(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)

    fun writeU16(buf: ByteArray, off: Int, value: Int) {
        buf[off]     = (value ushr 8).toByte()
        buf[off + 1] = (value and 0xFF).toByte()
    }

    /**
     * Builds a raw IPv4/UDP packet carrying [payload].
     * IP checksum is computed; UDP checksum is left as zero (optional in IPv4 — RFC 768).
     */
    fun buildIPv4UdpPacket(
        srcIp: ByteArray, dstIp: ByteArray,
        srcPort: Int, dstPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val udpLen = 8 + payload.size
        val ipLen  = 20 + udpLen
        val p = ByteArray(ipLen)
        p[0] = 0x45.toByte()                           // version=4, IHL=5 (20 bytes)
        writeU16(p, 2, ipLen)
        p[6] = 0x40.toByte()                           // DF flag
        p[8] = 64                                      // TTL
        p[9] = 17                                      // protocol = UDP
        System.arraycopy(srcIp, 0, p, 12, 4)
        System.arraycopy(dstIp, 0, p, 16, 4)
        writeU16(p, 10, onesComplementSum(p, 0, 20))   // IP header checksum
        writeU16(p, 20, srcPort)
        writeU16(p, 22, dstPort)
        writeU16(p, 24, udpLen)
        // bytes 26-27: UDP checksum — optional for IPv4, left as zero
        System.arraycopy(payload, 0, p, 28, payload.size)
        return p
    }

    /**
     * Builds a raw IPv6/UDP packet carrying [payload].
     * UDP checksum is mandatory in IPv6 (RFC 2460) and is computed via pseudo-header.
     */
    fun buildIPv6UdpPacket(
        srcIp: ByteArray, dstIp: ByteArray,
        srcPort: Int, dstPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val udpLen = 8 + payload.size
        val pktLen = 40 + udpLen
        val p = ByteArray(pktLen)
        p[0] = 0x60.toByte()                           // version=6
        writeU16(p, 4, udpLen)                         // payload length
        p[6] = 17                                      // next header = UDP
        p[7] = 64                                      // hop limit
        System.arraycopy(srcIp, 0, p, 8,  16)
        System.arraycopy(dstIp, 0, p, 24, 16)
        writeU16(p, 40, srcPort)
        writeU16(p, 42, dstPort)
        writeU16(p, 44, udpLen)
        System.arraycopy(payload, 0, p, 48, payload.size)
        writeU16(p, 46, ipv6UdpChecksum(srcIp, dstIp, p, 40, udpLen))
        return p
    }

    // ── DNS query/response helpers ────────────────────────────────────────────

    /**
     * Extracts the queried domain from a raw DNS wire-format message.
     * Returns null if the packet is malformed or too short.
     */
    fun extractQueryDomain(dns: ByteArray): String? {
        // DNS header is 12 bytes; question section starts at offset 12.
        if (dns.size < 13) return null
        val sb = StringBuilder()
        var i = 12
        try {
            while (i < dns.size) {
                val labelLen = dns[i].toInt() and 0xFF
                if (labelLen == 0) break
                if (i + 1 + labelLen > dns.size) return null
                if (sb.isNotEmpty()) sb.append('.')
                sb.append(String(dns, i + 1, labelLen, Charsets.US_ASCII))
                i += 1 + labelLen
            }
        } catch (_: Exception) {
            return null
        }
        return sb.toString().lowercase().ifEmpty { null }
    }

    /**
     * Returns true when the DNS response indicates the domain was blocked.
     *
     * Covers the most common blocking strategies used by DNS-based ad-blockers:
     * - **NXDOMAIN** (RCODE 3) — AdGuard "NXDOMAIN" mode, some NextDNS configs
     * - **REFUSED** (RCODE 5) — Unbound, Knot Resolver
     * - **0.0.0.0 / :: sinkhole** (RCODE 0 with A/AAAA pointing to 0.0.0.0, 127.0.0.1 or ::) —
     *   Pi-hole, NextDNS default, AdGuard "Default" mode
     */
    fun isBlockedResponse(response: ByteArray): Boolean {
        if (response.size < 12) return false
        val rcode = response[3].toInt() and 0x0F
        if (rcode == 3 || rcode == 5) return true   // NXDOMAIN or REFUSED

        // RCODE 0 (NOERROR) — check if the answer is a sinkhole address.
        if (rcode != 0) return false
        val anCount = readU16(response, 6)
        if (anCount == 0) return false  // no answers — legitimate empty response

        // Walk past the question section to reach the first answer RR.
        var off = 12
        val qdCount = readU16(response, 4)
        for (q in 0 until qdCount) {
            while (off < response.size) {
                val b = response[off].toInt() and 0xFF
                if (b == 0) { off += 1 + 4; break }           // end of labels + QTYPE(2) + QCLASS(2)
                if (b and 0xC0 == 0xC0) { off += 2 + 4; break } // compression pointer + QTYPE + QCLASS
                off += 1 + b
            }
        }

        // Parse the first answer RR and check for sinkhole IPs.
        return try {
            // Skip NAME (may be a compression pointer or inline labels).
            val nameEnd = skipName(response, off)
            val rrType = readU16(response, nameEnd)       // TYPE
            val rdLen  = readU16(response, nameEnd + 8)   // RDLENGTH
            val rdOff  = nameEnd + 10                     // RDATA start

            when {
                // A record (type 1), 4 bytes → check 0.0.0.0 or 127.0.0.1
                rrType == 1 && rdLen == 4 && rdOff + 4 <= response.size -> {
                    val ip = response.copyOfRange(rdOff, rdOff + 4)
                    ip.contentEquals(SINKHOLE_V4_ZERO) || ip.contentEquals(SINKHOLE_V4_LOCALHOST)
                }
                // AAAA record (type 28), 16 bytes → check ::
                rrType == 28 && rdLen == 16 && rdOff + 16 <= response.size -> {
                    val ip = response.copyOfRange(rdOff, rdOff + 16)
                    ip.contentEquals(SINKHOLE_V6)
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Skip a DNS name (labels or compression pointer) and return the offset after it. */
    private fun skipName(buf: ByteArray, start: Int): Int {
        var i = start
        while (i < buf.size) {
            val b = buf[i].toInt() and 0xFF
            if (b == 0) return i + 1                          // end of labels
            if (b and 0xC0 == 0xC0) return i + 2              // compression pointer (2 bytes)
            i += 1 + b
        }
        return i
    }

    private val SINKHOLE_V4_ZERO      = byteArrayOf(0, 0, 0, 0)
    private val SINKHOLE_V4_LOCALHOST  = byteArrayOf(127.toByte(), 0, 0, 1)
    private val SINKHOLE_V6           = ByteArray(16)  // all zeros = ::

    // ── Checksum helpers ──────────────────────────────────────────────────────

    /** One's complement sum over [len] bytes of [data] starting at [off]. */
    private fun onesComplementSum(data: ByteArray, off: Int, len: Int): Int {
        var sum = 0
        var i = off
        while (i < off + len - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (len % 2 != 0) sum += (data[off + len - 1].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv() and 0xFFFF
    }

    /**
     * RFC 2460 UDP checksum over the IPv6 pseudo-header + UDP segment.
     * Pseudo-header layout: src(16) + dst(16) + udpLen(4, BE) + zeros(3) + nextHdr(1)
     */
    private fun ipv6UdpChecksum(
        srcIp: ByteArray, dstIp: ByteArray,
        pkt: ByteArray, udpOff: Int, udpLen: Int,
    ): Int {
        val pseudo = ByteArray(40 + udpLen)
        System.arraycopy(srcIp, 0, pseudo, 0,  16)
        System.arraycopy(dstIp, 0, pseudo, 16, 16)
        // 32-bit UDP length at offset 32 — high bytes are zero since udpLen < 65536
        pseudo[34] = (udpLen ushr 8).toByte()
        pseudo[35] = (udpLen and 0xFF).toByte()
        pseudo[39] = 17                                // next header = UDP
        System.arraycopy(pkt, udpOff, pseudo, 40, udpLen)
        return onesComplementSum(pseudo, 0, pseudo.size)
    }

    /**
     * Builds a minimal NXDOMAIN (RCODE 3) DNS response from the original [query].
     *
     * The response copies the query ID, sets QR=1 + RA=1 + RCODE=3, keeps the
     * original question section, and returns zero answer/authority/additional RRs.
     * This is the cheapest possible local block — no heap allocation beyond the
     * final ByteArray since the question section is reused verbatim.
     */
    fun buildNxdomainResponse(query: ByteArray): ByteArray {
        // Just copy the entire query and flip the header bits.
        val resp = query.copyOf()
        // QR=1, Opcode=0, AA=0, TC=0, RD=1 → 0x81
        resp[2] = 0x81.toByte()
        // RA=1, Z=0, RCODE=3 (NXDOMAIN) → 0x83
        resp[3] = 0x83.toByte()
        // ANCOUNT = 0, NSCOUNT = 0, ARCOUNT = 0
        writeU16(resp, 6, 0)
        writeU16(resp, 8, 0)
        writeU16(resp, 10, 0)
        return resp
    }
}
