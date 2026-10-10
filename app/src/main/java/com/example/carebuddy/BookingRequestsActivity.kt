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

class BookingRequestsActivity : AppCompatActivity() {

    // ===== VIEWS =====
    private lateinit var rvRequests: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyMessage: TextView

    private lateinit var tabNew: TextView
    private lateinit var tabAccepted: TextView
    private lateinit var tabDeclined: TextView
    private lateinit var allTabs: List<TextView>

    private var currentTab: String = "Pending"

    // ===== FIREBASE LISTENER =====
    private var bookingListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking_requests)

        // ===== BIND VIEWS =====
        rvRequests = findViewById(R.id.rvRequests)
        emptyState = findViewById(R.id.emptyState)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)

        val btnClose = findViewById<ImageView>(R.id.btnClose)
        tabNew = findViewById(R.id.tabNew)
        tabAccepted = findViewById(R.id.tabAccepted)
        tabDeclined = findViewById(R.id.tabDeclined)

        allTabs = listOf(tabNew, tabAccepted, tabDeclined)

        rvRequests.layoutManager = LinearLayoutManager(this)

        // ===== CLOSE =====
        btnClose.setOnClickListener { finish() }

        // ===== TAB CLICKS =====
        tabNew.setOnClickListener { switchTab("Pending") }
        tabAccepted.setOnClickListener { switchTab("Accepted") }
        tabDeclined.setOnClickListener { switchTab("Declined") }

        // ===== LOAD DEFAULT =====
        switchTab("Pending")
    }

    // ===================================================================
    // START FIRESTORE LISTENER — live updates for companion
    // ===================================================================
    override fun onStart() {
        super.onStart()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        bookingListener = FirebaseCareBuddyStore.listenBookings(
            context = this,
            role = "companion",
            uid = uid,
            onChanged = {
                // Firestore pushed new data → refresh the current tab
                if (!isFinishing && !isDestroyed) {
                    switchTab(currentTab)
                }
            },
            onError = { error ->
                android.util.Log.w("BookingRequests", "Sync failed: ${error.message}")
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Couldn't sync requests. Check your connection and reopen this screen.", Toast.LENGTH_LONG).show()
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
    // Refresh current tab when returning from RequestDetailsActivity
    // ===================================================================
    override fun onResume() {
        super.onResume()
        switchTab(currentTab)
    }

    // ===================================================================
    // COMPANION BOOKINGS FROM LOCAL CACHE
    // ===================================================================
    private fun companionBookings(): List<CareBooking> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        return BookingStore.all(this).filter { it.companionUid == uid && uid.isNotBlank() }
    }

    // ===================================================================
    // SWITCH TAB
    // ===================================================================
    private fun switchTab(status: String) {
        currentTab = status

        // Reset all tabs
        allTabs.forEach { t ->
            t.setTextColor(ContextCompat.getColor(this, R.color.care_muted))
            t.setBackgroundResource(0)
            t.setTextSize(14f)
        }

        // Highlight the selected tab
        val selectedTab = when (status) {
            "Pending"  -> tabNew
            "Accepted" -> tabAccepted
            "Declined" -> tabDeclined
            else -> tabNew
        }
        selectedTab.setTextColor(ContextCompat.getColor(this, R.color.care_purple))
        selectedTab.setBackgroundResource(R.drawable.bg_tab_selected)

        // Filter bookings by status
        val filtered = companionBookings().filter {
            if (status == "Accepted") it.status in listOf("Accepted", "On The Way", "In Progress")
            else it.status == status
        }

        if (filtered.isEmpty()) {
            showEmptyState(status)
        } else {
            emptyState.visibility = View.GONE
            rvRequests.visibility = View.VISIBLE

            val showActions = (status == "Pending")
            val adapter = BookingRequestAdapter(
                requests = filtered,
                showActions = showActions,
                onAccept = { booking -> handleAccept(booking) },
                onDecline = { booking -> handleDecline(booking) },
                onDetails = { booking -> openDetails(booking) }
            )
            rvRequests.adapter = adapter
        }
    }

    // ===================================================================
    // EMPTY STATE TEXT
    // ===================================================================
    private fun showEmptyState(status: String) {
        emptyState.visibility = View.VISIBLE
        rvRequests.visibility = View.GONE

        when (status) {
            "Pending" -> {
                tvEmptyTitle.text = "No new requests"
                tvEmptyMessage.text = "New booking requests will appear here"
            }
            "Accepted" -> {
                tvEmptyTitle.text = "No accepted or active bookings"
                tvEmptyMessage.text = "Bookings you accept and current visits will appear here"
            }
            "Declined" -> {
                tvEmptyTitle.text = "No declined requests"
                tvEmptyMessage.text = "Requests you decline will appear here"
            }
        }
    }

    // ===================================================================
    // ACCEPT REQUEST (with notification)
    // ===================================================================
    private fun handleAccept(booking: CareBooking) {
        FirebaseCareBuddyStore.updateBookingStatus(booking.id, "Accepted") { error ->
            if (error != null) {
                Toast.makeText(
                    this,
                    "Couldn't accept this request. It is still pending; check your connection and try again.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                BookingStore.updateStatus(this, booking.id, "Accepted")
                switchTab(currentTab)
                Toast.makeText(this, "Request accepted", Toast.LENGTH_SHORT).show()

                // Notify the customer
                FirebaseCareBuddyStore.createNotification(
                    userId = booking.ownerUid,
                    type = "booking",
                    title = "Booking Accepted",
                    message = "Your booking has been accepted by ${booking.companion}",
                    bookingId = booking.id
                )
            }
        }
    }

    // ===================================================================
    // DECLINE REQUEST
    // ===================================================================
    private fun handleDecline(booking: CareBooking) {
        FirebaseCareBuddyStore.updateBookingStatus(booking.id, "Declined") { error ->
            if (error != null) {
                Toast.makeText(
                    this,
                    "Couldn't decline this request. It is still pending; check your connection and try again.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                BookingStore.updateStatus(this, booking.id, "Declined")
                switchTab(currentTab)
                Toast.makeText(this, "Request declined", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ===================================================================
    // OPEN FULL DETAILS
    // ===================================================================
    private fun openDetails(booking: CareBooking) {
        val intent = Intent(this, RequestDetailsActivity::class.java)
        intent.putExtra("bookingId", booking.id)
        startActivity(intent)
    }
}
