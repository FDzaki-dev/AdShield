# Changelog

## v3.2.1 — EKSPERIMEN: matikan rute IPv6 untuk isolasi bottleneck upload WARP (2026-08-04)

> **Ini build eksperimen/diagnostik, BUKAN keputusan arsitektur permanen.**
> User laporan hasil ukur nyata: WARP mati → 31.3 Mbps down / 3.43 Mbps up /
> 45 md. WARP aktif → 26.6 Mbps down / 0.48 Mbps up / 35 md. Download cuma
> turun 15% (wajar, overhead enkripsi normal) tapi upload jatuh **86%** —
> tidak proporsional, latensi malah membaik (tunnel-nya sendiri sehat).
> User pilih coba eksperimen tanpa rute IPv6 duluan (dari 3 opsi yang
> ditawarkan) untuk isolasi apakah jalur IPv6 WARP yang bermasalah di
> operator selulernya.

- **Perubahan:** `Peer.Builder` di `WarpTunnelManager.buildConfig()`
  sekarang TIDAK menambahkan `AllowedIPs = ::/0` selama
  `ROUTE_IPV6 = false` (konstanta baru, satu titik switch untuk revert
  total). `AllowedIPs = 0.0.0.0/0` (IPv4) TIDAK berubah — full-tunnel IPv4
  tetap sama seperti sebelumnya.
- **Dampak diketahui & disengaja:** selama flag ini `false`, trafik IPv6
  dari app manapun (kalau device/jaringan memang pakai IPv6) akan **lewat
  jalur normal device, TIDAK terenkripsi lewat WARP** — hanya IPv4 yang
  full-protected. Ini trade-off sadar untuk keperluan diagnostik, bukan
  bug.
- **WAJIB ditindaklanjuti:** ulangi speedtest yang sama (WARP aktif) di
  jaringan seluler yang sama. Kalau upload membaik signifikan → IPv6
  terbukti jadi penyebab, `ROUTE_IPV6=false` bisa dipertimbangkan jadi
  default permanen (dengan diskusi ulang soal trade-off keamanan IPv6 di
  atas). Kalau upload TETAP jatuh parah → bukan IPv6, revert
  `ROUTE_IPV6=true` segera dan cari penyebab lain (kemungkinan besar:
  keterbatasan uplink operator seluler itu sendiri yang teramplifikasi
  overhead WireGuard, bukan sesuatu yang bisa diperbaiki dari sisi app).
- Tidak ada perubahan lain — MTU 1280 (v3.2.0), endpoint fallback,
  keepalive, watchdog semua tetap.

## v3.2.0 — WARP: fix MTU untuk performa/stabilitas mobile (2026-08-04)

> User arahan eksplisit: "fokus dongkrak performance WARP 100 persen".
> User juga sudah konfirmasi v3.1.0 (screenshot device, "lumayan lah") —
> arah warna matte premium dianggap cukup untuk sekarang, prioritas
> pindah total ke performa WARP.

- **Root cause performa yang ditemukan:** `WarpTunnelManager.buildConfig()`
  tidak pernah set MTU eksplisit di `Interface.Builder` — library
  `com.wireguard.android:tunnel` pakai nilai auto/default-nya sendiri.
  Di banyak jaringan mobile (terutama seluler dengan overhead NAT/tunneling
  operator), paket WireGuard yang sudah dibungkus terenkapsulasi bisa
  melebihi MTU jalur nyata → fragmentasi → paket drop/retransmit, yang jauh
  lebih mahal terhadap throughput nyata dibanding sekadar pakai MTU lebih
  kecil.
- **Fix:** `Interface.Builder.setMtu(1280)` eksplisit (`WARP_MTU` constant
  baru di `WarpTunnelManager`). Nilai 1280 **diverifikasi lewat riset**
  (bukan tebakan) — ini persis default yang dipakai app resmi Cloudflare
  WARP Android sendiri ("just like the official Android app", dikonfirmasi
  dokumentasi `wgcf`) dan juga default profil `wgcf` generate untuk
  kompatibilitas maksimal lintas jaringan. Untuk jaringan yang tidak
  mengalami degradasi MTU, nilai ini bisa dinaikkan manual (1400-1460) demi
  throughput sedikit lebih tinggi, tapi 1280 adalah pilihan teraman lintas
  device/jaringan tanpa perlu deteksi kondisi jaringan per-user.
- Tidak ada perubahan lain — endpoint fallback (`engage.cloudflareclient.
  com:2408`), persistent keepalive 25s, dan seluruh watchdog/auto-reconnect
  TETAP seperti v2.1.0 (sudah sesuai rekomendasi resmi, tidak disentuh).
- **Belum bisa diverifikasi lewat pengukuran throughput nyata** di device —
  sandbox sesi ini tidak punya akses jaringan/Gradle. WAJIB dites di
  device fisik: bandingkan kecepatan unduh/unggah + stabilitas streaming
  sebelum/sesudah update ini, idealnya di jaringan seluler (bukan cuma
  Wi-Fi, karena itu yang paling terdampak masalah MTU).

## v3.1.0 — Warm graphite pass: legibility + arah "matte premium" (2026-08-04)

> User arahan eksplisit: tingkatkan legibility lagi + ubah arah warna dari
> "kegelapan" (near-black v3.0.0/v3.0.1) menuju matte "native Android ultra
> premium & expensive". HANYA `Color.kt` diubah (nilai hex) + version bump —
> nol perubahan struktur file lain, otomatis merambat ke semua layar via
> `Theme.kt`, sama seperti pola v3.0.1.

- **Base background** (`ShieldBgDark`): `#0C0F0D` → `#17181A` — pindah dari
  near-black bertint hijau jadi graphite netral hangat. Masih dark theme,
  tapi tidak lagi terasa "lubang hitam" — pendekatan ini yang dipakai
  hardware premium (matte plastic Pixel/Sony dll), bukan maksimal gelap.
- Seluruh tangga elevasi di-derive ulang dari base baru, tetap menjaga
  filosofi jarak lebar dari v3.0.1 (setiap step ≥ ~4-5% lightness dari
  tetangganya): `ShieldSurface` `#1A1F1A`→`#212325`,
  `ShieldSurface2`/`ShieldSurfaceAlt` `#262E25`→`#2C2F30`,
  `ShieldSurface3` `#313A2F`→`#3A3D3D`.
- `ShieldGreen` (signal/accent): `#3ED696` → `#3FC993` — sedikit
  didesaturasi ("brushed jade" bukan "neon mint") supaya selaras arah warm/
  premium, bukan tech-neon. `ShieldGreenDark`/`ShieldAccentDim` disesuaikan
  proporsional.
- `ShieldWarning`: `#DDB264` → `#D3AD6E` — digeser ke arah brass/gold
  keruh, bukan amber lurus; gold muted terasa lebih "mahal".
- `ShieldWhite` `#F3F5F1`→`#F6F5F2` (off-white hangat, bukan putih klinis),
  `ShieldTextMuted` `#AEB9B0`→`#BFC4C0`, `ShieldTextFaint`
  `#828D85`→`#93988F` — diverifikasi ulang kontras terhadap base baru yang
  lebih terang: tetap ≥4.5:1 (muted) dan ≥3:1 (faint).
- `ShieldOutline` `#4A5346`→`#52564F` — hairline border disesuaikan supaya
  tetap kelihatan jelas di atas surface baru yang lebih terang.
- `versionCode`/`versionName`: 16/3.0.1 → 17/3.1.0.
- **Belum dikonfirmasi user via screenshot device** — sama seperti v3.0.1,
  WAJIB jadi hal pertama yang dicek di sesi berikutnya sebelum menganggap
  ini sudah cukup "premium"/legible.

## v3.0.1 — Fix kontras/legibility palet matte (2026-08-04)

> User laporan langsung dari device (screenshot): warna kegelapan, legibility
> tidak membaik sama sekali dari sebelum redesign. Root cause: v3.0.0 lolos
> "terlihat matte premium" di color-picker tapi jarak antar step elevation
> & warna teks terlalu rapat secara actual contrast ratio di panel device.
> HANYA `Color.kt` yang diubah (nilai hex saja) — nol perubahan struktur
> file lain, otomatis merambat ke semua layar via `Theme.kt`.

- `ShieldSurface` (kartu): `#14170F` → `#1A1F1A` — sebelumnya nyaris tak
  beda dari background, kartu "menyatu" dengan latar.
- `ShieldSurface2`/`ShieldSurfaceAlt` (chip ikon nav): `#1B1F17` → `#262E25`.
- `ShieldOutline` (hairline border kartu): `#262C24` → `#4A5346` —
  sebelumnya nyaris tidak kelihatan, sekarang batas kartu benar-benar
  terlihat.
- `ShieldTextMuted`: `#8B9690` → `#AEB9B0` — teks sekunder (subjudul,
  deskripsi WARP) sebelumnya di bawah ambang baca nyaman.
- `ShieldTextFaint`: `#5C655F` → `#828D85` — caption/footnote sebelumnya
  nyaris invisible di screenshot user.
- `ShieldGreen` (signal/accent): `#2FBE86` → `#3ED696` — sedikit lebih
  terang supaya ikon di chip nav & ring proteksi kelihatan jelas, tetap
  matte (bukan neon).
- `ShieldDanger`/`ShieldWarning` dinaikkan tipis untuk konsistensi step.

## v3.0.0 — Redesign UI/UX: identitas visual "Matte Graphite / Jade Signal" (2026-08-04)

> Perubahan presentasi layer saja — nol perubahan logic/state/ViewModel.
> Semua wiring `MainViewModel` & callback tetap sama persis.

- **Design system baru** (`ui/theme/`): `Color.kt` diberi palet matte
  graphite + jade signal (bukan neon acid-green), file baru `Type.kt`
  (skala tipografi + `ShieldMonoStat` untuk readout teknis monospace) dan
  `Shape.kt` (skala radius besar 10–34dp, ganti default Material 4/8/12dp).
  `Theme.kt` sekarang merangkai `colorScheme` penuh (bukan cuma 6 role) +
  typography + shapes, supaya komponen Material3 default di layar lain
  otomatis ikut ganti kulit tanpa disentuh satu per satu.
- **`HomeScreen.kt` didesain ulang total**: toggle proteksi jadi
  "Protection Ring" (disc matte + ring instrumen tipis, definisi lewat
  hairline border `ShieldOutline`, bukan drop shadow), stat blocked/allowed
  pakai angka monospace, 4 item navigasi digabung jadi satu "NavGroup" card
  bergaya grouped-list premium (khas Mullvad/ProtonVPN) lengkap divider
  hairline, WarpModeCard dan quality-row ikut diberi treatment sama.
- `DiagnosticsScreen.kt`: radius kartu disamakan skala baru (14dp → 20dp) +
  ditambah hairline border `ShieldOutline` supaya konsisten dengan layar
  lain.
- RulesScreen/WhitelistScreen/LogsScreen/OnboardingScreen **tidak diubah**
  — sudah otomatis ikut palet & shape baru lewat `MaterialTheme`
  (tidak ada override warna/shape hardcoded di file-file itu selain nama
  konstanta `Shield*` yang sudah diretint di `Color.kt`).

## v2.6.1 — Unit test dasar: DnsPacket + BlocklistManager (2026-08-03)

> Lanjutan langsung dari fokus "100% reliability" (v2.5.1, v2.6.0).
> Tidak ada perubahan perilaku APK — test tidak ikut ter-package ke APK
> rilis. Versi dinaikkan murni untuk pelacakan riwayat, sesuai konvensi
> proyek ini.

- **Baru: `app/src/test/java/.../vpn/DnsPacketTest.kt`** — membangun
  paket IPv4/UDP/DNS query sintetis secara manual (persis seperti yang
  diterima nyata dari tun interface), lalu memverifikasi: ekstraksi
  domain multi-label, transaction ID, port; penolakan paket cacat
  (terlalu pendek, bukan IPv4, bukan UDP, bukan port 53, QDCOUNT=0); dan
  `buildBlockedResponse()` menghasilkan balasan A-record 0.0.0.0 yang
  benar (alamat/port ditukar dengan benar, transaction ID cocok,
  checksum IPv4 valid).
- **Baru: `app/src/test/java/.../data/BlocklistManagerTest.kt`** —
  mengunci ulang (regression-lock) dua keputusan arsitektur paling
  penting di proyek ini: exact-match by default (v1.1.0, bukan
  parent-domain matching), critical allowlist yang tidak bisa
  di-override apa pun (v1.2.0), dan additive-safety remote list (v2.5.0:
  remote list gagal/kosong tidak pernah menghapus custom/default). Juga
  mencakup normalisasi (case-insensitive, trailing dot), format
  parseLine (hosts-file style, wildcard, komentar), diffing
  `setCustomBlocked`, dan whitelisted-app bookkeeping.
- **`app/build.gradle.kts`**: tambah `testImplementation("junit:junit:4.13.2")`.
- **Catatan jujur soal verifikasi** (lihat Batas Jaminan standing rule):
  kedua test file ini SUDAH ditelusuri baris-demi-baris secara manual
  terhadap implementasi asli untuk memastikan setiap assertion
  benar-benar konsisten dengan logic yang ada (bukan tebakan) — tapi
  sandbox saat ini tidak punya Gradle/JDK Android/koneksi internet untuk
  benar-benar menjalankan `./gradlew testDebugUnitTest`. **WAJIB
  dijalankan di CI atau lokal sebelum dianggap "lolos".**

## v2.6.0 — Crash Logger Bawaan (2026-08-03)

> **Peningkatan MAJOR, bukan fitur user-facing biasa — bagian dari fokus
> "100% reliability" yang diminta user. Ini menutup gap yang seharusnya
> ada sejak v1.0.0 tapi belum pernah diimplementasikan.**

- **Baru: `util/CrashLogger.kt`** — dipasang sekali di
  `AdShieldApp.onCreate()` (baris PERTAMA, sebelum kode lain di
  `onCreate()`, supaya crash startup apapun juga tertangkap). Menangkap
  SEMUA uncaught exception di proses app, menulis laporan crash lengkap,
  lalu selalu meneruskan (chain) ke handler sebelumnya (default Android)
  supaya perilaku crash normal (dialog "app berhenti", proses dimatikan)
  tetap sama seperti biasa.
- **Lokasi & format sesuai spesifikasi:**
  - **API 29+ (Android 10 ke atas):** ditulis via MediaStore ke folder
    publik `Documents/AdShield/logs/` — TIDAK menambah izin storage
    legacy apa pun.
  - **API 24–28:** MediaStore Documents collection tidak tersedia di versi
    ini, dan menulis ke folder publik butuh izin legacy
    `WRITE_EXTERNAL_STORAGE` yang sengaja tidak ditambahkan hanya untuk
    logging (sesuai aturan baku). Sebagai gantinya, log ditulis ke
    penyimpanan eksternal privat app
    (`Android/data/com.fdzaki.adshield/files/AdShield/logs/`) — tidak
    butuh izin apa pun di versi Android manapun. Ini trade-off yang
    disengaja & didokumentasikan (lihat PROJECT_STATE.md Assumption Log),
    bukan bug: lokasi sedikit kurang mudah ditemukan di Android lama,
    tapi tetap ada dan tetap bisa diambil lewat file manager.
  - Nama file: `crash_<yyyyMMdd_HHmmss>_<8-char UUID>.txt` — unik, tidak
    pernah bentrok/timpa.
  - Isi laporan: nama & versi app (versionName + versionCode), versi
    Android OS + API level, manufacturer & model device, timestamp
    kejadian, nama & ID thread yang crash, stack trace lengkap.
- **Retention FIFO maks 50 file** — begitu melebihi 50, file crash log
  TERLAMA dihapus duluan. Query/hapus HANYA menyasar file yang cocok
  folder + prefix nama milik logger ini sendiri (`crash_*`) — tidak
  pernah menyentuh file diagnostik lain milik user atau app lain.
- **Fail-safe total:** seluruh proses (buat folder, tulis file,
  format stack trace, query & hapus log lama) dibungkus try-catch.
  Kalau logging gagal karena alasan apa pun, kegagalan itu diam-diam
  diabaikan — tidak pernah menyebabkan crash kedua atau mengganggu
  proses crash asli.
- Tidak ada perubahan pada fitur/perilaku aplikasi yang terlihat user
  sehari-hari (tidak ada UI baru untuk ini) — murni infrastruktur
  diagnostik untuk sesi debugging berikutnya.

## v2.5.1 — FIX KRITIS: DNS non-blocklist tidak pernah diteruskan (2026-08-03)

> **Ini bukan fitur baru — perbaikan bug fondasi yang ditemukan saat audit
> stabilitas menyeluruh atas permintaan user ("berhenti update fitur,
> fokus 100% reliability").**

- **Root cause:** `AdBlockVpnService` memakai satu
  `Executors.newSingleThreadExecutor()` untuk DUA hal sekaligus: (1)
  menjalankan `runPacketLoop()` — loop `while(running)` yang tidak pernah
  selesai selama VPN aktif — dan (2) mengeksekusi `forwardToUpstream()`
  untuk tiap query yang tidak diblokir. Karena executor itu cuma punya
  1 thread dan thread itu sudah terpakai selamanya oleh loop, setiap task
  `forwardToUpstream` yang di-submit hanya masuk antrian dan **tidak
  pernah benar-benar jalan** sampai VPN dimatikan.
- **Dampak nyata sebelum fix ini:** domain yang di-blokir tetap dapat
  balasan (jalur itu sinkron langsung di dalam loop, tidak lewat
  executor) — tapi domain manapun yang TIDAK ada di blocklist (mayoritas
  trafik normal: media sosial, banking, streaming, dll) query DNS-nya
  tidak pernah diteruskan ke resolver upstream sama sekali. Dari sisi
  user ini terlihat seperti "internet mati total" begitu Ad-Block DNS
  dinyalakan.
- **Fix:** pisahkan jadi dua executor terpisah — `loopExecutor`
  (`newSingleThreadExecutor`, khusus `runPacketLoop`) dan
  `forwardExecutor` (`newFixedThreadPool(4)`, khusus
  `forwardToUpstream`). Tidak ada perubahan perilaku lain, tidak ada
  perubahan pada logic blocking/matching/whitelist.
- **Scope perubahan:** 1 file kode (`AdBlockVpnService.kt`, edit
  parsial, protected file — bukan full rewrite) + dokumentasi wajib.
  Tidak menyentuh arsitektur VPN (`10.111.222.1/32`, packet parsing,
  BlocklistManager) sama sekali.
- **Belum bisa diverifikasi:** perbaikan ini adalah analisis statis atas
  bug konkuren yang teridentifikasi jelas dari kode (single-thread
  executor + infinite loop + task tambahan ke executor yang sama = tidak
  mungkin jalan, ini bukan dugaan). Tapi seperti biasa, saya tidak bisa
  mengklaim sudah verifikasi lewat compile/runtime sungguhan di sandbox
  ini — WAJIB dicek lewat build CI + tes manual di device (buka browser/
  app apapun saat Ad-Block DNS aktif, pastikan internet tetap normal
  untuk domain yang tidak diblokir).

## v2.5.0 — DNS AdBlocker: Auto-Update Blocklist + UI Lebih Mudah (2026-08-03)

> **Fix (2026-08-03, build kedua):** CI gagal di build pertama —
> `HorizontalPager`/`rememberPagerState` di `OnboardingScreen.kt` (v2.4.0)
> pakai API experimental Compose Foundation tanpa anotasi `@OptIn`.
> Kotlin compiler treat itu sebagai error (bukan warning) di versi
> Foundation yang dipakai project ini. Fix: tambah
> `@OptIn(ExperimentalFoundationApi::class)` di fungsi `OnboardingScreen`.
> Tidak ada perubahan perilaku — murni anotasi compiler. versionCode/
> versionName TIDAK dinaikkan lagi karena build pertama v2.5.0 gagal total
> (tidak pernah menghasilkan APK), jadi ini masih rilis v2.5.0 yang sama,
> bukan versi baru.

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
