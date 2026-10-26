package com.tdtu.ckmobile.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tdtu.ckmobile.data.local.TaskDatabase
import com.tdtu.ckmobile.data.remote.RetrofitClient

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = TaskDatabase.getInstance(applicationContext)
        val syncManager = SyncManager(
            taskDao = database.taskDao(),
            conflictLogDao = database.conflictLogDao(),
            taskApi = RetrofitClient.taskApi,
            context = applicationContext
        )

        return when (syncManager.sync()) {
            is SyncResult.Success -> Result.success()
            is SyncResult.TransientError ->
                if (runAttemptCount < 5) Result.retry() else Result.failure()
            is SyncResult.PermanentError -> Result.failure()
        }
    }
}
