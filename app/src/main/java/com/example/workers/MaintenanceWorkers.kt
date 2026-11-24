package com.example.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.repository.SmsRepository
import com.example.notifications.AfxNotificationHelper
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class InboxSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = SmsRepository(applicationContext)
        if (!repo.smsDataSource.hasReadSmsPermission()) {
            return Result.success()
        }

        repo.syncInboxFromDevice()

        val shouldNotify = inputData.getBoolean(KEY_NOTIFY, false)
        if (shouldNotify) {
            val settings = repo.settingsFlow.first()
            if (settings.notificationsEnabled && settings.notifyNewSms) {
                val sender = inputData.getString(KEY_SENDER) ?: "Message"
                val preview = inputData.getString(KEY_PREVIEW) ?: ""
                AfxNotificationHelper.showNewSmsNotification(
                    context = applicationContext,
                    sender = sender,
                    preview = preview,
                    privacyHidePreview = settings.privacyModeHidePreviews,
                    soundEnabled = settings.notificationSoundEnabled
                )
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_SENDER = "sender"
        const val KEY_PREVIEW = "preview"
        const val KEY_NOTIFY = "notify"
    }
}

class BackupReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = SmsRepository(applicationContext)
        val settings = repo.settingsFlow.first()

        // Also keep local index fresh when permission is granted
        if (repo.smsDataSource.hasReadSmsPermission()) {
            repo.syncInboxFromDevice()
        }

        if (settings.notificationsEnabled && settings.notifyBackupReminder) {
            val lastBackup = settings.lastBackupTimestamp
            val intervalMs = TimeUnit.DAYS.toMillis(settings.backupReminderDays.toLong())
            val elapsedMs = System.currentTimeMillis() - lastBackup
            if (lastBackup == 0L) {
                AfxNotificationHelper.showBackupReminderNotification(applicationContext, -1L)
            } else if (elapsedMs >= intervalMs) {
                val days = TimeUnit.MILLISECONDS.toDays(elapsedMs)
                AfxNotificationHelper.showBackupReminderNotification(applicationContext, days)
            }
        }
        return Result.success()
    }
}

object MaintenanceScheduler {
    private const val PERIODIC_WORK_NAME = "afx_sms_maintenance_worker"

    fun schedulePeriodicMaintenance(context: Context) {
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(
                context.applicationContext,
                androidx.work.Configuration.Builder().build()
            )
        }
        val request = PeriodicWorkRequestBuilder<BackupReminderWorker>(12, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
