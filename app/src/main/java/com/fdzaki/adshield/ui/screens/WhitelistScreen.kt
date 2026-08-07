package com.fdzaki.adshield.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fdzaki.adshield.ui.MainViewModel
import com.fdzaki.adshield.ui.theme.ShieldBgDark
import com.fdzaki.adshield.ui.theme.ShieldTextMuted
import com.fdzaki.adshield.ui.theme.ShieldWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val apps by viewModel.installedApps.collectAsState()
    val whitelisted by viewModel.whitelistedApps.collectAsState()
    var query by remember { mutableStateOf("") }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Whitelist per Aplikasi") },
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
            // Polish pass: search field brought in line with the leading-icon +
            // clear-button pattern already used on Logs/Rules — was plain-label
            // only here before, an inconsistency across otherwise-identical
            // search affordances in the same app.
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari aplikasi…", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Hapus pencarian")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                "Aplikasi yang di-whitelist tidak akan mengalami pemblokiran domain sama sekali. " +
                    "Butuh Android 10 (API 29) ke atas — di versi lebih lama, whitelist per-app " +
                    "belum bisa dideteksi sistem Android dan tidak akan berpengaruh.",
                color = ShieldTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(4.dp))
            // Feedback consistency: Logs/Rules both show a live count of what's
            // currently in view; Whitelist previously showed nothing here.
            if (apps.isNotEmpty()) {
                Text(
                    "${whitelisted.size} aplikasi di-whitelist · menampilkan ${filtered.size} dari ${apps.size}",
                    fontSize = 11.sp,
                    color = ShieldTextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            // Empty states: previously a blank LazyColumn either while the app
            // list was still loading or when a search matched nothing — both
            // looked identical to "broken screen". Logs/Rules already handle
            // this distinction; Whitelist didn't.
            if (apps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Memuat daftar aplikasi…", color = ShieldTextMuted, fontSize = 13.sp)
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tidak ada aplikasi yang cocok dengan pencarian.", color = ShieldTextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(
                            label = app.label,
                            packageName = app.packageName,
                            iconBitmap = remember(app.packageName) {
                                runCatching { app.icon?.toBitmap()?.asImageBitmap() }.getOrNull()
                            },
                            isWhitelisted = whitelisted.contains(app.packageName),
                            onToggle = { checked -> viewModel.toggleAppWhitelist(app.packageName, checked) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    label: String,
    packageName: String,
    iconBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    isWhitelisted: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Accessibility fix: same issue/fix as LogsScreen's toggle row —
            // app icon/name/package and the Switch used to be 3+ separate
            // TalkBack stops with the Switch itself announcing no app name.
            // `toggleable` merges them into one row-level stop and makes the
            // whole row tappable (iOS Settings row convention).
            .toggleable(
                value = isWhitelisted,
                onValueChange = onToggle,
                role = Role.Switch
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        } else {
            Box(Modifier.size(36.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(packageName, fontSize = 11.sp, color = ShieldTextMuted)
        }
        // onCheckedChange = null: the Row above owns the toggle via
        // `toggleable` now — a live callback here too would double-fire.
        Switch(checked = isWhitelisted, onCheckedChange = null)
    }
}
