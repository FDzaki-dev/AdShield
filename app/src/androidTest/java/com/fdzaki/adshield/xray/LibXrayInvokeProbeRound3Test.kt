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
 * v3.33.0 — Round 3, lanjutan LANGSUNG dari [LibXrayInvokeProbeTest] (round 1+2,
 * `apiVersion:2` + key `method` sudah TERBUKTI benar lewat CANDIDATE-WIN nyata
 * di run v3.32.3 — lihat PROJECT_STATE.md/CHANGELOG.md v3.32.3). Round ini TIDAK
 * mengulang pertanyaan bentuk envelope dasar (sudah terjawab) — dua pertanyaan
 * baru yang masih terbuka:
 *
 * 1. **`probeXrayVersionAndState`**: konfirmasi 2 method no-arg lain
 *    (`xrayVersion`, `getXrayState`) juga merespons via envelope yang sama —
 *    sanity-check murah sebelum lanjut ke method berpayload kompleks.
 * 2. **`probeTestXrayConfigCandidates`**: method `testXray` (dari dokumentasi
 *    resmi XTLS/libXray, signature Go `TestXray(datDir, configPath)`) PASTI
 *    butuh payload lebih dari `count` — TAPI nama field JSON invoke()-nya
 *    (`configPath` vs `configJson` vs `config`, dan apakah butuh file di disk
 *    atau string inline) TIDAK didokumentasikan publik. 3 kandidat dicoba:
 *    file config ditulis ke `context.filesDir` dulu (path pasti valid di
 *    sandbox app), lalu 3 variasi field JSON yang mereferensikannya/membawa
 *    isinya inline.
 *
 * Config WireGuard yang dites SENGAJA pakai placeholder (`secretKey` 32-byte
 * nol, `reserved:[0,0,0]`) — TUJUAN round ini murni "apakah field config ini
 * DITERIMA/diparse libXray", BUKAN "apakah tunnel WARP jalan". Isi
 * `secretKey`/`reserved` asli dari akun WARP TIDAK dipakai di sini (lihat
 * PROJECT_STATE.md kenapa formula `reserved` belum boleh dipakai mentah —
 * belum diverifikasi baca source wgcf). `publicKey` peer WARP resmi
 * (`bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=`) dan endpoint
 * (`engage.cloudflareclient.com:2408`) SUDAH dikonfirmasi konsisten lintas
 * banyak sumber independen (wgcf, dok Project X) — aman dipakai apa adanya.
 *
 * `testXray` diprioritaskan dari `runXray`/`runXrayFromJson` karena
 * kemungkinan besar cuma validasi config (tidak establish tunnel/butuh TUN
 * fd) — lebih aman untuk emulator CI. **BELUM DIVERIFIKASI** asumsi ini
 * benar — salah satu hal yang mau dibuktikan test ini sendiri.
 *
 * Sama seperti round 1+2: test ini SENGAJA tidak fail keras kalau semua
 * kandidat gagal — hasil "0 sukses tapi error jelas & seragam" tetap data
 * berguna untuk sesi berikutnya, dibaca dari Logcat prefix
 * `NOARG-RESULT:` / `TESTXRAY-WIN:` / `TESTXRAY-MISS:` / `TESTXRAY-ERROR:`.
 */
@RunWith(AndroidJUnit4::class)
class LibXrayInvokeProbeRound3Test {

    companion object {
        private const val TAG = "LibXrayInvokeProbeR3"

        // Peer WARP resmi — dikonfirmasi konsisten lintas wgcf/dok Project X,
        // BUKAN tebakan. Endpoint sama dengan fallback default
        // WarpTunnelManager existing (lihat Constants.kt).
        private const val WARP_PEER_PUBLIC_KEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
        private const val WARP_ENDPOINT = "engage.cloudflareclient.com:2408"

        // Placeholder 32-byte nol, base64 — SENGAJA bukan private key asli.
        // Tujuan probe cuma "apakah field ini diparse", bukan "apakah
        // handshake sukses" (itu langkah setelah ini, dengan key & reserved
        // ASLI dari akun WARP — belum boleh sebelum formula reserved
        // diverifikasi, lihat KDoc kelas).
        private const val PLACEHOLDER_ZERO_KEY_32B = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

        private fun probeConfigJson(): String = """
            {
              "outbounds": [{
                "protocol": "wireguard",
                "settings": {
                  "secretKey": "$PLACEHOLDER_ZERO_KEY_32B",
                  "address": ["172.16.0.2/32", "2606:4700:110:8949:fed8:2642:a640:c8e1/128"],
                  "peers": [{
                    "endpoint": "$WARP_ENDPOINT",
                    "publicKey": "$WARP_PEER_PUBLIC_KEY",
                    "keepAlive": 25,
                    "allowedIPs": ["0.0.0.0/0", "::/0"]
                  }],
                  "mtu": 1280,
                  "reserved": [0, 0, 0]
                },
                "tag": "warp"
              }]
            }
        """.trimIndent()
    }

    @Test
    fun probeXrayVersionAndState() {
        try {
            LibXray.touch()
            Log.i(TAG, "LibXray.touch() OK — native lib berhasil ter-load.")
        } catch (t: Throwable) {
            Log.e(TAG, "LibXray.touch() GAGAL: ${t.javaClass.simpleName}: ${t.message}", t)
        }

        // Method no-arg, envelope sudah TERBUKTI benar (round 2) — cuma
        // ganti "method", tidak perlu variasi bentuk lagi.
        val noArgMethods = listOf("xrayVersion", "getXrayState")
        for (method in noArgMethods) {
            val requestJson = """{"apiVersion":2,"method":"$method"}"""
            try {
                val response = LibXray.invoke(requestJson)
                Log.i(TAG, "NOARG-RESULT: [$method] request=$requestJson response=$response")
            } catch (t: Throwable) {
                Log.w(TAG, "NOARG-ERROR: [$method] request=$requestJson threw ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    @Test
    fun probeTestXrayConfigCandidates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val datDir = context.filesDir.absolutePath
        val configJsonString = probeConfigJson()

        val configFile = File(context.filesDir, "probe_config.json")
        configFile.writeText(configJsonString)

        // JSONObject.quote() menghasilkan string JSON-escaped SIAP PAKAI
        // (termasuk kutip pembuka/penutup) — dipakai langsung sebagai value,
        // bukan di-wrap manual, supaya newline/kutip di config JSON tidak
        // merusak envelope luar.
        val configJsonEscaped = JSONObject.quote(configJsonString)

        val candidates: List<Pair<String, String>> = listOf(
            "datDir+configPath(file)" to
                """{"apiVersion":2,"method":"testXray","datDir":"$datDir","configPath":"${configFile.absolutePath}"}""",
            "datDir+configJson(inline)" to
                """{"apiVersion":2,"method":"testXray","datDir":"$datDir","configJson":$configJsonEscaped}""",
            "datDir+config(inline)" to
                """{"apiVersion":2,"method":"testXray","datDir":"$datDir","config":$configJsonEscaped}"""
        )

        var anySuccess = false
        for ((label, requestJson) in candidates) {
            try {
                val response = LibXray.invoke(requestJson)
                val looksSuccessful = response.contains("\"success\":true") ||
                    response.contains("\"success\": true")
                if (looksSuccessful) {
                    anySuccess = true
                    Log.i(TAG, "TESTXRAY-WIN: [$label] response=$response")
                } else {
                    Log.i(TAG, "TESTXRAY-MISS: [$label] response=$response")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "TESTXRAY-ERROR: [$label] threw ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        Log.i(
            TAG,
            if (anySuccess)
                "PROBE ROUND 3 SELESAI — minimal 1 kandidat testXray sukses, cari TESTXRAY-WIN di atas."
            else
                "PROBE ROUND 3 SELESAI — 0 kandidat testXray sukses dari ${candidates.size} bentuk. " +
                    "Baca pesan error tiap TESTXRAY-MISS/-ERROR: kalau errornya soal FIELD " +
                    "(\"unknown method\"/field tidak dikenali) berarti nama field masih salah; " +
                    "kalau errornya soal ISI CONFIG (parse WireGuard/key format) berarti field " +
                    "sudah BENAR dan config-nya yang perlu diperbaiki — beda kelas temuan, " +
                    "catat mana yang terjadi sebelum coba kandidat baru."
        )
        // Sengaja TIDAK assertTrue — pola sama seperti round 1+2, hasil "0
        // sukses tapi error informatif" tetap data berguna untuk sesi
        // berikutnya, bukan kegagalan test.
    }
}
