package io.github.vanrin.applock

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Battery notes — why this service costs ~nothing:
 *  - Purely event-driven: the process sleeps until the system posts a window
 *    state change. No polling, no wakelocks, no foreground service, no alarms.
 *  - canRetrieveWindowContent is OFF, so the system never serializes view trees
 *    for us (the main battery cost of accessibility services).
 *  - Every check below is an in-memory set lookup; disk is never touched on the
 *    event path.
 */
class AppLockService : AccessibilityService() {

    private var currentForeground: String? = null

    // Keyboards fire window events while typing; never treat them as an app switch.
    private var imePackages: Set<String> = emptySet()

    private val ignoredPackages = setOf("com.android.systemui", "android")

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = LockSession.onScreenOff()
    }

    /**
     * Manifest receivers cannot get PACKAGE_ADDED since Android 8, but this
     * process lives as long as the accessibility service is enabled, so a
     * runtime-registered receiver gives us the iPhone-style "every new app
     * needs biometric" behaviour for free.
     */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED -> if (!replacing && pkg != packageName &&
                    Prefs.autoLockNewApps &&
                    packageManager.getLaunchIntentForPackage(pkg) != null
                ) {
                    Prefs.setLocked(context, pkg, true)
                    notifyNewAppLocked(pkg)
                }
                Intent.ACTION_PACKAGE_REMOVED -> if (!replacing) {
                    Prefs.setLocked(context, pkg, false)
                }
            }
            refreshImePackages() // a keyboard may have been installed/removed/updated
        }
    }

    override fun onServiceConnected() {
        Prefs.init(this)
        refreshImePackages()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
        createNotificationChannel()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        runCatching { unregisterReceiver(packageReceiver) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in ignoredPackages || pkg in imePackages) return

        if (LockSession.promptActive) {
            // On API < 30 the device-credential fallback is a Settings activity;
            // never re-prompt over it or PIN entry becomes impossible.
            if (Build.VERSION.SDK_INT < 30 && pkg == "com.android.settings") return
            // Apps like Play Store surface a second window right after launch,
            // which can land on top of the lock screen — re-raise it. Everything
            // else (launcher, credential UI) is ignored while the prompt is up.
            if (Prefs.isLocked(this, pkg) && !LockSession.isUnlocked(pkg)) launchLockScreen(pkg)
            return
        }

        if (pkg != currentForeground) {
            currentForeground = pkg
            LockSession.onForegroundChanged(pkg)
        }

        if (Prefs.isLocked(this, pkg) && !LockSession.isUnlocked(pkg)) launchLockScreen(pkg)
    }

    private fun launchLockScreen(pkg: String) {
        // Duplicate launches are safe: singleInstance + CLEAR_TOP route them to
        // onNewIntent, and LockScreenActivity ignores re-prompts while prompting.
        startActivity(
            Intent(this, LockScreenActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                putExtra(LockScreenActivity.EXTRA_PACKAGE, pkg)
            }
        )
    }

    override fun onInterrupt() = Unit

    private fun refreshImePackages() {
        imePackages = (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .inputMethodList.map { it.packageName }.toSet()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NEW_APP,
                getString(R.string.channel_new_app),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    private fun notifyNewAppLocked(pkg: String) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, CHANNEL_NEW_APP)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(getString(R.string.new_app_locked_title))
            .setContentText(getString(R.string.new_app_locked_text, label))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(pkg.hashCode(), n) }
    }

    companion object {
        private const val CHANNEL_NEW_APP = "new_app_locked"
    }
}
