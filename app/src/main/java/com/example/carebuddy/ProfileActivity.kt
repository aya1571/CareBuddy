package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class ProfileActivity : AppCompatActivity() {

    private var isCompanion = false

    // ===== VIEWS AS CLASS FIELDS =====
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileEmail: TextView
    private lateinit var tvProfilePhone: TextView
    private lateinit var tvVerifiedLabel: TextView
    private lateinit var ivProfilePhoto: ImageView
    private lateinit var tvProfileInitial: TextView

    private lateinit var bioCard: LinearLayout
    private lateinit var skillsCard: LinearLayout
    private lateinit var rateCard: LinearLayout

    private lateinit var tvBio: TextView
    private lateinit var tvSkills: TextView
    private lateinit var tvHourlyRate: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // ===== DETECT ROLE =====
        val role = intent.getStringExtra("role")
            ?: getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
                .getString("current_role", "elderly")
                .orEmpty()
        isCompanion = role.equals("companion", ignoreCase = true)

        // ===== BIND VIEWS =====
        val btnClose = findViewById<ImageView>(R.id.btnClose)
        ivProfilePhoto = findViewById(R.id.ivProfilePhoto)
        tvProfileInitial = findViewById(R.id.tvProfileInitial)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        tvProfileName = findViewById(R.id.tvProfileName)
        tvProfileEmail = findViewById(R.id.tvProfileEmail)
        tvProfilePhone = findViewById(R.id.tvProfilePhone)
        tvVerifiedLabel = findViewById(R.id.tvVerifiedLabel)

        bioCard = findViewById(R.id.bioCard)
        skillsCard = findViewById(R.id.skillsCard)
        rateCard = findViewById(R.id.rateCard)

        tvBio = findViewById(R.id.tvBio)
        tvSkills = findViewById(R.id.tvSkills)
        tvHourlyRate = findViewById(R.id.tvHourlyRate)

        val menuEditProfile = findViewById<LinearLayout>(R.id.menuEditProfile)
        val menuMyBookings = findViewById<LinearLayout>(R.id.menuMyBookings)
        val menuEarnings = findViewById<LinearLayout>(R.id.menuEarnings)
        val menuReviews = findViewById<LinearLayout>(R.id.menuReviews)
        val menuSettings = findViewById<LinearLayout>(R.id.menuSettings)
        val menuChangePassword = findViewById<LinearLayout>(R.id.menuChangePassword)
        val menuEmergencyContact = findViewById<LinearLayout>(R.id.menuEmergencyContact)
        val menuUserManual = findViewById<LinearLayout>(R.id.menuUserManual)

        // ===== CLOSE =====
        btnClose.setOnClickListener { finish() }

        // ===== SET HEADER =====
        tvHeaderTitle.text = if (isCompanion) "Companion Profile" else "Profile"

        // ===== SHOW/HIDE COMPANION-ONLY CARDS =====
        val companionCardVisibility = if (isCompanion) View.VISIBLE else View.GONE
        bioCard.visibility = companionCardVisibility
        skillsCard.visibility = companionCardVisibility
        rateCard.visibility = companionCardVisibility

        // ===== SHOW/HIDE MENU ITEMS =====
        menuEarnings.visibility = if (isCompanion) View.VISIBLE else View.GONE
        menuReviews.visibility = if (isCompanion) View.GONE else View.VISIBLE

        // ===== LOAD PROFILE FROM FIRESTORE =====
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadProfile(uid)

        // ===== MENU CLICKS =====
        menuEditProfile.setOnClickListener {
            startActivity(
                Intent(this, EditProfileActivity::class.java)
                    .putExtra("role", if (isCompanion) "companion" else "elderly")
            )
        }

        menuMyBookings.setOnClickListener {
            startActivity(
                Intent(this, MyBookingsActivity::class.java)
                    .putExtra("role", if (isCompanion) "companion" else "elderly")
            )
        }

        menuEarnings.setOnClickListener {
            startActivity(Intent(this, EarningsActivity::class.java))
        }

        menuReviews.setOnClickListener {
            startActivity(Intent(this, MyReviewsActivity::class.java))
        }

        menuChangePassword.setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        menuEmergencyContact.setOnClickListener {
            startActivity(Intent(this, EmergencyContactActivity::class.java))
        }

        menuUserManual.setOnClickListener {
            startActivity(
                Intent(this, UserManualActivity::class.java)
                    .putExtra("role", if (isCompanion) "companion" else "elderly")
            )
        }

        menuSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // ===================================================================
    // LOAD PROFILE
    // ===================================================================
    private fun loadProfile(uid: String) {
        FirebaseCareBuddyStore.loadUserProfile(uid) { userProfile, userError ->
            if (isFinishing || isDestroyed) return@loadUserProfile

            if (userError != null || userProfile == null) {
                // Fall back to prefs
                val prefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
                val fallbackName = prefs.getString("current_name", "User") ?: "User"
                tvProfileName.text = fallbackName
                tvProfileEmail.text = prefs.getString("current_email", "—") ?: "—"
                tvProfilePhone.text = prefs.getString("current_phone", "—") ?: "—"

                // Show initial, hide photo
                showInitial(fallbackName)
            } else {
                val displayName = (userProfile["displayName"] as? String).orEmpty()
                val email = (userProfile["email"] as? String).orEmpty()
                val phone = (userProfile["phone"] as? String).orEmpty()
                val verification = (userProfile["verificationStatus"] as? String).orEmpty()
                val photoBase64 = userProfile["photoBase64"] as? String

                tvProfileName.text = displayName.ifBlank { "User" }
                tvProfileEmail.text = email.ifBlank { "—" }
                tvProfilePhone.text = phone.ifBlank { "—" }

                // ===== VERIFICATION BADGE =====
                updateVerificationBadge(verification)

                // ===== PROFILE PHOTO =====
                if (!photoBase64.isNullOrBlank()) {
                    val bitmap = PhotoHelper.base64ToBitmap(photoBase64)
                    if (bitmap != null) {
                        ivProfilePhoto.setImageBitmap(bitmap)
                        ivProfilePhoto.visibility = View.VISIBLE
                        tvProfileInitial.visibility = View.GONE
                    } else {
                        showInitial(displayName)
                    }
                } else {
                    showInitial(displayName)
                }
            }

            // ===== IF COMPANION: LOAD COMPANION PROFILE =====
            if (isCompanion) {
                loadCompanionDetails(uid)
            }
        }
    }

    // ===================================================================
    // RELOAD ON RESUME (so edits show immediately)
    // ===================================================================
    override fun onResume() {
        super.onResume()
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            loadProfile(uid)   // reload everything (name, photo, companion details)
        }
    }

    // ===================================================================
    // LOAD COMPANION-SPECIFIC DETAILS
    // ===================================================================
    private fun loadCompanionDetails(uid: String) {
        // Show loading state
        tvBio.text = "Loading..."
        tvSkills.text = "Loading..."
        tvHourlyRate.text = "Loading..."

        FirebaseCareBuddyStore.loadCompanionProfile(uid) { profile, error ->
            if (isFinishing || isDestroyed) return@loadCompanionProfile

            if (error != null || profile == null) {
                tvBio.text = "Not set — tap Edit Profile to add"
                tvSkills.text = "No skills listed yet"
                tvHourlyRate.text = "Not set"
                return@loadCompanionProfile
            }

            // ===== BIO =====
            val bio = (profile["bio"] as? String).orEmpty().trim()
            tvBio.text = if (bio.isBlank() || bio == "CareBuddy companion") {
                "Not set — tap Edit Profile to add your bio"
            } else {
                bio
            }

            // ===== SKILLS =====
            val skills = (profile["skills"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            tvSkills.text = if (skills.isEmpty()) {
                "No skills listed yet"
            } else {
                skills.joinToString(" · ")
            }

            // ===== HOURLY RATE =====
            val rate = (profile["hourlyRate"] as? String).orEmpty().trim()
            tvHourlyRate.text = if (rate.isBlank()) {
                "Not set"
            } else {
                rate
            }

            // ===== VERIFICATION =====
            val verification = (profile["verificationStatus"] as? String).orEmpty()
            updateVerificationBadge(verification)
        }
    }

    // ===================================================================
    // SHOW INITIAL LETTER (when no photo)
    // ===================================================================
    private fun showInitial(name: String) {
        ivProfilePhoto.visibility = View.GONE
        tvProfileInitial.visibility = View.VISIBLE
        tvProfileInitial.text = name.trim().firstOrNull()?.uppercase()?.toString() ?: "?"
    }

    // ===================================================================
    // UPDATE VERIFICATION BADGE
    // ===================================================================
    private fun updateVerificationBadge(status: String) {
        val label = when (status.lowercase()) {
            "approved" -> "Verified"
            "rejected" -> "Not Verified"
            "pending" -> "Pending Review"
            else -> "Pending Review"
        }
        tvVerifiedLabel.text = label
    }
}
