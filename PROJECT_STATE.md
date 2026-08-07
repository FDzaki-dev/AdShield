# PROJECT_STATE.md (Claude-facing, bukan untuk user)

Baca file ini SEBELUM lanjut kerja di proyek ini pada sesi baru mana pun.

## Status terakhir
- **v3.23.0 (2026-08-06) — Apple-Style batch 4/N: toggle-row fix
  diperluas ke HomeScreen.** CI v3.22.0 confirmed hijau. Item yang
  SENGAJA ditunda di v3.22.0 sekarang dikerjakan: `WarpModeCard`/
  `IkeV2ModeCard` di `HomeScreen.kt` dapat `Modifier.toggleable(role =
  Role.Switch)` yang sama persis polanya dengan Logs/Whitelist —
  `enabled`/haptic/`onToggle` logic PINDAH ke Row level, bukan aturan
  baru (nilai `enabled` sama persis dengan yang sudah ada di Switch
  masing-masing sebelumnya). `Switch` di bawahnya `onCheckedChange =
  null`. `TextButton` profil server & `ProtectionRing` tidak disentuh
  (di luar scope Row yang di-toggle / bukan Switch). Detail
  CHANGELOG.md v3.23.0. **BELUM DIKONFIRMASI CI v3.23.0** — titik uji
  paling penting: tap tepat di kotak Switch WARP/IKEv2 harus toggle
  sekali (bukan dobel), tap di judul/subtitle kartu juga harus ikut
  toggle.
- v3.21.1 — CI hijau + device confirmed (screenshot user). Release GitHub
  v3.21.1 ter-publish, APK 12.4MB ada di Assets. Screenshot HomeScreen di
  device: status bar hitam pekat
  menyatu dengan konten (fix drift bug v3.21.1 kerja), ProtectionRing +
  StatCard + WarpModeCard render dengan benar, tidak ada crash/glitch
  visual. **v3.21.0 DAN v3.21.1 sama-sama confirmed aman** — boleh lanjut
  batch berikutnya. Ringkas kerjaan v3.21.1: (1) `ProtectionRing` dapat
  press-scale animation (0.94x, tween 120ms, ripple Material diganti
  scale). (2) Debug sweep nemu 1 bug lama nyata: `colors.xml
  shield_bg_dark` (statusBar/navBar/windowBackground) = #0F1512, tidak
  pernah sinkron ke `ShieldBgDark` Compose manapun — di-fix ke #000000,
  sekalian 4 color resource unused dihapus. Detail lengkap CHANGELOG.md
  v3.21.1.
- v3.21.0 (2026-08-06) — Apple-Style redesign batch 1/N. User
  serahkan arah proyek sepenuhnya ke saya ("kamu putuskan sendiri...
  fokus Polish ala Apple-Style, debugging, eksekusi sampai matang") —
  keputusan saya: prioritas presentation-layer-only changes dulu (0 risiko
  struktural ke logic/state), verifikasi ketat tiap file (brace/paren +
  duplicate-import + constant-name diff, semua 0 masalah), TIDAK pakai API
  Material3 eksperimental yang saya tak bisa verifikasi 100% tanpa
  compiler (`SegmentedButton`) — pilih custom composable dari primitif
  terbukti sebagai gantinya. `Color.kt` retint total ke Apple System
  Colors dark-mode ASLI (bukan reka-reka — systemBackground/systemGray/
  systemGreen/systemRed/systemOrange + label-opacity technique), 15/15
  nama konstanta dipertahankan (0 call-site berubah). `RulesScreen.kt`
  dapat segmented-control ala iOS. 4 screen (`Diagnostics`/`Logs`/`Rules`/
  `Whitelist`) TopAppBar diratakan (flat edge-to-edge, blend ke background
  hitam). Detail lengkap + alasan tiap angka warna: CHANGELOG.md v3.21.0.
  **SENGAJA TIDAK disentuh:** `Shape.kt`/`Type.kt` (sudah dekat konvensi
  Apple, tidak ada celah konkret ditemukan), `HomeScreen.kt` (kandidat
  batch 2 — press-scale animation ProtectionRing, ditunda sampai batch ini
  lolos CI+device test dulu, supaya kalau ada bug lebih gampang diisolasi
  batch mana penyebabnya). **BELUM DIKONFIRMASI build CI** — WAJIB jadi
  hal pertama dicek di sesi berikutnya, SEBELUM lanjut batch 2/N apa pun.
- v3.20.1 (2026-08-06) — HOTFIX build CI gagal dari push v3.20.0.**
  User upload log CI — `compileDebugKotlin FAILED`, `HomeScreen.kt:174:41`
  + `:180:41` "Type mismatch: inferred type is Long but Int was expected".
  Root cause: helper baru `formatStatCount(count: Int)` (v3.20.0) ditulis
  TANPA grep dulu tipe asli `blockedCount`/`allowedCount` di
  `MainViewModel` — keduanya `StateFlow<Long>`, bukan `Int`. Fix:
  parameter jadi `Long`. 1 baris, 1 file. **Pelajaran:** grep tipe
  deklarasi `StateFlow` asal SEBELUM menulis signature parameter
  eksplisit untuk helper baru yang menerimanya — jangan asumsikan `Int`
  dari nama variabel yang terdengar seperti "hitungan". Detail lengkap
  CHANGELOG.md v3.20.1. **BELUM di-push ulang / belum dikonfirmasi CI
  hijau** — WAJIB jadi hal pertama dicek di sesi berikutnya.
- v3.20.0 (2026-08-06) — UI/UX polish pass batch 1/N: konsistensi
  Whitelist + spinner/haptic/format angka Home.** User minta "polish UI
  dan UX sampai matang" — di luar urutan roadmap audit eksternal (masih
  di Testing & Diagnostic, belum lanjut). Audit statis 6 screen: Rules/
  Logs/Diagnostics sudah cukup matang dari batch Feedback lama (v3.3.1/
  v3.3.2), TIDAK disentuh. `WhitelistScreen` (search field polos tanpa
  leading/trailing icon, tanpa empty-state, tanpa count row — beda pola
  dari Logs/Rules) dan `HomeScreen` (connecting state tanpa spinner, tidak
  ada haptic di kontrol paling sering dipakai, angka StatCard tanpa
  pemisah ribuan) yang diperbaiki. Detail lengkap per-file di
  CHANGELOG.md v3.20.0 — tidak diulang di sini. **SENGAJA TIDAK disentuh:**
  `ProtectionRing` "no fill animation" (keputusan desain v3.0.0, bukan
  gap), warna/tema/shape manapun (scope murni interaksi, bukan redesign).
  Verifikasi statis (brace/paren balance + lexer nested-comment) ke 2 file
  diubah — 0 masalah. **BELUM DIKONFIRMASI build CI** — cek dulu di sesi
  berikutnya, MASIH BARENG v3.19.0 yang juga belum pernah dicek (unit test
  `testDebugUnitTest` belum pernah dijalankan sama sekali sejak ditulis).
  Kalau user minta lanjut polish UI/UX lagi (batch 2/N): kandidat
  berikutnya — `OnboardingScreen` transisi antar-slide, TalkBack/
  accessibility label pass menyeluruh (belum diaudit eksplisit), atau
  balik ke roadmap audit eksternal (Testing & Diagnostic) sesuai urutan
  lama kalau user lebih mau itu duluan.
- v3.19.0 (2026-08-06) — Testing & Diagnostic audit batch 1/N: DnsPacket
  coverage gap ditutup.** Kategori TERAKHIR roadmap audit eksternal (semua
  4 kategori sebelumnya sudah dikerjakan: Reliability ✅, Concurrency &
  Lifecycle ✅, Security ✅ [1 fix nyata: WARP key encryption + 1 batch
  clean], Performance ✅ [clean]). User OK Performance cukup, lanjut ke
  sini. Temuan: `DnsPacketTest.kt` (v2.6.1) cuma cover `parse()`/
  `buildBlockedResponse()` — 6 method yang ditambah belakangan (v3.7.0
  DNS cache: `withTransactionId`/`qtypeOf`/`extractCacheableTtlSeconds`;
  v3.9.0 prefetch: `encodeQuestionSection`/`buildQueryMessage`) 0 test,
  padahal file ini didokumentasikan sendiri sebagai paling kritis di
  codebase. Fix: +11 test case baru di file yang sama (bukan file baru),
  detail lengkap di CHANGELOG.md v3.19.0. `BlocklistManagerTest.kt` sudah
  cukup lengkap (14 test) dari v2.6.1 — tidak disentuh. Verifikasi statis:
  lexer nested-comment + brace-balance ke seluruh `app/src` termasuk test
  baru — 0 masalah. **BELUM DIJALANKAN `./gradlew testDebugUnitTest`**
  (tidak ada Gradle/JDK Android di sandbox sesi manapun sejauh ini) — WAJIB
  jadi hal PERTAMA dicek di sesi berikutnya; kalau ada test gagal
  (kemungkinan besar typo offset byte manual di test baru), itu prioritas
  nomor satu. **Setelah ini terkonfirmasi lulus: SEMUA 5 kategori audit
  eksternal (Reliability/Concurrency&Lifecycle/Security/Performance/
  Testing&Diagnostic) selesai satu putaran penuh** — next milestone
  kembali ke item lama yang masih pending dari sebelum audit dimulai:
  validasi WARP di device fisik end-to-end (lihat "Yang HARUS dikerjakan"
  di bawah, item #1 lama — belum pernah divalidasi sama sekali sepanjang
  riwayat proyek ini).
- **v3.18.0 (2026-08-06) — Performance audit batch 1/N: CLEAN (0 fix).**
  User anggap Security cukup (2 batch, 1 fix nyata + 1 batch clean), lanjut
  kategori roadmap berikutnya: Performance. Dicek: (1) hot path packet loop
  (`DnsPacketLoop`) — alokasi per-paket minimal, cache-hit tanpa executor
  hop, fire-and-forget forward. (2) `BlocklistManager.isBlocked`/
  `matchesAnyWildcard` — O(1) per level hash-lookup, sudah didokumentasikan
  sejak batch performa lama, tidak ada regresi. (3) `DnsCache` — TTL-aware,
  size-capped, eviction murah. (4) `AppUidWhitelistChecker` — uid→package
  di-cache, hanya dipanggil kalau ada app whitelisted (`hasWhitelistedApps()`
  guard di caller). (5) `UpstreamForwarder` — socket pooling per-thread
  sudah ada. (6) Sisi UI: semua `LazyColumn` (`LogsScreen`, `RulesScreen`,
  `WhitelistScreen`) pakai `key` stabil; filter list pakai
  `remember(keys)`, tidak recompute tiap recomposition; tidak ada blocking
  I/O di `Dispatchers.Main`. **Kesimpulan: tidak ada temuan baru** — batch
  performa sebelumnya (MTU fix v3.2.0, DNS cache v3.7.0, prefetch, wildcard
  O(1) v3.16.x) sudah menutup hot path yang realistis untuk app ini. **0
  file diubah, tidak perlu ZIP baru.** Kalau user OK Performance dianggap
  cukup, kategori terakhir roadmap: **Testing & Diagnostic** (lihat catatan
  lama: 0 unit test untuk `DnsPacket`/`BlocklistManager` — kandidat utama).
- **v3.18.0 (2026-08-06) — Security audit batch 2/N: TLS/pinning +
  hardcoded secret scan, CLEAN (0 fix).** Lanjutan batch 1 (di bawah),
  user konfirmasi CI v3.18.0 hijau. Dicek: (1) `DohClient.
  protectingSocketFactory` pakai `SSLContext.getInstance("TLS").
  init(null,null,null)` — `null` trust manager berarti pakai **default
  sistem** (validasi normal), BUKAN trust-all; dibungkus cuma untuk
  nyisipkan `VpnService.protect()` per socket, aman. (2)
  `WarpRegistrationClient.postJson` ke `api.cloudflareclient.com` —
  `HttpURLConnection` di atas URL `https://` (validasi TLS standar lewat
  cast implisit ke `HttpsURLConnection`), dipanggil SEBELUM tunnel WARP UP
  (`ensureRegistered()` duluan di `connect()`) — jadi TIDAK perlu
  `protect()` di titik ini, dikonfirmasi lewat baca alur
  `WarpTunnelManager.connect()`. Tidak ada certificate pinning di manapun
  — dicatat sebagai desain (bukan requirement wajib untuk app kelas ini),
  bukan bug. (3) `proguard-rules.pro` +
  `isMinifyEnabled=true`/`isShrinkResources=true` sudah aktif di release
  build sejak sebelumnya — tidak ada perubahan diperlukan. (4) Scan
  hardcoded secret (pola api-key/password/token literal) di seluruh
  `app/src/main/java` — 0 hit. (5) `.gitignore` sudah exclude
  `release.keystore`/`*.jks`/`keystore.properties`; `build.gradle.kts`
  signing config baca dari env var/`keystore.properties`, tidak ada
  password hardcoded. **0 file diubah batch ini** — tidak perlu ZIP baru/
  push/version bump, cukup catatan roadmap. Kandidat Security batch 3
  (kalau user lanjut kategori ini): exported `ContentProvider` — app ini
  tidak punya, N/A; `QUERY_ALL_PACKAGES` di manifest (dipakai whitelist
  per-app, legitimate use-case) belum diaudit soal fingerprinting-vector
  risk. **Kalau user anggap Security cukup, kategori berikutnya sesuai
  urutan roadmap: Performance.**
- **v3.18.0 (2026-08-06) — Security audit batch 1/N: WARP private key
  plaintext → EncryptedSharedPreferences.** User konfirmasi v3.17.1 CI
  hijau, lanjut roadmap (urutan audit: Reliability ✅, Concurrency &
  Lifecycle ✅, sekarang **Security**, lalu Performance, lalu Testing &
  Diagnostic). Temuan: `WarpAccountRepository` (private key + access
  token WARP) masih plain `preferencesDataStore`, padahal
  `VpnProfileRepository` (profil OpenVPN/IKEv2/Shadowsocks) sejak v3.15.0
  sudah `EncryptedSharedPreferences`. Fix: migrasi total ke
  `EncryptedSharedPreferences` (AES256_SIV/AES256_GCM), pola identik
  `VpnProfileRepository`, dependency `security-crypto` sudah ada di
  `build.gradle.kts` (tidak perlu ditambah). API publik (property/method
  names, tipe `Flow`) tidak berubah — `WarpTunnelManager` 0 perubahan.
  `wasTunnelRunning`/`hasAccount` jadi `flow { emit(...) }` one-shot;
  aman karena diverifikasi via grep — kedua caller cuma `.first()`, tidak
  pernah `collect` reactive. **Trade-off yang perlu diketahui user:** akun
  WARP existing di device TIDAK ter-migrasi otomatis dari DataStore lama
  (`adshield_warp`, sekarang ditinggalkan) — WARP akan re-register sekali
  secara diam-diam di percobaan connect berikutnya pasca-update (setara
  `clearAccount()`, bukan bug). Verifikasi statis: grep — 0 hit
  `Log.*` yang menyentuh private key/token WARP di manapun; lexer nested-
  block-comment — 0 masalah di seluruh `app/src`. 1 file kode diubah
  (`WarpAccountRepository.kt`) + version bump — dalam Batch Lock normal.
  **BELUM di-push / BELUM dikonfirmasi build CI** — WAJIB jadi hal pertama
  dicek di sesi berikutnya. Item Security audit lain (belum dikerjakan,
  kandidat batch 2+): scan hardcoded secret lain, cek exported components
  manifest — sudah dicek sekilas di sesi ini, tidak ada masalah (semua
  `exported=true` punya alasan sah: launcher Activity, VpnService/QS-tile
  yang memang wajib exported untuk dibind sistem, dilindungi permission
  system-level yang sesuai), belum dicek: ProGuard/R8 minify config,
  TLS/certificate pinning untuk request ke Cloudflare API.
- **v3.17.1 (2026-08-06) — HOTFIX build CI gagal dari push v3.17.0.** User
  upload artifact `log_fail_20260806_023527_run31066001167.zip` —
  `kspDebugKotlin FAILED`: `AdBlockVpnService.kt:262:1 Unclosed comment`.
  **Root cause:** KDoc pembuka class (baris 28-43) berisi teks
  `` `vpn/dns/*` `` di baris 35 — literal `/*` di dalam teks itu dibaca
  compiler Kotlin sebagai **pembuka block comment baru bersarang** (beda
  dari Java/C: block comment Kotlin BOLEH nested). `*/` pertama yang
  ditemukan (baris 43) menutup comment nested itu, BUKAN comment luar —
  comment luar jadi tidak pernah tertutup sampai EOF, makanya error posisi
  baris 262 (baris terakhir file), bukan di baris 35 tempat akar masalah
  sebenarnya. Fix: ganti frasa itu jadi `` `vpn.dns` package `` (tanpa
  `/*` literal). **Verifikasi tambahan dilakukan**: simulasi lexer nested-
  block-comment Kotlin (skrip Python sekali-pakai, bukan cuma brace/paren
  count) dijalankan ke SELURUH file `.kt` di `app/src/main/java` +
  `app/src/test/java` — 0 file lain bermasalah.
  **Pelajaran untuk self-verifikasi ke depan:** kalau menulis KDoc yang
  menyebut path/package dengan wildcard (`foo/bar/*`), JANGAN literal
  seperti itu di dalam comment Kotlin — tulis `` `foo.bar` package `` atau
  pisahkan karakter `/` dan `*` supaya tidak pernah membentuk urutan `/*`
  literal di dalam block comment manapun. Checklist statis sebelumnya
  (brace/paren balance) TIDAK menangkap kelas bug ini karena `/*…*/` bukan
  `{…}`/`(…)` — kelas bug baru untuk daftar self-verifikasi.
  **BELUM di-push ulang / belum dikonfirmasi CI hijau** — WAJIB jadi hal
  pertama dicek di sesi berikutnya.
  **UPDATE (2026-08-06, sesi verifikasi ulang):** user re-upload ZIP state
  ini utuh (belum ada perubahan kode baru). Re-run simulasi lexer nested-
  block-comment Kotlin ke SELURUH `app/src/main/java` + `app/src/test/java`
  — **0 file bermasalah**, KDoc `AdBlockVpnService.kt` sudah pakai
  `` `vpn.dns` package `` (bukan literal `/*` lagi). FILE_MANIFEST.txt vs
  isi ZIP aktual: **94/94 cocok, 0 selisih**. Dotfiles (`.gitignore`,
  `.github/workflows/build.yml`) utuh. **Kesimpulan: fix v3.17.1 valid
  secara statis, siap di-push** — CI hijau tetap harus dikonfirmasi user
  setelah push (di luar jangkauan analisis statis sandbox ini).
- **v3.17.0 (2026-08-06) — Refactor God Class: `AdBlockVpnService` dipecah
  jadi 8 file, 0 perubahan behavior.** Respons ke audit eksternal user
  ("VPN Service terlalu besar / God Class", skor Coding 8/10). Sebelumnya
  ~600 baris dalam 1 class: lifecycle Service, packet loop, upstream
  forward + socket pooling, prefetch, whitelist per-app UID, notification
  builder, watchdog scheduler — semua inline.
  **File baru (package `vpn/dns/` kecuali disebut lain):**
  `UpstreamForwarder.kt` (forwardToUpstream + DoH-then-UDP fallback +
  socket pooling per-thread — persis `getOrCreateUpstreamSocket`/
  `discardUpstreamSocket`/`closeAllSockets` lama, cuma dipindah),
  `DnsPrefetcher.kt` (prefetchPopularDomains/prefetchOne, persis sama),
  `AppUidWhitelistChecker.kt` (isFromWhitelistedApp + uidToPackageCache),
  `DnsPacketLoop.kt` (runPacketLoop + writeBlockedResponse/
  writeCachedResponse — dijalankan di `loopExecutor`, TETAP terpisah dari
  `forwardExecutor` sesuai keputusan #11, tidak berubah), `DnsQueryLogger.kt`
  (logAndCount — counter + Room log). Di `vpn/` (bukan `vpn/dns/`, karena
  bukan spesifik-DNS): `VpnNotificationFactory.kt` (buildNotification),
  `VpnWatchdog.kt` (scheduleWatchdog, AlarmManager).
  **`AdBlockVpnService.kt` sekarang murni orchestrator** (~250 baris): cuma
  `onCreate`/`onStartCommand`/`startVpn`/`stopVpn`/lifecycle callbacks +
  wiring ke kolaborator di atas + companion object (`ACTION_START`/
  `ACTION_STOP`/`EXTRA_MODE_SWITCH`/`lastError`) — **API companion PERSIS
  SAMA, 0 file lain (MainActivity/MainViewModel/QS tiles/BootReceiver/
  RestartReceiver) perlu diubah**, sudah diverifikasi via grep menyeluruh
  sebelum batch ini dimulai (lihat keputusan arsitektur #15 di bawah).
  **Setiap method dipindah verbatim** (badan fungsi, komentar penjelas
  insiden/keputusan ikut dipindah ke file barunya) — TIDAK ada logic yang
  ditulis ulang/"dirapikan" sekalian, supaya risiko regresi minimal untuk
  batch refactor murni struktural ini. Cuma 2 comment-only doc-reference
  di file lain (`DohClient.kt`, `WarpEndpointSelector.kt`) diupdate supaya
  tidak menunjuk ke lokasi lama.
  **Atomic Change note:** batch ini menyentuh 8 file kode + `build.gradle.kts`
  (version bump) — di atas batas normal 10 tapi masih 1 modul (`vpn/`),
  dicatat sebagai Atomic Change (migrasi arsitektur, sesuai permintaan user
  eksplisit "Refactor VpnService God Class") bukan pelanggaran Batch Lock.
  **BUILD CI GAGAL** — lihat entri v3.17.1 di atas untuk fix.
- **v3.16.9 (2026-08-06) — Concurrency & Lifecycle audit batch 2/N:
  `BlocklistManager` race condition.** Target lanjutan yang sudah dicatat
  di batch v3.16.8 kemarin. 2 caller independen di thread berbeda
  (`MainViewModel` flow collector yang aktif selama app kebuka, dan
  `AdBlockVpnService.startVpn()`) sama-sama manggil
  `setCustomBlocked`/`setCustomAllowed`/`setWhitelistedApps` — dikonfirmasi
  lewat `grep`, bukan hipotetis. Fix (detail lengkap CHANGELOG.md v3.16.9):
  (1) `setCustomBlocked()` di-`synchronized(this)` (ada lost-update race
  di `customBlockedSnapshot`, plain var tanpa lock). (2)
  `allowedExact`/`allowedWildcardBases`/`whitelistedApps` diganti dari
  `clear()`-lalu-`add()` di atas `ConcurrentHashMap.newKeySet()` (ada
  jendela transient-empty yang kelihatan dari `isBlocked()`/
  `isAppWhitelisted()` di packet-loop thread) ke `@Volatile` immutable
  snapshot yang di-swap sekali atomik.
  **BELUM DIKONFIRMASI build CI v3.16.9** — cek dulu di sesi berikutnya.
  Diagnostik WARP dari user (packet loss 100%→37%, reconnectAttempts
  tetap 0) dikonfirmasi cold-start blip biasa, BUKAN bug — tidak perlu
  fix terpisah untuk itu.
- v3.16.8 (2026-08-06) — Concurrency & Lifecycle audit batch 1/N.**
  Kategori ke-2 checklist user (Reliability dianggap cukup setelah
  v3.16.7 + CI hijau dikonfirmasi user). Item checklist verbatim tidak
  tersimpan di sini (cuma judul kategori) — batch ini audit mandiri
  terhadap `AdBlockVpnService`/`WarpForegroundService`, 2 bug nyata
  ditemukan+fix (lihat CHANGELOG.md v3.16.8 untuk detail lengkap):
  (1) **`WarpForegroundService.onDestroy()`** tidak pernah cancel
  `scope` → `observeQualityForNotification()`'s `combine().collect{}`
  (tanpa kondisi berhenti sendiri) jalan SELAMANYA pasca-destroy,
  notify() ke Context yang sudah destroyed. Fix: `scope.cancel()` +
  `disconnect()` dibungkus `NonCancellable` supaya teardown tetap bersih.
  (2) **`AdBlockVpnService.onDestroy()`** tidak pernah shutdown
  `loopExecutor`/`forwardExecutor` → 5 thread non-daemon bocor tiap
  toggle DNS-mode off→on (executor baru dibuat tiap `onCreate()`, yang
  lama tak pernah di-shutdown). Fix: shutdown keduanya di `onDestroy()`.
  `serviceScope`-nya SENGAJA tidak di-cancel (lihat alasan di
  CHANGELOG.md — semua coroutine di dalamnya sudah self-terminate lewat
  `running.get()`, cancel paksa cuma berisiko motong write
  `settingsRepository` in-flight).
  **BELUM DIKONFIRMASI build CI v3.16.8** — cek dulu di sesi berikutnya.
  **Belum diaudit (lower-priority, dicatat bukan dilupakan):**
  `IkeV2VpnEngine.engineScope`/`pollJob` tidak pernah di-cancel dari
  `MainViewModel` (tidak ada `onCleared()` override) — prioritas rendah
  karena profil IKEv2 tetap jalan di level OS terlepas app process,
  dan `MainViewModel` di app single-Activity ini praktis hidup seumur
  proses. Angkat lagi kalau user eksplisit minta atau kalau nanti ada
  laporan symptom terkait.
- v3.16.7 (2026-08-06) — Reliability audit batch 3/N: captive portal
  detection.** Item ke-3 checklist Reliability user. Pakai
  `NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL` (flag resmi Android,
  sama dengan notifikasi sistem "Sign in to network") di `onCapabilitiesChanged`
  pada `NetworkCallback` yang sudah ada — bukan menebak dari pola gagal
  probe. Saat portal aktif: `lastError` diganti pesan spesifik + budget
  `consecutiveFailures`/`reconnectAttempts` DI-FREEZE (bukan dihabiskan
  percuma retry yang pasti gagal). Saat portal capability hilang: langsung
  `attemptReconnect(immediate=true)`, tidak nunggu tick 25 detik
  berikutnya, budget yang di-freeze tadi masih utuh. State
  `captivePortalDetected` diekspos publik tapi BELUM diwire ke
  HomeScreen/UI di batch ini (`lastError` sudah cukup untuk membedakan
  pesannya di UI existing). **BELUM DIKONFIRMASI build CI v3.16.7** — cek
  dulu di sesi berikutnya sebelum lanjut item checklist berikutnya.
  Sisa checklist Reliability: (a) verifikasi runtime networkCallback
  setelah airplane-mode off/on — ini murni testing device, bukan kerja
  kode; (b) unit test retry registrasi v3.16.5 / give-up fix v3.16.6 —
  butuh interface/DI dulu untuk `WarpRegistrationClient`/`GoBackend`
  supaya bisa di-mock di JVM test (bukan device test).
- v3.16.6 (2026-08-06) — Reliability audit batch 2/N: keputusan failover
  + fix bug give-up state.** User serahkan keputusan failover WARP↔DNS ke
  Claude. **Keputusan (final, jangan diubah tanpa user minta eksplisit):
  TIDAK ADA auto-switch mode.** WARP dipilih user untuk enkripsi penuh;
  auto-fallback diam-diam ke DNS-only menurunkan proteksi tanpa consent
  saat itu terjadi — melanggar prinsip dasar VPN app. Yang diperbaiki
  sebagai gantinya: bug nyata di mana setelah `MAX_RECONNECT_ATTEMPTS`
  habis, watchdog TETAP manggil `attemptReconnect()` tiap 25 detik
  SELAMANYA tanpa pernah benar-benar berhenti (reconnectAttempts cuma
  reset oleh probe sukses) — padahal pesan errornya bilang "dihentikan
  sementara". Fix: watchdog & network-watcher benar-benar di-cancel,
  interface di-tear-down bersih (reuse pola `disconnect()`), begitu give-up
  terjadi. Efek: begitu WARP menyerah, trafik balik ke jalur normal TANPA
  proteksi (fail-open, bukan fail-closed) — dipilih sengaja karena
  fail-closed total bisa membuat user kehilangan internet tanpa notifikasi
  jelas kalau app tidak sedang dibuka; `warpLastError` sudah tersambung ke
  `HomeScreen.kt` jadi begitu user buka app, errornya jelas.
- v3.16.5 (2026-08-06) — Reliability audit batch 1: retry+backoff untuk
  registrasi WARP.
- v3.16.3 (2026-08-06) — Fix blind spot diagnostik CI (bukan fix bug
  aslinya). Run CI 31051130336 gagal tapi artifact `log_fail_*`-nya cuma
  berisi `FAILURE_SUMMARY.txt` 3 baris — TIDAK ADA info penyebab sama
  sekali (kemungkinan besar: compile error Kotlin di test sourceset yang
  ditambah v3.16.2, yang tidak menulis file report). Fix: console output
  step test & build sekarang di-`tee` ke log file dan SELALU dicopy ke
  fail-logs (tidak bergantung pola find report/mapping). **PENYEBAB ASLI
  KEGAGALAN RUN 31051130336 MASIH BELUM DIKETAHUI** — perlu run CI baru
  (v3.16.3) yang kalau gagal lagi, artifact-nya sekarang seharusnya berisi
  `test-output.log`/`build-output.log` dengan pesan error asli.
- **User memberi audit checklist eksternal** (Reliability, Concurrency &
  Lifecycle, Security, Performance, Maintainability — lihat pesan chat
  untuk daftar lengkap) sebagai prioritas kerja berikutnya, urutan:
  1) Reliability (failover/recovery/reconnect/backoff/network change),
  2) Concurrency & Lifecycle, 3) Security, 4) Performance, 5) Testing &
  Diagnostic. **JANGAN mulai item ini sebelum CI v3.16.3 dikonfirmasi
  lulus** — menumpuk fitur baru di atas build yang belum diketahui
  statusnya adalah pola risiko yang sama persis dengan insiden WARP
  (v2.1.0 menambah fitur di atas fondasi belum tervalidasi).
- v3.16.2 (2026-08-06) — Wire unit test ke CI (static review, bukan batch
  fitur). Ditemukan lewat static review kalau `DnsPacketTest.kt` /
  `BlocklistManagerTest.kt` (sudah ada di repo & manifest sejak sesi
  sebelumnya) TIDAK PERNAH dijalankan CI — workflow langsung
  `assembleRelease` tanpa step test. Tambah step `gradle testDebugUnitTest`
  sebelum build APK + perluas failure diagnostics buat include
  `test-results/**/*.xml`. **BELUM DIKONFIRMASI: run CI v3.16.2 sendiri**
  (tidak ada Gradle/Android SDK/network di lingkungan kerja sesi ini, jadi
  ini murni analisis statis — bukan hasil build/test sungguhan). Cek run
  CI v3.16.2 dulu di sesi berikutnya.
- **v3.16.1 (2026-08-06) — Fix CI (bukan batch fitur).** Cek build CI yang
  wajib dilakukan (tertunda sejak v3.13.0, lihat entri v3.16.0 di bawah)
  akhirnya dilakukan lewat log GitHub Actions yang diupload user manual
  (raw log archive run gagal). **Hasil: v3.16.0 FAILED di
  `minifyReleaseWithR8`** — R8 "Missing class" untuk
  `com.google.errorprone.annotations.*` dan `javax.annotation.*`
  (referenced dari `com.google.crypto.tink.*`, ditarik transitif oleh
  `androidx.security:security-crypto:1.1.0` yang dipakai
  `VpnProfileRepository` sejak sebelum v3.16.0 — bukan regresi dari batch
  IKEv2). Fix: `-dontwarn` di `proguard-rules.pro` (annotation compile-time
  ini memang tidak ada di runtime classpath, jadi `-dontwarn` benar, bukan
  `-keep`). **Sekaligus nambah workflow step failure-log artifact**
  (`log_fail_<timestamp>_run<id>`, lihat CHANGELOG.md v3.16.1) supaya
  next time gagal, cukup download artifact kecil itu — gak perlu lagi user
  manual download+upload raw log archive kayak sesi ini.
  **BELUM DIKONFIRMASI**: build CI utk v3.16.1 sendiri belum ada run baru.
  **WAJIB cek run CI v3.16.1 dulu di sesi berikutnya** — kalau masih gagal,
  cek artifact `log_fail_*` yang baru (bukan raw log archive lagi).
- v3.16.0 (2026-08-06) — Batch: layar konfigurasi IKEv2 + wire ke UI
  (lihat CHANGELOG.md untuk daftar file diubah lengkap).** Menutup gap yang
  dicatat eksplisit di entri v3.15.0 di bawah ("IKEv2 belum bisa diwire,
  belum ada UI-nya sama sekali"). Form profil (server/identity/
  username+password) + `IkeV2ModeCard` di Home screen, driven dari
  `MainViewModel` lewat `IkeV2VpnEngine` langsung (bukan Service — IKEv2
  dikelola `VpnManager` di level OS). Mutual exclusion dengan DNS/WARP
  ditambahkan di `MainActivity` (start salah satu mode = stop dua lainnya).
  **Kesenjangan yang diketahui dari batch ini (bukan bug, cakupan
  sengaja dipersempit — lihat CHANGELOG.md v3.16.0):** belum ada
  boot-persistence/QS tile untuk IKEv2, belum ada form auth sertifikat,
  split-tunnel per-app memang tidak bisa (batasan API `Ikev2VpnProfile`).
  **BELUM DIKONFIRMASI build CI** — antre bareng v3.13.0/v3.14.0/v3.15.0,
  keempatnya belum pernah dicek sama sekali. **WAJIB cek build CI dulu di
  sesi berikutnya sebelum lanjut kerja apa pun lagi** (termasuk sebelum
  balik ke Batch 4 Xray-core yang masih ditunda, lihat entri v3.15.0).
- v3.15.0 (2026-08-05) — Batch: wire WARP ke VpnEngine di titik drive
  nyata.** Riset Batch 4 (Shadowsocks/VLESS via Xray-core, lihat entri
  v3.12.0 poin 4 di bawah) menemukan **TIDAK ADA AAR resmi Xray-core di
  Maven/JitPack** — cuma source Go `2dust/AndroidLibXrayLite` yang harus
  di-compile sendiri lewat `gomobile bind` + NDK + toolchain Go, BUKAN
  sekadar tambah dependency Gradle. CI sekarang cuma Gradle/JDK — nambah
  Go+gomobile+NDK ke workflow adalah scope/risiko sekelas OpenVPN (yang
  sudah DIBATALKAN PERMANEN, lihat entri v3.14.0). **User memutuskan
  (2026-08-05): skip Batch 4 dulu, alihkan ke menuntaskan wiring engine
  yang SUDAH jadi (WARP adapter v3.13.0, IKEv2 v3.14.0) ke UI dulu** —
  **UPDATE (2026-08-06): status ditingkatkan dari "ditunda" jadi
  DIBATALKAN PERMANEN — sejajar OpenVPN.** User memutuskan skip protokol
  yang "banyak mudharatnya", fokus ke yang instant/native kayak
  WARP+WireGuard (dan IKEv2, sama-sama platform API tanpa dependency
  pihak ketiga). Alasan sama seperti OpenVPN: Xray-core butuh
  Go+gomobile+NDK toolchain sendiri (bukan sekadar dependency Gradle) +
  risiko lisensi. **JANGAN diangkat lagi kecuali user eksplisit minta
  DAN eksplisit menerima kompleksitas build Go/gomobile serta risiko
  lisensinya** — sama seperti syarat OpenVPN di atas. Kalau suatu saat
  diangkat lagi, mulai dari research di atas
  (jangan ulang cari AAR, sudah pasti tidak ada — opsinya cuma bangun
  sendiri via gomobile di CI, atau ganti pendekatan sepenuhnya).
  **File diubah:** `warp/WarpForegroundService.kt` — SATU-SATUNYA titik
  drive lifecycle WARP nyata di seluruh app (dikonfirmasi via grep: MainActivity/
  HomeScreen/BootReceiver/WarpTileService semua cuma kirim Intent ke
  service ini via `ACTION_START`/`ACTION_STOP`, TIDAK ADA yang panggil
  `WarpTunnelManager` langsung selain file ini dan `MainViewModel`
  untuk state read-only + `forgetWarpAccount()`). `onStartCommand()`
  sekarang connect/disconnect lewat `WarpVpnEngineAdapter` (instance baru,
  field `warpEngine`), bukan `tunnelManager` langsung lagi untuk itu.
  **Gap desain yang ditemukan & diselesaikan saat wiring:**
  `VpnEngine.connect()` return `Unit`, BUKAN `Boolean` sukses seperti
  `WarpTunnelManager.connect()` lama yang dipakai `WarpForegroundService`
  untuk memutuskan `settingsRepository.setActiveMode(WARP_TUNNEL)`. Fix:
  setelah `warpEngine.connect(config)` return, observe
  `warpEngine.state.first { Connected atau Error }` — sesuai kdoc
  `VpnEngine.kt` sendiri yang memang bilang "callers should still observe
  state rather than rely solely on this call returning". `activeMode`
  cuma di-set kalau hasilnya `Connected`, sama seperti behavior boolean
  lama, tapi sekarang lewat state observation bukan return value.
  **SENGAJA TIDAK diubah (baca dulu sebelum "memperbaiki"):**
  - `tunnelManager` (field `WarpTunnelManager` lama) TETAP ada di file
    yang sama, dipakai apa adanya di `observeQualityForNotification()`/
    `buildNotification()` — notifikasi butuh `WarpConnectionQuality`
    (latency, trafficConfirmed, reconnectAttempts) dan `Tunnel.State`
    mentah, yang `VpnEngineState` SENGAJA tidak bawa (lihat kdoc
    `VpnEngineState.kt`). `tunnelManager` dan `warpEngine` (adapter)
    membungkus `WarpTunnelManager.getInstance()` SINGLETON YANG SAMA —
    ini BUKAN dua sumber kebenaran bersaing, cuma dua "lensa" beda atas
    satu instance. JANGAN hapus `tunnelManager` demi "konsistensi" tanpa
    lebih dulu memperluas `VpnEngineState` untuk bawa data kualitas —
    itu perubahan interface terpisah, bukan bagian batch ini.
  - `MainViewModel` (warpState/warpLastError/warpConnecting/warpQuality/
    `forgetWarpAccount()`) TIDAK disentuh — itu semua observasi read-side
    untuk Diagnostics screen atau aksi (`forgetAccount()`) yang memang
    bukan bagian kontrak `VpnEngine` sama sekali (bukan connect/disconnect).
  - **IKEv2 (v3.14.0) BELUM bisa "diwire" ke UI mana pun — bukan
    terlewat, memang belum ada UI-nya sama sekali di app ini.**
    `VpnProtocolConfig.IkeV2` butuh server address + identity + auth
    (cert alias ATAU username+password) — tidak ada layar/form input
    untuk itu di mana pun, beda dengan WARP yang sudah punya toggle Home
    screen sejak lama. "Wiring" IKEv2 sungguhan berarti BIKIN layar
    konfigurasi baru dulu (form input server, pilih metode auth, persist
    ke `VpnProfileRepository`) — itu FITUR BARU, bukan sekadar
    menyambungkan kabel ke UI yang sudah ada seperti WARP tadi. **WAJIB
    tanya user dulu soal UX-nya sebelum mulai coding** (pola yang sama
    dipakai untuk DoH/DoT dulu di keputusan #0 — lihat "Belum
    diverifikasi/tertunda" di bawah), JANGAN reka sendiri bentuk form-nya.
  **BELUM DIKONFIRMASI build CI** untuk v3.15.0 ini — DAN masih menumpuk
  dari v3.14.0 serta v3.13.0 yang JUGA belum pernah dicek sama sekali.
  Cek build CI dulu di sesi berikutnya, idealnya sekali jalan untuk
  ketiga versi ini sekaligus (0 kode fungsional WARP/DNS lama berubah di
  ketiganya, jadi 1 build run cukup mewakili ketiganya).
- v3.14.0 (2026-08-05) — Batch 3/N: IKEv2 native engine, MENGGANTIKAN
  OpenVPN di roadmap.** **KEPUTUSAN BESAR:** riset (web search, bukan
  asumsi) menemukan OpenVPN TIDAK PUNYA jalur non-GPL/AGPL di Android —
  `ics-openvpn` (schwabe) adalah GPLv2 dan secara eksplisit BUKAN library
  untuk dipakai project lain; `openvpn3` core (dipakai OpenVPN Connect
  resmi) adalah AGPLv3, dan OpenVPN Inc dikonfirmasi MENOLAK memberi
  commercial license atas kode itu (support.openvpn.com). Pakai salah
  satunya berarti SELURUH AdShield wajib ikut open-source. **User
  memutuskan (2026-08-05): OpenVPN DIBATALKAN PERMANEN dari roadmap**,
  loncat ke IKEv2 native — JANGAN diangkat lagi kecuali user eksplisit
  minta dan eksplisit menerima konsekuensi GPL/AGPL.
  **File baru:** `protocol/IkeV2VpnEngine.kt` — implementasi `VpnEngine`
  pakai `android.net.VpnManager`/`Ikev2VpnProfile` (platform API AOSP,
  Apache 2.0, **0 dependency pihak ketiga, 0 risiko lisensi**). Setiap
  method/konstanta yang dipakai (`Ikev2VpnProfile.Builder`,
  `setAuthDigitalSignature`/`setAuthUsernamePassword`/`setBypassable`,
  `VpnManager.provisionVpnProfile`/`startProvisionedVpnProfileSession`/
  `stopProvisionedVpnProfile`/`getProvisionedVpnProfileState`,
  `VpnProfileState.STATE_*`, `VpnManager.ACTION_VPN_MANAGER_EVENT` +
  kategorinya) diverifikasi LANGSUNG ke source AOSP
  (`frameworks/base/core/java/android/net/{Ikev2VpnProfile,VpnManager,
  VpnProfileState}.java`) sebelum dipakai di kode — bukan ditebak dari
  familiarity umum dengan Android.
  **2 batasan platform (BUKAN gap sementara yang "belum dikerjakan" —
  ini batas API-nya sendiri, JANGAN dicoba "diperbaiki" tanpa naikkan
  minSdk engine ini):**
  1. `Ikev2VpnProfile.Builder(...)` butuh API 30 (Android 11) +
     `PackageManager.FEATURE_IPSEC_TUNNELS` di device. Di bawah itu,
     `connect()` langsung `VpnEngineState.Error` tanpa mencoba apa pun.
  2. Monitoring state/error publik (`VpnManager.
     getProvisionedVpnProfileState()`, broadcast
     `ACTION_VPN_MANAGER_EVENT`) butuh API 33 (Android 13). Field yang
     SAMA ada di source AOSP untuk API 30-32 tapi ditandai `@hide` —
     TIDAK bisa dipakai app pihak ketiga sama sekali di rentang itu. Di
     API 30-32, `IkeV2VpnEngine.state` jadi **tebakan optimis**
     (langsung `Connected` begitu `startProvisionedVpnProfileSession()`
     tidak melempar exception) — BUKAN konfirmasi tunnel benar-benar
     jalan. JANGAN klaim device di rentang 30-32 "sudah diverifikasi
     connect" hanya dari `state` — itu bukan sinyal asli di situ.
  **Keputusan desain kunci:**
  - `VpnEngine.prepareConsent(config): Intent?` (default `null`)
    ditambahkan ke interface — IKEv2 minta consent lewat Intent dari
    `VpnManager.provisionVpnProfile()` (mirip `VpnService.prepare()` tapi
    dikembalikan per-call, bukan Activity result code global).
    `WarpVpnEngineAdapter` (v3.13.0) TIDAK di-override — consent WARP/DNS
    tetap `VpnService.prepare()` di `MainActivity`, sepenuhnya di luar
    `VpnEngine`. Ini perubahan ADDITIVE (default method), bukan breaking.
  - `VpnProtocolConfig.IkeV2` mendukung 2 metode auth: `certificateAlias`
    (RSA digital signature, key/cert HARUS SUDAH ada di AndroidKeyStore —
    batch ini TIDAK menyediakan import sertifikat) atau `username`+
    `password` (EAP-MSCHAPv2). **PSK TIDAK dimodelkan** (gap diketahui,
    tambah field `presharedKey` dulu kalau nanti dibutuhkan).
    **Split-tunnel-by-app TIDAK BISA diimplementasi sama sekali** dengan
    `Ikev2VpnProfile` — API-nya cuma punya `setBypassable(Boolean)`
    global, tidak ada allow/deny list per-app seperti `VpnService.
    Builder.addAllowedApplication()`. JANGAN dianggap "belum
    diimplementasi" — memang tidak ada di platform API ini.
  **BELUM DIWIRE ke UI** (sama seperti `WarpVpnEngineAdapter` v3.13.0) —
  sengaja, batch ini murni engine + perluasan interface. **BELUM
  DIKONFIRMASI build CI** — cek dulu di sesi berikutnya.
- v3.13.0 (2026-08-05) — Batch 2/N: Adaptasi WireGuard/WARP ke `VpnEngine`
  interface, SELESAI implementasi statis.** Lanjutan langsung v3.12.0, urutan
  batch sesuai rencana yang sudah disepakati (lihat entri v3.12.0 di bawah).
  **File baru:** `protocol/WarpVpnEngineAdapter.kt` — membungkus
  `WarpTunnelManager.getInstance()` yang SUDAH ADA, **0 baris di manapun
  dalam package `warp/` diubah**. State diterjemahkan lewat `combine()` atas
  4 `StateFlow` yang sudah publicly exposed oleh `WarpTunnelManager`
  (`state: StateFlow<Tunnel.State>`, `connecting`, `lastError`, `quality`) —
  tidak ada field/method baru ditambahkan ke `WarpTunnelManager` itu sendiri.
  **File diubah:** `protocol/VpnProtocolConfig.kt` (tambah `VpnProtocolConfig.
  Warp` — marker config TANPA field server/key, WARP tetap registrasi-based
  lewat `WarpAccountRepository` seperti sebelumnya), `app/build.gradle.kts`
  (version bump saja).
  **Keputusan desain kunci (JANGAN dilanggar tanpa diskusi eksplisit):**
  - `VpnProtocolConfig.Warp.routeIpv6` SENGAJA TIDAK diteruskan ke
    `WarpTunnelManager.connect()` — manager itu sendiri sudah baca
    `SettingsRepository.warpRouteIpv6` langsung tiap `connect()`/
    `attemptReconnect()` (keputusan arsitektur #6e, TIDAK berubah). Kalau
    field config ini diteruskan juga, akan ada 2 sumber kebenaran bersaing
    untuk setting yang sama — JANGAN "perbaiki" ini dengan menambah
    parameter routeIpv6 ke `WarpTunnelManager.connect()` kecuali ada
    kebutuhan nyata untuk override per-panggilan di luar toggle Home
    screen yang sudah ada.
  - `connectedSinceMs` (dipakai `VpnEngineState.Connected`) adalah state
    BARU yang HANYA ada di level adapter — `WarpTunnelManager` sendiri
    tidak melacak timestamp ini. Di-set saat transisi pertama terdeteksi
    ke `Tunnel.State.UP`, di-reset ke 0 saat turun dari UP atau error.
  - Mapping `VpnEngineState.Reconnecting` cuma dari
    `quality.reconnectAttempts > 0` (bukan flag internal `reconnecting`
    milik `WarpTunnelManager`, yang `private` — sengaja tidak diekspos
    baru demi batch ini supaya 0 baris `WarpTunnelManager.kt` berubah).
    Diketahui & diterima: window kosmetik sempat baca `Reconnecting`
    padahal state asli sudah `Disconnected` tepat setelah `disconnect()`
    manual, karena `reconnectAttempts` baru direset di `connect()`
    berikutnya bukan di `disconnect()`. TIDAK memengaruhi
    `WarpTunnelManager` yang sebenarnya (source of truth `state`-nya
    sendiri tidak berubah sama sekali) — kalau ini dianggap mengganggu
    nanti, perbaikannya HARUS menambah expose flag `reconnecting` publik
    di `WarpTunnelManager`, bukan menebak-nebak dari `quality` lagi.
  - **BELUM DIWIRE ke UI mana pun** — `MainActivity`/`HomeScreen`/
    `BootReceiver`/`qs/WarpTileService` semua MASIH panggil
    `WarpTunnelManager` langsung, TIDAK lewat adapter ini. Ini SENGAJA
    (kdoc file menjelaskan alasan: buktikan interface compile+behave dulu
    sebelum migrasi call site, Batch Lock — diff batch ini terbatas ke
    `protocol/` saja). Migrasi UI ke lewat `VpnEngine` (kalau memang mau
    dilakukan — belum tentu perlu sebelum engine baru lain nambah) adalah
    batch terpisah lagi, BELUM dikerjakan.
  **BELUM DIKONFIRMASI build CI** untuk batch ini — cek dulu di sesi
  berikutnya sebelum lanjut ke Batch 3 (OpenVPN — lihat peringatan risiko
  di entri v3.12.0 di bawah, TETAP berlaku tanpa perubahan).
- v3.12.0 (2026-08-05) — Batch 1/N Arsitektur Multi-Protokol
  (scaffolding only).** **KEPUTUSAN ARSITEKTUR BESAR:** user memutuskan
  AdShield diperluas dari 2 mode (DNS Ad-Block/WARP) jadi VPN client
  multi-protokol (+ OpenVPN, IKEv2/IPsec, Shadowsocks/VLESS), rilis
  bertahap 1 engine/batch. Krisis DNS/DoH (v3.10.1–v3.11.1) resminya
  DITINGGALKAN oleh user ("gagal total. jadi buang jauh-jauh pertanyaan
  itu!!") — TIDAK PERNAH dikonfirmasi apa root cause sebenarnya (DoH pun
  belum sempat divalidasi device sebelum user pivot). **Kalau user kembali
  ke topik DNS Ad-Block nanti, ingat: v3.11.1 adalah state terakhir
  (DoH+fallback plain-UDP+MTU 1500+resolver diversity), status build CI
  TIDAK PERNAH dikonfirmasi sukses, dan root cause asli (kenapa plain
  UDP:53 gagal total di jaringan user) TIDAK PERNAH benar-benar
  diidentifikasi — cuma disingkirkan satu-satu tanpa pengganti pasti
  terbukti.**
  Batch 1 ini: `protocol/VpnEngine.kt` (interface), `protocol/VpnEngineState.kt`
  (sealed state), `protocol/VpnProtocolConfig.kt` (config model per
  protokol, parser BELUM ada), `data/VpnProfileRepository.kt`
  (EncryptedSharedPreferences untuk secrets), `AppMode` di Constants.kt
  ditambah 3 placeholder. **0 file DNS_ADBLOCK/WARP_TUNNEL existing
  disentuh** — risiko regresi rendah, tapi arsitektur "2 mode
  mutually-exclusive" akan berubah signifikan begitu engine baru mulai
  wired ke UI (activeMode jadi >2 kemungkinan nilai, NavGraph/HomeScreen
  perlu tahu cara render protokol baru).
  **Rencana batch berikutnya (urutan disepakati via CHANGELOG v3.12.0):**
  1. Adaptasi WireGuard/WARP existing ke `VpnEngine` interface (buktikan
     abstraksi ke engine yang SUDAH terbukti jalan sebelum tambah baru)
  2. OpenVPN (native ics-openvpn via JNI) — **PERINGATAN untuk sesi
     berikutnya:** ini permintaan paling berisiko dari seluruh scope.
     Integrasi JNI/native C++ ics-openvpn BUKAN sesuatu yang bisa ditulis
     dari nol lewat kode Kotlin biasa — butuh source tree asli
     ics-openvpn (submodule/AAR pihak ketiga) yang HARUS diverifikasi
     eksistensi & keamanannya dulu (cek Maven/JitPack coordinates real,
     jangan asumsi/karang nama artifact). JANGAN klaim "sudah
     terintegrasi" kalau yang sebenarnya cuma wrapper Kotlin di atas
     dependency yang belum divalidasi bisa di-resolve Gradle.
  3. IKEv2 — evaluasi dulu `android.net.IkeV2VpnProfile` (native Android
     10+, TIDAK butuh library pihak ketiga) vs StrongSwan AAR — kalau bisa
     native API, jauh lebih aman/simpel daripada tambah dependency besar.
  4. Shadowsocks/VLESS via Xray-core — sama seperti OpenVPN, cek dulu
     Maven/JitPack coordinates AAR resmi yang benar-benar ada sebelum
     nulis kode yang mengasumsikan API tertentu.
  **BELUM DIKONFIRMASI build CI** untuk batch 1 ini — cek dulu di sesi
  berikutnya sebelum mulai batch 2 (WireGuard adapter).
- v3.11.1 (2026-08-05) — HOTFIX: compile error di DohClient.kt.** CI
  build v3.11.0 GAGAL (`compileReleaseKotlin`) — user upload log GitHub
  Actions, ketahuan dari situ tanpa perlu tes device dulu. Root cause: 2
  overload `createSocket(InetAddress, ...)` di custom `SSLSocketFactory`
  meneruskan `InetAddress` mentah ke parameter yang minta `String`
  (`delegate.createSocket(Socket, String, Int, Boolean)`) — salah tipe,
  Kotlin gagal resolve overload. Fix: `.hostAddress` di kedua tempat.
  **Pelajaran:** DohClient.kt (v3.11.0) dikirim TANPA pernah dicoba
  compile lokal/CI dulu sebelum diserahkan ke user — ke depan, kalau ada
  akses build tool, coba compile-check dulu sebelum klaim "siap kirim"
  untuk kode dengan override interface Java/Android yang kompleks
  (SSLSocketFactory dkk rawan overload-mismatch seperti ini). **MASIH
  BELUM DIKONFIRMASI build CI sukses SETELAH fix ini** (baru perbaikan
  sintaks, belum di-submit ulang ke CI) — WAJIB dicek pertama di sesi
  berikutnya, BARU lanjut ke validasi fungsional DoH yang sudah
  direncanakan di v3.11.0 (poin a-d di bawah, urutannya TETAP sama,
  cuma tertunda karena compile error ini harus beres dulu).
- v3.11.0 (2026-08-05) — DNS-over-HTTPS (DoH), respons ke fix MTU
  v3.10.2 yang TIDAK menolong.** User laporkan error persis
  `DNS_PROBE_FINISHED_BAD_SECURE_CONFIG` (WiFi) + matot (data seluler)
  MASIH terjadi setelah fix MTU. Dicek & disingkirkan: Android Private DNS
  (user konfirmasi tidak pernah diaktifkan) dan Chrome Secure DNS (belum
  dikonfirmasi user secara eksplisit tapi user langsung minta lanjut ke
  DoH/DoT tanpa cek ini lebih dulu — "Patuhi perintah saya!!" — jadi
  kemungkinan Chrome Secure DNS BELUM benar-benar disingkirkan sebagai
  penyebab tambahan, cuma di-skip pengecekannya atas permintaan user).
  **Kesimpulan kerja:** plain UDP port 53 kemungkinan besar diblokir/rusak
  di jaringan user (baik WiFi maupun data seluler) — di luar kendali kode
  app kalau benar. **Keputusan user (last verdict):** implementasi DoH,
  fallback ke plain DNS kalau DoH gagal, dua provider (Cloudflare+Google)
  sekaligus, urutan Cloudflare dulu. Detail implementasi lihat CHANGELOG.md
  v3.11.0. **PENTING — BELUM DIKONFIRMASI SAMA SEKALI di device fisik**,
  termasuk hal paling dasar: apakah `HttpsURLConnection` + custom
  `SSLSocketFactory` yang manual protect() socket ini benar-benar bisa
  connect lewat tun interface tanpa loop/deadlock — ini pola BARU yang
  belum pernah dipakai di codebase ini sebelumnya (beda dari raw
  `DatagramSocket.protect()` yang sudah terbukti jalan). WAJIB jadi hal
  PALING PERTAMA dicek di sesi berikutnya, urutan: (a) build CI sukses
  dulu — TLS/HttpsURLConnection API dipakai benar secara sintaks, (b)
  nyalakan DNS Ad-Block di jaringan yang tadi gagal (WiFi & data seluler
  keduanya, karena user laporkan gagal di dua-duanya dengan gejala beda),
  (c) domain apa pun resolve normal (bukti DoH connect + protect() tidak
  deadlock), (d) kalau MASIH gagal — cek Diagnostik/Logcat apakah errornya
  soal SSL handshake, soal protect() gagal, atau DoH endpoint sendiri
  unreachable (baru itu penentu apakah lanjut DoT atau ada bug di
  DohClient). Kalau DoH ternyata TIDAK menolong juga, itu bukti kuat
  jaringan user block outbound HTTPS/443 ke domain tertentu juga (bukan
  cuma port 53) — di titik itu kemungkinan besar sudah di luar yang bisa
  diselesaikan lewat kode app sama sekali, perlu isolasi jaringan lain
  (coba SIM/WiFi berbeda) sebelum lanjut coding apa pun lagi.
- v3.10.2 (2026-08-05) — HOTFIX: VPN_MTU tidak wajar (32000→1500), respons
  ke laporan v3.10.1 TIDAK menolong.** User konfirmasi lewat checklist: (1)
  mode DNS ON masih total internet failure sama seperti sebelum v3.10.1,
  (2) BAHKAN akses browser ke IP langsung (mis. `8.8.8.8`) — yang
  bypass DNS sepenuhnya — juga gagal total. Ini kunci diagnosis: arsitektur
  `addRoute(Constants.VPN_ROUTE, 32)` cuma mendaftarkan rute ke
  `10.111.222.1/32` (port 53), jadi SECARA DESAIN trafik non-DNS (termasuk
  akses IP langsung) tidak seharusnya pernah lewat/tersentuh tun interface
  sama sekali — kalau itu pun gagal, masalahnya bukan lagi soal resolusi
  DNS atau resolver upstream (v3.10.1 sudah benar tapi tidak cukup),
  melainkan tun interface itu sendiri bermasalah di level
  establish()/kernel network stack device. **Ditemukan saat audit kode:**
  `Constants.VPN_MTU = 32000` — jauh di luar MTU link nyata manapun
  (WiFi/seluler real-world ~1500). Diturunkan ke `1500` (standar). Ini
  eksekusi langsung atas instruksi user ("last verdict"), BELUM
  dikonfirmasi hasil di device — WAJIB jadi hal pertama dicek di sesi
  berikutnya, urutan tes sama seperti sebelumnya (poin 1 & 2 checklist:
  DNS ON normal? akses IP langsung normal?). **Kalau masih gagal setelah
  fix MTU ini juga:** kemungkinan besar sudah habis ruang gerak di sisi
  kode `AdBlockVpnService`/`Constants` — geser dugaan ke (a) port 53/UDP
  diblokir total oleh jaringan/operator user (solusi: DoH/DoT, roadmap
  lama yang disisihkan), atau (b) faktor device/OEM/kernel spesifik di
  luar kendali kode app (minta user coba jaringan lain/device lain kalau
  ada, untuk isolasi variabel).
- v3.10.1 (2026-08-05) — HOTFIX: total DNS failure di device fisik user
  (semua app kehilangan internet saat mode DNS Ad-Block aktif).** User
  laporkan langsung: nyalakan DNS Ad-Block → SEMUA app (bukan cuma
  domain tertentu) kehilangan internet total. **Root cause:** v3.9.0
  mengganti `UPSTREAM_DNS_SERVERS` fallback dari `8.8.8.8` (Google) ke
  `1.0.0.1` (Cloudflare) supaya sesuai literal requirement roadmap "DNS
  cepat 1.1.1.1/1.0.0.1" — efek sampingnya TIDAK disadari saat itu: kedua
  resolver (`1.1.1.1` & `1.0.0.1`) sama-sama Cloudflare/AS yang sama. Di
  jaringan/operator yang memblokir Cloudflare DNS secara umum, KEDUA
  resolver gagal bareng — nol fallback provider lain, semua query DNS mati,
  dan karena app manapun yang butuh resolusi domain baru langsung stuck,
  efeknya kelihatan sebagai \"internet mati total\" walau VPN cuma nge-tunnel
  DNS (arsitektur `10.111.222.1/32`-only TETAP benar, ini murni soal upstream
  resolver-nya sendiri yang tidak bisa dihubungi). **Fix:** `Constants.
  UPSTREAM_DNS_SERVERS` sekarang `[1.1.1.1, 1.0.0.1, 8.8.8.8]` — primary
  pair Cloudflare tetap dipertahankan (roadmap requirement tidak dilanggar),
  Google ditambah balik sebagai fallback ke-3 supaya ada jalur keluar dari
  provider yang beda kalau Cloudflare diblokir. **Belum dikonfirmasi user
  apakah fix ini benar-benar memulihkan koneksi di jaringannya** — WAJIB
  jadi hal pertama dicek di sesi berikutnya sebelum menganggap ini selesai.
  Kalau MASIH gagal setelah fix, kemungkinan bukan soal resolver spesifik
  lagi — minta user cek apakah UDP port 53 ke resolver manapun diblokir
  total di jaringannya (beberapa jaringan publik/korporat/operator memang
  memaksa semua DNS lewat resolver mereka sendiri), yang butuh solusi beda
  (DoH/DoT, item roadmap yang sudah lama disisihkan — lihat #0 di bawah).
  **Pelajaran:** perubahan konfigurasi resolver upstream (atau parameter
  jaringan lain yang tergantung provider/carrier) HARUS dipandang sebagai
  perubahan berisiko regional/operator-spesifik, bukan cuma "sesuai spec
  literal" — sama seperti insiden WARP IPv6 (v3.2.1/v3.3.0) yang juga
  ternyata operator-spesifik. Ke depan: kalau roadmap minta resolver/
  endpoint spesifik yang MENGURANGI diversity provider (bukan cuma ganti),
  pertimbangkan untuk mempertahankan minimal 1 resolver dari provider
  berbeda sebagai fallback terakhir, bukan full-replace.
- v3.10.0-hotfix-repack (2026-08-05, PENTING — baca ini dulu sebelum apa
  pun lain) — insiden nested-folder di GitHub, ZIP sebelumnya SALAH
  bungkus.** User upload screenshot repo GitHub: root repo berisi folder
  literal `AdShield-main/` (sejajar `.github/` dan `.gitignore`) alih-alih
  `app/`, `build.gradle.kts`, dll langsung di root. **Root cause:** ZIP
  yang di-upload user ke sesi ini (`AdShield-main__5_.zip`) adalah hasil
  GitHub "Download ZIP" (selalu dibungkus `<repo>-<branch>/`). Saat
  repackaging v3.10.0, Claude meng-unzip lalu mem-package ULANG folder
  pembungkus yang sama (`AdShield-main/`) tanpa di-flatten dulu — ZIP
  hasil pengiriman ikut terbungkus. Command update Termux standar proyek
  ini (`unzip -o "$LATEST_ZIP" -d ~/projects/AdShield/`) mengasumsikan isi
  ZIP FLAT (root ZIP = root proyek), BUKAN dibungkus folder lagi — jadi
  hasil unzip jadi `~/projects/AdShield/AdShield-main/...`, dan `git add -A`
  ikut men-commit folder bersarang itu ke GitHub. Efek: `build.gradle.kts`
  tidak ada di root repo → CI Actions tidak bisa temukan project Gradle →
  build gagal/pending permanen.
  **Fix:** (1) ZIP pengiriman berikutnya SELALU flat (root ZIP = file
  proyek langsung, tidak dibungkus nama folder apa pun) — ini sudah aturan
  baku (`ZIP naming`/`Repack Cleanup Protection` di instruksi standing),
  pelanggarannya ada di proses packaging sesi lalu, bukan di aturan itu
  sendiri. (2) Repo GitHub yang SUDAH terlanjur nested wajib diperbaiki
  manual via command Termux (lihat bagian bawah PROJECT_STATE ini /
  respons chat sesi insiden) — pindahkan isi `AdShield-main/` ke root lalu
  hapus folder kosongnya, commit terpisah dari fitur apa pun.
  **Pelajaran untuk self-verifikasi ke depan:** kalau ZIP sumber yang
  di-upload user adalah hasil "Download ZIP" GitHub (ciri: nama file
  `<repo>-<branch>.zip`/`__N_.zip`, isi dibungkus `<repo>-<branch>/`),
  WAJIB flatten dulu sebelum re-package — JANGAN asumsikan folder
  pembungkus itu adalah konvensi resmi proyek hanya karena begitu
  strukturnya saat diterima. Verifikasi `unzip -l` HARUS eksplisit
  mengecek "apakah top-level ZIP = nama file proyek langsung (app/,
  build.gradle.kts, dst), bukan cuma 'ada folder pembungkus dengan nama
  masuk akal'" — kriteria lama terlalu longgar dan meloloskan insiden ini.
- v3.10.0 (2026-08-05) — Resource profiling instrumentation (memori &
  baterai), respons ke audit eksternal skor 9.0/10.** Audit menandai 5
  kekurangan; dicek silang dulu terhadap PROJECT_STATE.md sebelum kerja:
  item "kecepatan surfing" & "reconnect/stabilitas VPN" TERNYATA sudah
  selesai dikerjakan sejak v3.5.0–v3.9.0 (cuma belum divalidasi device,
  bukan belum dikerjakan) — TIDAK dikerjakan ulang. Item "memori/baterai
  profiling" dikonfirmasi 0% dikerjakan sebelumnya — ini yang dikerjakan
  batch ini. **File baru:** `util/ResourceMonitor.kt` — snapshot PSS app,
  memori sistem tersisa + flag low-memory, baterai (persen/suhu/status
  charging), semua lewat API tanpa permission baru (`ActivityManager.
  getProcessMemoryInfo`/`getMemoryInfo`, sticky intent
  `ACTION_BATTERY_CHANGED`) — tidak ada perubahan `AndroidManifest.xml`.
  **`ui/MainViewModel.kt`**: `resourceSnapshot` StateFlow, poll 3 detik via
  `flow{}.stateIn(..., WhileSubscribed(5000), ...)` — SENGAJA poll dari UI
  layer bukan service baru, supaya loop hanya jalan selagi Diagnostik
  dibuka, tidak menguras baterai di background (yang justru sedang coba
  diukur). **`ui/screens/DiagnosticsScreen.kt`**: section baru "Resource
  (Memori & Baterai)", masuk juga ke teks copy diagnostik. Murni
  instrumentasi baca-saja — TIDAK ada perubahan perilaku VPN/DNS/WARP.
  **Belum dikerjakan (sengaja di luar scope):** histori/tren metrik dari
  waktu ke waktu (baru snapshot titik-waktu, belum ada penyimpanan
  Room/interval/retention untuk grafik). **BELUM dikonfirmasi build CI +
  belum dilihat terisi data nyata di device** — cek dulu di sesi
  berikutnya, idealnya SEKALIAN dengan 4 item v3.9.0 yang juga masih
  menunggu validasi device.
- v3.9.0 (2026-08-05) — Internet Surfing Optimization batch 2: DNS
  prefetch + cache-warming, WARP connection warm-up, DNS resolver fallback
  1.1.1.1→1.0.0.1.** Lanjutan roadmap yang di v3.7.0 masih menyisakan 3 item
  "belum dikerjakan": DNS prefetch, pre-warming domain populer, connection
  warm-up eksplisit — ketiganya sekarang selesai (lihat CHANGELOG untuk
  detail lengkap). Ditambah 1 penyesuaian: `UPSTREAM_DNS_SERVERS` fallback
  diganti dari `8.8.8.8` ke `1.0.0.1` supaya cocok literal dengan requirement
  "Wajib: DNS cepat 1.1.1.1/1.0.0.1" di roadmap (sebelumnya fallback masih
  Google, bukan pasangan Cloudflare). Dicek ulang seluruh item roadmap
  "Internet Surfing Optimization" terhadap kode aktual sebelum mulai —
  semua item lain (DNS cache, Auto MTU, smart endpoint, fast reconnect, DNS
  leak protection, kill-switch, packet loss, keepalive 25s, toggle
  IPv4/IPv6) SUDAH beres sejak v3.7.0/v3.2.1, tidak diimplementasi ulang.
  **Belum dikonfirmasi build CI + belum ada pengujian di device fisik** —
  cek dulu di sesi berikutnya sebelum lanjut ke item lain.
- **v3.8.1 (2026-08-05) — Feedback audit fix: false-positive "ACTIVE" state
  survives DNS establish() failure, across QS tiles + Home ring.** User
  requested: audit "kecacatan logika feedback" di segmen toggle Quick
  Settings dan seluruh penunjang, eksekusi langsung 1 batch.
  - **Root cause:** `AdBlockVpnService.startVpn()` wrote
    `SettingsRepository.activeMode = DNS_ADBLOCK` and `wasRunning = true`
    unconditionally in a fire-and-forget coroutine BEFORE
    `builder.establish()` ran below it, and NEVER reverted either write if
    `establish()` failed (returned null or threw). `activeMode` is the
    single source of truth read by `DnsTileService`/`WarpTileService`
    (`onStartListening`'s flow collector) AND by
    `MainViewModel.vpnActive` → `HomeScreen`'s ring. Result: any DNS
    establish failure (another VPN app holding the interface, etc.) left
    BOTH the QS tile AND the Home ring stuck showing "ON"/green
    indefinitely, with the real error (`AdBlockVpnService.lastError`)
    silently sitting unobserved anywhere except the Diagnostics screen.
    WARP's equivalent path (`WarpForegroundService.onStartCommand`) already
    got this right — `if (connected) settingsRepository.setActiveMode(...)`
    — so this was a DNS-only asymmetry, not a design-level ambiguity.
  - **Fix, 4 files:**
    - `vpn/AdBlockVpnService.kt`: moved the `setWasRunning`/`setActiveMode`
      write to fire only after `iface != null` (i.e. after confirmed
      `establish()` success). Added an explicit
      `setActiveMode(AppMode.NONE)` + `setWasRunning(false)` in the
      `iface == null` failure branch — deliberately explicit rather than
      "leave as-is", because this same failure path also runs mid a
      WARP→DNS mode switch, where `activeMode` could otherwise be left
      reading the OLD (already-stopped) WARP_TUNNEL value.
    - `ui/MainViewModel.kt`: `vpnActive` was `MutableStateFlow(false)`
      manually flipped by `MainActivity`. Replaced with a derived
      `StateFlow` (`activeMode.map { it == AppMode.DNS_ADBLOCK }`) — same
      pattern already used for WARP's `warpUp`. `setVpnActive()`/
      `_vpnActive` removed entirely; there is now exactly one source of
      truth instead of two that could disagree.
    - `MainActivity.kt`: removed the (now meaningless)
      `viewModel.setVpnActive(true/false)` calls from
      `startDnsService()`/`stopDnsService()`.
    - `ui/screens/HomeScreen.kt`: added `dnsLastError` (already existed as
      a `StateFlow`, was just never collected here) inline under the ring,
      shown only while `!vpnActive`, mirroring the WARP card's existing
      `error = warpError`.
  - **Explicitly audited and found correct, NOT changed:** WarpForegroundService's
    own state-write gating (`if (connected)` — this was the reference
    pattern DNS was brought up to match); DnsTileService/WarpTileService
    themselves (the tiles' `onStartListening` flow-collection logic was
    already correct — the bug was entirely upstream, in what value the
    source-of-truth flow ever emitted); the VPN-permission-denied Toast/
    Snackbar paths in `MainActivity.vpnPermissionLauncher` (already fixed
    in an earlier "feedback audit" pass — see inline comments dated before
    this one, left untouched).
  - **Known gap, deliberately NOT fixed this batch (scope discipline, not
    an oversight):** the QS tiles give zero transient visual feedback
    during the multi-second window while WARP is actively connecting
    (`WarpTunnelManager.connect()` does endpoint/MTU probing, see v3.7.0
    entry) — no "Menyambungkan…" subtitle, no STATE change until the real
    result lands. `Tile.subtitle` requires API 29 (`Build.VERSION_CODES.Q`)
    and this app's `minSdk = 24`, so fixing it needs an SDK-version guard
    that wasn't part of this batch's specific ask (the false-positive
    ACTIVE state, which is now fixed). Also unaudited/unchanged: the tiles'
    fire-and-forget `stopDns()`/`stopWarp()` mutual-exclusion calls during
    a mode switch have no ordering guarantee relative to the new mode's
    start — functionally harmless (each service's own `onStartCommand`
    handles being called while the other is mid-stop) but worth another
    look if a future audit is specifically about mode-switch races rather
    than feedback display.

- **v3.8.0 (2026-08-05) — Quick Settings Tile, 2 tile terpisah (DNS/WARP)
  SELESAI implementasi statis, BELUM dikonfirmasi build CI + BELUM pernah
  dicoba tarik dari QS panel nyata di device.** User eksplisit minta: (1)
  tile terpisah per mode (bukan 1 tile gabungan), (2) toggle LANGSUNG dari
  luar app, BUKAN cuma masuk ke app untuk aktivasi manual. Ditemukan lewat
  audit: fitur ini 0% dikerjakan sejak v1.0.0 — yang ada cuma App
  Shortcuts (v2.2.0), beda fitur total (itu tekan-lama ikon launcher, dan
  tetap route lewat MainActivity untuk toggle-nya).
  - **File baru:** `qs/DnsTileService.kt`, `qs/WarpTileService.kt` — masing-
    masing `TileService`, pola instansiasi `SettingsRepository`/start-service
    meniru `BootReceiver` (applicationContext langsung, BUKAN lewat
    MainViewModel — TileService bukan LifecycleOwner). `res/drawable/
    ic_tile_dns.xml`, `res/drawable/ic_tile_warp.xml` — icon monokrom baru
    (tile QS di-auto-tint sistem berdasarkan Tile.STATE_ACTIVE/INACTIVE,
    icon 2 warna seperti shortcut lama akan bentrok dengan itu).
  - **Jalur toggle 100% background** (TIDAK ada Activity sama sekali) kalau
    izin VPN sudah pernah diberikan: `TileService.onClick()` langsung cek
    `VpnService.prepare() == null` lalu `ContextCompat.startForegroundService()`
    dari dalam TileService itu sendiri. Ini SENGAJA pakai exemption resmi
    Android untuk start-foreground-service-dari-background (`TileService#
    onClick()` ada di daftar exemption dokumentasi resmi
    `ForegroundServiceStartNotAllowedException`) — JANGAN diubah jadi selalu
    lewat Activity, itu persis yang user minta dihindari.
  - **Satu-satunya kasus tile membuka MainActivity: izin VPN belum pernah
    diberikan / dicabut** (`VpnService.prepare()` return non-null). Ini
    BATASAN OS, bukan pilihan desain — dialog konsen VpnService WAJIB
    Activity foreground, tidak ada API untuk menghindarinya. Ditangani lewat
    `ACTION_REQUEST_PERMISSION` (per-tile, di companion object masing-
    masing) → `MainActivity.handleTilePermissionIntent()`: SKIP `setContent()`
    total (early-return sebelum compose UI apa pun), langsung
    `vpnPermissionLauncher.launch(prepareIntent)`, lalu `finish()` diri
    sendiri persis setelah callback izin selesai (`finishAfterPendingStart`
    flag). Ditambah `Theme.AdShield.Transparent` (baru, `themes.xml`) khusus
    launch ini via `setTheme()` SEBELUM `super.onCreate()` — supaya tidak ada
    kedipan background gelap app di belakang dialog sistem. **JANGAN hapus
    guard early-return ini** — kalau dihapus, tile akan kelihatan "membuka
    app penuh" walau cuma sesaat, persis yang user minta dihindari.
  - **Mutual exclusion (2 mode tidak boleh bareng) diduplikasi manual** di
    kedua TileService (stop mode lain dengan `EXTRA_MODE_SWITCH=true`
    sebelum start) karena `MainActivity.startDnsService()`/
    `startWarpService()` tidak reachable dari TileService — TIDAK ada
    refactor ke fungsi bersama di batch ini (dua tempat kecil, duplikasi
    diterima demi tidak menyentuh lebih banyak file di batch yang sudah di
    atas batas normal).
  - **BELUM DIKERJAKAN / BELUM DIVERIFIKASI (WAJIB dicek sesi berikutnya):**
    (a) `startActivityAndCollapse(PendingIntent)` (API 34+) vs overload
    `Intent` lama (API <34) — cabang `Build.VERSION.SDK_INT` sudah ditulis
    berdasar dokumentasi resmi (method lama non-fungsional di Android 14+),
    TAPI belum pernah diverifikasi jalan nyata di kedua kelas device; (b)
    apakah tile beneran muncul di panel QS & bisa ditambahkan user (perlu
    device fisik, tidak bisa dicek dari sandbox); (c) apakah toggle tanpa
    buka app BENERAN tidak menampilkan Activity sekilas di kasus izin sudah
    granted — perlu observasi visual langsung; (d) Toast fallback saat user
    menolak dialog izin dari tile (`TILE_PERMISSION_DENIED_MESSAGE`) belum
    pernah terlihat muncul nyata.
  - **Atomic Change note:** batch ini menyentuh 12 file (di atas batas
    normal 10) — dicatat di Impact Report sebagai exception karena
    seluruh potongan (manifest, kedua TileService, plumbing MainActivity,
    tema, string, 2 icon, version bump) saling bergantung untuk bisa
    compile; memecahnya ke beberapa batch akan meninggalkan state yang
    tidak bisa dikompilasi di tengah jalan.
- v3.7.1 (2026-08-05) — UI display MTU/endpoint/packet-loss WARP —
  SELESAI, menutup item "kerja sesi berikutnya" dari v3.7.0. Murni UI:
  `DiagnosticsScreen.kt` (3 baris baru + masuk teks copy) dan
  `HomeScreen.kt` (`WarpQualityRow` suffix `· loss N%` kalau >0%). Tidak
  ada perubahan data layer/logic — field `mtuUsed`/`endpointUsed`/
  `packetLossPercent` sendiri sudah ada dari v3.7.0. **BELUM dikonfirmasi
  build CI + belum pernah dilihat terisi data nyata di device** (nunggu
  WARP tervalidasi end-to-end dulu — lihat item #1 prioritas di bawah).
  Brace/paren balance dicek statis, OK.
- v3.7.0 (2026-08-05) — Internet Surfing Optimization (VPN) — SELESAI
  implementasi statis, BELUM diuji throughput/latency nyata di device.**
  Scope: 5 prioritas dari permintaan user (DNS cache, Auto MTU, Smart
  endpoint selection, Fast reconnect, DNS leak protection) + beberapa item
  "wajib" lain dari spec (persistent keepalive 25s sudah ada dari
  sebelumnya, packet loss detection, kill-switch hardening).
  - **File baru:** `data/DnsCache.kt` (cache jawaban DNS positif in-memory,
    keyed `domain|qtype`, TTL diklem 30–3600s, size-capped 2000 entri,
    di-clear tiap `startVpn()`), `warp/WarpEndpointSelector.kt` (probe
    UDP paralel ke 6 kandidat endpoint WARP, pilih RTT terendah).
  - **`vpn/DnsPacket.kt`:** tambah `qtypeOf()`, `withTransactionId()`
    (re-stamp transaction ID saat serve dari cache — WAJIB, jangan hapus,
    kalau tidak semua cache-hit akan gagal match di client),
    `extractCacheableTtlSeconds()` (parser TTL jawaban pertama, return null
    kalau RCODE≠0/ANCOUNT=0 supaya NXDOMAIN/SERVFAIL tidak ikut ke-cache).
  - **`vpn/AdBlockVpnService.kt`:** cache-hit dijawab langsung di packet-loop
    thread (skip `forwardExecutor` sepenuhnya) via `writeCachedResponse()`;
    cache-miss & hasil forward sukses baru ditulis ke `DnsCache.put()`.
  - **`warp/WarpTunnelManager.kt`:** `buildConfig()` sekarang terima
    `endpointOverride`+`mtu` dinamis (sebelumnya hardcode `account.peerEndpoint`
    & `WARP_MTU=1280` konstan). `selectEndpointAndMtu()` dipanggil di
    `connect()`, hasil di-cache ke DataStore 30 menit
    (`warpCachedEndpoint`/`warpCachedMtu`/`warpEndpointCacheTime` di
    `SettingsRepository`) supaya tidak re-probe tiap toggle. Auto-MTU:
    `probeBestMtu()` coba `[1420,1400,1360,1280]` turun sampai kirim UDP
    sukses. **Kill-switch hardening:** `attemptReconnect()` TIDAK LAGI
    `setState(DOWN)` sebelum `setState(UP, config baru)` — sebelumnya ada
    gap tanpa tunnel saat reconnect yang bisa bocor trafik; sekarang
    langsung re-apply config di atas tunnel yang masih UP. **Fast
    reconnect:** `ConnectivityManager.NetworkCallback` (`registerNetworkWatcher()`)
    trigger `attemptReconnect(immediate=true)` (skip backoff) begitu OS
    lapor network berubah (WiFi↔data) — jangan tunggu health-check tick
    25 detik lagi. Packet loss: rolling window 8 probe terakhir
    (`probeOutcomeWindow`) → `WarpConnectionQuality.packetLossPercent`.
  - **`warp/WarpConnectionQuality.kt`:** field baru `mtuUsed`,
    `endpointUsed`, `packetLossPercent` — UI (Diagnostics/Home) BELUM
    diupdate untuk menampilkan field-field ini, itu kerja sesi berikutnya
    kalau user mau ditampilkan di layar.
  - **DNS leak protection:** TIDAK ada toggle/kode baru terpisah — sudah
    struktural dari config WireGuard yang ada (`parseDnsServers` jadi
    satu-satunya DNS + `AllowedIPs 0.0.0.0/0` rute semua trafik lewat
    tunnel), didokumentasikan di komentar `buildConfig()`.
  - **BELUM DIKERJAKAN dari wishlist user** (di luar 5 prioritas, boleh
    lanjut kalau diminta): DNS prefetch, cache domain populer (beda dari
    DNS cache query — ini soal pre-warming, belum ada), connection
    warm-up eksplisit, IPv4+IPv6 dual-stack toggle UI (backend `routeIpv6`
    sudah ada dari sebelumnya, cuma belum "auto pilih terbaik").
  - **WAJIB dicek sesi berikutnya sebelum klaim kerja beres:** (1) apakah
    `probeBestMtu()`/`WarpEndpointSelector` benar-benar reachable dari
    device asli (ini baru diuji secara statis, socket UDP mentah ke IP
    WARP publik BISA diblokir sebagian jaringan/carrier — kalau semua
    probe gagal, fallback ke `WARP_MTU=1280`+endpoint hostname default
    sudah ada tapi belum pernah kejadian real), (2) apakah
    `backend.setState(UP, config)` di WireGuard-android library benar2
    aman dipanggil di atas tunnel yang masih UP tanpa DOWN dulu (asumsi
    dari behavior GoBackend, BELUM diverifikasi baca source library
    langsung — kalau ternyata tidak, kill-switch hardening ini perlu
    direvisi balik ke pola DOWN→UP lama).
- **v3.6.1 (2026-08-04) — Redesign app badge/icon SELESAI.** Dikerjakan
  DI ATAS upload ulang v3.6.0 (sesi chat ini sebelumnya masih nyangkut di
  v3.3.3 lokal — user upload zip v3.6.0 dari sesi paralel lain, instruksi
  "lanjutkan dari sini"). **PENTING kalau lanjut kerja icon lagi**: sebelum
  ubah warna icon, SELALU cross-check dulu `ui/theme/Color.kt` — palette
  bisa sudah bergeser dari sesi paralel lain (baru saja kejadian:
  `ShieldBgDark` geser `#17181A`→`#181816` antara v3.3.x dan v3.6.0 tanpa
  tercatat eksplisit sebagai "icon perlu diupdate juga"). Detail teknis
  lengkap fix (checkmark rusak fill+stroke, warna basi, shield keluar
  safe-zone 3dp, tidak ada fallback API 24-25, tidak ada themed-icon
  monochrome) ada di CHANGELOG.md v3.6.1 — tidak diulang di sini. File baru:
  10 PNG raster (`mipmap-{m,h,x,xx,xxx}hdpi/ic_launcher{,_round}.png`) +
  `drawable/ic_launcher_monochrome.xml`. **BELUM diverifikasi di
  device/emulator asli** — icon cuma divalidasi lewat XML parse + preview
  render Pillow, BUKAN build APK sungguhan. Kalau device masih nunjukin
  icon lama setelah update, itu kemungkinan launcher cache, bukan bug —
  suruh user uninstall-reinstall APK buat verifikasi, jangan update in-place
  dulu waktu ngecek.
- v3.6.0 (2026-08-04) — Socket pooling upstream DNS, lanjutan v3.5.0.
  User pilih "berikan hasil yang maksimal" untuk pertanyaan behavior
  fallback-antar-resolver (dikonfirmasi: boleh tetap socket yang sama
  dalam satu query, tinggal soal apakah socket itu di-reuse LINTAS query
  atau tidak). Diimplementasikan: `ThreadLocal<DatagramSocket>` di
  `AdBlockVpnService` — 1 socket persisten per worker thread
  `forwardExecutor` (4 thread), dipakai ulang lintas query alih-alih
  create+protect+destroy tiap query. Aman tanpa demux transaction-ID
  karena satu socket tidak pernah dibagi antar-thread. `stopVpn()`
  sekarang panggil `closeUpstreamSockets()` supaya socket ditutup
  deterministik, tidak menggantung di thread yang memang tidak pernah
  di-shutdown. Detail lengkap di CHANGELOG.md v3.6.0 & keputusan
  arsitektur #11b di bawah. **BELUM dikonfirmasi build CI + belum ada
  pengujian konkurensi/throughput nyata di device** — WAJIB dicek di
  sesi berikutnya, idealnya SEKALIGUS dengan v3.5.0 (custom blocklist URL
  besar) karena keduanya sama-sama "perf work belum diukur nyata".
- v3.5.0 (2026-08-04) — Audit performa proaktif, 1 fix diterapkan. User
  minta "debugging sampai tuntas di segmen performance & optimalisasi"
  setelah CI v3.3.3+v3.4.0 dikonfirmasi hijau (lihat entri di bawah).
  Diaudit statis: `AdBlockVpnService` (packet loop), `DnsPacket`
  (parse/build), `BlocklistManager` (matching), `WarpTunnelManager`.
  **Fix diterapkan**: `BlocklistManager.matchesAnyWildcard()` — linear
  scan O(ukuran wildcard set) per query diganti jalan parent-suffix
  domain + hash lookup, O(kedalaman domain). Ini laten (blocklist bawaan
  cuma 55 entri wildcard, belum kerasa), tapi fitur custom blocklist URL
  (v2.5.0) bisa bikin set itu jadi ribuan entri — di titik itu linear
  scan lama akan kena biaya nyata di packet loop, jalur paling sensitif
  latensi di app. Semantik matching diverifikasi ulang manual identik
  terhadap semua 15 test case `BlocklistManagerTest.kt` yang sudah ada
  (statis, belum dijalankan — tidak ada Gradle/JDK di sandbox). Detail
  lengkap + 2 temuan sekunder yang SENGAJA tidak diubah (socket-per-query
  di `forwardToUpstream()`, dkk) ada di CHANGELOG.md v3.5.0. **BELUM
  dikonfirmasi build CI + belum ada pengukuran throughput nyata dengan
  blocklist besar sungguhan** — WAJIB dicek di sesi berikutnya sebelum
  klaim optimisasi ini kerasa dampaknya di dunia nyata (perlu user pasang
  custom blocklist URL besar dan bandingkan latensi DNS).
- **v3.3.3 + v3.4.0 (dikonfirmasi 2026-08-04) — CI HIJAU, sudah dicek
  langsung via GitHub Actions.** Run #27 (v3.3.3 hotfix `padding` import)
  dan run #28 (v3.4.0 legibility-max pass) keduanya **Status: Success**,
  artifact `AdShield_v3.4.0` (5.82 MB) ter-generate. Ini menutup 2 item
  "BELUM dikonfirmasi build CI" yang sebelumnya tercatat di bawah — TIDAK
  perlu dicek ulang. Yang masih outstanding dari v3.4.0: verifikasi visual
  legibility-max di device fisik (belum diminta user, masih pending kalau
  diangkat lagi).
- v3.4.0 (2026-08-04) — Legibility-max pass, `Color.kt` dirombak total.**
  User audit ulang setelah v3.1.0 pakai pilihan multi-select: SEMUA 4
  kategori (caption kecil, bg/card kurang beda gelap, border/ikon card nav
  pudar, ring/tombol proteksi) masih ditandai susah dibaca —
  "pokoknya legibility harus maksimal!!". Diinvestigasi lewat pengukuran
  kontras WCAG (relative luminance), bukan tebakan visual: ketemu 2 akar
  masalah di elevation ladder lama — (1) lightness step antar-tingkat cuma
  ~4-5%, (2) hue drift tak konsisten antar warna (220°→210°→195°→180°→94°→
  157°). Fix: 1 hue konsisten (45° warm-neutral) untuk seluruh ladder +
  step dilebarkan ke ~6-8%; `ShieldOutline` L32→46 (sumber tunggal semua
  border/divider/ring-track, di-grep-verifikasi — 1 perubahan ini otomatis
  benerin SEMUA card nav); `ShieldAccentDim` dinaikkan; `ShieldTextFaint`
  L58→68 (sebelumnya 4.04:1 vs surf2, di bawah floor AA 4.5:1 untuk teks
  kecil — sekarang 5.0-6.9:1). Detail angka lengkap di CHANGELOG.md.
  **Scope MURNI nilai warna** — 0 file baru, 0 perubahan logic/struktur,
  hanya `Color.kt` + version bump (`build.gradle.kts`). Hotfix v3.3.3
  (import `padding` di `MainActivity.kt`) sudah terbawa di codebase ini,
  TIDAK di-revert. **BELUM dikonfirmasi CI hijau maupun dilihat di device
  fisik** — WAJIB jadi hal pertama dicek di sesi berikutnya, SEKALIGUS
  dengan hotfix v3.3.3 yang juga belum pernah dikonfirmasi (satu push,
  dua hal dicek bareng: build sukses + screenshot legibility baru).
- **v3.3.3 (2026-08-04) — HOTFIX build CI gagal dari push v3.3.2.** User
  upload log GitHub Actions: `Build signed release APK` gagal —
  `MainActivity.kt:160:41 Unresolved reference: padding`. Penyebab:
  `Modifier.padding(scaffoldPadding)` ditambahkan di v3.3.1 (bungkus
  NavHost dalam Scaffold) tapi lupa import
  `androidx.compose.foundation.layout.padding` (file ini sebelumnya cuma
  pakai fillMaxSize/background, jadi belum pernah butuh import itu).
  **PELAJARAN PROSES**: static check sesi v3.3.1 (brace/paren balance +
  cek simbol tercakup wildcard import) TIDAK menangkap missing
  single-symbol-import karena `padding` bukan bagian dari wildcard
  manapun yang sudah ada — untuk sesi depan, WAJIB tambahkan langkah
  "grep semua pemanggilan fungsi baru vs daftar import eksplisit" kalau
  menambah modifier/fungsi yang tidak datang dari import wildcard yang
  sudah ada di file itu, bukan cuma andalkan brace-balance. Fix 1 baris,
  sudah diverifikasi grep ulang: log CI cuma laporkan 1 error (bukan
  cascading). **BELUM di-push ulang / belum dikonfirmasi CI hijau** —
  WAJIB jadi hal pertama dicek di sesi berikutnya.
- v3.3.2 (2026-08-04) — Audit sektor Feedback ROUND 2 SELESAI, celah
  terakhir ditutup.** User tanya ulang "tuntas gak bersisa?" setelah
  v3.3.1 → sweep ulang nemuin `requestBatteryOptimizationExemption()`
  (MainActivity) masih 100% silent bahkan lebih parah dari kasus VPN
  permission: (a) no-op diam kalau sudah exempt, (b) tidak pernah
  konfirmasi hasil dialog sistem, (c) `runCatching` tanpa fallback kalau
  Intent gagal dibuka (relevan langsung ke Infinix XOS, device target app
  ini). Fix: `registerForActivityResult` + baca ulang
  `PowerManager.isIgnoringBatteryOptimizations()` sebagai ground truth
  (resultCode Intent ini sendiri TIDAK reliable di banyak OEM, jangan
  pernah dipercaya langsung). **Sweep grep menyeluruh** atas semua
  `runCatching`/`startActivity`/`startService` di project dilakukan
  setelah ini — sisanya (WarpTunnelManager, CrashLogger, BlocklistManager,
  BootReceiver/RestartReceiver/WarpRestartReceiver) SEMUA di layer
  background/internal, bukan user-tap, JADI di luar cakupan sektor
  feedback dan TIDAK disentuh — kalau sesi depan diminta audit ulang
  sektor sama, langsung rujuk daftar ini, tidak perlu grep ulang dari nol.
  **BELUM dikonfirmasi build CI.**
- v3.3.1 (2026-08-04) — Audit sektor Feedback SELESAI, 6 celah ditutup
  dalam 1 batch.** User minta audit "apa yang benar-benar diharapkan user
  saat interaksi" difokuskan ke feedback (bukan fitur/bug lain). Temuan +
  fix: (1) VPN permission ditolak dulu silent no-op → sekarang Snackbar
  via `viewModel.notifyVpnPermissionDenied()`; (2) Reset statistik & (3)
  Bersihkan log dulu 1-tap langsung eksekusi → sekarang `AlertDialog`
  konfirmasi; (4) Add/remove domain custom (Rules) dulu tanpa konfirmasi →
  sekarang `UiEvent` Snackbar, khusus remove pakai Undo 5 detik; (5)
  `forgetWarpAccount()` di ViewModel SUDAH ADA dari sesi sebelumnya tapi
  **dead code** (tidak pernah dipanggil dari UI manapun) → sekarang ada
  tombol "Lupakan Akun WARP" di DiagnosticsScreen + confirm dialog.
  Infrastruktur baru: `UiEvent` sealed class + `Channel` di MainViewModel,
  1 `Scaffold`+`SnackbarHostState` global di MainActivity yang membungkus
  NavHost (screen manapun tinggal `sendEvent()`, tidak perlu Scaffold
  sendiri-sendiri). **SENGAJA TIDAK diubah**: toggle Whitelist per-app dan
  toggle "Simpan log query" (Logs) — `Switch` checked-state dinilai sudah
  cukup sebagai feedback visual, Snackbar tambahan akan terasa berlebihan/
  spammy kalau user toggle banyak item berturut-turut. **BELUM
  dikonfirmasi build CI** — WAJIB dicek di sesi berikutnya, terutama: (a)
  Snackbar collect di `LaunchedEffect(Unit)` tidak lifecycle-aware secara
  eksplisit — kalau nanti ada bug "event ke-drop saat rotasi cepat",
  pertimbangkan `repeatOnLifecycle`; (b) belum dicoba langsung di device
  apakah Undo pada hapus domain benar-benar mengembalikan entry yang sama
  persis (termasuk kalau domain itu wildcard `*.domain.com`).
- v3.3.0 (2026-08-04) — WARP IPv6 toggle SELESAI jadi setting user,
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

4c. **`BlocklistManager.matchesAnyWildcard()` (v3.5.0) — walk parent-suffix
   domain, JANGAN dikembalikan ke iterasi linear atas set wildcard.**
   Implementasi sekarang cek `bases.contains(domain)` lalu jalan
   `domain.substring(dotIndex+1)` tiap level, bukan `for (base in bases)`.
   Ini SENGAJA — biar biayanya O(kedalaman domain) bukan O(ukuran set

4d. **TIDAK ADA auto-switch mode WARP↔DNS saat salah satu gagal total
   (keputusan v3.16.6, diserahkan user ke Claude untuk diputuskan).**
   WARP dipilih user secara eksplisit untuk enkripsi penuh trafik;
   auto-fallback diam-diam ke DNS-only (yang cuma blokir iklan, TIDAK
   mengenkripsi apa pun) berarti menurunkan jaminan keamanan yang sudah
   dipilih user tanpa consent di momen itu terjadi — melanggar prinsip
   dasar aplikasi VPN/privacy. Kalau `MAX_RECONNECT_ATTEMPTS` habis, yang
   terjadi adalah tunnel WARP di-tear-down bersih + error jelas ke UI
   (`warpLastError`), BUKAN pindah otomatis ke mode DNS. Jangan ubah ini
   jadi auto-switch tanpa user memintanya secara eksplisit.
   wildcard), supaya custom blocklist URL (v2.5.0) yang besar tidak
   memperlambat SETIAP query DNS di packet loop. Semantik hasil identik
   dengan versi lama (`domain == base || domain.endsWith(".$base")`),
   sudah diverifikasi manual terhadap seluruh `BlocklistManagerTest.kt`.

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

11b. **Upstream `DatagramSocket` di-pool per-thread (v3.6.0) —
    `ThreadLocal<DatagramSocket>` + `openUpstreamSockets` registry,
    JANGAN dikembalikan ke "socket baru tiap query".** Sebelumnya
    `forwardToUpstream()` bikin+`protect()`+`close()` satu
    `DatagramSocket` baru untuk SETIAP query DNS non-blocked — mahal
    dipanggil di hot path. Sekarang tiap salah satu dari 4 worker thread
    `forwardExecutor` punya socket persisten sendiri, dipakai ulang
    lintas query. AMAN tanpa demux logic karena satu socket hanya pernah
    dipakai oleh SATU thread, SATU query pada satu waktu (tidak pernah
    dibagi antar-thread) — beda dengan skema pooling umum yang butuh
    matching balasan by transaction-ID. Perilaku fallback antar-resolver
    DALAM satu query TETAP sama persis (socket yang sama dicoba ke
    server berikutnya secara berurutan) — yang berubah cuma socket-nya
    hidup lintas-query, bukan dibuang tiap query. `closeUpstreamSockets()`
    WAJIB dipanggil dari `stopVpn()` (sudah ada) supaya socket tidak
    menggantung selamanya di thread `forwardExecutor` yang memang tidak
    pernah di-`shutdown()`. Kalau socket masuk state error/tertutup tak
    terduga, `discardUpstreamSocket()` membuang referensi ThreadLocal-nya
    supaya `getOrCreateUpstreamSocket()` bikin yang baru di panggilan
    berikutnya — jangan hapus fallback pembuatan ulang ini, itu jaring
    pengaman kalau ada IOException yang bikin socket tak terpakai lagi.

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

13. **Quick Settings Tile (v3.8.0) — `qs/DnsTileService.kt`,
   `qs/WarpTileService.kt`. Toggle WAJIB 100% background lewat
   `TileService.onClick()` sendiri kalau izin VPN sudah pernah diberikan —
   JANGAN "disederhanakan" jadi selalu buka MainActivity buat toggle,
   itu bertentangan langsung dengan permintaan eksplisit user.** Satu-
   satunya alasan sah untuk tile ini membuka Activity adalah dialog konsen
   `VpnService.prepare()` yang belum pernah di-grant/dicabut — itu batasan
   OS (VpnService WAJIB Activity foreground untuk dialog izin), bukan
   pilihan desain, dan hanya terjadi SEKALI per install (atau kalau user
   mencabut izin manual di Settings sistem).

14. **MainActivity.ACTION_REQUEST_PERMISSION per-tile (v3.8.0) — jalur
   consent-only, JANGAN dihapus guard `handleTilePermissionIntent()`
   early-return-nya.** Kalau guard ini dihapus/`setContent()` dipanggil
   lebih dulu, tile akan terlihat "membuka app penuh" sesaat sebelum
   dialog izin muncul — persis yang user minta dihindari. `finish()`
   HARUS terjadi di callback `vpnPermissionLauncher` (`finishAfterPendingStart`
   flag), TIDAK boleh langsung setelah `.launch()` dipanggil (Activity yang
   sudah `finish()` tidak akan menerima callback ActivityResult-nya).
   `Theme.AdShield.Transparent` (`themes.xml`) HANYA dipakai untuk launch
   ini via `setTheme()` sebelum `super.onCreate()` — kalau nambah kasus
   penggunaan tema ini di tempat lain, pastikan tidak mengganggu tampilan
   UI normal (tema ini sengaja tidak render apa pun).

15. **`vpn/AdBlockVpnService.kt` (v3.17.0) — orchestrator-only, JANGAN
   ditambah logic baru langsung di sini lagi.** Kontrak modul `vpn/` +
   `vpn/dns/` pasca-refactor:
   - `AdBlockVpnService` HANYA boleh berisi: lifecycle Service
     (`onCreate`/`onStartCommand`/`onTaskRemoved`/`onRevoke`/`onDestroy`),
     `startVpn()`/`stopVpn()` (orkestrasi urutan: builder.establish() →
     wiring hasil ke kolaborator → settingsRepository write), companion
     object public API. Kalau nambah pekerjaan baru (mis. protokol DNS
     baru, jenis forward baru), buat kolaborator baru di `vpn/dns/`
     (kalau spesifik-DNS) atau `vpn/` (kalau bukan), JANGAN inline lagi
     di class ini — itu persis yang baru saja diperbaiki.
   - `UpstreamForwarder`/`DnsPrefetcher` masing-masing MEMEGANG referensi
     `VpnService` (bukan Context biasa) — WAJIB, karena `VpnService.protect()`
     dan `DohClient.resolve(vpnService, ...)` butuh instance itu, bukan
     `Context` generik. Kedua kelas ini dibuat ulang di `onCreate()` tiap
     kali Service instance baru dibuat — JANGAN dijadikan singleton/
     companion-level, lifetime-nya sengaja terikat 1:1 ke Service instance
     yang memegangnya (beda dengan `BlocklistManager`/`DnsCache` yang
     memang singleton lintas-restart).
   - `DnsPacketLoop` WAJIB dijalankan di `loopExecutor` (thread tunggal),
     dan panggilan `forwarder.forwardToUpstream()` di dalamnya WAJIB tetap
     lewat `forwardExecutor.execute { ... }`, TIDAK PERNAH langsung
     dipanggil sinkron dari `DnsPacketLoop` — itu persis bug v2.5.1
     (single shared executor bikin forward tidak pernah jalan) kalau
     sampai keduanya balik dicampur.
   - Semua kolaborator baru ini stateless-terhadap-Android-lifecycle
     KECUALI cache internalnya sendiri (`uidToPackageCache` di
     `AppUidWhitelistChecker`, socket pool di `UpstreamForwarder`) — kalau
     nanti butuh unit test, kelas-kelas ini di `vpn/dns/` sudah lebih
     mudah di-test terisolasi dibanding dulu (tidak perlu subclass/mock
     `VpnService` penuh untuk test logic blocked/cache/forward-routing di
     `DnsPacketLoop`, misalnya) — TAPI unit test ini BELUM ditulis di batch
     ini (batch ini murni structural move, 0 test baru), cuma dicatat
     sebagai kemungkinan follow-up kalau user minta.

## Riwayat insiden kronologis

- **2026-08-06 (v3.17.0 CI build FAILED, fixed v3.17.1)**: `kspDebugKotlin`
  gagal — `AdBlockVpnService.kt:262:1 Unclosed comment`. Root cause: KDoc
  class berisi literal `` `vpn/dns/*` `` yang dibaca Kotlin sebagai
  pembuka block comment nested (Kotlin, beda dari Java/C, mendukung
  nested block comment). Ditemukan lewat artifact `log_fail_*` yang
  di-upload user manual. Fix: hapus literal `/*` dari teks KDoc. Detail
  lengkap di "Status terakhir" v3.17.1 di atas. **Belum dikonfirmasi** fix
  ini benar-benar bikin CI hijau (belum ada run baru).

- **2026-08-06 (v3.16.0 CI build FAILED, fixed v3.16.1)**: `minifyReleaseWithR8`
  gagal — R8 "Missing class" untuk annotation compile-time-only dari
  `com.google.errorprone.annotations.*` / `javax.annotation.*`, ditarik
  transitif oleh Tink lewat `androidx.security:security-crypto:1.1.0`
  (dipakai `VpnProfileRepository`, sudah ada sebelum v3.16.0 — bukan
  regresi IKEv2). Ditemukan lewat raw log archive Actions yang di-upload
  manual oleh user. Fix: `-dontwarn` di `proguard-rules.pro`. Detail
  lengkap di "Status terakhir" v3.16.1 di atas. **Belum dikonfirmasi**
  fix ini benar-benar bikin CI hijau (belum ada run baru).
- **2026-08-05 (v3.10.1)**: User laporkan total DNS failure (semua app
  kehilangan internet) saat mode DNS Ad-Block aktif di device fisik. Root
  cause: v3.9.0 mengurangi diversity provider upstream resolver (8.8.8.8 →
  1.0.0.1, keduanya sekarang Cloudflare) demi kepatuhan literal ke roadmap
  — di jaringan yang memblokir Cloudflare DNS, nol fallback tersisa. Fix:
  `8.8.8.8` ditambah balik sebagai resolver ke-3. Detail lengkap di "Status
  terakhir" di atas — TIDAK diulang di sini. **Belum dikonfirmasi user
  fix ini benar-benar memulihkan koneksi.**
- **2026-08-05 (v3.10.0-hotfix-repack)**: ZIP pengiriman v3.10.0 SALAH
  bungkus — top-level ZIP masih folder `AdShield-main/` (warisan dari ZIP
  sumber hasil "Download ZIP" GitHub yang di-upload user), bukan flat.
  Command update Termux standar meng-unzip itu jadi subfolder bersarang di
  repo, `build.gradle.kts` hilang dari root → CI tidak menemukan project.
  Ditemukan oleh user lewat screenshot repo GitHub. Root cause & fix detail
  lengkap di "Status terakhir" di atas — TIDAK diulang di sini.
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
qs/            DnsTileService, WarpTileService (QS tile per mode, v3.8.0 — toggle
               background langsung, hanya buka Activity untuk dialog izin VPN pertama kali)
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

**PALING BARU & PALING PENTING (2026-08-06, v3.23.0 — batch 4/N, BELUM dicek CI):**
1. Cek CI v3.23.0 dulu sebelum apa pun lagi.
2. Device WAJIB (titik risiko paling konkret batch ini): buka HomeScreen,
   tap TEPAT di kotak Switch kartu WARP — pastikan toggle SEKALI, bukan
   dobel/balik sendiri. Ulangi buat kartu IKEv2. Lalu tap di area judul/
   subtitle kartu (bukan kotak Switch) — juga harus ikut toggle.
3. Kalau semua oke → UI/UX Apple-style + accessibility sudah cukup matang
   di 4 batch (palette, segmented control, flat app bar, press-scale,
   toggle-row accessibility merata di semua Switch app). Tanya user mau
   lanjut ke arah lain (mis. balik ke roadmap audit eksternal lama —
   Testing & Diagnostic — atau declare UI/UX fase ini selesai).

**SEBELUMNYA (2026-08-06, v3.21.0 — Apple-Style batch 1/N):**
1. Cek CI v3.21.0 dulu — belum pernah dicek sejak push, JANGAN mulai
   batch 2/N (HomeScreen press-scale, dst) sebelum ini hijau.
2. Device: buka tiap screen (Home/Rules/Logs/Whitelist/Diagnostics),
   pastikan TopAppBar menyatu visual dengan background (bukan pita warna
   terpisah), segmented control di RulesScreen ganti tab dengan benar.
3. Kalau hijau & device oke → lanjut batch 2/N sesuai catatan v3.21.0 di
   atas (HomeScreen polish), atau balik ke roadmap audit eksternal lama
   kalau dirasa UI sudah cukup matang.

**SEBELUMNYA (2026-08-06, v3.20.1 — HOTFIX build CI v3.20.0):**
1. Cek run CI v3.20.1 SEBELUM apa pun lain — belum di-push ulang/
   dikonfirmasi hijau sama sekali sejak fix type-mismatch ini.
2. Kalau hijau, lanjut checklist device v3.20.0 di bawah (spinner+haptic).

**SEBELUMNYA (2026-08-06, v3.20.0 — UI/UX polish batch 1/N):**
1. ~~Cek CI v3.20.0~~ — GAGAL, lihat v3.20.1 di atas, sudah di-fix.
2. Device: tekan ProtectionRing + Switch WARP/IKEv2 — konfirmasi getar
   terasa (device Infinix XOS user, beberapa OEM strip haptic vendor
   sendiri, WAJIB dikonfirmasi nyata bukan cuma asumsi API terpanggil).
3. Device: nyalakan WARP/IKEv2, perhatikan slot icon selama fase
   "Menyambungkan…" — spinner harus terlihat jalan, bukan macet.
4. Kalau user mau lanjut polish batch 2/N: `OnboardingScreen` transisi,
   accessibility/TalkBack label pass — lihat entri v3.20.0 di atas.

**SEBELUMNYA (2026-08-06, v3.17.1 — HOTFIX build CI v3.17.0):**
1. Cek run CI v3.17.1 SEBELUM apa pun lain — belum di-push ulang/dikonfirmasi
   hijau sama sekali sejak fix "Unclosed comment" ini.
2. Kalau CI hijau: minta user coba toggle DNS Ad-Block di device fisik,
   pastikan domain non-blocklist tetap resolve normal (skenario paling
   sensitif dari refactor v3.17.0 — lihat catatan di entri v3.17.0).
3. Sisa item audit eksternal user (2026-08-06) yang BELUM dikerjakan:
   stress test trafik tinggi, optimasi blocklist engine lebih lanjut,
   crash/performance monitoring tambahan, perkuat auto recovery VPN,
   sederhanakan UX/Home screen — tanya user urutan prioritas berikutnya.

**SEBELUMNYA (2026-08-06, Concurrency & Lifecycle audit batch 2/N):**
1. Cek run CI v3.16.9 (build ijo?) — belum pernah dicek sama sekali.
2. Verifikasi device: buka Rules screen, tambah/hapus domain di
   "Izinkan" SAAT VPN DNS Ad-Block aktif dan sedang dipakai browsing —
   pastikan tidak ada domain allow-list yang kepencet blokir sesaat
   (sebelumnya race ini sulit direproduksi manual karena jendelanya
   sempit, jadi ini lebih ke sanity-check daripada bukti definitif).
3. Sisa target Concurrency & Lifecycle kalau user minta lebih dalam:
   `MainViewModel`/`ui/screens/*` (StateFlow collection scoping),
   `data/db/*` (Room DAO transaction safety).
4. `IkeV2VpnEngine.engineScope` masih belum di-cancel dari
   `MainViewModel` (lihat catatan v3.16.8) — masih lower-priority,
   angkat kalau user eksplisit minta.
5. Setelah Concurrency & Lifecycle dianggap cukup, lanjut ke Security
   (urutan checklist user berikutnya).

**PALING BARU & PALING PENTING (2026-08-05, v3.10.1): konfirmasi fix total
DNS failure benar-benar memulihkan koneksi.** (a) build CI sukses, (b)
nyalakan mode DNS Ad-Block di jaringan yang sama persis dengan yang gagal
sebelumnya (4.5G), (c) coba browsing/buka app apa pun — HARUS normal, tidak
ada lagi total loss, (d) kalau masih gagal: itu bukan lagi soal resolver
Cloudflare-vs-Google — cek apakah UDP port 53 diblokir total di jaringan
itu (butuh solusi DoH/DoT, bukan ganti-ganti IP resolver lagi).

**SEBELUMNYA (2026-08-05, v3.10.0): cek di device fisik.** (a) build CI
sukses, (b) buka Diagnostik saat app di background lama / setelah dipakai
berat (mode WARP+DNS bergantian) — apakah PSS app kelihatan wajar atau
ada indikasi leak (naik terus tanpa turun), (c) amati field baterai
(persen/suhu) beberapa saat — pastikan angkanya masuk akal dibanding
Pengaturan > Baterai sistem, (d) belum ada mekanisme alert/notifikasi
otomatis kalau resource terlalu tinggi — ini snapshot manual only, cocokkan
ekspektasi user apakah itu cukup atau perlu ambang batas otomatis nanti.

**PALING BARU (2026-08-05, v3.14.0) — cek ini DULUAN sebelum apa pun lain:**
build CI sukses dulu untuk batch IKEv2 engine ini (2 file baru/diubah di
`protocol/`, 0 file DNS/WARP existing disentuh) — perhatikan khusus
`@RequiresApi`/import `android.net.Ikev2VpnProfile` dkk yang butuh
compileSdk 34 (sudah terpasang di project ini).
**UPDATE (2026-08-06): Batch 4 Xray-core (Shadowsocks/VLESS) DIBATALKAN
PERMANEN**, sejajar OpenVPN — user memutuskan skip protokol yang
berisiko/kompleks (butuh toolchain Go+gomobile+NDK sendiri + risiko
lisensi), cukup protokol native/API resmi yang sudah ada: DNS Ad-Block +
WARP(WireGuard) + IKEv2. **Roadmap protokol VPN dianggap SELESAI**
kecuali user eksplisit angkat topik protokol baru lagi — kalau iya,
langkah yang tersisa cuma migrasi UI ke `VpnEngine` terpadu (WARP+IKEv2
sekaligus, belum diminta user).
**OpenVPN DIBATALKAN PERMANEN** — jangan diangkat lagi kecuali user
eksplisit minta & terima konsekuensi GPL/AGPL (lihat entri v3.14.0).
Krisis DNS/DoH (v3.9.0–v3.11.1) DITINGGALKAN user tanpa resolusi pasti —
TIDAK perlu dikejar lagi kecuali user eksplisit angkat topik itu lagi.

**SEBELUMNYA (2026-08-05, v3.13.0):** build CI batch adapter WARP — belum
dikonfirmasi juga, cek bareng v3.14.0 di atas kalau di-bundle satu push.

**SEBELUMNYA (2026-08-05, v3.10.2):** fix MTU 32000→1500 — TIDAK
menolong (superseded by v3.11.0 DoH), jangan diulang cek terpisah.

**SEBELUMNYA (2026-08-05, v3.9.0): sebelum apa pun yang lain, cek 4 hal dari batch
Internet Surfing Optimization #2 di device fisik:** (a) build CI sukses
dulu, (b) nyalakan mode DNS Ad-Block, tunggu ~3 detik, buka Diagnostics/Logs
— cek apakah ada lookup untuk domain populer (google.com, youtube.com, dst)
yang muncul TANPA user membuka app apa pun (bukti prefetch jalan), (c)
buka Chrome/app apa pun ke salah satu domain di `Constants.
POPULAR_PREFETCH_DOMAINS` segera setelah toggle DNS ON — harus terasa lebih
cepat resolve pertama kalinya dibanding domain di luar daftar itu, (d)
nyalakan mode WARP — cek kartu kualitas di Diagnostics/Home terisi
latency/traffic-confirmed dalam hitungan detik pertama, BUKAN kosong sampai
~8 detik seperti versi sebelumnya.

**SEBELUMNYA (2026-08-05, v3.8.0): cek QS Tile di
device fisik.** Urutan: (a) build CI sukses dulu, (b) tambahkan kedua tile
dari panel "Edit tiles" QS Android, (c) coba toggle DNS saat izin VPN
BELUM pernah diberikan — konfirmasi dialog izin muncul lalu tile langsung
aktif tanpa app kelihatan "terbuka" di Recents setelahnya, (d) coba toggle
lagi (izin sudah ada) — konfirmasi BENAR-BENAR tidak ada flash Activity
sama sekali, (e) ulangi (c)-(d) untuk tile WARP, (f) coba toggle satu tile
saat mode lain aktif — konfirmasi mutual exclusion tetap berlaku sama
seperti dari Home screen.

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
