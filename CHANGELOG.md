# Changelog

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
