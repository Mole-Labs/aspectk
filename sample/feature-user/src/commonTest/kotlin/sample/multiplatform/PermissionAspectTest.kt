package sample.multiplatform

import kotlinx.coroutines.test.runTest
import sample.multiplatform.aspects.LoggingAspect
import sample.multiplatform.aspects.PermissionAspect
import sample.multiplatform.aspects.TracingAspect
import sample.multiplatform.db.FakeUserDao
import sample.multiplatform.exceptions.PermissionDeniedException
import sample.multiplatform.service.UserService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionAspectTest {

    private lateinit var userService: UserService

    @BeforeTest
    fun setUp() {
        PermissionAspect.revokeAll()
        LoggingAspect.clearLogs()
        TracingAspect.clearStack()
        userService = UserService(FakeUserDao())
    }

    @AfterTest
    fun tearDown() {
        PermissionAspect.revokeAll()
        LoggingAspect.clearLogs()
        TracingAspect.clearStack()
    }

    @Test
    fun `PermissionAspect should allow function when required permission is granted`() = runTest {
        PermissionAspect.grantedPermissions += "READ_USER_DATA"

        val result = userService.getAllUsers()

        assertTrue(result.isEmpty(), "The function should run normally when the permission is granted")
    }

    @Test
    fun `PermissionAspect should throw PermissionDeniedException when permission is missing`() = runTest {
        // Call without the READ_USER_DATA permission
        assertFailsWith<PermissionDeniedException> {
            userService.getAllUsers()
        }
    }

    @Test
    fun `PermissionAspect should throw with descriptive message including permission name`() = runTest {
        val exception = assertFailsWith<PermissionDeniedException> {
            userService.getAllUsers()
        }

        assertTrue(
            exception.message?.contains("READ_USER_DATA") == true,
            "The exception message should include the permission name",
        )
    }

    @Test
    fun `PermissionAspect should throw with method name in message`() = runTest {
        val exception = assertFailsWith<PermissionDeniedException> {
            userService.getAllUsers()
        }

        assertTrue(
            exception.message?.contains("getAllUsers") == true,
            "The exception message should include the function name",
        )
    }

    @Test
    fun `PermissionAspect should allow ADMIN operations when ADMIN permission is granted`() = runTest {
        PermissionAspect.grantedPermissions += "ADMIN"

        val created = userService.createUser("alice", "alice@example.com")

        assertTrue(created, "createUser should succeed when the ADMIN permission is granted")
    }

    @Test
    fun `PermissionAspect should block ADMIN operations without ADMIN permission`() = runTest {
        assertFailsWith<PermissionDeniedException> {
            userService.createUser("alice", "alice@example.com")
        }
    }

    @Test
    fun `PermissionAspect should handle multiple permissions independently`() = runTest {
        // Grant only READ_USER_DATA
        PermissionAspect.grantedPermissions += "READ_USER_DATA"

        val users = userService.getAllUsers() // succeeds
        assertTrue(users.isEmpty())

        // Blocked because ADMIN is not granted
        assertFailsWith<PermissionDeniedException> {
            userService.createUser("alice", "alice@example.com")
        }
    }

    @Test
    fun `PermissionAspect should deny access after revoking permission`() = runTest {
        PermissionAspect.grantedPermissions += "READ_USER_DATA"
        userService.getAllUsers() // succeeds

        PermissionAspect.revokeAll()

        assertFailsWith<PermissionDeniedException> {
            userService.getAllUsers() // no permission
        }
    }

    @Test
    fun `functions without RequirePermission should execute without permission check`() {
        // login has no @RequirePermission, so it should run without a permission check
        val result = userService.login("admin", "secret")
        assertFalse(result.not(), "The login function should run without a permission check")
    }
}
