package sample.multiplatform.service

import sample.multiplatform.annotations.LogExecution
import sample.multiplatform.annotations.PreventDoubleClick
import sample.multiplatform.annotations.RequirePermission
import sample.multiplatform.annotations.Timed
import sample.multiplatform.annotations.Trace

/**
 * Sample payment-processing service.
 *
 * - [PreventDoubleClick]: prevents duplicate clicks on the payment button
 * - [RequirePermission]: gates access to sensitive payment operations
 * - [Trace]: traces the full payment flow
 */
class PaymentService {

    private var transactionCount = 0

    /**
     * Requests a payment.
     *
     * - [PreventDoubleClick]: prevents duplicate payment requests with a default 1000ms cooldown.
     * - [Timed]: measures how long payment processing takes.
     */
    @Timed
    @PreventDoubleClick(cooldownMs = 1000L)
    fun requestPayment(orderId: String, amount: Double): String {
        transactionCount++
        return "TXN-$transactionCount-${orderId.uppercase()}"
    }

    /**
     * Refunds a payment.
     *
     * - [PreventDoubleClick]: prevents duplicate refunds with a 500ms cooldown.
     * - [RequirePermission]: requires the "REFUND" permission.
     */
    @LogExecution(tag = "PaymentService", level = "WARN")
    @PreventDoubleClick(cooldownMs = 500L)
    @RequirePermission("REFUND")
    fun refund(transactionId: String): Boolean {
        return transactionId.startsWith("TXN-")
    }

    /**
     * Retrieves payment history.
     * - No permission required, but every call is logged.
     */
    @LogExecution(tag = "PaymentService")
    @Trace(spanName = "get-transaction-count")
    fun getTransactionCount(): Int = transactionCount
}
