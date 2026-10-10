package com.example.carebuddy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EarningAdapter(
    private val items: List<CareBooking>
) : RecyclerView.Adapter<EarningAdapter.EarningViewHolder>() {

    inner class EarningViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvActivity: TextView = itemView.findViewById(R.id.tvActivity)
        val tvCustomer: TextView = itemView.findViewById(R.id.tvCustomer)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EarningViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_earning, parent, false)
        return EarningViewHolder(view)
    }

    override fun onBindViewHolder(holder: EarningViewHolder, position: Int) {
        val booking = items[position]

        holder.tvActivity.text = booking.activity.ifBlank { "Service" }
        holder.tvCustomer.text = "👤  " + booking.ownerName
            .ifBlank { booking.ownerEmail.ifBlank { "CareBuddy user" } }
        holder.tvDate.text = "📅  ${booking.date}"

        val amount = if (booking.estimatedTotal > 0) {
            booking.estimatedTotal
        } else {
            BookingPricing.total(booking)
        }
        holder.tvAmount.text = "+" + BookingPricing.format(amount)
    }

    override fun getItemCount(): Int = items.size
}