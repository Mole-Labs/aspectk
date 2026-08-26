package sample.multiplatform

import sample.multiplatform.aspects.DebounceAspect
import sample.multiplatform.aspects.LoggingAspect
import sample.multiplatform.aspects.PermissionAspect
import sample.multiplatform.aspects.TimingAspect
import sample.multiplatform.service.PaymentService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimingAspectTest {

    private lateinit var paymentService: PaymentService

    private var fakeNow = 0L

    @BeforeTest
    fun setUp() {
        TimingAspect.clearTimings()
        DebounceAspect.reset()
        LoggingAspect.clearLogs()
        PermissionAspect.grantedPermissions += "PAYMENT"

        fakeNow = 0L
        TimingAspect.clock = { fakeNow }
        DebounceAspect.timeProvider = { fakeNow }
        paymentService = PaymentService()
    }

    @AfterTest
    fun tearDown() {
        TimingAspect.clearTimings()
        TimingAspect.clock = { sample.multiplatform.platform.currentTimeMillis() }
        DebounceAspect.reset()
        DebounceAspect.timeProvider = { sample.multiplatform.platform.currentTimeMillis() }
        LoggingAspect.clearLogs()
        PermissionAspect.revokeAll()
    }

    @Test
    fun `TimingAspect should record timing after Around advice completes`() {
        fakeNow = 0L
        paymentService.requestPayment("order-001", 100.0)

        assertEquals(1, TimingAspect.timings.size, "requestPayment 호출 후 타이밍이 기록되어야 합니다")
    }

    @Test
    fun `TimingAspect should include method name in timing log`() {
        fakeNow = 0L
        paymentService.requestPayment("order-001", 100.0)

        assertTrue(
            TimingAspect.timings.first().contains("requestPayment"),
            "타이밍 로그에 함수명이 포함되어야 합니다",
        )
    }

    @Test
    fun `TimingAspect should measure elapsed time via clock`() {
        var callCount = 0
        TimingAspect.clock = {
            // 첫 번째 호출(start)은 0, 두 번째 호출(end)은 42
            if (callCount++ == 0) 0L else 42L
        }

        paymentService.requestPayment("order-001", 100.0)

        assertTrue(
            TimingAspect.timings.first().contains("42ms"),
            "측정된 경과 시간이 타이밍 로그에 포함되어야 합니다",
        )
    }

    @Test
    fun `TimingAspect Around should return original function result`() {
        fakeNow = 0L
        val result = paymentService.requestPayment("order-001", 100.0)

        assertTrue(result.startsWith("TXN-"), "원본 함수의 반환값이 그대로 전달되어야 합니다")
    }

    @Test
    fun `TimingAspect should accumulate timings across multiple calls`() {
        fakeNow = 0L
        paymentService.requestPayment("order-001", 100.0)

        fakeNow = 2000L
        paymentService.requestPayment("order-002", 200.0)

        assertEquals(2, TimingAspect.timings.size, "두 번의 호출이 모두 기록되어야 합니다")
    }
}
