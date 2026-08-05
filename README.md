# AdShield

Aplikasi Android dengan **dua mode perlindungan terpisah** (tidak pernah
jalan bersamaan — pilih salah satu):

1. **Ad-Block DNS** — ad blocker & tracker blocker ringan, on-device, tanpa
   root, cuma nge-tunnel DNS.
2. **VPN Tunnel (WARP)** — full-tunnel WireGuard terenkripsi lewat
   Cloudflare WARP gratis, buat yang mau enkripsi SEMUA trafik (bukan cuma
   blokir iklan).

## Mode 1: Cara kerja Ad-Block DNS

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

## Mode 2: Cara kerja VPN Tunnel (WARP)

Mode ini pakai **library resmi WireGuard** (`com.wireguard.android:tunnel`,
engine GoBackend/wireguard-go) — bukan implementasi kripto buatan sendiri.
Beda total secara arsitektur dari mode DNS: ini nge-tunnel **SEMUA** trafik
(`0.0.0.0/0`), bukan cuma DNS.

Alur saat pertama kali diaktifkan:
1. Generate keypair WireGuard (Curve25519) lokal di device
2. Registrasi otomatis ke Cloudflare WARP — ini akun gratis tanpa perlu
   login, sama seperti yang didapat app 1.1.1.1 resmi saat pertama install.
   Prosesnya niru pendekatan proyek open-source [`wgcf`](https://github.com/ViRb3/wgcf).
3. Identitas WARP (private key, address, peer info) disimpan di DataStore
   terpisah (`WarpAccountRepository`) — connect berikutnya tidak registrasi
   ulang, langsung pakai yang tersimpan
4. `WarpTunnelManager` bangun `Config` WireGuard dari identitas tersimpan,
   nyalakan tunnel lewat `GoBackend`

**Yang WAJIB dipahami sebelum pakai mode ini:**
- **API registrasi WARP tidak resmi.** Cloudflare tidak mempublikasikan ini
  sebagai API publik yang didukung — bisa berubah/berhenti berfungsi kapan
  saja tanpa pemberitahuan. Kalau tiba-tiba gagal registrasi, cek apakah
  `WarpRegistrationClient.API_VERSION` perlu diperbarui (lihat komentar di
  file itu untuk cara ceknya).
- **ID ini beneran VPN pihak ketiga.** Semua trafik keluar lewat jaringan
  Cloudflare, bukan cuma untuk blokir iklan — beda tujuan dari mode
  Ad-Block DNS. Jangan aktifkan berbarengan dengan ekspektasi "dapat dua
  manfaat sekaligus", karena memang mutually-exclusive.
- Sudah diverifikasi lewat riset (bukan asumsi) kalau profil WireGuard
  standar — tanpa modifikasi field khusus apa pun — memang bisa connect ke
  WARP pakai client resmi manapun termasuk Android.

## Fitur

**Ad-Block DNS:**
- Toggle satu tombol aktif/nonaktif proteksi
- Statistik real-time (jumlah domain diblokir vs diizinkan)
- **Whitelist per-aplikasi** — pilih app tertentu yang tidak pernah diblokir
- **Aturan kustom** — tambah domain block/allow manual, override blocklist bawaan
- **Log domain** — riwayat 500 query terakhir dengan status blokir/izin, bisa dimatikan,
  bisa dicari per domain, dan difilter (Semua/Diblokir/Diizinkan)
- Blocklist bawaan offline (~100+ domain ads/tracker populer), bisa diperluas manual

**VPN Tunnel (WARP):**
- Registrasi WARP otomatis, sekali saja (tersimpan untuk connect berikutnya)
- Status koneksi real-time + pesan error kalau registrasi/handshake gagal
- **Indikator kualitas koneksi** — bukan cuma "interface UP", tapi probe
  nyata ke Cloudflare (`cdn-cgi/trace`) tiap ~25 detik: latensi (ms) +
  konfirmasi trafik benar-benar lewat WARP, ditampilkan sebagai titik
  status berwarna di Home screen dan di teks notifikasi
- **Auto reconnect** — kalau probe gagal berturut-turut atau tunnel jatuh
  sendiri, otomatis disambungkan ulang dengan backoff (maks 5 percobaan
  per sesi, direset tiap matikan-nyalakan manual)

**Berlaku untuk kedua mode:**
- Auto-start setelah reboot (mode yang terakhir aktif sebelum shutdown)
- Bertahan setelah app di-swipe dari Recents (foreground service + `START_STICKY`
  + watchdog AlarmManager untuk OEM agresif seperti XOS/MIUI/ColorOS)
- **Mutually exclusive** — mengaktifkan salah satu otomatis mematikan yang lain
- **App Shortcuts** — tekan lama ikon AdShield di launcher: buka
  Whitelist/Log langsung, atau toggle Nyalakan/Matikan DNS & WARP tanpa
  buka aplikasi dulu (label shortcut toggle otomatis mengikuti mode aktif)
- **Layar Diagnostik** — status teknis satu layar (versi app, perangkat,
  mode aktif, status & error terakhir DNS/WARP, statistik, memori app +
  memori sistem tersisa + baterai) dengan tombol salin-ke-clipboard untuk
  lapor masalah
- **Onboarding** — 4 layar pengenalan singkat saat pertama kali buka app:
  jelaskan kedua mode & kenapa mutually-exclusive, plus ajakan mengecualikan
  dari optimasi baterai. Bisa dilewati kapan saja, hanya muncul sekali.
- **Blocklist Kustom via URL** — tempel URL raw blocklist (format hosts
  atau satu domain per baris), diperbarui otomatis tiap 24 jam kalau ada
  koneksi internet, atau manual kapan saja lewat tombol "Perbarui
  Sekarang". Domain dari sini terpisah dari blocklist bawaan & aturan
  manual, jadi kalau unduhannya gagal, keduanya tetap utuh.
- **Aturan Kustom lebih mudah dikelola** — validasi format domain langsung
  saat mengetik, pencarian di dalam daftar (untuk daftar panjang), pesan
  kondisi kosong yang jelas

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
- **Whitelist per-app butuh Android 10 (API 29) ke atas** — OS baru
  menyediakan API untuk mendeteksi app pengirim query DNS mulai versi itu.
  Di Android lebih lama, toggle-nya tersimpan tapi tidak berpengaruh
  (dijelaskan langsung di layar Whitelist, bukan gagal diam-diam).
- **Mode WARP belum divalidasi end-to-end di device fisik manapun** (lihat
  PROJECT_STATE.md). Kode sudah dicocokkan ke javadoc resmi library, tapi
  belum ada bukti langsung "berhasil connect" dari build sungguhan — kalau
  ada masalah pas dites, laporkan pesan error persis yang muncul di UI.
- **Whitelist per-app dan blocklist domain TIDAK berlaku saat mode WARP
  aktif** — WARP itu tunnel WireGuard mentah tanpa filtering apa pun, cuma
  enkripsi. Kalau mau blokir iklan, pakai mode Ad-Block DNS.

## Crash Logger bawaan

[#crash-logger-bawaan](#crash-logger-bawaan)

Sejak v2.6.0, AdShield otomatis menyimpan laporan crash setiap kali
aplikasi mengalami uncaught exception:

- **Android 10 ke atas:** `Documents/AdShield/logs/` (folder publik,
  bisa dibuka lewat app Files/Berkas bawaan).
- **Android 9 ke bawah:** `Android/data/com.fdzaki.adshield/files/AdShield/logs/`
  (penyimpanan privat app — tidak butuh izin storage tambahan).

Maksimal 50 file terbaru disimpan (file tertua otomatis dihapus). Kalau
melaporkan crash, sertakan isi file `crash_...txt` terbaru dari folder
tersebut — ini jauh lebih cepat didiagnosis dibanding hanya deskripsi
gejala.

## Status proyek

- **v3.14.0 (terbaru)** — Batch 3/N: IKEv2 native (`protocol/IkeV2VpnEngine.kt`)
  pakai `android.net.VpnManager`/`Ikev2VpnProfile` platform API (0
  dependency pihak ketiga). OpenVPN dibatalkan permanen dari roadmap —
  tidak ada jalur non-GPL/AGPL yang legit di Android. Belum di-wire ke
  UI, belum dikonfirmasi build CI (lihat PROJECT_STATE.md).
- v3.13.0 — Batch 2/N: adaptasi WireGuard/WARP existing ke
  interface `VpnEngine` (`protocol/WarpVpnEngineAdapter.kt`). 0 baris di
  package `warp/` diubah — adapter murni membungkus `WarpTunnelManager`
  yang sudah ada, belum di-wire ke UI mana pun. Belum dikonfirmasi build
  CI (lihat PROJECT_STATE.md).
- v3.12.0 — Batch 1/N Arsitektur Multi-Protokol: fondasi
  untuk memperluas AdShield dari 2 mode (DNS Ad-Block/WARP) jadi VPN
  client multi-protokol (rencana: + OpenVPN, IKEv2, Shadowsocks/VLESS,
  rilis bertahap per protokol). Batch ini scaffolding saja — interface
  `VpnEngine`, model config per protokol, penyimpanan aman untuk
  secrets. Belum ada engine baru yang fungsional. Belum dikonfirmasi
  build CI (lihat PROJECT_STATE.md).
- v3.11.1 — HOTFIX: compile error di `DohClient.kt` (build v3.11.0 gagal CI). Fungsionalitas sama, murni perbaikan sintaks. Belum
  dikonfirmasi build sukses (lihat PROJECT_STATE.md).
- v3.11.0 — DNS-over-HTTPS (DoH): query DNS dicoba lewat
  HTTPS (Cloudflare, lalu Google) dulu sebelum fallback ke plain DNS
  biasa. Merespons laporan user: fix MTU v3.10.2 tidak menolong, plain
  UDP port 53 kemungkinan diblokir total di jaringannya. Belum
  dikonfirmasi di device (lihat PROJECT_STATE.md).
- v3.10.2 — HOTFIX: `VPN_MTU` diturunkan dari 32000 (tidak wajar) ke 1500
  (standar) — tidak cukup menyelesaikan masalah, lihat v3.11.0.
- v3.10.1 — HOTFIX: upstream DNS resolver diversity (`1.1.1.1, 1.0.0.1,
  8.8.8.8`) — tidak cukup menyelesaikan masalah total internet failure
  sendirian, lihat v3.10.2.
- v3.10.0 — Instrumentasi profiling memori & baterai (layar
  Diagnostik): PSS memori app, memori sistem tersisa, baterai
  (persen/suhu/status isi). Murni baca-saja, tidak ada perubahan perilaku
  VPN/DNS/WARP. Belum divalidasi di device fisik.
- v3.5.0–v3.9.0 — Rangkaian optimasi performa & reliability ("Internet
  Surfing Optimization" + audit performa/feedback): DNS cache, socket
  pooling upstream, blocklist matching O(kedalaman domain), auto-MTU +
  smart endpoint selection + fast reconnect + kill-switch hardening WARP,
  DNS prefetch/cache-warming, Quick Settings Tile per-mode. Detail lengkap
  per versi ada di CHANGELOG.md — daftar di bawah ini belum diperbarui
  sejak v2.5.0, lihat CHANGELOG.md untuk riwayat v2.6.0 ke atas.
- **v2.5.0** — DNS AdBlocker (scope ringan): blocklist kustom via URL
  dengan auto-update tiap 24 jam (WorkManager), UI Aturan Kustom lebih
  mudah dikelola (validasi domain, pencarian, pesan kondisi kosong).
  Custom DNS terenkripsi (DoH/DoT) sengaja disisihkan ke batch terpisah
  — itu perubahan arsitektur, bukan penambahan UI biasa (lihat Roadmap)
- v2.4.0 — UX & Onboarding: layar Onboarding 4-slide untuk pengguna
  baru (penjelasan kedua mode + pengecualian baterai), tampil sekali lalu
  tidak lagi (bisa dilewati kapan saja)
- v2.3.0 — Monitoring & Diagnostik: layar Diagnostik baru (status
  teknis + salin ke clipboard), Log Domain kini bisa dicari & difilter,
  kegagalan Ad-Block DNS sekarang terlihat (sebelumnya diam-diam gagal)
- v2.2.0 — App Shortcuts: tekan lama ikon di launcher untuk buka
  Whitelist/Log langsung, atau toggle DNS/WARP tanpa buka aplikasi
- v2.1.0 — WARP UX: auto reconnect + indikator kualitas koneksi
  (latensi & konfirmasi trafik nyata lewat probe Cloudflare). Belum
  divalidasi di device fisik (lihat Batasan di atas)
- v2.0.1 — perbaikan bug internal: race condition saat pindah mode
  DNS⇄WARP yang bisa merusak auto-restart-setelah-reboot (tidak ada
  perubahan perilaku yang terlihat user)
- v2.0.0 — mode baru VPN Tunnel (WARP), full-tunnel WireGuard via
  Cloudflare WARP gratis, terpisah & mutually-exclusive dari Ad-Block DNS
- v1.2.0 — pematangan: whitelist per-app benar-benar aktif (UID nyata),
  critical allowlist domain esensial konektivitas, DNS forward fallback
  multi-resolver
- v1.1.0 — matching domain diganti ke exact-match + wildcard eksplisit,
  blocklist bawaan dikurasi ulang (lebih presisi, tidak over-block)
- v1.0.1 — fix build gagal (salah import)
- v1.0.0 — rilis awal, arsitektur lengkap

Lihat CHANGELOG.md untuk detail lengkap tiap versi.

## Roadmap (belum dikerjakan)

- **Multi-protokol VPN (v3.12.0+, batch 3/N sudah selesai statis)** —
  perluasan dari 2 mode jadi VPN client multi-protokol: WireGuard/WARP
  (v3.13.0) dan IKEv2 native (v3.14.0) sudah punya `VpnEngine`; OpenVPN
  **dibatalkan permanen** (GPL/AGPL, lihat PROJECT_STATE.md);
  Shadowsocks/VLESS masih placeholder. Rilis bertahap 1 protokol per
  batch. Lihat PROJECT_STATE.md untuk urutan & status detail.
- Validasi mode WARP di device fisik (masih prioritas #1 — v2.1.0
  menambah fitur di atas fondasi yang belum pernah dibuktikan jalan nyata)
- ~~DNS AdBlocker: custom DNS terenkripsi (DoH/DoT)~~ — **DoH selesai
  v3.11.0** (fallback ke plain-UDP kalau DoH gagal). DoT (port 853) belum
  dikerjakan, prioritas rendah — lihat PROJECT_STATE.md
- Statistik per-aplikasi (domain mana diblokir untuk app mana)
- Dark/light theme toggle (saat ini dark-only)
- MASQUE (protokol QUIC dari IETF, dipakai iCloud Private Relay) — sengaja
  tidak dikerjakan, nyaris tidak ada library Android siap pakai (lihat
  PROJECT_STATE.md untuk alasan lengkap)

## Struktur proyek

```
app/src/main/java/com/fdzaki/adshield/
├── vpn/            AdBlockVpnService (engine Ad-Block DNS), DnsPacket (parser paket)
├── warp/           WarpTunnelManager (engine WireGuard/WARP), WarpRegistrationClient,
│                   WarpAccountRepository, WarpForegroundService, WarpAccount
├── data/           BlocklistManager, SettingsRepository (DataStore, termasuk
│                   activeMode), InstalledAppsRepository, data/db (Room - log domain)
├── receiver/       BootReceiver (restart mode aktif), RestartReceiver (watchdog DNS),
│                   WarpRestartReceiver (watchdog WARP)
├── ui/             MainViewModel + ui/screens (Home, Whitelist, Rules, Logs, Diagnostics)
└── ui/theme/       Compose theme (dark, hijau shield)
```

Lihat `PROJECT_STATE.md` untuk konteks arsitektur mendalam (dibaca Claude di
sesi berikutnya sebelum lanjut kerja).
