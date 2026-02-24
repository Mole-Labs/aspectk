package com.mole.sample

import com.mole.sample.aspects.DebounceAspect
import com.mole.sample.aspects.LoggingAspect
import com.mole.sample.aspects.PermissionAspect
import com.mole.sample.exceptions.DoubleClickException
import com.mole.sample.service.PaymentService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DebounceAspectTest {

    private lateinit var paymentService: PaymentService

    // 테스트에서 시간을 직접 제어하기 위한 가변 변수
    private var fakeNow = 0L

    @BeforeTest
    fun setUp() {
        DebounceAspect.reset()
        LoggingAspect.clearLogs()
        PermissionAspect.grantedPermissions += "PAYMENT"
        PermissionAspect.grantedPermissions += "REFUND"

        fakeNow = 0L
        // DebounceAspect의 시간 제공자를 mock으로 교체
        DebounceAspect.timeProvider = { fakeNow }
        paymentService = PaymentService()
    }

    @AfterTest
    fun tearDown() {
        DebounceAspect.reset()
        DebounceAspect.timeProvider = { com.mole.sample.platform.currentTimeMillis() }
        LoggingAspect.clearLogs()
        PermissionAspect.revokeAll()
    }

    @Test
    fun `DebounceAspect should allow first call`() {
        fakeNow = 0L

        val txnId = paymentService.requestPayment("order-001", 100.0)

        assertTrue(txnId.startsWith("TXN-"), "첫 번째 호출은 정상 처리되어야 합니다")
    }

    @Test
    fun `DebounceAspect should throw DoubleClickException on rapid re-call`() {
        fakeNow = 0L
        paymentService.requestPayment("order-001", 100.0) // 첫 호출 성공

        fakeNow = 500L // 500ms 후 (쿨다운 1000ms 이내)
        assertFailsWith<DoubleClickException> {
            paymentService.requestPayment("order-002", 200.0)
        }
    }

    @Test
    fun `DebounceAspect should allow call after cooldown period`() {
        fakeNow = 0L
        paymentService.requestPayment("order-001", 100.0) // 첫 호출

        fakeNow = 1500L // 1500ms 후 (쿨다운 1000ms 경과)
        val txnId = paymentService.requestPayment("order-002", 200.0) // 성공

        assertTrue(txnId.startsWith("TXN-"), "쿨다운 이후 호출은 허용되어야 합니다")
    }

    @Test
    fun `DebounceAspect should throw with descriptive message`() {
        fakeNow = 0L
        paymentService.requestPayment("order-001", 100.0)

        fakeNow = 200L
        val exception = assertFailsWith<DoubleClickException> {
            paymentService.requestPayment("order-002", 200.0)
        }

        assertTrue(
            exception.message?.contains("requestPayment") == true,
            "예외 메시지에 함수명이 포함되어야 합니다",
        )
        assertTrue(
            exception.message?.contains("800") == true,
            "남은 대기 시간(800ms)이 메시지에 포함되어야 합니다",
        )
    }

    @Test
    fun `DebounceAspect should track cooldown per function independently`() {
        fakeNow = 0L
        paymentService.requestPayment("order-001", 100.0) // requestPayment 첫 호출 (cooldown=1000ms)

        fakeNow = 100L
        // refund는 별도 함수이므로 별도 쿨다운 추적 (cooldown=500ms)
        val refundResult = paymentService.refund("TXN-1-ORDER-001")
        assertTrue(refundResult, "다른 함수는 독립적으로 쿨다운을 추적해야 합니다")
    }

    @Test
    fun `DebounceAspect should respect different cooldown values per annotation`() {
        fakeNow = 0L
        paymentService.refund("TXN-1-ORDER") // refund cooldown=500ms

        fakeNow = 300L // 300ms 후 (500ms 쿨다운 이내)
        assertFailsWith<DoubleClickException> {
            paymentService.refund("TXN-1-ORDER")
        }

        fakeNow = 600L // 600ms 후 (500ms 쿨다운 경과)
        val result = paymentService.refund("TXN-1-ORDER")
        assertTrue(result, "쿨다운(500ms) 이후에는 환불이 허용되어야 합니다")
    }
}
