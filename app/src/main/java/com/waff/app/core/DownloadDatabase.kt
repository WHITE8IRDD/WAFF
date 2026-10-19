package com.waff.app.core

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "download_records")
data class DownloadRecord(
    @PrimaryKey val id: Long,
    val filename: String,
    val mimeType: String,
    val createdAtMillis: Long
)

@Dao
interface DownloadRecordDao {
    @Query("SELECT * FROM download_records ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<DownloadRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DownloadRecord)

    @Query("DELETE FROM download_records WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [DownloadRecord::class], version = 1, exportSchema = false)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadRecordDao(): DownloadRecordDao

    companion object {
        @Volatile private var instance: DownloadDatabase? = null

        fun get(context: Context): DownloadDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, DownloadDatabase::class.java, "waff-downloads.db")
                .build().also { instance = it }
        }
    }
}
