package com.example.carebuddy

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

/** Live map for the owner of the booking (including a family member who booked for someone). */
class TrackCompanionActivity : AppCompatActivity() {
    private var trackingListener: ListenerRegistration? = null
    private var bookingListener: ListenerRegistration? = null
    private lateinit var status: TextView
    private lateinit var map: WebView
    private var tripStatus: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bookingId = intent.getStringExtra("bookingId").orEmpty()
        if (bookingId.isBlank()) { finish(); return }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(16)
        }
        val back = TextView(this).apply {
            text = "‹   Live companion location"
            textSize = 20f
            setTextColor(Color.rgb(45, 35, 80))
            setPadding(8)
            setOnClickListener { finish() }
        }
        status = TextView(this).apply {
            text = "Waiting for the companion to start the trip…"
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(8)
        }
        map = WebView(this).apply {
            settings.javaScriptEnabled = false
            settings.userAgentString = "${settings.userAgentString} CareBuddy/1.0"
            webViewClient = WebViewClient()
            setBackgroundColor(Color.rgb(240, 244, 247))
        }
        root.addView(back, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(map, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) { finish(); return }
        FirebaseCareBuddyStore.loadBooking(bookingId) { booking, error ->
            if (isFinishing || isDestroyed) return@loadBooking
            if (error != null || booking == null) {
                Toast.makeText(this, "Could not load this booking.", Toast.LENGTH_LONG).show()
                finish()
            } else if (booking.ownerUid != uid && booking.companionUid != uid) {
                Toast.makeText(this, "Only this booking's participants can view its location.", Toast.LENGTH_LONG).show()
                finish()
            } else if (booking.ownerUid != uid) {
                Toast.makeText(this, "The booking owner can view the live tracking screen.", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        trackingListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("bookings").document(bookingId).collection("tracking").document("current")
            .addSnapshotListener { snapshot, error ->
                if (isFinishing || isDestroyed) return@addSnapshotListener
                if (tripStatus.isNotBlank() && tripStatus != "On The Way") return@addSnapshotListener
                val lat = snapshot?.getDouble("latitude")
                val lon = snapshot?.getDouble("longitude")
                if (error != null || lat == null || lon == null) {
                    status.text = "Waiting for the companion's location. The companion must start the trip and allow location access."
                    return@addSnapshotListener
                }
                val updated = snapshot.getTimestamp("updatedAt")?.toDate()
                val accuracy = snapshot.getDouble("accuracyMeters")?.toInt()
                val age = updated?.let { (System.currentTimeMillis() - it.time).coerceAtLeast(0L) / 1000L }
                status.text = buildString {
                    append("Companion location updated ")
                    append(if (age == null) "just now" else if (age < 60) "$age sec ago" else "${age / 60} min ago")
                    if (accuracy != null) append(" • about ±${accuracy} m")
                    if (age != null && age > 90) append("\nLocation may be stale; check the companion's connection.")
                }
                showMap(lat, lon)
            }
        bookingListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("bookings").document(bookingId)
            .addSnapshotListener { snapshot, _ ->
                if (isFinishing || isDestroyed || snapshot == null) return@addSnapshotListener
                tripStatus = snapshot.getString("status").orEmpty()
                if (tripStatus == "In Progress" || tripStatus == "Completed") {
                    status.text = "The companion has arrived. Live location sharing has stopped."
                    map.loadData("", "text/html", "UTF-8")
                }
            }
    }

    private fun showMap(lat: Double, lon: Double) {
        val delta = 0.008
        val bbox = "${lon - delta},${lat - delta},${lon + delta},${lat + delta}"
        val url = "https://www.openstreetmap.org/export/embed.html?bbox=$bbox&layer=mapnik&marker=$lat,$lon"
        map.loadUrl(url)
    }

    override fun onDestroy() {
        trackingListener?.remove()
        bookingListener?.remove()
        if (::map.isInitialized) map.destroy()
        super.onDestroy()
    }
}
