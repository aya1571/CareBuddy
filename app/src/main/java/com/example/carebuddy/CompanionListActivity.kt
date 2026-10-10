package com.example.carebuddy

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CompanionListActivity : AppCompatActivity() {

    // ===== VIEWS =====
    private lateinit var rvCompanions: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyMessage: TextView
    private lateinit var adapter: CompanionAdapter
    private lateinit var filterChips: List<TextView>

    // ===== DATA =====
    private var allCompanions: List<Companion> = emptyList()
    private var currentFilter: String = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_companion_list)

        // ===== BIND VIEWS =====
        rvCompanions = findViewById(R.id.rvCompanions)
        emptyState = findViewById(R.id.emptyState)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)

        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val filterAll = findViewById<TextView>(R.id.filterAll)
        val filterMale = findViewById<TextView>(R.id.filterMale)
        val filterFemale = findViewById<TextView>(R.id.filterFemale)
        val filterDistance = findViewById<TextView>(R.id.filterDistance)

        filterChips = listOf(filterAll, filterMale, filterFemale, filterDistance)

        // ===== RECYCLERVIEW =====
        rvCompanions.layoutManager = LinearLayoutManager(this)

        // ===== CLOSE =====
        btnClose.setOnClickListener { finish() }

        // ===== FILTERS =====
        filterAll.setOnClickListener { applyFilter("All") }
        filterMale.setOnClickListener { applyFilter("Male") }
        filterFemale.setOnClickListener { applyFilter("Female") }
        filterDistance.text = "Area"
        filterDistance.setOnClickListener { applyFilter("Area") }

        // ===== LOAD REAL COMPANIONS FROM FIRESTORE =====
        loadCompanionsFromFirestore()
    }

    // ===================================================================
    // LOAD FROM FIRESTORE
    // ===================================================================
    private fun loadCompanionsFromFirestore() {
        // Show loading
        emptyState.visibility = View.VISIBLE
        tvEmptyTitle.text = "Loading..."
        tvEmptyMessage.text = "Fetching available companions..."
        rvCompanions.visibility = View.GONE

        FirebaseCareBuddyStore.loadAvailableCompanions { profiles, error ->
            if (isFinishing || isDestroyed) return@loadAvailableCompanions

            if (error != null) {
                Log.e("CompanionList", "Failed to load: ${error.message}")
                showEmpty("Couldn't load companions", "Check your connection and try again")
                return@loadAvailableCompanions
            }

            if (profiles.isEmpty()) {
                showEmpty("No verified companions", "Approved companion profiles will appear here")
                return@loadAvailableCompanions
            }

            // ===== CONVERT Firestore profiles → Companion objects =====
            allCompanions = profiles.map { profile ->
                Companion(
                    id = profile.uid,
                    name = profile.displayName,
                    age = 0,
                    yearsExperience = profile.yearsExperience,
                    rating = 0.0,
                    gender = profile.gender,
                    isAvailable = profile.available,
                    photoResId = android.R.drawable.ic_menu_my_calendar,
                    location = profile.location
                )
            }

            // ===== RENDER =====
            rvCompanions.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            applyFilter(currentFilter)
        }
    }

    // ===================================================================
    // BUILD ADAPTER
    // ===================================================================
    private fun buildAdapter(list: List<Companion>): CompanionAdapter {
        return CompanionAdapter(list) { companion ->
            val intent = Intent(this, CompanionProfileActivity::class.java)
            intent.putExtra("companionId", companion.id)
            intent.putExtra("companionName", companion.name)
            // Keep the search selections attached to the companion through the booking flow.
            listOf("activity", "date", "time", "location").forEach { key ->
                this.intent.getStringExtra(key)?.let { intent.putExtra(key, it) }
            }
            startActivity(intent)
        }
    }

    // ===================================================================
    // APPLY FILTER (All / Male / Female)
    // ===================================================================
    private fun applyFilter(filter: String) {
        currentFilter = filter

        // Update chip styles
        filterChips.forEach { chip ->
            chip.setBackgroundResource(R.drawable.bg_chip)
            chip.setTextColor(ContextCompat.getColor(this, R.color.care_ink))
        }
        val selectedChip = when (filter) {
            "Male" -> filterChips.getOrNull(1)
            "Female" -> filterChips.getOrNull(2)
            "Area" -> filterChips.getOrNull(3)
            else -> filterChips.firstOrNull()
        }
        selectedChip?.let {
            it.setBackgroundResource(R.drawable.bg_chip_selected)
            it.setTextColor(ContextCompat.getColor(this, R.color.white))
        }

        // Filter list
        val area = intent.getStringExtra("location")?.trim().orEmpty()
        val filteredList = when (filter) {
            "Male" -> allCompanions.filter { it.gender.equals("Male", true) }
            "Female" -> allCompanions.filter { it.gender.equals("Female", true) }
            "Area" -> allCompanions.sortedBy { it.locationAreaRank(area) }
            else -> allCompanions
        }

        if (filteredList.isEmpty() && allCompanions.isNotEmpty()) {
            showEmpty("No $filter companions", "Try a different filter")
        } else {
            rvCompanions.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            adapter = buildAdapter(filteredList)
            rvCompanions.adapter = adapter
        }
    }

    private fun Companion.locationAreaRank(area: String): Int {
        if (area.isBlank() || location.isBlank()) return 1
        return if (location.contains(area, ignoreCase = true) || area.contains(location, ignoreCase = true)) 0 else 1
    }

    // ===================================================================
    // EMPTY STATE
    // ===================================================================
    private fun showEmpty(title: String, message: String) {
        rvCompanions.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        tvEmptyTitle.text = title
        tvEmptyMessage.text = message
    }
}
