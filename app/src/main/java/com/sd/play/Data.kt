package com.sd.play

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "plays")
data class PlayCount(
    @PrimaryKey val songId: Long,
    val total: Int,
)

@Dao
interface PlayDao {
    @Query("INSERT INTO plays (songId, total) VALUES (:songId, 1) ON CONFLICT(songId) DO UPDATE SET total = total + 1")
    suspend fun increment(songId: Long)

    @Query("SELECT songId FROM plays ORDER BY total DESC LIMIT 40")
    suspend fun top40(): List<Long>

    @Query("SELECT * FROM plays")
    suspend fun all(): List<PlayCount>
}

@Database(entities = [PlayCount::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playDao(): PlayDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "sdplayer.db"
            ).build().also { instance = it }
        }
    }
}
