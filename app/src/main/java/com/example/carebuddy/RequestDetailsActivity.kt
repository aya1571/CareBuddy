package com.example.carebuddy

import android.Manifest
import android.content.Intent
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

class RequestDetailsActivity : AppCompatActivity() {

    private lateinit var actionBar: LinearLayout
    private lateinit var completeBar: LinearLayout
    private var pendingTripBooking: CareBooking? = null
    private val locationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        val booking = pendingTripBooking ?: return@registerForActivityResult
        pendingTripBooking = null
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasLocation) beginTrip(booking)
        else Toast.makeText(this, "Location permission is needed to share your trip location.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_details)

        // ===== FIND BOOKING =====
        val bookingId = intent.getStringExtra("bookingId")
        val booking = BookingStore.all(this).firstOrNull { it.id == bookingId }

        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val tvStatusLarge = findViewById<TextView>(R.id.tvStatusLarge)
        val actionBarView = findViewById<LinearLayout>(R.id.actionBar)
        val completeBarView = findViewById<LinearLayout>(R.id.completeBar)
        val emergencyButton = findViewById<Button>(R.id.btnEmergency)
        actionBar = actionBarView
        completeBar = completeBarView

        btnClose.setOnClickListener { finish() }

        if (booking == null) {
            Toast.makeText(this, "Request not found", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // ===== STATUS BADGE =====
        val (bgRes, textColor) = when (booking.status) {
            "Pending"   -> R.drawable.bg_badge_available to 0xFF2E7D32.toInt()
            "Accepted", "On The Way", "In Progress" -> R.drawable.bg_badge_ongoing to 0xFF1976D2.toInt()
            "Completed" -> R.drawable.bg_badge_completed to 0xFF616161.toInt()
            "Declined"  -> R.drawable.bg_badge_cancelled to 0xFFC62828.toInt()
            "Cancelled" -> R.drawable.bg_badge_cancelled to 0xFFC62828.toInt()
            else        -> R.drawable.bg_chip            to 0xFF888888.toInt()
        }
        tvStatusLarge.text = booking.status
        tvStatusLarge.setBackgroundResource(bgRes)
        tvStatusLarge.setTextColor(textColor)
        emergencyButton.visibility = if (booking.status in listOf("Accepted", "On The Way", "In Progress")) View.VISIBLE else View.GONE
        emergencyButton.setOnClickListener { showEmergencyOptions() }

        // ===== CUSTOMER INFO =====
        val customerName = booking.ownerName
            .ifBlank { booking.ownerEmail.ifBlank { "CareBuddy user" } }
        val customerEmail = booking.ownerEmail.ifBlank { "Not provided" }

        findViewById<TextView>(R.id.tvCustomerName).text = customerName
        findViewById<TextView>(R.id.tvCustomerEmail).text = customerEmail
        findViewById<TextView>(R.id.tvCustomerInitial).text =
            customerName.trim().firstOrNull()?.uppercase() ?: "?"

        // ===== BOOKING DETAILS =====
        findViewById<TextView>(R.id.tvActivity).text =
            booking.activity.ifBlank { "Booking" }
        findViewById<TextView>(R.id.tvDate).text = booking.date
        findViewById<TextView>(R.id.tvTime).text = booking.time
        findViewById<TextView>(R.id.tvLocation).text =
            booking.location.ifBlank { "Not specified" }
        findViewById<TextView>(R.id.tvDuration).text =
            "${booking.durationHours} hour(s)"

        // ===== PAYMENT INFO =====
        findViewById<TextView>(R.id.tvRate).text =
            booking.rate.ifBlank { "RM 30/hour" }
        findViewById<TextView>(R.id.tvPaymentMethod).text =
            booking.payment.ifBlank { "Not specified" }

        val total = if (booking.estimatedTotal > 0) {
            booking.estimatedTotal
        } else {
            BookingPricing.total(booking)
        }
        findViewById<TextView>(R.id.tvEstimatedTotal).text = BookingPricing.format(total)

        // ===== NOTES (only if present) =====
        val notesCard = findViewById<LinearLayout>(R.id.notesCard)
        if (booking.notes.isNotBlank()) {
            notesCard.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvNotes).text = booking.notes
        } else {
            notesCard.visibility = View.GONE
        }

        // ===== ACTION BUTTONS (depends on status) =====
        when (booking.status) {
            "Pending" -> {
                actionBar.visibility = View.VISIBLE
                completeBar.visibility = View.GONE
                findViewById<Button>(R.id.btnAccept).setOnClickListener {
                    updateStatus(booking, "Accepted")
                }
                findViewById<Button>(R.id.btnDecline).setOnClickListener {
                    confirmDecline(booking)
                }
            }
            "Accepted", "On The Way", "In Progress" -> {
                actionBar.visibility = View.GONE
                completeBar.visibility = View.VISIBLE
                val actionButton = findViewById<Button>(R.id.btnMarkComplete)
                if (booking.status == "Accepted") {
                    actionButton.text = "Start Trip and Share Location"
                    actionButton.setOnClickListener { requestLocationThenStartTrip(booking) }
                } else if (booking.status == "On The Way") {
                    actionButton.text = "Arrived • Start Service"
                    actionButton.setOnClickListener {
                        AlertDialog.Builder(this)
                            .setTitle("Arrived at the visit?")
                            .setMessage("This stops live location sharing and starts the service.")
                            .setPositiveButton("Arrived") { _, _ -> updateStatus(booking, "In Progress") }
                            .setNegativeButton("Not yet", null)
                            .show()
                    }
                } else {
                    actionButton.text = "Mark Visit as Complete"
                    actionButton.setOnClickListener { confirmComplete(booking) }
                }
            }
            else -> {
                // Completed, Declined, Cancelled — no actions
                actionBar.visibility = View.GONE
                completeBar.visibility = View.GONE
            }
        }
    }

    // ===================================================================
    // UPDATE STATUS
    // ===================================================================
    private fun updateStatus(booking: CareBooking, status: String) {
        FirebaseCareBuddyStore.updateBookingStatus(booking.id, status) { error ->
            if (error != null) {
                Toast.makeText(
                    this,
                    "Could not update booking. Check your connection and try again.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                BookingStore.updateStatus(this, booking.id, status)
                if (status == "On The Way") {
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
                if (status == "In Progress") {
                    stopAndClearTripTracking(this, booking.id)
                }
                if (status == "In Progress") {
                    FirebaseCareBuddyStore.createNotification(
                        userId = booking.ownerUid,
                        type = "booking",
                        title = "Service started",
                        message = "${booking.companion} has started your ${booking.activity} service.",
                        bookingId = booking.id
                    )
                }
                Toast.makeText(this, "Marked as $status", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // ===================================================================
    // CONFIRM DECLINE
    // ===================================================================
    private fun confirmDecline(booking: CareBooking) {
        AlertDialog.Builder(this)
            .setTitle("Decline this request?")
            .setMessage("The customer will be notified that you cannot take this booking.")
            .setPositiveButton("Yes, Decline") { _, _ ->
                updateStatus(booking, "Declined")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ===================================================================
    // CONFIRM COMPLETE
    // ===================================================================
    private fun confirmComplete(booking: CareBooking) {
        AlertDialog.Builder(this)
            .setTitle("Mark as complete?")
            .setMessage("This will close the booking and the customer will be asked to leave a review.")
            .setPositiveButton("Yes, Complete") { _, _ ->
                updateStatus(booking, "Completed")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestLocationThenStartTrip(booking: CareBooking) {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) beginTrip(booking) else {
            pendingTripBooking = booking
            val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
            locationPermissionRequest.launch(permissions.toTypedArray())
        }
    }

    private fun beginTrip(booking: CareBooking) {
        AlertDialog.Builder(this)
            .setTitle("Start trip?")
            .setMessage("Your location will be shared with the person who booked this visit until you arrive.")
            .setPositiveButton("Start Trip") { _, _ -> updateStatus(booking, "On The Way") }
            .setNegativeButton("Not yet", null)
            .show()
    }

    private fun showEmergencyOptions() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, "Please sign in to use booking emergency options.", Toast.LENGTH_LONG).show()
            return
        }
        FirebaseCareBuddyStore.loadUserProfile(uid) { profile, _ ->
            if (isFinishing || isDestroyed) return@loadUserProfile
            val name = profile?.get("emergencyContactName") as? String
            val phone = profile?.get("emergencyContactPhone") as? String
            val hasContact = !name.isNullOrBlank() && !phone.isNullOrBlank()
            val options = arrayOf(
                "Call 999 (genuine emergencies only)",
                if (hasContact) "Call $name — $phone" else "Add emergency contact"
            )
            AlertDialog.Builder(this)
                .setTitle("Emergency help")
                .setMessage("Choose a number. CareBuddy will open your phone dialer; you confirm the call there.")
                .setItems(options) { _, which ->
                    if (which == 0) openDialer("999")
                    else if (hasContact) openDialer(phone.orEmpty())
                    else startActivity(Intent(this, EmergencyContactActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun openDialer(number: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No phone dialer is available on this device.", Toast.LENGTH_LONG).show()
        }
    }
}
