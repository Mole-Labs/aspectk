package com.mole.sample.service

import com.mole.sample.annotations.LogExecution
import com.mole.sample.annotations.RequirePermission
import com.mole.sample.annotations.Trace

/**
 * 사용자 관리 서비스 샘플.
 *
 * - [LogExecution]: 모든 메서드의 호출 정보를 자동 로깅
 * - [RequirePermission]: 권한이 필요한 메서드에 접근 제어
 * - [Trace]: 메서드 호출 계층 추적
 */
class UserService {

    private val users = mutableMapOf<String, String>() // username → email

    /**
     * 사용자 로그인을 처리합니다.
     * - [LogExecution]: 비밀번호는 `***`로 마스킹되어 로그에 기록됩니다.
     */
    @LogExecution(tag = "UserService", level = "INFO")
    @Trace(spanName = "user-login")
    fun login(username: String, password: String): Boolean {
        return username == "admin" && password == "secret"
    }

    /**
     * 전체 사용자 목록을 조회합니다.
     * - [RequirePermission]: "READ_USER_DATA" 권한이 없으면 차단됩니다.
     */
    @LogExecution(tag = "UserService")
    @RequirePermission("READ_USER_DATA")
    fun getAllUsers(): List<String> {
        return users.keys.toList()
    }

    /**
     * 새 사용자를 등록합니다.
     * - [RequirePermission]: "ADMIN" 권한이 없으면 차단됩니다.
     */
    @LogExecution(tag = "UserService", level = "WARN")
    @RequirePermission("ADMIN")
    @Trace(spanName = "create-user")
    fun createUser(username: String, email: String): Boolean {
        if (users.containsKey(username)) return false
        users[username] = email
        return true
    }

    /**
     * 사용자를 삭제합니다.
     * - [RequirePermission]: "ADMIN" 권한이 없으면 차단됩니다.
     */
    @LogExecution(tag = "UserService", level = "WARN")
    @RequirePermission("ADMIN")
    fun deleteUser(username: String): Boolean {
        return users.remove(username) != null
    }

    fun getUserCount(): Int = users.size
}
