package com.example.security

/**
 * Classifies SMS messages containing one-time passwords (OTPs), verification codes,
 * banking alerts, or sensitive financial credentials so they can be automatically excluded
 * from external Telegram backups by default.
 */
object SensitiveMessageFilter {

    private val keywordPatterns = listOf(
        Regex("\\b(otp|one[\\s-]?time[\\s-]?pass(word|code)?|2fa|mfa)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(verification|verify|security|auth(entication)?|login|reset|confirmation)[\\s-]+code\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(cvv|cvc|pin|mpin|tpin|iban|swift|sort code)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(debited|credited|withdrawn|deposited|avl\\s*bal|avail\\.?\\s*bal(ance)?|a/c|acct|upi|neft|imps|rtgs)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(card\\s+ending|credit\\s+card|debit\\s+card|bank\\s+alert|wire\\s+transfer)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bdo\\s+not\\s+share\\s+(this\\s+)?(code|otp|pin)\\b", RegexOption.IGNORE_CASE)
    )

    private val numericOtpContextRegex = Regex(
        "(code|otp|pin|passcode|token|verify|verification)[^\\d]{0,20}\\d{4,8}\\b|\\b\\d{4,8}[^\\d]{0,20}(is\\s+your|verification|otp|code)",
        RegexOption.IGNORE_CASE
    )

    private val currencyAmountAlertRegex = Regex(
        "(usd|eur|gbp|inr|rs\\.?|\\$|€|£)\\s?[\\d,]+(\\.\\d{1,2})?.*\\b(spent|charged|paid|balance|account|card)\\b",
        RegexOption.IGNORE_CASE
    )

    fun isSensitive(sender: String, body: String): Boolean {
        val combined = "$sender $body"
        if (keywordPatterns.any { it.containsMatchIn(combined) }) return true
        if (numericOtpContextRegex.containsMatchIn(body)) return true
        if (currencyAmountAlertRegex.containsMatchIn(body)) return true
        return false
    }

    fun reasonTag(sender: String, body: String): String? {
        val combined = "$sender $body"
        return when {
            Regex("\\b(otp|2fa|verification|verify|security\\s+code|passcode)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(combined) || numericOtpContextRegex.containsMatchIn(body) -> "OTP / Verification"
            Regex("\\b(debited|credited|bank|a/c|acct|card|upi|balance|cvv)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(combined) || currencyAmountAlertRegex.containsMatchIn(body) -> "Banking / Financial"
            else -> null
        }
    }
}
