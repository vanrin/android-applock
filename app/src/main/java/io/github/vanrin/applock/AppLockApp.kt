package io.github.vanrin.applock

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.android.material.color.DynamicColors

class AppLockApp : Application() {

    private var startedGatedScreens = 0

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        Prefs.init(this)

        // Drop the self-lock session as soon as the last AppLock screen leaves
        // the foreground: reopening the app always asks for biometrics again.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (activity is GatedActivity) startedGatedScreens++
            }

            override fun onActivityStopped(activity: Activity) {
                if (activity is GatedActivity && --startedGatedScreens <= 0) {
                    startedGatedScreens = 0
                    SelfGate.authenticated = false
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
