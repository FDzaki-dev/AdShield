package com.fdzaki.adshield.vpn

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.fdzaki.adshield.MainActivity
import com.fdzaki.adshield.R
import com.fdzaki.adshield.data.BlocklistManager
import com.fdzaki.adshield.data.SettingsRepository
import com.fdzaki.adshield.data.db.AppDatabase
import com.fdzaki.adshield.data.db.DomainLogEntity
import com.fdzaki.adshield.receiver.RestartReceiver
import com.fdzaki.adshield.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private lateinit var settingsRepository: SettingsRepository
    private val blocklist = BlocklistManager.getInstance()

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> startVpn()
        }
        // START_STICKY: if the system kills this service under memory pressure,
        // it restarts automatically with a null intent, so protection resumes
        // even without the user reopening the app.
        return START_STICKY
    }

    private fun startVpn() {
        if (running.get()) return

        serviceScope.launch {
            blocklist.loadDefaultList(applicationContext)
            blocklist.setCustomBlocked(settingsRepository.customBlockedDomains.firstValue())
            blocklist.setCustomAllowed(settingsRepository.customAllowedDomains.firstValue())
            blocklist.setWhitelistedApps(settingsRepository.whitelistedApps.firstValue())
            settingsRepository.setWasRunning(true)
        }

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(Constants.VPN_ADDRESS, Constants.VPN_ADDRESS_PREFIX)
            .addDnsServer(Constants.VPN_ADDRESS)
            .addRoute(Constants.VPN_ROUTE, 32) // ONLY DNS traffic is routed into the tun
            .setMtu(Constants.VPN_MTU)
            .setBlocking(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        vpnInterface = try {
            builder.establish()
        } catch (e: Exception) {
            null
        }

        val iface = vpnInterface ?: return
        running.set(true)
        startForeground(Constants.NOTIF_ID, buildNotification())
        executor.execute { runPacketLoop(iface) }
    }

    private fun stopVpn() {
        running.set(false)
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        serviceScope.launch { settingsRepository.setWasRunning(false) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun runPacketLoop(iface: ParcelFileDescriptor) {
        val input = FileInputStream(iface.fileDescriptor)
        val output = FileOutputStream(iface.fileDescriptor)
        val buffer = ByteArray(Constants.VPN_MTU)

        while (running.get()) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                if (running.get()) continue else break
            }
            if (length <= 0) continue

            val packetCopy = buffer.copyOf(length)
            val query = DnsPacket.parse(packetCopy, length) ?: continue

            val blocked = blocklist.isBlocked(query.queryDomain)

            if (blocked) {
                writeBlockedResponse(output, query)
                logAndCount(query.queryDomain, true)
            } else {
                // Fire-and-forget async forward so a slow upstream lookup never
                // stalls the packet loop for other concurrent queries.
                executor.execute { forwardToUpstream(query, output) }
                logAndCount(query.queryDomain, false)
            }
        }

        try { input.close() } catch (_: Exception) {}
        try { output.close() } catch (_: Exception) {}
    }

    private fun writeBlockedResponse(output: FileOutputStream, query: DnsPacket.ParsedQuery) {
        try {
            val response = DnsPacket.buildBlockedResponse(query)
            synchronized(output) { output.write(response) }
        } catch (_: Exception) {
            // Dropping the query is an acceptable fallback: the requesting app
            // simply sees the DNS lookup time out, same net effect as blocked.
        }
    }

    private fun forwardToUpstream(query: DnsPacket.ParsedQuery, output: FileOutputStream) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            protect(socket) // exclude this socket from the VPN's own routing (avoid loop)
            socket.soTimeout = 4000

            val dnsRequest = buildForwardedRequest(query)
            val upstream = InetSocketAddress(Constants.UPSTREAM_DNS_SERVERS.first(), Constants.DNS_PORT)
            socket.send(DatagramPacket(dnsRequest, dnsRequest.size, upstream))

            val replyBuf = ByteArray(1500)
            val replyPacket = DatagramPacket(replyBuf, replyBuf.size)
            socket.receive(replyPacket)

            val responsePacket = DnsPacket.wrapUpstreamReply(
                query, replyBuf.copyOf(replyPacket.length)
            )
            synchronized(output) { output.write(responsePacket) }
        } catch (_: Exception) {
            // Upstream timeout/unreachable: silently drop, app's own DNS retry handles it.
        } finally {
            socket?.close()
        }
    }

    private fun buildForwardedRequest(query: DnsPacket.ParsedQuery): ByteArray {
        // Reconstruct just the DNS message (header ID + standard flags + question)
        val buf = java.nio.ByteBuffer.allocate(12 + query.rawDnsQuestionSection.size)
        buf.put(query.dnsTransactionId)
        buf.putShort(0x0100) // standard query, recursion desired
        buf.putShort(1); buf.putShort(0); buf.putShort(0); buf.putShort(0)
        buf.put(query.rawDnsQuestionSection)
        return buf.array().copyOf(buf.position())
    }

    private fun logAndCount(domain: String, blocked: Boolean) {
        serviceScope.launch {
            if (blocked) settingsRepository.incrementBlocked() else settingsRepository.incrementAllowed()
            if (settingsRepository.loggingEnabled.firstValue()) {
                runCatching {
                    AppDatabase.getInstance(applicationContext).domainLogDao()
                        .insert(DomainLogEntity(domain = domain, blocked = blocked))
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, AdBlockVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title_active))
            .setContentText(getString(R.string.notif_text_active))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The app was swiped away from Recents. START_STICKY + a foreground
        // service already keeps this service alive under stock Android; this
        // watchdog alarm is a defensive backup for OEM skins that ignore that
        // guarantee and kill foreground services anyway.
        if (running.get()) {
            scheduleWatchdog()
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun scheduleWatchdog() {
        try {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                this, 0, Intent(this, RestartReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val triggerAt = SystemClock.elapsedRealtime() + 3000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: Exception) {
            // Best-effort watchdog; normal START_STICKY still applies if this fails.
        }
    }

    override fun onRevoke() {
        // User revoked the VPN permission from system settings.
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.fdzaki.adshield.action.START"
        const val ACTION_STOP = "com.fdzaki.adshield.action.STOP"
    }
}

/** Small helper: read the current value of a Flow without collecting long-term. */
private suspend fun <T> Flow<T>.firstValue(): T = first()
