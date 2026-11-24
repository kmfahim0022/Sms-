package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object AfxNotificationHelper {

    const val CHANNEL_SMS = "afx_channel_sms_alerts"
    const val CHANNEL_BACKUP = "afx_channel_backup_status"
    const val CHANNEL_REMINDER = "afx_channel_backup_reminder"

    private const val NOTIF_ID_NEW_SMS = 1001
    private const val NOTIF_ID_BACKUP = 1002
    private const val NOTIF_ID_REMINDER = 1003

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val smsChannel = NotificationChannel(
            CHANNEL_SMS,
            context.getString(R.string.channel_sms_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_sms_desc)
        }

        val backupChannel = NotificationChannel(
            CHANNEL_BACKUP,
            context.getString(R.string.channel_backup_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_backup_desc)
        }

        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            context.getString(R.string.channel_reminder_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_reminder_desc)
        }

        manager.createNotificationChannels(listOf(smsChannel, backupChannel, reminderChannel))
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun launchAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showNewSmsNotification(
        context: Context,
        sender: String,
        preview: String,
        privacyHidePreview: Boolean,
        soundEnabled: Boolean
    ) {
        if (!hasNotificationPermission(context)) return
        val title = if (privacyHidePreview) "New SMS received" else "New SMS from $sender"
        val body = if (privacyHidePreview) "Preview hidden (Privacy Mode active)" else preview.take(120)

        val builder = NotificationCompat.Builder(context, CHANNEL_SMS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(launchAppIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(!soundEnabled)

        NotificationManagerCompat.from(context).notify(NOTIF_ID_NEW_SMS, builder.build())
    }

    fun showBackupResultNotification(
        context: Context,
        success: Boolean,
        title: String,
        message: String,
        soundEnabled: Boolean
    ) {
        if (!hasNotificationPermission(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_BACKUP)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(launchAppIntent(context))
            .setPriority(if (success) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setSilent(!soundEnabled)

        NotificationManagerCompat.from(context).notify(NOTIF_ID_BACKUP, builder.build())
    }

    fun showBackupReminderNotification(context: Context, daysSinceLastBackup: Long) {
        if (!hasNotificationPermission(context)) return
        val message = if (daysSinceLastBackup < 0) {
            "You haven't created a local SMS backup yet. Open AFX SMS Manager to back up your messages."
        } else {
            "It has been $daysSinceLastBackup days since your last SMS backup."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Local SMS Backup Reminder")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(launchAppIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)

        NotificationManagerCompat.from(context).notify(NOTIF_ID_REMINDER, builder.build())
    }
}
