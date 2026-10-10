package com.example.carebuddy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PaymentManagementAdapter(
    private val payments: List<AdminPaymentItem>
) : RecyclerView.Adapter<PaymentManagementAdapter.PaymentViewHolder>() {

    inner class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconCircle: FrameLayout = itemView.findViewById(R.id.iconCircle)
        val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        val tvBookingId: TextView = itemView.findViewById(R.id.tvBookingId)
        val tvUsers: TextView = itemView.findViewById(R.id.tvUsers)
        val tvMethod: TextView = itemView.findViewById(R.id.tvMethod)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_payment, parent, false)
        return PaymentViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        val payment = payments[position]

        holder.tvBookingId.text = payment.bookingId
        holder.tvUsers.text = "${payment.ownerName} → ${payment.companionName}"
        holder.tvMethod.text = payment.method.ifBlank { "Not set" }
        holder.tvDate.text = payment.date
        holder.tvAmount.text = BookingPricing.format(payment.amount)

        // Icon per method
        when (payment.method.lowercase()) {
            "card" -> {
                holder.iconCircle.setBackgroundResource(R.drawable.bg_icon_blue)
                holder.ivIcon.setImageResource(android.R.drawable.ic_menu_save)
                holder.ivIcon.setColorFilter(0xFF1976D2.toInt())
            }
            "online banking" -> {
                holder.iconCircle.setBackgroundResource(R.drawable.bg_icon_green)
                holder.ivIcon.setImageResource(android.R.drawable.ic_menu_agenda)
                holder.ivIcon.setColorFilter(0xFF2E7D32.toInt())
            }
            "touch 'n go" -> {
                holder.iconCircle.setBackgroundResource(R.drawable.bg_icon_purple)
                holder.ivIcon.setImageResource(android.R.drawable.ic_menu_call)
                holder.ivIcon.setColorFilter(0xFF7B1FA2.toInt())
            }
            "cash on arrival" -> {
                holder.iconCircle.setBackgroundResource(R.drawable.bg_icon_orange)
                holder.ivIcon.setImageResource(android.R.drawable.ic_menu_myplaces)
                holder.ivIcon.setColorFilter(0xFFF57C00.toInt())
            }
            else -> {
                holder.iconCircle.setBackgroundResource(R.drawable.bg_icon_blue)
                holder.ivIcon.setImageResource(android.R.drawable.ic_menu_save)
                holder.ivIcon.setColorFilter(0xFF1976D2.toInt())
            }
        }
    }

    override fun getItemCount(): Int = payments.size
}

data class AdminPaymentItem(
    val bookingId: String,
    val ownerName: String,
    val companionName: String,
    val method: String,
    val date: String,
    val amount: Double
)