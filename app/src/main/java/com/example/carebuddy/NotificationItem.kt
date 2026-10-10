package com.example.carebuddy

data class NotificationItem(
    val id: String,
    val type: String,          // "booking", "payment", "reminder", "message"
    val title: String,
    val message: String,
    val timestamp: String
)