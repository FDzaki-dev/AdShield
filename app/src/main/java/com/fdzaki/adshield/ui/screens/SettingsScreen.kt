package com.fdzaki.adshield.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fdzaki.adshield.ui.MainViewModel
import com.fdzaki.adshield.ui.components.SkeuoButton
import com.fdzaki.adshield.ui.theme.ShieldBgDark
import com.fdzaki.adshield.ui.theme.ShieldTextFaint
import com.fdzaki.adshield.ui.theme.ShieldTextMuted
import com.fdzaki.adshield.ui.theme.ShieldWhite
import com.fdzaki.adshield.util.AppTheme

/**
 * Settings screen — currently a single "Tema Aplikasi" section holding the
 * theme picker requested for the app. Kept as its own top-level screen
 * (rather than folded into Diagnostics, which is read-only/report-oriented)
 * so future user-configurable options have an obvious home.
 *
 * The picker itself uses [SkeuoButton] for BOTH options — even the "Default"
 * one — so the user can feel the difference between the two identities
 * (bevel/press behavior) before committing, not just read two lines of text.
 * Selecting an option calls MainViewModel.setAppTheme() immediately; there's
 * no separate "Apply" step since a theme choice is cheap/reversible and
 * MaterialTheme recomposition is instant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val appTheme by viewModel.appTheme.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
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
        },
        containerColor = ShieldBgDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "TEMA APLIKASI",
                color = ShieldTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                "Pilih tampilan visual AdShield. Perubahan berlaku langsung di seluruh aplikasi.",
                color = ShieldTextFaint,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            SkeuoButton(
                title = "Default",
                subtitle = "AMOLED Glassmorphism — hitam pekat + kaca berlapis, aksen hijau/biru tengah malam.",
                icon = Icons.Filled.Palette,
                selected = appTheme != AppTheme.SKEUO_RADICAL_DARK,
                onClick = { viewModel.setAppTheme(AppTheme.DEFAULT) }
            )

            SkeuoButton(
                title = "Radical Skeuomorphism (Dark)",
                subtitle = "Permukaan fisik gelap dengan bevel, cahaya terarah, dan kontrol bertekstur logam/karet — hanya mode gelap.",
                icon = Icons.Filled.DarkMode,
                selected = appTheme == AppTheme.SKEUO_RADICAL_DARK,
                onClick = { viewModel.setAppTheme(AppTheme.SKEUO_RADICAL_DARK) }
            )
        }
    }
}
