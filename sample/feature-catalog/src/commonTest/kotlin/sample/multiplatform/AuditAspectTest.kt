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
 * BaseViewModel의 메서드를 오버라이드하지 않는 최소 구현체.
 * BaseViewModel의 어노테이션이 붙은 메서드가 직접 호출되므로
 * inherits=true 없이도 Aspect가 동작하는 기본 케이스를 검증합니다.
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

        // MinimalViewModel은 loadData()를 오버라이드하지 않으므로
        // @AuditAction이 직접 붙어 있는 BaseViewModel.loadData()가 호출됨
        vm.loadData()

        assertTrue(AuditAspect.auditLogs.isNotEmpty(), "BaseViewModel 메서드 호출 시 감사 로그가 기록되어야 합니다")
    }

    @Test
    fun `AuditAspect should record log for ProductViewModel loadData without annotation on override`() {
        val vm = ProductViewModel()

        vm.loadData()

        assertTrue(
            AuditAspect.auditLogs.any { it.contains("loadData") },
            "ProductViewModel.loadData()는 @AuditAction 없이도 Aspect가 동작해야 합니다 (inherits=true)",
        )
    }

    @Test
    fun `AuditAspect should record log for OrderViewModel loadData without annotation on override`() {
        val vm = OrderViewModel()

        vm.loadData()

        assertTrue(
            AuditAspect.auditLogs.any { it.contains("loadData") },
            "OrderViewModel.loadData()는 @AuditAction 없이도 Aspect가 동작해야 합니다 (inherits=true)",
        )
    }

    @Test
    fun `AuditAspect should extract action parameter from annotation when method is directly annotated`() {
        // MinimalViewModel은 오버라이드 없이 BaseViewModel.loadData()를 그대로 사용하므로
        // @AuditAction(action = "load-data")가 annotationInfo에서 읽힘
        val vm = MinimalViewModel()

        vm.loadData()

        val log = AuditAspect.auditLogs.first { it.contains("loadData") }
        assertTrue(
            log.contains("action=load-data"),
            "어노테이션의 action 파라미터가 감사 로그에 반영되어야 합니다",
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

        assertEquals(4, AuditAspect.auditLogs.size, "네 번의 ViewModel 메서드 호출이 모두 감사 로그에 누적되어야 합니다")
    }
}
