package com.example.carebuddy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class BookingRequestAdapter(
    private val requests: List<CareBooking>,
    private val showActions: Boolean,
    private val onAccept: (CareBooking) -> Unit,
    private val onDecline: (CareBooking) -> Unit,
    private val onDetails: (CareBooking) -> Unit
) : RecyclerView.Adapter<BookingRequestAdapter.RequestViewHolder>() {

    inner class RequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvActivity: TextView = itemView.findViewById(R.id.tvActivity)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvCustomer: TextView = itemView.findViewById(R.id.tvCustomer)
        val tvDateTime: TextView = itemView.findViewById(R.id.tvDateTime)
        val tvLocation: TextView = itemView.findViewById(R.id.tvLocation)
        val tvPayment: TextView = itemView.findViewById(R.id.tvPayment)
        val actionRow: LinearLayout = itemView.findViewById(R.id.actionRow)
        val btnAccept: Button = itemView.findViewById(R.id.btnAccept)
        val btnDecline: Button = itemView.findViewById(R.id.btnDecline)
        val tvViewDetails: TextView = itemView.findViewById(R.id.tvViewDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking_request, parent, false)
        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val request = requests[position]

        // Activity + customer
        holder.tvActivity.text = request.activity.ifBlank { "Booking" }
        holder.tvCustomer.text = "👤  " + request.ownerName
            .ifBlank { request.ownerEmail.ifBlank { "CareBuddy user" } }

        // Date/Time
        holder.tvDateTime.text = "📅  ${request.date} • ${request.time}"

        // Location
        holder.tvLocation.text = "📍  ${request.location}"

        // Payment (from estimatedTotal)
        holder.tvPayment.text = BookingPricing.format(
            request.estimatedTotal.takeIf { it > 0 } ?: BookingPricing.total(request)
        )

        // Status badge
        val (bgRes, textColor, label) = when (request.status) {
            "Pending"  -> Triple(R.drawable.bg_badge_available, 0xFF2E7D32.toInt(), "New")
            "Accepted" -> Triple(R.drawable.bg_badge_ongoing,   0xFF1976D2.toInt(), "Accepted")
            "On The Way" -> Triple(R.drawable.bg_badge_ongoing, 0xFF1976D2.toInt(), "On The Way")
            "Completed"-> Triple(R.drawable.bg_badge_completed, 0xFF616161.toInt(), "Completed")
            "Declined" -> Triple(R.drawable.bg_badge_cancelled, 0xFFC62828.toInt(), "Declined")
            "Cancelled"-> Triple(R.drawable.bg_badge_cancelled, 0xFFC62828.toInt(), "Cancelled")
            else       -> Triple(R.drawable.bg_chip,            0xFF888888.toInt(), request.status)
        }
        holder.tvStatus.text = label
        holder.tvStatus.setBackgroundResource(bgRes)
        holder.tvStatus.setTextColor(textColor)

        // Show or hide the Accept/Decline action row
        if (showActions && request.status == "Pending") {
            holder.actionRow.visibility = View.VISIBLE
            holder.btnAccept.setOnClickListener { onAccept(request) }
            holder.btnDecline.setOnClickListener { onDecline(request) }
        } else {
            holder.actionRow.visibility = View.GONE
        }

        // View full details
        holder.tvViewDetails.setOnClickListener { onDetails(request) }
    }

    override fun getItemCount(): Int = requests.size
}
