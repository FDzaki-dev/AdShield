package com.fdzaki.adshield.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fdzaki.adshield.ui.MainViewModel
import com.fdzaki.adshield.ui.theme.ShieldDanger
import com.fdzaki.adshield.ui.theme.ShieldGreen
import com.fdzaki.adshield.ui.theme.ShieldTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val blocked by viewModel.customBlockedDomains.collectAsState()
    val allowed by viewModel.customAllowedDomains.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aturan Kustom") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Blokir (${blocked.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Izinkan (${allowed.size})") })
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("contoh.domain.com") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (input.isNotBlank()) {
                        if (tab == 0) viewModel.addBlockedDomain(input) else viewModel.addAllowedDomain(input)
                        input = ""
                    }
                }) { Text("Tambah") }
            }

            Text(
                if (tab == 0)
                    "Domain di sini akan selalu diblokir, di luar daftar bawaan."
                else
                    "Domain di sini akan selalu diizinkan, walau ada di daftar blokir bawaan (override).",
                color = ShieldTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            val list = if (tab == 0) blocked.sorted() else allowed.sorted()
            LazyColumn {
                items(list, key = { it }) { domain ->
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
