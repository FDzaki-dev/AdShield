# PROJECT_STATE.md (Claude-facing, bukan untuk user)

Baca file ini SEBELUM lanjut kerja di proyek ini pada sesi baru mana pun.

## Status terakhir
- **Versi terakhir selesai: v1.1.0** (matching presisi, 2026-08-01)
- Belum pernah di-push ke GitHub — v1.0.0 gagal build, v1.0.1 fix belum
  dikonfirmasi user sudah di-push atau belum saat v1.1.0 ini dibuat.
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

5. **Whitelist per-app BELUM terhubung ke UID paket nyata.**
   `BlocklistManager.isAppWhitelisted()` dan `setWhitelistedApps()` sudah
   ada tapi **belum dipanggil** dari packet loop di `AdBlockVpnService` —
   karena mendapatkan UID pemilik query DNS lokal butuh
   `ConnectivityManager.getConnectionOwnerUid()` (API 29+, butuh info
   local+remote address/port UDP) yang belum diimplementasikan. Saat ini
   whitelist per-app HANYA tersimpan di data layer tapi TIDAK BERPENGARUH ke
   hasil blocking sungguhan. **Ini gap yang harus ditutup di batch
   berikutnya — jangan asumsikan whitelist sudah berfungsi penuh sebelum
   ini diperbaiki.**

## Riwayat insiden kronologis

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

1. **Sambungkan per-app whitelist ke UID nyata** (lihat poin 5 di atas) —
   ini fitur yang diminta user secara eksplisit ("Advanced: whitelist
   per-app") tapi implementasinya baru separuh jalan.
2. Uji di device fisik: apakah watchdog AlarmManager di XOS Ted benar-benar
   mencegah service dibunuh saat app di-swipe dari Recents.
3. Belum ada unit test sama sekali — pertimbangkan test untuk `DnsPacket`
   parsing (paling kritis, paling gampang salah) dan `BlocklistManager`
   parent-domain matching.
4. Tidak ada `gradle-wrapper.jar` binary di repo ini (dibuat tanpa akses
   internet). CI pakai `gradle/actions/setup-gradle` (menginstal Gradle
   langsung, tidak butuh wrapper). Kalau user mau pakai `./gradlew` secara
   lokal, jalankan `gradle wrapper --gradle-version 8.7` sekali di
   Termux/device dengan Gradle terpasang untuk generate wrapper jar-nya.
