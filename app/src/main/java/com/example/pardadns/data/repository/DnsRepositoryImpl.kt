package com.example.pardadns.data.repository

import android.content.Context
import com.example.pardadns.data.model.DnsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class DnsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : DnsRepository {

    private val prefs by lazy {
        context.getSharedPreferences("custom_dns_providers", Context.MODE_PRIVATE)
    }

    /** Homepage providers = all known providers minus hidden ones. */
    override fun getProviders(): List<DnsProvider> {
        val hidden = getHiddenIds()
        return getAllKnownProviders().filterNot { it.id in hidden }
    }

    /** Available-to-add providers = hidden ones only. */
    override fun getAvailableProviders(): List<DnsProvider> {
        val hidden = getHiddenIds()
        return getAllKnownProviders().filter { it.id in hidden }
    }

    override fun addCustomProvider(provider: DnsProvider) {
        val current = getCustomProviders().toMutableList()
        current.add(provider)
        saveCustomProviders(current)
    }

    override fun removeCustomProvider(id: String) {
        val current = getCustomProviders().filterNot { it.id == id }
        saveCustomProviders(current)
        // Also remove from hidden set if present.
        val hidden = getHiddenIds().toMutableSet()
        if (hidden.remove(id)) saveHiddenIds(hidden)
    }

    override fun hideProvider(id: String) {
        val hidden = getHiddenIds().toMutableSet()
        hidden.add(id)
        saveHiddenIds(hidden)
    }

    override fun unhideProvider(id: String) {
        val hidden = getHiddenIds().toMutableSet()
        hidden.remove(id)
        saveHiddenIds(hidden)
    }

    // ── Internals ──────────────────────────────────────────────────────────

    /** All providers the app knows about: built-in + presets + user-created. */
    private fun getAllKnownProviders(): List<DnsProvider> {
        val ids = mutableSetOf<String>()
        val result = mutableListOf<DnsProvider>()
        // Built-in first, then presets, then custom — skip duplicates by id.
        for (p in builtInProviders() + presetProviders() + getCustomProviders()) {
            if (ids.add(p.id)) result.add(p)
        }
        return result
    }

    private fun getHiddenIds(): Set<String> {
        if (!prefs.contains(KEY_HIDDEN_IDS)) {
            // First launch: hide all preset providers so only built-in ones show.
            val defaultHidden = presetProviders().map { it.id }.toSet()
            saveHiddenIds(defaultHidden)
            return defaultHidden
        }
        return prefs.getStringSet(KEY_HIDDEN_IDS, emptySet()) ?: emptySet()
    }

    private fun saveHiddenIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN_IDS, ids).apply()
    }

    private fun getCustomProviders(): List<DnsProvider> {
        val json = prefs.getString(KEY_CUSTOM_PROVIDERS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DnsProvider(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    primaryIp = obj.getString("primaryIp"),
                    secondaryIp = obj.optString("secondaryIp", ""),
                    description = obj.optString("description", "Custom provider"),
                    primaryIpV6 = obj.optString("primaryIpV6", null).takeUnless { it.isNullOrEmpty() },
                    dohUrl = obj.optString("dohUrl", null).takeUnless { it.isNullOrEmpty() },
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveCustomProviders(providers: List<DnsProvider>) {
        val arr = JSONArray()
        providers.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("primaryIp", p.primaryIp)
                put("secondaryIp", p.secondaryIp)
                put("description", p.description)
                p.primaryIpV6?.let { put("primaryIpV6", it) }
                p.dohUrl?.let { put("dohUrl", it) }
            })
        }
        prefs.edit().putString(KEY_CUSTOM_PROVIDERS, arr.toString()).apply()
    }

    private fun builtInProviders(): List<DnsProvider> = listOf(
        // ── Primary purpose: ad/tracker blocking ──────────────────────────
        DnsProvider(
            id = "adguard",
            name = "AdGuard",
            primaryIp = "94.140.14.14",
            secondaryIp = "94.140.15.15",
            description = "Blocks ads, trackers & phishing",
            primaryIpV6 = "2a10:50c0::ad1:ff",
            dohUrl = "https://dns.adguard-dns.com/dns-query",
        ),
        // ── Speed + reliability baseline ──────────────────────────────────
        DnsProvider(
            id = "cloudflare",
            name = "Cloudflare",
            primaryIp = "1.1.1.1",
            secondaryIp = "1.0.0.1",
            description = "World's fastest DNS, no filtering",
            primaryIpV6 = "2606:4700:4700::1111",
            dohUrl = "https://cloudflare-dns.com/dns-query",
        ),
        // ── Speed + malware protection ────────────────────────────────────
        DnsProvider(
            id = "cloudflare-security",
            name = "Cloudflare Security",
            primaryIp = "1.1.1.2",
            secondaryIp = "1.0.0.2",
            description = "Cloudflare speed + malware blocking",
            primaryIpV6 = "2606:4700:4700::1112",
            dohUrl = "https://security.cloudflare-dns.com/dns-query",
        ),
        // ── Trusted non-profit security DNS ──────────────────────────────
        DnsProvider(
            id = "quad9",
            name = "Quad9",
            primaryIp = "9.9.9.9",
            secondaryIp = "149.112.112.112",
            description = "Non-profit, blocks malware & phishing",
            primaryIpV6 = "2620:fe::fe",
            dohUrl = "https://dns.quad9.net/dns-query",
        ),
    )

    private fun presetProviders(): List<DnsProvider> = listOf(
        // ── Best-in-class privacy ─────────────────────────────────────────
        DnsProvider(
            id = "mullvad",
            name = "Mullvad DNS",
            primaryIp = "194.242.2.2",
            secondaryIp = "194.242.2.3",
            description = "Zero logs, privacy-first",
            dohUrl = "https://dns.mullvad.net/dns-query",
        ),
        // ── Customizable / power users ────────────────────────────────────
        DnsProvider(
            id = "nextdns",
            name = "NextDNS",
            primaryIp = "45.90.28.0",
            secondaryIp = "45.90.30.0",
            description = "Customizable blocking (needs account)",
            primaryIpV6 = "2a07:a8c0::",
            dohUrl = "https://dns.nextdns.io/dns-query",
        ),
        DnsProvider(
            id = "adguard-family",
            name = "AdGuard Family",
            primaryIp = "94.140.14.15",
            secondaryIp = "94.140.15.16",
            description = "AdGuard — ads + adult content blocking",
            primaryIpV6 = "2a10:50c0::bad1:ff",
            dohUrl = "https://family.adguard-dns.com/dns-query",
            proOnly = true,
        ),
        DnsProvider(
            id = "adguard-unfiltered",
            name = "AdGuard Unfiltered",
            primaryIp = "94.140.14.140",
            secondaryIp = "94.140.14.141",
            description = "AdGuard — no filtering, just speed & privacy",
            primaryIpV6 = "2a10:50c0::1:ff",
            dohUrl = "https://unfiltered.adguard-dns.com/dns-query",
        ),
        DnsProvider(
            id = "cloudflare-family",
            name = "Cloudflare Family",
            primaryIp = "1.1.1.3",
            secondaryIp = "1.0.0.3",
            description = "Cloudflare — malware + adult content blocking",
            primaryIpV6 = "2606:4700:4700::1113",
            dohUrl = "https://family.cloudflare-dns.com/dns-query",
            proOnly = true,
        ),
        DnsProvider(
            id = "mullvad-adblock",
            name = "Mullvad Adblock",
            primaryIp = "194.242.2.2",
            secondaryIp = "194.242.2.3",
            description = "Mullvad — blocks ads & malware",
            dohUrl = "https://base.dns.mullvad.net/dns-query",
            proOnly = true,
        ),
        DnsProvider(
            id = "opendns",
            name = "OpenDNS",
            primaryIp = "208.67.222.222",
            secondaryIp = "208.67.220.220",
            description = "Cisco Umbrella — phishing & malware protection",
            dohUrl = "https://doh.opendns.com/dns-query",
        ),
        DnsProvider(
            id = "opendns-family",
            name = "OpenDNS FamilyShield",
            primaryIp = "208.67.222.123",
            secondaryIp = "208.67.220.123",
            description = "OpenDNS — blocks adult content",
            dohUrl = "https://doh.familyshield.opendns.com/dns-query",
            proOnly = true,
        ),
        DnsProvider(
            id = "cleanbrowsing",
            name = "CleanBrowsing Security",
            primaryIp = "185.228.168.9",
            secondaryIp = "185.228.169.9",
            description = "Blocks phishing, spam & malicious domains",
            dohUrl = "https://doh.cleanbrowsing.org/doh/security-filter/",
        ),
        DnsProvider(
            id = "cleanbrowsing-family",
            name = "CleanBrowsing Family",
            primaryIp = "185.228.168.168",
            secondaryIp = "185.228.169.168",
            description = "Blocks adult, proxy & mixed-content sites",
            dohUrl = "https://doh.cleanbrowsing.org/doh/family-filter/",
            proOnly = true,
        ),
        DnsProvider(
            id = "controld",
            name = "Control D",
            primaryIp = "76.76.2.0",
            secondaryIp = "76.76.10.0",
            description = "Customizable DNS service",
            dohUrl = "https://freedns.controld.com/p0",
        ),
        DnsProvider(
            id = "controld-malware-ads",
            name = "Control D Malware+Ads",
            primaryIp = "76.76.2.2",
            secondaryIp = "76.76.10.0",
            description = "Control D — blocks malware & ads",
            dohUrl = "https://freedns.controld.com/p2",
            proOnly = true,
        ),
        DnsProvider(
            id = "google",
            name = "Google DNS",
            primaryIp = "8.8.8.8",
            secondaryIp = "8.8.4.4",
            description = "Reliable public DNS (Google-operated)",
            primaryIpV6 = "2001:4860:4860::8888",
            dohUrl = "https://dns.google/dns-query",
        ),
        DnsProvider(
            id = "dns-sb",
            name = "DNS.SB",
            primaryIp = "185.222.222.222",
            secondaryIp = "45.11.45.11",
            description = "No-log public DNS with DNSSEC",
            primaryIpV6 = "2a09::",
            dohUrl = "https://doh.dns.sb/dns-query",
        ),
        DnsProvider(
            id = "hurricane-electric",
            name = "Hurricane Electric",
            primaryIp = "74.82.42.42",
            secondaryIp = "74.82.42.42",
            description = "Free anycast DNS by Hurricane Electric",
            primaryIpV6 = "2001:470:20::2",
            dohUrl = "https://ordns.he.net/dns-query",
        ),
        DnsProvider(
            id = "rethinkdns",
            name = "RethinkDNS",
            primaryIp = "45.90.28.0",
            secondaryIp = "45.90.30.0",
            description = "Open-source, configurable blocklists",
            dohUrl = "https://basic.rethinkdns.com/",
        ),
    )

    companion object {
        private const val KEY_CUSTOM_PROVIDERS = "custom_providers_json"
        private const val KEY_HIDDEN_IDS = "hidden_provider_ids"
    }
}
