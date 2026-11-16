package com.example.pardadns.data.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BlocklistEngineTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun h(domain: String) = DomainHash.hash(domain)

    private fun compile(text: String): HashIndex {
        val out = tmp.newFile()
        BlocklistParser.compile(text.byteInputStream(), out)
        return HashIndex.open(out)
    }

    @Test fun `parses hosts, plain, wildcard and adblock formats`() {
        assertEquals(h("ads.example.com"), BlocklistParser.parseLine("0.0.0.0 ads.example.com"))
        assertEquals(h("ads.example.com"), BlocklistParser.parseLine("127.0.0.1\tads.example.com  # comment"))
        assertEquals(h("ads.example.com"), BlocklistParser.parseLine("ads.example.com"))
        assertEquals(h("ads.example.com"), BlocklistParser.parseLine("*.ads.example.com"))
        assertEquals(h("ads.example.com"), BlocklistParser.parseLine("||ads.example.com^"))
        assertEquals(h("ads.example.com"), BlocklistParser.parseLine("||ads.example.com^\$important"))
        assertEquals(h("ads.example.com"), BlocklistParser.parseLine("  ADS.Example.COM.  "))
    }

    @Test fun `skips comments, exceptions, local names, ips and rules with modifiers`() {
        for (line in listOf(
            "", "# comment", "! adblock comment", "[Adblock Plus 2.0]",
            "@@||allowed.example.com^", "||tracker.com^\$third-party", "/regex[0-9]+/",
            "127.0.0.1 localhost", "127.0.0.1 localhost.localdomain", "::1 ip6-localhost",
            "0.0.0.0 0.0.0.0", "255.255.255.255 broadcasthost", "example.com/path", "com",
            "notanip ads.example.com",
        )) {
            assertEquals("should skip: $line", 0L, BlocklistParser.parseLine(line))
        }
    }

    @Test fun `compiles to a sorted unique set and blocks subdomains but not parents or tlds`() {
        val index = compile(
            """
            # header
            0.0.0.0 doubleclick.net
            doubleclick.net
            ||tracker.example.org^
            127.0.0.1 localhost
            """.trimIndent(),
        )
        assertEquals(2, index.size)
        assertTrue(index.containsDomainOrParent("doubleclick.net"))
        assertTrue(index.containsDomainOrParent("ad.g.doubleclick.net"))
        assertTrue(index.containsDomainOrParent("tracker.example.org."))
        assertFalse(index.containsDomainOrParent("example.org"))      // parent of a listed domain
        assertFalse(index.containsDomainOrParent("net"))              // bare TLD never matches
        assertFalse(index.containsDomainOrParent("notdoubleclick.net"))
    }

    @Test fun `merge produces the deduplicated union`() {
        val a = tmp.newFile().also { BlocklistParser.compile("a.com\nb.com\nc.com".byteInputStream(), it) }
        val b = tmp.newFile().also { BlocklistParser.compile("b.com\nd.com".byteInputStream(), it) }
        val merged = tmp.newFile()
        assertEquals(4, HashIndex.merge(listOf(a, b), merged))
        val index = HashIndex.open(merged)
        for (d in listOf("a.com", "b.com", "c.com", "d.com")) assertTrue(d, index.containsDomainOrParent(d))
        assertFalse(index.containsDomainOrParent("e.com"))
        for (i in 1 until index.size) assertTrue("sorted", index.at(i - 1) < index.at(i))
    }

    @Test fun `entry cap truncates instead of growing unbounded`() {
        val text = (1..50_000).joinToString("\n") { "host$it.example.com" }
        val out = tmp.newFile()
        val result = BlocklistParser.compile(text.byteInputStream(), out, maxEntries = 20_000)
        assertTrue(result.truncated)
        assertTrue(result.entries <= 20_000)
    }

    @Test fun `empty file yields empty index`() {
        assertEquals(0, compile("# nothing here\n").size)
        assertFalse(HashIndex.EMPTY.containsDomainOrParent("example.com"))
    }
}
