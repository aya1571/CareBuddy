package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar

class HomeActivity : AppCompatActivity() {

    // ===== Firebase booking listener =====
    private var bookingListener: ListenerRegistration? = null
    private lateinit var upcomingCard: LinearLayout
    private lateinit var upcomingTitle: TextView
    private lateinit var tvUserName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // ===================================================================
        // 1. DYNAMIC GREETING (Good morning / afternoon / evening)
        // ===================================================================
        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        tvGreeting.text = when (hour) {
            in 0..11 -> "Good morning,"
            in 12..17 -> "Good afternoon,"
            else -> "Good evening,"
        }

        // ===================================================================
        // 2. DYNAMIC USER NAME
        // Priority: Intent → SharedPreferences → Fallback "Friend"
        // ===================================================================
        tvUserName = findViewById(R.id.tvUserName)
        refreshUserName()

        upcomingCard = findViewById(R.id.upcomingBookingCard)
        upcomingTitle = findViewById(R.id.tvUpcomingTitle)
        upcomingCard.visibility = android.view.View.GONE
        upcomingTitle.visibility = android.view.View.GONE

        // ===================================================================
        // 3. SEARCH BAR → Find Companion Form (Screen 6)
        // ===================================================================
        findViewById<LinearLayout>(R.id.searchBar).setOnClickListener {
            startActivity(Intent(this, FindCompanionActivity::class.java))
        }

        // ===================================================================
        // 4. UPCOMING BOOKING CARD → Booking Details (Screen 13)
        // ===================================================================
        // Helper: build the intent with real data if a booking exists
        fun openUpcomingBooking() {
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            val upcoming = BookingStore.all(this).firstOrNull { it.ownerUid == uid && uid.isNotBlank() &&
                    it.status in listOf("Upcoming", "Pending", "Accepted") }

            val intent = Intent(this, BookingDetailsActivity::class.java)
                .putExtra("role", "elderly")
            if (upcoming != null) {
                intent.putExtra("bookingId", upcoming.id)
                intent.putExtra("activity", upcoming.activity)
                intent.putExtra("dateTime", "${upcoming.date} • ${upcoming.time}")
                intent.putExtra("companionName", upcoming.companion)
                intent.putExtra("status", upcoming.status)
                intent.putExtra("notes", upcoming.notes)
            }
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.upcomingBookingCard).setOnClickListener {
            openUpcomingBooking()
        }

        findViewById<android.widget.Button>(R.id.btnViewDetails).setOnClickListener {
            openUpcomingBooking()
        }

        refreshUpcomingBooking()

        // ===================================================================
        // 5. NOTIFICATION ICON (top right) → Notifications (Screen 14)
        // ===================================================================
        findViewById<ImageView>(R.id.ivNotification).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        // ===================================================================
        // 6. BOTTOM NAVIGATION
        // ===================================================================

        // Home — already on Home, so do nothing
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            // Already on Home — do nothing
        }

        // Bookings → My Bookings (Screen 12)
        findViewById<LinearLayout>(R.id.navBookings).setOnClickListener {
            startActivity(Intent(this, MyBookingsActivity::class.java))
        }

        // Notifications → Notifications (Screen 14)
        findViewById<LinearLayout>(R.id.navNotifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        // Profile → Profile (Screen 15)
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(
                Intent(this, ProfileActivity::class.java)
                    .putExtra("role", "elderly")
            )
        }
    }

    // ===================================================================
    // Firebase listener — starts when screen is visible
    // ===================================================================
    override fun onStart() {
        super.onStart()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        bookingListener = FirebaseCareBuddyStore.listenBookings(
            context = this,
            role = "elderly",
            uid = uid,
            onChanged = {
                if (!isFinishing && !isDestroyed) refreshUpcomingBooking()
            },
            onError = { error ->
                Log.w("HomeActivity", "Booking sync failed", error)
            }
        )
    }

    // ===================================================================
    // Firebase listener — removed when screen is hidden
    // ===================================================================
    override fun onStop() {
        bookingListener?.remove()
        bookingListener = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshUserName()
        refreshUpcomingBooking()
    }

    private fun refreshUserName() {
        if (!::tvUserName.isInitialized) return
        val nameFromIntent = intent.getStringExtra("name")
        val nameFromPrefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
            .getString("current_name", null)

        tvUserName.text = nameFromIntent
            ?: nameFromPrefs
            ?: "Friend"
    }

    private fun refreshUpcomingBooking() {
        if (!::upcomingCard.isInitialized) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val booking = BookingStore.all(this).firstOrNull { it.ownerUid == uid && uid.isNotBlank() &&
            it.status in listOf("Upcoming", "Pending", "Accepted") }
        upcomingCard.visibility = if (booking == null) android.view.View.GONE else android.view.View.VISIBLE
        upcomingTitle.visibility = if (booking == null) android.view.View.GONE else android.view.View.VISIBLE
        if (booking != null) {
            findViewById<TextView>(R.id.tvUpcomingActivity).text = booking.activity.ifBlank { "Booking" }
            findViewById<TextView>(R.id.tvUpcomingStatus).text = booking.status
            findViewById<TextView>(R.id.tvUpcomingDate).text = "📅 ${booking.date} • ${booking.time}"
            findViewById<TextView>(R.id.tvUpcomingLocation).text = "📍 ${booking.location.ifBlank { "Location not set" }}"
        }
    }
}
