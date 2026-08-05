package com.myfitnesslog.feature.update.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.myfitnesslog.BuildConfig
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a downloaded APK to the system package installer.
 *
 * The app cannot install anything itself - only the platform installer can, and
 * only with the user's explicit consent on the screen it puts up. This class does
 * the two things that consent requires: expose the file through a
 * [FileProvider] `content://` URI (a `file://` URI to another process has been
 * rejected since Android 7), and grant read permission on it for the duration of
 * the intent.
 *
 * The user must also have allowed this app to request installs. That is a one-time
 * per-app toggle in system settings which no app can grant itself, so
 * [canRequestInstall] reports it and [openInstallPermissionSettings] takes the user
 * straight to the right screen rather than leaving them to find it.
 */
@Singleton
class ApkInstaller @Inject constructor() {

    /** Whether the "install unknown apps" permission has been granted to this app. */
    fun canRequestInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where that permission is granted. */
    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /**
     * Launches the platform installer for [apk].
     *
     * Returns false if no installer responded, which is the honest outcome to
     * report rather than an exception: on a device with package installation
     * disabled by policy there is nothing the app can do about it.
     */
    fun install(context: Context, apk: File): Boolean {
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.updates", apk)

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // The installer starts its own task; this call may also originate from
            // the application context rather than an Activity.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            true
        } catch (e: android.content.ActivityNotFoundException) {
            false
        }
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
