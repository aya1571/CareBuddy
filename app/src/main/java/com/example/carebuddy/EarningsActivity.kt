package com.example.carebuddy

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class EarningsActivity : AppCompatActivity() {

    // ===== VIEWS AS CLASS FIELDS (so loadEarnings() can access them) =====
    private lateinit var tvTotalEarnings: TextView
    private lateinit var tvEarningsPeriod: TextView
    private lateinit var tvCompletedCount: TextView
    private lateinit var tvAverageEarning: TextView
    private lateinit var tvCardEarnings: TextView
    private lateinit var tvBankingEarnings: TextView
    private lateinit var tvTnGEarnings: TextView
    private lateinit var tvCashEarnings: TextView
    private lateinit var rvCompletedJobs: RecyclerView
    private lateinit var emptyState: LinearLayout

    // ===== FIREBASE LISTENER =====
    private var bookingListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_earnings)

        // ===== BIND VIEWS =====
        val btnClose = findViewById<ImageView>(R.id.btnClose)
        tvTotalEarnings = findViewById(R.id.tvTotalEarnings)
        tvEarningsPeriod = findViewById(R.id.tvEarningsPeriod)
        tvCompletedCount = findViewById(R.id.tvCompletedCount)
        tvAverageEarning = findViewById(R.id.tvAverageEarning)
        tvCardEarnings = findViewById(R.id.tvCardEarnings)
        tvBankingEarnings = findViewById(R.id.tvBankingEarnings)
        tvTnGEarnings = findViewById(R.id.tvTnGEarnings)
        tvCashEarnings = findViewById(R.id.tvCashEarnings)
        rvCompletedJobs = findViewById(R.id.rvCompletedJobs)
        emptyState = findViewById(R.id.emptyState)

        // ===== SETUP RECYCLERVIEW =====
        rvCompletedJobs.layoutManager = LinearLayoutManager(this)

        // ===== CLOSE =====
        btnClose.setOnClickListener { finish() }

        // ===== INITIAL LOAD =====
        loadEarnings()
    }

    // ===================================================================
    // START FIRESTORE LISTENER — live updates for companion earnings
    // ===================================================================
    override fun onStart() {
        super.onStart()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        bookingListener = FirebaseCareBuddyStore.listenBookings(
            context = this,
            role = "companion",
            uid = uid,
            onChanged = {
                // Firestore pushed new data → recalculate earnings
                if (!isFinishing && !isDestroyed) {
                    loadEarnings()
                }
            },
            onError = { error ->
                android.util.Log.w("Earnings", "Sync failed: ${error.message}")
            }
        )
    }

    // ===================================================================
    // STOP FIRESTORE LISTENER
    // ===================================================================
    override fun onStop() {
        bookingListener?.remove()
        bookingListener = null
        super.onStop()
    }

    // ===================================================================
    // LOAD EARNINGS — extracts totals, breakdown, and job list
    // ===================================================================
    private fun loadEarnings() {
        // ===== FETCH COMPLETED JOBS FOR THIS COMPANION =====
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val allBookings = BookingStore.all(this)
        val completed = allBookings.filter { booking ->
            uid.isNotBlank() && booking.companionUid == uid && booking.status == "Completed"
        }

        // ===== CALCULATE TOTALS =====
        val totalAmount = completed.sumOf {
            if (it.estimatedTotal > 0) it.estimatedTotal else BookingPricing.total(it)
        }
        val completedCount = completed.size
        val average = if (completedCount > 0) totalAmount / completedCount else 0.0

        // ===== POPULATE HERO CARD =====
        tvTotalEarnings.text = BookingPricing.format(totalAmount)
        tvEarningsPeriod.text = if (completedCount == 0) {
            "No completed bookings yet"
        } else {
            "From $completedCount completed booking(s)"
        }

        // ===== POPULATE STAT ROW =====
        tvCompletedCount.text = completedCount.toString()
        tvAverageEarning.text = BookingPricing.format(average)

        // ===== POPULATE PAYMENT METHOD BREAKDOWN =====
        val cardTotal = completed
            .filter { it.payment.equals("Card", true) }
            .sumOf { if (it.estimatedTotal > 0) it.estimatedTotal else BookingPricing.total(it) }

        val bankingTotal = completed
            .filter { it.payment.contains("Banking", true) || it.payment.contains("FPX", true) }
            .sumOf { if (it.estimatedTotal > 0) it.estimatedTotal else BookingPricing.total(it) }

        val tngTotal = completed
            .filter { it.payment.contains("Touch", true) || it.payment.contains("TnG", true) }
            .sumOf { if (it.estimatedTotal > 0) it.estimatedTotal else BookingPricing.total(it) }

        val cashTotal = completed
            .filter { it.payment.contains("Cash", true) }
            .sumOf { if (it.estimatedTotal > 0) it.estimatedTotal else BookingPricing.total(it) }

        tvCardEarnings.text = BookingPricing.format(cardTotal)
        tvBankingEarnings.text = BookingPricing.format(bankingTotal)
        tvTnGEarnings.text = BookingPricing.format(tngTotal)
        tvCashEarnings.text = BookingPricing.format(cashTotal)

        // ===== POPULATE RECENT JOBS =====
        if (completed.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            rvCompletedJobs.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            rvCompletedJobs.visibility = View.VISIBLE
            rvCompletedJobs.adapter = EarningAdapter(completed.sortedByDescending { it.date })
        }
    }
}
