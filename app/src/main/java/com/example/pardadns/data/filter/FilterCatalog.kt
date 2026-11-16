package com.example.pardadns.data.filter

enum class FilterCategory(val title: String, val subtitle: String) {
    ADS("Ads & Trackers", "Banner ads, analytics and tracking"),
    MALWARE("Malware & Phishing", "Scam, phishing and malware domains"),
    ADULT("Adult Content", "Pornography and explicit sites"),
    CUSTOM("Imported Lists", "Lists you added from a URL or file"),
}

/** A ready-made list shipped with the app. [recommended] lists are what a category toggle enables. */
data class CuratedList(
    val id: String,
    val name: String,
    val description: String,
    val category: FilterCategory,
    val url: String,
    val approxEntries: Int,
    val recommended: Boolean = false,
)

object FilterCatalog {
    val curated: List<CuratedList> = listOf(
        // ── Ads & trackers ──────────────────────────────────────────────────
        CuratedList(
            id = "oisd-small",
            name = "OISD Small",
            description = "Blocks most ads & trackers with almost no breakage",
            category = FilterCategory.ADS,
            url = "https://small.oisd.nl/domainswild",
            approxEntries = 56_000,
            recommended = true,
        ),
        CuratedList(
            id = "stevenblack",
            name = "StevenBlack Unified",
            description = "Classic hosts list of ads & malware from 15+ sources",
            category = FilterCategory.ADS,
            url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            approxEntries = 80_000,
        ),
        CuratedList(
            id = "adguard-dns",
            name = "AdGuard DNS Filter",
            description = "AdGuard's ad & tracker list built for DNS blocking",
            category = FilterCategory.ADS,
            url = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
            approxEntries = 187_000,
        ),
        CuratedList(
            id = "oisd-big",
            name = "OISD Big",
            description = "Maximum coverage: ads, trackers, malware & phishing",
            category = FilterCategory.ADS,
            url = "https://big.oisd.nl/domainswild",
            approxEntries = 236_000,
        ),
        // ── Malware & phishing ──────────────────────────────────────────────
        CuratedList(
            id = "urlhaus",
            name = "URLhaus",
            description = "Active malware distribution sites (abuse.ch)",
            category = FilterCategory.MALWARE,
            url = "https://urlhaus.abuse.ch/downloads/hostfile/",
            approxEntries = 500,
            recommended = true,
        ),
        CuratedList(
            id = "phishing-army",
            name = "Phishing Army",
            description = "Phishing and scam domains, updated daily",
            category = FilterCategory.MALWARE,
            url = "https://phishing.army/download/phishing_army_blocklist.txt",
            approxEntries = 158_000,
            recommended = true,
        ),
        // ── Adult content ───────────────────────────────────────────────────
        CuratedList(
            id = "oisd-nsfw-small",
            name = "OISD NSFW Small",
            description = "Most-visited adult sites, light on memory",
            category = FilterCategory.ADULT,
            url = "https://nsfw-small.oisd.nl/domainswild",
            approxEntries = 19_000,
            recommended = true,
        ),
        CuratedList(
            id = "stevenblack-porn",
            name = "StevenBlack Adult",
            description = "Adult-content extension of the StevenBlack hosts",
            category = FilterCategory.ADULT,
            url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts",
            approxEntries = 77_000,
        ),
        CuratedList(
            id = "oisd-nsfw",
            name = "OISD NSFW",
            description = "Comprehensive adult list — largest download",
            category = FilterCategory.ADULT,
            url = "https://nsfw.oisd.nl/domainswild",
            approxEntries = 474_000,
        ),
    )
}
