package io.github.rsclub22.tireservations.data

import android.content.Context
import android.os.Build

/**
 * True, wenn der Play Store die App installiert hat - dann übernimmt er die Updates
 * und die Prüfung gegen GitHub entfällt.
 *
 * Lag vorher als `UpdateChecker.isFromPlayStore` in der companion der Klasse. Sie
 * braucht dafür einen Android-Context, der Rest der Klasse nicht; deshalb steht sie
 * jetzt hier und `UpdateChecker` ist dadurch plattformfrei.
 */
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
