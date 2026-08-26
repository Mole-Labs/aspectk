package sample.multiplatform

import sample.multiplatform.aspects.TracingAspect
import sample.multiplatform.service.PaymentService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class TracingAspectTest {

    private lateinit var paymentService: PaymentService

    @BeforeTest
    fun setUp() {
        TracingAspect.clearStack()
        paymentService = PaymentService()
    }

    @AfterTest
    fun tearDown() {
        TracingAspect.clearStack()
    }

    @Test
    fun `TracingAspect should use method name when spanName is empty`() {
        paymentService.getTransactionCount()

        assertTrue(
            TracingAspect.callStack.any { it.contains("get-transaction-count") },
            "spanName이 지정된 경우 해당 이름이 기록되어야 합니다",
        )
    }
}
