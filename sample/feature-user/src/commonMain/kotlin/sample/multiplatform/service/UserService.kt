package sample.multiplatform.service

import sample.multiplatform.annotations.LogExecution
import sample.multiplatform.annotations.RequirePermission
import sample.multiplatform.annotations.Trace
import sample.multiplatform.db.User
import sample.multiplatform.db.UserDao

/**
 * 사용자 관리 서비스 샘플.
 *
 * - [LogExecution]: 모든 메서드의 호출 정보를 자동 로깅
 * - [RequirePermission]: 권한이 필요한 메서드에 접근 제어
 * - [Trace]: 메서드 호출 계층 추적
 *
 * Room KMP를 통해 플랫폼별 SQLite DB에 사용자 데이터를 영속 저장합니다.
 */
class UserService(private val dao: UserDao) {

    private var _isLoggedIn = false
    val isLoggedIn: Boolean get() = _isLoggedIn

    /**
     * 사용자 로그인을 처리합니다.
     * 데모용으로 자격증명을 하드코딩합니다.
     */
    @LogExecution(tag = "UserService", level = "INFO")
    @Trace(spanName = "user-login")
    fun login(username: String, password: String): Boolean {
        val success = username == "admin" && password == "secret"
        if (success) _isLoggedIn = true
        return success
    }

    /**
     * 현재 세션을 종료합니다.
     * - [LogExecution]: 로그아웃 이벤트를 자동 로깅합니다.
     */
    @LogExecution(tag = "UserService", level = "INFO")
    fun logout() {
        _isLoggedIn = false
    }

    /**
     * 전체 사용자 목록을 조회합니다.
     * - [RequirePermission]: "READ_USER_DATA" 권한이 없으면 차단됩니다.
     */
    @LogExecution(tag = "UserService")
    @RequirePermission("READ_USER_DATA")
    suspend fun getAllUsers(): List<String> {
        return dao.getAllUsers().map { it.username }
    }

    /**
     * 새 사용자를 등록합니다.
     * - [RequirePermission]: "ADMIN" 권한이 없으면 차단됩니다.
     */
    @LogExecution(tag = "UserService", level = "WARN")
    @RequirePermission("ADMIN")
    @Trace(spanName = "create-user")
    suspend fun createUser(username: String, email: String): Boolean {
        return dao.insertUser(User(username, email)) != -1L
    }

    /**
     * 사용자를 삭제합니다.
     * - [RequirePermission]: "ADMIN" 권한이 없으면 차단됩니다.
     */
    @LogExecution(tag = "UserService", level = "WARN")
    @RequirePermission("ADMIN")
    suspend fun deleteUser(username: String): Boolean {
        return dao.deleteUser(username) > 0
    }

    suspend fun getUserCount(): Int = dao.getUserCount()
}
