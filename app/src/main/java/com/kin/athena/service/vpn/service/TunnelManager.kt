/*
 * Copyright (C) 2025-2026 Vexzure
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should hinave received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.kin.athena.service.vpn.service

import android.os.Build
import android.util.Log
import com.kin.athena.core.logging.Logger
import com.kin.athena.service.firewall.handler.RuleHandler
import com.kin.athena.service.firewall.handler.filterPacket
import com.kin.athena.service.firewall.model.FirewallResult
import com.kin.athena.service.vpn.network.transport.dns.DNSModel
import com.kin.athena.service.vpn.network.transport.dns.toDNSModel
import com.kin.athena.service.vpn.network.transport.icmp.toICMPPacket
import com.kin.athena.service.vpn.network.transport.ipv4.toIPv4Header
import com.kin.athena.service.vpn.network.transport.tcp.TCPHeader
import com.kin.athena.service.vpn.network.transport.udp.extractUDPData
import com.kin.athena.service.vpn.network.transport.udp.handleDnsResponse
import com.kin.athena.service.vpn.network.transport.udp.soarResponse
import com.kin.athena.service.vpn.network.transport.udp.toUDPHeader
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TunnelManager(
  private val dnsServerV4: String = "9.9.9.9",
  private val dnsServerV6: String = "2620:fe::fe",
) {
  private var contextPtr: Long = 0
  private val lock = Any()
  private var isReleased = false

  fun initialize(): Boolean {
    contextPtr = jni_init(Build.VERSION.SDK_INT)
    if (contextPtr != 0L) {
      // Set DNS servers in native code
      jni_set_dns_servers(contextPtr, dnsServerV4, dnsServerV6)
    }
    return contextPtr != 0L
  }

  fun start(logLevel: Int) {
    if (contextPtr != 0L) {
      jni_start(contextPtr, logLevel)
    }
  }

  fun run(
    tunFd: Int,
    forwardDns: Boolean = true,
    rcode: Int = 3,
  ) {
    if (contextPtr != 0L) {
      jni_run(contextPtr, tunFd, forwardDns, rcode)
    }
  }

  fun stop() {
    if (contextPtr != 0L) {
      jni_stop(contextPtr)
    }
  }

  fun done() {
    if (contextPtr != 0L) {
      jni_done(contextPtr)
      contextPtr = 0L
    }
  }

  fun getMtu(): Int = jni_get_mtu()

  fun getProperty(name: String): String = jni_getprop(name)

  fun setDnsServers(
    dnsV4: String,
    dnsV6: String,
  ) {
    if (contextPtr != 0L) {
      jni_set_dns_servers(contextPtr, dnsV4, dnsV6)
    }
  }

  fun clearSessions() {
    synchronized(lock) {
      try {
        if (isReleased) {
          Logger.warn("Cannot clear sessions: TunnelManager has been released")
          return
        }
        val contextPtrSnapshot = contextPtr
        if (contextPtrSnapshot != 0L) {
          jni_clear_sessions(contextPtrSnapshot)
        } else {
          Logger.warn("Cannot clear sessions: TunnelManager not properly initialized")
        }
      } catch (e: UnsatisfiedLinkError) {
        Logger.error("Native library unavailable for clearSessions: ${e.message}")
      } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Logger.error("Error in clearSessions: ${e.message}", e)
      }
    }
  }

  fun release() {
    synchronized(lock) {
      if (!isReleased) {
        isReleased = true
        val contextPtrSnapshot = contextPtr
        if (contextPtrSnapshot != 0L) {
          try {
            stop()
            done()
          } catch (e: UnsatisfiedLinkError) {
            Logger.error("Native library unavailable during release: ${e.message}")
          } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.error("Error during TunnelManager release: ${e.message}", e)
          } finally {
            contextPtr = 0L
          }
          Logger.info("TunnelManager resources released")
        } else {
          Logger.info("TunnelManager was already released or not initialized")
        }
      }
    }
  }

  @Suppress("FunctionNaming")
  private external fun jni_init(sdk: Int): Long

  @Suppress("FunctionNaming")
  private external fun jni_start(
    context: Long,
    loglevel: Int,
  )

  @Suppress("FunctionNaming")
  private external fun jni_run(
    context: Long,
    tun: Int,
    fwd53: Boolean,
    rcode: Int,
  )

  @Suppress("FunctionNaming")
  private external fun jni_stop(context: Long)

  @Suppress("FunctionNaming")
  private external fun jni_done(context: Long)

  @Suppress("FunctionNaming")
  private external fun jni_getprop(name: String): String

  @Suppress("FunctionNaming")
  private external fun jni_get_mtu(): Int

  @Suppress("FunctionNaming")
  private external fun jni_clear_sessions(context: Long)

  @Suppress("FunctionNaming")
  private external fun jni_set_dns_servers(
    context: Long,
    dnsV4: String,
    dnsV6: String,
  )

  companion object {
    init {
      System.loadLibrary("athena")
    }
  }
}
