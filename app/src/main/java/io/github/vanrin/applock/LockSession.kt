package io.github.vanrin.applock

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory unlock sessions. Nothing here is persisted: a reboot locks everything.
 *
 * Timeout bookkeeping is evaluated lazily when events arrive — no Handler, no
 * alarm, no scheduled work — so the process stays idle between window changes.
 */
object LockSession {

    /** elapsedRealtime when the app left the foreground; null while it is foreground. */
    private class Session(@Volatile var leftForegroundAt: Long? = null)

    private val sessions = ConcurrentHashMap<String, Session>()

    /** True while LockScreenActivity is in front. The service must not launch
     *  another prompt on top of it (the device-credential fallback screen is
     *  provided by the Settings package, which the user may have locked). */
    @Volatile var promptActive = false

    fun unlock(pkg: String) { sessions[pkg] = Session() }

    fun forget(pkg: String) { sessions.remove(pkg) }

    fun clearAll() = sessions.clear()

    fun isUnlocked(pkg: String): Boolean {
        val s = sessions[pkg] ?: return false
        val leftAt = s.leftForegroundAt ?: return true
        return when (Prefs.relockPolicy) {
            RelockPolicy.IMMEDIATE -> false // should have been removed already; be safe
            RelockPolicy.SCREEN_OFF -> true
            RelockPolicy.TIMEOUT -> {
                val graceMs = Prefs.relockTimeoutMinutes * 60_000L
                if (SystemClock.elapsedRealtime() - leftAt <= graceMs) true
                else { sessions.remove(pkg); false }
            }
        }
    }

    /** Called on every foreground app change with the package now in front. */
    fun onForegroundChanged(nowForeground: String) {
        for ((pkg, s) in sessions) {
            if (pkg == nowForeground) {
                // Re-entered within grace (or still unlocked): resume the session.
                if (isUnlocked(pkg)) s.leftForegroundAt = null
            } else if (s.leftForegroundAt == null) {
                when (Prefs.relockPolicy) {
                    RelockPolicy.IMMEDIATE -> sessions.remove(pkg)
                    else -> s.leftForegroundAt = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    /** Screen off always ends IMMEDIATE and SCREEN_OFF sessions; for TIMEOUT it
     *  starts the grace timer even if the app is still in the foreground. */
    fun onScreenOff() {
        when (Prefs.relockPolicy) {
            RelockPolicy.TIMEOUT -> {
                val now = SystemClock.elapsedRealtime()
                for (s in sessions.values) if (s.leftForegroundAt == null) s.leftForegroundAt = now
            }
            else -> sessions.clear()
        }
    }
}
