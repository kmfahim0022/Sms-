package com.example

import android.app.Application
import com.example.data.repository.SmsRepository
import com.example.notifications.AfxNotificationHelper
import com.example.workers.MaintenanceScheduler

class AfxApplication : Application() {

    lateinit var repository: SmsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = SmsRepository(this)
        AfxNotificationHelper.createNotificationChannels(this)
        MaintenanceScheduler.schedulePeriodicMaintenance(this)
    }
}
