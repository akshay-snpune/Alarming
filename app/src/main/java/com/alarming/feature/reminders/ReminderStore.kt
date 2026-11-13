package com.alarming.feature.reminders

import android.content.Context
import androidx.room.*
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Entity(tableName = "reminders")
data class ReminderRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val repeat: String,
    val firstDate: String,
    val startMinute: Int,
    val endMinute: Int,
    val intervalMinutes: Int,
    val zoneId: String,
    val daysOfWeekMask: Int = 0,
    val sound: String,
    val vibration: Boolean,
    val enabled: Boolean = true,
    val completed: Boolean = false,
    val nextTrigger: Long? = null,
) {
    fun schedule() = ReminderSchedule(
        RepeatRule.valueOf(repeat), LocalDate.parse(firstDate), startMinute,
        endMinute, intervalMinutes, zoneId, daysOfWeekMask,
    )
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY completed, enabled DESC, nextTrigger")
    fun observe(): Flow<List<ReminderRecord>>
    @Query("SELECT * FROM reminders") suspend fun all(): List<ReminderRecord>
    @Query("SELECT EXISTS(SELECT 1 FROM reminders WHERE sound = :id)") suspend fun usesTone(id: String): Boolean
    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun find(id: Long): ReminderRecord?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(record: ReminderRecord): Long
    @Query("DELETE FROM reminders WHERE id = :id") suspend fun delete(id: Long)
}

@Database(
    entities = [ReminderRecord::class, com.alarming.audio.ImportedTone::class,
        com.alarming.feature.intervaltimer.IntervalTaskEntity::class,
        com.alarming.feature.intervaltimer.IntervalSlotEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class ReminderDatabase : RoomDatabase() {
    abstract fun reminders(): ReminderDao
    abstract fun tones(): com.alarming.audio.ImportedToneDao
    abstract fun intervalTasks(): com.alarming.feature.intervaltimer.IntervalTaskDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS imported_tones (id TEXT NOT NULL, name TEXT NOT NULL, filename TEXT NOT NULL, durationMillis INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS interval_tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL, sharedSound TEXT NOT NULL, startSound TEXT NOT NULL, completionSound TEXT NOT NULL, startAlertEnabled INTEGER NOT NULL, completionAlertEnabled INTEGER NOT NULL, vibrationEnabled INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS interval_task_slots (taskId INTEGER NOT NULL, position INTEGER NOT NULL, name TEXT NOT NULL, durationSeconds INTEGER NOT NULL, soundName TEXT NOT NULL, PRIMARY KEY(taskId, position), FOREIGN KEY(taskId) REFERENCES interval_tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_interval_task_slots_taskId ON interval_task_slots(taskId)")
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN daysOfWeekMask INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile private var instance: ReminderDatabase? = null
        fun get(context: Context): ReminderDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ReminderDatabase::class.java, "stagebell.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}

class ReminderStore internal constructor(
    private val context: Context,
    private val database: ReminderDatabase = ReminderDatabase.get(context),
) {
    private val dao = database.reminders()
    private val alarms = ReminderAlarms(context)
    private val mutex = Mutex()
    val records = dao.observe()

    suspend fun save(record: ReminderRecord) = mutex.withLock { saveLocked(record) }

    private suspend fun saveLocked(record: ReminderRecord) {
        require(record.title.isNotBlank() && record.title.length <= 80)
        require(record.description.length <= 500)
        val next = if (record.enabled && !record.completed) record.schedule().nextAfter(System.currentTimeMillis()) else null
        require(!record.enabled || record.completed || next != null) { "Choose a future date and time." }
        val updated = record.copy(nextTrigger = next)
        val id = dao.save(updated)
        alarms.cancel(id)
        alarms.schedule(updated.copy(id = id))
    }

    suspend fun delete(id: Long) = mutex.withLock {
        dao.delete(id)
        alarms.cancel(id)
    }

    suspend fun complete(id: Long) = mutex.withLock {
        dao.find(id)?.let { dao.save(it.copy(enabled = false, completed = true, nextTrigger = null)) }
        alarms.cancel(id)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean): Unit = mutex.withLock {
        val record = dao.find(id) ?: return@withLock
        saveLocked(record.copy(enabled = enabled, completed = false))
    }

    suspend fun recover() = mutex.withLock {
        val now = System.currentTimeMillis()
        for (record in dao.all()) {
            alarms.cancelAlarm(record.id)
            if (!record.enabled || record.completed) continue
            val next = if (record.repeat == RepeatRule.ONCE.name) {
                record.nextTrigger ?: record.schedule().nextAfter(Long.MIN_VALUE)
            } else record.schedule().nextAfter(now)
            val restored = record.copy(nextTrigger = next)
            dao.save(restored)
            alarms.schedule(restored)
        }
    }

    suspend fun deliver(id: Long, expectedTrigger: Long): Unit = mutex.withLock {
        val record = dao.find(id) ?: return
        if (!record.enabled || record.completed || record.nextTrigger != expectedTrigger) return
        val now = System.currentTimeMillis()
        if (now < expectedTrigger) {
            alarms.schedule(record)
            return
        }
        val next = if (record.repeat == RepeatRule.ONCE.name) null else record.schedule().nextAfter(now)
        val advanced = record.copy(
            nextTrigger = next, enabled = next != null,
            completed = next == null,
        )
        dao.save(advanced)
        alarms.schedule(advanced)
        if (record.repeat == RepeatRule.ONCE.name || now - expectedTrigger < 300_000L) {
            alarms.notify(record)
        }
    }

    companion object {
        @Volatile private var instance: ReminderStore? = null
        fun get(context: Context): ReminderStore = instance ?: synchronized(this) {
            instance ?: ReminderStore(context.applicationContext).also { instance = it }
        }
    }
}