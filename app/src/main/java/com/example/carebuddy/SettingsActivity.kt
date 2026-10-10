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

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // ===== BIND VIEWS =====
        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val switchNotifications = findViewById<SwitchCompat>(R.id.switchNotifications)

        val menuLanguage = findViewById<LinearLayout>(R.id.menuLanguage)
        val menuPrivacy = findViewById<LinearLayout>(R.id.menuPrivacy)
        val menuHelp = findViewById<LinearLayout>(R.id.menuHelp)
        val menuAbout = findViewById<LinearLayout>(R.id.menuAbout)
        val menuUserManual = findViewById<LinearLayout>(R.id.menuUserManual)

        val tvLanguageValue = findViewById<TextView>(R.id.tvLanguageValue)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        // ===== LOAD SAVED SETTINGS =====
        val prefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        val currentLanguage = prefs.getString("app_language", "English") ?: "English"

        switchNotifications.isChecked = notificationsEnabled
        tvLanguageValue.text = currentLanguage

        // ===== CLOSE =====
        btnClose.setOnClickListener { finish() }

        // ===== NOTIFICATIONS TOGGLE =====
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply()
            val msg = if (isChecked) "Notifications enabled" else "Notifications disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // ===== LANGUAGE SELECTION =====
        menuLanguage.setOnClickListener {
            showLanguageDialog()
        }

        // ===== PRIVACY POLICY =====
        menuPrivacy.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        // ===== USER MANUAL =====
        menuUserManual.setOnClickListener {
            startActivity(
                Intent(this, UserManualActivity::class.java)
                    .putExtra("role", getRoleForManual())
            )
        }

        // ===== HELP & SUPPORT =====
        menuHelp.setOnClickListener {
            showHelpSupport()
        }

        // ===== ABOUT US =====
        menuAbout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("About CareBuddy")
                .setMessage("CareBuddy v1.0.0\n\nFriendly, trusted companionship for everyday life.\n\n© 2025 CareBuddy Team.")
                .setPositiveButton("OK", null)
                .show()
        }

        // ===== LOGOUT =====
        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes, Logout") { _, _ ->
                    performLogout()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ===================================================================
    // LANGUAGE SELECTION DIALOG
    // ===================================================================
    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Bahasa Malaysia", "中文 (Chinese)")
        val prefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
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

                findViewById<TextView>(R.id.tvLanguageValue).text = chosen

                Toast.makeText(
                    this,
                    "Language set to $chosen",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHelpSupport() {
        val topics = arrayOf(
            "Sign in and account",
            "IC verification",
            "Find and book a companion",
            "Booking status and chat",
            "Trip location",
            "Emergency help",
            "Payments",
            "Connection and sync"
        )
        AlertDialog.Builder(this)
            .setTitle("CareBuddy Help & Support")
            .setItems(topics) { _, item ->
                val answer = when (item) {
                    0 -> "Register with email and password or use Google if enabled. If you forget your password, use Forgot Password on the login screen. Choose the correct role for your account."
                    1 -> "Upload a clear photo of the required identity document and face photo. Your account stays pending until an admin reviews it. Use test identities for school demonstrations and read the Privacy Policy first."
                    2 -> "Search companions, open a profile, choose the activity, date, time and location, then confirm the request. The companion must be approved and available."
                    3 -> "Pending and accepted requests appear under Upcoming in My Bookings. Companions can review requests in Booking Requests. Chat is tied to a booking and closes when the booking is completed or cancelled."
                    4 -> "For an active booking, open its details. When the companion starts the trip and grants location permission, the booking owner can choose Track Companion. Sharing stops when the companion marks arrival. Location needs internet and device location services."
                    5 -> "Open Emergency Help during an active booking and choose 999 only for a genuine emergency, or call your saved emergency contact. CareBuddy opens the phone dialer; you confirm the call on your phone."
                    6 -> "This student project records the selected payment method and estimated amount only. It does not charge cards, online banking or Touch 'n Go, and does not transfer payouts."
                    else -> "Bookings sync through Firebase when the device is online and Firestore rules are published. If changes do not appear, check internet, Firebase sign-in and the published rules, then reopen the screen."
                }
                AlertDialog.Builder(this).setTitle(topics[item]).setMessage(answer).setPositiveButton("OK", null).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun getRoleForManual(): String {
        val prefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
        return prefs.getString("current_role", "elderly").orEmpty().lowercase()
    }

    // ===================================================================
    // LOGOUT — clear session and return to Splash
    // ===================================================================
    private fun performLogout() {
        // 1. Firebase sign-out
        FirebaseAuth.getInstance().signOut()

        // 2. Clear saved session data (but keep language preference)
        val prefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
        val language = prefs.getString("app_language", "English")
        val notifications = prefs.getBoolean("notifications_enabled", true)

        prefs.edit().clear().apply()

        // Restore app-level settings (not tied to user)
        prefs.edit()
            .putString("app_language", language)
            .putBoolean("notifications_enabled", notifications)
            .apply()

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()

        // 3. Go to Splash and clear back stack
        val intent = Intent(this, SplashActivity::class.java)
        intent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        )
        startActivity(intent)
        finish()
    }
}
