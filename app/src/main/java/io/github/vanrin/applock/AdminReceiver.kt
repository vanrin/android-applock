package io.github.vanrin.applock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Holds no policies; being an active admin is enough to block plain uninstall
 * until the admin is deactivated (which sits behind the locked Settings app).
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disable_warning)
}
