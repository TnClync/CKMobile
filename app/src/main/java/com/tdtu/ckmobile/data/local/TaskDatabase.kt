package com.tdtu.ckmobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tdtu.ckmobile.data.local.dao.ConflictLogDao
import com.tdtu.ckmobile.data.local.dao.TaskDao
import com.tdtu.ckmobile.data.local.entity.ConflictLogEntity
import com.tdtu.ckmobile.data.local.entity.TaskEntity

@Database(
    entities = [TaskEntity::class, ConflictLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun conflictLogDao(): ConflictLogDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        fun getInstance(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "task_management.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
