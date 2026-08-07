package com.fdzaki.adshield.xray

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import libXray.LibXray
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v3.32.0 — libXray Android PoC roadmap langkah 2.5 (di antara "AAR
 * compile sukses" v3.31.0 dan "integrasi ke WarpTunnelManager" langkah 3).
 *
 * Bukan test fitur — ini test EMPIRIS untuk satu pertanyaan spesifik:
 * `LibXray.invoke(String): String` adalah dispatcher tunggal (dikonfirmasi
 * lewat bytecode `classes.jar` — LibXray.class HANYA punya method
 * `invoke(String):String`, tidak ada `xrayVersion()`/`getFreePorts()`
 * terpisah), tapi bentuk JSON envelope yang dikirim ke situ (field mana
 * yang jadi "aksi apa yang mau dijalankan") TIDAK ada di README/dokumentasi
 * publik XTLS/libXray manapun yang berhasil ditemukan — lihat
 * PROJECT_STATE.md v3.32.0 untuk kronologi riset lengkap.
 *
 * Daripada menebak sekali lalu menulis WarpTunnelManager di atas tebakan
 * itu (pola yang sudah 2x bikin krisis di proyek ini — DNS v3.9-v3.11),
 * test ini coba BEBERAPA bentuk envelope kandidat terhadap 1 aksi
 * read-only tanpa efek samping (`getFreePorts`, count=1 — cuma scan port
 * bebas di localhost, tidak butuh VPN/DialerController/ProcessFinder sama
 * sekali) dan catat mentah-mentah request+response tiap kandidat ke
 * Logcat. Sesi Claude berikutnya baca artifact logcat CI job
 * `libxray-invoke-probe`, cari baris `CANDIDATE-WIN:`, dan BARU dari situ
 * WarpTunnelManager/engine baru ditulis — berdasar bukti, bukan tebakan.
 *
 * Test ini SENGAJA tidak fail keras kalau semua kandidat gagal — itu tetap
 * hasil yang berguna (berarti field envelope-nya beda dari 9 kandidat di
 * bawah, bukan bug di test ini), yang penting logcat-nya lengkap dan bisa
 * dibaca sesi berikutnya tanpa perlu re-run apa pun.
 */
@RunWith(AndroidJUnit4::class)
class LibXrayInvokeProbeTest {

    companion object {
        private const val TAG = "LibXrayInvokeProbe"
    }

    // Setiap kandidat = 1 hipotesis bentuk envelope yang masuk akal dari
    // API permukaan yang KONFIRMED lewat bytecode (LibXrayInvokeRequest
    // cuma punya field APIVersion; GetFreePortsRequest cuma punya field
    // Count — getter/setter Java-nya getCount()/setCount(J), jadi nama
    // field Go-nya "Count", json tag-nya BELUM diketahui pasti apakah
    // "count" (lowerCamel, konvensi Go json standar) atau "Count"
    // (PascalCase, kalau strukturnya tidak punya tag json eksplisit).
    private val candidates: List<Pair<String, String>> = listOf(
        "name+count(lower)" to """{"name":"getFreePorts","count":1}""",
        "action+count(lower)" to """{"action":"getFreePorts","count":1}""",
        "method+count(lower)" to """{"method":"getFreePorts","count":1}""",
        "name+Count(Pascal)" to """{"name":"getFreePorts","Count":1}""",
        "name+nested-request" to """{"name":"getFreePorts","getFreePortsRequest":{"count":1}}""",
        "name+nested-data" to """{"name":"getFreePorts","data":{"count":1}}""",
        "flat-nested-only" to """{"getFreePortsRequest":{"count":1}}""",
        "apiVersion+name+count" to """{"apiVersion":1,"name":"getFreePorts","count":1}""",
        "dotted-name+count" to """{"name":"nodep.getFreePorts","count":1}"""
    )

    @Test
    fun probeInvokeEnvelopeShape() {
        // Paksa native lib (libgojni.so) ter-load lebih dulu lewat idiom
        // gomobile-bind standar, supaya kalau ADA yang gagal di titik ini
        // (bukan di invoke() itu sendiri), logcat jelas membedakan
        // "native lib gagal load" vs "envelope salah bentuk".
        try {
            LibXray.touch()
            Log.i(TAG, "LibXray.touch() OK — native lib berhasil ter-load.")
        } catch (t: Throwable) {
            Log.e(TAG, "LibXray.touch() GAGAL — native lib TIDAK ter-load, semua kandidat di bawah pasti gagal juga: ${t.javaClass.simpleName}: ${t.message}", t)
        }

        var anySuccess = false
        for ((label, requestJson) in candidates) {
            try {
                val response = LibXray.invoke(requestJson)
                val looksSuccessful = response.contains("\"success\":true") ||
                    response.contains("\"success\": true")
                if (looksSuccessful) {
                    anySuccess = true
                    Log.i(TAG, "CANDIDATE-WIN: [$label] request=$requestJson response=$response")
                } else {
                    Log.i(TAG, "CANDIDATE-MISS: [$label] request=$requestJson response=$response")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "CANDIDATE-ERROR: [$label] request=$requestJson threw ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        Log.i(
            TAG,
            if (anySuccess)
                "PROBE SELESAI — minimal 1 kandidat sukses, cari baris CANDIDATE-WIN di atas untuk bentuk envelope yang benar."
            else
                "PROBE SELESAI — 0 kandidat sukses dari ${candidates.size} bentuk. Bukan kegagalan test, ini hasil valid: " +
                    "envelope invoke() beda dari semua hipotesis di atas, perlu riset lanjutan (source Go libXray langsung) " +
                    "sebelum coba kandidat baru."
        )
        // Sengaja TIDAK assertTrue(anySuccess) — lihat KDoc kelas ini kenapa
        // test ini boleh "hijau" walau 0 kandidat sukses, selama logcat-nya
        // lengkap. Job CI yang menjalankan ini (libxray-invoke-probe) sudah
        // continue-on-error di level workflow untuk alasan yang sama.
    }
}
