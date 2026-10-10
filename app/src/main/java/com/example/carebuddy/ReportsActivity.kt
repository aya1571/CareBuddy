package com.example.carebuddy

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class ReportsActivity : AppCompatActivity() {

    // ===== VIEWS AS CLASS FIELDS =====
    private lateinit var tvTotalBookings: TextView
    private lateinit var tvCompleted: TextView
    private lateinit var tvPending: TextView
    private lateinit var tvAccepted: TextView
    private lateinit var tvCancelled: TextView
    private lateinit var tvReviewCount: TextView
    private lateinit var loadingState: LinearLayout
    private lateinit var emptyReviews: LinearLayout
    private lateinit var rvReviews: RecyclerView

    // ===== FIREBASE LISTENER =====
    private var bookingListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        // ===== BIND VIEWS =====
        val btnClose = findViewById<ImageView>(R.id.btnClose)
        tvTotalBookings = findViewById(R.id.tvTotalBookings)
        tvCompleted = findViewById(R.id.tvCompleted)
        tvPending = findViewById(R.id.tvPending)
        tvAccepted = findViewById(R.id.tvAccepted)
        tvCancelled = findViewById(R.id.tvCancelled)
        tvReviewCount = findViewById(R.id.tvReviewCount)
        loadingState = findViewById(R.id.loadingState)
        emptyReviews = findViewById(R.id.emptyReviews)
        rvReviews = findViewById(R.id.rvReviews)

        rvReviews.layoutManager = LinearLayoutManager(this)
        btnClose.setOnClickListener { finish() }

        // ===== INITIAL LOAD =====
        loadReport()
    }

    // ===================================================================
    // START FIRESTORE LISTENER — live updates for admin
    // ===================================================================
    override fun onStart() {
        super.onStart()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        bookingListener = FirebaseCareBuddyStore.listenBookings(
            context = this,
            role = "admin",
            uid = uid,
            onChanged = {
                // Firestore pushed new data → recompute stats
                if (!isFinishing && !isDestroyed) {
                    loadReport()
                }
            },
            onError = { error ->
                android.util.Log.w("Reports", "Sync failed: ${error.message}")
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
    // LOAD REPORT — stats + reviews
    // ===================================================================
    private fun loadReport() {
        // ===== BOOKING STATS =====
        val bookings = BookingStore.all(this)
        val total = bookings.size
        val completed = bookings.count { it.status == "Completed" }
        val pending = bookings.count { it.status == "Pending" }
        val accepted = bookings.count { it.status in listOf("Accepted", "On The Way", "In Progress") }
        val cancelled = bookings.count { it.status in listOf("Cancelled", "Declined") }

        fun pct(count: Int): String =
            if (total == 0) "0%" else "${count * 100 / total}%"

        tvTotalBookings.text = total.toString()
        tvCompleted.text = "$completed (${pct(completed)})"
        tvPending.text = "$pending (${pct(pending)})"
        tvAccepted.text = "$accepted (${pct(accepted)})"
        tvCancelled.text = "$cancelled (${pct(cancelled)})"

        // ===== REVIEWS =====
        loadReviews()
    }

    // ===================================================================
    // LOAD REVIEWS
    // ===================================================================
    private fun loadReviews() {
        loadingState.visibility = View.VISIBLE
        emptyReviews.visibility = View.GONE
        rvReviews.visibility = View.GONE

        FirebaseCareBuddyStore.loadReviews { reviews, error ->
            if (isFinishing || isDestroyed) return@loadReviews

            loadingState.visibility = View.GONE

            if (error != null) {
                Toast.makeText(
                    this,
                    "Failed to load reviews: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
                emptyReviews.visibility = View.VISIBLE
                return@loadReviews
            }

            if (reviews.isEmpty()) {
                emptyReviews.visibility = View.VISIBLE
                rvReviews.visibility = View.GONE
                tvReviewCount.text = "(0)"
            } else {
                emptyReviews.visibility = View.GONE
                rvReviews.visibility = View.VISIBLE
                tvReviewCount.text = "(${reviews.size})"
                rvReviews.adapter = ReviewAdapter(reviews)
            }
        }
    }
}
