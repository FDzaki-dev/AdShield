package com.fdzaki.adshield.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process

/**
 * On-demand memory + battery snapshot for the Diagnostics screen (v3.10.0 —
 * resource profiling instrumentation, PROJECT_STATE.md "Yang HARUS
 * dikerjakan" priority #3, previously a total gap). Deliberately POLL-based
 * from the UI layer (see MainViewModel.resourceSnapshot), not a background
 * logger/service: the ask was to *characterize* memory/battery usage, not
 * add a new always-on component — a persistent sampler would itself cost
 * part of the battery budget being measured. Every read here uses only
 * public, no-permission-required APIs (ActivityManager.getProcessMemoryInfo,
 * ActivityManager.getMemoryInfo, the sticky ACTION_BATTERY_CHANGED intent) —
 * none require any addition to AndroidManifest.xml.
 */
object ResourceMonitor {

    data class Snapshot(
        /** This process's proportional set size — the standard "how much RAM
         *  does my app actually use" metric, in KB. 0 until first read or if
         *  unavailable. */
        val appPssKb: Int = 0,
        /** System-wide RAM still available, in MB. */
        val systemAvailMemMb: Long = 0L,
        /** True when the system's own low-memory threshold is currently
         *  breached — a signal AdShield could be an early background-kill
         *  candidate right now, independent of this app's own PSS. */
        val systemLowMemory: Boolean = false,
        /** Battery charge, 0-100. -1 if the sticky battery intent wasn't available. */
        val batteryPercent: Int = -1,
        /** Battery temperature in Celsius. Null if unavailable. */
        val batteryTemperatureC: Float? = null,
        /** True while charging (USB/AC/wireless) or already full-on-charger. */
        val isCharging: Boolean = false,
        val updatedAt: Long = 0L
    )

    /**
     * Synchronous — every call here is a local system-service read (no I/O,
     * no network), safe at the low poll rate this is used at (~3s, only
     * while Diagnostics is visible — see MainViewModel). Every section is
     * wrapped in [runCatching] and silently falls back to the Snapshot
     * default for that field: a diagnostics screen must never itself be a
     * new crash source (same fail-safe philosophy as util/CrashLogger.kt).
     */
    fun snapshot(context: Context): Snapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

        val pssKb = runCatching {
            am?.getProcessMemoryInfo(intArrayOf(Process.myPid()))?.firstOrNull()?.totalPss ?: 0
        }.getOrDefault(0)

        var availMemMb = 0L
        var lowMemory = false
        runCatching {
            val sysMemInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(sysMemInfo)
            availMemMb = sysMemInfo.availMem / (1024 * 1024)
            lowMemory = sysMemInfo.lowMemory
        }

        var batteryPercent = -1
        var batteryTemp: Float? = null
        var charging = false
        runCatching {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) {
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPercent = (level * 100) / scale
                }
                val tempTenths = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                if (tempTenths != Int.MIN_VALUE) {
                    batteryTemp = tempTenths / 10f
                }
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        }

        return Snapshot(
            appPssKb = pssKb,
            systemAvailMemMb = availMemMb,
            systemLowMemory = lowMemory,
            batteryPercent = batteryPercent,
            batteryTemperatureC = batteryTemp,
            isCharging = charging,
            updatedAt = System.currentTimeMillis()
        )
    }
}
