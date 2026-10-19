package com.waff.app.core

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Entity(tableName = "managed_transfers")
data class ManagedTransferRecord(
    @PrimaryKey val id: Long,
    val filename: String,
    val mimeType: String,
    val sourceUrl: String,
    val formatSelection: String,
    val localPath: String,
    val createdAtMillis: Long,
    val status: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val reason: Int
)

@Dao
interface ManagedTransferDao {
    @Query("SELECT * FROM managed_transfers ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<ManagedTransferRecord>

    @Query("SELECT * FROM managed_transfers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ManagedTransferRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ManagedTransferRecord)

    @Query("UPDATE managed_transfers SET status = :status, bytesDownloaded = :downloaded, totalBytes = :total, reason = :reason WHERE id = :id")
    suspend fun updateProgress(id: Long, status: Int, downloaded: Long, total: Long, reason: Int)

    @Query("DELETE FROM managed_transfers WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface DownloadRecordDao {
    @Query("SELECT * FROM download_records ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<DownloadRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DownloadRecord)

    @Query("DELETE FROM download_records WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [DownloadRecord::class, ManagedTransferRecord::class], version = 2, exportSchema = false)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadRecordDao(): DownloadRecordDao
    abstract fun managedTransferDao(): ManagedTransferDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS managed_transfers (
                    id INTEGER NOT NULL PRIMARY KEY,
                    filename TEXT NOT NULL,
                    mimeType TEXT NOT NULL,
                    sourceUrl TEXT NOT NULL,
                    formatSelection TEXT NOT NULL,
                    localPath TEXT NOT NULL,
                    createdAtMillis INTEGER NOT NULL,
                    status INTEGER NOT NULL,
                    bytesDownloaded INTEGER NOT NULL,
                    totalBytes INTEGER NOT NULL,
                    reason INTEGER NOT NULL
                )""")
            }
        }
        @Volatile private var instance: DownloadDatabase? = null

        fun get(context: Context): DownloadDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, DownloadDatabase::class.java, "waff-downloads.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
