package sample.multiplatform.db

import androidx.room.Room
import androidx.room.RoomDatabase
import sample.multiplatform.AppApplication

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val ctx = AppApplication.appContext
    val dbFile = ctx.getDatabasePath("users.db")
    return Room.databaseBuilder<AppDatabase>(
        context = ctx,
        name = dbFile.absolutePath,
    )
}
