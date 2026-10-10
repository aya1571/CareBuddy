package com.example.carebuddy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

data class AdminCompanionItem(
    val uid: String,
    val name: String,
    val email: String,
    val phone: String,
    val role: String,
    val status: String,
    val verificationStatus: String,
    val location: String = "Kuala Lumpur",
    val hourlyRate: String = "RM 30/hour",
    val bio: String = "",
    val available: Boolean = false,
    val rating: Double = 0.0,
    val reviewCount: Int = 0
)

class CompanionManagementAdapter(
    private val companions: List<AdminCompanionItem>,
    private val onAction: (AdminCompanionItem) -> Unit
) : RecyclerView.Adapter<CompanionManagementAdapter.CompanionViewHolder>() {

    inner class CompanionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAvatar: TextView = itemView.findViewById(R.id.tvAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvRate: TextView = itemView.findViewById(R.id.tvRate)
        val tvStatusBadge: TextView = itemView.findViewById(R.id.tvStatusBadge)
        val tvVerified: TextView = itemView.findViewById(R.id.tvVerified)
        val tvRating: TextView = itemView.findViewById(R.id.tvRating)
        val tvAvailability: TextView = itemView.findViewById(R.id.tvAvailability)
        val btnAction: Button = itemView.findViewById(R.id.btnAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompanionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_companion, parent, false)
        return CompanionViewHolder(view)
    }

    override fun onBindViewHolder(holder: CompanionViewHolder, position: Int) {
        val companion = companions[position]

        // Avatar
        holder.tvAvatar.text = companion.name.trim().firstOrNull()?.uppercase() ?: "?"

        // Name + Rate + Location
        holder.tvName.text = companion.name.ifBlank { "Unnamed companion" }
        holder.tvRate.text = "${companion.hourlyRate} • ${companion.location}"

        // Status badge
        when (companion.status) {
            "Active" -> {
                holder.tvStatusBadge.text = "Active"
                holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_available)
                holder.tvStatusBadge.setTextColor(0xFF2E7D32.toInt())
            }
            "Suspended" -> {
                holder.tvStatusBadge.text = "Suspended"
                holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_cancelled)
                holder.tvStatusBadge.setTextColor(0xFFC62828.toInt())
            }
            else -> {
                holder.tvStatusBadge.text = companion.status
                holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_chip)
                holder.tvStatusBadge.setTextColor(0xFF888888.toInt())
            }
        }

        // Verified text
        when (companion.verificationStatus) {
            "approved" -> {
                holder.tvVerified.text = "✅ Verified"
                holder.tvVerified.setTextColor(0xFF2E7D32.toInt())
            }
            "rejected" -> {
                holder.tvVerified.text = "❌ Rejected"
                holder.tvVerified.setTextColor(0xFFC62828.toInt())
            }
            else -> {
                holder.tvVerified.text = "⏳ Pending"
                holder.tvVerified.setTextColor(0xFFF57C00.toInt())
            }
        }

        // Rating
        if (companion.reviewCount > 0) {
            holder.tvRating.text = "⭐ %.1f (%d)".format(companion.rating, companion.reviewCount)
        } else {
            holder.tvRating.text = "No ratings yet"
        }

        // Availability
        if (companion.available) {
            holder.tvAvailability.text = "🟢 Available now"
            holder.tvAvailability.setTextColor(0xFF2E7D32.toInt())
        } else {
            holder.tvAvailability.text = "🔴 Currently unavailable"
            holder.tvAvailability.setTextColor(0xFF888888.toInt())
        }

        // Action button
        if (companion.status == "Active") {
            holder.btnAction.text = "Suspend"
            holder.btnAction.background = ContextCompat.getDrawable(
                holder.itemView.context, R.drawable.bg_cancel_button
            )
            holder.btnAction.setTextColor(0xFFC62828.toInt())
        } else {
            holder.btnAction.text = "Restore"
            holder.btnAction.background = ContextCompat.getDrawable(
                holder.itemView.context, R.drawable.bg_call_button
            )
            holder.btnAction.setTextColor(0xFFFFFFFF.toInt())
        }

        holder.btnAction.setOnClickListener { onAction(companion) }
    }

    override fun getItemCount(): Int = companions.size
}