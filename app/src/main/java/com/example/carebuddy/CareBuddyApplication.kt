package com.example.carebuddy

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class CareBuddyApplication : Application() {
    private var notificationListener: ListenerRegistration? = null

    override fun onCreate() {
        super.onCreate()
        PhoneNotifications.createChannel(this)
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            notificationListener?.remove()
            notificationListener = null
            val uid = auth.currentUser?.uid ?: return@addAuthStateListener
            var firstSnapshot = true
            notificationListener = FirebaseFirestore.getInstance()
                .collection("notifications")
                .whereEqualTo("userId", uid)
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("CareBuddyNotifications", "Could not listen for notifications", error)
                        return@addSnapshotListener
                    }
                    val changes = snapshot?.documentChanges.orEmpty()
                    if (firstSnapshot) {
                        firstSnapshot = false
                        return@addSnapshotListener
                    }
                    changes.filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }
                        .forEach { change ->
                            val doc = change.document
                            val preferences = getSharedPreferences("carebuddy_phone_notifications", MODE_PRIVATE)
                            val seen = preferences.getStringSet("seen_ids_$uid", emptySet()).orEmpty()
                            if (doc.id in seen) return@forEach
                            PhoneNotifications.show(
                                this,
                                doc.id,
                                doc.getString("title") ?: "CareBuddy update",
                                doc.getString("message").orEmpty()
                            )
                            preferences.edit()
                                .putStringSet("seen_ids_$uid", (seen + doc.id).toList().takeLast(100).toSet())
                                .apply()
                        }
                }
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is HomeActivity || activity is CompanionHomeActivity || activity is AdminDashboardActivity) {
                    PhoneNotifications.requestPermissionIfNeeded(activity)
                }
                val language = activity.getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
                    .getString("app_language", "English") ?: "English"
                LocalizedLabels.apply(activity.findViewById(android.R.id.content), language)
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

private object LocalizedLabels {
    private val malay = mapOf(
        "Home" to "Utama", "Bookings" to "Tempahan", "Notifications" to "Notifikasi", "Profile" to "Profil",
        "Requests" to "Permintaan", "Earnings" to "Pendapatan", "Find a Companion" to "Cari Pengiring",
        "Search for a companion for your needs" to "Cari pengiring mengikut keperluan anda",
        "Search companion..." to "Cari pengiring...", "My Upcoming Booking" to "Tempahan Akan Datang",
        "View Details" to "Lihat Butiran", "No upcoming bookings" to "Tiada tempahan akan datang",
        "Upcoming Bookings" to "Tempahan Akan Datang", "No new requests" to "Tiada permintaan baharu",
        "Booking Requests" to "Permintaan Tempahan", "My Bookings" to "Tempahan Saya", "Upcoming" to "Akan Datang",
        "Accepted" to "Diterima", "Completed" to "Selesai", "Cancelled" to "Dibatalkan", "Pending" to "Menunggu",
        "Available" to "Tersedia", "Unavailable" to "Tidak tersedia", "Area" to "Kawasan",
        "Active" to "Aktif", "In Progress" to "Sedang Berlangsung", "On The Way" to "Dalam Perjalanan",
        "Emergency Help" to "Bantuan Kecemasan", "Track Companion" to "Jejak Pengiring",
        "Male" to "Lelaki", "Female" to "Perempuan", "All" to "Semua", "Settings" to "Tetapan",
        "Language" to "Bahasa", "Help & Support" to "Bantuan & Sokongan", "Privacy Policy" to "Dasar Privasi",
        "Save Changes" to "Simpan Perubahan", "Book Companion" to "Tempah Pengiring", "No reviews yet" to "Belum ada ulasan",
        "Companion Profile" to "Profil Pengiring", "About" to "Tentang", "Skills" to "Kemahiran",
        "Choose Language" to "Pilih Bahasa", "Login" to "Log Masuk", "Register" to "Daftar",
        "Password" to "Kata Laluan", "Email or Phone Number" to "E-mel atau Nombor Telefon",
        "Payment Method" to "Kaedah Pembayaran", "Confirm Booking" to "Sahkan Tempahan",
        "IC Verification" to "Pengesahan Kad Pengenalan", "Verification" to "Pengesahan",
        "Dashboard" to "Papan Pemuka", "User Management" to "Pengurusan Pengguna",
        "Companion Management" to "Pengurusan Pengiring", "Booking Management" to "Pengurusan Tempahan"
    )
    private val chinese = mapOf(
        "Home" to "首页", "Bookings" to "预约", "Notifications" to "通知", "Profile" to "个人资料",
        "Requests" to "请求", "Earnings" to "收入", "Find a Companion" to "寻找陪护员",
        "Search for a companion for your needs" to "根据您的需要寻找陪护员",
        "Search companion..." to "搜索陪护员...", "My Upcoming Booking" to "即将到来的预约",
        "View Details" to "查看详情", "No upcoming bookings" to "暂无即将到来的预约",
        "Upcoming Bookings" to "即将到来的预约", "No new requests" to "暂无新请求",
        "Booking Requests" to "预约请求", "My Bookings" to "我的预约", "Upcoming" to "即将到来",
        "Accepted" to "已接受", "Completed" to "已完成", "Cancelled" to "已取消", "Pending" to "待处理",
        "Available" to "可预约", "Unavailable" to "暂不可预约", "Area" to "地区",
        "Active" to "进行中", "In Progress" to "服务中", "On The Way" to "正在途中",
        "Emergency Help" to "紧急求助", "Track Companion" to "追踪陪护员",
        "Male" to "男性", "Female" to "女性", "All" to "全部", "Settings" to "设置",
        "Language" to "语言", "Help & Support" to "帮助与支持", "Privacy Policy" to "隐私政策",
        "Save Changes" to "保存更改", "Book Companion" to "预约陪护员", "No reviews yet" to "暂无评价",
        "Companion Profile" to "陪护员资料", "About" to "关于", "Skills" to "技能",
        "Choose Language" to "选择语言", "Login" to "登录", "Register" to "注册",
        "Password" to "密码", "Email or Phone Number" to "电子邮件或电话号码",
        "Payment Method" to "付款方式", "Confirm Booking" to "确认预约",
        "IC Verification" to "身份证验证", "Verification" to "验证", "Dashboard" to "控制面板",
        "User Management" to "用户管理", "Companion Management" to "陪护员管理", "Booking Management" to "预约管理"
    )

    fun apply(root: View?, language: String) {
        val labels = when (language) {
            "Bahasa Malaysia" -> malay
            "中文 (Chinese)" -> chinese
            else -> emptyMap()
        }
        if (root == null || labels.isEmpty()) return
        fun visit(view: View) {
            if (view is TextView) {
                val original = view.text?.toString()?.trim().orEmpty()
                labels[original]?.let { view.text = it }
                val description = view.contentDescription?.toString()?.trim().orEmpty()
                labels[description]?.let { view.contentDescription = it }
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(root)
    }
}
