package com.fdzaki.adshield.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawable.toBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fdzaki.adshield.ui.MainViewModel
import com.fdzaki.adshield.ui.theme.ShieldTextMuted

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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Cari aplikasi") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            Text(
                "Aplikasi yang di-whitelist tidak akan mengalami pemblokiran domain sama sekali.",
                color = ShieldTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
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
        Switch(checked = isWhitelisted, onCheckedChange = onToggle)
    }
}
