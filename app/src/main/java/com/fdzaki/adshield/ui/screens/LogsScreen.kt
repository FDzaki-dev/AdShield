package com.fdzaki.adshield.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fdzaki.adshield.ui.MainViewModel
import com.fdzaki.adshield.ui.theme.ShieldBgDark
import com.fdzaki.adshield.ui.theme.ShieldDanger
import com.fdzaki.adshield.ui.theme.ShieldGreen
import com.fdzaki.adshield.ui.theme.ShieldTextMuted
import com.fdzaki.adshield.ui.theme.ShieldWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogFilter { ALL, BLOCKED, ALLOWED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val logs by viewModel.recentLogs.collectAsState()
    val loggingEnabled by viewModel.loggingEnabled.collectAsState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }

    // Feedback audit finding: the DeleteSweep icon used to clear the whole
    // log in a single tap with no confirmation. Gated behind a confirm
    // dialog now — clearAll() is a DB wipe, not cheap to undo.
    var showClearConfirm by remember { mutableStateOf(false) }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Bersihkan semua log?") },
            text = { Text("${logs.size} entri log domain akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLogs()
                    showClearConfirm = false
                }) { Text("Hapus", color = ShieldDanger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Batal") }
            }
        )
    }

    // Client-side filter/search: recentLogs is already capped at 500 entries
    // (see DomainLogDao), so this is cheap and doesn't need a DB-level query.
    val filteredLogs = remember(logs, searchQuery, filter) {
        logs.filter { entry ->
            val matchesFilter = when (filter) {
                LogFilter.ALL -> true
                LogFilter.BLOCKED -> entry.blocked
                LogFilter.ALLOWED -> !entry.blocked
            }
            val matchesSearch = searchQuery.isBlank() ||
                entry.domain.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Domain") },
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
                },
                actions = {
                    IconButton(
                        onClick = { showClearConfirm = true },
                        enabled = logs.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Bersihkan log")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    // Accessibility fix: label Text + Switch were separate
                    // TalkBack focus stops before — Switch announced only
                    // "On/Off" with no idea what it controlled. `toggleable`
                    // merges the whole row into one stop ("Simpan log query
                    // domain, switch, on/off") and makes the full row
                    // tappable, not just the small Switch hit target — also
                    // matches how iOS Settings toggle rows behave.
                    .toggleable(
                        value = loggingEnabled,
                        onValueChange = { viewModel.setLoggingEnabled(it) },
                        role = Role.Switch
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Simpan log query domain", fontSize = 13.sp)
                // onCheckedChange = null: the Row above now owns the toggle
                // action via `toggleable`. Leaving a real callback here too
                // would double-fire when tapping directly on the Switch.
                Switch(checked = loggingEnabled, onCheckedChange = null)
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Cari domain…", fontSize = 13.sp) },
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

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == LogFilter.ALL,
                    onClick = { filter = LogFilter.ALL },
                    label = { Text("Semua (${logs.size})") }
                )
                FilterChip(
                    selected = filter == LogFilter.BLOCKED,
                    onClick = { filter = LogFilter.BLOCKED },
                    label = { Text("Diblokir (${logs.count { it.blocked }})") }
                )
                FilterChip(
                    selected = filter == LogFilter.ALLOWED,
                    onClick = { filter = LogFilter.ALLOWED },
                    label = { Text("Diizinkan (${logs.count { !it.blocked }})") }
                )
            }

            Spacer(Modifier.height(4.dp))

            if (logs.isNotEmpty()) {
                Text(
                    "Menampilkan ${filteredLogs.size} dari ${logs.size} entri",
                    fontSize = 11.sp,
                    color = ShieldTextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (logs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada aktivitas query domain.", color = ShieldTextMuted)
                }
            } else if (filteredLogs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tidak ada domain yang cocok dengan pencarian/filter.", color = ShieldTextMuted)
                }
            } else {
                LazyColumn {
                    items(filteredLogs, key = { it.id }) { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.domain, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Text(
                                    timeFormat.format(Date(entry.timestamp)),
                                    fontSize = 11.sp,
                                    color = ShieldTextMuted
                                )
                            }
                            Text(
                                if (entry.blocked) "Diblokir" else "Diizinkan",
                                color = if (entry.blocked) ShieldDanger else ShieldGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
