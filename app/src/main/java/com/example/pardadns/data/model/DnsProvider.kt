package com.example.pardadns.data.model

data class DnsProvider(
    val id: String,
    val name: String,
    val primaryIp: String,
    val secondaryIp: String,
    val description: String,
    /** IPv6 address of the primary upstream, used to forward IPv6 DNS queries. */
    val primaryIpV6: String? = null,
    /** DNS-over-HTTPS endpoint. Queries are sent here first; UDP is used as fallback. */
    val dohUrl: String? = null,
    /** Pro-only providers are listed for everyone but connecting requires Pro. */
    val proOnly: Boolean = false,
)
