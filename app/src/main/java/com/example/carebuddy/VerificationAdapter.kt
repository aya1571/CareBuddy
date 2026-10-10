package com.example.carebuddy

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class VerificationAdapter(
    private val submissions: List<VerificationItem>,
    private val onApprove: (VerificationItem) -> Unit,
    private val onReject: (VerificationItem) -> Unit
) : RecyclerView.Adapter<VerificationAdapter.VerificationViewHolder>() {

    inner class VerificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvRole: TextView = itemView.findViewById(R.id.tvRole)
        val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
        val ivIc: ImageView = itemView.findViewById(R.id.ivIc)
        val ivFace: ImageView = itemView.findViewById(R.id.ivFace)
        val btnApprove: Button = itemView.findViewById(R.id.btnApprove)
        val btnReject: Button = itemView.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_verification, parent, false)
        return VerificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: VerificationViewHolder, position: Int) {
        val item = submissions[position]

        holder.tvName.text = item.name
        holder.tvRole.text = item.role.replaceFirstChar { it.uppercase() }
        holder.tvEmail.text = item.email

        // ===== Load IC image =====
        val icBytes = item.icBytes
        if (icBytes != null) {
            val bitmap = BitmapFactory.decodeByteArray(icBytes, 0, icBytes.size)
            holder.ivIc.setImageBitmap(bitmap)
            holder.ivIc.clearColorFilter()
        } else {
            holder.ivIc.setImageResource(android.R.drawable.ic_menu_gallery)
            holder.ivIc.setColorFilter(
                ContextCompat.getColor(holder.itemView.context, R.color.care_muted)
            )
        }

        // ===== Load face photo =====
        val faceBytes = item.faceBytes
        if (faceBytes != null) {
            val bitmap = BitmapFactory.decodeByteArray(faceBytes, 0, faceBytes.size)
            holder.ivFace.setImageBitmap(bitmap)
            holder.ivFace.clearColorFilter()
        } else {
            holder.ivFace.setImageResource(android.R.drawable.ic_menu_camera)
            holder.ivFace.setColorFilter(
                ContextCompat.getColor(holder.itemView.context, R.color.care_muted)
            )
        }

        // ===== Actions =====
        holder.btnApprove.setOnClickListener { onApprove(item) }
        holder.btnReject.setOnClickListener { onReject(item) }
    }

    override fun getItemCount(): Int = submissions.size
}

// ===================================================================
// DATA CLASS for one verification row
// ===================================================================
data class VerificationItem(
    val uid: String,
    var name: String,        // ← changed from val to var
    var email: String,       // ← changed from val to var
    val role: String,
    val icPath: String,
    val facePath: String,
    var icBytes: ByteArray? = null,
    var faceBytes: ByteArray? = null,
    val hasSubmission: Boolean = true
)
