package com.example.carebuddy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LocalAccount(
    val email: String,
    val name: String,
    val role: String,
    val status: String = "Active"
)

/** On-device prototype account directory. It never stores passwords, IC numbers, or identity photos. */
object LocalAccountStore {
    private const val PREFS = "carebuddy_demo"
    private const val KEY = "local_accounts"

    fun all(context: Context): List<LocalAccount> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(json) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val email = item.optString("email").trim().lowercase()
            if (email.isBlank()) return@mapNotNull null
            LocalAccount(email, item.optString("name"), item.optString("role"), item.optString("status", "Active"))
        }
    }

    fun save(context: Context, account: LocalAccount) {
        if (account.email.isBlank()) return
        val accounts = all(context).toMutableList()
        val index = accounts.indexOfFirst { it.email.equals(account.email, true) }
        if (index >= 0) accounts[index] = account.copy(status = accounts[index].status)
        else accounts.add(account.copy(email = account.email.trim().lowercase()))
        persist(context, accounts)
    }

    fun rename(context: Context, email: String, name: String) {
        persist(context, all(context).map { if (it.email.equals(email, true)) it.copy(name = name.trim()) else it })
    }

    fun setStatus(context: Context, email: String, status: String) {
        persist(context, all(context).map { if (it.email.equals(email, true)) it.copy(status = status) else it })
    }

    private fun persist(context: Context, accounts: List<LocalAccount>) {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(JSONObject().apply {
                put("email", account.email)
                put("name", account.name)
                put("role", account.role)
                put("status", account.status)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
