package com.example.carebuddy

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// ===================================================================
// DATA MODELS
// ===================================================================

data class CareCompanionProfile(
    val uid: String,
    val displayName: String,
    val location: String,
    val hourlyRate: String,
    val bio: String,
    val skills: List<String>,
    val available: Boolean,
    val gender: String = "",
    val yearsExperience: Int = 0
)

data class CareVerificationSubmission(
    val uid: String,
    val role: String,
    val icPath: String,
    val facePath: String
)

// ===================================================================
// FIRESTORE-BACKED SHARED DATA STORE
// Local stores remain as the offline/demo cache.
// ===================================================================

object FirebaseCareBuddyStore {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // ===================================================================
    // VERIFICATION
    // ===================================================================

    fun submitVerification(
        uid: String,
        role: String,
        icPath: String,
        facePath: String,
        onComplete: (Exception?) -> Unit
    ) {
        val data = hashMapOf<String, Any>(
            "uid" to uid,
            "role" to role,
            "icPath" to icPath,
            "facePath" to facePath,
            "status" to "pending",
            "submittedAt" to FieldValue.serverTimestamp()
        )
        db.collection("verificationSubmissions").document(uid).set(data)
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it) }
    }

    fun loadPendingVerifications(
        onComplete: (List<CareVerificationSubmission>, Exception?) -> Unit
    ) {
        db.collection("verificationSubmissions")
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { result ->
                onComplete(
                    result.documents.mapNotNull { doc ->
                        CareVerificationSubmission(
                            uid = doc.id,
                            role = doc.getString("role") ?: return@mapNotNull null,
                            icPath = doc.getString("icPath") ?: return@mapNotNull null,
                            facePath = doc.getString("facePath") ?: return@mapNotNull null
                        )
                    },
                    null
                )
            }
            .addOnFailureListener { onComplete(emptyList(), it) }
    }

    fun reviewVerification(
        uid: String,
        status: String,
        onComplete: (Exception?) -> Unit
    ) {
        setVerificationStatus(uid, status) { statusError ->
            if (statusError != null) {
                onComplete(statusError)
                return@setVerificationStatus
            }
            db.collection("verificationSubmissions").document(uid)
                .update(
                    mapOf(
                        "status" to status,
                        "reviewedAt" to FieldValue.serverTimestamp()
                    )
                )
                .addOnSuccessListener { onComplete(null) }
                .addOnFailureListener { onComplete(it) }
        }
    }

    fun setVerificationStatus(
        uid: String,
        status: String,
        onComplete: (Exception?) -> Unit = {}
    ) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val role = userDoc.getString("role")
                userDoc.reference.update("verificationStatus", status)
                    .addOnSuccessListener {
                        if (role != "companion") {
                            onComplete(null)
                        } else {
                            val profileRef = db.collection("companionProfiles").document(uid)
                            profileRef.get().addOnSuccessListener { profile ->
                                if (profile.exists()) {
                                    profileRef.update("verificationStatus", status)
                                        .addOnSuccessListener { onComplete(null) }
                                        .addOnFailureListener { onComplete(it) }
                                } else {
                                    profileRef.set(
                                        mapOf(
                                            "uid" to uid,
                                            "displayName" to (userDoc.getString("displayName") ?: "CareBuddy companion"),
                                            "location" to "Kuala Lumpur",
                                            "hourlyRate" to "RM 30/hour",
                                            "bio" to "CareBuddy companion",
                                            "skills" to emptyList<String>(),
                                            "available" to false,
                                            "accountStatus" to (userDoc.getString("status") ?: "Active"),
                                            "verificationStatus" to status,
                                            "createdAt" to FieldValue.serverTimestamp()
                                        )
                                    ).addOnSuccessListener { onComplete(null) }
                                        .addOnFailureListener { onComplete(it) }
                                }
                            }.addOnFailureListener { onComplete(it) }
                        }
                    }
                    .addOnFailureListener { onComplete(it) }
            }
            .addOnFailureListener { onComplete(it) }
    }

    // ===================================================================
    // VERIFICATION — Base64 approach (no Firebase Storage needed)
    // ===================================================================

    fun submitVerificationBase64(
        uid: String,
        role: String,
        icBase64: String,
        faceBase64: String,
        onComplete: (Exception?) -> Unit
    ) {
        val data = hashMapOf<String, Any>(
            "uid" to uid,
            "role" to role,
            "icBase64" to icBase64,
            "faceBase64" to faceBase64,
            "status" to "pending",
            "submittedAt" to FieldValue.serverTimestamp()
        )
        db.collection("verificationSubmissions").document(uid).set(data)
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it) }
    }

    fun loadPendingVerificationsBase64(
        onComplete: (List<VerificationBase64>, Exception?) -> Unit
    ) {
        db.collection("verificationSubmissions")
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { result ->
                val items = result.documents.mapNotNull { doc ->
                    val ic = doc.getString("icBase64") ?: return@mapNotNull null
                    val face = doc.getString("faceBase64") ?: return@mapNotNull null
                    VerificationBase64(
                        uid = doc.id,
                        role = doc.getString("role") ?: "user",
                        icBase64 = ic,
                        faceBase64 = face
                    )
                }
                onComplete(items, null)
            }
            .addOnFailureListener { onComplete(emptyList(), it) }
    }

    data class VerificationBase64(
        val uid: String,
        val role: String,
        val icBase64: String,
        val faceBase64: String
    )

    // ===================================================================
    // REVIEWS
    // ===================================================================

    fun saveReview(
        review: CareReview,
        onComplete: (Exception?) -> Unit = {}
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            onComplete(IllegalStateException("Sign in before submitting a review."))
            return
        }
        val data = hashMapOf<String, Any>(
            "bookingId" to review.bookingId,
            "companion" to review.companion,
            "reviewer" to review.reviewer,
            "reviewerUid" to uid,
            "stars" to review.stars.coerceIn(1, 5),
            "comment" to review.comment,
            "createdAt" to review.createdAt
        )
        db.collection("reviews").document(review.bookingId).set(data)
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it) }
    }

    fun loadReviews(
        onComplete: (List<CareReview>, Exception?) -> Unit
    ) {
        db.collection("reviews").get()
            .addOnSuccessListener { result ->
                val reviews = result.documents.mapNotNull { document ->
                    val data = document.data ?: return@mapNotNull null
                    CareReview(
                        id = document.id,
                        bookingId = data["bookingId"] as? String ?: document.id,
                        companion = data["companion"] as? String ?: "",
                        reviewer = data["reviewer"] as? String ?: "CareBuddy user",
                        stars = (data["stars"] as? Number)?.toInt() ?: return@mapNotNull null,
                        comment = data["comment"] as? String ?: "",
                        createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                        reviewerUid = data["reviewerUid"] as? String ?: ""
                    )
                }.sortedByDescending { it.createdAt }
                onComplete(reviews, null)
            }
            .addOnFailureListener { onComplete(emptyList(), it) }
    }

    fun loadReviewsForUser(
        uid: String,
        onComplete: (List<CareReview>, Exception?) -> Unit
    ) {
        if (uid.isBlank()) {
            onComplete(emptyList(), IllegalStateException("Sign in to view your reviews."))
            return
        }
        db.collection("reviews").whereEqualTo("reviewerUid", uid).get()
            .addOnSuccessListener { result ->
                val reviews = result.documents.mapNotNull { document ->
                    val data = document.data ?: return@mapNotNull null
                    CareReview(
                        id = document.id,
                        bookingId = data["bookingId"] as? String ?: document.id,
                        companion = data["companion"] as? String ?: "",
                        reviewer = data["reviewer"] as? String ?: "CareBuddy user",
                        stars = (data["stars"] as? Number)?.toInt() ?: return@mapNotNull null,
                        comment = data["comment"] as? String ?: "",
                        createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                        reviewerUid = data["reviewerUid"] as? String ?: uid
                    )
                }.sortedByDescending { it.createdAt }
                onComplete(reviews, null)
            }
            .addOnFailureListener { onComplete(emptyList(), it) }
    }

    // ===================================================================
    // ANNOUNCEMENTS
    // ===================================================================

    data class Announcement(
        val id: String,
        val title: String,
        val message: String,
        val sentBy: String,
        val createdAt: Long
    )

    fun createAnnouncement(
        title: String,
        message: String,
        sentBy: String,
        onComplete: (Exception?) -> Unit
    ) {
        val data = hashMapOf<String, Any>(
            "title" to title,
            "message" to message,
            "sentBy" to sentBy,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("announcements").add(data)
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it) }
    }

    fun loadAnnouncements(
        onComplete: (List<Announcement>, Exception?) -> Unit
    ) {
        db.collection("announcements")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { result ->
                val announcements = result.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    Announcement(
                        id = doc.id,
                        title = data["title"] as? String ?: "",
                        message = data["message"] as? String ?: "",
                        sentBy = data["sentBy"] as? String ?: "Admin",
                        createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.seconds?.times(1000)
                            ?: System.currentTimeMillis()
                    )
                }
                onComplete(announcements, null)
            }
            .addOnFailureListener { onComplete(emptyList(), it) }
    }

    // ===================================================================
    // PROFILE PHOTO (Base64 in Firestore)
    // ===================================================================

    fun updateProfilePhoto(
        uid: String,
        photoBase64: String,
        onComplete: (Exception?) -> Unit = {}
    ) {
        db.collection("users").document(uid)
            .update("photoBase64", photoBase64)
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it) }
    }

    fun loadProfilePhoto(
        uid: String,
        onComplete: (String?, Exception?) -> Unit
    ) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                onComplete(doc.getString("photoBase64"), null)
            }
            .addOnFailureListener { onComplete(null, it) }
    }

    // ===================================================================
    // AUTO-SYNC — call from any activity's onStart()
    // ===================================================================
    /**
     * Starts a Firestore listener that keeps BookingStore fresh.
     * Returns a ListenerRegistration — MUST be removed in onStop().
     */
    fun autoSyncBookings(
        context: Context,
        onUpdate: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ): ListenerRegistration? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        val uid = user.uid
        val prefs = context.getSharedPreferences("carebuddy_demo", Context.MODE_PRIVATE)
        val role = prefs.getString("current_role", "elderly").orEmpty()

        return listenBookings(
            context = context,
            role = role,
            uid = uid,
            onChanged = {
                onUpdate?.invoke()
            },
            onError = { error ->
                android.util.Log.w("autoSyncBookings", "Sync failed: ${error.message}")
                onError?.invoke(error)
            }
        )
    }

    // ===================================================================
    // USER PROFILES
    // ===================================================================

    fun loadUserProfile(
        uid: String,
        onComplete: (Map<String, Any>?, Exception?) -> Unit
    ) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document -> onComplete(document.data, null) }
            .addOnFailureListener { onComplete(null, it) }
    }

    /**
     * Loads the companion-specific profile document (companionProfiles collection).
     * Contains bio, skills, hourlyRate, available, verificationStatus.
     */
    fun loadCompanionProfile(
        uid: String,
        onComplete: (Map<String, Any>?, Exception?) -> Unit
    ) {
        db.collection("companionProfiles").document(uid).get()
            .addOnSuccessListener { document -> onComplete(document.data, null) }
            .addOnFailureListener { onComplete(null, it) }
    }

    fun loadUsersByRole(
        role: String,
        onComplete: (List<Map<String, Any>>, Exception?) -> Unit
    ) {
        db.collection("users").get()
            .addOnSuccessListener { result ->
                onComplete(result.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    if (!(data["role"] as? String).equals(role, ignoreCase = true)) return@mapNotNull null
                    data.toMutableMap().apply { put("uid", doc.id) }
                }, null)
            }
            .addOnFailureListener { onComplete(emptyList(), it) }
    }

    fun loadAllUsers(
        onComplete: (List<Map<String, Any>>, Exception?) -> Unit
    ) {
        db.collection("users").get()
            .addOnSuccessListener { result ->
                onComplete(
                    result.documents.mapNotNull { doc ->
                        doc.data?.toMutableMap()?.apply { put("uid", doc.id) }
                    },
                    null
                )
            }
            .addOnFailureListener { onComplete(emptyList(), it) }
    }

    fun createAccountProfile(
        uid: String,
        email: String,
        name: String,
        phone: String,
        role: String,
        gender: String = "Prefer not to say",
        onComplete: (Exception?) -> Unit
    ) {
        val userData = hashMapOf<String, Any>(
            "uid" to uid,
            "email" to email,
            "displayName" to name,
            "phone" to phone,
            "gender" to gender,
            "role" to role,
            "status" to "Active",
            "verificationStatus" to "pending",
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).set(userData)
            .addOnSuccessListener {
                if (role != "companion") {
                    onComplete(null)
                    return@addOnSuccessListener
                }
                val companionData = hashMapOf<String, Any>(
                    "uid" to uid,
                    "displayName" to name,
                    "gender" to gender,
                    "location" to "Kuala Lumpur",
                    "hourlyRate" to "RM 30/hour",
                    "bio" to "CareBuddy companion",
                    "skills" to listOf("Companionship", "Errands"),
                    "accountStatus" to "Active",
                    "available" to false,
                    "verificationStatus" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection("companionProfiles").document(uid).set(companionData)
                    .addOnSuccessListener { onComplete(null) }
                    .addOnFailureListener { onComplete(it) }
            }
            .addOnFailureListener { onComplete(it) }
    }

    fun updateUserName(
        uid: String,
        name: String,
        role: String,
        onComplete: (Exception?) -> Unit = {}
    ) {
        val trimmed = name.trim()
        db.collection("users").document(uid).update("displayName", trimmed)
            .addOnSuccessListener {
                if (role == "companion") {
                    db.collection("companionProfiles").document(uid)
                        .update("displayName", trimmed)
                        .addOnSuccessListener { onComplete(null) }
                        .addOnFailureListener { onComplete(it) }
                } else {
                    onComplete(null)
                }
            }
            .addOnFailureListener { onComplete(it) }
    }

    fun saveEmergencyContact(
        uid: String,
        name: String,
        phone: String,
        onComplete: (Exception?) -> Unit = {}
    ) {
        db.collection("users").document(uid).update(
            mapOf(
                "emergencyContactName" to name.trim(),
                "emergencyContactPhone" to phone.trim()
            )
        ).addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it) }
    }

    fun setUserStatus(
        uid: String,
        status: String,
        onComplete: (Exception?) -> Unit = {}
    ) {
        val userRef = db.collection("users").document(uid)
        userRef.update("status", status)
            .addOnSuccessListener {
                userRef.get().addOnSuccessListener { user ->
                    if (!user.getString("role").equals("companion", true)) {
                        onComplete(null)
                    } else {
                        db.collection("companionProfiles").document(uid)
                            .update("accountStatus", status)
                            .addOnSuccessListener { onComplete(null) }
                            .addOnFailureListener { onComplete(it) }
                    }
                }.addOnFailureListener { onComplete(it) }
            }
            .addOnFailureListener { onComplete(it) }
    }

    // ===================================================================
    // COMPANIONS
    // ===================================================================

    fun loadAvailableCompanions(
        onComplete: (List<CareCompanionProfile>, Exception?) -> Unit
    ) {
        db.collection("companionProfiles")
            .whereEqualTo("verificationStatus", "approved")
            .get()
            .addOnSuccessListener { result ->
                val profiles = result.documents.mapNotNull { document ->
                    val data = document.data ?: return@mapNotNull null
                    CareCompanionProfile(
                        uid = document.id,
                        displayName = data["displayName"] as? String ?: return@mapNotNull null,
                        location = data["location"] as? String ?: "Kuala Lumpur",
                        hourlyRate = data["hourlyRate"] as? String ?: "RM 30/hour",
                        bio = data["bio"] as? String ?: "CareBuddy companion",
                        skills = (data["skills"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                        available = data["available"] as? Boolean ?: false,
                        gender = data["gender"] as? String ?: "",
                        yearsExperience = (data["yearsExperience"] as? Number)?.toInt() ?: 0
                    ).takeUnless { (data["accountStatus"] as? String).equals("Suspended", true) }
                }
                onComplete(profiles, null)
            }
            .addOnFailureListener { onComplete(emptyList(), it) }
    }

    fun setCompanionAvailability(
        uid: String,
        available: Boolean,
        onComplete: (Exception?) -> Unit = {}
    ) {
        db.collection("companionProfiles").document(uid).update("available", available)
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it) }
    }

    // ===================================================================
    // BOOKINGS
    // ===================================================================

    fun saveBooking(
        booking: CareBooking,
        onComplete: (Exception?) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onComplete(IllegalStateException("Sign in again before sending a booking request."))
            return
        }

        val lookup: ((String?, Exception?) -> Unit) = { companionUid, lookupError ->
            if (lookupError != null) {
                onComplete(lookupError)
            } else {
                val linkedBooking = booking.copy(
                    ownerUid = user.uid,
                    companionUid = companionUid.orEmpty()
                )
                val data = hashMapOf<String, Any>(
                    "id" to linkedBooking.id,
                    "ownerUid" to user.uid,
                    "ownerEmail" to linkedBooking.ownerEmail,
                    "ownerName" to linkedBooking.ownerName,
                    "companionUid" to linkedBooking.companionUid,
                    "companion" to linkedBooking.companion,
                    "rate" to linkedBooking.rate,
                    "date" to linkedBooking.date,
                    "time" to linkedBooking.time,
                    "payment" to linkedBooking.payment,
                    "status" to linkedBooking.status,
                    "activity" to linkedBooking.activity,
                    "location" to linkedBooking.location,
                    "notes" to linkedBooking.notes,
                    "durationHours" to linkedBooking.durationHours,
                    "estimatedTotal" to linkedBooking.estimatedTotal,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection("bookings").document(linkedBooking.id).set(data)
                    .addOnSuccessListener { onComplete(null) }
                    .addOnFailureListener { onComplete(it) }
            }
        }

        if (booking.companionUid.isBlank()) {
            findCompanionUid(booking.companion, lookup)
        } else {
            db.collection("companionProfiles").document(booking.companionUid).get()
                .addOnSuccessListener { profile ->
                    if (profile.getString("verificationStatus") != "approved" ||
                        profile.getString("accountStatus").equals("Suspended", true) ||
                        profile.getBoolean("available") != true
                    ) {
                        lookup(
                            null,
                            IllegalStateException("This companion is no longer verified and available.")
                        )
                    } else {
                        lookup(profile.id, null)
                    }
                }
                .addOnFailureListener { lookup(null, it) }
        }
    }

    fun listenBookings(
        context: Context,
        role: String,
        uid: String,
        onChanged: (List<CareBooking>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val collection = db.collection("bookings")
        val query = when (role) {
            "admin" -> collection
            "companion" -> collection.whereEqualTo("companionUid", uid)
            else -> collection.whereEqualTo("ownerUid", uid)
        }
        return query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            val bookings = snapshot?.documents.orEmpty().mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                CareBooking(
                    id = data["id"] as? String ?: doc.id,
                    companion = data["companion"] as? String ?: "",
                    rate = data["rate"] as? String ?: "",
                    date = data["date"] as? String ?: "",
                    time = data["time"] as? String ?: "",
                    payment = data["payment"] as? String ?: "",
                    status = data["status"] as? String ?: "Pending",
                    activity = data["activity"] as? String ?: "",
                    location = data["location"] as? String ?: "",
                    notes = data["notes"] as? String ?: "",
                    ownerEmail = data["ownerEmail"] as? String ?: "",
                    ownerName = data["ownerName"] as? String ?: "",
                    durationHours = (data["durationHours"] as? Number)?.toInt() ?: 1,
                    estimatedTotal = (data["estimatedTotal"] as? Number)?.toDouble() ?: 0.0,
                    ownerUid = data["ownerUid"] as? String ?: "",
                    companionUid = data["companionUid"] as? String ?: ""
                )
            }.sortedByDescending { it.id }
            if (BookingStore.all(context) != bookings) {
                BookingStore.replace(context, bookings)
                onChanged(bookings)
            }
        }
    }

    fun updateBookingStatus(
        id: String,
        status: String,
        onComplete: (Exception?) -> Unit = {}
    ) {
        db.collection("bookings").document(id)
            .update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it) }
    }

    fun updateBookingPayment(
        id: String,
        paymentMethod: String,
        onComplete: (Exception?) -> Unit = {}
    ) {
        db.collection("bookings").document(id)
            .update("payment", paymentMethod)
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it) }
    }

    fun loadBooking(
        id: String,
        onComplete: (CareBooking?, Exception?) -> Unit
    ) {
        if (id.isBlank()) {
            onComplete(null, IllegalArgumentException("Booking ID is missing."))
            return
        }
        db.collection("bookings").document(id).get()
            .addOnSuccessListener { doc ->
                val data = doc.data
                if (!doc.exists() || data == null) {
                    onComplete(null, null)
                    return@addOnSuccessListener
                }
                onComplete(
                    CareBooking(
                        id = data["id"] as? String ?: doc.id,
                        companion = data["companion"] as? String ?: "",
                        rate = data["rate"] as? String ?: "",
                        date = data["date"] as? String ?: "",
                        time = data["time"] as? String ?: "",
                        payment = data["payment"] as? String ?: "",
                        status = data["status"] as? String ?: "Pending",
                        activity = data["activity"] as? String ?: "",
                        location = data["location"] as? String ?: "",
                        notes = data["notes"] as? String ?: "",
                        ownerEmail = data["ownerEmail"] as? String ?: "",
                        ownerName = data["ownerName"] as? String ?: "",
                        durationHours = (data["durationHours"] as? Number)?.toInt() ?: 1,
                        estimatedTotal = (data["estimatedTotal"] as? Number)?.toDouble() ?: 0.0,
                        ownerUid = data["ownerUid"] as? String ?: "",
                        companionUid = data["companionUid"] as? String ?: ""
                    ),
                    null
                )
            }
            .addOnFailureListener { onComplete(null, it) }
    }

    // ===================================================================
    // NOTIFICATIONS
    // ===================================================================

    fun createNotification(
        userId: String,
        type: String,
        title: String,
        message: String,
        bookingId: String,
        onComplete: (Exception?) -> Unit = {}
    ) {
        val senderUid = FirebaseAuth.getInstance().currentUser?.uid
        if (senderUid.isNullOrBlank()) {
            onComplete(IllegalStateException("Sign in to send a notification."))
            return
        }
        val data = hashMapOf<String, Any>(
            "userId" to userId,
            "createdBy" to senderUid,
            "bookingId" to bookingId,
            "type" to type,
            "title" to title,
            "message" to message,
            "read" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("notifications").add(data)
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it) }
    }

    fun loadNotifications(
        userId: String,
        onComplete: (List<Map<String, Any>>, Exception?) -> Unit
    ) {
        db.collection("notifications")
            .whereEqualTo("userId", userId)
            .limit(50)
            .get()
            .addOnSuccessListener { result ->
                val items = result.documents.mapNotNull { doc ->
                    doc.data?.toMutableMap()?.apply { put("id", doc.id) }
                }.sortedByDescending { item ->
                    (item["createdAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0L
                }
                onComplete(items, null)
            }
            .addOnFailureListener { onComplete(emptyList(), it) }
    }

    // ===================================================================
    // PRIVATE HELPERS
    // ===================================================================

    private fun findCompanionUid(
        name: String,
        onComplete: (String?, Exception?) -> Unit
    ) {
        db.collection("companionProfiles")
            .whereEqualTo("displayName", name)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                val profile = result.documents.firstOrNull()
                if (profile == null || profile.getString("verificationStatus") != "approved") {
                    onComplete(
                        null,
                        IllegalStateException("This companion is not yet verified in the shared directory.")
                    )
                } else {
                    onComplete(profile.id, null)
                }
            }
            .addOnFailureListener { onComplete(null, it) }
    }
}
