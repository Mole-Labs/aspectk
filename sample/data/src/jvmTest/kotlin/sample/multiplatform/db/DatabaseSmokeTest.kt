package sample.multiplatform.db

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseSmokeTest {
    @Test
    fun `a user written through the DAO can be read back`() = runTest {
        val dao = database.userDao()
        dao.deleteUser("smoke")

        dao.insertUser(User(username = "smoke", email = "smoke@example.com"))

        assertEquals("smoke@example.com", dao.getAllUsers().single { it.username == "smoke" }.email)
        dao.deleteUser("smoke")
    }
}
