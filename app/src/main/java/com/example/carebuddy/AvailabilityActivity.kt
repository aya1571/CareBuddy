package com.example.carebuddy

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

class AvailabilityActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
    }

    // ===== DAY CONFIG =====
    private data class DayConfig(
        val key: String,
        val label: String,
        val switch: SwitchCompat,
        val startView: TextView,
        val endView: TextView
    )

    private lateinit var days: List<DayConfig>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_availability)

        // ===== COMPANION KEY =====
        val companionName = prefs.getString("companion_name", "Companion").orEmpty()
        val key = if (companionName.isBlank()) "companion" else companionName
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "_")

        // ===== BIND VIEWS =====
        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val switchAvailability = findViewById<SwitchCompat>(R.id.switchAvailability)
        val tvAvailabilityStatus = findViewById<TextView>(R.id.tvAvailabilityStatus)
        val btnSave = findViewById<Button>(R.id.btnSaveAvailability)

        // ===== BUILD DAY LIST =====
        days = listOf(
            DayConfig("mon", "Monday",
                findViewById(R.id.switchMon),
                findViewById(R.id.tvMonStart),
                findViewById(R.id.tvMonEnd)),
            DayConfig("tue", "Tuesday",
                findViewById(R.id.switchTue),
                findViewById(R.id.tvTueStart),
                findViewById(R.id.tvTueEnd)),
            DayConfig("wed", "Wednesday",
                findViewById(R.id.switchWed),
                findViewById(R.id.tvWedStart),
                findViewById(R.id.tvWedEnd)),
            DayConfig("thu", "Thursday",
                findViewById(R.id.switchThu),
                findViewById(R.id.tvThuStart),
                findViewById(R.id.tvThuEnd)),
            DayConfig("fri", "Friday",
                findViewById(R.id.switchFri),
                findViewById(R.id.tvFriStart),
                findViewById(R.id.tvFriEnd)),
            DayConfig("sat", "Saturday",
                findViewById(R.id.switchSat),
                findViewById(R.id.tvSatStart),
                findViewById(R.id.tvSatEnd)),
            DayConfig("sun", "Sunday",
                findViewById(R.id.switchSun),
                findViewById(R.id.tvSunStart),
                findViewById(R.id.tvSunEnd))
        )

        // ===== LOAD SAVED AVAILABILITY =====
        val isAvailable = prefs.getBoolean("companion_available", true)
        switchAvailability.isChecked = isAvailable
        updateAvailabilityLabel(tvAvailabilityStatus, isAvailable)

        // ===== LOAD SCHEDULE FROM PREFS =====
        days.forEach { day ->
            val dayKey = "schedule_${key}_${day.key}"
            val startKey = "schedule_start_${key}_${day.key}"
            val endKey = "schedule_end_${key}_${day.key}"

            val defaultEnabled = day.key !in listOf("sat", "sun")
            day.switch.isChecked = prefs.getBoolean(dayKey, defaultEnabled)
            day.startView.text = prefs.getString(startKey, "09:00 AM")
            day.endView.text = prefs.getString(endKey, "05:00 PM")

            // ===== TIME PICKER: START =====
            day.startView.setOnClickListener {
                pickTime(day.startView, currentText = day.startView.text.toString())
            }

            // ===== TIME PICKER: END =====
            day.endView.setOnClickListener {
                pickTime(day.endView, currentText = day.endView.text.toString())
            }
        }

        // ===== AVAILABILITY TOGGLE =====
        switchAvailability.setOnCheckedChangeListener { _, checked ->
            updateAvailabilityLabel(tvAvailabilityStatus, checked)
        }

        // ===== SAVE BUTTON =====
        btnSave.setOnClickListener {
            saveAll(key, switchAvailability.isChecked)
        }

        // ===== CLOSE =====
        btnClose.setOnClickListener { finish() }
    }

    // ===================================================================
    // UPDATE AVAILABILITY LABEL
    // ===================================================================
    private fun updateAvailabilityLabel(view: TextView, isAvailable: Boolean) {
        view.text = if (isAvailable) {
            "You are available for bookings"
        } else {
            "You are currently unavailable"
        }
    }

    // ===================================================================
    // SAVE EVERYTHING
    // ===================================================================
    private fun saveAll(key: String, isAvailable: Boolean) {
        val editor = prefs.edit()

        // Availability
        editor.putBoolean("companion_available", isAvailable)

        // Weekly schedule
        days.forEach { day ->
            val dayKey = "schedule_${key}_${day.key}"
            val startKey = "schedule_start_${key}_${day.key}"
            val endKey = "schedule_end_${key}_${day.key}"

            editor.putBoolean(dayKey, day.switch.isChecked)
            editor.putString(startKey, day.startView.text.toString())
            editor.putString(endKey, day.endView.text.toString())
        }
        editor.apply()

        // Sync availability to Firestore
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseCareBuddyStore.setCompanionAvailability(uid, isAvailable) { error ->
                if (error != null) {
                    Toast.makeText(
                        this,
                        "Saved locally, but Firebase sync failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        Toast.makeText(this, "Schedule saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    // ===================================================================
    // TIME PICKER
    // ===================================================================
    private fun pickTime(target: TextView, currentText: String) {
        val (hour, minute) = parseTime(currentText)

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val displayHour = if (selectedHour % 12 == 0) 12 else selectedHour % 12
            val amPm = if (selectedHour < 12) "AM" else "PM"
            val formatted = String.format(
                Locale.getDefault(),
                "%02d:%02d %s",
                displayHour,
                selectedMinute,
                amPm
            )
            target.text = formatted
        }, hour, minute, false).show()
    }

    // ===================================================================
    // PARSE "09:00 AM" → (9, 0)
    // ===================================================================
    private fun parseTime(text: String): Pair<Int, Int> {
        val regex = Regex("(\\d{1,2}):(\\d{2}) (AM|PM)", RegexOption.IGNORE_CASE)
        val match = regex.find(text) ?: return Pair(9, 0)

        val hour12 = match.groupValues[1].toIntOrNull() ?: 9
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val ampm = match.groupValues[3].uppercase()

        val hour = when {
            ampm == "AM" && hour12 == 12 -> 0
            ampm == "PM" && hour12 < 12 -> hour12 + 12
            else -> hour12
        }
        return Pair(hour, minute)
    }
}