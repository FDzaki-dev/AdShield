package com.fdzaki.adshield.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fdzaki.adshield.ui.components.TactileButton
import com.fdzaki.adshield.ui.components.TactileButtonVariant
import com.fdzaki.adshield.ui.theme.ShieldAccentDim
import com.fdzaki.adshield.ui.theme.ShieldGreen
import com.fdzaki.adshield.ui.theme.ShieldTextMuted
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Filled.Shield,
        title = "Selamat Datang di AdShield",
        body = "Dua mode perlindungan terpisah dalam satu app — pilih yang " +
            "sesuai kebutuhanmu. Bisa diganti kapan saja dari Home screen."
    ),
    OnboardingPage(
        icon = Icons.Filled.Wifi,
        title = "Mode Ad-Block DNS",
        body = "Blokir iklan & pelacak secara ringan, tanpa root. Hanya query " +
            "DNS yang disentuh — trafik lain (video, banking, dll) tetap " +
            "langsung ke internet, jadi hemat baterai dan latensi hampir " +
            "tidak terasa."
    ),
    OnboardingPage(
        icon = Icons.Filled.Lock,
        title = "Mode VPN Tunnel (WARP)",
        body = "Enkripsi SEMUA trafik lewat Cloudflare WARP gratis — cocok " +
            "kalau prioritasmu privasi penuh. Mode ini tidak memblokir " +
            "iklan, dan tidak bisa jalan bersamaan dengan Ad-Block DNS " +
            "(mutually exclusive, otomatis mematikan yang lain)."
    ),
    OnboardingPage(
        icon = Icons.Filled.BatteryChargingFull,
        title = "Satu Langkah Terakhir",
        body = "Agar proteksi tidak dimatikan sistem saat HP idle lama, " +
            "izinkan AdShield dikecualikan dari optimasi baterai. Bisa " +
            "dilewati dan diatur belakangan dari Home screen."
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    onRequestBatteryExemption: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onFinish) {
                Text("Lewati", color = ShieldTextMuted)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { index ->
            OnboardingPageContent(pages[index])
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pages.size) { index ->
                val active = index == pagerState.currentPage
                // Apple-Style pass: dot size/color now animates (was an
                // instant jump-cut before) — same smooth grow/shrink feel
                // as iOS's UIPageControl. Purely visual, `active`'s meaning
                // and the pager's own state/logic are untouched.
                val dotSize by animateDpAsState(
                    targetValue = if (active) 10.dp else 8.dp,
                    label = "onboardingDotSize"
                )
                val dotColor by animateColorAsState(
                    targetValue = if (active) ShieldGreen else ShieldTextMuted,
                    label = "onboardingDotColor"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(dotSize)
                        .background(color = dotColor, shape = CircleShape)
                )
            }
        }

        if (isLastPage) {
            // v4.6.0 — Tactile wiring batch (see PROJECT_STATE.md):
            // OutlinedButton -> TactileButton Secondary, same onClick.
            TactileButton(
                onClick = onRequestBatteryExemption,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                variant = TactileButtonVariant.Secondary
            ) { Text("Kecualikan dari Optimasi Baterai") }
            Spacer(Modifier.height(12.dp))
        }

        // v4.6.0 — Tactile wiring batch: Button -> TactileButton Primary
        // (already ShieldGreen-filled by default for Primary), same onClick.
        TactileButton(
            onClick = {
                if (isLastPage) {
                    onFinish()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            variant = TactileButtonVariant.Primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(if (isLastPage) "Mulai Pakai AdShield" else "Lanjut", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                // Apple-Style consistency pass: was an ad-hoc
                // `ShieldGreen.copy(alpha = 0.15f)` tint computed locally —
                // now reuses `ShieldAccentDim`, the SAME "tinted icon
                // background" constant every other screen uses, instead of
                // a second parallel green-tint formula existing only here.
                .background(color = ShieldAccentDim, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = ShieldGreen,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            page.title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            page.body,
            fontSize = 14.sp,
            color = ShieldTextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
