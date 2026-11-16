package com.example.pardadns.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.example.pardadns.data.model.AppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppFilterRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns exactly the apps that appear in the device's app drawer —
     * same query the launcher uses: ACTION_MAIN + CATEGORY_LAUNCHER.
     * This includes pre-installed apps with launcher icons but excludes
     * headless system packages, services, and ourselves.
     */
    fun getInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcherIntent, PackageManager.GET_META_DATA)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { resolveInfo ->
                AppInfo(
                    packageName = resolveInfo.activityInfo.packageName,
                    label       = resolveInfo.loadLabel(pm).toString(),
                    icon        = resolveInfo.loadIcon(pm),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Package names that are excluded from the VPN tunnel. */
    fun getExcludedPackages(): Set<String> =
        prefs.getStringSet(KEY_EXCLUDED, emptySet()) ?: emptySet()

    fun setExcluded(packageName: String, excluded: Boolean) {
        val current = getExcludedPackages().toMutableSet()
        if (excluded) current.add(packageName) else current.remove(packageName)
        prefs.edit().putStringSet(KEY_EXCLUDED, current).apply()
    }

    companion object {
        const val PREFS_NAME   = "parda_app_filter"
        const val KEY_EXCLUDED = "excluded_packages"
    }
}
