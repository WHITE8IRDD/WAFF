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
}
