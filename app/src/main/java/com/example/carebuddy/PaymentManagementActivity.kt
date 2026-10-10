package com.example.carebuddy

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PaymentManagementActivity : AppCompatActivity() {

    // ===== VIEWS (as class fields so loadPayments() can access them) =====
    private lateinit var tvTotalAmount: TextView
    private lateinit var tvTotalCount: TextView

    private lateinit var tvCardTotal: TextView
    private lateinit var tvCardCount: TextView
    private lateinit var tvBankingTotal: TextView
    private lateinit var tvBankingCount: TextView
    private lateinit var tvTnGTotal: TextView
    private lateinit var tvTnGCount: TextView
    private lateinit var tvCashTotal: TextView
    private lateinit var tvCashCount: TextView

    private lateinit var loadingState: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var rvPayments: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_management)

        // ===== BIND VIEWS =====
        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val btnRefresh = findViewById<ImageView>(R.id.btnRefresh)

        tvTotalAmount = findViewById(R.id.tvTotalAmount)
        tvTotalCount = findViewById(R.id.tvTotalCount)

        tvCardTotal = findViewById(R.id.tvCardTotal)
        tvCardCount = findViewById(R.id.tvCardCount)
        tvBankingTotal = findViewById(R.id.tvBankingTotal)
        tvBankingCount = findViewById(R.id.tvBankingCount)
        tvTnGTotal = findViewById(R.id.tvTnGTotal)
        tvTnGCount = findViewById(R.id.tvTnGCount)
        tvCashTotal = findViewById(R.id.tvCashTotal)
        tvCashCount = findViewById(R.id.tvCashCount)

        loadingState = findViewById(R.id.loadingState)
        emptyState = findViewById(R.id.emptyState)
        rvPayments = findViewById(R.id.rvPayments)

        rvPayments.layoutManager = LinearLayoutManager(this)

        // ===== CLICK LISTENERS =====
        btnClose.setOnClickListener { finish() }
        btnRefresh.setOnClickListener { loadPayments() }

        // ===== INITIAL LOAD =====
        loadPayments()
    }

    // ===================================================================
    // LOAD PAYMENTS (now a proper class method)
    // ===================================================================
    private fun loadPayments() {
        loadingState.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        rvPayments.visibility = View.GONE

        val bookings = BookingStore.all(this)

        if (bookings.isEmpty()) {
            loadingState.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            tvTotalAmount.text = "RM 0.00"
            tvTotalCount.text = "From 0 bookings"
            return
        }

        // ===== BUILD PAYMENT ITEMS =====
        val payments = bookings.map { booking ->
            AdminPaymentItem(
                bookingId = booking.id,
                ownerName = booking.ownerName.ifBlank { booking.ownerEmail.ifBlank { "User" } },
                companionName = booking.companion.ifBlank { "Unassigned" },
                method = booking.payment,
                date = booking.date,
                amount = if (booking.estimatedTotal > 0) booking.estimatedTotal
                else BookingPricing.total(booking)
            )
        }

        // ===== CALCULATE TOTALS =====
        val totalAmount = payments.sumOf { it.amount }
        val totalCount = payments.size

        fun methodSum(vararg keywords: String): Pair<Double, Int> {
            val filtered = payments.filter { p ->
                keywords.any { p.method.contains(it, ignoreCase = true) }
            }
            return Pair(filtered.sumOf { it.amount }, filtered.size)
        }

        val (cardSum, cardCount) = methodSum("Card")
        val (bankSum, bankCount) = methodSum("Banking", "FPX")
        val (tngSum, tngCount) = methodSum("Touch", "TnG")
        val (cashSum, cashCount) = methodSum("Cash")

        // ===== UPDATE HERO =====
        tvTotalAmount.text = BookingPricing.format(totalAmount)
        tvTotalCount.text = "From $totalCount booking(s)"

        // ===== UPDATE GRID =====
        tvCardTotal.text = BookingPricing.format(cardSum)
        tvCardCount.text = "$cardCount payment(s)"

        tvBankingTotal.text = BookingPricing.format(bankSum)
        tvBankingCount.text = "$bankCount payment(s)"

        tvTnGTotal.text = BookingPricing.format(tngSum)
        tvTnGCount.text = "$tngCount payment(s)"

        tvCashTotal.text = BookingPricing.format(cashSum)
        tvCashCount.text = "$cashCount payment(s)"

        // ===== UPDATE LIST =====
        loadingState.visibility = View.GONE
        rvPayments.visibility = View.VISIBLE

        rvPayments.adapter = PaymentManagementAdapter(
            payments.sortedByDescending { it.date }
        )
    }
}