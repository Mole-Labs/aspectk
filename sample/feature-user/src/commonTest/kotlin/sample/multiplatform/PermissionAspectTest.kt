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

        assertTrue(result.isEmpty(), "권한이 있으면 함수가 정상 실행되어야 합니다")
    }

    @Test
    fun `PermissionAspect should throw PermissionDeniedException when permission is missing`() = runTest {
        // READ_USER_DATA 권한 없이 호출
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
            "예외 메시지에 권한 이름이 포함되어야 합니다",
        )
    }

    @Test
    fun `PermissionAspect should throw with method name in message`() = runTest {
        val exception = assertFailsWith<PermissionDeniedException> {
            userService.getAllUsers()
        }

        assertTrue(
            exception.message?.contains("getAllUsers") == true,
            "예외 메시지에 함수명이 포함되어야 합니다",
        )
    }

    @Test
    fun `PermissionAspect should allow ADMIN operations when ADMIN permission is granted`() = runTest {
        PermissionAspect.grantedPermissions += "ADMIN"

        val created = userService.createUser("alice", "alice@example.com")

        assertTrue(created, "ADMIN 권한이 있으면 createUser가 성공해야 합니다")
    }

    @Test
    fun `PermissionAspect should block ADMIN operations without ADMIN permission`() = runTest {
        assertFailsWith<PermissionDeniedException> {
            userService.createUser("alice", "alice@example.com")
        }
    }

    @Test
    fun `PermissionAspect should handle multiple permissions independently`() = runTest {
        // READ_USER_DATA만 부여
        PermissionAspect.grantedPermissions += "READ_USER_DATA"

        val users = userService.getAllUsers() // 성공
        assertTrue(users.isEmpty())

        // ADMIN은 없으므로 차단
        assertFailsWith<PermissionDeniedException> {
            userService.createUser("alice", "alice@example.com")
        }
    }

    @Test
    fun `PermissionAspect should deny access after revoking permission`() = runTest {
        PermissionAspect.grantedPermissions += "READ_USER_DATA"
        userService.getAllUsers() // 성공

        PermissionAspect.revokeAll()

        assertFailsWith<PermissionDeniedException> {
            userService.getAllUsers() // 권한 없음
        }
    }

    @Test
    fun `functions without RequirePermission should execute without permission check`() {
        // login은 @RequirePermission이 없으므로 권한 없이도 실행되어야 함
        val result = userService.login("admin", "secret")
        assertFalse(result.not(), "로그인 함수는 권한 검사 없이 실행되어야 합니다")
    }
}
