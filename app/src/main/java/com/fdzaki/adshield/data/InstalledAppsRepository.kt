package com.fdzaki.adshield.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystemApp: Boolean
)

class InstalledAppsRepository(private val context: Context) {

    suspend fun loadUserFacingApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
        apps
            .filter { app ->
                // Show launchable apps, plus any system app the user might still
                // want to whitelist (e.g. a stock browser or store app).
                pm.getLaunchIntentForPackage(app.packageName) != null || !isSystem(app)
            }
            .distinctBy { it.packageName }
            .map { app ->
                InstalledApp(
                    packageName = app.packageName,
                    label = pm.getApplicationLabel(app).toString(),
                    icon = runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull(),
                    isSystemApp = isSystem(app)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    // v4.5.0 — Silent Leak Detector (see PROJECT_STATE.md): single-package
    // lookup for the leak list, which only has a handful of package names
    // to resolve at a time — a full loadUserFacingApps() scan would be
    // wasteful just to find the label/icon for a few packages. Returns null
    // for an app that was uninstalled after it made the logged query.
    suspend fun loadAppInfo(packageName: String): InstalledApp? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        runCatching {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            InstalledApp(
                packageName = packageName,
                label = pm.getApplicationLabel(appInfo).toString(),
                icon = runCatching { pm.getApplicationIcon(packageName) }.getOrNull(),
                isSystemApp = isSystem(appInfo)
            )
        }.getOrNull()
    }

    private fun isSystem(app: ApplicationInfo): Boolean =
        (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
}
