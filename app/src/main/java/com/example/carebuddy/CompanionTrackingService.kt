package com.example.carebuddy

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

fun stopAndClearTripTracking(context: Context, bookingId: String) {
    context.stopService(Intent(context, CompanionTrackingService::class.java))
    if (bookingId.isNotBlank()) {
        FirebaseFirestore.getInstance().collection("bookings").document(bookingId)
            .collection("tracking").document("current").delete()
    }
}

/** Shares only the assigned companion's location for one booking while its status is On The Way. */
class CompanionTrackingService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private var bookingId: String = ""
    private var bookingListener: ListenerRegistration? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        bookingId = intent?.getStringExtra(EXTRA_BOOKING_ID).orEmpty()
        if (bookingId.isBlank() || FirebaseAuth.getInstance().currentUser == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, trackingNotification())
        bookingListener?.remove()
        bookingListener = FirebaseFirestore.getInstance().collection("bookings").document(bookingId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && (!snapshot.exists() || snapshot.getString("status") != "On The Way")) stopSelf()
            }
        try {
            val provider = if (fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                LocationManager.GPS_PROVIDER
            } else {
                LocationManager.NETWORK_PROVIDER
            }
            locationManager.requestLocationUpdates(provider, UPDATE_INTERVAL_MS, MIN_DISTANCE_METERS, this)
            locationManager.getLastKnownLocation(provider)?.let(::onLocationChanged)
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("bookings").document(bookingId)
            .collection("tracking").document("current")
            .set(mapOf(
                "companionUid" to uid,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracyMeters" to location.accuracy.toDouble(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
    }

    override fun onProviderDisabled(provider: String) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    @Deprecated("Deprecated by Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onDestroy() {
        bookingListener?.remove()
        bookingListener = null
        runCatching { locationManager.removeUpdates(this) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Active trip location", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun trackingNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle("CareBuddy trip in progress")
        .setContentText("Your location is shared with the person who booked this visit.")
        .setOngoing(true)
        .build()

    companion object {
        const val EXTRA_BOOKING_ID = "tracking_booking_id"
        private const val CHANNEL_ID = "carebuddy_active_trip"
        private const val NOTIFICATION_ID = 4207
        private const val UPDATE_INTERVAL_MS = 10_000L
        private const val MIN_DISTANCE_METERS = 10f
    }
}
