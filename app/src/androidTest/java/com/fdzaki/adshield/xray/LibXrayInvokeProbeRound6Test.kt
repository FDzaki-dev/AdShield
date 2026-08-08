package com.fdzaki.adshield.xray

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import libXray.LibXray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * v3.36.0 — ROUND 6, dipicu HASIL round 5 (v3.35.0): `probeConfigFileBytesBeforeInvoke`
 * membuktikan file BENAR ADA isi (470 byte, `readText()` sukses PERSIS
 * sebelum `invoke()`) dan `probeTestXrayMinimalFreedomConfig` (config
 * 53-byte, `freedom`-only, TANPA WireGuard) tetap gagal — DUA hipotesis
 * round 5 (file kosong / config WireGuard bermasalah) SAMA-SAMA GUGUR.
 * Error `"infra/conf/serial: failed to read config file > EOF"` identik
 * di SEMUA percobaan sejak round 3, terlepas dari isi/ukuran file.
 *
 * Kesimpulan sementara paling masuk akal: field `configPath` di dalam
 * `"payload"` TIDAK PERNAH benar-benar sampai ke fungsi Go
 * `xray.TestXray(datDir, configPath)` — kemungkinan bug di dispatcher
 * `Invoke()` (`android_wrapper.go`, source-nya TIDAK berhasil dibaca
 * penuh lewat pencarian web berulang kali, kemungkinan reflection/
 * remarshal-ke-base64 khusus utk method yang underlying func-nya
 * `func XXX(base64Text string) string` — `testXray` salah satunya —
 * berbeda dari `getFreePorts` yang underlying func-nya `GetFreePorts(count
 * int)` (param langsung, bukan base64Text) dan TERBUKTI benar di round
 * 4/5). Kalau benar, `configPath` internal jadi STRING KOSONG akibat bug
 * itu, dan Xray-core coba baca file `""` → persis pola error
 * "berhasil `os.Open`, tapi 0 byte / EOF instan" (`os.Open("")` sebetulnya
 * harusnya gagal "no such file", TAPI banyak implementasi konfigurasi
 * Xray-core treat path kosong sebagai "baca dari stdin" — stdin process
 * instrumented test kosong/EOF instan, PERSIS cocok pesan errornya).
 *
 * Test PALING MURAH DAN DEFINITIF untuk buktikan/sangkal ini:
 * `probeTestXrayNonexistentPath` — kirim `configPath` yang JELAS-JELAS
 * TIDAK ADA di filesystem sama sekali. Kalau errornya BEDA (mis. "no such
 * file or directory" / "open ...: no such file") → field configPath
 * TERBUKTI sampai ke Go, path asli kita yang bermasalah (balik ke
 * investigasi isi/permission). Kalau errornya TETAP PERSIS SAMA
 * `"infra/conf/serial: failed to read config file > EOF"` seperti path
 * asli yang valid → field configPath TERBUKTI TIDAK PERNAH sampai ke
 * Go sama sekali (selalu efektif jadi string kosong/stdin) — ini bug di
 * libXray AAR ini utk `testXray` via `Invoke()`, BUKAN bug di kode kita.
 * Kalau ini yang terjadi, kesimpulan utk `WarpTunnelManager` nanti:
 * jangan pakai `testXray` utk validasi config sama sekali, cari cara lain
 * (mis. `runXray` langsung dgn config asli, terima error real-time saat
 * start, atau skip validasi client-side dan andalkan handshake WireGuard
 * asli sbg sinyal config valid/tidak).
 *
 * `probeTestXrayEmptyStringPath` — kontrol tambahan: kirim `configPath`
 * STRING KOSONG eksplisit (`""`) — kalau errornya PERSIS SAMA dengan
 * nonexistent-path DAN dengan path asli yang valid, itu bukti tambahan
 * kuat teori "internal selalu efektif jadi stdin/string kosong" di atas.
 *
 * Sama seperti round 1-5: tidak fail keras kalau semua kandidat gagal.
 */
@RunWith(AndroidJUnit4::class)
class LibXrayInvokeProbeRound6Test {

    companion object {
        private const val TAG = "LibXrayInvokeProbeR6"
    }

    private fun invokeAndLog(prefix: String, label: String, requestJson: String) {
        try {
            val response = LibXray.invoke(requestJson)
            val ok = response.contains("\"success\":true") || response.contains("\"success\": true")
            if (ok) {
                Log.i(TAG, "$prefix-WIN: [$label] request=$requestJson response=$response")
            } else {
                Log.i(TAG, "$prefix-MISS: [$label] request=$requestJson response=$response")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "$prefix-ERROR: [$label] request=$requestJson threw ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    @Test
    fun probeTestXrayNonexistentPath() {
        try {
            LibXray.touch()
            Log.i(TAG, "LibXray.touch() OK — native lib berhasil ter-load.")
        } catch (t: Throwable) {
            Log.e(TAG, "LibXray.touch() GAGAL: ${t.javaClass.simpleName}: ${t.message}", t)
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val datDir = context.filesDir.absolutePath
        val datDirEscaped = JSONObject.quote(datDir)

        // Path yang DIJAMIN tidak pernah ada — bukan file yang ditulis
        // test ini, tidak ditulis test lain, folder acak tidak dibuat.
        val nonexistentPath = File(context.filesDir, "definitely_does_not_exist_r6_probe.json").absolutePath
        val nonexistentPathEscaped = JSONObject.quote(nonexistentPath)

        invokeAndLog(
            "TESTXRAY-NOFILE",
            "configPath=nonexistent",
            """{"apiVersion":2,"method":"testXray","payload":{"datDir":$datDirEscaped,"configPath":$nonexistentPathEscaped}}"""
        )

        Log.i(
            TAG,
            "PROBE ROUND 6 nonexistent-path SELESAI — BANDINGKAN error di atas dengan error round 3/4/5 " +
                "(\"infra/conf/serial ... EOF\"): kalau BEDA (mis. \"no such file\"), configPath asli kita valid, " +
                "field-nya SAMPAI ke Go, masalah ada di isi/akses file. Kalau SAMA PERSIS, configPath TIDAK PERNAH " +
                "sampai ke Go — bug di dispatcher Invoke() untuk testXray, bukan di kode kita."
        )
    }

    @Test
    fun probeTestXrayEmptyStringPath() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val datDir = context.filesDir.absolutePath
        val datDirEscaped = JSONObject.quote(datDir)

        invokeAndLog(
            "TESTXRAY-EMPTYPATH",
            "configPath=\"\"(explicit empty)",
            """{"apiVersion":2,"method":"testXray","payload":{"datDir":$datDirEscaped,"configPath":""}}"""
        )
    }
}
