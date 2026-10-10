package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class CompanionProfileActivity : AppCompatActivity() {

    private var companionId: String = ""
    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileDetails: TextView
    private lateinit var tvProfileRating: TextView
    private lateinit var tvExperienceBadge: TextView
    private lateinit var tvAvailableBadge: TextView
    private lateinit var tvAbout: TextView
    private lateinit var btnBookCompanion: Button
    private lateinit var layoutSkills: LinearLayout
    private var loadedName = "Companion"
    private var loadedRate = ""
    private var isAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_companion_profile)
        companionId = intent.getStringExtra("companionId").orEmpty()
        tvProfileName = findViewById(R.id.tvProfileName)
        tvProfileDetails = findViewById(R.id.tvProfileDetails)
        tvProfileRating = findViewById(R.id.tvProfileRating)
        tvExperienceBadge = findViewById(R.id.tvExperienceBadge)
        tvAvailableBadge = findViewById(R.id.tvAvailableBadge)
        tvAbout = findViewById(R.id.tvAbout)
        btnBookCompanion = findViewById(R.id.btnBookCompanion)
        layoutSkills = findViewById(R.id.layoutSkills)

        findViewById<ImageView>(R.id.btnClose).setOnClickListener { finish() }
        btnBookCompanion.isEnabled = false
        btnBookCompanion.text = "Loading profile..."
        btnBookCompanion.setOnClickListener {
            if (!isAvailable) {
                Toast.makeText(this, "This companion is currently unavailable", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, BookingActivity::class.java)
                .putExtra("companionId", companionId)
                .putExtra("companionName", loadedName)
                .putExtra("rate", loadedRate)
                .putExtra("activity", intent.getStringExtra("activity"))
                .putExtra("date", intent.getStringExtra("date"))
                .putExtra("time", intent.getStringExtra("time"))
                .putExtra("location", intent.getStringExtra("location")))
        }
        if (companionId.isBlank()) {
            Toast.makeText(this, "Companion not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        loadProfile()
    }

    private fun loadProfile() {
        FirebaseCareBuddyStore.loadCompanionProfile(companionId) { profile, error ->
            if (isFinishing || isDestroyed) return@loadCompanionProfile
            if (error != null || profile == null || profile["verificationStatus"] != "approved") {
                Toast.makeText(this, "This companion profile is no longer available", Toast.LENGTH_LONG).show()
                finish()
                return@loadCompanionProfile
            }
            loadedName = profile["displayName"] as? String
                ?: intent.getStringExtra("companionName") ?: "Companion"
            loadedRate = (profile["hourlyRate"] as? String).orEmpty().ifBlank { "RM 30/hour" }
            isAvailable = profile["available"] as? Boolean ?: false
            val gender = (profile["gender"] as? String).orEmpty()
            val location = (profile["location"] as? String).orEmpty()
            val bio = (profile["bio"] as? String).orEmpty()
            val skills = (profile["skills"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            val years = (profile["yearsExperience"] as? Number)?.toInt()

            tvProfileName.text = loadedName
            tvProfileDetails.text = listOf(gender, location).filter { it.isNotBlank() }.joinToString(" • ")
            tvProfileRating.text = "No reviews yet"
            tvExperienceBadge.text = years?.let { if (it >= 3) "$it years experience" else "New companion" } ?: "Experience not listed"
            tvAvailableBadge.text = if (isAvailable) "Available" else "Currently unavailable"
            if (!isAvailable) tvAvailableBadge.setBackgroundResource(R.drawable.bg_chip)
            tvAbout.text = bio.ifBlank { "The companion has not added a bio yet." }
            renderSkills(skills)
            btnBookCompanion.isEnabled = isAvailable
            btnBookCompanion.text = if (isAvailable) "Book Companion" else "Unavailable right now"
        }
    }

    private fun renderSkills(skills: List<String>) {
        layoutSkills.removeAllViews()
        val values = skills.ifEmpty { listOf("No skills listed") }
        values.take(6).forEach { skill ->
            val chip = TextView(this).apply {
                text = skill
                setTextColor(ContextCompat.getColor(this@CompanionProfileActivity, R.color.care_purple))
                setBackgroundResource(R.drawable.bg_chip_small)
                textSize = 12f
                setPadding(16, 10, 16, 10)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 8, 8) }
            }
            layoutSkills.addView(chip)
        }
    }
}
