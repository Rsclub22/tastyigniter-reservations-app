package io.github.rsclub22.tireservations.data

import android.content.Context
import android.os.Build

object InstallSource {
    /** True if the Play Store installed the app; then it handles updates itself. */
    fun isFromPlayStore(context: Context): Boolean {
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()
        return installer == "com.android.vending"
    }
}
