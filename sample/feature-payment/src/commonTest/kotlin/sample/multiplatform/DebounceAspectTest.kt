package sample.multiplatform

import sample.multiplatform.aspects.DebounceAspect
import sample.multiplatform.aspects.LoggingAspect
import sample.multiplatform.aspects.PermissionAspect
import sample.multiplatform.exceptions.DoubleClickException
import sample.multiplatform.service.PaymentService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DebounceAspectTest {

    private lateinit var paymentService: PaymentService

    // Mutable variable used to control the clock directly in tests
    private var fakeNow = 0L

    @BeforeTest
    fun setUp() {
        DebounceAspect.reset()
        LoggingAspect.clearLogs()
        PermissionAspect.grantedPermissions += "PAYMENT"
        PermissionAspect.grantedPermissions += "REFUND"

        fakeNow = 0L
        // Swap DebounceAspect's time provider for a mock
        DebounceAspect.timeProvider = { fakeNow }
        paymentService = PaymentService()
    }

    @AfterTest
    fun tearDown() {
        DebounceAspect.reset()
        DebounceAspect.timeProvider = { sample.multiplatform.platform.currentTimeMillis() }
        LoggingAspect.clearLogs()
        PermissionAspect.revokeAll()
    }

    @Test
    fun `DebounceAspect should allow first call`() {
        fakeNow = 0L

        val txnId = paymentService.requestPayment("order-001", 100.0)

        assertTrue(txnId.startsWith("TXN-"), "The first call should be processed normally")
    }

    @Test
    fun `DebounceAspect should throw DoubleClickException on rapid re-call`() {
        fakeNow = 0L
        paymentService.requestPayment("order-001", 100.0) // first call succeeds

        fakeNow = 500L // 500ms later (within the 1000ms cooldown)
        assertFailsWith<DoubleClickException> {
            paymentService.requestPayment("order-002", 200.0)
        }
    }

    @Test
    fun `DebounceAspect should allow call after cooldown period`() {
        fakeNow = 0L
        paymentService.requestPayment("order-001", 100.0) // first call

        fakeNow = 1500L // 1500ms later (past the 1000ms cooldown)
        val txnId = paymentService.requestPayment("order-002", 200.0) // succeeds

        assertTrue(txnId.startsWith("TXN-"), "A call made after the cooldown should be allowed")
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
            "The exception message should include the function name",
        )
        assertTrue(
            exception.message?.contains("800") == true,
            "The remaining wait time (800ms) should be included in the message",
        )
    }

    @Test
    fun `DebounceAspect should track cooldown per function independently`() {
        fakeNow = 0L
        paymentService.requestPayment("order-001", 100.0) // first requestPayment call (cooldown=1000ms)

        fakeNow = 100L
        // refund is a separate function, so it tracks its own cooldown (cooldown=500ms)
        val refundResult = paymentService.refund("TXN-1-ORDER-001")
        assertTrue(refundResult, "Different functions should track their cooldowns independently")
    }

    @Test
    fun `DebounceAspect should respect different cooldown values per annotation`() {
        fakeNow = 0L
        paymentService.refund("TXN-1-ORDER") // refund cooldown=500ms

        fakeNow = 300L // 300ms later (within the 500ms cooldown)
        assertFailsWith<DoubleClickException> {
            paymentService.refund("TXN-1-ORDER")
        }

        fakeNow = 600L // 600ms later (past the 500ms cooldown)
        val result = paymentService.refund("TXN-1-ORDER")
        assertTrue(result, "Refunds should be allowed once the cooldown (500ms) has elapsed")
    }
}
