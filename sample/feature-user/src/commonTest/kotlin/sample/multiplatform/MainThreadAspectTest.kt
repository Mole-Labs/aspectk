package sample.multiplatform

import sample.multiplatform.aspects.MainThreadAspect
import sample.multiplatform.db.FakeUserDao
import sample.multiplatform.service.UserService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainThreadAspectTest {

    private lateinit var userService: UserService

    @BeforeTest
    fun setUp() {
        MainThreadAspect.clear()
        userService = UserService(FakeUserDao())
    }

    @AfterTest
    fun tearDown() = MainThreadAspect.clear()

    @Test
    fun `advice runs on a function carrying only an androidx annotation`() {
        val greeting = userService.greeting("alice")

        assertEquals("Welcome back, alice", greeting)
        assertEquals(listOf("greeting"), MainThreadAspect.calls)
    }

    @Test
    fun `a function without the annotation is not recorded`() {
        userService.logout()

        assertTrue(MainThreadAspect.calls.isEmpty())
    }
}
