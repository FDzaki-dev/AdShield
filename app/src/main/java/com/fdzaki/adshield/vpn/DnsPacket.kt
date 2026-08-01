package com.fdzaki.adshield.vpn

import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Minimal IPv4 + UDP + DNS parsing/building, just enough to:
 *  1) read the queried domain name out of an outgoing DNS request packet
 *  2) build a synthetic "blocked" response (A record -> 0.0.0.0) when needed
 *
 * We deliberately only handle IPv4/UDP/port-53 traffic — that is all that
 * ever reaches the tun interface, because the VpnService route table only
 * points the fake DNS server address at us (see AdBlockVpnService).
 */
object DnsPacket {

    data class ParsedQuery(
        val sourceAddress: InetAddress,
        val sourcePort: Int,
        val destAddress: InetAddress,
        val destPort: Int,
        val dnsTransactionId: ByteArray,
        val queryDomain: String,
        val rawDnsQuestionSection: ByteArray
    )

    /** Returns null if this isn't a well-formed IPv4/UDP/DNS-query packet we can handle. */
    fun parse(packet: ByteArray, length: Int): ParsedQuery? {
        if (length < 28) return null // shorter than min IP+UDP+DNS header
        val buffer = ByteBuffer.wrap(packet, 0, length)

        val versionAndIhl = buffer.get(0).toInt()
        val version = (versionAndIhl and 0xF0) shr 4
        if (version != 4) return null
        val ihl = (versionAndIhl and 0x0F) * 4
        val protocol = buffer.get(9).toInt() and 0xFF
        if (protocol != 17) return null // UDP only

        val srcAddrBytes = ByteArray(4)
        buffer.position(12)
        buffer.get(srcAddrBytes)
        val dstAddrBytes = ByteArray(4)
        buffer.get(dstAddrBytes)

        val udpStart = ihl
        if (udpStart + 8 > length) return null
        val srcPort = ((packet[udpStart].toInt() and 0xFF) shl 8) or (packet[udpStart + 1].toInt() and 0xFF)
        val dstPort = ((packet[udpStart + 2].toInt() and 0xFF) shl 8) or (packet[udpStart + 3].toInt() and 0xFF)
        if (dstPort != 53) return null

        val dnsStart = udpStart + 8
        if (dnsStart + 12 > length) return null // DNS header is 12 bytes

        val qdCount = ((packet[dnsStart + 4].toInt() and 0xFF) shl 8) or (packet[dnsStart + 5].toInt() and 0xFF)
        if (qdCount < 1) return null

        val txId = packet.copyOfRange(dnsStart, dnsStart + 2)

        // Parse the QNAME (sequence of length-prefixed labels, ending at 0x00)
        var pos = dnsStart + 12
        val nameBuilder = StringBuilder()
        while (pos < length) {
            val labelLen = packet[pos].toInt() and 0xFF
            if (labelLen == 0) { pos += 1; break }
            pos += 1
            if (pos + labelLen > length) return null
            if (nameBuilder.isNotEmpty()) nameBuilder.append('.')
            nameBuilder.append(String(packet, pos, labelLen, Charsets.US_ASCII))
            pos += labelLen
        }
        val qnameEnd = pos
        // include QTYPE(2) + QCLASS(2) in the question section we forward verbatim
        val questionEnd = (qnameEnd + 4).coerceAtMost(length)
        val rawQuestion = packet.copyOfRange(dnsStart + 12, questionEnd)

        val domain = nameBuilder.toString()
        if (domain.isEmpty()) return null

        return ParsedQuery(
            sourceAddress = InetAddress.getByAddress(srcAddrBytes),
            sourcePort = srcPort,
            destAddress = InetAddress.getByAddress(dstAddrBytes),
            destPort = dstPort,
            dnsTransactionId = txId,
            queryDomain = domain,
            rawDnsQuestionSection = rawQuestion
        )
    }

    /**
     * Builds a full IPv4/UDP/DNS response packet that answers the query with
     * 0.0.0.0, spoofing source = original destination (our fake DNS server)
     * and destination = original source (the requesting app), so it looks
     * exactly like a legitimate reply arriving through the tun interface.
     */
    fun buildBlockedResponse(query: ParsedQuery): ByteArray {
        // ---- DNS section ----
        val dns = ByteBuffer.allocate(12 + query.rawDnsQuestionSection.size + 16)
        dns.put(query.dnsTransactionId)
        dns.putShort(0x8180.toShort()) // standard response, recursion available, no error
        dns.putShort(1) // QDCOUNT
        dns.putShort(1) // ANCOUNT
        dns.putShort(0) // NSCOUNT
        dns.putShort(0) // ARCOUNT
        dns.put(query.rawDnsQuestionSection)
        // Answer: pointer to name at offset 12, TYPE A, CLASS IN, TTL 60, RDLENGTH 4, RDATA 0.0.0.0
        dns.put(0xC0.toByte()); dns.put(0x0C.toByte())
        dns.putShort(1) // TYPE A
        dns.putShort(1) // CLASS IN
        dns.putInt(60)  // TTL
        dns.putShort(4) // RDLENGTH
        dns.put(byteArrayOf(0, 0, 0, 0)) // 0.0.0.0
        val dnsBytes = dns.array().copyOf(dns.position())

        // ---- UDP section ----
        val udpLength = 8 + dnsBytes.size
        val udp = ByteBuffer.allocate(udpLength)
        udp.putShort(query.destPort.toShort())   // src port = our fake DNS server's port (53)
        udp.putShort(query.sourcePort.toShort()) // dst port = requesting app's ephemeral port
        udp.putShort(udpLength.toShort())
        udp.putShort(0) // checksum 0 = not computed (valid for IPv4/UDP)
        udp.put(dnsBytes)
        val udpBytes = udp.array()

        // ---- IPv4 section ----
        val totalLength = 20 + udpBytes.size
        val ip = ByteBuffer.allocate(totalLength)
        ip.put(0x45.toByte())      // version 4, IHL 5
        ip.put(0x00.toByte())      // DSCP/ECN
        ip.putShort(totalLength.toShort())
        ip.putShort(0)             // identification
        ip.putShort(0x4000.toShort()) // flags: don't fragment
        ip.put(64.toByte())        // TTL
        ip.put(17.toByte())        // protocol UDP
        ip.putShort(0)             // header checksum placeholder
        ip.put(query.destAddress.address)   // src = fake DNS server
        ip.put(query.sourceAddress.address) // dst = requesting app
        ip.put(udpBytes)

        val bytes = ip.array()
        val checksum = ipv4Checksum(bytes, 0, 20)
        bytes[10] = (checksum shr 8).toByte()
        bytes[11] = (checksum and 0xFF).toByte()
        return bytes
    }

    /**
     * Wraps a raw DNS reply message (bytes received verbatim from the real
     * upstream resolver) back into an IPv4/UDP packet addressed to the app
     * that originally issued the query, spoofing the source as our fake DNS
     * server so the OS accepts it as a legitimate reply on the tun interface.
     */
    fun wrapUpstreamReply(query: ParsedQuery, upstreamDnsMessage: ByteArray): ByteArray {
        val udpLength = 8 + upstreamDnsMessage.size
        val udp = ByteBuffer.allocate(udpLength)
        udp.putShort(query.destPort.toShort())
        udp.putShort(query.sourcePort.toShort())
        udp.putShort(udpLength.toShort())
        udp.putShort(0)
        udp.put(upstreamDnsMessage)
        val udpBytes = udp.array()

        val totalLength = 20 + udpBytes.size
        val ip = ByteBuffer.allocate(totalLength)
        ip.put(0x45.toByte())
        ip.put(0x00.toByte())
        ip.putShort(totalLength.toShort())
        ip.putShort(0)
        ip.putShort(0x4000.toShort())
        ip.put(64.toByte())
        ip.put(17.toByte())
        ip.putShort(0)
        ip.put(query.destAddress.address)
        ip.put(query.sourceAddress.address)
        ip.put(udpBytes)

        val bytes = ip.array()
        val checksum = ipv4Checksum(bytes, 0, 20)
        bytes[10] = (checksum shr 8).toByte()
        bytes[11] = (checksum and 0xFF).toByte()
        return bytes
    }

    private fun ipv4Checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or
                (if (i + 1 < offset + length) (data[i + 1].toInt() and 0xFF) else 0)
            sum += word
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
