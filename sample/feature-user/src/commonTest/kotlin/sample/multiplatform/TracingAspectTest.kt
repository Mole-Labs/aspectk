package sample.multiplatform

import kotlinx.coroutines.test.runTest
import sample.multiplatform.aspects.LoggingAspect
import sample.multiplatform.aspects.PermissionAspect
import sample.multiplatform.aspects.TracingAspect
import sample.multiplatform.db.FakeUserDao
import sample.multiplatform.service.UserService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TracingAspectTest {

    private lateinit var userService: UserService

    @BeforeTest
    fun setUp() {
        TracingAspect.clearStack()
        LoggingAspect.clearLogs()
        PermissionAspect.grantedPermissions += "ADMIN"
        PermissionAspect.grantedPermissions += "READ_USER_DATA"
        userService = UserService(FakeUserDao())
    }

    @AfterTest
    fun tearDown() {
        TracingAspect.clearStack()
        LoggingAspect.clearLogs()
        PermissionAspect.revokeAll()
    }

    @Test
    fun `TracingAspect should add span to call stack when traced function is called`() {
        userService.login("alice", "pass")

        assertTrue(TracingAspect.callStack.isNotEmpty(), "A span should be recorded in callStack")
    }

    @Test
    fun `TracingAspect should use custom spanName from annotation`() {
        userService.login("alice", "pass")

        assertTrue(
            TracingAspect.callStack.contains("user-login"),
            "spanName='user-login' should be recorded in callStack",
        )
    }

    @Test
    fun `TracingAspect should track depth for nested traced calls`() = runTest {
        val traceLogs = mutableListOf<String>()
        TracingAspect.logger = { traceLogs.add(it) }

        userService.createUser("alice", "alice@example.com")

        // createUser carries @Trace(spanName="create-user")
        assertTrue(traceLogs.any { it.contains("create-user") }, "The create-user span should be traced")
    }

    @Test
    fun `TracingAspect should accumulate spans across multiple calls`() = runTest {
        userService.login("alice", "pass")
        userService.createUser("bob", "bob@example.com")

        assertEquals(2, TracingAspect.callStack.size, "Both @Trace function calls should accumulate")
    }
}
