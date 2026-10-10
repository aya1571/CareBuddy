package com.example.carebuddy

data class Companion(
    val id: String,
    val name: String,
    val age: Int,
    val yearsExperience: Int,
    val rating: Double,
    val gender: String,          // "Male" or "Female"
    val isAvailable: Boolean,
    val photoResId: Int,         // placeholder image resource
    val location: String = ""
)
