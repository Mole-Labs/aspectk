package sample.multiplatform.service

import sample.multiplatform.annotations.LogExecution
import sample.multiplatform.annotations.RequirePermission
import sample.multiplatform.annotations.Trace
import sample.multiplatform.db.User
import sample.multiplatform.db.UserDao

/**
 * Sample user-management service.
 *
 * - [LogExecution]: automatically logs call info for every method
 * - [RequirePermission]: gates access to methods that require a permission
 * - [Trace]: traces the method-call hierarchy
 *
 * Persists user data to a platform-specific SQLite database via Room KMP.
 */
class UserService(private val dao: UserDao) {

    private var _isLoggedIn = false
    val isLoggedIn: Boolean get() = _isLoggedIn

    /**
     * Handles user login.
     * Credentials are hardcoded for demo purposes.
     */
    @LogExecution(tag = "UserService", level = "INFO")
    @Trace(spanName = "user-login")
    fun login(username: String, password: String): Boolean {
        val success = username == "admin" && password == "secret"
        if (success) _isLoggedIn = true
        return success
    }

    /**
     * Ends the current session.
     * - [LogExecution]: automatically logs the logout event.
     */
    @LogExecution(tag = "UserService", level = "INFO")
    fun logout() {
        _isLoggedIn = false
    }

    /**
     * Retrieves the full list of users.
     * - [RequirePermission]: blocked without the "READ_USER_DATA" permission.
     */
    @LogExecution(tag = "UserService")
    @RequirePermission("READ_USER_DATA")
    suspend fun getAllUsers(): List<String> {
        return dao.getAllUsers().map { it.username }
    }

    /**
     * Registers a new user.
     * - [RequirePermission]: blocked without the "ADMIN" permission.
     */
    @LogExecution(tag = "UserService", level = "WARN")
    @RequirePermission("ADMIN")
    @Trace(spanName = "create-user")
    suspend fun createUser(username: String, email: String): Boolean {
        return dao.insertUser(User(username, email)) != -1L
    }

    /**
     * Deletes a user.
     * - [RequirePermission]: blocked without the "ADMIN" permission.
     */
    @LogExecution(tag = "UserService", level = "WARN")
    @RequirePermission("ADMIN")
    suspend fun deleteUser(username: String): Boolean {
        return dao.deleteUser(username) > 0
    }

    suspend fun getUserCount(): Int = dao.getUserCount()
}
