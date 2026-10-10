package com.example.carebuddy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CareReview(
    val id: String,
    val bookingId: String,
    val companion: String,
    val reviewer: String,
    val stars: Int,
    val comment: String,
    val createdAt: Long,
    val reviewerUid: String = ""
)

/** On-device demo review storage; replace with Firestore when the project is configured. */
object ReviewStore {
    private const val PREFS = "carebuddy_demo"
    private const val KEY = "reviews"

    fun all(context: Context): List<CareReview> {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(value) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            CareReview(
                item.optString("id"), item.optString("bookingId"), item.optString("companion"),
                item.optString("reviewer"), item.optInt("stars"), item.optString("comment"),
                item.optLong("createdAt"), item.optString("reviewerUid")
            )
        }.sortedByDescending { it.createdAt }
    }

    fun forBooking(context: Context, bookingId: String): CareReview? =
        all(context).firstOrNull { it.bookingId == bookingId }

    fun add(context: Context, review: CareReview) {
        if (forBooking(context, review.bookingId) != null) return
        val array = JSONArray()
        (all(context).reversed() + review).forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id); put("bookingId", item.bookingId); put("companion", item.companion)
                put("reviewer", item.reviewer); put("stars", item.stars); put("comment", item.comment)
                put("createdAt", item.createdAt); put("reviewerUid", item.reviewerUid)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
