package io.github.vanrin.applock

import android.content.Context
import android.content.SharedPreferences

enum class RelockPolicy { IMMEDIATE, SCREEN_OFF, TIMEOUT }

/**
 * Single source of truth for all persisted state, fronted by an in-memory cache.
 *
 * The accessibility service consults this on every window event, so reads must
 * never touch disk: the cache is loaded once and kept coherent because every
 * write in the app goes through this object (service + UI share one process).
 */
object Prefs {
    private const val FILE = "applock"
    private const val KEY_LOCKED = "locked_packages"
    private const val KEY_POLICY = "relock_policy"
    private const val KEY_TIMEOUT_MIN = "relock_timeout_minutes"
    private const val KEY_AUTOLOCK_NEW = "autolock_new_apps"

    val DEFAULT_LOCKED = setOf("com.android.vending")

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var locked: Set<String> = emptySet()
    @Volatile var relockPolicy: RelockPolicy = RelockPolicy.IMMEDIATE
        private set
    @Volatile var relockTimeoutMinutes: Int = 1
        private set
    @Volatile var autoLockNewApps: Boolean = true
        private set

    private fun backing(ctx: Context): SharedPreferences {
        prefs?.let { return it }
        synchronized(this) {
            prefs?.let { return it }
            val p = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            locked = p.getStringSet(KEY_LOCKED, DEFAULT_LOCKED) ?: DEFAULT_LOCKED
            relockPolicy = runCatching {
                RelockPolicy.valueOf(p.getString(KEY_POLICY, null) ?: "")
            }.getOrDefault(RelockPolicy.IMMEDIATE)
            relockTimeoutMinutes = p.getInt(KEY_TIMEOUT_MIN, 1)
            autoLockNewApps = p.getBoolean(KEY_AUTOLOCK_NEW, true)
            prefs = p
            return p
        }
    }

    fun init(ctx: Context) { backing(ctx) }

    fun lockedApps(ctx: Context): Set<String> { backing(ctx); return locked }

    fun isLocked(ctx: Context, pkg: String): Boolean { backing(ctx); return pkg in locked }

    fun setLocked(ctx: Context, pkg: String, lock: Boolean) {
        val p = backing(ctx)
        synchronized(this) {
            val set = locked.toMutableSet()
            if (lock) set.add(pkg) else set.remove(pkg)
            locked = set
            p.edit().putStringSet(KEY_LOCKED, set).apply()
        }
        if (!lock) LockSession.forget(pkg)
    }

    fun setRelockPolicy(ctx: Context, policy: RelockPolicy) {
        val p = backing(ctx)
        relockPolicy = policy
        p.edit().putString(KEY_POLICY, policy.name).apply()
    }

    fun setRelockTimeoutMinutes(ctx: Context, minutes: Int) {
        val p = backing(ctx)
        relockTimeoutMinutes = minutes.coerceIn(1, 720)
        p.edit().putInt(KEY_TIMEOUT_MIN, relockTimeoutMinutes).apply()
    }

    fun setAutoLockNewApps(ctx: Context, on: Boolean) {
        val p = backing(ctx)
        autoLockNewApps = on
        p.edit().putBoolean(KEY_AUTOLOCK_NEW, on).apply()
    }
}
