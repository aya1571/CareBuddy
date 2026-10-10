package com.example.carebuddy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NotificationAdapter(
    private val items: List<NotificationItem>,
    private val onItemClick: (NotificationItem) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconCircle: FrameLayout = itemView.findViewById(R.id.iconCircle)
        val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val item = items[position]

        holder.tvTitle.text = item.title
        holder.tvMessage.text = item.message
        holder.tvTime.text = item.timestamp

        // Set icon + circle color based on type
        when (item.type) {
            "booking" -> {
                holder.iconCircle.setBackgroundResource(R.drawable.bg_icon_green)
                holder.ivIcon.setImageResource(android.R.drawable.checkbox_on_background)
                holder.ivIcon.setColorFilter(0xFF2E7D32.toInt())
            }
            "payment" -> {
                holder.iconCircle.setBackgroundResource(R.drawable.bg_icon_blue)
                holder.ivIcon.setImageResource(android.R.drawable.ic_menu_save)
                holder.ivIcon.setColorFilter(0xFF1976D2.toInt())
            }
            "reminder" -> {
                holder.iconCircle.setBackgroundResource(R.drawable.bg_icon_orange)
                holder.ivIcon.setImageResource(android.R.drawable.ic_popup_reminder)
                holder.ivIcon.setColorFilter(0xFFF57C00.toInt())
            }
            "message" -> {
                holder.iconCircle.setBackgroundResource(R.drawable.bg_icon_purple)
                holder.ivIcon.setImageResource(android.R.drawable.ic_dialog_email)
                holder.ivIcon.setColorFilter(0xFF7B1FA2.toInt())
            }
            else -> {
                holder.iconCircle.setBackgroundResource(R.drawable.bg_icon_blue)
                holder.ivIcon.setImageResource(android.R.drawable.ic_popup_reminder)
                holder.ivIcon.setColorFilter(0xFF1976D2.toInt())
            }
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size
}