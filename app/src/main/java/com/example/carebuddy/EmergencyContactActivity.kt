package com.example.carebuddy

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.firebase.auth.FirebaseAuth

class EmergencyContactActivity : AppCompatActivity() {
    private lateinit var contactName: EditText
    private lateinit var contactPhone: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24)
            setBackgroundColor(Color.rgb(248, 246, 252))
        }
        val back = TextView(this).apply {
            text = "‹   Emergency Contact"
            textSize = 21f
            setTextColor(Color.rgb(48, 36, 83))
            setPadding(4)
            setOnClickListener { finish() }
        }
        val explanation = TextView(this).apply {
            text = "Add someone you trust. During a booking, the Emergency button can open your phone dialer with this number ready."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(4)
        }
        contactName = EditText(this).apply {
            hint = "Contact name"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        contactPhone = EditText(this).apply {
            hint = "Phone number, including country code if needed"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_PHONE
        }
        saveButton = Button(this).apply { text = "Save Emergency Contact" }

        root.addView(back, matchWrap())
        root.addView(explanation, matchWrap())
        root.addView(contactName, withTopMargin(20))
        root.addView(contactPhone, withTopMargin(12))
        root.addView(saveButton, withTopMargin(20))
        setContentView(root)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, "Please sign in first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        FirebaseCareBuddyStore.loadUserProfile(uid) { profile, error ->
            if (isFinishing || isDestroyed) return@loadUserProfile
            if (error != null) {
                Toast.makeText(this, "Could not load your saved contact.", Toast.LENGTH_LONG).show()
            } else {
                contactName.setText(profile?.get("emergencyContactName") as? String ?: "")
                contactPhone.setText(profile?.get("emergencyContactPhone") as? String ?: "")
            }
        }
        saveButton.setOnClickListener { save(uid) }
    }

    private fun save(uid: String) {
        val name = contactName.text.toString().trim()
        val phone = contactPhone.text.toString().trim()
        val digits = phone.count(Char::isDigit)
        if (name.isBlank() || name.length > 80) {
            contactName.error = "Enter the contact's name"
            return
        }
        if (digits !in 7..15 || phone.length > 24) {
            contactPhone.error = "Enter a valid phone number"
            return
        }
        saveButton.isEnabled = false
        FirebaseCareBuddyStore.saveEmergencyContact(uid, name, phone) { error ->
            if (isFinishing || isDestroyed) return@saveEmergencyContact
            saveButton.isEnabled = true
            if (error != null) {
                Toast.makeText(this, "Could not save contact. Check your connection and Firestore rules.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Emergency contact saved.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun withTopMargin(top: Int) = matchWrap().apply { topMargin = (top * resources.displayMetrics.density).toInt() }
}
