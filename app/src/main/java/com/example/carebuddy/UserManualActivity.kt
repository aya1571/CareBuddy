package com.example.carebuddy

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class UserManualActivity : AppCompatActivity() {

    private lateinit var stepsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_manual)

        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val tvManualTitle = findViewById<TextView>(R.id.tvManualTitle)
        val tvManualSubtitle = findViewById<TextView>(R.id.tvManualSubtitle)
        stepsContainer = findViewById(R.id.stepsContainer)

        btnClose.setOnClickListener { finish() }

        // ===== DETECT ROLE =====
        val role = intent.getStringExtra("role")
            ?: getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
                .getString("current_role", "elderly")
                .orEmpty()
                .lowercase()

        // ===== LOAD ROLE-SPECIFIC CONTENT =====
        when (role) {
            "companion" -> {
                tvManualTitle.text = "How to use CareBuddy (Companion)"
                tvManualSubtitle.text = "A simple guide for companions."
                loadCompanionSteps()
            }
            "admin" -> {
                tvManualTitle.text = "Admin Guide"
                tvManualSubtitle.text = "A guide for administrators."
                loadAdminSteps()
            }
            else -> {
                tvManualTitle.text = "How to use CareBuddy"
                tvManualSubtitle.text = "A simple guide to help you get started."
                loadElderlySteps()
            }
        }
    }

    // ===================================================================
    // ELDERLY STEPS
    // ===================================================================
    private fun loadElderlySteps() {
        addStepCard(
            icon = "🏠",
            title = "Home",
            description = "Your main page shows a welcome message, a search bar to find companions, and your upcoming bookings."
        )
        addStepCard(
            icon = "🔍",
            title = "Find a Companion",
            description = "Tap the search bar. Choose the activity (Hospital, Shopping, Errands), pick a date, time, and enter your location. Then tap Search."
        )
        addStepCard(
            icon = "🤝",
            title = "Book a Companion",
            description = "Tap a companion from the list. Review their profile, then tap 'Book Companion'. Fill in your notes and confirm."
        )
        addStepCard(
            icon = "💳",
            title = "Payment",
            description = "Choose your preferred payment method: Card, Online Banking, Touch 'n Go, or Cash on Arrival. Then tap Pay Now."
        )
        addStepCard(
            icon = "📋",
            title = "My Bookings",
            description = "View all your bookings here — Upcoming, Ongoing, Completed, or Cancelled. Tap a booking to see details or cancel."
        )
        addStepCard(
            icon = "⭐",
            title = "Rate Your Companion",
            description = "After a booking is completed, you can rate your companion and leave a review to help others."
        )
        addStepCard(
            icon = "👤",
            title = "Profile",
            description = "View and update your personal information. You can edit your name, add a profile photo, and change your password."
        )
        addStepCard(
            icon = "🔔",
            title = "Notifications",
            description = "Tap the bell icon to see updates about your bookings and important announcements."
        )
    }

    // ===================================================================
    // COMPANION STEPS
    // ===================================================================
    private fun loadCompanionSteps() {
        addStepCard(
            icon = "🏠",
            title = "Home Dashboard",
            description = "Your dashboard shows today's bookings, your earnings this week, and your availability status."
        )
        addStepCard(
            icon = "✅",
            title = "Set Availability",
            description = "Toggle the availability switch ON to receive booking requests. You can also set your weekly schedule."
        )
        addStepCard(
            icon = "📥",
            title = "Booking Requests",
            description = "When elderly users book you, requests appear in the Requests tab. Tap Accept to confirm or Decline to reject."
        )
        addStepCard(
            icon = "📋",
            title = "My Bookings",
            description = "View all your accepted, ongoing, and completed jobs. Tap a booking to see details or mark it as complete."
        )
        addStepCard(
            icon = "✔️",
            title = "Complete a Job",
            description = "After finishing a job, open it from My Bookings and tap 'Mark Visit as Complete' to close it."
        )
        addStepCard(
            icon = "💰",
            title = "Earnings",
            description = "View your total earnings, count of completed jobs, and breakdown by payment method."
        )
        addStepCard(
            icon = "👤",
            title = "Profile",
            description = "Update your bio, skills, and hourly rate so elderly users can find you. Add a profile photo for trust."
        )
        addStepCard(
            icon = "🔔",
            title = "Notifications",
            description = "Receive alerts for new booking requests and important updates."
        )
    }

    // ===================================================================
    // ADMIN STEPS
    // ===================================================================
    private fun loadAdminSteps() {
        addStepCard(
            icon = "📊",
            title = "Dashboard",
            description = "See platform statistics at a glance: total users, companions, pending verifications, and active bookings."
        )
        addStepCard(
            icon = "👥",
            title = "User Management",
            description = "View all elderly users. Suspend accounts that violate rules, or restore suspended ones."
        )
        addStepCard(
            icon = "🧑‍⚕️",
            title = "Companion Management",
            description = "View all companions, their rating, availability, and verification status. Suspend or restore as needed."
        )
        addStepCard(
            icon = "✅",
            title = "Verification Requests",
            description = "Review submitted IC and face photos. Approve or reject each submission. Approved companions can receive bookings."
        )
        addStepCard(
            icon = "📅",
            title = "Booking Management",
            description = "View all bookings across the platform. Filter by status (Pending, Accepted, Completed, Cancelled) and cancel if needed."
        )
        addStepCard(
            icon = "💳",
            title = "Payment Management",
            description = "See payment records for all bookings. Track which methods are being used most."
        )
        addStepCard(
            icon = "📈",
            title = "Reports & Reviews",
            description = "View platform statistics and all customer reviews. Identify top-rated companions and areas for improvement."
        )
        addStepCard(
            icon = "📢",
            title = "Announcements",
            description = "Send important messages to all users. They appear in the notifications screen."
        )
        addStepCard(
            icon = "⚙️",
            title = "Settings",
            description = "Configure platform settings and sign out of your admin account."
        )
    }

    // ===================================================================
    // ADD A STEP CARD
    // ===================================================================
    private fun addStepCard(icon: String, title: String, description: String) {
        // ===== CONTAINER CARD =====
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = ContextCompat.getDrawable(this@UserManualActivity, R.drawable.bg_card)
        }
        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(10)
        }

        // ===== ICON =====
        val iconView = TextView(this).apply {
            text = icon
            textSize = 32f
            gravity = Gravity.CENTER
        }
        val iconParams = LinearLayout.LayoutParams(dp(48), dp(48))
        card.addView(iconView, iconParams)

        // ===== TEXT COLUMN =====
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        val textParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )

        // Title
        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@UserManualActivity, R.color.care_ink))
            setTypeface(typeface, Typeface.BOLD)
        }
        textColumn.addView(titleView)

        // Description
        val descView = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@UserManualActivity, R.color.care_muted))
            setPadding(0, dp(6), 0, 0)
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        textColumn.addView(descView)

        card.addView(textColumn, textParams)

        // ===== ADD TO CONTAINER =====
        stepsContainer.addView(card, cardParams)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}