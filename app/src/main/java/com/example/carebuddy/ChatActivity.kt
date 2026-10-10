package com.example.carebuddy

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class ChatActivity : AppCompatActivity() {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var bookingId: String
    private lateinit var currentUid: String
    private lateinit var messagesLayout: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var composer: LinearLayout
    private lateinit var closedNotice: TextView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private var bookingListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private var bookingIsOpen = false
    private var peerUid = ""
    private var peerName = "Booking participant"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        bookingId = intent.getStringExtra("bookingId").orEmpty()
        currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        messagesLayout = findViewById(R.id.chatMessages)
        scroll = findViewById(R.id.chatScroll)
        composer = findViewById(R.id.chatComposer)
        closedNotice = findViewById(R.id.tvChatClosed)
        input = findViewById(R.id.etChatMessage)
        sendButton = findViewById(R.id.btnSendChat)

        findViewById<ImageView>(R.id.btnChatBack).setOnClickListener { finish() }
        sendButton.setOnClickListener { sendMessage() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }

        if (bookingId.isBlank() || currentUid.isBlank()) {
            Toast.makeText(this, "Open chat from an active booking", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        listenToBooking()
    }

    private fun listenToBooking() {
        bookingListener = db.collection("bookings").document(bookingId)
            .addSnapshotListener(this) { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    Toast.makeText(this, "Could not load this booking chat", Toast.LENGTH_LONG).show()
                    finish()
                    return@addSnapshotListener
                }
                val ownerUid = snapshot.getString("ownerUid").orEmpty()
                val companionUid = snapshot.getString("companionUid").orEmpty()
                if (currentUid == ownerUid) {
                    peerUid = companionUid
                    peerName = snapshot.getString("companion").orEmpty().ifBlank { "Companion" }
                } else if (currentUid == companionUid) {
                    peerUid = ownerUid
                    peerName = snapshot.getString("ownerName").orEmpty()
                        .ifBlank { snapshot.getString("ownerEmail").orEmpty().ifBlank { "Customer" } }
                } else {
                    Toast.makeText(this, "You are not part of this booking", Toast.LENGTH_LONG).show()
                    finish()
                    return@addSnapshotListener
                }
                val status = snapshot.getString("status").orEmpty()
                bookingIsOpen = status in OPEN_STATUSES
                findViewById<TextView>(R.id.tvChatName).text = peerName
                findViewById<TextView>(R.id.tvChatStatus).text = "Booking • $status"
                composer.visibility = if (bookingIsOpen) View.VISIBLE else View.GONE
                closedNotice.visibility = if (bookingIsOpen) View.GONE else View.VISIBLE
                if (messagesListener == null) listenToMessages()
            }
    }

    private fun listenToMessages() {
        messagesListener = db.collection("bookings").document(bookingId)
            .collection("messages")
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .addSnapshotListener(this) { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Could not load messages. Check your connection.", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                messagesLayout.removeAllViews()
                val docs = snapshot?.documents.orEmpty()
                if (docs.isEmpty()) {
                    val empty = TextView(this).apply {
                        text = "Messages about this booking appear here."
                        gravity = Gravity.CENTER
                        setTextColor(ContextCompat.getColor(this@ChatActivity, R.color.care_muted))
                        setPadding(12, 28, 12, 28)
                    }
                    messagesLayout.addView(empty)
                } else {
                    docs.forEach { doc ->
                        val senderUid = doc.getString("senderUid").orEmpty()
                        val message = doc.getString("text").orEmpty()
                        if (message.isNotBlank()) addMessageBubble(message, senderUid == currentUid, doc.getTimestamp("sentAt"))
                    }
                }
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
    }

    private fun addMessageBubble(text: String, mine: Boolean, timestamp: Timestamp?) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (mine) Gravity.END else Gravity.START
            setPadding(0, 5, 0, 5)
        }
        val bubble = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(if (mine) Color.WHITE else ContextCompat.getColor(this@ChatActivity, R.color.care_ink))
            setPadding(14, 10, 14, 10)
            background = GradientDrawable().apply {
                setColor(if (mine) ContextCompat.getColor(this@ChatActivity, R.color.care_purple) else Color.WHITE)
                cornerRadius = 24f
            }
            setMaxWidth((resources.displayMetrics.widthPixels * 0.78f).toInt())
        }
        row.addView(bubble)
        val time = TextView(this).apply {
            this.text = timestamp?.toDate()?.let { SimpleDateFormat("h:mm a", Locale.getDefault()).format(it) } ?: "Sending…"
            textSize = 10f
            setTextColor(ContextCompat.getColor(this@ChatActivity, R.color.care_muted))
            setPadding(4, 3, 4, 0)
        }
        row.addView(time)
        messagesLayout.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isBlank()) return
        if (!bookingIsOpen) {
            closeChatUi()
            return
        }
        if (peerUid.isBlank()) {
            Toast.makeText(this, "The other participant is not linked to this booking", Toast.LENGTH_SHORT).show()
            return
        }
        sendButton.isEnabled = false
        val messageData = hashMapOf<String, Any>(
            "senderUid" to currentUid,
            "text" to text,
            "sentAt" to FieldValue.serverTimestamp()
        )
        db.collection("bookings").document(bookingId).collection("messages")
            .add(messageData)
            .addOnSuccessListener {
                input.text.clear()
                sendButton.isEnabled = bookingIsOpen
                FirebaseCareBuddyStore.createNotification(
                    userId = peerUid,
                    type = "chat",
                    title = "New message from $peerName",
                    message = text.take(120),
                    bookingId = bookingId
                )
            }
            .addOnFailureListener { error ->
                sendButton.isEnabled = bookingIsOpen
                Toast.makeText(this, "Message not sent: ${error.localizedMessage ?: "check your connection"}", Toast.LENGTH_LONG).show()
            }
    }

    private fun closeChatUi() {
        bookingIsOpen = false
        composer.visibility = View.GONE
        closedNotice.visibility = View.VISIBLE
        closedNotice.text = "This chat has ended because the service is finished."
    }

    override fun onDestroy() {
        bookingListener?.remove()
        messagesListener?.remove()
        super.onDestroy()
    }

    companion object {
        private val OPEN_STATUSES = setOf("Pending", "Accepted", "Upcoming", "Ongoing", "On The Way", "In Progress")
    }
}
