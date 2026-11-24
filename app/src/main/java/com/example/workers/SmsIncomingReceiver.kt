package com.example.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Listens for incoming SMS broadcasts strictly to refresh the app's local Room inbox
 * and show an optional local notification (respecting Privacy Mode).
 * Does NOT forward or transmit SMS externally.
 */
class SmsIncomingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val firstSender = messages.firstOrNull()?.displayOriginatingAddress ?: "SMS"
        val combinedBody = messages.joinToString("") { it.messageBody.orEmpty() }

        val inputData = Data.Builder()
            .putString(InboxSyncWorker.KEY_SENDER, firstSender)
            .putString(InboxSyncWorker.KEY_PREVIEW, combinedBody.take(120))
            .putBoolean(InboxSyncWorker.KEY_NOTIFY, true)
            .build()

        val request = OneTimeWorkRequestBuilder<InboxSyncWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "afx_incoming_sms_sync",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
