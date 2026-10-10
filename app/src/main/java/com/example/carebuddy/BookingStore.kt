package com.example.carebuddy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CareBooking(
    val id: String,
    val companion: String,
    val rate: String,
    val date: String,
    val time: String,
    val payment: String,
    val status: String,
    val activity: String = "",
    val location: String = "",
    val notes: String = "",
    val ownerEmail: String = "",
    val ownerName: String = "",
    val durationHours: Int = 1,
    val estimatedTotal: Double = 0.0,
    val ownerUid: String = "",
    val companionUid: String = ""
)

/** Small on-device demo store. Replace with an authenticated backend before production use. */
object BookingStore {
    private const val PREFS = "carebuddy_demo"
    private const val BOOKINGS = "bookings"

    fun all(context: Context): List<CareBooking> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(BOOKINGS, "[]") ?: "[]"
        val array = runCatching { JSONArray(json) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            CareBooking(
                item.optString("id"), item.optString("companion"), item.optString("rate"),
                item.optString("date"), item.optString("time"), item.optString("payment"),
                item.optString("status", "Confirmed"), item.optString("activity"),
                item.optString("location"), item.optString("notes"),
                item.optString("ownerEmail"), item.optString("ownerName"),
                item.optInt("durationHours", 1), item.optDouble("estimatedTotal", 0.0),
                item.optString("ownerUid"), item.optString("companionUid")
            )
        }.reversed()
    }

    fun find(context: Context, id: String): CareBooking? =
        all(context).firstOrNull { it.id == id }

    fun add(context: Context, booking: CareBooking) {
        val current = all(context).reversed()
        persist(context, current + booking)
    }

    fun updateStatus(context: Context, id: String, status: String) {
        val chronological = all(context).reversed()
        persist(context, chronological.map { if (it.id == id) it.copy(status = status) else it })
    }

    fun updatePayment(context: Context, id: String, payment: String) {
        val chronological = all(context).reversed()
        persist(context, chronological.map { if (it.id == id) it.copy(payment = payment) else it })
    }

    fun replace(context: Context, bookings: List<CareBooking>) {
        persist(context, bookings.reversed())
    }

    private fun persist(context: Context, bookings: List<CareBooking>) {
        val array = JSONArray()
        bookings.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id); put("companion", item.companion); put("rate", item.rate)
                put("date", item.date); put("time", item.time); put("payment", item.payment)
                put("status", item.status); put("activity", item.activity)
                put("location", item.location); put("notes", item.notes)
                put("ownerEmail", item.ownerEmail); put("ownerName", item.ownerName)
                put("durationHours", item.durationHours); put("estimatedTotal", item.estimatedTotal)
                put("ownerUid", item.ownerUid); put("companionUid", item.companionUid)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(BOOKINGS, array.toString()).apply()
    }
}

object BookingPricing {
    fun hourlyRate(rate: String): Double = Regex("RM\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        .find(rate)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    fun total(booking: CareBooking): Double = booking.estimatedTotal.takeIf { it > 0.0 }
        ?: hourlyRate(booking.rate) * booking.durationHours.coerceAtLeast(1)

    fun format(amount: Double): String = "RM %.2f".format(Locale.US, amount)
}

object CompanionAvailability {
    fun key(name: String): String = name.trim().lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "_").trim('_')

    fun isAvailable(context: Context, name: String): Boolean {
        val id = key(name)
        if (id.isBlank()) return true
        return context.getSharedPreferences("carebuddy_demo", Context.MODE_PRIVATE)
            .getBoolean("available_$id", true)
    }

    fun isWithinSchedule(context: Context, name: String, date: String, time: String): Boolean {
        val id = key(name)
        if (!isAvailable(context, name)) return false
        val parsedDate = runCatching {
            SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { isLenient = false }.parse(date)
        }.getOrNull() ?: return false
        val parsedTime = runCatching {
            SimpleDateFormat("hh:mm a", Locale.US).apply { isLenient = false }.parse(time)
        }.getOrNull() ?: return false
        val day = SimpleDateFormat("EEEE", Locale.US).format(parsedDate).lowercase(Locale.ROOT)
        val prefs = context.getSharedPreferences("carebuddy_demo", Context.MODE_PRIVATE)
        val weekdayDefault = day != "saturday" && day != "sunday"
        if (!prefs.getBoolean("schedule_${id}_$day", weekdayDefault)) return false

        fun minutesAfterMidnight(value: Date): Int {
            val calendar = java.util.Calendar.getInstance().also { it.time = value }
            return calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
        }
        val start = runCatching {
            SimpleDateFormat("hh:mm a", Locale.US).parse(prefs.getString("schedule_start_$id", "09:00 AM"))
        }.getOrNull() ?: return false
        val end = runCatching {
            SimpleDateFormat("hh:mm a", Locale.US).parse(prefs.getString("schedule_end_$id", "05:00 PM"))
        }.getOrNull() ?: return false
        val selectedMinutes = minutesAfterMidnight(parsedTime)
        return selectedMinutes >= minutesAfterMidnight(start) && selectedMinutes <= minutesAfterMidnight(end)
    }
}
