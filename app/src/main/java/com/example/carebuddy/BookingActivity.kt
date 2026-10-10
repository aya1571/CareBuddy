package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class BookingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_COMPANION = "companionName"
        const val EXTRA_RATE = "rate"
        const val EXTRA_COMPANION_UID = "companionId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking)

        // ===== GET DATA FROM INTENT =====
        val companionId = intent.getStringExtra("companionId").orEmpty()
        val companionName = intent.getStringExtra("companionName").orEmpty()
        val activity = intent.getStringExtra("activity").orEmpty()
        val date = intent.getStringExtra("date").orEmpty()
        val time = intent.getStringExtra("time").orEmpty()
        val location = intent.getStringExtra("location").orEmpty()
        val rate = intent.getStringExtra(EXTRA_RATE).orEmpty()
        if (listOf(companionId, companionName, activity, date, time, location).any { it.isBlank() }) {
            Toast.makeText(this, "Please start from Find Companion and choose your booking details", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // ===== BIND VIEWS =====
        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val tvCompanionName = findViewById<TextView>(R.id.tvCompanionName)
        val tvCompanionRating = findViewById<TextView>(R.id.tvCompanionRating)
        val tvActivity = findViewById<TextView>(R.id.tvActivity)
        val tvDate = findViewById<TextView>(R.id.tvDate)
        val tvTime = findViewById<TextView>(R.id.tvTime)
        val tvLocation = findViewById<TextView>(R.id.tvLocation)
        val etNotes = findViewById<EditText>(R.id.etNotes)
        val tvPrice = findViewById<TextView>(R.id.tvPrice)
        val btnConfirmBooking = findViewById<Button>(R.id.btnConfirmBooking)

        // ===== POPULATE FROM INTENT =====
        tvCompanionName.text = companionName
        tvCompanionRating.text = "No reviews yet"

        tvActivity.text = activity
        tvDate.text = date
        tvTime.text = time
        tvLocation.text = location

        // ===== ESTIMATED PRICE =====
        val hourlyRate = BookingPricing.hourlyRate(rate)
        if (hourlyRate <= 0.0) {
            Toast.makeText(this, "This companion has not set a valid hourly rate", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val durationHours = 2
        val price = BookingPricing.format(hourlyRate * durationHours)
        tvPrice.text = price

        // ===== CLOSE =====
        btnClose.setOnClickListener { finish() }

        // ===== CONFIRM BOOKING =====
        btnConfirmBooking.setOnClickListener {
            val notes = etNotes.text.toString().trim()
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnConfirmBooking.isEnabled = false
            FirebaseCareBuddyStore.loadUserProfile(user.uid) { profile, error ->
                if (isFinishing || isDestroyed) return@loadUserProfile

                if (error != null || profile == null) {
                    btnConfirmBooking.isEnabled = true
                    Toast.makeText(
                        this,
                        "We couldn't check your verification status. Check your connection and try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@loadUserProfile
                }

                val accountRole = (profile["role"] as? String).orEmpty()
                val accountStatus = (profile["status"] as? String).orEmpty()
                val verificationStatus = (profile["verificationStatus"] as? String).orEmpty()
                val blockMessage = when {
                    !accountRole.equals("elderly", ignoreCase = true) ->
                        "Only elderly accounts can create bookings."
                    !accountStatus.equals("Active", ignoreCase = true) ->
                        "This account is not active. Please contact the CareBuddy administrator."
                    verificationStatus.equals("rejected", ignoreCase = true) ->
                        "Your IC verification was rejected. Contact the administrator for help."
                    !verificationStatus.equals("approved", ignoreCase = true) ->
                        "Your IC verification is pending. You can book after an administrator approves it."
                    else -> null
                }

                if (blockMessage != null) {
                    btnConfirmBooking.isEnabled = true
                    Toast.makeText(this, blockMessage, Toast.LENGTH_LONG).show()
                    return@loadUserProfile
                }

                saveVerifiedBooking(
                    ownerUid = user.uid,
                    notes = notes,
                    companionId = companionId,
                    companionName = companionName,
                    activityName = activity,
                    date = date,
                    time = time,
                    location = location,
                    rate = rate,
                    price = price,
                    durationHours = durationHours,
                    confirmButton = btnConfirmBooking
                )
            }
        }
    }

    private fun saveVerifiedBooking(
        ownerUid: String,
        notes: String,
        companionId: String,
        companionName: String,
        activityName: String,
        date: String,
        time: String,
        location: String,
        rate: String,
        price: String,
        durationHours: Int,
        confirmButton: Button
    ) {
        val prefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
        val ownerEmail = prefs.getString("current_email", "").orEmpty()
        val ownerName = prefs.getString("current_name", "").orEmpty()
        val numericPrice = price.replace("RM", "").replace(" ", "").toDoubleOrNull() ?: 0.0
        val booking = CareBooking(
            id = "CB" + System.currentTimeMillis(),
            ownerUid = ownerUid,
            ownerEmail = ownerEmail,
            ownerName = ownerName,
            companionUid = companionId,
            companion = companionName,
            rate = rate,
            date = date,
            time = time,
            payment = "Card",
            status = "Pending",
            activity = activityName,
            location = location,
            notes = notes,
            durationHours = durationHours,
            estimatedTotal = numericPrice
        )

        FirebaseCareBuddyStore.saveBooking(booking) { error ->
            if (isFinishing || isDestroyed) return@saveBooking
            confirmButton.isEnabled = true
            if (error != null) {
                Toast.makeText(
                    this,
                    "Could not save booking: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
                return@saveBooking
            }

            FirebaseCareBuddyStore.createNotification(
                userId = booking.companionUid,
                type = "booking",
                title = "New Booking Request",
                message = "${booking.ownerName.ifBlank { "A user" }} booked you for ${booking.activity}",
                bookingId = booking.id
            )

            startActivity(Intent(this, PaymentActivity::class.java).apply {
                putExtra("bookingId", booking.id)
                putExtra("companionId", companionId)
                putExtra("companionName", companionName)
                putExtra("activity", activityName)
                putExtra("date", date)
                putExtra("time", time)
                putExtra("location", location)
                putExtra("price", price)
                putExtra("notes", notes)
            })
            finish()
        }
    }
}
