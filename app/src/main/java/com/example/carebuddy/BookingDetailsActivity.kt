package com.example.carebuddy

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth

class BookingDetailsActivity : AppCompatActivity() {

    private var isCompanion = false
    private var selectedBooking: CareBooking? = null
    private lateinit var tvStatus: TextView
    private lateinit var tvName: TextView
    private lateinit var tvNameLabel: TextView
    private lateinit var tvActivity: TextView
    private lateinit var tvDateTime: TextView
    private lateinit var tvLocation: TextView
    private lateinit var notesCard: LinearLayout
    private lateinit var tvNotes: TextView
    private lateinit var btnCall: Button
    private lateinit var btnChat: Button
    private lateinit var btnComplete: Button
    private lateinit var btnStartService: Button
    private lateinit var btnTrackCompanion: Button
    private lateinit var btnEmergency: Button
    private lateinit var btnCancel: Button
    private var contactPhone: String? = null
    private var bookingWaitingForLocationPermission: CareBooking? = null
    private val locationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        val booking = bookingWaitingForLocationPermission ?: return@registerForActivityResult
        bookingWaitingForLocationPermission = null
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasLocation) {
            confirmStatusChange(booking, "On The Way", "Start trip?", "Your location will be shared with the person who booked this visit until you arrive.")
        } else {
            Toast.makeText(this, "Location permission is needed to share your trip location.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking_details)

        val role = intent.getStringExtra("role")
            ?: getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
                .getString("current_role", "elderly").orEmpty()
        isCompanion = role.equals("companion", ignoreCase = true)

        tvStatus = findViewById(R.id.tvStatusLarge)
        tvName = findViewById(R.id.tvCompanionName)
        tvNameLabel = findViewById(R.id.tvCompanionLabel)
        tvActivity = findViewById(R.id.tvActivity)
        tvDateTime = findViewById(R.id.tvDateTime)
        tvLocation = findViewById(R.id.tvLocation)
        notesCard = findViewById(R.id.notesCard)
        tvNotes = findViewById(R.id.tvNotes)
        btnCall = findViewById(R.id.btnCallPrimary)
        btnChat = findViewById(R.id.btnOpenChat)
        btnComplete = findViewById(R.id.btnMarkComplete)
        btnStartService = findViewById(R.id.btnStartService)
        btnTrackCompanion = findViewById(R.id.btnTrackCompanion)
        btnEmergency = findViewById(R.id.btnEmergency)
        btnCancel = findViewById(R.id.btnCancelBooking)

        findViewById<ImageView>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvContactSupport).setOnClickListener {
            Toast.makeText(this, "Please contact your CareBuddy administrator for support.", Toast.LENGTH_LONG).show()
        }

        btnCall.isEnabled = false
        btnCall.alpha = 0.6f
        btnCall.setOnClickListener {
            val phone = contactPhone
            if (phone.isNullOrBlank()) {
                Toast.makeText(this, "No phone number is available for this user.", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            }
        }

        val bookingId = intent.getStringExtra("bookingId").orEmpty()
        if (bookingId.isBlank()) {
            showMissingBooking("This screen needs a booking ID. Open it from My Bookings.")
            return
        }

        // Show the matching locally cached booking immediately, then refresh from Firestore.
        BookingStore.find(this, bookingId)?.let(::renderBooking)
        FirebaseCareBuddyStore.loadBooking(bookingId) { booking, error ->
            if (isFinishing || isDestroyed) return@loadBooking
            when {
                booking != null -> {
                    selectedBooking = booking
                    BookingStore.updateStatus(this, booking.id, booking.status)
                    renderBooking(booking)
                    loadContactPhone(booking)
                }
                error != null -> {
                    if (selectedBooking == null) showMissingBooking("Could not load this booking. Check your connection and try again.")
                }
                selectedBooking == null -> showMissingBooking("This booking could not be found.")
            }
        }
    }

    private fun renderBooking(booking: CareBooking) {
        selectedBooking = booking
        tvNameLabel.text = if (isCompanion) "Customer" else "Your Companion"
        tvName.text = if (isCompanion) {
            booking.ownerName.ifBlank { booking.ownerEmail.ifBlank { "Customer name not provided" } }
        } else {
            booking.companion.ifBlank { "Companion not assigned" }
        }
        tvActivity.text = booking.activity.ifBlank { "Activity not provided" }
        tvDateTime.text = listOf(booking.date, booking.time).filter { it.isNotBlank() }
            .joinToString(" • ").ifBlank { "Date and time not provided" }
        tvLocation.text = booking.location.ifBlank { "Location not provided" }
        tvStatus.text = booking.status

        val (bgRes, textColor) = when (booking.status) {
            "Upcoming", "Pending" -> R.drawable.bg_badge_available to 0xFF2E7D32.toInt()
            "Ongoing", "Accepted", "On The Way", "In Progress" -> R.drawable.bg_badge_ongoing to 0xFF1976D2.toInt()
            "Completed" -> R.drawable.bg_badge_completed to 0xFF616161.toInt()
            "Cancelled", "Declined" -> R.drawable.bg_badge_cancelled to 0xFFC62828.toInt()
            else -> R.drawable.bg_chip to 0xFF888888.toInt()
        }
        tvStatus.setBackgroundResource(bgRes)
        tvStatus.setTextColor(textColor)

        if (booking.notes.isBlank()) {
            notesCard.visibility = View.GONE
        } else {
            notesCard.visibility = View.VISIBLE
            tvNotes.text = booking.notes
        }

        btnCall.text = if (isCompanion) "📞  Call Customer" else "📞  Call Companion"
        val chatIsOpen = booking.status in listOf("Pending", "Accepted", "Upcoming", "Ongoing", "On The Way", "In Progress")
        btnChat.text = if (chatIsOpen) "Chat about this booking" else "View Chat History"
        btnChat.isEnabled = booking.ownerUid == FirebaseAuth.getInstance().currentUser?.uid ||
            booking.companionUid == FirebaseAuth.getInstance().currentUser?.uid
        btnChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java).putExtra("bookingId", booking.id))
        }
        btnStartService.visibility = if (isCompanion && booking.status in listOf("Accepted", "On The Way")) View.VISIBLE else View.GONE
        btnStartService.text = if (booking.status == "On The Way") "Arrived • Start Service" else "Start Trip and Share Location"
        btnStartService.setOnClickListener {
            if (booking.status == "Accepted") requestLocationThenStartTrip(booking)
            else confirmStatusChange(booking, "In Progress", "Arrived at the visit?", "This stops live location sharing and starts the service.")
        }
        btnTrackCompanion.visibility = if (!isCompanion && booking.status == "On The Way") View.VISIBLE else View.GONE
        btnTrackCompanion.setOnClickListener {
            startActivity(Intent(this, TrackCompanionActivity::class.java).putExtra("bookingId", booking.id))
        }
        val emergencyVisible = booking.status in listOf("Accepted", "On The Way", "Ongoing", "In Progress")
        btnEmergency.visibility = if (emergencyVisible) View.VISIBLE else View.GONE
        btnEmergency.setOnClickListener { showEmergencyOptions() }
        btnComplete.visibility = if (isCompanion && booking.status in listOf("Ongoing", "In Progress")) View.VISIBLE else View.GONE
        btnCancel.visibility = if (!isCompanion && booking.status in listOf("Upcoming", "Pending", "Accepted")) View.VISIBLE else View.GONE

        btnComplete.setOnClickListener {
            confirmStatusChange(booking, "Completed", "Mark as complete?", "This will close the booking and the customer can leave a review.")
        }
        btnCancel.setOnClickListener {
            confirmStatusChange(booking, "Cancelled", "Cancel booking?", "Are you sure you want to cancel this booking?")
        }
    }

    private fun loadContactPhone(booking: CareBooking) {
        val contactUid = if (isCompanion) booking.ownerUid else booking.companionUid
        if (contactUid.isBlank()) return
        FirebaseCareBuddyStore.loadUserProfile(contactUid) { profile, _ ->
            val phone = profile?.get("phone") as? String
            if (!phone.isNullOrBlank()) {
                contactPhone = phone
                btnCall.isEnabled = true
                btnCall.alpha = 1f
            }
        }
    }

    private fun showEmergencyOptions() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, "Please sign in to use booking emergency options.", Toast.LENGTH_LONG).show()
            return
        }
        FirebaseCareBuddyStore.loadUserProfile(uid) { profile, _ ->
            if (isFinishing || isDestroyed) return@loadUserProfile
            val contactName = profile?.get("emergencyContactName") as? String
            val contactPhone = profile?.get("emergencyContactPhone") as? String
            val hasContact = !contactName.isNullOrBlank() && !contactPhone.isNullOrBlank()
            val options = arrayOf(
                "Call 999 (genuine emergencies only)",
                if (hasContact) "Call $contactName — $contactPhone" else "Add emergency contact"
            )
            AlertDialog.Builder(this)
                .setTitle("Emergency help")
                .setMessage("Choose a number. CareBuddy will open your phone dialer; you confirm the call there.")
                .setItems(options) { _, which ->
                    if (which == 0) openDialer("999")
                    else if (hasContact) openDialer(contactPhone.orEmpty())
                    else startActivity(Intent(this, EmergencyContactActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun openDialer(phoneNumber: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phoneNumber, null)))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No phone dialer is available on this device.", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmStatusChange(booking: CareBooking, newStatus: String, title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Confirm") { _, _ ->
                FirebaseCareBuddyStore.updateBookingStatus(booking.id, newStatus) { error ->
                    if (error != null) {
                        Toast.makeText(this, "Could not update booking. Check your connection and try again.", Toast.LENGTH_LONG).show()
                    } else {
                        BookingStore.updateStatus(this, booking.id, newStatus)
                        if (newStatus == "On The Way") {
                            val service = Intent(this, CompanionTrackingService::class.java)
                                .putExtra(CompanionTrackingService.EXTRA_BOOKING_ID, booking.id)
                            ContextCompat.startForegroundService(this, service)
                            FirebaseCareBuddyStore.createNotification(
                                userId = booking.ownerUid,
                                type = "booking",
                                title = "Companion is on the way",
                                message = "${booking.companion} started travelling to your ${booking.activity} visit.",
                                bookingId = booking.id
                            )
                        }
                        if (newStatus == "In Progress") {
                            stopAndClearTripTracking(this, booking.id)
                            FirebaseCareBuddyStore.createNotification(
                                userId = booking.ownerUid,
                                type = "booking",
                                title = "Service started",
                                message = "${booking.companion} has started your ${booking.activity} service.",
                                bookingId = booking.id
                            )
                        }
                        Toast.makeText(this, when (newStatus) {
                            "On The Way" -> "Trip started; location sharing is on"
                            "In Progress" -> "Arrived; service started"
                            "Completed" -> "Booking marked complete"
                            else -> "Booking cancelled"
                        }, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .setNegativeButton("Keep booking", null)
            .show()
    }

    private fun requestLocationThenStartTrip(booking: CareBooking) {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            confirmStatusChange(booking, "On The Way", "Start trip?", "Your location will be shared with the person who booked this visit until you arrive.")
        } else {
            bookingWaitingForLocationPermission = booking
            val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
            locationPermissionRequest.launch(permissions.toTypedArray())
        }
    }

    private fun showMissingBooking(message: String) {
        tvStatus.text = "Booking unavailable"
        tvName.text = "No booking selected"
        tvActivity.text = "—"
        tvDateTime.text = "—"
        tvLocation.text = "—"
        btnCall.visibility = View.GONE
        btnChat.visibility = View.GONE
        btnComplete.visibility = View.GONE
        btnStartService.visibility = View.GONE
        btnTrackCompanion.visibility = View.GONE
        btnEmergency.visibility = View.GONE
        btnCancel.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
