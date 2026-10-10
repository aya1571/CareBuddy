package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PaymentActivity : AppCompatActivity() {

    // ===== STATE =====
    private var selectedMethod: String = "card"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)

        // ===== GET DATA FROM INTENT =====
        val bookingId = intent.getStringExtra("bookingId").orEmpty()
        val companionId = intent.getStringExtra("companionId").orEmpty()
        val companionName = intent.getStringExtra("companionName").orEmpty()
        val activity = intent.getStringExtra("activity").orEmpty()
        val date = intent.getStringExtra("date").orEmpty()
        val time = intent.getStringExtra("time").orEmpty()
        val location = intent.getStringExtra("location").orEmpty()
        val price = intent.getStringExtra("price").orEmpty()

        // ===== BIND VIEWS =====
        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val tvTotalAmount = findViewById<TextView>(R.id.tvTotalAmount)
        val tvBookingSummary = findViewById<TextView>(R.id.tvBookingSummary)

        val optionCard = findViewById<LinearLayout>(R.id.optionCard)
        val optionBanking = findViewById<LinearLayout>(R.id.optionBanking)
        val optionTng = findViewById<LinearLayout>(R.id.optionTng)
        val optionCash = findViewById<LinearLayout>(R.id.optionCash)

        val btnPayNow = findViewById<Button>(R.id.btnPayNow)

        // ===== POPULATE SUMMARY =====
        tvTotalAmount.text = price.ifBlank { "Amount unavailable" }
        tvBookingSummary.text = listOf(activity, date, time).filter { it.isNotBlank() }.joinToString(" • ")

        // ===== CLOSE =====
        btnClose.setOnClickListener { finish() }

        // ===== PAYMENT METHOD SELECTION =====
        optionCard.setOnClickListener { selectPaymentMethod("card") }
        optionBanking.setOnClickListener { selectPaymentMethod("banking") }
        optionTng.setOnClickListener { selectPaymentMethod("tng") }
        optionCash.setOnClickListener { selectPaymentMethod("cash") }

        // ===== PAY NOW =====
        btnPayNow.setOnClickListener {
            if (bookingId.isBlank()) {
                Toast.makeText(this, "Booking ID is missing. Go back and open the booking again.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            btnPayNow.isEnabled = false

            val methodLabel = when (selectedMethod) {
                "card" -> "Card"
                "banking" -> "Online Banking"
                "tng" -> "Touch n Go"
                "cash" -> "Cash on Arrival"
                else -> "Unknown"
            }

            Toast.makeText(
                this,
                "Saving payment method...",
                Toast.LENGTH_SHORT
            ).show()

            // ===== SAVE PAYMENT METHOD TO FIRESTORE =====
            val navigateToConfirmation = {
                val intent = Intent(this, BookingConfirmationActivity::class.java)
                intent.putExtra("bookingId", bookingId)
                intent.putExtra("companionId", companionId)
                intent.putExtra("companionName", companionName)
                intent.putExtra("activity", activity)
                intent.putExtra("date", date)
                intent.putExtra("time", time)
                intent.putExtra("location", location)
                intent.putExtra("price", price)
                intent.putExtra("paymentMethod", methodLabel)
                startActivity(intent)
                finish()
            }

            FirebaseCareBuddyStore.updateBookingPayment(bookingId, methodLabel) { error ->
                if (isFinishing || isDestroyed) return@updateBookingPayment
                if (error != null) {
                    btnPayNow.isEnabled = true
                    Toast.makeText(
                        this,
                        "Could not save the payment method to Firebase. Nothing was changed. Check Firestore rules and try again.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    BookingStore.updatePayment(this, bookingId, methodLabel)
                    Toast.makeText(this, "Payment method saved. No money was charged.", Toast.LENGTH_LONG).show()
                    navigateToConfirmation()
                }
            }
        }
    }

    // ===================================================================
    // HIGHLIGHT SELECTED PAYMENT METHOD
    // ===================================================================
    private fun selectPaymentMethod(method: String) {
        selectedMethod = method

        // Reset all options to unselected
        resetOption(R.id.optionCard)
        resetOption(R.id.optionBanking)
        resetOption(R.id.optionTng)
        resetOption(R.id.optionCash)

        // Highlight the selected one
        val selectedId = when (method) {
            "card" -> R.id.optionCard
            "banking" -> R.id.optionBanking
            "tng" -> R.id.optionTng
            "cash" -> R.id.optionCash
            else -> R.id.optionCard
        }

        val option = findViewById<LinearLayout>(selectedId)
        option.setBackgroundResource(R.drawable.bg_radio_option_selected)

        // Change icon tint of selected
        val checkIcon = option.getChildAt(option.childCount - 1) as ImageView
        checkIcon.setImageResource(android.R.drawable.checkbox_on_background)
        checkIcon.setColorFilter(ContextCompat.getColor(this, R.color.care_purple))
    }

    // ===================================================================
    // RESET ONE OPTION BACK TO DEFAULT STYLE
    // ===================================================================
    private fun resetOption(id: Int) {
        val option = findViewById<LinearLayout>(id)
        option.setBackgroundResource(R.drawable.bg_radio_option)

        val checkIcon = option.getChildAt(option.childCount - 1) as ImageView
        checkIcon.setImageResource(android.R.drawable.checkbox_off_background)
        checkIcon.setColorFilter(ContextCompat.getColor(this, R.color.care_muted))
    }
}
