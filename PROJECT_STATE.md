# PROJECT_STATE.md (Claude-facing, bukan untuk user)

Baca file ini SEBELUM lanjut kerja di proyek ini pada sesi baru mana pun.

## STATUS PROYEK: AKTIF — v4.7.5, notif WARP connected: latency/loss/data jadi headline (2026-08-13)

**User minta** (dari screenshot notif): baris utama notif connected
seringnya nampilin "bukan WARP resmi" bukan info berguna — ganti dengan
latency dkk.

**Bukan bug, salah prioritas info:** `trafficConfirmed` sering stuck
`false` karena keterbatasan library (lihat kdoc `WarpConnectionQuality.
Level`, v3.28.0) — jadi baris utama JAUH lebih sering nampilin status
konfirmasi itu ketimbang angka latency, padahal `WarpConnectionQuality`
selalu punya latency/packet-loss/rx/tx yang valid selama tunnel UP &
sudah diprobe minimal 1x.

**Diubah (`WarpForegroundService.kt`, 1 file):**
- `buildNotification()` cabang "connected & sudah diprobe": `contentText`
  (baris collapsed) sekarang SELALU `"{latency} ms • {loss}% loss •
  {rx}↓ {tx}↑"`, TIDAK lagi cabang berdasar `trafficConfirmed`.
- `trafficConfirmed` + endpoint/MTU dipakai pindah ke `BigTextStyle`
  (`expandedText`) — tetap ditampilkan pas notif di-expand, cuma bukan
  headline. `Diagnostik` screen TIDAK disentuh — masih sumber kebenaran
  detail lengkap kalau user mau lihat lebih jauh.
- `formatBytes()` baru (private, di `WarpForegroundService`) — binary
  (1024-based: KB/MB/GB) biar konsisten sama cara Android sendiri
  ngelaporin data usage, BUKAN decimal/SI 1000-based.
- State "connecting"/"memeriksa kualitas"/"reconnecting" (belum ada data
  buat ditampilkan) TIDAK diubah — cuma cabang "connected & ada data".

**PENTING untuk sesi lanjutan:** kalau nanti nambah field baru ke
`WarpConnectionQuality` yang "berguna buat user lihat sekilas" (mis.
jitter, server region), taruh di `contentText` (headline) kalau memang
selalu punya nilai valid saat connected; taruh di `expandedText` kalau
sifatnya diagnostik/sekunder (mirip pola `trafficConfirmed`/endpoint di
atas) — jangan taruh keduanya di headline sekaligus, notif collapsed
Android motong ke ~1 baris di kebanyakan device jadi field akan
ke-truncate kalau kepanjangan.

## STATUS SEBELUMNYA: v4.7.4, hotfix notif WARP nyangkut "Menyambungkan…" (2026-08-13)

**User lapor:** notif WARP masih "Menyambungkan ke Cloudflare WARP…"
(searching) walau toggle sudah dimatikan manual dari UI.

**Root cause:** `WarpForegroundService.observeQualityForNotification()`'s
`combine(tunnelManager.state, tunnelManager.quality).collect{}` tidak
pernah di-cancel sampai `onDestroy()` — yang baru jalan setelah message
queue Service kosong, BUKAN serentak dengan `stopForeground()`/
`stopSelf()` di ACTION_STOP. `warpEngine.disconnect()` (dipanggil di jalur
stop yang sama, SEBELUM `stopForeground()`) men-drive
`tunnelManager.state` ke `DOWN` lewat `Tunnel.onStateChange()` — collector
lama itu ikut menangkap emission DOWN itu dan `notify()` ULANG notifikasi.
`buildNotification()`'s logic `state != UP -> "Menyambungkan…"` awalnya
dimaksudkan buat startup (state DOWN sebelum UP pertama kali), tapi
kepicu juga oleh DOWN saat SHUTDOWN — teks yang sama, konteks beda total.
notify() ulang ini kejadian PERSIS di jendela setelah
`stopForeground(STOP_FOREGROUND_REMOVE)` sudah menghapus notif asli, jadi
notif "connecting" hasil notify() ulang itu jadi notif berdiri sendiri
yang tidak terikat foreground-service state apa pun lagi — tidak pernah
hilang sendiri sampai user swipe manual dari tray.

**Fix (`WarpForegroundService.kt`, 1 file, bukan Atomic Change — cukup 1
file untuk fix ini):**
- `notificationJob` (referensi ke Job collector) di-cancel EKSPLISIT di
  AWAL blok ACTION_STOP, SEBELUM `warpEngine.disconnect()` dipanggil —
  bukan menunggu `scope.cancel()` di `onDestroy()` yang telat.
- Flag `@Volatile stopping` sebagai guard kedua DI DALAM `collect{}`
  (`if (stopping) return@collect`) — menutup jendela balapan kalau ada
  emission yang SUDAH terlanjur in-flight lewat `combine()` tepat saat
  `cancel()` diproses (cancel Job Kotlin tidak instan-interrupt kalau
  collector body sedang di tengah eksekusi).
- `onDestroy()` di-hardening dengan guard yang sama (`stopping = true` +
  `notificationJob?.cancel()` sebelum `scope.cancel()`) untuk jalur
  teardown yang TIDAK lewat ACTION_STOP (mis. process death setelah
  watchdog `onTaskRemoved`) — supaya konsisten, bukan cuma nutup 1 jalur.

**PENTING untuk sesi lanjutan:** kalau nanti nambah sumber emission baru
ke `observeQualityForNotification()` (state tambahan, quality field baru,
dll), INGAT pola ini — `notify()` dengan `WARP_NOTIF_ID` yang sama BISA
"membangkitkan" notif yang sudah di-`stopForeground(REMOVE)` selama
collector-nya belum benar-benar berhenti. Jangan asumsikan
`stopForeground()`/`stopSelf()` langsung mematikan semua coroutine
Service — keduanya cuma request, bukan sinkron.

**Verifikasi:** comment balance file disubah OK, regex scan bug-class
v4.7.1 (`*/` nyelip di kdoc) diulang ke seluruh repo — bersih.

## STATUS SEBELUMNYA: v4.7.3, efek "timbul" dimaksimalkan (2026-08-11)

**User minta:** maksimalkan efek raised/embossed di seluruh sistem Tactile.
Semua perubahan di token SHARED (`TactileTokens.kt` + `Color.kt`) — otomatis
nyebar ke `TactileSurface`/`TactileButton`/`ProtectionRing` (semua baca
`TactileTokens.elevationRaised` yang sama) tanpa sentuh screen satu-satu.

**Diubah:**
- `TactileTokens.kt` — `elevationRaised` 6dp -> **14dp** (>2x), `bevelWidth`
  1dp -> **1.5dp**. `elevationRaisedPressed` SENGAJA tetap 1dp — kontras
  rest(14dp) vs pressed(1dp) yang bikin animasi tekan kerasa "collapse
  dramatis", bukan cuma bayangan lebih tebal merata.
- `Color.kt` — kontras gradient panel + tepi bevel dilebarkan:
  `PanelHighlight`/`PanelShadow` (fill gradient), `BevelHighlight`/
  `BevelShadow` (tepi bevel, alpha ~2x), `PanelRecessedBase`/
  `PanelRecessedHighlight` + `BevelRecessedHighlight`/`BevelRecessedShadow`
  (groove/recessed, dilebarkan proporsional biar tetap kebaca "di bawah"
  panel raised yang sekarang jauh lebih menonjol).
- `TactileSwitch.kt` — thumb shadow literal 3dp -> **6dp** (satu-satunya
  elevation yang TIDAK baca `TactileTokens` — konstanta lokal, ikut
  dinaikkan manual biar konsisten).

**TIDAK diubah:** semua warna STATE (`ShieldGreen`, `ShieldDanger`, dst),
`LocalTrimAccent` scope dari v4.7.2 — ini murni intensitas depth cue, bukan
warna/scope.

**Verifikasi:** comment balance semua file disentuh OK, regex scan bug-class
v4.7.1 diulang ke seluruh repo — bersih.

## STATUS SEBELUMNYA: v4.7.2, perluas cakupan trim accent (2026-08-11)

**User eksplisit minta** (screenshot toggle ON, cuma icon nav yang berubah):
perluas `LocalTrimAccent` ke SEMUA elemen dekoratif, dipilih opsi "paling
kerasa" dari 3 opsi yang diajukan (bukan cuma icon tint v4.7.0).

**Diubah (3 file kode + PROJECT_STATE.md + CHANGELOG.md + versionCode):**
- `TactileSurface.kt` — bevel border DEFAULT (bukan `accentActive`) sekarang
  `LocalTrimAccent`, bukan `BevelHighlight`/`BevelShadow` netral. Dampaknya
  LUAS: karena ini komponen shared, SEMUA card di SEMUA layar yang sudah
  wired (v4.6.0) otomatis dapat tepi bertinta tema tanpa sentuh satu-satu
  layar. `accentActive` TETAP `ShieldGreen` hardcoded, tidak berubah — jadi
  WarpModeCard saat aktif TETAP hijau, bukan ikut tema.
- `TactileSwitch.kt` — param baru `accentColor: Color = ShieldGreen`
  (default = 0 perubahan di SEMUA call site lama: WARP, IPv6, whitelist,
  aturan blok/allow, dst — semua TETAP hijau kalau ON). HANYA
  `NavToggleRow` (switch toggle tema itu sendiri) yang eksplisit override
  jadi `LocalTrimAccent.current`.
- `HomeScreen.kt` — `NavDivider()` + 2 `HorizontalDivider` di dalam
  `WarpModeCard` retint ke `trimAccent.copy(alpha=0.45f)` (dari
  `ShieldOutline` netral). `ProtectionRing`'s `trackColor = ShieldOutline`
  (baris ~291) **SENGAJA TIDAK disentuh** — itu representasi state
  (progress ring proteksi), bukan chrome dekoratif.

**Prinsip yang TETAP dipegang (tidak berubah dari v4.7.0):** `ShieldGreen`
untuk state proteksi ASLI (WARP toggle switch, ProtectionRing, StatCard,
WarpModeCard `accentActive`) TIDAK PERNAH ikut `LocalTrimAccent` — cuma
CHROME (border kartu, divider, switch dekoratif tema-itu-sendiri) yang
sekarang ikut. Kalau ada permintaan lanjutan expand ke tempat lain, cek
dulu itu representasi state atau murni dekoratif sebelum ganti warnanya.

**Verifikasi:** comment balance semua file disentuh OK, regex scan bug class
v4.7.1 (`*/` nyelip di kdoc) diulang ke SELURUH repo — bersih.

## STATUS SEBELUMNYA: v4.7.1, hotfix CI compile error di Theme.kt (2026-08-11)

**Root cause:** kdoc `Theme.kt` baris 15 (v4.7.0) berisi teks
`background/surface*/primary/error` — urutan karakter `*/` di tengah kata
("surface*/primary") ke-parse Kotlin sebagai PENUTUP block comment
(`/** ... */`), bukan teks biasa. Comment tertutup prematur di situ, sisa
kdoc (baris 15-34) ikut ke-parse sebagai kode top-level -> puluhan error
"Expecting a top level declaration" -> `kspDebugKotlin` FAILED. Diketahui
dari CI log yang di-upload user (run 31489667976), bukan ditemukan
manual/nebak — cross-check baris:kolom error persis di posisi `*/` yang
dimaksud.

**Fix:** reword jadi `background, surface family, primary, error` (hilangkan
adjacency `*` + `/`). SATU baris diubah, SATU file (`Theme.kt`). Sudah
di-scan ULANG regex `[a-zA-Z0-9_]\*/[a-zA-Z]` ke SELURUH `.kt` di repo —
tidak ada instance lain dari bug class yang sama.

**PENTING — sesi sebelumnya di chat ini SEMPAT membangun batch lain (full
"Neumorphism Pivot" — dual-shadow tokens, rewrite `TactileSurface`/
`Button`/`Switch`) di atas snapshot v4.4.0 yang SUDAH BASI saat itu
disadari. Batch itu DIBUANG, bukan digabung — proyek nyata sudah maju ke
v4.5.0 (Silent Leak Detector) -> v4.6.0 (wiring 5 layar) -> v4.7.0 (toggle
Titanium+Lapis Lazuli, arsitektur `LocalTrimAccent`) lewat sesi/device lain
sebelum upload CI log ini. Kalau nemu referensi "dual-shadow neumorphic" /
"NeumorphicLayer" di riwayat chat, itu TIDAK PERNAH masuk ke repo — abaikan,
jangan dikira sudah ter-apply.**

**Pelajaran multi-device sync:** batch berikutnya, kalau ada keraguan
proyek mungkin sudah berubah di device/sesi lain, cross-check dulu
`PROJECT_STATE.md`/`CHANGELOG.md` versi TERBARU yang di-upload user
terhadap asumsi awal sesi — jangan lanjut dari snapshot lama tanpa
verifikasi kalau user sempat upload ulang project zip di tengah sesi.

## STATUS SEBELUMNYA: v4.7.0, toggle tema kustom ke-2 "Titanium + Lapis Lazuli" (2026-08-11)

Toggle baru di Home (grup nav, baris terakhir) ganti trim accent dekoratif
antara Titanium+Brass (default) dan Titanium+Lapis Lazuli. Detail lengkap:
lihat CHANGELOG.md v4.7.0.

**Poin penting untuk sesi lanjutan:**
- `LocalTrimAccent` (CompositionLocal di `ui/theme/ThemeVariant.kt`) adalah
  SATU-SATUNYA cara benar buat komponen baru ikut tema. Jangan import
  `AccentBrass`/`LapisLazuli` langsung di komponen — itu val statis, tidak
  akan ikut toggle runtime. Kalau nambah komponen dekoratif baru yang
  "harus ikut tema", baca `LocalTrimAccent.current`, bukan token warna
  mentah.
- `ShieldGreen` TETAP warna state proteksi di KEDUA tema, sengaja tidak
  ikut toggle — jangan "perbaiki" ini jadi ikut tema tanpa diminta eksplisit,
  itu akan merusak recognisability status ON/OFF yang sudah didokumentasikan
  sejak v4.4.0.
- Baru ADA 2 tempat yang benar-benar baca `LocalTrimAccent`:
  `TactileButton` Primary variant dan `HomeScreen.NavRow`/`NavToggleRow`
  icon tint. `TactileSurface`'s `accentColor` param (dipakai di beberapa
  call site dengan `ShieldGreen` eksplisit) TIDAK disentuh — itu state,
  bukan dekoratif, per keputusan di atas.
- Storage key theme disimpan sebagai string (`"titanium_brass"`/
  `"titanium_lapis"`), bukan enum `.name`/ordinal — `AppThemeVariant.
  fromStorageKey()` fallback ke TITANIUM_BRASS kalau key tidak dikenali
  (device lama / typo), jadi aman ditambah varian tema baru nanti tanpa
  migrasi data.

## STATUS SEBELUMNYA: v4.6.0, Tactile wiring batch selesai (2026-08-11)

5 layar pending dari v4.4.0 (`RulesScreen`, `LogsScreen`,
`DiagnosticsScreen`, `WhitelistScreen`, `OnboardingScreen`) sudah di-wire
penuh ke `Tactile*`. Detail per layar: lihat CHANGELOG.md v4.6.0.

**Poin penting untuk sesi lanjutan:**
- Backlog wiring skeuomorphism-lite dari v4.4.0 SELESAI. Kalau user minta
  "lanjutkan skeuomorphism" lagi, TIDAK ada lagi 5-layar-pending yang
  dimaksud — tanya dulu apa yang dimaksud (mis. `SilentLeakScreen` v4.5.0,
  yang sengaja dibiarkan Material3 karena di luar scope batch itu).
- `TactileButtonVariant.Primary` baru punya call site PERTAMA di batch ini
  (`OnboardingScreen` CTA) — sebelumnya cuma didokumentasikan, tidak
  dipakai di manapun. Kalau ada bug visual di tombol hijau CTA onboarding,
  itu jalur kode yang baru pertama kali benar-benar dieksekusi di device.
- Pola `TactileButton` Secondary + `Text(color = ShieldDanger)` eksplisit
  (dipakai di "Lupakan Akun WARP") adalah cara yang benar untuk tombol
  destructive/danger di sistem Tactile — `TactileButton` sendiri tidak
  punya varian Danger, jangan tambah varian baru tanpa alasan kuat, cukup
  override warna teks seperti pola ini.

## STATUS SEBELUMNYA: v4.5.1, hotfix CI compile error (2026-08-11)

## STATUS SEBELUMNYA: v4.5.0, "Silent Leak Detector" — fitur unggulan baru (2026-08-11)

User minta fitur unggulan yang tidak ada di app ad-block/VPN generik lain
(dipilih dari 3 opsi yang diajukan). Dibangun: deteksi app yang melakukan
query DNS SAAT LAYAR MATI ("silent leak" / trafik diam-diam), on-device
penuh, tanpa cloud, tanpa permission baru. Detail lengkap desain + file
yang disentuh: lihat CHANGELOG.md v4.5.0.

**Poin penting untuk sesi lanjutan:**
- `ScreenStateMonitor.isScreenOff` hanya di-set benar SELAMA sesi VPN DNS
  aktif (start()/stop() dipanggil dari `AdBlockVpnService.startVpn()`/
  `stopVpn()`, mirror `queryLogger`). WARP-only session TIDAK mengisi
  `backgroundApp` — WARP tidak lewat `DnsPacketLoop` sama sekali (lihat
  keputusan arsitektur #7). Kalau user minta fitur ini juga jalan di mode
  WARP, itu scope baru (perlu titik instrumentasi terpisah di jalur WARP),
  BUKAN bug di sini.
- `AppUidWhitelistChecker.resolvePackageName()` hanya jalan API 29+ (sama
  seperti whitelist sebelumnya) — di bawah itu, silent leak detector tidak
  bisa atribusi per-app sama sekali (bukan kosong karena bug, tapi memang
  API `getConnectionOwnerUid` tidak tersedia).
- `domain_log.backgroundApp` sengaja TIDAK diberi index terpisah —
  keputusan eksplisit (lihat kdoc `DomainLogEntity.kt`), jangan tambahkan
  tanpa alasan baru (mis. kalau `PRUNE_KEEP_ROWS` dinaikkan jauh di atas
  2000 nanti, evaluasi ulang).
- `SilentLeakScreen` masih Material3 polos seperti 5 layar Tactile pending
  di bawah — kalau "lanjutkan skeuomorphism" diminta lagi, tambahkan layar
  ini ke daftar yang di-wire, jangan lupa.

## STATUS SEBELUMNYA: v4.4.0, "Radikal Redesign" — theme regresi + skeuomorphism-lite (2026-08-09)

**Root cause regresi "theme custom menghilang" (dikonfirmasi):** v3.43.0
membangun `ui/components/Tactile*.kt` (skeuomorphic-lite components) +
`TactileTokens.kt` lengkap dengan token warna barunya, TAPI tidak pernah
wiring komponennya ke layar manapun. v4.0.0 cleanup lalu menghapusnya
sebagai "dead code, 0 call site" — padahal itu fitur setengah-jadi yang
lupa di-wire, bukan kode mati beneran. Root cause = disiplin proses yang
hilang: token/komponen dibuat di satu batch, wiring "menyusul" di batch
lain yang tidak pernah datang.

**Aturan baru mulai v4.4.0 — WAJIB dipatuhi di sesi manapun ke depan:**
Setiap komponen UI baru (`Tactile*` atau apa pun namanya) HARUS di-wire ke
minimal 1 layar nyata DI BATCH YANG SAMA dengan pembuatannya. Dilarang
"bangun tokennya dulu, nanti sesi lain baru dipasang" — itu persis pola yang
menyebabkan regresi ini. Kalau scope tidak cukup untuk wiring semua layar
sekaligus, WAJIB dicatat eksplisit di CHANGELOG.md + section ini sebagai
"Wired vs pending" (lihat CHANGELOG.md v4.4.0) — bukan didiamkan.

**Status wiring skeuomorphism-lite ("Tactile" system) per 2026-08-09:**
- ✅ HomeScreen.kt — wired penuh (ProtectionRing, WarpModeCard, StatCard x2,
  NavGroup, 2 Switch, tombol Reset statistik).
- ⏳ RulesScreen.kt, LogsScreen.kt, DiagnosticsScreen.kt, WhitelistScreen.kt,
  OnboardingScreen.kt — MASIH Material3 polos (Card/Switch/OutlinedButton).
  Otomatis ikut palet+shape baru lewat alias `Shield*` di Color.kt (jadi
  visualnya konsisten, tidak pecah), tapi belum dapat bevel/gradient/shadow.
  **INI YANG HARUS DIKERJAKAN DI SESI LANJUTAN** kalau diminta "lanjutkan
  skeuomorphism-nya" — jangan bikin komponen baru lagi, WIRING 5 layar di
  atas ke komponen `Tactile*` yang SUDAH ADA di `ui/components/`.

**Arah desain:** brushed-titanium instrument panel (bukan glass/blur seperti
identitas sebelumnya). Lihat kdoc `ui/theme/Color.kt` untuk token lengkap +
alasan tiap pilihan warna. Depth pakai teknik Compose asli (`Modifier.border`
dengan Brush gradient, `Modifier.shadow()` asli) — bukan gambar tekstur.

## STATUS SEBELUMNYA: v4.3.0, "Radikal Perf" batch 3 — Room DB (2026-08-09)

Audit radikal lanjutan (batch 3). Ketemu `domain_log` tidak ada index di
`timestamp` DAN tidak pernah di-prune (`pruneOlderThan()` sudah lama ada
tapi tak pernah dipanggil) — tabel tumbuh tak terbatas + tiap query
`ORDER BY timestamp` full-scan, jadi makin lambat seiring waktu. Fix:
index di `DomainLogEntity`, `AppDatabase` version 1→2 (destructive migration
sudah di-set, aman), `DomainLogDao.pruneKeepingLatest(keep)` baru dipanggil
dari `DnsQueryLogger` tiap 5 menit (keep 2000 baris — UI cuma pernah nampilin
500 lewat LIMIT). Kalau nambah kolom/query baru ke `domain_log` nanti,
CEK apakah butuh index tambahan juga — jangan ulangi pola yang sama.

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
