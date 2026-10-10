package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class AdminDashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        // ===== BIND VIEWS =====
        val tvAdminName = findViewById<TextView>(R.id.tvAdminName)
        val btnLogout = findViewById<ImageView>(R.id.btnLogout)
        val tvTotalUsers = findViewById<TextView>(R.id.tvTotalUsers)
        val tvTotalCompanions = findViewById<TextView>(R.id.tvTotalCompanions)
        val tvPendingVerifications = findViewById<TextView>(R.id.tvPendingVerifications)
        val tvActiveBookings = findViewById<TextView>(R.id.tvActiveBookings)

        // ===== LOAD ADMIN NAME =====
        val prefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
        val name = prefs.getString("current_name", "Admin") ?: "Admin"
        tvAdminName.text = "Welcome, $name"

        // ===== LOAD STATS FROM FIRESTORE =====
        loadStats(tvTotalUsers, tvTotalCompanions, tvPendingVerifications, tvActiveBookings)

        // ===== QUICK ACTIONS =====
        findViewById<LinearLayout>(R.id.actionUserManagement).setOnClickListener {
            startActivity(Intent(this, UserManagementActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.actionCompanionManagement).setOnClickListener {
            startActivity(Intent(this, CompanionManagementActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.actionVerification).setOnClickListener {
            startActivity(Intent(this, VerificationActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.actionBookingManagement).setOnClickListener {
            startActivity(Intent(this, BookingManagementActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.actionPaymentManagement).setOnClickListener {
            startActivity(Intent(this, PaymentManagementActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.actionReports).setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.actionSettings).setOnClickListener {
            startActivity(Intent(this, AdminSettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.actionAnnouncements).setOnClickListener {
            startActivity(Intent(this, AnnouncementsActivity::class.java))
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
    // LOAD STATS FROM FIRESTORE
    // ===================================================================
    private fun loadStats(
        tvUsers: TextView,
        tvCompanions: TextView,
        tvPending: TextView,
        tvBookings: TextView
    ) {
        // Default placeholders while loading
        tvUsers.text = "…"
        tvCompanions.text = "…"
        tvPending.text = "…"
        tvBookings.text = "…"

        FirebaseCareBuddyStore.loadAllUsers { users, error ->
            if (error == null) {
                val elderlyCount = users.count { it["role"] == "elderly" }
                val companionCount = users.count { it["role"] == "companion" }
                tvUsers.text = elderlyCount.toString()
                tvCompanions.text = companionCount.toString()
            } else {
                tvUsers.text = "—"
                tvCompanions.text = "—"
            }
        }

        FirebaseCareBuddyStore.loadPendingVerificationsBase64 { submissions, error ->
            tvPending.text = if (error == null) submissions.size.toString() else "—"
        }

        // Active bookings = bookings on this device with status Pending or Accepted
        val allBookings = BookingStore.all(this)
        val active = allBookings.count { it.status in listOf("Pending", "Accepted", "On The Way", "In Progress") }
        tvBookings.text = active.toString()
    }

    // ===================================================================
    // OPEN ROLE PORTAL FOR DETAIL MANAGEMENT
    // ===================================================================
    private fun openPortal(screen: String) {
        startActivity(
            Intent(this, RolePortalActivity::class.java)
                .putExtra(RolePortalActivity.EXTRA_ROLE, "admin")
                .putExtra(RolePortalActivity.EXTRA_SCREEN, screen)
        )
    }

    // ===================================================================
    // LOGOUT
    // ===================================================================
    private fun performLogout() {
        FirebaseAuth.getInstance().signOut()

        val prefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
        val language = prefs.getString("app_language", "English")
        val notifications = prefs.getBoolean("notifications_enabled", true)
        prefs.edit().clear().apply()
        prefs.edit()
            .putString("app_language", language)
            .putBoolean("notifications_enabled", notifications)
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
