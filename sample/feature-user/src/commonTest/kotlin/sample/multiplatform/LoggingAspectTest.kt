package sample.multiplatform

import kotlinx.coroutines.test.runTest
import sample.multiplatform.aspects.LoggingAspect
import sample.multiplatform.aspects.PermissionAspect
import sample.multiplatform.db.FakeUserDao
import sample.multiplatform.service.UserService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoggingAspectTest {

    private lateinit var userService: UserService

    @BeforeTest
    fun setUp() {
        LoggingAspect.clearLogs()
        PermissionAspect.grantedPermissions += "ADMIN"
        PermissionAspect.grantedPermissions += "READ_USER_DATA"
        userService = UserService(FakeUserDao())
    }

    @AfterTest
    fun tearDown() {
        LoggingAspect.clearLogs()
        PermissionAspect.revokeAll()
    }

    @Test
    fun `LoggingAspect should record log when annotated function is called`() {
        userService.login("alice", "pass123")

        assertTrue(LoggingAspect.logs.isNotEmpty(), "A log entry should be recorded")
    }

    @Test
    fun `LoggingAspect should include method name in log`() {
        userService.login("alice", "pass123")

        val log = LoggingAspect.logs.first { it.contains("login") }
        assertTrue(log.contains("login"), "The log should include the function name")
    }

    @Test
    fun `LoggingAspect should use custom tag and level from annotation`() {
        userService.login("alice", "pass123")

        val log = LoggingAspect.logs.first { it.contains("login") }
        assertTrue(log.contains("[INFO]"), "level=INFO should be reflected in the log")
        assertTrue(log.contains("[UserService]"), "tag=UserService should be reflected in the log")
    }

    @Test
    fun `LoggingAspect should mask password parameter`() {
        userService.login("alice", "secretpassword")

        val log = LoggingAspect.logs.first { it.contains("login") }
        assertTrue(log.contains("***"), "The password should be masked")
        assertTrue(!log.contains("secretpassword"), "The actual password must not be exposed in the log")
    }

    @Test
    fun `LoggingAspect should record multiple function calls`() = runTest {
        userService.login("alice", "pass")
        userService.createUser("bob", "bob@example.com")
        userService.getAllUsers()

        assertEquals(3, LoggingAspect.logs.size, "All three function calls should be recorded")
    }

    @Test
    fun `LoggingAspect should include return type in log`() = runTest {
        userService.getAllUsers()

        val log = LoggingAspect.logs.first { it.contains("getAllUsers") }
        assertTrue(log.contains("List"), "The return type should be included in the log")
    }
}
