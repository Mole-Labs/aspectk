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

        assertEquals(1, TimingAspect.timings.size, "A timing should be recorded after requestPayment is called")
    }

    @Test
    fun `TimingAspect should include method name in timing log`() {
        fakeNow = 0L
        paymentService.requestPayment("order-001", 100.0)

        assertTrue(
            TimingAspect.timings.first().contains("requestPayment"),
            "The timing log should include the function name",
        )
    }

    @Test
    fun `TimingAspect should measure elapsed time via clock`() {
        var callCount = 0
        TimingAspect.clock = {
            // First call (start) returns 0, second call (end) returns 42
            if (callCount++ == 0) 0L else 42L
        }

        paymentService.requestPayment("order-001", 100.0)

        assertTrue(
            TimingAspect.timings.first().contains("42ms"),
            "The measured elapsed time should be included in the timing log",
        )
    }

    @Test
    fun `TimingAspect Around should return original function result`() {
        fakeNow = 0L
        val result = paymentService.requestPayment("order-001", 100.0)

        assertTrue(result.startsWith("TXN-"), "The original function's return value should be passed through unchanged")
    }

    @Test
    fun `TimingAspect should accumulate timings across multiple calls`() {
        fakeNow = 0L
        paymentService.requestPayment("order-001", 100.0)

        fakeNow = 2000L
        paymentService.requestPayment("order-002", 200.0)

        assertEquals(2, TimingAspect.timings.size, "Both calls should be recorded")
    }
}
