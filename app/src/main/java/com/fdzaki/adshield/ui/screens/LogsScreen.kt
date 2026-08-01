package com.fdzaki.adshield.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fdzaki.adshield.ui.MainViewModel
import com.fdzaki.adshield.ui.theme.ShieldDanger
import com.fdzaki.adshield.ui.theme.ShieldGreen
import com.fdzaki.adshield.ui.theme.ShieldTextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val logs by viewModel.recentLogs.collectAsState()
    val loggingEnabled by viewModel.loggingEnabled.collectAsState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Domain") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearLogs() }) {
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Simpan log query domain", fontSize = 13.sp)
                Switch(checked = loggingEnabled, onCheckedChange = { viewModel.setLoggingEnabled(it) })
            }

            if (logs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada aktivitas query domain.", color = ShieldTextMuted)
                }
            } else {
                LazyColumn {
                    items(logs, key = { it.id }) { entry ->
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
