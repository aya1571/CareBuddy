package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar

class CompanionHomeActivity : AppCompatActivity() {

    // ===== Firebase booking listener =====
    private var bookingListener: ListenerRegistration? = null
    private var changingAvailabilityFromServer = false
    private lateinit var availabilitySwitch: SwitchCompat
    private lateinit var availabilityStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_companion_home)
        findViewById<LinearLayout>(R.id.sampleBookingCard1).visibility = android.view.View.GONE
        findViewById<LinearLayout>(R.id.sampleBookingCard2).visibility = android.view.View.GONE

        // ===================================================================
        // 1. DYNAMIC GREETING
        // ===================================================================
        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        tvGreeting.text = when (hour) {
            in 0..11 -> "Good morning,"
            in 12..17 -> "Good afternoon,"
            else -> "Good evening,"
        }

        // ===================================================================
        // 2. COMPANION NAME
        // ===================================================================
        val tvCompanionName = findViewById<TextView>(R.id.tvCompanionName)
        val prefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
        val name = intent.getStringExtra("name")
            ?: prefs.getString("current_name", "Companion")
            ?: "Companion"
        tvCompanionName.text = name

        // ===================================================================
        // 3. TODAY'S BOOKINGS CARD → MyBookingsActivity (companion view)
        // ===================================================================
        findViewById<LinearLayout>(R.id.todaysBookingsCard).setOnClickListener {
            startActivity(
                Intent(this, MyBookingsActivity::class.java)
                    .putExtra("role", "companion")
            )
        }

        // ===================================================================
        // 4. EARNINGS CARD → EarningsActivity (Screen 26)
        // ===================================================================
        findViewById<LinearLayout>(R.id.earningsCard).setOnClickListener {
            startActivity(Intent(this, EarningsActivity::class.java))
        }

        // ===================================================================
        // 5. AVAILABILITY TOGGLE
        // ===================================================================
        val switchAvailability = findViewById<SwitchCompat>(R.id.switchAvailability)
        availabilitySwitch = switchAvailability
        availabilityStatus = findViewById(R.id.tvAvailabilityStatus)

        val isAvailable = prefs.getBoolean("companion_available", false)
        switchAvailability.isChecked = isAvailable
        renderAvailability(isAvailable)

        switchAvailability.setOnCheckedChangeListener { _, checked ->
            if (changingAvailabilityFromServer) return@setOnCheckedChangeListener
            prefs.edit().putBoolean("companion_available", checked).apply()
            renderAvailability(checked)
            val msg = if (checked) "You're now available" else "You're now unavailable"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

            // Sync to Firestore
            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                FirebaseCareBuddyStore.setCompanionAvailability(uid, checked) { error ->
                    if (error != null) {
                        changingAvailabilityFromServer = true
                        switchAvailability.isChecked = !checked
                        changingAvailabilityFromServer = false
                        prefs.edit().putBoolean("companion_available", !checked).apply()
                        renderAvailability(!checked)
                        Toast.makeText(
                            this,
                            "Could not update availability. Try again when connected.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        // ===================================================================
        // 5b. AVAILABILITY CARD TAP → Full schedule editor (Screen 28)
        // ===================================================================
        findViewById<LinearLayout>(R.id.availabilityCard).setOnClickListener {
            startActivity(Intent(this, AvailabilityActivity::class.java))
        }

        // ===================================================================
        // 6. NOTIFICATION BELL
        // ===================================================================
        findViewById<ImageView>(R.id.ivNotification).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        // ===================================================================
        // 7. BOTTOM NAVIGATION
        // ===================================================================
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            // Already on Home
        }

        findViewById<LinearLayout>(R.id.navRequests).setOnClickListener {
            startActivity(Intent(this, BookingRequestsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.navBookings).setOnClickListener {
            startActivity(
                Intent(this, MyBookingsActivity::class.java)
                    .putExtra("role", "companion")
            )
        }

        findViewById<LinearLayout>(R.id.navEarnings).setOnClickListener {
            startActivity(Intent(this, EarningsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(
                Intent(this, ProfileActivity::class.java)
                    .putExtra("role", "companion")
            )
        }

        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseCareBuddyStore.loadCompanionProfile(uid) { profile, error ->
                if (error == null && profile != null && !isFinishing && !isDestroyed) {
                    val remoteAvailable = profile["available"] as? Boolean ?: false
                    prefs.edit().putBoolean("companion_available", remoteAvailable).apply()
                    changingAvailabilityFromServer = true
                    switchAvailability.isChecked = remoteAvailable
                    changingAvailabilityFromServer = false
                    renderAvailability(remoteAvailable)
                }
            }
        }
        refreshBookingSummary()
    }

    // ===================================================================
    // Firebase listener — starts when screen is visible
    // ===================================================================
    override fun onStart() {
        super.onStart()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        bookingListener = FirebaseCareBuddyStore.listenBookings(
            context = this,
            role = "companion",
            uid = uid,
            onChanged = {
                if (!isFinishing && !isDestroyed) refreshBookingSummary()
            },
            onError = { error ->
                android.util.Log.w("CompanionHome", "Booking sync failed", error)
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
        refreshBookingSummary()
    }

    private fun renderAvailability(available: Boolean) {
        availabilityStatus.text = if (available) "You are available for bookings" else "You are currently unavailable"
    }

    private fun refreshBookingSummary() {
        if (!::availabilityStatus.isInitialized) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val all = BookingStore.all(this).filter { it.companionUid == uid && uid.isNotBlank() }
        val active = all.filter { it.status in listOf("Pending", "Accepted", "On The Way", "In Progress") }
        val today = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        val todayCount = active.count { it.date.equals(today, ignoreCase = true) }
        findViewById<TextView>(R.id.tvTodayBookings).text =
            if (active.isEmpty()) "No upcoming bookings" else "${active.size} upcoming • $todayCount today"
        val earned = all.filter { it.status == "Completed" }.sumOf { BookingPricing.total(it) }
        findViewById<TextView>(R.id.tvEarnings).text = BookingPricing.format(earned)
        val upcoming = active.take(2)
        val cardIds = listOf(R.id.sampleBookingCard1, R.id.sampleBookingCard2)
        val titleIds = listOf(R.id.tvUpcomingActivity1, R.id.tvUpcomingActivity2)
        val statusIds = listOf(R.id.tvUpcomingStatus1, R.id.tvUpcomingStatus2)
        val dateIds = listOf(R.id.tvUpcomingDate1, R.id.tvUpcomingDate2)
        val locationIds = listOf(R.id.tvUpcomingLocation1, R.id.tvUpcomingLocation2)
        cardIds.forEachIndexed { index, cardId ->
            val booking = upcoming.getOrNull(index)
            findViewById<LinearLayout>(cardId).visibility = if (booking == null) android.view.View.GONE else android.view.View.VISIBLE
            if (booking != null) {
                findViewById<TextView>(titleIds[index]).text = booking.activity.ifBlank { "Booking" }
                findViewById<TextView>(statusIds[index]).text = booking.status
                findViewById<TextView>(dateIds[index]).text = "📅 ${booking.date} • ${booking.time}"
                findViewById<TextView>(locationIds[index]).text = "📍 ${booking.location.ifBlank { "Location not set" }}"
            }
        }
    }
}
