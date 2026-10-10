package com.example.carebuddy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnnouncementAdapter(
    private val announcements: List<FirebaseCareBuddyStore.Announcement>,
    private val dateFormat: SimpleDateFormat
) : RecyclerView.Adapter<AnnouncementAdapter.AnnouncementViewHolder>() {

    inner class AnnouncementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val tvFooter: TextView = itemView.findViewById(R.id.tvFooter)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnnouncementViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_announcement, parent, false)
        return AnnouncementViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnnouncementViewHolder, position: Int) {
        val item = announcements[position]

        holder.tvTitle.text = item.title.ifBlank { "Announcement" }
        holder.tvMessage.text = item.message

        val dateStr = dateFormat.format(Date(item.createdAt))
        holder.tvFooter.text = "Sent by ${item.sentBy} • $dateStr"
    }

    override fun getItemCount(): Int = announcements.size
}