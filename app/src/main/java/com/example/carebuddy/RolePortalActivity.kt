package com.example.carebuddy

import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class RolePortalActivity : AppCompatActivity() {

    // ===================================================================
    // CONSTANTS
    // ===================================================================
    companion object {
        const val EXTRA_ROLE = "role"
        const val EXTRA_SCREEN = "screen"
        const val EXTRA_COMPANION = "companion_name"
        const val EXTRA_COMPANION_UID = "companion_uid"
        const val EXTRA_RATE = "companion_rate"
        const val EXTRA_BOOKING_ID = "booking_id"

        private const val PREFS_NAME = "carebuddy_demo"
        private const val TAG = "CareBuddy"
    }

    // ===================================================================
    // STATE
    // ===================================================================
    private lateinit var role: String
    private lateinit var content: LinearLayout
    private var bookingListener: ListenerRegistration? = null

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    private val accentColor: Int by lazy {
        when (role) {
            "companion" -> R.color.care_companion
            "admin" -> R.color.care_green
            else -> R.color.care_purple
        }
    }

    // ===================================================================
    // LIFECYCLE
    // ===================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        role = intent.getStringExtra(EXTRA_ROLE) ?: "elderly"
        val screen = intent.getStringExtra(EXTRA_SCREEN) ?: "Dashboard"

        setupLayout()
        renderHeader(screen)
        routeToPage(screen)
        renderFooter()
    }

    override fun onStart() {
        super.onStart()
        attachBookingListener()
    }

    override fun onStop() {
        bookingListener?.remove()
        bookingListener = null
        super.onStop()
    }

    // ===================================================================
    // LAYOUT SETUP
    // ===================================================================
    private fun setupLayout() {
        val scroll = ScrollView(this).apply {
            setBackgroundResource(R.drawable.bg_screen)
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }
        scroll.addView(content)
        setContentView(scroll)
    }

    private fun renderHeader(screen: String) {
        text(
            "CareBuddy  ·  ${role.replaceFirstChar { it.uppercase() }}",
            14, accentColor, true
        )
        text(screen, 28, R.color.care_ink, true, top = 18)
    }

    private fun renderFooter() {
        action("Home") {
            if (role == "elderly") {
                startActivity(Intent(this, HomeActivity::class.java))
            } else {
                open("Dashboard")
            }
        }
        action("Sign out") { signOut() }
    }

    private fun routeToPage(screen: String) {
        when (role) {
            "companion" -> companionPage(screen)
            "admin" -> adminPage(screen)
            else -> elderlyPage(screen)
        }
    }

    // ===================================================================
    // ELDERLY PAGE
    // ===================================================================
    private fun elderlyPage(screen: String) {
        when (screen) {
            "Companion Profile" -> elderlyCompanionProfile()
            "My Bookings" -> elderlyMyBookings()
            "Rate and Review" -> elderlyRateAndReview()
            "Notifications" -> elderlyNotifications()
            "Profile" -> elderlyProfile()
            else -> card(screen, "Choose an option below to continue.")
        }
    }

    private fun elderlyCompanionProfile() {
        val name = intent.getStringExtra(EXTRA_COMPANION) ?: "Aisha Rahman"
        val rate = intent.getStringExtra(EXTRA_RATE) ?: "RM 30/hour"

        val reviews = ReviewStore.all(this).filter { it.companion.equals(name, true) }
        val rating = if (reviews.isEmpty()) {
            "New companion · No reviews yet"
        } else {
            "★ %.1f · %d review(s)".format(
                java.util.Locale.US,
                reviews.map { it.stars }.average(),
                reviews.size
            )
        }

        card(
            "$name · $rating",
            "Companion profile · Kuala Lumpur\n\n" +
                    "Friendly support for hospital visits, shopping and everyday errands.\n\n" +
                    "Rate: $rate"
        )

        action("Book this companion") {
            startActivity(
                Intent(this, BookingActivity::class.java)
                    .putExtra(BookingActivity.EXTRA_COMPANION, name)
                    .putExtra(BookingActivity.EXTRA_RATE, rate)
                    .putExtra(EXTRA_COMPANION_UID, intent.getStringExtra(EXTRA_COMPANION_UID))
            )
        }
    }

    private fun elderlyMyBookings() {
        val email = prefs.getString("current_email", "").orEmpty()
        val bookings = BookingStore.all(this).filter {
            email.isBlank() || it.ownerEmail.isBlank() || it.ownerEmail.equals(email, true)
        }

        if (bookings.isEmpty()) {
            card("No bookings yet", "Your confirmed bookings will appear here.")
            return
        }

        bookings.forEach { booking ->
            card(
                "${booking.activity.ifBlank { "Booking" }} · ${booking.date}",
                "${booking.companion} · ${booking.time}\n" +
                        "${booking.location}\n" +
                        "${booking.durationHours} hour(s) · ${booking.rate}\n" +
                        "Estimate: ${BookingPricing.format(BookingPricing.total(booking))}\n" +
                        "${booking.payment}\n" +
                        "Status: ${booking.status}"
            )

            if (booking.status == "Completed") {
                val review = ReviewStore.forBooking(this, booking.id)
                if (review == null) {
                    action("Rate this visit") { open("Rate and Review", booking.id) }
                } else {
                    val stars = "★".repeat(review.stars) + "☆".repeat(5 - review.stars)
                    card(
                        "Your review",
                        stars + if (review.comment.isBlank()) "" else "\n${review.comment}"
                    )
                }
            }
        }
    }

    private fun elderlyRateAndReview() {
        val email = prefs.getString("current_email", "").orEmpty()
        val booking = BookingStore.all(this).firstOrNull {
            it.id == intent.getStringExtra(EXTRA_BOOKING_ID) &&
                    it.status == "Completed" &&
                    (email.isBlank() || it.ownerEmail.isBlank() || it.ownerEmail.equals(email, true))
        }

        if (booking == null) {
            card("Review unavailable", "Only a completed booking on your account can be reviewed.")
            return
        }

        val existing = ReviewStore.forBooking(this, booking.id)
        if (existing != null) {
            card(
                "Review already submitted",
                "${existing.stars} / 5 stars\n${existing.comment}"
            )
            return
        }

        card("How was ${booking.companion}?", "Your rating helps other users choose a companion.")

        val rating = RatingBar(this).apply {
            numStars = 5
            stepSize = 1f
            this.rating = 5f
        }
        content.addView(rating, match())

        val comment = EditText(this).apply {
            hint = "Write a review (optional)"
            minLines = 3
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        content.addView(comment, match())

        action("Submit review") {
            val review = CareReview(
                id = System.currentTimeMillis().toString(),
                bookingId = booking.id,
                companion = booking.companion,
                reviewer = prefs.getString("current_name", "CareBuddy user").orEmpty(),
                stars = rating.rating.toInt().coerceIn(1, 5),
                comment = comment.text.toString().trim(),
                createdAt = System.currentTimeMillis(),
                reviewerUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            )
            FirebaseCareBuddyStore.saveReview(review) { error ->
                if (error != null) {
                    toast("Could not submit review to Firebase: ${error.localizedMessage ?: "check Firestore rules and try again"}")
                    return@saveReview
                }
                ReviewStore.add(this, review)
                toast("Review submitted")
                startActivity(Intent(this, MyReviewsActivity::class.java))
                finish()
            }
        }
    }

    private fun elderlyNotifications() {
        card(
            "Welcome to CareBuddy",
            "Your account is ready. Complete your profile and book a companion whenever you need support."
        )

        val email = prefs.getString("current_email", "").orEmpty()
        BookingStore.all(this)
            .filter { email.isBlank() || it.ownerEmail.isBlank() || it.ownerEmail.equals(email, true) }
            .take(3)
            .forEach { card("Booking ${it.status}", "${it.companion} · ${it.date} at ${it.time}") }
    }

    private fun elderlyProfile() {
        val name = intent.getStringExtra("name")?.takeIf { it.isNotBlank() }
            ?: prefs.getString("elderly_name", "CareBuddy member")
            ?: "CareBuddy member"

        val input = EditText(this).apply {
            setText(name)
            hint = "Your name"
        }
        content.addView(input, match())

        action("Save profile") {
            val editedName = input.text.toString().trim()
            val email = prefs.getString("current_email", "").orEmpty()

            prefs.edit()
                .putString("elderly_name", editedName)
                .putString("current_name", editedName)
                .apply {
                    if (email.isNotBlank()) putString("profile_name:$email", editedName)
                }
                .apply()

            if (email.isNotBlank()) LocalAccountStore.rename(this, email, editedName)

            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                FirebaseCareBuddyStore.updateUserName(uid, editedName, "elderly") { error ->
                    if (error != null) toast("Saved locally, Firebase update failed.")
                }
            }
            toast("Profile saved")
        }

        card("Identity verification", verificationText())
    }

    // ===================================================================
    // COMPANION PAGE
    // ===================================================================
    private fun companionPage(screen: String) {
        when (screen) {
            "Dashboard" -> companionDashboard()
            "Booking Requests" -> companionBookingRequests()
            "Request Details" -> companionRequestDetails()
            "My Bookings" -> companionMyBookings()
            "Availability" -> companionAvailability()
            "Earnings" -> companionEarnings()
            "Profile" -> companionProfile()
            "Notifications" -> companionNotifications()
            "Verification" -> companionVerification()
            else -> card(screen, "Companion workspace")
        }

        if (screen == "Dashboard") companionMenu()
    }

    private fun companionDashboard() {
        val bookings = companionBookings()
        val pending = bookings.count { it.status == "Pending" }
        val active = bookings.count { it.status == "Accepted" }
        val companionName = prefs.getString("companion_name", "").orEmpty()

        val reviews = ReviewStore.all(this).filter { it.companion.equals(companionName, true) }
        val rating = if (reviews.isEmpty()) {
            "No ratings yet"
        } else {
            "%.1f / 5 from %d review(s)".format(
                java.util.Locale.US,
                reviews.map { it.stars }.average(),
                reviews.size
            )
        }

        card(
            "Good day!",
            "You're all set. Review requests, manage your availability and keep your profile up to date.\n\n" +
                    "Pending requests: $pending\n" +
                    "Accepted bookings: $active\n" +
                    "Rating: $rating"
        )
    }

    private fun companionBookingRequests() {
        val requests = companionBookings().filter { it.status.equals("Pending", true) }

        if (requests.isEmpty()) {
            card("No pending requests", "New booking requests will appear here.")
            return
        }

        requests.forEach { booking ->
            card(
                "${booking.activity.ifBlank { "Booking" }} · ${booking.date}",
                "${booking.companion} · ${booking.time}\n" +
                        "${booking.location}\n" +
                        "${booking.rate} · ${booking.payment}\n" +
                        "Status: ${booking.status}"
            )
            action("View request details") { open("Request Details", booking.id) }
            action("Accept request") {
                changeBookingStatus(booking.id, "Accepted")
                toast("Request accepted")
                recreate()
            }
            action("Decline request") {
                changeBookingStatus(booking.id, "Declined")
                toast("Request declined")
                recreate()
            }
        }
    }

    private fun companionRequestDetails() {
        val booking = BookingStore.all(this)
            .firstOrNull { it.id == intent.getStringExtra(EXTRA_BOOKING_ID) }

        if (booking == null) {
            card("Request details", "Open a booking request to see its details.")
            return
        }

        card(
            booking.activity.ifBlank { "Booking request" },
            "Requested by: ${booking.ownerName.ifBlank { booking.ownerEmail.ifBlank { "CareBuddy user" } }}\n" +
                    "Date: ${booking.date}\n" +
                    "Time: ${booking.time}\n" +
                    "Location: ${booking.location}\n" +
                    "Duration: ${booking.durationHours} hour(s)\n" +
                    "Rate: ${booking.rate}\n" +
                    "Estimate: ${BookingPricing.format(BookingPricing.total(booking))}\n" +
                    "Payment method: ${booking.payment}\n" +
                    "Status: ${booking.status}\n" +
                    (if (booking.notes.isBlank()) "" else "\nNotes: ${booking.notes}\n") +
                    "\nPayment is not processed in this demo."
        )
    }

    private fun companionMyBookings() {
        val bookings = companionBookings().filter { it.status !in listOf("Declined", "Cancelled") }

        if (bookings.isEmpty()) {
            card("No bookings yet", "Accepted requests and upcoming work will appear here.")
            return
        }

        bookings.forEach { booking ->
            card(
                "${booking.activity.ifBlank { "Booking" }} · ${booking.date}",
                "${booking.companion} · ${booking.time}\n" +
                        "${booking.location}\n" +
                        "${booking.durationHours} hour(s) · ${booking.rate}\n" +
                        "Status: ${booking.status}"
            )
            if (booking.status == "Accepted") {
                action("Mark visit complete") {
                    changeBookingStatus(booking.id, "Completed")
                    toast("Booking marked complete")
                    recreate()
                }
            }
        }
    }

    private fun companionAvailability() {
        val name = prefs.getString("companion_name", "Companion").orEmpty()
        val key = CompanionAvailability.key(name)
        val availableKey = "available_$key"
        val available = prefs.getBoolean(availableKey, false)

        card(
            "Your status: ${if (available) "Available" else "Unavailable"}",
            "Let elderly users know whether you can accept new requests."
        )

        val toggle = Switch(this).apply {
            text = "Accept new booking requests"
            isChecked = available
            setOnCheckedChangeListener { _, enabled ->
                prefs.edit().putBoolean(availableKey, enabled).apply()
                FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                    FirebaseCareBuddyStore.setCompanionAvailability(uid, enabled) { error ->
                        if (error != null) toast("Saved locally, Firebase update failed.")
                    }
                }
                recreate()
            }
        }
        content.addView(toggle, match())

        renderWeeklySchedule(key)
    }

    private fun renderWeeklySchedule(key: String) {
        val startKey = "schedule_start_$key"
        val endKey = "schedule_end_$key"
        val startTime = prefs.getString(startKey, "09:00 AM").orEmpty()
        val endTime = prefs.getString(endKey, "05:00 PM").orEmpty()

        card("Weekly schedule", "Choose which days you can accept bookings.")

        listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            .forEach { day ->
                val dayKey = "schedule_${key}_${day.lowercase()}"
                val weekdayDefault = day !in listOf("Saturday", "Sunday")
                val daySwitch = Switch(this).apply {
                    text = day
                    isChecked = prefs.getBoolean(dayKey, weekdayDefault)
                    setOnCheckedChangeListener { _, enabled ->
                        prefs.edit().putBoolean(dayKey, enabled).apply()
                    }
                }
                content.addView(daySwitch, match())
            }

        action("Start time · $startTime") { chooseScheduleTime(startKey, startTime) }
        action("End time · $endTime") { chooseScheduleTime(endKey, endTime) }
    }

    private fun companionEarnings() {
        val completed = companionBookings().filter { it.status == "Completed" }
        val total = completed.sumOf { BookingPricing.total(it) }

        card(
            "Earnings summary",
            "${completed.size} completed booking(s)\n" +
                    "Total earned: ${BookingPricing.format(total)}\n" +
                    "Payouts are processed externally in this version."
        )

        completed.forEach {
            card(
                "${it.date} · ${it.time}",
                "${it.activity.ifBlank { "Service" }} · ${it.companion}\n" +
                        "${it.durationHours} hour(s) · ${BookingPricing.format(BookingPricing.total(it))}"
            )
        }
    }

    private fun companionProfile() {
        val input = EditText(this).apply {
            setText(prefs.getString("companion_name", "Companion"))
            hint = "Display name"
        }
        content.addView(input, match())

        action("Save profile") {
            val editedName = input.text.toString().trim()
            val email = prefs.getString("current_email", "").orEmpty()

            prefs.edit()
                .putString("companion_name", editedName)
                .putString("current_name", editedName)
                .apply {
                    if (email.isNotBlank()) putString("profile_name:$email", editedName)
                }
                .apply()

            if (email.isNotBlank()) LocalAccountStore.rename(this, email, editedName)

            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                FirebaseCareBuddyStore.updateUserName(uid, editedName, "companion") { error ->
                    if (error != null) toast("Saved locally, Firebase update failed.")
                }
            }
            toast("Profile saved")
        }

        card("Verification", verificationText())
    }

    private fun companionNotifications() {
        val pending = companionBookings().filter { it.status == "Pending" }
        if (pending.isEmpty()) card("No new requests", "New booking request alerts will appear here.")
        pending.forEach {
            card("Booking request", "${it.activity.ifBlank { "Service" }} · ${it.date} at ${it.time}")
        }

        val completedCount = companionBookings().count { it.status == "Completed" }
        if (completedCount > 0) card("Completed bookings", "$completedCount booking(s) marked complete.")

        val companionName = prefs.getString("companion_name", "").orEmpty()
        ReviewStore.all(this)
            .filter { it.companion.equals(companionName, true) }
            .take(3)
            .forEach { review ->
                card(
                    "New review · ${review.stars}/5",
                    "${review.reviewer}: ${review.comment.ifBlank { "No written comment" }}"
                )
            }
    }

    private fun companionVerification() {
        val mode = prefs.getString("identity_submission_mode", "not_submitted")
        val message = when (mode) {
            "uploaded" -> "Your IC and face photos have been sent for admin review. You'll be notified once verified."
            "local_only" -> "You continued without uploading your photos. Admin review is pending."
            else -> "Complete the IC and face-photo step to submit your identity for review."
        }
        card("Identity verification", message)
    }

    private fun companionMenu() {
        listOf(
            "Booking Requests", "Request Details", "My Bookings",
            "Availability", "Earnings", "Profile", "Notifications", "Verification"
        ).forEach { page -> action(page) { open(page) } }
    }

    // ===================================================================
    // ADMIN PAGE
    // ===================================================================
    private fun adminPage(screen: String) {
        when (screen) {
            "Dashboard" -> adminDashboard()
            "User Management" -> manageAccounts("elderly", "Elderly users")
            "Companion Management" -> manageAccounts("companion", "Companions")
            "Verification" -> manageVerification()
            "Booking Management" -> adminBookingManagement()
            "Payment Management" -> adminPaymentManagement()
            "Reports/Reviews" -> adminReports()
            "Settings" -> adminSettings()
            else -> card(screen, "Admin workspace")
        }

        if (screen != "Dashboard") {
            action("Back to admin dashboard") { open("Dashboard") }
        }
    }

    private fun adminDashboard() {
        val bookings = BookingStore.all(this)
        card("Shared service overview", "Loading accounts and bookings from Firebase…")

        FirebaseCareBuddyStore.loadAllUsers { accounts, error ->
            if (isFinishing || isDestroyed) return@loadAllUsers

            if (error != null) {
                card(
                    "Firebase unavailable",
                    "Could not load shared account totals. " +
                            "Confirm Firestore is enabled and this signed-in account is an admin."
                )
                return@loadAllUsers
            }

            val elderlyCount = accounts.count { it["role"] == "elderly" }
            val companionCount = accounts.count { it["role"] == "companion" }

            card(
                "Account totals",
                "Elderly users: $elderlyCount\n" +
                        "Companions: $companionCount\n" +
                        "Bookings on this device: ${bookings.size}\n" +
                        "Pending: ${bookings.count { it.status == "Pending" }}\n" +
                        "Accepted: ${bookings.count { it.status == "Accepted" }}\n" +
                        "Completed: ${bookings.count { it.status == "Completed" }}"
            )
        }
        adminMenu()
    }

    private fun adminBookingManagement() {
        val bookings = BookingStore.all(this)

        if (bookings.isEmpty()) {
            card("No bookings", "Bookings created in the app will appear here.")
            return
        }

        bookings.forEach { booking ->
            card(
                "${booking.activity.ifBlank { "Booking" }} · ${booking.date}",
                "ID: ${booking.id}\n" +
                        "Requested by: ${booking.ownerName.ifBlank { booking.ownerEmail.ifBlank { "CareBuddy user" } }}\n" +
                        "Companion: ${booking.companion} · ${booking.time}\n" +
                        "${booking.location}\n" +
                        "${booking.durationHours} hour(s) · ${BookingPricing.format(BookingPricing.total(booking))}\n" +
                        "${booking.payment}\n" +
                        "Status: ${booking.status}" +
                        (if (booking.notes.isBlank()) "" else "\nNotes: ${booking.notes}")
            )

            if (booking.status !in listOf("Completed", "Cancelled", "Declined")) {
                action("Mark ${if (booking.status == "Accepted") "completed" else "accepted"}") {
                    changeBookingStatus(
                        booking.id,
                        if (booking.status == "Accepted") "Completed" else "Accepted"
                    )
                    recreate()
                }
            }
            if (booking.status !in listOf("Cancelled", "Completed")) {
                action("Cancel booking") {
                    changeBookingStatus(booking.id, "Cancelled")
                    recreate()
                }
            }
        }
    }

    private fun adminPaymentManagement() {
        val bookings = BookingStore.all(this)

        if (bookings.isEmpty()) {
            card(
                "No payment records",
                "Payment method selections will appear here."
            )
            return
        }

        bookings.forEach {
            card(
                "Booking ${it.id}",
                "${it.date} · ${it.companion}\n" +
                        "Estimate: ${BookingPricing.format(BookingPricing.total(it))}\n" +
                        "Selected method: ${it.payment}\n" +
                        "Payment status: Recorded"
            )
        }
    }

    private fun adminReports() {
        val bookings = BookingStore.all(this)
        val total = bookings.size.coerceAtLeast(1)
        val completed = bookings.count { it.status == "Completed" }
        val pending = bookings.count { it.status == "Pending" }
        val cancelledOrDeclined = bookings.count { it.status in listOf("Cancelled", "Declined") }

        card(
            "Booking report",
            "Total bookings: ${bookings.size}\n" +
                    "Completed: $completed (${completed * 100 / total}%)\n" +
                    "Pending: $pending (${pending * 100 / total}%)\n" +
                    "Cancelled/declined: $cancelledOrDeclined\n\n" +
                    "Customer reviews are listed below."
        )

        card("Reviews", "Loading reviews from Firebase…")

        FirebaseCareBuddyStore.loadReviews { reviews, error ->
            if (isFinishing || isDestroyed) return@loadReviews

            if (error != null) {
                card("Reviews unavailable", "Could not load shared reviews. Check Firestore setup.")
                return@loadReviews
            }

            card("Shared reviews", "${reviews.size} review(s)")
            if (reviews.isEmpty()) {
                card("No reviews yet", "Reviews submitted after completed bookings will appear here.")
            }
            reviews.forEach { review ->
                val stars = "★".repeat(review.stars.coerceIn(0, 5)) +
                        "☆".repeat((5 - review.stars).coerceIn(0, 5))
                card(
                    "${review.companion} · $stars",
                    "${review.reviewer}\n${review.comment.ifBlank { "No written comment" }}"
                )
            }
        }
    }

    private fun adminSettings() {
        card(
            "Platform settings · On-device demo",
            "Changes apply on this device only. Firebase is needed to share settings with other users."
        )

        val bookingsEnabled = prefs.getBoolean("booking_requests_enabled", true)
        val bookingSwitch = Switch(this).apply {
            text = "Accept new booking requests"
            isChecked = bookingsEnabled
            setOnCheckedChangeListener { _, enabled ->
                prefs.edit().putBoolean("booking_requests_enabled", enabled).apply()
                toast(if (enabled) "New bookings enabled" else "New bookings paused")
            }
        }
        content.addView(bookingSwitch, match())

        card(
            "Service limits",
            "Payments are recorded but not charged (requires a payment gateway). " +
                    "Payouts to companions are handled externally."
        )
    }

    private fun adminMenu() {
        listOf(
            "User Management", "Companion Management", "Verification",
            "Booking Management", "Payment Management", "Reports/Reviews", "Settings"
        ).forEach { page -> action(page) { open(page) } }
    }

    // ===================================================================
    // SHARED: VERIFICATION / ACCOUNTS MANAGEMENT
    // ===================================================================
    private fun manageAccounts(roleFilter: String, title: String) {
        card(title, "Loading accounts from Firebase…")

        FirebaseCareBuddyStore.loadUsersByRole(roleFilter) { accounts, error ->
            if (isFinishing || isDestroyed) return@loadUsersByRole

            if (error != null) {
                card(
                    "Firebase unavailable",
                    "Could not load shared accounts. Check Firestore permissions."
                )
                return@loadUsersByRole
            }

            if (accounts.isEmpty()) {
                card("No accounts yet", "Newly registered $roleFilter accounts will appear here.")
                return@loadUsersByRole
            }

            accounts.forEach { account ->
                val uid = account["uid"] as? String ?: return@forEach
                val name = account["displayName"] as? String ?: "Unnamed user"
                val email = account["email"] as? String ?: ""
                val status = account["status"] as? String ?: "Active"
                val verification = account["verificationStatus"] as? String ?: "pending"

                card(name, "$email\nAccount: $status\nIdentity review: $verification")

                val nextStatus = if (status == "Active") "Suspended" else "Active"
                action(if (status == "Active") "Suspend account" else "Restore account") {
                    FirebaseCareBuddyStore.setUserStatus(uid, nextStatus) { updateError ->
                        if (updateError != null) {
                            toast("Could not update this account in Firebase.")
                        } else {
                            toast("Account marked $nextStatus")
                            recreate()
                        }
                    }
                }
            }
        }
    }

    private fun manageVerification() {
        card(
            "Identity verification",
            "Uploaded IC and face photos can be reviewed below. " +
                    "Approve or reject each pending submission."
        )

        FirebaseCareBuddyStore.loadPendingVerificationsBase64 { submissions, error ->
            if (isFinishing || isDestroyed) return@loadPendingVerificationsBase64

            if (error != null) {
                card(
                    "Firebase unavailable",
                    "Could not load verification requests. Confirm Firestore is enabled and this signed-in account has the admin role."
                )
                return@loadPendingVerificationsBase64
            }

            if (submissions.isEmpty()) {
                card(
                    "No pending requests",
                    "No verification requests are waiting for review."
                )
            } else {
                submissions.forEach { submission ->
                    renderVerificationSubmission(submission)
                }
            }
        }
    }

    private fun renderVerificationSubmission(submission: FirebaseCareBuddyStore.VerificationBase64) {
        val uid = submission.uid
        val role = submission.role
        FirebaseCareBuddyStore.loadUserProfile(uid) { profile, _ ->
            if (isFinishing || isDestroyed) return@loadUserProfile

            val name = profile?.get("displayName") as? String ?: "CareBuddy user"
            val email = profile?.get("email") as? String ?: ""

            card("$name · $role", email)
            addVerificationImage("IC image", submission.icBase64)
            addVerificationImage("Face photo", submission.faceBase64)

            action("Approve identity") { reviewVerification(uid, "approved") }
            action("Reject identity") { reviewVerification(uid, "rejected") }
        }
    }

    private fun reviewVerification(uid: String, status: String) {
        FirebaseCareBuddyStore.reviewVerification(uid, status) { reviewError ->
            if (reviewError != null) {
                toast("Could not $status this request. Check Firebase rules.")
            } else {
                toast("Identity $status")
                recreate()
            }
        }
    }

    private fun addVerificationImage(label: String, encodedImage: String) {
        val title = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(color(R.color.care_muted))
            setPadding(0, dp(8), 0, dp(4))
        }
        content.addView(title, match())

        val image = ImageView(this).apply {
            contentDescription = label
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundResource(R.drawable.bg_card)
        }
        content.addView(
            image,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180))
        )

        val bytes = runCatching { Base64.decode(encodedImage, Base64.DEFAULT) }.getOrNull()
        if (bytes != null) {
            image.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        } else {
                image.visibility = View.GONE
                title.text = "$label · unavailable"
        }
    }

    // ===================================================================
    // SHARED HELPERS
    // ===================================================================
    private fun verificationText(): String {
        return when (prefs.getString("identity_submission_mode", "not_submitted")) {
            "uploaded" -> "Your IC and face photos were sent for admin review. Status: pending."
            "local_only" -> "You continued without uploading your photos. Admin review remains pending because the images were not sent."
            else -> "Complete the IC and face-photo step to submit your identity for review."
        }
    }

    private fun companionBookings(): List<CareBooking> {
        val name = prefs.getString("companion_name", "").orEmpty()
        return BookingStore.all(this).filter { booking ->
            name.isBlank() || name == "Companion" || booking.companion.equals(name, true)
        }
    }

    private fun changeBookingStatus(bookingId: String, status: String) {
        BookingStore.updateStatus(this, bookingId, status)
        FirebaseCareBuddyStore.updateBookingStatus(bookingId, status) { error ->
            if (error != null) toast("Status saved locally, Firebase sync failed.")
        }
    }

    private fun chooseScheduleTime(prefKey: String, current: String) {
        val parsed = Regex("(\\d{1,2}):(\\d{2}) (AM|PM)", RegexOption.IGNORE_CASE).find(current)
        val currentHour = parsed?.groupValues?.get(1)?.toIntOrNull()?.rem(12) ?: 9
        val hour = if (parsed?.groupValues?.get(3)?.uppercase() == "PM") currentHour + 12 else currentHour
        val minute = parsed?.groupValues?.get(2)?.toIntOrNull() ?: 0

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val displayHour = if (selectedHour % 12 == 0) 12 else selectedHour % 12
            val displayTime = String.format(
                "%02d:%02d %s",
                displayHour,
                selectedMinute,
                if (selectedHour < 12) "AM" else "PM"
            )
            prefs.edit().putString(prefKey, displayTime).apply()
            recreate()
        }, hour, minute, false).show()
    }

    private fun attachBookingListener() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        bookingListener = FirebaseCareBuddyStore.listenBookings(
            this,
            role,
            uid,
            onChanged = { recreate() },
            onError = { error -> Log.w(TAG, "Firestore booking sync failed", error) }
        )
    }

    private fun signOut() {
        FirebaseAuth.getInstance().signOut()
        prefs.edit()
            .remove("current_email")
            .remove("current_name")
            .remove("current_role")
            .apply()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
        )
        finish()
    }

    // ===================================================================
    // UI HELPERS
    // ===================================================================
    private fun open(screen: String, bookingId: String? = null) {
        val intent = Intent(this, RolePortalActivity::class.java)
            .putExtra(EXTRA_ROLE, role)
            .putExtra(EXTRA_SCREEN, screen)
        if (bookingId != null) intent.putExtra(EXTRA_BOOKING_ID, bookingId)
        startActivity(intent)
    }

    private fun action(
        label: String,
        destination: Class<*>? = null,
        onClick: (() -> Unit)? = null
    ) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(color(accentColor))
            background = ContextCompat.getDrawable(this@RolePortalActivity, R.drawable.bg_card)
            setOnClickListener {
                when {
                    onClick != null -> onClick()
                    destination != null -> startActivity(Intent(this@RolePortalActivity, destination))
                }
            }
        }
        content.addView(
            button,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
                .apply { topMargin = dp(10) }
        )
    }

    private fun card(title: String, body: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = ContextCompat.getDrawable(this@RolePortalActivity, R.drawable.bg_card)
        }
        box.addView(
            TextView(this).apply {
                text = title
                textSize = 17f
                setTextColor(color(R.color.care_ink))
                setTypeface(typeface, Typeface.BOLD)
            }
        )
        box.addView(
            TextView(this).apply {
                text = body
                textSize = 14f
                setTextColor(color(R.color.care_muted))
                setPadding(0, dp(6), 0, 0)
            }
        )
        content.addView(
            box,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )
    }

    private fun text(value: String, size: Int, colorRes: Int, bold: Boolean, top: Int = 0) {
        val view = TextView(this).apply {
            text = value
            textSize = size.toFloat()
            gravity = Gravity.START
            setTextColor(color(colorRes))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
        content.addView(
            view,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(top) }
        )
    }

    private fun match() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
