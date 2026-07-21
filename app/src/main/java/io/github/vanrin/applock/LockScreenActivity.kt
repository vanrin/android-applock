package io.github.vanrin.applock

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class LockScreenActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE = "locked_package"
    }

    private lateinit var lockedPkg: String
    private var prompting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        if (pkg == null) { goHome(); return }
        lockedPkg = pkg
        setContentView(R.layout.activity_lock)
        bindApp()

        findViewById<Button>(R.id.btnUnlock).setOnClickListener { showPrompt() }

        // Back must NOT reveal the locked app underneath — send user home instead.
        onBackPressedDispatcher.addCallback(this) { goHome() }

        showPrompt()
    }

    // singleInstance: a second locked app reuses this instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        if (pkg != lockedPkg) {
            lockedPkg = pkg
            bindApp()
        }
        showPrompt()
    }

    override fun onStart() {
        super.onStart()
        LockSession.promptActive = true
    }

    override fun onStop() {
        LockSession.promptActive = false
        prompting = false
        super.onStop()
    }

    private fun bindApp() {
        val pm = packageManager
        val (label, icon) = runCatching {
            val info = pm.getApplicationInfo(lockedPkg, 0)
            pm.getApplicationLabel(info).toString() to pm.getApplicationIcon(info)
        }.getOrElse { lockedPkg to null }
        findViewById<TextView>(R.id.txtApp).text = getString(R.string.locked_message, label)
        findViewById<ImageView>(R.id.imgApp).setImageDrawable(
            icon ?: ContextCompat.getDrawable(this, R.drawable.ic_lock)
        )
    }

    private fun showPrompt() {
        if (prompting) return
        prompting = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    LockSession.unlock(lockedPkg)
                    LockSession.promptActive = false
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Cancelled or locked out: stay here. User can retry with the
                    // button or press Back to go home.
                    prompting = false
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_name))
            .setSubtitle(getString(R.string.auth_subtitle))
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
