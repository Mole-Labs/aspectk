package sample.service

import sample.annotations.LogExecution
import sample.annotations.PreventDoubleClick
import sample.annotations.RequirePermission
import sample.annotations.Trace

/**
 * 결제 처리 서비스 샘플.
 *
 * - [PreventDoubleClick]: 결제 버튼 중복 클릭 방지
 * - [RequirePermission]: 민감한 결제 기능 접근 제어
 * - [Trace]: 결제 흐름 전체 추적
 */
class PaymentService {

    private var transactionCount = 0

    /**
     * 결제를 요청합니다.
     *
     * - [PreventDoubleClick]: 기본 1000ms 쿨다운으로 중복 결제 요청을 방지합니다.
     * - [RequirePermission]: "PAYMENT" 권한이 있어야 결제할 수 있습니다.
     * - [Trace]: 결제 흐름 추적에 포함됩니다.
     */
//    @LogExecution(tag = "PaymentService", level = "INFO")
    @PreventDoubleClick(cooldownMs = 1000L)
//    @RequirePermission("PAYMENT")
//    @Trace(spanName = "request-payment")
    fun requestPayment(orderId: String, amount: Double): String {
        transactionCount++
        return "TXN-$transactionCount-${orderId.uppercase()}"
    }

    /**
     * 결제를 환불합니다.
     *
     * - [PreventDoubleClick]: 500ms 쿨다운으로 중복 환불을 방지합니다.
     * - [RequirePermission]: "REFUND" 권한이 있어야 환불할 수 있습니다.
     */
    @LogExecution(tag = "PaymentService", level = "WARN")
    @PreventDoubleClick(cooldownMs = 500L)
    @RequirePermission("REFUND")
    fun refund(transactionId: String): Boolean {
        return transactionId.startsWith("TXN-")
    }

    /**
     * 결제 내역을 조회합니다.
     * - 별도 권한 없이 조회 가능하지만 모든 호출이 로깅됩니다.
     */
    @LogExecution(tag = "PaymentService")
    @Trace(spanName = "get-transaction-count")
    fun getTransactionCount(): Int = transactionCount
}
