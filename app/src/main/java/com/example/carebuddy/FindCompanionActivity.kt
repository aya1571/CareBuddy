package com.example.carebuddy

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FindCompanionActivity : AppCompatActivity() {

    // ===== STATE =====
    private var selectedActivity: String? = null
    private var selectedDate: String? = null
    private var selectedTime: String? = null

    private lateinit var chips: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_companion)

        // ===== BIND VIEWS =====
        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val chipHospital = findViewById<TextView>(R.id.chipHospital)
        val chipShopping = findViewById<TextView>(R.id.chipShopping)
        val chipErrands = findViewById<TextView>(R.id.chipErrands)
        val chipOthers = findViewById<TextView>(R.id.chipOthers)
        val fieldDate = findViewById<LinearLayout>(R.id.fieldDate)
        val fieldTime = findViewById<LinearLayout>(R.id.fieldTime)
        val etLocation = findViewById<EditText>(R.id.etLocation)
        val btnSearch = findViewById<Button>(R.id.btnSearch)

        chips = listOf(chipHospital, chipShopping, chipErrands, chipOthers)

        // ===== CLOSE =====
        btnClose.setOnClickListener { finish() }

        // ===== ACTIVITY CHIPS =====
        chipHospital.setOnClickListener { selectChip("Hospital") }
        chipShopping.setOnClickListener { selectChip("Shopping") }
        chipErrands.setOnClickListener { selectChip("Errands") }
        chipOthers.setOnClickListener { selectChip("Others") }

        // ===== DATE PICKER =====
        fieldDate.setOnClickListener { showDatePicker() }

        // ===== TIME PICKER =====
        fieldTime.setOnClickListener { showTimePicker() }

        // ===== SEARCH BUTTON =====
        btnSearch.setOnClickListener {
            val locationText = etLocation.text.toString().trim()

            when {
                selectedActivity == null -> {
                    Toast.makeText(this, "Please select an activity", Toast.LENGTH_SHORT).show()
                }
                selectedDate == null -> {
                    Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show()
                }
                selectedTime == null -> {
                    Toast.makeText(this, "Please select a time", Toast.LENGTH_SHORT).show()
                }
                locationText.isBlank() -> {
                    Toast.makeText(this, "Please enter your location", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // Pass all filter values to the results screen (Screen 7)
                    val intent = Intent(this, CompanionListActivity::class.java)
                    intent.putExtra("activity", selectedActivity)
                    intent.putExtra("date", selectedDate)
                    intent.putExtra("time", selectedTime)
                    intent.putExtra("location", locationText)
                    startActivity(intent)
                }
            }
        }
    }

    // ===================================================================
    // CHIP SELECTION
    // ===================================================================
    private fun selectChip(activity: String) {
        selectedActivity = activity

        // Reset all chips
        chips.forEach { chip ->
            chip.setBackgroundResource(R.drawable.bg_chip)
            chip.setTextColor(ContextCompat.getColor(this, R.color.care_ink))
        }

        // Highlight the chosen chip
        chips.firstOrNull { it.text.toString() == activity }?.let {
            it.setBackgroundResource(R.drawable.bg_chip_selected)
            it.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
    }

    // ===================================================================
    // DATE PICKER
    // ===================================================================
    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                selectedDate = formatter.format(picked.time)

                val tvDateValue = findViewById<TextView>(R.id.tvDateValue)
                tvDateValue.text = selectedDate
                tvDateValue.setTextColor(ContextCompat.getColor(this, R.color.care_ink))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        // Prevent past dates
        dialog.datePicker.minDate = System.currentTimeMillis() - 1000
        dialog.show()
    }

    // ===================================================================
    // TIME PICKER
    // ===================================================================
    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val dialog = TimePickerDialog(
            this,
            { _, hour, minute ->
                val amPm = if (hour < 12) "AM" else "PM"
                val hour12 = when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
                selectedTime = String.format(Locale.getDefault(), "%02d:%02d %s", hour12, minute, amPm)

                val tvTimeValue = findViewById<TextView>(R.id.tvTimeValue)
                tvTimeValue.text = selectedTime
                tvTimeValue.setTextColor(ContextCompat.getColor(this, R.color.care_ink))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        )
        dialog.show()
    }
}