# PROJECT_STATE.md (Claude-facing, bukan untuk user)

Baca file ini SEBELUM lanjut kerja di proyek ini pada sesi baru mana pun.

## STATUS PROYEK: AKTIF — v4.3.0, Setting tema kustom "Radical Skeuomorphism Dark" (2026-08-09)

User minta tombol pemilih tema di layar Setting, dengan tema baru dikonfigurasi
100% dari `compose-skeuomorphism-radical-literal-dark-performance.md` (spec
diupload user, bukan bagian repo — nilai token disalin verbatim ke
`ui/theme/SkeuoTokens.kt`, JANGAN diubah tanpa mengecek ulang doc itu kalau
sesi depan diminta "sesuaikan lagi dengan spec").

Detail lengkap perubahan: lihat CHANGELOG.md v4.3.0. Ringkasan arsitektur:
- Theme sekarang dual-mode: `AdShieldTheme(themeMode, content)` di
  `ui/theme/Theme.kt` pilih antara `AdShieldColorScheme` (default lama,
  TIDAK diubah) dan `SkeuoColorScheme` (baru) + shape ladder masing-masing.
  `themeMode` default ke `AppTheme.DEFAULT` — instalasi lama & setiap call
  site yang belum di-update tetap render identitas AMOLED lama persis sama.
- Sumber kebenaran pilihan tema: `SettingsRepository.appTheme` (DataStore
  string key `app_theme`) → `MainViewModel.appTheme` (StateFlow) →
  dikoleksi sekali di `MainActivity.setContent{}` → diteruskan ke
  `AdShieldTheme(themeMode = appTheme)`. Layar baru `SettingsScreen.kt`
  (route NavHost `"settings"`, dibuka dari `HomeScreen` NavRow "Pengaturan
  Aplikasi") adalah satu-satunya penulis lewat `viewModel.setAppTheme()`.
- Bevel/fisik primitives (`Modifier.skeuoRaised()`/`skeuoRecessed()` di
  `SkeuoTokens.kt`, dipakai `SkeuoButton.kt`) SENGAJA dibangun dari
  Shape+Brush+shadow()/border() saja — TIDAK ada Canvas custom, TIDAK ada
  blur, TIDAK ada animasi looping — sesuai spec §11-14/§23 (performance-first
  skeuomorphism). Kalau nanti diminta bikin komponen skeuo baru
  (SkeuoSwitch/SkeuoSlider/SkeuoKnob dari spec §7-9), pola yang sama harus
  diikuti — jangan lompat ke Canvas/bitmap texture kecuali benar-benar perlu.
- `SkeuoButton` menandai state SELECTED lewat warna ikon/teks (accent), bukan
  cuma lewat depth/scale — spec §19 eksplisit melarang state depth-only.

**Kalau sesi berikutnya diminta perluas skeuo theme** (switch/slider/knob
fisik beneran, bukan cuma tombol/warna): itu pekerjaan baru dari komponen
yang belum ada (`SkeuoSwitch.kt`, `SkeuoSlider.kt`, `SkeuoKnob.kt`,
`SkeuoSurface.kt`, `SkeuoPanel.kt` di spec §20 struktur folder belum semuanya
dibuat — baru `SkeuoButton.kt` yang ada). Jangan asumsikan sudah lengkap.

## STATUS SEBELUMNYA: v4.2.0, "Radikal Perf" batch 2 — WARP (2026-08-09)

User minta WARP juga di-dongkrak radikal (lanjutan v4.1.0 di DNS mode). Audit
modul `warp/` — sebagian besar sudah teraudit berat sebelumnya (probing
konkuren, cache endpoint/MTU 30 menit, event-driven combine) dan jalur
paket WARP sendiri ada di native WireGuard GoBackend (di luar kode Kotlin) —
jadi TIDAK ada "packet loop" seperti DNS mode yang bisa dioptimasi lagi di
sisi app. Satu fix nyata ditemukan & diterapkan:

- `WarpTunnelManager.probeTrace()` — hapus `connection.disconnect()` per
  health-check (tiap 25 detik selama tunnel nyala) — kelas bug sama persis
  dengan `DohClient` sebelum v4.1.0 (disconnect() mematikan keep-alive/
  connection-pool). JANGAN tambahkan `disconnect()` balik ke sini tanpa
  sadar itu menghilangkan reuse koneksi lagi. TIDAK butuh protect()ing
  SSLSocketFactory di sini (beda dari DohClient) karena probe ini jalan
  LEWAT tunnel WARP yang sudah UP, bukan lewat forwarder DNS mode.

Kalau sesi berikutnya diminta cari perf win WARP lagi: endpoint-probe timeout
(800ms, `WarpEndpointSelector.PROBE_TIMEOUT_MS`) SENGAJA tidak diubah di
batch ini — mengubahnya berisiko salah klasifikasi endpoint yang genuinely
lambat-tapi-jalan sebagai "unreachable", beda kelas risiko dari fix
connection-reuse murni di atas; perlu diskusi eksplisit dgn user dulu kalau
mau disentuh.

## STATUS SEBELUMNYA: v4.1.0, "Radikal Perf" batch 1 — DNS (2026-08-09)

User minta dongkrak performa "radikal". Audit fokus ke hot path (packet
loop + upstream forward), BUKAN kosmetik — lihat CHANGELOG.md v4.1.0 untuk
detail penuh. Ringkasan 2 fix nyata:

1. `DohClient` — `SSLSocketFactory` sekarang di-cache per-VpnService-instance
   (lihat kdoc di file). SEBELUM ini, fix v3.25.0 (hapus `disconnect()`
   per-query) tidak pernah benar-benar menyala karena factory baru dibuat
   tiap query — kalau ada perubahan lain ke `queryOne()`/`protectingSocketFactory()`
   nanti, JANGAN balikin ke "factory baru per call" tanpa sadar itu
   menghilangkan connection-reuse lagi.
2. `DnsQueryLogger` — buffer in-memory (`AtomicLong` + `ConcurrentLinkedQueue`)
   + flush batched tiap 3 detik (`FLUSH_INTERVAL_MS`), BUKAN lagi 1
   `dataStore.edit{}` + 1 coroutine launch per query. `start()`/`stop()`
   WAJIB dipanggil simetris di `AdBlockVpnService.startVpn()`/`stopVpn()`
   (sudah di-wire) — kalau lupa `stop()`, buffer di RAM tidak pernah
   sampai ke disk saat VPN berhenti.

## STATUS SEBELUMNYA: v4.0.0, major cleanup (2026-08-09)

User eksplisit minta: proyek ini "kegemukan" untuk cakupan yang sebenarnya
cuma 2 fitur utama (DNS Ad-Block + WARP) + penunjang esensial — semua yang
tidak berhubungan dieliminasi. Batch ini (v4.0.0) adalah Atomic Change
besar (>10 file, lintas modul), dikerjakan atas izin eksplisit user untuk
penghapusan massal.

## Apa yang DIHAPUS di v4.0.0 (JANGAN ditambah balik tanpa user minta lagi)

1. **`app/libs/libXray.aar` (96.7MB) + seluruh `app/src/androidTest/.../xray/`
   (6 file probe test) + 2 job CI (`libxray-poc`, `libxray-invoke-probe`).**
   Investigasi Xray-core untuk WARP sudah ditutup permanen sebelumnya
   (won't-fix — AAR terbukti tidak bisa dipakai). File-file ini murni
   dokumentasi investigasi mati, tidak pernah dipakai app produksi.
2. **IKEv2 (protokol VPN ke-3) — dihapus TOTAL**, bukan cuma di-disable:
   `protocol/IkeV2VpnEngine.kt`, `data/VpnProfileRepository.kt` (hanya
   dipakai IKEv2 + OpenVPN/Shadowsocks yang memang tak pernah
   diimplementasi), varian `IkeV2` di `VpnProtocolConfig.kt`,
   `AppMode.IKEV2/OPENVPN/SHADOWSOCKS`, `IkeV2ModeCard`+`IkeV2ProfileDialog`
   di `HomeScreen.kt`, seluruh wiring di `MainActivity.kt`/
   `MainViewModel.kt`. Scope app sekarang PERSIS 2 mode:
   `AppMode.DNS_ADBLOCK` dan `AppMode.WARP_TUNNEL`.
3. **`ui/components/Tactile*.kt` (4 file) + `ui/theme/TactileTokens.kt`** —
   0 call site sejak dibuat, dead code murni.

**Kalau user minta salah satu ini balik**: itu fitur baru dari nol, bukan
"restore" — `AppMode`/`VpnProtocolConfig` sudah disederhanakan total ke 1
varian (`Warp`), jangan asumsikan kode lama masih konsisten.

## Yang TETAP ada (2 fitur utama + penunjang esensial)

- **DNS Ad-Block** (`vpn/`, `vpn/dns/`, `data/BlocklistManager.kt`,
  `data/DnsCache.kt`).
- **WARP/WireGuard** (`warp/`, `protocol/` — hanya varian `Warp`).
- **Penunjang esensial**: whitelist per-app, aturan kustom (`RulesScreen`),
  log query (`LogsScreen`), Diagnostik (`DiagnosticsScreen` +
  `ResourceMonitor`), Crash Logger (`CrashLogger.kt`, WAJIB — lihat #12),
  QS Tile (`qs/`), App Shortcuts, Boot/watchdog receivers, Onboarding.

## Keputusan arsitektur utama (JANGAN dilanggar tanpa diskusi eksplisit)

1. VPN DNS-mode hanya menunnel DNS (`addRoute` ke `10.111.222.1/32`),
   BUKAN full-tunnel. Upgrade ke IP/SNI blocking = perubahan besar, tanya
   user dulu.
2. minSdk 24, compile/target SDK 34. Package `com.fdzaki.adshield`.
3. Blocklist merge logic di `BlocklistManager`, bukan VpnService.
4. Matching = exact-match default; wildcard hanya via prefix `*.domain.com`
   eksplisit, di-walk sebagai parent-suffix (bukan iterasi linear).
5. TIDAK ADA auto-switch WARP↔DNS saat salah satu gagal — user pilih
   eksplisit, auto-fallback menurunkan jaminan keamanan tanpa consent.
6. Whitelist per-app via `getConnectionOwnerUid()` (API 29+). Critical
   allowlist selalu override blocklist — jangan dihapus.
7. WARP (`warp/`) = engine terpisah total dari AdBlockVpnService, pakai
   `com.wireguard.android:tunnel`, full-tunnel. Dua mode tidak pernah
   jalan bersamaan (mutual exclusion di MainActivity). `EXTRA_MODE_SWITCH`
   pada intent STOP jangan dihapus (cegah race activeMode).
8. WARP watchdog+quality probe di `WarpTunnelManager`, sumber kebenaran =
   probe nyata `cdn-cgi/trace`, bukan cuma `Tunnel.State.UP`.
   `WARP_MTU = 1280`, `warpRouteIpv6` default `false` (data speedtest nyata
   di operator seluler tertentu).
9. App Shortcuts dinamis via `ShortcutsManager` saja, dipanggil dari
   `AdShieldApp`. Baca mode aktif via suspend `.first()`, bukan `.value`.
10. Auto-update blocklist (`BlocklistUpdateWorker` di `BlocklistManager`),
    domain remote di set terpisah dari default+custom, cache lokal
    write-then-rename, interval 24 jam.
11. `AdBlockVpnService` — `loopExecutor` (packet loop) vs `forwardExecutor`
    (forward upstream) TERPISAH, jangan disatukan. Upstream socket
    di-pool per-thread (`ThreadLocal`), `closeUpstreamSockets()` wajib di
    `stopVpn()`.
12. `CrashLogger.kt` — `install()` sekali di baris PERTAMA
    `AdShieldApp.onCreate()`, selalu chain `previousHandler`, semua I/O
    try-catch diam-diam gagal, retention FIFO maks 50, MediaStore API 29+
    (tanpa `WRITE_EXTERNAL_STORAGE`).
13. QS Tile toggle wajib 100% background via `onClick()` kecuali consent
    VpnService belum pernah di-grant.
14. `AdBlockVpnService` = orchestrator-only; kolaborator baru masuk
    `vpn/dns/` (spesifik-DNS) atau `vpn/`, jangan inline di class ini.

## Yang HARUS dikerjakan di batch berikutnya (prioritas)

**PALING BARU (2026-08-09, v4.0.0): verifikasi CI + device SEBELUM apa pun
lain.**
1. Cek CI — belum pernah di-push sejak cleanup ini. Risiko utama:
   `MainActivity`/`MainViewModel`/`HomeScreen` diedit surgically (banyak
   string-replace manual) — pastikan `assembleRelease` sukses dulu.
2. Device: HomeScreen HANYA 2 kartu mode (DNS/WARP), tidak ada lagi kartu
   IKEv2. Toggle masing-masing normal.
3. Device: APK size harus turun signifikan (libXray.aar sebelumnya ikut
   ter-bundle native libs-nya ke APK walau tak dipanggil kode produksi;
   sekarang dependency itu sudah hilang total).
4. Semua item roadmap lama yang menyebut Xray-core/IKEv2/OpenVPN/
   Shadowsocks SUDAH TIDAK RELEVAN — jangan diangkat lagi kecuali user
   eksplisit minta salah satunya balik sebagai fitur baru.

**Item lama yang masih relevan (belum pernah dikonfirmasi di device):**
WARP end-to-end di device fisik, whitelist per-app di Android <10 vs ≥10,
unit test dijalankan (`testDebugUnitTest`), watchdog AlarmManager survive
Recents-swipe di OEM agresif.

## Struktur package singkat (pasca v4.0.0)

```
qs/            DnsTileService, WarpTileService
vpn/           AdBlockVpnService (VpnService, packet loop), DnsPacket
vpn/dns/       UpstreamForwarder, DnsPrefetcher, AppUidWhitelistChecker,
               DnsPacketLoop, DnsQueryLogger
warp/          WarpTunnelManager, WarpRegistrationClient,
               WarpAccountRepository, WarpForegroundService, WarpAccount
protocol/      VpnEngine/VpnEngineState/VpnProtocolConfig (Warp-only) +
               WarpVpnEngineAdapter
data/          BlocklistManager, SettingsRepository (activeMode),
               InstalledAppsRepository, data/db/ (Room: log domain)
receiver/      BootReceiver, RestartReceiver, WarpRestartReceiver
util/          Constants, AppMode (2 mode), ShortcutsManager, CrashLogger,
               ResourceMonitor
ui/            MainViewModel, ui/screens/ (Home, Whitelist, Rules, Logs,
               Diagnostics, Onboarding), ui/theme/
```

## Riwayat sebelum v4.0.0 (ringkas — detail penuh di git history)

Proyek sempat berkembang jadi VPN client multi-protokol (OpenVPN
dibatalkan karena GPL/AGPL; IKEv2 sempat diimplementasi+diwire penuh lalu
DIHAPUS di v4.0.0 karena di luar scope; Shadowsocks/Xray-core investigasi
berujung won't-fix, DIHAPUS di v4.0.0). Insiden teknis dari era itu semua
sudah di-fix sebelum v4.0.0 dan tidak relevan lagi: executor tunggal
packet-loop+forward, total DNS failure dari resolver diversity hilang,
MTU salah ketik, nested-folder ZIP repack, coroutine scope leak di
Warp/IKEv2 adapter. Detail lengkap: CHANGELOG.md / git log.
