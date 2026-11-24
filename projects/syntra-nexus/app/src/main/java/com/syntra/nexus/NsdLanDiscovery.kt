package com.syntra.nexus

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/** Opt-in discovery only. Results are untrusted; no socket or pairing is opened. */
data class DiscoveredNexusPeer(
    val serviceName: String,
    val hostAddress: String,
    val port: Int,
    val serviceType: String
) {
    val isUntrusted: Boolean get() = true

    fun isStructurallyValid(): Boolean =
        serviceName.startsWith(SERVICE_NAME_PREFIX) &&
            hostAddress.isNotBlank() &&
            port in 1..65535 &&
            serviceType.trimEnd('.') == SERVICE_TYPE.trimEnd('.')

    companion object {
        const val SERVICE_NAME_PREFIX = "SYNTRA-NEXUS"
        const val SERVICE_TYPE = "_syntranexus._tcp."
    }
}

class NsdLanDiscovery(
    context: Context,
    private val onPeersChanged: (List<DiscoveredNexusPeer>) -> Unit,
    private val onStatusChanged: (String) -> Unit
) {
    private val manager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val peers = LinkedHashMap<String, DiscoveredNexusPeer>()
    private val running = AtomicBoolean(false)

    @Volatile
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        peers.clear()
        publishPeers()
        postStatus("Đang tìm thiết bị NEXUS trong mạng Wi-Fi…")

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                postStatus("Đang quét LAN. Thiết bị tìm thấy chưa được xác minh.")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.trimEnd('.')
                        .equals(DiscoveredNexusPeer.SERVICE_TYPE.trimEnd('.'), ignoreCase = true)
                ) return
                if (!serviceInfo.serviceName.startsWith(DiscoveredNexusPeer.SERVICE_NAME_PREFIX)) return

                try {
                    manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            postStatus("Không thể đọc thông tin một dịch vụ NEXUS (mã $errorCode).")
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val address: InetAddress = info.host ?: return
                            val peer = DiscoveredNexusPeer(
                                serviceName = info.serviceName,
                                hostAddress = address.hostAddress ?: return,
                                port = info.port,
                                serviceType = info.serviceType
                            )
                            if (!peer.isStructurallyValid()) return
                            mainHandler.post {
                                if (!running.get()) return@post
                                peers[peer.serviceName] = peer
                                publishPeers()
                                postStatus("Tìm thấy ${peers.size} dịch vụ NEXUS chưa xác minh.")
                            }
                        }
                    })
                } catch (_: IllegalArgumentException) {
                    postStatus("Không thể phân giải dịch vụ LAN.")
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                mainHandler.post {
                    peers.remove(serviceInfo.serviceName)
                    publishPeers()
                    postStatus("Một dịch vụ đã rời khỏi mạng hoặc không còn được quảng bá.")
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                postStatus("Đã dừng quét LAN.")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                running.set(false)
                discoveryListener = null
                postStatus("Không thể bắt đầu quét LAN (mã $errorCode).")
                try {
                    manager.stopServiceDiscovery(this)
                } catch (_: IllegalArgumentException) {
                    // Listener was not registered.
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                postStatus("Không thể dừng quét LAN sạch sẽ (mã $errorCode).")
            }
        }

        discoveryListener = listener
        try {
            manager.discoverServices(
                DiscoveredNexusPeer.SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                listener
            )
        } catch (_: Exception) {
            running.set(false)
            discoveryListener = null
            postStatus("Thiết bị không thể khởi động NSD. Hãy kiểm tra Wi-Fi và thử lại.")
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        val listener = discoveryListener
        discoveryListener = null
        if (listener != null) {
            try {
                manager.stopServiceDiscovery(listener)
            } catch (_: IllegalArgumentException) {
                // Discovery already stopped by the framework.
            }
        }
        mainHandler.post {
            peers.clear()
            publishPeers()
            postStatus("Đã dừng quét LAN.")
        }
    }

    fun release() = stop()

    private fun publishPeers() {
        val snapshot = peers.values.toList()
        mainHandler.post { onPeersChanged(snapshot) }
    }

    private fun postStatus(value: String) {
        mainHandler.post { onStatusChanged(value) }
    }
}
