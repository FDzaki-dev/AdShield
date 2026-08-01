# Changelog

## v1.2.0 — Pematangan fitur, fokus anti-salah-blokir (2026-08-01)

Tidak ada fitur baru — murni menyelesaikan/mengeraskan fitur yang sudah
diumumkan tapi belum tuntas, plus menutup kelas false-positive yang paling
membingungkan:

- **Critical allowlist**: domain esensial konektivitas Android (captive
  portal check, connectivity check, time sync — `connectivitycheck.
  gstatic.com`, `clients3.google.com`, `time.android.com`, dll) sekarang
  SELALU diizinkan, tidak bisa ikut terblokir oleh blocklist bawaan maupun
  aturan kustom apa pun. Ini menutup kelas bug paling membingungkan: HP
  terlihat "tidak ada internet" padahal cuma DNS captive-portal check yang
  gagal ke-resolve.
- **Whitelist per-app benar-benar berfungsi sekarang.** Sebelumnya toggle
  di WhitelistScreen tersimpan di data layer tapi TIDAK berpengaruh ke
  hasil blocking sungguhan (lihat PROJECT_STATE.md v1.1.0). Sekarang
  `AdBlockVpnService` resolve UID pengirim query lewat
  `ConnectivityManager.getConnectionOwnerUid()` (API 29+, dengan cache
  per-UID biar tidak query PackageManager berulang) dan benar-benar
  melewatkan blocking untuk app yang di-whitelist. Di bawah Android 10,
  fitur ini otomatis tidak aktif (dijelaskan di UI, bukan gagal diam-diam).
- **DNS forwarding fallback multi-resolver.** Sebelumnya hanya mencoba
  resolver pertama (1.1.1.1) — kalau timeout, query langsung di-drop dan
  terlihat identik dengan "diblokir" dari sudut pandang app yang query.
  Sekarang mencoba semua resolver di `Constants.UPSTREAM_DNS_SERVERS`
  (1.1.1.1 → 8.8.8.8) sebelum menyerah, jadi resolver yang lagi bermasalah
  tidak menyamar jadi "salah blokir".

Tidak ada perubahan pada `DnsPacket`, UI screens (selain 1 baris penjelasan
di WhitelistScreen), atau matching logic dari v1.1.0.

## v1.1.0 — Matching presisi ala Cloudflare 1.1.1.1 (2026-08-01)

**Perubahan cara kerja inti (BlocklistManager) — bukan cuma bugfix:**

- **Ganti strategi matching dari "parent-domain walk" ke exact-match +
  wildcard eksplisit.** Sebelumnya, blokir `contoh.com` otomatis ikut
  blokir SEMUA subdomain-nya (`apa.contoh.com`, `x.y.contoh.com`, dst) —
  ini bisa over-block domain CDN/infrastruktur yang dipakai bareng konten
  legit. Sekarang: `domain.com` di blocklist = exact match saja.
  `*.domain.com` = wildcard, baru ikut blokir semua subdomain. Berlaku
  untuk blocklist bawaan MAUPUN aturan kustom user.
- **Blocklist bawaan dipangkas & dikurasi ulang** (`blocklist_default.txt`):
  dihapus semua tool crash-reporting/APM (Crashlytics, Sentry, Bugsnag,
  New Relic) dan product-analytics umum (Mixpanel, Amplitude, Segment,
  Hotjar, FullStory) — domain ini bisa mematahkan fitur app (laporan crash,
  A/B testing) tanpa manfaat blokir iklan. Domain ad-network murni yang
  memang didedikasikan untuk ad-serving ditandai wildcard (`*.`) supaya
  cakupannya tetap efektif; Google Analytics/Tag Manager dibuat exact-match
  saja karena beberapa subdomainnya dipakai untuk fungsi non-iklan.
- **RulesScreen**: hint teks diperbarui menjelaskan sintaks `*.domain.com`
  untuk wildcard, dan penjelasan bahwa allow-list selalu menang atas semua
  aturan blokir (termasuk wildcard bawaan) — jalan keluar cepat kalau ada
  false positive.

Tidak ada perubahan pada engine VPN (`AdBlockVpnService`/`DnsPacket`) —
murni perubahan di layer matching/data.

## v1.0.1 — Perbaikan build gagal (2026-08-01)

- **Fix:** `WhitelistScreen.kt` gagal kompilasi di CI —
  `Unresolved reference: toBitmap`. Penyebab: import salah
  (`androidx.compose.ui.graphics.drawable.toBitmap`, package yang tidak
  punya fungsi ini). Diperbaiki ke `androidx.core.graphics.drawable.toBitmap`
  (dari dependency `core-ktx` yang memang sudah ada di `build.gradle.kts`).
  Tidak ada perubahan behavior/fitur, murni perbaikan import.

## v1.0.0 — Rilis awal (2026-08-01)

Arsitektur lengkap dari nol:

**Core engine**
- `AdBlockVpnService`: VpnService lokal, hanya rute DNS (`10.111.222.1/32`)
  yang ditunnel — trafik lain tidak disentuh
- `DnsPacket`: parser manual paket IPv4/UDP/DNS + builder respons blokir
  (A record 0.0.0.0) + wrapper untuk relay balasan resolver asli
- Forward query yang tidak diblokir ke 1.1.1.1 / 8.8.8.8 via socket yang
  di-`protect()` agar tidak looping ke VPN sendiri

**Persistensi background**
- Foreground service + `START_STICKY`
- `BootReceiver`: auto-start setelah reboot jika sebelumnya aktif
- `RestartReceiver` + AlarmManager watchdog: dipicu dari `onTaskRemoved()`,
  jaring pengaman untuk OEM battery manager agresif (XOS/MIUI/ColorOS) yang
  suka membunuh foreground service walau seharusnya dilindungi
- Tombol shortcut minta pengecualian battery optimization di Home screen

**Blocklist & aturan**
- `BlocklistManager`: in-memory hash set, O(1) lookup, cek parent-domain
  (blokir domain induk otomatis cakup semua subdomain)
- `blocklist_default.txt`: ~100+ domain ads/tracker populer bawaan
  (Google/Meta ad network, Unity Ads, AppLovin, Taboola, Criteo, dll)
- Custom block/allow rules per-domain (override), tersimpan di DataStore

**Whitelist per-app**
- `InstalledAppsRepository`: daftar app terinstal (nama + ikon)
- Toggle per-app di `WhitelistScreen` untuk bypass total dari pemblokiran

**Log domain**
- Room DB (`domain_log` table), 500 entri terakhir, bisa dimatikan logging-nya
- `LogsScreen`: riwayat real-time dengan status diblokir/diizinkan

**UI**
- `HomeScreen`: toggle utama, statistik blocked/allowed, navigasi ke 3 screen
  advanced
- `WhitelistScreen`, `RulesScreen`, `LogsScreen`
- Tema Compose dark, warna hijau shield (`#00C896`)

**Build & signing**
- Release keystore asli (`release.keystore`, alias `adshield`) sudah dibuat
  dan disertakan dalam ZIP delivery ini
- GitHub Actions CI (`.github/workflows/build.yml`): build APK signed release,
  nama artifact mengikuti versionName
- `keystore.properties` untuk build lokal (gitignored, tidak pernah di-commit)
