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
 * v3.35.0 — ROUND 5, dipicu HASIL round 4 (v3.34.1, run CI KEDUA setelah
 * hotfix tag logcat) yang SEKARANG lengkap terbaca:
 *
 * 1. **Teori payload-nesting round 4 TERBUKTI BENAR untuk `getFreePorts`**:
 *    versi payload-nested (`apiVersion:2`) balas `data:{"ports":[41971,
 *    34425,41767]}` — port ASLI, 3 angka beda-beda, bukan kosong. Versi
 *    flat (repro persis `CANDIDATE-WIN` round 2) balas `data:{}` —
 *    KOSONG. Ini MENGKONFIRMASI dugaan round 4: `CANDIDATE-WIN` round 2
 *    dulu memang sukses-semu (vacuous) — `count` tidak pernah ke-bind ke
 *    struct Go karena flat, fungsi jalan dgn default kosong lalu balas
 *    `success:true` tanpa data. **Envelope BENAR = payload di-nest,
 *    bukan flat** — dikonfirmasi ganda sekarang (bukti positif getFreePorts
 *    ASLI, bukan cuma bukti negatif "flat gagal").
 * 2. **`apiVersion` HARUS 2, bukan 1** — kandidat `apiVersion:1` (baik
 *    getFreePorts maupun testXray) balas `"unsupported apiVersion"` PERSIS
 *    seperti round 1. README resmi yang dikutip round 4 (`"apiVersion":1`
 *    di contoh) TERNYATA cuma contoh ilustratif generik, BUKAN nilai yang
 *    AAR build ini terima — dokumentasi vs implementasi AAR ini beda,
 *    catat ini biar sesi depan tidak coba apiVersion:1 lagi tanpa alasan
 *    baru.
 * 3. **`testXray` TETAP gagal EOF PERSIS SAMA** walau payload sudah
 *    di-nest + apiVersion:2 benar (2 kombinasi field yang sudah kebukti
 *    benar dari poin 1+2 di atas) — jadi teori "root cause = flat vs
 *    nested" SALAH untuk `testXray` spesifik, walau benar utk
 *    `getFreePorts`. Riset error string `"infra/conf/serial: failed to
 *    read config file > EOF"` (web search, laporan bug xray-core lain)
 *    mengonfirmasi: error ini muncul spesifik saat Xray-core BERHASIL
 *    `os.Open()` file tapi file-nya KOSONG (0 byte) saat dibaca — bukan
 *    "file not found", bukan "field tidak dikenal". Dua hipotesis baru:
 *    (a) file `probe_config_r4.json` yang ditulis `configFile.writeText()`
 *    entah kenapa KOSONG/belum ter-flush ke disk pas `invoke()` dipanggil
 *    (walau `writeText` di Kotlin/JVM harusnya synchronous blocking I/O);
 *    (b) config WireGuard placeholder round 3/4 ada masalah spesifik
 *    (bukan soal file kosong beneran, tapi Xray-core versi ini decode
 *    config-nya lewat jalur berbeda yang kebetulan lempar pesan generik
 *    yang SAMA).
 *
 * Round ini murni DIAGNOSTIK memisahkan 2 hipotesis di atas, TIDAK
 * menambah kandidat envelope baru (envelope sudah pasti benar dari poin
 * 1+2):
 * 1. `probeConfigFileBytesBeforeInvoke` — baca balik file dari Kotlin
 *    (`file.length()`, cuplikan isi) PERSIS sebelum `invoke()` dipanggil,
 *    log eksplisit byte count — kalau length()==0 di titik itu, hipotesis
 *    (a) TERBUKTI (bug di sisi Kotlin/timing, bukan libXray). Kalau
 *    length() > 0 tapi tetap EOF, hipotesis (a) GUGUR, condong ke (b).
 * 2. `probeTestXrayMinimalFreedomConfig` — ulang `testXray` payload-nested
 *    + apiVersion:2 (envelope yang sudah pasti benar), tapi config-nya
 *    diganti config Xray MINIMAL valid (1 outbound `freedom`, TANPA
 *    WireGuard sama sekali) — kalau config minimal ini SUKSES (atau
 *    minimal error BEDA, bukan EOF baca file), berarti mekanisme baca
 *    file/payload-nya sendiri OK, masalahnya spesifik di config WireGuard
 *    round 3/4 punya kita. Kalau tetap PERSIS EOF yang sama, masalahnya
 *    di jalur baca file itu sendiri (bukan isi config apa pun).
 *
 * Sama seperti round 1-4: tidak fail keras kalau semua kandidat gagal.
 */
@RunWith(AndroidJUnit4::class)
class LibXrayInvokeProbeRound5Test {

    companion object {
        private const val TAG = "LibXrayInvokeProbeR5"

        // Config Xray minimal VALID (1 outbound freedom, tanpa WireGuard)
        // — dipakai sebagai kontrol negatif: kalau config sesederhana ini
        // pun tetap EOF, masalahnya BUKAN isi config WireGuard round 3/4.
        private fun minimalFreedomConfigJson(): String =
            """{"outbounds":[{"protocol":"freedom","tag":"direct"}]}"""
    }

    private fun invokeAndLog(tag: String, prefix: String, label: String, requestJson: String): Boolean {
        return try {
            val response = LibXray.invoke(requestJson)
            val ok = response.contains("\"success\":true") || response.contains("\"success\": true")
            if (ok) {
                Log.i(tag, "$prefix-WIN: [$label] request=$requestJson response=$response")
            } else {
                Log.i(tag, "$prefix-MISS: [$label] request=$requestJson response=$response")
            }
            ok
        } catch (t: Throwable) {
            Log.w(tag, "$prefix-ERROR: [$label] request=$requestJson threw ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    @Test
    fun probeConfigFileBytesBeforeInvoke() {
        try {
            LibXray.touch()
            Log.i(TAG, "LibXray.touch() OK — native lib berhasil ter-load.")
        } catch (t: Throwable) {
            Log.e(TAG, "LibXray.touch() GAGAL: ${t.javaClass.simpleName}: ${t.message}", t)
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val datDir = context.filesDir.absolutePath
        // Config sama seperti round 3/4 (WireGuard placeholder) supaya
        // hasil round ini benar-benar comparable 1:1 dengan EOF round 3/4.
        val configJsonString = """
            {
              "outbounds": [{
                "protocol": "wireguard",
                "settings": {
                  "secretKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                  "address": ["172.16.0.2/32"],
                  "peers": [{
                    "endpoint": "engage.cloudflareclient.com:2408",
                    "publicKey": "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
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

        val configFile = File(context.filesDir, "probe_config_r5.json")
        configFile.writeText(configJsonString)

        // Baca BALIK dari disk, PERSIS sebelum invoke() — bukti langsung
        // apakah file benar-benar berisi data di titik ini.
        val lengthOnDisk = configFile.length()
        val rereadContent = configFile.readText()
        Log.i(
            TAG,
            "FILE-CHECK: path=${configFile.absolutePath} length()=$lengthOnDisk " +
                "readText().length=${rereadContent.length} " +
                "preview=\"${rereadContent.take(60).replace("\n", "\\n")}...\""
        )

        val configPathEscaped = JSONObject.quote(configFile.absolutePath)
        val datDirEscaped = JSONObject.quote(datDir)
        val requestJson =
            """{"apiVersion":2,"method":"testXray","payload":{"datDir":$datDirEscaped,"configPath":$configPathEscaped}}"""

        invokeAndLog(TAG, "TESTXRAY-FILECHECK", "v2+payload(reread-verified)", requestJson)
    }

    @Test
    fun probeTestXrayMinimalFreedomConfig() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val datDir = context.filesDir.absolutePath
        val configFile = File(context.filesDir, "probe_config_r5_minimal.json")
        configFile.writeText(minimalFreedomConfigJson())
        Log.i(TAG, "FILE-CHECK-MINIMAL: length()=${configFile.length()}")

        val configPathEscaped = JSONObject.quote(configFile.absolutePath)
        val datDirEscaped = JSONObject.quote(datDir)
        val requestJson =
            """{"apiVersion":2,"method":"testXray","payload":{"datDir":$datDirEscaped,"configPath":$configPathEscaped}}"""

        val success = invokeAndLog(TAG, "TESTXRAY-MINIMAL", "v2+payload+freedom-only", requestJson)
        Log.i(
            TAG,
            if (success)
                "PROBE ROUND 5 MINIMAL SELESAI — SUKSES dgn config freedom-only. Masalah round 3/4 ADA di " +
                    "config WireGuard placeholder kita, bukan di mekanisme baca file/payload."
            else
                "PROBE ROUND 5 MINIMAL SELESAI — config MINIMAL (freedom-only, tanpa WireGuard sama sekali) " +
                    "TETAP gagal. Kalau errornya PERSIS \"infra/conf/serial ... EOF\" yang sama, masalahnya BUKAN " +
                    "isi config sama sekali — ada sesuatu di jalur baca file itu sendiri (permission, cgo string " +
                    "marshaling, atau datDir yang divalidasi lebih dulu sebelum configPath dibaca) yang perlu " +
                    "diriset lebih dalam sebelum coba WireGuard config lagi."
        )
    }
}
