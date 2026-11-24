package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupRestoreResult
import com.example.data.backup.LocalBackupManager
import com.example.data.local.CachedSmsEntity
import com.example.data.sms.AndroidSmsDataSource
import com.example.security.KeyStoreCryptoManager
import com.example.security.SensitiveMessageFilter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches AFX SMS Manager`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AFX SMS Manager", appName)
    }

    @Test
    fun `sensitive message filter detects OTPs and banking alerts`() {
        assertTrue(SensitiveMessageFilter.isSensitive("Bank", "Your OTP is 482910. Do not share this code."))
        assertTrue(SensitiveMessageFilter.isSensitive("HDFC", "A/C XX1234 debited by USD 45.00."))
        assertFalse(SensitiveMessageFilter.isSensitive("Alex", "Hey, are we still meeting for coffee at 4pm?"))
    }

    @Test
    fun `passphrase encryption and backup restore prevent duplicates`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backupManager = LocalBackupManager(context)
        val hash = AndroidSmsDataSource.computeBodyHash("+15550199", 1700000000000L, 1, "Hello AFX")
        val msg = CachedSmsEntity(
            uniqueKey = "sys_1_${hash.take(12)}",
            systemSmsId = 1L,
            threadId = 1L,
            address = "+15550199",
            displayName = "Alex",
            body = "Hello AFX",
            timestamp = 1700000000000L,
            type = 1,
            isRead = true,
            isFavorite = true,
            bodyHash = hash
        )

        val export = backupManager.createBackupFile(listOf(msg), "StrongPass123!")
        assertTrue(export is com.example.data.backup.BackupExportResult.Success)
        val exportedJson = (export as com.example.data.backup.BackupExportResult.Success).jsonContent

        val wrong = backupManager.parseAndPrepareRestore(exportedJson, "WrongPass", emptySet())
        assertTrue(wrong is BackupRestoreResult.WrongPassphrase)

        val valid = backupManager.parseAndPrepareRestore(exportedJson, "StrongPass123!", emptySet())
        assertTrue(valid is BackupRestoreResult.Success)
        assertEquals(1, (valid as BackupRestoreResult.Success).restoredCount)

        val deduped = backupManager.parseAndPrepareRestore(exportedJson, "StrongPass123!", setOf(hash))
        assertTrue(deduped is BackupRestoreResult.Success)
        assertEquals(0, (deduped as BackupRestoreResult.Success).restoredCount)
        assertEquals(1, deduped.duplicateSkippedCount)
    }

    @Test
    fun `pin hashing and token masking work safely`() {
        val salt = KeyStoreCryptoManager.generateSaltBase64()
        val hash = KeyStoreCryptoManager.hashPin("2468", salt)
        assertTrue(KeyStoreCryptoManager.verifyPin("2468", salt, hash))
        assertFalse(KeyStoreCryptoManager.verifyPin("1357", salt, hash))

        val masked = KeyStoreCryptoManager.maskToken("123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZ1234")
        assertNotNull(masked)
        assertFalse(masked.contains("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
        assertTrue(masked.endsWith("1234"))
    }
}
