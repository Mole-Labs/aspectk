package sample.multiplatform.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

// :data doesn't depend on :composeApp (that would be circular -- :composeApp already depends on
// :data), so the Application Context has to be handed in from outside instead of imported
// directly. composeApp's own Application.onCreate() sets this.
object DatabaseContext {
    lateinit var appContext: Context
}

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val ctx = DatabaseContext.appContext
    val dbFile = ctx.getDatabasePath("users.db")
    return Room.databaseBuilder<AppDatabase>(
        context = ctx,
        name = dbFile.absolutePath,
    )
}
