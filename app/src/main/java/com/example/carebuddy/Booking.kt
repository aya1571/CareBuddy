package com.example.carebuddy

data class Booking(
    val id: String,
    val activity: String,
    val dateTime: String,
    val companionName: String,
    val status: String,
    val iconResId: Int,
    // ===== NEW: extras for companion view =====
    val customerName: String = "",
    val companion: String = "",
    val date: String = "",
    val time: String = "",
    val location: String = ""
)