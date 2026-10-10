package com.example.carebuddy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ReviewAdapter(
    private val reviews: List<CareReview>
) : RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder>() {

    inner class ReviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCompanion: TextView = itemView.findViewById(R.id.tvCompanion)
        val tvStars: TextView = itemView.findViewById(R.id.tvStars)
        val tvReviewer: TextView = itemView.findViewById(R.id.tvReviewer)
        val tvComment: TextView = itemView.findViewById(R.id.tvComment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = reviews[position]

        holder.tvCompanion.text = review.companion.ifBlank { "Unknown companion" }

        val stars = review.stars.coerceIn(0, 5)
        holder.tvStars.text = "★".repeat(stars) + "☆".repeat(5 - stars)

        holder.tvReviewer.text = "By ${review.reviewer.ifBlank { "CareBuddy user" }}"

        if (review.comment.isBlank()) {
            holder.tvComment.text = "(No written comment)"
            holder.tvComment.setTextColor(0xFF888888.toInt())
            holder.tvComment.setTypeface(null, android.graphics.Typeface.ITALIC)
        } else {
            holder.tvComment.text = review.comment
            holder.tvComment.setTextColor(0xFF333333.toInt())
            holder.tvComment.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }

    override fun getItemCount(): Int = reviews.size
}