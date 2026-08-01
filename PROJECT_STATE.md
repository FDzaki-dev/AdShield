# PROJECT_STATE.md (Claude-facing, bukan untuk user)

Baca file ini SEBELUM lanjut kerja di proyek ini pada sesi baru mana pun.

## Status terakhir
- **Versi terakhir selesai: v1.2.0** (pematangan fitur, 2026-08-01)
- Belum pernah di-push ke GitHub — v1.0.0 gagal build, belum ada konfirmasi
  versi mana yang sudah user push. Sinkronkan status ini kalau user kasih
  tau progress push-nya.
- Belum pernah dites di device asli (belum ada feedback bug dari user).

## Keputusan arsitektur utama (JANGAN dilanggar tanpa diskusi eksplisit)

1. **VPN hanya menunnel DNS, bukan full-tunnel.**
   `Builder.addRoute(Constants.VPN_ROUTE, 32)` HANYA mendaftarkan rute ke
   `10.111.222.1/32`, bukan `0.0.0.0/0`. Ini disengaja — kalau diubah ke
   full-tunnel, semua trafik app lain (video, banking, dll) akan lewat proses
   parsing kita yang cuma didesain untuk DNS, dan akan rusak/lambat drastis.
   Kalau suatu saat mau upgrade ke pemblokiran berbasis IP/SNI (bukan cuma
   DNS), itu perubahan arsitektur besar — tanya user dulu apa saja behavior
   yang harus tetap sama.

2. **Minimal SDK 24, compile/target SDK 34** — konsisten dengan
   GalleryCleaner & device user (Infinix XOS).

3. **Nama paket:** `com.fdzaki.adshield`. Jangan diganti tanpa alasan kuat
   (mengubah applicationId = install baru, kehilangan semua data user lama).

4. **Blocklist merge logic ada di `BlocklistManager`, bukan di VpnService.**
   VpnService hanya panggil `blocklist.isBlocked(domain)` — jangan taruh
   logic blocking langsung di packet loop, supaya loop tetap ringan/cepat.

4b. **Matching = exact-match by default, wildcard HANYA lewat prefix
   eksplisit `*.domain.com`.** Ini keputusan sadar per permintaan user
   (v1.1.0) — JANGAN kembalikan ke "parent-domain walk" (cek d, lalu
   parent-nya, lalu parent-nya lagi dst) karena itu persis yang bikin user
   komplain over-blocking. Kalau nambah domain baru ke
   `blocklist_default.txt`, defaultnya exact-match; kasih prefix `*.` HANYA
   kalau domain itu 100% didedikasikan untuk ad-serving dan tidak mungkin
   dipakai app lain untuk fungsi legit.

5. **Whitelist per-app kini terhubung ke UID nyata (SELESAI di v1.2.0).**
   `AdBlockVpnService.isFromWhitelistedApp()` pakai
   `ConnectivityManager.getConnectionOwnerUid()` (API 29+ saja — di bawah
   itu OS tidak expose API ini, jadi whitelist per-app tidak berpengaruh
   di Android <10, ini dijelaskan di UI bukan silent-fail). Kalau ada
   laporan whitelist tidak berfungsi dari user, cek dulu versi Android
   device-nya sebelum asumsikan ada bug baru.

5b. **Critical allowlist (v1.2.0)**: `BlocklistManager.criticalAllowlist`
   berisi domain esensial konektivitas (captive portal, time sync) yang
   SELALU diizinkan, override semua blocklist/aturan kustom. JANGAN hapus
   set ini walau kelihatan "tidak dipakai" — ini jaring pengaman terhadap
   kelas bug "HP kelihatan tidak ada internet" yang sangat membingungkan
   user untuk didiagnosis. Kalau nambah domain esensial baru ke sini,
   dokumentasikan alasannya di komentar kode.

## Riwayat insiden kronologis

- **2026-08-01 (v1.2.0, ditemukan saat repackaging)**: Ketemu 3 direktori
  sampah berisi nama literal `{a,b,c}` di `app/src/main/java/.../adshield/`
  dan `app/src/main/res/` — sisa dari command `mkdir -p ... {a,b,c}` di
  setup awal proyek yang brace-expansion-nya gagal (kemungkinan shell
  environment tidak mendukung penuh). Direktori ini KOSONG (tidak ada
  source file di dalamnya) jadi tidak pernah memengaruhi build, tapi sudah
  ikut ter-zip di v1.0.0–v1.1.0 tanpa terdeteksi self-verifikasi
  sebelumnya (checklist self-verifikasi saat itu cuma cek brace balance +
  jumlah file, bukan nama direktori aneh). Sudah dibersihkan. Pelajaran:
  self-verifikasi ke depan sebaiknya juga cek `find . -type d -name "*{*"`
  atau semacamnya untuk artefak shell yang salah eksekusi.
- **2026-08-01 (v1.2.0)**: User minta fokus pematangan, bukan fitur baru.
  Ditutup: (1) whitelist per-app disambungkan ke UID nyata — sebelumnya
  hanya separuh jalan, (2) critical allowlist ditambahkan untuk cegah
  false-block domain esensial konektivitas, (3) DNS forward sekarang
  fallback ke resolver kedua kalau yang pertama gagal. Bukan insiden/bug
  ditemukan dari user — ini kerja pematangan proaktif atas gap yang sudah
  tercatat sebelumnya.
- **2026-08-01 (v1.1.0)**: User komplain matching parent-domain "kebablasan"
  memblokir host yang bukan seharusnya (mirip komplain umum terhadap
  hosts-file blocker naif). Diganti total ke exact-match + wildcard
  eksplisit (lihat keputusan arsitektur #4b). Blocklist bawaan juga
  dipangkas, buang tool APM/analytics umum yang bukan murni ad-serving.
- **2026-08-01 (v1.0.1)**: CI `compileReleaseKotlin` gagal —
  `WhitelistScreen.kt` pakai import salah `androidx.compose.ui.graphics.
  drawable.toBitmap` (package tidak punya fungsi ini). `toBitmap()` yang
  benar berasal dari `androidx.core.graphics.drawable` (core-ktx). Sudah
  diperbaiki. Pelajaran: saat pakai extension function dari core-ktx di
  layar Compose, jangan asumsikan namespace-nya ikut `androidx.compose.*`.
- **2026-08-01 (v1.0.0)**: Proyek dibuat dari nol. Tidak ada insiden pada
  batch ini.

## Struktur package singkat

```
vpn/           AdBlockVpnService (VpnService, packet loop), DnsPacket (parser/builder)
data/          BlocklistManager (in-memory), SettingsRepository (DataStore),
               InstalledAppsRepository, data/db/ (Room: DomainLogEntity/Dao/AppDatabase)
receiver/      BootReceiver, RestartReceiver
ui/            MainViewModel, ui/screens/ (Home, Whitelist, Rules, Logs), ui/theme/
util/          Constants (semua magic number/string terpusat di sini)
```

## Yang HARUS dikerjakan di batch berikutnya (prioritas)

1. Uji di device fisik: apakah watchdog AlarmManager di XOS Ted benar-benar
   mencegah service dibunuh saat app di-swipe dari Recents.
2. Uji whitelist per-app di device fisik Ted (Infinix XOS) — cek dulu versi
   Android-nya di atas/di bawah 10 sebelum menyimpulkan bug kalau ada laporan.
3. Belum ada unit test sama sekali — pertimbangkan test untuk `DnsPacket`
   parsing (paling kritis, paling gampang salah) dan `BlocklistManager`
   exact/wildcard matching (termasuk critical allowlist).
4. Tidak ada `gradle-wrapper.jar` binary di repo ini (dibuat tanpa akses
   internet). CI pakai `gradle/actions/setup-gradle` (menginstal Gradle
   langsung, tidak butuh wrapper). Kalau user mau pakai `./gradlew` secara
   lokal, jalankan `gradle wrapper --gradle-version 8.7` sekali di
   Termux/device dengan Gradle terpasang untuk generate wrapper jar-nya.
5. (Belum prioritas, jangan dikerjakan kecuali diminta): deteksi DoH,
   import blocklist dari URL custom, statistik per-app.
