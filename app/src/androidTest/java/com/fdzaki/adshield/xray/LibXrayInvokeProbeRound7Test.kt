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
 * v3.37.0 — ROUND 7, dipicu HASIL round 6 (v3.36.0) yang DEFINITIF:
 * `probeTestXrayNonexistentPath` DAN `probeTestXrayEmptyStringPath`
 * SAMA-SAMA balas error PERSIS SAMA (`"infra/conf/serial: failed to
 * read config file > EOF"`) dengan path asli yang valid & berisi —
 * membuktikan field `configPath` TIDAK PERNAH sampai ke fungsi Go untuk
 * method `testXray` via `Invoke()`. Ini bug di dispatcher AAR ini
 * (kategori method `func XXX(base64Text string) string`), BUKAN bug di
 * kode kita, dan BUKAN sesuatu yang bisa "diperbaiki" dari sisi Kotlin.
 *
 * Pertanyaan yang BELUM terjawab, dan JAUH lebih penting dari `testXray`
 * itu sendiri: apakah `runXray` — method yang SUNGGUHAN akan dipakai utk
 * menyalakan tunnel produksi (bukan cuma validasi) — kena bug dispatcher
 * yang SAMA? Kalau ya, `configPath` juga akan selalu kosong saat start
 * tunnel sungguhan → **jalur Xray-core lewat AAR libXray ini MATI TOTAL
 * untuk kebutuhan WARP-native, bukan cuma soal validasi client-side**.
 * Kalau TIDAK (error beda dari EOF, atau malah `success:true`),
 * `runXray` selamat dari bug ini, dan strategi resminya: skip
 * `testXray` untuk validasi client-side, langsung integrasi `runXray`
 * + tangani kegagalan real-time dari respons/observasi tunnel.
 *
 * Metodologi SAMA PERSIS seperti round 6 (nonexistent-path sebagai bukti
 * definitif): kirim `configPath` yang DIJAMIN tidak ada. Kalau errornya
 * "no such file"/beda dari pola EOF → field SAMPAI ke Go, `runXray`
 * AMAN dipakai. Kalau errornya PERSIS pola EOF yang sama seperti
 * `testXray` → field JUGA tidak pernah sampai, `runXray` JUGA kena bug
 * yang sama.
 *
 * **`stopXray` SELALU dipanggil di `finally`** — best-effort cleanup,
 * kalau ternyata `runXray` dengan configPath kosong/nonexistent somehow
 * tetap mencoba start proses/goroutine background, kita tidak ingin
 * meninggalkan proses itu menggantung selama sisa instrumented test run.
 *
 * Tidak fail keras kalau gagal — pola sama seperti round 1-6, murni
 * observasional lewat Logcat tag `LibXrayInvokeProbeR7`.
 */
@RunWith(AndroidJUnit4::class)
class LibXrayInvokeProbeRound7Test {

    companion object {
        private const val TAG = "LibXrayInvokeProbeR7"
    }

    private fun invokeAndLog(prefix: String, label: String, requestJson: String): String {
        return try {
            val response = LibXray.invoke(requestJson)
            val ok = response.contains("\"success\":true") || response.contains("\"success\": true")
            if (ok) {
                Log.i(TAG, "$prefix-WIN: [$label] request=$requestJson response=$response")
            } else {
                Log.i(TAG, "$prefix-MISS: [$label] request=$requestJson response=$response")
            }
            response
        } catch (t: Throwable) {
            Log.w(TAG, "$prefix-ERROR: [$label] request=$requestJson threw ${t.javaClass.simpleName}: ${t.message}")
            ""
        }
    }

    @Test
    fun probeRunXrayNonexistentPath() {
        try {
            LibXray.touch()
            Log.i(TAG, "LibXray.touch() OK — native lib berhasil ter-load.")
        } catch (t: Throwable) {
            Log.e(TAG, "LibXray.touch() GAGAL: ${t.javaClass.simpleName}: ${t.message}", t)
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val datDir = context.filesDir.absolutePath
        val datDirEscaped = JSONObject.quote(datDir)

        // Path yang DIJAMIN tidak pernah ada — sama persis polanya
        // dengan round 6, tapi sekarang untuk method "runXray".
        val nonexistentPath = File(context.filesDir, "definitely_does_not_exist_r7_probe.json").absolutePath
        val nonexistentPathEscaped = JSONObject.quote(nonexistentPath)

        try {
            invokeAndLog(
                "RUNXRAY-NOFILE",
                "configPath=nonexistent",
                """{"apiVersion":2,"method":"runXray","payload":{"datDir":$datDirEscaped,"configPath":$nonexistentPathEscaped}}"""
            )
        } finally {
            // Best-effort cleanup — kalau runXray somehow start proses
            // background walau config invalid, jangan biarkan menggantung.
            invokeAndLog(
                "STOPXRAY-CLEANUP",
                "cleanup setelah probeRunXrayNonexistentPath",
                """{"apiVersion":2,"method":"stopXray","payload":{}}"""
            )
        }

        Log.i(
            TAG,
            "PROBE ROUND 7 runXray-nonexistent-path SELESAI — BANDINGKAN error RUNXRAY-NOFILE di atas dengan " +
                "pola EOF round 3-6 (\"infra/conf/serial ... EOF\"): kalau SAMA PERSIS, runXray JUGA kena bug " +
                "dispatcher yang sama (configPath tidak pernah sampai) — jalur Xray-core via libXray AAR ini MATI " +
                "TOTAL utk WARP-native, harus cari basis lain atau batalkan roadmap Xray-core sepenuhnya. Kalau " +
                "BEDA (mis. \"no such file\"), runXray AMAN — lanjut ke Round 8: integrasi langsung skip testXray, " +
                "config asli (WireGuard-outbound + reserved bytes WARP), tangani kegagalan dari respons/observasi " +
                "tunnel real-time, TANPA validasi client-side testXray sama sekali."
        )
    }
}
