package com.kin.athena.service.vpn.network.transport.udp

import com.kin.athena.service.vpn.network.transport.ipv4.IPv4
import java.nio.ByteBuffer

fun soarResponse(
  clientPacketData: ByteBuffer,
  @Suppress("UnusedParameter") ipHeader: IPv4,
): ByteArray {
  val udpHeader = clientPacketData.toUDPHeader()
  val udpData = ByteBuffer.wrap(udpHeader.extractUDPData(clientPacketData))

  val message = Message(udpData)
  message.header.setFlag(Flags.QR.toInt())
  message.header.rcode = Rcode.NOERROR
  message.addRecord(createSOAResponse(), Section.AUTHORITY)
  return message.toWire()
}

fun handleDnsResponse(
  requestPacket: UDPModel,
  ipHeader: IPv4,
  responsePayload: ByteArray,
): ByteArray = createResponsePacket(ipHeader, requestPacket, responsePayload)

private fun createSOAResponse(): SOARecord {
  val negativeCacheTtlSeconds = 5L
  val name = Name("dnsnet.dnsnet.invalid.")

  return SOARecord(
    name,
    DClass.IN,
    negativeCacheTtlSeconds,
    name,
    name,
    0,
    0,
    0,
    0,
    negativeCacheTtlSeconds,
  )
}
