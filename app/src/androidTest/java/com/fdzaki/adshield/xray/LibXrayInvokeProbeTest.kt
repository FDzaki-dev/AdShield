package com.fdzaki.adshield.xray

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import libXray.LibXray
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v3.32.3 — ROUND 2 kandidat envelope, setelah v3.32.0 run PERTAMA yang
 * benar-benar tereksekusi (2 hotfix CI shell sebelumnya, v3.32.1/v3.32.2,
 * murni infrastruktur — lihat CHANGELOG.md) memberi hasil: 0/9 kandidat
 * sukses, TAPI kesembilan-sembilannya balas error yang SAMA PERSIS:
 * `"unsupported apiVersion"` — termasuk kandidat #8 yang eksplisit kirim
 * `"apiVersion":1`. Itu clue nyata, bukan buntu: bukan field `name`/
 * `action`/nesting yang salah, tapi NILAI `apiVersion`-nya. Riset lanjutan
 * (web search, dokumentasi resmi XTLS/libXray) mengonfirmasi:
 * **`Invoke` sekarang cuma terima `apiVersion: 2`** (bukan 1) — versi AAR
 * yang di-commit `app/libs/libXray.aar` (dibangun v3.31.0 dari `main`
 * branch libXray saat itu) sudah di versi yang butuh 2, bukan asumsi lama
 * kita di v3.32.0 yang coba 1.
 *
 * Kandidat ROUND 2 di bawah SEMUA pakai `apiVersion: 2` (fix dari akar
 * masalah round 1), tetap variasi bentuk `name`/`action`/nesting yang
 * SAMA seperti round 1 (belum tentu bentuk itu juga benar, cuma
 * apiVersion-nya yang sudah pasti diperbaiki) + 1 kandidat baru
 * `APIVersion` PascalCase (jaga-jaga kalau field Go-nya sebenarnya tidak
 * punya json tag eksplisit, walau dokumentasi resmi menulis "apiVersion"
 * lowerCamel).
 *
 * Bukan test fitur — ini test EMPIRIS. Sesi Claude berikutnya baca
 * artifact logcat CI job `libxray-invoke-probe`, cari baris
 * `CANDIDATE-WIN:`, dan BARU dari situ WarpTunnelManager/engine baru
 * ditulis — berdasar bukti, bukan tebakan.
 *
 * Test ini SENGAJA tidak fail keras kalau semua kandidat gagal — itu tetap
 * hasil yang berguna, yang penting logcat-nya lengkap dan bisa dibaca
 * sesi berikutnya tanpa perlu re-run apa pun.
 */
@RunWith(AndroidJUnit4::class)
class LibXrayInvokeProbeTest {

    companion object {
        private const val TAG = "LibXrayInvokeProbe"
    }

    // ROUND 2 (v3.32.3) — semua kandidat sekarang pakai "apiVersion":2,
    // fix dari root cause round 1 (README resmi: "Invoke currently
    // accepts only apiVersion: 2"). Variasi name/action/nesting field
    // dipertahankan sama seperti round 1 (belum ada bukti baru soal itu),
    // + 1 kandidat baru APIVersion PascalCase (kandidat #10) jaga-jaga.
    private val candidates: List<Pair<String, String>> = listOf(
        "v2+name+count(lower)" to """{"apiVersion":2,"name":"getFreePorts","count":1}""",
        "v2+action+count(lower)" to """{"apiVersion":2,"action":"getFreePorts","count":1}""",
        "v2+method+count(lower)" to """{"apiVersion":2,"method":"getFreePorts","count":1}""",
        "v2+name+Count(Pascal)" to """{"apiVersion":2,"name":"getFreePorts","Count":1}""",
        "v2+name+nested-request" to """{"apiVersion":2,"name":"getFreePorts","getFreePortsRequest":{"count":1}}""",
        "v2+name+nested-data" to """{"apiVersion":2,"name":"getFreePorts","data":{"count":1}}""",
        "v2+flat-nested-only" to """{"apiVersion":2,"getFreePortsRequest":{"count":1}}""",
        "v2+dotted-name+count" to """{"apiVersion":2,"name":"nodep.getFreePorts","count":1}""",
        "v2+action-as-key+count" to """{"apiVersion":2,"getFreePorts":{"count":1}}""",
        "APIVersion(Pascal)+name+count" to """{"APIVersion":2,"name":"getFreePorts","count":1}"""
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
