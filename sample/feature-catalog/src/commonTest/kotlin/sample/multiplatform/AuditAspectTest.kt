package sample.multiplatform

import sample.multiplatform.aspects.AuditAspect
import sample.multiplatform.viewmodel.BaseViewModel
import sample.multiplatform.viewmodel.OrderViewModel
import sample.multiplatform.viewmodel.ProductViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Minimal implementation that doesn't override any of BaseViewModel's methods.
 * Since BaseViewModel's annotated method is called directly, this verifies the
 * baseline case where the aspect applies without needing inherits=true.
 */
private class MinimalViewModel : BaseViewModel()

class AuditAspectTest {

    @BeforeTest
    fun setUp() {
        AuditAspect.clearLogs()
    }

    @AfterTest
    fun tearDown() {
        AuditAspect.clearLogs()
    }

    @Test
    fun `AuditAspect should record log when annotated BaseViewModel method is called`() {
        val vm = MinimalViewModel()

        // MinimalViewModel doesn't override loadData(), so this calls
        // BaseViewModel.loadData(), which is directly annotated with @AuditAction
        vm.loadData()

        assertTrue(AuditAspect.auditLogs.isNotEmpty(), "An audit log should be recorded when a BaseViewModel method is called")
    }

    @Test
    fun `AuditAspect should record log for ProductViewModel loadData without annotation on override`() {
        val vm = ProductViewModel()

        vm.loadData()

        assertTrue(
            AuditAspect.auditLogs.any { it.contains("loadData") },
            "ProductViewModel.loadData() should trigger the aspect even without @AuditAction (inherits=true)",
        )
    }

    @Test
    fun `AuditAspect should record log for OrderViewModel loadData without annotation on override`() {
        val vm = OrderViewModel()

        vm.loadData()

        assertTrue(
            AuditAspect.auditLogs.any { it.contains("loadData") },
            "OrderViewModel.loadData() should trigger the aspect even without @AuditAction (inherits=true)",
        )
    }

    @Test
    fun `AuditAspect should extract action parameter from annotation when method is directly annotated`() {
        // MinimalViewModel uses BaseViewModel.loadData() as-is without overriding it,
        // so @AuditAction(action = "load-data") is read from its annotationInfo
        val vm = MinimalViewModel()

        vm.loadData()

        val log = AuditAspect.auditLogs.first { it.contains("loadData") }
        assertTrue(
            log.contains("action=load-data"),
            "The annotation's action parameter should be reflected in the audit log",
        )
    }

    @Test
    fun `AuditAspect should accumulate logs across multiple ViewModel calls`() {
        val productVm = ProductViewModel()
        val orderVm = OrderViewModel()

        productVm.loadData()
        orderVm.loadData()
        productVm.submit("new-product")
        orderVm.reset()

        assertEquals(4, AuditAspect.auditLogs.size, "All four ViewModel method calls should accumulate in the audit log")
    }
}
