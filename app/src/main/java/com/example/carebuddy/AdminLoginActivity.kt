package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminLoginActivity : AppCompatActivity() {

    // Admin secret code (change before production!)
    private val ADMIN_SECRET = "123456"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)

        // ===== BIND VIEWS =====
        val etAdminEmail = findViewById<EditText>(R.id.etAdminEmail)
        val etAdminPassword = findViewById<EditText>(R.id.etAdminPassword)
        val etAdminCode = findViewById<EditText>(R.id.etAdminCode)
        val btnAdminSignIn = findViewById<Button>(R.id.btnAdminSignIn)
        val tvBackToUserLogin = findViewById<TextView>(R.id.tvBackToUserLogin)

        // ===== BACK TO USER LOGIN =====
        tvBackToUserLogin.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // ===== SIGN IN =====
        btnAdminSignIn.setOnClickListener {
            val email = etAdminEmail.text.toString().trim().lowercase()
            val password = etAdminPassword.text.toString()
            val code = etAdminCode.text.toString().trim()

            // ===== VALIDATION =====
            when {
                email.isBlank() || password.isBlank() || code.isBlank() -> {
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                }
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show()
                }
                code != ADMIN_SECRET -> {
                    Toast.makeText(this, "Invalid admin code", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // ===== FIREBASE SIGN IN =====
                    btnAdminSignIn.isEnabled = false
                    FirebaseAuth.getInstance()
                        .signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            btnAdminSignIn.isEnabled = true
                            if (!task.isSuccessful) {
                                Toast.makeText(
                                    this,
                                    "Login failed: ${task.exception?.localizedMessage}",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@addOnCompleteListener
                            }

                            // ===== VERIFY ROLE IS ADMIN =====
                            val user = FirebaseAuth.getInstance().currentUser
                            if (user == null) {
                                Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show()
                                return@addOnCompleteListener
                            }

                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(user.uid)
                                .get()
                                .addOnSuccessListener { doc ->
                                    val role = doc.getString("role")
                                    if (role != "admin") {
                                        FirebaseAuth.getInstance().signOut()
                                        Toast.makeText(
                                            this,
                                            "This account is not an admin.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@addOnSuccessListener
                                    }

                                    // ===== SAVE SESSION =====
                                    val prefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
                                    prefs.edit()
                                        .putString("current_email", email)
                                        .putString("current_name", doc.getString("displayName") ?: "Admin")
                                        .putString("current_role", "admin")
                                        .apply()

                                    Toast.makeText(this, "Welcome, Admin!", Toast.LENGTH_SHORT).show()

                                    // ===== GO TO DASHBOARD =====
                                    startActivity(
                                        Intent(this, AdminDashboardActivity::class.java)
                                    )
                                    finish()
                                }
                                .addOnFailureListener { error ->
                                    Toast.makeText(
                                        this,
                                        "Could not verify admin: ${error.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                }
            }
        }
    }
}