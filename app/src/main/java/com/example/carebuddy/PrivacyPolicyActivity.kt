package com.example.carebuddy

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class PrivacyPolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        val btnClose = findViewById<ImageView>(R.id.btnClose)
        btnClose.setOnClickListener { finish() }
    }
}