package io.github.vanrin.applock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsActivity : GatedActivity() {

    private val adminComponent by lazy { ComponentName(this, AdminReceiver::class.java) }
    private val dpm by lazy { getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    private val pm by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // --- Relock policy ---
        val group = findViewById<RadioGroup>(R.id.relockGroup)
        val minutes = findViewById<EditText>(R.id.timeoutMinutes)
        when (Prefs.relockPolicy) {
            RelockPolicy.IMMEDIATE -> group.check(R.id.relockImmediate)
            RelockPolicy.SCREEN_OFF -> group.check(R.id.relockScreenOff)
            RelockPolicy.TIMEOUT -> group.check(R.id.relockTimeout)
        }
        minutes.setText(Prefs.relockTimeoutMinutes.toString())
        minutes.isEnabled = Prefs.relockPolicy == RelockPolicy.TIMEOUT

        group.setOnCheckedChangeListener { _, id ->
            val policy = when (id) {
                R.id.relockScreenOff -> RelockPolicy.SCREEN_OFF
                R.id.relockTimeout -> RelockPolicy.TIMEOUT
                else -> RelockPolicy.IMMEDIATE
            }
            Prefs.setRelockPolicy(this, policy)
            minutes.isEnabled = policy == RelockPolicy.TIMEOUT
            // Changing policy invalidates existing grace timers.
            LockSession.clearAll()
        }
        minutes.doAfterTextChanged {
            it?.toString()?.toIntOrNull()?.let { m -> Prefs.setRelockTimeoutMinutes(this, m) }
        }

        // --- Auto-lock new installs ---
        findViewById<MaterialSwitch>(R.id.switchAutoLock).apply {
            isChecked = Prefs.autoLockNewApps
            setOnCheckedChangeListener { _, on -> Prefs.setAutoLockNewApps(this@SettingsActivity, on) }
        }

        // --- Lock the Settings app (blocks disabling the service / uninstall) ---
        findViewById<MaterialSwitch>(R.id.switchLockSettings).apply {
            isChecked = Prefs.isLocked(this@SettingsActivity, SETTINGS_PKG)
            setOnCheckedChangeListener { _, on ->
                Prefs.setLocked(this@SettingsActivity, SETTINGS_PKG, on)
            }
        }

        findViewById<Button>(R.id.btnAdmin).setOnClickListener {
            if (dpm.isAdminActive(adminComponent)) {
                dpm.removeActiveAdmin(adminComponent)
                refreshButtons()
            } else {
                startActivity(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            getString(R.string.admin_explanation),
                        )
                    }
                )
            }
        }

        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                )
            )
        }
    }

    override fun onUnlocked() = refreshButtons()

    private fun refreshButtons() {
        val adminOn = dpm.isAdminActive(adminComponent)
        findViewById<Button>(R.id.btnAdmin).text =
            getString(if (adminOn) R.string.admin_disable else R.string.admin_enable)
        findViewById<TextView>(R.id.txtAdminStatus).text =
            getString(if (adminOn) R.string.admin_on else R.string.admin_off)

        val exempt = pm.isIgnoringBatteryOptimizations(packageName)
        findViewById<Button>(R.id.btnBattery).visibility =
            if (exempt) Button.GONE else Button.VISIBLE
        findViewById<TextView>(R.id.txtBatteryStatus).text =
            getString(if (exempt) R.string.battery_exempt_on else R.string.battery_exempt_off)
    }

    companion object {
        private const val SETTINGS_PKG = "com.android.settings"
    }
}
