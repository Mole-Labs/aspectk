package sample.multiplatform.db

import androidx.room.RoomDatabase

expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>

val database: AppDatabase by lazy {
    getDatabaseBuilder()
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}
