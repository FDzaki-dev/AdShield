package com.fdzaki.adshield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fdzaki.adshield.ui.MainViewModel
import com.fdzaki.adshield.ui.components.TactileButton
import com.fdzaki.adshield.ui.components.TactileButtonVariant
import com.fdzaki.adshield.ui.theme.ShieldBgDark
import com.fdzaki.adshield.ui.theme.ShieldDanger
import com.fdzaki.adshield.ui.theme.ShieldGreen
import com.fdzaki.adshield.ui.theme.ShieldSurface2
import com.fdzaki.adshield.ui.theme.ShieldSurface3
import com.fdzaki.adshield.ui.theme.ShieldTextMuted
import com.fdzaki.adshield.ui.theme.ShieldWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bare domain, or "*.domain" wildcard — same forms BlocklistManager.parseLine accepts. */
private val domainRegex = Regex(
    "^(\\*\\.)?[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val blocked by viewModel.customBlockedDomains.collectAsState()
    val allowed by viewModel.customAllowedDomains.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val trimmedInput = input.trim()
    val isDuplicate = trimmedInput.isNotEmpty() &&
        (if (tab == 0) blocked.contains(trimmedInput.lowercase()) else allowed.contains(trimmedInput.lowercase()))
    val isValidFormat = trimmedInput.isEmpty() || domainRegex.matches(trimmedInput)
    val canAdd = trimmedInput.isNotEmpty() && isValidFormat && !isDuplicate

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aturan Kustom") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ShieldBgDark,
                    titleContentColor = ShieldWhite,
                    navigationIconContentColor = ShieldWhite,
                    actionIconContentColor = ShieldWhite
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            BlocklistUrlSection(viewModel)

            HorizontalDivider()

            // Apple-style pass: iOS-like pill segmented control instead of
            // Material's underline TabRow — plain foundation primitives only
            // (Box/Row/clip/background/clickable), no Material3 experimental
            // SegmentedButton API, to keep this a near-zero-risk visual swap.
            // `tab` state and its onClick wiring are 100% unchanged.
            SegmentedTabs(
                options = listOf("Blokir (${blocked.size})", "Izinkan (${allowed.size})"),
                selectedIndex = tab,
                onSelect = { tab = it }
            )

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("domain.com atau *.domain.com") },
                        singleLine = true,
                        isError = trimmedInput.isNotEmpty() && (!isValidFormat || isDuplicate),
                        supportingText = {
                            when {
                                trimmedInput.isNotEmpty() && !isValidFormat ->
                                    Text("Format domain tidak valid", color = ShieldDanger)
                                isDuplicate ->
                                    Text("Domain sudah ada di daftar ini", color = ShieldDanger)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    // v4.6.0 — Tactile wiring batch (see PROJECT_STATE.md):
                    // Button -> TactileButton Primary, same onClick/enabled.
                    TactileButton(
                        onClick = {
                            if (tab == 0) viewModel.addBlockedDomain(trimmedInput) else viewModel.addAllowedDomain(trimmedInput)
                            input = ""
                        },
                        enabled = canAdd,
                        variant = TactileButtonVariant.Primary,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
                    ) { Text("Tambah") }
                }
            }

            Text(
                if (tab == 0)
                    "\"domain.com\" hanya blokir domain itu persis. Pakai \"*.domain.com\" " +
                        "kalau mau ikut blokir semua subdomain-nya juga."
                else
                    "Override: domain di sini akan selalu diizinkan walau match dengan aturan " +
                        "blokir manapun (termasuk wildcard bawaan). Berguna kalau ada domain " +
                        "yang salah terblokir (false positive).",
                color = ShieldTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            val fullList = if (tab == 0) blocked.sorted() else allowed.sorted()
            if (fullList.size > 5) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("Cari domain di daftar ini…", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Hapus pencarian")
                            }
                        }
                    },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
            }

            val filteredList = remember(fullList, searchQuery) {
                if (searchQuery.isBlank()) fullList
                else fullList.filter { it.contains(searchQuery, ignoreCase = true) }
            }

            if (fullList.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (tab == 0) "Belum ada domain yang diblokir manual." else "Belum ada domain yang diizinkan manual.",
                        color = ShieldTextMuted,
                        fontSize = 13.sp
                    )
                }
            } else if (filteredList.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Tidak ada domain yang cocok pencarian.", color = ShieldTextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn {
                    items(filteredList, key = { it }) { domain ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                domain,
                                modifier = Modifier.weight(1f),
                                color = if (tab == 0) ShieldDanger else ShieldGreen
                            )
                            IconButton(onClick = {
                                if (tab == 0) viewModel.removeBlockedDomain(domain) else viewModel.removeAllowedDomain(domain)
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = ShieldTextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Collapsed by default (starts closed so it doesn't push the domain list
 * below the fold on smaller screens) — expand to configure/refresh a
 * downloaded blocklist URL, auto-refreshed every 24 hours by
 * BlocklistUpdateWorker whenever a URL is saved.
 */
@Composable
private fun BlocklistUrlSection(viewModel: MainViewModel) {
    val savedUrl by viewModel.customBlocklistUrl.collectAsState()
    val lastUpdated by viewModel.blocklistLastUpdated.collectAsState()
    val status by viewModel.blocklistUpdateStatus.collectAsState()

    var expanded by rememberSaveable { mutableStateOf(false) }
    var urlInput by remember(savedUrl) { mutableStateOf(savedUrl) }

    val dateFormat = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale("id", "ID")) }
    val isError = status.startsWith("Gagal")
    val isBusy = status == "Memperbarui…"

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Blocklist Kustom (URL)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    if (savedUrl.isBlank()) "Belum diaktifkan"
                    else "Aktif — diperbarui otomatis tiap 24 jam",
                    color = ShieldTextMuted,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Tutup" else "Buka"
                )
            }
        }

        if (expanded) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    "Tempel URL raw ke file blocklist (format hosts atau satu domain per baris — " +
                        "sama seperti blocklist bawaan). Dijalankan otomatis tiap 24 jam sekali " +
                        "kalau ada koneksi internet, atau bisa diperbarui manual kapan saja.",
                    color = ShieldTextMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // v4.6.0 — Tactile wiring batch (see PROJECT_STATE.md):
                    // Button -> TactileButton Primary, OutlinedButton ->
                    // TactileButton Secondary, same onClick/enabled/labels.
                    TactileButton(
                        onClick = {
                            viewModel.setCustomBlocklistUrl(urlInput.trim())
                            if (urlInput.isNotBlank()) viewModel.refreshBlocklistNow()
                        },
                        enabled = !isBusy,
                        variant = TactileButtonVariant.Primary
                    ) { Text(if (urlInput.isBlank()) "Simpan (Nonaktifkan)" else "Simpan & Perbarui") }

                    if (savedUrl.isNotBlank()) {
                        TactileButton(
                            onClick = { viewModel.refreshBlocklistNow() },
                            enabled = !isBusy,
                            variant = TactileButtonVariant.Secondary
                        ) { Text("Perbarui Sekarang") }
                    }
                }

                if (status.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        status,
                        color = when {
                            isBusy -> ShieldTextMuted
                            isError -> ShieldDanger
                            else -> ShieldGreen
                        },
                        fontSize = 12.sp
                    )
                }
                if (lastUpdated > 0L) {
                    Text(
                        "Terakhir diperbarui: ${dateFormat.format(Date(lastUpdated))}",
                        color = ShieldTextMuted,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * iOS-style pill segmented control — rounded outer pill, a smaller filled
 * pill for the selected option, equal-width segments. Pure `Row`/`Box` +
 * `clip`/`background`/`clickable`; deliberately avoids the Material3
 * `SegmentedButton` experimental API so this reuses only primitives already
 * proven elsewhere in this codebase.
 */
@Composable
private fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ShieldSurface2)
            .padding(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) ShieldSurface3 else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) ShieldWhite else ShieldTextMuted
                )
            }
        }
    }
}
