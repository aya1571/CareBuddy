package com.example.carebuddy

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class BookingManagementActivity : AppCompatActivity() {

    // ===== VIEWS =====
    private lateinit var rvBookings: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyMessage: TextView

    private lateinit var tabAll: TextView
    private lateinit var tabPending: TextView
    private lateinit var tabAccepted: TextView
    private lateinit var tabCompleted: TextView
    private lateinit var tabCancelled: TextView
    private lateinit var allTabs: List<TextView>

    private var currentFilter: String = "All"

    // ===== FIREBASE LISTENER =====
    private var bookingListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking_management)

        // ===== BIND VIEWS =====
        rvBookings = findViewById(R.id.rvBookings)
        emptyState = findViewById(R.id.emptyState)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)

        val btnClose = findViewById<ImageView>(R.id.btnClose)
        tabAll = findViewById(R.id.tabAll)
        tabPending = findViewById(R.id.tabPending)
        tabAccepted = findViewById(R.id.tabAccepted)
        tabCompleted = findViewById(R.id.tabCompleted)
        tabCancelled = findViewById(R.id.tabCancelled)

        allTabs = listOf(tabAll, tabPending, tabAccepted, tabCompleted, tabCancelled)

        rvBookings.layoutManager = LinearLayoutManager(this)
        btnClose.setOnClickListener { finish() }

        // ===== TAB CLICKS =====
        tabAll.setOnClickListener { switchFilter("All", tabAll) }
        tabPending.setOnClickListener { switchFilter("Pending", tabPending) }
        tabAccepted.setOnClickListener { switchFilter("Accepted", tabAccepted) }
        tabCompleted.setOnClickListener { switchFilter("Completed", tabCompleted) }
        tabCancelled.setOnClickListener { switchFilter("Cancelled", tabCancelled) }

        // ===== LOAD DEFAULT =====
        switchFilter("All", tabAll)
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
                // Firestore pushed new data → refresh current filter
                if (!isFinishing && !isDestroyed) {
                    switchFilter(currentFilter, null)
                }
            },
            onError = { error ->
                android.util.Log.w("BookingMgmt", "Sync failed: ${error.message}")
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Couldn't sync bookings. Check your connection; saved records may be out of date.", Toast.LENGTH_LONG).show()
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
    // Refresh on return
    // ===================================================================
    override fun onResume() {
        super.onResume()
        switchFilter(currentFilter, null)
    }

    // ===================================================================
    // SWITCH FILTER
    // ===================================================================
    private fun switchFilter(filter: String, selectedTab: TextView?) {
        currentFilter = filter

        // Reset all tabs
        allTabs.forEach { t ->
            t.setTextColor(ContextCompat.getColor(this, R.color.care_muted))
            t.setBackgroundResource(0)
            t.setTextSize(13f)
        }

        // Highlight selected
        val target = selectedTab ?: when (filter) {
            "All" -> tabAll
            "Pending" -> tabPending
            "Accepted" -> tabAccepted
            "Completed" -> tabCompleted
            "Cancelled" -> tabCancelled
            else -> tabAll
        }
        target.setTextColor(ContextCompat.getColor(this, R.color.care_purple))
        target.setBackgroundResource(R.drawable.bg_tab_selected)

        // ===== Get bookings from local cache =====
        val all = BookingStore.all(this)
        val filtered = when (filter) {
            "All" -> all
            "Accepted" -> all.filter { it.status in listOf("Accepted", "On The Way", "In Progress") }
            else -> all.filter { it.status == filter }
        }

        if (filtered.isEmpty()) {
            showEmptyState(filter)
        } else {
            emptyState.visibility = View.GONE
            rvBookings.visibility = View.VISIBLE

            val adapter = AdminBookingAdapter(
                bookings = filtered,
                onViewDetails = { booking -> viewDetails(booking) },
                onCancel = { booking -> confirmCancel(booking) }
            )
            rvBookings.adapter = adapter
        }
    }

    // ===================================================================
    // EMPTY STATE
    // ===================================================================
    private fun showEmptyState(filter: String) {
        emptyState.visibility = View.VISIBLE
        rvBookings.visibility = View.GONE

        val (title, message) = when (filter) {
            "All" -> "No bookings yet" to "Bookings created in the app will appear here"
            "Pending" -> "No pending bookings" to "New requests waiting for companion approval will appear here"
            "Accepted" -> "No active bookings" to "Accepted requests and current visits will appear here"
            "Completed" -> "No completed bookings" to "Finished jobs will appear here"
            "Cancelled" -> "No cancelled bookings" to "Cancelled or declined bookings will appear here"
            else -> "No bookings" to "Nothing to show here"
        }
        tvEmptyTitle.text = title
        tvEmptyMessage.text = message
    }

    // ===================================================================
    // VIEW DETAILS — show an AlertDialog with full info
    // ===================================================================
    private fun viewDetails(booking: CareBooking) {
        val ownerName = booking.ownerName.ifBlank { booking.ownerEmail.ifBlank { "CareBuddy user" } }
        val total = if (booking.estimatedTotal > 0) booking.estimatedTotal
        else BookingPricing.total(booking)

        val message = buildString {
            appendLine("ID: ${booking.id}")
            appendLine("Customer: $ownerName")
            appendLine("Companion: ${booking.companion.ifBlank { "Not assigned" }}")
            appendLine("Activity: ${booking.activity}")
            appendLine("Date: ${booking.date}")
            appendLine("Time: ${booking.time}")
            appendLine("Location: ${booking.location}")
            appendLine("Duration: ${booking.durationHours} hour(s)")
            appendLine("Rate: ${booking.rate}")
            appendLine("Total: ${BookingPricing.format(total)}")
            appendLine("Payment: ${booking.payment}")
            appendLine("Status: ${booking.status}")
            if (booking.notes.isNotBlank()) {
                appendLine()
                appendLine("Notes:")
                appendLine(booking.notes)
            }
        }

        AlertDialog.Builder(this)
            .setTitle(booking.activity.ifBlank { "Booking" })
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }

    // ===================================================================
    // CANCEL BOOKING
    // ===================================================================
    private fun confirmCancel(booking: CareBooking) {
        AlertDialog.Builder(this)
            .setTitle("Cancel this booking?")
            .setMessage("The customer and companion will both be notified. This cannot be undone.")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                FirebaseCareBuddyStore.updateBookingStatus(booking.id, "Cancelled") { error ->
                    if (error != null) {
                        Toast.makeText(
                            this,
                            "Couldn't cancel the booking in Firebase. It remains unchanged; check your connection and retry.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        BookingStore.updateStatus(this, booking.id, "Cancelled")
                        if (booking.ownerUid.isNotBlank()) {
                            FirebaseCareBuddyStore.createNotification(
                                userId = booking.ownerUid,
                                type = "booking",
                                title = "Booking Cancelled",
                                message = "An administrator cancelled your booking.",
                                bookingId = booking.id
                            )
                        }
                        if (booking.companionUid.isNotBlank()) {
                            FirebaseCareBuddyStore.createNotification(
                                userId = booking.companionUid,
                                type = "booking",
                                title = "Booking Cancelled",
                                message = "An administrator cancelled a booking assigned to you.",
                                bookingId = booking.id
                            )
                        }
                        Toast.makeText(this, "Booking cancelled", Toast.LENGTH_SHORT).show()
                    }
                    switchFilter(currentFilter, null)
                }
            }
            .setNegativeButton("Keep", null)
            .show()
    }
}
