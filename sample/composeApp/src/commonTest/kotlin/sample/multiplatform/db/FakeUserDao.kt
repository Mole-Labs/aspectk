package sample.multiplatform.db

class FakeUserDao : UserDao {
    private val users = mutableMapOf<String, String>() // username → email

    override suspend fun getAllUsers(): List<User> =
        users.entries.map { User(it.key, it.value) }

    override suspend fun insertUser(user: User): Long =
        if (users.containsKey(user.username)) -1L
        else { users[user.username] = user.email; 1L }

    override suspend fun deleteUser(username: String): Int =
        if (users.remove(username) != null) 1 else 0

    override suspend fun getUserCount(): Int = users.size
}
