package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class MyBookingsActivity : AppCompatActivity() {

    // ===== STATE =====
    private lateinit var rvBookings: RecyclerView
    private lateinit var tabUpcoming: TextView
    private lateinit var tabOngoing: TextView
    private lateinit var tabCompleted: TextView
    private lateinit var tabCancelled: TextView
    private lateinit var allTabs: List<TextView>
    private lateinit var emptyState: LinearLayout
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyMessage: TextView

    private var currentTab: String = "Upcoming"
    private var isCompanion: Boolean = false

    // ===== FIREBASE LISTENER =====
    private var bookingListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_bookings)

        // ===== DETECT ROLE =====
        val role = intent.getStringExtra("role")
            ?: getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
                .getString("current_role", "elderly")
                .orEmpty()
        isCompanion = role.equals("companion", ignoreCase = true)

        // ===== BIND VIEWS =====
        rvBookings = findViewById(R.id.rvBookings)
        val btnClose = findViewById<ImageView>(R.id.btnClose)
        tabUpcoming = findViewById(R.id.tabUpcoming)
        tabOngoing = findViewById(R.id.tabOngoing)
        tabCompleted = findViewById(R.id.tabCompleted)
        tabCancelled = findViewById(R.id.tabCancelled)
        emptyState = findViewById(R.id.emptyState)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)

        allTabs = listOf(tabUpcoming, tabOngoing, tabCompleted, tabCancelled)

        rvBookings.layoutManager = LinearLayoutManager(this)
        btnClose.setOnClickListener { finish() }

        // ===== SET TAB LABELS BASED ON ROLE =====
        if (isCompanion) {
            tabUpcoming.text = "Accepted"
            tabOngoing.text = "In Progress"
            tabCompleted.text = "Completed"
            tabCancelled.text = "Declined"
        } else {
            tabUpcoming.text = "Upcoming"
            tabOngoing.text = "Ongoing"
            tabCompleted.text = "Completed"
            tabCancelled.text = "Cancelled"
        }

        // ===== TAB CLICKS =====
        tabUpcoming.setOnClickListener {
            switchTab(if (isCompanion) "Accepted" else "Upcoming", tabUpcoming)
        }
        tabOngoing.setOnClickListener {
            switchTab(if (isCompanion) "In Progress" else "Ongoing", tabOngoing)
        }
        tabCompleted.setOnClickListener {
            switchTab("Completed", tabCompleted)
        }
        tabCancelled.setOnClickListener {
            switchTab(if (isCompanion) "Declined" else "Cancelled", tabCancelled)
        }

        // ===== LOAD DEFAULT TAB =====
        switchTab(if (isCompanion) "Accepted" else "Upcoming", tabUpcoming)
    }

    // ===================================================================
    // START FIRESTORE LISTENER — live updates across devices
    // ===================================================================
    override fun onStart() {
        super.onStart()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val role = if (isCompanion) "companion" else "elderly"

        bookingListener = FirebaseCareBuddyStore.listenBookings(
            context = this,
            role = role,
            uid = uid,
            onChanged = {
                // Firestore pushed new data → refresh the current tab
                if (!isFinishing && !isDestroyed) {
                    switchTab(currentTab)
                }
            },
            onError = { error ->
                android.util.Log.w("MyBookings", "Sync failed: ${error.message}")
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Couldn't sync bookings. Showing saved bookings; check your connection.", Toast.LENGTH_LONG).show()
                }
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
    // Refresh on return from BookingDetailsActivity
    // ===================================================================
    override fun onResume() {
        super.onResume()
        switchTab(currentTab)
    }

    // ===================================================================
    // GET BOOKINGS FOR CURRENT ROLE
    // ===================================================================
    private fun getBookings(): List<CareBooking> {
        val prefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
        val email = prefs.getString("current_email", "").orEmpty()
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val all = BookingStore.all(this)

        if (all.isEmpty()) return emptyList()

        return if (isCompanion) {
            all.filter { booking ->
                uid.isNotBlank() && booking.companionUid == uid
            }
        } else {
            all.filter { booking ->
                uid.isNotBlank() && booking.ownerUid == uid ||
                        uid.isBlank() && email.isNotBlank() && booking.ownerEmail.equals(email, true)
            }
        }
    }

    // ===================================================================
    // SWITCH TAB
    // ===================================================================
    private fun switchTab(status: String, selectedTabView: TextView? = null) {
        currentTab = status

        // Reset all tabs
        allTabs.forEach { t ->
            t.setTextColor(ContextCompat.getColor(this, R.color.care_muted))
            t.setBackgroundResource(0)
        }

        // Highlight selected
        val target = selectedTabView ?: when (status) {
            "Upcoming", "Accepted" -> tabUpcoming
            "Ongoing", "In Progress" -> tabOngoing
            "Completed" -> tabCompleted
            "Cancelled", "Declined" -> tabCancelled
            else -> tabUpcoming
        }
        target.setTextColor(ContextCompat.getColor(this, R.color.care_purple))
        target.setBackgroundResource(R.drawable.bg_tab_selected)

        // Get bookings from Firestore local cache
        val remote = getBookings()

        // Build the list from Firestore cache only
        val displayList: List<Booking> = remote
            .filter {
                if (status == "Upcoming" && !isCompanion) it.status in listOf("Upcoming", "Pending", "Accepted")
                else if (status == "In Progress") it.status in listOf("On The Way", "In Progress")
                else if (status == "Ongoing") it.status in listOf("On The Way", "In Progress", "Ongoing")
                else it.status == status
            }
            .map { it.toDisplayBooking() }

        if (displayList.isEmpty()) {
            showEmptyState(status)
        } else {
            emptyState.visibility = View.GONE
            rvBookings.visibility = View.VISIBLE

            val adapter = BookingAdapter(displayList) { booking ->
                val intent = Intent(this, BookingDetailsActivity::class.java)
                intent.putExtra("bookingId", booking.id)
                intent.putExtra("activity", booking.activity)
                intent.putExtra("dateTime", booking.dateTime)
                intent.putExtra("companionName", booking.companionName)
                intent.putExtra("status", booking.status)
                intent.putExtra("role", if (isCompanion) "companion" else "elderly")
                startActivity(intent)
            }
            rvBookings.adapter = adapter
        }
    }

    // ===================================================================
    // EMPTY STATE
    // ===================================================================
    private fun showEmptyState(status: String) {
        emptyState.visibility = View.VISIBLE
        rvBookings.visibility = View.GONE

        val (title, message) = when (status) {
            "Upcoming" -> "No upcoming bookings" to "Your future bookings will appear here"
            "Accepted" -> "No accepted bookings" to "Jobs you accept will appear here"
            "Ongoing" -> "No ongoing bookings" to "Bookings in progress will appear here"
            "In Progress" -> "No ongoing jobs" to "Bookings currently in progress will appear here"
            "Completed" -> "No completed bookings" to "Your past completed work will appear here"
            "Cancelled" -> "No cancelled bookings" to "Cancelled bookings will appear here"
            "Declined" -> "No declined bookings" to "Declined requests will appear here"
            else -> "No bookings" to "Nothing to show here yet"
        }
        tvEmptyTitle.text = title
        tvEmptyMessage.text = message
    }
}

// ===================================================================
// CONVERTER: CareBooking → Booking (for the RecyclerView)
// ===================================================================
fun CareBooking.toDisplayBooking(): Booking {
    return Booking(
        id = this.id,
        activity = this.activity.ifBlank { "Booking" },
        dateTime = "${this.date} • ${this.time}",
        companionName = this.companion.ifBlank { this.ownerName.ifBlank { "CareBuddy user" } },
        status = this.status,
        iconResId = when (this.activity.lowercase()) {
            "hospital", "hospital appointment" -> android.R.drawable.ic_menu_my_calendar
            "shopping" -> android.R.drawable.ic_menu_agenda
            "errands" -> android.R.drawable.ic_menu_mylocation
            else -> android.R.drawable.ic_menu_my_calendar
        },
        customerName = this.ownerName.ifBlank { this.ownerEmail },
        companion = this.companion,
        date = this.date,
        time = this.time,
        location = this.location
    )
}
