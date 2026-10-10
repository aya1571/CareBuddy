package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class MyReviewsActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private var uid: String = ""
    private var reviews: List<CareReview> = emptyList()
    private var reviewsMessage: String? = null
    private var bookingListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) {
            finish()
            return
        }

        val scroll = ScrollView(this).apply { setBackgroundResource(R.drawable.bg_screen) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val close = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            contentDescription = "Close"
            setColorFilter(getColor(R.color.care_ink))
            setOnClickListener { finish() }
        }
        header.addView(close, LinearLayout.LayoutParams(dp(32), dp(32)))
        val title = TextView(this).apply {
            text = "My Reviews"
            textSize = 22f
            setTextColor(getColor(R.color.care_ink))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(12), 0, 0, 0)
        }
        header.addView(title)
        root.addView(header)

        root.addView(TextView(this).apply {
            text = "Rate companions after completed bookings, or view reviews you have sent."
            textSize = 14f
            setTextColor(getColor(R.color.care_muted))
            setPadding(0, dp(10), 0, dp(18))
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)

        renderReviews()
        FirebaseCareBuddyStore.loadReviewsForUser(uid) { loaded, error ->
            if (isFinishing || isDestroyed) return@loadReviewsForUser
            if (error == null) {
                reviews = loaded
                reviewsMessage = null
            } else {
                reviewsMessage = "Could not load shared reviews. Check your connection and Firestore rules."
            }
            renderReviews()
        }
    }

    override fun onStart() {
        super.onStart()
        if (uid.isBlank()) return
        bookingListener = FirebaseCareBuddyStore.listenBookings(
            context = this,
            role = "elderly",
            uid = uid,
            onChanged = { renderReviews() },
            onError = { error -> android.util.Log.w("MyReviews", "Booking sync failed: ${error.message}") }
        )
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) renderReviews()
    }

    override fun onStop() {
        bookingListener?.remove()
        bookingListener = null
        super.onStop()
    }

    private fun renderReviews() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val completed = BookingStore.all(this).filter { it.ownerUid == uid && it.status == "Completed" }
        if (completed.isEmpty()) {
            addCard("No completed visits yet", "When a companion completes one of your bookings, you can leave a review here.")
            reviewsMessage?.let { addCard("Reviews unavailable", it) }
            return
        }

        completed.forEach { booking ->
            val review = reviews.firstOrNull { it.bookingId == booking.id }
                ?: ReviewStore.forBooking(this, booking.id)
            if (review == null) {
                addCard(booking.companion.ifBlank { "Completed booking" }, "${booking.activity} · ${booking.date} at ${booking.time}")
                val rate = TextView(this).apply {
                    text = "Rate this visit"
                    textSize = 15f
                    setTextColor(getColor(R.color.care_purple))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    setBackgroundResource(R.drawable.bg_primary)
                    setOnClickListener {
                        startActivity(
                            Intent(this@MyReviewsActivity, RolePortalActivity::class.java)
                                .putExtra(RolePortalActivity.EXTRA_ROLE, "elderly")
                                .putExtra(RolePortalActivity.EXTRA_SCREEN, "Rate and Review")
                                .putExtra(RolePortalActivity.EXTRA_BOOKING_ID, booking.id)
                        )
                    }
                }
                list.addView(rate, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(14) })
            } else {
                val stars = "★".repeat(review.stars.coerceIn(0, 5)) + "☆".repeat((5 - review.stars).coerceIn(0, 5))
                val text = buildString {
                    append("${booking.activity} · ${booking.date}\n$stars")
                    if (review.comment.isNotBlank()) append("\n${review.comment}")
                }
                addCard(booking.companion.ifBlank { "Your review" }, text)
            }
        }
        reviewsMessage?.let { addCard("Reviews unavailable", it) }
    }

    private fun addCard(title: String, message: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.bg_card)
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(getColor(R.color.care_ink))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(getColor(R.color.care_muted))
            setPadding(0, dp(6), 0, 0)
        })
        list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
