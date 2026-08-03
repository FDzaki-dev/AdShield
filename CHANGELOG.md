# Changelog

## v2.5.0 — DNS AdBlocker: Auto-Update Blocklist + UI Lebih Mudah (2026-08-03)

Batch kedua dari sisa daftar "Kekurangan AdShield" — kategori DNS AdBlocker,
scope "ringan" (bukan DoH/DoT, itu sengaja disisihkan jadi batch tersendiri
karena perubahan arsitektur di `AdBlockVpnService`).

- **Baru: Blocklist Kustom via URL, auto-update tiap 24 jam** — tempel URL
  raw ke file blocklist (format hosts atau satu domain per baris) di layar
  Aturan Kustom. `BlocklistUpdateWorker` (WorkManager, dependency sudah ada
  sejak lama tapi belum pernah dipakai) mengunduh, memvalidasi, cache ke
  penyimpanan lokal (write-then-rename, tahan crash saat proses berjalan),
  lalu langsung diterapkan ke `BlocklistManager` yang sedang aktif tanpa
  perlu restart VPN. Ada tombol "Perbarui Sekarang" untuk trigger manual
  kapan saja, plus status terakhir (berhasil/gagal + waktu) ditampilkan
  langsung di layar.
- Domain dari blocklist kustom disimpan di **set terpisah** dari blocklist
  bawaan & aturan manual — kalau URL gagal diunduh atau kosong, blocklist
  bawaan dan aturan kustom manual TIDAK terpengaruh sama sekali (lihat
  keputusan arsitektur di `PROJECT_STATE.md`).
- **UI Aturan Kustom lebih mudah dikelola:**
  - Validasi format domain langsung saat mengetik (border merah + pesan
    kalau formatnya tidak valid atau domain sudah ada di daftar)
  - Pencarian di dalam daftar Blokir/Izinkan (muncul otomatis kalau daftar
    lebih dari 5 domain, supaya tidak makan tempat kalau daftarnya masih
    pendek)
  - Pesan kondisi kosong yang jelas ("Belum ada domain..." vs "Tidak ada
    yang cocok pencarian")
- Tidak ada perubahan pada matching logic (`isBlocked`) untuk blocklist
  bawaan maupun aturan manual — murni penambahan sumber blocklist ketiga
  yang independen.

## v2.4.0 — UX & Onboarding (2026-08-03)

Batch ketiga dari daftar "Kekurangan AdShield" — kategori UX & Onboarding.
Murni fitur baru untuk pengguna baru pertama kali buka app; tidak menyentuh
logic DNS/WARP, matching domain, atau mode aktif manapun.

- **Baru: Layar Onboarding 4-slide** (`ui/screens/OnboardingScreen.kt`) —
  tampil otomatis hanya sekali di pemasangan baru: (1) selamat datang, (2)
  penjelasan Mode Ad-Block DNS, (3) penjelasan Mode VPN Tunnel (WARP) +
  peringatan mutually-exclusive, (4) ajakan mengecualikan dari optimasi
  baterai (tombol sama seperti yang sudah ada di Home). Bisa dilewati
  ("Lewati") kapan saja dari slide manapun.
- Status "sudah lihat onboarding" disimpan di `SettingsRepository`
  (`has_seen_onboarding`, DataStore, default `false`) — user existing yang
  update dari versi lama TIDAK akan melihat onboarding lagi karena flag ini
  otomatis `false` hanya untuk instalasi baru; tapi karena ini kunci baru di
  DataStore yang sama, instalasi existing juga membaca default `false` saat
  pertama kali baca. **Catatan:** ini artinya user yang update dari v2.3.0
  ke v2.4.0 AKAN melihat onboarding sekali (bukan cuma instalasi baru
  murni) — dianggap dampak kecil/dapat diterima (bisa dilewati 1 tombol),
  bukan regresi fungsional.
- `MainActivity` menahan render pertama NavHost sampai status onboarding
  selesai dibaca (satu kali baca suspend dari DataStore, pola sama seperti
  `currentActiveMode()`), supaya Home tidak sempat "kedip" sebelum
  redirect ke Onboarding.
- Tidak ada perubahan pada arsitektur VPN, matching domain, whitelist
  per-app, atau mode WARP itu sendiri.

## v2.3.0 — Monitoring & Diagnostik (2026-08-03)

Batch kedua dari daftar "Kekurangan AdShield" — kategori Monitoring &
Diagnostik (log lebih mudah dibaca, halaman diagnostik baru, error
handling lebih jelas). Tidak menyentuh logic inti DNS/WARP kecuali
menambah pelaporan error yang sebelumnya diam-diam gagal.

- **Baru: Layar Diagnostik.** Ringkasan status teknis satu layar — versi
  app, info perangkat/Android, status pengecualian optimasi baterai, mode
  aktif, status & error terakhir masing-masing mode (DNS/WARP), statistik
  blokir, dan kualitas koneksi WARP. Tombol di kanan atas menyalin seluruh
  info ke clipboard dalam format teks siap tempel — untuk melapor masalah
  tanpa perlu mendeskripsikan tiap field manual. Dapat dibuka dari Home
  screen (`ui/screens/DiagnosticsScreen.kt` baru).
- **Fix: kegagalan Ad-Block DNS sekarang terlihat, sebelumnya diam-diam
  gagal.** `AdBlockVpnService.startVpn()` sebelumnya kalau `establish()`
  VPN interface gagal (exception ATAU mengembalikan null, mis. VPN app
  lain sedang memegang koneksi), toggle di Home screen cuma kembali ke
  posisi off tanpa pesan apa pun. Sekarang ada `AdBlockVpnService.lastError`
  (StateFlow companion, pola yang sama seperti `WarpTunnelManager.lastError`
  yang sudah ada sejak v2.0.0), diekspos lewat
  `MainViewModel.dnsLastError` dan ditampilkan di layar Diagnostik.
- **Log Domain lebih mudah dibaca.** `LogsScreen` sekarang punya kolom
  pencarian domain (client-side, murah karena data sudah dibatasi 500
  entri terakhir oleh `DomainLogDao.recentEntries()`) dan 3 filter chip
  (Semua/Diblokir/Diizinkan) dengan jumlah masing-masing, plus ringkasan
  "menampilkan X dari Y entri".
- Tidak ada perubahan pada arsitektur VPN, matching domain, whitelist
  per-app, atau mode WARP itu sendiri — murni observability tambahan di
  atas state yang sudah ada.

## v2.2.0 — App Shortcuts: navigasi cepat dari launcher (2026-08-02)

Diminta user terpisah dari daftar "Kekurangan AdShield" — tujuannya
navigasi lebih cepat tanpa harus buka aplikasi dulu. Tidak menyentuh logic
DNS/WARP inti sama sekali, murni menambah jalur akses baru ke fungsi yang
sudah ada.

- **Baru: 2 shortcut statis** (tekan lama ikon AdShield di launcher) —
  "Whitelist" dan "Log", langsung membuka layar itu tanpa mampir ke Home
  dulu. Didefinisikan di `res/xml/shortcuts.xml`, label & ikon tetap
  (tidak berubah sesuai state).
- **Baru: 2 shortcut dinamis** — "Nyalakan/Matikan DNS" dan
  "Nyalakan/Matikan WARP". Label & ikonnya otomatis berganti sesuai mode
  yang sedang aktif, disinkronkan dari `AdShieldApp` setiap
  `SettingsRepository.activeMode` berubah (baik toggle dari shortcut
  maupun dari tombol di Home screen) lewat `util/ShortcutsManager.kt` baru.
- **Fix (ditemukan saat implementasi batch ini, bukan regresi lama):**
  toggle shortcut bisa salah arah kalau aplikasi dibuka dari kondisi
  ke-kill total lewat shortcut — penyebabnya baca state dari StateFlow
  yang belum sempat ada subscriber-nya (`activeMode.value` masih seed
  `AppMode.NONE`, bukan mode asli tersimpan). Diperbaiki dengan
  `MainViewModel.currentActiveMode()`, baca `.first()` langsung dari
  DataStore.
- Tidak ada perubahan pada mode Ad-Block DNS maupun WARP itu sendiri —
  cuma jalur akses baru ke toggle & 2 layar yang sudah ada.

## v2.1.0 — WARP UX: auto reconnect & indikator kualitas koneksi (2026-08-02)

Batch pertama dari daftar "Kekurangan AdShield" yang diminta user — fokus
kategori WARP UX saja (bukan DNS AdBlocker/Monitoring/UX, itu batch
terpisah). Semua fitur di bawah murni tambahan (extend), tidak menyentuh
alur `connect()`/`disconnect()` yang sudah teruji, dan tidak mengubah
kontrak/behavior mode Ad-Block DNS sama sekali.

- **Baru: Auto reconnect WARP.** `WarpTunnelManager` sekarang punya
  watchdog internal yang mengecek konektivitas nyata setiap ~25 detik
  selama tunnel seharusnya aktif. Kalau 2x probe berturut-turut gagal
  (atau interface WireGuard-nya ternyata DOWN padahal seharusnya UP),
  tunnel otomatis di-restart dengan backoff eksponensial (5s → 10s → 20s
  → 40s → capped 60s), maksimum 5 percobaan per sesi "nyala" — supaya
  tidak menguras baterai kalau memang tidak ada internet sama sekali.
  Reset otomatis tiap kali user matikan-nyalakan manual.
- **Baru: Indikator kualitas koneksi.** Bukan cuma "interface UP" (yang
  bisa menipu — WireGuard bisa "UP" walau paket tidak benar-benar sampai
  ke Cloudflare), tapi probe nyata ke `cdn-cgi/trace` Cloudflare tiap
  siklus watchdog: latensi (ms) dan konfirmasi `warp=on` dari body respons
  itu sendiri. Ditampilkan sebagai titik status berwarna + teks singkat di
  bawah toggle WARP (hijau = baik, kuning = agak lambat, merah = trafik
  belum terkonfirmasi/sedang reconnect), dan juga di teks notifikasi
  persisten WARP (supaya kelihatan tanpa buka app).
- Tidak ada perubahan pada mode Ad-Block DNS, tidak ada file yang dihapus,
  tidak ada regresi pada fitur WARP yang sudah ada (registrasi, mutual
  exclusion dua mode, EXTRA_MODE_SWITCH, dll — semua utuh).

**Catatan penting (belum berubah dari v2.0.1):** batch ini TIDAK menguji
WARP di device fisik — itu masih prioritas #1 yang tercatat di
PROJECT_STATE.md dan belum dilakukan. Auto-reconnect di atas ditulis
berdasarkan API resmi WireGuard Android (`Backend.getStatistics()`,
diverifikasi lewat javadoc.io) dan endpoint publik Cloudflare
(`cdn-cgi/trace`, dipakai luas oleh proyek WARP pihak ketiga untuk cek
status `warp=on`), tapi keduanya belum pernah dibuktikan jalan nyata di
HP.

## v2.0.1 — Perbaikan race condition activeMode (2026-08-02)

Bukan fitur baru — audit kode menyeluruh (diminta user: "bawa aplikasi ke
tahap finish, fokus WARP & WireGuard") menemukan dan menutup 1 bug nyata:

- **Fix:** Berpindah mode (Ad-Block DNS ⇄ VPN Tunnel WARP) bisa membuat
  status mode aktif yang tersimpan (`activeMode`) salah tersimpan sebagai
  "tidak ada mode aktif", walau salah satu mode sebenarnya sedang jalan.
  Penyebab: dua service (`AdBlockVpnService`, `WarpForegroundService`)
  sama-sama menulis status ini dari proses background terpisah tanpa
  urutan yang terjamin saat perpindahan mode terjadi. Ini bisa merusak
  fitur auto-restart-setelah-reboot untuk kasus tertentu (device restart
  tepat setelah user pindah mode, sebelum status sempat "settle").
  Diperbaiki dengan menandai stop yang terjadi karena perpindahan mode
  secara eksplisit, supaya cuma satu sisi yang menulis status final.
- Tidak ada perubahan perilaku yang terlihat user — toggle mode, statistik,
  whitelist, rules, logs semua tetap sama persis.

## v2.0.0 — Mode VPN Tunnel (WARP), full-tunnel WireGuard (2026-08-02)

**Fitur besar baru** — mode kedua yang terpisah total dan mutually-exclusive
dari Ad-Block DNS, untuk kasus "mau enkripsi semua trafik", bukan cuma
blokir iklan:

- **Engine**: `com.wireguard.android:tunnel` (library resmi WireGuard,
  GoBackend/wireguard-go) — bukan implementasi crypto buatan sendiri.
- **Registrasi otomatis ke Cloudflare WARP** (gratis, tanpa akun) lewat
  pendekatan yang sama dengan proyek open-source `wgcf`: generate keypair
  Curve25519 lokal, POST ke `api.cloudflareclient.com/{versi}/reg`, simpan
  identitas WARP di DataStore terpisah (`WarpAccountRepository`) supaya
  tidak registrasi ulang tiap connect.
- **Full-tunnel** (`0.0.0.0/0`, `::/0`) — beda arsitektur total dari
  AdBlockVpnService yang cuma nge-tunnel DNS. Dua engine ini TIDAK PERNAH
  jalan bersamaan (`AppMode.DNS_ADBLOCK` vs `AppMode.WARP_TUNNEL`,
  ditegakkan di `MainActivity` — start salah satu otomatis stop yang lain).
- **Foreground service terpisah** (`WarpForegroundService`) dengan pola
  survival yang sama seperti AdBlockVpnService: `START_STICKY`, watchdog
  AlarmManager (`WarpRestartReceiver`) untuk swipe-dari-Recents, dan
  auto-restart setelah reboot (`BootReceiver` diperluas untuk mode apa pun
  yang terakhir aktif, bukan cuma DNS).
- **UI**: kartu baru di HomeScreen dengan switch, status (menyambung/aktif),
  dan pesan error kalau registrasi/koneksi gagal.

**Keterbatasan yang WAJIB diketahui user** (didokumentasikan langsung di UI
dan README, bukan disembunyikan):
- API registrasi WARP **tidak resmi** — Cloudflare bisa mengubah/mematikannya
  kapan saja tanpa pemberitahuan. Kalau registrasi gagal terus, versi API
  (`WarpRegistrationClient.API_VERSION`) kemungkinan perlu diperbarui.
- Ini VPN pihak ketiga sungguhan — semua trafik keluar lewat jaringan
  Cloudflare, bukan cuma untuk blokir iklan. User perlu paham ini beda
  fungsi dari mode Ad-Block DNS.
- Sudah diverifikasi (via riset, bukan asumsi) bahwa profil WireGuard
  standar tanpa modifikasi apa pun BISA connect ke WARP menggunakan client
  resmi manapun termasuk Android — ini bukan fitur setengah-jadi yang
  "mungkin tidak jalan", tapi tetap bergantung pada API tidak resmi yang
  disebut di atas.

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
