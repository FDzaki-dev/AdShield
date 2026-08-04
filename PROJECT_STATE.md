# PROJECT_STATE.md (Claude-facing, bukan untuk user)

Baca file ini SEBELUM lanjut kerja di proyek ini pada sesi baru mana pun.

## Status terakhir
- **v3.3.0 (2026-08-04) — WARP IPv6 toggle SELESAI jadi setting user,
  menutup eksperimen v3.2.1.** Hasil eksperimen v3.2.1 SUDAH DIKONFIRMASI
  user lewat data nyata: WARP+IPv6-off (42.3↓/4.67↑ Mbps) mengalahkan
  baseline tanpa VPN (31.3↓/3.43↑) di kedua arah — hipotesis "IPv6 WARP
  bottleneck di operator seluler user" terbukti benar, bukan kebetulan.
  Dari 3 opsi tindak lanjut yang ditawarkan, user pilih **toggle di
  Setting** (bukan dikunci permanen, bukan tes ulang lagi). Implementasi:
  `SettingsRepository.warpRouteIpv6` (default `false`, sesuai hasil
  pengukuran) + toggle baru di `HomeScreen` `WarpModeCard`. Konstanta
  eksperimen `ROUTE_IPV6` di `WarpTunnelManager` (v3.2.1) SUDAH DIHAPUS,
  digantikan pembacaan setting asli. **BELUM dikonfirmasi build CI +
  belum dicoba toggle-nya benar-benar berfungsi di device (nyalakan WARP
  dengan toggle ON, cek koneksi tetap connect meski upload mungkin
  lambat lagi — itu ekspektasi normal, BUKAN bug)** — WAJIB dicek di sesi
  berikutnya. Item eksperimen v3.2.1 di bawah SUDAH DITUTUP oleh entri
  ini, tidak perlu dicek ulang terpisah.
- v3.2.1 (2026-08-04) — EKSPERIMEN diagnostik upload SELESAI DIBUAT,
  BELUM DIUKUR.** User kirim data speedtest nyata: WARP mati 31.3↓/3.43↑
  Mbps/45ms, WARP aktif 26.6↓/0.48↑ Mbps/35ms (4.5G). Download -15% wajar,
  upload -86% TIDAK wajar — dipilih user untuk diisolasi lewat build tanpa
  rute IPv6 (`ROUTE_IPV6=false` di `WarpTunnelManager`, lihat CHANGELOG).
  **INI BUKAN FIX FINAL** — WAJIB jadi hal pertama dicek di sesi
  berikutnya: minta user ulangi speedtest WARP-aktif di jaringan seluler
  yang sama persis. Kalau upload membaik → IPv6 terbukti biang keladi,
  baru diskusikan apakah `ROUTE_IPV6=false` jadi default permanen (trade-
  off: IPv6 device bocor keluar tunnel). Kalau upload TETAP jelek →
  revert `ROUTE_IPV6=true` (satu baris) dan simpulkan ini keterbatasan
  uplink operator seluler yang teramplifikasi overhead WireGuard secara
  umum, bukan bug spesifik app — jangan lanjut coba-coba parameter lain
  tanpa data baru dari user.
- v3.2.0 (2026-08-04) — WARP MTU fix SELESAI**, respons langsung ke
  arahan user "fokus dongkrak performance WARP 100 persen" (setelah
  v3.1.0 dikonfirmasi lewat screenshot device, "lumayan lah" — dianggap
  cukup, TIDAK perlu iterasi warna lagi kecuali user angkat lagi).
  `WarpTunnelManager.buildConfig()` sebelumnya tidak pernah set MTU
  eksplisit (pakai default library) — root cause performa realistis di
  banyak jaringan seluler (fragmentasi paket WireGuard terenkapsulasi >
  MTU jalur nyata operator). Fix: `setMtu(1280)`, nilai yang **diverifikasi
  lewat riset** (bukan tebakan) — persis default app resmi Cloudflare WARP
  Android & profil `wgcf` untuk kompatibilitas maksimal. HANYA
  `WarpTunnelManager.kt` diubah (+ version bump) — endpoint
  fallback/keepalive/watchdog semua TETAP, tidak disentuh. **BELUM
  diverifikasi lewat pengukuran throughput nyata di device** — WAJIB jadi
  hal pertama yang dicek di sesi berikutnya: user perlu bandingkan
  kecepatan unduh/unggah + stabilitas sebelum/sesudah, idealnya di
  jaringan SELULER (paling terdampak masalah MTU, bukan cuma Wi-Fi). Kalau
  masih terasa lambat setelah ini, jangan langsung utak-atik MTU lagi
  sedikit-sedikit — tanya dulu detail: jaringan apa (WiFi/seluler,
  provider), lambat di download/upload/latency/keduanya, dibanding
  baseline tanpa VPN berapa.
- **v3.1.0 (2026-08-04) — Warm graphite pass DIKONFIRMASI** oleh user via
  screenshot device ("Lumayan lah") — arah matte premium/warm graphite
  dianggap sudah cukup untuk saat ini. Item pending konfirmasi visual dari
  sesi sebelumnya SUDAH DITUTUP, tidak perlu dicek lagi kecuali user
  mengangkat masalah visual baru.
- v3.0.1 (2026-08-04) — Fix kontras/legibility SELESAI, respons langsung
  ke laporan user disertai screenshot device (background/kartu/teks kegelapan,
  "gak membaik sama sekali" dari sebelum redesign v3.0.0). Root cause: nilai
  hex v3.0.0 lolos di color-picker tapi jarak antar elevation step & warna
  teks terlalu rapat secara contrast ratio nyata di panel OLED. HANYA
  `Color.kt` yang diubah (lihat CHANGELOG untuk diff nilai hex lengkap) —
  `Type.kt`/`Shape.kt`/`Theme.kt`/screens TIDAK disentuh, semua otomatis
  ikut lewat `MaterialTheme`. **BELUM dikonfirmasi user via screenshot baru**
  — WAJIB jadi hal pertama yang dicek di sesi berikutnya sebelum
  menganggap kontras sudah cukup. Kalau masih kurang, jangan cuma naikkan
  nilai lagi sedikit-sedikit — tanya user elemen mana spesifik yang masih
  susah dibaca (nama layar, ukuran teks, kondisi lighting) supaya perbaikan
  terarah, bukan trial-and-error hex value berulang.
- v3.0.0 (2026-08-04) — Redesign UI/UX SELESAI ("Matte Graphite / Jade
  Signal").** User minta explisit: matte, "native Android ultra premium &
  expensive", tetap khas app VPN. Scope MURNI presentation layer:
  `ui/theme/Color.kt` (palet baru, nama konstanta lama dipertahankan
  supaya layar lain otomatis ikut ganti kulit), `ui/theme/Type.kt` (BARU —
  skala tipografi + `ShieldMonoStat` monospace untuk readout teknis),
  `ui/theme/Shape.kt` (BARU — skala radius 10–34dp), `ui/theme/Theme.kt`
  (colorScheme penuh + typography + shapes dirangkai ke `MaterialTheme`),
  `ui/screens/HomeScreen.kt` (didesain ulang total: "Protection Ring"
  sebagai signature element, NavGroup grouped-list card, hairline border
  `ShieldOutline` di semua card pengganti shadow elevation),
  `DiagnosticsScreen.kt` (radius+border disamakan skala baru). **TIDAK ADA
  perubahan logic/ViewModel/state sama sekali** — semua callback
  `HomeScreen(...)` di `MainActivity.kt` tetap persis sama signature-nya.
  RulesScreen/WhitelistScreen/LogsScreen/OnboardingScreen sengaja TIDAK
  disentuh — sudah otomatis ikut tema baru karena tidak override warna/shape
  hardcoded. **BELUM dikonfirmasi build CI** (v3.0.0 belum pernah dicompile
  — dikerjakan tanpa akses Gradle/Android SDK di sandbox sesi ini, sama
  seperti v2.6.1) — WAJIB jadi prioritas #1 di sesi berikutnya, SEBELUM
  #1 lama (unit test v2.6.1) karena kalau v3.0.0 gagal compile, itu blocker
  yang lebih baru. Jalankan build CI dulu, baru `testDebugUnitTest`.
- **v2.6.1 (2026-08-03) — Unit test dasar SELESAI ditulis** untuk
  `DnsPacket` (`DnsPacketTest.kt`) dan `BlocklistManager`
  (`BlocklistManagerTest.kt`), + `testImplementation("junit:junit:4.13.2")`
  ditambahkan ke `app/build.gradle.kts`. **User memutuskan lanjut di sesi
  lain di titik ini** — belum sempat dijalankan sama sekali (tidak ada
  Gradle/JDK Android/internet di sandbox sesi ini). SESI BERIKUTNYA WAJIB
  mulai dari: jalankan `./gradlew testDebugUnitTest` dulu (lokal atau lewat
  step baru di CI kalau belum ada) sebelum lanjut ke apa pun — kalau ada
  test yang gagal, itu prioritas nomor satu, bukan lanjut ke WARP.
- v2.6.0 (2026-08-03) — Crash Logger bawaan SELESAI diimplementasikan
  (`util/CrashLogger.kt` + panggilan `install()` di baris pertama
  `AdShieldApp.onCreate()`). Ini menutup gap yang sudah tercatat sejak
  audit v2.5.1. Masih BELUM dikonfirmasi build CI + belum pernah memicu
  crash sungguhan di device untuk membuktikan file log benar-benar
  muncul di lokasi yang diharapkan — WAJIB jadi hal yang dicek di sesi
  berikutnya (bareng dengan #1 di atas dan v2.5.1 di bawah, idealnya satu
  sesi tes device sekaligus untuk ketiganya). Assumption teknis yang
  dibuat (lihat AI Assumption Log di bawah): MediaStore Files collection
  (bukan Downloads) dipakai untuk insert generik ke
  `Documents/AdShield/logs/` pada API 29+; belum diverifikasi lewat
  pengujian nyata bahwa file benar-benar muncul & terlihat di aplikasi
  Files/Berkas bawaan Android setelahnya — cuma diverifikasi lewat
  pengetahuan API resmi (ContentResolver.insert + RELATIVE_PATH), bukan
  observasi langsung.
- v2.5.1 (2026-08-03) — user secara eksplisit minta STOP semua kerja
  fitur baru, fokus 100% ke reliability/performance/optimalisasi jangka
  panjang. Audit stabilitas menyeluruh menemukan bug KRITIS di
  `AdBlockVpnService`: executor tunggal dipakai untuk loop paket (tidak
  pernah selesai) SEKALIGUS untuk forward ke upstream — task forward
  cuma masuk antrian dan tidak pernah jalan, artinya domain non-blocklist
  TIDAK PERNAH resolve selama VPN DNS aktif (lihat CHANGELOG v2.5.1 untuk
  detail lengkap). Sudah diperbaiki: dipisah jadi `loopExecutor` +
  `forwardExecutor` terpisah. **BELUM dikonfirmasi user via build CI +
  tes device fisik** — WAJIB minta konfirmasi ini di sesi berikutnya
  sebelum menganggap masalah selesai. Ditemukan juga saat audit yang sama:
  Crash Logger bawaan (wajib sejak awal sesuai standing instruction) TIDAK
  PERNAH benar-benar diimplementasikan di project ini — belum dikerjakan,
  jadi prioritas #2 di batch berikutnya (lihat "Yang HARUS dikerjakan").
- v2.5.0 build PERTAMA gagal di CI (`compileReleaseKotlin FAILED` —
  lihat insiden di bawah), sudah diperbaiki di sesi yang sama. ZIP hasil
  fix dikirim ke user, TAPI belum dikonfirmasi build KEDUA berhasil.
  **WAJIB minta log build terbaru dari user di sesi berikutnya kalau belum
  ada konfirmasi eksplisit "build sukses"** — jangan asumsikan fix ini
  otomatis berhasil hanya karena akar masalahnya sudah jelas.
- Belum dikonfirmasi sudah di-push ke GitHub oleh user untuk v2.5.0 —
  cek `git log` di sesi berikutnya sebelum asumsikan sudah ter-push
  (begitu juga v2.3.0 dan v2.4.0 kalau belum dikonfirmasi juga).
- Belum pernah dites di device asli. Mode WARP KHUSUSNYA belum pernah
  divalidasi end-to-end (registrasi + handshake + trafik lewat tunnel) di
  device fisik manapun — kode sudah diverifikasi API-nya cocok dengan
  javadoc resmi library, tapi belum ada bukti langsung "berhasil connect
  ke Cloudflare" dari device Ted. Ini TETAP prioritas #1 — user secara
  eksplisit memilih skip validasi dan lanjut ke fitur baru (2026-08-02),
  jadi fitur v2.1.0 (auto-reconnect, quality probe) dan v2.2.0 (shortcuts)
  JUGA belum pernah dibuktikan jalan nyata, sama seperti tunnel dasarnya.
- **Soal `log_failure_Adshield.txt` yang di-upload user bareng v2.1.0 zip
  (2026-08-02)**: isinya BUKAN bukti build gagal. Semua run di `gh run
  list` yang tercantum berstatus sukses (✓). Baris terakhir di file itu
  (`gh run view --log-failed` tanpa run ID) cuma gagal karena command-nya
  butuh run ID eksplisit saat non-interactive, bukan karena ada build yang
  benar-benar gagal. Belum ada bukti kegagalan CI nyata sampai sesi ini.

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

6. **Mode VPN Tunnel (WARP) — v2.0.0, package `warp/`.** Engine TERPISAH
   TOTAL dari AdBlockVpnService, pakai library resmi
   `com.wireguard.android:tunnel` (GoBackend), full-tunnel (`0.0.0.0/0`).
   Keputusan yang JANGAN dilanggar:
   - Dua mode (`AppMode.DNS_ADBLOCK` / `AppMode.WARP_TUNNEL`) TIDAK PERNAH
     boleh jalan bersamaan. Mutual exclusion ditegakkan di
     `MainActivity.startDnsService()`/`startWarpService()` (saling stop
     yang lain dulu). Kalau nambah titik masuk baru untuk start salah satu
     mode (mis. quick-settings tile, widget), WAJIB lewat fungsi yang sama,
     jangan panggil `AdBlockVpnService`/`WarpForegroundService` langsung.
   - `WarpRegistrationClient.API_VERSION` (saat ini `"v0a1922"`) adalah
     path segment yang Cloudflare ubah sewaktu-waktu tanpa pemberitahuan —
     ini BUKAN bug kalau tiba-tiba registrasi gagal, cek dulu apakah
     Cloudflare sudah ganti versi (lihat source wgcf terbaru).
   - Field request registrasi (`install_id`, `tos`, `key`, `fcm_token`,
     `type`, `model`, `locale`) meniru persis apa yang dikirim `wgcf` —
     JANGAN kurangi field ini kalau registrasi mulai gagal, kemungkinan
     malah perlu DITAMBAH (Cloudflare pernah memperketat validasi).
   - Endpoint peer WARP TIDAK di-hardcode — selalu dari hasil respons
     registrasi (`config.peers[0].endpoint.host`), dengan fallback
     `engage.cloudflareclient.com:2408` kalau field itu kosong.
   - API resmi library WireGuard (method names Interface.Builder/
     Peer.Builder/Config.Builder) sudah diverifikasi lewat javadoc.io
     resmi saat implementasi — kalau upgrade versi dependency
     `com.wireguard.android:tunnel`, cek changelog resminya dulu sebelum
     asumsikan API sama persis.

6b. **EXTRA_MODE_SWITCH pada intent STOP (v2.0.1) — JANGAN dihapus.**
   `AdBlockVpnService` dan `WarpForegroundService` masing-masing punya
   `EXTRA_MODE_SWITCH` pada intent ACTION_STOP mereka. Kalau flag ini
   `true`, service yang di-stop TIDAK menulis `AppMode.NONE` ke
   `SettingsRepository.activeMode`. Ini WAJIB ada karena kedua service
   punya CoroutineScope independen di Dispatchers.IO — kalau service A
   (yang di-stop) dan service B (yang baru start) sama-sama menulis
   `activeMode` tanpa koordinasi, urutan tulisnya tidak terjamin, dan bisa
   berakhir dengan `activeMode = NONE` walau salah satu mode sebenarnya
   jalan (merusak auto-restart setelah reboot). `MainActivity.startDnsService()`
   / `startWarpService()` SELALU kirim `isModeSwitch = true` ke stop-nya
   mode lain. Hanya tombol Stop langsung dari HomeScreen yang kirim
   `isModeSwitch = false` (default).

6c. **WARP watchdog & connection-quality probe (v2.1.0) — package `warp/`,
   semua di dalam `WarpTunnelManager`, JANGAN dipindah ke
   `WarpForegroundService`.** Keputusan yang JANGAN dilanggar:
   - Watchdog punya scope sendiri (`managerScope`, `SupervisorJob`) yang
     hidup selama singleton `WarpTunnelManager` hidup (selama proses app),
     BUKAN scope milik `WarpForegroundService`. Ini sengaja — kalau
     scope-nya ikut service, watchdog akan mati tiap kali service
     di-restart oleh sistem, padahal `WarpTunnelManager` sendiri (via
     `getInstance()`) tetap singleton yang sama.
   - Flag `desiredRunning` (bukan `state == UP`) yang jadi acuan "apakah
     seharusnya nyala" — supaya watchdog bisa membedakan "user memang
     matikan tunnel" vs "tunnel jatuh sendiri padahal harusnya nyala".
     JANGAN ganti jadi cek `state` langsung, race dengan proses reconnect
     yang sengaja set state DOWN sesaat sebelum UP lagi.
   - Sumber kebenaran "tunnel benar-benar jalan" BUKAN `Tunnel.State.UP`
     saja (itu cuma berarti interface WireGuard terbentuk), tapi probe
     nyata ke `https://www.cloudflare.com/cdn-cgi/trace` tiap siklus
     watchdog, dicek apakah body respons mengandung baris `warp=on`
     (atau `warp=plus`). Endpoint ini dipilih karena Cloudflare sendiri
     yang mengisi field itu — cuma valid kalau request beneran nyampe
     lewat edge WARP, bukan asumsi/tebakan.
   - Auto-reconnect pakai backoff eksponensial dengan cap
     `MAX_RECONNECT_ATTEMPTS = 5` per sesi connect() — supaya kalau
     memang tidak ada internet sama sekali, tidak menguras baterai
     retry tanpa henti. Counter direset otomatis tiap `connect()` baru
     (matikan-nyalakan manual dari user).
   - `Statistics` API resmi WireGuard (`Backend.getStatistics(tunnel)`)
     TIDAK punya method waktu-handshake (`lastHandshakeEpochMillis` dsb)
     di versi manapun yang diperiksa (2021–2023) — cuma
     `peers()/peerRx()/peerTx()/totalRx()/totalTx()/isStale()`. JANGAN
     asumsikan ada API handshake-time di versi library ini; itulah kenapa
     latensi diukur lewat trace-probe HTTP, bukan dari statistik
     WireGuard itu sendiri.

6d. **`WarpTunnelManager.WARP_MTU = 1280` (v3.2.0) — JANGAN diubah tanpa
   verifikasi ulang.** Ini bukan angka sembarang — cocok persis dengan
   default app resmi Cloudflare WARP Android & profil `wgcf`, dipilih
   karena paling aman lintas jaringan (mencegah fragmentasi paket
   WireGuard terenkapsulasi di jaringan seluler/NAT operator). Kalau nanti
   mau menaikkan (mis. 1400-1460 untuk throughput lebih tinggi di jaringan
   yang tidak mengalami degradasi MTU), itu WAJIB jadi opsi yang bisa
   dipilih user (bukan hardcode diam-diam diganti), karena nilai yang
   terlalu tinggi untuk jaringan tertentu bisa membuat tunnel yang
   sebelumnya jalan normal jadi tidak stabil.

6e. **`SettingsRepository.warpRouteIpv6` (v3.3.0, promosi dari eksperimen
   v3.2.1) — user-facing setting, default `false`.** Menggantikan
   penyimpangan sementara dari keputusan #6 ("full-tunnel 0.0.0.0/0 DAN
   ::/0") dengan pilihan eksplisit user, bukan hardcode diam-diam.
   Terbukti lewat data nyata (speedtest device user, lihat CHANGELOG
   v3.2.1/v3.3.0) bahwa IPv6 lewat WARP di operator selulernya bikin
   upload jatuh 86% — default `false` mengikuti bukti itu. JANGAN ubah
   default ini balik ke `true` tanpa data baru yang menunjukkan mayoritas
   user tidak mengalami masalah serupa. Dibaca ulang tiap `connect()`/
   `attemptReconnect()` di `WarpTunnelManager` (bukan snapshot sekali di
   awal) — TAPI WireGuard config sendiri tetap terkunci selama satu sesi
   tunnel berjalan (ganti toggle saat WARP aktif baru berlaku di
   reconnect/nyala-ulang berikutnya, bukan langsung).

7. **App Shortcuts (v2.2.0) — kontrak yang JANGAN dilanggar.**
   - Shortcut statis (Whitelist, Log) dideklarasikan di
     `res/xml/shortcuts.xml`, bukan runtime — labelnya tidak pernah
     berubah. Shortcut dinamis (toggle DNS, toggle WARP) dikelola HANYA
     lewat `util/ShortcutsManager.kt`, dipanggil dari `AdShieldApp`
     (collect `SettingsRepository.activeMode`, bukan dari MainActivity)
     supaya label tetap sinkron walau toggle terjadi dari Home screen,
     bukan dari shortcut itu sendiri.
   - `MainActivity.handleShortcutToggleIntent()` WAJIB baca mode lewat
     `viewModel.currentActiveMode()` (suspend, `.first()` langsung dari
     DataStore), BUKAN `viewModel.activeMode.value` — StateFlow itu
     `stateIn(..., WhileSubscribed(5000), AppMode.NONE)`, jadi `.value`
     di cold-start (sebelum ada UI yang subscribe) masih seed `NONE`,
     bukan mode asli. Kalau ini diganti balik ke `.value` demi
     "simplifikasi", toggle shortcut akan salah arah saat app dibuka
     dari kondisi ke-kill total lewat shortcut.
   - `ShortcutManagerCompat.setDynamicShortcuts()` (bukan add/update
     manual) dipanggil tiap `activeMode` berubah — ini SENGAJA mengganti
     seluruh dynamic set sekaligus (selalu kirim kedua shortcut DNS+WARP)
     supaya tidak perlu bookkeeping id per-shortcut yang rawan drift.

8. **DNS-mode error surfacing (v2.3.0) — `AdBlockVpnService.lastError`,
   companion-level StateFlow, pola SAMA seperti `WarpTunnelManager.lastError`.**
   Sebelum ini, kalau `Builder.establish()` gagal (exception atau
   mengembalikan null), `startVpn()` cuma `return` diam-diam — toggle Home
   screen balik ke off tanpa penjelasan. JANGAN hapus companion state ini
   atau kembalikan ke silent-return. Kalau nambah titik kegagalan baru di
   `startVpn()`, set `_lastError.value` dengan pesan yang jelas SEBELUM
   `return`, jangan biarkan diam-diam lagi.

8b. **Layar Diagnostik (`ui/screens/DiagnosticsScreen.kt`, v2.3.0) tidak
   punya sumber kebenaran sendiri.** Semua field yang ditampilkan dibaca
   langsung dari state yang sudah ada di `MainViewModel`/`Build.*`/
   `PackageManager` — JANGAN duplikasi state ke sini. Kalau nambah field
   diagnostik baru, tambahkan di `MainViewModel` dulu (atau baca langsung
   dari `Build`/`PackageManager` kalau memang statis), baru tampilkan di
   sini.

9. **Onboarding (v2.4.0) — `ui/screens/OnboardingScreen.kt`, flag
   `SettingsRepository.hasSeenOnboarding` (DataStore, default `false`).**
   Keputusan yang JANGAN dilanggar:
   - `MainActivity` MENAHAN render pertama `NavHost` (tampil `Box` kosong
     berwarna `ShieldBgDark`) sampai `startAtOnboarding` (nullable
     `Boolean`) terisi dari pembacaan suspend satu-kali
     `viewModel.currentHasSeenOnboarding()`. Pola ini SENGAJA sama seperti
     `currentActiveMode()` untuk shortcut — JANGAN baca lewat StateFlow
     `.value` di titik ini, seed value `stateIn()` belum tentu representasi
     data asli sebelum ada subscriber.
   - `startDestination` NavHost ditentukan sekali di awal (`"onboarding"`
     atau `"home"`) berdasarkan flag itu — bukan lewat `LaunchedEffect`
     yang navigate() belakangan, supaya tidak ada kedipan Home→Onboarding.
   - `OnboardingScreen` tidak punya akses langsung ke `SettingsRepository`
     atau `MainViewModel` — hanya terima 2 callback (`onFinish`,
     `onRequestBatteryExemption`) dari `MainActivity`, konsisten dengan
     pola screen lain (`HomeScreen` dsb.) yang stateless terhadap
     persistence, semua state datang dari luar.
   - **Diketahui & diterima:** karena flag ini kunci DataStore baru, user
     existing yang update dari versi sebelum v2.4.0 akan tetap melihat
     onboarding sekali (default `false` juga berlaku untuk instalasi lama
     yang belum pernah menulis kunci ini). Ini bukan bug — didokumentasikan
     di CHANGELOG.md sebagai dampak kecil yang diterima, bukan regresi.
     JANGAN "perbaiki" dengan menambah migrasi/deteksi versi lama kecuali
     user melapor ini benar-benar mengganggu.

10. **Auto-update blocklist (v2.5.0) — `BlocklistManager.kt` (sekarang
    juga berisi `class BlocklistUpdateWorker`), `SettingsRepository`
    (`custom_blocklist_url` — sudah ada dari sebelumnya tapi belum pernah
    dipakai; + `blocklist_last_updated`, `blocklist_update_status` baru).**
    Keputusan yang JANGAN dilanggar:
    - `BlocklistUpdateWorker` SENGAJA ditaruh di file `BlocklistManager.kt`
      yang sama (bukan file terpisah) — supaya batch ini tetap di bawah
      batas 10 file. Kalau nanti Worker ini berkembang jadi jauh lebih
      besar (misal nambah dukungan multiple URL), BOLEH dipisah ke file
      sendiri di batch yang lain — itu bukan pelanggaran, cuma refactor.
    - Domain dari blocklist remote (URL) disimpan di
      `remoteBlockedExact`/`remoteBlockedWildcardBases` — **set TERPISAH**
      dari `blockedExact`/`blockedWildcardBases` (yang berisi gabungan
      default+custom). JANGAN digabung jadi satu set. Alasannya:
      `loadRemoteList()` melakukan clear-then-fill total setiap kali
      dipanggil (bukan diff incremental seperti `setCustomBlocked`) — kalau
      remote & default/custom berbagi set yang sama, kegagalan/kekosongan
      fetch remote bisa ikut menghapus entry default/custom yang masih
      valid. Set terpisah membuat kegagalan blocklist remote murni
      additive-safe: paling buruk, remote list-nya kosong, tapi default +
      custom tetap utuh.
    - `BlocklistUpdateWorker` berjalan **in-process** (app ini tidak
      mendeklarasikan proses terpisah untuk WorkManager) — makanya boleh
      langsung panggil `BlocklistManager.getInstance().loadRemoteList(...)`
      dari dalam Worker setelah fetch sukses, supaya perubahan langsung
      berlaku tanpa perlu restart VPN. JANGAN asumsikan ini di proses
      terpisah kalau nanti ada perubahan ke `AndroidManifest.xml` yang
      menambah `android:process` pada WorkManager initializer/service.
    - Cache lokal ditulis dengan pola write-then-rename (`.tmp` lalu
      `renameTo`) — JANGAN diubah jadi direct-write, supaya proses yang
      mati di tengah unduhan tidak meninggalkan cache setengah-jadi yang
      akan dibaca lagi saat VPN start berikutnya.
    - `MainViewModel.reconcileBlocklistSchedule()` dipanggil di `init{}`
      dengan `ExistingPeriodicWorkPolicy.KEEP` — idempotent dengan sengaja,
      supaya tidak me-reset jadwal periodic yang sudah berjalan setiap kali
      ViewModel dibuat ulang (misal saat rotasi layar/navigasi).
    - Interval auto-update 24 jam + `Constraints` wajib `NetworkType.CONNECTED`
      — TIDAK retry agresif saat gagal (`Result.failure()`, bukan
      `Result.retry()`), konsisten dengan filosofi backoff WARP di
      `WarpTunnelManager` (jangan boros baterai kalau memang lagi tidak ada
      jaringan/URL-nya salah).
    - **Belum dikerjakan (sengaja disisihkan ke batch terpisah):** Custom
      DNS terenkripsi (DoH/DoT). Ini butuh perubahan arsitektur di
      `AdBlockVpnService` (yang sekarang forward plain-UDP ke upstream),
      BUKAN sekadar tambahan UI seperti batch ini. Lihat item #0 di bawah.

11. **`AdBlockVpnService` — dua executor terpisah (v2.5.1), JANGAN
    disatukan lagi.** `loopExecutor` (`newSingleThreadExecutor`) HANYA
    untuk `runPacketLoop()` (loop tak-berhenti selama VPN aktif).
    `forwardExecutor` (`newFixedThreadPool(4)`) HANYA untuk
    `forwardToUpstream()`. JANGAN kembalikan ke satu executor bersama —
    itu persis bug yang bikin domain non-blocklist tidak pernah resolve
    (lihat riwayat insiden 2026-08-03 / CHANGELOG v2.5.1). Kalau nambah
    jenis pekerjaan async baru di service ini, pertimbangkan apakah dia
    lebih mirip "loop yang tidak pernah selesai" (butuh thread sendiri)
    atau "task pendek per-event" (aman di `forwardExecutor`) sebelum
    memutuskan mau ditaruh di mana.

12. **`util/CrashLogger.kt` (v2.6.0) — kontrak fail-safe yang JANGAN
    dilonggarkan.** `CrashLogger.install()` dipanggil SEKALI, di baris
    PERTAMA `AdShieldApp.onCreate()` (sebelum kode lain apa pun) —
    JANGAN pindahkan ke tempat lain atau taruh setelah inisialisasi lain
    yang mungkin crash duluan sebelum logger terpasang. Kontrak yang
    tidak boleh dilanggar:
    - SELALU chain ke `previousHandler` di blok `finally`, apa pun yang
      terjadi saat logging (sukses atau gagal). JANGAN pernah membiarkan
      throwable "berhenti" di logger ini tanpa diteruskan — itu akan
      mengubah perilaku crash normal Android (dialog "app berhenti" +
      kill process) jadi tidak terjadi sama sekali.
    - SEMUA operasi I/O (buat folder, tulis file, query/hapus log lama)
      dibungkus try-catch yang membiarkan kegagalan diam-diam gagal —
      JANGAN tambahkan operasi baru di sini tanpa pembungkus yang sama,
      atau logger ini sendiri bisa jadi sumber crash kedua.
    - Retention (FIFO, maks 50) HANYA menghapus file yang cocok folder
      (`Documents/AdShield/logs/` atau padanan privat di API lama) DAN
      prefix nama (`crash_*`) milik logger ini sendiri — JANGAN perluas
      selection/filter query ini tanpa mempertahankan kedua syarat
      tersebut, supaya tidak pernah menyentuh file diagnostik lain milik
      user atau app lain (lihat Koeksistensi Log Diagnostik di
      instruksi baku).
    - Split API level (MediaStore untuk 29+, app-private external untuk
      di bawahnya) adalah keputusan SADAR untuk menghindari penambahan
      izin storage legacy — JANGAN "perbaiki" dengan menambah
      `WRITE_EXTERNAL_STORAGE` ke manifest demi menyatukan lokasi log di
      semua versi Android.

## Riwayat insiden kronologis

- **2026-08-03 (v2.6.1)**: Lanjutan #3 dari roadmap "stop fitur, fokus
  100% reliability" — unit test dasar untuk `DnsPacket` dan
  `BlocklistManager`. User memutuskan menghentikan sesi tepat di titik
  ini ("lanjut di sesi lain saja") sebelum test sempat dijalankan sama
  sekali. Bukan insiden — murni jeda kerja normal. Lihat "Status
  terakhir" di atas untuk instruksi resume yang jelas.
- **2026-08-03 (v2.6.0)**: User minta "peningkatan major, bukan minor" —
  lanjutan langsung dari arahan "stop fitur, fokus 100% reliability"
  (v2.5.1). Dikerjakan sesuai urutan prioritas #2 yang sudah disepakati:
  Crash Logger bawaan, gap yang sudah tercatat sejak audit sebelumnya
  tapi belum pernah benar-benar diimplementasikan sejak v1.0.0. Bukan
  insiden/bug — kerja infrastruktur diagnostik murni, tidak ada
  perubahan pada fitur/perilaku user-facing apa pun. Batch ini menyentuh
  2 file (`util/CrashLogger.kt` baru, `AdShieldApp.kt` — edit parsial,
  Application Class protected — hanya tambah 1 import + 1 baris
  pemanggilan di awal `onCreate()`, + `app/build.gradle.kts` versionCode/
  versionName), jauh di bawah batas maksimal batch (10 file).
- **2026-08-03 (v2.5.1, insiden BUKAN dari laporan user — ditemukan lewat
  audit stabilitas proaktif atas permintaan eksplisit "stop fitur, fokus
  100% reliability")**: `AdBlockVpnService` sejak v1.0.0 memakai
  `Executors.newSingleThreadExecutor()` yang sama untuk `runPacketLoop()`
  (loop tak-berhenti) dan `forwardToUpstream()` (dipanggil per-query lewat
  `executor.execute { ... }` di dalam loop itu sendiri). Karena hanya ada
  1 thread di pool dan thread itu selamanya sibuk menjalankan loop, setiap
  task forward yang di-submit tidak pernah benar-benar dieksekusi — bug
  ini SUDAH ADA sejak arsitektur awal (v1.0.0), bukan regresi dari batch
  manapun setelahnya, dan baru terlihat sekarang karena belum ada
  pengujian device fisik yang mengonfirmasi domain non-blocklist bisa
  resolve normal saat DNS mode aktif. Diperbaiki dengan memisah jadi
  `loopExecutor`/`forwardExecutor`. **Pelajaran:** analisis konkurensi
  (thread pool + tugas berjalan selamanya + tugas tambahan ke pool yang
  sama) harus jadi bagian rutin self-verifikasi ke depan, bukan cuma cek
  brace balance/import — kelas bug ini tidak akan terdeteksi checklist
  statis manapun yang sudah ada sebelum ini.
- **2026-08-03 (v2.5.0, build pertama GAGAL)**: User upload log CI
  (`logs_83484826529.zip`) — `compileReleaseKotlin FAILED` dengan 12 error
  identik: "This foundation API is experimental and is likely to change or
  be removed in the future" di `OnboardingScreen.kt`, semuanya menunjuk ke
  `rememberPagerState`/`HorizontalPager`/`pagerState.currentPage`/
  `animateScrollToPage`. **Akar masalah:** API itu ada di bawah
  `@ExperimentalFoundationApi` di versi Compose Foundation yang dipakai
  project ini (via compose-bom 2024.06.00) — Kotlin compiler treat
  penggunaan API experimental tanpa `@OptIn` sebagai ERROR saat kompilasi
  release (bukan cuma lint warning), makanya lolos dari semua static check
  batch v2.4.0 (brace/paren balance, cross-reference import) tapi gagal di
  compiler sungguhan. Ini persis kelas kegagalan yang sudah dicatat sebagai
  keterbatasan di "Scope of Guarantee" instruksi user: AI tidak bisa
  mengklaim sudah verifikasi hasil kompilasi kalau lingkungan eksekusi
  (sandbox ini) tidak mendukung Gradle/compiler Kotlin sungguhan. **Fix:**
  tambah `import androidx.compose.foundation.ExperimentalFoundationApi` +
  `@OptIn(ExperimentalFoundationApi::class)` di fungsi `OnboardingScreen`.
  Tidak ada perubahan file lain, tidak ada perubahan perilaku aplikasi.
  versionCode/versionName TETAP di 11/2.5.0 (build pertama gagal total,
  tidak pernah menghasilkan APK, jadi belum pernah benar-benar "rilis").
  **Pelajaran untuk batch selanjutnya yang pakai Compose Foundation API
  baru (Pager, LazyStaggeredGrid, dll.):** SELALU cek dokumentasi resmi
  apakah API itu masih `@ExperimentalFoundationApi` di versi Foundation
  yang terpasang, jangan asumsikan stabil hanya karena API-nya sudah lama
  ada di Compose secara umum.
- **2026-08-03 (v2.5.0)**: User pilih scope "ringan" dari kategori DNS
  AdBlocker: auto-update blocklist berkala + UI Aturan Kustom lebih mudah.
  DoH/DoT sengaja TIDAK dikerjakan di batch ini (disepakati eksplisit oleh
  user sebagai batch terpisah karena itu perubahan arsitektur, bukan
  UI/fitur tambahan biasa). Bukan insiden/bug — kerja fitur baru murni.
  Batch ini menyentuh 10 file (`BlocklistManager.kt` — full rewrite,
  BUKAN protected file, sekarang juga berisi `BlocklistUpdateWorker`;
  `SettingsRepository.kt`, `MainViewModel.kt` — edit parsial;
  `RulesScreen.kt` — full rewrite, bukan protected file;
  `AdBlockVpnService.kt` — edit parsial, satu baris; `app/build.gradle.kts`
  — edit parsial versionCode/versionName, protected; +
  `PROJECT_STATE.md`, `CHANGELOG.md`, `FILE_MANIFEST.txt`, `README.md`),
  PAS di batas maksimal batch (10 file) — `BlocklistUpdateWorker`
  ditaruh di file yang sama dengan `BlocklistManager` khusus supaya tidak
  melebihi batas ini alih-alih perlu Atomic Change Exception (lihat
  keputusan arsitektur #10). Jumlah file fisik proyek TIDAK bertambah
  (tetap 53) karena tidak ada file baru — cek `FILE_MANIFEST.txt`.
- **2026-08-03 (v2.4.0)**: User pilih batch "UX & Onboarding" dari daftar
  "Kekurangan AdShield" (kategori tersisa terakhir dari 2 pilihan: DNS
  AdBlocker atau UX/Onboarding). Ditambahkan layar Onboarding 4-slide baru
  + flag `hasSeenOnboarding` di `SettingsRepository`. Bukan insiden/bug —
  kerja fitur baru murni. Batch ini menyentuh 9 file (`SettingsRepository.kt`,
  `MainViewModel.kt`, `MainActivity.kt` — edit parsial, Navigation Graph &
  MainActivity protected, `OnboardingScreen.kt` baru, `app/build.gradle.kts`
  — edit parsial versionCode/versionName, protected, + `PROJECT_STATE.md`,
  `CHANGELOG.md`, `FILE_MANIFEST.txt`, `README.md`), di bawah batas maksimal
  batch (10 file), tidak perlu Atomic Change Exception. Tidak ada perubahan
  pada arsitektur VPN/matching/whitelist/mode WARP. Satu trade-off diketahui
  & didokumentasikan (lihat keputusan arsitektur #9): user existing yang
  update juga akan melihat onboarding sekali, karena flag defaultnya `false`
  untuk siapa saja yang belum pernah menulis kunci ini — diterima sebagai
  dampak kecil, bukan di-workaround dengan migrasi versi.
- **2026-08-03 (v2.3.0)**: User pilih batch "Monitoring & Diagnostik" dari
  daftar "Kekurangan AdShield" (opsi: status teknis + tombol salin
  clipboard). Selama analisis ditemukan (bukan regresi, gap desain sejak
  awal): `AdBlockVpnService` tidak pernah punya jalur pelaporan error sama
  sekali — semua kegagalan `establish()` VPN interface (exception maupun
  null return) hanya `return` diam-diam, beda dengan `WarpTunnelManager`
  yang sejak v2.0.0 sudah punya `lastError`. Ditambahkan
  `AdBlockVpnService.lastError` (companion StateFlow, pola sama), diekspos
  lewat `MainViewModel.dnsLastError`. Perubahan lain: `LogsScreen` ditambah
  pencarian + filter chip (client-side, aman karena data sudah dibatasi
  500 entri oleh `DomainLogDao.recentEntries()` sejak awal, tidak perlu
  query DB baru). Batch ini menyentuh 6 file kode (`AdBlockVpnService.kt`,
  `MainViewModel.kt`, `HomeScreen.kt`, `MainActivity.kt` — edit parsial
  navigasi, `LogsScreen.kt`, `DiagnosticsScreen.kt` baru) — pas di batas
  maksimal batch (10 file dengan dokumentasi), tidak perlu Atomic Change
  Exception. Tidak ada perubahan pada arsitektur VPN/matching/whitelist.

- **2026-08-02 (v2.2.0, ditemukan saat validasi sebelum packaging —
  PENTING, baca ini setiap sesi baru)**: `.gitignore` dan
  `.github/workflows/build.yml` TIDAK PERNAH menjadi bagian dari ZIP
  manapun yang dikirim Claude ke user — keduanya dibuat sekali lewat
  command Termux langsung saat setup awal proyek (`echo "release.keystore"
  >> .gitignore`, dan workflow CI dibuat/diedit manual di luar alur ZIP).
  Ini artinya file ZIP hasil Claude MEMANG selalu tidak berisi kedua file
  itu — bukan bug, ini fakta soal alur kerja proyek ini. TAPI ini bahaya
  laten: command update Termux standar (`find . -mindepth 1 -maxdepth 1 !
  -name '.git' -exec rm -rf {} +` lalu unzip) akan MENGHAPUS keduanya
  kalau tidak dikecualikan secara eksplisit, dan push berikutnya akan
  menghapusnya dari GitHub juga → CI mati. **WAJIB**: command Termux yang
  diberikan ke user setiap update HARUS mengecualikan `.gitignore` DAN
  `.github` dari langkah `rm -rf` (bukan cuma `.git`), KECUALI user
  eksplisit minta kedua file itu diperbarui/dihapus. Kalau suatu saat user
  minta Claude mengelola isi `.gitignore`/`build.yml` lewat ZIP juga
  (bukan manual lagi), council ini harus di-update dan kedua file itu
  wajib disertakan di ZIP dari titik itu seterusnya.

- **2026-08-02 (v2.2.0)**: User minta "semua fitur shortcut" untuk navigasi
  cepat tanpa buka app dulu. Ditambahkan Android App Shortcuts (tekan lama
  ikon launcher): 2 shortcut statis (Whitelist, Log — `res/xml/shortcuts.xml`)
  + 2 shortcut dinamis toggle (Nyalakan/Matikan DNS, Nyalakan/Matikan WARP —
  `util/ShortcutsManager.kt`, disinkronkan otomatis dari `AdShieldApp` tiap
  `activeMode` berubah). Selama implementasi ditemukan & langsung diperbaiki
  1 bug logika (bukan regresi dari kode lama, murni salah desain awal di
  batch ini): `viewModel.activeMode.value` dibaca langsung di cold-start
  sebelum ada yang subscribe ke StateFlow-nya, sehingga masih seed value
  `AppMode.NONE`, bukan mode asli tersimpan — bisa bikin toggle shortcut
  salah arah kalau app baru dibuka lewat shortcut dari kondisi ke-kill total.
  Diperbaiki dengan `MainViewModel.currentActiveMode()` (baca `.first()`
  langsung dari `SettingsRepository`, bukan lewat StateFlow yang di-stateIn).
  Efek samping: 2 method reference (`::stopDnsService`, `::stopWarpService`)
  sudah lambda sejak v2.0.1, tidak perlu diubah lagi di batch ini.
- **2026-08-02 (v2.1.0)**: User memberi daftar "Kekurangan AdShield"
  (gap analysis WARP/DNS/Monitoring/UX). Ditanya dulu (sesuai aturan
  analisis dampak arsitektur): (a) validasi device dulu atau lanjut fitur
  baru — user pilih lanjut fitur baru; (b) kategori batch-1 — user pilih
  "WARP UX (auto reconnect, indikator kualitas koneksi)". Sebelum menulis
  kode, verifikasi API `Backend.getStatistics()`/`Statistics` lewat
  javadoc.io resmi (bukan asumsi) — ditemukan API ini TIDAK expose waktu
  handshake, jadi desain latensi dialihkan ke trace-probe HTTP ke
  Cloudflare (lihat keputusan arsitektur #6c). Bukan insiden/bug — kerja
  fitur baru murni, tidak ada regresi pada mode Ad-Block DNS maupun WARP
  dasar (registrasi, mutual exclusion, EXTRA_MODE_SWITCH semua utuh).
- **2026-08-02 (v2.0.1)**: Audit kode menyeluruh atas permintaan user
  ("bawa aplikasi ke tahap finish, fokus WARP & WireGuard"). Ditemukan race
  condition: `startDnsService()`/`startWarpService()` di `MainActivity`
  men-stop mode lain lalu langsung start mode baru, dan KEDUA service
  menulis `SettingsRepository.activeMode` dari CoroutineScope terpisah
  tanpa urutan terjamin — hasil akhirnya bisa salah (`NONE` padahal ada
  mode aktif), merusak auto-restart setelah reboot untuk kasus tertentu.
  Diperbaiki dengan `EXTRA_MODE_SWITCH` (lihat keputusan arsitektur #6b).
  Efek samping: referensi method `::stopDnsService`/`::stopWarpService` di
  `MainActivity` diganti jadi lambda eksplisit (`{ stopDnsService() }`)
  karena Kotlin tidak menerapkan default parameter value pada method
  reference yang dicocokkan ke tipe fungsi `() -> Unit` — kalau dibiarkan
  referensi lama, build akan gagal kompilasi. Tidak ada fitur baru/behavior
  UI yang berubah, murni perbaikan korektnes internal.
- **2026-08-02 (v2.0.0)**: User minta fitur "overkill setara WARP/MASQUE/
  WireGuard". Diklarifikasi dulu sebelum kerja (sesuai aturan analisis
  dampak arsitektur di userPreferences) — hasil klarifikasi: mode terpisah
  mutually-exclusive, pakai WARP Cloudflare gratis, fokus WireGuard saja
  (MASQUE nyaris tidak ada library Android). Riset dilakukan via web search
  sebelum nulis kode (bukan asumsi dari training data) untuk: (1) apakah
  library resmi WireGuard Android mendukung reserved-bytes WARP — TIDAK,
  tapi dikonfirmasi lewat banyak sumber independen (wgcf, forum pengguna)
  bahwa profil WireGuard standar tetap bisa connect ke WARP tanpa itu;
  (2) format request/response API registrasi WARP — diambil dari source
  wgcf.py yang ter-arsip; (3) nama method exact di javadoc.io resmi
  library `com.wireguard.android:tunnel` untuk Interface.Builder/
  Peer.Builder/Config.Builder, supaya kode tidak asal tebak nama API.
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
warp/          WarpTunnelManager (GoBackend wrapper), WarpRegistrationClient (HTTP
               registrasi Cloudflare), WarpAccountRepository (DataStore identitas
               WARP), WarpForegroundService (wrapper notifikasi/watchdog), WarpAccount
data/          BlocklistManager (in-memory), SettingsRepository (DataStore, termasuk
               activeMode), InstalledAppsRepository, data/db/ (Room: log domain)
receiver/      BootReceiver (restart mode aktif setelah reboot), RestartReceiver
               (watchdog DNS), WarpRestartReceiver (watchdog WARP)
util/          Constants, AppMode (2 mode mutually-exclusive), ShortcutsManager
               (push 2 shortcut dinamis toggle DNS/WARP — lihat keputusan #7)
ui/            MainViewModel, ui/screens/ (Home, Whitelist, Rules, Logs), ui/theme/
```

## Yang HARUS dikerjakan di batch berikutnya (prioritas)

**Arahan user (2026-08-03, TERBARU — MENGGANTIKAN arahan #0 di bawah
untuk sementara): STOP semua kerja fitur baru. Fokus 100% ke
reliability/performance/optimalisasi. Jangan kembali ke daftar "Kekurangan
AdShield"/fitur baru manapun kecuali user eksplisit minta lanjut lagi.**
Urutan kerja yang disepakati:
  1. ✅ SELESAI (v2.5.1): fix deadlock executor `AdBlockVpnService`.
     Belum dikonfirmasi build+device — cek ini duluan di sesi berikutnya.
  2. ✅ SELESAI (v2.6.0): Crash Logger bawaan (`util/CrashLogger.kt`).
     Belum dikonfirmasi build CI + belum pernah memicu crash sungguhan
     di device untuk membuktikan file log benar-benar muncul — cek ini
     juga di sesi berikutnya, idealnya SEKALIAN dengan #1 (satu build
     CI, satu sesi tes device, dua hal dicek bareng).
  3. ✅ SELESAI (v2.6.1): Unit test dasar `DnsPacketTest.kt` +
     `BlocklistManagerTest.kt`. **SESI INI DIHENTIKAN ATAS PERMINTAAN
     USER TEPAT DI TITIK INI — belum sempat dijalankan sama sekali**
     (tidak ada Gradle/JDK Android/internet di sandbox). Jalankan
     `./gradlew testDebugUnitTest` di awal sesi berikutnya sebelum apa
     pun yang lain.
  4. Baru setelah #1-#3 terkonfirmasi solid (build sukses + test lolos +
     crash logger & fix deadlock tervalidasi di device fisik): lanjut
     validasi mode WARP di device fisik (lihat item #1 lama di bawah —
     prioritasnya TETAP tinggi, cuma urutannya sekarang setelah fondasi
     dibereskan dulu).

---

0. **Arahan user (2026-08-03, DIPERBARUI): user mengizinkan fitur baru
   dari daftar "Kekurangan AdShield", dikerjakan satu kategori/scope per
   batch.** Sudah selesai: Batch-1 WARP UX (v2.1.0), App Shortcuts di luar
   daftar asli (v2.2.0), Batch-2 Monitoring & Diagnostik (v2.3.0), Batch-3
   UX & Onboarding (v2.4.0), Batch-4 DNS AdBlocker scope ringan — auto-
   update blocklist + UI Aturan Kustom lebih mudah (v2.5.0). Satu-satunya
   item tersisa dari daftar asli "Kekurangan AdShield":
   - **Custom DNS terenkripsi (DoH/DoT).** SENGAJA disisihkan terpisah
     dari batch v2.5.0 karena ini perubahan arsitektur, bukan UI biasa:
     `AdBlockVpnService` saat ini forward query DNS ke upstream
     (`1.1.1.1`/`8.8.8.8`) lewat **plain UDP polos**. DoH/DoT butuh paket
     dibungkus TLS/HTTPS sebelum dikirim — nambah beban di packet loop
     yang sekarang sengaja didesain ringan (lihat keputusan #4). WAJIB
     tanya dulu ke user: behavior apa yang harus tetap sama (fallback ke
     plain DNS kalau DoH gagal? provider mana saja yang didukung dulu?)
     sebelum mulai coding — JANGAN langsung reka sendiri.
   Di luar daftar asli "Kekurangan AdShield" tapi berpotensi relevan kalau
   user minta: WARP belum divalidasi di device fisik manapun (baca bagian
   "Belum diverifikasi" di README.md) — ini butuh testing manual oleh
   user, bukan sesuatu yang bisa "dikerjakan" lewat kode semata.
   Mode Ad-Block DNS tetap dipertahankan apa adanya kecuali user minta
   perubahan eksplisit.
1. **PALING PENTING, MASIH TERTUNDA: uji mode WARP di device fisik Ted.**
   Ini belum pernah divalidasi end-to-end sama sekali (lihat "Status
   terakhir" di atas) — v2.1.0 MENAMBAH fitur di atas fondasi yang belum
   tervalidasi ini, jadi risiko menumpuk. Yang perlu dicek urut: (a)
   apakah `gradle assembleRelease` di CI sukses compile dengan dependency
   WireGuard baru, (b) apakah registrasi WARP sukses dapat respons dari
   Cloudflare, (c) apakah tunnel benar-benar UP dan trafik internet jalan
   lewat WARP (cek IP publik berubah), (d) BARU di v2.1.0 — apakah
   indikator kualitas & auto-reconnect berperilaku benar (coba matikan
   Wi-Fi/data sebentar saat WARP aktif, lihat apakah dot berubah merah lalu
   otomatis reconnect saat internet balik). Kalau gagal di titik manapun,
   laporkan pesan error persis dari UI/logcat.
2. Uji di device fisik: apakah watchdog AlarmManager di XOS Ted benar-benar
   mencegah service dibunuh saat app di-swipe dari Recents (berlaku untuk
   KEDUA mode, DNS dan WARP).
3. Uji whitelist per-app di device fisik Ted (Infinix XOS) — cek dulu versi
   Android-nya di atas/di bawah 10 sebelum menyimpulkan bug kalau ada laporan.
4. Belum ada unit test sama sekali — pertimbangkan test untuk `DnsPacket`
   parsing (paling kritis, paling gampang salah) dan `BlocklistManager`
   exact/wildcard matching (termasuk critical allowlist).
5. Tidak ada `gradle-wrapper.jar` binary di repo ini (dibuat tanpa akses
   internet). CI pakai `gradle/actions/setup-gradle` (menginstal Gradle
   langsung, tidak butuh wrapper). Kalau user mau pakai `./gradlew` secara
   lokal, jalankan `gradle wrapper --gradle-version 8.7` sekali di
   Termux/device dengan Gradle terpasang untuk generate wrapper jar-nya.
6. (Belum prioritas, jangan dikerjakan kecuali diminta): deteksi DoH,
   import blocklist dari URL custom, statistik per-app, MASQUE (nyaris
   tidak ada library Android siap pakai — lihat riwayat keputusan v2.0.0).

## Insiden CI mati (ditemukan 2026-08-03, root cause commit 1b4bc24 / v2.1.0)

`.github/workflows/build.yml` DAN `.gitignore` sempat terhapus dari git di
commit `1b4bc24` (v2.1.0) — command update Termux versi lama (sebelum
pengecualian `.gitignore`/`.github` ditambahkan ke `rm -rf`) ikut men-commit
PENGHAPUSAN kedua file itu ke GitHub. Akibatnya GitHub Actions tidak pernah
jalan untuk v2.1.0, v2.2.0, maupun v2.3.0 — 3 rilis berturut-turut tanpa CI
tervalidasi, baru disadari saat user tanya kenapa tidak ada run baru.
Dipulihkan 2026-08-03 lewat `git show 1b4bc24~1:<path>` (isi asli, bukan
ditulis ulang manual). Command update Termux SUDAH diperbaiki sejak v2.3.0
untuk mengecualikan `.gitignore`/`.github` dari `rm -rf` — insiden ini bukti
kenapa pengecualian itu wajib ada di command manapun ke depan, JANGAN pernah
dihapus.
