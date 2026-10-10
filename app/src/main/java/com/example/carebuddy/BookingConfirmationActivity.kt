package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
class BookingConfirmationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking_confirmation)

        // ===== GET DATA FROM INTENT =====
        val bookingId = intent.getStringExtra("bookingId").orEmpty()
        val companionName = intent.getStringExtra("companionName").orEmpty()
        val date = intent.getStringExtra("date").orEmpty()
        val time = intent.getStringExtra("time").orEmpty()
        val location = intent.getStringExtra("location").orEmpty()
        val paymentMethod = intent.getStringExtra("paymentMethod").orEmpty()

        // ===== BIND VIEWS =====
        val tvBookingId = findViewById<TextView>(R.id.tvBookingId)
        val tvCompanionName = findViewById<TextView>(R.id.tvCompanionName)
        val tvDateTime = findViewById<TextView>(R.id.tvDateTime)
        val tvLocation = findViewById<TextView>(R.id.tvLocation)
        val btnViewBookings = findViewById<Button>(R.id.btnViewBookings)
        val btnBackHome = findViewById<Button>(R.id.btnBackHome)

        tvBookingId.text = if (bookingId.isBlank()) "Booking ID unavailable" else "Booking ID: $bookingId"

        // ===== POPULATE VIEWS =====
        tvCompanionName.text = companionName.ifBlank { "Companion not assigned" }
        tvDateTime.text = listOf(date, time).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Date and time unavailable" }
        tvLocation.text = location.ifBlank { "Location not provided" }
        findViewById<TextView>(R.id.tvPaymentStatus).text = paymentMethod.ifBlank { "Not recorded" }

        // ===== VIEW MY BOOKINGS BUTTON =====
        btnViewBookings.setOnClickListener {
            val intent = Intent(this, MyBookingsActivity::class.java)
                .putExtra("role", "elderly")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
        }

        // ===== BACK TO HOME BUTTON =====
        btnBackHome.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        }

        // ===== PREVENT BACK BUTTON FROM RETURNING TO BOOKING FORM =====
        // (Once booking is confirmed, user shouldn't go back to Screen 9)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        // Override to go to Home instead of back to BookingActivity
        val intent = Intent(this, HomeActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}
