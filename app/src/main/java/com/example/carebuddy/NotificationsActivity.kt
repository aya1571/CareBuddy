package com.example.carebuddy

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationsActivity : AppCompatActivity() {

    private lateinit var rvNotifications: RecyclerView
    private lateinit var emptyState: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        val btnClose = findViewById<ImageView>(R.id.btnClose)
        rvNotifications = findViewById(R.id.rvNotifications)
        emptyState = findViewById(R.id.emptyState)

        btnClose.setOnClickListener { finish() }
        rvNotifications.layoutManager = LinearLayoutManager(this)

        loadNotifications()
    }

    private fun loadNotifications() {
        emptyState.visibility = View.GONE
        rvNotifications.visibility = View.GONE

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            emptyState.visibility = View.VISIBLE
            return
        }

        FirebaseCareBuddyStore.loadNotifications(uid) { items, error ->
            if (isFinishing || isDestroyed) return@loadNotifications

            // Also load announcements (they apply to all users)
            FirebaseCareBuddyStore.loadAnnouncements { announcements, _ ->
                if (isFinishing || isDestroyed) return@loadAnnouncements

                val announcementItems = announcements.map { a ->
                    NotificationItem(
                        id = "announcement-${a.id}",
                        type = "announcement",
                        title = a.title.ifBlank { "Announcement" },
                        message = a.message,
                        timestamp = formatTimestamp(a.createdAt)
                    )
                }

                val notificationItems = items.map { map ->
                    NotificationItem(
                        id = map["id"] as? String ?: "",
                        type = map["type"] as? String ?: "booking",
                        title = map["title"] as? String ?: "Notification",
                        message = map["message"] as? String ?: "",
                        timestamp = formatTimestamp(
                            (map["createdAt"] as? com.google.firebase.Timestamp)?.seconds?.times(1000)
                                ?: System.currentTimeMillis()
                        )
                    )
                }

                val allItems = announcementItems + notificationItems

                if (allItems.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    rvNotifications.visibility = View.GONE
                } else {
                    emptyState.visibility = View.GONE
                    rvNotifications.visibility = View.VISIBLE

                    val adapter = NotificationAdapter(allItems) { item ->
                        Toast.makeText(this, item.title, Toast.LENGTH_SHORT).show()
                    }
                    rvNotifications.adapter = adapter
                }
            }
        }
    }

    private fun formatTimestamp(millis: Long): String {
        return SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault())
            .format(Date(millis))
    }
}