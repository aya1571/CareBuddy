package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnnouncementsActivity : AppCompatActivity() {

    private lateinit var rvAnnouncements: RecyclerView
    private lateinit var loadingState: LinearLayout
    private lateinit var emptyState: LinearLayout
    private val CREATE_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_announcements)

        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val fabCreate = findViewById<FloatingActionButton>(R.id.fabCreate)
        rvAnnouncements = findViewById(R.id.rvAnnouncements)
        loadingState = findViewById(R.id.loadingState)
        emptyState = findViewById(R.id.emptyState)

        rvAnnouncements.layoutManager = LinearLayoutManager(this)

        btnClose.setOnClickListener { finish() }

        fabCreate.setOnClickListener {
            startActivityForResult(
                Intent(this, CreateAnnouncementActivity::class.java),
                CREATE_REQUEST
            )
        }

        loadAnnouncements()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_REQUEST && resultCode == RESULT_OK) {
            loadAnnouncements()
        }
    }

    private fun loadAnnouncements() {
        loadingState.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        rvAnnouncements.visibility = View.GONE

        FirebaseCareBuddyStore.loadAnnouncements { announcements, error ->
            if (isFinishing || isDestroyed) return@loadAnnouncements
            loadingState.visibility = View.GONE

            if (error != null) {
                Toast.makeText(
                    this,
                    "Failed to load: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
                emptyState.visibility = View.VISIBLE
                return@loadAnnouncements
            }

            if (announcements.isEmpty()) {
                emptyState.visibility = View.VISIBLE
                rvAnnouncements.visibility = View.GONE
            } else {
                emptyState.visibility = View.GONE
                rvAnnouncements.visibility = View.VISIBLE

                val dateFormat = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault())
                rvAnnouncements.adapter = AnnouncementAdapter(announcements, dateFormat)
            }
        }
    }
}