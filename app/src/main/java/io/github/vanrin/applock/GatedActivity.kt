package io.github.vanrin.applock

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/** Process-wide self-lock session: cleared by AppLockApp when the last of our
 *  screens leaves the foreground, so reopening AppLock always re-authenticates. */
object SelfGate {
    @Volatile var authenticated = false
}

/**
 * AppLock guards other apps, so its own UI must be at least as hard to open.
 * Every screen extends this; the layout must include view_gate.xml as an
 * opaque overlay (R.id.gate / R.id.btnGateUnlock).
 */
abstract class GatedActivity : AppCompatActivity() {

    private var authInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recents thumbnails must not reveal which apps are protected.
        // Debug builds stay capturable so screenshots/docs are possible.
        if (!BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        Prefs.init(this)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        findViewById<Button>(R.id.btnGateUnlock).setOnClickListener { authenticate() }
    }

    override fun onStart() {
        super.onStart()
        updateGate()
        if (!SelfGate.authenticated && !authInProgress) authenticate()
    }

    private fun updateGate() {
        findViewById<View>(R.id.gate).visibility =
            if (SelfGate.authenticated) View.GONE else View.VISIBLE
        if (SelfGate.authenticated) onUnlocked()
    }

    /** Called once the gate drops (and on every onStart while authenticated). */
    protected open fun onUnlocked() {}

    private fun authenticate() {
        if (authInProgress) return
        authInProgress = true
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authInProgress = false
                    SelfGate.authenticated = true
                    updateGate()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authInProgress = false
                    updateGate()
                }
            }
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.app_name))
                .setSubtitle(getString(R.string.self_lock_subtitle))
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .setConfirmationRequired(false)
                .build()
        )
    }
}
