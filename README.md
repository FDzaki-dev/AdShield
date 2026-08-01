# AdShield

Ad blocker & tracker blocker native Android, berjalan sepenuhnya on-device
tanpa root, menggunakan `VpnService` lokal untuk menyaring DNS.

## Cara kerja singkat

AdShield **tidak** menangkap semua trafik internet. VPN builder hanya
mendaftarkan rute ke alamat DNS palsu (`10.111.222.1/32`), jadi **hanya
paket DNS (port 53)** yang masuk ke tun interface — semua trafik lain (video,
gambar, koneksi banking, dll) tetap langsung ke internet tanpa disentuh sama
sekali. Ini teknik yang sama dipakai DNS66/AdAway VPN-mode, jadi latensi dan
baterai jauh lebih hemat dibanding VPN full-tunnel.

Alur satu query DNS:
1. App lain query domain → OS arahkan ke `10.111.222.1` (DNS palsu kita)
2. `AdBlockVpnService` baca paket dari tun, parse nama domain (`DnsPacket.parse`)
3. Cek `BlocklistManager` (in-memory, gabungan blocklist bawaan + custom rules
   dari Settings) → domain match?
   - Matching **exact-match by default** (mirip Cloudflare 1.1.1.1 for
     Families / Pi-hole yang presisi) — blokir `contoh.com` TIDAK otomatis
     ikut blokir `apa.contoh.com`. Subdomain hanya ikut terblokir kalau
     domain-nya didaftar sebagai wildcard eksplisit (`*.contoh.com`), baik
     di blocklist bawaan maupun di Aturan Kustom. Ini sengaja untuk
     menghindari over-blocking domain CDN/infrastruktur yang dipakai
     bareng-bareng oleh iklan maupun konten legit.
   - **Match** → balas langsung dengan A record `0.0.0.0` (`DnsPacket.buildBlockedResponse`)
   - **Tidak match** → forward ke resolver asli (Cloudflare 1.1.1.1 / Google 8.8.8.8)
     lewat socket yang di-`protect()` (supaya tidak looping balik ke VPN),
     lalu balasannya di-relay balik ke app pemohon

## Fitur

- Toggle satu tombol aktif/nonaktif proteksi
- Statistik real-time (jumlah domain diblokir vs diizinkan)
- **Whitelist per-aplikasi** — pilih app tertentu yang tidak pernah diblokir
- **Aturan kustom** — tambah domain block/allow manual, override blocklist bawaan
- **Log domain** — riwayat 500 query terakhir dengan status blokir/izin, bisa dimatikan
- Auto-start setelah reboot (jika sebelumnya aktif)
- Bertahan setelah app di-swipe dari Recents (foreground service + `START_STICKY`
  + watchdog AlarmManager untuk OEM agresif seperti XOS/MIUI/ColorOS)
- Blocklist bawaan offline (~100+ domain ads/tracker populer), bisa diperluas manual

## Batasan yang perlu diketahui

- **Hanya memblokir via DNS.** Iklan yang di-serve dari domain yang sama dengan
  konten utama app (mis. iklan in-house yang tidak lewat CDN ad-network
  terpisah) tidak akan terblokir — ini batasan teknik DNS-blocking secara umum,
  bukan hanya AdShield.
- **Traffic HTTPS/DoH pihak ketiga** (app yang hardcode DNS-over-HTTPS ke resolver
  sendiri, mis. beberapa versi Chrome/Firefox) bisa melewati filter DNS ini.
  Solusi jangka panjang: deteksi & blokir port DoH umum (belum diimplementasi).
- Bukan pemblokir iklan berbasis root/iptables — cakupannya lebih terbatas
  dari solusi root, tapi jauh lebih aman & tidak butuh akses root.
- Agar servis benar-benar tidak dimatikan sistem saat idle lama di HP dengan
  battery manager agresif (Infinix XOS dsb.), user WAJIB mengizinkan
  "No restrictions" / "Kecualikan dari optimasi baterai" — ada tombol
  shortcut untuk ini di Home screen.

## Status proyek

- **v1.1.0** — matching domain diganti ke exact-match + wildcard eksplisit,
  blocklist bawaan dikurasi ulang (lebih presisi, tidak over-block)
- v1.0.1 — fix build gagal (salah import)
- v1.0.0 — rilis awal, arsitektur lengkap

Lihat CHANGELOG.md untuk detail lengkap tiap versi.

## Roadmap (belum dikerjakan)

- Deteksi & blokir DNS-over-HTTPS (DoH) umum agar tidak bisa dilewati
- Import blocklist dari URL custom (field sudah ada di data layer, UI belum)
- Statistik per-aplikasi (domain mana diblokir untuk app mana)
- Dark/light theme toggle (saat ini dark-only)

## Struktur proyek

```
app/src/main/java/com/fdzaki/adshield/
├── vpn/            AdBlockVpnService (engine inti), DnsPacket (parser paket)
├── data/           BlocklistManager, SettingsRepository (DataStore), 
│                   InstalledAppsRepository, data/db (Room - log domain)
├── receiver/       BootReceiver (auto-start), RestartReceiver (watchdog)
├── ui/             MainViewModel + ui/screens (Home, Whitelist, Rules, Logs)
└── ui/theme/       Compose theme (dark, hijau shield)
```

Lihat `PROJECT_STATE.md` untuk konteks arsitektur mendalam (dibaca Claude di
sesi berikutnya sebelum lanjut kerja).
