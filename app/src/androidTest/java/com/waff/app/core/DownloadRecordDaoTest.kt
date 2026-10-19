package com.waff.app.core

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadRecordDaoTest {
    private lateinit var database: DownloadDatabase
    private lateinit var dao: DownloadRecordDao

    @Before fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java).build()
        dao = database.downloadRecordDao()
    }

    @After fun closeDatabase() = database.close()

    @Test fun recordsSurviveDaoReloadAndAreRemovedById() = runBlocking {
        val record = DownloadRecord(7L, "sample.mp4", "video/mp4", 1234L)
        dao.insert(record)
        assertEquals(listOf(record), dao.getAll())
        dao.delete(record.id)
        assertEquals(emptyList<DownloadRecord>(), dao.getAll())
    }

    @Test fun directTransferHistoryCachesSourceAndSystemStatus() = runBlocking {
        val record = DownloadRecord(8L, "saved.mp4", "video/mp4", 5678L,
            sourceUrl = "https://example.test/saved.mp4", status = android.app.DownloadManager.STATUS_SUCCESSFUL,
            bytesDownloaded = 2048L, totalBytes = 2048L, reason = 0, localUri = "content://downloads/8")
        dao.insert(record)
        assertEquals(record, dao.getById(record.id))
        dao.updateState(record.id, android.app.DownloadManager.STATUS_FAILED, 1024L, 2048L, android.app.DownloadManager.ERROR_UNKNOWN, record.localUri)
        assertEquals(android.app.DownloadManager.STATUS_FAILED, dao.getById(record.id)?.status)
        assertEquals(record.sourceUrl, dao.getById(record.id)?.sourceUrl)
    }

    @Test fun managedTransfersPersistProgressAndCanBeRemoved() = runBlocking {
        val managed = database.managedTransferDao()
        val record = ManagedTransferRecord(-1L, "merged.mkv", "video/x-matroska", "https://example.test/v",
            "137+140", "/tmp/merged.mkv", 1234L, android.app.DownloadManager.STATUS_PENDING, 0L, 0L, 0)
        managed.insert(record)
        managed.updateProgress(record.id, android.app.DownloadManager.STATUS_RUNNING, 50L, 100L, 0)
        assertEquals(50L, managed.getById(record.id)?.bytesDownloaded)
        assertEquals(android.app.DownloadManager.STATUS_RUNNING, managed.getById(record.id)?.status)
        managed.updateProgress(record.id, android.app.DownloadManager.STATUS_PAUSED, 50L, 100L, MANAGED_TRANSFER_PAUSED_REASON)
        assertEquals(MANAGED_TRANSFER_PAUSED_REASON, managed.getById(record.id)?.reason)
        managed.delete(record.id)
        assertEquals(null, managed.getById(record.id))
    }
}
