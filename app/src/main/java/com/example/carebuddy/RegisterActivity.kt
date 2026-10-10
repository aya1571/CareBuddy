package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // ===== FIND VIEWS =====
        val name = findViewById<EditText>(R.id.etName)
        val email = findViewById<EditText>(R.id.etEmail)
        val phone = findViewById<EditText>(R.id.etPhone)
        val icNumber = findViewById<EditText>(R.id.etIcNumber)
        val password = findViewById<EditText>(R.id.etPassword)
        val confirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val registerButton = findViewById<Button>(R.id.btnRegister)
        val role = findViewById<Spinner>(R.id.spRegisterRole)
        val gender = findViewById<Spinner>(R.id.spRegisterGender)
        val genderOptions = listOf("Prefer not to say", "Female", "Male")
        gender.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genderOptions)
        val auth = FirebaseAuth.getInstance()

        // ===== SETUP ROLE SPINNER =====
        val roles = resources.getStringArray(R.array.registration_roles)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            roles
        )
        role.adapter = adapter

        // ===== REGISTER BUTTON LOGIC =====
        registerButton.setOnClickListener {
            val nameText = name.text.toString().trim()
            val emailText = email.text.toString().trim().lowercase()
            val phoneText = phone.text.toString().trim()
            val icText = icNumber.text.toString().trim()
            val passwordText = password.text.toString()
            val confirmPasswordText = confirmPassword.text.toString()
            val selectedRole = when (role.selectedItemPosition) {
                0 -> "elderly"
                1 -> "companion"
                else -> ""
            }
            val genderText = gender.selectedItem?.toString().orEmpty()

            when {
                // 1. Check all fields are filled
                nameText.isBlank() || emailText.isBlank() || phoneText.isBlank() ||
                        icText.isBlank() || passwordText.isBlank() || confirmPasswordText.isBlank() -> {
                    Toast.makeText(this, "Please complete all fields", Toast.LENGTH_SHORT).show()
                }
                // 2. NEW: Check role is selected
                selectedRole.isBlank() -> {
                    Toast.makeText(this, "Please select a role", Toast.LENGTH_SHORT).show()
                }
                // 3. Check IC number format
                icText.length != 12 || !icText.all { it.isDigit() } -> {
                    Toast.makeText(this, "Please enter a valid 12-digit IC number", Toast.LENGTH_SHORT).show()
                }
                // 4. Check email format
                !Patterns.EMAIL_ADDRESS.matcher(emailText).matches() -> {
                    Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                }
                // 5. Check passwords match
                passwordText != confirmPasswordText -> {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                }
                // 6. Check password length
                passwordText.length < 6 -> {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                }
                // 7. All good — create account
                else -> {
                    registerButton.isEnabled = false
                    auth.createUserWithEmailAndPassword(emailText, passwordText)
                        .addOnCompleteListener(this) { task ->
                            registerButton.isEnabled = true
                            if (!task.isSuccessful) {
                                Toast.makeText(
                                    this,
                                    task.exception?.localizedMessage ?: "Registration failed. Please try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@addOnCompleteListener
                            }

                            val prefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
                            prefs.edit()
                                .putString("current_email", emailText)
                                .putString("current_name", nameText)
                                .putString("current_role", selectedRole)
                                .putString("profile_name:$emailText", nameText)
                                .putString("profile_role:$emailText", selectedRole)
                                .putBoolean("ic_verification_pending", true)
                                .apply()

                            val user = auth.currentUser
                            if (user == null) {
                                Toast.makeText(
                                    this,
                                    "Account created, but sign-in could not be restored. Please log in.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@addOnCompleteListener
                            }

                            FirebaseCareBuddyStore.createAccountProfile(
                                user.uid, emailText, nameText, phoneText, selectedRole, genderText
                            ) { profileError ->
                                if (profileError != null) {
                                    Toast.makeText(
                                        this,
                                        "Account created. Shared profile setup is pending: ${profileError.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                startActivity(
                                    Intent(this, IcVerificationActivity::class.java)
                                        .putExtra("name", nameText)
                                        .putExtra("email", emailText)
                                        .putExtra(RolePortalActivity.EXTRA_ROLE, selectedRole)
                                )
                            }
                        }
                }
            }
        }
    }
}
