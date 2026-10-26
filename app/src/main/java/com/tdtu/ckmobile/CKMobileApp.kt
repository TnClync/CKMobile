package com.tdtu.ckmobile

import android.app.Application
import com.tdtu.ckmobile.data.local.TaskDatabase
import com.tdtu.ckmobile.data.sync.SyncScheduler

class CKMobileApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Pre-warm local database instance
        TaskDatabase.getInstance(this)

        // Schedule 15-minute periodic sync with WorkManager
        SyncScheduler.schedulePeriodic(this)
    }
}
