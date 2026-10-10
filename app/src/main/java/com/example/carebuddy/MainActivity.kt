package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val email = findViewById<EditText>(R.id.etEmail)
        val password = findViewById<EditText>(R.id.etPassword)
        val loginButton = findViewById<Button>(R.id.btnLogin)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        val googleButton = findViewById<Button>(R.id.btnGoogleSignIn)
        val registerButton = findViewById<Button>(R.id.btnRegister)
        val tvAdminAccess = findViewById<TextView>(R.id.tvAdminAccess)

        val auth = FirebaseAuth.getInstance()
        val prefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)

        // ===== REGISTER BUTTON =====
        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // ===== FORGOT PASSWORD =====
        tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        // ===== ADMIN ACCESS =====
        tvAdminAccess.setOnClickListener {
            startActivity(Intent(this, AdminLoginActivity::class.java))
        }

        // ===== LOGIN BUTTON =====
        loginButton.setOnClickListener {
            val emailAddress = email.text.toString().trim().lowercase()
            val passwordText = password.text.toString()

            when {
                emailAddress.isBlank() || passwordText.isBlank() -> {
                    Toast.makeText(this, "Please enter your email and password", Toast.LENGTH_SHORT).show()
                }
                !Patterns.EMAIL_ADDRESS.matcher(emailAddress).matches() -> {
                    Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    loginButton.isEnabled = false
                    auth.signInWithEmailAndPassword(emailAddress, passwordText)
                        .addOnCompleteListener(this) { task ->
                            loginButton.isEnabled = true
                            if (!task.isSuccessful) {
                                Toast.makeText(
                                    this,
                                    task.exception?.localizedMessage ?: "Sign-in failed.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@addOnCompleteListener
                            }

                            val user = task.result.user
                            if (user == null) {
                                auth.signOut()
                                Toast.makeText(this, "Sign-in failed. Please try again.", Toast.LENGTH_LONG).show()
                                return@addOnCompleteListener
                            }

                            // ===== READ ROLE FROM FIRESTORE =====
                            handleLoginSuccess(user, emailAddress, prefs)
                        }
                }
            }
        }

        // ===== GOOGLE SIGN-IN =====
        googleButton.setOnClickListener {
            startGoogleSignIn(googleButton)
        }
    }

    // ===================================================================
    // HANDLE LOGIN SUCCESS — read role from Firestore, route accordingly
    // ===================================================================
    private fun handleLoginSuccess(
        user: FirebaseUser,
        emailAddress: String,
        prefs: android.content.SharedPreferences
    ) {
        FirebaseCareBuddyStore.loadUserProfile(user.uid) { profile, error ->
            if (isFinishing || isDestroyed) return@loadUserProfile

            if (error != null || profile == null) {
                // No Firestore doc — treat as new user
                FirebaseAuth.getInstance().signOut()
                Toast.makeText(
                    this,
                    "No profile found. Please register first.",
                    Toast.LENGTH_LONG
                ).show()
                return@loadUserProfile
            }

            // ===== CHECK ACCOUNT STATUS =====
            val status = profile["status"] as? String ?: "Active"
            if (status != "Active") {
                FirebaseAuth.getInstance().signOut()
                Toast.makeText(
                    this,
                    "This account is suspended. Contact CareBuddy support.",
                    Toast.LENGTH_LONG
                ).show()
                return@loadUserProfile
            }

            // ===== READ ROLE =====
            val role = (profile["role"] as? String ?: "elderly").lowercase()
            val displayName = (profile["displayName"] as? String ?: "").ifBlank {
                prefs.getString("profile_name:$emailAddress", "").orEmpty()
            }

            // ===== SAVE SESSION =====
            prefs.edit()
                .putString("current_email", emailAddress)
                .putString("current_name", displayName)
                .putString("current_role", role)
                .putString("profile_role:$emailAddress", role)
                .putString("profile_name:$emailAddress", displayName)
                .putBoolean("ic_verification_pending", false)
                .apply()

            if (role == "companion") {
                prefs.edit()
                    .putString("companion_name", displayName.ifBlank { "Companion" })
                    .apply()
            }

            // ===== ROUTE BY ROLE =====
            val destination = when (role) {
                "elderly" -> HomeActivity::class.java
                "companion" -> CompanionHomeActivity::class.java
                "admin" -> AdminDashboardActivity::class.java
                else -> HomeActivity::class.java
            }

            startActivity(
                Intent(this, destination)
                    .putExtra("name", displayName)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
            )
            finish()
        }
    }

    // ===================================================================
    // GOOGLE SIGN-IN
    // ===================================================================
    private fun startGoogleSignIn(button: Button) {
        val webClientIdResource = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (webClientIdResource == 0) {
            Toast.makeText(this, "Google sign-in needs the updated google-services.json from Firebase.", Toast.LENGTH_LONG).show()
            return
        }

        val googleOption = GetGoogleIdOption.Builder()
            .setServerClientId(getString(webClientIdResource))
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        button.isEnabled = false
        lifecycleScope.launch {
            try {
                val result = CredentialManager.create(this@MainActivity)
                    .getCredential(this@MainActivity, request)
                val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(firebaseCredential)
                    .addOnCompleteListener(this@MainActivity) { task ->
                        button.isEnabled = true
                        val user = task.result?.user
                        if (!task.isSuccessful || user == null) {
                            val message = task.exception?.localizedMessage ?: "Google sign-in failed."
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                            return@addOnCompleteListener
                        }
                        handleGoogleAccount(user)
                    }
            } catch (error: Exception) {
                button.isEnabled = true
                Toast.makeText(
                    this@MainActivity,
                    error.localizedMessage ?: "Could not open Google sign-in.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun handleGoogleAccount(user: FirebaseUser) {
        val email = user.email?.trim()?.lowercase().orEmpty()
        if (email.isBlank()) {
            FirebaseAuth.getInstance().signOut()
            Toast.makeText(this, "Google did not provide an email address.", Toast.LENGTH_LONG).show()
            return
        }

        FirebaseCareBuddyStore.loadUserProfile(user.uid) { profile, _ ->
            if (isFinishing || isDestroyed) return@loadUserProfile

            if (profile == null) {
                // New Google user — ask for phone + role
                askForGoogleProfileDetails(user, email)
                return@loadUserProfile
            }

            val status = profile["status"] as? String ?: "Active"
            if (status != "Active") {
                FirebaseAuth.getInstance().signOut()
                Toast.makeText(this, "This account is suspended.", Toast.LENGTH_LONG).show()
                return@loadUserProfile
            }

            val role = (profile["role"] as? String ?: "elderly").lowercase()
            val displayName = (profile["displayName"] as? String ?: "").ifBlank {
                user.displayName.orEmpty()
            }

            val prefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
            prefs.edit()
                .putString("current_email", email)
                .putString("current_name", displayName)
                .putString("current_role", role)
                .putString("profile_role:$email", role)
                .putString("profile_name:$email", displayName)
                .putBoolean("ic_verification_pending", false)
                .apply()

            if (role == "companion") {
                prefs.edit().putString("companion_name", displayName.ifBlank { "Companion" }).apply()
            }

            val destination = when (role) {
                "elderly" -> HomeActivity::class.java
                "companion" -> CompanionHomeActivity::class.java
                "admin" -> AdminDashboardActivity::class.java
                else -> HomeActivity::class.java
            }

            startActivity(
                Intent(this, destination)
                    .putExtra("name", displayName)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
            )
            finish()
        }
    }



    // ===================================================================
    // NEW GOOGLE USER — ask for phone + role
    // ===================================================================
    private fun askForGoogleProfileDetails(user: FirebaseUser, email: String) {
        val name = user.displayName.orEmpty().ifBlank { email.substringBefore('@') }

        val dialogView = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val phoneInput = EditText(this).apply {
            hint = "Mobile phone number"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            textSize = 16f
        }
        dialogView.addView(phoneInput)

        val roleLabel = android.widget.TextView(this).apply {
            text = "I am registering as:"
            setPadding(0, 32, 0, 8)
            textSize = 14f
        }
        dialogView.addView(roleLabel)

        val roleSpinner = android.widget.Spinner(this)
        val roles = arrayOf("Elderly", "Companion")
        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            roles
        )
        roleSpinner.adapter = adapter
        dialogView.addView(roleSpinner)

        AlertDialog.Builder(this)
            .setTitle("Finish creating your account")
            .setView(dialogView)
            .setNegativeButton("Cancel") { _, _ -> FirebaseAuth.getInstance().signOut() }
            .setPositiveButton("Continue", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val phone = phoneInput.text.toString().trim()
                        val role = roleSpinner.selectedItem?.toString()?.lowercase().orEmpty()

                        if (phone.length < 7) {
                            phoneInput.error = "Enter a valid phone number"
                            return@setOnClickListener
                        }

                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false

                        FirebaseCareBuddyStore.createAccountProfile(
                            user.uid, email, name, phone, role
                        ) { error ->
                            if (isFinishing || isDestroyed) return@createAccountProfile

                            if (error != null) {
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                                Toast.makeText(this, "Could not save your profile.", Toast.LENGTH_LONG).show()
                            } else {
                                dialog.dismiss()

                                getSharedPreferences("carebuddy_demo", MODE_PRIVATE).edit()
                                    .putString("current_email", email)
                                    .putString("current_name", name)
                                    .putString("current_role", role)
                                    .putString("profile_name:$email", name)
                                    .putString("profile_role:$email", role)
                                    .putBoolean("ic_verification_pending", true)
                                    .apply()

                                startActivity(
                                    Intent(this, IcVerificationActivity::class.java)
                                        .putExtra("name", name)
                                        .putExtra("email", email)
                                        .putExtra(RolePortalActivity.EXTRA_ROLE, role)
                                )
                                finish()
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    // ===================================================================
// FORGOT PASSWORD DIALOG
// ===================================================================
    private fun showForgotPasswordDialog() {
        // Pre-fill with what the user typed in the email field, if any
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val prefilledEmail = etEmail.text.toString().trim()

        // Create input field for the dialog
        val emailInput = EditText(this).apply {
            hint = "Enter your registered email"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(prefilledEmail)
            textSize = 16f
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Reset Password")
            .setMessage("We'll send a password reset link to your email.")
            .setView(emailInput)
            .setPositiveButton("Send") { _, _ ->
                val email = emailInput.text.toString().trim().lowercase()

                if (email.isBlank()) {
                    Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                sendPasswordResetEmail(email)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ===================================================================
// SEND PASSWORD RESET EMAIL
// ===================================================================
    private fun sendPasswordResetEmail(email: String) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnSuccessListener {
                AlertDialog.Builder(this)
                    .setTitle("Email Sent")
                    .setMessage("A password reset link has been sent to:\n\n$email\n\nCheck your inbox and spam folder.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .addOnFailureListener { error ->
                val msg = when {
                    error.localizedMessage?.contains("no user record", true) == true ->
                        "No account found with that email"
                    error.localizedMessage?.contains("network", true) == true ->
                        "Network error. Check your connection."
                    else -> error.localizedMessage ?: "Failed to send reset email"
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
    }
}