package com.example.carebuddy

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class ChangePasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        // ===== BIND VIEWS =====
        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val etCurrentPassword = findViewById<EditText>(R.id.etCurrentPassword)
        val etNewPassword = findViewById<EditText>(R.id.etNewPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnUpdate = findViewById<Button>(R.id.btnUpdatePassword)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)

        btnClose.setOnClickListener { finish() }

        // ===== UPDATE PASSWORD =====
        btnUpdate.setOnClickListener {
            val currentPassword = etCurrentPassword.text.toString()
            val newPassword = etNewPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            // ===== VALIDATION =====
            when {
                currentPassword.isBlank() -> {
                    Toast.makeText(this, "Please enter your current password", Toast.LENGTH_SHORT).show()
                }
                newPassword.isBlank() -> {
                    Toast.makeText(this, "Please enter a new password", Toast.LENGTH_SHORT).show()
                }
                newPassword.length < 6 -> {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                }
                newPassword == currentPassword -> {
                    Toast.makeText(this, "New password must be different from current", Toast.LENGTH_SHORT).show()
                }
                newPassword != confirmPassword -> {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    updatePassword(btnUpdate, currentPassword, newPassword)
                }
            }
        }

        // ===== FORGOT PASSWORD =====
        tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    // ===================================================================
    // UPDATE PASSWORD
    // ===================================================================
    private fun updatePassword(btnUpdate: Button, currentPassword: String, newPassword: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val email = user.email
        if (email.isNullOrBlank()) {
            Toast.makeText(this, "Account email not found", Toast.LENGTH_SHORT).show()
            return
        }

        btnUpdate.isEnabled = false
        btnUpdate.text = "Updating..."

        // ===== STEP 1: RE-AUTHENTICATE =====
        val credential = EmailAuthProvider.getCredential(email, currentPassword)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                // ===== STEP 2: UPDATE PASSWORD =====
                user.updatePassword(newPassword)
                    .addOnSuccessListener {
                        btnUpdate.isEnabled = true
                        btnUpdate.text = "Update Password"

                        AlertDialog.Builder(this)
                            .setTitle("Password Updated")
                            .setMessage("Your password has been changed successfully.")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    }
                    .addOnFailureListener { error ->
                        btnUpdate.isEnabled = true
                        btnUpdate.text = "Update Password"
                        Toast.makeText(
                            this,
                            "Failed to update: ${error.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { error ->
                btnUpdate.isEnabled = true
                btnUpdate.text = "Update Password"

                val msg = when {
                    error.localizedMessage?.contains("password is invalid", true) == true ->
                        "Current password is incorrect"
                    error.localizedMessage?.contains("network", true) == true ->
                        "Network error. Check your connection."
                    else -> error.localizedMessage ?: "Authentication failed"
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
    }

    // ===================================================================
    // FORGOT PASSWORD DIALOG
    // ===================================================================
    private fun showForgotPasswordDialog() {
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email

        if (email.isNullOrBlank()) {
            Toast.makeText(this, "Email not available", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Reset password")
            .setMessage("We'll send a password reset link to:\n\n$email")
            .setPositiveButton("Send Email") { _, _ ->
                FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        Toast.makeText(
                            this,
                            "Reset link sent. Check your inbox.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(
                            this,
                            "Failed: ${error.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}