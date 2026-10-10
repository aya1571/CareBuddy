package com.example.carebuddy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CompanionAdapter(
    private val companions: List<Companion>,
    private val onItemClick: (Companion) -> Unit
) : RecyclerView.Adapter<CompanionAdapter.CompanionViewHolder>() {

    inner class CompanionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivPhoto: ImageView = itemView.findViewById(R.id.ivCompanionPhoto)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvDetails: TextView = itemView.findViewById(R.id.tvDetails)
        val tvRating: TextView = itemView.findViewById(R.id.tvRating)
        val tvExperience: TextView = itemView.findViewById(R.id.tvExperience)
        val tvAvailability: TextView = itemView.findViewById(R.id.tvAvailability)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompanionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_companion, parent, false)
        return CompanionViewHolder(view)
    }

    override fun onBindViewHolder(holder: CompanionViewHolder, position: Int) {
        val companion = companions[position]

        holder.tvName.text = companion.name
        holder.tvDetails.text = listOf(companion.gender, companion.location)
            .filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Profile details not added" }
        holder.tvRating.text = if (companion.rating > 0.0) companion.rating.toString() else "No reviews"
        holder.tvExperience.text = when {
            companion.yearsExperience >= 3 -> "${companion.yearsExperience} yrs experience"
            companion.yearsExperience > 0 -> "${companion.yearsExperience} yr experience"
            else -> "Experience not listed"
        }

        // Availability badge
        if (companion.isAvailable) {
            holder.tvAvailability.text = "Available"
            holder.tvAvailability.setBackgroundResource(R.drawable.bg_badge_available)
            holder.tvAvailability.setTextColor(0xFF2E7D32.toInt())
        } else {
            holder.tvAvailability.text = "Busy"
            holder.tvAvailability.setBackgroundResource(R.drawable.bg_chip)
            holder.tvAvailability.setTextColor(0xFF888888.toInt())
        }

        // Placeholder photo
        holder.ivPhoto.setImageResource(companion.photoResId)

        // Click handler
        holder.itemView.setOnClickListener { onItemClick(companion) }
    }

    override fun getItemCount(): Int = companions.size
}
