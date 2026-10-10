package com.example.carebuddy

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class BookingAdapter(
    private val bookings: List<Booking>,
    private val onItemClick: (Booking) -> Unit
) : RecyclerView.Adapter<BookingAdapter.BookingViewHolder>() {

    inner class BookingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.ivActivityIcon)
        val tvActivity: TextView = itemView.findViewById(R.id.tvActivity)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvDateTime: TextView = itemView.findViewById(R.id.tvDateTime)
        val tvCompanion: TextView = itemView.findViewById(R.id.tvCompanion)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking, parent, false)
        return BookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]

        holder.tvActivity.text = booking.activity
        holder.tvStatus.text = booking.status
        holder.tvDateTime.text = "📅 ${booking.dateTime}"
        holder.tvCompanion.text = "🧑 ${booking.companionName}"
        holder.ivIcon.setImageResource(booking.iconResId)

        // Status badge colors
        val (bgRes, textColor) = when (booking.status) {
            "Upcoming" -> R.drawable.bg_badge_available to 0xFF2E7D32.toInt()    // green
            "Ongoing", "On The Way", "In Progress" -> R.drawable.bg_badge_ongoing to 0xFF1976D2.toInt() // blue
            "Completed" -> R.drawable.bg_badge_completed to 0xFF616161.toInt()   // gray
            "Cancelled" -> R.drawable.bg_badge_cancelled to 0xFFC62828.toInt()   // red
            else -> R.drawable.bg_chip to 0xFF888888.toInt()
        }
        holder.tvStatus.setBackgroundResource(bgRes)
        holder.tvStatus.setTextColor(textColor)

        holder.itemView.setOnClickListener { onItemClick(booking) }
    }

    override fun getItemCount(): Int = bookings.size
}
