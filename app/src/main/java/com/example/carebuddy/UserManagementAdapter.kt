package com.example.carebuddy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

data class AdminUserItem(
    val uid: String,
    val name: String,
    val email: String,
    val role: String,
    val status: String,
    val verificationStatus: String,
    val phone: String = ""
)

class UserManagementAdapter(
    private val users: List<AdminUserItem>,
    private val onAction: (AdminUserItem) -> Unit
) : RecyclerView.Adapter<UserManagementAdapter.UserViewHolder>() {

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAvatar: TextView = itemView.findViewById(R.id.tvAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
        val tvStatusBadge: TextView = itemView.findViewById(R.id.tvStatusBadge)
        val tvInfo: TextView = itemView.findViewById(R.id.tvInfo)
        val btnAction: Button = itemView.findViewById(R.id.btnAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]

        // Avatar: first letter of name
        holder.tvAvatar.text = user.name.trim().firstOrNull()?.uppercase() ?: "?"

        // Name + Email
        holder.tvName.text = user.name.ifBlank { "Unnamed user" }
        holder.tvEmail.text = user.email.ifBlank { "No email" }

        // Status badge
        when (user.status) {
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
                holder.tvStatusBadge.text = user.status
                holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_chip)
                holder.tvStatusBadge.setTextColor(0xFF888888.toInt())
            }
        }

        // Info line
        val verifText = when (user.verificationStatus) {
            "approved" -> "Verified"
            "rejected" -> "Rejected"
            else -> "Pending review"
        }
        val roleText = user.role.replaceFirstChar { it.uppercase() }
        holder.tvInfo.text = "$roleText • $verifText"

        // Action button
        if (user.status == "Active") {
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

        holder.btnAction.setOnClickListener { onAction(user) }
    }

    override fun getItemCount(): Int = users.size
}