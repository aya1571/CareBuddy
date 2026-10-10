package com.example.carebuddy

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class VerificationActivity : AppCompatActivity() {

    private lateinit var rvVerifications: RecyclerView
    private lateinit var loadingState: LinearLayout
    private lateinit var emptyState: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification)

        val btnClose = findViewById<ImageView>(R.id.btnClose)
        rvVerifications = findViewById(R.id.rvVerifications)
        loadingState = findViewById(R.id.loadingState)
        emptyState = findViewById(R.id.emptyState)

        rvVerifications.layoutManager = LinearLayoutManager(this)
        btnClose.setOnClickListener { finish() }

        loadPendingVerifications()
    }

    // ===================================================================
    // LOAD PENDING VERIFICATIONS — Base64 version
    // ===================================================================
    private fun loadPendingVerifications() {
        showLoading(true)

        FirebaseCareBuddyStore.loadPendingVerificationsBase64 { submissions, error ->
            if (isFinishing || isDestroyed) return@loadPendingVerificationsBase64

            if (error != null) {
                showLoading(false)
                Toast.makeText(
                    this,
                    "Failed to load: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
                return@loadPendingVerificationsBase64
            }

            FirebaseCareBuddyStore.loadAllUsers { users, usersError ->
                if (isFinishing || isDestroyed) return@loadAllUsers
                if (usersError != null) {
                    showLoading(false)
                    Toast.makeText(this, "Could not load pending accounts: ${usersError.localizedMessage}", Toast.LENGTH_LONG).show()
                    return@loadAllUsers
                }
                val submittedUids = submissions.map { it.uid }.toSet()
                val withImages = submissions.map { sub ->
                    VerificationItem(
                        uid = sub.uid, name = "Loading...", email = "", role = sub.role,
                        icPath = "", facePath = "", icBytes = decodeBase64(sub.icBase64),
                        faceBytes = decodeBase64(sub.faceBase64), hasSubmission = true
                    )
                }
                val withoutImages = users.filter { user ->
                    (user["verificationStatus"] as? String).equals("pending", true) &&
                        (user["uid"] as? String) !in submittedUids
                }.mapNotNull { user ->
                    val uid = user["uid"] as? String ?: return@mapNotNull null
                    VerificationItem(uid, user["displayName"] as? String ?: "CareBuddy user",
                        user["email"] as? String ?: "", user["role"] as? String ?: "user", "", "",
                        hasSubmission = false)
                }
                val items = withImages + withoutImages
                if (items.isEmpty()) {
                    showLoading(false)
                    showEmpty(true)
                    return@loadAllUsers
                }
                val adapter = VerificationAdapter(
                    submissions = items,
                    onApprove = { confirmAction(it, approve = true) },
                    onReject = { confirmAction(it, approve = false) }
                )
                rvVerifications.adapter = adapter
                showLoading(false)
                showEmpty(false)
                if (withoutImages.isNotEmpty()) {
                    Toast.makeText(this, "Accounts without submitted images must be checked in person before approval.", Toast.LENGTH_LONG).show()
                }
                withImages.forEach { item ->
                    FirebaseCareBuddyStore.loadUserProfile(item.uid) { profile, _ ->
                        if (isFinishing || isDestroyed) return@loadUserProfile
                        item.name = profile?.get("displayName") as? String ?: "CareBuddy user"
                        item.email = profile?.get("email") as? String ?: ""
                        val currentAdapter = rvVerifications.adapter as? VerificationAdapter ?: return@loadUserProfile
                        val index = items.indexOfFirst { it.uid == item.uid }
                        if (index >= 0) currentAdapter.notifyItemChanged(index)
                    }
                }
            }
        }
    }

    // ===================================================================
    // DECODE BASE64 → ByteArray (for adapter)
    // ===================================================================
    private fun decodeBase64(base64: String?): ByteArray? {
        if (base64.isNullOrBlank()) return null
        return try {
            Base64.decode(base64, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    // ===================================================================
    // APPROVE / REJECT
    // ===================================================================
    private fun confirmAction(item: VerificationItem, approve: Boolean) {
        val title = if (approve) "Approve this request?" else "Reject this request?"
        val message = if (!item.hasSubmission && approve) {
            "No identity images were submitted in the app. Only approve after checking the original IC in person."
        } else if (approve) {
            "The user will be able to use CareBuddy fully."
        } else {
            "The user will need to submit new documents."
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(if (approve) "Yes, Approve" else "Yes, Reject") { _, _ ->
                val status = if (approve) "approved" else "rejected"
                val review: ((Exception?) -> Unit) = review@{ error ->
                    if (isFinishing || isDestroyed) return@review
                    if (error != null) {
                        Toast.makeText(
                            this,
                            "Failed: ${error.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            if (approve) "Approved" else "Rejected",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadPendingVerifications()
                    }
                }
                if (item.hasSubmission) FirebaseCareBuddyStore.reviewVerification(item.uid, status, review)
                else FirebaseCareBuddyStore.setVerificationStatus(item.uid, status, review)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ===================================================================
    // STATE SWITCHERS
    // ===================================================================
    private fun showLoading(show: Boolean) {
        loadingState.visibility = if (show) View.VISIBLE else View.GONE
        rvVerifications.visibility = if (show) View.GONE else rvVerifications.visibility
    }

    private fun showEmpty(show: Boolean) {
        emptyState.visibility = if (show) View.VISIBLE else View.GONE
        rvVerifications.visibility = if (show) View.GONE else View.VISIBLE
    }
}
