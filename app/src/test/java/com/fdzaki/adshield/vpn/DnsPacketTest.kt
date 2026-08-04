package com.fdzaki.adshield.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Covers [DnsPacket.parse] / [DnsPacket.buildBlockedResponse] — per
 * PROJECT_STATE.md this is "paling kritis, paling gampang salah" (the most
 * critical, most error-prone part of the codebase) and had zero test
 * coverage before this file.
 *
 * These tests build synthetic IPv4/UDP/DNS packets by hand (mirroring
 * exactly what the real VpnService tun interface would hand to `parse()`)
 * rather than depending on any real network capture, so they're
 * deterministic and don't need any Android framework or network access.
 */
class DnsPacketTest {

    /** Builds a minimal, valid IPv4/UDP/DNS-query packet asking for [domain]. */
    private fun buildDnsQueryPacket(
        domain: String,
        srcAddr: ByteArray = byteArrayOf(10, 111, 222, 5),
        dstAddr: ByteArray = byteArrayOf(10, 111, 222, 1),
        srcPort: Int = 54321,
        dstPort: Int = 53,
        transactionId: ByteArray = byteArrayOf(0x12, 0x34)
    ): ByteArray {
        // ---- DNS section: header (12 bytes) + QNAME + QTYPE/QCLASS ----
        val labels = domain.split(".")
        var qnameSize = 1 // trailing zero-length label
        for (l in labels) qnameSize += 1 + l.length
        val dns = ByteBuffer.allocate(12 + qnameSize + 4)
        dns.put(transactionId)
        dns.putShort(0x0100) // standard query, recursion desired
        dns.putShort(1); dns.putShort(0); dns.putShort(0); dns.putShort(0) // QD=1, AN/NS/AR=0
        for (label in labels) {
            dns.put(label.length.toByte())
            dns.put(label.toByteArray(Charsets.US_ASCII))
        }
        dns.put(0) // root label
        dns.putShort(1) // QTYPE A
        dns.putShort(1) // QCLASS IN
        val dnsBytes = dns.array()

        // ---- UDP section ----
        val udpLength = 8 + dnsBytes.size
        val udp = ByteBuffer.allocate(udpLength)
        udp.putShort(srcPort.toShort())
        udp.putShort(dstPort.toShort())
        udp.putShort(udpLength.toShort())
        udp.putShort(0) // checksum unchecked by parse()
        udp.put(dnsBytes)
        val udpBytes = udp.array()

        // ---- IPv4 section (20-byte header, no options -> IHL=5) ----
        val totalLength = 20 + udpBytes.size
        val ip = ByteBuffer.allocate(totalLength)
        ip.put(0x45.toByte())         // version 4, IHL 5
        ip.put(0x00.toByte())         // DSCP/ECN
        ip.putShort(totalLength.toShort())
        ip.putShort(0)                // identification
        ip.putShort(0x4000.toShort()) // flags
        ip.put(64.toByte())           // TTL
        ip.put(17.toByte())           // protocol = UDP
        ip.putShort(0)                // header checksum (parse() doesn't validate it)
        ip.put(srcAddr)
        ip.put(dstAddr)
        ip.put(udpBytes)
        return ip.array()
    }

    // ---- parse(): happy path ----

    @Test
    fun `parse extracts domain, ports and transaction id from a valid query`() {
        val packet = buildDnsQueryPacket("example.com", srcPort = 40000, transactionId = byteArrayOf(0xAB.toByte(), 0xCD.toByte()))
        val parsed = DnsPacket.parse(packet, packet.size)

        assertNotNull(parsed)
        parsed!!
        assertEquals("example.com", parsed.queryDomain)
        assertEquals(40000, parsed.sourcePort)
        assertEquals(53, parsed.destPort)
        assertArrayEquals(byteArrayOf(0xAB.toByte(), 0xCD.toByte()), parsed.dnsTransactionId)
    }

    @Test
    fun `parse handles multi-label subdomains correctly`() {
        val packet = buildDnsQueryPacket("ads.tracker.example.co.id")
        val parsed = DnsPacket.parse(packet, packet.size)
        assertEquals("ads.tracker.example.co.id", parsed!!.queryDomain)
    }

    // ---- parse(): rejects malformed / irrelevant packets ----

    @Test
    fun `parse rejects packets shorter than the minimum header size`() {
        val tooShort = ByteArray(20)
        assertNull(DnsPacket.parse(tooShort, tooShort.size))
    }

    @Test
    fun `parse rejects non-IPv4 packets`() {
        val packet = buildDnsQueryPacket("example.com")
        packet[0] = 0x65 // version 6 in the high nibble
        assertNull(DnsPacket.parse(packet, packet.size))
    }

    @Test
    fun `parse rejects non-UDP protocol`() {
        val packet = buildDnsQueryPacket("example.com")
        packet[9] = 6 // TCP instead of UDP(17)
        assertNull(DnsPacket.parse(packet, packet.size))
    }

    @Test
    fun `parse rejects packets not addressed to port 53`() {
        val packet = buildDnsQueryPacket("example.com", dstPort = 8053)
        assertNull(DnsPacket.parse(packet, packet.size))
    }

    @Test
    fun `parse rejects a DNS message with zero questions`() {
        val packet = buildDnsQueryPacket("example.com")
        // QDCOUNT lives right after the 8-byte IPv4 header offset... recompute
        // offset generically: IHL(20) + UDP(8) + DNS header QDCOUNT at byte 4-5.
        val qdCountOffset = 20 + 8 + 4
        packet[qdCountOffset] = 0
        packet[qdCountOffset + 1] = 0
        assertNull(DnsPacket.parse(packet, packet.size))
    }

    // ---- buildBlockedResponse(): manually decode the synthetic reply ----

    @Test
    fun `buildBlockedResponse produces a well-formed 0-point-0-point-0-point-0 A-record reply`() {
        val packetBytes = buildDnsQueryPacket(
            "blocked.example.com",
            srcAddr = byteArrayOf(10, 111, 222, 5),
            dstAddr = byteArrayOf(10, 111, 222, 1),
            srcPort = 40000,
            dstPort = 53,
            transactionId = byteArrayOf(0x99.toByte(), 0x88.toByte())
        )
        val realQuery = DnsPacket.parse(packetBytes, packetBytes.size)!!

        val response = DnsPacket.buildBlockedResponse(realQuery)
        val buf = ByteBuffer.wrap(response)

        // IPv4 header
        assertEquals(0x45, buf.get(0).toInt() and 0xFF)      // version 4, IHL 5
        assertEquals(17, buf.get(9).toInt() and 0xFF)        // protocol UDP
        val respSrcAddr = ByteArray(4).also { buf.position(12); buf.get(it) }
        val respDstAddr = ByteArray(4).also { buf.get(it) }
        assertArrayEquals(byteArrayOf(10, 111, 222, 1), respSrcAddr) // spoofed as fake DNS server
        assertArrayEquals(byteArrayOf(10, 111, 222, 5), respDstAddr) // back to the requesting app

        // UDP header (starts at byte 20)
        val respSrcPort = ((response[20].toInt() and 0xFF) shl 8) or (response[21].toInt() and 0xFF)
        val respDstPort = ((response[22].toInt() and 0xFF) shl 8) or (response[23].toInt() and 0xFF)
        assertEquals(53, respSrcPort)     // "from" our fake DNS server
        assertEquals(40000, respDstPort)  // "to" the original requester's ephemeral port

        // DNS header (starts at byte 28): transaction ID must match the query
        assertEquals(0x99, response[28].toInt() and 0xFF)
        assertEquals(0x88, response[29].toInt() and 0xFF)
        val ancount = ((response[34].toInt() and 0xFF) shl 8) or (response[35].toInt() and 0xFF)
        assertEquals(1, ancount) // exactly one answer record

        // Last 4 bytes of the packet are the A-record RDATA -> must be 0.0.0.0
        val rdata = response.copyOfRange(response.size - 4, response.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), rdata)
    }

    @Test
    fun `buildBlockedResponse produces a valid IPv4 header checksum`() {
        val packetBytes = buildDnsQueryPacket("checksum-test.example.com")
        val query = DnsPacket.parse(packetBytes, packetBytes.size)!!
        val response = DnsPacket.buildBlockedResponse(query)

        // Standard IPv4 checksum self-check: summing all 16-bit words of the
        // 20-byte header (checksum field included) in one's-complement
        // arithmetic must fold to exactly 0xFFFF.
        var sum = 0
        var i = 0
        while (i < 20) {
            val word = ((response[i].toInt() and 0xFF) shl 8) or (response[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        assertTrue(sum == 0xFFFF)
    }
}
