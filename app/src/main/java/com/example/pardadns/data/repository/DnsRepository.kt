package com.example.pardadns.data.repository

import com.example.pardadns.data.model.DnsProvider

interface DnsRepository {
    /** All providers currently shown on the homepage. */
    fun getProviders(): List<DnsProvider>
    /** Providers that have been removed from the homepage (available to re-add). */
    fun getAvailableProviders(): List<DnsProvider>
    fun addCustomProvider(provider: DnsProvider)
    fun removeCustomProvider(id: String)
    /** Hide a provider from the homepage — it moves to the available pool. */
    fun hideProvider(id: String)
    /** Un-hide a provider — it moves back to the homepage. */
    fun unhideProvider(id: String)
}
