package sample.multiplatform.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUser(user: User): Long

    @Query("DELETE FROM users WHERE username = :username")
    suspend fun deleteUser(username: String): Int

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
}
