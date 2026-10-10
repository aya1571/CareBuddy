package com.example.carebuddy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AdminBookingAdapter(
    private val bookings: List<CareBooking>,
    private val onViewDetails: (CareBooking) -> Unit,
    private val onCancel: (CareBooking) -> Unit
) : RecyclerView.Adapter<AdminBookingAdapter.BookingViewHolder>() {

    inner class BookingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvActivity: TextView = itemView.findViewById(R.id.tvActivity)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvDateTime: TextView = itemView.findViewById(R.id.tvDateTime)
        val tvOwner: TextView = itemView.findViewById(R.id.tvOwner)
        val tvCompanion: TextView = itemView.findViewById(R.id.tvCompanion)
        val tvLocation: TextView = itemView.findViewById(R.id.tvLocation)
        val tvTotal: TextView = itemView.findViewById(R.id.tvTotal)
        val actionRow: LinearLayout = itemView.findViewById(R.id.actionRow)
        val btnViewDetails: Button = itemView.findViewById(R.id.btnViewDetails)
        val btnCancel: Button = itemView.findViewById(R.id.btnCancel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_booking, parent, false)
        return BookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]

        holder.tvActivity.text = booking.activity.ifBlank { "Booking" }
        holder.tvDateTime.text = "📅 ${booking.date} • ${booking.time}"

        val ownerName = booking.ownerName.ifBlank { booking.ownerEmail.ifBlank { "CareBuddy user" } }
        holder.tvOwner.text = "👤 Customer: $ownerName"
        holder.tvCompanion.text = "🧑 Companion: ${booking.companion.ifBlank { "Not assigned" }}"
        holder.tvLocation.text = "📍 ${booking.location.ifBlank { "Not specified" }}"

        val total = if (booking.estimatedTotal > 0) booking.estimatedTotal
        else BookingPricing.total(booking)
        holder.tvTotal.text = BookingPricing.format(total)

        // Status badge
        val (bgRes, textColor) = when (booking.status) {
            "Pending"   -> R.drawable.bg_badge_available to 0xFF2E7D32.toInt()
            "Accepted", "On The Way", "In Progress" -> R.drawable.bg_badge_ongoing to 0xFF1976D2.toInt()
            "Completed" -> R.drawable.bg_badge_completed to 0xFF616161.toInt()
            "Cancelled" -> R.drawable.bg_badge_cancelled to 0xFFC62828.toInt()
            "Declined"  -> R.drawable.bg_badge_cancelled to 0xFFC62828.toInt()
            else        -> R.drawable.bg_chip            to 0xFF888888.toInt()
        }
        holder.tvStatus.text = booking.status
        holder.tvStatus.setBackgroundResource(bgRes)
        holder.tvStatus.setTextColor(textColor)

        // Hide "Cancel" for terminal states
        val isCancellable = booking.status !in listOf("Completed", "Cancelled", "Declined")
        holder.btnCancel.visibility = if (isCancellable) View.VISIBLE else View.GONE

        holder.btnViewDetails.setOnClickListener { onViewDetails(booking) }
        holder.btnCancel.setOnClickListener { onCancel(booking) }
    }

    override fun getItemCount(): Int = bookings.size
}
