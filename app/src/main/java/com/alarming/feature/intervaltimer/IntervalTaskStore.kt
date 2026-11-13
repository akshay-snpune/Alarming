package com.alarming.feature.intervaltimer

import android.content.Context
import androidx.room.*
import androidx.room.withTransaction
import com.alarming.feature.reminders.ReminderDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "interval_tasks")
data class IntervalTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val sharedSound: String,
    val startSound: String,
    val completionSound: String,
    val startAlertEnabled: Boolean,
    val completionAlertEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "interval_task_slots",
    primaryKeys = ["taskId", "position"],
    foreignKeys = [ForeignKey(
        entity = IntervalTaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("taskId")],
)
data class IntervalSlotEntity(
    val taskId: Long,
    val position: Int,
    val name: String,
    val durationSeconds: Int,
    val soundName: String,
)

data class IntervalTaskWithSlots(
    @Embedded val task: IntervalTaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val slots: List<IntervalSlotEntity>,
)

@Dao
interface IntervalTaskDao {
    @Transaction
    @Query("SELECT * FROM interval_tasks ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<IntervalTaskWithSlots>>

    @Transaction
    @Query("SELECT * FROM interval_tasks WHERE id = :id")
    suspend fun find(id: Long): IntervalTaskWithSlots?

    @Query("SELECT COUNT(*) FROM interval_tasks") suspend fun count(): Int
    @Query("SELECT EXISTS(SELECT 1 FROM interval_tasks WHERE sharedSound = :id OR startSound = :id OR completionSound = :id UNION ALL SELECT 1 FROM interval_task_slots WHERE soundName = :id)")
    suspend fun usesTone(id: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(task: IntervalTaskEntity): Long
    @Query("DELETE FROM interval_task_slots WHERE taskId = :taskId") suspend fun clearSlots(taskId: Long)
    @Insert suspend fun insertSlots(slots: List<IntervalSlotEntity>)
    @Query("DELETE FROM interval_tasks WHERE id = :id") suspend fun delete(id: Long)
}

class IntervalTaskStore internal constructor(
    private val database: ReminderDatabase,
) {
    private val dao = database.intervalTasks()
    val tasks = dao.observeAll()

    suspend fun get(id: Long) = dao.find(id)

    suspend fun save(task: IntervalTaskEntity, slots: List<IntervalSlotEntity>): Long {
        require(task.name.isNotBlank()) { "Enter a task name." }
        require(slots.isNotEmpty() && slots.size <= 50) { "A task needs 1 to 50 stages." }
        require(slots.all { it.durationSeconds in 1..18_000 }) { "Each stage must be 1 second to 5 hours." }
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val id = dao.insert(task.copy(id = task.id, createdAt = if (task.createdAt > 0) task.createdAt else now, updatedAt = now))
            dao.clearSlots(id)
            dao.insertSlots(slots.mapIndexed { index, slot -> slot.copy(taskId = id, position = index) })
            id
        }
    }

    suspend fun seedIfEmpty() {
        if (dao.count() != 0) return
        val now = System.currentTimeMillis()
        save(
            IntervalTaskEntity(name = "Morning Exercise", description = "A steady start, one stage at a time.",
                sharedSound = "Soft bell", startSound = "Focus tone", completionSound = "Completion chime",
                startAlertEnabled = true, completionAlertEnabled = true, vibrationEnabled = false,
                createdAt = now, updatedAt = now),
            listOf(
                IntervalSlotEntity(0, 0, "Warm-up", 420, "Soft bell"),
                IntervalSlotEntity(0, 1, "Stretching", 240, "Digital beep"),
                IntervalSlotEntity(0, 2, "Exercise", 180, "Double bell"),
                IntervalSlotEntity(0, 3, "Cool down", 420, "Soft bell"),
            ),
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)

    companion object {
        @Volatile private var instance: IntervalTaskStore? = null
        fun get(context: Context): IntervalTaskStore = instance ?: synchronized(this) {
            instance ?: IntervalTaskStore(ReminderDatabase.get(context.applicationContext)).also { instance = it }
        }
    }
}