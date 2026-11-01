package id.a3.core.domain.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SanitizedCrashReporterTest {

    private class TestCrashReporterDelegate : CrashReporter {
        val loggedMessages = mutableListOf<String>()
        val recordedExceptions = mutableListOf<Throwable>()
        val customKeys = mutableMapOf<String, Any>()
        var recordedUserId: String? = null

        override fun log(message: String) {
            loggedMessages.add(message)
        }

        override fun recordException(throwable: Throwable) {
            recordedExceptions.add(throwable)
        }

        override fun setCustomKey(key: String, value: String) {
            customKeys[key] = value
        }

        override fun setCustomKey(key: String, value: Boolean) {
            customKeys[key] = value
        }

        override fun setCustomKey(key: String, value: Int) {
            customKeys[key] = value
        }

        override fun setUserId(userId: String) {
            recordedUserId = userId
        }
    }

    private lateinit var delegate: TestCrashReporterDelegate
    private lateinit var reporter: SanitizedCrashReporter

    @Before
    fun setUp() {
        delegate = TestCrashReporterDelegate()
        reporter = SanitizedCrashReporter(delegate)
    }

    @Test
    fun `sanitizes Indonesian customer phone numbers from log messages`() {
        reporter.log("Order created for customer Ibu Siti with phone 081234567890 successfully")
        reporter.log("SMS notification sent to +6282347785986 for pickup")

        assertEquals(2, delegate.loggedMessages.size)
        assertEquals("Order created for customer Ibu Siti with phone [REDACTED_PHONE] successfully", delegate.loggedMessages[0])
        assertEquals("SMS notification sent to [REDACTED_PHONE] for pickup", delegate.loggedMessages[1])
        assertFalse(delegate.loggedMessages[0].contains("081234567890"))
        assertFalse(delegate.loggedMessages[1].contains("+6282347785986"))
    }

    @Test
    fun `sanitizes PIN and password from log messages`() {
        reporter.log("Manager approval verified with pin=4444")
        reporter.log("Owner login attempt: password: 'supersecretpassword'")

        assertEquals(2, delegate.loggedMessages.size)
        assertTrue(delegate.loggedMessages[0].contains("[REDACTED]"))
        assertTrue(delegate.loggedMessages[1].contains("[REDACTED]"))
        assertFalse(delegate.loggedMessages[0].contains("4444"))
        assertFalse(delegate.loggedMessages[1].contains("supersecretpassword"))
    }

    @Test
    fun `sanitizes Bearer auth tokens from log messages`() {
        reporter.log("Sync request dispatched with Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.token123")

        assertEquals(1, delegate.loggedMessages.size)
        assertEquals("Sync request dispatched with Authorization: Bearer [REDACTED_TOKEN]", delegate.loggedMessages[0])
        assertFalse(delegate.loggedMessages[0].contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
    }

    @Test
    fun `sanitizes exception messages before reporting`() {
        val sensitiveException = IllegalArgumentException("Customer 081298765432 failed payment with pin=1234")
        reporter.recordException(sensitiveException)

        assertEquals(1, delegate.recordedExceptions.size)
        val recorded = delegate.recordedExceptions.first()
        val recordedMsg = recorded.message ?: ""

        assertTrue(recordedMsg.contains("[REDACTED_PHONE]"))
        assertTrue(recordedMsg.contains("[REDACTED]"))
        assertFalse(recordedMsg.contains("081298765432"))
        assertFalse(recordedMsg.contains("1234"))
    }

    @Test
    fun `sanitizes custom keys and userId`() {
        reporter.setUserId("user_081234567890")
        reporter.setCustomKey("customer_phone", "085211223344")

        assertEquals("user_[REDACTED_PHONE]", delegate.recordedUserId)
        assertEquals("[REDACTED_PHONE]", delegate.customKeys["customer_phone"])
    }
}
