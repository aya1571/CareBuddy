package com.example.carebuddy

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CompanionManagementActivity : AppCompatActivity() {

    private var allCompanions: List<AdminCompanionItem> = emptyList()
    private var currentFilter: String = "All"
    private var searchQuery: String = ""

    private lateinit var rvCompanions: RecyclerView
    private lateinit var loadingState: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyMessage: TextView
    private lateinit var etSearch: EditText

    private lateinit var tabAll: TextView
    private lateinit var tabAvailable: TextView
    private lateinit var tabUnavailable: TextView
    private lateinit var allTabs: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_companion_management)

        rvCompanions = findViewById(R.id.rvCompanions)
        loadingState = findViewById(R.id.loadingState)
        emptyState = findViewById(R.id.emptyState)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)
        etSearch = findViewById(R.id.etSearch)

        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val btnRefresh = findViewById<ImageView>(R.id.btnRefresh)

        tabAll = findViewById(R.id.tabAll)
        tabAvailable = findViewById(R.id.tabAvailable)
        tabUnavailable = findViewById(R.id.tabUnavailable)
        allTabs = listOf(tabAll, tabAvailable, tabUnavailable)

        rvCompanions.layoutManager = LinearLayoutManager(this)

        btnClose.setOnClickListener { finish() }
        btnRefresh.setOnClickListener { loadCompanions() }

        tabAll.setOnClickListener { switchFilter("All") }
        tabAvailable.setOnClickListener { switchFilter("Available") }
        tabUnavailable.setOnClickListener { switchFilter("Unavailable") }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim()?.lowercase() ?: ""
                applyFilters()
            }
        })

        loadCompanions()
    }

    // ===================================================================
    // LOAD COMPANIONS
    // ===================================================================
    private fun loadCompanions() {
        showLoading(true)

        // Load users with role = companion
        FirebaseCareBuddyStore.loadUsersByRole("companion") { users, error ->
            if (isFinishing || isDestroyed) return@loadUsersByRole

            if (error != null) {
                showLoading(false)
                Toast.makeText(
                    this,
                    "Failed to load companions. Check Firestore permissions.",
                    Toast.LENGTH_LONG
                ).show()
                return@loadUsersByRole
            }

            if (users.isEmpty()) {
                allCompanions = emptyList()
                showLoading(false)
                applyFilters()
                return@loadUsersByRole
            }

            // For each companion, also load their companionProfile (for rate, bio, etc.)
            val loadedItems = mutableListOf<AdminCompanionItem>()
            var pending = users.size

            users.forEach { userMap ->
                val uid = userMap["uid"] as? String ?: ""
                if (uid.isBlank()) {
                    pending--
                    if (pending == 0) finishLoading(loadedItems)
                    return@forEach
                }

                FirebaseCareBuddyStore.loadCompanionProfile(uid) { profile, _ ->
                    if (isFinishing || isDestroyed) return@loadCompanionProfile

                    val item = AdminCompanionItem(
                        uid = uid,
                        name = (userMap["displayName"] as? String)
                            ?: (profile?.get("displayName") as? String)
                            ?: "Unnamed companion",
                        email = userMap["email"] as? String ?: "",
                        phone = userMap["phone"] as? String ?: "",
                        role = "companion",
                        status = userMap["status"] as? String ?: "Active",
                        verificationStatus = userMap["verificationStatus"] as? String ?: "pending",
                        location = profile?.get("location") as? String ?: "Kuala Lumpur",
                        hourlyRate = profile?.get("hourlyRate") as? String ?: "RM 30/hour",
                        bio = profile?.get("bio") as? String ?: "",
                        available = profile?.get("available") as? Boolean ?: false
                    )

                    loadedItems.add(item)
                    pending--
                    if (pending == 0) finishLoading(loadedItems)
                }
            }
        }
    }

    private fun finishLoading(items: List<AdminCompanionItem>) {
        allCompanions = items.sortedBy { it.name.lowercase() }
        showLoading(false)
        applyFilters()
    }

    // ===================================================================
    // APPLY FILTER + SEARCH
    // ===================================================================
    private fun applyFilters() {
        val filtered = allCompanions.filter { c ->
            val matchesTab = when (currentFilter) {
                "Available" -> c.available
                "Unavailable" -> !c.available
                else -> true
            }

            val matchesSearch = searchQuery.isBlank() ||
                    c.name.lowercase().contains(searchQuery) ||
                    c.email.lowercase().contains(searchQuery) ||
                    c.location.lowercase().contains(searchQuery)

            matchesTab && matchesSearch
        }

        if (filtered.isEmpty()) {
            showEmpty(currentFilter, searchQuery)
        } else {
            showList(filtered)
        }
    }

    // ===================================================================
    // SWITCH FILTER
    // ===================================================================
    private fun switchFilter(filter: String) {
        currentFilter = filter

        allTabs.forEach { tab ->
            tab.setTextColor(ContextCompat.getColor(this, R.color.care_muted))
            tab.setBackgroundResource(0)
        }

        val target = when (filter) {
            "Available" -> tabAvailable
            "Unavailable" -> tabUnavailable
            else -> tabAll
        }
        target.setTextColor(ContextCompat.getColor(this, R.color.care_purple))
        target.setBackgroundResource(R.drawable.bg_tab_selected)

        applyFilters()
    }

    // ===================================================================
    // SUSPEND / RESTORE
    // ===================================================================
    private fun handleAction(companion: AdminCompanionItem) {
        val isSuspending = companion.status == "Active"
        val title = if (isSuspending) "Suspend this companion?" else "Restore this companion?"
        val message = if (isSuspending) {
            "${companion.name} will not be able to receive bookings."
        } else {
            "${companion.name} will regain access."
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(if (isSuspending) "Yes, Suspend" else "Yes, Restore") { _, _ ->
                val newStatus = if (isSuspending) "Suspended" else "Active"
                FirebaseCareBuddyStore.setUserStatus(companion.uid, newStatus) { error ->
                    if (error != null) {
                        Toast.makeText(this, "Failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Companion marked $newStatus", Toast.LENGTH_SHORT).show()
                        loadCompanions()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ===================================================================
    // STATE SWITCHERS
    // ===================================================================
    private fun showLoading(show: Boolean) {
        loadingState.visibility = if (show) View.VISIBLE else View.GONE
        rvCompanions.visibility = View.GONE
        emptyState.visibility = View.GONE
    }

    private fun showList(companions: List<AdminCompanionItem>) {
        loadingState.visibility = View.GONE
        emptyState.visibility = View.GONE
        rvCompanions.visibility = View.VISIBLE

        rvCompanions.adapter = CompanionManagementAdapter(companions) { c -> handleAction(c) }
    }

    private fun showEmpty(filter: String, query: String) {
        loadingState.visibility = View.GONE
        rvCompanions.visibility = View.GONE
        emptyState.visibility = View.VISIBLE

        if (query.isNotBlank()) {
            tvEmptyTitle.text = "No matches"
            tvEmptyMessage.text = "No companions match \"$query\""
        } else {
            tvEmptyTitle.text = when (filter) {
                "Available" -> "No available companions"
                "Unavailable" -> "No unavailable companions"
                else -> "No companions yet"
            }
            tvEmptyMessage.text = "Try a different filter or refresh"
        }
    }
}