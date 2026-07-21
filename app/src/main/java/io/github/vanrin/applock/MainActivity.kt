package io.github.vanrin.applock

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : GatedActivity() {

    private lateinit var adapter: AppListAdapter

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = AppListAdapter { pkg, locked ->
            Prefs.setLocked(this, pkg, locked)
            updateCount()
        }
        findViewById<RecyclerView>(R.id.appList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        findViewById<EditText>(R.id.search).doAfterTextChanged {
            adapter.filter(it?.toString().orEmpty())
        }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnFixAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnFixOverlay).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onUnlocked() {
        refreshStatus()
        loadApps() // re-query every time: picks up apps installed since last visit
    }

    private fun refreshStatus() {
        val serviceOn = isAccessibilityServiceEnabled()
        val overlayOn = Settings.canDrawOverlays(this)

        findViewById<TextView>(R.id.txtAccessibilityStatus).text =
            getString(if (serviceOn) R.string.status_service_on else R.string.status_service_off)
        findViewById<Button>(R.id.btnFixAccessibility).visibility =
            if (serviceOn) Button.GONE else Button.VISIBLE

        findViewById<TextView>(R.id.txtOverlayStatus).text =
            getString(if (overlayOn) R.string.status_overlay_on else R.string.status_overlay_off)
        findViewById<Button>(R.id.btnFixOverlay).visibility =
            if (overlayOn) Button.GONE else Button.VISIBLE

        findViewById<TextView>(R.id.txtProtection).visibility =
            if (serviceOn && overlayOn) TextView.GONE else TextView.VISIBLE
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val component = ComponentName(this, AppLockService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any {
            it.equals(component.flattenToString(), true) ||
                it.equals(component.flattenToShortString(), true)
        }
    }

    private fun updateCount() {
        findViewById<TextView>(R.id.txtCount).text =
            getString(R.string.locked_count, Prefs.lockedApps(this).size)
    }

    private fun loadApps() {
        Executors.newSingleThreadExecutor().execute {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val locked = Prefs.lockedApps(this)
            val entries = packageManager.queryIntentActivities(intent, 0)
                .map {
                    AppListAdapter.AppEntry(
                        label = it.loadLabel(packageManager).toString(),
                        pkg = it.activityInfo.packageName,
                    )
                }
                .distinctBy { it.pkg }
                .filter { it.pkg != packageName }
                .sortedWith(
                    compareByDescending<AppListAdapter.AppEntry> { it.pkg in locked }
                        .thenBy { it.label.lowercase() }
                )
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                adapter.submit(entries, locked)
                updateCount()
            }
        }
    }
}
