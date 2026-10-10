package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private val SPLASH_DELAY: Long = 1500 // 1.5 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            routeUser()
        }, SPLASH_DELAY)
    }

    private fun routeUser() {
        val prefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
        val user = FirebaseAuth.getInstance().currentUser

        // 1. No Firebase user → Welcome screen
        if (user == null) {
            goTo(MainActivity::class.java)
            return
        }

        // 2. Firebase user exists → check local session
        val email = user.email?.trim()?.lowercase().orEmpty()
        val savedEmail = prefs.getString("current_email", "").orEmpty()
        val role = prefs.getString("current_role", "").orEmpty()

        // 2a. Session mismatch → Welcome screen
        if (email.isBlank() || !savedEmail.equals(email, ignoreCase = true)) {
            goTo(MainActivity::class.java)
            return
        }

        // 2b. Invalid role → Welcome screen
        if (role !in setOf("elderly", "companion", "admin")) {
            goTo(MainActivity::class.java)
            return
        }

        // 2c. Load user details
        val name = prefs.getString("current_name", "")
            .orEmpty()
            .ifBlank { user.displayName.orEmpty() }

        // 2d. IC verification pending → IC screen
        if (prefs.getBoolean("ic_verification_pending", false)) {
            startActivity(
                Intent(this, IcVerificationActivity::class.java)
                    .putExtra("name", name)
                    .putExtra("email", email)
                    .putExtra(RolePortalActivity.EXTRA_ROLE, role)
            )
            finish()
            return
        }

        // 2e. Route by role
        when (role) {
            "elderly" -> {
                startActivity(
                    Intent(this, HomeActivity::class.java)
                        .putExtra("name", name)
                        .addFlags(clearFlags())
                )
                finish()
            }
            "companion" -> {
                startActivity(
                    Intent(this, CompanionHomeActivity::class.java)
                        .putExtra("name", name)
                        .addFlags(clearFlags())
                )
                finish()
            }
            "admin" -> {
                startActivity(
                    Intent(this, AdminLoginActivity::class.java)
                        .addFlags(clearFlags())
                )
                finish()
            }
            else -> goTo(MainActivity::class.java)
        }
    }

    private fun goTo(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
        finish()
    }

    private fun clearFlags(): Int {
        return Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
}