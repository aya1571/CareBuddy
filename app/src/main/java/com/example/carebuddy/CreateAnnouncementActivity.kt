package com.example.carebuddy

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CreateAnnouncementActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_announcement)

        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val etTitle = findViewById<EditText>(R.id.etTitle)
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)

        btnClose.setOnClickListener { finish() }

        btnSend.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val message = etMessage.text.toString().trim()

            when {
                title.isBlank() -> {
                    Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show()
                }
                title.length < 4 -> {
                    Toast.makeText(this, "Title must be at least 4 characters", Toast.LENGTH_SHORT).show()
                }
                message.isBlank() -> {
                    Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
                }
                message.length < 10 -> {
                    Toast.makeText(this, "Message must be at least 10 characters", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    val adminName = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
                        .getString("current_name", "System Admin") ?: "System Admin"

                    btnSend.isEnabled = false
                    FirebaseCareBuddyStore.createAnnouncement(
                        title = title,
                        message = message,
                        sentBy = adminName
                    ) { error ->
                        btnSend.isEnabled = true
                        if (error != null) {
                            Toast.makeText(
                                this,
                                "Failed to send: ${error.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(this, "Announcement sent!", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                    }
                }
            }
        }
    }
}