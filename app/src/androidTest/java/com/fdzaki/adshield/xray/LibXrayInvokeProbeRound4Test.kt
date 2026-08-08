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
 * v3.34.0 — ROUND 4, dipicu HASIL round 3 (v3.33.0): ketiga kandidat
 * `testXray` (configPath/configJson/config, semuanya FLAT di top-level)
 * gagal dengan error IDENTIK: `"infra/conf/serial: failed to read config
 * file > EOF"` — bukan "unknown method"/field-tidak-dikenal, tapi juga
 * BUKAN error parse-config-invalid yang diharapkan kalau field-nya benar.
 * Error "EOF" dari `infra/conf/serial` berarti Xray-core mencoba BACA file
 * config tapi dapat STRING KOSONG — konsisten dengan payload yang tidak
 * ke-bind sama sekali ke struct Go `TestXrayRequest`, walau nama field
 * (`datDir`/`configPath`) sudah PERSIS cocok dengan source resmi.
 *
 * ROOT CAUSE ditemukan lewat riset ulang (README resmi XTLS/libXray,
 * bagian "libXray exposes a single structured entrypoint", dibaca LANGSUNG
 * dari repo, bukan cache lama): bentuk request `Invoke()` yang didokumentasikan
 * adalah
 * ```
 * { "apiVersion": 1, "method": "runXray", "payload": { "configPath": "..." } }
 * ```
 * — field method (`datDir`, `configPath`, dst) HARUS di-nest di bawah key
 * `"payload"`, BUKAN flat di top-level seperti semua kandidat round 1-3.
 * Ini juga mempertanyakan ulang `CANDIDATE-WIN` round 2
 * (`{"apiVersion":2,"method":"getFreePorts","count":1}` →
 * `{"success":true,"data":{},"error":""}`): `data` KOSONG (`{}`), bukan
 * daftar port asli — mencurigakan sebagai "sukses semu" (param `count`
 * flat tidak ke-bind, fungsi jalan dengan default/kosong lalu balas
 * `success:true` vacuous), BUKAN bukti valid bahwa envelope flat benar.
 * Round ini membuktikan/menyangkal itu langsung dengan membandingkan
 * `getFreePorts` FLAT vs NESTED-payload side by side.
 *
 * 3 pertanyaan round ini:
 * 1. **`probeGetFreePortsFlatVsPayload`** — ulang `getFreePorts` count=3,
 *    flat (persis kandidat WIN round 2) vs nested `"payload":{"count":3}`,
 *    apiVersion 2 utk keduanya. Kalau versi payload balas `data` BERISI
 *    (misal daftar 3 port), itu bukti flat round 2 memang sukses-semu.
 * 2. **`probeTestXrayPayloadNested`** — 4 kandidat `testXray` dengan
 *    `datDir`+`configPath` (nama field PERSIS sama seperti round 3,
 *    sudah dikonfirmasi cocok source Go) tapi kali ini DI-NEST di bawah
 *    `"payload"`, kombinasi `apiVersion` 1 (sesuai contoh README apa
 *    adanya) dan 2 (sesuai yang dipakai no-arg methods round 1-3 yang
 *    TERBUKTI beneran jalan — `xrayVersion` balas versi asli
 *    `"26.7.28"`, bukan data kosong, jadi apiVersion:2 utk no-arg TETAP
 *    dipercaya benar, bukan di-drop).
 * 3. **`probeNoArgPayloadOmittedVsEmpty`** — sanity tambahan murah:
 *    `xrayVersion` dengan `"payload":{}` eksplisit vs tanpa key `payload`
 *    sama sekali, keduanya apiVersion 2 — pastikan no-arg method tidak
 *    butuh `payload` kosong secara eksplisit (harusnya sama-sama sukses,
 *    kalau beda berarti ada validasi wajib-ada-payload yang belum
 *    diketahui).
 *
 * Config WireGuard tetap placeholder (lihat KDoc [LibXrayInvokeProbeRound3Test]
 * untuk alasan lengkap kenapa `secretKey`/`reserved` asli belum dipakai).
 *
 * Sama seperti round 1-3: tidak fail keras kalau semua kandidat gagal.
 */
@RunWith(AndroidJUnit4::class)
class LibXrayInvokeProbeRound4Test {

    companion object {
        private const val TAG = "LibXrayInvokeProbeR4"

        private const val WARP_PEER_PUBLIC_KEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
        private const val WARP_ENDPOINT = "engage.cloudflareclient.com:2408"
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

    private fun runCandidates(tag: String, prefix: String, candidates: List<Pair<String, String>>): Boolean {
        var anySuccess = false
        for ((label, requestJson) in candidates) {
            try {
                val response = LibXray.invoke(requestJson)
                val looksSuccessful = response.contains("\"success\":true") ||
                    response.contains("\"success\": true")
                if (looksSuccessful) {
                    anySuccess = true
                    Log.i(tag, "$prefix-WIN: [$label] request=$requestJson response=$response")
                } else {
                    Log.i(tag, "$prefix-MISS: [$label] request=$requestJson response=$response")
                }
            } catch (t: Throwable) {
                Log.w(tag, "$prefix-ERROR: [$label] request=$requestJson threw ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        return anySuccess
    }

    @Test
    fun probeGetFreePortsFlatVsPayload() {
        try {
            LibXray.touch()
            Log.i(TAG, "LibXray.touch() OK — native lib berhasil ter-load.")
        } catch (t: Throwable) {
            Log.e(TAG, "LibXray.touch() GAGAL: ${t.javaClass.simpleName}: ${t.message}", t)
        }

        val candidates = listOf(
            "flat(round2-win-repro)" to """{"apiVersion":2,"method":"getFreePorts","count":3}""",
            "payload-nested(v2)" to """{"apiVersion":2,"method":"getFreePorts","payload":{"count":3}}""",
            "payload-nested(v1)" to """{"apiVersion":1,"method":"getFreePorts","payload":{"count":3}}"""
        )
        runCandidates(TAG, "PORTS", candidates)
        Log.i(
            TAG,
            "PROBE getFreePorts SELESAI — bandingkan field \"data\" tiap baris PORTS-WIN/-MISS di atas: " +
                "kalau versi payload-nested balas data BERISI (daftar port) sementara versi flat balas " +
                "data:{} kosong, itu bukti CANDIDATE-WIN round 2 dulu sukses-semu (vacuous), bukan envelope " +
                "flat yang benar — payload-nested yang harus dipakai seterusnya."
        )
    }

    @Test
    fun probeTestXrayPayloadNested() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val datDir = context.filesDir.absolutePath
        val configJsonString = probeConfigJson()
        val configFile = File(context.filesDir, "probe_config_r4.json")
        configFile.writeText(configJsonString)
        val configPathEscaped = JSONObject.quote(configFile.absolutePath)
        val datDirEscaped = JSONObject.quote(datDir)

        val candidates = listOf(
            "v2+payload{datDir,configPath}" to
                """{"apiVersion":2,"method":"testXray","payload":{"datDir":$datDirEscaped,"configPath":$configPathEscaped}}""",
            "v1+payload{datDir,configPath}" to
                """{"apiVersion":1,"method":"testXray","payload":{"datDir":$datDirEscaped,"configPath":$configPathEscaped}}"""
        )

        val anySuccess = runCandidates(TAG, "TESTXRAY", candidates)
        Log.i(
            TAG,
            if (anySuccess)
                "PROBE ROUND 4 testXray SELESAI — sukses, cari TESTXRAY-WIN di atas. Config masih placeholder " +
                    "(secretKey/reserved nol) — kalau responsnya bilang config VALID meski placeholder, itu " +
                    "aneh (harusnya reject di layer WireGuard key validation) - catat isi response persis."
            else
                "PROBE ROUND 4 testXray SELESAI — 0/${candidates.size} sukses. Kalau error TESTXRAY-MISS masih " +
                    "PERSIS \"infra/conf/serial ... EOF\" walau sudah di-nest payload, berarti dugaan root cause " +
                    "round ini SALAH (bukan soal nesting) — datDir/configPath perlu dicek lagi dari sisi lain " +
                    "(mis. apakah configFile beneran ada di path itu SAAT invoke() dipanggil, race condition " +
                    "writeText async, dsb). Kalau errornya BEDA (soal isi WireGuard config, bukan EOF baca file), " +
                    "berarti nesting payload sudah BENAR dan config placeholder-nya yang perlu diganti next round."
        )
    }

    @Test
    fun probeNoArgPayloadOmittedVsEmpty() {
        val candidates = listOf(
            "payload-omitted" to """{"apiVersion":2,"method":"xrayVersion"}""",
            "payload-empty-object" to """{"apiVersion":2,"method":"xrayVersion","payload":{}}"""
        )
        runCandidates(TAG, "NOARG-PAYLOAD", candidates)
    }
}
