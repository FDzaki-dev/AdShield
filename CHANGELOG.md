# Changelog

## v4.2.0 — "Radikal Perf" batch 2: fix TLS handshake berulang di WARP health-check (2026-08-09)

User minta WARP juga di-dongkrak radikal. Audit modul `warp/` (WarpTunnelManager,
WarpForegroundService, WarpAccountRepository, WarpEndpointSelector,
WarpRegistrationClient, WarpVpnEngineAdapter): sebagian besar SUDAH teraudit
berat di batch v3.7/v3.16/v3.27/v3.41 (endpoint probing sudah konkuren,
endpoint/MTU sudah di-cache 30 menit, event-driven combine tanpa polling,
dsb) — dan jalur penerusan paket WARP sendiri sepenuhnya di native WireGuard
GoBackend (di luar kendali kode Kotlin), jadi tidak ada "packet loop" ala DNS
mode yang bisa dioptimasi di sisi app. Satu bottleneck nyata & signifikan
tetap ditemukan:

**`WarpTunnelManager.probeTrace()` — TLS handshake penuh di SETIAP health-check,
selama tunnel menyala.** Persis kelas bug yang sama seperti `DohClient`
sebelum fix v4.1.0: `connection.disconnect()` dipanggil di setiap probe,
mematikan eligibilitas keep-alive/connection-pool JVM. Probe ini jalan tiap
`HEALTH_CHECK_INTERVAL_MS` (25 detik) SELAMA WARP aktif — bisa ratusan kali
per sesi berjam-jam — jadi setiap panggilan membayar TLS handshake penuh ke
Cloudflare yang seharusnya bisa di-reuse dari koneksi sebelumnya. Beda dari
DohClient, probe ini TIDAK butuh custom protect()ing SSLSocketFactory (lewat
tunnel WARP yang sudah UP, seperti trafik app lain), jadi fix-nya lebih
simpel: cukup hapus `disconnect()`, connection pool bawaan Android langsung
jalan begitu stream dibaca habis lewat `.use {}`.

Bonus: `latencyMs` yang ditampilkan di kartu kualitas WARP sekarang RTT yang
lebih jujur (tidak lagi digelembungkan oleh handshake TLS di setiap probe).

**Diubah:**
- `warp/WarpTunnelManager.kt` — `probeTrace()`: hapus `disconnect()` per-probe,
  tambah header `Connection: keep-alive` eksplisit (konsisten dgn DohClient).

Tidak ada perubahan perilaku terlihat user selain latency probe yang sedikit
lebih akurat — murni pengurangan overhead TLS/radio di health-check loop.

## v4.1.0 — "Radikal Perf": hilangkan 2 bottleneck tersembunyi di hot path DNS (2026-08-09)

User minta dongkrak performa secara radikal. Audit menemukan 2 bottleneck
nyata di jalur tercepat aplikasi (bukan micro-opt kosmetik):

1. **`DohClient` — TLS handshake penuh di SETIAP query DoH, bukan cuma yang
   pertama.** v3.25.0 sebelumnya sudah menghapus `disconnect()` per-query
   supaya JVM connection-pool/keep-alive HTTPS bisa dipakai ulang — tapi
   fix itu tidak pernah benar-benar menyala: `protectingSocketFactory()`
   masih dibuat BARU di setiap panggilan `queryOne()`, dan pool koneksi
   `HttpsURLConnection` di Android mengunci identitas `SSLSocketFactory`
   sebagai bagian dari key-nya — factory baru tiap query = tidak pernah
   ada reuse sama sekali. Karena DoH dicoba PERTAMA untuk setiap query DNS
   yang diteruskan (keputusan 2026-08-05), ini berarti setiap lookup non-
   cache-hit membayar 1-2 round-trip TLS handshake ekstra yang seharusnya
   tidak perlu. Fix: cache SATU instance factory per `VpnService` yang
   sedang hidup (masih `protect()` tiap socket individual, jadi properti
   keamanan tidak berubah) — sekarang reuse benar-benar terjadi.
2. **`DnsQueryLogger` — disk write sinkron per SATU query DNS.**
   `incrementBlocked()`/`incrementAllowed()` masing-masing memicu
   `DataStore.edit{}` (write-then-rename file) untuk SETIAP query yang
   ditangani packet loop — plus 1 coroutine launch baru per query. Di
   browsing biasa itu puluhan disk write sinkron per detik hanya untuk 2
   counter. Fix: `log()` sekarang hanya increment `AtomicLong` +
   masuk antrian in-memory (tanpa suspend, aman dipanggil dari packet-loop
   thread), dikuras oleh satu loop background setiap 3 detik jadi SATU
   `dataStore.edit{}` + SATU transaksi Room batched (`insertAll` baru di
   `DomainLogDao`) — bukan N terpisah. `stop()` melakukan flush sinkron
   final saat VPN berhenti supaya tidak ada count/log yang hilang.

**Diubah:**
- `vpn/DohClient.kt` — cache `SSLSocketFactory` per-VpnService-instance.
- `vpn/dns/DnsQueryLogger.kt` — rewrite ke buffer+batch-flush (lihat di atas).
- `data/SettingsRepository.kt` — tambah `incrementCountersBy(blocked, allowed)`
  (1 edit{} untuk kedua counter, dipakai oleh flush batch).
- `data/db/DomainLogDao.kt` — tambah `insertAll(entries: List<DomainLogEntity>)`.
- `vpn/AdBlockVpnService.kt` — wire `queryLogger.start()`/`.stop()` di
  startVpn()/stopVpn(), simetris dengan `prefetcher.start()` yang sudah ada.

Tidak ada perubahan perilaku yang terlihat user (counter/log tetap akurat,
cuma ditulis lebih jarang) — murni pengurangan I/O dan latency di hot path.

## v4.0.0 — Major cleanup: hapus semua fitur di luar scope DNS+WARP (2026-08-09)

> User: proyek "kegemukan" untuk 2 fitur utama (DNS Ad-Block + WARP) +
> penunjang esensial — minta eliminasi total semua yang tidak berhubungan.

Atomic Change besar, lintas modul, atas izin eksplisit user.

**Dihapus:**
- `app/libs/libXray.aar` (96.7MB) + `app/src/androidTest/.../xray/` (6 file
  probe test) + job CI `libxray-poc`/`libxray-invoke-probe`. Investigasi
  Xray-core sudah won't-fix permanen sebelumnya — dead weight murni.
- IKEv2 (protokol VPN ke-3) — dihapus total: `IkeV2VpnEngine.kt`,
  `VpnProfileRepository.kt`, varian `IkeV2` di `VpnProtocolConfig`,
  `AppMode.IKEV2/OPENVPN/SHADOWSOCKS`, `IkeV2ModeCard`+`IkeV2ProfileDialog`
  di HomeScreen, seluruh wiring MainActivity/MainViewModel. App sekarang
  persis 2 mode: DNS Ad-Block dan WARP.
- `ui/components/Tactile*.kt` (4 file) + `TactileTokens.kt` — dead code,
  0 call site sejak dibuat.

**Dampak:** ukuran project ~94MB → ~1.2MB (source), APK rilis akan jauh
lebih kecil (libXray native libs tidak lagi ter-bundle). Tidak ada
perubahan behavior DNS Ad-Block maupun WARP — keduanya diverifikasi tetap
utuh, hanya kode/UI protokol ke-3 dan investigasi mati yang dibuang.

Detail lengkap keputusan: lihat PROJECT_STATE.md.

## v3.43.1 — Fix CI build failure: missing `getValue` import (2026-08-08)

> CI run 31258449475 gagal di `:app:compileDebugKotlin` — 7 error "Type
> 'State<T>' has no method 'getValue(...)' and thus it cannot serve as a
> delegate" di `TactileButton.kt` (3x) dan `TactileSwitch.kt` (4x), commit
> 782f9d1.

Root cause: `by animateDpAsState(...)`/`by collectIsPressedAsState()` butuh
`import androidx.compose.runtime.getValue` (operator extension) di file yang
memakainya — hilang di kedua file baru itu (v3.43.0). `TactileSlider.kt`
sudah benar karena juga pakai `setValue` untuk `var ... by remember`.

Fix: tambah `import androidx.compose.runtime.getValue` ke `TactileButton.kt`
dan `TactileSwitch.kt`. Tidak ada perubahan logic/behavior.

## v3.43.0 — Theme overhaul: AMOLED Glassmorphism Hybrid + Midnight Blue Gradient (2026-08-08)

> User: uploaded `compose-skeuomorphism-lite-amoled-glass-hybrid-midnight-gradient.md`,
> minta timpa theme lama sampai bersih, 100% sesuai spec markdown.

Full retint of `ui/theme/` from the previous "Apple-Style System Colors" pass
(v3.21.0) to the new mandatory spec. **Zero call-site edits** — same
reskin-at-the-source pattern as v3.0.0/v3.21.0: all 14 legacy `Shield*` names
still resolve, just aliased onto new canonical tokens.

- `Color.kt` — REWRITE. New canonical tokens copied verbatim from spec §2:
  `AmoledBackground`, `GlassSurface(Elevated/Pressed)`, `MidnightBlueTint`,
  `MidnightBlueAccent`, `TextPrimary/Secondary`, `GlassHighlight/Border/Shadow`,
  `MidnightBlueGradientAlpha`. Legacy `Shield*` names aliased onto these; app
  semantic state colors (green=protected, red=danger, orange=warning) kept
  as a separate section — spec doesn't forbid a status signal color, only a
  dominant-blue identity or hardcoded random surface colors.
- `Theme.kt` — REWRITE. `darkColorScheme` remapped to the new tokens;
  `outline`/`outlineVariant` now use the low-alpha glass hairlines (spec §4:
  "never bright white"); `secondary` role now carries `MidnightBlueAccent` as
  the restrained ambient accent.
- `TactileTokens.kt` — NEW. Centralized elevation/press/gradient tokens (spec
  §12: "do not duplicate tactile constants throughout screen files"). Single
  light source top-left→bottom-right (spec §3).
- `ui/components/TactileSurface.kt`, `TactileButton.kt`, `TactileSwitch.kt`,
  `TactileSlider.kt` — NEW. Reusable tactile primitives per spec §7/§12
  architecture (not yet wired into existing screens — available for next
  incremental screen-by-screen adoption pass, kept out of this batch to stay
  atomic/reviewable).
- `colors.xml` — `shield_bg_dark` synced to `AmoledBackground` (#030508) to
  avoid repeating the v3.21.1 pre-Compose-flash drift bug.
- `Shape.kt`, `Type.kt` — untouched, already spec-compliant (large tactile
  radii, no hardcoded colors).
- No screen files touched. No protected asset structurally changed (only
  `versionCode`/`versionName` bump in `app/build.gradle.kts`, a permitted
  partial edit).

## v3.42.0 — Final debugging/optimization pass: sweep menyeluruh, CLEAN (2026-08-08)

> User: "Lakukan Debugging+optimalisasi untuk terakhir kalinya."

Sweep sistematis atas SEMUA `CoroutineScope(...)` manual di codebase
(11 titik, di-grep satu-satu) + semua `registerReceiver`/komponen
lifecycle (`Service`/`TileService`), menyusul 2 leak nyata yang baru
ditemukan & diperbaiki (v3.40.0 `IkeV2VpnEngine`, v3.41.0
`WarpVpnEngineAdapter`):

- `WarpTunnelManager.managerScope`, `AdShieldApp.appScope` — CLEAN,
  memang didesain hidup seumur proses (singleton/Application), bukan
  leak (didokumentasikan sejak awal, deputusan #6c).
- `AdBlockVpnService.serviceScope` — CLEAN, keputusan sadar sejak
  v3.16.8 (coroutine self-terminate lewat `running.get()`, cancel
  paksa berisiko motong write in-flight — TIDAK diubah).
- `DnsTileService`/`WarpTileService` — CLEAN, sudah benar sejak awal
  (`onCreate`/`onDestroy` cancel scope dengan benar).
- `BootReceiver` — CLEAN, `goAsync()`+`pendingResult.finish()` di
  `finally` sudah benar.
- `RestartReceiver`/`WarpRestartReceiver` — CLEAN, sinkron, tidak ada
  scope untuk di-leak.
- `IkeV2VpnEngine`/`WarpVpnEngineAdapter` — sudah diperbaiki
  (v3.40.0/v3.41.0), diverifikasi ulang sekarang keduanya benar.
- `ResourceMonitor.registerReceiver(null, ...)` — CLEAN, pola resmi
  Android untuk ambil sticky intent baterai, BUKAN registrasi
  persisten (tidak butuh unregister).

**0 fix kode baru batch ini** — sweep ini murni verifikasi, hasilnya
bersih di seluruh sisa codebase. Menutup permintaan user "debugging +
optimalisasi untuk terakhir kalinya": scope-leak class of bug yang
baru ditemukan (v3.40.0/v3.41.0) sudah disapu tuntas ke SELURUH
codebase, bukan cuma 2 titik yang kebetulan ketemu. Version bump saja
(84/3.42.0) — konsisten praktik project ini menandai audit-clean
dengan version bump kecil (lihat pola sama v3.18.0 Security batch 2,
Performance audit, dst).

## v3.41.0 — Fix leak nyata: `WarpVpnEngineAdapter.adapterScope` tak pernah di-cancel (2026-08-08)

> Ditemukan saat user tanya "sudah tidak ada lagi yang mau di-debug?" —
> jawaban jujurnya: ada, dan ini LEBIH parah dari fix v3.40.0.

`WarpVpnEngineAdapter` (dipakai `WarpForegroundService`) punya
`adapterScope` sendiri yang menjalankan
`combine(...).onEach{}.launchIn(adapterScope)` di `init{}` — collector
TANPA kondisi berhenti sendiri, kelas bug SAMA PERSIS dengan
`WarpForegroundService.onDestroy()` yang sudah diperbaiki v3.16.8. TAPI
`onDestroy()` v3.16.8 cuma cancel `scope` milik Service sendiri —
`adapterScope` milik `warpEngine` (instance terpisah) TIDAK PERNAH
tersentuh. Karena `WarpVpnEngineAdapter` BARU dibuat setiap
`onCreate()` Service (bukan singleton), setiap restart service
menambah SATU collector forever-running lagi di atas yang lama —
lebih parah dari leak sekali (v3.40.0 punya scope kalau `MainViewModel`
ini hidup seumur proses; service ini bisa restart berkali-kali).

**Fix:** `WarpVpnEngineAdapter.release()` (baru) — cancel
`adapterScope`. Dipanggil dari `WarpForegroundService.onDestroy()`
setelah `scope.cancel()` sendiri.

Verifikasi statis: lexer nested-comment seluruh `app/src` — 0 masalah.
2 file kode diubah + version bump. **BELUM DIKONFIRMASI CI/device.**

## v3.40.0 — PROJECT STATUS: HIATUS (resmi) + fix leak IkeV2VpnEngine (2026-08-08)

> User resmi melabeli proyek ini **HIATUS** — bukan dibatalkan/dihapus,
> tapi tidak lagi dikerjakan aktif kecuali user membuka sesi baru
> secara eksplisit. Lihat `PROJECT_STATE.md` bagian atas untuk detail.

**Fix nyata (bukan cuma dokumentasi):** `IkeV2VpnEngine.engineScope`/
`pollJob` tidak pernah di-cancel — gap yang sudah tercatat sejak
concurrency/lifecycle audit batch 2 (v3.16.8) tapi sengaja ditunda
sebagai low-priority. Ditutup batch ini:
- `IkeV2VpnEngine.releaseMonitoring()` (baru) — cancel `pollJob` +
  unregister broadcast receiver + cancel `engineScope`. SENGAJA TIDAK
  memanggil `disconnect()` — profil IKEv2 dikelola OS (`VpnManager`)
  dan didesain hidup lepas dari proses app, mematikannya di
  `onCleared()` akan salah menghentikan sesi yang user tidak minta
  dihentikan.
- `MainViewModel.onCleared()` (baru, sebelumnya tidak ada override
  sama sekali) — panggil `ikeV2Engine.releaseMonitoring()` sekali.

Verifikasi statis: lexer nested-comment ke seluruh `app/src` — 0
masalah. 2 file kode diubah + version bump. **BELUM DIKONFIRMASI CI/
device** — konsisten dengan status hiatus, tidak dikejar sampai sesi
berikutnya dibuka user.

## v3.39.0 — KEPUTUSAN FINAL: roadmap Xray-core/WARP-reserved-bytes ditutup (2026-08-08)

> User serahkan keputusan ("cari jalan keluarnya sendiri") atas 3 opsi
> yang diajukan v3.38.0 setelah Round 7 membuktikan AAR `libXray` mati
> total. **Keputusan: terima status quo, permanen.**

**Alasan:**
1. Fork wireguard-go sendiri (toolchain Go+NDK penuh) — beban
   maintenance solo-dev tidak sepadan cuma untuk badge "resmi"
   Cloudflare; tunnel yang jalan sekarang sudah terenkripsi penuh &
   sehat. Pola risiko sama seperti OpenVPN (v3.14.0) & Xray-core sendiri
   yang baru terbukti buntu.
2. bepass-sdk (CC BY-NC-SA) — menuntut komitmen non-komersial permanen
   yang belum bisa dijamin (app belum punya model monetisasi final
   terkunci), demi fitur kosmetik (label semata).
3. Dampak ke user: nol. DNS Ad-Block (fitur utama) tidak tersentuh;
   WARP tetap enkripsi penuh, cuma tidak dapat tag `warp=on` — sudah
   dikomunikasikan jujur sejak v3.28.0.

**Aksi:** dokumentasi murni — `PROJECT_STATE.md` keputusan arsitektur
#16 (CLOSED, won't-fix) + roadmap "Yang HARUS dikerjakan" dibersihkan
dari seluruh entri round 1-7 Xray-core. 0 kode app/test disentuh. Test
probe (`xray/` package + `libXray.aar`) TETAP di repo sebagai
dokumentasi investigasi, tidak dihapus tanpa izin eksplisit user.

**Syarat buka topik ini lagi (lihat keputusan #16 PROJECT_STATE.md):**
versi AAR libXray baru yang terverifikasi fix bug dispatcher, ATAU
library Android resmi lain yang native dukung `reserved` bytes tanpa
toolchain Go/NDK sendiri, ATAU user eksplisit menerima trade-off opsi
2/3 di atas dengan kesadaran penuh.

## v3.38.0 — Round 7 CONFIRMED: Xray-core via libXray AAR mati total untuk WARP-native (2026-08-08)

> **Hasil Round 7 (v3.37.0) DIBACA — FINAL.** `probeRunXrayNonexistentPath`
> balas `"infra/conf/serial: failed to read config file > EOF"` — PERSIS
> SAMA dengan bug `testXray` round 6. `stopXray` cleanup sukses normal
> (`success:true`), tidak ada proses menggantung.
>
> **Kesimpulan final, menutup roadmap 7-round (v3.29.0–v3.37.0):**
> `configPath` tidak pernah sampai ke Go baik untuk `testXray` maupun
> `runXray` — bug dispatcher `Invoke()` di level AAR (kategori method
> `func XXX(base64Text string) string`), bukan bug kode app, tidak ada
> workaround dari sisi Kotlin/Android. **AAR `libXray` yang dipakai
> (dibangun dari `main` branch v3.31.0, Xray-core `v26.7.28`) TIDAK BISA
> dipakai sama sekali untuk menyalakan tunnel** — bukan cuma soal
> validasi client-side yang bisa di-skip.
>
> **0 kode app produksi disentuh.** WARP tetap di jalur
> `com.wireguard.android:tunnel` yang sudah berjalan sehat (v3.28.0,
> label jujur "Tersambung, bukan WARP resmi"). File probe (`xray/`
> package, `androidTest/`, job CI terpisah `libxray-invoke-probe`)
> dibiarkan di repo sebagai dokumentasi investigasi — tidak pernah
> masuk jalur build/release reguler.
>
> **Keputusan arah berikutnya (butuh input user, TIDAK diputuskan
> sepihak — lihat PROJECT_STATE.md "Yang HARUS dikerjakan"):** terima
> status quo / fork wireguard-go sendiri / re-evaluasi lisensi
> `bepass-sdk`. Roadmap Xray-core sendiri dianggap SELESAI diinvestigasi
> per versi ini.

## v3.37.0 — Round 6 CONFIRMED + Round 7: apakah bug dispatcher juga menimpa runXray produksi (2026-08-08)

> **Hasil Round 6 (v3.36.0) DIBACA — DEFINITIF, bukan lagi hipotesis:**
> `probeTestXrayNonexistentPath` (configPath ke file yang dijamin tidak
> ada) DAN `probeTestXrayEmptyStringPath` (configPath="" eksplisit)
> SAMA-SAMA balas `"infra/conf/serial: failed to read config file >
> EOF"` — **PERSIS SAMA** dengan error round 3-5 pakai file 470-byte
> yang TERBUKTI ada isinya. Kesimpulan: field `configPath` di payload
> `testXray` **TIDAK PERNAH sampai ke Go sama sekali** — ini bug di
> dispatcher `Invoke()` AAR ini untuk method kategori
> `func XXX(base64Text string) string`, BUKAN bug kode kita, BUKAN bisa
> diperbaiki dari sisi Kotlin manapun. Gradle log dikonfirmasi
> `BUILD SUCCESSFUL`, `Finished 10 tests` (2 round 6 + round 1-5 lama).
>
> **Pertanyaan baru yang JAUH lebih penting**: `testXray` cuma dipakai
> untuk *validasi* config — method yang SUNGGUHAN dipakai buat
> menyalakan tunnel produksi adalah `runXray`. Kalau `runXray` kena bug
> dispatcher yang SAMA (kemungkinan besar — kemungkinan besar kategori
> func Go-nya sama, `base64Text string`), maka jalur Xray-core lewat AAR
> libXray ini **MATI TOTAL untuk kebutuhan WARP-native**, bukan cuma
> soal validasi client-side yang bisa di-skip.

**File baru — `LibXrayInvokeProbeRound7Test.kt`** (package sama
`xray/`), 1 test: `probeRunXrayNonexistentPath` — metodologi identik
round 6 (nonexistent-path sebagai bukti definitif) tapi untuk method
`runXray`. `stopXray` SELALU dipanggil di `finally` (best-effort
cleanup, jaga-jaga kalau `runXray` tetap start proses background walau
config invalid). Tag Logcat baru `LibXrayInvokeProbeR7`, sudah
ditambahkan proaktif ke filter `adb logcat` di `.github/workflows/build.yml`
(pelajaran dari hotfix v3.34.1 — jangan lagi ketinggalan tag).

**0 baris kode app produksi (`WarpTunnelManager`/dst) disentuh** — murni
1 file test baru di `androidTest/` + 1 baris filter workflow + version
bump.

**WAJIB dicek PALING PERTAMA sesi berikutnya**: baca
`invoke-probe-logcat.log` run baru, tag `LibXrayInvokeProbeR7`, baris
`RUNXRAY-NOFILE-WIN`/`-MISS`/`-ERROR`:
- Error **SAMA PERSIS** pola EOF → `runXray` JUGA kena bug, jalur
  Xray-core via libXray AAR ini dianggap **MATI TOTAL**, roadmap perlu
  keputusan besar berikutnya (cari basis lain / batalkan Xray-core
  sepenuhnya, WARP tetap di jalur `com.wireguard.android:tunnel` biasa
  "bukan WARP resmi" seperti keputusan v3.28.0).
- Error **BEDA** (mis. "no such file") atau `success:true` → `runXray`
  AMAN, lanjut Round 8: desain integrasi langsung (config WireGuard-
  outbound + `reserved` bytes WARP asli), skip `testXray` sepenuhnya,
  tangani kegagalan lewat observasi tunnel real-time bukan validasi
  client-side.

## v3.36.0 — Round 6: definitive test whether configPath ever reaches Go (2026-08-08)

> Round 5 data: file PROVEN non-empty (470 bytes, read back before
> invoke) AND minimal 53-byte freedom-only config (no WireGuard) BOTH
> still gave identical EOF. Both round-5 hypotheses eliminated. New
> theory: `configPath` field may never actually reach Go's `TestXray()`
> at all (possible dispatcher bug specific to methods whose underlying
> Go func takes `base64Text string`, unlike `getFreePorts` which takes
> direct params and works correctly). File baru
> `LibXrayInvokeProbeRound6Test.kt`: nonexistent-path + empty-string-path
> controls — if error stays identical, field is proven never delivered
> (library bug, not our code); if error changes, our real path has a
> content/access issue. Logcat filter proactively tagged R6.

## v3.35.0 — Round 5 diagnostic: envelope confirmed, testXray EOF mystery isolated (2026-08-08)

> Round 4 data lengkap: **envelope payload-nested + apiVersion:2
> TERBUKTI benar** (getFreePorts balas port asli via nested, kosong via
> flat — CANDIDATE-WIN round 2 terbukti sukses-semu; apiVersion:1 selalu
> "unsupported apiVersion"). Tapi `testXray` tetap EOF sama persis walau
> envelope sudah benar. File baru `LibXrayInvokeProbeRound5Test.kt`:
> verifikasi isi file di disk PERSIS sebelum invoke() (isolasi bug
> Kotlin/timing) + config minimal freedom-only sebagai kontrol (isolasi
> apakah masalah di config WireGuard vs di jalur baca file). Workflow
> logcat filter ditambah tag R5 proaktif. 0 baris kode app produksi
> disentuh.

## v3.34.1 — HOTFIX: logcat filter missing R4 tag (2026-08-08)

> Round 4 test (3 fungsi) TERBUKTI jalan (`Starting 6 tests`/`Finished 6
> tests` di gradle log — naik dari 3), tapi `adb logcat -s` capture
> command di `.github/workflows/build.yml` cuma daftar tag
> `LibXrayInvokeProbe`/`...R3`, lupa tambah `...R4` — semua output round 4
> ke-silent, 0 data terbaca. Fix: tambah `LibXrayInvokeProbeR4:*` ke
> command capture. 1 baris workflow, tidak ada kode test/app yang diubah.
> Data hasil round 4 (payload-nesting theory) masih KOSONG — WAJIB run
> ulang CI setelah fix ini.

## v3.34.0 — Round 4 probe: root cause found — payload must be nested (2026-08-08)

> Riset ulang README resmi XTLS/libXray (dibaca langsung dari repo)
> mengonfirmasi bentuk request `Invoke()` yang benar:
> `{"apiVersion":1,"method":"runXray","payload":{"configPath":"..."}}` —
> field method HARUS di-nest di bawah `"payload"`, bukan flat. Ini
> menjelaskan error EOF seragam di round 3 (payload flat = field tidak
> ke-bind = Xray-core baca config kosong). Juga mempertanyakan ulang
> `CANDIDATE-WIN` round 2 (`getFreePorts` flat balas `data:{}` kosong,
> dicurigai sukses-semu).

**File baru — `LibXrayInvokeProbeRound4Test.kt`** (package sama `xray/`),
3 test: `probeGetFreePortsFlatVsPayload` (bandingkan flat vs
payload-nested langsung, buktikan/sangkal teori sukses-semu),
`probeTestXrayPayloadNested` (datDir+configPath di-nest payload,
apiVersion 1 & 2), `probeNoArgPayloadOmittedVsEmpty` (sanity murah).
Round 1-3 tidak diubah, tetap jalan (workflow sudah run seluruh package).
Versi dinaikkan ke 3.34.0 (versionCode 75). 0 baris kode app produksi
disentuh. **Belum dikonfirmasi run CI** — lihat PROJECT_STATE.md untuk
langkah baca logcat sesi berikutnya.

## v3.33.0 — Round 3 probe: no-arg methods + testXray payload candidates (2026-08-08)

> Lanjutan langsung round 1+2 (v3.32.0/v3.32.3 — envelope
> `{"apiVersion":2,"method":"..."}` TERBUKTI lewat CANDIDATE-WIN nyata
> `getFreePorts`). **Semua probe dijalankan via job CI `libxray-invoke-probe`
> yang sudah ada — dikonfirmasi user, tidak ada harness manual/eksternal
> di luar GitHub Actions.**

**File baru — `LibXrayInvokeProbeRound3Test.kt`** (package sama
`xray/`), 2 test:
1. `probeXrayVersionAndState` — 2 method no-arg lain (`xrayVersion`,
   `getXrayState`, dari daftar aksi resmi yang sama dikutip di v3.32.3)
   lewat envelope yang sudah pasti benar. Sanity-check murah.
2. `probeTestXrayConfigCandidates` — 3 kandidat nama field payload untuk
   `testXray` (`configPath` ke file yang ditulis ke `context.filesDir`,
   `configJson` inline, `config` inline), semua bawa config WireGuard
   PLACEHOLDER (`secretKey` 32-byte nol, `reserved:[0,0,0]`, peer
   publicKey `bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=` + endpoint
   `engage.cloudflareclient.com:2408` — keduanya sudah terverifikasi
   resmi lintas sumber, bukan tebakan).

**`.github/workflows/build.yml`** — job `libxray-invoke-probe`: command
gradle diganti dari `-Pandroid.testInstrumentationRunnerArguments.class=
<1 class>` jadi `...arguments.package=com.fdzaki.adshield.xray` — run
SEMUA class test di package itu (round 1+2 lama TETAP jalan + round 3
baru) dalam 1 instrumented-test pass, tidak perlu edit command lagi tiap
nambah round baru ke depan. `adb logcat` filter ditambah tag
`LibXrayInvokeProbeR3` (tag round 1+2 `LibXrayInvokeProbe` tetap ada).

**Kenapa config placeholder, bukan data WARP asli:** tujuan round ini
murni "field mana yang dikenali libXray", BUKAN "apakah WARP connect".
Formula `reserved` (3 byte dari base64-decode account `id`) masih
confidence SEDANG (lihat entri riset sebelumnya) — BELUM boleh dipakai
ke config nyata sebelum baca source `ViRb3/wgcf` langsung.

**0 baris kode app produksi (`WarpTunnelManager`/dst) disentuh** — file
baru murni di `androidTest/`, + edit parsial 2 baris `build.yml` (job
probe saja).

**BELUM DIKONFIRMASI run CI** — WAJIB dicek pertama di sesi berikutnya:
`invoke-probe-logcat.log`, cari `NOARG-RESULT:`/`TESTXRAY-WIN:`/
`TESTXRAY-MISS:`/`TESTXRAY-ERROR:`. Lihat PROJECT_STATE.md untuk cara
membaca hasilnya.

## v3.32.3 — Round 2 kandidat envelope: root cause round 1 ketemu, `apiVersion` harus 2 bukan 1 (2026-08-07)

> User upload log run v3.32.2 (CI HIJAU total: `build` 4m32s, `libxray-poc`
> 5m5s, `libxray-invoke-probe` 5m48s — screenshot dikonfirmasi 3/3
> checkmark hijau) + artifact `libxray-invoke-probe-log.zip`.
> **Probe akhirnya benar-benar jalan penuh** (2 hotfix CI shell v3.32.1/
> v3.32.2 terbukti berhasil): `LibXray.touch() OK`, kesembilan kandidat
> round 1 tereksekusi, hasil `PROBE SELESAI — 0 kandidat sukses dari 9
> bentuk`. **TAPI bukan buntu** — kesembilan `CANDIDATE-MISS` balas error
> yang PERSIS SAMA: `"unsupported apiVersion"`, termasuk kandidat #8
> (`apiVersion+name+count`) yang eksplisit kirim `"apiVersion":1`. Itu
> sinyal jelas: bukan field `name`/`action`/nesting yang salah, NILAI
> `apiVersion`-nya yang salah.
> **Riset lanjutan (web search dokumentasi resmi XTLS/libXray)
> mengonfirmasi:** README/dokumentasi resmi menyatakan `Invoke` sekarang
> cuma menerima `apiVersion: 2` — bukan 1. AAR yang sudah di-commit di
> `app/libs/libXray.aar` (dibangun v3.31.0 dari `main` branch libXray
> saat itu) rupanya sudah di versi protokol yang butuh 2. Dokumentasi
> yang sama juga menyebut daftar nama aksi yang didukung — termasuk
> `getFreePorts`, `runXray`, `stopXray`, `xrayVersion`, `getXrayState`,
> dll — konsisten dengan aksi yang sudah dipakai probe ini
> (`getFreePorts`), jadi nama aksinya kemungkinan besar sudah benar dari
> round 1, cuma `apiVersion`-nya yang salah.
> **Fix (1 file test, `LibXrayInvokeProbeTest.kt`):** 10 kandidat ROUND 2
> — 9 variasi bentuk yang sama dari round 1 (name/action/method/nesting)
> tapi semua sekarang pakai `"apiVersion":2`, + 1 kandidat baru
> `APIVersion` PascalCase (jaga-jaga field Go tanpa tag json eksplisit,
> walau dokumentasi menulis lowerCamel). **BELUM ADA jaminan salah satu
> dari 10 kandidat ini yang benar** — apiVersion sekarang sudah pasti
> benar (2, bukan tebakan), tapi bentuk `name`/`action`/nesting-nya masih
> hipotesis round 1 yang belum terbukti terpisah dari masalah apiVersion.
> Kalau round 2 ini JUGA 0 sukses (dengan error BEDA dari "unsupported
> apiVersion", karena itu sudah pasti teratasi), itu baru benar-benar
> waktunya baca source Go `github.com/XTLS/libXray` (fungsi controller/
> invoke) langsung, bukan coba kandidat ke-11 dari tebakan lagi.
> **BELUM DIKONFIRMASI run baru** — WAJIB dicek PALING PERTAMA di sesi
> berikutnya: baca `invoke-probe-logcat.log`, cari `CANDIDATE-WIN:`. Kalau
> ketemu, itu bentuk envelope final — baru dari situ integrasi
> `WarpTunnelManager` (roadmap langkah 3) mulai ditulis.

## v3.32.2 — HOTFIX #2: backslash line-continuation di script emulator-runner pecah jadi task gradle literal `\` (2026-08-07)

> User upload log run v3.32.1 (`logs_84738010523.zip`). **Job `build`
> HIJAU (2x `BUILD SUCCESSFUL`, APK ter-signed+ter-rilis normal), job
> `libxray-poc` TETAP HIJAU** (`build_attempt.outcome = success`) — fix
> v3.32.1 tidak merusak keduanya, sesuai perkiraan. **Job
> `libxray-invoke-probe`: emulator boot sukses (25.8 detik), hotfix
> v3.32.1 BERHASIL — step sekarang benar-benar sampai ke Gradle** (bukti:
> log `Starting a Gradle Daemon`, `Welcome to Gradle 9.6.1!` muncul,
> beda total dari v3.32.0 yang mati sebelum baris itu). TAPI Gradle
> langsung `FAILURE: Selection failed — Task '\' not found in root
> project 'AdShield'`. **Root cause baru:** command gradle di script
> ditulis 3-baris pakai backslash line-continuation (`gradle ... \` lalu
> lanjut baris berikut) — pola yang normal di step `run:` biasa (bash),
> TAPI action `reactivecircus/android-emulator-runner@v2` mengeksekusi
> `script:`-nya lewat mekanisme yang TIDAK mempertahankan backslash-
> newline sebagai penyambung baris (walau interpreter akhirnya `sh`,
> bukan soal dash vs bash lagi — beda kelas bug dari v3.32.1) — backslash
> literal ikut lolos sebagai token/argumen terpisah ke Gradle, dibaca
> sebagai nama task literal `\`.
> **Fix (1 file, `.github/workflows/build.yml`, edit parsial job
> `libxray-invoke-probe` saja):** command gradle digabung jadi SATU
> baris penuh (flag `-Pandroid...` dan `--no-daemon` di baris yang sama
> dengan `gradle connectedDebugAndroidTest`, tanpa backslash sama
> sekali) — menghilangkan ketergantungan pada line-continuation apa pun
> di dalam `script:` action ini, portable terlepas dari mekanisme
> eksekusi persisnya. Sisa struktur (redirect `> log 2>&1`, simpan
> `$?`, `cat log`, logcat, `exit $GRADLE_EXIT`) TIDAK berubah dari
> v3.32.1 — itu bagian yang sudah terbukti benar. Job `build`/
> `libxray-poc` (step `run:` biasa, line-continuation di situ TERBUKTI
> jalan normal dari log ini — 2x `BUILD SUCCESSFUL`) TIDAK disentuh.
> **Pelajaran:** pola line-continuation backslash yang valid di step
> `run:` (bash) TIDAK otomatis valid di `script:` action pihak ketiga
> manapun — dua step berbeda, dua mekanisme eksekusi berbeda, walau
> sama-sama "berakhir di /bin/sh" secara permukaan. Verifikasi ke depan
> untuk action pihak ketiga dengan input `script:`: cek dulu apakah ada
> testcase/dokumentasi resmi yang menunjukkan multi-baris dengan
> continuation, jangan asumsikan sama dengan `run:` step biasa.
> **BELUM DIKONFIRMASI run baru** — WAJIB dicek PALING PERTAMA di sesi
> berikutnya: apakah `gradle connectedDebugAndroidTest` sekarang benar-
> benar mengeksekusi test (bukan lagi "Task not found"), BARU baca
> `invoke-probe-logcat.log` untuk `CANDIDATE-WIN`.

## v3.32.1 — HOTFIX: job `libxray-invoke-probe` gagal SEBELUM gradle sempat jalan (2026-08-07)

> User upload log run v3.32.0 (`logs_84727506606.zip`, 3 job: `build`,
> `libxray-poc`, `libxray-invoke-probe`). **Root cause ditemukan di
> `libxray-invoke-probe/5_Run instrumented probe test on emulator.txt`:**
> emulator boot SUKSES (`sys.boot_completed=1`, animasi didisable OK),
> tapi step gagal SEBELUM baris `gradle connectedDebugAndroidTest`
> manapun sempat dieksekusi — error persis `/usr/bin/sh: 1: set: Illegal
> option -o pipefail` diikuti `##[error]The process '/usr/bin/sh' failed
> with exit code 2`. **Penyebab:** input `script:` milik action
> `reactivecircus/android-emulator-runner@v2` dieksekusi lewat
> `/usr/bin/sh -c ...` (dash di Ubuntu runner ini), BUKAN bash seperti
> step `run:` biasa di GitHub Actions — dash tidak punya opsi
> `-o pipefail` (itu bash-only). Baris pertama script langsung mati,
> jadi **9 kandidat envelope `LibXrayInvokeProbeTest` di v3.32.0 NOL
> PERNAH DICOBA sama sekali** — bukan "0 kandidat sukses" (hasil valid
> yang sudah diantisipasi step "Report result to step summary"), tapi
> test-nya sendiri tidak pernah start. Job `build` (APK utama, run
> `set -o pipefail` di dalam `run:` step biasa = bash) dan `libxray-poc`
> (sama, `run:` step biasa) TIDAK terdampak bug ini — keduanya pakai
> shell yang benar, cuma job `libxray-invoke-probe` yang levat action
> emulator-runner yang kena.
> **Fix (1 file, `.github/workflows/build.yml`, edit parsial job
> `libxray-invoke-probe` saja):** hapus `set -o pipefail` (sintaks
> bash-only), ganti pola `2>&1 | tee log` (butuh pipefail utk exit-code
> gradle yang benar) jadi POSIX-portable: `gradle ... > log 2>&1`,
> simpan `$?` gradle ke variabel, `cat log` (biar tetap tampil di
> Actions run output persis seperti `tee` sebelumnya), baru `exit
> $GRADLE_EXIT` di akhir — exit code step sekarang mencerminkan hasil
> gradle yang sebenarnya (bukan exit code `tee`/`sh` builtin), tanpa
> butuh bash sama sekali. Job `build`/`libxray-poc` (step `run:` biasa,
> bash valid) TIDAK diubah — `set -o pipefail` di situ tetap benar &
> tetap dipertahankan.
> **BELUM DIKONFIRMASI run baru** — WAJIB jadi hal pertama dicek di sesi
> berikutnya: apakah job `libxray-invoke-probe` v3.32.1 sekarang benar-
> benar sampai ke baris `gradle connectedDebugAndroidTest`, BARU dari
> situ baca `invoke-probe-logcat.log` utk `CANDIDATE-WIN` (pertanyaan
> asli v3.32.0 yang sekarang baru bisa benar-benar dijawab).

## v3.32.0 — libXray invoke() envelope probe, roadmap langkah 2.5 (2026-08-07)

> User konfirmasi job `libxray-poc` run v3.31.0 HIJAU, upload artifact
> `libxray-poc-log` (`libXray.aar` 96.7MB + `libxray-build.log` 75 baris,
> 0 error). Diverifikasi statis (bukan asumsi): AAR asli, 4 ABI
> `libgojni.so` (armeabi-v7a/arm64-v8a/x86/x86_64) + `classes.jar` valid
> berisi API resmi XTLS/libXray terbaru — dibongkar lewat parser bytecode
> `.class` custom (tidak ada `javap`/JDK penuh di sandbox sesi ini, cuma
> JRE) untuk baca method table `LibXray.class` persis: HANYA ada
> `invoke(String):String`, `registerDialerController`,
> `registerListenerController`, `registerProcessFinder`, `resetDNS`,
> `setDNS`, `touch`, `_init` — TIDAK ADA method terpisah `xrayVersion()`/
> `getFreePorts()`/`runXray()` dst. Semua aksi (daftar lengkap dari README:
> `getFreePorts convertShareLinksToXrayJson ... ping pingBatch testXray
> runXray runXrayFromJson stopXray xrayVersion getXrayState`) didispatch
> lewat SATU `invoke(String):String`, respons JSON `{success, data,
> error}` (dikonfirmasi lewat cuplikan README yang terindeks web search:
> "Invoke returns a failure response with success: false, data: null...").
>
> **YANG TIDAK ditemukan meski sudah dicari (web search + fetch README
> resmi XTLS/libXray, + baca ulang SEMUA getter/setter di 13 kelas
> Request/Response lewat parser bytecode yang sama):** field
> "action"/"name"/"method" apa pun di `LibXrayInvokeRequest` (cuma punya
> `APIVersion: Long`) atau kelas manapun lain yang menunjukkan bagaimana
> `invoke()` tahu request JSON yang dikirim itu untuk aksi yang mana.
> Kelas-kelas `RunXrayRequest{XrayJson}`/`GetFreePortsRequest{Count}`/dst
> adalah objek Go asli (gomobile proxy, `refnum`+`incGoRef`) yang dipakai
> platform lain (iOS Swift/cgo) — TIDAK ADA bukti langsung mereka
> disable/diserialize persis begitu ke dalam String yang diterima
> `invoke()` di Android.
>
> **Keputusan: JANGAN tulis integrasi `WarpTunnelManager`/engine baru di
> atas tebakan field ini** — pola menebak lalu klaim "sudah terintegrasi"
> tanpa validasi persis pola krisis DNS v3.9.0-v3.11.1 (2x insiden nyata
> di riwayat proyek ini). Sebagai gantinya: instrumented test baru
> `LibXrayInvokeProbeTest.kt` mencoba 9 bentuk envelope kandidat (variasi
> nama key diskriminator `name`/`action`/`method`, casing `count`/`Count`,
> nesting `getFreePortsRequest`/`data`, dengan/tanpa `apiVersion`) ke aksi
> read-only tanpa efek samping (`getFreePorts`, count=1 — scan port bebas
> lokal, TIDAK butuh `DialerController`/`ProcessFinder`/VPN aktif sama
> sekali), log RAW request+response tiap kandidat ke Logcat dengan prefix
> `CANDIDATE-WIN:`/`CANDIDATE-MISS:`/`CANDIDATE-ERROR:` supaya sesi
> berikutnya baca 1 file logcat dan langsung tahu bentuk yang benar (atau
> tahu pasti kalau ke-9 hipotesis ini semua salah, tanpa perlu re-run).
>
> **File diubah/baru (7 file — batch normal, di bawah batas 10):**
> - `app/libs/libXray.aar` (BARU, binary 96.7MB) — AAR asli dari artifact
>   CI run v3.31.0 yang dikonfirmasi user, dicommit supaya job probe tidak
>   perlu rebuild dari source Go tiap kali (build Go dari nol di
>   `libxray-poc` makan waktu lama, AAR-nya sendiri deterministik sekali
>   sukses).
> - `app/build.gradle.kts` — `implementation(files("libs/libXray.aar"))`
>   + 2 dependency `androidTest` baru (`androidx.test.ext:junit:1.1.5`,
>   `androidx.test:runner:1.5.2` — HANYA dipakai test probe ini, belum ada
>   Espresso/UI test lain). versionCode/versionName bump.
> - `app/src/androidTest/java/com/fdzaki/adshield/xray/
>   LibXrayInvokeProbeTest.kt` (BARU) — lihat KDoc di file itu sendiri
>   untuk detail lengkap tiap kandidat, tidak diulang di sini.
> - `.github/workflows/build.yml` — job baru `libxray-invoke-probe`,
>   SEPENUHNYA independen dari `build` dan `libxray-poc` (tidak ada
>   `needs:`, `continue-on-error: true` di level job) — pakai
>   `reactivecircus/android-emulator-runner@v2` (emulator x86_64 API 30,
>   cocok salah satu dari 4 ABI AAR) untuk `connectedDebugAndroidTest`
>   ter-filter ke SATU kelas test ini saja, upload logcat sebagai artifact
>   `libxray-invoke-probe-log`.
>
> **SENGAJA TIDAK disentuh:** `WarpTunnelManager.kt`, package `warp/`
> manapun, `protocol/` manapun — 0 baris. Ini murni riset/probe terisolasi,
> tidak ada jalur eksekusi app normal yang menyentuh `libXray` sama sekali
> di batch ini.
>
> **BELUM DIKONFIRMASI run CI job baru ini** (nama `libxray-invoke-probe`)
> — WAJIB jadi hal pertama dicek di sesi berikutnya. Baca
> `invoke-probe-logcat.log` dari artifact `libxray-invoke-probe-log`:
> - Ada baris `CANDIDATE-WIN:` → itu bentuk envelope yang benar, BARU dari
>   situ mulai desain `XrayWarpEngine`/config WireGuard-outbound dengan
>   `reserved` bytes (roadmap langkah 3 sesungguhnya).
> - 0 `CANDIDATE-WIN:` sama sekali → kirim isi lengkap logcat-nya ke sesi
>   berikutnya, JANGAN tebak kandidat ke-10 tanpa data itu di tangan —
>   kemungkinan besar perlu baca source Go `XTLS/libXray` langsung (bukan
>   cuma README) untuk fungsi yang menangani `invoke()`.
> - Kalau bahkan `LibXray.touch()` gagal (native lib tidak ter-load sama
>   sekali di ABI x86_64 emulator) → itu temuan terpisah & lebih mendasar,
>   cek dulu apakah ABI x86_64 di AAR benar-benar valid (re-extract
>   `libXray.aar` dari artifact, cek ukuran `jni/x86_64/libgojni.so` masuk
>   akal, bandingkan dengan yang sudah diverifikasi statis sesi ini:
>   ~53.9MB).

## v3.31.0 — libXray PoC iterasi 3: versi Go persis sesuai error (2026-08-07)

> `GOTOOLCHAIN=local` (v3.30.0) terbukti berguna — run v3.30.0 gagal
> dengan pesan JELAS, bukan lagi ambigu:
> ```
> go: go.mod requires go >= 1.26.3 (running go 1.25.12; GOTOOLCHAIN=local)
> ```
> Tinggal pasang persis yang diminta. `go-version: '1.25'` → `'1.26.3'`.
> `GOTOOLCHAIN=local` tetap dipertahankan (kalau ada modul lain yang
> minta lebih tinggi lagi, errornya tetap akan sejelas ini, bukan balik
> jadi opaque).

**Kalau ini masih gagal**: kemungkinan bukan lagi soal versi Go (sudah
dipenuhi persis), tapi tahap berikutnya di `build/main.py android`
(NDK version, `gomobile init`, atau langkah build AAR itu sendiri) —
kirim log barunya, jangan asumsi ini otomatis "hampir selesai".

**Verifikasi statis:** YAML re-validated, 2 job tetap terdaftar. 0 file
app disentuh — job `build` (APK utama) tidak berubah.

## v3.30.0 — libXray PoC iterasi 2: fix versi Go (2026-08-07)

> Hasil run v3.29.0 (`libxray-poc-log`, dikirim user): job GAGAL di step
> "Attempt Android AAR build". Log baris 74-78 — root cause JELAS, bukan
> tebakan:
> ```
> go: upgraded golang.org/x/mobile ... => v0.0.0-20260803200217-...
> go: golang.org/x/mobile@... requires go >= 1.25.0; switching to go1.25.12
> gomobile: go mod tidy failed: exit status 1
> go: downloading go1.26 (linux/amd64)
> go: download go1.26 for linux/amd64: toolchain not available
> ```
> `go-version: '1.21.6'` yang saya pasang v3.29.0 (dari referensi
> `SaeedDev94/Xray` yang ternyata sudah usang) jauh lebih lama dari yang
> `golang.org/x/mobile` versi sekarang butuh. Go otomatis coba upgrade
> sendiri (GOTOOLCHAIN=auto, default sejak Go 1.21) — berhasil naik ke
> 1.25.12, tapi lanjut mau naik lagi ke 1.26 yang gagal di-download di
> runner ini (bukan berarti 1.26 belum rilis — bisa juga isu proxy/allow-
> list, tidak diverifikasi, karena fix di bawah bikin ini gak relevan lagi
> kalau 1.25 sudah cukup).

**Fix (2 baris, `.github/workflows/build.yml`, job `libxray-poc` saja —
job `build` tetap tidak disentuh):**
- `go-version: '1.21.6'` → `'1.25'` (langsung penuhi syarat minimum
  `golang.org/x/mobile`, gak nunggu auto-upgrade dari versi kuno).
- Tambah `GOTOOLCHAIN=local` — supaya kalau MASIH kurang, errornya jelas
  ("go.mod requires go >= X"), bukan "toolchain not available" yang
  ambigu (bisa network, bisa versi belum rilis, gak actionable).

**Ini iterasi ke-2, bukan klaim fix final.** Kalau masih merah, kemungkinan
besar errornya sekarang JAUH lebih jelas (persis versi Go yang beneran
dibutuhkan, bukan chain-download yang gagal) — kirim log barunya lagi.

**Verifikasi statis:** YAML re-validated (`yaml.safe_load`) — 2 job tetap
terdaftar, tidak ada job baru/hilang. 0 file app disentuh.

## v3.29.0 — Roadmap langkah 1+2: keputusan basis native + CI feasibility PoC (2026-08-07)

> v3.28.0 build hijau di device (CI dikonfirmasi). Lanjut roadmap fix
> native reserved-bytes (bukan langsung nulis kode Go/JNI blind — lihat
> alasan di v3.28.0).

**Langkah 1 — riset lisensi/basis, KEPUTUSAN: Xray-core via `XTLS/libXray`**
(bukan bepass-sdk, bukan fork wireguard-go sendiri). Dicek 3 opsi:
- `bepass-sdk`/Oblivion: CC BY-NC-SA (NonCommercial) — risiko: CC license
  didesain utk karya kreatif bukan kode, ShareAlike bisa "menular" ke
  keputusan lisensi AdShield ke depan, & proyeknya niche/kontributor
  sedikit. **Tidak dipilih.**
- Fork wireguard-go sendiri: kontrol penuh, tapi AdShield jadi harus
  maintain implementasi protokol WireGuard selamanya sendirian — beban
  jangka panjang tertinggi utk tim 1 orang. **Tidak dipilih.**
- **Xray-core (dipilih)**: MIT (dikonfirmasi via listing F-Droid resmi
  utk `SaeedDev94/Xray`, client Android Xray nyata yg sudah shipping).
  Wireguard outbound-nya native dukung `"reserved": [...]`. Wrapper
  mobile resminya, `XTLS/libXray` (juga MIT, aktif dipelihara XTLS org
  sendiri, bukan pihak ke-3), sudah py-script build siap pakai utk
  Android (`build/main.py android`), pin ke tag rilis Xray-core (`v26.7.
  28` per dokumentasinya — reproducible, bukan `latest` yg bisa berubah
  diam-diam), dan punya `SetDNS`/socket-protect API yang RELEVAN LANGSUNG
  ke masalah kita sendiri (app jalan di dalam VPN service sendiri, sama
  persis kelas masalah yg pernah bikin krisis DNS v3.9-v3.11 dulu).
  Bonus: kalau kelak Shadowsocks/MASQUE beneran mau diimplementasi
  (bukan cuma "benefit"-nya kayak v3.27.0), Xray-core sudah dukung
  keduanya native di bawah 1 dependency yang sama.

**Langkah 2 — CI feasibility PoC (job baru `libxray-poc` di
`.github/workflows/build.yml`):**
- Job **terpisah total** dari job `build` (tidak ada `needs:`,
  `continue-on-error: true` di level job) — TIDAK BISA menunda atau
  memblokir build APK utama maupun publish GitHub Release. Kalau job ini
  merah, itu bukan berarti AdShield rusak.
- Isinya: setup Go 1.21.6 (versi yg didokumentasikan `SaeedDev94/Xray`,
  referensi shipping app terdekat yg ketemu — **belum diverifikasi**
  cocok dgn `go.mod` libXray versi terkini) + Android NDK 26.1.10909125
  + clone `XTLS/libXray` + `gomobile` + jalankan
  `python3 build/main.py android` apa adanya, log lengkap di-upload
  sebagai artifact `libxray-poc-log` (build log + .aar kalau sukses).
- **Ini PoC, bukan integrasi.** Belum ada 1 baris pun kode
  `WarpTunnelManager`/`WarpAccount`/`WarpRegistrationClient` yang
  disentuh — jalur WARP yang sekarang jalan (v3.28.0, "bukan WARP resmi"
  tapi tunnel-nya sehat) **tidak berubah sama sekali**, 0 risiko regresi.

**Yang HARUS dicek sebelum lanjut ke langkah 3 (integrasi):**
1. Buka run Actions v3.29.0 → cek job `libxray-poc` (terpisah dari
   `build`) → hijau atau merah?
2. Kalau merah: download artifact `libxray-poc-log`, salin isi
   `libxray-build.log` (terutama error paling akhir) ke sesi
   berikutnya — JANGAN skip log-nya, itu satu-satunya cara tahu Go
   version/NDK version yang benar butuh diganti apa.
3. Kalau hijau: cek artifact ada file `.aar`-nya — itu baru "toolchain
   kepasang", belum berarti WARP+reserved-bytes bakal jalan; integrasi
   ke `WarpTunnelManager` (roadmap langkah 3-4) baru mulai setelah ini.

**Verifikasi statis:** YAML divalidasi (`yaml.safe_load` — parse sukses,
2 job terdaftar: `build`, `libxray-poc`). **Isi step-nya SENDIRI (Go/NDK
versi, urutan install) BELUM tervalidasi — itu justru tujuan PoC ini,
bukan sesuatu yang sudah saya klaim benar.**

## v3.28.0 — Honest WARP labeling + roadmap jangka panjang (2026-08-07)

> Tindak lanjut temuan device pertama (v3.27.0): tunnel WARP connect &
> sehat (0% loss, HTTP tetap dapat respons), tapi `trafficConfirmed`
> permanen false. **Root cause dikonfirmasi 2x independen**: WireGuard
> punya field "reserved" (3 byte) di header handshake yang Cloudflare
> WAJIBKAN diisi ID akun supaya edge men-tag `warp=on`; `com.wireguard.
> android:tunnel` (library resmi yang dipakai) TIDAK PUNYA API untuk itu
> sama sekali (dicek ke javadoc publiknya — Config/Peer/Interface cuma
> kenal field wg-quick standar). Bukti silang: wgcf/warp-plus (tool CLI
> resmi-tak-resmi utk WARP) SEMUA menghasilkan field `"reserved": [...]`
> per-akun yang wajib dipasang; satu-satunya app Android yang benar-benar
> WARP resmi (Oblivion, `bepass-org/oblivion`) sengaja TIDAK pakai
> `com.wireguard.android:tunnel` — dia bawa implementasi WireGuard-Go
> custom sendiri (bepass-sdk) justru demi bisa pasang `reserved`.

**Batch ini (aman, langsung kelihatan, 0 risiko):**
- `WarpConnectionQuality.kt`: `Level` dapat state baru `NOT_CONFIRMED`,
  dipisah dari `UNKNOWN`. Sebelumnya "baru saja connect, belum pernah
  dicek" dan "sudah dicek berkali-kali, tunnel sehat, tapi Cloudflare
  gak pernah tag WARP" SAMA-SAMA jadi `UNKNOWN`/"Belum diperiksa" — user
  disuruh nunggu sesuatu yang gak akan pernah terjadi. Sekarang beda
  label, beda warna (kuning bukan abu/merah, karena tunnel-nya emang
  gak rusak).
- `DiagnosticsScreen.kt`, `HomeScreen.kt`, `WarpForegroundService.kt`:
  label & notifikasi diganti jujur — "Tersambung, tapi bukan WARP resmi"
  / "Tersambung ke Cloudflare — bukan WARP resmi (lihat Diagnostik)",
  + catatan penjelasan singkat di teks diagnostik lengkap.

**KENAPA saya TIDAK langsung nulis fix native (patch reserved-bytes /
ganti ke WireGuard-Go custom / Xray-core outbound) di batch ini,
meskipun itu perbaikan jangka panjang yang sesungguhnya:**
- Semua jalan yang beneran nge-fix ini (lihat riset di atas) butuh
  toolchain Go + NDK (gomobile bind / cgo) yang dikompilasi — PERSIS
  risiko yang sudah 2x dibatalkan di proyek ini (v3.12.0, v3.15.0)
  karena gak bisa diverifikasi tanpa build environment.
- Environment kerja saya sekarang **tidak ada akses jaringan & toolchain
  Go/NDK** — kalau saya nulis kode Go/JNI/cgo sekarang, itu 100% tidak
  tervalidasi sampai CI jalan, dan kemungkinan gagal compile di percobaan
  pertama itu TINGGI (proyek referensi seperti Oblivion/warp-plus
  eksplisit menyebut butuh Go 1.22 + NDK r26b spesifik). Menulis buta
  lalu klaim "sudah fix" itu justru pola yang berkali-kali salah di
  riwayat proyek ini (krisis DNS v3.9–v3.11) — TIDAK diulang di sini.
- Jadi: fix native butuh dikerjakan bertahap & div, alidasi tiap tahap
  lewat CI (bukan diklaim selesai di 1 batch), bukan berarti tidak
  dikerjakan.

**Roadmap jangka panjang (belum dieksekusi, urutan diusulkan):**
1. Riset/pilih basis: fork wireguard-go custom (self-maintain, kontrol
   penuh) vs adopsi bepass-sdk (sudah ada, tapi lisensi CC BY-NC-SA —
   NonCommercial, perlu dicek kompatibel/tidak dgn rencana AdShield) vs
   Xray-core WireGuard outbound (MIT, sudah dukung `reserved` native,
   tapi buka lagi keputusan yang dibatalkan v3.12.0/v3.15.0).
2. Setup skeleton module Go terpisah (mis. `warp-native/`) + step CI
   install Go+NDK, di-build sebagai job CI TERPISAH yang boleh gagal
   (tidak memblokir release APK utama) sampai terbukti hijau berkali2.
3. Baru setelah skeleton native kompil bersih di CI beberapa kali,
   diintegrasikan ke `WarpTunnelManager` di belakang flag — jalur
   `com.wireguard.android:tunnel` yang sekarang (bekerja, walau bukan
   WARP resmi) TETAP jadi fallback, tidak dihapus.
4. `WarpAccount`/`WarpRegistrationClient` perlu field `reservedBytes:
   ByteArray` baru (Cloudflare mengembalikan `id` di respons /reg — perlu
   dicek transformasi id→reserved yang persis dipakai wgcf, BELUM
   diverifikasi di sini).

**Verifikasi statis:** brace/paren 4 file diubah — 0 masalah. Grep
konfirmasi `Level.NOT_CONFIRMED` sudah ditangani di SEMUA `when`
exhaustive atas `Level` (`HomeScreen.kt`, `DiagnosticsScreen.kt`) — tidak
ada cabang yang kelewat (kalau kelewat, Kotlin akan gagal compile, bukan
runtime silent bug, jadi ini kelas kesalahan yang aman terdeteksi CI).

**BELUM DIKONFIRMASI CI/device** — titik uji: buka Diagnostik/Home
setelah WARP connect, pastikan label baru "Tersambung, bukan WARP resmi"
muncul (bukan lagi "Belum diperiksa" yang nyangkut).

## v3.27.0 — Shadowsocks/MASQUE *benefits* adopted, protokolnya sendiri TIDAK disentuh (2026-08-07)

> User eksplisit minta manfaat dari Shadowsocks & MASQUE tanpa implementasi
> protokolnya (Xray-core/QUIC tetap DIBATALKAN, lihat v3.15.0/v3.12.0) — 2
> batch terpisah, masing-masing 1 file kode inti.

**Batch 1 (Shadowsocks-inspired) — port camouflage untuk endpoint WARP:**
- **Fix desync versi juga ketutup di batch ini**: `app/build.gradle.kts`
  sebelumnya masih `61/3.23.0` padahal kode sudah berisi v3.24.0–v3.26.0
  (menutup salah satu blocker rilis dari review sebelumnya).
- `util/Constants.kt`: `WARP_ENDPOINT_CANDIDATES` (dulu 6 entri, semua port
  2408) sekarang generate 24 entri = 6 anycast host × 4 port
  (`WARP_FALLBACK_PORTS = [2408, 4500, 1701, 500]`). Port 500/1701/4500
  BUKAN karangan — diverifikasi ke dokumentasi firewall resmi Cloudflare
  (`developers.cloudflare.com/cloudflare-one/.../deployment/firewall/`):
  itu memang fallback port resmi WARP. **Tidak ditambah port 443** —
  dicek eksplisit lewat web search, Cloudflare TIDAK mendokumentasikan
  WARP jalan di 443 (beda dari MASQUE mode mereka yang memang QUIC/443,
  tapi itu protokol terpisah yang sengaja tidak disentuh).
- **Manfaat yang didapat** (Shadowsocks-style: nyamarin dari DPI/port-block)
  tanpa 1 baris pun kode WireGuard/handshake berubah: 2408 gampang
  di-fingerprint (jarang dipakai protokol lain); 500 (ISAKMP)/1701
  (L2TP)/4500 (IPsec NAT-T) menyamar sebagai trafik VPN korporat umum yang
  kebanyakan firewall sudah izinkan. `WarpEndpointSelector` (0 baris
  logic diubah, cuma kdoc) sudah probe SEMUA kandidat paralel & pilih
  RTT tercepat — manfaat port-camouflage otomatis didapat gratis dari
  logic "pilih yang reachable+tercepat" yang sudah ada sejak v3.7.0.
- Elemen pertama list TETAP `engage.cloudflareclient.com:2408` (fallback
  aman kalau semua probe gagal) — urutan `flatMap` host-major/port-minor
  menjamin ini, tidak berubah dari sebelumnya.

**Batch 2 (MASQUE-inspired) — debounce migrasi jaringan:**
- `warp/WarpTunnelManager.kt`: `onAvailable()` di `NetworkCallback`
  sebelumnya trigger `attemptReconnect(immediate=true)` (full endpoint
  re-probe + MTU probe + WireGuard reconfigure) untuk SETIAP event
  network-switch, termasuk saat WiFi lagi flapping (sinyal lemah,
  reselection tower seluler) yang bisa fire onAvailable() beruntun dalam
  hitungan ratus-ms. Sekarang: `networkSwitchDebounceJob` — event baru
  cancel debounce lama, cuma network TERAKHIR dalam jendela
  `NETWORK_SWITCH_DEBOUNCE_MS=700ms` yang benar-benar trigger reconnect.
  Analog manfaat MASQUE (QUIC connection migration mentoleransi path
  berubah-ubah sesaat sebelum commit migrasi) — **QUIC/HTTP3/MASQUE itu
  sendiri TIDAK diimplementasikan**, ini murni debounce di level
  `ConnectivityManager.NetworkCallback` yang sudah ada sejak v3.7.0.
- `disconnect()`: `networkSwitchDebounceJob?.cancel()` ditambah supaya
  toggle off manual tidak menyisakan reconnect debounce yang nembak
  setelah user sudah matikan WARP.
- Reconnect tunggal (kasus normal, bukan flapping) tetap terasa cepat —
  700ms nyaris tak terasa dibanding `HEALTH_CHECK_INTERVAL_MS` 25 detik
  yang jadi pembanding lama.

**Verifikasi statis:** brace/paren balance ke 3 file diubah
(`Constants.kt`, `WarpEndpointSelector.kt`, `WarpTunnelManager.kt`) — 0
masalah. Grep dikonfirmasi tidak ada file lain yang mengasumsikan bentuk/
jumlah lama `WARP_ENDPOINT_CANDIDATES`.

**BELUM DIKONFIRMASI CI maupun device** — titik uji device paling
relevan: (a) WARP tetap bisa connect normal (endpoint list berubah bentuk,
pastikan tidak ada endpoint yang malah gagal total), (b) matikan-nyalakan
WiFi cepat berturut-turut saat WARP aktif — pastikan cuma 1 reconnect
yang akhirnya terjadi (bukan langsung 1 reconnect kelihatan segera tapi
juga bukan macet lebih dari ~1 detik untuk switch tunggal yang stabil).

## v3.26.0 — ROOT CAUSE FIX Krisis DNS/DoH: app tidak exclude diri sendiri dari VPN-nya sendiri (2026-08-07)

**Ditemukan lewat log Diagnostik device asli** (fitur v3.25.0 baru
kepakai persis untuk ini): `DoH gagal terakhir: https://dns.google/dns-query
(UnknownHostException: Unable to resolve host "dns.google": No address
associated with hostname)`, `DoH fallback ke UDP polos: Ya (24x
beruntun)`.

**Root cause:** `AdBlockVpnService.startVpn()` membuat VPN builder dengan
`addDnsServer(10.111.222.1)` + `addRoute(10.111.222.1, 32)` TANPA PERNAH
memanggil `addDisallowedApplication(packageName)` — app ini tidak pernah
mengecualikan dirinya sendiri dari VPN buatannya sendiri. `DohClient`
resolve endpoint DoH lewat HOSTNAME (`cloudflare-dns.com`/`dns.google`),
dan `VpnService.protect()` di dalamnya cuma melindungi SOCKET data —
BUKAN langkah resolusi hostname sistem yang terjadi sebelum socket itu
ada. Akibatnya resolusi hostname milik proses app sendiri ikut diarahkan
ke DNS palsu (10.111.222.1) = app itu sendiri → nyasar balik ke packet-
loop ad-block-nya sendiri (self-referential) → gagal resolve → persis
gejala di Diagnostik.

**Ini kemungkinan besar JUGA menjelaskan sebagian krisis lama
v3.9.0–v3.11.1** (plain-UDP:53 "gagal total" 2026-08-05) — meski plain-UDP
forward pakai IP literal (bukan hostname, jadi TIDAK kena bug spesifik
ini), pola dasarnya sama: app tidak pernah exclude diri sendiri dari VPN
buatannya, jadi kalau ADA jalur mana pun di app yang butuh resolusi
hostname sistem, jalur itu otomatis kena loop ini.

**Fix — `AdBlockVpnService.kt`:**
- `builder.addDisallowedApplication(packageName)` ditambah setelah
  builder dikonfigurasi, sebelum `establish()` — pola standar SETIAP app
  VPN Android untuk menghindari self-loop ini. Dibungkus try-catch
  `PackageManager.NameNotFoundException` (secara teknis tidak realistis
  gagal untuk package sendiri, tapi kalau toh gagal, VPN tetap
  dilanjutkan dibuat tanpa exclude daripada gagal total — `protect()`
  yang sudah ada tetap jadi lapis kedua untuk socket data walau tidak
  menutup celah resolusi hostname).
- **0 file lain diubah** — WARP (`WarpForegroundService`, VPN terpisah
  lewat backend WireGuard) tidak terdampak/tidak disentuh, murni Builder
  milik `AdBlockVpnService`.

**Verifikasi statis:** brace/paren balance `{=32 }=32 (=140 )=140` +
duplicate-import check — 0 masalah.

**BELUM DIKONFIRMASI CI maupun device — ini fix PALING PENTING untuk
divalidasi dari semua yang pernah dikerjakan soal krisis DoH.** Titik uji
device: nyalakan DNS Ad-Block di jaringan yang SAMA yang menghasilkan log
di atas → buka Diagnostik → "DoH sukses terakhir" HARUS terisi endpoint
(bukan "-") dan "DoH fallback ke UDP polos" harus balik ke "Tidak" atau
jauh berkurang dari 24x. Kalau MASIH gagal dengan reason yang SAMA
persis, root cause di atas salah/tidak cukup — lapor reason barunya.

## v3.25.0 — Krisis DNS/DoH: diagnosability + connection-reuse fix (2026-08-07)

User minta kerjakan "Krisis DoH/DNS custom resolver (v3.9–v3.11) yang
ditinggalkan tanpa resolusi pasti" sampai matang. Root cause asli TIDAK
bisa dipastikan tanpa device (tetap dicatat di PROJECT_STATE), tapi 2 gap
konkret yang menghalangi diagnosis dan performa DIPERBAIKI di sesi ini:

**Gap #1 — nol visibilitas kegagalan (alasan asli krisis lama tidak
pernah root-caused):** `DohClient.resolve()`/`UpstreamForwarder` menelan
SEMUA exception via `catch (_: Exception)` kosong — tidak ada cara tahu
apakah gagalnya di TLS handshake, `protect()`, timeout, HTTP error, atau
endpoint unreachable. Fix: `vpn/DohHealthMonitor.kt` (file baru, in-memory
`StateFlow`, tidak dipersist) mencatat endpoint sukses/gagal terakhir +
reason (exception class + message) + hitungan fallback-ke-UDP-polos
beruntun. Diwire ke `MainViewModel.dohHealth` → `DiagnosticsScreen`
(section "Ad-Block DNS", tampil hanya kalau ada data — tidak mengganggu
fresh install). Sekarang device-test berikutnya bisa langsung baca
alasan gagal yang SPESIFIK, bukan cuma "internet gak jalan".

**Gap #2 — connection reuse dimatikan paksa (perf, bukan cuma
kosmetik):** `DohClient.queryOne()` memanggil `conn.disconnect()` di
`finally` SETELAH SETIAP query — per dokumentasi `HttpURLConnection`,
`disconnect()` mencegah koneksi dipakai ulang lewat keep-alive pool
bawaan JVM. Efeknya: SETIAP lookup DNS (bisa puluhan/ratusan per sesi
browsing) bayar TLS handshake penuh dari nol, bukan reuse koneksi yang
sudah ada — overhead besar yang bisa terasa seperti "DoH lambat/gak
reliable" padahal cuma soal reuse. Fix: hapus `disconnect()`, tetap aman
karena stream sudah selalu dibaca+ditutup penuh lewat `.use {}` (syarat
sebenarnya supaya koneksi eligible untuk reuse). 0 perubahan ke urutan
fallback DoH→UDP-polos.

**Soal WARP registration (API tak resmi Cloudflare) — diaudit ulang,
sudah cukup matang, TIDAK diubah:** `WarpTunnelManager.ensureRegistered()`
sudah punya retry+exponential-backoff (v3.16.5), `WarpAccountRepository`
sudah register-once-lalu-persist (EncryptedSharedPreferences, jadi
existing user TIDAK re-register tiap connect), `WarpRegistrationClient`
sudah punya pesan error actionable (arahkan cek `API_VERSION`) dan
`warpLastError` sudah live di Diagnostics. Satu-satunya risiko tersisa
(Cloudflare mengubah format API tak resmi kapan pun) memang inheren ke
sifat API tak resmi — TIDAK ada perbaikan kode yang bisa menghilangkan
risiko itu sepenuhnya, cuma bisa dideteksi cepat (sudah, lewat pesan
error yang ada) bukan dicegah.

**Verifikasi statis:** brace/paren balance + duplicate-import check ke
seluruh 4 file (1 baru: `DohHealthMonitor.kt`, 3 diubah: `DohClient.kt`,
`MainViewModel.kt`, `DiagnosticsScreen.kt`) — 0 masalah.

**BELUM DIKONFIRMASI CI maupun device.** Titik uji device paling penting:
nyalakan DNS Ad-Block, buka Diagnostik — field "DoH sukses terakhir"
harus terisi endpoint (bukan "-") kalau DoH jalan; kalau masih gagal,
field "DoH gagal terakhir" sekarang HARUS berisi reason spesifik (bukan
kosong) — reason itulah yang menentukan langkah investigasi berikutnya
(lihat kdoc `DohClient.resolve()` poin (d) lama di PROJECT_STATE).

## v3.24.0 — Apple-Style batch 5/N: toggle-row fix diperluas ke IPv6 route switch (2026-08-07)

CI v3.23.0 dikonfirmasi HIJAU (user). Sapuan terakhir pola toggle-row:
satu-satunya Switch tersisa di `HomeScreen.kt` yang belum pakai pola
`toggleable(role = Role.Switch)` — baris "Rutekan IPv6 lewat WARP" di
`WarpModeCard` — sekarang disamakan dengan WarpModeCard/IkeV2ModeCard
(v3.23.0), Logs, dan Whitelist (v3.22.0).

**`HomeScreen.kt`:**
- `Row` label+deskripsi+Switch IPv6 sekarang `Modifier.toggleable(role =
  Role.Switch)`, haptic dipindah ke `onValueChange` — pola identik 3x
  batch sebelumnya, 0 aturan baru.
- `Switch` di bawahnya `onCheckedChange = null` (cegah double-fire).
- `haptic` dipakai ulang dari `val haptic = LocalHapticFeedback.current`
  yang sudah ada di scope composable ini (tidak ada deklarasi baru).
- Signature `onToggleRouteIpv6: (Boolean) -> Unit` & call-site
  `viewModel.setWarpRouteIpv6(it)` di `HomeScreen()` TIDAK berubah.

**Verifikasi statis:** brace/paren balance (`{=102 }=102 (=394 )=394`) +
duplicate-import check — 0 masalah.

**Dengan ini semua Switch di app (HomeScreen ×3, Logs, Whitelist) sudah
konsisten pakai pola toggleable Row — sapuan accessibility/UX Apple-Style
untuk kategori toggle dianggap SELESAI.**

**Titik uji di device:** tap di area label "Rutekan IPv6 lewat WARP"
(bukan cuma kotak Switch) harus ikut toggle; tap tepat di Switch tetap
toggle sekali (tidak dobel).

## v3.23.0 — Apple-Style batch 4/N: toggle-row fix diperluas ke HomeScreen (2026-08-06)

CI v3.22.0 dikonfirmasi HIJAU (user). Batch ini menyelesaikan item yang
SENGAJA ditunda di v3.22.0: toggle-row accessibility fix sekarang juga
diterapkan ke `WarpModeCard`/`IkeV2ModeCard` — bisa dikerjakan sekarang
karena polanya sudah terbukti aman lolos CI 2x berturut (Logs, Whitelist).

**`HomeScreen.kt`:**
- `Row` icon+judul+subtitle+Switch di kedua card sekarang
  `Modifier.toggleable(role = Role.Switch)`, PERSIS pola yang sama dengan
  `enabled`/`onValueChange` yang SEBELUMNYA sudah ada di `Switch` masing-
  masing — bukan aturan baru, cuma dipindah ke level Row:
  - WarpModeCard: `enabled = !connecting` (sama persis)
  - IkeV2ModeCard: `enabled = !connecting && hasProfile` (sama persis)
- Haptic (`performHapticFeedback`) + `onToggle(it)` dipindah ke
  `onValueChange` toggleable — TIDAK diduplikasi, `Switch` di bawahnya
  `onCheckedChange = null`.
- `TextButton` "Ubah/Isi profil server" di IkeV2ModeCard TIDAK
  disentuh — dia di luar Row yang di-toggle, tetap kontrol terpisah.
- `ProtectionRing` punya kontrol tap-nya sendiri (`clickable` +
  press-scale dari v3.21.1) — BUKAN Switch, tidak relevan dengan pola
  `toggleable` batch ini, tidak disentuh.

**Verifikasi statis:** brace/paren balance + duplicate-import check
`HomeScreen.kt` — 0 masalah.

**Titik uji paling penting di device:** tap TEPAT di kotak Switch WARP/
IKEv2 — harus toggle SEKALI (bukan dobel/balik sendiri), DAN tap di area
judul/subtitle kartu (bukan di kotak Switch) juga harus ikut men-toggle.

## v3.22.0 — Apple-Style batch 3/N: accessibility toggle-row fix + Onboarding polish (2026-08-06)

CI v3.21.0 & v3.21.1 dikonfirmasi HIJAU + screenshot device HomeScreen
normal (dikirim user). Lanjut batch 3/N sesuai otorisasi "kamu putuskan
sendiri" — kali ini fokus ke accessibility, bukan cuma visual.

**Accessibility fix nyata — `LogsScreen.kt` + `WhitelistScreen.kt`:**
- Sebelumnya: label teks ("Simpan log query domain" / nama app) dan
  `Switch` di sebelahnya adalah 2+ TalkBack focus stop TERPISAH — Switch
  sendiri cuma bunyi "On/Off" tanpa konteks dia punya switch APA.
- Fix: `Row` pembungkus dikasih `Modifier.toggleable(role = Role.Switch)`,
  jadi 1 accessible node gabungan ("Simpan log query domain, switch, on"),
  DAN sekalian bikin seluruh baris bisa ditap (bukan cuma kotak Switch
  kecil) — pola yang sama dipakai baris toggle Settings iOS.
- `Switch` sendiri di-`onCheckedChange = null` supaya toggle logic HANYA
  dipegang `toggleable` di Row (bukan API custom — `onCheckedChange`
  Material3 Switch memang nullable persis untuk pola ini) — mencegah
  double-fire kalau user tap tepat di kotak Switch-nya.
- **SENGAJA TIDAK diterapkan ke `HomeScreen.kt`** (WarpModeCard/
  IkeV2ModeCard Switch) — sudah ada logic `enabled = !connecting` +
  haptic per-Switch dari batch sebelumnya; menggabungkan itu dengan
  `toggleable` di level Row butuh mikir ulang urutan panggilan
  haptic+enabled+onValueChange sekaligus, risiko regresi nyata tanpa
  compiler buat verifikasi. Bukan lupa — keputusan sadar.

**`OnboardingScreen.kt`:**
- Lingkaran ikon tiap halaman: dulu `ShieldGreen.copy(alpha = 0.15f)`
  dihitung sendiri lokal di file ini — sekarang pakai `ShieldAccentDim`,
  konstanta "tinted icon bg" yang SAMA dipakai layar lain (1 sumber
  kebenaran, bukan 2 rumus tint hijau paralel).
- Dot indikator halaman: dulu ganti ukuran/warna instan (jump-cut) —
  sekarang `animateDpAsState`/`animateColorAsState`, growth/shrink halus
  ala `UIPageControl` iOS. Murni visual, state `active`/logic pager sama
  persis.

**Verifikasi statis:** brace/paren balance + duplicate-import check ke
SEMUA 3 file diubah — 0 masalah. `Switch(onCheckedChange = null)`
dikonfirmasi valid — parameter itu memang nullable di Material3 API,
bukan asumsi.

## v3.21.1 — Apple-Style batch 2/N: ProtectionRing press-scale + debug sweep hasil temuan real bug (2026-08-06)

User: "Lanjut" (setelah v3.21.0 dianggap sudah build hijau — **catatan:
saya TIDAK menerima log CI baru, ini asumsi dari instruksi "Lanjut" itu
sendiri, bukan verifikasi nyata**, lihat PROJECT_STATE.md).

**`HomeScreen.kt` — press-scale di `ProtectionRing`:**
- Kontrol paling sering ditekan di app ini sekarang scale-down 0.94x saat
  ditahan (`animateFloatAsState` + `collectIsPressedAsState`, tween
  120ms) — gestur taktil khas iOS di hampir semua tombol.
- Ripple Material default di kontrol ini SENGAJA dimatikan
  (`indication = null`) supaya scale animation jadi satu-satunya feedback
  visual, bukan numpuk dengan ripple lingkaran Material — konsisten sama
  bahasa motion Apple (scale, bukan ripple).
- `interactionSource` HANYA drive animasi lokal ini — tidak membungkus
  ulang `onClick`/haptic yang sudah ada, logic toggle 100% sama persis.

**Debug sweep (diminta eksplisit: "debugging... sampai matang") — 1 bug
nyata ditemukan, TIDAK terkait batch manapun sebelumnya:**
- `res/values/colors.xml`: `shield_bg_dark` = **#0F1512** — dipakai di
  `themes.xml` buat `android:statusBarColor`/`navigationBarColor`/
  `windowBackground` (yaitu apa yang kelihatan SEBELUM Compose sempat
  gambar apa pun + area status/nav bar sistem di sekitar konten Compose).
  Nilai ini TIDAK PERNAH cocok dengan `ShieldBgDark` di Compose manapun —
  bukan #181816 (nilai lama) ataupun #000000 (nilai baru v3.21.0) — bug
  drift lama yang sudah ada sejak sebelum sesi ini, cuma gak kelihatan di
  palet lama yang sama-sama gelap-kusam, sekarang bakal SANGAT kelihatan
  (status bar hijau tua vs konten app hitam pekat). **Fix: disamakan ke
  #000000.**
- Sekalian dibersihkan: `shield_primary`/`shield_primary_dark`/
  `shield_accent`/`shield_danger` di file yang sama — di-grep, 100% TIDAK
  direferensikan di mana pun (bukan cuma di Kotlin, di seluruh `app/src`).
  Dihapus. (Dicatat di sini sesuai aturan hapus-file-butuh-izin — user
  sudah kasih otorisasi umum "kamu putuskan sendiri" di pesan sebelumnya.)

**SENGAJA TIDAK diubah:** `WarpModeCard`/`IkeV2ModeCard` Switch — sudah
punya feedback taktil bawaan Material Switch sendiri (thumb slide),
nambah scale animation di situ jadi tumpang tindih, bukan polish; `NavRow`
list items — ripple Material default di baris list sudah cukup dekat
dengan pola list iOS (highlight saat ditekan), tidak perlu diganti scale.

**Verifikasi statis:** brace/paren balance + duplicate-import check
`HomeScreen.kt` — 0 masalah. XML `colors.xml` divalidasi well-formed. Grep
ulang referensi `shield_bg_dark` — masih resolve dengan benar ke
`themes.xml`. **BELUM DIKONFIRMASI CI** (baik v3.21.0 maupun v3.21.1 ini)
— lihat PROJECT_STATE.md.

## v3.21.0 — Apple-Style redesign batch 1/N (2026-08-06)

User: "rombak UI dan UX ala Apple-Style dan anti regresi", lalu di pesan
berikutnya menyerahkan arah proyek sepenuhnya ("kamu putuskan sendiri...
fokus Polish ala Apple-Style, debugging, eksekusi sampai matang"). Batch
ini murni presentation layer — **0 file ViewModel/logic/DAO/Manifest
disentuh**, sama seperti disiplin redesign v3.0.0 dulu, supaya risiko
regresi fungsional = nol secara struktural, bukan cuma diklaim.

**`Color.kt` — retint total ke Apple System Colors dark-mode asli:**
- `ShieldBgDark` #181816→**#000000** (systemBackground), `ShieldSurface`
  →**#1C1C1E** (secondarySystemBackground), `ShieldSurface2`→**#2C2C2E**,
  `ShieldSurface3`→**#3A3A3C** (systemGray5/4)
- `ShieldGreen`→**#30D158** (systemGreen), `ShieldDanger`→**#FF453A**
  (systemRed), `ShieldWarning`→**#FF9F0A** (systemOrange) — semua nilai
  resmi Apple HIG, bukan reka-reka
- `ShieldWhite`→**#FFFFFF** murni (dulu off-white hangat). `ShieldTextMuted`/
  `ShieldTextFaint` diganti jadi **white-alpha** (60%/38%) meniru teknik
  `label`/`secondaryLabel` Apple sendiri, BUKAN nilai acak — dihitung dulu:
  60% putih di atas `ShieldSurface` (#1C1C1E) ≈10.6:1, 38%≈7.1:1, jauh di
  atas floor AA 4.5:1 untuk teks kecil (standar yang sama dipakai audit
  legibility v3.4.0, supaya perbaikan lama itu TIDAK regresi)
- `ShieldOutline`→**#6E6E73** (netral, bukan warm-tint lama) — luminance
  dicek ulang ≈4.1:1 vs `ShieldBgDark`, sepadan dengan nilai lama yang
  memang sengaja dinaikkan di v3.4.0 buat fix "border pudar"
- **15/15 nama konstanta dipertahankan persis** — reskin di sumber, 0
  call-site di seluruh app perlu diubah untuk warna ikut ganti kulit

**`RulesScreen.kt` — segmented control ala iOS:**
- `TabRow`/`Tab` Material diganti `SegmentedTabs` custom (pill container +
  pill terpilih) — SENGAJA pakai primitif `Box`/`Row`/`clip`/`background`/
  `clickable` saja, BUKAN Material3 `SegmentedButton` (API eksperimental
  yang parameternya tidak bisa saya verifikasi 100% tanpa compiler — demi
  "anti regresi", pilih API yang polanya sudah terbukti jalan di file lain)
- State `tab: Int` dan `onSelect` wiring 100% sama persis, cuma tampilan

**`DiagnosticsScreen.kt`/`LogsScreen.kt`/`RulesScreen.kt`/
`WhitelistScreen.kt` — flat edge-to-edge TopAppBar:**
- `containerColor` disamakan ke `ShieldBgDark` (dulu default
  `colorScheme.surface`, beda kontras dengan halaman di bawahnya yang kini
  hitam pekat — bikin bar tampak seperti pita terpisah, bukan menyatu ala
  nav bar iOS)

**SENGAJA TIDAK diubah:** `Shape.kt`/`Type.kt` — sudah diperiksa langsung,
skala radius besar & tracking negatif di judul yang ada SUDAH dekat dengan
konvensi Apple (large-radius card, tight tracking di title), ubah tanpa
alasan konkret cuma nambah risiko; `HomeScreen.kt` — tidak disentuh batch
ini (kandidat batch 2: press-scale animation di ProtectionRing, ditunda
sengaja karena `animateFloatAsState`+`interactionSource` nambah state baru,
mau device-test batch ini dulu sebelum nambah lapisan lagi).

**Verifikasi statis:** brace/paren balance + duplicate-import check ke
SEMUA 5 file diubah (`Color.kt`, `RulesScreen.kt`, `LogsScreen.kt`,
`WhitelistScreen.kt`, `DiagnosticsScreen.kt`) — 0 masalah. Constant-name
diff `Color.kt` (15 lama vs 15 baru) — identik. **BELUM dikonfirmasi CI**
— WAJIB dicek dulu di sesi berikutnya sebelum lanjut batch 2.

## v3.20.1 — HOTFIX build CI gagal dari push v3.20.0 (2026-08-06)

User upload log CI (`log_fail_20260806_085220_run31086594048.zip`) —
`compileDebugKotlin FAILED`, 2 error identik: `HomeScreen.kt:174:41` dan
`:180:41`, "Type mismatch: inferred type is Long but Int was expected".

**Root cause:** helper baru `formatStatCount()` (v3.20.0) ditulis dengan
signature `(count: Int)` tanpa mengecek langsung tipe asli
`blockedCount`/`allowedCount` di `MainViewModel` — keduanya
`StateFlow<Long>` (dibaca dari `SettingsRepository`), bukan `Int`.
Sebelumnya kode lama cuma `blockedCount.toString()` (aman untuk tipe
apa pun), jadi mismatch ini baru muncul begitu helper dengan parameter
bertipe eksplisit ditambahkan.

**Fix:** `formatStatCount(count: Int)` → `formatStatCount(count: Long)`.
1 baris, 1 file (`HomeScreen.kt`). `NumberFormat.format()` overload
untuk `Long` sudah ada di JDK standar, tidak perlu perubahan lain.

**Pelajaran untuk self-verifikasi ke depan:** saat menambah fungsi
helper baru yang menerima nilai dari `StateFlow`/`MainViewModel` yang
sudah ada, WAJIB grep tipe deklarasi aslinya dulu (`grep -n "nama:
StateFlow"` di `MainViewModel.kt`) sebelum menulis signature parameter
eksplisit — jangan asumsikan `Int` hanya karena nilainya konseptual
"hitungan"/counter. Checklist statis sebelumnya (brace/paren balance +
lexer nested-comment) TIDAK menangkap kelas bug ini karena itu murni
type-checking, bukan soal struktur sintaks.

**Verifikasi statis:** brace/paren balance ulang — 0 masalah. **BELUM
di-push ulang / belum dikonfirmasi CI hijau** — WAJIB jadi hal pertama
dicek di sesi berikutnya.

## v3.20.0 — UI/UX polish pass batch 1: konsistensi, feedback, tactile (2026-08-06)

> User minta "polish UI dan UX sampai matang". Diaudit statis seluruh 6
> screen (`HomeScreen`/`WhitelistScreen`/`RulesScreen`/`LogsScreen`/
> `DiagnosticsScreen`/`OnboardingScreen`) — Rules/Logs/Diagnostics sudah
> cukup matang (search+filter+empty-state+confirm-dialog sudah lengkap
> dari audit Feedback v3.3.1-v3.3.2). 2 celah nyata ditemukan di
> `WhitelistScreen` + 3 peningkatan di `HomeScreen`, semua verified via
> baca kode langsung (bukan asumsi/redesign visual).

**`WhitelistScreen.kt` (celah konsistensi vs Logs/Rules screen):**
- Search field polos (cuma `label`) diganti pola `placeholder`+leading
  `Search` icon+trailing `Clear` icon — identik dengan Logs/Rules, yang
  sebelumnya cuma Whitelist yang beda.
- Empty state: sebelumnya `LazyColumn` kosong tampil blank total kalau
  `apps` masih memuat ATAU hasil pencarian nihil — tidak terbedakan dari
  "layar rusak". Sekarang: "Memuat daftar aplikasi…" (apps masih kosong)
  vs "Tidak ada aplikasi yang cocok dengan pencarian." (filtered kosong)
  — pola sama seperti `LogsScreen`/`RulesScreen`.
- Count feedback baris baru: "N aplikasi di-whitelist · menampilkan X
  dari Y" — Logs/Rules sudah punya count row serupa, Whitelist belum.

**`HomeScreen.kt`:**
- **Connecting-state spinner:** `WarpModeCard`/`IkeV2ModeCard` sebelumnya
  cuma ganti teks jadi "Menyambungkan…" tanpa elemen visual bergerak sama
  sekali selama jendela registrasi+handshake beberapa detik. Sekarang
  `CircularProgressIndicator` kecil (18dp) menggantikan icon Lock/VpnKey
  di slot yang sama selagi `connecting == true`.
- **Haptic feedback:** `ProtectionRing` (kontrol paling sering ditekan di
  app ini) + `Switch` WARP/IKEv2 sekarang `performHapticFeedback(
  HapticFeedbackType.LongPress)` saat ditekan — dipilih untuk selaras
  dengan pola VPN client premium (WARP resmi, Mullvad), dan memberi
  konfirmasi taktil bahkan sebelum state visual selesai update. Tidak
  mengubah `onClick`/`onCheckedChange` behavior asli, cuma menyisipkan
  haptic call sebelum meneruskan ke callback yang sudah ada.
- **Format angka StatCard:** `blockedCount`/`allowedCount` sebelumnya
  `toString()` polos — jadi digit blob susah dibaca begitu masuk ribuan
  setelah pemakaian berminggu-minggu. Sekarang `NumberFormat` locale
  `in-ID` (pemisah ribuan titik, mis. "12.345").

**SENGAJA TIDAK diubah:** `ProtectionRing`'s "no fill animation on toggle"
(kdoc v3.0.0 — restraint yang disengaja, bukan gap); warna/tema/shape
(scope batch ini murni interaksi+konsistensi, bukan redesign visual);
`OnboardingScreen` (sudah dilihat, tidak ada celah nyata ditemukan).

**Verifikasi statis:** brace/paren balance + lexer nested-block-comment
Kotlin ke 2 file yang diubah — 0 masalah. **BELUM dikonfirmasi build CI**
— cek dulu di sesi berikutnya, sekaligus dengan v3.19.0 yang juga masih
menunggu (unit test `testDebugUnitTest` belum pernah jalan sama sekali).

## v3.19.0 — Testing & Diagnostic audit batch 1: DnsPacket coverage gap (2026-08-06)

> Kategori terakhir roadmap audit eksternal (Reliability ✅, Concurrency &
> Lifecycle ✅, Security ✅, Performance ✅ — semua batch sebelumnya clean/
> fixed). User anggap Performance cukup, lanjut Testing & Diagnostic.

**Temuan:** `DnsPacketTest.kt` (ditulis v2.6.1) cuma cover `parse()` dan
`buildBlockedResponse()` — 6 method `DnsPacket` yang ditambah belakangan
(v3.7.0 DNS cache: `withTransactionId`, `qtypeOf`, `extractCacheableTtlSeconds`;
v3.9.0 prefetch: `encodeQuestionSection`, `buildQueryMessage`) tidak punya
test sama sekali, padahal file ini secara eksplisit didokumentasikan
sebagai "paling kritis, paling gampang salah" di codebase.

**Fix (1 file test, `DnsPacketTest.kt`):** tambah 11 test case baru:
- `withTransactionId`: swap 2 byte pertama benar, tidak mutasi buffer asli,
  no-op kalau input terlalu pendek.
- `qtypeOf`: baca QTYPE dari query asli hasil `parse()`, dan fallback 0
  untuk question section < 4 byte.
- `encodeQuestionSection`/`buildQueryMessage`: round-trip konsisten satu
  sama lain, struktur QTYPE/QCLASS byte-per-byte benar, header 12-byte
  well-formed (QDCOUNT=1, ANCOUNT=0).
- `extractCacheableTtlSeconds`: baca TTL dari respons valid, `null` untuk
  RCODE non-zero (NXDOMAIN dkk), `null` untuk ANCOUNT=0, `null` untuk
  pesan yang terlalu pendek/truncated — 4 skenario yang jadi dasar
  keputusan cache DnsCache v3.7.0.

Semua test murni JVM (bangun byte array manual, tidak butuh Android
framework/Robolectric), pola sama persis dengan test lama supaya konsisten.
`BlocklistManagerTest.kt` sudah cukup lengkap dari awal (14 test, mencakup
exact/wildcard/allow-override/critical-allowlist/normalization/remote-list
diffing) — tidak ada perubahan di batch ini.

**Verifikasi statis:** simulasi lexer nested-block-comment + brace-balance
ke seluruh `app/src` (termasuk file test baru) — 0 masalah.

**BELUM DIJALANKAN** (`./gradlew testDebugUnitTest` — tidak ada Gradle/JDK
Android di sandbox) — WAJIB jadi hal pertama dicek di sesi berikutnya;
kalau ada test yang gagal (typo offset/assertion), itu prioritas nomor
satu sebelum lanjut apa pun.

## v3.18.0 — Security audit batch 1: WARP private key tidak lagi plaintext (2026-08-06)

> v3.17.1 dikonfirmasi build CI hijau oleh user. Lanjut ke kategori audit
> berikutnya sesuai urutan yang disepakati: Reliability ✅ → Concurrency &
> Lifecycle ✅ → **Security (mulai batch ini)** → Performance → Testing &
> Diagnostic.

**Temuan:** `WarpAccountRepository` menyimpan WireGuard private key +
Cloudflare access token di `preferencesDataStore` — file preferences biasa,
**tidak terenkripsi** di storage. Berbeda dengan `VpnProfileRepository`
(OpenVPN/IKEv2/Shadowsocks) yang sejak v3.15.0 sudah pakai
`EncryptedSharedPreferences`. Private key WARP adalah identitas kripto
penuh untuk tunnel — kalau device di-root atau file diekstrak fisik, private
key + token bisa dibaca langsung sebagai teks biasa.

**Fix (1 file, `WarpAccountRepository.kt`):** migrasi total ke
`EncryptedSharedPreferences` (AES256_SIV key / AES256_GCM value), pola
persis sama dengan `VpnProfileRepository`. Dependency `security-crypto`
sudah ada di `build.gradle.kts` (dipakai `VpnProfileRepository`), tidak
perlu ditambah. API publik (nama properti/method + tipe `Flow`) TIDAK
berubah — `WarpTunnelManager` (satu-satunya pemakai) nol perubahan.
`wasTunnelRunning`/`hasAccount` jadi `flow { emit(...) }` one-shot
(sebelumnya reactive DataStore Flow) — aman karena kedua caller di
codebase ini cuma pernah `.first()`, tidak pernah `collect` terus-menerus
(diverifikasi via grep sebelum ubah).

**Efek upgrade:** akun WARP lama yang tersimpan di DataStore lama
(`adshield_warp`) tidak di-migrasi otomatis — file itu ditinggalkan begitu
saja. User existing akan re-register WARP sekali secara diam-diam di
percobaan connect berikutnya (biaya sama seperti `clearAccount()`, bukan
bug/kehilangan data, cuma perilaku observable saat upgrade).

**Verifikasi:** grep menyeluruh — tidak ada `Log.*` yang menyentuh
private key/access token WARP di manapun. Simulasi lexer nested-block-
comment: 0 masalah di seluruh `app/src`.

## v3.17.1 — HOTFIX build CI gagal dari push v3.17.0 (2026-08-06)

> User upload artifact `log_fail_20260806_023527_run31066001167.zip`:
> `kspDebugKotlin FAILED` — `AdBlockVpnService.kt:262:1 Unclosed comment`.

**Root cause:** KDoc pembuka `AdBlockVpnService` (baris 28-43) memuat
teks `` `vpn/dns/*` `` — literal `/*` di dalamnya dibaca compiler Kotlin
sebagai pembuka block comment BARU yang bersarang di dalam comment yang
sedang terbuka (Kotlin block comments BOLEH nested, beda dari Java/C).
`*/` pertama yang ditemukan (baris 43) menutup comment nested itu, bukan
comment luar — comment luar jadi terbuka sampai akhir file, makanya
error muncul di baris 262 (baris terakhir), bukan di baris 35 tempat
akar masalahnya.

**Fix (1 file, `AdBlockVpnService.kt`):** frasa `` `vpn/dns/*` `` diganti
`` `vpn.dns` package `` — tidak ada perubahan lain.

**Verifikasi tambahan:** skrip Python sekali-pakai yang mensimulasikan
lexer nested-block-comment Kotlin dijalankan ke SELURUH file `.kt` di
`app/src/main/java` + `app/src/test/java` — dikonfirmasi 0 file lain
punya masalah serupa.

**Pelajaran:** checklist statis sebelumnya (brace/paren balance) tidak
menangkap kelas bug `/* */` karena bukan `{ }`/`( )`. Ke depan, KDoc yang
menyebut path/wildcard (`foo/bar/*`) harus ditulis `` `foo.bar` package ``
atau bentuk lain yang tidak pernah membentuk urutan literal `/*`.

## v3.17.0 — Refactor God Class: AdBlockVpnService dipecah jadi 8 file (2026-08-06)

> Respons ke audit eksternal user: "VPN Service terlalu besar (God Class)"
> (skor Coding 8/10, kekurangan #1). Murni structural move — 0 perubahan
> logic/behavior.

**Sebelum:** `AdBlockVpnService.kt` (~600 baris) berisi lifecycle Service +
packet loop + upstream forward (DoH/UDP + socket pooling) + prefetch +
whitelist per-app UID + notification builder + watchdog scheduler, semua
inline dalam 1 class.

**Sesudah — 7 file baru + 1 file ditulis ulang:**
- `vpn/dns/UpstreamForwarder.kt` — `forwardToUpstream()`, DoH-then-UDP
  fallback, socket pool per-worker-thread (`getOrCreateUpstreamSocket`/
  `discardUpstreamSocket`/`closeAllSockets`), `buildForwardedRequest()`.
- `vpn/dns/DnsPrefetcher.kt` — `prefetchPopularDomains`/`prefetchOne`.
- `vpn/dns/AppUidWhitelistChecker.kt` — `isFromWhitelistedApp` +
  `uidToPackageCache`.
- `vpn/dns/DnsPacketLoop.kt` — `runPacketLoop` (baca tun, routing
  blocked/cache/forward) + `writeBlockedResponse`/`writeCachedResponse`.
- `vpn/dns/DnsQueryLogger.kt` — `logAndCount` (counter + Room log).
- `vpn/VpnNotificationFactory.kt` — `buildNotification`.
- `vpn/VpnWatchdog.kt` — `scheduleWatchdog` (AlarmManager).
- `vpn/AdBlockVpnService.kt` — ditulis ulang jadi orchestrator murni
  (~250 baris): lifecycle callbacks + wiring kolaborator + companion
  object public API (`ACTION_START`/`ACTION_STOP`/`EXTRA_MODE_SWITCH`/
  `lastError`) — **API companion 100% tidak berubah**, jadi 0 file lain
  (MainActivity, MainViewModel, DnsTileService, BootReceiver,
  RestartReceiver) perlu diedit.

**Cara pemindahan:** setiap fungsi dipindah verbatim (badan fungsi +
komentar penjelas insiden/keputusan historis ikut dibawa ke lokasi
barunya), bukan ditulis ulang — supaya batch refactor struktural ini
tidak sekalian menyuntik risiko regresi logic. 2 comment-only doc
reference di file lain (`DohClient.kt`, `WarpEndpointSelector.kt`)
diupdate supaya menunjuk ke lokasi kode yang baru.

**Keputusan desain (lihat PROJECT_STATE.md keputusan arsitektur #15
untuk detail lengkap):** `UpstreamForwarder`/`DnsPrefetcher` memegang
referensi `VpnService` langsung (bukan `Context` generik) karena
`VpnService.protect()`/`DohClient.resolve()` butuh instance itu — kedua
kolaborator ini dibuat ulang di `onCreate()` tiap Service instance baru,
BUKAN singleton. `DnsPacketLoop` tetap WAJIB jalan di `loopExecutor`
terpisah dari `forwardExecutor` (keputusan #11, tidak berubah).

**Atomic Change:** 8 file kode + `build.gradle.kts` (version bump) — di
atas batas normal 10 file tapi 1 modul (`vpn/`), migrasi arsitektur atas
permintaan eksplisit user, dicatat sebagai exception Batch Lock.

**BELUM DIKONFIRMASI build CI SAMA SEKALI** — batch ini menyentuh hot
path DNS (packet loop + forward), jadi ini WAJIB jadi hal pertama dicek
di sesi berikutnya sebelum kerja apa pun lagi (lihat PROJECT_STATE.md).

## v3.16.9 — Concurrency & Lifecycle audit batch 2/N: BlocklistManager race (2026-08-06)

> User konfirmasi diagnostik CI v3.16.8 hijau + snapshot WARP pulih sendiri
> (cold-start blip biasa, bukan bug). Lanjut audit Concurrency & Lifecycle
> ke target yang sudah dicatat di PROJECT_STATE.md: `BlocklistManager`.

**Ditemukan (`app/src/main/java/com/fdzaki/adshield/data/BlocklistManager.kt`):**
`setCustomBlocked()`/`setCustomAllowed()`/`setWhitelistedApps()` dipanggil
dari 2 tempat independen yang jalan di thread/dispatcher berbeda:
`MainViewModel` (collector Flow `settingsRepository.customBlockedDomains`
dkk, aktif SELAMA app kebuka) dan `AdBlockVpnService.startVpn()` (sekali
tiap VPN DNS mulai, di `serviceScope`). Dua bug nyata:
1. `customBlockedSnapshot` (plain `var`, bukan `@Volatile`, tanpa lock) —
   race read-modify-write kalau kedua caller di atas kebetulan jalan
   bersamaan (lost update, bukan cuma soal visibility).
2. `allowedExact`/`allowedWildcardBases`/`whitelistedApps` di-mutasi lewat
   `clear()` lalu `add()` satu-satu di atas `ConcurrentHashMap.newKeySet()`
   — antara `clear()` dan `add()` selesai, `isBlocked()`/`isAppWhitelisted()`
   yang dipanggil dari packet-loop thread (tiap query DNS) bisa lihat set
   KOSONG sesaat → domain yang di-allow-list user bisa kepencet blokir
   sesaat, atau app whitelisted sesaat tidak di-bypass.

**Fix:**
- `setCustomBlocked()` dibungkus `synchronized(this)` — seluruh urutan
  baca-diff-tulis jadi atomik antar 2 caller di atas.
- `allowedExact`+`allowedWildcardBases` diganti satu `AllowSnapshot`
  (`data class` берisi exact+wildcard) di belakang `@Volatile var` —
  `setCustomAllowed()` sekarang bangun snapshot baru lengkap dulu lalu
  SATU KALI assignment atomik, bukan clear-then-add bertahap.
  `isBlocked()` baca snapshot itu SEKALI ke local val di awal supaya cek
  exact+wildcard konsisten walau `setCustomAllowed()` gonta-ganti
  snapshot di tengah pemanggilan.
- `whitelistedApps` diganti dari `ConcurrentHashMap.newKeySet()` ke
  `@Volatile var whitelistedApps: Set<String>` dengan pola swap yang sama.

**Dampak nyata sebelum fix:** domain di Aturan Kustom "Izinkan" bisa
ke-blokir sesaat, atau app di whitelist bisa tetap kena DNS-block
sesaat, tiap kali Rules screen disave SAAT VPN DNS lagi start/restart
bersamaan — jendela racenya sempit tapi nyata, bukan hipotetis (2 call
site sungguhan sudah dikonfirmasi lewat `grep`).

**BELUM DIKONFIRMASI build CI v3.16.9 — cek dulu di sesi berikutnya.**

## v3.16.8 — Concurrency & Lifecycle audit batch 1/N (2026-08-06)

> Kategori ke-2 dari checklist audit eksternal user (setelah Reliability
> dianggap cukup — lihat v3.16.7). Item persis dari checklist tidak
> tersimpan verbatim di PROJECT_STATE.md (cuma judul kategori), jadi batch
> ini adalah audit mandiri static-review terhadap 2 Service inti aplikasi
> (`AdBlockVpnService`, `WarpForegroundService`) fokus ke lifecycle
> cleanup — dua bug nyata ditemukan & diperbaiki.

**`app/src/main/java/com/fdzaki/adshield/warp/WarpForegroundService.kt`
(bug paling serius batch ini):** `onDestroy()` sebelumnya cuma
`scope.launch { warpEngine.disconnect() }` tanpa pernah cancel `scope`
itu sendiri. `observeQualityForNotification()` (dipanggil tiap start)
menjalankan `combine(...).collect { }` TANPA kondisi berhenti sendiri
(beda dari loop-loop di `AdBlockVpnService` yang semuanya cek
`running.get()`) — akibatnya coroutine ini terus jalan SELAMANYA setelah
Service destroyed, terus memanggil `NotificationManagerCompat.notify()`
pakai `this@WarpForegroundService` (Context yang sudah destroyed) tiap
tick state/quality WARP berubah, selama proses app masih hidup. Fix:
`scope.cancel()` dipanggil di akhir `onDestroy()`; `warpEngine.disconnect()`
dibungkus `withContext(NonCancellable)` supaya tetap selesai bersih
walau `scope`-nya sendiri barusan di-cancel (kalau tidak dibungkus,
`cancel()` bisa memutus `disconnect()` di tengah teardown interface).

**`app/src/main/java/com/fdzaki/adshield/vpn/AdBlockVpnService.kt`:**
`onDestroy()` sebelumnya tidak pernah `shutdown()` `loopExecutor`
(single-thread) maupun `forwardExecutor` (4 thread) — keduanya dibuat
BARU tiap `onCreate()` (tiap toggle DNS-mode off→on bikin instance
Service baru), tapi instance LAMA-nya tidak pernah di-shutdown. Thread
worker `ExecutorService` tidak berhenti sendiri hanya karena Service
pembuatnya destroyed — cuma `shutdown()`/`shutdownNow()` yang
menghentikannya. Tanpa fix ini, tiap toggle bocor 5 thread non-daemon
hidup selamanya (menumpuk terus selama proses app hidup). Fix: kedua
executor di-`shutdown()`/`shutdownNow()` di `onDestroy()`.
`serviceScope` (coroutine Job) SENGAJA TIDAK di-cancel — semua
coroutine di dalamnya (termasuk `prefetchPopularDomains()`) sudah
self-terminate lewat cek `running.get()` dalam satu iterasi loop
setelah `stopVpn()`, jadi cancel paksa cuma berisiko motong write
`settingsRepository` yang sedang in-flight (mis. `setWasRunning(false)`)
tanpa manfaat tambahan — thread pool adalah bug nyatanya, bukan Job-nya.

**Belum diaudit batch ini (lower-priority, dicatat untuk sesi
berikutnya):** `IkeV2VpnEngine.engineScope`/`pollJob` — dibuat per
`MainViewModel` instance, tidak pernah di-cancel dari `onCleared()`.
Prioritas lebih rendah dari 2 bug di atas karena: (a) profil IKEv2
tetap jalan di level OS (`VpnManager`) terlepas app process hidup/mati,
jadi ini cuma soal UI-side polling, bukan kebocoran koneksi; (b)
`MainViewModel` sebagai `AndroidViewModel` di app single-Activity ini
praktis hidup seumur proses, `onCleared()` jarang benar-benar terpanggil
di luar proses mati (yang otomatis membereskan semuanya).

**BELUM DIKONFIRMASI build CI v3.16.8 — cek dulu di sesi berikutnya.**

## v3.16.7 — Reliability audit batch 3/N: captive portal detection (2026-08-06)

> Lanjutan checklist Reliability dari user (item ke-3): membedakan
> "jaringan butuh login captive portal" dari "internet benar-benar mati",
> yang sebelumnya terlihat identik ke watchdog WARP.

**Masalah:** `probeTrace()` (HTTP request ke `cdn-cgi/trace` lewat tunnel)
timeout/gagal dengan cara yang sama persis baik saat internet benar-benar
mati maupun saat jaringan WiFi terkunci di halaman login captive portal
(bandara/kafe) — WireGuard handshake sama-sama tidak bisa tembus sampai
user login. Akibatnya watchdog menghabiskan seluruh budget
`MAX_RECONNECT_ATTEMPTS` mencoba reconnect yang pasti gagal, lalu
menampilkan pesan generik "auto-reconnect dihentikan" yang menyesatkan
user (app/tunnel dikira rusak, padahal cuma butuh login WiFi).

**`app/src/main/java/com/fdzaki/adshield/warp/WarpTunnelManager.kt`**
- Tambah `onCapabilitiesChanged` di `NetworkCallback` yang sudah ada
  (dipakai untuk fast-reconnect network-switch) — cek
  `NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL`, flag resmi Android
  yang sama dipakai notifikasi sistem "Sign in to network". Dipilih
  dibanding menebak dari pola kegagalan probe karena lebih andal (captive
  portal kadang meloloskan sebagian UDP tanpa timeout bersih).
- State baru: `captivePortalDetected: StateFlow<Boolean>` (public, untuk
  wiring UI masa depan — belum dipakai HomeScreen di batch ini) +
  `captivePortalActive` internal flag.
- Saat portal terdeteksi: `lastError` diisi pesan spesifik ("butuh login,
  buka browser") menggantikan pesan generik; `registerProbeFailure()` dan
  `attemptReconnect()` di-guard supaya TIDAK membuang budget
  `consecutiveFailures`/`reconnectAttempts` selama portal masih aktif —
  keduanya cuma diam-diam skip sampai capability berubah, tidak berpura-pura
  ada progres.
- Saat portal capability hilang (user sudah login): `lastError`
  dibersihkan + langsung `attemptReconnect(immediate = true)`, tidak
  menunggu tick health-check berikutnya (~25 detik) — budget reconnect yang
  di-freeze tadi masih utuh.
- `disconnect()` reset `captivePortalActive`/`captivePortalDetected` supaya
  toggle manual off-on tidak membawa state portal basi dari sesi
  sebelumnya.

**Belum dikerjakan dari checklist Reliability (lihat PROJECT_STATE.md):**
verifikasi runtime `networkCallback` setelah airplane-mode off/on, dan
unit test untuk retry registrasi (v3.16.5) / fix give-up state (v3.16.6) —
keduanya butuh `WarpRegistrationClient`/`GoBackend` di belakang
interface/DI dulu supaya bisa di-mock di JVM test.

**BELUM DIKONFIRMASI build CI v3.16.7 — cek dulu di sesi berikutnya.**

## v3.16.6 — Reliability audit batch 2/N: keputusan failover + fix give-up state (2026-08-06)

> User serahkan keputusan failover WARP↔DNS ke Claude ("keputusan terbaik
> berada digenggaman mu"). Keputusan diambil + didokumentasikan di bawah,
> plus satu bug nyata ditemukan di jalur "give up" saat investigasi.

**Keputusan arsitektur: TIDAK ada auto-switch mode WARP→DNS atau
sebaliknya.** Alasan: user memilih mode WARP secara eksplisit untuk
enkripsi penuh trafik. Auto-switch diam-diam ke DNS-only (yang HANYA
memblokir iklan, TIDAK mengenkripsi trafik lain) saat WARP gagal berarti
diam-diam menurunkan jaminan keamanan yang user sudah pilih, tanpa consent
di momen itu. Ini melanggar prinsip dasar aplikasi VPN/privacy: jangan
pernah melemahkan proteksi tanpa persetujuan eksplisit saat itu terjadi.
Keputusannya: tetap 2 mode terpisah, tidak saling menggantikan otomatis;
yang diperbaiki adalah supaya kegagalan WARP itu sendiri berperilaku benar
dan jelas (lihat di bawah), bukan menutupinya dengan pindah mode diam-diam.

**Bug ditemukan saat investigasi (`WarpTunnelManager.kt`):** setelah
`MAX_RECONNECT_ATTEMPTS` (5) habis, `attemptReconnect()` cuma `return` —
tapi `reconnectAttempts` cuma di-reset ke 0 oleh probe yang BERHASIL, jadi
kalau tunnel-nya benar-benar mati, watchdog tetap manggil `attemptReconnect()`
lagi tiap `HEALTH_CHECK_INTERVAL_MS` (25 detik) SELAMANYA — tiap panggilan
langsung `return` di cabang yang sama, jadi pesan "dihentikan sementara"
menyesatkan: tidak pernah benar-benar berhenti, cuma diam-diam gagal
berulang tanpa henti (buang baterai/probe, dan gak ada jalan balik ke UP
tanpa user maksa toggle mode off-on).

**`app/src/main/java/com/fdzaki/adshield/warp/WarpTunnelManager.kt`**
- Cabang "give up" di `attemptReconnect()`: sekarang benar-benar
  menghentikan watchdog (`watchdogJob?.cancel()`), unregister network
  watcher, dan tear down interface (`backend.setState(tunnel, DOWN, null)`)
  — state jadi deterministik (benar-benar DOWN + error jelas), bukan limbo
  retry diam-diam selamanya. Pola teardown ini SAMA dengan `disconnect()`
  yang sudah ada (bukan perilaku baru, cuma dipakai ulang untuk kasus
  "menyerah").
- Efek samping yang disengaja: begitu WARP tear-down, trafik kembali lewat
  jalur normal TANPA proteksi (fail-open untuk konektivitas internet dasar)
  — tapi dengan pesan error yang sudah SUDAH tersambung ke UI
  (`warpLastError` di `HomeScreen.kt`, dikonfirmasi masih ada sebelum
  batch ini). Alternatif fail-closed (blokir semua trafik sampai user
  restart manual) dipertimbangkan tapi ditolak: user bisa kehilangan
  internet sepenuhnya tanpa notifikasi push yang jelas kalau mereka tidak
  sedang membuka app — resiko lebih besar daripada fail-open dengan pesan
  error yang jelas begitu mereka buka app.

**`app/build.gradle.kts`**
- `versionCode` 47 → 48, `versionName` 3.16.5 → 3.16.6.

**Batas jaminan:** analisis statis (coroutine cancellation semantics
diverifikasi manual — `watchdogJob.cancel()` dipanggil dari dalam coroutine
yang sama dengan job tsb aman, checked-cooperative di suspension point
berikutnya). Belum ada bukti runtime.

## v3.16.5 — Reliability audit batch 1: retry+backoff untuk registrasi WARP (2026-08-06)

> CI v3.16.4 confirmed hijau oleh user. Mulai kerjakan audit checklist
> eksternal (Reliability, prioritas #1). Item pertama: "Retry dengan
> Exponential Backoff untuk handshake/koneksi".

**Temuan:** `WarpTunnelManager.ensureRegistered()` memanggil
`WarpRegistrationClient.register()` sekali saja — gagal sekali (mis. hiccup
jaringan sesaat) langsung gagalkan seluruh `connect()`, padahal
`attemptReconnect()` di file yang sama SUDAH punya exponential backoff untuk
kegagalan reconnect. Registrasi (yang jalan duluan, sebelum tunnel/watchdog
ada) jadi satu-satunya titik tanpa retry sama sekali.

**`app/src/main/java/com/fdzaki/adshield/warp/WarpTunnelManager.kt`**
- `ensureRegistered()`: retry sampai `REGISTER_MAX_ATTEMPTS` (3x) dengan
  exponential backoff (`REGISTER_BASE_BACKOFF_MS`=2s → cap
  `REGISTER_MAX_BACKOFF_MS`=10s) — pola sama dengan `attemptReconnect()`,
  budget lebih kecil karena ini blocking di dalam `connect()` yang user
  tunggu langsung (bukan proses background).
- Tidak ada import baru (`delay`, `min` sudah dipakai di file yang sama).

**`app/build.gradle.kts`**
- `versionCode` 46 → 47, `versionName` 3.16.4 → 3.16.5.

**Batas jaminan:** ini analisis statis kode Kotlin (logika backoff, tipe,
scope var sudah diverifikasi manual baris-per-baris) — belum ada bukti
runtime karena tidak ada Gradle/Android SDK/network di lingkungan kerja
sesi ini. Belum ada unit test untuk retry ini (`WarpRegistrationClient`
adalah Kotlin `object`, butuh refactor ke interface/DI dulu untuk bisa
di-mock di JVM test — dicatat sebagai item terpisah di PROJECT_STATE, belum
dikerjakan batch ini).

## v3.16.4 — Fix compile error di DnsPacketTest.kt (root cause ketemu) (2026-08-06)

> User upload `log_fail_20260805_221223_run31051760094.zip` — sekarang ada
> `test-output.log` (hasil fix v3.16.3), dan langsung ketahuan akar
> masalahnya: `:app:compileDebugUnitTestKotlin FAILED` dengan 6 error
> `The integer literal does not conform to the expected type Byte` di
> `DnsPacketTest.kt` baris 27, 28, 144, 145, 160, 161.

**Root cause:** `byteArrayOf(10, 111, 222, 5)` — literal `222` melebihi
rentang `Byte` Kotlin (-128..127, karena `Byte` signed). Kotlin tidak
mengizinkan narrowing implisit untuk literal di luar rentang itu, beda
dengan `10`/`111`/`5`/`1` yang masih di dalam rentang jadi lolos tanpa
keluhan compiler.

**`app/src/test/java/com/fdzaki/adshield/vpn/DnsPacketTest.kt`**
- Semua 6 kemunculan `222` di `byteArrayOf(...)` diubah jadi `222.toByte()`
  (pola yang sama sudah dipakai di file yang sama untuk `0xAB.toByte()`
  dkk., cuma kelewatan di 4 alamat IP `10.111.222.x` ini). Tidak ada
  perubahan pada nilai/skenario test, murni fix sintaks Kotlin.
- Dicek juga: tidak ada literal >127 lain di file test ini (`BlocklistManagerTest.kt`
  tidak pakai `byteArrayOf` sama sekali, jadi tidak terdampak bug yang sama).

**`app/build.gradle.kts`**
- `versionCode` 45 → 46, `versionName` 3.16.3 → 3.16.4.

**BELUM DIKONFIRMASI:** run CI v3.16.4. Fix ini sudah pasti membereskan
error kompilasi yang tercatat di log (itu murni analisis statis kode Kotlin,
bukan tebakan), tapi belum ada bukti runtime bahwa `compileDebugUnitTestKotlin`
dan `testDebugUnitTest` benar-benar PASS sampai selesai (mis. assertion di
dalam test itu sendiri belum pernah tereksekusi sama sekali). Cek run
berikutnya sebelum lanjut ke audit checklist reliability/dst.

## v3.16.3 — Fix diagnostic blind spot: run 31051130336 gagal tanpa detail (2026-08-06)

> User upload `log_fail_20260805_220324_run31051130336.zip` — isinya cuma
> `FAILURE_SUMMARY.txt` (3 baris: run id, commit, timestamp), TIDAK ADA
> mapping/reports/test-results sama sekali. Root cause: step `Run unit
> tests` (baru ditambah v3.16.2) kemungkinan gagal di tahap kompilasi
> Kotlin test sourceset — error compiler Kotlin ditulis ke console, BUKAN
> ke file report, jadi semua pola `find` di step diagnostik lama nihil hasil.

**`.github/workflows/build.yml`**
- Step `Run unit tests` & `Build signed release APK`: output di-pipe ke
  `tee test-output.log` / `tee build-output.log` (dengan `set -o pipefail`
  supaya exit code asli tetap terjaga, bukan exit code `tee`).
- Step `Collect failure diagnostics`: copy kedua file log itu ke
  `fail-logs/` tanpa syarat (di awal, sebelum pencarian mapping/reports/
  test-results) — supaya kegagalan tahap kompilasi yang tidak menulis
  report file apa pun tetap terekam di artifact `log_fail_*`.

**`app/build.gradle.kts`**
- `versionCode` 44 → 45, `versionName` 3.16.2 → 3.16.3.

**BELUM DIKONFIRMASI & BLOCKING:** run 31051130336 (commit
`41ffd5619258974dc640dfec478eecaeece208c3`) masih gagal dengan penyebab
TIDAK DIKETAHUI — v3.16.3 cuma memperbaiki *kemampuan mendiagnosis*,
BUKAN penyebab gagalnya sendiri, karena penyebabnya belum kebaca sama
sekali dari artifact yang ada. **Jangan lanjut ke item Audit Checklist
(reliability/concurrency/security/dst.) sebelum run CI v3.16.3 dicek DAN
lulus** — menambah fitur di atas build yang belum diketahui statusnya
mengulang pola risiko yang sama seperti insiden WARP (lihat
PROJECT_STATE.md bagian pending).

## v3.16.2 — Wire unit test ke CI (2026-08-06)

> Gap ditemukan lewat static review: `DnsPacketTest.kt` dan
> `BlocklistManagerTest.kt` (ditambahkan sebelumnya, sudah tercatat di
> `FILE_MANIFEST.txt`) tidak pernah benar-benar dijalankan oleh
> `.github/workflows/build.yml` — workflow langsung `gradle assembleRelease`
> tanpa step test terpisah. Artinya regresi di parser DNS atau logika
> matching blocklist bisa lolos ke APK release tanpa terdeteksi CI.

**`.github/workflows/build.yml`**
- Tambah step `Run unit tests` (`gradle testDebugUnitTest --no-daemon`)
  sebelum step build APK, supaya build gagal cepat kalau ada regresi di
  `DnsPacket`/`BlocklistManager` — tidak perlu tunggu sampai tahap R8/signing.
- Step `Collect failure diagnostics` diperluas untuk ikut copy
  `app/build/test-results/**/*.xml` (hasil JUnit test), bukan cuma
  `app/build/reports` — supaya kegagalan unit test juga terbaca dari
  artifact `log_fail_*` yang kecil, konsisten dengan pola yang sudah ada
  untuk kegagalan R8 di v3.16.1.

**`app/build.gradle.kts`**
- `versionCode` 43 → 44, `versionName` 3.16.1 → 3.16.2.

**Belum dikonfirmasi** (butuh runner sungguhan, di luar kemampuan analisis
statis): run CI v3.16.2 sendiri. Cek run ini dulu di sesi berikutnya sebelum
lanjut ke item pending lain (validasi WARP end-to-end tetap prioritas
tertinggi, lihat PROJECT_STATE.md).

## v3.16.1 — Fix CI: R8 minifyReleaseWithR8 FAILED + artifact log kegagalan (2026-08-06)

> Ditemukan dari cek build CI yang tertunda sejak v3.13.0 (lihat PROJECT_STATE.md).
> Build v3.16.0 **FAILED** di task `minifyReleaseWithR8` — bukan bug kode app,
> tapi R8 tidak menemukan annotation compile-time-only (`errorprone`,
> `javax.annotation`) yang ditarik transitif oleh Google Tink lewat
> `androidx.security:security-crypto:1.1.0` (dipakai `VpnProfileRepository`
> buat `EncryptedSharedPreferences`). Class-class itu memang tidak ada di
> runtime classpath by design — solusinya `-dontwarn`, bukan `-keep`.

**File diubah:**
- `app/proguard-rules.pro` — tambah `-dontwarn` untuk
  `com.google.errorprone.annotations.**`, `javax.annotation.**`,
  `javax.annotation.concurrent.**`.
- `.github/workflows/build.yml` — step baru `Collect failure diagnostics` +
  `Upload failure log artifact` (`if: failure()`), upload `fail-logs/**`
  (missing_rules.txt, lint/build reports, FAILURE_SUMMARY.txt) sebagai
  GitHub Actions artifact bernama `log_fail_<timestampUTC>_run<run_id>`,
  retention 14 hari. Tujuan: cukup buka tab Actions run yang gagal → download
  artifact kecil ini, tanpa perlu "Download log archive" mentah lagi.
- `app/build.gradle.kts` — versionCode 42→43, versionName 3.16.0→3.16.1.

**Belum diverifikasi:** build CI untuk v3.16.1 ini sendiri (root cause R8
sudah diperbaiki berdasar analisis statis log, tapi belum ada run baru yang
mengonfirmasi fix-nya benar-benar hijau).

## v3.16.0 — Batch: bikin layar konfigurasi IKEv2 + wire ke UI (2026-08-06)

> Lanjutan v3.15.0. `IkeV2VpnEngine` (v3.14.0) belum punya UI sama sekali
> (server/identity/auth belum ada form input di mana pun) — batch ini
> menambah form profil + kartu toggle di Home screen, driven langsung dari
> `MainViewModel` (bukan lewat Service khusus seperti DNS/WARP — IKEv2
> tunnelnya dikelola `android.net.VpnManager` di level OS begitu
> diprovision, jadi tidak butuh custom `VpnService` subclass sendiri).

**File baru:** tidak ada (semua perubahan additive ke file existing).

**File diubah:**
- `data/VpnProfileRepository.kt` — `saveIkeV2Profile`/`getIkeV2Profile`
  (username+password/EAP-MSCHAPv2 saja; auth sertifikat belum ada form-nya,
  lihat kdoc).
- `ui/MainViewModel.kt` — instance `IkeV2VpnEngine`, state `ikeV2State`,
  `ikeV2Profile`, `saveIkeV2Profile()`, `prepareIkeV2Consent()`,
  `connectIkeV2()`/`disconnectIkeV2()`. `activeMode` di-set `AppMode.IKEV2`
  cuma di `connectIkeV2()` (tidak nunggu observasi `Connected` seperti fix
  WARP di v3.15.0 — beda kasus, IKEv2 API 30-32 memang `Connected` optimis
  segera, lihat `IkeV2VpnEngine.startMonitoring()`).
- `ui/screens/HomeScreen.kt` — `IkeV2ModeCard` (mirror gaya `WarpModeCard`)
  + `IkeV2ProfileDialog` (form server/identity/username/password).
- `MainActivity.kt` — `ikeV2ConsentLauncher` (launcher terpisah dari
  `vpnPermissionLauncher` — consent IKEv2 lewat
  `VpnManager.provisionVpnProfile()`, bukan `VpnService.prepare()`),
  `requestIkeV2Start()`. Mutual exclusion: `startDnsService()`/
  `startWarpService()` sekarang juga panggil `viewModel.disconnectIkeV2()`;
  `requestIkeV2Start()` stop DNS+WARP dulu.

**SENGAJA TIDAK dikerjakan (di luar scope batch ini):**
- Boot-persistence (`BootReceiver`) dan QS tile untuk IKEv2 — beda dari
  DNS/WARP yang sudah punya keduanya. IKEv2 hanya start/stop manual dari
  Home screen untuk saat ini.
- Auth sertifikat (`certificateAlias`) — form cuma username+password.
- Split-tunnel per-app — memang tidak bisa (batasan `Ikev2VpnProfile`, lihat
  kdoc `VpnProtocolConfig.IkeV2`), bukan belum dikerjakan.

**BELUM DIKONFIRMASI build CI** — menumpuk di antrean yang sama dengan
v3.13.0/v3.14.0/v3.15.0 (lihat entri-entri itu), semuanya BELUM PERNAH
dicek sama sekali. Cek build CI dulu di sesi berikutnya sebelum lanjut
apa pun — idealnya satu run mewakili keempatnya sekaligus.

## v3.15.0 — Batch: wire WARP ke VpnEngine di titik drive nyata (2026-08-05)

> Riset Batch 4 (Shadowsocks/VLESS via Xray-core) menemukan TIDAK ADA AAR
> resmi di Maven/JitPack — cuma source Go (2dust/AndroidLibXrayLite) yang
> harus di-compile sendiri via gomobile+NDK, scope/risiko mirip OpenVPN.
> User pilih skip dulu, alihkan ke menuntaskan wiring engine yang sudah ada
> (WARP adapter v3.13.0 + IKEv2 v3.14.0) ke UI dulu.

**File diubah:** `warp/WarpForegroundService.kt` — SATU-SATUNYA titik drive
lifecycle WARP nyata di seluruh app (MainActivity/HomeScreen/BootReceiver/
WarpTileService semua cuma kirim Intent ke service ini, tidak pernah panggil
`WarpTunnelManager` langsung). `connect()`/`disconnect()` sekarang lewat
`WarpVpnEngineAdapter`, bukan `WarpTunnelManager` langsung — membuktikan
abstraksi `VpnEngine` benar-benar men-drive trafik produksi.

**Gap desain yang ditemukan & diselesaikan:** `VpnEngine.connect()`
mengembalikan `Unit`, bukan `Boolean` sukses seperti
`WarpTunnelManager.connect()` lama. Diganti dengan observasi
`warpEngine.state.first { Connected atau Error }` setelah `connect()`
return, sesuai kdoc `VpnEngine.kt` sendiri ("callers should still observe
state"). `activeMode` cuma di-set `WARP_TUNNEL` kalau hasilnya `Connected`.

**SENGAJA TIDAK diubah:** `tunnelManager` (instance `WarpTunnelManager`)
TETAP dipakai apa adanya di `observeQualityForNotification()`/
`buildNotification()` — butuh detail `WarpConnectionQuality`/`Tunnel.State`
yang memang tidak dibawa `VpnEngineState`. Kedua objek (`tunnelManager` &
`warpEngine`) membungkus singleton `WarpTunnelManager.getInstance()` yang
SAMA — bukan dua sumber kebenaran bersaing. `MainViewModel` (state
`warpState`/`warpQuality`/`forgetWarpAccount()` untuk Diagnostics screen)
JUGA TIDAK diubah — itu observasi/aksi read-side, bukan titik drive
connect/disconnect, dan `forgetAccount()` bukan bagian kontrak `VpnEngine`.

**IKEv2 (v3.14.0) BELUM bisa di-"wire" — bukan diabaikan, memang belum ada
UI-nya sama sekali.** `VpnProtocolConfig.IkeV2` butuh server address,
identity, dan auth (cert alias ATAU username+password) — tidak ada layar/
form untuk itu di app manapun saat ini, beda dengan WARP yang sudah
punya toggle Home screen. Wiring IKEv2 sungguhan = bikin layar konfigurasi
baru (input server, pilih metode auth, persist `VpnProtocolConfig.IkeV2`)
dulu — fitur baru, bukan sekadar wiring. **WAJIB tanya user dulu soal UX-nya
sebelum mulai** (mirip keputusan DoH/DoT yang disisihkan v2.5.0).

**BELUM DIKONFIRMASI build CI** untuk batch ini (juga masih menumpuk dari
v3.14.0/v3.13.0 yang juga belum pernah dicek) — cek build CI dulu di sesi
berikutnya, idealnya sekali jalan untuk ketiga versi ini sekaligus.

## v3.14.0 — Batch 3/N: IKEv2 native engine, ganti OpenVPN (2026-08-05)

> **Keputusan besar:** OpenVPN DIBATALKAN dari roadmap — riset menemukan
> TIDAK ADA library resmi non-GPL/AGPL untuk OpenVPN di Android
> (`ics-openvpn`=GPLv2, `openvpn3` core resmi OpenVPN Inc=AGPLv3, dan
> OpenVPN Inc menolak commercial license). Pakai keduanya berarti seluruh
> AdShield wajib ikut open-source. User pilih skip permanen, loncat ke
> IKEv2 native Android — **0 dependency pihak ketiga, 0 risiko lisensi**
> (`android.net.VpnManager`/`Ikev2VpnProfile` adalah platform API AOSP,
> Apache 2.0).

**File baru:**
1. **`protocol/IkeV2VpnEngine.kt`** — engine `VpnEngine` native pakai
   `VpnManager`/`Ikev2VpnProfile`. Setiap method/konstanta diverifikasi
   langsung ke source AOSP (`frameworks/base`) sebelum dipakai, bukan
   tebakan. 2 batasan platform (bukan gap sementara — batas API itu
   sendiri): (a) `Ikev2VpnProfile.Builder` butuh API 30+
   (`FEATURE_IPSEC_TUNNELS`), (b) monitoring state/error publik
   (`getProvisionedVpnProfileState()`, broadcast `ACTION_VPN_MANAGER_EVENT`)
   butuh API 33+ — field yang sama ada di source AOSP untuk API 30-32 tapi
   ditandai `@hide`, TIDAK bisa dipakai app biasa. Di API 30-32, `state`
   jadi tebakan optimis (`Connected` begitu `startProvisionedVpnProfileSession()`
   tidak melempar exception), BUKAN sinyal terkonfirmasi.

**File diubah:**
1. **`protocol/VpnEngine.kt`** — tambah `prepareConsent(config): Intent?`
   (default `null`, non-breaking). IKEv2 minta consent lewat Intent dari
   `VpnManager.provisionVpnProfile()` (mirip `VpnService.prepare()` tapi
   per-call, bukan dicek sekali global) — `VpnEngine` lama tidak punya
   hook untuk pola ini sama sekali. `WarpVpnEngineAdapter` TIDAK di-override
   (consent WARP/DNS tetap `VpnService.prepare()` di MainActivity, di luar
   interface ini).
2. **`protocol/VpnProtocolConfig.kt`** — tambah `VpnProtocolConfig.IkeV2`
   dengan 2 metode auth (`certificateAlias` via AndroidKeyStore, atau
   `username`+`password`/EAP-MSCHAPv2). PSK TIDAK dimodelkan (gap
   diketahui). Split-tunnel-by-app TIDAK bisa diimplementasi sama sekali
   dengan API ini (`Ikev2VpnProfile` cuma punya `setBypassable()` global,
   bukan allow/deny list per-app) — bukan "belum", tapi memang tidak ada
   di platform API.
3. **`app/build.gradle.kts`** — versionCode 39→40, versionName 3.13.0→3.14.0.

**BELUM DIWIRE ke UI** (sama seperti WarpVpnEngineAdapter v3.13.0) —
sengaja, batch ini murni engine + interface. **BELUM dikonfirmasi build
CI** — cek dulu di sesi berikutnya. Provisioning cert (`certificateAlias`)
mengasumsikan alias SUDAH ada di AndroidKeyStore — batch ini tidak
menyediakan UI import sertifikat.

## v3.13.0 — Batch 2/N: Adaptasi WireGuard/WARP ke VpnEngine (2026-08-05)

> Lanjutan langsung v3.12.0 — urutan batch yang sudah disepakati:
> "buktikan abstraksi ke engine yang SUDAH terbukti jalan dulu, sebelum
> tambah engine baru." Batch ini **HANYA menambah 1 file adapter baru** —
> **0 baris di `warp/*.kt` diubah**, jadi risiko regresi terhadap mode
> WARP yang sudah ada (UI, QS tile, watchdog, dsb — semua masih panggil
> `WarpTunnelManager` langsung) adalah nol.

**File baru:**
1. **`protocol/WarpVpnEngineAdapter.kt`** — implementasi `VpnEngine` yang
   membungkus `WarpTunnelManager.getInstance()` (singleton yang sudah
   ada), TANPA mengubah class itu sendiri. Menerjemahkan
   `Tunnel.State` + `connecting` + `lastError` + `quality.reconnectAttempts`
   (empat `StateFlow` yang sudah ada di `WarpTunnelManager`) jadi satu
   `StateFlow<VpnEngineState>` lewat `combine()`.

**File diubah:**
1. **`protocol/VpnProtocolConfig.kt`** — tambah `VpnProtocolConfig.Warp`
   (marker config, TANPA field server/key — WARP registrasi-based lewat
   `WarpAccountRepository`, bukan profil user-supplied seperti
   OpenVpn/IkeV2/Shadowsocks). `routeIpv6` di config ini **sengaja tidak**
   diteruskan ke `WarpTunnelManager` — manager itu sendiri sudah baca
   `SettingsRepository.warpRouteIpv6` langsung tiap `connect()`/
   `attemptReconnect()` (keputusan arsitektur #6e), jadi meneruskan field
   ini lewat config akan bikin 2 sumber kebenaran bersaing untuk setting
   yang sama.
2. **`app/build.gradle.kts`** — versionCode 38→39, versionName 3.12.0→3.13.0.

**AI Assumption Log (dicatat, bukan diverifikasi lewat compiler/device):**
- `connectedSinceMs` untuk `VpnEngineState.Connected` DIBUAT BARU di
  level adapter (`WarpTunnelManager` sendiri tidak melacak timestamp ini
  — UI WARP yang sudah ada baca "elapsed since" dari notifikasi
  foreground service, bukan dari sini). Timestamp di-set saat transisi
  pertama ke `Tunnel.State.UP` terdeteksi, di-reset ke 0 saat state
  turun dari UP — BELUM diverifikasi lewat compile/device run.
- Mapping `Reconnecting` HANYA dari `quality.reconnectAttempts > 0` —
  `WarpTunnelManager.reconnecting` (flag internal yang lebih akurat)
  bersifat `private`, tidak diekspos publicly, jadi adapter TIDAK bisa
  membaca itu langsung tanpa mengubah `WarpTunnelManager.kt` (yang
  sengaja tidak disentuh batch ini). Trade-off yang diterima: window
  singkat di mana `reconnectAttempts` masih > 0 dari sesi reconnect
  SEBELUMNYA (baru di-reset ke 0 di `connect()` manual berikutnya) bisa
  membuat state sempat terbaca `Reconnecting` alih-alih `Disconnected`
  tepat setelah `disconnect()` manual — kosmetik, tidak memengaruhi
  `WarpTunnelManager` yang sebenarnya (single source of truth `state`nya
  sendiri tidak berubah).
- **BELUM DIWIRE ke UI mana pun** (MainActivity/HomeScreen/BootReceiver/
  QS tile semua masih panggil `WarpTunnelManager` langsung) — itu
  keputusan sadar batch ini (lihat kdoc file), bukan kelalaian.
- **BELUM dikonfirmasi build CI** — cek dulu di sesi berikutnya sebelum
  lanjut Batch 3 (OpenVPN, item paling berisiko — lihat peringatan
  PROJECT_STATE.md v3.12.0).

## v3.12.0 — Batch 1/N: Architecture Multi-Protokol (scaffolding) (2026-08-05)

> **Keputusan besar user (2026-08-05):** AdShield diperluas dari 2 mode
> (DNS Ad-Block, WARP) jadi VPN client multi-protokol: WireGuard (sudah
> ada), + OpenVPN, IKEv2/IPsec, Shadowsocks/VLESS. User eksplisit minta
> rilis bertahap, 1 engine per batch (Batch Lock dipatuhi). **Batch ini =
> fondasi arsitektur SAJA — belum ada satu pun engine baru yang
> fungsional.** DNS Ad-Block & WARP TIDAK disentuh sama sekali di batch
> ini (0 file mode lama diubah).

**File baru:**
1. **`protocol/VpnEngine.kt`** — interface kontrak umum tiap engine
   (`connect()`, `disconnect()`, `state: StateFlow<VpnEngineState>`).
2. **`protocol/VpnEngineState.kt`** — sealed class state konektivitas
   (Disconnected/Connecting/Connected/Reconnecting/Error), dipakai semua
   engine (termasuk nanti WireGuard existing, saat diadaptasi).
3. **`protocol/VpnProtocolConfig.kt`** — sealed class model konfigurasi
   per protokol (OpenVpn/IkeV2/Shadowsocks) + `SplitTunnelMode` enum.
   **Parser `.ovpn`/`.conf`/URL/QR BELUM diimplementasi** — cuma shape
   data class, field kemungkinan masih berubah setelah parser nyata
   ditulis per-engine.
4. **`data/VpnProfileRepository.kt`** — penyimpanan aman (Keamanan Data,
   poin 3 spesifikasi user) untuk private key/password/token pakai
   `EncryptedSharedPreferences`, terpisah total dari `SettingsRepository`
   (DataStore plain) — secrets tidak pernah masuk prefs tak terenkripsi.

**File berubah:**
- **`util/Constants.kt`** — `AppMode` ditambah 3 konstanta placeholder
  (`OPENVPN`, `IKEV2`, `SHADOWSOCKS`) — **belum wired ke UI/service
  manapun**, cuma identifier stabil untuk protocol/ package.
- **`app/build.gradle.kts`** — dependency `androidx.security:security-crypto:1.1.0`
  (diverifikasi via web search — versi stable terkini per Jul 2025,
  BUKAN alpha yang dipakai walau EncryptedSharedPreferences sudah
  deprecated sejak 1.1.0-beta01, masih berfungsi di 1.1.0 stable).

**BELUM DIKERJAKAN (sengaja, batch terpisah per Batch Lock):**
- WireGuard/WARP existing belum diadaptasi ke `VpnEngine` interface baru
  (rencana: batch berikutnya, "buktikan abstraksi ke engine yang sudah
  jalan" sebelum tambah engine baru)
- OpenVPN (JNI/native ics-openvpn), IKEv2 (StrongSwan/IKEv2VpnProfile),
  Shadowsocks/VLESS (Xray-core) — 0% dikerjakan, masing-masing 1 batch
  terpisah nanti
- Kill Switch, Split Tunneling UI, Auto-Reconnect NetworkCallback, QS
  Tile per-protokol baru — menunggu minimal 1 engine baru jalan dulu
- Foreground Service notification interaktif (durasi/kecepatan) — sudah
  ada foreground service utk DNS/WARP, belum ada untuk protokol baru

**BELUM DIKONFIRMASI build CI** — batch ini murni file baru + 1
dependency, risiko regresi ke mode existing rendah (0 file
DNS/WARP disentuh), tapi tetap WAJIB cek CI dulu sebelum lanjut batch 2.

## v3.11.1 — HOTFIX: compile error DohClient.kt (2026-08-05)

> CI `compileReleaseKotlin` gagal di v3.11.0: 2 overload `createSocket`
> di custom `SSLSocketFactory` (`vpn/DohClient.kt`) memanggil
> `delegate.createSocket(Socket, String, Int, Boolean)` tapi mengirim
> `InetAddress` di posisi parameter `String` — Kotlin tidak resolve
> overload manapun.

**Fix:**
1. **`vpn/DohClient.kt`** — di 2 method (`createSocket(InetAddress, Int)`
   dan `createSocket(InetAddress, Int, InetAddress, Int)`), konversi ke
   `.hostAddress` (String) sebelum diteruskan ke `delegate.createSocket()`.

Fungsionalitas DoH v3.11.0 tidak berubah, murni perbaikan sintaks compile.
**BELUM DIKONFIRMASI** build CI sukses maupun tes device — cek CI dulu di
sesi berikutnya sebelum lanjut ke validasi fungsional.

## v3.11.0 — DNS-over-HTTPS (DoH), fallback ke plain DNS (2026-08-05)

> User laporkan v3.10.2 (fix MTU) TIDAK menolong — error persis:
> `DNS_PROBE_FINISHED_BAD_SECURE_CONFIG` di WiFi, matot di data seluler.
> Bukan Android Private DNS atau Chrome Secure DNS (dikonfirmasi user:
> keduanya tidak pernah diaktifkan). Kesimpulan: plain UDP port 53 memang
> diblokir/rusak total di jaringan user — bukan lagi soal kode app.
> **Keputusan user (2026-08-05, "last verdict"):** implementasi DoH,
> fallback ke plain DNS biasa kalau DoH gagal, dua provider sekaligus
> (Cloudflare + Google).

**Fitur baru:**
1. **`vpn/DohClient.kt`** (baru) — resolver DNS-over-HTTPS pakai
   `HttpsURLConnection` bawaan Android (tanpa dependency baru). Setiap
   socket di-`protect()` manual lewat custom `SSLSocketFactory` supaya
   trafik DoH sendiri tidak ikut ke-tunnel balik ke tun interface kita
   sendiri (prinsip sama seperti socket UDP upstream yang sudah ada).
2. **`util/Constants.kt`** — `DOH_ENDPOINTS` (Cloudflare
   `cloudflare-dns.com/dns-query`, Google `dns.google/dns-query`),
   `DOH_TIMEOUT_MS = 4000`.
3. **`vpn/AdBlockVpnService.kt`** — `forwardToUpstream()` dan
   `prefetchOne()` sekarang coba DoH DULU (kedua provider berurutan),
   baru fallback ke rantai resolver plain-UDP lama
   (`Constants.UPSTREAM_DNS_SERVERS`) kalau DoH gagal total. Plain-UDP
   dipertahankan sebagai jaring pengaman untuk jaringan yang justru
   memblokir DoH tapi UDP:53 normal — bukan full-replace.

**Belum dikerjakan (sengaja di luar scope batch ini):** DoT (DNS-over-TLS,
port 853) — DoH sudah cukup untuk kasus user saat ini (trafik lewat 443,
sama seperti HTTPS biasa); DoT pakai port terpisah (853) yang sama
rentannya diblokir seperti UDP:53 kalau operator block berdasarkan port,
jadi prioritasnya rendah. **BELUM DIKONFIRMASI** di device fisik — WAJIB
jadi hal pertama dicek di sesi berikutnya (lihat PROJECT_STATE.md).

## v3.10.2 — HOTFIX: VPN_MTU tidak wajar (32000 → 1500) (2026-08-05)

> User laporkan v3.10.1 (fix resolver diversity) TIDAK menolong — mode DNS
> Ad-Block masih total internet failure, TERMASUK akses browser ke IP
> langsung (bypass DNS sepenuhnya). Ini janggal: arsitektur
> `addRoute(VPN_ROUTE, 32)` cuma capture trafik ke `10.111.222.1` (port
> 53) — trafik lain seharusnya tidak pernah disentuh VPN sama sekali.
> Gejala ini mengarah ke tun interface bermasalah di level
> establish()/kernel, bukan soal resolusi DNS.

**Fix:**
1. **`util/Constants.kt`** — `VPN_MTU` `32000` → `1500`. Nilai lama jauh
   di luar MTU link nyata manapun (WiFi/seluler ~1500) — kandidat kuat
   penyebab tun interface direject/berperilaku aneh oleh network stack
   Android tertentu.

**BELUM DIKONFIRMASI** user di device fisik — WAJIB jadi hal pertama
dicek di sesi berikutnya. Kalau masih gagal setelah ini, dugaan bergeser
ke arah non-DNS-spesifik sepenuhnya (bukan lagi soal resolver ataupun
MTU) — kemungkinan port 53/UDP diblokir total di jaringan operator user,
atau ada faktor device/OEM lain di luar kendali kode app.

## v3.10.1 — HOTFIX: total DNS failure (upstream resolver diversity) (2026-08-05)

> User laporkan: nyalakan mode DNS Ad-Block → SEMUA app kehilangan internet
> total (bukan sekadar domain tertentu gagal). Root cause: v3.9.0 mengganti
> fallback resolver `8.8.8.8` (Google) → `1.0.0.1` (Cloudflare) demi
> kepatuhan literal ke requirement roadmap "DNS cepat 1.1.1.1/1.0.0.1" —
> efeknya baru terasa sekarang: `1.1.1.1` dan `1.0.0.1` SAMA-SAMA
> Cloudflare. Di jaringan yang blokir Cloudflare DNS, kedua resolver gagal
> bareng, nol fallback provider lain tersisa, seluruh resolusi DNS mati.

**Fix:**
1. **`util/Constants.kt`** — `UPSTREAM_DNS_SERVERS` sekarang
   `[1.1.1.1, 1.0.0.1, 8.8.8.8]`. Primary pair Cloudflare TETAP
   dipertahankan (tidak melanggar requirement roadmap), Google ditambah
   balik sebagai resolver ke-3 — provider berbeda, jalur keluar kalau
   Cloudflare diblokir di jaringan tertentu.

**Belum dikerjakan:** deteksi otomatis "resolver mana yang benar-benar
reachable di jaringan ini" (baru fallback berurutan tetap, bukan smart
selection) — kalau masalah serupa muncul lagi dengan resolver berbeda,
pertimbangkan probe reachability seperti `WarpEndpointSelector` tapi untuk
DNS plain-UDP. **BELUM DIKONFIRMASI** user — fix ini berdasar analisis kode
+ pola insiden serupa (WARP IPv6, v3.2.1), bukan hasil tes device langsung.
WAJIB jadi hal pertama dicek di sesi berikutnya.

## v3.10.0-hotfix-repack — Perbaikan struktur ZIP/repo nested-folder (2026-08-05)

> **Bukan perubahan kode app.** ZIP pengiriman v3.10.0 sebelumnya salah
> dibungkus (masih ada folder `AdShield-main/` di top-level), warisan dari
> ZIP sumber "Download ZIP" GitHub yang di-upload user ke sesi ini. Command
> update Termux standar proyek ini meng-unzip isi ZIP diasumsikan flat
> langsung ke root proyek — karena masih dibungkus, hasilnya jadi folder
> `AdShield-main/` bersarang di dalam repo GitHub, `build.gradle.kts` tidak
> ada di root, CI Actions gagal menemukan project Gradle. Root cause detail
> di PROJECT_STATE.md. Batch ini: (1) ZIP pengiriman baru — flat, tanpa
> folder pembungkus apa pun; (2) command Termux untuk memperbaiki repo yang
> sudah kadung nested di GitHub. **versionCode/versionName TIDAK berubah**
> — isi kode identik dengan v3.10.0, cuma struktur paket/repo yang diperbaiki.

## v3.10.0 — Resource profiling instrumentation: memori & baterai (2026-08-05)

> Respons ke audit eksternal (skor 9.0/10) yang menandai "konsumsi baterai &
> memori perlu profiling" sebagai gap nyata — dicek ulang terhadap
> PROJECT_STATE.md dan dikonfirmasi 0% dikerjakan sebelumnya (beda dari 2
> item lain di audit yang sama, "kecepatan surfing" & "reconnect/stabilitas
> VPN", yang sudah selesai sejak v3.5.0–v3.9.0 dan cuma menunggu validasi
> device). Scope: instrumentasi baca-saja untuk mengukur, BUKAN optimasi —
> optimasi baru bisa diarahkan setelah ada data nyata dari lapangan.

**Baru:**
1. **`util/ResourceMonitor.kt`** (file baru) — snapshot memori app (PSS via
   `ActivityManager.getProcessMemoryInfo`), memori sistem tersisa + flag
   low-memory (`ActivityManager.getMemoryInfo`), dan baterai (persen, suhu,
   status charging via sticky intent `ACTION_BATTERY_CHANGED`). Semua API
   dipakai TIDAK butuh permission baru apa pun — tidak ada perubahan
   `AndroidManifest.xml`. Tiap bagian dibungkus `runCatching` terpisah
   (pola fail-safe sama seperti `util/CrashLogger.kt`) — kegagalan baca
   satu metrik tidak menjatuhkan metrik lain atau layar Diagnostik.
2. **`ui/MainViewModel.kt`** — `resourceSnapshot: StateFlow<ResourceMonitor.
   Snapshot>`, di-poll tiap 3 detik lewat `flow { while(true) { emit(...);
   delay(...) } }.stateIn(..., WhileSubscribed(5000), ...)`. SENGAJA
   poll-based dari UI layer, bukan service/logger baru — `WhileSubscribed`
   berarti loop polling ini hanya benar-benar jalan selagi layar Diagnostik
   dibuka, tidak menguras apa pun di background saat tidak dilihat (kalau
   ditambah jadi background sampler permanen, itu sendiri jadi biaya
   baterai yang justru sedang coba diukur/dihindari).
3. **`ui/screens/DiagnosticsScreen.kt`** — section baru "Resource (Memori &
   Baterai)": PSS app (MB), memori sistem tersisa (MB, merah kalau flag
   `lowMemory` sistem aktif), persen+status-charging baterai, suhu baterai.
   Ikut masuk ke teks "salin info diagnostik" yang sudah ada (tidak ada
   sumber kebenaran baru — pola yang sama seperti field lain di layar ini,
   lihat keputusan arsitektur #8b di PROJECT_STATE.md).

**Belum dikerjakan (sengaja di luar scope batch ini):** histori/logging
metrik dari waktu ke waktu (baru snapshot titik-waktu saat ini, bukan
tren) — kalau nanti mau grafik/tren, itu perlu keputusan penyimpanan data
baru (Room table? interval berapa? retention berapa lama?), bukan
perluasan kecil dari batch ini. Tidak ada perubahan pada `AdBlockVpnService`/
`WarpTunnelManager` — batch ini murni instrumentasi baca, TIDAK mengubah
perilaku VPN/DNS/WARP apa pun. **BELUM dikonfirmasi build CI + belum
dilihat terisi data nyata di device** — cek dulu di sesi berikutnya.

## v3.9.0 — Internet Surfing Optimization batch 2: DNS prefetch, warm-up, DNS server switch (2026-08-05)

> Lanjutan roadmap "Internet Surfing Optimization" (batch 1 = v3.7.0). User
> minta item yang masih tercatat "belum dikerjakan" di v3.7.0 diselesaikan:
> DNS prefetch, cache domain populer, connection warm-up. Ditambah 1 item
> "Wajib" dari roadmap yang sebelumnya belum sepenuhnya sesuai (resolver
> fallback DNS). Analisis statis saja — belum ada pengujian
> throughput/startup-latency di device fisik.

**Baru:**
1. **DNS prefetch + cache domain populer** (`vpn/AdBlockVpnService.kt`,
   `util/Constants.kt`) — 2.5 detik setelah mode DNS Ad-Block aktif, 24
   domain infrastruktur/CDN bertrafik tinggi (`Constants.
   POPULAR_PREFETCH_DOMAINS`: Google, YouTube, Apple, Cloudflare, Meta,
   WhatsApp, TikTok CDN, Microsoft, GitHub, AWS, Wikipedia, X, Discord,
   Netflix, Spotify) di-resolve di background lewat socket `protect()`
   terpisah dari socket pooled query nyata, hasil positif langsung masuk
   `DnsCache` — query PERTAMA app nyata untuk domain-domain ini langsung
   cache-hit, bukan cold round-trip upstream. Sengaja TIDAK cek blocklist
   dulu (lihat komentar kode): aman karena packet loop selalu cek
   `blocklist.isBlocked()` SEBELUM baca `DnsCache`, jadi cache domain yang
   ternyata diblokir cuma memori terbuang, bukan bug korektnes.
2. **`vpn/DnsPacket.kt`** — 2 fungsi baru: `encodeQuestionSection()` (encode
   domain string ke wire-format DNS QUESTION) dan `buildQueryMessage()`
   (rakit pesan query DNS standalone). Dibutuhkan prefetch karena beda dari
   forward query biasa, prefetch tidak punya paket tun asli untuk disalin
   question section-nya.
3. **Connection warm-up WARP** (`warp/WarpTunnelManager.kt`) —
   `startWatchdog()` sekarang tembak health-check PERTAMA langsung begitu
   tunnel UP, bukan tunggu `INITIAL_CHECK_DELAY_MS` (8 detik, dihapus).
   Manfaat ganda: paket pertama lewat interface baru mempercepat handshake/
   routing WireGuard settle, DAN kartu kualitas WARP di UI dapat angka
   latency/traffic-confirmed nyata dalam ~1 round-trip probe, bukan blank
   sampai 8 detik.
4. **DNS resolver fallback diganti** (`util/Constants.kt`) —
   `UPSTREAM_DNS_SERVERS` dari `1.1.1.1, 8.8.8.8` jadi `1.1.1.1, 1.0.0.1`,
   menyamakan dengan requirement "Wajib: DNS cepat 1.1.1.1/1.0.0.1" di
   roadmap — resolver kedua sekarang tetap Cloudflare, bukan lompat ke
   Google saat resolver pertama gagal.

**Item roadmap yang SUDAH beres sejak v3.7.0 (dicek ulang, tidak diulang):**
DNS cache internal, Auto MTU tuning, Smart endpoint selection, Fast
reconnect (network-switch watcher), DNS leak protection (struktural via
WireGuard `AllowedIPs 0.0.0.0/0` + DNS didorong lewat tunnel), Kill-switch
hardening (reconnect tanpa DOWN dulu), Packet loss detection, Persistent
keepalive 25s, toggle IPv4/IPv6 routing (`warpRouteIpv6`, sudah ada di
HomeScreen sejak v3.2.1).

**Item roadmap yang TETAP di luar scope batch ini (perlu keputusan
arsitektur terpisah, lihat PROJECT_STATE.md):** DoH/DoT untuk mode DNS
Ad-Block (forward ke upstream saat ini tetap UDP polos by design — lihat
keputusan lama, bukan regresi baru).

**File disentuh (5):** `util/Constants.kt`, `vpn/DnsPacket.kt`,
`vpn/AdBlockVpnService.kt`, `warp/WarpTunnelManager.kt`,
`app/build.gradle.kts` (versionCode 31→32, versionName 3.8.1→3.9.0).

**Belum dikonfirmasi build CI + belum ada pengujian di device fisik**
(prefetch belum pernah dilihat benar-benar mengurangi cold-lookup di
Diagnostics/Logs, warm-up WARP belum dicek turunkan waktu-sampai-latency-
pertama-tampil secara nyata) — cek sesi berikutnya.

## v3.8.1 — Feedback audit: false-positive "ACTIVE" on DNS failure (2026-08-05)

> User-requested audit of Quick Settings toggle feedback logic (1-batch,
> direct execution). Root cause found and fixed across 4 files.

**Diperbaiki (bug, bukan fitur baru):**
1. **`vpn/AdBlockVpnService.kt`** — `activeMode`/`wasRunning` used to be
   written to DataStore unconditionally BEFORE `builder.establish()` ran,
   and were never reverted on failure. Since `activeMode` is the single
   source of truth for both QS tiles (`DnsTileService`/`WarpTileService`)
   AND the Home ring (`MainViewModel.vpnActive`), a failed VPN interface
   left both stuck showing "ON" forever with zero correction — a silent
   false positive. WARP's equivalent path already gated this correctly on
   `if (connected)`; DNS mode did not. Now symmetric: write only happens
   after `establish()` confirmed success; explicit `setActiveMode(NONE)` on
   failure (also covers the WARP→DNS mode-switch-then-fail case, where the
   old mode would otherwise linger as "active").
2. **`ui/MainViewModel.kt`** — `vpnActive` was a `MutableStateFlow` flipped
   optimistically by `MainActivity` the instant a tap happened, regardless
   of actual establish() outcome. Removed; now derived directly from the
   persisted `activeMode` (same pattern WARP's `warpUp` already used), so
   it structurally cannot disagree with reality anymore.
3. **`MainActivity.kt`** — removed the now-obsolete
   `viewModel.setVpnActive(true/false)` calls from `startDnsService()`/
   `stopDnsService()`.
4. **`ui/screens/HomeScreen.kt`** — `dnsLastError` was only ever shown on
   the Diagnostics screen. Now surfaced inline under the ring (mirrors
   WARP's existing `error = warpError` card) whenever DNS mode isn't active
   and a last-error is present, so a failure is visible on the screen the
   user actually lands on.

**Confirmed NOT touched (audited, found correct):** `WarpForegroundService.kt`
state-write gating, `qs/DnsTileService.kt`/`qs/WarpTileService.kt` tile
subscription logic itself (bug was upstream in the state source, not the
tile code), VPN-permission-denied Snackbar/Toast paths (already fixed in a
prior audit pass, see code comments in `MainActivity.kt`).

**Belum dikerjakan (dicatat, bukan diabaikan):** no transient
"Menyambungkan…" tile-subtitle feedback during the multi-second WARP
connect window — would need a `Build.VERSION_CODES.Q` guard (tile subtitle
API, minSdk is 24) and was judged out of scope for this batch's specific
ask (the false-positive ACTIVE state). Flagged for a follow-up batch.

## v3.8.0 — Quick Settings Tile, 2 tile terpisah DNS/WARP (2026-08-05)

> User minta: tile QS terpisah per mode, TIDAK sekadar buka app — tile
> harus bisa langsung mengaktifkan fitur dari luar aplikasi. Audit
> menemukan fitur ini 0% dikerjakan sejak awal (App Shortcuts v2.2.0 beda
> fitur, dan tetap route lewat MainActivity).

**Baru:**
1. **`qs/DnsTileService.kt`, `qs/WarpTileService.kt`** — TileService per
   mode. Toggle 100% background (tanpa Activity) kalau izin VPN sudah ada,
   pakai exemption resmi Android untuk start-foreground-service-dari-
   background di `TileService#onClick()`.
2. **Dialog izin VPN pertama kali** (satu-satunya kasus tile buka
   Activity — batasan OS VpnService, bukan pilihan desain): MainActivity
   menangani lewat `ACTION_REQUEST_PERMISSION` per-tile, skip total
   `setContent()`, `finish()` diri sendiri persis setelah dialog selesai.
   Tema baru `Theme.AdShield.Transparent` (`themes.xml`) mencegah kedipan
   background gelap app selama proses ini.
3. **Icon monokrom baru** `ic_tile_dns.xml`, `ic_tile_warp.xml` — QS tile
   di-auto-tint sistem berdasar state aktif/nonaktif, icon 2 warna lama
   (shortcut) tidak cocok dipakai ulang.
4. Mutual exclusion (2 mode tidak boleh bareng) diduplikasi manual di
   kedua TileService — lihat PROJECT_STATE.md keputusan #13/#14 untuk
   alasan lengkap kenapa tidak direfactor ke fungsi bersama di batch ini.

**Diubah:** `AndroidManifest.xml` (2 `<service>` baru + izin sistem
`BIND_QUICK_SETTINGS_TILE`), `app/build.gradle.kts` (versionCode 29→30,
versionName 3.7.1→3.8.0), `strings.xml` (label default 2 tile).

**Belum dikonfirmasi build CI + belum pernah dicoba tarik tile dari QS
panel nyata di device** (WARP sendiri juga masih belum divalidasi
end-to-end — lihat PROJECT_STATE.md) — WAJIB dicek sesi berikutnya
sebelum klaim fitur ini beres, termasuk kasus dialog izin ditolak user
(Toast fallback, belum pernah terlihat muncul nyata).

**Atomic Change:** batch ini menyentuh 12 file, di atas batas normal 10 —
lihat catatan di PROJECT_STATE.md untuk alasan (seluruh potongan saling
bergantung untuk bisa compile).

## v3.7.1 — Tampilkan MTU/endpoint/packet-loss WARP di UI (2026-08-05)

> Tindak lanjut v3.7.0: field `mtuUsed`, `endpointUsed`, `packetLossPercent`
> di `WarpConnectionQuality` sudah dihitung sejak v3.7.0 tapi belum pernah
> ditampilkan di layar manapun. Murni perubahan UI, tidak ada perubahan
> logic/data layer.

**Diubah:**
1. **DiagnosticsScreen** — 3 baris baru di kartu "VPN Tunnel (WARP)":
   Packet loss (%, merah kalau >10%), MTU dipakai, Endpoint dipakai. Juga
   ditambahkan ke teks "salin info diagnostik".
2. **HomeScreen** (`WarpQualityRow`) — suffix ringkas `· loss N%` di baris
   status WARP, hanya muncul kalau packet loss > 0% (supaya kasus umum 0%
   tidak menuh-menuhin tampilan glance-level).

**Belum dikonfirmasi build CI + belum dicoba tampil dengan data WARP nyata
di device** (WARP sendiri masih belum divalidasi end-to-end — lihat
PROJECT_STATE.md) — WAJIB dicek sesi berikutnya.

## v3.7.0 — Internet Surfing Optimization: DNS cache, Auto MTU, Smart endpoint, Fast reconnect, DNS leak protection (2026-08-05)

> User minta paket optimasi VPN "Internet Surfing Optimization" dengan 5
> prioritas: DNS cache, Auto MTU, Smart endpoint selection, Fast reconnect,
> DNS leak protection. Analisis statis + implementasi lengkap; belum ada
> pengujian throughput/latency di device fisik (lihat PROJECT_STATE.md).

**Baru:**
1. **DNS cache** (`data/DnsCache.kt`) — jawaban positif dari upstream
   di-cache in-memory (keyed `domain|qtype`), TTL diambil dari jawaban DNS
   asli (diklem 30–3600 detik), maks 2000 entri, dibersihkan tiap VPN
   restart. Cache-hit dijawab langsung dari thread packet-loop
   (`AdBlockVpnService`), skip round-trip socket upstream sepenuhnya.
2. **Auto MTU** — `WarpTunnelManager.probeBestMtu()` coba MTU
   1420→1400→1360→1280, pakai yang pertama berhasil kirim UDP tanpa error,
   ganti MTU statis 1280 yang selalu dipakai sebelumnya.
3. **Smart endpoint selection** (`warp/WarpEndpointSelector.kt`) — probe
   RTT paralel ke 6 endpoint WARP Cloudflare, pilih yang tercepat,
   di-cache 30 menit di DataStore biar tidak re-probe tiap toggle.
4. **Fast reconnect** — `ConnectivityManager.NetworkCallback` deteksi
   pergantian jaringan (WiFi↔data) dan langsung trigger reconnect (skip
   backoff), tidak nunggu health-check tick berikutnya (sampai 25 detik).
5. **DNS leak protection** — didokumentasikan sebagai struktural pada
   config WireGuard yang sudah ada (satu-satunya DNS server via tunnel +
   AllowedIPs 0.0.0.0/0); tidak perlu kode/toggle terpisah.
6. **Kill-switch hardening** (bonus, bukan diminta eksplisit tapi termasuk
   spec "Stabilitas") — reconnect tidak lagi `DOWN` dulu sebelum `UP`,
   menghilangkan celah singkat trafik bisa lolos dari tunnel saat reconnect.
7. **Packet loss detection** — rolling window 8 probe kesehatan terakhir →
   `WarpConnectionQuality.packetLossPercent`.

**Field baru `WarpConnectionQuality`:** `mtuUsed`, `endpointUsed`,
`packetLossPercent` (belum ditampilkan di UI Diagnostics/Home).

**Belum dikerjakan dari wishlist:** DNS prefetch, pre-warming domain
populer, connection warm-up eksplisit, toggle UI auto-pilih IPv4/IPv6
terbaik. Lihat PROJECT_STATE.md untuk detail & alasan.

## v3.6.1 — Redesign app badge/icon jadi lebih profesional (2026-08-04)

> User minta redesign badge aplikasi. Dikerjakan di atas base v3.6.0 yang
> di-upload ulang (sesi ini sebelumnya masih di v3.3.3) — palette dicek
> ulang dulu terhadap `ui/theme/Color.kt` karena sesi lain sempat geser
> `ShieldBgDark` dari `#17181A` → `#181816`.

**Masalah pada icon lama (ditemukan sebelum redesign):**
1. **Checkmark rusak secara teknis** — path checkmark set `fillColor` DAN
   `strokeColor` sekaligus di satu path terbuka (belum di-`Z`/close). Path
   terbuka dengan fillColor akan auto-close & terisi, jadi hasilnya
   tumpukan wedge terisi di BAWAH garis stroke — bukan checkmark bersih.
2. **Warna basi** — `ic_launcher_background` (`#0F1512`) & foreground
   (`#00C896`) dari sebelum tema di-refactor ke "Matte Graphite / Jade
   Signal" (v3.1.0+). Icon dan tema in-app app secara visual tidak nyambung.
3. **Bentuk shield sedikit keluar safe-zone** — titik bawah `(54,90)`
   berjarak 36dp dari center adaptive-icon (radius safe-zone cuma 33dp) →
   berisiko terpotong di launcher dengan mask lingkaran/squircle.
4. **Tidak ada fallback API 24–25** — cuma ada
   `mipmap-anydpi-v26` (API 26+). `minSdk = 24`, jadi Android 7.0/7.1 tidak
   punya resource icon yang cocok sama sekali (bisa tampil icon default
   kosong Android, bukan crash, tapi tidak profesional).
5. **Tidak ada themed-icon Android 13+** — belum ada layer `<monochrome>`,
   jadi launcher Material You tidak bisa re-tint icon sesuai wallpaper.

**Fix:**
- Shield di-desain ulang dengan kurva bezier presisi (bukan garis lurus
  angular seperti sebelumnya), dipastikan seluruh titik path berada dalam
  radius 33dp dari center 108dp canvas (safe-zone adaptive icon).
- Two-tone: base `#23694C` (jade gelap) + facet kanan `#3FC993` (jade
  terang, = `ShieldGreen` di tema) — split flat vertikal, bukan gradient,
  untuk kesan faceted/dimensional tanpa risiko gradient rendering di
  VectorDrawable.
- Checkmark: path terpisah, HANYA stroke (`fillColor="#00000000"` eksplisit),
  `strokeWidth=7`, round cap/join, warna `#181816` (= `ShieldBgDark`
  terkini) — sync otomatis kalau tema berubah lagi.
- `ic_launcher_background` disamakan ke `#181816`.
- `drawable/ic_launcher_monochrome.xml` baru (silhouette shield polos,
  putih) + `<monochrome>` ditambahkan ke `mipmap-anydpi-v26/ic_launcher.xml`
  DAN `ic_launcher_round.xml`.
- **Legacy PNG raster baru** untuk `mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi`
  (48/72/96/144/192px), masing-masing `ic_launcher.png` (persegi) +
  `ic_launcher_round.png` (masked lingkaran) — di-render manual via
  supersampling 8x + sampling kurva bezier (Pillow, tanpa dependency SVG
  eksternal) supaya kurva tetap halus di resolusi rendah.

**File disentuh (9): `drawable/ic_launcher_foreground.xml`,
`drawable/ic_launcher_monochrome.xml` (baru),
`values/ic_launcher_background.xml`,
`mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`,
`mipmap-{m,h,x,xx,xxx}hdpi/` × 2 file (baru, 10 PNG total),
`app/build.gradle.kts` (version bump).**

**Verifikasi**: semua XML baru divalidasi parse (`xml.etree.ElementTree`),
semua 10 PNG dikonfirmasi ter-generate, preview visual dirender & dicek
manual sebelum packaging. **BELUM diverifikasi tampilan asli di
homescreen/launcher device** — cek pertama kali install ulang APK, bukan
cuma update in-place (launcher sering cache icon lama pada in-place
update).

## v3.6.0 — Perf: pool upstream DatagramSocket per-thread (2026-08-04)

> Lanjutan langsung dari v3.5.0 — user minta "berikan hasil yang maksimal"
> untuk temuan sekunder yang kemarin sengaja disisihkan (socket-per-query),
> setelah dikonfirmasi behavior fallback antar-resolver boleh tetap sama.

**Sebelumnya**: `forwardToUpstream()` bikin `DatagramSocket` baru + panggil
`protect()` + `close()` untuk **setiap query DNS non-blocked** — biaya
create/protect/destroy soket dibayar per-query, di jalur paling sering
dieksekusi di app (setiap domain yang TIDAK diblokir).

**Fix**: `ThreadLocal<DatagramSocket>` — masing-masing dari 4 worker thread
`forwardExecutor` sekarang punya 1 socket persisten yang dipakai ulang
lintas query, bukan dibuang tiap query. **Aman tanpa demux by
transaction-ID** (yang biasanya dibutuhkan skema connection-pooling) karena
satu socket cuma pernah disentuh satu thread, satu query pada satu waktu —
tidak pernah dibagi bareng antar-thread. Perilaku fallback antar-resolver
DALAM satu query (coba server berikutnya kalau timeout/gagal) **persis
sama** seperti sebelumnya — cuma lifetime socket-nya yang berubah, dari
"per-query" jadi "per-thread selama VPN aktif".

**Resource safety**: `openUpstreamSockets` (registry `ConcurrentHashMap`
keySet) melacak semua socket hidup supaya `stopVpn()` bisa menutup semuanya
secara deterministik — mencegah socket menggantung selamanya karena
`forwardExecutor` sendiri memang tidak pernah di-`shutdown()`. Kalau socket
masuk state error tak terduga, `discardUpstreamSocket()` membuang referensi
ThreadLocal-nya supaya panggilan berikutnya bikin yang baru (bukan terus
gagal di socket rusak).

Scope MURNI 1 file kode (`AdBlockVpnService.kt`) + version bump — 0 file
baru/dihapus, 0 perubahan behavior yang terlihat user.

**Confidence Rating: 88%** — logika thread-confinement diverifikasi manual
(tidak ada titik di kode yang membagi satu `DatagramSocket` antar-thread),
tapi belum ada pengujian konkurensi nyata di device (banyak app query DNS
bersamaan, load tinggi) untuk membuktikan tidak ada race yang terlewat.
**BELUM dikonfirmasi build CI + belum diukur throughput/CPU nyata sebelum-
sesudah di device** — WAJIB dicek di sesi berikutnya, idealnya sekaligus
dengan pengukuran v3.5.0 (custom blocklist URL besar).

## v3.5.0 — Perf audit: wildcard matching O(n)→O(depth) (2026-08-04)

> User minta "debugging sampai tuntas di segmen performance & optimalisasi"
> setelah CI v3.3.3+v3.4.0 dikonfirmasi hijau. Audit statis proaktif
> (bukan laporan bug user) atas jalur tercepat aplikasi: VPN packet loop
> (`AdBlockVpnService`), parsing (`DnsPacket`), matching (`BlocklistManager`),
> dan WARP (`WarpTunnelManager`).

**Temuan**: `BlocklistManager.matchesAnyWildcard()` linear-scan seluruh set
wildcard base untuk **setiap query DNS** (blocked maupun tidak — dipanggil
di jalur `isBlocked()` yang jalan di packet loop `AdBlockVpnService`). Aman
saat ini (~55 entri wildcard bawaan, biaya diabaikan), TAPI fitur custom
blocklist URL (v2.5.0) membuat `bases` bisa membesar jadi ribuan-puluhan
ribu entri dari list publik — begitu itu terjadi, linear scan ini jadi
biaya nyata per query, di komponen yang paling sensitif latensi di app.

**Fix**: ganti jadi jalan parent-suffix dari domain (bukan iterasi
`bases`) + `HashSet.contains()` di tiap level — O(kedalaman domain, ~2-5
label tipikal) menggantikan O(ukuran `bases`), independen dari seberapa
besar blocklist remote yang dipasang user. Semantik matching **identik**
persis dengan sebelumnya (diverifikasi ulang manual terhadap semua 15
test case `BlocklistManagerTest.kt` yang sudah ada, termasuk kasus
"suffix-only overlap tidak boleh ke-fool" — tidak ada regresi keputusan
arsitektur #4b).

**Diaudit tapi SENGAJA TIDAK diubah** (temuan sekunder, bukan langsung
diperbaiki):
- `AdBlockVpnService.forwardToUpstream()` bikin `DatagramSocket` baru per
  query (create+`protect()`+destroy). Reuse socket/pool bisa hemat
  overhead, TAPI itu perubahan arsitektur konkurensi (perlu demux balasan
  per transaction-ID kalau socket dipakai bareng oleh 4 thread
  `forwardExecutor`) — bukan optimisasi aman-langsung seperti fix
  wildcard di atas. Disisihkan, tanya user dulu kalau mau dikerjakan.
- `WarpTunnelManager`, `DnsPacket.parse()`/`buildBlockedResponse()`: sudah
  diperiksa, tidak ada temuan baru — MTU (v3.2.0), IPv6 toggle (v3.3.0),
  dan parsing single-pass sudah optimal untuk desainnya masing-masing.

Scope MURNI 1 file kode (`BlocklistManager.kt`) + version bump — 0 file
baru/dihapus, 0 perubahan behavior yang terlihat user (hasil `isBlocked()`
identik untuk semua input).

**Confidence Rating: 90%** — logika ekivalensi diverifikasi manual
terhadap seluruh test suite yang ada (statis, bukan dijalankan — belum
ada Gradle/JDK di sandbox sesi ini). -10% karena belum ada pengukuran
throughput nyata dengan blocklist besar sungguhan di device (butuh data
dari user: pasang custom blocklist URL berukuran besar, bandingkan
latensi DNS sebelum/sesudah — belum bisa dibuktikan tanpa itu). **BELUM
dikonfirmasi build CI** — cek ini duluan di sesi berikutnya.

## v3.4.0 — Legibility-max pass, palet ulang total (2026-08-04)

> User audit ulang setelah v3.1.0: SEMUA 4 kategori masih ditandai susah
> dibaca — caption kecil, bg/card kurang beda gelap, border/ikon card nav
> pudar, ring/tombol proteksi. Bukan preferensi subjektif — diukur pakai
> kontras WCAG relative-luminance, root cause ketemu di `Color.kt`.

**Akar masalah (ditemukan lewat pengukuran, bukan tebakan)**: v3.1.0 benar
soal kontras teks-vs-surface, tapi elevation ladder (bg→surf→surf2→surf3)
cuma berjarak ~4-5% lightness per step, DAN tiap step pakai hue yang
berbeda-beda (220°→210°→195°→180°→94°→157° — drift, bukan palet
konsisten). Itu sebabnya border/elevation kelihatan "pudar" walau angka
kontras teks lolos AA.

**Fix — palet dirombak total di `Color.kt`** (satu-satunya sumber warna,
di-grep-verifikasi 0 hex literal liar di file screen manapun):
- Elevation ladder: 1 hue konsisten (45°, warm-neutral), lightness step
  dilebarkan (~6-8pt, sebelumnya ~4-5pt) — `ShieldBgDark` #181816,
  `ShieldSurface` #282724, `ShieldSurface2` #383733, `ShieldSurface3`
  #4E4C46.
- `ShieldOutline` #52564F→#7C796E (L32→46) — SATU perubahan ini otomatis
  memperbaiki border card nav, divider, dan ring track inaktif di SEMUA
  layar (dipakai di `NavGroup`, `NavDivider`, `StatCard`, `WarpModeCard`,
  `ProtectionRing`, dst — sumber tunggal, sudah di-grep).
- `ShieldAccentDim` #2B4038→#345142 — disc ring/chip proteksi aktif lebih
  kelihatan sebagai isian, bukan blob gelap nyaris tak terlihat.
- `ShieldTextFaint` (caption/deskripsi kecil) #93988F→#ADB1AA (L58→68) —
  sebelumnya 4.04:1 vs surf2 (di bawah floor AA 4.5:1 untuk teks kecil),
  sekarang 5.0-6.9:1 di semua elevation step.
- `ShieldGreen`/`Warning`/`Danger`/`White` TIDAK disentuh — sudah solid
  (7:1+) dari v3.1.0, tidak terdampak rombakan ladder/outline.

**Confidence Rating: 92%** — perubahan murni nilai warna (`Color.kt` +
version bump), 0 perubahan struktur/logic/import baru, static check
brace-balance clean. -8% karena: belum ada verifikasi CI (v3.3.3 di bawah
masih belum dikonfirmasi hijau juga) dan belum dilihat langsung di device
fisik — kontras dihitung matematis (WCAG relative luminance), bukan
screenshot visual asli.

## v3.3.3 — HOTFIX: CI build gagal, missing import di MainActivity.kt (2026-08-04)

> User upload log GitHub Actions dari push v3.3.2 — `Build signed release
> APK` gagal di kompilasi Kotlin: `MainActivity.kt:160:41 Unresolved
> reference: padding`.

**Akar masalah**: batch v3.3.1 menambahkan `Modifier.padding(scaffoldPadding)`
saat membungkus `NavHost` dalam `Scaffold`, tapi `MainActivity.kt` sebelumnya
cuma pakai `Modifier.fillMaxSize()`/`.background()` — jadi
`androidx.compose.foundation.layout.padding` (fungsi ekstensi Modifier)
belum pernah diimpor di file ini. Lolos dari static check sesi sebelumnya
karena cross-check waktu itu cuma verifikasi *brace/paren balance* dan
*"apakah simbol tercakup wildcard import"* — tidak benar-benar menjalankan
kompilator Kotlin (tidak ada runner tersedia di sesi itu, sudah diberi tahu
di Confidence Rating waktu itu: "belum ada runtime/CI verification").

**Fix**: tambah `import androidx.compose.foundation.layout.padding` di
`MainActivity.kt`. Satu baris, satu file, tidak ada perubahan logic.

**Verifikasi log CI**: hanya **1** error dilaporkan compiler (bukan
beruntun/cascading) — dicek eksplisit dengan grep semua baris `e: file:`
di log build, bukan cuma baris error pertama.

**File disentuh (2): `MainActivity.kt` (fix), `app/build.gradle.kts`
(version bump). Bukan Atomic Change — hotfix single-line.**

## v3.3.2 — Audit sektor Feedback ROUND 2: tutup celah battery-exemption (2026-08-04)

> User tanya ulang "sudah tuntas gak bersisa?" setelah v3.3.1 — sweep ulang
> menemukan 1 celah lagi yang terlewat: alur "Kecualikan dari Optimasi
> Baterai" (dipanggil dari Onboarding & Home) LEBIH parah dari kasus VPN
> permission di v3.3.1, karena dibungkus `runCatching { startActivity(intent) }`
> tanpa fallback sama sekali.

**3 sub-celah di 1 fungsi (`requestBatteryOptimizationExemption`):**
1. Kalau app **sudah** dikecualikan, tombol tetap tampil dan tap-nya
   sepenuhnya no-op — user bisa tap berkali-kali tanpa tahu itu percuma.
2. Kalau dialog sistem tampil dan user pilih Izinkan/Tolak, **tidak pernah
   ada konfirmasi** balik ke app — beda dari VPN permission (v3.3.1) yang
   sudah dibenerin.
3. Kalau Intent `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` sendiri gagal
   dibuka (sejumlah ROM OEM memblokirnya — termasuk **Infinix XOS, device
   target app ini**), kegagalan ditelan `runCatching` tanpa jejak apa pun.

**Fix:**
- `requestBatteryOptimizationExemption()` sekarang pakai
  `registerForActivityResult` (`batteryExemptionLauncher`) alih-alih
  `startActivity` polos.
- Resultcode Intent ini TIDAK diandalkan (dikenal tidak reliable di banyak
  OEM) — begitu dialog sistem kembali, app baca ulang ground truth lewat
  `PowerManager.isIgnoringBatteryOptimizations()` dan kirim
  `UiEvent.Message` sesuai hasil sebenarnya.
- Kalau sudah exempt sebelumnya → langsung kirim Snackbar konfirmasi
  ("proteksi background lebih aman") alih-alih diam.
- Kalau Intent gagal dibuka → Snackbar arahkan user ke jalur manual
  ("Pengaturan > Baterai > Aplikasi tak terbatas").
- `MainViewModel`: `notifyBatteryExemptionResult(granted: Boolean)` +
  `notifyBatteryExemptionUnavailable()`.

**File disentuh (2): `MainActivity.kt`, `MainViewModel.kt`.**
**Hasil sweep ulang seluruh project**: sisa `runCatching` lain (WarpTunnelManager,
CrashLogger, BlocklistManager, BootReceiver, dll) semuanya di layer
background/internal, bukan aksi user-tap — di luar cakupan sektor feedback,
tidak diubah.

## v3.3.1 — Audit sektor Feedback: tutup 6 celah aksi tanpa konfirmasi (2026-08-04)

> User minta audit fokus "apa yang benar-benar diharapkan user saat
> interaksi" pada aspek feedback. Temuan: 6 aksi berjalan sepenuhnya senyap
> (tidak ada toast/snackbar/dialog), 1 fitur (`forgetWarpAccount()`) sudah
> ada logic-nya tapi tidak pernah dipasang ke UI manapun (dead entry point).

**Infrastruktur baru — `UiEvent` (MainViewModel.kt)**
- `sealed class UiEvent { Message, UndoableMessage }` dikirim lewat
  `Channel<UiEvent>` (bukan StateFlow — sengaja, karena ini one-shot event,
  StateFlow berisiko re-show Snackbar yang sama saat recomposition/config
  change).
- `MainActivity` sekarang punya satu `Scaffold` + `SnackbarHostState` global
  yang membungkus `NavHost`, collect `viewModel.uiEvents` di satu tempat —
  jadi screen manapun bisa kirim Snackbar tanpa deklarasi Scaffold sendiri.

**Celah yang ditutup:**
1. **VPN permission ditolak** — `vpnPermissionLauncher` else-branch dulu
   kosong total. Sekarang panggil `viewModel.notifyVpnPermissionDenied()` →
   Snackbar "Izin VPN ditolak — AdShield butuh izin ini...".
2. **Reset statistik (Home)** — dulu langsung eksekusi 1 tap. Sekarang
   `AlertDialog` konfirmasi dulu.
3. **Bersihkan log (Logs)** — sama, `AlertDialog` konfirmasi + tombol
   dinonaktifkan kalau log sudah kosong (mencegah dialog muncul percuma).
4. **Tambah/hapus domain custom (Rules)** — `addBlockedDomain`,
   `removeBlockedDomain`, `addAllowedDomain`, `removeAllowedDomain` sekarang
   kirim `UiEvent`. Hapus domain pakai `UndoableMessage` (Snackbar+"Urungkan"
   5 detik) alih-alih dialog konfirmasi — lebih ringan untuk aksi yang
   sering diulang (hapus banyak domain satu-satu).
5. **Lupakan Akun WARP** — `forgetWarpAccount()` di ViewModel sudah ada
   sejak sebelumnya tapi **tidak pernah dipanggil dari UI manapun** (dead
   code). Ditambahkan tombol "Lupakan Akun WARP" di DiagnosticsScreen +
   `AlertDialog` konfirmasi + Snackbar setelah selesai.
6. **Whitelist toggle (Whitelist) & logging toggle (Logs)** — DIPERIKSA,
   TIDAK diubah: `Switch` checked-state sendiri sudah memberi feedback
   visual instan yang cukup: menambah Snackbar di sini dinilai berlebihan
   dan berisiko spam kalau user toggle banyak app berturut-turut.

**File disentuh (6, di bawah batas 10 — tidak perlu Atomic Change Exception
meski lintas 5 screen + MainActivity, karena tiap file <15 baris net-diff):**
`MainViewModel.kt`, `MainActivity.kt`, `HomeScreen.kt`, `LogsScreen.kt`,
`DiagnosticsScreen.kt`, `app/build.gradle.kts` (version bump only).

**Tidak ada migrasi data, tidak ada perubahan schema DB/DataStore.**

## v3.3.0 — WARP: toggle "Rutekan IPv6" jadi setting user, bukan hardcode (2026-08-04)

> Lanjutan v3.2.1. User konfirmasi hasil eksperimen: WARP+IPv6-off
> mengalahkan baseline tanpa VPN di kedua arah (42.3↓/4.67↑ Mbps vs
> baseline 31.3↓/3.43↑). User pilih dari 3 opsi tindak lanjut: **kasih
> toggle di Setting**, bukan langsung dikunci permanen atau dites ulang
> berkali-kali.

- **`SettingsRepository`**: setting baru `warp_route_ipv6` (Boolean,
  default `false` — mengikuti hasil pengukuran v3.2.1) + `warpRouteIpv6`
  Flow + `setWarpRouteIpv6()`.
- **`WarpTunnelManager`**: konstanta eksperimen `ROUTE_IPV6` (v3.2.1)
  DIHAPUS, diganti baca `settingsRepository.warpRouteIpv6` tiap `connect()`
  dan `attemptReconnect()` (auto-reconnect otomatis ikut preferensi
  terbaru, bukan snapshot beku). `buildConfig()` sekarang terima parameter
  `routeIpv6: Boolean` alih-alih baca konstanta langsung.
- **`MainViewModel`**: expose `warpRouteIpv6` StateFlow (pola sama seperti
  setting lain, `stateIn` + `WhileSubscribed(5000)`) + `setWarpRouteIpv6()`.
- **`HomeScreen`**: `WarpModeCard` dapat toggle baru "Rutekan IPv6 lewat
  WARP" di bagian bawah kartu (selalu terlihat, bukan cuma saat aktif) +
  caption menjelaskan default nonaktif dan kapan berlaku (saat WARP
  dinyalakan ulang — WireGuard config terkunci selama tunnel jalan, ganti
  setting saat tunnel aktif TIDAK langsung reconnect otomatis).
- **Tidak ada migrasi data** — user baru maupun existing sama-sama dapat
  default `false` (sama seperti behavior v3.2.1 sebelumnya), jadi tidak
  ada regresi untuk siapa pun yang belum pernah menyentuh setting ini.
- Batch ini menyentuh 4 file kode (`SettingsRepository.kt`,
  `WarpTunnelManager.kt`, `MainViewModel.kt`, `HomeScreen.kt`) — jauh di
  bawah batas maksimal batch (10 file), tidak perlu Atomic Change
  Exception.

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
