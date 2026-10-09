package sample.multiplatform.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val username: String,
    val email: String,
)
