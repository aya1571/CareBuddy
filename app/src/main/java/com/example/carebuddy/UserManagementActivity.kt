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

class UserManagementActivity : AppCompatActivity() {

    // ===== STATE =====
    private var allUsers: List<AdminUserItem> = emptyList()
    private var currentFilter: String = "All"
    private var searchQuery: String = ""

    // ===== VIEWS =====
    private lateinit var rvUsers: RecyclerView
    private lateinit var loadingState: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyMessage: TextView
    private lateinit var etSearch: EditText

    private lateinit var tabAll: TextView
    private lateinit var tabActive: TextView
    private lateinit var tabSuspended: TextView
    private lateinit var allTabs: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_management)

        rvUsers = findViewById(R.id.rvUsers)
        loadingState = findViewById(R.id.loadingState)
        emptyState = findViewById(R.id.emptyState)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)
        etSearch = findViewById(R.id.etSearch)

        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val btnRefresh = findViewById<ImageView>(R.id.btnRefresh)

        tabAll = findViewById(R.id.tabAll)
        tabActive = findViewById(R.id.tabActive)
        tabSuspended = findViewById(R.id.tabSuspended)
        allTabs = listOf(tabAll, tabActive, tabSuspended)

        rvUsers.layoutManager = LinearLayoutManager(this)

        btnClose.setOnClickListener { finish() }
        btnRefresh.setOnClickListener { loadUsers() }

        // ===== TAB CLICKS =====
        tabAll.setOnClickListener { switchFilter("All") }
        tabActive.setOnClickListener { switchFilter("Active") }
        tabSuspended.setOnClickListener { switchFilter("Suspended") }

        // ===== SEARCH =====
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim()?.lowercase() ?: ""
                applyFilters()
            }
        })

        // ===== LOAD DATA =====
        loadUsers()
    }

    // ===================================================================
    // LOAD USERS FROM FIRESTORE
    // ===================================================================
    private fun loadUsers() {
        showLoading(true)

        // ===== Fetch BOTH elderly users AND companions =====
        FirebaseCareBuddyStore.loadUsersByRole("elderly") { elderly, error1 ->
            FirebaseCareBuddyStore.loadUsersByRole("companion") { companions, error2 ->

                if (isFinishing || isDestroyed) return@loadUsersByRole

                if (error1 != null || error2 != null) {
                    showLoading(false)
                    Toast.makeText(
                        this,
                        "Failed to load users. Check Firestore permissions.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@loadUsersByRole
                }

                allUsers = (elderly + companions).map { map ->
                    AdminUserItem(
                        uid = map["uid"] as? String ?: "",
                        name = map["displayName"] as? String ?: "",
                        email = map["email"] as? String ?: "",
                        role = map["role"] as? String ?: "elderly",
                        status = map["status"] as? String ?: "Active",
                        verificationStatus = map["verificationStatus"] as? String ?: "pending",
                        phone = map["phone"] as? String ?: ""
                    )
                }.sortedBy { it.name.lowercase() }

                showLoading(false)
                applyFilters()
            }
        }
    }

    // ===================================================================
    // APPLY CURRENT FILTER + SEARCH
    // ===================================================================
    private fun applyFilters() {
        val filtered = allUsers.filter { user ->
            // Filter by tab
            val matchesTab = when (currentFilter) {
                "Active" -> user.status == "Active"
                "Suspended" -> user.status == "Suspended"
                else -> true
            }

            // Filter by search
            val matchesSearch = searchQuery.isBlank() ||
                    user.name.lowercase().contains(searchQuery) ||
                    user.email.lowercase().contains(searchQuery)

            matchesTab && matchesSearch
        }

        if (filtered.isEmpty()) {
            showEmpty(currentFilter, searchQuery)
        } else {
            showList(filtered)
        }
    }

    // ===================================================================
    // SWITCH FILTER TAB
    // ===================================================================
    private fun switchFilter(filter: String) {
        currentFilter = filter

        allTabs.forEach { tab ->
            tab.setTextColor(ContextCompat.getColor(this, R.color.care_muted))
            tab.setBackgroundResource(0)
        }

        val target = when (filter) {
            "Active" -> tabActive
            "Suspended" -> tabSuspended
            else -> tabAll
        }
        target.setTextColor(ContextCompat.getColor(this, R.color.care_purple))
        target.setBackgroundResource(R.drawable.bg_tab_selected)

        applyFilters()
    }

    // ===================================================================
    // ACTION — suspend/restore
    // ===================================================================
    private fun handleAction(user: AdminUserItem) {
        val isSuspending = user.status == "Active"
        val title = if (isSuspending) "Suspend this account?" else "Restore this account?"
        val message = if (isSuspending) {
            "${user.name} will not be able to sign in."
        } else {
            "${user.name} will regain access."
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(if (isSuspending) "Yes, Suspend" else "Yes, Restore") { _, _ ->
                val newStatus = if (isSuspending) "Suspended" else "Active"
                FirebaseCareBuddyStore.setUserStatus(user.uid, newStatus) { error ->
                    if (error != null) {
                        Toast.makeText(this, "Failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Account marked $newStatus", Toast.LENGTH_SHORT).show()
                        loadUsers()
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
        rvUsers.visibility = View.GONE
        emptyState.visibility = View.GONE
    }

    private fun showList(users: List<AdminUserItem>) {
        loadingState.visibility = View.GONE
        emptyState.visibility = View.GONE
        rvUsers.visibility = View.VISIBLE

        rvUsers.adapter = UserManagementAdapter(users) { user -> handleAction(user) }
    }

    private fun showEmpty(filter: String, query: String) {
        loadingState.visibility = View.GONE
        rvUsers.visibility = View.GONE
        emptyState.visibility = View.VISIBLE

        if (query.isNotBlank()) {
            tvEmptyTitle.text = "No matches"
            tvEmptyMessage.text = "No users match \"$query\""
        } else {
            tvEmptyTitle.text = when (filter) {
                "Active" -> "No active users"
                "Suspended" -> "No suspended users"
                else -> "No users yet"
            }
            tvEmptyMessage.text = "Try a different filter or refresh"
        }
    }
}