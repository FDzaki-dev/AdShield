# Changelog

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
