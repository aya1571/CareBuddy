package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.auth.FirebaseAuth
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class AdminSettingsActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_settings)

        // ===== BIND =====
        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val switchNotifications = findViewById<SwitchCompat>(R.id.switchNotifications)
        val switchBookingRequests = findViewById<SwitchCompat>(R.id.switchBookingRequests)

        val menuLanguage = findViewById<LinearLayout>(R.id.menuLanguage)
        val menuPrivacy = findViewById<LinearLayout>(R.id.menuPrivacy)
        val menuHelp = findViewById<LinearLayout>(R.id.menuHelp)
        val menuAbout = findViewById<LinearLayout>(R.id.menuAbout)
        val menuUserManual = findViewById<LinearLayout>(R.id.menuUserManual)

        val tvLanguageValue = findViewById<TextView>(R.id.tvLanguageValue)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        // ===== LOAD SAVED VALUES =====
        switchNotifications.isChecked = prefs.getBoolean("notifications_enabled", true)
        switchBookingRequests.isChecked = prefs.getBoolean("booking_requests_enabled", true)
        tvLanguageValue.text = prefs.getString("app_language", "English") ?: "English"

        // ===== CLOSE =====
        btnClose.setOnClickListener { finish() }

        // ===== NOTIFICATIONS TOGGLE =====
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply()
            Toast.makeText(
                this,
                if (isChecked) "Notifications enabled" else "Notifications disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        // ===== BOOKING REQUESTS TOGGLE =====
        switchBookingRequests.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("booking_requests_enabled", isChecked).apply()
            Toast.makeText(
                this,
                if (isChecked) "New bookings enabled" else "New bookings paused",
                Toast.LENGTH_SHORT
            ).show()
        }

        // ===== LANGUAGE =====
        menuLanguage.setOnClickListener {
            showLanguageDialog(tvLanguageValue)
        }

        // ===== PRIVACY POLICY =====
        menuPrivacy.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        // ===== HELP & SUPPORT =====
        menuHelp.setOnClickListener {
            showHelpSupport()
        }

        // ===== USER MANUAL =====
        menuUserManual.setOnClickListener {
            startActivity(
                Intent(this, UserManualActivity::class.java)
                    .putExtra("role", "admin")
            )
        }

        // ===== ABOUT =====
        menuAbout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("About CareBuddy")
                .setMessage(
                    "CareBuddy Admin v1.0.0\n\n" +
                            "Companion platform administration.\n\n" +
                            "© 2025 CareBuddy Team"
                )
                .setPositiveButton("OK", null)
                .show()
        }

        // ===== LOGOUT =====
        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out of Admin?")
                .setPositiveButton("Yes, Sign Out") { _, _ -> performLogout() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ===================================================================
    // LANGUAGE DIALOG
    // ===================================================================
    private fun showLanguageDialog(tvValue: TextView) {
        val languages = arrayOf("English", "Bahasa Malaysia", "中文 (Chinese)")
        val current = prefs.getString("app_language", "English") ?: "English"
        val currentIndex = languages.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Choose Language")
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                val chosen = languages[which]
                prefs.edit().putString("app_language", chosen).apply()
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(when (chosen) {
                        "Bahasa Malaysia" -> "ms"
                        "中文 (Chinese)" -> "zh"
                        else -> "en"
                    })
                )
                tvValue.text = chosen
                Toast.makeText(this, "Language set to $chosen", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHelpSupport() {
        AlertDialog.Builder(this)
            .setTitle("CareBuddy Admin Help")
            .setMessage("Use User Management to review user accounts and Companion Management to review companion profiles. Verification shows submitted identity documents for manual review. Booking Management shows requests and active services. Payment Management is a record view only: it does not process charges or payouts. Reports summarize booking and review data. Publish Firestore rules after changing them in the project.")
            .setPositiveButton("OK", null)
            .show()
    }

    // ===================================================================
    // LOGOUT
    // ===================================================================
    private fun performLogout() {
        FirebaseAuth.getInstance().signOut()

        val language = prefs.getString("app_language", "English")
        val notifs = prefs.getBoolean("notifications_enabled", true)
        val bookings = prefs.getBoolean("booking_requests_enabled", true)
        prefs.edit().clear().apply()
        prefs.edit()
            .putString("app_language", language)
            .putBoolean("notifications_enabled", notifs)
            .putBoolean("booking_requests_enabled", bookings)
            .apply()

        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()

        startActivity(
            Intent(this, SplashActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
        )
        finish()
    }
}
